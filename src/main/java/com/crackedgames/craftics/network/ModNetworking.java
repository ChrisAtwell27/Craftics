package com.crackedgames.craftics.network;

import com.crackedgames.craftics.CrafticsMod;
import com.crackedgames.craftics.combat.CombatManager;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.level.*;
import com.crackedgames.craftics.world.CrafticsSavedData;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

public class ModNetworking {

    public static void registerServer() {
        // Register C2S payload types
        PayloadTypeRegistry.playC2S().register(StartLevelPayload.ID, StartLevelPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(CombatActionPayload.ID, CombatActionPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(PostLevelChoicePayload.ID, PostLevelChoicePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(TraderBuyPayload.ID, TraderBuyPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(TraderDonePayload.ID, TraderDonePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(StatChoicePayload.ID, StatChoicePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(AffinityChoicePayload.ID, AffinityChoicePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(EventChoicePayload.ID, EventChoicePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(RespecPayload.ID, RespecPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(AffinityRespecPayload.ID, AffinityRespecPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(MoveSlotShiftPayload.ID, MoveSlotShiftPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(LeadCommandPayload.ID, LeadCommandPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(LeadSelectPayload.ID, LeadSelectPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ClearPartyPayload.ID, ClearPartyPayload.CODEC);

        // Register S2C payload types
        PayloadTypeRegistry.playS2C().register(EnterCombatPayload.ID, EnterCombatPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(CombatEventPayload.ID, CombatEventPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ExitCombatPayload.ID, ExitCombatPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(CombatSyncPayload.ID, CombatSyncPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(VictoryChoicePayload.ID, VictoryChoicePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(TraderOfferPayload.ID, TraderOfferPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(LevelUpPayload.ID, LevelUpPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(PlayerStatsSyncPayload.ID, PlayerStatsSyncPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(TileSetPayload.ID, TileSetPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(TeammateHoverPayload.ID, TeammateHoverPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(EventRoomPayload.ID, EventRoomPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(AchievementUnlockPayload.ID, AchievementUnlockPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(GuideBookSyncPayload.ID, GuideBookSyncPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(AddonBonusSyncPayload.ID, AddonBonusSyncPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(LoadingScreenPayload.ID, LoadingScreenPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ScoreboardSyncPayload.ID, ScoreboardSyncPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(VfxClientPayload.ID, VfxClientPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(PartyMobsSyncPayload.ID, PartyMobsSyncPayload.CODEC);

        // Register C2S hover update
        PayloadTypeRegistry.playC2S().register(HoverUpdatePayload.ID, HoverUpdatePayload.CODEC);

        // Handle "start level" — starts a biome run by biome ID
        ServerPlayNetworking.registerGlobalReceiver(StartLevelPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            ServerWorld world = (ServerWorld) player.getEntityWorld();
            String biomeId = payload.biomeId();

            CrafticsSavedData data = CrafticsSavedData.get(world);
            data.claimLegacyData(player.getUuid());
            CrafticsSavedData.PlayerData pd = data.getPlayerData(player.getUuid());

            // Only the party leader may start a biome run — otherwise two members
            // racing the Enter button would spawn parallel CombatManager instances
            // and corrupt PARTY_COMBAT_LEADER routing.
            com.crackedgames.craftics.world.Party playerParty = data.getPlayerParty(player.getUuid());
            if (playerParty != null && !playerParty.isLeader(player.getUuid())) {
                player.sendMessage(net.minecraft.text.Text.literal(
                    "\u00a7cOnly the party leader can start a biome run."), false);
                ServerPlayNetworking.send(player, new ExitCombatPayload(false));
                return;
            }

            // Prevent the leader from double-starting (e.g. clicking Enter twice
            // while an earlier transition is still in flight).
            if (CombatManager.get(player).isActive()) {
                ServerPlayNetworking.send(player, new ExitCombatPayload(false));
                return;
            }

            // Require a personal world before starting biome runs
            java.util.UUID effectiveOwner = data.getEffectiveWorldOwner(player.getUuid());
            if (!data.hasPersonalWorld(effectiveOwner)) {
                player.sendMessage(net.minecraft.text.Text.literal(
                    "\u00a7cCreate a personal world first: \u00a7e/craftics world create"), false);
                ServerPlayNetworking.send(player, new ExitCombatPayload(false));
                return;
            }

            // Find the biome template by ID
            BiomeTemplate biome = null;
            for (BiomeTemplate b : BiomeRegistry.getAllBiomes()) {
                if (b.biomeId.equals(biomeId)) {
                    biome = b;
                    break;
                }
            }
            if (biome == null) {
                CrafticsMod.LOGGER.warn("No biome found for ID '{}'", biomeId);
                ServerPlayNetworking.send(player, new ExitCombatPayload(false));
                return;
            }

            // Check if this biome is unlocked (using path order, not registry order)
            pd.initBranchIfNeeded();
            java.util.List<String> fullPath = BiomePath.getFullPath(Math.max(0, pd.branchChoice));
            int biomeOrder = fullPath.indexOf(biomeId) + 1; // 1-based
            if (biomeOrder <= 0 || biomeOrder > pd.highestBiomeUnlocked) {
                CrafticsMod.LOGGER.warn("Player {} tried to start locked biome {} (unlocked={}, needed={})",
                    player.getName().getString(), biomeId, pd.highestBiomeUnlocked, biomeOrder);
                ServerPlayNetworking.send(player, new ExitCombatPayload(false));
                return;
            }

            // Start or resume biome run
            int levelIndex;
            if (pd.isInBiomeRun() && pd.activeBiomeId.equals(biome.biomeId)) {
                // Resuming — continue from where they left off
                levelIndex = pd.activeBiomeLevelIndex;
            } else {
                // New run — start from beginning
                pd.startBiomeRun(biome.biomeId);
                pd.discoverBiome(biome.biomeId);
                data.markDirty();
                levelIndex = 0;
            }

            int globalLevel = biome.startLevel + levelIndex;
            // HP-per-level scaling is an island-owner setting. For a guest entering
            // a party-leader's world, the leader's flag applies; for a solo start,
            // it's the player's own. getEffectiveWorldOwner handles both.
            java.util.UUID hpScalingOwner = data.getEffectiveWorldOwner(player.getUuid());
            boolean ownerHpScale = data.getPlayerData(hpScalingOwner).scaleHpPerLevelEnabled;
            LevelDefinition levelDef = LevelRegistry.get(globalLevel, pd.branchChoice, ownerHpScale);
            if (levelDef == null) {
                CrafticsMod.LOGGER.warn("No definition for level {}", globalLevel);
                ServerPlayNetworking.send(player, new ExitCombatPayload(false));
                return;
            }

            // Build arena in the player's (or party leader's) world slot
            java.util.UUID worldOwner = data.getEffectiveWorldOwner(player.getUuid());
            GridArena arena = ArenaBuilder.build(world, levelDef, worldOwner);
            BlockPos startPos = arena.getPlayerStartBlockPos();
            BlockPos origin = arena.getOrigin();
            float cameraYaw = ArenaBuilder.consumePendingCameraYaw();

            player.requestTeleport(startPos.getX() + 0.5, startPos.getY(), startPos.getZ() + 0.5);

            // Collect following tamed pets from the hub before entering combat
            var hubPetSnapshots = com.crackedgames.craftics.combat.HubPetCollector
                .collectFollowingPets(world, player, data);
            CombatManager.get(player).setHubPetSnapshots(hubPetSnapshots);

            ServerPlayNetworking.send(player, new EnterCombatPayload(
                origin.getX(), origin.getY(), origin.getZ(),
                arena.getWidth(), arena.getHeight(), cameraYaw
            ));

            CombatManager.get(player).startCombat(player, arena, levelDef);

            // Create EventManager for this party/solo run and assign to CombatManager
            java.util.List<java.util.UUID> partyMembers = data.getPartyMemberUuids(player.getUuid());
            com.crackedgames.craftics.combat.EventManager em = new com.crackedgames.craftics.combat.EventManager(partyMembers);
            CombatManager leaderCm = CombatManager.get(player);
            leaderCm.setEventManager(em);
            leaderCm.setWorldOwnerUuid(worldOwner);

            // Register leader as party participant
            leaderCm.addPartyMember(player);

            // Teleport and register all online party members into the same combat.
            //
            // Each member gets a grid-space desired tile fanned out from the leader's
            // spawn, clamped to the arena bounds, then resolved to the nearest free
            // walkable tile. This mirrors CombatManager.transitionPartyToArena and
            // guarantees players 3+ never land outside the grid (the previous formula
            // added raw world-Z offsets with no bounds check).
            com.crackedgames.craftics.core.GridPos leaderGrid = arena.getPlayerGridPos();
            java.util.Set<com.crackedgames.craftics.core.GridPos> reservedSpawns = new java.util.HashSet<>();
            reservedSpawns.add(leaderGrid);
            int memberIndex = 0;
            for (java.util.UUID memberUuid : partyMembers) {
                if (memberUuid.equals(player.getUuid())) continue;
                ServerPlayerEntity member = world.getServer().getPlayerManager().getPlayer(memberUuid);
                if (member != null) {
                    // memberIndex=0 → +1, =1 → -1, =2 → +2, =3 → -2, ...
                    int dx = (memberIndex % 2 == 0) ? ((memberIndex / 2) + 1) : -((memberIndex / 2) + 1);
                    memberIndex++;
                    int cx = Math.max(0, Math.min(arena.getWidth() - 1, leaderGrid.x() + dx));
                    int cz = Math.max(0, Math.min(arena.getHeight() - 1, leaderGrid.z()));
                    com.crackedgames.craftics.core.GridPos desired = new com.crackedgames.craftics.core.GridPos(cx, cz);
                    com.crackedgames.craftics.core.GridPos chosen =
                        com.crackedgames.craftics.combat.CombatManager
                            .findNearestWalkableUnreserved(arena, desired, reservedSpawns);
                    if (chosen == null) chosen = leaderGrid;
                    reservedSpawns.add(chosen);

                    BlockPos memberBlockPos = arena.gridToBlockPos(chosen);
                    member.requestTeleport(
                        memberBlockPos.getX() + 0.5,
                        memberBlockPos.getY(),
                        memberBlockPos.getZ() + 0.5);
                    member.changeGameMode(net.minecraft.world.GameMode.ADVENTURE);
                    ServerPlayNetworking.send(member, new EnterCombatPayload(
                        origin.getX(), origin.getY(), origin.getZ(),
                        arena.getWidth(), arena.getHeight(), cameraYaw
                    ));
                    leaderCm.addPartyMember(member);
                    // Move item is persistent \u2014 server tick keeps it stocked.
                    com.crackedgames.craftics.item.MoveSlotManager.enforce(member);
                    // Also set EventManager on their CombatManager for between-level coordination
                    CombatManager.get(memberUuid).setEventManager(em);
                    CrafticsMod.LOGGER.info("Party member {} joined combat (biome {}, level {})",
                        member.getName().getString(), biome.biomeId, levelIndex + 1);
                }
            }

            CrafticsMod.LOGGER.info("Player {} started {} (biome {}, level {}, party size {})",
                player.getName().getString(), levelDef.getName(), biome.biomeId, levelIndex + 1,
                partyMembers.size());
        });

        // Handle combat actions — route to party leader's CombatManager with sender validation
        ServerPlayNetworking.registerGlobalReceiver(CombatActionPayload.ID, (payload, context) -> {
            CombatManager.getActiveCombat(context.player().getUuid())
                .handleAction(payload, context.player().getUuid());
        });

        // Handle post-level choice (Go Home vs Continue) — route to party leader
        ServerPlayNetworking.registerGlobalReceiver(PostLevelChoicePayload.ID, (payload, context) -> {
            CombatManager.getActiveCombat(context.player().getUuid())
                .handlePostLevelChoice(context.player(), payload.goHome());
        });

        // Handle trader buy request — route to party leader
        ServerPlayNetworking.registerGlobalReceiver(TraderBuyPayload.ID, (payload, context) -> {
            CombatManager.getActiveCombat(context.player().getUuid())
                .handleTraderBuy(context.player(), payload.tradeIndex());
        });

        // Handle trader done — proceed to next level — route to party leader
        ServerPlayNetworking.registerGlobalReceiver(TraderDonePayload.ID, (payload, context) -> {
            CombatManager.getActiveCombat(context.player().getUuid())
                .handleTraderDone(context.player());
        });

        // Handle stat choice from level-up screen
        ServerPlayNetworking.registerGlobalReceiver(StatChoicePayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            com.crackedgames.craftics.combat.PlayerProgression.Stat[] stats =
                com.crackedgames.craftics.combat.PlayerProgression.Stat.values();
            int ordinal = payload.statOrdinal();
            if (ordinal >= 0 && ordinal < stats.length) {
                ServerWorld overworld = (ServerWorld) player.getEntityWorld();
                com.crackedgames.craftics.combat.PlayerProgression progression =
                    com.crackedgames.craftics.combat.PlayerProgression.get(overworld);
                if (progression.allocateStat(player, stats[ordinal])) {
                    player.sendMessage(net.minecraft.text.Text.literal(
                        "§a+" + stats[ordinal].displayName + "! §7(Now " +
                        progression.getStats(player).getEffective(stats[ordinal]) + ")"), false);

                    // Re-sync updated stats to client so CombatState stays current
                    com.crackedgames.craftics.combat.PlayerProgression.PlayerStats ps =
                        progression.getStats(player);
                    StringBuilder statData = new StringBuilder();
                    for (com.crackedgames.craftics.combat.PlayerProgression.Stat s : stats) {
                        if (statData.length() > 0) statData.append(":");
                        statData.append(ps.getPoints(s));
                    }
                    // Build affinity data string
                    StringBuilder affData = new StringBuilder();
                    for (com.crackedgames.craftics.combat.PlayerProgression.Affinity a : com.crackedgames.craftics.combat.PlayerProgression.Affinity.values()) {
                        if (affData.length() > 0) affData.append(":");
                        affData.append(ps.getAffinityPoints(a));
                    }
                    CrafticsSavedData data = CrafticsSavedData.get(overworld);
                    ServerPlayNetworking.send(player, new PlayerStatsSyncPayload(
                        ps.level, ps.unspentPoints, statData.toString(), data.getPlayerData(player.getUuid()).emeralds, affData.toString()
                    ));

                    // Mark that player now needs to pick an affinity
                    ps.pendingAffinityChoice = true;
                    progression.saveStats(player);
                }
            }
        });

        // Handle affinity choice from level-up screen (second step)
        ServerPlayNetworking.registerGlobalReceiver(AffinityChoicePayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            com.crackedgames.craftics.combat.PlayerProgression.Affinity[] affinities =
                com.crackedgames.craftics.combat.PlayerProgression.Affinity.values();
            int ordinal = payload.affinityOrdinal();
            if (ordinal >= 0 && ordinal < affinities.length) {
                ServerWorld overworld = (ServerWorld) player.getEntityWorld();
                com.crackedgames.craftics.combat.PlayerProgression progression =
                    com.crackedgames.craftics.combat.PlayerProgression.get(overworld);
                com.crackedgames.craftics.combat.PlayerProgression.PlayerStats ps =
                    progression.getStats(player);
                if (ps.pendingAffinityChoice || ps.canAllocateAffinity()) {
                    ps.allocateAffinity(affinities[ordinal]);
                    progression.saveStats(player);
                    player.sendMessage(net.minecraft.text.Text.literal(
                        "§6+" + affinities[ordinal].displayName + " affinity! §7(Now +" +
                        ps.getAffinityPoints(affinities[ordinal]) + ")"), false);
                    com.crackedgames.craftics.achievement.AchievementManager.checkProgression(player);

                    // Re-sync stats (including affinity) to client
                    com.crackedgames.craftics.combat.PlayerProgression.Stat[] stats2 =
                        com.crackedgames.craftics.combat.PlayerProgression.Stat.values();
                    StringBuilder statData2 = new StringBuilder();
                    for (com.crackedgames.craftics.combat.PlayerProgression.Stat s : stats2) {
                        if (statData2.length() > 0) statData2.append(":");
                        statData2.append(ps.getPoints(s));
                    }
                    StringBuilder affData2 = new StringBuilder();
                    for (com.crackedgames.craftics.combat.PlayerProgression.Affinity a : affinities) {
                        if (affData2.length() > 0) affData2.append(":");
                        affData2.append(ps.getAffinityPoints(a));
                    }
                    CrafticsSavedData data2 = CrafticsSavedData.get(overworld);
                    ServerPlayNetworking.send(player, new PlayerStatsSyncPayload(
                        ps.level, ps.unspentPoints, statData2.toString(), data2.getPlayerData(player.getUuid()).emeralds, affData2.toString()
                    ));
                }
            }
        });

        // Handle event room choice (shrine/traveler/vault)
        ServerPlayNetworking.registerGlobalReceiver(EventChoicePayload.ID, (payload, context) -> {
            CombatManager.getActiveCombat(context.player().getUuid())
                .handleEventChoice(context.player(), payload.choiceIndex());
        });

        // Handle respec — refund and reallocate stat points (costs XP levels)
        ServerPlayNetworking.registerGlobalReceiver(RespecPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            ServerWorld overworld = (ServerWorld) player.getEntityWorld();
            com.crackedgames.craftics.combat.PlayerProgression progression =
                com.crackedgames.craftics.combat.PlayerProgression.get(overworld);
            com.crackedgames.craftics.combat.PlayerProgression.PlayerStats ps =
                progression.getStats(player);
            com.crackedgames.craftics.combat.PlayerProgression.Stat[] stats =
                com.crackedgames.craftics.combat.PlayerProgression.Stat.values();

            // Parse deltas
            String[] parts = payload.statDeltas().split(":");
            if (parts.length != stats.length) return;

            int[] deltas = new int[stats.length];
            int totalRefunded = 0;
            int totalAllocated = 0;
            for (int i = 0; i < stats.length; i++) {
                try {
                    deltas[i] = Integer.parseInt(parts[i]);
                } catch (NumberFormatException e) { return; }
                if (deltas[i] < 0) {
                    totalRefunded += -deltas[i];
                    // Ensure player actually has enough points in this stat to refund
                    if (ps.getPoints(stats[i]) + deltas[i] < 0) return;
                } else if (deltas[i] > 0) {
                    totalAllocated += deltas[i];
                }
            }

            // Validate
            if (totalRefunded == 0 && totalAllocated == 0) return;

            // Allocations beyond refunded points come from unspent pool
            int unspentNeeded = totalAllocated - totalRefunded;
            if (unspentNeeded > 0 && ps.unspentPoints < unspentNeeded) return;

            if (player.experienceLevel < totalRefunded) {
                player.sendMessage(net.minecraft.text.Text.literal(
                    "\u00a7cNot enough XP levels! Need " + totalRefunded + ", have " + player.experienceLevel), false);
                return;
            }

            // Apply: deduct XP levels, apply deltas, adjust unspent points
            if (totalRefunded > 0) {
                player.addExperienceLevels(-totalRefunded);
            }
            for (int i = 0; i < stats.length; i++) {
                if (deltas[i] != 0) {
                    ps.setPoints(stats[i], ps.getPoints(stats[i]) + deltas[i]);
                }
            }
            // unspentNeeded > 0 means spending from pool, < 0 means returning to pool
            ps.unspentPoints -= unspentNeeded;
            progression.saveStats(player);

            // Re-sync stats to client
            StringBuilder statData = new StringBuilder();
            for (com.crackedgames.craftics.combat.PlayerProgression.Stat s : stats) {
                if (statData.length() > 0) statData.append(":");
                statData.append(ps.getPoints(s));
            }
            StringBuilder affData = new StringBuilder();
            for (com.crackedgames.craftics.combat.PlayerProgression.Affinity a : com.crackedgames.craftics.combat.PlayerProgression.Affinity.values()) {
                if (affData.length() > 0) affData.append(":");
                affData.append(ps.getAffinityPoints(a));
            }
            CrafticsSavedData data = CrafticsSavedData.get(overworld);
            ServerPlayNetworking.send(player, new PlayerStatsSyncPayload(
                ps.level, ps.unspentPoints, statData.toString(), data.getPlayerData(player.getUuid()).emeralds, affData.toString()
            ));

            player.sendMessage(net.minecraft.text.Text.literal(
                "\u00a7aStats respecced! \u00a77(-" + totalRefunded + " XP level" + (totalRefunded != 1 ? "s" : "") + ")"), false);
        });

        // Rotate the Move item's locked hotbar slot one step left or right.
        ServerPlayNetworking.registerGlobalReceiver(MoveSlotShiftPayload.ID, (payload, context) -> {
            ServerPlayerEntity p = context.player();
            context.player().getServer().execute(() ->
                com.crackedgames.craftics.item.MoveSlotManager.shift(p, payload.direction()));
        });

        // Clear every mob from the player's battle party (hub-only keybind).
        ServerPlayNetworking.registerGlobalReceiver(ClearPartyPayload.ID, (payload, context) -> {
            ServerPlayerEntity p = context.player();
            context.player().getServer().execute(() ->
                com.crackedgames.craftics.combat.PartyMobs.clearParty(p));
        });

        // Lead command: player commands an ally to move/attack using a Lead.
        ServerPlayNetworking.registerGlobalReceiver(LeadCommandPayload.ID, (payload, context) -> {
            ServerPlayerEntity p = context.player();
            context.player().getServer().execute(() -> {
                com.crackedgames.craftics.combat.CombatManager cm =
                    com.crackedgames.craftics.combat.CombatManager.get(p);
                if (cm != null && cm.isActive()) {
                    cm.handleLeadCommand(payload.allyEntityId(),
                        payload.targetX(), payload.targetZ(), payload.targetEntityId());
                }
            });
        });

        // Lead selection: tell the server to glow the picked ally so it shows
        // to everyone in the party (client-side setGlowing gets overwritten by
        // the data tracker sync). -1 clears the selection's glow.
        ServerPlayNetworking.registerGlobalReceiver(LeadSelectPayload.ID, (payload, context) -> {
            ServerPlayerEntity p = context.player();
            context.player().getServer().execute(() -> {
                com.crackedgames.craftics.combat.CombatManager cm =
                    com.crackedgames.craftics.combat.CombatManager.get(p);
                if (cm != null && cm.isActive()) {
                    cm.handleLeadSelect(payload.allyEntityId());
                }
            });
        });

        // Handle affinity respec — allocate unspent affinity points and/or refund
        // allocated ones. Refunds cost 1 XP level each; allocating from the
        // level-derived unspent pool is free. Mirrors the stat respec.
        ServerPlayNetworking.registerGlobalReceiver(AffinityRespecPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            ServerWorld overworld = (ServerWorld) player.getEntityWorld();
            com.crackedgames.craftics.combat.PlayerProgression progression =
                com.crackedgames.craftics.combat.PlayerProgression.get(overworld);
            com.crackedgames.craftics.combat.PlayerProgression.PlayerStats ps =
                progression.getStats(player);
            com.crackedgames.craftics.combat.PlayerProgression.Affinity[] affinities =
                com.crackedgames.craftics.combat.PlayerProgression.Affinity.values();

            // Parse deltas
            String[] parts = payload.affinityDeltas().split(":");
            if (parts.length != affinities.length) return;

            int[] deltas = new int[affinities.length];
            int totalRefunded = 0;
            int totalAllocated = 0;
            for (int i = 0; i < affinities.length; i++) {
                try {
                    deltas[i] = Integer.parseInt(parts[i]);
                } catch (NumberFormatException e) { return; }
                if (deltas[i] < 0) {
                    totalRefunded += -deltas[i];
                    // Ensure the player actually has enough points to refund here
                    if (ps.getAffinityPoints(affinities[i]) + deltas[i] < 0) return;
                } else if (deltas[i] > 0) {
                    totalAllocated += deltas[i];
                }
            }

            if (totalRefunded == 0 && totalAllocated == 0) return;

            // Net new allocations draw from the level-derived unspent affinity pool —
            // this is what lets force-given levels and older saves be spent here.
            int unspentAffinity = Math.max(0, ps.expectedAffinityPoints() - ps.getTotalAffinityPoints());
            int unspentNeeded = totalAllocated - totalRefunded;
            if (unspentNeeded > 0 && unspentAffinity < unspentNeeded) return;

            if (player.experienceLevel < totalRefunded) {
                player.sendMessage(net.minecraft.text.Text.literal(
                    "§cNot enough XP levels! Need " + totalRefunded + ", have " + player.experienceLevel), false);
                return;
            }

            // Apply: deduct XP levels for refunds, then redistribute affinity points
            if (totalRefunded > 0) {
                player.addExperienceLevels(-totalRefunded);
            }
            for (int i = 0; i < affinities.length; i++) {
                if (deltas[i] != 0) {
                    ps.setAffinityPoints(affinities[i], ps.getAffinityPoints(affinities[i]) + deltas[i]);
                }
            }
            progression.saveStats(player);
            syncPlayerStats(player);

            player.sendMessage(net.minecraft.text.Text.literal(
                "§aAffinities respecced!" + (totalRefunded > 0
                    ? " §7(-" + totalRefunded + " XP level" + (totalRefunded != 1 ? "s" : "") + ")"
                    : "")), false);
        });

        // Handle hover updates — relay to party members
        ServerPlayNetworking.registerGlobalReceiver(HoverUpdatePayload.ID, (payload, context) -> {
            ServerPlayerEntity hoverPlayer = context.player();
            ServerWorld world = (ServerWorld) hoverPlayer.getEntityWorld();
            CrafticsSavedData data = CrafticsSavedData.get(world);
            com.crackedgames.craftics.world.Party party = data.getPlayerParty(hoverPlayer.getUuid());
            if (party == null) return;
            String senderName = hoverPlayer.getName().getString();
            for (java.util.UUID memberUuid : party.getMemberUuids()) {
                if (memberUuid.equals(hoverPlayer.getUuid())) continue;
                ServerPlayerEntity member = world.getServer().getPlayerManager().getPlayer(memberUuid);
                if (member != null) {
                    ServerPlayNetworking.send(member, new TeammateHoverPayload(
                        hoverPlayer.getUuid(), senderName, payload.gridX(), payload.gridZ()
                    ));
                }
            }
        });
    }

    /**
     * Builds the player's current progression stats (level, unspent points, stat
     * and affinity allocations, emeralds) and pushes them to their client. Call
     * after anything that changes a player's stats outside the normal level-up /
     * respec flow — notably the admin {@code set_level}/{@code set_stat}/
     * {@code reset_stats} commands — so the respec menus always show live data.
     */
    public static void syncPlayerStats(ServerPlayerEntity player) {
        ServerWorld overworld = player.getServer().getOverworld();
        com.crackedgames.craftics.combat.PlayerProgression progression =
            com.crackedgames.craftics.combat.PlayerProgression.get(overworld);
        com.crackedgames.craftics.combat.PlayerProgression.PlayerStats ps = progression.getStats(player);

        StringBuilder statData = new StringBuilder();
        for (com.crackedgames.craftics.combat.PlayerProgression.Stat s :
                com.crackedgames.craftics.combat.PlayerProgression.Stat.values()) {
            if (statData.length() > 0) statData.append(":");
            statData.append(ps.getPoints(s));
        }
        StringBuilder affData = new StringBuilder();
        for (com.crackedgames.craftics.combat.PlayerProgression.Affinity a :
                com.crackedgames.craftics.combat.PlayerProgression.Affinity.values()) {
            if (affData.length() > 0) affData.append(":");
            affData.append(ps.getAffinityPoints(a));
        }
        CrafticsSavedData data = CrafticsSavedData.get(overworld);
        ServerPlayNetworking.send(player, new PlayerStatsSyncPayload(
            ps.level, ps.unspentPoints, statData.toString(),
            data.getPlayerData(player.getUuid()).emeralds, affData.toString()));
    }
}
