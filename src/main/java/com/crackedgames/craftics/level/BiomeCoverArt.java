package com.crackedgames.craftics.level;

import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Where a biome's card art lives, and the only place a biome id is turned into a texture path.
 *
 * <h2>Why this is not a string concatenation</h2>
 *
 * <p>It used to be, at five separate call sites, and every one of them crashed on any biome id
 * carrying a namespace. {@code Identifier.of} rejects a path containing {@code :}, so
 * {@code "textures/gui/biomes/" + "myaddon:route_1" + ".png"} throws rather than returning
 * something that simply does not resolve. On the title screen that meant an exception every
 * frame, before the "no card art, draw a flat backdrop" fallback on the next line could ever
 * run - a fallback written for exactly this case and made unreachable by the line above it.
 *
 * <p>Craftics' own biomes are bare ({@code plains}, {@code cave}, {@code deep_dark}), so nothing
 * in the base game ever produced an id that could trip it. The documented convention for addon
 * biomes is {@code namespace:path}, which the addon template's own example uses, so every
 * code-registered addon campaign hit it immediately.
 *
 * <h2>Where art goes</h2>
 *
 * <ul>
 *   <li>A bare id resolves inside Craftics: {@code plains} →
 *       {@code craftics:textures/gui/biomes/plains.png}.</li>
 *   <li>A namespaced id resolves inside <b>that namespace</b>: {@code myaddon:route_1} →
 *       {@code myaddon:textures/gui/biomes/route_1.png}, which is
 *       {@code assets/myaddon/textures/gui/biomes/route_1.png} in the addon's own jar.</li>
 * </ul>
 *
 * <p>The addon's own namespace rather than a subfolder under Craftics': it is where a Minecraft
 * mod's assets already live, so an addon ships art without coordinating a directory layout with
 * anyone, and two addons using the same biome name cannot overwrite each other's cards.
 *
 * <p>Nothing here throws. An id that cannot name a texture at all returns null, and every caller
 * treats null the same way it treats art that is simply absent.
 */
public final class BiomeCoverArt {

    private BiomeCoverArt() {}

    /** Directory, under whichever namespace owns the biome, that holds biome card art. */
    public static final String DIR = "textures/gui/biomes/";

    /**
     * The card art texture for a biome, or null when the id cannot name one.
     *
     * @param biomeId a bare id ({@code plains}) or a namespaced one ({@code myaddon:route_1})
     */
    @Nullable
    public static Identifier coverTexture(@Nullable String biomeId) {
        if (biomeId == null || biomeId.isBlank()) return null;

        String namespace = "craftics";
        String path = biomeId;
        int colon = biomeId.indexOf(':');
        if (colon >= 0) {
            namespace = biomeId.substring(0, colon);
            path = biomeId.substring(colon + 1);
            if (namespace.isEmpty() || path.isEmpty()) return null;
        }

        try {
            return Identifier.of(namespace, DIR + path + ".png");
        } catch (Exception invalid) {
            // An id with characters no resource location accepts - uppercase, a space, a
            // second colon. Null so the caller draws its fallback, which is the same
            // outcome as art that was never shipped, and the outcome the caller is
            // already written to handle.
            return null;
        }
    }

    /**
     * The classpath location of a biome's card art inside Craftics' own jar, or null when the
     * art belongs to some other mod.
     *
     * <p>Used for the world icon, which is written by reading the PNG straight out of the jar
     * rather than through a resource manager - so it can only reach art Craftics itself ships.
     * A namespaced biome's art lives in that addon's jar and is not reachable this way, which
     * is a missing world icon and nothing worse.
     */
    @Nullable
    public static String crafticsClasspathPath(@Nullable String biomeId) {
        if (biomeId == null || biomeId.isBlank() || biomeId.indexOf(':') >= 0) return null;
        return "/assets/craftics/" + DIR + biomeId + ".png";
    }
}
