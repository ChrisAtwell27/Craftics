# Broodmother Rework Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rework the Broodmother boss from a static spawn-spam priority cascade into a state-based predator with interactive egg sacs, ceiling mechanics, and web terrain.

**Architecture:** State-machine AI (Hunting/Nesting) in BroodmotherAI.java, egg sacs as 1HP CombatEntity instances with boss-passthrough pathfinding, web overlays tracked in GridArena and placed as cobweb blocks at Y+1 with grid-based slowness (vanilla cobweb physics counteracted by velocity reset).

**Tech Stack:** Java 21, Fabric mod, Minecraft 1.21.x

**Spec:** `docs/superpowers/specs/2026-04-02-broodmother-rework-design.md`

---

## File Map

| File | Role |
|------|------|
| `src/main/java/.../combat/CombatEntity.java` | Add `passableForBoss` field |
| `src/main/java/.../combat/Pathfinding.java` | Check `passableForBoss` in blocking logic |
| `src/main/java/.../core/GridArena.java` | Web overlay tracking (map of pos→turns) |
| `src/main/java/.../combat/ai/AIRegistry.java` | Register egg sac AI |
| `src/main/java/.../combat/ai/boss/BroodmotherAI.java` | Full rewrite — state machine |
| `src/main/java/.../combat/CombatManager.java` | Egg sac spawning, web placement/breaking, ceiling dive, death hooks, particles, effect cases |

---

### Task 1: CombatEntity — Add passableForBoss flag

Egg sacs must block all entities except the Broodmother. We add a flag that pathfinding checks.

**Files:**
- Modify: `src/main/java/com/crackedgames/craftics/combat/CombatEntity.java`

- [ ] **Step 1: Add the field and accessors**

After the `onCeiling` field block (around line 158), add:

```java
/** If true, boss entities can pathfind through this entity (e.g., egg sacs). */
private boolean passableForBoss = false;
public boolean isPassableForBoss() { return passableForBoss; }
public void setPassableForBoss(boolean v) { this.passableForBoss = v; }
```

---

### Task 2: Pathfinding — Respect passableForBoss

**Files:**
- Modify: `src/main/java/com/crackedgames/craftics/combat/Pathfinding.java`

- [ ] **Step 1: Update isBlockedBy to allow boss passthrough**

Replace the `isBlockedBy` method (lines 123-128):

```java
private static boolean isBlockedBy(GridArena arena, GridPos pos, CombatEntity self) {
    CombatEntity occupant = arena.getOccupant(pos);
    if (occupant == null) return false;
    // Don't block on our own tile
    if (self != null && occupant == self) return false;
    // Boss entities can walk through passable entities (e.g., egg sacs)
    if (self != null && self.isBoss() && occupant.isPassableForBoss()) return false;
    return true;
}
```

- [ ] **Step 2: Update canPlaceSizedEntity for sized boss passthrough**

Replace `canPlaceSizedEntity` (lines 134-143):

```java
private static boolean canPlaceSizedEntity(GridArena arena, GridPos anchor, int entitySize,
                                            CombatEntity self, boolean hasBoat) {
    for (GridPos tile : GridArena.getOccupiedTiles(anchor, entitySize)) {
        if (!arena.isInBounds(tile)) return false;
        var gridTile = arena.getTile(tile);
        if (gridTile == null || !gridTile.isWalkable(hasBoat)) return false;
        if (isBlockedBy(arena, tile, self)) return false;
    }
    return true;
}
```

No change to `canPlaceSizedEntity` itself — it already delegates to `isBlockedBy`. Just verify the method body is unchanged. The `isBlockedBy` update handles it.

- [ ] **Step 3: Build to verify**

```bash
cd "d:/_My Projects/Craftics" && ./gradlew build
```

---

### Task 3: GridArena — Web overlay tracking

**Files:**
- Modify: `src/main/java/com/crackedgames/craftics/core/GridArena.java`

- [ ] **Step 1: Add web overlay map and methods**

Add a new field after `playerGridPos` (line 18):

```java
private final Map<GridPos, Integer> webOverlays = new HashMap<>();
```

Add these methods before the player position section (before line 118):

```java
// --- Web overlay tracking (Broodmother) ---

public boolean hasWebOverlay(GridPos pos) {
    return webOverlays.containsKey(pos);
}

public void setWebOverlay(GridPos pos, int turns) {
    webOverlays.put(pos, turns);
}

public void clearWebOverlay(GridPos pos) {
    webOverlays.remove(pos);
}

/** Tick all web overlays. Returns positions where webs expired this tick. */
public java.util.List<GridPos> tickWebOverlays() {
    java.util.List<GridPos> expired = new java.util.ArrayList<>();
    var it = webOverlays.entrySet().iterator();
    while (it.hasNext()) {
        var entry = it.next();
        int remaining = entry.getValue() - 1;
        if (remaining <= 0) {
            expired.add(entry.getKey());
            it.remove();
        } else {
            entry.setValue(remaining);
        }
    }
    return expired;
}

public Map<GridPos, Integer> getWebOverlays() {
    return webOverlays;
}

public void clearAllWebOverlays() {
    webOverlays.clear();
}
```

- [ ] **Step 2: Build to verify**

```bash
cd "d:/_My Projects/Craftics" && ./gradlew build
```

---

### Task 4: AIRegistry — Register egg sac AI

Egg sacs are CombatEntity instances that never take actions. We need a simple AI that returns Idle.

**Files:**
- Modify: `src/main/java/com/crackedgames/craftics/combat/ai/AIRegistry.java`

- [ ] **Step 1: Register the egg sac AI using PassiveAI**

PassiveAI already exists and returns Idle. In the static block, add after the boss registrations (around line 125):

```java
// === Structures (non-acting entities) ===
register("craftics:egg_sac", passive);
```

This reuses the existing `PassiveAI` reference from line 9 (`EnemyAI passive = new PassiveAI();`).

---

### Task 5: BroodmotherAI — Full rewrite

This is the core task. Complete rewrite of the AI with state machine, all abilities, and ceiling mechanics.

**Files:**
- Modify: `src/main/java/com/crackedgames/craftics/combat/ai/boss/BroodmotherAI.java`

- [ ] **Step 1: Rewrite BroodmotherAI.java**

Replace the entire file contents with:

```java
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
    private int ceilingTurnsRemaining = 0;
    private GridPos ceilingDiveTarget = null;      // player pos snapshot for dive
    private List<GridPos> ceilingWebTargets = null; // web rain targets (P2)

    // --- Web rain (consumed by CombatManager after processing boss turn) ---
    private List<GridPos> pendingWebRain = null;

    /**
     * Called by CombatManager after spawning egg sac entities.
     * Records their grid positions for spawn/tracking logic.
     */
    public void registerEggSac(GridPos pos) {
        eggSacs.add(pos);
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
    public int getCeilingTurnsRemaining() { return ceilingTurnsRemaining; }

    /** Called by CombatManager after boss turn to check for pending web rain placement. */
    public List<GridPos> consumeWebRain() {
        var r = pendingWebRain;
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
        // --- Ceiling sequence (multi-turn, overrides everything) ---
        if (ceilingTurnsRemaining > 0) {
            return handleCeilingSequence(self, arena, playerPos);
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
        ceilingDiveTarget = playerPos; // snapshot player position

        if (isPhaseTwo()) {
            // Hunting Dive: 3-turn sequence (ascend → web rain → dive)
            // Turn N: CeilingAscend (this turn)
            // Turn N+1: handleCeilingSequence places webs + sets dive warning
            // Turn N+2: pendingWarning auto-resolves → CeilingDrop + AreaAttack
            ceilingTurnsRemaining = 2;
            ceilingWebTargets = getRandomWalkableTiles(arena, 5);
        } else {
            // Ceiling Ambush: 2-turn sequence (ascend → dive)
            // Turn N: CeilingAscend (this turn) + set dive warning
            // Turn N+1: pendingWarning auto-resolves → CeilingDrop + AreaAttack
            ceilingTurnsRemaining = 0; // no extra turns needed
            ceilingWebTargets = null;
            List<GridPos> diveTiles = getAreaTiles(arena, playerPos, 1); // 3x3
            // Resolve action includes CeilingDrop so boss lands back on the grid
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
     * Handle turns while on the ceiling. Called by chooseAbility when ceilingTurnsRemaining > 0.
     * Only applies to Phase 2 Hunting Dive (Phase 1 resolves via pendingWarning auto-resolve).
     */
    private EnemyAction handleCeilingSequence(CombatEntity self, GridArena arena, GridPos playerPos) {
        ceilingTurnsRemaining--;

        if (ceilingTurnsRemaining == 1) {
            // Web rain turn (Phase 2, turn N+1)
            // 1) Store web targets for CombatManager to place cobweb blocks
            pendingWebRain = ceilingWebTargets;

            // 2) Update dive target to current player position (tracks player)
            ceilingDiveTarget = playerPos;

            // 3) Set warning for the dive next turn — resolve includes CeilingDrop
            List<GridPos> diveTiles = getAreaTiles(arena, ceilingDiveTarget, 1);
            pendingWarning = new BossWarning(
                self.getEntityId(), BossWarning.WarningType.TILE_HIGHLIGHT,
                diveTiles, 1,
                new EnemyAction.CompositeAction(List.of(
                    new EnemyAction.CeilingDrop(ceilingDiveTarget, 0),
                    new EnemyAction.AreaAttack(ceilingDiveTarget, 1, self.getAttackPower() + 4, "hunting_dive_slam")
                )),
                0xFFFF4444
            );

            ceilingWebTargets = null;
            return new EnemyAction.Idle(); // boss stays on ceiling, CM places webs
        }

        // Fallback: if somehow we get here with counter <= 0, force a dive
        ceilingTurnsRemaining = 0;
        ceilingDiveTarget = null;
        ceilingWebTargets = null;
        return new EnemyAction.CompositeAction(List.of(
            new EnemyAction.CeilingDrop(playerPos, 0),
            new EnemyAction.AreaAttack(playerPos, 1, self.getAttackPower() + 4, "hunting_dive_slam")
        ));
    }

    // --- Pounce ---

    private EnemyAction tryPounce(CombatEntity self, GridArena arena, GridPos playerPos) {
        setCooldown(CD_POUNCE, 2);
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

        // Return a SummonMinions action with "craftics:egg_sac" type
        // CombatManager will handle creating the entities and registering them
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
```

- [ ] **Step 2: Build to verify**

```bash
cd "d:/_My Projects/Craftics" && ./gradlew build
```

---

### Task 6: CombatManager — Egg sac entity spawning at fight start

Replace the current `initEggSacs` call with proper entity creation using turtle egg blocks.

**Files:**
- Modify: `src/main/java/com/crackedgames/craftics/combat/CombatManager.java`

- [ ] **Step 1: Rewrite Broodmother section in initBossSetup**

Find the existing code at line ~5657:
```java
if (ai instanceof BroodmotherAI broodmother) {
    broodmother.initEggSacs(arena);
}
```

Replace with:

```java
if (ai instanceof BroodmotherAI broodmother) {
    spawnBroodmotherEggSacs(broodmother, 3);
}
```

- [ ] **Step 2: Add spawnBroodmotherEggSacs method**

Add this method near `initBossSetup`:

```java
/**
 * Spawn egg sac entities for the Broodmother fight.
 * Egg sacs are 1HP destructible entities using turtle egg blocks.
 * Solid for all entities except the Broodmother (passableForBoss).
 */
private void spawnBroodmotherEggSacs(BroodmotherAI broodmother, int count) {
    ServerWorld world = (ServerWorld) player.getEntityWorld();
    List<GridPos> candidates = new ArrayList<>();
    int w = arena.getWidth(), h = arena.getHeight();
    candidates.add(new GridPos(1, 1));
    candidates.add(new GridPos(w - 2, 1));
    candidates.add(new GridPos(1, h - 2));
    candidates.add(new GridPos(w - 2, h - 2));
    candidates.add(new GridPos(w / 2, 1));
    candidates.add(new GridPos(w / 2, h - 2));
    Collections.shuffle(candidates);

    int placed = 0;
    for (GridPos pos : candidates) {
        if (placed >= count) break;
        if (!arena.isInBounds(pos) || arena.isOccupied(pos)) continue;
        GridTile tile = arena.getTile(pos);
        if (tile == null || !tile.isWalkable()) continue;

        placeEggSacEntity(broodmother, pos, world);
        placed++;
    }
}

/**
 * Place a single egg sac entity at the given position.
 * Creates a tiny invisible armor stand as the "mob", places a turtle egg block,
 * and registers a CombatEntity with 1HP.
 */
private void placeEggSacEntity(BroodmotherAI broodmother, GridPos pos, ServerWorld world) {
    // Create a CombatEntity directly (no actual Minecraft mob needed for static objects)
    // Use a unique negative entity ID to avoid collision with real MC entities
    int fakeEntityId = -(eggSacIdCounter++);
    CombatEntity eggSac = new CombatEntity(fakeEntityId, "craftics:egg_sac", 1, 0, 0, 1, 1);
    eggSac.setGridPos(pos);
    eggSac.setPassableForBoss(true);
    eggSac.setAiOverrideKey("craftics:egg_sac");

    enemies.add(eggSac);
    arena.placeEntity(eggSac);
    broodmother.registerEggSac(pos);

    // Place turtle egg block in the world
    BlockPos bp = arena.gridToBlockPos(pos);
    world.setBlockState(bp.up(1), net.minecraft.block.Blocks.TURTLE_EGG.getDefaultState(),
        net.minecraft.block.Block.NOTIFY_ALL);

    // Spawn placement particles
    world.spawnParticles(net.minecraft.particle.ParticleTypes.ITEM_SLIME,
        bp.getX() + 0.5, bp.getY() + 1.5, bp.getZ() + 0.5,
        8, 0.3, 0.2, 0.3, 0.01);
}
```

- [ ] **Step 3: Add the egg sac ID counter field**

Add near the top of CombatManager with other fields:

```java
private int eggSacIdCounter = 10000; // counter for fake egg sac entity IDs
```

- [ ] **Step 4: Check if CombatEntity constructor accepts those parameters**

The CombatEntity constructor needs to accept (entityId, entityTypeId, hp, atk, def, moveSpeed, size). Check the existing constructor and adapt. If the constructor uses different parameter order, update the `placeEggSacEntity` call to match.

- [ ] **Step 5: Build to verify**

```bash
cd "d:/_My Projects/Craftics" && ./gradlew build
```

---

### Task 7: CombatManager — Egg sac death handling

When an egg sac entity dies, notify the Broodmother AI and remove the turtle egg block.

**Files:**
- Modify: `src/main/java/com/crackedgames/craftics/combat/CombatManager.java`

- [ ] **Step 1: Add Broodmother case to notifyBossOfMinionDeath**

In `notifyBossOfMinionDeath` (around line 5627), after the ShulkerArchitect block (line 5646), add:

```java
// Broodmother: egg sac destroyed
if (ai instanceof BroodmotherAI bm) {
    if ("craftics:egg_sac".equals(deadEntity.getEntityTypeId())) {
        GridPos sacPos = deadEntity.getGridPos();
        bm.onEggSacDestroyed(sacPos);
        sendMessage("§a✦ Egg sac destroyed! Spawn capacity reduced.");
        // Remove turtle egg block from world
        ServerWorld world = (ServerWorld) player.getEntityWorld();
        BlockPos bp = arena.gridToBlockPos(sacPos);
        world.setBlockState(bp.up(1), net.minecraft.block.Blocks.AIR.getDefaultState(),
            net.minecraft.block.Block.NOTIFY_ALL);
        // Destruction particles
        world.spawnParticles(net.minecraft.particle.ParticleTypes.ITEM_SLIME,
            bp.getX() + 0.5, bp.getY() + 1.5, bp.getZ() + 0.5,
            12, 0.3, 0.3, 0.3, 0.02);
        world.spawnParticles(net.minecraft.particle.ParticleTypes.CLOUD,
            bp.getX() + 0.5, bp.getY() + 1.5, bp.getZ() + 0.5,
            5, 0.2, 0.2, 0.2, 0.01);
    }
}
```

- [ ] **Step 2: Handle egg sac SummonMinions in spawnBossMinions for Phase 2 placement**

In `spawnBossMinions` (line ~4460), add a check at the top of the method for egg sac type:

```java
// Special handling: Broodmother egg sac placement (not a real mob spawn)
if ("craftics:egg_sac".equals(sm.entityTypeId())) {
    EnemyAI bossAi = AIRegistry.get(currentEnemy.getAiKey());
    if (bossAi instanceof BroodmotherAI broodmother) {
        ServerWorld world = (ServerWorld) player.getEntityWorld();
        for (GridPos pos : sm.positions()) {
            placeEggSacEntity(broodmother, pos, world);
        }
        sendMessage("§e  The Broodmother lays new eggs!");
    }
    return;
}
```

- [ ] **Step 3: Build to verify**

```bash
cd "d:/_My Projects/Craftics" && ./gradlew build
```

---

### Task 8: CombatManager — CeilingDrop in dispatchBossSubAction + web rain consumption

The ceiling dive resolve action is a `CompositeAction(CeilingDrop, AreaAttack)`. `dispatchBossSubAction` doesn't handle `CeilingDrop` — we need to add it. We also need to consume the Broodmother's pending web rain after processing her turn.

**Files:**
- Modify: `src/main/java/com/crackedgames/craftics/combat/CombatManager.java`

- [ ] **Step 1: Add CeilingDrop case to dispatchBossSubAction**

In `dispatchBossSubAction` (line ~4339), add a case for CeilingDrop after the existing cases:

```java
case EnemyAction.CeilingDrop drop -> {
    if (currentEnemy != null) {
        currentEnemy.setOnCeiling(false);
        currentEnemy.setGridPos(drop.landingPos());
        arena.placeEntity(currentEnemy);
        MobEntity dropMob = currentEnemy.getMobEntity();
        if (dropMob != null) {
            dropMob.setInvisible(false);
            BlockPos landBlock = arena.gridToBlockPos(drop.landingPos());
            dropMob.requestTeleport(landBlock.getX() + 0.5, landBlock.getY() + 1.0, landBlock.getZ() + 0.5);
        }
        sendMessage("§c  " + currentEnemy.getDisplayName() + " slams down from the ceiling!");
    }
}
```

- [ ] **Step 2: Add web rain consumption after boss turn processing**

After the boss's action is processed (at the end of the enemy turn handling, when `enemyTurnState == EnemyTurnState.DONE`), add a check for pending web rain. Find the section where enemy turn completes (around where `enemyTurnState = EnemyTurnState.DONE` is set for boss actions). Add after the action processing:

```java
// Broodmother: consume pending web rain (Hunting Dive phase 2)
if (currentEnemy != null && currentEnemy.isBoss()) {
    EnemyAI bossAi = AIRegistry.get(currentEnemy.getAiKey());
    if (bossAi instanceof BroodmotherAI bm) {
        List<GridPos> webRain = bm.consumeWebRain();
        if (webRain != null && !webRain.isEmpty()) {
            placeWebOverlays(webRain, 2);
            sendMessage("§e  The Broodmother rains webs from the ceiling!");
        }
    }
}
```

The best place for this is right after the main action dispatch switch completes, before `sendSync()`.

- [ ] **Step 3: Build to verify**

```bash
cd "d:/_My Projects/Craftics" && ./gradlew build
```

---

### Task 9: CombatManager — Web overlay placement and removal

Handle cobweb block placement, tick duration, and vanilla physics counteraction.

**Files:**
- Modify: `src/main/java/com/crackedgames/craftics/combat/CombatManager.java`

- [ ] **Step 1: Add web placement method**

Add this helper method:

```java
/**
 * Place cobweb blocks at Y+1 and register web overlays in the arena.
 * Webs apply slowness and last the given number of turns.
 */
private void placeWebOverlays(List<GridPos> positions, int turns) {
    ServerWorld world = (ServerWorld) player.getEntityWorld();
    for (GridPos pos : positions) {
        if (!arena.isInBounds(pos)) continue;
        arena.setWebOverlay(pos, turns);
        BlockPos bp = arena.gridToBlockPos(pos);
        world.setBlockState(bp.up(1), net.minecraft.block.Blocks.COBWEB.getDefaultState(),
            net.minecraft.block.Block.NOTIFY_ALL);
        // Placement particles
        world.spawnParticles(net.minecraft.particle.ParticleTypes.ITEM_SLIME,
            bp.getX() + 0.5, bp.getY() + 1.5, bp.getZ() + 0.5,
            5, 0.3, 0.2, 0.3, 0.01);
    }
}

/**
 * Remove a web overlay at the given position — clears the cobweb block and arena tracking.
 */
private void removeWebOverlay(GridPos pos) {
    arena.clearWebOverlay(pos);
    ServerWorld world = (ServerWorld) player.getEntityWorld();
    BlockPos bp = arena.gridToBlockPos(pos);
    world.setBlockState(bp.up(1), net.minecraft.block.Blocks.AIR.getDefaultState(),
        net.minecraft.block.Block.NOTIFY_ALL);
}
```

- [ ] **Step 2: Tick web overlays at end of each turn cycle**

Find where temporary tile effects are ticked (near line ~3534 where hex trap duration is ticked). Add web overlay ticking:

```java
// Tick web overlay durations
List<GridPos> expiredWebs = arena.tickWebOverlays();
for (GridPos pos : expiredWebs) {
    removeWebOverlay(pos);
}
```

Wait — `removeWebOverlay` already clears from arena. But `tickWebOverlays` already removed the entry. So just remove the blocks:

```java
// Tick web overlay durations
List<GridPos> expiredWebs = arena.tickWebOverlays();
if (!expiredWebs.isEmpty()) {
    ServerWorld world = (ServerWorld) player.getEntityWorld();
    for (GridPos pos : expiredWebs) {
        BlockPos bp = arena.gridToBlockPos(pos);
        world.setBlockState(bp.up(1), net.minecraft.block.Blocks.AIR.getDefaultState(),
            net.minecraft.block.Block.NOTIFY_ALL);
    }
}
```

- [ ] **Step 3: Apply slowness when player moves onto a webbed tile**

In `handleMove` (line ~1157), after the player position is updated and the path is walked, check if the destination has a web overlay:

```java
// Web overlay slowness (Broodmother)
GridPos finalPos = arena.getPlayerGridPos();
if (arena.hasWebOverlay(finalPos)) {
    combatEffects.addEffect(CombatEffects.EffectType.SLOWNESS, 1, 0);
    sendMessage("§7  Cobwebs slow your movement! (-1 speed next turn)");
}
```

- [ ] **Step 4: Counteract vanilla cobweb physics**

After player movement resolves (after the teleport/position-set in handleMove), reset player velocity to prevent vanilla cobweb slowdown:

```java
// Prevent vanilla cobweb physics from causing desync
player.setVelocity(0, 0, 0);
player.velocityModified = true;
```

Add this at the end of the movement resolution, just before `sendSync()`.

- [ ] **Step 5: Build to verify**

```bash
cd "d:/_My Projects/Craftics" && ./gradlew build
```

---

### Task 10: CombatManager — Player web breaking

Allow the player to break webs by spending their attack action on an adjacent or current-tile web.

**Files:**
- Modify: `src/main/java/com/crackedgames/craftics/combat/CombatManager.java`

- [ ] **Step 1: Add web breaking check in handleAttack**

At the beginning of `handleAttack` (line ~1262), before the target entity lookup, add:

```java
// Web breaking: player can break a web on their tile or adjacent tile
// using their attack action if wielding a sword or axe
if (arena.hasWebOverlay(clickedTile)) {
    int webDist = arena.getPlayerGridPos().manhattanDistance(clickedTile);
    if (webDist <= 1) {
        String heldItem = getPlayerMainHandItemId();
        if (heldItem != null && (heldItem.contains("sword") || heldItem.contains("axe"))) {
            removeWebOverlay(clickedTile);
            apRemaining -= 1; // costs 1 AP (attack action)
            sendMessage("§a  You cut through the cobweb!");
            player.getWorld().playSound(null, arena.gridToBlockPos(clickedTile),
                net.minecraft.sound.SoundEvents.BLOCK_COBWEB_BREAK,
                net.minecraft.sound.SoundCategory.BLOCKS, 1.0f, 1.0f);
            sendSync();
            refreshHighlights();
            return;
        }
    }
}
```

Note: Check if `getPlayerMainHandItemId()` exists or if we need to use `player.getMainHandStack().getItem()` to check the item. Adapt as needed based on the existing codebase patterns.

- [ ] **Step 2: Build to verify**

```bash
cd "d:/_My Projects/Craftics" && ./gradlew build
```

---

### Task 11: CombatManager — Fix web_spray effect application

Currently `web_spray` and `web_spray_poison` only spawn particles — they don't apply stun/slowness. Fix this.

**Files:**
- Modify: `src/main/java/com/crackedgames/craftics/combat/CombatManager.java`

- [ ] **Step 1: Add web_spray cases to applyBossAreaEffect**

In `applyBossAreaEffect` (around line 4846), add these cases in the switch:

```java
case "web_spray" -> {
    combatEffects.addEffect(CombatEffects.EffectType.SLOWNESS, 1, 0);
    player.setVelocity(0, 0, 0);
    player.velocityModified = true;
    // Stun: skip next turn
    // Use a dedicated flag or set AP to 0 for next turn
    sendMessage("§7  Webbed! Stunned for 1 turn + slowed!");
}
case "web_spray_poison" -> {
    combatEffects.addEffect(CombatEffects.EffectType.SLOWNESS, 1, 0);
    combatEffects.addEffect(CombatEffects.EffectType.POISON, 2, 0);
    player.setVelocity(0, 0, 0);
    player.velocityModified = true;
    sendMessage("§7  Webbed! Stunned + slowed + poisoned!");
}
```

Note: The existing combat system may handle stun differently for the player vs enemies. Check how player stun works — it might set `apRemaining = 0` and `movePointsRemaining = 0` for the next turn, or use a dedicated `CombatEffects.EffectType.STUN`. Adapt accordingly.

- [ ] **Step 2: Build to verify**

```bash
cd "d:/_My Projects/Craftics" && ./gradlew build
```

---

### Task 12: CombatManager — Ceiling slam and Hunting Dive particles

Add particle effects for the new Broodmother abilities.

**Files:**
- Modify: `src/main/java/com/crackedgames/craftics/combat/CombatManager.java`

- [ ] **Step 1: Add particle cases in spawnAreaAttackParticles**

In `spawnAreaAttackParticles` (around line 4881), add after the Broodmother section (after `web_spray_poison`):

```java
// === Broodmother ceiling attacks ===
case "ceiling_slam" -> {
    world.spawnParticles(net.minecraft.particle.ParticleTypes.CLOUD, cx, cy, cz, 20, spread + 0.5, 0.3, spread + 0.5, 0.02);
    world.spawnParticles(net.minecraft.particle.ParticleTypes.CRIT, cx, cy + 0.5, cz, 15, spread, 0.8, spread, 0.03);
    world.spawnParticles(net.minecraft.particle.ParticleTypes.ITEM_SLIME, cx, cy, cz, 10, spread, 0.5, spread, 0.01);
}
case "hunting_dive_slam" -> {
    world.spawnParticles(net.minecraft.particle.ParticleTypes.CLOUD, cx, cy, cz, 25, spread + 0.5, 0.3, spread + 0.5, 0.03);
    world.spawnParticles(net.minecraft.particle.ParticleTypes.CRIT, cx, cy + 0.5, cz, 20, spread, 1.0, spread, 0.04);
    world.spawnParticles(net.minecraft.particle.ParticleTypes.ITEM_SLIME, cx, cy, cz, 15, spread + 0.3, 0.6, spread + 0.3, 0.02);
    world.spawnParticles(net.minecraft.particle.ParticleTypes.ENCHANTED_HIT, cx, cy + 0.3, cz, 8, spread, 0.4, spread, 0.01);
}
case "pounce" -> {
    world.spawnParticles(net.minecraft.particle.ParticleTypes.CRIT, cx, cy + 0.3, cz, 10, spread, 0.5, spread, 0.02);
    world.spawnParticles(net.minecraft.particle.ParticleTypes.CLOUD, cx, cy, cz, 6, spread, 0.2, spread, 0.0);
}
```

- [ ] **Step 2: Build to verify**

```bash
cd "d:/_My Projects/Craftics" && ./gradlew build
```

---

### Task 13: CombatManager — Clean up webs on combat end

When combat ends (victory or defeat), clear all web overlays and cobweb blocks.

**Files:**
- Modify: `src/main/java/com/crackedgames/craftics/combat/CombatManager.java`

- [ ] **Step 1: Add web cleanup to combat cleanup**

Find the method that cleans up the arena on combat end (likely `endCombat` or `cleanupArena`). Add:

```java
// Clear Broodmother web overlays
if (arena != null) {
    for (GridPos pos : new ArrayList<>(arena.getWebOverlays().keySet())) {
        BlockPos bp = arena.gridToBlockPos(pos);
        world.setBlockState(bp.up(1), net.minecraft.block.Blocks.AIR.getDefaultState(),
            net.minecraft.block.Block.NOTIFY_ALL);
    }
    arena.clearAllWebOverlays();
}
```

Also clean up any remaining turtle egg blocks from egg sacs:

```java
// Clean up remaining egg sac blocks
for (CombatEntity e : enemies) {
    if ("craftics:egg_sac".equals(e.getEntityTypeId()) && e.getGridPos() != null) {
        BlockPos bp = arena.gridToBlockPos(e.getGridPos());
        world.setBlockState(bp.up(1), net.minecraft.block.Blocks.AIR.getDefaultState(),
            net.minecraft.block.Block.NOTIFY_ALL);
    }
}
```

- [ ] **Step 2: Build to verify**

```bash
cd "d:/_My Projects/Craftics" && ./gradlew build
```

---

### Task 14: Verify and fix build

After all changes, do a full build and fix any compilation errors.

**Files:**
- All modified files

- [ ] **Step 1: Full build**

```bash
cd "d:/_My Projects/Craftics" && ./gradlew build 2>&1 | head -80
```

- [ ] **Step 2: Fix any compilation errors**

Common issues to check:
- CombatEntity constructor parameter order
- Import statements for new classes (BroodmotherAI, Blocks, etc.)
- Method visibility (private/public)
- Verify `getPlayerMainHandItemId()` exists or adapt web breaking code
- Verify `CombatEffects.EffectType` has SLOWNESS, POISON, etc.
- Check that `manhattanDistance` exists on GridPos

- [ ] **Step 3: Rebuild after fixes**

```bash
cd "d:/_My Projects/Craftics" && ./gradlew build
```

---

### Task 15: In-game testing checklist

Manual verification in-game.

- [ ] **Step 1: Test egg sac spawning**
- 3 turtle egg blocks appear at fight start
- Egg sacs are visible on the grid
- Player cannot walk through egg sacs
- Player can attack and one-hit destroy egg sacs
- Destroying egg sac removes turtle egg block and shows particles

- [ ] **Step 2: Test Broodmother AI states**
- Starts in HUNTING state (approaches player)
- Transitions to NESTING when egg sac destroyed
- Transitions back to HUNTING when minions alive
- Spawned cave spiders capped at egg sac count

- [ ] **Step 3: Test Ceiling Ambush (Phase 1)**
- Broodmother ascends (goes invisible/off-grid)
- 3x3 red warning tiles appear
- Slams down next turn for ATK+4 damage
- Player can dodge by moving out of warning area

- [ ] **Step 4: Test Hunting Dive (Phase 2)**
- At ≤50% HP, Broodmother gets faster
- Ceiling sequence: ascend → web rain (cobwebs appear) → dive
- Cobwebs apply slowness when walked on
- Cobwebs breakable with sword/axe (costs attack action)
- Cobwebs expire after 2 turns

- [ ] **Step 5: Test web spray effects**
- Web spray applies stun + slowness in Phase 1
- Web spray adds poison in Phase 2

- [ ] **Step 6: Test combat cleanup**
- All turtle eggs and cobwebs removed when fight ends
