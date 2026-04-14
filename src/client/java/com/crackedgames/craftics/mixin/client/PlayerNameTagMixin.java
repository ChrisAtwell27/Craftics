package com.crackedgames.craftics.mixin.client;

import com.crackedgames.craftics.client.CombatState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
//? if >=1.21.2 {
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
//?}
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
 *
 * <p>Targets {@code PlayerEntityRenderer.renderLabelIfPresent}, whose signature
 * changed in 1.21.2+: the {@code AbstractClientPlayerEntity + float tickDelta}
 * parameters were replaced with a single {@code PlayerEntityRenderState}. The
 * render state has no UUID field, so on 1.21.2+ we match party members by
 * username (String) against {@link CombatState.TurnOrderEntry#name()} instead
 * of UUID.
 */
@Mixin(PlayerEntityRenderer.class)
public class PlayerNameTagMixin {

    //? if <=1.21.1 {
    /*@Inject(method = "renderLabelIfPresent(Lnet/minecraft/client/network/AbstractClientPlayerEntity;Lnet/minecraft/text/Text;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;IF)V",
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
        float labelHeight = player.getHeight() + 0.5f;
        craftics$drawCustomLabel(client, matrices, vertexConsumers, displayName, labelHeight, isActiveTurn, light);
    }
    *///?} else {
    @Inject(method = "renderLabelIfPresent(Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;Lnet/minecraft/text/Text;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At("HEAD"), cancellable = true)
    private void craftics$customNameTag(PlayerEntityRenderState state, Text text,
                                         MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                         int light, CallbackInfo ci) {
        if (!CombatState.isInCombat()) return;

        java.util.List<CombatState.TurnOrderEntry> turnOrder = CombatState.getTurnOrderList();
        if (turnOrder.isEmpty()) return; // solo play

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        // Render state has no UUID — match by username instead.
        String stateName = state.name;
        if (stateName == null) return;
        // Don't modify self name tag
        if (stateName.equals(client.player.getGameProfile().getName())) return;

        boolean isActiveTurn = false;
        boolean isPartyMember = false;

        for (CombatState.TurnOrderEntry entry : turnOrder) {
            if (stateName.equals(entry.name())) {
                isPartyMember = true;
                if (entry.isCurrent()) isActiveTurn = true;
                break;
            }
        }

        if (!isPartyMember) return;

        // Cancel vanilla rendering and do custom
        ci.cancel();

        float labelHeight = state.height + 0.5f;
        craftics$drawCustomLabel(client, matrices, vertexConsumers, stateName, labelHeight, isActiveTurn, light);
    }
    //?}

    /**
     * Shared drawing routine used by both shard-specific injectors above.
     * Translates into label space relative to the camera and draws the
     * party-highlighted custom label at {@code labelHeight} above the entity origin.
     */
    private void craftics$drawCustomLabel(MinecraftClient client, MatrixStack matrices,
                                           VertexConsumerProvider vertexConsumers,
                                           String displayName, float labelHeight,
                                           boolean isActiveTurn, int light) {
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

        matrices.push();
        matrices.translate(0.0, labelHeight, 0.0);
        matrices.multiply(client.gameRenderer.getCamera().getRotation());
        matrices.scale(-0.025f, -0.025f, 0.025f);

        TextRenderer textRenderer = client.textRenderer;
        float x = -textRenderer.getWidth(customLabel) / 2.0f;
        int bgAlpha = (int)(client.options.getTextBackgroundOpacity(0.25f) * 255.0f) << 24;

        int bgColor = isActiveTurn ? (0x40005500 | (bgAlpha & 0xFF000000))
                                   : (0x40003355 | (bgAlpha & 0xFF000000));
        textRenderer.draw(customLabel, x, 0, nameColor, false,
            matrices.peek().getPositionMatrix(), vertexConsumers,
            TextRenderer.TextLayerType.SEE_THROUGH, bgColor, light);
        textRenderer.draw(customLabel, x, 0, nameColor, false,
            matrices.peek().getPositionMatrix(), vertexConsumers,
            TextRenderer.TextLayerType.NORMAL, 0, light);

        matrices.pop();
    }
}
