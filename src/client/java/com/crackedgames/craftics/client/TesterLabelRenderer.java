package com.crackedgames.craftics.client;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;

/**
 * Renders a floating cosmetic title (e.g. "Founding Tester") above every
 * registered {@link TesterRegistry} player, in that tester's signature color.
 * Purely cosmetic and always visible (in and out of combat); it sits just above
 * the vanilla name tag and never replaces it.
 *
 * <p>Modeled on {@link PartyLabelRenderer}. Because {@link TesterRegistry} is
 * identical on every client, no server sync is required - each client filters
 * the players it is already rendering.
 */
public final class TesterLabelRenderer {

    private TesterLabelRenderer() {}

    /** Don't draw the label past this distance (squared) to match name-tag visibility / perf. */
    private static final double MAX_DIST_SQ = 48.0 * 48.0;

    /** Register the world-render callback. Called once from client init. */
    public static void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(TesterLabelRenderer::onRenderWorld);
    }

    private static void onRenderWorld(WorldRenderContext ctx) {
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
        int bgColor = (int) (client.options.getTextBackgroundOpacity(0.25f) * 255.0f) << 24;

        for (AbstractClientPlayerEntity p : client.world.getPlayers()) {
            if (p.isInvisible()) continue;
            TesterRegistry.Tester tester = TesterRegistry.get(p.getGameProfile().getName());
            if (tester == null) continue;

            Vec3d pos = p.getLerpedPos(tickDelta);
            if (pos.squaredDistanceTo(cam) > MAX_DIST_SQ) continue;

            MutableText label = Text.literal(tester.title());
            if (TesterRegistry.isBold(tester)) label.formatted(Formatting.BOLD);
            int color = TesterRegistry.colorOf(tester);
            float labelX = -textRenderer.getWidth(label) / 2.0f;

            matrices.push();
            matrices.translate(
                pos.x - cam.x,
                pos.y - cam.y + p.getHeight() + 0.9,
                pos.z - cam.z);
            matrices.multiply(camera.getRotation());
            matrices.scale(-0.025f, -0.025f, 0.025f);

            var matrix = matrices.peek().getPositionMatrix();
            // Background pass (see-through) then a crisp normal pass on top.
            textRenderer.draw(label, labelX, 0, color, false, matrix, consumers,
                TextRenderer.TextLayerType.SEE_THROUGH, bgColor, 0xF000F0);
            textRenderer.draw(label, labelX, 0, color, false, matrix, consumers,
                TextRenderer.TextLayerType.NORMAL, 0, 0xF000F0);
            matrices.pop();
        }
    }
}
