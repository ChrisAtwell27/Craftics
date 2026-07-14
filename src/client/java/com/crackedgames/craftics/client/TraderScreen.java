package com.crackedgames.craftics.client;

import com.crackedgames.craftics.client.guide.GuideButton;
import com.crackedgames.craftics.client.guide.GuideTheme;
import com.crackedgames.craftics.network.TraderBuyPayload;
import com.crackedgames.craftics.network.TraderDonePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Booth shop screen for the trading hall (and any TraderOfferPayload sender).
 * Parchment panel listing each trade as a row: item icon + count, name, emerald
 * cost, and remaining stock. Rows gray out when sold out, costs turn red when
 * unaffordable, hovering shows the real item tooltip, and a successful purchase
 * flashes the row green while the emerald counter ticks down.
 *
 * <p>Trades arrive serialized as {@code itemId~count~cost~stock~desc} entries
 * joined by {@code |} (a 4-field legacy form without stock is tolerated). The
 * server re-sends the payload after every purchase; {@link #updateOffer} applies
 * it in place so the screen never flickers closed.
 */
public class TraderScreen extends Screen {

    /** One parsed trade row. {@code stock < 0} means unlimited (legacy data). */
    private record Row(ItemStack stack, int cost, int stock, String desc) {}

    private final String traderName;
    private final String traderIcon;
    private List<Row> rows;
    private int playerEmeralds;

    /** Animated emerald display value (eases toward {@link #playerEmeralds}). */
    private float shownEmeralds;
    /** Per-row green flash timestamps (ms) started when a purchase confirms. */
    private final long[] rowFlash = new long[64];
    /** Guards the close() safety net from double-sending Done. */
    private boolean doneSent = false;

    // Panel layout
    private static final int PANEL_W   = 360;
    private static final int ROW_H     = 22;
    private static final int ROW_GAP   = 3;
    private static final int HEADER_H  = 46;
    private static final int PANEL_PAD = 12;

    public TraderScreen(String traderName, String traderIcon, String tradeData,
                        List<ItemStack> stacks, int playerEmeralds) {
        super(Text.literal("Merchant"));
        this.traderName = traderName;
        this.traderIcon = traderIcon;
        this.rows = parse(tradeData, stacks);
        this.playerEmeralds = playerEmeralds;
        this.shownEmeralds = playerEmeralds;
    }

    /** Apply a refreshed offer (post-purchase) in place: flash rows whose stock
     *  dropped, and let the emerald counter ease to the new value. */
    public void updateOffer(String tradeData, List<ItemStack> stacks, int newEmeralds) {
        List<Row> fresh = parse(tradeData, stacks);
        for (int i = 0; i < fresh.size() && i < rows.size() && i < rowFlash.length; i++) {
            if (fresh.get(i).stock() >= 0 && fresh.get(i).stock() < rows.get(i).stock()) {
                rowFlash[i] = System.currentTimeMillis();
            }
        }
        this.rows = fresh;
        this.playerEmeralds = newEmeralds;
    }

    private static List<Row> parse(String tradeData, List<ItemStack> stacks) {
        List<Row> out = new ArrayList<>();
        if (tradeData == null || tradeData.isEmpty()) return out;
        int idx = 0;
        for (String entry : tradeData.split("\\|")) {
            String[] p = entry.split("~", 5);
            // Prefer the real stack from the payload: rebuilding from the bare item id loses
            // every component, which previewed potions as "Uncraftable Potion / No Effects"
            // and hid enchantments. The id form stays as the fallback.
            ItemStack real = stacks != null && idx < stacks.size() && !stacks.get(idx).isEmpty()
                ? stacks.get(idx).copy() : null;
            idx++;
            try {
                if (p.length == 5) {
                    out.add(new Row(real != null ? real : stackOf(p[0], Integer.parseInt(p[1])),
                        Integer.parseInt(p[2]), Integer.parseInt(p[3]), p[4]));
                } else if (p.length == 4) {
                    // Legacy 4-field form: no stock (unlimited).
                    out.add(new Row(real != null ? real : stackOf(p[0], Integer.parseInt(p[1])),
                        Integer.parseInt(p[2]), -1, p[3]));
                }
            } catch (NumberFormatException ignored) {
                // Malformed entry - skip the row rather than break the screen.
            }
        }
        return out;
    }

    private static ItemStack stackOf(String itemId, int count) {
        try {
            Item item = Registries.ITEM.get(Identifier.of(itemId));
            return new ItemStack(item == Items.AIR ? Items.BARRIER : item, Math.max(1, count));
        } catch (Exception e) {
            return new ItemStack(Items.BARRIER);
        }
    }

    // -------------------------------------------------------------------------
    // Layout helpers
    // -------------------------------------------------------------------------

    private int panelHeight() {
        int listH = rows.size() * (ROW_H + ROW_GAP);
        return PANEL_PAD + HEADER_H + listH + ROW_GAP + 20 + PANEL_PAD; // +20 Done row
    }

    private int panelTop() {
        return (this.height - panelHeight()) / 2;
    }

    private int rowsStartY() {
        return panelTop() + PANEL_PAD + HEADER_H;
    }

    private int rowX() {
        return (this.width - PANEL_W) / 2 + 8;
    }

    private int rowW() {
        return PANEL_W - 16;
    }

    // -------------------------------------------------------------------------
    // init
    // -------------------------------------------------------------------------

    @Override
    protected void init() {
        this.clearChildren();
        int doneY = rowsStartY() + rows.size() * (ROW_H + ROW_GAP) + ROW_GAP;
        this.addDrawableChild(GuideButton.of(
            this.width / 2 - 75, doneY, 150, 20,
            Text.literal("Done Trading"),
            btn -> sendDoneAndClose()
        ));
    }

    private void sendDoneAndClose() {
        if (!doneSent) {
            doneSent = true;
            ClientPlayNetworking.send(new TraderDonePayload());
        }
        this.close();
    }

    @Override
    public void removed() {
        super.removed();
        // Safety net: any close path (ESC, forced close) must release the server's
        // booth session, or the player can't click-walk in the scene afterwards.
        if (!doneSent) {
            doneSent = true;
            ClientPlayNetworking.send(new TraderDonePayload());
        }
    }

    // -------------------------------------------------------------------------
    // Input
    // -------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int idx = rowAt(mouseX, mouseY);
            if (idx >= 0) {
                Row row = rows.get(idx);
                boolean canBuy = row.stock() != 0 && playerEmeralds >= row.cost();
                if (canBuy) {
                    ClientPlayNetworking.send(new TraderBuyPayload(idx));
                    playClick(1.2f);
                } else {
                    playClick(0.6f); // dull thud - can't afford / sold out
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void playClick(float pitch) {
        MinecraftClient mc = MinecraftClient.getInstance();
        mc.getSoundManager().play(PositionedSoundInstance.master(
            SoundEvents.UI_BUTTON_CLICK.value(), pitch));
    }

    /** Index of the trade row under the cursor, or -1. */
    private int rowAt(double mouseX, double mouseY) {
        int x = rowX();
        int y = rowsStartY();
        if (mouseX < x || mouseX >= x + rowW()) return -1;
        for (int i = 0; i < rows.size(); i++) {
            int top = y + i * (ROW_H + ROW_GAP);
            if (mouseY >= top && mouseY < top + ROW_H) return i;
        }
        return -1;
    }

    // -------------------------------------------------------------------------
    // Render
    // -------------------------------------------------------------------------

    /** The parchment panel belongs to the background pass: drawn after the
     *  vanilla blur/darken gradient (so it stays crisp) but before the Done
     *  button and row content that render() paints on top. */
    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        super.renderBackground(context, mouseX, mouseY, delta);
        GuideTheme.drawPanel(context, (this.width - PANEL_W) / 2, panelTop(), PANEL_W, panelHeight());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Ease the emerald counter toward the real value (~6 units/frame at 60fps).
        shownEmeralds += (playerEmeralds - shownEmeralds) * Math.min(1f, delta * 0.35f);
        if (Math.abs(playerEmeralds - shownEmeralds) < 0.05f) shownEmeralds = playerEmeralds;

        super.render(context, mouseX, mouseY, delta);

        // Header: merchant name, flavor line, emerald balance with icon.
        int centerX = this.width / 2;
        int y = panelTop() + PANEL_PAD;
        GuideTheme.drawCentered(context, this.textRenderer,
            traderIcon + " " + traderName, centerX, y, GuideTheme.GOLD);
        GuideTheme.drawCentered(context, this.textRenderer,
            "\"Take a look at my wares...\"", centerX, y + 13, GuideTheme.INK_SOFT);
        String balance = String.valueOf(Math.round(shownEmeralds));
        int balW = this.textRenderer.getWidth(balance) + 18;
        int balX = centerX - balW / 2;
        context.drawItem(new ItemStack(Items.EMERALD), balX, y + 24);
        GuideTheme.drawInk(context, this.textRenderer, balance, balX + 18, y + 28, GuideTheme.INK);

        // Trade rows.
        long now = System.currentTimeMillis();
        int x = rowX();
        int listY = rowsStartY();
        int hoveredIdx = rowAt(mouseX, mouseY);
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            int top = listY + i * (ROW_H + ROW_GAP);
            boolean soldOut = row.stock() == 0;
            boolean affordable = playerEmeralds >= row.cost();
            boolean hovered = i == hoveredIdx && !soldOut;

            // Row fill: shaded parchment, brighter on hover, dim when sold out.
            int fill = soldOut ? 0x30000000 : (hovered ? 0x28FFFFFF : 0x14000000);
            context.fill(x, top, x + rowW(), top + ROW_H, fill);
            // Purchase flash: green wash fading over 450ms.
            if (i < rowFlash.length && rowFlash[i] > 0) {
                long age = now - rowFlash[i];
                if (age < 450) {
                    int a = (int) (0x66 * (1f - age / 450f));
                    context.fill(x, top, x + rowW(), top + ROW_H, (a << 24) | 0x55FF77);
                }
            }

            // Item icon + count.
            context.drawItem(row.stack(), x + 3, top + 3);
            if (row.stack().getCount() > 1) {
                String cnt = "x" + row.stack().getCount();
                GuideTheme.drawInk(context, this.textRenderer, cnt, x + 22, top + 12, GuideTheme.INK_SOFT);
            }

            // Name/description.
            int descX = x + 22 + (row.stack().getCount() > 1 ? 18 : 0);
            int descColor = soldOut ? GuideTheme.INK_FAINT : GuideTheme.INK;
            GuideTheme.drawInk(context, this.textRenderer, row.desc(), descX, top + 7, descColor);

            // Right side: SOLD OUT, or cost + emerald icon (+ stock hint).
            if (soldOut) {
                String sold = "SOLD OUT";
                int sw = this.textRenderer.getWidth(sold);
                GuideTheme.drawInk(context, this.textRenderer, sold,
                    x + rowW() - sw - 6, top + 7, 0xFF8A3324);
            } else {
                String cost = String.valueOf(row.cost());
                int costColor = affordable ? GuideTheme.INK : 0xFFB03030;
                int cw = this.textRenderer.getWidth(cost);
                int costX = x + rowW() - cw - 24;
                GuideTheme.drawInk(context, this.textRenderer, cost, costX, top + 7, costColor);
                context.drawItem(new ItemStack(Items.EMERALD), costX + cw + 3, top + 3);
                if (row.stock() > 0) {
                    String left = row.stock() + " left";
                    int lw = this.textRenderer.getWidth(left);
                    GuideTheme.drawInk(context, this.textRenderer, left,
                        costX - lw - 8, top + 7, GuideTheme.INK_FAINT);
                }
            }
        }

        // Hover tooltip: the real item tooltip, drawn last so it layers on top.
        if (hoveredIdx >= 0) {
            context.drawTooltip(this.textRenderer,
                Screen.getTooltipFromItem(MinecraftClient.getInstance(), rows.get(hoveredIdx).stack()),
                mouseX, mouseY);
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true; // removed() safety net sends Done, so ESC can't leak the session
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
