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

    public record SchemData(int width, int height, int length, BlockState[] palette, byte[] blockData) {

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
            for (int i = 0; i < palCount; i++) {
                BlockState s = palette[i];
                if (s == null) continue;
                Block block = s.getBlock();
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
            boolean[] keep = new boolean[total];
            int nonAir = 0;
            for (int y = 0; y < height; y++) {
                for (int z = 0; z < length; z++) {
                    for (int x = 0; x < width; x++) {
                        int flat = ((y * length) + z) * width + x;
                        int id = paletteIds[flat];
                        if (id < 0 || id >= palCount || palette[id] == null || palAir[id]) continue;
                        nonAir++;
                        keep[flat] = !cullBuried
                            || palExempt[id] || isExposed(paletteIds, palHides, x, y, z);
                    }
                }
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
            // always enough. Scan top-down so force-kept gravity chains
            // cascade their own supports.
            BlockState[] synthetic = new BlockState[total];
            int supports = 0;
            for (int y = height - 1; y >= 1; y--) {
                for (int z = 0; z < length; z++) {
                    for (int x = 0; x < width; x++) {
                        int flat = ((y * length) + z) * width + x;
                        int id = paletteIds[flat];
                        if (id < 0 || id >= palCount || !palGravity[id] || !keep[flat]) continue;
                        int belowFlat = (((y - 1) * length) + z) * width + x;
                        int belowId = paletteIds[belowFlat];
                        boolean realSupport = belowId >= 0 && belowId < palCount
                            && palette[belowId] != null
                            && (!palFallThrough[belowId] || palExempt[belowId]);
                        if (realSupport) {
                            keep[belowFlat] = true;
                        } else if (synthetic[belowFlat] == null) {
                            synthetic[belowFlat] = supportFor(palette[id]);
                            supports++;
                        }
                    }
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
                        world.setBlockState(pos, palette[id], ArenaBuilder.SET_FLAGS);
                        filled++;
                    }
                }
            }
            if (filled > 0) {
                CrafticsMod.LOGGER.info("Schematic repair: filled {} hollowed-out interior blocks", filled);
            }
            return filled;
        }

        /** True if any face of (x,y,z) borders something that doesn't fully hide it. */
        private boolean isExposed(int[] paletteIds, boolean[] palHides, int x, int y, int z) {
            return faceOpen(paletteIds, palHides, x + 1, y, z)
                || faceOpen(paletteIds, palHides, x - 1, y, z)
                || faceOpen(paletteIds, palHides, x, y, z + 1)
                || faceOpen(paletteIds, palHides, x, y, z - 1)
                || faceOpen(paletteIds, palHides, x, y + 1, z)
                || faceOpen(paletteIds, palHides, x, y - 1, z);
        }

        /**
         * Out of bounds counts as open: sides and sky, plus the underside so
         * floating builds (home island) keep a correct bottom face.
         */
        private boolean faceOpen(int[] paletteIds, boolean[] palHides, int x, int y, int z) {
            if (x < 0 || x >= width || y < 0 || y >= height || z < 0 || z >= length) return true;
            int id = paletteIds[((y * length) + z) * width + x];
            return id < 0 || id >= palHides.length || !palHides[id];
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

        CrafticsMod.LOGGER.info("Loaded .schem {}: {}x{}x{}, {} palette entries",
            sourceName, schemWidth, schemHeight, schemLength, paletteMap.size());

        return new SchemData(schemWidth, schemHeight, schemLength, palette, blockDataBytes);
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
