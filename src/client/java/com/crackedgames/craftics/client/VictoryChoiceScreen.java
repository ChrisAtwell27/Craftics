package com.crackedgames.craftics.client;

import com.crackedgames.craftics.network.PostLevelChoicePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/**
 * Shown after winning a non-boss level.
 * Player chooses: Go Home (safe, reset biome) or Continue (risky, next level).
 * Also used for trial chamber / random event prompts (levelIndex == -1).
 */
public class VictoryChoiceScreen extends Screen {

    private final int emeraldsEarned;
    private final int totalEmeralds;
    private final String biomeName;
    private final int levelIndex;
    private final boolean nextIsBoss;

    /** True when this screen is prompting for a trial/event, not a regular level victory. */
    private final boolean isEventPrompt;

    public VictoryChoiceScreen(int emeraldsEarned, int totalEmeralds,
                                String biomeName, int levelIndex, boolean nextIsBoss) {
        super(Text.literal("Victory!"));
        this.emeraldsEarned = emeraldsEarned;
        this.totalEmeralds = totalEmeralds;
        this.biomeName = biomeName;
        this.levelIndex = levelIndex;
        this.nextIsBoss = nextIsBoss;
        this.isEventPrompt = (levelIndex == -1);
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int btnW = 300;
        int btnH = 20;

        if (isEventPrompt) {
            // --- Event prompt (Trial Chamber, Treasure Vault, Ominous Trial, addon events) ---
            boolean isTreasure = biomeName.contains("Treasure");
            boolean isOminous = biomeName.contains("Ominous");
            boolean isTrial = biomeName.contains("Trial");

            String acceptLabel;
            String declineLabel;

            if (isTreasure) {
                acceptLabel = "\u00a7a\u2726 Enter the Vault (Free loot!)";
                declineLabel = "\u00a77\u2717 Skip and continue";
            } else if (isOminous) {
                acceptLabel = "\u00a7c\u2694 Accept the Ominous Trial (Legendary loot!)";
                declineLabel = "\u00a77\u2717 Not worth the risk...";
            } else if (isTrial) {
                acceptLabel = "\u00a76\u2694 Enter the Trial Chamber (Rare loot!)";
                declineLabel = "\u00a77\u2717 Pass and continue";
            } else {
                acceptLabel = "\u00a76\u2694 Explore " + biomeName;
                declineLabel = "\u00a77\u2717 Pass and continue";
            }

            // Accept button (sends goHome=false to enter trial)
            this.addDrawableChild(ButtonWidget.builder(
                Text.literal(acceptLabel),
                btn -> {
                    this.close();
                    TransitionOverlay.startTransition(
                        biomeName, "Entering...",
                        () -> ClientPlayNetworking.send(new PostLevelChoicePayload(false))
                    );
                }
            ).dimensions(centerX - btnW / 2, centerY + 30, btnW, btnH).build());

            // Decline button (sends goHome=true to skip)
            this.addDrawableChild(ButtonWidget.builder(
                Text.literal(declineLabel),
                btn -> {
                    this.close();
                    TransitionOverlay.startTransition(
                        "Onward!", "",
                        () -> ClientPlayNetworking.send(new PostLevelChoicePayload(true))
                    );
                }
            ).dimensions(centerX - btnW / 2, centerY + 55, btnW, btnH).build());

        } else {
            // --- Normal victory choice ---
            this.addDrawableChild(ButtonWidget.builder(
                Text.literal("\u00a7a\u2302 Go Home (Keep loot, reset biome progress)"),
                btn -> {
                    this.close();
                    TransitionOverlay.startTransition(
                        "Returning to Hub", "Safe travels...",
                        () -> ClientPlayNetworking.send(new PostLevelChoicePayload(true))
                    );
                }
            ).dimensions(centerX - btnW / 2, centerY + 30, btnW, btnH).build());

            int nextLevel = levelIndex + 1; // payload is already next level index (0-based)
            if (nextIsBoss) {
                this.addDrawableChild(ButtonWidget.builder(
                    Text.literal("\u00a74\u00a7l\u2620 BOSS FIGHT: " + biomeName + " \u2620 (Risk it all!)"),
                    btn -> {
                        this.close();
                        TransitionOverlay.startTransition(
                            "\u00a74\u00a7l\u2620 BOSS FIGHT \u2620",
                            biomeName + " \u2014 Prepare yourself...",
                            () -> ClientPlayNetworking.send(new PostLevelChoicePayload(false))
                        );
                    }
                ).dimensions(centerX - btnW / 2, centerY + 55, btnW, btnH).build());
            } else {
                this.addDrawableChild(ButtonWidget.builder(
                    Text.literal("\u00a7c\u2694 Continue to Level " + nextLevel + " (Risk it all!)"),
                    btn -> {
                        this.close();
                        TransitionOverlay.startTransition(
                            biomeName + " \u2014 Level " + nextLevel, "Onward!",
                            () -> ClientPlayNetworking.send(new PostLevelChoicePayload(false))
                        );
                    }
                ).dimensions(centerX - btnW / 2, centerY + 55, btnW, btnH).build());
            }
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        if (isEventPrompt) {
            renderEventPrompt(context, centerX, centerY);
        } else {
            renderVictoryScreen(context, centerX, centerY);
        }
    }

    private void renderEventPrompt(DrawContext context, int cx, int cy) {
        boolean isTreasure = biomeName.contains("Treasure");
        boolean isOminous = biomeName.contains("Ominous");
        boolean isTrial = biomeName.contains("Trial");

        if (isTreasure) {
            // Treasure Vault prompt
            context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("\u00a76\u00a7l\u2726 TREASURE VAULT \u2726"),
                cx, cy - 55, 0xFFAA00);
            context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("\u00a7eA hidden vault filled with riches!"),
                cx, cy - 38, 0xFFFF55);
            context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("\u00a77No enemies inside \u2014 just free loot."),
                cx, cy - 22, 0xAAAAAA);
        } else if (isOminous) {
            // Ominous Trial prompt
            context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("\u00a74\u00a7l\u2694 OMINOUS TRIAL CHAMBER \u2694"),
                cx, cy - 55, 0xFF5555);
            context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("\u00a7cA dark and powerful trial awaits..."),
                cx, cy - 38, 0xFF5555);
            context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("\u00a74\u26a0 WARNING: Contains a Warden!"),
                cx, cy - 22, 0xAA0000);
            context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("\u00a7dRewards: Legendary-tier loot"),
                cx, cy - 6, 0xFF55FF);
        } else if (isTrial) {
            // Standard Trial Chamber prompt
            context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("\u00a76\u00a7l\u2694 TRIAL CHAMBER DISCOVERED \u2694"),
                cx, cy - 55, 0xFFAA00);
            context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("\u00a7eA mysterious trial awaits..."),
                cx, cy - 38, 0xFFFF55);
            context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("\u00a77Defeat trial mobs for rare loot!"),
                cx, cy - 22, 0xAAAAAA);
            context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("\u00a7cEnemies are tougher than normal."),
                cx, cy - 6, 0xFF5555);
        } else {
            // Generic addon event prompt — use the event's display name
            context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("\u00a76\u00a7l\u2726 " + biomeName.toUpperCase() + " \u2726"),
                cx, cy - 55, 0xFFAA00);
            context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("\u00a7eA rare discovery!"),
                cx, cy - 38, 0xFFFF55);
            context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("\u00a77Will you explore it?"),
                cx, cy - 22, 0xAAAAAA);
        }
    }

    private void renderVictoryScreen(DrawContext context, int cx, int cy) {
        // Victory header
        context.drawCenteredTextWithShadow(this.textRenderer,
            Text.literal("\u00a7a\u00a7l*** VICTORY! ***"),
            cx, cy - 55, 0x55FF55);

        // Biome name + level
        context.drawCenteredTextWithShadow(this.textRenderer,
            Text.literal("\u00a7f" + biomeName + " \u2014 Level " + (levelIndex + 1) + " Complete"),
            cx, cy - 38, 0xFFFFFF);

        // Emeralds earned
        context.drawCenteredTextWithShadow(this.textRenderer,
            Text.literal("\u00a7a+ " + emeraldsEarned + " Emeralds  \u00a77(Total: " + totalEmeralds + ")"),
            cx, cy - 18, 0x55FF55);

        if (nextIsBoss) {
            // Boss warning — big and red
            context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("\u00a74\u00a7l\u2620 WARNING: NEXT LEVEL IS A BOSS FIGHT! \u2620"),
                cx, cy + 4, 0xFF5555);
            context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("\u00a7c\u26a0 If you die, you lose ALL items!"),
                cx, cy + 18, 0xFF5555);
        } else {
            // Normal warning
            context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("\u00a7c\u26a0 If you die, you lose ALL items!"),
                cx, cy + 10, 0xFF5555);
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false; // Must choose
    }
}
