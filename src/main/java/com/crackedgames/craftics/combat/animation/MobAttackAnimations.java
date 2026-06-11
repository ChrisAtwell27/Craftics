package com.crackedgames.craftics.combat.animation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-mob-type attack animation registry + keyframe curves.
 *
 * <p>Every combat attack plays a short positional animation (the mob leans,
 * lunges, hops or channels) driven by {@code CombatManager.tickEnemyAnimating}.
 * Which curve a mob plays is decided here: an explicit registration wins,
 * otherwise ranged attacks use {@link Style#RANGED_DRAW} and everything else
 * falls back to {@link Style#LUNGE}. This replaces the old hardcoded
 * {@code isSpiderLike}/{@code isHeavyHitter}/... chain so addons can give
 * their own mobs a fitting attack style:
 *
 * <pre>{@code CrafticsAPI.registerAttackAnimation("mymod:lava_crab", Style.POUNCE);}</pre>
 *
 * <p>Each style is a tick-indexed curve of (forward, rise) offsets from the
 * mob's origin along its facing, plus a strike flag on the impact frame (hand
 * swing + {@link AnimState#ATTACK} pose/FX) and a done flag.
 */
public final class MobAttackAnimations {
    private MobAttackAnimations() {}

    /** The attack-animation archetypes. */
    public enum Style {
        /** Default melee: ease forward, ease back. */
        LUNGE,
        /** Crouch low, spring forward and up (spiders). */
        POUNCE,
        /** Slow rise, fast forward slam (golems, ravagers, wardens). */
        SLAM,
        /** Fast low dash with a held strike pose (wolves, cats, vexes). */
        DASH,
        /** Hop straight up, slam down toward the target (slimes). */
        BOUNCE,
        /** Sink-flicker, jump-cut beside the target, ease back (endermen). */
        BLINK,
        /** Back up a step, head-down charge with extra reach (goats, camels). */
        RAM,
        /** Two quick small pokes (insects and small critters). */
        JAB,
        /** Stand and channel — arms raised ({@link AnimState#CAST}), bob, release (witches, evokers). */
        CAST,
        /** Lean away to draw/aim, snap forward on release (all ranged attacks). */
        RANGED_DRAW
    }

    /** One tick of an attack curve. {@code forward} is along the mob→target line. */
    public record Frame(double forward, double rise, boolean strike, boolean done) {}

    private static final Map<String, Style> BY_TYPE = new ConcurrentHashMap<>();

    static {
        register("minecraft:spider", Style.POUNCE);
        register("minecraft:cave_spider", Style.POUNCE);

        register("minecraft:iron_golem", Style.SLAM);
        register("minecraft:ravager", Style.SLAM);
        register("minecraft:warden", Style.SLAM);
        register("minecraft:hoglin", Style.SLAM);
        register("minecraft:zoglin", Style.SLAM);
        register("minecraft:polar_bear", Style.SLAM);

        register("minecraft:wolf", Style.DASH);
        register("minecraft:fox", Style.DASH);
        register("minecraft:ocelot", Style.DASH);
        register("minecraft:cat", Style.DASH);
        register("minecraft:vex", Style.DASH);
        register("minecraft:phantom", Style.DASH);

        register("minecraft:slime", Style.BOUNCE);
        register("minecraft:magma_cube", Style.BOUNCE);

        register("minecraft:enderman", Style.BLINK);
        register("minecraft:endermite", Style.BLINK);

        register("minecraft:goat", Style.RAM);
        register("minecraft:camel", Style.RAM);

        register("minecraft:silverfish", Style.JAB);
        register("minecraft:bee", Style.JAB);
        register("minecraft:chicken", Style.JAB);
        register("minecraft:parrot", Style.JAB);
        register("minecraft:rabbit", Style.JAB);
        register("minecraft:frog", Style.JAB);

        register("minecraft:witch", Style.CAST);
        register("minecraft:evoker", Style.CAST);
        register("minecraft:illusioner", Style.CAST);
    }

    /** Register (or override) the attack style for an entity type. Addon-safe; last wins. */
    public static void register(String entityTypeId, Style style) {
        if (entityTypeId != null && style != null) BY_TYPE.put(entityTypeId, style);
    }

    /**
     * Resolve the style for an attack. Ranged attacks always read as a draw
     * unless the mob is a registered caster — a witch lobbing a potion should
     * channel, not nock an arrow.
     */
    public static Style styleFor(String entityTypeId, boolean isRanged) {
        Style s = entityTypeId != null ? BY_TYPE.get(entityTypeId) : null;
        if (isRanged) {
            return s == Style.CAST ? Style.CAST : Style.RANGED_DRAW;
        }
        return s != null ? s : Style.LUNGE;
    }

    /** The pose to enter when the attack begins (casters channel, everyone else winds up). */
    public static AnimState windupPose(Style style) {
        return style == Style.CAST ? AnimState.CAST : AnimState.WINDUP;
    }

    /** Whether the strike frame should also flash the {@link AnimState#ATTACK} pose/FX (melee swings only). */
    public static boolean strikeUsesAttackPose(Style style) {
        return style != Style.RANGED_DRAW && style != Style.CAST;
    }

    /**
     * The (forward, rise) offset for {@code tick} (1-based) of {@code style}.
     * {@code strike} marks the impact frame; {@code done} marks completion
     * (the caller snaps the mob back to its origin).
     */
    public static Frame frameAt(Style style, int tick) {
        return switch (style) {
            case LUNGE -> phased(tick,
                // forward 5, back 4 — strike at full extension
                new Phase(5, p -> 0.55 * easeOut(p), p -> 0.0, 4),
                new Phase(4, p -> 0.55 * (1 - easeIn(p)), p -> 0.0, -1));
            case POUNCE -> phased(tick,
                new Phase(4, p -> 0.0, p -> -0.2 * easeIn(p), -1),            // crouch
                new Phase(3, p -> 0.7 * easeOut(p), p -> -0.2 + 0.35 * easeOut(p), 1), // spring
                new Phase(3, p -> 0.7 * (1 - easeIn(p)), p -> 0.15 * (1 - easeIn(p)), -1));
            case SLAM -> phased(tick,
                new Phase(6, p -> 0.0, p -> 0.25 * easeIn(p), -1),            // wind up tall
                new Phase(2, p -> 0.6 * easeOut(p), p -> 0.25 * (1 - easeOut(p)), 1), // slam
                new Phase(4, p -> 0.6 * (1 - easeIn(p)), p -> 0.0, -1));
            case DASH -> phased(tick,
                new Phase(3, p -> 0.65 * easeOut(p), p -> -0.1 * easeOut(p), 1), // low dash
                new Phase(2, p -> 0.65, p -> -0.1, -1),                          // hold
                new Phase(3, p -> 0.65 * (1 - easeIn(p)), p -> -0.1 * (1 - easeIn(p)), -1));
            case BOUNCE -> phased(tick,
                new Phase(4, p -> 0.0, p -> 0.6 * Math.sin(p * Math.PI * 0.5), -1), // rise
                new Phase(2, p -> 0.5 * easeIn(p), p -> 0.6 * (1 - easeIn(p)), 1),  // slam down
                new Phase(3, p -> 0.5 * (1 - easeIn(p)), p -> 0.0, -1));
            case BLINK -> phased(tick,
                new Phase(3, p -> 0.0, p -> -0.3 * p, -1),                    // sink/flicker
                new Phase(2, p -> 0.7, p -> 0.0, 1),                          // jump-cut beside target
                new Phase(4, p -> 0.7 * (1 - easeIn(p)), p -> 0.0, -1));
            case RAM -> phased(tick,
                new Phase(4, p -> -0.15 * easeIn(p), p -> -0.08 * easeIn(p), -1), // back up, head down
                new Phase(2, p -> -0.15 + 1.0 * easeOut(p), p -> -0.12, 1),       // thunder forward
                new Phase(4, p -> 0.85 * (1 - easeIn(p)), p -> -0.12 * (1 - easeIn(p)), -1));
            case JAB -> phased(tick,
                new Phase(2, p -> 0.3 * easeOut(p), p -> 0.0, 1),             // poke one
                new Phase(2, p -> 0.3 * (1 - p), p -> 0.0, -1),
                new Phase(2, p -> 0.4 * easeOut(p), p -> 0.0, 1),             // poke two
                new Phase(2, p -> 0.4 * (1 - p), p -> 0.0, -1));
            case CAST -> phased(tick,
                // Channel in place with a gentle bob, release near the end.
                new Phase(9, p -> 0.0, p -> 0.05 * Math.sin(p * 9 * 0.6), -1),
                new Phase(2, p -> 0.15 * easeOut(p), p -> 0.0, 1),            // release flick
                new Phase(3, p -> 0.15 * (1 - p), p -> 0.0, -1));
            case RANGED_DRAW -> phased(tick,
                new Phase(6, p -> -0.35 * easeIn(p), p -> 0.0, -1),           // draw — lean away
                new Phase(3, p -> -0.35 * (1 - easeOut(p)), p -> 0.0, 1));    // release — snap back
        };
    }

    // ── curve plumbing ────────────────────────────────────────────────────

    private interface Curve { double at(double p); }

    /** One animation phase: {@code ticks} long; {@code strikeTick} is 1-based within the phase, -1 = none. */
    private record Phase(int ticks, Curve forward, Curve rise, int strikeTick) {}

    private static Frame phased(int tick, Phase... phases) {
        int t = tick;
        int total = 0;
        for (Phase ph : phases) total += ph.ticks();
        for (Phase ph : phases) {
            if (t <= ph.ticks()) {
                double p = (double) t / ph.ticks();
                return new Frame(ph.forward().at(p), ph.rise().at(p),
                    t == ph.strikeTick(), tick >= total);
            }
            t -= ph.ticks();
        }
        // Past the end — hold origin, report done.
        return new Frame(0, 0, false, true);
    }

    private static double easeIn(double p) { return p * p; }
    private static double easeOut(double p) { return 1 - (1 - p) * (1 - p); }
}
