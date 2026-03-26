package net.minecraft.client.render.debug;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.SharedConstants;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.debug.DebugHudEntries;
import net.minecraft.client.render.Frustum;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.predicate.entity.EntityPredicates;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.LightType;
import net.minecraft.world.debug.DebugDataStore;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class DebugRenderer {
	private final List<DebugRenderer.Renderer> renderers = new ArrayList();
	private long currentVersion;

	public DebugRenderer() {
		this.initRenderers();
	}

	public void initRenderers() {
		MinecraftClient minecraftClient = MinecraftClient.getInstance();
		this.renderers.clear();
		if (minecraftClient.debugHudEntryList.isEntryVisible(DebugHudEntries.CHUNK_BORDERS)) {
			this.renderers.add(new ChunkBorderDebugRenderer(minecraftClient));
		}

		if (minecraftClient.debugHudEntryList.isEntryVisible(DebugHudEntries.CHUNK_SECTION_OCTREE)) {
			this.renderers.add(new OctreeDebugRenderer(minecraftClient));
		}

		if (SharedConstants.PATHFINDING) {
			this.renderers.add(new PathfindingDebugRenderer());
		}

		if (minecraftClient.debugHudEntryList.isEntryVisible(DebugHudEntries.VISUALIZE_WATER_LEVELS)) {
			this.renderers.add(new WaterDebugRenderer(minecraftClient));
		}

		if (minecraftClient.debugHudEntryList.isEntryVisible(DebugHudEntries.VISUALIZE_HEIGHTMAP)) {
			this.renderers.add(new HeightmapDebugRenderer(minecraftClient));
		}

		if (minecraftClient.debugHudEntryList.isEntryVisible(DebugHudEntries.VISUALIZE_COLLISION_BOXES)) {
			this.renderers.add(new CollisionDebugRenderer(minecraftClient));
		}

		if (minecraftClient.debugHudEntryList.isEntryVisible(DebugHudEntries.VISUALIZE_ENTITY_SUPPORTING_BLOCKS)) {
			this.renderers.add(new SupportingBlockDebugRenderer(minecraftClient));
		}

		if (SharedConstants.NEIGHBORSUPDATE) {
			this.renderers.add(new NeighborUpdateDebugRenderer());
		}

		if (SharedConstants.EXPERIMENTAL_REDSTONEWIRE_UPDATE_ORDER) {
			this.renderers.add(new RedstoneUpdateOrderDebugRenderer());
		}

		if (SharedConstants.STRUCTURES) {
			this.renderers.add(new StructureDebugRenderer());
		}

		if (minecraftClient.debugHudEntryList.isEntryVisible(DebugHudEntries.VISUALIZE_BLOCK_LIGHT_LEVELS)
			|| minecraftClient.debugHudEntryList.isEntryVisible(DebugHudEntries.VISUALIZE_SKY_LIGHT_LEVELS)) {
			this.renderers
				.add(
					new SkyLightDebugRenderer(
						minecraftClient,
						minecraftClient.debugHudEntryList.isEntryVisible(DebugHudEntries.VISUALIZE_BLOCK_LIGHT_LEVELS),
						minecraftClient.debugHudEntryList.isEntryVisible(DebugHudEntries.VISUALIZE_SKY_LIGHT_LEVELS)
					)
				);
		}

		if (minecraftClient.debugHudEntryList.isEntryVisible(DebugHudEntries.VISUALIZE_SOLID_FACES)) {
			this.renderers.add(new BlockOutlineDebugRenderer(minecraftClient));
		}

		if (SharedConstants.VILLAGE_SECTIONS) {
			this.renderers.add(new VillageSectionsDebugRenderer());
		}

		if (SharedConstants.BRAIN) {
			this.renderers.add(new BrainDebugRenderer(minecraftClient));
		}

		if (SharedConstants.POI) {
			this.renderers.add(new PoiDebugRenderer(new BrainDebugRenderer(minecraftClient)));
		}

		if (SharedConstants.BEES) {
			this.renderers.add(new BeeDebugRenderer(minecraftClient));
		}

		if (SharedConstants.RAIDS) {
			this.renderers.add(new RaidCenterDebugRenderer(minecraftClient));
		}

		if (SharedConstants.GOAL_SELECTOR) {
			this.renderers.add(new GoalSelectorDebugRenderer(minecraftClient));
		}

		if (minecraftClient.debugHudEntryList.isEntryVisible(DebugHudEntries.VISUALIZE_CHUNKS_ON_SERVER)) {
			this.renderers.add(new ChunkLoadingDebugRenderer(minecraftClient));
		}

		if (SharedConstants.GAME_EVENT_LISTENERS) {
			this.renderers.add(new GameEventDebugRenderer());
		}

		if (minecraftClient.debugHudEntryList.isEntryVisible(DebugHudEntries.VISUALIZE_SKY_LIGHT_SECTIONS)) {
			this.renderers.add(new LightDebugRenderer(minecraftClient, LightType.SKY));
		}

		if (SharedConstants.BREEZE_MOB) {
			this.renderers.add(new BreezeDebugRenderer(minecraftClient));
		}

		if (SharedConstants.ENTITY_BLOCK_INTERSECTION) {
			this.renderers.add(new EntityBlockIntersectionsDebugRenderer());
		}

		if (minecraftClient.debugHudEntryList.isEntryVisible(DebugHudEntries.ENTITY_HITBOXES)) {
			this.renderers.add(new EntityHitboxDebugRenderer(minecraftClient));
		}

		this.renderers.add(new ChunkDebugRenderer(minecraftClient));
	}

	public void render(Frustum frustum, double cameraX, double cameraY, double cameraZ, float tickProgress) {
		MinecraftClient minecraftClient = MinecraftClient.getInstance();
		DebugDataStore debugDataStore = minecraftClient.getNetworkHandler().getDebugDataStore();
		if (minecraftClient.debugHudEntryList.getVersion() != this.currentVersion) {
			this.currentVersion = minecraftClient.debugHudEntryList.getVersion();
			this.initRenderers();
		}

		for (DebugRenderer.Renderer renderer : this.renderers) {
			renderer.render(cameraX, cameraY, cameraZ, debugDataStore, frustum, tickProgress);
		}
	}

	public static Optional<Entity> getTargetedEntity(@Nullable Entity entity, int maxDistance) {
		if (entity == null) {
			return Optional.empty();
		} else {
			Vec3d vec3d = entity.getEyePos();
			Vec3d vec3d2 = entity.getRotationVec(1.0F).multiply(maxDistance);
			Vec3d vec3d3 = vec3d.add(vec3d2);
			Box box = entity.getBoundingBox().stretch(vec3d2).expand(1.0);
			int i = maxDistance * maxDistance;
			EntityHitResult entityHitResult = ProjectileUtil.raycast(entity, vec3d, vec3d3, box, EntityPredicates.CAN_HIT, i);
			if (entityHitResult == null) {
				return Optional.empty();
			} else {
				return vec3d.squaredDistanceTo(entityHitResult.getPos()) > i ? Optional.empty() : Optional.of(entityHitResult.getEntity());
			}
		}
	}

	private static Vec3d hueToRgb(float hue) {
		float f = 5.99999F;
		int i = (int)(MathHelper.clamp(hue, 0.0F, 1.0F) * 5.99999F);
		float g = hue * 5.99999F - i;

		return switch (i) {
			case 0 -> new Vec3d(1.0, g, 0.0);
			case 1 -> new Vec3d(1.0F - g, 1.0, 0.0);
			case 2 -> new Vec3d(0.0, 1.0, g);
			case 3 -> new Vec3d(0.0, 1.0 - g, 1.0);
			case 4 -> new Vec3d(g, 0.0, 1.0);
			case 5 -> new Vec3d(1.0, 0.0, 1.0 - g);
			default -> throw new IllegalStateException("Unexpected value: " + i);
		};
	}

	private static Vec3d shiftHue(float r, float g, float b, float dHue) {
		Vec3d vec3d = hueToRgb(dHue).multiply(r);
		Vec3d vec3d2 = hueToRgb((dHue + 0.33333334F) % 1.0F).multiply(g);
		Vec3d vec3d3 = hueToRgb((dHue + 0.6666667F) % 1.0F).multiply(b);
		Vec3d vec3d4 = vec3d.add(vec3d2).add(vec3d3);
		double d = Math.max(Math.max(1.0, vec3d4.x), Math.max(vec3d4.y, vec3d4.z));
		return new Vec3d(vec3d4.x / d, vec3d4.y / d, vec3d4.z / d);
	}

	@Environment(EnvType.CLIENT)
	public interface Renderer {
		void render(double cameraX, double cameraY, double cameraZ, DebugDataStore store, Frustum frustum, float tickProgress);
	}
}
