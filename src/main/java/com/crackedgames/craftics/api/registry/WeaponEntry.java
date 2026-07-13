package com.crackedgames.craftics.api.registry;

import com.crackedgames.craftics.api.WeaponAbilityHandler;
import com.crackedgames.craftics.combat.DamageType;
import net.minecraft.item.Item;
import org.jetbrains.annotations.Nullable;

import java.util.function.IntSupplier;

/**
 * Immutable definition of a weapon's Craftics combat stats and optional special ability.
 *
 * <p>Every item the player can use as a weapon in a Craftics turn must have a registered
 * {@code WeaponEntry}. Built-in vanilla weapons are registered by {@code VanillaWeapons};
 * addons register their own through {@code CrafticsAPI.registerWeapon}. Build entries
 * with {@link #builder(Item)}:
 *
 * <pre>{@code
 * WeaponEntry.builder(MyItems.RUNE_BLADE)
 *     .damageType(DamageType.SLASHING)
 *     .attackPower(9)
 *     .apCost(1)
 *     .range(1)
 *     .ability(Abilities.bleed().and(Abilities.sweepAdjacent(0.10, 0.05)))
 *     .build();
 * }</pre>
 *
 * @param item        the weapon item
 * @param damageType  the damage category used for affinity and armor-set bonuses
 * @param secondaryDamageType optional second affinity, or {@code null}. A hybrid weapon
 *                    also scales off this type, but at half weight, so it is never
 *                    strictly better than a weapon devoted to one affinity. Mob
 *                    resistances and immunities always resolve against {@code damageType}
 * @param attackPower base attack power, evaluated lazily so config-driven values reload
 *                    without restarting the server
 * @param apCost      action points spent per attack
 * @param range       attack range in tiles; negative values use special range constants
 *                    defined in {@code PlayerCombatStats}
 * @param isRanged    whether this is a ranged weapon; ranged weapons use line-of-sight
 *                    targeting instead of melee adjacency checks
 * @param breakChance probability ({@code 0.0} to {@code 1.0}) that one durability point
 *                    is consumed per attack; {@code 0.0} means the weapon never breaks
 * @param ability     optional on-hit effect, or {@code null} for a plain attack
 * @param targetlessCast optional tile-aimed action for weapons that do something to the
 *                    ground rather than to an enemy (arrow rain, trap-laying). When set,
 *                    attacking an empty tile runs this instead of being rejected
 * @since 0.2.0
 */
public record WeaponEntry(
    Item item,
    DamageType damageType,
    @Nullable DamageType secondaryDamageType,
    IntSupplier attackPower,
    int apCost,
    int range,
    boolean isRanged,
    double breakChance,
    @Nullable WeaponAbilityHandler ability,
    @Nullable com.crackedgames.craftics.api.TargetlessCastHandler targetlessCast
) {
    public static Builder builder(Item item) { return new Builder(item); }

    /** Fluent builder for {@link WeaponEntry}. */
    public static class Builder {
        private final Item item;
        private DamageType damageType = DamageType.PHYSICAL;
        private DamageType secondaryDamageType = null;
        private IntSupplier attackPower = () -> 1;
        private int apCost = 1;
        private int range = 1;
        private boolean isRanged = false;
        private double breakChance = 0.0;
        private WeaponAbilityHandler ability = null;
        private com.crackedgames.craftics.api.TargetlessCastHandler targetlessCast = null;

        public Builder(Item item) { this.item = item; }

        /** Damage category for affinity and armor-set bonuses. Default {@code PHYSICAL}. */
        public Builder damageType(DamageType dt) { this.damageType = dt; return this; }

        /**
         * A second affinity this weapon also scales off, at half weight. Use for hybrid
         * weapons (a bow that is both Water and Ranged). Mob resistances still resolve
         * against the primary {@link #damageType}. Default {@code null} - no hybrid.
         */
        public Builder secondaryDamageType(DamageType dt) { this.secondaryDamageType = dt; return this; }

        /** Fixed base attack power. Default {@code 1}. */
        public Builder attackPower(int power) { this.attackPower = () -> power; return this; }

        /**
         * Dynamic base attack power, evaluated on every attack so config-driven values
         * take effect without restarting the server.
         */
        public Builder attackPower(IntSupplier supplier) { this.attackPower = supplier; return this; }

        /** Action points spent per attack. Default {@code 1}. */
        public Builder apCost(int cost) { this.apCost = cost; return this; }

        /** Attack range in tiles. Default {@code 1}. */
        public Builder range(int range) { this.range = range; return this; }

        /** Whether this is a ranged weapon; ranged weapons use line-of-sight targeting. Default {@code false}. */
        public Builder ranged(boolean ranged) { this.isRanged = ranged; return this; }

        /**
         * Probability that one durability point is consumed per attack.
         * {@code 0.0} means the weapon never breaks in Craftics combat. Default {@code 0.0}.
         */
        public Builder breakChance(double chance) { this.breakChance = chance; return this; }

        /** Optional on-hit ability. Default {@code null} (plain attack). */
        public Builder ability(WeaponAbilityHandler handler) { this.ability = handler; return this; }

        /**
         * Optional tile-aimed action, run when the player attacks an empty tile. Gives a
         * weapon something to do with the ground - rain arrows, lay a trap - instead of
         * being told there is no enemy there. Default {@code null}.
         */
        public Builder targetlessCast(com.crackedgames.craftics.api.TargetlessCastHandler handler) {
            this.targetlessCast = handler;
            return this;
        }

        public WeaponEntry build() {
            return new WeaponEntry(item, damageType, secondaryDamageType, attackPower, apCost,
                range, isRanged, breakChance, ability, targetlessCast);
        }
    }
}
