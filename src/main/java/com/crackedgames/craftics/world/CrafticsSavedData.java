package com.crackedgames.craftics.world;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;
//? if >=1.21.5 {
/*import net.minecraft.world.PersistentStateType;
import com.mojang.serialization.Codec;
*///?}

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

    /** Custom lobby spawn (root-level, shared across all players). Y=Integer.MIN_VALUE sentinel = unset,
     *  falls back to the default lobby column. Set via /craftics lobby setspawn. */
    public int lobbySpawnX = 0, lobbySpawnY = Integer.MIN_VALUE, lobbySpawnZ = 0;
    public float lobbySpawnYaw = 0f;

    /** The stored custom lobby spawn, or null when unset (Y sentinel Integer.MIN_VALUE). */
    public net.minecraft.util.math.BlockPos getLobbySpawn() {
        if (lobbySpawnY == Integer.MIN_VALUE) return null;
        return new net.minecraft.util.math.BlockPos(lobbySpawnX, lobbySpawnY, lobbySpawnZ);
    }

    // === Fixed island layout (Task 6: de-laned) ===
    // Every island lives in its OWNER'S dimension (craftics:island/<uuid>), so all
    // content sits at the SAME fixed coordinates in that dim - there are no per-player
    // lanes anymore. The overworld keeps only the central lobby. The hub is at the
    // dim origin; arenas march out along +X; the fixed event/scene rooms share an X
    // column and are separated in Z. Coordinates are small enough that the client
    // overlay renderer's single-precision float math stays well-conditioned.
    /** Hub (dim origin) for every island. */
    private static final int HUB_Y = 65;
    /** Base X for numbered arenas: arena level N sits at (ARENA_BASE_X + N*ARENA_STRIDE_X). */
    private static final int ARENA_BASE_X = 1000;
    private static final int ARENA_STRIDE_X = 300;
    /** Shared X column + build Y for the fixed event/scene rooms. */
    private static final int ROOM_X = 1500;
    private static final int ROOM_Y = 100;

    private final Map<UUID, PlayerData> players = new HashMap<>();
    private final Map<UUID, Party> parties = new HashMap<>();
    private final Map<UUID, UUID> playerToParty = new HashMap<>();
    /** Run participants who disconnected before a hardcore wipe landed: wiped on next join. */
    private final java.util.Set<UUID> pendingHardcoreWipe = new java.util.HashSet<>();

    public static class PlayerData {
        public int highestBiomeUnlocked = 1;
        public int emeralds = 0;
        public String activeBiomeId = "";
        /**
         * Id of the campaign this player's run was last played on. Stamped on first
         * world load (see {@link #checkCampaignStamp()}); a mismatch on a later load
         * (the active campaign changed) is warned about but never silently remapped.
         */
        public String activeCampaignId = "";
        public int activeBiomeLevelIndex = 0;
        public int branchChoice = -1;
        public String discoveredBiomes = "";
        public int ngPlusLevel = 0;
        public boolean inCombat = false;
        public boolean starterGuideGranted = false;
        public int worldSlot = -1;
        /** Hotbar slot index (0-8) where the Move item is force-locked. Default slot 9 (index 8). */
        public int lockedMoveSlot = 8;
        public boolean personalHubBuilt = false;
        public int personalHubVersion = 0;
        /**
         * Per-island toggle for "enemies gain +hpPerLevel HP per level within a biome".
         * Defaults to OFF - enemies only get the biome-ordinal HP bonus, no per-level
         * ramp within a biome. Island owners can opt into the steeper per-level
         * scaling via {@code /craftics hp_per_level on}. Only read for the
         * effective world owner - guests inherit whatever the owner has set.
         */
        public boolean scaleHpPerLevelEnabled = false;
        /** Hardcore island: full-party combat defeat deletes the island and
         *  wipes every run participant. Set by {@code /new hardcore}; dies with
         *  the record on {@link CrafticsSavedData#resetPlayerData}. */
        public boolean hardcoreIsland = false;
        /** True once this island has been checked/migrated from the old overworld-lane
         *  layout to the per-owner dimension. Absent in old saves -> false -> triggers
         *  the one-time migration in {@link IslandMigration#ensureMigrated}. */
        public boolean islandMigrated = false;
        /** True once this island has defeated the Raid event. Gates the Trading Hall
         *  (needs a met trader AND a defeated raid) and drops the raid's event chance
         *  from the new-world 75% down to a normal event rate. Island-scoped via owner. */
        public boolean raidDefeated = false;
        /** Pity timer - resets when an event occurs */
        public int levelsSinceLastEvent = 0;
        /** Trader types (TraderSystem.TraderType.name()) met in run events - island-scoped via owner. */
        public final java.util.Set<String> metTraders = new java.util.HashSet<>();
        /** Barter category ids (BarterCategory.id()) met in run events - island-scoped via owner. */
        public final java.util.Set<String> metBarterers = new java.util.HashSet<>();
        /** Pets waiting at the hub to rejoin next fight */
        private final java.util.List<net.minecraft.nbt.NbtCompound> hubPets = new java.util.ArrayList<>();
        /**
         * Entity UUIDs of mobs the player explicitly added to their battle party
         * via Shift+Right-Click. Order-preserving; the effective cap scales with
         * the player's Pet Affinity (see {@code PartyMobs.partyCap}).
         * These - and only these - are the mobs collected into combat.
         */
        private final java.util.List<UUID> partyMobs = new java.util.ArrayList<>();
        /** Server-authoritative unlocked guide entries (bestiary mobs, trims) */
        private final java.util.Set<String> unlockedGuideEntries = new java.util.LinkedHashSet<>();
        /** Whether arenas have been pre-generated for this world slot. */
        /** Hub spawn point (podzol marker position). -1 = not set, fall back to hubCenter. */
        public int hubSpawnX = -1, hubSpawnY = -1, hubSpawnZ = -1;
        public boolean arenasPreGenerated = false;
        /** Pre-built arena metadata: level -> "originX,originY,originZ,width,height,playerX,playerZ" */
        private final Map<Integer, String> preBuiltArenas = new HashMap<>();

        /** Per-island boss defeat counts, keyed by boss biome id. Drives the
         *  linear boss-HP ramp (see CrafticsConfig.bossKillHpScale). */
        private final Map<String, Integer> bossKills = new HashMap<>();

        // === Infinite mode ===
        /** True while this player is HOSTING an active infinite run. The run cursor
         *  reuses activeBiomeId/activeBiomeLevelIndex on this same record. */
        public boolean infiniteActive = false;
        /** Levels+bosses cleared in the current run. Drives DIFFICULTY scaling
         *  (boss moves/actions ramp every ESCALATION_INTERVAL of these) and the
         *  "BIOME N CLEARED" counter. NOT the score - see {@link #infiniteScore}. */
        public int infiniteBiomesCleared = 0;
        /** Live POINT score for the current run: +5 per level and +10 per boss,
         *  each minus 1 per 2 player-turns taken (floored at 1 per clear). Kept
         *  separate from infiniteBiomesCleared so fast play scores high without
         *  inflating the difficulty ramp. */
        public int infiniteScore = 0;
        /** Participant UUIDs of the current run (host included), comma-separated. */
        public String infiniteParticipants = "";
        /** On the ISLAND OWNER's record: UUID of the member hosting the island's
         *  active infinite run ("" = none). The rest-room bell resolves the run
         *  through this pointer since the ringer may not be the host. */
        public String infiniteHostRef = "";
        /** Personal best infinite POINT score (see {@link #infiniteScore}), across all runs. */
        public int highestInfiniteScore = 0;
        /** On each PARTICIPANT's record: UUID of the run host they joined ("" = none). */
        public String infiniteRunHost = "";
        /** True while this participant's pre-run inventory + progression are stashed. */
        public boolean infiniteStashActive = false;
        /** Pre-run inventory snapshot (PlayerInventory.writeNbt format). */
        public net.minecraft.nbt.NbtList infiniteStashInventory = new net.minecraft.nbt.NbtList();
        public int infiniteStashSelectedSlot = 0;
        /** Pre-run PlayerProgression snapshot (PlayerStats.serialize format). */
        public String infiniteStashStats = "";
        /** On the HOST's record: true while the run is parked at a save point (the host
         *  left mid-run or logged out). The cursor, score, and cleared count all stay;
         *  opening Infinite Mode again resumes it, {@code /craftics infinite stop}
         *  abandons it. */
        public boolean infiniteSuspended = false;
        /** The host's run inventory, parked while the run is suspended. */
        public net.minecraft.nbt.NbtList infiniteParkedInventory = new net.minecraft.nbt.NbtList();
        public int infiniteParkedSelectedSlot = 0;
        /** The host's run progression, parked while the run is suspended. */
        public String infiniteParkedStats = "";
        /** The parked run's own biome/level cursor. A LIVE infinite run borrows
         *  {@link #activeBiomeId}/{@link #activeBiomeLevelIndex}; suspending moves the
         *  cursor here so normal biome runs can use the shared fields in the meantime. */
        public String infiniteParkedBiomeId = "";
        public int infiniteParkedLevelIndex = 0;
        /** Last known player name, for offline leaderboard rows. Refreshed on join. */
        public String lastKnownName = "";

        public int getBossKills(String biomeId) {
            return bossKills.getOrDefault(biomeId, 0);
        }

        public void incrementBossKills(String biomeId) {
            bossKills.merge(biomeId, 1, Integer::sum);
        }

        public void storeArenaMetadata(int level, net.minecraft.util.math.BlockPos origin,
                                        int width, int height,
                                        com.crackedgames.craftics.core.GridPos playerStart,
                                        boolean[][] insideMask) {
            storeArenaMetadata(level, origin, width, height, playerStart, insideMask, null);
        }

        public void storeArenaMetadata(int level, net.minecraft.util.math.BlockPos origin,
                                        int width, int height,
                                        com.crackedgames.craftics.core.GridPos playerStart,
                                        boolean[][] insideMask, String biomeId) {
            String base = origin.getX() + "," + origin.getY() + "," + origin.getZ()
                + "," + width + "," + height + "," + playerStart.x() + "," + playerStart.z();
            // Polygon arenas: pack the playable mask (row-major bits, idx = x*h + z)
            // as a trailing hex field so scanExisting can restore the shape. Without
            // it a pre-generated polygon arena reloads as a full rectangle, and mobs
            // spawn / the cursor hovers outside the drawn outline.
            if (insideMask != null) {
                base += "," + packMask(insideMask, width, height);
            }
            // Biome stamp: a prefixed trailing field ("b=<id>") that records which
            // biome this cached arena was built for. New Game+ re-rolls the branch
            // order while the arena cache is keyed only by level number, so on a new
            // cycle a level can resolve to a different biome than the one cached here
            // (e.g. mountain boss vs jungle arena). On load we compare this stamp to
            // the level's current biome and rebuild on mismatch. The "b=" prefix is
            // scanned by name, not position, so it never disturbs the positional
            // metadata/mask fields and old saves (no stamp) simply read as null.
            if (biomeId != null && !biomeId.isEmpty()) {
                base += ",b=" + biomeId;
            }
            preBuiltArenas.put(level, base);
        }

        private static String packMask(boolean[][] mask, int w, int h) {
            byte[] bytes = new byte[(w * h + 7) / 8];
            for (int x = 0; x < w && x < mask.length; x++) {
                for (int z = 0; z < h && z < mask[x].length; z++) {
                    if (mask[x][z]) {
                        int idx = x * h + z;
                        bytes[idx >> 3] |= (byte) (1 << (idx & 7));
                    }
                }
            }
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        }

        /** Returns int[]{originX, originY, originZ, width, height, playerX, playerZ} or null.
         *  Tolerates a trailing 8th polygon-mask field (see {@link #getArenaMask}). */
        public int[] getArenaMetadata(int level) {
            String raw = preBuiltArenas.get(level);
            if (raw == null) return null;
            String[] parts = raw.split(",");
            if (parts.length < 7) return null;
            try {
                int[] result = new int[7];
                for (int i = 0; i < 7; i++) result[i] = Integer.parseInt(parts[i]);
                return result;
            } catch (NumberFormatException e) {
                return null;
            }
        }

        /** The biome id this level's cached arena was built for, or {@code null}
         *  for older metadata saved before the biome stamp existed. Used to detect
         *  a New Game+ branch re-roll that points this level at a different biome
         *  than the one physically cached, so the arena can be rebuilt to match. */
        public String getArenaBiome(int level) {
            String raw = preBuiltArenas.get(level);
            if (raw == null) return null;
            for (String part : raw.split(",")) {
                if (part.startsWith("b=")) return part.substring(2);
            }
            return null;
        }

        /** Drop the cached arena metadata for a level so the next visit rebuilds it. */
        public void invalidateArena(int level) {
            preBuiltArenas.remove(level);
        }

        /** The stored polygon mask for a pre-built arena, or {@code null} for a
         *  rectangular arena (or older metadata saved before masks were packed). */
        public boolean[][] getArenaMask(int level, int w, int h) {
            String raw = preBuiltArenas.get(level);
            if (raw == null) return null;
            String[] parts = raw.split(",");
            if (parts.length < 8) return null;
            String hex = parts[7];
            // A biome stamp ("b=...") can occupy field 7 when the arena is
            // rectangular (no mask). That is not a mask; treat it as "no mask".
            if (hex.startsWith("b=")) return null;
            if (hex.isEmpty() || w <= 0 || h <= 0) return null;
            try {
                byte[] bytes = new byte[hex.length() / 2];
                for (int i = 0; i < bytes.length; i++) {
                    bytes[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
                }
                boolean[][] mask = new boolean[w][h];
                for (int x = 0; x < w; x++) {
                    for (int z = 0; z < h; z++) {
                        int idx = x * h + z;
                        int bi = idx >> 3;
                        if (bi < bytes.length) mask[x][z] = (bytes[bi] & (1 << (idx & 7))) != 0;
                    }
                }
                return mask;
            } catch (Exception e) {
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

        /**
         * Live battle-party mob UUID list - mutable and order-preserving. The
         * effective cap is dynamic ({@code PartyMobs.partyCap}: 1 + the player's
         * Pet Affinity level). Always call {@code markDirty()} after changing it.
         */
        public java.util.List<UUID> getPartyMobs() { return partyMobs; }

        /** Whether {@code mobUuid} is currently in the player's battle party. */
        public boolean isPartyMob(UUID mobUuid) { return partyMobs.contains(mobUuid); }

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
            return NgPlusScaling.multiplier(ngPlusLevel);
        }

        /** Guards {@link #checkCampaignStamp()} so it runs at most once per loaded instance. */
        private transient boolean campaignStampChecked = false;

        /**
         * Compare this player's stamped {@code activeCampaignId} against the currently-active
         * campaign on world load. A new/unstamped run (empty stamp, including pre-campaign
         * saves) silently adopts the active campaign's id. A mismatch is logged once and the
         * run state is left untouched - we never silently remap a run onto a different campaign.
         * Runs at most once per loaded {@link PlayerData} instance.
         */
        public void checkCampaignStamp() {
            if (campaignStampChecked) return;
            campaignStampChecked = true;
            String active = com.crackedgames.craftics.level.campaign.CampaignManager.active() != null
                ? com.crackedgames.craftics.level.campaign.CampaignManager.active().id() : "";
            if (activeCampaignId == null || activeCampaignId.isEmpty()) {
                // New/unstamped world (or pre-campaign save) - stamp it now with the active campaign.
                activeCampaignId = active;
            } else if (!activeCampaignId.equals(active)) {
                com.crackedgames.craftics.CrafticsMod.LOGGER.warn(
                    "World was last played on campaign '{}' but the active campaign is now '{}'. "
                    + "Run state may be inconsistent; not remapping.", activeCampaignId, active);
            }
        }

        public NbtCompound toNbt() {
            NbtCompound nbt = new NbtCompound();
            nbt.putInt("highestBiomeUnlocked", highestBiomeUnlocked);
            nbt.putInt("emeralds", emeralds);
            nbt.putString("activeBiomeId", activeBiomeId);
            nbt.putString("activeCampaignId", activeCampaignId);
            nbt.putInt("activeBiomeLevelIndex", activeBiomeLevelIndex);
            nbt.putInt("branchChoice", branchChoice);
            nbt.putString("discoveredBiomes", discoveredBiomes);
            nbt.putInt("ngPlusLevel", ngPlusLevel);
            nbt.putBoolean("inCombat", inCombat);
            nbt.putBoolean("starterGuideGranted", starterGuideGranted);
            nbt.putInt("worldSlot", worldSlot);
            nbt.putInt("lockedMoveSlot", lockedMoveSlot);
            nbt.putBoolean("personalHubBuilt", personalHubBuilt);
            nbt.putInt("personalHubVersion", personalHubVersion);
            nbt.putBoolean("scaleHpPerLevelEnabled", scaleHpPerLevelEnabled);
            nbt.putBoolean("hardcoreIsland", hardcoreIsland);
            nbt.putBoolean("islandMigrated", islandMigrated);
            nbt.putBoolean("raidDefeated", raidDefeated);
            // Pipe-delimited (mirrors unlockedGuideEntries). Safe: TraderType enum names and
            // Identifier-validated BarterCategory ids can never contain '|'.
            nbt.putString("metTraders", String.join("|", metTraders));
            nbt.putString("metBarterers", String.join("|", metBarterers));
            NbtList petList = new NbtList();
            hubPets.forEach(petList::add);
            nbt.put("hubPets", petList);
            nbt.putString("unlockedGuideEntries", String.join("|", unlockedGuideEntries));
            StringBuilder partyMobsRaw = new StringBuilder();
            for (UUID mob : partyMobs) {
                if (partyMobsRaw.length() > 0) partyMobsRaw.append('|');
                partyMobsRaw.append(mob.toString());
            }
            nbt.putString("partyMobs", partyMobsRaw.toString());
            nbt.putInt("hubSpawnX", hubSpawnX);
            nbt.putInt("hubSpawnY", hubSpawnY);
            nbt.putInt("hubSpawnZ", hubSpawnZ);
            nbt.putBoolean("arenasPreGenerated", arenasPreGenerated);
            NbtCompound arenasMeta = new NbtCompound();
            for (var entry : preBuiltArenas.entrySet()) {
                arenasMeta.putString(String.valueOf(entry.getKey()), entry.getValue());
            }
            nbt.put("preBuiltArenas", arenasMeta);
            NbtCompound bossKillsNbt = new NbtCompound();
            for (var e : bossKills.entrySet()) {
                bossKillsNbt.putInt(e.getKey(), e.getValue());
            }
            nbt.put("bossKills", bossKillsNbt);
            nbt.putBoolean("infiniteActive", infiniteActive);
            nbt.putInt("infiniteBiomesCleared", infiniteBiomesCleared);
            nbt.putInt("infiniteScore", infiniteScore);
            nbt.putString("infiniteParticipants", infiniteParticipants);
            nbt.putString("infiniteHostRef", infiniteHostRef);
            nbt.putInt("highestInfiniteScore", highestInfiniteScore);
            nbt.putString("infiniteRunHost", infiniteRunHost);
            nbt.putBoolean("infiniteStashActive", infiniteStashActive);
            nbt.put("infiniteStashInventory", infiniteStashInventory.copy());
            nbt.putInt("infiniteStashSelectedSlot", infiniteStashSelectedSlot);
            nbt.putString("infiniteStashStats", infiniteStashStats);
            nbt.putBoolean("infiniteSuspended", infiniteSuspended);
            nbt.put("infiniteParkedInventory", infiniteParkedInventory.copy());
            nbt.putInt("infiniteParkedSelectedSlot", infiniteParkedSelectedSlot);
            nbt.putString("infiniteParkedStats", infiniteParkedStats);
            nbt.putString("infiniteParkedBiomeId", infiniteParkedBiomeId);
            nbt.putInt("infiniteParkedLevelIndex", infiniteParkedLevelIndex);
            nbt.putString("lastKnownName", lastKnownName);
            return nbt;
        }

        //? if <=1.21.4 {
        public static PlayerData fromNbt(NbtCompound nbt) {
            PlayerData pd = new PlayerData();
            pd.highestBiomeUnlocked = nbt.getInt("highestBiomeUnlocked");
            pd.emeralds = nbt.getInt("emeralds");
            pd.activeBiomeId = nbt.getString("activeBiomeId");
            pd.activeCampaignId = nbt.contains("activeCampaignId") ? nbt.getString("activeCampaignId") : "";
            pd.activeBiomeLevelIndex = nbt.getInt("activeBiomeLevelIndex");
            pd.branchChoice = nbt.contains("branchChoice") ? nbt.getInt("branchChoice") : -1;
            pd.discoveredBiomes = nbt.contains("discoveredBiomes") ? nbt.getString("discoveredBiomes") : "";
            pd.ngPlusLevel = nbt.contains("ngPlusLevel") ? nbt.getInt("ngPlusLevel") : 0;
            pd.inCombat = nbt.contains("inCombat") && nbt.getBoolean("inCombat");
            pd.starterGuideGranted = nbt.contains("starterGuideGranted") && nbt.getBoolean("starterGuideGranted");
            pd.worldSlot = nbt.contains("worldSlot") ? nbt.getInt("worldSlot") : -1;
            pd.lockedMoveSlot = nbt.contains("lockedMoveSlot") ? Math.max(0, Math.min(8, nbt.getInt("lockedMoveSlot"))) : 8;
            pd.personalHubBuilt = nbt.contains("personalHubBuilt") && nbt.getBoolean("personalHubBuilt");
            pd.personalHubVersion = nbt.contains("personalHubVersion") ? nbt.getInt("personalHubVersion") : 0;
            // Default: true (matches global config default) - islands created before this
            // field existed keep the old scaling behavior.
            pd.scaleHpPerLevelEnabled = !nbt.contains("scaleHpPerLevelEnabled") || nbt.getBoolean("scaleHpPerLevelEnabled");
            pd.hardcoreIsland = nbt.contains("hardcoreIsland") && nbt.getBoolean("hardcoreIsland");
            pd.islandMigrated = nbt.contains("islandMigrated") && nbt.getBoolean("islandMigrated");
            pd.raidDefeated = nbt.contains("raidDefeated") && nbt.getBoolean("raidDefeated");
            if (nbt.contains("metTraders")) {
                String raw = nbt.getString("metTraders");
                if (!raw.isEmpty()) {
                    for (String entry : raw.split("\\|")) {
                        if (!entry.isEmpty()) pd.metTraders.add(entry);
                    }
                }
            }
            if (nbt.contains("metBarterers")) {
                String raw = nbt.getString("metBarterers");
                if (!raw.isEmpty()) {
                    for (String entry : raw.split("\\|")) {
                        if (!entry.isEmpty()) pd.metBarterers.add(entry);
                    }
                }
            }
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
            if (nbt.contains("partyMobs")) {
                String partyRaw = nbt.getString("partyMobs");
                if (!partyRaw.isEmpty()) {
                    for (String entry : partyRaw.split("\\|")) {
                        if (entry.isEmpty()) continue;
                        try { pd.partyMobs.add(UUID.fromString(entry)); }
                        catch (IllegalArgumentException ignored) {}
                    }
                }
            }
            pd.hubSpawnX = nbt.contains("hubSpawnX") ? nbt.getInt("hubSpawnX") : -1;
            pd.hubSpawnY = nbt.contains("hubSpawnY") ? nbt.getInt("hubSpawnY") : -1;
            pd.hubSpawnZ = nbt.contains("hubSpawnZ") ? nbt.getInt("hubSpawnZ") : -1;
            pd.arenasPreGenerated = nbt.contains("arenasPreGenerated") && nbt.getBoolean("arenasPreGenerated");
            if (nbt.contains("preBuiltArenas")) {
                NbtCompound arenasMeta = nbt.getCompound("preBuiltArenas");
                for (String key : arenasMeta.getKeys()) {
                    try {
                        pd.preBuiltArenas.put(Integer.parseInt(key), arenasMeta.getString(key));
                    } catch (NumberFormatException ignored) {}
                }
            }
            if (nbt.contains("bossKills")) {
                NbtCompound bk = nbt.getCompound("bossKills");
                for (String key : bk.getKeys()) {
                    pd.bossKills.put(key, bk.getInt(key));
                }
            }
            pd.infiniteActive = nbt.contains("infiniteActive") && nbt.getBoolean("infiniteActive");
            pd.infiniteBiomesCleared = nbt.contains("infiniteBiomesCleared") ? nbt.getInt("infiniteBiomesCleared") : 0;
            pd.infiniteScore = nbt.contains("infiniteScore") ? nbt.getInt("infiniteScore") : 0;
            pd.infiniteParticipants = nbt.contains("infiniteParticipants") ? nbt.getString("infiniteParticipants") : "";
            pd.infiniteHostRef = nbt.contains("infiniteHostRef") ? nbt.getString("infiniteHostRef") : "";
            pd.highestInfiniteScore = nbt.contains("highestInfiniteScore") ? nbt.getInt("highestInfiniteScore") : 0;
            pd.infiniteRunHost = nbt.contains("infiniteRunHost") ? nbt.getString("infiniteRunHost") : "";
            pd.infiniteStashActive = nbt.contains("infiniteStashActive") && nbt.getBoolean("infiniteStashActive");
            if (nbt.contains("infiniteStashInventory")) {
                pd.infiniteStashInventory = nbt.getList("infiniteStashInventory",
                    net.minecraft.nbt.NbtElement.COMPOUND_TYPE);
            }
            pd.infiniteStashSelectedSlot = nbt.contains("infiniteStashSelectedSlot") ? nbt.getInt("infiniteStashSelectedSlot") : 0;
            pd.infiniteStashStats = nbt.contains("infiniteStashStats") ? nbt.getString("infiniteStashStats") : "";
            pd.infiniteSuspended = nbt.contains("infiniteSuspended") && nbt.getBoolean("infiniteSuspended");
            if (nbt.contains("infiniteParkedInventory")) {
                pd.infiniteParkedInventory = nbt.getList("infiniteParkedInventory",
                    net.minecraft.nbt.NbtElement.COMPOUND_TYPE);
            }
            pd.infiniteParkedSelectedSlot = nbt.contains("infiniteParkedSelectedSlot") ? nbt.getInt("infiniteParkedSelectedSlot") : 0;
            pd.infiniteParkedStats = nbt.contains("infiniteParkedStats") ? nbt.getString("infiniteParkedStats") : "";
            pd.infiniteParkedBiomeId = nbt.contains("infiniteParkedBiomeId") ? nbt.getString("infiniteParkedBiomeId") : "";
            pd.infiniteParkedLevelIndex = nbt.contains("infiniteParkedLevelIndex") ? nbt.getInt("infiniteParkedLevelIndex") : 0;
            pd.lastKnownName = nbt.contains("lastKnownName") ? nbt.getString("lastKnownName") : "";
            return pd;
        }
        //?} else {
        /*public static PlayerData fromNbt(NbtCompound nbt) {
            PlayerData pd = new PlayerData();
            pd.highestBiomeUnlocked = nbt.getInt("highestBiomeUnlocked", 0);
            pd.emeralds = nbt.getInt("emeralds", 0);
            pd.activeBiomeId = nbt.getString("activeBiomeId", "");
            pd.activeCampaignId = nbt.getString("activeCampaignId", "");
            pd.activeBiomeLevelIndex = nbt.getInt("activeBiomeLevelIndex", 0);
            pd.branchChoice = nbt.getInt("branchChoice", -1);
            pd.discoveredBiomes = nbt.getString("discoveredBiomes", "");
            pd.ngPlusLevel = nbt.getInt("ngPlusLevel", 0);
            pd.inCombat = nbt.getBoolean("inCombat", false);
            pd.starterGuideGranted = nbt.getBoolean("starterGuideGranted", false);
            pd.worldSlot = nbt.getInt("worldSlot", -1);
            pd.lockedMoveSlot = Math.max(0, Math.min(8, nbt.getInt("lockedMoveSlot", 8)));
            pd.personalHubBuilt = nbt.getBoolean("personalHubBuilt", false);
            pd.personalHubVersion = nbt.getInt("personalHubVersion", 0);
            pd.scaleHpPerLevelEnabled = nbt.getBoolean("scaleHpPerLevelEnabled", true);
            pd.hardcoreIsland = nbt.getBoolean("hardcoreIsland", false);
            pd.islandMigrated = nbt.getBoolean("islandMigrated", false);
            pd.raidDefeated = nbt.getBoolean("raidDefeated", false);
            String metTradersRaw = nbt.getString("metTraders", "");
            if (!metTradersRaw.isEmpty()) {
                for (String entry : metTradersRaw.split("\\|")) {
                    if (!entry.isEmpty()) pd.metTraders.add(entry);
                }
            }
            String metBartererRaw = nbt.getString("metBarterers", "");
            if (!metBartererRaw.isEmpty()) {
                for (String entry : metBartererRaw.split("\\|")) {
                    if (!entry.isEmpty()) pd.metBarterers.add(entry);
                }
            }
            NbtList pl = nbt.getListOrEmpty("hubPets");
            for (int i = 0; i < pl.size(); i++) pl.getCompound(i).ifPresent(pd.hubPets::add);
            String guideRaw = nbt.getString("unlockedGuideEntries", "");
            if (!guideRaw.isEmpty()) {
                for (String entry : guideRaw.split("\\|")) {
                    if (!entry.isEmpty()) pd.unlockedGuideEntries.add(entry);
                }
            }
            String partyRaw = nbt.getString("partyMobs", "");
            if (!partyRaw.isEmpty()) {
                for (String entry : partyRaw.split("\\|")) {
                    if (entry.isEmpty()) continue;
                    try { pd.partyMobs.add(UUID.fromString(entry)); }
                    catch (IllegalArgumentException ignored) {}
                }
            }
            pd.hubSpawnX = nbt.getInt("hubSpawnX", -1);
            pd.hubSpawnY = nbt.getInt("hubSpawnY", -1);
            pd.hubSpawnZ = nbt.getInt("hubSpawnZ", -1);
            pd.arenasPreGenerated = nbt.getBoolean("arenasPreGenerated", false);
            NbtCompound arenasMeta = nbt.getCompoundOrEmpty("preBuiltArenas");
            for (String key : arenasMeta.getKeys()) {
                try {
                    pd.preBuiltArenas.put(Integer.parseInt(key), arenasMeta.getString(key, ""));
                } catch (NumberFormatException ignored) {}
            }
            NbtCompound bk = nbt.getCompoundOrEmpty("bossKills");
            for (String key : bk.getKeys()) {
                pd.bossKills.put(key, bk.getInt(key, 0));
            }
            pd.infiniteActive = nbt.getBoolean("infiniteActive", false);
            pd.infiniteBiomesCleared = nbt.getInt("infiniteBiomesCleared", 0);
            pd.infiniteScore = nbt.getInt("infiniteScore", 0);
            pd.infiniteParticipants = nbt.getString("infiniteParticipants", "");
            pd.infiniteHostRef = nbt.getString("infiniteHostRef", "");
            pd.highestInfiniteScore = nbt.getInt("highestInfiniteScore", 0);
            pd.infiniteRunHost = nbt.getString("infiniteRunHost", "");
            pd.infiniteStashActive = nbt.getBoolean("infiniteStashActive", false);
            pd.infiniteStashInventory = nbt.getListOrEmpty("infiniteStashInventory");
            pd.infiniteStashSelectedSlot = nbt.getInt("infiniteStashSelectedSlot", 0);
            pd.infiniteStashStats = nbt.getString("infiniteStashStats", "");
            pd.infiniteSuspended = nbt.getBoolean("infiniteSuspended", false);
            pd.infiniteParkedInventory = nbt.getListOrEmpty("infiniteParkedInventory");
            pd.infiniteParkedSelectedSlot = nbt.getInt("infiniteParkedSelectedSlot", 0);
            pd.infiniteParkedStats = nbt.getString("infiniteParkedStats", "");
            pd.infiniteParkedBiomeId = nbt.getString("infiniteParkedBiomeId", "");
            pd.infiniteParkedLevelIndex = nbt.getInt("infiniteParkedLevelIndex", 0);
            pd.lastKnownName = nbt.getString("lastKnownName", "");
            return pd;
        }
        *///?}
    }

    public CrafticsSavedData() {}

    /** Always call markDirty() after modifying */
    public PlayerData getPlayerData(UUID playerId) {
        PlayerData pd = players.computeIfAbsent(playerId, id -> new PlayerData());
        // Verify the run's campaign stamp the first time this data is touched after load.
        // Self-guarded: stamps a new/unstamped run with the active campaign, or warns once
        // on a mismatch. No-op on every subsequent access for the same instance.
        pd.checkCampaignStamp();
        return pd;
    }

    public PlayerData getPlayerData(ServerPlayerEntity player) {
        return getPlayerData(player.getUuid());
    }

    /** Flag an offline hardcore-run participant for wipe-on-join. */
    public void addPendingHardcoreWipe(UUID playerId) {
        if (pendingHardcoreWipe.add(playerId)) markDirty();
    }

    /** True exactly once per flagged player: removes the flag when found. */
    public boolean consumePendingHardcoreWipe(UUID playerId) {
        boolean had = pendingHardcoreWipe.remove(playerId);
        if (had) markDirty();
        return had;
    }

    /**
     * Hardcore wipe: replace the owner's island record with a fresh one, freeing
     * the world slot so {@code /new} works again. Keeps account-ish fields only.
     * starterGuideGranted stays true because the wipe hands the book back directly.
     */
    public void resetPlayerData(UUID playerId) {
        PlayerData old = players.get(playerId);
        PlayerData fresh = new PlayerData();
        if (old != null) {
            fresh.lastKnownName = old.lastKnownName;
            fresh.highestInfiniteScore = old.highestInfiniteScore;
        }
        fresh.starterGuideGranted = true;
        players.put(playerId, fresh);
        markDirty();
    }

    //? if <=1.21.4 {
    public static CrafticsSavedData fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        CrafticsSavedData data = new CrafticsSavedData();
        data.hubBuilt = nbt.getBoolean("hubBuilt");
        data.hubVersion = nbt.getInt("hubVersion");
        data.nextWorldSlot = nbt.contains("nextWorldSlot") ? nbt.getInt("nextWorldSlot") : 0;
        data.lobbySpawnX = nbt.contains("lobbySpawnX") ? nbt.getInt("lobbySpawnX") : 0;
        data.lobbySpawnY = nbt.contains("lobbySpawnY") ? nbt.getInt("lobbySpawnY") : Integer.MIN_VALUE;
        data.lobbySpawnZ = nbt.contains("lobbySpawnZ") ? nbt.getInt("lobbySpawnZ") : 0;
        data.lobbySpawnYaw = nbt.contains("lobbySpawnYaw") ? nbt.getFloat("lobbySpawnYaw") : 0f;

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
            // Placeholder UUID - claimed by first player to join
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

        if (nbt.contains("pendingHardcoreWipe")) {
            for (String s : nbt.getString("pendingHardcoreWipe").split("\\|")) {
                if (s.isEmpty()) continue;
                try { data.pendingHardcoreWipe.add(UUID.fromString(s)); }
                catch (IllegalArgumentException ignored) {}
            }
        }

        return data;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        nbt.putBoolean("hubBuilt", hubBuilt);
        nbt.putInt("hubVersion", hubVersion);
        nbt.putInt("nextWorldSlot", nextWorldSlot);
        nbt.putInt("lobbySpawnX", lobbySpawnX);
        nbt.putInt("lobbySpawnY", lobbySpawnY);
        nbt.putInt("lobbySpawnZ", lobbySpawnZ);
        nbt.putFloat("lobbySpawnYaw", lobbySpawnYaw);

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

        nbt.putString("pendingHardcoreWipe", joinPendingHardcoreWipe());

        return nbt;
    }

    private static final PersistentState.Type<CrafticsSavedData> TYPE =
        new PersistentState.Type<>(CrafticsSavedData::new, CrafticsSavedData::fromNbt, null);

    /** Single global state, always rooted at the overworld's PersistentStateManager.
     *  Island dims (craftics:island/&lt;uuid&gt;) intentionally read the SAME state, so
     *  passing an island world here still returns the one shared save. Do not switch
     *  this to {@code world.getPersistentStateManager()} - it would fork per-dim state. */
    public static CrafticsSavedData get(ServerWorld world) {
        return world.getServer().getOverworld().getPersistentStateManager().getOrCreate(TYPE, "craftics_data");
    }
    //?} else {
    /*private static CrafticsSavedData decodeNbt(NbtCompound nbt) {
        CrafticsSavedData data = new CrafticsSavedData();
        data.hubBuilt = nbt.getBoolean("hubBuilt", false);
        data.hubVersion = nbt.getInt("hubVersion", 0);
        data.nextWorldSlot = nbt.getInt("nextWorldSlot", 0);
        data.lobbySpawnX = nbt.getInt("lobbySpawnX", 0);
        data.lobbySpawnY = nbt.getInt("lobbySpawnY", Integer.MIN_VALUE);
        data.lobbySpawnZ = nbt.getInt("lobbySpawnZ", 0);
        data.lobbySpawnYaw = nbt.getFloat("lobbySpawnYaw", 0f);

        NbtCompound playersNbt = nbt.getCompoundOrEmpty("players");
        for (String key : playersNbt.getKeys()) {
            try {
                UUID uuid = UUID.fromString(key);
                playersNbt.getCompound(key).ifPresent(playerNbt ->
                    data.players.put(uuid, PlayerData.fromNbt(playerNbt)));
            } catch (IllegalArgumentException ignored) {}
        }

        // Legacy migration from single-player save format
        if (nbt.contains("highestBiomeUnlocked") && !nbt.contains("players")) {
            PlayerData legacy = new PlayerData();
            legacy.highestBiomeUnlocked = nbt.getInt("highestBiomeUnlocked", 0);
            legacy.emeralds = nbt.getInt("emeralds", 0);
            legacy.activeBiomeId = nbt.getString("activeBiomeId", "");
            legacy.activeBiomeLevelIndex = nbt.getInt("activeBiomeLevelIndex", 0);
            legacy.branchChoice = nbt.getInt("branchChoice", -1);
            legacy.discoveredBiomes = nbt.getString("discoveredBiomes", "");
            legacy.ngPlusLevel = nbt.getInt("ngPlusLevel", 0);
            data.players.put(new UUID(0, 0), legacy);
        }

        NbtCompound partiesNbt = nbt.getCompoundOrEmpty("parties");
        for (String key : partiesNbt.getKeys()) {
            try {
                UUID partyId = UUID.fromString(key);
                partiesNbt.getCompound(key).ifPresent(partyNbt -> {
                    Party party = Party.fromNbt(partyNbt);
                    data.parties.put(partyId, party);
                    for (UUID memberUuid : party.getMemberUuids()) {
                        data.playerToParty.put(memberUuid, partyId);
                    }
                });
            } catch (IllegalArgumentException ignored) {}
        }

        for (String s : nbt.getString("pendingHardcoreWipe", "").split("\\|")) {
            if (s.isEmpty()) continue;
            try { data.pendingHardcoreWipe.add(UUID.fromString(s)); }
            catch (IllegalArgumentException ignored) {}
        }

        return data;
    }

    private NbtCompound encodeNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putBoolean("hubBuilt", hubBuilt);
        nbt.putInt("hubVersion", hubVersion);
        nbt.putInt("nextWorldSlot", nextWorldSlot);
        nbt.putInt("lobbySpawnX", lobbySpawnX);
        nbt.putInt("lobbySpawnY", lobbySpawnY);
        nbt.putInt("lobbySpawnZ", lobbySpawnZ);
        nbt.putFloat("lobbySpawnYaw", lobbySpawnYaw);

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

        nbt.putString("pendingHardcoreWipe", joinPendingHardcoreWipe());

        return nbt;
    }

    private static final Codec<CrafticsSavedData> CODEC = NbtCompound.CODEC.xmap(
        CrafticsSavedData::decodeNbt,
        CrafticsSavedData::encodeNbt
    );

    private static final PersistentStateType<CrafticsSavedData> TYPE =
        new PersistentStateType<>("craftics_data", CrafticsSavedData::new, CODEC, null);

    // Single global state, always overworld-rooted; island dims read the same save.
    public static CrafticsSavedData get(ServerWorld world) {
        return world.getServer().getOverworld().getPersistentStateManager().getOrCreate(TYPE);
    }
    *///?}

    /** Pipe-joined uuid list for NBT (mirrors the metTraders string pattern). */
    private String joinPendingHardcoreWipe() {
        StringBuilder sb = new StringBuilder();
        for (UUID id : pendingHardcoreWipe) {
            if (sb.length() > 0) sb.append('|');
            sb.append(id);
        }
        return sb.toString();
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

    /** All player UUIDs with saved data (defensive copy) - used to enumerate every
     *  world owner's fixed event room for the cleanup command. */
    public java.util.Set<UUID> getAllPlayerIds() {
        return new java.util.HashSet<>(players.keySet());
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

    /** Get the world origin (hub center) for a player's personal island, or null if not
     *  created. Fixed at the island dim origin - {@code worldSlot} is only the
     *  "has an island" marker now (>=0), never a coordinate. */
    public net.minecraft.util.math.BlockPos getWorldOrigin(UUID playerId) {
        PlayerData pd = getPlayerData(playerId);
        if (pd.worldSlot < 0) return null; // still the "has an island" marker
        return new net.minecraft.util.math.BlockPos(0, HUB_Y, 0);
    }

    /** Alias for getWorldOrigin - returns the center of the player's personal hub. */
    public net.minecraft.util.math.BlockPos getHubOrigin(UUID playerId) {
        return getWorldOrigin(playerId);
    }

    /**
     * Returns the podzol-based spawn point if set, otherwise falls back to hub center.
     * This is the position players actually teleport to for /home.
     */
    public net.minecraft.util.math.BlockPos getHubSpawnPos(UUID playerId) {
        PlayerData pd = getPlayerData(playerId);
        if (pd.hubSpawnX >= 0 && pd.hubSpawnY >= 0 && pd.hubSpawnZ >= 0) {
            return new net.minecraft.util.math.BlockPos(pd.hubSpawnX, pd.hubSpawnY, pd.hubSpawnZ);
        }
        return getHubOrigin(playerId);
    }

    /** Get the arena origin for a specific level within the player's island dim.
     *  Numbered arenas march out along +X: level N at (1000 + N*300, 100, 0). */
    public net.minecraft.util.math.BlockPos getArenaOrigin(UUID playerId, int level) {
        PlayerData pd = getPlayerData(playerId);
        if (pd.worldSlot < 0) return null;
        return new net.minecraft.util.math.BlockPos(ARENA_BASE_X + level * ARENA_STRIDE_X, ROOM_Y, 0);
    }

    /** Get the trader area origin within the player's island dim. */
    public net.minecraft.util.math.BlockPos getTraderOrigin(UUID playerId) {
        PlayerData pd = getPlayerData(playerId);
        if (pd.worldSlot < 0) return null;
        return new net.minecraft.util.math.BlockPos(ROOM_X, ROOM_Y, 500);
    }

    /** Get the dig site origin within the player's island dim. */
    public net.minecraft.util.math.BlockPos getDigSiteOrigin(UUID playerId) {
        PlayerData pd = getPlayerData(playerId);
        if (pd.worldSlot < 0) return null;
        return new net.minecraft.util.math.BlockPos(ROOM_X, ROOM_Y, 600);
    }

    /**
     * Get a dedicated "scratch" arena origin for addon event levels (e.g. the
     * Artifacts mimic encounter). Placed inside the player's lane so chunks are
     * already loaded near the hub, but offset away from the numbered arena row
     * so it can't collide with a pre-built arena.
     */
    public net.minecraft.util.math.BlockPos getEventArenaOrigin(UUID playerId) {
        PlayerData pd = getPlayerData(playerId);
        if (pd.worldSlot < 0) return null;
        return new net.minecraft.util.math.BlockPos(ROOM_X, ROOM_Y, 700);
    }

    /**
     * Dedicated origin slot for trial chamber arenas. Trial chamber level
     * numbers are 9000+ to avoid colliding with regular biome levels; routing
     * those through the standard {@code level * 300} arena origin would send
     * them out to world X ~2.7 million where single-precision float math in the
     * client overlay renderer produces visible half-block offsets and per-frame
     * jitter. Park them in the fixed room column instead so the coordinates stay
     * small and well-conditioned in float range.
     */
    public net.minecraft.util.math.BlockPos getTrialChamberOrigin(UUID playerId) {
        PlayerData pd = getPlayerData(playerId);
        if (pd.worldSlot < 0) return null;
        return new net.minecraft.util.math.BlockPos(ROOM_X, ROOM_Y, 800);
    }

    /** Merchant-scene origin within the player's island dim, one Z slot per scene type so the
     *  Village (Z 900) and Bartering Station (Z 1000) can be active independently. Both sit
     *  past trader (Z 500), dig (Z 600), event (Z 700), and trial (Z 800) in the fixed room column. */
    public net.minecraft.util.math.BlockPos getSceneOrigin(UUID playerId, String sceneName) {
        PlayerData pd = getPlayerData(playerId);
        if (pd.worldSlot < 0) return null;
        int sceneZ = "barter_station".equals(sceneName) ? 1000 : 900;
        return new net.minecraft.util.math.BlockPos(ROOM_X, ROOM_Y, sceneZ);
    }

    /** Infinite-mode rest room origin: last slot in the fixed room column (Z 1100),
     *  past the merchant scenes. Rebuilt on every boss clear by RestRoomBuilder. */
    public net.minecraft.util.math.BlockPos getRestRoomOrigin(UUID playerId) {
        PlayerData pd = getPlayerData(playerId);
        if (pd.worldSlot < 0) return null;
        return new net.minecraft.util.math.BlockPos(ROOM_X, ROOM_Y, 1100);
    }

    /** Read-only view of every stored player record - used by the infinite-mode
     *  leaderboard, which ranks offline players too. */
    public Map<UUID, PlayerData> getAllPlayerData() {
        return java.util.Collections.unmodifiableMap(players);
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
     * Get the hub teleport position for a player. Resolves to the podzol-based spawn
     * in their (or party leader's) personal hub, or the central lobby if no world exists.
     */
    public net.minecraft.util.math.BlockPos getHubTeleportPos(UUID playerId) {
        UUID owner = getEffectiveWorldOwner(playerId);
        net.minecraft.util.math.BlockPos spawn = getHubSpawnPos(owner);
        if (spawn != null) return spawn;
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
