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
                arenaByEntity.remove(id);
                lastBlockPos.remove(id);
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
                arenaByEntity.remove(id);
                lastBlockPos.remove(id);
                it.remove();
            } else if (landed) {
                // Rare: entity has settled but vanilla hasn't placed yet (deferred until
                // its next tick). Mark obstacle from the current resting position.
                com.crackedgames.craftics.core.GridArena landArena = arenaByEntity.remove(id);
                if (landArena != null) {
                    markObstacleAt(landArena, fbe.getBlockPos());
                }
                lastBlockPos.remove(id);
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
        if (last == null || landArena == null) return;
        // Vanilla places the block at the position above the floor (origin.y + 1).
        // Use the arena's known floor Y to absorb 1-tick vertical movement between
        // our last recording and the actual placement.
        BlockPos placement = new BlockPos(last.getX(), landArena.getOrigin().getY() + 1, last.getZ());
        if (world.getBlockState(placement).isAir()) return;
        markObstacleAt(landArena, placement);
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

    public void clearAll(ServerWorld world) {
        for (UUID id : tracked.keySet()) {
            Entity e = world.getEntity(id);
            if (e != null) e.discard();
        }
        tracked.clear();
        arenaByEntity.clear();
        lastBlockPos.clear();
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
