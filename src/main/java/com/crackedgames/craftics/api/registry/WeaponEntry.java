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
