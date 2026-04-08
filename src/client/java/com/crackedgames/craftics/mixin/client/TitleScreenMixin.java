package com.crackedgames.craftics.mixin.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds a beta notice and anti-piracy message to the Minecraft title screen.
 */
@Mixin(TitleScreen.class)
public class TitleScreenMixin {

    @Inject(method = "render", at = @At("TAIL"))
    private void craftics$renderBetaNotice(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        TitleScreen screen = (TitleScreen) (Object) this;
        var textRenderer = net.minecraft.client.MinecraftClient.getInstance().textRenderer;
        int screenW = screen.width;

        String line1 = "THIS IS A BETA TEST OF CRAFTICS.";
        String line2 = "IF YOU DOWNLOADED THIS ON CURSEFORGE, IT IS NOT A LEGITIMATE COPY.";
        String line3 = "THIS MOD IS CREATED BY CRACKED GAMES LLC";

        int y = 2;
        int lineHeight = 10;
        int color = 0xFFFF5555; // red

        context.drawCenteredTextWithShadow(textRenderer, Text.literal(line1), screenW / 2, y, color);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(line2), screenW / 2, y + lineHeight, color);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(line3), screenW / 2, y + lineHeight * 2, 0xFFFFAA00); // gold
    }
}
