package com.crackedgames.craftics.client.render;

import com.crackedgames.craftics.entity.ShadowCloneEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.client.util.SkinTextures;

import java.util.UUID;

/**
 * Whose skin a Shadow wears, and how dark it is drawn.
 *
 * <p>Kept apart from {@link ShadowCloneRenderer} because that class is written three times over
 * for three generations of Minecraft's renderer, and none of this changes between them.
 */
public final class ShadowCloneSkins {

    private ShadowCloneSkins() {}

    /**
     * What the skin is multiplied by: about a third of its brightness, leaning violet, so a
     * Shadow reads as its player seen in bad light rather than as a different person.
     */
    public static final int TINT = 0xFF58506E;

    /**
     * The skin of the player this Shadow copies. Every player in the fight is in the client's
     * player list, which is where their skin already lives, so nothing has to be sent. A
     * Shadow of nobody, or of somebody who has left, falls back to the default skin their id
     * would get.
     */
    public static SkinTextures of(ShadowCloneEntity shadow) {
        UUID owner = shadow.getOwnerUuid();
        if (owner != null) {
            ClientPlayNetworkHandler network = MinecraftClient.getInstance().getNetworkHandler();
            PlayerListEntry entry = network == null ? null : network.getPlayerListEntry(owner);
            if (entry != null) return entry.getSkinTextures();
        }
        return DefaultSkinHelper.getSkinTextures(owner != null ? owner : shadow.getUuid());
    }

    /** True when the copied player uses the slim-armed model. */
    public static boolean slim(SkinTextures skin) {
        return skin.model() == SkinTextures.Model.SLIM;
    }

    /** Multiply one ARGB colour by {@link #TINT}, channel by channel. */
    public static int darken(int argb) {
        int a = (argb >>> 24) & 0xFF;
        int r = ((argb >>> 16) & 0xFF) * ((TINT >>> 16) & 0xFF) / 255;
        int g = ((argb >>> 8) & 0xFF) * ((TINT >>> 8) & 0xFF) / 255;
        int b = (argb & 0xFF) * (TINT & 0xFF) / 255;
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
