package com.crackedgames.craftics.vfx.weapon;

import com.crackedgames.craftics.vfx.VfxAnchor;
import com.crackedgames.craftics.vfx.VfxDescriptor;
import com.crackedgames.craftics.vfx.VfxPrimitive;
import net.minecraft.block.Blocks;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;

/**
 * Catalog of VfxDescriptors per weapon + hit outcome. Keyed by WeaponVfxSelector.
 *
 * <p>Directional language: every impact sprays its particles along the actual
 * hit direction (origin→target, resolved at fire time) via DirectionalBurst,
 * so a blow from the west visibly carries through to the east. Ground slams
 * ripple outward as a multi-tick Shockwave - per-ring particles, a pale tile
 * flash under the wave front, falling-pitch thumps, and a visual knock-up of
 * the mobs each ring passes beneath.
 */
public final class WeaponVfx {
    private WeaponVfx() {}

    private static final Vec3d TIGHT = new Vec3d(0.15, 0.15, 0.15);
    private static final Vec3d WIDE  = new Vec3d(0.4, 0.4, 0.4);
    private static final Vec3d ZERO  = Vec3d.ZERO;

    /** Pale dust color for the shockwave front tile flash. The amber "damage"
     *  flash is sent separately by CombatManager on resolve, so the wave uses a
     *  neutral tone that layers on top without reading as extra damage tiles. */
    private static final int WAVE_DUST = 0xD8CDB8;
    /** Soul-fire accent for the netherite execute ground scorch. */
    private static final int EXECUTE_SCORCH = 0xFF3020;

    // --- Fist ---
    public static final VfxDescriptor FIST = VfxDescriptor.builder()
        .phase(0)
            .sound(VfxAnchor.TARGET, SoundEvents.ENTITY_PLAYER_ATTACK_NODAMAGE, 1.0f, 1.0f)
            .particles(ParticleTypes.CLOUD, VfxAnchor.TARGET, 3, TIGHT, 0.05)
            .directionalBurst(VfxAnchor.TARGET, ParticleTypes.CLOUD, 5, 0.25, 40, 0.10)
            .shake(0.2f, 4)
        .build();

    // --- Swords ---
    public static final VfxDescriptor SWORD_BASIC = VfxDescriptor.builder()
        .phase(0)
            .sound(VfxAnchor.TARGET, SoundEvents.ITEM_TRIDENT_HIT, 1.0f, 1.1f)
            .particles(ParticleTypes.SWEEP_ATTACK, VfxAnchor.TARGET, 1, ZERO, 0.0)
            .particles(ParticleTypes.CRIT, VfxAnchor.TARGET, 3, TIGHT, 0.1)
            .directionalBurst(VfxAnchor.TARGET, ParticleTypes.CRIT, 8, 0.32, 30, 0.12)
            .shake(0.3f, 5)
        .build();

    public static final VfxDescriptor SWORD_DIAMOND_CRIT = VfxDescriptor.builder()
        .phase(0)
            .sound(VfxAnchor.TARGET, SoundEvents.ITEM_TRIDENT_HIT, 1.2f, 1.3f)
            .particles(ParticleTypes.SWEEP_ATTACK, VfxAnchor.TARGET, 2, ZERO, 0.0)
            .particles(ParticleTypes.ENCHANTED_HIT, VfxAnchor.TARGET, 6, WIDE, 0.2)
            .directionalBurst(VfxAnchor.TARGET, ParticleTypes.ENCHANTED_HIT, 12, 0.38, 35, 0.18)
            .ring(VfxAnchor.TARGET, 0.8, ParticleTypes.ENCHANT, 12)
            .bounce(VfxAnchor.TARGET, 0, 0, 0.22f, 8)
            .shake(0.5f, 6)
            .hitPause(2)
            .floatingText(VfxAnchor.TARGET, "CRIT!", 0xFFFFAA00, 30)
        .build();

    public static final VfxDescriptor SWORD_NETHERITE_EXECUTE = VfxDescriptor.builder()
        .phase(0)
            .sound(VfxAnchor.ORIGIN, SoundEvents.BLOCK_NETHERITE_BLOCK_PLACE, 1.0f, 0.8f)
            .particles(ParticleTypes.SOUL_FIRE_FLAME, VfxAnchor.ORIGIN, 8, TIGHT, 0.05)
            .vignette(VfxPrimitive.VignetteType.EXECUTE, 1, 8)
        .phase(5)
            .sound(VfxAnchor.TARGET, SoundEvents.ENTITY_WARDEN_ATTACK_IMPACT, 1.2f, 0.7f)
            .ring(VfxAnchor.TARGET, 1.2, ParticleTypes.SOUL_FIRE_FLAME, 16)
            .directionalBurst(VfxAnchor.TARGET, ParticleTypes.SOUL_FIRE_FLAME, 10, 0.32, 40, 0.20)
            .particles(ParticleTypes.LARGE_SMOKE, VfxAnchor.TARGET, 20, new Vec3d(0.6, 0.6, 0.6), 0.05)
            .debris(VfxAnchor.TARGET, Blocks.BLACKSTONE.getDefaultState(), 8, 0.3)
            .tileRingFlash(VfxAnchor.TARGET, 0, EXECUTE_SCORCH, 14)
            .bounce(VfxAnchor.TARGET, 0, 0, 0.5f, 12)
            .shake(0.9f, 8)
            .hitPause(3)
            .floatingText(VfxAnchor.TARGET, "EXECUTE", 0xFFFF2222, 30)
        .phase(12)
            .launchBlock(VfxAnchor.TARGET, new Vec3d(0.0, 0.5, 0.0),
                         Blocks.CRYING_OBSIDIAN.getDefaultState(), 40)
        .build();

    public static final VfxDescriptor SWORD_SWEEP = VfxDescriptor.builder()
        .phase(0)
            .particles(ParticleTypes.SWEEP_ATTACK, VfxAnchor.TARGET, 1, ZERO, 0.0)
            .directionalBurst(VfxAnchor.TARGET, ParticleTypes.CRIT, 6, 0.3, 60, 0.10)
            .shake(0.2f, 3)
        .build();

    // --- Axes ---
    public static final VfxDescriptor AXE_CLEAVE = VfxDescriptor.builder()
        .phase(0)
            .sound(VfxAnchor.TARGET, SoundEvents.ITEM_AXE_STRIP, 1.0f, 0.9f)
            .particles(ParticleTypes.CRIT, VfxAnchor.TARGET, 4, new Vec3d(0.5, 0.3, 0.5), 0.15)
            .directionalBurst(VfxAnchor.TARGET, ParticleTypes.CRIT, 10, 0.42, 25, 0.10)
            .debris(VfxAnchor.TARGET, Blocks.DIRT.getDefaultState(), 6, 0.25)
            .trail(VfxAnchor.ORIGIN, VfxAnchor.TARGET, ParticleTypes.SWEEP_ATTACK, null, 4, 0.4)
            .bounce(VfxAnchor.TARGET, 0, 0, 0.18f, 7)
            .shake(0.5f, 6)
        .build();

    public static final VfxDescriptor AXE_STUN_FLOURISH = VfxDescriptor.builder()
        .phase(6)
            .ring(VfxAnchor.TARGET, 0.4, ParticleTypes.CLOUD, 8)
            .particles(ParticleTypes.CLOUD, VfxAnchor.TARGET, 6, new Vec3d(0.1, 0.5, 0.1), 0.05)
            .floatingText(VfxAnchor.TARGET, "STUNNED", 0xFFFFFF55, 25)
        .build();

    // --- Mace ---
    /** Ground slam: windup whoosh, then the impact detonates a 2-ring shockwave
     *  that ripples out under the 3x3 slam area - dust rings, a pale wave flash,
     *  falling-pitch thumps, and mobs bouncing as the wave passes under them. */
    public static final VfxDescriptor MACE_SLAM = VfxDescriptor.builder()
        .phase(0)
            .sound(VfxAnchor.ORIGIN, SoundEvents.ITEM_MACE_SMASH_AIR, 1.0f, 1.0f)
            .particles(ParticleTypes.CLOUD, VfxAnchor.ORIGIN, 6, WIDE, 0.1)
        .phase(5)
            .sound(VfxAnchor.TARGET, SoundEvents.ITEM_MACE_SMASH_GROUND, 1.0f, 1.0f)
            .particles(ParticleTypes.EXPLOSION, VfxAnchor.TARGET, 1, ZERO, 0.0)
            .directionalBurst(VfxAnchor.TARGET, ParticleTypes.CLOUD, 10, 0.45, 70, 0.25)
            .debris(VfxAnchor.TARGET, Blocks.COBBLESTONE.getDefaultState(), 8, 0.4)
            .launchFloorBlock(VfxAnchor.TARGET, new Vec3d( 0.35, 0.6,  0.0), 40)
            .launchFloorBlock(VfxAnchor.TARGET, new Vec3d(-0.35, 0.6,  0.0), 40)
            .launchFloorBlock(VfxAnchor.TARGET, new Vec3d( 0.0,  0.7,  0.35), 40)
            .bounce(VfxAnchor.TARGET, 0, 0, 0.5f, 12)
            .shockwave(VfxAnchor.TARGET, 2, 2, ParticleTypes.CLOUD, ParticleTypes.WHITE_ASH,
                       WAVE_DUST, 10, 0.35f, 10, SoundEvents.BLOCK_BASALT_BREAK)
            .shake(0.8f, 10)
            .hitPause(2)
        .build();

    /** Heavy smash (Wind Burst): a bigger 3-ring wave, heavier smash sound, and
     *  a brief white flash - the arena-clearing statement swing. */
    public static final VfxDescriptor MACE_SMASH_AOE = VfxDescriptor.builder()
        .phase(0)
            .sound(VfxAnchor.ORIGIN, SoundEvents.ITEM_MACE_SMASH_AIR, 1.2f, 0.8f)
            .particles(ParticleTypes.CLOUD, VfxAnchor.ORIGIN, 8, WIDE, 0.1)
        .phase(5)
            .sound(VfxAnchor.TARGET, SoundEvents.ITEM_MACE_SMASH_GROUND_HEAVY, 1.1f, 0.95f)
            .particles(ParticleTypes.EXPLOSION, VfxAnchor.TARGET, 2, TIGHT, 0.0)
            .directionalBurst(VfxAnchor.TARGET, ParticleTypes.CLOUD, 14, 0.5, 90, 0.3)
            .debris(VfxAnchor.TARGET, Blocks.COBBLESTONE.getDefaultState(), 14, 0.5)
            .launchFloorBlock(VfxAnchor.TARGET, new Vec3d( 0.5, 0.8,  0.0), 50)
            .launchFloorBlock(VfxAnchor.TARGET, new Vec3d(-0.5, 0.8,  0.0), 50)
            .launchFloorBlock(VfxAnchor.TARGET, new Vec3d( 0.0, 0.9,  0.5), 50)
            .launchFloorBlock(VfxAnchor.TARGET, new Vec3d( 0.0, 0.9, -0.5), 50)
            .bounce(VfxAnchor.TARGET, 0, 0, 0.6f, 14)
            .shockwave(VfxAnchor.TARGET, 3, 2, ParticleTypes.CLOUD, ParticleTypes.WHITE_ASH,
                       WAVE_DUST, 12, 0.45f, 12, SoundEvents.BLOCK_BASALT_BREAK)
            .screenFlash(0x28FFFFFF, 4)
            .shake(1.0f, 12)
            .hitPause(4)
        .build();

    /** Light per-victim flourish for mobs caught in the slam's 3x3 - the wave
     *  itself supplies the ring/bounce/thump, so this stays a small poof
     *  instead of re-detonating the whole AoE on every extra target. */
    public static final VfxDescriptor MACE_SMASH_SECONDARY = VfxDescriptor.builder()
        .phase(5)
            .particles(ParticleTypes.CLOUD, VfxAnchor.TARGET, 4, TIGHT, 0.08)
            .particles(ParticleTypes.CRIT, VfxAnchor.TARGET, 3, TIGHT, 0.12)
        .build();

    // --- Bow ---
    public static final VfxDescriptor BOW = VfxDescriptor.builder()
        .phase(0)
            .sound(VfxAnchor.ORIGIN, SoundEvents.ITEM_CROSSBOW_LOADING_MIDDLE.value(), 0.6f, 1.5f)
            .particles(ParticleTypes.ENCHANT, VfxAnchor.ORIGIN, 4, TIGHT, 0.05)
        .phase(4)
            .sound(VfxAnchor.TARGET, SoundEvents.ENTITY_ARROW_HIT, 1.0f, 1.0f)
            .trail(VfxAnchor.ORIGIN, VfxAnchor.TARGET, ParticleTypes.CRIT, null, 10, 0.15)
            .directionalBurst(VfxAnchor.TARGET, ParticleTypes.CRIT, 5, 0.4, 15, 0.05)
            .shake(0.3f, 5)
        .build();

    // --- Crossbow ---
    public static final VfxDescriptor CROSSBOW = VfxDescriptor.builder()
        .phase(0)
            .sound(VfxAnchor.ORIGIN, SoundEvents.ITEM_CROSSBOW_SHOOT, 1.0f, 1.0f)
        .phase(4)
            .sound(VfxAnchor.TARGET, SoundEvents.ENTITY_ARROW_HIT, 1.2f, 0.9f)
            .trail(VfxAnchor.ORIGIN, VfxAnchor.TARGET, ParticleTypes.CRIT, ParticleTypes.WHITE_ASH, 14, 0.1)
            .directionalBurst(VfxAnchor.TARGET, ParticleTypes.CRIT, 7, 0.5, 12, 0.05)
            .shake(0.4f, 5)
        .build();

    public static final VfxDescriptor CROSSBOW_PIERCE_FLOURISH = VfxDescriptor.builder()
        .phase(4)
            .trail(VfxAnchor.ORIGIN, VfxAnchor.TARGET, ParticleTypes.CRIT, null, 8, 0.1)
            .directionalBurst(VfxAnchor.TARGET, ParticleTypes.CRIT, 5, 0.45, 12, 0.05)
        .build();

    // --- Trident ---
    public static final VfxDescriptor TRIDENT_STAB = VfxDescriptor.builder()
        .phase(0)
            .sound(VfxAnchor.ORIGIN, SoundEvents.ITEM_TRIDENT_THROW.value(), 0.5f, 1.3f)
            .converge(VfxAnchor.ORIGIN, 0.8, ParticleTypes.BUBBLE, 10)
        .phase(4)
            .sound(VfxAnchor.TARGET, SoundEvents.ITEM_TRIDENT_HIT, 1.0f, 0.9f)
            .particles(ParticleTypes.SPLASH, VfxAnchor.TARGET, 8, new Vec3d(0.4, 0.4, 0.4), 0.1)
            .directionalBurst(VfxAnchor.TARGET, ParticleTypes.SPLASH, 8, 0.35, 30, 0.15)
            .particles(ParticleTypes.DRIPPING_WATER, VfxAnchor.TARGET, 6, TIGHT, 0.02)
            .shake(0.4f, 5)
        .build();

    public static final VfxDescriptor TRIDENT_THROWN = VfxDescriptor.builder()
        .phase(0)
            .sound(VfxAnchor.ORIGIN, SoundEvents.ITEM_TRIDENT_THROW.value(), 1.0f, 1.0f)
            .particles(ParticleTypes.BUBBLE, VfxAnchor.ORIGIN, 6, TIGHT, 0.1)
        .phase(8)
            .sound(VfxAnchor.TARGET, SoundEvents.ITEM_TRIDENT_HIT, 1.2f, 0.9f)
            .trail(VfxAnchor.ORIGIN, VfxAnchor.TARGET, ParticleTypes.BUBBLE, ParticleTypes.SPLASH, 18, 0.3)
            .particles(ParticleTypes.SPLASH, VfxAnchor.TARGET, 10, WIDE, 0.2)
            .directionalBurst(VfxAnchor.TARGET, ParticleTypes.SPLASH, 8, 0.4, 25, 0.15)
            .shake(0.5f, 6)
        .build();

    // --- Shovel ---
    public static final VfxDescriptor SHOVEL = VfxDescriptor.builder()
        .phase(0)
            .sound(VfxAnchor.TARGET, SoundEvents.BLOCK_GRAVEL_HIT, 1.0f, 0.9f)
            .debris(VfxAnchor.TARGET, Blocks.DIRT.getDefaultState(), 10, 0.3)
            .particles(ParticleTypes.CLOUD, VfxAnchor.TARGET, 4, TIGHT, 0.1)
            .shake(0.3f, 4)
        .build();

    public static final VfxDescriptor SHOVEL_HEAVY = VfxDescriptor.builder()
        .phase(0)
            .sound(VfxAnchor.TARGET, SoundEvents.BLOCK_GRAVEL_HIT, 1.2f, 0.7f)
            .debris(VfxAnchor.TARGET, Blocks.DIRT.getDefaultState(), 14, 0.4)
            .particles(ParticleTypes.CLOUD, VfxAnchor.TARGET, 6, TIGHT, 0.15)
            .launchBlock(VfxAnchor.TARGET, new Vec3d(0.2, 0.5, 0.0), Blocks.DIRT.getDefaultState(), 30)
            .bounce(VfxAnchor.TARGET, 0, 0, 0.2f, 7)
            .shake(0.4f, 5)
        .build();

    // --- Hoe ---
    public static final VfxDescriptor HOE = VfxDescriptor.builder()
        .phase(0)
            .sound(VfxAnchor.TARGET, SoundEvents.ITEM_HOE_TILL, 1.0f, 1.2f)
            .particles(ParticleTypes.HAPPY_VILLAGER, VfxAnchor.TARGET, 6, TIGHT, 0.1)
            .trail(VfxAnchor.ORIGIN, VfxAnchor.TARGET, ParticleTypes.ENCHANT, null, 6, 0.2)
            .shake(0.2f, 3)
        .build();

    public static final VfxDescriptor HOE_AP_REFUND_FLOURISH = VfxDescriptor.builder()
        .phase(0)
            .sound(VfxAnchor.ORIGIN, SoundEvents.BLOCK_BEACON_ACTIVATE, 0.4f, 1.8f)
            .particles(ParticleTypes.END_ROD, VfxAnchor.ORIGIN, 10, new Vec3d(0.3, 0.5, 0.3), 0.15)
            .ring(VfxAnchor.ORIGIN, 0.6, ParticleTypes.ENCHANT, 12)
            .vignette(VfxPrimitive.VignetteType.FROST, 1, 6)
            .floatingText(VfxAnchor.ORIGIN, "+1 AP", 0xFF55FFFF, 25)
        .build();
}
