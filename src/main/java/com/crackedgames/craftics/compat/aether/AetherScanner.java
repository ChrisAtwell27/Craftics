package com.crackedgames.craftics.compat.aether;

import com.crackedgames.craftics.api.EquipmentScanner;
import com.crackedgames.craftics.api.StatModifiers;
import com.crackedgames.craftics.combat.CombatEffects;
import com.crackedgames.craftics.combat.TrimEffects.Bonus;
import com.crackedgames.craftics.compat.artifacts.AccessoriesReflect;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Reads what a player is wearing from the Aether and turns it into combat modifiers.
 *
 * <p>Two sources. Armor is the vanilla armor slots: every full set brings a conditional
 * effect of its own that {@code ArmorSetEntry} has no field for, and Gravitite brings part of
 * its one with each piece. Everything else -
 * gloves, rings, pendants, capes, the shield - sits in Accessories slots, which is the API
 * the Aether is built on, read through {@link AccessoriesReflect}.
 *
 * <p>The Aether asks for matching gloves before a set's ability switches on. That rule is not
 * carried over: armor class and the set bonus here already come from the four armor pieces
 * alone, and a set that is "complete" for one purpose and not for another would be a puzzle
 * rather than a rule.
 */
final class AetherScanner implements EquipmentScanner {

    /** Gloves the Aether rates at 0.75 punch damage or better. */
    private static final Set<String> STRONG_GLOVES = Set.of(
        "diamond_gloves", "netherite_gloves", "gravitite_gloves",
        "valkyrie_gloves", "phoenix_gloves", "obsidian_gloves");

    @Override
    public StatModifiers scan(ServerPlayerEntity player) {
        StatModifiers mods = new StatModifiers();
        if (!AetherCompat.isLoaded() || player == null) return mods;

        applyArmor(mods, player);

        // Count first, apply once per item kind: two rings are two bonuses, but they are one
        // effect with twice the strength, not two handlers each announcing itself.
        Map<String, Integer> worn = new HashMap<>();
        boolean[] zaniteGlovesWorn = {false};
        AccessoriesReflect.forEachEquipped(player, (ItemStack stack) -> {
            String path = AetherCompat.pathOf(stack.getItem());
            if (path == null) return;
            worn.merge(path, 1, Integer::sum);
            if (path.equals("zanite_gloves") && stack.isDamageable()
                    && stack.getDamage() * 2 > stack.getMaxDamage()) {
                zaniteGlovesWorn[0] = true;
            }
        });
        applyAccessories(mods, worn, zaniteGlovesWorn[0]);
        return mods;
    }

    private static void applyArmor(StatModifiers mods, ServerPlayerEntity player) {
        // Gravitite works piece by piece: each one worn adds to the chance.
        int gravitite = piecesOf(player, "gravitite");
        if (gravitite > 0) {
            mods.addCombatEffect("Gravitite Updraft", new AetherEffects.Updraft(gravitite));
        }
        String set = fullSet(player);
        if (set != null) {
            switch (set) {
                case "zanite" -> mods.addCombatEffect("Hardened Zanite", new AetherEffects.Hardened());
                case "obsidian" -> mods.addCombatEffect("Tempered Obsidian", new AetherEffects.Tempered());
                case "valkyrie" -> mods.addCombatEffect("Valkyrie Wings", new AetherEffects.Winged());
                case "neptune" -> mods.addCombatEffect("Neptune's Favor", new AetherEffects.Ward(
                    CombatEffects.EffectType.SOAKED, "§3✦ Neptune armor sheds the water."));
                case "phoenix" -> mods.addCombatEffect("Phoenix Plumage", new AetherEffects.Ward(
                    CombatEffects.EffectType.BURNING, "§6✦ Phoenix armor drinks the flame."));
                default -> { }
            }
        }
        if ("sentry_boots".equals(AetherCompat.pathOf(player.getEquippedStack(EquipmentSlot.FEET).getItem()))) {
            mods.addCombatEffect("Sentry Boots", new AetherEffects.Braced(1, "Sentry Boots"));
        }
        // Always attached: the weapon half is decided per hit, by whatever is in hand then.
        mods.addCombatEffect("Out of Depth", new AetherEffects.Outsider(outsiderArmorPieces(player)));
    }

    /** Worn armor pieces that are not from the Aether. An empty slot is not a penalty. */
    private static int outsiderArmorPieces(ServerPlayerEntity player) {
        int pieces = 0;
        for (EquipmentSlot slot : new EquipmentSlot[]{
                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack worn = player.getEquippedStack(slot);
            if (!worn.isEmpty() && !AetherCompat.isAetherItem(worn.getItem())) pieces++;
        }
        return pieces;
    }

    /** How many of the four armor slots hold a piece of {@code material}. */
    private static int piecesOf(ServerPlayerEntity player, String material) {
        int pieces = 0;
        for (EquipmentSlot slot : new EquipmentSlot[]{
                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            if (material.equals(materialOf(AetherCompat.pathOf(player.getEquippedStack(slot).getItem())))) {
                pieces++;
            }
        }
        return pieces;
    }

    /** The material all four armor slots share ("valkyrie"), or null when they do not. */
    static String fullSet(ServerPlayerEntity player) {
        String material = null;
        for (EquipmentSlot slot : new EquipmentSlot[]{
                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            String m = materialOf(AetherCompat.pathOf(player.getEquippedStack(slot).getItem()));
            if (m == null) return null;
            if (material == null) material = m;
            else if (!material.equals(m)) return null;
        }
        return material;
    }

    /** {@code "valkyrie_chestplate"} to {@code "valkyrie"}; null for anything that is not an armor piece. */
    static String materialOf(String path) {
        if (path == null) return null;
        for (String suffix : new String[]{"_helmet", "_chestplate", "_leggings", "_boots"}) {
            if (path.endsWith(suffix)) return path.substring(0, path.length() - suffix.length());
        }
        return null;
    }

    /**
     * Physical affinity a pair of gloves is worth: 2 for the Aether's strong gloves, 1 for the
     * rest.
     *
     * <p>Physical, not Melee Power. Gloves in the Aether make a bare hand hit harder, and a
     * bare hand is what Physical is. As Melee Power they raised every sword, axe and hammer
     * the wearer picked up as well.
     */
    static int gloveBonus(String path, boolean wornZanite) {
        if (path == null || !path.endsWith("_gloves")) return 0;
        if (STRONG_GLOVES.contains(path)) return 2;
        // Zanite gloves hit harder as they wear, like everything else made of it.
        if (path.equals("zanite_gloves") && wornZanite) return 2;
        return 1;
    }

    /** What a dyed cape keeps off its wearer, or null for anything that is not one. */
    static AetherEffects.Shortened cape(String path) {
        if (path == null) return null;
        return switch (path) {
            case "red_cape" -> new AetherEffects.Shortened("Red Cape", "Weakness",
                CombatEffects.EffectType.WEAKNESS);
            case "blue_cape" -> new AetherEffects.Shortened("Blue Cape", "Levitation",
                CombatEffects.EffectType.LEVITATION);
            case "white_cape" -> new AetherEffects.Shortened("White Cape", "Poison",
                CombatEffects.EffectType.POISON);
            case "yellow_cape" -> new AetherEffects.Shortened("Yellow Cape", "burn",
                CombatEffects.EffectType.BURNING, CombatEffects.EffectType.SOUL_BURNING);
            default -> null;
        };
    }

    private static String capeName(String path) {
        return Character.toUpperCase(path.charAt(0)) + path.substring(1, path.indexOf('_')) + " Cape";
    }

    static void applyAccessories(StatModifiers mods, Map<String, Integer> worn, boolean wornZanite) {
        int chill = 0;
        int drift = 0;
        for (Map.Entry<String, Integer> e : worn.entrySet()) {
            String path = e.getKey();
            int count = e.getValue();
            int gloves = gloveBonus(path, wornZanite);
            if (gloves > 0) {
                mods.add(Bonus.PHYSICAL_POWER, gloves);
                continue;
            }
            switch (path) {
                // Zanite speeds mining in the Aether. There is nothing to mine here, so the
                // gemstone's luck is what carries over.
                case "zanite_ring", "zanite_pendant" -> mods.add(Bonus.LUCK, count);
                case "ice_ring", "ice_pendant" -> chill += count;
                case "agility_cape" -> mods.add(Bonus.SPEED, 1);
                case "invisibility_cloak" -> mods.add(Bonus.STEALTH_RANGE, 2);
                case "valkyrie_cape", "golden_feather" -> drift += 1;
                case "regeneration_stone" -> mods.add(Bonus.REGEN, 1);
                case "iron_bubble" -> mods.add(Bonus.WATER_POWER, 1);
                case "shield_of_repulsion" ->
                    mods.addCombatEffect("Shield of Repulsion", new AetherEffects.Repulsion());
                // Plain jewellery and plain capes. The Aether gives these nothing to do, and
                // they turn up in every loot table here, so each has a small thing of its own:
                // iron is sturdy, gold bites and takes a charm, and a cape keeps one kind of
                // trouble off you a turn sooner.
                case "iron_ring" -> mods.add(Bonus.MAX_HP, count);
                case "iron_pendant" -> mods.add(Bonus.DEFENSE, count);
                case "golden_ring" -> mods.add(Bonus.ARMOR_PEN, count);
                case "golden_pendant" -> mods.add(Bonus.SPECIAL_POWER, count);
                case "red_cape", "blue_cape", "white_cape", "yellow_cape" ->
                    mods.addCombatEffect(capeName(path), cape(path));
                // The Swet Cape is read where it matters, by the swets: AetherMobs.swetPrey.
                default -> { }
            }
        }
        if (chill > 0) mods.addCombatEffect("Ice Charm", new AetherEffects.Chill(chill));
        if (drift > 0) mods.addCombatEffect("Feather Fall", new AetherEffects.Braced(drift, "Your slow fall"));
    }
}
