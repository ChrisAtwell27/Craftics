package com.crackedgames.craftics.combat.sherd;

import com.crackedgames.craftics.CrafticsMod;
import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.PotterySherdSpells;
import com.crackedgames.craftics.combat.ProjectileSpawner;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;
import com.crackedgames.craftics.core.GridTile;
import com.crackedgames.craftics.core.TileType;
import com.crackedgames.craftics.network.TileFlashPayload;
import com.crackedgames.craftics.vfx.GhostBlocks;
import com.crackedgames.craftics.vfx.Vfx;
import com.crackedgames.craftics.vfx.VfxContext;
import com.crackedgames.craftics.vfx.VfxDescriptor;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * What a sherd's staging is written against: the arena it plays on, where the cast came from
 * and where it landed, and a timeline to put things on.
 *
 * <p>{@link SpellVisuals} covers the shape every spell shares - gather, streak, burst. This is
 * for the part that is the spell's own: the floor turning to water under a wave, a spike of
 * stone coming up through it, the ground left scorched. A {@link SpellVisuals.Choreography} is
 * handed one of these and says what happens on which tick.
 *
 * <h2>Rules it keeps so a choreography cannot break them</h2>
 * <ul>
 *   <li><b>Nothing here changes the fight.</b> Blocks are shown, not placed (see
 *       {@link GhostBlocks}); the grid, the tiles and the combatants are never touched. The
 *       spell has already resolved by the time any of this is seen.</li>
 *   <li><b>Nothing a player could fall into or be pushed out of is shown where one
 *       stands.</b> A player's own game believes what it is shown, and would try to swim in a
 *       pool or step out of a wall that is not there. The floor under them may still change
 *       to another solid block, which they go on standing on.</li>
 *   <li><b>The floor is only repainted where it is plain floor.</b> A pool, a pit or a wall
 *       keeps its own look: painting over it would be lying about the terrain.</li>
 *   <li><b>Everything is scheduled up front.</b> Every method takes the tick it happens on,
 *       counted from the cast. A choreography lays out its whole timeline when it is called
 *       and must not call back into this from inside {@link #at}.</li>
 *   <li><b>It does not make the player wait.</b> Only the first {@value #HOLDS_TURN_TICKS}
 *       ticks hold the caster's turn. A choreography may run longer than that; the rest of
 *       it plays out behind whatever they do next.</li>
 * </ul>
 */
public final class SpellStage {

    private final ServerWorld world;
    private final GridArena arena;
    private final GridPos caster;
    private final List<GridPos> targets;
    private final List<GridPos> origins;
    private final List<GridPos> pets = new ArrayList<>();
    private final int radius;
    private final Set<GridPos> playerTiles = new HashSet<>();
    private final boolean blocksAllowed;

    SpellStage(SpellContext ctx, List<GridPos> targets, List<GridPos> origins, int radius) {
        this.world = ctx.world();
        this.arena = ctx.arena();
        this.caster = ctx.casterPos();
        this.targets = List.copyOf(targets);
        this.origins = List.copyOf(origins);
        for (CombatEntity pet : ctx.livePets()) pets.add(pet.getGridPos());
        this.radius = Math.max(0, radius);
        BlockPos origin = arena.getOrigin();
        for (ServerPlayerEntity player : world.getPlayers()) {
            BlockPos at = player.getBlockPos();
            playerTiles.add(new GridPos(at.getX() - origin.getX(), at.getZ() - origin.getZ()));
        }
        playerTiles.add(caster);
        var config = CrafticsMod.CONFIG;
        this.blocksAllowed = config == null
            || (config.vfxBlockEntitiesEnabled() && config.vfxIntensity() >= 0.01f);
    }

    public ServerWorld world() { return world; }
    public GridArena arena() { return arena; }
    /** The tile the cast came from. */
    public GridPos caster() { return caster; }
    /** The tiles this part of the spell landed on. Empty when it hit nothing. */
    public List<GridPos> targets() { return targets; }
    /**
     * Where each of {@link #targets} stood before this part of the spell touched it, in the
     * same order. Differs from it only for a target the spell moved: pulled, or thrown.
     */
    public List<GridPos> origins() { return origins; }
    /** Where the caster's living pets stand. */
    public List<GridPos> pets() { return pets; }
    /** How far this part of the spell reaches around its centre, in steps. 0 for a single target. */
    public int radius() { return radius; }

    /** Whether {@code tile} is ground a spell may draw on: inside the arena and not a hole in it. */
    public boolean isGround(GridPos tile) {
        if (tile == null || !arena.isInBounds(tile)) return false;
        GridTile t = arena.getTile(tile);
        return t != null && t.getType() != TileType.VOID;
    }

    /**
     * How long a spell's staging may hold the caster's turn. Whatever is scheduled later than
     * this still plays, in the background, while they get on with their turn.
     */
    public static final int HOLDS_TURN_TICKS = 12;

    /** Do {@code action} on {@code tick}. Tick 0 is the moment of the cast. */
    public void at(int tick, Runnable action) {
        if (tick <= 0) action.run();
        else if (tick <= HOLDS_TURN_TICKS) PotterySherdSpells.queue(tick, action);
        else PotterySherdSpells.queueBackground(tick, action);
    }

    // ── Blocks ───────────────────────────────────────────────────────────────

    /** Show the floor under {@code tile} as {@code state} from {@code tick}, for {@code hold} ticks. */
    public void floor(int tick, GridPos tile, BlockState state, int hold) {
        if (!blocksAllowed || !arena.isInBounds(tile)) return;
        GridTile t = arena.getTile(tile);
        if (t == null || t.getType() != TileType.NORMAL) return;
        BlockPos pos = arena.gridToBlockPos(tile).down();
        // Under a player, only something they can go on standing on.
        if (playerTiles.contains(tile) && !Block.isShapeFullCube(state.getCollisionShape(world, pos))) return;
        at(tick, () -> GhostBlocks.of(world).show(world, pos, state, hold));
    }

    /**
     * Show {@code state} standing on {@code tile}, {@code up} blocks above the floor (0 is the
     * block a mob's feet are in), from {@code tick} for {@code hold} ticks. Only into empty
     * air: it never paints over a wall, a plant or anything else already there.
     */
    public void prop(int tick, GridPos tile, int up, BlockState state, int hold) {
        if (!blocksAllowed || !isGround(tile) || playerTiles.contains(tile)) return;
        BlockPos pos = arena.gridToBlockPos(tile).up(up);
        at(tick, () -> {
            if (!world.getBlockState(pos).isAir()) return;
            GhostBlocks.of(world).show(world, pos, state, hold);
        });
    }

    // ── Particles, sound, light ──────────────────────────────────────────────

    /** A puff of {@code particle} over {@code tile}, {@code height} blocks above the floor. */
    public void burst(int tick, GridPos tile, double height, ParticleEffect particle,
                      int count, double spread, double speed) {
        if (!arena.isInBounds(tile)) return;
        BlockPos pos = arena.gridToBlockPos(tile);
        at(tick, () -> world.spawnParticles(particle,
            pos.getX() + 0.5, pos.getY() + height, pos.getZ() + 0.5, count, spread, spread * 0.6, spread, speed));
    }

    /** Chips of the floor under {@code tile} itself, whatever this arena's floor is made of. */
    public void floorDebris(int tick, GridPos tile, int count) {
        if (!isGround(tile)) return;
        BlockPos surface = arena.gridToBlockPos(tile);
        at(tick, () -> {
            BlockState floor = world.getBlockState(surface.down());
            if (floor.isAir()) return;
            world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, floor),
                surface.getX() + 0.5, surface.getY() + 0.15, surface.getZ() + 0.5, count, 0.3, 0.1, 0.3, 0.12);
        });
    }

    /** Fragments of {@code state} flying off {@code tile}, as if a block of it had just broken there. */
    public void shatter(int tick, GridPos tile, double height, BlockState state, int count) {
        burst(tick, tile, height, new BlockStateParticleEffect(ParticleTypes.BLOCK, state), count, 0.3, 0.12);
    }

    /** A flat circle of {@code particle} around the middle of {@code tile}. */
    public void circle(int tick, GridPos tile, double radius, double height, ParticleEffect particle, int count) {
        if (!arena.isInBounds(tile)) return;
        BlockPos pos = arena.gridToBlockPos(tile);
        at(tick, () -> {
            for (int i = 0; i < count; i++) {
                double angle = 2 * Math.PI * i / count;
                world.spawnParticles(particle, pos.getX() + 0.5 + Math.cos(angle) * radius,
                    pos.getY() + height, pos.getZ() + 0.5 + Math.sin(angle) * radius, 1, 0.02, 0.02, 0.02, 0.0);
            }
        });
    }

    /** A vertical line of {@code particle} over {@code tile}, between two heights above the floor. */
    public void column(int tick, GridPos tile, double from, double to, ParticleEffect particle, int count) {
        if (!arena.isInBounds(tile)) return;
        BlockPos pos = arena.gridToBlockPos(tile);
        at(tick, () -> {
            for (int i = 0; i < count; i++) {
                double y = from + (to - from) * i / Math.max(1, count - 1);
                world.spawnParticles(particle, pos.getX() + 0.5, pos.getY() + y, pos.getZ() + 0.5,
                    1, 0.05, 0.02, 0.05, 0.0);
            }
        });
    }

    /** A line of {@code particle} arcing from one tile to another. {@code secondary} may be null. */
    public void streak(int tick, GridPos from, GridPos to, ParticleEffect particle, ParticleEffect secondary,
                       int count, double arc) {
        BlockPos a = arena.gridToBlockPos(from);
        BlockPos b = arena.gridToBlockPos(to);
        at(tick, () -> ProjectileSpawner.spawnSpellTrail(world, a, b, particle, secondary, count, arc));
    }

    /**
     * A bolt of lightning on {@code tile}. Real to look at and to hear, and nothing else: it
     * starts no fire and strikes nobody.
     */
    public void lightning(int tick, GridPos tile) {
        if (!arena.isInBounds(tile)) return;
        BlockPos pos = arena.gridToBlockPos(tile);
        at(tick, () -> {
            //? if <=1.21.1 {
            net.minecraft.entity.LightningEntity bolt = net.minecraft.entity.EntityType.LIGHTNING_BOLT.create(world);
            //?} else {
            /*net.minecraft.entity.LightningEntity bolt = net.minecraft.entity.EntityType.LIGHTNING_BOLT.create(
                world, net.minecraft.entity.SpawnReason.TRIGGERED);
            *///?}
            if (bolt == null) return;
            bolt.setCosmetic(true);
            bolt.refreshPositionAfterTeleport(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
            world.spawnEntity(bolt);
        });
    }

    public void sound(int tick, GridPos tile, SoundEvent sound, float volume, float pitch) {
        BlockPos pos = arena.gridToBlockPos(tile);
        at(tick, () -> world.playSound(null, pos, sound, SoundCategory.PLAYERS, volume, pitch));
    }

    /** Light {@code tiles} up on the grid overlay in {@code argb} for {@code ticks}. */
    public void flash(int tick, Collection<GridPos> tiles, int argb, int ticks) {
        int[] packed = new int[tiles.size() * 2];
        int i = 0;
        for (GridPos tile : tiles) {
            packed[i++] = tile.x();
            packed[i++] = tile.z();
        }
        if (packed.length == 0) return;
        at(tick, () -> {
            TileFlashPayload payload = new TileFlashPayload(packed, argb, ticks);
            for (ServerPlayerEntity player : world.getPlayers()) ServerPlayNetworking.send(player, payload);
        });
    }

    /** Shake every watcher's camera. Scaled, and switched off, by the VFX intensity setting. */
    public void shake(int tick, float intensity, int ticks) {
        play(VfxDescriptor.builder().phase(Math.max(0, tick)).shake(intensity, ticks).build());
    }

    /** Wash every watcher's screen with {@code argb} for {@code ticks}. */
    public void screenFlash(int tick, int argb, int ticks) {
        play(VfxDescriptor.builder().phase(Math.max(0, tick)).screenFlash(argb, ticks).build());
    }

    private void play(VfxDescriptor descriptor) {
        BlockPos from = arena.gridToBlockPos(caster);
        BlockPos to = targets.isEmpty() ? from : arena.gridToBlockPos(targets.get(0));
        Vfx.play(world, descriptor, VfxContext.ofBlocks(from, to, 0f, arena));
    }
}
