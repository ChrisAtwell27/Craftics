package com.crackedgames.craftics.client;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;
/**
 * Renders the combat HUD overlay: turn indicator, AP/Speed pips, HP bar,
 * mode indicator, and enemy health bars with mob head icons.
 */
public class CombatHudOverlay implements HudRenderCallback {

    // Maps entity type IDs to their 16x16 mob head textures.
    private static final java.util.Map<String, net.minecraft.util.Identifier> MOB_HEAD_TEXTURES;
    static {
        java.util.Map<String, net.minecraft.util.Identifier> m = new java.util.HashMap<>();
        m.put("minecraft:zombie", net.minecraft.util.Identifier.of("craftics", "textures/mob_heads/zombie.png"));
        m.put("minecraft:husk", net.minecraft.util.Identifier.of("craftics", "textures/mob_heads/husk.png"));
        m.put("minecraft:drowned", net.minecraft.util.Identifier.of("craftics", "textures/mob_heads/drowned.png"));
        m.put("minecraft:zombie_villager", net.minecraft.util.Identifier.of("craftics", "textures/mob_heads/zombie_villager.png"));
        m.put("minecraft:skeleton", net.minecraft.util.Identifier.of("craftics", "textures/mob_heads/skeleton.png"));
        m.put("minecraft:stray", net.minecraft.util.Identifier.of("craftics", "textures/mob_heads/stray.png"));
        m.put("minecraft:wither_skeleton", net.minecraft.util.Identifier.of("craftics", "textures/mob_heads/wither_skeleton.png"));
        m.put("minecraft:bogged", net.minecraft.util.Identifier.of("craftics", "textures/mob_heads/bogged.png"));
        m.put("minecraft:spider", net.minecraft.util.Identifier.of("craftics", "textures/mob_heads/spider.png"));
        m.put("minecraft:cave_spider", net.minecraft.util.Identifier.of("craftics", "textures/mob_heads/cave_spider.png"));
        m.put("minecraft:endermite", net.minecraft.util.Identifier.of("craftics", "textures/mob_heads/endermite.png"));
        m.put("minecraft:silverfish", net.minecraft.util.Identifier.of("craftics", "textures/mob_heads/silverfish.png"));
        m.put("minecraft:bee", net.minecraft.util.Identifier.of("craftics", "textures/mob_heads/bee.png"));
        m.put("minecraft:creeper", net.minecraft.util.Identifier.of("craftics", "textures/mob_heads/creeper.png"));
        m.put("minecraft:blaze", net.minecraft.util.Identifier.of("craftics", "textures/mob_heads/blaze.png"));
        m.put("minecraft:ghast", net.minecraft.util.Identifier.of("craftics", "textures/mob_heads/ghast.png"));
        m.put("minecraft:magma_cube", net.minecraft.util.Identifier.of("craftics", "textures/mob_heads/magma_cube.png"));
        m.put("minecraft:slime", net.minecraft.util.Identifier.of("craftics", "textures/mob_heads/slime.png"));
        m.put("minecraft:breeze", net.minecraft.util.Identifier.of("craftics", "textures/mob_heads/breeze.png"));
        m.put("minecraft:enderman", net.minecraft.util.Identifier.of("craftics", "textures/mob_heads/enderman.png"));
        m.put("minecraft:ender_dragon", net.minecraft.util.Identifier.of("craftics", "textures/mob_heads/ender_dragon.png"));
        m.put("minecraft:shulker", net.minecraft.util.Identifier.of("craftics", "textures/mob_heads/shulker.png"));
        m.put("minecraft:witch", net.minecraft.util.Identifier.of("craftics", "textures/mob_heads/witch.png"));
        m.put("minecraft:pillager", net.minecraft.util.Identifier.of("craftics", "textures/mob_heads/pillager.png"));
        m.put("minecraft:vindicator", net.minecraft.util.Identifier.of("craftics", "textures/mob_heads/vindicator.png"));
        m.put("minecraft:evoker", net.minecraft.util.Identifier.of("craftics", "textures/mob_heads/evoker.png"));
        m.put("minecraft:vex", net.minecraft.util.Identifier.of("craftics", "textures/mob_heads/vex.png"));
        m.put("minecraft:illusioner", net.minecraft.util.Identifier.of("craftics", "textures/mob_heads/illusioner.png"));
        m.put("minecraft:ravager", net.minecraft.util.Identifier.of("craftics", "textures/mob_heads/ravager.png"));
        m.put("minecraft:piglin", net.minecraft.util.Identifier.of("craftics", "textures/mob_heads/piglin.png"));
        m.put("minecraft:piglin_brute", net.minecraft.util.Identifier.of("craftics", "textures/mob_heads/piglin_brute.png"));
        m.put("minecraft:zombified_piglin", net.minecraft.util.Identifier.of("craftics", "textures/mob_heads/zombified_piglin.png"));
        m.put("minecraft:hoglin", net.minecraft.util.Identifier.of("craftics", "textures/mob_heads/hoglin.png"));
        m.put("minecraft:zoglin", net.minecraft.util.Identifier.of("craftics", "textures/mob_heads/zoglin.png"));
        m.put("minecraft:strider", net.minecraft.util.Identifier.of("craftics", "textures/mob_heads/strider.png"));
        m.put("minecraft:warden", net.minecraft.util.Identifier.of("craftics", "textures/mob_heads/warden.png"));
        m.put("minecraft:phantom", net.minecraft.util.Identifier.of("craftics", "textures/mob_heads/phantom.png"));
        m.put("minecraft:guardian", net.minecraft.util.Identifier.of("craftics", "textures/mob_heads/guardian.png"));
        m.put("minecraft:elder_guardian", net.minecraft.util.Identifier.of("craftics", "textures/mob_heads/elder_guardian.png"));
        m.put("minecraft:wither", net.minecraft.util.Identifier.of("craftics", "textures/mob_heads/wither.png"));
        m.put("minecraft:wolf", net.minecraft.util.Identifier.of("craftics", "textures/mob_heads/wolf.png"));
        m.put("minecraft:ocelot", net.minecraft.util.Identifier.of("craftics", "textures/mob_heads/ocelot.png"));
        m.put("minecraft:cat", net.minecraft.util.Identifier.of("craftics", "textures/mob_heads/cat.png"));
        m.put("minecraft:goat", net.minecraft.util.Identifier.of("craftics", "textures/mob_heads/goat.png"));
        m.put("minecraft:polar_bear", net.minecraft.util.Identifier.of("craftics", "textures/mob_heads/polar_bear.png"));
        m.put("minecraft:panda", net.minecraft.util.Identifier.of("craftics", "textures/mob_heads/panda.png"));
        m.put("minecraft:fox", net.minecraft.util.Identifier.of("craftics", "textures/mob_heads/fox.png"));
        m.put("minecraft:bat", net.minecraft.util.Identifier.of("craftics", "textures/mob_heads/bat.png"));
        m.put("minecraft:cow", net.minecraft.util.Identifier.of("craftics", "textures/mob_heads/cow.png"));
        m.put("minecraft:pig", net.minecraft.util.Identifier.of("craftics", "textures/mob_heads/pig.png"));
        m.put("minecraft:sheep", net.minecraft.util.Identifier.of("craftics", "textures/mob_heads/sheep.png"));
        m.put("minecraft:chicken", net.minecraft.util.Identifier.of("craftics", "textures/mob_heads/chicken.png"));
        m.put("minecraft:rabbit", net.minecraft.util.Identifier.of("craftics", "textures/mob_heads/rabbit.png"));
        m.put("minecraft:mooshroom", net.minecraft.util.Identifier.of("craftics", "textures/mob_heads/mooshroom.png"));
        m.put("minecraft:camel", net.minecraft.util.Identifier.of("craftics", "textures/mob_heads/camel.png"));
        m.put("minecraft:sniffer", net.minecraft.util.Identifier.of("craftics", "textures/mob_heads/sniffer.png"));
        m.put("minecraft:iron_golem", net.minecraft.util.Identifier.of("craftics", "textures/mob_heads/iron_golem.png"));
        m.put("minecraft:snow_golem", net.minecraft.util.Identifier.of("craftics", "textures/mob_heads/snow_golem.png"));
        m.put("minecraft:villager", net.minecraft.util.Identifier.of("craftics", "textures/mob_heads/villager.png"));
        m.put("minecraft:wandering_trader", net.minecraft.util.Identifier.of("craftics", "textures/mob_heads/wandering_trader.png"));
        m.put("minecraft:dolphin", net.minecraft.util.Identifier.of("craftics", "textures/mob_heads/dolphin.png"));
        m.put("minecraft:squid", net.minecraft.util.Identifier.of("craftics", "textures/mob_heads/squid.png"));
        m.put("minecraft:glow_squid", net.minecraft.util.Identifier.of("craftics", "textures/mob_heads/glow_squid.png"));
        m.put("minecraft:turtle", net.minecraft.util.Identifier.of("craftics", "textures/mob_heads/turtle.png"));
        m.put("minecraft:cod", net.minecraft.util.Identifier.of("craftics", "textures/mob_heads/cod.png"));
        m.put("minecraft:salmon", net.minecraft.util.Identifier.of("craftics", "textures/mob_heads/salmon.png"));
        m.put("minecraft:pufferfish", net.minecraft.util.Identifier.of("craftics", "textures/mob_heads/pufferfish.png"));
        m.put("minecraft:axolotl", net.minecraft.util.Identifier.of("craftics", "textures/mob_heads/axolotl.png"));
        m.put("minecraft:frog", net.minecraft.util.Identifier.of("craftics", "textures/mob_heads/frog.png"));
        m.put("minecraft:tadpole", net.minecraft.util.Identifier.of("craftics", "textures/mob_heads/tadpole.png"));
        m.put("minecraft:allay", net.minecraft.util.Identifier.of("craftics", "textures/mob_heads/allay.png"));
        m.put("minecraft:horse", net.minecraft.util.Identifier.of("craftics", "textures/mob_heads/horse.png"));
        m.put("minecraft:donkey", net.minecraft.util.Identifier.of("craftics", "textures/mob_heads/donkey.png"));
        m.put("minecraft:mule", net.minecraft.util.Identifier.of("craftics", "textures/mob_heads/mule.png"));
        m.put("minecraft:skeleton_horse", net.minecraft.util.Identifier.of("craftics", "textures/mob_heads/skeleton_horse.png"));
        m.put("minecraft:zombie_horse", net.minecraft.util.Identifier.of("craftics", "textures/mob_heads/zombie_horse.png"));
        m.put("minecraft:llama", net.minecraft.util.Identifier.of("craftics", "textures/mob_heads/llama.png"));
        m.put("minecraft:parrot", net.minecraft.util.Identifier.of("craftics", "textures/mob_heads/parrot.png"));
        MOB_HEAD_TEXTURES = java.util.Collections.unmodifiableMap(m);
    }

    @Override
    public void onHudRender(DrawContext ctx, RenderTickCounter tickCounter) {
        if (!CombatState.isInCombat()) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        // Larger UI accessibility option
        boolean largeUI = false;
        try { largeUI = com.crackedgames.craftics.CrafticsMod.CONFIG.largerUI(); } catch (Exception ignored) {}
        float uiScale = largeUI ? 1.5f : 1.0f;
        if (largeUI) {
            ctx.getMatrices().push();
            ctx.getMatrices().scale(uiScale, uiScale, 1.0f);
        }

        int screenW = (int)(client.getWindow().getScaledWidth() / uiScale);
        int screenH = (int)(client.getWindow().getScaledHeight() / uiScale);

        // --- Turn indicator (top center) ---
        String turnText;
        int turnColor;
        if (CombatState.isPlayerTurn()) {
            turnText = "YOUR TURN";
            turnColor = 0xFF55FF55; // green
        } else if (CombatState.isEnemyTurn()) {
            turnText = "ENEMY TURN";
            turnColor = 0xFFFF5555; // red
        } else {
            turnText = "...";
            turnColor = 0xFFAAAAAA;
        }
        ctx.drawCenteredTextWithShadow(client.textRenderer,
            Text.literal(turnText), screenW / 2, 8, turnColor);
        ctx.drawCenteredTextWithShadow(client.textRenderer,
            Text.literal("Turn " + CombatState.getTurnNumber()), screenW / 2, 20, 0xFFCCCCCC);

        // --- HP bar (top left) ---
        int hpBarX = 10;
        int hpBarY = 8;
        int hpBarW = 100;
        int hpBarH = 8;
        int hp = CombatState.getPlayerHp();
        int maxHp = CombatState.getPlayerMaxHp();
        float hpPct = maxHp > 0 ? (float) hp / maxHp : 0;

        // Background
        ctx.fill(hpBarX - 1, hpBarY - 1, hpBarX + hpBarW + 1, hpBarY + hpBarH + 1, 0xFF222222);
        // HP fill (green -> yellow -> red)
        int hpColor = hpPct > 0.5f ? 0xFF55FF55 : hpPct > 0.25f ? 0xFFFFFF55 : 0xFFFF5555;
        ctx.fill(hpBarX, hpBarY, hpBarX + (int)(hpBarW * hpPct), hpBarY + hpBarH, hpColor);
        // Text
        ctx.drawTextWithShadow(client.textRenderer,
            Text.literal("HP: " + hp + "/" + maxHp), hpBarX, hpBarY + hpBarH + 2, 0xFFFFFFFF);

        // --- Active effects below HP bar ---
        String effects = CombatState.getPlayerEffects();
        if (effects != null && !effects.isEmpty()) {
            int effectY = hpBarY + hpBarH + 14;
            String[] effectParts = effects.split(" \\| ");
            for (String eff : effectParts) {
                boolean isDebuff = eff.contains("Poison") || eff.contains("Wither") || eff.contains("Burning")
                    || eff.contains("Slowness") || eff.contains("Weakness");
                int effColor = isDebuff ? 0xFFFF6666 : 0xFF66FF66;
                String icon = isDebuff ? "\u2620 " : "\u2728 ";
                ctx.drawTextWithShadow(client.textRenderer,
                    Text.literal(icon + eff), hpBarX, effectY, effColor);
                effectY += 10;
            }
        }

        // --- AP pips (bottom right, above hotbar) — Terraria-style color tiers ---
        int pipY = screenH - 58;
        int ap = CombatState.getApRemaining();
        int maxAp = CombatState.getMaxAp();
        int apPipX = screenW - 10 - Math.max(maxAp, 3) * 14;
        int apLabelColor = maxAp >= 6 ? 0xFFFFEE88 : maxAp >= 4 ? 0xFFFFCC00 : 0xFFFFAA00;
        ctx.drawTextWithShadow(client.textRenderer, Text.literal("AP"), apPipX, pipY - 10, apLabelColor);
        for (int i = 0; i < maxAp; i++) {
            if (i >= ap) {
                // Empty pip
                ctx.fill(apPipX + i * 14, pipY, apPipX + i * 14 + 10, pipY + 10, 0xFF444444);
                ctx.fill(apPipX + i * 14 + 1, pipY + 1, apPipX + i * 14 + 9, pipY + 9, 0xFF333333);
            } else {
                // Tiered colors: base orange → gold → bright yellow → white
                int outer, inner;
                if (i < 3) {
                    // Tier 1 (base): orange
                    outer = 0xFFFF8800; inner = 0xFFFFAA22;
                } else if (i < 5) {
                    // Tier 2 (bonus): gold
                    outer = 0xFFFFCC00; inner = 0xFFFFDD44;
                } else if (i < 7) {
                    // Tier 3 (high): bright amber
                    outer = 0xFFFFDD33; inner = 0xFFFFEE77;
                } else {
                    // Tier 4 (legendary): white-gold
                    outer = 0xFFFFEE88; inner = 0xFFFFFFC0;
                }
                ctx.fill(apPipX + i * 14, pipY, apPipX + i * 14 + 10, pipY + 10, outer);
                ctx.fill(apPipX + i * 14 + 1, pipY + 1, apPipX + i * 14 + 9, pipY + 9, inner);
                // Shine highlight on top edge
                ctx.fill(apPipX + i * 14 + 2, pipY + 1, apPipX + i * 14 + 8, pipY + 3,
                    (inner & 0x00FFFFFF) | 0x66000000);
            }
        }

        // --- Speed pips (bottom right, below AP) — Terraria-style color tiers ---
        int speed = CombatState.getMovePointsRemaining();
        int maxSpeed = CombatState.getMaxSpeed();
        int spdPipY = pipY + 14;
        int spdLabelColor = maxSpeed >= 7 ? 0xFFAAEEFF : maxSpeed >= 5 ? 0xFF66CCFF : 0xFF55AAFF;
        ctx.drawTextWithShadow(client.textRenderer, Text.literal("SPD"), apPipX - 5, spdPipY - 10, spdLabelColor);
        for (int i = 0; i < maxSpeed; i++) {
            if (i >= speed) {
                ctx.fill(apPipX + i * 14, spdPipY, apPipX + i * 14 + 10, spdPipY + 10, 0xFF444444);
                ctx.fill(apPipX + i * 14 + 1, spdPipY + 1, apPipX + i * 14 + 9, spdPipY + 9, 0xFF333333);
            } else {
                int outer, inner;
                if (i < 3) {
                    // Tier 1: blue
                    outer = 0xFF3388CC; inner = 0xFF55AAEE;
                } else if (i < 5) {
                    // Tier 2: cyan
                    outer = 0xFF44AADD; inner = 0xFF66CCFF;
                } else if (i < 7) {
                    // Tier 3: light cyan
                    outer = 0xFF55CCEE; inner = 0xFF88DDFF;
                } else if (i < 9) {
                    // Tier 4: ice white
                    outer = 0xFF88DDFF; inner = 0xFFAAEEFF;
                } else {
                    // Tier 5: brilliant white (mount bonus territory)
                    outer = 0xFFAAEEFF; inner = 0xFFDDFFFF;
                }
                ctx.fill(apPipX + i * 14, spdPipY, apPipX + i * 14 + 10, spdPipY + 10, outer);
                ctx.fill(apPipX + i * 14 + 1, spdPipY + 1, apPipX + i * 14 + 9, spdPipY + 9, inner);
                // Shine highlight
                ctx.fill(apPipX + i * 14 + 2, spdPipY + 1, apPipX + i * 14 + 8, spdPipY + 3,
                    (inner & 0x00FFFFFF) | 0x66000000);
            }
        }

        // --- Emerald counter (bottom right, below speed) ---
        int emeralds = CombatState.getEmeralds();
        ctx.drawTextWithShadow(client.textRenderer,
            Text.literal("\u00a7a\u25c6 " + emeralds + " Emeralds"),
            apPipX - 10, spdPipY + 14, 0xFF55FF55);

        // --- Mode indicator (bottom center) ---
        CombatInputHandler.ActionMode mode = CombatInputHandler.getActionMode(client);
        String modeText = switch (mode) {
            case MOVE -> "\u00a7aMOVE";
            case MELEE_ATTACK -> "\u00a7cATTACK";
            case RANGED_ATTACK -> "\u00a76RANGED";
            case USE_ITEM -> "\u00a7dUSE ITEM";
        };
        ctx.drawCenteredTextWithShadow(client.textRenderer,
            Text.literal(modeText), screenW / 2, screenH - 58, 0xFFFFFFFF);

        // (Active effects now rendered below HP bar instead)

        // --- Kill streak indicator (below turn indicator) ---
        int streak = CombatState.getKillStreak();
        if (streak >= 2) {
            int streakColor = streak >= 5 ? 0xFFFF55FF : streak >= 3 ? 0xFFFFAA00 : 0xFFFFFF55;
            ctx.drawCenteredTextWithShadow(client.textRenderer,
                Text.literal("\u00a7l" + streak + "x Kill Streak!"),
                screenW / 2, 32, streakColor);
        }

        // --- Enemy HP bars with mob head icons (top right) ---
        renderEnemyBars(ctx, client, screenW);

        // --- Combat visual effects (damage numbers, flashes) ---
        CombatVisualEffects.render(ctx, client, screenW, screenH);

        // --- Combat log (bottom left, fading messages) ---
        CombatLog.render(ctx, client.textRenderer, screenW, screenH);

        if (largeUI) {
            ctx.getMatrices().pop();
        }
    }

    /**
     * Render enemy health bars OR detailed inspect panel in the top-right corner.
     * When hovering over an enemy, shows detailed stats instead of the full list.
     */
    private void renderEnemyBars(DrawContext ctx, MinecraftClient client, int screenW) {
        java.util.Map<Integer, int[]> enemies = CombatState.getEnemyHpMap();
        java.util.Map<Integer, String> types = CombatState.getEnemyTypeMap();
        if (enemies.isEmpty()) return;

        int hoveredId = CombatState.getHoveredEnemyId();

        // If hovering over an enemy, show detailed inspect panel
        if (hoveredId != -1 && enemies.containsKey(hoveredId)) {
            renderInspectPanel(ctx, client, screenW, hoveredId, enemies.get(hoveredId), types.getOrDefault(hoveredId, "minecraft:zombie"));
            return;
        }

        // Normal: show all enemy health bars
        int headSize = 16;
        int barW = 50;
        int barH = 6;
        int padding = 3;
        int entryH = headSize + 2;
        int startX = screenW - headSize - barW - 40;
        int startY = 6;

        int y = startY;
        for (java.util.Map.Entry<Integer, int[]> entry : enemies.entrySet()) {
            int entityId = entry.getKey();
            int[] hpData = entry.getValue();
            int eHp = hpData[0];
            int eMaxHp = hpData[1];
            float ePct = eMaxHp > 0 ? (float) eHp / eMaxHp : 0;

            String typeIdFull = types.getOrDefault(entityId, "minecraft:zombie");
            String typeId = typeIdFull.contains(";") ? typeIdFull.substring(0, typeIdFull.indexOf(';')) : typeIdFull;
                net.minecraft.util.Identifier headTex = MOB_HEAD_TEXTURES.get(typeId);
                if (headTex != null) {
                    drawMobHead(ctx, headTex, startX, y, headSize);
                } else {
                    int squareColor = getMobColor(typeId);
                    ctx.fill(startX, y, startX + headSize, y + headSize, squareColor);
                    ctx.fill(startX + 1, y + 1, startX + headSize - 1, y + headSize - 1,
                        (squareColor & 0x00FFFFFF) | 0xCC000000);
                    String initial = getDisplayInitial(typeId);
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
        }
    }

    /**
     * Detailed inspect panel for a single hovered enemy.
     * Shows name, HP bar, ATK, DEF, SPD, range, and any status effects.
     */
    private void renderInspectPanel(DrawContext ctx, MinecraftClient client, int screenW,
                                     int entityId, int[] hpData, String typeIdRaw) {
        int eHp = hpData[0];
        int eMaxHp = hpData[1];
        float ePct = eMaxHp > 0 ? (float) eHp / eMaxHp : 0;

        // Parse status effects and boss metadata from typeId
        // Format: "minecraft:zombie;boss=Name;atk=6;def=3;spd=2;range=1;Stunned;Slowed"
        String[] parts = typeIdRaw.split(";");
        String typeId = parts[0];
        java.util.List<String> enemyEffects = new java.util.ArrayList<>();
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

        // Get display name — use boss name if available, otherwise derive from entity type
        String displayName;
        if (bossName != null) {
            displayName = bossName;
        } else {
            String rawId = typeId;
            int colon = rawId.indexOf(':');
            if (colon >= 0) rawId = rawId.substring(colon + 1);
            displayName = rawId.substring(0, 1).toUpperCase() + rawId.substring(1).replace('_', ' ');
        }

        // Panel dimensions (grows with effects count, wider for bosses)
        int panelW = bossName != null ? 140 : 120;
        int panelH = 100 + enemyEffects.size() * 10;
        int panelX = screenW - panelW - 8;
        int panelY = 6;

        // Background
        int bgColor = bossName != null ? 0xFF8B0000 : getMobColor(typeId);
        ctx.fill(panelX - 2, panelY - 2, panelX + panelW + 2, panelY + panelH + 2, 0xCC000000);
        ctx.fill(panelX, panelY, panelX + panelW, panelY + 14, (bgColor & 0x00FFFFFF) | 0xBB000000);

        // Name header (gold for bosses)
        int nameColor = bossName != null ? 0xFFFFAA00 : 0xFFFFFFFF;
            net.minecraft.util.Identifier inspectHead = MOB_HEAD_TEXTURES.get(typeId);
            if (inspectHead != null) {
                drawMobHead(ctx, inspectHead, panelX, panelY, 14);
                ctx.drawTextWithShadow(client.textRenderer,
                    Text.literal("\u00a7l" + displayName), panelX + 16, panelY + 3, nameColor);
            } else {
                ctx.drawCenteredTextWithShadow(client.textRenderer,
                    Text.literal("\u00a7l" + displayName), panelX + panelW / 2, panelY + 3, nameColor);
            }

        int y = panelY + 17;

        // HP bar (full width)
        int barW = panelW - 4;
        int barH = 8;
        ctx.fill(panelX + 2, y, panelX + 2 + barW, y + barH, 0xFF333333);
        int hpColor = ePct > 0.5f ? 0xFF55FF55 : ePct > 0.25f ? 0xFFFFFF55 : 0xFFFF5555;
        ctx.fill(panelX + 2, y, panelX + 2 + (int)(barW * ePct), y + barH, hpColor);
        ctx.drawCenteredTextWithShadow(client.textRenderer,
            Text.literal(eHp + " / " + eMaxHp + " HP"), panelX + panelW / 2, y, 0xFFFFFFFF);
        y += barH + 5;

        // Stats — use boss stats if available, otherwise fallback to entity type defaults
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

        // Active effects on this enemy
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

        // Enemy behavior hint
        String behavior = getAIHint(typeId);
        if (behavior != null) {
            ctx.drawTextWithShadow(client.textRenderer,
                Text.literal("\u00a78" + behavior), panelX + 4, y, 0xFF888888);
        }
    }

    private static int getDefaultStat(String typeId, String stat) {
        // Approximate stats based on entity type (matches biome JSON base values)
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

    /**
     * Draws a mob head texture at the given screen position, scaled to size x size pixels.
     * Textures are stored as 16x16 PNGs; matrix scaling is used when drawing at other sizes.
     */
    private static void drawMobHead(DrawContext ctx, net.minecraft.util.Identifier texture, int x, int y, int size) {
        if (size == 16) {
            ctx.drawTexture(texture, x, y, 0f, 0f, 16, 16, 16, 16);
        } else {
            ctx.getMatrices().push();
            ctx.getMatrices().translate(x, y, 0);
            float s = size / 16.0f;
            ctx.getMatrices().scale(s, s, 1.0f);
            ctx.drawTexture(texture, 0, 0, 0f, 0f, 16, 16, 16, 16);
            ctx.getMatrices().pop();
        }
    }

    /**
     * Get a representative color for each mob type (used as fallback icon).
     */
    private static int getMobColor(String entityTypeId) {
        return switch (entityTypeId) {
            case "minecraft:zombie", "minecraft:husk", "minecraft:drowned" -> 0xFF55AA55;
            case "minecraft:skeleton", "minecraft:stray", "minecraft:wither_skeleton" -> 0xFFCCCCCC;
            case "minecraft:creeper" -> 0xFF00CC00;
            case "minecraft:spider" -> 0xFF553333;
            case "minecraft:enderman" -> 0xFF330033;
            case "minecraft:blaze" -> 0xFFFF8800;
            case "minecraft:ghast" -> 0xFFEEEEEE;
            case "minecraft:phantom" -> 0xFF4466AA;
            case "minecraft:witch" -> 0xFF9933CC;
            case "minecraft:pillager" -> 0xFF666666;
            case "minecraft:vindicator" -> 0xFF777777;
            case "minecraft:shulker" -> 0xFF9955CC;
            case "minecraft:wolf" -> 0xFFBBAA99;
            case "minecraft:ocelot" -> 0xFFDDCC44;
            case "minecraft:piglin" -> 0xFFCC8855;
            case "minecraft:hoglin" -> 0xFF885533;
            case "minecraft:warden" -> 0xFF003344;
            case "minecraft:ender_dragon" -> 0xFF220022;
            case "minecraft:magma_cube" -> 0xFFCC4400;
            case "minecraft:goat" -> 0xFFCCBB99;
            case "minecraft:camel" -> 0xFFDDAA55;
            case "minecraft:breeze" -> 0xFF55CCFF;
            case "minecraft:bogged" -> 0xFF668844;
            case "minecraft:cave_spider" -> 0xFF224455;
            case "minecraft:silverfish" -> 0xFFAAAAAA;
            case "minecraft:slime" -> 0xFF55CC55;
            default -> 0xFF888888;
        };
    }

    /**
     * Get a 1-2 char display initial for fallback icon.
     */
    private static String getDisplayInitial(String entityTypeId) {
        String id = entityTypeId;
        int colon = id.indexOf(':');
        if (colon >= 0) id = id.substring(colon + 1);
        // Special short names
        return switch (entityTypeId) {
            case "minecraft:ender_dragon" -> "D";
            case "minecraft:wither_skeleton" -> "WS";
            case "minecraft:magma_cube" -> "MC";
            case "minecraft:cave_spider" -> "CS";
            case "minecraft:breeze" -> "Br";
            case "minecraft:bogged" -> "Bo";
            default -> id.substring(0, 1).toUpperCase();
        };
    }
}
