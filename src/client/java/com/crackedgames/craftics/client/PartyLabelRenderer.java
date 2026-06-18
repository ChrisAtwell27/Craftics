package com.crackedgames.craftics.client;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Renders a floating "Active In Party" label above every mob in the local
 * player's battle party. Membership is pushed from the server via
 * {@code PartyMobsSyncPayload}; the label is purely cosmetic and never touches
 * the mob's own name tag.
 *
 * @since 0.3.0
 */
public final class PartyLabelRenderer {

    private PartyLabelRenderer() {}

    /** Entity UUIDs of the local player's party mobs (server-synced). */
    private static final Set<UUID> PARTY_MOBS = ConcurrentHashMap.newKeySet();

    private static final Text LABEL =
        Text.literal("Active In Party").formatted(Formatting.GREEN, Formatting.BOLD);

    /** Replace the tracked party-mob UUID set, called from the sync receiver. */
    public static void setPartyMobs(Set<UUID> uuids) {
        PARTY_MOBS.clear();
        PARTY_MOBS.addAll(uuids);
    }

    /** Forget all party mobs (on disconnect). */
    public static void clear() {
        PARTY_MOBS.clear();
    }

    /** Register the world-render callback. Called once from client init. */
    public static void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(PartyLabelRenderer::onRenderWorld);
    }

    private static void onRenderWorld(WorldRenderContext ctx) {
        if (PARTY_MOBS.isEmpty()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return;
        if (client.options.hudHidden) return;

        MatrixStack matrices = ctx.matrixStack();
        VertexConsumerProvider consumers = ctx.consumers();
        if (matrices == null || consumers == null) return;

        Camera camera = ctx.camera();
        Vec3d cam = camera.getPos();
        //? if <=1.21.4 {
        float tickDelta = ctx.tickCounter().getTickDelta(false);
        //?} else {
        /*float tickDelta = ctx.tickCounter().getTickProgress(false);
        *///?}
        TextRenderer textRenderer = client.textRenderer;

        float labelX = -textRenderer.getWidth(LABEL) / 2.0f;
        int bgColor = (int) (client.options.getTextBackgroundOpacity(0.25f) * 255.0f) << 24;

        for (Entity entity : client.world.getEntities()) {
            if (!(entity instanceof MobEntity mob)) continue;
            if (!PARTY_MOBS.contains(mob.getUuid())) continue;

            Vec3d pos = mob.getLerpedPos(tickDelta);
            matrices.push();
            matrices.translate(
                pos.x - cam.x,
                pos.y - cam.y + mob.getHeight() + 0.55,
                pos.z - cam.z);
            matrices.multiply(camera.getRotation());
            matrices.scale(-0.025f, -0.025f, 0.025f);

            var matrix = matrices.peek().getPositionMatrix();
            // Background pass (see-through) then a crisp normal pass on top.
            textRenderer.draw(LABEL, labelX, 0, 0xFF55FF55, false, matrix, consumers,
                TextRenderer.TextLayerType.SEE_THROUGH, bgColor, 0xF000F0);
            textRenderer.draw(LABEL, labelX, 0, 0xFF55FF55, false, matrix, consumers,
                TextRenderer.TextLayerType.NORMAL, 0, 0xF000F0);
            matrices.pop();
        }
    }
}
