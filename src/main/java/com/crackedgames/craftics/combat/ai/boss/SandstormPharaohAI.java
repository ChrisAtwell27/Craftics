package com.crackedgames.craftics.combat.ai.boss;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.ai.EnemyAction;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Scorching Desert Boss — "The Sandstorm Pharaoh"
 * Entity: Husk | 25HP / 6ATK / 1DEF / Speed 2 | Size 2×2
 *
 * Abilities:
 * - Plant Mine: Invisible mine on a tile, 6 dmg on contact. Max 4 active.
 * - Sand Burial: 2×2 quicksand, stun 1 turn. P2: 3×3.
 * - Sandstorm: 3×3 AoE, 3 dmg, -1 accuracy 2 turns.
 * - Curse of the Sands: Mark player — tiles moved off become quicksand. 3 turns.
 *
 * Phase 2 — "Tomb Wrath": 2 mines/turn, 3×3 burial, summon 2 Husks (once).
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

        // Curse of the Sands — apply to player every 6 turns
        if (!isOnCooldown(CD_CURSE) && dist <= 4) {
            setCooldown(CD_CURSE, 6);
            // This is effectively a debuff applied as an AreaAttack with a special effect name
            return new EnemyAction.AreaAttack(playerPos, 0, 0, "curse_of_sands");
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
            List<GridPos> mines = candidates.subList(0, Math.min(mineCount, candidates.size()));
            if (!mines.isEmpty()) {
                setCooldown(CD_MINE, 2);
                activeMines += mines.size();
                // Mines are placed as a special AreaAttack with minimal warning
                return new EnemyAction.AreaAttack(mines.get(0), 0, 0, "plant_mine");
            }
        }

        // Sandstorm AoE
        if (!isOnCooldown(CD_STORM) && dist <= 5) {
            List<GridPos> stormTiles = getAreaTiles(arena, playerPos, 1);
            setCooldown(CD_STORM, 3);
            EnemyAction storm = new EnemyAction.AreaAttack(playerPos, 1, 3, "sandstorm");
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
