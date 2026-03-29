package com.crackedgames.craftics.combat;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;

import java.util.*;

/**
 * Per-player progression data, stored by UUID for multiplayer compatibility.
 * Each player has a level, stat points, and allocated stats.
 */
public class PlayerProgression extends PersistentState {

    /**
     * Stats that can be upgraded on level-up.
     */
    public enum Stat {
        SPEED("Speed", "§b⚡", "+1 movement per turn", 3),
        AP("Action Points", "§e⚡", "+1 action per turn", 3),
        MELEE_POWER("Melee Power", "§c⚔", "+1 melee damage", 0),
        RANGED_POWER("Ranged Power", "§d\uD83C\uDFF9", "+1 ranged damage", 0),
        VITALITY("Vitality", "§a❤", "+2 max HP", 0),
        DEFENSE("Defense", "§9\uD83D\uDEE1", "+1 damage reduction", 0),
        LUCK("Luck", "§6✦", "+1 loot & crit chance", 0),
        RESOURCEFUL("Resourceful", "§2\uD83D\uDCB0", "+1 emerald/level & trader discount", 0);

        public final String displayName;
        public final String icon;
        public final String description;
        public final int baseValue;

        Stat(String displayName, String icon, String description, int baseValue) {
            this.displayName = displayName;
            this.icon = icon;
            this.description = description;
            this.baseValue = baseValue;
        }
    }

    /**
     * Per-player stat data.
     */
    /** Damage affinity types that players can upgrade on level-up. */
    public enum Affinity {
        SWORD("Sword", "\u00a7c\u2694", "+1 Sword damage"),
        CLEAVING("Cleaving", "\u00a76\u2716", "+1 Cleaving damage"),
        BLUNT("Blunt", "\u00a78\u2B24", "+1 Blunt damage"),
        RANGED("Ranged", "\u00a7b\u27B3", "+1 Ranged damage"),
        WATER("Water", "\u00a73\u2248", "+1 Water damage"),
        MAGIC("Magic", "\u00a7d\u2728", "+1 Magic damage"),
        PHYSICAL("Physical", "\u00a77\u270A", "+1 Physical damage");

        public final String displayName;
        public final String icon;
        public final String description;

        Affinity(String displayName, String icon, String description) {
            this.displayName = displayName;
            this.icon = icon;
            this.description = description;
        }
    }

    public static class PlayerStats {
        public int level = 1;
        public int unspentPoints = 0;
        private final EnumMap<Stat, Integer> statPoints = new EnumMap<>(Stat.class);
        // Boss kill counts per biome ID — used for diminishing level-up returns
        private final Map<String, Integer> bossKills = new HashMap<>();
        // Permanent damage affinity bonuses from level-ups
        private final EnumMap<Affinity, Integer> affinityPoints = new EnumMap<>(Affinity.class);
        public boolean pendingAffinityChoice = false; // set after stat choice, cleared after affinity choice

        public PlayerStats() {
            for (Stat s : Stat.values()) {
                statPoints.put(s, 0);
            }
            for (Affinity a : Affinity.values()) {
                affinityPoints.put(a, 0);
            }
        }

        public int getAffinityPoints(Affinity affinity) {
            return affinityPoints.getOrDefault(affinity, 0);
        }

        public void allocateAffinity(Affinity affinity) {
            affinityPoints.put(affinity, getAffinityPoints(affinity) + 1);
            pendingAffinityChoice = false;
        }

        /** Record a boss kill for a biome and return true if this kill earns a level-up. */
        public boolean recordBossKillAndCheckLevelUp(String biomeId) {
            int kills = bossKills.getOrDefault(biomeId, 0) + 1;
            bossKills.put(biomeId, kills);

            // Threshold: 1st kill = level up. Then need 2 more (total 3), then 4 more (total 7), etc.
            // Kills needed for level N from this boss: 2^(N-1) where N starts at 1
            // Total kills for level N: 2^0 + 2^1 + ... + 2^(N-1) = 2^N - 1
            // So we level up when kills equals a value of form 2^N - 1: 1, 3, 7, 15, ...
            int threshold = 1;
            int totalNeeded = 0;
            while (totalNeeded + threshold <= kills) {
                totalNeeded += threshold;
                if (totalNeeded == kills) return true;
                threshold *= 2;
            }
            return false;
        }

        public int getBossKills(String biomeId) {
            return bossKills.getOrDefault(biomeId, 0);
        }

        /** Get kills needed for next level-up from this boss. */
        public int getKillsUntilNextLevel(String biomeId) {
            int kills = bossKills.getOrDefault(biomeId, 0);
            int threshold = 1;
            int totalNeeded = 0;
            while (totalNeeded + threshold <= kills) {
                totalNeeded += threshold;
                threshold *= 2;
            }
            return totalNeeded + threshold - kills;
        }

        public int getPoints(Stat stat) {
            return statPoints.getOrDefault(stat, 0);
        }

        public int getEffective(Stat stat) {
            return stat.baseValue + getPoints(stat);
        }

        public boolean allocatePoint(Stat stat) {
            if (unspentPoints <= 0) return false;
            statPoints.put(stat, getPoints(stat) + 1);
            unspentPoints--;
            return true;
        }

        /** Admin: directly set the allocated points for a stat. */
        public void setPoints(Stat stat, int value) {
            statPoints.put(stat, Math.max(0, value));
        }

        public void grantLevelUp() {
            level++;
            unspentPoints++;
        }

        // Serialize: "level:unspent:s0:s1:...:s7|bossKills|affinities"
        public String serialize() {
            StringBuilder sb = new StringBuilder();
            sb.append(level).append(':').append(unspentPoints);
            for (Stat s : Stat.values()) {
                sb.append(':').append(getPoints(s));
            }
            // Section 2: boss kill data
            sb.append('|');
            {
                boolean first = true;
                for (Map.Entry<String, Integer> e : bossKills.entrySet()) {
                    if (!first) sb.append(',');
                    sb.append(e.getKey()).append('=').append(e.getValue());
                    first = false;
                }
            }
            // Section 3: affinity points
            sb.append('|');
            {
                boolean first = true;
                for (Affinity a : Affinity.values()) {
                    int pts = getAffinityPoints(a);
                    if (pts > 0) {
                        if (!first) sb.append(',');
                        sb.append(a.name()).append('=').append(pts);
                        first = false;
                    }
                }
            }
            return sb.toString();
        }

        public static PlayerStats deserialize(String data) {
            PlayerStats ps = new PlayerStats();
            // Split into sections: stats | bossKills | affinities
            String[] sections = data.split("\\|", -1);
            String statPart = sections[0];
            String bossPart = sections.length > 1 ? sections[1] : "";
            String affinityPart = sections.length > 2 ? sections[2] : "";

            String[] parts = statPart.split(":");
            if (parts.length >= 2) {
                ps.level = Integer.parseInt(parts[0]);
                ps.unspentPoints = Integer.parseInt(parts[1]);
                Stat[] stats = Stat.values();
                for (int i = 0; i < stats.length && i + 2 < parts.length; i++) {
                    ps.statPoints.put(stats[i], Integer.parseInt(parts[i + 2]));
                }
            }
            // Parse boss kills
            if (!bossPart.isEmpty()) {
                for (String entry : bossPart.split(",")) {
                    String[] kv = entry.split("=", 2);
                    if (kv.length == 2) {
                        try { ps.bossKills.put(kv[0], Integer.parseInt(kv[1])); }
                        catch (NumberFormatException ignored) {}
                    }
                }
            }
            // Parse affinity points
            if (!affinityPart.isEmpty()) {
                for (String entry : affinityPart.split(",")) {
                    String[] kv = entry.split("=", 2);
                    if (kv.length == 2) {
                        try {
                            Affinity a = Affinity.valueOf(kv[0]);
                            ps.affinityPoints.put(a, Integer.parseInt(kv[1]));
                        } catch (Exception ignored) {}
                    }
                }
            }
            return ps;
        }
    }

    // UUID string -> serialized stats string
    private final Map<String, String> playerData = new HashMap<>();

    // Runtime cache (not serialized directly, built from playerData)
    private final transient Map<UUID, PlayerStats> cache = new HashMap<>();

    public static PlayerProgression fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        PlayerProgression pp = new PlayerProgression();
        NbtCompound players = nbt.getCompound("players");
        for (String key : players.getKeys()) {
            pp.playerData.put(key, players.getString(key));
        }
        return pp;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        NbtCompound players = new NbtCompound();
        for (Map.Entry<String, String> entry : playerData.entrySet()) {
            players.putString(entry.getKey(), entry.getValue());
        }
        nbt.put("players", players);
        return nbt;
    }

    private static final PersistentState.Type<PlayerProgression> TYPE =
        new PersistentState.Type<>(PlayerProgression::new, PlayerProgression::fromNbt, null);

    public PlayerProgression() {}

    /**
     * Get stats for a player, creating defaults if first time.
     */
    public PlayerStats getStats(UUID playerId) {
        PlayerStats cached = cache.get(playerId);
        if (cached != null) return cached;

        String key = playerId.toString();
        String data = playerData.get(key);
        PlayerStats stats;
        if (data != null) {
            stats = PlayerStats.deserialize(data);
        } else {
            stats = new PlayerStats();
            playerData.put(key, stats.serialize());
            markDirty();
        }
        cache.put(playerId, stats);
        return stats;
    }

    public PlayerStats getStats(ServerPlayerEntity player) {
        return getStats(player.getUuid());
    }

    /**
     * Save a player's stats back to persistent storage.
     */
    public void saveStats(UUID playerId) {
        PlayerStats stats = cache.get(playerId);
        if (stats != null) {
            playerData.put(playerId.toString(), stats.serialize());
            markDirty();
        }
    }

    public void saveStats(ServerPlayerEntity player) {
        saveStats(player.getUuid());
    }

    /**
     * Grant a level-up to a player (called after biome boss victory).
     */
    public void grantLevelUp(ServerPlayerEntity player) {
        PlayerStats stats = getStats(player);
        stats.grantLevelUp();
        saveStats(player);
    }

    /**
     * Allocate a stat point for a player. Returns true if successful.
     */
    public boolean allocateStat(ServerPlayerEntity player, Stat stat) {
        PlayerStats stats = getStats(player);
        boolean success = stats.allocatePoint(stat);
        if (success) {
            saveStats(player);
        }
        return success;
    }

    public static PlayerProgression get(ServerWorld world) {
        // Always use the overworld's state manager so data persists across dimensions
        return world.getServer().getOverworld().getPersistentStateManager().getOrCreate(TYPE, "craftics_progression");
    }
}
