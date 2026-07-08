package com.crackedgames.craftics.combat;

import com.crackedgames.craftics.CrafticsMod;
import com.crackedgames.craftics.combat.ai.AIRegistry;
import com.crackedgames.craftics.combat.ai.boss.InfiniteAbilityPool;
import com.crackedgames.craftics.combat.ai.boss.InfiniteBossAI;
import com.crackedgames.craftics.level.BiomeTemplate;
import com.crackedgames.craftics.level.InfiniteSpec;
import com.crackedgames.craftics.network.PlayerStatsSyncPayload;
import com.crackedgames.craftics.world.CrafticsSavedData;
import com.crackedgames.craftics.world.IslandDimensions;
import com.crackedgames.craftics.world.RestRoomBuilder;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * INFINITE MODE run lifecycle. An infinite run is a roguelike take on the
 * normal biome run:
 *
 * <ul>
 *   <li><b>Entry</b>: the Level Select block's "Infinite Mode" button or
 *       {@code /craftics infinite} sends {@code StartLevelPayload(START_ID)};
 *       {@link RunInviteManager} runs its normal party Yes/No lobby, then calls
 *       {@link #startRun} before the first arena is built.</li>
 *   <li><b>Fresh start</b>: every participant's inventory AND progression
 *       (level / stats / affinities) are stashed and replaced with an empty
 *       inventory + level-1 profile. Emeralds are shared with the main save
 *       and are the ONE thing a run pays out permanently.</li>
 *   <li><b>Loop</b>: plains first, 5 levels per biome ending in a randomized
 *       boss ({@link InfiniteBossAI}). Each boss clear = +1 score, a level-up
 *       (alternating stat point / affinity pick), then the rest room - ring
 *       the bell and a random biome starts, forever. Difficulty keys off the
 *       cleared count (see {@link InfiniteSpec}); every 10 biomes the boss
 *       gains +1 move in its pool and +1 action per turn.</li>
 *   <li><b>Exit</b>: Go Home after a level, {@code /home} from the rest room,
 *       party wipe, or the host logging out. Run items vanish, the stash comes
 *       back, the best score is banked on the global board.</li>
 * </ul>
 *
 * Run state lives on the HOST's {@code PlayerData} (same record that carries
 * the normal run cursor); each participant's stash lives on their own record,
 * so every piece survives a server restart.
 */
public final class InfiniteRunManager {
    private InfiniteRunManager() {}

    /** Sentinel "biome id" carried by StartLevelPayload to request an infinite run. */
    public static final String START_ID = "craftics:infinite";
    /** Every run opens in plains, mirroring the campaign. */
    public static final String STARTING_BIOME = "plains";
    /** AIRegistry key for the randomized infinite boss. */
    public static final String BOSS_AI_KEY = "boss:infinite";

    /** Standard-size (1x1) mobs the randomized boss can appear as. */
    private static final String[] BOSS_MOB_POOL = {
        "minecraft:zombie", "minecraft:husk", "minecraft:drowned", "minecraft:zombie_villager",
        "minecraft:skeleton", "minecraft:stray", "minecraft:bogged", "minecraft:wither_skeleton",
        "minecraft:pillager", "minecraft:vindicator", "minecraft:evoker", "minecraft:witch",
        "minecraft:piglin", "minecraft:piglin_brute", "minecraft:blaze", "minecraft:breeze",
        "minecraft:enderman"
    };

    /** Extra move + extra action-per-turn every this many cleared biomes. */
    private static final int ESCALATION_INTERVAL = 10;
    private static final int BASE_MOVES = 4;

    private static final Random RNG = new Random();

    // ─── Init ──────────────────────────────────────────────────────────────────

    /** Called once from CrafticsMod.onInitialize. */
    public static void init() {
        // Fallback registry entry so aiKey lookups (grid size probes, resolveAi
        // fallback) never land on the default passive AI. Real fights always pin
        // a purpose-built instance via setAiInstance at spawn.
        AIRegistry.registerBoss(BOSS_AI_KEY,
            () -> new InfiniteBossAI(List.of(), "The Nameless Wanderer"));

        // The rest-room continue bell.
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (world.isClient() || !(player instanceof ServerPlayerEntity sp)) return ActionResult.PASS;
            if (!(world instanceof ServerWorld sw) || !IslandDimensions.isIslandWorld(sw)) return ActionResult.PASS;
            BlockPos pos = hit.getBlockPos();
            if (!world.getBlockState(pos).isOf(Blocks.BELL)) return ActionResult.PASS;
            UUID islandOwner = IslandDimensions.ownerOf(sw);
            if (islandOwner == null) return ActionResult.PASS;
            CrafticsSavedData data = CrafticsSavedData.get(sw);
            BlockPos origin = data.getRestRoomOrigin(islandOwner);
            if (origin == null || !pos.equals(RestRoomBuilder.bellPos(origin))) return ActionResult.PASS;
            return onBellRung(sp, sw, data, islandOwner);
        });
    }

    // ─── Queries ───────────────────────────────────────────────────────────────

    /** True when {@code uuid}'s PlayerData hosts an active infinite run. */
    public static boolean isHostOfActiveRun(CrafticsSavedData data, UUID uuid) {
        return uuid != null && data.getPlayerData(uuid).infiniteActive;
    }

    /**
     * The infinite parameters for a level about to be generated, or null when
     * the host isn't in an infinite run. Boss levels roll the boss here: its
     * appearance, its "The ____ ____" name, and its movepool.
     */
    public static InfiniteSpec specFor(CrafticsSavedData data, UUID hostUuid,
                                       BiomeTemplate biome, int globalLevel) {
        if (hostUuid == null) return null;
        CrafticsSavedData.PlayerData host = data.getPlayerData(hostUuid);
        if (!host.infiniteActive) return null;
        int ordinal = Math.max(0, host.infiniteBiomesCleared);
        if (biome == null || !biome.isBossLevel(globalLevel)) {
            return InfiniteSpec.forLevel(ordinal);
        }
        int moves = BASE_MOVES + host.infiniteBiomesCleared / ESCALATION_INTERVAL;
        int actionsPerTurn = 1 + host.infiniteBiomesCleared / ESCALATION_INTERVAL;
        String bossType = BOSS_MOB_POOL[RNG.nextInt(BOSS_MOB_POOL.length)];
        String bossName = generateBossName(RNG);
        List<String> abilities = InfiniteAbilityPool.rollIds(RNG, moves);
        return new InfiniteSpec(ordinal, bossType, bossName, abilities, actionsPerTurn);
    }

    // ─── Run start ─────────────────────────────────────────────────────────────

    /**
     * Initialize a fresh infinite run for {@code starter} + accepted party
     * members. Called by {@link RunInviteManager#beginRun} when it sees the
     * {@link #START_ID} sentinel; returns the real biome id to start in.
     */
    public static String startRun(ServerPlayerEntity starter, List<UUID> participants) {
        ServerWorld world = (ServerWorld) starter.getEntityWorld();
        MinecraftServer server = world.getServer();
        CrafticsSavedData data = CrafticsSavedData.get(world);
        PlayerProgression progression = PlayerProgression.get(world);
        UUID hostUuid = starter.getUuid();

        CrafticsSavedData.PlayerData host = data.getPlayerData(hostUuid);
        host.infiniteActive = true;
        host.infiniteBiomesCleared = 0;
        host.infiniteParticipants = joinUuids(participants);
        host.startBiomeRun(STARTING_BIOME);

        UUID islandOwner = data.getEffectiveWorldOwner(hostUuid);
        data.getPlayerData(islandOwner).infiniteHostRef = hostUuid.toString();

        for (UUID uuid : participants) {
            ServerPlayerEntity member = server.getPlayerManager().getPlayer(uuid);
            if (member == null) continue;
            CrafticsSavedData.PlayerData pd = data.getPlayerData(uuid);
            pd.infiniteRunHost = hostUuid.toString();
            pd.lastKnownName = member.getName().getString();
            stashAndReset(member, pd, progression);
            syncStats(member, progression, pd);
            member.sendMessage(Text.literal("§5§l∞ INFINITE MODE ∞"), false);
            member.sendMessage(Text.literal(
                "§7Your items and levels are stashed away - you start from nothing."), false);
            member.sendMessage(Text.literal(
                "§7Emeralds you earn are yours to keep. Everything else stays behind."), false);
        }
        data.markDirty();
        CrafticsMod.LOGGER.info("Infinite run started by {} with {} player(s)",
            starter.getName().getString(), participants.size());
        return STARTING_BIOME;
    }

    /** Stash inventory + progression, then hand the player a clean slate. */
    private static void stashAndReset(ServerPlayerEntity player, CrafticsSavedData.PlayerData pd,
                                      PlayerProgression progression) {
        if (!pd.infiniteStashActive) {
            pd.infiniteStashInventory = player.getInventory().writeNbt(new NbtList());
            //? if <=1.21.4 {
            pd.infiniteStashSelectedSlot = player.getInventory().selectedSlot;
            //?} else
            /*pd.infiniteStashSelectedSlot = player.getInventory().getSelectedSlot();*/
            pd.infiniteStashStats = progression.snapshotSerialized(player.getUuid());
            pd.infiniteStashActive = true;
        }
        player.getInventory().clear();
        player.getInventory().markDirty();
        // The Move item is a core control, not loot - hand it straight back.
        com.crackedgames.craftics.item.MoveSlotManager.enforce(player);
        // Infinite runs always begin with a tiny wood bootstrap for crafting.
        player.giveItemStack(new ItemStack(Items.OAK_LOG, 2));
        progression.resetForInfiniteRun(player.getUuid());
    }

    // ─── Boss clear → rest room ───────────────────────────────────────────────

    /**
     * A biome's randomized boss just died. Banks the score, rolls the next
     * biome, rebuilds the rest room (fresh coal and all) and moves the party
     * there. Combat itself is already torn down by the caller (CombatManager).
     */
    public static void onBossDefeated(ServerWorld world, UUID hostUuid,
                                      List<ServerPlayerEntity> participants) {
        CrafticsSavedData data = CrafticsSavedData.get(world);
        CrafticsSavedData.PlayerData host = data.getPlayerData(hostUuid);
        host.infiniteBiomesCleared++;
        int score = host.infiniteBiomesCleared;

        for (ServerPlayerEntity member : participants) {
            CrafticsSavedData.PlayerData pd = data.getPlayerData(member.getUuid());
            pd.lastKnownName = member.getName().getString();
            if (score > pd.highestInfiniteScore) {
                pd.highestInfiniteScore = score;
                member.sendMessage(Text.literal("§6§l★ NEW PERSONAL BEST: " + score
                    + (score == 1 ? " biome!" : " biomes!")), false);
            }
        }

        // Completely random next realm (just never the one that was cleared).
        String next = rollNextBiome(host.activeBiomeId, Math.max(0, host.branchChoice));
        host.startBiomeRun(next);

        // Every ESCALATION_INTERVAL clears the bosses grow crueler - call it out.
        if (score % ESCALATION_INTERVAL == 0) {
            for (ServerPlayerEntity member : participants) {
                member.sendMessage(Text.literal(
                    "§4§l⚠ The bosses ahead learned a new move... and strike twice as often."), false);
            }
        }

        // Resolve the rest-room owner from the CURRENT island first. In party
        // infinite runs the run host and island owner can differ.
        UUID islandOwner = IslandDimensions.ownerOf(world);
        if (islandOwner == null) {
            islandOwner = data.getEffectiveWorldOwner(hostUuid);
        }
        BlockPos origin = data.getRestRoomOrigin(islandOwner);
        if (origin == null) {
            // Last-chance fallback to keep the run flowing if ownership records
            // are temporarily out of sync.
            origin = data.getRestRoomOrigin(hostUuid);
        }
        if (origin != null) {
            RestRoomBuilder.build(world, origin, score, host.highestInfiniteScore);
            BlockPos spawn = RestRoomBuilder.spawnPos(origin);
            for (ServerPlayerEntity member : participants) {
                member.requestTeleport(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5);
                member.sendMessage(Text.literal("§5§l∞ Score: " + score
                    + " §r§7- rest, craft, smelt, smith."), false);
                member.sendMessage(Text.literal(
                    "§7Ring the §ebell§7 to brave the next realm, or §e/home§7 to bank your run."), false);
            }
        } else {
            CrafticsMod.LOGGER.warn("Infinite onBossDefeated: no rest room origin found (host={}, islandOwner={})",
                hostUuid, islandOwner);
        }
        data.markDirty();
    }

    // ─── The bell ──────────────────────────────────────────────────────────────

    private static ActionResult onBellRung(ServerPlayerEntity ringer, ServerWorld world,
                                           CrafticsSavedData data, UUID islandOwner) {
        String hostRef = data.getPlayerData(islandOwner).infiniteHostRef;
        if (hostRef == null || hostRef.isEmpty()) return ActionResult.PASS;
        UUID hostUuid;
        try {
            hostUuid = UUID.fromString(hostRef);
        } catch (IllegalArgumentException e) {
            return ActionResult.PASS;
        }
        CrafticsSavedData.PlayerData host = data.getPlayerData(hostUuid);
        if (!host.infiniteActive) return ActionResult.PASS;

        world.playSound(null, ringer.getBlockPos(), SoundEvents.BLOCK_BELL_USE,
            SoundCategory.BLOCKS, 2.0f, 1.0f);

        ServerPlayerEntity hostPlayer = world.getServer().getPlayerManager().getPlayer(hostUuid);
        if (hostPlayer == null) {
            ringer.sendMessage(Text.literal("§cThe run's host is offline - the bell rings hollow."), false);
            return ActionResult.SUCCESS;
        }
        if (!ringer.getUuid().equals(hostUuid)) {
            ringer.sendMessage(Text.literal("§7Only §e" + hostPlayer.getName().getString()
                + "§7 (the run's host) can ring the party onward."), false);
            return ActionResult.SUCCESS;
        }
        // NOTE: getActiveCombat() never returns null (its fallback creates an
        // inactive instance), so a null-check here would silently eat every ring.
        if (CombatManager.isEngaged(hostUuid)) {
            return ActionResult.SUCCESS; // already mid-fight; ignore the double-ring
        }

        // Gather the participants who are still in this island dim and free to fight.
        List<UUID> gathered = new ArrayList<>();
        gathered.add(hostUuid);
        for (UUID uuid : parseUuids(host.infiniteParticipants)) {
            if (uuid.equals(hostUuid)) continue;
            ServerPlayerEntity member = world.getServer().getPlayerManager().getPlayer(uuid);
            if (member != null && member.getEntityWorld() == world
                    && !CombatManager.isEngaged(uuid)) {
                gathered.add(uuid);
            }
        }
        host.infiniteParticipants = joinUuids(gathered);
        data.markDirty();

        RunInviteManager.beginRun(hostPlayer, host.activeBiomeId, gathered);
        return ActionResult.SUCCESS;
    }

    // ─── Run end ───────────────────────────────────────────────────────────────

    /**
     * End {@code hostUuid}'s infinite run: run items evaporate, every
     * participant's stashed inventory + progression come back, scores stay
     * banked. Offline participants are restored by {@link #onPlayerJoin} the
     * next time they log in. Safe to call twice.
     */
    public static void endRun(MinecraftServer server, UUID hostUuid, String reason) {
        ServerWorld overworld = server.getOverworld();
        CrafticsSavedData data = CrafticsSavedData.get(overworld);
        PlayerProgression progression = PlayerProgression.get(overworld);
        CrafticsSavedData.PlayerData host = data.getPlayerData(hostUuid);
        if (!host.infiniteActive) return;

        int finalScore = host.infiniteBiomesCleared;
        List<UUID> participants = parseUuids(host.infiniteParticipants);
        if (!participants.contains(hostUuid)) participants.add(hostUuid);

        host.infiniteActive = false;
        host.infiniteBiomesCleared = 0;
        host.infiniteParticipants = "";
        host.endBiomeRun();

        // Clear the island-side ref on WHICHEVER record it was stamped on at startRun.
        // getEffectiveWorldOwner resolves through the party leader, so re-resolving it
        // here returns a different record after any mid-run leadership change - the
        // stale ref then refuses every future run on the island with no recovery.
        for (CrafticsSavedData.PlayerData pd : data.getAllPlayerData().values()) {
            if (hostUuid.toString().equals(pd.infiniteHostRef)) {
                pd.infiniteHostRef = "";
            }
        }

        for (UUID uuid : participants) {
            ServerPlayerEntity member = server.getPlayerManager().getPlayer(uuid);
            if (member != null) {
                CrafticsSavedData.PlayerData pd = data.getPlayerData(uuid);
                member.sendMessage(Text.literal("§5§l∞ RUN OVER §r§7(" + reason + ")§5§l ∞"), false);
                member.sendMessage(Text.literal("§6Final score: §l" + finalScore
                    + "§r§6 biome" + (finalScore == 1 ? "" : "s")
                    + " §7| Best: §6" + Math.max(pd.highestInfiniteScore, finalScore)), false);
                restoreParticipant(member, data, progression);
            }
            // Offline members keep their stash flags; onPlayerJoin restores them.
        }
        data.markDirty();
        CrafticsMod.LOGGER.info("Infinite run ended ({}) host={} score={}", reason, hostUuid, finalScore);
    }

    /** Bring back one participant's stashed inventory + progression. */
    private static void restoreParticipant(ServerPlayerEntity player, CrafticsSavedData data,
                                           PlayerProgression progression) {
        CrafticsSavedData.PlayerData pd = data.getPlayerData(player.getUuid());
        pd.infiniteRunHost = "";
        if (!pd.infiniteStashActive) return;

        var inventory = player.getInventory();
        inventory.clear(); // run loot stays behind - only emeralds persist
        inventory.readNbt(pd.infiniteStashInventory);
        //? if <=1.21.4 {
        inventory.selectedSlot = Math.max(0, Math.min(pd.infiniteStashSelectedSlot, 8));
        //?} else
        /*inventory.setSelectedSlot(Math.max(0, Math.min(pd.infiniteStashSelectedSlot, 8)));*/
        inventory.markDirty();
        com.crackedgames.craftics.item.MoveSlotManager.enforce(player);

        progression.restoreSnapshot(player.getUuid(), pd.infiniteStashStats);

        pd.infiniteStashActive = false;
        pd.infiniteStashInventory = new NbtList();
        pd.infiniteStashSelectedSlot = 0;
        pd.infiniteStashStats = "";
        data.markDirty();

        syncStats(player, progression, pd);
        player.sendMessage(Text.literal(
            "§aYour stashed items and levels have returned. §7(Run emeralds kept.)"), false);
    }

    /**
     * Join-time recovery. Logging out mid-run forfeits your seat: a returning
     * participant is restored and dropped from the roster, and a returning HOST
     * ends the whole run (nobody else can advance it).
     */
    public static void onPlayerJoin(ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        CrafticsSavedData data = CrafticsSavedData.get(server.getOverworld());
        CrafticsSavedData.PlayerData pd = data.getPlayerData(player.getUuid());
        pd.lastKnownName = player.getName().getString();
        data.markDirty();
        if (!pd.infiniteStashActive) return;

        String hostRef = pd.infiniteRunHost;
        if (player.getUuid().toString().equals(hostRef)) {
            endRun(server, player.getUuid(), "host returned from disconnect");
            return;
        }
        // Participant: leave the (possibly still-running) run and take the stash back.
        if (hostRef != null && !hostRef.isEmpty()) {
            try {
                CrafticsSavedData.PlayerData host = data.getPlayerData(UUID.fromString(hostRef));
                host.infiniteParticipants = removeUuid(host.infiniteParticipants, player.getUuid());
            } catch (IllegalArgumentException ignored) {}
        }
        restoreParticipant(player, data, PlayerProgression.get(server.getOverworld()));
    }

    /**
     * A player used {@code /home} outside combat (e.g. from the rest room).
     * The host banks and ends the run; a non-host member just steps out of it.
     */
    public static void onHomeExit(ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        CrafticsSavedData data = CrafticsSavedData.get(server.getOverworld());
        CrafticsSavedData.PlayerData pd = data.getPlayerData(player.getUuid());
        if (pd.infiniteRunHost == null || pd.infiniteRunHost.isEmpty()) return;

        if (pd.infiniteRunHost.equals(player.getUuid().toString())) {
            endRun(server, player.getUuid(), "returned home");
            return;
        }
        try {
            CrafticsSavedData.PlayerData host = data.getPlayerData(UUID.fromString(pd.infiniteRunHost));
            host.infiniteParticipants = removeUuid(host.infiniteParticipants, player.getUuid());
            data.markDirty();
        } catch (IllegalArgumentException ignored) {}
        player.sendMessage(Text.literal("§7You leave the infinite run."), false);
        restoreParticipant(player, data, PlayerProgression.get(server.getOverworld()));
    }

    // ─── Leaderboard ───────────────────────────────────────────────────────────

    /** Chat leaderboard: every player's highest infinite score, online or not. */
    public static void sendLeaderboard(ServerPlayerEntity viewer) {
        MinecraftServer server = viewer.getServer();
        if (server == null) return;
        CrafticsSavedData data = CrafticsSavedData.get(server.getOverworld());

        List<Object[]> rows = new ArrayList<>(); // [name, score]
        for (var entry : data.getAllPlayerData().entrySet()) {
            int best = entry.getValue().highestInfiniteScore;
            if (best <= 0) continue;
            String name = entry.getValue().lastKnownName;
            if (name == null || name.isEmpty()) {
                name = entry.getKey().toString().substring(0, 8);
            }
            rows.add(new Object[]{name, best});
        }
        rows.sort((a, b) -> Integer.compare((int) b[1], (int) a[1]));

        viewer.sendMessage(Text.literal("§5§l∞ ═══ INFINITE MODE - HALL OF LEGENDS ═══ ∞"), false);
        if (rows.isEmpty()) {
            viewer.sendMessage(Text.literal("§7No runs recorded yet. Be the first!"), false);
            return;
        }
        for (int i = 0; i < Math.min(10, rows.size()); i++) {
            String medal = switch (i) {
                case 0 -> "§6①";
                case 1 -> "§7②";
                case 2 -> "§c③";
                default -> "§8" + (i + 1) + ".";
            };
            viewer.sendMessage(Text.literal(" " + medal + " §f" + rows.get(i)[0]
                + " §7- §5" + rows.get(i)[1] + " biome" + ((int) rows.get(i)[1] == 1 ? "" : "s")), false);
        }
        int own = data.getPlayerData(viewer.getUuid()).highestInfiniteScore;
        viewer.sendMessage(Text.literal("§7Your best: §5" + own), false);
    }

    // ─── Boss names ────────────────────────────────────────────────────────────

    private static List<String> nameFirst = null;
    private static List<String> nameSecond = null;

    /** "The <first> <second>", words pulled from data/craftics/infinite/boss_words.json. */
    public static String generateBossName(Random rng) {
        loadBossWords();
        String first = nameFirst.get(rng.nextInt(nameFirst.size()));
        String second = nameSecond.get(rng.nextInt(nameSecond.size()));
        return "The " + first + " " + second;
    }

    private static synchronized void loadBossWords() {
        if (nameFirst != null) return;
        List<String> first = new ArrayList<>();
        List<String> second = new ArrayList<>();
        try (InputStream in = InfiniteRunManager.class.getClassLoader()
                .getResourceAsStream("data/craftics/infinite/boss_words.json")) {
            if (in != null) {
                var json = com.google.gson.JsonParser
                    .parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                    .getAsJsonObject();
                for (var el : json.getAsJsonArray("first")) first.add(el.getAsString());
                for (var el : json.getAsJsonArray("second")) second.add(el.getAsString());
            }
        } catch (Exception e) {
            CrafticsMod.LOGGER.warn("Failed to load infinite boss word list; using fallback", e);
        }
        if (first.isEmpty()) first = List.of("Nameless", "Endless", "Hollow", "Withered");
        if (second.isEmpty()) second = List.of("Wanderer", "Tyrant", "Reaper", "Warden");
        nameFirst = first;
        nameSecond = second;
    }

    // ─── Helpers ───────────────────────────────────────────────────────────────

    /** A random campaign biome different from the one just cleared. */
    private static String rollNextBiome(String currentBiomeId, int branchChoice) {
        List<String> ids = new ArrayList<>(
            com.crackedgames.craftics.level.campaign.CampaignManager.orderedBiomeIds(branchChoice));
        ids.remove(currentBiomeId);
        if (ids.isEmpty()) return currentBiomeId == null || currentBiomeId.isEmpty()
            ? STARTING_BIOME : currentBiomeId;
        return ids.get(RNG.nextInt(ids.size()));
    }

    /** Push the run-scoped level/stats to the client HUD. */
    private static void syncStats(ServerPlayerEntity player, PlayerProgression progression,
                                  CrafticsSavedData.PlayerData pd) {
        PlayerProgression.PlayerStats stats = progression.getStats(player);
        StringBuilder statData = new StringBuilder();
        for (PlayerProgression.Stat s : PlayerProgression.Stat.values()) {
            if (statData.length() > 0) statData.append(":");
            statData.append(stats.getPoints(s));
        }
        StringBuilder affData = new StringBuilder();
        for (PlayerProgression.Affinity a : PlayerProgression.Affinity.values()) {
            if (affData.length() > 0) affData.append(":");
            affData.append(stats.getAffinityPoints(a));
        }
        ServerPlayNetworking.send(player, new PlayerStatsSyncPayload(
            stats.level, stats.unspentPoints, statData.toString(), pd.emeralds, affData.toString()));
    }

    private static String joinUuids(List<UUID> uuids) {
        StringBuilder sb = new StringBuilder();
        for (UUID uuid : uuids) {
            if (sb.length() > 0) sb.append(',');
            sb.append(uuid);
        }
        return sb.toString();
    }

    private static List<UUID> parseUuids(String csv) {
        List<UUID> out = new ArrayList<>();
        if (csv == null || csv.isEmpty()) return out;
        for (String part : csv.split(",")) {
            try {
                out.add(UUID.fromString(part.trim()));
            } catch (IllegalArgumentException ignored) {}
        }
        return out;
    }

    private static String removeUuid(String csv, UUID uuid) {
        List<UUID> uuids = parseUuids(csv);
        uuids.remove(uuid);
        return joinUuids(uuids);
    }
}
