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
 *   <li><b>Save points</b>: Go Home, {@code /home}, or logging out PARKS the
 *       host's run - run inventory and progression are stowed, the stash comes
 *       back, and the score/biome/level cursor stay saved. Opening Infinite
 *       Mode again resumes it; {@code /craftics infinite stop} abandons it.</li>
 *   <li><b>Exit</b>: a party wipe (or hardcore defeat) ends the run for real.
 *       Run items vanish, the stash comes back, the best score is banked on
 *       the global board.</li>
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

    /**
     * Participants who have been OFFERED the run-start class selection and haven't answered
     * yet. Transient on purpose: the pick is only valid while the offer is outstanding, so a
     * stray or replayed {@code InfiniteClassPickPayload} can never grant a second class. A
     * server restart mid-offer simply drops the offer - the player continues classless,
     * exactly as if they'd skipped.
     */
    private static final java.util.Set<UUID> pendingClassOffers =
        java.util.Collections.synchronizedSet(new java.util.HashSet<>());

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

    /** True when {@code uuid} has an infinite run parked at a save point. */
    public static boolean hasParkedRun(CrafticsSavedData data, UUID uuid) {
        if (uuid == null) return false;
        CrafticsSavedData.PlayerData pd = data.getPlayerData(uuid);
        return pd.infiniteActive && pd.infiniteSuspended;
    }

    /**
     * Resolve the active infinite-run host from the perspective of one participant,
     * regardless of who the party leader / island owner is. The run's "who is host"
     * state lives on THREE records (infiniteActive on the host, infiniteHostRef on the
     * island owner, infiniteRunHost on each participant); the victory/rest-room flow
     * used to assume host == party leader, which is only true solo. This checks, in
     * order: the candidate itself, the candidate's infiniteRunHost pointer, then the
     * participant's own record + pointer. Returns the host UUID whose record has
     * infiniteActive == true, or null if none of them point at an active run.
     */
    public static UUID resolveActiveHost(CrafticsSavedData data, UUID candidate, UUID participant) {
        if (candidate != null && data.getPlayerData(candidate).infiniteActive) return candidate;
        UUID viaCandidate = parseHostRef(data.getPlayerData(candidate).infiniteRunHost, data);
        if (viaCandidate != null) return viaCandidate;
        if (participant != null) {
            if (data.getPlayerData(participant).infiniteActive) return participant;
            UUID viaParticipant = parseHostRef(data.getPlayerData(participant).infiniteRunHost, data);
            if (viaParticipant != null) return viaParticipant;
        }
        return null;
    }

    // ─── Scoring ─────────────────────────────────────────────────────────────
    /** Base points for clearing a normal level. */
    public static final int LEVEL_POINTS = 5;
    /** Base points for beating a boss. */
    public static final int BOSS_POINTS = 10;

    /**
     * Points earned for a clear: {@code base} minus 1 per 2 player-turns taken,
     * floored at 1. Rewards finishing fast. Pure - unit-testable.
     *
     * @param base       LEVEL_POINTS or BOSS_POINTS
     * @param playerTurns turns the player took in that single level/fight
     */
    public static int clearPoints(int base, int playerTurns) {
        return Math.max(1, base - Math.max(0, playerTurns) / 2);
    }

    /**
     * Add {@code points} to the run host's live score, refresh the participants'
     * personal-best, and return the new running total. Host is resolved by the
     * caller; participants are the run party for best-score updates.
     */
    public static int awardScore(CrafticsSavedData data, UUID hostUuid, int points,
                                 List<ServerPlayerEntity> participants) {
        CrafticsSavedData.PlayerData host = data.getPlayerData(hostUuid);
        host.infiniteScore += points;
        int total = host.infiniteScore;
        for (ServerPlayerEntity member : participants) {
            CrafticsSavedData.PlayerData pd = data.getPlayerData(member.getUuid());
            if (total > pd.highestInfiniteScore) pd.highestInfiniteScore = total;
        }
        data.markDirty();
        return total;
    }

    /** Parse an infiniteRunHost string and return it only if it names an ACTIVE host. */
    private static UUID parseHostRef(String hostRef, CrafticsSavedData data) {
        if (hostRef == null || hostRef.isEmpty()) return null;
        try {
            UUID parsed = UUID.fromString(hostRef);
            return data.getPlayerData(parsed).infiniteActive ? parsed : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
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
        // A suspended run is not being played: a host with a parked run who starts a
        // NORMAL biome run must not have infinite scaling bleed into it.
        if (!host.infiniteActive || host.infiniteSuspended) return null;
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
        host.infiniteScore = 0;
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
            offerClassSelection(member);
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

    // ─── Class selection ───────────────────────────────────────────────────────

    /** Send the class-selection offer and arm the one-shot pick guard. */
    private static void offerClassSelection(ServerPlayerEntity member) {
        pendingClassOffers.add(member.getUuid());
        ServerPlayNetworking.send(member,
            new com.crackedgames.craftics.network.InfiniteClassOfferPayload());
    }

    /**
     * The starter weapon each class begins with. Deliberately modest - the run starts from
     * NOTHING, so a stone tool is a real leg up without skipping the early scramble.
     */
    private static ItemStack classWeaponFor(PlayerProgression.Affinity affinity) {
        return switch (affinity) {
            case SLASHING -> new ItemStack(Items.STONE_SWORD);
            case CLEAVING -> new ItemStack(Items.STONE_AXE);
            case BLUNT -> new ItemStack(Items.STICK);
            case RANGED -> new ItemStack(Items.BOW);
            case WATER -> new ItemStack(Items.HORN_CORAL);
            case SPECIAL -> new ItemStack(Items.STONE_HOE);
            case PET -> new ItemStack(Items.STONE_SHOVEL);
            case PHYSICAL -> new ItemStack(Items.SHIELD);
        };
    }

    /**
     * Resolve a player's class pick from the run-start screen: +1 point in the chosen
     * affinity and its starter weapon (Ranged also gets a handful of arrows). Ordinal
     * {@code -1} is an explicit skip. Ignored entirely unless this player has an
     * outstanding offer, so the packet can neither be replayed nor forged mid-run.
     */
    public static void applyClassPick(ServerPlayerEntity player, int affinityOrdinal) {
        if (!pendingClassOffers.remove(player.getUuid())) return;

        if (affinityOrdinal < 0 || affinityOrdinal >= PlayerProgression.Affinity.values().length) {
            player.sendMessage(Text.literal(
                "§7No class - just you, your wits, and two logs. Good luck."), false);
            return;
        }
        PlayerProgression.Affinity affinity = PlayerProgression.Affinity.values()[affinityOrdinal];
        ServerWorld world = (ServerWorld) player.getEntityWorld();
        PlayerProgression progression = PlayerProgression.get(world);
        PlayerProgression.PlayerStats stats = progression.getStats(player);
        stats.setAffinityPoints(affinity, stats.getAffinityPoints(affinity) + 1);
        progression.saveStats(player);

        ItemStack weapon = classWeaponFor(affinity);
        String weaponName = weapon.getName().getString();
        player.giveItemStack(weapon);
        if (affinity == PlayerProgression.Affinity.RANGED) {
            player.giveItemStack(new ItemStack(Items.ARROW, 8));
        }

        CrafticsSavedData data = CrafticsSavedData.get(world);
        syncStats(player, progression, data.getPlayerData(player.getUuid()));
        player.sendMessage(Text.literal("§5§l∞ " + affinity.icon + " §r§d"
            + affinity.displayName + " class!§7 +1 " + affinity.displayName
            + " affinity and a " + weaponName + " to open with."), false);
    }

    // ─── Boss clear → rest room ───────────────────────────────────────────────

    /**
     * A biome's randomized boss just died. Banks the score, rolls the next
     * biome, rebuilds the rest room (fresh coal and all) and moves the party
     * there. Combat itself is already torn down by the caller (CombatManager).
     */
    public static void onBossDefeated(ServerWorld world, UUID hostUuid,
                                      List<ServerPlayerEntity> participants, int playerTurns) {
        CrafticsSavedData data = CrafticsSavedData.get(world);
        CrafticsSavedData.PlayerData host = data.getPlayerData(hostUuid);
        // Difficulty/progress counter (drives the boss ramp + "BIOME N" line).
        host.infiniteBiomesCleared++;
        int cleared = host.infiniteBiomesCleared;

        // POINT score: +10 for the boss, minus 1 per 2 player-turns, floored at 1.
        // Capture each member's prior best BEFORE awardScore bumps it, so the
        // "new personal best" line only fires on a genuine beat.
        java.util.Map<UUID, Integer> priorBest = new java.util.HashMap<>();
        for (ServerPlayerEntity member : participants) {
            priorBest.put(member.getUuid(), data.getPlayerData(member.getUuid()).highestInfiniteScore);
        }
        int earned = clearPoints(BOSS_POINTS, playerTurns);
        int total = awardScore(data, hostUuid, earned, participants);
        for (ServerPlayerEntity member : participants) {
            data.getPlayerData(member.getUuid()).lastKnownName = member.getName().getString();
            member.sendMessage(Text.literal("§5§l∞ Boss down! §r§d+" + earned
                + " points §7(fewer turns = more). Score: §f" + total), false);
            if (total > priorBest.getOrDefault(member.getUuid(), 0) && total > 0) {
                member.sendMessage(Text.literal("§6§l★ NEW PERSONAL BEST: " + total
                    + " points!"), false);
            }
        }

        // Completely random next realm (just never the one that was cleared).
        String next = rollNextBiome(host.activeBiomeId, Math.max(0, host.branchChoice));
        host.startBiomeRun(next);

        // Every ESCALATION_INTERVAL clears the bosses grow crueler - call it out.
        if (cleared % ESCALATION_INTERVAL == 0) {
            for (ServerPlayerEntity member : participants) {
                member.sendMessage(Text.literal(
                    "§4§l⚠ The bosses ahead learned a new move... and strike twice as often."), false);
            }
        }

        // Skip the rest room: go straight into the next biome's first level. The
        // rest room was a between-biomes breather (craft/smelt/bell-to-continue), but
        // its build/teleport was failing, so we now transition directly. host.activeBiomeId
        // is already set to `next` by startBiomeRun above; beginRun builds and drops
        // the party into that biome's level 1, the same path the bell used to trigger.
        for (ServerPlayerEntity member : participants) {
            member.sendMessage(Text.literal("§5§l∞ Score: " + total
                + " §r§7- onward to the next realm!"), false);
        }
        data.markDirty();

        ServerPlayerEntity hostPlayer = world.getServer().getPlayerManager().getPlayer(hostUuid);
        if (hostPlayer == null) {
            // Host offline (shouldn't happen right after their boss kill): nothing to
            // do - the run stays active and resumes when they rejoin.
            CrafticsMod.LOGGER.warn("Infinite onBossDefeated: host {} offline, cannot start next biome", hostUuid);
            return;
        }
        List<UUID> nextParty = new ArrayList<>();
        for (ServerPlayerEntity member : participants) nextParty.add(member.getUuid());
        RunInviteManager.beginRun(hostPlayer, next, nextParty);
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

    // ─── Save points (suspend / resume) ─────────────────────────────────────────

    /**
     * Park {@code hostUuid}'s run at a save point instead of ending it. The score, the
     * cleared count, and the biome/level cursor all stay on the host's record; the host's
     * run inventory and run progression are parked, and their pre-run stash comes back.
     * Opening Infinite Mode again resumes exactly here; {@code /craftics infinite stop}
     * abandons it.
     *
     * <p>Other participants' seats are forfeited (they are restored and can be re-invited
     * at resume), matching how a participant's own disconnect already works. The island's
     * run lock is released so a parked run never blocks the island. Safe to call twice,
     * and safe to call for an offline host - the parking half then happens on their next
     * join, via {@link #onPlayerJoin}.
     */
    public static void suspendRun(MinecraftServer server, UUID hostUuid, String reason) {
        ServerWorld overworld = server.getOverworld();
        CrafticsSavedData data = CrafticsSavedData.get(overworld);
        PlayerProgression progression = PlayerProgression.get(overworld);
        CrafticsSavedData.PlayerData host = data.getPlayerData(hostUuid);
        if (!host.infiniteActive) return;
        if (!host.infiniteSuspended) {
            // Move the run's biome/level cursor off the SHARED fields and onto its own
            // parked pair, so a normal biome run can use activeBiomeId/LevelIndex while
            // this run waits. Resume moves it back.
            host.infiniteParkedBiomeId =
                host.activeBiomeId == null ? STARTING_BIOME : host.activeBiomeId;
            host.infiniteParkedLevelIndex = host.activeBiomeLevelIndex;
            host.endBiomeRun();
        }
        host.infiniteSuspended = true;

        // Release the island lock: a parked run must never wedge the island. Resume
        // re-stamps it. (Same all-records sweep as endRun, for the same drift reason.)
        for (CrafticsSavedData.PlayerData pd : data.getAllPlayerData().values()) {
            if (hostUuid.toString().equals(pd.infiniteHostRef)) {
                pd.infiniteHostRef = "";
            }
        }

        // Everyone but the host steps out (their run items evaporate, stash returns).
        // A member still mid-fight is left alone - their own exit path restores them.
        for (UUID uuid : parseUuids(host.infiniteParticipants)) {
            if (uuid.equals(hostUuid)) continue;
            ServerPlayerEntity member = server.getPlayerManager().getPlayer(uuid);
            if (member != null && !CombatManager.isEngaged(uuid)) {
                member.sendMessage(Text.literal(
                    "§5§l∞ §7The host parked the run - your stashed items return."), false);
                restoreParticipant(member, data, progression);
            }
        }
        host.infiniteParticipants = "";

        ServerPlayerEntity hostPlayer = server.getPlayerManager().getPlayer(hostUuid);
        if (hostPlayer != null) {
            parkAndRestore(hostPlayer, host, data, progression);
        }
        data.markDirty();
        CrafticsMod.LOGGER.info("Infinite run suspended ({}) host={} score={} biome={} level={}",
            reason, hostUuid, host.infiniteScore, host.infiniteParkedBiomeId,
            host.infiniteParkedLevelIndex);
    }

    /**
     * The parking half of a suspend: snapshot the host's run inventory + run progression
     * into the parked fields, then hand their pre-run stash back. No-op when the stash was
     * already restored (double suspend, or an orphaned state a previous version left).
     */
    private static void parkAndRestore(ServerPlayerEntity player, CrafticsSavedData.PlayerData pd,
                                       CrafticsSavedData data, PlayerProgression progression) {
        if (!pd.infiniteStashActive) return;
        pd.infiniteParkedInventory = player.getInventory().writeNbt(new NbtList());
        //? if <=1.21.4 {
        pd.infiniteParkedSelectedSlot = player.getInventory().selectedSlot;
        //?} else
        /*pd.infiniteParkedSelectedSlot = player.getInventory().getSelectedSlot();*/
        pd.infiniteParkedStats = progression.snapshotSerialized(player.getUuid());
        restoreParticipant(player, data, progression);
        // restoreParticipant cleared the host pointer, but this run still exists.
        pd.infiniteRunHost = player.getUuid().toString();
        player.sendMessage(Text.literal("§5§l∞ RUN SAVED §r§7- score §f" + pd.infiniteScore
            + "§7, parked at §f" + pd.infiniteParkedBiomeId + "§7 level §f"
            + (pd.infiniteParkedLevelIndex + 1) + "§7."), false);
        player.sendMessage(Text.literal(
            "§7Open §dInfinite Mode§7 to resume, or §e/craftics infinite stop§7 to abandon it."), false);
    }

    /**
     * Resume {@code starter}'s parked run: the run inventory and run progression come
     * back, the CURRENT overworld inventory + stats are freshly stashed (whatever changed
     * while the run was parked is what must return when it ends), and the party lands in
     * the saved biome at the saved level. Returns the biome id to begin in, or
     * {@code null} when the starter has no parked run - the caller then starts fresh.
     *
     * <p>Participants other than the host start the resumed run with the usual clean
     * slate; only the host carries parked items.
     */
    public static String resumeRun(ServerPlayerEntity starter, List<UUID> participants) {
        ServerWorld world = (ServerWorld) starter.getEntityWorld();
        MinecraftServer server = world.getServer();
        CrafticsSavedData data = CrafticsSavedData.get(world);
        PlayerProgression progression = PlayerProgression.get(world);
        UUID hostUuid = starter.getUuid();
        CrafticsSavedData.PlayerData host = data.getPlayerData(hostUuid);
        if (!host.infiniteActive || !host.infiniteSuspended) return null;
        // The shared cursor may be carrying a normal biome run right now; resuming would
        // overwrite it. The caller gates on this too - this is the belt to its suspenders.
        if (host.isInBiomeRun()) {
            starter.sendMessage(Text.literal(
                "§cFinish your current biome run (or Go Home from it) before resuming the infinite run."), false);
            return null;
        }

        host.infiniteSuspended = false;
        host.infiniteParticipants = joinUuids(participants);
        // Move the parked cursor back onto the shared fields the live run plays on.
        host.startBiomeRun(host.infiniteParkedBiomeId == null || host.infiniteParkedBiomeId.isEmpty()
            ? STARTING_BIOME : host.infiniteParkedBiomeId);
        host.activeBiomeLevelIndex = Math.max(0, host.infiniteParkedLevelIndex);
        host.infiniteParkedBiomeId = "";
        host.infiniteParkedLevelIndex = 0;
        UUID islandOwner = data.getEffectiveWorldOwner(hostUuid);
        data.getPlayerData(islandOwner).infiniteHostRef = hostUuid.toString();

        for (UUID uuid : participants) {
            ServerPlayerEntity member = server.getPlayerManager().getPlayer(uuid);
            if (member == null) continue;
            CrafticsSavedData.PlayerData pd = data.getPlayerData(uuid);
            pd.infiniteRunHost = hostUuid.toString();
            pd.lastKnownName = member.getName().getString();
            if (uuid.equals(hostUuid)) {
                unparkHost(member, pd, progression);
            } else {
                stashAndReset(member, pd, progression);
                // A fresh clean slate deserves the class pick; the host resumed with their
                // run profile intact, so their earlier choice (or skip) stands.
                offerClassSelection(member);
            }
            syncStats(member, progression, pd);
            member.sendMessage(Text.literal("§5§l∞ RUN RESUMED ∞"), false);
            member.sendMessage(Text.literal("§7Score §f" + host.infiniteScore
                + "§7, " + host.infiniteBiomesCleared + " biome"
                + (host.infiniteBiomesCleared == 1 ? "" : "s") + " cleared - back into §f"
                + host.activeBiomeId + "§7 at level §f" + (host.activeBiomeLevelIndex + 1) + "§7."), false);
        }
        data.markDirty();
        CrafticsMod.LOGGER.info("Infinite run resumed by {} at {} level {} (score {})",
            starter.getName().getString(), host.activeBiomeId,
            host.activeBiomeLevelIndex, host.infiniteScore);
        return host.activeBiomeId;
    }

    /**
     * The un-parking half of a resume. The stash is re-snapshotted from the player's
     * CURRENT state, not reused: anything they gained or lost while the run was parked
     * belongs to their real profile and must be what comes back when the run ends.
     */
    private static void unparkHost(ServerPlayerEntity player, CrafticsSavedData.PlayerData pd,
                                   PlayerProgression progression) {
        pd.infiniteStashInventory = player.getInventory().writeNbt(new NbtList());
        //? if <=1.21.4 {
        pd.infiniteStashSelectedSlot = player.getInventory().selectedSlot;
        //?} else
        /*pd.infiniteStashSelectedSlot = player.getInventory().getSelectedSlot();*/
        pd.infiniteStashStats = progression.snapshotSerialized(player.getUuid());
        pd.infiniteStashActive = true;

        var inventory = player.getInventory();
        inventory.clear();
        inventory.readNbt(pd.infiniteParkedInventory);
        //? if <=1.21.4 {
        inventory.selectedSlot = Math.max(0, Math.min(pd.infiniteParkedSelectedSlot, 8));
        //?} else
        /*inventory.setSelectedSlot(Math.max(0, Math.min(pd.infiniteParkedSelectedSlot, 8)));*/
        inventory.markDirty();
        com.crackedgames.craftics.item.MoveSlotManager.enforce(player);
        progression.restoreSnapshot(player.getUuid(), pd.infiniteParkedStats);

        pd.infiniteParkedInventory = new NbtList();
        pd.infiniteParkedSelectedSlot = 0;
        pd.infiniteParkedStats = "";
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

        int finalScore = host.infiniteScore;
        int biomesCleared = host.infiniteBiomesCleared;
        List<UUID> participants = parseUuids(host.infiniteParticipants);
        if (!participants.contains(hostUuid)) participants.add(hostUuid);

        host.infiniteActive = false;
        host.infiniteBiomesCleared = 0;
        host.infiniteScore = 0;
        host.infiniteParticipants = "";
        // A LIVE run owns the shared cursor; a suspended one parked it, and the shared
        // fields may already be carrying a normal biome run that must survive this.
        if (!host.infiniteSuspended) host.endBiomeRun();
        // Any parked save point dies with the run - its items were run loot.
        host.infiniteSuspended = false;
        host.infiniteParkedInventory = new NbtList();
        host.infiniteParkedSelectedSlot = 0;
        host.infiniteParkedStats = "";
        host.infiniteParkedBiomeId = "";
        host.infiniteParkedLevelIndex = 0;

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
                    + "§r§6 points §7(" + biomesCleared + " biome" + (biomesCleared == 1 ? "" : "s")
                    + " cleared) §7| Best: §6" + Math.max(pd.highestInfiniteScore, finalScore)), false);
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
     * Join-time recovery. A returning HOST's run parks at a save point - their run
     * inventory is stowed, their stash comes back, and Infinite Mode resumes it. A
     * returning participant forfeits their seat and is restored (only the host can
     * carry a run).
     *
     * <p>The stash restore here is UNCONDITIONAL once a stash exists and no live run
     * claims it. The old flow routed the host through {@code endRun}, whose
     * {@code !infiniteActive} guard could be defeated by any exit path that ended the
     * run while the host was offline - leaving the player permanently holding the run
     * inventory with their real one stranded in the stash. That state (and old saves
     * already in it) now heals on the next join.
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
        if (player.getUuid().toString().equals(hostRef) && pd.infiniteActive) {
            // Logging out mid-run is a save point, not a forfeit.
            suspendRun(server, player.getUuid(), "logged out mid-run");
            return;
        }
        // Either a participant returning to someone else's run (seat forfeited), or an
        // orphaned stash whose run already ended behind this player's back. Both cases:
        // take the stash back, no conditions attached.
        if (hostRef != null && !hostRef.isEmpty() && !player.getUuid().toString().equals(hostRef)) {
            try {
                CrafticsSavedData.PlayerData host = data.getPlayerData(UUID.fromString(hostRef));
                host.infiniteParticipants = removeUuid(host.infiniteParticipants, player.getUuid());
            } catch (IllegalArgumentException ignored) {}
        }
        restoreParticipant(player, data, PlayerProgression.get(server.getOverworld()));
    }

    /**
     * A player used {@code /home} outside combat (e.g. from the rest room). The host's
     * run parks at a save point; a non-host member just steps out of it.
     */
    public static void onHomeExit(ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        CrafticsSavedData data = CrafticsSavedData.get(server.getOverworld());
        CrafticsSavedData.PlayerData pd = data.getPlayerData(player.getUuid());
        if (pd.infiniteRunHost == null || pd.infiniteRunHost.isEmpty()) return;

        if (pd.infiniteRunHost.equals(player.getUuid().toString())) {
            suspendRun(server, player.getUuid(), "returned home");
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

    /**
     * Explicitly throw the run away ({@code /craftics infinite stop}). The host's run -
     * parked or live - ends for good: run items evaporate, stashes come back, the score
     * stays banked. A non-host member just steps out, same as {@code /home}.
     */
    public static void abandonRun(ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        CrafticsSavedData data = CrafticsSavedData.get(server.getOverworld());
        CrafticsSavedData.PlayerData pd = data.getPlayerData(player.getUuid());
        if (pd.infiniteRunHost == null || pd.infiniteRunHost.isEmpty()) return;

        if (pd.infiniteRunHost.equals(player.getUuid().toString())) {
            endRun(server, player.getUuid(), "abandoned");
            // endRun's restore only reaches players whose stash is still active; a host
            // abandoning an already-parked run was restored at suspend time, so the only
            // thing left to clear is the pointer that kept the parked run addressable.
            pd.infiniteRunHost = "";
            data.markDirty();
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
                + " §7- §5" + rows.get(i)[1] + " pts"), false);
        }
        int own = data.getPlayerData(viewer.getUuid()).highestInfiniteScore;
        viewer.sendMessage(Text.literal("§7Your best: §5" + own + " pts"), false);
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
