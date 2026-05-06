package com.crackedgames.craftics.vfx;

import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Manages {@link FallingBlockEntity} instances spawned by {@link VfxPrimitive.LaunchBlock}
 * and {@link VfxPrimitive.LaunchFloorBlock}.
 *
 * Guarantees:
 *   • Entities are constructed manually (not via spawnFromBlock) to avoid 1-tick render lag.
 *   • Entities past {@code lifetimeTicks} are discarded mid-air with a poof.
 *   • Entities that land are tracked as VFX obstacles on the arena grid.
 *   • {@link #clearAll(ServerWorld)} (called from CombatManager.endCombat) purges every tracked entity.
 */
public final class VfxBlockTracker {

    private static final Map<ServerWorld, VfxBlockTracker> INSTANCES = new WeakHashMap<>();

    public static VfxBlockTracker of(ServerWorld w) {
        return INSTANCES.computeIfAbsent(w, k -> new VfxBlockTracker());
    }

    /** entity UUID → tick when it should be discarded. */
    private final Map<UUID, Long> tracked = new HashMap<>();

    /** entity UUID → arena that spawned it (for obstacle marking on land). */
    private final Map<UUID, com.crackedgames.craftics.core.GridArena> arenaByEntity = new HashMap<>();

    /** entity UUID → last observed block position. Vanilla FallingBlockEntity places
     *  its block AND discards itself in the same tick that {@code onGround} becomes
     *  true — and that entity tick runs before our END_SERVER_TICK pass. So when we
     *  see the entity is gone, we use the position recorded on the prior tick to
     *  locate the placed block and mark its grid tile as a VFX obstacle. */
    private final Map<UUID, BlockPos> lastBlockPos = new HashMap<>();

    /** entity UUID → block state it carries. Used when scanning for the placed
     *  block: the entity moves up to ~1 block in the final pre-landing tick, so
     *  vanilla can place at any tile within ±1 of {@link #lastBlockPos}. We only
     *  treat a tile as ours if its world block matches this exact state. */
    private final Map<UUID, BlockState> launchedState = new HashMap<>();

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /** Legacy overload — no arena obstacle tracking. */
    public void launchInto(ServerWorld world, Vec3d origin, Vec3d velocity,
                            BlockState state, int lifetimeTicks) {
        launchInto(world, origin, velocity, state, lifetimeTicks, null);
    }

    public void launchInto(ServerWorld world, Vec3d origin, Vec3d velocity,
                            BlockState state, int lifetimeTicks,
                            @Nullable com.crackedgames.craftics.core.GridArena arena) {
        if (!com.crackedgames.craftics.CrafticsMod.CONFIG.vfxBlockEntitiesEnabled()) {
            spawnFallbackDebris(world, origin, state);
            return;
        }

        // Manual construction avoids FallingBlockEntity.spawnFromBlock, which:
        //   (a) writes BlockState into the world at spawnPos as a side effect
        //   (b) spawns the entity before position/velocity are applied → 1-tick render lag
        //? if <=1.21.1 {
        FallingBlockEntity fbe = net.minecraft.entity.EntityType.FALLING_BLOCK.create(world);
        //?} else {
        /*FallingBlockEntity fbe = net.minecraft.entity.EntityType.FALLING_BLOCK.create(world, net.minecraft.entity.SpawnReason.TRIGGERED);
        *///?}
        if (fbe == null) {
            spawnFallbackDebris(world, origin, state);
            return;
        }

        // Inject block state via NBT round-trip (setBlockState is private)
        net.minecraft.nbt.NbtCompound nbt = new net.minecraft.nbt.NbtCompound();
        fbe.writeNbt(nbt);
        nbt.put("BlockState", net.minecraft.nbt.NbtHelper.fromBlockState(state));
        nbt.putInt("Time", 1);              // skip the "just spawned" lag tick
        nbt.putBoolean("DropItem", false);
        fbe.readNbt(nbt);

        fbe.setPosition(origin.x, origin.y, origin.z);
        fbe.setVelocity(velocity);
        fbe.timeFalling = 1;

        world.spawnEntity(fbe);
        tracked.put(fbe.getUuid(), world.getTime() + lifetimeTicks);
        if (arena != null) {
            arenaByEntity.put(fbe.getUuid(), arena);
            lastBlockPos.put(fbe.getUuid(), fbe.getBlockPos());
            launchedState.put(fbe.getUuid(), state);
        }
    }

    public void tick(ServerWorld world) {
        if (tracked.isEmpty()) return;
        long now = world.getTime();
        Iterator<Map.Entry<UUID, Long>> it = tracked.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Long> entry = it.next();
            UUID id = entry.getKey();
            Entity e = world.getEntity(id);
            if (!(e instanceof FallingBlockEntity fbe) || fbe.isRemoved()) {
                // Vanilla discards the entity in the same tick that it places the block,
                // and that entity tick runs before this END_SERVER_TICK pass. Mark the
                // obstacle from the position we recorded on the prior tick.
                markObstacleFromLastPos(world, id);
                forgetEntity(id);
                it.remove();
                continue;
            }
            boolean expired = now >= entry.getValue();
            boolean landed = fbe.isOnGround();
            if (expired && !landed) {
                // Lifetime expired mid-air — discard with poof, no obstacle conversion
                BlockState state = fbe.getBlockState();
                Vec3d pos = fbe.getPos();
                spawnPoof(world, pos, state);
                fbe.discard();
                forgetEntity(id);
                it.remove();
            } else if (landed) {
                // Rare: entity has settled but vanilla hasn't placed yet (deferred until
                // its next tick). Mark obstacle from the current resting position.
                com.crackedgames.craftics.core.GridArena landArena = arenaByEntity.get(id);
                if (landArena != null) {
                    markObstacleAt(landArena, fbe.getBlockPos());
                }
                forgetEntity(id);
                it.remove();
            } else {
                // Still in flight — remember where we last saw it so we can mark the
                // obstacle on the next tick if vanilla places + discards atomically.
                lastBlockPos.put(id, fbe.getBlockPos());
            }
        }
    }

    private void markObstacleFromLastPos(ServerWorld world, UUID id) {
        BlockPos last = lastBlockPos.get(id);
        com.crackedgames.craftics.core.GridArena landArena = arenaByEntity.get(id);
        BlockState expected = launchedState.get(id);
        if (last == null || landArena == null || expected == null) return;

        // Vanilla places at floor(entity.x), origin.y+1, floor(entity.z) at the moment
        // it lands. Our last record is from the end of the prior tick, so the entity
        // can have crossed an X/Z boundary in its final fall step. Scan ±1 around the
        // last position; only mark a tile if the world block there matches the state
        // we launched (so we don't accidentally claim a pre-existing wall or another
        // boss-placed obstacle).
        int placementY = landArena.getOrigin().getY() + 1;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos candidate = new BlockPos(last.getX() + dx, placementY, last.getZ() + dz);
                if (!world.getBlockState(candidate).equals(expected)) continue;
                markObstacleAt(landArena, candidate);
            }
        }
    }

    private void markObstacleAt(com.crackedgames.craftics.core.GridArena landArena, BlockPos blockPos) {
        BlockPos arenaOrigin = landArena.getOrigin();
        int gx = blockPos.getX() - arenaOrigin.getX();
        int gz = blockPos.getZ() - arenaOrigin.getZ();
        com.crackedgames.craftics.core.GridPos gp =
            new com.crackedgames.craftics.core.GridPos(gx, gz);
        if (landArena.isInBounds(gp)) {
            landArena.markVfxObstacle(gp);
        }
    }

    private void forgetEntity(UUID id) {
        arenaByEntity.remove(id);
        lastBlockPos.remove(id);
        launchedState.remove(id);
    }

    public void clearAll(ServerWorld world) {
        for (UUID id : tracked.keySet()) {
            Entity e = world.getEntity(id);
            if (e != null) e.discard();
        }
        tracked.clear();
        arenaByEntity.clear();
        lastBlockPos.clear();
        launchedState.clear();
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void spawnFallbackDebris(ServerWorld world, Vec3d pos, BlockState state) {
        BlockStateParticleEffect effect = new BlockStateParticleEffect(ParticleTypes.BLOCK, state);
        world.spawnParticles(effect, pos.x, pos.y, pos.z, 8, 0.3, 0.2, 0.3, 0.2);
    }

    private void spawnPoof(ServerWorld world, Vec3d pos, BlockState state) {
        BlockStateParticleEffect effect = new BlockStateParticleEffect(ParticleTypes.BLOCK, state);
        world.spawnParticles(effect, pos.x, pos.y, pos.z, 6, 0.2, 0.2, 0.2, 0.1);
    }
}
