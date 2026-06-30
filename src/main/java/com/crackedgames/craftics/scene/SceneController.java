package com.crackedgames.craftics.scene;

import com.crackedgames.craftics.CrafticsMod;
import com.crackedgames.craftics.combat.EntityWalker;
import com.crackedgames.craftics.network.EnterEventCinematicPayload;
import com.crackedgames.craftics.network.ExitEventCinematicPayload;
import com.crackedgames.craftics.network.SceneStatePayload;
import com.crackedgames.craftics.world.CrafticsSavedData;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.PiglinEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Drives a per-island walk-around merchant scene (Stage 1: enter, third-person click-to-walk
 * past standing NPCs, leave). One instance per island (world-owner UUID). Fully decoupled from
 * CombatManager: it owns its own session state and returns players to the hub on leave.
 */
public final class SceneController {
    private static final Map<UUID, SceneController> INSTANCES = new HashMap<>();

    private final UUID islandOwner;
    private final ServerWorld world;
    private final BlockPos origin;
    private final String sceneName;
    private SceneLayout layout;
    private final List<UUID> members = new ArrayList<>();
    private final List<Integer> npcEntityIds = new ArrayList<>();
    private final Map<BlockPos, BlockState> snapshot = new LinkedHashMap<>();
    private final List<ChunkPos> forcedChunks = new ArrayList<>();
    // One active walker per player while they are click-walking.
    private final Map<UUID, EntityWalker> walkers = new HashMap<>();

    private SceneController(UUID islandOwner, ServerWorld world, BlockPos origin, String sceneName) {
        this.islandOwner = islandOwner;
        this.world = world;
        this.origin = origin;
        this.sceneName = sceneName;
    }

    // ---- static dispatch (called from ModNetworking receivers + the tick hook) ----

    public static void handleEnter(ServerPlayerEntity player, String sceneName) {
        if (!"village".equals(sceneName) && !"barter_station".equals(sceneName)) {
            CrafticsMod.LOGGER.warn("Unknown scene '{}' requested by {}", sceneName,
                player.getName().getString());
            return;
        }
        // FIX 3: block entering a scene while in active combat
        if (com.crackedgames.craftics.combat.CombatManager.getActiveCombat(player.getUuid()) != null) return;
        ServerWorld world = (ServerWorld) player.getEntityWorld();
        CrafticsSavedData data = CrafticsSavedData.get(world);
        UUID owner = data.getEffectiveWorldOwner(player.getUuid());
        if (INSTANCES.containsKey(owner)) return; // a scene is already active for this island
        BlockPos origin = data.getSceneOrigin(owner);
        if (origin == null) {
            CrafticsMod.LOGGER.warn("No scene origin (no world slot) for {}",
                player.getName().getString());
            return;
        }
        SceneController c = new SceneController(owner, world, origin, sceneName);
        INSTANCES.put(owner, c);
        c.build(player);
    }

    public static void handleClick(ServerPlayerEntity player, int tx, int tz) {
        SceneController c = forPlayer(player.getUuid());
        if (c != null) c.walkTo(player, tx, tz);
    }

    public static void handleLeave(ServerPlayerEntity player) {
        SceneController c = forPlayer(player.getUuid());
        if (c != null) c.leave();
    }

    public static void tickAll() {
        for (SceneController c : new ArrayList<>(INSTANCES.values())) c.tick();
    }

    public static void onDisconnect(UUID playerUuid) {
        SceneController c = forPlayer(playerUuid);
        if (c == null) return;
        c.members.remove(playerUuid);
        c.walkers.remove(playerUuid);
        if (c.members.isEmpty()) c.teardown();
    }

    private static SceneController forPlayer(UUID playerUuid) {
        for (SceneController c : INSTANCES.values()) {
            if (c.members.contains(playerUuid)) return c;
        }
        return null;
    }

    // ---- lifecycle ----

    private void build(ServerPlayerEntity leader) {
        this.layout = CodeSceneBuilder.buildLayout(
            origin.getX(), origin.getY(), origin.getZ(), sceneName);
        CodeSceneBuilder.place(world, origin, layout, snapshot);
        forceLoad();
        spawnNpcs();
        // Bring the whole online party into the scene.
        CrafticsSavedData data = CrafticsSavedData.get(world);
        for (UUID memberUuid : data.getPartyMemberUuids(leader.getUuid())) {
            ServerPlayerEntity m = world.getServer().getPlayerManager().getPlayer(memberUuid);
            if (m == null) continue;
            members.add(memberUuid);
            ServerPlayNetworking.send(m, new EnterEventCinematicPayload());
            // Carry the scene floor footprint so the client can seed TileRaycast's grid
            // bounds (a scene never calls enterCombat). oy is the floor-BLOCK Y (origin Y
            // minus 1): the floor slab sits at origin.getY()-1 and its top surface - the
            // plane TileRaycast intersects at arenaOriginY+1, where the player's feet stand -
            // is at origin.getY(). So arenaOriginY must be origin.getY()-1.
            ServerPlayNetworking.send(m, new SceneStatePayload(true,
                origin.getX(), origin.getY() - 1, origin.getZ(),
                CodeSceneBuilder.FLOOR_WIDTH, CodeSceneBuilder.FLOOR_DEPTH));
            m.requestTeleport(layout.spawnX() + 0.5, layout.spawnY(), layout.spawnZ() + 0.5);
            m.setYaw(layout.spawnYaw());
            m.setHeadYaw(layout.spawnYaw());
        }
    }

    private void forceLoad() {
        int margin = 16;
        int minCX = (origin.getX() - margin) >> 4, maxCX = (origin.getX() + 32 + margin) >> 4;
        int minCZ = (origin.getZ() - margin) >> 4, maxCZ = (origin.getZ() + 16 + margin) >> 4;
        for (int cx = minCX; cx <= maxCX; cx++) {
            for (int cz = minCZ; cz <= maxCZ; cz++) {
                world.setChunkForced(cx, cz, true);
                forcedChunks.add(new ChunkPos(cx, cz));
            }
        }
    }

    private void spawnNpcs() {
        for (StandSlot s : layout.stands()) {
            Entity npc = "barter_station".equals(sceneName)
                ? EntityType.PIGLIN.spawn(world, new BlockPos(s.npcX(), s.npcY(), s.npcZ()), SpawnReason.EVENT)
                : EntityType.VILLAGER.spawn(world, new BlockPos(s.npcX(), s.npcY(), s.npcZ()), SpawnReason.EVENT);
            if (npc == null) {
                CrafticsMod.LOGGER.warn("Scene booth NPC failed to spawn at {},{}", s.npcX(), s.npcZ());
                continue;
            }
            npc.refreshPositionAndAngles(s.npcX() + 0.5, s.npcY(), s.npcZ() + 0.5, s.npcYaw(), 0f);
            if (npc instanceof net.minecraft.entity.mob.MobEntity mob) {
                mob.setAiDisabled(true);
                mob.setInvulnerable(true);
                mob.setPersistent();
                mob.setNoGravity(true);
            }
            if (npc instanceof PiglinEntity piglin) piglin.setImmuneToZombification(true);
            npcEntityIds.add(npc.getId());
        }
    }

    private void walkTo(ServerPlayerEntity player, int tx, int tz) {
        if (walkers.containsKey(player.getUuid())) return; // already walking
        // FIX 1: validate tile is inside the scene floor footprint (local coords from TileRaycast).
        if (tx < 0 || tx >= CodeSceneBuilder.FLOOR_WIDTH || tz < 0 || tz >= CodeSceneBuilder.FLOOR_DEPTH) return;
        // Convert local scene tile → world coords by adding the scene origin.
        double sx = player.getX(), sy = player.getY(), sz = player.getZ();
        double ex = origin.getX() + tx + 0.5, ez = origin.getZ() + tz + 0.5;
        double dist = Math.hypot(ex - sx, ez - sz);
        int ticks = Math.max(1, (int) Math.round(dist / 0.25)); // 0.25 blocks/tick, matches combat
        final ServerPlayerEntity fp = player;
        EntityWalker.Mover mover = (x, y, z, yaw) -> {
            fp.setYaw(yaw); fp.setHeadYaw(yaw); fp.setBodyYaw(yaw); fp.setOnGround(true);
            //? if <=1.21.4 {
            fp.prevX = fp.getX();
            fp.prevY = fp.getY();
            fp.prevZ = fp.getZ();
            //?} else {
            /*fp.lastX = fp.getX();
            fp.lastY = fp.getY();
            fp.lastZ = fp.getZ();
            *///?}
            double dx = x - fp.getX(), dz = z - fp.getZ();
            double len = Math.sqrt(dx * dx + dz * dz);
            if (len > 0) { fp.setVelocity(dx / len * 0.12, 0, dz / len * 0.12); fp.velocityDirty = true; }
            fp.setPosition(x, y, z);
            fp.networkHandler.requestTeleport(x, y, z, yaw, fp.getPitch());
        };
        EntityWalker walker = new EntityWalker(mover, sx, sy, sz, ex, layout.spawnY(), ez, ticks,
            () -> walkers.remove(fp.getUuid()));
        walkers.put(player.getUuid(), walker);
    }

    private void tick() {
        for (EntityWalker w : new ArrayList<>(walkers.values())) {
            w.tick();
        }
    }

    private void leave() {
        CrafticsSavedData data = CrafticsSavedData.get(world);
        for (UUID memberUuid : new ArrayList<>(members)) {
            ServerPlayerEntity m = world.getServer().getPlayerManager().getPlayer(memberUuid);
            if (m == null) continue;
            ServerPlayNetworking.send(m, new ExitEventCinematicPayload());
            ServerPlayNetworking.send(m, new SceneStatePayload(false, 0, 0, 0, 0, 0));
            BlockPos hub = data.getHubTeleportPos(memberUuid);
            if (hub != null) m.requestTeleport(hub.getX() + 0.5, hub.getY(), hub.getZ() + 0.5);
        }
        teardown();
    }

    private void teardown() {
        // Discard NPC entities.
        for (int id : npcEntityIds) {
            Entity e = world.getEntityById(id);
            if (e != null) e.discard();
        }
        npcEntityIds.clear();
        // Restore overwritten blocks (reverse insertion order is unnecessary; states are independent).
        for (Map.Entry<BlockPos, BlockState> e : snapshot.entrySet()) {
            world.setBlockState(e.getKey(), e.getValue(), net.minecraft.block.Block.FORCE_STATE);
        }
        snapshot.clear();
        // Release forced chunks.
        for (ChunkPos cp : forcedChunks) world.setChunkForced(cp.x, cp.z, false);
        forcedChunks.clear();
        walkers.clear();
        members.clear();
        INSTANCES.remove(islandOwner);
    }
}
