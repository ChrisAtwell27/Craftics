package com.crackedgames.craftics.mixin.client;

import net.minecraft.client.sound.MusicTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Suppresses all vanilla Minecraft background music. Craftics plays its own
 * context-driven soundtrack (biome / boss / event tracks under the Jukebox/Note Blocks
 * category), so the vanilla menu / creative / biome music must never start.
 *
 * <p>{@code MusicTracker.tick()} is the only thing that selects and starts vanilla music;
 * cancelling it at HEAD means no vanilla track is ever picked or played. Because mixins are
 * applied at class load, this is in effect before the title-screen music can begin.</p>
 */
@Mixin(MusicTracker.class)
public class MusicTrackerMixin {

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void craftics$suppressVanillaMusic(CallbackInfo ci) {
        ci.cancel();
    }
}
