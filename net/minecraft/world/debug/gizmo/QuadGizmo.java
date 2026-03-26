package net.minecraft.world.debug.gizmo;

import net.minecraft.client.render.DrawStyle;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public record QuadGizmo(Vec3d a, Vec3d b, Vec3d c, Vec3d d, DrawStyle style) implements Gizmo {
	public static QuadGizmo ofFace(Vec3d nwd, Vec3d seu, Direction direction, DrawStyle style) {
		return switch (direction) {
			case DOWN -> new QuadGizmo(
				new Vec3d(nwd.x, nwd.y, nwd.z), new Vec3d(seu.x, nwd.y, nwd.z), new Vec3d(seu.x, nwd.y, seu.z), new Vec3d(nwd.x, nwd.y, seu.z), style
			);
			case UP -> new QuadGizmo(
				new Vec3d(nwd.x, seu.y, nwd.z), new Vec3d(nwd.x, seu.y, seu.z), new Vec3d(seu.x, seu.y, seu.z), new Vec3d(seu.x, seu.y, nwd.z), style
			);
			case NORTH -> new QuadGizmo(
				new Vec3d(nwd.x, nwd.y, nwd.z), new Vec3d(nwd.x, seu.y, nwd.z), new Vec3d(seu.x, seu.y, nwd.z), new Vec3d(seu.x, nwd.y, nwd.z), style
			);
			case SOUTH -> new QuadGizmo(
				new Vec3d(nwd.x, nwd.y, seu.z), new Vec3d(seu.x, nwd.y, seu.z), new Vec3d(seu.x, seu.y, seu.z), new Vec3d(nwd.x, seu.y, seu.z), style
			);
			case WEST -> new QuadGizmo(
				new Vec3d(nwd.x, nwd.y, nwd.z), new Vec3d(nwd.x, nwd.y, seu.z), new Vec3d(nwd.x, seu.y, seu.z), new Vec3d(nwd.x, seu.y, nwd.z), style
			);
			case EAST -> new QuadGizmo(
				new Vec3d(seu.x, nwd.y, nwd.z), new Vec3d(seu.x, seu.y, nwd.z), new Vec3d(seu.x, seu.y, seu.z), new Vec3d(seu.x, nwd.y, seu.z), style
			);
		};
	}

	@Override
	public void draw(GizmoDrawer consumer, float opacity) {
		if (this.style.hasFill()) {
			int i = this.style.fill(opacity);
			consumer.addQuad(this.a, this.b, this.c, this.d, i);
		}

		if (this.style.hasStroke()) {
			int i = this.style.stroke(opacity);
			consumer.addLine(this.a, this.b, i, this.style.strokeWidth());
			consumer.addLine(this.b, this.c, i, this.style.strokeWidth());
			consumer.addLine(this.c, this.d, i, this.style.strokeWidth());
			consumer.addLine(this.d, this.a, i, this.style.strokeWidth());
		}
	}
}
