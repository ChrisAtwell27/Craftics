package com.crackedgames.craftics.mixin.client;

import net.minecraft.client.gui.screen.world.WorldCreator;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.gen.WorldPreset;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.file.Path;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Default the world creation screen to the Craftics world preset (void world)
 * instead of the normal overworld generation.
 */
@Mixin(WorldCreator.class)
public abstract class WorldCreatorMixin {

    @Inject(method = "<init>", at = @At("TAIL"))
    private void craftics$defaultToCrafticsPreset(
            Path savesDirectory,
            net.minecraft.client.world.GeneratorOptionsHolder generatorOptionsHolder,
            Optional<RegistryKey<WorldPreset>> defaultPreset,
            OptionalLong seed,
            CallbackInfo ci) {
        WorldCreator self = (WorldCreator) (Object) this;
        for (WorldCreator.WorldType type : self.getNormalWorldTypes()) {
            if (type.preset().getKey().isPresent()
                    && type.preset().getKey().get().getValue().getNamespace().equals("craftics")) {
                self.setWorldType(type);
                break;
            }
        }
    }
}
