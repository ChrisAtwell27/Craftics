package com.crackedgames.craftics.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.BlockPos;

import java.util.Random;

/**
 * Ambient particle life inside an arena: the biome's own weather drifting through the board, heat
 * shimmer off lava, wisps rising out of void pits, and dust kicked up where a combatant steps.
 *
 * <p>All of it is client-side and derived from state the client already has - the arena bounds,
 * the local biome, and the block actually sitting on each tile - so nothing is synced and a
 * server never spends a tick on it. Everything is capped per tick and skipped entirely when the
 * player has particles turned down, so a dense arena can't drown the frame rate.
 *
 * <p>Weather here is about READING the arena, not decoration: falling snow marks a cold board
 * where blizzard mechanics fire, spores mark the fungal biomes, and the shimmer/wisps mark the
 * two tile types that can kill you outright.
 */
public final class ArenaAmbientFx {

    private ArenaAmbientFx() {}

    /** Height above the floor that weather particles spawn at. */
    private static final int WEATHER_HEIGHT = 9;
    /** Weather particles attempted per tick across the whole board. */
    private static final int WEATHER_PER_TICK = 3;
    /** Hazard tiles sampled per tick - the board is walked in slices, not all at once. */
    private static final int HAZARD_SAMPLES_PER_TICK = 6;
    /** Board margin the weather covers past the tile grid, so it doesn't stop at a hard line. */
    private static final int WEATHER_MARGIN = 6;

    private static final Random RNG = new Random();

    /** Which ambient family this arena's biome belongs to. */
    private enum Weather { NONE, SNOW, DUST, RAIN, SPORE_WARM, SPORE_COLD, ASH, PETALS, GLOOM }

    private static Weather cachedWeather = Weather.NONE;
    private static long cachedWeatherKey = Long.MIN_VALUE;

    /** Advance one client tick's worth of ambience. Safe to call every tick, in or out of combat. */
    public static void tick(MinecraftClient mc) {
        if (mc == null || mc.world == null || mc.player == null) return;
        if (!CombatState.isInCombat() && !CombatState.isInScene()) return;
        if (com.crackedgames.craftics.client.vfx.HitPauseState.isFrozen()) return;
        int w = CombatState.getArenaWidth();
        int h = CombatState.getArenaHeight();
        if (w <= 0 || h <= 0) return;

        int ox = CombatState.getArenaOriginX();
        int oy = CombatState.getArenaOriginY();
        int oz = CombatState.getArenaOriginZ();

        spawnWeather(mc, ox, oy, oz, w, h);
        spawnHazardFx(mc, ox, oy, oz, w, h);
    }

    /** Biome weather drifting down through the board. */
    private static void spawnWeather(MinecraftClient mc, int ox, int oy, int oz, int w, int h) {
        Weather weather = weatherFor(mc, ox, oy, oz, w, h);
        if (weather == Weather.NONE) return;

        for (int i = 0; i < WEATHER_PER_TICK; i++) {
            double x = ox - WEATHER_MARGIN + RNG.nextDouble() * (w + WEATHER_MARGIN * 2);
            double z = oz - WEATHER_MARGIN + RNG.nextDouble() * (h + WEATHER_MARGIN * 2);
            double y = oy + 2 + RNG.nextDouble() * WEATHER_HEIGHT;
            ParticleEffect effect = effectFor(weather);
            if (effect == null) continue;
            // Drift is horizontal only; these particle types fall under their own gravity, and a
            // downward velocity on top of that reads as sleet rather than weather.
            spawn(mc, effect, x, y, z,
                (RNG.nextDouble() - 0.5) * 0.02, 0.0, (RNG.nextDouble() - 0.5) * 0.02);
        }
    }

    /**
     * Hazard tells: heat shimmer over lava, pale wisps climbing out of void holes, and a sculk
     * pulse over sensor-field tiles. Sampled at random rather than swept, so cost is fixed no
     * matter how big the arena is.
     */
    private static void spawnHazardFx(MinecraftClient mc, int ox, int oy, int oz, int w, int h) {
        for (int i = 0; i < HAZARD_SAMPLES_PER_TICK; i++) {
            int tx = RNG.nextInt(w);
            int tz = RNG.nextInt(h);
            BlockPos floor = new BlockPos(ox + tx, oy, oz + tz);
            var state = mc.world.getBlockState(floor);

            if (state.isOf(net.minecraft.block.Blocks.LAVA)
                    || state.isOf(net.minecraft.block.Blocks.MAGMA_BLOCK)) {
                // Rising heat, not flame: the tile already glows, what it needs is the column of
                // distortion that says "do not walk here".
                spawn(mc, ParticleTypes.SMOKE,
                    floor.getX() + RNG.nextDouble(), floor.getY() + 1.05,
                    floor.getZ() + RNG.nextDouble(),
                    0.0, 0.04 + RNG.nextDouble() * 0.03, 0.0);
                continue;
            }
            if (state.isOf(net.minecraft.block.Blocks.SCULK)) {
                spawn(mc, ParticleTypes.SCULK_SOUL,
                    floor.getX() + RNG.nextDouble(), floor.getY() + 1.1,
                    floor.getZ() + RNG.nextDouble(),
                    0.0, 0.01, 0.0);
                continue;
            }
            // A void tile is air at the floor with nothing standable below it (the same test the
            // tile scan uses). Wisps climb out of it so a lethal hole never reads as flat shadow.
            if (state.isAir()) {
                BlockPos below = floor.down();
                boolean standable = com.crackedgames.craftics.combat.WallBlocks
                    .providesStandingSurface(mc.world.getBlockState(below), mc.world, below);
                if (!standable) {
                    spawn(mc, ParticleTypes.CLOUD,
                        floor.getX() + 0.2 + RNG.nextDouble() * 0.6, floor.getY() - 0.4,
                        floor.getZ() + 0.2 + RNG.nextDouble() * 0.6,
                        0.0, 0.05 + RNG.nextDouble() * 0.04, 0.0);
                }
            }
        }
    }

    /**
     * Dust kicked up where something lands on a tile. Called from the move event so it fires for
     * enemies and allies too, not just the local player.
     */
    public static void stepDust(MinecraftClient mc, int tileX, int tileZ) {
        if (mc == null || mc.world == null || !CombatState.isInCombat()) return;
        double x = CombatState.getArenaOriginX() + tileX + 0.5;
        double z = CombatState.getArenaOriginZ() + tileZ + 0.5;
        double y = CombatState.getArenaOriginY() + 1.05;
        for (int i = 0; i < 5; i++) {
            double angle = RNG.nextDouble() * Math.PI * 2;
            double speed = 0.03 + RNG.nextDouble() * 0.04;
            spawn(mc, ParticleTypes.POOF,
                x + (RNG.nextDouble() - 0.5) * 0.6, y, z + (RNG.nextDouble() - 0.5) * 0.6,
                Math.cos(angle) * speed, 0.02, Math.sin(angle) * speed);
        }
    }

    /** Which weather family the arena's biome calls for. Cached per arena. */
    private static Weather weatherFor(MinecraftClient mc, int ox, int oy, int oz, int w, int h) {
        long key = (((long) ox) << 40) ^ (((long) oz) << 16) ^ (w * 31L + h);
        if (key == cachedWeatherKey) return applyRain(mc, cachedWeather);

        String biome = mc.world.getBiome(new BlockPos(ox + w / 2, oy + 1, oz + h / 2)).getKey()
            .map(k -> k.getValue().getPath())
            .orElse("");
        Weather weather;
        if (biome.contains("snowy") || biome.contains("frozen") || biome.contains("ice")
            || biome.contains("peaks") || biome.contains("grove")) {
            weather = Weather.SNOW;
        } else if (biome.contains("desert") || biome.contains("badlands")
            || biome.contains("savanna")) {
            weather = Weather.DUST;
        } else if (biome.contains("cherry")) {
            weather = Weather.PETALS;
        } else if (biome.contains("crimson")) {
            weather = Weather.SPORE_WARM;
        } else if (biome.contains("warped")) {
            weather = Weather.SPORE_COLD;
        } else if (biome.contains("basalt") || biome.contains("soul_sand")
            || biome.contains("nether")) {
            weather = Weather.ASH;
        } else if (biome.contains("dark_forest") || biome.contains("deep_dark")) {
            weather = Weather.GLOOM;
        } else if (biome.contains("jungle") || biome.contains("swamp")
            || biome.contains("mangrove")) {
            weather = Weather.RAIN;   // these arenas read as humid whether or not it's raining
        } else {
            weather = Weather.NONE;
        }
        cachedWeather = weather;
        cachedWeatherKey = key;
        return applyRain(mc, weather);
    }

    /** Real weather overrides a dry biome: rain falls on plains too. Snow biomes stay snowing. */
    private static Weather applyRain(MinecraftClient mc, Weather base) {
        if (!mc.world.isRaining()) return base;
        if (base == Weather.SNOW || base == Weather.ASH) return base;
        return Weather.RAIN;
    }

    private static ParticleEffect effectFor(Weather weather) {
        return switch (weather) {
            case SNOW -> ParticleTypes.SNOWFLAKE;
            case DUST -> new BlockStateParticleEffect(ParticleTypes.FALLING_DUST,
                net.minecraft.block.Blocks.SAND.getDefaultState());
            case RAIN -> ParticleTypes.RAIN;
            case SPORE_WARM -> ParticleTypes.CRIMSON_SPORE;
            case SPORE_COLD -> ParticleTypes.WARPED_SPORE;
            case ASH -> ParticleTypes.ASH;
            case PETALS -> ParticleTypes.CHERRY_LEAVES;
            case GLOOM -> ParticleTypes.MYCELIUM;
            case NONE -> null;
        };
    }

    /**
     * Spawn one particle on the client world.
     *
     * <p>Shard split: 1.21.5 renamed {@code World.addParticle} to {@code addParticleClient} when
     * the client/server particle paths were separated. Same arguments, same behaviour - routing
     * every spawn through here keeps that rename in one place.
     */
    private static void spawn(MinecraftClient mc, ParticleEffect effect,
                              double x, double y, double z, double vx, double vy, double vz) {
        //? if <=1.21.4 {
        mc.world.addParticle(effect, x, y, z, vx, vy, vz);
        //?} else {
        /*mc.world.addParticleClient(effect, x, y, z, vx, vy, vz);
        *///?}
    }

    /** Drop the cached biome read (arena change / disconnect). */
    public static void reset() {
        cachedWeatherKey = Long.MIN_VALUE;
        cachedWeather = Weather.NONE;
    }
}
