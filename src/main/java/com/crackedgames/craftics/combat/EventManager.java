package com.crackedgames.craftics.combat;

import com.crackedgames.craftics.CrafticsMod;
import com.crackedgames.craftics.level.BiomeTemplate;
import com.crackedgames.craftics.level.LevelDefinition;
import com.crackedgames.craftics.network.*;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.*;

/**
 * Manages between-level events for a party (or solo player).
 * One instance per active biome run. Handles event rolling, vote collection,
 * per-player rewards, and coordination of trader/dig site interactions.
 */
public class EventManager {
    private final List<UUID> participantUuids;

    // Pending state (moved from CombatManager)
    private LevelDefinition pendingNextLevelDef;
    private BiomeTemplate pendingBiome;
    private boolean trialChamberPending = false;
    private LevelDefinition trialChamberLevelDef;
    private boolean lastFightWasTrial = false;
    private String forcedNextEvent = null;
    private boolean digSitePending = false;
    private TraderSystem.TraderOffer activeTraderOffer;
    private int traderEmeraldsGiven = 0;
    private net.minecraft.entity.passive.WanderingTraderEntity spawnedTrader;

    // Vote tracking
    private final Map<UUID, Boolean> pendingVotes = new HashMap<>();
    private EventType pendingEventType = null;
    private long voteStartTime = 0;
    private static final long VOTE_TIMEOUT_MS = 30_000;

    // Completion tracking (for trader/dig site — wait for all players)
    private final Set<UUID> completedPlayers = new HashSet<>();

    // Players who have finished their current fight (for post-level sync)
    private final Set<UUID> fightFinishedPlayers = new HashSet<>();

    public EventManager(List<UUID> participantUuids) {
        this.participantUuids = new ArrayList<>(participantUuids);
    }

    // === Getters/Setters for pending state ===

    public List<UUID> getParticipantUuids() { return Collections.unmodifiableList(participantUuids); }
    public LevelDefinition getPendingNextLevel() { return pendingNextLevelDef; }
    public void setPendingNextLevel(LevelDefinition def) { this.pendingNextLevelDef = def; }
    public BiomeTemplate getPendingBiome() { return pendingBiome; }
    public void setPendingBiome(BiomeTemplate biome) { this.pendingBiome = biome; }
    public boolean isTrialChamberPending() { return trialChamberPending; }
    public void setTrialChamberPending(boolean pending) { this.trialChamberPending = pending; }
    public LevelDefinition getTrialChamberLevelDef() { return trialChamberLevelDef; }
    public void setTrialChamberLevelDef(LevelDefinition def) { this.trialChamberLevelDef = def; }
    public boolean wasLastFightTrial() { return lastFightWasTrial; }
    public void setLastFightWasTrial(boolean was) { this.lastFightWasTrial = was; }
    public boolean isDigSitePending() { return digSitePending; }
    public void setDigSitePending(boolean pending) { this.digSitePending = pending; }
    public TraderSystem.TraderOffer getActiveTraderOffer() { return activeTraderOffer; }
    public void setActiveTraderOffer(TraderSystem.TraderOffer offer) { this.activeTraderOffer = offer; }
    public int getTraderEmeraldsGiven() { return traderEmeraldsGiven; }
    public void setTraderEmeraldsGiven(int amount) { this.traderEmeraldsGiven = amount; }
    public net.minecraft.entity.passive.WanderingTraderEntity getSpawnedTrader() { return spawnedTrader; }
    public void setSpawnedTrader(net.minecraft.entity.passive.WanderingTraderEntity trader) { this.spawnedTrader = trader; }
    public void setForcedNextEvent(String event) { this.forcedNextEvent = event; }

    /** Remove a participant (they left the biome run). */
    public void removeParticipant(UUID playerUuid) {
        participantUuids.remove(playerUuid);
        pendingVotes.remove(playerUuid);
        completedPlayers.remove(playerUuid);
        fightFinishedPlayers.remove(playerUuid);
    }

    // === Event Rolling ===

    /**
     * Roll which event occurs between levels.
     * Delegates to EventRegistry for probability cascade.
     */
    public String rollEvent(int biomeOrdinal, int levelIndex, int ngPlusLevel, boolean earlyBiome) {
        // Check for forced event
        if (forcedNextEvent != null) {
            String forced = forcedNextEvent;
            forcedNextEvent = null;
            return forced;
        }

        // Skip conditions
        if (levelIndex <= 1) return "none";

        java.util.Random rng = new java.util.Random();
        float eventRoll = rng.nextFloat();

        // Early biome reduction
        if (earlyBiome) {
            if (eventRoll > CrafticsMod.CONFIG.earlyBiomeEventChance()) return "none";
            eventRoll = rng.nextFloat();
        }

        return com.crackedgames.craftics.api.registry.EventRegistry.roll(eventRoll, biomeOrdinal);
    }

    // === Vote System ===

    /**
     * Start a vote for a choice event. Sends VictoryChoicePayload to all participants.
     * For solo players, their first response resolves immediately.
     */
    public void startVote(EventType eventType, int emeraldsEarned, String label, ServerWorld world) {
        pendingEventType = eventType;
        pendingVotes.clear();
        voteStartTime = System.currentTimeMillis();

        for (UUID uuid : participantUuids) {
            ServerPlayerEntity p = world.getServer().getPlayerManager().getPlayer(uuid);
            if (p != null) {
                com.crackedgames.craftics.world.CrafticsSavedData data =
                    com.crackedgames.craftics.world.CrafticsSavedData.get(world);
                com.crackedgames.craftics.world.CrafticsSavedData.PlayerData pd = data.getPlayerData(uuid);
                ServerPlayNetworking.send(p, new VictoryChoicePayload(
                    emeraldsEarned, pd.emeralds, false, label, -1, false
                ));
            }
        }
    }

    /**
     * Record a player's vote. Returns true if all votes are in and the result is ready.
     */
    public boolean handleVote(UUID playerUuid, boolean accept) {
        pendingVotes.put(playerUuid, accept);
        return isVoteComplete();
    }

    /** Check if voting is complete (all online participants voted or timeout). */
    public boolean isVoteComplete() {
        if (pendingEventType == null) return false;
        if (pendingVotes.size() >= participantUuids.size()) return true;
        return System.currentTimeMillis() - voteStartTime > VOTE_TIMEOUT_MS;
    }

    /** Get the pending event type being voted on. */
    public EventType getPendingEventType() { return pendingEventType; }

    /**
     * Get the vote result. Majority wins, ties go to accept.
     * Non-voters are excluded. If all abstain, default to decline.
     */
    public boolean getVoteResult() {
        if (pendingVotes.isEmpty()) return false;
        long accepts = pendingVotes.values().stream().filter(v -> v).count();
        long declines = pendingVotes.values().stream().filter(v -> !v).count();
        return accepts >= declines; // ties go to accept
    }

    /** Clear vote state after resolution. */
    public void clearVote() {
        pendingEventType = null;
        pendingVotes.clear();
        voteStartTime = 0;
    }

    // === Completion Tracking (trader/dig site) ===

    public void markPlayerCompleted(UUID playerUuid) {
        completedPlayers.add(playerUuid);
    }

    public boolean allPlayersCompleted() {
        for (UUID uuid : participantUuids) {
            if (!completedPlayers.contains(uuid)) return false;
        }
        return true;
    }

    public void clearCompletions() {
        completedPlayers.clear();
    }

    // === Fight Sync ===

    public void markFightFinished(UUID playerUuid) {
        fightFinishedPlayers.add(playerUuid);
    }

    public boolean allFightsFinished() {
        for (UUID uuid : participantUuids) {
            if (!fightFinishedPlayers.contains(uuid)) return false;
        }
        return true;
    }

    public void clearFightFinished() {
        fightFinishedPlayers.clear();
    }

    // === Helpers ===

    /** Get online participants as player entities. */
    public List<ServerPlayerEntity> getOnlineParticipants(ServerWorld world) {
        List<ServerPlayerEntity> online = new ArrayList<>();
        for (UUID uuid : participantUuids) {
            ServerPlayerEntity p = world.getServer().getPlayerManager().getPlayer(uuid);
            if (p != null) online.add(p);
        }
        return online;
    }

    /** Send a message to all online participants. */
    public void broadcastMessage(ServerWorld world, String message) {
        for (ServerPlayerEntity p : getOnlineParticipants(world)) {
            p.sendMessage(net.minecraft.text.Text.literal(message), false);
        }
    }

    /** Send a payload to all online participants. */
    public void broadcastPayload(ServerWorld world, net.minecraft.network.packet.CustomPayload payload) {
        for (ServerPlayerEntity p : getOnlineParticipants(world)) {
            ServerPlayNetworking.send(p, payload);
        }
    }
}
