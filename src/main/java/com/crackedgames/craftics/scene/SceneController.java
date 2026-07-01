package com.crackedgames.craftics.scene;

import com.crackedgames.craftics.CrafticsMod;
import com.crackedgames.craftics.combat.EntityWalker;
import com.crackedgames.craftics.combat.TraderSystem;
import com.crackedgames.craftics.network.EnterEventCinematicPayload;
import com.crackedgames.craftics.network.ExitEventCinematicPayload;
import com.crackedgames.craftics.network.SceneStatePayload;
import com.crackedgames.craftics.world.CrafticsSavedData;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.PiglinEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Drives a walk-around merchant scene (enter, third-person click-to-walk past standing NPCs,
 * leave). One instance per (island world-owner, scene type), so the Village and Bartering
 * Station can be active independently on the same island. Players enter and leave per-player:
 * the scene is built once (first player in) and torn down once (last player out). Fully
 * decoupled from CombatManager: it owns its own session state and returns players to the hub.
 */
public final class SceneController {
    /** One session per (island owner, scene type), so the Village and Bartering Station can
     *  be active independently on the same island. */
    private record SceneKey(UUID owner, String sceneName) {}

    private static final Map<SceneKey, SceneController> INSTANCES = new HashMap<>();

    private final SceneKey key;
    private final ServerWorld world;
    private final BlockPos origin;
    private final String sceneName;
    private SceneLayout layout;
    private final List<UUID> members = new ArrayList<>();
    private final List<Integer> npcEntityIds = new ArrayList<>();
    /** Booth index -> spawned NPC entity id, so a booth's trade can bind its own villager. */
    private final java.util.Map<Integer, Integer> boothNpcIds = new java.util.HashMap<>();
    private final Map<BlockPos, BlockState> snapshot = new LinkedHashMap<>();
    private final List<ChunkPos> forcedChunks = new ArrayList<>();
    // One active walker per player while they are click-walking.
    private final Map<UUID, EntityWalker> walkers = new HashMap<>();
    // Per-booth single-customer trade lock (villager Merchant binds one customer at a time).
    private final java.util.Map<Integer, UUID> boothCustomer = new java.util.HashMap<>();
    private final java.util.Map<Integer, java.util.Deque<UUID>> boothQueue = new java.util.HashMap<>();
    // Which booth a player is currently engaged with (trade or barter), for routing + cleanup.
    private final java.util.Map<UUID, Integer> playerBooth = new java.util.HashMap<>();
    // Per-player rolled barter threshold for the current bartering-station session.
    private final java.util.Map<UUID, Integer> barterThresholds = new java.util.HashMap<>();

    private SceneController(SceneKey key, ServerWorld world, BlockPos origin, String sceneName) {
        this.key = key;
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
        // Block entering a scene only while ACTUALLY in active combat. getActiveCombat never
        // returns null (CombatManager.get lazily creates a per-player instance), so a bare
        // != null check blocks every player who has ever played - it must gate on isActive().
        com.crackedgames.craftics.combat.CombatManager activeCombat =
            com.crackedgames.craftics.combat.CombatManager.getActiveCombat(player.getUuid());
        if (activeCombat != null && activeCombat.isActive()) return;
        ServerWorld world = (ServerWorld) player.getEntityWorld();
        CrafticsSavedData data = CrafticsSavedData.get(world);
        UUID owner = data.getEffectiveWorldOwner(player.getUuid());
        SceneKey key = new SceneKey(owner, sceneName);

        SceneController c = INSTANCES.get(key);
        if (c == null) {
            // First player into this (island, scene) - build the scene once.
            BlockPos origin = data.getSceneOrigin(owner, sceneName);
            if (origin == null) {
                CrafticsMod.LOGGER.warn("No scene origin (no world slot) for {}",
                    player.getName().getString());
                return;
            }
            c = new SceneController(key, world, origin, sceneName);
            INSTANCES.put(key, c);
            c.buildScene();
        }
        if (c.members.contains(player.getUuid())) return; // already in this scene
        c.joinPlayer(player);
    }

    public static void handleClick(ServerPlayerEntity player, int tx, int tz) {
        SceneController c = forPlayer(player.getUuid());
        if (c != null) c.onClick(player, tx, tz);
    }

    public static void handleLeave(ServerPlayerEntity player) {
        SceneController c = forPlayer(player.getUuid());
        if (c != null) c.removePlayer(player.getUuid());
    }

    public static void tickAll() {
        for (SceneController c : new ArrayList<>(INSTANCES.values())) c.tick();
    }

    public static void onDisconnect(UUID playerUuid) {
        SceneController c = forPlayer(playerUuid);
        if (c != null) c.removePlayer(playerUuid, false); // client is gone; skip the S2C sends
    }

    public static SceneController forPlayer(UUID playerUuid) {
        for (SceneController c : INSTANCES.values()) {
            if (c.members.contains(playerUuid)) return c;
        }
        return null;
    }

    // ---- lifecycle ----

    /** One-time world setup for this scene: layout, blocks, forced chunks, NPCs. No players. */
    private void buildScene() {
        this.layout = CodeSceneBuilder.buildLayout(
            origin.getX(), origin.getY(), origin.getZ(), sceneName);
        CodeSceneBuilder.place(world, origin, layout, snapshot);
        forceLoad();
        spawnNpcs();
    }

    /** Bring a single player into the already-built scene. */
    private void joinPlayer(ServerPlayerEntity player) {
        members.add(player.getUuid());
        ServerPlayNetworking.send(player, new EnterEventCinematicPayload());
        // Carry the scene floor footprint so the client can seed TileRaycast's grid bounds
        // (a scene never calls enterCombat). The floor slab sits at origin.getY()-1; its top
        // surface - the plane TileRaycast intersects at arenaOriginY+1, where the player's
        // feet stand - is at origin.getY(). So arenaOriginY must be origin.getY()-1.
        ServerPlayNetworking.send(player, new SceneStatePayload(true,
            origin.getX(), origin.getY() - 1, origin.getZ(),
            CodeSceneBuilder.FLOOR_WIDTH, CodeSceneBuilder.FLOOR_DEPTH));
        player.requestTeleport(layout.spawnX() + 0.5, layout.spawnY(), layout.spawnZ() + 0.5);
        player.setYaw(layout.spawnYaw());
        player.setHeadYaw(layout.spawnYaw());
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
        java.util.List<StandSlot> stands = layout.stands();
        for (int i = 0; i < stands.size(); i++) {
            StandSlot s = stands.get(i);
            Entity npc = "barter_station".equals(sceneName)
                ? EntityType.PIGLIN.spawn(world, new BlockPos(s.npcX(), s.npcY(), s.npcZ()), SpawnReason.EVENT)
                : EntityType.VILLAGER.spawn(world, new BlockPos(s.npcX(), s.npcY(), s.npcZ()), SpawnReason.EVENT);
            if (npc == null) {
                CrafticsMod.LOGGER.warn("Scene booth NPC failed to spawn at {},{}", s.npcX(), s.npcZ());
                continue;
            }
            npc.refreshPositionAndAngles(s.npcX() + 0.5, s.npcY(), s.npcZ() + 0.5, s.npcYaw(), 0f);
            if (npc instanceof net.minecraft.entity.mob.MobEntity mob) {
                mob.setAiDisabled(true);
                mob.setInvulnerable(true);
                mob.setPersistent();
                mob.setNoGravity(true);
            }
            if (npc instanceof PiglinEntity piglin) piglin.setImmuneToZombification(true);
            npcEntityIds.add(npc.getId());
            boothNpcIds.put(i, npc.getId());
        }
    }

    private void onClick(ServerPlayerEntity player, int tx, int tz) {
        java.util.List<StandSlot> stands = layout.stands();
        for (int i = 0; i < stands.size(); i++) {
            StandSlot s = stands.get(i);
            // tx/tz are LOCAL floor tiles (TileRaycast); StandSlot rect is WORLD - convert before testing.
            if (s.contains(tx + origin.getX(), tz + origin.getZ())) { walkToBooth(player, s, i); return; }
        }
        walkTo(player, tx, tz); // plain floor click (Stage 1)
    }

    /** Walk the player to a booth's walk-up tile, then open that booth on arrival. */
    private void walkToBooth(ServerPlayerEntity player, StandSlot s, int boothIndex) {
        if (walkers.containsKey(player.getUuid())) return;
        double sx = player.getX(), sy = player.getY(), sz = player.getZ();
        double ex = s.playerX() + 0.5, ez = s.playerZ() + 0.5;
        double dist = Math.hypot(ex - sx, ez - sz);
        int ticks = Math.max(1, (int) Math.round(dist / 0.25));
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
            com.crackedgames.craftics.combat.CinematicBroadcast.broadcastPlayerPositionToOthers(fp);
        };
        EntityWalker walker = new EntityWalker(mover, sx, sy, sz, ex, layout.spawnY(), ez, ticks,
            () -> { walkers.remove(fp.getUuid()); openBooth(fp, boothIndex); });
        walkers.put(player.getUuid(), walker);
    }

    /** Open a booth's trade or barter for the arriving player. Filled by Stage 2c Tasks 6-7. */
    private void openBooth(ServerPlayerEntity player, int boothIndex) {
        String occupant = SceneBooths.occupantFor(sceneName, boothIndex);
        if ("barter_station".equals(sceneName)) {
            openBarterFor(player, boothIndex, occupant); // Task 7
            return;
        }
        TraderSystem.TraderType type = SceneBooths.traderTypeFor(occupant);
        if (type == null) return;
        UUID current = boothCustomer.get(boothIndex);
        if (current != null && !current.equals(player.getUuid())) {
            java.util.Deque<UUID> q = boothQueue.computeIfAbsent(boothIndex, k -> new java.util.ArrayDeque<>());
            if (!q.contains(player.getUuid())) q.add(player.getUuid());
            player.sendMessage(net.minecraft.text.Text.literal("§7This merchant is busy — you're next in line..."), false);
            return;
        }
        openTradeFor(player, boothIndex, type);
    }

    /** Open a bartering-station booth: roll the piglin threshold, send the intro dialogue,
     *  and push the barter HUD context (gold + max offer) to the client. */
    private void openBarterFor(ServerPlayerEntity player, int boothIndex, String occupant) {
        com.crackedgames.craftics.combat.barter.BarterCategory cat = SceneBooths.barterCategoryFor(occupant);
        if (cat == null) return;
        playerBooth.put(player.getUuid(), boothIndex);
        java.util.Random rng = new java.util.Random();
        barterThresholds.put(player.getUuid(),
            com.crackedgames.craftics.combat.barter.PiglinBarterSystem.rollThreshold(rng));
        var def = com.crackedgames.craftics.combat.dialogue.DialogueRegistry.get(
            "craftics:piglin_barter_" + cat.localId());
        if (def == null) def = com.crackedgames.craftics.combat.dialogue.DialogueRegistry.pickFromGroup(
            "piglin_barter", new java.util.Random());
        if (def != null) sendSceneDialogue(player, def);
        int gold = countSceneGold(player);
        ServerPlayNetworking.send(player, new com.crackedgames.craftics.network.BarterContextPayload(
            true, gold, com.crackedgames.craftics.combat.barter.PiglinBarterSystem.MAX_THRESHOLD));
    }

    // ---- dialogue dispatch + trade close / reconcile / queue promotion (Task 7) ----

    /** Static dispatch target (Task 8 network receiver) - routes a dialogue choice to the
     *  scene the player is currently in, if any. */
    public static void handleDialogueChoice(ServerPlayerEntity player, String action) {
        SceneController c = forPlayer(player.getUuid());
        if (c != null) c.onDialogueChoice(player, action);
    }

    private void onDialogueChoice(ServerPlayerEntity player, String action) {
        if (com.crackedgames.craftics.network.DialogueChoicePayload.ACTION_MERCHANT_CLOSED.equals(action)) {
            var done = com.crackedgames.craftics.combat.dialogue.DialogueRegistry.get("craftics:trader_done");
            if (done != null) sendSceneDialogue(player, done);
            return;
        }
        Integer booth = playerBooth.get(player.getUuid());
        boolean inBarter = booth != null && "barter_station".equals(sceneName);
        if (inBarter && action != null && (action.startsWith("barter:")
                || com.crackedgames.craftics.network.DialogueChoicePayload.ACTION_DISMISS.equals(action))) {
            handleBarterChoice(player, booth, action);
            return;
        }
        var outcome = com.crackedgames.craftics.combat.dialogue.DialogueActions.resolve(action);
        switch (outcome) {
            case OPEN_TRADE, REOPEN_SHOP -> reopenTrade(player);
            case FINISH, CLOSE -> closeTrade(player);
            default -> {
                if (com.crackedgames.craftics.network.DialogueChoicePayload.ACTION_DISMISS.equals(action))
                    closeTrade(player);
            }
        }
    }

    /** Re-open the trade screen for the player's current booth (e.g. "buy more"). */
    private void reopenTrade(ServerPlayerEntity player) {
        Integer booth = playerBooth.get(player.getUuid());
        if (booth == null) return;
        TraderSystem.TraderType type = SceneBooths.traderTypeFor(SceneBooths.occupantFor(sceneName, booth));
        if (type != null) openTradeFor(player, booth, type);
    }

    /** Close a trade: reconcile the player's emerald balance back into saved data (every
     *  emerald item in their inventory becomes the new balance, mirroring the give on open),
     *  then release the booth and promote the next queued customer. */
    private void closeTrade(ServerPlayerEntity player) {
        CrafticsSavedData data = CrafticsSavedData.get(world);
        CrafticsSavedData.PlayerData pd = data.getPlayerData(player.getUuid());
        int collected = 0;
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.getItem() == net.minecraft.item.Items.EMERALD) {
                collected += stack.getCount();
                player.getInventory().removeStack(i);
            }
        }
        pd.emeralds = collected;
        data.markDirty();
        freeBooth(player);
    }

    /** Release the player's booth lock and, if they held it, hand it to the next queued
     *  party member still in the scene. */
    private void freeBooth(ServerPlayerEntity player) {
        Integer booth = playerBooth.remove(player.getUuid());
        if (booth == null) return;
        if (player.getUuid().equals(boothCustomer.get(booth))) {
            boothCustomer.remove(booth);
            // Clear the villager's bound customer (mirrors combat's handleTraderDone). Vanilla
            // already clears it on an Esc-close, but this covers the reopen/superseded paths and
            // ensures a freed booth is never left bound to a departed shopper before promotion.
            Integer npcId = boothNpcIds.get(booth);
            Entity npc = npcId != null ? world.getEntityById(npcId) : null;
            if (npc instanceof net.minecraft.entity.passive.MerchantEntity m) m.setCustomer(null);
            java.util.Deque<UUID> q = boothQueue.get(booth);
            while (q != null && !q.isEmpty()) {
                UUID nextUuid = q.poll();
                ServerPlayerEntity next = world.getServer().getPlayerManager().getPlayer(nextUuid);
                if (next != null && members.contains(nextUuid)) {
                    TraderSystem.TraderType type =
                        SceneBooths.traderTypeFor(SceneBooths.occupantFor(sceneName, booth));
                    if (type != null) openTradeFor(next, booth, type);
                    break;
                }
            }
        }
    }

    /** Resolve a barter dialogue choice: parse the offer, validate against gold, pay the
     *  gamble, roll the outcome, deliver the reward, and drive the reveal UI. */
    private void handleBarterChoice(ServerPlayerEntity player, int boothIndex, String action) {
        String occupant = SceneBooths.occupantFor(sceneName, boothIndex);
        com.crackedgames.craftics.combat.barter.BarterCategory cat = SceneBooths.barterCategoryFor(occupant);
        if (cat == null) { playerBooth.remove(player.getUuid()); return; }
        String categoryId = cat.id();
        int tier = 1;
        if (com.crackedgames.craftics.network.DialogueChoicePayload.ACTION_DISMISS.equals(action)
                || "barter:leave".equals(action)) {
            ServerPlayNetworking.send(player,
                new com.crackedgames.craftics.network.BarterContextPayload(false, 0, 0));
            playerBooth.remove(player.getUuid());
            barterThresholds.remove(player.getUuid());
            return;
        }
        if (!action.startsWith("barter:offer:")) { playerBooth.remove(player.getUuid()); return; }
        int offer;
        try { offer = Integer.parseInt(action.substring("barter:offer:".length())); }
        catch (NumberFormatException ex) { playerBooth.remove(player.getUuid()); return; }
        int gold = countSceneGold(player);
        int max = Math.min(com.crackedgames.craftics.combat.barter.PiglinBarterSystem.MAX_THRESHOLD, gold);
        if (offer < 1 || offer > max) { openBarterFor(player, boothIndex, occupant); return; }
        removeSceneGold(player, offer);
        java.util.Random rng = new java.util.Random();
        int threshold = barterThresholds.getOrDefault(player.getUuid(),
            com.crackedgames.craftics.combat.barter.PiglinBarterSystem.MAX_THRESHOLD);
        double chance = com.crackedgames.craftics.combat.barter.PiglinBarterSystem.successChance(offer, threshold);
        boolean success = rng.nextDouble() < chance;
        java.util.List<ItemStack> reveal = new java.util.ArrayList<>();
        String line;
        if (success) {
            ItemStack reward = com.crackedgames.craftics.combat.barter.PiglinBarterSystem
                .rollGoodReward(categoryId, tier, rng);
            if (reward.isEmpty()) reward = com.crackedgames.craftics.combat.barter.PiglinBarterSystem.rollJunk(rng);
            reveal.add(reward.copy());
            com.crackedgames.craftics.combat.LootDelivery.deliver(player, reward);
            line = "The piglin nods and shoves over " + reward.getCount() + "x " + reward.getName().getString() + ".";
        } else {
            ItemStack junk = com.crackedgames.craftics.combat.barter.PiglinBarterSystem.rollJunk(rng);
            reveal.add(junk.copy());
            com.crackedgames.craftics.combat.LootDelivery.deliver(player, junk);
            line = "The piglin grumbles and flicks you " + junk.getCount() + "x " + junk.getName().getString() + ".";
        }
        ServerPlayNetworking.send(player,
            new com.crackedgames.craftics.network.BarterContextPayload(false, 0, 0));
        sendSceneRewardReveal(player, com.crackedgames.craftics.network.RewardRevealPayload.STYLE_GAMBLE,
            success, "Piglin Barter", line, reveal);
    }

    // ---- scene-local replicas of CombatManager's private helpers ----

    /** Replica of CombatManager.sendDialogue: push a dialogue box to the client. */
    private void sendSceneDialogue(ServerPlayerEntity player,
                                   com.crackedgames.craftics.combat.dialogue.DialogueDefinition def) {
        if (def == null) return;
        java.util.List<String> labels = new java.util.ArrayList<>();
        java.util.List<String> actions = new java.util.ArrayList<>();
        for (var ch : def.choices()) { labels.add(ch.label()); actions.add(ch.action()); }
        ServerPlayNetworking.send(player, new com.crackedgames.craftics.network.DialoguePayload(
            def.speaker(),
            com.crackedgames.craftics.network.DialoguePayload.encodeLines(def.lines()),
            com.crackedgames.craftics.network.DialoguePayload.encodeChoices(labels, actions),
            com.crackedgames.craftics.network.DialoguePayload.BG_AUTO));
    }

    /** Replica of CombatManager.countGold: count gold ingots across the inventory. */
    private int countSceneGold(ServerPlayerEntity p) {
        int total = 0;
        for (int i = 0; i < p.getInventory().size(); i++) {
            ItemStack s = p.getInventory().getStack(i);
            if (s.getItem() == net.minecraft.item.Items.GOLD_INGOT) total += s.getCount();
        }
        return total;
    }

    /** Replica of CombatManager.removeGold: remove exactly {@code amount} gold ingots. */
    private void removeSceneGold(ServerPlayerEntity p, int amount) {
        int remaining = amount;
        for (int i = 0; i < p.getInventory().size() && remaining > 0; i++) {
            ItemStack s = p.getInventory().getStack(i);
            if (s.getItem() == net.minecraft.item.Items.GOLD_INGOT) {
                int take = Math.min(remaining, s.getCount());
                s.decrement(take);
                remaining -= take;
            }
        }
    }

    /** Replica of CombatManager.sendRewardReveal: open the shared reward-reveal screen. */
    private void sendSceneRewardReveal(ServerPlayerEntity player, int style, boolean success,
                                       String title, String line, java.util.List<ItemStack> items) {
        ServerPlayNetworking.send(player, new com.crackedgames.craftics.network.RewardRevealPayload(
            style, success ? 1 : 0, title == null ? "" : title,
            line == null ? "" : line, items));
    }

    private void openTradeFor(ServerPlayerEntity player, int boothIndex, TraderSystem.TraderType type) {
        Integer npcId = boothNpcIds.get(boothIndex);
        Entity npc = npcId != null ? world.getEntityById(npcId) : null;
        if (!(npc instanceof net.minecraft.entity.passive.MerchantEntity merchant)) return;
        boothCustomer.put(boothIndex, player.getUuid());
        playerBooth.put(player.getUuid(), boothIndex);

        CrafticsSavedData data = CrafticsSavedData.get(world);
        CrafticsSavedData.PlayerData pd = data.getPlayerData(player.getUuid());
        int give = pd.emeralds;
        if (give > 0) {
            int remaining = give;
            while (remaining > 0) {
                int stackSize = Math.min(64, remaining);
                player.getInventory().insertStack(
                    new net.minecraft.item.ItemStack(net.minecraft.item.Items.EMERALD, stackSize));
                remaining -= stackSize;
            }
            pd.emeralds = 0;
            data.markDirty();
        }

        int tier = 1; // Stage 2c scenes are tier 1; Stage 3 may vary this.
        TraderSystem.TraderOffer offer = SceneOfferStore.getOffers(key.owner(), boothIndex, type, tier);
        net.minecraft.village.TradeOfferList offers = merchant.getOffers();
        offers.clear();
        for (TraderSystem.Trade t : offer.trades()) {
            net.minecraft.village.TradedItem cost =
                new net.minecraft.village.TradedItem(net.minecraft.item.Items.EMERALD, t.emeraldCost());
            offers.add(new net.minecraft.village.TradeOffer(cost, t.item().copy(), 99, 0, 0f));
        }
        merchant.setCustomer(player);
        merchant.sendOffers(player, merchant.getDisplayName(), 1);
    }

    private void walkTo(ServerPlayerEntity player, int tx, int tz) {
        if (walkers.containsKey(player.getUuid())) return; // already walking
        // FIX 1: validate tile is inside the scene floor footprint (local coords from TileRaycast).
        if (tx < 0 || tx >= CodeSceneBuilder.FLOOR_WIDTH || tz < 0 || tz >= CodeSceneBuilder.FLOOR_DEPTH) return;
        // Convert local scene tile → world coords by adding the scene origin.
        double sx = player.getX(), sy = player.getY(), sz = player.getZ();
        double ex = origin.getX() + tx + 0.5, ez = origin.getZ() + tz + 0.5;
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
            com.crackedgames.craftics.combat.CinematicBroadcast.broadcastPlayerPositionToOthers(fp);
        };
        EntityWalker walker = new EntityWalker(mover, sx, sy, sz, ex, layout.spawnY(), ez, ticks,
            () -> walkers.remove(fp.getUuid()));
        walkers.put(player.getUuid(), walker);
    }

    private void tick() {
        for (EntityWalker w : new ArrayList<>(walkers.values())) {
            w.tick();
        }
    }

    /** Remove one player from the scene: restore their control, teleport them to the hub,
     *  and tear the scene down if they were the last member. Shared by leave + disconnect.
     *  Pass sendClient=false for a disconnecting player (their client is already gone). */
    private void removePlayer(UUID playerUuid) {
        removePlayer(playerUuid, true);
    }

    private void removePlayer(UUID playerUuid, boolean sendClient) {
        if (!members.remove(playerUuid)) return;
        walkers.remove(playerUuid);
        ServerPlayerEntity p = world.getServer().getPlayerManager().getPlayer(playerUuid);
        if (sendClient && p != null) {
            ServerPlayNetworking.send(p, new ExitEventCinematicPayload());
            ServerPlayNetworking.send(p, new SceneStatePayload(false, 0, 0, 0, 0, 0));
            CrafticsSavedData data = CrafticsSavedData.get(world);
            BlockPos hub = data.getHubTeleportPos(playerUuid);
            if (hub != null) {
                // Land on the highest solid block in the hub column so a stale/low stored hub Y
                // (or a hollow island interior) can't drop the player under the island into the
                // void. Mirrors combat's teleportToHub (CombatManager.hubLandingY usage).
                int landY = com.crackedgames.craftics.CrafticsMod.hubLandingY(
                    world, hub.getX(), hub.getZ(), hub.getY());
                int y = landY != Integer.MIN_VALUE ? landY : hub.getY();
                p.requestTeleport(hub.getX() + 0.5, y, hub.getZ() + 0.5);
            }
        }
        // Reconcile a mid-trade leave: a player holding a TRADE booth got their whole emerald
        // balance handed to them as items on open, so close the trade to fold those items back
        // into saved data and free/promote the booth. Barter booths hold no emerald items.
        ServerPlayerEntity leaving = world.getServer().getPlayerManager().getPlayer(playerUuid);
        if (leaving != null && playerBooth.containsKey(playerUuid) && !"barter_station".equals(sceneName)) {
            closeTrade(leaving); // reconciles emeralds + frees booth + promotes queue
        } else {
            playerBooth.remove(playerUuid);
            barterThresholds.remove(playerUuid);
        }
        if (members.isEmpty()) teardown();
    }

    private void teardown() {
        // Discard NPC entities.
        for (int id : npcEntityIds) {
            Entity e = world.getEntityById(id);
            if (e != null) e.discard();
        }
        npcEntityIds.clear();
        boothNpcIds.clear();
        // Restore overwritten blocks (reverse insertion order is unnecessary; states are independent).
        for (Map.Entry<BlockPos, BlockState> e : snapshot.entrySet()) {
            world.setBlockState(e.getKey(), e.getValue(), net.minecraft.block.Block.FORCE_STATE);
        }
        snapshot.clear();
        // Release forced chunks.
        for (ChunkPos cp : forcedChunks) world.setChunkForced(cp.x, cp.z, false);
        forcedChunks.clear();
        walkers.clear();
        boothCustomer.clear();
        boothQueue.clear();
        playerBooth.clear();
        barterThresholds.clear();
        members.clear();
        INSTANCES.remove(key);
    }
}
