package com.crackedgames.craftics.client;

import com.crackedgames.craftics.network.EventChoicePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/**
 * Interactive event room screen shown when the player enters a
 * Shrine, Wounded Traveler area, or Treasure Vault.
 * Each event type renders its own layout with meaningful player choices.
 */
public class EventRoomScreen extends Screen {

    private final String eventType;
    private final String eventData;

    private static final int BTN_W = 320;
    private static final int BTN_H = 22;
    private static final int BTN_GAP = 4;

    public EventRoomScreen(String eventType, String eventData) {
        super(Text.literal("Event"));
        this.eventType = eventType;
        this.eventData = eventData;
    }

    @Override
    protected void init() {
        this.clearChildren();
        switch (eventType) {
            case "shrine" -> initShrine();
            case "traveler" -> initTraveler();
            case "vault" -> initVault();
            case "enchanter" -> initEnchanter();
        }
    }

    // ── Shrine of Fortune ──
    // Data format: "smallCost:medCost:largeCost:playerEmeralds"
    private int[] shrineCosts;
    private int shrineEmeralds;

    private void initShrine() {
        String[] parts = eventData.split(":");
        shrineCosts = new int[]{
            Integer.parseInt(parts[0]),
            Integer.parseInt(parts[1]),
            Integer.parseInt(parts[2])
        };
        shrineEmeralds = Integer.parseInt(parts[3]);

        int cx = this.width / 2;
        int startY = this.height / 2 + 10;

        String[] labels = {"Small Offering", "Medium Offering", "Large Offering"};
        String[] tiers = {"§7Common reward", "§aGood reward", "§6Rare reward"};
        for (int i = 0; i < 3; i++) {
            int cost = shrineCosts[i];
            boolean canAfford = shrineEmeralds >= cost;
            String label = (canAfford ? "§a" : "§8") + labels[i]
                + " §7(" + cost + " emeralds) — " + tiers[i];
            final int choice = i;
            ButtonWidget btn = ButtonWidget.builder(
                Text.literal(label),
                b -> sendChoice(choice)
            ).dimensions(cx - BTN_W / 2, startY + i * (BTN_H + BTN_GAP), BTN_W, BTN_H).build();
            btn.active = canAfford;
            this.addDrawableChild(btn);
        }

        // Walk away button
        this.addDrawableChild(ButtonWidget.builder(
            Text.literal("§7✗ Walk Away"),
            b -> sendChoice(-1)
        ).dimensions(cx - BTN_W / 2, startY + 3 * (BTN_H + BTN_GAP) + 6, BTN_W, BTN_H).build());
    }

    // ── Wounded Traveler ──
    // Data format: "idx:itemName:tier|idx:itemName:tier|..."
    private String[][] travelerFoods;

    private void initTraveler() {
        int cx = this.width / 2;
        int startY = this.height / 2 + 10;

        if (eventData.isEmpty()) {
            // No food available
            this.addDrawableChild(ButtonWidget.builder(
                Text.literal("§c✗ You have no food to give..."),
                b -> sendChoice(-1)
            ).dimensions(cx - BTN_W / 2, startY, BTN_W, BTN_H).build());
            return;
        }

        String[] entries = eventData.split("\\|");
        travelerFoods = new String[entries.length][];
        for (int i = 0; i < entries.length; i++) {
            travelerFoods[i] = entries[i].split(":");
        }

        // Sort by tier descending so best food shows first
        java.util.Arrays.sort(travelerFoods, (a, b) ->
            Integer.parseInt(b[2]) - Integer.parseInt(a[2]));

        // Limit to 6 food options max to avoid overflowing
        int count = Math.min(travelerFoods.length, 6);
        for (int i = 0; i < count; i++) {
            String itemName = travelerFoods[i][1];
            int tier = Integer.parseInt(travelerFoods[i][2]);
            String tierLabel = switch (tier) {
                case 1 -> "§7Basic";
                case 2 -> "§aGood";
                case 3 -> "§6Great";
                case 4 -> "§d§lLegendary";
                default -> "§7";
            };
            String tierHint = switch (tier) {
                case 1 -> "§8Low reward chance";
                case 2 -> "§7Decent reward chance";
                case 3 -> "§eHigh reward chance";
                case 4 -> "§dGuaranteed rare reward!";
                default -> "";
            };
            final int choice = Integer.parseInt(travelerFoods[i][0]); // original slot index
            ButtonWidget btn = ButtonWidget.builder(
                Text.literal("§f Give " + itemName + "  " + tierLabel + "  " + tierHint),
                b -> sendChoice(choice)
            ).dimensions(cx - BTN_W / 2, startY + i * (BTN_H + BTN_GAP), BTN_W, BTN_H).build();
            this.addDrawableChild(btn);
        }

        // Leave button
        this.addDrawableChild(ButtonWidget.builder(
            Text.literal("§7✗ Leave Without Helping"),
            b -> sendChoice(-1)
        ).dimensions(cx - BTN_W / 2, startY + count * (BTN_H + BTN_GAP) + 6, BTN_W, BTN_H).build());
    }

    // ── Enchanter ──
    // Data format: "slotId:itemName:weapon:enchant|slotId:itemName:armor:trim|..."
    private void initEnchanter() {
        int cx = this.width / 2;
        int startY = this.height / 2 + 10;

        if (eventData.isEmpty()) {
            this.addDrawableChild(ButtonWidget.builder(
                Text.literal("§c✗ You have nothing to enchant..."),
                b -> sendChoice(-1)
            ).dimensions(cx - BTN_W / 2, startY, BTN_W, BTN_H).build());
            return;
        }

        String[] entries = eventData.split("\\|");
        int count = Math.min(entries.length, 6);
        for (int i = 0; i < count; i++) {
            String[] parts = entries[i].split(":");
            if (parts.length < 3) continue;
            int slotId = Integer.parseInt(parts[0]);
            String itemName = parts[1];
            boolean isArmor = "armor".equals(parts[2]);
            String enhancementType = parts.length > 3
                ? parts[3]
                : (isArmor ? "trim" : "enchant");
            String hint = "trim".equals(enhancementType)
                ? "§b+Trim"
                : "§d+Enchantment";
            ButtonWidget btn = ButtonWidget.builder(
                Text.literal("§f✦ Enhance " + itemName + "  " + hint),
                b -> sendChoice(slotId)
            ).dimensions(cx - BTN_W / 2, startY + i * (BTN_H + BTN_GAP), BTN_W, BTN_H).build();
            this.addDrawableChild(btn);
        }

        this.addDrawableChild(ButtonWidget.builder(
            Text.literal("§7✗ Decline"),
            b -> sendChoice(-1)
        ).dimensions(cx - BTN_W / 2, startY + count * (BTN_H + BTN_GAP) + 6, BTN_W, BTN_H).build());
    }

    // ── Treasure Vault ──
    private void initVault() {
        int cx = this.width / 2;
        int startY = this.height / 2 + 10;

        this.addDrawableChild(ButtonWidget.builder(
            Text.literal("§a✦ Open the Vault §7(Free loot!)"),
            b -> sendChoice(0)
        ).dimensions(cx - BTN_W / 2, startY, BTN_W, BTN_H).build());

        this.addDrawableChild(ButtonWidget.builder(
            Text.literal("§7✗ Leave Empty-Handed"),
            b -> sendChoice(-1)
        ).dimensions(cx - BTN_W / 2, startY + BTN_H + BTN_GAP + 6, BTN_W, BTN_H).build());
    }

    private void sendChoice(int choiceIndex) {
        ClientPlayNetworking.send(new EventChoicePayload(choiceIndex));
        this.close();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        int cx = this.width / 2;
        int headerY = this.height / 2 - 50;

        switch (eventType) {
            case "shrine" -> renderShrineHeader(context, cx, headerY);
            case "traveler" -> renderTravelerHeader(context, cx, headerY);
            case "vault" -> renderVaultHeader(context, cx, headerY);
            case "enchanter" -> renderEnchanterHeader(context, cx, headerY);
        }
    }

    private void renderShrineHeader(DrawContext ctx, int cx, int y) {
        ctx.drawCenteredTextWithShadow(this.textRenderer,
            Text.literal("§e§l✦ SHRINE OF FORTUNE ✦"), cx, y, 0xFFAA00);
        ctx.drawCenteredTextWithShadow(this.textRenderer,
            Text.literal("§7A mystical shrine pulses with energy..."), cx, y + 14, 0xAAAAAA);
        ctx.drawCenteredTextWithShadow(this.textRenderer,
            Text.literal("§fChoose your offering. Bigger risk, bigger reward."), cx, y + 28, 0xFFFFFF);
        ctx.drawCenteredTextWithShadow(this.textRenderer,
            Text.literal("§aYour emeralds: §e" + shrineEmeralds), cx, y + 42, 0x55FF55);
    }

    private void renderTravelerHeader(DrawContext ctx, int cx, int y) {
        ctx.drawCenteredTextWithShadow(this.textRenderer,
            Text.literal("§c§l❤ WOUNDED TRAVELER ❤"), cx, y, 0xFF5555);
        ctx.drawCenteredTextWithShadow(this.textRenderer,
            Text.literal("§7A wounded traveler begs for food..."), cx, y + 14, 0xAAAAAA);
        ctx.drawCenteredTextWithShadow(this.textRenderer,
            Text.literal("§fBetter food = better reward!"), cx, y + 28, 0xFFFFFF);
    }

    private void renderEnchanterHeader(DrawContext ctx, int cx, int y) {
        ctx.drawCenteredTextWithShadow(this.textRenderer,
            Text.literal("§d§l✨ WANDERING ENCHANTER ✨"), cx, y, 0xFF55FF);
        ctx.drawCenteredTextWithShadow(this.textRenderer,
            Text.literal("§7\"Give me an item and I'll enhance it...\""), cx, y + 14, 0xAAAAAA);
        ctx.drawCenteredTextWithShadow(this.textRenderer,
            Text.literal("§fWeapons get enchantments, armor gets trims."), cx, y + 28, 0xFFFFFF);
    }

    private void renderVaultHeader(DrawContext ctx, int cx, int y) {
        ctx.drawCenteredTextWithShadow(this.textRenderer,
            Text.literal("§6§l✦ TREASURE VAULT ✦"), cx, y, 0xFFAA00);
        ctx.drawCenteredTextWithShadow(this.textRenderer,
            Text.literal("§eA hidden vault filled with riches!"), cx, y + 14, 0xFFFF55);
        ctx.drawCenteredTextWithShadow(this.textRenderer,
            Text.literal("§7No enemies inside — just free loot."), cx, y + 28, 0xAAAAAA);
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public boolean shouldCloseOnEsc() { return false; }
}
