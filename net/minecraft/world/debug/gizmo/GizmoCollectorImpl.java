package net.minecraft.world.debug.gizmo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;

public class GizmoCollectorImpl implements GizmoCollector {
	private final List<GizmoCollectorImpl.Entry> gizmos = new ArrayList();
	private final List<GizmoCollectorImpl.Entry> pendingGizmos = new ArrayList();

	@Override
	public VisibilityConfigurable collect(Gizmo gizmo) {
		GizmoCollectorImpl.Entry entry = new GizmoCollectorImpl.Entry(gizmo);
		this.gizmos.add(entry);
		return entry;
	}

	public List<GizmoCollectorImpl.Entry> extractGizmos() {
		ArrayList<GizmoCollectorImpl.Entry> arrayList = new ArrayList(this.gizmos);
		arrayList.addAll(this.pendingGizmos);
		long l = Util.getMeasuringTimeMs();
		this.gizmos.removeIf(entry -> entry.getRemovalTime() < l);
		this.pendingGizmos.clear();
		return arrayList;
	}

	public List<GizmoCollectorImpl.Entry> getGizmos() {
		return this.gizmos;
	}

	public void add(Collection<GizmoCollectorImpl.Entry> gizmos) {
		this.pendingGizmos.addAll(gizmos);
	}

	public static class Entry implements VisibilityConfigurable {
		private final Gizmo gizmo;
		private boolean ignoreOcclusion;
		private long creationTime;
		private long removalTime;
		private boolean fadeOut;

		Entry(Gizmo gizmo) {
			this.gizmo = gizmo;
		}

		@Override
		public VisibilityConfigurable ignoreOcclusion() {
			this.ignoreOcclusion = true;
			return this;
		}

		@Override
		public VisibilityConfigurable withLifespan(int lifespan) {
			this.creationTime = Util.getMeasuringTimeMs();
			this.removalTime = this.creationTime + lifespan;
			return this;
		}

		@Override
		public VisibilityConfigurable fadeOut() {
			this.fadeOut = true;
			return this;
		}

		public float getOpacity(long time) {
			if (this.fadeOut) {
				long l = this.removalTime - this.creationTime;
				long m = time - this.creationTime;
				return 1.0F - MathHelper.clamp((float)m / (float)l, 0.0F, 1.0F);
			} else {
				return 1.0F;
			}
		}

		public boolean ignoresOcclusion() {
			return this.ignoreOcclusion;
		}

		public long getRemovalTime() {
			return this.removalTime;
		}

		public Gizmo getGizmo() {
			return this.gizmo;
		}
	}
}
