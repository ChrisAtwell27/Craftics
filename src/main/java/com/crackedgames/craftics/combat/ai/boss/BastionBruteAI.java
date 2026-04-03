package com.crackedgames.craftics.combat.ai.boss;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.ai.EnemyAction;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;
import com.crackedgames.craftics.core.TileType;

import java.util.List;

/**
 * Crimson Forest Boss — "The Bastion Brute" (Skeleton warlord)
 * Entity: Skeleton | 45HP / 8ATK / 3DEF / Speed 3 | Size 1×1
 *
 * Abilities:
 * - Gore Charge: 4-tile line charge, ATK+3, knockback 3. Destroys obstacles. P2: fire trail.
 * - Fungal Growth: 3×3 crimson nylium. Boss heals 2HP/turn on it. 3 turns.
 * - Rampage: All adjacent tiles (8), ATK damage each. P2: 2-tile radius.
 * - Summon Pack: 2 Piglins (8HP/4ATK/Range 3). Once per fight.
 *
 * Phase 2 — "Blood Frenzy": +4 ATK, fire trail on charge, rampage 2-tile radius,
 * no knockback, speed 4.
 */
public class BastionBruteAI extends BossAI {
    private static final String CD_CHARGE = "gore_charge";
    private static final String CD_FUNGAL = "fungal_growth";
    private static final String CD_RAMPAGE = "rampage";
    private boolean summonedPack = false;

    @Override
    protected void onPhaseTransition(CombatEntity self, GridArena arena, GridPos playerPos) {
        self.setEnraged(true);
        self.setSpeedBonus(1); // Speed 3 → 4
    }

    @Override
    protected EnemyAction chooseAbility(CombatEntity self, GridArena arena, GridPos playerPos) {
        GridPos myPos = self.getGridPos();
        int dist = self.minDistanceTo(playerPos);
        int atk = self.getAttackPower() + (isPhaseTwo() ? 4 : 0);

        // Summon Pack — once per fight
        if (!summonedPack && getAliveMinionCount() == 0 && dist >= 3) {
            summonedPack = true;
            List<GridPos> spawnPositions = findSummonPositions(arena, 2);
            if (!spawnPositions.isEmpty()) {
                return new EnemyAction.SummonMinions(
                    "minecraft:piglin", spawnPositions.size(), spawnPositions, 8, 4, 0);
            }
        }

        // Gore Charge — line charge when at range
        if (!isOnCooldown(CD_CHARGE) && dist >= 2 && dist <= 5) {
            setCooldown(CD_CHARGE, 2);
            int[] dir = getDirectionToward(myPos, playerPos);
            List<GridPos> chargePath = getLineTiles(arena, myPos, dir[0], dir[1], 4);
            EnemyAction chargeAction;
            if (isPhaseTwo()) {
                // Gore Charge + fire trail in Phase 2
                chargeAction = new EnemyAction.CompositeAction(List.of(
                    new EnemyAction.LineAttack(myPos, dir[0], dir[1], 4, atk + 3),
                    new EnemyAction.CreateTerrain(chargePath, TileType.FIRE, 2)
                ));
            } else {
                chargeAction = new EnemyAction.LineAttack(myPos, dir[0], dir[1], 4, atk + 3);
            }
            pendingWarning = new BossWarning(
                self.getEntityId(), BossWarning.WarningType.TILE_HIGHLIGHT,
                chargePath, 1, chargeAction, 0xFFCC0000);
            return advanceWhileCharging(self, arena, playerPos);
        }

        // Rampage — if adjacent or close
        if (!isOnCooldown(CD_RAMPAGE) && dist <= (isPhaseTwo() ? 2 : 1)) {
            setCooldown(CD_RAMPAGE, 2);
            int radius = isPhaseTwo() ? 2 : 1;
            List<GridPos> rampageTiles = getAreaTiles(arena, myPos, radius);
            return new EnemyAction.AreaAttack(myPos, radius, atk, "rampage");
        }

        // Fungal Growth — create healing terrain
        if (!isOnCooldown(CD_FUNGAL)) {
            setCooldown(CD_FUNGAL, 3);
            List<GridPos> fungalTiles = getAreaTiles(arena, myPos, 1);
            return new EnemyAction.BossAbility("fungal_growth",
                new EnemyAction.CreateTerrain(fungalTiles, TileType.NORMAL, 3),
                fungalTiles);
        }

        // Melee attack if adjacent
        if (dist <= 1) {
            if (isPhaseTwo()) {
                return new EnemyAction.Attack(atk);
            }
            return new EnemyAction.AttackWithKnockback(atk, 3);
        }

        return meleeOrApproach(self, arena, playerPos, 0);
    }
}
