package com.crackedgames.craftics.client.guide;

import com.crackedgames.craftics.client.TesterRegistry;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ProfileComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Guide book screen — polished parchment field manual.
 *
 * Left: leather sidebar with an icon accordion (categories + entries).
 * Right: parchment page with the selected entry's content.
 * Bestiary renders as an icon grid with structured stat badges in the detail view.
 *
 * Everything is custom-drawn with fills / text / item icons (no textures,
 * no ButtonWidgets) so it renders identically on every stonecutter shard.
 */
public class GuideBookScreen extends Screen {
    // UI version: 2026-06-12 parchment overhaul

    // ── Palette ──────────────────────────────────────────────────────────────
    private static final int COVER_EDGE   = 0xFF1F1209;
    private static final int COVER        = 0xFF3D2817;
    private static final int COVER_LIGHT  = 0xFF5A3D24;
    private static final int SIDEBAR_BG   = 0xFF31200F;
    private static final int SIDEBAR_ROW  = 0xFF3F2B16;
    private static final int SIDEBAR_HOVER= 0xFF54391D;
    private static final int SIDEBAR_SEL  = 0xFF6B4A24;
    private static final int GOLD         = 0xFFE8B637;
    private static final int GOLD_DIM     = 0xFFB78A2A;
    private static final int PARCH        = 0xFFEADCB3;
    private static final int PARCH_EDGE   = 0xFFD9C490;
    private static final int PARCH_SHADE  = 0xFFC4AA72;
    private static final int INK          = 0xFF3B2B12;
    private static final int INK_SOFT     = 0xFF6E5A36;
    private static final int INK_FAINT    = 0xFF9A8455;
    private static final int RULE         = 0xFFB39A66;

    private static final int LINE_HEIGHT = 11;

    // ── Layout (computed in init) ────────────────────────────────────────────
    private int bookX, bookY, bookW, bookH;
    private int sidebarW;
    private int headerH = 18;
    private int pageX, pageY, pageW, pageH; // parchment content area

    // ── State ────────────────────────────────────────────────────────────────
    private int selectedCategory = 0;
    private int selectedEntry = 0;
    private int currentPage = 0;
    private int bestiaryPage = 0;   // current page of the bestiary grid
    private int sidebarScroll = 0;
    private int maxSidebarScroll = 0;

    // ── Click zones (rebuilt every frame) ────────────────────────────────────
    private static final int Z_CAT = 0, Z_ENTRY = 1, Z_CELL = 2, Z_PREV = 3, Z_NEXT = 4, Z_BACK = 5,
                             Z_BPREV = 6, Z_BNEXT = 7;
    private record Zone(int kind, int a, int x, int y, int w, int h) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }
    private final List<Zone> zones = new ArrayList<>();

    // Item icon cache: id string -> stack (AIR for unknown ids, drawn as nothing)
    private static final Map<String, ItemStack> ICON_CACHE = new HashMap<>();

    public GuideBookScreen() {
        super(Text.literal("Craftics Field Manual"));
    }

    private static ItemStack icon(String id) {
        if (id == null || id.isEmpty()) return ItemStack.EMPTY;
        return ICON_CACHE.computeIfAbsent(id, key -> {
            // Support "preferredId|fallbackId|..." chains: used by compat bestiary
            // entries so a modded spawn-egg icon (e.g. creeperoverhaul:desert_creeper_spawn_egg)
            // falls back to a vanilla item when that mod's item isn't registered.
            // Plain single ids (no '|') behave exactly as before.
            for (String part : key.split("\\|")) {
                if (part.isEmpty()) continue;
                try {
                    Identifier ident = Identifier.of(part);
                    if (Registries.ITEM.containsId(ident)) {
                        return new ItemStack(Registries.ITEM.get(ident));
                    }
                } catch (Exception ignored) {
                    // malformed id — try the next fallback
                }
            }
            return ItemStack.EMPTY;
        });
    }

    // Player-head ItemStack cache for the Hall of Testers — built once per name.
    // Minecraft resolves and caches the skin for a profile player-head itself,
    // so this works for offline names too (shows the default head until the skin
    // finishes loading, then swaps in the real one).
    private static final Map<String, ItemStack> HEAD_CACHE = new HashMap<>();

    private static ItemStack testerHead(String name) {
        return HEAD_CACHE.computeIfAbsent(name, n -> {
            ItemStack head = new ItemStack(Items.PLAYER_HEAD);
            try {
                head.set(DataComponentTypes.PROFILE,
                    new ProfileComponent(Optional.of(n), Optional.empty(),
                        new com.mojang.authlib.properties.PropertyMap()));
            } catch (Throwable ignored) {
                // Fall back to a blank head rather than break the guide.
            }
            return head;
        });
    }

    @Override
    protected void init() {
        super.init();
        // Responsive book: grows with the window within sane bounds
        bookW = MathHelper.clamp(width - 70, 360, 560);
        bookH = MathHelper.clamp(height - 60, 240, 340);
        bookX = (width - bookW) / 2;
        bookY = (height - bookH) / 2;
        sidebarW = bookW >= 500 ? 144 : 126;
        pageX = bookX + sidebarW + 7;
        pageY = bookY + headerH + 4;
        pageW = bookX + bookW - 8 - pageX;
        pageH = bookY + bookH - 8 - pageY;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Rendering
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        zones.clear();

        // Dim the world
        ctx.fill(0, 0, width, height, 0xB0000000);

        drawBookChrome(ctx);
        drawSidebar(ctx, mouseX, mouseY);
        drawContent(ctx, mouseX, mouseY);
    }

    /** Leather cover, spine, header band, title, corner studs, parchment page. */
    private void drawBookChrome(DrawContext ctx) {
        // Cover with bevel
        ctx.fill(bookX - 4, bookY - 4, bookX + bookW + 4, bookY + bookH + 4, COVER_EDGE);
        ctx.fill(bookX - 2, bookY - 2, bookX + bookW + 2, bookY + bookH + 2, COVER);
        ctx.fill(bookX - 2, bookY - 2, bookX + bookW + 2, bookY, COVER_LIGHT); // top bevel highlight

        // Header band
        ctx.fill(bookX, bookY, bookX + bookW, bookY + headerH, SIDEBAR_BG);
        ctx.fill(bookX, bookY + headerH - 1, bookX + bookW, bookY + headerH, COVER_EDGE);
        ctx.fill(bookX, bookY + headerH, bookX + bookW, bookY + headerH + 1, GOLD_DIM);

        // Title with flourishes
        String flourish = "✦ ";
        ctx.drawCenteredTextWithShadow(textRenderer,
            Text.literal("§l" + flourish + title.getString() + " " + "✦"),
            bookX + bookW / 2, bookY + 5, GOLD);

        // Gold corner studs
        int s = 3;
        ctx.fill(bookX - 3, bookY - 3, bookX - 3 + s, bookY - 3 + s, GOLD_DIM);
        ctx.fill(bookX + bookW + 3 - s, bookY - 3, bookX + bookW + 3, bookY - 3 + s, GOLD_DIM);
        ctx.fill(bookX - 3, bookY + bookH + 3 - s, bookX - 3 + s, bookY + bookH + 3, GOLD_DIM);
        ctx.fill(bookX + bookW + 3 - s, bookY + bookH + 3 - s, bookX + bookW + 3, bookY + bookH + 3, GOLD_DIM);

        // Sidebar leather
        ctx.fill(bookX, bookY + headerH + 1, bookX + sidebarW, bookY + bookH, SIDEBAR_BG);
        // Spine shadow
        ctx.fill(bookX + sidebarW, bookY + headerH + 1, bookX + sidebarW + 2, bookY + bookH, COVER_EDGE);
        ctx.fill(bookX + sidebarW + 2, bookY + headerH + 1, bookX + sidebarW + 3, bookY + bookH, COVER_LIGHT);

        // Parchment page with layered edge shading
        int px0 = bookX + sidebarW + 3, py0 = bookY + headerH + 1;
        int px1 = bookX + bookW, py1 = bookY + bookH;
        ctx.fill(px0, py0, px1, py1, PARCH_SHADE);
        ctx.fill(px0 + 1, py0 + 1, px1 - 1, py1 - 1, PARCH_EDGE);
        ctx.fill(px0 + 3, py0 + 3, px1 - 3, py1 - 3, PARCH);
    }

    // ── Sidebar ──────────────────────────────────────────────────────────────

    private static final int CAT_ROW_H = 20;
    private static final int ENTRY_ROW_H = 17;

    private void drawSidebar(DrawContext ctx, int mouseX, int mouseY) {
        List<GuideBookData.Category> cats = GuideBookData.getCategories();

        int viewTop = bookY + headerH + 3;
        int viewBottom = bookY + bookH - 3;
        int viewH = viewBottom - viewTop;

        // Total height for scroll bounds
        int totalH = 0;
        for (int i = 0; i < cats.size(); i++) {
            totalH += CAT_ROW_H + 1;
            if (i == selectedCategory && !cats.get(i).name().equals("Enemy Bestiary")) {
                totalH += cats.get(i).entries().size() * (ENTRY_ROW_H + 1);
            }
        }
        maxSidebarScroll = Math.max(0, totalH - viewH);
        sidebarScroll = MathHelper.clamp(sidebarScroll, 0, maxSidebarScroll);

        ctx.enableScissor(bookX, viewTop, bookX + sidebarW, viewBottom);

        int y = viewTop - sidebarScroll;
        for (int ci = 0; ci < cats.size(); ci++) {
            GuideBookData.Category cat = cats.get(ci);
            boolean sel = ci == selectedCategory;

            // Category row
            int rx = bookX + 3, rw = sidebarW - 10;
            if (y + CAT_ROW_H > viewTop && y < viewBottom) {
                boolean hover = mouseX >= rx && mouseX < rx + rw && mouseY >= y && mouseY < y + CAT_ROW_H
                    && mouseY >= viewTop && mouseY < viewBottom;
                int bg = sel ? SIDEBAR_SEL : (hover ? SIDEBAR_HOVER : SIDEBAR_ROW);
                ctx.fill(rx, y, rx + rw, y + CAT_ROW_H, bg);
                if (sel) { // gold accent bar
                    ctx.fill(rx, y, rx + 2, y + CAT_ROW_H, GOLD);
                }
                ctx.drawItem(icon(cat.iconItem()), rx + 4, y + 2);
                String arrow = sel ? "▼ " : "▶ ";
                int nameColor = sel ? GOLD : 0xFFE3D2A8;
                ctx.drawTextWithShadow(textRenderer,
                    Text.literal(trimToWidth(arrow + cat.name(), rw - 26)),
                    rx + 23, y + 6, nameColor);
            }
            zones.add(new Zone(Z_CAT, ci, rx, y, rw, CAT_ROW_H));
            y += CAT_ROW_H + 1;

            // Entry rows (bestiary uses the grid instead)
            if (sel && !cat.name().equals("Enemy Bestiary")) {
                for (int ei = 0; ei < cat.entries().size(); ei++) {
                    GuideBookData.Entry entry = cat.entries().get(ei);
                    boolean unlocked = GuideBookData.isUnlocked(entry.name());
                    int ex = bookX + 9, ew = sidebarW - 16;
                    if (y + ENTRY_ROW_H > viewTop && y < viewBottom) {
                        boolean hover = unlocked && mouseX >= ex && mouseX < ex + ew
                            && mouseY >= y && mouseY < y + ENTRY_ROW_H
                            && mouseY >= viewTop && mouseY < viewBottom;
                        boolean selE = ei == selectedEntry;
                        if (selE || hover) {
                            ctx.fill(ex, y, ex + ew, y + ENTRY_ROW_H, selE ? SIDEBAR_SEL : SIDEBAR_HOVER);
                        }
                        if (unlocked) {
                            ctx.drawItem(icon(entry.iconItem()), ex + 2, y);
                            int color = selE ? GOLD : 0xFFCDBB92;
                            String name = trimToWidth(stripBold(entry.name()), ew - 24);
                            ctx.drawTextWithShadow(textRenderer, Text.literal(name), ex + 21, y + 4, color);
                        } else {
                            ctx.drawTextWithShadow(textRenderer, Text.literal("???"), ex + 21, y + 4, 0xFF6E5C40);
                        }
                    }
                    if (unlocked) zones.add(new Zone(Z_ENTRY, ei, ex, y, ew, ENTRY_ROW_H));
                    y += ENTRY_ROW_H + 1;
                }
            }
        }

        ctx.disableScissor();

        // Scrollbar
        if (maxSidebarScroll > 0) {
            int trackX = bookX + sidebarW - 5;
            ctx.fill(trackX, viewTop, trackX + 3, viewBottom, COVER_EDGE);
            int thumbH = Math.max(12, viewH * viewH / (viewH + maxSidebarScroll));
            int thumbY = viewTop + (viewH - thumbH) * sidebarScroll / maxSidebarScroll;
            ctx.fill(trackX, thumbY, trackX + 3, thumbY + thumbH, GOLD_DIM);
        }
    }

    // ── Content area ─────────────────────────────────────────────────────────

    private void drawContent(DrawContext ctx, int mouseX, int mouseY) {
        List<GuideBookData.Category> cats = GuideBookData.getCategories();
        if (selectedCategory >= cats.size()) return;
        GuideBookData.Category cat = cats.get(selectedCategory);

        if (cat.name().equals("Enemy Bestiary")) {
            drawBestiary(ctx, cat, mouseX, mouseY);
            return;
        }

        if (cat.name().equals("Hall of Testers")) {
            drawTesterHall(ctx, cat);
            return;
        }

        if (selectedEntry < 0 || selectedEntry >= cat.entries().size()) {
            drawCategoryLanding(ctx, cat);
            return;
        }
        GuideBookData.Entry entry = cat.entries().get(selectedEntry);
        if (!GuideBookData.isUnlocked(entry.name())) {
            String lockMsg = cat.name().equals("Armor Trims")
                ? "Acquire an armor trim template to unlock..."
                : "This entry is locked.";
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("§o" + lockMsg),
                pageX + pageW / 2, pageY + pageH / 2, INK_FAINT);
            return;
        }
        if (currentPage >= entry.pages().size()) currentPage = 0;
        GuideBookData.Page page = entry.pages().get(currentPage);

        int y = pageY + 2;
        // Title row: icon + title
        ctx.drawItem(icon(entry.iconItem()), pageX, y - 2);
        ctx.drawText(textRenderer, Text.literal("§l" + stripBold(page.title())),
            pageX + 20, y + 1, INK, false);
        y += LINE_HEIGHT + 5;
        drawRule(ctx, pageX, y, pageW);
        y += 7;

        // Body
        drawBody(ctx, page.text(), pageX, y, pageW, pageY + pageH - 16);

        drawPageNav(ctx, entry, mouseX, mouseY);
    }

    /** Landing panel when a category has no entry selected. */
    private void drawCategoryLanding(DrawContext ctx, GuideBookData.Category cat) {
        int cy = pageY + pageH / 2 - 16;
        ctx.drawItem(icon(cat.iconItem()), pageX + pageW / 2 - 8, cy);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("§l" + cat.name()),
            pageX + pageW / 2, cy + 22, INK);
        for (String line : wrapText("§o" + cat.description(), pageW - 20)) {
            cy += LINE_HEIGHT;
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(line),
                pageX + pageW / 2, cy + 26, INK_SOFT);
        }
    }

    /** Hall of Testers: each person's skin head + name/title in their live rank color, grouped by rank. */
    private void drawTesterHall(DrawContext ctx, GuideBookData.Category cat) {
        int y = pageY + 2;
        ctx.drawText(textRenderer, Text.literal("§lHall of Testers"), pageX, y, INK, false);
        y += LINE_HEIGHT + 4;
        drawRule(ctx, pageX, y, pageW);
        y += 6;
        for (String line : wrapText("§o" + cat.description(), pageW)) {
            ctx.drawText(textRenderer, Text.literal(line), pageX, y, INK_SOFT, false);
            y += LINE_HEIGHT;
        }
        y += 4;

        int bottom = pageY + pageH;
        for (TesterRegistry.Rank rank : TesterRegistry.Rank.values()) {
            List<TesterRegistry.Tester> group = new ArrayList<>();
            for (TesterRegistry.Tester t : TesterRegistry.all()) {
                if (t.rank() == rank) group.add(t);
            }
            if (group.isEmpty()) continue;
            if (y + LINE_HEIGHT > bottom) return;
            ctx.drawText(textRenderer, Text.literal("§l" + rankLabel(rank)), pageX, y, INK, false);
            y += LINE_HEIGHT + 2;
            for (TesterRegistry.Tester t : group) {
                if (y + 18 > bottom) return;
                ctx.drawItem(testerHead(t.name()), pageX + 2, y - 3);
                int col = TesterRegistry.colorOf(t);
                Text nameText = TesterRegistry.isBold(t)
                    ? Text.literal(t.name()).formatted(Formatting.BOLD)
                    : Text.literal(t.name());
                int nameX = pageX + 22;
                ctx.drawTextWithShadow(textRenderer, nameText, nameX, y, col);
                int titleX = nameX + textRenderer.getWidth(nameText) + 6;
                ctx.drawText(textRenderer, Text.literal(t.title()), titleX, y, INK_SOFT, false);
                y += 19;
            }
            y += 3;
        }
    }

    private static String rankLabel(TesterRegistry.Rank rank) {
        return switch (rank) {
            case CREATOR -> "Creator";
            case HELPER -> "Special Helpers";
            case TESTER -> "Testers";
        };
    }

    /** Decorative horizontal rule with a center diamond. */
    private void drawRule(DrawContext ctx, int x, int y, int w) {
        ctx.fill(x, y, x + w, y + 1, RULE);
        int cx = x + w / 2;
        ctx.fill(cx - 1, y - 1, cx + 1, y + 2, GOLD_DIM);
    }

    /** Word-wrapped body text in ink on parchment. Carries §-codes across wraps. */
    private void drawBody(DrawContext ctx, String text, int x, int y, int w, int maxY) {
        for (String raw : text.split("\n")) {
            if (raw.isEmpty()) { y += LINE_HEIGHT / 2; continue; }
            for (String line : wrapText(raw, w)) {
                if (y + LINE_HEIGHT > maxY) return;
                ctx.drawText(textRenderer, Text.literal(line), x, y, INK, false);
                y += LINE_HEIGHT;
            }
        }
    }

    /** Prev / Next as drawn parchment buttons + page indicator. */
    private void drawPageNav(DrawContext ctx, GuideBookData.Entry entry, int mouseX, int mouseY) {
        int pages = entry.pages().size();
        if (pages <= 1) return;

        int navY = bookY + bookH - 19;
        int btnW = 44, btnH = 13;

        if (currentPage > 0) {
            drawNavButton(ctx, pageX, navY, btnW, btnH, "◀ Prev", mouseX, mouseY);
            zones.add(new Zone(Z_PREV, 0, pageX, navY, btnW, btnH));
        }
        if (currentPage < pages - 1) {
            int nx = pageX + pageW - btnW;
            drawNavButton(ctx, nx, navY, btnW, btnH, "Next ▶", mouseX, mouseY);
            zones.add(new Zone(Z_NEXT, 0, nx, navY, btnW, btnH));
        }
        ctx.drawCenteredTextWithShadow(textRenderer,
            Text.literal((currentPage + 1) + " / " + pages),
            pageX + pageW / 2, navY + 3, INK_FAINT);
    }

    private void drawNavButton(DrawContext ctx, int x, int y, int w, int h, String label,
                               int mouseX, int mouseY) {
        boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        ctx.fill(x, y, x + w, y + h, hover ? PARCH_SHADE : PARCH_EDGE);
        ctx.fill(x, y, x + w, y + 1, RULE);
        ctx.fill(x, y + h - 1, x + w, y + h, RULE);
        ctx.fill(x, y, x + 1, y + h, RULE);
        ctx.fill(x + w - 1, y, x + w, y + h, RULE);
        int tw = textRenderer.getWidth(label);
        ctx.drawText(textRenderer, Text.literal(label), x + (w - tw) / 2, y + 3,
            hover ? INK : INK_SOFT, false);
    }

    // ── Bestiary ─────────────────────────────────────────────────────────────

    private static final int CELL = 24;

    private void drawBestiary(DrawContext ctx, GuideBookData.Category cat, int mouseX, int mouseY) {
        int y = pageY + 2;

        // Header + discovery progress bar
        int unlockedCount = GuideBookData.getBestiaryUnlocked();
        int total = GuideBookData.getBestiaryTotal();
        ctx.drawText(textRenderer, Text.literal("§lBestiary"), pageX, y, INK, false);
        String prog = unlockedCount + " / " + total;
        ctx.drawText(textRenderer, Text.literal(prog),
            pageX + pageW - textRenderer.getWidth(prog), y, INK_SOFT, false);
        y += LINE_HEIGHT + 2;
        // progress bar
        ctx.fill(pageX, y, pageX + pageW, y + 3, PARCH_SHADE);
        if (total > 0) {
            ctx.fill(pageX, y, pageX + pageW * unlockedCount / total, y + 3, GOLD_DIM);
        }
        y += 7;

        // Detail view if a mob is selected & unlocked
        if (selectedEntry >= 0 && selectedEntry < cat.entries().size()) {
            GuideBookData.Entry entry = cat.entries().get(selectedEntry);
            if (GuideBookData.isUnlocked(entry.name())) {
                drawBestiaryDetail(ctx, entry, y, mouseX, mouseY);
                return;
            }
        }

        // Grid (paginated — reserves the bottom row for Prev/Next page nav)
        int cols = Math.max(4, (pageW + 2) / (CELL + 2));
        int gridTop = y;
        int gridBottom = bookY + bookH - 21;
        int rows = Math.max(1, (gridBottom - gridTop + 2) / (CELL + 2));
        int perPage = Math.max(1, rows * cols);
        int totalEntries = cat.entries().size();
        int totalPages = Math.max(1, (totalEntries + perPage - 1) / perPage);
        bestiaryPage = MathHelper.clamp(bestiaryPage, 0, totalPages - 1);
        int start = bestiaryPage * perPage;
        int end = Math.min(totalEntries, start + perPage);

        String hoverName = null;
        for (int i = start; i < end; i++) {
            int slot = i - start;
            int cx = pageX + (slot % cols) * (CELL + 2);
            int cy = gridTop + (slot / cols) * (CELL + 2);

            GuideBookData.Entry entry = cat.entries().get(i);
            boolean unlocked = GuideBookData.isUnlocked(entry.name());
            boolean boss = entry.stats() != null && "Boss".equals(entry.stats().role());
            boolean hover = mouseX >= cx && mouseX < cx + CELL && mouseY >= cy && mouseY < cy + CELL;

            // Cell
            int bg = unlocked ? (hover ? 0xFFDcc68f : PARCH_EDGE) : 0xFF554830;
            ctx.fill(cx, cy, cx + CELL, cy + CELL, bg);
            int border = unlocked ? (boss ? GOLD_DIM : RULE) : 0xFF453A26;
            if (hover && unlocked) border = GOLD;
            ctx.fill(cx, cy, cx + CELL, cy + 1, border);
            ctx.fill(cx, cy + CELL - 1, cx + CELL, cy + CELL, border);
            ctx.fill(cx, cy, cx + 1, cy + CELL, border);
            ctx.fill(cx + CELL - 1, cy, cx + CELL, cy + CELL, border);

            if (unlocked) {
                ctx.drawItem(icon(entry.iconItem()), cx + (CELL - 16) / 2, cy + (CELL - 16) / 2);
                zones.add(new Zone(Z_CELL, i, cx, cy, CELL, CELL));
                if (hover) hoverName = (boss ? "§6§l" : "") + entry.name();
            } else {
                ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("?"),
                    cx + CELL / 2, cy + (CELL - 8) / 2, 0xFF8a7850);
                if (hover) hoverName = "§8???";
            }
        }
        if (hoverName != null) {
            ctx.drawTooltip(textRenderer, Text.literal(hoverName), mouseX, mouseY);
        }

        drawBestiaryNav(ctx, totalPages, mouseX, mouseY);
    }

    /** Grid page nav — mirrors drawPageNav exactly so the bestiary pages the same way. */
    private void drawBestiaryNav(DrawContext ctx, int totalPages, int mouseX, int mouseY) {
        if (totalPages <= 1) return;

        int navY = bookY + bookH - 19;
        int btnW = 44, btnH = 13;

        if (bestiaryPage > 0) {
            drawNavButton(ctx, pageX, navY, btnW, btnH, "◀ Prev", mouseX, mouseY);
            zones.add(new Zone(Z_BPREV, 0, pageX, navY, btnW, btnH));
        }
        if (bestiaryPage < totalPages - 1) {
            int nx = pageX + pageW - btnW;
            drawNavButton(ctx, nx, navY, btnW, btnH, "Next ▶", mouseX, mouseY);
            zones.add(new Zone(Z_BNEXT, 0, nx, navY, btnW, btnH));
        }
        ctx.drawCenteredTextWithShadow(textRenderer,
            Text.literal((bestiaryPage + 1) + " / " + totalPages),
            pageX + pageW / 2, navY + 3, INK_FAINT);
    }

    /** Detail view: name, role + stat badges, weak/resist lines, description. */
    private void drawBestiaryDetail(DrawContext ctx, GuideBookData.Entry entry, int y,
                                    int mouseX, int mouseY) {
        // Back link
        String back = "◀ Back to grid";
        boolean backHover = mouseX >= pageX && mouseX < pageX + textRenderer.getWidth(back)
            && mouseY >= y && mouseY < y + 10;
        ctx.drawText(textRenderer, Text.literal(back), pageX, y,
            backHover ? INK : INK_FAINT, false);
        zones.add(new Zone(Z_BACK, 0, pageX, y, textRenderer.getWidth(back) + 4, 11));
        y += LINE_HEIGHT + 2;

        GuideBookData.MobStats stats = entry.stats();
        boolean boss = stats != null && "Boss".equals(stats.role());

        // Name + icon
        ctx.drawItem(icon(entry.iconItem()), pageX, y - 2);
        ctx.drawText(textRenderer,
            Text.literal("§l" + entry.name()), pageX + 20, y + 1,
            boss ? 0xFF8A5A00 : INK, false);
        y += LINE_HEIGHT + 5;
        drawRule(ctx, pageX, y, pageW);
        y += 6;

        // Badges
        if (stats != null) {
            int bx = pageX;
            bx = drawBadge(ctx, bx, y, stats.role(), roleColor(stats.role()));
            bx = drawBadge(ctx, bx, y, label("HP", stats.hp()), 0xFFA8392E);
            bx = drawBadge(ctx, bx, y, label("ATK", stats.atk()), 0xFFB06A22);
            bx = drawBadge(ctx, bx, y, label("DEF", stats.def()), 0xFF3D6593);
            bx = drawBadge(ctx, bx, y, label("SPD", stats.spd()), 0xFF3F7D45);
            bx = drawBadge(ctx, bx, y, label("RNG", stats.rng()), 0xFF6F4D96);
            drawBadge(ctx, bx, y, stats.size(), 0xFF5F5F5F);
            y += 15;

            y = drawTypeLine(ctx, y, "Weak to: ", stats.weak(), 0xFF2E6B33);
            y = drawTypeLine(ctx, y, "Resists: ", stats.resist(), 0xFF96342A);
            y = drawTypeLine(ctx, y, "Immune: ", stats.immune(), 0xFF5A2D6B);
            y += 3;
        }

        // Description (current page of possibly several)
        if (currentPage >= entry.pages().size()) currentPage = 0;
        GuideBookData.Page page = entry.pages().get(currentPage);
        drawBody(ctx, page.text(), pageX, y, pageW, pageY + pageH - 24);

        // Scaling reminder
        if (stats != null && stats.hp() != null) {
            ctx.drawText(textRenderer,
                Text.literal("§oBase stats — enemies scale with biome, level & party."),
                pageX, bookY + bookH - 18, INK_FAINT, false);
        }

        drawPageNav(ctx, entry, mouseX, mouseY);
    }

    private static String label(String name, String value) {
        return value == null || value.isEmpty() ? null : name + " " + value;
    }

    private static int roleColor(String role) {
        if (role == null) return 0xFF5F5F5F;
        return switch (role) {
            case "Boss" -> 0xFF9A6A00;
            case "Mini-Boss" -> 0xFFA0561E;
            case "Hostile" -> 0xFF7E2D24;
            case "Neutral" -> 0xFF6E6426;
            case "Passive" -> 0xFF3F7D45;
            case "Summon" -> 0xFF6F4D96;
            case "Hazard" -> 0xFF555555;
            default -> 0xFF5F5F5F;
        };
    }

    /** Draws a small colored chip. Returns next x. Skips null text. */
    private int drawBadge(DrawContext ctx, int x, int y, String text, int color) {
        if (text == null || text.isEmpty()) return x;
        int w = textRenderer.getWidth(text) + 6;
        if (x + w > pageX + pageW) return x; // out of row space — drop rather than overflow
        ctx.fill(x, y, x + w, y + 12, color);
        ctx.fill(x, y, x + w, y + 1, brighten(color));
        ctx.drawTextWithShadow(textRenderer, Text.literal(text), x + 3, y + 2, 0xFFF5EBD0);
        return x + w + 3;
    }

    private static int brighten(int argb) {
        int r = Math.min(255, ((argb >> 16) & 0xFF) + 45);
        int g = Math.min(255, ((argb >> 8) & 0xFF) + 45);
        int b = Math.min(255, (argb & 0xFF) + 45);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /** "Weak to: X, Y" line in a type color. Returns new y (unchanged if no data). */
    private int drawTypeLine(DrawContext ctx, int y, String prefix, String types, int color) {
        if (types == null || types.isEmpty()) return y;
        for (String line : wrapText(prefix + types, pageW)) {
            ctx.drawText(textRenderer, Text.literal(line), pageX, y, color, false);
            y += LINE_HEIGHT;
        }
        return y;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Input
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            // Sidebar clicks only count inside the sidebar viewport
            boolean inSidebarView = mouseX >= bookX && mouseX < bookX + sidebarW
                && mouseY >= bookY + headerH + 3 && mouseY < bookY + bookH - 3;
            List<GuideBookData.Category> cats = GuideBookData.getCategories();
            for (Zone z : zones) {
                if (!z.contains(mouseX, mouseY)) continue;
                switch (z.kind()) {
                    case Z_CAT -> {
                        if (!inSidebarView) continue;
                        if (selectedCategory != z.a()) {
                            selectedCategory = z.a();
                            selectedEntry = cats.get(z.a()).name().equals("Enemy Bestiary") ? -1 : 0;
                            currentPage = 0;
                            bestiaryPage = 0;
                            sidebarScroll = 0;
                        } else if (cats.get(z.a()).name().equals("Enemy Bestiary")) {
                            selectedEntry = -1; // back to grid
                        }
                        return true;
                    }
                    case Z_ENTRY -> {
                        if (!inSidebarView) continue;
                        selectedEntry = z.a();
                        currentPage = 0;
                        return true;
                    }
                    case Z_CELL -> { selectedEntry = z.a(); currentPage = 0; return true; }
                    case Z_PREV -> { currentPage--; return true; }
                    case Z_NEXT -> { currentPage++; return true; }
                    case Z_BACK -> { selectedEntry = -1; currentPage = 0; return true; }
                    case Z_BPREV -> { bestiaryPage--; return true; }
                    case Z_BNEXT -> { bestiaryPage++; return true; }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (mouseX >= bookX && mouseX < bookX + sidebarW
                && mouseY >= bookY && mouseY < bookY + bookH) {
            sidebarScroll = MathHelper.clamp(sidebarScroll - (int) (verticalAmount * 16), 0, maxSidebarScroll);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Arrow keys flip pages
        List<GuideBookData.Category> cats = GuideBookData.getCategories();
        // Bestiary grid view: arrows flip grid pages (upper bound clamped in render)
        if (selectedCategory < cats.size() && selectedEntry < 0
                && cats.get(selectedCategory).name().equals("Enemy Bestiary")) {
            if (keyCode == 263 && bestiaryPage > 0) { bestiaryPage--; return true; } // LEFT
            if (keyCode == 262) { bestiaryPage++; return true; }                     // RIGHT
        }
        if (selectedCategory < cats.size() && selectedEntry >= 0
                && selectedEntry < cats.get(selectedCategory).entries().size()) {
            GuideBookData.Entry entry = cats.get(selectedCategory).entries().get(selectedEntry);
            if (keyCode == 263 && currentPage > 0) { currentPage--; return true; }                       // LEFT
            if (keyCode == 262 && currentPage < entry.pages().size() - 1) { currentPage++; return true; } // RIGHT
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Text helpers
    // ─────────────────────────────────────────────────────────────────────────

    /** Strip leading §l so we can re-apply our own bold/colors cleanly. */
    private static String stripBold(String s) {
        return s.replace("§l", "");
    }

    private String trimToWidth(String s, int maxW) {
        if (textRenderer.getWidth(s) <= maxW) return s;
        String out = s;
        while (out.length() > 2 && textRenderer.getWidth(out + "..") > maxW) {
            out = out.substring(0, out.length() - 1);
        }
        return out + "..";
    }

    /** Word wrap that carries the last §-code onto continuation lines. */
    private List<String> wrapText(String text, int maxWidth) {
        List<String> result = new ArrayList<>();
        String[] words = text.split(" ");
        StringBuilder line = new StringBuilder();
        String carry = "";
        for (String word : words) {
            String test = line.isEmpty() ? word : line + " " + word;
            if (textRenderer.getWidth(test) > maxWidth && !line.isEmpty()) {
                result.add(line.toString());
                carry = lastFormatCode(line.toString(), carry);
                line = new StringBuilder(carry + word);
            } else {
                line = new StringBuilder(test);
            }
        }
        if (!line.isEmpty()) result.add(line.toString());
        return result;
    }

    /** Find the active §-code at the end of a line ("" if none / reset). */
    private static String lastFormatCode(String line, String inherited) {
        String code = inherited;
        for (int i = 0; i < line.length() - 1; i++) {
            if (line.charAt(i) == '§') {
                char c = Character.toLowerCase(line.charAt(i + 1));
                if (c == 'r') code = "";
                else if ((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f')) code = "§" + c;
                else code = code + "§" + c; // style codes stack on color
            }
        }
        return code;
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public boolean shouldCloseOnEsc() { return true; }
}
