package com.crackedgames.craftics.api.registry;

import com.crackedgames.craftics.api.ProjectileImpactHandler;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Impact handlers for boss projectile types Craftics does not resolve itself.
 *
 * <p>Keyed by the {@code projectileType} string a boss passes to
 * {@code EnemyAction.SpawnProjectile}. The built-in types never reach this registry, so a
 * handler cannot replace one of them; pick a namespaced-looking name of your own
 * ({@code "mymod_ice_bolt"}).
 *
 * @since 0.4.9
 */
public final class ProjectileImpactRegistry {

    private ProjectileImpactRegistry() {}

    private static final Map<String, ProjectileImpactHandler> HANDLERS = new ConcurrentHashMap<>();
    private static final Set<String> DEFLECTABLE = ConcurrentHashMap.newKeySet();

    /**
     * @param deflectable whether a player's hit knocks the projectile back instead of
     *                    destroying it (see {@link ProjectileImpactHandler#onDeflect})
     */
    public static void register(String projectileType, ProjectileImpactHandler handler,
                                boolean deflectable) {
        if (projectileType == null || handler == null) return;
        HANDLERS.put(projectileType, handler);
        if (deflectable) DEFLECTABLE.add(projectileType);
        else DEFLECTABLE.remove(projectileType);
    }

    /** The handler for {@code projectileType}, or null. */
    public static ProjectileImpactHandler get(String projectileType) {
        return projectileType == null ? null : HANDLERS.get(projectileType);
    }

    public static boolean isDeflectable(String projectileType) {
        return projectileType != null && DEFLECTABLE.contains(projectileType);
    }

    /** Clear every registration. Test hook. */
    public static void clear() {
        HANDLERS.clear();
        DEFLECTABLE.clear();
    }
}
