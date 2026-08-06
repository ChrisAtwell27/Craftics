package com.crackedgames.craftics.raid;

import com.crackedgames.craftics.CrafticsMod;
import com.crackedgames.craftics.combat.CombatManager;
import com.crackedgames.craftics.world.VisitProtection;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The five-minute join window. Holds the join ORDER (packing is fill-first, so
 * order decides who lands in which instance) and every joiner's origin, so any
 * exit path can put them back.
 *
 * <p>One window server-wide at a time: the daily schedule opens exactly one.
 */
public final class RaidBossLobby {
    private RaidBossLobby() {}

    public enum JoinResult { JOINED, NO_RAID, WINDOW_CLOSED, ALREADY_JOINED, BUSY, VISITING, FULL }

    private static RaidBossDefinition boss = null;
    private static final List<UUID> JOINERS = new ArrayList<>();
    private static int ticksLeft = 0;

    public static void open(RaidBossDefinition definition, int windowSeconds) {
        boss = definition;
        JOINERS.clear();
        ticksLeft = Math.max(1, windowSeconds) * 20;
    }

    public static void close() {
        boss = null;
        JOINERS.clear();
        ticksLeft = 0;
    }

    /**
     * Server stop / world unload: drop the lobby with no player-facing broadcast -
     * nobody is online to read one at shutdown - so a leftover boss, joiner list or
     * countdown can't leak into whatever save loads next in the same JVM
     * (a singleplayer world switch keeps these statics alive across worlds).
     * Does not touch {@link RaidBossOrigins}; the caller clears that separately since
     * it also holds entries from already-started instances.
     */
    public static void reset() {
        close();
    }

    /**
     * A joiner disconnected before the window closed. Drop them from the roster and
     * forget their origin: they were never pulled into a started instance, so
     * nothing else will ever act on either entry, and leaving them in JOINERS would
     * keep occupying a capacity slot and inflate the "(N joined)" countdown
     * broadcast until the window closes on its own. No-op if they aren't currently
     * in the lobby (already joined an instance, or never joined at all).
     */
    public static void leave(UUID id) {
        if (JOINERS.remove(id)) {
            RaidBossOrigins.forget(id);
        }
    }

    public static boolean isOpen() { return boss != null && ticksLeft > 0; }

    public static RaidBossDefinition boss() { return boss; }

    public static int secondsLeft() { return Math.max(0, ticksLeft / 20); }

    public static int count() { return JOINERS.size(); }

    public static List<UUID> joiners() { return List.copyOf(JOINERS); }

    /** Count the window down. Returns true on the tick the window closes. */
    public static boolean tick() {
        if (boss == null || ticksLeft <= 0) return false;
        ticksLeft--;
        return ticksLeft == 0;
    }

    /**
     * The "is this player free to raid" predicate, shared by {@link #join} and
     * {@link RaidBossInstance#start} so the two checks can never drift into two
     * different definitions of "busy": not mid-combat/mid-event
     * ({@link CombatManager#isEngaged}), not mid-biome-run (the persisted
     * {@code PlayerData.isInBiomeRun()} - called out separately by both the design
     * spec and {@code docs/modding/raid-bosses.md} because a player parked at the
     * victory or level-select screen between levels has an INACTIVE CombatManager and
     * would otherwise slip through isEngaged alone), and not a foreign visitor on
     * someone else's island.
     *
     * @return the reason they are ineligible, or {@code null} when they are free to raid.
     */
    public static JoinResult checkEligibility(ServerPlayerEntity p) {
        UUID id = p.getUuid();
        if (CombatManager.isEngaged(id)) return JoinResult.BUSY;
        if (isMidBiomeRun(p)) return JoinResult.BUSY;
        if (VisitProtection.isForeignVisitor(p)) return JoinResult.VISITING;
        return null;
    }

    private static boolean isMidBiomeRun(ServerPlayerEntity p) {
        com.crackedgames.craftics.world.CrafticsSavedData data = com.crackedgames.craftics.world.CrafticsSavedData
            .get((net.minecraft.server.world.ServerWorld) p.getEntityWorld());
        return data.getPlayerData(p.getUuid()).isInBiomeRun();
    }

    /**
     * Try to add a player. Eligibility: window open, not already in, not mid-run or
     * mid-combat, not a visitor on someone else's island, and under the instance cap.
     */
    public static JoinResult join(ServerPlayerEntity p) {
        if (boss == null) return JoinResult.NO_RAID;
        if (ticksLeft <= 0) return JoinResult.WINDOW_CLOSED;
        UUID id = p.getUuid();
        if (JOINERS.contains(id)) return JoinResult.ALREADY_JOINED;
        JoinResult ineligible = checkEligibility(p);
        if (ineligible != null) return ineligible;
        if (!RaidBossLobbyPacking.hasRoom(JOINERS.size(), CrafticsMod.CONFIG.raidBossMaxInstances())) {
            return JoinResult.FULL;
        }

        // Remembered HERE, at join time, not when the instance later starts: by the
        // time instances start the player may have wandered off from where they
        // joined. RaidBossOrigins.remember is first-write-wins, so this earlier value
        // correctly wins over whatever RaidBossInstance.start captures later.
        RaidBossOrigins.remember(id, new RaidBossOrigins.Origin(
            p.getEntityWorld().getRegistryKey().getValue().toString(),
            p.getX(), p.getY(), p.getZ(), p.getYaw(), p.getPitch()));
        JOINERS.add(id);
        return JoinResult.JOINED;
    }
}
