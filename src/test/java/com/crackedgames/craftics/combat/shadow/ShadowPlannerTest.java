package com.crackedgames.craftics.combat.shadow;

import com.crackedgames.craftics.combat.shadow.ShadowPlanner.Kind;
import com.crackedgames.craftics.combat.shadow.ShadowPlanner.Situation;
import com.crackedgames.craftics.combat.shadow.ShadowPlanner.Step;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * How a Shadow spends a turn.
 *
 * <p>It gets the player's AP and Speed and the player's weapons, and is asked one question over
 * and over until the answer is "nothing": what next? Each answer is one ordinary enemy action,
 * so everything the game already does for a zombie swinging a sword happens for the Shadow too.
 */
class ShadowPlannerTest {

    private static final ShadowWeapon FIST = new ShadowWeapon(-1, "fist", 1, 1, 1, false);
    private static final ShadowWeapon SWORD = new ShadowWeapon(0, "minecraft:diamond_sword", 7, 1, 1, false);
    private static final ShadowWeapon AXE = new ShadowWeapon(1, "minecraft:diamond_axe", 9, 2, 1, false);
    private static final ShadowWeapon SPEAR = new ShadowWeapon(2, "mod:spear", 6, 1, 2, false);
    private static final ShadowWeapon BOW = new ShadowWeapon(3, "minecraft:bow", 5, 2, 6, true);
    private static final ShadowWeapon LONGBOW = new ShadowWeapon(4, "mod:longbow", 12, 2, 8, true);

    private static final ShadowItem BREAD = ShadowItem.heal(10, "minecraft:bread", 1, 5);
    private static final ShadowItem STRENGTH = ShadowItem.buff(11, "minecraft:potion", 1, "STRENGTH", 2, 3);
    private static final ShadowItem POISON = ShadowItem.debuff(12, "minecraft:splash_potion", 1, 4, "POISON", 0, 3);
    private static final ShadowItem IMMOLATION = ShadowItem.spell(13, "minecraft:burn_pottery_sherd", "immolation", 4, 3, 100);
    private static final ShadowItem TIDAL_SURGE = ShadowItem.spell(14, "minecraft:flow_pottery_sherd", "tidal_surge", 4, 0, 100)
        .withRadius(2).centredOnSelf();
    private static final ShadowItem PEARL = ShadowItem.blink(15, "minecraft:ender_pearl", "an_ender_pearl", 1, 99);
    private static final ShadowItem PHASE_STEP = ShadowItem.blink(16, "minecraft:explorer_pottery_sherd", "phase_step", 3, 4);
    private static final ShadowItem VEXES = ShadowItem.summon(17, "minecraft:archer_pottery_sherd", "seeker_vexes", 3, 2);

    /** Builder-ish helper: a healthy Shadow, mid-turn, with clear sight of its target. */
    private static Situation at(int distance, int ap, int move, List<ShadowWeapon> weapons) {
        return new Situation(ap, move, distance, true, 100, false, 0, weapons, List.of());
    }

    @Test
    void swingsWhenTheTargetIsInReach() {
        Step step = ShadowPlanner.next(at(1, 3, 3, List.of(FIST, SWORD)));

        assertEquals(Kind.ATTACK, step.kind());
        assertEquals(1, step.index(), "the sword, not the fist");
    }

    @Test
    void walksUpWhenTheTargetIsOutOfReach() {
        Step step = ShadowPlanner.next(at(4, 3, 3, List.of(FIST, SWORD)));

        assertEquals(Kind.APPROACH, step.kind());
        assertEquals(1, step.index(), "closing to the reach of its best weapon");
    }

    @Test
    void aLongerWeaponReachesFurther() {
        assertEquals(Kind.ATTACK, ShadowPlanner.next(at(2, 3, 3, List.of(FIST, SPEAR))).kind());
        assertEquals(Kind.APPROACH, ShadowPlanner.next(at(2, 3, 3, List.of(FIST, SWORD))).kind());
    }

    @Test
    void usesTheWeaponThatReachesEvenIfABetterOneDoesNot() {
        // At two tiles the axe cannot connect. The spear can, so the spear it is.
        Step step = ShadowPlanner.next(at(2, 3, 3, List.of(FIST, AXE, SPEAR)));

        assertEquals(Kind.ATTACK, step.kind());
        assertEquals(2, step.index());
    }

    @Test
    void aWeaponItCannotAffordIsNotAnOption() {
        // One AP left: the 2-AP axe is out, the sword is in.
        Step step = ShadowPlanner.next(at(1, 1, 0, List.of(FIST, SWORD, AXE)));

        assertEquals(Kind.ATTACK, step.kind());
        assertEquals(1, step.index());
    }

    @Test
    void betweenEqualWeaponsTheCheaperOneWins() {
        ShadowWeapon heavy = new ShadowWeapon(5, "mod:heavy", 7, 2, 1, false);

        assertEquals(1, ShadowPlanner.next(at(1, 3, 0, List.of(heavy, SWORD))).index());
    }

    @Test
    void shootsFromRange() {
        Step step = ShadowPlanner.next(at(5, 2, 3, List.of(FIST, BOW)));

        assertEquals(Kind.ATTACK, step.kind());
        assertEquals(1, step.index());
    }

    @Test
    void cannotShootWhatItCannotSee() {
        Situation blocked = new Situation(2, 3, 5, false, 100, false, 0, List.of(FIST, BOW), List.of());

        assertEquals(Kind.APPROACH, ShadowPlanner.next(blocked).kind());
    }

    @Test
    void anArcherWithABetterBladeShootsThenClosesIn() {
        List<ShadowWeapon> kit = List.of(FIST, SWORD, BOW);

        assertEquals(Kind.ATTACK, ShadowPlanner.next(at(5, 2, 3, kit)).kind());
        // AP spent on the shot. The sword is the better weapon, so it uses its legs.
        Step after = ShadowPlanner.next(at(5, 0, 3, kit));
        assertEquals(Kind.APPROACH, after.kind());
        assertEquals(1, after.index());
    }

    @Test
    void anArcherWhoseBowIsItsBestWeaponHoldsItsGround() {
        List<ShadowWeapon> kit = List.of(FIST, SWORD, LONGBOW);

        assertEquals(Kind.END, ShadowPlanner.next(at(5, 0, 3, kit)).kind());
    }

    @Test
    void stopsWhenItHasNothingLeftToSpend() {
        assertEquals(Kind.END, ShadowPlanner.next(at(1, 0, 3, List.of(FIST, SWORD))).kind());
        assertEquals(Kind.END, ShadowPlanner.next(at(4, 3, 0, List.of(FIST, SWORD))).kind());
    }

    @Test
    void keepsWalkingEvenWithNoApLeft() {
        // Ending the turn two tiles nearer is still worth doing.
        assertEquals(Kind.APPROACH, ShadowPlanner.next(at(6, 0, 2, List.of(FIST, SWORD))).kind());
    }

    @Test
    void anythingOwedFromTheLastActionComesFirst() {
        // A sword swing that still has to carry into the next person, a thrown snowball that
        // still has to knock its target back: these finish before anything new starts.
        Situation owed = new Situation(2, 3, 1, true, 100, true, 1, List.of(FIST, SWORD), List.of());

        assertEquals(Kind.FOLLOW_UP, ShadowPlanner.next(owed).kind());
    }

    @Test
    void castsASherdWhenItCanAffordItAndTheTargetIsInRange() {
        Situation inRange = new Situation(4, 3, 3, true, 100, false, 0, List.of(FIST, SWORD), List.of(IMMOLATION));

        Step step = ShadowPlanner.next(inRange);
        assertEquals(Kind.SPELL, step.kind());
        assertEquals(0, step.index());
    }

    @Test
    void aSherdThatCostsMoreThanItsApStaysInThePack() {
        // Three AP, a four AP sherd. It walks up and uses its fists like anyone else would.
        Situation poor = new Situation(3, 3, 3, true, 100, false, 0, List.of(FIST, SWORD), List.of(IMMOLATION));

        assertEquals(Kind.APPROACH, ShadowPlanner.next(poor).kind());
    }

    @Test
    void aSherdNeedsRangeAndSight() {
        Situation far = new Situation(4, 3, 6, true, 100, false, 0, List.of(FIST, SWORD), List.of(IMMOLATION));
        Situation hidden = new Situation(4, 3, 3, false, 100, false, 0, List.of(FIST, SWORD), List.of(IMMOLATION));

        assertEquals(Kind.APPROACH, ShadowPlanner.next(far).kind());
        assertEquals(Kind.APPROACH, ShadowPlanner.next(hidden).kind());
    }

    @Test
    void aSpellCentredOnItselfWaitsUntilTheTargetIsInsideIt() {
        Situation outside = new Situation(4, 3, 3, true, 100, false, 0, List.of(FIST, SWORD), List.of(TIDAL_SURGE));
        Situation inside = new Situation(4, 3, 2, true, 100, false, 0, List.of(FIST, SWORD), List.of(TIDAL_SURGE));

        assertEquals(Kind.APPROACH, ShadowPlanner.next(outside).kind());
        assertEquals(Kind.SPELL, ShadowPlanner.next(inside).kind());
    }

    @Test
    void summonsBeforeAnythingElseItCouldDo() {
        Situation start = new Situation(3, 3, 1, true, 100, false, 0, List.of(FIST, SWORD), List.of(VEXES));

        assertEquals(Kind.SUMMON, ShadowPlanner.next(start).kind());
    }

    @Test
    void throwsAPearlToCloseADistanceItCannotWalk() {
        // Six tiles off, three Speed. Walking gets halfway. The pearl gets there.
        Situation far = new Situation(3, 3, 6, true, 100, false, 0, List.of(FIST, SWORD), List.of(PEARL));

        Step step = ShadowPlanner.next(far);
        assertEquals(Kind.BLINK, step.kind());
        assertEquals(0, step.index());
    }

    @Test
    void doesNotWasteAPearlWhenItIsAlreadyInReach() {
        Situation beside = new Situation(3, 3, 1, true, 100, false, 0, List.of(FIST, SWORD), List.of(PEARL));

        assertEquals(Kind.ATTACK, ShadowPlanner.next(beside).kind());
    }

    @Test
    void aShortBlinkOnlyWorksWhenTheTargetIsCloseEnough() {
        // Phase Step reaches four tiles, and it has to land beside the target, not on it.
        Situation reachable = new Situation(3, 0, 5, true, 100, false, 0, List.of(FIST, SWORD), List.of(PHASE_STEP));
        Situation tooFar = new Situation(3, 0, 6, true, 100, false, 0, List.of(FIST, SWORD), List.of(PHASE_STEP));

        assertEquals(Kind.BLINK, ShadowPlanner.next(reachable).kind());
        assertEquals(Kind.END, ShadowPlanner.next(tooFar).kind());
    }

    @Test
    void walksRatherThanBlinksWhenWalkingGetsThere() {
        // The target is two tiles off and it has the legs for it. The pearl can wait.
        Situation near = new Situation(3, 3, 2, true, 100, false, 0, List.of(FIST, SWORD), List.of(PEARL));

        assertEquals(Kind.APPROACH, ShadowPlanner.next(near).kind());
    }

    @Test
    void eatsWhenBadlyHurt() {
        Situation hurt = new Situation(3, 3, 1, true, 30, false, 0, List.of(FIST, SWORD), List.of(BREAD));

        Step step = ShadowPlanner.next(hurt);
        assertEquals(Kind.HEAL, step.kind());
        assertEquals(0, step.index());
    }

    @Test
    void doesNotWasteFoodWhileHealthy() {
        Situation fine = new Situation(3, 3, 1, true, 80, false, 0, List.of(FIST, SWORD), List.of(BREAD));

        assertEquals(Kind.ATTACK, ShadowPlanner.next(fine).kind());
    }

    @Test
    void cannotEatWithoutTheApForIt() {
        Situation hurt = new Situation(0, 0, 1, true, 20, false, 0, List.of(FIST, SWORD), List.of(BREAD));

        assertEquals(Kind.END, ShadowPlanner.next(hurt).kind());
    }

    @Test
    void throwsAPotionWhenTheTargetIsCloseEnough() {
        Situation near = new Situation(3, 3, 3, true, 100, false, 0, List.of(FIST, SWORD), List.of(POISON));

        Step step = ShadowPlanner.next(near);
        assertEquals(Kind.DEBUFF, step.kind());
        assertEquals(0, step.index());
    }

    @Test
    void holdsThePotionWhenTheTargetIsTooFarOrOutOfSight() {
        Situation far = new Situation(3, 3, 7, true, 100, false, 0, List.of(FIST, SWORD), List.of(POISON));
        Situation hidden = new Situation(3, 3, 3, false, 100, false, 0, List.of(FIST, SWORD), List.of(POISON));

        assertEquals(Kind.APPROACH, ShadowPlanner.next(far).kind());
        assertEquals(Kind.APPROACH, ShadowPlanner.next(hidden).kind());
    }

    @Test
    void drinksABuffOnTheWayIn() {
        // Nothing to hit yet, so the walk is a good time for it.
        Situation closing = new Situation(3, 3, 6, true, 100, false, 0, List.of(FIST, SWORD), List.of(STRENGTH));

        assertEquals(Kind.BUFF, ShadowPlanner.next(closing).kind());
    }

    @Test
    void doesNotStopSwingingToDrink() {
        Situation inReach = new Situation(3, 3, 1, true, 100, false, 0, List.of(FIST, SWORD), List.of(STRENGTH));

        assertEquals(Kind.ATTACK, ShadowPlanner.next(inReach).kind());
    }

    @Test
    void aTurnCannotGoOnForever() {
        Situation long_ = new Situation(99, 99, 1, true, 100, false, ShadowPlanner.MAX_ACTIONS,
            List.of(FIST, SWORD), List.of());

        assertEquals(Kind.END, ShadowPlanner.next(long_).kind());
    }

    @Test
    void aWholeTurnPlaysOutAsWalkThenSwingUntilTheApIsGone() {
        // Three AP, three Speed, a 1-AP sword, target four tiles off: one walk, then swings.
        List<ShadowWeapon> kit = List.of(FIST, SWORD);
        int ap = 3, move = 3, distance = 4, actions = 0, swings = 0;
        for (int guard = 0; guard < 20; guard++) {
            Step step = ShadowPlanner.next(new Situation(ap, move, distance, true, 100, false, actions, kit, List.of()));
            if (step.kind() == Kind.END) break;
            actions++;
            if (step.kind() == Kind.APPROACH) {
                int walked = Math.min(move, distance - kit.get(step.index()).range());
                move -= walked;
                distance -= walked;
            } else if (step.kind() == Kind.ATTACK) {
                ap -= kit.get(step.index()).apCost();
                swings++;
            }
        }

        assertEquals(1, distance);
        assertEquals(3, swings);
        assertEquals(0, ap);
    }
}
