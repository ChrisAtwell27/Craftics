package com.crackedgames.craftics.combat;

import com.crackedgames.craftics.core.GridPos;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.Items;

import java.util.Map;

/**
 * Banner combat utilities. Single source of truth for the 16 vanilla banner
 * item↔block↔color-id mapping plus the pure-logic banner-zone defense lookup.
 *
 * <p>Mirrors the {@link HornVariants} pattern: anything that might need
 * Stonecutter version-drift handling for banners would land here, isolated
 * from the rest of the combat code. As of 1.21.1–1.21.5 banner block APIs
 * are stable, so no Stonecutter blocks are needed today.
 *
 * <p>The {@code Items.*}/{@code Blocks.*}-keyed lookup tables live in nested
 * holder classes so they are only initialized on first use. This keeps the
 * pure-logic methods ({@link #isBannerEffect}, {@link #defenseBonusAt})
 * loadable in unit tests, where Minecraft's bootstrap hasn't run.
 */
public final class BannerEffects {

    private BannerEffects() {}

    /** Tile-effect map values starting with this prefix indicate a planted
     *  banner. Stored either as the legacy {@code "banner"}, the
     *  color-tagged {@code "banner:<color>"}, or the
     *  Special-scaled {@code "banner:<color>:<bonusDef>"} (current). */
    public static final String EFFECT_PREFIX = "banner";

    /** Manhattan distance within which the +DEF aura applies. */
    public static final int AURA_RADIUS = 2;

    /** Base defense bonus granted by standing inside any banner aura. The
     *  player's Special-class affinity adds to this at placement time. */
    public static final int DEFENSE_BONUS = 2;

    /** Lazily initialized so the rest of the class can be loaded in unit tests
     *  without bootstrapping {@code Items}. */
    private static final class ItemToColor {
        static final Map<Item, String> MAP = Map.ofEntries(
            Map.entry(Items.WHITE_BANNER,      "white"),
            Map.entry(Items.ORANGE_BANNER,     "orange"),
            Map.entry(Items.MAGENTA_BANNER,    "magenta"),
            Map.entry(Items.LIGHT_BLUE_BANNER, "light_blue"),
            Map.entry(Items.YELLOW_BANNER,     "yellow"),
            Map.entry(Items.LIME_BANNER,       "lime"),
            Map.entry(Items.PINK_BANNER,       "pink"),
            Map.entry(Items.GRAY_BANNER,       "gray"),
            Map.entry(Items.LIGHT_GRAY_BANNER, "light_gray"),
            Map.entry(Items.CYAN_BANNER,       "cyan"),
            Map.entry(Items.PURPLE_BANNER,     "purple"),
            Map.entry(Items.BLUE_BANNER,       "blue"),
            Map.entry(Items.BROWN_BANNER,      "brown"),
            Map.entry(Items.GREEN_BANNER,      "green"),
            Map.entry(Items.RED_BANNER,        "red"),
            Map.entry(Items.BLACK_BANNER,      "black")
        );
    }

    private static final class ItemToBlock {
        static final Map<Item, Block> MAP = Map.ofEntries(
            Map.entry(Items.WHITE_BANNER,      Blocks.WHITE_BANNER),
            Map.entry(Items.ORANGE_BANNER,     Blocks.ORANGE_BANNER),
            Map.entry(Items.MAGENTA_BANNER,    Blocks.MAGENTA_BANNER),
            Map.entry(Items.LIGHT_BLUE_BANNER, Blocks.LIGHT_BLUE_BANNER),
            Map.entry(Items.YELLOW_BANNER,     Blocks.YELLOW_BANNER),
            Map.entry(Items.LIME_BANNER,       Blocks.LIME_BANNER),
            Map.entry(Items.PINK_BANNER,       Blocks.PINK_BANNER),
            Map.entry(Items.GRAY_BANNER,       Blocks.GRAY_BANNER),
            Map.entry(Items.LIGHT_GRAY_BANNER, Blocks.LIGHT_GRAY_BANNER),
            Map.entry(Items.CYAN_BANNER,       Blocks.CYAN_BANNER),
            Map.entry(Items.PURPLE_BANNER,     Blocks.PURPLE_BANNER),
            Map.entry(Items.BLUE_BANNER,       Blocks.BLUE_BANNER),
            Map.entry(Items.BROWN_BANNER,      Blocks.BROWN_BANNER),
            Map.entry(Items.GREEN_BANNER,      Blocks.GREEN_BANNER),
            Map.entry(Items.RED_BANNER,        Blocks.RED_BANNER),
            Map.entry(Items.BLACK_BANNER,      Blocks.BLACK_BANNER)
        );
    }

    /** True iff the item is one of the 16 vanilla banners. */
    public static boolean isBanner(Item item) {
        return item != null && ItemToColor.MAP.containsKey(item);
    }

    /** Banner color id (e.g. {@code "white"}, {@code "light_blue"}) for use
     *  in tile-effect string encoding. Returns {@code null} if not a banner. */
    public static String colorIdForItem(Item item) {
        if (item == null) return null;
        return ItemToColor.MAP.get(item);
    }

    /** Banner block to place in the world for the given banner item.
     *  Returns {@code null} if not a banner. */
    public static Block blockForItem(Item item) {
        if (item == null) return null;
        return ItemToBlock.MAP.get(item);
    }

    /** Resolve a banner color id back to its Block. Used when re-creating
     *  the visible block from the tile-effect string at placement time. */
    public static Block blockForColorId(String colorId) {
        if (colorId == null) return null;
        return switch (colorId) {
            case "white"      -> Blocks.WHITE_BANNER;
            case "orange"     -> Blocks.ORANGE_BANNER;
            case "magenta"    -> Blocks.MAGENTA_BANNER;
            case "light_blue" -> Blocks.LIGHT_BLUE_BANNER;
            case "yellow"     -> Blocks.YELLOW_BANNER;
            case "lime"       -> Blocks.LIME_BANNER;
            case "pink"       -> Blocks.PINK_BANNER;
            case "gray"       -> Blocks.GRAY_BANNER;
            case "light_gray" -> Blocks.LIGHT_GRAY_BANNER;
            case "cyan"       -> Blocks.CYAN_BANNER;
            case "purple"     -> Blocks.PURPLE_BANNER;
            case "blue"       -> Blocks.BLUE_BANNER;
            case "brown"      -> Blocks.BROWN_BANNER;
            case "green"      -> Blocks.GREEN_BANNER;
            case "red"        -> Blocks.RED_BANNER;
            case "black"      -> Blocks.BLACK_BANNER;
            default           -> null;
        };
    }

    /** True iff the given tile-effect map value represents a banner. Accepts
     *  the legacy {@code "banner"}, color-tagged {@code "banner:<color>"},
     *  and Special-scaled {@code "banner:<color>:<bonusDef>"} forms — old
     *  saves keep working. */
    public static boolean isBannerEffect(String tileEffectValue) {
        if (tileEffectValue == null || tileEffectValue.isEmpty()) return false;
        return tileEffectValue.equals(EFFECT_PREFIX)
            || tileEffectValue.startsWith(EFFECT_PREFIX + ":");
    }

    /**
     * Defense bonus for a position from any banner zone in the given tile-effects
     * map. Returns the per-banner DEF value (frozen at placement time, encoded
     * into the tile-effect string) for the strongest banner whose aura covers
     * the position, or {@code 0} if no banner is in range.
     *
     * <p>Bonuses do NOT compound across overlapping banners; the position
     * receives only the highest single banner's bonus.
     */
    public static int defenseBonusAt(GridPos pos, Map<GridPos, String> tileEffects) {
        if (pos == null || tileEffects == null || tileEffects.isEmpty()) return 0;
        int best = 0;
        for (var entry : tileEffects.entrySet()) {
            if (!isBannerEffect(entry.getValue())) continue;
            if (pos.manhattanDistance(entry.getKey()) > AURA_RADIUS) continue;
            int bonus = parseEncodedBonus(entry.getValue());
            if (bonus > best) best = bonus;
        }
        return best;
    }

    /** Parse the trailing {@code :<int>} from a banner tile-effect value.
     *  Returns {@link #DEFENSE_BONUS} for legacy values ({@code "banner"} or
     *  {@code "banner:<color>"}) that don't encode the bonus. */
    private static int parseEncodedBonus(String tileEffectValue) {
        if (tileEffectValue == null) return DEFENSE_BONUS;
        int lastColon = tileEffectValue.lastIndexOf(':');
        if (lastColon < 0) return DEFENSE_BONUS; // bare "banner"
        String tail = tileEffectValue.substring(lastColon + 1);
        try {
            int parsed = Integer.parseInt(tail);
            return parsed > 0 ? parsed : DEFENSE_BONUS;
        } catch (NumberFormatException e) {
            return DEFENSE_BONUS; // tail was the color id, not a number
        }
    }
}
