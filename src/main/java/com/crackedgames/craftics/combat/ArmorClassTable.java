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
 * Full-set totals: leather/gold 7, chainmail 11, iron/copper 13, diamond 19,
 * netherite 23. See {@code docs/superpowers/specs/2026-05-10-armor-class-overhaul-design.md}.
 *
 * <p>The pure math ({@link #baseAC} / {@link #pieceAC}) is registry-free so it
 * can be unit-tested without a Minecraft bootstrap. {@link #getPieceAC(Item)}
 * resolves a live item by parsing its registry ID - which means Copper Age
 * Backport armor (registered under the {@code minecraft:} namespace as
 * {@code copper_*}) is picked up automatically with no compat hook.
 *
 * <p>A material {@link #baseAC} does not know falls back to whatever
 * {@code ArmorSetRegistry} has registered for that key, so a mod (or a datapack)
 * can give its armor an AC by registering an {@code ArmorSetEntry.armorClass}
 * rather than by editing the switch below.
 */
public final class ArmorClassTable {

    private ArmorClassTable() {}

    /** Armor slot, decoupled from {@code net.minecraft.entity.EquipmentSlot} so the formula stays pure. */
    public enum Slot { HELMET, CHESTPLATE, LEGGINGS, BOOTS }

    /**
     * Base AC ({@code B}) for a built-in armor material. Accepts BOTH spellings of the
     * gold material: the item registry path yields {@code "golden"} (from
     * {@code golden_helmet}) while the armor-set key is {@code "gold"}, and this table is
     * looked up with each of them from different call sites. Unknown materials return 0;
     * use {@link #resolveBaseAC} to also consult the armor-set registry. Kept
     * registry-free so the AC formula stays unit-testable.
     */
    public static int baseAC(String material) {
        return switch (material) {
            case "leather"   -> 2;
            case "chainmail" -> 3;
            // Gold is the Gambler set: crit chance and emeralds, not protection. It is
            // deliberately the softest metal in the game, on a par with leather.
            case "golden", "gold" -> 2;
            case "iron"      -> 4;
            case "copper"    -> 4;
            case "turtle"    -> 4; // turtle helmet treated as a B=4 helm (= 2 AC)
            case "diamond"   -> 6;
            case "netherite" -> 7;
            default          -> 0;
        };
    }

    /**
     * Per-slot AC contribution from a material's base value. Non-positive base &rarr; 0.
     * A positive base never yields a 0 piece: the softest set (B=1, the Robe) still
     * gives every slot at least 1 AC, so wearing a piece always counts for something.
     */
    public static int pieceAC(int baseB, Slot slot) {
        if (baseB <= 0) return 0;
        int ac = switch (slot) {
            case LEGGINGS   -> baseB;
            case CHESTPLATE -> baseB + 1;
            case HELMET, BOOTS -> (baseB + 1) / 2; // integer ⌈B/2⌉
        };
        return Math.max(1, ac);
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
     * Base AC for an armor set key: the built-in {@link #baseAC} table first, then
     * whatever {@code ArmorSetRegistry} has registered for that key. Registered sets
     * therefore cannot override a vanilla material's AC, only supply one for a
     * material the table doesn't know.
     */
    public static int resolveBaseAC(String armorSetKey) {
        if (armorSetKey == null) return 0;
        int builtIn = baseAC(armorSetKey);
        if (builtIn > 0) return builtIn;
        return com.crackedgames.craftics.api.registry.ArmorSetRegistry.getArmorClass(armorSetKey);
    }

    /** The slot an armor item occupies, or {@code null} if it is not an armor piece. */
    public static Slot slotOf(Item item) {
        if (item == null) return null;
        String path = Registries.ITEM.getId(item).getPath();
        if (path.endsWith("_helmet"))     return Slot.HELMET;
        if (path.endsWith("_chestplate")) return Slot.CHESTPLATE;
        if (path.endsWith("_leggings"))   return Slot.LEGGINGS;
        if (path.endsWith("_boots"))      return Slot.BOOTS;
        return null;
    }

    /**
     * AC contributed by a single armor item - vanilla, Copper Age Backport, or any
     * modded set that registered an {@code armorClass}. Returns 0 for empty slots,
     * non-armor items, or materials with no AC anywhere.
     */
    public static int getPieceAC(Item item) {
        if (item == null) return 0;
        Slot slot = slotOf(item);
        if (slot == null) return 0;
        return pieceAC(resolveBaseAC(armorSetKeyOf(item)), slot);
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
