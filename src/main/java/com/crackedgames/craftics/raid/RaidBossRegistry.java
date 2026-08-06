package com.crackedgames.craftics.raid;

import com.crackedgames.craftics.CrafticsMod;
import com.crackedgames.craftics.combat.ai.AIRegistry;
import com.crackedgames.craftics.combat.ai.boss.RaidBossAI;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The loaded raid boss definitions, plus their AI registration.
 *
 * <p>Each definition registers a stateful boss AI under {@code
 * boss:raidboss/<id>} so CombatManager's existing boss-spawn path resolves a
 * fresh RaidBossAI per fight; two concurrent instances of the same boss must not
 * share cooldowns or phase state.
 */
public final class RaidBossRegistry {
    private RaidBossRegistry() {}

    private static final Map<String, RaidBossDefinition> DEFS = new LinkedHashMap<>();

    /** Copy bundled examples if needed, then re-read the config directory. */
    public static void reload(MinecraftServer server) {
        RaidBossJsonLoader.copyBundledIfAbsent(server);
        DEFS.clear();
        for (RaidBossDefinition def : RaidBossJsonLoader.loadAll()) {
            put(def);
        }
        CrafticsMod.LOGGER.info("Raid bosses loaded: {}", DEFS.size());
    }

    /** Register (or replace) one definition and its AI factory. */
    public static void put(RaidBossDefinition def) {
        DEFS.put(def.id(), def);
        AIRegistry.registerBoss(def.bossAiRegistryKey(), () -> new RaidBossAI(def));
    }

    public static void remove(String id) {
        DEFS.remove(id);
    }

    public static RaidBossDefinition get(String id) {
        return id == null ? null : DEFS.get(id);
    }

    public static List<RaidBossDefinition> all() {
        List<RaidBossDefinition> out = new ArrayList<>(DEFS.values());
        out.sort(Comparator.comparing(RaidBossDefinition::id));
        return out;
    }

    public static List<RaidBossRotation.Candidate> candidates() {
        List<RaidBossRotation.Candidate> out = new ArrayList<>();
        for (RaidBossDefinition def : all()) {
            out.add(new RaidBossRotation.Candidate(def.id(), def.weight()));
        }
        return out;
    }
}
