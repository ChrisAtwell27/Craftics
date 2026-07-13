package com.crackedgames.craftics.client;

import com.crackedgames.craftics.block.LevelSelectScreenHandler;
import com.crackedgames.craftics.level.campaign.CampaignManager;
import com.crackedgames.craftics.level.campaign.CampaignNode;
import com.crackedgames.craftics.level.campaign.CampaignRegion;
import com.crackedgames.craftics.network.StartLevelPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.resource.ResourceManager;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Paged biome selector. The player browses one <em>dimension</em> at a time -
 * Overworld / Nether / End by default, plus any pages contributed by addons
 * via {@link #registerExtraPage(DimensionPage)}.
 * <p>
 * Within a page, biome cards side-scroll horizontally (the legacy layout).
 * A tab bar at the top lets the player jump between pages with the mouse or
 * via the Q / E hotkeys.
 * <p>
 * <b>Unlock model:</b> the handler still hands the client a single global
 * {@code highestUnlocked} number - biomes are indexed into that via their
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

    /** All pages, in tab order. Always non-empty - at minimum contains Overworld. */
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

    /** Horizontal scroll of the tab strip in pixels, eased toward keeping the
     *  active tab centered. Stays 0 while every tab fits on screen. */
    private float tabScrollX = 0;
    private float tabScrollTarget = 0;

    private ButtonWidget enterButton;
    private ButtonWidget leftArrow;
    private ButtonWidget rightArrow;

    /** Tab hit-boxes (rebuilt each frame). Index matches {@link #pages}. */
    private final List<int[]> tabBounds = new ArrayList<>();

    /** Biome-card hit-boxes from the last render pass: {x1, y1, x2, y2, biomeIndex}. */
    private final List<int[]> cardBounds = new ArrayList<>();

    /** Progress-dot hit-boxes from the last render pass: {x1, y1, x2, y2, biomeIndex}. */
    private final List<int[]> dotBounds = new ArrayList<>();

    /** Tab the cursor is over this frame (for the progress tooltip), or -1. */
    private int hoveredTabIndex = -1;

    /**
     * Per-texture "does this resource exist" cache. Custom-campaign biomes often
     * ship no {@code textures/gui/biomes/<id>.png}; rather than hammer the resource
     * manager every frame for every visible card, the answer is resolved once per
     * Identifier and reused.
     */
    private final Map<Identifier, Boolean> textureExists = new HashMap<>();

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
        // Seed the starting biome (always discovered) - the first node of the
        // active campaign in branch-adjusted run order.
        List<String> ord = CampaignManager.orderedBiomeIds(Math.max(0, branchChoice));
        if (!ord.isEmpty()) {
            discovered.add(ord.get(0));
        }

        this.pages = buildPages(branchChoice);

        // Land on the page that contains the highest unlocked biome so the
        // player's next step is visible without any clicks.
        currentPageIndex = pickInitialPage();
        selectedIndex = pickInitialBiomeInPage(pages.get(currentPageIndex));
        scrollOffset = scrollTarget = selectedIndex;

        rebuildWidgets();
    }

    /**
     * Assemble the tab list - one page per region of the <em>active campaign</em>
     * (vanilla by default), in campaign order. Each page's biomes are listed in
     * the branch-adjusted run order, and the GLOBAL {@code order} counter advances
     * in that same flattened run order so it matches the server's
     * {@code highestUnlocked} cursor (which counts biomes in campaign order across
     * regions). Addon pages are appended afterwards.
     *
     * <p>Vanilla trace: overworld nodes get order 1-9, nether 10-14, end 15-18.
     */
    private List<DimensionPage> buildPages(int branchChoice) {
        List<DimensionPage> out = new ArrayList<>();

        // Flattened node list in branch-adjusted run order across ALL regions.
        // The branch only swaps segments WITHIN a single region, so each region's
        // nodes stay a contiguous block here; iterating regions in order and
        // filtering `flat` to the current region advances `order` in the correct
        // global sequence (region 0 -> 1..k, region 1 -> k+1.., ...).
        List<CampaignNode> flat = CampaignManager.orderedNodes(Math.max(0, branchChoice));

        int order = 1;
        for (CampaignRegion region : CampaignManager.regions()) {
            List<BiomeEntry> entries = new ArrayList<>();
            int color = region.mapColor(); // region ARGB tint for tab + card border
            for (CampaignNode node : flat) {
                CampaignRegion r = CampaignManager.regionOf(node.biomeId());
                if (r != null && region.id().equals(r.id())) {
                    String label = node.labelOverride() != null ? node.labelOverride() : node.biomeId();
                    Identifier tex = Identifier.of("craftics",
                        "textures/gui/biomes/" + node.biomeId() + ".png");
                    entries.add(new BiomeEntry(node.biomeId(), label, color, tex, order++));
                }
            }
            String tabLabel = region.icon() + " " + region.displayName().toUpperCase();
            out.add(new DimensionPage(region.id(), region.displayName(), tabLabel, color, entries));
        }

        // Defensive: with no active campaign (shouldn't happen in-game) keep the
        // screen non-empty so the render / rebuild paths never index an empty list.
        if (out.isEmpty()) {
            out.add(new DimensionPage("campaign", "Campaign", "CAMPAIGN",
                0xFFFFFFFF, new ArrayList<>()));
        }

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
        enterButton = ButtonWidget.builder(Text.literal(btnLabel), button -> activateFocusedBiome())
            .dimensions(cx - 80, buttonY, 160, 20).build();
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

        // Bottom-left: enter the walk-around merchant scenes.
        // Each button appears only once the island has MET a merchant of that kind at a run
        // event. Before that the hall would just be a building full of empty stalls, so showing
        // the way in would promise something that isn't there yet.
        if (handler.hasMetAnyTrader()) {
            ButtonWidget tradingHallBtn = ButtonWidget.builder(
                    Text.literal("\u00A7lTrading Hall"),
                    b -> {
                        this.close();
                        ClientPlayNetworking.send(
                            new com.crackedgames.craftics.network.EnterScenePayload("village"));
                    })
                .dimensions(8, this.height - 48, 120, 20).build();
            tradingHallBtn.setTooltip(net.minecraft.client.gui.tooltip.Tooltip.of(
                Text.literal("\u00A77Visit the traders you have met. Meet more at run events to fill the hall.")));
            this.addDrawableChild(tradingHallBtn);
        }

        if (handler.hasMetAnyBarterer()) {
            ButtonWidget barterStationBtn = ButtonWidget.builder(
                    Text.literal("\u00A7lBartering Station"),
                    b -> {
                        this.close();
                        ClientPlayNetworking.send(
                            new com.crackedgames.craftics.network.EnterScenePayload("barter_station"));
                    })
                .dimensions(8, this.height - 24, 120, 20).build();
            barterStationBtn.setTooltip(net.minecraft.client.gui.tooltip.Tooltip.of(
                Text.literal("\u00A77Gamble gold with the piglins you have met.")));
            this.addDrawableChild(barterStationBtn);
        }

        // Bottom-right: infinite mode. Rides the normal run-start flow under a
        // sentinel biome id; the server handles the party prompt + fresh-start rules.
        ButtonWidget infiniteBtn = ButtonWidget.builder(
                Text.literal("\u00A75\u00A7l\u221E Infinite Mode"),
                b -> {
                    this.close();
                    TransitionOverlay.startTransition(
                        "\u221E Infinite Mode \u221E", "No items. No levels. No end.",
                        () -> ClientPlayNetworking.send(new StartLevelPayload(
                            "craftics:infinite"))
                    );
                })
            .dimensions(this.width - 128, buttonY, 120, 20).build();
        this.addDrawableChild(infiniteBtn);
    }

    private void scrollBiome(int dir) {
        List<BiomeEntry> biomes = currentBiomes();
        if (biomes.isEmpty()) return;
        selectedIndex = Math.max(0, Math.min(selectedIndex + dir, biomes.size() - 1));
        scrollTarget = selectedIndex;
        rebuildWidgets();
    }

    /** Jump straight to a biome index on the current page (card / dot click). */
    private void jumpToBiome(int index) {
        List<BiomeEntry> biomes = currentBiomes();
        if (biomes.isEmpty()) return;
        selectedIndex = Math.max(0, Math.min(index, biomes.size() - 1));
        scrollTarget = selectedIndex;
        rebuildWidgets();
    }

    /**
     * Enter the focused biome if it's unlocked - shared by the Enter button,
     * clicking the focused card, and the Enter key.
     */
    private void activateFocusedBiome() {
        List<BiomeEntry> biomes = currentBiomes();
        if (biomes.isEmpty()) return;
        BiomeEntry focused = biomes.get(Math.max(0, Math.min(selectedIndex, biomes.size() - 1)));
        if (focused.order > highestUnlocked) return;
        this.close();
        TransitionOverlay.startTransition(
            focused.displayName, "Entering the arena...",
            () -> ClientPlayNetworking.send(new StartLevelPayload(focused.biomeId))
        );
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

        // Focused-card swell amplitude (used for cardScale below). Kept subtle:
        // the focused card grows imgSize*amp/2 past its base on each side, and
        // the name label / progress dots sit at fixed offsets below the card's
        // BASE bottom edge, so a large swell would let the enlarged card creep
        // over them. 5% keeps the card clear of the name without a proportional
        // layout push (which would otherwise collide with the fixed-gap Enter
        // button on tall windows).
        float focusAmp = 0.05f;

        // NG+ title
        int totalBiomes = CampaignManager.totalBiomes();
        int ngPlus = totalBiomes > 0 ? Math.max(0, (highestUnlocked - 1) / totalBiomes) : 0;
        String title = ngPlus > 0
            ? "\u00a7l\u00a76\u2605 CRAFTICS \u2605 \u00a7c(NG+" + ngPlus + ")"
            : "\u00a7l\u00a76\u2605 CRAFTICS \u2605";
        context.drawCenteredTextWithShadow(this.textRenderer, title, cx, 6, 0xFFAA00);

        // Tab bar
        int tabY = 20;
        int tabH = 16;
        renderTabBar(context, tabY, tabH, mouseX, mouseY, delta);

        // Render biome cards for current page (visible range around scroll position)
        List<BiomeEntry> biomes = currentBiomes();
        if (biomes.isEmpty()) return;

        cardBounds.clear();
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

            // Cards swell as they approach the focus slot. Driven by the eased
            // scrollOffset, so the size change slides with the carousel instead
            // of popping when the selection changes.
            float focusT = Math.max(0f, 1f - Math.abs(relPos));
            float cardScale = 1.0f + focusAmp * focusT * focusT;
            boolean cardScaled = cardScale > 1.001f;

            // Hit-box matches the scaled visual.
            int half = (int) (imgSize * cardScale / 2);
            cardBounds.add(new int[] { cardCenterX - half, cy - half, cardCenterX + half, cy + half, i });
            boolean hovered = mouseX >= cardCenterX - half && mouseX < cardCenterX + half
                && mouseY >= cy - half && mouseY < cy + half;

            if (cardScaled) {
                context.getMatrices().push();
                context.getMatrices().translate((float) cardCenterX, (float) cy, 0f);
                context.getMatrices().scale(cardScale, cardScale, 1f);
                context.getMatrices().translate((float) -cardCenterX, (float) -cy, 0f);
            }

            if (unlocked) {
                drawBiomeIcon(context, entry.texture, imgX, imgY, imgSize, imgSize, entry.dimColor);
                if (!isFocused) {
                    // Lift the dim slightly on hover so cards read as clickable.
                    context.fill(imgX, imgY, imgX + imgSize, imgY + imgSize,
                        hovered ? 0x60000000 : 0xA0000000);
                }
            } else {
                context.fill(imgX, imgY, imgX + imgSize, imgY + imgSize, 0xFF1A1A2A);
                context.fill(imgX + 1, imgY + 1, imgX + imgSize - 1, imgY + imgSize - 1,
                    hovered ? 0xFF2A2A3E : 0xFF222233);

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
            } else if (hovered) {
                // Thin hover outline on side cards.
                int hoverColor = 0x88FFFFFF;
                context.fill(imgX - 1, imgY - 1, imgX + imgSize + 1, imgY, hoverColor);
                context.fill(imgX - 1, imgY + imgSize, imgX + imgSize + 1, imgY + imgSize + 1, hoverColor);
                context.fill(imgX - 1, imgY, imgX, imgY + imgSize, hoverColor);
                context.fill(imgX + imgSize, imgY, imgX + imgSize + 1, imgY + imgSize, hoverColor);
            }

            if (cardScaled) {
                context.getMatrices().pop();
            }

            // Name label is drawn AFTER the scale pop so the focused card's
            // swell never displaces it - it keeps a fixed gap above the
            // progress dots regardless of cardScale.
            if (isFocused) {
                // Undiscovered locked biomes keep their mystery as "???".
                String name = (unlocked || isDiscovered) ? entry.displayName : "???";
                int nameColor = unlocked ? 0xFFFFFFFF : 0xFF8888AA;
                int nameW = this.textRenderer.getWidth(name);
                context.drawTextWithShadow(this.textRenderer, name,
                    cardCenterX - nameW / 2, imgY + imgSize + 6, nameColor);
            }
        }

        // Progress dots \u2014 one per biome on this page (cleared / next / locked),
        // clickable to jump. Fixed offset below the card's base bottom edge; the
        // focused card's subtle swell stays clear of this row.
        renderProgressDots(context, biomes, cx, cy + imgSize / 2 + 18, mouseX, mouseY);

        // Footer hint
        context.drawCenteredTextWithShadow(this.textRenderer,
            "\u00a78\u2191 \u2193 / Q / E: dimension  \u00b7  \u2190 \u2192: browse  \u00b7  click card: select  \u00b7  Enter: play",
            cx, this.height - 14, 0x555555);

        // Tab progress tooltip (drawn last so it overlays everything).
        if (hoveredTabIndex >= 0 && hoveredTabIndex < pages.size()) {
            DimensionPage page = pages.get(hoveredTabIndex);
            List<Text> tip = new ArrayList<>();
            if (isPageUnlocked(page)) {
                int cleared = 0;
                for (BiomeEntry e : page.biomes) {
                    if (e.order < highestUnlocked) cleared++;
                }
                tip.add(Text.literal(page.displayName));
                if (!page.biomes.isEmpty()) {
                    tip.add(Text.literal("\u00a77" + cleared + " / " + page.biomes.size() + " biomes cleared"));
                }
            } else {
                tip.add(Text.literal("\u00a7c" + page.displayName + " \u2014 locked"));
                tip.add(Text.literal("\u00a77Clear earlier biomes to unlock this region."));
            }
            context.drawTooltip(this.textRenderer, tip, mouseX, mouseY);
        }

        this.drawMouseoverTooltip(context, mouseX, mouseY);
    }

    /**
     * Row of small progress dots under the carousel: green = cleared,
     * gold = the next biome to tackle, dark = still locked. The focused
     * biome gets a white ring. Clicking a dot jumps to that biome.
     */
    private void renderProgressDots(DrawContext context, List<BiomeEntry> biomes,
                                    int cx, int y, int mouseX, int mouseY) {
        dotBounds.clear();
        int n = biomes.size();
        if (n <= 1) return;

        int dot = 5;
        int gap = 4;
        int totalW = n * dot + (n - 1) * gap;
        int maxW = this.width - 60;
        if (totalW > maxW) {
            dot = 3;
            gap = 2;
            totalW = n * dot + (n - 1) * gap;
            if (totalW > maxW) return; // absurd biome count \u2014 drop the dots
        }

        int x = cx - totalW / 2;
        for (int i = 0; i < n; i++) {
            BiomeEntry e = biomes.get(i);
            boolean cleared = e.order < highestUnlocked;
            boolean current = e.order == highestUnlocked;
            int color = cleared ? 0xFF55CC55 : current ? 0xFFFFCC33 : 0xFF333344;
            boolean hovered = mouseX >= x - 2 && mouseX < x + dot + 2
                && mouseY >= y - 2 && mouseY < y + dot + 2;

            if (i == selectedIndex) {
                context.fill(x - 1, y - 1, x + dot + 1, y + dot + 1, 0xFFFFFFFF);
            } else if (hovered) {
                context.fill(x - 1, y - 1, x + dot + 1, y + dot + 1, 0x88FFFFFF);
            }
            context.fill(x, y, x + dot, y + dot, color);

            dotBounds.add(new int[] { x - 2, y - 2, x + dot + 2, y + dot + 2, i });
            x += dot + gap;
        }
    }

    /**
     * Draw the dimension tab bar. Each tab is a flat rectangle with the
     * dimension's label centered inside. The active tab sits slightly lower
     * with a bright outline; locked tabs render muted with a padlock glyph.
     */
    private void renderTabBar(DrawContext context, int tabY, int tabH, int mouseX, int mouseY, float delta) {
        tabBounds.clear();
        hoveredTabIndex = -1;

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

        // When every tab fits, center the strip as before. With enough regions
        // / addon pages to overflow, the strip becomes a scrolling viewport
        // that keeps the active tab centered; chevrons mark hidden tabs.
        int viewX = 14;
        int viewW = this.width - viewX * 2;
        boolean overflowing = totalW > viewW;
        int startX;
        if (!overflowing) {
            tabScrollX = tabScrollTarget = 0;
            startX = (this.width - totalW) / 2;
        } else {
            int activeCenter = 0;
            int ax = 0;
            for (int i = 0; i < pages.size(); i++) {
                if (i == currentPageIndex) { activeCenter = ax + widths[i] / 2; break; }
                ax += widths[i] + gap;
            }
            tabScrollTarget = Math.max(0, Math.min(totalW - viewW, activeCenter - viewW / 2f));
            tabScrollX += (tabScrollTarget - tabScrollX) * Math.min(1.0f, 8.0f * delta);
            if (Math.abs(tabScrollTarget - tabScrollX) < 0.5f) tabScrollX = tabScrollTarget;
            startX = viewX - (int) tabScrollX;
            context.enableScissor(viewX, tabY - 1, viewX + viewW, tabY + tabH + 1);
        }

        int x = startX;

        for (int i = 0; i < pages.size(); i++) {
            DimensionPage page = pages.get(i);
            int w = widths[i];
            boolean unlocked = isPageUnlocked(page);
            boolean active = i == currentPageIndex;
            // Hover / click regions clamp to the viewport when scrolling, so a
            // tab clipped by the scissor can't be hit through the chevrons.
            int hitX1 = overflowing ? Math.max(x, viewX) : x;
            int hitX2 = overflowing ? Math.min(x + w, viewX + viewW) : x + w;
            boolean hovered = mouseX >= hitX1 && mouseX < hitX2 && mouseY >= tabY && mouseY < tabY + tabH;
            if (hovered) hoveredTabIndex = i;

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

            tabBounds.add(new int[] { hitX1, tabY, hitX2, tabY + tabH });
            x += w + gap;
        }

        if (overflowing) {
            context.disableScissor();
            // Edge chevrons hint at tabs scrolled out of view on either side.
            int chevY = tabY + (tabH - 8) / 2 + 1;
            if (tabScrollX > 1) {
                context.drawTextWithShadow(this.textRenderer, "◀", viewX - 11, chevY, 0xFF8888AA);
            }
            if (tabScrollX < totalW - viewW - 1) {
                context.drawTextWithShadow(this.textRenderer, "▶", viewX + viewW + 3, chevY, 0xFF8888AA);
            }
        }
    }

    private void drawBiomeIcon(DrawContext context, Identifier texture, int x, int y, int w, int h, int fallbackColor) {
        // Custom-campaign biomes often have no card art. Rather than render the
        // missing-texture (purple/black checkerboard) placeholder, fall back to a
        // solid colored panel tinted with the biome's region color.
        if (!textureResourceExists(texture)) {
            int fill = 0xFF000000 | (fallbackColor & 0x00FFFFFF); // force opaque
            context.fill(x, y, x + w, y + h, fill);
            // Subtle inner panel + darker border so the card still reads as a tile.
            int inner = 0xFF000000 | (dim(fallbackColor, 0.65f) & 0x00FFFFFF);
            context.fill(x + 2, y + 2, x + w - 2, y + h - 2, inner);
            return;
        }
        //? if <=1.21.1 {
        context.drawTexture(texture, x, y, 0f, 0f, w, h, w, h);
        //?} else {
        /*context.drawTexture(net.minecraft.client.render.RenderLayer::getGuiTextured, texture, x, y, 0f, 0f, w, h, w, h);
        *///?}
    }

    /**
     * Whether {@code texture} resolves to an actual resource. Cached per Identifier
     * (the answer is stable for the screen's lifetime) so the resource manager isn't
     * queried every frame for every visible card.
     */
    private boolean textureResourceExists(Identifier texture) {
        Boolean cached = textureExists.get(texture);
        if (cached != null) return cached;
        boolean exists = false;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null) {
            ResourceManager rm = client.getResourceManager();
            if (rm != null) {
                exists = rm.getResource(texture).isPresent();
            }
        }
        textureExists.put(texture, exists);
        return exists;
    }

    /** Scale each RGB channel of an ARGB color by {@code factor} (alpha untouched). */
    private static int dim(int argb, float factor) {
        int r = (int) (((argb >> 16) & 0xFF) * factor);
        int g = (int) (((argb >> 8) & 0xFF) * factor);
        int b = (int) ((argb & 0xFF) * factor);
        return (argb & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    // ─────────────────────────────────────────────────────────────────────
    // Input
    // ─────────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Tab bar / dots / cards are hit-tested before super - HandledScreen
        // consumes every click (cursor-stack handling), so anything after it
        // never fires. Widgets (arrows, Enter) don't overlap these regions.
        if (button == 0) {
            for (int i = 0; i < tabBounds.size(); i++) {
                int[] b = tabBounds.get(i);
                if (mouseX >= b[0] && mouseX < b[2] && mouseY >= b[1] && mouseY < b[3]) {
                    switchToPage(i);
                    return true;
                }
            }
            for (int[] d : dotBounds) {
                if (mouseX >= d[0] && mouseX < d[2] && mouseY >= d[1] && mouseY < d[3]) {
                    jumpToBiome(d[4]);
                    return true;
                }
            }
            for (int[] c : cardBounds) {
                if (mouseX >= c[0] && mouseX < c[2] && mouseY >= c[1] && mouseY < c[3]) {
                    if (c[4] == selectedIndex) {
                        // Clicking the focused card enters it (when unlocked).
                        activateFocusedBiome();
                    } else {
                        jumpToBiome(c[4]);
                    }
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
        // Up/Down arrows + Q/E: dimension row switching, Enter: play the focused biome
        // 263 = LEFT, 262 = RIGHT, 265 = UP, 264 = DOWN, 81 = Q, 69 = E, 257 = ENTER, 335 = KP_ENTER
        if (keyCode == 263) {
            scrollBiome(-1);
            return true;
        } else if (keyCode == 262) {
            scrollBiome(1);
            return true;
        } else if (keyCode == 81 || keyCode == 265) {
            cyclePage(-1);
            return true;
        } else if (keyCode == 69 || keyCode == 264) {
            cyclePage(1);
            return true;
        } else if (keyCode == 257 || keyCode == 335) {
            activateFocusedBiome();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
