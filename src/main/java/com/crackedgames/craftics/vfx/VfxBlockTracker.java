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
        if (arena != null) arenaByEntity.put(fbe.getUuid(), arena);
    }

    public void tick(ServerWorld world) {
        if (tracked.isEmpty()) return;
        long now = world.getTime();
        Iterator<Map.Entry<UUID, Long>> it = tracked.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Long> entry = it.next();
            Entity e = world.getEntity(entry.getKey());
            if (!(e instanceof FallingBlockEntity fbe) || fbe.isRemoved()) {
                arenaByEntity.remove(entry.getKey());
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
                arenaByEntity.remove(entry.getKey());
                it.remove();
            } else if (landed) {
                // Let vanilla handle the block placement (FallingBlockEntity will place the block
                // on next tick if onGround is true). Mark the tile as VFX-obstacle so combat
                // pathfinding respects it.
                com.crackedgames.craftics.core.GridArena landArena = arenaByEntity.get(entry.getKey());
                if (landArena != null) {
                    BlockPos landedBlock = fbe.getBlockPos();
                    // Compute grid position inline from arena origin
                    net.minecraft.util.math.BlockPos arenaOrigin = landArena.getOrigin();
                    int gx = landedBlock.getX() - arenaOrigin.getX();
                    int gz = landedBlock.getZ() - arenaOrigin.getZ();
                    com.crackedgames.craftics.core.GridPos gp =
                        new com.crackedgames.craftics.core.GridPos(gx, gz);
                    if (landArena.isInBounds(gp)) {
                        landArena.markVfxObstacle(gp);
                    }
                }
                // Remove from tracking — vanilla continues the entity's placement logic
                arenaByEntity.remove(entry.getKey());
                it.remove();
            }
        }
    }

    public void clearAll(ServerWorld world) {
        for (UUID id : tracked.keySet()) {
            Entity e = world.getEntity(id);
            if (e != null) e.discard();
        }
        tracked.clear();
        arenaByEntity.clear();
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
