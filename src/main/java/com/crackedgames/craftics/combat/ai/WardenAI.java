package com.crackedgames.craftics.combat.ai;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.Pathfinding;
import com.crackedgames.craftics.combat.ai.boss.BossAI;
import com.crackedgames.craftics.combat.ai.boss.BossWarning;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;
import com.crackedgames.craftics.core.TileType;

import java.util.ArrayList;
import java.util.List;

/**
 * Deep Dark Boss — "The Warden" (Enhanced with Vibration Sense)
 * Entity: Warden | 50HP / 8ATK / 4DEF / Speed 3 | Size 2×2
 *
 * Core Mechanic — Vibration Sense:
 * - Blind — hunts by vibration, not sight.
 * - Projectile distraction: player throws at empty tile → Warden chases that tile.
 * - Movement 3+ tiles in a turn causes lock-on (overrides distraction).
 * - Phase 2: distraction lasts 1 turn only, movement threshold drops to 2+.
 *
 * Abilities:
 * - Sculk Spread: tiles attacked become sculk (+1 dmg per adjacent sculk)
 * - Sculk Shrieker: trap tile, triggers on proximity, 3 damage + vibration override
 * - Darkness Pulse: all tiles go dark for 1 turn, Warden gets +2 speed
 * - Tremor Stomp: when confused, stomp sends tremors in 4 cardinal directions
 * - (Existing) Melee: devastating close-range attack
 * - (Existing) Sonic Boom (Phase 2): range 4, ignores LOS
 *
 * Phase 2 — "The Ancient Awakens":
 * - +3 bonus damage, Sonic Boom unlocked
 * - Sculk regen: 1 HP/turn per sculk tile on field
 * - Darkness Pulse every 2 turns instead of 4
 * - 2 Sculk Shriekers placed automatically
 * - Distraction only lasts 1 turn
 * - Movement threshold drops to 2+ tiles
 * - Tremor Stomp always fires (no 50% chance), range 4
 */
public class WardenAI extends BossAI {
    private static final String CD_DARKNESS = "darkness_pulse";
    private static final String CD_SHRIEKER = "sculk_shrieker";
    private static final String CD_STOMP = "tremor_stomp";

    // Vibration tracking
    private GridPos vibrationTarget = null;
    private boolean isConfused = false;
    private boolean distractionActive = false;
    private int distractionTurnsLeft = 0;
    private final List<GridPos> sculkTiles = new ArrayList<>();
    private final List<GridPos> shriekerTiles = new ArrayList<>();

    /**
     * Called by CombatManager when the player throws a projectile at an empty tile.
     * Sets the vibration target for the Warden to chase.
     */
    public void onProjectileDistraction(GridPos target) {
        vibrationTarget = target;
        distractionActive = true;
        isConfused = false;
        distractionTurnsLeft = isPhaseTwo() ? 1 : 99; // P2: only 1 turn
    }

    /**
     * Called by CombatManager when the player moves.
     * If movement exceeds threshold, Warden locks on to player.
     */
    public void onPlayerMove(GridPos playerPos, int tilesMoved) {
        int threshold = isPhaseTwo() ? 2 : 3;
        if (tilesMoved >= threshold) {
            vibrationTarget = playerPos;
            distractionActive = false;
            isConfused = false;
        }
    }

    /**
     * Called by CombatManager when a shrieker is triggered.
     * Overrides projectile distraction.
     */
    public void onShriekerTriggered(GridPos shriekerPos) {
        vibrationTarget = shriekerPos;
        distractionActive = false;
        isConfused = false;
    }

    @Override
    protected void onPhaseTransition(CombatEntity self, GridArena arena, GridPos playerPos) {
        self.setEnraged(true);
        // Place 2 sculk shriekers automatically
        List<GridPos> positions = findSummonPositions(arena, 2);
        for (GridPos pos : positions) {
            shriekerTiles.add(pos);
        }
    }

    @Override
    protected EnemyAction chooseAbility(CombatEntity self, GridArena arena, GridPos playerPos) {
        GridPos myPos = self.getGridPos();
        int bonusDamage = isPhaseTwo() ? 3 : 0;

        // Tick distraction timer
        if (distractionActive) {
            distractionTurnsLeft--;
            if (distractionTurnsLeft <= 0) {
                distractionActive = false;
                vibrationTarget = null;
            }
        }

        // Phase 2: sculk regen
        if (isPhaseTwo() && !sculkTiles.isEmpty()) {
            self.heal(sculkTiles.size());
        }

        // Determine effective target (vibration or player)
        GridPos effectiveTarget = (vibrationTarget != null) ? vibrationTarget : playerPos;

        // If confused (reached distraction tile, found nothing) — Tremor Stomp or idle
        if (isConfused) {
            isConfused = false;
            boolean doStomp = isPhaseTwo() || Math.random() < 0.5;
            if (doStomp) {
                int range = isPhaseTwo() ? 4 : 3;
                List<GridPos> stompTiles = getCrossTiles(arena, myPos, range);
                return new EnemyAction.BossAbility("tremor_stomp",
                    new EnemyAction.AreaAttack(myPos, range, 3, "tremor_stomp"),
                    stompTiles);
            }
            return new EnemyAction.Idle();
        }

        // If chasing distraction target and we've arrived — become confused
        if (distractionActive && vibrationTarget != null) {
            int distToTarget = myPos.manhattanDistance(vibrationTarget);
            if (distToTarget <= 1) {
                isConfused = true;
                distractionActive = false;
                vibrationTarget = null;
                return new EnemyAction.Idle();
            }
        }

        int distToTarget = self.minDistanceTo(effectiveTarget);
        int distToPlayer = self.minDistanceTo(playerPos);

        // Darkness Pulse — AoE around the player that blinds them. Telegraphed so the
        // player sees it coming and can reposition out of the warning tiles.
        int darknessCooldown = isPhaseTwo() ? 2 : 4;
        if (!isOnCooldown(CD_DARKNESS)) {
            setCooldown(CD_DARKNESS, darknessCooldown);
            int pulseRadius = isPhaseTwo() ? 3 : 2;
            List<GridPos> pulseTiles = getAreaTiles(arena, playerPos, pulseRadius);
            return new EnemyAction.BossAbility("darkness_pulse",
                new EnemyAction.AreaAttack(playerPos, pulseRadius, 2, "darkness_pulse"),
                pulseTiles);
        }

        // Place Sculk Shrieker
        if (!isOnCooldown(CD_SHRIEKER) && shriekerTiles.size() < (isPhaseTwo() ? 4 : 2)) {
            setCooldown(CD_SHRIEKER, 3);
            GridPos shriekerPos = findSummonPositions(arena, 1).isEmpty() ? null :
                findSummonPositions(arena, 1).get(0);
            if (shriekerPos != null) {
                shriekerTiles.add(shriekerPos);
                return new EnemyAction.CreateTerrain(List.of(shriekerPos), TileType.OBSTACLE, 0);
            }
        }

        // Adjacent to effective target — melee attack
        if (distToPlayer <= 1) {
            // Mark attacked tile as sculk
            sculkTiles.add(myPos);
            int sculkBonus = countAdjacentSculk(myPos);
            return new EnemyAction.Attack(self.getAttackPower() + bonusDamage + sculkBonus);
        }

        // Sonic Boom (Phase 2) — target highest-priority vibration source
        if (isPhaseTwo() && distToTarget <= 4) {
            return new EnemyAction.RangedAttack(self.getAttackPower() + bonusDamage, "sonic_boom");
        }

        // Rush toward effective target
        GridPos moveTarget = AIUtils.findBestAdjacentTarget(arena, myPos, effectiveTarget, self.getMoveSpeed());
        if (moveTarget == null) moveTarget = effectiveTarget;

        List<GridPos> path = Pathfinding.findPath(arena, myPos, moveTarget, self.getMoveSpeed(), self);
        if (path.isEmpty()) return AIUtils.seekOrWander(self, arena, playerPos);

        GridPos endPos = path.get(path.size() - 1);
        if (endPos.manhattanDistance(playerPos) <= 1) {
            sculkTiles.add(endPos);
            int sculkBonus = countAdjacentSculk(endPos);
            return new EnemyAction.MoveAndAttack(path, self.getAttackPower() + bonusDamage + sculkBonus);
        }
        return new EnemyAction.Move(path);
    }

    private int countAdjacentSculk(GridPos pos) {
        int count = 0;
        for (int[] d : new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) {
            GridPos adj = new GridPos(pos.x() + d[0], pos.z() + d[1]);
            if (sculkTiles.contains(adj)) count++;
        }
        return count;
    }

    public List<GridPos> getSculkTiles() { return sculkTiles; }
    public List<GridPos> getShriekerTiles() { return shriekerTiles; }
    public boolean isWardenConfused() { return isConfused; }
    public GridPos getVibrationTarget() { return vibrationTarget; }
}
