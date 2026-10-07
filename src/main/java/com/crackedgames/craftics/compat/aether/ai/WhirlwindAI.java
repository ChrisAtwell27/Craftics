package com.crackedgames.craftics.compat.aether.ai;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.ai.EnemyAI;
import com.crackedgames.craftics.combat.ai.EnemyAction;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;
import com.crackedgames.craftics.core.GridTile;

import java.util.List;

/**
 * The Aether's two whirlwinds: a column of wind that throws whatever it touches.
 *
 * <p><b>Whirlwind.</b> Not out to get anyone. It wanders, and if a player is beside it when
 * its turn comes round it tosses them two tiles away and carries on. No damage - like the
 * zephyr's gust, where you land is the whole of the harm. Because it never hunts, it does not
 * count as a threat, so a room is not held open by one drifting in the corner.
 *
 * <p><b>Evil Whirlwind.</b> The same wind with intent. It closes on its target and its toss
 * is a real hit: its attack damage and a longer throw.
 *
 * <p>In the Aether neither can be harmed and both blow themselves out after half a minute.
 * Here they have health like anything else and stay until killed: the lifetime is pushed out
 * of reach when one is spawned (see {@code AetherMobs}), and "cannot be harmed" was only ever
 * true of the real entity, which a fight does not hit.
 *
 * <p>Neither goes into water. The real entity deletes itself the moment it touches a fluid,
 * the same way a swet does, so a path that would wet it is turned down.
 */
public class WhirlwindAI implements EnemyAI {

    /** Tiles a plain whirlwind throws its victim. */
    public static final int TOSS_TILES = 2;
    /** Tiles an evil whirlwind's hit throws its victim. */
    public static final int EVIL_TOSS_TILES = 3;

    private final boolean evil;

    public WhirlwindAI(boolean evil) {
        this.evil = evil;
    }

    @Override
    public EnemyAction decideAction(CombatEntity self, GridArena arena, GridPos playerPos) {
        boolean beside = playerPos != null && self.minChebyshevDistanceTo(playerPos) <= 1;
        if (beside) {
            if (evil) return new EnemyAction.AttackWithKnockback(self.getAttackPower(), EVIL_TOSS_TILES);
            EnemyAction toss = toss(self, arena, playerPos);
            if (toss != null) return toss;
        }
        if (evil && playerPos != null) {
            EnemyAction chase = dry(arena, moveOrIdle(AetherAi.stepToward(self, arena, playerPos)));
            if (chase != null) return chase;
            return new EnemyAction.Idle();
        }
        EnemyAction drift = dry(arena, AetherAi.loiter(self, arena));
        return drift != null ? drift : new EnemyAction.Idle();
    }

    /** A plain whirlwind hunts nobody. An evil one always does. */
    @Override
    public boolean isHostileThreat(CombatEntity self, GridArena arena, GridPos playerPos) {
        return evil;
    }

    /**
     * Throw whoever is beside it straight away from it, as far as there is clear ground.
     * Null when there is nowhere to throw them to: a victim against a wall just stays put.
     */
    private static EnemyAction toss(CombatEntity self, GridArena arena, GridPos target) {
        int[] dir = ZephyrAI.gustDirection(self.nearestTileTo(target), target);
        int room = AetherAi.roomBehind(arena, target, dir, TOSS_TILES);
        if (room == 0) return null;
        // -1 is the engine's word for "the player"; a pet is named by its own id.
        CombatEntity pet = arena.getOccupant(target);
        int thrown = pet != null && pet.isAlly() ? pet.getEntityId() : -1;
        return new EnemyAction.ForcedMovement(thrown, dir[0], dir[1], room);
    }

    private static EnemyAction moveOrIdle(List<GridPos> path) {
        return path == null || path.isEmpty() ? null : new EnemyAction.Move(path);
    }

    /** {@code action} if it keeps the whirlwind out of water, otherwise null. */
    private static EnemyAction dry(GridArena arena, EnemyAction action) {
        if (action instanceof EnemyAction.Move move) {
            for (GridPos step : move.path()) {
                GridTile tile = arena.getTile(step);
                if (tile != null && tile.isWater()) return null;
            }
        }
        return action;
    }
}
