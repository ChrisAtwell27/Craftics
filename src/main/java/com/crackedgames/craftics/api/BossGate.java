package com.crackedgames.craftics.api;

import net.minecraft.server.network.ServerPlayerEntity;

import java.util.List;

/**
 * A requirement a party has to meet before it is let into a biome's boss level.
 *
 * <p>Some bosses cannot be fought with whatever the party happens to be carrying. The Aether's
 * Slider is the case this was written for: only a pickaxe hurts it, so a party without one
 * walks into a fight it can neither win nor leave. A gate is asked on the way in instead, while
 * turning back still costs nothing.
 *
 * <p>A gate only answers the question. When it refuses, Craftics shows the lines it returned in
 * a dialogue that cannot be closed, and the party leader has to pick one of two ways out: go
 * home, which ends the run exactly as Go Home on the victory screen does, or start the biome
 * again from its first level with everything the party is carrying. Both choices, and what they
 * do, belong to the engine, so a gate never describes or implements them.
 *
 * <pre>{@code
 * BossGateRegistry.register("mymod_vault", party -> party.stream().anyMatch(MyMod::hasKey)
 *     ? List.of()
 *     : List.of("Nobody in your party is carrying the vault key."));
 * }</pre>
 *
 * <p>Asked every time a campaign run is about to enter the boss level, a run resumed from the
 * hub included. Never asked in Infinite Mode, whose boss levels roll a boss of their own rather
 * than the biome's.
 *
 * @since 0.5.0
 */
@FunctionalInterface
public interface BossGate {

    /**
     * Decide whether this party may enter the boss level.
     *
     * @param party everyone about to go in, all of them online. Never empty
     * @return the narrator lines saying why the party may not enter, in the order they are
     *         shown. An empty list, or null, lets the party through
     */
    List<String> check(List<ServerPlayerEntity> party);
}
