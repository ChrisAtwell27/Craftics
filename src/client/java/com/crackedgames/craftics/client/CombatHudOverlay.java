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

    private static int turnBannerAge = 0;
    private static int lastTurnPhase = -1;

    @Override
    public void onHudRender(DrawContext ctx, RenderTickCounter tickCounter) {
        if (!CombatState.isInCombat()) return;

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

        int currentPhase = CombatState.isPlayerTurn() ? 1 : CombatState.isEnemyTurn() ? 2 : 0;
        if (currentPhase != lastTurnPhase) {
            lastTurnPhase = currentPhase;
            turnBannerAge = 0;
        }
        turnBannerAge++;

        renderPlayerStatusPanel(ctx, client, screenW);
        renderAllyRoster(ctx, client, screenW);
        renderTurnBanner(ctx, client, screenW);
        renderEnemyRoster(ctx, client, screenW);
        renderModePill(ctx, client, screenW, screenH);
        renderResourceBar(ctx, client, screenW, screenH);

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

        int barW = 120;
        int barH = 10;
        int panelPad = 6;
        int effectRowH = icons.isEmpty() ? 0 : 16;
        int panelW = barW + panelPad * 2;
        int panelH = panelPad + barH + (effectRowH > 0 ? 4 + effectRowH : 0) + panelPad;
        int panelX = 8;
        int panelY = 6;

        drawPanel(ctx, panelX, panelY, panelW, panelH);

        int barX = panelX + panelPad;
        int barY = panelY + panelPad;
        ctx.fill(barX, barY, barX + barW, barY + barH, 0xFF222222);
        int hpColor = hpPct > 0.5f ? 0xFF55FF55 : hpPct > 0.25f ? 0xFFFFFF55 : 0xFFFF5555;
        ctx.fill(barX, barY, barX + (int)(barW * hpPct), barY + barH, hpColor);
        String hpText = hp + "/" + maxHp;
        int textW = client.textRenderer.getWidth(hpText);
        ctx.drawTextWithShadow(client.textRenderer,
            Text.literal(hpText), barX + (barW - textW) / 2, barY + 1, 0xFFFFFFFF);

        if (!icons.isEmpty()) {
            int iconY = barY + barH + 4;
            int iconX = barX;
            for (EffectIcon icon : icons) {
                int iconW = 12;
                int bgColor = icon.isDebuff ? 0xCC882222 : 0xCC226622;
                ctx.fill(iconX, iconY, iconX + iconW, iconY + iconW, bgColor);
                ctx.drawTextWithShadow(client.textRenderer,
                    Text.literal(icon.abbrev), iconX + 1, iconY + 2, icon.isDebuff ? 0xFFFF8888 : 0xFF88FF88);
                if (icon.turns > 0) {
                    String turns = String.valueOf(icon.turns);
                    int tw = client.textRenderer.getWidth(turns);
                    ctx.drawTextWithShadow(client.textRenderer,
                        Text.literal("\u00a77" + turns), iconX + iconW - tw, iconY + iconW - 8, 0xFFAAAAAA);
                }
                iconX += iconW + 2;
            }
        }
    }

    private void renderPartyHpList(DrawContext ctx, MinecraftClient client,
                                    java.util.List<CombatState.PartyMemberHp> members) {
        int panelPad = 6;
        int barW = 90;
        int barH = 8;
        int nameW = 50;
        int rowH = barH + 4;
        int panelW = panelPad + nameW + 4 + barW + panelPad;
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

            ctx.drawTextWithShadow(client.textRenderer,
                Text.literal(displayName), panelX + panelPad, y, nameColor);

            int barX = panelX + panelPad + nameW + 4;
            ctx.fill(barX, y, barX + barW, y + barH, 0xFF222222);
            if (!member.dead()) {
                ctx.fill(barX, y, barX + (int)(barW * hpPct), y + barH, hpBarColor);
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
            int hpColor = ePct > 0.5f ? 0xFF55FF55 : ePct > 0.25f ? 0xFFFFFF55 : 0xFFFF5555;
            ctx.fill(barX, barY, barX + (int)(barW * ePct), barY + barH, hpColor);
            ctx.drawTextWithShadow(client.textRenderer,
                Text.literal("\u00a77" + eHp + "/" + eMaxHp),
                barX + barW + 2, barY - 2, 0xFFAAAAAA);

            y += entryH;
        }
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

        // After 30 ticks, collapse "YOUR TURN" to just "Turn N"
        boolean showFullBanner = !fadeBanner || turnBannerAge <= 30;
        int turnNum = CombatState.getTurnNumber();

        StringBuilder banner = new StringBuilder();
        if (showFullBanner) {
            banner.append(turnText).append(" \u2014 ");
        }
        banner.append("Turn ").append(turnNum);

        int streak = CombatState.getKillStreak();
        String streakBadge = "";
        if (streak >= 2) {
            streakBadge = "  " + streak + "x";
        }

        String fullText = banner.toString();
        int textW = client.textRenderer.getWidth(fullText);
        int badgeW = streakBadge.isEmpty() ? 0 : client.textRenderer.getWidth(streakBadge) + 6;
        int totalW = textW + badgeW;
        int pillW = totalW + 12;
        int pillH = 14;
        int pillX = screenW / 2 - pillW / 2;
        int pillY = 4;

        int alpha = 255;
        if (fadeBanner && turnBannerAge > 30 && turnBannerAge <= 45) {
            alpha = 255;
        }

        drawPill(ctx, pillX, pillY, pillW, pillH, pillColor);

        int textX = pillX + 6;
        int textY = pillY + 3;

        if (showFullBanner) {
            ctx.drawTextWithShadow(client.textRenderer, Text.literal(fullText), textX, textY, turnColor);
        } else {
            ctx.drawTextWithShadow(client.textRenderer, Text.literal(fullText), textX, textY, 0xFFCCCCCC);
        }

        if (!streakBadge.isEmpty()) {
            int streakColor = streak >= 5 ? 0xFFFF55FF : streak >= 3 ? 0xFFFFAA00 : 0xFFFFFF55;
            int badgeBg = streak >= 5 ? 0xCC442244 : streak >= 3 ? 0xCC443311 : 0xCC444411;
            int badgeX = textX + textW + 3;
            drawPill(ctx, badgeX, pillY + 2, badgeW, pillH - 4, badgeBg);
            ctx.drawTextWithShadow(client.textRenderer,
                Text.literal(streakBadge.trim()), badgeX + 3, textY, streakColor);
        }
    }

    private void renderEnemyRoster(DrawContext ctx, MinecraftClient client, int screenW) {
        Map<Integer, int[]> enemies = CombatState.getEnemyHpMap();
        Map<Integer, String> types = CombatState.getEnemyTypeMap();
        if (enemies.isEmpty()) return;

        int hoveredId = CombatState.getHoveredEnemyId();

        if (hoveredId != -1 && enemies.containsKey(hoveredId)) {
            renderInspectPanel(ctx, client, screenW, hoveredId, enemies.get(hoveredId),
                types.getOrDefault(hoveredId, "minecraft:zombie"));
            return;
        }

        boolean compact = true;
        try { compact = com.crackedgames.craftics.CrafticsMod.CONFIG.compactEnemyList(); } catch (Exception ignored) {}

        int headSize = 16;
        int barW = 50;
        int barH = 6;
        int padding = 3;
        int entryH = headSize + 2;

        int enemyCount = enemies.size();
        int showCount = (compact && enemyCount > 4) ? 3 : enemyCount;
        boolean collapsed = compact && enemyCount > 4;

        int panelContentW = headSize + padding + barW + 40;
        int headerH = 14;
        int panelContentH = headerH + showCount * entryH + (collapsed ? 14 : 0);
        int panelPad = 4;
        int panelW = panelContentW + panelPad * 2;
        int panelH = panelContentH + panelPad;
        int panelX = screenW - panelW - 6;
        int panelY = 4;

        drawPanel(ctx, panelX, panelY, panelW, panelH);

        ctx.drawTextWithShadow(client.textRenderer,
            Text.literal("\u00a77Enemies (" + enemyCount + ")"),
            panelX + panelPad, panelY + 3, 0xFFAAAAAA);

        int startX = panelX + panelPad;
        int y = panelY + headerH;

        int drawn = 0;
        for (Map.Entry<Integer, int[]> entry : enemies.entrySet()) {
            if (drawn >= showCount) break;

            int entityId = entry.getKey();
            int[] hpData = entry.getValue();
            int eHp = hpData[0];
            int eMaxHp = hpData[1];
            float ePct = eMaxHp > 0 ? (float) eHp / eMaxHp : 0;

            String typeIdFull = types.getOrDefault(entityId, "minecraft:zombie");
            String typeId = typeIdFull.contains(";") ? typeIdFull.substring(0, typeIdFull.indexOf(';')) : typeIdFull;

            Identifier headTex = MobHeadTextures.get(typeId);
            if (headTex != null) {
                MobHeadTextures.drawMobHead(ctx, headTex, startX, y, headSize);
            } else {
                int squareColor = MobHeadTextures.getMobColor(typeId);
                ctx.fill(startX, y, startX + headSize, y + headSize, squareColor);
                ctx.fill(startX + 1, y + 1, startX + headSize - 1, y + headSize - 1,
                    (squareColor & 0x00FFFFFF) | 0xCC000000);
                String initial = MobHeadTextures.getDisplayInitial(typeId);
                ctx.drawCenteredTextWithShadow(client.textRenderer,
                    Text.literal(initial), startX + headSize / 2, y + 4, 0xFFFFFFFF);
            }

            int barX = startX + headSize + padding;
            int barY = y + (headSize - barH) / 2;
            ctx.fill(barX - 1, barY - 1, barX + barW + 1, barY + barH + 1, 0x88000000);
            int eColor = ePct > 0.5f ? 0xFF55FF55 : ePct > 0.25f ? 0xFFFFFF55 : 0xFFFF5555;
            ctx.fill(barX, barY, barX + (int)(barW * ePct), barY + barH, eColor);
            ctx.drawTextWithShadow(client.textRenderer,
                Text.literal("\u00a77" + eHp + "/" + eMaxHp),
                barX + barW + 3, barY - 1, 0xFFAAAAAA);

            y += entryH;
            drawn++;
        }

        if (collapsed) {
            int remaining = enemyCount - showCount;
            int miniX = startX;
            int miniSize = 8;
            int miniDrawn = 0;

            List<String> remainingTypes = new ArrayList<>();
            int skipped = 0;
            for (Map.Entry<Integer, int[]> entry : enemies.entrySet()) {
                if (skipped < showCount) { skipped++; continue; }
                String tFull = types.getOrDefault(entry.getKey(), "minecraft:zombie");
                String tId = tFull.contains(";") ? tFull.substring(0, tFull.indexOf(';')) : tFull;
                remainingTypes.add(tId);
            }

            for (String rt : remainingTypes) {
                if (miniDrawn >= 6) break; // max 6 mini heads
                Identifier miniTex = MobHeadTextures.get(rt);
                if (miniTex != null) {
                    MobHeadTextures.drawMobHead(ctx, miniTex, miniX, y + 3, miniSize);
                } else {
                    ctx.fill(miniX, y + 3, miniX + miniSize, y + 3 + miniSize, MobHeadTextures.getMobColor(rt));
                }
                miniX += miniSize + 1;
                miniDrawn++;
            }

            ctx.drawTextWithShadow(client.textRenderer,
                Text.literal("\u00a78+" + remaining + " more"),
                miniX + 2, y + 3, 0xFF888888);
        }
    }

    // ─── Inspect Panel (hover detail) ────────────────────────────────────

    private void renderInspectPanel(DrawContext ctx, MinecraftClient client, int screenW,
                                     int entityId, int[] hpData, String typeIdRaw) {
        int eHp = hpData[0];
        int eMaxHp = hpData[1];
        float ePct = eMaxHp > 0 ? (float) eHp / eMaxHp : 0;

        String[] parts = typeIdRaw.split(";");
        String typeId = parts[0];
        List<String> enemyEffects = new ArrayList<>();
        String bossName = null;
        int bossAtk = -1, bossDef = -1, bossSpd = -1, bossRange = -1;
        for (int i = 1; i < parts.length; i++) {
            if (parts[i].startsWith("boss=")) bossName = parts[i].substring(5);
            else if (parts[i].startsWith("atk=")) bossAtk = Integer.parseInt(parts[i].substring(4));
            else if (parts[i].startsWith("def=")) bossDef = Integer.parseInt(parts[i].substring(4));
            else if (parts[i].startsWith("spd=")) bossSpd = Integer.parseInt(parts[i].substring(4));
            else if (parts[i].startsWith("range=")) bossRange = Integer.parseInt(parts[i].substring(6));
            else enemyEffects.add(parts[i]);
        }

        String displayName;
        if (bossName != null) {
            displayName = bossName;
        } else {
            String rawId = typeId;
            int colon = rawId.indexOf(':');
            if (colon >= 0) rawId = rawId.substring(colon + 1);
            displayName = rawId.substring(0, 1).toUpperCase() + rawId.substring(1).replace('_', ' ');
        }

        int panelW = bossName != null ? 140 : 120;
        int panelH = 100 + enemyEffects.size() * 10;
        int panelX = screenW - panelW - 8;
        int panelY = 4;

        // Use standard panel with boss tint
        int bgColor = bossName != null ? 0xBB2A0A0A : PANEL_BG;
        ctx.fill(panelX - 1, panelY - 1, panelX + panelW + 1, panelY + panelH + 1, PANEL_BORDER);
        ctx.fill(panelX, panelY, panelX + panelW, panelY + panelH, bgColor);
        // Name header bar
        int headerColor = bossName != null ? 0xBB8B0000 : 0xBB222244;
        ctx.fill(panelX, panelY, panelX + panelW, panelY + 14, headerColor);

        int nameColor = bossName != null ? 0xFFFFAA00 : 0xFFFFFFFF;
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
        ctx.fill(panelX + 2, y, panelX + 2 + barW, y + barH, 0xFF333333);
        int hpColor = ePct > 0.5f ? 0xFF55FF55 : ePct > 0.25f ? 0xFFFFFF55 : 0xFFFF5555;
        ctx.fill(panelX + 2, y, panelX + 2 + (int)(barW * ePct), y + barH, hpColor);
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

        String behavior = getAIHint(typeId);
        if (behavior != null) {
            ctx.drawTextWithShadow(client.textRenderer,
                Text.literal("\u00a78" + behavior), panelX + 4, y, 0xFF888888);
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
            default -> { modeText = "MOVE"; modeColor = 0xFF55FF55; pillBg = 0xBB113311; }
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

    // ─── 5. Resource Bar (Bottom-Right, Horizontal) ──────────────────────

    private void renderResourceBar(DrawContext ctx, MinecraftClient client, int screenW, int screenH) {
        int ap = CombatState.getApRemaining();
        int maxAp = CombatState.getMaxAp();
        int speed = CombatState.getMovePointsRemaining();
        int maxSpeed = CombatState.getMaxSpeed();

        int pipSize = 10;
        int pipGap = 4; // between pips
        int sectionGap = 12; // between AP and SPD sections

        // Calculate widths
        int apLabelW = client.textRenderer.getWidth("AP") + 4;
        int apPipsW = maxAp * (pipSize + pipGap) - pipGap;
        int spdLabelW = client.textRenderer.getWidth("SPD") + 4;
        int spdPipsW = maxSpeed * (pipSize + pipGap) - pipGap;
        int totalW = apLabelW + apPipsW + sectionGap + spdLabelW + spdPipsW;

        int rowY = screenH - 52;
        int startX = screenW - totalW - 10;

        // AP label
        int apLabelColor = maxAp >= 6 ? 0xFFFFEE88 : maxAp >= 4 ? 0xFFFFCC00 : 0xFFFFAA00;
        ctx.drawTextWithShadow(client.textRenderer, Text.literal("AP"), startX, rowY, apLabelColor);
        int x = startX + apLabelW;

        // AP pips
        for (int i = 0; i < maxAp; i++) {
            if (i >= ap) {
                ctx.fill(x, rowY, x + pipSize, rowY + pipSize, 0xFF444444);
                ctx.fill(x + 1, rowY + 1, x + pipSize - 1, rowY + pipSize - 1, 0xFF333333);
            } else {
                int outer, inner;
                if (i < 3) { outer = 0xFFFF8800; inner = 0xFFFFAA22; }
                else if (i < 5) { outer = 0xFFFFCC00; inner = 0xFFFFDD44; }
                else if (i < 7) { outer = 0xFFFFDD33; inner = 0xFFFFEE77; }
                else { outer = 0xFFFFEE88; inner = 0xFFFFFFC0; }
                ctx.fill(x, rowY, x + pipSize, rowY + pipSize, outer);
                ctx.fill(x + 1, rowY + 1, x + pipSize - 1, rowY + pipSize - 1, inner);
                ctx.fill(x + 2, rowY + 1, x + pipSize - 2, rowY + 3,
                    (inner & 0x00FFFFFF) | 0x66000000);
            }
            x += pipSize + pipGap;
        }

        x += sectionGap - pipGap; // gap between sections

        // SPD label
        int spdLabelColor = maxSpeed >= 7 ? 0xFFAAEEFF : maxSpeed >= 5 ? 0xFF66CCFF : 0xFF55AAFF;
        ctx.drawTextWithShadow(client.textRenderer, Text.literal("SPD"), x, rowY, spdLabelColor);
        x += spdLabelW;

        // SPD pips
        for (int i = 0; i < maxSpeed; i++) {
            if (i >= speed) {
                ctx.fill(x, rowY, x + pipSize, rowY + pipSize, 0xFF444444);
                ctx.fill(x + 1, rowY + 1, x + pipSize - 1, rowY + pipSize - 1, 0xFF333333);
            } else {
                int outer, inner;
                if (i < 3) { outer = 0xFF3388CC; inner = 0xFF55AAEE; }
                else if (i < 5) { outer = 0xFF44AADD; inner = 0xFF66CCFF; }
                else if (i < 7) { outer = 0xFF55CCEE; inner = 0xFF88DDFF; }
                else if (i < 9) { outer = 0xFF88DDFF; inner = 0xFFAAEEFF; }
                else { outer = 0xFFAAEEFF; inner = 0xFFDDFFFF; }
                ctx.fill(x, rowY, x + pipSize, rowY + pipSize, outer);
                ctx.fill(x + 1, rowY + 1, x + pipSize - 1, rowY + pipSize - 1, inner);
                ctx.fill(x + 2, rowY + 1, x + pipSize - 2, rowY + 3,
                    (inner & 0x00FFFFFF) | 0x66000000);
            }
            x += pipSize + pipGap;
        }
    }

    // ─── Effect Parsing ──────────────────────────────────────────────────

    private record EffectIcon(String abbrev, int turns, boolean isDebuff) {}

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

            // Remove Roman numerals
            name = name.replaceAll("\\s+[IVX]+$", "").trim();

            boolean isDebuff = name.equalsIgnoreCase("Poison") || name.equalsIgnoreCase("Wither")
                || name.equalsIgnoreCase("Burning") || name.equalsIgnoreCase("Slowness")
                || name.equalsIgnoreCase("Weakness") || name.equalsIgnoreCase("Blindness")
                || name.equalsIgnoreCase("Darkness") || name.equalsIgnoreCase("Mining Fatigue")
                || name.equalsIgnoreCase("Levitation") || name.equalsIgnoreCase("Hunger");

            String abbrev = getEffectAbbrev(name);
            icons.add(new EffectIcon(abbrev, turns, isDebuff));
        }
        return icons;
    }

    private static String getEffectAbbrev(String name) {
        return switch (name.toLowerCase()) {
            case "speed" -> "Spd";
            case "poison" -> "Psn";
            case "strength" -> "Str";
            case "resistance" -> "Res";
            case "regeneration" -> "Rgn";
            case "fire resistance" -> "FR";
            case "invisibility" -> "Inv";
            case "wither" -> "Wth";
            case "burning" -> "Brn";
            case "slowness" -> "Slw";
            case "weakness" -> "Wkn";
            case "blindness" -> "Bln";
            case "absorption" -> "Abs";
            case "luck" -> "Lck";
            case "slow falling" -> "SF";
            case "haste" -> "Hst";
            case "water breathing" -> "WB";
            case "mining fatigue" -> "MF";
            case "levitation" -> "Lev";
            case "darkness" -> "Drk";
            case "hunger" -> "Hgr";
            case "jump boost" -> "Jmp";
            case "night vision" -> "NV";
            default -> name.length() >= 3 ? name.substring(0, 3) : name;
        };
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
            case "minecraft:enderman" -> "Teleports behind you";
            case "minecraft:phantom" -> "Swoops in straight lines";
            case "minecraft:witch" -> "Throws random potions";
            case "minecraft:ghast" -> "Long range, flees melee";
            case "minecraft:shulker" -> "Stationary turret";
            case "minecraft:vindicator" -> "Rushes with axe, fast";
            case "minecraft:pillager" -> "Crossbow, keeps distance";
            case "minecraft:warden" -> "Phase shift at 50% HP";
            case "minecraft:ender_dragon" -> "Swoops then breath attack";
            case "minecraft:zombie", "minecraft:husk" -> "Charges straight at you";
            case "minecraft:drowned" -> "Trident throw + melee";
            case "minecraft:blaze" -> "Fireball at medium range";
            case "minecraft:bee" -> "Swarm: all agro if one hit";
            case "minecraft:wolf" -> "Hunts prey, agro if hit";
            case "minecraft:goat" -> "Rams with knockback if hit";
            default -> null;
        };
    }
}
