package com.crackedgames.craftics.client;

import com.crackedgames.craftics.block.LevelSelectScreenHandler;
import com.crackedgames.craftics.level.BiomePath;
import com.crackedgames.craftics.network.StartLevelPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Side-scrolling biome selector. Large biome icon in the center,
 * slivers of adjacent biomes peeking from the edges. Locked biomes
 * show a gray box with "?". Button below the focused biome to enter.
 */
public class LevelSelectScreen extends HandledScreen<LevelSelectScreenHandler> {

    /** All biomes in display order (overworld → nether → end). */
    private List<BiomeEntry> biomes;
    private int selectedIndex = 0;
    private int highestUnlocked;
    private Set<String> discovered;

    /** Smooth horizontal scroll offset (pixels). Positive = scrolled right. */
    private float scrollOffset = 0;
    private float scrollTarget = 0;

    private ButtonWidget enterButton;
    private ButtonWidget leftArrow;
    private ButtonWidget rightArrow;

    // Dimension boundary indices (first biome index that belongs to a new dim)
    private int netherStartIndex = -1;
    private int endStartIndex = -1;

    private record BiomeEntry(String biomeId, String displayName, int dimColor,
                               Identifier texture, int order) {}

    private static final Identifier TEXTURE_BASE = Identifier.of("craftics", "textures/gui/biomes/");

    public LevelSelectScreen(LevelSelectScreenHandler handler,
                              PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundWidth = 280;
        this.backgroundHeight = 240;
    }

    @Override
    protected void init() {
        super.init();

        this.highestUnlocked = handler.getHighestLevelUnlocked();
        int branchChoice = handler.getBranchChoice();
        String discoveredStr = handler.getDiscoveredBiomes();

        List<String> overworldPath = BiomePath.getPath(Math.max(0, branchChoice));
        List<String> netherPath = BiomePath.getNetherPath();
        List<String> endPath = BiomePath.getEndPath();
        boolean netherUnlocked = highestUnlocked > overworldPath.size();
        boolean endUnlocked = netherUnlocked && highestUnlocked > overworldPath.size() + netherPath.size();

        this.discovered = new java.util.HashSet<>();
        if (discoveredStr != null && !discoveredStr.isEmpty()) {
            for (String id : discoveredStr.split(",")) {
                discovered.add(id.trim());
            }
        }
        discovered.add(BiomePath.PLAINS);

        // Build flat biome list across all dimensions
        biomes = new ArrayList<>();
        int order = 1;

        for (String id : overworldPath) {
            BiomePath.NodeInfo info = BiomePath.getNodeInfo(id);
            biomes.add(new BiomeEntry(id, info.displayName(), 0xFF55FF55,
                Identifier.of("craftics", "textures/gui/biomes/" + id + ".png"), order++));
        }

        netherStartIndex = biomes.size();
        if (netherUnlocked) {
            for (String id : netherPath) {
                BiomePath.NodeInfo info = BiomePath.getNetherNodeInfo(id);
                biomes.add(new BiomeEntry(id, info.displayName(), 0xFFFF5555,
                    Identifier.of("craftics", "textures/gui/biomes/" + id + ".png"), order++));
            }
        }

        endStartIndex = biomes.size();
        if (endUnlocked) {
            for (String id : endPath) {
                BiomePath.NodeInfo info = BiomePath.getEndNodeInfo(id);
                biomes.add(new BiomeEntry(id, info.displayName(), 0xFFAA55CC,
                    Identifier.of("craftics", "textures/gui/biomes/" + id + ".png"), order++));
            }
        }

        // Start on the highest unlocked biome (the biome the player should tackle next)
        selectedIndex = Math.min(highestUnlocked - 1, biomes.size() - 1);
        selectedIndex = Math.max(0, selectedIndex);
        scrollOffset = scrollTarget = selectedIndex;

        rebuildWidgets();
    }

    private void rebuildWidgets() {
        this.clearChildren();
        int cx = this.width / 2;
        int imgSize = getImageSize();
        int buttonY = this.height / 2 + imgSize / 2 + 24;

        BiomeEntry focused = biomes.get(selectedIndex);
        boolean unlocked = focused.order <= highestUnlocked;

        // Enter button
        String btnLabel = unlocked ? "\u00a7l\u2794 Enter " + focused.displayName : "\u00a78\u2716 Locked";
        enterButton = ButtonWidget.builder(Text.literal(btnLabel), button -> {
            if (focused.order <= highestUnlocked) {
                this.close();
                TransitionOverlay.startTransition(
                    focused.displayName, "Entering the arena...",
                    () -> ClientPlayNetworking.send(new StartLevelPayload(focused.biomeId))
                );
            }
        }).dimensions(cx - 80, buttonY, 160, 20).build();
        enterButton.active = unlocked;
        this.addDrawableChild(enterButton);

        // Left / Right arrows
        leftArrow = ButtonWidget.builder(Text.literal("\u25C0"), button -> scroll(-1))
            .dimensions(cx - imgSize / 2 - 28, this.height / 2 - 10, 20, 20).build();
        leftArrow.active = selectedIndex > 0;
        this.addDrawableChild(leftArrow);

        rightArrow = ButtonWidget.builder(Text.literal("\u25B6"), button -> scroll(1))
            .dimensions(cx + imgSize / 2 + 8, this.height / 2 - 10, 20, 20).build();
        rightArrow.active = selectedIndex < biomes.size() - 1;
        this.addDrawableChild(rightArrow);
    }

    private void scroll(int dir) {
        selectedIndex = Math.max(0, Math.min(selectedIndex + dir, biomes.size() - 1));
        scrollTarget = selectedIndex;
        rebuildWidgets();
    }

    private int getImageSize() {
        // Scale image to fit nicely — roughly 60% of the smaller screen dimension
        return Math.min((int)(this.height * 0.52), (int)(this.width * 0.38));
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        // Dark gradient background
        context.fillGradient(0, 0, this.width, this.height, 0xE0080810, 0xF0080810);
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        // Suppress default HandledScreen title/inventory labels
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Animate scroll
        float scrollSpeed = 6.0f * delta;
        if (Math.abs(scrollTarget - scrollOffset) < 0.01f) {
            scrollOffset = scrollTarget;
        } else {
            scrollOffset += (scrollTarget - scrollOffset) * Math.min(1.0f, scrollSpeed);
        }

        super.render(context, mouseX, mouseY, delta);

        int cx = this.width / 2;
        int cy = this.height / 2 - 10;
        int imgSize = getImageSize();
        int spacing = imgSize * 3 / 2;

        // NG+ title
        List<String> overworldPath = BiomePath.getPath(Math.max(0, handler.getBranchChoice()));
        List<String> netherPath = BiomePath.getNetherPath();
        List<String> endPath = BiomePath.getEndPath();
        int totalBiomes = overworldPath.size() + netherPath.size() + endPath.size();
        int ngPlus = Math.max(0, (highestUnlocked - 1) / totalBiomes);
        String title = ngPlus > 0
            ? "\u00a7l\u00a76\u2605 CRAFTICS \u2605 \u00a7c(NG+" + ngPlus + ")"
            : "\u00a7l\u00a76\u2605 CRAFTICS \u2605";
        context.drawCenteredTextWithShadow(this.textRenderer, title, cx, 8, 0xFFAA00);
        context.drawCenteredTextWithShadow(this.textRenderer,
            "\u00a77Level Select", cx, 22, 0x888888);

        // Render biome cards (visible range around scroll position)
        int visibleSlots = (this.width / spacing) + 3;
        int minI = Math.max(0, (int) scrollOffset - visibleSlots / 2);
        int maxI = Math.min(biomes.size() - 1, (int) scrollOffset + visibleSlots / 2);

        for (int i = minI; i <= maxI; i++) {
            float relPos = i - scrollOffset; // -N..0..+N, 0 = center
            int cardCenterX = cx + (int)(relPos * spacing);

            // Skip if off-screen
            if (cardCenterX + imgSize / 2 < -20 || cardCenterX - imgSize / 2 > this.width + 20) continue;

            BiomeEntry entry = biomes.get(i);
            boolean unlocked = entry.order <= highestUnlocked;
            boolean isFocused = i == selectedIndex;
            boolean isDiscovered = discovered.contains(entry.biomeId);

            int imgX = cardCenterX - imgSize / 2;
            int imgY = cy - imgSize / 2;

            if (unlocked) {
                // Draw biome icon
                drawBiomeIcon(context, entry.texture, imgX, imgY, imgSize, imgSize);

                // Darkened overlay for non-focused cards
                if (!isFocused) {
                    context.fill(imgX, imgY, imgX + imgSize, imgY + imgSize, 0xA0000000);
                }
            } else {
                // Locked: dark gray box
                context.fill(imgX, imgY, imgX + imgSize, imgY + imgSize, 0xFF1A1A2A);
                context.fill(imgX + 1, imgY + 1, imgX + imgSize - 1, imgY + imgSize - 1, 0xFF222233);

                // "?" in center
                String q = "?";
                int qw = this.textRenderer.getWidth(q);
                context.drawTextWithShadow(this.textRenderer, q,
                    cardCenterX - qw / 2, cy - 4, 0xFF555566);
            }

            // Border around focused card
            if (isFocused) {
                int borderColor = unlocked ? entry.dimColor : 0xFF444466;
                // Top, bottom, left, right borders (2px thick)
                context.fill(imgX - 2, imgY - 2, imgX + imgSize + 2, imgY, borderColor);
                context.fill(imgX - 2, imgY + imgSize, imgX + imgSize + 2, imgY + imgSize + 2, borderColor);
                context.fill(imgX - 2, imgY, imgX, imgY + imgSize, borderColor);
                context.fill(imgX + imgSize, imgY, imgX + imgSize + 2, imgY + imgSize, borderColor);
            }

            // Biome name below image
            if (isFocused && (unlocked || isDiscovered)) {
                String name = entry.displayName;
                int nameW = this.textRenderer.getWidth(name);
                context.drawTextWithShadow(this.textRenderer, name,
                    cardCenterX - nameW / 2, imgY + imgSize + 6, 0xFFFFFFFF);
            }

            // Dimension label above card when crossing dimension boundary
            if (isFocused) {
                String dimLabel = null;
                int dimLabelColor = 0xFFFFFF;
                if (i >= endStartIndex && endStartIndex < biomes.size()) {
                    dimLabel = "\u00a75\u00a7l\u2726 THE END \u2726";
                    dimLabelColor = 0xAA55CC;
                } else if (i >= netherStartIndex && netherStartIndex < biomes.size()) {
                    dimLabel = "\u00a7c\u00a7l\u2620 THE NETHER \u2620";
                    dimLabelColor = 0xFF5555;
                } else {
                    dimLabel = "\u00a7a\u00a7l\u2600 OVERWORLD \u2600";
                    dimLabelColor = 0x55FF55;
                }
                context.drawCenteredTextWithShadow(this.textRenderer, dimLabel, cx, imgY - 14, dimLabelColor);
            }
        }

        // Footer hint
        context.drawCenteredTextWithShadow(this.textRenderer,
            "\u00a78Scroll or use arrows to browse biomes",
            cx, this.height - 14, 0x555555);

        this.drawMouseoverTooltip(context, mouseX, mouseY);
    }

    private void drawBiomeIcon(DrawContext context, Identifier texture, int x, int y, int w, int h) {
        context.drawTexture(texture, x, y, 0f, 0f, w, h, w, h);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (verticalAmount > 0 && selectedIndex > 0) {
            scroll(-1);
        } else if (verticalAmount < 0 && selectedIndex < biomes.size() - 1) {
            scroll(1);
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Left arrow = 263, Right arrow = 262
        if (keyCode == 263 && selectedIndex > 0) {
            scroll(-1);
            return true;
        } else if (keyCode == 262 && selectedIndex < biomes.size() - 1) {
            scroll(1);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
