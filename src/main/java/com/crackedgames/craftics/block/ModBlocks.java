package com.crackedgames.craftics.block;

import com.crackedgames.craftics.CrafticsMod;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.MapColor;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

public class ModBlocks {

    public static final Block LEVEL_SELECT_BLOCK = registerBlock("level_select_block",
        new LevelSelectBlock(AbstractBlock.Settings.create()
            .mapColor(MapColor.OAK_TAN)
            .strength(2.0f, 3.0f)
            .sounds(net.minecraft.sound.BlockSoundGroup.WOOD)
            .luminance(state -> 7)
            .nonOpaque()
            .solidBlock((state, world, pos) -> false)
            .suffocates((state, world, pos) -> false)
            .blockVision((state, world, pos) -> false)
        )
    );

    public static final BlockEntityType<LevelSelectBlockEntity> LEVEL_SELECT_BLOCK_ENTITY =
        Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            Identifier.of(CrafticsMod.MOD_ID, "level_select_block_entity"),
            FabricBlockEntityTypeBuilder.create(LevelSelectBlockEntity::new, LEVEL_SELECT_BLOCK).build()
        );

    private static Block registerBlock(String name, Block block) {
        Identifier id = Identifier.of(CrafticsMod.MOD_ID, name);
        Registry.register(Registries.BLOCK, id, block);

        // Register block item
        Registry.register(Registries.ITEM, id,
            new BlockItem(block, new Item.Settings()));

        return block;
    }

    public static void register() {
        CrafticsMod.LOGGER.info("Registering Craftics blocks...");
    }
}
