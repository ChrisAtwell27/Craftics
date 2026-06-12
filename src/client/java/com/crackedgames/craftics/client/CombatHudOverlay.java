package com.crackedgames.craftics.client;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CombatHudOverlay implements HudRenderCallback {

    private static final int PANEL_BG = 0xBB111122;
    private static final int PANEL_BORDER = 0xFF333344;

    /** Wall-clock time of the last turn-phase change — drives the banner
     *  entrance pop and the fade to the compact "Turn N" pill, independent
     *  of frame rate (the old tick counter advanced per FRAME, so the fade
     *  ran 2-4x too fast on high-refresh displays). */
    private static long phaseChangeMs = 0;
    private static int lastTurnPhase = -1;

    /** Displayed HP fraction per bar key, eased toward the real value each
     *  frame so damage drains visibly instead of snapping. */
    private static final Map<String, float[]> hpAnim = new java.util.HashMap<>();
    private static long lastFrameMs = 0;
    private static float frameDtSec = 0f;

    /** On-screen End Turn button rect {x1,y1,x2,y2} in GUI coordinates
     *  (uiScale already applied), or null while hidden. Hit-tested by
     *  {@link #tryClickHudButtons} from the combat input handler. */
    private static int[] endTurnBtnRect = null;
    /** Bottom Y of the enemy roster (or inspect panel) updated each render so the
     *  tile tooltip can position itself directly below it without overlap. */
    private static int enemyRosterBottomY = 4;
    /** Width of the enemy roster (or inspect panel) updated each render so the
     *  tile tooltip can match it visually instead of growing to fit text. */
    private static int enemyRosterPanelW = 160;
    /** Right edge X of the enemy roster (or inspect panel) so the tile tooltip
     *  aligns with the same right margin even when its width differs. */
    private static int enemyRosterRightX = -1;
    /** Bottom Y of the party turn-order panel this frame so the enemy
     *  act-order strip can stack below it instead of overlapping. */
    private static int turnOrderBottomY = 20;

    @Override
    public void onHudRender(DrawContext ctx, RenderTickCounter tickCounter) {
        // F1 (vanilla "hide HUD") collapses all combat UI together with the rest.
        if (MinecraftClient.getInstance().options.hudHidden) return;
        if (!CombatState.isInCombat()) {
            // Drop per-fight animation state so the next battle starts clean
            // (entity ids are reused across levels).
            if (!hpAnim.isEmpty()) hpAnim.clear();
            endTurnBtnRect = null;
            lastFrameMs = 0;
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        boolean largeUI = false;
        try { largeUI = com.crackedgames.craftics.CrafticsMod.CONFIG.largerUI(); } catch (Exception ignored) {}
        float uiScale = largeUI ? 1.5f : 1.0f;
        if (largeUI) {
            ctx.getMatrices().push();
            ctx.getMatrices().scale(uiScale, uiScale, 1.0f);
        }

        int screenW = (int)(client.getWindow().getScaledWidth() / uiScale);
        int screenH = (int)(client.getWindow().getScaledHeight() / uiScale);

        long now = System.currentTimeMillis();
        frameDtSec = lastFrameMs == 0 ? 0f : Math.min(0.1f, (now - lastFrameMs) / 1000.0f);
        lastFrameMs = now;
        endTurnBtnRect = null;

        int currentPhase = CombatState.isPlayerTurn() ? 1 : CombatState.isEnemyTurn() ? 2 : 0;
        if (currentPhase != lastTurnPhase) {
            lastTurnPhase = currentPhase;
            phaseChangeMs = now;
        }

        renderPlayerStatusPanel(ctx, client, screenW);
        renderAllyRoster(ctx, client, screenW);
        renderTurnBanner(ctx, client, screenW);
        renderTurnOrder(ctx, client, screenW);
        renderActOrderStrip(ctx, client, screenW);
        renderEnemyRoster(ctx, client, screenW);
        renderTileTooltip(ctx, client, screenW, screenH);
        renderModePill(ctx, client, screenW, screenH);
        renderResourceBar(ctx, client, screenW, screenH);
        renderEndTurnButton(ctx, client, screenW, screenH, uiScale);
        renderMoveCostLabel(ctx, client, screenW, screenH, uiScale);

        boolean showEmeralds = false;
        try { showEmeralds = com.crackedgames.craftics.CrafticsMod.CONFIG.showEmeraldsInCombat(); } catch (Exception ignored) {}
        if (showEmeralds) {
            int emeralds = CombatState.getEmeralds();
            ctx.drawTextWithShadow(client.textRenderer,
                Text.literal("\u00a7a\u25c6 " + emeralds), screenW - 60, screenH - 70, 0xFF55FF55);
        }

        CombatVisualEffects.render(ctx, client, screenW, screenH);
        CombatLog.render(ctx, client.textRenderer, screenW, screenH);

        if (largeUI) {
            ctx.getMatrices().pop();
        }
    }

    private static void drawPanel(DrawContext ctx, int x, int y, int w, int h) {
        // Border
        ctx.fill(x - 1, y - 1, x + w + 1, y + h + 1, PANEL_BORDER);
        // Fill
        ctx.fill(x, y, x + w, y + h, PANEL_BG);
    }

    private static void drawPill(DrawContext ctx, int x, int y, int w, int h, int color) {
        ctx.fill(x, y, x + w, y + h, color);
    }

    // ─── Smooth HP Bars ──────────────────────────────────────────────────

    /**
     * Advance and return the displayed HP fraction for {@code key}, easing
     * toward {@code target} at a fixed wall-clock rate.
     */
    private static float smoothPct(String key, float target) {
        float[] s = hpAnim.computeIfAbsent(key, k -> new float[]{target});
        float cur = s[0];
        float diff = target - cur;
        if (Math.abs(diff) < 0.003f) {
            cur = target;
        } else {
            cur += diff * Math.min(1f, frameDtSec * 6f);
        }
        s[0] = cur;
        return cur;
    }

    /**
     * HP bar with damage feedback: dark track, colored fill at the REAL value,
     * and — while the eased display value catches up — a bright "ghost"
     * segment over the chunk just lost, draining toward the fill. Heals sweep
     * a lighter segment upward instead. Keys must be unique per on-screen bar
     * (the same entity shown in two panels needs two keys, otherwise the
     * easing advances twice per frame).
     */
    private static void drawHpBar(DrawContext ctx, String key, int x, int y, int w, int h,
                                  float pct, int fillColor, int trackColor) {
        pct = Math.max(0f, Math.min(1f, pct));
        float shown = smoothPct(key, pct);
        ctx.fill(x, y, x + w, y + h, trackColor);
        int fillW = (int) (w * pct);
        if (fillW > 0) {
            ctx.fill(x, y, x + fillW, y + h, fillColor);
        }
        if (shown > pct) {
            int ghostEnd = (int) (w * shown);
            if (ghostEnd > fillW) {
                ctx.fill(x + fillW, y, x + ghostEnd, y + h, 0xCCFFD9C9);
            }
        } else if (shown < pct) {
            int sweepStart = (int) (w * shown);
            if (fillW > sweepStart) {
                ctx.fill(x + sweepStart, y, x + fillW, y + h, 0x66FFFFFF);
            }
        }
    }

    /** Standard HP fill color by remaining fraction. */
    private static int hpColor(float pct) {
        return pct > 0.5f ? 0xFF55FF55 : pct > 0.25f ? 0xFFFFFF55 : 0xFFFF5555;
    }

    // ─── Mouse Mapping (HUD widgets) ─────────────────────────────────────

    /**
     * Cursor position in scaled-GUI coordinates (before any largerUI scale),
     * or null without a window. Uses the same screen→framebuffer assumption
     * as vanilla's Mouse handler.
     */
    private static double[] mouseGuiPos(MinecraftClient client) {
        var window = client.getWindow();
        if (window == null || window.getWidth() <= 0 || window.getHeight() <= 0) return null;
        double fx = (double) window.getScaledWidth() / window.getWidth();
        double fy = (double) window.getScaledHeight() / window.getHeight();
        return new double[]{client.mouse.getX() * fx, client.mouse.getY() * fy};
    }

    private static boolean isMouseOver(MinecraftClient client, float uiScale,
                                       int x, int y, int w, int h) {
        double[] m = mouseGuiPos(client);
        if (m == null) return false;
        double mx = m[0] / uiScale;
        double my = m[1] / uiScale;
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    /**
     * Handle a left-click on HUD widgets (currently the End Turn button).
     * Called by {@code CombatInputHandler} BEFORE tile click handling; returns
     * true when the click was consumed.
     */
    public static boolean tryClickHudButtons(MinecraftClient client) {
        int[] r = endTurnBtnRect;
        if (r == null || !CombatState.isLocalPlayersTurn()) return false;
        double[] m = mouseGuiPos(client);
        if (m == null) return false;
        if (m[0] >= r[0] && m[0] < r[2] && m[1] >= r[1] && m[1] < r[3]) {
            CombatInputHandler.sendEndTurn();
            return true;
        }
        return false;
    }

    /**
     * Draw a player's face (8×8 face region from the skin sheet, plus the hat overlay)
     * at the given screen position, scaled to {@code size}×{@code size} pixels.
     * Falls back to a gray square if the skin texture can't be resolved.
     *
     * @param playerName the player's name, used to look up the skin via the network handler
     */
    private static void drawPlayerHead(DrawContext ctx, MinecraftClient client, String playerName, int x, int y, int size) {
        Identifier skinTex = getPlayerSkinTexture(client, playerName);
        if (skinTex == null) {
            // Fallback: colored square with first initial
            ctx.fill(x, y, x + size, y + size, 0xFF555555);
            if (playerName != null && !playerName.isEmpty()) {
                ctx.drawCenteredTextWithShadow(client.textRenderer,
                    Text.literal(playerName.substring(0, 1).toUpperCase()),
                    x + size / 2, y + (size - 8) / 2, 0xFFFFFFFF);
            }
            return;
        }
        // The Minecraft skin texture is 64×64. The face is at (8,8)-(16,16),
        // the hat overlay at (40,8)-(48,16). Both are 8×8 pixels.
        // Draw face base layer
        //? if <=1.21.1 {
        /*ctx.drawTexture(skinTex, x, y, size, size, 8.0f, 8.0f, 8, 8, 64, 64);
        *///?} else {
        ctx.drawTexture(net.minecraft.client.render.RenderLayer::getGuiTextured, skinTex, x, y, 8.0f, 8.0f, size, size, 8, 8, 64, 64);
        //?}
        // Draw hat overlay on top (semi-transparent second layer on most skins)
        //? if <=1.21.1 {
        /*ctx.drawTexture(skinTex, x, y, size, size, 40.0f, 8.0f, 8, 8, 64, 64);
        *///?} else {
        ctx.drawTexture(net.minecraft.client.render.RenderLayer::getGuiTextured, skinTex, x, y, 40.0f, 8.0f, size, size, 8, 8, 64, 64);
        //?}
    }

    /**
     * Resolve the skin texture {@link Identifier} for a player by name.
     * Returns null if the player isn't in the network handler's player list
     * (e.g. offline, not yet loaded).
     */
    private static Identifier getPlayerSkinTexture(MinecraftClient client, String playerName) {
        if (client.getNetworkHandler() == null || playerName == null) return null;
        for (var entry : client.getNetworkHandler().getPlayerList()) {
            if (playerName.equals(entry.getProfile().getName())) {
                return entry.getSkinTextures().texture();
            }
        }
        return null;
    }

    private void renderPlayerStatusPanel(DrawContext ctx, MinecraftClient client, int screenW) {
        java.util.List<CombatState.PartyMemberHp> partyList = CombatState.getPartyHpList();

        if (!partyList.isEmpty()) {
            renderPartyHpList(ctx, client, partyList);
            return;
        }

        int hp = CombatState.getPlayerHp();
        int maxHp = CombatState.getPlayerMaxHp();
        float hpPct = maxHp > 0 ? (float) hp / maxHp : 0;

        String effects = CombatState.getPlayerEffects();
        List<EffectIcon> icons = parseEffects(effects);

        int headSize = 10;
        int headGap = 3;
        int barW = 120;
        int barH = 10;
        int panelPad = 6;
        int pillH = 11;
        int pillSpacing = 2;
        int rowSpacing = 2;

        // Build each row as we go, wrapping pills to a new line when the current row
        // overflows the panel width. This lets us always show the full effect name.
        // We compute total height first so the panel background fits.
        List<List<EffectRowItem>> rows = new ArrayList<>();
        int availableWidth = barW;
        if (!icons.isEmpty()) {
            List<EffectRowItem> row = new ArrayList<>();
            int rowW = 0;
            for (EffectIcon icon : icons) {
                String label = icon.fullName;
                if (!icon.level.isEmpty()) label += " " + icon.level;
                if (icon.turns > 0) label += " " + icon.turns + "t";
                int pillW = client.textRenderer.getWidth(label) + 6;
                if (rowW + pillW > availableWidth && !row.isEmpty()) {
                    rows.add(row);
                    row = new ArrayList<>();
                    rowW = 0;
                }
                row.add(new EffectRowItem(icon, label, pillW));
                rowW += pillW + pillSpacing;
            }
            if (!row.isEmpty()) rows.add(row);
        }
        int effectsBlockH = 0;
        if (!rows.isEmpty()) {
            effectsBlockH = 4 + rows.size() * pillH + (rows.size() - 1) * rowSpacing;
        }

        int panelW = panelPad + headSize + headGap + barW + panelPad;
        int panelH = panelPad + Math.max(headSize, barH) + effectsBlockH + panelPad;
        int panelX = 8;
        int panelY = 6;

        drawPanel(ctx, panelX, panelY, panelW, panelH);

        // Player head next to the HP bar
        String playerName = client.player != null ? client.player.getName().getString() : null;
        int headX = panelX + panelPad;
        int headY = panelY + panelPad;
        drawPlayerHead(ctx, client, playerName, headX, headY, headSize);

        int barX = headX + headSize + headGap;
        int barY = panelY + panelPad + (headSize - barH) / 2;
        drawHpBar(ctx, "self", barX, barY, barW, barH, hpPct, hpColor(hpPct), 0xFF222222);
        String hpText = hp + "/" + maxHp;
        int textW = client.textRenderer.getWidth(hpText);
        ctx.drawTextWithShadow(client.textRenderer,
            Text.literal(hpText), barX + (barW - textW) / 2, barY + 1, 0xFFFFFFFF);

        if (!rows.isEmpty()) {
            int rowY = barY + barH + 4;
            for (List<EffectRowItem> row : rows) {
                int pillX = barX;
                for (EffectRowItem item : row) {
                    int bgColor = item.icon.isDebuff ? 0xCC882222 : 0xCC226622;
                    int borderColor = item.icon.isDebuff ? 0xFFCC5544 : 0xFF55CC55;
                    int textColor = item.icon.isDebuff ? 0xFFFFCCCC : 0xFFCCFFCC;
                    ctx.fill(pillX - 1, rowY - 1, pillX + item.width + 1, rowY + pillH, borderColor);
                    ctx.fill(pillX, rowY, pillX + item.width, rowY + pillH - 1, bgColor);
                    ctx.drawTextWithShadow(client.textRenderer,
                        Text.literal(item.label), pillX + 3, rowY + 2, textColor);
                    pillX += item.width + pillSpacing;
                }
                rowY += pillH + rowSpacing;
            }
        }
    }

    /** One pill in the effects row — precomputed width so we can wrap cleanly. */
    private record EffectRowItem(EffectIcon icon, String label, int width) {}

    private void renderPartyHpList(DrawContext ctx, MinecraftClient client,
                                    java.util.List<CombatState.PartyMemberHp> members) {
        int panelPad = 6;
        int headSz = 8;
        int headGp = 2;
        int barW = 90;
        int barH = 8;
        int nameW = 50;
        int rowH = Math.max(headSz, barH) + 4;
        int panelW = panelPad + headSz + headGp + nameW + 4 + barW + panelPad;
        int panelH = panelPad + members.size() * rowH + panelPad - 2;
        int panelX = 8;
        int panelY = 6;

        drawPanel(ctx, panelX, panelY, panelW, panelH);

        int y = panelY + panelPad;
        boolean first = true;
        for (CombatState.PartyMemberHp member : members) {
            float hpPct = member.maxHp() > 0 ? (float) member.hp() / member.maxHp() : 0;
            int nameColor;
            int hpBarColor;
            String prefix;

            if (member.dead()) {
                nameColor = 0xFF888888;
                hpBarColor = 0xFF553333;
                prefix = "\u2620 "; // skull
            } else if (first) {
                nameColor = 0xFFFFFF55; // yellow for self
                hpBarColor = hpPct > 0.5f ? 0xFF55FF55 : hpPct > 0.25f ? 0xFFFFFF55 : 0xFFFF5555;
                prefix = "";
            } else {
                nameColor = 0xFFAAFFAA;
                hpBarColor = hpPct > 0.5f ? 0xFF44BB44 : hpPct > 0.25f ? 0xFFBBBB44 : 0xFFBB4444;
                prefix = "";
            }

            String displayName = prefix + member.name();
            if (client.textRenderer.getWidth(displayName) > nameW) {
                while (client.textRenderer.getWidth(displayName + "..") > nameW && displayName.length() > prefix.length() + 1) {
                    displayName = displayName.substring(0, displayName.length() - 1);
                }
                displayName += "..";
            }

            // Player head
            drawPlayerHead(ctx, client, member.name(), panelX + panelPad, y, headSz);

            ctx.drawTextWithShadow(client.textRenderer,
                Text.literal(displayName), panelX + panelPad + headSz + headGp, y, nameColor);

            int barX = panelX + panelPad + headSz + headGp + nameW + 4;
            if (member.dead()) {
                ctx.fill(barX, y, barX + barW, y + barH, 0xFF222222);
            } else {
                drawHpBar(ctx, "party:" + member.name(), barX, y, barW, barH,
                    hpPct, hpBarColor, 0xFF222222);
            }

            String hpText = member.dead() ? "DEAD" : member.hp() + "/" + member.maxHp();
            int tw = client.textRenderer.getWidth(hpText);
            ctx.drawTextWithShadow(client.textRenderer,
                Text.literal(hpText), barX + (barW - tw) / 2, y, member.dead() ? 0xFFFF5555 : 0xFFFFFFFF);

            y += rowH;
            first = false;
        }
    }

    private void renderAllyRoster(DrawContext ctx, MinecraftClient client, int screenW) {
        Map<Integer, int[]> allies = CombatState.getAllyHpMap();
        Map<Integer, String> allyTypes = CombatState.getAllyTypeMap();
        if (allies.isEmpty()) return;

        int headSize = 14;
        int barW = 40;
        int barH = 5;
        int padding = 3;
        int entryH = headSize + 1;

        int panelPad = 4;
        int headerH = 13;
        int panelContentW = headSize + padding + barW + 30;
        int panelW = panelContentW + panelPad * 2;
        int panelH = headerH + allies.size() * entryH + panelPad;
        int panelX = 8;
        int panelY = 50; // below player status panel

        ctx.fill(panelX - 1, panelY - 1, panelX + panelW + 1, panelY + panelH + 1, 0xFF224422);
        ctx.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xBB112211);

        ctx.drawTextWithShadow(client.textRenderer,
            Text.literal("\u00a7aAllies (" + allies.size() + ")"),
            panelX + panelPad, panelY + 3, 0xFF55FF55);

        int startX = panelX + panelPad;
        int y = panelY + headerH;

        for (Map.Entry<Integer, int[]> entry : allies.entrySet()) {
            int[] hpData = entry.getValue();
            int eHp = hpData[0];
            int eMaxHp = hpData[1];
            float ePct = eMaxHp > 0 ? (float) eHp / eMaxHp : 0;

            String typeIdFull = allyTypes.getOrDefault(entry.getKey(), "minecraft:wolf");
            String typeId = typeIdFull.contains(";") ? typeIdFull.substring(0, typeIdFull.indexOf(';')) : typeIdFull;

            Identifier headTex = MobHeadTextures.get(typeId);
            if (headTex != null) {
                MobHeadTextures.drawMobHead(ctx, headTex, startX, y, headSize);
            } else {
                int squareColor = MobHeadTextures.getMobColor(typeId);
                ctx.fill(startX, y, startX + headSize, y + headSize, squareColor);
                String initial = MobHeadTextures.getDisplayInitial(typeId);
                ctx.drawCenteredTextWithShadow(client.textRenderer,
                    Text.literal(initial), startX + headSize / 2, y + 3, 0xFFFFFFFF);
            }

            int barX = startX + headSize + padding;
            int barY = y + (headSize - barH) / 2;
            ctx.fill(barX - 1, barY - 1, barX + barW + 1, barY + barH + 1, 0x88000000);
            drawHpBar(ctx, "ally:" + entry.getKey(), barX, barY, barW, barH,
                ePct, hpColor(ePct), 0x00000000);
            ctx.drawTextWithShadow(client.textRenderer,
                Text.literal("\u00a77" + eHp + "/" + eMaxHp),
                barX + barW + 2, barY - 2, 0xFFAAAAAA);

            y += entryH;
        }
    }

    /** Linear interpolation. */
    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    /** Per-channel ARGB interpolation. */
    private static int lerpColor(int from, int to, float t) {
        int a = (int) lerp((from >>> 24) & 0xFF, (to >>> 24) & 0xFF, t);
        int r = (int) lerp((from >> 16) & 0xFF, (to >> 16) & 0xFF, t);
        int g = (int) lerp((from >> 8) & 0xFF, (to >> 8) & 0xFF, t);
        int b = (int) lerp(from & 0xFF, to & 0xFF, t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private void renderTurnBanner(DrawContext ctx, MinecraftClient client, int screenW) {
        boolean fadeBanner = true;
        try { fadeBanner = com.crackedgames.craftics.CrafticsMod.CONFIG.fadeTurnBanner(); } catch (Exception ignored) {}

        String turnText;
        int turnColor;
        int pillColor;
        if (CombatState.isPlayerTurn()) {
            turnText = "YOUR TURN";
            turnColor = 0xFF55FF55;
            pillColor = 0xBB113311;
        } else if (CombatState.isEnemyTurn()) {
            turnText = "ENEMY TURN";
            turnColor = 0xFFFF5555;
            pillColor = 0xBB331111;
        } else {
            turnText = "...";
            turnColor = 0xFFAAAAAA;
            pillColor = 0xBB222222;
        }

        long age = System.currentTimeMillis() - phaseChangeMs;

        // 0\u20131500ms: full "YOUR TURN \u2014 Turn N". 1500\u20132100ms: crossfade and
        // shrink into the compact "Turn N" pill that stays up for the rest
        // of the phase.
        float fadeP;
        if (!fadeBanner || age <= 1500) fadeP = 0f;
        else if (age >= 2100) fadeP = 1f;
        else fadeP = (age - 1500) / 600f;
        fadeP = fadeP * fadeP * (3 - 2 * fadeP); // smoothstep

        int turnNum = CombatState.getTurnNumber();
        String fullText = turnText + " \u2014 Turn " + turnNum;
        String compactText = "Turn " + turnNum;

        int streak = CombatState.getKillStreak();
        String streakBadge = streak >= 2 ? streak + "x" : "";

        int fullTextW = client.textRenderer.getWidth(fullText);
        int compactTextW = client.textRenderer.getWidth(compactText);
        int badgeW = streakBadge.isEmpty() ? 0 : client.textRenderer.getWidth(streakBadge) + 6;
        int badgeArea = badgeW == 0 ? 0 : badgeW + 5;

        int contentW = Math.round(lerp(fullTextW, compactTextW, fadeP));
        int pillW = contentW + 12 + badgeArea;
        int pillH = 14;
        int pillX = screenW / 2 - pillW / 2;
        int pillY = 4;

        // Entrance pop: scale the pill up briefly whenever the phase flips so
        // the turn change registers even when the player is watching the board.
        float pop = age < 240 ? 1.0f + 0.18f * (1.0f - age / 240.0f) : 1.0f;
        boolean scaled = pop > 1.001f;
        if (scaled) {
            float cx = screenW / 2.0f;
            float cy = pillY + pillH / 2.0f;
            ctx.getMatrices().push();
            ctx.getMatrices().translate(cx, cy, 0);
            ctx.getMatrices().scale(pop, pop, 1.0f);
            ctx.getMatrices().translate(-cx, -cy, 0);
        }

        drawPill(ctx, pillX, pillY, pillW, pillH, pillColor);

        int textCx = pillX + 6 + contentW / 2;
        int textY = pillY + 3;

        // Crossfade the two layouts. Text alpha below ~10 renders fully opaque
        // in MC's font renderer, so tiny alphas are skipped instead.
        int fullAlpha = Math.round(255 * (1 - fadeP));
        if (fullAlpha > 10) {
            ctx.drawCenteredTextWithShadow(client.textRenderer, Text.literal(fullText),
                textCx, textY, (turnColor & 0x00FFFFFF) | (fullAlpha << 24));
        }
        int compactAlpha = Math.round(255 * fadeP);
        if (compactAlpha > 10) {
            ctx.drawCenteredTextWithShadow(client.textRenderer, Text.literal(compactText),
                textCx, textY, 0x00CCCCCC | (compactAlpha << 24));
        }

        if (!streakBadge.isEmpty()) {
            int streakColor = streak >= 5 ? 0xFFFF55FF : streak >= 3 ? 0xFFFFAA00 : 0xFFFFFF55;
            int badgeBg = streak >= 5 ? 0xCC442244 : streak >= 3 ? 0xCC443311 : 0xCC444411;
            int badgeX = pillX + pillW - badgeW - 4;
            drawPill(ctx, badgeX, pillY + 2, badgeW, pillH - 4, badgeBg);
            ctx.drawTextWithShadow(client.textRenderer,
                Text.literal(streakBadge), badgeX + 3, textY, streakColor);
        }

        if (scaled) {
            ctx.getMatrices().pop();
        }
    }

    /**
     * Render the turn order display below the turn banner — shows each party member
     * in queue order with the active player highlighted.
     */
    private void renderTurnOrder(DrawContext ctx, MinecraftClient client, int screenW) {
        turnOrderBottomY = 20; // below the banner; pushed down when the panel draws
        java.util.List<CombatState.TurnOrderEntry> order = CombatState.getTurnOrderList();
        if (order.isEmpty()) return; // solo play — nothing to show

        int headSz = 10;
        int headGp = 2;
        int entryH = headSz + 2;
        int panelPad = 4;
        int indicatorW = 6; // width of the ">" arrow
        int maxNameW = 0;
        for (CombatState.TurnOrderEntry e : order) {
            int w = client.textRenderer.getWidth(e.name());
            if (w > maxNameW) maxNameW = w;
        }
        int panelW = panelPad + indicatorW + 2 + headSz + headGp + maxNameW + panelPad;
        int panelH = panelPad + order.size() * entryH + panelPad - 2;
        int panelX = screenW / 2 - panelW / 2;
        int panelY = 22; // below the turn banner
        turnOrderBottomY = panelY + panelH + 2;

        drawPanel(ctx, panelX, panelY, panelW, panelH);

        int y = panelY + panelPad;
        for (CombatState.TurnOrderEntry entry : order) {
            int nameColor;
            String indicator;
            if (entry.isCurrent()) {
                nameColor = 0xFF55FF55; // bright green for active turn
                indicator = "\u25B6"; // right-pointing triangle
                // Highlight background for current player
                ctx.fill(panelX + 1, y - 1, panelX + panelW - 1, y + entryH - 2, 0x44005500);
            } else {
                nameColor = 0xFFAAAAAA; // gray for waiting
                indicator = " ";
            }
            int contentX = panelX + panelPad;
            ctx.drawTextWithShadow(client.textRenderer,
                Text.literal(indicator), contentX, y + 1, 0xFF55FF55);
            int afterIndicator = contentX + indicatorW + 2;
            drawPlayerHead(ctx, client, entry.name(), afterIndicator, y, headSz);
            ctx.drawTextWithShadow(client.textRenderer,
                Text.literal(entry.name()), afterIndicator + headSz + headGp, y + 1, nameColor);
            y += entryH;
        }
    }

    // ─── Enemy Act-Order Strip ───────────────────────────────────────────

    /**
     * During the enemy phase, a centered strip of unit portraits in the order
     * they will act (server sync order, mirrored by CombatState). The unit
     * currently taking its action — learned from the attack-anim event, so
     * best-effort — gets a gold frame; the underline below each portrait
     * reads friend (green) vs foe (red) at a glance.
     */
    private void renderActOrderStrip(DrawContext ctx, MinecraftClient client, int screenW) {
        if (!CombatState.isEnemyTurn()) return;
        java.util.List<Integer> order = CombatState.getUnitActOrder();
        if (order.isEmpty()) return;

        Map<Integer, int[]> enemies = CombatState.getEnemyHpMap();
        Map<Integer, int[]> allies = CombatState.getAllyHpMap();
        Map<Integer, String> enemyTypes = CombatState.getEnemyTypeMap();
        Map<Integer, String> allyTypes = CombatState.getAllyTypeMap();

        // Only units still present in a roster (the dead drop out on sync).
        List<Integer> ids = new ArrayList<>();
        for (int id : order) {
            if (enemies.containsKey(id) || allies.containsKey(id)) ids.add(id);
        }
        if (ids.isEmpty()) return;

        int headSz = 12;
        int gap = 3;
        int maxShown = 10;
        int shown = Math.min(ids.size(), maxShown);
        int overflow = ids.size() - shown;
        String moreLabel = overflow > 0 ? "+" + overflow : null;
        int moreW = moreLabel == null ? 0 : client.textRenderer.getWidth(moreLabel) + 3;

        int pad = 4;
        int stripW = pad + shown * (headSz + gap) - gap + moreW + pad;
        int stripH = pad + headSz + 3 + pad; // portrait + friend/foe underline
        int stripX = screenW / 2 - stripW / 2;
        int stripY = turnOrderBottomY + 2;

        drawPanel(ctx, stripX, stripY, stripW, stripH);

        int actingId = CombatState.getActingEnemyId();
        int x = stripX + pad;
        int y = stripY + pad;
        for (int i = 0; i < shown; i++) {
            int id = ids.get(i);
            boolean isAlly = allies.containsKey(id);
            String typeFull = (isAlly ? allyTypes : enemyTypes).getOrDefault(id, "minecraft:zombie");
            String typeId = typeFull.contains(";") ? typeFull.substring(0, typeFull.indexOf(';')) : typeFull;
            boolean acting = id == actingId;

            if (acting) {
                ctx.fill(x - 2, y - 2, x + headSz + 2, y + headSz + 5, 0xFFFFDD55);
                ctx.fill(x - 1, y - 1, x + headSz + 1, y + headSz + 4, 0xFF221A00);
            }
            Identifier tex = MobHeadTextures.get(typeId);
            if (tex != null) {
                MobHeadTextures.drawMobHead(ctx, tex, x, y, headSz);
            } else {
                ctx.fill(x, y, x + headSz, y + headSz, MobHeadTextures.getMobColor(typeId));
                ctx.drawCenteredTextWithShadow(client.textRenderer,
                    Text.literal(MobHeadTextures.getDisplayInitial(typeId)),
                    x + headSz / 2, y + 2, 0xFFFFFFFF);
            }
            ctx.fill(x, y + headSz + 1, x + headSz, y + headSz + 3,
                isAlly ? 0xFF55CC55 : acting ? 0xFFFFDD55 : 0xFFCC4444);
            x += headSz + gap;
        }
        if (moreLabel != null) {
            ctx.drawTextWithShadow(client.textRenderer, Text.literal(moreLabel),
                x + 1, y + 3, 0xFF888888);
        }
    }

    private record TileTooltipInfo(String title, String line1, String line2) {}

    /**
     * Identify the tile under the cursor and return its tooltip info, or
     * {@code null} if the tile is just plain ground. Reads two block layers:
     * the floor block (water/lava/snow/void) and the column above it
     * (obstacles, plants, cobwebs, cactus).
     */
    private static TileTooltipInfo lookupTileTooltip(net.minecraft.world.World world,
                                                     net.minecraft.util.math.BlockPos floorPos) {
        net.minecraft.block.Block floor = world.getBlockState(floorPos).getBlock();
        net.minecraft.block.Block above = world.getBlockState(floorPos.up()).getBlock();

        // Stealth plants — tall grass / large fern (2-block-tall).
        if (above == net.minecraft.block.Blocks.TALL_GRASS) {
            return new TileTooltipInfo("\u00a7a\u00a7lTall Grass",
                "\u00a7fEntities in tall grass are hidden.",
                "\u00a77Attack to break it (1 AP), or hit from adjacent.");
        }
        if (above == net.minecraft.block.Blocks.LARGE_FERN) {
            return new TileTooltipInfo("\u00a72\u00a7lLarge Fern",
                "\u00a7fEntities in this fern are hidden.",
                "\u00a77Attack to break it (1 AP), or hit from adjacent.");
        }

        // Cobweb — slow obstacle.
        if (above == net.minecraft.block.Blocks.COBWEB) {
            return new TileTooltipInfo("\u00a7f\u00a7lCobweb",
                "\u00a7fSlows movement.",
                "\u00a77Walking through clears the web.");
        }

        // Cactus — damaging obstacle.
        if (above == net.minecraft.block.Blocks.CACTUS) {
            return new TileTooltipInfo("\u00a72\u00a7lCactus",
                "\u00a7fBlocks movement and line of sight.",
                "\u00a77Damages anything that touches it.");
        }

        // Floor hazards — lava and magma.
        if (floor == net.minecraft.block.Blocks.LAVA
            || floor == net.minecraft.block.Blocks.MAGMA_BLOCK) {
            return new TileTooltipInfo("\u00a7c\u00a7lLava",
                "\u00a7fDeals 10 damage when stepped on.",
                "\u00a77Avoid walking through.");
        }

        // Powder snow — sinks and freezes.
        if (floor == net.minecraft.block.Blocks.POWDER_SNOW) {
            return new TileTooltipInfo("\u00a7b\u00a7lPowder Snow",
                "\u00a7fSinks you in.",
                "\u00a77Freeze damage unless wearing leather boots.");
        }

        // Water — distinguish shallow (walkable) from deep (instant kill).
        if (floor == net.minecraft.block.Blocks.WATER) {
            net.minecraft.block.Block below = world.getBlockState(floorPos.down()).getBlock();
            if (below == net.minecraft.block.Blocks.WATER) {
                return new TileTooltipInfo("\u00a73\u00a7lDeep Water",
                    "\u00a7fInstantly fatal.",
                    "\u00a77Walk around or use a boat to cross.");
            }
            return new TileTooltipInfo("\u00a79\u00a7lWater",
                "\u00a7fWalkable. Standing here applies Soaked.",
                "\u00a77Soaked entities take more lightning damage.");
        }

        // Air at the floor level — sunken pit (solid below) or void (nothing below).
        if (floor.getDefaultState().isAir()) {
            net.minecraft.block.Block below = world.getBlockState(floorPos.down()).getBlock();
            if (!below.getDefaultState().isAir()) {
                return new TileTooltipInfo("\u00a77\u00a7lSunken Pit",
                    "\u00a7fLower terrain. Walkable.",
                    "\u00a77Stand here for slight cover from line-of-sight checks.");
            }
            return new TileTooltipInfo("\u00a74\u00a7lVoid",
                "\u00a7fFalling here is instantly fatal.",
                "\u00a77Mind your step.");
        }

        // Generic obstacle — any solid block above the floor that we didn't
        // already special-case. Covers logs (fallen trees), stems, stone
        // boulders, leaves, etc.
        net.minecraft.block.BlockState aboveState = world.getBlockState(floorPos.up());
        if (!aboveState.isAir()) {
            return new TileTooltipInfo("\u00a78\u00a7lObstacle",
                "\u00a7fBlocks movement and line of sight.",
                "\u00a77Path around it or destroy it.");
        }

        return null;
    }

    /**
     * Hover tooltip for any obstacle/hazard tile in the arena. Positioned just
     * below the enemy roster (top-right) so it never covers the play area.
     * Skipped when the cursor is on an enemy — the inspect panel takes priority
     * there.
     */
    private void renderTileTooltip(DrawContext ctx, MinecraftClient client, int screenW, int screenH) {
        com.crackedgames.craftics.core.GridPos hover = CombatState.getHoveredTile();
        if (hover == null || client.world == null) return;
        if (CombatState.getHoveredEnemyId() != -1) return;

        net.minecraft.util.math.BlockPos floorPos = new net.minecraft.util.math.BlockPos(
            CombatState.getArenaOriginX() + hover.x(),
            CombatState.getArenaOriginY(),
            CombatState.getArenaOriginZ() + hover.z());

        TileTooltipInfo info = lookupTileTooltip(client.world, floorPos);
        if (info == null) return;

        int padX = 6;
        int padY = 4;
        // Match the enemy roster's width and right edge so the tooltip looks
        // like a continuation of the panel above instead of a separate box.
        int panelW = enemyRosterPanelW;
        int panelX = enemyRosterRightX > 0 ? enemyRosterRightX - panelW : screenW - panelW - 6;
        int contentW = panelW - padX * 2;

        // Wrap long description lines to the available content width.
        java.util.List<net.minecraft.text.OrderedText> line1Wrapped =
            client.textRenderer.wrapLines(Text.literal(info.line1()), contentW);
        java.util.List<net.minecraft.text.OrderedText> line2Wrapped =
            client.textRenderer.wrapLines(Text.literal(info.line2()), contentW);
        int totalLines = 1 + line1Wrapped.size() + line2Wrapped.size();
        int lineH = 10;
        int panelH = totalLines * lineH + padY * 2;

        int panelY = enemyRosterBottomY;
        int maxY = screenH - panelH - 6;
        if (panelY > maxY) panelY = Math.max(4, maxY);

        drawPanel(ctx, panelX, panelY, panelW, panelH);
        int textX = panelX + padX;
        int textY = panelY + padY;
        ctx.drawTextWithShadow(client.textRenderer, Text.literal(info.title()), textX, textY, 0xFFFFFFFF);
        textY += lineH;
        for (net.minecraft.text.OrderedText line : line1Wrapped) {
            ctx.drawTextWithShadow(client.textRenderer, line, textX, textY, 0xFFFFFFFF);
            textY += lineH;
        }
        for (net.minecraft.text.OrderedText line : line2Wrapped) {
            ctx.drawTextWithShadow(client.textRenderer, line, textX, textY, 0xFFAAAAAA);
            textY += lineH;
        }
    }

    private void renderEnemyRoster(DrawContext ctx, MinecraftClient client, int screenW) {
        Map<Integer, int[]> enemies = CombatState.getEnemyHpMap();
        Map<Integer, String> types = CombatState.getEnemyTypeMap();

        int hoveredId = CombatState.getHoveredEnemyId();

        // Hovered party player → player inspect panel (friendly info, shown even under blindness).
        String hoveredPlayer = CombatState.getHoveredPlayerUuid();
        if (hoveredPlayer != null) {
            CombatState.PlayerStats ps = CombatState.getPlayerStats(hoveredPlayer);
            if (ps != null) {
                renderPlayerInspectPanel(ctx, client, screenW, ps);
                enemyRosterPanelW = 130;
                enemyRosterRightX = screenW - 8;
                enemyRosterBottomY = 200;
                return;
            }
        }

        // Hovered ally → reuse the enemy inspect panel; ally type strings carry full stats.
        if (hoveredId != -1 && CombatState.getAllyHpMap().containsKey(hoveredId)) {
            String allyType = CombatState.getAllyTypeMap().getOrDefault(hoveredId, "minecraft:wolf");
            renderInspectPanel(ctx, client, screenW, hoveredId,
                CombatState.getAllyHpMap().get(hoveredId), allyType);
            enemyRosterPanelW = 120;
            enemyRosterRightX = screenW - 8;
            enemyRosterBottomY = 200;
            return;
        }

        if (enemies.isEmpty()) return;

        // Blindness hides enemy inspection entirely — no hover panel, no stat readout.
        // The standard enemy roster (to the side) still shows below.
        if (!CombatState.hasBlindness() && hoveredId != -1 && enemies.containsKey(hoveredId)) {
            String typeIdRaw = types.getOrDefault(hoveredId, "minecraft:zombie");
            renderInspectPanel(ctx, client, screenW, hoveredId, enemies.get(hoveredId), typeIdRaw);
            // Mirror renderInspectPanel's dimensions so the tile tooltip can
            // align under it. Width matches the boss/non-boss split there.
            boolean isBoss = typeIdRaw.contains(";boss=");
            int inspectPanelW = isBoss ? 140 : 120;
            enemyRosterPanelW = inspectPanelW;
            enemyRosterRightX = screenW - 8;
            enemyRosterBottomY = 200;
            return;
        }

        // ── Vertical head roster ─────────────────────────────────────────────
        // No outlining container: a single column of mob heads down the right
        // edge, each reddening as its owner loses HP so the party's health reads
        // at a glance. The boss (if any) keeps the roster's only persistent HP
        // bar, floating above the column.
        int headSize = 18;
        int gap = 4;
        int margin = 6;
        int rightX = screenW - margin;        // heads/bar right-align to here
        int headX = rightX - headSize;

        // Identify the boss up front — it renders as a bar, not a head, so it
        // must not also appear in the head column.
        int bossEntityId = -1;
        String bossDisplayName = null;
        for (Map.Entry<Integer, String> tEntry : types.entrySet()) {
            String tRaw = tEntry.getValue();
            if (tRaw.contains(";boss=") && enemies.containsKey(tEntry.getKey())) {
                bossEntityId = tEntry.getKey();
                int bIdx = tRaw.indexOf(";boss=") + 6;
                int bEnd = tRaw.indexOf(';', bIdx);
                bossDisplayName = bEnd > 0 ? tRaw.substring(bIdx, bEnd) : tRaw.substring(bIdx);
                break;
            }
        }
        boolean hasBoss = bossEntityId >= 0;

        List<Map.Entry<Integer, int[]>> nonBoss = new ArrayList<>();
        for (Map.Entry<Integer, int[]> entry : enemies.entrySet()) {
            if (entry.getKey() != bossEntityId) nonBoss.add(entry);
        }

        int y = 4;

        // Boss bar (kept distinct): a floating full-width HP bar with the name
        // above it. Frame turns molten gold and a phase badge appears in phase
        // two. No surrounding panel — just the bar's own dark track.
        if (hasBoss) {
            int[] bossHp = enemies.get(bossEntityId);
            int bHp = bossHp[0];
            int bMaxHp = bossHp[1];
            float bPct = bMaxHp > 0 ? (float) bHp / bMaxHp : 0;
            String bTypeFull = types.getOrDefault(bossEntityId, "");
            boolean bossPhaseTwo = bTypeFull.contains(";phase=2");

            int bossBarW = 150;
            int bossBarX = rightX - bossBarW;
            int bossBarH = 8;

            String bossLabel = "§4§l☠ " + bossDisplayName;
            if (client.textRenderer.getWidth(bossLabel + " ☠") > bossBarW) {
                while (bossLabel.length() > 6
                        && client.textRenderer.getWidth(bossLabel + ".. ☠") > bossBarW) {
                    bossLabel = bossLabel.substring(0, bossLabel.length() - 1);
                }
                bossLabel = bossLabel + ".. ☠";
            } else {
                bossLabel = bossLabel + " ☠";
            }
            ctx.drawCenteredTextWithShadow(client.textRenderer,
                Text.literal(bossLabel), bossBarX + bossBarW / 2, y, 0xFFFF5555);
            y += 11;

            int frameColor = bossPhaseTwo ? 0xFF885500 : 0xFF440000;
            ctx.fill(bossBarX - 1, y - 1, bossBarX + bossBarW + 1, y + bossBarH + 1, frameColor);
            int bossColor = bPct > 0.5f ? 0xFFCC2222 : bPct > 0.25f ? 0xFFCC6600 : 0xFFFF0000;
            drawHpBar(ctx, "boss:" + bossEntityId, bossBarX, y, bossBarW, bossBarH,
                bPct, bossColor, 0xFF220000);
            String bossHpText = "§7" + bHp + "/" + bMaxHp;
            int bossHpTw = client.textRenderer.getWidth(bossHpText);
            ctx.drawTextWithShadow(client.textRenderer,
                Text.literal(bossHpText), bossBarX + (bossBarW - bossHpTw) / 2, y, 0xFFEEEEEE);
            if (bossPhaseTwo) {
                String phaseBadge = "§6§lII";
                int badgeW = client.textRenderer.getWidth(phaseBadge);
                ctx.drawTextWithShadow(client.textRenderer,
                    Text.literal(phaseBadge), bossBarX + bossBarW - badgeW - 2, y, 0xFFFFAA00);
            }
            y += bossBarH + 6;
        }

        // Regular enemies: a single vertical column of heads, reddening by damage.
        // Hover an enemy for its exact HP bar and numbers on the inspect panel.
        int maxHeads = 12;
        int shown = 0;
        for (Map.Entry<Integer, int[]> entry : nonBoss) {
            if (shown >= maxHeads) break;

            int[] hpData = entry.getValue();
            int eHp = hpData[0];
            int eMaxHp = hpData[1];
            float ePct = eMaxHp > 0 ? (float) eHp / eMaxHp : 0;
            float redAmount = 1f - ePct;

            String typeIdFull = types.getOrDefault(entry.getKey(), "minecraft:zombie");
            String typeId = typeIdFull.contains(";") ? typeIdFull.substring(0, typeIdFull.indexOf(';')) : typeIdFull;

            // Per-head dark backing (not a list container) for legibility over
            // bright terrain.
            ctx.fill(headX - 1, y - 1, headX + headSize + 1, y + headSize + 1, 0x66000000);

            Identifier headTex = MobHeadTextures.get(typeId);
            if (headTex != null) {
                MobHeadTextures.drawMobHeadTinted(ctx, headTex, headX, y, headSize, redAmount);
            } else {
                int squareColor = MobHeadTextures.tintTowardRed(MobHeadTextures.getMobColor(typeId), redAmount);
                ctx.fill(headX, y, headX + headSize, y + headSize, squareColor);
                ctx.fill(headX + 1, y + 1, headX + headSize - 1, y + headSize - 1,
                    (squareColor & 0x00FFFFFF) | 0xCC000000);
                String initial = MobHeadTextures.getDisplayInitial(typeId);
                ctx.drawCenteredTextWithShadow(client.textRenderer,
                    Text.literal(initial), headX + headSize / 2, y + 5, 0xFFFFFFFF);
            }

            y += headSize + gap;
            shown++;
        }

        int remaining = nonBoss.size() - shown;
        if (remaining > 0) {
            String more = "§8+" + remaining;
            int tw = client.textRenderer.getWidth(more);
            ctx.drawTextWithShadow(client.textRenderer, Text.literal(more), rightX - tw, y, 0xFFAAAAAA);
            y += 10;
        }

        // Expose geometry so the floor-tile tooltip can right-align beneath the
        // head column instead of overlapping it.
        enemyRosterRightX = rightX;
        enemyRosterPanelW = 130;
        enemyRosterBottomY = y + 2;
    }

    /** Convert a numeric string level to a Roman numeral for display (1..10 range). */
    private static String toRoman(String lvlStr) {
        int lvl;
        try { lvl = Integer.parseInt(lvlStr); } catch (NumberFormatException e) { return lvlStr; }
        return switch (lvl) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            case 7 -> "VII";
            case 8 -> "VIII";
            case 9 -> "IX";
            case 10 -> "X";
            default -> String.valueOf(lvl);
        };
    }

    // ─── Inspect Panel (hover detail) ────────────────────────────────────

    private void renderInspectPanel(DrawContext ctx, MinecraftClient client, int screenW,
                                     int entityId, int[] hpData, String typeIdRaw) {
        int eHp = hpData[0];
        int eMaxHp = hpData[1];
        float ePct = eMaxHp > 0 ? (float) eHp / eMaxHp : 0;
        boolean isAlly = typeIdRaw.contains(";ally");

        String[] parts = typeIdRaw.split(";");
        String typeId = parts[0];
        List<String> enemyEffects = new ArrayList<>();
        List<String> enemyEnchants = new ArrayList<>();
        String bossName = null;
        String stackName = null;
        int bossAtk = -1, bossDef = -1, bossSpd = -1, bossRange = -1;
        for (int i = 1; i < parts.length; i++) {
            if (parts[i].startsWith("boss=")) bossName = parts[i].substring(5);
            else if (parts[i].startsWith("name=")) stackName = parts[i].substring(5);
            else if (parts[i].startsWith("atk=")) bossAtk = Integer.parseInt(parts[i].substring(4));
            else if (parts[i].startsWith("def=")) bossDef = Integer.parseInt(parts[i].substring(4));
            else if (parts[i].startsWith("spd=")) bossSpd = Integer.parseInt(parts[i].substring(4));
            else if (parts[i].startsWith("range=")) bossRange = Integer.parseInt(parts[i].substring(6));
            else if (parts[i].startsWith("ench=")) {
                String encoded = parts[i].substring(5);
                if (!encoded.isEmpty()) {
                    for (String pair : encoded.split(",")) {
                        int sep = pair.indexOf(':');
                        if (sep <= 0) continue;
                        String name = pair.substring(0, sep).replace('_', ' ');
                        String lvl = pair.substring(sep + 1);
                        enemyEnchants.add(name + " " + toRoman(lvl));
                    }
                }
            }
            else if (parts[i].startsWith("mv=")) { /* movement style — not a status effect */ }
            else if (parts[i].startsWith("phase=")) { /* boss phase badge — not a status effect */ }
            else if (parts[i].equals("ally")) { /* ally tag — not a status effect */ }
            else enemyEffects.add(parts[i]);
        }

        String displayName;
        if (bossName != null) {
            displayName = bossName;
        } else if (stackName != null) {
            displayName = stackName;
        } else {
            String rawId = typeId;
            int colon = rawId.indexOf(':');
            if (colon >= 0) rawId = rawId.substring(colon + 1);
            displayName = rawId.substring(0, 1).toUpperCase() + rawId.substring(1).replace('_', ' ');
        }

        // Theme tag (water/jungle/cold) — tells the player what a hit from this
        // unit will inflict before they eat one. Reads the same MobThemeTags
        // registry the server resolves on-hit effects from, so the two can't drift.
        String themeLine = null;
        int themeColor = 0;
        if (!isAlly) {
            if (com.crackedgames.craftics.combat.MobThemeTags.isWater(typeId)) {
                themeLine = "§b◆ Hit: Soaked "
                    + com.crackedgames.craftics.combat.MobThemeTags.SOAK_TURNS + "t";
                themeColor = 0xFF55CCFF;
            } else if (com.crackedgames.craftics.combat.MobThemeTags.isJungle(typeId)) {
                themeLine = "§a◆ Hit: Poison "
                    + com.crackedgames.craftics.combat.MobThemeTags.POISON_TURNS + "t";
                themeColor = 0xFF66DD66;
            } else if (com.crackedgames.craftics.combat.MobThemeTags.isCold(typeId)) {
                themeLine = "§f◆ Hit: Weakness "
                    + com.crackedgames.craftics.combat.MobThemeTags.WEAKNESS_TURNS + "t";
                themeColor = 0xFFCCEEFF;
            }
        }

        int panelW = bossName != null ? 140 : 120;
        int panelH = 100 + (themeLine != null ? 10 : 0) + enemyEffects.size() * 10 + (enemyEnchants.isEmpty() ? 0 : (enemyEnchants.size() * 10 + 4));
        int panelX = screenW - panelW - 8;
        int panelY = 4;

        // Use standard panel with boss tint
        int bgColor = bossName != null ? 0xBB2A0A0A : PANEL_BG;
        ctx.fill(panelX - 1, panelY - 1, panelX + panelW + 1, panelY + panelH + 1, PANEL_BORDER);
        ctx.fill(panelX, panelY, panelX + panelW, panelY + panelH, bgColor);
        // Name header bar — green-tinted for allies so friend/foe reads at a glance.
        int headerColor = bossName != null ? 0xBB8B0000 : isAlly ? 0xBB1B4A1B : 0xBB222244;
        ctx.fill(panelX, panelY, panelX + panelW, panelY + 14, headerColor);

        int nameColor = bossName != null ? 0xFFFFAA00 : isAlly ? 0xFF66DD66 : 0xFFFFFFFF;
        Identifier inspectHead = MobHeadTextures.get(typeId);
        if (inspectHead != null) {
            MobHeadTextures.drawMobHead(ctx, inspectHead, panelX, panelY, 14);
            ctx.drawTextWithShadow(client.textRenderer,
                Text.literal("\u00a7l" + displayName), panelX + 16, panelY + 3, nameColor);
        } else {
            ctx.drawCenteredTextWithShadow(client.textRenderer,
                Text.literal("\u00a7l" + displayName), panelX + panelW / 2, panelY + 3, nameColor);
        }

        int y = panelY + 17;

        // HP bar
        int barW = panelW - 4;
        int barH = 8;
        drawHpBar(ctx, "insp:" + entityId, panelX + 2, y, barW, barH,
            ePct, hpColor(ePct), 0xFF333333);
        ctx.drawCenteredTextWithShadow(client.textRenderer,
            Text.literal(eHp + " / " + eMaxHp + " HP"), panelX + panelW / 2, y, 0xFFFFFFFF);
        y += barH + 5;

        int atk = bossAtk >= 0 ? bossAtk : getDefaultStat(typeId, "atk");
        int def = bossDef >= 0 ? bossDef : getDefaultStat(typeId, "def");
        int spd = bossSpd >= 0 ? bossSpd : getDefaultStat(typeId, "spd");
        int range = bossRange >= 0 ? bossRange : getDefaultStat(typeId, "range");

        ctx.drawTextWithShadow(client.textRenderer,
            Text.literal("\u00a7c\u2694 ATK " + atk + "  \u00a79\u26E8 DEF " + def),
            panelX + 4, y, 0xFFCCCCCC);
        y += 11;
        ctx.drawTextWithShadow(client.textRenderer,
            Text.literal("\u00a7b\u2B06 SPD " + spd + "  \u00a7e\u27B3 RNG " + range),
            panelX + 4, y, 0xFFCCCCCC);
        y += 11;

        if (themeLine != null) {
            ctx.drawTextWithShadow(client.textRenderer,
                Text.literal(themeLine), panelX + 4, y, themeColor);
            y += 10;
        }

        if (!enemyEffects.isEmpty()) {
            for (String eff : enemyEffects) {
                boolean isDebuff = eff.equals("Stunned") || eff.equals("Slowed") || eff.equals("Burning");
                String icon = isDebuff ? "\u2620 " : "\u2728 ";
                int effColor = isDebuff ? 0xFFFF6666 : 0xFFFFAA00;
                ctx.drawTextWithShadow(client.textRenderer,
                    Text.literal(icon + eff), panelX + 4, y, effColor);
                y += 10;
            }
        } else {
            ctx.drawTextWithShadow(client.textRenderer,
                Text.literal("\u00a78No effects"), panelX + 4, y, 0xFF555555);
            y += 10;
        }

        // Enchantments on enemy gear
        if (!enemyEnchants.isEmpty()) {
            y += 2;
            ctx.drawTextWithShadow(client.textRenderer,
                Text.literal("\u00a7d\u2728 Enchants:"), panelX + 4, y, 0xFFDD88FF);
            y += 10;
            for (String ench : enemyEnchants) {
                ctx.drawTextWithShadow(client.textRenderer,
                    Text.literal("\u00a7d  " + ench), panelX + 4, y, 0xFFCC99FF);
                y += 10;
            }
        }

        String behavior = getAIHint(typeId);
        if (behavior != null) {
            ctx.drawTextWithShadow(client.textRenderer,
                Text.literal("\u00a78" + behavior), panelX + 4, y, 0xFF888888);
        }
    }

    /** Hover detail panel for a party player \u2014 mirrors the enemy inspect panel layout. */
    private void renderPlayerInspectPanel(DrawContext ctx, MinecraftClient client, int screenW,
                                          CombatState.PlayerStats ps) {
        // Active effects are only known for the local client player.
        boolean isSelf = client.player != null
            && client.player.getUuid().toString().equals(ps.uuid());
        List<String> effects = new ArrayList<>();
        if (isSelf) {
            String fx = CombatState.getPlayerEffects();
            if (fx != null && !fx.isEmpty()) {
                for (String part : fx.split(" \\| ")) {
                    String t = part.trim();
                    if (!t.isEmpty()) effects.add(t);
                }
            }
        }

        int effectRows = effects.isEmpty() ? (isSelf ? 1 : 0) : effects.size();
        int panelW = 130;
        int panelH = 62 + effectRows * 10;
        int panelX = screenW - panelW - 8;
        int panelY = 4;

        ctx.fill(panelX - 1, panelY - 1, panelX + panelW + 1, panelY + panelH + 1, PANEL_BORDER);
        ctx.fill(panelX, panelY, panelX + panelW, panelY + panelH, PANEL_BG);
        // Blue-tinted header marks a player \u2014 distinct from enemy (dark) and ally (green).
        ctx.fill(panelX, panelY, panelX + panelW, panelY + 14, 0xBB1B3A5A);

        String title = (ps.dead() ? "\u2620 " : "") + ps.name();
        ctx.drawCenteredTextWithShadow(client.textRenderer,
            Text.literal("\u00a7l" + title), panelX + panelW / 2, panelY + 3,
            ps.dead() ? 0xFFFF5555 : 0xFF88CCFF);

        int y = panelY + 17;

        // HP bar
        int barW = panelW - 4;
        int barH = 8;
        float pct = ps.maxHp() > 0 ? (float) ps.hp() / ps.maxHp() : 0;
        drawHpBar(ctx, "pinsp:" + ps.uuid(), panelX + 2, y, barW, barH,
            pct, hpColor(pct), 0xFF333333);
        ctx.drawCenteredTextWithShadow(client.textRenderer,
            Text.literal(ps.hp() + " / " + ps.maxHp() + " HP"), panelX + panelW / 2, y, 0xFFFFFFFF);
        y += barH + 5;

        ctx.drawTextWithShadow(client.textRenderer,
            Text.literal("\u00a7c\u2694 ATK " + ps.atk() + "  \u00a79\u26e8 AC " + ps.ac()),
            panelX + 4, y, 0xFFCCCCCC);
        y += 11;
        ctx.drawTextWithShadow(client.textRenderer,
            Text.literal("\u00a7b\u2b06 SPD " + ps.speed() + "  \u00a7e\u26a1 AP " + ps.ap()),
            panelX + 4, y, 0xFFCCCCCC);
        y += 11;

        if (!effects.isEmpty()) {
            for (String eff : effects) {
                ctx.drawTextWithShadow(client.textRenderer,
                    Text.literal("\u00a7e\u2728 " + eff), panelX + 4, y, 0xFFFFAA00);
                y += 10;
            }
        } else if (isSelf) {
            ctx.drawTextWithShadow(client.textRenderer,
                Text.literal("\u00a78No effects"), panelX + 4, y, 0xFF555555);
        }
    }

    // ─── 4. Mode Pill (Bottom-Center) ────────────────────────────────────

    private void renderModePill(DrawContext ctx, MinecraftClient client, int screenW, int screenH) {
        CombatInputHandler.ActionMode mode = CombatInputHandler.getActionMode(client);
        String modeText;
        int modeColor;
        int pillBg;
        switch (mode) {
            case MOVE -> { modeText = "MOVE"; modeColor = 0xFF55FF55; pillBg = 0xBB113311; }
            case MELEE_ATTACK -> { modeText = "ATTACK"; modeColor = 0xFFFF5555; pillBg = 0xBB331111; }
            case RANGED_ATTACK -> { modeText = "RANGED"; modeColor = 0xFFFFAA00; pillBg = 0xBB332211; }
            case USE_ITEM -> { modeText = "ITEM"; modeColor = 0xFFFF55FF; pillBg = 0xBB331133; }
            case LEAD -> {
                boolean picked = CombatState.getLeadSelectedAllyId() != null;
                modeText = picked ? "COMMAND" : "COMMAND: PICK ALLY";
                modeColor = 0xFF66BBFF; pillBg = 0xBB112233;
            }
            default -> { modeText = "MOVE"; modeColor = 0xFF55FF55; pillBg = 0xBB113311; }
        }

        // Show the AP price on attack modes so planning a turn doesn't require
        // memorizing costs.
        if (mode == CombatInputHandler.ActionMode.MELEE_ATTACK
            || mode == CombatInputHandler.ActionMode.RANGED_ATTACK) {
            int cost = 2;
            try { cost = com.crackedgames.craftics.CrafticsMod.CONFIG.attackApCost(); } catch (Exception ignored) {}
            modeText = modeText + " · " + cost + " AP";
        }

        int textW = client.textRenderer.getWidth(modeText);
        int pillW = textW + 12;
        int pillH = 14;
        int pillX = screenW / 2 - pillW / 2;
        int pillY = screenH - 58;

        drawPill(ctx, pillX, pillY, pillW, pillH, pillBg);
        ctx.drawCenteredTextWithShadow(client.textRenderer,
            Text.literal(modeText), screenW / 2, pillY + 3, modeColor);
    }

    // ─── End Turn Button (Bottom-Center, above the mode pill) ───────────

    /** Localized name of the end-turn keybind, for the button label. */
    private static String endTurnKeyName() {
        try {
            var key = com.crackedgames.craftics.CrafticsClient.getEndTurnKey();
            if (key != null) {
                String name = key.getBoundKeyLocalizedText().getString();
                if (!name.isEmpty()) return name.toUpperCase(java.util.Locale.ROOT);
            }
        } catch (Exception ignored) {}
        return "R";
    }

    /**
     * Clickable End Turn button. Before this the only way to end a turn was
     * knowing the R keybind — invisible to new players. Shown only on the
     * local player's turn; once AP and movement are both spent, the border
     * pulses to nudge "you're done here."
     */
    private void renderEndTurnButton(DrawContext ctx, MinecraftClient client,
                                     int screenW, int screenH, float uiScale) {
        if (!CombatState.isLocalPlayersTurn()) return;

        String label = "END TURN";
        String keyLabel = "[" + endTurnKeyName() + "]";
        int labelW = client.textRenderer.getWidth(label);
        int keyW = client.textRenderer.getWidth(keyLabel);
        int btnW = labelW + keyW + 17;
        int btnH = 16;
        int btnX = screenW / 2 - btnW / 2;
        int btnY = screenH - 78;

        boolean hovered = isMouseOver(client, uiScale, btnX, btnY, btnW, btnH);
        boolean allSpent = CombatState.getApRemaining() <= 0
            && CombatState.getMovePointsRemaining() <= 0;

        int border = 0xFF2E7D4F;
        if (allSpent) {
            float pulse = (float) (0.5 + 0.5 * Math.sin(System.currentTimeMillis() * 0.006));
            border = lerpColor(0xFF2E7D4F, 0xFF7FFFA0, pulse);
        }
        if (hovered) border = 0xFFAAFFC0;
        int bg = hovered ? 0xEE1C4A2A : 0xCC10301C;

        ctx.fill(btnX - 1, btnY - 1, btnX + btnW + 1, btnY + btnH + 1, border);
        ctx.fill(btnX, btnY, btnX + btnW, btnY + btnH, bg);
        ctx.drawTextWithShadow(client.textRenderer, Text.literal(label),
            btnX + 6, btnY + 4, hovered ? 0xFFFFFFFF : 0xFF99FF99);
        ctx.drawTextWithShadow(client.textRenderer, Text.literal(keyLabel),
            btnX + btnW - keyW - 5, btnY + 4, 0xFF77AA77);

        endTurnBtnRect = new int[]{
            Math.round(btnX * uiScale), Math.round(btnY * uiScale),
            Math.round((btnX + btnW) * uiScale), Math.round((btnY + btnH) * uiScale)};
    }

    // ─── Move Cost Cursor Label ──────────────────────────────────────────

    /**
     * Small "N SPD" tag beside the cursor while hovering a reachable move
     * tile, fed by the path the overlay renderer previews — answers "how much
     * of my movement does this step cost?" without leaving the cursor.
     */
    private void renderMoveCostLabel(DrawContext ctx, MinecraftClient client,
                                     int screenW, int screenH, float uiScale) {
        if (!CombatState.isLocalPlayersTurn()) return;
        if (CombatInputHandler.getActionMode(client) != CombatInputHandler.ActionMode.MOVE) return;
        com.crackedgames.craftics.core.GridPos hover = CombatState.getHoveredTile();
        java.util.List<com.crackedgames.craftics.core.GridPos> path = TileOverlayRenderer.getActivePath();
        if (hover == null || path == null || path.isEmpty()
            || !hover.equals(TileOverlayRenderer.getActivePathTarget())) return;

        double[] m = mouseGuiPos(client);
        if (m == null) return;
        int mx = (int) (m[0] / uiScale);
        int my = (int) (m[1] / uiScale);

        int cost = path.size();
        int remaining = CombatState.getMovePointsRemaining();
        String label = cost + " SPD";
        int tw = client.textRenderer.getWidth(label);
        int bx = Math.min(mx + 12, screenW - tw - 6);
        int by = Math.min(my + 12, screenH - 14);

        ctx.fill(bx - 3, by - 2, bx + tw + 3, by + 10, 0xB0101822);
        int color = cost >= remaining ? 0xFFFFDD55 : 0xFF7FD7FF;
        ctx.drawTextWithShadow(client.textRenderer, Text.literal(label), bx, by, color);
    }

    // ─── 5. Resource Bar (Bottom-Right, Horizontal) ──────────────────────

    private void renderResourceBar(DrawContext ctx, MinecraftClient client, int screenW, int screenH) {
        int maxAp = CombatState.getMaxAp();
        int maxSpeed = CombatState.getMaxSpeed();
        // The broadcast remaining-AP / move-points are the CURRENT turn-holder's
        // live values (there is no per-UUID remaining-resource field). On a
        // non-acting teammate's screen they'd show the active player's draining
        // pips as if they were their own, so display full bars when it isn't the
        // local player's turn — they'll spend from full when their turn comes.
        boolean myTurn = CombatState.isLocalPlayersTurn();
        int ap = myTurn ? CombatState.getApRemaining() : maxAp;
        int speed = myTurn ? CombatState.getMovePointsRemaining() : maxSpeed;

        // One pip per point, shrinking pip size as totals grow so the row always
        // fits. The old layout fixed each section at 3 shared slots, which meant
        // spending AP/SPD above the cap changed NOTHING on screen — the pips
        // only started draining once you were already below 3.
        int totalPips = Math.max(1, maxAp + maxSpeed);
        int pipSize = totalPips <= 8 ? 10 : totalPips <= 12 ? 8 : totalPips <= 18 ? 6 : 5;
        int pipGap = pipSize >= 8 ? 4 : 3; // between pips
        int sectionGap = 12; // between AP and SPD sections

        int apDisplay = maxAp;
        int spdDisplay = maxSpeed;

        // Labels include current/max numeric values alongside the pip visuals
        String apLabel = "AP " + ap + "/" + maxAp;
        String spdLabel = "SPD " + speed + "/" + maxSpeed;

        // Calculate widths
        int apLabelW = client.textRenderer.getWidth(apLabel) + 4;
        int apPipsW = apDisplay * (pipSize + pipGap) - pipGap;
        int spdLabelW = client.textRenderer.getWidth(spdLabel) + 4;
        int spdPipsW = spdDisplay * (pipSize + pipGap) - pipGap;
        int totalW = apLabelW + apPipsW + sectionGap + spdLabelW + spdPipsW;

        int rowY = screenH - 52;
        int startX = screenW - totalW - 10;

        // AP label
        int apLabelColor = maxAp >= 6 ? 0xFFFFEE88 : maxAp >= 4 ? 0xFFFFCC00 : 0xFFFFAA00;
        ctx.drawTextWithShadow(client.textRenderer, Text.literal(apLabel), startX, rowY, apLabelColor);
        int x = startX + apLabelW;

        // AP pips — one per point
        int pipY = rowY + (10 - pipSize) / 2;
        for (int i = 0; i < apDisplay; i++) {
            if (i >= ap) {
                ctx.fill(x, pipY, x + pipSize, pipY + pipSize, 0xFF444444);
                ctx.fill(x + 1, pipY + 1, x + pipSize - 1, pipY + pipSize - 1, 0xFF333333);
            } else {
                int outer, inner;
                if (i < 3) { outer = 0xFFFF8800; inner = 0xFFFFAA22; }
                else if (i < 5) { outer = 0xFFFFCC00; inner = 0xFFFFDD44; }
                else if (i < 7) { outer = 0xFFFFDD33; inner = 0xFFFFEE77; }
                else { outer = 0xFFFFEE88; inner = 0xFFFFFFC0; }
                ctx.fill(x, pipY, x + pipSize, pipY + pipSize, outer);
                ctx.fill(x + 1, pipY + 1, x + pipSize - 1, pipY + pipSize - 1, inner);
                if (pipSize >= 7) {
                    ctx.fill(x + 2, pipY + 1, x + pipSize - 2, pipY + 3,
                        (inner & 0x00FFFFFF) | 0x66000000);
                }
            }
            x += pipSize + pipGap;
        }

        x += sectionGap - pipGap; // gap between sections

        // SPD label
        int spdLabelColor = maxSpeed >= 7 ? 0xFFAAEEFF : maxSpeed >= 5 ? 0xFF66CCFF : 0xFF55AAFF;
        ctx.drawTextWithShadow(client.textRenderer, Text.literal(spdLabel), x, rowY, spdLabelColor);
        x += spdLabelW;

        // SPD pips — one per point
        for (int i = 0; i < spdDisplay; i++) {
            if (i >= speed) {
                ctx.fill(x, pipY, x + pipSize, pipY + pipSize, 0xFF444444);
                ctx.fill(x + 1, pipY + 1, x + pipSize - 1, pipY + pipSize - 1, 0xFF333333);
            } else {
                int outer, inner;
                if (i < 3) { outer = 0xFF3388CC; inner = 0xFF55AAEE; }
                else if (i < 5) { outer = 0xFF44AADD; inner = 0xFF66CCFF; }
                else if (i < 7) { outer = 0xFF55CCEE; inner = 0xFF88DDFF; }
                else if (i < 9) { outer = 0xFF88DDFF; inner = 0xFFAAEEFF; }
                else { outer = 0xFFAAEEFF; inner = 0xFFDDFFFF; }
                ctx.fill(x, pipY, x + pipSize, pipY + pipSize, outer);
                ctx.fill(x + 1, pipY + 1, x + pipSize - 1, pipY + pipSize - 1, inner);
                if (pipSize >= 7) {
                    ctx.fill(x + 2, pipY + 1, x + pipSize - 2, pipY + 3,
                        (inner & 0x00FFFFFF) | 0x66000000);
                }
            }
            x += pipSize + pipGap;
        }

    }

    // ─── Effect Parsing ──────────────────────────────────────────────────

    /** One active combat effect shown under the HP bar. */
    private record EffectIcon(String fullName, String level, int turns, boolean isDebuff) {}

    private static List<EffectIcon> parseEffects(String effects) {
        List<EffectIcon> icons = new ArrayList<>();
        if (effects == null || effects.isEmpty()) return icons;

        String[] parts = effects.split(" \\| ");
        for (String part : parts) {
            String name = part.trim();
            int turns = 0;

            // Extract turn count from parenthesized suffix: "Poison(2)" or "Poison II(3t)"
            int paren = name.indexOf('(');
            if (paren >= 0) {
                String turnStr = name.substring(paren + 1).replaceAll("[^0-9]", "");
                if (!turnStr.isEmpty()) turns = Integer.parseInt(turnStr);
                name = name.substring(0, paren).trim();
            }

            // Split off the Roman numeral level suffix so we can render it small.
            String level = "";
            java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("\\s+([IVX]+)$").matcher(name);
            if (m.find()) {
                level = m.group(1);
                name = name.substring(0, m.start()).trim();
            }

            boolean isDebuff = name.equalsIgnoreCase("Poison") || name.equalsIgnoreCase("Wither")
                || name.equalsIgnoreCase("Burning") || name.equalsIgnoreCase("Slowness")
                || name.equalsIgnoreCase("Weakness") || name.equalsIgnoreCase("Blindness")
                || name.equalsIgnoreCase("Darkness") || name.equalsIgnoreCase("Mining Fatigue")
                || name.equalsIgnoreCase("Levitation") || name.equalsIgnoreCase("Hunger")
                || name.equalsIgnoreCase("Bleeding") || name.equalsIgnoreCase("Soaked")
                || name.equalsIgnoreCase("Confusion");

            icons.add(new EffectIcon(name, level, turns, isDebuff));
        }
        return icons;
    }

    // ─── Stat Defaults ───────────────────────────────────────────────────

    private static int getDefaultStat(String typeId, String stat) {
        return switch (stat) {
            case "atk" -> switch (typeId) {
                case "minecraft:zombie", "minecraft:husk", "minecraft:drowned" -> 3;
                case "minecraft:skeleton", "minecraft:stray", "minecraft:pillager" -> 3;
                case "minecraft:spider" -> 3;
                case "minecraft:creeper" -> 5;
                case "minecraft:vindicator" -> 4;
                case "minecraft:witch" -> 3;
                case "minecraft:enderman" -> 5;
                case "minecraft:blaze" -> 4;
                case "minecraft:ghast" -> 5;
                case "minecraft:warden" -> 8;
                case "minecraft:ender_dragon" -> 10;
                default -> 2;
            };
            case "def" -> switch (typeId) {
                case "minecraft:vindicator", "minecraft:wither_skeleton" -> 1;
                case "minecraft:warden" -> 4;
                case "minecraft:shulker" -> 3;
                default -> 0;
            };
            case "spd" -> switch (typeId) {
                case "minecraft:spider", "minecraft:wolf", "minecraft:vindicator" -> 3;
                case "minecraft:phantom", "minecraft:ocelot", "minecraft:ender_dragon" -> 4;
                case "minecraft:enderman" -> 5;
                case "minecraft:skeleton", "minecraft:stray", "minecraft:pillager", "minecraft:creeper" -> 2;
                case "minecraft:ghast", "minecraft:shulker" -> 1;
                case "minecraft:warden" -> 3;
                default -> 2;
            };
            case "range" -> switch (typeId) {
                case "minecraft:skeleton", "minecraft:stray", "minecraft:drowned" -> 3;
                case "minecraft:pillager", "minecraft:piglin", "minecraft:blaze" -> 4;
                case "minecraft:ghast" -> 6;
                case "minecraft:shulker" -> 5;
                case "minecraft:witch" -> 3;
                default -> 1;
            };
            default -> 0;
        };
    }

    private static String getAIHint(String typeId) {
        return switch (typeId) {
            case "minecraft:skeleton", "minecraft:stray" -> "Kites at range, retreats if close";
            case "minecraft:creeper" -> "Sneaks close, fuses, then explodes";
            case "minecraft:spider" -> "Pounces over obstacles";
            case "minecraft:enderman" -> "Teleports across the arena";
            case "minecraft:endermite", "minecraft:breeze" -> "Blinks a short distance";
            case "minecraft:vindicator", "minecraft:piglin_brute" -> "Dashes in a straight line";
            case "minecraft:hoglin", "minecraft:ravager" -> "Charges in a straight line";
            case "minecraft:magma_cube", "minecraft:slime" -> "Bounces toward you, ignoring walls";
            case "minecraft:phantom" -> "Swoops in over obstacles";
            case "minecraft:ghast" -> "Attacks from long range";
            case "minecraft:blaze" -> "Ranged fire attacks";
            case "minecraft:witch" -> "Throws splash potions";
            case "minecraft:warden" -> "Slow, but hits very hard";
            case "minecraft:wither_skeleton" -> "Inflicts Wither on hit";
            default -> "";
        };
    }
}