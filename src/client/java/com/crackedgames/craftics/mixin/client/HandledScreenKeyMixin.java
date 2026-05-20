package com.crackedgames.craftics.mixin.client;

import com.crackedgames.craftics.CrafticsClient;
import com.crackedgames.craftics.client.CombatState;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.option.KeyBinding;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lets the Craftics "hide/reveal inventory UI" keybind toggle the stats and
 * damage-affinity panels while the inventory screen is open — keybindings
 * otherwise don't fire while a screen is capturing input.
 */
@Mixin(HandledScreen.class)
public class HandledScreenKeyMixin {

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void craftics$toggleInventoryUi(int keyCode, int scanCode, int modifiers,
                                            CallbackInfoReturnable<Boolean> cir) {
        if (!((Object) this instanceof InventoryScreen)) return;
        KeyBinding key = CrafticsClient.getToggleUiKey();
        if (key != null && key.matchesKey(keyCode, scanCode)) {
            CombatState.toggleStatsOverlay();
            cir.setReturnValue(true);
        }
    }
}
