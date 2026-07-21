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

        // The splash center must be within throw range of the player and in a
        // clear line of sight - matching bows/crossbows so the splash can't be
        // dropped across the whole map or thrown through a wall.
        GridPos playerPos = arena.getPlayerGridPos();
        if (playerPos.manhattanDistance(targetTile) > THROWABLE_RANGE) {
            return "§cToo far! Max throw range is " + THROWABLE_RANGE + " tiles.";
        }
        if (!Pathfinding.hasLineOfSight(arena, playerPos, targetTile)) {
            return "§cLine of sight blocked by an obstacle!";
        }

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
        // Multi-tile entities appear once per occupied tile in getOccupants(); track
        // which we've already splashed so a 2x2 mob isn't damaged multiple times.
        java.util.Set<CombatEntity> splashed = new java.util.HashSet<>();
        for (CombatEntity enemy : arena.getOccupants().values()) {
            if (!enemy.isAlive() || enemy.isAlly()) continue;
            if (!splashed.add(enemy)) continue;
            // A Creaking with a living heart is invulnerable - the splash can't
            // damage or status it; the heart must be destroyed instead.
            if (CombatManager.isInvulnerableCreaking(enemy)) continue;
            if (enemy.minDistanceTo(targetTile) <= radius) {
                // Deal Water-type damage (assume Water type is handled in takeDamage or add a param if needed)
                int dealt = enemy.takeDamage(waterDamage); // TODO: Pass Water type if needed
                // Apply Soaked
                enemy.stackSoaked(soakedLevel, soakedLevel);
                // Apply Poison if pufferfish
                if (poisonLevel > 0) {
                    enemy.stackPoison(poisonLevel, poisonLevel);
                }
                // Apply Confusion if nautilus/heart. Nerf: never guaranteed - roll chance.
                if (confusionLevel > 0
                        && com.crackedgames.craftics.combat.ConfusionLogic.rollHits(
                            Math.random(),
                            com.crackedgames.craftics.CrafticsMod.CONFIG.confusionApplyChance())) {
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

    /**
     * Raw food to its campfire-cooked result. Mirrors vanilla's campfire recipes rather
     * than looking them up, because the recipe API has moved between Minecraft versions
     * and a campfire only ever accepts this fixed handful of inputs anyway.
     */
    private static final Map<Item, Item> CAMPFIRE_COOKING = Map.ofEntries(
        Map.entry(Items.BEEF, Items.COOKED_BEEF),
        Map.entry(Items.PORKCHOP, Items.COOKED_PORKCHOP),
        Map.entry(Items.CHICKEN, Items.COOKED_CHICKEN),
        Map.entry(Items.MUTTON, Items.COOKED_MUTTON),
        Map.entry(Items.RABBIT, Items.COOKED_RABBIT),
        Map.entry(Items.COD, Items.COOKED_COD),
        Map.entry(Items.SALMON, Items.COOKED_SALMON),
        Map.entry(Items.POTATO, Items.BAKED_POTATO),
        Map.entry(Items.KELP, Items.DRIED_KELP)
    );

    /** Most food a single campfire action can cook. */
    public static final int CAMPFIRE_COOK_BATCH = 4;

    /** True if {@code item} is a raw food a campfire can cook. */
    public static boolean isCampfireCookable(Item item) {
        return CAMPFIRE_COOKING.containsKey(item);
    }

    /** The cooked result for a raw food, or {@code null} if it isn't cookable. */
    public static Item campfireResult(Item raw) {
        return CAMPFIRE_COOKING.get(raw);
    }

    // Items that are "usable" in combat
    private static final Set<Item> THROWABLES = Set.of(
        Items.SNOWBALL, Items.EGG, Items.ENDER_PEARL, Items.FIRE_CHARGE, Items.WIND_CHARGE,
        Items.BRICK
    );

    /** Base damage a thrown brick deals before affinity/trim/head bonuses. */
    public static final int BRICK_BASE_DAMAGE = 4;

    public static boolean isFood(Item item) {
        return FOOD_HEAL.containsKey(item) || isArtifactsNonConsumingFood(item);
    }

    /** Heal an ally party member by consuming one of the held food stack. Used
     *  when a player clicks an adjacent ally tile while holding food, so co-op
     *  parties can share consumables. Returns the chat-formatted result message,
     *  or {@code null} if {@code stack} isn't an edible food item we recognise.
     *
     *  <p>Medic scales off the FEEDER's hoe, not the recipient's - the medic is the one
     *  doing the healing, so it's their enchantment that decides how much it's worth. */
    public static String feedAlly(ServerPlayerEntity feeder, ServerPlayerEntity ally, ItemStack stack) {
        Item food = stack.getItem();
        if (!FOOD_HEAL.containsKey(food)) return null;
        int medic = HoeEnchantEffects.medicBonus(feeder);
        int healAmount = FOOD_HEAL.getOrDefault(food, 1) + medic;
        float maxHealth = ally.getMaxHealth();
        float before = ally.getHealth();
        float newHealth = Math.min(maxHealth, before + healAmount);
        ally.setHealth(newHealth);
        int healed = Math.round(newHealth - before);
        String itemName = stack.getName().getString();
        String allyName = ally.getName().getString();
        stack.decrement(1);
        if (medic > 0 && ally.getEntityWorld() instanceof ServerWorld fsw) {
            HoeEnchantEffects.medicVfx(fsw, ally.getX(), ally.getY(), ally.getZ());
        }
        return "§aFed " + itemName + " to " + allyName + " - healed " + healed
            + " HP §7(" + (int) newHealth + "/" + (int) maxHealth + ")";
    }

    /** Feed milk to an adjacent ally: clears ALL of THEIR status effects, consumes the feeder's
     *  bucket, returns an empty bucket to the feeder. Mirrors {@link #feedAlly} for food. */
    public static String feedMilkToAlly(ServerPlayerEntity feeder, ServerPlayerEntity ally, ItemStack stack) {
        stack.decrement(1);
        feeder.getInventory().insertStack(new ItemStack(Items.BUCKET));
        CombatEffects allyFx = CombatManager.get(ally).getCombatEffects();
        allyFx.clear();
        ally.clearStatusEffects();
        String allyName = ally.getName().getString();
        return "§fFed milk to " + allyName + " - all their effects cleared.";
    }

    /**
     * Eternal Steak / Everlasting Beef from the Artifacts mod - non-consuming food items.
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

    // Breeding/taming materials - maps mob entity type → item that tames them
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
        Items.BELL, Items.JUKEBOX, Items.CROSSBOW, Items.SPYGLASS, Items.ELYTRA
    );

    public static int getApCost(Item item) {
        com.crackedgames.craftics.api.registry.UsableItemEntry registered =
            com.crackedgames.craftics.api.registry.UsableItemRegistry.getOrNull(item);
        if (registered != null) return registered.apCost();
        if (item == Items.GOLDEN_CARROT) return 0;
        if (PotterySherdSpells.isPotterySherd(item)) return PotterySherdSpells.getSherdApCost(item);
        if (item == Items.FISHING_ROD) return FISHING_AP_COST;
        if (item == Items.TNT) return 2; // raised from default 1: TNT now deals %-max-HP blast damage
        if (item == Items.MILK_BUCKET) return 3; // clears ALL effects; feed self or an adjacent ally
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
            // A horn with no readable variant still costs a horn's worth of AP - quoting a
            // cheaper number here would let an unnamed horn undercut every real one.
            return GoatHornEffects.HORN_AP_COST;
        }
        return getApCost(stack.getItem());
    }

    private static final Set<Item> EXTRA_USABLE = Set.of(
        Items.SPYGLASS, Items.COMPASS, Items.BELL, Items.LAVA_BUCKET,
        Items.SCAFFOLDING, Items.CAMPFIRE, Items.ANVIL, Items.CHIPPED_ANVIL, Items.DAMAGED_ANVIL,
        Items.HONEY_BLOCK, Items.SLIME_BLOCK,
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
        Items.LANTERN, Items.TORCH, Items.GOAT_HORN, Items.ECHO_SHARD, Items.BRUSH, Items.ELYTRA
    );

    private static boolean isBanner(Item item) {
        return BannerEffects.isBanner(item);
    }

    private static final Set<Item> WATER_THROWABLES = Set.of(
        Items.TURTLE_EGG, Items.PUFFERFISH, Items.NAUTILUS_SHELL, Items.HEART_OF_THE_SEA
    );

    public static boolean isWaterThrowable(Item item) {
        return WATER_THROWABLES.contains(item);
    }

    public static boolean isUsableItem(Item item) {
        return com.crackedgames.craftics.api.registry.UsableItemRegistry.isRegistered(item)
            || isFood(item) || isPotion(item) || isSplashPotion(item)
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

        // Flint & steel on an ignitable ally ignites it instead of any other use.
        String litCoal = tryIgniteAlly(arena, targetTile, item);
        if (litCoal != null) return litCoal;

        // Per-ally heal item (iron ingot → iron golem, snowball → snow golem, ...)
        // takes priority over the item's normal combat use.
        String allyHeal = tryHealAlly(arena, targetTile, item, held);
        if (allyHeal != null) return allyHeal;

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
        } else if (item == Items.BRICK) {
            return useBrick(player, arena, targetTile, held);
        } else if (item == Items.ENDER_PEARL) {
            return useEnderPearl(player, arena, targetTile, held);
        } else if (isSplashPotion(item)) {
            return useSplashPotion(player, arena, targetTile, held);
        } else if (item == Items.FIRE_CHARGE) {
            return useFireCharge(player, arena, targetTile, held);
        } else if (item == Items.WIND_CHARGE) {
            return useWindCharge(player, arena, targetTile, held);
        } else if (item == Items.ELYTRA) {
            return useElytra(player, arena, targetTile, held);
        } else if (item == Items.MILK_BUCKET) {
            return useMilkBucket(player, held);
        } else if (item == Items.TNT) {
            return useTNT(player, arena, targetTile, held);
        } else if (item == Items.COBWEB) {
            return useCobweb(player, arena, targetTile, held);
        } else if (item == Items.SHIELD) {
            return "§9Shield is passive! Equip in offhand for +1 AC and 25% chance to block attacks.";
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
            return useBell(arena, targetTile, held);
        } else if (item == Items.LAVA_BUCKET) {
            return useLavaBucket(arena, targetTile, held);
        } else if (item == Items.SCAFFOLDING) {
            return useScaffolding(arena, targetTile, held);
        } else if (item == Items.CAMPFIRE) {
            return useCampfire(arena, targetTile, held);
        } else if (item == Items.ANVIL || item == Items.CHIPPED_ANVIL || item == Items.DAMAGED_ANVIL) {
            return useAnvil(player, arena, targetTile, held);
        } else if (item == Items.HONEY_BLOCK) {
            return useHoneyBlock(arena, targetTile, held);
        } else if (item == Items.SLIME_BLOCK) {
            return useSlimeBlock(arena, targetTile, held);
        } else if (item == Items.POWDER_SNOW_BUCKET) {
            return usePowderSnow(player, arena, targetTile, held);
        } else if (item == Items.JUKEBOX) {
            return useJukebox(arena, targetTile, held);
        } else if (isBanner(item)) {
            return useBanner(player, arena, targetTile, held);
        } else if (item == Items.WATER_BUCKET) {
            return useWaterBucket(arena, targetTile, held);
        } else if (item == Items.BUCKET) {
            return useEmptyBucket(arena, targetTile, held);
        } else if (item == Items.SPONGE) {
            return useSponge(arena, targetTile, held);
        } else if (isPickaxe(item)) {
            return usePickaxe(arena, targetTile, held);
        } else if (item == Items.CROSSBOW) {
            return useCrossbow(player, arena, targetTile, held);
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

    /** Prefix for fishing results - CombatManager deducts extra AP. */
    public static final String FISHING_PREFIX = "§bFISH:";
    public static final int FISHING_AP_COST = 3;

    /** Prefix for tile effects - CombatManager processes these. Format: TILE:type:x:z */
    public static final String TILE_EFFECT_PREFIX = "§eTILE:";
    /** Anvil-drop signal: §8ANVIL:targetEntityId:damage. Server spawns the falling-anvil VFX. */
    public static final String ANVIL_DROP_PREFIX = "§8ANVIL:";
    /** Prefix for ally buff - CombatManager processes this. */
    public static final String ALLY_BUFF_PREFIX = "§dBUFF:";

    private static boolean isMoveFeather(ItemStack s) {
        return s.getItem() == com.crackedgames.craftics.item.ModItems.MOVE_ITEM;
    }

    private static String useFood(ServerPlayerEntity player, ItemStack stack, Item food) {
        // Eternal Steak / Everlasting Beef (Artifacts mod) - non-consuming food.
        // Per the Craftics × Artifacts spec: 2 AP, heals 3 HP, doesn't decrement the stack.
        if (isArtifactsNonConsumingFood(food)) {
            float curMax = player.getMaxHealth();
            float curHp = player.getHealth();
            int heal = 3;
            float newHp = Math.min(curMax, curHp + heal);
            player.setHealth(newHp);
            String displayName = stack.getName().getString();
            return "§dAte " + displayName + " - healed " + heal + " HP §7(it's eternal!)";
        }

        int healAmount = FOOD_HEAL.getOrDefault(food, 1);
        float maxHealth = player.getMaxHealth();
        float newHealth = Math.min(maxHealth, player.getHealth() + healAmount);
        player.setHealth(newHealth);
        stack.decrement(1);

        // Risky foods - apply debuff effects via the hooked path so addon
        // immunities (e.g. Antidote Vessel) can intercept them.
        CombatManager cm = CombatManager.getActiveCombat(player.getUuid());
        java.util.Random rng = java.util.concurrent.ThreadLocalRandom.current();
        // These foods apply the CRAFTICS combat effect only - the vanilla
        // StatusEffect doubled up as real per-tick HP damage that bypasses the
        // turn-based combat system and kept ticking even outside combat. The
        // combat-effect handles the turn-based debuff on its own.
        if (food == Items.POISONOUS_POTATO && rng.nextFloat() < 0.60f) {
            boolean applied = cm.addEffectHooked(CombatEffects.EffectType.POISON, 2, 0);
            return applied
                ? "§aAte Poisonous Potato! Healed " + healAmount + " HP §c(Poisoned for 2 turns!)"
                : "§aAte Poisonous Potato! Healed " + healAmount + " HP §a(Poison resisted!)";
        } else if (food == Items.SPIDER_EYE) {
            boolean applied = cm.addEffectHooked(CombatEffects.EffectType.POISON, 2, 0);
            return applied
                ? "§aAte Spider Eye! Healed " + healAmount + " HP §c(Poisoned for 2 turns!)"
                : "§aAte Spider Eye! Healed " + healAmount + " HP §a(Poison resisted!)";
        } else if (food == Items.ROTTEN_FLESH && rng.nextFloat() < 0.80f) {
            boolean applied = cm.addEffectHooked(CombatEffects.EffectType.WEAKNESS, 2, 0);
            return applied
                ? "§aAte Rotten Flesh! Healed " + healAmount + " HP §c(Weakened for 2 turns!)"
                : "§aAte Rotten Flesh! Healed " + healAmount + " HP §a(Weakness resisted!)";
        }

        // Golden apple also gives absorption. Route the buffs through the combat
        // effect system (addEffectHooked) so they actually function in combat - the
        // ABSORPTION hook grants the combat absorption shield, and RESISTANCE/
        // REGENERATION register as real combat effects. The vanilla addStatusEffect
        // calls are kept only for the vanilla HUD icons.
        if (food == Items.GOLDEN_APPLE) {
            cm.addEffectHooked(CombatEffects.EffectType.ABSORPTION, 5, 0);
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 2400, 0));
        } else if (food == Items.ENCHANTED_GOLDEN_APPLE) {
            player.setHealth(maxHealth);
            cm.addEffectHooked(CombatEffects.EffectType.ABSORPTION, 5, 3);
            cm.addEffectHooked(CombatEffects.EffectType.RESISTANCE, 5, 0);
            cm.addEffectHooked(CombatEffects.EffectType.REGENERATION, 5, 1);
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 2400, 3));
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 6000, 0));
            // NO vanilla Regeneration. It is the one effect that must never reach the player:
            // it heals in real time, which drip-feeds HP back between turns and quietly
            // undoes the turn-based damage economy. The addEffectHooked call above already
            // gives the combat-only version, which heals a little at the START of each turn.
        }

        // Golden carrot restores 1 AP (capped at max AP)
        if (food == Items.GOLDEN_CARROT) {
            PlayerProgression.PlayerStats gcStats = PlayerProgression.get(
                (net.minecraft.server.world.ServerWorld) player.getEntityWorld()).getStats(player);
            int maxAp = gcStats.getEffective(PlayerProgression.Stat.AP)
                + PlayerCombatStats.getSetApBonus(player);
            if (cm.getApRemaining() >= maxAp) {
                // Refund the food - AP is already full, undo the heal + consumption
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

    /** HP restored per Instant Health level (Level I). Level II heals double, etc.
     *  Raised above the old 4 so a heal potion is worth spending a turn on. */
    private static final int INSTANT_HEALTH_PER_LEVEL = 8;

    private static String useDrinkPotion(ServerPlayerEntity player, ItemStack stack) {
        // Read potion contents BEFORE decrementing (decrement destroys component data)
        var potionContents = stack.get(net.minecraft.component.DataComponentTypes.POTION_CONTENTS);
        if (!trySpecialConserve(player, stack)) {
            stack.decrement(1);
            // Bottle only refunds when the potion was actually consumed.
            player.getInventory().insertStack(new ItemStack(Items.GLASS_BOTTLE));
        }

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
                    // Route through the live top-up so a SPEED/HASTE potion drunk on
                    // your turn grants movement/AP this turn, not next.
                    CombatManager.getActiveCombat(player.getUuid()).applyPlayerEffectLive(combatType, turns, scaledAmplifier);
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
                    // Medic (hoe) tops up every heal a Special item does.
                    int medic = HoeEnchantEffects.medicBonus(player);
                    int healAmount = INSTANT_HEALTH_PER_LEVEL * (sei.getAmplifier() + 1)
                        + getSpecialAffinityPoints(player) + medic;
                    player.setHealth(Math.min(player.getMaxHealth(), player.getHealth() + healAmount));
                    if (applied.length() > 0) applied.append(", ");
                    applied.append("Healed ").append(healAmount);
                    if (medic > 0) {
                        HoeEnchantEffects.medicVfx(sw, player.getX(), player.getY(), player.getZ());
                    } else {
                        // Heart particles for healing
                        sw.spawnParticles(net.minecraft.particle.ParticleTypes.HEART,
                            player.getX(), player.getY() + 1.5, player.getZ(), 6, 0.4, 0.3, 0.4, 0.05);
                    }
                }
            }
            if (applied.length() > 0) {
                return "§dDrank potion! " + applied;
            }
        }
        return "§dDrank potion!";
    }

    /** Public: the client potion tooltip (CombatTooltips) reuses this mapping so hover
     *  text and the actual applied effect can never drift apart. */
    public static CombatEffects.EffectType mapStatusEffect(net.minecraft.entity.effect.StatusEffect effect) {
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

    /** Public: the client potion tooltip (CombatTooltips) calls this with the effect's real
     *  vanilla duration so extended (long_) variants show their doubled turn count. */
    public static int getTurnsForPotion(CombatEffects.EffectType type, int vanillaDurationTicks) {
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
            case AIRTIME -> 1; // not vanilla-potion-driven; unreachable via this path
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
        return SpecialAffinity.points(player);
    }

    /**
     * Roll the Special "conserve" save: each Special affinity point grants a
     * 10% chance to NOT consume a caster-class item this use. Returns
     * {@code true} on a successful conserve (caller should skip the
     * decrement and any side effects tied to consumption, e.g. the empty
     * bottle refund). On a save, also sends the flair chat line so every
     * call site has identical UX. Returns {@code false} when there's no
     * Special investment or the roll missed.
     */
    private static boolean trySpecialConserve(ServerPlayerEntity player, ItemStack stack) {
        int specialPts = SpecialAffinity.points(player);
        double saveChance = specialPts * 0.10;
        if (saveChance <= 0 || Math.random() >= saveChance) return false;
        CombatManager.getActiveCombat(player.getUuid()).sendMessage(
            "§dConserved! Special focus preserves your "
                + stack.getName().getString() + ".");
        return true;
    }

    /**
     * Consume one of a Special-class consumable (banner, horn, fire/wind
     * charge, snowball, egg, ender pearl, ...). Wraps {@link #trySpecialConserve}
     * so simple call sites don't have to duplicate the if/decrement pattern.
     * Use {@code trySpecialConserve} directly when the consume site has extra
     * side effects to gate on the save (potions refund a glass bottle, etc.).
     *
     * <p>Non-Special utility items (TNT, hay block, pickaxe durability, ...)
     * should keep plain {@code stack.decrement(1)} so this perk stays scoped
     * to the caster archetype.
     */
    private static void consumeSpecialItem(ServerPlayerEntity player, ItemStack stack) {
        if (!trySpecialConserve(player, stack)) {
            stack.decrement(1);
        }
    }

    /**
     * Whether {@code item} is a Special-class consumable - the caster's toolkit, and therefore
     * what the hoe enchantments (Reserving, Performative) key off.
     *
     * <p>This is the same set every {@link #consumeSpecialItem} call site covers, listed once
     * here so the two can't drift. Note it deliberately does NOT go through
     * {@code DamageType.fromWeapon}: that reads the WeaponRegistry, and a fire charge or ender
     * pearl is a usable item rather than a registered weapon, so it would answer null for most
     * of this list.
     *
     * <p>Pottery sherds are Special casts too, but they are classified by
     * {@code PotterySherdSpells.isPotterySherd} at the call site rather than duplicated here.
     */
    public static boolean isSpecialConsumable(Item item) {
        if (item == null) return false;
        if (isPotion(item)) return true; // drink, splash and lingering all conserve
        if (item == Items.GOAT_HORN) return true;
        if (item == Items.FIRE_CHARGE || item == Items.WIND_CHARGE) return true;
        if (item == Items.SNOWBALL || item == Items.EGG || item == Items.ENDER_PEARL) return true;
        return isBanner(item);
    }

    private static int getPotionPotencyBonus(ServerPlayerEntity player) {
        return SpecialAffinity.potencyBonus(player);
    }

    private static int getPotionDurationBonus(ServerPlayerEntity player) {
        return SpecialAffinity.durationBonus(player);
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
            player, CombatManager.getActiveCombat(player.getUuid()).getTrimScan(), effects,
            type, getPlayerStats(player))
            + DamageType.getMobHeadBonus(
                player.getEquippedStack(net.minecraft.entity.EquipmentSlot.HEAD), type);
    }

    private static int applyTypedDamage(ServerPlayerEntity player, CombatEntity target, int baseDamage,
                                        DamageType type) {
        // A Creaking with a living heart takes no damage from any item; the heart
        // must be destroyed instead. Callers that want a player-facing message
        // (e.g. flint &amp; steel) check isInvulnerableCreaking themselves first.
        if (CombatManager.isInvulnerableCreaking(target)) {
            return 0;
        }
        CombatEffects effects = CombatManager.getActiveCombat(player.getUuid()).getCombatEffects();
        int rawDamage = baseDamage + getTypedDamageBonus(player, effects, type);
        // Radiant (hoe): Special items burn the undead. Every Special item's damage funnels
        // through here, so hooking it once covers charges, pearls, sherds, potions and the rest.
        if (type == DamageType.SPECIAL) {
            int radiant = HoeEnchantEffects.radiantBonus(player, target);
            if (radiant > 0) {
                rawDamage += radiant;
                var radiantArena = CombatManager.getActiveCombat(player.getUuid()).getArena();
                if (radiantArena != null && player.getEntityWorld() instanceof ServerWorld rw) {
                    HoeEnchantEffects.radiantVfx(rw, radiantArena.gridToBlockPos(target.getGridPos()));
                }
            }
        }
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

    /** Max tiles a thrown combat item (snowball, egg, pufferfish, water throwables) can reach. */
    private static final int THROWABLE_RANGE = 4;

    /** Durability the Elytra launch spends per use. */
    private static final int ELYTRA_DURABILITY_COST = 2;

    /**
     * Validate that {@code enemy} can be hit by a thrown item: within
     * {@link #THROWABLE_RANGE} tiles of the player and with a clear line of sight,
     * matching bow/crossbow targeting so throwables can't reach across the whole
     * map or hit through walls. Returns an error message to send back, or
     * {@code null} when the throw is allowed.
     */
    private static String validateThrowReach(GridArena arena, CombatEntity enemy) {
        GridPos playerPos = arena.getPlayerGridPos();
        if (enemy.minDistanceTo(playerPos) > THROWABLE_RANGE) {
            return "§cToo far! Max throw range is " + THROWABLE_RANGE + " tiles.";
        }
        if (!Pathfinding.hasLineOfSight(arena, playerPos, enemy.nearestTileTo(playerPos))) {
            return "§cLine of sight blocked by an obstacle!";
        }
        return null;
    }

    /**
     * Validate that {@code enemy} is adjacent to the player (within one tile,
     * diagonals included) with a clear line of sight. Used by melee-style item
     * interactions (flint &amp; steel, taming) that should require standing next
     * to the target rather than reaching across the arena.
     */
    private static String validateAdjacentReach(GridArena arena, CombatEntity enemy) {
        GridPos playerPos = arena.getPlayerGridPos();
        GridPos nearest = enemy.nearestTileTo(playerPos);
        int cheby = Math.max(Math.abs(playerPos.x() - nearest.x()),
                             Math.abs(playerPos.z() - nearest.z()));
        if (cheby > 1) return "§cToo far! Stand next to the target.";
        if (!Pathfinding.hasLineOfSight(arena, playerPos, nearest)) {
            return "§cLine of sight blocked by an obstacle!";
        }
        return null;
    }

    private static String useSnowball(ServerPlayerEntity player, GridArena arena,
                                       GridPos targetTile, ItemStack stack) {
        if (targetTile == null) return "§cNeed to target a tile!";
        CombatEntity enemy = arena.getOccupant(targetTile);
        if (enemy == null || !enemy.isAlive()) return "§cNo enemy at target!";
        String reach = validateThrowReach(arena, enemy);
        if (reach != null) return reach;

        consumeSpecialItem(player, stack);
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
        String reach = validateThrowReach(arena, enemy);
        if (reach != null) return reach;

        consumeSpecialItem(player, stack);
        int dealt = applyTypedDamage(player, enemy, damage, DamageType.SPECIAL);
        return "§eEgg hit " + enemy.getDisplayName() + " for " + dealt + " Special damage!";
    }

    /**
     * Brick - the common man's throwable. Blunt damage, so it inherits the whole
     * BLUNT interaction table (undead take extra, slimes/arthropods resist) and
     * scales off Blunt affinity, trims, and the Creeper head like any blunt hit.
     * Plain decrement: BLUNT is not a Special-class consumable, so the caster's
     * conserve perk deliberately doesn't apply.
     */
    private static String useBrick(ServerPlayerEntity player, GridArena arena,
                                    GridPos targetTile, ItemStack stack) {
        if (targetTile == null) return "§cNeed to target a tile!";
        CombatEntity enemy = arena.getOccupant(targetTile);
        if (enemy == null || !enemy.isAlive()) return "§cNo enemy at target!";
        String reach = validateThrowReach(arena, enemy);
        if (reach != null) return reach;

        stack.decrement(1);
        int dealt = applyTypedDamage(player, enemy, BRICK_BASE_DAMAGE, DamageType.BLUNT);
        return "§7Brick hit " + enemy.getDisplayName() + " for " + dealt + " Blunt damage!";
    }

    private static String useEnderPearl(ServerPlayerEntity player, GridArena arena,
                                         GridPos targetTile, ItemStack stack) {
        if (targetTile == null) return "§cNeed to target a tile!";
        if (!arena.isInBounds(targetTile)) return "§cTarget out of bounds!";
        if (arena.isOccupied(targetTile)) return "§cTile is occupied!";
        var tile = arena.getTile(targetTile);
        if (tile == null || !tile.isWalkable()) return "§cCan't teleport there!";

        consumeSpecialItem(player, stack);
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

    /**
     * Apply a potion's ENEMY-side combat effects to each target, exactly as a splash potion
     * landing on them does. Extracted from {@link #useSplashPotion} so the Trapper enchant's
     * sprung trap runs the identical ruleset - one switch, no drift. Appends per-target
     * fragments to {@code msg} and returns how many applications landed. Death checks are
     * the caller's job.
     */
    public static int applyPotionEffectsToEnemies(ServerPlayerEntity player,
            java.util.List<CombatEntity> targets,
            net.minecraft.component.type.PotionContentsComponent potionContents,
            StringBuilder msg) {
        if (player == null || targets == null || targets.isEmpty() || potionContents == null) return 0;
        CombatEffects effects = CombatManager.getActiveCombat(player.getUuid()).getCombatEffects();
        int specialDamageBonus = getTypedDamageBonus(player, effects, DamageType.SPECIAL);
        int hitCount = 0;
        for (StatusEffectInstance sei : potionContents.getEffects()) {
            var effectType = sei.getEffectType().value();
            int amp = sei.getAmplifier();
            int scaledAmp = getScaledPotionAmplifier(player, amp);

            for (CombatEntity target : targets) {
                // Vanilla particles on mob
                if (target.getMobEntity() != null) {
                    target.getMobEntity().addStatusEffect(new StatusEffectInstance(
                        sei.getEffectType(), -1, scaledAmp, false, true));
                }
                if (effectType == StatusEffects.INSTANT_DAMAGE.value()) {
                    int dealt = target.takeSpecialDamage(3 * (scaledAmp + 1) + specialDamageBonus, 0.12);
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
                    int witherTurns = getScaledPotionTurns(player, CombatEffects.EffectType.WITHER, sei.getDuration());
                    target.stackWither(witherTurns, scaledAmp);
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
        }
        return hitCount;
    }

    private static String useSplashPotion(ServerPlayerEntity player, GridArena arena,
                                           GridPos targetTile, ItemStack stack) {
        if (targetTile == null) return "§cNeed to target a tile!";
        if (arena.getPlayerGridPos().manhattanDistance(targetTile) > POTION_THROW_RANGE) {
            return "§cToo far! Max throw range is " + POTION_THROW_RANGE + " tiles.";
        }
        CombatEntity enemy = arena.getOccupant(targetTile);

        // Read potion contents BEFORE decrementing (decrement destroys component data)
        var potionContents = stack.get(net.minecraft.component.DataComponentTypes.POTION_CONTENTS);
        consumeSpecialItem(player, stack);

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
            CombatEffects effects = CombatManager.getActiveCombat(player.getUuid()).getCombatEffects();
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

            hitCount += applyPotionEffectsToEnemies(player, aoeEnemies, potionContents, msg);

            for (StatusEffectInstance sei : potionContents.getEffects()) {
                var effectType = sei.getEffectType().value();
                int amp = sei.getAmplifier();
                int scaledAmp = getScaledPotionAmplifier(player, amp);

                // === Apply effects to the player if caught in the blast ===
                // Both buffs AND debuffs land on a self-targeted splash, matching vanilla
                // (a weakness potion thrown at your own feet now actually weakens you).
                if (playerInRange) {
                    CombatEffects.EffectType combatType = mapStatusEffect(effectType);
                    if (effectType == StatusEffects.INSTANT_HEALTH.value()) {
                        int medicSplash = HoeEnchantEffects.medicBonus(player);
                        int heal = INSTANT_HEALTH_PER_LEVEL * (amp + 1)
                            + getSpecialAffinityPoints(player) + medicSplash;
                        player.setHealth(Math.min(player.getMaxHealth(), player.getHealth() + heal));
                        msg.append("§a+").append(heal).append("HP ");
                        if (medicSplash > 0 && player.getEntityWorld() instanceof ServerWorld msw) {
                            HoeEnchantEffects.medicVfx(msw, player.getX(), player.getY(), player.getZ());
                        }
                        hitCount++;
                    } else if (combatType != null) {
                        boolean debuff = CombatEffects.isDebuff(combatType);
                        // Buffs keep the player's potion-potency scaling; a self-inflicted
                        // debuff uses the raw potion level so brewing mastery doesn't make
                        // the player punish themselves harder.
                        int selfAmp = debuff ? amp : scaledAmp;
                        int turns = getScaledPotionTurns(player, combatType, sei.getDuration());
                        // Live top-up so a self-splashed SPEED/HASTE applies this turn.
                        CombatManager.getActiveCombat(player.getUuid()).applyPlayerEffectLive(combatType, turns, selfAmp);
                        player.addStatusEffect(new StatusEffectInstance(
                            sei.getEffectType(), -1, selfAmp, false, true));
                        msg.append(debuff ? "§c" : "§d").append("You: ")
                            .append(combatType.displayName)
                            .append(formatPotionLevel(selfAmp)).append(" ");
                        hitCount++;
                    }
                }
            }

            if (hitCount > 0) {
                // Check deaths from AoE
                for (CombatEntity ce : aoeEnemies) {
                    CombatManager.getActiveCombat(player.getUuid()).checkAndHandleDeathPublic(ce);
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

        consumeSpecialItem(player, stack);
        int dealt = applyTypedDamage(player, enemy, 4 + enemy.percentMaxHpDamage(0.08), DamageType.SPECIAL);
        // Set mob on fire visually for 3 seconds
        if (enemy.getMobEntity() != null) {
            enemy.getMobEntity().setFireTicks(60);
        }
        return "§6Fire charge hit " + enemy.getDisplayName() + " for " + dealt + " Special damage!";
    }

    // --- Wind Charge: contextual. Thrown at an adjacent empty tile it launches
    //     the player away (mobility tool); thrown at an enemy it deals 1 Special
    //     damage and knocks them up to 3 tiles back, as before. ---
    private static String useWindCharge(ServerPlayerEntity player, GridArena arena,
                                         GridPos targetTile, ItemStack stack) {
        if (targetTile == null) return "§cNeed to target a tile!";

        GridPos playerPos = arena.getPlayerGridPos();
        int ddx = targetTile.x() - playerPos.x();
        int ddz = targetTile.z() - playerPos.z();
        boolean adjacent = Math.abs(ddx) <= 1 && Math.abs(ddz) <= 1 && !(ddx == 0 && ddz == 0);

        CombatEntity enemy = arena.getOccupant(targetTile);

        // Self-launch: an adjacent empty tile blasts the player the opposite way.
        if (adjacent && (enemy == null || !enemy.isAlive())) {
            return windChargeSelfLaunch(player, arena, stack, playerPos, ddx, ddz);
        }

        // Otherwise: knock an enemy back - the original behaviour.
        if (enemy == null || !enemy.isAlive()) return "§cNo enemy at target!";

        consumeSpecialItem(player, stack);
        int dealt = applyTypedDamage(player, enemy, 1, DamageType.SPECIAL);

        // Push the enemy up to 3 tiles directly away from the player. Stops
        // early on walls, obstacles, or other occupants; lands ON the final
        // walkable tile in the push direction.
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

    /**
     * Wind Charge self-movement: launches the player up to 2 tiles in the
     * direction opposite the targeted (adjacent) tile, stopping early at walls,
     * obstacles, or occupants. Arms the wind-charge momentum bonus so an attack
     * made immediately afterwards - with no move in between - deals 1.5x damage.
     */
    private static String windChargeSelfLaunch(ServerPlayerEntity player, GridArena arena,
                                                ItemStack stack, GridPos playerPos, int ddx, int ddz) {
        int dx = -Integer.signum(ddx);
        int dz = -Integer.signum(ddz);

        GridPos landing = playerPos;
        int moved = 0;
        for (int i = 1; i <= 2; i++) {
            GridPos candidate = new GridPos(playerPos.x() + dx * i, playerPos.z() + dz * i);
            if (!arena.isInBounds(candidate) || arena.isOccupied(candidate)) break;
            GridTile tile = arena.getTile(candidate);
            if (tile == null || !tile.isWalkable()) break;
            landing = candidate;
            moved = i;
        }

        if (moved == 0) return "§fWind charge fizzles - no room to launch!";

        consumeSpecialItem(player, stack);
        arena.setPlayerGridPos(landing);
        BlockPos bp = arena.gridToBlockPos(landing);
        player.requestTeleport(bp.getX() + 0.5, bp.getY(), bp.getZ() + 0.5);

        // Apply/stack the Airtime effect for the player's next attack.
        CombatEffects airFx = CombatManager.getActiveCombat(player.getUuid()).getCombatEffects();
        int nextAmp = Math.min(airFx.getAirtimeLevel(), CombatEffects.AIRTIME_MAX_AMPLIFIER);
        // getAirtimeLevel() is 0 when absent (-> amplifier 0 = Airtime I) and N when already
        // airborne (-> amplifier N = one level higher), so self-launching stacks Airtime,
        // capped at V. Duration is always refreshed to 1 turn.
        airFx.addEffect(CombatEffects.EffectType.AIRTIME, 1, nextAmp);
        int airLevel = airFx.getAirtimeLevel();

        if (player.getEntityWorld() instanceof ServerWorld sw) {
            sw.spawnParticles(net.minecraft.particle.ParticleTypes.GUST,
                bp.getX() + 0.5, bp.getY() + 1.0, bp.getZ() + 0.5, 8, 0.4, 0.4, 0.4, 0.02);
            sw.spawnParticles(net.minecraft.particle.ParticleTypes.SWEEP_ATTACK,
                bp.getX() + 0.5, bp.getY() + 1.0, bp.getZ() + 0.5, 2, 0.2, 0.2, 0.2, 0.0);
            sw.playSound(null, bp,
                net.minecraft.sound.SoundEvents.ENTITY_WIND_CHARGE_WIND_BURST.value(),
                net.minecraft.sound.SoundCategory.PLAYERS, 1.0f, 1.0f);
        }
        return "§fWind charge! Launched " + moved + " tile" + (moved == 1 ? "" : "s")
            + " - §bAirtime x" + airLevel + "§f (+2 range/level; next weapon hit scales up).";
    }

    // --- Elytra: 2 AP launch. Rise out of view, land on any in-bounds tile that isn't a
    // wall. Hazards are legal landings - you take whatever the tile normally does to you.
    private static String useElytra(ServerPlayerEntity player, GridArena arena,
                                     GridPos targetTile, ItemStack stack) {
        if (targetTile == null) return "§cNeed to target a tile!";
        if (!arena.isInBounds(targetTile)) return "§cTarget out of bounds!";
        GridPos playerPos = arena.getPlayerGridPos();
        if (targetTile.equals(playerPos)) return "§cYou're already there!";
        if (arena.isOccupied(targetTile)) return "§cSomething is standing there!";
        GridTile tile = arena.getTile(targetTile);
        if (tile == null) return "§cInvalid tile!";
        // Walls only. Water / lava / fire / powder snow are all legal landings.
        if (tile.getType() == com.crackedgames.craftics.core.TileType.OBSTACLE) return "§cCan't land on a wall!";

        // 2 durability, using the same manual idiom as every other Craftics durability item.
        if (stack.getDamage() + ELYTRA_DURABILITY_COST >= stack.getMaxDamage()) {
            stack.decrement(1);
        } else {
            stack.setDamage(stack.getDamage() + ELYTRA_DURABILITY_COST);
        }

        CombatManager cm = CombatManager.getActiveCombat(player.getUuid());
        cm.applyPlayerEffectLive(CombatEffects.EffectType.AIRTIME, 2, 1); // Airtime II, 2 turns
        cm.flyPlayerTo(targetTile);

        return "§b§lElytra Launch! §fYou soar up and land at (" + targetTile.x() + ","
            + targetTile.z() + ") - §bAirtime II §f(2 turns).";
    }

    // --- Milk Bucket: clears all status effects (good and bad) ---
    private static String useMilkBucket(ServerPlayerEntity player, ItemStack stack) {
        stack.decrement(1);
        player.getInventory().insertStack(new ItemStack(Items.BUCKET));
        CombatEffects effects = CombatManager.getActiveCombat(player.getUuid()).getCombatEffects();
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

        // Fuse particles - smoke rising from the placed TNT
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
        // Flint &amp; steel is a melee-range interaction - you have to stand next to
        // the target. Without this you could ignite anything anywhere on the map.
        String reach = validateAdjacentReach(arena, enemy);
        if (reach != null) return reach;
        // A Creaking with a living heart is invulnerable - destroy its heart instead.
        if (CombatManager.isInvulnerableCreaking(enemy)) {
            return "§c§lThe Creaking is invulnerable! §7Destroy its §4Creaking Heart§7 instead!";
        }

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
        // Route the buffs through the combat effect system so they function in combat
        // (the ABSORPTION hook grants the real shield). Vanilla effects kept for HUD icons.
        CombatManager cm = CombatManager.getActiveCombat(player.getUuid());
        cm.addEffectHooked(CombatEffects.EffectType.REGENERATION, 5, 1);
        cm.addEffectHooked(CombatEffects.EffectType.ABSORPTION, 5, 1);
        // Combat regen only - see the note on the enchanted golden apple. Absorption is fine:
        // it is a flat shield, not a heal-over-time.
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

        // No fishing in a cleared/safe room. With no hostile enemies present the
        // player can't be interrupted, so fishing a placed water tile turns into
        // infinite free loot. Require at least one living enemy on the grid.
        boolean hasLiveEnemy = false;
        for (CombatEntity occupant : arena.getOccupants().values()) {
            if (occupant.isAlive() && !occupant.isAlly()) { hasLiveEnemy = true; break; }
        }
        if (!hasLiveEnemy) return "§cNothing's biting - no danger, no catch. Fish during a fight!";

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

        // Enchanted books need a random stored enchantment applied, otherwise fishing
        // one up hands the player a blank "Enchanted Book" with no enchantment on it.
        // Reuse the same version-correct helper the event-room rewards use.
        ItemStack caught = loot == Items.ENCHANTED_BOOK
            ? RandomEvents.createRandomEnchantedBook(player)
            : new ItemStack(loot, count);
        LootDelivery.deliver(player, caught);
        String lootName = caught.getName().getString();
        return FISHING_PREFIX + rarity + "Caught: " + lootName + "!";
    }

    /** Prefix returned when taming/befriending succeeds - CombatManager processes this. */
    public static final String TAME_PREFIX = "§aTAME:";
    /** Prefix for passive mobs that get sent to the hub instead of becoming combat allies. */
    public static final String BEFRIEND_PREFIX = "§aBEFRIEND:";
    /** Prefix for mounting a tamed mob (horse/donkey/camel with saddle). */
    public static final String MOUNT_PREFIX = "§aMOUNT:";
    /** Prefix for TNT placement - CombatManager tracks the tile for delayed detonation. */
    public static final String TNT_PREFIX = "§6TNT:";

    /** Mobs that can be mounted in combat (require a saddle). */
    private static final Set<String> MOUNTABLE_MOBS = Set.of(
        "minecraft:horse", "minecraft:donkey", "minecraft:camel",
        "minecraft:mule", "minecraft:skeleton_horse", "minecraft:zombie_horse"
    );

    // --- Spyglass: Mark an enemy (2 AP, no consume) ---
    // Marks the target so it takes extra damage (2x; 1.5x for bosses) for the rest of
    // this turn and the next, gives it the Glowing outline, and reveals its stats. Only
    // one enemy can be marked at a time - marking a new target clears the previous mark.
    private static String useSpyglass(GridArena arena, GridPos targetTile) {
        if (targetTile == null) return "§cNeed to target an enemy!";
        CombatEntity enemy = arena.getOccupant(targetTile);
        if (enemy == null || !enemy.isAlive() || enemy.isAlly()) return "§cNo enemy at target!";

        // Only one mark at a time: clear any existing mark (and its glow) first.
        for (CombatEntity other : arena.getOccupants().values()) {
            if (other != enemy && other.isMarked()) {
                other.setMarkedTurns(0);
                if (other.getMobEntity() != null) other.getMobEntity().setGlowing(false);
            }
        }

        // Mark for 2 turns: covers the rest of this turn + the next (ticks down once per
        // round at turn start). Glowing outline mirrors the lead-on-ally highlight.
        enemy.setMarkedTurns(2);
        if (enemy.getMobEntity() != null) enemy.getMobEntity().setGlowing(true);

        return "§d✦ Marked §e" + enemy.getDisplayName() + "§d! §7(takes "
            + (enemy.isBoss() ? "1.5x" : "2x") + " damage) §8- HP: " + enemy.getCurrentHp() + "/" + enemy.getMaxHp()
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

    // --- Bell: place a bell block, AoE stun enemies within 2 tiles (2 AP). ---
    private static String useBell(GridArena arena, GridPos targetTile, ItemStack stack) {
        if (targetTile == null) return "§cNeed to target a tile!";
        if (!arena.isInBounds(targetTile)) return "§cTarget out of bounds!";
        if (arena.isOccupied(targetTile)) return "§cTile is occupied!";
        int stunned = 0;
        for (CombatEntity e : arena.getOccupants().values()) {
            if (!e.isAlive() || e.isAlly()) continue;
            int dist = Math.abs(e.getGridPos().x() - targetTile.x()) + Math.abs(e.getGridPos().z() - targetTile.z());
            if (dist <= 2) {
                e.setStunned(true);
                stunned++;
            }
        }
        stack.decrement(1);
        return TILE_EFFECT_PREFIX + "bell:" + targetTile.x() + ":" + targetTile.z()
            + "|§6Bell rings! " + stunned + " enemies stunned for 1 turn.";
    }

    // --- Lava Bucket: place lava on tile - deals damage to enemies that step on it (1 AP) ---
    private static String useLavaBucket(GridArena arena, GridPos targetTile, ItemStack stack) {
        if (targetTile == null) return "§cNeed to target a tile!";
        if (!arena.isInBounds(targetTile)) return "§cTarget out of bounds!";
        if (arena.isOccupied(targetTile)) return "§cTile is occupied!";
        GridTile tile = arena.getTile(targetTile);
        // Only pour onto a plain, walkable floor tile - not onto obstacles, hazards,
        // or existing water/lava.
        if (tile == null || !tile.isSafeForSpawn()) return "§cCan't place lava there!";
        // Empties the bucket and returns the empty bucket, matching milk/water/powder snow.
        stack.decrement(1);
        return TILE_EFFECT_PREFIX + "lava:" + targetTile.x() + ":" + targetTile.z()
            + "|GIVE:bucket|§6Placed lava! Enemies on it take 3 fire damage per turn.";
    }

    /**
     * Placement guard for special blocks (campfire, banner, jukebox, ...): they need
     * flat, solid ground. Rejects void, sunken pits, water, lava, fire, powder snow,
     * obstacles and stair ramps. Returns the refusal message, or null when placeable.
     */
    private static String requireFlatGround(GridArena arena, GridPos targetTile) {
        GridTile tile = arena.getTile(targetTile);
        if (tile == null) return "§cTarget out of bounds!";
        return switch (tile.getType()) {
            case VOID -> "§cCan't place that over the void!";
            case WATER, DEEP_WATER -> "§cCan't place that in water!";
            case LAVA -> "§cCan't place that in lava!";
            case FIRE -> "§cCan't place that in fire!";
            case LOW_GROUND -> "§cCan't place that in a sunken pit!";
            case POWDER_SNOW -> "§cCan't place that on powder snow!";
            case OBSTACLE -> "§cThat tile is blocked!";
            case STAIR -> "§cNeeds flat ground - not a stair.";
            default -> null;
        };
    }

    // --- Scaffolding: place elevated tile - gives +1 range to ranged attacks from it (1 AP) ---
    private static String useScaffolding(GridArena arena, GridPos targetTile, ItemStack stack) {
        if (targetTile == null) return "§cNeed to target a tile!";
        String ground = requireFlatGround(arena, targetTile);
        if (ground != null) return ground;
        stack.decrement(1);
        return TILE_EFFECT_PREFIX + "scaffold:" + targetTile.x() + ":" + targetTile.z()
            + "|§aPlaced scaffolding! +1 range for ranged attacks from this tile.";
    }

    // --- Campfire: place healing zone - heals 1 HP per turn when adjacent (1 AP) + creates light zone ---
    private static String useCampfire(GridArena arena, GridPos targetTile, ItemStack stack) {
        if (targetTile == null) return "§cNeed to target a tile!";
        String ground = requireFlatGround(arena, targetTile);
        if (ground != null) return ground;
        stack.decrement(1);
        return TILE_EFFECT_PREFIX + "campfire:" + targetTile.x() + ":" + targetTile.z()
            + "|§6Placed campfire! Heals 2 HP per turn while you're inside the 5x5 area + creates light (negate darkness).";
    }

    // --- Anvil: drop on enemy - falling-block animation, then %-max-HP Special damage. ---
    // The anvil has three condition stages, each dealing a fraction of the
    // target's MAX HP and degrading to the next on use:
    //   pristine  (minecraft:anvil)         -> 1/2 max HP, degrades to chipped
    //   chipped   (minecraft:chipped_anvil) -> 1/3 max HP, degrades to damaged
    //   damaged   (minecraft:damaged_anvil) -> 1/4 max HP, breaks (consumed) on use
    // Degradation is guaranteed unless Special affinity saves it: each Special
    // point grants a 10% additive chance the anvil does NOT degrade this use
    // (10 points = it never degrades). Stacks split: one use consumes one anvil
    // from the held stack and, on a degrade, hands back a single next-stage
    // anvil, so a stack of pristine anvils becomes (stack-1) pristine + 1 chipped.
    // The actual damage is deferred to the moment the falling anvil "lands" so
    // the player sees it crash down on the target. Vanilla anvil gravity damage
    // is disabled - only the Craftics %-HP damage applies.
    private static String useAnvil(ServerPlayerEntity player, GridArena arena, GridPos targetTile, ItemStack stack) {
        if (targetTile == null) return "§cNeed to target an enemy!";
        CombatEntity enemy = arena.getOccupant(targetTile);
        if (enemy == null || !enemy.isAlive()) return "§cNo enemy at target!";

        Item current = stack.getItem();
        // Damage denominator by condition: pristine 1/2, chipped 1/3, damaged 1/4.
        int denom = current == Items.CHIPPED_ANVIL ? 3
                  : current == Items.DAMAGED_ANVIL ? 4
                  : 2;
        // Floor of 10 so the anvil always lands a meaningful hit even against a
        // very low max-HP target (e.g. a 2-HP parrot at the 1/4 stage) and stays
        // useful as a guaranteed-damage tool.
        int damage = Math.max(10, enemy.getMaxHp() / denom);

        // Consume the used anvil from the (possibly stacked) held stack.
        stack.decrement(1);

        // Degrade to the next condition unless Special affinity conserves it.
        // The damaged anvil's next stage is "broken" (no item handed back).
        Item next = current == Items.ANVIL ? Items.CHIPPED_ANVIL
                  : current == Items.CHIPPED_ANVIL ? Items.DAMAGED_ANVIL
                  : null; // damaged -> breaks
        String degradeNote;
        if (rollSpecialConserveAnvil(player)) {
            // No degrade: hand back an identical anvil so the stack is unchanged.
            player.getInventory().insertStack(new ItemStack(current));
            degradeNote = " §dYour Special focus spared the anvil from wear.";
        } else if (next != null) {
            player.getInventory().insertStack(new ItemStack(next));
            degradeNote = " §7The anvil is now " + (next == Items.CHIPPED_ANVIL ? "chipped" : "damaged") + ".";
        } else {
            degradeNote = " §8The anvil shatters on impact!";
        }

        return ANVIL_DROP_PREFIX + enemy.getEntityId() + ":" + damage
            + "|§8An anvil falls toward " + enemy.getDisplayName() + "!" + degradeNote;
    }

    /**
     * Roll the anvil's degrade-conserve save: each Special affinity point grants
     * a 10% additive chance the anvil does NOT wear down this use (10+ points =
     * guaranteed). Distinct from {@link #trySpecialConserve} only in that it
     * sends no chat line (the anvil result message reports wear inline).
     */
    private static boolean rollSpecialConserveAnvil(ServerPlayerEntity player) {
        double saveChance = SpecialAffinity.points(player) * 0.10;
        return saveChance > 0 && Math.random() < saveChance;
    }

    // --- Honey Block: place sticky trap - enemies lose all movement when stepping on it (1 AP) ---
    private static String useHoneyBlock(GridArena arena, GridPos targetTile, ItemStack stack) {
        if (targetTile == null) return "§cNeed to target a tile!";
        if (!arena.isInBounds(targetTile)) return "§cTarget out of bounds!";
        if (arena.isOccupied(targetTile)) return "§cTile is occupied!";
        String ground = requireFlatGround(arena, targetTile);
        if (ground != null) return ground;
        stack.decrement(1);
        return TILE_EFFECT_PREFIX + "honey:" + targetTile.x() + ":" + targetTile.z()
            + "|§eHoney block placed! Enemies that walk onto it lose all remaining movement.";
    }

    // --- Slime Block: place a bouncy wall. Acts as a sturdy obstacle and
    //     stuns any enemy adjacent at the end of their turn (bounce effect).
    private static String useSlimeBlock(GridArena arena, GridPos targetTile, ItemStack stack) {
        if (targetTile == null) return "§cNeed to target a tile!";
        if (!arena.isInBounds(targetTile)) return "§cTarget out of bounds!";
        if (arena.isOccupied(targetTile)) return "§cTile is occupied!";
        String ground = requireFlatGround(arena, targetTile);
        if (ground != null) return ground;
        stack.decrement(1);
        return TILE_EFFECT_PREFIX + "slime:" + targetTile.x() + ":" + targetTile.z()
            + "|§aSlime block placed! Bouncy wall - pushes adjacent enemies back when they end their turn next to it.";
    }

    // --- Powder Snow Bucket: place powder snow block on a tile. Enemies who
    //     stand or step on it sink into it, get stunned + take freeze damage
    //     via the existing POWDER_SNOW tile mechanic. Bucket is returned empty.
    private static String usePowderSnow(ServerPlayerEntity player, GridArena arena, GridPos targetTile, ItemStack stack) {
        if (targetTile == null) return "§cNeed to target a tile!";
        if (!arena.isInBounds(targetTile)) return "§cTarget out of bounds!";
        if (arena.isOccupied(targetTile)) return "§cTile is occupied!";
        String ground = requireFlatGround(arena, targetTile);
        if (ground != null) return ground;
        // If an enemy is right on the target tile, freeze them immediately.
        CombatEntity enemy = arena.getOccupant(targetTile);
        String hitMsg = "";
        if (enemy != null && enemy.isAlive() && !enemy.isAlly()) {
            int dealt = applyTypedDamage(player, enemy, 1, DamageType.SPECIAL);
            enemy.setStunned(true);
            hitMsg = " §bFroze " + enemy.getDisplayName() + " for " + dealt + " Special damage!";
        }
        stack.decrement(1);
        // Empty bucket returned via GIVE: prefix, mirroring useEmptyBucket.
        return TILE_EFFECT_PREFIX + "powder_snow:" + targetTile.x() + ":" + targetTile.z()
            + "|GIVE:bucket|§bPowder snow placed! Enemies that step on it freeze." + hitMsg;
    }

    // --- Jukebox: place a jukebox block (full cube, obstacle) on a target tile.
    //     Music plays - buffs all allies +1 speed for this battle. (2 AP)
    private static String useJukebox(GridArena arena, GridPos targetTile, ItemStack stack) {
        if (targetTile == null) return "§cNeed to target a tile!";
        if (!arena.isInBounds(targetTile)) return "§cTarget out of bounds!";
        if (arena.isOccupied(targetTile)) return "§cTile is occupied!";
        GridPos playerPos = arena.getPlayerGridPos();
        int dist = Math.abs(playerPos.x() - targetTile.x()) + Math.abs(playerPos.z() - targetTile.z());
        if (dist > 1) return "§cMust place next to yourself.";
        String ground = requireFlatGround(arena, targetTile);
        if (ground != null) return ground;
        stack.decrement(1);
        int buffed = 0;
        for (CombatEntity e : arena.getOccupants().values()) {
            if (e.isAlive() && e.isAlly()) {
                e.setSpeedBonus(e.getSpeedBonus() + 3);
                buffed++;
            }
        }
        return TILE_EFFECT_PREFIX + "jukebox:" + targetTile.x() + ":" + targetTile.z()
            + "|§dMusic plays across the arena! " + buffed + " allies buffed (+3 speed for this battle).";
    }

    // --- Banner: plant defense zone - +2 defense (scaled by Special affinity)
    //     to player/allies within 2 tiles (1 AP) ---
    private static String useBanner(ServerPlayerEntity player, GridArena arena,
                                    GridPos targetTile, ItemStack stack) {
        if (targetTile == null) return "§cNeed to target a tile!";
        if (!arena.isInBounds(targetTile)) return "§cTarget out of bounds!";
        String ground = requireFlatGround(arena, targetTile);
        if (ground != null) return ground;
        String color = BannerEffects.colorIdForItem(stack.getItem());
        if (color == null) color = "white";
        int totalDef = BannerEffects.DEFENSE_BONUS + SpecialAffinity.potencyBonus(player);
        consumeSpecialItem(player, stack);
        return TILE_EFFECT_PREFIX + "banner:" + targetTile.x() + ":" + targetTile.z()
            + ":" + color + ":" + totalDef
            + "|§5Banner planted! §7+" + totalDef + " DEF for player/allies within 2 tiles.";
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
        if (arena.isOccupied(targetTile)) return "§cTile is occupied!";
        GridTile tile = arena.getTile(targetTile);
        // Only pour onto a plain, walkable floor tile - not onto obstacles, hazards,
        // or existing water.
        if (tile == null || !tile.isSafeForSpawn() || tile.isWater()) {
            return "§cCan't place water there!";
        }
        // Empties the water bucket and hands back the empty bucket, matching the
        // milk bucket so the player keeps the container.
        stack.decrement(1);
        return TILE_EFFECT_PREFIX + "water:" + targetTile.x() + ":" + targetTile.z()
            + "|GIVE:bucket|§bPlaced water! Creates a fishable water tile.";
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
        } else if (tile.getType() == com.crackedgames.craftics.core.TileType.POWDER_SNOW) {
            stack.decrement(1);
            return TILE_EFFECT_PREFIX + "clear:" + targetTile.x() + ":" + targetTile.z()
                + "|GIVE:powder_snow_bucket|§fScooped up powder snow!";
        }
        return "§cThat tile has no liquid to pick up!";
    }

    // --- Sponge: place a sponge block on a tile, also draining any adjacent
    //     water tiles. The placed block makes the tile untraversable.
    private static String useSponge(GridArena arena, GridPos targetTile, ItemStack stack) {
        if (targetTile == null) return "§cNeed to target a tile!";
        if (!arena.isInBounds(targetTile)) return "§cTarget out of bounds!";
        if (arena.isOccupied(targetTile)) return "§cTile is occupied!";
        GridPos playerPos = arena.getPlayerGridPos();
        int dist = Math.abs(playerPos.x() - targetTile.x()) + Math.abs(playerPos.z() - targetTile.z());
        if (dist > 1) return "§cMust place next to yourself.";
        // Drain adjacent water tiles in a 1-tile cross.
        int drained = 0;
        for (int[] off : new int[][]{{0,0},{1,0},{-1,0},{0,1},{0,-1}}) {
            GridPos near = new com.crackedgames.craftics.core.GridPos(
                targetTile.x() + off[0], targetTile.z() + off[1]);
            GridTile gt = arena.getTile(near);
            if (gt != null && gt.isWater()) {
                gt.setType(com.crackedgames.craftics.core.TileType.NORMAL);
                drained++;
            }
        }
        stack.decrement(1);
        return TILE_EFFECT_PREFIX + "sponge:" + targetTile.x() + ":" + targetTile.z()
            + "|§eSponge placed! Drained " + drained + " water tile" + (drained == 1 ? "" : "s") + ".";
    }

    // --- Pickaxe: three tile interactions on an adjacent tile (1 AP, durability cost) ---
    //   * OBSTACLE  -> break it, leaving a walkable floor (original behavior)
    //   * NORMAL    -> dig a 1-deep sunken pit (LOW_GROUND), walling it so the
    //                  player doesn't punch through into a large air gap below
    //   * LOW_GROUND-> deepen the pit into a VOID hole (instant-death), keeping
    //                  cobblestone interior walls and a black-concrete bottom
    private static String usePickaxe(GridArena arena, GridPos targetTile, ItemStack stack) {
        if (targetTile == null) return "§cNeed to target a tile!";
        GridTile tile = arena.getTile(targetTile);
        if (tile == null) return "§cInvalid tile!";
        if (!arena.isInBounds(targetTile)) return "§cTarget out of bounds!";
        if (arena.isOccupied(targetTile)) return "§cTile is occupied!";

        com.crackedgames.craftics.core.TileType type = tile.getType();
        boolean isObstacle = type == com.crackedgames.craftics.core.TileType.OBSTACLE;
        boolean isPit = type == com.crackedgames.craftics.core.TileType.LOW_GROUND;
        boolean isDiggableFloor = type == com.crackedgames.craftics.core.TileType.NORMAL;

        if (!isObstacle && !isPit && !isDiggableFloor) {
            return "§cYou can't mine that tile.";
        }
        if (isObstacle && tile.isPermanent()) return "§cThis obstacle is too solid to break!";

        GridPos playerPos = arena.getPlayerGridPos();
        int dist = Math.abs(playerPos.x() - targetTile.x()) + Math.abs(playerPos.z() - targetTile.z());
        if (dist > 1) return "§cToo far! Stand next to the tile.";

        if (stack.getDamage() + 1 >= stack.getMaxDamage()) {
            stack.decrement(1);
        } else {
            stack.setDamage(stack.getDamage() + 1);
        }

        if (isObstacle) {
            return TILE_EFFECT_PREFIX + "break:" + targetTile.x() + ":" + targetTile.z()
                + "|§7Obstacle broken! Tile is now walkable.";
        }
        if (isPit) {
            return TILE_EFFECT_PREFIX + "void:" + targetTile.x() + ":" + targetTile.z()
                + "|§8You dig the pit into a bottomless void! Anything that falls in is lost.";
        }
        // NORMAL floor -> sunken pit
        return TILE_EFFECT_PREFIX + "pit:" + targetTile.x() + ":" + targetTile.z()
            + "|§7You dig out a sunken pit.";
    }

    /** Base damage of a crossbow bolt fired as an item action, before Special affinity. */
    private static final int CROSSBOW_BOLT_DAMAGE = 3;

    // --- Crossbow: fire a bolt at 4-tile range (2 AP, durability cost) ---
    private static String useCrossbow(ServerPlayerEntity player, GridArena arena,
                                      GridPos targetTile, ItemStack stack) {
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
        int dealt = applyTypedDamage(player, enemy, CROSSBOW_BOLT_DAMAGE, DamageType.RANGED);
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
        consumeSpecialItem(player, stack);

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
        cloud.setDuration(999999); // effectively permanent - cleaned up when combat ends
        cloud.setRadiusGrowth(0); // don't shrink - stays until combat ends
        cloud.setWaitTime(0);
        if (potionContents != null) {
            // Set the full potion contents - handles color, effects, and particles automatically
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
        String ground = requireFlatGround(arena, targetTile);
        if (ground != null) return ground;
        stack.decrement(1);
        return TILE_EFFECT_PREFIX + "lightning:" + targetTile.x() + ":" + targetTile.z()
            + "|§eLightning rod placed! It will strike all nearby enemies next turn.";
    }

    // --- Cactus: place an obstacle wall that deals 1 damage to adjacent enemies (1 AP) ---
    private static String useCactus(GridArena arena, GridPos targetTile, ItemStack stack) {
        if (targetTile == null) return "§cNeed to target a tile!";
        if (!arena.isInBounds(targetTile)) return "§cTarget out of bounds!";
        if (arena.isOccupied(targetTile)) return "§cTile is occupied!";
        String ground = requireFlatGround(arena, targetTile);
        if (ground != null) return ground;
        stack.decrement(1);
        return TILE_EFFECT_PREFIX + "cactus:" + targetTile.x() + ":" + targetTile.z()
            + "|§2Cactus placed! It blocks movement and pricks adjacent enemies for 1 damage.";
    }

    /**
     * Per-ally heal item: if {@code targetTile} holds an ally whose registered
     * {@code AllyEntry.healItem} is the held item, heal it for {@code healAmount}
     * HP (e.g. iron ingot on an iron golem, snowball on a snow golem). Returns
     * {@code null} when this isn't a heal interaction, so the item falls through
     * to its normal combat use.
     */
    private static String tryHealAlly(GridArena arena, GridPos targetTile, Item item, ItemStack held) {
        if (targetTile == null) return null;
        CombatEntity ally = arena.getOccupant(targetTile);
        if (ally == null || !ally.isAlive() || !ally.isAlly()) return null;
        com.crackedgames.craftics.api.registry.AllyEntry entry =
            com.crackedgames.craftics.api.registry.AllyRegistry.getOrNull(ally.getEntityTypeId());
        if (entry == null || entry.healItem() == null || entry.healItem() != item) return null;
        if (ally.getCurrentHp() >= ally.getMaxHp()) {
            return "§c" + ally.getDisplayName() + " is already at full health!";
        }
        held.decrement(1);
        int healed = Math.min(entry.healAmount(), ally.getMaxHp() - ally.getCurrentHp());
        return ALLY_BUFF_PREFIX + "heal:" + ally.getEntityId() + ":" + healed
            + "|§a" + ally.getDisplayName() + " heals for " + healed + " HP!";
    }

    /**
     * Flint &amp; steel on an ignitable ally ignites it: returns an ALLY_BUFF protocol
     * string the CombatManager parses to flip the ally into its lit one-shot state.
     * Returns null when not this interaction. Flint &amp; steel is the universal
     * igniter trigger (a vanilla item) but is NOT consumed (it has durability); the
     * effect is gated on the generic
     * {@link com.crackedgames.craftics.combat.ai.ally.IgnitableAllyRegistry}, so a
     * non-ignitable ally just falls through.
     */
    private static String tryIgniteAlly(GridArena arena, GridPos targetTile, Item item) {
        if (item != Items.FLINT_AND_STEEL || targetTile == null) return null;
        CombatEntity ally = arena.getOccupant(targetTile);
        if (ally == null || !ally.isAlive() || !ally.isAlly()) return null;
        if (!com.crackedgames.craftics.combat.ai.ally.IgnitableAllyRegistry.isIgnitable(ally.getEntityTypeId())) {
            return "§cThat ally can't be ignited.";
        }
        if (ally.isLitOneShot()) return "§cThat ally is already lit!";
        return ALLY_BUFF_PREFIX + "litcoal:" + ally.getEntityId() + "|§6✦ The ally ignites!";
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
        String ground = requireFlatGround(arena, targetTile);
        if (ground != null) return ground;
        stack.decrement(1);
        return TILE_EFFECT_PREFIX + "cake:" + targetTile.x() + ":" + targetTile.z()
            + "|§dCake placed! Heals 2 HP when stepped on (3 uses).";
    }

    // --- Spore Blossom: place a spore blossom + AoE slow enemies within 3 tiles.
    //     The placed block is purely decorative (non-full-cube, tile stays walkable).
    private static String useSporeBlossom(GridArena arena, GridPos targetTile, ItemStack stack) {
        if (targetTile == null) return "§cNeed to target a tile!";
        if (!arena.isInBounds(targetTile)) return "§cTarget out of bounds!";
        if (arena.isOccupied(targetTile)) return "§cTile is occupied!";
        String ground = requireFlatGround(arena, targetTile);
        if (ground != null) return ground;
        int slowed = 0;
        for (CombatEntity e : arena.getOccupants().values()) {
            if (!e.isAlive() || e.isAlly()) continue;
            int dist = Math.abs(e.getGridPos().x() - targetTile.x()) + Math.abs(e.getGridPos().z() - targetTile.z());
            if (dist <= 3) {
                e.setSpeedBonus(e.getSpeedBonus() - 1);
                slowed++;
            }
        }
        stack.decrement(1);
        return TILE_EFFECT_PREFIX + "spore_blossom:" + targetTile.x() + ":" + targetTile.z()
            + "|§dSpore cloud! " + slowed + " enemies slowed (-1 speed).";
    }

    // --- Lantern: reveals invisible/hidden enemies in 3-tile radius (1 AP, consumes) ---
    private static String useLantern(GridArena arena, GridPos targetTile, ItemStack stack) {
        if (targetTile == null) return "§cNeed to target a tile!";
        String ground = requireFlatGround(arena, targetTile);
        if (ground != null) return ground;
        stack.decrement(1);
        return TILE_EFFECT_PREFIX + "lantern:" + targetTile.x() + ":" + targetTile.z()
            + "|§eLight zone created! Reveals hidden enemies within 3 tiles + negates darkness.";
    }

    // --- Torch: place lightweight light source (1 AP) - creates light (radius 2, negates darkness) ---
    private static String useTorch(GridArena arena, GridPos targetTile, ItemStack stack) {
        if (targetTile == null) return "§cNeed to target a tile!";
        String ground = requireFlatGround(arena, targetTile);
        if (ground != null) return ground;
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

        // The horn is not consumed - it's reusable, just costs AP
        // CombatManager will handle applying the effect via HORN_EFFECT_PREFIX
        return HORN_EFFECT_PREFIX + hornId + "|" + GoatHornEffects.getTooltip(hornId);
    }

    /** Prefix for horn effects - CombatManager applies the actual buff/debuff. */
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
        // Taming is a hands-on interaction - you must be standing next to the
        // animal. Without this you could tame mobs anywhere on the map and clear
        // rooms instantly.
        String reach = validateAdjacentReach(arena, occupant);
        if (reach != null) return reach;

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
            // Mountable mobs require a saddle - player mounts them for bonus speed
            if (MOUNTABLE_MOBS.contains(entityType)) {
                if (hasSaddle(player)) {
                    consumeSaddle(player);
                    return MOUNT_PREFIX + occupant.getEntityId();
                }
                // No saddle - tame as normal walking ally
            }
            // Returns TAME_PREFIX - CombatManager converts to ally
            return TAME_PREFIX + occupant.getEntityId();
        } else {
            // Passive mobs (cow, sheep, pig, chicken, etc.) - show hearts, vanish, sent to hub
            return BEFRIEND_PREFIX + occupant.getEntityId() + ":" + entityType;
        }
    }
}
