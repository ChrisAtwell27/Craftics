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
        PayloadTypeRegistry.playC2S().register(GameOverAckPayload.ID, GameOverAckPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(TraderBuyPayload.ID, TraderBuyPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(TraderDonePayload.ID, TraderDonePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(StatChoicePayload.ID, StatChoicePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(AffinityChoicePayload.ID, AffinityChoicePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(RespecPayload.ID, RespecPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(AffinityRespecPayload.ID, AffinityRespecPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(MoveSlotShiftPayload.ID, MoveSlotShiftPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(LeadCommandPayload.ID, LeadCommandPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(LeadSelectPayload.ID, LeadSelectPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ClearPartyPayload.ID, ClearPartyPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(MountAbilityPayload.ID, MountAbilityPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(DialogueChoicePayload.ID, DialogueChoicePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(EnterScenePayload.ID, EnterScenePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(SceneClickPayload.ID, SceneClickPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(LeaveScenePayload.ID, LeaveScenePayload.CODEC);

        // Register S2C payload types
        PayloadTypeRegistry.playS2C().register(EnterCombatPayload.ID, EnterCombatPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(CombatEventPayload.ID, CombatEventPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ExitCombatPayload.ID, ExitCombatPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(CombatSyncPayload.ID, CombatSyncPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(VictoryChoicePayload.ID, VictoryChoicePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(GameOverItemsPayload.ID, GameOverItemsPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(TraderOfferPayload.ID, TraderOfferPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(LevelUpPayload.ID, LevelUpPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(PlayerStatsSyncPayload.ID, PlayerStatsSyncPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(TileSetPayload.ID, TileSetPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(TileFlashPayload.ID, TileFlashPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(TeammateHoverPayload.ID, TeammateHoverPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(DialoguePayload.ID, DialoguePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(EnterEventCinematicPayload.ID, EnterEventCinematicPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ExitEventCinematicPayload.ID, ExitEventCinematicPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SceneStatePayload.ID, SceneStatePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(AchievementUnlockPayload.ID, AchievementUnlockPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(GuideBookSyncPayload.ID, GuideBookSyncPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(AddonBonusSyncPayload.ID, AddonBonusSyncPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(LoadingScreenPayload.ID, LoadingScreenPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ScoreboardSyncPayload.ID, ScoreboardSyncPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(VfxClientPayload.ID, VfxClientPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(PartyMobsSyncPayload.ID, PartyMobsSyncPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(MusicSyncPayload.ID, MusicSyncPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(BarterContextPayload.ID, BarterContextPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(RewardRevealPayload.ID, RewardRevealPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(RunInvitePayload.ID, RunInvitePayload.CODEC);

        // Register C2S hover update
        PayloadTypeRegistry.playC2S().register(HoverUpdatePayload.ID, HoverUpdatePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(RunInviteResponsePayload.ID, RunInviteResponsePayload.CODEC);

        // Handle "start level" - starts a biome run by biome ID
        ServerPlayNetworking.registerGlobalReceiver(StartLevelPayload.ID, (payload, context) -> {
            // Any party member may start a run now. The lobby validates, invites the rest
            // of the island, and begins the run once everyone has answered (see
            // RunInviteManager). The owner has no special status - they're a regular joiner.
            com.crackedgames.craftics.combat.RunInviteManager.requestStart(
                context.player(), payload.biomeId());
        });

        // Handle a player's answer to a run-join invite (the Yes/No popup)
        ServerPlayNetworking.registerGlobalReceiver(RunInviteResponsePayload.ID, (payload, context) -> {
            com.crackedgames.craftics.combat.RunInviteManager.respond(
                context.player().getEntityWorld().getServer(),
                context.player().getUuid(), payload.accept() != 0);
        });

        // Handle combat actions - route to party leader's CombatManager with sender validation
        ServerPlayNetworking.registerGlobalReceiver(CombatActionPayload.ID, (payload, context) -> {
            CombatManager.getActiveCombat(context.player().getUuid())
                .handleAction(payload, context.player().getUuid());
        });

        // Handle post-level choice (Go Home vs Continue) - route to party leader
        ServerPlayNetworking.registerGlobalReceiver(PostLevelChoicePayload.ID, (payload, context) -> {
            CombatManager.getActiveCombat(context.player().getUuid())
                .handlePostLevelChoice(context.player(), payload.goHome());
        });

        // Handle game-over coin-flip ack - route to party leader's CombatManager
        ServerPlayNetworking.registerGlobalReceiver(GameOverAckPayload.ID, (payload, context) -> {
            CombatManager active = CombatManager.getActiveCombat(context.player().getUuid());
            if (active != null) active.handleGameOverAck(context.player());
        });

        // Handle trader buy request - route to party leader
        ServerPlayNetworking.registerGlobalReceiver(TraderBuyPayload.ID, (payload, context) -> {
            CombatManager.getActiveCombat(context.player().getUuid())
                .handleTraderBuy(context.player(), payload.tradeIndex());
        });

        // Handle trader done - proceed to next level - route to party leader
        ServerPlayNetworking.registerGlobalReceiver(TraderDonePayload.ID, (payload, context) -> {
            CombatManager.getActiveCombat(context.player().getUuid())
                .handleTraderDone(context.player());
        });

        // Handle dialogue choice - route to party leader's CombatManager
        ServerPlayNetworking.registerGlobalReceiver(DialogueChoicePayload.ID, (payload, context) -> {
            CombatManager.getActiveCombat(context.player().getUuid())
                .handleDialogueChoice(context.player(), payload.action());
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

        // Handle respec - refund and reallocate stat points (costs XP levels)
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

        // Mount ability: player presses the mount-ability key while riding a combat mount.
        ServerPlayNetworking.registerGlobalReceiver(MountAbilityPayload.ID, (payload, context) -> {
            ServerPlayerEntity p = context.player();
            context.player().getServer().execute(() -> {
                com.crackedgames.craftics.combat.CombatManager cm =
                    com.crackedgames.craftics.combat.CombatManager.get(p);
                if (cm != null && cm.isActive()) {
                    cm.handleMountAbility(p.getUuid());
                }
            });
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

        // Handle affinity respec - allocate unspent affinity points and/or refund
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

            // Net new allocations draw from the level-derived unspent affinity pool -
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
            // Respeccing into 1-point-in-every-affinity must be able to unlock Jack of All
            // Trades; normal allocation checks this, the respec path previously did not.
            com.crackedgames.craftics.achievement.AchievementManager.checkProgression(player);

            player.sendMessage(net.minecraft.text.Text.literal(
                "§aAffinities respecced!" + (totalRefunded > 0
                    ? " §7(-" + totalRefunded + " XP level" + (totalRefunded != 1 ? "s" : "") + ")"
                    : "")), false);
        });

        // Handle scene enter/click/leave - dispatch to SceneController
        ServerPlayNetworking.registerGlobalReceiver(EnterScenePayload.ID, (payload, context) -> {
            com.crackedgames.craftics.scene.SceneController.handleEnter(
                context.player(), payload.sceneName());
        });
        ServerPlayNetworking.registerGlobalReceiver(SceneClickPayload.ID, (payload, context) -> {
            com.crackedgames.craftics.scene.SceneController.handleClick(
                context.player(), payload.tx(), payload.tz());
        });
        ServerPlayNetworking.registerGlobalReceiver(LeaveScenePayload.ID, (payload, context) -> {
            com.crackedgames.craftics.scene.SceneController.handleLeave(context.player());
        });

        // Handle hover updates - relay to party members
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
     * respec flow - notably the admin {@code set_level}/{@code set_stat}/
     * {@code reset_stats} commands - so the respec menus always show live data.
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
