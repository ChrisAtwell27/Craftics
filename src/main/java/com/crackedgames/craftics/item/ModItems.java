package com.crackedgames.craftics.item;

import com.crackedgames.craftics.CrafticsMod;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;

public class ModItems {

    public static final Item GUIDE_BOOK = registerItem("guide_book",
        new GuideBookItem(new Item.Settings()
            .maxCount(1)
            .rarity(Rarity.UNCOMMON)
        )
    );

    private static Item registerItem(String name, Item item) {
        return Registry.register(Registries.ITEM, Identifier.of(CrafticsMod.MOD_ID, name), item);
    }

    public static void register() {
        CrafticsMod.LOGGER.info("Registering Craftics items...");
    }
}
