package com.crackedgames.craftics.combat;

import java.util.EnumMap;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Turn-based effect tracking for combat. Effects tick down by turns, not real time.
 * Effects can be applied in the hub (frozen until combat starts) or during combat.
 */
public class CombatEffects {

    public enum EffectType {
        // Buffs
        SPEED("Speed", "+2 movement"),
        STRENGTH("Strength", "+3 attack"),
        RESISTANCE("Resistance", "+2 defense"),
        REGENERATION("Regeneration", "+2 HP/turn"),
        FIRE_RESISTANCE("Fire Resistance", "immune to fire"),
        INVISIBILITY("Invisibility", "enemies skip you"),
        ABSORPTION("Absorption", "extra HP"),
        LUCK("Luck", "+crit chance"),
        SLOW_FALLING("Slow Falling", "no knockback"),
        HASTE("Haste", "+1 AP"),
        WATER_BREATHING("Water Breathing", "+2 water damage"),
        // Debuffs
        POISON("Poison", "-1 HP/turn"),
        SLOWNESS("Slowness", "-1 movement"),
        WEAKNESS("Weakness", "-2 attack"),
        WITHER("Wither", "-2 HP/turn"),
        BURNING("Burning", "-1 HP/turn"),
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
        public int turnsRemaining; // -1 = frozen (hub-applied, waiting for combat)
        public int amplifier;     // 0 = level I, 1 = level II, etc.
        public int frozenDefaultTurns; // stored duration for when a frozen effect unfreezes

        public ActiveEffect(EffectType type, int turns, int amplifier) {
            this.type = type;
            this.turnsRemaining = turns;
            this.amplifier = amplifier;
            this.frozenDefaultTurns = turns;
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

    /**
     * Add or refresh an effect. If already active, refreshes duration.
     */
    public void addEffect(EffectType type, int turns, int amplifier) {
        int maxDur = com.crackedgames.craftics.CrafticsMod.CONFIG.maxCombatEffectDuration();
        effects.put(type, new ActiveEffect(type, Math.min(turns, maxDur), amplifier));
    }

    /**
     * Add a frozen effect (applied in hub, timer starts on combat entry).
     * The defaultTurns will be set when combat starts.
     */
    public void addFrozenEffect(EffectType type, int defaultTurns, int amplifier) {
        ActiveEffect effect = new ActiveEffect(type, -1, amplifier);
        effect.frozenDefaultTurns = defaultTurns;
        effects.put(type, effect);
    }

    /**
     * Unfreeze all frozen effects (called when combat starts).
     */
    public void unfreezeAll(int defaultTurns) {
        for (ActiveEffect effect : effects.values()) {
            if (effect.isFrozen()) {
                effect.turnsRemaining = defaultTurns;
            }
        }
    }

    /**
     * Tick all effects down by 1 turn. Remove expired effects.
     * Called at the end of each full turn cycle (after enemy turn).
     * Returns messages about expired effects.
     */
    /** List of effect types that expired on the last tickTurn call. */
    private final java.util.List<EffectType> lastExpired = new java.util.ArrayList<>();

    public java.util.List<EffectType> getLastExpired() { return lastExpired; }

    public String tickTurn() {
        lastExpired.clear();
        StringJoiner expired = new StringJoiner(", ");
        var iterator = effects.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            ActiveEffect effect = entry.getValue();
            if (effect.isFrozen()) continue; // don't tick frozen effects

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
     * Apply per-turn effects like regeneration and poison.
     * Called at the START of the player's turn.
     * Returns HP change (positive = heal, negative = damage).
     */
    public int applyPerTurnEffects() {
        int hpChange = 0;

        ActiveEffect regen = effects.get(EffectType.REGENERATION);
        if (regen != null && !regen.isFrozen()) {
            hpChange += 2 * (regen.amplifier + 1);
        }

        ActiveEffect poison = effects.get(EffectType.POISON);
        if (poison != null && !poison.isFrozen()) {
            hpChange -= com.crackedgames.craftics.CrafticsMod.CONFIG.poisonDamagePerTurn() * (poison.amplifier + 1);
        }

        ActiveEffect wither = effects.get(EffectType.WITHER);
        if (wither != null && !wither.isFrozen()) {
            hpChange -= 2 * (wither.amplifier + 1);
        }

        ActiveEffect burning = effects.get(EffectType.BURNING);
        if (burning != null && !burning.isFrozen() && !hasFireResistance()) {
            hpChange -= 1 * (burning.amplifier + 1);
        }

        return hpChange;
    }

    // Stat bonus getters
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

    /**
     * Get a display string of active effects for the HUD.
     */
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

    public Map<EffectType, ActiveEffect> getAll() {
        return effects;
    }
}
