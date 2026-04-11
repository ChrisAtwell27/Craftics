package com.crackedgames.craftics.compat.artifacts;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player transient state for stateful Artifacts effects.
 * <p>
 * Why a static map instead of handler instance fields? Craftics re-runs
 * {@code TrimEffects.scan(player)} at the start of every player turn
 * (see {@code CombatManager.startPlayerTurn}), which produces a fresh list of
 * {@code CombatEffectHandler} instances from the scanner. Any state stored on the
 * handler itself is therefore wiped after the first turn. Keying off the player
 * UUID lets the state survive across those re-scans while still being cleaned up
 * when combat ends.
 */
public final class ArtifactsState {

    private static final ConcurrentHashMap<UUID, ArtifactsState> STATE = new ConcurrentHashMap<>();

    /** Scarf of Invisibility: has the player landed their first attack this fight yet? */
    public boolean scarfFirstAttackUsed = false;

    /** Cross Necklace: is the 50% damage reduction shield currently active? */
    public boolean crossShieldActive = false;

    /** Chorus Totem: has the once-per-combat lethal save already been spent? */
    public boolean chorusTotemUsed = false;

    /** Umbrella: has the first-hit complete block already been spent this fight? */
    public boolean umbrellaFirstHitUsed = false;

    /** Novelty Drinking Hat: which stat (if any) was rolled at combat start. */
    public NoveltyStat noveltyStat = null;

    /** Rooted Boots / movement-on-turn tracking: did the player move on this turn? */
    public boolean movedThisTurn = false;

    /** Panic Necklace: is the +2 Speed buff currently active (1 turn)? */
    public boolean panicSpeedActive = false;

    /** Enemies that have already been tagged with "was soaked when hit" for logging dedupe, etc. */
    public Set<Integer> transientFlags = new HashSet<>();

    public static ArtifactsState get(UUID playerId) {
        return STATE.computeIfAbsent(playerId, k -> new ArtifactsState());
    }

    /** Called at combat start to wipe any leftover per-fight flags. */
    public static void reset(UUID playerId) {
        STATE.remove(playerId);
    }

    public enum NoveltyStat { MELEE, SPEED, DEFENSE, LUCK }
}
