package com.crackedgames.craftics.combat.sherd;

import com.crackedgames.craftics.core.GridPos;

import java.util.ArrayList;
import java.util.List;

/**
 * What a cast did, accumulated as it runs.
 *
 * <p>Every sherd used to build its own chat line with a {@code StringBuilder} interleaved
 * through its game-state code, and separately collect its own {@code List<BlockPos>} of places
 * to draw particles at. Both are bookkeeping that has nothing to do with what the spell means,
 * and doing them by hand is why adding an effect to a sherd meant editing its message too.
 * Effects now report what they did and the engine writes the sentence.
 *
 * <p>Hit tiles are recorded rather than block positions so the report stays arena-relative;
 * the engine converts once, at the point it draws.
 */
public final class SpellReport {

    private final List<String> fragments = new ArrayList<>();
    private final List<GridPos> hitTiles = new ArrayList<>();
    private int totalDamage = 0;
    private int entitiesAffected = 0;

    /** Add a clause to the cast's chat line, e.g. {@code "Husk takes 12 damage"}. */
    public void say(String fragment) {
        if (fragment != null && !fragment.isEmpty()) fragments.add(fragment);
    }

    /** Note that something happened at a tile, so the engine can draw an impact there. */
    public void hit(GridPos tile) {
        if (tile != null) hitTiles.add(tile);
    }

    public void addDamage(int amount) {
        totalDamage += amount;
        if (amount > 0) entitiesAffected++;
    }

    public List<String> fragments() { return fragments; }
    public List<GridPos> hitTiles() { return hitTiles; }
    public int totalDamage() { return totalDamage; }
    public int entitiesAffected() { return entitiesAffected; }
    public boolean isEmpty() { return fragments.isEmpty(); }
}
