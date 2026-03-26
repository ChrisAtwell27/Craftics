package com.crackedgames.craftics.combat.ai.boss;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.ai.EnemyAction;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;

import java.util.ArrayList;
import java.util.List;

/**
 * End City Boss — "The Shulker Architect"
 * Entity: Shulker | 50HP / 9ATK / 4DEF / Range 5 / Speed 1 | Size 2×2
 *
 * Abilities:
 * - Bullet Storm: 4 (P2: 6) homing bullets, 3 dmg each + Levitation (random 1-tile move).
 * - Deploy Turret: Stationary shulker turret (6HP, 1 bullet/turn range 4, 2 dmg). Max 3 (P2: 5).
 * - Fortify Shell: 80% damage reduction 1 turn. P2: also reflects 50% damage.
 * - Teleport Link: Teleport to active turret position (destroys that turret).
 *
 * Phase 2 — "Defense Protocol": 6 bullets, turret levitation bullets, reflect shell,
 * turret limit 5, auto-deploy every 3 turns.
 */
public class ShulkerArchitectAI extends BossAI {
    private static final String CD_BULLET = "bullet_storm";
    private static final String CD_TURRET = "deploy_turret";
    private static final String CD_SHELL = "fortify_shell";
    private static final String CD_LINK = "teleport_link";
    private final List<GridPos> turretPositions = new ArrayList<>();
    private boolean wasHitLastTurn = false;

    @Override
    protected void onPhaseTransition(CombatEntity self, GridArena arena, GridPos playerPos) {
        self.setEnraged(true);
    }

    @Override
    protected EnemyAction chooseAbility(CombatEntity self, GridArena arena, GridPos playerPos) {
        GridPos myPos = self.getGridPos();
        int dist = self.minDistanceTo(playerPos);

        // Phase 2: Auto-deploy turrets every 3 turns if below cap
        int turretCap = isPhaseTwo() ? 5 : 3;
        if (isPhaseTwo() && getTurnCounter() % 3 == 0 && turretPositions.size() < turretCap) {
            List<GridPos> pos = findSummonPositions(arena, 1);
            if (!pos.isEmpty()) {
                turretPositions.add(pos.get(0));
                return new EnemyAction.SummonMinions(
                    "minecraft:shulker", 1, pos, 6, 2, 0);
            }
        }

        // Fortify Shell — reactive when hit
        if (wasHitLastTurn && !isOnCooldown(CD_SHELL)) {
            wasHitLastTurn = false;
            setCooldown(CD_SHELL, 3);
            String shellType = isPhaseTwo() ? "fortify_shell_reflect" : "fortify_shell";
            return new EnemyAction.BossAbility(shellType,
                new EnemyAction.ModifySelf("defense", 20, 1),
                List.of(myPos));
        }
        wasHitLastTurn = false;

        // Teleport Link — escape melee range
        if (!isOnCooldown(CD_LINK) && dist <= 2 && !turretPositions.isEmpty()) {
            setCooldown(CD_LINK, 2);
            // Find farthest turret from player
            GridPos farthest = null;
            int maxDist = 0;
            for (GridPos tp : turretPositions) {
                int d = tp.manhattanDistance(playerPos);
                if (d > maxDist) {
                    maxDist = d;
                    farthest = tp;
                }
            }
            if (farthest != null) {
                turretPositions.remove(farthest);
                return new EnemyAction.Teleport(farthest);
            }
        }

        // Deploy Turret
        if (!isOnCooldown(CD_TURRET) && turretPositions.size() < turretCap) {
            setCooldown(CD_TURRET, 2);
            List<GridPos> pos = findSummonPositions(arena, 1);
            if (!pos.isEmpty()) {
                turretPositions.add(pos.get(0));
                return new EnemyAction.SummonMinions(
                    "minecraft:shulker", 1, pos, 6, 2, 0);
            }
        }

        // Bullet Storm — main ranged attack
        if (!isOnCooldown(CD_BULLET) && dist <= 5) {
            setCooldown(CD_BULLET, 2);
            int bulletCount = isPhaseTwo() ? 6 : 4;
            // Target player + nearby tiles
            List<GridPos> targets = new ArrayList<>();
            targets.add(playerPos);
            for (int[] d : new int[][]{{1,0},{-1,0},{0,1},{0,-1},{1,1},{-1,-1}}) {
                GridPos p = new GridPos(playerPos.x() + d[0], playerPos.z() + d[1]);
                if (arena.isInBounds(p) && targets.size() < bulletCount) targets.add(p);
            }
            return new EnemyAction.AreaAttack(playerPos, 1, 3, "bullet_storm");
        }

        // Ranged attack if in range
        if (dist <= 5) {
            return new EnemyAction.RangedAttack(self.getAttackPower(), "shulker_bullet");
        }

        return meleeOrApproach(self, arena, playerPos, 0);
    }

    /** Called by CombatManager when the boss takes damage — primes Fortify Shell */
    public void notifyDamaged() { wasHitLastTurn = true; }

    public void removeTurret(GridPos pos) { turretPositions.remove(pos); }
    public List<GridPos> getTurretPositions() { return turretPositions; }
}
