# Addon Extensibility API Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Convert every hardcoded combat system into registry-backed lookups and expose a public API so addon mods can register custom weapons, equipment effects, armor sets, trim effects, events, and enchantment bonuses.

**Architecture:** Replace if/else chains and switch statements in `DamageType`, `PlayerCombatStats`, `WeaponAbility`, `TrimEffects`, `EventManager`, and `RandomEvents` with registry lookups. Craftics dogfoods its own API by registering vanilla content at init. New files live under `api/` and `api/registry/`. Existing enums (`DamageType`, `Affinity`, `SetBonus`, etc.) stay unchanged — addons map into existing types.

**Tech Stack:** Java 21, Fabric 1.21.1, Minecraft modding API

**Spec:** `docs/superpowers/specs/2026-04-07-addon-extensibility-api-design.md`

---

## File Structure

### New files (under `src/main/java/com/crackedgames/craftics/`)

| File | Responsibility |
|---|---|
| `api/registry/WeaponRegistry.java` | `Map<Item, WeaponEntry>` — register, get, fallback for unknown items |
| `api/registry/WeaponEntry.java` | Immutable weapon data record + Builder inner class |
| `api/WeaponAbilityHandler.java` | `@FunctionalInterface` for custom weapon ability logic |
| `api/Abilities.java` | Chainable building blocks: bleed, sweep, stun, knockback, aoe, etc. |
| `api/registry/ArmorSetRegistry.java` | `Map<String, ArmorSetEntry>` — register, getBonus |
| `api/registry/ArmorSetEntry.java` | Armor set bonus data record + Builder |
| `api/registry/TrimPatternRegistry.java` | `Map<String, TrimPatternEntry>` — register, get |
| `api/registry/TrimPatternEntry.java` | Per-piece stat + set bonus data record |
| `api/registry/TrimMaterialRegistry.java` | `Map<String, TrimMaterialEntry>` — register, get |
| `api/registry/TrimMaterialEntry.java` | Material stat bonus data record |
| `api/EquipmentScanner.java` | `@FunctionalInterface` for addon equipment scanning |
| `api/StatModifiers.java` | Mutable accumulator of `Bonus` → int + optional `SetBonus` |
| `api/registry/EquipmentScannerRegistry.java` | `Map<String, EquipmentScanner>` — register, scanAll |
| `api/registry/EventRegistry.java` | Ordered list of `EventEntry` — register, roll, getById |
| `api/registry/EventEntry.java` | Event data record (id, displayName, probability, handler, etc.) |
| `api/EventHandler.java` | `@FunctionalInterface` for custom event logic |
| `api/EventTemplates.java` | Pre-built event handler factories: gamble, giveReward, ambush, trader |
| `api/registry/EnchantmentRegistry.java` | `Map<String, EnchantmentEffectHandler>` — register, applyAll |
| `api/EnchantmentEffectHandler.java` | `@FunctionalInterface` for enchantment stat bonuses |
| `api/EnchantmentContext.java` | Context passed to enchantment handlers (level, player, modifiers) |
| `api/VanillaWeapons.java` | All vanilla weapon registrations (extracted from PlayerCombatStats/WeaponAbility) |
| `api/VanillaContent.java` | Registers all vanilla armor sets, trims, events, enchantments |

### Modified files

| File | Change |
|---|---|
| `api/CrafticsAPI.java` | Add all new `registerXxx()` methods |
| `combat/DamageType.java` | Remove `fromWeapon()` + `getArmorSetBonus()`, delegate to registries |
| `combat/PlayerCombatStats.java` | Remove if/else chains for attack power/range, use WeaponRegistry |
| `combat/WeaponAbility.java` | Slim down to delegate to WeaponRegistry + keep `findAdjacentEnemies` utility |
| `combat/TrimEffects.java` | Replace pattern/material switches with registry lookups, merge equipment scanner results |
| `combat/EventManager.java` | Refactor `rollEvent()` to use EventRegistry |
| `combat/RandomEvents.java` | Extract handlers into EventHandler implementations |
| `CrafticsMod.java` | Call `VanillaWeapons.registerAll()` + `VanillaContent.registerAll()` at init |

---

## Task 1: Weapon Registry + WeaponEntry + WeaponAbilityHandler

**Files:**
- Create: `src/main/java/com/crackedgames/craftics/api/registry/WeaponRegistry.java`
- Create: `src/main/java/com/crackedgames/craftics/api/registry/WeaponEntry.java`
- Create: `src/main/java/com/crackedgames/craftics/api/WeaponAbilityHandler.java`

- [ ] **Step 1: Create WeaponAbilityHandler interface**

```java
package com.crackedgames.craftics.api;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.PlayerProgression;
import com.crackedgames.craftics.combat.WeaponAbility;
import com.crackedgames.craftics.core.GridArena;
import net.minecraft.server.network.ServerPlayerEntity;

@FunctionalInterface
public interface WeaponAbilityHandler {
    WeaponAbility.AttackResult apply(ServerPlayerEntity player, CombatEntity target,
                                      GridArena arena, int baseDamage,
                                      PlayerProgression.PlayerStats stats, int luckPoints);

    default WeaponAbilityHandler and(WeaponAbilityHandler next) {
        WeaponAbilityHandler first = this;
        return (player, target, arena, baseDamage, stats, luckPoints) -> {
            WeaponAbility.AttackResult r1 = first.apply(player, target, arena, baseDamage, stats, luckPoints);
            WeaponAbility.AttackResult r2 = next.apply(player, target, arena, r1.totalDamage(), stats, luckPoints);
            var msgs = new java.util.ArrayList<>(r1.messages());
            msgs.addAll(r2.messages());
            var extras = new java.util.ArrayList<>(r1.extraTargets());
            extras.addAll(r2.extraTargets());
            return new WeaponAbility.AttackResult(r2.totalDamage(), msgs, extras);
        };
    }
}
```

- [ ] **Step 2: Create WeaponEntry record with Builder**

```java
package com.crackedgames.craftics.api.registry;

import com.crackedgames.craftics.api.WeaponAbilityHandler;
import com.crackedgames.craftics.combat.DamageType;
import net.minecraft.item.Item;
import org.jetbrains.annotations.Nullable;

import java.util.function.IntSupplier;

public record WeaponEntry(
    Item item,
    DamageType damageType,
    IntSupplier attackPower,
    int apCost,
    int range,
    boolean isRanged,
    double breakChance,
    @Nullable WeaponAbilityHandler ability
) {
    public static Builder builder(Item item) { return new Builder(item); }

    public static class Builder {
        private final Item item;
        private DamageType damageType = DamageType.PHYSICAL;
        private IntSupplier attackPower = () -> 1;
        private int apCost = 1;
        private int range = 1;
        private boolean isRanged = false;
        private double breakChance = 0.0;
        private WeaponAbilityHandler ability = null;

        public Builder(Item item) { this.item = item; }

        public Builder damageType(DamageType dt) { this.damageType = dt; return this; }
        public Builder attackPower(int power) { this.attackPower = () -> power; return this; }
        public Builder attackPower(IntSupplier supplier) { this.attackPower = supplier; return this; }
        public Builder apCost(int cost) { this.apCost = cost; return this; }
        public Builder range(int range) { this.range = range; return this; }
        public Builder ranged(boolean ranged) { this.isRanged = ranged; return this; }
        public Builder breakChance(double chance) { this.breakChance = chance; return this; }
        public Builder ability(WeaponAbilityHandler handler) { this.ability = handler; return this; }

        public WeaponEntry build() {
            return new WeaponEntry(item, damageType, attackPower, apCost, range, isRanged, breakChance, ability);
        }
    }
}
```

- [ ] **Step 3: Create WeaponRegistry**

```java
package com.crackedgames.craftics.api.registry;

import com.crackedgames.craftics.combat.DamageType;
import net.minecraft.item.Item;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class WeaponRegistry {
    private static final Map<Item, WeaponEntry> REGISTRY = new ConcurrentHashMap<>();
    private static final WeaponEntry DEFAULT = new WeaponEntry(
        null, DamageType.PHYSICAL, () -> 1, 1, 1, false, 0.0, null
    );

    private WeaponRegistry() {}

    public static void register(Item item, WeaponEntry entry) {
        REGISTRY.put(item, entry);
    }

    public static WeaponEntry get(Item item) {
        return REGISTRY.getOrDefault(item, DEFAULT);
    }

    @Nullable
    public static WeaponEntry getOrNull(Item item) {
        return REGISTRY.get(item);
    }

    public static boolean isRegistered(Item item) {
        return REGISTRY.containsKey(item);
    }

    public static DamageType getDamageType(Item item) {
        return get(item).damageType();
    }

    public static int getAttackPower(Item item) {
        return get(item).attackPower().getAsInt();
    }

    public static int getApCost(Item item) {
        return get(item).apCost();
    }

    public static int getRange(Item item) {
        return get(item).range();
    }

    public static double getBreakChance(Item item) {
        return get(item).breakChance();
    }

    public static boolean hasAbility(Item item) {
        return get(item).ability() != null;
    }
}
```

- [ ] **Step 4: Verify compilation**

Run: `./gradlew compileJava 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL (new files compile independently, no callers yet)

- [ ] **Step 5: Commit**

```
feat(api): add WeaponRegistry, WeaponEntry, and WeaponAbilityHandler

Core registry infrastructure for extensible weapon registration.
Addons can register custom weapons with damage type, attack power,
AP cost, range, break chance, and ability handlers.
```

---

## Task 2: Abilities building blocks

**Files:**
- Create: `src/main/java/com/crackedgames/craftics/api/Abilities.java`

- [ ] **Step 1: Create Abilities class with building block methods**

Extract the reusable combat patterns from `WeaponAbility.applyAbility()` into composable `WeaponAbilityHandler` factories. Each method returns a `WeaponAbilityHandler` that can be chained with `.and()`.

Building blocks to extract:
- `bleed()` — from sword logic (lines 68-73 of WeaponAbility.java): apply bleed stacks based on Sharpness level
- `sweepAdjacent(baseChance, bonusPerPoint)` — from sword sweep logic (lines 191-203): chance to hit adjacent enemies for half damage
- `armorIgnore(baseChance, bonusPerPoint)` — from axe logic (lines 221-236): chance to bypass defense
- `stun(baseChance, bonusPerPoint)` — from blunt logic (lines 240-248): chance to stun target
- `knockbackDirection(distance)` — from water/breeze rod logic: push target away from player
- `aoe(radius, damageMultiplier)` — from mace shockwave logic (lines 358-370): splash to adjacent
- `applyEffect(type, turns, amplifier)` — generic: inflict a CombatEffect on target
- `pierce()` — from spear logic (lines 283-300): hit enemy behind target in line
- `fireDamage(bonusDmg)` — from blaze rod logic (lines 437-443): set fire + bonus damage

Each building block should accept the same `(player, target, arena, baseDamage, stats, luckPoints)` signature and return an `AttackResult`. Use the `findAdjacentEnemies` helper (copy from WeaponAbility since it's a static utility).

Full code: implement all building blocks listed above as `public static WeaponAbilityHandler` factory methods. Reference the exact logic from the current `WeaponAbility.applyAbility()` method for each.

- [ ] **Step 2: Verify compilation**

Run: `./gradlew compileJava 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```
feat(api): add Abilities building blocks for weapon ability composition

Composable WeaponAbilityHandler factories: bleed, sweep, armorIgnore,
stun, knockback, aoe, applyEffect, pierce, fireDamage. Chainable
with .and() for combining multiple effects.
```

---

## Task 3: Register all vanilla weapons + refactor DamageType/PlayerCombatStats/WeaponAbility

**Files:**
- Create: `src/main/java/com/crackedgames/craftics/api/VanillaWeapons.java`
- Modify: `src/main/java/com/crackedgames/craftics/combat/DamageType.java`
- Modify: `src/main/java/com/crackedgames/craftics/combat/PlayerCombatStats.java`
- Modify: `src/main/java/com/crackedgames/craftics/combat/WeaponAbility.java`
- Modify: `src/main/java/com/crackedgames/craftics/CrafticsMod.java`

- [ ] **Step 1: Create VanillaWeapons.java**

Register every vanilla weapon that currently appears in the if/else chains of `PlayerCombatStats.getAttackPower()`, `DamageType.fromWeapon()`, `WeaponAbility.getAttackCost()`, `WeaponAbility.applyAbility()`, `WeaponAbility.getBreakChance()`, and `PlayerCombatStats.getWeaponRange()`.

For each weapon, use `WeaponEntry.builder()` with:
- `damageType` from `DamageType.fromWeapon()` logic
- `attackPower` from `PlayerCombatStats.getAttackPower()` — use `IntSupplier` referencing `CrafticsMod.CONFIG.dmgXxx()` for config-backed weapons, fixed int for non-config weapons (hoes, shovels)
- `apCost` from `WeaponAbility.getAttackCost()`
- `range` from `PlayerCombatStats.getWeaponRange()` — 1 for melee, specific values for bow/crossbow/trident
- `isRanged` — true for bow, crossbow, trident
- `breakChance` from `WeaponAbility.getBreakChance()`
- `ability` — for the initial registration, use `null` (abilities will be wired in Task 4 after Abilities class exists)

This is a large file (~60+ weapon registrations). Group by weapon category with comments: swords, axes, mace, trident, bow, crossbow, blunt (stick/bamboo/blaze rod/breeze rod), corals (live/dead/fans), hoes, shovels.

Example pattern for one weapon:
```java
WeaponRegistry.register(Items.DIAMOND_SWORD, WeaponEntry.builder(Items.DIAMOND_SWORD)
    .damageType(DamageType.SLASHING)
    .attackPower(() -> CrafticsMod.CONFIG.dmgDiamondSword())
    .apCost(1).range(1)
    .build());
```

- [ ] **Step 2: Refactor DamageType.fromWeapon()**

Replace the entire `fromWeapon(Item weapon)` method body with:
```java
public static DamageType fromWeapon(Item weapon) {
    return WeaponRegistry.getDamageType(weapon);
}
```

Keep the `isCoral(Item)` method for now (it's used elsewhere as a category check). Add a `// TODO: move to WeaponRegistry category check` comment.

- [ ] **Step 3: Refactor PlayerCombatStats.getAttackPower()**

Replace the entire if/else chain with:
```java
public static int getAttackPower(ServerPlayerEntity player) {
    Item weapon = player.getMainHandStack().getItem();
    return WeaponRegistry.getAttackPower(weapon);
}
```

The fist fallback is handled by `WeaponRegistry.DEFAULT` which returns 1. Update the default to reference config:
In `WeaponRegistry`, change the DEFAULT attackPower to `() -> com.crackedgames.craftics.CrafticsMod.CONFIG.dmgFist()`.

- [ ] **Step 4: Refactor WeaponAbility.getAttackCost()**

Replace with:
```java
public static int getAttackCost(Item weapon) {
    return WeaponRegistry.getApCost(weapon);
}
```

- [ ] **Step 5: Refactor PlayerCombatStats.getWeaponRange()**

Replace with:
```java
public static int getWeaponRange(ServerPlayerEntity player) {
    Item weapon = player.getMainHandStack().getItem();
    return WeaponRegistry.getRange(weapon);
}
```

Note: The bow range currently adds `getBowPowerRange(player)` dynamically based on Power enchant level. This needs to stay dynamic. For the bow registration, set the base range to 3 and handle the Power bonus in `CombatManager` where range is consumed (it already calls `getWeaponRange()` then adds enchant bonuses). Alternatively, `WeaponEntry` could take an `IntSupplier` for range too — but simpler to just keep the enchant bonus addition in the caller for now.

Actually, since bow range depends on the specific player's enchants, the cleanest approach: keep `getWeaponRange()` in `PlayerCombatStats` but have it read base range from registry and add enchant bonuses:
```java
public static int getWeaponRange(ServerPlayerEntity player) {
    Item weapon = player.getMainHandStack().getItem();
    int baseRange = WeaponRegistry.getRange(weapon);
    if (weapon == Items.BOW && hasArrows(player)) {
        return baseRange + getBowPowerRange(player);
    }
    if (weapon == Items.CROSSBOW && hasArrows(player)) {
        return baseRange; // RANGE_CROSSBOW_ROOK is already set as range in registry
    }
    return baseRange;
}
```

- [ ] **Step 6: Refactor WeaponAbility.getBreakChance() and hasAbility()**

Replace `getBreakChance()`:
```java
public static double getBreakChance(Item item) {
    return WeaponRegistry.getBreakChance(item);
}
```

Replace `hasAbility()`:
```java
public static boolean hasAbility(Item item) {
    return WeaponRegistry.hasAbility(item);
}
```

Remove the private helper methods `isSword()`, `isAxe()`, `isSpear()` from `WeaponAbility` — they're no longer needed once abilities are wired through the registry.

- [ ] **Step 7: Wire VanillaWeapons.registerAll() into CrafticsMod.onInitialize()**

In `CrafticsMod.onInitialize()`, add after the config load and before event registrations:
```java
com.crackedgames.craftics.api.VanillaWeapons.registerAll();
```

- [ ] **Step 8: Verify compilation**

Run: `./gradlew compileJava 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL

- [ ] **Step 9: Commit**

```
refactor: replace weapon if/else chains with WeaponRegistry lookups

DamageType.fromWeapon(), PlayerCombatStats.getAttackPower(),
WeaponAbility.getAttackCost/hasAbility/getBreakChance, and
PlayerCombatStats.getWeaponRange() now delegate to WeaponRegistry.
All vanilla weapons registered in VanillaWeapons.java.
```

---

## Task 4: Wire vanilla weapon abilities into registry

**Files:**
- Modify: `src/main/java/com/crackedgames/craftics/api/VanillaWeapons.java`
- Modify: `src/main/java/com/crackedgames/craftics/combat/WeaponAbility.java`

- [ ] **Step 1: Add ability handlers to VanillaWeapons registrations**

For each weapon category, set the `.ability()` using either building blocks from `Abilities` or inline lambdas for complex logic:

**Swords:** Each sword's ability is complex (bleed + smite + bane + knockback + sweeping edge + per-weapon unique effects). Create a single `SwordAbilityHandler` lambda that encapsulates the full sword logic from `WeaponAbility.applyAbility()` lines 67-216. Register it on all sword entries. Per-weapon unique effects (diamond crit, netherite execute) can be checked inside via `player.getMainHandStack().getItem()`.

**Axes:** Extract axe armor-ignore logic (lines 218-236) into a handler.

**Mace:** Extract full mace logic (lines 302-434) — density, breach, wind burst, base shockwave, knockback.

**Blunt (stick/bamboo):** Stun chance handler.

**Blaze Rod:** Fire + stun handler.

**Breeze Rod:** Knockback + stun handler.

**Trident:** Water knockback + soaked handler.

**Corals:** Each coral type has unique effects — register individual abilities per coral item.

**Crossbow:** Pierce-through + multishot + piercing bleed handler.

**Bow:** Handled elsewhere (CombatManager applies bow effects directly). Register with `null` ability.

**Hoes/Shovels:** No abilities. Register with `null`.

- [ ] **Step 2: Refactor WeaponAbility.applyAbility() to delegate to registry**

Replace the entire body of `applyAbility()` with:
```java
public static AttackResult applyAbility(ServerPlayerEntity player, Item weapon,
                                         CombatEntity target, GridArena arena,
                                         int baseDamage,
                                         PlayerProgression.PlayerStats playerStats,
                                         int luckPoints) {
    WeaponEntry entry = WeaponRegistry.get(weapon);
    if (entry.ability() == null) {
        return new AttackResult(baseDamage, "");
    }
    return entry.ability().apply(player, target, arena, baseDamage, playerStats, luckPoints);
}
```

Keep the `findAdjacentEnemies()` utility method (make it `public static` so `Abilities` and addon handlers can use it). Keep the `AttackResult` record definition. Remove all the old weapon-specific logic.

- [ ] **Step 3: Verify compilation**

Run: `./gradlew compileJava 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```
refactor: wire all vanilla weapon abilities through WeaponRegistry

WeaponAbility.applyAbility() now delegates to registered ability
handlers. All vanilla weapon logic preserved in VanillaWeapons.java.
WeaponAbility.java reduced from 731 lines to ~50 (utility methods only).
```

---

## Task 5: Armor Set Registry

**Files:**
- Create: `src/main/java/com/crackedgames/craftics/api/registry/ArmorSetRegistry.java`
- Create: `src/main/java/com/crackedgames/craftics/api/registry/ArmorSetEntry.java`
- Modify: `src/main/java/com/crackedgames/craftics/combat/DamageType.java`
- Modify: `src/main/java/com/crackedgames/craftics/combat/PlayerCombatStats.java`

- [ ] **Step 1: Create ArmorSetEntry record**

```java
package com.crackedgames.craftics.api.registry;

import com.crackedgames.craftics.combat.DamageType;
import java.util.HashMap;
import java.util.Map;

public record ArmorSetEntry(
    String armorSetId,
    Map<DamageType, Integer> damageTypeBonuses,
    int speedBonus,
    int apBonus,
    int defenseBonus,
    int attackBonus,
    int apCostReduction,
    String description
) {
    public static Builder builder(String armorSetId) { return new Builder(armorSetId); }

    public int getDamageTypeBonus(DamageType type) {
        return damageTypeBonuses.getOrDefault(type, 0);
    }

    public static class Builder {
        private final String armorSetId;
        private final Map<DamageType, Integer> damageTypeBonuses = new HashMap<>();
        private int speedBonus = 0, apBonus = 0, defenseBonus = 0, attackBonus = 0, apCostReduction = 0;
        private String description = "";

        public Builder(String id) { this.armorSetId = id; }
        public Builder damageBonus(DamageType type, int bonus) { damageTypeBonuses.put(type, bonus); return this; }
        public Builder allDamageBonus(int bonus) { for (DamageType t : DamageType.values()) damageTypeBonuses.put(t, bonus); return this; }
        public Builder speedBonus(int v) { this.speedBonus = v; return this; }
        public Builder apBonus(int v) { this.apBonus = v; return this; }
        public Builder defenseBonus(int v) { this.defenseBonus = v; return this; }
        public Builder attackBonus(int v) { this.attackBonus = v; return this; }
        public Builder apCostReduction(int v) { this.apCostReduction = v; return this; }
        public Builder description(String d) { this.description = d; return this; }
        public ArmorSetEntry build() {
            return new ArmorSetEntry(armorSetId, Map.copyOf(damageTypeBonuses),
                speedBonus, apBonus, defenseBonus, attackBonus, apCostReduction, description);
        }
    }
}
```

- [ ] **Step 2: Create ArmorSetRegistry**

```java
package com.crackedgames.craftics.api.registry;

import com.crackedgames.craftics.combat.DamageType;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ArmorSetRegistry {
    private static final Map<String, ArmorSetEntry> REGISTRY = new ConcurrentHashMap<>();

    private ArmorSetRegistry() {}

    public static void register(ArmorSetEntry entry) {
        REGISTRY.put(entry.armorSetId(), entry);
    }

    public static ArmorSetEntry get(String armorSetId) {
        return REGISTRY.get(armorSetId);
    }

    public static int getDamageTypeBonus(String armorSet, DamageType type) {
        ArmorSetEntry entry = REGISTRY.get(armorSet);
        return entry != null ? entry.getDamageTypeBonus(type) : 0;
    }

    public static int getSpeedBonus(String armorSet) {
        ArmorSetEntry entry = REGISTRY.get(armorSet);
        return entry != null ? entry.speedBonus() : 0;
    }

    public static int getApBonus(String armorSet) {
        ArmorSetEntry entry = REGISTRY.get(armorSet);
        return entry != null ? entry.apBonus() : 0;
    }

    public static int getDefenseBonus(String armorSet) {
        ArmorSetEntry entry = REGISTRY.get(armorSet);
        return entry != null ? entry.defenseBonus() : 0;
    }

    public static int getAttackBonus(String armorSet) {
        ArmorSetEntry entry = REGISTRY.get(armorSet);
        return entry != null ? entry.attackBonus() : 0;
    }

    public static int getApCostReduction(String armorSet) {
        ArmorSetEntry entry = REGISTRY.get(armorSet);
        return entry != null ? entry.apCostReduction() : 0;
    }

    public static String getDescription(String armorSet) {
        ArmorSetEntry entry = REGISTRY.get(armorSet);
        return entry != null ? entry.description() : "";
    }
}
```

- [ ] **Step 3: Register vanilla armor sets in VanillaContent**

Create `VanillaContent.java` (partially — armor sets first). Register:
- leather: PHYSICAL +2, speed +2, ap +1, description from `getSetBonusDescription`
- chainmail: SLASHING +2, speed +1, apCostReduction 1
- iron: CLEAVING +2, defense +2
- gold: SPECIAL +2
- diamond: BLUNT +2, defense +3, attack +1
- netherite: allDamageBonus(1), defense +4, attack +2
- turtle: WATER +3

- [ ] **Step 4: Refactor DamageType.getArmorSetBonus()**

Replace with:
```java
public static int getArmorSetBonus(String armorSet, DamageType type) {
    return ArmorSetRegistry.getDamageTypeBonus(armorSet, type);
}
```

- [ ] **Step 5: Refactor PlayerCombatStats armor set methods**

Replace `getSetSpeedBonus()`, `getSetApBonus()`, `getSetDefenseBonus()`, `getSetAttackBonus()`, `getSetApCostReduction()`, `getSetBonusDescription()` to delegate to `ArmorSetRegistry`:

```java
public static int getSetSpeedBonus(ServerPlayerEntity player) {
    return ArmorSetRegistry.getSpeedBonus(getArmorSet(player));
}
public static int getSetApBonus(ServerPlayerEntity player) {
    return ArmorSetRegistry.getApBonus(getArmorSet(player));
}
// ... etc for defense, attack, apCostReduction, description
```

Keep `getArmorSet()` method (armor detection logic) unchanged — it still derives the set name from equipped items. Keep `hasTurtleSet()`, `hasNetheriteSet()`, `hasGoldSet()` as convenience wrappers.

- [ ] **Step 6: Wire VanillaContent into CrafticsMod.onInitialize()**

Add after `VanillaWeapons.registerAll()`:
```java
com.crackedgames.craftics.api.VanillaContent.registerAll();
```

- [ ] **Step 7: Verify compilation**

Run: `./gradlew compileJava 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL

- [ ] **Step 8: Commit**

```
refactor: replace armor set switch statements with ArmorSetRegistry

All vanilla armor sets registered via ArmorSetEntry builders.
DamageType.getArmorSetBonus() and PlayerCombatStats armor set methods
now delegate to ArmorSetRegistry.
```

---

## Task 6: Trim Pattern + Material Registries

**Files:**
- Create: `src/main/java/com/crackedgames/craftics/api/registry/TrimPatternRegistry.java`
- Create: `src/main/java/com/crackedgames/craftics/api/registry/TrimPatternEntry.java`
- Create: `src/main/java/com/crackedgames/craftics/api/registry/TrimMaterialRegistry.java`
- Create: `src/main/java/com/crackedgames/craftics/api/registry/TrimMaterialEntry.java`
- Modify: `src/main/java/com/crackedgames/craftics/combat/TrimEffects.java`
- Modify: `src/main/java/com/crackedgames/craftics/api/VanillaContent.java`

- [ ] **Step 1: Create TrimPatternEntry and TrimPatternRegistry**

`TrimPatternEntry` record: `patternId`, `perPieceStat` (Bonus), `perPieceDescription`, `setBonus` (SetBonus), `setBonusName`, `setBonusDescription`.

`TrimPatternRegistry`: `Map<String, TrimPatternEntry>`, methods for `register()`, `get()`, `getPerPieceBonus()`, `getSetBonus()`, `getSetBonusName()`, `getPerPieceDescription()`, `getSetBonusDescription()`.

- [ ] **Step 2: Create TrimMaterialEntry and TrimMaterialRegistry**

`TrimMaterialEntry` record: `materialId`, `stat` (Bonus), `valuePerPiece` (int), `description`.

`TrimMaterialRegistry`: `Map<String, TrimMaterialEntry>`, methods for `register()`, `get()`, `getMaterialBonus()`, `getMaterialValue()`, `getDescription()`.

- [ ] **Step 3: Register vanilla trims in VanillaContent**

Register all 17 patterns (sentry, dune, coast, wild, ward, eye, vex, tide, snout, rib, spire, wayfinder, shaper, silence, raiser, host, flow, bolt) with their per-piece stats, set bonuses, names, and descriptions — pulled directly from the current switch statements in `TrimEffects.java`.

Register all 11 materials (iron, copper, gold, lapis, emerald, diamond, netherite, redstone, amethyst, quartz, resin) with their stats, values, and descriptions.

- [ ] **Step 4: Refactor TrimEffects switch statements**

Replace `getPerPieceBonus(String patternId)`:
```java
private static Bonus getPerPieceBonus(String patternId) {
    TrimPatternEntry entry = TrimPatternRegistry.get(patternId);
    return entry != null ? entry.perPieceStat() : null;
}
```

Replace `getMaterialBonus(String materialId)`:
```java
private static Bonus getMaterialBonus(String materialId) {
    TrimMaterialEntry entry = TrimMaterialRegistry.get(materialId);
    return entry != null ? entry.stat() : null;
}
```

Replace the material value logic (quartz special case):
```java
int matValue = TrimMaterialRegistry.getMaterialValue(materialId);
```

Replace `getSetBonus(String patternId)`:
```java
private static SetBonus getSetBonus(String patternId) {
    TrimPatternEntry entry = TrimPatternRegistry.get(patternId);
    return entry != null ? entry.setBonus() : SetBonus.NONE;
}
```

Replace `getSetBonusName()`, `getPerPieceDescription()`, `getMaterialDescription()`, `getSetBonusDescription()` similarly.

- [ ] **Step 5: Verify compilation**

Run: `./gradlew compileJava 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```
refactor: replace trim switch statements with TrimPatternRegistry/TrimMaterialRegistry

All 17 vanilla patterns and 11 materials registered via records.
TrimEffects now delegates to registries for per-piece, material,
and set bonus lookups.
```

---

## Task 7: Equipment Scanner Registry

**Files:**
- Create: `src/main/java/com/crackedgames/craftics/api/EquipmentScanner.java`
- Create: `src/main/java/com/crackedgames/craftics/api/StatModifiers.java`
- Create: `src/main/java/com/crackedgames/craftics/api/registry/EquipmentScannerRegistry.java`
- Modify: `src/main/java/com/crackedgames/craftics/combat/TrimEffects.java`

- [ ] **Step 1: Create EquipmentScanner, StatModifiers, EquipmentScannerRegistry**

`EquipmentScanner`:
```java
@FunctionalInterface
public interface EquipmentScanner {
    StatModifiers scan(ServerPlayerEntity player);
}
```

`StatModifiers`:
```java
public class StatModifiers {
    private final Map<TrimEffects.Bonus, Integer> bonuses = new HashMap<>();
    private TrimEffects.SetBonus setBonus = TrimEffects.SetBonus.NONE;
    private String setBonusName = "";

    public void add(TrimEffects.Bonus bonus, int value) {
        bonuses.merge(bonus, value, Integer::sum);
    }
    public void addSetBonus(TrimEffects.SetBonus bonus, String name) {
        this.setBonus = bonus;
        this.setBonusName = name;
    }
    public int get(TrimEffects.Bonus bonus) {
        return bonuses.getOrDefault(bonus, 0);
    }
    public Map<TrimEffects.Bonus, Integer> getAll() { return bonuses; }
    public TrimEffects.SetBonus getSetBonus() { return setBonus; }
    public String getSetBonusName() { return setBonusName; }
}
```

`EquipmentScannerRegistry`:
```java
public final class EquipmentScannerRegistry {
    private static final Map<String, EquipmentScanner> SCANNERS = new LinkedHashMap<>();

    public static void register(String id, EquipmentScanner scanner) {
        SCANNERS.put(id, scanner);
    }

    public static StatModifiers scanAll(ServerPlayerEntity player) {
        StatModifiers combined = new StatModifiers();
        for (EquipmentScanner scanner : SCANNERS.values()) {
            StatModifiers result = scanner.scan(player);
            for (var entry : result.getAll().entrySet()) {
                combined.add(entry.getKey(), entry.getValue());
            }
            if (result.getSetBonus() != TrimEffects.SetBonus.NONE) {
                combined.addSetBonus(result.getSetBonus(), result.getSetBonusName());
            }
        }
        return combined;
    }
}
```

- [ ] **Step 2: Integrate into TrimEffects.scan()**

After the existing trim scanning loop builds the `TrimScan`, merge addon scanner results:

```java
// After the existing scan logic, before returning:
StatModifiers addonMods = EquipmentScannerRegistry.scanAll(player);
for (var entry : addonMods.getAll().entrySet()) {
    bonuses.merge(entry.getKey(), entry.getValue(), Integer::sum);
}
if (addonMods.getSetBonus() != SetBonus.NONE && setBonus == SetBonus.NONE) {
    setBonus = addonMods.getSetBonus();
    setName = addonMods.getSetBonusName();
}
```

- [ ] **Step 3: Verify compilation**

Run: `./gradlew compileJava 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```
feat(api): add EquipmentScanner registry for addon equipment effects

Addon mods can register scanners that contribute stat bonuses from
non-standard inventory slots (trinkets, baubles, etc.). Results
merge into TrimScan alongside vanilla trim bonuses.
```

---

## Task 8: Event Registry

**Files:**
- Create: `src/main/java/com/crackedgames/craftics/api/registry/EventRegistry.java`
- Create: `src/main/java/com/crackedgames/craftics/api/registry/EventEntry.java`
- Create: `src/main/java/com/crackedgames/craftics/api/EventHandler.java`
- Create: `src/main/java/com/crackedgames/craftics/api/EventTemplates.java`
- Modify: `src/main/java/com/crackedgames/craftics/combat/EventManager.java`
- Modify: `src/main/java/com/crackedgames/craftics/api/VanillaContent.java`

- [ ] **Step 1: Create EventHandler, EventEntry, EventRegistry**

`EventHandler`:
```java
@FunctionalInterface
public interface EventHandler {
    void execute(List<ServerPlayerEntity> participants, ServerWorld world,
                 EventManager eventManager);
}
```

`EventEntry`: record with `id`, `displayName`, `probability` (float), `minBiomeOrdinal` (int), `isChoiceEvent` (boolean), `handler` (EventHandler).

`EventRegistry`:
- `List<EventEntry>` (ordered — built-in first, then addon)
- `register(EventEntry)` — appends to list
- `rollEvent(int biomeOrdinal, int levelIndex, boolean earlyBiome)` — reimplements the probability cascade from `EventManager.rollEvent()`, iterating all registered events, filtering by `minBiomeOrdinal`, rolling against cumulative probability. Includes probability overflow protection: if total probability > 0.90, log a warning and scale down proportionally.
- `getById(String id)` — for forced events
- `getAll()` — for debugging

- [ ] **Step 2: Create EventTemplates**

Factory methods returning `EventHandler`:
- `gamble(int baseCost, int costVariance, LootTable rewardTable)` — generic shrine-style: spend emeralds, roll reward
- `giveReward(LootTable rewardTable)` — no cost, just give items
- `ambush(List<String> enemyTypeIds)` — trigger combat with specific enemies
- `spawnTrader(Supplier<TraderSystem.TraderOffer> offerGenerator)` — spawn trader with offers

These extract the core patterns from `RandomEvents.handleShrine()`, `RandomEvents.handleWoundedTraveler()`, etc. Addon mods can use these as base patterns.

- [ ] **Step 3: Register vanilla events in VanillaContent**

Register all 8 built-in events with their current probabilities from `EventType` and handlers extracted from `RandomEvents`:

```java
EventRegistry.register(new EventEntry("craftics:ominous_trial", "Ominous Trial", 0.05f, 10, true, handler));
EventRegistry.register(new EventEntry("craftics:trial_chamber", "Trial Chamber", 0.10f, 0, true, handler));
// ... etc
```

For built-in events with complex handler logic (shrine, traveler, dig site, etc.), the handler wraps the existing `RandomEvents` static methods. These can be cleaned up later but should preserve behavior.

- [ ] **Step 4: Refactor EventManager.rollEvent()**

Replace the hardcoded probability cascade with:
```java
public String rollEvent(int biomeOrdinal, int levelIndex, int ngPlusLevel, boolean earlyBiome) {
    // Check forced event
    if (forcedNextEvent != null) {
        String forced = forcedNextEvent;
        forcedNextEvent = null;
        return forced; // now returns string ID, not EventType
    }

    // Skip conditions
    if (levelIndex <= 1) return "none";

    Random rng = new Random();
    float eventRoll = rng.nextFloat();

    if (earlyBiome) {
        if (eventRoll > CrafticsMod.CONFIG.earlyBiomeEventChance()) return "none";
        eventRoll = rng.nextFloat();
    }

    return EventRegistry.roll(eventRoll, biomeOrdinal);
}
```

Change return type from `EventType` to `String` (event ID). Update all callers in `CombatManager` to work with string IDs instead of the `EventType` enum. `EventType` enum stays for backwards compat but is no longer the primary dispatch mechanism.

- [ ] **Step 5: Verify compilation**

Run: `./gradlew compileJava 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```
refactor: replace hardcoded event cascade with EventRegistry

EventManager.rollEvent() now delegates to EventRegistry for
probability-based event selection. All 8 vanilla events registered
with their handlers. Addon mods can register custom events via API.
```

---

## Task 9: Enchantment Registry

**Files:**
- Create: `src/main/java/com/crackedgames/craftics/api/registry/EnchantmentRegistry.java`
- Create: `src/main/java/com/crackedgames/craftics/api/EnchantmentEffectHandler.java`
- Create: `src/main/java/com/crackedgames/craftics/api/EnchantmentContext.java`
- Modify: `src/main/java/com/crackedgames/craftics/api/VanillaContent.java`

- [ ] **Step 1: Create EnchantmentEffectHandler, EnchantmentContext, EnchantmentRegistry**

`EnchantmentEffectHandler`:
```java
@FunctionalInterface
public interface EnchantmentEffectHandler {
    void apply(EnchantmentContext ctx);
}
```

`EnchantmentContext`:
```java
public class EnchantmentContext {
    private final int level;
    private final ServerPlayerEntity player;
    private final StatModifiers modifiers;

    public EnchantmentContext(int level, ServerPlayerEntity player, StatModifiers modifiers) {
        this.level = level;
        this.player = player;
        this.modifiers = modifiers;
    }

    public int getLevel() { return level; }
    public ServerPlayerEntity getPlayer() { return player; }
    public StatModifiers getModifiers() { return modifiers; }
}
```

`EnchantmentRegistry`:
```java
public final class EnchantmentRegistry {
    private static final Map<String, EnchantmentEffectHandler> REGISTRY = new ConcurrentHashMap<>();

    public static void register(String enchantmentId, EnchantmentEffectHandler handler) {
        REGISTRY.put(enchantmentId, handler);
    }

    public static StatModifiers applyAll(ServerPlayerEntity player) {
        StatModifiers mods = new StatModifiers();
        // Scan all armor + weapon enchantments
        for (var entry : REGISTRY.entrySet()) {
            int level = findEnchantLevel(player, entry.getKey());
            if (level > 0) {
                entry.getValue().apply(new EnchantmentContext(level, player, mods));
            }
        }
        return mods;
    }

    private static int findEnchantLevel(ServerPlayerEntity player, String enchantId) {
        // Check weapon
        int level = PlayerCombatStats.getEnchantLevel(player.getMainHandStack(), enchantId);
        if (level > 0) return level;
        // Check all armor slots
        for (EquipmentSlot slot : new EquipmentSlot[]{
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            level = Math.max(level, PlayerCombatStats.getEnchantLevel(player.getEquippedStack(slot), enchantId));
        }
        return level;
    }
}
```

- [ ] **Step 2: Register vanilla enchantment effects in VanillaContent**

Register stat-bonus enchantments:
```java
// Protection: every 2 levels = +1 defense (scans all armor)
EnchantmentRegistry.register("minecraft:protection", ctx -> {
    ctx.getModifiers().add(TrimEffects.Bonus.DEFENSE, ctx.getLevel() / 2);
});
// ... similarly for blast_protection, projectile_protection, etc.
```

Note: Weapon-ability enchantments (sharpness, smite, bane, etc.) are handled by the `WeaponAbilityHandler` in the weapon registry — they read enchant levels directly via `PlayerCombatStats.getSharpness()` etc. Those helper methods in `PlayerCombatStats` stay unchanged.

- [ ] **Step 3: Verify compilation**

Run: `./gradlew compileJava 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```
feat(api): add EnchantmentRegistry for extensible enchantment effects

Addon mods can register enchantments that provide passive stat bonuses.
Vanilla protection enchantments registered. Weapon-ability enchantments
(sharpness, smite, etc.) handled by WeaponAbilityHandler, not this registry.
```

---

## Task 10: Expand CrafticsAPI surface

**Files:**
- Modify: `src/main/java/com/crackedgames/craftics/api/CrafticsAPI.java`

- [ ] **Step 1: Add all new registration methods**

Add to `CrafticsAPI.java`:

```java
// Weapons
public static void registerWeapon(Item item, WeaponEntry entry) {
    WeaponRegistry.register(item, entry);
}

// Equipment scanners
public static void registerEquipmentScanner(String id, EquipmentScanner scanner) {
    EquipmentScannerRegistry.register(id, scanner);
}

// Armor sets
public static void registerArmorSet(ArmorSetEntry entry) {
    ArmorSetRegistry.register(entry);
}

// Trim patterns
public static void registerTrimPattern(TrimPatternEntry entry) {
    TrimPatternRegistry.register(entry);
}

// Trim materials
public static void registerTrimMaterial(TrimMaterialEntry entry) {
    TrimMaterialRegistry.register(entry);
}

// Events
public static void registerEvent(EventEntry entry) {
    EventRegistry.register(entry);
}

// Enchantments
public static void registerEnchantment(String enchantmentId, EnchantmentEffectHandler handler) {
    EnchantmentRegistry.register(enchantmentId, handler);
}
```

Add necessary imports for all registry and entry types.

- [ ] **Step 2: Verify compilation**

Run: `./gradlew compileJava 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```
feat(api): expand CrafticsAPI with all registry methods

Public API now exposes: registerWeapon, registerEquipmentScanner,
registerArmorSet, registerTrimPattern, registerTrimMaterial,
registerEvent, registerEnchantment. Full addon extensibility.
```

---

## Task 11: Full build + runtime verification

**Files:** None (verification only)

- [ ] **Step 1: Full build**

Run: `./gradlew build 2>&1 | tail -30`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Check for any remaining direct references to old patterns**

Search for any leftover hardcoded weapon checks that should now use the registry:
- `Items.WOODEN_SWORD` in `DamageType.java` (should be gone from `fromWeapon`)
- `Items.DIAMOND_SWORD` in `WeaponAbility.java` (should be gone from `applyAbility`)
- `case "leather"` in `DamageType.java` (should be gone from `getArmorSetBonus`)
- `case "sentry"` in `TrimEffects.java` (should be gone from `getPerPieceBonus`)

Run:
```bash
grep -rn "Items\.\(WOODEN\|STONE\|IRON\|GOLDEN\|DIAMOND\|NETHERITE\)_SWORD" src/main/java/com/crackedgames/craftics/combat/DamageType.java
grep -rn "Items\.\(WOODEN\|STONE\|IRON\|GOLDEN\|DIAMOND\|NETHERITE\)_SWORD" src/main/java/com/crackedgames/craftics/combat/WeaponAbility.java
grep -n 'case "leather"' src/main/java/com/crackedgames/craftics/combat/DamageType.java
grep -n 'case "sentry"' src/main/java/com/crackedgames/craftics/combat/TrimEffects.java
```

Expected: No matches (all moved to registry-based code)

- [ ] **Step 3: Verify CombatManager still compiles with refactored APIs**

The biggest risk is `CombatManager.java` which calls into all these systems. Check it compiles:

Run: `./gradlew compileJava 2>&1 | grep -i error | head -20`
Expected: No errors

- [ ] **Step 4: Commit**

```
chore: verify full build after registry refactor

All hardcoded patterns replaced with registry lookups.
Build passes. No remaining direct weapon/armor/trim references
in refactored files.
```

---

## Task 12: Update GitHub Pages documentation

**Files:**
- Modify: `docs/modding.html`

- [ ] **Step 1: Rewrite modding.html**

Update the modding guide to document the full public API. Add sections for:

1. **Overview** — what Craftics exposes for addon mods
2. **Getting Started** — how to set up a compat mod (Fabric mod that depends on Craftics, call `CrafticsAPI` from `onInitialize()`)
3. **Weapon Registration** — `CrafticsAPI.registerWeapon()` with builder examples, ability building blocks, custom ability interface
4. **Equipment Scanners** — `CrafticsAPI.registerEquipmentScanner()` with Artifacts-style example
5. **Armor Sets** — `CrafticsAPI.registerArmorSet()` with builder example
6. **Trim Effects** — `CrafticsAPI.registerTrimPattern()` and `registerTrimMaterial()`
7. **Events** — `CrafticsAPI.registerEvent()` with templates and custom handler examples
8. **Enchantments** — `CrafticsAPI.registerEnchantment()` with stat modifier example
9. **Biomes** (existing) — keep current JSON datapack docs
10. **Enemy AI** (existing) — keep current AI registration docs
11. **Complete Example** — a full compat mod skeleton showing Simply Swords integration

Follow the existing HTML structure and CSS classes from the current `modding.html`.

- [ ] **Step 2: Verify HTML renders locally**

Open `docs/modding.html` in a browser and verify sections render correctly.

- [ ] **Step 3: Commit**

```
docs: rewrite modding guide with full API documentation

Documents all new registry APIs: weapons, equipment scanners,
armor sets, trims, events, and enchantments. Includes builder
examples, building blocks reference, and complete compat mod example.
```

---

## Task Summary

| Task | Description | Dependencies |
|---|---|---|
| 1 | WeaponRegistry + WeaponEntry + WeaponAbilityHandler | None |
| 2 | Abilities building blocks | Task 1 |
| 3 | VanillaWeapons + refactor DamageType/PlayerCombatStats/WeaponAbility | Task 1 |
| 4 | Wire vanilla weapon abilities | Tasks 2, 3 |
| 5 | ArmorSetRegistry + refactor | None (parallel with 1-4) |
| 6 | TrimPatternRegistry + TrimMaterialRegistry + refactor | None (parallel with 1-5) |
| 7 | EquipmentScannerRegistry | Task 6 (depends on TrimEffects) |
| 8 | EventRegistry + refactor | None (parallel with 1-7) |
| 9 | EnchantmentRegistry | Task 7 (uses StatModifiers) |
| 10 | Expand CrafticsAPI | Tasks 1-9 |
| 11 | Full build verification | Task 10 |
| 12 | GitHub Pages docs | Task 10 |

**Parallelizable groups:**
- Group A: Tasks 1 → 2 → 3 → 4 (weapon pipeline)
- Group B: Task 5 (armor sets — independent)
- Group C: Tasks 6 → 7 (trims + equipment scanners)
- Group D: Task 8 (events — independent)
- Group E: Task 9 (enchantments — needs StatModifiers from Task 7)
- Final: Tasks 10 → 11 → 12 (integration + verification + docs)
