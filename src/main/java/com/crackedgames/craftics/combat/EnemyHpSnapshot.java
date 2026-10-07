package com.crackedgames.craftics.combat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * The health of every enemy at one moment, so an action can be asked afterwards whether it
 * hurt any of them.
 *
 * <p>This exists for the actions that have no single place saying "that was an attack". A weapon
 * swing is always an attack and breaks tall-grass cover the moment it is committed. A used item
 * is not: a snowball, a splash potion and a loaf of bread all go through the same call, and a
 * sherd can be a fireball or a heal. Rather than keep a list of which items count and forget
 * the next one added, take this before the action and ask {@link #anyHurt} after it.
 *
 * <p>Compared mob by mob, never as a total, so one enemy healing cannot hide another being hit.
 * Absorption is counted with health: a blow soaked by golden hearts still landed.
 */
public final class EnemyHpSnapshot {

    private final List<CombatEntity> seen = new ArrayList<>();
    private final List<Integer> healthThen = new ArrayList<>();

    private EnemyHpSnapshot() {}

    /** Record every living enemy in {@code combatants}. Allies and the already dead are left out. */
    public static EnemyHpSnapshot of(Collection<CombatEntity> combatants) {
        EnemyHpSnapshot snapshot = new EnemyHpSnapshot();
        if (combatants == null) return snapshot;
        for (CombatEntity e : combatants) {
            if (e == null || !e.isAlive() || e.isAlly()) continue;
            snapshot.seen.add(e);
            snapshot.healthThen.add(health(e));
        }
        return snapshot;
    }

    /** True when an enemy recorded here has since died or lost health or absorption. */
    public boolean anyHurt() {
        for (int i = 0; i < seen.size(); i++) {
            CombatEntity e = seen.get(i);
            if (!e.isAlive() || health(e) < healthThen.get(i)) return true;
        }
        return false;
    }

    private static int health(CombatEntity e) {
        return e.getCurrentHp() + e.getAbsorption();
    }
}
