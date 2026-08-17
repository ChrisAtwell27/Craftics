package com.crackedgames.craftics.combat.infinite;

import com.crackedgames.craftics.CrafticsMod;
import com.crackedgames.craftics.world.CrafticsSavedData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Owns the chapter lifecycle: the current seed, when it next rotates, and what rotation
 * does to the two leaderboards.
 *
 * <p>Rotation is checked against wall-clock time on a slow tick rather than scheduled
 * with a timer, because a timer dies with the server process. The next boundary is
 * stored as an absolute instant, so a rotation whose moment passed while the server was
 * down fires once on the next boot instead of being skipped.
 */
public final class ChapterManager {

    private ChapterManager() {}

    /** Rotation check cadence. Once every 5 seconds is far finer than a schedule that
     *  fires at most daily, and costs one long comparison. */
    private static final int TICK_INTERVAL = 100;

    private static int clock = 0;

    /**
     * The current chapter's seed, rolling one on first use.
     *
     * <p>A save that predates chapters holds seed 0. Rolling lazily here rather than at
     * world load means every entry point (level generation, the info command, the board)
     * sees a real seed without any of them needing to care which one ran first.
     */
    public static long seedOf(CrafticsSavedData data) {
        if (data.chapterSeed == 0L) {
            data.chapterSeed = freshSeed();
            if (data.chapterStartedAt == 0L) data.chapterStartedAt = System.currentTimeMillis();
            data.markDirty();
            CrafticsMod.LOGGER.info("[chapter] rolled initial seed {} for chapter {}",
                data.chapterSeed, data.chapterNumber);
        }
        return data.chapterSeed;
    }

    /** Aggregate-tick hook. Rotates when the stored boundary has passed. */
    public static void tick(MinecraftServer server) {
        if (++clock < TICK_INTERVAL) return;
        clock = 0;
        CrafticsSavedData data = CrafticsSavedData.get(server.getOverworld());
        if (data.nextRotationAt <= 0L) return; // manual only
        if (System.currentTimeMillis() < data.nextRotationAt) return;
        rotate(server, "scheduled");
    }

    /** Millis until the next rotation, or {@link Long#MAX_VALUE} when manual only. */
    public static long millisUntilRotation(CrafticsSavedData data) {
        if (data.nextRotationAt <= 0L) return Long.MAX_VALUE;
        return data.nextRotationAt - System.currentTimeMillis();
    }

    /** Player UUIDs with a nonzero chapter score, best first. The chapter board order. */
    public static List<UUID> chapterStandings(CrafticsSavedData data) {
        List<Map.Entry<UUID, CrafticsSavedData.PlayerData>> rows = new ArrayList<>();
        for (var entry : data.getAllPlayerData().entrySet()) {
            if (entry.getValue().highestInfiniteScore > 0) rows.add(entry);
        }
        rows.sort((a, b) -> Integer.compare(
            b.getValue().highestInfiniteScore, a.getValue().highestInfiniteScore));
        List<UUID> out = new ArrayList<>();
        for (var entry : rows) out.add(entry.getKey());
        return out;
    }

    /**
     * End the current chapter and open the next one.
     *
     * <p>Order matters: standings must be banked BEFORE the scores that produced them
     * are zeroed, and the seed must change before anyone can start a run on the new
     * chapter.
     */
    public static void rotate(MinecraftServer server, String reason) {
        CrafticsSavedData data = CrafticsSavedData.get(server.getOverworld());
        int closing = data.chapterNumber;

        // 1. Freeze the standings and bank career points.
        List<UUID> standings = chapterStandings(data);
        List<String> podium = new ArrayList<>();
        for (int i = 0; i < Math.min(ChapterPlacement.BANKED_PLACES, standings.size()); i++) {
            int place = i + 1;
            CrafticsSavedData.PlayerData pd = data.getPlayerData(standings.get(i));
            pd.chapterPlacementPoints += ChapterPlacement.pointsForPlace(place);
            pd.chaptersPlaced++;
            if (pd.bestChapterPlacement == 0 || place < pd.bestChapterPlacement) {
                pd.bestChapterPlacement = place;
            }
            if (place <= 3) {
                String name = pd.lastKnownName == null || pd.lastKnownName.isEmpty()
                    ? standings.get(i).toString().substring(0, 8) : pd.lastKnownName;
                podium.add(ChapterPlacement.ordinal(place) + " " + name
                    + " (" + pd.highestInfiniteScore + ")");
            }
        }

        // 2. Zero the chapter board. allTimeInfiniteScore already holds the lifetime
        //    peak (awardScore keeps it current), so nothing is lost here.
        //
        //    A LIVE run's running total goes with it. awardScore banks the host's
        //    cumulative infiniteScore into highestInfiniteScore, so a run that straddles
        //    a rotation would carry every point it earned under the OLD chapter into the
        //    new board on its next award - points that already bought a placement above.
        //    Zeroing the counter restarts the run's contribution at the boundary, which
        //    is the same rule as "your chapter score is what you scored this chapter".
        //    Parked runs are left alone: step 5 ends them and shows finalScore first.
        for (CrafticsSavedData.PlayerData pd : data.getAllPlayerData().values()) {
            pd.highestInfiniteScore = 0;
            if (pd.infiniteActive && !pd.infiniteSuspended) pd.infiniteScore = 0;
        }

        // 3. New seed, new chapter number.
        data.chapterSeed = freshSeed();
        data.chapterNumber++;
        data.chapterStartedAt = System.currentTimeMillis();

        // 4. Next boundary, computed from the moment we just rotated. ChapterSchedule
        //    is strictly-after, so this cannot resolve to right now and re-fire.
        data.nextRotationAt = ChapterSchedule.nextAfter(
            data.rotationRule, data.rotationZone, data.chapterStartedAt);
        data.markDirty();

        // 5. End parked runs: their ladder no longer exists. Live runs are deliberately
        //    left alone to finish rather than killing a party mid-fight; they bank into
        //    the new chapter.
        int ended = 0;
        for (var entry : new ArrayList<>(data.getAllPlayerData().entrySet())) {
            CrafticsSavedData.PlayerData pd = entry.getValue();
            if (pd.infiniteActive && pd.infiniteSuspended) {
                com.crackedgames.craftics.combat.InfiniteRunManager.endRun(
                    server, entry.getKey(), "chapter ended");
                ended++;
            }
        }

        // 6. Tell everyone.
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            player.sendMessage(Text.literal("§5§l∞ CHAPTER " + closing + " HAS ENDED ∞"), false);
            for (String line : podium) {
                player.sendMessage(Text.literal("§6" + line), false);
            }
            player.sendMessage(Text.literal("§d§lCHAPTER " + data.chapterNumber
                + "§r§7 begins. Infinite mode has a brand new world - and the board is clear."), false);
        }

        CrafticsMod.LOGGER.info(
            "[chapter] rotated ({}) {} -> {} seed={} banked={} parkedRunsEnded={} nextAt={}",
            reason, closing, data.chapterNumber, data.chapterSeed,
            Math.min(ChapterPlacement.BANKED_PLACES, standings.size()), ended, data.nextRotationAt);
    }

    /** Set the recurring rule and recompute the next boundary from now. */
    public static void setSchedule(MinecraftServer server, String rule) {
        CrafticsSavedData data = CrafticsSavedData.get(server.getOverworld());
        data.rotationRule = rule == null ? "" : rule;
        data.nextRotationAt = ChapterSchedule.nextAfter(
            data.rotationRule, data.rotationZone, System.currentTimeMillis());
        data.markDirty();
        CrafticsMod.LOGGER.info("[chapter] schedule set to '{}' next={}",
            data.rotationRule, data.nextRotationAt);
    }

    /**
     * Set the zone the rule's wall-clock times are read in, and re-anchor the boundary.
     *
     * @return false if {@code zoneId} was not recognised, in which case the server's own zone
     *         was used instead. Reported rather than swallowed: an unrecognised zone still
     *         "works", just on the wrong clock, so the only sign of a typo was rotations
     *         happening at an hour nobody asked for.
     */
    public static boolean setZone(MinecraftServer server, String zoneId) {
        CrafticsSavedData data = CrafticsSavedData.get(server.getOverworld());
        boolean known = ChapterSchedule.isKnownZone(zoneId);
        data.rotationZone = ChapterSchedule.resolveZone(zoneId).getId();
        if (!known) {
            CrafticsMod.LOGGER.warn("[chapter] unknown zone '{}'; falling back to the server zone '{}'",
                zoneId, data.rotationZone);
        }
        data.nextRotationAt = ChapterSchedule.nextAfter(
            data.rotationRule, data.rotationZone, System.currentTimeMillis());
        data.markDirty();
        CrafticsMod.LOGGER.info("[chapter] zone set to '{}' next={}",
            data.rotationZone, data.nextRotationAt);
        return known;
    }

    /** A fresh seed. Wall-clock entropy is fine HERE and only here: this is the one
     *  place a chapter is supposed to become unpredictable. */
    private static long freshSeed() {
        long seed = new java.util.Random().nextLong();
        return seed == 0L ? 1L : seed; // 0 is the "unset" sentinel
    }
}
