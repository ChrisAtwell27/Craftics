package com.crackedgames.craftics.combat;

import java.util.EnumMap;
import java.util.Map;
import java.util.StringJoiner;

// Effects tick down by turns, not real time. Can be frozen (applied in hub, starts on combat entry)
public class CombatEffects {

    public enum EffectType {
        SPEED("Speed", "+2 movement/level"),
        STRENGTH("Strength", "+3 attack/level"),
        RESISTANCE("Resistance", "-2 damage taken/level"),
        // Resistance's mirror, and the player-side twin of CombatEntity's defensePenalty.
        // Mobs could always be made easier to hurt; players could not, so any effect that
        // wanted to strip a player's guard had nothing to apply and silently did nothing.
        VULNERABLE("Vulnerable", "+2 damage taken/level"),
        REGENERATION("Regeneration", "+2 HP/turn/level"),
        FIRE_RESISTANCE("Fire Resistance", "immune to fire; +1 Special damage"),
        INVISIBILITY("Invisibility", "enemies skip you"),
        ABSORPTION("Absorption", "extra HP"),
        LUCK("Luck", "+5% crit chance/level"),
        SLOW_FALLING("Slow Falling", "no knockback"),
        HASTE("Haste", "+1 AP/level"),
        WATER_BREATHING("Water Breathing", "+2 water damage"),
        POISON("Poison", "-(2/level + turns left) HP/turn, front-loaded"),
        SLOWNESS("Slowness", "-1 movement/level"),
        WEAKNESS("Weakness", "-2 attack/level"),
        WITHER("Wither", "-(1+level) HP/turn, ramping each turn"),
        BURNING("Burning", "-(1+level) HP/turn"),
        // Soul fire's own burn. Same shape as BURNING but it holds longer and bites harder,
        // and fire resistance only blunts it instead of turning it off - being fireproof is
        // what makes ordinary fire a non-event, and soul fire is meant to still be a threat
        // to someone who has solved fire.
        SOUL_BURNING("Soul Burning", "-(2+level) HP/turn; fire resistance only softens it"),
        BLEEDING("Bleeding", "Stacking HP loss/turn (1, 1, 3, 5, 7...)"),
        BLINDNESS("Blindness", "-2 range/level"),
        MINING_FATIGUE("Mining Fatigue", "-1 AP/level"),
        LEVITATION("Levitation", "-1 movement/level"),
        DARKNESS("Darkness", "enemies beyond 2 tiles are hidden"),
        SOAKED("Soaked", "-1 speed, 2x lightning"),
        CONFUSION("Confusion", "attack allies"),
        AIRTIME("Airtime", "+2 ranged range/level; +0.5x next weapon hit/level"),
        WARPED("Warped", "movement mirrored"),
        // The player-side twin of CombatEntity's Marked, and it means the same thing on
        // both sides of the grid: everything that hits you hits twice as hard.
        MARKED("Marked", "2x damage taken");

        public final String displayName;
        public final String description;

        EffectType(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }
    }

    public static class ActiveEffect {
        public final EffectType type;
        public int turnsRemaining; // -1 = frozen (waiting for combat start)
        public int amplifier;     // 0 = level I, 1 = level II, etc
        public int frozenDefaultTurns;
        /**
         * Highest turnsRemaining this effect has reached. Used by the wither
         * ramp so the per-turn multiplier is computed against the peak rather
         * than the current value. Updated when a stronger stack is applied.
         */
        public int peakTurns;

        public ActiveEffect(EffectType type, int turns, int amplifier) {
            this.type = type;
            this.turnsRemaining = turns;
            this.amplifier = amplifier;
            this.frozenDefaultTurns = turns;
            this.peakTurns = turns;
        }

        public boolean isFrozen() {
            return turnsRemaining == -1;
        }

        public void unfreeze(int fallbackTurns) {
            if (isFrozen()) {
                this.turnsRemaining = frozenDefaultTurns > 0 ? frozenDefaultTurns : fallbackTurns;
            }
        }
    }

    private final EnumMap<EffectType, ActiveEffect> effects = new EnumMap<>(EffectType.class);

    /** Fallback duration cap used when the config isn't loaded (unit tests, or an effect
     *  applied before mod init). Matches the config default. */
    private static final int DEFAULT_MAX_DURATION = 10;

    /** Damage soul burning adds on top of the ordinary burn tick. */
    public static final int SOUL_BURN_EXTRA = 1;
    /** All that fire resistance takes off a soul burn tick. It softens; it does not stop it. */
    public static final int SOUL_BURN_RESISTED_REDUCTION = 1;
    /** Turns a soul burn holds, against BURN_TURNS for the ordinary kind. */
    public static final int SOUL_BURN_TURNS = 5;

    /**
     * The configured cap on effect duration, or {@link #DEFAULT_MAX_DURATION} if the config
     * hasn't loaded. Reading {@code CONFIG} directly NPE'd whenever it was null, which made
     * this class impossible to unit-test and would have crashed combat outright if any effect
     * were ever applied before mod init.
     */
    private static int maxEffectDuration() {
        var config = com.crackedgames.craftics.CrafticsMod.CONFIG;
        return config != null ? config.maxCombatEffectDuration() : DEFAULT_MAX_DURATION;
    }

    /**
     * Add bleed stacks, the same way {@code CombatEntity.stackBleed} does.
     *
     * <p>Bleed is the one effect that is a COUNT rather than a duration: it punishes being hit
     * often, not being hit once for a long time. The stack count lives in {@code turnsRemaining}
     * on purpose - stacks decay one per turn and bleed ends when they run out, which is exactly
     * what the ordinary duration tick already does, so there is no second counter to keep in
     * sync with the first.
     *
     * <p>Before this, a player's bleed was a duration with an amplifier that REPLACED on every
     * application, so a mob hitting you five times left bleed at the same size it started while
     * the identical five hits on a mob built to five stacks. Same word, two mechanics.
     */
    public void stackBleed(int stacks) {
        if (stacks <= 0) return;
        ActiveEffect prev = effects.get(EffectType.BLEEDING);
        // The player ceiling, not the mob one. Stacks climb faster than they decay when you are
        // being hit every turn, and the mob curve turned loose on a player's health bar made
        // bleed harsher than the flat effect it replaced.
        int total = Math.min(EffectFormulas.MAX_PLAYER_BLEED_STACKS,
            (prev != null ? prev.turnsRemaining : 0) + stacks);
        ActiveEffect next = new ActiveEffect(EffectType.BLEEDING, total, Math.max(0, total - 1));
        next.peakTurns = total;
        effects.put(EffectType.BLEEDING, next);
    }

    /** Live bleed stacks, 0 when not bleeding. Mirrors {@code CombatEntity.getBleedStacks}. */
    public int getBleedStacks() {
        ActiveEffect bleeding = effects.get(EffectType.BLEEDING);
        return bleeding != null ? Math.max(0, bleeding.turnsRemaining) : 0;
    }

    public void addEffect(EffectType type, int turns, int amplifier) {
        // Bleed never takes the duration path - see stackBleed. Callers pass it as an amplifier
        // (amplifier + 1 = stacks, the same arithmetic the mob side uses), so it is translated
        // here rather than at every call site.
        if (type == EffectType.BLEEDING) {
            // amplifier + 1 = stacks, the same arithmetic every other effect uses for its
            // level and the same number CombatEntity.stackBleed takes. The duration argument is
            // ignored on purpose: a stack decays every turn, so the stack count already IS the
            // duration, and honouring both would let one bleed be counted twice.
            stackBleed(amplifier + 1);
            return;
        }
        // Same floor the mob side enforces (CombatEntity.MIN_DOT_TURNS): a one-turn DoT
        // flickers and vanishes, so nothing is allowed to apply one. Stuns and control
        // effects are untouched - they do their job on the turn they land.
        int requested = isDamageOverTime(type)
            ? Math.max(com.crackedgames.craftics.combat.CombatEntity.MIN_DOT_TURNS, turns)
            : turns;
        int finalTurns = Math.min(requested, maxEffectDuration());
        ActiveEffect prev = effects.get(type);
        ActiveEffect next = new ActiveEffect(type, finalTurns, amplifier);
        // When stacking the SAME effect, keep the highest peak the player has
        // seen so the wither ramp doesn't reset to 1x just because the user
        // re-applied at a longer duration.
        if (prev != null) {
            next.peakTurns = Math.max(prev.peakTurns, finalTurns);
        }
        // Water beats fire, in both directions. Mirrors CombatEntity.stackSoaked /
        // stackBurning so the rule holds whether the victim is a player or a mob.
        if (type == EffectType.BURNING && hasEffect(EffectType.SOAKED)) {
            // A drenched player can't catch light. Without this, the douse below would just
            // be undone by the next fire proc in the same turn.
            return;
        }

        effects.put(type, next);

        if (type == EffectType.SOAKED) {
            effects.remove(EffectType.BURNING);
        }
    }

    // Frozen = applied in hub, timer starts when combat begins
    public void addFrozenEffect(EffectType type, int defaultTurns, int amplifier) {
        ActiveEffect effect = new ActiveEffect(type, -1, amplifier);
        effect.frozenDefaultTurns = defaultTurns;
        effects.put(type, effect);
    }

    /**
     * Start the clock on every effect that was applied in the hub. {@code defaultTurns} is
     * only a fallback: each frozen effect already recorded the duration it was applied with,
     * and {@link ActiveEffect#unfreeze} honours it. Overwriting {@code turnsRemaining}
     * directly here discarded that, so a 3-turn buff and an 8-turn buff both entered combat
     * at exactly the caller's blanket value - and left {@code unfreeze} with no call sites.
     */
    public void unfreezeAll(int defaultTurns) {
        for (ActiveEffect effect : effects.values()) {
            effect.unfreeze(defaultTurns);
        }
    }

    // Ticks down all effects by 1 turn, returns comma-joined names of expired ones
    private final java.util.List<EffectType> lastExpired = new java.util.ArrayList<>();

    public java.util.List<EffectType> getLastExpired() { return lastExpired; }

    public String tickTurn() {
        lastExpired.clear();
        StringJoiner expired = new StringJoiner(", ");
        var iterator = effects.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            ActiveEffect effect = entry.getValue();
            if (effect.isFrozen()) continue;

            effect.turnsRemaining--;
            if (effect.turnsRemaining <= 0) {
                expired.add(effect.type.displayName);
                lastExpired.add(effect.type);
                iterator.remove();
            }
        }
        return expired.length() > 0 ? expired.toString() : null;
    }

    /**
     * Iron Will helmet: the mental effects (Confusion, Blindness, Darkness) tick out at
     * double speed. Called right after {@link #tickTurn} for wearers - drops one EXTRA turn
     * from each mental effect, expiring it exactly as tickTurn would (including joining
     * {@link #getLastExpired()}). Returns the expired names, or null.
     */
    public String tickMentalEffectsExtra() {
        StringJoiner expired = new StringJoiner(", ");
        var iterator = effects.entrySet().iterator();
        while (iterator.hasNext()) {
            ActiveEffect effect = iterator.next().getValue();
            if (effect.isFrozen()) continue;
            EffectType t = effect.type;
            if (t != EffectType.CONFUSION && t != EffectType.BLINDNESS
                && t != EffectType.DARKNESS) continue;
            effect.turnsRemaining--;
            if (effect.turnsRemaining <= 0) {
                expired.add(effect.type.displayName);
                lastExpired.add(effect.type);
                iterator.remove();
            }
        }
        return expired.length() > 0 ? expired.toString() : null;
    }

    /**
     * Net HP change from regen / poison / wither / burning / bleeding this turn.
     * Positive = heal, negative = damage.
     *
     * <p>{@code specialAffinity} is the victim's Special affinity points;
     * burning, poison, and wither all scale with it. Pass 0 for non-player
     * victims (mob-on-mob DoTs don't read player affinity).
     *
     * <p>Damage formulas:
     * <ul>
     *   <li>Burning = 1 + level + specialAffinity (per turn). Blocked by Fire Resistance.
     *   <li>Poison  = (2 × level) + turnsRemaining + specialAffinity. Higher
     *       early, fades as the effect ticks down.
     *   <li>Wither  = (1 + level + specialAffinity) × (peakTurns - turnsRemaining + 1).
     *       Opposite ramp - starts at 1× base and climbs to (peakTurns)× on
     *       the final tick. {@code peakTurns} updates when stacked higher.
     * </ul>
     */
    public int applyPerTurnEffects(int specialAffinity) {
        return applyPerTurnEffects(specialAffinity, 0);
    }

    /**
     * @param maxHp the victim's maximum health, so every damage-over-time tick carries a share
     *              of their own pool - see {@link EffectFormulas#maxHpDotBonus}. Pass 0 to omit
     *              that term, which is what the formula-level tests want and what the older
     *              single-argument overload does.
     */
    public int applyPerTurnEffects(int specialAffinity, int maxHp) {
        int hpChange = 0;
        // One share of the pool, computed once and charged by each DOT that is active - the same
        // term CombatEntity has always added to a mob's ticks. Without it a DOT means something
        // completely different depending on whose health bar it is sitting on.
        int pool = maxHp > 0 ? EffectFormulas.maxHpDotBonus(maxHp) : 0;
        // Every tick below is the mob's own number - same formula, same pool term - put through
        // EffectFormulas.forPlayer at the end. The shared rules stay shared; only the last step
        // knows whose health bar this is.

        ActiveEffect regen = effects.get(EffectType.REGENERATION);
        if (regen != null && !regen.isFrozen()) {
            hpChange += 2 * (regen.amplifier + 1);
        }

        ActiveEffect poison = effects.get(EffectType.POISON);
        if (poison != null && !poison.isFrozen()) {
            hpChange -= EffectFormulas.forPlayer(EffectFormulas.poisonTick(
                poison.amplifier + 1, poison.turnsRemaining, specialAffinity) + pool);
        }

        ActiveEffect wither = effects.get(EffectType.WITHER);
        if (wither != null && !wither.isFrozen()) {
            hpChange -= EffectFormulas.forPlayer(EffectFormulas.witherTick(
                wither.amplifier + 1, wither.peakTurns, wither.turnsRemaining, specialAffinity)
                + pool);
        }

        ActiveEffect burning = effects.get(EffectType.BURNING);
        if (burning != null && !burning.isFrozen() && !hasFireResistance()) {
            hpChange -= EffectFormulas.forPlayer(
                EffectFormulas.burningTick(burning.amplifier + 1, specialAffinity) + pool);
        }

        // Soul burning: hotter than ordinary fire, and fire resistance does not switch it
        // off. Being fireproof reduces it by SOUL_BURN_RESISTED_REDUCTION and no more, so
        // the gear and the potion that make ordinary fire a non-event still leave soul fire
        // worth running out of. Never reduced below 1 - "resisted" is not "immune".
        ActiveEffect soulBurning = effects.get(EffectType.SOUL_BURNING);
        if (soulBurning != null && !soulBurning.isFrozen()) {
            int soulTick = EffectFormulas.burningTick(soulBurning.amplifier + 1, specialAffinity)
                + SOUL_BURN_EXTRA;
            if (hasFireResistance()) {
                soulTick = Math.max(1, soulTick - SOUL_BURN_RESISTED_REDUCTION);
            }
            hpChange -= EffectFormulas.forPlayer(soulTick + pool);
        }

        // turnsRemaining IS the stack count here - see stackBleed.
        ActiveEffect bleeding = effects.get(EffectType.BLEEDING);
        if (bleeding != null && !bleeding.isFrozen()) {
            hpChange -= EffectFormulas.forPlayer(
                EffectFormulas.bleedTick(bleeding.turnsRemaining) + pool);
        }

        return hpChange;
    }

    /** Backwards-compatible overload - assumes 0 special affinity. */
    public int applyPerTurnEffects() {
        return applyPerTurnEffects(0);
    }

    /** Airtime stacks are capped at this amplifier (Airtime V) to bound the payoff. */
    public static final int AIRTIME_MAX_AMPLIFIER = 4;

    public boolean hasEffect(EffectType type) {
        ActiveEffect e = effects.get(type);
        return e != null && !e.isFrozen();
    }

    /** Turns left on an active effect, or 0 when it isn't running (or is still frozen).
     *  Lets callers that EXTEND a duration (fire tiles re-applying Burning) read what is
     *  already there instead of overwriting it - {@link #addEffect} replaces the timer. */
    public int getTurnsRemaining(EffectType type) {
        ActiveEffect e = effects.get(type);
        return e != null && !e.isFrozen() ? Math.max(0, e.turnsRemaining) : 0;
    }

    /** Amplifier of an active effect (0 = level I), or -1 when it isn't running. Lets a
     *  re-application take the MAX level instead of overwriting a stronger stack with a
     *  weaker one - stepping out of soul fire into ordinary fire must not cool a burn down. */
    public int getAmplifier(EffectType type) {
        ActiveEffect e = effects.get(type);
        return e != null && !e.isFrozen() ? e.amplifier : -1;
    }

    public int getSpeedBonus() {
        if (!hasEffect(EffectType.SPEED)) return 0;
        return 2 * (effects.get(EffectType.SPEED).amplifier + 1);
    }

    public int getSpeedPenalty() {
        if (!hasEffect(EffectType.SLOWNESS)) return 0;
        return 1 + effects.get(EffectType.SLOWNESS).amplifier;
    }

    public int getStrengthBonus() {
        if (!hasEffect(EffectType.STRENGTH)) return 0;
        return 3 * (effects.get(EffectType.STRENGTH).amplifier + 1);
    }

    public boolean hasAirtime() {
        return hasEffect(EffectType.AIRTIME);
    }

    /** Airtime level (amplifier + 1), or 0 when not airborne. */
    public int getAirtimeLevel() {
        return hasAirtime() ? effects.get(EffectType.AIRTIME).amplifier + 1 : 0;
    }

    /**
     * Spend one Airtime stack for a weapon hit. Returns the level that was in effect for this
     * hit (0 if not airborne), so the caller can scale damage by that level, then drops the
     * amplifier by one - removing the effect entirely when it was at level I.
     */
    public int consumeAirtimeStack() {
        ActiveEffect e = effects.get(EffectType.AIRTIME);
        if (e == null || e.isFrozen()) return 0;
        int level = e.amplifier + 1;
        if (e.amplifier <= 0) {
            effects.remove(EffectType.AIRTIME);
        } else {
            e.amplifier -= 1;
        }
        return level;
    }

    public int getWeaknessPenalty() {
        if (!hasEffect(EffectType.WEAKNESS)) return 0;
        return 2 * (effects.get(EffectType.WEAKNESS).amplifier + 1);
    }

    public int getResistanceBonus() {
        if (!hasEffect(EffectType.RESISTANCE)) return 0;
        return 2 * (effects.get(EffectType.RESISTANCE).amplifier + 1);
    }

    /** Extra damage taken per hit. The same 2-per-level as {@link #getResistanceBonus}, added
     *  instead of subtracted, so the pair is symmetrical the way the mob side already was. */
    public int getDefensePenalty() {
        if (!hasEffect(EffectType.VULNERABLE)) return 0;
        return 2 * (effects.get(EffectType.VULNERABLE).amplifier + 1);
    }

    public boolean hasFireResistance() {
        return hasEffect(EffectType.FIRE_RESISTANCE);
    }

    public boolean isInvisible() {
        return hasEffect(EffectType.INVISIBILITY);
    }

    public boolean hasAbsorption() {
        return hasEffect(EffectType.ABSORPTION);
    }

    public int getLuckBonus() {
        if (!hasEffect(EffectType.LUCK)) return 0;
        return 1 + effects.get(EffectType.LUCK).amplifier;
    }

    public boolean hasSlowFalling() {
        return hasEffect(EffectType.SLOW_FALLING);
    }

    public int getHasteBonus() {
        if (!hasEffect(EffectType.HASTE)) return 0;
        return 1 + effects.get(EffectType.HASTE).amplifier;
    }

    public int getBlindnessPenalty() {
        if (!hasEffect(EffectType.BLINDNESS)) return 0;
        return 2 * (effects.get(EffectType.BLINDNESS).amplifier + 1);
    }

    public int getMiningFatiguePenalty() {
        if (!hasEffect(EffectType.MINING_FATIGUE)) return 0;
        return 1 + effects.get(EffectType.MINING_FATIGUE).amplifier;
    }

    public int getLevitationPenalty() {
        if (!hasEffect(EffectType.LEVITATION)) return 0;
        return 1 + effects.get(EffectType.LEVITATION).amplifier;
    }

    /**
     * Darkness no longer shaves attack range - it is now a client-side fog of
     * war (enemies beyond 2 tiles are hidden from the affected player, handled
     * in CombatState/render mixins). Kept returning 0 so any legacy caller
     * summing vision penalties still compiles and behaves.
     */
    public int getDarknessPenalty() {
        return 0;
    }

    /** Total range lost to vision debuffs. Blindness only - Darkness is now fog of war. */
    public int getRangePenalty() {
        int blindnessLevel = hasEffect(EffectType.BLINDNESS)
            ? effects.get(EffectType.BLINDNESS).amplifier + 1 : 0;
        return EffectFormulas.rangePenalty(blindnessLevel, 0);
    }

    public String getDisplayString() {
        if (effects.isEmpty()) return "";

        StringJoiner sj = new StringJoiner(" | ");
        for (ActiveEffect e : effects.values()) {
            String turns = e.isFrozen() ? "frozen" : e.turnsRemaining + "t";
            String level = e.amplifier > 0 ? " " + romanLevel(e.amplifier + 1) : "";
            sj.add(e.type.displayName + level + " (" + turns + ")");
        }
        return sj.toString();
    }

    private static String romanLevel(int n) {
        return switch (n) { case 2 -> "II"; case 3 -> "III"; case 4 -> "IV"; case 5 -> "V"; default -> String.valueOf(n); };
    }

    public boolean isEmpty() {
        return effects.isEmpty();
    }

    public void clear() {
        effects.clear();
    }

    /** Remove one specific effect, buff or debuff. Returns true if it was present. */
    public boolean removeEffect(EffectType type) {
        return type != null && effects.remove(type) != null;
    }

    /** Remove all negative (debuff) effects, leaving positive buffs intact. */
    public void clearDebuffs() {
        effects.keySet().removeIf(CombatEffects::isDebuff);
    }

    /**
     * Remove a single debuff (the first one in iteration order) and return its type, leaving all
     * buffs and any remaining debuffs intact. Returns null when the wielder carries no debuffs.
     * Powers the Reversal enchant's per-hit cleanse.
     */
    public EffectType removeFirstDebuff() {
        for (EffectType type : effects.keySet()) {
            if (isDebuff(type)) {
                effects.remove(type);
                return type;
            }
        }
        return null;
    }

    /** True for the effects that deal damage on a per-turn tick, and so need a duration
     *  long enough to actually tick. See the floor applied in {@link #addEffect}. */
    public static boolean isDamageOverTime(EffectType type) {
        return switch (type) {
            case POISON, WITHER, BURNING, SOUL_BURNING, BLEEDING -> true;
            default -> false;
        };
    }

    /** True if {@code type} is a harmful effect (removed by a cleanse). */
    public static boolean isDebuff(EffectType type) {
        return switch (type) {
            case POISON, SLOWNESS, WEAKNESS, WITHER, BURNING, SOUL_BURNING, BLEEDING,
                 BLINDNESS, MINING_FATIGUE, LEVITATION, DARKNESS, SOAKED, CONFUSION, WARPED,
                 MARKED, VULNERABLE -> true;
            default -> false;
        };
    }

    /**
     * True if {@code type} is a beneficial effect.
     *
     * <p>Deliberately a whitelist rather than {@code !isDebuff(type)}. Not every effect is one or
     * the other - AIRTIME is a positional state, not something a player is buffed by - and with a
     * negation the next neutral effect added to the enum would silently start counting as a buff
     * and hand out the Alchemist feat for standing in the air.
     */
    public static boolean isBuff(EffectType type) {
        return switch (type) {
            case SPEED, STRENGTH, RESISTANCE, REGENERATION, FIRE_RESISTANCE, INVISIBILITY,
                 ABSORPTION, LUCK, SLOW_FALLING, HASTE, WATER_BREATHING -> true;
            default -> false;
        };
    }

    /** How many distinct buffs are active right now. The Alchemist feat counts this. */
    public int countActiveBuffs() {
        int n = 0;
        for (EffectType type : effects.keySet()) {
            if (isBuff(type)) n++;
        }
        return n;
    }

    public Map<EffectType, ActiveEffect> getAll() {
        return effects;
    }
}
