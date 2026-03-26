package net.minecraft.client.render.debug;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.DrawStyle;
import net.minecraft.client.render.Frustum;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.ColorHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.debug.DebugDataStore;
import net.minecraft.world.debug.gizmo.GizmoDrawing;

@Environment(EnvType.CLIENT)
public class ChunkBorderDebugRenderer implements DebugRenderer.Renderer {
	private static final float field_63585 = 4.0F;
	private static final float field_63586 = 1.0F;
	private final MinecraftClient client;
	private static final int DARK_CYAN = ColorHelper.getArgb(255, 0, 155, 155);
	private static final int YELLOW = ColorHelper.getArgb(255, 255, 255, 0);
	private static final int LIGHT_RED = ColorHelper.fromFloats(1.0F, 0.25F, 0.25F, 1.0F);

	public ChunkBorderDebugRenderer(MinecraftClient client) {
		this.client = client;
	}

	@Override
	public void render(double cameraX, double cameraY, double cameraZ, DebugDataStore store, Frustum frustum, float tickProgress) {
		Entity entity = this.client.gameRenderer.getCamera().getFocusedEntity();
		float f = this.client.world.getBottomY();
		float g = this.client.world.getTopYInclusive() + 1;
		ChunkSectionPos chunkSectionPos = ChunkSectionPos.from(entity.getBlockPos());
		double d = chunkSectionPos.getMinX();
		double e = chunkSectionPos.getMinZ();

		for (int i = -16; i <= 32; i += 16) {
			for (int j = -16; j <= 32; j += 16) {
				GizmoDrawing.line(new Vec3d(d + i, f, e + j), new Vec3d(d + i, g, e + j), ColorHelper.fromFloats(0.5F, 1.0F, 0.0F, 0.0F), 4.0F);
			}
		}

		for (int i = 2; i < 16; i += 2) {
			int j = i % 4 == 0 ? DARK_CYAN : YELLOW;
			GizmoDrawing.line(new Vec3d(d + i, f, e), new Vec3d(d + i, g, e), j, 1.0F);
			GizmoDrawing.line(new Vec3d(d + i, f, e + 16.0), new Vec3d(d + i, g, e + 16.0), j, 1.0F);
		}

		for (int i = 2; i < 16; i += 2) {
			int j = i % 4 == 0 ? DARK_CYAN : YELLOW;
			GizmoDrawing.line(new Vec3d(d, f, e + i), new Vec3d(d, g, e + i), j, 1.0F);
			GizmoDrawing.line(new Vec3d(d + 16.0, f, e + i), new Vec3d(d + 16.0, g, e + i), j, 1.0F);
		}

		for (int i = this.client.world.getBottomY(); i <= this.client.world.getTopYInclusive() + 1; i += 2) {
			float h = i;
			int k = i % 8 == 0 ? DARK_CYAN : YELLOW;
			GizmoDrawing.line(new Vec3d(d, h, e), new Vec3d(d, h, e + 16.0), k, 1.0F);
			GizmoDrawing.line(new Vec3d(d, h, e + 16.0), new Vec3d(d + 16.0, h, e + 16.0), k, 1.0F);
			GizmoDrawing.line(new Vec3d(d + 16.0, h, e + 16.0), new Vec3d(d + 16.0, h, e), k, 1.0F);
			GizmoDrawing.line(new Vec3d(d + 16.0, h, e), new Vec3d(d, h, e), k, 1.0F);
		}

		for (int i = 0; i <= 16; i += 16) {
			for (int j = 0; j <= 16; j += 16) {
				GizmoDrawing.line(new Vec3d(d + i, f, e + j), new Vec3d(d + i, g, e + j), LIGHT_RED, 4.0F);
			}
		}

		GizmoDrawing.box(
				new Box(
					chunkSectionPos.getMinX(),
					chunkSectionPos.getMinY(),
					chunkSectionPos.getMinZ(),
					chunkSectionPos.getMaxX() + 1,
					chunkSectionPos.getMaxY() + 1,
					chunkSectionPos.getMaxZ() + 1
				),
				DrawStyle.stroked(LIGHT_RED, 1.0F)
			)
			.ignoreOcclusion();

		for (int i = this.client.world.getBottomY(); i <= this.client.world.getTopYInclusive() + 1; i += 16) {
			GizmoDrawing.line(new Vec3d(d, i, e), new Vec3d(d, i, e + 16.0), LIGHT_RED, 4.0F);
			GizmoDrawing.line(new Vec3d(d, i, e + 16.0), new Vec3d(d + 16.0, i, e + 16.0), LIGHT_RED, 4.0F);
			GizmoDrawing.line(new Vec3d(d + 16.0, i, e + 16.0), new Vec3d(d + 16.0, i, e), LIGHT_RED, 4.0F);
			GizmoDrawing.line(new Vec3d(d + 16.0, i, e), new Vec3d(d, i, e), LIGHT_RED, 4.0F);
		}
	}
}
