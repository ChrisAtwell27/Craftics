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

    public static void register() {
        CrafticsMod.LOGGER.info("Registering Craftics screen handlers...");
    }
}
