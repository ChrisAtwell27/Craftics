package com.crackedgames.craftics.block;

import net.minecraft.block.Block;

/**
 * Marker block used by {@code ArenaBuilder} to define a non-rectangular arena
 * boundary. Place three or more of these around a polygon outline (in any
 * order - the scanner sorts them into a convex/concave hull at load time) and
 * the arena will use that polygon as its in-bounds mask instead of the simple
 * rectangle the DIAMOND/EMERALD pair produces.
 *
 * <p>The block has no special behavior - it's a pure marker. Distinctive
 * appearance just makes it easy to spot in a schematic editor or in-world.
 *
 * <p>Backwards compatible: schematics that only contain the legacy
 * DIAMOND/EMERALD corner pair keep producing a rectangular arena unchanged.
 */
public class ArenaCornerBlock extends Block {
    public ArenaCornerBlock(Settings settings) {
        super(settings);
    }
}
