package com.crackedgames.craftics.combat.ai.boss;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.Pathfinding;
import com.crackedgames.craftics.combat.ai.AIUtils;
import com.crackedgames.craftics.combat.ai.EnemyAction;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;
import com.crackedgames.craftics.core.TileType;

import java.util.ArrayList;
import java.util.List;

/**
 * Snowy Tundra Boss — "The Frostbound Huntsman"
 * Entity: Stray | 25HP / 5ATK / 2DEF / Range 4 / Speed 2 | Size 2×2
 *
 * Abilities:
 * - Blizzard: 3×3 AoE, 3 dmg + Slowness 2 turns. P2: center tile stuns.
 * - Ice Wall: Line of 3 obstacle tiles, 3 turns. Blocks movement + LOS.
 * - Frost Arrow: Range 4, ATK dmg + 1-turn Slowness.
 * - Glacial Trap: 2×2 freeze, start-of-turn stun 1 turn. Lasts 2 turns.
 *
 * Phase 2 — "Permafrost": Speed 3, random frozen tiles every 2 turns.
 */
public class FrostboundAI extends BossAI {
    private static final String CD_BLIZZARD = "blizzard";
    private static final String CD_ICE_WALL = "ice_wall";
    private static final String CD_TRAP = "glacial_trap";

    @Override
    protected void onPhaseTransition(CombatEntity self, GridArena arena, GridPos playerPos) {
        self.setEnraged(true);
        self.setSpeedBonus(1); // Speed 2 → 3
    }

    @Override
    protected EnemyAction chooseAbility(CombatEntity self, GridArena arena, GridPos playerPos) {
        GridPos myPos = self.getGridPos();
        int dist = self.minDistanceTo(playerPos);

        // Phase 2: Auto-freeze 2 random tiles every 2 turns
        if (isPhaseTwo() && getTurnCounter() % 2 == 0) {
            List<GridPos> freezeTiles = findSummonPositions(arena, 2); // reuse for random tiles
            if (!freezeTiles.isEmpty()) {
                // This passive effect layers on top of the ability chosen below
                // CombatManager handles this via BossAI phase 2 passive check
            }
        }

        // Blizzard on player cluster
        if (!isOnCooldown(CD_BLIZZARD) && dist <= 5) {
            List<GridPos> blizzardTiles = getAreaTiles(arena, playerPos, 1); // 3×3
            setCooldown(CD_BLIZZARD, 3);
            EnemyAction blizzardAction = new EnemyAction.AreaAttack(playerPos, 1, 3, "blizzard");
            pendingWarning = new BossWarning(
                self.getEntityId(), BossWarning.WarningType.GATHERING_PARTICLES,
                blizzardTiles, 1, blizzardAction, 0xFF88CCFF);
            return new EnemyAction.Idle();
        }

        // Ice Wall: cut off escape routes
        if (!isOnCooldown(CD_ICE_WALL) && dist >= 2) {
            // Place wall perpendicular to the line between boss and player
            int[] dir = getDirectionToward(myPos, playerPos);
            int wallDx = dir[1] != 0 ? 1 : 0; // perpendicular
            int wallDz = dir[0] != 0 ? 1 : 0;
            // Place wall midway between boss and player
            GridPos midpoint = new GridPos(
                (myPos.x() + playerPos.x()) / 2,
                (myPos.z() + playerPos.z()) / 2);
            List<GridPos> wallTiles = new ArrayList<>();
            wallTiles.add(midpoint);
            wallTiles.add(new GridPos(midpoint.x() + wallDx, midpoint.z() + wallDz));
            wallTiles.add(new GridPos(midpoint.x() - wallDx, midpoint.z() - wallDz));
            // Filter to in-bounds
            wallTiles.removeIf(p -> !arena.isInBounds(p));
            if (!wallTiles.isEmpty()) {
                setCooldown(CD_ICE_WALL, 4);
                EnemyAction wallAction = new EnemyAction.CreateTerrain(wallTiles, TileType.OBSTACLE, 3);
                pendingWarning = new BossWarning(
                    self.getEntityId(), BossWarning.WarningType.TILE_HIGHLIGHT,
                    wallTiles, 1, wallAction, 0xFF44AAFF);
                return new EnemyAction.Idle();
            }
        }

        // Glacial Trap on tiles near player
        if (!isOnCooldown(CD_TRAP) && dist <= 4) {
            List<GridPos> trapTiles = getAreaTiles(arena, playerPos, 0); // 1×1 starting; actually 2×2
            // Make it a 2×2 starting from player pos
            trapTiles = new ArrayList<>();
            trapTiles.add(playerPos);
            trapTiles.add(new GridPos(playerPos.x() + 1, playerPos.z()));
            trapTiles.add(new GridPos(playerPos.x(), playerPos.z() + 1));
            trapTiles.add(new GridPos(playerPos.x() + 1, playerPos.z() + 1));
            trapTiles.removeIf(p -> !arena.isInBounds(p));
            setCooldown(CD_TRAP, 3);
            EnemyAction trapAction = new EnemyAction.AreaAttack(playerPos, 1, 0, "glacial_trap");
            pendingWarning = new BossWarning(
                self.getEntityId(), BossWarning.WarningType.GROUND_CRACK,
                trapTiles, 1, trapAction, 0xFF66DDFF);
            return new EnemyAction.Idle();
        }

        // Frost Arrow: standard ranged attack
        if (dist >= 2 && dist <= 4) {
            return new EnemyAction.RangedAttack(self.getAttackPower(), "frost_arrow");
        }

        // Kite away if too close
        if (dist <= 1) {
            GridPos fleeTarget = AIUtils.getFleeTarget(arena, myPos, playerPos, self.getMoveSpeed());
            if (fleeTarget != null) {
                List<GridPos> path = Pathfinding.findPath(arena, myPos, fleeTarget, self.getMoveSpeed(), self);
                if (!path.isEmpty()) {
                    return new EnemyAction.Flee(path);
                }
            }
        }

        return meleeOrApproach(self, arena, playerPos, 0);
    }
}
