package com.crackedgames.craftics.raid;

/**
 * A raid boss's opening advantage: either it acts twice per enemy phase, or it
 * carries a permanent buff. Exactly one, never both, never neither.
 *
 * <p>The buff is held as a lowercase id string rather than a RaidBossBuff so the
 * parser has no dependency on the buff enum's resolution rules; RaidBossBuff.of
 * turns it into the enum at spawn time.
 */
public record RaidBossPower(boolean isDoubleMove, String buffEffect, int amplifier) {

    public static RaidBossPower doubleMove() {
        return new RaidBossPower(true, "", 0);
    }

    public static RaidBossPower buff(String effect, int amplifier) {
        return new RaidBossPower(false, effect, Math.max(0, amplifier));
    }
}
