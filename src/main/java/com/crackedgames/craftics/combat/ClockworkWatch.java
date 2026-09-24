package com.crackedgames.craftics.combat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The Clockwork chestplate's memory: one per player per fight. Minecraft free so the whole rule
 * is unit-testable without a bootstrap. CombatManager feeds it the actions the wearer takes,
 * tagged with the combat round they were taken in, and asks it whether the next attack is doubled.
 *
 * <p>The rule: the watch remembers the FIRST {@value #SEQUENCE_LENGTH} actions of the wearer's
 * first turn in the fight. If their second turn opens with the same actions in the same order,
 * the watch strikes and their next attack deals {@value #DAMAGE_MULT}x. Only the first three
 * actions of each turn count - anything after them is ignored - and a first turn with fewer than
 * three actions leaves the watch unwound. One chance per fight: a broken sequence jams it.
 *
 * <p>Turns are read off the combat ROUND rather than counted here. Every party member gets
 * exactly one turn per round and rounds start at 1, so round 1 is everyone's first turn and
 * round 2 their second - no turn-start hook has to remember to call in, including the opening
 * turn, which starts without passing through the per-turn reset.
 */
public final class ClockworkWatch {

    /** How many opening actions the watch remembers, and then expects repeated. */
    public static final int SEQUENCE_LENGTH = 3;
    /** Damage multiplier on the attack a struck watch empowers. */
    public static final int DAMAGE_MULT = 2;

    /**
     * The kind of action the watch saw. Coarse on purpose: an attack is an attack whatever it
     * targeted, and a move is a move however far it went. What has to repeat is the rhythm.
     */
    public enum Action {
        MOVE("Move"),
        ATTACK("Attack"),
        ITEM("Item"),
        MINE("Mine"),
        COMMAND("Command"),
        STOMP("Stomp"),
        MOUNT("Mount");

        private final String label;

        Action(String label) { this.label = label; }

        /** Chat label, e.g. "Attack". */
        public String label() { return label; }
    }

    /** What recording one action did, so the caller can tell the player. */
    public enum Result {
        /** Nothing: outside the two watched turns, past the first three actions, or the watch is done. */
        IGNORED,
        /** First turn: remembered, with fewer than three so far. */
        RECORDED,
        /** First turn: the third action - the sequence is set. */
        WOUND,
        /** Second turn: the first turn never reached three actions, so there is nothing to repeat. */
        UNWOUND,
        /** Second turn: matched the remembered action at this position. */
        MATCHED,
        /** Second turn: the third match - the next attack is doubled. */
        STRUCK,
        /** Second turn: broke the sequence. The watch is done for this fight. */
        JAMMED
    }

    private final List<Action> remembered = new ArrayList<>(SEQUENCE_LENGTH);
    private int matched = 0;
    private boolean done = false;
    private boolean primed = false;

    /**
     * Log one action the wearer actually took.
     *
     * @param round  the combat round it was taken in (1 = the first turn of the fight)
     * @param action what kind of action it was
     * @return what the action did to the watch
     */
    public Result record(int round, Action action) {
        if (done || action == null) return Result.IGNORED;
        if (round <= 1) {
            if (remembered.size() >= SEQUENCE_LENGTH) return Result.IGNORED;
            remembered.add(action);
            return remembered.size() == SEQUENCE_LENGTH ? Result.WOUND : Result.RECORDED;
        }
        if (round == 2) {
            // "Any less, and it won't work": a first turn of one or two actions set nothing.
            if (remembered.size() < SEQUENCE_LENGTH) {
                done = true;
                return Result.UNWOUND;
            }
            if (action != remembered.get(matched)) {
                done = true;
                return Result.JAMMED;
            }
            matched++;
            if (matched == SEQUENCE_LENGTH) {
                done = true;
                primed = true;
                return Result.STRUCK;
            }
            return Result.MATCHED;
        }
        // Round 3 onward: the second turn is over. A primed strike still waits for its attack.
        done = true;
        return Result.IGNORED;
    }

    /** Spend the doubled attack. True exactly once, and only after the watch struck. */
    public boolean consumeStrike() {
        if (!primed) return false;
        primed = false;
        return true;
    }

    /** Whether the watch struck and the doubled attack is still waiting to be spent. */
    public boolean isPrimed() { return primed; }

    /** Second-turn actions matched so far. */
    public int matched() { return matched; }

    /** The remembered opening, in order. */
    public List<Action> remembered() { return Collections.unmodifiableList(remembered); }

    /** "Move → Attack → Attack", for chat. */
    public static String describe(List<Action> actions) {
        StringBuilder sb = new StringBuilder();
        for (Action a : actions) {
            if (sb.length() > 0) sb.append(" → ");
            sb.append(a.label());
        }
        return sb.toString();
    }
}
