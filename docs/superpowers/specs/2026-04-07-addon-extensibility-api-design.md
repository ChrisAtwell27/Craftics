# Craftics Addon Extensibility API — Design Spec

**Date:** 2026-04-07
**Goal:** Make every core combat system extensible so addon/compat mods can register custom weapons, equipment effects, armor set bonuses, trim effects, events, and enchantment effects through a public Java API.

## Decisions

| Decision | Choice |
|---|---|
| Registration pattern | Addons call `CrafticsAPI.registerXxx()` from `onInitialize()` |
| Weapon abilities | Building blocks + custom interface (both) |
| Equipment effects | Scanner hook — addon scans its own inventory slots |
| Damage types / affinities | Map into existing 8 types, no custom types |
| Events | Templates + custom handler interface (both) |
| Data format | Code API only, no JSON required |
| Architecture | Registry-first refactor — Craftics dogfoods its own API for vanilla content |

## Approach: Registry-First Refactor

Convert every hardcoded system (weapon lookup, trim bonuses, armor set bonuses, enchantment effects, events) into internal registries. Craftics registers its own vanilla weapons/trims/events using the same API that addons use. Existing enums (`DamageType`, `Affinity`, `SetBonus`, etc.) stay as-is — addons map into existing types.

Existing `switch` statements and `if/else` chains become registry lookups. This makes the codebase simpler overall while guaranteeing addon parity.

---

## Section 1: Weapon Registry

### Problem

`DamageType.fromWeapon()`, `PlayerCombatStats.getAttackPower()`, `WeaponAbility.getAttackCost()`, `WeaponAbility.applyAbility()`, and `PlayerCombatStats.getWeaponRange()` all use giant if/else chains keyed on `Items.XXX`.

### Solution

Collapse into a single `WeaponRegistry` backed by `Map<Item, WeaponEntry>`.

### Data types

```java
public record WeaponEntry(
    Item item,
    DamageType damageType,
    IntSupplier attackPower,  // IntSupplier so vanilla weapons can reference config values
    int apCost,               // default 1
    int range,                // default 1 (melee)
    boolean isRanged,
    @Nullable WeaponAbilityHandler ability  // null = no special ability
) {
    public static Builder builder(Item item) { ... }

    // Convenience: addon mods pass a fixed int, builder wraps it in () -> value
    // Vanilla registrations pass config refs: () -> CONFIG.dmgDiamondSword()
}
```

### WeaponAbilityHandler — functional interface for custom logic

```java
@FunctionalInterface
public interface WeaponAbilityHandler {
    AttackResult apply(ServerPlayerEntity player, CombatEntity target,
                       GridArena arena, int baseDamage,
                       PlayerProgression.PlayerStats stats, int luckPoints);
}
```

### Builder with chainable building blocks

```java
WeaponEntry.builder(Items.DIAMOND_SWORD)
    .damageType(DamageType.SLASHING)
    .attackPower(7).apCost(1).range(1)
    .ability(Abilities.bleed()
        .and(Abilities.sweepAdjacent(0.1f, 5)))
    .build();
```

### Built-in building blocks (extracted from current WeaponAbility logic)

- `Abilities.bleed()` — apply bleed stacks based on Sharpness enchant
- `Abilities.sweepAdjacent(baseChance, bonusPerPoint)` — hit adjacent enemies
- `Abilities.armorIgnore(baseChance, bonusPerPoint)` — bypass defense
- `Abilities.stun(baseChance, bonusPerPoint)` — stun target
- `Abilities.knockback(baseChance, bonusPerPoint)` — push + apply Soaked
- `Abilities.aoe(radius, damageMultiplier)` — AoE splash
- `Abilities.applyEffect(EffectType, turns, amplifier)` — inflict status

### Registration

Craftics registers all vanilla weapons during `CrafticsMod.onInitialize()` using the builder API. Unregistered items return a default PHYSICAL / 1 damage / 1 AP entry (current fist behavior).

### Files affected

- **New:** `api/registry/WeaponRegistry.java`, `api/registry/WeaponEntry.java`, `api/WeaponAbilityHandler.java`, `api/Abilities.java`
- **Modified:** `DamageType.java` (remove `fromWeapon()`), `PlayerCombatStats.java` (remove if/else chains), `WeaponAbility.java` (extract logic into building blocks), `CombatManager.java` (use registry lookups)

---

## Section 2: Equipment Scanner Registry

### Problem

`TrimEffects.scan()` only reads vanilla armor trim slots. Mods like Artifacts use non-standard inventory slots (Trinkets API) that Craftics can't know about.

### Solution

A scanner hook that lets addon mods contribute stat bonuses from any source.

### Data types

```java
@FunctionalInterface
public interface EquipmentScanner {
    StatModifiers scan(ServerPlayerEntity player);
}

public class StatModifiers {
    public void add(TrimEffects.Bonus bonus, int value);
    public void addSetBonus(TrimEffects.SetBonus setBonus, String displayName);
    public int get(TrimEffects.Bonus bonus);
}
```

### Integration

`TrimEffects.scan()` stays as the core scanner for vanilla armor trims. After building the `TrimScan`, it iterates all registered `EquipmentScanner`s and merges their `StatModifiers` into the final result. The rest of the combat pipeline reads from `TrimScan` unchanged.

### Example (Artifacts compat mod)

```java
CrafticsAPI.registerEquipmentScanner("artifacts", (player) -> {
    StatModifiers mods = new StatModifiers();
    if (TrinketsApi.getEquipped(player, ArtifactItems.CRYSTAL_HEART)) {
        mods.add(Bonus.MAX_HP, 4);
    }
    if (TrinketsApi.getEquipped(player, ArtifactItems.FLAME_PENDANT)) {
        mods.addSetBonus(SetBonus.INFERNAL, "Flame Pendant");
    }
    return mods;
});
```

### Files affected

- **New:** `api/EquipmentScanner.java`, `api/StatModifiers.java`, `api/registry/EquipmentScannerRegistry.java`
- **Modified:** `TrimEffects.java` (merge addon scanner results into TrimScan)

---

## Section 3: Armor Set Bonus Registry

### Problem

`DamageType.getArmorSetBonus()` has a hardcoded switch mapping armor material strings to damage type bonuses.

### Solution

Registry-backed lookup.

### Data types

```java
public record ArmorSetEntry(
    String armorSetId,
    Map<DamageType, Integer> bonuses
) {
    public static Builder builder(String armorSetId) { ... }
}
```

### Builder

```java
ArmorSetEntry.builder("mythril")
    .bonus(DamageType.SPECIAL, 3)
    .bonus(DamageType.WATER, 1)
    .build();
```

### Registration

Craftics registers vanilla sets (leather/chainmail/iron/gold/diamond/netherite/turtle) at init. `DamageType.getArmorSetBonus()` becomes `ArmorSetRegistry.getBonus(armorSet, type)`.

Armor set detection (deriving the set name from equipped armor items) stays unchanged.

### Files affected

- **New:** `api/registry/ArmorSetRegistry.java`, `api/registry/ArmorSetEntry.java`
- **Modified:** `DamageType.java` (remove `getArmorSetBonus()` switch, delegate to registry)

---

## Section 4: Trim Effect Registry

### Problem

Three switch statements in `TrimEffects` — `getPerPieceBonus()`, `getMaterialBonus()`, and `getSetBonus()` — are hardcoded to vanilla trim pattern/material IDs.

### Solution

Registry-backed lookup for both patterns and materials.

### Data types

```java
public record TrimPatternEntry(
    String patternId,
    TrimEffects.Bonus perPieceStat,
    String perPieceDescription,
    TrimEffects.SetBonus setBonus,
    String setBonusName,
    String setBonusDescription
) {}

public record TrimMaterialEntry(
    String materialId,
    TrimEffects.Bonus stat,
    int valuePerPiece,     // usually 1, quartz is 2
    String description
) {}
```

### Registration

Vanilla patterns and materials are registered at init. Switch statements become `TrimPatternRegistry.get(patternId)` / `TrimMaterialRegistry.get(materialId)`. Unknown IDs return null (no bonus), same as current `default -> null`.

Set bonus logic (requires 4 matching pattern pieces) stays unchanged — the registry just provides what bonus a given pattern gives.

### Files affected

- **New:** `api/registry/TrimPatternRegistry.java`, `api/registry/TrimPatternEntry.java`, `api/registry/TrimMaterialRegistry.java`, `api/registry/TrimMaterialEntry.java`
- **Modified:** `TrimEffects.java` (replace switches with registry lookups)

---

## Section 5: Event Registry

### Problem

`EventType` is a sealed enum with 8 fixed types. `EventManager.rollEvent()` uses a hardcoded probability cascade. `RandomEvents` has static handler methods that can't be extended.

### Solution

`EventType` stays as an enum for built-in events, but a new `EventRegistry` allows custom events that participate in the between-level event roll.

### Data types

```java
public record EventEntry(
    String id,                  // "mymod:enchanted_forge"
    String displayName,
    float probability,
    int minBiomeOrdinal,        // 0 = available from start
    boolean isChoiceEvent,
    EventHandler handler
) {}

@FunctionalInterface
public interface EventHandler {
    void execute(List<ServerPlayerEntity> participants, ServerWorld world,
                 EventManager eventManager);
}
```

### Templates for common patterns

```java
EventTemplates.gamble(emeraldCost, rewardTable)    // Shrine-style
EventTemplates.giveReward(rewardTable)             // Free reward
EventTemplates.spawnTrader(offerGenerator)          // Trader-style
EventTemplates.ambush(enemyList)                    // Spawn combat
```

### How event rolling changes

`EventManager.rollEvent()` currently uses a hardcoded probability cascade. New version:
1. Collects all registered events (built-in + addon) with their probabilities
2. Filters by `minBiomeOrdinal`
3. Rolls through the cascade in registration order (built-in first, then addon events)
4. Remainder = NONE

**Probability overflow protection:** If total registered probability exceeds a threshold (e.g. 0.90), the registry logs a warning and proportionally scales all probabilities down so there is always a NONE remainder. This prevents addons from accidentally eliminating "no event" rolls.

Built-in events (SHRINE, AMBUSH, etc.) are registered at init with their current probabilities and their existing handler logic extracted from `RandomEvents` static methods.

Forced events still work — `setForcedNextEvent("mymod:enchanted_forge")` checks the registry by ID.

### Files affected

- **New:** `api/registry/EventRegistry.java`, `api/registry/EventEntry.java`, `api/EventHandler.java`, `api/EventTemplates.java`
- **Modified:** `EventManager.java` (refactor `rollEvent()` to use registry), `RandomEvents.java` (extract handlers into EventHandler implementations), `EventType.java` (keep for built-in IDs, no longer sole source of truth)

---

## Section 6: Enchantment Bonus Registry

### Problem

`PlayerCombatStats` has hardcoded enchantment lookups — Protection gives defense, Projectile Protection gives ranged defense, Power gives bow range, etc.

### Solution

Registry for enchantments that provide passive stat bonuses.

### Data types

```java
public record EnchantmentEntry(
    String enchantmentId,
    EnchantmentEffectHandler handler
) {}

@FunctionalInterface
public interface EnchantmentEffectHandler {
    void apply(EnchantmentContext ctx);
}

public class EnchantmentContext {
    public int getLevel();
    public ServerPlayerEntity getPlayer();
    public StatModifiers getModifiers();
}
```

### Scope

This registry handles enchantments that give passive stat bonuses (Protection -> defense, Projectile Protection -> ranged defense, etc.).

Enchantments that modify weapon abilities (Sharpness -> bleed stacks, Smite -> AoE undead damage) are handled through `WeaponAbilityHandler` in Section 1 — the ability handler reads enchantment levels from the player's weapon.

### Files affected

- **New:** `api/registry/EnchantmentRegistry.java`, `api/registry/EnchantmentEntry.java`, `api/EnchantmentEffectHandler.java`, `api/EnchantmentContext.java`
- **Modified:** `PlayerCombatStats.java` (replace hardcoded enchantment bonuses with registry lookups)

---

## Section 7: Expanded CrafticsAPI Surface

The public API class grows to expose all registries:

```java
public final class CrafticsAPI {
    // Existing
    public static void registerAI(String entityTypeId, EnemyAI ai);
    public static void registerBiome(BiomeTemplate template);

    // New — Weapons
    public static void registerWeapon(Item item, WeaponEntry entry);

    // New — Equipment scanners
    public static void registerEquipmentScanner(String id, EquipmentScanner scanner);

    // New — Armor sets
    public static void registerArmorSet(ArmorSetEntry entry);

    // New — Trim patterns & materials
    public static void registerTrimPattern(TrimPatternEntry entry);
    public static void registerTrimMaterial(TrimMaterialEntry entry);

    // New — Events
    public static void registerEvent(EventEntry entry);

    // New — Enchantment effects
    public static void registerEnchantment(String enchantmentId, EnchantmentEffectHandler handler);

    // Existing
    public static int getTotalLevels();
    public static boolean hasEnvironmentStyle(String styleName);
}
```

All methods delegate to their respective registry classes. Addons call these from `onInitialize()`. Craftics calls them from its own `onInitialize()` to register vanilla content.

---

## Files Summary

### New files

| Package | File | Purpose |
|---|---|---|
| `api/registry/` | `WeaponRegistry.java` | `Map<Item, WeaponEntry>` with get/register/fallback |
| `api/registry/` | `WeaponEntry.java` | Immutable weapon data + builder |
| `api/registry/` | `ArmorSetRegistry.java` | `Map<String, ArmorSetEntry>` |
| `api/registry/` | `ArmorSetEntry.java` | Armor set bonus data + builder |
| `api/registry/` | `TrimPatternRegistry.java` | `Map<String, TrimPatternEntry>` |
| `api/registry/` | `TrimPatternEntry.java` | Per-piece + set bonus data |
| `api/registry/` | `TrimMaterialRegistry.java` | `Map<String, TrimMaterialEntry>` |
| `api/registry/` | `TrimMaterialEntry.java` | Material stat bonus data |
| `api/registry/` | `EventRegistry.java` | Ordered list of EventEntry + roll logic |
| `api/registry/` | `EventEntry.java` | Event data + probability + handler |
| `api/registry/` | `EnchantmentRegistry.java` | `Map<String, EnchantmentEffectHandler>` |
| `api/registry/` | `EnchantmentEntry.java` | Enchantment effect data |
| `api/registry/` | `EquipmentScannerRegistry.java` | `Map<String, EquipmentScanner>` |
| `api/` | `WeaponAbilityHandler.java` | Functional interface for weapon abilities |
| `api/` | `EquipmentScanner.java` | Functional interface for equipment scanning |
| `api/` | `StatModifiers.java` | Mutable stat bonus accumulator |
| `api/` | `EventHandler.java` | Functional interface for event logic |
| `api/` | `EnchantmentEffectHandler.java` | Functional interface for enchantment effects |
| `api/` | `EnchantmentContext.java` | Context object for enchantment handlers |
| `api/` | `Abilities.java` | Chainable weapon ability building blocks |
| `api/` | `EventTemplates.java` | Pre-built event handler templates |

### Modified files

| File | Change |
|---|---|
| `CrafticsAPI.java` | Add all new `registerXxx()` methods |
| `CrafticsMod.java` | Register all vanilla content at init using the API |
| `DamageType.java` | Remove `fromWeapon()` and `getArmorSetBonus()` switches, delegate to registries |
| `PlayerCombatStats.java` | Remove if/else chains for attack power/range/enchantments, use WeaponRegistry + EnchantmentRegistry |
| `WeaponAbility.java` | Extract ability logic into `Abilities` building blocks, `applyAbility()` delegates to `WeaponRegistry` |
| `TrimEffects.java` | Replace pattern/material switches with registry lookups, merge equipment scanner results |
| `EventManager.java` | Refactor `rollEvent()` to use EventRegistry, support custom event IDs |
| `RandomEvents.java` | Extract handler methods into EventHandler implementations |
| `CombatManager.java` | Use registry lookups where it currently calls into the refactored classes |

### Documentation (GitHub Pages)

| File | Change |
|---|---|
| `docs/modding.html` | Rewrite to document the full public API — all `CrafticsAPI.registerXxx()` methods, builder patterns, examples for each registry, and compat mod guide (Simply Swords, Artifacts examples) |
| `docs/index.html` | Update feature list to highlight addon/modding support |

### Unchanged

| File | Reason |
|---|---|
| `EnemyAction.java` | Sealed interface — action types are part of the animation/rendering contract |
| `CombatEffects.java` | EffectType enum stays — scanners can apply existing effects |
| `PlayerProgression.java` | Stat/Affinity enums stay — addons map into existing types |
| `BiomeRegistry.java` | Already extensible |
| `AIRegistry.java` | Already extensible |
