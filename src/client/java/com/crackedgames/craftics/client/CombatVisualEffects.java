package com.crackedgames.craftics.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class CombatVisualEffects {

    private static final List<FloatingText> activeTexts = new ArrayList<>();
    private static final List<DelayedEffect> delayedEffects = new ArrayList<>();
    private static int screenFlashTicks = 0;
    private static int screenFlashColor = 0;
    private static int attackFlashTicks = 0;

    private static int deathOverlayTick = 0;
    private static int deathOverlayDuration = 0;
    private static int downedFlashTicks = 0;

    private static float shakeIntensity = 0f;
    private static float shakeOffsetX = 0f;
    private static float shakeOffsetZ = 0f;
    private static final java.util.Random shakeRng = new java.util.Random();

    public static void spawnDamageNumberAtEntity(int entityId, int damage, boolean isPlayerDamage) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getWindow() == null || client.world == null) return;

        int screenW = client.getWindow().getScaledWidth();
        int screenH = client.getWindow().getScaledHeight();

        var entity = client.world.getEntityById(entityId);
        float baseX, baseY;

        if (entity != null) {
            // Rough isometric projection for damage number placement
            double relX = entity.getX() - CombatState.getArenaCenterX();
            double relZ = entity.getZ() - CombatState.getArenaCenterZ();
            baseX = (float)(screenW / 2 + (relX - relZ) * 16);
            baseY = (float)(screenH / 2 + (relX + relZ) * 8 - entity.getHeight() * 20);
        } else {
            baseX = isPlayerDamage ? screenW / 2f : screenW / 2f + 40;
            baseY = isPlayerDamage ? screenH / 2f - 20 : screenH / 2f - 30;
        }

        baseX += (float)((Math.random() - 0.5) * 30);
        baseY += (float)((Math.random() - 0.5) * 10);

        String text = "-" + damage;
        int color = isPlayerDamage ? 0xFFFF4444 : 0xFFFFAA00;
        if (damage >= 5) {
            text = "\u00a7l" + text;
            color = isPlayerDamage ? 0xFFFF2222 : 0xFFFF6600;
        }

        activeTexts.add(new FloatingText(baseX, baseY, text, color, 35));
    }

    public static void spawnDamageNumber(int damage, boolean isPlayerDamage) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getWindow() == null) return;

        int screenW = client.getWindow().getScaledWidth();
        int screenH = client.getWindow().getScaledHeight();

        float baseX = screenW / 2f + (float)((Math.random() - 0.5) * 40);
        float baseY = screenH / 2f - 30 + (float)((Math.random() - 0.5) * 15);

        String text = "-" + damage;
        int color = 0xFFFF4444;
        if (damage >= 5) text = "\u00a7l" + text;

        activeTexts.add(new FloatingText(baseX, baseY, text, color, 35));
    }

    public static void spawnDeathTextAtEntity(int entityId, String mobName) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getWindow() == null || client.world == null) return;

        int screenW = client.getWindow().getScaledWidth();
        int screenH = client.getWindow().getScaledHeight();

        var entity = client.world.getEntityById(entityId);
        float baseX, baseY;

        if (entity != null) {
            double relX = entity.getX() - CombatState.getArenaCenterX();
            double relZ = entity.getZ() - CombatState.getArenaCenterZ();
            baseX = (float)(screenW / 2 + (relX - relZ) * 16);
            baseY = (float)(screenH / 2 + (relX + relZ) * 8 - 40);
        } else {
            baseX = screenW / 2f + (float)((Math.random() - 0.5) * 40);
            baseY = screenH / 2f - 40;
        }

        activeTexts.add(new FloatingText(baseX, baseY, "\u2620 " + mobName, 0xFFFF4444, 45));
    }

    public static void flashDamage() {
        screenFlashTicks = 8;
        screenFlashColor = 0x88FF0000; // toned-down red
    }

    public static void flashHeal() {
        screenFlashTicks = 6;
        screenFlashColor = 0x5500FF00;
    }

    public static void flashAttack() {
        attackFlashTicks = 5;
    }

    public static void triggerShakeTimed(float intensity, int durationTicks) {
        triggerShake(intensity);
        // durationTicks is honoured by the exponential decay already in tick() — we retain
        // the parameter in the signature so callers stay forward-compatible if we later add
        // a non-exponential duration model.
    }

    public static void flashWithColor(int argb, int durationTicks) {
        screenFlashTicks = Math.max(1, durationTicks);
        screenFlashColor = argb;
    }

    public static void addFloatingTextAt(double worldX, double worldY, double worldZ,
                                          String text, int color, int lifetimeTicks) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getWindow() == null) return;
        int screenW = client.getWindow().getScaledWidth();
        int screenH = client.getWindow().getScaledHeight();
        double relX = worldX - CombatState.getArenaCenterX();
        double relZ = worldZ - CombatState.getArenaCenterZ();
        float baseX = (float)(screenW / 2 + (relX - relZ) * 16);
        float baseY = (float)(screenH / 2 + (relX + relZ) * 8 - 20);
        activeTexts.add(new FloatingText(baseX, baseY, text, color, lifetimeTicks));
    }

    // Distinct from downed flash: slower, darker, more ominous
    public static void startDeathOverlay(int durationTicks) {
        deathOverlayTick = 0;
        deathOverlayDuration = durationTicks > 0 ? durationTicks : 60;
    }

    // Party member downed (NOT dead) — amber flash, distinct from red death overlay
    public static void flashDowned() {
        downedFlashTicks = 15;
        screenFlashTicks = 10;
        screenFlashColor = 0x88FF8800; // amber/orange
    }

    public static void resetOverlays() {
        deathOverlayTick = 0;
        deathOverlayDuration = 0;
        downedFlashTicks = 0;
        screenFlashTicks = 0;
        attackFlashTicks = 0;
    }

    // intensity: 0.3 = light, 0.6 = medium, 1.0 = heavy
    public static void triggerShake(float intensity) {
        try {
            if (!com.crackedgames.craftics.CrafticsMod.CONFIG.screenShakeOnHit()) return;
            if (com.crackedgames.craftics.CrafticsMod.CONFIG.disableCameraShake()) return;
        } catch (Exception ignored) {}
        shakeIntensity = Math.max(shakeIntensity, intensity);
    }

    public static float getShakeOffsetX() { return shakeOffsetX; }
    public static float getShakeOffsetZ() { return shakeOffsetZ; }

    // Fire damage visuals after a delay to sync with animation impact frame
    public static void scheduleImpact(int delayTicks, int entityId, int damage) {
        delayedEffects.add(new DelayedEffect(delayTicks, entityId, damage));
    }

    // Which tick the weapon animation actually "hits" — syncs damage VFX with strike
    public static int getWeaponImpactDelay() {
        var client = net.minecraft.client.MinecraftClient.getInstance();
        if (client.player == null) return 5;
        String itemId = net.minecraft.registry.Registries.ITEM.getId(
            client.player.getMainHandStack().getItem()).getPath();

        if (itemId.contains("bow") || itemId.contains("crossbow")) return 10; // release frame
        if (itemId.contains("axe")) return 9;     // slam frame
        if (itemId.contains("mace")) return 10;    // slam frame
        if (itemId.contains("trident")) return 5;  // stab frame
        return 6; // sword slash impact
    }

    public static void tick() {
        if (com.crackedgames.craftics.client.vfx.HitPauseState.isFrozen()) {
            com.crackedgames.craftics.client.vfx.HitPauseState.tick();
            return;
        }
        if (screenFlashTicks > 0) screenFlashTicks--;
        if (attackFlashTicks > 0) attackFlashTicks--;
        if (downedFlashTicks > 0) downedFlashTicks--;
        if (deathOverlayDuration > 0 && deathOverlayTick < deathOverlayDuration) {
            deathOverlayTick++;
        }
        Iterator<DelayedEffect> dit = delayedEffects.iterator();
        while (dit.hasNext()) {
            DelayedEffect de = dit.next();
            de.delay--;
            if (de.delay <= 0) {
                spawnDamageNumberAtEntity(de.entityId, de.damage, false);
                flashAttack();
                float shakeAmount = Math.min(1.0f, de.damage / 8.0f) * 0.6f + 0.15f;
                triggerShake(shakeAmount);
                dit.remove();
            }
        }

        if (shakeIntensity > 0.01f) {
            shakeOffsetX = (shakeRng.nextFloat() - 0.5f) * 2f * shakeIntensity;
            shakeOffsetZ = (shakeRng.nextFloat() - 0.5f) * 2f * shakeIntensity;
            shakeIntensity *= 0.7f; // fast exponential decay
        } else {
            shakeIntensity = 0f;
            shakeOffsetX = 0f;
            shakeOffsetZ = 0f;
        }

        Iterator<FloatingText> it = activeTexts.iterator();
        while (it.hasNext()) {
            FloatingText ft = it.next();
            ft.ticksRemaining--;
            ft.y -= 1.0f; // float upward faster
            if (ft.ticksRemaining <= 0) it.remove();
        }
    }

    public static void render(DrawContext ctx, MinecraftClient client, int screenW, int screenH) {
        // Status-effect vignettes. Rendered first so the flash/death overlays draw on top.
        // These are purely client-side and only show for the player with the effect —
        // each client reads its own CombatState which reflects only that player's effects.
        // Depth scales with effect level so stacking the effect visibly encroaches further
        // into the screen.
        if (CombatState.isInCombat()) {
            int blind = CombatState.getBlindnessLevel();
            if (blind > 0) {
                drawVignette(ctx, screenW, screenH, 0x000000,
                    Math.min(255, 220 + blind * 8),
                    scaledDepth(0.55f, blind, 0.12f, 0.9f));
            }
            int poison = CombatState.getPoisonLevel();
            if (poison > 0) {
                drawVignette(ctx, screenW, screenH, 0x33AA33,
                    Math.min(220, 150 + poison * 18),
                    scaledDepth(0.32f, poison, 0.10f, 0.75f));
            }
            int burning = CombatState.getBurningLevel();
            if (burning > 0) {
                drawVignette(ctx, screenW, screenH, 0xCC3311,
                    Math.min(230, 170 + burning * 16),
                    scaledDepth(0.34f, burning, 0.10f, 0.78f));
            }
        }

        if (screenFlashTicks > 0) {
            float alpha = (float) screenFlashTicks / 8.0f;
            int baseAlpha = (screenFlashColor >> 24) & 0xFF;
            int fadedAlpha = (int)(alpha * baseAlpha);
            int color = (screenFlashColor & 0x00FFFFFF) | (fadedAlpha << 24);
            ctx.fill(0, 0, screenW, screenH, color);
        }

        if (attackFlashTicks > 0) {
            int alpha = (int)(((float) attackFlashTicks / 5.0f) * 120);
            int color = (alpha << 24) | 0xFFFFFF;
            int thickness = 3;
            ctx.fill(0, 0, screenW, thickness, color);
            ctx.fill(0, screenH - thickness, screenW, screenH, color);
            ctx.fill(0, 0, thickness, screenH, color);
            ctx.fill(screenW - thickness, 0, screenW, screenH, color);
        }

        for (FloatingText ft : activeTexts) {
            float fade = Math.min(1.0f, ft.ticksRemaining / 10.0f);
            int alpha = (int)(fade * 255);
            int color = (ft.color & 0x00FFFFFF) | (alpha << 24);

            ctx.drawCenteredTextWithShadow(client.textRenderer,
                Text.literal(ft.text), (int) ft.x, (int) ft.y, color);
        }

        if (deathOverlayDuration > 0 && deathOverlayTick > 0) {
            float progress = (float) deathOverlayTick / deathOverlayDuration;
            float eased = progress * progress;
            int alpha = (int)(eased * 180); // max ~70% opacity
            int deathColor = (alpha << 24) | 0x220000; // very dark red / near-black
            ctx.fill(0, 0, screenW, screenH, deathColor);

            if (progress > 0.65f) {
                float textAlpha = (progress - 0.65f) / 0.35f;
                int textColor = ((int)(textAlpha * 255) << 24) | 0xCC2222;
                ctx.drawCenteredTextWithShadow(client.textRenderer,
                    Text.literal("\u00a7l\u2620 YOU DIED \u2620"),
                    screenW / 2, screenH / 2 - 10, textColor);
            }
        }

        if (downedFlashTicks > 0) {
            float progress = (float) downedFlashTicks / 15.0f;
            int alpha = (int)(progress * 160);
            int borderColor = (alpha << 24) | 0xFF8800; // orange
            int thickness = (int)(6 * progress) + 2;
            ctx.fill(0, 0, screenW, thickness, borderColor);
            ctx.fill(0, screenH - thickness, screenW, screenH, borderColor);
            ctx.fill(0, 0, thickness, screenH, borderColor);
            ctx.fill(screenW - thickness, 0, screenW, screenH, borderColor);

            if (downedFlashTicks > 5) {
                float textAlpha = progress;
                int textColor = ((int)(textAlpha * 255) << 24) | 0xFF8800;
                ctx.drawCenteredTextWithShadow(client.textRenderer,
                    Text.literal("\u00a7l\u00a76DOWNED"),
                    screenW / 2, screenH / 2 - 10, textColor);
            }
        }
    }

    /**
     * Scale a vignette's base depth by the effect level (stack count). Level 1 uses
     * the base depth; each additional level adds {@code perLevel} fraction of screen
     * up to {@code cap}. Used so stacking Poison II, III, etc. visibly encroaches
     * further into the view.
     */
    private static float scaledDepth(float base, int level, float perLevel, float cap) {
        float depth = base + Math.max(0, level - 1) * perLevel;
        return Math.min(cap, depth);
    }

    /**
     * Draw a rectangular vignette by layering nested frame slices with a quadratic
     * alpha falloff from the edges toward the centre. No texture required.
     *
     * @param rgb           The vignette colour as 0xRRGGBB.
     * @param maxAlpha      Peak alpha at the outermost pixel (0–255).
     * @param depthFraction Fraction of the shortest screen side the vignette reaches
     *                      before fully fading out. 0.35–0.6 looks natural.
     */
    private static void drawVignette(DrawContext ctx, int screenW, int screenH,
                                     int rgb, int maxAlpha, float depthFraction) {
        int shortSide = Math.min(screenW, screenH);
        int bandDepth = Math.max(20, (int)(shortSide * depthFraction));
        int slices = 32;
        int sliceW = Math.max(1, bandDepth / slices);
        int actualSlices = bandDepth / sliceW;

        for (int i = 0; i < actualSlices; i++) {
            int inset = i * sliceW;
            // Quadratic fade from 1.0 at the edge to 0.0 at depth
            float t = (float) i / actualSlices;
            float fade = (1.0f - t) * (1.0f - t);
            int alpha = (int)(maxAlpha * fade);
            if (alpha <= 0) continue;
            int color = (alpha << 24) | (rgb & 0xFFFFFF);
            // Top band
            ctx.fill(inset, inset, screenW - inset, inset + sliceW, color);
            // Bottom band
            ctx.fill(inset, screenH - inset - sliceW, screenW - inset, screenH - inset, color);
            // Left band (exclude corners already covered by top/bottom)
            ctx.fill(inset, inset + sliceW, inset + sliceW, screenH - inset - sliceW, color);
            // Right band (exclude corners)
            ctx.fill(screenW - inset - sliceW, inset + sliceW, screenW - inset, screenH - inset - sliceW, color);
        }
    }

    private static class FloatingText {
        float x, y;
        String text;
        int color;
        int ticksRemaining;

        FloatingText(float x, float y, String text, int color, int ticks) {
            this.x = x; this.y = y;
            this.text = text; this.color = color;
            this.ticksRemaining = ticks;
        }
    }

    private static class DelayedEffect {
        int delay;
        int entityId;
        int damage;

        DelayedEffect(int delay, int entityId, int damage) {
            this.delay = delay;
            this.entityId = entityId;
            this.damage = damage;
        }
    }
}
