package com.crackedgames.craftics.api;

import com.crackedgames.craftics.combat.CombatEffects;
import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.CombatManager;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;
import net.minecraft.server.world.ServerWorld;

import java.util.List;

/**
 * What a boss projectile does when it lands, for projectile types Craftics does not know.
 *
 * <p>A boss launches a projectile with {@code EnemyAction.SpawnProjectile}, naming a
 * {@code projectileType}. Craftics moves it across the grid, lets the player hit it, and
 * calls the handler registered for that type when it reaches something. The built-in types
 * (ghast fireball, wither skull, shulker bullet) are resolved inside {@code CombatManager};
 * this is the same hook for everything else. The projectile is removed after the handler
 * returns - the handler decides what the impact does, not whether there was one.
 *
 * <p>Register with
 * {@link com.crackedgames.craftics.api.registry.ProjectileImpactRegistry#register}.
 *
 * @since 0.4.9
 */
@FunctionalInterface
public interface ProjectileImpactHandler {

    void onImpact(Context ctx);

    /**
     * A projectile the player has struck, for a type registered as deflectable.
     *
     * <p>The default sends it back the way it came, like a ghast fireball: it keeps flying,
     * now away from the player, and {@link #onImpact} is called with
     * {@link Context#redirected()} true when it reaches something. Override to resolve the
     * strike on the spot instead - return true and the projectile is spent.
     *
     * @return true if the strike has been fully handled and the projectile should be removed
     */
    default boolean onDeflect(Context ctx) {
        return false;
    }

    /** The impact, and the things a handler may do about it. */
    interface Context {
        /** The projectile itself: its attack power is the damage it was launched with. */
        CombatEntity projectile();

        /** The tile it struck, or the tile it stopped on when it hit a wall. */
        GridPos impactPos();

        /** True once a player has hit it back. A redirected projectile no longer hurts players. */
        boolean redirected();

        GridArena arena();

        ServerWorld world();

        /** The fight. For anything the helpers below do not cover. */
        CombatManager combat();

        /** Living enemies (not allies, not other projectiles) within {@code radius} of the impact. */
        List<CombatEntity> enemiesNear(int radius);

        /**
         * Damage every party member within {@code radius} of the impact, and leave an effect
         * on each one actually hurt. Goes through dodge, armor and the death check like any
         * other boss hit.
         *
         * @param effect  an effect to apply to each victim, or null for none
         * @param message chat line per victim, with {@code %s} for "you"/the name and
         *                {@code %d} for the damage dealt; null for none
         * @return true if this ended the fight. The handler must return immediately.
         */
        boolean hitPlayers(int radius, int damage, CombatEffects.EffectType effect,
                           int turns, int amplifier, String message);

        /** Damage an enemy and book the kill if it dies. Honours a guard; see {@link #pierce}. */
        int damage(CombatEntity enemy, int amount);

        /** Like {@link #damage}, but lands on a combatant holding a guard. */
        int pierce(CombatEntity enemy, int amount);

        void message(String text);
    }
}
