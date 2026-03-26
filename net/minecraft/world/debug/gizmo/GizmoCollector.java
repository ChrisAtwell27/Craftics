package net.minecraft.world.debug.gizmo;

public interface GizmoCollector {
	VisibilityConfigurable NOOP_CONFIGURABLE = new VisibilityConfigurable() {
		@Override
		public VisibilityConfigurable ignoreOcclusion() {
			return this;
		}

		@Override
		public VisibilityConfigurable withLifespan(int lifespan) {
			return this;
		}

		@Override
		public VisibilityConfigurable fadeOut() {
			return this;
		}
	};
	GizmoCollector EMPTY = gizmo -> NOOP_CONFIGURABLE;

	VisibilityConfigurable collect(Gizmo gizmo);
}
