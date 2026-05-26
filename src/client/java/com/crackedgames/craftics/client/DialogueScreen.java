package com.crackedgames.craftics.client;

import com.crackedgames.craftics.network.DialogueChoicePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.List;

/**
 * Bottom-of-screen NPC dialogue box shown during non-combat event cinematics.
 *
 * <p>Lines reveal one character at a time (typewriter), playing a randomly-pitched
 * villager voice blip per newly-completed word. Clicking either skips the typewriter
 * to the end of the current line or advances to the next line. Once the LAST line is
 * fully typed, the choice buttons are built; each sends a {@link DialogueChoicePayload}
 * and closes the screen.
 *
 * <p>Uses the same DrawContext-based rendering as other custom screens, with {@link MobHeadTextures}
 * for the speaker portrait (with a colored-square fallback when no head texture exists).
 */
public class DialogueScreen extends Screen {

    private final String speakerId;
    private final List<String> lines;
    private final List<String> choiceLabels;
    private final List<String> choiceActions;

    private int lineIndex = 0;
    private int charsShown = 0;
    private int tickCounter = 0;
    private int lastWordCount = 0;
    private boolean lineComplete = false;

    private static final int CHARS_PER_TICK_DIV = 1; // reveal one char per tick (~2x faster)
    private static final int BOX_H = 60;
    private static final int PORTRAIT = 48;
    private static final int PORTRAIT_PAD = 4; // backing-panel padding around the portrait
    private static final int BOX_MARGIN = 20;
    private static final int BOX_BOTTOM_GAP = 20;

    private static final int BOX_BG = 0xEE0D0F14;
    private static final int BOX_BORDER = 0xFF4A5468;

    public DialogueScreen(String speakerId, List<String> lines,
                          List<String> choiceLabels, List<String> choiceActions) {
        super(Text.literal("Dialogue"));
        this.speakerId = speakerId;
        this.lines = lines;
        this.choiceLabels = choiceLabels;
        this.choiceActions = choiceActions;
        // A dialogue with no lines is still "complete" so choices show immediately.
        if (lines.isEmpty()) {
            this.lineComplete = true;
        }
    }

    private boolean onLastLine() { return lineIndex >= lines.size() - 1; }

    private String currentLine() {
        if (lines.isEmpty()) return "";
        return lines.get(Math.min(lineIndex, lines.size() - 1));
    }

    @Override
    protected void init() {
        rebuildChoices();
    }

    private void rebuildChoices() {
        this.clearChildren();
        if (!(onLastLine() && lineComplete) || choiceLabels.isEmpty()) return;

        int n = choiceLabels.size();
        // Mirror the box top computed in render() so buttons sit above the box.
        int boxTop = this.height - BOX_BOTTOM_GAP - BOX_H;
        int avail = this.width - 2 * BOX_MARGIN;
        int gap = 6;
        int btnW = Math.min(150, avail / Math.max(1, Math.min(n, 3)));
        int perRow = Math.max(1, avail / (btnW + gap));
        int totalRows = (n + perRow - 1) / perRow;

        for (int i = 0; i < n; i++) {
            int row = i / perRow;
            int col = i % perRow;
            int rowCount = Math.min(perRow, n - row * perRow);
            int rowW = rowCount * btnW + (rowCount - 1) * gap;
            int x = (this.width - rowW) / 2 + col * (btnW + gap);
            // Rows stack UPWARD: the last row sits just above the box, earlier rows higher.
            int y = boxTop - 6 - (totalRows - row) * 24;
            final String action = choiceActions.get(i);
            this.addDrawableChild(ButtonWidget.builder(
                    Text.literal(choiceLabels.get(i)),
                    b -> choose(action))
                .dimensions(x, y, btnW, 20).build());
        }
    }

    private void choose(String action) {
        ClientPlayNetworking.send(new DialogueChoicePayload(action));
        this.close();
    }

    @Override
    public void tick() {
        if (lineComplete) return;
        tickCounter++;
        if (tickCounter % CHARS_PER_TICK_DIV == 0 && charsShown < currentLine().length()) {
            charsShown++;
            String shown = currentLine().substring(0, charsShown);
            String trimmed = shown.trim();
            int words = trimmed.isEmpty() ? 0 : trimmed.split("\\s+").length;
            if (words > lastWordCount) {
                lastWordCount = words;
                playVoice();
            }
            if (charsShown >= currentLine().length()) {
                lineComplete = true;
                rebuildChoices();
            }
        }
    }

    private void playVoice() {
        if (this.client == null || this.client.world == null) return;
        float pitch = 0.9f + this.client.world.random.nextFloat() * 0.3f;
        net.minecraft.sound.SoundEvent voice = isNarrator()
            ? net.minecraft.sound.SoundEvents.UI_BUTTON_CLICK.value() // neutral narrator blip
            : net.minecraft.sound.SoundEvents.ENTITY_VILLAGER_AMBIENT;
        this.client.getSoundManager().play(PositionedSoundInstance.master(voice, pitch));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (!lineComplete) {
                // First click: skip the typewriter to the end of this line.
                charsShown = currentLine().length();
                lineComplete = true;
                rebuildChoices();
                return true;
            } else if (!onLastLine()) {
                // Click on a finished non-last line: advance.
                lineIndex++;
                charsShown = 0;
                lastWordCount = 0;
                lineComplete = false;
                tickCounter = 0; // reset cadence for the new line
                return true;
            } else if (onLastLine() && choiceLabels.isEmpty()) {
                ClientPlayNetworking.send(new DialogueChoicePayload(DialogueChoicePayload.ACTION_DISMISS));
                this.close();
                return true;
            }
            // On the last finished line, the choice buttons handle clicks.
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** Background mode:
     *  <ul>
     *    <li>Event-area dialogues (trader, enchanter, traveler, vault, etc.)
     *        run inside an event cinematic — the player was teleported into a
     *        scene built for them and should still see it behind the box.</li>
     *    <li>Mid-combat dialogues should keep the arena visible for the same
     *        reason — losing the battlefield is jarring.</li>
     *    <li>Pre-level intros (boss intros, trial vote, shiny vote, ambush
     *        warnings) fire between levels with no cinematic and no active
     *        combat. The stale previous arena would blur behind the box if we
     *        let it show through, so we paint solid black for focus.</li>
     *  </ul> */
    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        boolean keepScenery = CombatState.isCinematicActive() || CombatState.isInCombat();
        if (keepScenery) {
            super.renderBackground(ctx, mouseX, mouseY, delta);
        } else {
            ctx.fill(0, 0, this.width, this.height, 0xFF000000);
        }
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);

        int boxX = BOX_MARGIN;
        int boxW = this.width - 2 * BOX_MARGIN;
        int boxY = this.height - BOX_BOTTOM_GAP - BOX_H;

        boolean narrator = isNarrator();

        if (!narrator) {
            // Speaker portrait resting on the box's top-right edge.
            int px = boxX + boxW - PORTRAIT - 8;
            // Portrait rests on (overlaps) the box's top edge by a few px (layout B).
            int py = boxY - PORTRAIT + 6;
            // While actively talking (line still typing), give the portrait a gentle
            // vertical bob; hold still once the line is complete.
            int portraitYOff = 0;
            if (!lineComplete) {
                portraitYOff = (int) Math.round(Math.sin(System.currentTimeMillis() / 90.0) * 2.0);
            }
            int portraitY = py + portraitYOff;
            int panelX = px - PORTRAIT_PAD;
            int panelY = portraitY - PORTRAIT_PAD;
            int panelW = PORTRAIT + 2 * PORTRAIT_PAD;

            // Portrait backing panel: a small extension that only occupies the strip ABOVE
            // the box's top edge. The box background is translucent, so drawing the panel
            // behind the box would show through (and shift as the portrait bobs). Instead we
            // clip the panel to [panelY, boxY) and let it merge seamlessly into the box top.
            int panelBottom = boxY; // meet the box's top edge exactly
            if (panelY < panelBottom) {
                ctx.fill(panelX, panelY, panelX + panelW, panelBottom, BOX_BG);
                ctx.fill(panelX, panelY, panelX + panelW, panelY + 1, BOX_BORDER);            // top
                ctx.fill(panelX, panelY, panelX + 1, panelBottom, BOX_BORDER);                // left
                ctx.fill(panelX + panelW - 1, panelY, panelX + panelW, panelBottom, BOX_BORDER); // right
            }

            // Box background + border with the panel seam.
            ctx.fill(boxX, boxY, boxX + boxW, boxY + BOX_H, BOX_BG);
            ctx.fill(boxX, boxY + BOX_H - 1, boxX + boxW, boxY + BOX_H, BOX_BORDER); // bottom
            ctx.fill(boxX, boxY, boxX + 1, boxY + BOX_H, BOX_BORDER);               // left
            ctx.fill(boxX + boxW - 1, boxY, boxX + boxW, boxY + BOX_H, BOX_BORDER); // right
            boolean panelAbove = panelY < boxY;
            int seamL = panelX + 1, seamR = panelX + panelW - 1;
            if (panelAbove) {
                ctx.fill(boxX, boxY, seamL, boxY + 1, BOX_BORDER);            // top, left of panel
                ctx.fill(seamR, boxY, boxX + boxW, boxY + 1, BOX_BORDER);     // top, right of panel
            } else {
                ctx.fill(boxX, boxY, boxX + boxW, boxY + 1, BOX_BORDER);      // full top (no panel above)
            }

            // Portrait drawn last so it sits on top of both panel and box.
            Identifier headTex = MobHeadTextures.get(speakerId);
            if (headTex != null) {
                MobHeadTextures.drawMobHead(ctx, headTex, px, portraitY, PORTRAIT);
            } else {
                ctx.fill(px, portraitY, px + PORTRAIT, portraitY + PORTRAIT, MobHeadTextures.getMobColor(speakerId));
                drawBorderRect(ctx, px, portraitY, PORTRAIT, PORTRAIT, BOX_BORDER);
            }

            // Speaker name inside the box.
            ctx.drawTextWithShadow(this.textRenderer,
                Text.literal("§e" + speakerDisplayName()), boxX + 8, boxY + 6, 0xFFFFAA);
        } else {
            // Narrator: plain box, no portrait, panel, or name.
            ctx.fill(boxX, boxY, boxX + boxW, boxY + BOX_H, BOX_BG);
            drawBorderRect(ctx, boxX, boxY, boxW, BOX_H, BOX_BORDER);
        }

        // Current line (revealed prefix). While typing, the most recently revealed
        // characters wiggle a little as they're placed, settling as they age. Once
        // the line is fully typed it renders perfectly still.
        String shown = currentLine().substring(0, Math.min(charsShown, currentLine().length()));
        // The per-character wiggle draws each char separately, which would render a
        // raw "§" and mis-advance x if a line contained formatting codes. Trader lines
        // are plain text, but for any future formatted dialogue fall back to the static
        // draw so colors render correctly and text never drifts.
        if (lineComplete || shown.indexOf('§') >= 0) {
            ctx.drawTextWithShadow(this.textRenderer,
                Text.literal(shown), boxX + 8, boxY + 22, 0xFFFFFFFF);
        } else {
            int x = boxX + 8;
            int baseY = boxY + 22;
            long timeMs = System.currentTimeMillis();
            for (int i = 0; i < shown.length(); i++) {
                String ch = String.valueOf(shown.charAt(i));
                int age = charsShown - 1 - i; // 0 = newest char
                double yOff = 0;
                if (age >= 0 && age < 4) {
                    double decay = (4 - age) / 4.0;                    // 1.0 newest -> ~0 older
                    yOff = Math.sin(timeMs / 60.0 + i) * 1.5 * decay;  // tiny wiggle, settles with age
                }
                ctx.drawTextWithShadow(this.textRenderer,
                    Text.literal(ch), x, baseY + (int) Math.round(yOff), 0xFFFFFFFF);
                x += this.textRenderer.getWidth(ch);
            }
        }

        // "click to continue" hint while choices aren't yet shown.
        if (choiceLabels.isEmpty() || !(onLastLine() && lineComplete)) {
            ctx.drawTextWithShadow(this.textRenderer,
                Text.literal("§8(click to continue)"),
                boxX + boxW - 110, boxY + BOX_H - 12, 0xFF888888);
        }
    }

    /** Draw a 1px rectangular border using 4 fills (version-portable). */
    private static void drawBorderRect(DrawContext ctx, int x, int y, int w, int h, int color) {
        ctx.fill(x, y, x + w, y + 1, color);             // top
        ctx.fill(x, y + h - 1, x + w, y + h, color);     // bottom
        ctx.fill(x, y, x + 1, y + h, color);             // left
        ctx.fill(x + w - 1, y, x + w, y + h, color);     // right
    }

    private boolean isNarrator() { return speakerId == null || speakerId.isEmpty(); }

    private String speakerDisplayName() {
        try {
            Identifier id = Identifier.of(speakerId);
            if (!Registries.ENTITY_TYPE.containsId(id)) return "???";
            return Registries.ENTITY_TYPE.get(id).getName().getString();
        } catch (Exception e) {
            return "???";
        }
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public boolean shouldCloseOnEsc() { return false; }
}
