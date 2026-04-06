package com.crackedgames.craftics.level;

import com.crackedgames.craftics.CrafticsMod;
import net.minecraft.block.Block;
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

/** Loads WorldEdit .schem files (Sponge Schematic v2/v3) and places them in the world */
public class SchemLoader {

    public record SchemData(int width, int height, int length, BlockState[] palette, byte[] blockData) {

        public void place(ServerWorld world, int placeX, int placeY, int placeZ) {
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
                            if (index >= blockData.length) return;
                            b = blockData[index++] & 0xFF;
                            paletteId |= (b & 0x7F) << shift;
                            shift += 7;
                        } while ((b & 0x80) != 0);

                        int flat = ((y * length) + z) * width + x;
                        paletteIds[flat] = paletteId;
                    }
                }
            }

            // Pass 1: non-gravity blocks first (supports must exist before sand/gravel)
            for (int y = 0; y < height; y++) {
                for (int z = 0; z < length; z++) {
                    for (int x = 0; x < width; x++) {
                        int flat = ((y * length) + z) * width + x;
                        int paletteId = paletteIds[flat];
                        if (paletteId >= 0 && paletteId < palette.length) {
                            BlockState state = palette[paletteId];
                            if (state != null && !(state.getBlock() instanceof FallingBlock)) {
                                world.setBlockState(
                                    new BlockPos(placeX + x, placeY + y, placeZ + z),
                                    state, ArenaBuilder.SET_FLAGS
                                );
                            }
                        }
                    }
                }
            }

            // Pass 2: gravity blocks (sand, gravel, etc.)
            for (int y = 0; y < height; y++) {
                for (int z = 0; z < length; z++) {
                    for (int x = 0; x < width; x++) {
                        int flat = ((y * length) + z) * width + x;
                        int paletteId = paletteIds[flat];
                        if (paletteId >= 0 && paletteId < palette.length) {
                            BlockState state = palette[paletteId];
                            if (state != null && (state.getBlock() instanceof FallingBlock)) {
                                world.setBlockState(
                                    new BlockPos(placeX + x, placeY + y, placeZ + z),
                                    state, ArenaBuilder.SET_FLAGS
                                );
                            }
                        }
                    }
                }
            }
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
            schem = root.getCompound("Schematic");
        } else {
            schem = root;
        }

        int schemWidth = schem.getShort("Width") & 0xFFFF;
        int schemHeight = schem.getShort("Height") & 0xFFFF;
        int schemLength = schem.getShort("Length") & 0xFFFF;

        if (schemWidth == 0 || schemHeight == 0 || schemLength == 0) {
            CrafticsMod.LOGGER.warn("Invalid schematic dimensions in {}: {}x{}x{}",
                sourceName, schemWidth, schemHeight, schemLength);
            return null;
        }

        // Palette + block data location differs per version
        NbtCompound paletteNbt;
        byte[] blockDataBytes;

        if (schem.contains("Blocks")) {
            // v3
            NbtCompound blocks = schem.getCompound("Blocks");
            paletteNbt = blocks.getCompound("Palette");
            blockDataBytes = blocks.getByteArray("Data");
        } else {
            // v2
            paletteNbt = schem.getCompound("Palette");
            blockDataBytes = schem.getByteArray("BlockData");
        }

        int maxId = 0;
        Map<String, Integer> paletteMap = new HashMap<>();
        for (String key : paletteNbt.getKeys()) {
            int id = paletteNbt.getInt(key);
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
            Block block = Registries.BLOCK.get(Identifier.of(plainId));
            if (block != Blocks.AIR || "minecraft:air".equals(plainId)) {
                if (blockStr.contains("[")) {
                    return parseWithProperties(block, blockStr);
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
