package com.crackedgames.craftics.compat.paladins;

import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleType;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

/**
 * Resolves the Paladins/spell_engine "magic" particles by id at runtime so spell VFX are
 * authentic when the mods are present, with a vanilla fallback so nothing crashes when a
 * particle id is missing. spell_engine is a hard dependency of Paladins, so these resolve
 * whenever Paladins is loaded.
 */
public final class PaladinsParticles {

    private PaladinsParticles() {}

    /** Resolve spell_engine:<name> to a ParticleEffect, or return the vanilla fallback. */
    static ParticleEffect resolve(String seName, ParticleEffect fallback) {
        Identifier id = Identifier.of("spell_engine", seName);
        if (!Registries.PARTICLE_TYPE.containsId(id)) return fallback;
        ParticleType<?> type = Registries.PARTICLE_TYPE.get(id);
        if (type instanceof ParticleEffect effect) {
            return effect;
        }
        return fallback;
    }

    // ── Heal theme (green) ──
    /** Heal ascend, rising pillar of green motes. Fallback: HEART. */
    static ParticleEffect heal() { return resolve("magic_heal_ascend", ParticleTypes.HEART); }
    /** Heal burst, a green flash on impact. Fallback: HAPPY_VILLAGER. */
    static ParticleEffect healBurst() { return resolve("magic_heal_burst", ParticleTypes.HAPPY_VILLAGER); }
    /** Heal float, drifting green motes. Fallback: COMPOSTER. */
    static ParticleEffect healFloat() { return resolve("magic_heal_float", ParticleTypes.COMPOSTER); }

    // ── Holy theme (golden white) ──
    /** Holy burst, radiant sphere on impact. Fallback: END_ROD. */
    static ParticleEffect holyBurst() { return resolve("magic_holy_burst", ParticleTypes.END_ROD); }
    /** Holy decelerate, a settling golden aura. Fallback: END_ROD. */
    static ParticleEffect holyDecelerate() { return resolve("magic_holy_decelerate", ParticleTypes.END_ROD); }
    /** Holy ascend, a rising column of light. Fallback: END_ROD. */
    static ParticleEffect holyAscend() { return resolve("magic_holy_ascend", ParticleTypes.END_ROD); }
    /** Holy float, drifting golden motes. Fallback: GLOW. */
    static ParticleEffect holyFloat() { return resolve("magic_holy_float", ParticleTypes.GLOW); }

    // ── Spark theme (cast charge) ──
    /** Spark float, the charge-up shimmer at the caster. Fallback: ENCHANTED_HIT. */
    static ParticleEffect spark() { return resolve("magic_spark_float", ParticleTypes.ENCHANTED_HIT); }
    /** Spark burst, a snap of energy on release. Fallback: ELECTRIC_SPARK. */
    static ParticleEffect sparkBurst() { return resolve("magic_spark_burst", ParticleTypes.ELECTRIC_SPARK); }

    // ── Spell theme (beam/projectile) ──
    /** Spell float, the beam body. Fallback: ENCHANT. */
    static ParticleEffect spellFloat() { return resolve("magic_spell_float", ParticleTypes.ENCHANT); }
    /** Spell burst, the beam impact. Fallback: FLASH. */
    static ParticleEffect spellBurst() { return resolve("magic_spell_burst", ParticleTypes.FLASH); }
    /** Spell decelerate, a settling ward. Fallback: WITCH. */
    static ParticleEffect spellDecelerate() { return resolve("magic_spell_decelerate", ParticleTypes.WITCH); }

    /** Spark decelerate, a settling shimmer (banner/aura). Fallback: WAX_ON. */
    static ParticleEffect sparkDecelerate() { return resolve("magic_spark_decelerate", ParticleTypes.WAX_ON); }
}
