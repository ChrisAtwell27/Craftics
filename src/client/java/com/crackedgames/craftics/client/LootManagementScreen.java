package com.crackedgames.craftics.client;

import com.crackedgames.craftics.screen.LootManagementScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * Post-victory loot screen: a chest-style GUI (vanilla container texture) with
 * the overflow loot on top and the player's inventory below. Closing it with
 * loot still unclaimed shows a confirmation; the leftover is then discarded and
 * combat continues.
 */
public class LootManagementScreen extends HandledScreen<LootManagementScreenHandler> {

    public LootManagementScreen(LootManagementScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundWidth = 176;
        this.backgroundHeight = 114 + LootManagementScreenHandler.LOOT_ROWS * 18; // 150
        this.playerInventoryTitleY = this.backgroundHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
        // Buttons sit in the gap just above the chest panel.
        int btnY = this.y - 20;
        // Take All — shift-move every loot slot into the inventory.
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Take All"), b -> takeAll())
            .dimensions(this.x + 4, btnY, 80, 16).build());
        // Continue — close, with a leftover-loot warning if anything remains.
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Continue"), b -> tryClose())
            .dimensions(this.x + this.backgroundWidth - 84, btnY, 80, 16).build());
    }

    private void takeAll() {
        if (this.client == null || this.client.interactionManager == null || this.client.player == null) {
            return;
        }
        for (int i = 0; i < LootManagementScreenHandler.LOOT_SLOTS; i++) {
            this.client.interactionManager.clickSlot(
                this.handler.syncId, i, 0, SlotActionType.QUICK_MOVE, this.client.player);
        }
    }

    private void tryClose() {
        int remaining = this.handler.remainingLootCount();
        if (remaining > 0 && this.client != null) {
            this.client.setScreen(new ConfirmScreen(
                confirmed -> {
                    if (confirmed) {
                        doClose();
                    } else if (this.client != null) {
                        this.client.setScreen(this);
                    }
                },
                Text.literal("Leave loot behind?"),
                Text.literal(remaining + " item" + (remaining != 1 ? "s" : "")
                    + " will be left behind and lost.")));
        } else {
            doClose();
        }
    }

    private void doClose() {
        if (this.client != null && this.client.player != null) {
            this.client.player.closeHandledScreen();
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // ESC (GLFW key 256) and the inventory key both route through the close
        // confirmation instead of closing the screen directly.
        boolean isInventoryKey = this.client != null
            && this.client.options.inventoryKey.matchesKey(keyCode, scanCode);
        if (keyCode == 256 || isInventoryKey) {
            tryClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        // Vanilla chest GUI texture: the loot rows on top, player inventory below —
        // exactly how a small chest looks when opened.
        int rows = LootManagementScreenHandler.LOOT_ROWS;
        drawChestTexture(context, this.x, this.y, 0, 0, this.backgroundWidth, rows * 18 + 17);
        drawChestTexture(context, this.x, this.y + rows * 18 + 17, 0, 126, this.backgroundWidth, 96);
    }

    /** Draws a region of the vanilla generic-container texture across all version shards. */
    private void drawChestTexture(DrawContext context, int x, int y, int u, int v, int w, int h) {
        Identifier tex = Identifier.of("minecraft", "textures/gui/container/generic_54.png");
        //? if <=1.21.1 {
        /*context.drawTexture(tex, x, y, (float) u, (float) v, w, h, 256, 256);
        *///?} else {
        context.drawTexture(net.minecraft.client.render.RenderLayer::getGuiTextured, tex, x, y, (float) u, (float) v, w, h, 256, 256);
        //?}
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        this.drawMouseoverTooltip(context, mouseX, mouseY);
    }
}
