package com.crackedgames.craftics.combat;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;

public class PlayerCombatStats {

    public static int getAttackPower(ServerPlayerEntity player) {
        Item weapon = player.getMainHandStack().getItem();
        // Swords
        if (weapon == Items.WOODEN_SWORD) return com.crackedgames.craftics.CrafticsMod.CONFIG.dmgWoodenSword();
        if (weapon == Items.STONE_SWORD) return com.crackedgames.craftics.CrafticsMod.CONFIG.dmgStoneSword();
        if (weapon == Items.IRON_SWORD) return com.crackedgames.craftics.CrafticsMod.CONFIG.dmgIronSword();
        if (weapon == Items.GOLDEN_SWORD) return com.crackedgames.craftics.CrafticsMod.CONFIG.dmgGoldenSword();
        if (weapon == Items.DIAMOND_SWORD) return com.crackedgames.craftics.CrafticsMod.CONFIG.dmgDiamondSword();
        if (weapon == Items.NETHERITE_SWORD) return com.crackedgames.craftics.CrafticsMod.CONFIG.dmgNetheriteSword();
        // Axes
        if (weapon == Items.WOODEN_AXE) return com.crackedgames.craftics.CrafticsMod.CONFIG.dmgWoodenAxe();
        if (weapon == Items.STONE_AXE) return com.crackedgames.craftics.CrafticsMod.CONFIG.dmgStoneAxe();
        if (weapon == Items.IRON_AXE) return com.crackedgames.craftics.CrafticsMod.CONFIG.dmgIronAxe();
        if (weapon == Items.GOLDEN_AXE) return com.crackedgames.craftics.CrafticsMod.CONFIG.dmgGoldenAxe();
        if (weapon == Items.DIAMOND_AXE) return com.crackedgames.craftics.CrafticsMod.CONFIG.dmgDiamondAxe();
        if (weapon == Items.NETHERITE_AXE) return com.crackedgames.craftics.CrafticsMod.CONFIG.dmgNetheriteAxe();
        // Heavy / special
        if (weapon == Items.MACE) return com.crackedgames.craftics.CrafticsMod.CONFIG.dmgMace();
        if (weapon == Items.TRIDENT) return com.crackedgames.craftics.CrafticsMod.CONFIG.dmgTrident();
        // Ranged
        if (weapon == Items.BOW) return com.crackedgames.craftics.CrafticsMod.CONFIG.dmgBow();
        if (weapon == Items.CROSSBOW) return com.crackedgames.craftics.CrafticsMod.CONFIG.dmgCrossbow();
        // Blunt rods
        if (weapon == Items.STICK) return com.crackedgames.craftics.CrafticsMod.CONFIG.dmgStick();
        if (weapon == Items.BAMBOO) return com.crackedgames.craftics.CrafticsMod.CONFIG.dmgBamboo();
        if (weapon == Items.BLAZE_ROD) return com.crackedgames.craftics.CrafticsMod.CONFIG.dmgBlazeRod();
        if (weapon == Items.BREEZE_ROD) return com.crackedgames.craftics.CrafticsMod.CONFIG.dmgBreezeRod();
        // Coral weapons
        if (weapon == Items.TUBE_CORAL) return com.crackedgames.craftics.CrafticsMod.CONFIG.dmgCoralTube();
        if (weapon == Items.BRAIN_CORAL) return com.crackedgames.craftics.CrafticsMod.CONFIG.dmgCoralBrain();
        if (weapon == Items.BUBBLE_CORAL) return com.crackedgames.craftics.CrafticsMod.CONFIG.dmgCoralBubble();
        if (weapon == Items.FIRE_CORAL) return com.crackedgames.craftics.CrafticsMod.CONFIG.dmgCoralFire();
        if (weapon == Items.HORN_CORAL) return com.crackedgames.craftics.CrafticsMod.CONFIG.dmgCoralHorn();
        // Dead corals
        if (weapon == Items.DEAD_TUBE_CORAL || weapon == Items.DEAD_BRAIN_CORAL
            || weapon == Items.DEAD_BUBBLE_CORAL || weapon == Items.DEAD_FIRE_CORAL
            || weapon == Items.DEAD_HORN_CORAL) return com.crackedgames.craftics.CrafticsMod.CONFIG.dmgCoralDead();
        // Coral fans
        if (weapon == Items.TUBE_CORAL_FAN || weapon == Items.BRAIN_CORAL_FAN
            || weapon == Items.BUBBLE_CORAL_FAN || weapon == Items.FIRE_CORAL_FAN
            || weapon == Items.HORN_CORAL_FAN) return com.crackedgames.craftics.CrafticsMod.CONFIG.dmgCoralFan();
        // Dead coral fans (same as dead corals)
        if (weapon == Items.DEAD_TUBE_CORAL_FAN || weapon == Items.DEAD_BRAIN_CORAL_FAN
            || weapon == Items.DEAD_BUBBLE_CORAL_FAN || weapon == Items.DEAD_FIRE_CORAL_FAN
            || weapon == Items.DEAD_HORN_CORAL_FAN) return com.crackedgames.craftics.CrafticsMod.CONFIG.dmgCoralDead();
        return com.crackedgames.craftics.CrafticsMod.CONFIG.dmgFist();
    }

    public static int getDefense(ServerPlayerEntity player) {
        return player.getArmor(); // vanilla armor value (int)
    }

    /** Crossbow uses special rook pattern — return -1 to signal unlimited cardinal range. */
    public static final int RANGE_CROSSBOW_ROOK = -1;

    public static int getWeaponRange(ServerPlayerEntity player) {
        Item weapon = player.getMainHandStack().getItem();
        if (weapon == Items.BOW && hasArrows(player)) return 3;
        if (weapon == Items.CROSSBOW && hasArrows(player)) return RANGE_CROSSBOW_ROOK;
        if (weapon == Items.TRIDENT) return 3;
        return 1; // melee
    }

    /** Check if target is reachable by crossbow rook pattern (same row or column, clear line). */
    public static boolean isInCrossbowLine(com.crackedgames.craftics.core.GridArena arena,
            com.crackedgames.craftics.core.GridPos from, com.crackedgames.craftics.core.GridPos to) {
        if (from.x() != to.x() && from.z() != to.z()) return false; // not cardinal
        if (from.equals(to)) return false;

        int dx = Integer.signum(to.x() - from.x());
        int dz = Integer.signum(to.z() - from.z());
        int cx = from.x() + dx;
        int cz = from.z() + dz;

        // Walk along the line checking for obstacles
        while (cx != to.x() || cz != to.z()) {
            var tile = arena.getTile(new com.crackedgames.craftics.core.GridPos(cx, cz));
            if (tile == null || !tile.isWalkable()) return false; // blocked by wall/obstacle
            cx += dx;
            cz += dz;
        }
        return true;
    }

    public static boolean hasShield(ServerPlayerEntity player) {
        return player.getEquippedStack(EquipmentSlot.OFFHAND).getItem() == Items.SHIELD;
    }

    public static boolean isBow(ServerPlayerEntity player) {
        return player.getMainHandStack().getItem() == Items.BOW && hasArrows(player);
    }

    public static boolean hasArrows(ServerPlayerEntity player) {
        for (int i = 0; i < player.getInventory().size(); i++) {
            Item item = player.getInventory().getStack(i).getItem();
            if (item == Items.ARROW || item == Items.TIPPED_ARROW) return true;
        }
        return false;
    }

    public static void consumeArrow(ServerPlayerEntity player) {
        for (int i = 0; i < player.getInventory().size(); i++) {
            var stack = player.getInventory().getStack(i);
            if (stack.getItem() == Items.ARROW) {
                stack.decrement(1);
                return;
            }
        }
    }

    /** Detect which armor set the player is wearing. Returns the set name or "mixed". */
    public static String getArmorSet(ServerPlayerEntity player) {
        Item head = player.getEquippedStack(EquipmentSlot.HEAD).getItem();
        Item chest = player.getEquippedStack(EquipmentSlot.CHEST).getItem();
        Item legs = player.getEquippedStack(EquipmentSlot.LEGS).getItem();
        Item feet = player.getEquippedStack(EquipmentSlot.FEET).getItem();

        // Turtle helmet is special — it's a "set" with any armor type
        if (head == Items.TURTLE_HELMET) return "turtle";

        // Check full sets
        if (head == Items.LEATHER_HELMET && chest == Items.LEATHER_CHESTPLATE
            && legs == Items.LEATHER_LEGGINGS && feet == Items.LEATHER_BOOTS) return "leather";
        if (head == Items.CHAINMAIL_HELMET && chest == Items.CHAINMAIL_CHESTPLATE
            && legs == Items.CHAINMAIL_LEGGINGS && feet == Items.CHAINMAIL_BOOTS) return "chainmail";
        if (head == Items.IRON_HELMET && chest == Items.IRON_CHESTPLATE
            && legs == Items.IRON_LEGGINGS && feet == Items.IRON_BOOTS) return "iron";
        if (head == Items.GOLDEN_HELMET && chest == Items.GOLDEN_CHESTPLATE
            && legs == Items.GOLDEN_LEGGINGS && feet == Items.GOLDEN_BOOTS) return "gold";
        if (head == Items.DIAMOND_HELMET && chest == Items.DIAMOND_CHESTPLATE
            && legs == Items.DIAMOND_LEGGINGS && feet == Items.DIAMOND_BOOTS) return "diamond";
        if (head == Items.NETHERITE_HELMET && chest == Items.NETHERITE_CHESTPLATE
            && legs == Items.NETHERITE_LEGGINGS && feet == Items.NETHERITE_BOOTS) return "netherite";

        return "mixed";
    }

    /** Get set bonus description for display. */
    public static String getSetBonusDescription(String armorSet) {
        return switch (armorSet) {
            case "leather" -> "\u00a7eBrawler: +2 Speed, +1 AP, +2 Fist dmg, 2x kill streak multiplier";
            case "chainmail" -> "\u00a77Rogue: +1 Speed, attacks cost -1 AP (min 1)";
            case "iron" -> "\u00a7fGuard: +2 Defense, immune to knockback";
            case "gold" -> "\u00a76Gambler: +3 Luck crit chance, +1 emerald per kill";
            case "diamond" -> "\u00a7bKnight: +3 Defense, +1 Attack";
            case "netherite" -> "\u00a74Juggernaut: +4 Defense, +2 Attack, immune to fire damage";
            case "turtle" -> "\u00a72Aquatic: Water tiles are walkable, +1 HP regen per turn, +1 Range when on water";
            default -> "";
        };
    }

    /** Speed bonus from armor set. */
    public static int getSetSpeedBonus(ServerPlayerEntity player) {
        String set = getArmorSet(player);
        return switch (set) {
            case "leather" -> 2;
            case "chainmail" -> 1;
            default -> 0;
        };
    }

    /** AP bonus from armor set. */
    public static int getSetApBonus(ServerPlayerEntity player) {
        String set = getArmorSet(player);
        return switch (set) {
            case "leather" -> 1;
            default -> 0;
        };
    }

    /** Defense bonus from armor set (on top of vanilla armor value). */
    public static int getSetDefenseBonus(ServerPlayerEntity player) {
        String set = getArmorSet(player);
        return switch (set) {
            case "iron" -> 2;
            case "diamond" -> 3;
            case "netherite" -> 4;
            default -> 0;
        };
    }

    /** Attack bonus from armor set. */
    public static int getSetAttackBonus(ServerPlayerEntity player) {
        String set = getArmorSet(player);
        return switch (set) {
            case "diamond" -> 1;
            case "netherite" -> 2;
            default -> 0;
        };
    }

    /** AP cost reduction from chainmail set (reduces attack AP cost). */
    public static int getSetApCostReduction(ServerPlayerEntity player) {
        return "chainmail".equals(getArmorSet(player)) ? 1 : 0;
    }

    /** Check if player has turtle set (water walking + regen). */
    public static boolean hasTurtleSet(ServerPlayerEntity player) {
        return "turtle".equals(getArmorSet(player));
    }

    /** Check if player has netherite set (fire immunity). */
    public static boolean hasNetheriteSet(ServerPlayerEntity player) {
        return "netherite".equals(getArmorSet(player));
    }

    /** Check if player has gold set (luck bonus). */
    public static boolean hasGoldSet(ServerPlayerEntity player) {
        return "gold".equals(getArmorSet(player));
    }

    /** Get tipped arrow effect from inventory. Returns effect name or null. */
    public static String findAndConsumeTippedArrow(ServerPlayerEntity player) {
        for (int i = 0; i < player.getInventory().size(); i++) {
            var stack = player.getInventory().getStack(i);
            if (stack.getItem() == Items.TIPPED_ARROW) {
                var potionContents = stack.get(net.minecraft.component.DataComponentTypes.POTION_CONTENTS);
                if (potionContents != null) {
                    for (var effect : potionContents.getEffects()) {
                        var effectType = effect.getEffectType().value();
                        stack.decrement(1);
                        if (effectType == net.minecraft.entity.effect.StatusEffects.POISON.value()) return "poison";
                        if (effectType == net.minecraft.entity.effect.StatusEffects.SLOWNESS.value()) return "slowness";
                        if (effectType == net.minecraft.entity.effect.StatusEffects.WEAKNESS.value()) return "weakness";
                        if (effectType == net.minecraft.entity.effect.StatusEffects.INSTANT_DAMAGE.value()) return "harming";
                        if (effectType == net.minecraft.entity.effect.StatusEffects.INSTANT_HEALTH.value()) return "healing";
                        if (effectType == net.minecraft.entity.effect.StatusEffects.FIRE_RESISTANCE.value()) return "fire_resistance";
                        return "unknown";
                    }
                }
                // Regular tipped arrow with no recognized effect — consume as normal arrow
                stack.decrement(1);
                return null;
            }
        }
        return null;
    }

    /** Check for tipped arrows before regular arrows. */
    public static boolean hasTippedArrows(ServerPlayerEntity player) {
        for (int i = 0; i < player.getInventory().size(); i++) {
            if (player.getInventory().getStack(i).getItem() == Items.TIPPED_ARROW) return true;
        }
        return false;
    }

    // ─── Enchantment support ────────────────────────────────────────────

    /**
     * Get the level of an enchantment on a stack by checking its enchantment data component.
     * Uses string matching on registry ID for maximum compatibility with 1.21.1.
     */
    public static int getEnchantLevel(ItemStack stack, String enchantId) {
        ItemEnchantmentsComponent enchants = stack.getOrDefault(
            DataComponentTypes.ENCHANTMENTS, ItemEnchantmentsComponent.DEFAULT);
        for (RegistryEntry<Enchantment> entry : enchants.getEnchantments()) {
            var key = entry.getKey();
            if (key.isPresent() && key.get().getValue().toString().equals(enchantId)) {
                return enchants.getLevel(entry);
            }
        }
        return 0;
    }

    // ─── Weapon enchantment bonuses ─────────────────────────────────────

    /** Get bonus attack damage from weapon enchantments (Sharpness, Smite, Bane of Arthropods). */
    public static int getWeaponEnchantBonus(ServerPlayerEntity player) {
        ItemStack weapon = player.getMainHandStack();
        int bonus = 0;
        // Sharpness: +1 damage per level
        bonus += getEnchantLevel(weapon, "minecraft:sharpness");
        // Smite: +2 damage per level (vs undead — applied generically in tactical combat)
        bonus += getEnchantLevel(weapon, "minecraft:smite") * 2;
        // Bane of Arthropods: +2 per level
        bonus += getEnchantLevel(weapon, "minecraft:bane_of_arthropods") * 2;
        return bonus;
    }

    /** Check if weapon has Fire Aspect. Returns level (0 = none). */
    public static int getFireAspect(ServerPlayerEntity player) {
        return getEnchantLevel(player.getMainHandStack(), "minecraft:fire_aspect");
    }

    /** Check if weapon has Knockback. Returns level (0 = none). */
    public static int getKnockback(ServerPlayerEntity player) {
        return getEnchantLevel(player.getMainHandStack(), "minecraft:knockback");
    }

    /** Check if weapon has Looting. Returns level (0 = none). */
    public static int getLooting(ServerPlayerEntity player) {
        return getEnchantLevel(player.getMainHandStack(), "minecraft:looting");
    }

    /** Check if weapon has Sweeping Edge. Returns level (0 = none). */
    public static int getSweepingEdge(ServerPlayerEntity player) {
        return getEnchantLevel(player.getMainHandStack(), "minecraft:sweeping_edge");
    }

    // ─── Armor enchantment bonuses ──────────────────────────────────────

    /** Get total Protection enchantment level across all armor pieces, converted to defense. */
    public static int getTotalProtection(ServerPlayerEntity player) {
        int total = 0;
        for (EquipmentSlot slot : new EquipmentSlot[]{
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack piece = player.getEquippedStack(slot);
            total += getEnchantLevel(piece, "minecraft:protection");
            total += getEnchantLevel(piece, "minecraft:blast_protection");
            total += getEnchantLevel(piece, "minecraft:projectile_protection");
        }
        // Each protection level = roughly +0.5 defense. Every 2 levels = +1 defense.
        return total / 2;
    }

    /** Check if any armor has Thorns. Returns highest level across all pieces. */
    public static int getThorns(ServerPlayerEntity player) {
        int max = 0;
        for (EquipmentSlot slot : new EquipmentSlot[]{
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            max = Math.max(max, getEnchantLevel(player.getEquippedStack(slot), "minecraft:thorns"));
        }
        return max;
    }

    /** Check if boots have Feather Falling. */
    public static boolean hasFeatherFalling(ServerPlayerEntity player) {
        return getEnchantLevel(player.getEquippedStack(EquipmentSlot.FEET), "minecraft:feather_falling") > 0;
    }

    // ─── Bow enchantment bonuses ────────────────────────────────────────

    /** Get Power level on bow (bonus damage). */
    public static int getBowPower(ServerPlayerEntity player) {
        return getEnchantLevel(player.getMainHandStack(), "minecraft:power");
    }

    /** Check for Flame enchantment on bow. */
    public static boolean hasBowFlame(ServerPlayerEntity player) {
        return getEnchantLevel(player.getMainHandStack(), "minecraft:flame") > 0;
    }

    /** Check for Infinity on bow (don't consume arrows). */
    public static boolean hasInfinity(ServerPlayerEntity player) {
        return getEnchantLevel(player.getMainHandStack(), "minecraft:infinity") > 0;
    }

    /** Get Punch level (knockback on arrows). */
    public static int getBowPunch(ServerPlayerEntity player) {
        return getEnchantLevel(player.getMainHandStack(), "minecraft:punch");
    }

    // ─── Mace enchantments ──────────────────────────────────────────────

    /** Density: bonus damage per level to mace AoE shockwave. */
    public static int getDensity(ServerPlayerEntity player) {
        return getEnchantLevel(player.getMainHandStack(), "minecraft:density");
    }

    /** Breach: armor penetration. Each level ignores 2 defense points on the target. */
    public static int getBreach(ServerPlayerEntity player) {
        return getEnchantLevel(player.getMainHandStack(), "minecraft:breach");
    }

    /** Wind Burst: increased knockback range (+1 tile per level). */
    public static int getWindBurst(ServerPlayerEntity player) {
        return getEnchantLevel(player.getMainHandStack(), "minecraft:wind_burst");
    }

    // ─── Tool enchantments ──────────────────────────────────────────────

    /** Check Unbreaking level on held item. Higher = less durability consumption. */
    public static int getUnbreaking(ServerPlayerEntity player) {
        return getEnchantLevel(player.getMainHandStack(), "minecraft:unbreaking");
    }

    /** Check Efficiency level on held item (faster pickaxe obstacle breaking). */
    public static int getEfficiency(ServerPlayerEntity player) {
        return getEnchantLevel(player.getMainHandStack(), "minecraft:efficiency");
    }
}
