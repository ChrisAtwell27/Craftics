package com.crackedgames.craftics.vfx.weapon;

import com.crackedgames.craftics.vfx.VfxDescriptor;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;

/** Maps (weapon item + hit outcome) → VfxDescriptor. */
public final class WeaponVfxSelector {
    private WeaponVfxSelector() {}

    public enum Outcome {
        BASIC_HIT,
        CRIT,
        EXECUTE,
        SWEEP_SECONDARY,     // extra hit on a sweep secondary target
        SMASH_AOE,
        PIERCE_SECONDARY,
        THROWN,              // for trident thrown attack
        AP_REFUND            // hoe Special proc
    }

    public static VfxDescriptor select(Item item, Outcome outcome) {
        // Abilities override weapon base
        if (outcome == Outcome.EXECUTE) return WeaponVfx.SWORD_NETHERITE_EXECUTE;
        if (outcome == Outcome.SMASH_AOE) return WeaponVfx.MACE_SMASH_AOE;
        if (outcome == Outcome.SWEEP_SECONDARY) return WeaponVfx.SWORD_SWEEP;
        if (outcome == Outcome.PIERCE_SECONDARY) return WeaponVfx.CROSSBOW_PIERCE_FLOURISH;
        if (outcome == Outcome.THROWN) return WeaponVfx.TRIDENT_THROWN;
        if (outcome == Outcome.AP_REFUND) return WeaponVfx.HOE_AP_REFUND_FLOURISH;

        if (item == Items.AIR) return WeaponVfx.FIST;

        String path = Registries.ITEM.getId(item).getPath();

        if (path.endsWith("_sword")) {
            if (outcome == Outcome.CRIT && item == Items.DIAMOND_SWORD) return WeaponVfx.SWORD_DIAMOND_CRIT;
            return WeaponVfx.SWORD_BASIC;
        }
        if (path.endsWith("_axe")) return WeaponVfx.AXE_CLEAVE;
        if (item == Items.MACE) return WeaponVfx.MACE_SLAM;
        if (item == Items.BOW) return WeaponVfx.BOW;
        if (item == Items.CROSSBOW) return WeaponVfx.CROSSBOW;
        if (item == Items.TRIDENT) return WeaponVfx.TRIDENT_STAB;
        if (path.endsWith("_shovel")) {
            WeaponPalette.Tier tier = WeaponPalette.of(item);
            return (tier == WeaponPalette.Tier.DIAMOND || tier == WeaponPalette.Tier.NETHERITE)
                ? WeaponVfx.SHOVEL_HEAVY : WeaponVfx.SHOVEL;
        }
        if (path.endsWith("_hoe")) return WeaponVfx.HOE;

        return WeaponVfx.FIST;
    }
}
