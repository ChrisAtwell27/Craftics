package com.crackedgames.craftics.combat;

import com.crackedgames.craftics.network.RewardRevealPayload;
import com.crackedgames.craftics.world.CrafticsSavedData;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.function.Supplier;

/**
 * Lootbox CHESTS: physical, admin-placed kiosks in the world. Right-clicking one charges
 * the player's emerald bank - or consumes a Key, a marked name tag only admins grant -
 * then plays the chest's real lid animation with sounds and a particle burst, hands the
 * rolled loot over, and presents it through the reward-reveal screen. The chest stays
 * put and can be opened again; it's a station, not a consumable.
 *
 * <p><b>Compliance.</b> Two properties keep this inside Minecraft's EULA and Usage
 * Guidelines: everything is earned in play (emeralds) or admin-granted (Keys) with no
 * real-money hooks of any kind, and the FULL odds table for every box is available to
 * every player via {@code /craftics lootbox odds <type>} - generated from the exact
 * pool data the rolls consume, so the disclosure can never drift from the behavior.
 *
 * <p>Pools are declarative ({@link Section} lists): each section is a chance plus a
 * uniform pick over prototype stacks. {@link #roll} and {@link #oddsLines} both walk
 * the same sections. Modded entries (Simply Swords legends, Basic Weapons) appear only
 * when the mod is installed; every table degrades to pure vanilla cleanly.
 *
 * <p>Registered chest positions persist in {@link CrafticsSavedData} so kiosks survive
 * restarts. Breaking a registered chest by other means leaves a dead registration that
 * simply never triggers (the block check runs on every use).
 */
public final class LootboxManager {
    private LootboxManager() {}

    /** custom_data key marking a Key name tag. */
    private static final String KEY_NBT_KEY = "craftics_lootbox_key";

    private static final Random RNG = new Random();

    public enum Type {
        WEAPONS("Weapon Cache", "§6", 20),
        ARMOR("Armor Cache", "§b", 25),
        MATERIALS("Material Crate", "§a", 10),
        SPECIAL("Special Cache", "§d", 15),
        BOOKS("Tome Cache", "§5", 30);

        public final String display;
        public final String color;
        public final int emeraldCost;

        Type(String display, String color, int emeraldCost) {
            this.display = display;
            this.color = color;
            this.emeraldCost = emeraldCost;
        }

        public static Type byName(String name) {
            for (Type t : values()) {
                if (t.name().equalsIgnoreCase(name)) return t;
            }
            return null;
        }
    }

    /**
     * One slice of a box's pool: {@code chance} to fire, {@code picks} uniform draws
     * from {@code prototypes} when it does. The odds command renders exactly this.
     */
    private record Section(String label, double chance, int picks, List<ItemStack> prototypes) {}

    // ── Post-roll polish odds ────────────────────────────────────────────────
    // These are rolled PER ITEM, independently of which section produced it, so they never
    // change what you get - only how good the thing you got is. Disclosed separately in the
    // odds table for exactly that reason.

    /** Chance a rolled weapon comes pre-enchanted. */
    private static final double WEAPON_ENCHANT_CHANCE = 0.35;
    /** Chance a rolled armor piece comes pre-enchanted. */
    private static final double ARMOR_ENCHANT_CHANCE = 0.30;
    /** Chance a rolled armor piece comes trimmed. Rolled independently of the enchant. */
    private static final double ARMOR_TRIM_CHANCE = 0.20;
    /** Chance an enchanted item gets a SECOND enchantment on top of the first. */
    private static final double SECOND_ENCHANT_CHANCE = 0.25;
    /** Highest enchant level a lootbox will roll. */
    private static final int MAX_ENCHANT_LEVEL = 3;

    /**
     * Legendary weapons - Simply Swords uniques and Simply Bows uniques, the rare boss-drop
     * tier of each mod. Every id is resolved through the registry and skipped when absent, so
     * this whole table degrades to the vanilla netherite fallback on a plain install.
     */
    private static final String[] LEGENDARY_IDS = {
        // Simply Swords uniques
        "simplyswords:arcanethyst", "simplyswords:awakened_lichblade", "simplyswords:bramblethorn",
        "simplyswords:brimstone_claymore", "simplyswords:caelestis", "simplyswords:chompolotl",
        "simplyswords:dreadtide", "simplyswords:emberblade", "simplyswords:emberlash",
        "simplyswords:enigma", "simplyswords:flamewind", "simplyswords:frostfall",
        "simplyswords:harbinger", "simplyswords:hearthflame", "simplyswords:hiveheart",
        "simplyswords:icewhisper", "simplyswords:livyatan", "simplyswords:magiblade",
        "simplyswords:magic_estoc", "simplyswords:magiscythe", "simplyswords:magispear",
        "simplyswords:mjolnir", "simplyswords:molten_edge", "simplyswords:ribboncleaver",
        "simplyswords:shadowsting", "simplyswords:slumbering_lichblade", "simplyswords:soulkeeper",
        "simplyswords:soulpyre", "simplyswords:soulrender", "simplyswords:soulstealer",
        "simplyswords:stars_edge", "simplyswords:stormbringer", "simplyswords:storms_edge",
        "simplyswords:sunfire", "simplyswords:sword_on_a_stick",
        // Simply Bows uniques - the mod nests its item paths one level deep.
        "simplybows:bee_bow/bee_bow", "simplybows:blossom_bow/blossom_bow",
        "simplybows:bubble_bow/bubble_bow", "simplybows:earth_bow/earth_bow",
        "simplybows:echo_bow/echo_bow", "simplybows:ice_bow/ice_bow",
        "simplybows:vine_bow/vine_bow"
    };

    // ── Init / tick ──────────────────────────────────────────────────────────

    /** Called once from CrafticsMod.onInitialize: the chest-click hook. */
    public static void init() {
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (world.isClient() || !(player instanceof ServerPlayerEntity sp)) return ActionResult.PASS;
            if (!(world instanceof ServerWorld sw)) return ActionResult.PASS;
            BlockPos pos = hit.getBlockPos();
            CrafticsSavedData data = CrafticsSavedData.get(sw);
            String entry = data.getLootboxChestType(sw, pos);
            if (entry == null) return ActionResult.PASS;
            if (!sw.getBlockState(pos).isOf(Blocks.CHEST)) {
                // Chest gone (broken by other means): drop the dead registration and its label.
                data.unregisterLootboxChest(sw, pos);
                LootboxPresentation.clearLabel(sw, pos);
                return ActionResult.PASS;
            }
            // Registration value is "TYPE" or "TYPE,cost" (per-chest price override).
            int comma = entry.indexOf(',');
            Type type = Type.byName(comma < 0 ? entry : entry.substring(0, comma));
            if (type == null) return ActionResult.PASS;
            int cost = type.emeraldCost;
            if (comma >= 0) {
                try {
                    cost = Integer.parseInt(entry.substring(comma + 1));
                } catch (NumberFormatException ignored) {}
            }
            openChest(sp, sw, pos, type, cost);
            return ActionResult.SUCCESS; // never open the vanilla container UI
        });
    }

    /** Pending lid-close animations, fired from the aggregate server tick. */
    private record PendingClose(ServerWorld world, BlockPos pos, long fireTick) {}
    private static final List<PendingClose> PENDING_CLOSES = new ArrayList<>();

    public static void tick(MinecraftServer server) {
        // Labels and ambient particles ride the same aggregate tick as the lid closes.
        LootboxPresentation.tick(server);
        if (PENDING_CLOSES.isEmpty()) return;
        Iterator<PendingClose> it = PENDING_CLOSES.iterator();
        while (it.hasNext()) {
            PendingClose pc = it.next();
            if (pc.world().getTime() < pc.fireTick()) continue;
            it.remove();
            if (pc.world().getBlockState(pc.pos()).isOf(Blocks.CHEST)) {
                pc.world().addSyncedBlockEvent(pc.pos(), Blocks.CHEST, 1, 0);
                pc.world().playSound(null, pc.pos(), SoundEvents.BLOCK_CHEST_CLOSE,
                    SoundCategory.BLOCKS, 0.8f, 1.0f);
            }
        }
    }

    // ── Placement ────────────────────────────────────────────────────────────

    /**
     * Place and register a lootbox chest at {@code pos}, facing {@code facing}'s
     * opposite (toward the admin who placed it). Fails when the spot isn't air.
     * {@code emeraldCost} overrides the type's standard price; 0 makes the chest
     * free to open.
     */
    public static boolean placeChest(ServerWorld world, BlockPos pos,
                                     net.minecraft.util.math.Direction facing, Type type,
                                     int emeraldCost) {
        if (!world.getBlockState(pos).isAir()) return false;
        world.setBlockState(pos, Blocks.CHEST.getDefaultState()
            .with(ChestBlock.FACING, facing.getOpposite()), 3);
        CrafticsSavedData data = CrafticsSavedData.get(world);
        data.registerLootboxChest(world, pos, emeraldCost == type.emeraldCost
            ? type.name() : type.name() + "," + Math.max(0, emeraldCost));
        return true;
    }

    /** Unregister and remove the lootbox chest at {@code pos}. */
    public static boolean removeChest(ServerWorld world, BlockPos pos) {
        CrafticsSavedData data = CrafticsSavedData.get(world);
        if (data.getLootboxChestType(world, pos) == null) return false;
        data.unregisterLootboxChest(world, pos);
        LootboxPresentation.clearLabel(world, pos);
        if (world.getBlockState(pos).isOf(Blocks.CHEST)) {
            world.breakBlock(pos, false);
        }
        return true;
    }

    // ── Keys ─────────────────────────────────────────────────────────────────

    /** A Key: opens any lootbox chest free of charge. Admin-granted only. */
    public static ItemStack createKey(int count) {
        ItemStack key = new ItemStack(Items.NAME_TAG, count);
        NbtCompound nbt = new NbtCompound();
        nbt.putBoolean(KEY_NBT_KEY, true);
        key.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
        key.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§e§lLootbox Key"));
        key.set(DataComponentTypes.LORE, new LoreComponent(List.of(
            Text.literal("§7Opens any lootbox chest at no emerald cost."),
            Text.literal("§7Consumed on use."))));
        key.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        return key;
    }

    private static boolean isKey(ItemStack stack) {
        if (stack.isEmpty() || stack.getItem() != Items.NAME_TAG) return false;
        NbtComponent data = stack.get(DataComponentTypes.CUSTOM_DATA);
        return data != null && data.copyNbt().contains(KEY_NBT_KEY);
    }

    /** The first Key in the player's inventory (offhand checked first), or null. */
    private static ItemStack findKey(ServerPlayerEntity player) {
        if (isKey(player.getOffHandStack())) return player.getOffHandStack();
        var inv = player.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            if (isKey(inv.getStack(i))) return inv.getStack(i);
        }
        return null;
    }

    // ── Opening ──────────────────────────────────────────────────────────────

    private static void openChest(ServerPlayerEntity player, ServerWorld world,
                                  BlockPos pos, Type type, int cost) {
        CrafticsSavedData data = CrafticsSavedData.get(world);
        CrafticsSavedData.PlayerData pd = data.getPlayerData(player.getUuid());

        String paidWith;
        ItemStack keyStack = cost > 0 ? findKey(player) : null;
        if (cost <= 0) {
            paidWith = "Free to open";
        } else if (keyStack != null) {
            keyStack.decrement(1);
            paidWith = "Opened with a Key";
        } else if (pd.emeralds >= cost) {
            pd.emeralds -= cost;
            data.markDirty();
            paidWith = "Paid " + cost + " emeralds (" + pd.emeralds + " left)";
        } else {
            player.sendMessage(Text.literal("§cYou need " + cost
                + " banked emeralds (or a Key). You have " + pd.emeralds
                + ". §7Odds: /craftics lootbox odds " + type.name().toLowerCase()), false);
            return;
        }

        // The chest's own lid animation plus a flourish, then a delayed close.
        world.addSyncedBlockEvent(pos, Blocks.CHEST, 1, 1);
        world.playSound(null, pos, SoundEvents.BLOCK_CHEST_OPEN, SoundCategory.BLOCKS, 1.0f, 1.0f);
        world.playSound(null, pos, SoundEvents.BLOCK_VAULT_OPEN_SHUTTER, SoundCategory.BLOCKS, 0.8f, 1.2f);
        world.spawnParticles(net.minecraft.particle.ParticleTypes.HAPPY_VILLAGER,
            pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 16, 0.4, 0.4, 0.4, 0.1);
        world.spawnParticles(net.minecraft.particle.ParticleTypes.END_ROD,
            pos.getX() + 0.5, pos.getY() + 0.9, pos.getZ() + 0.5, 10, 0.25, 0.4, 0.25, 0.05);
        LootboxPresentation.openBurst(world, pos, type);
        world.playSound(null, pos, SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.5f, 1.6f);
        PENDING_CLOSES.add(new PendingClose(world, pos.toImmutable(), world.getTime() + 30));

        List<ItemStack> rewards = roll(type, world);
        for (ItemStack reward : rewards) {
            if (!player.getInventory().insertStack(reward.copy())) {
                player.dropItem(reward.copy(), false);
            }
        }
        ServerPlayNetworking.send(player, new RewardRevealPayload(
            RewardRevealPayload.STYLE_CHEST, 1,
            type.color + "§l" + type.display, "§7" + paidWith, rewards));
    }

    // ── Pools ────────────────────────────────────────────────────────────────
    // Each type's pool is a list of Sections; roll() and oddsLines() both walk it, so
    // the disclosed odds are the executed odds by construction.

    /** Resolve a possibly-modded item id, or null when the mod isn't installed. */
    private static Item modded(String id) {
        Identifier ident = Identifier.of(id);
        return Registries.ITEM.containsId(ident) ? Registries.ITEM.get(ident) : null;
    }

    private static void addModded(List<ItemStack> pool, String... ids) {
        for (String id : ids) {
            Item item = modded(id);
            if (item != null) pool.add(new ItemStack(item));
        }
    }

    private static List<Section> sectionsFor(Type type) {
        return switch (type) {
            case WEAPONS -> {
                List<ItemStack> commons = new ArrayList<>(List.of(
                    new ItemStack(Items.STONE_SWORD), new ItemStack(Items.STONE_AXE),
                    new ItemStack(Items.STONE_HOE), new ItemStack(Items.STONE_SHOVEL),
                    new ItemStack(Items.IRON_SWORD), new ItemStack(Items.IRON_AXE),
                    new ItemStack(Items.BOW), new ItemStack(Items.STICK),
                    new ItemStack(Items.BLAZE_ROD), new ItemStack(Items.HORN_CORAL),
                    new ItemStack(Items.BRAIN_CORAL)));
                addModded(commons, "basicweapons:iron_dagger", "basicweapons:iron_spear",
                    "basicweapons:iron_club", "basicweapons:iron_quarterstaff");
                List<ItemStack> uncommons = List.of(
                    new ItemStack(Items.DIAMOND_SWORD), new ItemStack(Items.DIAMOND_AXE),
                    new ItemStack(Items.DIAMOND_HOE), new ItemStack(Items.DIAMOND_SHOVEL),
                    new ItemStack(Items.CROSSBOW), new ItemStack(Items.TRIDENT),
                    new ItemStack(Items.BREEZE_ROD));
                List<ItemStack> legends = new ArrayList<>();
                addModded(legends, LEGENDARY_IDS);
                if (legends.isEmpty()) {
                    legends.add(new ItemStack(Items.NETHERITE_SWORD));
                    legends.add(new ItemStack(Items.NETHERITE_AXE));
                }
                yield List.of(
                    new Section("Guaranteed", 1.0, 2, commons),
                    new Section("Bonus", 0.35, 1, uncommons),
                    new Section("LEGENDARY", 0.03, 1, legends));
            }
            case ARMOR -> List.of(
                new Section("Guaranteed", 1.0, 2, List.of(
                    new ItemStack(Items.CHAINMAIL_HELMET), new ItemStack(Items.CHAINMAIL_CHESTPLATE),
                    new ItemStack(Items.CHAINMAIL_LEGGINGS), new ItemStack(Items.CHAINMAIL_BOOTS),
                    new ItemStack(Items.IRON_HELMET), new ItemStack(Items.IRON_CHESTPLATE),
                    new ItemStack(Items.IRON_LEGGINGS), new ItemStack(Items.IRON_BOOTS))),
                new Section("Bonus", 0.25, 1, List.of(
                    new ItemStack(Items.DIAMOND_HELMET), new ItemStack(Items.DIAMOND_CHESTPLATE),
                    new ItemStack(Items.DIAMOND_LEGGINGS), new ItemStack(Items.DIAMOND_BOOTS))),
                new Section("Rare", 0.05, 1, List.of(
                    new ItemStack(Items.NETHERITE_HELMET), new ItemStack(Items.NETHERITE_CHESTPLATE),
                    new ItemStack(Items.NETHERITE_LEGGINGS), new ItemStack(Items.NETHERITE_BOOTS))),
                new Section("Trim bonus", 0.10, 1, List.of(
                    new ItemStack(Items.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE),
                    new ItemStack(Items.DUNE_ARMOR_TRIM_SMITHING_TEMPLATE),
                    new ItemStack(Items.COAST_ARMOR_TRIM_SMITHING_TEMPLATE),
                    new ItemStack(Items.WILD_ARMOR_TRIM_SMITHING_TEMPLATE),
                    new ItemStack(Items.HOST_ARMOR_TRIM_SMITHING_TEMPLATE))));
            case MATERIALS -> List.of(
                new Section("Guaranteed", 1.0, 4, List.of(
                    new ItemStack(Items.OAK_LOG, 24), new ItemStack(Items.COBBLESTONE, 32),
                    new ItemStack(Items.IRON_INGOT, 6), new ItemStack(Items.GOLD_INGOT, 3),
                    new ItemStack(Items.COAL, 12), new ItemStack(Items.ARROW, 24),
                    new ItemStack(Items.STRING, 6), new ItemStack(Items.LEATHER, 4))),
                new Section("Bonus", 0.15, 1, List.of(new ItemStack(Items.EMERALD, 2))));
            case SPECIAL -> {
                // EVERY special combat item, straight from the same source of truth the combat
                // code uses (ItemUseHandler.specialLootItems). The old hand-written list held
                // eleven entries out of the mod's whole special-item roster, so the cache paid
                // 15 emeralds for fire charges and pottery sherds over and over.
                List<ItemStack> everySpecial = new ArrayList<>();
                for (Item item : com.crackedgames.craftics.combat.ItemUseHandler.specialLootItems()) {
                    everySpecial.add(new ItemStack(item, specialStackSize(item)));
                }
                if (everySpecial.isEmpty()) everySpecial.add(new ItemStack(Items.FIRE_CHARGE, 3));
                yield List.of(
                    new Section("Guaranteed", 1.0, 3, everySpecial),
                    new Section("Bonus", 0.40, 1, everySpecial),
                    new Section("Jackpot", 0.08, 2, everySpecial));
            }
            case BOOKS -> List.of(); // books roll over enchant keys, not item prototypes
        };
    }

    /** Enchant keys the Tome Cache rolls: common vanilla picks plus every Craftics enchant. */
    private static List<String> bookKeys() {
        List<String> keys = new ArrayList<>(List.of(
            "sharpness", "smite", "knockback", "fire_aspect", "sweeping_edge",
            "power", "punch", "piercing", "quick_charge", "protection",
            "thorns", "unbreaking", "mending", "density", "breach"));
        for (CrafticsEnchantments.Entry e : CrafticsEnchantments.ALL) {
            keys.add(e.id());
        }
        return keys;
    }

    private static final double BOOKS_SECOND_CHANCE = 0.35;

    /** Consumables come in useful handfuls; gear and tools come as one. */
    private static int specialStackSize(Item item) {
        if (item == Items.FIRE_CHARGE || item == Items.WIND_CHARGE || item == Items.SNOWBALL
            || item == Items.EGG || item == Items.BRICK) return 4;
        if (item == Items.ENDER_PEARL || item == Items.TNT || item == Items.BONE_MEAL
            || item == Items.COBWEB || item == Items.SCAFFOLDING) return 2;
        return 1;
    }

    /**
     * Roll the polish passes on one reward: pre-enchants and armor trims.
     *
     * <p>Rolled per item and entirely separately from the pool that produced it, so the odds of
     * GETTING a diamond chestplate and the odds of it ARRIVING enchanted are independent numbers
     * - which is also how they're disclosed. Enchantments are chosen from the same
     * valid-for-this-item tables mob gear uses, so a crossbow never rolls a bow enchant.
     */
    /**
     * Which armor slot an item belongs in, or null when it isn't armor.
     *
     * <p>Matched on the registry path rather than {@code instanceof ArmorItem} or the equipment
     * component: both of those moved between 1.21.1 and 1.21.5, while the naming convention
     * hasn't - and this way modded armor sets are classified correctly too.
     */
    private static net.minecraft.entity.EquipmentSlot armorSlotOf(ItemStack stack) {
        Identifier id = Registries.ITEM.getId(stack.getItem());
        if (id == null) return null;
        String path = id.getPath();
        if (path.endsWith("helmet") || path.endsWith("_cap")) {
            return net.minecraft.entity.EquipmentSlot.HEAD;
        }
        if (path.endsWith("chestplate") || path.endsWith("_tunic")) {
            return net.minecraft.entity.EquipmentSlot.CHEST;
        }
        if (path.endsWith("leggings") || path.endsWith("_pants")) {
            return net.minecraft.entity.EquipmentSlot.LEGS;
        }
        if (path.endsWith("boots")) return net.minecraft.entity.EquipmentSlot.FEET;
        return null;
    }

    private static void polish(ItemStack stack, ServerWorld world) {
        if (stack.isEmpty()) return;
        net.minecraft.entity.EquipmentSlot slot = armorSlotOf(stack);
        boolean isArmor = slot != null;

        if (isArmor && RNG.nextDouble() < ARMOR_TRIM_CHANCE) {
            CombatManager.applyRandomTrim(stack, world);
        }

        double enchantChance = isArmor ? ARMOR_ENCHANT_CHANCE : WEAPON_ENCHANT_CHANCE;
        String[] pool = isArmor
            ? CombatManager.getValidArmorEnchants(slot)
            : CombatManager.getValidWeaponEnchants(stack);
        if (pool.length == 0 || RNG.nextDouble() >= enchantChance) return;

        int rolls = 1 + (RNG.nextDouble() < SECOND_ENCHANT_CHANCE ? 1 : 0);
        for (int i = 0; i < rolls; i++) {
            String chosen = pool[RNG.nextInt(pool.length)];
            CombatManager.applyMobEnchant(stack, chosen, 1 + RNG.nextInt(MAX_ENCHANT_LEVEL), world);
        }
    }

    private static List<ItemStack> roll(Type type, ServerWorld world) {
        if (type == Type.BOOKS) return rollBooks(world);
        List<ItemStack> out = new ArrayList<>();
        for (Section section : sectionsFor(type)) {
            if (RNG.nextDouble() >= section.chance()) continue;
            for (int i = 0; i < section.picks(); i++) {
                ItemStack rolled = section.prototypes()
                    .get(RNG.nextInt(section.prototypes().size())).copy();
                polish(rolled, world);
                out.add(rolled);
            }
        }
        // A bow without ammunition is a stick with extra steps.
        if (out.stream().anyMatch(s -> s.getItem() == Items.BOW)) {
            out.add(new ItemStack(Items.ARROW, 16));
        }
        return out;
    }

    private static List<ItemStack> rollBooks(ServerWorld world) {
        List<ItemStack> out = new ArrayList<>();
        //? if <=1.21.1 {
        var registry = world.getRegistryManager().get(RegistryKeys.ENCHANTMENT);
        //?} else {
        /*var registry = world.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT);
        *///?}
        List<String> keys = bookKeys();
        int books = 1 + (RNG.nextDouble() < BOOKS_SECOND_CHANCE ? 1 : 0);
        for (int i = 0; i < books; i++) {
            String key = keys.get(RNG.nextInt(keys.size()));
            var entry = registry.streamEntries()
                .filter(e -> e.getKey().isPresent()
                    && e.getKey().get().getValue().getPath().equals(key))
                .findFirst().orElse(null);
            if (entry == null) continue;
            int level = 1 + RNG.nextInt(Math.max(1, entry.value().getMaxLevel()));
            ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
            ItemEnchantmentsComponent.Builder builder = new ItemEnchantmentsComponent.Builder(
                book.getOrDefault(DataComponentTypes.STORED_ENCHANTMENTS,
                    ItemEnchantmentsComponent.DEFAULT));
            builder.add(entry, level);
            book.set(DataComponentTypes.STORED_ENCHANTMENTS, builder.build());
            out.add(book);
        }
        if (out.isEmpty()) out.add(new ItemStack(Items.BOOK, 3)); // registry miss fallback
        return out;
    }

    // ── Odds disclosure ──────────────────────────────────────────────────────

    /**
     * The full odds table for {@code type}, one chat line per entry, derived from the
     * SAME sections the roll consumes. This is the player-facing disclosure required to
     * run randomized rewards responsibly - never gate or hide it.
     */
    /** A 0..1 chance as a display percentage, keeping one decimal only when it needs one. */
    private static String pct(double chance) {
        double p = chance * 100.0;
        return p == Math.rint(p) ? (int) p + "%" : String.format("%.1f%%", p);
    }

    public static List<String> oddsLines(Type type) {
        List<String> lines = new ArrayList<>();
        lines.add(type.color + "§l" + type.display + " §7- standard cost §a" + type.emeraldCost
            + " emeralds§7 (a Key always works; a chest may be priced differently). Full odds:");
        if (type == Type.BOOKS) {
            List<String> keys = bookKeys();
            int pct = (int) Math.round(100.0 / keys.size());
            lines.add("§7Always: §f1 enchanted book§7; §f"
                + (int) (BOOKS_SECOND_CHANCE * 100) + "%§7 chance of a second.");
            lines.add("§7Each book: uniform over §f" + keys.size()
                + "§7 enchants (§f~" + Math.max(1, pct) + "%§7 each), level 1..max:");
            lines.add("§8" + String.join(", ", keys));
            return lines;
        }
        for (Section section : sectionsFor(type)) {
            String header = section.chance() >= 1.0
                ? "§7Always (" + section.picks() + " pick" + (section.picks() == 1 ? "" : "s") + "):"
                : "§7" + (int) Math.round(section.chance() * 100) + "% chance ("
                    + section.picks() + " pick):";
            lines.add(header + " §8each equally likely, "
                + String.format("%.1f", 100.0 / section.prototypes().size()) + "% within the section");
            StringBuilder items = new StringBuilder("§f");
            for (int i = 0; i < section.prototypes().size(); i++) {
                if (i > 0) items.append("§7, §f");
                ItemStack proto = section.prototypes().get(i);
                items.append(proto.getName().getString());
                if (proto.getCount() > 1) items.append(" x").append(proto.getCount());
            }
            lines.add(items.toString());
        }
        lines.add("§7Bows always come with 16 arrows.");
        // The polish odds are SEPARATE rolls: they never change which item you get, only how
        // good the item you got is. Disclosed as their own block for exactly that reason.
        if (type == Type.WEAPONS) {
            lines.add("§e§lEnchant roll §7(independent of the item roll):");
            lines.add("§7Each weapon: §f" + pct(WEAPON_ENCHANT_CHANCE)
                + "§7 chance to arrive enchanted.");
            lines.add("§7If enchanted: §f" + pct(SECOND_ENCHANT_CHANCE)
                + "§7 chance of a second enchantment. Levels §f1-" + MAX_ENCHANT_LEVEL
                + "§7, uniform.");
            lines.add("§8Enchantments are drawn only from those valid for that weapon type.");
        }
        if (type == Type.ARMOR) {
            lines.add("§e§lEnchant and trim rolls §7(independent of the item roll, and of each other):");
            lines.add("§7Each piece: §f" + pct(ARMOR_ENCHANT_CHANCE)
                + "§7 chance to arrive enchanted.");
            lines.add("§7If enchanted: §f" + pct(SECOND_ENCHANT_CHANCE)
                + "§7 chance of a second enchantment. Levels §f1-" + MAX_ENCHANT_LEVEL
                + "§7, uniform.");
            lines.add("§7Each piece: §f" + pct(ARMOR_TRIM_CHANCE)
                + "§7 chance to arrive trimmed (random pattern and material, both uniform).");
            lines.add("§8A piece can roll both, one, or neither - the two rolls don't affect each other.");
        }
        return lines;
    }
}
