package com.crackedgames.craftics.combat;

import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;

import java.util.UUID;

/**
 * A timed trap laid on an arena tile.
 *
 * <p>Craftics' older ground effects ({@code tileEffects}) are a bare
 * {@code Map<GridPos,String>} that lives until combat ends - honey and slime are consumed
 * on contact, everything else is permanent. A trap needs more: it expires on its own, it
 * marks itself with particles every turn so the player can see where it is, and it fires
 * a specific effect on whoever walks into it.
 *
 * <p>Traps are stored in {@code CombatManager}'s trap map and driven from three places:
 * {@code tickTileTraps} re-emits their particles and ages them once per round,
 * {@code tickEnemyMoving} fires them when an enemy arrives, and {@code endCombat} clears
 * them. They live alongside {@code tileEffects} rather than inside it, because a tile can
 * hold both (a trap laid on a campfire tile still heals).
 *
 * @param kind      what the trap does when something steps in it
 * @param turnsLeft rounds remaining before it fades; decremented once per round
 * @param owner     the player who laid it, so its healing finds the right party
 */
public record TileTrap(Kind kind, int turnsLeft, UUID owner) {

    /** How long a freshly laid trap of each kind lasts, in rounds. */
    public static final int FLOWER_FIELD_TURNS = 4;
    public static final int BUBBLE_COLUMN_TURNS = 3;

    /** Damage an enemy takes from ending its move on a flower field tile. */
    public static final int FLOWER_FIELD_DAMAGE = 3;
    /** Health the flower field restores to the player or an ally standing in it. */
    public static final int FLOWER_FIELD_HEAL = 2;
    /** Turns of Confusion the flower field's pollen inflicts on an enemy standing in it. */
    public static final int FLOWER_FIELD_CONFUSION_TURNS = 1;
    /** Tiles a bubble column launches whatever triggers it. */
    public static final int BUBBLE_COLUMN_LAUNCH = 1;
    /** Turns of Soaked a bubble column applies. */
    public static final int BUBBLE_COLUMN_SOAK_TURNS = 2;

    public enum Kind {
        /**
         * Everbloom's flower field. Persistent: it hurts and bewilders every enemy that
         * ends a move on it, and mends the party standing in it, for as long as it lasts.
         */
        FLOWER_FIELD(() -> ParticleTypes.HAPPY_VILLAGER, () -> ParticleTypes.FALLING_NECTAR, false),

        /**
         * Bubbleveil's bubble column. One-shot: the first enemy to step in it is soaked
         * and launched, and the column pops.
         */
        BUBBLE_COLUMN(() -> ParticleTypes.BUBBLE_COLUMN_UP, () -> ParticleTypes.SPLASH, true);

        // Held as suppliers, not values: touching ParticleTypes runs Minecraft's registry
        // bootstrap, and this enum has to be readable from a plain unit test.
        private final java.util.function.Supplier<ParticleEffect> marker;
        private final java.util.function.Supplier<ParticleEffect> accent;
        private final boolean consumedOnTrigger;

        Kind(java.util.function.Supplier<ParticleEffect> marker,
             java.util.function.Supplier<ParticleEffect> accent, boolean consumedOnTrigger) {
            this.marker = marker;
            this.accent = accent;
            this.consumedOnTrigger = consumedOnTrigger;
        }

        /** The particle that marks this trap's tile every round. */
        public ParticleEffect marker() { return marker.get(); }

        /** A second particle, sprinkled more thinly, so the marker reads as more than noise. */
        public ParticleEffect accent() { return accent.get(); }

        /** True if triggering the trap destroys it. */
        public boolean consumedOnTrigger() { return consumedOnTrigger; }

        /** How many rounds a fresh trap of this kind lasts. */
        public int duration() {
            return this == FLOWER_FIELD ? FLOWER_FIELD_TURNS : BUBBLE_COLUMN_TURNS;
        }
    }

    /** A newly laid trap of {@code kind}, at its full duration. */
    public static TileTrap fresh(Kind kind, UUID owner) {
        return fresh(kind, owner, 0);
    }

    /**
     * A newly laid trap that lingers {@code bonusTurns} beyond its usual span - what a
     * lucky wielder's traps do.
     */
    public static TileTrap fresh(Kind kind, UUID owner, int bonusTurns) {
        return new TileTrap(kind, kind.duration() + Math.max(0, bonusTurns), owner);
    }

    /** This trap one round older. {@link #expired()} once its turns run out. */
    public TileTrap aged() {
        return new TileTrap(kind, turnsLeft - 1, owner);
    }

    public boolean expired() {
        return turnsLeft <= 0;
    }
}
