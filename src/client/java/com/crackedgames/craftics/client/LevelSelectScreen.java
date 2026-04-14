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
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Paged biome selector. The player browses one <em>dimension</em> at a time —
 * Overworld / Nether / End by default, plus any pages contributed by addons
 * via {@link #registerExtraPage(DimensionPage)}.
 * <p>
 * Within a page, biome cards side-scroll horizontally (the legacy layout).
 * A tab bar at the top lets the player jump between pages with the mouse or
 * via the Q / E hotkeys.
 * <p>
 * <b>Unlock model:</b> the handler still hands the client a single global
 * {@code highestUnlocked} number — biomes are indexed into that via their
 * {@code order} field, which reflects position in the full path. A whole
 * page is considered "unlocked" (tab clickable) as soon as any biome on it
 * is unlocked; otherwise the tab renders with a padlock and can't be opened.
 */
public class LevelSelectScreen extends HandledScreen<LevelSelectScreenHandler> {

    // ─────────────────────────────────────────────────────────────────────
    // Addon extension API
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Extra dimension pages contributed by addons. Populated from client
     * init (e.g. a custom ClientModInitializer) before the screen is opened.
     * Pages are appended to the end of the built-in tab list in registration
     * order. Each page must have a unique id.
     */
    private static final List<DimensionPage> EXTRA_PAGES = new ArrayList<>();

    /**
     * Register an extra dimension tab. Safe to call from client mod init.
     * Replaces any previously-registered page with the same {@code id}.
     */
    public static void registerExtraPage(DimensionPage page) {
        if (page == null || page.id == null || page.id.isEmpty()) return;
        EXTRA_PAGES.removeIf(p -> p.id.equals(page.id));
        EXTRA_PAGES.add(page);
    }

    /**
     * A dimension page shown as a tab in the level select UI. Addons build
     * one via {@link #builder(String, String)} and hand it to
     * {@link #registerExtraPage(DimensionPage)}.
     */
    public static final class DimensionPage {
        public final String id;
        public final String displayName;
        public final String tabLabel;      // short label shown on the tab (e.g. "OVERWORLD")
        public final int dimColor;          // ARGB tint for tab + biome card border
        public final List<BiomeEntry> biomes;

        public DimensionPage(String id, String displayName, String tabLabel,
                             int dimColor, List<BiomeEntry> biomes) {
            this.id = id;
            this.displayName = displayName;
            this.tabLabel = tabLabel;
            this.dimColor = dimColor;
            this.biomes = biomes != null ? List.copyOf(biomes) : List.of();
        }

        public static Builder builder(String id, String displayName) {
            return new Builder(id, displayName);
        }

        public static final class Builder {
            private final String id;
            private final String displayName;
            private String tabLabel;
            private int dimColor = 0xFFFFFFFF;
            private final List<BiomeEntry> biomes = new ArrayList<>();

            private Builder(String id, String displayName) {
                this.id = id;
                this.displayName = displayName;
                this.tabLabel = displayName.toUpperCase();
            }

            public Builder tabLabel(String label) { this.tabLabel = label; return this; }
            public Builder color(int argb) { this.dimColor = argb; return this; }

            public Builder addBiome(String biomeId, String displayName, int order) {
                Identifier tex = Identifier.of("craftics", "textures/gui/biomes/" + biomeId + ".png");
                biomes.add(new BiomeEntry(biomeId, displayName, dimColor, tex, order));
                return this;
            }

            public Builder addBiome(BiomeEntry entry) { biomes.add(entry); return this; }

            public DimensionPage build() {
                return new DimensionPage(id, displayName, tabLabel, dimColor, biomes);
            }
        }
    }

    public record BiomeEntry(String biomeId, String displayName, int dimColor,
                              Identifier texture, int order) {}

    // ─────────────────────────────────────────────────────────────────────
    // Instance state
    // ─────────────────────────────────────────────────────────────────────

    /** All pages, in tab order. Always non-empty — at minimum contains Overworld. */
    private List<DimensionPage> pages;

    /** Index into {@link #pages}. */
    private int currentPageIndex = 0;

    /** Index into {@code pages.get(currentPageIndex).biomes}. Reset when switching tabs. */
    private int selectedIndex = 0;

    /** Global unlock threshold as supplied by the server. */
    private int highestUnlocked;

    /** Set of biome IDs the player has visited. */
    private Set<String> discovered;

    /** Smooth horizontal scroll offset (biome indices, not pixels). */
    private float scrollOffset = 0;
    private float scrollTarget = 0;

    private ButtonWidget enterButton;
    private ButtonWidget leftArrow;
    private ButtonWidget rightArrow;

    /** Tab hit-boxes (rebuilt each frame). Index matches {@link #pages}. */
    private final List<int[]> tabBounds = new ArrayList<>();

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

        this.discovered = new java.util.HashSet<>();
        if (discoveredStr != null && !discoveredStr.isEmpty()) {
            for (String id : discoveredStr.split(",")) {
                discovered.add(id.trim());
            }
        }
        discovered.add(BiomePath.PLAINS);

        this.pages = buildPages(branchChoice);

        // Land on the page that contains the highest unlocked biome so the
        // player's next step is visible without any clicks.
        currentPageIndex = pickInitialPage();
        selectedIndex = pickInitialBiomeInPage(pages.get(currentPageIndex));
        scrollOffset = scrollTarget = selectedIndex;

        rebuildWidgets();
    }

    /**
     * Assemble the tab list. Built-in pages (Overworld / Nether / End) are
     * always present — we render them as locked tabs when the player hasn't
     * unlocked them yet, so the UI shape is stable across a run. Addon pages
     * are appended.
     */
    private List<DimensionPage> buildPages(int branchChoice) {
        List<DimensionPage> out = new ArrayList<>();

        int order = 1;

        // Overworld — always unlocked (player starts here).
        List<BiomeEntry> overworld = new ArrayList<>();
        for (String id : BiomePath.getPath(Math.max(0, branchChoice))) {
            BiomePath.NodeInfo info = BiomePath.getNodeInfo(id);
            overworld.add(new BiomeEntry(id, info.displayName(), 0xFF55FF55,
                Identifier.of("craftics", "textures/gui/biomes/" + id + ".png"), order++));
        }
        out.add(new DimensionPage("overworld", "Overworld",
            "\u2600 OVERWORLD", 0xFF55FF55, overworld));

        // Nether
        List<BiomeEntry> nether = new ArrayList<>();
        for (String id : BiomePath.getNetherPath()) {
            BiomePath.NodeInfo info = BiomePath.getNetherNodeInfo(id);
            nether.add(new BiomeEntry(id, info.displayName(), 0xFFFF5555,
                Identifier.of("craftics", "textures/gui/biomes/" + id + ".png"), order++));
        }
        out.add(new DimensionPage("nether", "The Nether",
            "\u2620 NETHER", 0xFFFF5555, nether));

        // End
        List<BiomeEntry> end = new ArrayList<>();
        for (String id : BiomePath.getEndPath()) {
            BiomePath.NodeInfo info = BiomePath.getEndNodeInfo(id);
            end.add(new BiomeEntry(id, info.displayName(), 0xFFAA55CC,
                Identifier.of("craftics", "textures/gui/biomes/" + id + ".png"), order++));
        }
        out.add(new DimensionPage("end", "The End",
            "\u2726 THE END", 0xFFAA55CC, end));

        // Addon-contributed pages.
        out.addAll(EXTRA_PAGES);

        return Collections.unmodifiableList(out);
    }

    /** True if at least one biome in this page is unlocked. */
    private boolean isPageUnlocked(DimensionPage page) {
        for (BiomeEntry entry : page.biomes) {
            if (entry.order <= highestUnlocked) return true;
        }
        return false;
    }

    /** First page that contains the "next biome to tackle", else 0. */
    private int pickInitialPage() {
        for (int i = 0; i < pages.size(); i++) {
            DimensionPage page = pages.get(i);
            for (BiomeEntry entry : page.biomes) {
                if (entry.order == highestUnlocked) return i;
            }
        }
        // Fallback: last page that's unlocked.
        int lastUnlocked = 0;
        for (int i = 0; i < pages.size(); i++) {
            if (isPageUnlocked(pages.get(i))) lastUnlocked = i;
        }
        return lastUnlocked;
    }

    /** Within a page, pick the biome matching highestUnlocked, else the last unlocked one, else 0. */
    private int pickInitialBiomeInPage(DimensionPage page) {
        if (page.biomes.isEmpty()) return 0;
        for (int i = 0; i < page.biomes.size(); i++) {
            if (page.biomes.get(i).order == highestUnlocked) return i;
        }
        int lastUnlocked = 0;
        for (int i = 0; i < page.biomes.size(); i++) {
            if (page.biomes.get(i).order <= highestUnlocked) lastUnlocked = i;
        }
        return lastUnlocked;
    }

    private DimensionPage currentPage() {
        return pages.get(currentPageIndex);
    }

    private List<BiomeEntry> currentBiomes() {
        return currentPage().biomes;
    }

    // ─────────────────────────────────────────────────────────────────────
    // Widgets (Enter / arrows)
    // ─────────────────────────────────────────────────────────────────────

    private void rebuildWidgets() {
        this.clearChildren();
        int cx = this.width / 2;
        int imgSize = getImageSize();
        int buttonY = this.height / 2 + imgSize / 2 + 24;

        List<BiomeEntry> biomes = currentBiomes();
        if (biomes.isEmpty()) {
            // Shouldn't happen (Overworld always has content), but handle gracefully.
            return;
        }
        selectedIndex = Math.max(0, Math.min(selectedIndex, biomes.size() - 1));
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

        // Left / Right biome arrows
        leftArrow = ButtonWidget.builder(Text.literal("\u25C0"), button -> scrollBiome(-1))
            .dimensions(cx - imgSize / 2 - 28, this.height / 2 - 10, 20, 20).build();
        leftArrow.active = selectedIndex > 0;
        this.addDrawableChild(leftArrow);

        rightArrow = ButtonWidget.builder(Text.literal("\u25B6"), button -> scrollBiome(1))
            .dimensions(cx + imgSize / 2 + 8, this.height / 2 - 10, 20, 20).build();
        rightArrow.active = selectedIndex < biomes.size() - 1;
        this.addDrawableChild(rightArrow);
    }

    private void scrollBiome(int dir) {
        List<BiomeEntry> biomes = currentBiomes();
        if (biomes.isEmpty()) return;
        selectedIndex = Math.max(0, Math.min(selectedIndex + dir, biomes.size() - 1));
        scrollTarget = selectedIndex;
        rebuildWidgets();
    }

    /** Switch to a different dimension tab. No-op if the page is locked. */
    private void switchToPage(int newIndex) {
        if (newIndex < 0 || newIndex >= pages.size()) return;
        if (newIndex == currentPageIndex) return;
        if (!isPageUnlocked(pages.get(newIndex))) return;
        currentPageIndex = newIndex;
        selectedIndex = pickInitialBiomeInPage(pages.get(newIndex));
        scrollOffset = scrollTarget = selectedIndex;
        rebuildWidgets();
    }

    /** Cycle to next/previous unlocked page. */
    private void cyclePage(int dir) {
        if (pages.size() <= 1) return;
        int n = pages.size();
        for (int step = 1; step <= n; step++) {
            int idx = ((currentPageIndex + dir * step) % n + n) % n;
            if (isPageUnlocked(pages.get(idx))) {
                switchToPage(idx);
                return;
            }
        }
    }

    private int getImageSize() {
        return Math.min((int)(this.height * 0.52), (int)(this.width * 0.38));
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        context.fillGradient(0, 0, this.width, this.height, 0xE0080810, 0xF0080810);
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        // Suppress default HandledScreen title/inventory labels
    }

    // ─────────────────────────────────────────────────────────────────────
    // Rendering
    // ─────────────────────────────────────────────────────────────────────

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Smooth scroll animation
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
        int ngPlus = totalBiomes > 0 ? Math.max(0, (highestUnlocked - 1) / totalBiomes) : 0;
        String title = ngPlus > 0
            ? "\u00a7l\u00a76\u2605 CRAFTICS \u2605 \u00a7c(NG+" + ngPlus + ")"
            : "\u00a7l\u00a76\u2605 CRAFTICS \u2605";
        context.drawCenteredTextWithShadow(this.textRenderer, title, cx, 6, 0xFFAA00);

        // Tab bar
        int tabY = 20;
        int tabH = 16;
        renderTabBar(context, tabY, tabH, mouseX, mouseY);

        // Render biome cards for current page (visible range around scroll position)
        List<BiomeEntry> biomes = currentBiomes();
        if (biomes.isEmpty()) return;

        int visibleSlots = (this.width / spacing) + 3;
        int minI = Math.max(0, (int) scrollOffset - visibleSlots / 2);
        int maxI = Math.min(biomes.size() - 1, (int) scrollOffset + visibleSlots / 2);

        for (int i = minI; i <= maxI; i++) {
            float relPos = i - scrollOffset;
            int cardCenterX = cx + (int)(relPos * spacing);

            if (cardCenterX + imgSize / 2 < -20 || cardCenterX - imgSize / 2 > this.width + 20) continue;

            BiomeEntry entry = biomes.get(i);
            boolean unlocked = entry.order <= highestUnlocked;
            boolean isFocused = i == selectedIndex;
            boolean isDiscovered = discovered.contains(entry.biomeId);

            int imgX = cardCenterX - imgSize / 2;
            int imgY = cy - imgSize / 2;

            if (unlocked) {
                drawBiomeIcon(context, entry.texture, imgX, imgY, imgSize, imgSize);
                if (!isFocused) {
                    context.fill(imgX, imgY, imgX + imgSize, imgY + imgSize, 0xA0000000);
                }
            } else {
                context.fill(imgX, imgY, imgX + imgSize, imgY + imgSize, 0xFF1A1A2A);
                context.fill(imgX + 1, imgY + 1, imgX + imgSize - 1, imgY + imgSize - 1, 0xFF222233);

                String q = "?";
                int qw = this.textRenderer.getWidth(q);
                context.drawTextWithShadow(this.textRenderer, q,
                    cardCenterX - qw / 2, cy - 4, 0xFF555566);
            }

            if (isFocused) {
                int borderColor = unlocked ? entry.dimColor : 0xFF444466;
                context.fill(imgX - 2, imgY - 2, imgX + imgSize + 2, imgY, borderColor);
                context.fill(imgX - 2, imgY + imgSize, imgX + imgSize + 2, imgY + imgSize + 2, borderColor);
                context.fill(imgX - 2, imgY, imgX, imgY + imgSize, borderColor);
                context.fill(imgX + imgSize, imgY, imgX + imgSize + 2, imgY + imgSize, borderColor);
            }

            if (isFocused && (unlocked || isDiscovered)) {
                String name = entry.displayName;
                int nameW = this.textRenderer.getWidth(name);
                context.drawTextWithShadow(this.textRenderer, name,
                    cardCenterX - nameW / 2, imgY + imgSize + 6, 0xFFFFFFFF);
            }
        }

        // Footer hint
        context.drawCenteredTextWithShadow(this.textRenderer,
            "\u00a78Q / E: switch dimension  \u00b7  \u2190 \u2192: browse biomes",
            cx, this.height - 14, 0x555555);

        this.drawMouseoverTooltip(context, mouseX, mouseY);
    }

    /**
     * Draw the dimension tab bar. Each tab is a flat rectangle with the
     * dimension's label centered inside. The active tab sits slightly lower
     * with a bright outline; locked tabs render muted with a padlock glyph.
     */
    private void renderTabBar(DrawContext context, int tabY, int tabH, int mouseX, int mouseY) {
        tabBounds.clear();

        // Measure each tab's width individually so labels of different lengths
        // don't get truncated. Minimum 64 so single-word labels still feel
        // substantial.
        int[] widths = new int[pages.size()];
        int totalW = 0;
        int gap = 4;
        for (int i = 0; i < pages.size(); i++) {
            int lw = this.textRenderer.getWidth(pages.get(i).tabLabel);
            widths[i] = Math.max(64, lw + 16);
            totalW += widths[i];
        }
        totalW += gap * Math.max(0, pages.size() - 1);

        int startX = (this.width - totalW) / 2;
        int x = startX;

        for (int i = 0; i < pages.size(); i++) {
            DimensionPage page = pages.get(i);
            int w = widths[i];
            boolean unlocked = isPageUnlocked(page);
            boolean active = i == currentPageIndex;
            boolean hovered = mouseX >= x && mouseX < x + w && mouseY >= tabY && mouseY < tabY + tabH;

            // Background
            int bgColor;
            if (active) {
                bgColor = 0xCC101828;
            } else if (unlocked && hovered) {
                bgColor = 0xAA202838;
            } else if (unlocked) {
                bgColor = 0x88101828;
            } else {
                bgColor = 0x88050510;
            }
            context.fill(x, tabY, x + w, tabY + tabH, bgColor);

            // Top accent bar in dimension color
            int accent = unlocked ? page.dimColor : 0xFF444466;
            context.fill(x, tabY, x + w, tabY + 2, accent);
            if (active) {
                // Bottom border on active tab
                context.fill(x, tabY + tabH - 1, x + w, tabY + tabH, accent);
                // Side borders
                context.fill(x, tabY, x + 1, tabY + tabH, accent);
                context.fill(x + w - 1, tabY, x + w, tabY + tabH, accent);
            }

            // Label
            String label;
            int labelColor;
            if (unlocked) {
                label = page.tabLabel;
                labelColor = active ? 0xFFFFFFFF : 0xFFBBBBCC;
            } else {
                label = "\uD83D\uDD12 " + page.tabLabel; // padlock emoji prefix
                labelColor = 0xFF666677;
            }
            int labelW = this.textRenderer.getWidth(label);
            context.drawTextWithShadow(this.textRenderer, label,
                x + (w - labelW) / 2, tabY + (tabH - 8) / 2 + 1, labelColor);

            tabBounds.add(new int[] { x, tabY, x + w, tabY + tabH });
            x += w + gap;
        }
    }

    private void drawBiomeIcon(DrawContext context, Identifier texture, int x, int y, int w, int h) {
        //? if <=1.21.1 {
        /*context.drawTexture(texture, x, y, 0f, 0f, w, h, w, h);
        *///?} else {
        context.drawTexture(net.minecraft.client.render.RenderLayer::getGuiTextured, texture, x, y, 0f, 0f, w, h, w, h);
        //?}
    }

    // ─────────────────────────────────────────────────────────────────────
    // Input
    // ─────────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Tab bar hit-test first (so vanilla button layer doesn't eat the click).
        if (button == 0) {
            for (int i = 0; i < tabBounds.size(); i++) {
                int[] b = tabBounds.get(i);
                if (mouseX >= b[0] && mouseX < b[2] && mouseY >= b[1] && mouseY < b[3]) {
                    switchToPage(i);
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        List<BiomeEntry> biomes = currentBiomes();
        if (verticalAmount > 0 && selectedIndex > 0) {
            scrollBiome(-1);
        } else if (verticalAmount < 0 && selectedIndex < biomes.size() - 1) {
            scrollBiome(1);
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Left/Right arrows: biome navigation within current page
        // Q/E: dimension tab switching
        // 263 = LEFT, 262 = RIGHT, 81 = Q, 69 = E
        if (keyCode == 263) {
            scrollBiome(-1);
            return true;
        } else if (keyCode == 262) {
            scrollBiome(1);
            return true;
        } else if (keyCode == 81) {
            cyclePage(-1);
            return true;
        } else if (keyCode == 69) {
            cyclePage(1);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
