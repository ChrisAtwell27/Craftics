package com.crackedgames.craftics.combat.biomeeffect.effects;

import com.crackedgames.craftics.combat.biomeeffect.BiomeEffect;
import com.crackedgames.craftics.combat.miniboss.MinibossContext;

/**
 * The river biome's persistent weather layer: a current that sweeps the water. Every two rounds
 * the current pulls, always warned a round ahead:
 *
 * <ul>
 *   <li>Odd rounds (1, 3, 5...) telegraph - flow arrows appear on every water tile pointing the
 *       way that tile's current runs (toward the nearest arena edge), giving a full turn to wade
 *       out of the water.</li>
 *   <li>Even rounds (2, 4, 6...) resolve - anyone still standing in the water is swept a couple of
 *       tiles toward the nearest outside edge. Units on dry ground are untouched.</li>
 * </ul>
 *
 * <p>Direction is resolved from the arena's water layout (see
 * {@code CombatManager.triggerRiverCurrent}): where the water sits mostly on one side the whole
 * current flows that way; on arenas ringed by water (the boss arena) it flows outward in every
 * direction at once, each unit toward its own nearest edge.
 */
public final class RiverCurrentEffect implements BiomeEffect {

    @Override
    public String id() {
        return "river_current";
    }

    @Override
    public void onFightStart(MinibossContext ctx) {
        ctx.message("§9The river runs fast - the current tugs at the shallows.");
    }

    @Override
    public void onRoundStart(MinibossContext ctx) {
        int round = ctx.round();
        if (round % 2 == 0) {
            // Resolve round: sweep everyone in the water toward the nearest edge.
            ctx.riverCurrent(false);
            ctx.message("§9The current surges - it drags you toward the banks!");
        } else {
            // Telegraph round: paint the flow arrows on the water for the pull next round.
            ctx.riverCurrent(true);
            ctx.message("§bThe water churns - the current will pull next turn. Get to dry ground!");
        }
    }
}
