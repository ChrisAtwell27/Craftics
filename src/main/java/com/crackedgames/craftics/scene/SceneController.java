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
        record Trader(TraderSystem.TraderType type, TraderSystem.TraderOffer offer,
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
        ServerWorld world = (ServerWorld) player.getEntityWorld();
        CrafticsSavedData data = CrafticsSavedData.get(world);
        UUID owner = data.getEffectiveWorldOwner(player.getUuid());
        if (INSTANCES.containsKey(owner)) return; // a scene is already active for this island
        BlockPos origin = data.getSceneOrigin(owner);
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
        ServerPlayNetworking.send(player, new SceneStatePayload(false, 0, 0, 0, 0, 0));
        if (c.members.isEmpty()) c.teardown();
    }

    public static void tickAll() {
        for (SceneController c : new ArrayList<>(INSTANCES.values())) c.tick();
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
        this.layout = CodeSceneBuilder.buildLayout(
            origin.getX(), origin.getY(), origin.getZ(), sceneName);
        CodeSceneBuilder.place(world, origin, layout, snapshot);
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
            // bounds (a scene never calls enterCombat). oy is the floor-BLOCK Y (origin Y
            // minus 1): the floor slab sits at origin.getY()-1 and its top surface - the
            // plane TileRaycast intersects at arenaOriginY+1, where the player's feet stand -
            // is at origin.getY(). So arenaOriginY must be origin.getY()-1.
            ServerPlayNetworking.send(m, new SceneStatePayload(true,
                origin.getX(), origin.getY() - 1, origin.getZ(),
                CodeSceneBuilder.FLOOR_WIDTH, CodeSceneBuilder.FLOOR_DEPTH));
            m.requestTeleport(layout.spawnX() + 0.5, layout.spawnY(), layout.spawnZ() + 0.5);
            m.setYaw(layout.spawnYaw());
            m.setHeadYaw(layout.spawnYaw());
        }
    }

    /**
     * Give every booth a merchant identity. The village draws distinct villager
     * trader types; the bartering station draws distinct piglin barter
     * categories (tier-gated, falling back to the full list if the gate empties
     * it). Re-rolled per visit so the hall's stock rotates.
     */
    private void assignOccupants() {
        Random rng = new Random();
        boothOccupants.clear();
        if ("barter_station".equals(sceneName)) {
            List<BarterCategory> pool = new ArrayList<>(
                com.crackedgames.craftics.api.registry.BarterCategoryRegistry.all());
            pool.removeIf(c -> c.minBiomeTier() > tradeTier);
            if (pool.isEmpty()) {
                pool = new ArrayList<>(
                    com.crackedgames.craftics.api.registry.BarterCategoryRegistry.all());
            }
            java.util.Collections.shuffle(pool, rng);
            for (int i = 0; i < layout.stands().size(); i++) {
                // Wrap around if there are more booths than categories.
                BarterCategory cat = pool.isEmpty() ? null : pool.get(i % pool.size());
                if (cat == null) {
                    boothOccupants.add(new BoothOccupant.Barter(new BarterCategory(
                        "craftics:generic", "Barterer", "§6♦", "odds and ends", 0)));
                } else {
                    boothOccupants.add(new BoothOccupant.Barter(cat));
                }
            }
        } else {
            List<TraderSystem.TraderType> types =
                new ArrayList<>(List.of(TraderSystem.TraderType.values()));
            java.util.Collections.shuffle(types, rng);
            for (int i = 0; i < layout.stands().size(); i++) {
                TraderSystem.TraderType type = types.get(i % types.size());
                TraderSystem.TraderOffer offer = TraderSystem.generateOffer(type, tradeTier, rng);
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
                npc.setCustomName(Text.literal(t.type().icon + " " + t.type().displayName));
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
        if (tx < 0 || tx >= CodeSceneBuilder.FLOOR_WIDTH || tz < 0 || tz >= CodeSceneBuilder.FLOOR_DEPTH) return;
        int wx = origin.getX() + tx, wz = origin.getZ() + tz;
        int boothIdx = -1;
        for (int i = 0; i < layout.stands().size(); i++) {
            if (layout.stands().get(i).contains(wx, wz)) { boothIdx = i; break; }
        }
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
            fp.setPosition(x, y, z);
            fp.networkHandler.requestTeleport(x, y, z, yaw, fp.getPitch());
        };
        EntityWalker walker = new EntityWalker(mover, sx, sy, sz, ex, layout.spawnY(), ez, ticks,
            () -> {
                walkers.remove(fp.getUuid());
                if (onArrive != null) onArrive.run();
            });
        walkers.put(player.getUuid(), walker);
    }

    /** Open the booth's merchant UI for one player (they just arrived at its stand). */
    private void openBooth(ServerPlayerEntity player, int boothIdx) {
        if (boothIdx < 0 || boothIdx >= boothOccupants.size()) return;
        BoothOccupant occ = boothOccupants.get(boothIdx);
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
        for (int i = 0; i < trades.size(); i++) {
            TraderSystem.Trade t = trades.get(i);
            if (sb.length() > 0) sb.append('|');
            sb.append(Registries.ITEM.getId(t.item().getItem()))
              .append('~').append(t.item().getCount())
              .append('~').append(t.emeraldCost())
              .append('~').append(trader.stock()[i])
              .append('~').append(t.description());
        }
        CrafticsSavedData data = CrafticsSavedData.get(world);
        int emeralds = data.getPlayerData(player.getUuid()).emeralds;
        ServerPlayNetworking.send(player, new TraderOfferPayload(
            trader.type().displayName, trader.type().icon, sb.toString(), emeralds,
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
        CrafticsSavedData data = CrafticsSavedData.get(world);
        for (UUID memberUuid : new ArrayList<>(members)) {
            ServerPlayerEntity m = world.getServer().getPlayerManager().getPlayer(memberUuid);
            if (m == null) continue;
            ServerPlayNetworking.send(m, new ExitEventCinematicPayload());
            ServerPlayNetworking.send(m, new SceneStatePayload(false, 0, 0, 0, 0, 0));
            BlockPos hub = data.getHubTeleportPos(memberUuid);
            if (hub != null) m.requestTeleport(hub.getX() + 0.5, hub.getY(), hub.getZ() + 0.5);
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
