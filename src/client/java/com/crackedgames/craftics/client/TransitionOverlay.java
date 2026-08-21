package com.crackedgames.craftics.client;

import com.crackedgames.craftics.client.guide.GuideTheme;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;

/**
 * Full-screen fade-to-black overlay for level transitions.
 * Fades in -> holds at full black -> action fires -> fades out.
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
        "Axes ignore armor, great against heavily armored foes.",
        "Maces deal AoE damage to all enemies in a 3x3 area.",
        "Sticks and bamboo have a chance to stun enemies for a turn.",
        "Tridents can be thrown in straight or diagonal lines, but you have to walk over them to pick them up.",
        "Food heals you in combat. Cooked rabbit stew restores the most HP.",
        "Potions cost 1 AP to use and apply combat effects for several turns.",
        "Tipped arrows apply their potion effect to enemies hit by your bow.",
        "Golden armor gives a 15% critical hit chance.",
        "Netherite armor grants +1 to ALL damage types.",
        "Diamond swords have a 30% crit chance for double damage.",
        "Netherite swords deal triple damage to enemies below 30% HP.",
        "Enchantments on your gear have real combat effects. Check their tooltips.",
        "Armor trims grant unique combat bonuses depending on the template pattern.",
        "A full set of matching trims unlocks a powerful set bonus.",
        "Each biome has different enemy types. Learn their resistances.",
        "Bosses have two phases. They get new abilities below 50% HP.",
        "Some enemies are weak to specific damage types. Experiment!",
        "You can level up by defeating bosses. Each level grants stat or affinity points.",
        "Affinity points boost a damage type by +3 per point, plus a unique passive effect.",
        "Slashing affinity increases your sword sweep chance by 5% per point.",
        "Ranged affinity gives a 5% ricochet chain chance per point.",
        "Blunt affinity adds a 3% stun chance per point.",
        "Physical affinity gives a 3% counterattack chance when you're hit.",
        "Special affinity has a 3% chance to make an attack cost 0 AP.",
        "Pet affinity boosts your tamed allies with +3 HP per point.",
        "Mob skulls are rare 1% drops. Wear them for a +1 damage type bonus.",
        "Skeleton Skull: +1 Ranged. Creeper Head: +1 Blunt. Zombie Head: +1 Physical.",
        "Piglin Head grants +1 Slashing. Wither Skull grants +1 Special.",
        "Pottery sherds are powerful spell scrolls. Each one has a unique combat ability.",
        "Goat horns have different effects based on their variant. Check the tooltip.",
        "Recovery Compass saves your inventory on death. Keep one in your hotbar.",
        "Totem of Undying activates automatically in combat, saving you from a killing blow.",
        "You can tame passive mobs by clicking them. They'll fight alongside you.",
        "Tamed allies attack enemies on their own turn and benefit from your Pet affinity.",
        "Emeralds are earned from combat. Spend them at traders between fights.",
        "Golden Apples heal 8 HP and grant Absorption in combat.",
        "Enchanted Golden Apples restore full HP plus Absorption IV, Resistance, and Regen II.",
        "Crossbow bolts pierce through targets and hit enemies behind them for 50% damage.",
        "Bows with Infinity don't consume arrows, but tipped arrows are still spent.",
        "Fire Aspect on your weapon ignites enemies, dealing burn damage over time.",
        "Knockback enchantment pushes enemies back after a melee hit.",
        "Looting increases the chance of enemies dropping their equipped gear.",
        "Protection enchants on armor reduce incoming damage from all sources.",
        "Thorns on armor reflects damage back at enemies who hit you.",
        "Feather Falling negates knockback effects from enemy attacks.",
        "Watch for red warning tiles. They show where a boss attack will land next turn.",
        "Bosses telegraph their big attacks with particles and highlighted tiles. Move off them!",
        "Speed stat determines how many tiles you can move per turn.",
        "AP (Action Points) are spent on attacks, items, and abilities. Plan your turns!",
        "You can end your turn early to save AP for a better opportunity.",
        "Water tiles apply the Soaked debuff. Soaked enemies take double lightning damage.",
        "Fire tiles deal damage when stepped on. Enemies avoid walking through them.",
        "Some enemies can fly, ignoring obstacles and terrain hazards.",
        "Spiders can climb to the ceiling and drop on you from above!",
        "Endermen teleport. Ranged attacks may miss them entirely.",
        "The Ender Dragon swoops across the arena. Watch for the warning tiles and deflect.",
        "Witches throw potions that apply harmful effects. Take them out quickly!",
        "Creepers explode after a fuse turn. Run or kill them before they blow.",
        "Baby mobs are faster but weaker than their adult counterparts.",
        "Invite friends to your party for co-op combat. Enemies scale with party size.",
        "In a party, enemies gain +25% HP per extra player.",
        "Kill streaks grant bonus emeralds. Chain kills for maximum profit.",
        "Hoes are Special-type weapons. Low base damage, but boosted by Special affinity.",
        "Shovels are Pet-type weapons. Their damage scales with Pet affinity.",
        "Blaze Rods deal fire damage and have a stun chance.",
        "Coral weapons deal Water-type damage, great with Water affinity.",
        "The Move item is locked to one hotbar slot. Use Left/Right arrow keys to rotate which slot it sits in.",
        "Holding the Move item enters Move mode. Click a highlighted tile to walk there.",
        "The Move item is persistent. It can't be dropped or moved out of its slot.",
        "Spawn eggs cost 2 AP and summon a one-battle ally on a target tile within 5 squares.",
        "Spawn eggs are rare drops from victories, about 4% chance, boosted by luck items.",
        "Bees fly over water and obstacles, ignoring terrain hazards entirely.",
        "Bees move 4 tiles per turn, faster than most melee mobs. Don't let them swarm you.",
        "Trial Chambers are optional rare-loot encounters that appear after winning a fight.",
        "Trial Chamber Breezes hit hard. Their wind charge knocks you and nearby mobs back 2 tiles.",
        "Bogged arrows apply Poison for 3 turns. Eat to recover or wait it out.",
        "Stacked enemies act as one mob until killed. Defeat the mount and the rider drops onto the same tile.",
        "Zombie Stack: kill the adult and a baby zombie drops off, ready to fight next turn.",
        "Skeleton Horsemen split into a Skeleton Horse and a Skeleton when you defeat them.",
        "Press F1 to hide all combat UI for a clean screenshot.",
        "The Loot Management screen lets you cherry-pick rewards when your inventory is overflowing.",
        "\"Active In Party\" floats above mobs in your battle party. Shift+Right-Click to add or remove.",
        "Trial Chambers ignore biome scaling. They have their own difficulty curve based on NG+ level.",
        "Wind Charges blast you 2 tiles away from an adjacent target, useful for quick repositioning.",
        "Self-launching with a Wind Charge grants Airtime: your next weapon hit deals +0.5x per stack, and ranged attacks reach +2 tiles per stack.",
        "Rocket Crossbows have AOE damage at long range. Load a Firework Rocket in your off-hand.",
        "The Affinities Respec menu lets you redistribute affinity points, costing 1 XP level per refund.",
        "Iron golems are healed by iron ingots in combat. Heal-items get used on a tile-click, not on your golem.",
        "Force-killing a stacked enemy with one shot will still spawn its rider. You can't skip the drop.",
        "Bringing a mountable ally into battle makes you ride them automatically. (Usually needs a saddle)",
        "Hold a Lead in combat to command allies. Click an ally to select, then click a target.",
        "A lead-commanded ally attacks for free without using their AI turn that round.",
        "Lead commands cost 2 AP. The ally can only attack enemies adjacent to it.",
        "Use a Lead to reposition an ally to a safer tile, or set up the perfect attack angle.",
        "Click your selected ally again to cancel a lead command without spending any AP.",
        "Hold a full block and click a nearby tile for 1 AP to drop a temporary wall. It lasts 4 turns. Mine it to get the block back.",
        "Click a void or sunken tile with a full block to bridge it at ground level instead of walling it. The fill stays for the whole fight.",
        "Place a Campfire on a tile to create a 5x5 healing zone. Stay inside it for +2 HP per turn.",
        "Scaffolding gives +1 range to ranged attacks made from that tile. Place it for high-ground value.",
        "Honey Blocks are floor traps. Enemies that walk onto one lose all remaining movement. They look just like normal floor to the AI.",
        "Slime Blocks are floor traps that bounce enemies. They get stunned and lose remaining movement when they step on one.",
        "Powder Snow Buckets place a powder snow tile. Enemies that step on it sink and freeze. Returns an empty bucket.",
        "Sponge clears the water tile you target and adjacent water tiles in a cross. The sponge block itself blocks movement.",
        "Spore Blossom places a decorative block and slows every enemy within 3 tiles by 1 speed.",
        "Jukebox plays music for the whole arena. Every ally gets +3 Speed for the rest of the battle.",
        "Bell rings out at a placement tile and stuns every enemy within 2 tiles for 1 turn.",
        "Anvil drops from above the target enemy with a real falling-block animation, then crushes them for 15 Special damage.",
        "Drag with the right mouse button to orbit the camera around the arena for a better angle.",
        "Drag with the middle mouse button to pan the camera. Hold Shift while dragging to recenter it.",
        "Scroll the mouse wheel to zoom the camera in and out during combat.",
    };

    private static final java.util.Random TIP_RNG = new java.util.Random();

    // Timing (in ticks)
    // A swipe wants to be quicker than the fade it replaced: a curtain crossing the screen
    // reads as slow long before a dissolve does, and the walkers are the part worth watching.
    private static final int FADE_IN_TICKS = 8;    // ~0.4s swipe on
    private static final int HOLD_TICKS = 5;        // brief hold at full black
    private static final int FADE_OUT_TICKS = 8;   // ~0.4s swipe off
    private static int holdTimer = 0;
    /** The server said the load finished while walkers were still on screen; sweep once
     *  they have left. Cleared when it fires, and by {@link #startTransition} so a fresh
     *  transition can never inherit the previous one's pending exit. */
    private static boolean exitRequested = false;

    /**
     * Start a transition. Screen fades to black, then fires the action callback.
     * Call startFadeOut() later (e.g. when a response payload arrives) to reveal the new scene.
     *
     * @param text     Main text shown centered on black screen (e.g. "Plains - Level 1")
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
        exitRequested = false;
        // A transition starting while the last one still has walkers on screen means the
        // previous sequence was abandoned (a cancelled load, a second transition racing the
        // first). Clear them rather than let the old cast bleed into the new one's row.
        LoadingWalkers.clear();
        currentTip = TIPS[TIP_RNG.nextInt(TIPS.length)];
        state = State.FADING_IN;
    }

    /**
     * Begin fading the overlay away, revealing the new scene.
     * Called when the server response arrives (EnterCombatPayload, ExitCombatPayload, etc.)
     */
    public static void startFadeOut() {
        if (state == State.IDLE) return;
        // The load is done, but the walkers have not left yet. Send them off the right edge
        // first and let tick() start the sweep once the screen is theirs to give back -
        // sweeping now would wipe them mid-stride, which is the one beat the sequence exists
        // to show. With no walkers on screen this is a no-op and the fade starts immediately.
        if (LoadingWalkers.isBusy()) {
            exitRequested = true;
            LoadingWalkers.beginExit();
            return;
        }
        state = State.FADING_OUT;
    }

    /**
     * Start a transition that a party walks across - see {@link LoadingWalkers}.
     *
     * <p>{@code cast} is the server's list of affinity names, one per member. Empty means no
     * walkers and this behaves exactly like {@link #startTransition}.
     */
    public static void startTransitionWithCast(String text, String subtitle,
                                               java.util.List<String> cast, Runnable action) {
        // A transition already running is the normal case, not an error: the victory screen
        // starts one the moment its button is pressed, and the server's loading screen arrives
        // a moment later carrying the cast. Restarting here replayed the swipe from zero - the
        // "swipe twice" - and reset the walkers to the left edge, which is why they appeared
        // to jump most of the way to the centre. Adopt the cast into the running transition
        // instead, and only start a new one when nothing is on screen.
        if (isActive()) {
            if (!LoadingWalkers.isBusy()) LoadingWalkers.begin(cast);
            return;
        }
        startTransition(text, subtitle, action);
        LoadingWalkers.begin(cast);
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
        LoadingWalkers.tick();
        // The walkers finished filing off the right edge, so the screen can be handed back.
        // Deliberately checked here rather than from inside LoadingWalkers: the overlay owns
        // its own state machine, and a renderer reaching in to drive it is how the two end up
        // disagreeing about whether a transition is running.
        if (state == State.HOLDING && exitRequested && !LoadingWalkers.isBusy()) {
            exitRequested = false;
            state = State.FADING_OUT;
        }
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
                    // Normally we stay holding until startFadeOut() is called externally, by
                    // the server's response. But every one of those six calls is a payload, and
                    // a payload that never arrives - the arena build threw, the party leader
                    // dropped, a proxy moved the player between backends mid-transition - used
                    // to leave this holding forever.
                    //
                    // That is not a cosmetic stall. HudTransitionMixin cancels InGameHud.render
                    // outright while this is active, taking the hotbar, health, chat and every
                    // Craftics HUD layer with it, so the player is left facing an opaque black
                    // screen with no message and nothing to click. It survived quit-to-title and
                    // rejoining, because reset() had no callers - only restarting the game
                    // cleared it.
                    //
                    // So the hold gets an outer bound. Fading out on a timeout shows the world
                    // again, which at worst reveals a transition that did not finish; staying
                    // black shows nothing and cannot be recovered from at all.
                    holdTimer = 0;
                    if (++strandedTicks >= MAX_HOLD_TICKS) {
                        com.crackedgames.craftics.CrafticsMod.LOGGER.warn(
                            "Transition overlay held for {} ticks with no server response;"
                            + " fading out so the HUD is not lost.", MAX_HOLD_TICKS);
                        startFadeOut();
                    }
                } else {
                    strandedTicks = 0;
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
        if (MinecraftClient.getInstance().options.hudHidden) return;
        if (state == State.IDLE || alpha <= 0f) return;

        MinecraftClient client = MinecraftClient.getInstance();
        int screenW = client.getWindow().getScaledWidth();
        int screenH = client.getWindow().getScaledHeight();

        // A swipe, not a fade. The black is always fully opaque; what changes is how much of
        // the screen it covers. It enters from the left, crosses, holds the whole screen, then
        // keeps travelling the same way to uncover - so the curtain reads as one object moving
        // across rather than the world dimming and brightening in place.
        //
        // alpha is still the 0..1 coverage, so isFullyBlack() and every caller that reasons
        // about "is the screen hidden yet" behave exactly as before.
        final int black = 0xFF000000;
        int covered = Math.round(screenW * alpha);
        if (state == State.FADING_OUT) {
            // Uncovering: the left edge of the black marches right, revealing behind it.
            int left = screenW - covered;
            context.fill(left, 0, screenW, screenH, black);
        } else {
            context.fill(0, 0, covered, screenH, black);
        }

        // The cast only draws once the curtain has actually covered the screen - part-way
        // through a swipe they would be standing on the live world.
        if (alpha >= 1f) {
            LoadingWalkers.render(context, 1f);
        }

        // Title and subtitle sit ABOVE the row rather than dead centre, which is where the
        // walkers stand. Both are anchored off LoadingWalkers.rowTopY() instead of the screen
        // centre, so tuning the walkers' size or baseline moves the text with them rather than
        // leaving it overlapping the sprites.
        if (alpha >= 1f) {
            int rowTop = LoadingWalkers.rowTopY();
            if (!displayText.isEmpty()) {
                context.drawCenteredTextWithShadow(client.textRenderer,
                    Text.literal(displayText), screenW / 2, rowTop - 34, 0xFFFFFFFF);
            }
            if (!subtitleText.isEmpty()) {
                context.drawCenteredTextWithShadow(client.textRenderer,
                    Text.literal(subtitleText), screenW / 2, rowTop - 20, 0xFFAAAAAA);
            }
        }

        // The tip stays pinned to the bottom edge, well clear of the row.
        if (alpha >= 1f && !currentTip.isEmpty()) {
            Text tipLine = Text.literal("TIP: ")
                .styled(s -> s.withBold(true)
                    .withColor(net.minecraft.text.TextColor.fromRgb(GuideTheme.GOLD & 0x00FFFFFF)))
                .append(Text.literal(currentTip).styled(s -> s.withBold(false)));
            context.drawCenteredTextWithShadow(
                client.textRenderer, tipLine,
                screenW / 2, screenH - 14, 0xFF888888);
        }
    }

    /**
     * Ticks the hold may overrun its timer before the overlay gives up and fades out.
     *
     * <p>Generous on purpose - 30 seconds. A slow arena build on a loaded server is normal and
     * must not be interrupted; this is a backstop against a response that is never coming, not
     * a deadline for one that is merely late.
     */
    private static final int MAX_HOLD_TICKS = 20 * 30;

    /** Ticks spent holding past holdTimer with no server response. */
    private static int strandedTicks = 0;

    /** Force-reset the overlay (e.g. on disconnect). */
    public static void reset() {
        strandedTicks = 0;
        state = State.IDLE;
        alpha = 0f;
        displayText = "";
        subtitleText = "";
        currentTip = "";
        onFullyBlack = null;
        actionFired = false;
    }
}
