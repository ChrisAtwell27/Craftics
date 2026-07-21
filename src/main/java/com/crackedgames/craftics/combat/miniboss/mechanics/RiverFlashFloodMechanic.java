package com.crackedgames.craftics.combat.miniboss.mechanics;

import com.crackedgames.craftics.combat.miniboss.MinibossContext;
import com.crackedgames.craftics.combat.miniboss.MinibossMechanic;
import com.crackedgames.craftics.combat.miniboss.MinibossSpawns;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;
import com.crackedgames.craftics.core.TileType;
import com.crackedgames.craftics.level.LevelDefinition;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundEvents;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The river biome's level-4 miniboss: the Flash Flood encounter. Opens with 3 drowned,
 * ordinal-scaled exactly like {@link DesertSandstormMechanic}'s husks. The signature hazard is
 * rising water: every other round, the entire row farthest from the player start that hasn't
 * already been flooded is converted to {@link TileType#WATER}, advancing one row closer to the
 * player start each cycle. WATER already soaks non-flying entities that step onto it (existing
 * arena behavior), so this mechanic only needs to place the tiles - no extra per-tile effect
 * code is required.
 *
 * <p>Flooding stops before reaching the player-start rows (row 0 and row 1) so the spawn point
 * is never drowned out from under the party.
 *
 * <p>No extra win objective - clearing the drowned (and any future adds) completes the fight, so
 * {@link #isComplete} uses the interface default (always true); the flood is pure pressure to
 * force the fight to resolve before the arena runs out of dry ground.
 */
public final class RiverFlashFloodMechanic implements MinibossMechanic {

    private static final int DROWNED_COUNT = 3;
    private static final int FLOOD_DURATION = 99; // rounds - effectively permanent for the fight

    /** How many rows have been flooded so far, counting inward from height-1. */
    private int floodedRows = 0;

    @Override
    public String biomeId() {
        return "river";
    }

    @Override
    public String introTitle() {
        return "§9§l☠ Flash Flood";
    }

    @Override
    public List<LevelDefinition.EnemySpawn> initialSpawns(int width, int height, int ordinal, Random rng) {
        int hpBonus = ordinal * com.crackedgames.craftics.CrafticsMod.CONFIG.hpPerBiome();
        int atkBonus = ordinal / Math.max(1, com.crackedgames.craftics.CrafticsMod.CONFIG.atkPerBiome());

        List<LevelDefinition.EnemySpawn> spawns = new ArrayList<>();
        List<GridPos> used = new ArrayList<>();
        used.add(new GridPos(width / 2, 0)); // player start - never spawn here

        for (int i = 0; i < DROWNED_COUNT; i++) {
            GridPos pos = MinibossSpawns.findOpen(width, height, used, rng);
            if (pos == null) continue;
            used.add(pos);
            spawns.add(new LevelDefinition.EnemySpawn("minecraft:drowned", pos,
                14 + hpBonus, 4 + atkBonus, 0, 1));
        }

        return spawns;
    }

    @Override
    public void onFightStart(MinibossContext ctx) {
        floodedRows = 0;
        ctx.banner(introTitle());
        ctx.playSound(SoundEvents.BLOCK_WATER_AMBIENT, 0.6f, 1.0f);
    }

    @Override
    public void onRoundStart(MinibossContext ctx) {
        if (ctx.round() % 2 != 0) return; // rises every 2nd round

        GridArena arena = ctx.arena();
        int height = arena.getHeight();
        int width = arena.getWidth();

        int row = height - 1 - floodedRows;
        if (row <= 1) return; // stop before drowning the player-start row

        for (int x = 0; x < width; x++) {
            GridPos floodTile = new GridPos(x, row);
            ctx.placeTemporaryTile(floodTile, TileType.WATER, FLOOD_DURATION);
            // Signature hazard - make the rising water read: splashing surface plus a
            // falling-water burst on every newly flooded tile.
            ctx.spawnTileParticle(ParticleTypes.SPLASH, floodTile, 6, 0.3, 0.05);
            ctx.spawnHazardBurst(ParticleTypes.FALLING_WATER, floodTile);
        }
        floodedRows++;
        ctx.message("§9The water rises!");
        ctx.playSound(SoundEvents.ENTITY_GENERIC_SPLASH, 0.7f, 0.8f);
    }
}
