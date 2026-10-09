package com.crackedgames.craftics.level;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.CombatManager;
import com.crackedgames.craftics.combat.MobTrait;
import com.crackedgames.craftics.combat.MobTraitCatalog;
import com.crackedgames.craftics.combat.MobTraits;
import com.crackedgames.craftics.combat.ai.AIRegistry;
import com.crackedgames.craftics.combat.ai.boss.BossAI;
import com.crackedgames.craftics.network.MobTraitCatalogPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds and pushes the bestiary's trait table.
 *
 * <p>Like the biome atlas, nothing here is authored: every row is worked out from the mobs
 * the game can actually field, by the same {@link MobTraits} resolution a fight uses. The
 * bestiary's hand-written pages can drift from the code; its trait row cannot.
 *
 * <p>Entries are named exactly as {@code CombatManager.unlockBestiaryForCombat} names them -
 * {@link CombatManager#entityTypeIdToMobName} for an ordinary mob,
 * {@link CombatManager#getBossName} for a biome's boss - so the client can look a bestiary
 * entry up by the name it already has, with no mapping of its own to get wrong.
 */
public final class MobTraitSync {
    private MobTraitSync() {}

    /** Send the trait table to one player. */
    public static void send(ServerPlayerEntity player) {
        if (player == null) return;
        ServerPlayNetworking.send(player,
            new MobTraitCatalogPayload(MobTraitCatalog.encode(build())));
    }

    /** Re-send to everyone. Called after a datapack reload, which can change the roster. */
    public static void sendToAll(MinecraftServer server) {
        if (server == null) return;
        String encoded = MobTraitCatalog.encode(build());
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(player, new MobTraitCatalogPayload(encoded));
        }
    }

    /** Bestiary entry name to traits, for every mob the game knows how to field. */
    static Map<String, List<MobTrait>> build() {
        Map<String, List<MobTrait>> out = new LinkedHashMap<>();

        // Ordinary mobs: everything with an AI or a declared trait, plus every pool entry.
        // The registries reach mobs no biome pool lists (summons, event and trial spawns).
        for (String key : AIRegistry.registeredKeys()) {
            if (isEntityTypeKey(key)) addMob(out, key, key);
        }
        for (String key : MobTraits.declaredKeys()) {
            if (isEntityTypeKey(key)) addMob(out, key, key);
        }
        for (BiomeTemplate registered : BiomeRegistry.getAllBiomes()) {
            BiomeTemplate biome = registered.withPreludeFolded();
            addPool(out, biome.hostileMobs);
            addPool(out, biome.passiveMobs);
        }

        // Bosses last, so a boss entry is never merged into a same-named mob entry.
        for (BiomeTemplate biome : BiomeRegistry.getAllBiomes()) {
            if (biome.boss == null || biome.boss.entityTypeId() == null) continue;
            String name = CombatManager.getBossName(biome.biomeId);
            if ("Boss".equals(name)) continue; // no bestiary entry is keyed by the fallback
            String type = biome.boss.entityTypeId();
            String aiKey = "boss:" + biome.biomeId;
            boolean large = AIRegistry.get(aiKey) instanceof BossAI bossAi
                ? bossAi.getGridSize() > 1 : isLarge(type);
            List<MobTrait> traits = MobTraits.forType(type, aiKey, true, isFireImmune(type), large);
            if (!traits.isEmpty()) out.put(name, traits);
        }
        return out;
    }

    private static void addPool(Map<String, List<MobTrait>> out, MobPoolEntry[] pool) {
        if (pool == null) return;
        for (MobPoolEntry mob : pool) {
            if (mob == null || mob.entityTypeId() == null) continue;
            addMob(out, mob.entityTypeId(), mob.aiKey());
        }
    }

    /**
     * Two entity types can share a bestiary name (vanilla's Creaking and the backport's), and
     * one type can appear under several AI keys. The entry is the union, since the bestiary
     * page is about all of them.
     */
    private static void addMob(Map<String, List<MobTrait>> out, String type, String aiKey) {
        List<MobTrait> traits = MobTraits.forType(type, aiKey, false, isFireImmune(type), isLarge(type));
        if (traits.isEmpty()) return;
        out.merge(CombatManager.entityTypeIdToMobName(type), traits, MobTraitCatalog::union);
    }

    /** An entity type id, as opposed to a boss key or an internal AI key like "projectile". */
    private static boolean isEntityTypeKey(String key) {
        return key != null && key.indexOf(':') > 0 && !key.startsWith("boss:");
    }

    private static boolean isFireImmune(String entityTypeId) {
        Identifier id = Identifier.tryParse(entityTypeId);
        return id != null && Registries.ENTITY_TYPE.containsId(id)
            && Registries.ENTITY_TYPE.get(id).isFireImmune();
    }

    private static boolean isLarge(String entityTypeId) {
        int[] fp = CombatEntity.getDefaultFootprint(entityTypeId);
        return fp[0] > 1 || fp[1] > 1;
    }
}
