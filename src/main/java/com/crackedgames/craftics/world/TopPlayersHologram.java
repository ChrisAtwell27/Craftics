package com.crackedgames.craftics.world;

import com.crackedgames.craftics.mixin.DisplayEntityInvoker;
import com.crackedgames.craftics.mixin.TextDisplayInvoker;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The permanent career board: championship points banked from chapter finishes.
 *
 * <p>Sibling to {@link InfiniteScoreboardHologram}, which shows the CURRENT chapter and
 * zeroes every rotation. This one is never reset. Rotation is the only thing that writes
 * to it - see {@code ChapterManager.rotate} - so between rotations it is static, and the
 * refresh pass exists only so a board spawned mid-chapter fills itself in.
 *
 * <p>Same command-tag scheme as its sibling, so it survives restarts as a plain display
 * entity with no bookkeeping of our own.
 */
public final class TopPlayersHologram {

    private TopPlayersHologram() {}

    /** Command tag identifying our career boards among the world's display entities. */
    public static final String TAG = "craftics_top_players_board";

    private static final int REFRESH_TICKS = 100;

    private static int clock = 0;

    /** Spawn a board at {@code pos}. Returns the entity, or null when spawning failed. */
    public static DisplayEntity.TextDisplayEntity spawn(ServerWorld world, Vec3d pos) {
        DisplayEntity.TextDisplayEntity board =
            new DisplayEntity.TextDisplayEntity(EntityType.TEXT_DISPLAY, world);
        board.refreshPositionAndAngles(pos.x, pos.y, pos.z, 0f, 0f);
        board.addCommandTag(TAG);
        ((DisplayEntityInvoker) board).craftics$setBillboardMode(DisplayEntity.BillboardMode.CENTER);
        ((TextDisplayInvoker) board).craftics$setLineWidth(220);
        ((TextDisplayInvoker) board).craftics$setText(buildBoard(world));
        return world.spawnEntity(board) ? board : null;
    }

    /** Remove every tagged board within {@code radius} blocks of {@code pos}. Returns count. */
    public static int removeNear(ServerWorld world, Vec3d pos, double radius) {
        int removed = 0;
        for (DisplayEntity.TextDisplayEntity board : boardsIn(world)) {
            if (board.getPos().squaredDistanceTo(pos) <= radius * radius) {
                board.discard();
                removed++;
            }
        }
        return removed;
    }

    /**
     * Aggregate-tick hook: refresh every Craftics board in every loaded world.
     *
     * <p>Career and season boards are refreshed in ONE pass. Scanning is the expensive part -
     * {@code iterateEntities()} walks every entity in every loaded world - so a second board
     * type with its own tick would double that cost for a feature that is two lines of text.
     * Both texts are still built lazily and at most once, so a server with no boards placed
     * does no work beyond the scan itself.
     */
    public static void tick(MinecraftServer server) {
        if (++clock < REFRESH_TICKS) return;
        clock = 0;
        Text careerBoard = null;
        Text seasonBoard = null;
        for (ServerWorld world : server.getWorlds()) {
            for (Entity e : world.iterateEntities()) {
                if (!(e instanceof DisplayEntity.TextDisplayEntity display)) continue;
                var tags = display.getCommandTags();
                if (tags.contains(TAG)) {
                    if (careerBoard == null) careerBoard = buildBoard(world);
                    ((TextDisplayInvoker) display).craftics$setText(careerBoard);
                } else if (tags.contains(SeasonLeaderboard.TAG)) {
                    if (seasonBoard == null) seasonBoard = SeasonLeaderboard.buildBoard(world);
                    ((TextDisplayInvoker) display).craftics$setText(seasonBoard);
                }
            }
        }
    }

    /** Every text display in {@code world} carrying {@code tag}. Shared with the season board. */
    public static List<DisplayEntity.TextDisplayEntity> taggedBoards(ServerWorld world, String tag) {
        List<DisplayEntity.TextDisplayEntity> out = new ArrayList<>();
        for (Entity e : world.iterateEntities()) {
            if (e instanceof DisplayEntity.TextDisplayEntity display
                    && display.getCommandTags().contains(tag)) {
                out.add(display);
            }
        }
        return out;
    }

    private static List<DisplayEntity.TextDisplayEntity> boardsIn(ServerWorld world) {
        return taggedBoards(world, TAG);
    }

    /** The board text: the top ten career point totals. */
    private static Text buildBoard(ServerWorld anyWorld) {
        CrafticsSavedData data = CrafticsSavedData.get(anyWorld);
        List<Object[]> rows = new ArrayList<>(); // [name, points, chapters]
        for (Map.Entry<UUID, CrafticsSavedData.PlayerData> entry : data.getAllPlayerData().entrySet()) {
            int points = entry.getValue().chapterPlacementPoints;
            if (points <= 0) continue;
            // A UUID fragment is not a name - see PlayerData.knownName().
            String name = entry.getValue().knownName();
            if (name == null) continue;
            rows.add(new Object[]{name, points, entry.getValue().chaptersPlaced});
        }
        rows.sort((a, b) -> Integer.compare((int) b[1], (int) a[1]));

        StringBuilder sb = new StringBuilder();
        sb.append("§6§lTOP PLAYERS§r\n§eALL TIME\n§8------------------\n");
        if (rows.isEmpty()) {
            sb.append("§7No chapter has ended yet.\n§7Standings appear at the first reset.");
        }
        for (int i = 0; i < Math.min(10, rows.size()); i++) {
            String rankColor = switch (i) {
                case 0 -> "§6";
                case 1 -> "§7";
                case 2 -> "§c";
                default -> "§8";
            };
            int chapters = (int) rows.get(i)[2];
            sb.append(rankColor).append(i + 1).append(". §f")
              .append(rows.get(i)[0]).append(" §7- §6").append(rows.get(i)[1])
              .append(" pts §8(").append(chapters)
              .append(chapters == 1 ? " chapter)" : " chapters)").append("\n");
        }
        return Text.literal(sb.toString().stripTrailing());
    }
}
