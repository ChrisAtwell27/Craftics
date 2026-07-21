package com.crackedgames.craftics.combat.biomeeffect;

import com.crackedgames.craftics.combat.CombatEffects;
import com.crackedgames.craftics.combat.miniboss.MinibossContext;

/**
 * Desert biome weather: a driving sandstorm that periodically blinds the whole party.
 *
 * <p>Cadence mirrors {@code SnowyBlizzardMechanic}'s gust cycle - one warning round, then
 * the hit: every {@link #CADENCE} rounds the storm "gathers" (message only, no effect), and
 * on the following round it breaks, applying Blindness ({@code -2} attack range) for
 * {@link #BLIND_TURNS} turns to every live party member via
 * {@link MinibossContext#applyPartyEffect}. The warning round is the counterplay: close
 * distance before the whiteout lands, exactly like reading a boss telegraph.
 *
 * <p>Instance state on the shared singleton (same pattern as SnowyBlizzardMechanic's
 * pendingGust), reset in {@link #onFightStart} so storms never leak across fights.
 */
public final class SandstormEffect implements BiomeEffect {

    /** A storm gathers every this-many rounds (and breaks the round after). */
    private static final int CADENCE = 4;
    /** How many turns the blindness lasts once the storm breaks. */
    private static final int BLIND_TURNS = 2;

    /** True when last round telegraphed a storm that breaks this round. */
    private boolean pendingStorm = false;

    @Override
    public String id() {
        return "sandstorm";
    }

    @Override
    public void onFightStart(MinibossContext ctx) {
        pendingStorm = false;
        ctx.message("§6§l☀ Sandstorm §r§7- the desert wind howls. Every few turns the storm"
            + " will break and blind everyone caught in the open.");
    }

    @Override
    public void onRoundStart(MinibossContext ctx) {
        // Resolve a storm telegraphed last round: NOW the whiteout hits everyone.
        if (pendingStorm) {
            pendingStorm = false;
            ctx.applyPartyEffect(CombatEffects.EffectType.BLINDNESS, BLIND_TURNS);
            ctx.message("§6☀ The sandstorm breaks! §7Blinded for " + BLIND_TURNS
                + " turns (-2 attack range).");
            return; // one storm beat per round - never warn and hit together
        }

        // Otherwise, on the cadence, warn: the storm lands at the NEXT round boundary,
        // so the party gets one full turn to close distance before range collapses.
        if (ctx.round() % CADENCE != 0 || ctx.round() == 0) return;
        pendingStorm = true;
        ctx.message("§6☀ Sand whips up around the arena - §7the storm breaks next round."
            + " Get close before it blinds you!");
    }
}
