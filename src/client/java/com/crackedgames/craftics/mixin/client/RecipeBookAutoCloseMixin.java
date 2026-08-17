package com.crackedgames.craftics.mixin.client;

import com.crackedgames.craftics.client.RecipeBookState;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Folds the recipe book away when the inventory closes.
 *
 * <p>Vanilla remembers the book's open state, so opening it once left it open for every
 * inventory afterwards until it was dismissed by hand - and while it is open the Craftics stat
 * and affinity panels are suppressed, so a book nobody meant to leave open quietly hid them
 * for the rest of the session.
 *
 * <p>Targets {@link HandledScreen} with an instanceof guard rather than {@link InventoryScreen}
 * directly, for the reason spelled out on {@code InventoryClickMixin}: InventoryScreen does not
 * declare these methods itself on every shard we build, and the static mixin remapper cannot
 * resolve a name that isn't there.
 */
@Mixin(HandledScreen.class)
public class RecipeBookAutoCloseMixin {

    @Inject(method = "removed", at = @At("HEAD"))
    private void craftics$closeRecipeBookOnExit(CallbackInfo ci) {
        if ((Object) this instanceof InventoryScreen screen) {
            RecipeBookState.closeIfOpen(screen);
        }
    }
}
