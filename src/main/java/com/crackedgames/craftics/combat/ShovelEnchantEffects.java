package com.crackedgames.craftics.combat;

import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * Behavior for the shovel (Pet affinity) enchantments. The shovel never swings - it is a focus
 * the owner carries, and every effect here fires through the owner's ALLIES instead.
 *
 * <p>Levels are read from anywhere in the owner's inventory via {@link CrafticsEnchantments},
 * taking the highest of any shovel carried, so nothing has to be held and duplicates don't stack.
 *
 * <p>Split out of {@code CombatManager} because these are self-contained: given the ally, its
 * victim, and the owner, each effect knows what to do. The manager just calls
 * {@link #applyOnAllyHit} at the moment an ally's attack lands.
 *
 * @since 0.2.92
 */
public final class ShovelEnchantEffects {

    private ShovelEnchantEffects() {}

    /** Damage each level of Honed adds to every one of the owner's pets. */
    public static final int HONED_DAMAGE_PER_LEVEL = 1;

    /** Turns of Burning that Fire Fang I inflicts; each further level adds one more. */
    private static final int FIRE_FANG_BASE_TURNS = 2;
    /** Extra burn damage per turn that Fire Fang stacks on. */
    private static final int FIRE_FANG_DAMAGE = 1;

    /** Turns of Soaked that Water Fang I inflicts; each further level adds one more. */
    private static final int WATER_FANG_BASE_TURNS = 2;

    /** Shock damage dealt to each enemy caught around the victim. */
    private static final int THUNDER_FANG_DAMAGE = 3;

    /**
     * Extra damage the owner's Honed shovel grants this ally. Folded into the same per-attack
     * bonus as Pet affinity, so the number on the inspect panel matches what the ally hits for.
     *
     * @return {@code level * }{@value #HONED_DAMAGE_PER_LEVEL}, or 0 without the enchantment
     */
    public static int honedBonus(ServerPlayerEntity owner) {
        return CrafticsEnchantments.level(owner, CrafticsEnchantments.HONED) * HONED_DAMAGE_PER_LEVEL;
    }

    /**
     * The radius Thunder Fang's shock reaches around the ally's victim: 1 tile at level I, out to
     * 3 at level III. Level 0 means no Thunder Fang, so nothing is shocked.
     */
    public static int thunderRadius(int level) {
        return level <= 0 ? 0 : level;
    }

    /**
     * Run every Fang the owner carries against the enemy an ally just hit. Called immediately
     * after the ally's damage lands, so a victim killed by the hit itself is never processed.
     *
     * <p>Thunder Fang is the only one that reaches past the victim: it arcs to every OTHER live
     * enemy within {@link #thunderRadius} tiles. It deliberately runs last, so a Water Fang shovel
     * paired with a Thunder Fang one soaks the victim before the arc, and the shock lands doubled
     * (soaked targets take double lightning damage).
     *
     * @param ally   the ally that just attacked
     * @param victim the enemy it hit; must still be alive
     * @param owner  the ally's owner, whose shovels supply the enchantments
     * @param arena  the combat arena, for finding shock targets
     * @param world  the world, for particles and sound
     * @param shocked out-param: every enemy Thunder Fang arced to, for the caller's death checks
     * @return a short coloured chat fragment naming what fired, or {@code ""} if nothing did
     */
    public static String applyOnAllyHit(CombatEntity ally, CombatEntity victim,
                                        ServerPlayerEntity owner, GridArena arena, ServerWorld world,
                                        List<CombatEntity> shocked) {
        if (ally == null || victim == null || owner == null || !victim.isAlive()) return "";

        StringBuilder msg = new StringBuilder();
        BlockPos victimBlock = arena != null ? arena.gridToBlockPos(victim.getGridPos()) : null;

        int fire = CrafticsEnchantments.level(owner, CrafticsEnchantments.FIRE_FANG);
        if (fire > 0) {
            // Level I burns 2 turns, II burns 3, III burns 4.
            victim.stackBurning(FIRE_FANG_BASE_TURNS + fire - 1, FIRE_FANG_DAMAGE);
            msg.append(" §6Fire Fang!");
            if (world != null && victimBlock != null) {
                world.spawnParticles(ParticleTypes.FLAME,
                    victimBlock.getX() + 0.5, victimBlock.getY() + 1.0, victimBlock.getZ() + 0.5,
                    12, 0.3, 0.5, 0.3, 0.03);
                world.spawnParticles(ParticleTypes.LAVA,
                    victimBlock.getX() + 0.5, victimBlock.getY() + 1.0, victimBlock.getZ() + 0.5,
                    3, 0.2, 0.2, 0.2, 0.0);
                world.playSound(null, victimBlock, SoundEvents.ENTITY_BLAZE_SHOOT,
                    SoundCategory.PLAYERS, 0.6f, 1.4f);
            }
        }

        int water = CrafticsEnchantments.level(owner, CrafticsEnchantments.WATER_FANG);
        if (water > 0) {
            // Level I soaks 2 turns, II soaks 3, III soaks 4.
            victim.stackSoaked(WATER_FANG_BASE_TURNS + water - 1, 0);
            msg.append(" §bWater Fang!");
            if (world != null && victimBlock != null) {
                world.spawnParticles(ParticleTypes.SPLASH,
                    victimBlock.getX() + 0.5, victimBlock.getY() + 1.2, victimBlock.getZ() + 0.5,
                    16, 0.35, 0.4, 0.35, 0.12);
                world.spawnParticles(ParticleTypes.FALLING_WATER,
                    victimBlock.getX() + 0.5, victimBlock.getY() + 1.4, victimBlock.getZ() + 0.5,
                    8, 0.3, 0.2, 0.3, 0.0);
                world.playSound(null, victimBlock, SoundEvents.ENTITY_PLAYER_SPLASH,
                    SoundCategory.PLAYERS, 0.7f, 1.2f);
            }
        }

        // Thunder last: anything Water Fang just soaked takes DOUBLE lightning damage.
        int thunder = CrafticsEnchantments.level(owner, CrafticsEnchantments.THUNDER_FANG);
        if (thunder > 0 && arena != null) {
            int radius = thunderRadius(thunder);
            List<CombatEntity> arced = shockNear(victim, arena, radius, world);
            if (shocked != null) shocked.addAll(arced);
            msg.append(" §eThunder Fang!");
            if (!arced.isEmpty()) {
                msg.append(" §7(").append(arced.size()).append(" shocked)");
            }
            if (world != null && victimBlock != null) {
                world.playSound(null, victimBlock, SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER,
                    SoundCategory.PLAYERS, 0.35f, 1.8f);
            }
        }

        return msg.toString();
    }

    /**
     * Arc Thunder Fang from {@code victim} to every OTHER live enemy within {@code radius} tiles,
     * dealing {@value #THUNDER_FANG_DAMAGE} lightning damage to each. The victim itself is skipped -
     * it already took the ally's hit, and shocking it again would double-dip the same strike.
     *
     * @return the enemies actually shocked, so the caller can run its death pipeline on them
     */
    private static List<CombatEntity> shockNear(CombatEntity victim, GridArena arena, int radius,
                                                ServerWorld world) {
        List<CombatEntity> hit = new ArrayList<>();
        GridPos center = victim.getGridPos();
        for (CombatEntity e : arena.getOccupants().values()) {
            if (e == victim || !e.isAlive() || e.isAlly() || e.isMountWall()) continue;
            if (hit.contains(e)) continue; // a multi-tile enemy appears once per occupied tile
            if (e.minDistanceTo(center) > radius) continue;
            // Lightning damage doubles on a Soaked target - the Water Fang combo.
            e.takeLightningDamage(THUNDER_FANG_DAMAGE);
            hit.add(e);
            if (world != null) {
                BlockPos bp = arena.gridToBlockPos(e.getGridPos());
                world.spawnParticles(ParticleTypes.ELECTRIC_SPARK,
                    bp.getX() + 0.5, bp.getY() + 1.0, bp.getZ() + 0.5,
                    18, 0.35, 0.6, 0.35, 0.25);
                world.spawnParticles(ParticleTypes.END_ROD,
                    bp.getX() + 0.5, bp.getY() + 1.2, bp.getZ() + 0.5,
                    4, 0.2, 0.3, 0.2, 0.02);
            }
        }
        // Draw the arc itself from the victim out to each thing it jumped to.
        if (world != null && !hit.isEmpty()) {
            BlockPos from = arena.gridToBlockPos(center);
            for (CombatEntity e : hit) {
                ProjectileSpawner.spawnSpellTrail(world, from, arena.gridToBlockPos(e.getGridPos()),
                    ParticleTypes.ELECTRIC_SPARK, ParticleTypes.END_ROD, 10, 0.35);
            }
        }
        return hit;
    }
}
