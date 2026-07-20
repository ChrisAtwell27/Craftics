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

    private final GridArena arena;
    private final Random rng;
    private final int round;
    private final List<CombatEntity> enemies;
    private final SpawnFn spawnFn;
    private final TileFn tileFn;
    private final BlockObjectFn blockObjectFn;
    private final WindGustFn windGustFn;
    private final WindGustFn windTelegraphFn;
    private final PartyEffectFn partyEffectFn;
    private final java.util.function.Consumer<String> message;
    private final java.util.function.Consumer<String> banner;

    public MinibossContext(GridArena arena, Random rng, int round, List<CombatEntity> enemies,
                           SpawnFn spawnFn, TileFn tileFn, BlockObjectFn blockObjectFn,
                           WindGustFn windGustFn, WindGustFn windTelegraphFn,
                           PartyEffectFn partyEffectFn,
                           java.util.function.Consumer<String> message,
                           java.util.function.Consumer<String> banner) {
        this.arena = arena; this.rng = rng; this.round = round; this.enemies = enemies;
        this.spawnFn = spawnFn; this.tileFn = tileFn; this.blockObjectFn = blockObjectFn;
        this.windGustFn = windGustFn; this.windTelegraphFn = windTelegraphFn;
        this.partyEffectFn = partyEffectFn;
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
    /** Telegraphs arrows across the arena and drags every player + enemy (dirX,dirZ) 2 tiles. */
    public void windGust(int dirX, int dirZ) { windGustFn.gust(dirX, dirZ); }
    /** Paints the gust's warning arrows in (dirX,dirZ) WITHOUT dragging - call a round before
     *  {@link #windGust} so the player sees which way the wind will pull. */
    public void windTelegraph(int dirX, int dirZ) { windTelegraphFn.gust(dirX, dirZ); }
    /** Applies a status effect to EVERY live party member (solo: just the player) - the
     *  party-wide analogue of a single addEffectHooked call. Used by weather effects
     *  (e.g. sandstorm blinding everyone). */
    public void applyPartyEffect(com.crackedgames.craftics.combat.CombatEffects.EffectType type, int turns) {
        partyEffectFn.apply(type, turns);
    }
    public void message(String s) { message.accept(s); }
    public void banner(String s) { banner.accept(s); }
}
