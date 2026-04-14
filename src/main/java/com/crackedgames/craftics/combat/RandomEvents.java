package com.crackedgames.craftics.combat;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Random;

/**
 * Non-combat random events that happen between levels.
 * These give rewards/effects without entering combat.
 */
public class RandomEvents {

    // ── Shrine of Fortune ──
    // Player spends emeralds to gamble for rewards
    public static String handleShrine(ServerPlayerEntity player, com.crackedgames.craftics.world.CrafticsSavedData.PlayerData pd) {
        Random rng = new Random();
        int cost = 3 + rng.nextInt(3); // 3-5 emeralds

        if (pd.emeralds < cost) {
            return "\u00a7e\u00a7lShrine of Fortune \u00a7r\u00a77appears!\n\u00a7cYou need " + cost + " emeralds to make an offering. (Have: " + pd.emeralds + ")";
        }

        pd.spendEmeralds(cost);

        // Roll reward
        int roll = rng.nextInt(100);
        ItemStack reward;
        String desc;

        if (roll < 30) {
            // Common: consumables
            reward = switch (rng.nextInt(4)) {
                case 0 -> new ItemStack(Items.GOLDEN_APPLE, 2);
                case 1 -> new ItemStack(Items.ENDER_PEARL, 3);
                case 2 -> new ItemStack(Items.ARROW, 32);
                default -> new ItemStack(Items.COOKED_BEEF, 8);
            };
            desc = "\u00a7aThe shrine rewards your faith!";
        } else if (roll < 60) {
            // Good: gear
            reward = switch (rng.nextInt(4)) {
                case 0 -> new ItemStack(Items.DIAMOND, 3);
                case 1 -> new ItemStack(Items.SHIELD, 1);
                case 2 -> new ItemStack(Items.IRON_SWORD, 1);
                default -> new ItemStack(Items.TOTEM_OF_UNDYING, 1);
            };
            desc = "\u00a7bThe shrine glows brightly!";
        } else if (roll < 85) {
            // Great: rare items
            reward = switch (rng.nextInt(3)) {
                case 0 -> new ItemStack(Items.DIAMOND_SWORD, 1);
                case 1 -> new ItemStack(Items.DIAMOND_CHESTPLATE, 1);
                default -> new ItemStack(Items.ENCHANTED_GOLDEN_APPLE, 1);
            };
            desc = "\u00a7d\u00a7lThe shrine erupts with light!";
        } else {
            // Jackpot: refund + bonus
            pd.addEmeralds(cost * 3);
            reward = new ItemStack(Items.EMERALD, cost * 3);
            desc = "\u00a76\u00a7l\u2726 JACKPOT! \u2726 \u00a7r\u00a76Triple emeralds returned!";
        }

        String rewardName = reward.getName().getString();
        LootDelivery.deliver(player, reward);
        return "\u00a7e\u00a7lShrine of Fortune! \u00a7r\u00a77(" + cost + " emeralds offered)\n" + desc + "\n\u00a77Received: \u00a7f" + rewardName;
    }

    // ── Wounded Traveler ──
    // Player gives food to receive a random reward
    public static String handleWoundedTraveler(ServerPlayerEntity player) {
        // Check if player has any food
        int foodSlot = -1;
        for (int i = 0; i < player.getInventory().size(); i++) {
            if (ItemUseHandler.isFood(player.getInventory().getStack(i).getItem())) {
                foodSlot = i;
                break;
            }
        }

        if (foodSlot < 0) {
            return "\u00a7e\u00a7lWounded Traveler \u00a7r\u00a77begs for food...\n\u00a7cYou have no food to give!";
        }

        // Consume the food
        String foodName = player.getInventory().getStack(foodSlot).getName().getString();
        player.getInventory().getStack(foodSlot).decrement(1);

        // Give reward
        Random rng = new Random();
        ItemStack reward;
        int roll = rng.nextInt(100);

        if (roll < 40) {
            reward = switch (rng.nextInt(4)) {
                case 0 -> new ItemStack(Items.EMERALD, 3);
                case 1 -> new ItemStack(Items.IRON_INGOT, 5);
                case 2 -> new ItemStack(Items.BONE, 4);
                default -> new ItemStack(Items.ARROW, 16);
            };
        } else if (roll < 70) {
            reward = switch (rng.nextInt(3)) {
                case 0 -> new ItemStack(Items.DIAMOND, 1);
                case 1 -> new ItemStack(Items.GOLDEN_APPLE, 1);
                default -> new ItemStack(Items.SPYGLASS, 1);
            };
        } else if (roll < 90) {
            reward = switch (rng.nextInt(3)) {
                case 0 -> new ItemStack(Items.SADDLE, 1);
                case 1 -> new ItemStack(Items.ENCHANTED_BOOK, 1);
                default -> new ItemStack(Items.NAME_TAG, 1);
            };
        } else {
            reward = switch (rng.nextInt(2)) {
                case 0 -> new ItemStack(Items.DIAMOND_SWORD, 1);
                default -> new ItemStack(Items.TOTEM_OF_UNDYING, 1);
            };
        }

        String rewardName = reward.getName().getString();
        LootDelivery.deliver(player, reward);
        return "\u00a7e\u00a7lWounded Traveler! \u00a7r\u00a77You give " + foodName + ".\n\u00a7a\"Thank you, brave warrior!\"\n\u00a77Received: \u00a7f" + rewardName;
    }

    // ── Suspicious Block ──
    // Player brushes suspicious sand/gravel for a 75% chance at a random pottery sherd
    public static String handleSuspiciousBlock(ServerPlayerEntity player) {
        Random rng = new Random();
        boolean isSand = rng.nextBoolean();
        String blockName = isSand ? "Suspicious Sand" : "Suspicious Gravel";

        if (rng.nextInt(4) != 0) {
            // 75% chance: random pottery sherd
            var sherdList = new java.util.ArrayList<>(PotterySherdSpells.POTTERY_SHERDS);
            net.minecraft.item.Item sherd = sherdList.get(rng.nextInt(sherdList.size()));
            ItemStack reward = new ItemStack(sherd, 1);
            String sherdName = reward.getName().getString();
            LootDelivery.deliver(player, reward);
            return "\u00a76\u00a7l\u2726 " + blockName + "! \u2726\n"
                + "\u00a77You carefully brush away the layers...\n"
                + "\u00a7a\u00a7lYou uncover an ancient pottery sherd!\n"
                + "\u00a77Received: \u00a7d" + sherdName;
        } else {
            // 75% chance: consolation — flint or clay ball
            ItemStack consolation = isSand
                ? new ItemStack(Items.CLAY_BALL, 2)
                : new ItemStack(Items.FLINT, 2);
            String consolationName = consolation.getName().getString();
            LootDelivery.deliver(player, consolation);
            return "\u00a76\u00a7l\u2726 " + blockName + "! \u2726\n"
                + "\u00a77You carefully brush away the layers...\n"
                + "\u00a77Nothing of value — just " + consolationName.toLowerCase() + ".\n"
                + "\u00a77Received: \u00a7f" + consolationName;
        }
    }

    // ── Treasure Vault ──
    // Generates a level definition with no enemies — just loot, scaled to biome tier
    public static com.crackedgames.craftics.level.LevelDefinition generateTreasureVault(int biomeOrdinal) {
        return TrialChamberEvent.generateTreasureVault(biomeOrdinal);
    }

    // ── Ominous Trial Chamber ──
    // Harder version of trial chamber with elite mobs
    public static com.crackedgames.craftics.level.LevelDefinition generateOminousTrial(int biomeOrdinal, int ngPlusLevel) {
        return TrialChamberEvent.generateOminous(biomeOrdinal, ngPlusLevel);
    }

    // ── Ambush ──
    // Quick unavoidable fight with 2-3 fast enemies
    public static com.crackedgames.craftics.level.LevelDefinition generateAmbush(String arenaBiomeId, int biomeOrdinal, int ngPlusLevel) {
        return TrialChamberEvent.generateAmbush(arenaBiomeId, biomeOrdinal, ngPlusLevel);
    }

    // === Multi-player reward methods ===

    /**
     * Execute shrine event for all participants. Each player pays and rolls independently.
     */
    public static void handleShrineForAll(java.util.List<ServerPlayerEntity> participants,
                                           com.crackedgames.craftics.world.CrafticsSavedData data) {
        for (ServerPlayerEntity player : participants) {
            com.crackedgames.craftics.world.CrafticsSavedData.PlayerData pd = data.getPlayerData(player.getUuid());
            String result = handleShrine(player, pd);
            data.markDirty();
            player.sendMessage(net.minecraft.text.Text.literal(result), false);
        }
    }

    /**
     * Execute traveler event for all participants. Each donates food and rolls independently.
     */
    public static void handleTravelerForAll(java.util.List<ServerPlayerEntity> participants) {
        for (ServerPlayerEntity player : participants) {
            String result = handleWoundedTraveler(player);
            player.sendMessage(net.minecraft.text.Text.literal(result), false);
        }
    }

    /**
     * Execute dig site reward for a single player (called when they right-click the block).
     */
    public static String handleDigSiteForPlayer(ServerPlayerEntity player) {
        return handleSuspiciousBlock(player);
    }
}
