package com.crackedgames.craftics.combat;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;
//? if >=1.21.5 {
/*import net.minecraft.world.PersistentStateType;
import com.mojang.serialization.Codec;
*///?}

import com.crackedgames.craftics.achievement.Achievement;

import java.util.*;

// Per-player progression: level, stat points, affinities, achievements
// Stored by UUID for multiplayer
public class PlayerProgression extends PersistentState {

    public enum Stat {
        SPEED("Speed", "§b⚡", "+1 movement per turn", 3),
        AP("Action Points", "§e⚡", "+1 action per turn", 3),
        MELEE_POWER("Melee Power", "§c⚔", "+1 melee damage", 0),
        RANGED_POWER("Ranged Power", "§d\uD83C\uDFF9", "+1 ranged damage", 0),
        VITALITY("Vitality", "§a❤", "+2 max HP", 0),
        DEFENSE("Defense", "§9\uD83D\uDEE1", "+1 damage reduction", 0),
        LUCK("Luck", "§6✦", "+2% all combat procs & loot", 0),
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

    public enum Affinity {
        SLASHING("Slashing", "\u00a7c\u2694", "+3 dmg, +5% sweep chance"),
        CLEAVING("Cleaving", "\u00a76\u2716", "+3 dmg, +3% armor ignore"),
        BLUNT("Blunt", "\u00a78\u2B24", "+3 dmg, +3% stun chance"),
        RANGED("Ranged", "\u00a7b\u27B3", "+3 ranged dmg, +5% ricochet chain chance"),
        WATER("Water", "\u00a73\u2248", "+3 dmg, +3% knockback & Wet"),
        SPECIAL("Special", "\u00a7d\u2728", "+3 dmg, +3% free AP on use"),
        PET("Pet", "\u00a7a\uD83D\uDC3E", "+3 dmg, +3 HP to allies"),
        PHYSICAL("Physical", "\u00a77\u270A", "+3 dmg, +3% counterattack");

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
        // Diminishing level-up returns per biome
        private final Map<String, Integer> bossKills = new HashMap<>();
        private final EnumMap<Affinity, Integer> affinityPoints = new EnumMap<>(Affinity.class);
        public boolean pendingAffinityChoice = false;
        private final Set<String> achievements = new HashSet<>();

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

        /** Total affinity points allocated across all types. */
        public int getTotalAffinityPoints() {
            int total = 0;
            for (Affinity a : Affinity.values()) {
                total += getAffinityPoints(a);
            }
            return total;
        }

        /** Player is owed an affinity pick if they have fewer affinity points than expected for their level. */
        public boolean canAllocateAffinity() {
            // Affinities are awarded on odd levels (3, 5, 7...) — count how many they should have
            int expectedAffinities = (level - 1) / 2; // level 3→1, level 5→2, level 7→3
            return getTotalAffinityPoints() < expectedAffinities;
        }

        public boolean hasAchievement(Achievement achievement) {
            return achievements.contains(achievement.name());
        }

        /** Returns true if newly unlocked */
        public boolean grantAchievement(Achievement achievement) {
            return achievements.add(achievement.name());
        }

        public Set<String> getAchievements() {
            return Collections.unmodifiableSet(achievements);
        }

        public int getAchievementCount() {
            return achievements.size();
        }

        // Level up at exponentially increasing thresholds: 1, 3, 7, 15, ...
        // Prevents farming a single boss for infinite levels
        public boolean recordBossKillAndCheckLevelUp(String biomeId) {
            int kills = bossKills.getOrDefault(biomeId, 0) + 1;
            bossKills.put(biomeId, kills);
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

        /** Admin override */
        public void setPoints(Stat stat, int value) {
            statPoints.put(stat, Math.max(0, value));
        }

        public void grantLevelUp() {
            level++;
            // Alternate: even levels grant a stat point, odd levels grant an affinity choice
            if (level % 2 == 0) {
                unspentPoints++;
            }
            // Odd levels: affinity is handled by pendingAffinityChoice flag in CombatManager
        }

        /** Returns true if this level awards a stat point (even levels). */
        public boolean isStatLevel() {
            return level % 2 == 0;
        }

        /** Returns true if this level awards an affinity point (odd levels > 1). */
        public boolean isAffinityLevel() {
            return level % 2 == 1 && level > 1;
        }

        // Format: "level:unspent:s0:s1:...:s7|bossKills|affinities|achievements"
        public String serialize() {
            StringBuilder sb = new StringBuilder();
            sb.append(level).append(':').append(unspentPoints);
            for (Stat s : Stat.values()) {
                sb.append(':').append(getPoints(s));
            }
            sb.append('|');
            {
                boolean first = true;
                for (Map.Entry<String, Integer> e : bossKills.entrySet()) {
                    if (!first) sb.append(',');
                    sb.append(e.getKey()).append('=').append(e.getValue());
                    first = false;
                }
            }
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
            sb.append('|');
            sb.append(String.join(",", achievements));
            return sb.toString();
        }

        public static PlayerStats deserialize(String data) {
            PlayerStats ps = new PlayerStats();
            String[] sections = data.split("\\|", -1);
            String statPart = sections[0];
            String bossPart = sections.length > 1 ? sections[1] : "";
            String affinityPart = sections.length > 2 ? sections[2] : "";
            String achievementPart = sections.length > 3 ? sections[3] : "";

            String[] parts = statPart.split(":");
            if (parts.length >= 2) {
                ps.level = Integer.parseInt(parts[0]);
                ps.unspentPoints = Integer.parseInt(parts[1]);
                Stat[] stats = Stat.values();
                for (int i = 0; i < stats.length && i + 2 < parts.length; i++) {
                    ps.statPoints.put(stats[i], Integer.parseInt(parts[i + 2]));
                }
            }
            if (!bossPart.isEmpty()) {
                for (String entry : bossPart.split(",")) {
                    String[] kv = entry.split("=", 2);
                    if (kv.length == 2) {
                        try { ps.bossKills.put(kv[0], Integer.parseInt(kv[1])); }
                        catch (NumberFormatException ignored) {}
                    }
                }
            }
            if (!affinityPart.isEmpty()) {
                for (String entry : affinityPart.split(",")) {
                    String[] kv = entry.split("=", 2);
                    if (kv.length == 2) {
                        try {
                            // Migration: SWORD -> SLASHING rename
                            String affinityName = "SWORD".equals(kv[0]) ? "SLASHING" : kv[0];
                            Affinity a = Affinity.valueOf(affinityName);
                            ps.affinityPoints.put(a, Integer.parseInt(kv[1]));
                        } catch (Exception ignored) {}
                    }
                }
            }
            if (!achievementPart.isEmpty()) {
                for (String name : achievementPart.split(",")) {
                    String trimmed = name.trim();
                    if (!trimmed.isEmpty()) {
                        ps.achievements.add(trimmed);
                    }
                }
            }
            return ps;
        }
    }

    private final Map<String, String> playerData = new HashMap<>();

    private final transient Map<UUID, PlayerStats> cache = new HashMap<>();

    //? if <=1.21.4 {
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
    //?} else {
    /*private static final Codec<PlayerProgression> CODEC = NbtCompound.CODEC.xmap(
        nbt -> {
            PlayerProgression pp = new PlayerProgression();
            NbtCompound players = nbt.getCompoundOrEmpty("players");
            for (String key : players.getKeys()) {
                pp.playerData.put(key, players.getString(key, ""));
            }
            return pp;
        },
        pp -> {
            NbtCompound nbt = new NbtCompound();
            NbtCompound players = new NbtCompound();
            for (Map.Entry<String, String> entry : pp.playerData.entrySet()) {
                players.putString(entry.getKey(), entry.getValue());
            }
            nbt.put("players", players);
            return nbt;
        }
    );

    private static final PersistentStateType<PlayerProgression> TYPE =
        new PersistentStateType<>("craftics_progression", PlayerProgression::new, CODEC, null);
    *///?}

    public PlayerProgression() {}

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

    public void grantLevelUp(ServerPlayerEntity player) {
        PlayerStats stats = getStats(player);
        stats.grantLevelUp();
        saveStats(player);
    }

    public boolean allocateStat(ServerPlayerEntity player, Stat stat) {
        PlayerStats stats = getStats(player);
        boolean success = stats.allocatePoint(stat);
        if (success) {
            saveStats(player);
        }
        return success;
    }

    //? if <=1.21.4 {
    public static PlayerProgression get(ServerWorld world) {
        // Always overworld so data persists across dimensions
        return world.getServer().getOverworld().getPersistentStateManager().getOrCreate(TYPE, "craftics_progression");
    }
    //?} else {
    /*public static PlayerProgression get(ServerWorld world) {
        return world.getServer().getOverworld().getPersistentStateManager().getOrCreate(TYPE);
    }
    *///?}
}
