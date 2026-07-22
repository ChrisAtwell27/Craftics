package com.crackedgames.craftics.combat.miniboss;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;
import com.crackedgames.craftics.core.TileType;
import net.minecraft.block.Block;
import java.util.List;
import java.util.Random;

/**
 * The slice of CombatManager a mechanic is allowed to touch. Constructed fresh each round so
 * a mechanic never holds a stale reference. All actions delegate to CombatManager helpers.
 */
public final class MinibossContext {
    public interface SpawnFn { CombatEntity spawn(String typeId, GridPos tile, int hp, int atk, int def, int range); }
    public interface TileFn  { void place(GridPos p, TileType type, int turns); }
    /**
     * Places a permanent, pickaxe-breakable OBSTACLE tile backed by a specific
     * world block (e.g. a sculk sensor). Unlike {@link #spawnBlockObject} this is
     * NOT an entity with HP - it's a normal arena obstacle the player mines for
     * 1 AP, after which the tile reverts to walkable NORMAL. The effect detects
     * the break by re-validating the tile against the arena. Delegates to
     * CombatManager's {@code placeObstacleTile}.
     */
    public interface ObstacleFn { void place(GridPos p, Block block); }
    /**
     * Places a block-backed destructible object (grave, banner, etc.) rather than a normal mob
     * entity - {@link #spawnMob} cannot do this, it only spawns real mob entities and would not
     * render or behave correctly for something like {@code craftics:grave}. Delegates to
     * CombatManager's {@code placeBlockObject}, which handles the fake negative entity id,
     * scenery/inert flags, and the world block placement.
     */
    public interface BlockObjectFn { CombatEntity place(String typeId, GridPos tile, int hp, Block block); }
    /**
     * Telegraphs sweeping arrow glyphs across the whole arena in (dirX,dirZ) and drags every
     * unit on the grid - all party players and every live enemy - {@code tiles} tiles that
     * direction, mirroring {@link com.crackedgames.craftics.combat.ai.boss.FrostboundAI}'s
     * harpoon-pull gust. Delegates to CombatManager's {@code triggerWindGust}, which has the
     * player/entity movement primitives a {@link MinibossContext} does not expose directly.
     */
    public interface WindGustFn { void gust(int dirX, int dirZ); }
    /**
     * Applies a status effect to EVERY live party member (solo: just the player) - the
     * party-wide analogue of a single {@code addEffectHooked} call. Used by weather effects
     * (e.g. sandstorm blinding everyone).
     */
    public interface PartyEffectFn { void apply(com.crackedgames.craftics.combat.CombatEffects.EffectType type, int turns); }
    public interface SoundFn { void play(net.minecraft.sound.SoundEvent sound, float volume, float pitch); }
    public interface AmbientParticleFn { void spawn(net.minecraft.particle.ParticleEffect particle, double x, double y, double z, int count, double dx, double dy, double dz, double speed); }
    public interface ParticipantsFn { java.util.List<net.minecraft.server.network.ServerPlayerEntity> get(); }
    public interface WarnTilesFn { void warn(java.util.List<GridPos> tiles); }
    /**
     * Runs the river current: paints per-water-tile flow arrows and, unless {@code telegraphOnly},
     * sweeps every player/enemy standing on water toward the nearest arena edge. Delegates to
     * CombatManager's {@code triggerRiverCurrent}, which owns the water scan and the drag/knockback
     * movement primitives a {@link MinibossContext} does not expose directly.
     */
    public interface RiverCurrentFn { void run(boolean telegraphOnly); }
    public interface SwiftSneakFn { boolean has(net.minecraft.server.network.ServerPlayerEntity p); }
    public interface PlayerTileFn { GridPos of(net.minecraft.server.network.ServerPlayerEntity p); }

    private final GridArena arena;
    private final Random rng;
    private final int round;
    private final List<CombatEntity> enemies;
    private final SpawnFn spawnFn;
    private final TileFn tileFn;
    private final ObstacleFn obstacleFn;
    private final BlockObjectFn blockObjectFn;
    private final WindGustFn windGustFn;
    private final WindGustFn windTelegraphFn;
    private final PartyEffectFn partyEffectFn;
    private final SoundFn soundFn;
    private final AmbientParticleFn ambientParticleFn;
    private final ParticipantsFn participantsFn;
    private final WarnTilesFn warnTilesFn;
    private final RiverCurrentFn riverCurrentFn;
    private final SwiftSneakFn swiftSneakFn;
    private final PlayerTileFn playerTileFn;
    private final java.util.function.Consumer<String> message;
    private final java.util.function.Consumer<String> banner;

    public MinibossContext(GridArena arena, Random rng, int round, List<CombatEntity> enemies,
                           SpawnFn spawnFn, TileFn tileFn, ObstacleFn obstacleFn, BlockObjectFn blockObjectFn,
                           WindGustFn windGustFn, WindGustFn windTelegraphFn,
                           PartyEffectFn partyEffectFn,
                           SoundFn soundFn, AmbientParticleFn ambientParticleFn,
                           ParticipantsFn participantsFn, WarnTilesFn warnTilesFn,
                           RiverCurrentFn riverCurrentFn, SwiftSneakFn swiftSneakFn,
                           PlayerTileFn playerTileFn,
                           java.util.function.Consumer<String> message,
                           java.util.function.Consumer<String> banner) {
        this.arena = arena; this.rng = rng; this.round = round; this.enemies = enemies;
        this.spawnFn = spawnFn; this.tileFn = tileFn; this.obstacleFn = obstacleFn; this.blockObjectFn = blockObjectFn;
        this.windGustFn = windGustFn; this.windTelegraphFn = windTelegraphFn;
        this.partyEffectFn = partyEffectFn;
        this.soundFn = soundFn; this.ambientParticleFn = ambientParticleFn;
        this.participantsFn = participantsFn; this.warnTilesFn = warnTilesFn;
        this.riverCurrentFn = riverCurrentFn; this.swiftSneakFn = swiftSneakFn;
        this.playerTileFn = playerTileFn;
        this.message = message; this.banner = banner;
    }

    public GridArena arena() { return arena; }
    public Random rng() { return rng; }
    public int round() { return round; }
    public List<CombatEntity> enemies() { return enemies; }
    public CombatEntity spawnMob(String typeId, GridPos tile, int hp, int atk, int def, int range) {
        return spawnFn.spawn(typeId, tile, hp, atk, def, range);
    }
    public CombatEntity spawnBlockObject(String typeId, GridPos tile, int hp, Block block) {
        return blockObjectFn.place(typeId, tile, hp, block);
    }
    public void placeTemporaryTile(GridPos p, TileType type, int turns) { tileFn.place(p, type, turns); }
    /** Places a permanent, pickaxe-breakable OBSTACLE tile backed by {@code block}
     *  (see {@link ObstacleFn}). The player mines it for 1 AP; the effect detects
     *  the break by re-checking the tile's type/block against the arena. */
    public void placeObstacle(GridPos p, Block block) { obstacleFn.place(p, block); }
    /** Telegraphs arrows across the arena and drags every player + enemy (dirX,dirZ) 2 tiles. */
    public void windGust(int dirX, int dirZ) { windGustFn.gust(dirX, dirZ); }
    /** Paints the gust's warning arrows in (dirX,dirZ) WITHOUT dragging - call a round before
     *  {@link #windGust} so the player sees which way the wind will pull. */
    public void windTelegraph(int dirX, int dirZ) { windTelegraphFn.gust(dirX, dirZ); }
    /** Applies a status effect to EVERY live party member (solo: just the player) for {@code turns}
     *  of the PLAYER's own turns. Effects run from onRoundStart, which fires in the enemy phase;
     *  a player status ticks down once during that enemy phase, so a bare N would expire before the
     *  player next acts. We add +1 here so {@code turns} means "turns the player actually feels it"
     *  - the caller passes the player-facing duration and doesn't have to know the turn ordering.
     *  Used by weather/hazard effects (sandstorm blind, sculk darkness, ...). */
    public void applyPartyEffect(com.crackedgames.craftics.combat.CombatEffects.EffectType type, int turns) {
        partyEffectFn.apply(type, turns <= 0 ? turns : turns + 1);
    }
    /** Plays a sound to the whole party, positioned at the arena. Weather/event cues. */
    public void playSound(net.minecraft.sound.SoundEvent sound, float volume, float pitch) {
        soundFn.play(sound, volume, pitch);
    }
    /** Spawns ambient particles (rain, snow, embers...) at an arena-relative position. */
    public void spawnAmbientParticle(net.minecraft.particle.ParticleEffect particle, double x, double y, double z,
                                      int count, double dx, double dy, double dz, double speed) {
        ambientParticleFn.spawn(particle, x, y, z, count, dx, dy, dz, speed);
    }
    /** Ambient particles centered on a grid TILE (world coords resolved from the arena), so a
     *  mechanic doesn't have to repeat the origin/grid math. Spawned a little above the floor
     *  (gridToBlockPos is the Y+1 overlay level) with a small XZ spread across the tile. */
    public void spawnTileParticle(net.minecraft.particle.ParticleEffect particle, GridPos tile,
                                   int count, double spread, double speed) {
        if (arena == null || tile == null) return;
        net.minecraft.util.math.BlockPos bp = arena.gridToBlockPos(tile);
        ambientParticleFn.spawn(particle, bp.getX() + 0.5, bp.getY() + 0.2, bp.getZ() + 0.5,
            count, spread, 0.15, spread, speed);
    }

    /** A one-shot "puff" burst on a tile: a spawn/impact/telegraph marker. Denser and taller than
     *  {@link #spawnTileParticle} so a discrete event (a mob warping in, a rock crushing down, a
     *  vent about to erupt) reads at a glance. */
    public void spawnHazardBurst(net.minecraft.particle.ParticleEffect particle, GridPos tile) {
        if (arena == null || tile == null) return;
        net.minecraft.util.math.BlockPos bp = arena.gridToBlockPos(tile);
        ambientParticleFn.spawn(particle, bp.getX() + 0.5, bp.getY() + 0.4, bp.getZ() + 0.5,
            12, 0.35, 0.35, 0.35, 0.05);
    }

    public void message(String s) { message.accept(s); }
    public void banner(String s) { banner.accept(s); }
    /** Live party players (solo: just the acting player). */
    public java.util.List<net.minecraft.server.network.ServerPlayerEntity> participants() { return participantsFn.get(); }
    /** Paints a red danger-warning overlay on {@code tiles} for the party - a standalone
     *  telegraph (e.g. the sculk sensor's next-round silverfish spawn), independent of any
     *  boss attack warning. */
    public void warnTiles(java.util.List<GridPos> tiles) { warnTilesFn.warn(tiles); }
    /** Runs the river current. {@code telegraphOnly=true} just paints the water-tile flow arrows
     *  (the warning round); {@code false} also sweeps everyone standing in the water toward the
     *  nearest arena edge. See {@link RiverCurrentFn}. */
    public void riverCurrent(boolean telegraphOnly) { riverCurrentFn.run(telegraphOnly); }
    /** True if the player's boots have Swift Sneak (bypasses sculk-sensor triggers). */
    public boolean hasSwiftSneak(net.minecraft.server.network.ServerPlayerEntity p) { return swiftSneakFn.has(p); }
    /** The grid tile a specific party member currently stands on (not just the acting player). */
    public GridPos tileOf(net.minecraft.server.network.ServerPlayerEntity p) { return playerTileFn.of(p); }
}
