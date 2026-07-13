package com.crackedgames.craftics.combat;

import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;

/**
 * Behavior for the hoe (Special affinity) enchantments. Like the shovel, the hoe is never
 * swung - it is a focus the owner carries, and every effect here rides on the owner's
 * SPECIAL-ITEM casts (potions, banners, horns, charges, pearls, pottery sherds).
 *
 * <p>Levels come from anywhere in the owner's inventory via {@link CrafticsEnchantments}, taking
 * the highest of any hoe carried, so nothing has to be held and duplicates don't stack.
 *
 * <p>Each of these deliberately ADDS to an existing Special-affinity curve rather than replacing
 * it: a hoe makes a Special build better at what it already does, instead of being a parallel
 * system. See {@link SpecialAffinity} for the affinity's own side of each formula.
 *
 * @since 0.2.92
 */
public final class HoeEnchantEffects {

    private HoeEnchantEffects() {}

    /** Free-AP chance each level of Reserving adds, on top of Special affinity's own 3%/point. */
    public static final double RESERVING_CHANCE_PER_LEVEL = 0.05;
    /** Double-cast chance each level of Performative adds. */
    public static final double PERFORMATIVE_CHANCE_PER_LEVEL = 0.05;
    /** Bonus damage each level of Radiant adds to a Special item used against the undead. */
    public static final int RADIANT_DAMAGE_PER_LEVEL = 2;
    /** Bonus HP each level of Medic adds to a Special item's healing. */
    public static final int MEDIC_HEAL_PER_LEVEL = 2;

    // ── Reserving: chance a Special item costs no AP ────────────────────────

    /**
     * The chance a Special-item cast costs no AP: Special affinity's own {@code 3%/point} and
     * {@code 2%/Luck}, plus {@value #RESERVING_CHANCE_PER_LEVEL} per level of Reserving.
     *
     * <p>The affinity terms mirror the Magic Surge proc on Special WEAPON attacks, so a caster
     * gets the same feel whether they are swinging a Special weapon or throwing a Special item.
     *
     * @return a probability in {@code [0, 1]}
     */
    public static double freeApChance(ServerPlayerEntity player) {
        if (player == null) return 0.0;
        var stats = PlayerProgression.get((ServerWorld) player.getEntityWorld()).getStats(player);
        int specialPts = stats.getAffinityPoints(PlayerProgression.Affinity.SPECIAL);
        int luck = stats.getPoints(PlayerProgression.Stat.LUCK);
        int reserving = CrafticsEnchantments.level(player, CrafticsEnchantments.RESERVING);
        double chance = specialPts * 0.03 + luck * 0.02 + reserving * RESERVING_CHANCE_PER_LEVEL;
        return Math.max(0.0, Math.min(1.0, chance));
    }

    /**
     * Roll {@link #freeApChance}. On a hit, the caller must SKIP its AP deduction.
     *
     * @return true when this cast should cost nothing
     */
    public static boolean rollFreeAp(ServerPlayerEntity player) {
        double chance = freeApChance(player);
        return chance > 0 && Math.random() < chance;
    }

    // ── Performative: chance to cast a Special item twice ────────────────────

    /** The chance a Special-item cast fires a second time: {@value #PERFORMATIVE_CHANCE_PER_LEVEL} per level. */
    public static double doubleCastChance(ServerPlayerEntity player) {
        if (player == null) return 0.0;
        int level = CrafticsEnchantments.level(player, CrafticsEnchantments.PERFORMATIVE);
        return Math.min(1.0, level * PERFORMATIVE_CHANCE_PER_LEVEL);
    }

    /**
     * Roll {@link #doubleCastChance}. On a hit, the caller should run the same cast a second
     * time WITHOUT charging AP or consuming another item - the encore is free.
     */
    public static boolean rollDoubleCast(ServerPlayerEntity player) {
        double chance = doubleCastChance(player);
        return chance > 0 && Math.random() < chance;
    }

    // ── Radiant: Special items hit the undead harder ─────────────────────────

    /**
     * Bonus damage a Special item deals to {@code victim}, which is nothing at all unless the
     * victim is undead. {@value #RADIANT_DAMAGE_PER_LEVEL} per level, so a maxed Radiant V hoe
     * adds +10 against a zombie and +0 against a spider.
     */
    public static int radiantBonus(ServerPlayerEntity player, CombatEntity victim) {
        if (player == null || victim == null) return 0;
        if (!CombatManager.isUndeadMob(victim.getEntityTypeId())) return 0;
        return CrafticsEnchantments.level(player, CrafticsEnchantments.RADIANT) * RADIANT_DAMAGE_PER_LEVEL;
    }

    // ── Medic: Special items heal for more ───────────────────────────────────

    /**
     * Bonus HP added to healing a Special item does, whether the target is the caster, a
     * teammate, or a pet. {@value #MEDIC_HEAL_PER_LEVEL} per level.
     *
     * <p>Applied as a flat add rather than a multiplier so it is worth the same on a weak heal
     * as a strong one, which keeps a Medic hoe useful from the first potion to the last.
     */
    public static int medicBonus(ServerPlayerEntity player) {
        if (player == null) return 0;
        return CrafticsEnchantments.level(player, CrafticsEnchantments.MEDIC) * MEDIC_HEAL_PER_LEVEL;
    }

    // ── VFX ─────────────────────────────────────────────────────────────────

    /** Reserving: the cast costs nothing. Amethyst chime and a ring of enchant runes. */
    public static void reservingVfx(ServerPlayerEntity player, ServerWorld world) {
        if (player == null || world == null) return;
        world.spawnParticles(ParticleTypes.ENCHANT,
            player.getX(), player.getY() + 1.4, player.getZ(), 22, 0.5, 0.5, 0.5, 0.6);
        world.playSound(null, player.getBlockPos(),
            SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 0.8f, 1.5f);
    }

    /** Performative: the cast echoes. A second, brighter flourish and an evoker's cast sting. */
    public static void performativeVfx(ServerPlayerEntity player, ServerWorld world) {
        if (player == null || world == null) return;
        world.spawnParticles(ParticleTypes.WAX_OFF,
            player.getX(), player.getY() + 1.5, player.getZ(), 18, 0.6, 0.4, 0.6, 0.15);
        world.spawnParticles(ParticleTypes.END_ROD,
            player.getX(), player.getY() + 1.2, player.getZ(), 10, 0.4, 0.5, 0.4, 0.05);
        world.playSound(null, player.getBlockPos(),
            SoundEvents.ENTITY_EVOKER_CAST_SPELL, SoundCategory.PLAYERS, 0.7f, 1.6f);
    }

    /** Radiant: holy light burns the undead. A pillar of glow motes on the victim. */
    public static void radiantVfx(ServerWorld world, net.minecraft.util.math.BlockPos victimBlock) {
        if (world == null || victimBlock == null) return;
        world.spawnParticles(ParticleTypes.END_ROD,
            victimBlock.getX() + 0.5, victimBlock.getY() + 1.2, victimBlock.getZ() + 0.5,
            20, 0.25, 0.8, 0.25, 0.04);
        world.spawnParticles(ParticleTypes.GLOW,
            victimBlock.getX() + 0.5, victimBlock.getY() + 1.0, victimBlock.getZ() + 0.5,
            12, 0.3, 0.5, 0.3, 0.02);
        world.playSound(null, victimBlock,
            SoundEvents.ITEM_TOTEM_USE, SoundCategory.PLAYERS, 0.35f, 1.9f);
    }

    /** Medic: a warmer, greener heal burst than the plain heart particles. */
    public static void medicVfx(ServerWorld world, double x, double y, double z) {
        if (world == null) return;
        world.spawnParticles(ParticleTypes.HEART, x, y + 1.5, z, 8, 0.4, 0.3, 0.4, 0.05);
        world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, x, y + 1.2, z, 14, 0.45, 0.5, 0.45, 0.06);
    }
}
