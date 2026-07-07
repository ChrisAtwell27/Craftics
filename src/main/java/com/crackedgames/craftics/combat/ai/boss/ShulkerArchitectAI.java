package com.crackedgames.craftics.combat.ai.boss;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.ai.EnemyAction;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;

import java.util.ArrayList;
import java.util.List;

/**
 * End City Boss - "The Shulker Architect"
 * Entity: Shulker | 50HP / 9ATK / 4DEF / Range 5 / Speed 1 | Size 2×2
 *
 * Abilities:
 * - Bullet Storm: blooms 4 (P2: 6) homing bullet ENTITIES from the ring of
 *   tiles around its 2x2 shell - each a real 1-HP seeker (SeekingProjectileAI,
 *   2 tiles/turn, 3 dmg + Levitation on impact). Shoot them down or outplay
 *   them; a new bloom only starts once the previous volley has resolved.
 * - Deploy Turret: Stationary shulker turret (6HP, 1 bullet/turn range 4, 2 dmg). Max 3 (P2: 5).
 * - Fortify Shell: 80% damage reduction 1 turn. P2: also reflects 50% damage.
 * - Teleport Link: Blink to a tile beside the turret farthest from the player.
 *
 * Phase 2 - "Defense Protocol": 6 bullets, turret levitation bullets, reflect shell,
 * turret limit 5, auto-deploy every 3 turns.
 */
public class ShulkerArchitectAI extends BossAI {
    @Override public int getGridSize() { return 2; }
    private static final String CD_BULLET = "bullet_storm";
    private static final String CD_TURRET = "deploy_turret";
    private static final String CD_SHELL = "fortify_shell";
    private static final String CD_LINK = "teleport_link";
    private final List<GridPos> turretPositions = new ArrayList<>();
    private boolean wasHitLastTurn = false;

    @Override
    protected void onPhaseTransition(CombatEntity self, GridArena arena, GridPos playerPos) {
        self.setEnraged(true);
    }

    @Override
    protected EnemyAction chooseAbility(CombatEntity self, GridArena arena, GridPos playerPos) {
        GridPos myPos = self.getGridPos();
        int dist = self.minDistanceTo(playerPos);

        // Phase 2: Auto-deploy turrets every 3 turns if below cap
        int turretCap = isPhaseTwo() ? 5 : 3;
        if (isPhaseTwo() && getTurnCounter() % 3 == 0 && turretPositions.size() < turretCap) {
            List<GridPos> pos = findSummonPositions(arena, 1);
            if (!pos.isEmpty()) {
                turretPositions.add(pos.get(0));
                return new EnemyAction.SummonMinions(
                    "minecraft:shulker", 1, pos, 6, 2, 0);
            }
        }

        // Fortify Shell - reactive when hit
        if (wasHitLastTurn && !isOnCooldown(CD_SHELL)) {
            wasHitLastTurn = false;
            setCooldown(CD_SHELL, 3);
            String shellType = isPhaseTwo() ? "fortify_shell_reflect" : "fortify_shell";
            return new EnemyAction.BossAbility(shellType,
                new EnemyAction.ModifySelf("defense", 20, 1),
                List.of(myPos));
        }
        wasHitLastTurn = false;

        // Teleport Link - escape melee range by blinking to the network. The
        // boss lands BESIDE the turret farthest from the player (footprint-
        // validated for its 2×2 body) - the old code teleported squarely onto
        // the still-living turret's tile, clipping into its own minion.
        if (!isOnCooldown(CD_LINK) && dist <= 2 && !turretPositions.isEmpty()) {
            GridPos farthest = null;
            int maxDist = -1;
            for (GridPos tp : turretPositions) {
                int d = tp.manhattanDistance(playerPos);
                if (d > maxDist) {
                    maxDist = d;
                    farthest = tp;
                }
            }
            GridPos landing = farthest != null ? findLandingNear(arena, farthest, self) : null;
            if (landing != null) {
                setCooldown(CD_LINK, 2);
                return new EnemyAction.Teleport(landing);
            }
        }

        // Deploy Turret
        if (!isOnCooldown(CD_TURRET) && turretPositions.size() < turretCap) {
            List<GridPos> pos = findSummonPositions(arena, 1);
            if (!pos.isEmpty()) {
                setCooldown(CD_TURRET, 2);
                turretPositions.add(pos.get(0));
                return new EnemyAction.SummonMinions(
                    "minecraft:shulker", 1, pos, 6, 2, 0);
            }
        }

        // Bullet Storm - blooms a ring of homing bullet ENTITIES around the
        // boss's own shell: 4 in P1 / 6 in P2, each a 1-HP seeker that chases
        // the player at 2 tiles/turn (SeekingProjectileAI) and lands 3 damage
        // + Levitation on impact. The bullets themselves are the telegraph -
        // visible, killable, and a full turn away - so the old marked-tile
        // warning is gone. A fresh bloom waits until the last volley has fully
        // resolved (killed or landed) so the sky never stacks two storms.
        if (!isOnCooldown(CD_BULLET) && dist <= 5 && countLiveBullets(self, arena) == 0) {
            int bulletCount = isPhaseTwo() ? 6 : 4;
            List<GridPos> ring = findBloomPositions(self, arena, bulletCount);
            if (!ring.isEmpty()) {
                setCooldown(CD_BULLET, 2);
                GridPos center = self.getGridPos();
                List<int[]> directions = new ArrayList<>();
                for (GridPos p : ring) {
                    int dx = Integer.signum(p.x() - center.x());
                    int dz = Integer.signum(p.z() - center.z());
                    directions.add(new int[]{dx != 0 ? dx : 0, dx != 0 ? 0 : (dz != 0 ? dz : 1)});
                }
                return new EnemyAction.SpawnProjectile(
                    "minecraft:blaze", ring, directions, 1, 3, 0, "shulker_bullet");
            }
        }

        // Ranged attack if in range
        if (dist <= 5) {
            return new EnemyAction.RangedAttack(self.getAttackPower(), "shulker_bullet");
        }

        return meleeOrApproach(self, arena, playerPos, 0);
    }

    /** Live homing bullets owned by this boss still in flight. */
    private int countLiveBullets(CombatEntity self, GridArena arena) {
        int n = 0;
        for (CombatEntity e : arena.getOccupants().values()) {
            if (e.isAlive() && e.isProjectile()
                    && "shulker_bullet".equals(e.getProjectileType())
                    && e.getProjectileOwnerId() == self.getEntityId()) {
                n++;
            }
        }
        return n;
    }

    /**
     * Up to {@code count} free walkable tiles from the ring around the boss's
     * 2x2 footprint, picked evenly around the loop so the bloom surrounds the
     * shell instead of clustering on one side.
     */
    private List<GridPos> findBloomPositions(CombatEntity self, GridArena arena, int count) {
        GridPos anchor = self.getGridPos();
        int size = self.getMaxSize();
        // Walk the ring clockwise: top edge, right edge, bottom edge, left edge.
        List<GridPos> ring = new ArrayList<>();
        for (int x = -1; x <= size; x++) ring.add(new GridPos(anchor.x() + x, anchor.z() - 1));
        for (int z = 0; z < size; z++) ring.add(new GridPos(anchor.x() + size, anchor.z() + z));
        for (int x = size; x >= -1; x--) ring.add(new GridPos(anchor.x() + x, anchor.z() + size));
        for (int z = size - 1; z >= 0; z--) ring.add(new GridPos(anchor.x() - 1, anchor.z() + z));

        List<GridPos> valid = new ArrayList<>();
        for (GridPos p : ring) {
            if (!arena.isInBounds(p) || arena.isOccupied(p)) continue;
            if (arena.getTile(p) == null || !arena.getTile(p).isWalkable()) continue;
            valid.add(p);
        }
        if (valid.size() <= count) return valid;
        List<GridPos> spread = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            spread.add(valid.get(i * valid.size() / count));
        }
        return spread;
    }

    /** A footprint-valid anchor adjacent to {@code turret} for the 2×2 boss; null if none. */
    private GridPos findLandingNear(GridArena arena, GridPos turret, CombatEntity self) {
        int size = self.getMaxSize();
        for (int dx = -size; dx <= 1; dx++) {
            for (int dz = -size; dz <= 1; dz++) {
                GridPos p = new GridPos(turret.x() + dx, turret.z() + dz);
                if (com.crackedgames.craftics.combat.ai.AIUtils.canPlaceFootprint(arena, p, size)) {
                    return p;
                }
            }
        }
        return null;
    }

    /** Called by CombatManager when the boss takes damage - primes Fortify Shell */
    public void notifyDamaged() { wasHitLastTurn = true; }

    public void removeTurret(GridPos pos) { turretPositions.remove(pos); }
    public List<GridPos> getTurretPositions() { return turretPositions; }
}
