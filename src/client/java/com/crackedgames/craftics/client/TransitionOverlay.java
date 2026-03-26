package com.crackedgames.craftics.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;

/**
 * Full-screen fade-to-black overlay for level transitions.
 * Fades in → holds at full black → action fires → fades out.
 */
public class TransitionOverlay {

    private enum State { IDLE, FADING_IN, HOLDING, FADING_OUT }

    private static State state = State.IDLE;
    private static float alpha = 0f;
    private static String displayText = "";
    private static String subtitleText = "";
    private static Runnable onFullyBlack = null;
    private static boolean actionFired = false;

    // Timing (in ticks)
    private static final int FADE_IN_TICKS = 15;   // ~0.75s
    private static final int HOLD_TICKS = 5;        // brief hold at full black
    private static final int FADE_OUT_TICKS = 15;   // ~0.75s
    private static int holdTimer = 0;

    /**
     * Start a transition. Screen fades to black, then fires the action callback.
     * Call startFadeOut() later (e.g. when a response payload arrives) to reveal the new scene.
     *
     * @param text     Main text shown centered on black screen (e.g. "Plains — Level 1")
     * @param subtitle Smaller text below main (e.g. "Entering the arena..." or "")
     * @param action   Runs once the screen is fully black (send packet, etc.)
     */
    public static void startTransition(String text, String subtitle, Runnable action) {
        displayText = text != null ? text : "";
        subtitleText = subtitle != null ? subtitle : "";
        onFullyBlack = action;
        actionFired = false;
        alpha = 0f;
        holdTimer = 0;
        state = State.FADING_IN;
    }

    /**
     * Begin fading the overlay away, revealing the new scene.
     * Called when the server response arrives (EnterCombatPayload, ExitCombatPayload, etc.)
     */
    public static void startFadeOut() {
        if (state == State.IDLE) return;
        state = State.FADING_OUT;
    }

    /** Whether a transition is currently active (any phase). */
    public static boolean isActive() {
        return state != State.IDLE;
    }

    /** Whether the screen is fully black (safe to teleport/switch scenes). */
    public static boolean isFullyBlack() {
        return (state == State.HOLDING || (state == State.FADING_OUT && alpha > 0.95f));
    }

    /** Advance the fade state machine. Call once per client tick. */
    public static void tick() {
        switch (state) {
            case FADING_IN -> {
                alpha += 1f / FADE_IN_TICKS;
                if (alpha >= 1f) {
                    alpha = 1f;
                    state = State.HOLDING;
                    holdTimer = HOLD_TICKS;
                    // Fire the action once we're fully black
                    if (!actionFired && onFullyBlack != null) {
                        actionFired = true;
                        onFullyBlack.run();
                        onFullyBlack = null;
                    }
                }
            }
            case HOLDING -> {
                alpha = 1f;
                holdTimer--;
                if (holdTimer <= 0) {
                    // Stay holding until startFadeOut() is called externally
                    // (the server response triggers it)
                    holdTimer = 0;
                }
            }
            case FADING_OUT -> {
                alpha -= 1f / FADE_OUT_TICKS;
                if (alpha <= 0f) {
                    alpha = 0f;
                    state = State.IDLE;
                    displayText = "";
                    subtitleText = "";
                }
            }
            default -> {}
        }
    }

    /** Render the overlay. Call from HudRenderCallback. */
    public static void render(DrawContext context, RenderTickCounter tickCounter) {
        if (state == State.IDLE || alpha <= 0f) return;

        MinecraftClient client = MinecraftClient.getInstance();
        int screenW = client.getWindow().getScaledWidth();
        int screenH = client.getWindow().getScaledHeight();

        int a = (int) (alpha * 255);
        int color = (a << 24); // black with variable alpha

        // Full-screen black fill
        context.fill(0, 0, screenW, screenH, color);

        // Draw text only when mostly opaque
        if (alpha > 0.5f) {
            int textAlpha = (int) (Math.min(1f, (alpha - 0.5f) * 2f) * 255);

            if (!displayText.isEmpty()) {
                int textColor = 0xFFFFFF | (textAlpha << 24);
                context.drawCenteredTextWithShadow(
                    client.textRenderer,
                    Text.literal(displayText),
                    screenW / 2, screenH / 2 - 10,
                    textColor
                );
            }

            if (!subtitleText.isEmpty()) {
                int subColor = 0xAAAAAA | (textAlpha << 24);
                context.drawCenteredTextWithShadow(
                    client.textRenderer,
                    Text.literal(subtitleText),
                    screenW / 2, screenH / 2 + 8,
                    subColor
                );
            }
        }
    }

    /** Force-reset the overlay (e.g. on disconnect). */
    public static void reset() {
        state = State.IDLE;
        alpha = 0f;
        displayText = "";
        subtitleText = "";
        onFullyBlack = null;
        actionFired = false;
    }
}
