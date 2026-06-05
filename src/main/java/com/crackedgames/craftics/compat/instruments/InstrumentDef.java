package com.crackedgames.craftics.compat.instruments;

import java.util.List;

/** Immutable combat definition for one instrument. See the design spec roster table. */
public record InstrumentDef(
    String modId,            // "genshinstrument" or "evenmoreinstruments"
    String itemPath,         // e.g. "glorious_drum"
    Role role,
    Shape shape,
    int apCost,              // 1..3
    List<Effect> effects,    // buffs (support) or debuffs (attack); one or more; [] for none
    int baseDamage,          // attack: per-tile SPECIAL base; support: 0
    Signature signature,     // NONE for non-signature instruments
    List<String> soundIds,   // mod sound paths, e.g. "glorious_drum_note_0"
    int noteColor            // RGB tint for ParticleTypes.NOTE
) {
    public enum Role { ATTACK, SUPPORT }

    public enum Shape { RING1, RING2, FILLED_DISC2, STAR, PLUS, DIAGONALS, EXPANDING_PULSE, CONE, SCATTER, FULL_ARENA }

    public enum Signature { NONE, KNOCKBACK, FLAT_HEAL, ARENA_HEAL, CLEANSE }

    /** A buff or debuff payload applied in-shape. {@code type == null} means no status (used for a pure flat-heal). */
    public record Effect(com.crackedgames.craftics.combat.CombatEffects.EffectType type, int turns, int amplifier, int flatHeal) {
        public static Effect of(com.crackedgames.craftics.combat.CombatEffects.EffectType t, int turns, int amp) {
            return new Effect(t, turns, amp, 0);
        }
    }
}
