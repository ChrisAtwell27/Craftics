package com.crackedgames.craftics.vfx.weapon;

import net.minecraft.item.Item;
import net.minecraft.item.Items;

/** Maps a weapon Item to its material tier, which drives visual palette + sound pitch. */
public final class WeaponPalette {
    private WeaponPalette() {}

    public enum Tier {
        WOOD(0.9f),
        STONE(0.95f),
        IRON(1.0f),
        GOLD(1.05f),
        DIAMOND(1.1f),
        NETHERITE(0.85f);

        public final float pitchMultiplier;
        Tier(float p) { this.pitchMultiplier = p; }
    }

    public static Tier of(Item item) {
        if (item == Items.WOODEN_SWORD || item == Items.WOODEN_AXE || item == Items.WOODEN_SHOVEL || item == Items.WOODEN_HOE) return Tier.WOOD;
        if (item == Items.STONE_SWORD || item == Items.STONE_AXE || item == Items.STONE_SHOVEL || item == Items.STONE_HOE) return Tier.STONE;
        if (item == Items.IRON_SWORD || item == Items.IRON_AXE || item == Items.IRON_SHOVEL || item == Items.IRON_HOE) return Tier.IRON;
        if (item == Items.GOLDEN_SWORD || item == Items.GOLDEN_AXE || item == Items.GOLDEN_SHOVEL || item == Items.GOLDEN_HOE) return Tier.GOLD;
        if (item == Items.DIAMOND_SWORD || item == Items.DIAMOND_AXE || item == Items.DIAMOND_SHOVEL || item == Items.DIAMOND_HOE) return Tier.DIAMOND;
        if (item == Items.NETHERITE_SWORD || item == Items.NETHERITE_AXE || item == Items.NETHERITE_SHOVEL || item == Items.NETHERITE_HOE) return Tier.NETHERITE;
        return Tier.IRON;
    }
}
