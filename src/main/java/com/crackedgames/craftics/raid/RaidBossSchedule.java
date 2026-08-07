package com.crackedgames.craftics.raid;

import com.crackedgames.craftics.CrafticsMod;
import com.crackedgames.craftics.network.RaidBossToastPayload;
import com.crackedgames.craftics.world.CrafticsSavedData;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
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
    /** Guards the "N minute(s) left" broadcast to firing once per minute mark, not
     *  once per tick within that mark's second - the WINDOW_OPEN countdown below runs
     *  every tick so the window itself stays second-accurate, but integer division
     *  (secondsLeft() % 60 == 0) alone stays true for all 20 ticks of that second. */
    private static int lastMinuteBroadcast = -1;

    /** Pre-window warning thresholds, largest first. Each fires once as a toast when
     *  {@link #ticksUntilWindow} crosses below it during the ANNOUNCED phase. */
    private static final int[] WARNING_MINUTES = {30, 10};
    /** Parallel to {@link #WARNING_MINUTES}: true once that threshold's toast has fired
     *  (or was skipped as already-past at announce time) for the current raid. A
     *  crossed-boolean rather than an exact tick-count match, so a lagging or batched
     *  tick can never cause a threshold to be stepped over unfired. */
    private static final boolean[] warningFired = new boolean[WARNING_MINUTES.length];

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
        lastMinuteBroadcast = -1;
        java.util.Arrays.fill(warningFired, false);
    }

    public static void tick(MinecraftServer server) {
        if (server == null) return;
        if (!CrafticsMod.CONFIG.raidBossesEnabled()) {
            // The master switch can flip off mid-window (a config edit or reload).
            // Bailing out here with no cleanup used to freeze whatever state was
            // current: a pending announce or an open lobby never advanced, the phase
            // never returned to IDLE, and forceStart's `phase != IDLE` guard refused
            // admin control forever. Reset both the schedule and the lobby so turning
            // raids back on later starts clean, and so an admin can still forceStart.
            if (phase != Phase.IDLE || RaidBossLobby.isOpen()) {
                for (UUID id : RaidBossLobby.joiners()) RaidBossOrigins.forget(id);
                RaidBossLobby.close();
                reset();
                CrafticsMod.LOGGER.info("Raid bosses disabled mid-schedule; reset to IDLE.");
            }
            return;
        }

        // The join countdown runs every tick so the window is second-accurate.
        if (phase == Phase.WINDOW_OPEN) {
            if (RaidBossLobby.tick()) {
                startAllInstances(server);
            } else {
                int secondsLeft = RaidBossLobby.secondsLeft();
                int minuteMark = secondsLeft / 60;
                if (secondsLeft > 0 && secondsLeft % 60 == 0 && minuteMark != lastMinuteBroadcast) {
                    lastMinuteBroadcast = minuteMark;
                    broadcastToast(server, minuteMark + "m left",
                        pending.name() + " - " + RaidBossLobby.count() + " joined");
                }
            }
            return;
        }

        if (phase == Phase.ANNOUNCED) {
            if (--ticksUntilWindow <= 0) {
                openWindow(server);
                return;
            }
            checkPreWindowWarnings(server);
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
        // A threshold already at or behind the lead time is covered by this very
        // announcement, so mark it fired up front -otherwise a short-lead admin
        // /raidboss start would fire the 30/10 minute warnings within the same second.
        for (int i = 0; i < WARNING_MINUTES.length; i++) {
            warningFired[i] = minutesUntil <= WARNING_MINUTES[i];
        }
        broadcastToast(server, "RAID INCOMING",
            boss.name() + " in " + minutesUntil + "m");
        CrafticsMod.LOGGER.info("Announced raid boss '{}', window opens in {} minute(s)",
            boss.id(), minutesUntil);
    }

    /** Fire each not-yet-fired warning threshold that {@link #ticksUntilWindow} has
     *  now dropped to or below. Runs every tick of the ANNOUNCED phase. */
    private static void checkPreWindowWarnings(MinecraftServer server) {
        for (int i = 0; i < WARNING_MINUTES.length; i++) {
            if (warningFired[i]) continue;
            int thresholdTicks = WARNING_MINUTES[i] * 60 * 20;
            if (ticksUntilWindow <= thresholdTicks) {
                warningFired[i] = true;
                broadcastToast(server, pending.name() + " - " + WARNING_MINUTES[i] + "m",
                    "/raidboss to prep");
            }
        }
    }

    private static void openWindow(MinecraftServer server) {
        phase = Phase.WINDOW_OPEN;
        int seconds = CrafticsMod.CONFIG.raidBossJoinWindowSeconds();
        RaidBossLobby.open(pending, seconds);
        broadcastToast(server, pending.name() + " ARRIVED",
            "/raidboss to join (" + (seconds / 60) + "m)");
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
        int totalRaiders = 0;
        for (List<UUID> group : packed) {
            RaidBossInstance instance = new RaidBossInstance(UUID.randomUUID(), boss, group);
            boolean ok;
            try {
                ok = instance.start(server);
            } catch (Exception e) {
                // One group's arena build or dimension open throwing must not abort the
                // loop: the lobby is already closed and the phase already reset by the
                // time we get here, so any group after the one that threw would
                // otherwise never start, and would never even hear why.
                CrafticsMod.LOGGER.error(
                    "Raid '{}' instance threw during start(); the rest of the pack is still attempted",
                    boss.id(), e);
                ok = false;
            }
            if (ok) {
                started++;
                totalRaiders += instance.roster().size();
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
        if (started > 0) {
            // One announcement for the whole raid, not one per instance: with multiple
            // arenas this used to fire the same "descends" line to every online player
            // once per instance (RaidBossInstance.start() broadcast it directly).
            broadcastToast(server, boss.name() + " descends!",
                totalRaiders + " raiders, " + started + " arena(s)");
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
            broadcastToast(server, "Raid Cancelled", "The raid was called off.");
        }
        RaidBossInstance.endAll(server);
    }

    /** Seconds until the next scheduled raid announcement, or -1 when none is configured. */
    public static int secondsUntilNextRaid() {
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
    public static String nextSlotDescription() {
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

    /** Server-wide raid boss toast, replacing the chat broadcasts these scheduling
     *  messages used to spam. Command replies stay on chat -see RaidBossCommands. */
    private static void broadcastToast(MinecraftServer server, String title, String subtitle) {
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(p, new RaidBossToastPayload(title, subtitle));
        }
    }
}
