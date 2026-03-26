package com.crackedgames.craftics.world;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Per-player persistent game state + shared world state.
 * Player-specific data (emeralds, biome progress, etc.) is stored per-UUID.
 * World-level data (hub built, hub version) is shared.
 */
public class CrafticsSavedData extends PersistentState {
    // Shared world state
    public boolean hubBuilt = false;
    public int hubVersion = 0;

    // Per-player state
    private final Map<UUID, PlayerData> players = new HashMap<>();

    // Tracks which player's data is loaded into the legacy fields
    private UUID currentLegacyPlayerId = null;

    /** Per-player game data. */
    public static class PlayerData {
        public int highestBiomeUnlocked = 1;
        public int emeralds = 0;
        public String activeBiomeId = "";
        public int activeBiomeLevelIndex = 0;
        public int branchChoice = -1;
        public String discoveredBiomes = "";
        public int ngPlusLevel = 0;
        /** True while the player is mid-battle — used to restart the fight on rejoin. */
        public boolean inCombat = false;

        public boolean isInBiomeRun() {
            return activeBiomeId != null && !activeBiomeId.isEmpty();
        }

        public void startBiomeRun(String biomeId) {
            this.activeBiomeId = biomeId;
            this.activeBiomeLevelIndex = 0;
        }

        public void advanceBiomeRun() {
            this.activeBiomeLevelIndex++;
        }

        public void endBiomeRun() {
            this.activeBiomeId = "";
            this.activeBiomeLevelIndex = 0;
        }

        public void addEmeralds(int amount) {
            this.emeralds += amount;
        }

        public boolean spendEmeralds(int amount) {
            if (this.emeralds >= amount) {
                this.emeralds -= amount;
                return true;
            }
            return false;
        }

        public boolean isBiomeDiscovered(String biomeId) {
            return discoveredBiomes.contains(biomeId);
        }

        public void discoverBiome(String biomeId) {
            if (!isBiomeDiscovered(biomeId)) {
                discoveredBiomes = discoveredBiomes.isEmpty() ? biomeId : discoveredBiomes + "," + biomeId;
            }
        }

        public void initBranchIfNeeded() {
            if (branchChoice < 0) {
                branchChoice = new java.util.Random().nextInt(2);
            }
        }

        public void startNewGamePlus() {
            ngPlusLevel++;
            highestBiomeUnlocked = 1;
            discoveredBiomes = "";
            activeBiomeId = "";
            activeBiomeLevelIndex = 0;
            branchChoice = new java.util.Random().nextInt(2);
        }

        public float getNgPlusMultiplier() {
            return 1.0f + ngPlusLevel * 0.25f;
        }

        public NbtCompound toNbt() {
            NbtCompound nbt = new NbtCompound();
            nbt.putInt("highestBiomeUnlocked", highestBiomeUnlocked);
            nbt.putInt("emeralds", emeralds);
            nbt.putString("activeBiomeId", activeBiomeId);
            nbt.putInt("activeBiomeLevelIndex", activeBiomeLevelIndex);
            nbt.putInt("branchChoice", branchChoice);
            nbt.putString("discoveredBiomes", discoveredBiomes);
            nbt.putInt("ngPlusLevel", ngPlusLevel);
            nbt.putBoolean("inCombat", inCombat);
            return nbt;
        }

        public static PlayerData fromNbt(NbtCompound nbt) {
            PlayerData pd = new PlayerData();
            pd.highestBiomeUnlocked = nbt.getInt("highestBiomeUnlocked");
            pd.emeralds = nbt.getInt("emeralds");
            pd.activeBiomeId = nbt.getString("activeBiomeId");
            pd.activeBiomeLevelIndex = nbt.getInt("activeBiomeLevelIndex");
            pd.branchChoice = nbt.contains("branchChoice") ? nbt.getInt("branchChoice") : -1;
            pd.discoveredBiomes = nbt.contains("discoveredBiomes") ? nbt.getString("discoveredBiomes") : "";
            pd.ngPlusLevel = nbt.contains("ngPlusLevel") ? nbt.getInt("ngPlusLevel") : 0;
            pd.inCombat = nbt.contains("inCombat") && nbt.getBoolean("inCombat");
            return pd;
        }
    }

    public CrafticsSavedData() {}

    /** Get or create per-player data. Always call markDirty() after modifying. */
    public PlayerData getPlayerData(UUID playerId) {
        return players.computeIfAbsent(playerId, id -> new PlayerData());
    }

    /** Get per-player data by entity. */
    public PlayerData getPlayerData(ServerPlayerEntity player) {
        return getPlayerData(player.getUuid());
    }

    // === Convenience shortcuts that delegate to per-player data ===
    // These maintain backwards compatibility with existing code that uses `data.emeralds` etc.
    // For new code, prefer `data.getPlayerData(player).emeralds` directly.

    /** @deprecated Use getPlayerData(player).highestBiomeUnlocked */
    public int highestBiomeUnlocked = 1;
    /** @deprecated Use getPlayerData(player).emeralds */
    public int emeralds = 0;
    /** @deprecated Use getPlayerData(player).activeBiomeId */
    public String activeBiomeId = "";
    /** @deprecated Use getPlayerData(player).activeBiomeLevelIndex */
    public int activeBiomeLevelIndex = 0;
    /** @deprecated Use getPlayerData(player).branchChoice */
    public int branchChoice = -1;
    /** @deprecated Use getPlayerData(player).discoveredBiomes */
    public String discoveredBiomes = "";
    /** @deprecated Use getPlayerData(player).ngPlusLevel */
    public int ngPlusLevel = 0;
    /** @deprecated Use getPlayerData(player).inCombat */
    public boolean inCombat = false;

    /** Load a specific player's data into the legacy fields (for backwards compat). */
    public void loadPlayerIntoLegacy(UUID playerId) {
        currentLegacyPlayerId = playerId;
        PlayerData pd = getPlayerData(playerId);
        this.highestBiomeUnlocked = pd.highestBiomeUnlocked;
        this.emeralds = pd.emeralds;
        this.activeBiomeId = pd.activeBiomeId;
        this.activeBiomeLevelIndex = pd.activeBiomeLevelIndex;
        this.branchChoice = pd.branchChoice;
        this.discoveredBiomes = pd.discoveredBiomes;
        this.ngPlusLevel = pd.ngPlusLevel;
        this.inCombat = pd.inCombat;
    }

    /** Save legacy fields back into a specific player's data. */
    public void saveLegacyToPlayer(UUID playerId) {
        PlayerData pd = getPlayerData(playerId);
        pd.highestBiomeUnlocked = this.highestBiomeUnlocked;
        pd.emeralds = this.emeralds;
        pd.activeBiomeId = this.activeBiomeId;
        pd.activeBiomeLevelIndex = this.activeBiomeLevelIndex;
        pd.branchChoice = this.branchChoice;
        pd.discoveredBiomes = this.discoveredBiomes;
        pd.ngPlusLevel = this.ngPlusLevel;
        pd.inCombat = this.inCombat;
        markDirty();
    }

    // === Legacy convenience methods (delegate to legacy fields) ===

    public boolean isInBiomeRun() { return activeBiomeId != null && !activeBiomeId.isEmpty(); }
    public void startBiomeRun(String biomeId) { this.activeBiomeId = biomeId; this.activeBiomeLevelIndex = 0; markDirty(); }
    public void advanceBiomeRun() { this.activeBiomeLevelIndex++; markDirty(); }
    public void endBiomeRun() { this.activeBiomeId = ""; this.activeBiomeLevelIndex = 0; markDirty(); }
    public void addEmeralds(int amount) { this.emeralds += amount; markDirty(); }
    public boolean spendEmeralds(int amount) { if (emeralds >= amount) { emeralds -= amount; markDirty(); return true; } return false; }
    public boolean isBiomeDiscovered(String biomeId) { return discoveredBiomes.contains(biomeId); }
    public void discoverBiome(String biomeId) {
        if (!isBiomeDiscovered(biomeId)) {
            discoveredBiomes = discoveredBiomes.isEmpty() ? biomeId : discoveredBiomes + "," + biomeId;
            markDirty();
        }
    }
    public void initBranchIfNeeded() { if (branchChoice < 0) { branchChoice = new java.util.Random().nextInt(2); markDirty(); } }
    public java.util.List<String> getPath() {
        initBranchIfNeeded();
        return com.crackedgames.craftics.level.BiomePath.getPath(branchChoice);
    }
    public void startNewGamePlus() {
        ngPlusLevel++; highestBiomeUnlocked = 1; discoveredBiomes = ""; activeBiomeId = "";
        activeBiomeLevelIndex = 0; branchChoice = new java.util.Random().nextInt(2); markDirty();
    }
    public float getNgPlusMultiplier() { return 1.0f + ngPlusLevel * 0.25f; }

    /**
     * Auto-sync legacy fields back to PlayerData whenever the state is marked dirty.
     * This prevents desync when code modifies legacy fields without calling saveLegacyToPlayer().
     */
    @Override
    public void markDirty() {
        if (currentLegacyPlayerId != null) {
            PlayerData pd = getPlayerData(currentLegacyPlayerId);
            pd.highestBiomeUnlocked = this.highestBiomeUnlocked;
            pd.emeralds = this.emeralds;
            pd.activeBiomeId = this.activeBiomeId;
            pd.activeBiomeLevelIndex = this.activeBiomeLevelIndex;
            pd.branchChoice = this.branchChoice;
            pd.discoveredBiomes = this.discoveredBiomes;
            pd.ngPlusLevel = this.ngPlusLevel;
            pd.inCombat = this.inCombat;
        }
        super.markDirty();
    }

    // === Serialization ===

    public static CrafticsSavedData fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        CrafticsSavedData data = new CrafticsSavedData();
        data.hubBuilt = nbt.getBoolean("hubBuilt");
        data.hubVersion = nbt.getInt("hubVersion");

        // Load per-player data
        if (nbt.contains("players")) {
            NbtCompound playersNbt = nbt.getCompound("players");
            for (String key : playersNbt.getKeys()) {
                try {
                    UUID uuid = UUID.fromString(key);
                    data.players.put(uuid, PlayerData.fromNbt(playersNbt.getCompound(key)));
                } catch (IllegalArgumentException ignored) {}
            }
        }

        // Legacy migration: if old single-player fields exist, import them
        if (nbt.contains("highestBiomeUnlocked") && !nbt.contains("players")) {
            PlayerData legacy = new PlayerData();
            legacy.highestBiomeUnlocked = nbt.getInt("highestBiomeUnlocked");
            legacy.emeralds = nbt.getInt("emeralds");
            legacy.activeBiomeId = nbt.getString("activeBiomeId");
            legacy.activeBiomeLevelIndex = nbt.getInt("activeBiomeLevelIndex");
            legacy.branchChoice = nbt.contains("branchChoice") ? nbt.getInt("branchChoice") : -1;
            legacy.discoveredBiomes = nbt.contains("discoveredBiomes") ? nbt.getString("discoveredBiomes") : "";
            legacy.ngPlusLevel = nbt.contains("ngPlusLevel") ? nbt.getInt("ngPlusLevel") : 0;
            // Store under a placeholder UUID — will be claimed by first player to join
            data.players.put(new UUID(0, 0), legacy);
        }

        return data;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        nbt.putBoolean("hubBuilt", hubBuilt);
        nbt.putInt("hubVersion", hubVersion);

        NbtCompound playersNbt = new NbtCompound();
        for (var entry : players.entrySet()) {
            playersNbt.put(entry.getKey().toString(), entry.getValue().toNbt());
        }
        nbt.put("players", playersNbt);

        return nbt;
    }

    private static final PersistentState.Type<CrafticsSavedData> TYPE =
        new PersistentState.Type<>(CrafticsSavedData::new, CrafticsSavedData::fromNbt, null);

    public static CrafticsSavedData get(ServerWorld world) {
        return world.getServer().getOverworld().getPersistentStateManager().getOrCreate(TYPE, "craftics_data");
    }

    /**
     * Claim legacy data for a player joining for the first time.
     * If there's a placeholder UUID(0,0) from migration, transfer it to this player.
     */
    public void claimLegacyData(UUID playerId) {
        UUID legacyId = new UUID(0, 0);
        if (players.containsKey(legacyId) && !players.containsKey(playerId)) {
            players.put(playerId, players.remove(legacyId));
            markDirty();
        }
    }
}
