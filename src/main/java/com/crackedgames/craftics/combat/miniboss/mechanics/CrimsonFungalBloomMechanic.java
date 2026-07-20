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
 * The Crimson Forest biome's level-4 miniboss: the Fungal Bloom encounter. Opens with 3 hoglins,
 * ordinal-scaled exactly like {@link DesertSandstormMechanic}'s husks and {@link
 * RiverFlashFloodMechanic}'s drowned. The signature hazard is spreading crimson fungus: a couple
 * of {@link TileType#SPORE} tiles are seeded at fight start, and each round every existing spore
 * tile has a chance to bloom onto one of its (cardinally adjacent, in-bounds, not-yet-spored)
 * neighbors, so the infestation creeps outward over the course of the fight.
 *
 * <p>Design note: SPORE is defined as walkable with {@code damageOnStep = 0} - it can't hurt the
 * player on contact the way LAVA/FIRE/EMBER do, so this reads as a soft pathing hazard/texture
 * rather than a damage source. (The tile javadoc mentions "applies Poison on step" as an
 * aspirational note, but {@link MinibossContext} has no primitive to reach the player from a
 * mechanic - see {@link DesertSandstormMechanic}'s javadoc for the same limitation.) The
 * achievable version implemented here is the spread itself: the pressure is "clear the hoglins
 * before the grove is fully overgrown," purely atmospheric. Growth is capped at roughly a third
 * of the arena's tiles so it never fully covers the floor instantly.
 *
 * <p>No extra win objective - clearing the hoglins (and any future adds) completes the fight, so
 * {@link #isComplete} uses the interface default (always true).
 */
public final class CrimsonFungalBloomMechanic implements MinibossMechanic {

    private static final int HOGLIN_COUNT = 3;
    private static final int SPORE_DURATION = 99; // rounds - effectively permanent for the fight
    private static final double SPREAD_CHANCE = 0.4; // per-tile chance to bloom a neighbor each round

    /** All tiles currently covered in spores. */
    private final List<GridPos> sporeTiles = new ArrayList<>();

    @Override
    public String biomeId() {
        return "crimson_forest";
    }

    @Override
    public String introTitle() {
        return "§c§l☠ Fungal Bloom";
    }

    @Override
    public List<LevelDefinition.EnemySpawn> initialSpawns(int width, int height, int ordinal, Random rng) {
        int hpBonus = ordinal * com.crackedgames.craftics.CrafticsMod.CONFIG.hpPerBiome();
        int atkBonus = ordinal / Math.max(1, com.crackedgames.craftics.CrafticsMod.CONFIG.atkPerBiome());

        List<LevelDefinition.EnemySpawn> spawns = new ArrayList<>();
        List<GridPos> used = new ArrayList<>();
        used.add(new GridPos(width / 2, 0)); // player start - never spawn here

        for (int i = 0; i < HOGLIN_COUNT; i++) {
            GridPos pos = MinibossSpawns.findOpen(width, height, used, rng);
            if (pos == null) continue;
            used.add(pos);
            spawns.add(new LevelDefinition.EnemySpawn("minecraft:hoglin", pos,
                22 + hpBonus, 5 + atkBonus, 1, 1));
        }

        return spawns;
    }

    @Override
    public void onFightStart(MinibossContext ctx) {
        sporeTiles.clear();
        ctx.banner(introTitle());

        GridArena arena = ctx.arena();
        GridPos playerStart = arena.getPlayerStart();
        int seedCount = 2 + ctx.rng().nextInt(2); // 2-3 seed tiles

        for (int i = 0; i < seedCount; i++) {
            GridPos pos = findSeedTile(arena, playerStart, sporeTiles, ctx.rng());
            if (pos == null) continue;
            ctx.placeTemporaryTile(pos, TileType.SPORE, SPORE_DURATION);
            sporeTiles.add(pos);
        }
    }

    @Override
    public void onRoundStart(MinibossContext ctx) {
        GridArena arena = ctx.arena();
        int width = arena.getWidth();
        int height = arena.getHeight();
        int cap = Math.max(1, (width * height) / 3);

        if (sporeTiles.size() >= cap) return;

        GridPos playerStart = arena.getPlayerStart();
        List<GridPos> newlyBloomed = new ArrayList<>();

        // Snapshot the current spore tiles so newly-added ones this round don't
        // immediately spread again in the same pass.
        List<GridPos> existing = new ArrayList<>(sporeTiles);
        for (GridPos spore : existing) {
            if (sporeTiles.size() + newlyBloomed.size() >= cap) break;
            if (ctx.rng().nextDouble() >= SPREAD_CHANCE) continue;

            GridPos next = pickSpreadTarget(arena, spore, playerStart, sporeTiles, newlyBloomed, ctx.rng());
            if (next == null) continue;

            ctx.placeTemporaryTile(next, TileType.SPORE, SPORE_DURATION);
            newlyBloomed.add(next);
        }

        if (!newlyBloomed.isEmpty()) {
            sporeTiles.addAll(newlyBloomed);
            ctx.message("§c☣ The spores spread!");
        }
    }

    /**
     * Picks a random cardinally-adjacent, in-bounds tile to {@code from} that isn't already
     * spored (in either the tracked list or this round's newly-bloomed batch) and isn't the
     * player start. Returns {@code null} if every neighbor is unavailable.
     */
    private static GridPos pickSpreadTarget(GridArena arena, GridPos from, GridPos playerStart,
                                             List<GridPos> existing, List<GridPos> newlyBloomed, Random rng) {
        List<GridPos> candidates = new ArrayList<>(4);
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] d : dirs) {
            GridPos cand = new GridPos(from.x() + d[0], from.z() + d[1]);
            if (!arena.isInBounds(cand)) continue;
            if (cand.equals(playerStart)) continue;
            if (existing.contains(cand) || newlyBloomed.contains(cand)) continue;
            candidates.add(cand);
        }
        if (candidates.isEmpty()) return null;
        return candidates.get(rng.nextInt(candidates.size()));
    }

    /**
     * Finds a random in-bounds tile for an initial spore seed, avoiding the player start and
     * any tile already claimed this fight. Mirrors the retry-loop shape of {@code
     * findOpenSpawn} used elsewhere, but samples the full arena rather than a spawn-safe
     * sub-rectangle since spores are purely cosmetic/pathing hazards.
     */
    private static GridPos findSeedTile(GridArena arena, GridPos playerStart, List<GridPos> used, Random rng) {
        int width = arena.getWidth();
        int height = arena.getHeight();
        for (int attempts = 0; attempts < 40; attempts++) {
            int x = rng.nextInt(Math.max(1, width));
            int z = rng.nextInt(Math.max(1, height));
            GridPos pos = new GridPos(x, z);
            if (!arena.isInBounds(pos)) continue;
            if (pos.equals(playerStart)) continue;
            if (used.contains(pos)) continue;
            return pos;
        }
        return null;
    }
}
