package com.crackedgames.craftics.api;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;

/**
 * Immutable definition of an arena environment theme - the floor, border-post, and
 * light blocks an arena is decorated with, plus which flavor-obstacle style it uses.
 *
 * <p>Built-in environments (plains, forest, desert, nether, end, ...) are registered by
 * Craftics at startup. Addons register their own through
 * {@code CrafticsAPI.registerEnvironment} or a {@code craftics/environments/} datapack
 * file; a biome then selects one with {@code "environment": "<id>"}.
 *
 * @param id         unique environment id, e.g. {@code "mymod:aether"}
 * @param floorBlock block used for normal arena floor tiles
 * @param postBlock  block used for arena border light-posts
 * @param lightBlock block placed atop each light-post
 * @param decorStyle flavor-obstacle style - a built-in environment id (e.g.
 *                   {@code "forest"}) reuses that environment's decorations;
 *                   defaults to this environment's own id
 * @since 0.2.0
 */
public record EnvironmentDef(
    String id,
    Block floorBlock,
    Block postBlock,
    Block lightBlock,
    String decorStyle
) {
    public static Builder builder(String id) {
        return new Builder(id);
    }

    /** Fluent builder for {@link EnvironmentDef}. */
    public static class Builder {
        private final String id;
        private Block floorBlock = Blocks.GRASS_BLOCK;
        private Block postBlock = Blocks.OAK_FENCE;
        private Block lightBlock = Blocks.LANTERN;
        private String decorStyle;

        public Builder(String id) {
            this.id = id;
        }

        /** Block used for normal arena floor tiles. Default grass. */
        public Builder floorBlock(Block block) {
            this.floorBlock = block;
            return this;
        }

        /** Block used for arena border light-posts. Default oak fence. */
        public Builder postBlock(Block block) {
            this.postBlock = block;
            return this;
        }

        /** Block placed atop each light-post. Default lantern. */
        public Builder lightBlock(Block block) {
            this.lightBlock = block;
            return this;
        }

        /** Flavor-obstacle style. Defaults to this environment's own id. */
        public Builder decorStyle(String style) {
            this.decorStyle = style;
            return this;
        }

        public EnvironmentDef build() {
            return new EnvironmentDef(id, floorBlock, postBlock, lightBlock,
                decorStyle != null ? decorStyle : id);
        }
    }
}
