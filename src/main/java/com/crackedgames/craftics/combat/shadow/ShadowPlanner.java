package com.crackedgames.craftics.combat.shadow;

import java.util.List;

/**
 * Decides what a Shadow does next.
 *
 * <p>A Shadow's turn is the player's turn in a mirror: so much AP, so much Speed, spent one
 * action at a time until there is nothing useful left to buy. This is asked "what next?" after
 * every action and answers with a single step. The caller turns each step into one ordinary
 * enemy action, which is what lets the Shadow reuse everything the game already does for a
 * zombie holding a sword.
 *
 * <p>Pure: it sees numbers and lists, never the arena, so {@code ShadowPlannerTest} can walk
 * it through whole turns.
 */
public final class ShadowPlanner {

    private ShadowPlanner() {}

    /** A turn ends after this many actions whatever is left, so a huge AP stat cannot stall a fight. */
    public static final int MAX_ACTIONS = 8;
    /** At or below this share of its health, a Shadow with food eats it. */
    public static final int HEAL_BELOW_PERCENT = 45;

    public enum Kind {
        /**
         * Finish what the last action started: a sword swing carrying into the next person, a
         * snowball's target being knocked back. Free.
         */
        FOLLOW_UP,
        /** Eat, drink or cast something to restore health. {@code index} is into the items. */
        HEAL,
        /** Call creatures to fight for it. {@code index} is into the items. */
        SUMMON,
        /** Throw a harmful potion. {@code index} is into the items. */
        DEBUFF,
        /** Throw something or cast an attacking sherd. {@code index} is into the items. */
        SPELL,
        /** Hit the target with a weapon. {@code index} is into the weapons. */
        ATTACK,
        /** Take a buff. {@code index} is into the items. */
        BUFF,
        /** Appear beside the target. {@code index} is into the items. */
        BLINK,
        /** Walk toward the target, far enough to use the weapon at {@code index}. */
        APPROACH,
        /** Nothing worth doing. The turn is over. */
        END
    }

    /** @param index the weapon or item the step uses, or -1 when it uses neither */
    public record Step(Kind kind, int index) {
        static final Step FOLLOW_UP = new Step(Kind.FOLLOW_UP, -1);
        static final Step END = new Step(Kind.END, -1);
    }

    /**
     * Everything the decision depends on.
     *
     * @param apLeft       AP not yet spent this turn
     * @param moveLeft     tiles of movement not yet spent this turn
     * @param distance     tiles to the target
     * @param lineOfSight  whether a shot or a throw would get there
     * @param hpPercent    the Shadow's health, 0 to 100
     * @param followUpOwed whether the last action left something still to resolve
     * @param actionsTaken actions already taken this turn
     * @param weapons      every weapon in the kit; never empty, bare fists are always in it
     * @param items        the usable things it still has charges of
     */
    public record Situation(int apLeft, int moveLeft, int distance, boolean lineOfSight, int hpPercent,
                            boolean followUpOwed, int actionsTaken,
                            List<ShadowWeapon> weapons, List<ShadowItem> items) {}

    public static Step next(Situation s) {
        if (s.followUpOwed()) return Step.FOLLOW_UP;
        if (s.actionsTaken() >= MAX_ACTIONS) return Step.END;

        if (s.hpPercent() <= HEAL_BELOW_PERCENT) {
            int food = firstAffordable(s, ShadowItem.Use.HEAL);
            if (food >= 0) return new Step(Kind.HEAL, food);
        }

        // Company first: the sooner it arrives, the more turns it gets.
        int summon = firstAffordable(s, ShadowItem.Use.SUMMON);
        if (summon >= 0) return new Step(Kind.SUMMON, summon);

        // Anything thrown or cast goes before a weapon swing. It has limited uses and a
        // limited window, and a sword will still be there afterwards.
        int cast = firstInRange(s);
        if (cast >= 0) {
            ShadowItem item = s.items().get(cast);
            return new Step(item.use() == ShadowItem.Use.SPELL ? Kind.SPELL : Kind.DEBUFF, cast);
        }

        int weapon = bestUsableWeapon(s);
        if (weapon >= 0) return new Step(Kind.ATTACK, weapon);

        // Nothing to hit right now, so the time is free: drink up.
        int buff = firstAffordable(s, ShadowItem.Use.BUFF);
        if (buff >= 0) return new Step(Kind.BUFF, buff);

        int strongest = strongestWeapon(s.weapons());
        boolean outOfReach = strongest >= 0
            && !s.weapons().get(strongest).reaches(s.distance(), s.lineOfSight());
        if (outOfReach) {
            // A blink is for a gap its legs cannot close this turn. If walking gets there,
            // walking is free and the pearl is better kept.
            int stillShort = s.distance() - s.weapons().get(strongest).range() - s.moveLeft();
            if (stillShort > 0) {
                int blink = firstBlinkThatLands(s);
                if (blink >= 0) return new Step(Kind.BLINK, blink);
            }
            // Close in, AP or no AP: a turn that ends two tiles nearer is still a better
            // turn. A Shadow whose best weapon IS in reach stays put, which is what keeps an
            // archer with a good bow at range.
            if (s.moveLeft() > 0) return new Step(Kind.APPROACH, strongest);
        }
        return Step.END;
    }

    /** The first item of this kind it can pay for. */
    private static int firstAffordable(Situation s, ShadowItem.Use use) {
        for (int i = 0; i < s.items().size(); i++) {
            ShadowItem item = s.items().get(i);
            if (item.use() == use && item.apCost() <= s.apLeft()) return i;
        }
        return -1;
    }

    /** The first potion or spell it can pay for and land on the target from here. */
    private static int firstInRange(Situation s) {
        for (int i = 0; i < s.items().size(); i++) {
            ShadowItem item = s.items().get(i);
            boolean aimed = item.use() == ShadowItem.Use.DEBUFF || item.use() == ShadowItem.Use.SPELL;
            if (!aimed || item.apCost() > s.apLeft()) continue;
            if (s.distance() <= item.range() && s.lineOfSight()) return i;
        }
        return -1;
    }

    /** The first blink it can pay for that reaches a tile beside the target. */
    private static int firstBlinkThatLands(Situation s) {
        for (int i = 0; i < s.items().size(); i++) {
            ShadowItem item = s.items().get(i);
            if (item.use() != ShadowItem.Use.BLINK || item.apCost() > s.apLeft()) continue;
            if (s.distance() - 1 <= item.range()) return i;
        }
        return -1;
    }

    /** The strongest weapon that is affordable and in reach, cheaper first among equals. */
    private static int bestUsableWeapon(Situation s) {
        int best = -1;
        for (int i = 0; i < s.weapons().size(); i++) {
            ShadowWeapon w = s.weapons().get(i);
            if (w.apCost() > s.apLeft() || !w.reaches(s.distance(), s.lineOfSight())) continue;
            if (best < 0 || beats(w, s.weapons().get(best))) best = i;
        }
        return best;
    }

    private static int strongestWeapon(List<ShadowWeapon> weapons) {
        int best = -1;
        for (int i = 0; i < weapons.size(); i++) {
            if (best < 0 || beats(weapons.get(i), weapons.get(best))) best = i;
        }
        return best;
    }

    private static boolean beats(ShadowWeapon a, ShadowWeapon b) {
        if (a.power() != b.power()) return a.power() > b.power();
        return a.apCost() < b.apCost();
    }
}
