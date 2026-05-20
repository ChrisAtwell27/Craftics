package com.crackedgames.craftics.block;

import com.crackedgames.craftics.CrafticsMod;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.util.Identifier;

public class ModScreenHandlers {

    public static final ScreenHandlerType<LevelSelectScreenHandler> LEVEL_SELECT_SCREEN_HANDLER =
        Registry.register(
            Registries.SCREEN_HANDLER,
            Identifier.of(CrafticsMod.MOD_ID, "level_select"),
            new ExtendedScreenHandlerType<>(LevelSelectScreenHandler::new,
                LevelSelectScreenHandler.LevelSelectData.PACKET_CODEC)
        );

    public static final ScreenHandlerType<com.crackedgames.craftics.screen.LootManagementScreenHandler>
        LOOT_MANAGEMENT_SCREEN_HANDLER =
        Registry.register(
            Registries.SCREEN_HANDLER,
            Identifier.of(CrafticsMod.MOD_ID, "loot_management"),
            new ScreenHandlerType<>(
                com.crackedgames.craftics.screen.LootManagementScreenHandler::new,
                net.minecraft.resource.featuretoggle.FeatureFlags.VANILLA_FEATURES));

    public static void register() {
        CrafticsMod.LOGGER.info("Registering Craftics screen handlers...");
    }
}
