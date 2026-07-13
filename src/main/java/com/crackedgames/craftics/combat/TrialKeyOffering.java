package com.crackedgames.craftics.combat;

import com.crackedgames.craftics.api.registry.WeaponRegistry;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Trial keys as a shrine offering.
 *
 * <p>Trial keys were previously loot-only: nothing in Craftics ever consumed one. A
 * player holding one at a shrine may hand it over instead of emeralds, and unlike an
 * emerald offering the shrine never eats a key for nothing - a key always pays out.
 *
 * <p>A plain {@linkplain net.minecraft.item.Items#TRIAL_KEY trial key} buys one solid
 * piece of gear. An {@linkplain net.minecraft.item.Items#OMINOUS_TRIAL_KEY ominous
 * trial key} buys one of three legendary outcomes, all heavily enchanted:
 * a Simply Swords boss unique, a trimmed armor piece, or a weapon drawn from the whole
 * weapon registry - every affinity, including modded weapons.
 */
public final class TrialKeyOffering {

    private TrialKeyOffering() {}

    /** Dialogue action for handing over a plain trial key. */
    public static final String ACTION_TRIAL = "shrine:trial_key";
    /** Dialogue action for handing over an ominous trial key. */
    public static final String ACTION_OMINOUS = "shrine:ominous_trial_key";

    /** Armor pool for the trimmed-armor outcome - four slots x six materials. */
    private static final Item[] ARMOR = {
        Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS,
        Items.CHAINMAIL_HELMET, Items.CHAINMAIL_CHESTPLATE, Items.CHAINMAIL_LEGGINGS, Items.CHAINMAIL_BOOTS,
        Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS,
        Items.GOLDEN_HELMET, Items.GOLDEN_CHESTPLATE, Items.GOLDEN_LEGGINGS, Items.GOLDEN_BOOTS,
        Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS,
        Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS,
    };

    /** Fallback weapons for the any-affinity roll, used only if the registry is empty. */
    private static final Item[] FALLBACK_WEAPONS = {
        Items.NETHERITE_SWORD, Items.NETHERITE_AXE, Items.MACE, Items.BOW, Items.CROSSBOW, Items.TRIDENT
    };

    /** How many armor pieces an ominous key's armor outcome hands out. */
    private static final int OMINOUS_ARMOR_PIECES = 2;

    // =========================================================================
    // Inventory
    // =========================================================================

    public static boolean hasTrialKey(ServerPlayerEntity player) {
        return countOf(player, Items.TRIAL_KEY) > 0;
    }

    public static boolean hasOminousTrialKey(ServerPlayerEntity player) {
        return countOf(player, Items.OMINOUS_TRIAL_KEY) > 0;
    }

    private static int countOf(ServerPlayerEntity player, Item item) {
        if (player == null) return 0;
        PlayerInventory inv = player.getInventory();
        int count = 0;
        for (int i = 0; i < inv.size(); i++) {
            if (inv.getStack(i).isOf(item)) count += inv.getStack(i).getCount();
        }
        return count;
    }

    /** Take one key of {@code item} from the player. Returns false if they had none. */
    private static boolean consumeOne(ServerPlayerEntity player, Item item) {
        PlayerInventory inv = player.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack stack = inv.getStack(i);
            if (stack.isOf(item)) {
                stack.decrement(1);
                return true;
            }
        }
        return false;
    }

    // =========================================================================
    // Rewards
    // =========================================================================

    /** The result of handing a key to the shrine: what to grant, and what to narrate. */
    public record Offering(List<ItemStack> rewards, String resultLine, String revealTitle) {}

    /**
     * Consume the key named by {@code action} and roll its reward. Returns {@code null}
     * when the action isn't a key offering or the player no longer holds that key
     * (a stale dialogue button, or a party member who spent theirs first).
     */
    public static Offering offer(ServerPlayerEntity player, ServerWorld world, String action, Random rng) {
        if (ACTION_TRIAL.equals(action)) {
            if (!consumeOne(player, Items.TRIAL_KEY)) return null;
            return rollTrial(world, rng);
        }
        if (ACTION_OMINOUS.equals(action)) {
            if (!consumeOne(player, Items.OMINOUS_TRIAL_KEY)) return null;
            return rollOminous(player, world, rng);
        }
        return null;
    }

    /**
     * A plain key: one good, lightly enchanted piece of gear plus a supply item. Two
     * enchantments at level 1 or 2 - useful, not decisive. The god-tier roll belongs to
     * the ominous key. Guaranteed to pay out; the shrine never wastes a key.
     */
    private static Offering rollTrial(ServerWorld world, Random rng) {
        List<ItemStack> rewards = new ArrayList<>();
        ItemStack gear = rng.nextBoolean()
            ? enchantedWeapon(world, rng, false)
            : trimmedArmor(world, rng, false);
        rewards.add(gear);
        rewards.add(supply(rng));
        return new Offering(rewards,
            "The shrine accepts the key. The lock turns, and it gives you: "
                + gear.getName().getString() + ".",
            "Trial Key Offering");
    }

    /**
     * An ominous key: one of three legendary outcomes, always heavily enchanted. The
     * legendary branch draws from every installed unique-weapon mod - Simply Swords blades
     * and Simply Bows bows alike - and falls through to the plain weapon roll when neither
     * is installed, so it never hands back an empty stack.
     */
    private static Offering rollOminous(ServerPlayerEntity player, ServerWorld world, Random rng) {
        List<ItemStack> rewards = new ArrayList<>();
        String line;
        String title = "Ominous Trial Key Offering";

        int roll = rng.nextInt(3);
        if (roll == 0) {
            ItemStack unique = CombatManager.rollUniqueWeapon(player);
            if (!unique.isEmpty()) {
                CombatManager.heavilyEnchant(world, unique,
                    CombatManager.getValidWeaponEnchants(unique), rng);
                rewards.add(unique);
                line = "The ominous key screams as it turns. A legendary weapon answers: "
                    + unique.getName().getString() + ".";
                return new Offering(rewards, line, title);
            }
            roll = 2; // no unique-weapon mod installed - fall through to the weapon roll
        }

        if (roll == 1) {
            for (int i = 0; i < OMINOUS_ARMOR_PIECES; i++) {
                rewards.add(trimmedArmor(world, rng, true));
            }
            line = "The ominous key turns. Armor of a fallen champion is laid before you.";
        } else {
            ItemStack weapon = anyAffinityWeapon(world, rng);
            rewards.add(weapon);
            line = "The ominous key turns. A weapon thick with enchantment appears: "
                + weapon.getName().getString() + ".";
        }
        rewards.add(supply(rng));
        return new Offering(rewards, line, title);
    }

    // =========================================================================
    // Reward pieces
    // =========================================================================

    /**
     * Enchant {@code stack} at the strength this key deserves: an ominous key gives the
     * god roll, a plain key two modest enchantments.
     */
    private static void enchant(ServerWorld world, ItemStack stack, String[] pool,
                                Random rng, boolean godTier) {
        if (godTier) {
            CombatManager.heavilyEnchant(world, stack, pool, rng);
        } else {
            CombatManager.lightlyEnchant(world, stack, pool, rng);
        }
    }

    /** A randomly trimmed armor piece of any material, enchanted to the key's strength. */
    private static ItemStack trimmedArmor(ServerWorld world, Random rng, boolean godTier) {
        Item item = ARMOR[rng.nextInt(ARMOR.length)];
        ItemStack stack = new ItemStack(item, 1);
        enchant(world, stack, CombatManager.getValidArmorEnchants(armorSlotFor(item)), rng, godTier);
        CombatManager.applyRandomTrim(stack, world);
        return stack;
    }

    /** A weapon from the fixed vanilla top-tier pool, enchanted to the key's strength. */
    private static ItemStack enchantedWeapon(ServerWorld world, Random rng, boolean godTier) {
        ItemStack stack = new ItemStack(FALLBACK_WEAPONS[rng.nextInt(FALLBACK_WEAPONS.length)], 1);
        enchant(world, stack, CombatManager.getValidWeaponEnchants(stack), rng, godTier);
        return stack;
    }

    /**
     * A weapon drawn from the entire weapon registry, so every affinity - and every
     * installed weapon mod - is in the pool. Damageable items only: a stick or a coral fan
     * is registered as a weapon but makes a poor prize. Only the ominous key rolls this,
     * so it always comes out god-enchanted.
     */
    private static ItemStack anyAffinityWeapon(ServerWorld world, Random rng) {
        List<Item> pool = new ArrayList<>();
        for (Item item : WeaponRegistry.registeredItems()) {
            if (item != null && new ItemStack(item).isDamageable()) pool.add(item);
        }
        if (pool.isEmpty()) return enchantedWeapon(world, rng, true);
        // Sort for a stable draw: registry iteration order is not deterministic and a
        // seeded rng should give the same prize on the same seed.
        pool.sort(java.util.Comparator.comparing(i -> Registries.ITEM.getId(i).toString()));
        ItemStack stack = new ItemStack(pool.get(rng.nextInt(pool.size())), 1);
        enchant(world, stack, CombatManager.getValidWeaponEnchants(stack), rng, true);
        return stack;
    }

    /** One high-tier consumable to round out the payout. */
    private static ItemStack supply(Random rng) {
        return switch (rng.nextInt(4)) {
            case 0 -> new ItemStack(Items.ENCHANTED_GOLDEN_APPLE, 1);
            case 1 -> new ItemStack(Items.GOLDEN_APPLE, 2);
            case 2 -> new ItemStack(Items.TOTEM_OF_UNDYING, 1);
            default -> new ItemStack(Items.GOLDEN_CARROT, 4);
        };
    }

    /** Map an armor item to its slot, for the enchant-pool lookup. */
    private static EquipmentSlot armorSlotFor(Item item) {
        String id = Registries.ITEM.getId(item).getPath();
        if (id.endsWith("_helmet")) return EquipmentSlot.HEAD;
        if (id.endsWith("_chestplate")) return EquipmentSlot.CHEST;
        if (id.endsWith("_leggings")) return EquipmentSlot.LEGS;
        return EquipmentSlot.FEET;
    }
}
