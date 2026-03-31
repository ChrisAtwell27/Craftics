package com.crackedgames.craftics.combat.ai.boss;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.ai.AIUtils;
import com.crackedgames.craftics.combat.ai.EnemyAI;
import com.crackedgames.craftics.combat.ai.EnemyAction;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;

import java.util.*;

/**
 * Abstract base class for all boss AIs. Handles:
 * - Phase tracking (Phase 1 → Phase 2 at ≤50% HP)
 * - Turn counter
 * - Ability cooldowns
 * - Pending warnings (telegraphed abilities)
 * - Phase transition callback
 *
 * Subclasses implement chooseAbility() for their unique behavior.
 */
public abstract class BossAI implements EnemyAI {
    protected int turnCounter = 0;
    protected boolean phaseTwo = false;
    protected final Map<String, Integer> cooldowns = new HashMap<>();
    protected BossWarning pendingWarning = null;

    // Summoned minion tracking — boss AI can check how many are alive
    protected final List<Integer> summonedMinionIds = new ArrayList<>();

    // Projectile tracking — separate from minions
    protected final List<Integer> projectileIds = new ArrayList<>();

    /**
     * Grid size for this boss. Override in subclasses for naturally large mobs.
     * Default is 1 (humanoid-sized bosses). Return 2 for large mobs (spider, hoglin, ghast, etc.)
     */
    public int getGridSize() { return 1; }

    @Override
    public final EnemyAction decideAction(CombatEntity self, GridArena arena, GridPos playerPos) {
        turnCounter++;

        // Phase transition check
        if (!phaseTwo && self.getCurrentHp() <= self.getMaxHp() / 2) {
            phaseTwo = true;
            onPhaseTransition(self, arena, playerPos);
        }

        // Tick cooldowns
        cooldowns.replaceAll((k, v) -> Math.max(0, v - 1));

        // Clean up dead minions — check all occupants by entity ID
        summonedMinionIds.removeIf(id -> {
            for (CombatEntity e : arena.getOccupants().values()) {
                if (e.getEntityId() == id && e.isAlive()) return false;
            }
            return true; // not found or dead
        });

        // Clean up dead projectiles
        projectileIds.removeIf(id -> {
            for (CombatEntity e : arena.getOccupants().values()) {
                if (e.getEntityId() == id && e.isAlive()) return false;
            }
            return true;
        });

        // Tick pending warning countdown, then check if it should resolve
        if (pendingWarning != null) {
            pendingWarning.tick();
            if (pendingWarning.isReady()) {
                EnemyAction resolvedAction = pendingWarning.getResolveAction();
                pendingWarning = null;
                return resolvedAction;
            }
        }

        // Let the subclass choose what to do
        return chooseAbility(self, arena, playerPos);
    }

    /**
     * Called once when the boss transitions to Phase 2 (≤50% HP).
     * Subclasses can use this to set enraged state, create terrain, etc.
     */
    protected abstract void onPhaseTransition(CombatEntity self, GridArena arena, GridPos playerPos);

    /**
     * Choose which ability to use this turn. Called every turn after phase/cooldown handling.
     */
    protected abstract EnemyAction chooseAbility(CombatEntity self, GridArena arena, GridPos playerPos);

    // === Utility methods for subclasses ===

    protected boolean isOnCooldown(String ability) {
        return cooldowns.getOrDefault(ability, 0) > 0;
    }

    protected void setCooldown(String ability, int turns) {
        cooldowns.put(ability, turns);
    }

    protected int getAliveMinionCount() {
        return summonedMinionIds.size();
    }

    protected int getAliveProjectileCount() {
        return projectileIds.size();
    }

    /** Called by CombatManager after spawning a projectile entity. */
    public void registerSpawnedProjectile(int entityId) {
        projectileIds.add(entityId);
    }

    /** Called by CombatManager after spawning a minion entity. */
    public void registerSpawnedMinion(int entityId) {
        summonedMinionIds.add(entityId);
    }

    protected boolean isPhaseTwo() {
        return phaseTwo;
    }

    protected int getTurnCounter() {
        return turnCounter;
    }

    /** Get current pending warning (for CombatManager to render). */
    public BossWarning getPendingWarning() {
        return pendingWarning;
    }

    /** Clear pending warning (called by CombatManager when it takes ownership via BossAbility). */
    public void clearPendingWarning() {
        pendingWarning = null;
    }

    /** Tick the pending warning down. Called by CombatManager at end of player turn. */
    public void tickWarning() {
        if (pendingWarning != null) {
            pendingWarning.tick();
        }
    }

    /**
     * Find empty tiles in the arena for summoning minions.
     * Returns up to 'count' random empty walkable tiles.
     */
    protected List<GridPos> findSummonPositions(GridArena arena, int count) {
        List<GridPos> candidates = new ArrayList<>();
        for (int x = 0; x < arena.getWidth(); x++) {
            for (int z = 0; z < arena.getHeight(); z++) {
                GridPos pos = new GridPos(x, z);
                if (!arena.isOccupied(pos) && arena.getTile(pos) != null
                        && arena.getTile(pos).isWalkable()) {
                    candidates.add(pos);
                }
            }
        }
        Collections.shuffle(candidates);
        return candidates.subList(0, Math.min(count, candidates.size()));
    }

    /**
     * Find empty tiles near a specific position for targeted summoning.
     */
    protected List<GridPos> findSummonPositionsNear(GridArena arena, GridPos center, int radius, int count) {
        List<GridPos> candidates = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                GridPos pos = new GridPos(center.x() + dx, center.z() + dz);
                if (arena.isInBounds(pos) && !arena.isOccupied(pos)
                        && arena.getTile(pos) != null && arena.getTile(pos).isWalkable()) {
                    candidates.add(pos);
                }
            }
        }
        Collections.shuffle(candidates);
        return candidates.subList(0, Math.min(count, candidates.size()));
    }

    /**
     * Get tiles in a + cross pattern centered on a position.
     */
    protected List<GridPos> getCrossTiles(GridArena arena, GridPos center, int range) {
        List<GridPos> tiles = new ArrayList<>();
        for (int[] dir : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            for (int i = 1; i <= range; i++) {
                GridPos pos = new GridPos(center.x() + dir[0] * i, center.z() + dir[1] * i);
                if (arena.isInBounds(pos)) {
                    tiles.add(pos);
                }
            }
        }
        return tiles;
    }

    /**
     * Get tiles in a square area centered on a position.
     */
    protected List<GridPos> getAreaTiles(GridArena arena, GridPos center, int radius) {
        List<GridPos> tiles = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                GridPos pos = new GridPos(center.x() + dx, center.z() + dz);
                if (arena.isInBounds(pos)) {
                    tiles.add(pos);
                }
            }
        }
        return tiles;
    }

    /**
     * Get tiles in a line from a start point in a direction.
     */
    protected List<GridPos> getLineTiles(GridArena arena, GridPos start, int dx, int dz, int length) {
        List<GridPos> tiles = new ArrayList<>();
        GridPos current = start;
        for (int i = 0; i < length; i++) {
            current = new GridPos(current.x() + dx, current.z() + dz);
            if (arena.isInBounds(current)) {
                tiles.add(current);
            }
        }
        return tiles;
    }

    /**
     * Get tiles in a full row of the arena.
     */
    protected List<GridPos> getRowTiles(GridArena arena, int z) {
        List<GridPos> tiles = new ArrayList<>();
        for (int x = 0; x < arena.getWidth(); x++) {
            tiles.add(new GridPos(x, z));
        }
        return tiles;
    }

    /**
     * Get tiles in a full column of the arena.
     */
    protected List<GridPos> getColumnTiles(GridArena arena, int x) {
        List<GridPos> tiles = new ArrayList<>();
        for (int z = 0; z < arena.getHeight(); z++) {
            tiles.add(new GridPos(x, z));
        }
        return tiles;
    }

    /**
     * Find the cardinal direction (dx, dz) from one pos toward another.
     */
    protected int[] getDirectionToward(GridPos from, GridPos to) {
        int dx = to.x() - from.x();
        int dz = to.z() - from.z();
        if (Math.abs(dx) >= Math.abs(dz)) {
            return new int[]{Integer.signum(dx), 0};
        } else {
            return new int[]{0, Integer.signum(dz)};
        }
    }

    /**
     * Check if a charge path is clear (no obstacles) in a direction.
     * Returns the valid path tiles (stops at obstacle or arena edge).
     */
    protected List<GridPos> getChargePath(GridArena arena, GridPos start, int dx, int dz, int maxLength) {
        List<GridPos> path = new ArrayList<>();
        GridPos current = start;
        for (int i = 0; i < maxLength; i++) {
            GridPos next = new GridPos(current.x() + dx, current.z() + dz);
            if (!arena.isInBounds(next)) break;
            if (arena.getTile(next) != null && !arena.getTile(next).isWalkable()
                    && arena.getTile(next).getType() != com.crackedgames.craftics.core.TileType.FIRE) {
                break; // hit obstacle
            }
            path.add(next);
            current = next;
        }
        return path;
    }

    /**
     * Find spawn positions in front of the boss toward the player for projectile spawning.
     * For a sized boss (e.g. 2×2), positions are on the edge facing the player, 1-2 tiles out.
     */
    protected List<GridPos> getProjectileSpawnPositions(GridArena arena, GridPos bossPos, int bossSize,
                                                         GridPos playerPos, int count) {
        int[] dir = getDirectionToward(bossPos, playerPos);
        List<GridPos> candidates = new ArrayList<>();

        // Search 1-2 tiles ahead of the boss edge, spreading perpendicular
        for (int depth = 1; depth <= 2 && candidates.size() < count; depth++) {
            for (int offset = -1; offset <= bossSize; offset++) {
                GridPos pos;
                if (dir[0] != 0) {
                    int frontX = dir[0] > 0 ? bossPos.x() + bossSize - 1 + depth : bossPos.x() - depth;
                    pos = new GridPos(frontX, bossPos.z() + offset);
                } else {
                    int frontZ = dir[1] > 0 ? bossPos.z() + bossSize - 1 + depth : bossPos.z() - depth;
                    pos = new GridPos(bossPos.x() + offset, frontZ);
                }
                if (arena.isInBounds(pos) && !arena.isOccupied(pos)
                        && arena.getTile(pos) != null && arena.getTile(pos).isWalkable()) {
                    candidates.add(pos);
                }
                if (candidates.size() >= count) break;
            }
        }

        return candidates.subList(0, Math.min(count, candidates.size()));
    }

    /**
     * Default melee-or-move fallback: if adjacent attack, else path toward player.
     */
    protected EnemyAction meleeOrApproach(CombatEntity self, GridArena arena, GridPos playerPos, int extraDamage) {
        int dist = self.minDistanceTo(playerPos);
        if (dist <= 1) {
            return new EnemyAction.Attack(self.getAttackPower() + extraDamage);
        }
        return AIUtils.seekOrWander(self, arena, playerPos);
    }
}
