package com.crackedgames.craftics.combat.ai.boss;

/**
 * The Bastion Brute's Momentum math.
 *
 * <p>Momentum inverts the reflex every other boss teaches: the Brute hits harder the further it
 * travelled to reach you, so the safest tile is the one next to it, and kiting is what feeds it.
 * Its kit already punishes hugging (adjacent Rampage, Ground Slam's fire cross, a piglin pack
 * that body-blocks the retreat), so the counterplay is a real decision rather than a free win.
 *
 * <p>The three war banners are asymmetric, so which one the player breaks is a triage decision
 * rather than an interchangeable chore. The March grants the speed that feeds Momentum, so
 * breaking it cuts the ramp at its source instead of by a special case: a slower Brute covers
 * fewer tiles, so it hits softer. Fury is flat attack, felt on every hit rather than only after
 * a long charge. The Horde gates new summons, and it is gated in the AI rather than here because
 * it grants no number.
 *
 * <p>Minecraft-free so the whole ruleset is unit-testable with no bootstrap.
 */
public final class Momentum {

    private Momentum() {}

    /** Damage added to the Brute's next hit, one per tile it travelled. */
    public static int bonusForTilesMoved(int tilesMoved) {
        return Math.max(0, tilesMoved);
    }

    /**
     * Speed granted by the Banner of the March. Once it falls this returns 0, leaving the Brute
     * on its base speed; {@code CombatEntity.getMoveSpeed} floors enemies at 1 tile per turn, so
     * it always closes eventually.
     */
    public static int marchSpeedBonus(boolean marchAlive) {
        return marchAlive ? 2 : 0;
    }

    /**
     * Attack granted by the Banner of Fury. Flat, so breaking it is legible on the very next hit
     * including Gore Charge, rather than only on hits that followed a long run-up.
     */
    public static int furyAttackBonus(boolean furyAlive) {
        return furyAlive ? 3 : 0;
    }
}
