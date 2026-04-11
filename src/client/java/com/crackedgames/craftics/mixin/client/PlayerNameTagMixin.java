package com.crackedgames.craftics.mixin.client;

import com.crackedgames.craftics.client.CombatState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to render custom highlighted name tags for party members during combat.
 * Shows the active turn player's name in bright green with a glow, and other
 * party members in team color.
 */
@Mixin(PlayerEntityRenderer.class)
public class PlayerNameTagMixin {

    @Inject(method = "renderLabelIfPresent(Lnet/minecraft/client/network/AbstractClientPlayerEntity;Lnet/minecraft/text/Text;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;IF)V",
            at = @At("HEAD"), cancellable = true)
    private void craftics$customNameTag(AbstractClientPlayerEntity player, Text text,
                                         MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                         int light, float tickDelta, CallbackInfo ci) {
        if (!CombatState.isInCombat()) return;

        java.util.List<CombatState.TurnOrderEntry> turnOrder = CombatState.getTurnOrderList();
        if (turnOrder.isEmpty()) return; // solo play

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        // Don't modify self name tag
        if (player.getUuid().equals(client.player.getUuid())) return;

        String playerUuid = player.getUuid().toString();
        boolean isActiveTurn = false;
        boolean isPartyMember = false;

        for (CombatState.TurnOrderEntry entry : turnOrder) {
            if (entry.uuid().equals(playerUuid)) {
                isPartyMember = true;
                if (entry.isCurrent()) isActiveTurn = true;
                break;
            }
        }

        if (!isPartyMember) return;

        // Cancel vanilla rendering and do custom
        ci.cancel();

        String displayName = player.getName().getString();
        String prefix;
        int nameColor;
        if (isActiveTurn) {
            prefix = "\u25B6 "; // triangle indicator
            nameColor = 0xFF55FF55; // bright green
        } else {
            prefix = "";
            nameColor = 0xFF55FFFF; // cyan for party member
        }

        Text customLabel = Text.literal(prefix + displayName);

        // Render the custom name tag at the vanilla position
        matrices.push();
        matrices.translate(0.0, player.getHeight() + 0.5, 0.0);
        matrices.multiply(client.gameRenderer.getCamera().getRotation());
        matrices.scale(-0.025f, -0.025f, 0.025f);

        TextRenderer textRenderer = client.textRenderer;
        float x = -textRenderer.getWidth(customLabel) / 2.0f;
        int bgAlpha = (int)(MinecraftClient.getInstance().options.getTextBackgroundOpacity(0.25f) * 255.0f) << 24;

        // Background plate with team color tint
        int bgColor = isActiveTurn ? (0x40005500 | (bgAlpha & 0xFF000000))
                                   : (0x40003355 | (bgAlpha & 0xFF000000));
        textRenderer.draw(customLabel, x, 0, nameColor, false,
            matrices.peek().getPositionMatrix(), vertexConsumers,
            TextRenderer.TextLayerType.SEE_THROUGH, bgColor, light);
        // Foreground text
        textRenderer.draw(customLabel, x, 0, nameColor, false,
            matrices.peek().getPositionMatrix(), vertexConsumers,
            TextRenderer.TextLayerType.NORMAL, 0, light);

        matrices.pop();
    }
}
