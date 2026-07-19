package com.crackedgames.craftics.level;

import com.crackedgames.craftics.CrafticsMod;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FallingBlock;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Loads WorldEdit .schem files (Sponge Schematic v2/v3) and places them in the world */
public class SchemLoader {

    /**
     * Pure buried/exposed test over a flat volume grid: true when any face of
     * (x,y,z) borders something that doesn't fully hide it (out-of-bounds
     * counts as open - sides, sky, AND the underside so floating builds like
     * the home island keep a correct bottom face).
     *
     * <p>This is the single source of truth for "what the visibility cull
     * skips" - place()'s keep[] mask, repairBuried()'s fill set, and
     * undoRepairBuried()'s removal set all derive from it, which is what makes
     * the repair and its undo exact inverses. Static and MC-free so
     * {@code SchemBuriedMaskTest} can pin the geometry down.
     */
    static boolean isExposedAt(int[] paletteIds, boolean[] palHides,
                               int width, int height, int length, int x, int y, int z) {
        return faceOpenAt(paletteIds, palHides, width, height, length, x + 1, y, z)
            || faceOpenAt(paletteIds, palHides, width, height, length, x - 1, y, z)
            || faceOpenAt(paletteIds, palHides, width, height, length, x, y, z + 1)
            || faceOpenAt(paletteIds, palHides, width, height, length, x, y, z - 1)
            || faceOpenAt(paletteIds, palHides, width, height, length, x, y + 1, z)
            || faceOpenAt(paletteIds, palHides, width, height, length, x, y - 1, z);
    }

    private static boolean faceOpenAt(int[] paletteIds, boolean[] palHides,
                                      int width, int height, int length, int x, int y, int z) {
        if (x < 0 || x >= width || y < 0 || y >= height || z < 0 || z >= length) return true;
        int id = paletteIds[((y * length) + z) * width + x];
        return id < 0 || id >= palHides.length || !palHides[id];
    }

    /**
     * The visibility cull's keep/cull decision over a whole volume: true at a
     * flat index means the solid there must be placed. Air is never kept -
     * place() writes it unconditionally.
     *
     * <p>{@code palCorner} flags the arena corner markers
     * ({@code craftics:arena_corner}). 2+ of them mean {@code ArenaBuilder}
     * will read the arena floor off their Y (3+ as a polygon outline, exactly
     * 2 as a rectangle's opposite corner pair), so the floor and the layer
     * supporting it are force-kept inside the corners' X/Z bounding box.
     * Without that, the support layer is fully buried and the cull deletes it,
     * dropping the arena floor into the void. Legacy DIAMOND+EMERALD rectangle
     * arenas carry no corner markers and take the untouched path; a single
     * stray corner defines no footprint and takes it too.
     *
     * <p>Static and MC-free (plain int[]/boolean[]) so
     * {@code SchemArenaFloorKeepTest} can pin the geometry down without a
     * Minecraft bootstrap.
     */
    static boolean[] computeKeepMask(int[] paletteIds, boolean[] palAir, boolean[] palHides,
                                     boolean[] palExempt, boolean[] palCorner,
                                     int width, int height, int length, boolean cullBuried) {
        return computeKeepMask(paletteIds, palAir, palHides, palExempt, palCorner,
            width, height, length, cullBuried, false);
    }

    /**
     * @param taperEdges true when {@code ArenaBuilder.taperSchemEdges} will carve
     *                   a stepped slope into this volume's outer tiles after
     *                   placement. Those columns are then kept solid rather than
     *                   hollow, because the carve turns their buried interior
     *                   into a visible face. See {@link #taperExposedAt}.
     */
    static boolean[] computeKeepMask(int[] paletteIds, boolean[] palAir, boolean[] palHides,
                                     boolean[] palExempt, boolean[] palCorner,
                                     int width, int height, int length, boolean cullBuried,
                                     boolean taperEdges) {
        int palCount = palAir.length;
        boolean[] keep = new boolean[width * height * length];
        for (int y = 0; y < height; y++) {
            for (int z = 0; z < length; z++) {
                for (int x = 0; x < width; x++) {
                    int flat = ((y * length) + z) * width + x;
                    int id = paletteIds[flat];
                    if (id < 0 || id >= palCount || palAir[id]) continue;
                    keep[flat] = !cullBuried
                        || palExempt[id]
                        || isExposedAt(paletteIds, palHides, width, height, length, x, y, z)
                        || (taperEdges && taperExposedAt(width, height, length, x, y, z));
                }
            }
        }
        if (cullBuried) forceKeepArenaFloor(paletteIds, palAir, palCorner, width, height, length, keep);
        return keep;
    }

    /**
     * True when {@code ArenaBuilder}'s edge taper will expose (x,y,z).
     *
     * <p>The taper carves every column within {@code TAPER_FADE} tiles of an
     * edge down to a stepped surface that rises 2 blocks per tile inward, so the
     * arena's bank reads as a slope instead of a sliced-off wall. The cull runs
     * first and only keeps the schematic's visible shell, which means the carve
     * cuts straight into hollow interior: the surviving stepped geometry then
     * hangs over open space, and the arena's underside shows through its own
     * edge. Keeping the whole carved column solid closes that cavity at the one
     * place that can tell terrain from an intentional gap - the schematic.
     *
     * <p>Everything at or below the taper's surface in a fade column is kept:
     * the surface tile becomes the new top face, and the blocks under it back
     * the slope's riser where the next step out cuts lower still.
     *
     * <p>Mirrors {@code ArenaBuilder.taperMaxKeepLocalY} / {@code taperEdgeDist};
     * keep the two in sync.
     */
    static boolean taperExposedAt(int width, int height, int length, int x, int y, int z) {
        int dist = Math.min(Math.min(x, width - 1 - x), Math.min(z, length - 1 - z));
        if (dist >= TAPER_FADE) return false;
        return y <= Math.min(dist * 2, height - 1);
    }

    /** Mirrors {@code ArenaBuilder.TAPER_FADE}; keep the two in sync. */
    static final int TAPER_FADE = 4;

    /**
     * Force-keep the corner-marked arena's floor and its support layer. The
     * corners' bounding box is used as the footprint rather than a true
     * point-in-polygon test: it over-keeps a few blocks just outside the
     * outline's diagonal edges, which costs nothing next to a missing floor.
     * A 2-corner rectangle pair defines the same bbox, so it shares the path.
     */
    private static void forceKeepArenaFloor(int[] paletteIds, boolean[] palAir, boolean[] palCorner,
                                            int width, int height, int length, boolean[] keep) {
        int palCount = palAir.length;
        int corners = 0;
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        int floorY = Integer.MAX_VALUE;
        for (int y = 0; y < height; y++) {
            for (int z = 0; z < length; z++) {
                for (int x = 0; x < width; x++) {
                    int id = paletteIds[((y * length) + z) * width + x];
                    if (id < 0 || id >= palCount || !palCorner[id]) continue;
                    corners++;
                    minX = Math.min(minX, x); maxX = Math.max(maxX, x);
                    minZ = Math.min(minZ, z); maxZ = Math.max(maxZ, z);
                    // Lowest marker wins if a schematic ever staggers them;
                    // ArenaBuilder reads one Y, and keeping the deeper layer
                    // is the safe side of the guess.
                    floorY = Math.min(floorY, y);
                }
            }
        }
        if (corners < 2) return;

        for (int y = Math.max(0, floorY - 1); y <= floorY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int x = minX; x <= maxX; x++) {
                    int flat = ((y * length) + z) * width + x;
                    int id = paletteIds[flat];
                    if (id < 0 || id >= palCount || palAir[id]) continue;
                    keep[flat] = true;
                }
            }
        }
    }

    /**
     * Marks the cell one below a kept gravity block as needing a synthesized
     * support, returned by {@link #computeGravitySupport} as a flat index of
     * {@code SYNTH_BELOW_BOTTOM} when the gravity block sits on y=0 and there is
     * no cell below it inside the volume.
     */
    static final int SYNTH_BELOW_BOTTOM = -1;

    /**
     * The gravity-support pass over a whole volume, split out of {@link
     * SchemData#place} so it can be pinned down without a Minecraft bootstrap.
     *
     * <p>Every kept gravity block (sand, gravel, concrete powder, powder snow)
     * must rest on something actually placed, or the first block update drops it
     * as a falling entity and leaves a hole in the arena floor. Two outcomes per
     * gravity block:
     *
     * <ul>
     *   <li>a real block below that will not fall through: force-keep it, by
     *       setting {@code keep[belowFlat]} even when the cull buried it;</li>
     *   <li>otherwise: the caller must synthesize one solid block below, which
     *       this reports by writing the gravity block's own flat index into
     *       {@code synthSource[belowFlat]} (the caller derives the material from
     *       it via {@code supportFor}).</li>
     * </ul>
     *
     * <p>Gravity blocks on y=0 have no cell below them inside the volume, so
     * their support cannot be represented in {@code synthSource}. They are
     * reported through {@code bottomSynth} instead: a length-{@code width *
     * length} array whose x/z entries hold the flat index of the y=0 gravity
     * block needing a support placed one block BELOW the volume's floor. The
     * arena floor of a hollow arena is exactly this case: the schematic's ground
     * runs to the bottom edge of the volume, so any sand there had nothing under
     * it and fell into the void on load.
     *
     * <p>Scanned top-down so force-kept gravity chains cascade their own
     * supports (a sand column resolves from the top block downward).
     *
     * @param keep       the keep mask, mutated in place to force-keep real supports
     * @param synthSource per-cell source gravity block index, or -1 for none
     * @param bottomSynth per-x/z source gravity block index for below-volume
     *                    supports, or -1 for none
     */
    static void computeGravitySupport(int[] paletteIds, boolean[] palGravity,
                                      boolean[] palFallThrough, boolean[] palExempt,
                                      boolean[] palPresent,
                                      int width, int height, int length,
                                      boolean[] keep, int[] synthSource, int[] bottomSynth) {
        int palCount = palGravity.length;
        java.util.Arrays.fill(synthSource, -1);
        java.util.Arrays.fill(bottomSynth, -1);
        for (int y = height - 1; y >= 0; y--) {
            for (int z = 0; z < length; z++) {
                for (int x = 0; x < width; x++) {
                    int flat = ((y * length) + z) * width + x;
                    int id = paletteIds[flat];
                    if (id < 0 || id >= palCount || !palGravity[id] || !keep[flat]) continue;
                    if (y == 0) {
                        // Nothing below inside the volume: the support has to go
                        // one block under the volume's own floor.
                        if (bottomSynth[z * width + x] < 0) bottomSynth[z * width + x] = flat;
                        continue;
                    }
                    int belowFlat = (((y - 1) * length) + z) * width + x;
                    int belowId = paletteIds[belowFlat];
                    boolean realSupport = belowId >= 0 && belowId < palCount
                        && palPresent[belowId]
                        && (!palFallThrough[belowId] || palExempt[belowId]);
                    if (realSupport) {
                        keep[belowFlat] = true;
                    } else if (synthSource[belowFlat] < 0) {
                        synthSource[belowFlat] = flat;
                    }
                }
            }
        }
    }

    /** One schematic block entity: schematic-local position + full NBT payload ("id" set). */
    public record SchemBlockEntity(int x, int y, int z, NbtCompound nbt) {}

    public record SchemData(int width, int height, int length, BlockState[] palette, byte[] blockData,
                            java.util.List<SchemBlockEntity> blockEntities) {

        /**
         * Vanilla blocks the post-place scans treat as functional markers
         * (arena corners + spawns in {@code ArenaBuilder}, hub spawn podzol in
         * {@code HubRoomBuilder}). Never culled, even when fully buried, so
         * the scans always find them.
         */
        private static final Set<Block> MARKER_BLOCKS = Set.of(
            Blocks.DIAMOND_BLOCK, Blocks.EMERALD_BLOCK, Blocks.GOLD_BLOCK,
            Blocks.IRON_BLOCK, Blocks.COPPER_BLOCK, Blocks.COAL_BLOCK, Blocks.PODZOL);

        /** Place with the terrain-style visibility cull (arenas): buried filler
         *  is skipped. Solid-structure schematics (the home island) must use
         *  {@link #place(ServerWorld, int, int, int, boolean)} with
         *  {@code cullBuried = false} or their interiors generate hollow. */
        public void place(ServerWorld world, int placeX, int placeY, int placeZ) {
            place(world, placeX, placeY, placeZ, true);
        }

        /**
         * Place with the visibility cull, telling it whether {@code ArenaBuilder}
         * will taper this volume's edges afterwards so the cull can keep the
         * columns that carve will expose. See {@link #taperExposedAt}.
         */
        public void placeTapered(ServerWorld world, int placeX, int placeY, int placeZ,
                                 boolean taperEdges) {
            place(world, placeX, placeY, placeZ, true, taperEdges);
        }

        /** Decode the varint-packed block indices, or null if truncated. */
        private int[] decodePaletteIds() {
            int total = width * height * length;
            int[] paletteIds = new int[total];
            int index = 0;
            for (int y = 0; y < height; y++) {
                for (int z = 0; z < length; z++) {
                    for (int x = 0; x < width; x++) {
                        int paletteId = 0;
                        int shift = 0;
                        int b;
                        do {
                            if (index >= blockData.length) return null;
                            b = blockData[index++] & 0xFF;
                            paletteId |= (b & 0x7F) << shift;
                            shift += 7;
                        } while ((b & 0x80) != 0);

                        int flat = ((y * length) + z) * width + x;
                        paletteIds[flat] = paletteId;
                    }
                }
            }
            return paletteIds;
        }

        public void place(ServerWorld world, int placeX, int placeY, int placeZ, boolean cullBuried) {
            place(world, placeX, placeY, placeZ, cullBuried, false);
        }

        public void place(ServerWorld world, int placeX, int placeY, int placeZ,
                          boolean cullBuried, boolean taperEdges) {
            int total = width * height * length;
            int[] paletteIds = decodePaletteIds();
            if (paletteIds == null) return;

            // Classify the palette once instead of re-deciding per block
            int palCount = palette.length;
            boolean[] palAir = new boolean[palCount];
            boolean[] palLiquid = new boolean[palCount];      // holds any fluid (water, lava, waterlogged)
            boolean[] palGravity = new boolean[palCount];
            boolean[] palHides = new boolean[palCount];       // opaque full cube: fully hides the face behind it
            boolean[] palFallThrough = new boolean[palCount]; // a gravity block would fall through it
            boolean[] palExempt = new boolean[palCount];      // never culled
            boolean[] palCorner = new boolean[palCount];      // polygon arena outline marker
            for (int i = 0; i < palCount; i++) {
                BlockState s = palette[i];
                if (s == null) continue;
                Block block = s.getBlock();
                palCorner[i] = block == com.crackedgames.craftics.block.ModBlocks.ARENA_CORNER_BLOCK;
                palAir[i] = s.isAir();
                // Fluid-state check instead of isLiquid()/opacity: on some MC
                // versions fluids report as opaque full cubes (their culling
                // shape is a full cube so adjacent water faces cull each
                // other), which made deep water hide its own floor. This also
                // catches waterlogged blocks, kelp, seagrass etc.
                palLiquid[i] = !s.getFluidState().isEmpty();
                // Powder snow is not a FallingBlock, but arena generation still
                // needs support injected under unsupported powder snow tiles so
                // they don't pop when the arena is loaded.
                palGravity[i] = block instanceof FallingBlock || block == Blocks.POWDER_SNOW;
                // Liquids count as air for culling: water (and lava) never
                // hides a face, so the floors and walls of rivers and other
                // bodies of water are always kept.
                palHides[i] = !palAir[i] && !palLiquid[i] && isOpaqueFullCube(s);
                palFallThrough[i] = canFallThrough(s);
                // Functional blocks must always be placed: marker blocks the
                // post-place scans look for, block entities (chests, spawners,
                // signs...) and anything modded (scene/corner markers etc.).
                palExempt[i] = !palAir[i]
                    && (MARKER_BLOCKS.contains(block)
                        || block instanceof BlockEntityProvider
                        || !"minecraft".equals(Registries.BLOCK.getId(block).getNamespace()));
            }

            // Visibility cull: only place blocks with at least one face that
            // can actually be seen - bordering air or any non-opaque block
            // (glass, stairs, water...) inside the volume, which also keeps
            // interior rooms and caves intact, or sitting on the volume's
            // outer boundary. Fully buried filler is skipped entirely; on
            // terrain-style schematics that is the bulk of the blocks.
            boolean[] keep = computeKeepMask(paletteIds, palAir, palHides, palExempt, palCorner,
                width, height, length, cullBuried, taperEdges);

            int nonAir = 0;
            for (int flat = 0; flat < total; flat++) {
                int id = paletteIds[flat];
                if (id < 0 || id >= palCount || palette[id] == null || palAir[id]) continue;
                nonAir++;
            }

            // Gravity supports: every kept gravity block must rest on
            // something that is actually placed, or the first block update in
            // combat drops it as a falling entity (and breaks any snow layer
            // riding on top). If the schematic has a real block below,
            // force-keep it even when buried. If there is a gap below (air,
            // water, snow layers...), synthesize ONE matching solid block
            // instead of the old same-material column fill: sand -> sandstone,
            // red sand -> red sandstone, gravel -> stone, concrete powder ->
            // its concrete. A solid block cannot fall, so a single one is
            // always enough. Gravity blocks on the volume's bottom edge get
            // their support one block below the volume: an arena's ground runs
            // to that edge, so sand there had nothing under it at all.
            boolean[] palPresent = new boolean[palCount];
            for (int i = 0; i < palCount; i++) palPresent[i] = palette[i] != null;

            int[] synthSource = new int[total];
            int[] bottomSynth = new int[width * length];
            computeGravitySupport(paletteIds, palGravity, palFallThrough, palExempt, palPresent,
                width, height, length, keep, synthSource, bottomSynth);

            BlockState[] synthetic = new BlockState[total];
            int supports = 0;
            for (int flat = 0; flat < total; flat++) {
                if (synthSource[flat] < 0) continue;
                synthetic[flat] = supportFor(palette[paletteIds[synthSource[flat]]]);
                supports++;
            }

            // Supports for the y=0 gravity blocks, one block under the volume.
            for (int z = 0; z < length; z++) {
                for (int x = 0; x < width; x++) {
                    int src = bottomSynth[z * width + x];
                    if (src < 0) continue;
                    world.setBlockState(
                        new BlockPos(placeX + x, placeY - 1, placeZ + z),
                        supportFor(palette[paletteIds[src]]), ArenaBuilder.SET_FLAGS
                    );
                    supports++;
                }
            }

            int placed = 0;
            // Pass 1: air (always placed so the region is cleared), synthetic
            // supports, and kept non-gravity blocks - supports and surfaces
            // must exist before sand/gravel lands on them.
            for (int y = 0; y < height; y++) {
                for (int z = 0; z < length; z++) {
                    for (int x = 0; x < width; x++) {
                        int flat = ((y * length) + z) * width + x;
                        if (synthetic[flat] != null) {
                            world.setBlockState(
                                new BlockPos(placeX + x, placeY + y, placeZ + z),
                                synthetic[flat], ArenaBuilder.SET_FLAGS
                            );
                            placed++;
                            continue;
                        }
                        int id = paletteIds[flat];
                        if (id < 0 || id >= palCount) continue;
                        BlockState state = palette[id];
                        if (state == null) continue;
                        if (palAir[id]) {
                            world.setBlockState(
                                new BlockPos(placeX + x, placeY + y, placeZ + z),
                                state, ArenaBuilder.SET_FLAGS
                            );
                        } else if (!palGravity[id] && keep[flat]) {
                            world.setBlockState(
                                new BlockPos(placeX + x, placeY + y, placeZ + z),
                                state, ArenaBuilder.SET_FLAGS
                            );
                            placed++;
                        }
                    }
                }
            }

            // Pass 2: gravity blocks last so every support already exists
            for (int y = 0; y < height; y++) {
                for (int z = 0; z < length; z++) {
                    for (int x = 0; x < width; x++) {
                        int flat = ((y * length) + z) * width + x;
                        int id = paletteIds[flat];
                        if (id < 0 || id >= palCount) continue;
                        BlockState state = palette[id];
                        if (state != null && palGravity[id] && keep[flat] && synthetic[flat] == null) {
                            world.setBlockState(
                                new BlockPos(placeX + x, placeY + y, placeZ + z),
                                state, ArenaBuilder.SET_FLAGS
                            );
                            placed++;
                        }
                    }
                }
            }

            CrafticsMod.LOGGER.info(
                "Placed schematic: {} of {} solid blocks set ({} buried blocks culled, {} gravity supports added)",
                placed, nonAir, nonAir - (placed - supports), supports);

            applyBlockEntities(world, placeX, placeY, placeZ);
        }

        /**
         * Restore the schematic's saved block-entity NBT (sign text, chest
         * contents, banner patterns, ...) onto the blocks just placed. Positions
         * whose block wasn't placed, or whose block holds no block entity, are
         * skipped - block-entity blocks are cull-exempt in place(), so anything
         * with saved data is normally present.
         */
        private void applyBlockEntities(ServerWorld world, int placeX, int placeY, int placeZ) {
            if (blockEntities == null || blockEntities.isEmpty()) return;
            int restored = 0;
            for (SchemBlockEntity sbe : blockEntities) {
                BlockPos pos = new BlockPos(placeX + sbe.x(), placeY + sbe.y(), placeZ + sbe.z());
                net.minecraft.block.entity.BlockEntity be = world.getBlockEntity(pos);
                if (be == null) continue;
                NbtCompound nbt = sbe.nbt().copy();
                nbt.putInt("x", pos.getX());
                nbt.putInt("y", pos.getY());
                nbt.putInt("z", pos.getZ());
                try {
                    be.read(nbt, world.getRegistryManager());
                    be.markDirty();
                    world.updateListeners(pos, world.getBlockState(pos), world.getBlockState(pos), 3);
                    restored++;
                } catch (Exception e) {
                    CrafticsMod.LOGGER.warn("Failed to restore block entity at {}: {}", pos, e.toString());
                }
            }
            if (restored > 0) {
                CrafticsMod.LOGGER.info("Restored {} block entities from schematic", restored);
            }
        }

        /**
         * Repair pass for volumes placed by older builds through the visibility
         * cull: re-place ONLY the buried solids the cull skipped, and only into
         * positions the world currently has as air. Surface blocks the player
         * mined and anything the player built are untouched - this fills
         * exactly the holes the cull bug left behind, nothing else.
         *
         * <p>The "what was culled" math must mirror {@link #place}'s keep[]
         * computation (exempt || exposed); keep the two in sync.
         *
         * @return the number of blocks filled in
         */
        public int repairBuried(ServerWorld world, int placeX, int placeY, int placeZ) {
            int[] paletteIds = decodePaletteIds();
            if (paletteIds == null) return 0;

            int palCount = palette.length;
            boolean[] palAir = new boolean[palCount];
            boolean[] palHides = new boolean[palCount];
            boolean[] palExempt = new boolean[palCount];
            for (int i = 0; i < palCount; i++) {
                BlockState s = palette[i];
                if (s == null) continue;
                Block block = s.getBlock();
                palAir[i] = s.isAir();
                boolean liquid = !s.getFluidState().isEmpty();
                palHides[i] = !palAir[i] && !liquid && isOpaqueFullCube(s);
                palExempt[i] = !palAir[i]
                    && (MARKER_BLOCKS.contains(block)
                        || block instanceof BlockEntityProvider
                        || !"minecraft".equals(Registries.BLOCK.getId(block).getNamespace()));
            }

            int filled = 0;
            for (int y = 0; y < height; y++) {
                for (int z = 0; z < length; z++) {
                    for (int x = 0; x < width; x++) {
                        int flat = ((y * length) + z) * width + x;
                        int id = paletteIds[flat];
                        if (id < 0 || id >= palCount || palette[id] == null || palAir[id]) continue;
                        // Only the blocks the cull skipped: buried, non-exempt solids.
                        if (palExempt[id] || isExposed(paletteIds, palHides, x, y, z)) continue;
                        BlockPos pos = new BlockPos(placeX + x, placeY + y, placeZ + z);
                        if (!world.getBlockState(pos).isAir()) continue;
                        // NOTIFY_LISTENERS (unlike arena builds): the island's chunks
                        // can be loaded and watched while this runs, and un-notified
                        // fills render as invisible ghost blocks to those clients.
                        world.setBlockState(pos, palette[id],
                            Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
                        filled++;
                    }
                }
            }
            if (filled > 0) {
                CrafticsMod.LOGGER.info("Schematic repair: filled {} hollowed-out interior blocks", filled);
            }
            return filled;
        }

        /**
         * Inverse of {@link #repairBuried}: remove exactly the blocks that pass
         * could have placed - buried, non-exempt schematic solids whose world
         * block still MATCHES the schematic state - setting them back to air.
         * Anything a player placed (different state) or that was never filled
         * is untouched. Used by {@code /craftics world undorepair} to revert
         * the briefly-shipped automatic island repair on saves it misfired on.
         *
         * @return the number of blocks removed
         */
        public int undoRepairBuried(ServerWorld world, int placeX, int placeY, int placeZ) {
            int[] paletteIds = decodePaletteIds();
            if (paletteIds == null) return 0;

            int palCount = palette.length;
            boolean[] palAir = new boolean[palCount];
            boolean[] palHides = new boolean[palCount];
            boolean[] palExempt = new boolean[palCount];
            for (int i = 0; i < palCount; i++) {
                BlockState s = palette[i];
                if (s == null) continue;
                Block block = s.getBlock();
                palAir[i] = s.isAir();
                boolean liquid = !s.getFluidState().isEmpty();
                palHides[i] = !palAir[i] && !liquid && isOpaqueFullCube(s);
                palExempt[i] = !palAir[i]
                    && (MARKER_BLOCKS.contains(block)
                        || block instanceof BlockEntityProvider
                        || !"minecraft".equals(Registries.BLOCK.getId(block).getNamespace()));
            }

            int removed = 0;
            for (int y = 0; y < height; y++) {
                for (int z = 0; z < length; z++) {
                    for (int x = 0; x < width; x++) {
                        int flat = ((y * length) + z) * width + x;
                        int id = paletteIds[flat];
                        if (id < 0 || id >= palCount || palette[id] == null || palAir[id]) continue;
                        // Same buried set repairBuried fills.
                        if (palExempt[id] || isExposed(paletteIds, palHides, x, y, z)) continue;
                        BlockPos pos = new BlockPos(placeX + x, placeY + y, placeZ + z);
                        if (!world.getBlockState(pos).equals(palette[id])) continue;
                        world.setBlockState(pos, Blocks.AIR.getDefaultState(),
                            Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
                        removed++;
                    }
                }
            }
            if (removed > 0) {
                CrafticsMod.LOGGER.info("Schematic un-repair: removed {} filled interior blocks", removed);
            }
            return removed;
        }

        /** True if any face of (x,y,z) borders something that doesn't fully hide it. */
        private boolean isExposed(int[] paletteIds, boolean[] palHides, int x, int y, int z) {
            return isExposedAt(paletteIds, palHides, width, height, length, x, y, z);
        }

        /**
         * Solid support block placed under an unsupported gravity block:
         * sand -> sandstone, red sand -> red sandstone, gravel -> stone,
         * concrete powder -> matching concrete, anything else -> stone.
         */
        private static BlockState supportFor(BlockState gravityState) {
            Block block = gravityState.getBlock();
            if (block == Blocks.SAND || block == Blocks.SUSPICIOUS_SAND) return Blocks.SANDSTONE.getDefaultState();
            if (block == Blocks.RED_SAND) return Blocks.RED_SANDSTONE.getDefaultState();
            if (block == Blocks.GRAVEL || block == Blocks.SUSPICIOUS_GRAVEL) return Blocks.STONE.getDefaultState();
            String path = Registries.BLOCK.getId(block).getPath();
            if (path.endsWith("_concrete_powder")) {
                Block concrete = Registries.BLOCK.get(Identifier.of("minecraft",
                    path.substring(0, path.length() - "_powder".length())));
                if (concrete != Blocks.AIR) return concrete.getDefaultState();
            }
            return Blocks.STONE.getDefaultState();
        }

        /** Version-split: the full-cube check signature differs between MC versions. */
        private static boolean isOpaqueFullCube(BlockState state) {
            //? if <=1.21.1 {
            return state.isOpaqueFullCube(net.minecraft.world.EmptyBlockView.INSTANCE, net.minecraft.util.math.BlockPos.ORIGIN);
            //?} else {
            /*return state.isOpaqueFullCube();
            *///?}
        }

        /**
         * Mirrors vanilla FallingBlock.canFallThrough, except powder snow is
         * treated as a valid support for our synthetic-support pass so stacked
         * powder doesn't get replaced by stone.
         */
        private static boolean canFallThrough(BlockState state) {
            if (state.getBlock() == Blocks.POWDER_SNOW) return false;
            return state.isAir() || state.isReplaceable() || state.isLiquid()
                || state.isIn(net.minecraft.registry.tag.BlockTags.FIRE);
        }
    }

    public static SchemData load(InputStream in, String sourceName) {
        try {
            return parseSchem(in, sourceName);
        } catch (Exception e) {
            CrafticsMod.LOGGER.error("Failed to load .schem from resource: {}", sourceName, e);
            return null;
        }
    }

    public static SchemData load(Path schemPath) {
        if (!Files.exists(schemPath)) return null;
        try (InputStream in = Files.newInputStream(schemPath)) {
            return parseSchem(in, schemPath.toString());
        } catch (Exception e) {
            CrafticsMod.LOGGER.error("Failed to load .schem file: {}", schemPath, e);
            return null;
        }
    }

    private static SchemData parseSchem(InputStream in, String sourceName) throws Exception {
        NbtCompound root = NbtIo.readCompressed(in, NbtSizeTracker.ofUnlimitedBytes());

        // v3 wraps in "Schematic" compound; v2 is flat
        NbtCompound schem;
        if (root.contains("Schematic")) {
            //? if <=1.21.4 {
            schem = root.getCompound("Schematic");
            //?} else
            /*schem = root.getCompoundOrEmpty("Schematic");*/
        } else {
            schem = root;
        }

        //? if <=1.21.4 {
        int schemWidth = schem.getShort("Width") & 0xFFFF;
        int schemHeight = schem.getShort("Height") & 0xFFFF;
        int schemLength = schem.getShort("Length") & 0xFFFF;
        //?} else {
        /*int schemWidth = schem.getShort("Width").orElse((short) 0) & 0xFFFF;
        int schemHeight = schem.getShort("Height").orElse((short) 0) & 0xFFFF;
        int schemLength = schem.getShort("Length").orElse((short) 0) & 0xFFFF;
        *///?}

        if (schemWidth == 0 || schemHeight == 0 || schemLength == 0) {
            CrafticsMod.LOGGER.warn("Invalid schematic dimensions in {}: {}x{}x{}",
                sourceName, schemWidth, schemHeight, schemLength);
            return null;
        }

        // Palette + block data location differs per version
        NbtCompound paletteNbt;
        byte[] blockDataBytes;
        NbtCompound beContainer; // where the BlockEntities list lives (v3: Blocks, v2: root)

        if (schem.contains("Blocks")) {
            //? if <=1.21.4 {
            // v3
            NbtCompound blocks = schem.getCompound("Blocks");
            paletteNbt = blocks.getCompound("Palette");
            blockDataBytes = blocks.getByteArray("Data");
            //?} else {
            /*// v3
            NbtCompound blocks = schem.getCompoundOrEmpty("Blocks");
            paletteNbt = blocks.getCompoundOrEmpty("Palette");
            blockDataBytes = blocks.getByteArray("Data").orElse(new byte[0]);
            *///?}
            beContainer = blocks;
        } else {
            //? if <=1.21.4 {
            // v2
            paletteNbt = schem.getCompound("Palette");
            blockDataBytes = schem.getByteArray("BlockData");
            //?} else {
            /*// v2
            paletteNbt = schem.getCompoundOrEmpty("Palette");
            blockDataBytes = schem.getByteArray("BlockData").orElse(new byte[0]);
            *///?}
            beContainer = schem;
        }

        int maxId = 0;
        Map<String, Integer> paletteMap = new HashMap<>();
        for (String key : paletteNbt.getKeys()) {
            //? if <=1.21.4 {
            int id = paletteNbt.getInt(key);
            //?} else
            /*int id = paletteNbt.getInt(key, 0);*/
            paletteMap.put(key, id);
            if (id > maxId) maxId = id;
        }

        BlockState[] palette = new BlockState[maxId + 1];
        for (Map.Entry<String, Integer> entry : paletteMap.entrySet()) {
            String blockStr = entry.getKey();
            int id = entry.getValue();
            palette[id] = parseBlockState(blockStr);
        }

        java.util.List<SchemBlockEntity> blockEntities = parseBlockEntities(beContainer);

        CrafticsMod.LOGGER.info("Loaded .schem {}: {}x{}x{}, {} palette entries, {} block entities",
            sourceName, schemWidth, schemHeight, schemLength, paletteMap.size(), blockEntities.size());

        return new SchemData(schemWidth, schemHeight, schemLength, palette, blockDataBytes, blockEntities);
    }

    /**
     * Parse the schematic's saved block entities (sign text, chest contents, ...).
     * v3 nests each entry's payload under "Data"; v2 stores it inline next to
     * Pos/Id. Either way the result carries a complete NBT payload with "id" set,
     * positioned by schematic-local coordinates.
     */
    private static java.util.List<SchemBlockEntity> parseBlockEntities(NbtCompound container) {
        java.util.List<SchemBlockEntity> result = new java.util.ArrayList<>();
        //? if <=1.21.4 {
        net.minecraft.nbt.NbtList list = container.getList("BlockEntities", net.minecraft.nbt.NbtElement.COMPOUND_TYPE);
        //?} else
        /*net.minecraft.nbt.NbtList list = container.getListOrEmpty("BlockEntities");*/
        for (net.minecraft.nbt.NbtElement el : list) {
            if (!(el instanceof NbtCompound entry)) continue;
            //? if <=1.21.4 {
            int[] pos = entry.getIntArray("Pos");
            String id = entry.getString("Id");
            //?} else {
            /*int[] pos = entry.getIntArray("Pos").orElse(new int[0]);
            String id = entry.getString("Id", "");
            *///?}
            if (pos.length != 3 || id.isEmpty()) continue;
            NbtCompound nbt;
            if (entry.contains("Data")) {
                //? if <=1.21.4 {
                nbt = entry.getCompound("Data").copy();
                //?} else
                /*nbt = entry.getCompoundOrEmpty("Data").copy();*/
            } else {
                nbt = entry.copy();
                nbt.remove("Pos");
                nbt.remove("Id");
            }
            nbt.putString("id", id);
            result.add(new SchemBlockEntity(pos[0], pos[1], pos[2], nbt));
        }
        return result;
    }

    // Parses "minecraft:oak_stairs[facing=north,half=bottom]" into a BlockState
    private static BlockState parseBlockState(String blockStr) {
        try {
            String plainId = blockStr.contains("[") ? blockStr.substring(0, blockStr.indexOf('[')) : blockStr;
            // Pale Garden Backport: rewrite vanilla 1.21.4 pale-garden block ids
            // to the modded namespace when the backport mod is loaded (1.21.1).
            // The schematic is built against vanilla ids and would otherwise
            // resolve to AIR on older shards.
            String remapped = com.crackedgames.craftics.compat.palegardenbackport
                .PaleGardenBackportCompat.remapBlockId(plainId);
            Block block = Registries.BLOCK.get(Identifier.of(remapped));
            if (block != Blocks.AIR || "minecraft:air".equals(remapped)) {
                if (blockStr.contains("[")) {
                    // Reattach properties to the (possibly remapped) block id
                    String propsTail = blockStr.contains("[") ? blockStr.substring(blockStr.indexOf('[')) : "";
                    return parseWithProperties(block, remapped + propsTail);
                }
                return block.getDefaultState();
            }
        } catch (Exception ignored) {}

        CrafticsMod.LOGGER.debug("Unknown block in schematic: {}", blockStr);
        return Blocks.AIR.getDefaultState();
    }

    private static BlockState parseWithProperties(Block block, String blockStr) {
        try {
            BlockState state = block.getDefaultState();
            String propsStr = blockStr.substring(blockStr.indexOf('[') + 1, blockStr.lastIndexOf(']'));
            String[] props = propsStr.split(",");

            for (String prop : props) {
                String[] kv = prop.split("=", 2);
                if (kv.length != 2) continue;
                String key = kv[0].trim();
                String val = kv[1].trim();

                for (net.minecraft.state.property.Property<?> property : state.getProperties()) {
                    if (property.getName().equals(key)) {
                        state = applyProperty(state, property, val);
                        break;
                    }
                }
            }
            return state;
        } catch (Exception e) {
            return block.getDefaultState();
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends Comparable<T>> BlockState applyProperty(
            BlockState state, net.minecraft.state.property.Property<T> property, String value) {
        var parsed = property.parse(value);
        if (parsed.isPresent()) {
            return state.with(property, parsed.get());
        }
        return state;
    }
}
