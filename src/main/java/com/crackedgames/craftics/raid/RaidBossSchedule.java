package com.crackedgames.craftics.raid;

import com.crackedgames.craftics.CrafticsMod;
import com.crackedgames.craftics.world.CrafticsSavedData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Drives the daily raid: announce, open the window, start every instance at once.
 *
 * <p>Slot firing is decided by {@link RaidBossScheduleMath} against the server's
 * local wall clock, and the ANNOUNCE action is what marks a slot fired for the
 * day. Everything after the announcement is an in-memory countdown, so a restart
 * mid-sequence loses that raid rather than replaying it.
 */
public final class RaidBossSchedule {
    private RaidBossSchedule() {}

    public enum Phase { IDLE, ANNOUNCED, WINDOW_OPEN }

    /** Evaluate the wall clock once a second, not 20 times. */
    private static final int TICK_INTERVAL = 20;

    private static final Random RNG = new Random();

    private static int tickCounter = 0;
    private static Phase phase = Phase.IDLE;
    private static RaidBossDefinition pending = null;
    private static int ticksUntilWindow = 0;

    public static Phase phase() { return phase; }

    public static RaidBossDefinition pendingBoss() { return pending; }

    /**
     * Server stop / world unload: drop back to IDLE with no player-facing broadcast -
     * nobody is online to read one at shutdown. Without this, an ANNOUNCED or
     * WINDOW_OPEN raid built for this save's clock and players would keep ticking
     * once a different save loads in the same JVM (a singleplayer world switch keeps
     * these statics alive across worlds), potentially auto-starting a raid nobody
     * here ever announced. Does not touch {@link RaidBossLobby}; the caller resets
     * that separately.
     */
    public static void reset() {
        phase = Phase.IDLE;
        pending = null;
        ticksUntilWindow = 0;
        tickCounter = 0;
    }

    public static void tick(MinecraftServer server) {
        if (server == null) return;
        if (!CrafticsMod.CONFIG.raidBossesEnabled()) return;

        // The join countdown runs every tick so the window is second-accurate.
        if (phase == Phase.WINDOW_OPEN) {
            if (RaidBossLobby.tick()) {
                startAllInstances(server);
            } else if (RaidBossLobby.secondsLeft() % 60 == 0 && RaidBossLobby.secondsLeft() > 0) {
                broadcast(server, Text.literal("§e" + RaidBossLobby.secondsLeft() / 60
                    + " minute(s) left to §6/raidboss§e into " + pending.name()
                    + " §7(" + RaidBossLobby.count() + " joined)"));
            }
            return;
        }

        if (phase == Phase.ANNOUNCED) {
            if (--ticksUntilWindow <= 0) openWindow(server);
            return;
        }

        if (++tickCounter < TICK_INTERVAL) return;
        tickCounter = 0;
        evaluateSlots(server);
    }

    private static void evaluateSlots(MinecraftServer server) {
        List<String> slots = RaidBossScheduleMath.parseSlots(CrafticsMod.CONFIG.raidBossTimes());
        if (slots.isEmpty()) return;

        CrafticsSavedData data = CrafticsSavedData.get(server.getOverworld());
        RaidBossState state = RaidBossState.parse(data.raidBossState);
        long today = LocalDate.now().toEpochDay();
        LocalTime now = LocalTime.now();
        int nowMinute = now.getHour() * 60 + now.getMinute();
        int lead = CrafticsMod.CONFIG.raidBossAnnounceLeadMinutes();
        int window = CrafticsMod.CONFIG.raidBossJoinWindowSeconds();

        for (String slot : slots) {
            int slotMinute = RaidBossScheduleMath.minuteOfDay(slot);
            long lastFired = state.slotLastFiredDay().getOrDefault(slot, Long.MIN_VALUE);
            RaidBossScheduleMath.Action action = RaidBossScheduleMath.evaluate(
                slotMinute, nowMinute, lead, window, today, lastFired);

            if (action == RaidBossScheduleMath.Action.MISSED) {
                state.markFired(slot, today);
                data.raidBossState = state.serialize();
                data.markDirty();
                CrafticsMod.LOGGER.info(
                    "Raid slot {} was missed (server offline across its window); skipping today.", slot);
                continue;
            }
            if (action != RaidBossScheduleMath.Action.ANNOUNCE) continue;

            RaidBossDefinition chosen = rollBoss(state, today);
            state.markFired(slot, today);
            if (chosen != null) {
                // Recorded at announce, not at start: a crash mid-window must not let
                // the same boss be rolled again tomorrow.
                state.recordBoss(today, chosen.id(), CrafticsMod.CONFIG.raidBossNoRepeatDays() + 2);
            }
            data.raidBossState = state.serialize();
            data.markDirty();

            if (chosen == null) {
                CrafticsMod.LOGGER.warn("Raid slot {} fired but no raid boss is defined.", slot);
                continue;
            }
            announce(server, chosen, Math.max(0, slotMinute - nowMinute));
            return; // one raid at a time
        }
    }

    private static RaidBossDefinition rollBoss(RaidBossState state, long today) {
        List<RaidBossRotation.Candidate> candidates = RaidBossRegistry.candidates();
        RaidBossRotation.Pick pick = RaidBossRotation.pick(
            candidates, state, today, CrafticsMod.CONFIG.raidBossNoRepeatDays(), RNG);
        if (pick.exclusionDropped()) {
            CrafticsMod.LOGGER.warn(
                "Only {} raid boss(es) defined but raidBossNoRepeatDays={}; "
                + "the no-repeat window was ignored for today's roll.",
                candidates.size(), CrafticsMod.CONFIG.raidBossNoRepeatDays());
        }
        return pick.bossId() == null ? null : RaidBossRegistry.get(pick.bossId());
    }

    private static void announce(MinecraftServer server, RaidBossDefinition boss, int minutesUntil) {
        pending = boss;
        phase = Phase.ANNOUNCED;
        ticksUntilWindow = Math.max(1, minutesUntil * 60 * 20);
        broadcast(server, Text.literal("§4§l✦ RAID BOSS ✦ §r§c" + boss.name()
            + " §7stirs. It arrives in " + minutesUntil + " minute(s). Be ready to §6/raidboss§7."));
        CrafticsMod.LOGGER.info("Announced raid boss '{}', window opens in {} minute(s)",
            boss.id(), minutesUntil);
    }

    private static void openWindow(MinecraftServer server) {
        phase = Phase.WINDOW_OPEN;
        int seconds = CrafticsMod.CONFIG.raidBossJoinWindowSeconds();
        RaidBossLobby.open(pending, seconds);
        broadcast(server, Text.literal("§4§l✦ " + pending.name() + " HAS ARRIVED ✦"));
        broadcast(server, Text.literal("§eType §6/raidboss§e to join. "
            + (seconds / 60) + " minute(s), up to 8 raiders per arena."));
    }

    private static void startAllInstances(MinecraftServer server) {
        RaidBossDefinition boss = RaidBossLobby.boss();
        List<UUID> joinOrder = RaidBossLobby.joiners();
        RaidBossLobby.close();
        phase = Phase.IDLE;
        pending = null;

        if (boss == null || joinOrder.isEmpty()) {
            if (boss != null) {
                CrafticsMod.LOGGER.info("Raid '{}' cancelled: nobody joined.", boss.id());
            }
            for (UUID id : joinOrder) RaidBossOrigins.forget(id);
            return;
        }

        List<List<UUID>> packed = RaidBossLobbyPacking.pack(
            joinOrder, CrafticsMod.CONFIG.raidBossMaxInstances());
        int started = 0;
        for (List<UUID> group : packed) {
            RaidBossInstance instance = new RaidBossInstance(UUID.randomUUID(), boss, group);
            if (instance.start(server)) {
                started++;
            } else {
                // Forget every origin in the group unconditionally, not just the ones
                // still online: a joiner who disconnected before the window closed
                // (the only way start() fails when someone did join) would otherwise
                // keep a stale RaidBossOrigins entry forever, mis-teleporting them on
                // their next raid.
                for (UUID id : group) {
                    RaidBossOrigins.forget(id);
                    ServerPlayerEntity p = server.getPlayerManager().getPlayer(id);
                    if (p != null) {
                        p.sendMessage(Text.literal("§cThe raid could not start. You were not moved."), false);
                    }
                }
            }
        }
        CrafticsMod.LOGGER.info("Raid '{}' started {} instance(s) for {} raider(s)",
            boss.id(), started, joinOrder.size());
    }

    /** Tell a player who just logged in about a raid already in the pipeline. */
    public static void onPlayerJoin(ServerPlayerEntity p) {
        if (phase == Phase.ANNOUNCED && pending != null) {
            p.sendMessage(Text.literal("§4§l✦ RAID BOSS ✦ §r§c" + pending.name()
                + " §7arrives in " + Math.max(1, ticksUntilWindow / 20 / 60) + " minute(s)."), false);
        } else if (phase == Phase.WINDOW_OPEN && pending != null) {
            p.sendMessage(Text.literal("§4§l✦ " + pending.name()
                + " §r§cis here! §6/raidboss§c to join, "
                + RaidBossLobby.secondsLeft() + "s left."), false);
        }
    }

    /** Admin: run a boss right now, using the normal announce-then-window sequence. */
    public static boolean forceStart(MinecraftServer server, String bossId) {
        if (phase != Phase.IDLE) return false;
        RaidBossDefinition boss = RaidBossRegistry.get(bossId);
        if (boss == null) return false;
        announce(server, boss, 1);
        return true;
    }

    /** Admin: drop a pending raid, or tear down every running instance. */
    public static void cancel(MinecraftServer server) {
        if (phase != Phase.IDLE) {
            for (UUID id : RaidBossLobby.joiners()) RaidBossOrigins.forget(id);
            RaidBossLobby.close();
            phase = Phase.IDLE;
            pending = null;
            broadcast(server, Text.literal("§7The raid was called off."));
        }
        RaidBossInstance.endAll(server);
    }

    /** Seconds until the next scheduled raid announcement, or -1 when none is configured. */
    public static int secondsUntilNextRaid(MinecraftServer server) {
        List<String> slots = RaidBossScheduleMath.parseSlots(CrafticsMod.CONFIG.raidBossTimes());
        if (slots.isEmpty()) return -1;
        LocalTime now = LocalTime.now();
        int nowSecond = now.getHour() * 3600 + now.getMinute() * 60 + now.getSecond();
        int best = Integer.MAX_VALUE;
        for (String slot : slots) {
            int slotSecond = RaidBossScheduleMath.minuteOfDay(slot) * 60;
            int delta = slotSecond - nowSecond;
            if (delta < 0) delta += 24 * 3600;
            best = Math.min(best, delta);
        }
        return best == Integer.MAX_VALUE ? -1 : best;
    }

    /** "18:00" style description of the next slot, or "none" when unconfigured. */
    public static String nextSlotDescription(MinecraftServer server) {
        List<String> slots = RaidBossScheduleMath.parseSlots(CrafticsMod.CONFIG.raidBossTimes());
        if (slots.isEmpty()) return "none";
        LocalTime now = LocalTime.now();
        int nowMinute = now.getHour() * 60 + now.getMinute();
        String best = slots.get(0);
        int bestDelta = Integer.MAX_VALUE;
        for (String slot : slots) {
            int delta = RaidBossScheduleMath.minuteOfDay(slot) - nowMinute;
            if (delta < 0) delta += 24 * 60;
            if (delta < bestDelta) { bestDelta = delta; best = slot; }
        }
        return best;
    }

    private static void broadcast(MinecraftServer server, Text message) {
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            p.sendMessage(message, false);
        }
    }
}
