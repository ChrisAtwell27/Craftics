package com.crackedgames.craftics.api;

/**
 * The entry point for a Craftics addon mod.
 *
 * <p>An addon implements this interface and declares it under the {@code craftics}
 * entrypoint in its {@code fabric.mod.json}:
 *
 * <pre>{@code
 * "entrypoints": {
 *   "craftics": [ "com.example.mymod.MyCrafticsAddon" ]
 * }
 * }</pre>
 *
 * <p>Craftics invokes {@link #onCrafticsInit()} once, after it has registered all of
 * its own built-in content (weapons, armor sets, trims, events, enchantments, …) and
 * after its built-in compat modules. Addon registrations therefore always run last
 * and deterministically override any same-keyed built-in entry — unlike registering
 * from a plain {@code ModInitializer}, where load order against Craftics is undefined.
 *
 * <pre>{@code
 * public final class MyCrafticsAddon implements CrafticsAddon {
 *     @Override
 *     public void onCrafticsInit() {
 *         CrafticsAPI.registerWeapon(MyItems.RUNE_BLADE, WeaponEntry.builder(MyItems.RUNE_BLADE)
 *             .damageType(DamageType.SLASHING).attackPower(9).apCost(1).range(1)
 *             .build());
 *     }
 * }
 * }</pre>
 *
 * <p>No-code content (JSON datapacks under {@code data/<namespace>/craftics/...}) does
 * not need an addon class at all — it is discovered and loaded automatically.
 *
 * @since 0.2.0
 */
public interface CrafticsAddon {

    /**
     * Called once during mod initialization, after Craftics has registered all of its
     * built-in content. Perform all {@link CrafticsAPI} registrations here.
     *
     * <p>Exceptions thrown from this method are caught and logged by Craftics — a
     * failing addon will not crash the game or block other addons from loading.
     */
    void onCrafticsInit();
}
