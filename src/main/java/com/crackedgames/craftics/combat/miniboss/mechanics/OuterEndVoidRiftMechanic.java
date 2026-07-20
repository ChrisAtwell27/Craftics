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
 * The outer end islands biome's level-4 miniboss: the Void Rift. Opens with 3 endermen,
 * ordinal-scaled exactly like {@link RiverFlashFloodMechanic}'s drowned. The signature hazard is
 * the arena itself crumbling: every round, the outermost still-intact ring of tiles (the border
 * at inset {@code ringsCrumbled} from the arena edge) is converted to {@link TileType#VOID} - a
 * non-walkable hole with no step damage, since falling into the void is denial rather than a
 * damage tick. This shrinks the safe playing field one ring per round, pressuring the fight
 * toward a close center.
 *
 * <p>Crumbling stops once the remaining safe core would get too small for the fight to stay
 * winnable (rings crumbled &gt;= min(width, height)/2 - 2), leaving a guaranteed safe core in the
 * middle of the arena. The player-start tile (width/2, 0) is always protected - if it would fall
 * on the ring being crumbled this round, that single tile is skipped so the party never loses
 * their spawn point out from under them.
 *
 * <p>No extra win objective - clearing the endermen (and any future adds) completes the fight, so
 * {@link #isComplete} uses the interface default (always true); the crumbling rift is pure
 * pressure to force the fight to resolve before the arena runs out of solid ground.
 */
public final class OuterEndVoidRiftMechanic implements MinibossMechanic {

    private static final int ENDERMAN_COUNT = 3;
    private static final int VOID_DURATION = 99; // rounds - effectively permanent for the fight

    /** How many rings have been crumbled so far, counting inward from the arena border. */
    private int ringsCrumbled = 0;

    @Override
    public String biomeId() {
        return "outer_end_islands";
    }

    @Override
    public String introTitle() {
        return "§5§l☠ Void Rift";
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
        ringsCrumbled = 0;
        ctx.banner(introTitle());
    }

    @Override
    public void onRoundStart(MinibossContext ctx) {
        GridArena arena = ctx.arena();
        int width = arena.getWidth();
        int height = arena.getHeight();

        int maxRings = Math.min(width, height) / 2 - 2;
        if (ringsCrumbled >= maxRings) return; // stop crumbling - preserve a safe core

        GridPos playerStart = new GridPos(width / 2, 0);
        int ring = ringsCrumbled;
        boolean crumbledAny = false;

        for (int x = 0; x < width; x++) {
            for (int z = 0; z < height; z++) {
                if (Math.min(Math.min(x, width - 1 - x), Math.min(z, height - 1 - z)) != ring) continue;
                GridPos pos = new GridPos(x, z);
                if (pos.x() == playerStart.x() && pos.z() == playerStart.z()) continue; // never void the spawn
                ctx.placeTemporaryTile(pos, TileType.VOID, VOID_DURATION);
                crumbledAny = true;
            }
        }

        ringsCrumbled++;
        if (crumbledAny) ctx.message("§5The islands crumble into the void!");
    }
}
