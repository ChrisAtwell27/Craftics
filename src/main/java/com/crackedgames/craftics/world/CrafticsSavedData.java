package com.crackedgames.craftics.world;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import net.minecraft.nbt.NbtList;
import java.util.Map;
import java.util.UUID;

/** Per-player persistent game state (emeralds, biome progress, etc.) + shared world state (hub) */
public class CrafticsSavedData extends PersistentState {
    public boolean hubBuilt = false;
    public int hubVersion = 0;

    private int nextWorldSlot = 0;

    /** Z distance between player lanes (rows). Arenas are narrow in Z, so 1000 is plenty. */
    private static final int LANE_SPACING_Z = 1000;
    /** X offset where the hub sits for every player. */
    private static final int HUB_X = 10000;

    private final Map<UUID, PlayerData> players = new HashMap<>();
    private final Map<UUID, Party> parties = new HashMap<>();
    private final Map<UUID, UUID> playerToParty = new HashMap<>();

    public static class PlayerData {
        public int highestBiomeUnlocked = 1;
        public int emeralds = 0;
        public String activeBiomeId = "";
        public int activeBiomeLevelIndex = 0;
        public int branchChoice = -1;
        public String discoveredBiomes = "";
        public int ngPlusLevel = 0;
        public boolean inCombat = false;
        public boolean starterGuideGranted = false;
        public int worldSlot = -1;
        public boolean personalHubBuilt = false;
        public int personalHubVersion = 0;
        /** Pity timer — resets when an event occurs */
        public int levelsSinceLastEvent = 0;
        /** Pets waiting at the hub to rejoin next fight */
        private final java.util.List<net.minecraft.nbt.NbtCompound> hubPets = new java.util.ArrayList<>();
        /** Server-authoritative unlocked guide entries (bestiary mobs, trims) */
        private final java.util.Set<String> unlockedGuideEntries = new java.util.LinkedHashSet<>();
        /** Whether arenas have been pre-generated for this world slot. */
        public boolean arenasPreGenerated = false;
        /** Pre-built arena metadata: level -> "originX,originY,originZ,width,height,playerX,playerZ" */
        private final Map<Integer, String> preBuiltArenas = new HashMap<>();

        public void storeArenaMetadata(int level, net.minecraft.util.math.BlockPos origin,
                                        int width, int height,
                                        com.crackedgames.craftics.core.GridPos playerStart) {
            preBuiltArenas.put(level, origin.getX() + "," + origin.getY() + "," + origin.getZ()
                + "," + width + "," + height + "," + playerStart.x() + "," + playerStart.z());
        }

        /** Returns int[]{originX, originY, originZ, width, height, playerX, playerZ} or null. */
        public int[] getArenaMetadata(int level) {
            String raw = preBuiltArenas.get(level);
            if (raw == null) return null;
            String[] parts = raw.split(",");
            if (parts.length != 7) return null;
            try {
                int[] result = new int[7];
                for (int i = 0; i < 7; i++) result[i] = Integer.parseInt(parts[i]);
                return result;
            } catch (NumberFormatException e) {
                return null;
            }
        }

        public boolean unlockGuideEntry(String entryName) {
            return unlockedGuideEntries.add(entryName);
        }

        public java.util.Set<String> getUnlockedGuideEntries() {
            return java.util.Collections.unmodifiableSet(unlockedGuideEntries);
        }

        public void pushHubPet(String type, int hp, int maxHp, int atk, int def, int speed, int range) {
            net.minecraft.nbt.NbtCompound n = new net.minecraft.nbt.NbtCompound();
            n.putString("type", type); n.putInt("hp", hp); n.putInt("maxHp", maxHp);
            n.putInt("atk", atk);      n.putInt("def", def);
            n.putInt("speed", speed);  n.putInt("range", range);
            hubPets.add(n);
        }

        public java.util.List<net.minecraft.nbt.NbtCompound> drainHubPets() {
            var pets = new java.util.ArrayList<>(hubPets);
            hubPets.clear();
            return pets;
        }

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
            if (discoveredBiomes.isEmpty()) return false;
            for (String discovered : discoveredBiomes.split(",")) {
                if (discovered.equals(biomeId)) return true;
            }
            return false;
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
            nbt.putBoolean("starterGuideGranted", starterGuideGranted);
            nbt.putInt("worldSlot", worldSlot);
            nbt.putBoolean("personalHubBuilt", personalHubBuilt);
            nbt.putInt("personalHubVersion", personalHubVersion);
            NbtList petList = new NbtList();
            hubPets.forEach(petList::add);
            nbt.put("hubPets", petList);
            nbt.putString("unlockedGuideEntries", String.join("|", unlockedGuideEntries));
            nbt.putBoolean("arenasPreGenerated", arenasPreGenerated);
            NbtCompound arenasMeta = new NbtCompound();
            for (var entry : preBuiltArenas.entrySet()) {
                arenasMeta.putString(String.valueOf(entry.getKey()), entry.getValue());
            }
            nbt.put("preBuiltArenas", arenasMeta);
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
            pd.starterGuideGranted = nbt.contains("starterGuideGranted") && nbt.getBoolean("starterGuideGranted");
            pd.worldSlot = nbt.contains("worldSlot") ? nbt.getInt("worldSlot") : -1;
            pd.personalHubBuilt = nbt.contains("personalHubBuilt") && nbt.getBoolean("personalHubBuilt");
            pd.personalHubVersion = nbt.contains("personalHubVersion") ? nbt.getInt("personalHubVersion") : 0;
            if (nbt.contains("hubPets")) {
                NbtList pl = nbt.getList("hubPets", net.minecraft.nbt.NbtElement.COMPOUND_TYPE);
                for (int i = 0; i < pl.size(); i++) pd.hubPets.add(pl.getCompound(i));
            }
            if (nbt.contains("unlockedGuideEntries")) {
                String raw = nbt.getString("unlockedGuideEntries");
                if (!raw.isEmpty()) {
                    for (String entry : raw.split("\\|")) {
                        if (!entry.isEmpty()) pd.unlockedGuideEntries.add(entry);
                    }
                }
            }
            pd.arenasPreGenerated = nbt.contains("arenasPreGenerated") && nbt.getBoolean("arenasPreGenerated");
            if (nbt.contains("preBuiltArenas")) {
                NbtCompound arenasMeta = nbt.getCompound("preBuiltArenas");
                for (String key : arenasMeta.getKeys()) {
                    try {
                        pd.preBuiltArenas.put(Integer.parseInt(key), arenasMeta.getString(key));
                    } catch (NumberFormatException ignored) {}
                }
            }
            return pd;
        }
    }

    public CrafticsSavedData() {}

    /** Always call markDirty() after modifying */
    public PlayerData getPlayerData(UUID playerId) {
        return players.computeIfAbsent(playerId, id -> new PlayerData());
    }

    public PlayerData getPlayerData(ServerPlayerEntity player) {
        return getPlayerData(player.getUuid());
    }

    public static CrafticsSavedData fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        CrafticsSavedData data = new CrafticsSavedData();
        data.hubBuilt = nbt.getBoolean("hubBuilt");
        data.hubVersion = nbt.getInt("hubVersion");
        data.nextWorldSlot = nbt.contains("nextWorldSlot") ? nbt.getInt("nextWorldSlot") : 0;

        if (nbt.contains("players")) {
            NbtCompound playersNbt = nbt.getCompound("players");
            for (String key : playersNbt.getKeys()) {
                try {
                    UUID uuid = UUID.fromString(key);
                    data.players.put(uuid, PlayerData.fromNbt(playersNbt.getCompound(key)));
                } catch (IllegalArgumentException ignored) {}
            }
        }

        // Legacy migration from single-player save format
        if (nbt.contains("highestBiomeUnlocked") && !nbt.contains("players")) {
            PlayerData legacy = new PlayerData();
            legacy.highestBiomeUnlocked = nbt.getInt("highestBiomeUnlocked");
            legacy.emeralds = nbt.getInt("emeralds");
            legacy.activeBiomeId = nbt.getString("activeBiomeId");
            legacy.activeBiomeLevelIndex = nbt.getInt("activeBiomeLevelIndex");
            legacy.branchChoice = nbt.contains("branchChoice") ? nbt.getInt("branchChoice") : -1;
            legacy.discoveredBiomes = nbt.contains("discoveredBiomes") ? nbt.getString("discoveredBiomes") : "";
            legacy.ngPlusLevel = nbt.contains("ngPlusLevel") ? nbt.getInt("ngPlusLevel") : 0;
            // Placeholder UUID — claimed by first player to join
            data.players.put(new UUID(0, 0), legacy);
        }

        if (nbt.contains("parties")) {
            NbtCompound partiesNbt = nbt.getCompound("parties");
            for (String key : partiesNbt.getKeys()) {
                try {
                    UUID partyId = UUID.fromString(key);
                    Party party = Party.fromNbt(partiesNbt.getCompound(key));
                    data.parties.put(partyId, party);
                    for (UUID memberUuid : party.getMemberUuids()) {
                        data.playerToParty.put(memberUuid, partyId);
                    }
                } catch (IllegalArgumentException ignored) {}
            }
        }

        return data;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        nbt.putBoolean("hubBuilt", hubBuilt);
        nbt.putInt("hubVersion", hubVersion);
        nbt.putInt("nextWorldSlot", nextWorldSlot);

        NbtCompound playersNbt = new NbtCompound();
        for (var entry : players.entrySet()) {
            playersNbt.put(entry.getKey().toString(), entry.getValue().toNbt());
        }
        nbt.put("players", playersNbt);

        NbtCompound partiesNbt = new NbtCompound();
        for (var entry : parties.entrySet()) {
            partiesNbt.put(entry.getKey().toString(), entry.getValue().toNbt());
        }
        nbt.put("parties", partiesNbt);

        return nbt;
    }

    private static final PersistentState.Type<CrafticsSavedData> TYPE =
        new PersistentState.Type<>(CrafticsSavedData::new, CrafticsSavedData::fromNbt, null);

    public static CrafticsSavedData get(ServerWorld world) {
        return world.getServer().getOverworld().getPersistentStateManager().getOrCreate(TYPE, "craftics_data");
    }

    public Party getParty(UUID partyId) {
        return parties.get(partyId);
    }

    public Party getPlayerParty(UUID playerUuid) {
        UUID partyId = playerToParty.get(playerUuid);
        return partyId != null ? parties.get(partyId) : null;
    }

    /** Get all parties (for invite lookup). */
    public Map<UUID, Party> getAllParties() {
        return Collections.unmodifiableMap(parties);
    }

    public Party createParty(UUID leaderUuid) {
        leaveParty(leaderUuid);
        UUID partyId = UUID.randomUUID();
        Party party = new Party(partyId, leaderUuid);
        parties.put(partyId, party);
        playerToParty.put(leaderUuid, partyId);
        markDirty();
        return party;
    }

    public boolean joinParty(UUID playerUuid, UUID partyId) {
        Party party = parties.get(partyId);
        if (party == null || !party.hasInvite(playerUuid)) return false;
        leaveParty(playerUuid);
        party.addMember(playerUuid);
        playerToParty.put(playerUuid, partyId);
        markDirty();
        return true;
    }

    public void leaveParty(UUID playerUuid) {
        UUID partyId = playerToParty.remove(playerUuid);
        if (partyId == null) return;
        Party party = parties.get(partyId);
        if (party == null) return;
        party.removeMember(playerUuid);
        if (party.isEmpty()) {
            parties.remove(partyId);
        }
        markDirty();
    }

    public void disbandParty(UUID partyId) {
        Party party = parties.remove(partyId);
        if (party == null) return;
        for (UUID member : party.getMemberUuids()) {
            playerToParty.remove(member);
        }
        markDirty();
    }

    public void addPartyInvite(UUID partyId, UUID inviteeUuid) {
        Party party = parties.get(partyId);
        if (party != null) {
            party.addInvite(inviteeUuid);
            markDirty();
        }
    }

    public void removePartyInvite(UUID partyId, UUID inviteeUuid) {
        Party party = parties.get(partyId);
        if (party != null) {
            party.removeInvite(inviteeUuid);
            markDirty();
        }
    }

    public void kickFromParty(UUID partyId, UUID playerUuid) {
        Party party = parties.get(partyId);
        if (party == null) return;
        party.removeMember(playerUuid);
        playerToParty.remove(playerUuid);
        if (party.isEmpty()) {
            parties.remove(partyId);
        }
        markDirty();
    }

    /**
     * Get party member UUIDs for a player. Returns a list containing just the player
     * if they are not in a party (solo = party of 1).
     */
    public List<UUID> getPartyMemberUuids(UUID playerUuid) {
        Party party = getPlayerParty(playerUuid);
        if (party == null) return List.of(playerUuid);
        return new ArrayList<>(party.getMemberUuids());
    }

    // === World Slot System (per-player world isolation) ===

    /** Allocate a new world slot for a player. Returns the assigned slot index. */
    public int allocateWorldSlot(UUID playerId) {
        PlayerData pd = getPlayerData(playerId);
        if (pd.worldSlot >= 0) return pd.worldSlot; // already has one
        pd.worldSlot = nextWorldSlot++;
        markDirty();
        return pd.worldSlot;
    }

    /** Get the world origin (hub center) for a player's personal world, or null if not created. */
    public net.minecraft.util.math.BlockPos getWorldOrigin(UUID playerId) {
        PlayerData pd = getPlayerData(playerId);
        if (pd.worldSlot < 0) return null;
        return new net.minecraft.util.math.BlockPos(HUB_X, 65, pd.worldSlot * LANE_SPACING_Z);
    }

    /** Alias for getWorldOrigin — returns the center of the player's personal hub. */
    public net.minecraft.util.math.BlockPos getHubOrigin(UUID playerId) {
        return getWorldOrigin(playerId);
    }

    /** Offset from hub center to first arena — well beyond render distance. */
    private static final int ARENA_OFFSET = 1000;

    /** Get the arena origin for a specific level within a player's world. */
    public net.minecraft.util.math.BlockPos getArenaOrigin(UUID playerId, int level) {
        PlayerData pd = getPlayerData(playerId);
        if (pd.worldSlot < 0) return null;
        int laneZ = pd.worldSlot * LANE_SPACING_Z;
        return new net.minecraft.util.math.BlockPos(HUB_X + ARENA_OFFSET + level * 300, 100, laneZ);
    }

    /** Get the trader area origin within a player's world. */
    public net.minecraft.util.math.BlockPos getTraderOrigin(UUID playerId) {
        PlayerData pd = getPlayerData(playerId);
        if (pd.worldSlot < 0) return null;
        int laneZ = pd.worldSlot * LANE_SPACING_Z;
        return new net.minecraft.util.math.BlockPos(HUB_X + ARENA_OFFSET + 500, 100, laneZ + 500);
    }

    /** Get the dig site origin within a player's world. */
    public net.minecraft.util.math.BlockPos getDigSiteOrigin(UUID playerId) {
        PlayerData pd = getPlayerData(playerId);
        if (pd.worldSlot < 0) return null;
        int laneZ = pd.worldSlot * LANE_SPACING_Z;
        return new net.minecraft.util.math.BlockPos(HUB_X + ARENA_OFFSET + 500, 100, laneZ + 600);
    }

    /**
     * Get the effective world owner for a player. If in a party, returns the party leader
     * (all party members use the leader's world). Otherwise returns the player's own UUID.
     */
    public UUID getEffectiveWorldOwner(UUID playerId) {
        Party party = getPlayerParty(playerId);
        if (party != null) return party.getLeaderUuid();
        return playerId;
    }

    /**
     * Get the hub teleport position for a player. Resolves to their personal hub,
     * or the party leader's hub, or the central lobby if no personal world exists.
     */
    public net.minecraft.util.math.BlockPos getHubTeleportPos(UUID playerId) {
        UUID owner = getEffectiveWorldOwner(playerId);
        net.minecraft.util.math.BlockPos hub = getHubOrigin(owner);
        if (hub != null) return hub;
        // Fallback to central lobby
        return new net.minecraft.util.math.BlockPos(0, 65, 0);
    }

    /** Check if a player has a personal world. */
    public boolean hasPersonalWorld(UUID playerId) {
        return getPlayerData(playerId).worldSlot >= 0;
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
