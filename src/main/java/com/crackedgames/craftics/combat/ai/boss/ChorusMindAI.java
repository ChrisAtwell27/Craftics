package com.crackedgames.craftics.combat.ai.boss;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.ai.EnemyAction;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;
import com.crackedgames.craftics.core.TileType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Chorus Grove Boss — "The Chorus Mind" (Enderman)
 * Entity: Enderman | 60HP / 12ATK / 3DEF / Speed 2 (+ chorus teleport) | Size 2×2
 *
 * Abilities:
 * - Chorus Bloom: Grow chorus plant obstacles on 4-5 tiles. Boss teleports to any plant as free action.
 *   Plants: 4HP, destructible. P2: plants auto-spread 1/turn.
 * - Entangle: 2×2 (P2: 3×3) root area. Immobilize 1 turn + 4 dmg.
 * - Chorus Bomb: Range 4, 2×2 AoE 5 dmg + random teleport on hit. P2: teleport toward boss.
 * - Resonance Cascade: Every plant pulses 3 dmg to all adjacent tiles. More plants = more damage.
 *   P2: auto every 2 turns.
 *
 * Phase 2 — "Overgrowth": Auto-spread plants, auto resonance every 2 turns,
 * entangle 3×3, boss teleports to random plant every turn start, chorus bomb pulls toward boss.
 */
public class ChorusMindAI extends BossAI {
    private static final String CD_BLOOM = "chorus_bloom";
    private static final String CD_ENTANGLE = "entangle";
    private static final String CD_BOMB = "chorus_bomb";
    private static final String CD_CASCADE = "resonance_cascade";
    private final List<GridPos> chorusPlants = new ArrayList<>();

    @Override
    protected void onPhaseTransition(CombatEntity self, GridArena arena, GridPos playerPos) {
        self.setEnraged(true);
        // Auto-spread begins — handled in chooseAbility
    }

    @Override
    protected EnemyAction chooseAbility(CombatEntity self, GridArena arena, GridPos playerPos) {
        GridPos myPos = self.getGridPos();
        int dist = self.minDistanceTo(playerPos);

        // Phase 2: Auto-spread plants
        if (isPhaseTwo()) {
            List<GridPos> newPlants = new ArrayList<>();
            for (GridPos plant : new ArrayList<>(chorusPlants)) {
                for (int[] d : new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) {
                    GridPos adj = new GridPos(plant.x() + d[0], plant.z() + d[1]);
                    if (arena.isInBounds(adj) && !arena.isOccupied(adj)
                            && !chorusPlants.contains(adj) && !newPlants.contains(adj)
                            && arena.getTile(adj) != null && arena.getTile(adj).isWalkable()) {
                        newPlants.add(adj);
                        break; // Only 1 spread per plant per turn
                    }
                }
            }
            if (!newPlants.isEmpty()) {
                // Limit auto-growth
                if (newPlants.size() > 3) {
                    Collections.shuffle(newPlants);
                    newPlants = newPlants.subList(0, 3);
                }
                chorusPlants.addAll(newPlants);
            }
        }

        // Phase 2: Teleport to random plant at turn start
        if (isPhaseTwo() && !chorusPlants.isEmpty()) {
            Collections.shuffle(chorusPlants);
            for (GridPos plant : chorusPlants) {
                if (!arena.isOccupied(plant)) {
                    // Teleport then pick ability
                    EnemyAction ability = chooseOffensiveAbility(self, arena, playerPos);
                    return new EnemyAction.CompositeAction(List.of(
                        new EnemyAction.Teleport(plant),
                        ability != null ? ability : new EnemyAction.Idle()
                    ));
                }
            }
        }

        EnemyAction action = chooseOffensiveAbility(self, arena, playerPos);
        return action != null ? action : meleeOrApproach(self, arena, playerPos, 0);
    }

    private EnemyAction chooseOffensiveAbility(CombatEntity self, GridArena arena, GridPos playerPos) {
        GridPos myPos = self.getGridPos();
        int dist = myPos.manhattanDistance(playerPos);

        // Resonance Cascade — needs plants on field
        boolean autoCascade = isPhaseTwo() && getTurnCounter() % 2 == 0;
        if ((!isOnCooldown(CD_CASCADE) || autoCascade) && chorusPlants.size() >= 3) {
            setCooldown(CD_CASCADE, 3);
            List<GridPos> cascadeTiles = new ArrayList<>();
            for (GridPos plant : chorusPlants) {
                for (int[] d : new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) {
                    GridPos adj = new GridPos(plant.x() + d[0], plant.z() + d[1]);
                    if (arena.isInBounds(adj) && !cascadeTiles.contains(adj)) {
                        cascadeTiles.add(adj);
                    }
                }
            }
            pendingWarning = new BossWarning(
                self.getEntityId(), BossWarning.WarningType.ENTITY_GLOW,
                cascadeTiles, 1,
                new EnemyAction.AreaAttack(myPos, 0, 3, "resonance_cascade"),
                0xFFFF00FF);
            return new EnemyAction.Idle();
        }

        // Chorus Bloom — plant more chorus
        if (!isOnCooldown(CD_BLOOM)) {
            setCooldown(CD_BLOOM, 3);
            int count = isPhaseTwo() ? 5 : 4;
            List<GridPos> bloomTiles = findSummonPositions(arena, count);
            chorusPlants.addAll(bloomTiles);
            return new EnemyAction.CreateTerrain(bloomTiles, TileType.OBSTACLE, 0);
        }

        // Entangle — root area
        if (!isOnCooldown(CD_ENTANGLE) && dist <= 4) {
            setCooldown(CD_ENTANGLE, 3);
            int radius = isPhaseTwo() ? 1 : 0; // 3×3 or 2×2
            List<GridPos> entangleTiles;
            if (radius == 0) {
                // 2×2 centered on player
                entangleTiles = new ArrayList<>();
                entangleTiles.add(playerPos);
                entangleTiles.add(new GridPos(playerPos.x() + 1, playerPos.z()));
                entangleTiles.add(new GridPos(playerPos.x(), playerPos.z() + 1));
                entangleTiles.add(new GridPos(playerPos.x() + 1, playerPos.z() + 1));
                entangleTiles.removeIf(p -> !arena.isInBounds(p));
            } else {
                entangleTiles = getAreaTiles(arena, playerPos, radius);
            }
            pendingWarning = new BossWarning(
                self.getEntityId(), BossWarning.WarningType.GROUND_CRACK,
                entangleTiles, 1,
                new EnemyAction.AreaAttack(playerPos, radius, 4, "entangle"),
                0xFF44CC44);
            return new EnemyAction.Idle();
        }

        // Chorus Bomb — ranged AoE
        if (!isOnCooldown(CD_BOMB) && dist <= 4 && dist >= 2) {
            setCooldown(CD_BOMB, 2);
            String effect = isPhaseTwo() ? "chorus_bomb_pull" : "chorus_bomb";
            return new EnemyAction.AreaAttack(playerPos, 1, 5, effect);
        }

        // Melee if adjacent
        if (dist <= 1) {
            return new EnemyAction.Attack(self.getAttackPower());
        }

        return null;
    }

    public void removePlant(GridPos pos) { chorusPlants.remove(pos); }
    public List<GridPos> getChorusPlants() { return chorusPlants; }
}
