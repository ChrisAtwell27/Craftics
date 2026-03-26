package com.crackedgames.craftics.mixin.client;

import com.crackedgames.craftics.client.CombatState;
import com.crackedgames.craftics.client.DamageTypePanel;
import com.crackedgames.craftics.combat.PlayerProgression;
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
        InventoryScreen screen = (InventoryScreen)(Object)this;
        int screenWidth = screen.width;
        int screenHeight = screen.height;

        // Position the stats panel to the right of the inventory
        int panelX = (screenWidth / 2) + 90;
        int panelY = (screenHeight / 2) - 80;
        int lineHeight = 12;

        // Background panel
        context.fill(panelX - 4, panelY - 4, panelX + 120, panelY + 140, 0xCC000000);
        context.fill(panelX - 3, panelY - 3, panelX + 119, panelY + 139, 0xCC1A1A2E);

        // Title
        context.drawTextWithShadow(net.minecraft.client.MinecraftClient.getInstance().textRenderer,
            "\u00a76\u00a7l\u2605 Stats", panelX, panelY, 0xFFFFAA00);
        panelY += lineHeight + 2;

        // Level
        context.drawTextWithShadow(net.minecraft.client.MinecraftClient.getInstance().textRenderer,
            "\u00a7fLevel \u00a7e\u00a7l" + CombatState.getPlayerLevel(), panelX, panelY, 0xFFFFFFFF);
        panelY += lineHeight;

        // Unspent points
        int unspent = CombatState.getUnspentPoints();
        if (unspent > 0) {
            context.drawTextWithShadow(net.minecraft.client.MinecraftClient.getInstance().textRenderer,
                "\u00a7a" + unspent + " point" + (unspent != 1 ? "s" : "") + " available!", panelX, panelY, 0xFF55FF55);
        }
        panelY += lineHeight + 2;

        // Divider
        context.fill(panelX, panelY, panelX + 115, panelY + 1, 0xFF444444);
        panelY += 4;

        // Each stat
        PlayerProgression.Stat[] stats = PlayerProgression.Stat.values();
        for (int i = 0; i < stats.length; i++) {
            PlayerProgression.Stat stat = stats[i];
            int points = CombatState.getStatPoints(i);
            int effective = stat.baseValue + points;

            String line = stat.icon + " " + stat.displayName + " \u00a77" + effective;
            if (points > 0) {
                line += " \u00a78(+" + points + ")";
            }

            context.drawTextWithShadow(net.minecraft.client.MinecraftClient.getInstance().textRenderer, line, panelX, panelY, 0xFFFFFFFF);
            panelY += lineHeight;
        }

        // Divider
        panelY += 2;
        context.fill(panelX, panelY, panelX + 115, panelY + 1, 0xFF444444);
        panelY += 4;

        // Emeralds
        context.drawTextWithShadow(net.minecraft.client.MinecraftClient.getInstance().textRenderer,
            "\u00a7a\u00a7l\u2B22 \u00a7f" + CombatState.getEmeralds() + " Emeralds", panelX, panelY, 0xFF55FF55);

        // Damage type affinity panel on the left side
        DamageTypePanel.render(context, screenWidth, screenHeight);
    }
}
