package com.crackedgames.craftics.combat;

/**
 * The 21 hybrid armor-set effect IDs - one per cell of the material-pair matrix.
 * A {@code HybridSetEntry} carries one of these; Phase 2's {@code HybridSetEffects}
 * switches on it to apply the signature combat mechanic.
 */
public enum HybridEffect {
    SKIRMISHER, COUNTERPUNCHER, LUCKY_STREAK, BREAKER, RAMPAGE, RUN_AND_GUN,
    SENTINEL, CUTPURSE, DUELIST, AMBUSH, DEADEYE, GILDED_GUARD, WARLORD,
    IMMOVABLE, AEGIS, GLADIATOR, BERSERKER, CONTAGION, STONEWALL, SIEGE, STORMBRINGER
}
