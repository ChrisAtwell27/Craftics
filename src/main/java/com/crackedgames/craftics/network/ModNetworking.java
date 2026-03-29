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

        // Register C2S hover update
        PayloadTypeRegistry.playC2S().register(HoverUpdatePayload.ID, HoverUpdatePayload.CODEC);

        // Handle "start level" — starts a biome run by biome ID
        ServerPlayNetworking.registerGlobalReceiver(StartLevelPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            ServerWorld world = (ServerWorld) player.getEntityWorld();
            String biomeId = payload.biomeId();

            CrafticsSavedData data = CrafticsSavedData.get(world);
            data.claimLegacyData(player.getUuid());
            data.loadPlayerIntoLegacy(player.getUuid());

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
            java.util.List<String> fullPath = BiomePath.getFullPath(Math.max(0, data.branchChoice));
            int biomeOrder = fullPath.indexOf(biomeId) + 1; // 1-based
            if (biomeOrder <= 0 || biomeOrder > data.highestBiomeUnlocked) {
                CrafticsMod.LOGGER.warn("Player {} tried to start locked biome {} (unlocked={}, needed={})",
                    player.getName().getString(), biomeId, data.highestBiomeUnlocked, biomeOrder);
                ServerPlayNetworking.send(player, new ExitCombatPayload(false));
                return;
            }

            // Start or resume biome run
            int levelIndex;
            if (data.isInBiomeRun() && data.activeBiomeId.equals(biome.biomeId)) {
                // Resuming — continue from where they left off
                levelIndex = data.activeBiomeLevelIndex;
            } else {
                // New run — start from beginning
                data.startBiomeRun(biome.biomeId);
                data.discoverBiome(biome.biomeId);
                levelIndex = 0;
            }

            int globalLevel = biome.startLevel + levelIndex;
            LevelDefinition levelDef = LevelRegistry.get(globalLevel, data.branchChoice);
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

            ServerPlayNetworking.send(player, new EnterCombatPayload(
                origin.getX(), origin.getY(), origin.getZ(),
                arena.getWidth(), arena.getHeight(), cameraYaw
            ));

            CombatManager.get(player).startCombat(player, arena, levelDef);
            data.saveLegacyToPlayer(player.getUuid());

            // Create EventManager for this party/solo run and assign to CombatManager
            java.util.List<java.util.UUID> partyMembers = data.getPartyMemberUuids(player.getUuid());
            com.crackedgames.craftics.combat.EventManager em = new com.crackedgames.craftics.combat.EventManager(partyMembers);
            CombatManager leaderCm = CombatManager.get(player);
            leaderCm.setEventManager(em);
            leaderCm.setWorldOwnerUuid(worldOwner);

            // Register leader as party participant
            leaderCm.addPartyMember(player);

            // Teleport and register all online party members into the same combat
            for (java.util.UUID memberUuid : partyMembers) {
                if (memberUuid.equals(player.getUuid())) continue;
                ServerPlayerEntity member = world.getServer().getPlayerManager().getPlayer(memberUuid);
                if (member != null) {
                    member.requestTeleport(startPos.getX() + 0.5, startPos.getY(), startPos.getZ() + 0.5);
                    member.changeGameMode(net.minecraft.world.GameMode.ADVENTURE);
                    ServerPlayNetworking.send(member, new EnterCombatPayload(
                        origin.getX(), origin.getY(), origin.getZ(),
                        arena.getWidth(), arena.getHeight(), cameraYaw
                    ));
                    leaderCm.addPartyMember(member);
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

        // Handle combat actions — route to party leader's CombatManager
        ServerPlayNetworking.registerGlobalReceiver(CombatActionPayload.ID, (payload, context) -> {
            CombatManager.getActiveCombat(context.player().getUuid()).handleAction(payload);
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
                        ps.level, ps.unspentPoints, statData.toString(), data.emeralds, affData.toString()
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
                        ps.level, ps.unspentPoints, statData2.toString(), data2.emeralds, affData2.toString()
                    ));
                }
            }
        });

        // Handle event room choice (shrine/traveler/vault)
        ServerPlayNetworking.registerGlobalReceiver(EventChoicePayload.ID, (payload, context) -> {
            CombatManager.getActiveCombat(context.player().getUuid())
                .handleEventChoice(context.player(), payload.choiceIndex());
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
