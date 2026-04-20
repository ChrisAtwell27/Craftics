package com.crackedgames.craftics.combat;

import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;
import com.crackedgames.craftics.core.GridTile;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.Map;
import java.util.Set;

/**
 * Handles item usage during combat (food, potions, throwables, utilities).
 */
public class ItemUseHandler {
    // --- AoE Water Throwables: Turtle Egg, Pufferfish, Nautilus Shell, Heart of the Sea --- 
    private static String useWaterThrowable(ServerPlayerEntity player, GridArena arena, GridPos targetTile, ItemStack stack, Item item) {
        if (targetTile == null) return "§cNeed to target a tile!";
        if (!arena.isInBounds(targetTile)) return "§cTarget out of bounds!";

        // Determine tier and effect
        int radius = 1, waterDamage = 2, soakedLevel = 1, confusionLevel = 0, poisonLevel = 0;
        String name = stack.getName().getString();
        if (item == Items.TURTLE_EGG) {
            radius = 1; waterDamage = 2; soakedLevel = 1;
        } else if (item == Items.PUFFERFISH) {
            radius = 2; waterDamage = 3; soakedLevel = 2; poisonLevel = 1;
        } else if (item == Items.NAUTILUS_SHELL) {
            radius = 2; waterDamage = 4; soakedLevel = 3; confusionLevel = 1;
        } else if (item == Items.HEART_OF_THE_SEA) {
            radius = 3; waterDamage = 5; soakedLevel = 4; confusionLevel = 2;
        }

        stack.decrement(1);
        int hitCount = 0;
        StringBuilder msg = new StringBuilder();
        for (CombatEntity enemy : arena.getOccupants().values()) {
            if (!enemy.isAlive() || enemy.isAlly()) continue;
            if (enemy.getGridPos().manhattanDistance(targetTile) <= radius) {
                // Deal Water-type damage (assume Water type is handled in takeDamage or add a param if needed)
                int dealt = enemy.takeDamage(waterDamage); // TODO: Pass Water type if needed
                // Apply Soaked
                enemy.stackSoaked(soakedLevel, soakedLevel);
                // Apply Poison if pufferfish
                if (poisonLevel > 0) {
                    enemy.stackPoison(poisonLevel, poisonLevel);
                }
                // Apply Confusion if nautilus/heart
                if (confusionLevel > 0) {
                    enemy.stackConfusion(confusionLevel, confusionLevel);
                }
                hitCount++;
                msg.append("§b").append(enemy.getDisplayName()).append(" -").append(dealt).append("HP ");
            }
        }
        // Visual/sound feedback (optional)
        // TODO: Add splash particles and water sound
        if (hitCount > 0) {
            return "§3" + name + " splashed " + hitCount + "! " + msg.toString().trim();
        }
        return "§3" + name + " splashed, but hit no enemies.";
    }

    // Food items and their heal values
    private static final Map<Item, Integer> FOOD_HEAL = Map.ofEntries(
        Map.entry(Items.APPLE, 2),
        Map.entry(Items.BREAD, 3),
        Map.entry(Items.COOKED_BEEF, 5),
        Map.entry(Items.COOKED_PORKCHOP, 5),
        Map.entry(Items.COOKED_CHICKEN, 3),
        Map.entry(Items.COOKED_MUTTON, 4),
        Map.entry(Items.COOKED_COD, 3),
        Map.entry(Items.COOKED_SALMON, 4),
        Map.entry(Items.BAKED_POTATO, 3),
        Map.entry(Items.COOKIE, 1),
        Map.entry(Items.PUMPKIN_PIE, 4),
        Map.entry(Items.MELON_SLICE, 1),
        Map.entry(Items.SWEET_BERRIES, 1),
        Map.entry(Items.GLOW_BERRIES, 1),
        Map.entry(Items.GOLDEN_CARROT, 2),
        Map.entry(Items.GOLDEN_APPLE, 8),
        Map.entry(Items.ENCHANTED_GOLDEN_APPLE, 10),
        Map.entry(Items.HONEY_BOTTLE, 3),
        Map.entry(Items.SUSPICIOUS_STEW, 4),
        Map.entry(Items.CHORUS_FRUIT, 2),
        Map.entry(Items.DRIED_KELP, 1),
        Map.entry(Items.BEEF, 2),
        Map.entry(Items.PORKCHOP, 2),
        Map.entry(Items.CHICKEN, 1),
        Map.entry(Items.MUTTON, 2),
        Map.entry(Items.COD, 1),
        Map.entry(Items.SALMON, 1),
        Map.entry(Items.RABBIT, 2),
        Map.entry(Items.COOKED_RABBIT, 3),
        Map.entry(Items.TROPICAL_FISH, 1),
        Map.entry(Items.POTATO, 1),
        Map.entry(Items.POISONOUS_POTATO, 1),
        Map.entry(Items.CARROT, 2),
        Map.entry(Items.BEETROOT, 1),
        Map.entry(Items.MUSHROOM_STEW, 4),
        Map.entry(Items.BEETROOT_SOUP, 4),
        Map.entry(Items.RABBIT_STEW, 6),
        Map.entry(Items.SPIDER_EYE, 1),
        Map.entry(Items.ROTTEN_FLESH, 2)
    );

    // Items that are "usable" in combat
    private static final Set<Item> THROWABLES = Set.of(
        Items.SNOWBALL, Items.EGG, Items.ENDER_PEARL, Items.FIRE_CHARGE, Items.WIND_CHARGE
    );

    public static boolean isFood(Item item) {
        return FOOD_HEAL.containsKey(item) || isArtifactsNonConsumingFood(item);
    }

    /**
     * Eternal Steak / Everlasting Beef from the Artifacts mod — non-consuming food items.
     * Detected by registry id so this works without a compile-time dependency on Artifacts.
     */
    public static boolean isArtifactsNonConsumingFood(Item item) {
        net.minecraft.util.Identifier id = net.minecraft.registry.Registries.ITEM.getId(item);
        if (id == null || !"artifacts".equals(id.getNamespace())) return false;
        String path = id.getPath();
        return "eternal_steak".equals(path) || "everlasting_beef".equals(path);
    }

    public static boolean isPotion(Item item) {
        return item == Items.POTION;
    }

    public static boolean isSplashPotion(Item item) {
        return item == Items.SPLASH_POTION;
    }

    public static boolean isThrowable(Item item) {
        if (THROWABLES.contains(item)) return true;
        //? if >=1.21.5 {
        /*if (item == net.minecraft.item.Items.BLUE_EGG || item == net.minecraft.item.Items.BROWN_EGG) return true;
        *///?}
        return false;
    }

    // Breeding/taming materials — maps mob entity type → item that tames them
    private static final Map<String, Set<Item>> BREEDING_ITEMS = Map.ofEntries(
        Map.entry("minecraft:wolf", Set.of(Items.BONE, Items.BEEF, Items.COOKED_BEEF)),
        Map.entry("minecraft:cat", Set.of(Items.COD, Items.SALMON, Items.COOKED_COD, Items.COOKED_SALMON)),
        Map.entry("minecraft:ocelot", Set.of(Items.COD, Items.SALMON)),
        Map.entry("minecraft:cow", Set.of(Items.WHEAT)),
        Map.entry("minecraft:sheep", Set.of(Items.WHEAT)),
        Map.entry("minecraft:pig", Set.of(Items.CARROT, Items.POTATO, Items.BEETROOT)),
        Map.entry("minecraft:chicken", Set.of(Items.WHEAT_SEEDS, Items.MELON_SEEDS, Items.PUMPKIN_SEEDS, Items.BEETROOT_SEEDS)),
        Map.entry("minecraft:horse", Set.of(Items.GOLDEN_APPLE, Items.GOLDEN_CARROT)),
        Map.entry("minecraft:donkey", Set.of(Items.GOLDEN_APPLE, Items.GOLDEN_CARROT)),
        Map.entry("minecraft:rabbit", Set.of(Items.CARROT, Items.GOLDEN_CARROT, Items.DANDELION)),
        Map.entry("minecraft:llama", Set.of(Items.HAY_BLOCK)),
        Map.entry("minecraft:fox", Set.of(Items.SWEET_BERRIES, Items.GLOW_BERRIES)),
        Map.entry("minecraft:goat", Set.of(Items.WHEAT)),
        Map.entry("minecraft:bee", Set.of(Items.DANDELION, Items.POPPY, Items.BLUE_ORCHID)),
        Map.entry("minecraft:mooshroom", Set.of(Items.WHEAT)),
        Map.entry("minecraft:parrot", Set.of(Items.WHEAT_SEEDS, Items.MELON_SEEDS)),
        Map.entry("minecraft:turtle", Set.of(Items.SEAGRASS)),
        Map.entry("minecraft:axolotl", Set.of(Items.TROPICAL_FISH_BUCKET)),
        Map.entry("minecraft:frog", Set.of(Items.SLIME_BALL)),
        Map.entry("minecraft:camel", Set.of(Items.CACTUS)),
        Map.entry("minecraft:sniffer", Set.of(Items.TORCHFLOWER_SEEDS))
    );

    public static boolean isBreedingItem(Item item, String entityTypeId) {
        Set<Item> items = BREEDING_ITEMS.get(entityTypeId);
        return items != null && items.contains(item);
    }

    public static boolean isAnyBreedingItem(Item item) {
        for (Set<Item> items : BREEDING_ITEMS.values()) {
            if (items.contains(item)) return true;
        }
        return item == Items.BONE; // bone always counts
        }

    public static boolean isFishingRod(Item item) {
        return item == Items.FISHING_ROD;
    }

    // Items with special AP costs
    private static final Set<Item> TWO_AP_ITEMS = Set.of(
        Items.BELL, Items.JUKEBOX, Items.CROSSBOW
    );

    public static int getApCost(Item item) {
        if (item == Items.GOLDEN_CARROT) return 0;
        if (PotterySherdSpells.isPotterySherd(item)) return PotterySherdSpells.getSherdApCost(item);
        if (item == Items.FISHING_ROD) return FISHING_AP_COST;
        if (TWO_AP_ITEMS.contains(item)) return 2;
        if (isArtifactsNonConsumingFood(item)) return 2;
        return 1;
    }

    /**
     * Get AP cost for an ItemStack (handles goat horn variants with different costs).
     */
    public static int getApCost(ItemStack stack) {
        if (stack.getItem() == Items.GOAT_HORN) {
            String hornId = GoatHornEffects.getHornId(stack);
            if (hornId != null) return GoatHornEffects.getApCost(hornId);
            return 2;
        }
        return getApCost(stack.getItem());
    }

    private static final Set<Item> EXTRA_USABLE = Set.of(
        Items.SPYGLASS, Items.COMPASS, Items.BELL, Items.LAVA_BUCKET,
        Items.SCAFFOLDING, Items.CAMPFIRE, Items.ANVIL, Items.HONEY_BLOCK,
        Items.POWDER_SNOW_BUCKET, Items.JUKEBOX,
        Items.WHITE_BANNER, Items.BLACK_BANNER, Items.RED_BANNER,
        Items.BLUE_BANNER, Items.GREEN_BANNER, Items.YELLOW_BANNER,
        Items.ORANGE_BANNER, Items.PURPLE_BANNER, Items.CYAN_BANNER,
        Items.LIGHT_BLUE_BANNER, Items.MAGENTA_BANNER, Items.PINK_BANNER,
        Items.BROWN_BANNER, Items.GRAY_BANNER, Items.LIGHT_GRAY_BANNER,
        Items.LIME_BANNER,
        Items.WATER_BUCKET, Items.SPONGE, Items.IRON_PICKAXE,
        Items.DIAMOND_PICKAXE, Items.NETHERITE_PICKAXE, Items.STONE_PICKAXE,
        Items.WOODEN_PICKAXE, Items.GOLDEN_PICKAXE, Items.CROSSBOW,
        Items.LINGERING_POTION, Items.LIGHTNING_ROD, Items.CACTUS,
        Items.HAY_BLOCK, Items.CAKE, Items.SPORE_BLOSSOM,
        Items.LANTERN, Items.TORCH, Items.GOAT_HORN, Items.ECHO_SHARD, Items.BRUSH
    );

    private static boolean isBanner(Item item) {
        return item.toString().contains("banner");
    }

    private static final Set<Item> WATER_THROWABLES = Set.of(
        Items.TURTLE_EGG, Items.PUFFERFISH, Items.NAUTILUS_SHELL, Items.HEART_OF_THE_SEA
    );

    public static boolean isWaterThrowable(Item item) {
        return WATER_THROWABLES.contains(item);
    }

    public static boolean isUsableItem(Item item) {
        return isFood(item) || isPotion(item) || isSplashPotion(item)
            || isThrowable(item) || isWaterThrowable(item) || item == Items.TNT || item == Items.SHIELD
            || item == Items.MILK_BUCKET || item == Items.BUCKET || item == Items.TOTEM_OF_UNDYING
            || item == Items.COBWEB || item == Items.FLINT_AND_STEEL
            || isAnyBreedingItem(item) || isFishingRod(item)
            || EXTRA_USABLE.contains(item) || isBanner(item) || isPickaxe(item)
            || item == Items.GOAT_HORN || PotterySherdSpells.isPotterySherd(item);
    }

    /**
     * Use the player's held item. Returns a message describing what happened,
     * or null if the item can't be used.
     */
    public static String useItem(ServerPlayerEntity player, GridArena arena, GridPos targetTile) {
        ItemStack held = player.getMainHandStack();
        Item item = held.getItem();

        if (item == Items.GOAT_HORN) {
            return useGoatHorn(arena, held);
        } else if (isFood(item)) {
            return useFood(player, held, item);
        } else if (isPotion(item)) {
            return useDrinkPotion(player, held);
        } else if (item == Items.SNOWBALL) {
            return useSnowball(player, arena, targetTile, held);
        } else if (item == Items.EGG) {
            return useEgg(player, arena, targetTile, held, 1);
        //? if >=1.21.5 {
        /*} else if (item == net.minecraft.item.Items.BLUE_EGG || item == net.minecraft.item.Items.BROWN_EGG) {
            return useEgg(player, arena, targetTile, held, 3);
        *///?}
        } else if (item == Items.ENDER_PEARL) {
            return useEnderPearl(player, arena, targetTile, held);
        } else if (isSplashPotion(item)) {
            return useSplashPotion(player, arena, targetTile, held);
        } else if (item == Items.FIRE_CHARGE) {
            return useFireCharge(player, arena, targetTile, held);
        } else if (item == Items.WIND_CHARGE) {
            return useWindCharge(player, arena, targetTile, held);
        } else if (item == Items.MILK_BUCKET) {
            return useMilkBucket(player, held);
        } else if (item == Items.TNT) {
            return useTNT(player, arena, targetTile, held);
        } else if (item == Items.COBWEB) {
            return useCobweb(player, arena, targetTile, held);
        } else if (item == Items.SHIELD) {
            return "§9Shield is passive! Equip in offhand for +1 DEF and 25% chance to block attacks.";
        } else if (item == Items.FLINT_AND_STEEL) {
            return useFlintAndSteel(player, arena, targetTile, held);
        } else if (item == Items.TOTEM_OF_UNDYING) {
            return useTotem(player, held);
        } else if (isAnyBreedingItem(item)) {
            return useBreedingItem(player, arena, targetTile, held);
        } else if (isFishingRod(item)) {
            return useFishingRod(player, arena, targetTile, held);
        } else if (item == Items.TURTLE_EGG || item == Items.PUFFERFISH || item == Items.NAUTILUS_SHELL || item == Items.HEART_OF_THE_SEA) {
            return useWaterThrowable(player, arena, targetTile, held, item);
        } else if (item == Items.SPYGLASS) {
            return useSpyglass(arena, targetTile);
        } else if (item == Items.COMPASS) {
            return useCompass(arena);
        } else if (item == Items.BELL) {
            return useBell(arena, targetTile);
        } else if (item == Items.LAVA_BUCKET) {
            return useLavaBucket(arena, targetTile, held);
        } else if (item == Items.SCAFFOLDING) {
            return useScaffolding(arena, targetTile, held);
        } else if (item == Items.CAMPFIRE) {
            return useCampfire(arena, targetTile, held);
        } else if (item == Items.ANVIL) {
            return useAnvil(player, arena, targetTile, held);
        } else if (item == Items.HONEY_BLOCK) {
            return useHoneyBlock(arena, targetTile, held);
        } else if (item == Items.POWDER_SNOW_BUCKET) {
            return usePowderSnow(player, arena, targetTile, held);
        } else if (item == Items.JUKEBOX) {
            return useJukebox(arena, held);
        } else if (isBanner(item)) {
            return useBanner(arena, targetTile, held);
        } else if (item == Items.WATER_BUCKET) {
            return useWaterBucket(arena, targetTile, held);
        } else if (item == Items.BUCKET) {
            return useEmptyBucket(arena, targetTile, held);
        } else if (item == Items.SPONGE) {
            return useSponge(arena, targetTile, held);
        } else if (isPickaxe(item)) {
            return usePickaxe(arena, targetTile, held);
        } else if (item == Items.CROSSBOW) {
            return useCrossbow(arena, targetTile, held);
        } else if (item == Items.LINGERING_POTION) {
            return useLingeringPotion(player, arena, targetTile, held);
        } else if (item == Items.LIGHTNING_ROD) {
            return useLightningRod(arena, targetTile, held);
        } else if (item == Items.CACTUS) {
            return useCactus(arena, targetTile, held);
        } else if (item == Items.HAY_BLOCK) {
            return useHayBale(arena, targetTile, held);
        } else if (item == Items.CAKE) {
            return useCake(arena, targetTile, held);
        } else if (item == Items.SPORE_BLOSSOM) {
            return useSporeBlossom(arena, targetTile, held);
        } else if (item == Items.LANTERN) {
            return useLantern(arena, targetTile, held);
        } else if (item == Items.TORCH) {
            return useTorch(arena, targetTile, held);
        } else if (item == Items.ECHO_SHARD) {
            return useEchoShard(arena, held);
        } else if (item == Items.BRUSH) {
            return useBrush(player, arena, targetTile, held);
        }

        return null;
    }

    /** Prefix for fishing results — CombatManager deducts extra AP. */
    public static final String FISHING_PREFIX = "§bFISH:";
    public static final int FISHING_AP_COST = 3;

    /** Prefix for tile effects — CombatManager processes these. Format: TILE:type:x:z */
    public static final String TILE_EFFECT_PREFIX = "§eTILE:";
    /** Prefix for ally buff — CombatManager processes this. */
    public static final String ALLY_BUFF_PREFIX = "§dBUFF:";

    private static boolean isMoveFeather(ItemStack s) {
        return s.getItem() == Items.FEATHER && s.contains(net.minecraft.component.DataComponentTypes.CUSTOM_NAME);
    }

    private static String useFood(ServerPlayerEntity player, ItemStack stack, Item food) {
        // Eternal Steak / Everlasting Beef (Artifacts mod) — non-consuming food.
        // Per the Craftics × Artifacts spec: 2 AP, heals 3 HP, doesn't decrement the stack.
        if (isArtifactsNonConsumingFood(food)) {
            float curMax = player.getMaxHealth();
            float curHp = player.getHealth();
            int heal = 3;
            float newHp = Math.min(curMax, curHp + heal);
            player.setHealth(newHp);
            String displayName = stack.getName().getString();
            return "§dAte " + displayName + " — healed " + heal + " HP §7(it's eternal!)";
        }

        int healAmount = FOOD_HEAL.getOrDefault(food, 1);
        float maxHealth = player.getMaxHealth();
        float newHealth = Math.min(maxHealth, player.getHealth() + healAmount);
        player.setHealth(newHealth);
        stack.decrement(1);

        // Risky foods — apply debuff effects via the hooked path so addon
        // immunities (e.g. Antidote Vessel) can intercept them.
        CombatManager cm = CombatManager.get(player);
        java.util.Random rng = java.util.concurrent.ThreadLocalRandom.current();
        if (food == Items.POISONOUS_POTATO && rng.nextFloat() < 0.60f) {
            boolean applied = cm.addEffectHooked(CombatEffects.EffectType.POISON, 2, 0);
            if (applied) {
                player.addStatusEffect(new StatusEffectInstance(net.minecraft.entity.effect.StatusEffects.POISON, -1, 0, false, true));
                return "§aAte Poisonous Potato! Healed " + healAmount + " HP §c(Poisoned for 2 turns!)";
            }
            return "§aAte Poisonous Potato! Healed " + healAmount + " HP §a(Poison resisted!)";
        } else if (food == Items.SPIDER_EYE) {
            boolean applied = cm.addEffectHooked(CombatEffects.EffectType.POISON, 2, 0);
            if (applied) {
                player.addStatusEffect(new StatusEffectInstance(net.minecraft.entity.effect.StatusEffects.POISON, -1, 0, false, true));
                return "§aAte Spider Eye! Healed " + healAmount + " HP §c(Poisoned for 2 turns!)";
            }
            return "§aAte Spider Eye! Healed " + healAmount + " HP §a(Poison resisted!)";
        } else if (food == Items.ROTTEN_FLESH && rng.nextFloat() < 0.80f) {
            boolean applied = cm.addEffectHooked(CombatEffects.EffectType.WEAKNESS, 2, 0);
            if (applied) {
                player.addStatusEffect(new StatusEffectInstance(net.minecraft.entity.effect.StatusEffects.WEAKNESS, -1, 0, false, true));
                return "§aAte Rotten Flesh! Healed " + healAmount + " HP §c(Weakened for 2 turns!)";
            }
            return "§aAte Rotten Flesh! Healed " + healAmount + " HP §a(Weakness resisted!)";
        }

        // Golden apple also gives absorption
        if (food == Items.GOLDEN_APPLE) {
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 2400, 0));
        } else if (food == Items.ENCHANTED_GOLDEN_APPLE) {
            player.setHealth(maxHealth);
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 2400, 3));
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 6000, 0));
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 600, 1));
        }

        // Golden carrot restores 1 AP (capped at max AP)
        if (food == Items.GOLDEN_CARROT) {
            PlayerProgression.PlayerStats gcStats = PlayerProgression.get(
                (net.minecraft.server.world.ServerWorld) player.getEntityWorld()).getStats(player);
            int maxAp = gcStats.getEffective(PlayerProgression.Stat.AP)
                + PlayerCombatStats.getSetApBonus(player);
            if (cm.getApRemaining() >= maxAp) {
                // Refund the food — AP is already full, undo the heal + consumption
                player.setHealth(player.getHealth() - healAmount);
                stack.increment(1);
                return "§cAP is already full!";
            }
            cm.setApRemaining(Math.min(maxAp, cm.getApRemaining() + 1));
            return "§aAte " + stack.getName().getString() + "! Healed " + healAmount + " HP, §b+1 AP §a(HP: "
                + (int) newHealth + "/" + (int) maxHealth + ")";
        }

        return "§aAte " + stack.getName().getString() + "! Healed " + healAmount + " HP (HP: "
            + (int) newHealth + "/" + (int) maxHealth + ")";
    }

    private static String useDrinkPotion(ServerPlayerEntity player, ItemStack stack) {
        CombatEffects effects = CombatManager.get(player).getCombatEffects();
        // Read potion contents BEFORE decrementing (decrement destroys component data)
        var potionContents = stack.get(net.minecraft.component.DataComponentTypes.POTION_CONTENTS);
        stack.decrement(1);
        player.getInventory().insertStack(new ItemStack(Items.GLASS_BOTTLE));

        // Drinking sound
        //? if <=1.21.1 {
        player.getWorld().playSound(null, player.getBlockPos(),
            net.minecraft.sound.SoundEvents.ENTITY_GENERIC_DRINK,
            net.minecraft.sound.SoundCategory.PLAYERS, 1.0f, 1.0f);
        //?} else {
        /*player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
            net.minecraft.sound.SoundEvents.ENTITY_GENERIC_DRINK,
            net.minecraft.sound.SoundCategory.PLAYERS, 1.0f, 1.0f);
        *///?}

        // Swirl particles on player
        net.minecraft.server.world.ServerWorld sw = (net.minecraft.server.world.ServerWorld) player.getEntityWorld();
        sw.spawnParticles(net.minecraft.particle.ParticleTypes.WITCH,
            player.getX(), player.getY() + 1.0, player.getZ(), 12, 0.3, 0.5, 0.3, 0.05);
        sw.spawnParticles(net.minecraft.particle.ParticleTypes.EFFECT,
            player.getX(), player.getY() + 0.8, player.getZ(), 8, 0.2, 0.4, 0.2, 0.02);

        // Apply potion effects (contents read before decrement above)
        if (potionContents != null) {
            StringBuilder applied = new StringBuilder();
            for (StatusEffectInstance sei : potionContents.getEffects()) {
                var effectType = sei.getEffectType().value();
                CombatEffects.EffectType combatType = mapStatusEffect(effectType);
                if (combatType != null) {
                    int scaledAmplifier = getScaledPotionAmplifier(player, sei.getAmplifier());
                    int turns = getScaledPotionTurns(player, combatType, sei.getDuration());
                    effects.addEffect(combatType, turns, scaledAmplifier);
                    // Apply vanilla effect for particles (infinite duration, removed when combat effect expires)
                    player.addStatusEffect(new StatusEffectInstance(
                        sei.getEffectType(), -1, scaledAmplifier, false, true));
                    if (applied.length() > 0) applied.append(", ");
                    applied.append(combatType.displayName)
                        .append(formatPotionLevel(scaledAmplifier))
                        .append(" (").append(turns).append("t)");
                }
                // Instant effects
                if (effectType == StatusEffects.INSTANT_HEALTH.value()) {
                    int healAmount = 4 * (sei.getAmplifier() + 1) + getSpecialAffinityPoints(player);
                    player.setHealth(Math.min(player.getMaxHealth(), player.getHealth() + healAmount));
                    if (applied.length() > 0) applied.append(", ");
                    applied.append("Healed ").append(healAmount);
                    // Heart particles for healing
                    sw.spawnParticles(net.minecraft.particle.ParticleTypes.HEART,
                        player.getX(), player.getY() + 1.5, player.getZ(), 6, 0.4, 0.3, 0.4, 0.05);
                }
            }
            if (applied.length() > 0) {
                return "§dDrank potion! " + applied;
            }
        }
        return "§dDrank potion!";
    }

    private static CombatEffects.EffectType mapStatusEffect(net.minecraft.entity.effect.StatusEffect effect) {
        // Buffs
        if (effect == StatusEffects.SPEED.value()) return CombatEffects.EffectType.SPEED;
        if (effect == StatusEffects.STRENGTH.value()) return CombatEffects.EffectType.STRENGTH;
        if (effect == StatusEffects.RESISTANCE.value()) return CombatEffects.EffectType.RESISTANCE;
        if (effect == StatusEffects.REGENERATION.value()) return CombatEffects.EffectType.REGENERATION;
        if (effect == StatusEffects.FIRE_RESISTANCE.value()) return CombatEffects.EffectType.FIRE_RESISTANCE;
        if (effect == StatusEffects.INVISIBILITY.value()) return CombatEffects.EffectType.INVISIBILITY;
        if (effect == StatusEffects.ABSORPTION.value()) return CombatEffects.EffectType.ABSORPTION;
        if (effect == StatusEffects.LUCK.value()) return CombatEffects.EffectType.LUCK;
        if (effect == StatusEffects.SLOW_FALLING.value()) return CombatEffects.EffectType.SLOW_FALLING;
        if (effect == StatusEffects.HASTE.value()) return CombatEffects.EffectType.HASTE;
        if (effect == StatusEffects.WATER_BREATHING.value()) return CombatEffects.EffectType.WATER_BREATHING;
        // Debuffs
        if (effect == StatusEffects.POISON.value()) return CombatEffects.EffectType.POISON;
        if (effect == StatusEffects.SLOWNESS.value()) return CombatEffects.EffectType.SLOWNESS;
        if (effect == StatusEffects.WEAKNESS.value()) return CombatEffects.EffectType.WEAKNESS;
        if (effect == StatusEffects.WITHER.value()) return CombatEffects.EffectType.WITHER;
        if (effect == StatusEffects.BLINDNESS.value()) return CombatEffects.EffectType.BLINDNESS;
        if (effect == StatusEffects.MINING_FATIGUE.value()) return CombatEffects.EffectType.MINING_FATIGUE;
        if (effect == StatusEffects.LEVITATION.value()) return CombatEffects.EffectType.LEVITATION;
        if (effect == StatusEffects.DARKNESS.value()) return CombatEffects.EffectType.DARKNESS;
        return null;
    }

    private static boolean isBuffEffect(CombatEffects.EffectType type) {
        return switch (type) {
            case SPEED, STRENGTH, RESISTANCE, REGENERATION, FIRE_RESISTANCE,
                 INVISIBILITY, ABSORPTION, LUCK, SLOW_FALLING, HASTE, WATER_BREATHING -> true;
            default -> false;
        };
    }

    private static int getTurnsForPotion(CombatEffects.EffectType type, int vanillaDurationTicks) {
        int baseTurns = switch (type) {
            case SPEED, STRENGTH, RESISTANCE, ABSORPTION -> 5;
            case REGENERATION -> 3;
            case FIRE_RESISTANCE, INVISIBILITY, WATER_BREATHING -> 4;
            case POISON, SLOWNESS, WEAKNESS -> 3;
            case WITHER, BURNING -> 3;
            case LUCK, HASTE, SLOW_FALLING -> 4;
            case BLINDNESS, MINING_FATIGUE, LEVITATION, DARKNESS -> 3;
            case SOAKED, CONFUSION -> 3;
            case BLEEDING -> 3;
        };
        // Extended potions (long vanilla duration > 4 min) double the combat turn count
        if (vanillaDurationTicks > 4800) {
            baseTurns *= 2;
        }
        return Math.min(baseTurns, com.crackedgames.craftics.CrafticsMod.CONFIG.maxCombatEffectDuration());
    }

    private static int getTurnsForPotion(CombatEffects.EffectType type) {
        return getTurnsForPotion(type, 0);
    }

    private static PlayerProgression.PlayerStats getPlayerStats(ServerPlayerEntity player) {
        return PlayerProgression.get((ServerWorld) player.getEntityWorld()).getStats(player);
    }

    private static int getSpecialAffinityPoints(ServerPlayerEntity player) {
        return getPlayerStats(player).getAffinityPoints(PlayerProgression.Affinity.SPECIAL);
    }

    private static int getPotionPotencyBonus(ServerPlayerEntity player) {
        return getSpecialAffinityPoints(player) / 3;
    }

    private static int getPotionDurationBonus(ServerPlayerEntity player) {
        int specialPoints = getSpecialAffinityPoints(player);
        return specialPoints > 0 ? 1 + ((specialPoints - 1) / 4) : 0;
    }

    private static int getScaledPotionTurns(ServerPlayerEntity player, CombatEffects.EffectType type,
                                            int vanillaDurationTicks) {
        int turns = getTurnsForPotion(type, vanillaDurationTicks) + getPotionDurationBonus(player);
        return Math.min(turns, com.crackedgames.craftics.CrafticsMod.CONFIG.maxCombatEffectDuration());
    }

    private static int getScaledPotionAmplifier(ServerPlayerEntity player, int vanillaAmplifier) {
        return vanillaAmplifier + getPotionPotencyBonus(player);
    }

    private static int getTypedDamageBonus(ServerPlayerEntity player, CombatEffects effects, DamageType type) {
        return DamageType.getTotalBonus(
            PlayerCombatStats.getArmorSet(player), CombatManager.get(player).getTrimScan(), effects,
            type, getPlayerStats(player))
            + DamageType.getMobHeadBonus(
                player.getEquippedStack(net.minecraft.entity.EquipmentSlot.HEAD), type);
    }

    private static int applyTypedDamage(ServerPlayerEntity player, CombatEntity target, int baseDamage,
                                        DamageType type) {
        CombatEffects effects = CombatManager.get(player).getCombatEffects();
        int rawDamage = baseDamage + getTypedDamageBonus(player, effects, type);
        int adjustedDamage = MobResistances.applyResistance(target.getEntityTypeId(), type, rawDamage);
        if (adjustedDamage <= 0) {
            return 0;
        }
        return target.takeDamage(adjustedDamage);
    }

    private static String formatPotionLevel(int amplifier) {
        return switch (amplifier) {
            case 0 -> "";
            case 1 -> " II";
            case 2 -> " III";
            case 3 -> " IV";
            case 4 -> " V";
            default -> " " + (amplifier + 1);
        };
    }

    private static String useSnowball(ServerPlayerEntity player, GridArena arena,
                                       GridPos targetTile, ItemStack stack) {
        if (targetTile == null) return "§cNeed to target a tile!";
        CombatEntity enemy = arena.getOccupant(targetTile);
        if (enemy == null || !enemy.isAlive()) return "§cNo enemy at target!";

        stack.decrement(1);
        int dealt = applyTypedDamage(player, enemy, 1, DamageType.SPECIAL);

        // Knockback: push enemy 1 tile away from player
        GridPos playerPos = arena.getPlayerGridPos();
        int dx = Integer.signum(enemy.getGridPos().x() - playerPos.x());
        int dz = Integer.signum(enemy.getGridPos().z() - playerPos.z());
        GridPos knockbackPos = new GridPos(enemy.getGridPos().x() + dx, enemy.getGridPos().z() + dz);

        if (arena.isInBounds(knockbackPos) && !arena.isOccupied(knockbackPos)) {
            var tile = arena.getTile(knockbackPos);
            if (tile != null && tile.isWalkable()) {
                arena.moveEntity(enemy, knockbackPos);
                if (enemy.getMobEntity() != null) {
                    var bp = arena.gridToBlockPos(knockbackPos);
                    enemy.getMobEntity().requestTeleport(bp.getX() + 0.5, bp.getY(), bp.getZ() + 0.5);
                }
                return "§bSnowball hit! " + enemy.getDisplayName() + " took " + dealt + " Special damage and was knocked back!";
            }
        }
        return "§bSnowball hit " + enemy.getDisplayName() + " for " + dealt + " Special damage! (no knockback room)";
    }

    private static String useEgg(ServerPlayerEntity player, GridArena arena,
                                  GridPos targetTile, ItemStack stack, int damage) {
        if (targetTile == null) return "§cNeed to target a tile!";
        CombatEntity enemy = arena.getOccupant(targetTile);
        if (enemy == null || !enemy.isAlive()) return "§cNo enemy at target!";

        stack.decrement(1);
        int dealt = applyTypedDamage(player, enemy, damage, DamageType.SPECIAL);
        return "§eEgg hit " + enemy.getDisplayName() + " for " + dealt + " Special damage!";
    }

    private static String useEnderPearl(ServerPlayerEntity player, GridArena arena,
                                         GridPos targetTile, ItemStack stack) {
        if (targetTile == null) return "§cNeed to target a tile!";
        if (!arena.isInBounds(targetTile)) return "§cTarget out of bounds!";
        if (arena.isOccupied(targetTile)) return "§cTile is occupied!";
        var tile = arena.getTile(targetTile);
        if (tile == null || !tile.isWalkable()) return "§cCan't teleport there!";

        stack.decrement(1);
        arena.setPlayerGridPos(targetTile);
        var bp = arena.gridToBlockPos(targetTile);
        player.requestTeleport(bp.getX() + 0.5, bp.getY(), bp.getZ() + 0.5);

        // Ender pearl costs 2 HP
        player.setHealth(Math.max(1, player.getHealth() - 2));

        return "§5Teleported to (" + targetTile.x() + "," + targetTile.z() + ")! Took 2 damage.";
    }

    /**
     * Read active potion effects and return combat stat modifiers.
     * Call this each turn to update combat stats.
     */
    public static int getSpeedBonus(ServerPlayerEntity player) {
        if (player.hasStatusEffect(StatusEffects.SPEED)) return 2;
        return 0;
    }

    public static int getStrengthBonus(ServerPlayerEntity player) {
        if (player.hasStatusEffect(StatusEffects.STRENGTH)) return 3;
        return 0;
    }

    public static int getResistanceBonus(ServerPlayerEntity player) {
        if (player.hasStatusEffect(StatusEffects.RESISTANCE)) return 2;
        return 0;
    }

    public static boolean hasFireResistance(ServerPlayerEntity player) {
        return player.hasStatusEffect(StatusEffects.FIRE_RESISTANCE);
    }

    public static boolean isInvisible(ServerPlayerEntity player) {
        return player.hasStatusEffect(StatusEffects.INVISIBILITY);
    }

    // --- Splash Potion: throw at target tile, applies effects to enemy ---
    private static final int POTION_THROW_RANGE = 4;

    private static String useSplashPotion(ServerPlayerEntity player, GridArena arena,
                                           GridPos targetTile, ItemStack stack) {
        if (targetTile == null) return "§cNeed to target a tile!";
        if (arena.getPlayerGridPos().manhattanDistance(targetTile) > POTION_THROW_RANGE) {
            return "§cToo far! Max throw range is " + POTION_THROW_RANGE + " tiles.";
        }
        CombatEntity enemy = arena.getOccupant(targetTile);

        // Read potion contents BEFORE decrementing (decrement destroys component data)
        var potionContents = stack.get(net.minecraft.component.DataComponentTypes.POTION_CONTENTS);
        stack.decrement(1);

        net.minecraft.server.world.ServerWorld sw = (net.minecraft.server.world.ServerWorld) player.getEntityWorld();
        BlockPos playerBlock = arena.gridToBlockPos(arena.getPlayerGridPos());
        BlockPos targetBlock = arena.gridToBlockPos(targetTile);

        // Throw sound
        sw.playSound(null, player.getBlockPos(),
            net.minecraft.sound.SoundEvents.ENTITY_SPLASH_POTION_THROW,
            net.minecraft.sound.SoundCategory.PLAYERS, 1.0f, 1.0f);

        // Throw arc trail particles (potion bottle flying through air)
        ProjectileSpawner.spawnPotionThrow(sw, playerBlock, targetBlock);

        if (potionContents != null) {
            CombatEffects effects = CombatManager.get(player).getCombatEffects();
            GridPos playerPos = arena.getPlayerGridPos();
            int specialDamageBonus = getTypedDamageBonus(player, effects, DamageType.SPECIAL);
            int hitCount = 0;
            StringBuilder msg = new StringBuilder();

            // Collect all enemies in 3x3 AoE (manhattan distance <= 1 from target)
            java.util.List<CombatEntity> aoeEnemies = new java.util.ArrayList<>();
            for (CombatEntity ce : arena.getOccupants().values()) {
                if (!ce.isAlive()) continue;
                if (ce.getGridPos().manhattanDistance(targetTile) <= 1) {
                    aoeEnemies.add(ce);
                }
            }
            // Deduplicate (multi-tile entities appear multiple times in occupants)
            aoeEnemies = new java.util.ArrayList<>(new java.util.LinkedHashSet<>(aoeEnemies));

            // Check if player is in the splash radius
            boolean playerInRange = playerPos.manhattanDistance(targetTile) <= 1;

            ProjectileSpawner.spawnPotionSplash(sw, targetBlock, aoeEnemies.isEmpty() && playerInRange);

            for (StatusEffectInstance sei : potionContents.getEffects()) {
                var effectType = sei.getEffectType().value();
                int amp = sei.getAmplifier();
                int scaledAmp = getScaledPotionAmplifier(player, amp);

                // === Apply debuffs to ALL enemies in 3x3 ===
                for (CombatEntity target : aoeEnemies) {
                    // Vanilla particles on mob
                    if (target.getMobEntity() != null) {
                        target.getMobEntity().addStatusEffect(new StatusEffectInstance(
                            sei.getEffectType(), -1, scaledAmp, false, true));
                    }
                    if (effectType == StatusEffects.INSTANT_DAMAGE.value()) {
                        int dealt = target.takeDamage(3 * (scaledAmp + 1) + specialDamageBonus);
                        msg.append("§5").append(target.getDisplayName()).append(" -").append(dealt).append("HP ");
                    } else if (effectType == StatusEffects.POISON.value()) {
                        target.takeDamage(1 + scaledAmp);
                        int poisonTurns = getScaledPotionTurns(player, CombatEffects.EffectType.POISON, sei.getDuration());
                        target.stackPoison(poisonTurns, scaledAmp + 1);
                        msg.append("§2").append(target.getDisplayName()).append(" poisoned ");
                    } else if (effectType == StatusEffects.SLOWNESS.value()) {
                        target.setSpeedBonus(target.getSpeedBonus() - (1 + scaledAmp));
                        msg.append("§7").append(target.getDisplayName()).append(" slowed ");
                    } else if (effectType == StatusEffects.WEAKNESS.value()) {
                        target.setAttackPenalty(target.getAttackPenalty() + 2 + scaledAmp);
                        msg.append("§8").append(target.getDisplayName()).append(" weakened ");
                    } else if (effectType == StatusEffects.WITHER.value()) {
                        target.takeDamage(2 + scaledAmp + specialDamageBonus);
                        msg.append("§8").append(target.getDisplayName()).append(" withered ");
                    } else if (effectType == StatusEffects.BLINDNESS.value() || effectType == StatusEffects.DARKNESS.value()) {
                        target.setStunned(true);
                        msg.append("§8").append(target.getDisplayName()).append(" stunned ");
                    } else if (effectType == StatusEffects.LEVITATION.value()) {
                        target.setSpeedBonus(target.getSpeedBonus() - (1 + scaledAmp));
                        msg.append("§d").append(target.getDisplayName()).append(" levitating ");
                    }
                    hitCount++;
                }

                // === Apply buffs to player if in range ===
                if (playerInRange) {
                    CombatEffects.EffectType combatType = mapStatusEffect(effectType);
                    if (effectType == StatusEffects.INSTANT_HEALTH.value()) {
                        int heal = 4 * (amp + 1) + getSpecialAffinityPoints(player);
                        player.setHealth(Math.min(player.getMaxHealth(), player.getHealth() + heal));
                        msg.append("§a+").append(heal).append("HP ");
                        hitCount++;
                    } else if (combatType != null && isBuffEffect(combatType)) {
                        int turns = getScaledPotionTurns(player, combatType, sei.getDuration());
                        effects.addEffect(combatType, turns, scaledAmp);
                        player.addStatusEffect(new StatusEffectInstance(
                            sei.getEffectType(), -1, scaledAmp, false, true));
                        msg.append("§d").append(combatType.displayName)
                            .append(formatPotionLevel(scaledAmp)).append(" ");
                        hitCount++;
                    }
                }
            }

            if (hitCount > 0) {
                // Check deaths from AoE
                for (CombatEntity ce : aoeEnemies) {
                    CombatManager.get(player).checkAndHandleDeathPublic(ce);
                }
                return "§5Splash hit " + hitCount + "! " + msg.toString().trim();
            }
        }
        ProjectileSpawner.spawnPotionSplash(sw, targetBlock, false);
        return "§dThrew splash potion!";
    }

    // --- Fire Charge: ranged fire damage to a target ---
    private static String useFireCharge(ServerPlayerEntity player, GridArena arena,
                                         GridPos targetTile, ItemStack stack) {
        if (targetTile == null) return "§cNeed to target a tile!";
        CombatEntity enemy = arena.getOccupant(targetTile);
        if (enemy == null || !enemy.isAlive()) return "§cNo enemy at target!";

        stack.decrement(1);
        int dealt = applyTypedDamage(player, enemy, 4, DamageType.SPECIAL);
        // Set mob on fire visually for 3 seconds
        if (enemy.getMobEntity() != null) {
            enemy.getMobEntity().setFireTicks(60);
        }
        return "§6Fire charge hit " + enemy.getDisplayName() + " for " + dealt + " Special damage!";
    }

    // --- Wind Charge: 1 Special damage + 3-tile knockback away from the player ---
    private static String useWindCharge(ServerPlayerEntity player, GridArena arena,
                                         GridPos targetTile, ItemStack stack) {
        if (targetTile == null) return "§cNeed to target a tile!";
        CombatEntity enemy = arena.getOccupant(targetTile);
        if (enemy == null || !enemy.isAlive()) return "§cNo enemy at target!";

        stack.decrement(1);
        int dealt = applyTypedDamage(player, enemy, 1, DamageType.SPECIAL);

        // Push the enemy up to 3 tiles directly away from the player. Stops
        // early on walls, obstacles, or other occupants; lands ON the final
        // walkable tile in the push direction.
        GridPos playerPos = arena.getPlayerGridPos();
        GridPos enemyStart = enemy.getGridPos();
        int dx = Integer.signum(enemyStart.x() - playerPos.x());
        int dz = Integer.signum(enemyStart.z() - playerPos.z());
        if (dx == 0 && dz == 0) { dx = 1; }

        GridPos landing = enemyStart;
        int pushed = 0;
        for (int i = 1; i <= 3; i++) {
            GridPos candidate = new GridPos(enemyStart.x() + dx * i, enemyStart.z() + dz * i);
            if (!arena.isInBounds(candidate) || arena.isOccupied(candidate)) break;
            GridTile tile = arena.getTile(candidate);
            if (tile == null || !tile.isWalkable()) break;
            landing = candidate;
            pushed = i;
        }

        if (pushed > 0) {
            arena.moveEntity(enemy, landing);
            if (enemy.getMobEntity() != null) {
                BlockPos bp = arena.gridToBlockPos(landing);
                enemy.getMobEntity().requestTeleport(bp.getX() + 0.5, bp.getY(), bp.getZ() + 0.5);
            }

            // Wind burst particles along the push path
            if (player.getEntityWorld() instanceof ServerWorld sw) {
                BlockPos bp = arena.gridToBlockPos(landing);
                sw.spawnParticles(net.minecraft.particle.ParticleTypes.GUST,
                    bp.getX() + 0.5, bp.getY() + 1.0, bp.getZ() + 0.5,
                    6, 0.4, 0.4, 0.4, 0.01);
                sw.spawnParticles(net.minecraft.particle.ParticleTypes.SWEEP_ATTACK,
                    bp.getX() + 0.5, bp.getY() + 1.0, bp.getZ() + 0.5,
                    2, 0.2, 0.2, 0.2, 0.0);
                sw.playSound(null, bp,
                    net.minecraft.sound.SoundEvents.ENTITY_WIND_CHARGE_WIND_BURST.value(),
                    net.minecraft.sound.SoundCategory.PLAYERS, 1.0f, 1.0f);
            }

            return "§fWind charge! " + enemy.getDisplayName() + " took " + dealt
                + " Special damage and was blown back " + pushed + " tile"
                + (pushed == 1 ? "" : "s") + "!";
        }
        return "§fWind charge hit " + enemy.getDisplayName() + " for " + dealt
            + " Special damage! (no room to push)";
    }

    // --- Milk Bucket: clears all status effects (good and bad) ---
    private static String useMilkBucket(ServerPlayerEntity player, ItemStack stack) {
        stack.decrement(1);
        player.getInventory().insertStack(new ItemStack(Items.BUCKET));
        CombatEffects effects = CombatManager.get(player).getCombatEffects();
        effects.clear();
        player.clearStatusEffects();
        return "§fDrank milk! All effects cleared.";
    }

    // --- TNT: place on target tile, explodes at the start of next round ---
    private static String useTNT(ServerPlayerEntity player, GridArena arena,
                                  GridPos targetTile, ItemStack stack) {
        if (targetTile == null) return "§cNeed to target a tile!";
        if (!arena.isInBounds(targetTile)) return "§cTarget out of bounds!";

        // Place a physical TNT block on the arena floor
        net.minecraft.server.world.ServerWorld world = (net.minecraft.server.world.ServerWorld) player.getEntityWorld();
        net.minecraft.util.math.BlockPos bp = arena.gridToBlockPos(targetTile);
        world.setBlockState(bp, net.minecraft.block.Blocks.TNT.getDefaultState());

        // Fuse particles — smoke rising from the placed TNT
        world.spawnParticles(net.minecraft.particle.ParticleTypes.SMOKE,
            bp.getX() + 0.5, bp.getY() + 1.0, bp.getZ() + 0.5,
            8, 0.2, 0.3, 0.2, 0.01);
        world.spawnParticles(net.minecraft.particle.ParticleTypes.FLAME,
            bp.getX() + 0.5, bp.getY() + 0.9, bp.getZ() + 0.5,
            3, 0.05, 0.05, 0.05, 0.0);

        // Play TNT fuse sound
        world.playSound(null, bp,
            net.minecraft.sound.SoundEvents.ENTITY_TNT_PRIMED,
            net.minecraft.sound.SoundCategory.BLOCKS, 1.0f, 1.0f);

        stack.decrement(1);
        // Return special prefix so CombatManager tracks this for detonation
        return TNT_PREFIX + targetTile.x() + "," + targetTile.z();
    }

    // --- Cobweb: slows an enemy (stuns for 1 turn) ---
    private static String useCobweb(ServerPlayerEntity player, GridArena arena,
                                     GridPos targetTile, ItemStack stack) {
        if (targetTile == null) return "§cNeed to target a tile!";
        CombatEntity enemy = arena.getOccupant(targetTile);
        if (enemy == null || !enemy.isAlive()) return "§cNo enemy at target!";

        stack.decrement(1);
        enemy.setStunned(true);
        return "§7Webbed " + enemy.getDisplayName() + "! They skip their next turn.";
    }

    // --- Flint and Steel: sets target enemy on fire, damage over time ---
    private static String useFlintAndSteel(ServerPlayerEntity player, GridArena arena,
                                            GridPos targetTile, ItemStack stack) {
        if (targetTile == null) return "§cNeed to target a tile!";
        CombatEntity enemy = arena.getOccupant(targetTile);
        if (enemy == null || !enemy.isAlive()) return "§cNo enemy at target!";

        // Durability cost
        if (stack.getDamage() + 1 >= stack.getMaxDamage()) {
            stack.decrement(1);
        } else {
            stack.setDamage(stack.getDamage() + 1);
        }
        int dealt = applyTypedDamage(player, enemy, 2, DamageType.SPECIAL);
        if (enemy.getMobEntity() != null) {
            enemy.getMobEntity().setFireTicks(100); // 5 seconds of fire
        }
        return "§6Set " + enemy.getDisplayName() + " on fire! " + dealt + " Special damage.";
    }

    // --- Totem of Undying: prevents death once, full heal ---
    private static String useTotem(ServerPlayerEntity player, ItemStack stack) {
        stack.decrement(1);
        player.setHealth(player.getMaxHealth());
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 900, 1));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 100, 1));
        return "§6§lTotem activated! Full heal + Regeneration!";
    }

    private static boolean hasSaddle(ServerPlayerEntity player) {
        for (int i = 0; i < player.getInventory().size(); i++) {
            if (player.getInventory().getStack(i).getItem() == Items.SADDLE) return true;
        }
        return false;
    }

    private static void consumeSaddle(ServerPlayerEntity player) {
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.getItem() == Items.SADDLE) {
                stack.decrement(1);
                return;
            }
        }
    }

    // --- Fishing Rod: cast into adjacent water tile for random loot (3 AP) ---
    private static String useFishingRod(ServerPlayerEntity player, GridArena arena,
                                         GridPos targetTile, ItemStack stack) {
        if (targetTile == null) return "§cNeed to target a water tile!";
        GridTile tile = arena.getTile(targetTile);
        if (tile == null || !tile.isWater()) return "§cThat's not a water tile!";

        // Must be adjacent (Manhattan distance 1)
        GridPos playerPos = arena.getPlayerGridPos();
        int dist = Math.abs(playerPos.x() - targetTile.x()) + Math.abs(playerPos.z() - targetTile.z());
        if (dist > 1) return "§cToo far! Stand next to the water.";

        // Durability cost
        if (stack.getDamage() + 1 >= stack.getMaxDamage()) {
            stack.decrement(1);
        } else {
            stack.setDamage(stack.getDamage() + 1);
        }

        // Random fishing loot
        java.util.Random rng = new java.util.Random();
        int roll = rng.nextInt(100);
        Item loot;
        int count = 1;
        String rarity;
        if (roll < 50) {
            // Common fish (50%)
            loot = switch (rng.nextInt(4)) {
                case 0 -> Items.PUFFERFISH;
                case 1 -> Items.SALMON;
                case 2 -> Items.TROPICAL_FISH;
                default -> Items.COD;
            };
            rarity = "§7";
        } else if (roll < 75) {
            // Useful items (25%)
            int usefulRoll = rng.nextInt(11);
            loot = switch (usefulRoll) {
                case 0 -> Items.BONE;
                case 1 -> Items.STRING;
                case 2 -> Items.LILY_PAD;
                case 3 -> Items.INK_SAC;
                case 4 -> Items.LEATHER;
                case 5 -> Items.GLOW_INK_SAC;
                case 6 -> Items.PRISMARINE_SHARD;
                case 7 -> Items.PRISMARINE_CRYSTALS;
                case 8 -> Items.OBSIDIAN;
                case 9 -> Items.SLIME_BALL;
                case 10 -> Items.NAME_TAG;
                default -> Items.TURTLE_EGG;
            };
            rarity = "§a";
        } else if (roll < 90) {
            // Good items (15%)
            int goodRoll = rng.nextInt(23);
            loot = switch (goodRoll) {
                case 0 -> Items.NAME_TAG;
                case 1 -> Items.SADDLE;
                case 2 -> Items.NAUTILUS_SHELL;
                case 3 -> Items.BOW;
                case 4 -> Items.ENCHANTED_BOOK;
                case 5 -> Items.TUBE_CORAL;
                case 6 -> Items.BRAIN_CORAL;
                case 7 -> Items.BUBBLE_CORAL;
                case 8 -> Items.FIRE_CORAL;
                case 9 -> Items.HORN_CORAL;
                case 10 -> Items.TUBE_CORAL_FAN;
                case 11 -> Items.BRAIN_CORAL_FAN;
                case 12 -> Items.BUBBLE_CORAL_FAN;
                case 13 -> Items.FIRE_CORAL_FAN;
                case 14 -> Items.HORN_CORAL_FAN;
                case 15 -> Items.DEAD_TUBE_CORAL;
                case 16 -> Items.DEAD_BRAIN_CORAL;
                case 17 -> Items.DEAD_BUBBLE_CORAL;
                case 18 -> Items.DEAD_FIRE_CORAL;
                case 19 -> Items.HEART_OF_THE_SEA;
                case 20 -> Items.TURTLE_SCUTE;
                case 21 -> Items.CROSSBOW;
                case 22 -> Items.TRIDENT;
                default -> Items.DEAD_HORN_CORAL;
            };
            rarity = "§b";
        } else {
            // Rare treasure (10%)
            loot = switch (rng.nextInt(8)) {
                case 0 -> Items.DIAMOND;
                case 1 -> Items.EMERALD;
                case 2 -> Items.GOLDEN_APPLE;
                case 3 -> Items.TURTLE_SCUTE;
                case 4 -> Items.ENCHANTED_GOLDEN_APPLE;
                case 5 -> Items.AXOLOTL_BUCKET;
                case 6 -> Items.WATER_BUCKET;
                case 7 -> Items.TRIDENT;
                default -> Items.HEART_OF_THE_SEA;
            };
            rarity = "§d";
        }

        LootDelivery.deliver(player, new ItemStack(loot, count));
        String lootName = new ItemStack(loot).getName().getString();
        return FISHING_PREFIX + rarity + "Caught: " + lootName + "!";
    }

    /** Prefix returned when taming/befriending succeeds — CombatManager processes this. */
    public static final String TAME_PREFIX = "§aTAME:";
    /** Prefix for passive mobs that get sent to the hub instead of becoming combat allies. */
    public static final String BEFRIEND_PREFIX = "§aBEFRIEND:";
    /** Prefix for mounting a tamed mob (horse/donkey/camel with saddle). */
    public static final String MOUNT_PREFIX = "§aMOUNT:";
    /** Prefix for TNT placement — CombatManager tracks the tile for delayed detonation. */
    public static final String TNT_PREFIX = "§6TNT:";

    /** Mobs that can be mounted in combat (require a saddle). */
    private static final Set<String> MOUNTABLE_MOBS = Set.of(
        "minecraft:horse", "minecraft:donkey", "minecraft:camel",
        "minecraft:mule", "minecraft:skeleton_horse", "minecraft:zombie_horse"
    );

    // --- Spyglass: reveal enemy stats (1 AP, no consume) ---
    private static String useSpyglass(GridArena arena, GridPos targetTile) {
        if (targetTile == null) return "§cNeed to target an enemy!";
        CombatEntity enemy = arena.getOccupant(targetTile);
        if (enemy == null || !enemy.isAlive()) return "§cNo enemy at target!";
        return "§e" + enemy.getDisplayName() + " — HP: " + enemy.getCurrentHp() + "/" + enemy.getMaxHp()
            + " | ATK: " + enemy.getAttackPower() + " | DEF: " + enemy.getDefense()
            + " | Range: " + enemy.getRange() + " | Speed: " + enemy.getMoveSpeed();
    }

    // --- Compass: reveal all enemy positions (1 AP, no consume) ---
    private static String useCompass(GridArena arena) {
        StringBuilder sb = new StringBuilder("§6Enemy positions: ");
        for (CombatEntity e : arena.getOccupants().values()) {
            if (e.isAlive() && !e.isAlly()) {
                sb.append(e.getDisplayName()).append("(")
                  .append(e.getGridPos().x()).append(",").append(e.getGridPos().z())
                  .append(") ");
            }
        }
        return sb.toString();
    }

    // --- Bell: AoE stun all enemies within 2 tiles of target (2 AP, no consume) ---
    private static String useBell(GridArena arena, GridPos targetTile) {
        if (targetTile == null) return "§cNeed to target a tile!";
        int stunned = 0;
        for (CombatEntity e : arena.getOccupants().values()) {
            if (!e.isAlive() || e.isAlly()) continue;
            int dist = Math.abs(e.getGridPos().x() - targetTile.x()) + Math.abs(e.getGridPos().z() - targetTile.z());
            if (dist <= 2) {
                e.setStunned(true);
                stunned++;
            }
        }
        return "§6Bell rings! " + stunned + " enemies stunned for 1 turn.";
    }

    // --- Lava Bucket: place lava on tile — deals damage to enemies that step on it (1 AP) ---
    private static String useLavaBucket(GridArena arena, GridPos targetTile, ItemStack stack) {
        if (targetTile == null) return "§cNeed to target a tile!";
        if (!arena.isInBounds(targetTile)) return "§cTarget out of bounds!";
        if (arena.isOccupied(targetTile)) return "§cTile is occupied!";
        stack.decrement(1);
        return TILE_EFFECT_PREFIX + "lava:" + targetTile.x() + ":" + targetTile.z()
            + "|§6Placed lava! Enemies on it take 3 fire damage per turn.";
    }

    // --- Scaffolding: place elevated tile — gives +1 range to ranged attacks from it (1 AP) ---
    private static String useScaffolding(GridArena arena, GridPos targetTile, ItemStack stack) {
        if (targetTile == null) return "§cNeed to target a tile!";
        if (!arena.isInBounds(targetTile)) return "§cTarget out of bounds!";
        stack.decrement(1);
        return TILE_EFFECT_PREFIX + "scaffold:" + targetTile.x() + ":" + targetTile.z()
            + "|§aPlaced scaffolding! +1 range for ranged attacks from this tile.";
    }

    // --- Campfire: place healing zone — heals 1 HP per turn when adjacent (1 AP) + creates light zone ---
    private static String useCampfire(GridArena arena, GridPos targetTile, ItemStack stack) {
        if (targetTile == null) return "§cNeed to target a tile!";
        if (!arena.isInBounds(targetTile)) return "§cTarget out of bounds!";
        stack.decrement(1);
        return TILE_EFFECT_PREFIX + "campfire:" + targetTile.x() + ":" + targetTile.z()
            + "|§6Placed campfire! Heals 1 HP per turn when standing on or next to it + creates light (negate darkness).";
    }

    // --- Anvil: drop on enemy — 5 damage, consumes item (1 AP) ---
    private static String useAnvil(ServerPlayerEntity player, GridArena arena, GridPos targetTile, ItemStack stack) {
        if (targetTile == null) return "§cNeed to target an enemy!";
        CombatEntity enemy = arena.getOccupant(targetTile);
        if (enemy == null || !enemy.isAlive()) return "§cNo enemy at target!";
        stack.decrement(1);
        int dealt = applyTypedDamage(player, enemy, 15, DamageType.SPECIAL);
        return "§8Anvil dropped on " + enemy.getDisplayName() + " for " + dealt + " Special damage!";
    }

    // --- Honey Block: place sticky trap — enemies lose all movement when stepping on it (1 AP) ---
    private static String useHoneyBlock(GridArena arena, GridPos targetTile, ItemStack stack) {
        if (targetTile == null) return "§cNeed to target a tile!";
        if (!arena.isInBounds(targetTile)) return "§cTarget out of bounds!";
        stack.decrement(1);
        return TILE_EFFECT_PREFIX + "honey:" + targetTile.x() + ":" + targetTile.z()
            + "|§eHoney block placed! Enemies that walk onto it lose all remaining movement.";
    }

    // --- Powder Snow Bucket: freeze enemy — stun + 1 cold damage (1 AP) ---
    private static String usePowderSnow(ServerPlayerEntity player, GridArena arena, GridPos targetTile, ItemStack stack) {
        if (targetTile == null) return "§cNeed to target an enemy!";
        CombatEntity enemy = arena.getOccupant(targetTile);
        if (enemy == null || !enemy.isAlive()) return "§cNo enemy at target!";
        stack.decrement(1);
        int dealt = applyTypedDamage(player, enemy, 1, DamageType.SPECIAL);
        enemy.setStunned(true);
        return "§bFrozen! " + enemy.getDisplayName() + " takes " + dealt + " Special damage and skips next turn.";
    }

    // --- Jukebox: play music — buff all allies +1 speed for this battle (2 AP, consumes) ---
    private static String useJukebox(GridArena arena, ItemStack stack) {
        stack.decrement(1);
        int buffed = 0;
        for (CombatEntity e : arena.getOccupants().values()) {
            if (e.isAlive() && e.isAlly()) {
                e.setSpeedBonus(e.getSpeedBonus() + 1);
                buffed++;
            }
        }
        return ALLY_BUFF_PREFIX + "music|§dMusic plays! " + buffed + " allies buffed (+1 speed for this battle).";
    }

    // --- Banner: plant defense zone — +2 defense to player/allies within 2 tiles (1 AP) ---
    private static String useBanner(GridArena arena, GridPos targetTile, ItemStack stack) {
        if (targetTile == null) return "§cNeed to target a tile!";
        if (!arena.isInBounds(targetTile)) return "§cTarget out of bounds!";
        stack.decrement(1);
        return TILE_EFFECT_PREFIX + "banner:" + targetTile.x() + ":" + targetTile.z()
            + "|§5Banner planted! +2 defense for player/allies within 2 tiles.";
    }

    private static boolean isPickaxe(Item item) {
        return item == Items.IRON_PICKAXE || item == Items.DIAMOND_PICKAXE
            || item == Items.NETHERITE_PICKAXE || item == Items.STONE_PICKAXE
            || item == Items.WOODEN_PICKAXE || item == Items.GOLDEN_PICKAXE;
    }

    // --- Water Bucket: place water tile on empty walkable tile (1 AP) ---
    private static String useWaterBucket(GridArena arena, GridPos targetTile, ItemStack stack) {
        if (targetTile == null) return "§cNeed to target a tile!";
        if (!arena.isInBounds(targetTile)) return "§cTarget out of bounds!";
        stack.decrement(1);
        return TILE_EFFECT_PREFIX + "water:" + targetTile.x() + ":" + targetTile.z()
            + "|§bPlaced water! Creates a fishable water tile.";
    }

    // --- Empty Bucket: pick up water or lava from a tile ---
    private static String useEmptyBucket(GridArena arena, GridPos targetTile, ItemStack stack) {
        if (targetTile == null) return "§cNeed to target a water or lava tile!";
        GridTile tile = arena.getTile(targetTile);
        if (tile == null) return "§cInvalid tile!";
        if (tile.getType() == com.crackedgames.craftics.core.TileType.WATER) {
            stack.decrement(1);
            return TILE_EFFECT_PREFIX + "clear:" + targetTile.x() + ":" + targetTile.z()
                + "|GIVE:water_bucket|§bScooped up water!";
        } else if (tile.getType() == com.crackedgames.craftics.core.TileType.LAVA) {
            stack.decrement(1);
            return TILE_EFFECT_PREFIX + "clear:" + targetTile.x() + ":" + targetTile.z()
                + "|GIVE:lava_bucket|§6Scooped up lava!";
        }
        return "§cThat tile has no liquid to pick up!";
    }

    // --- Sponge: absorb adjacent water tile (1 AP) ---
    private static String useSponge(GridArena arena, GridPos targetTile, ItemStack stack) {
        if (targetTile == null) return "§cNeed to target a water tile!";
        GridTile tile = arena.getTile(targetTile);
        if (tile == null || !tile.isWater()) return "§cThat's not a water tile!";
        GridPos playerPos = arena.getPlayerGridPos();
        int dist = Math.abs(playerPos.x() - targetTile.x()) + Math.abs(playerPos.z() - targetTile.z());
        if (dist > 1) return "§cToo far! Stand next to the water.";
        stack.decrement(1);
        return TILE_EFFECT_PREFIX + "sponge:" + targetTile.x() + ":" + targetTile.z()
            + "|§eSponge absorbed the water!";
    }

    // --- Pickaxe: break obstacle tile, making it walkable (1 AP, durability cost) ---
    private static String usePickaxe(GridArena arena, GridPos targetTile, ItemStack stack) {
        if (targetTile == null) return "§cNeed to target an obstacle!";
        GridTile tile = arena.getTile(targetTile);
        if (tile == null) return "§cInvalid tile!";
        if (tile.isWalkable()) return "§cThat tile is already walkable!";
        if (tile.isPermanent()) return "§cThis obstacle is too solid to break!";
        GridPos playerPos = arena.getPlayerGridPos();
        int dist = Math.abs(playerPos.x() - targetTile.x()) + Math.abs(playerPos.z() - targetTile.z());
        if (dist > 1) return "§cToo far! Stand next to the obstacle.";
        if (stack.getDamage() + 1 >= stack.getMaxDamage()) {
            stack.decrement(1);
        } else {
            stack.setDamage(stack.getDamage() + 1);
        }
        return TILE_EFFECT_PREFIX + "break:" + targetTile.x() + ":" + targetTile.z()
            + "|§7Obstacle broken! Tile is now walkable.";
    }

    // --- Crossbow: 4-tile range, 3 damage (2 AP, durability cost) ---
    private static String useCrossbow(GridArena arena, GridPos targetTile, ItemStack stack) {
        if (targetTile == null) return "§cNeed to target an enemy!";
        CombatEntity enemy = arena.getOccupant(targetTile);
        if (enemy == null || !enemy.isAlive()) return "§cNo enemy at target!";
        GridPos playerPos = arena.getPlayerGridPos();
        int dist = Math.abs(playerPos.x() - targetTile.x()) + Math.abs(playerPos.z() - targetTile.z());
        if (dist > 4) return "§cOut of range! (max 4 tiles)";
        if (stack.getDamage() + 1 >= stack.getMaxDamage()) {
            stack.decrement(1);
        } else {
            stack.setDamage(stack.getDamage() + 1);
        }
        int dealt = enemy.takeDamage(3);
        return "§7Crossbow bolt hits " + enemy.getDisplayName() + " for " + dealt + " damage!";
    }

    // --- Lingering Potion: create poison cloud on tile for 3 turns (1 AP) ---
    private static String useLingeringPotion(ServerPlayerEntity player, GridArena arena, GridPos targetTile, ItemStack stack) {
        if (targetTile == null) return "§cNeed to target a tile!";
        if (!arena.isInBounds(targetTile)) return "§cTarget out of bounds!";
        if (arena.getPlayerGridPos().manhattanDistance(targetTile) > POTION_THROW_RANGE) {
            return "§cToo far! Max throw range is " + POTION_THROW_RANGE + " tiles.";
        }

        // Read potion contents before decrementing
        var potionContents = stack.get(net.minecraft.component.DataComponentTypes.POTION_CONTENTS);
        stack.decrement(1);

        net.minecraft.server.world.ServerWorld sw = (net.minecraft.server.world.ServerWorld) player.getEntityWorld();
        BlockPos playerBlock = arena.gridToBlockPos(arena.getPlayerGridPos());
        BlockPos targetBlock = arena.gridToBlockPos(targetTile);

        // Throw sound + arc
        sw.playSound(null, player.getBlockPos(),
            net.minecraft.sound.SoundEvents.ENTITY_LINGERING_POTION_THROW,
            net.minecraft.sound.SoundCategory.PLAYERS, 1.0f, 1.0f);
        ProjectileSpawner.spawnPotionThrow(sw, playerBlock, targetBlock);

        // Spawn a real AreaEffectCloudEntity for vanilla cloud visuals
        net.minecraft.entity.AreaEffectCloudEntity cloud = new net.minecraft.entity.AreaEffectCloudEntity(
            sw, targetBlock.getX() + 0.5, targetBlock.getY(), targetBlock.getZ() + 0.5);
        cloud.setRadius(1.5f);
        cloud.setDuration(999999); // effectively permanent — cleaned up when combat ends
        cloud.setRadiusGrowth(0); // don't shrink — stays until combat ends
        cloud.setWaitTime(0);
        if (potionContents != null) {
            // Set the full potion contents — handles color, effects, and particles automatically
            cloud.setPotionContents(potionContents);
        } else {
            // Default to poison cloud
            cloud.addEffect(new StatusEffectInstance(StatusEffects.POISON, 100, 0));
        }
        cloud.addCommandTag("craftics_arena");
        sw.spawnEntity(cloud);

        return TILE_EFFECT_PREFIX + "poison_cloud:" + targetTile.x() + ":" + targetTile.z()
            + "|§5Lingering potion cloud! Enemies passing through are affected.";
    }

    // --- Lightning Rod: place on tile, strikes next turn for 4 AoE damage (1 AP) ---
    private static String useLightningRod(GridArena arena, GridPos targetTile, ItemStack stack) {
        if (targetTile == null) return "§cNeed to target a tile!";
        if (!arena.isInBounds(targetTile)) return "§cTarget out of bounds!";
        stack.decrement(1);
        return TILE_EFFECT_PREFIX + "lightning:" + targetTile.x() + ":" + targetTile.z()
            + "|§eLightning rod placed! It will strike all nearby enemies next turn.";
    }

    // --- Cactus: place an obstacle wall that deals 1 damage to adjacent enemies (1 AP) ---
    private static String useCactus(GridArena arena, GridPos targetTile, ItemStack stack) {
        if (targetTile == null) return "§cNeed to target a tile!";
        if (!arena.isInBounds(targetTile)) return "§cTarget out of bounds!";
        if (arena.isOccupied(targetTile)) return "§cTile is occupied!";
        stack.decrement(1);
        return TILE_EFFECT_PREFIX + "cactus:" + targetTile.x() + ":" + targetTile.z()
            + "|§2Cactus placed! It blocks movement and pricks adjacent enemies for 1 damage.";
    }

    // --- Hay Bale: throw to ally pet, heals 50% max HP (1 AP) ---
    private static String useHayBale(GridArena arena, GridPos targetTile, ItemStack stack) {
        if (targetTile == null) return "§cNeed to target an ally!";
        CombatEntity ally = arena.getOccupant(targetTile);
        if (ally == null || !ally.isAlive() || !ally.isAlly()) return "§cNo ally at target!";
        stack.decrement(1);
        int healAmount = Math.max(4, ally.getMaxHp() / 2);
        int healed = Math.min(healAmount, ally.getMaxHp() - ally.getCurrentHp());
        return ALLY_BUFF_PREFIX + "heal:" + ally.getEntityId() + ":" + healed
            + "|§aHay bale heals " + ally.getDisplayName() + " for " + healed + " HP!";
    }

    // --- Cake: place on tile, heals 2 HP to anyone stepping on it, 3 uses (1 AP) ---
    private static String useCake(GridArena arena, GridPos targetTile, ItemStack stack) {
        if (targetTile == null) return "§cNeed to target a tile!";
        if (!arena.isInBounds(targetTile)) return "§cTarget out of bounds!";
        stack.decrement(1);
        return TILE_EFFECT_PREFIX + "cake:" + targetTile.x() + ":" + targetTile.z()
            + "|§dCake placed! Heals 2 HP when stepped on (3 uses).";
    }

    // --- Spore Blossom: AoE slow, enemies within 3 tiles get -1 speed for 2 turns (1 AP) ---
    private static String useSporeBlossom(GridArena arena, GridPos targetTile, ItemStack stack) {
        if (targetTile == null) return "§cNeed to target a tile!";
        stack.decrement(1);
        int slowed = 0;
        for (CombatEntity e : arena.getOccupants().values()) {
            if (!e.isAlive() || e.isAlly()) continue;
            int dist = Math.abs(e.getGridPos().x() - targetTile.x()) + Math.abs(e.getGridPos().z() - targetTile.z());
            if (dist <= 3) {
                e.setSpeedBonus(e.getSpeedBonus() - 1);
                slowed++;
            }
        }
        return "§dSpore cloud! " + slowed + " enemies slowed (-1 speed).";
    }

    // --- Lantern: reveals invisible/hidden enemies in 3-tile radius (1 AP, consumes) ---
    private static String useLantern(GridArena arena, GridPos targetTile, ItemStack stack) {
        if (targetTile == null) return "§cNeed to target a tile!";
        stack.decrement(1);
        return TILE_EFFECT_PREFIX + "lantern:" + targetTile.x() + ":" + targetTile.z()
            + "|§eLight zone created! Reveals hidden enemies within 3 tiles + negates darkness.";
    }

    // --- Torch: place lightweight light source (1 AP) — creates light (radius 2, negates darkness) ---
    private static String useTorch(GridArena arena, GridPos targetTile, ItemStack stack) {
        if (targetTile == null) return "§cNeed to target a tile!";
        if (!arena.isInBounds(targetTile)) return "§cTarget out of bounds!";
        stack.decrement(1);
        return TILE_EFFECT_PREFIX + "torch:" + targetTile.x() + ":" + targetTile.z()
            + "|§eLight from torch! Creates a smaller light zone (radius 2) that negates darkness.";
    }

    // --- Goat Horn: taunt all enemies to target you next turn (1 AP, no consume) ---
    private static String useGoatHorn(GridArena arena, ItemStack stack) {
        String hornId = GoatHornEffects.getHornId(stack);
        if (hornId == null) {
            return "§7The horn makes no sound...";
        }

        // Get the combat effects and enemy list from the arena occupants
        java.util.List<CombatEntity> enemies = new java.util.ArrayList<>();
        for (CombatEntity e : arena.getOccupants().values()) {
            if (!e.isAlive() || e.isAlly()) continue;
            enemies.add(e);
        }

        // The horn is not consumed — it's reusable, just costs AP
        // CombatManager will handle applying the effect via HORN_EFFECT_PREFIX
        return HORN_EFFECT_PREFIX + hornId + "|" + GoatHornEffects.getTooltip(hornId);
    }

    /** Prefix for horn effects — CombatManager applies the actual buff/debuff. */
    public static final String HORN_EFFECT_PREFIX = "§6HORN:";

    // --- Echo Shard: teleport back to your position at start of turn (1 AP) ---
    private static String useEchoShard(GridArena arena, ItemStack stack) {
        stack.decrement(1);
        return ALLY_BUFF_PREFIX + "echo|§5Echo shard activates! You'll return to your start position at end of turn.";
    }

    // --- Brush: excavate random item from sand/gravel tile (1 AP) ---
    private static String useBrush(ServerPlayerEntity player, GridArena arena, GridPos targetTile, ItemStack stack) {
        if (targetTile == null) return "§cNeed to target a tile!";
        GridPos playerPos = arena.getPlayerGridPos();
        int dist = Math.abs(playerPos.x() - targetTile.x()) + Math.abs(playerPos.z() - targetTile.z());
        if (dist > 1) return "§cToo far! Stand next to the tile.";
        if (stack.getDamage() + 1 >= stack.getMaxDamage()) {
            stack.decrement(1);
        } else {
            stack.setDamage(stack.getDamage() + 1);
        }
        java.util.Random rng = new java.util.Random();
        Item[] loot = {
            Items.GOLD_NUGGET, Items.IRON_NUGGET, Items.CLAY_BALL,
            Items.EMERALD, Items.DIAMOND, Items.AMETHYST_SHARD,
            Items.ARROW, Items.BONE, Items.STRING,
            Items.FLINT, Items.COAL, Items.BRICK
        };
        Item found = loot[rng.nextInt(loot.length)];
        LootDelivery.deliver(player, new ItemStack(found, 1));
        String name = new ItemStack(found).getName().getString();
        return "§eBrushed up: " + name + "!";
    }

    private static String useBreedingItem(ServerPlayerEntity player, GridArena arena,
                                           GridPos targetTile, ItemStack stack) {
        if (targetTile == null) return "§cNeed to target a tile!";
        CombatEntity occupant = arena.getOccupant(targetTile);
        if (occupant == null || !occupant.isAlive()) return "§cNo creature at target!";
        if (occupant.isAlly()) return "§cAlready tamed!";

        String entityType = occupant.getEntityTypeId();
        Item item = stack.getItem();

        // Check if this item works on this mob type
        if (!isBreedingItem(item, entityType)) {
            String mobName = entityType.substring(entityType.indexOf(':') + 1);
            return "§cThis item doesn't work on " + mobName + "!";
        }

        stack.decrement(1);

        // All tameable mobs become combat allies
        Set<String> combatTameable = Set.of(
            "minecraft:wolf", "minecraft:cat", "minecraft:ocelot",
            "minecraft:horse", "minecraft:donkey", "minecraft:llama",
            "minecraft:camel", "minecraft:fox", "minecraft:mule",
            "minecraft:skeleton_horse", "minecraft:zombie_horse",
            "minecraft:cow", "minecraft:sheep", "minecraft:pig",
            "minecraft:chicken", "minecraft:rabbit", "minecraft:bee",
            "minecraft:mooshroom", "minecraft:parrot", "minecraft:turtle",
            "minecraft:axolotl", "minecraft:frog", "minecraft:sniffer", "minecraft:goat"
        );

        if (combatTameable.contains(entityType)) {
            // Mountable mobs require a saddle — player mounts them for bonus speed
            if (MOUNTABLE_MOBS.contains(entityType)) {
                if (hasSaddle(player)) {
                    consumeSaddle(player);
                    return MOUNT_PREFIX + occupant.getEntityId();
                }
                // No saddle — tame as normal walking ally
            }
            // Returns TAME_PREFIX — CombatManager converts to ally
            return TAME_PREFIX + occupant.getEntityId();
        } else {
            // Passive mobs (cow, sheep, pig, chicken, etc.) — show hearts, vanish, sent to hub
            return BEFRIEND_PREFIX + occupant.getEntityId() + ":" + entityType;
        }
    }
}
