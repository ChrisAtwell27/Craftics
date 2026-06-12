package com.crackedgames.craftics.block;

import net.minecraft.block.Block;

/**
 * Corner marker for a scene booth ("stand"). Placed in PAIRS: two stand markers
 * mark the opposite corners of a rectangular booth region, and clicking anywhere
 * in that rectangle counts as clicking the booth. A booth is identified by the
 * {@code npc_marker} that sits inside the rectangle (which also carries the
 * occupant); the corner markers themselves hold no data.
 *
 * <p>{@link com.crackedgames.craftics.scene.SceneScanner} records the corner
 * positions, then replaces the marker with the most common neighboring block so
 * it is invisible in the built scene. Pure marker, no behavior, no block entity.
 */
public class StandMarkerBlock extends Block {

    public StandMarkerBlock(Settings settings) {
        super(settings);
    }
}
