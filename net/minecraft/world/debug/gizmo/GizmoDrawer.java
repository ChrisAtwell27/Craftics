package net.minecraft.world.debug.gizmo;

import net.minecraft.util.math.Vec3d;

public interface GizmoDrawer {
	void addPoint(Vec3d pos, int color, float size);

	void addLine(Vec3d start, Vec3d end, int color, float width);

	void addPolygon(Vec3d[] vertices, int color);

	void addQuad(Vec3d a, Vec3d b, Vec3d c, Vec3d d, int color);

	void addText(Vec3d pos, String text, TextGizmo.Style style);
}
