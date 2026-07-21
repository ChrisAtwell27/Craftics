package com.crackedgames.craftics.combat.biomeeffect.effects;

import com.crackedgames.craftics.combat.biomeeffect.BiomeEffect;
import com.crackedgames.craftics.combat.miniboss.MinibossContext;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;
import com.crackedgames.craftics.core.GridTile;
import com.crackedgames.craftics.core.TileType;
import net.minecraft.block.Blocks;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;

import java.util.Random;

/**
 * The jungle biome's persistent weather layer: a steady rain that churns a handful of grass/dirt
 * tiles into mud every round for the rest of the fight. Mud lasts the whole encounter (the arena
 * rebuild on the next level naturally resets it), so the puddle spreads and compounds as the
 * fight goes on.
 */
public final class JungleRainEffect implements BiomeEffect {

    private static final int TILES_MIN = 2;
    private static final int TILES_MAX = 3; // inclusive
    private static final int MUD_DURATION = 99; // effectively "lasts the fight"
    private static final int PLACEMENT_ATTEMPTS = 10; // per tile, before giving up on this round

    // --- Continuous ambience (onCombatTick) ---
    private static final int PARTICLE_CADENCE = 2; // scatter rain drops every 2 ticks (~10/sec)
    private static final int DROPS_MIN = 6;
    private static final int DROPS_MAX = 10; // inclusive
    private static final int SOUND_CADENCE = 40; // soft rain loop every 2 seconds

    @Override
    public String id() {
        return "jungle_rain";
    }

    private static final double MUD_PATCH_CHANCE = 0.5; // chance a level opens with a mud patch
    private static final int MUD_PATCH_DURATION = 999;   // effectively permanent - the standing bog

    @Override
    public void onFightStart(MinibossContext ctx) {
        ctx.message("§9☔ Rain begins to fall - the ground turns to mud.");
        // A jungle level has a chance to open with a standing bog: an irregular ~3x3 mud patch
        // in a random spot (not on the player start). Distinct from the rain's per-round mud.
        if (ctx.rng().nextDouble() < MUD_PATCH_CHANCE) {
            spawnMudPatch(ctx);
        }
    }

    /** Place an irregular ~3x3 mud patch centered on a random tile (5-8 of the 9 cells, so the
     *  edge is ragged rather than a clean square). Skips the player-start tile. */
    private void spawnMudPatch(MinibossContext ctx) {
        GridArena arena = ctx.arena();
        int width = arena.getWidth();
        int height = arena.getHeight();
        Random rng = ctx.rng();
        GridPos playerStart = new GridPos(width / 2, 0);
        // Center in the arena interior so the full 3x3 mostly fits.
        int cx = 2 + rng.nextInt(Math.max(1, width - 4));
        int cz = 3 + rng.nextInt(Math.max(1, height - 5));
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                // Ragged edge: the center + orthogonal always mud, the 4 corners 50/50.
                boolean corner = dx != 0 && dz != 0;
                if (corner && rng.nextBoolean()) continue;
                GridPos p = new GridPos(cx + dx, cz + dz);
                if (!arena.isInBounds(p) || p.equals(playerStart)) continue;
                GridTile t = arena.getTile(p);
                if (t == null || !t.isWalkable()) continue;
                ctx.placeTemporaryTile(p, TileType.MUD, MUD_PATCH_DURATION);
            }
        }
    }

    @Override
    public void onRoundStart(MinibossContext ctx) {
        GridArena arena = ctx.arena();
        int width = arena.getWidth();
        int height = arena.getHeight();
        Random rng = ctx.rng();
        GridPos playerStart = new GridPos(width / 2, 0);

        int target = TILES_MIN + rng.nextInt(TILES_MAX - TILES_MIN + 1);
        int converted = 0;
        for (int i = 0; i < target; i++) {
            GridPos pos = findMuddableTile(arena, width, height, playerStart, rng);
            if (pos == null) continue;
            ctx.placeTemporaryTile(pos, TileType.MUD, MUD_DURATION);
            converted++;
        }

        if (converted > 0) {
            ctx.message("§6The rain churns the ground to mud.");
        }
    }

    /**
     * Continuous rain ambience: a light scatter of falling-water drops across the arena every
     * couple of ticks (reads as steady rainfall without spamming particles every tick), plus a
     * soft looping rain sound every couple seconds. {@code RAIN} is a splash-only client
     * particle in places, so this uses {@code FALLING_WATER} for the falling drop and a ground
     * {@code SPLASH} - the same pairing CombatManager already uses for water impacts (e.g. the
     * riptide dash VFX), so both are known-good server-spawnable particles on every shard.
     */
    @Override
    public void onCombatTick(MinibossContext ctx, int tick) {
        if (tick % PARTICLE_CADENCE == 0) {
            GridArena arena = ctx.arena();
            int width = arena.getWidth();
            int height = arena.getHeight();
            Random rng = ctx.rng();
            BlockPos origin = arena.getOrigin();
            int drops = DROPS_MIN + rng.nextInt(DROPS_MAX - DROPS_MIN + 1);
            for (int i = 0; i < drops; i++) {
                int x = rng.nextInt(Math.max(1, width));
                int z = rng.nextInt(Math.max(1, height));
                GridPos pos = new GridPos(x, z);
                if (!arena.isInBounds(pos)) continue;
                BlockPos ground = arena.gridToBlockPos(pos);
                double wx = ground.getX() + 0.5;
                double wz = ground.getZ() + 0.5;
                double dropY = origin.getY() + 4 + rng.nextInt(3); // +4 to +6 above the floor
                ctx.spawnAmbientParticle(ParticleTypes.FALLING_WATER, wx, dropY, wz,
                    1, 0.3, 0.1, 0.3, 0.05);
                ctx.spawnAmbientParticle(ParticleTypes.SPLASH, wx, ground.getY(), wz,
                    1, 0.2, 0.05, 0.2, 0.02);
            }
        }

        if (tick % SOUND_CADENCE == 0) {
            ctx.playSound(SoundEvents.WEATHER_RAIN, 0.4f, 1.0f);
        }
    }

    /** Picks a random tile whose LIVE block is grass or dirt (not already mud/other), a few
     *  attempts at a time, skipping the player-start tile. Null if nothing found in the budget. */
    private static GridPos findMuddableTile(GridArena arena, int width, int height,
                                             GridPos playerStart, Random rng) {
        for (int attempt = 0; attempt < PLACEMENT_ATTEMPTS; attempt++) {
            int x = rng.nextInt(Math.max(1, width));
            int z = rng.nextInt(Math.max(1, height));
            GridPos pos = new GridPos(x, z);
            if (pos.equals(playerStart)) continue;

            GridTile tile = arena.getTile(pos);
            if (tile == null) continue;
            var block = tile.getBlockType();
            // The jungle floor is grass + moss; rain churns those (and plain dirt) into mud.
            // The already-mud tiles are excluded so the rain only converts fresh ground.
            if (block == Blocks.GRASS_BLOCK || block == Blocks.DIRT || block == Blocks.MOSS_BLOCK) {
                return pos;
            }
        }
        return null;
    }
}
