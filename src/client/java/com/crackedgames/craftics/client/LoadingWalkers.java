package com.crackedgames.craftics.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * The cast that walks across the loading screen: one figure per party member, each animated with
 * the sprite sheet for that player's strongest affinity.
 *
 * <p>Runs entirely inside {@link TransitionOverlay}'s black hold. The overlay already sweeps to
 * black and back; this fills the part in between, which used to be a static title and a tip.
 *
 * <p><b>Shape of the sequence.</b> Members enter one at a time from the left, each starting
 * {@link #ENTRY_STAGGER_TICKS} after the one before, and walk to their own slot in a row centred
 * on screen. Once there they hold, marking time, for as long as the load takes - which is not a
 * known duration, so nothing here counts down to the end. When the server says it is finished
 * ({@link #beginExit()}) they leave in the same order they arrived, each walking off the right
 * edge, and only once the last one is gone does the overlay sweep back off.
 *
 * <p><b>The cast is decided server-side.</b> Affinity points live on the server and are not
 * synced for other players, so the row is built there and arrives as a list of affinity names
 * (see {@code LoadingScreenPayload}). The client renders what it is told and never guesses; an
 * empty or unparseable cast simply means no walkers, and the overlay behaves as it always did.
 */
public final class LoadingWalkers {

    private LoadingWalkers() {}

    // ── Sheet geometry ──────────────────────────────────────────────────────
    /** Every sheet is one row of frames, left to right, at this size. */
    private static final int FRAME_W = 136;
    private static final int FRAME_H = 162;
    private static final int FRAME_COUNT = 8;
    private static final int SHEET_W = FRAME_W * FRAME_COUNT;   // 1088
    private static final int SHEET_H = FRAME_H;                 // 162

    /** Ticks each frame is held. 3 gives a ~2.4 walk cycles/sec gait at 20 TPS. */
    private static final int TICKS_PER_FRAME = 3;

    // ── Layout ──────────────────────────────────────────────────────────────
    /** On-screen height of a walker, in GUI pixels. Width follows the sheet's aspect. */
    private static final int DRAW_H = 72;
    private static final int DRAW_W = Math.round(DRAW_H * (FRAME_W / (float) FRAME_H));
    /** Gap between walkers when several stand together. */
    private static final int SPACING = 10;
    /** How far below the vertical centre their feet sit. */
    private static final int BASELINE_OFFSET = 30;

    // ── Timing ──────────────────────────────────────────────────────────────
    /**
     * How long one walker takes to cross, in ticks.
     *
     * <p>A fixed duration with an eased curve, NOT a proportional lerp. A lerp covers a fixed
     * fraction of the remaining gap each tick, so its first step is the biggest one - from off
     * screen that put the walker most of the way to the centre in a handful of ticks and made
     * the entrance look like a teleport. Interpolating over a known duration gives every walker
     * the same, readable crossing whatever distance it starts from.
     */
    private static final int ENTER_TICKS = 12;
    /** Leaving is a touch brisker than arriving, but still legible. */
    private static final int EXIT_TICKS = 26;
    /** Ticks between one walker setting off and the next. */
    private static final int ENTRY_STAGGER_TICKS = 10;
    /** Ticks between one walker leaving and the next following. */
    private static final int EXIT_STAGGER_TICKS = 8;
    /** The row stands assembled at least this long before it is allowed to disperse. */
    private static final int CENTER_HOLD_TICKS = 30;   // 1.5s
    /**
     * And the empty screen holds this long after the last one leaves, before the swipe off.
     *
     * <p>Short on purpose. A pause here is dead air with nothing on screen - the exit has
     * already read by the time the last walker clears the edge, so anything longer is just the
     * player waiting on black.
     */
    private static final int EMPTY_HOLD_TICKS = 6;     // 0.3s
    /** Hard ceiling on the whole entrance, so a hung load never leaves them mid-stride. */
    private static final int ENTRY_TIMEOUT_TICKS = 240;

    private enum Phase { IDLE, ENTERING, WAITING, EXITING, EMPTY_HOLD }

    /** Ease-in-out over 0..1. Starts and ends gently, quickest in the middle. */
    private static float smooth(float t) {
        if (t <= 0f) return 0f;
        if (t >= 1f) return 1f;
        return t * t * (3f - 2f * t);
    }

    private static Phase phase = Phase.IDLE;
    private static final List<Walker> WALKERS = new ArrayList<>();
    private static int tick = 0;
    private static int exitTick = 0;
    /** The load finished before the row had assembled; leave as soon as it has. */
    private static boolean exitPending = false;

    /** Ticks the assembled row has been standing still. */
    private static int centeredTick = 0;
    /** Ticks since the last walker left the screen. */
    private static int emptyTick = 0;

    /** One figure: which sheet it wears, where it stops, and where it is now. */
    private static final class Walker {
        final Identifier sheet;
        final int order;          // position in the row, left to right
        float x;                  // current left edge, GUI pixels
        float startX;             // where it came on from
        float targetX;            // where it stops when the row is assembled
        float exitX;              // where it walks off to
        int animTick;             // drives the frame cursor

        Walker(Identifier sheet, int order) {
            this.sheet = sheet;
            this.order = order;
        }

        int frame() { return (animTick / TICKS_PER_FRAME) % FRAME_COUNT; }
    }

    /**
     * Start the sequence for a cast of affinity names (as sent by the server).
     *
     * <p>Safe to call with junk: unknown names fall back to a default sheet, and an empty list
     * leaves the overlay with no walkers at all rather than failing.
     */
    public static void begin(List<String> affinities) {
        WALKERS.clear();
        tick = 0;
        exitTick = 0;
        if (affinities == null || affinities.isEmpty()) {
            phase = Phase.IDLE;
            return;
        }
        for (int i = 0; i < affinities.size(); i++) {
            WALKERS.add(new Walker(sheetFor(affinities.get(i)), i));
        }
        layout();
        phase = Phase.ENTERING;
    }

    /**
     * The load is done - send them off to the right.
     *
     * <p><b>Does not interrupt the entrance.</b> An arena usually builds faster than the row
     * takes to assemble, so this almost always arrives while walkers are still crossing. The
     * first version snapped everyone to their slot at that point, which meant the walk-on was
     * never actually seen - the row blinked into place and immediately left. Instead the
     * request is remembered and the exit begins once the row has formed, so the loading screen
     * is held for the entrance rather than the entrance being sacrificed to the load.
     */
    public static void beginExit() {
        if (phase == Phase.IDLE || phase == Phase.EXITING || phase == Phase.EMPTY_HOLD) return;
        // Only ever a request. The WAITING phase decides when to act on it, so the centre
        // hold is honoured even when the load finishes the instant the row lands.
        exitPending = true;
    }

    /** Actually set them walking off the right edge. */
    private static void startExit() {
        exitPending = false;
        exitTick = 0;
        phase = Phase.EXITING;
    }

    /** True while walkers are still on screen and the overlay must stay black. */
    public static boolean isBusy() {
        return phase != Phase.IDLE;
    }

    /**
     * Top edge of the walker row in GUI pixels, so callers can position around it.
     *
     * <p>Exposed rather than duplicated: the row's Y is derived from the draw height and the
     * baseline offset, and anything drawn near it has to move when those are tuned. Returns the
     * same value whether or not a cast is present, so a walker-less transition still lays text
     * out consistently.
     */
    public static int rowTopY() {
        int screenH = MinecraftClient.getInstance().getWindow().getScaledHeight();
        return screenH / 2 - DRAW_H / 2 + BASELINE_OFFSET;
    }

    /** Drop everything - combat ended, the player disconnected, the overlay was cancelled. */
    public static void clear() {
        WALKERS.clear();
        phase = Phase.IDLE;
        tick = 0;
        exitTick = 0;
        centeredTick = 0;
        emptyTick = 0;
        exitPending = false;
    }

    /** Assign each walker its slot in a row centred on screen, and park it off the left edge. */
    private static void layout() {
        MinecraftClient client = MinecraftClient.getInstance();
        int screenW = client.getWindow().getScaledWidth();
        int count = WALKERS.size();
        int rowW = count * DRAW_W + (count - 1) * SPACING;
        int rowLeft = (screenW - rowW) / 2;
        for (Walker w : WALKERS) {
            w.targetX = rowLeft + w.order * (DRAW_W + SPACING);
            // Everyone comes on from the same point just past the left edge and is spaced out
            // by the stagger instead of by their starting position. Starting each walker
            // further left made the later ones cross a longer distance in the same time, so
            // the row arrived at visibly different speeds.
            w.startX = -DRAW_W - 8f;
            w.x = w.startX;
            w.exitX = screenW + DRAW_W + 8f;
        }
    }

    /**
     * Advance one client tick. Call from the same place TransitionOverlay ticks.
     *
     * <p>The sequence is fixed and every step is waited out in full: black covers the screen,
     * the row walks on staggered from the left, it stands assembled for at least
     * {@link #CENTER_HOLD_TICKS}, it walks off staggered to the right, the empty screen holds
     * for {@link #EMPTY_HOLD_TICKS}, and only then does the overlay swipe away.
     */
    public static void tick() {
        if (phase == Phase.IDLE) return;
        tick++;
        switch (phase) {
            case ENTERING -> {
                boolean allArrived = true;
                for (Walker w : WALKERS) {
                    // Each walker waits out its stagger before setting off.
                    int since = tick - w.order * ENTRY_STAGGER_TICKS;
                    if (since <= 0) { allArrived = false; continue; }
                    float p = Math.min(1f, since / (float) ENTER_TICKS);
                    w.x = w.startX + (w.targetX - w.startX) * smooth(p);
                    if (p < 1f) {
                        w.animTick++;
                        allArrived = false;
                    }
                }
                // The row never resolving would hold a black screen forever; snap it rather
                // than let a slow frame or an odd resolution strand the sequence.
                if (tick > ENTRY_TIMEOUT_TICKS) {
                    for (Walker w : WALKERS) w.x = w.targetX;
                    allArrived = true;
                }
                if (allArrived) {
                    phase = Phase.WAITING;
                    centeredTick = 0;
                }
            }
            case WAITING -> {
                // Marking time: the cycle keeps running so the row is alive while it waits.
                for (Walker w : WALKERS) w.animTick++;
                centeredTick++;
                // Two conditions, both required. The hold is a MINIMUM so the assembled row is
                // actually seen even when the load finished before it formed; the load being
                // done is the other, because leaving early would just mean waiting on a black
                // screen instead. Whichever takes longer is what the player waits for.
                if (exitPending && centeredTick >= CENTER_HOLD_TICKS) startExit();
            }
            case EXITING -> {
                exitTick++;
                boolean allGone = true;
                for (Walker w : WALKERS) {
                    int since = exitTick - w.order * EXIT_STAGGER_TICKS;
                    if (since <= 0) { allGone = false; continue; }
                    float p = Math.min(1f, since / (float) EXIT_TICKS);
                    w.x = w.targetX + (w.exitX - w.targetX) * smooth(p);
                    if (p < 1f) {
                        w.animTick++;
                        allGone = false;
                    }
                }
                if (allGone) {
                    phase = Phase.EMPTY_HOLD;
                    emptyTick = 0;
                }
            }
            case EMPTY_HOLD -> {
                // A beat of empty black after the last one leaves, before the swipe off - so
                // the exit lands rather than being cut off by the curtain chasing them out.
                emptyTick++;
                if (emptyTick >= EMPTY_HOLD_TICKS) clear();
            }
            default -> {}
        }
    }

    /**
     * Draw the row. Called from the overlay's own render, after the black fill, so the walkers
     * sit on top of it - and only while it is opaque enough to hide the world behind them.
     */
    public static void render(DrawContext context, float overlayAlpha) {
        if (phase == Phase.IDLE || WALKERS.isEmpty()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        int screenH = client.getWindow().getScaledHeight();
        int top = screenH / 2 - DRAW_H / 2 + BASELINE_OFFSET;

        int a = (int) (Math.min(1f, overlayAlpha) * 255);
        if (a <= 0) return;
        int tint = 0xFFFFFF | (a << 24);

        for (Walker w : WALKERS) {
            int frameX = w.frame() * FRAME_W;
            drawFrame(context, w.sheet, Math.round(w.x), top, frameX, tint);
        }
    }

    /** One frame of one sheet, scaled to the on-screen walker size. */
    private static void drawFrame(DrawContext context, Identifier sheet,
                                  int x, int y, int frameX, int tint) {
        //? if <=1.21.1 {
        context.drawTexture(sheet, x, y, DRAW_W, DRAW_H,
            frameX, 0, FRAME_W, FRAME_H, SHEET_W, SHEET_H);
        //?} else {
        /*context.drawTexture(net.minecraft.client.render.RenderLayer::getGuiTextured, sheet,
            x, y, frameX, 0, DRAW_W, DRAW_H, FRAME_W, FRAME_H, SHEET_W, SHEET_H, tint);
        *///?}
    }

    /**
     * The sheet for an affinity name.
     *
     * <p>Names arrive as the server's enum constants (SLASHING, CLEAVING, ...) and the files are
     * {@code PlayerLoadingWalking<Name>.png}. A player who has never spent an affinity point has
     * no strongest affinity, so the server sends the default rather than inventing one.
     */
    private static Identifier sheetFor(String affinityName) {
        String name = affinityName == null ? "" : affinityName.trim().toUpperCase(java.util.Locale.ROOT);
        String file = switch (name) {
            case "SLASHING"  -> "Slashing";
            case "CLEAVING"  -> "Cleaving";
            case "BLUNT"     -> "Blunt";
            case "RANGED"    -> "Ranged";
            case "WATER"     -> "Water";
            case "SPECIAL"   -> "Special";
            case "PET"       -> "Pet";
            case "PHYSICAL"  -> "Physical";
            default          -> "Physical";   // unspent affinities, or anything unrecognised
        };
        return Identifier.of("craftics", "textures/loading_animations/PlayerLoadingWalking" + file + ".png");
    }
}
