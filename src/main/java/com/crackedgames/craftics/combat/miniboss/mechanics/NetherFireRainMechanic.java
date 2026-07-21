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
 * The Nether Wastes biome's level-4 miniboss: the Fire Rain encounter. Opens with 3 piglin
 * brutes, ordinal-scaled exactly like {@link DesertSandstormMechanic}'s husks and
 * {@link SnowyBlizzardMechanic}'s creepers. The signature hazard is a literal rain of embers:
 * every round, 3-4 random tiles across the arena are painted with temporary
 * {@link TileType#EMBER}, which is already walkable and carries {@code damageOnStep} - stepping
 * on a freshly-rained ember tile burns whoever crosses it, no extra effect code required.
 *
 * <p>Design note: unlike {@link SnowyBlizzardMechanic}'s directional gust (confined to a
 * "downwind" half of the arena) this is a uniform rain - embers can land anywhere except the
 * player-start tile, which mirrors how {@code initialSpawns} keeps that tile clear of enemies too.
 * If an enemy happens to be standing on a tile that gets painted, it is left as-is (enemies burn
 * on the hazard too, which is a fine and even thematic outcome) - this never targets the player
 * directly, since {@link MinibossContext} exposes no primitive to reach the player/party.
 *
 * <p>No extra win objective - clearing the piglin brutes (and any future adds) completes the
 * fight, so {@link #isComplete} uses the interface default (always true).
 */
public final class NetherFireRainMechanic implements MinibossMechanic {

    private static final int BRUTE_COUNT = 3;
    private static final int EMBER_TILES_MIN = 3;
    private static final int EMBER_TILES_MAX = 4; // inclusive
    private static final int EMBER_DURATION = 3; // rounds the ember patch lingers

    @Override
    public String biomeId() {
        return "nether_wastes";
    }

    @Override
    public String introTitle() {
        return "§c§l☠ Fire Rain";
    }

    @Override
    public List<LevelDefinition.EnemySpawn> initialSpawns(int width, int height, int ordinal, Random rng) {
        int hpBonus = ordinal * com.crackedgames.craftics.CrafticsMod.CONFIG.hpPerBiome();
        int atkBonus = ordinal / Math.max(1, com.crackedgames.craftics.CrafticsMod.CONFIG.atkPerBiome());

        List<LevelDefinition.EnemySpawn> spawns = new ArrayList<>();
        List<GridPos> used = new ArrayList<>();
        used.add(new GridPos(width / 2, 0)); // player start - never spawn here

        for (int i = 0; i < BRUTE_COUNT; i++) {
            GridPos pos = MinibossSpawns.findOpen(width, height, used, rng);
            if (pos == null) continue;
            used.add(pos);
            spawns.add(new LevelDefinition.EnemySpawn("minecraft:piglin_brute", pos,
                20 + hpBonus, 6 + atkBonus, 1, 1));
        }

        return spawns;
    }

    @Override
    public void onFightStart(MinibossContext ctx) {
        ctx.banner(introTitle());
        ctx.playSound(SoundEvents.BLOCK_FIRE_AMBIENT, 0.6f, 0.9f);
    }

    @Override
    public void onRoundStart(MinibossContext ctx) {
        rainEmbers(ctx);
    }

    /**
     * Paints a scatter of temporary EMBER tiles across the whole arena every round, avoiding the
     * player-start tile. EMBER is already walkable + damageOnStep per {@link TileType}, so no
     * extra burn logic is needed here - the tile type itself is the hazard.
     */
    private void rainEmbers(MinibossContext ctx) {
        GridArena arena = ctx.arena();
        int width = arena.getWidth();
        int height = arena.getHeight();
        Random rng = ctx.rng();

        GridPos playerStart = new GridPos(width / 2, 0);
        int tileCount = EMBER_TILES_MIN + rng.nextInt(EMBER_TILES_MAX - EMBER_TILES_MIN + 1);

        for (int i = 0; i < tileCount; i++) {
            GridPos pos = null;
            for (int attempts = 0; attempts < 20; attempts++) {
                int x = rng.nextInt(Math.max(1, width));
                int z = rng.nextInt(Math.max(1, height));
                GridPos candidate = new GridPos(x, z);
                if (!candidate.equals(playerStart)) {
                    pos = candidate;
                    break;
                }
            }
            if (pos == null) continue;
            ctx.placeTemporaryTile(pos, TileType.EMBER, EMBER_DURATION);
            ctx.spawnTileParticle(ParticleTypes.FLAME, pos, 6, 0.3, 0.02);
            ctx.spawnHazardBurst(ParticleTypes.LAVA, pos);
        }

        ctx.message("§c☄ Embers rain down!");
        ctx.playSound(SoundEvents.BLOCK_FIRE_AMBIENT, 0.6f, 0.7f);
    }
}
