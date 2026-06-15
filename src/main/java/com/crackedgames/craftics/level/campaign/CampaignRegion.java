package com.crackedgames.craftics.level.campaign;

import java.util.ArrayList;
import java.util.List;

/**
 * Immutable themed "realm" of a {@link Campaign} - an ordered list of {@link CampaignNode}s
 * plus presentation metadata (display name, chat color, glyph icon, map color).
 *
 * <p>Build entries with {@link #builder(String)}:
 *
 * <pre>{@code
 * CampaignRegion.builder("surface")
 *     .displayName("Surface")
 *     .color("§a").icon("⛰").mapColor(0xFF88CC44)
 *     .node("village")
 *     .node("wildwood", "The Wildwood")
 *     .build();
 * }</pre>
 *
 * <p>Validation lives in the canonical constructor and throws IllegalArgumentException.
 *
 * @param id          unique region id within its campaign, e.g. {@code "surface"}
 * @param displayName name shown to players; defaults to the id when {@code null}
 * @param color       Minecraft chat color code string, e.g. {@code "§a"}
 * @param icon        unicode glyph string shown for this region
 * @param mapColor    ARGB int used to tint this region on the campaign map
 * @param nodes       ordered nodes that make up this region (defensively copied)
 * @since 0.2.2
 */
public record CampaignRegion(
    String id,
    String displayName,
    String color,
    String icon,
    int mapColor,
    List<CampaignNode> nodes
) {
    public CampaignRegion {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("CampaignRegion requires a non-blank id");
        }
        // The JSON loader may pass null via this canonical constructor; default to id.
        if (displayName == null) {
            displayName = id;
        }
        nodes = List.copyOf(nodes);
    }

    public static Builder builder(String id) {
        return new Builder(id);
    }

    /** Fluent builder for {@link CampaignRegion}. */
    public static class Builder {
        private final String id;
        private String displayName;
        private String color = "§f";
        private String icon = "?";
        private int mapColor = 0xFFFFFFFF;
        private final List<CampaignNode> nodes = new ArrayList<>();

        public Builder(String id) {
            this.id = id;
            this.displayName = id;
        }

        /** Name shown to players. Defaults to the id. */
        public Builder displayName(String displayName) {
            this.displayName = displayName;
            return this;
        }

        /** Minecraft chat color code, e.g. {@code "§a"}. Defaults to {@code "§f"}. */
        public Builder color(String color) {
            this.color = color;
            return this;
        }

        /** Unicode glyph shown for this region. Defaults to {@code "?"}. */
        public Builder icon(String icon) {
            this.icon = icon;
            return this;
        }

        /** ARGB map tint. Defaults to opaque white {@code 0xFFFFFFFF}. */
        public Builder mapColor(int mapColor) {
            this.mapColor = mapColor;
            return this;
        }

        /** Append a fully built node. */
        public Builder node(CampaignNode node) {
            this.nodes.add(node);
            return this;
        }

        /** Append a node referencing {@code biomeId} with no label override. */
        public Builder node(String biomeId) {
            this.nodes.add(CampaignNode.of(biomeId));
            return this;
        }

        /** Append a node referencing {@code biomeId} with an explicit map label. */
        public Builder node(String biomeId, String label) {
            this.nodes.add(CampaignNode.of(biomeId, label));
            return this;
        }

        /** Build the region; the canonical constructor validates (IllegalArgumentException). */
        public CampaignRegion build() {
            return new CampaignRegion(id, displayName, color, icon, mapColor, nodes);
        }
    }
}
