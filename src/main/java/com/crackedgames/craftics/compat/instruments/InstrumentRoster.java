package com.crackedgames.craftics.compat.instruments;

import com.crackedgames.craftics.combat.CombatEffects.EffectType;
import com.crackedgames.craftics.compat.instruments.InstrumentDef.Effect;
import com.crackedgames.craftics.compat.instruments.InstrumentDef.Role;
import com.crackedgames.craftics.compat.instruments.InstrumentDef.Shape;
import com.crackedgames.craftics.compat.instruments.InstrumentDef.Signature;

import java.util.List;

/** The 15 instruments. Source of truth mirrors docs/superpowers/specs/2026-06-05-instruments-compat-design.md. */
public final class InstrumentRoster {

    private InstrumentRoster() {}

    private static final String G = "genshinstrument";
    private static final String E = "evenmoreinstruments";

    public static final List<InstrumentDef> ALL = List.of(
        // --- Attack (7) --- (effects = debuffs applied to enemies in-shape)
        new InstrumentDef(G, "glorious_drum", Role.ATTACK, Shape.FILLED_DISC2, 3,
            List.of(Effect.of(EffectType.SLOWNESS, 2, 0)), 6, Signature.KNOCKBACK,
            List.of("glorious_drum_note_0", "glorious_drum_note_3", "glorious_drum_note_5", "glorious_drum_note_1"), 0xFF5533),
        new InstrumentDef(G, "djem_djem_drum", Role.ATTACK, Shape.RING1, 2,
            List.of(Effect.of(EffectType.WEAKNESS, 2, 0)), 4, Signature.NONE,
            List.of("djem_djem_drum_note_0", "djem_djem_drum_note_1", "djem_djem_drum_note_2", "djem_djem_drum_note_3"), 0xCC7A2A),
        new InstrumentDef(G, "nightwind_horn", Role.ATTACK, Shape.CONE, 2,
            List.of(Effect.of(EffectType.SLOWNESS, 2, 0)), 6, Signature.NONE,
            List.of("nightwind_horn_attack_0", "nightwind_horn_attack_1", "nightwind_horn_attack_2"), 0x6A78FF),
        new InstrumentDef(E, "trombone", Role.ATTACK, Shape.CONE, 2,
            List.of(Effect.of(EffectType.WEAKNESS, 2, 0)), 7, Signature.NONE,
            List.of("trombone_note_1", "trombone_note_2"), 0xE0B030),
        new InstrumentDef(E, "saxophone", Role.ATTACK, Shape.CONE, 2,
            List.of(Effect.of(EffectType.CONFUSION, 2, 0)), 5, Signature.NONE,
            List.of("saxophone_note_1", "saxophone_note_5", "saxophone_note_3"), 0xFFC34D),
        new InstrumentDef(E, "pipa", Role.ATTACK, Shape.STAR, 2,
            List.of(Effect.of(EffectType.BLEEDING, 3, 0)), 6, Signature.NONE,
            List.of("pipa_tremolo_note_1", "pipa_tremolo_note_3", "pipa_note_5"), 0xCC2244),
        new InstrumentDef(E, "shamisen", Role.ATTACK, Shape.DIAGONALS, 2,
            List.of(Effect.of(EffectType.BLEEDING, 2, 1)), 6, Signature.NONE,
            List.of("shamisen_note_1", "shamisen_note_3", "shamisen_note_5"), 0xC8323C),

        // --- Support (8) --- (effects = buffs applied to friendlies in-shape; some have two)
        new InstrumentDef(G, "windsong_lyre", Role.SUPPORT, Shape.RING2, 2,
            List.of(Effect.of(EffectType.SLOW_FALLING, 3, 0)), 0, Signature.NONE,
            List.of("windsong_lyre_note_1", "windsong_lyre_note_2", "windsong_lyre_note_3"), 0x9CD8FF),
        new InstrumentDef(G, "vintage_lyre", Role.SUPPORT, Shape.PLUS, 2,
            List.of(Effect.of(EffectType.REGENERATION, 3, 0)), 0, Signature.FLAT_HEAL,
            List.of("vintage_lyre_note_1", "vintage_lyre_note_3", "vintage_lyre_note_5"), 0xF0C868),
        new InstrumentDef(G, "floral_zither", Role.SUPPORT, Shape.SCATTER, 2,
            List.of(Effect.of(EffectType.REGENERATION, 3, 0)), 0, Signature.NONE,
            List.of("floral_zither_new_note_5", "floral_zither_new_note_3", "floral_zither_new_note_1"), 0xF06090),
        new InstrumentDef(G, "ukulele", Role.SUPPORT, Shape.RING1, 1,
            List.of(Effect.of(EffectType.SPEED, 2, 0)), 0, Signature.NONE,
            List.of("ukulele_note_2", "ukulele_note_4", "ukulele_note_6"), 0xFFE24D),
        new InstrumentDef(E, "guitar", Role.SUPPORT, Shape.EXPANDING_PULSE, 2,
            List.of(Effect.of(EffectType.REGENERATION, 3, 0), Effect.of(EffectType.RESISTANCE, 2, 0)), 0, Signature.NONE,
            List.of("guitar_note_0", "guitar_note_2", "guitar_note_4", "guitar_note_5"), 0xE0B060),
        new InstrumentDef(E, "violin", Role.SUPPORT, Shape.FULL_ARENA, 3,
            List.of(Effect.of(EffectType.SPEED, 3, 0)), 0, Signature.ARENA_HEAL,
            List.of("violin_note_1", "violin_note_3", "violin_note_5", "violin_note_7"), 0xD8E0FF),
        new InstrumentDef(E, "keyboard", Role.SUPPORT, Shape.FILLED_DISC2, 3,
            List.of(Effect.of(EffectType.REGENERATION, 3, 1), Effect.of(EffectType.ABSORPTION, 3, 1)), 0, Signature.CLEANSE,
            List.of("keyboard_note_1", "keyboard_note_2", "keyboard_note_3"), 0xC8A0FF),
        new InstrumentDef(E, "koto", Role.SUPPORT, Shape.EXPANDING_PULSE, 2,
            List.of(Effect.of(EffectType.REGENERATION, 4, 0), Effect.of(EffectType.SLOW_FALLING, 3, 0)), 0, Signature.NONE,
            List.of("koto_note_0", "koto_note_4", "koto_note_8", "koto_note_12"), 0x7AE0D0)
    );
}
