package com.crackedgames.craftics.util;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Diagnostic sweep over every static registry, looking for broken raw-id slots -
 * null holes or unbound references. Vanilla registries never contain these; a mod
 * that registers lazily at runtime, unregisters entries, or assigns raw ids by hand
 * can leave one behind, and the next full registry iteration (Fabric registry-sync's
 * unmap on disconnect is the usual victim) dies with a NullPointerException that the
 * crash report cannot attribute to any mod.
 *
 * <p>This scan names the guilty registry and the healthy entries around the broken
 * slot - their namespaces point at the culprit - BEFORE that crash happens. Runs on
 * server start (init-time damage) and server stop (runtime damage, logged moments
 * before the client-side disconnect unmap would crash).
 */
public final class RegistryHealthScanner {
    private static final Logger LOGGER = LoggerFactory.getLogger("craftics-registry-scan");

    private RegistryHealthScanner() {}

    /**
     * Sweep all static registries; log every broken slot with its neighbors.
     * Returns the number of broken slots found.
     */
    public static int scan(String phase) {
        long start = System.nanoTime();
        int broken = 0;
        int registries = 0;
        try {
            for (Identifier regId : Registries.REGISTRIES.getIds()) {
                Registry<?> reg = Registries.REGISTRIES.get(regId);
                if (reg == null) continue;
                registries++;
                broken += scanRegistry(regId, reg);
            }
        } catch (Throwable t) {
            LOGGER.warn("[{}] Registry scan aborted: {}", phase, t.toString());
            return 0;
        }
        long ms = (System.nanoTime() - start) / 1_000_000;
        if (broken > 0) {
            LOGGER.error("[{}] {} broken registry slot(s) across {} registries ({} ms). The mod "
                + "namespace next to each slot above is the likely cause of registry-sync "
                + "crashes on disconnect.", phase, broken, registries, ms);
        } else {
            // Always visible: proves the scan ran and found nothing at this moment.
            LOGGER.info("[{}] {} registries scanned, all healthy ({} ms)", phase, registries, ms);
        }
        return broken;
    }

    /**
     * Compact null raw-id slots out of every registry so a full iteration cannot
     * NPE. The holes come from Fabric registry-sync's join-time remap when the
     * server has registry entries this client lacks; Fabric's own disconnect
     * unmap then crashes iterating them. Removing the nulls is safe ONLY at
     * disconnect: the very next thing that happens is that same unmap rebuilding
     * every raw-id assignment from the stored vanilla defaults, so the shifted
     * ids this compaction causes never reach gameplay or networking.
     */
    public static void repairHoles(String phase) {
        int repaired = 0;
        try {
            for (Identifier regId : Registries.REGISTRIES.getIds()) {
                Registry<?> reg = Registries.REGISTRIES.get(regId);
                if (!(reg instanceof com.crackedgames.craftics.mixin.SimpleRegistryAccessor<?> acc)) continue;
                var rawList = acc.craftics$rawIdToEntry();
                int before = rawList.size();
                if (rawList.removeIf(java.util.Objects::isNull)) {
                    repaired += before - rawList.size();
                    LOGGER.warn("[{}] Compacted {} null raw-id slot(s) out of registry {} so the "
                        + "disconnect unmap can iterate it", phase, before - rawList.size(), regId);
                }
            }
        } catch (Throwable t) {
            LOGGER.warn("[{}] Registry hole repair aborted: {}", phase, t.toString());
            return;
        }
        if (repaired > 0) {
            LOGGER.warn("[{}] {} registry hole(s) repaired. Root cause: the server registers "
                + "entries this client doesn't have (client/server mod list or version mismatch) "
                + "- align the mod lists to fix it properly.", phase, repaired);
        }
    }

    /**
     * JOIN-time splint: fill null raw-id slots with the nearest healthy entry's Reference
     * so mid-session lookups and iterations survive. The holes come from registry-sync
     * remapping against a server whose registry holds phantom duplicate slots (e.g.
     * copperagebackport registering its armor material five times - the sync map names
     * only the last slot, and the client has nothing to put in the other four). Unlike
     * {@link #repairHoles}, this does NOT shift raw ids - every live id keeps its
     * server-aligned position, so it is safe while playing; the filler entries occupy
     * ids the server never references. Without it, equipping an item whose material
     * resolves near the holes (the copper helmet) NPE'd the wearer's client mid-render.
     */
    public static void splintHoles(String phase) {
        int splinted = 0;
        try {
            for (Identifier regId : Registries.REGISTRIES.getIds()) {
                Registry<?> reg = Registries.REGISTRIES.get(regId);
                if (!(reg instanceof com.crackedgames.craftics.mixin.SimpleRegistryAccessor<?> acc)) continue;
                var rawList = acc.craftics$rawIdToEntry();
                for (int i = 0; i < rawList.size(); i++) {
                    if (rawList.get(i) != null) continue;
                    // Nearest healthy neighbor, preferring earlier slots (stable choice).
                    var filler = pickFiller(rawList, i);
                    if (filler == null) continue;
                    fillSlot(rawList, i, filler);
                    splinted++;
                    LOGGER.warn("[{}] Splinted null raw-id slot {} of registry {} with '{}' so "
                        + "lookups/iteration can't NPE mid-session", phase, i, regId, filler.registryKey().getValue());
                }
            }
        } catch (Throwable t) {
            LOGGER.warn("[{}] Registry hole splint aborted: {}", phase, t.toString());
            return;
        }
        if (splinted > 0) {
            LOGGER.warn("[{}] {} registry hole(s) splinted. Root cause is a server-side mod "
                + "registering duplicate/phantom entries (or a client/server mod mismatch) - "
                + "fix the mod lists to remove this warning.", phase, splinted);
        }
    }

    private static net.minecraft.registry.entry.RegistryEntry.Reference<?> pickFiller(
            it.unimi.dsi.fastutil.objects.ObjectList<? extends net.minecraft.registry.entry.RegistryEntry.Reference<?>> rawList,
            int hole) {
        for (int d = 1; d < rawList.size(); d++) {
            int lo = hole - d, hi = hole + d;
            if (lo >= 0 && rawList.get(lo) != null) return rawList.get(lo);
            if (hi < rawList.size() && rawList.get(hi) != null) return rawList.get(hi);
        }
        return null;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void fillSlot(it.unimi.dsi.fastutil.objects.ObjectList rawList, int i, Object filler) {
        rawList.set(i, filler);
    }

    private static int scanRegistry(Identifier regId, Registry<?> reg) {
        // 1) The exact iteration Fabric's disconnect unmap performs. A broken slot
        //    anywhere in the raw-id list - including trailing holes past the last
        //    registered entry - kills this, so it catches everything the crash sees.
        String iterFailure = null;
        try {
            for (Object value : reg) {
                if (value == null) {
                    iterFailure = "iterator produced null";
                    break;
                }
            }
        } catch (Throwable t) {
            iterFailure = t.toString();
        }

        // 2) Slot probe with positions: walk raw ids until every registered entry
        //    has been seen - the raw list can be LARGER than size() when holes exist.
        int size;
        try {
            size = reg.size();
        } catch (Throwable t) {
            return iterFailure != null ? 1 : 0;
        }
        int broken = 0;
        int seen = 0;
        for (int i = 0; seen < size && i < size + 65536; i++) {
            String problem = null;
            Object value = null;
            try {
                value = reg.get(i);
            } catch (Throwable t) {
                // Unbound Reference.value() throws here - exactly what kills unmap.
                problem = t.toString();
            }
            if (value != null) {
                seen++;
                continue;
            }
            if (problem == null) problem = "empty slot";
            broken++;
            LOGGER.error("Registry {} raw id {} is broken ({}). Neighbors: {} | {}",
                regId, i, problem, neighbor(reg, i, -1), neighbor(reg, i, +1));
        }
        if (iterFailure != null && broken == 0) {
            // The iterator dies but every probed slot looked fine: trailing hole or
            // exotic corruption. Still name the registry - it IS the crash source.
            broken = 1;
            LOGGER.error("Registry {} cannot be fully iterated ({}) - this registry is "
                + "what kills the disconnect unmap. Last healthy entry: {}",
                regId, iterFailure, neighbor(reg, size, -1));
        } else if (iterFailure != null) {
            LOGGER.error("Registry {} full iteration also fails: {}", regId, iterFailure);
        }
        return broken;
    }

    /** Closest healthy entry id in the given direction - its namespace points at the culprit. */
    private static String neighbor(Registry<?> reg, int from, int step) {
        for (int i = from + step; i >= 0 && i < reg.size(); i += step) {
            try {
                Object value = reg.get(i);
                if (value != null) {
                    Identifier id = idOf(reg, value);
                    return (id != null ? id.toString() : "?") + " (raw " + i + ")";
                }
            } catch (Throwable ignored) {}
        }
        return "none";
    }

    @SuppressWarnings("unchecked")
    private static Identifier idOf(Registry<?> reg, Object value) {
        return ((Registry<Object>) reg).getId(value);
    }
}
