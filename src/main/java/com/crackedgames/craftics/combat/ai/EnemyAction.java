package com.crackedgames.craftics.combat.ai;

import com.crackedgames.craftics.combat.CombatEffects;
import com.crackedgames.craftics.core.GridPos;
import com.crackedgames.craftics.core.TileType;

import java.util.List;

public sealed interface EnemyAction {

    /**
     * A single status effect applied to the player when they are caught in an
     * enemy {@link Explode} blast. {@code CombatManager}'s Explode handler
     * routes each entry through {@code addEffectHooked} so addon immunities
     * still intercept.
     */
    record BlastEffect(CombatEffects.EffectType effect, int turns, int amplifier) {
        /** Convenience: amplifier 0 (level I). */
        public BlastEffect(CombatEffects.EffectType effect, int turns) {
            this(effect, turns, 0);
        }
    }

    record Move(List<GridPos> path) implements EnemyAction {}
    record Attack(int damage) implements EnemyAction {}
    record MoveAndAttack(List<GridPos> path, int damage) implements EnemyAction {}
    /** Move, attack, then reposition using remaining movement in the same turn. */
    record MoveAttackMove(List<GridPos> approachPath, int damage, List<GridPos> retreatPath) implements EnemyAction {}
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
    /**
     * Creeper / bomb-mob AoE explosion centered on self. Damages all entities
     * in the blast radius and optionally applies a list of status effects to
     * the player on hit (e.g. BLINDNESS for cave_creeper, SLOWNESS for
     * snowy_creeper). {@code blastEffects} may be empty for vanilla creepers.
     */
    record Explode(int damage, int radius, List<BlastEffect> blastEffects) implements EnemyAction {
        /** Vanilla/bomb explosion with no post-blast status effects. */
        public Explode(int damage, int radius) {
            this(damage, radius, List.of());
        }
    }
    /** Witch/ranged: attack from distance without moving (potion throw, etc.) */
    record RangedAttack(int damage, String effectName) implements EnemyAction {}
    /** Phantom: swoop in a line, deal damage to player if in path, end at destination */
    record Swoop(List<GridPos> path, int damage) implements EnemyAction {}
    /**
     * Mimic dash: charge along {@code dirX/dirZ} from the mimic's current tile until
     * the path is blocked by a wall or obstacle. Players/allies in the path are
     * shoved sideways and take {@code damage}. The mimic ends on the last walkable
     * tile of the dash. Used by the Artifacts compat mimic encounter.
     */
    record MimicDash(int dirX, int dirZ, int damage) implements EnemyAction {}
    /**
     * Mimic tantrum: a single compound attack where the mimic hops between an
     * ordered list of grid tiles over multiple animation frames. If any hop
     * lands on the player's tile, the player takes {@code damage}, the mimic
     * stops on the previous (safe) tile, and the rest of the list is discarded.
     * <p>
     * The hop list is guaranteed to form a valid walk — each tile is adjacent
     * (cardinal or diagonal) to the previous one. Used by the Artifacts compat
     * mimic encounter.
     */
    record MimicTantrum(List<GridPos> path, int damage) implements EnemyAction {}
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

    /** Spider ceiling ascend: mob shoots web upward, rises off the grid for 1 turn. */
    record CeilingAscend() implements EnemyAction {}

    /** Spider ceiling drop: mob drops from ceiling onto a tile near the player, then attacks. */
    record CeilingDrop(GridPos landingPos, int damage) implements EnemyAction {}

    /** Multi-action: execute multiple actions in sequence (e.g., teleport + attack + create terrain). */
    record CompositeAction(List<EnemyAction> actions) implements EnemyAction {}

    // === Projectile action types ===

    /** Boss spawns projectile entities that travel in a straight line.
     *  Each position is paired with a direction (dx, dz) from the directions list. */
    record SpawnProjectile(String entityTypeId, List<GridPos> positions, List<int[]> directions,
                           int hp, int atk, int def, String projectileType) implements EnemyAction {}

    /** Projectile entity moves in its set direction; impacts=true means it hits wall/player/enemy at path end.
     *  impactPos is the center of the explosion/effect (null if no impact). */
    record ProjectileMove(List<GridPos> path, boolean impacts, GridPos impactPos) implements EnemyAction {}
}
