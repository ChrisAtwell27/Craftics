package com.crackedgames.craftics.combat.ai.boss;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.ai.EnemyAction;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Scorching Desert Boss - "The Sandstorm Pharaoh"
 * Entity: Husk | 25HP / 6ATK / 1DEF / Speed 2 | Size 2×2
 *
 * Abilities:
 * - Plant Mine: Buried mine on a tile (subtle sand tell), 6 dmg + 1-turn stun on contact. Max 4 active.
 * - Sand Burial: 2×2 quicksand, stun 1 turn. P2: 3×3.
 * - Sandstorm: 5×5 AoE, 3 dmg (P2: 4) + Weakness 2 turns.
 * - Curse of the Sands: telegraphed 3×3 hex, 3 dmg. Cursed players sprout a live
 *   sand mine on every tile they move off for 3 turns (CombatManager state).
 *
 * Phase 2 - "Tomb Wrath": 2 mines/turn, 3×3 burial, summon 2 Husks (once) -
 * and every Sand Burial cast releases 2 SCARABS while the sand gathers: 1-HP
 * homing bug entities (SeekingProjectileAI, 2 tiles/turn) that burrow in for
 * 2 damage + 2 turns of Poison. Squash them before they reach you.
 */
public class SandstormPharaohAI extends BossAI {
    private static final String CD_MINE = "plant_mine";
    private static final String CD_BURIAL = "sand_burial";
    private static final String CD_STORM = "sandstorm";
    private static final String CD_CURSE = "curse_sands";
    private static final int MAX_MINES = 4;
    private int activeMines = 0;
    private boolean guardsSpawned = false;

    @Override
    protected void onPhaseTransition(CombatEntity self, GridArena arena, GridPos playerPos) {
        self.setEnraged(true);
    }

    @Override
    protected EnemyAction chooseAbility(CombatEntity self, GridArena arena, GridPos playerPos) {
        GridPos myPos = self.getGridPos();
        int dist = self.minDistanceTo(playerPos);

        // Phase 2: Summon 2 Husks once
        if (isPhaseTwo() && !guardsSpawned) {
            guardsSpawned = true;
            List<GridPos> positions = findSummonPositions(arena, 2);
            if (!positions.isEmpty()) {
                return new EnemyAction.SummonMinions("minecraft:husk", positions.size(), positions, 8, 3, 0);
            }
        }

        // Curse of the Sands - telegraphed 3x3 hex. Anyone caught is CURSED:
        // CombatManager plants a live sand mine on every tile the player moves
        // off for the next 3 turns (the "curse_of_sands" area effect). Dodging
        // the marked square dodges the curse entirely.
        if (!isOnCooldown(CD_CURSE) && dist <= 5) {
            setCooldown(CD_CURSE, 6);
            List<GridPos> curseTiles = getAreaTiles(arena, playerPos, 1);
            EnemyAction curse = new EnemyAction.AreaAttack(playerPos, 1, 3, "curse_of_sands");
            pendingWarning = new BossWarning(
                self.getEntityId(), BossWarning.WarningType.GATHERING_PARTICLES,
                curseTiles, 1, curse, 0xFF9944CC);
            return advanceWhileCharging(self, arena, playerPos);
        }

        // Plant Mines
        if (!isOnCooldown(CD_MINE) && activeMines < MAX_MINES) {
            int mineCount = isPhaseTwo() ? 2 : 1;
            List<GridPos> candidates = new ArrayList<>();
            // Place mines in player's likely movement paths
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    GridPos pos = new GridPos(playerPos.x() + dx, playerPos.z() + dz);
                    if (arena.isInBounds(pos) && !arena.isOccupied(pos)
                            && arena.getTile(pos) != null && arena.getTile(pos).isWalkable()) {
                        candidates.add(pos);
                    }
                }
            }
            Collections.shuffle(candidates);
            // Respect the active-mine cap so we never plant more than MAX_MINES at once.
            int slots = Math.min(mineCount, MAX_MINES - activeMines);
            List<GridPos> mines = candidates.subList(0, Math.min(slots, candidates.size()));
            if (!mines.isEmpty()) {
                setCooldown(CD_MINE, 2);
                activeMines += mines.size();
                // Each mine is a persistent step-trigger trap registered CombatManager-side
                // from this AreaAttack's "plant_mine" effect. One AreaAttack per tile so all
                // chosen tiles (Phase 2 plants 2) get registered.
                if (mines.size() == 1) {
                    return new EnemyAction.AreaAttack(mines.get(0), 0, 0, "plant_mine");
                }
                List<EnemyAction> mineActions = new ArrayList<>();
                for (GridPos m : mines) {
                    mineActions.add(new EnemyAction.AreaAttack(m, 0, 0, "plant_mine"));
                }
                return new EnemyAction.CompositeAction(mineActions);
            }
        }

        // Sandstorm AoE - a 5x5 wall of stinging sand (weakens on hit)
        if (!isOnCooldown(CD_STORM) && dist <= 5) {
            List<GridPos> stormTiles = getAreaTiles(arena, playerPos, 2);
            setCooldown(CD_STORM, 3);
            EnemyAction storm = new EnemyAction.AreaAttack(playerPos, 2, isPhaseTwo() ? 4 : 3, "sandstorm");
            pendingWarning = new BossWarning(
                self.getEntityId(), BossWarning.WarningType.GATHERING_PARTICLES,
                stormTiles, 1, storm, 0xFFDDAA44);
            return advanceWhileCharging(self, arena, playerPos);
        }

        // Sand Burial
        if (!isOnCooldown(CD_BURIAL) && dist <= 4) {
            int burialRadius = isPhaseTwo() ? 1 : 0; // 3×3 vs 2×2
            List<GridPos> burialTiles;
            if (isPhaseTwo()) {
                burialTiles = getAreaTiles(arena, playerPos, 1);
            } else {
                burialTiles = new ArrayList<>();
                burialTiles.add(playerPos);
                burialTiles.add(new GridPos(playerPos.x() + 1, playerPos.z()));
                burialTiles.add(new GridPos(playerPos.x(), playerPos.z() + 1));
                burialTiles.add(new GridPos(playerPos.x() + 1, playerPos.z() + 1));
                burialTiles.removeIf(p -> !arena.isInBounds(p));
            }
            setCooldown(CD_BURIAL, 3);
            EnemyAction burial = new EnemyAction.AreaAttack(playerPos, burialRadius, 0, "sand_burial");
            pendingWarning = new BossWarning(
                self.getEntityId(), BossWarning.WarningType.TILE_HIGHLIGHT,
                burialTiles, 1, burial, 0xFFCC9933);
            // Phase 2: the gathering sand disgorges 2 homing scarabs on the
            // cast turn (instead of the usual advance) - burial turns apply
            // pressure from two directions at once.
            if (isPhaseTwo()) {
                List<GridPos> scarabTiles = findSummonPositions(arena, 2);
                if (!scarabTiles.isEmpty()) {
                    List<int[]> dirs = new ArrayList<>();
                    for (GridPos t : scarabTiles) {
                        int sdx = Integer.signum(playerPos.x() - t.x());
                        int sdz = Integer.signum(playerPos.z() - t.z());
                        dirs.add(sdx != 0 ? new int[]{sdx, 0} : new int[]{0, sdz != 0 ? sdz : 1});
                    }
                    return new EnemyAction.SpawnProjectile(
                        "minecraft:blaze", scarabTiles, dirs, 1, 2, 0, "scarab");
                }
            }
            return advanceWhileCharging(self, arena, playerPos);
        }

        // Default: melee approach
        return meleeOrApproach(self, arena, playerPos, isPhaseTwo() ? 2 : 0);
    }

    /** Called by CombatManager when a mine is triggered/destroyed. */
    public void notifyMineDestroyed() {
        activeMines = Math.max(0, activeMines - 1);
    }
}
