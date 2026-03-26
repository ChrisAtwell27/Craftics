package com.crackedgames.craftics.combat.ai.boss;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.ai.EnemyAction;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;
import com.crackedgames.craftics.core.TileType;

import java.util.ArrayList;
import java.util.List;

/**
 * Nether Wastes Boss — "The Molten King" (Magma Cube)
 * Entity: Magma Cube | 35HP / 7ATK / 2DEF / Speed 2 | Size 2×2
 *
 * Abilities:
 * - Magma Eruption: Leap to tile, 3×3 AoE 5 dmg + ring of fire tiles
 * - Split: Reactive — splits when taking 8+ damage in one hit
 * - Lava Trail: Passive — movement leaves fire tiles (2 turns, permanent P2)
 * - Absorb: Merges with adjacent small/medium cube, heals for its HP
 *
 * Phase 2 — "Meltdown": Permanent fire tiles, permanent eruption rings,
 * death explosion if not split, absorb range 2.
 */
public class MoltenKingAI extends BossAI {
    private static final String CD_ERUPTION = "magma_eruption";
    private static final String CD_ABSORB = "absorb";
    private boolean hasSplit = false;

    @Override
    protected void onPhaseTransition(CombatEntity self, GridArena arena, GridPos playerPos) {
        self.setEnraged(true);
        // Lava trail tiles become permanent in Phase 2 — handled by CombatManager
    }

    @Override
    protected EnemyAction chooseAbility(CombatEntity self, GridArena arena, GridPos playerPos) {
        GridPos myPos = self.getGridPos();
        int dist = self.minDistanceTo(playerPos);

        // Absorb a nearby minion to heal
        if (!isOnCooldown(CD_ABSORB) && getAliveMinionCount() > 0) {
            int absorbeRange = isPhaseTwo() ? 2 : 1;
            for (CombatEntity e : arena.getOccupants().values()) {
                if (summonedMinionIds.contains(e.getEntityId()) && e.isAlive()) {
                    int mDist = myPos.manhattanDistance(e.getGridPos());
                    if (mDist <= absorbeRange) {
                        setCooldown(CD_ABSORB, 2);
                        // Absorb = heal for minion's HP, kill the minion
                        return new EnemyAction.BossAbility("absorb",
                            new EnemyAction.ModifySelf("hp", e.getCurrentHp(), 0),
                            List.of(e.getGridPos()));
                    }
                }
            }
        }

        // Magma Eruption — leap to tile near player
        if (!isOnCooldown(CD_ERUPTION) && dist >= 2) {
            setCooldown(CD_ERUPTION, 3);
            GridPos landingPos = playerPos; // Target near player
            List<GridPos> aoeArea = getAreaTiles(arena, landingPos, 1);
            // Fire ring = tiles at radius 2 (outer ring of 5×5 minus inner 3×3)
            List<GridPos> fireRing = new ArrayList<>();
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (Math.abs(dx) <= 1 && Math.abs(dz) <= 1) continue;
                    GridPos p = new GridPos(landingPos.x() + dx, landingPos.z() + dz);
                    if (arena.isInBounds(p)) fireRing.add(p);
                }
            }
            int fireDuration = isPhaseTwo() ? 0 : 2;
            EnemyAction eruption = new EnemyAction.CompositeAction(List.of(
                new EnemyAction.AreaAttack(landingPos, 1, 5, "magma_eruption"),
                new EnemyAction.CreateTerrain(fireRing, TileType.FIRE, fireDuration)
            ));
            pendingWarning = new BossWarning(
                self.getEntityId(), BossWarning.WarningType.TILE_HIGHLIGHT,
                aoeArea, 1, eruption, 0xFFFF4400);
            return new EnemyAction.Idle();
        }

        // Lava Trail — passive on movement (CombatManager converts move tiles to fire)
        // Melee attack if adjacent
        if (dist <= 1) {
            return new EnemyAction.Attack(self.getAttackPower());
        }

        return meleeOrApproach(self, arena, playerPos, 0);
    }

    /**
     * Called by CombatManager when the boss takes 8+ damage in a single hit.
     * Returns the split action for spawning medium cubes.
     */
    public EnemyAction reactToHeavyDamage(CombatEntity self, GridArena arena) {
        if (!hasSplit) {
            hasSplit = true;
            List<GridPos> splitPositions = findSummonPositionsNear(arena, self.getGridPos(), 2, 2);
            if (!splitPositions.isEmpty()) {
                return new EnemyAction.SummonMinions(
                    "minecraft:magma_cube", splitPositions.size(), splitPositions, 15, 4, 0);
            }
        }
        return null;
    }

    public boolean hasSplit() { return hasSplit; }
}
