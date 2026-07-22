package com.crackedgames.craftics.combat.ai.deeperanddarker;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.ai.AIUtils;
import com.crackedgames.craftics.combat.ai.EnemyAI;
import com.crackedgames.craftics.combat.ai.EnemyAction;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;
import net.minecraft.entity.mob.MobEntity;

import java.util.List;

/**
 * Deeper-and-Darker Stalker miniboss AI (Ancient Temple, deep_dark level 4).
 *
 * <p>A tall, warden-like stalker that lurches between two modes on an every-
 * other-turn cadence:
 * <ul>
 *   <li><b>HUNT turn</b> - fully visible, closes in and hits like a heavy melee
 *       bruiser (lower speed than a real warden).</li>
 *   <li><b>STALK turn</b> - it vanishes. The entity goes invisible and the
 *       player can no longer see OR hover-target it (vanilla {@code isInvisible()}
 *       entities are already skipped by {@code TileRaycast}). It relocates to a
 *       fresh tile near the player while unseen, then reappears at the start of
 *       its next HUNT turn, ready to strike from a new angle.</li>
 * </ul>
 *
 * <p>Per-entity state (the mode counter) lives in {@code CombatEntity.aiMemory}
 * so the shared AI instance stays correct even with multiple stalkers, and it
 * survives across turns without a fresh-instance factory.
 */
public class StalkerAI implements EnemyAI {

    /** aiMemory key: monotonically increasing count of this stalker's own turns. */
    private static final String TURN_KEY = "stalker_turn";
    /** aiMemory key: 1 while the stalker is currently invisible (mid-STALK). */
    private static final String HIDDEN_KEY = "stalker_hidden";

    @Override
    public EnemyAction decideAction(CombatEntity self, GridArena arena, GridPos playerPos) {
        int turn = self.getAiMemory(TURN_KEY, 0);
        self.setAiMemory(TURN_KEY, turn + 1);
        MobEntity mob = self.getMobEntity();

        boolean wasHidden = self.getAiMemory(HIDDEN_KEY, 0) == 1;

        // Coming out of a STALK turn: reappear, then act as a HUNT turn.
        if (wasHidden) {
            self.setAiMemory(HIDDEN_KEY, 0);
            if (mob != null) mob.setInvisible(false);
            return huntTurn(self, arena, playerPos);
        }

        // Alternate HUNT / STALK on every other turn (0,2,4 = HUNT; 1,3,5 = STALK).
        boolean stalkTurn = (turn % 2) == 1;
        if (stalkTurn) {
            return stalkTurn(self, arena, playerPos, mob);
        }
        return huntTurn(self, arena, playerPos);
    }

    /** Visible aggression: close and hit hard. */
    private EnemyAction huntTurn(CombatEntity self, GridArena arena, GridPos playerPos) {
        if (self.minDistanceTo(playerPos) <= 1) {
            return new EnemyAction.Attack(self.getAttackPower());
        }
        // meleeOrApproach equivalent: seek toward the player at the stalker's
        // (deliberately modest) speed.
        return AIUtils.seekOrWander(self, arena, playerPos);
    }

    /**
     * Vanish and relocate. The stalker goes invisible this turn and teleports to
     * a fresh tile near the player (but not adjacent - it reappears at range, then
     * closes on its next HUNT turn). It stays hidden until its next turn.
     */
    private EnemyAction stalkTurn(CombatEntity self, GridArena arena, GridPos playerPos, MobEntity mob) {
        self.setAiMemory(HIDDEN_KEY, 1);
        if (mob != null) mob.setInvisible(true);

        GridPos relocate = findStalkTile(arena, self.getGridPos(), playerPos);
        if (relocate != null && !relocate.equals(self.getGridPos())) {
            // Instant reposition while unseen - no lerp animation (Teleport), which
            // reads as the stalker melting into the dark and re-forming elsewhere.
            return new EnemyAction.Teleport(relocate);
        }
        // Nowhere to slip to - just hold, still invisible.
        return new EnemyAction.Idle();
    }

    /**
     * A walkable, unoccupied tile 2-3 tiles from the player, preferring one on the
     * opposite side from where the stalker currently stands so it emerges from a
     * new direction. Null if nothing fits.
     */
    private GridPos findStalkTile(GridArena arena, GridPos myPos, GridPos playerPos) {
        List<GridPos> ring2 = ringCandidates(arena, playerPos, 2);
        List<GridPos> ring3 = ringCandidates(arena, playerPos, 3);
        List<GridPos> all = new java.util.ArrayList<>(ring2);
        all.addAll(ring3);
        if (all.isEmpty()) return null;

        // Prefer the tile farthest from our current position (emerge from behind).
        GridPos best = null;
        int bestScore = -1;
        for (GridPos p : all) {
            int score = p.manhattanDistance(myPos);
            if (score > bestScore) {
                bestScore = score;
                best = p;
            }
        }
        return best;
    }

    /** Walkable, unoccupied tiles at exactly Chebyshev {@code radius} from center. */
    private List<GridPos> ringCandidates(GridArena arena, GridPos center, int radius) {
        List<GridPos> out = new java.util.ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                GridPos p = new GridPos(center.x() + dx, center.z() + dz);
                if (!arena.isInBounds(p) || arena.isOccupied(p)) continue;
                var tile = arena.getTile(p);
                if (tile == null || !tile.isWalkable()) continue;
                out.add(p);
            }
        }
        return out;
    }
}
