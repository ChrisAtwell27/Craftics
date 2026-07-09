package com.crackedgames.craftics.world;

/**
 * Migrates a pre-"de-lane" island (built in the void overworld at a per-player
 * lane) into the player's new per-owner Fantasy dimension. Old layout put each
 * hub at (10000, 65, worldSlot*1000); the new layout puts every hub at (0,65,0)
 * inside craftics:island/&lt;uuid&gt;. Without this, an updating player's built base
 * is orphaned and /home drops them into the void (see
 * docs/superpowers/specs/2026-07-09-island-migration-design.md).
 *
 * <p>Geometry helpers here are pure and unit-tested; the world-touching entry
 * point {@code ensureMigrated} is added in Task 2.
 */
public final class IslandMigration {

    private IslandMigration() {}

    // === Historical old-layout constants (deleted from current CrafticsSavedData) ===
    static final int OLD_HUB_X = 10000;
    static final int OLD_HUB_Y = 65;
    static final int OLD_LANE_SPACING_Z = 1000;

    // === Copy box tuning ===
    static final int MARGIN = 64;
    static final int BOX_MIN_Y = 0;
    static final int BOX_MAX_Y = 160;

    /** Old overworld hub origin for a lane index: (10000, 65, slot*1000). */
    public static int[] oldHubOrigin(int worldSlot) {
        return new int[]{OLD_HUB_X, OLD_HUB_Y, worldSlot * OLD_LANE_SPACING_Z};
    }

    /** Immutable copy bounds (inclusive) in overworld coordinates. */
    public record MigrationBox(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {}

    /**
     * Copy bounds around the old hub: schem footprint (centered on the hub) plus
     * MARGIN blocks in each horizontal direction, Y clamped to [BOX_MIN_Y, BOX_MAX_Y].
     */
    public static MigrationBox migrationBox(int schemW, int schemL, int hubX, int hubY, int hubZ) {
        int halfW = schemW / 2;
        int halfL = schemL / 2;
        return new MigrationBox(
            hubX - halfW - MARGIN, BOX_MIN_Y, hubZ - halfL - MARGIN,
            hubX + halfW + MARGIN, BOX_MAX_Y, hubZ + halfL + MARGIN);
    }

    /** Map an old-overworld coord to its new-dim coord (preserves offset from hub). */
    public static int[] translate(int sx, int sy, int sz,
                                  int oldHubX, int oldHubY, int oldHubZ,
                                  int newHubX, int newHubY, int newHubZ) {
        return new int[]{
            sx - oldHubX + newHubX,
            sy - oldHubY + newHubY,
            sz - oldHubZ + newHubZ};
    }

    /**
     * Ensure {@code owner}'s island exists in the new dimension. Idempotent: no-op
     * once islandMigrated is set. On first call for an old-format save it probes the
     * old overworld lane; if the built base is there it copies it into the new dim,
     * otherwise it builds a fresh hub. Either way it stamps the island as built +
     * migrated so /home never voids the player. Never throws - failures fall back to
     * a fresh hub.
     */
    public static void ensureMigrated(net.minecraft.server.MinecraftServer server,
                                      java.util.UUID owner) {
        if (server == null || owner == null) return;
        net.minecraft.server.world.ServerWorld overworld = server.getOverworld();
        CrafticsSavedData data = CrafticsSavedData.get(overworld);
        CrafticsSavedData.PlayerData pd = data.getPlayerData(owner);

        if (pd.islandMigrated) return;                 // already handled
        if (!data.hasPersonalWorld(owner)) {           // not an island owner
            pd.islandMigrated = true;
            data.markDirty();
            return;
        }

        net.minecraft.server.world.ServerWorld island =
            IslandDimensions.getOrCreate(server, owner);
        net.minecraft.util.math.BlockPos newHub = data.getHubOrigin(owner);

        try {
            com.crackedgames.craftics.level.SchemLoader.SchemData schem = loadHomeSchem(server);
            int[] oldHub = oldHubOrigin(pd.worldSlot);
            boolean copied = false;
            if (schem != null && oldBaseExists(overworld, oldHub, schem)) {
                copyBase(overworld, island, schem, oldHub,
                    new int[]{newHub.getX(), newHub.getY(), newHub.getZ()}, pd);
                copied = true;
            }
            if (!copied) {
                net.minecraft.util.math.BlockPos spawn = HubRoomBuilder.build(island, newHub);
                pd.hubSpawnX = spawn.getX();
                pd.hubSpawnY = spawn.getY();
                pd.hubSpawnZ = spawn.getZ();
            }
        } catch (Exception e) {
            com.crackedgames.craftics.CrafticsMod.LOGGER.error(
                "[IslandMigration] copy failed for {} - building fresh hub", owner, e);
            net.minecraft.util.math.BlockPos spawn = HubRoomBuilder.build(island, newHub);
            pd.hubSpawnX = spawn.getX();
            pd.hubSpawnY = spawn.getY();
            pd.hubSpawnZ = spawn.getZ();
        }

        pd.personalHubBuilt = true;
        pd.personalHubVersion = HubRoomBuilder.HUB_VERSION;
        pd.islandMigrated = true;
        data.markDirty();
        com.crackedgames.craftics.CrafticsMod.LOGGER.info(
            "[IslandMigration] island of {} ensured in new dim (spawn {},{},{})",
            owner, pd.hubSpawnX, pd.hubSpawnY, pd.hubSpawnZ);
    }

    /** Load the bundled home.schem (footprint + palette) or null on failure. */
    private static com.crackedgames.craftics.level.SchemLoader.SchemData loadHomeSchem(
            net.minecraft.server.MinecraftServer server) {
        net.minecraft.util.Identifier id = net.minecraft.util.Identifier.of("craftics", "home.schem");
        var res = server.getResourceManager().getResource(id);
        if (res.isEmpty()) return null;
        try (java.io.InputStream in = res.get().getInputStream()) {
            return com.crackedgames.craftics.level.SchemLoader.load(in, id.toString());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Probe the old overworld lane for a built base. Scans the schem-footprint column
     * band around the old hub for ANY non-air block; the old hub schem was placed
     * raised +20 from hub Y, so scan a generous Y window. True => there is a base to copy.
     */
    private static boolean oldBaseExists(net.minecraft.server.world.ServerWorld overworld,
                                         int[] oldHub,
                                         com.crackedgames.craftics.level.SchemLoader.SchemData schem) {
        int halfW = schem.width() / 2;
        int halfL = schem.length() / 2;
        for (int y = BOX_MIN_Y; y <= BOX_MAX_Y; y += 4) {         // coarse Y step for the probe
            for (int dx = -halfW; dx <= halfW; dx += 4) {
                for (int dz = -halfL; dz <= halfL; dz += 4) {
                    net.minecraft.util.math.BlockPos p = new net.minecraft.util.math.BlockPos(
                        oldHub[0] + dx, y, oldHub[2] + dz);
                    if (!overworld.getBlockState(p).isAir()) return true;
                }
            }
        }
        return false;
    }

    /**
     * Block-by-block copy of the old base box into the new dim, translating coords and
     * carrying block entities (chest/barrel contents, signs) via NBT. Recomputes the
     * new-dim spawn from the podzol marker's translated position.
     */
    private static void copyBase(net.minecraft.server.world.ServerWorld src,
                                 net.minecraft.server.world.ServerWorld dst,
                                 com.crackedgames.craftics.level.SchemLoader.SchemData schem,
                                 int[] oldHub, int[] newHub,
                                 CrafticsSavedData.PlayerData pd) {
        MigrationBox box = migrationBox(schem.width(), schem.length(), oldHub[0], oldHub[1], oldHub[2]);
        var lookup = src.getRegistryManager();
        net.minecraft.util.math.BlockPos.Mutable sp = new net.minecraft.util.math.BlockPos.Mutable();
        net.minecraft.util.math.BlockPos.Mutable dp = new net.minecraft.util.math.BlockPos.Mutable();
        int[] podzolNew = null;

        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                for (int y = box.minY(); y <= box.maxY(); y++) {
                    sp.set(x, y, z);
                    net.minecraft.block.BlockState state = src.getBlockState(sp);
                    int[] t = translate(x, y, z, oldHub[0], oldHub[1], oldHub[2],
                        newHub[0], newHub[1], newHub[2]);
                    dp.set(t[0], t[1], t[2]);
                    dst.setBlockState(dp, state, net.minecraft.block.Block.NOTIFY_LISTENERS);

                    net.minecraft.block.entity.BlockEntity srcBe = src.getBlockEntity(sp);
                    if (srcBe != null) {
                        // Round-trip the source BE's data into the freshly-placed dst BE.
                        net.minecraft.nbt.NbtCompound beNbt = srcBe.createNbtWithIdentifyingData(lookup);
                        net.minecraft.block.entity.BlockEntity dstBe = dst.getBlockEntity(dp);
                        if (dstBe != null) {
                            dstBe.read(beNbt, lookup);
                            dstBe.markDirty();
                        }
                    }
                    if (state.isOf(net.minecraft.block.Blocks.PODZOL)) {
                        podzolNew = new int[]{t[0], t[1] + 1, t[2]}; // spawn one above podzol
                    }
                }
            }
        }

        if (podzolNew != null) {
            pd.hubSpawnX = podzolNew[0];
            pd.hubSpawnY = podzolNew[1];
            pd.hubSpawnZ = podzolNew[2];
        } else {
            // No marker found (unusual): fall back to the translated old spawn.
            int[] t = translate(pd.hubSpawnX, pd.hubSpawnY, pd.hubSpawnZ,
                oldHub[0], oldHub[1], oldHub[2], newHub[0], newHub[1], newHub[2]);
            pd.hubSpawnX = t[0];
            pd.hubSpawnY = t[1];
            pd.hubSpawnZ = t[2];
        }
    }
}
