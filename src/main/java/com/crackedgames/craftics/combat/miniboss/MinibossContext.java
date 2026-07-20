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

    private final GridArena arena;
    private final Random rng;
    private final int round;
    private final List<CombatEntity> enemies;
    private final SpawnFn spawnFn;
    private final TileFn tileFn;
    private final BlockObjectFn blockObjectFn;
    private final java.util.function.Consumer<String> message;
    private final java.util.function.Consumer<String> banner;

    public MinibossContext(GridArena arena, Random rng, int round, List<CombatEntity> enemies,
                           SpawnFn spawnFn, TileFn tileFn, BlockObjectFn blockObjectFn,
                           java.util.function.Consumer<String> message,
                           java.util.function.Consumer<String> banner) {
        this.arena = arena; this.rng = rng; this.round = round; this.enemies = enemies;
        this.spawnFn = spawnFn; this.tileFn = tileFn; this.blockObjectFn = blockObjectFn;
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
    public void message(String s) { message.accept(s); }
    public void banner(String s) { banner.accept(s); }
}
