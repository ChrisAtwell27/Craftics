package com.crackedgames.craftics.client;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/**
 * A green arrow that bobs above whichever combatant the cursor is over, confirming what the
 * player is about to select before they commit to it.
 *
 * <p>Reads the hover state the HUD already tracks ({@link CombatState#getHoveredEnemyId()} and
 * {@link CombatState#getHoveredPlayerUuid()}), so it needs no state or networking of its own
 * and can never disagree with what the rest of the UI thinks is hovered.
 *
 * <p>Drawn above the status-effect icons so the two never overlap.
 *
 * @since 0.3.0
 */
public final class HoverTargetArrowRenderer {

    private HoverTargetArrowRenderer() {}

    private static final Identifier ARROW =
        Identifier.of("craftics", "textures/gui/effects/hover_arrow.png");

    /** Sits above the effect-icon row (which floats at entity height + 0.75). */
    private static final double HEIGHT_OFFSET = 1.25;

    /** Side length of the arrow in world units - bigger than an effect icon; it's a pointer. */
    private static final float ARROW_SIZE = 0.40f;

    /** Bob amplitude in blocks, and how fast it cycles. */
    private static final double BOB_AMPLITUDE = 0.12;
    private static final double BOB_SPEED = 0.15;

    private static final int FULL_BRIGHT = 0xF000F0;

    public static void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(HoverTargetArrowRenderer::onRenderWorld);
    }

    private static void onRenderWorld(WorldRenderContext ctx) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.options.hudHidden) return;
        if (!CombatState.isInCombat()) return;

        int hoveredEnemyId = CombatState.getHoveredEnemyId();
        String hoveredPlayerUuid = CombatState.getHoveredPlayerUuid();
        boolean anyHover = hoveredEnemyId != -1
            || (hoveredPlayerUuid != null && !hoveredPlayerUuid.isEmpty());
        if (!anyHover) return;

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

        Entity target = findHovered(client, hoveredEnemyId, hoveredPlayerUuid);
        if (target == null) return;

        // Bob on world time rather than a private counter, so the arrow keeps a steady rhythm
        // instead of jumping whenever the hover target changes.
        double bob = BOB_AMPLITUDE
            * Math.sin((client.world.getTime() + tickDelta) * BOB_SPEED);

        Vec3d pos = target.getLerpedPos(tickDelta);

        matrices.push();
        matrices.translate(
            pos.x - cam.x,
            pos.y - cam.y + target.getHeight() + HEIGHT_OFFSET + bob,
            pos.z - cam.z);
        matrices.multiply(camera.getRotation());

        Matrix4f matrix = matrices.peek().getPositionMatrix();

        // See-through layer so the arrow reads even when its target is behind cover.
        VertexConsumer vc = consumers.getBuffer(RenderLayer.getTextSeeThrough(ARROW));
        float h = ARROW_SIZE / 2.0f;
        vc.vertex(matrix, -h,  h, 0).color(255, 255, 255, 255).texture(0f, 1f).light(FULL_BRIGHT);
        vc.vertex(matrix,  h,  h, 0).color(255, 255, 255, 255).texture(1f, 1f).light(FULL_BRIGHT);
        vc.vertex(matrix,  h, -h, 0).color(255, 255, 255, 255).texture(1f, 0f).light(FULL_BRIGHT);
        vc.vertex(matrix, -h, -h, 0).color(255, 255, 255, 255).texture(0f, 0f).light(FULL_BRIGHT);

        matrices.pop();
    }

    /** The hovered entity: an enemy/ally by entity id, or a party member by UUID. */
    private static Entity findHovered(MinecraftClient client, int enemyId, String playerUuid) {
        if (enemyId != -1) {
            return client.world.getEntityById(enemyId);
        }
        if (playerUuid == null || playerUuid.isEmpty()) return null;
        for (PlayerEntity p : client.world.getPlayers()) {
            if (p.getUuid().toString().equals(playerUuid)) return p;
        }
        return null;
    }
}
