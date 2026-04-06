package com.crackedgames.craftics.combat;

import com.crackedgames.craftics.core.GridPos;
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
        if (weapon == Items.WOODEN_SWORD) return com.crackedgames.craftics.CrafticsMod.CONFIG.dmgWoodenSword();
        if (weapon == Items.STONE_SWORD) return com.crackedgames.craftics.CrafticsMod.CONFIG.dmgStoneSword();
        if (weapon == Items.IRON_SWORD) return com.crackedgames.craftics.CrafticsMod.CONFIG.dmgIronSword();
        if (weapon == Items.GOLDEN_SWORD) return com.crackedgames.craftics.CrafticsMod.CONFIG.dmgGoldenSword();
        if (weapon == Items.DIAMOND_SWORD) return com.crackedgames.craftics.CrafticsMod.CONFIG.dmgDiamondSword();
        if (weapon == Items.NETHERITE_SWORD) return com.crackedgames.craftics.CrafticsMod.CONFIG.dmgNetheriteSword();
        if (weapon == Items.WOODEN_AXE) return com.crackedgames.craftics.CrafticsMod.CONFIG.dmgWoodenAxe();
        if (weapon == Items.STONE_AXE) return com.crackedgames.craftics.CrafticsMod.CONFIG.dmgStoneAxe();
        if (weapon == Items.IRON_AXE) return com.crackedgames.craftics.CrafticsMod.CONFIG.dmgIronAxe();
        if (weapon == Items.GOLDEN_AXE) return com.crackedgames.craftics.CrafticsMod.CONFIG.dmgGoldenAxe();
        if (weapon == Items.DIAMOND_AXE) return com.crackedgames.craftics.CrafticsMod.CONFIG.dmgDiamondAxe();
        if (weapon == Items.NETHERITE_AXE) return com.crackedgames.craftics.CrafticsMod.CONFIG.dmgNetheriteAxe();
        if (weapon == Items.MACE) return com.crackedgames.craftics.CrafticsMod.CONFIG.dmgMace();
        if (weapon == Items.TRIDENT) return com.crackedgames.craftics.CrafticsMod.CONFIG.dmgTrident();
        if (weapon == Items.BOW) return com.crackedgames.craftics.CrafticsMod.CONFIG.dmgBow();
        if (weapon == Items.CROSSBOW) return com.crackedgames.craftics.CrafticsMod.CONFIG.dmgCrossbow();
        if (weapon == Items.STICK) return com.crackedgames.craftics.CrafticsMod.CONFIG.dmgStick();
        if (weapon == Items.BAMBOO) return com.crackedgames.craftics.CrafticsMod.CONFIG.dmgBamboo();
        if (weapon == Items.BLAZE_ROD) return com.crackedgames.craftics.CrafticsMod.CONFIG.dmgBlazeRod();
        if (weapon == Items.BREEZE_ROD) return com.crackedgames.craftics.CrafticsMod.CONFIG.dmgBreezeRod();
        if (weapon == Items.TUBE_CORAL) return com.crackedgames.craftics.CrafticsMod.CONFIG.dmgCoralTube();
        if (weapon == Items.BRAIN_CORAL) return com.crackedgames.craftics.CrafticsMod.CONFIG.dmgCoralBrain();
        if (weapon == Items.BUBBLE_CORAL) return com.crackedgames.craftics.CrafticsMod.CONFIG.dmgCoralBubble();
        if (weapon == Items.FIRE_CORAL) return com.crackedgames.craftics.CrafticsMod.CONFIG.dmgCoralFire();
        if (weapon == Items.HORN_CORAL) return com.crackedgames.craftics.CrafticsMod.CONFIG.dmgCoralHorn();
        if (weapon == Items.DEAD_TUBE_CORAL || weapon == Items.DEAD_BRAIN_CORAL
            || weapon == Items.DEAD_BUBBLE_CORAL || weapon == Items.DEAD_FIRE_CORAL
            || weapon == Items.DEAD_HORN_CORAL) return com.crackedgames.craftics.CrafticsMod.CONFIG.dmgCoralDead();
        if (weapon == Items.TUBE_CORAL_FAN || weapon == Items.BRAIN_CORAL_FAN
            || weapon == Items.BUBBLE_CORAL_FAN || weapon == Items.FIRE_CORAL_FAN
            || weapon == Items.HORN_CORAL_FAN) return com.crackedgames.craftics.CrafticsMod.CONFIG.dmgCoralFan();
        if (weapon == Items.DEAD_TUBE_CORAL_FAN || weapon == Items.DEAD_BRAIN_CORAL_FAN
            || weapon == Items.DEAD_BUBBLE_CORAL_FAN || weapon == Items.DEAD_FIRE_CORAL_FAN
            || weapon == Items.DEAD_HORN_CORAL_FAN) return com.crackedgames.craftics.CrafticsMod.CONFIG.dmgCoralDead();
        if (weapon == Items.WOODEN_HOE) return 1;
        if (weapon == Items.STONE_HOE) return 1;
        if (weapon == Items.IRON_HOE) return 2;
        if (weapon == Items.GOLDEN_HOE) return 2;
        if (weapon == Items.DIAMOND_HOE) return 3;
        if (weapon == Items.NETHERITE_HOE) return 3;
        if (weapon == Items.WOODEN_SHOVEL) return 2;
        if (weapon == Items.STONE_SHOVEL) return 3;
        if (weapon == Items.IRON_SHOVEL) return 4;
        if (weapon == Items.GOLDEN_SHOVEL) return 3;
        if (weapon == Items.DIAMOND_SHOVEL) return 5;
        if (weapon == Items.NETHERITE_SHOVEL) return 6;
        return com.crackedgames.craftics.CrafticsMod.CONFIG.dmgFist();
    }

    public static int getDefense(ServerPlayerEntity player) {
        return player.getArmor();
    }

    /** Crossbow uses special rook pattern — return -1 to signal unlimited cardinal range. */
    public static final int RANGE_CROSSBOW_ROOK = -1;

    /** Max throw range for trident (cardinal/diagonal line). */
    public static final int TRIDENT_THROW_RANGE = 5;

    public static int getWeaponRange(ServerPlayerEntity player) {
        Item weapon = player.getMainHandStack().getItem();
        if (weapon == Items.BOW && hasArrows(player)) return 3 + getBowPowerRange(player);
        if (weapon == Items.CROSSBOW && hasArrows(player)) return RANGE_CROSSBOW_ROOK;
        if (weapon == Items.TRIDENT) return TRIDENT_THROW_RANGE;
        return 1;
    }

    /** Check if a target is on a valid straight/diagonal line from the player. */
    public static boolean isInTridentLine(GridPos from, GridPos to) {
        int dx = to.x() - from.x();
        int dz = to.z() - from.z();
        if (dx == 0 && dz == 0) return false;
        // Cardinal: same row or column
        if (dx == 0 || dz == 0) return true;
        // Diagonal: |dx| == |dz|
        return Math.abs(dx) == Math.abs(dz);
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

        // Walk along the line checking for obstacles and blocking entities
        while (cx != to.x() || cz != to.z()) {
            var intermediatePos = new com.crackedgames.craftics.core.GridPos(cx, cz);
            var tile = arena.getTile(intermediatePos);
            if (tile == null || !tile.isWalkable()) return false; // blocked by wall/obstacle
            if (arena.isEnemyOccupied(intermediatePos)) return false; // blocked by entity in the way
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

    public static int getSetSpeedBonus(ServerPlayerEntity player) {
        String set = getArmorSet(player);
        return switch (set) {
            case "leather" -> 2;
            case "chainmail" -> 1;
            default -> 0;
        };
    }

    public static int getSetApBonus(ServerPlayerEntity player) {
        String set = getArmorSet(player);
        return switch (set) {
            case "leather" -> 1;
            default -> 0;
        };
    }

    public static int getSetDefenseBonus(ServerPlayerEntity player) {
        String set = getArmorSet(player);
        return switch (set) {
            case "iron" -> 2;
            case "diamond" -> 3;
            case "netherite" -> 4;
            default -> 0;
        };
    }

    public static int getSetAttackBonus(ServerPlayerEntity player) {
        String set = getArmorSet(player);
        return switch (set) {
            case "diamond" -> 1;
            case "netherite" -> 2;
            default -> 0;
        };
    }

    public static int getSetApCostReduction(ServerPlayerEntity player) {
        return "chainmail".equals(getArmorSet(player)) ? 1 : 0;
    }

    public static boolean hasTurtleSet(ServerPlayerEntity player) {
        return "turtle".equals(getArmorSet(player));
    }

    public static boolean hasNetheriteSet(ServerPlayerEntity player) {
        return "netherite".equals(getArmorSet(player));
    }

    public static boolean hasGoldSet(ServerPlayerEntity player) {
        return "gold".equals(getArmorSet(player));
    }

    public static String findAndConsumeTippedArrow(ServerPlayerEntity player) {
        for (int i = 0; i < player.getInventory().size(); i++) {
            var stack = player.getInventory().getStack(i);
            if (stack.getItem() == Items.TIPPED_ARROW) {
                var potionContents = stack.get(net.minecraft.component.DataComponentTypes.POTION_CONTENTS);
                if (potionContents != null) {
                    for (var effect : potionContents.getEffects()) {
                        var effectType = effect.getEffectType().value();
                        String result = null;
                        if (effectType == net.minecraft.entity.effect.StatusEffects.POISON.value()) result = "poison";
                        else if (effectType == net.minecraft.entity.effect.StatusEffects.SLOWNESS.value()) result = "slowness";
                        else if (effectType == net.minecraft.entity.effect.StatusEffects.WEAKNESS.value()) result = "weakness";
                        else if (effectType == net.minecraft.entity.effect.StatusEffects.INSTANT_DAMAGE.value()) result = "harming";
                        else if (effectType == net.minecraft.entity.effect.StatusEffects.INSTANT_HEALTH.value()) result = "healing";
                        else if (effectType == net.minecraft.entity.effect.StatusEffects.FIRE_RESISTANCE.value()) result = "fire_resistance";
                        if (result != null) {
                            stack.decrement(1);
                            return result;
                        }
                        // unrecognized effect, skip
                    }
                }
                // no recognized effect, consume as normal arrow
                stack.decrement(1);
                return null;
            }
        }
        return null;
    }

    public static boolean hasTippedArrows(ServerPlayerEntity player) {
        for (int i = 0; i < player.getInventory().size(); i++) {
            if (player.getInventory().getStack(i).getItem() == Items.TIPPED_ARROW) return true;
        }
        return false;
    }

    /** Uses string matching on registry ID for 1.21.1 compatibility. */
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

    // Sharpness gives flat +1/level here; Smite/Bane are AoE/debuff in WeaponAbility
    public static int getWeaponEnchantBonus(ServerPlayerEntity player) {
        ItemStack weapon = player.getMainHandStack();
        int bonus = 0;
        bonus += getEnchantLevel(weapon, "minecraft:sharpness");
        return bonus;
    }

    public static int getSharpness(ServerPlayerEntity player) {
        return getEnchantLevel(player.getMainHandStack(), "minecraft:sharpness");
    }

    public static int getSmite(ServerPlayerEntity player) {
        return getEnchantLevel(player.getMainHandStack(), "minecraft:smite");
    }

    public static int getBane(ServerPlayerEntity player) {
        return getEnchantLevel(player.getMainHandStack(), "minecraft:bane_of_arthropods");
    }

    public static int getFireAspect(ServerPlayerEntity player) {
        return getEnchantLevel(player.getMainHandStack(), "minecraft:fire_aspect");
    }

    public static boolean isUndead(String entityTypeId) {
        return switch (entityTypeId) {
            case "minecraft:zombie", "minecraft:husk", "minecraft:drowned",
                 "minecraft:skeleton", "minecraft:stray", "minecraft:wither_skeleton",
                 "minecraft:phantom", "minecraft:zombified_piglin",
                 "minecraft:zombie_villager", "minecraft:skeleton_horse",
                 "minecraft:zombie_horse", "minecraft:wither" -> true;
            default -> false;
        };
    }

    public static boolean isArthropod(String entityTypeId) {
        return switch (entityTypeId) {
            case "minecraft:spider", "minecraft:cave_spider",
                 "minecraft:silverfish", "minecraft:endermite", "minecraft:bee" -> true;
            default -> false;
        };
    }

    public static int getKnockback(ServerPlayerEntity player) {
        return getEnchantLevel(player.getMainHandStack(), "minecraft:knockback");
    }

    public static int getLooting(ServerPlayerEntity player) {
        return getEnchantLevel(player.getMainHandStack(), "minecraft:looting");
    }

    public static int getSweepingEdge(ServerPlayerEntity player) {
        return getEnchantLevel(player.getMainHandStack(), "minecraft:sweeping_edge");
    }

    /** Each 2 protection levels = +1 defense. Stacks across all armor pieces. */
    public static int getTotalProtection(ServerPlayerEntity player) {
        int total = 0;
        for (EquipmentSlot slot : new EquipmentSlot[]{
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack piece = player.getEquippedStack(slot);
            total += getEnchantLevel(piece, "minecraft:protection");
            total += getEnchantLevel(piece, "minecraft:blast_protection");
            total += getEnchantLevel(piece, "minecraft:projectile_protection");
        }
        return total / 2;
    }

    public static int getThorns(ServerPlayerEntity player) {
        int max = 0;
        for (EquipmentSlot slot : new EquipmentSlot[]{
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            max = Math.max(max, getEnchantLevel(player.getEquippedStack(slot), "minecraft:thorns"));
        }
        return max;
    }

    public static boolean hasFeatherFalling(ServerPlayerEntity player) {
        return getEnchantLevel(player.getEquippedStack(EquipmentSlot.FEET), "minecraft:feather_falling") > 0;
    }

    /** Scaled: 1/3/5/8/11 per level */
    public static int getBowPower(ServerPlayerEntity player) {
        int level = getEnchantLevel(player.getMainHandStack(), "minecraft:power");
        return switch (level) {
            case 1 -> 1;
            case 2 -> 3;
            case 3 -> 5;
            case 4 -> 8;
            case 5 -> 11;
            default -> level > 5 ? 11 + (level - 5) * 3 : 0;
        };
    }

    /** Scaled: 1/1/2/2/3 per level */
    public static int getBowPowerRange(ServerPlayerEntity player) {
        int level = getEnchantLevel(player.getMainHandStack(), "minecraft:power");
        return switch (level) {
            case 1, 2 -> 1;
            case 3, 4 -> 2;
            case 5 -> 3;
            default -> level > 5 ? 3 : 0;
        };
    }

    public static int getBowFlame(ServerPlayerEntity player) {
        return getEnchantLevel(player.getMainHandStack(), "minecraft:flame");
    }

    public static boolean hasBowFlame(ServerPlayerEntity player) {
        return getBowFlame(player) > 0;
    }

    public static boolean hasInfinity(ServerPlayerEntity player) {
        return getEnchantLevel(player.getMainHandStack(), "minecraft:infinity") > 0;
    }

    public static int getBowPunch(ServerPlayerEntity player) {
        return getEnchantLevel(player.getMainHandStack(), "minecraft:punch");
    }

    public static int getQuickCharge(ServerPlayerEntity player) {
        return getEnchantLevel(player.getMainHandStack(), "minecraft:quick_charge");
    }

    public static boolean hasMultishot(ServerPlayerEntity player) {
        return getEnchantLevel(player.getMainHandStack(), "minecraft:multishot") > 0;
    }

    public static int getPiercing(ServerPlayerEntity player) {
        return getEnchantLevel(player.getMainHandStack(), "minecraft:piercing");
    }

    public static int getDensity(ServerPlayerEntity player) {
        return getEnchantLevel(player.getMainHandStack(), "minecraft:density");
    }

    public static int getBreach(ServerPlayerEntity player) {
        return getEnchantLevel(player.getMainHandStack(), "minecraft:breach");
    }

    public static int getWindBurst(ServerPlayerEntity player) {
        return getEnchantLevel(player.getMainHandStack(), "minecraft:wind_burst");
    }

    public static int getRiptide(ServerPlayerEntity player) {
        return getEnchantLevel(player.getMainHandStack(), "minecraft:riptide");
    }

    public static int getChanneling(ServerPlayerEntity player) {
        return getEnchantLevel(player.getMainHandStack(), "minecraft:channeling");
    }

    public static int getLoyalty(ServerPlayerEntity player) {
        return getEnchantLevel(player.getMainHandStack(), "minecraft:loyalty");
    }

    public static int getImpaling(ServerPlayerEntity player) {
        return getEnchantLevel(player.getMainHandStack(), "minecraft:impaling");
    }

    /** Scaled: 1/2/5/8/10 */
    public static int getImpalingDamage(ServerPlayerEntity player) {
        int level = getImpaling(player);
        return switch (level) {
            case 1 -> 1;
            case 2 -> 2;
            case 3 -> 5;
            case 4 -> 8;
            case 5 -> 10;
            default -> level > 5 ? 10 + (level - 5) * 2 : 0;
        };
    }

    /** Scaled: 1/1/2/2/3 */
    public static int getImpalingBleed(ServerPlayerEntity player) {
        int level = getImpaling(player);
        return switch (level) {
            case 1, 2 -> 1;
            case 3, 4 -> 2;
            case 5 -> 3;
            default -> level > 5 ? 3 : 0;
        };
    }

    public static int getUnbreaking(ServerPlayerEntity player) {
        return getEnchantLevel(player.getMainHandStack(), "minecraft:unbreaking");
    }

    public static int getEfficiency(ServerPlayerEntity player) {
        return getEnchantLevel(player.getMainHandStack(), "minecraft:efficiency");
    }
}
