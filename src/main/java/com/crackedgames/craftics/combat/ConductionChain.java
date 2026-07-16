package com.crackedgames.craftics.combat;

import com.crackedgames.craftics.core.GridPos;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * The Tidecaller's Conduction chain walk, extracted Minecraft-free so the spacing rules
 * are unit-testable without a bootstrap.
 *
 * <p>Lightning strikes one marked combatant, then arcs outward: everything within
 * {@code linkRange} tiles of the LAST combatant struck conducts - not everything near the
 * origin. That distinction is the whole mechanic: a conga line of drowned carries the bolt
 * across the arena one jump at a time, and spreading out breaks the chain. It mirrors the
 * Guster sherd's chain-lightning BFS so the game's two chain effects read as the same
 * physics.
 *
 * <p>The caller owns exclusions (the Tidecaller itself, the dead): anything that must not
 * conduct simply is not in the position list.
 */
public final class ConductionChain {

    private ConductionChain() {}

    /** One struck combatant: its index into the caller's combatant list and its jump depth
     *  (0 = the marked combatant). */
    public record Link(int index, int depth) {}

    /**
     * Breadth-first walk from {@code startIndex} over the given combatant positions.
     * Returns the links in strike order, each with the number of jumps it took the bolt
     * to reach it.
     *
     * @return links in strike order; empty if {@code startIndex} is out of range
     */
    public static List<Link> walk(List<GridPos> positions, int startIndex, int linkRange) {
        List<Link> chain = new ArrayList<>();
        if (startIndex < 0 || startIndex >= positions.size()) return chain;
        boolean[] hit = new boolean[positions.size()];
        Deque<Link> queue = new ArrayDeque<>();
        Link seed = new Link(startIndex, 0);
        hit[startIndex] = true;
        queue.add(seed);
        chain.add(seed);
        while (!queue.isEmpty()) {
            Link current = queue.poll();
            GridPos from = positions.get(current.index());
            for (int i = 0; i < positions.size(); i++) {
                if (hit[i]) continue;
                if (positions.get(i).manhattanDistance(from) <= linkRange) {
                    hit[i] = true;
                    Link next = new Link(i, current.depth() + 1);
                    queue.add(next);
                    chain.add(next);
                }
            }
        }
        return chain;
    }

    /** Damage for a link: decays 1 per jump, floored at 1 so every link stings. Matches
     *  the Guster sherd's per-jump decay so the two chains feel like the same rule. */
    public static int damageAt(int baseDamage, int depth) {
        return Math.max(1, baseDamage - depth);
    }
}
