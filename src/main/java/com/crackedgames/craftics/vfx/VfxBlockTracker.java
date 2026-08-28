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
 *   • Every block one of them leaves in the world is remembered and put back at
 *     {@link #clearAll(ServerWorld)}, wherever it landed.
 *   • {@link #clearAll(ServerWorld)} (called from CombatManager.endCombat) purges every tracked entity.
 *
 * <p>The obstacle marking and the block bookkeeping are deliberately separate. Marking is a
 * gameplay statement - "the grid has something on this tile" - and it only applies to a tile
 * the arena owns and still believes is clear. Cleanup cannot be that picky: a launched block
 * comes down on walls, on other debris, a level above the floor, and outside the arena
 * entirely, and every one of those used to become a real block nobody owned. Arenas are
 * rescanned from their blocks on revisit, so anything left behind is baked in permanently.
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
     *  true - and that entity tick runs before our END_SERVER_TICK pass. So when we
     *  see the entity is gone, we use the position recorded on the prior tick to
     *  locate the placed block and mark its grid tile as a VFX obstacle. */
    private final Map<UUID, BlockPos> lastBlockPos = new HashMap<>();

    /** entity UUID → block state it carries. Used when scanning for the placed
     *  block: the entity moves up to ~1 block in the final pre-landing tick, so
     *  vanilla can place at any tile within ±1 of {@link #lastBlockPos}. We only
     *  treat a tile as ours if its world block matches this exact state. */
    private final Map<UUID, BlockState> launchedState = new HashMap<>();

    /** entity UUID → the last few cells it flew through, oldest first, each with the state
     *  that cell held BEFORE the entity got there.
     *
     *  <p>This is what makes a landing identifiable after the fact. Vanilla places the block
     *  and discards the entity inside one entity tick, so by the time this class looks there
     *  is only a block in the world - and a block that matches what we launched is not proof
     *  it is ours, since the arena is full of the same stone. A cell we watched be air one
     *  tick and hold our block the next is proof. */
    private final Map<UUID, java.util.LinkedHashMap<BlockPos, BlockState>> flightPath =
        new HashMap<>();

    /** How many cells of flight to remember. The entity is only ever a tile or two above
     *  where it lands on the tick before it lands; more than this is memory for nothing. */
    private static final int FLIGHT_MEMORY = 4;

    /** Turns a landed block stands on the arena floor before it crumbles.
     *
     *  <p>It is meant to land - a slam that throws the floor up and leaves nothing behind is
     *  a light show - but landing and staying are different things. Held for a few turns so
     *  it is real terrain to path around and mine, then broken like any other temporary
     *  block, cracks and all. Matches the collapse rubble it sits alongside. */
    private static final int LANDED_DEBRIS_TURNS = 3;

    /** What one of our blocks covered up where it came to rest. */
    private record Landing(BlockState placed, BlockState prior) {}

    /** Every block this world's launches have left lying around, and what was there before.
     *  Put back at {@link #clearAll}. Not keyed by arena on purpose: a block that sailed out
     *  of the arena is exactly the one nothing else is going to clean up. */
    private final Map<BlockPos, Landing> landedBlocks = new java.util.LinkedHashMap<>();

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /** Legacy overload - no arena obstacle tracking. */
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
        // Flight is recorded for every launch, arena or no arena. The arena only decides
        // whether a landing becomes an OBSTACLE on the grid; whether it gets cleaned up is
        // not the arena's business, and a launch with no arena attached used to be tracked
        // by nothing at all - so anything it left behind stayed for good.
        lastBlockPos.put(fbe.getUuid(), fbe.getBlockPos());
        launchedState.put(fbe.getUuid(), state);
        notePosition(world, fbe.getUuid(), fbe.getBlockPos());
        if (arena != null) {
            arenaByEntity.put(fbe.getUuid(), arena);
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
                // and that entity tick runs before this END_SERVER_TICK pass. Find the
                // block from the cells we watched it fly through.
                resolveLanding(world, id);
                forgetEntity(id);
                it.remove();
                continue;
            }
            boolean expired = now >= entry.getValue();
            boolean landed = fbe.isOnGround();
            if (expired && !landed) {
                // Lifetime expired mid-air - discard with poof, no obstacle conversion
                BlockState state = fbe.getBlockState();
                Vec3d pos = fbe.getPos();
                spawnPoof(world, pos, state);
                fbe.discard();
                forgetEntity(id);
                it.remove();
            } else if (landed) {
                // Rare: entity has settled but vanilla hasn't placed yet (deferred until
                // its next tick). The world here still holds what was there before, which
                // is the one moment the prior state can be read directly.
                BlockPos resting = fbe.getBlockPos();
                recordLanding(id, resting, world.getBlockState(resting));
                com.crackedgames.craftics.core.GridArena landArena = arenaByEntity.get(id);
                if (landArena != null) {
                    markObstacleAt(landArena, resting);
                }
                forgetEntity(id);
                it.remove();
            } else {
                // Still in flight - remember where we last saw it, and what that cell held
                // before we got there, so the landing can be identified next tick if vanilla
                // places + discards atomically.
                lastBlockPos.put(id, fbe.getBlockPos());
                notePosition(world, id, fbe.getBlockPos());
            }
        }
    }

    /**
     * Work out where a vanished entity put its block, and write both halves of the answer:
     * the world block to put back at the end of the fight, and the grid obstacle if the tile
     * is one the arena will take.
     */
    private void resolveLanding(ServerWorld world, UUID id) {
        BlockState expected = launchedState.get(id);
        if (expected == null) return;
        com.crackedgames.craftics.core.GridArena landArena = arenaByEntity.get(id);

        // The flight path first, newest cell back. A cell that held something else when the
        // entity flew through it and holds our block now IS our block - at any height, on a
        // wall, on top of earlier debris, outside the arena. That is the case the old planar
        // scan could not see and the one that leaked.
        java.util.LinkedHashMap<BlockPos, BlockState> path = flightPath.get(id);
        if (path != null) {
            java.util.List<Map.Entry<BlockPos, BlockState>> cells =
                new java.util.ArrayList<>(path.entrySet());
            for (int i = cells.size() - 1; i >= 0; i--) {
                BlockPos pos = cells.get(i).getKey();
                BlockState prior = cells.get(i).getValue();
                if (prior.equals(expected)) continue;          // it already looked like this
                if (!world.getBlockState(pos).equals(expected)) continue;
                recordLanding(id, pos, prior);
                if (landArena != null) markObstacleAt(landArena, pos);
                return;
            }
        }

        // Nothing in the path matched - the entity can fall more than a block in its last
        // step and skip the cell it lands in. Fall back to the old ±1 sweep at the arena's
        // surface height, which is where most of them come to rest.
        BlockPos last = lastBlockPos.get(id);
        if (last == null || landArena == null) return;
        int placementY = landArena.getOrigin().getY() + 1;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos candidate = new BlockPos(last.getX() + dx, placementY, last.getZ() + dz);
                if (!world.getBlockState(candidate).equals(expected)) continue;
                // Only over a tile the arena still calls empty. Without that this sweep would
                // happily claim a cobblestone wall as a cobblestone block it launched, and
                // delete a piece of the arena when the fight ended.
                if (!isClearTile(landArena, candidate)) continue;
                recordLanding(id, candidate, net.minecraft.block.Blocks.AIR.getDefaultState());
                markObstacleAt(landArena, candidate);
                return;
            }
        }
    }

    /** Whether the arena still believes this cell is clear floor. */
    private boolean isClearTile(com.crackedgames.craftics.core.GridArena arena, BlockPos blockPos) {
        com.crackedgames.craftics.core.GridPos gp = new com.crackedgames.craftics.core.GridPos(
            blockPos.getX() - arena.getOrigin().getX(),
            blockPos.getZ() - arena.getOrigin().getZ());
        if (!arena.isInBounds(gp)) return false;
        com.crackedgames.craftics.core.GridTile tile = arena.getTile(gp);
        return tile != null && tile.getType() == com.crackedgames.craftics.core.TileType.NORMAL;
    }

    /** Remember the state a cell held before the entity reached it. Capture-once per cell. */
    private void notePosition(ServerWorld world, UUID id, BlockPos pos) {
        java.util.LinkedHashMap<BlockPos, BlockState> path =
            flightPath.computeIfAbsent(id, k -> new java.util.LinkedHashMap<>());
        BlockPos key = pos.toImmutable();
        if (path.containsKey(key)) return;
        path.put(key, world.getBlockState(key));
        while (path.size() > FLIGHT_MEMORY) {
            path.remove(path.keySet().iterator().next());
        }
    }

    /** Log a block one of ours left in the world, so the end of the fight can undo it. */
    private void recordLanding(UUID id, BlockPos pos, BlockState prior) {
        BlockState placed = launchedState.get(id);
        if (placed == null) return;
        // Capture-once: two blocks landing on the same cell in one fight must restore the
        // state from before the FIRST of them, which is the only one that was ever real.
        landedBlocks.putIfAbsent(pos.toImmutable(), new Landing(placed, prior));
    }

    /**
     * Hand a landed block to the arena: an obstacle on the grid for as long as it stands,
     * and a countdown that breaks it the way a player-placed wall breaks - the same crack
     * overlay deepening turn by turn, the same removal at the end of it.
     *
     * <p>Only over a tile that was plain floor. Debris that comes down on a wall, in water,
     * or outside the arena is not the grid's business; it is still logged for restoration,
     * so it goes when the fight does.
     */
    private void markObstacleAt(com.crackedgames.craftics.core.GridArena landArena, BlockPos blockPos) {
        BlockPos arenaOrigin = landArena.getOrigin();
        int gx = blockPos.getX() - arenaOrigin.getX();
        int gz = blockPos.getZ() - arenaOrigin.getZ();
        com.crackedgames.craftics.core.GridPos gp =
            new com.crackedgames.craftics.core.GridPos(gx, gz);
        if (!landArena.isInBounds(gp)) return;
        if (!landArena.markVfxObstacle(gp)) return;
        // Null item: nobody paid for this block, so breaking it hands nothing back.
        landArena.markPlacedWall(gp, null, LANDED_DEBRIS_TURNS);
    }

    private void forgetEntity(UUID id) {
        arenaByEntity.remove(id);
        lastBlockPos.remove(id);
        launchedState.remove(id);
        flightPath.remove(id);
    }

    /**
     * Bin every entity still in the air and put back every block the landed ones left.
     *
     * <p>A landing is only undone if our block is still the one standing there: a player who
     * mined it, or a boss that built over it, has already answered for that cell and this must
     * not overwrite the answer.
     */
    public void clearAll(ServerWorld world) {
        for (UUID id : tracked.keySet()) {
            Entity e = world.getEntity(id);
            if (e != null) e.discard();
        }
        tracked.clear();
        arenaByEntity.clear();
        lastBlockPos.clear();
        launchedState.clear();
        flightPath.clear();

        for (Map.Entry<BlockPos, Landing> entry : landedBlocks.entrySet()) {
            Landing landing = entry.getValue();
            if (!world.getBlockState(entry.getKey()).equals(landing.placed())) continue;
            world.setBlockState(entry.getKey(), landing.prior(),
                net.minecraft.block.Block.NOTIFY_LISTENERS
                    | net.minecraft.block.Block.FORCE_STATE);
        }
        landedBlocks.clear();
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
