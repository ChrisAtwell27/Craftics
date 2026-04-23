package com.crackedgames.craftics.combat;

import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;
import com.crackedgames.craftics.core.GridTile;
import com.crackedgames.craftics.core.TileType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;

import java.util.List;

/**
 * Centralized stealth-tile logic. A stealth tile ({@link TileType#providesStealth}
 * = true) visually hides its occupant via vanilla {@code INVISIBILITY} and
 * suppresses AI target-selection unless the hunter is on an adjacent tile.
 *
 * <p>Breaking the tile's block clears the effect automatically — the tile type
 * is re-read each tick, and the next non-stealth read lets the potion expire
 * naturally.
 */
public final class StealthTiles {
    private StealthTiles() {}

    public static boolean isStealthTile(GridArena arena, GridPos pos) {
        if (arena == null || pos == null) return false;
        GridTile t = arena.getTile(pos.x(), pos.z());
        return t != null && t.getType().providesStealth;
    }

    /** True if {@code target} is concealed from {@code observer} (observer not adjacent). */
    public static boolean isConcealedFrom(GridArena arena, GridPos observer, GridPos target) {
        if (!isStealthTile(arena, target)) return false;
        if (observer == null || target == null) return false;
        int dx = Math.abs(observer.x() - target.x());
        int dz = Math.abs(observer.z() - target.z());
        return dx > 1 || dz > 1; // not cardinally/diagonally adjacent
    }

    /**
     * Refresh vanilla invisibility on every combatant currently on a stealth
     * tile. Called once per combat tick; the effect is re-applied for a short
     * duration so it auto-expires shortly after the entity steps off.
     */
    public static void applyEach(GridArena arena, LivingEntity player, List<CombatEntity> enemies) {
        if (arena == null) return;
        if (player != null && !player.isRemoved()) {
            applyToEntity(player, isStealthTile(arena, arena.getPlayerGridPos()));
        }
        if (enemies != null) {
            for (CombatEntity e : enemies) {
                if (e == null || !e.isAlive()) continue;
                LivingEntity mob = e.getMobEntity();
                if (mob == null) continue;
                applyToEntity(mob, isStealthTile(arena, e.getGridPos()));
            }
        }
    }

    private static void applyToEntity(LivingEntity entity, boolean stealthed) {
        if (stealthed) {
            // Re-apply every tick with a 30-tick buffer so stepping off fades in ~1.5s.
            entity.addStatusEffect(new StatusEffectInstance(
                StatusEffects.INVISIBILITY, 30, 0, false, false, false));
        }
    }
}
