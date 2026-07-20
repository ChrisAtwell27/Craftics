package com.crackedgames.craftics.combat.miniboss.mechanics;

import com.crackedgames.craftics.combat.miniboss.MinibossContext;
import com.crackedgames.craftics.combat.miniboss.MinibossMechanic;
import com.crackedgames.craftics.combat.miniboss.MinibossSpawns;
import com.crackedgames.craftics.compat.creeperoverhaul.CreeperOverhaulCompat;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;
import com.crackedgames.craftics.core.TileType;
import com.crackedgames.craftics.level.LevelDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The snowy biome's level-4 miniboss: the Blizzard encounter. Opens with 3 creepers - real
 * {@code creeperoverhaul:snowy_creeper}s when Creeper Overhaul is loaded, plain
 * {@code minecraft:creeper} otherwise - ordinal-scaled exactly like
 * {@link DesertSandstormMechanic}'s husks. The fight layers two pressures on top of the base
 * creeper trio:
 *
 * <ul>
 *   <li>Raid-style reinforcement waves: every 3rd round, two more (snowy) creepers skitter in
 *       while the arena is under the {@link #CROWD_CAP}, mirroring
 *       {@link JungleBroodmotherMechanic}'s add-wave loop and live-tile dedup.</li>
 *   <li>A real wind gust every {@link #GUST_CADENCE} rounds: a random cardinal direction is
 *       rolled, telegraphed with sweeping arrow glyphs across the whole arena via
 *       {@link MinibossContext#windGust}, and every player + enemy on the grid is dragged that
 *       direction - the same primitive {@link com.crackedgames.craftics.combat.ai.boss.FrostboundAI}'s
 *       harpoon pull uses. A light frost accent (a handful of temporary {@link TileType#FROST}
 *       tiles on the downwind side) rides along for flavor.</li>
 * </ul>
 *
 * <p>No extra win objective - clearing all creepers (and any reinforcement waves) completes the
 * fight, so {@link #isComplete} uses the interface default (always true).
 */
public final class SnowyBlizzardMechanic implements MinibossMechanic {

    private static final int CREEPER_COUNT = 3;
    private static final int ADD_CADENCE = 3; // reinforcement wave every 3rd round
    private static final int ADD_WAVE_SIZE = 2;
    private static final int CROWD_CAP = 8; // skip reinforcements if this many enemies are already alive
    private static final int GUST_CADENCE = 2; // gust cycle: telegraph one round, drag the next
    private static final int FROST_TILES_MIN = 2;
    private static final int FROST_TILES_MAX = 3; // inclusive
    private static final int FROST_DURATION = 2; // rounds the gust's frost accent lingers

    /** The gust telegraphed last round, waiting to resolve this round. null = nothing pending;
     *  a 2-int {dirX, dirZ}. Instance state on the shared singleton, reset in onFightStart. */
    private int[] pendingGust = null;

    /** Wind direction rolled each gust, as a (dx,dz) cardinal pair. */
    private static final int DIR_NORTH = 0; // toward z=0
    private static final int DIR_SOUTH = 1; // toward z=height-1
    private static final int DIR_EAST = 2;  // toward x=width-1
    private static final int DIR_WEST = 3;  // toward x=0

    /** Resolves the themed creeper type for this fight: the Creeper Overhaul snowy variant when
     *  the mod is loaded, else the vanilla creeper. Applied to both the initial trio and every
     *  reinforcement wave so the whole fight reads as "snowy creepers", not just the opener. */
    private static String creeperTypeId() {
        return CreeperOverhaulCompat.isLoaded() ? "creeperoverhaul:snowy_creeper" : "minecraft:creeper";
    }

    @Override
    public String biomeId() {
        return "snowy";
    }

    @Override
    public String introTitle() {
        return "§b§l☠ Blizzard";
    }

    @Override
    public List<LevelDefinition.EnemySpawn> initialSpawns(int width, int height, int ordinal, Random rng) {
        int hpBonus = ordinal * com.crackedgames.craftics.CrafticsMod.CONFIG.hpPerBiome();
        int atkBonus = ordinal / Math.max(1, com.crackedgames.craftics.CrafticsMod.CONFIG.atkPerBiome());

        List<LevelDefinition.EnemySpawn> spawns = new ArrayList<>();
        List<GridPos> used = new ArrayList<>();
        used.add(new GridPos(width / 2, 0)); // player start - never spawn here

        String creeperType = creeperTypeId();
        for (int i = 0; i < CREEPER_COUNT; i++) {
            GridPos pos = MinibossSpawns.findOpen(width, height, used, rng);
            if (pos == null) continue;
            used.add(pos);
            spawns.add(new LevelDefinition.EnemySpawn(creeperType, pos,
                12 + hpBonus, 5 + atkBonus, 0, 1));
        }

        return spawns;
    }

    @Override
    public void onFightStart(MinibossContext ctx) {
        pendingGust = null;
        ctx.banner(introTitle());
    }

    @Override
    public void onRoundStart(MinibossContext ctx) {
        spawnReinforcements(ctx);
        blowWindGust(ctx);
    }

    /** Every 3rd round, add 2 more (snowy) creepers while the arena isn't already crowded. */
    private void spawnReinforcements(MinibossContext ctx) {
        if (ctx.round() % ADD_CADENCE != 0) return;

        int liveCount = 0;
        for (var e : ctx.enemies()) {
            if (e.isAlive()) liveCount++;
        }
        if (liveCount >= CROWD_CAP) return; // arena too crowded, skip this wave

        GridArena arena = ctx.arena();
        List<GridPos> used = new ArrayList<>();
        for (var e : ctx.enemies()) {
            if (e.isAlive() && e.getGridPos() != null) used.add(e.getGridPos());
        }

        boolean spawnedAny = false;
        for (int i = 0; i < ADD_WAVE_SIZE; i++) {
            GridPos pos = MinibossSpawns.findOpen(arena.getWidth(), arena.getHeight(), used, ctx.rng());
            if (pos == null) continue;
            used.add(pos);
            ctx.spawnMob(creeperTypeId(), pos, 12, 5, 0, 1);
            spawnedAny = true;
        }
        if (spawnedAny) ctx.message("§bMore creepers stagger out of the whiteout!");
    }

    /**
     * Every {@link #GUST_CADENCE} rounds, rolls a random cardinal wind direction and calls
     * {@link MinibossContext#windGust} - the real Frostbound-style gust: sweeping arrow glyphs
     * across the whole arena telegraph the drag direction, then every player and enemy on the
     * grid is shoved that way. A small scatter of temporary FROST tiles rides along on the
     * downwind side as a light accent, reusing the tile type's existing "Frozen risk on step"
     * contract from {@link TileType}.
     */
    private void blowWindGust(MinibossContext ctx) {
        // Resolve a gust telegraphed last round: NOW drag everything the warned direction.
        if (pendingGust != null) {
            int dirX = pendingGust[0];
            int dirZ = pendingGust[1];
            pendingGust = null;
            ctx.message("§b❄ The gust hits - everything is dragged " + dirNameOf(dirX, dirZ) + "!");
            ctx.windGust(dirX, dirZ);
            windGustFrostAccent(ctx, dirX, dirZ);
            return; // don't also telegraph the same round - one action per round reads clearly
        }

        // Otherwise, on the cadence, telegraph the NEXT gust: paint the arrows, no drag yet.
        if (ctx.round() % GUST_CADENCE != 0) return;

        Random rng = ctx.rng();
        int dir = rng.nextInt(4);
        int dirX;
        int dirZ;
        switch (dir) {
            case DIR_NORTH -> { dirX = 0; dirZ = -1; }
            case DIR_SOUTH -> { dirX = 0; dirZ = 1; }
            case DIR_EAST -> { dirX = 1; dirZ = 0; }
            default -> { dirX = -1; dirZ = 0; }
        }
        pendingGust = new int[]{dirX, dirZ};
        ctx.message("§b❄ The wind rises to the " + dirNameOf(dirX, dirZ)
            + " - the arrows show which way it will drag. Brace!");
        ctx.windTelegraph(dirX, dirZ);
    }

    private static String dirNameOf(int dirX, int dirZ) {
        if (dirZ < 0) return "north";
        if (dirZ > 0) return "south";
        if (dirX > 0) return "east";
        return "west";
    }

    /** Scatter a few temporary FROST tiles on the downwind side of a resolved gust (flavor). */
    private void windGustFrostAccent(MinibossContext ctx, int dirX, int dirZ) {
        GridArena arena = ctx.arena();
        int width = arena.getWidth();
        int height = arena.getHeight();
        Random rng = ctx.rng();
        int dir = dirZ < 0 ? DIR_NORTH : dirZ > 0 ? DIR_SOUTH : dirX > 0 ? DIR_EAST : DIR_WEST;
        // Light frost accent on the downwind side, purely cosmetic follow-through.
        int tileCount = FROST_TILES_MIN + rng.nextInt(FROST_TILES_MAX - FROST_TILES_MIN + 1);
        for (int i = 0; i < tileCount; i++) {
            int x;
            int z;
            switch (dir) {
                case DIR_NORTH -> { // downwind = low z half
                    x = rng.nextInt(Math.max(1, width));
                    z = rng.nextInt(Math.max(1, height / 2));
                }
                case DIR_SOUTH -> { // downwind = high z half
                    x = rng.nextInt(Math.max(1, width));
                    int half = Math.max(1, height / 2);
                    z = half + rng.nextInt(Math.max(1, height - half));
                }
                case DIR_EAST -> { // downwind = high x half
                    int half = Math.max(1, width / 2);
                    x = half + rng.nextInt(Math.max(1, width - half));
                    z = rng.nextInt(Math.max(1, height));
                }
                default -> { // DIR_WEST - downwind = low x half
                    x = rng.nextInt(Math.max(1, width / 2));
                    z = rng.nextInt(Math.max(1, height));
                }
            }
            ctx.placeTemporaryTile(new GridPos(x, z), TileType.FROST, FROST_DURATION);
        }
    }
}
