package com.crackedgames.craftics.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * The fighting-game battle intro, client side: camera zooms in tight on each fighter in
 * turn, their affinity intro animation plays, then the camera pulls back out to the
 * standard combat framing and input unlocks.
 *
 * <p>Runs entirely on this client's clock, started by {@code CombatIntroPayload}. The
 * sequence waits for the loading transition (swipe + walkers) to clear first - each
 * client's transition finishes at its own moment, so a server-stepped timeline would cut
 * the camera mid-swipe for whoever loads slowest. The server locks combat input for a
 * matching duration on its side, so a client that unlocks early still can't act early.
 *
 * <p>Failsafes, in the spirit of every other sequence in this mod: a hard timeout on the
 * overlay wait, and an abort the moment combat stops existing (defeat, /home, disconnect
 * teardown) so the intro can never strand the camera or the input lock.
 */
public final class CombatIntroSequence {

    private CombatIntroSequence() {}

    private record Entry(int entityId, String affinity) {}

    private static final List<Entry> cast = new ArrayList<>();
    private static boolean running = false;
    private static boolean waitingForOverlay = false;
    private static boolean inTail = false;
    private static int waitTimeoutTicks = 0;
    private static int stepTicks = 45;
    private static int stepIndex = -1;
    private static int tickInStep = 0;

    /** Camera distance while dwelling on a fighter - well inside the manual zoom floor. */
    private static final float INTRO_ZOOM = 5.5f;
    /** Give up waiting for the loading overlay after 15s and play anyway. */
    private static final int OVERLAY_WAIT_TIMEOUT_TICKS = 300;
    /** Zoom-out breather after the last fighter before input unlocks. */
    private static final int TAIL_TICKS = 20;

    /** Parse the wire cast ("entityId:AFFINITY,...") and arm the sequence. */
    public static void start(String castData, int perStepTicks) {
        cast.clear();
        if (castData != null && !castData.isBlank()) {
            for (String token : castData.split(",")) {
                int colon = token.indexOf(':');
                if (colon <= 0) continue;
                try {
                    cast.add(new Entry(Integer.parseInt(token.substring(0, colon)),
                        token.substring(colon + 1)));
                } catch (NumberFormatException ignored) { /* malformed entry - skip */ }
            }
        }
        if (cast.isEmpty()) return;
        stepTicks = Math.max(20, perStepTicks);
        running = true;
        waitingForOverlay = true;
        inTail = false;
        waitTimeoutTicks = OVERLAY_WAIT_TIMEOUT_TICKS;
        stepIndex = -1;
        tickInStep = 0;
        CombatState.setIntroActive(true);
    }

    /** Ticked every client tick from CrafticsClient, alongside CombatAnimations. */
    public static void tick() {
        if (!running) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!CombatState.isInCombat() || mc.world == null || mc.player == null) {
            abort();
            return;
        }

        if (waitingForOverlay) {
            waitTimeoutTicks--;
            if (TransitionOverlay.isActive() && waitTimeoutTicks > 0) return;
            waitingForOverlay = false;
            beginStep(0);
            faceCastToCamera(mc);
            return;
        }

        faceCastToCamera(mc);
        tickInStep++;

        if (inTail) {
            if (tickInStep >= TAIL_TICKS) finish();
            return;
        }

        if (tickInStep >= stepTicks) {
            int next = stepIndex + 1;
            if (next < cast.size()) {
                beginStep(next);
            } else {
                // Last fighter done: start the pull-back while input stays locked, so
                // the zoom-out is part of the show rather than a fight for the camera.
                inTail = true;
                tickInStep = 0;
                CombatState.introZoomOut();
            }
        }
    }

    private static void beginStep(int idx) {
        stepIndex = idx;
        tickInStep = 0;
        Entry e = cast.get(idx);
        var world = MinecraftClient.getInstance().world;
        var entity = world != null ? world.getEntityById(e.entityId()) : null;
        if (entity == null) return; // fighter left/not yet tracked - camera holds, time still passes
        // +10 so the focus never expires mid-dwell; the next step re-focuses anyway.
        CombatState.introFocusOn(entity.getX(), entity.getZ(), INTRO_ZOOM, stepTicks + 10);
        if (entity instanceof AbstractClientPlayerEntity acp) {
            CombatAnimations.playIntro(acp, e.affinity());
        }
    }

    /**
     * Turn every fighter to face the camera, eased. Presenting to the audience is the
     * whole point of an intro; without this each fighter holds whatever heading they
     * were teleported in with, which for the isometric camera is usually a profile
     * or their back. Applied every tick because remote players' rotations are
     * re-asserted by the server sync each tick, and the current + prev fields are
     * both set so the renderer never interpolates from a stale angle.
     */
    private static void faceCastToCamera(MinecraftClient mc) {
        if (mc.gameRenderer == null || mc.world == null) return;
        var camPos = mc.gameRenderer.getCamera().getPos();
        for (Entry e : cast) {
            var ent = mc.world.getEntityById(e.entityId());
            if (!(ent instanceof AbstractClientPlayerEntity p)) continue;
            double dx = camPos.x - p.getX();
            double dz = camPos.z - p.getZ();
            if (dx * dx + dz * dz < 0.01) continue;
            float target = (float) Math.toDegrees(net.minecraft.util.math.MathHelper.atan2(dz, dx)) - 90.0f;
            float eased = p.getHeadYaw()
                + net.minecraft.util.math.MathHelper.wrapDegrees(target - p.getHeadYaw()) * 0.3f;
            p.setYaw(eased);
            p.setHeadYaw(eased);
            p.setBodyYaw(eased);
            //? if <=1.21.4 {
            p.prevYaw = eased;
            p.prevHeadYaw = eased;
            p.prevBodyYaw = eased;
            //?} else {
            /*p.lastYaw = eased;
            p.lastHeadYaw = eased;
            p.lastBodyYaw = eased;
            *///?}
        }
    }

    private static void finish() {
        running = false;
        cast.clear();
        CombatState.setIntroActive(false);
    }

    /** Hard stop: combat ended (or is gone) mid-intro. Never leave the lock behind. */
    public static void abort() {
        running = false;
        waitingForOverlay = false;
        inTail = false;
        cast.clear();
        if (CombatState.isIntroActive()) CombatState.setIntroActive(false);
    }
}
