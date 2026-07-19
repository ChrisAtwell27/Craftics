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
 * Floating, live-updating "INFINITE MODE - HALL OF LEGENDS" board: a vanilla text display
 * entity tagged {@link #TAG}, refreshed from every player's banked best score.
 *
 * <p>Spawned and removed by admin command; the entity itself persists in the world save
 * like any display entity, so the board survives restarts with no bookkeeping of our own -
 * the refresh pass simply finds every tagged display in loaded worlds and rewrites its
 * text. Refresh runs every {@link #REFRESH_TICKS} server ticks off the aggregate tick,
 * and skips all work when no board exists in any loaded world.
 */
public final class InfiniteScoreboardHologram {
    private InfiniteScoreboardHologram() {}

    /** Command tag identifying our boards among the world's display entities. */
    public static final String TAG = "craftics_infinite_board";

    /** Refresh cadence: every 5 seconds is instant enough for a leaderboard. */
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

    /** Aggregate-tick hook: refresh every board in every loaded world on the cadence. */
    public static void tick(MinecraftServer server) {
        if (++clock < REFRESH_TICKS) return;
        clock = 0;
        Text board = null; // built lazily, once, only if some world actually has a board
        for (ServerWorld world : server.getWorlds()) {
            for (DisplayEntity.TextDisplayEntity display : boardsIn(world)) {
                if (board == null) board = buildBoard(world);
                ((TextDisplayInvoker) display).craftics$setText(board);
            }
        }
    }

    private static List<DisplayEntity.TextDisplayEntity> boardsIn(ServerWorld world) {
        List<DisplayEntity.TextDisplayEntity> out = new ArrayList<>();
        for (Entity e : world.iterateEntities()) {
            if (e instanceof DisplayEntity.TextDisplayEntity display
                    && display.getCommandTags().contains(TAG)) {
                out.add(display);
            }
        }
        return out;
    }

    /** The board text: header plus the top ten banked infinite scores, medal-colored. */
    private static Text buildBoard(ServerWorld anyWorld) {
        CrafticsSavedData data = CrafticsSavedData.get(anyWorld);
        List<Object[]> rows = new ArrayList<>(); // [name, score]
        for (Map.Entry<UUID, CrafticsSavedData.PlayerData> entry : data.getAllPlayerData().entrySet()) {
            int best = entry.getValue().highestInfiniteScore;
            if (best <= 0) continue;
            String name = entry.getValue().lastKnownName;
            if (name == null || name.isEmpty()) {
                name = entry.getKey().toString().substring(0, 8);
            }
            rows.add(new Object[]{name, best});
        }
        rows.sort((a, b) -> Integer.compare((int) b[1], (int) a[1]));

        StringBuilder sb = new StringBuilder();
        sb.append("§5§lINFINITE MODE§r\n§dHALL OF LEGENDS\n§8------------------\n");
        if (rows.isEmpty()) {
            sb.append("§7No runs recorded yet.\n§7Be the first!");
        }
        for (int i = 0; i < Math.min(10, rows.size()); i++) {
            String rankColor = switch (i) {
                case 0 -> "§6";
                case 1 -> "§7";
                case 2 -> "§c";
                default -> "§8";
            };
            sb.append(rankColor).append(i + 1).append(". §f")
              .append(rows.get(i)[0]).append(" §7- §5").append(rows.get(i)[1]).append("\n");
        }
        return Text.literal(sb.toString().stripTrailing());
    }
}
