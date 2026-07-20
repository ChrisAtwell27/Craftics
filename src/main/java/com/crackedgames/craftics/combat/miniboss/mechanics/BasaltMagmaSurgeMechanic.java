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
 * The Basalt Deltas biome's level-4 miniboss: the Magma Surge encounter. Opens with 3 magma
 * cubes, ordinal-scaled exactly like {@link DesertSandstormMechanic}'s husks. The signature
 * hazard is a telegraphed vent eruption: one round it announces a scattered set of 3-4 tiles
 * about to erupt, the next round those tiles become temporary {@link TileType#LAVA} (which
 * already carries {@code damageOnStep = 10}, so no extra damage/effect code is needed here),
 * then the cycle re-telegraphs a fresh set on the following even round.
 *
 * <p>Mirrors {@link DesertSandstormMechanic}'s pendingRow/telegraph pattern, but swaps the
 * "whole lane" hazard shape for a scattered set of individual tiles ({@code pendingVents}),
 * matching the vent-eruption fantasy instead of a sweeping sandstorm.
 *
 * <p>No extra win objective - clearing the magma cubes (and any splits they spawn on death,
 * which is existing vanilla behavior and still counts toward the clear) completes the fight,
 * so {@link #isComplete} uses the interface default (always true).
 */
public final class BasaltMagmaSurgeMechanic implements MinibossMechanic {

    private static final int MAGMA_CUBE_COUNT = 3;
    private static final int LAVA_DURATION = 3; // rounds the erupted vent tiles stay as lava
    private static final int MIN_VENTS = 3;
    private static final int MAX_VENTS = 4; // inclusive

    /** Tiles telegraphed last round, waiting to erupt into lava this round. Empty when idle. */
    private final List<GridPos> pendingVents = new ArrayList<>();

    @Override
    public String biomeId() {
        return "basalt_deltas";
    }

    @Override
    public String introTitle() {
        return "§6§l☠ Magma Surge";
    }

    @Override
    public List<LevelDefinition.EnemySpawn> initialSpawns(int width, int height, int ordinal, Random rng) {
        int hpBonus = ordinal * com.crackedgames.craftics.CrafticsMod.CONFIG.hpPerBiome();
        int atkBonus = ordinal / Math.max(1, com.crackedgames.craftics.CrafticsMod.CONFIG.atkPerBiome());

        List<LevelDefinition.EnemySpawn> spawns = new ArrayList<>();
        List<GridPos> used = new ArrayList<>();
        used.add(new GridPos(width / 2, 0)); // player start - never spawn here

        for (int i = 0; i < MAGMA_CUBE_COUNT; i++) {
            GridPos pos = MinibossSpawns.findOpen(width, height, used, rng);
            if (pos == null) continue;
            used.add(pos);
            spawns.add(new LevelDefinition.EnemySpawn("minecraft:magma_cube", pos,
                16 + hpBonus, 5 + atkBonus, 0, 1));
        }

        return spawns;
    }

    @Override
    public void onFightStart(MinibossContext ctx) {
        pendingVents.clear();
        ctx.banner(introTitle());
    }

    @Override
    public void onRoundStart(MinibossContext ctx) {
        // Resolve vents telegraphed last round by erupting them into lava.
        if (!pendingVents.isEmpty()) {
            for (GridPos p : pendingVents) {
                ctx.placeTemporaryTile(p, TileType.LAVA, LAVA_DURATION);
            }
            pendingVents.clear();
            ctx.message("§6Lava erupts!");
        }

        // Telegraph the next eruption on even rounds when nothing is currently pending, so
        // telegraph (round N) and resolve (round N+1) alternate cleanly without overlapping.
        if (ctx.round() % 2 == 0 && pendingVents.isEmpty()) {
            GridArena arena = ctx.arena();
            List<GridPos> avoid = new ArrayList<>();
            avoid.add(new GridPos(arena.getWidth() / 2, 0)); // player start

            int ventCount = MIN_VENTS + ctx.rng().nextInt(MAX_VENTS - MIN_VENTS + 1);
            for (int i = 0; i < ventCount; i++) {
                GridPos pos = MinibossSpawns.findOpen(arena.getWidth(), arena.getHeight(), avoid, ctx.rng());
                if (pos == null) continue;
                avoid.add(pos);
                pendingVents.add(pos);
            }
            ctx.message("§6The ground glows - vents about to erupt!");
        }
    }
}
