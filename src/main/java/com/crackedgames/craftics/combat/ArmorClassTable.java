package com.crackedgames.craftics.combat;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;

/**
 * Armor Class lookup for the AC-based defense system.
 *
 * <p>Each armor material has a <b>base AC</b> ({@code B}); each worn piece
 * contributes independently per the per-slot formula:
 * <ul>
 *   <li>Leggings - {@code B}</li>
 *   <li>Chestplate - {@code B + 1}</li>
 *   <li>Helmet / Boots - {@code ⌈B / 2⌉}</li>
 * </ul>
 * Full-set totals: leather 7, chainmail/gold 11, iron/copper 13, diamond 19,
 * netherite 23. See {@code docs/superpowers/specs/2026-05-10-armor-class-overhaul-design.md}.
 *
 * <p>The pure math ({@link #baseAC} / {@link #pieceAC}) is registry-free so it
 * can be unit-tested without a Minecraft bootstrap. {@link #getPieceAC(Item)}
 * resolves a live item by parsing its registry ID - which means Copper Age
 * Backport armor (registered under the {@code minecraft:} namespace as
 * {@code copper_*}) is picked up automatically with no compat hook.
 */
public final class ArmorClassTable {

    private ArmorClassTable() {}

    /** Armor slot, decoupled from {@code net.minecraft.entity.EquipmentSlot} so the formula stays pure. */
    public enum Slot { HELMET, CHESTPLATE, LEGGINGS, BOOTS }

    /**
     * Base AC ({@code B}) for an armor material, keyed by the registry-ID prefix
     * (e.g. {@code "iron"} from {@code iron_helmet}). Unknown materials -
     * including modded armor that hasn't registered an AC - return 0.
     */
    public static int baseAC(String material) {
        return switch (material) {
            case "leather"   -> 2;
            case "chainmail" -> 3;
            case "golden"    -> 3;
            case "iron"      -> 4;
            case "copper"    -> 4;
            case "turtle"    -> 4; // turtle helmet treated as a B=4 helm (= 2 AC)
            case "diamond"   -> 6;
            case "netherite" -> 7;
            default          -> 0;
        };
    }

    /** Per-slot AC contribution from a material's base value. Non-positive base &rarr; 0. */
    public static int pieceAC(int baseB, Slot slot) {
        if (baseB <= 0) return 0;
        return switch (slot) {
            case LEGGINGS   -> baseB;
            case CHESTPLATE -> baseB + 1;
            case HELMET, BOOTS -> (baseB + 1) / 2; // integer ⌈B/2⌉
        };
    }

    /**
     * The material prefix of an armor item's registry ID (e.g. {@code "iron"} from
     * {@code iron_helmet}), or {@code null} if the item is not an armor piece.
     */
    public static String materialOf(Item item) {
        if (item == null) return null;
        String path = Registries.ITEM.getId(item).getPath();
        if (!(path.endsWith("_helmet") || path.endsWith("_chestplate")
                || path.endsWith("_leggings") || path.endsWith("_boots"))) {
            return null;
        }
        return path.substring(0, path.lastIndexOf('_'));
    }

    /**
     * The armor-set registry key for an armor item: its {@link #materialOf} material,
     * normalized to the key the armor-set registry uses. The registry registers gold
     * armor under {@code "gold"} while the item registry path yields {@code "golden"};
     * this bridges that one mismatch. Returns {@code null} for non-armor items.
     */
    public static String armorSetKeyOf(Item item) {
        String material = materialOf(item);
        if (material == null) return null;
        return "golden".equals(material) ? "gold" : material;
    }

    /**
     * AC contributed by a single armor item (vanilla + Copper Age Backport).
     * Returns 0 for empty slots, non-armor items, or unrecognized materials.
     */
    public static int getPieceAC(Item item) {
        if (item == null) return 0;
        String material = materialOf(item);
        if (material == null) return 0;
        String path = Registries.ITEM.getId(item).getPath();
        Slot slot;
        if (path.endsWith("_helmet"))          slot = Slot.HELMET;
        else if (path.endsWith("_chestplate")) slot = Slot.CHESTPLATE;
        else if (path.endsWith("_leggings"))   slot = Slot.LEGGINGS;
        else                                   slot = Slot.BOOTS;
        return pieceAC(baseAC(material), slot);
    }

    /** AC contributed by a worn stack. Empty stacks contribute 0. */
    public static int getPieceAC(ItemStack stack) {
        return (stack == null || stack.isEmpty()) ? 0 : getPieceAC(stack.getItem());
    }

    /**
     * Enemy DEF contribution from a single worn armor piece, material-scaled.
     *
     * <p>Enemies use the flat 5%-per-point / 60%-cap DEF stat, not the player's
     * AC dodge system, so a piece's AC ({@link #getPieceAC}) is compressed by a
     * third (⌈AC/3⌉) to stay inside that budget while still ranking materials.
     * Per full set: leather ~2, iron/copper ~4, diamond ~7, netherite ~8 DEF.
     * Non-armor / unknown materials contribute 0 (modded armor without an AC
     * falls back to the caller's flat +1, same as before).
     */
    public static int getPieceDefense(Item item) {
        return pieceDefenseFromAC(getPieceAC(item));
    }

    /** Pure AC-&gt;enemy-DEF compression (⌈ac/3⌉). Registry-free for unit tests. */
    public static int pieceDefenseFromAC(int ac) {
        return ac <= 0 ? 0 : (ac + 2) / 3;
    }

    /** Enemy DEF from a worn stack. Empty stacks contribute 0. */
    public static int getPieceDefense(ItemStack stack) {
        return (stack == null || stack.isEmpty()) ? 0 : getPieceDefense(stack.getItem());
    }
}
