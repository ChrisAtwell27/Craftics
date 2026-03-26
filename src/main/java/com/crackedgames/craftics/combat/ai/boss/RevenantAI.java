package com.crackedgames.craftics.combat.ai.boss;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.ai.AIUtils;
import com.crackedgames.craftics.combat.ai.EnemyAction;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;
import com.crackedgames.craftics.core.TileType;

import java.util.List;

/**
 * Plains Boss — "The Revenant" (Undead Knight)
 * Entity: Zombie | 20HP / 4ATK / 2DEF / Speed 2 | Size 2×2
 *
 * Abilities:
 * - Raise the Dead: Summons 1–2 Zombies (6HP/2ATK) every 3 turns (2 in P2). Cap 3 alive.
 * - Death Charge: Straight line charge up to 3 tiles, ATK+2 damage. P2: ATK+4 + fire trail.
 * - Shield Bash: Adjacent knockback 2 tiles, half ATK damage.
 *
 * Phase 2 (≤50% HP) — "Undying Rage":
 * - Gains Regeneration (1 HP/turn) — handled by CombatManager checking isEnraged
 * - Raise the Dead every 2 turns instead of 3
 * - Death Charge ATK+4 + fire trail
 */
public class RevenantAI extends BossAI {
    private static final int SUMMON_CAP = 3;
    private static final String CD_RAISE = "raise_dead";
    private static final String CD_CHARGE = "death_charge";

    @Override
    protected void onPhaseTransition(CombatEntity self, GridArena arena, GridPos playerPos) {
        self.setEnraged(true);
    }

    @Override
    protected EnemyAction chooseAbility(CombatEntity self, GridArena arena, GridPos playerPos) {
        GridPos myPos = self.getGridPos();
        int dist = self.minDistanceTo(playerPos);
        int summonInterval = isPhaseTwo() ? 2 : 3;

        // Priority 1: Resolve pending warning (handled by base class)

        // Priority 2: Raise the Dead on schedule
        if (!isOnCooldown(CD_RAISE) && getAliveMinionCount() < SUMMON_CAP) {
            int count = isPhaseTwo() ? 2 : (getAliveMinionCount() == 0 ? 2 : 1);
            List<GridPos> positions = findSummonPositions(arena, count);
            if (!positions.isEmpty()) {
                setCooldown(CD_RAISE, summonInterval);
                // Telegraph: ground cracks on summon tiles, resolves next turn
                EnemyAction summon = new EnemyAction.SummonMinions(
                    "minecraft:zombie", positions.size(), positions, 6, 2, 0);
                pendingWarning = new BossWarning(
                    self.getEntityId(), BossWarning.WarningType.GROUND_CRACK,
                    positions, 1, summon, 0xFF44FF44);
                return new EnemyAction.Idle();
            }
        }

        // Priority 3: Death Charge if aligned on cardinal axis and in range
        if (!isOnCooldown(CD_CHARGE) && dist >= 2 && dist <= 4) {
            int[] dir = getDirectionToward(myPos, playerPos);
            // Check if player is on same axis
            boolean aligned = (dir[0] != 0 && myPos.z() == playerPos.z())
                           || (dir[1] != 0 && myPos.x() == playerPos.x());
            if (aligned) {
                List<GridPos> chargePath = getChargePath(arena, myPos, dir[0], dir[1], 3);
                if (!chargePath.isEmpty()) {
                    int chargeDmg = self.getAttackPower() + (isPhaseTwo() ? 4 : 2);
                    setCooldown(CD_CHARGE, 2);

                    // Build the warning action
                    List<EnemyAction> actions = new java.util.ArrayList<>();
                    actions.add(new EnemyAction.LineAttack(myPos, dir[0], dir[1], chargePath.size(), chargeDmg));
                    if (isPhaseTwo()) {
                        // Fire trail on charge path
                        actions.add(new EnemyAction.CreateTerrain(chargePath, TileType.FIRE, 1));
                    }
                    EnemyAction resolved = actions.size() == 1 ? actions.get(0)
                        : new EnemyAction.CompositeAction(actions);

                    pendingWarning = new BossWarning(
                        self.getEntityId(), BossWarning.WarningType.TILE_HIGHLIGHT,
                        chargePath, 1, resolved, 0xFFFF4444);
                    return new EnemyAction.Idle();
                }
            }
        }

        // Priority 4: Shield Bash if player is adjacent
        if (dist <= 1) {
            int bashDmg = Math.max(1, self.getAttackPower() / 2);
            // Determine knockback direction: away from boss
            int kdx = Integer.signum(playerPos.x() - myPos.x());
            int kdz = Integer.signum(playerPos.z() - myPos.z());
            if (kdx == 0 && kdz == 0) kdx = 1; // fallback
            return new EnemyAction.ForcedMovement(-1, kdx, kdz, 2);
        }

        // Priority 5: Melee attack or approach
        return meleeOrApproach(self, arena, playerPos, isPhaseTwo() ? 2 : 0);
    }
}
