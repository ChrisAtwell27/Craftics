package com.crackedgames.craftics.combat.ai.boss;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.ai.EnemyAction;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Dense Jungle Boss — "The Broodmother" (Spider Queen)
 * Entity: Spider | 35HP / 6ATK / 2DEF / Speed 3 | Size 2×2
 *
 * State-based AI with two modes: HUNTING (aggressive) and NESTING (defensive).
 *
 * Phase 1 Abilities:
 *   Hunting: Ceiling Ambush, Pounce, Venomous Bite
 *   Nesting: Spawn Brood, Web Spray
 *
 * Phase 2 — "Nest Awakening" (≤50% HP):
 *   +2 Speed, Ceiling Ambush → Hunting Dive (web rain + dive-bomb),
 *   Web Spray gains Poison, Pounce range +1, can place new egg sacs.
 *
 * Egg sacs are 1HP entities on the grid (turtle egg blocks).
 * Max alive cave spiders = number of living egg sacs.
 */
public class BroodmotherAI extends BossAI {
    @Override public int getGridSize() { return 2; }

    // --- Cooldown keys ---
    private static final String CD_CEILING = "ceiling";
    private static final String CD_POUNCE = "pounce";
    private static final String CD_WEB = "web_spray";
    private static final String CD_BROOD = "spawn_brood";

    // --- State machine ---
    public enum State { HUNTING, NESTING }
    private State state = State.HUNTING;

    // --- Egg sac tracking (grid positions of living egg sac entities) ---
    private final List<GridPos> eggSacs = new ArrayList<>();
    private boolean eggSacDestroyedThisCycle = false;

    // --- Ceiling mechanic ---
    // True when the boss has ascended but not yet set the dive warning (P2 web-rain turn).
    private boolean ceilingWebRainPending = false;

    // --- Web rain (consumed by CombatManager after processing boss turn) ---
    private List<GridPos> pendingWebRain = null;

    /**
     * Called by CombatManager during boss setup to place the initial three egg sac entities.
     * Selects positions at the corners/edges of the arena and registers them as active sacs.
     * CombatManager is responsible for spawning the actual entities at these positions.
     */
    public List<GridPos> initEggSacs(GridArena arena) {
        int w = arena.getWidth(), h = arena.getHeight();
        List<GridPos> candidates = new ArrayList<>();
        candidates.add(new GridPos(1, 1));
        candidates.add(new GridPos(w - 2, 1));
        candidates.add(new GridPos(1, h - 2));
        candidates.add(new GridPos(w - 2, h - 2));
        candidates.add(new GridPos(w / 2, 1));
        candidates.add(new GridPos(w / 2, h - 2));
        Collections.shuffle(candidates);
        List<GridPos> chosen = new ArrayList<>();
        for (GridPos pos : candidates) {
            if (chosen.size() >= 3) break;
            if (arena.isInBounds(pos) && !arena.isOccupied(pos)) {
                eggSacs.add(pos);
                chosen.add(pos);
            }
        }
        return chosen;
    }

    /**
     * Called by CombatManager after spawning egg sac entities.
     * Records their grid positions for spawn/tracking logic.
     */
    public void registerEggSac(GridPos pos) {
        if (!eggSacs.contains(pos)) {
            eggSacs.add(pos);
        }
    }

    /**
     * Called by CombatManager when an egg sac entity dies.
     * Removes it from tracking and triggers Nesting transition.
     */
    public void onEggSacDestroyed(GridPos pos) {
        eggSacs.remove(pos);
        eggSacDestroyedThisCycle = true;
    }

    public List<GridPos> getEggSacs() { return eggSacs; }
    public State getState() { return state; }

    /** Called by CombatManager after boss turn to check for pending web rain placement. */
    public List<GridPos> consumeWebRain() {
        List<GridPos> r = pendingWebRain;
        pendingWebRain = null;
        return r;
    }

    @Override
    protected void onPhaseTransition(CombatEntity self, GridArena arena, GridPos playerPos) {
        self.setEnraged(true);
        self.setSpeedBonus(2); // Speed 3 → 5
    }

    @Override
    protected EnemyAction chooseAbility(CombatEntity self, GridArena arena, GridPos playerPos) {
        // --- P2 Ceiling Sequence: web-rain turn while boss is on ceiling ---
        // On the turn AFTER ascending (P2 only), we place webs and set the dive warning.
        if (ceilingWebRainPending) {
            return handleWebRainTurn(self, arena, playerPos);
        }

        // --- State transitions ---
        updateState();

        // --- Dispatch based on state ---
        if (state == State.HUNTING) {
            return huntingBehavior(self, arena, playerPos);
        } else {
            return nestingBehavior(self, arena, playerPos);
        }
    }

    // =========================================================================
    // State Transitions
    // =========================================================================

    private void updateState() {
        if (state == State.HUNTING) {
            // Switch to NESTING if an egg sac was just destroyed
            if (eggSacDestroyedThisCycle) {
                eggSacDestroyedThisCycle = false;
                state = State.NESTING;
                return;
            }
            // Switch to NESTING if no minions alive and brood spawn is ready
            if (getAliveMinionCount() == 0 && !isOnCooldown(CD_BROOD) && !eggSacs.isEmpty()) {
                state = State.NESTING;
            }
        } else { // NESTING
            eggSacDestroyedThisCycle = false; // clear flag
            // Switch to HUNTING if we have minion cover
            if (getAliveMinionCount() > 0) {
                state = State.HUNTING;
                return;
            }
            // Switch to HUNTING if nothing left to do in nest
            if (isOnCooldown(CD_BROOD) && isOnCooldown(CD_WEB)) {
                state = State.HUNTING;
            }
        }
    }

    // =========================================================================
    // HUNTING State
    // =========================================================================

    private EnemyAction huntingBehavior(CombatEntity self, GridArena arena, GridPos playerPos) {
        int dist = self.minDistanceTo(playerPos);

        // 1) Ceiling Ambush / Hunting Dive
        if (!isOnCooldown(CD_CEILING)) {
            return startCeilingSequence(self, arena, playerPos);
        }

        // 2) Pounce
        int pounceRange = isPhaseTwo() ? 4 : 3;
        if (!isOnCooldown(CD_POUNCE) && dist >= 2 && dist <= pounceRange) {
            EnemyAction pounce = tryPounce(self, arena, playerPos);
            if (pounce != null) return pounce;
        }

        // 3) Venomous Bite (melee)
        if (dist <= 1) {
            return new EnemyAction.RangedAttack(self.getAttackPower(), "venomous_bite");
        }

        // 4) Walk toward player
        return meleeOrApproach(self, arena, playerPos, 0);
    }

    // =========================================================================
    // NESTING State
    // =========================================================================

    private EnemyAction nestingBehavior(CombatEntity self, GridArena arena, GridPos playerPos) {
        int dist = self.minDistanceTo(playerPos);

        // 1) Spawn Brood
        if (!isOnCooldown(CD_BROOD) && !eggSacs.isEmpty()) {
            EnemyAction spawn = trySpawnBrood(self, arena);
            if (spawn != null) return spawn;
        }

        // 2) Web Spray
        if (!isOnCooldown(CD_WEB) && dist <= 4) {
            return doWebSpray(self, arena, playerPos);
        }

        // 3) Place new egg sacs (Phase 2 only, fewer than 3 remaining)
        if (isPhaseTwo() && eggSacs.size() < 3) {
            EnemyAction place = tryPlaceEggSacs(self, arena);
            if (place != null) return place;
        }

        // 4) Move toward nearest egg sac
        GridPos nearestSac = findNearestEggSac(self.getGridPos());
        if (nearestSac != null) {
            return meleeOrApproach(self, arena, nearestSac, 0);
        }

        return new EnemyAction.Idle();
    }

    // =========================================================================
    // Abilities
    // =========================================================================

    // --- Ceiling Ambush (P1) / Hunting Dive (P2) ---

    private EnemyAction startCeilingSequence(CombatEntity self, GridArena arena, GridPos playerPos) {
        setCooldown(CD_CEILING, 4);

        if (isPhaseTwo()) {
            // Hunting Dive: ascend this turn, next turn place web rain + set dive warning.
            // The dive itself resolves via pendingWarning auto-resolve on the turn after that.
            ceilingWebRainPending = true;
        } else {
            // Ceiling Ambush: ascend this turn, set dive warning — auto-resolves next boss turn.
            List<GridPos> diveTiles = getAreaTiles(arena, playerPos, 1);
            pendingWarning = new BossWarning(
                self.getEntityId(), BossWarning.WarningType.TILE_HIGHLIGHT,
                diveTiles, 1,
                new EnemyAction.CompositeAction(List.of(
                    new EnemyAction.CeilingDrop(playerPos, 0),
                    new EnemyAction.AreaAttack(playerPos, 1, self.getAttackPower() + 4, "ceiling_slam")
                )),
                0xFFFF4444
            );
        }

        return new EnemyAction.CeilingAscend();
    }

    /**
     * Called on the turn after ascending in Phase 2 (boss is on ceiling).
     * Places web rain tiles and sets the dive warning for the next turn.
     * The warning auto-resolves at the top of the boss's following turn.
     */
    private EnemyAction handleWebRainTurn(CombatEntity self, GridArena arena, GridPos playerPos) {
        ceilingWebRainPending = false;

        // Store web rain targets for CombatManager to place cobweb blocks
        pendingWebRain = getRandomWalkableTiles(arena, 5);

        // Set dive warning targeting current player position — auto-resolves next boss turn
        List<GridPos> diveTiles = getAreaTiles(arena, playerPos, 1);
        pendingWarning = new BossWarning(
            self.getEntityId(), BossWarning.WarningType.TILE_HIGHLIGHT,
            diveTiles, 1,
            new EnemyAction.CompositeAction(List.of(
                new EnemyAction.CeilingDrop(playerPos, 0),
                new EnemyAction.AreaAttack(playerPos, 1, self.getAttackPower() + 4, "hunting_dive_slam")
            )),
            0xFFFF4444
        );

        return new EnemyAction.Idle(); // boss stays on ceiling; CombatManager places webs
    }

    // --- Pounce ---

    private EnemyAction tryPounce(CombatEntity self, GridArena arena, GridPos playerPos) {
        // Find landing spot adjacent to player
        GridPos landingPos = null;
        for (int[] d : new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) {
            GridPos p = new GridPos(playerPos.x() + d[0], playerPos.z() + d[1]);
            if (arena.isInBounds(p) && !arena.isOccupied(p)
                    && arena.getTile(p) != null && arena.getTile(p).isWalkable()) {
                landingPos = p;
                break;
            }
        }
        if (landingPos == null) return null;

        setCooldown(CD_POUNCE, 2);

        // 2×2 AoE pounce landing
        List<GridPos> landingTiles = new ArrayList<>();
        landingTiles.add(landingPos);
        landingTiles.add(new GridPos(landingPos.x() + 1, landingPos.z()));
        landingTiles.add(new GridPos(landingPos.x(), landingPos.z() + 1));
        landingTiles.add(new GridPos(landingPos.x() + 1, landingPos.z() + 1));
        landingTiles.removeIf(p -> !arena.isInBounds(p));

        EnemyAction pounce = new EnemyAction.AreaAttack(landingPos, 1, self.getAttackPower() + 2, "pounce");
        pendingWarning = new BossWarning(
            self.getEntityId(), BossWarning.WarningType.TILE_HIGHLIGHT,
            landingTiles, 1, pounce, 0xFFFF4444);
        return new EnemyAction.Idle();
    }

    // --- Web Spray ---

    private EnemyAction doWebSpray(CombatEntity self, GridArena arena, GridPos playerPos) {
        setCooldown(CD_WEB, 3);
        List<GridPos> webTiles = getAreaTiles(arena, playerPos, 1);
        String effect = isPhaseTwo() ? "web_spray_poison" : "web_spray";
        EnemyAction webAction = new EnemyAction.AreaAttack(playerPos, 1, 0, effect);
        pendingWarning = new BossWarning(
            self.getEntityId(), BossWarning.WarningType.GATHERING_PARTICLES,
            webTiles, 1, webAction, 0xFFCCCCCC);
        return new EnemyAction.Idle();
    }

    // --- Spawn Brood ---

    private EnemyAction trySpawnBrood(CombatEntity self, GridArena arena) {
        // Max alive spiders = number of living egg sacs
        int maxSpiders = eggSacs.size();
        int canSpawn = maxSpiders - getAliveMinionCount();
        if (canSpawn <= 0) return null;

        setCooldown(CD_BROOD, 3);

        List<GridPos> spawnPositions = new ArrayList<>();
        List<GridPos> warnPositions = new ArrayList<>();
        int spawned = 0;
        for (GridPos sacPos : eggSacs) {
            if (spawned >= canSpawn) break;
            // Find an adjacent walkable empty tile
            for (int[] d : new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) {
                GridPos p = new GridPos(sacPos.x() + d[0], sacPos.z() + d[1]);
                if (arena.isInBounds(p) && !arena.isOccupied(p)
                        && arena.getTile(p) != null && arena.getTile(p).isWalkable()) {
                    spawnPositions.add(p);
                    warnPositions.add(sacPos);
                    spawned++;
                    break;
                }
            }
        }

        if (spawnPositions.isEmpty()) return null;

        EnemyAction spawn = new EnemyAction.SummonMinions(
            "minecraft:cave_spider", spawnPositions.size(), spawnPositions, 3, 2, 0);
        pendingWarning = new BossWarning(
            self.getEntityId(), BossWarning.WarningType.GROUND_CRACK,
            warnPositions, 1, spawn, 0xFF44AA44);
        return new EnemyAction.Idle();
    }

    // --- Place Egg Sacs (Phase 2 only) ---

    private EnemyAction tryPlaceEggSacs(CombatEntity self, GridArena arena) {
        int needed = Math.min(2, 3 - eggSacs.size());
        if (needed <= 0) return null;

        // Find positions near existing sacs or boss position
        GridPos searchCenter = eggSacs.isEmpty() ? self.getGridPos() : eggSacs.get(0);
        List<GridPos> candidates = findSummonPositionsNear(arena, searchCenter, 3, needed);

        if (candidates.isEmpty()) return null;

        // SummonMinions with "craftics:egg_sac" type — CombatManager creates entities and registers them
        return new EnemyAction.SummonMinions(
            "craftics:egg_sac", candidates.size(), candidates, 1, 0, 0);
    }

    // =========================================================================
    // Utility
    // =========================================================================

    private GridPos findNearestEggSac(GridPos from) {
        GridPos nearest = null;
        int bestDist = Integer.MAX_VALUE;
        for (GridPos sac : eggSacs) {
            int d = from.manhattanDistance(sac);
            if (d < bestDist) {
                bestDist = d;
                nearest = sac;
            }
        }
        return nearest;
    }

    private List<GridPos> getRandomWalkableTiles(GridArena arena, int count) {
        List<GridPos> candidates = new ArrayList<>();
        for (int x = 0; x < arena.getWidth(); x++) {
            for (int z = 0; z < arena.getHeight(); z++) {
                GridPos pos = new GridPos(x, z);
                if (arena.isInBounds(pos) && arena.getTile(pos) != null
                        && arena.getTile(pos).isWalkable() && !arena.isOccupied(pos)) {
                    candidates.add(pos);
                }
            }
        }
        Collections.shuffle(candidates);
        return candidates.subList(0, Math.min(count, candidates.size()));
    }
}
