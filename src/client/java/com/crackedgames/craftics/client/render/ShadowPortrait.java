package com.crackedgames.craftics.client.render;

import com.crackedgames.craftics.api.client.CrafticsClientAPI;
import com.crackedgames.craftics.entity.ModEntities;
import com.crackedgames.craftics.entity.ShadowCloneEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;

/**
 * A Shadow's icon in the combat HUD: the face of the player it copies, a little darker.
 *
 * <p>Craftics picks a head icon by entity type, and every Shadow is the same type whoever it
 * is a Shadow of. So this goes through the portrait hook addons use for exactly that problem:
 * it is handed the entity, asks it whose skin it wears, and draws that face. The turn order,
 * the rosters and the inspect panel all ask the same hook, so all three get it.
 */
public final class ShadowPortrait {

    private ShadowPortrait() {}

    /** Laid over the face. Dark enough to read as the Shadow, light enough to still read as you. */
    private static final int SHADE = 0x66100818;
    /** The red Craftics lays over its own head icons as a combatant gets hurt. */
    private static final int HURT_RED = 0x00FF1810;
    private static final int HURT_MAX_ALPHA = 168;

    public static void register() {
        CrafticsClientAPI.registerPortraitRenderer(ShadowPortrait::draw);
    }

    private static boolean draw(DrawContext ctx, int entityId, String typeId,
                                int x, int y, int size, float damageTint) {
        if (!ModEntities.SHADOW_CLONE_TYPE_ID.equals(typeId)) return false;
        var world = MinecraftClient.getInstance().world;
        // Not loaded on this client right now: let Craftics draw its usual stand-in.
        if (world == null || !(world.getEntityById(entityId) instanceof ShadowCloneEntity shadow)) return false;

        PlayerSkinDrawer.draw(ctx, ShadowCloneSkins.of(shadow), x, y, size);
        ctx.fill(x, y, x + size, y + size, SHADE);

        float hurt = Math.max(0f, Math.min(1f, damageTint));
        if (hurt > 0.02f) {
            ctx.fill(x, y, x + size, y + size, ((int) (hurt * HURT_MAX_ALPHA) << 24) | HURT_RED);
        }
        return true;
    }
}
