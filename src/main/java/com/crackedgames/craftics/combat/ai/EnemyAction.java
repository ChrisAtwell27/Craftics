package com.crackedgames.craftics.combat.ai;

import com.crackedgames.craftics.core.GridPos;
import com.crackedgames.craftics.core.TileType;

import java.util.List;

public sealed interface EnemyAction {
    record Move(List<GridPos> path) implements EnemyAction {}
    record Attack(int damage) implements EnemyAction {}
    record MoveAndAttack(List<GridPos> path, int damage) implements EnemyAction {}
    record Flee(List<GridPos> path) implements EnemyAction {}
    record StartFuse() implements EnemyAction {}
    record Detonate() implements EnemyAction {}
    record Idle() implements EnemyAction {}

    /** Enderman: instant teleport to a tile (no lerp animation) */
    record Teleport(GridPos target) implements EnemyAction {}
    /** Enderman: teleport then immediately attack */
    record TeleportAndAttack(GridPos target, int damage) implements EnemyAction {}
    /** Spider: pounce over 1 tile gap to land adjacent and attack */
    record Pounce(GridPos landingPos, int damage) implements EnemyAction {}
    /** Creeper: AoE explosion centered on self, damages all in radius */
    record Explode(int damage, int radius) implements EnemyAction {}
    /** Witch/ranged: attack from distance without moving (potion throw, etc.) */
    record RangedAttack(int damage, String effectName) implements EnemyAction {}
    /** Phantom: swoop in a line, deal damage to player if in path, end at destination */
    record Swoop(List<GridPos> path, int damage) implements EnemyAction {}
    /** Attack another mob (predator hunting prey). Target is entity ID. */
    record AttackMob(int targetEntityId, int damage) implements EnemyAction {}
    /** Move then attack another mob. */
    record MoveAndAttackMob(List<GridPos> path, int targetEntityId, int damage) implements EnemyAction {}
    /** Attack player and knock them back N tiles away from attacker. */
    record AttackWithKnockback(int damage, int knockbackTiles) implements EnemyAction {}
    /** Move then attack player with knockback. */
    record MoveAndAttackWithKnockback(List<GridPos> path, int damage, int knockbackTiles) implements EnemyAction {}

    // === Boss-specific action types ===

    /** Boss summons minions onto the arena. */
    record SummonMinions(String entityTypeId, int count, List<GridPos> positions,
                         int hp, int atk, int def) implements EnemyAction {}

    /** AoE attack centered on a tile affecting all entities within radius. */
    record AreaAttack(GridPos center, int radius, int damage, String effectName) implements EnemyAction {}

    /** Create or transform terrain tiles for a duration (0 = permanent). */
    record CreateTerrain(List<GridPos> tiles, TileType terrainType, int duration) implements EnemyAction {}

    /** Line attack from a start point in a direction, hitting all tiles in the line. */
    record LineAttack(GridPos start, int dx, int dz, int length, int damage) implements EnemyAction {}

    /** Boss modifies its own stats temporarily. duration 0 = permanent. */
    record ModifySelf(String stat, int amount, int duration) implements EnemyAction {}

    /** Force-move a target entity in a direction. targetEntityId -1 = player. */
    record ForcedMovement(int targetEntityId, int dx, int dz, int tiles) implements EnemyAction {}

    /** Boss telegraphs a warning — the actual ability resolves next turn.
     *  The warningId is used to match the warning to its resolution in BossWarning. */
    record BossAbility(String abilityName, EnemyAction resolvedAction,
                       List<GridPos> warningTiles) implements EnemyAction {}

    /** Multi-action: execute multiple actions in sequence (e.g., teleport + attack + create terrain). */
    record CompositeAction(List<EnemyAction> actions) implements EnemyAction {}
}
