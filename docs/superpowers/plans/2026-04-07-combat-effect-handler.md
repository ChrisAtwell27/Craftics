# CombatEffectHandler Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a combat lifecycle callback system so addon mods can register fully custom combat effects (damage reflection, death prevention, loot modification, etc.) through the existing EquipmentScanner API.

**Architecture:** Create a `CombatEffectHandler` interface with 24 default no-op callbacks, a `CombatResult` return type for modifying callbacks, and a `CombatEffectContext` for shared state. Combat effects are carried by `StatModifiers` alongside flat stat bonuses, collected during `TrimEffects.scan()`, and invoked at specific hook points throughout `CombatManager`.

**Tech Stack:** Java 21, Fabric 1.21.1

**Spec:** `docs/superpowers/specs/2026-04-07-combat-effect-handler-design.md`

---

## File Structure

### New files (under `src/main/java/com/crackedgames/craftics/`)

| File | Responsibility |
|---|---|
| `api/CombatResult.java` | Return type for modifying callbacks — carries modified value, messages, and cancellation flag |
| `api/CombatEffectHandler.java` | Interface with 24 default no-op lifecycle callbacks |
| `api/CombatEffectContext.java` | Context object providing player, arena, effects, allies, enemies to callbacks |
| `api/NamedCombatEffect.java` | Record pairing a display name with a handler instance |

### Modified files

| File | Change |
|---|---|
| `api/StatModifiers.java` | Add `List<NamedCombatEffect>`, `addCombatEffect()`, `getCombatEffects()` |
| `combat/TrimEffects.java` | Add `combatEffects` field to `TrimScan` record, collect from scanner results |
| `combat/CombatManager.java` | Store active effects list, create context, insert 24 hook invocation points |
| `docs/modding.html` | Add Combat Effects documentation section |

---

## Task 1: CombatResult + NamedCombatEffect

**Files:**
- Create: `src/main/java/com/crackedgames/craftics/api/CombatResult.java`
- Create: `src/main/java/com/crackedgames/craftics/api/NamedCombatEffect.java`

- [ ] **Step 1: Create CombatResult**

```java
package com.crackedgames.craftics.api;

import java.util.List;

public record CombatResult(
    int modifiedValue,
    List<String> messages,
    boolean cancelled
) {
    public static CombatResult unchanged(int originalValue) {
        return new CombatResult(originalValue, List.of(), false);
    }

    public static CombatResult modify(int newValue, String message) {
        return new CombatResult(newValue, List.of(message), false);
    }

    public static CombatResult cancel(String message) {
        return new CombatResult(0, List.of(message), true);
    }
}
```

- [ ] **Step 2: Create NamedCombatEffect**

```java
package com.crackedgames.craftics.api;

public record NamedCombatEffect(String name, CombatEffectHandler handler) {}
```

Note: This will have a compile error referencing `CombatEffectHandler` which doesn't exist yet. That's fine — it's created in Task 2.

- [ ] **Step 3: Verify compilation**

Run: `./gradlew compileJava 2>&1 | tail -10`
Expected: May fail on NamedCombatEffect if CombatEffectHandler isn't created yet. If so, create both Task 1 and Task 2 together before compiling.

---

## Task 2: CombatEffectHandler interface

**Files:**
- Create: `src/main/java/com/crackedgames/craftics/api/CombatEffectHandler.java`

- [ ] **Step 1: Create the interface with all 24 callbacks**

```java
package com.crackedgames.craftics.api;

import com.crackedgames.craftics.combat.CombatEffects;
import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.core.GridPos;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.List;

public interface CombatEffectHandler {

    // --- Core combat lifecycle ---
    default void onCombatStart(CombatEffectContext ctx) {}
    default void onTurnStart(CombatEffectContext ctx) {}
    default void onTurnEnd(CombatEffectContext ctx) {}
    default void onCombatEnd(CombatEffectContext ctx) {}

    // --- Damage dealing ---
    default CombatResult onDealDamage(CombatEffectContext ctx, CombatEntity target, int damage) {
        return CombatResult.unchanged(damage);
    }
    default void onDealKillingBlow(CombatEffectContext ctx, CombatEntity killed) {}
    default void onCrit(CombatEffectContext ctx, CombatEntity target, int damage) {}
    default void onMiss(CombatEffectContext ctx, CombatEntity target) {}

    // --- Damage receiving ---
    default CombatResult onTakeDamage(CombatEffectContext ctx, CombatEntity attacker, int damage) {
        return CombatResult.unchanged(damage);
    }
    default CombatResult onLethalDamage(CombatEffectContext ctx, CombatEntity attacker, int damage) {
        return CombatResult.unchanged(damage);
    }
    default void onDodge(CombatEffectContext ctx, CombatEntity attacker) {}
    default void onBlocked(CombatEffectContext ctx, CombatEntity attacker, int blockedDamage) {}

    // --- Movement ---
    default void onMove(CombatEffectContext ctx, GridPos from, GridPos to, int distance) {}
    default CombatResult onKnockback(CombatEffectContext ctx, CombatEntity source, int distance) {
        return CombatResult.unchanged(distance);
    }

    // --- Allies/Pets ---
    default void onAllyAttack(CombatEffectContext ctx, CombatEntity ally, CombatEntity target, int damage) {}
    default CombatResult onAllyTakeDamage(CombatEffectContext ctx, CombatEntity ally, CombatEntity attacker, int damage) {
        return CombatResult.unchanged(damage);
    }
    default void onAllyKill(CombatEffectContext ctx, CombatEntity ally, CombatEntity killed) {}
    default void onAllyDeath(CombatEffectContext ctx, CombatEntity ally) {}

    // --- Status effects ---
    default CombatResult onEffectApplied(CombatEffectContext ctx, CombatEffects.EffectType effect, int turns) {
        return CombatResult.unchanged(turns);
    }
    default void onEffectExpired(CombatEffectContext ctx, CombatEffects.EffectType effect) {}

    // --- Economy/Progression ---
    default void onLootRoll(CombatEffectContext ctx, List<ItemStack> loot) {}
    default CombatResult onEmeraldGain(CombatEffectContext ctx, int amount) {
        return CombatResult.unchanged(amount);
    }

    // --- Enemy-specific ---
    default void onEnemySpawn(CombatEffectContext ctx, CombatEntity enemy) {}
    default void onBossPhaseChange(CombatEffectContext ctx, CombatEntity boss, int newPhase) {}
}
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew compileJava 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL (CombatEffectContext doesn't exist yet but is only used as a parameter type in default methods — Java compiles the interface definition without resolving parameter types of unimplemented defaults until they're called. Actually, Java DOES need the type to compile. So Task 3 must be done before this compiles.)

---

## Task 3: CombatEffectContext

**Files:**
- Create: `src/main/java/com/crackedgames/craftics/api/CombatEffectContext.java`

- [ ] **Step 1: Create CombatEffectContext**

```java
package com.crackedgames.craftics.api;

import com.crackedgames.craftics.combat.CombatEffects;
import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.TrimEffects;
import com.crackedgames.craftics.core.GridArena;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.List;
import java.util.ArrayList;

public class CombatEffectContext {
    private ServerPlayerEntity player;
    private GridArena arena;
    private CombatEffects playerEffects;
    private TrimEffects.TrimScan trimScan;

    public CombatEffectContext(ServerPlayerEntity player, GridArena arena,
                                CombatEffects playerEffects, TrimEffects.TrimScan trimScan) {
        this.player = player;
        this.arena = arena;
        this.playerEffects = playerEffects;
        this.trimScan = trimScan;
    }

    public ServerPlayerEntity getPlayer() { return player; }
    public GridArena getArena() { return arena; }
    public CombatEffects getPlayerEffects() { return playerEffects; }
    public TrimEffects.TrimScan getTrimScan() { return trimScan; }

    public List<CombatEntity> getAllEnemies() {
        List<CombatEntity> enemies = new ArrayList<>();
        for (CombatEntity e : arena.getOccupants().values()) {
            if (e.isAlive() && !e.isAlly()) enemies.add(e);
        }
        return enemies;
    }

    public List<CombatEntity> getAllAllies() {
        List<CombatEntity> allies = new ArrayList<>();
        for (CombatEntity e : arena.getOccupants().values()) {
            if (e.isAlive() && e.isAlly()) allies.add(e);
        }
        return allies;
    }

    /** Update context at the start of each turn (arena state may have changed). */
    public void update(ServerPlayerEntity player, GridArena arena,
                       CombatEffects playerEffects, TrimEffects.TrimScan trimScan) {
        this.player = player;
        this.arena = arena;
        this.playerEffects = playerEffects;
        this.trimScan = trimScan;
    }
}
```

- [ ] **Step 2: Verify compilation of all 4 new files**

Run: `./gradlew compileJava 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL

---

## Task 4: Extend StatModifiers + TrimScan

**Files:**
- Modify: `src/main/java/com/crackedgames/craftics/api/StatModifiers.java`
- Modify: `src/main/java/com/crackedgames/craftics/combat/TrimEffects.java`

- [ ] **Step 1: Add combat effect storage to StatModifiers**

Add to `StatModifiers.java`:
- New field: `private final List<NamedCombatEffect> combatEffects = new ArrayList<>();`
- New method: `public void addCombatEffect(String name, CombatEffectHandler handler)` — creates a `NamedCombatEffect` and adds to list
- New method: `public List<NamedCombatEffect> getCombatEffects()` — returns the list
- Add imports for `NamedCombatEffect`, `CombatEffectHandler`, `ArrayList`, `List`

- [ ] **Step 2: Add combatEffects field to TrimScan record**

In `TrimEffects.java`, modify the `TrimScan` record (line 55) to add a new field:

Change from:
```java
public record TrimScan(
    Map<Bonus, Integer> bonuses,
    SetBonus setBonus,
    String setName,
    int trimCount,
    Map<String, Integer> materialCounts
)
```

To:
```java
public record TrimScan(
    Map<Bonus, Integer> bonuses,
    SetBonus setBonus,
    String setName,
    int trimCount,
    Map<String, Integer> materialCounts,
    List<NamedCombatEffect> combatEffects
)
```

Add import: `import com.crackedgames.craftics.api.NamedCombatEffect;` and `import java.util.List;` (if not already present) and `import java.util.ArrayList;`

Add convenience method to the record:
```java
public List<NamedCombatEffect> getCombatEffects() {
    return combatEffects != null ? combatEffects : List.of();
}
```

- [ ] **Step 3: Collect combat effects in scan() method**

In `TrimEffects.scan()`, after the existing addon scanner merge block (around line 122), collect combat effects from the scanner results:

After:
```java
if (addonMods.getSetBonus() != SetBonus.NONE && setBonus == SetBonus.NONE) {
    setBonus = addonMods.getSetBonus();
    setName = addonMods.getSetBonusName();
}
```

Add:
```java
List<NamedCombatEffect> combatEffects = new ArrayList<>(addonMods.getCombatEffects());
```

Then update the return statement (line 124) from:
```java
return new TrimScan(bonuses, setBonus, setName, trimCount, materialCounts);
```
To:
```java
return new TrimScan(bonuses, setBonus, setName, trimCount, materialCounts, combatEffects);
```

- [ ] **Step 4: Verify compilation**

Run: `./gradlew compileJava 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL

---

## Task 5: Wire hooks into CombatManager — storage + context + core lifecycle

**Files:**
- Modify: `src/main/java/com/crackedgames/craftics/combat/CombatManager.java`

This is the largest task. We add the active effects list, create the context, and insert hook invocations. Split into sub-steps by hook category.

- [ ] **Step 1: Add fields and initialization**

Near line 578 where `activeTrimScan` is declared, add:
```java
private java.util.List<com.crackedgames.craftics.api.NamedCombatEffect> activeCombatEffects = new java.util.ArrayList<>();
private com.crackedgames.craftics.api.CombatEffectContext effectContext = null;
```

Find where `activeTrimScan` is populated (search for `activeTrimScan = TrimEffects.scan`). After that line, add:
```java
activeCombatEffects = activeTrimScan.getCombatEffects();
effectContext = new com.crackedgames.craftics.api.CombatEffectContext(player, arena, combatEffects, activeTrimScan);
```

- [ ] **Step 2: Add helper methods for invoking hooks**

Add private helper methods at the bottom of CombatManager (near the `onEnemyKilled` method around line 10622):

```java
/** Fire a void combat effect hook. */
private void fireEffectHook(java.util.function.Consumer<com.crackedgames.craftics.api.CombatEffectHandler> hook) {
    if (activeCombatEffects == null || effectContext == null) return;
    for (var effect : activeCombatEffects) {
        hook.accept(effect.handler());
    }
}

/** Fire a CombatResult combat effect hook, chaining modified values. */
private int fireEffectHookChained(int initialValue,
        java.util.function.BiFunction<com.crackedgames.craftics.api.CombatEffectHandler, Integer, com.crackedgames.craftics.api.CombatResult> hook) {
    if (activeCombatEffects == null || effectContext == null) return initialValue;
    int value = initialValue;
    for (var effect : activeCombatEffects) {
        com.crackedgames.craftics.api.CombatResult result = hook.apply(effect.handler(), value);
        value = result.modifiedValue();
        for (String msg : result.messages()) {
            sendMessage(msg);
        }
        if (result.cancelled()) {
            return 0;
        }
    }
    return value;
}
```

- [ ] **Step 3: Insert onCombatStart hook**

Find where PHANTOM set bonus is checked at combat start (around line 3592, in `startEnemyTurn()`). The PHANTOM check is the first thing that runs at combat start. But actually, `onCombatStart` should fire once when combat begins, not on every enemy turn. Find where the combat is first initialized (where `activeTrimScan` is set). After the effect context is created (from Step 1), add:

```java
fireEffectHook(h -> h.onCombatStart(effectContext));
```

- [ ] **Step 4: Insert onTurnStart and onTurnEnd hooks**

Find where the player turn begins — around line 3586 where `"'s turn!"` message is sent. After that message, add:
```java
fireEffectHook(h -> h.onTurnStart(effectContext));
```

Find where the player turn ends — this is `startEnemyTurn()` (line 3591). At the top of `startEnemyTurn()`, before the PHANTOM check, add:
```java
fireEffectHook(h -> h.onTurnEnd(effectContext));
```

- [ ] **Step 5: Insert onCombatEnd hook**

Find where combat victory is handled (around line 8113 where emeralds are awarded). Before the victory processing, add:
```java
fireEffectHook(h -> h.onCombatEnd(effectContext));
```

- [ ] **Step 6: Verify compilation**

Run: `./gradlew compileJava 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL

---

## Task 6: Wire damage dealing hooks into CombatManager

**Files:**
- Modify: `src/main/java/com/crackedgames/craftics/combat/CombatManager.java`

- [ ] **Step 1: Insert onDealDamage hook**

Find where the player's attack damage is calculated and applied to the target. This is in the attack resolution code — search for where `WeaponAbility.applyAbility` is called (around line 2062). After the ability result is computed and before the damage is actually applied/reported, insert:

```java
int hookedDamage = fireEffectHookChained(abilityResult.totalDamage(),
    (h, dmg) -> h.onDealDamage(effectContext, fTarget, dmg));
```

Use `hookedDamage` instead of `abilityResult.totalDamage()` for the final damage applied.

- [ ] **Step 2: Insert onDealKillingBlow hook**

In the `onEnemyKilled` method (line 10623), after the existing kill processing, add:
```java
fireEffectHook(h -> h.onDealKillingBlow(effectContext, enemy));
```

- [ ] **Step 3: Insert onCrit hook**

Find where critical hits are detected — search for "CRITICAL" or "crit" in the attack code. After a crit is detected and bonus damage applied, add:
```java
fireEffectHook(h -> h.onCrit(effectContext, target, critDamage));
```

- [ ] **Step 4: Insert onMiss hook**

Find where the ETHEREAL dodge check happens (line 604). After the dodge message, add:
```java
fireEffectHook(h -> h.onMiss(effectContext, null));
```

Note: `onMiss` is for when the player misses. The ETHEREAL dodge is when an enemy misses the player — that's `onDodge`. Search for any player-attack-miss logic. If none exists (player attacks always hit), skip this hook insertion for now.

- [ ] **Step 5: Verify compilation**

Run: `./gradlew compileJava 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL

---

## Task 7: Wire damage receiving hooks into CombatManager

**Files:**
- Modify: `src/main/java/com/crackedgames/craftics/combat/CombatManager.java`

- [ ] **Step 1: Insert onTakeDamage hook**

In the `damagePlayer` method (line 602), after the defense calculation produces `actual` damage (around line 620) but before applying it to the player (line 627), add:

```java
actual = fireEffectHookChained(actual, (h, dmg) -> h.onTakeDamage(effectContext, null, dmg));
if (actual <= 0) return 0;
```

Note: The `attacker` parameter is null here because `damagePlayer` doesn't receive the attacker. Search for callers of `damagePlayer` to see if the attacker can be threaded through. If not, null is acceptable — addon devs can check the context for enemy positions.

- [ ] **Step 2: Insert onLethalDamage hook**

In `damagePlayer`, after the OCEAN_BLESSING check (around line 641), add a check: if the player's health is at the clamped minimum (1), fire lethal damage hooks:

```java
if (player.getHealth() <= 1) {
    int lethalResult = fireEffectHookChained(actual, (h, dmg) -> h.onLethalDamage(effectContext, null, dmg));
    if (lethalResult == 0) {
        // A handler cancelled the lethal damage — player survives
        return 0;
    }
}
```

- [ ] **Step 3: Insert onDodge hook**

At the ETHEREAL dodge check (line 604-608), after the dodge message and before `return 0`, add:
```java
fireEffectHook(h -> h.onDodge(effectContext, null));
```

- [ ] **Step 4: Insert onBlocked hook**

Search for shield block logic in `damagePlayer`. If the shield brace absorbs damage (the `shieldBraced` section), after the block, add:
```java
fireEffectHook(h -> h.onBlocked(effectContext, null, blockedAmount));
```

If shield blocking is handled elsewhere (not in `damagePlayer`), find and insert there.

- [ ] **Step 5: Verify compilation**

Run: `./gradlew compileJava 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL

---

## Task 8: Wire movement, knockback, ally, status, economy, and enemy hooks

**Files:**
- Modify: `src/main/java/com/crackedgames/craftics/combat/CombatManager.java`

- [ ] **Step 1: Insert onMove hook**

Find where player movement is resolved — search for TERRAFORMER set bonus (line 3864) since that fires after movement. Near that location, after the player's grid position is updated, add:

```java
fireEffectHook(h -> h.onMove(effectContext, fromPos, toPos, distance));
```

- [ ] **Step 2: Insert onKnockback hook**

Search for where the player gets knocked back (not enemy knockback — player knockback). If player knockback exists, insert:
```java
int kbDist = fireEffectHookChained(knockbackDistance, (h, d) -> h.onKnockback(effectContext, source, d));
```

If player knockback doesn't exist as a separate mechanic, skip this hook for now.

- [ ] **Step 3: Insert onAllyAttack hook**

Find where ally/pet damage is calculated (around line 4831 where `totalDamage = ally.getAttackPower() + petBonus`). After the ally's attack is resolved and damage is applied, add:

```java
fireEffectHook(h -> h.onAllyAttack(effectContext, ally, target, totalDamage));
```

- [ ] **Step 4: Insert onAllyKill and onAllyDeath hooks**

After an ally kills an enemy (search for where ally kill is detected), add:
```java
fireEffectHook(h -> h.onAllyKill(effectContext, ally, killed));
```

Where an ally entity dies, add:
```java
fireEffectHook(h -> h.onAllyDeath(effectContext, deadAlly));
```

- [ ] **Step 5: Insert onEffectApplied hook**

In `CombatEffects.addEffect()` — this is in `CombatEffects.java`, not CombatManager. However, `CombatEffects` doesn't have access to the effect context. Instead, hook this in CombatManager where effects are applied to the player. Search for `combatEffects.addEffect` calls in CombatManager. Before each call, fire:
```java
int hookedTurns = fireEffectHookChained(turns, (h, t) -> h.onEffectApplied(effectContext, effectType, t));
```

- [ ] **Step 6: Insert onEffectExpired hook**

In CombatManager, after `combatEffects.tickTurn()` is called (which returns expired effect names), check `combatEffects.getLastExpired()` and fire for each:
```java
for (CombatEffects.EffectType expired : combatEffects.getLastExpired()) {
    fireEffectHook(h -> h.onEffectExpired(effectContext, expired));
}
```

- [ ] **Step 7: Insert onLootRoll hook**

At line 8084/8095 where `levelDef.rollCompletionLoot()` is called, capture the result into a mutable list, then fire:
```java
List<ItemStack> loot = new ArrayList<>(levelDef.rollCompletionLoot());
fireEffectHook(h -> h.onLootRoll(effectContext, loot));
// Use loot instead of the original roll result
```

- [ ] **Step 8: Insert onEmeraldGain hook**

At line 8140 where `emeraldsEarned` is calculated, after the FORTUNE_PEAK check, add:
```java
emeraldsEarned = fireEffectHookChained(emeraldsEarned, (h, amt) -> h.onEmeraldGain(effectContext, amt));
```

- [ ] **Step 9: Insert onEnemySpawn hook**

Find where enemies are spawned into the arena during level setup. After each enemy entity is created and added to the arena, add:
```java
fireEffectHook(h -> h.onEnemySpawn(effectContext, enemy));
```

- [ ] **Step 10: Insert onBossPhaseChange hook**

Search for phase change logic in boss AI. The `EVENT_PHASE_CHANGED` payload is sent at line 4323. If boss phase transitions are handled in CombatManager, insert there. If they're in BossAI classes, the hook will need to be called from CombatManager when it processes the boss's phase change action. Add:
```java
fireEffectHook(h -> h.onBossPhaseChange(effectContext, boss, newPhase));
```

- [ ] **Step 11: Verify compilation**

Run: `./gradlew compileJava 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL

---

## Task 9: Full build verification

**Files:** None (verification only)

- [ ] **Step 1: Full build**

Run: `./gradlew build 2>&1 | tail -15`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Verify all hook points compile cleanly**

Search for any remaining TODO or placeholder comments added during hook insertion:
```bash
grep -rn "TODO\|FIXME\|HACK" src/main/java/com/crackedgames/craftics/api/CombatEffect*.java src/main/java/com/crackedgames/craftics/api/CombatResult.java src/main/java/com/crackedgames/craftics/api/NamedCombatEffect.java
```
Expected: No results (or only intentional ones in CombatEffectHandler javadoc)

- [ ] **Step 3: Verify the callback chain works end-to-end**

Check that `StatModifiers.addCombatEffect()` → `TrimEffects.scan()` → `TrimScan.getCombatEffects()` → `CombatManager.activeCombatEffects` → `fireEffectHook()` forms a complete chain by tracing the code:
```bash
grep -n "addCombatEffect\|getCombatEffects\|activeCombatEffects\|fireEffectHook" src/main/java/com/crackedgames/craftics/api/StatModifiers.java src/main/java/com/crackedgames/craftics/combat/TrimEffects.java src/main/java/com/crackedgames/craftics/combat/CombatManager.java
```

---

## Task 10: Update GitHub Pages docs

**Files:**
- Modify: `docs/modding.html`

- [ ] **Step 1: Add Combat Effects section**

After the Equipment Scanners section in `docs/modding.html`, add a new section documenting:

1. **Overview** — what combat effects are and how they differ from flat stats
2. **CombatEffectHandler interface** — list all 24 callbacks with descriptions
3. **CombatResult** — the three factory methods (unchanged, modify, cancel)
4. **CombatEffectContext** — available getters
5. **Registration** — how to add combat effects via `StatModifiers.addCombatEffect()`
6. **Statefulness** — handlers are per-combat instances, can have fields
7. **Examples** — Thorns Ring, Death Save, Berserker Rage, Emerald Fortune

Update the sidebar navigation to include the new section.

Follow the existing HTML structure and CSS classes from the current `modding.html`.

- [ ] **Step 2: Verify HTML renders**

Open `docs/modding.html` in a browser.

---

## Task Summary

| Task | Description | Dependencies |
|---|---|---|
| 1 | CombatResult + NamedCombatEffect | None |
| 2 | CombatEffectHandler interface | Task 1 (NamedCombatEffect) |
| 3 | CombatEffectContext | None (but Tasks 1-3 must all exist before compile) |
| 4 | Extend StatModifiers + TrimScan | Tasks 1-3 |
| 5 | CombatManager: storage + context + core lifecycle hooks | Task 4 |
| 6 | CombatManager: damage dealing hooks | Task 5 |
| 7 | CombatManager: damage receiving hooks | Task 5 |
| 8 | CombatManager: movement, ally, status, economy, enemy hooks | Task 5 |
| 9 | Full build verification | Tasks 5-8 |
| 10 | GitHub Pages docs | Task 9 |

**Parallelizable:** Tasks 6, 7, 8 can run in parallel (independent hook categories, different CombatManager locations). Tasks 1-3 should be done together (cross-dependencies).
