package com.crackedgames.craftics.combat.miniboss.mechanics;

import com.crackedgames.craftics.combat.CombatEntity;
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
 * The Chorus Grove biome's level-4 miniboss: the Chorus Bloom encounter. Opens with 3 endermen,
 * ordinal-scaled exactly like {@link CrimsonFungalBloomMechanic}'s hoglins. The signature hazard
 * is spreading chorus growth: a couple of {@link TileType#SPORE} tiles (reused here as "chorus
 * growth" - walkable, thematic) are seeded at fight start, and each round every existing chorus
 * tile has a chance to bloom onto one of its (cardinally adjacent, in-bounds, not-yet-bloomed)
 * neighbors, so the grove creeps wider over the course of the fight.
 *
 * <p>Design note: the intended version - teleporting anyone who ends a turn adjacent to a chorus
 * tile, mirroring chorus fruit's real disorientation gimmick - needs entity-relocation and
 * player-reach primitives {@link MinibossContext} does not expose (see {@link
 * DesertSandstormMechanic}'s javadoc for the same class of limitation). Rather than invent those
 * primitives, this implements the achievable equivalent: the chorus growth itself is the hazard/
 * texture (clear the endermen before the grove is fully overgrown), and Confusion is applied as
 * optional flavor to any living enemy standing on a chorus tile at the start of each round -
 * {@link CombatEntity#stackConfusion} exists and only touches enemies, so this stays within what
 * the context actually supports without reaching for the player. Growth is capped at roughly a
 * third of the arena's tiles, mirroring {@link CrimsonFungalBloomMechanic} exactly.
 *
 * <p>No extra win objective - clearing the endermen (and any future adds) completes the fight, so
 * {@link #isComplete} uses the interface default (always true).
 */
public final class ChorusGroveBloomMechanic implements MinibossMechanic {

    private static final int ENDERMAN_COUNT = 3;
    private static final int SPORE_DURATION = 99; // rounds - effectively permanent for the fight
    private static final double SPREAD_CHANCE = 0.4; // per-tile chance to bloom a neighbor each round
    private static final int CONFUSION_TURNS = 2;
    private static final int CONFUSION_AMP_INCREASE = 0;

    /** All tiles currently covered in chorus growth. */
    private final List<GridPos> chorusTiles = new ArrayList<>();

    @Override
    public String biomeId() {
        return "chorus_grove";
    }

    @Override
    public String introTitle() {
        return "§d§l☠ Chorus Bloom";
    }

    @Override
    public List<LevelDefinition.EnemySpawn> initialSpawns(int width, int height, int ordinal, Random rng) {
        int hpBonus = ordinal * com.crackedgames.craftics.CrafticsMod.CONFIG.hpPerBiome();
        int atkBonus = ordinal / Math.max(1, com.crackedgames.craftics.CrafticsMod.CONFIG.atkPerBiome());

        List<LevelDefinition.EnemySpawn> spawns = new ArrayList<>();
        List<GridPos> used = new ArrayList<>();
        used.add(new GridPos(width / 2, 0)); // player start - never spawn here

        for (int i = 0; i < ENDERMAN_COUNT; i++) {
            GridPos pos = MinibossSpawns.findOpen(width, height, used, rng);
            if (pos == null) continue;
            used.add(pos);
            spawns.add(new LevelDefinition.EnemySpawn("minecraft:enderman", pos,
                30 + hpBonus, 7 + atkBonus, 0, 1));
        }

        return spawns;
    }

    @Override
    public void onFightStart(MinibossContext ctx) {
        chorusTiles.clear();
        ctx.banner(introTitle());
        ctx.playSound(SoundEvents.BLOCK_CHORUS_FLOWER_GROW, 0.6f, 0.9f);

        GridArena arena = ctx.arena();
        GridPos playerStart = arena.getPlayerStart();
        int seedCount = 2 + ctx.rng().nextInt(2); // 2-3 seed tiles

        for (int i = 0; i < seedCount; i++) {
            GridPos pos = findSeedTile(arena, playerStart, chorusTiles, ctx.rng());
            if (pos == null) continue;
            ctx.placeTemporaryTile(pos, TileType.SPORE, SPORE_DURATION);
            ctx.spawnTileParticle(ParticleTypes.PORTAL, pos, 5, 0.3, 0.02);
            chorusTiles.add(pos);
        }
    }

    @Override
    public void onRoundStart(MinibossContext ctx) {
        GridArena arena = ctx.arena();
        int width = arena.getWidth();
        int height = arena.getHeight();
        int cap = Math.max(1, (width * height) / 3);

        if (chorusTiles.size() < cap) {
            GridPos playerStart = arena.getPlayerStart();
            List<GridPos> newlyBloomed = new ArrayList<>();

            // Snapshot the current chorus tiles so newly-added ones this round don't
            // immediately spread again in the same pass.
            List<GridPos> existing = new ArrayList<>(chorusTiles);
            for (GridPos spore : existing) {
                if (chorusTiles.size() + newlyBloomed.size() >= cap) break;
                if (ctx.rng().nextDouble() >= SPREAD_CHANCE) continue;

                GridPos next = pickSpreadTarget(arena, spore, playerStart, chorusTiles, newlyBloomed, ctx.rng());
                if (next == null) continue;

                ctx.placeTemporaryTile(next, TileType.SPORE, SPORE_DURATION);
                ctx.spawnTileParticle(ParticleTypes.PORTAL, next, 5, 0.3, 0.02);
                newlyBloomed.add(next);
            }

            if (!newlyBloomed.isEmpty()) {
                chorusTiles.addAll(newlyBloomed);
                ctx.message("§dThe chorus blooms wider...");
                ctx.playSound(SoundEvents.ENTITY_ENDERMAN_TELEPORT, 0.5f, 1.0f);
            }
        }

        applyConfusionToOccupants(ctx);
    }

    /**
     * Optional flavor: any living enemy currently standing on a chorus tile gets a short
     * Confusion stack refreshed. Purely cosmetic disorientation theme - skipped entirely for
     * any enemy without a grid position, and safe to no-op if no enemy is on a chorus tile.
     */
    private void applyConfusionToOccupants(MinibossContext ctx) {
        if (chorusTiles.isEmpty()) return;
        int confused = 0;
        for (CombatEntity e : ctx.enemies()) {
            if (!e.isAlive()) continue;
            GridPos pos = e.getGridPos();
            if (pos == null) continue;
            if (chorusTiles.contains(pos)) {
                e.stackConfusion(CONFUSION_TURNS, CONFUSION_AMP_INCREASE);
                ctx.spawnHazardBurst(ParticleTypes.WITCH, pos);
                confused++;
            }
        }
        // One aggregated cue per round (not per enemy) so the confusion is legible without
        // spamming the log when several enemies stand in the grove at once.
        if (confused > 0) {
            ctx.message(confused == 1
                ? "§dThe chorus scrambles an enemy's senses - Confused!"
                : "§dThe chorus scrambles " + confused + " enemies' senses - Confused!");
            ctx.playSound(SoundEvents.ENTITY_ENDERMAN_TELEPORT, 0.4f, 1.4f);
        }
    }

    /**
     * Picks a random cardinally-adjacent, in-bounds tile to {@code from} that isn't already
     * bloomed (in either the tracked list or this round's newly-bloomed batch) and isn't the
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
     * Finds a random in-bounds tile for an initial chorus seed, avoiding the player start and
     * any tile already claimed this fight. Mirrors the retry-loop shape of {@code
     * findOpenSpawn} used elsewhere, but samples the full arena rather than a spawn-safe
     * sub-rectangle since chorus tiles are purely cosmetic/pathing hazards.
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
