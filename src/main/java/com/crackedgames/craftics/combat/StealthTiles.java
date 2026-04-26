package com.crackedgames.craftics.combat;

import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;
import com.crackedgames.craftics.core.GridTile;
import com.crackedgames.craftics.core.TileType;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.List;

/**
 * Centralized stealth-tile logic. A stealth tile ({@link TileType#providesStealth}
 * = true) visually hides its occupant via vanilla {@code INVISIBILITY} and
 * suppresses AI target-selection unless the hunter is on an adjacent tile.
 *
 * <p>The cached {@link GridTile} type is the primary source of truth, but we
 * also fall back to a live world-block read so a {@code TALL_GRASS} /
 * {@code LARGE_FERN} block placed at the tile's upper level still grants
 * stealth even when the post-build tile classification missed it (which has
 * been observed on MC versions newer than 1.21.1).
 */
public final class StealthTiles {
    private StealthTiles() {}

    public static boolean isStealthTile(GridArena arena, GridPos pos, World world) {
        if (arena == null || pos == null) return false;
        GridTile t = arena.getTile(pos.x(), pos.z());
        if (t != null && t.getType().providesStealth) return true;
        if (world == null) return false;
        BlockPos worldPos = arena.gridToBlockPos(pos).up(1);
        var state = world.getBlockState(worldPos);
        return state.isOf(Blocks.TALL_GRASS) || state.isOf(Blocks.LARGE_FERN);
    }

    /** True if {@code target} is concealed from {@code observer} (observer not adjacent). */
    public static boolean isConcealedFrom(GridArena arena, GridPos observer, GridPos target, World world) {
        if (!isStealthTile(arena, target, world)) return false;
        if (observer == null || target == null) return false;
        int dx = Math.abs(observer.x() - target.x());
        int dz = Math.abs(observer.z() - target.z());
        return dx > 1 || dz > 1; // not cardinally/diagonally adjacent
    }

    /**
     * Refresh vanilla invisibility on every combatant currently on a stealth
     * tile. Called once per combat tick. Entities no longer on a stealth tile
     * have the invisibility actively stripped so visibility returns instantly
     * — without this an entity stays invisible for up to 1.5s after stepping
     * off, which makes the mechanic feel buggy.
     */
    public static void applyEach(GridArena arena, LivingEntity player, List<CombatEntity> enemies) {
        if (arena == null) return;
        World world = player != null ? player.getEntityWorld() : null;
        if (player != null && !player.isRemoved()) {
            applyToEntity(player, isStealthTile(arena, arena.getPlayerGridPos(), world));
        }
        if (enemies != null) {
            for (CombatEntity e : enemies) {
                if (e == null || !e.isAlive()) continue;
                LivingEntity mob = e.getMobEntity();
                if (mob == null) continue;
                applyToEntity(mob, isStealthTile(arena, e.getGridPos(), world));
            }
        }
    }

    private static void applyToEntity(LivingEntity entity, boolean stealthed) {
        if (stealthed) {
            // Re-apply every tick with a small buffer so a one-tick gap in the
            // detection (e.g. during animation snapshots) doesn't flicker.
            // showParticles=false because particles around the entity would
            // give away its position; showIcon=true so the invisibility badge
            // appears in the status-effect HUD next to other buffs/debuffs.
            entity.addStatusEffect(new StatusEffectInstance(
                StatusEffects.INVISIBILITY, 30, 0, false, false, true));
        } else {
            // Off the tile — strip OUR invisibility instantly. Heuristic match
            // (short duration, amplifier 0) avoids removing potion-of-
            // invisibility, which is much longer (3600+ ticks) at amplifier 0
            // but with showParticles=true, vs ours which is 30 ticks with
            // showParticles=false. Duration check alone is sufficient.
            StatusEffectInstance current = entity.getStatusEffect(StatusEffects.INVISIBILITY);
            if (current != null && current.getDuration() <= 30 && current.getAmplifier() == 0) {
                entity.removeStatusEffect(StatusEffects.INVISIBILITY);
            }
        }
    }
}
