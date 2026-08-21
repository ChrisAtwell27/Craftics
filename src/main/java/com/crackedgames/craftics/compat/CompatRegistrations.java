package com.crackedgames.craftics.compat;

/**
 * The one list of deferred compat registrations, called from every place that needs them.
 *
 * <p>Compat weapons and armor cannot be registered during our own main entrypoint: they need
 * the actual {@code Item} instances from another mod, and Fabric gives no ordering guarantee
 * that the other mod's entrypoint has run by then. So each compat has a {@code registerDeferred}
 * that finishes the job once every mod's main phase is over, and each is a no-op after the
 * first call.
 *
 * <p><b>Why this class exists.</b> There are three moments that qualify as "late enough", and
 * they do not overlap:
 *
 * <ul>
 *   <li>{@code SERVER_STARTING} - the only one a dedicated server reaches.</li>
 *   <li>{@code CLIENT_STARTED} - the only one a multiplayer client reaches. A client connected
 *       to someone else's world never sees a server lifecycle event, so without this its
 *       registries stay empty and every compat tooltip renders blank.</li>
 *   <li>The first tooltip render - the backstop for anything that slipped both, such as a
 *       resource-pack reload before a world is loaded.</li>
 * </ul>
 *
 * <p>Each of those used to keep its own hand-written list of compats to call, and they drifted:
 * instruments and paladins were on the server list and neither client one, and simply-swords was
 * on one client list but not the other. The symptom was the worst kind - a single-player host saw
 * full stats on everything because their client shares a JVM with the server, and everyone else
 * on the same world saw none, so it looked like a permissions bug rather than a missing call.
 *
 * <p>Adding a compat means adding it <b>here, once</b>. A new one that forgets a call site is
 * how this happened the first time.
 */
public final class CompatRegistrations {

    private CompatRegistrations() {}

    /**
     * Run every compat's deferred registration.
     *
     * <p>Safe to call repeatedly and from either side: each implementation guards on its own
     * "already registered" flag and on whether the mod it targets is present at all.
     */
    public static void registerAllDeferred() {
        run("copper age", com.crackedgames.craftics.compat.copperagebackport.CopperAgeCompat::registerDeferred);
        run("basic weapons", com.crackedgames.craftics.compat.basicweapons.BasicWeaponsCompat::registerDeferred);
        run("instruments", com.crackedgames.craftics.compat.instruments.InstrumentsCompat::registerDeferred);
        run("paladins", com.crackedgames.craftics.compat.paladins.PaladinsCompat::registerDeferred);
        run("simply swords", com.crackedgames.craftics.compat.simplyswords.SimplySwordsCompat::registerDeferred);
        run("immersive armors", com.crackedgames.craftics.compat.immersivearmors.ImmersiveArmorsCompat::registerDeferred);
        run("simply bows", com.crackedgames.craftics.compat.simplybows.SimplyBowsCompat::registerDeferred);
        run("deeper and darker", com.crackedgames.craftics.compat.deeperanddarker.DeeperAndDarkerCompat::registerDeferred);
    }

    /**
     * Run one compat's registration, and let the others run whatever it does.
     *
     * <p>Without this the list is only as good as its unluckiest member: these resolve items
     * out of another mod's registry, and a mod that renamed an id between versions turns a
     * lookup into a throw. One throw here used to take out every compat listed after it, so a
     * bad id in an early module made unrelated mods' weapons lose their stats - and because
     * this also runs from the tooltip render, it would keep doing so on every frame the player
     * hovered anything.
     *
     * <p>Logged once per compat rather than per attempt: this is called from a render path, and
     * a per-frame stack trace is its own kind of broken.
     */
    private static void run(String name, Runnable registration) {
        if (FAILED.contains(name)) return;
        try {
            registration.run();
        } catch (Throwable t) {
            FAILED.add(name);
            com.crackedgames.craftics.CrafticsMod.LOGGER.error(
                "[Craftics] compat registration for {} failed - its items will have no Craftics "
                + "stats, but every other compat is unaffected", name, t);
        }
    }

    /** Compats whose registration threw. Kept so the failure is reported once, not per frame. */
    private static final java.util.Set<String> FAILED =
        java.util.Collections.synchronizedSet(new java.util.HashSet<>());
}
