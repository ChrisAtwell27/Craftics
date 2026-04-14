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

    private static final RegistryKey<Item> GUIDE_BOOK_KEY =
        RegistryKey.of(RegistryKeys.ITEM, Identifier.of(CrafticsMod.MOD_ID, "guide_book"));

    public static final Item GUIDE_BOOK = registerItem("guide_book",
        new GuideBookItem(new Item.Settings()
            //? if >=1.21.2 {
            .registryKey(GUIDE_BOOK_KEY)
            //?}
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
