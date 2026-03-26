package net.minecraft.world.debug.gizmo;

import net.minecraft.client.render.DrawStyle;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.jspecify.annotations.Nullable;

public class GizmoDrawing {
	static final ThreadLocal<GizmoCollector> CURRENT_GIZMO_COLLECTOR = new ThreadLocal();

	private GizmoDrawing() {
	}

	public static GizmoDrawing.CollectorScope using(GizmoCollector gizmoCollector) {
		GizmoDrawing.CollectorScope collectorScope = new GizmoDrawing.CollectorScope();
		CURRENT_GIZMO_COLLECTOR.set(gizmoCollector);
		return collectorScope;
	}

	public static VisibilityConfigurable collect(Gizmo gizmo) {
		GizmoCollector gizmoCollector = (GizmoCollector)CURRENT_GIZMO_COLLECTOR.get();
		if (gizmoCollector == null) {
			throw new IllegalStateException("Gizmos cannot be created here! No GizmoCollector has been registered.");
		} else {
			return gizmoCollector.collect(gizmo);
		}
	}

	public static VisibilityConfigurable box(Box box, DrawStyle style) {
		return box(box, style, false);
	}

	public static VisibilityConfigurable box(Box box, DrawStyle style, boolean coloredCornerStroke) {
		return collect(new BoxGizmo(box, style, coloredCornerStroke));
	}

	public static VisibilityConfigurable box(BlockPos blockPos, DrawStyle style) {
		return box(new Box(blockPos), style);
	}

	public static VisibilityConfigurable box(BlockPos blockPos, float expansion, DrawStyle style) {
		return box(new Box(blockPos).expand(expansion), style);
	}

	public static VisibilityConfigurable circle(Vec3d pos, float radius, DrawStyle style) {
		return collect(new CircleGizmo(pos, radius, style));
	}

	public static VisibilityConfigurable line(Vec3d start, Vec3d end, int color) {
		return collect(new LineGizmo(start, end, color, 3.0F));
	}

	public static VisibilityConfigurable line(Vec3d start, Vec3d end, int color, float width) {
		return collect(new LineGizmo(start, end, color, width));
	}

	public static VisibilityConfigurable arrow(Vec3d start, Vec3d end, int color) {
		return collect(new ArrowGizmo(start, end, color, 2.5F));
	}

	public static VisibilityConfigurable arrow(Vec3d start, Vec3d end, int color, float width) {
		return collect(new ArrowGizmo(start, end, color, width));
	}

	public static VisibilityConfigurable face(Vec3d nwd, Vec3d seu, Direction direction, DrawStyle style) {
		return collect(QuadGizmo.ofFace(nwd, seu, direction, style));
	}

	public static VisibilityConfigurable quad(Vec3d a, Vec3d b, Vec3d c, Vec3d d, DrawStyle style) {
		return collect(new QuadGizmo(a, b, c, d, style));
	}

	public static VisibilityConfigurable point(Vec3d pos, int color, float size) {
		return collect(new PointGizmo(pos, color, size));
	}

	public static VisibilityConfigurable blockLabel(String text, BlockPos blockPos, int yOffset, int color, float scale) {
		double d = 1.3;
		double e = 0.2;
		VisibilityConfigurable visibilityConfigurable = text(text, Vec3d.add(blockPos, 0.5, 1.3 + yOffset * 0.2, 0.5), TextGizmo.Style.left(color).scaled(scale));
		visibilityConfigurable.ignoreOcclusion();
		return visibilityConfigurable;
	}

	public static VisibilityConfigurable entityLabel(Entity entity, int yOffset, String text, int color, float scale) {
		double d = 2.4;
		double e = 0.25;
		double f = entity.getBlockX() + 0.5;
		double g = entity.getY() + 2.4 + yOffset * 0.25;
		double h = entity.getBlockZ() + 0.5;
		float i = 0.5F;
		VisibilityConfigurable visibilityConfigurable = text(text, new Vec3d(f, g, h), TextGizmo.Style.centered(color).scaled(scale).adjusted(0.5F));
		visibilityConfigurable.ignoreOcclusion();
		return visibilityConfigurable;
	}

	public static VisibilityConfigurable text(String text, Vec3d pos, TextGizmo.Style style) {
		return collect(new TextGizmo(pos, text, style));
	}

	public static class CollectorScope implements AutoCloseable {
		@Nullable
		private final GizmoCollector prevCollector = (GizmoCollector)GizmoDrawing.CURRENT_GIZMO_COLLECTOR.get();
		private boolean closed;

		CollectorScope() {
		}

		public void close() {
			if (!this.closed) {
				this.closed = true;
				GizmoDrawing.CURRENT_GIZMO_COLLECTOR.set(this.prevCollector);
			}
		}
	}
}
