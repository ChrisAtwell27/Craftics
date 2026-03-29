package com.crackedgames.craftics.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Client-side visual effects for combat: floating damage numbers positioned
 * over entities, screen flash overlays, and attack indicators.
 */
public class CombatVisualEffects {

    private static final List<FloatingText> activeTexts = new ArrayList<>();
    private static final List<DelayedEffect> delayedEffects = new ArrayList<>();
    private static int screenFlashTicks = 0;
    private static int screenFlashColor = 0;
    private static int attackFlashTicks = 0;

    // Screen shake state
    private static float shakeIntensity = 0f;
    private static float shakeOffsetX = 0f;
    private static float shakeOffsetZ = 0f;
    private static final java.util.Random shakeRng = new java.util.Random();

    /**
     * Spawn a floating damage number over a specific entity using its entity ID.
     * Falls back to screen-center if entity not found.
     */
    public static void spawnDamageNumberAtEntity(int entityId, int damage, boolean isPlayerDamage) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getWindow() == null || client.world == null) return;

        int screenW = client.getWindow().getScaledWidth();
        int screenH = client.getWindow().getScaledHeight();

        // Try to find the entity's screen position
        var entity = client.world.getEntityById(entityId);
        float baseX, baseY;

        if (entity != null) {
            // Use entity's position projected to approximate screen location
            // In isometric view, offset from center based on entity position relative to arena center
            double relX = entity.getX() - CombatState.getArenaCenterX();
            double relZ = entity.getZ() - CombatState.getArenaCenterZ();
            // Isometric projection approximation (camera is SW-facing at ~55 deg pitch)
            baseX = (float)(screenW / 2 + (relX - relZ) * 16);
            baseY = (float)(screenH / 2 + (relX + relZ) * 8 - entity.getHeight() * 20);
        } else {
            // Fallback to center
            baseX = isPlayerDamage ? screenW / 2f : screenW / 2f + 40;
            baseY = isPlayerDamage ? screenH / 2f - 20 : screenH / 2f - 30;
        }

        // Random jitter
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

    /**
     * Spawn a floating damage number at screen center (for player damage from sync).
     */
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

    /**
     * Spawn death text at an entity's position.
     */
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

    /**
     * Flash a RED overlay when the player takes damage — very visible.
     */
    public static void flashDamage() {
        screenFlashTicks = 8;
        screenFlashColor = 0x88FF0000; // toned-down red
    }

    /**
     * Flash green when the player heals.
     */
    public static void flashHeal() {
        screenFlashTicks = 6;
        screenFlashColor = 0x5500FF00;
    }

    /**
     * Brief white border flash for player attack.
     */
    public static void flashAttack() {
        attackFlashTicks = 5;
    }

    /**
     * Trigger screen shake. Intensity scales with damage dealt.
     * @param intensity 0.0-1.0+ range (0.3 = light, 0.6 = medium, 1.0 = heavy)
     */
    public static void triggerShake(float intensity) {
        // Respect both screenShakeOnHit and disableCameraShake config
        try {
            if (!com.crackedgames.craftics.CrafticsMod.CONFIG.screenShakeOnHit()) return;
            if (com.crackedgames.craftics.CrafticsMod.CONFIG.disableCameraShake()) return;
        } catch (Exception ignored) {}
        shakeIntensity = Math.max(shakeIntensity, intensity);
    }

    /** Current shake X offset for camera. */
    public static float getShakeOffsetX() { return shakeOffsetX; }

    /** Current shake Z offset for camera. */
    public static float getShakeOffsetZ() { return shakeOffsetZ; }

    /**
     * Schedule damage visuals to fire after a delay (synced with animation impact frame).
     * @param delayTicks ticks to wait before showing damage number/flash/shake
     * @param entityId target entity for damage number positioning
     * @param damage amount of damage dealt
     */
    public static void scheduleImpact(int delayTicks, int entityId, int damage) {
        delayedEffects.add(new DelayedEffect(delayTicks, entityId, damage));
    }

    /**
     * Returns the animation impact tick for the player's current weapon.
     * Used to sync damage visuals with the animation's strike moment.
     */
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

    /**
     * Tick all active effects.
     */
    public static void tick() {
        if (screenFlashTicks > 0) screenFlashTicks--;
        if (attackFlashTicks > 0) attackFlashTicks--;

        // Process delayed effects
        Iterator<DelayedEffect> dit = delayedEffects.iterator();
        while (dit.hasNext()) {
            DelayedEffect de = dit.next();
            de.delay--;
            if (de.delay <= 0) {
                // Fire the impact visuals now
                spawnDamageNumberAtEntity(de.entityId, de.damage, false);
                flashAttack();
                float shakeAmount = Math.min(1.0f, de.damage / 8.0f) * 0.6f + 0.15f;
                triggerShake(shakeAmount);
                dit.remove();
            }
        }

        // Screen shake: compute random offsets, then decay
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

    /**
     * Render all active visual effects on the HUD.
     */
    public static void render(DrawContext ctx, MinecraftClient client, int screenW, int screenH) {
        // Screen flash overlay (damage/heal) — full screen tint
        if (screenFlashTicks > 0) {
            float alpha = (float) screenFlashTicks / 8.0f;
            int baseAlpha = (screenFlashColor >> 24) & 0xFF;
            int fadedAlpha = (int)(alpha * baseAlpha);
            int color = (screenFlashColor & 0x00FFFFFF) | (fadedAlpha << 24);
            ctx.fill(0, 0, screenW, screenH, color);
        }

        // Attack flash (bright white border pulse)
        if (attackFlashTicks > 0) {
            int alpha = (int)(((float) attackFlashTicks / 5.0f) * 120);
            int color = (alpha << 24) | 0xFFFFFF;
            int thickness = 3;
            ctx.fill(0, 0, screenW, thickness, color);
            ctx.fill(0, screenH - thickness, screenW, screenH, color);
            ctx.fill(0, 0, thickness, screenH, color);
            ctx.fill(screenW - thickness, 0, screenW, screenH, color);
        }

        // Floating damage/death numbers
        for (FloatingText ft : activeTexts) {
            float fade = Math.min(1.0f, ft.ticksRemaining / 10.0f);
            int alpha = (int)(fade * 255);
            int color = (ft.color & 0x00FFFFFF) | (alpha << 24);

            ctx.drawCenteredTextWithShadow(client.textRenderer,
                Text.literal(ft.text), (int) ft.x, (int) ft.y, color);
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
