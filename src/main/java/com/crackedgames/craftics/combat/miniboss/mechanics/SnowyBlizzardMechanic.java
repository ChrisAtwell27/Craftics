package com.crackedgames.craftics.combat.miniboss.mechanics;

import com.crackedgames.craftics.combat.miniboss.MinibossContext;
import com.crackedgames.craftics.combat.miniboss.MinibossMechanic;
import com.crackedgames.craftics.combat.miniboss.MinibossSpawns;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;
import com.crackedgames.craftics.core.TileType;
import com.crackedgames.craftics.level.LevelDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The snowy biome's level-4 miniboss: the Blizzard encounter. Opens with 3 creepers (the
 * "snowy/gelid creepers" - themed via display only, the spawned type is the normal
 * {@code minecraft:creeper}), ordinal-scaled exactly like {@link DesertSandstormMechanic}'s husks.
 * The fight layers two pressures on top of the base creeper trio:
 *
 * <ul>
 *   <li>Raid-style reinforcement waves: every 3rd round, two more creepers skitter in while the
 *       arena is under the {@link #CROWD_CAP}, mirroring {@link JungleBroodmotherMechanic}'s
 *       add-wave loop and live-tile dedup.</li>
 *   <li>A shifting frost gust: every round a random wind direction (N/S/E/W) is rolled purely for
 *       flavor messaging, and a scatter of temporary {@link TileType#FROST} tiles is painted across
 *       the "downwind" half of the arena (the half the wind is blowing toward), reading as a
 *       blizzard sweeping across the ground.</li>
 * </ul>
 *
 * <p>Design note: the brief's literal intent is a wind gust that shoves every unit 1 tile and
 * carries a Frozen risk on the party. {@link MinibossContext} has no knockback/shove primitive and
 * no way to reach the player/party - it only exposes {@link MinibossContext#enemies()} - so
 * inventing either is out of scope for this task. This mechanic implements the achievable
 * equivalent: the gust becomes a directional scatter of walkable FROST hazard tiles (the tile type
 * itself already carries the "Blizzard applies Frozen risk on step" contract per its declaration in
 * {@link TileType}), so stepping through the downwind zone is where the Frozen risk actually lives,
 * without needing to move any unit. A future {@code moveUnit}/{@code damageParty} context primitive
 * would let this mechanic apply the literal shove + Frozen-on-everyone version described in the
 * brief.
 *
 * <p>No extra win objective - clearing all creepers (and any reinforcement waves) completes the
 * fight, so {@link #isComplete} uses the interface default (always true).
 */
public final class SnowyBlizzardMechanic implements MinibossMechanic {

    private static final int CREEPER_COUNT = 3;
    private static final int ADD_CADENCE = 3; // reinforcement wave every 3rd round
    private static final int ADD_WAVE_SIZE = 2;
    private static final int CROWD_CAP = 8; // skip reinforcements if this many enemies are already alive
    private static final int FROST_TILES_MIN = 3;
    private static final int FROST_TILES_MAX = 4; // inclusive
    private static final int FROST_DURATION = 2; // rounds the gust's frost patch lingers

    /** Wind direction rolled each round, purely for flavor + which arena half gets frosted. */
    private static final int DIR_NORTH = 0; // toward z=0
    private static final int DIR_SOUTH = 1; // toward z=height-1
    private static final int DIR_EAST = 2;  // toward x=width-1
    private static final int DIR_WEST = 3;  // toward x=0

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

        for (int i = 0; i < CREEPER_COUNT; i++) {
            GridPos pos = MinibossSpawns.findOpen(width, height, used, rng);
            if (pos == null) continue;
            used.add(pos);
            spawns.add(new LevelDefinition.EnemySpawn("minecraft:creeper", pos,
                12 + hpBonus, 5 + atkBonus, 0, 1));
        }

        return spawns;
    }

    @Override
    public void onFightStart(MinibossContext ctx) {
        ctx.banner(introTitle());
    }

    @Override
    public void onRoundStart(MinibossContext ctx) {
        spawnReinforcements(ctx);
        blowFrostGust(ctx);
    }

    /** Every 3rd round, add 2 more creepers while the arena isn't already crowded. */
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
            ctx.spawnMob("minecraft:creeper", pos, 12, 5, 0, 1);
            spawnedAny = true;
        }
        if (spawnedAny) ctx.message("§bMore creepers stagger out of the whiteout!");
    }

    /**
     * Rolls a random wind direction each round and paints a scatter of temporary FROST tiles
     * across the downwind half of the arena - the half the wind is blowing toward.
     */
    private void blowFrostGust(MinibossContext ctx) {
        GridArena arena = ctx.arena();
        int width = arena.getWidth();
        int height = arena.getHeight();
        Random rng = ctx.rng();

        int dir = rng.nextInt(4);
        String dirName;
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

        dirName = switch (dir) {
            case DIR_NORTH -> "north";
            case DIR_SOUTH -> "south";
            case DIR_EAST -> "east";
            default -> "west";
        };
        ctx.message("§b❄ A freezing gust blows " + dirName + "!");
    }
}
