package com.crackedgames.craftics.client;

import com.crackedgames.craftics.api.registry.WeaponEntry;
import com.crackedgames.craftics.api.registry.WeaponRegistry;
import com.crackedgames.craftics.compat.aether.AetherCompat;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.List;

/**
 * Builds the Craftics tooltip lines for Aether items.
 *
 * <p>Weapons get the full combat block in place of the Aether's own tooltip, which describes
 * what the item does in the Aether and not what it does on this grid. Accessories and
 * consumables keep theirs and gain a short block saying what they are worth in a fight.
 *
 * <p>The ability text here must mirror {@code AetherCompat} and {@code AetherScanner}: the
 * numbers that can be read live (damage, AP, range, a zanite blade's current bonus) are, and
 * the rest are written out. {@code AetherTooltipCoverageTest} holds the two lists together.
 */
public final class AetherTooltips {

    private AetherTooltips() {}

    public static boolean isAether(Identifier id) {
        return AetherCompat.MOD_ID.equals(id.getNamespace());
    }

    /** The combat block for a registered Aether weapon. */
    public static void appendWeaponLines(ItemStack stack, String path, List<Text> lines) {
        Item item = stack.getItem();
        WeaponEntry entry = WeaponRegistry.getOrNull(item);
        if (entry == null) return;
        int dmg = entry.attackPower().getAsInt();
        int ap = entry.apCost();
        var dt = entry.damageType();

        lines.add(Text.empty());
        lines.add(Text.literal("§6§lCraftics Combat:"));
        // Same shape as the vanilla weapon stat line: "<dmg> DMG | Range N | N AP | Type"
        StringBuilder stat = new StringBuilder();
        stat.append("§c").append(dmg).append(" DMG §7| Range ").append(entry.range()).append(" §7| ");
        if (ap > 1) stat.append("§c");
        stat.append(ap).append(" AP §7| ").append(dt.color)
            .append(com.crackedgames.craftics.api.registry.AffinitySkinRegistry.nameOf(dt));
        lines.add(Text.literal(stat.toString()));

        for (String line : weaponLines(path)) {
            TooltipWrap.addWrapped(lines, " ", "§e• " + line);
        }
        // The one number on an Aether weapon that changes as you use it.
        if (path.startsWith("zanite_") && stack.isDamageable()) {
            int bonus = AetherCompat.zaniteBonus(dmg, stack.getDamage(), stack.getMaxDamage());
            lines.add(Text.literal(bonus > 0
                ? "§d • Worn: +" + bonus + " damage right now"
                : "§8 • Not worn enough to bite yet"));
        }
    }

    /**
     * What each registered weapon does beyond its stats. Keyed by registry path; a weapon
     * with nothing to add (shovels, hoes) returns no lines.
     */
    static String[] weaponLines(String path) {
        return switch (path) {
            case "skyroot_sword", "skyroot_axe" -> new String[]{
                "Bounty: a mob it kills drops its loot twice (not bosses)"};
            case "holystone_sword", "holystone_axe" -> new String[]{
                "10% chance per hit to knock an Ambrosium Shard loose"};
            case "zanite_sword", "zanite_axe" -> new String[]{
                "Hits harder the more worn it is, up to +150% just before it breaks"};
            case "gravitite_sword", "gravitite_axe" -> new String[]{
                "Launch: a grounded target crashes back down for +25% damage"};
            case "valkyrie_axe", "valkyrie_shovel", "valkyrie_hoe" -> new String[]{
                "Reach: strikes from " + AetherCompat.VALKYRIE_REACH + " tiles away"};
            case "valkyrie_lance" -> new String[]{
                "Reach: strikes from " + AetherCompat.VALKYRIE_REACH + " tiles away",
                "A lance does not sweep"};
            case "flaming_sword" -> new String[]{"Sets the target Burning for 3 turns"};
            case "lightning_sword" -> new String[]{
                "Every hit calls lightning for +25% damage",
                "Doubled against a Soaked target"};
            case "holy_sword" -> new String[]{"+50% damage to the undead"};
            case "vampire_blade" -> new String[]{"Heals you for 20% of the hit while you are hurt"};
            case "pig_slayer" -> new String[]{
                "Double damage to pigs, piglins and hoglins",
                "+50% instead against a boss"};
            case "candy_cane_sword" -> new String[]{"25% chance per hit to snap off a Candy Cane"};
            case "hammer_of_kingbdogz" -> new String[]{
                "Thrown shockwave: knocks the target back 1 tile",
                "Enemies beside it take half damage"};
            case "golden_dart_shooter" -> new String[]{"Fires Golden Darts, one per shot"};
            case "enchanted_dart_shooter" -> new String[]{"Fires Enchanted Darts, one per shot"};
            case "poison_dart_shooter" -> new String[]{
                "Fires Poison Darts, one per shot",
                "Poisons for 3 turns"};
            case "phoenix_bow" -> new String[]{
                "Uses arrows, like any bow",
                "Sets the target Burning for 3 turns"};
            case "cloud_staff" -> new String[]{
                "Cloud crystal: Weakens the target for 2 turns",
                "+" + AetherCompat.CLOUD_CRYSTAL_FIRE_BONUS + " damage to blazes and fire minions"};
            default -> new String[0];
        };
    }

    /**
     * A short Craftics block for an Aether accessory or consumable.
     *
     * @return true if the item has one, in which case the caller is done with this item
     */
    public static boolean appendGearLines(String path, List<Text> lines) {
        String[] gear = gearLines(path);
        if (gear.length == 0) return false;
        lines.add(Text.empty());
        lines.add(Text.literal("§6§lCraftics:"));
        for (String line : gear) {
            TooltipWrap.addWrapped(lines, " ", "§e• " + line);
        }
        return true;
    }

    /** Worn and used items. Cosmetic accessories, and anything with no effect here, return nothing. */
    static String[] gearLines(String path) {
        if (path.endsWith("_gloves")) {
            if (path.equals("zanite_gloves")) {
                return new String[]{"+1 Melee Power", "+2 once more than half worn"};
            }
            return new String[]{"+" + glovePower(path) + " Melee Power"};
        }
        return switch (path) {
            case "zanite_ring", "zanite_pendant" -> new String[]{"+1 Luck"};
            case "ice_ring", "ice_pendant" -> new String[]{
                "Burning on you lasts 1 turn less", "Stacks with the other ice charm"};
            case "agility_cape" -> new String[]{"+1 Speed"};
            case "invisibility_cloak" -> new String[]{"+2 Stealth Range"};
            case "valkyrie_cape", "golden_feather" -> new String[]{"Knockback moves you 1 tile less"};
            case "regeneration_stone" -> new String[]{"+1 Regen"};
            case "iron_bubble" -> new String[]{"+1 Water Power"};
            case "shield_of_repulsion" -> new String[]{
                "After a turn in which you did not move:",
                "50% chance to turn a ranged hit back on the shooter"};
            case "golden_dart", "enchanted_dart", "poison_dart" -> new String[]{
                "Ammo for the matching Dart Shooter"};
            case "ambrosium_shard" -> new String[]{
                "Use: restore " + AetherCompat.AMBROSIUM_HEAL + " HP (1 AP)"};
            case "healing_stone" -> new String[]{"Use: Regeneration for 3 turns (1 AP)"};
            case "white_apple", "skyroot_remedy_bucket" -> new String[]{"Use: cures Poison (1 AP)"};
            case "lightning_knife" -> new String[]{
                "Throw: " + AetherCompat.LIGHTNING_KNIFE_DAMAGE + " lightning damage (range "
                    + AetherCompat.THROWN_RANGE + ", 1 AP)",
                "Doubled against a Soaked target"};
            default -> new String[0];
        };
    }

    /** Mirrors {@code AetherScanner.gloveBonus} for gloves whose value does not change. */
    private static int glovePower(String path) {
        return switch (path) {
            case "diamond_gloves", "netherite_gloves", "gravitite_gloves",
                 "valkyrie_gloves", "phoenix_gloves", "obsidian_gloves" -> 2;
            default -> 1;
        };
    }

    /** The one armor piece whose effect is not a set bonus. Appended without ending the chain. */
    public static void appendArmorNote(String path, List<Text> lines) {
        if (path.equals("sentry_boots")) {
            lines.add(Text.literal("§6Sentry Boots: §7knockback moves you 1 tile less"));
        }
    }
}
