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
    private static String currentTip = "";

    private static final String[] TIPS = {
        "Swords have a chance to sweep adjacent enemies when attacking.",
        "Axes ignore armor — great against heavily armored foes.",
        "Maces deal AoE damage to all enemies in a 3x3 area.",
        "Sticks and bamboo have a chance to stun enemies for a turn.",
        "Tridents can be thrown in straight or diagonal lines, but you have to walk over them to pick them up.",
        "Food heals you in combat — cooked rabbit stew restores the most HP!",
        "Potions cost 1 AP to use and apply combat effects for several turns.",
        "Tipped arrows apply their potion effect to enemies hit by your bow.",
        "Golden armor gives a 15% critical hit chance.",
        "Netherite armor grants +1 to ALL damage types.",
        "Diamond swords have a 30% crit chance for double damage.",
        "Netherite swords deal triple damage to enemies below 30% HP.",
        "Enchantments on your gear have real combat effects — check their tooltips!",
        "Armor trims grant unique combat bonuses depending on the template pattern.",
        "A full set of matching trims unlocks a powerful set bonus.",
        "Each biome has different enemy types — learn their resistances!",
        "Bosses have two phases — they get new abilities below 50% HP.",
        "Some enemies are weak to specific damage types. Experiment!",
        "You can level up by defeating bosses. Each level grants stat or affinity points.",
        "Affinity points boost a damage type by +3 per point, plus a unique passive effect.",
        "Slashing affinity increases your sword sweep chance by 5% per point.",
        "Ranged affinity gives a 5% ricochet chain chance per point.",
        "Blunt affinity adds a 3% stun chance per point.",
        "Physical affinity gives a 3% counterattack chance when you're hit.",
        "Special affinity has a 3% chance to make an attack cost 0 AP.",
        "Pet affinity boosts your tamed allies with +3 HP per point.",
        "Mob skulls are rare 1% drops — wear them for a +1 damage type bonus!",
        "Skeleton Skull: +1 Ranged. Creeper Head: +1 Blunt. Zombie Head: +1 Physical.",
        "Piglin Head grants +1 Slashing. Wither Skull grants +1 Special.",
        "Pottery sherds are powerful spell scrolls — each one has a unique combat ability.",
        "Goat horns have different effects based on their variant — check the tooltip!",
        "Recovery Compass saves your inventory on death — keep one in your hotbar.",
        "Totem of Undying activates automatically in combat, saving you from a killing blow.",
        "You can tame passive mobs by clicking them — they'll fight alongside you!",
        "Tamed allies attack enemies on their own turn and benefit from your Pet affinity.",
        "Emeralds are earned from combat — spend them at traders between fights.",
        "Golden Apples heal 8 HP and grant Absorption in combat.",
        "Enchanted Golden Apples restore full HP plus Absorption IV, Resistance, and Regen II.",
        "Crossbow bolts pierce through targets and hit enemies behind them for 50% damage.",
        "Bows with Infinity don't consume arrows — but tipped arrows are still spent.",
        "Fire Aspect on your weapon ignites enemies, dealing burn damage over time.",
        "Knockback enchantment pushes enemies back after a melee hit.",
        "Looting increases the chance of enemies dropping their equipped gear.",
        "Protection enchants on armor reduce incoming damage from all sources.",
        "Thorns on armor reflects damage back at enemies who hit you.",
        "Feather Falling negates knockback effects from enemy attacks.",
        "Watch for red warning tiles — they show where a boss attack will land next turn!",
        "Bosses telegraph their big attacks with particles and highlighted tiles. Move off them!",
        "Speed stat determines how many tiles you can move per turn.",
        "AP (Action Points) are spent on attacks, items, and abilities. Plan your turns!",
        "You can end your turn early to save AP for a better opportunity.",
        "Water tiles apply the Soaked debuff — soaked enemies take double lightning damage.",
        "Fire tiles deal damage when stepped on. Enemies avoid walking through them.",
        "Some enemies can fly, ignoring obstacles and terrain hazards.",
        "Spiders can climb to the ceiling and drop on you from above!",
        "Endermen teleport — ranged attacks may miss them entirely.",
        "The Ender Dragon swoops across the arena — watch for the warning tiles and dodge!",
        "Witches throw potions that apply harmful effects. Take them out quickly!",
        "Creepers explode after a fuse turn — run or kill them before they blow!",
        "Baby mobs are faster but weaker than their adult counterparts.",
        "Invite friends to your party for co-op combat — enemies scale with party size.",
        "In a party, enemies gain +25% HP per extra player.",
        "Kill streaks grant bonus emeralds — chain kills for maximum profit!",
        "Hoes are Special-type weapons — low base damage, but boosted by Special affinity.",
        "Shovels are Pet-type weapons — their damage scales with Pet affinity.",
        "Blaze Rods deal fire damage and have a stun chance.",
        "Coral weapons deal Water-type damage — great with Water affinity!",
    };

    private static final java.util.Random TIP_RNG = new java.util.Random();

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
        currentTip = TIPS[TIP_RNG.nextInt(TIPS.length)];
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

            // Tip at the bottom of the screen
            if (!currentTip.isEmpty()) {
                int tipColor = 0x888888 | (textAlpha << 24);
                int labelColor = 0xFFAA00 | (textAlpha << 24);
                context.drawCenteredTextWithShadow(
                    client.textRenderer,
                    Text.literal("\u00a7l\u2B50 TIP: ").append(Text.literal(currentTip).styled(s -> s.withBold(false))),
                    screenW / 2, screenH - 30,
                    tipColor
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
        currentTip = "";
        onFullyBlack = null;
        actionFired = false;
    }
}
