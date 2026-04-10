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
 *
 * Split mechanic: at 50% HP the original boss dies and splits cleanly into 2
 * smaller copies that retain all boss abilities. Each copy acts as an
 * independent boss with its own state. The splits do NOT split further.
 *
 * Gen 0: 4×4 footprint  →  splits into 2 × Gen 1 at 50% HP
 * Gen 1: 2×2 footprint  →  no further splitting
 *
 * Abilities (both generations):
 * - Magma Eruption: Teleport-leap + 3×3 blast + fire ring
 * - Lava Cage: Ring player with fire tiles
 * - Absorb: Merge with a nearby minion to heal
 * - Lava Creep (Phase 2): Arena shrinks via fire rings
 *
 * Phase 2 — "Meltdown": Permanent fire tiles, arena shrink, faster cooldowns.
 */
public class MoltenKingAI extends BossAI {
    private final int generation; // 0 = original, 1 = final split
    private static final String CD_CREEP = "lava_creep";
    private static final String CD_ERUPTION = "magma_eruption";
    private static final String CD_CAGE = "lava_cage";
    private static final String CD_ABSORB = "absorb";
    private int creepStage = 0;

    public MoltenKingAI() { this(0); }
    public MoltenKingAI(int generation) { this.generation = generation; }

    @Override public int getGridSize() { return generation == 0 ? 4 : 2; }

    /** Returns the AI key for spawned split copies, or null if this generation doesn't split. */
    public String getNextGenAiKey() {
        return generation == 0 ? "boss:nether_wastes_g1" : null;
    }

    /** Whether this generation can still split. */
    public boolean canSplit() { return generation == 0; }

    /** Get the generation number. */
    public int getGeneration() { return generation; }

    @Override
    protected void onPhaseTransition(CombatEntity self, GridArena arena, GridPos playerPos) {
        self.setEnraged(true);
        creepStage = 0;
    }

    @Override
    protected EnemyAction chooseAbility(CombatEntity self, GridArena arena, GridPos playerPos) {
        GridPos myPos = self.getGridPos();
        int dist = self.minDistanceTo(playerPos);

        int eruptionDmg = generation == 0 ? 12 : 10;
        int cageDuration = isPhaseTwo() ? 0 : 4;

        // Phase 2 mechanic: arena shrink every other round as a circular lava ring
        if (isPhaseTwo() && getTurnCounter() % 2 == 0 && !isOnCooldown(CD_CREEP)) {
            List<GridPos> creepRing = getCreepRingTiles(arena);
            if (!creepRing.isEmpty()) {
                setCooldown(CD_CREEP, 2);
                return new EnemyAction.CreateTerrain(creepRing, TileType.FIRE, 0);
            }
        }

        // Lava Cage — deny escape routes around the player
        if (!isOnCooldown(CD_CAGE) && dist >= 2) {
            List<GridPos> cageTiles = getLavaCageTiles(arena, playerPos);
            if (!cageTiles.isEmpty()) {
                setCooldown(CD_CAGE, isPhaseTwo() ? 2 : 3);
                pendingWarning = new BossWarning(
                    self.getEntityId(), BossWarning.WarningType.TILE_HIGHLIGHT,
                    cageTiles, 1,
                    new EnemyAction.CreateTerrain(cageTiles, TileType.FIRE, cageDuration),
                    0xFFFF5500
                );
                return new EnemyAction.Idle();
            }
        }

        // Absorb a nearby minion to heal
        if (!isOnCooldown(CD_ABSORB) && getAliveMinionCount() > 0) {
            int absorbeRange = isPhaseTwo() ? 2 : 1;
            for (CombatEntity e : arena.getOccupants().values()) {
                if (summonedMinionIds.contains(e.getEntityId()) && e.isAlive()) {
                    int mDist = myPos.manhattanDistance(e.getGridPos());
                    if (mDist <= absorbeRange) {
                        setCooldown(CD_ABSORB, 2);
                        return new EnemyAction.BossAbility("absorb",
                            new EnemyAction.ModifySelf("hp", e.getCurrentHp(), 0),
                            List.of(e.getGridPos()));
                    }
                }
            }
        }

        // Magma Eruption — leap to a valid tile near player and explode
        if (!isOnCooldown(CD_ERUPTION) && dist >= 2) {
            setCooldown(CD_ERUPTION, isPhaseTwo() ? 2 : 3);
            GridPos landingPos = findEruptionLanding(arena, playerPos, myPos);
            if (landingPos == null) {
                landingPos = playerPos;
            }
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
                new EnemyAction.Teleport(landingPos),
                new EnemyAction.AreaAttack(landingPos, 1, eruptionDmg, "magma_eruption"),
                new EnemyAction.CreateTerrain(fireRing, TileType.FIRE, fireDuration)
            ));
            pendingWarning = new BossWarning(
                self.getEntityId(), BossWarning.WarningType.TILE_HIGHLIGHT,
                aoeArea, 1, eruption, 0xFFFF4400);
            return new EnemyAction.Idle();
        }

        // Melee attack if adjacent — molten slam hits hard and pushes the player
        // into whatever burning terrain the boss just left behind.
        if (dist <= 1) {
            int meleeDmg = self.getAttackPower() + (isPhaseTwo() ? 3 : 2);
            return new EnemyAction.AttackWithKnockback(meleeDmg, 2);
        }

        return meleeOrApproach(self, arena, playerPos, 0);
    }

    private List<GridPos> getLavaCageTiles(GridArena arena, GridPos center) {
        List<GridPos> tiles = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (Math.abs(dx) + Math.abs(dz) != 1) continue;
                GridPos p = new GridPos(center.x() + dx, center.z() + dz);
                if (!arena.isInBounds(p)) continue;
                if (arena.getTile(p) == null || !arena.getTile(p).isWalkable()) continue;
                tiles.add(p);
            }
        }
        return tiles;
    }

    private GridPos findEruptionLanding(GridArena arena, GridPos playerPos, GridPos fallback) {
        List<GridPos> candidates = new ArrayList<>();
        for (int radius = 1; radius <= 2; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                    GridPos p = new GridPos(playerPos.x() + dx, playerPos.z() + dz);
                    if (!arena.isInBounds(p)) continue;
                    if (arena.isOccupied(p)) continue;
                    if (arena.getTile(p) == null || !arena.getTile(p).isWalkable()) continue;
                    candidates.add(p);
                }
            }
            if (!candidates.isEmpty()) break;
        }
        if (candidates.isEmpty()) {
            if (arena.isInBounds(fallback) && !arena.isOccupied(fallback)) return fallback;
            return null;
        }
        java.util.Collections.shuffle(candidates);
        return candidates.get(0);
    }

    private List<GridPos> getCreepRingTiles(GridArena arena) {
        int width = arena.getWidth();
        int height = arena.getHeight();
        int bands = Math.max(3, Math.min(width, height) / 2);
        int stage = Math.min(creepStage, bands - 1);
        double bandSize = 1.0 / bands;
        double outer = 1.0 - stage * bandSize;
        double inner = Math.max(0.0, outer - bandSize);

        double cx = (width - 1) / 2.0;
        double cz = (height - 1) / 2.0;
        double rx = Math.max(1.0, (width - 1) / 2.0);
        double rz = Math.max(1.0, (height - 1) / 2.0);

        double gapAngle = (getTurnCounter() * 0.7) % (Math.PI * 2.0);
        double gapWidth = 0.45;

        List<GridPos> ring = new ArrayList<>();
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < height; z++) {
                GridPos p = new GridPos(x, z);
                if (arena.getTile(p) == null || !arena.getTile(p).isWalkable()) continue;

                double nx = (x - cx) / rx;
                double nz = (z - cz) / rz;
                double radial = Math.sqrt(nx * nx + nz * nz);
                if (radial > outer || radial <= inner) continue;

                double a = Math.atan2(z - cz, x - cx);
                double d1 = angleDistance(a, gapAngle);
                double d2 = angleDistance(a, gapAngle + Math.PI);
                if (d1 < gapWidth || d2 < gapWidth) continue;

                ring.add(p);
            }
        }

        if (!ring.isEmpty()) {
            creepStage = Math.min(creepStage + 1, bands - 1);
        }
        return ring;
    }

    private double angleDistance(double a, double b) {
        double d = Math.abs(a - b) % (Math.PI * 2.0);
        return d > Math.PI ? (Math.PI * 2.0 - d) : d;
    }
}
