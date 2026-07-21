package com.crackedgames.craftics.combat.miniboss.mechanics;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.miniboss.MinibossContext;
import com.crackedgames.craftics.combat.miniboss.MinibossMechanic;
import com.crackedgames.craftics.combat.miniboss.MinibossSpawns;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;
import com.crackedgames.craftics.core.TileType;
import com.crackedgames.craftics.level.LevelDefinition;
import net.minecraft.sound.SoundEvents;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The cave biome's level-4 miniboss: the Cave-In encounter. Opens with 3 enemies mixing spiders
 * and silverfish, ordinal-scaled like the other biomes' openers. The signature hazard is a
 * collapsing ceiling: every 2nd round, 2-3 random tiles cave in as temporary {@link TileType#RUBBLE}
 * (matching {@link MountainRockbreakerMechanic}'s falling-stone convention). If a living enemy
 * happens to be standing on a chosen tile it gets crushed, taking direct damage and getting
 * stunned - same crush pattern as {@link MountainRockbreakerMechanic}.
 *
 * <p>No extra win objective - the cave-in is pure pressure/pathing denial as rubble shrinks the
 * arena over time; clearing the enemies completes the fight via the base "all enemies cleared"
 * check, so {@link #isComplete} uses the interface default (always true).
 */
public final class CaveInMechanic implements MinibossMechanic {

    private static final int COLLAPSE_CADENCE = 2;   // ceiling collapses every 2nd round
    private static final int RUBBLE_MIN_TILES = 2;
    private static final int RUBBLE_MAX_TILES = 3;
    private static final int RUBBLE_DURATION = 3;     // rounds the rubble stays impassable
    private static final int RUBBLE_DAMAGE = 4;

    @Override
    public String biomeId() {
        return "cave";
    }

    @Override
    public String introTitle() {
        return "§8§l☠ Cave-In";
    }

    @Override
    public List<LevelDefinition.EnemySpawn> initialSpawns(int width, int height, int ordinal, Random rng) {
        int hpBonus = ordinal * com.crackedgames.craftics.CrafticsMod.CONFIG.hpPerBiome();
        int atkBonus = ordinal / Math.max(1, com.crackedgames.craftics.CrafticsMod.CONFIG.atkPerBiome());

        List<LevelDefinition.EnemySpawn> spawns = new ArrayList<>();
        List<GridPos> used = new ArrayList<>();
        used.add(new GridPos(width / 2, 0)); // player start - never spawn here

        // Mix of spiders and silverfish: randomize which two of the three slots are spiders vs.
        // silverfish so the opener isn't always the same shape (2 spiders + 1 silverfish, or
        // 1 spider + 2 silverfish).
        boolean extraSilverfish = rng.nextBoolean();
        for (int i = 0; i < 3; i++) {
            GridPos pos = MinibossSpawns.findOpen(width, height, used, rng);
            if (pos == null) continue;
            used.add(pos);

            boolean isSilverfish = extraSilverfish ? (i >= 1) : (i >= 2);
            if (isSilverfish) {
                spawns.add(new LevelDefinition.EnemySpawn("minecraft:silverfish", pos,
                    8 + hpBonus, 3 + atkBonus, 0, 1));
            } else {
                spawns.add(new LevelDefinition.EnemySpawn("minecraft:spider", pos,
                    14 + hpBonus, 4 + atkBonus, 0, 1));
            }
        }

        return spawns;
    }

    @Override
    public void onFightStart(MinibossContext ctx) {
        ctx.banner(introTitle());
        ctx.playSound(SoundEvents.BLOCK_GRAVEL_BREAK, 0.6f, 0.7f);
    }

    @Override
    public void onRoundStart(MinibossContext ctx) {
        if (ctx.round() % COLLAPSE_CADENCE != 0) return;

        GridArena arena = ctx.arena();
        GridPos playerStart = new GridPos(arena.getWidth() / 2, 0);

        List<GridPos> avoid = new ArrayList<>();
        avoid.add(playerStart);

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
                    ctx.message("§8The ceiling crushes " + occupant.getDisplayName() + "!");
                    break;
                }
            }
        }

        if (!chosen.isEmpty()) {
            ctx.message("§8The ceiling collapses!");
            ctx.playSound(SoundEvents.BLOCK_STONE_BREAK, 0.7f, 0.6f);
        }
    }

    /**
     * Picks a random tile within the arena bounds, avoiding the given avoid-list (exact match)
     * and any tile already chosen this round. Mirrors
     * {@code MountainRockbreakerMechanic#pickRubbleTile}.
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
