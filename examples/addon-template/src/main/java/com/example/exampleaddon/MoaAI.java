package com.example.exampleaddon;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.Pathfinding;
import com.crackedgames.craftics.combat.ai.AIUtils;
import com.crackedgames.craftics.combat.ai.EnemyAI;
import com.crackedgames.craftics.combat.ai.EnemyAction;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;

import java.util.List;

/**
 * A custom combat AI for the Moa enemy.
 *
 * <p>An EnemyAI decides what an enemy does on its turn. You register one with
 * {@code CrafticsAPI.registerAI} under a key, and an enemy uses it by naming that
 * key in its {@code "ai"} field (see enemies/moa.json). Without a custom AI an
 * enemy just borrows a vanilla mob's behavior.
 *
 * <p>This Moa fights in two phases based on its health:
 * <ul>
 *   <li>Above half health it is aggressive. It dashes in and strikes in melee.</li>
 *   <li>At or below half health it turns cautious. It backs away from the player
 *       and attacks from range instead.</li>
 * </ul>
 *
 * <p>{@code decideAction} returns one of the {@code EnemyAction} records (Attack,
 * Move, MoveAndAttack, RangedAttack, and so on). See the Craftics modding guide
 * for the full set.
 */
public final class MoaAI implements EnemyAI {

	@Override
	public EnemyAction decideAction(CombatEntity self, GridArena arena, GridPos playerPos) {
		boolean wounded = self.getCurrentHp() * 2 <= self.getMaxHp();
		return wounded
			? rangedPhase(self, arena, playerPos)
			: meleePhase(self, arena, playerPos);
	}

	/** Above half health: dash at the player and strike in melee. */
	private EnemyAction meleePhase(CombatEntity self, GridArena arena, GridPos playerPos) {
		GridPos myPos = self.getGridPos();
		int speed = self.getMoveSpeed();

		// Already next to the player: strike.
		if (self.minDistanceTo(playerPos) == 1) {
			return new EnemyAction.Attack(self.getAttackPower());
		}

		// Run to a tile beside the player and attack on arrival.
		GridPos goal = AIUtils.findBestAdjacentTarget(arena, myPos, playerPos, speed);
		if (goal == null) {
			goal = playerPos;
		}
		List<GridPos> path = Pathfinding.findPath(arena, myPos, goal, speed, self);
		if (path.isEmpty()) {
			return AIUtils.seekOrWander(self, arena, playerPos);
		}
		GridPos end = path.get(path.size() - 1);
		if (end.manhattanDistance(playerPos) == 1) {
			return new EnemyAction.MoveAndAttack(path, self.getAttackPower());
		}
		return new EnemyAction.Move(path);
	}

	/** At or below half health: back away from the player and attack from range. */
	private EnemyAction rangedPhase(CombatEntity self, GridArena arena, GridPos playerPos) {
		GridPos myPos = self.getGridPos();
		int speed = self.getMoveSpeed();
		int range = self.getRange();
		int dist = self.minDistanceTo(playerPos);

		// Player is too close: back off, and shoot if the retreat still ends in range.
		if (dist < range) {
			GridPos fleeTo = AIUtils.getFleeTarget(arena, myPos, playerPos, speed);
			if (fleeTo != null) {
				List<GridPos> path = Pathfinding.findPath(arena, myPos, fleeTo, speed, self);
				if (!path.isEmpty()) {
					GridPos end = path.get(path.size() - 1);
					if (end.manhattanDistance(playerPos) <= range) {
						return new EnemyAction.MoveAndAttack(path, self.getAttackPower());
					}
					return new EnemyAction.Move(path);
				}
			}
		}

		// At a good distance and within range: fire. The second argument selects
		// the projectile visual; see Craftics' own ranged AIs for the options.
		if (dist <= range) {
			return new EnemyAction.RangedAttack(self.getAttackPower(), "arrow");
		}

		// Out of range: close the gap. Next turn handles the shot.
		GridPos goal = AIUtils.findBestAdjacentTarget(arena, myPos, playerPos, speed);
		if (goal == null) {
			goal = playerPos;
		}
		List<GridPos> path = Pathfinding.findPath(arena, myPos, goal, speed, self);
		if (path.isEmpty()) {
			return AIUtils.seekOrWander(self, arena, playerPos);
		}
		return new EnemyAction.Move(path);
	}
}
