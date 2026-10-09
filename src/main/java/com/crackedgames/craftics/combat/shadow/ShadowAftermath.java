package com.crackedgames.craftics.combat.shadow;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.core.GridPos;

import java.util.ArrayList;
import java.util.List;

/**
 * What a weapon did to one stand-in, read off it afterwards.
 *
 * <p>A Shadow uses its copied weapon by running the weapon's real ability code against
 * stand-ins for the player and their pets (see {@code ShadowStrike}). That code does what it
 * always does: takes health off its target, sets it alight, shoves it across the arena. This
 * is how those results get back out. One of these watches a stand-in from before the weapon
 * is used, and {@link #read} says what changed, in terms that can be applied to the real
 * victim: damage, a shove, and status effects by name.
 *
 * <p>Two effects are softened on the way out, because they are fine against a monster and
 * miserable against a person. A stun becomes Slowness, since stunning a player is taking
 * their turn away. Shattered armour becomes Vulnerable for a few turns rather than for the
 * rest of the fight.
 *
 * <p>Pure: it only ever looks at the {@code CombatEntity} it was given.
 */
public final class ShadowAftermath {

    /** A status effect to put on the real victim. {@code type} is a {@code CombatEffects.EffectType} name. */
    public record Effect(String type, int turns, int level) {}

    /**
     * @param damage  health the stand-in lost
     * @param movedX  how far it was shoved along x, in tiles; negative is west
     * @param movedZ  how far it was shoved along z, in tiles; negative is north
     * @param effects status effects it picked up
     */
    public record Change(int damage, int movedX, int movedZ, List<Effect> effects) {
        /** True when the weapon did nothing at all to this stand-in. */
        public boolean nothing() {
            return damage <= 0 && movedX == 0 && movedZ == 0 && effects.isEmpty();
        }
    }

    private static final int STUN_SLOW_TURNS = 2;
    private static final int STUN_SLOW_LEVEL = 1;
    private static final int SOFT_DEBUFF_TURNS = 3;
    private static final int BLEED_TURNS = 3;

    private final CombatEntity standIn;
    private final int hp;
    private final GridPos pos;
    private final int burning, poison, wither, slowness, soaked, bleed, blinded, levitation;
    private final int attackPenalty, defensePenalty, defenseShattered;
    private final boolean stunned;

    private ShadowAftermath(CombatEntity standIn) {
        this.standIn = standIn;
        this.hp = standIn.getCurrentHp();
        this.pos = standIn.getGridPos();
        this.burning = standIn.getBurningTurns();
        this.poison = standIn.getPoisonTurns();
        this.wither = standIn.getWitherTurns();
        this.slowness = standIn.getSlownessTurns();
        this.soaked = standIn.getSoakedTurns();
        this.bleed = standIn.getBleedStacks();
        this.blinded = standIn.getBlindedTurns();
        this.levitation = standIn.getLevitationStateTurns();
        this.attackPenalty = standIn.getAttackPenalty();
        this.defensePenalty = standIn.getDefensePenalty();
        this.defenseShattered = standIn.getPermanentDefReduction();
        this.stunned = standIn.isStunned();
    }

    /** Start watching a stand-in. Call before the weapon is used on it. */
    public static ShadowAftermath watch(CombatEntity standIn) {
        return new ShadowAftermath(standIn);
    }

    /** What has happened to the stand-in since {@link #watch}. */
    public Change read() {
        List<Effect> effects = new ArrayList<>();
        if (standIn.getBurningTurns() > burning) {
            effects.add(new Effect("BURNING", standIn.getBurningTurns(), standIn.getBurningAmplifier()));
        }
        if (standIn.getPoisonTurns() > poison) {
            effects.add(new Effect("POISON", standIn.getPoisonTurns(), standIn.getPoisonAmplifier()));
        }
        if (standIn.getWitherTurns() > wither) {
            effects.add(new Effect("WITHER", standIn.getWitherTurns(), standIn.getWitherAmplifier()));
        }
        boolean stunnedNow = standIn.isStunned() && !stunned;
        if (standIn.getSlownessTurns() > slowness) {
            effects.add(new Effect("SLOWNESS", standIn.getSlownessTurns(),
                Math.max(0, standIn.getSlownessPenalty() - 1)));
        } else if (stunnedNow) {
            effects.add(new Effect("SLOWNESS", STUN_SLOW_TURNS, STUN_SLOW_LEVEL));
        }
        if (standIn.getSoakedTurns() > soaked) {
            effects.add(new Effect("SOAKED", standIn.getSoakedTurns(), 0));
        }
        if (standIn.getBleedStacks() > bleed) {
            effects.add(new Effect("BLEEDING", BLEED_TURNS, standIn.getBleedStacks() - bleed - 1));
        }
        if (standIn.getBlindedTurns() > blinded) {
            effects.add(new Effect("BLINDNESS", standIn.getBlindedTurns(), 0));
        }
        if (standIn.getLevitationStateTurns() > levitation) {
            effects.add(new Effect("LEVITATION", standIn.getLevitationStateTurns(),
                standIn.getLevitationStateAmplifier()));
        }
        if (standIn.getAttackPenalty() > attackPenalty) {
            effects.add(new Effect("WEAKNESS", SOFT_DEBUFF_TURNS, 0));
        }
        if (standIn.getDefensePenalty() > defensePenalty
                || standIn.getPermanentDefReduction() > defenseShattered) {
            effects.add(new Effect("VULNERABLE", SOFT_DEBUFF_TURNS, 0));
        }
        GridPos now = standIn.getGridPos();
        int movedX = now == null || pos == null ? 0 : now.x() - pos.x();
        int movedZ = now == null || pos == null ? 0 : now.z() - pos.z();
        return new Change(Math.max(0, hp - standIn.getCurrentHp()), movedX, movedZ, effects);
    }
}
