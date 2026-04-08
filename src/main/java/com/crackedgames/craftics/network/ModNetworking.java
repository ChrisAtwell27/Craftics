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
            LevelDefinition levelDef = LevelRegistry.get(globalLevel, pd.branchChoice);
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

            // Teleport and register all online party members into the same combat
            int memberOffset = 0;
            for (java.util.UUID memberUuid : partyMembers) {
                if (memberUuid.equals(player.getUuid())) continue;
                ServerPlayerEntity member = world.getServer().getPlayerManager().getPlayer(memberUuid);
                if (member != null) {
                    // Offset each party member so they don't stack on the same tile
                    memberOffset++;
                    int offsetZ = (memberOffset % 2 == 1) ? memberOffset : -memberOffset;
                    member.requestTeleport(startPos.getX() + 0.5, startPos.getY(), startPos.getZ() + 0.5 + offsetZ);
                    member.changeGameMode(net.minecraft.world.GameMode.ADVENTURE);
                    ServerPlayNetworking.send(member, new EnterCombatPayload(
                        origin.getX(), origin.getY(), origin.getZ(),
                        arena.getWidth(), arena.getHeight(), cameraYaw
                    ));
                    leaderCm.addPartyMember(member);
                    // Give party member the Move feather (same as leader gets in startCombat)
                    net.minecraft.item.ItemStack displaced = member.getInventory().getStack(8);
                    if (!displaced.isEmpty()) {
                        int emptySlot = member.getInventory().getEmptySlot();
                        if (emptySlot != -1) {
                            member.getInventory().setStack(emptySlot, displaced);
                        }
                    }
                    net.minecraft.item.ItemStack moveItem = new net.minecraft.item.ItemStack(net.minecraft.item.Items.FEATHER);
                    moveItem.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME,
                        net.minecraft.text.Text.literal("\u00a7aMove"));
                    member.getInventory().setStack(8, moveItem);
                    member.getInventory().selectedSlot = 8;
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
                if (ps.pendingAffinityChoice) {
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
}
