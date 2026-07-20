package com.crackedgames.craftics.client.bugreport;

import com.crackedgames.craftics.CrafticsMod;
import com.crackedgames.craftics.client.guide.GuideButton;
import com.crackedgames.craftics.client.guide.GuideTheme;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * The {@code /bugreport} form: title, summary, recent screenshots to attach,
 * and an "attach log" toggle. Submits through {@link BugReportSender} to the
 * Craftics website, which posts the report into the Discord bug-report forum.
 *
 * <p>Text input is a deliberately tiny inline editor (two focusable boxes,
 * paste support, backspace, enter) instead of {@code EditBoxWidget} - the
 * vanilla text widgets changed constructors across the 1.21.x shards this
 * source compiles against, and we only need the basics.
 */
public class BugReportScreen extends Screen {

    private static final int PANEL_W = 320;
    private static final int TITLE_MAX = 100;
    private static final int SUMMARY_MAX = 2000;
    private static final int MAX_SHOTS_LISTED = 5;
    private static final int MAX_SHOTS_ATTACHED = 3;
    private static final int SUMMARY_VISIBLE_LINES = 6;

    private enum Focus { NONE, TITLE, SUMMARY }
    private enum State { EDITING, SENDING, SENT, FAILED }

    private final StringBuilder title = new StringBuilder();
    private final StringBuilder summary = new StringBuilder();
    private Focus focus = Focus.TITLE;
    private State state = State.EDITING;
    private String status = "";
    private int closeTicks = -1;

    private final List<File> shots = new ArrayList<>();
    private final boolean[] shotSelected = new boolean[MAX_SHOTS_LISTED];
    private boolean attachLog = true;

    private ButtonWidget sendButton;

    // Laid out in render(); consumed by mouseClicked().
    private int panelX, panelY, panelH;
    private int titleBoxY, summaryBoxY, shotsListY, logToggleY;

    public BugReportScreen() {
        super(Text.literal("Report a Bug"));
    }

    @Override
    protected void init() {
        shots.clear();
        File dir = new File(this.client.runDirectory, "screenshots");
        File[] files = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".png"));
        if (files != null) {
            Arrays.sort(files, Comparator.comparingLong(File::lastModified).reversed());
            for (int i = 0; i < files.length && i < MAX_SHOTS_LISTED; i++) {
                shots.add(files[i]);
            }
        }

        panelH = 96 + 14                         // header + title row
            + SUMMARY_VISIBLE_LINES * 10 + 14    // summary box
            + Math.max(1, shots.size()) * 11     // screenshot rows
            + 30;                                // log toggle + status
        panelX = (this.width - PANEL_W) / 2;
        panelY = Math.max(8, (this.height - panelH - 30) / 2);

        int btnY = panelY + panelH + 8;
        int btnW = 112, gap = 10, cx = this.width / 2;
        sendButton = GuideButton.of(cx - btnW - gap / 2, btnY, btnW, 20,
            Text.literal("§a✔ Send Report"), b -> submit());
        this.addDrawableChild(sendButton);
        this.addDrawableChild(GuideButton.of(cx + gap / 2, btnY, btnW, 20,
            Text.literal("§7Cancel"), b -> this.close()));
    }

    // === Submit ============================================================

    private void submit() {
        if (state != State.EDITING) return;
        if (title.toString().trim().length() < 4) {
            status = "§cGive the report a short title first.";
            return;
        }
        if (summary.toString().trim().length() < 10) {
            status = "§cDescribe what happened in the summary.";
            return;
        }
        String endpoint = CrafticsMod.CONFIG.bugReportEndpoint();
        if (endpoint == null || endpoint.isBlank() || !endpoint.startsWith("http")) {
            status = "§cNo report endpoint configured (Craftics config).";
            return;
        }
        List<File> attached = new ArrayList<>();
        for (int i = 0; i < shots.size(); i++) {
            if (shotSelected[i]) attached.add(shots.get(i));
        }
        state = State.SENDING;
        status = "§6Sending...";
        sendButton.active = false;
        BugReportSender.sendAsync(endpoint, title.toString().trim(), summary.toString().trim(),
            attached, attachLog, result -> {
                if (result.sent()) {
                    state = State.SENT;
                    status = "§a" + result.detail();
                    closeTicks = 50;
                } else {
                    state = State.FAILED;
                    status = "§c" + result.detail();
                    sendButton.active = true;
                }
            });
    }

    @Override
    public void tick() {
        super.tick();
        if (closeTicks > 0 && --closeTicks == 0) this.close();
        // A failed report may be retried after fixing connectivity.
        if (state == State.FAILED) state = State.EDITING;
    }

    // === Input =============================================================

    private StringBuilder focused() {
        return focus == Focus.TITLE ? title : focus == Focus.SUMMARY ? summary : null;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        StringBuilder target = focused();
        if (state == State.EDITING && target != null && chr >= ' ' && chr != '§') {
            int max = focus == Focus.TITLE ? TITLE_MAX : SUMMARY_MAX;
            if (target.length() < max) target.append(chr);
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        StringBuilder target = focused();
        if (state == State.EDITING && target != null) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (target.length() > 0) {
                    int cut = hasControlDown() ? wordStart(target) : target.length() - 1;
                    target.delete(cut, target.length());
                }
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_TAB) {
                focus = focus == Focus.TITLE ? Focus.SUMMARY : Focus.TITLE;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                if (focus == Focus.TITLE) {
                    focus = Focus.SUMMARY;
                } else if (summary.length() < SUMMARY_MAX) {
                    summary.append('\n');
                }
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_V && hasControlDown()) {
                String clip = this.client.keyboard.getClipboard();
                if (clip != null) {
                    int max = focus == Focus.TITLE ? TITLE_MAX : SUMMARY_MAX;
                    String cleaned = clip.replace("§", "").replace("\r", "");
                    if (focus == Focus.TITLE) cleaned = cleaned.replace("\n", " ");
                    target.append(cleaned, 0, Math.min(cleaned.length(), max - target.length()));
                }
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private static int wordStart(StringBuilder sb) {
        int i = sb.length() - 1;
        while (i > 0 && sb.charAt(i - 1) != ' ' && sb.charAt(i - 1) != '\n') i--;
        return Math.max(0, i);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (state == State.EDITING && button == 0) {
            int innerX = panelX + 12, innerW = PANEL_W - 24;
            if (in(mouseX, mouseY, innerX, titleBoxY, innerW, 14)) {
                focus = Focus.TITLE;
                return true;
            }
            if (in(mouseX, mouseY, innerX, summaryBoxY, innerW, SUMMARY_VISIBLE_LINES * 10 + 4)) {
                focus = Focus.SUMMARY;
                return true;
            }
            for (int i = 0; i < shots.size(); i++) {
                if (in(mouseX, mouseY, innerX, shotsListY + i * 11, innerW, 11)) {
                    if (!shotSelected[i] && selectedCount() >= MAX_SHOTS_ATTACHED) {
                        status = "§cAt most " + MAX_SHOTS_ATTACHED + " screenshots.";
                    } else {
                        shotSelected[i] = !shotSelected[i];
                    }
                    return true;
                }
            }
            if (in(mouseX, mouseY, innerX, logToggleY, innerW, 11)) {
                attachLog = !attachLog;
                return true;
            }
            focus = Focus.NONE;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private int selectedCount() {
        int n = 0;
        for (boolean b : shotSelected) if (b) n++;
        return n;
    }

    // === Render ============================================================

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, this.width, this.height, 0xB0000000);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        GuideTheme.drawPanel(ctx, panelX, panelY, PANEL_W, panelH);

        int cx = this.width / 2;
        int x = panelX + 12, w = PANEL_W - 24;
        int y = panelY + 10;
        boolean blink = (System.currentTimeMillis() / 400) % 2 == 0;

        GuideTheme.drawCentered(ctx, this.textRenderer, "§lReport a Bug", cx, y, GuideTheme.GOLD);
        y += 12;
        GuideTheme.drawRule(ctx, x, y, w);
        y += 8;

        // Title
        GuideTheme.drawInk(ctx, this.textRenderer, "Title", x, y, GuideTheme.INK_SOFT);
        y += 10;
        titleBoxY = y;
        drawBox(ctx, x, y, w, 14, focus == Focus.TITLE);
        String titleShown = tail(title.toString().replace('\n', ' '), w - 10);
        if (focus == Focus.TITLE && blink && state == State.EDITING) titleShown += "_";
        GuideTheme.drawInk(ctx, this.textRenderer, titleShown, x + 4, y + 3, GuideTheme.INK);
        y += 20;

        // Summary
        GuideTheme.drawInk(ctx, this.textRenderer, "What happened?", x, y, GuideTheme.INK_SOFT);
        y += 10;
        summaryBoxY = y;
        int sumH = SUMMARY_VISIBLE_LINES * 10 + 4;
        drawBox(ctx, x, y, w, sumH, focus == Focus.SUMMARY);
        List<String> lines = wrap(summary.toString(), w - 10);
        if (focus == Focus.SUMMARY && blink && state == State.EDITING) {
            if (lines.isEmpty()) lines = List.of("_");
            else {
                List<String> copy = new ArrayList<>(lines);
                copy.set(copy.size() - 1, copy.get(copy.size() - 1) + "_");
                lines = copy;
            }
        }
        int from = Math.max(0, lines.size() - SUMMARY_VISIBLE_LINES);
        for (int i = from; i < lines.size(); i++) {
            GuideTheme.drawInk(ctx, this.textRenderer, lines.get(i), x + 4, y + 3 + (i - from) * 10, GuideTheme.INK);
        }
        y += sumH + 6;

        // Screenshots
        GuideTheme.drawInk(ctx, this.textRenderer,
            "Attach screenshots  §7(F2 in-game takes one, reopen to attach)", x, y, GuideTheme.INK_SOFT);
        y += 10;
        shotsListY = y;
        if (shots.isEmpty()) {
            GuideTheme.drawInk(ctx, this.textRenderer, "§oNo screenshots yet.", x + 4, y + 1, GuideTheme.INK_FAINT);
            y += 11;
        } else {
            for (int i = 0; i < shots.size(); i++) {
                File f = shots.get(i);
                boolean sel = shotSelected[i];
                String mark = sel ? "§6☑ " : "§7☐ ";
                String label = mark + "§r" + tail(f.getName(), w - 70)
                    + " §7(" + age(f.lastModified()) + ")";
                GuideTheme.drawInk(ctx, this.textRenderer, label, x + 4, y + 1,
                    sel ? GuideTheme.INK : GuideTheme.INK_FAINT);
                y += 11;
            }
        }
        y += 4;

        // Log toggle
        logToggleY = y;
        GuideTheme.drawInk(ctx, this.textRenderer,
            (attachLog ? "§6☑ " : "§7☐ ") + "§rAttach game log §7(recent lines of latest.log)",
            x + 4, y + 1, GuideTheme.INK);
        y += 13;

        // Status
        if (!status.isEmpty()) {
            GuideTheme.drawCentered(ctx, this.textRenderer, status, cx, y, GuideTheme.INK);
        } else {
            GuideTheme.drawCentered(ctx, this.textRenderer,
                "§7Sends title, summary, attachments and mod/version info.", cx, y, GuideTheme.INK_FAINT);
        }
    }

    private void drawBox(DrawContext ctx, int x, int y, int w, int h, boolean focused) {
        ctx.fill(x - 1, y - 1, x + w + 1, y + h + 1, focused ? GuideTheme.GOLD_DIM : GuideTheme.RULE);
        ctx.fill(x, y, x + w, y + h, 0xFFF4E9C8);
    }

    /** Word-wrap {@code text} (respecting explicit newlines) to {@code maxWidth} px. */
    private List<String> wrap(String text, int maxWidth) {
        List<String> out = new ArrayList<>();
        for (String para : text.split("\n", -1)) {
            String line = "";
            for (String word : para.split(" ", -1)) {
                String candidate = line.isEmpty() ? word : line + " " + word;
                if (this.textRenderer.getWidth(candidate) <= maxWidth) {
                    line = candidate;
                    continue;
                }
                if (!line.isEmpty()) out.add(line);
                // Hard-break words wider than the box.
                while (this.textRenderer.getWidth(word) > maxWidth) {
                    int cut = 1;
                    while (cut < word.length()
                        && this.textRenderer.getWidth(word.substring(0, cut + 1)) <= maxWidth) cut++;
                    out.add(word.substring(0, cut));
                    word = word.substring(cut);
                }
                line = word;
            }
            out.add(line);
        }
        // Trim the trailing empty line unless the text genuinely ends with \n.
        if (!out.isEmpty() && out.get(out.size() - 1).isEmpty() && !text.endsWith("\n")) {
            out.remove(out.size() - 1);
        }
        return out;
    }

    /** Rightmost slice of {@code s} that fits {@code maxWidth} px (keeps the typing end visible). */
    private String tail(String s, int maxWidth) {
        if (this.textRenderer.getWidth(s) <= maxWidth) return s;
        int from = 0;
        while (from < s.length() && this.textRenderer.getWidth(s.substring(from)) > maxWidth) from++;
        return s.substring(from);
    }

    private static String age(long lastModified) {
        long mins = Math.max(0, (System.currentTimeMillis() - lastModified) / 60000L);
        if (mins < 1) return "just now";
        if (mins < 60) return mins + "m ago";
        long hours = mins / 60;
        if (hours < 24) return hours + "h ago";
        return (hours / 24) + "d ago";
    }

    @Override public boolean shouldPause() { return false; }
    @Override public boolean shouldCloseOnEsc() { return state != State.SENDING; }
}
