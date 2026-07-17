package com.crackedgames.craftics.combat;

import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;

/**
 * Behavior for the axe (Cleaving affinity) enchantments.
 *
 * <p>Unlike the shovel and hoe, which are carried focuses whose enchantments fire through the
 * owner's pets and Special casts, the axe is the weapon doing the hitting. Its enchantments read
 * the HELD stack via {@link CrafticsEnchantments#heldLevel}, so they only ever apply to the swing
 * of an actual axe.
 *
 * @since 0.2.92
 */
public final class AxeEnchantEffects {

    private AxeEnchantEffects() {}

    /**
     * Damage multiplier Facade applies while its bearer is suffering a debuff.
     *
     * <p>Flat rather than per-level: Facade caps at level 1, so there is no curve to scale.
     */
    public static final double FACADE_MULT = 1.5;

    // ── Facade: fight hurt, hit harder ──────────────────────────────────────

    /**
     * Apply {@link #FACADE_MULT} to {@code baseDamage}.
     *
     * <p>Pure integer math, split out from the player/effect lookup so it can be unit-tested
     * without a Minecraft bootstrap. Rounds via {@link Math#round}, matching the Airtime and
     * spear-momentum multipliers in {@code CombatManager} rather than truncating like the crit
     * and kill-streak ones: at 1.5x an odd base would otherwise lose half a point of damage
     * (a 5-damage hit paying 7 instead of 8).
     *
     * @param baseDamage damage before the Facade bonus
     * @param active     whether Facade should fire at all
     * @return the multiplied damage, or {@code baseDamage} untouched when {@code active} is false
     */
    public static int facadeDamage(int baseDamage, boolean active) {
        if (!active || baseDamage <= 0) return baseDamage;
        return (int) Math.round(baseDamage * FACADE_MULT);
    }

    /**
     * Whether Facade is live: the player is holding an axe carrying the enchantment AND is
     * currently under at least one debuff.
     *
     * <p>"Debuff" is whatever {@link CombatEffects#isDebuff} says it is, so an effect added to
     * that list later arms Facade automatically and no list here can drift out of sync.
     */
    public static boolean facadeActive(ServerPlayerEntity player, CombatEffects effects) {
        if (player == null || effects == null) return false;
        if (CrafticsEnchantments.heldLevel(player, CrafticsEnchantments.FACADE) <= 0) return false;
        return hasAnyDebuff(effects);
    }

    /** True when {@code effects} holds at least one effect {@link CombatEffects#isDebuff} calls harmful. */
    public static boolean hasAnyDebuff(CombatEffects effects) {
        if (effects == null) return false;
        for (CombatEffects.EffectType type : effects.getAll().keySet()) {
            if (CombatEffects.isDebuff(type)) return true;
        }
        return false;
    }

    // ── VFX ─────────────────────────────────────────────────────────────────

    /** Facade: the mask holds. Ashen motes and a muted, low crit thud. */
    public static void facadeVfx(ServerPlayerEntity player, ServerWorld world) {
        if (player == null || world == null) return;
        world.spawnParticles(ParticleTypes.SMOKE,
            player.getX(), player.getY() + 1.3, player.getZ(), 14, 0.4, 0.5, 0.4, 0.02);
        world.spawnParticles(ParticleTypes.CRIT,
            player.getX(), player.getY() + 1.1, player.getZ(), 10, 0.5, 0.4, 0.5, 0.15);
        world.playSound(null, player.getBlockPos(),
            SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, SoundCategory.PLAYERS, 0.6f, 0.7f);
    }
}
