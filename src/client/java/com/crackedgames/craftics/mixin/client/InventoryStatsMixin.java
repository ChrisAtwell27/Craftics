package com.crackedgames.craftics.mixin.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Renders player progression stats on the right side of the inventory screen.
 */
@Mixin(InventoryScreen.class)
public class InventoryStatsMixin {

    @Inject(method = "render", at = @At("TAIL"))
    private void craftics$renderStats(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!com.crackedgames.craftics.client.CombatState.isStatsOverlayVisible()) return;
        InventoryScreen screen = (InventoryScreen)(Object)this;
        int sw = screen.width, sh = screen.height;
        com.crackedgames.craftics.client.StatsPanel.render(context, sw, sh, mouseX, mouseY);
        com.crackedgames.craftics.client.DamageTypePanel.render(context, sw, sh, mouseX, mouseY);
    }
}
