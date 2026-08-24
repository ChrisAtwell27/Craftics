package com.crackedgames.craftics.world;

import com.crackedgames.craftics.mixin.DisplayEntityInvoker;
import com.crackedgames.craftics.mixin.TextDisplayInvoker;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The season standings board: one score per player, chapter play plus island progress.
 *
 * <p>Built the same way {@link TopPlayersHologram} is - a tagged vanilla text display entity, no
 * custom entity, no packet, no client code - and refreshed from that class's existing scan so
 * the two boards share one pass over each world's entities rather than doubling it.
 *
 * <p>Reads {@code getAllPlayerData()}, so the board includes players who are offline. That is
 * the whole point of a season board and is why it cannot be the TAB list, which can only ever
 * show who is connected right now.
 *
 * <p>The score itself lives in {@link SeasonScore} and is derived on every refresh rather than
 * stored, so it can never disagree with the values it came from.
 */
public final class SeasonLeaderboard {

    private SeasonLeaderboard() {}

    /** Command tag identifying season boards among the world's display entities. */
    public static final String TAG = "craftics_season_board";

    /** How many players the board lists. */
    private static final int ROWS = 10;

    /**
     * Spawn a season board whose BOTTOM edge sits at {@code pos}, or null when the world
     * refuses the entity. The entity goes half a board higher than that, because the board is
     * centred on its own pivot - see {@link BoardLayout}.
     */
    public static DisplayEntity.TextDisplayEntity spawn(ServerWorld world, Vec3d pos) {
        DisplayEntity.TextDisplayEntity board =
            new DisplayEntity.TextDisplayEntity(EntityType.TEXT_DISPLAY, world);
        Text text = buildBoard(world);
        board.refreshPositionAndAngles(pos.x, pos.y + BoardLayout.halfHeight(text), pos.z, 0f, 0f);
        board.addCommandTag(TAG);
        ((DisplayEntityInvoker) board).craftics$setBillboardMode(DisplayEntity.BillboardMode.CENTER);
        ((TextDisplayInvoker) board).craftics$setLineWidth(260);
        BoardLayout.applyText(board, text);
        return world.spawnEntity(board) ? board : null;
    }

    /** Remove every season board within {@code radius} of {@code pos}; returns how many. */
    public static int removeNear(ServerWorld world, Vec3d pos, double radius) {
        int removed = 0;
        for (DisplayEntity.TextDisplayEntity board : TopPlayersHologram.taggedBoards(world, TAG)) {
            if (board.getPos().squaredDistanceTo(pos) <= radius * radius) {
                board.discard();
                removed++;
            }
        }
        return removed;
    }

    /** One scored player, ready to render. */
    private record Row(String name, SeasonScore.Breakdown score) {}

    /**
     * The board text: the top {@value #ROWS} season scores.
     *
     * <p>Each row shows the split as well as the total, because a single number cannot tell a
     * player whether the person above them got there by grinding chapters or by taking their
     * island deep - and that is the question anyone looking at this board is asking.
     */
    public static Text buildBoard(ServerWorld anyWorld) {
        CrafticsSavedData data = CrafticsSavedData.get(anyWorld);
        SeasonScore.Weights weights = weights();

        List<Row> rows = new ArrayList<>();
        for (Map.Entry<UUID, CrafticsSavedData.PlayerData> entry : data.getAllPlayerData().entrySet()) {
            CrafticsSavedData.PlayerData pd = entry.getValue();
            SeasonScore.Breakdown score = SeasonScore.score(new SeasonScore.Inputs(
                pd.allTimeInfiniteScore,
                pd.chapterPlacementPoints,
                pd.chaptersPlaced,
                pd.highestBiomeUnlocked,
                SeasonScore.countCsv(pd.discoveredBiomes),
                pd.ngPlusLevel,
                pd.raidDefeated), weights);
            // A player who has done nothing is not a standing.
            if (score.total() <= 0) continue;
            // No name, no row. There is no UUID->name lookup anywhere in the mod, and printing
            // a UUID fragment names nobody a reader could recognise. Names are recorded on
            // every join, so this only hides someone who has not logged in since that shipped.
            String name = pd.knownName();
            if (name == null) continue;
            rows.add(new Row(name, score));
        }
        rows.sort((a, b) -> Long.compare(b.score().total(), a.score().total()));

        StringBuilder sb = new StringBuilder();
        sb.append("§b§lSEASON STANDINGS§r\n§7chapters + island\n§8------------------\n");
        if (rows.isEmpty()) {
            sb.append("§7No scores yet.\n§7Play a chapter or grow your island.");
        }
        for (int i = 0; i < Math.min(ROWS, rows.size()); i++) {
            Row row = rows.get(i);
            String rankColor = switch (i) {
                case 0 -> "§6";
                case 1 -> "§7";
                case 2 -> "§c";
                default -> "§8";
            };
            sb.append(rankColor).append(i + 1).append(". §f")
              .append(row.name()).append(" §7- §b").append(row.score().total())
              .append("\n   §8chapters ").append(row.score().chapterScore())
              .append(" §8/ island ").append(row.score().islandScore()).append("\n");
        }
        return Text.literal(sb.toString().stripTrailing());
    }

    /** Score weights, read live from config so a server can retune without a restart. */
    private static SeasonScore.Weights weights() {
        var cfg = com.crackedgames.craftics.CrafticsMod.CONFIG;
        return new SeasonScore.Weights(
            cfg.seasonWeightInfinitePoint(),
            cfg.seasonWeightPlacementPoint(),
            cfg.seasonWeightChapterPlaced(),
            cfg.seasonWeightBiomeDepth(),
            cfg.seasonWeightBiomeDiscovered(),
            cfg.seasonWeightNgPlusLevel(),
            cfg.seasonWeightRaidBonus());
    }
}
