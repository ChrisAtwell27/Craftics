# CombatEffectHandler API — Design Spec

**Date:** 2026-04-07
**Goal:** Allow addon mods to register fully custom combat effects with lifecycle callbacks, enabling equipment items to have unique behaviors beyond flat stat bonuses.

## Decisions

| Decision | Choice |
|---|---|
| Return type | `CombatResult` for modifying callbacks, `void` for reactive callbacks |
| Handler interface | Single interface with 24 default no-op methods |
| Effects per item | Multiple handlers per item allowed |
| Statefulness | Stateful per combat encounter (fresh instances each fight) |
| Registration point | Through existing `StatModifiers` via `addCombatEffect()` — no new API method |
| Handler ordering | Registration order, no priority system |

---

## CombatResult

Return type for callbacks that can modify combat values (damage, duration, amounts). Callbacks that are purely reactive return `void`.

```java
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

### Which callbacks return CombatResult vs void

**Returns CombatResult (can modify):** `onDealDamage`, `onTakeDamage`, `onLethalDamage`, `onAllyTakeDamage`, `onKnockback`, `onEmeraldGain`, `onEffectApplied`

**Returns void (side effects only):** `onCombatStart`, `onTurnStart`, `onTurnEnd`, `onCombatEnd`, `onDealKillingBlow`, `onCrit`, `onMiss`, `onDodge`, `onBlocked`, `onMove`, `onAllyAttack`, `onAllyKill`, `onAllyDeath`, `onEffectExpired`, `onLootRoll` (receives mutable list), `onEnemySpawn`, `onBossPhaseChange`

Rule: if the result feeds back into a combat calculation, it returns `CombatResult`. If it just reacts, it's `void`.

---

## CombatEffectContext

Shared context object passed to every callback. Provides access to the combat session state without passing 5+ parameters to each method.

```java
public class CombatEffectContext {
    public ServerPlayerEntity getPlayer();
    public GridArena getArena();
    public CombatEffects getPlayerEffects();
    public TrimEffects.TrimScan getTrimScan();
    public List<CombatEntity> getAllEnemies();
    public List<CombatEntity> getAllAllies();
}
```

Created once per combat encounter. Updated each turn with current arena state, enemy list, and ally list.

---

## CombatEffectHandler Interface

Single interface with 24 default no-op methods. Addon devs override only what they need.

```java
public interface CombatEffectHandler {
    // --- Core combat lifecycle ---
    default void onCombatStart(CombatEffectContext ctx) {}
    default void onTurnStart(CombatEffectContext ctx) {}
    default void onTurnEnd(CombatEffectContext ctx) {}
    default void onCombatEnd(CombatEffectContext ctx) {}

    // --- Damage dealing ---
    default CombatResult onDealDamage(CombatEffectContext ctx, CombatEntity target, int damage)
        { return CombatResult.unchanged(damage); }
    default void onDealKillingBlow(CombatEffectContext ctx, CombatEntity killed) {}
    default void onCrit(CombatEffectContext ctx, CombatEntity target, int damage) {}
    default void onMiss(CombatEffectContext ctx, CombatEntity target) {}

    // --- Damage receiving ---
    default CombatResult onTakeDamage(CombatEffectContext ctx, CombatEntity attacker, int damage)
        { return CombatResult.unchanged(damage); }
    default CombatResult onLethalDamage(CombatEffectContext ctx, CombatEntity attacker, int damage)
        { return CombatResult.unchanged(damage); }
    default void onDodge(CombatEffectContext ctx, CombatEntity attacker) {}
    default void onBlocked(CombatEffectContext ctx, CombatEntity attacker, int blockedDamage) {}

    // --- Movement ---
    default void onMove(CombatEffectContext ctx, GridPos from, GridPos to, int distance) {}
    default CombatResult onKnockback(CombatEffectContext ctx, CombatEntity source, int distance)
        { return CombatResult.unchanged(distance); }

    // --- Allies/Pets ---
    default void onAllyAttack(CombatEffectContext ctx, CombatEntity ally, CombatEntity target, int damage) {}
    default CombatResult onAllyTakeDamage(CombatEffectContext ctx, CombatEntity ally, CombatEntity attacker, int damage)
        { return CombatResult.unchanged(damage); }
    default void onAllyKill(CombatEffectContext ctx, CombatEntity ally, CombatEntity killed) {}
    default void onAllyDeath(CombatEffectContext ctx, CombatEntity ally) {}

    // --- Status effects ---
    default CombatResult onEffectApplied(CombatEffectContext ctx, CombatEffects.EffectType effect, int turns)
        { return CombatResult.unchanged(turns); }
    default void onEffectExpired(CombatEffectContext ctx, CombatEffects.EffectType effect) {}

    // --- Economy/Progression ---
    default void onLootRoll(CombatEffectContext ctx, List<ItemStack> loot) {}
    default CombatResult onEmeraldGain(CombatEffectContext ctx, int amount)
        { return CombatResult.unchanged(amount); }

    // --- Enemy-specific ---
    default void onEnemySpawn(CombatEffectContext ctx, CombatEntity enemy) {}
    default void onBossPhaseChange(CombatEffectContext ctx, CombatEntity boss, int newPhase) {}
}
```

---

## NamedCombatEffect

Pairs a display name with a handler instance for identification and combat log display.

```java
public record NamedCombatEffect(String name, CombatEffectHandler handler) {}
```

---

## StatModifiers Changes

Add combat effect storage alongside existing flat stat bonuses:

```java
public class StatModifiers {
    // Existing
    private final Map<TrimEffects.Bonus, Integer> bonuses = new HashMap<>();
    private TrimEffects.SetBonus setBonus = TrimEffects.SetBonus.NONE;
    private String setBonusName = "";

    // New
    private final List<NamedCombatEffect> combatEffects = new ArrayList<>();

    // Existing methods unchanged...

    // New methods
    public void addCombatEffect(String name, CombatEffectHandler handler) {
        combatEffects.add(new NamedCombatEffect(name, handler));
    }

    public List<NamedCombatEffect> getCombatEffects() {
        return combatEffects;
    }
}
```

---

## TrimEffects.scan() Changes

After merging addon scanner stat bonuses (already implemented), also collect combat effect handlers:

```java
// After existing scanner merge block:
List<NamedCombatEffect> combatEffects = new ArrayList<>();
for (NamedCombatEffect effect : addonMods.getCombatEffects()) {
    combatEffects.add(effect);
}
```

The `TrimScan` record gains a new field:

```java
public record TrimScan(
    Map<Bonus, Integer> bonuses,
    SetBonus setBonus,
    String setName,
    int trimCount,
    Map<String, Integer> materialCounts,
    List<NamedCombatEffect> combatEffects  // NEW
) {
    // Existing methods...
    public List<NamedCombatEffect> getCombatEffects() {
        return combatEffects != null ? combatEffects : List.of();
    }
}
```

---

## CombatManager Integration

### Storage

Active combat effects are stored as a field on the combat session alongside existing state:

```java
private List<NamedCombatEffect> activeCombatEffects = new ArrayList<>();
private CombatEffectContext effectContext;
```

Populated when `TrimScan` is computed at combat start:

```java
activeCombatEffects = activeTrimScan.getCombatEffects();
effectContext = new CombatEffectContext(player, arena, combatEffects, activeTrimScan);
```

### Invocation pattern

For `CombatResult`-returning callbacks — chain results, each handler receives previous handler's modified value:

```java
int damage = rawDamage;
List<String> effectMessages = new ArrayList<>();

for (NamedCombatEffect effect : activeCombatEffects) {
    CombatResult result = effect.handler().onTakeDamage(effectContext, attacker, damage);
    damage = result.modifiedValue();
    effectMessages.addAll(result.messages());
    if (result.cancelled()) {
        damage = 0;
        break;
    }
}
// Apply final damage, send messages
```

For `void` callbacks — iterate and call:

```java
for (NamedCombatEffect effect : activeCombatEffects) {
    effect.handler().onTurnStart(effectContext);
}
```

### Hook insertion points

| Hook | CombatManager location |
|---|---|
| `onCombatStart` | Where PHANTOM/FERAL checks happen at combat init |
| `onTurnStart` | Existing turn start block |
| `onTurnEnd` | Existing turn end block |
| `onCombatEnd` | Combat finish / victory handling |
| `onDealDamage` | After base damage calculated, before applied to target |
| `onDealKillingBlow` | After kill detection in attack resolution |
| `onCrit` | After critical hit detection |
| `onMiss` | After dodge/miss detection |
| `onTakeDamage` | Where FORTRESS/ETHEREAL checks happen |
| `onLethalDamage` | Where OCEAN_BLESSING check happens |
| `onDodge` | After ETHEREAL dodge triggers |
| `onBlocked` | After shield block processing |
| `onMove` | After player movement resolved |
| `onKnockback` | Where knockback is applied to player |
| `onAllyAttack` | Where pet/ally damage is processed |
| `onAllyTakeDamage` | Where pet/ally receives damage |
| `onAllyKill` | After pet/ally kills an enemy |
| `onAllyDeath` | When pet/ally is killed |
| `onEffectApplied` | In CombatEffects.addEffect() |
| `onEffectExpired` | In CombatEffects.tickTurn() when effect expires |
| `onLootRoll` | In post-level reward distribution |
| `onEmeraldGain` | When emeralds are awarded |
| `onEnemySpawn` | In enemy spawning during level setup |
| `onBossPhaseChange` | In BossAI phase transition logic |

---

## Addon Usage Example

```java
CrafticsAPI.registerEquipmentScanner("mymod", player -> {
    StatModifiers mods = new StatModifiers();

    // Flat stats from a simple ring
    if (hasEquipped(player, SPEED_RING)) {
        mods.add(Bonus.SPEED, 1);
    }

    // Thorns Ring: reflect 25% of damage taken
    if (hasEquipped(player, THORNS_RING)) {
        mods.addCombatEffect("Thorns Ring", new CombatEffectHandler() {
            @Override
            public CombatResult onTakeDamage(CombatEffectContext ctx, CombatEntity attacker, int damage) {
                int reflected = Math.max(1, damage / 4);
                attacker.takeDamage(reflected);
                return CombatResult.modify(damage, "§6Thorns Ring reflects " + reflected + " damage!");
            }
        });
    }

    // Lucky Totem: prevent death once + bonus emeralds
    if (hasEquipped(player, LUCKY_TOTEM)) {
        mods.addCombatEffect("Lucky Totem - Death Save", new CombatEffectHandler() {
            private boolean used = false;
            @Override
            public CombatResult onLethalDamage(CombatEffectContext ctx, CombatEntity attacker, int damage) {
                if (!used) {
                    used = true;
                    ctx.getPlayer().heal(10);
                    return CombatResult.cancel("§d✦ Lucky Totem saves you from death!");
                }
                return CombatResult.unchanged(damage);
            }
        });
        mods.addCombatEffect("Lucky Totem - Fortune", new CombatEffectHandler() {
            @Override
            public CombatResult onEmeraldGain(CombatEffectContext ctx, int amount) {
                return CombatResult.modify(amount + 1, "§6Lucky Totem: +1 bonus emerald!");
            }
        });
    }

    // Berserker Charm: enrage on low HP + lifesteal on kill
    if (hasEquipped(player, BERSERKER_CHARM)) {
        mods.addCombatEffect("Berserker Charm - Rage", new CombatEffectHandler() {
            @Override
            public CombatResult onDealDamage(CombatEffectContext ctx, CombatEntity target, int damage) {
                float hpPercent = ctx.getPlayer().getHealth() / ctx.getPlayer().getMaxHealth();
                if (hpPercent < 0.3f) {
                    int bonus = damage / 2;
                    return CombatResult.modify(damage + bonus, "§4Berserker Rage! +" + bonus + " damage!");
                }
                return CombatResult.unchanged(damage);
            }
        });
        mods.addCombatEffect("Berserker Charm - Lifesteal", new CombatEffectHandler() {
            @Override
            public void onDealKillingBlow(CombatEffectContext ctx, CombatEntity killed) {
                ctx.getPlayer().heal(3);
            }
        });
    }

    return mods;
});
```

---

## Files Summary

### New files

| File | Purpose |
|---|---|
| `api/CombatEffectHandler.java` | Interface with 24 default no-op callbacks |
| `api/CombatEffectContext.java` | Context object with player, arena, effects, allies, enemies |
| `api/CombatResult.java` | Return type for modifying callbacks (value + messages + cancelled) |
| `api/NamedCombatEffect.java` | Record pairing display name with handler |

### Modified files

| File | Change |
|---|---|
| `api/StatModifiers.java` | Add `combatEffects` list, `addCombatEffect()`, `getCombatEffects()` |
| `combat/TrimEffects.java` | Collect combat effects from scanner results, add field to `TrimScan` record |
| `combat/CombatManager.java` | Store active effects, create context, insert 24 hook invocation points |
| `docs/modding.html` | Add Combat Effects documentation section |

### Unchanged

| File | Reason |
|---|---|
| `api/CrafticsAPI.java` | No new method — effects go through existing `registerEquipmentScanner()` |
| `api/EquipmentScanner.java` | Interface unchanged — still returns `StatModifiers` |
| All registry files | No changes needed |
