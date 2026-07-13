package com.crackedgames.craftics.item;

import com.crackedgames.craftics.CrafticsMod;
import com.crackedgames.craftics.block.ModBlocks;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * The Craftics creative-inventory tab. Collects everything the mod registers - the
 * control items and the world-editing marker blocks - into one group, so a builder does
 * not have to fish Craftics content out of the vanilla tabs.
 *
 * <p>Only items with a real inventory model belong here: the level-select ghost block has
 * no {@code BlockItem}, so it is deliberately absent. Content from compat mods (Simply
 * Swords, Immersive Armors, ...) keeps its own tabs - this group is Craftics' own items.
 */
public final class ModItemGroups {

    private ModItemGroups() {}

    private static final RegistryKey<ItemGroup> CRAFTICS_GROUP_KEY =
        RegistryKey.of(RegistryKeys.ITEM_GROUP, Identifier.of(CrafticsMod.MOD_ID, "craftics"));

    /**
     * Build and register the tab. Called from {@code CrafticsMod.onInitialize} AFTER
     * {@code ModBlocks.register()} and {@code ModItems.register()}, so every item it
     * references already exists in the registry.
     */
    public static void register() {
        ItemGroup group = ItemGroup.create(ItemGroup.Row.TOP, 0)
            .displayName(Text.translatable("itemGroup.craftics.craftics"))
            // The guide book is the mod's front door, so it fronts the tab.
            .icon(() -> new ItemStack(ModItems.GUIDE_BOOK))
            .entries((context, entries) -> {
                entries.add(ModItems.GUIDE_BOOK);
                entries.add(ModItems.MOVE_ITEM);
                entries.add(ModBlocks.LEVEL_SELECT_BLOCK);
                entries.add(ModBlocks.ARENA_CORNER_BLOCK);
                entries.add(ModBlocks.SCENE_SPAWN_BLOCK);
                entries.add(ModBlocks.NPC_MARKER_BLOCK);
                entries.add(ModBlocks.STAND_MARKER_BLOCK);
            })
            .build();
        Registry.register(Registries.ITEM_GROUP, CRAFTICS_GROUP_KEY, group);
        CrafticsMod.LOGGER.info("Registered Craftics creative tab");
    }
}
