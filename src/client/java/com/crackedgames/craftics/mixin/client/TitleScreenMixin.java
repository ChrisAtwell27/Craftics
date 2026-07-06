package com.crackedgames.craftics.mixin.client;

import com.crackedgames.craftics.client.menu.CrafticsTitleScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Replaces the vanilla title screen with {@link CrafticsTitleScreen} wherever
 * it would appear (game start, quitting a world, disconnects).
 *
 * <p>The swap happens at the HEAD of {@code TitleScreen.init()} - before any
 * vanilla widgets or the Realms notification client are created - and cancels
 * the rest of init. {@code setScreen} re-entrancy is safe here: the inner call
 * detaches the half-initialized TitleScreen (its {@code removed()} runs, the
 * Craftics screen becomes {@code currentScreen}) and the cancelled outer init
 * never touches the discarded instance again. The Craftics screen is not a
 * TitleScreen subclass, so the swap can never recurse.</p>
 */
@Mixin(TitleScreen.class)
public class TitleScreenMixin {

    @Inject(method = "init()V", at = @At("HEAD"), cancellable = true)
    private void craftics$swapToCrafticsMenu(CallbackInfo ci) {
        ci.cancel();
        MinecraftClient.getInstance().setScreen(new CrafticsTitleScreen());
    }
}
