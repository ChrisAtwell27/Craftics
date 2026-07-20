package com.crackedgames.craftics.combat.miniboss.mechanics;

import com.crackedgames.craftics.combat.CombatEntity;
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
 * The mountain biome's level-4 miniboss: the Rockbreaker Kin encounter. This is a literal
 * miniboss - one beefy "elite" iron golem, scaled with {@code ordinal} exactly like the other
 * biome elites. No companion add is spawned; the golem's bulk and the falling-rubble hazard are
 * the whole fight.
 *
 * <p>Every 3rd round, 2-3 random tiles across the arena crash down as temporary
 * {@link TileType#RUBBLE} (3-round expiry, matching the other biomes' falling-stone convention).
 * If a living enemy happens to be standing on a chosen tile it gets crushed: it takes
 * {@link #RUBBLE_DAMAGE} direct damage and is stunned. In practice this mostly denies tiles to
 * the player (forcing a reroute around the arena) since {@link MinibossContext} has no primitive
 * to reach the player/party directly - only {@link MinibossContext#enemies()} is exposed - but it
 * can also crush a wandering add, which is an achievable bonus on top of the tile-denial hazard.
 *
 * <p>No extra win objective - the golem is a normal enemy, so killing it completes the fight via
 * the base "all enemies cleared" check. {@link #isComplete} uses the interface default (always
 * true).
 */
public final class MountainRockbreakerMechanic implements MinibossMechanic {

    private static final int RUBBLE_CADENCE = 3;   // falling rubble every 3rd round
    private static final int RUBBLE_MIN_TILES = 2;
    private static final int RUBBLE_MAX_TILES = 3;
    private static final int RUBBLE_DURATION = 3;  // rounds the rubble stays impassable
    private static final int RUBBLE_DAMAGE = 4;

    private GridPos eliteStartPos;

    @Override
    public String biomeId() {
        return "mountain";
    }

    @Override
    public String introTitle() {
        return "§7§l☠ Rockbreaker Kin";
    }

    @Override
    public List<LevelDefinition.EnemySpawn> initialSpawns(int width, int height, int ordinal, Random rng) {
        int hpBonus = ordinal * com.crackedgames.craftics.CrafticsMod.CONFIG.hpPerBiome();
        int atkBonus = ordinal / Math.max(1, com.crackedgames.craftics.CrafticsMod.CONFIG.atkPerBiome());

        List<LevelDefinition.EnemySpawn> spawns = new ArrayList<>();
        List<GridPos> used = new ArrayList<>();
        used.add(new GridPos(width / 2, 0)); // player start - never spawn here

        // Elite Rockbreaker Kin - a beefy iron golem, placed near arena center. The golem alone
        // is the fight, so no companion add is spawned alongside it.
        GridPos elitePos = MinibossSpawns.findOpenBiased(width, height, used, rng, width / 2, height / 2);
        if (elitePos == null) elitePos = new GridPos(width / 2, height / 2);
        used.add(elitePos);
        eliteStartPos = elitePos;
        spawns.add(new LevelDefinition.EnemySpawn("minecraft:iron_golem", elitePos,
            50 + hpBonus * 2, 7 + atkBonus, 3, 1));

        return spawns;
    }

    @Override
    public void onFightStart(MinibossContext ctx) {
        ctx.banner(introTitle());
    }

    @Override
    public void onRoundStart(MinibossContext ctx) {
        if (ctx.round() % RUBBLE_CADENCE != 0) return;

        GridArena arena = ctx.arena();
        GridPos playerStart = new GridPos(arena.getWidth() / 2, 0);

        List<GridPos> avoid = new ArrayList<>();
        avoid.add(playerStart);
        if (eliteStartPos != null) avoid.add(eliteStartPos);

        int tileCount = RUBBLE_MIN_TILES + ctx.rng().nextInt(RUBBLE_MAX_TILES - RUBBLE_MIN_TILES + 1);
        List<GridPos> chosen = new ArrayList<>();
        for (int i = 0; i < tileCount; i++) {
            GridPos pos = pickRubbleTile(arena, avoid, chosen, ctx.rng());
            if (pos == null) continue;
            chosen.add(pos);
            ctx.placeTemporaryTile(pos, TileType.RUBBLE, RUBBLE_DURATION);

            // If a living enemy happens to be standing on the tile, it gets crushed.
            for (CombatEntity occupant : ctx.enemies()) {
                if (occupant.isAlive() && pos.equals(occupant.getGridPos())) {
                    occupant.takeDamage(RUBBLE_DAMAGE);
                    occupant.setStunned(true);
                    ctx.message("§7Falling rock crushes " + occupant.getDisplayName() + "!");
                    break;
                }
            }
        }

        if (!chosen.isEmpty()) ctx.message("§7Rocks crash down!");
    }

    /**
     * Picks a random tile within the arena bounds, avoiding the given avoid-list (exact match)
     * and any tile already chosen this round.
     */
    private static GridPos pickRubbleTile(GridArena arena, List<GridPos> avoid, List<GridPos> chosen, Random rng) {
        int width = arena.getWidth();
        int height = arena.getHeight();
        for (int attempts = 0; attempts < 20; attempts++) {
            int x = rng.nextInt(Math.max(1, width));
            int z = rng.nextInt(Math.max(1, height));
            GridPos pos = new GridPos(x, z);
            if (avoid.contains(pos) || chosen.contains(pos)) continue;
            return pos;
        }
        return null;
    }
}
