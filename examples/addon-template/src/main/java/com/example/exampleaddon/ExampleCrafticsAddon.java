package com.example.exampleaddon;

import com.crackedgames.craftics.api.Abilities;
import com.crackedgames.craftics.api.CrafticsAPI;
import com.crackedgames.craftics.api.CrafticsAddon;
import com.crackedgames.craftics.api.registry.WeaponEntry;
import com.crackedgames.craftics.combat.CombatEffects;
import com.crackedgames.craftics.combat.DamageType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

/**
 * Entry point for this Craftics addon.
 *
 * <p>It is declared under the {@code craftics} entrypoint in {@code fabric.mod.json}.
 * Craftics calls {@link #onCrafticsInit()} once at startup.
 *
 * <p>This example shows the most common real addon job, cross-mod compatibility.
 * It does not add content of its own. It takes existing content from another mod
 * (the Aether) and registers it with Craftics so it works in turn-based combat.
 *
 * <p>{@link #onCrafticsInit()} also shows the two registration timings. The Moa's
 * AI is registered right away because it is this addon's own class. The Zanite
 * Sword lookup is deferred to server start because it depends on another mod.
 */
public final class ExampleCrafticsAddon implements CrafticsAddon {

	/**
	 * The Aether mod's Zanite Sword, the item this addon turns into a Craftics
	 * weapon. Referencing another mod's content by {@link Identifier} needs no
	 * compile-time dependency on that mod.
	 */
	private static final Identifier ZANITE_SWORD_ID = Identifier.of("aether", "zanite_sword");

	@Override
	public void onCrafticsInit() {
		// Register the Moa's combat AI under the key its enemy JSON names
		// ("ai": "exampleaddon:moa" in data/exampleaddon/craftics/enemies/moa.json).
		// MoaAI is this addon's own class, so it is safe to register right away.
		CrafticsAPI.registerAI("exampleaddon:moa", new MoaAI());

		// The Aether's Zanite Sword may not be in the item registry yet when
		// addons initialize, since mod load order is undefined. Defer that lookup
		// to server start, by which point every mod's items are registered.
		ServerLifecycleEvents.SERVER_STARTING.register(server -> registerZaniteSword());
	}

	/**
	 * Registers the Aether mod's Zanite Sword as a Craftics weapon, but only if
	 * the Aether mod is installed. If it is not, this is a harmless no-op.
	 */
	private static void registerZaniteSword() {
		if (!Registries.ITEM.containsId(ZANITE_SWORD_ID)) {
			// The Aether mod is not installed, so there is nothing to integrate.
			return;
		}
		Item zaniteSword = Registries.ITEM.get(ZANITE_SWORD_ID);

		// Register the Zanite Sword as a Craftics weapon: a slashing blade that
		// costs 1 action point, reaches 1 tile, and hits for 11. Its gravitational
		// effect weighs the struck enemy down with Slowness for 2 turns. Slowness
		// is the enemy-applicable "weighed down" effect, the same one Craftics uses
		// for the Shulker's levitation attack.
		//
		// The Abilities class has more building blocks (stun, armorIgnore, aoe,
		// knockbackDirection, pierce, and so on). Compose them with .and(...).
		CrafticsAPI.registerWeapon(zaniteSword, WeaponEntry.builder(zaniteSword)
			.damageType(DamageType.SLASHING)
			.attackPower(11)
			.apCost(1)
			.range(1)
			.ability(Abilities.applyEffect(CombatEffects.EffectType.SLOWNESS, 2, 0))
			.build());
	}
}
