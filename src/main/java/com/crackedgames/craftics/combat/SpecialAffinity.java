package com.crackedgames.craftics.combat;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

/**
 * Single source of truth for how the player's Special-class affinity scales
 * the output of utility items (potions, banners, goat horns). Same formulas
 * as the previous private statics on {@link ItemUseHandler}, hoisted here so
 * everything that should respect the affinity can call into one place.
 */
public final class SpecialAffinity {

    private SpecialAffinity() {}

    /** Raw points the player has invested in the Special affinity track. */
    public static int points(ServerPlayerEntity player) {
        if (player == null) return 0;
        return PlayerProgression.get((ServerWorld) player.getEntityWorld())
            .getStats(player)
            .getAffinityPoints(PlayerProgression.Affinity.SPECIAL);
    }

    /** Amplifier bonus added on top of the base level. 1 step per 3 points. */
    public static int potencyBonus(ServerPlayerEntity player) {
        return points(player) / 3;
    }

    /** Extra turns added to the duration. First point grants +1, then +1 per
     *  4 additional points. Returns 0 for a player with no Special points. */
    public static int durationBonus(ServerPlayerEntity player) {
        int p = points(player);
        return p > 0 ? 1 + ((p - 1) / 4) : 0;
    }
}
