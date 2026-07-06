package com.crackedgames.craftics.combat.ai.boss;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.ai.EnemyAction;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;

import java.util.ArrayList;
import java.util.List;

/**
 * End City Boss - "The Shulker Architect"
 * Entity: Shulker | 50HP / 9ATK / 4DEF / Range 5 / Speed 1 | Size 2×2
 *
 * Abilities:
 * - Bullet Storm: telegraphed volley - 4 (P2: 6) marked tiles around the player,
 *   3 dmg each on resolve, with a half-power plink while it charges.
 * - Deploy Turret: Stationary shulker turret (6HP, 1 bullet/turn range 4, 2 dmg). Max 3 (P2: 5).
 * - Fortify Shell: 80% damage reduction 1 turn. P2: also reflects 50% damage.
 * - Teleport Link: Blink to a tile beside the turret farthest from the player.
 *
 * Phase 2 - "Defense Protocol": 6 bullets, turret levitation bullets, reflect shell,
 * turret limit 5, auto-deploy every 3 turns.
 */
public class ShulkerArchitectAI extends BossAI {
    @Override public int getGridSize() { return 2; }
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

        // Fortify Shell - reactive when hit
        if (wasHitLastTurn && !isOnCooldown(CD_SHELL)) {
            wasHitLastTurn = false;
            setCooldown(CD_SHELL, 3);
            String shellType = isPhaseTwo() ? "fortify_shell_reflect" : "fortify_shell";
            return new EnemyAction.BossAbility(shellType,
                new EnemyAction.ModifySelf("defense", 20, 1),
                List.of(myPos));
        }
        wasHitLastTurn = false;

        // Teleport Link - escape melee range by blinking to the network. The
        // boss lands BESIDE the turret farthest from the player (footprint-
        // validated for its 2×2 body) - the old code teleported squarely onto
        // the still-living turret's tile, clipping into its own minion.
        if (!isOnCooldown(CD_LINK) && dist <= 2 && !turretPositions.isEmpty()) {
            GridPos farthest = null;
            int maxDist = -1;
            for (GridPos tp : turretPositions) {
                int d = tp.manhattanDistance(playerPos);
                if (d > maxDist) {
                    maxDist = d;
                    farthest = tp;
                }
            }
            GridPos landing = farthest != null ? findLandingNear(arena, farthest, self) : null;
            if (landing != null) {
                setCooldown(CD_LINK, 2);
                return new EnemyAction.Teleport(landing);
            }
        }

        // Deploy Turret
        if (!isOnCooldown(CD_TURRET) && turretPositions.size() < turretCap) {
            List<GridPos> pos = findSummonPositions(arena, 1);
            if (!pos.isEmpty()) {
                setCooldown(CD_TURRET, 2);
                turretPositions.add(pos.get(0));
                return new EnemyAction.SummonMinions(
                    "minecraft:shulker", 1, pos, 6, 2, 0);
            }
        }

        // Bullet Storm - a telegraphed volley: one bullet per marked tile
        // (player's tile + surrounding spread, 4 in P1 / 6 in P2), resolving
        // next turn. The old version built the target list, threw it away, and
        // fired an instant untelegraphed AoE - the bullet count did nothing
        // and phase two's extra bullets were pure flavor text.
        if (!isOnCooldown(CD_BULLET) && dist <= 5) {
            setCooldown(CD_BULLET, 2);
            int bulletCount = isPhaseTwo() ? 6 : 4;
            List<GridPos> targets = new ArrayList<>();
            targets.add(playerPos);
            for (int[] d : new int[][]{{1,0},{-1,0},{0,1},{0,-1},{1,1},{-1,-1}}) {
                GridPos p = new GridPos(playerPos.x() + d[0], playerPos.z() + d[1]);
                if (arena.isInBounds(p) && targets.size() < bulletCount) targets.add(p);
            }
            List<EnemyAction> bullets = new ArrayList<>();
            for (GridPos t : targets) {
                bullets.add(new EnemyAction.AreaAttack(t, 0, 3, "bullet_storm"));
            }
            pendingWarning = new BossWarning(
                self.getEntityId(), BossWarning.WarningType.GATHERING_PARTICLES,
                targets, 1, new EnemyAction.CompositeAction(bullets), 0xFFCC88FF);
            // Plink a normal bullet while the volley charges - no free turn.
            return new EnemyAction.RangedAttack(Math.max(2, self.getAttackPower() / 2), "shulker_bullet");
        }

        // Ranged attack if in range
        if (dist <= 5) {
            return new EnemyAction.RangedAttack(self.getAttackPower(), "shulker_bullet");
        }

        return meleeOrApproach(self, arena, playerPos, 0);
    }

    /** A footprint-valid anchor adjacent to {@code turret} for the 2×2 boss; null if none. */
    private GridPos findLandingNear(GridArena arena, GridPos turret, CombatEntity self) {
        int size = self.getMaxSize();
        for (int dx = -size; dx <= 1; dx++) {
            for (int dz = -size; dz <= 1; dz++) {
                GridPos p = new GridPos(turret.x() + dx, turret.z() + dz);
                if (com.crackedgames.craftics.combat.ai.AIUtils.canPlaceFootprint(arena, p, size)) {
                    return p;
                }
            }
        }
        return null;
    }

    /** Called by CombatManager when the boss takes damage - primes Fortify Shell */
    public void notifyDamaged() { wasHitLastTurn = true; }

    public void removeTurret(GridPos pos) { turretPositions.remove(pos); }
    public List<GridPos> getTurretPositions() { return turretPositions; }
}
