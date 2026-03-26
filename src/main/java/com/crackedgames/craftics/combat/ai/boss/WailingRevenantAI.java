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
 * Soul Sand Valley Boss — "The Wailing Revenant" (Ghast)
 * Entity: Ghast | 40HP / 8ATK / 1DEF / Range 6 / Speed 1 (teleports) | Size 2×2
 *
 * Abilities:
 * - Soul Barrage: 3 (P2: 5) soul fireballs at different tiles. 4 dmg + soul fire.
 * - Wail of Despair: AoE debuff: -2 ATK within 4 tiles, 2 turns.
 * - Soul Chain: Tethers player, 2 dmg/turn. Breaks at 5+ tiles from anchor.
 * - Phase Shift: Teleport to random corner/edge when player within 3 tiles.
 *
 * Phase 2 — "Requiem": 5 fireballs, wail + slowness, 2 chains, permanent fire, speed 2.
 */
public class WailingRevenantAI extends BossAI {
    private static final String CD_BARRAGE = "soul_barrage";
    private static final String CD_WAIL = "wail_despair";
    private static final String CD_CHAIN = "soul_chain";
    private static final String CD_SHIFT = "phase_shift";
    private int activeChains = 0;

    @Override
    protected void onPhaseTransition(CombatEntity self, GridArena arena, GridPos playerPos) {
        self.setEnraged(true);
        self.setSpeedBonus(1); // Speed 1 → 2
    }

    @Override
    protected EnemyAction chooseAbility(CombatEntity self, GridArena arena, GridPos playerPos) {
        GridPos myPos = self.getGridPos();
        int dist = self.minDistanceTo(playerPos);

        // Phase Shift — escape when player gets close
        if (!isOnCooldown(CD_SHIFT) && dist <= 3) {
            setCooldown(CD_SHIFT, 1);
            GridPos escapeTo = findCornerOrEdge(arena, playerPos);
            if (escapeTo != null) {
                return new EnemyAction.Teleport(escapeTo);
            }
        }

        // Soul Chain — tether the player
        int maxChains = isPhaseTwo() ? 2 : 1;
        if (!isOnCooldown(CD_CHAIN) && activeChains < maxChains && dist <= 6) {
            setCooldown(CD_CHAIN, 4);
            activeChains++;
            return new EnemyAction.BossAbility("soul_chain",
                new EnemyAction.AreaAttack(playerPos, 0, 2, "soul_chain"),
                List.of(playerPos));
        }

        // Soul Barrage — multi-target fireball attack
        if (!isOnCooldown(CD_BARRAGE)) {
            setCooldown(CD_BARRAGE, 2);
            int count = isPhaseTwo() ? 5 : 3;
            List<GridPos> targets = generateBarrageTargets(arena, playerPos, count);
            int fireDuration = isPhaseTwo() ? 0 : 2; // permanent in P2
            List<EnemyAction> actions = new ArrayList<>();
            for (GridPos target : targets) {
                actions.add(new EnemyAction.AreaAttack(target, 0, 4, "soul_fireball"));
            }
            actions.add(new EnemyAction.CreateTerrain(targets, TileType.FIRE, fireDuration));
            pendingWarning = new BossWarning(
                self.getEntityId(), BossWarning.WarningType.TILE_HIGHLIGHT,
                targets, 1,
                new EnemyAction.CompositeAction(actions),
                0xFF4488FF);
            return new EnemyAction.Idle();
        }

        // Wail of Despair — AoE debuff
        if (!isOnCooldown(CD_WAIL) && dist <= 5) {
            setCooldown(CD_WAIL, 3);
            List<GridPos> wailTiles = getAreaTiles(arena, myPos, 4);
            String effect = isPhaseTwo() ? "wail_despair_slow" : "wail_despair";
            return new EnemyAction.AreaAttack(myPos, 4, 0, effect);
        }

        // Standard ranged attack
        if (dist <= 6) {
            return new EnemyAction.RangedAttack(self.getAttackPower(), "soul_fireball");
        }

        return meleeOrApproach(self, arena, playerPos, 0);
    }

    public void onChainBroken() {
        activeChains = Math.max(0, activeChains - 1);
    }

    public int getActiveChains() { return activeChains; }

    private List<GridPos> generateBarrageTargets(GridArena arena, GridPos playerPos, int count) {
        List<GridPos> targets = new ArrayList<>();
        targets.add(playerPos); // One always aimed at player
        // Spread around player
        List<GridPos> nearby = getAreaTiles(arena, playerPos, 2);
        Collections.shuffle(nearby);
        for (GridPos p : nearby) {
            if (targets.size() >= count) break;
            if (!targets.contains(p)) targets.add(p);
        }
        return targets;
    }

    private GridPos findCornerOrEdge(GridArena arena, GridPos awayFrom) {
        int w = arena.getWidth(), h = arena.getHeight();
        List<GridPos> corners = new ArrayList<>(List.of(
            new GridPos(1, 1),
            new GridPos(w - 2, 1),
            new GridPos(1, h - 2),
            new GridPos(w - 2, h - 2)
        ));
        // Pick the corner farthest from the player
        corners.sort((a, b) -> b.manhattanDistance(awayFrom) - a.manhattanDistance(awayFrom));
        for (GridPos c : corners) {
            if (arena.isInBounds(c) && !arena.isOccupied(c)
                    && arena.getTile(c) != null && arena.getTile(c).isWalkable()) {
                return c;
            }
        }
        return null;
    }
}
