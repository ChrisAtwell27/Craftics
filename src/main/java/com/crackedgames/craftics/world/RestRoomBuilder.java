package com.crackedgames.craftics.world;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * INFINITE MODE rest room: the small crafting/smelting/smithing chamber the
 * party lands in after each boss. Rebuilt from scratch on every visit so the
 * furnace fuel (exactly 1 coal), leftover items, and any player edits reset
 * between biomes. Fixed footprint in the island's room column (see
 * {@code CrafticsSavedData.getRestRoomOrigin}).
 *
 * <p>Layout (origin = north-west floor corner, interior 9x7):
 * furnace + crafting table + smithing table + anvil along the north wall, the
 * continue-bell on a pedestal at the east end, a score sign beside it, lantern
 * lighting, and the spawn point in the room's center.
 */
public final class RestRoomBuilder {
    private RestRoomBuilder() {}

    public static final int WIDTH = 11;   // X span (walls included)
    public static final int DEPTH = 9;    // Z span (walls included)
    public static final int HEIGHT = 5;   // floor to ceiling (inclusive walls)

    /** Interior spawn point, relative to origin. */
    public static BlockPos spawnPos(BlockPos origin) {
        return origin.add(WIDTH / 2, 1, DEPTH / 2);
    }

    /** Where the continue-bell sits (on its pedestal), relative to origin. */
    public static BlockPos bellPos(BlockPos origin) {
        return origin.add(WIDTH - 3, 2, DEPTH / 2);
    }

    /**
     * Build (or rebuild) the rest room. Always safe to call again - every block
     * in the footprint is rewritten, so the room comes back pristine.
     *
     * @param score      current run score, shown on the wall sign
     * @param bestScore  the host's personal best, shown under it
     */
    public static void build(ServerWorld world, BlockPos origin, int score, int bestScore) {
        BlockState floor = Blocks.POLISHED_ANDESITE.getDefaultState();
        BlockState wall = Blocks.STONE_BRICKS.getDefaultState();
        BlockState accent = Blocks.CHISELED_STONE_BRICKS.getDefaultState();
        BlockState ceiling = Blocks.DARK_OAK_PLANKS.getDefaultState();
        BlockState air = Blocks.AIR.getDefaultState();

        for (int x = 0; x < WIDTH; x++) {
            for (int z = 0; z < DEPTH; z++) {
                for (int y = 0; y <= HEIGHT; y++) {
                    BlockPos pos = origin.add(x, y, z);
                    boolean edge = x == 0 || z == 0 || x == WIDTH - 1 || z == DEPTH - 1;
                    if (y == 0) {
                        world.setBlockState(pos, edge ? wall : floor);
                    } else if (y == HEIGHT) {
                        world.setBlockState(pos, ceiling);
                    } else if (edge) {
                        boolean corner = (x == 0 || x == WIDTH - 1) && (z == 0 || z == DEPTH - 1);
                        world.setBlockState(pos, corner ? accent : wall);
                    } else {
                        world.setBlockState(pos, air);
                    }
                }
            }
        }

        // ── Workstations along the north wall (z = 1), facing into the room ──
        int stationZ = 1;
        int baseX = 2;
        // Furnace, pre-loaded with exactly 1 coal in the fuel slot.
        BlockPos furnacePos = origin.add(baseX, 1, stationZ);
        world.setBlockState(furnacePos, Blocks.FURNACE.getDefaultState()
            .with(Properties.HORIZONTAL_FACING, Direction.SOUTH));
        if (world.getBlockEntity(furnacePos) instanceof AbstractFurnaceBlockEntity furnace) {
            furnace.setStack(1, new ItemStack(Items.COAL, 1)); // slot 1 = fuel
            furnace.markDirty();
        }
        world.setBlockState(origin.add(baseX + 2, 1, stationZ), Blocks.CRAFTING_TABLE.getDefaultState());
        world.setBlockState(origin.add(baseX + 4, 1, stationZ), Blocks.SMITHING_TABLE.getDefaultState());
        world.setBlockState(origin.add(baseX + 6, 1, stationZ),
            Blocks.ANVIL.getDefaultState().with(Properties.HORIZONTAL_AXIS, Direction.Axis.X));

        // ── Continue bell on a pedestal at the east end ──
        BlockPos bell = bellPos(origin);
        world.setBlockState(bell.down(), accent);
        world.setBlockState(bell, Blocks.BELL.getDefaultState()
            .with(Properties.ATTACHMENT, net.minecraft.block.enums.Attachment.FLOOR)
            .with(Properties.HORIZONTAL_FACING, Direction.WEST));

        // ── Score sign next to the bell ──
        placeSign(world, bell.add(0, 0, -2), new String[]{
            "§5§l∞ INFINITE ∞",
            "§0Score: §5" + score,
            "§0Best: §5" + bestScore,
            "§0Ring to go on"
        });
        placeSign(world, bell.add(0, 0, 2), new String[]{
            "§0Rest up, then",
            "§0ring the bell.",
            "§0§o/home banks",
            "§0§oyour run."
        });

        // ── Lighting ──
        BlockState lantern = Blocks.LANTERN.getDefaultState().with(Properties.HANGING, true);
        world.setBlockState(origin.add(2, HEIGHT - 1, DEPTH / 2), lantern);
        world.setBlockState(origin.add(WIDTH / 2, HEIGHT - 1, DEPTH / 2), lantern);
        world.setBlockState(origin.add(WIDTH - 3, HEIGHT - 1, DEPTH / 2), lantern);
    }

    private static void placeSign(ServerWorld world, BlockPos pos, String[] lines) {
        BlockState signState = Blocks.OAK_SIGN.getDefaultState()
            .with(net.minecraft.block.SignBlock.ROTATION, 12); // facing west, toward the room
        world.setBlockState(pos, signState);
        var blockEntity = world.getBlockEntity(pos);
        if (blockEntity instanceof net.minecraft.block.entity.SignBlockEntity sign) {
            for (int i = 0; i < Math.min(lines.length, 4); i++) {
                sign.setText(sign.getText(true).withMessage(i,
                    net.minecraft.text.Text.literal(lines[i])), true);
            }
            sign.setWaxed(true);
            sign.markDirty();
        }
    }
}
