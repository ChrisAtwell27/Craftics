package com.crackedgames.craftics.combat;

import java.util.EnumMap;
import java.util.Map;
import java.util.StringJoiner;

// Effects tick down by turns, not real time. Can be frozen (applied in hub, starts on combat entry)
public class CombatEffects {

    public enum EffectType {
        SPEED("Speed", "+2 movement"),
        STRENGTH("Strength", "+3 attack"),
        RESISTANCE("Resistance", "-2 damage taken/level"),
        REGENERATION("Regeneration", "+2 HP/turn"),
        FIRE_RESISTANCE("Fire Resistance", "immune to fire"),
        INVISIBILITY("Invisibility", "enemies skip you"),
        ABSORPTION("Absorption", "extra HP"),
        LUCK("Luck", "+crit chance"),
        SLOW_FALLING("Slow Falling", "no knockback"),
        HASTE("Haste", "+1 AP"),
        WATER_BREATHING("Water Breathing", "+2 water damage"),
        POISON("Poison", "-1 HP/turn"),
        SLOWNESS("Slowness", "-1 movement"),
        WEAKNESS("Weakness", "-2 attack"),
        WITHER("Wither", "-2 HP/turn"),
        BURNING("Burning", "-1 HP/turn"),
        BLEEDING("Bleeding", "Stacking HP loss/turn (1, 3, 6, 10...)"),
        BLINDNESS("Blindness", "-2 range"),
        MINING_FATIGUE("Mining Fatigue", "-1 AP"),
        LEVITATION("Levitation", "-1 movement"),
        DARKNESS("Darkness", "-1 range"),
        SOAKED("Soaked", "-1 speed, 2x lightning"),
        CONFUSION("Confusion", "attack allies");

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

    public void addEffect(EffectType type, int turns, int amplifier) {
        int maxDur = com.crackedgames.craftics.CrafticsMod.CONFIG.maxCombatEffectDuration();
        int finalTurns = Math.min(turns, maxDur);
        ActiveEffect prev = effects.get(type);
        ActiveEffect next = new ActiveEffect(type, finalTurns, amplifier);
        // When stacking the SAME effect, keep the highest peak the player has
        // seen so the wither ramp doesn't reset to 1x just because the user
        // re-applied at a longer duration.
        if (prev != null) {
            next.peakTurns = Math.max(prev.peakTurns, finalTurns);
        }
        effects.put(type, next);
    }

    // Frozen = applied in hub, timer starts when combat begins
    public void addFrozenEffect(EffectType type, int defaultTurns, int amplifier) {
        ActiveEffect effect = new ActiveEffect(type, -1, amplifier);
        effect.frozenDefaultTurns = defaultTurns;
        effects.put(type, effect);
    }

    public void unfreezeAll(int defaultTurns) {
        for (ActiveEffect effect : effects.values()) {
            if (effect.isFrozen()) {
                effect.turnsRemaining = defaultTurns;
            }
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
     *       Opposite ramp — starts at 1× base and climbs to (peakTurns)× on
     *       the final tick. {@code peakTurns} updates when stacked higher.
     * </ul>
     */
    public int applyPerTurnEffects(int specialAffinity) {
        int hpChange = 0;

        ActiveEffect regen = effects.get(EffectType.REGENERATION);
        if (regen != null && !regen.isFrozen()) {
            hpChange += 2 * (regen.amplifier + 1);
        }

        ActiveEffect poison = effects.get(EffectType.POISON);
        if (poison != null && !poison.isFrozen()) {
            int level = poison.amplifier + 1;
            int dmg = (2 * level) + poison.turnsRemaining + specialAffinity;
            hpChange -= Math.max(1, dmg);
        }

        ActiveEffect wither = effects.get(EffectType.WITHER);
        if (wither != null && !wither.isFrozen()) {
            int level = wither.amplifier + 1;
            int base = 1 + level + specialAffinity;
            int peak = Math.max(1, wither.peakTurns);
            int consumed = Math.max(1, peak - wither.turnsRemaining + 1);
            hpChange -= Math.max(1, base * consumed);
        }

        ActiveEffect burning = effects.get(EffectType.BURNING);
        if (burning != null && !burning.isFrozen() && !hasFireResistance()) {
            int level = burning.amplifier + 1;
            hpChange -= Math.max(1, 1 + level + specialAffinity);
        }

        ActiveEffect bleeding = effects.get(EffectType.BLEEDING);
        if (bleeding != null && !bleeding.isFrozen()) {
            // Bleed damage scales triangularly with stacks (1, 3, 6, 10, ...).
            int stacks = bleeding.amplifier + 1;
            hpChange -= stacks * (stacks + 1) / 2;
        }

        return hpChange;
    }

    /** Backwards-compatible overload — assumes 0 special affinity. */
    public int applyPerTurnEffects() {
        return applyPerTurnEffects(0);
    }

    public boolean hasEffect(EffectType type) {
        ActiveEffect e = effects.get(type);
        return e != null && !e.isFrozen();
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

    public int getWeaknessPenalty() {
        if (!hasEffect(EffectType.WEAKNESS)) return 0;
        return 2 * (effects.get(EffectType.WEAKNESS).amplifier + 1);
    }

    public int getResistanceBonus() {
        if (!hasEffect(EffectType.RESISTANCE)) return 0;
        return 2 * (effects.get(EffectType.RESISTANCE).amplifier + 1);
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

    public int getDarknessPenalty() {
        if (!hasEffect(EffectType.DARKNESS)) return 0;
        return 1 + effects.get(EffectType.DARKNESS).amplifier;
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

    /** Remove all negative (debuff) effects, leaving positive buffs intact. */
    public void clearDebuffs() {
        effects.keySet().removeIf(CombatEffects::isDebuff);
    }

    /** True if {@code type} is a harmful effect (removed by a cleanse). */
    public static boolean isDebuff(EffectType type) {
        return switch (type) {
            case POISON, SLOWNESS, WEAKNESS, WITHER, BURNING, BLEEDING,
                 BLINDNESS, MINING_FATIGUE, LEVITATION, DARKNESS, SOAKED, CONFUSION -> true;
            default -> false;
        };
    }

    public Map<EffectType, ActiveEffect> getAll() {
        return effects;
    }
}
