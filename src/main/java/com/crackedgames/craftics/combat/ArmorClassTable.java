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
 *   <li>Leggings &mdash; {@code B}</li>
 *   <li>Chestplate &mdash; {@code B + 1}</li>
 *   <li>Helmet / Boots &mdash; {@code ⌈B / 2⌉}</li>
 * </ul>
 * Full-set totals: leather 7, chainmail/gold 11, iron/copper 13, diamond 19,
 * netherite 23. See {@code docs/superpowers/specs/2026-05-10-armor-class-overhaul-design.md}.
 *
 * <p>The pure math ({@link #baseAC} / {@link #pieceAC}) is registry-free so it
 * can be unit-tested without a Minecraft bootstrap. {@link #getPieceAC(Item)}
 * resolves a live item by parsing its registry ID — which means Copper Age
 * Backport armor (registered under the {@code minecraft:} namespace as
 * {@code copper_*}) is picked up automatically with no compat hook.
 */
public final class ArmorClassTable {

    private ArmorClassTable() {}

    /** Armor slot, decoupled from {@code net.minecraft.entity.EquipmentSlot} so the formula stays pure. */
    public enum Slot { HELMET, CHESTPLATE, LEGGINGS, BOOTS }

    /**
     * Base AC ({@code B}) for an armor material, keyed by the registry-ID prefix
     * (e.g. {@code "iron"} from {@code iron_helmet}). Unknown materials &mdash;
     * including modded armor that hasn't registered an AC &mdash; return 0.
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
     * AC contributed by a single armor item (vanilla + Copper Age Backport).
     * Returns 0 for empty slots, non-armor items, or unrecognized materials.
     */
    public static int getPieceAC(Item item) {
        if (item == null) return 0;
        String path = Registries.ITEM.getId(item).getPath();
        Slot slot;
        if (path.endsWith("_helmet"))          slot = Slot.HELMET;
        else if (path.endsWith("_chestplate")) slot = Slot.CHESTPLATE;
        else if (path.endsWith("_leggings"))   slot = Slot.LEGGINGS;
        else if (path.endsWith("_boots"))      slot = Slot.BOOTS;
        else return 0;
        String material = path.substring(0, path.lastIndexOf('_'));
        return pieceAC(baseAC(material), slot);
    }

    /** AC contributed by a worn stack. Empty stacks contribute 0. */
    public static int getPieceAC(ItemStack stack) {
        return (stack == null || stack.isEmpty()) ? 0 : getPieceAC(stack.getItem());
    }
}
