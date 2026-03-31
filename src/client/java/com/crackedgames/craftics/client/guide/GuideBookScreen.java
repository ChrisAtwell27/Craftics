package com.crackedgames.craftics.client.guide;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.List;

/**
 * Guide book screen with vertical accordion sidebar.
 * Left side: category headers (click to expand) with entries listed below selected category.
 * Right side: page content for selected entry.
 */
public class GuideBookScreen extends Screen {

    private static final int BOOK_WIDTH = 380;
    private static final int BOOK_HEIGHT = 250;
    private static final int SIDEBAR_WIDTH = 120;
    private static final int PADDING = 8;
    private static final int LINE_HEIGHT = 11;
    private static final int CATEGORY_HEIGHT = 16;
    private static final int ENTRY_HEIGHT = 14;

    private int bookX, bookY;
    private int selectedCategory = 0;
    private int selectedEntry = 0;
    private int currentPage = 0;
    private int sidebarScroll = 0;
    private int maxSidebarScroll = 0;

    public GuideBookScreen() {
        super(Text.literal("Craftics Field Manual"));
    }

    @Override
    protected void init() {
        super.init();
        bookX = (width - BOOK_WIDTH) / 2;
        bookY = (height - BOOK_HEIGHT) / 2;
        rebuildButtons();
    }

    private void rebuildButtons() {
        clearChildren();
        List<GuideBookData.Category> cats = GuideBookData.getCategories();

        // Calculate total sidebar content height for scrolling
        int totalHeight = 0;
        for (int i = 0; i < cats.size(); i++) {
            totalHeight += CATEGORY_HEIGHT + 2;
            if (i == selectedCategory) {
                totalHeight += cats.get(i).entries().size() * (ENTRY_HEIGHT + 1);
            }
        }
        int sidebarVisibleH = BOOK_HEIGHT - PADDING * 2;
        maxSidebarScroll = Math.max(0, totalHeight - sidebarVisibleH);
        sidebarScroll = Math.min(sidebarScroll, maxSidebarScroll);

        // Build accordion: category headers + entries for selected category
        int y = bookY + PADDING - sidebarScroll;
        for (int catIdx = 0; catIdx < cats.size(); catIdx++) {
            GuideBookData.Category cat = cats.get(catIdx);
            final int ci = catIdx;
            boolean isSelected = (catIdx == selectedCategory);

            // Category header button
            String catLabel = (isSelected ? "\u00a7e\u25bc " : "\u00a77\u25b6 ") + cat.name();
            if (y + CATEGORY_HEIGHT > bookY && y < bookY + BOOK_HEIGHT) {
                var catBtn = ButtonWidget.builder(
                    Text.literal(catLabel),
                    b -> {
                        if (selectedCategory != ci) {
                            selectedCategory = ci;
                            // Bestiary starts with no entry selected so the grid shows first
                            selectedEntry = cats.get(ci).name().equals("Enemy Bestiary") ? -1 : 0;
                            currentPage = 0;
                            sidebarScroll = 0;
                        } else if (cats.get(ci).name().equals("Enemy Bestiary")) {
                            // Re-clicking Bestiary category goes back to grid
                            selectedEntry = -1;
                        }
                        rebuildButtons();
                    }
                ).dimensions(bookX + 2, y, SIDEBAR_WIDTH - 4, CATEGORY_HEIGHT).build();
                addDrawableChild(catBtn);
            }
            y += CATEGORY_HEIGHT + 2;

            // Entries for selected category (skip for Bestiary — uses grid in content area)
            if (isSelected && !cat.name().equals("Enemy Bestiary")) {
                for (int entIdx = 0; entIdx < cat.entries().size(); entIdx++) {
                    GuideBookData.Entry entry = cat.entries().get(entIdx);
                    boolean unlocked = GuideBookData.isUnlocked(entry.name());
                    final int ei = entIdx;

                    boolean isSelectedEntry = (entIdx == selectedEntry);
                    String entLabel;
                    if (!unlocked) entLabel = "  \u00a78???";
                    else if (isSelectedEntry) entLabel = "  \u00a7e\u25b8 " + entry.name();
                    else entLabel = "  " + entry.name();
                    if (y + ENTRY_HEIGHT > bookY && y < bookY + BOOK_HEIGHT) {
                        var entBtn = ButtonWidget.builder(
                            Text.literal(entLabel),
                            b -> {
                                if (unlocked) {
                                    selectedEntry = ei;
                                    currentPage = 0;
                                    rebuildButtons();
                                }
                            }
                        ).dimensions(bookX + 6, y, SIDEBAR_WIDTH - 10, ENTRY_HEIGHT).build();
                        entBtn.active = unlocked;
                        addDrawableChild(entBtn);
                    }
                    y += ENTRY_HEIGHT + 1;
                }
            }
        }

        // Page navigation arrows
        GuideBookData.Category currentCat = cats.get(selectedCategory);
        if (selectedEntry >= 0 && selectedEntry < currentCat.entries().size()) {
            GuideBookData.Entry entry = currentCat.entries().get(selectedEntry);
            if (GuideBookData.isUnlocked(entry.name())) {
                int navY = bookY + BOOK_HEIGHT - 24;
                int contentX = bookX + SIDEBAR_WIDTH + PADDING;

                if (currentPage > 0) {
                    addDrawableChild(ButtonWidget.builder(
                        Text.literal("< Prev"),
                        b -> { currentPage--; rebuildButtons(); }
                    ).dimensions(contentX, navY, 50, 18).build());
                }
                if (currentPage < entry.pages().size() - 1) {
                    addDrawableChild(ButtonWidget.builder(
                        Text.literal("Next >"),
                        b -> { currentPage++; rebuildButtons(); }
                    ).dimensions(bookX + BOOK_WIDTH - 58, navY, 50, 18).build());
                }
            }
        }
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // Darkened background
        ctx.fill(0, 0, width, height, 0xA0000000);

        // Book background
        ctx.fill(bookX - 2, bookY - 2, bookX + BOOK_WIDTH + 2, bookY + BOOK_HEIGHT + 2, 0xFF3B2A1A);
        ctx.fill(bookX, bookY, bookX + BOOK_WIDTH, bookY + BOOK_HEIGHT, 0xFF5C4033);

        // Sidebar background
        ctx.fill(bookX, bookY, bookX + SIDEBAR_WIDTH, bookY + BOOK_HEIGHT, 0xFF4A3528);

        // Sidebar divider line
        ctx.fill(bookX + SIDEBAR_WIDTH, bookY, bookX + SIDEBAR_WIDTH + 1, bookY + BOOK_HEIGHT, 0xFF2A1A0E);

        // Render sidebar buttons clipped to sidebar bounds
        ctx.enableScissor(bookX, bookY, bookX + SIDEBAR_WIDTH, bookY + BOOK_HEIGHT);
        super.render(ctx, mouseX, mouseY, delta);
        ctx.disableScissor();

        // Re-render content area buttons (page nav) outside scissor so they're visible
        for (var child : children()) {
            if (child instanceof ButtonWidget btn) {
                if (btn.getX() >= bookX + SIDEBAR_WIDTH) {
                    btn.render(ctx, mouseX, mouseY, delta);
                }
            }
        }

        // Title
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("\u00a76\u00a7l" + title.getString()),
            bookX + BOOK_WIDTH / 2, bookY - 16, 0xFFFFAA00);

        // Scroll indicators
        if (sidebarScroll > 0) {
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("\u00a77\u25b2"),
                bookX + SIDEBAR_WIDTH / 2, bookY + 1, 0xFF888888);
        }
        if (sidebarScroll < maxSidebarScroll) {
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("\u00a77\u25bc"),
                bookX + SIDEBAR_WIDTH / 2, bookY + BOOK_HEIGHT - 10, 0xFF888888);
        }

        // Render page content
        renderContent(ctx);
    }

    private static final int GRID_CELL = 24;
    private static final int GRID_COLS = 7;

    private void renderContent(DrawContext ctx) {
        List<GuideBookData.Category> cats = GuideBookData.getCategories();
        if (selectedCategory >= cats.size()) return;
        GuideBookData.Category cat = cats.get(selectedCategory);

        // Bestiary uses special grid rendering
        if (cat.name().equals("Enemy Bestiary")) {
            renderBestiaryGrid(ctx, cat);
            return;
        }

        if (selectedEntry < 0 || selectedEntry >= cat.entries().size()) return;
        GuideBookData.Entry entry = cat.entries().get(selectedEntry);
        if (!GuideBookData.isUnlocked(entry.name())) {
            int cx = bookX + SIDEBAR_WIDTH + (BOOK_WIDTH - SIDEBAR_WIDTH) / 2;
            String lockMsg = cat.name().equals("Armor Trims")
                    ? "\u00a77\u00a7oAcquire an armor trim template to unlock..."
                    : "\u00a77\u00a7oThis entry is locked.";
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(lockMsg),
                cx, bookY + BOOK_HEIGHT / 2, 0xFF888888);
            return;
        }

        if (currentPage >= entry.pages().size()) return;
        GuideBookData.Page page = entry.pages().get(currentPage);

        int contentX = bookX + SIDEBAR_WIDTH + PADDING + 4;
        int contentW = BOOK_WIDTH - SIDEBAR_WIDTH - PADDING * 2 - 8;
        int y = bookY + PADDING;

        // Page title
        ctx.drawTextWithShadow(textRenderer, Text.literal("\u00a7e\u00a7l" + page.title()),
            contentX, y, 0xFFFFDD44);
        y += LINE_HEIGHT + 4;

        // Separator line
        ctx.fill(contentX, y, contentX + contentW, y + 1, 0xFF8B7355);
        y += 6;

        // Page text — word wrap
        String[] lines = page.text().split("\n");
        for (String line : lines) {
            if (line.isEmpty()) {
                y += LINE_HEIGHT / 2;
                continue;
            }
            List<String> wrapped = wrapText(line, contentW);
            for (String wl : wrapped) {
                if (y + LINE_HEIGHT > bookY + BOOK_HEIGHT - 30) break;
                ctx.drawTextWithShadow(textRenderer, Text.literal(wl), contentX, y, 0xFFDDCCBB);
                y += LINE_HEIGHT;
            }
        }

        // Page indicator
        if (entry.pages().size() > 1) {
            String pageStr = (currentPage + 1) + " / " + entry.pages().size();
            int cx = bookX + SIDEBAR_WIDTH + (BOOK_WIDTH - SIDEBAR_WIDTH) / 2;
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("\u00a77" + pageStr),
                cx, bookY + BOOK_HEIGHT - 12, 0xFF888888);
        }
    }

    /** Render bestiary as a grid of cells. Locked = dark "???", unlocked = mob name + colored. */
    private void renderBestiaryGrid(DrawContext ctx, GuideBookData.Category cat) {
        int contentX = bookX + SIDEBAR_WIDTH + PADDING;
        int contentW = BOOK_WIDTH - SIDEBAR_WIDTH - PADDING * 2;
        int y = bookY + PADDING;

        // Header with discovery count
        String header = "\u00a76\u00a7lBestiary \u00a77(" + GuideBookData.getBestiaryUnlocked()
            + "/" + GuideBookData.getBestiaryTotal() + " discovered)";
        ctx.drawTextWithShadow(textRenderer, Text.literal(header), contentX, y, 0xFFFFAA00);
        y += LINE_HEIGHT + 4;
        ctx.fill(contentX, y, contentX + contentW, y + 1, 0xFF8B7355);
        y += 4;

        // If a specific mob is selected and unlocked, show its details
        if (selectedEntry >= 0 && selectedEntry < cat.entries().size()) {
            GuideBookData.Entry entry = cat.entries().get(selectedEntry);
            if (GuideBookData.isUnlocked(entry.name())) {
                renderBestiaryDetail(ctx, entry, contentX, y, contentW);
                return;
            }
        }

        // Grid of mob cells
        int cellW = (contentW - 4) / GRID_COLS;
        int cellH = 18;
        int rows = (cat.entries().size() + GRID_COLS - 1) / GRID_COLS;

        for (int i = 0; i < cat.entries().size(); i++) {
            int col = i % GRID_COLS;
            int row = i / GRID_COLS;
            int cx = contentX + col * cellW + 2;
            int cy = y + row * (cellH + 2);

            if (cy + cellH > bookY + BOOK_HEIGHT - 4) break;

            GuideBookData.Entry entry = cat.entries().get(i);
            boolean unlocked = GuideBookData.isUnlocked(entry.name());

            // Cell background
            int bgColor = unlocked ? 0xFF3A5A3A : 0xFF2A2A2A;
            if (i == selectedEntry && unlocked) bgColor = 0xFF4A6A3A;
            ctx.fill(cx, cy, cx + cellW - 2, cy + cellH, bgColor);
            // Border
            int borderColor = unlocked ? 0xFF55AA55 : 0xFF444444;
            ctx.fill(cx, cy, cx + cellW - 2, cy + 1, borderColor); // top
            ctx.fill(cx, cy + cellH - 1, cx + cellW - 2, cy + cellH, borderColor); // bottom
            ctx.fill(cx, cy, cx + 1, cy + cellH, borderColor); // left
            ctx.fill(cx + cellW - 3, cy, cx + cellW - 2, cy + cellH, borderColor); // right

            // Text
            String label = unlocked ? entry.name() : "???";
            int textColor = unlocked ? 0xFFCCFFCC : 0xFF666666;
            // Truncate if too wide
            String display = label;
            while (textRenderer.getWidth(display) > cellW - 6 && display.length() > 2) {
                display = display.substring(0, display.length() - 1);
            }
            if (display.length() < label.length()) display += "..";
            ctx.drawTextWithShadow(textRenderer, Text.literal(display),
                cx + 3, cy + (cellH - 8) / 2, textColor);
        }
    }

    /** Render detailed view of a selected bestiary mob. */
    private void renderBestiaryDetail(DrawContext ctx, GuideBookData.Entry entry,
                                       int contentX, int y, int contentW) {
        // Back hint (clicking the Bestiary category header also returns to grid)
        ctx.drawTextWithShadow(textRenderer, Text.literal("\u00a78[Click \u00a77Bestiary\u00a78 category or another mob to go back]"),
            contentX, y, 0xFF666666);
        y += LINE_HEIGHT + 2;

        // Mob name
        ctx.drawTextWithShadow(textRenderer, Text.literal("\u00a7a\u00a7l" + entry.name()),
            contentX, y, 0xFF55FF55);
        y += LINE_HEIGHT + 4;

        ctx.fill(contentX, y, contentX + contentW, y + 1, 0xFF55AA55);
        y += 6;

        // Mob description from page text
        if (!entry.pages().isEmpty()) {
            String[] lines = entry.pages().get(0).text().split("\n");
            for (String line : lines) {
                if (line.isEmpty()) {
                    y += LINE_HEIGHT / 2;
                    continue;
                }
                // Color-code weakness/resistance lines
                int color = 0xFFDDCCBB;
                if (line.startsWith("Weak to:")) color = 0xFF55FF55;
                else if (line.startsWith("Resist:")) color = 0xFFFF5555;
                else if (line.startsWith("Immune:")) color = 0xFFFF4444;

                List<String> wrapped = wrapText(line, contentW);
                for (String wl : wrapped) {
                    if (y + LINE_HEIGHT > bookY + BOOK_HEIGHT - 8) return;
                    ctx.drawTextWithShadow(textRenderer, Text.literal(wl), contentX, y, color);
                    y += LINE_HEIGHT;
                }
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Handle bestiary grid clicks
        List<GuideBookData.Category> cats = GuideBookData.getCategories();
        if (selectedCategory < cats.size() && cats.get(selectedCategory).name().equals("Enemy Bestiary")) {
            GuideBookData.Category cat = cats.get(selectedCategory);
            int contentX = bookX + SIDEBAR_WIDTH + PADDING;
            int contentW = BOOK_WIDTH - SIDEBAR_WIDTH - PADDING * 2;
            int cellW = (contentW - 4) / GRID_COLS;
            int cellH = 18;
            int gridY = bookY + PADDING + LINE_HEIGHT + 4 + 1 + 4; // matches renderBestiaryGrid layout

            if (mouseX >= contentX && mouseX < contentX + contentW
                    && mouseY >= gridY && mouseY < bookY + BOOK_HEIGHT) {
                int col = (int)((mouseX - contentX - 2) / cellW);
                int row = (int)((mouseY - gridY) / (cellH + 2));
                int idx = row * GRID_COLS + col;
                if (col >= 0 && col < GRID_COLS && idx >= 0 && idx < cat.entries().size()) {
                    if (GuideBookData.isUnlocked(cat.entries().get(idx).name())) {
                        selectedEntry = idx;
                        currentPage = 0;
                        rebuildButtons();
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        // Scroll sidebar when mouse is over it
        if (mouseX >= bookX && mouseX < bookX + SIDEBAR_WIDTH
                && mouseY >= bookY && mouseY < bookY + BOOK_HEIGHT) {
            sidebarScroll -= (int) (verticalAmount * 14);
            sidebarScroll = Math.max(0, Math.min(sidebarScroll, maxSidebarScroll));
            rebuildButtons();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private List<String> wrapText(String text, int maxWidth) {
        List<String> result = new java.util.ArrayList<>();
        String[] words = text.split(" ");
        StringBuilder line = new StringBuilder();
        for (String word : words) {
            String test = line.isEmpty() ? word : line + " " + word;
            if (textRenderer.getWidth(test) > maxWidth && !line.isEmpty()) {
                result.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(test);
            }
        }
        if (!line.isEmpty()) result.add(line.toString());
        return result;
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public boolean shouldCloseOnEsc() { return true; }
}
