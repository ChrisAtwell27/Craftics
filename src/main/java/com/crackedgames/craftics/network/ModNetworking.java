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

        // Register S2C payload types
        PayloadTypeRegistry.playS2C().register(EnterCombatPayload.ID, EnterCombatPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(CombatEventPayload.ID, CombatEventPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ExitCombatPayload.ID, ExitCombatPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(CombatSyncPayload.ID, CombatSyncPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(VictoryChoicePayload.ID, VictoryChoicePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(TraderOfferPayload.ID, TraderOfferPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(LevelUpPayload.ID, LevelUpPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(PlayerStatsSyncPayload.ID, PlayerStatsSyncPayload.CODEC);

        // Handle "start level" — starts a biome run by biome ID
        ServerPlayNetworking.registerGlobalReceiver(StartLevelPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            ServerWorld world = (ServerWorld) player.getEntityWorld();
            String biomeId = payload.biomeId();

            CrafticsSavedData data = CrafticsSavedData.get(world);
            data.claimLegacyData(player.getUuid());
            data.loadPlayerIntoLegacy(player.getUuid());

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

            // Build arena, teleport, start combat
            GridArena arena = ArenaBuilder.build(world, levelDef);
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
            CombatManager.get(player).setEventManager(em);
            for (java.util.UUID memberUuid : partyMembers) {
                if (!memberUuid.equals(player.getUuid())) {
                    CombatManager.get(memberUuid).setEventManager(em);
                }
            }

            CrafticsMod.LOGGER.info("Player {} started {} (biome {}, level {})",
                player.getName().getString(), levelDef.getName(), biome.biomeId, levelIndex + 1);
        });

        // Handle combat actions
        ServerPlayNetworking.registerGlobalReceiver(CombatActionPayload.ID, (payload, context) -> {
            CombatManager.get(context.player()).handleAction(payload);
        });

        // Handle post-level choice (Go Home vs Continue)
        ServerPlayNetworking.registerGlobalReceiver(PostLevelChoicePayload.ID, (payload, context) -> {
            CombatManager.get(context.player()).handlePostLevelChoice(context.player(), payload.goHome());
        });

        // Handle trader buy request
        ServerPlayNetworking.registerGlobalReceiver(TraderBuyPayload.ID, (payload, context) -> {
            CombatManager.get(context.player()).handleTraderBuy(context.player(), payload.tradeIndex());
        });

        // Handle trader done — proceed to next level
        ServerPlayNetworking.registerGlobalReceiver(TraderDonePayload.ID, (payload, context) -> {
            CombatManager.get(context.player()).handleTraderDone(context.player());
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
                    CrafticsSavedData data = CrafticsSavedData.get(overworld);
                    ServerPlayNetworking.send(player, new PlayerStatsSyncPayload(
                        ps.level, ps.unspentPoints, statData.toString(), data.emeralds
                    ));
                }
            }
        });
    }
}
