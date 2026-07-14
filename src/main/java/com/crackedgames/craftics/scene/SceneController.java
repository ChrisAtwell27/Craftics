package com.crackedgames.craftics.scene;

import com.crackedgames.craftics.CrafticsMod;
import com.crackedgames.craftics.combat.EntityWalker;
import com.crackedgames.craftics.combat.LootDelivery;
import com.crackedgames.craftics.combat.TraderSystem;
import com.crackedgames.craftics.combat.barter.BarterCategory;
import com.crackedgames.craftics.combat.barter.PiglinBarterSystem;
import com.crackedgames.craftics.network.BarterContextPayload;
import com.crackedgames.craftics.network.DialogueChoicePayload;
import com.crackedgames.craftics.network.DialoguePayload;
import com.crackedgames.craftics.network.EnterEventCinematicPayload;
import com.crackedgames.craftics.network.ExitEventCinematicPayload;
import com.crackedgames.craftics.network.RewardRevealPayload;
import com.crackedgames.craftics.network.SceneStatePayload;
import com.crackedgames.craftics.network.TraderOfferPayload;
import com.crackedgames.craftics.world.CrafticsSavedData;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.PiglinEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Drives a per-island walk-around merchant scene (Stage 2: enter, third-person
 * click-to-walk, click a booth to walk up and trade, leave). One instance per
 * island (world-owner UUID). Fully decoupled from CombatManager: it owns its own
 * session state and returns players to the hub on leave.
 *
 * <p>Each booth hosts a real merchant assigned at build time: the village gets
 * distinct villager trader types (Weaponsmith, Alchemist, ...) selling from the
 * emerald bank with per-visit stock, the bartering station gets distinct piglin
 * barter personalities (Warmonger, Hoarder, ...) running the gold gamble.
 * Clicking anywhere in a booth's stand rectangle walks the player to its stand
 * tile and opens that merchant's UI on arrival.
 */
public final class SceneController {
    private static final Map<UUID, SceneController> INSTANCES = new HashMap<>();

    /** A booth's merchant identity + inventory, assigned when the scene builds. */
    private sealed interface BoothOccupant {
        /** Villager trader: fixed type, rolled trade list, shared per-visit stock. */
        record Trader(com.crackedgames.craftics.combat.TraderCategory type, TraderSystem.TraderOffer offer,
                      int[] stock) implements BoothOccupant {}
        /** Piglin barter personality: the gold gamble against this category's pool. */
        record Barter(BarterCategory category) implements BoothOccupant {}
    }

    private final UUID islandOwner;
    private final ServerWorld world;
    private final BlockPos origin;
    private final String sceneName;
    private SceneLayout layout;
    private final List<UUID> members = new ArrayList<>();
    private final List<Integer> npcEntityIds = new ArrayList<>();
    private final List<BoothOccupant> boothOccupants = new ArrayList<>();
    private final Map<BlockPos, BlockState> snapshot = new LinkedHashMap<>();
    private final List<ChunkPos> forcedChunks = new ArrayList<>();
    // One active walker per player while they are click-walking.
    private final Map<UUID, EntityWalker> walkers = new HashMap<>();
    // Player → booth index with an open trade/barter UI.
    private final Map<UUID, Integer> activeBooth = new HashMap<>();
    // Player → per-visit piglin gamble threshold (rolled when the booth opens).
    private final Map<UUID, Integer> barterThresholds = new HashMap<>();
    /** Trade quality tier: the island's highest unlocked biome (clamped to 1..9). */
    private int tradeTier = 1;
    /** The scene's actual footprint, set at build time. Dynamic now: a procedural hall sizes
     *  itself to the merchant registry, and a schematic hall is whatever the author drew. Used
     *  for the client's tile-raycast bounds and to reject clicks outside the scene. */
    private int floorWidth = 16;
    private int floorDepth = CodeSceneBuilder.FLOOR_DEPTH;

    /**
     * The floor BLOCK's Y - one below the surface entities stand on. This is the convention
     * {@code SceneStatePayload} and {@code TileRaycast} share (the raycast intersects the floor
     * plane at {@code oy + 1}).
     *
     * <p>Derived from the layout rather than the scene origin, because the two build paths put
     * their floor in different places: the procedural hall lays a slab at {@code origin.getY()-1},
     * while a schematic's floor is wherever the author drew it inside the volume. Assuming the
     * former for both is what made every click in a hand-built hall land on the wrong tile.
     */
    private int floorBlockY() {
        return (layout != null ? layout.spawnY() : origin.getY()) - 1;
    }

    /**
     * The scene's booth rectangles for the client's glow renderer, WORLD coords:
     * {@code minX,minZ,maxX,maxZ,occupied;...}. Occupancy matters - an empty stand is
     * plain ground and glowing it would invite clicks that walk up to nobody.
     */
    private String boothDataString() {
        if (layout == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < layout.stands().size(); i++) {
            StandSlot slot = layout.stands().get(i);
            boolean occupied = i < boothOccupants.size() && boothOccupants.get(i) != null;
            if (sb.length() > 0) sb.append(';');
            sb.append(slot.minX()).append(',').append(slot.minZ()).append(',')
              .append(slot.maxX()).append(',').append(slot.maxZ()).append(',')
              .append(occupied ? 1 : 0);
        }
        return sb.toString();
    }

    private SceneController(UUID islandOwner, ServerWorld world, BlockPos origin, String sceneName) {
        this.islandOwner = islandOwner;
        this.world = world;
        this.origin = origin;
        this.sceneName = sceneName;
    }

    // ---- static dispatch (called from ModNetworking receivers + the tick hook) ----

    public static void handleEnter(ServerPlayerEntity player, String sceneName) {
        if (!"village".equals(sceneName) && !"barter_station".equals(sceneName)) {
            CrafticsMod.LOGGER.warn("Unknown scene '{}' requested by {}", sceneName,
                player.getName().getString());
            return;
        }
        // FIX 3: block entering a scene while mid-run. NOTE: getActiveCombat()
        // can never return null (it falls through to a created-on-demand
        // instance), so the old `getActiveCombat(...) != null` guard silently
        // blocked ALL scene entry - isEngaged() is the real check.
        if (com.crackedgames.craftics.combat.CombatManager.isEngaged(player.getUuid())) return;
        if (com.crackedgames.craftics.world.VisitProtection.isForeignVisitor(player)) return;
        ServerWorld world = (ServerWorld) player.getEntityWorld();
        // Scene booths are built at fixed coords inside the owner's island dim. Entry
        // is only valid while the player is standing in an island dim - otherwise the
        // build/teleport would land in the overworld lobby. (De-laned, Task 6.)
        if (!com.crackedgames.craftics.world.IslandDimensions.isIslandWorld(world)) return;
        CrafticsSavedData data = CrafticsSavedData.get(world);
        UUID owner = data.getEffectiveWorldOwner(player.getUuid());
        // Server-side unlock gate (the client hides the buttons too, but a stale or
        // hand-crafted payload must not open a hall the island hasn't earned). The
        // Trading Hall needs BOTH a met trader and a defeated Raid.
        var ownerData = data.getPlayerData(owner);
        if ("village".equals(sceneName)
                && (ownerData.metTraders.isEmpty() || !ownerData.raidDefeated)) {
            player.sendMessage(net.minecraft.text.Text.literal(
                "§cThe Trading Hall is locked - meet a trader at a run event and defend"
                + " the villagers from a Raid first."), false);
            return;
        }
        if ("barter_station".equals(sceneName) && ownerData.metBarterers.isEmpty()) {
            return;
        }
        if (INSTANCES.containsKey(owner)) return; // a scene is already active for this island
        BlockPos origin = data.getSceneOrigin(owner, sceneName);
        if (origin == null) {
            CrafticsMod.LOGGER.warn("No scene origin (no world slot) for {}",
                player.getName().getString());
            return;
        }
        SceneController c = new SceneController(owner, world, origin, sceneName);
        INSTANCES.put(owner, c);
        c.build(player);
    }

    public static void handleClick(ServerPlayerEntity player, int tx, int tz) {
        SceneController c = forPlayer(player.getUuid());
        if (c != null) c.clickTile(player, tx, tz);
    }

    public static void handleLeave(ServerPlayerEntity player) {
        SceneController c = forPlayer(player.getUuid());
        if (c != null) c.leave();
    }

    /** Scene-scoped TraderBuyPayload. True when this player is shopping at a
     *  scene booth (the payload is consumed; CombatManager must not see it).
     *  A player who is mid-run always routes to combat, whatever scene state
     *  they may have leaked - combat gates must never be starved. */
    public static boolean handleSceneTraderBuy(ServerPlayerEntity player, int tradeIndex) {
        SceneController c = forPlayer(player.getUuid());
        if (c == null) return false;
        if (com.crackedgames.craftics.combat.CombatManager.isEngaged(player.getUuid())) return false;
        Integer booth = c.activeBooth.get(player.getUuid());
        if (booth == null || !(c.boothOccupants.get(booth) instanceof BoothOccupant.Trader trader)) {
            return true; // stale packet in a scene → swallow, never route to combat
        }
        c.buyFromBooth(player, booth, trader, tradeIndex);
        return true;
    }

    /** Scene-scoped TraderDonePayload: close this player's TRADER booth session.
     *  A dying TraderScreen's safety net must not kill a freshly opened piglin
     *  barter session, so barter booths are only closed via the dialogue path. */
    public static boolean handleSceneTraderDone(ServerPlayerEntity player) {
        SceneController c = forPlayer(player.getUuid());
        if (c == null) return false;
        if (com.crackedgames.craftics.combat.CombatManager.isEngaged(player.getUuid())) return false;
        Integer booth = c.activeBooth.get(player.getUuid());
        if (booth != null && c.boothOccupants.get(booth) instanceof BoothOccupant.Trader) {
            c.activeBooth.remove(player.getUuid());
        }
        return true;
    }

    /** Scene-scoped DialogueChoicePayload (the piglin barter stepper actions). */
    public static boolean handleSceneDialogueChoice(ServerPlayerEntity player, String action) {
        SceneController c = forPlayer(player.getUuid());
        if (c == null) return false;
        if (com.crackedgames.craftics.combat.CombatManager.isEngaged(player.getUuid())) return false;
        c.barterChoice(player, action);
        return true;
    }

    /** True when the player is currently inside any island's merchant scene. */
    public static boolean isSceneMember(UUID playerUuid) {
        return forPlayer(playerUuid) != null;
    }

    /**
     * Pull one player out of their scene because they are entering a combat
     * run. Mirrors {@link #onDisconnect} but also resets the player's client
     * scene state (the run's EnterCombatPayload follows and takes over).
     * Tears the scene down when they were the last member.
     */
    public static void ejectForRun(ServerPlayerEntity player) {
        SceneController c = forPlayer(player.getUuid());
        if (c == null) return;
        c.members.remove(player.getUuid());
        c.walkers.remove(player.getUuid());
        c.activeBooth.remove(player.getUuid());
        c.barterThresholds.remove(player.getUuid());
        ServerPlayNetworking.send(player, new ExitEventCinematicPayload());
        ServerPlayNetworking.send(player, new SceneStatePayload(false, 0, 0, 0, 0, 0, ""));
        if (c.members.isEmpty()) c.teardown();
    }

    public static void tickAll() {
        for (SceneController c : new ArrayList<>(INSTANCES.values())) c.tick();
    }

    /** Count of currently-active island scenes, for {@code /craftics status}.
     *  INSTANCES only ever holds live scenes (created on enter, removed by
     *  {@link #teardown} on the last member leaving), so raw size IS the count. */
    public static int activeCount() {
        return INSTANCES.size();
    }

    public static void onDisconnect(UUID playerUuid) {
        SceneController c = forPlayer(playerUuid);
        if (c == null) return;
        c.members.remove(playerUuid);
        c.walkers.remove(playerUuid);
        c.activeBooth.remove(playerUuid);
        c.barterThresholds.remove(playerUuid);
        if (c.members.isEmpty()) c.teardown();
    }

    private static SceneController forPlayer(UUID playerUuid) {
        for (SceneController c : INSTANCES.values()) {
            if (c.members.contains(playerUuid)) return c;
        }
        return null;
    }

    // ---- lifecycle ----

    private void build(ServerPlayerEntity leader) {
        // A hand-built schematic wins when one exists, at craftics_scenes/<name>.schem next to the
        // run dir or bundled as data/craftics/scenes/<name>.schem. Booths are wherever its marker
        // blocks say, so an authored hall can look like anything. With no schematic we fall back
        // to the procedural walkway, which sizes itself to the merchant registry.
        SceneScanner.Placed placed =
            SceneScanner.buildAndScan(world, sceneName, origin, snapshot);
        if (placed != null && !placed.layout().stands().isEmpty()) {
            this.layout = placed.layout();
            this.floorWidth = placed.width();
            this.floorDepth = placed.length();
            CrafticsMod.LOGGER.info("Scene '{}' built from schematic: {} booth(s), {}x{}",
                sceneName, layout.stands().size(), floorWidth, floorDepth);
        } else {
            if (placed != null) {
                // A schematic that loaded but marked no booths is a mistake worth naming - the
                // player would otherwise get a hall with no merchants and no explanation.
                CrafticsMod.LOGGER.warn(
                    "Scene schematic '{}' has no usable booths (need STAND marker pairs with an "
                    + "NPC marker inside each). Falling back to the procedural scene.", sceneName);
            }
            this.layout = CodeSceneBuilder.buildLayout(
                origin.getX(), origin.getY(), origin.getZ(), sceneName);
            CodeSceneBuilder.place(world, origin, layout, sceneName, snapshot);
            this.floorWidth = CodeSceneBuilder.floorWidth(sceneName);
            this.floorDepth = CodeSceneBuilder.FLOOR_DEPTH;
        }

        // Wall the scene in so nobody falls off it. Outlines the BLOCKS: every standable
        // surface in the footprint gets its exposed fall edges walled, reachable or not -
        // the click-walk mover is a straight-line lerp, so "the player can't walk there"
        // was never a safe assumption. Runs after the floor exists in the world.
        // Snapshot-tracked, so it comes down with the scene.
        if (layout != null) {
            CodeSceneBuilder.sealPerimeter(world, origin, layout.spawnY(),
                floorWidth, floorDepth, snapshot);
        }
        CrafticsSavedData data = CrafticsSavedData.get(world);
        this.tradeTier = Math.max(1, Math.min(9,
            data.getPlayerData(islandOwner).highestBiomeUnlocked));
        assignOccupants();
        forceLoad();
        spawnNpcs();
        // Bring the whole online party into the scene - EXCEPT members who are
        // mid-run (or held at a between-level gate): yanking them out of a live
        // arena would strand their combat and let scene routing starve its packets.
        for (UUID memberUuid : data.getPartyMemberUuids(leader.getUuid())) {
            ServerPlayerEntity m = world.getServer().getPlayerManager().getPlayer(memberUuid);
            if (m == null) continue;
            if (com.crackedgames.craftics.combat.CombatManager.isEngaged(memberUuid)) continue;
            members.add(memberUuid);
            ServerPlayNetworking.send(m, new EnterEventCinematicPayload());
            // Carry the scene floor footprint so the client can seed TileRaycast's grid
            // bounds (a scene never calls enterCombat). oy is the floor-BLOCK Y: one BELOW the
            // surface feet stand on, because TileRaycast intersects the floor plane at
            // arenaOriginY + 1. layout.spawnY() is that standing surface for BOTH build paths, so
            // the floor block is one under it. Deriving this from the layout (rather than
            // assuming origin.getY() - 1, which is only true of the procedural hall) is what
            // makes a schematic whose floor sits high inside its own volume click correctly.
            ServerPlayNetworking.send(m, new SceneStatePayload(true,
                origin.getX(), floorBlockY(), origin.getZ(),
                floorWidth, floorDepth, boothDataString()));
            // Arrive TWO BLOCKS BEHIND the spawn marker (opposite its facing), still looking the
            // way it points - so the player enters with the hall in front of them instead of
            // standing on the marker tile itself. Falls back to the marker tile when the ground
            // behind it isn't standable. The yaw must go through the network handler or the
            // marker's facing never reaches the client.
            double[] entry = backedUpEntry(2);
            m.networkHandler.requestTeleport(entry[0], entry[1], entry[2], layout.spawnYaw(), 0f);
            m.setYaw(layout.spawnYaw());
            m.setHeadYaw(layout.spawnYaw());
        }
    }

    /**
     * Give every booth a merchant identity, but only for merchants the island
     * has actually met (via {@link MetMerchants}). Unmet merchants leave their
     * stand empty ({@code null}) rather than falling back to a default or
     * duplicating another merchant to fill the hall - meeting every merchant is
     * what fills the hall. Re-rolled per visit so the hall's stock rotates.
     */
    private void assignOccupants() {
        Random rng = new Random();
        boothOccupants.clear();
        CrafticsSavedData.PlayerData ownerData =
            CrafticsSavedData.get(world).getPlayerData(islandOwner);
        if ("barter_station".equals(sceneName)) {
            List<BarterCategory> pool = MetMerchants.filterMet(
                new ArrayList<>(com.crackedgames.craftics.api.registry.BarterCategoryRegistry.all()),
                ownerData.metBarterers, BarterCategory::id);
            pool.removeIf(c -> c.minBiomeTier() > tradeTier);
            java.util.Collections.shuffle(pool, rng);
            for (int i = 0; i < layout.stands().size(); i++) {
                // One booth per met category; leftover stands stay empty. No defaults.
                boothOccupants.add(i < pool.size() ? new BoothOccupant.Barter(pool.get(i)) : null);
            }
        } else {
            // Met-trader ids are matched through resolveLegacy, because saves written before
            // 0.2.10 stored the bare enum name ("WEAPONSMITH") rather than a namespaced id.
            // Without that, every existing player would silently lose every merchant they had met.
            List<com.crackedgames.craftics.combat.TraderCategory> types = new ArrayList<>();
            for (String metId : ownerData.metTraders) {
                var cat = com.crackedgames.craftics.api.registry.TraderCategoryRegistry
                    .resolveLegacy(metId);
                // A null here means the id names a trader that is no longer registered - an addon
                // the player has uninstalled. Skip it rather than fail the hall.
                if (cat != null && cat.minBiomeTier() <= tradeTier && !types.contains(cat)) {
                    types.add(cat);
                }
            }
            java.util.Collections.shuffle(types, rng);
            for (int i = 0; i < layout.stands().size(); i++) {
                if (i >= types.size()) { boothOccupants.add(null); continue; }
                com.crackedgames.craftics.combat.TraderCategory type = types.get(i);
                TraderSystem.TraderOffer offer = TraderSystem.generateOffer(type, tradeTier, rng, world);
                int[] stock = new int[offer.trades().size()];
                for (int t = 0; t < stock.length; t++) {
                    // Big-ticket items are one-offs; consumables restock 1-3 per visit.
                    stock[t] = offer.trades().get(t).emeraldCost() >= 10 ? 1 : 1 + rng.nextInt(3);
                }
                boothOccupants.add(new BoothOccupant.Trader(type, offer, stock));
            }
        }
    }

    private void forceLoad() {
        int margin = 16;
        int minCX = (origin.getX() - margin) >> 4, maxCX = (origin.getX() + 32 + margin) >> 4;
        int minCZ = (origin.getZ() - margin) >> 4, maxCZ = (origin.getZ() + 16 + margin) >> 4;
        for (int cx = minCX; cx <= maxCX; cx++) {
            for (int cz = minCZ; cz <= maxCZ; cz++) {
                world.setChunkForced(cx, cz, true);
                forcedChunks.add(new ChunkPos(cx, cz));
            }
        }
    }

    private void spawnNpcs() {
        for (int i = 0; i < layout.stands().size(); i++) {
            if (boothOccupants.get(i) == null) { npcEntityIds.add(-1); continue; }
            StandSlot s = layout.stands().get(i);
            Entity npc = "barter_station".equals(sceneName)
                ? EntityType.PIGLIN.spawn(world, new BlockPos(s.npcX(), s.npcY(), s.npcZ()), SpawnReason.EVENT)
                : EntityType.VILLAGER.spawn(world, new BlockPos(s.npcX(), s.npcY(), s.npcZ()), SpawnReason.EVENT);
            if (npc == null) {
                CrafticsMod.LOGGER.warn("Scene booth NPC failed to spawn at {},{}", s.npcX(), s.npcZ());
                npcEntityIds.add(-1); // keep booth index ↔ npc id alignment
                continue;
            }
            npc.refreshPositionAndAngles(s.npcX() + 0.5, s.npcY(), s.npcZ() + 0.5, s.npcYaw(), 0f);
            if (npc instanceof net.minecraft.entity.mob.MobEntity mob) {
                mob.setAiDisabled(true);
                mob.setInvulnerable(true);
                mob.setPersistent();
                mob.setNoGravity(true);
            }
            if (npc instanceof PiglinEntity piglin) {
                piglin.setImmuneToZombification(true);
                // A gold ingot in hand sells the "gold for goods" fantasy at a glance.
                piglin.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.GOLD_INGOT));
            }
            // Merchant identity floats over the booth so the hall reads at a glance.
            BoothOccupant occ = i < boothOccupants.size() ? boothOccupants.get(i) : null;
            if (occ instanceof BoothOccupant.Trader t) {
                npc.setCustomName(Text.literal(t.type().icon() + " " + t.type().displayName()));
                npc.setCustomNameVisible(true);
            } else if (occ instanceof BoothOccupant.Barter b) {
                npc.setCustomName(Text.literal(b.category().icon() + " " + b.category().displayName()));
                npc.setCustomNameVisible(true);
            }
            npcEntityIds.add(npc.getId());
        }
    }

    // ---- interaction ----

    /**
     * A scene floor click. If the tile lies inside a booth's stand rectangle,
     * walk the player to that booth's stand tile and open its merchant on
     * arrival; otherwise it's a plain walk-to.
     */
    private void clickTile(ServerPlayerEntity player, int tx, int tz) {
        if (walkers.containsKey(player.getUuid())) return; // already walking
        if (activeBooth.containsKey(player.getUuid())) return; // trading - the screen owns input
        // FIX 1: validate tile is inside the scene floor footprint (local coords from TileRaycast).
        if (tx < 0 || tx >= floorWidth || tz < 0 || tz >= floorDepth) return;
        int wx = origin.getX() + tx, wz = origin.getZ() + tz;
        int boothIdx = -1;
        for (int i = 0; i < layout.stands().size(); i++) {
            if (layout.stands().get(i).contains(wx, wz)) { boothIdx = i; break; }
        }
        // An empty stand (unmet merchant) is plain ground: no booth walk-up.
        if (boothIdx >= 0 && boothOccupants.get(boothIdx) == null) boothIdx = -1;
        if (boothIdx >= 0) {
            StandSlot slot = layout.stands().get(boothIdx);
            final int fBooth = boothIdx;
            walkTo(player, slot.playerX() + 0.5, slot.playerZ() + 0.5, () -> {
                float faceYaw = slot.playerYaw();
                player.setYaw(faceYaw); player.setHeadYaw(faceYaw); player.setBodyYaw(faceYaw);
                player.networkHandler.requestTeleport(
                    player.getX(), player.getY(), player.getZ(), faceYaw, player.getPitch());
                openBooth(player, fBooth);
            });
        } else {
            walkTo(player, wx + 0.5, wz + 0.5, null);
        }
    }

    private void walkTo(ServerPlayerEntity player, double ex, double ez, Runnable onArrive) {
        double sx = player.getX(), sy = player.getY(), sz = player.getZ();
        double dist = Math.hypot(ex - sx, ez - sz);
        int ticks = Math.max(1, (int) Math.round(dist / 0.25)); // 0.25 blocks/tick, matches combat
        final ServerPlayerEntity fp = player;
        EntityWalker.Mover mover = (x, y, z, yaw) -> {
            // Force the full rotation, view included: the scene uses the locked sky camera, so
            // the view yaw is never seen as a camera - but it IS what the teleport packet
            // carries, and the local player's own model renders from it. Without it the figure
            // slides along facing the wrong way while walking.
            fp.setYaw(yaw); fp.setHeadYaw(yaw); fp.setBodyYaw(yaw); fp.setOnGround(true);
            //? if <=1.21.4 {
            fp.prevX = fp.getX();
            fp.prevY = fp.getY();
            fp.prevZ = fp.getZ();
            //?} else {
            /*fp.lastX = fp.getX();
            fp.lastY = fp.getY();
            fp.lastZ = fp.getZ();
            *///?}
            double dx = x - fp.getX(), dz = z - fp.getZ();
            double len = Math.sqrt(dx * dx + dz * dz);
            if (len > 0) { fp.setVelocity(dx / len * 0.12, 0, dz / len * 0.12); fp.velocityDirty = true; }
            // Follow the terrain rather than the walker's flat lerp: villages have steps, and a
            // straight-line Y walked the player into rising ground / floated them over dips.
            double gy = terrainStandY(fp.getX(), fp.getY(), fp.getZ(), x, z);
            fp.setPosition(x, gy, z);
            fp.networkHandler.requestTeleport(x, gy, z, fp.getYaw(), fp.getPitch());
        };
        EntityWalker walker = new EntityWalker(mover, sx, sy, sz, ex, layout.spawnY(), ez, ticks,
            () -> {
                walkers.remove(fp.getUuid());
                if (onArrive != null) onArrive.run();
            });
        walkers.put(player.getUuid(), walker);
    }

    /**
     * The entry position: {@code stepsBack} blocks behind the spawn marker, opposite its facing,
     * so the player arrives looking INTO the scene rather than standing on the marker tile. Each
     * step back is verified standable (ground below, room to stand); the walk stops at the last
     * good tile, so an authored spawn near a wall or edge degrades to the marker tile itself.
     *
     * @return {x, y, z} entity coordinates (tile centers)
     */
    private double[] backedUpEntry(int stepsBack) {
        double yawRad = Math.toRadians(layout.spawnYaw());
        // Facing forward is (-sin, cos); backing up walks the opposite way.
        int dx = (int) Math.round(Math.sin(yawRad));
        int dz = (int) Math.round(-Math.cos(yawRad));
        int x = layout.spawnX(), z = layout.spawnZ(), y = layout.spawnY();
        for (int i = 0; i < stepsBack; i++) {
            int nx = x + dx, nz = z + dz;
            BlockPos feet = new BlockPos(nx, y, nz);
            boolean ok = !world.getBlockState(feet.down()).getCollisionShape(world, feet.down()).isEmpty()
                && world.getBlockState(feet).getCollisionShape(world, feet).isEmpty()
                && world.getBlockState(feet.up()).getCollisionShape(world, feet.up()).isEmpty();
            if (!ok) break;
            x = nx;
            z = nz;
        }
        return new double[]{x + 0.5, y, z + 0.5};
    }

    /**
     * The Y a walking player should stand at when moving into column {@code (tx, tz)}: the current
     * height, or one step up/down if that's where the ground is - the same +-1 rule the perimeter
     * flood fill walks with. Falls back to the current Y when nothing nearby is standable (mid-step
     * over a fence line, etc.), which just means "keep doing what the lerp was doing".
     */
    private double terrainStandY(double curX, double curY, double curZ, double tx, double tz) {
        int bx = (int) Math.floor(tx), bz = (int) Math.floor(tz);
        int by = (int) Math.floor(curY + 0.01);
        for (int dy : new int[]{0, -1, 1}) {
            BlockPos feet = new BlockPos(bx, by + dy, bz);
            boolean groundBelow = !world.getBlockState(feet.down())
                .getCollisionShape(world, feet.down()).isEmpty();
            boolean feetFree = world.getBlockState(feet)
                .getCollisionShape(world, feet).isEmpty();
            boolean headFree = world.getBlockState(feet.up())
                .getCollisionShape(world, feet.up()).isEmpty();
            if (groundBelow && feetFree && headFree) return by + dy;
        }
        return curY;
    }

    /** Open the booth's merchant UI for one player (they just arrived at its stand). */
    private void openBooth(ServerPlayerEntity player, int boothIdx) {
        if (boothIdx < 0 || boothIdx >= boothOccupants.size()) return;
        BoothOccupant occ = boothOccupants.get(boothIdx);
        if (occ == null) return; // empty stand - nothing to open
        activeBooth.put(player.getUuid(), boothIdx);
        if (occ instanceof BoothOccupant.Trader trader) {
            playBoothSound(boothIdx, SoundEvents.ENTITY_VILLAGER_TRADE, 1.0f, 1.0f);
            sendTraderOffer(player, trader, true);
        } else if (occ instanceof BoothOccupant.Barter barter) {
            playBoothSound(boothIdx, SoundEvents.ENTITY_PIGLIN_AMBIENT, 1.0f, 1.0f);
            barterThresholds.put(player.getUuid(), PiglinBarterSystem.rollThreshold(new Random()));
            var def = com.crackedgames.craftics.combat.dialogue.DialogueRegistry.get(
                "craftics:piglin_barter_" + barter.category().localId());
            if (def == null) {
                def = com.crackedgames.craftics.combat.dialogue.DialogueRegistry.pickFromGroup(
                    "piglin_barter", new Random());
            }
            if (def != null) sendDialogue(player, def);
            ServerPlayNetworking.send(player, new BarterContextPayload(
                true, countGold(player), PiglinBarterSystem.MAX_THRESHOLD));
        }
    }

    /** Serialize a booth's trade list for TraderOfferPayload:
     *  {@code itemId~count~cost~stock~desc} entries joined by {@code |}.
     *  {@code openScreen} distinguishes "the player just walked up" (open a
     *  shop screen) from post-purchase refreshes (update in place only, never
     *  resurrect a screen the player already closed). */
    private void sendTraderOffer(ServerPlayerEntity player, BoothOccupant.Trader trader,
                                 boolean openScreen) {
        StringBuilder sb = new StringBuilder();
        List<TraderSystem.Trade> trades = trader.offer().trades();
        // The REAL stacks travel alongside the string: components (potion contents,
        // enchantments) don't survive an itemId round-trip, and the preview lied about them.
        List<ItemStack> stacks = new ArrayList<>(trades.size());
        for (int i = 0; i < trades.size(); i++) {
            TraderSystem.Trade t = trades.get(i);
            if (sb.length() > 0) sb.append('|');
            sb.append(Registries.ITEM.getId(t.item().getItem()))
              .append('~').append(t.item().getCount())
              .append('~').append(t.emeraldCost())
              .append('~').append(trader.stock()[i])
              .append('~').append(t.description());
            stacks.add(t.item().copy());
        }
        CrafticsSavedData data = CrafticsSavedData.get(world);
        int emeralds = data.getPlayerData(player.getUuid()).emeralds;
        ServerPlayNetworking.send(player, new TraderOfferPayload(
            trader.type().displayName(), trader.type().icon(), sb.toString(), stacks, emeralds,
            openScreen ? 1 : 0));
    }

    /** One purchase at a villager booth, paid from the player's emerald bank. */
    private void buyFromBooth(ServerPlayerEntity player, int boothIdx,
                              BoothOccupant.Trader trader, int tradeIndex) {
        List<TraderSystem.Trade> trades = trader.offer().trades();
        if (tradeIndex < 0 || tradeIndex >= trades.size()) return;
        if (trader.stock()[tradeIndex] <= 0) {
            sendTraderOffer(player, trader, false); // client was stale - resync
            return;
        }
        TraderSystem.Trade trade = trades.get(tradeIndex);
        CrafticsSavedData data = CrafticsSavedData.get(world);
        CrafticsSavedData.PlayerData pd = data.getPlayerData(player.getUuid());
        if (!pd.spendEmeralds(trade.emeraldCost())) {
            sendTraderOffer(player, trader, false); // can't afford - resync the emerald count
            return;
        }
        data.markDirty();
        trader.stock()[tradeIndex]--;
        deliverOrDrop(player, trade.item().copy());
        playBoothSound(boothIdx, SoundEvents.ENTITY_VILLAGER_YES, 1.0f, 1.0f);
        // Refresh every party member currently shopping this booth (shared stock).
        for (Map.Entry<UUID, Integer> e : activeBooth.entrySet()) {
            if (e.getValue() != boothIdx) continue;
            ServerPlayerEntity shopper = world.getServer().getPlayerManager().getPlayer(e.getKey());
            if (shopper != null) sendTraderOffer(shopper, trader, false);
        }
        com.crackedgames.craftics.network.ModNetworking.syncPlayerStats(player);
    }

    /** Drive the piglin barter stepper: {@code barter:offer:<n>}, {@code barter:leave},
     *  or the DISMISS sentinel that closes the session after a result. */
    private void barterChoice(ServerPlayerEntity player, String action) {
        Integer boothIdx = activeBooth.get(player.getUuid());
        if (boothIdx == null
                || !(boothOccupants.get(boothIdx) instanceof BoothOccupant.Barter barter)) {
            // Not in a barter session: swallow (scenes have no other dialogue flows).
            activeBooth.remove(player.getUuid());
            return;
        }
        if (action == null || DialogueChoicePayload.ACTION_DISMISS.equals(action)) {
            closeBarter(player);
            return;
        }
        if (action.equals("barter:leave")) {
            var leave = new com.crackedgames.craftics.combat.dialogue.DialogueDefinition(
                "craftics:piglin_barter_leave", "minecraft:piglin", "piglin_barter_leave",
                List.of("The piglin snorts and turns away."), List.of());
            sendDialogue(player, leave);
            ServerPlayNetworking.send(player, new BarterContextPayload(false, 0, 0));
            return; // DISMISS on the leave line closes the session
        }
        if (!action.startsWith("barter:offer:")) { closeBarter(player); return; }

        int offer;
        try { offer = Integer.parseInt(action.substring("barter:offer:".length())); }
        catch (NumberFormatException ex) { closeBarter(player); return; }

        int gold = countGold(player);
        int max = Math.min(PiglinBarterSystem.MAX_THRESHOLD, gold);
        if (offer < 1 || offer > max) {
            // Stale gold count - re-send the stepper context with fresh numbers.
            ServerPlayNetworking.send(player, new BarterContextPayload(
                true, gold, PiglinBarterSystem.MAX_THRESHOLD));
            return;
        }

        removeGold(player, offer); // the gamble is paid whether it wins or loses

        Random rng = new Random();
        int threshold = barterThresholds.getOrDefault(player.getUuid(),
            PiglinBarterSystem.MAX_THRESHOLD);
        double chance = PiglinBarterSystem.successChance(offer, threshold);
        boolean success = rng.nextDouble() < chance;

        List<ItemStack> revealItems = new ArrayList<>();
        String resultLine;
        if (success) {
            ItemStack reward = PiglinBarterSystem.rollGoodReward(
                barter.category().id(), tradeTier, rng);
            if (reward.isEmpty()) reward = PiglinBarterSystem.rollJunk(rng);
            int rewardCount = reward.getCount();
            String rewardName = reward.getName().getString();
            revealItems.add(reward.copy());
            deliverOrDrop(player, reward);
            resultLine = "The piglin nods and shoves over " + rewardCount + "x " + rewardName + ".";

            int surplus = offer - threshold;
            if (surplus > 0 && rng.nextDouble() < PiglinBarterSystem.overpayBonusChance(surplus)) {
                ItemStack bonus = PiglinBarterSystem.rollGoodReward(
                    barter.category().id(), tradeTier, rng);
                if (!bonus.isEmpty()) {
                    revealItems.add(bonus.copy());
                    int bonusCount = bonus.getCount();
                    String bonusName = bonus.getName().getString();
                    deliverOrDrop(player, bonus);
                    resultLine += " It tosses in " + bonusCount + "x " + bonusName + " too!";
                }
            }
            playBoothSound(boothIdx, SoundEvents.ENTITY_PIGLIN_CELEBRATE, 1.0f, 1.0f);
        } else {
            ItemStack junk = PiglinBarterSystem.rollJunk(rng);
            int junkCount = junk.getCount();
            String junkName = junk.getName().getString();
            revealItems.add(junk.copy());
            deliverOrDrop(player, junk);
            resultLine = "The piglin grumbles and flicks you " + junkCount + "x " + junkName + ".";
            playBoothSound(boothIdx, SoundEvents.ENTITY_PIGLIN_ANGRY, 1.0f, 0.9f);
        }

        // Close the stepper, then the coin-flip reveal; its DISMISS routes back
        // here and closes the session (the player can re-open the booth to go again).
        ServerPlayNetworking.send(player, new BarterContextPayload(false, 0, 0));
        ServerPlayNetworking.send(player, new RewardRevealPayload(
            RewardRevealPayload.STYLE_GAMBLE, success ? 1 : 0,
            "Piglin Barter", resultLine, revealItems));
    }

    private void closeBarter(ServerPlayerEntity player) {
        activeBooth.remove(player.getUuid());
        barterThresholds.remove(player.getUuid());
        // Reset the client's parked stepper context too: a session closed via
        // the removed()-safety-net DISMISS never saw an explicit context-false.
        ServerPlayNetworking.send(player, new BarterContextPayload(false, 0, 0));
    }

    /** Deliver loot, dropping any overflow at the player's feet - the goods are
     *  already paid for (emeralds/gold spent, stock decremented), so a full
     *  inventory must never make them vanish. */
    private static void deliverOrDrop(ServerPlayerEntity player, ItemStack stack) {
        ItemStack leftover = LootDelivery.deliver(player, stack);
        if (leftover != null && !leftover.isEmpty()) {
            player.dropItem(leftover, false);
        }
    }

    private void sendDialogue(ServerPlayerEntity player,
                              com.crackedgames.craftics.combat.dialogue.DialogueDefinition def) {
        if (def == null) return;
        List<String> labels = new ArrayList<>();
        List<String> actions = new ArrayList<>();
        for (var ch : def.choices()) { labels.add(ch.label()); actions.add(ch.action()); }
        ServerPlayNetworking.send(player, new DialoguePayload(
            def.speaker(),
            DialoguePayload.encodeLines(def.lines()),
            DialoguePayload.encodeChoices(labels, actions),
            DialoguePayload.BG_SCENERY));
    }

    /** Play a sound at a booth's NPC (or its stand if the NPC failed to spawn). */
    private void playBoothSound(int boothIdx, net.minecraft.sound.SoundEvent sound,
                                float volume, float pitch) {
        StandSlot s = layout.stands().get(boothIdx);
        world.playSound(null, s.npcX() + 0.5, s.npcY() + 0.5, s.npcZ() + 0.5,
            sound, SoundCategory.NEUTRAL, volume, pitch);
    }

    /** Count gold ingots across the player's whole inventory. */
    private static int countGold(ServerPlayerEntity p) {
        int total = 0;
        for (int i = 0; i < p.getInventory().size(); i++) {
            ItemStack s = p.getInventory().getStack(i);
            if (s.getItem() == Items.GOLD_INGOT) total += s.getCount();
        }
        return total;
    }

    /** Remove exactly {@code amount} gold ingots from the player's inventory. */
    private static void removeGold(ServerPlayerEntity p, int amount) {
        int remaining = amount;
        for (int i = 0; i < p.getInventory().size() && remaining > 0; i++) {
            ItemStack s = p.getInventory().getStack(i);
            if (s.getItem() == Items.GOLD_INGOT) {
                int take = Math.min(remaining, s.getCount());
                s.decrement(take);
                remaining -= take;
            }
        }
    }

    private void tick() {
        for (EntityWalker w : new ArrayList<>(walkers.values())) {
            w.tick();
        }
    }

    private void leave() {
        for (UUID memberUuid : new ArrayList<>(members)) {
            ServerPlayerEntity m = world.getServer().getPlayerManager().getPlayer(memberUuid);
            if (m == null) continue;
            ServerPlayNetworking.send(m, new ExitEventCinematicPayload());
            ServerPlayNetworking.send(m, new SceneStatePayload(false, 0, 0, 0, 0, 0, ""));
            com.crackedgames.craftics.world.HubTeleports.toHub(m);
        }
        teardown();
    }

    private void teardown() {
        // Discard NPC entities.
        for (int id : npcEntityIds) {
            if (id < 0) continue;
            Entity e = world.getEntityById(id);
            if (e != null) e.discard();
        }
        npcEntityIds.clear();
        // Restore overwritten blocks (reverse insertion order is unnecessary; states are independent).
        for (Map.Entry<BlockPos, BlockState> e : snapshot.entrySet()) {
            world.setBlockState(e.getKey(), e.getValue(), net.minecraft.block.Block.FORCE_STATE);
        }
        snapshot.clear();
        // Release forced chunks.
        for (ChunkPos cp : forcedChunks) world.setChunkForced(cp.x, cp.z, false);
        forcedChunks.clear();
        walkers.clear();
        members.clear();
        activeBooth.clear();
        barterThresholds.clear();
        boothOccupants.clear();
        INSTANCES.remove(islandOwner);
    }
}
