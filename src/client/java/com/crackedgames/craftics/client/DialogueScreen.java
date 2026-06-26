package com.crackedgames.craftics.client;

import com.crackedgames.craftics.client.guide.GuideButton;
import com.crackedgames.craftics.client.guide.GuideTheme;
import com.crackedgames.craftics.network.DialogueChoicePayload;
import com.crackedgames.craftics.network.DialoguePayload;
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
    /** Server-declared backdrop mode (DialoguePayload.BG_*). */
    private final int background;

    private int lineIndex = 0;
    private int charsShown = 0;
    private int tickCounter = 0;
    private int lastWordCount = 0;
    private boolean lineComplete = false;

    // --- Piglin barter stepper state ---------------------------------------
    // The barter intro narration arrives as a normal DialoguePayload (opening a
    // DialogueScreen) while the +/- offer context arrives as a separate
    // BarterContextPayload. Because either packet can land first, the context is
    // parked in these static "pending" fields by the receiver; init() picks it up
    // when the screen is (re)built. applyBarterContext(...) lets the receiver push
    // fresh context onto an already-open screen and re-init it.
    private static boolean pendingBarterActive = false;
    private static int pendingBarterGold = 0;
    private static int pendingBarterMaxOffer = 0;

    private boolean barterActive = false;
    private int barterGold = 0;
    private int barterMaxOffer = 0;
    private int barterOffer = 1;
    private ButtonWidget barterMinusButton;
    private ButtonWidget barterPlusButton;
    private ButtonWidget barterOfferButton;
    private ButtonWidget barterWalkButton;

    private static final int CHARS_PER_TICK_DIV = 1; // reveal one char per tick (~2x faster)
    private static final int BOX_H = 60;
    private static final int PORTRAIT = 48;
    private static final int PORTRAIT_PAD = 4; // backing-panel padding around the portrait
    private static final int BOX_MARGIN = 20;
    private static final int BOX_BOTTOM_GAP = 20;

    public DialogueScreen(String speakerId, List<String> lines,
                          List<String> choiceLabels, List<String> choiceActions) {
        this(speakerId, lines, choiceLabels, choiceActions,
            DialoguePayload.BG_AUTO);
    }

    public DialogueScreen(String speakerId, List<String> lines,
                          List<String> choiceLabels, List<String> choiceActions,
                          int background) {
        super(Text.literal("Dialogue"));
        this.speakerId = speakerId;
        this.lines = lines;
        this.choiceLabels = choiceLabels;
        this.choiceActions = choiceActions;
        this.background = background;
        // A dialogue with no lines is still "complete" so choices show immediately.
        if (lines.isEmpty()) {
            this.lineComplete = true;
        }
    }

    /** Park barter context from the S2C receiver so the next-built DialogueScreen adopts it. */
    public static void setPendingBarterContext(int gold, int maxOffer) {
        pendingBarterActive = true;
        pendingBarterGold = gold;
        pendingBarterMaxOffer = maxOffer;
    }

    /** Clear parked barter context (e.g. the offer resolved / stepper should not show). */
    public static void clearPendingBarterContext() {
        pendingBarterActive = false;
        pendingBarterGold = 0;
        pendingBarterMaxOffer = 0;
    }

    private boolean onLastLine() { return lineIndex >= lines.size() - 1; }

    private String currentLine() {
        if (lines.isEmpty()) return "";
        return lines.get(Math.min(lineIndex, lines.size() - 1));
    }

    @Override
    protected void init() {
        // Adopt the barter context that arrived (before or after the intro narration
        // that opened this screen) from the BarterContextPayload receiver. Mirror the
        // parked active state in BOTH directions: a result/leave line clears the parked
        // context, and rebuilding the screen for that line must drop out of barter mode
        // so the choiceless dismiss can fire. Retaining a stale true here was half of the
        // softlock that kept the event from ever finalizing.
        this.barterActive = pendingBarterActive;
        if (pendingBarterActive) {
            this.barterGold = pendingBarterGold;
            this.barterMaxOffer = pendingBarterMaxOffer;
        }
        rebuildChoices();
    }

    /**
     * Push fresh barter context onto an already-open DialogueScreen (e.g. the intro
     * narration opened this screen first, then the BarterContextPayload landed). Stores
     * the values and re-inits so the stepper widgets appear/update immediately.
     */
    public void applyBarterContext(boolean active, int gold, int maxOffer) {
        this.barterActive = active;
        this.barterGold = gold;
        this.barterMaxOffer = maxOffer;
        this.clearChildren();
        this.init();
    }

    /** Effective upper bound on the offer: never above the player's gold or the server cap. */
    private int barterCap() {
        return Math.max(1, Math.min(barterMaxOffer, barterGold));
    }

    private void initBarterStepper() {
        int cap = barterCap();
        barterOffer = Math.max(1, Math.min(barterOffer, cap));

        boolean hasGold = barterGold > 0;

        // Lay the stepper out fully above the dialogue box.
        // Keep both rows clear of the box so labels/text never overlap buttons.
        int btnSize = 20;
        int valueW = 60; // reserved space between - and + for the offer number
        int gap = 6;
        int offerBtnW = 90;
        int walkBtnW = 90;
        int boxTop = this.height - BOX_BOTTOM_GAP - BOX_H;
        // Lift the action row enough that the "you have N gold" line drawn beneath it
        // still clears the dialogue box top instead of clipping into it. The gold line
        // needs ~11px; reserve that gap above the box.
        int actionY = boxTop - btnSize - 13;
        int rowY = actionY - btnSize - gap;

        // Centered cluster:  [-] (value) [+]
        int clusterW = btnSize + gap + valueW + gap + btnSize;
        int clusterX = (this.width - clusterW) / 2;

        int minusX = clusterX;
        int plusX = clusterX + btnSize + gap + valueW + gap;

        barterMinusButton = GuideButton.of(minusX, rowY, btnSize, btnSize,
            Text.literal("-"), b -> adjustOffer(-1));
        barterPlusButton = GuideButton.of(plusX, rowY, btnSize, btnSize,
            Text.literal("+"), b -> adjustOffer(1));

        // Action buttons one row below the stepper (still above the box).
        int actionsW = offerBtnW + gap + walkBtnW;
        int actionsX = (this.width - actionsW) / 2;
        barterOfferButton = GuideButton.of(actionsX, actionY, offerBtnW, btnSize,
            Text.literal("Offer Gold"), b -> submitOffer());
        barterWalkButton = GuideButton.of(actionsX + offerBtnW + gap, actionY, walkBtnW, btnSize,
            Text.literal("Walk away"), b -> choose("barter:leave"));

        // With no gold the player can only walk away.
        barterMinusButton.active = hasGold;
        barterPlusButton.active = hasGold;
        barterOfferButton.active = hasGold;

        this.addDrawableChild(barterMinusButton);
        this.addDrawableChild(barterPlusButton);
        this.addDrawableChild(barterOfferButton);
        this.addDrawableChild(barterWalkButton);
    }

    private void adjustOffer(int delta) {
        int cap = barterCap();
        barterOffer = Math.max(1, Math.min(barterOffer + delta, cap));
    }

    private void submitOffer() {
        choose("barter:offer:" + barterOffer);
    }

    private boolean isOverBarterWidget(double mx, double my) {
        return overWidget(barterMinusButton, mx, my)
            || overWidget(barterPlusButton, mx, my)
            || overWidget(barterOfferButton, mx, my)
            || overWidget(barterWalkButton, mx, my);
    }

    private static boolean overWidget(ButtonWidget w, double mx, double my) {
        return w != null
            && mx >= w.getX() && mx < w.getX() + w.getWidth()
            && my >= w.getY() && my < w.getY() + w.getHeight();
    }

    private void rebuildChoices() {
        this.clearChildren();
        // The barter stepper is independent of the typewriter/choice state: it must
        // survive every children rebuild (typewriter skip, line advance, last-line
        // completion), so re-add it on each rebuild while a barter is active.
        if (barterActive) {
            initBarterStepper();
        }
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
            this.addDrawableChild(GuideButton.of(x, y, btnW, 20,
                Text.literal(choiceLabels.get(i)),
                b -> choose(action)));
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
        // Give the barter stepper widgets first crack at the click so they work even
        // while the intro line is still typing (a click on the box still skips the
        // typewriter via the fall-through below).
        if (barterActive && button == 0 && isOverBarterWidget(mouseX, mouseY)) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
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
            } else if (onLastLine() && choiceLabels.isEmpty() && !barterActive) {
                // In barter mode the stepper buttons drive submission/leaving, so a
                // click on the finished last line must NOT auto-dismiss the screen.
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
     *        run inside an event cinematic - the player was teleported into a
     *        scene built for them and should still see it behind the box.</li>
     *    <li>Mid-combat dialogues should keep the arena visible for the same
     *        reason - losing the battlefield is jarring.</li>
     *    <li>Pre-level intros (boss intros, trial vote, shiny vote, ambush
     *        warnings) fire between levels with no cinematic and no active
     *        combat. The stale previous arena would blur behind the box if we
     *        let it show through, so we paint solid black for focus.</li>
     *  </ul>
     *  <p>The server declares the intended mode via {@code DialoguePayload.background}.
     *  We honor that explicitly because the client's own combat/cinematic flags are
     *  unreliable at the moment a pre-level intro opens: the boss intro is sent
     *  during the transition out of the just-finished fight, BEFORE the ExitCombat
     *  packet clears {@code inCombat}, so the old auto-only heuristic saw "still in
     *  combat" and blurred the dead arena through. {@code BG_AUTO} keeps the legacy
     *  heuristic for any caller that doesn't specify. */
    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        boolean keepScenery = switch (background) {
            case DialoguePayload.BG_SCENERY -> true;
            case DialoguePayload.BG_SOLID -> false;
            default -> CombatState.isCinematicActive() || CombatState.isInCombat();
        };
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

        // Main dialogue box: a parchment panel. drawPanel draws its leather bevel 4px
        // OUTSIDE the rect, so the BOX_MARGIN/BOX_BOTTOM_GAP (20px) margins leave plenty
        // of room on the sides and bottom.
        GuideTheme.drawPanel(ctx, boxX, boxY, boxW, BOX_H);

        if (!narrator) {
            // Speaker portrait resting on the box's top-right edge.
            int px = boxX + boxW - PORTRAIT - 8;
            // Portrait rests just above the box's top edge.
            int py = boxY - PORTRAIT + 6;
            // While actively talking (line still typing), give the portrait a gentle
            // vertical bob; hold still once the line is complete.
            int portraitYOff = 0;
            if (!lineComplete) {
                portraitYOff = (int) Math.round(Math.sin(System.currentTimeMillis() / 90.0) * 2.0);
            }
            int portraitY = py + portraitYOff;

            // Portrait backing: its own small parchment panel framing the portrait. The
            // drawPanel bevel won't merge seamlessly with the box panel below, so the two
            // sit as cleanly separated adjacent parchment frames (intentional look). Inset
            // the panel rect by PORTRAIT_PAD so the leather/gold frame surrounds the image.
            GuideTheme.drawPanel(ctx, px + PORTRAIT_PAD, portraitY + PORTRAIT_PAD,
                PORTRAIT - 2 * PORTRAIT_PAD, PORTRAIT - 2 * PORTRAIT_PAD);

            // Portrait drawn last so it sits on top of its panel.
            Identifier headTex = MobHeadTextures.get(speakerId);
            if (headTex != null) {
                MobHeadTextures.drawMobHead(ctx, headTex, px, portraitY, PORTRAIT);
            } else {
                ctx.fill(px, portraitY, px + PORTRAIT, portraitY + PORTRAIT, MobHeadTextures.getMobColor(speakerId));
            }

            // Speaker name inside the box.
            GuideTheme.drawInk(ctx, this.textRenderer,
                speakerDisplayName(), boxX + 8, boxY + 6, GuideTheme.GOLD);
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
            GuideTheme.drawInk(ctx, this.textRenderer,
                shown, boxX + 8, boxY + 22, GuideTheme.INK);
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
                GuideTheme.drawInk(ctx, this.textRenderer,
                    ch, x, baseY + (int) Math.round(yOff), GuideTheme.INK);
                x += this.textRenderer.getWidth(ch);
            }
        }

        // "click to continue" hint while choices aren't yet shown.
        if (choiceLabels.isEmpty() || !(onLastLine() && lineComplete)) {
            GuideTheme.drawInk(ctx, this.textRenderer,
                "(click to continue)",
                boxX + boxW - 110, boxY + BOX_H - 12, GuideTheme.INK_FAINT);
        }

        if (barterActive) {
            renderBarterStepper(ctx);
        }
    }

    /** Labels + the current offer value for the barter stepper, aligned to its widgets. */
    private void renderBarterStepper(DrawContext ctx) {
        if (barterMinusButton == null) return;

        // The stepper widgets were positioned in initBarterStepper(); derive the text
        // anchors from them so label and value always line up with the buttons.
        int rowY = barterMinusButton.getY();
        int centerX = this.width / 2;

        // "YOUR OFFER" heading sits just above the stepper row.
        GuideTheme.drawCentered(ctx, this.textRenderer,
            "YOUR OFFER", centerX, rowY - 11, GuideTheme.GOLD);

        // Current offer value centered between the - and + buttons.
        GuideTheme.drawCentered(ctx, this.textRenderer,
            String.valueOf(barterOffer), centerX, rowY + 6, GuideTheme.INK);

        // Player's gold count below the action buttons.
        int goldY = barterWalkButton != null
            ? barterWalkButton.getY() + barterWalkButton.getHeight() + 2
            : rowY + 48;
        GuideTheme.drawCentered(ctx, this.textRenderer,
            "you have " + barterGold + " gold", centerX, goldY, GuideTheme.INK_SOFT);
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
