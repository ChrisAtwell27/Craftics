package com.crackedgames.craftics.client.menu;

import com.crackedgames.craftics.CrafticsMod;
import com.crackedgames.craftics.level.campaign.Campaign;
import com.crackedgames.craftics.level.campaign.CampaignManager;
import com.crackedgames.craftics.level.campaign.CampaignNode;
import com.crackedgames.craftics.level.campaign.CampaignRegion;
import com.crackedgames.craftics.level.campaign.VanillaCampaign;
import net.minecraft.client.MinecraftClient;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * "Where is the player?" snapshot for the custom title screen.
 * <p>
 * The main menu shows the biome the player is currently on in their most
 * recently played singleplayer world. No world is loaded at title-screen time,
 * so this reads the save files directly off disk on a worker thread:
 * <ol>
 *   <li>{@code saves/&lt;world&gt;/level.dat} - world display name + LastPlayed
 *       (most recent world wins),</li>
 *   <li>{@code saves/&lt;world&gt;/data/craftics_data.dat} - the same
 *       {@link com.crackedgames.craftics.world.CrafticsSavedData} NBT the server
 *       persists (per-player {@code highestBiomeUnlocked} / {@code activeBiomeId}
 *       / {@code discoveredBiomes} / {@code ngPlusLevel}).</li>
 * </ol>
 * Biome ids are resolved to names / regions / colors through the active
 * {@link Campaign} - registered from common mod init, so it's available on the
 * menu with no server running - falling back to the built-in vanilla campaign.
 * <p>
 * Every failure mode (no saves dir, corrupt NBT, unknown campaign biome) degrades
 * to a defaults snapshot; the menu never blocks or crashes on bad save data.
 */
public final class MenuWorldState {

    private MenuWorldState() {}

    /** One campaign node prepared for the menu's progress strip. */
    public record NodeView(String biomeId, String displayName, String regionId,
                           String regionIcon, int regionColor,
                           boolean cleared, boolean current, boolean discovered) {}

    /**
     * Everything the title screen needs. {@code worldFolder} is {@code null}
     * when no playable singleplayer world exists ("begin your run" state).
     */
    public record Snapshot(@Nullable String worldFolder,
                           String worldName,
                           String biomeId,
                           String biomeName,
                           String regionName,
                           String regionIcon,
                           int regionColor,
                           int highestUnlocked,
                           int totalBiomes,
                           int ngPlus,
                           List<String> backdropBiomes,
                           List<NodeView> path) {

        public boolean hasWorld() {
            return worldFolder != null;
        }
    }

    /** Load asynchronously; never completes exceptionally. */
    public static CompletableFuture<Snapshot> load(MinecraftClient client) {
        return CompletableFuture
            .supplyAsync(() -> scan(client), Util.getMainWorkerExecutor())
            .exceptionally(e -> {
                CrafticsMod.LOGGER.warn("Craftics menu: failed to read save data", e);
                return defaults(null, "");
            });
    }

    // ─────────────────────────────────────────────────────────────────────
    // Scan
    // ─────────────────────────────────────────────────────────────────────

    private static Snapshot scan(MinecraftClient client) {
        Path saves = client.getLevelStorage().getSavesDirectory();

        // Most recently played world, judged by level.dat's LastPlayed stamp.
        String bestFolder = null;
        String bestName = "";
        long bestPlayed = Long.MIN_VALUE;
        if (Files.isDirectory(saves)) {
            try (DirectoryStream<Path> dirs = Files.newDirectoryStream(saves)) {
                for (Path dir : dirs) {
                    Path levelDat = dir.resolve("level.dat");
                    if (!Files.isRegularFile(levelDat)) continue;
                    try {
                        NbtCompound root = NbtIo.readCompressed(levelDat, NbtSizeTracker.ofUnlimitedBytes());
                        NbtCompound data = child(root, "Data");
                        long played = i64(data, "LastPlayed", 0L);
                        if (played > bestPlayed) {
                            bestPlayed = played;
                            bestFolder = dir.getFileName().toString();
                            bestName = str(data, "LevelName", bestFolder);
                        }
                    } catch (Exception ignored) {
                        // Corrupt / locked level.dat - skip this world.
                    }
                }
            } catch (Exception e) {
                CrafticsMod.LOGGER.warn("Craftics menu: could not list saves directory", e);
            }
        }

        if (bestFolder == null) {
            return defaults(null, "");
        }

        // Craftics progress for that world. A world without craftics_data.dat
        // (never entered a run) still gets the fresh-run defaults.
        int highest = 1;
        int ngPlus = 0;
        int branch = 0;
        String activeBiome = "";
        LinkedHashSet<String> discovered = new LinkedHashSet<>();
        Path dataFile = saves.resolve(bestFolder).resolve("data").resolve("craftics_data.dat");
        if (Files.isRegularFile(dataFile)) {
            try {
                NbtCompound root = NbtIo.readCompressed(dataFile, NbtSizeTracker.ofUnlimitedBytes());
                NbtCompound me = pickPlayer(child(child(root, "data"), "players"), client);
                if (me != null) {
                    highest = Math.max(1, i32(me, "highestBiomeUnlocked", 1));
                    ngPlus = Math.max(0, i32(me, "ngPlusLevel", 0));
                    branch = i32(me, "branchChoice", 0) == 1 ? 1 : 0;
                    activeBiome = str(me, "activeBiomeId", "");
                    String raw = str(me, "discoveredBiomes", "");
                    if (!raw.isEmpty()) {
                        for (String id : raw.split(",")) {
                            if (!id.isBlank()) discovered.add(id.trim());
                        }
                    }
                }
            } catch (Exception e) {
                CrafticsMod.LOGGER.warn("Craftics menu: could not read craftics_data.dat for '{}'", bestFolder, e);
            }
        }

        return build(bestFolder, bestName, highest, ngPlus, branch, activeBiome, discovered);
    }

    /**
     * The saved-data entry for the local player: exact profile UUID first, then
     * the legacy pre-multiplayer placeholder UUID(0,0), then whichever entry has
     * progressed furthest (covers username/UUID changes between sessions).
     */
    @Nullable
    private static NbtCompound pickPlayer(NbtCompound players, MinecraftClient client) {
        UUID uuid = null;
        try {
            uuid = client.getGameProfile().getId();
        } catch (Exception ignored) {}
        if (uuid != null && players.contains(uuid.toString())) {
            return child(players, uuid.toString());
        }
        String legacy = new UUID(0, 0).toString();
        if (players.contains(legacy)) {
            return child(players, legacy);
        }
        NbtCompound best = null;
        int bestHighest = Integer.MIN_VALUE;
        for (String key : players.getKeys()) {
            NbtCompound entry = child(players, key);
            int h = i32(entry, "highestBiomeUnlocked", 1);
            if (h > bestHighest) {
                bestHighest = h;
                best = entry;
            }
        }
        return best;
    }

    // ─────────────────────────────────────────────────────────────────────
    // Snapshot assembly
    // ─────────────────────────────────────────────────────────────────────

    private static Snapshot build(@Nullable String worldFolder, String worldName,
                                  int highest, int ngPlus, int branch,
                                  String activeBiome, LinkedHashSet<String> discovered) {
        Campaign campaign = CampaignManager.active();
        if (campaign == null) campaign = VanillaCampaign.build();

        List<CampaignNode> nodes = campaign.orderedNodes(branch);
        List<String> order = campaign.orderedBiomeIds(branch);
        if (nodes.isEmpty()) {
            return defaults(worldFolder, worldName);
        }

        // The starting biome is always discovered.
        discovered.add(order.get(0));

        // "The biome you're on": mid-run biome when a run is active, otherwise
        // the next biome to tackle (the highestUnlocked cursor).
        int curIdx = Math.max(0, Math.min(highest - 1, nodes.size() - 1));
        if (!activeBiome.isEmpty()) {
            int i = order.indexOf(activeBiome);
            if (i >= 0) curIdx = i;
        }
        discovered.add(order.get(curIdx));

        List<NodeView> path = new ArrayList<>(nodes.size());
        for (int i = 0; i < nodes.size(); i++) {
            CampaignNode node = nodes.get(i);
            CampaignRegion region = campaign.regionOf(node.biomeId());
            path.add(new NodeView(
                node.biomeId(),
                nodeName(node),
                region != null ? region.id() : "",
                region != null ? region.icon() : "✦",
                region != null ? opaque(region.mapColor()) : 0xFF8888AA,
                i < curIdx || (i + 1) < highest,
                i == curIdx,
                discovered.contains(node.biomeId())));
        }

        // Backdrop rotation: current biome first, then the other discovered
        // biomes in campaign order (wrapping) - the menu slowly tours the
        // player's own journey.
        List<String> backdrop = new ArrayList<>();
        for (int i = 0; i < order.size(); i++) {
            String id = order.get((curIdx + i) % order.size());
            if (discovered.contains(id) && !backdrop.contains(id)) backdrop.add(id);
        }
        if (backdrop.isEmpty()) backdrop.add(order.get(0));

        NodeView cur = path.get(curIdx);
        CampaignRegion curRegion = campaign.regionOf(cur.biomeId());
        return new Snapshot(
            worldFolder, worldName,
            cur.biomeId(), cur.displayName(),
            curRegion != null ? curRegion.displayName() : "Unknown",
            cur.regionIcon(), cur.regionColor(),
            highest, nodes.size(), ngPlus,
            List.copyOf(backdrop), List.copyOf(path));
    }

    /** Fresh-run snapshot rooted at the campaign's first biome (or plains). */
    private static Snapshot defaults(@Nullable String worldFolder, String worldName) {
        return build(worldFolder, worldName, 1, 0, 0, "", new LinkedHashSet<>());
    }

    private static String nodeName(CampaignNode node) {
        if (node.labelOverride() != null && !node.labelOverride().isEmpty()) {
            return node.labelOverride();
        }
        return prettify(node.biomeId());
    }

    /** "soul_sand_valley" → "Soul Sand Valley" for biomes without a label. */
    public static String prettify(String biomeId) {
        String[] words = biomeId.replace(':', '_').split("_");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (w.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return sb.length() > 0 ? sb.toString() : biomeId;
    }

    private static int opaque(int argb) {
        return 0xFF000000 | (argb & 0x00FFFFFF);
    }

    // ─────────────────────────────────────────────────────────────────────
    // NBT accessors (getter signatures changed in 1.21.5)
    // ─────────────────────────────────────────────────────────────────────

    //? if <=1.21.4 {
    private static NbtCompound child(NbtCompound nbt, String key) {
        return nbt.getCompound(key);
    }

    private static String str(NbtCompound nbt, String key, String def) {
        return nbt.contains(key) ? nbt.getString(key) : def;
    }

    private static int i32(NbtCompound nbt, String key, int def) {
        return nbt.contains(key) ? nbt.getInt(key) : def;
    }

    private static long i64(NbtCompound nbt, String key, long def) {
        return nbt.contains(key) ? nbt.getLong(key) : def;
    }
    //?} else {
    /*private static NbtCompound child(NbtCompound nbt, String key) {
        return nbt.getCompoundOrEmpty(key);
    }

    private static String str(NbtCompound nbt, String key, String def) {
        return nbt.getString(key, def);
    }

    private static int i32(NbtCompound nbt, String key, int def) {
        return nbt.getInt(key, def);
    }

    private static long i64(NbtCompound nbt, String key, long def) {
        return nbt.getLong(key, def);
    }
    *///?}
}
