package com.crackedgames.craftics.combat;

import com.crackedgames.craftics.network.RewardRevealPayload;
import com.crackedgames.craftics.screen.ReadOnlyMenuScreenHandler;
import com.crackedgames.craftics.world.CrafticsSavedData;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
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
     *
     * <p>{@code rarity} is display only (it drives the reveal's colour and flourish size, see
     * {@link LootboxReveal}) - the chance shown to players and the chance the roll uses are
     * both {@code chance / prototypes.size()}, computed once in {@link #oddsLines}.
     */
    private record Section(String label, LootboxRarity rarity, double chance, int picks,
                           List<ItemStack> prototypes, int minCount, int maxCount) {

        /** Section whose rewards arrive at their prototype's own stack size. */
        Section(String label, LootboxRarity rarity, double chance, int picks,
                List<ItemStack> prototypes) {
            this(label, rarity, chance, picks, prototypes, 0, 0);
        }

        /** True when this section rolls its own quantity instead of using the prototype's. */
        boolean hasCountRange() { return minCount > 0 && maxCount >= minCount; }
    }

    /** One item that came out of {@link #roll}, paired with the rarity its section carries. */
    private record RolledReward(ItemStack stack, LootboxRarity rarity) {}

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
    /** Highest enchant level a lootbox will ROLL - the die has 5 faces. The level actually
     *  applied is clamped down to the chosen enchantment's own real maximum by
     *  {@link CombatManager#applyMobEnchant} (Knockback excepted; see
     *  {@code CombatManager.ENCHANT_LEVEL_UNCAPPED}), so a single-level enchantment like
     *  Hilt or Mending never rolls higher than I. */
    private static final int MAX_ENCHANT_LEVEL = 5;

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

    /** Called once from CrafticsMod.onInitialize: the chest-click hooks. */
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
            ChestConfig config = resolveChestConfig(entry);
            if (config == null) return ActionResult.PASS;
            if (!CrafticsSavedData.get(sw).areLootboxesEnabled()) {
                // SUCCESS, not PASS. Passing would drop the click through to the next use
                // handler - in the lobby that is the spawn protection, which would answer a
                // temporarily closed kiosk with "the lobby is protected" and tell the player
                // the wrong thing entirely.
                sp.sendMessage(net.minecraft.text.Text.literal(
                    "§eThis lootbox is closed right now."), true);
                return ActionResult.SUCCESS;
            }
            if (config.cost() > 0 && findKey(sp) != null) {
                // Holding a valid Key: skip the confirm stop entirely and open right away -
                // openChest() is the one place that actually consumes it.
                openChest(sp, sw, pos, config.type(), config.cost());
                return ActionResult.SUCCESS;
            }
            // A stop, not a formality: price, balance and an [Odds] button, before anything is
            // charged. The [Open] icon is what actually calls confirmOpen().
            openConfirmMenu(sp, sw, pos, config.type(), config.cost());
            return ActionResult.SUCCESS; // never open the vanilla container UI
        });
        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            if (world.isClient() || !(player instanceof ServerPlayerEntity sp)) return ActionResult.PASS;
            if (!(world instanceof ServerWorld sw)) return ActionResult.PASS;
            CrafticsSavedData data = CrafticsSavedData.get(sw);
            String entry = data.getLootboxChestType(sw, pos);
            if (entry == null) return ActionResult.PASS;
            if (!sw.getBlockState(pos).isOf(Blocks.CHEST)) {
                data.unregisterLootboxChest(sw, pos);
                LootboxPresentation.clearLabel(sw, pos);
                return ActionResult.PASS;
            }
            ChestConfig config = resolveChestConfig(entry);
            if (config == null) return ActionResult.PASS;
            // Punching a lootbox chest shows the odds - and only the odds. Cancelling here
            // (any non-PASS result on the logical server) is what keeps a registered kiosk
            // from ever taking damage or breaking from a punch.
            openOddsMenu(sp, config.type(), sw, null);
            return ActionResult.SUCCESS;
        });
        // A crash mid-reveal can leave a rising item display behind; sweep any on restart.
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTED.register(
            LootboxReveal::sweepLeftovers);
        // The full-registry pools (weapons, armor, trims, enchantments) are swept once and
        // cached - see the "── Pools ──" section below. A fresh world or a successful /reload
        // can change what's in those registries (a datapack enchantment, a mod loaded into a
        // new world), so both events drop the cache and let the next lootbox use rebuild it.
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTED.register(
            server -> invalidatePoolCaches());
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.END_DATA_PACK_RELOAD.register(
            (server, resourceManager, success) -> {
                if (success) invalidatePoolCaches();
            });
    }

    /** Registration value decoded: which type a chest is, and what it costs to open. */
    private record ChestConfig(Type type, int cost) {}

    /** Registration value is "TYPE" or "TYPE,cost" (per-chest price override). */
    private static ChestConfig resolveChestConfig(String entry) {
        int comma = entry.indexOf(',');
        Type type = Type.byName(comma < 0 ? entry : entry.substring(0, comma));
        if (type == null) return null;
        int cost = type.emeraldCost;
        if (comma >= 0) {
            try {
                cost = Integer.parseInt(entry.substring(comma + 1));
            } catch (NumberFormatException ignored) {}
        }
        return new ChestConfig(type, cost);
    }

    // ── The confirm stop ─────────────────────────────────────────────────────
    // "Open this box for N emeralds?" - price and balance on screen, odds one click away,
    // before anything is charged. Nothing here rolls anything; clicking [Open it] just runs
    // confirmOpen(), which re-validates the chest and hands off to openChest() - the same
    // charge/roll/deliver/log path a Key or an emerald balance always goes through.
    //
    // Both this and the odds menu below are plain chests full of icon ItemStacks with custom
    // names and lore, opened through ReadOnlyMenuScreenHandler wrapping a vanilla generic
    // container type - no custom client screen, no networking. Ported layout from the
    // reference plugin's LootboxConfirmMenu: box at slot 4, confirm at 11, odds at 13,
    // cancel at 15, in a 27-slot (3-row) inventory.

    private static final int CONFIRM_SIZE = 27;
    private static final int CONFIRM_BOX_SLOT = 4;
    private static final int CONFIRM_CONFIRM_SLOT = 11;
    private static final int CONFIRM_ODDS_SLOT = 13;
    private static final int CONFIRM_CANCEL_SLOT = 15;

    /**
     * Turn a legacy §-coded string into a properly styled {@link Text}.
     *
     * <p>{@code Text.literal("§6§lLEGENDARY")} does NOT produce gold bold text. It produces a
     * component whose literal CONTENT is the characters {@code §6§lLEGENDARY} and whose style
     * is empty. Vanilla's tooltip renderer happens to still interpret legacy codes as it
     * draws, which is why this looked fine - but that is a courtesy of one renderer, not a
     * property of the component. Anything that draws the tooltip from the component itself
     * reads the string as-is, and the codes come out as literal text: "6lLEGENDARY". A tooltip
     * mod doing its own rendering is the usual way this surfaces, and it is right to - the
     * component never said it was gold.
     *
     * <p>So parse the codes into real {@link Style} here and let the styling be structural.
     * Every renderer then agrees, with or without another mod in the way.
     *
     * <p>Italics are explicitly disabled at the root: a custom item name is rendered italic by
     * default, which is not what any of these menu labels want.
     */
    private static Text legacyText(String raw) {
        MutableText out = Text.empty().setStyle(Style.EMPTY.withItalic(false));
        Style style = Style.EMPTY.withItalic(false);
        StringBuilder buf = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '§' && i + 1 < raw.length()) {
                if (buf.length() > 0) {
                    out.append(Text.literal(buf.toString()).setStyle(style));
                    buf.setLength(0);
                }
                Formatting fmt = Formatting.byCode(raw.charAt(++i));
                if (fmt == null) continue;
                // A colour code clears the formatting flags, exactly as it does in the legacy
                // scheme - so §6§l is gold+bold but §l§6 is plain gold.
                if (fmt == Formatting.RESET) style = Style.EMPTY.withItalic(false);
                else if (fmt.isColor()) style = Style.EMPTY.withItalic(false).withColor(fmt);
                else style = style.withFormatting(fmt);
                continue;
            }
            buf.append(c);
        }
        if (buf.length() > 0) out.append(Text.literal(buf.toString()).setStyle(style));
        return out;
    }

    /** Sets a display item's name and lore. The one place both menus build an icon, so the
     *  component-setting idiom (stable across every shard this mod targets) lives once. */
    private static void setDisplay(ItemStack stack, String name, List<String> lore) {
        stack.set(DataComponentTypes.CUSTOM_NAME, legacyText(name));
        stack.set(DataComponentTypes.LORE,
            new LoreComponent(lore.stream().<Text>map(LootboxManager::legacyText).toList()));
    }

    /** Places a single-count display icon (name + lore) into a menu inventory slot. */
    private static void setIcon(SimpleInventory inv, int slot, ItemStack baseIcon,
                                String name, List<String> lore) {
        ItemStack icon = baseIcon.copy();
        icon.setCount(1);
        setDisplay(icon, name, lore);
        com.crackedgames.craftics.screen.MenuIcons.mark(icon);
        inv.setStack(slot, icon);
    }

    /** Shows the price, the player's own balance, and buttons to open, view odds, or cancel. */
    public static void openConfirmMenu(ServerPlayerEntity player, ServerWorld world, BlockPos pos,
                                       Type type, int cost) {
        CrafticsSavedData data = CrafticsSavedData.get(world);
        int balance = data.getPlayerData(player.getUuid()).emeralds;
        boolean hasKey = cost > 0 && findKey(player) != null;
        boolean affordable = cost <= 0 || hasKey || balance >= cost;

        SimpleInventory inv = new SimpleInventory(CONFIRM_SIZE);

        List<String> boxLore = new ArrayList<>();
        boxLore.add(cost <= 0 ? "§aFree to open" : "§f" + cost + " §7emeralds §8(or a Key)");
        boxLore.add("§7Your balance: §e" + balance + " emeralds" + (hasKey ? " §8(+ a Key)" : ""));
        setIcon(inv, CONFIRM_BOX_SLOT, new ItemStack(Items.CHEST), type.color + "§l" + type.display, boxLore);

        if (affordable) {
            setIcon(inv, CONFIRM_CONFIRM_SLOT, new ItemStack(Items.LIME_DYE), "§a§lOpen it", List.of(
                cost <= 0 ? "§7This one is free."
                    : hasKey ? "§7Consumes a Key."
                    : "§7Spends §f" + cost + " §7emeralds."));
        } else {
            setIcon(inv, CONFIRM_CONFIRM_SLOT, new ItemStack(Items.GRAY_DYE), "§8Not enough", List.of(
                "§7You need §f" + (cost - balance) + " §7more (or a Key)."));
        }

        setIcon(inv, CONFIRM_ODDS_SLOT, new ItemStack(Items.BOOK), "§b§lView the odds",
            List.of("§7Every reward and its exact chance."));
        setIcon(inv, CONFIRM_CANCEL_SLOT, new ItemStack(Items.BARRIER), "§c§lCancel",
            List.of("§7Closes without spending anything."));

        player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
            (syncId, playerInv, viewer) -> new ReadOnlyMenuScreenHandler(
                ScreenHandlerType.GENERIC_9X3, syncId, playerInv, inv, CONFIRM_SIZE / 9,
                slot -> handleConfirmClick(slot, player, world, pos, type, cost)),
            Text.literal(type.color + "Open " + type.display)));
    }

    /** Routes a click in the confirm menu. Confirming re-validates affordability defensively
     *  (the icon already reflects it, but balance can change while the menu is open) and then
     *  calls {@link #confirmOpen} - never rolls or charges anything itself. */
    private static void handleConfirmClick(int slot, ServerPlayerEntity player, ServerWorld world,
                                           BlockPos pos, Type type, int cost) {
        if (slot == CONFIRM_ODDS_SLOT) {
            openOddsMenu(player, type, world, () -> openConfirmMenu(player, world, pos, type, cost));
        } else if (slot == CONFIRM_CANCEL_SLOT) {
            player.closeHandledScreen();
        } else if (slot == CONFIRM_CONFIRM_SLOT) {
            CrafticsSavedData data = CrafticsSavedData.get(world);
            int balance = data.getPlayerData(player.getUuid()).emeralds;
            boolean hasKey = cost > 0 && findKey(player) != null;
            if (cost > 0 && !hasKey && balance < cost) {
                player.sendMessage(Text.literal("§cYou need " + (cost - balance)
                    + " more emeralds (or a Key)."), false);
                return;
            }
            player.closeHandledScreen();
            confirmOpen(player, world, pos);
        }
        // The box icon and any empty filler slot do nothing; the click is already swallowed.
    }

    /** Re-validates the chest and performs the real open. Called from the [Open it] click
     *  (and still reachable via the legacy {@code /craftics lootbox confirmopen} command). */
    public static boolean confirmOpen(ServerPlayerEntity player, ServerWorld world, BlockPos pos) {
        if (!world.getBlockState(pos).isOf(Blocks.CHEST)) return false;
        CrafticsSavedData data = CrafticsSavedData.get(world);
        String entry = data.getLootboxChestType(world, pos);
        if (entry == null) return false;
        ChestConfig config = resolveChestConfig(entry);
        if (config == null) return false;
        openChest(player, world, pos, config.type(), config.cost());
        return true;
    }

    // ── The odds menu ────────────────────────────────────────────────────────
    // One slot per SECTION rather than per item: the weapon/armor/enchantment pools are full
    // registry sweeps that can run into the hundreds of entries, and a fixed-size inventory
    // cannot give each one its own slot. Every number shown is read straight off the same
    // Section list (or, for Tome Caches, the same allEnchantments() sweep) that oddsLines()
    // renders to chat and roll() draws from - so the menu, the chat command and the actual
    // roll can never disagree about what's winnable or how likely it is.

    private static final int ODDS_ROWS = 2;
    private static final int ODDS_SIZE = ODDS_ROWS * 9;
    private static final int ODDS_BACK_SLOT = ODDS_SIZE - 1;

    /** Up to five example item names from a pool, for the "a few examples" lore line. */
    private static List<String> exampleNames(List<ItemStack> prototypes, int max) {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < Math.min(max, prototypes.size()); i++) {
            ItemStack proto = prototypes.get(i);
            String name = proto.getName().getString();
            names.add(proto.getCount() > 1 ? name + " x" + proto.getCount() : name);
        }
        return names;
    }

    /** The lore for one section's icon: rarity, trigger chance, picks, quantity range (if
     *  any), the per-item chance within the section, and a few example items. */
    private static List<String> sectionLore(Section section) {
        List<String> lore = new ArrayList<>();
        lore.add(section.rarity().legacyColor + section.rarity().label);
        lore.add("");
        lore.add(section.chance() >= 1.0
            ? "§aAlways triggers"
            : "§f" + pct(section.chance()) + " §achance to trigger");
        lore.add("§f" + section.picks() + " §apick" + (section.picks() == 1 ? "" : "s")
            + " when it triggers");
        if (section.hasCountRange()) {
            lore.add("§f" + section.minCount() + "-" + section.maxCount() + " §aof it");
        }
        int poolSize = Math.max(1, section.prototypes().size());
        lore.add("§f" + LootboxOdds.format(LootboxOdds.chanceOf(1, poolSize))
            + " §aper item §7(each equally likely)");
        lore.add("");
        if (section.prototypes().size() > NAME_LIST_THRESHOLD) {
            lore.add("§b" + section.prototypes().size() + " possible items");
            lore.add("§7Every matching item currently in your game, vanilla and modded.");
            lore.add("§7Examples: §f" + String.join("§7, §f", exampleNames(section.prototypes(), 4)));
        } else if (!section.prototypes().isEmpty()) {
            lore.add("§7Examples:");
            List<String> shown = exampleNames(section.prototypes(), 8);
            for (String name : shown) lore.add("§f" + name);
            if (section.prototypes().size() > shown.size()) {
                lore.add("§7...and " + (section.prototypes().size() - shown.size()) + " more");
            }
        }
        return lore;
    }

    /** The weapon/armor enchant (and, for armor, trim) polish odds: rolled per item,
     *  independently of which section produced it, so they get their own icon rather than
     *  being folded into every section's lore. Mirrors the same block {@link #oddsLines}
     *  appends to chat, off the same constants. */
    private static void addPolishIcon(SimpleInventory inv, int slot, Type type) {
        List<String> lore = new ArrayList<>();
        if (type == Type.WEAPONS) {
            lore.add("§7Rolled independently of which weapon you get:");
            lore.add("");
            lore.add("§f" + pct(WEAPON_ENCHANT_CHANCE) + " §achance to arrive enchanted");
            lore.add("§f" + pct(SECOND_ENCHANT_CHANCE) + " §achance of a second enchantment");
            lore.add("§7Levels §f1-" + MAX_ENCHANT_LEVEL + " §7, uniform §8(never above what that enchant allows)");
            lore.add("");
            lore.add("§7Only enchantments valid for that weapon can roll.");
            setIcon(inv, slot, new ItemStack(Items.ENCHANTED_BOOK), "§e§lEnchant roll", lore);
        } else if (type == Type.ARMOR) {
            lore.add("§7Rolled independently of which piece you get, and of each other:");
            lore.add("");
            lore.add("§f" + pct(ARMOR_ENCHANT_CHANCE) + " §achance to arrive enchanted");
            lore.add("§f" + pct(SECOND_ENCHANT_CHANCE) + " §achance of a second enchantment");
            lore.add("§7Levels §f1-" + MAX_ENCHANT_LEVEL + " §7, uniform §8(never above what that enchant allows)");
            lore.add("§f" + pct(ARMOR_TRIM_CHANCE) + " §achance to arrive trimmed");
            lore.add("§7(random pattern and material, both uniform)");
            setIcon(inv, slot, new ItemStack(Items.ENCHANTED_BOOK), "§e§lEnchant & trim roll", lore);
        }
    }

    /** The Tome Cache has no Sections - every enchantment currently registered is a direct
     *  pick, not a prototype list - so its two icons (the guaranteed book, and the 35%-chance
     *  second one) are built from the same {@link #allEnchantments} sweep and
     *  {@link #BOOKS_SECOND_CHANCE} constant {@link #oddsLines} uses for its chat text. */
    private static void addBooksIcons(SimpleInventory inv, ServerWorld world) {
        List<RegistryEntry<Enchantment>> entries = allEnchantments(world);
        if (entries.isEmpty()) {
            setIcon(inv, 0, new ItemStack(Items.BOOK), "§a§lPlain books", List.of(
                "§aAlways triggers", "§f3 §aplain books",
                "§7No enchantments are registered right now - this is the fallback."));
            return;
        }
        double within = LootboxOdds.chanceOf(1, entries.size());
        List<String> examples = new ArrayList<>();
        for (RegistryEntry<Enchantment> e : entries) {
            if (examples.size() >= 5) break;
            e.getKey().ifPresent(k -> examples.add(k.getValue().getPath()));
        }

        List<String> first = new ArrayList<>();
        first.add("§aAlways triggers");
        first.add("§f1 §apick");
        first.add("§f" + LootboxOdds.format(within) + " §aper enchantment §7(each equally likely)");
        first.add("§7Level 1..max, uniform");
        first.add("");
        first.add("§b" + entries.size() + " possible enchantments");
        first.add("§7Examples: §f" + String.join("§7, §f", examples));
        setIcon(inv, 0, new ItemStack(Items.ENCHANTED_BOOK), "§a§lEnchanted book", first);

        List<String> second = new ArrayList<>();
        second.add("§f" + pct(BOOKS_SECOND_CHANCE) + " §achance to trigger");
        second.add("§f1 §apick, same pool as above");
        second.add("§f" + LootboxOdds.format(within) + " §aper enchantment §7(each equally likely)");
        setIcon(inv, 1, new ItemStack(Items.ENCHANTED_BOOK), "§b§lSecond book §7(bonus)", second);
    }

    /** Every reward this box can produce, one icon per section, plus a polish-roll icon for
     *  types that have one and a back/close button. Never rolls anything - purely a read of
     *  {@link #sectionsFor} / {@link #allEnchantments}, the same data {@link #roll} consumes. */
    private static SimpleInventory buildOddsInventory(Type type, ServerWorld world) {
        SimpleInventory inv = new SimpleInventory(ODDS_SIZE);
        if (type == Type.BOOKS) {
            addBooksIcons(inv, world);
        } else {
            int slot = 0;
            for (Section section : sectionsFor(type, world)) {
                if (slot >= ODDS_BACK_SLOT) {
                    com.crackedgames.craftics.CrafticsMod.LOGGER.warn(
                        "Lootbox odds menu for {} has more sections than fit ({} slots); truncating.",
                        type, ODDS_BACK_SLOT);
                    break;
                }
                ItemStack icon = section.prototypes().isEmpty()
                    ? new ItemStack(Items.PAPER) : section.prototypes().get(0);
                setIcon(inv, slot++, icon,
                    section.rarity().legacyColor + "§l" + section.label(), sectionLore(section));
            }
            if ((type == Type.WEAPONS || type == Type.ARMOR) && slot < ODDS_BACK_SLOT) {
                addPolishIcon(inv, slot, type);
            }
        }
        return inv;
    }

    /** Opens the read-only odds menu. {@code onBack} is run when the back/close icon is
     *  clicked - reopening the confirm menu when this was reached from it, or null to just
     *  close (reached by punching the chest directly, which has no parent to return to). */
    public static void openOddsMenu(ServerPlayerEntity player, Type type, ServerWorld world, Runnable onBack) {
        SimpleInventory inv = buildOddsInventory(type, world);
        Runnable back = onBack != null ? onBack : player::closeHandledScreen;
        setIcon(inv, ODDS_BACK_SLOT, new ItemStack(Items.BARRIER),
            onBack != null ? "§c§lBack" : "§c§lClose",
            List.of(onBack != null ? "§7Back to the confirm screen." : "§7Closes this."));

        player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
            (syncId, playerInv, viewer) -> new ReadOnlyMenuScreenHandler(
                ScreenHandlerType.GENERIC_9X2, syncId, playerInv, inv, ODDS_ROWS,
                slot -> {
                    if (slot == ODDS_BACK_SLOT) back.run();
                }),
            Text.literal(type.color + "Odds: " + type.display)));
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

        // Decided, delivered and logged BEFORE any flourish plays - see LootboxReveal, whose
        // safety property this exists to satisfy: losing the animation to a disconnect or a
        // dimension change must never cost the reward, because the reward is already gone.
        List<RolledReward> rolled = roll(type, world);
        List<ItemStack> rewards = new ArrayList<>(rolled.size());
        for (RolledReward r : rolled) {
            rewards.add(r.stack());
            LootDelivery.deliver(player, r.stack().copy());
        }
        com.crackedgames.craftics.CrafticsMod.LOGGER.info(
            "{} opened a {} lootbox chest ({}): {}", player.getName().getString(),
            type.name(), paidWith, rewards.stream().map(s -> s.getCount() + "x "
                + s.getName().getString()).toList());
        ServerPlayNetworking.send(player, new RewardRevealPayload(
            RewardRevealPayload.STYLE_CHEST, 1,
            type.color + "§l" + type.display, "§7" + paidWith, rewards));

        // The in-world flourish: purely cosmetic and strictly after the reward already exists,
        // so several rewards from one open cascade one after another instead of overlapping.
        int stagger = 0;
        for (RolledReward r : rolled) {
            LootboxReveal.play(player, world, pos, r.rarity(), r.stack(),
                r.stack().getName().getString(), stagger);
            stagger += LootboxReveal.CASCADE_STAGGER;
        }
    }

    // ── Pools ────────────────────────────────────────────────────────────────
    // Each type's pool is a list of Sections; roll() and oddsLines() both walk it, so
    // the disclosed odds are the executed odds by construction.
    //
    // WEAPONS, ARMOR and the armor trim section are swept from the live item registry
    // (Registries.ITEM is static - frozen after mod init, so these are safe to cache
    // forever once built). Enchantments are a genuine dynamic registry and need a
    // ServerWorld to enumerate at all. invalidatePoolCaches() drops all four caches on
    // SERVER_STARTED and a successful /reload (see init()); the next lootbox use or
    // /odds lookup rebuilds whichever cache is null.

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

    private static java.util.Map<WeaponTier, List<ItemStack>> weaponTierCache;
    private static List<ItemStack> coralPoolCache;
    private static List<ItemStack> armorPoolCache;
    private static List<ItemStack> trimTemplatePoolCache;
    private static List<RegistryEntry<Enchantment>> enchantPoolCache;

    /** Drops every cached pool so the next use rebuilds from the current registries. */
    private static void invalidatePoolCaches() {
        weaponTierCache = null;
        coralPoolCache = null;
        armorPoolCache = null;
        trimTemplatePoolCache = null;
        enchantPoolCache = null;
        materialsWoodCache = null;
        materialsStoneCache = null;
        materialsIngotGemCache = null;
    }

    /**
     * NAME-based exclusion, not an id list: Simply Swords ships four "Relic" items
     * ({@code dormant_relic}, {@code tainted_relic}, {@code righteous_relic},
     * {@code decaying_relic} - confirmed from its lang file) that are {@code SwordItem}
     * subclasses, which is exactly why {@link #isWeaponItem} would otherwise sweep them in.
     * They are not weapons - they're progression items carried in the inventory to transform
     * into something else, and are never actually wielded in combat (they also never got a
     * {@code WeaponEntry} from {@code SimplySwordsCompat}, so a player who somehow equipped
     * one would just be hitting with bare-fist stats). Matching on the id substring "relic"
     * rather than a fixed four-id list catches whichever of the four are actually registered
     * in a given install, and any relic Simply Swords (or another mod) adds later.
     */
    private static boolean isRelicItem(Item item) {
        Identifier id = Registries.ITEM.getId(item);
        return id != null && id.getPath().contains("relic");
    }

    /**
     * Whether {@code stack} counts as a weapon for the Weapon Cache sweep. Mirrors the exact
     * definition {@code CombatManager.getValidWeaponEnchants} already uses to pick an enchant
     * pool (see {@code CrafticsEnchantments.isAxeLike}/{@code isSword}/{@code matchesBlunt}),
     * so anything that can take a Craftics weapon enchant shows up here too - including a
     * modded weapon whose Item class Craftics has never heard of, as long as it was registered
     * with a matching {@code DamageType}. Shovels and hoes count: this mod repurposes them as
     * the Pet and Special focuses, so every material's shovel/hoe is meaningful loot, not just
     * a mining tool.
     */
    private static boolean isWeaponItem(ItemStack stack) {
        Item item = stack.getItem();
        if (isRelicItem(item)) return false;
        if (item instanceof net.minecraft.item.BowItem
            || item instanceof net.minecraft.item.CrossbowItem
            || item instanceof net.minecraft.item.TridentItem
            || item instanceof net.minecraft.item.MaceItem
            || item instanceof net.minecraft.item.ShovelItem
            || item instanceof net.minecraft.item.HoeItem) {
            return true;
        }
        if (CrafticsEnchantments.isAxeLike(stack) || CrafticsEnchantments.matchesBlunt(stack)
                || CrafticsEnchantments.isSword(stack)) {
            return true;
        }
        //? if <=1.21.4 {
        return item instanceof net.minecraft.item.SwordItem;
        //?} else {
        /*return item.getRegistryEntry().isIn(net.minecraft.registry.tag.ItemTags.SWORDS);
        *///?}
    }

    /**
     * Every weapon in the game: {@link com.crackedgames.craftics.api.registry.WeaponRegistry}
     * first (vanilla plus every compat module and datapack-registered weapon - Simply Swords,
     * Simply Bows, Basic Weapons, Deeper and Darker, Paladins, Copper Age, and anything a
     * future datapack adds through {@code WeaponJsonLoader}), unioned with a structural sweep
     * of the whole item registry so a mod's sword/axe/bow/etc that nobody wrote Craftics
     * compat for still shows up. The Simply Swords/Bows legendaries and vanilla netherite
     * tools are excluded here - they're the LEGENDARY section's own curated pool, and a reward
     * should never sit in two sections at once.
     */
    /** Material tier a weapon belongs to, read from its registry id. */
    private enum WeaponTier { BASE, DIAMOND, NETHERITE }

    /**
     * Which tier an item sits in, by id substring.
     *
     * <p>Matching on the id rather than an explicit item list is what makes this work for
     * modded gear: Simply Swords and Basic Weapons ship diamond, netherite, and runic
     * variants of many weapons, and naming them item by item never kept up. Anything whose
     * id says netherite is gated behind the legendary tier; diamond and runic fall into the
     * same rare tier, whoever added them.
     */
    private static WeaponTier tierOf(Item item) {
        Identifier id = Registries.ITEM.getId(item);
        String path = id == null ? "" : id.getPath();
        if (path.contains("netherite")) return WeaponTier.NETHERITE;
        if (path.contains("diamond") || path.contains("runic")) return WeaponTier.DIAMOND;
        return WeaponTier.BASE;
    }

    /** Every weapon in the game, split by material tier. Built once, cached. */
    private static java.util.Map<WeaponTier, List<ItemStack>> weaponTiers() {
        if (weaponTierCache != null) return weaponTierCache;
        java.util.Set<Item> exclude = new java.util.HashSet<>();
        for (String id : LEGENDARY_IDS) {
            Item item = modded(id);
            if (item != null) exclude.add(item);
        }

        java.util.LinkedHashSet<Item> found = new java.util.LinkedHashSet<>(
            com.crackedgames.craftics.api.registry.WeaponRegistry.registeredItems());
        for (Item item : Registries.ITEM) {
            if (item == Items.AIR) continue;
            if (isWeaponItem(new ItemStack(item))) found.add(item);
        }
        found.removeAll(exclude);
        // Belt and suspenders: isWeaponItem already refuses relics (see isRelicItem), but a
        // relic could in principle also arrive via WeaponRegistry.registeredItems() if some
        // future compat module ever registered one directly. Named explicitly, right where
        // the LEGENDARY exclusion above already carves out the pool, so it stays obvious why.
        found.removeIf(LootboxManager::isRelicItem);

        java.util.Map<WeaponTier, List<ItemStack>> tiers = new java.util.EnumMap<>(WeaponTier.class);
        for (WeaponTier tier : WeaponTier.values()) tiers.put(tier, new ArrayList<>());
        for (Item item : found) tiers.get(tierOf(item)).add(new ItemStack(item));

        // A pool that ended up empty would make its section unrollable, so give each a floor.
        if (tiers.get(WeaponTier.BASE).isEmpty()) tiers.get(WeaponTier.BASE).add(new ItemStack(Items.IRON_SWORD));
        if (tiers.get(WeaponTier.DIAMOND).isEmpty()) tiers.get(WeaponTier.DIAMOND).add(new ItemStack(Items.DIAMOND_SWORD));
        if (tiers.get(WeaponTier.NETHERITE).isEmpty()) tiers.get(WeaponTier.NETHERITE).add(new ItemStack(Items.NETHERITE_SWORD));

        java.util.Map<WeaponTier, List<ItemStack>> frozen = new java.util.EnumMap<>(WeaponTier.class);
        tiers.forEach((tier, list) -> frozen.put(tier, List.copyOf(list)));
        weaponTierCache = java.util.Collections.unmodifiableMap(frozen);
        return weaponTierCache;
    }

    /** Weapons of no special material: everything below diamond. */
    private static List<ItemStack> weaponPool() {
        return weaponTiers().get(WeaponTier.BASE);
    }

    /** Diamond-tier weapons, vanilla and modded. Rare tier only. */
    private static List<ItemStack> diamondWeaponPool() {
        return weaponTiers().get(WeaponTier.DIAMOND);
    }

    /** Netherite-tier weapons, vanilla and modded. Legendary tier only. */
    private static List<ItemStack> netheriteWeaponPool() {
        return weaponTiers().get(WeaponTier.NETHERITE);
    }

    /**
     * Every live coral, plus a single dead one. Swept by id so a mod's own coral joins in.
     * Dead coral is deliberately capped at one entry: five near-identical grey variants would
     * crowd out the colourful ones they are supposed to be a downgrade from.
     */
    private static List<ItemStack> coralPool() {
        if (coralPoolCache != null) return coralPoolCache;
        List<ItemStack> pool = new ArrayList<>();
        ItemStack firstDead = null;
        for (Item item : Registries.ITEM) {
            if (item == Items.AIR) continue;
            Identifier id = Registries.ITEM.getId(item);
            if (id == null) continue;
            String path = id.getPath();
            // The plain coral item only: not fans, not blocks.
            if (!path.endsWith("_coral")) continue;
            if (path.startsWith("dead_")) {
                if (firstDead == null) firstDead = new ItemStack(item);
                continue;
            }
            pool.add(new ItemStack(item));
        }
        if (firstDead != null) pool.add(firstDead);
        if (pool.isEmpty()) pool.add(new ItemStack(Items.HORN_CORAL));
        coralPoolCache = List.copyOf(pool);
        return coralPoolCache;
    }

    /**
     * Every armor piece in the game, vanilla and modded, classified the same way
     * {@link #armorSlotOf} already does for the enchant/trim polish pass. Netherite is
     * excluded - it's reserved for the Rare section below so it stays a step up from the
     * general pool instead of being diluted into it.
     */
    private static List<ItemStack> armorPool() {
        if (armorPoolCache != null) return armorPoolCache;
        java.util.Set<Item> exclude = java.util.Set.of(
            Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE,
            Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS);
        List<ItemStack> pool = new ArrayList<>();
        for (Item item : Registries.ITEM) {
            if (item == Items.AIR || exclude.contains(item)) continue;
            ItemStack candidate = new ItemStack(item);
            if (armorSlotOf(candidate) != null) pool.add(candidate);
        }
        armorPoolCache = List.copyOf(pool);
        return armorPoolCache;
    }

    /**
     * Every armor trim smithing template in the game. Trim TEMPLATE items are a static
     * registry concern, not a dynamic one: the trim PATTERN record that used to carry a
     * reference to its template item ({@code templateItem()}) lost that field on the newer
     * shards, so the only cross-version-safe link left is the naming convention vanilla
     * itself uses (and this project already relies on elsewhere for armor slot matching) -
     * every trim template's id ends in "_armor_trim_smithing_template". A mod adding its own
     * trim patterns picks this pool up automatically as long as it follows that convention.
     */
    private static List<ItemStack> trimTemplatePool() {
        if (trimTemplatePoolCache != null) return trimTemplatePoolCache;
        List<ItemStack> pool = new ArrayList<>();
        for (Item item : Registries.ITEM) {
            if (item == Items.AIR) continue;
            Identifier id = Registries.ITEM.getId(item);
            if (id != null && id.getPath().endsWith("_armor_trim_smithing_template")) {
                pool.add(new ItemStack(item));
            }
        }
        trimTemplatePoolCache = List.copyOf(pool);
        return trimTemplatePoolCache;
    }

    // ── Material Crate pools ─────────────────────────────────────────────────
    // Sweeps rather than hand-written lists, for the same reason the weapon and armor pools
    // are: a mod's new wood set, ore tier or gem should show up on its own. Vanilla item tags
    // are used where one exists and covers modded content by convention; the rest match on the
    // registry id, which is how this file already finds trim templates and armor slots.

    private static List<ItemStack> materialsWoodCache;
    private static List<ItemStack> materialsStoneCache;
    private static List<ItemStack> materialsIngotGemCache;

    /** Every log and plank in the game, via the tags mods are expected to populate. */
    private static List<ItemStack> woodPool() {
        if (materialsWoodCache != null) return materialsWoodCache;
        List<ItemStack> pool = new ArrayList<>();
        addTagged(pool, net.minecraft.registry.tag.ItemTags.LOGS);
        addTagged(pool, net.minecraft.registry.tag.ItemTags.PLANKS);
        if (pool.isEmpty()) pool.add(new ItemStack(Items.OAK_LOG));
        materialsWoodCache = List.copyOf(pool);
        return materialsWoodCache;
    }

    /** Stone and its cousins. No single vanilla tag covers these, so match on the id. */
    private static List<ItemStack> stonePool() {
        if (materialsStoneCache != null) return materialsStoneCache;
        String[] needles = {"cobblestone", "deepslate", "andesite", "diorite", "granite",
            "basalt", "blackstone", "tuff", "calcite", "dripstone", "sandstone", "netherrack",
            "end_stone", "prismarine"};
        List<ItemStack> pool = new ArrayList<>();
        for (Item item : Registries.ITEM) {
            if (item == Items.AIR) continue;
            Identifier id = Registries.ITEM.getId(item);
            if (id == null) continue;
            String path = id.getPath();
            boolean match = path.equals("stone") || path.equals("smooth_stone");
            for (String needle : needles) {
                if (path.contains(needle)) { match = true; break; }
            }
            // Slabs, stairs, walls and buttons are builds, not raw material.
            if (match && !path.endsWith("_slab") && !path.endsWith("_stairs")
                    && !path.endsWith("_wall") && !path.endsWith("_button")
                    && !path.endsWith("_pressure_plate")) {
                pool.add(new ItemStack(item));
            }
        }
        if (pool.isEmpty()) pool.add(new ItemStack(Items.COBBLESTONE));
        materialsStoneCache = List.copyOf(pool);
        return materialsStoneCache;
    }

    /** Ingots, gems and raw ore drops. Ids ending in "_ingot" catch modded tiers for free. */
    private static List<ItemStack> ingotGemPool() {
        if (materialsIngotGemCache != null) return materialsIngotGemCache;
        List<ItemStack> pool = new ArrayList<>();
        for (Item item : Registries.ITEM) {
            if (item == Items.AIR) continue;
            Identifier id = Registries.ITEM.getId(item);
            if (id == null) continue;
            String path = id.getPath();
            // Netherite is the legendary tier's job, not this one.
            if (path.contains("netherite")) continue;
            if (path.endsWith("_ingot") || path.endsWith("_gem") || path.startsWith("raw_")
                    || path.equals("diamond") || path.equals("emerald") || path.equals("coal")
                    || path.equals("lapis_lazuli") || path.equals("redstone")
                    || path.equals("quartz") || path.equals("amethyst_shard")
                    || path.equals("copper_ingot")) {
                pool.add(new ItemStack(item));
            }
        }
        if (pool.isEmpty()) pool.add(new ItemStack(Items.IRON_INGOT));
        materialsIngotGemCache = List.copyOf(pool);
        return materialsIngotGemCache;
    }

    /** Farm produce. Hand-listed: no tag groups "things you grow" usefully. */
    private static List<ItemStack> cropPool() {
        List<ItemStack> pool = new ArrayList<>(List.of(
            new ItemStack(Items.WHEAT), new ItemStack(Items.CARROT), new ItemStack(Items.POTATO),
            new ItemStack(Items.BEETROOT), new ItemStack(Items.MELON_SLICE),
            new ItemStack(Items.PUMPKIN), new ItemStack(Items.SUGAR_CANE),
            new ItemStack(Items.COCOA_BEANS), new ItemStack(Items.NETHER_WART),
            new ItemStack(Items.SWEET_BERRIES), new ItemStack(Items.GLOW_BERRIES),
            new ItemStack(Items.BAMBOO), new ItemStack(Items.KELP), new ItemStack(Items.APPLE),
            new ItemStack(Items.WHEAT_SEEDS), new ItemStack(Items.BROWN_MUSHROOM),
            new ItemStack(Items.RED_MUSHROOM), new ItemStack(Items.CACTUS)));
        return List.copyOf(pool);
    }

    /** Utility staples, including the liquid buckets that do not stack. */
    private static List<ItemStack> miscMaterialPool() {
        return List.of(
            new ItemStack(Items.BOOK), new ItemStack(Items.PAPER), new ItemStack(Items.STRING),
            new ItemStack(Items.LEATHER), new ItemStack(Items.STICK), new ItemStack(Items.TORCH),
            new ItemStack(Items.ARROW), new ItemStack(Items.FLINT), new ItemStack(Items.CLAY_BALL),
            new ItemStack(Items.GUNPOWDER), new ItemStack(Items.SLIME_BALL),
            new ItemStack(Items.WATER_BUCKET), new ItemStack(Items.LAVA_BUCKET),
            new ItemStack(Items.MILK_BUCKET), new ItemStack(Items.POWDER_SNOW_BUCKET));
    }

    private static List<ItemStack> obsidianPool() {
        return List.of(new ItemStack(Items.OBSIDIAN), new ItemStack(Items.CRYING_OBSIDIAN));
    }

    /** The material tier worth building a box around. */
    private static List<ItemStack> legendaryMaterialPool() {
        return List.of(
            new ItemStack(Items.NETHERITE_INGOT), new ItemStack(Items.NETHERITE_SCRAP),
            new ItemStack(Items.NETHER_STAR), new ItemStack(Items.ECHO_SHARD),
            new ItemStack(Items.HEART_OF_THE_SEA), new ItemStack(Items.ANCIENT_DEBRIS));
    }

    /** Add one stack per item in a tag, skipping the tag entirely if nothing is bound to it. */
    private static void addTagged(List<ItemStack> pool,
                                  net.minecraft.registry.tag.TagKey<Item> tag) {
        for (net.minecraft.registry.entry.RegistryEntry<Item> entry : Registries.ITEM.iterateEntries(tag)) {
            Item item = entry.value();
            if (item != Items.AIR) pool.add(new ItemStack(item));
        }
    }

    private static List<Section> sectionsFor(Type type, ServerWorld world) {
        // world is unused by every branch below except (indirectly) BOOKS, which bypasses
        // this method entirely - see roll()/oddsLines(). Threaded through anyway so every
        // pool builder has access to it, since trims/enchantments are the one part of this
        // that's genuinely tied to a world's dynamic registries.
        return switch (type) {
            case WEAPONS -> {
                List<ItemStack> legends = new ArrayList<>();
                addModded(legends, LEGENDARY_IDS);
                if (legends.isEmpty()) {
                    legends.add(new ItemStack(Items.NETHERITE_SWORD));
                    legends.add(new ItemStack(Items.NETHERITE_AXE));
                    legends.add(new ItemStack(Items.NETHERITE_HOE));
                    legends.add(new ItemStack(Items.NETHERITE_SHOVEL));
                    legends.add(new ItemStack(Items.NETHERITE_PICKAXE));
                }
                // The unique-weapon ids are legendary in their own right, so they ride
                // alongside every netherite-tier weapon rather than replacing them.
                legends.addAll(netheriteWeaponPool());
                List<Section> weaponSections = new ArrayList<>(List.of(
                    new Section("Guaranteed", LootboxRarity.COMMON, 1.0, 2, weaponPool()),
                    new Section("Bonus", LootboxRarity.UNCOMMON, 0.35, 1, weaponPool()),
                    new Section("Coral", LootboxRarity.UNCOMMON, 0.30, 1, coralPool()),
                    new Section("Rare", LootboxRarity.RARE, 0.08, 1, diamondWeaponPool())));
                List<ItemStack> ultraRareWeapons = ultraRareWeaponPool();
                if (!ultraRareWeapons.isEmpty()) {
                    weaponSections.add(new Section("Ultra Rare", LootboxRarity.EPIC, 0.005, 1, ultraRareWeapons));
                }
                weaponSections.add(new Section("LEGENDARY", LootboxRarity.LEGENDARY, 0.01, 1, legends));
                yield List.copyOf(weaponSections);
            }
            case ARMOR -> {
                List<Section> armorSections = new ArrayList<>(List.of(
                    new Section("Guaranteed", LootboxRarity.COMMON, 1.0, 2, armorPool()),
                    new Section("Bonus", LootboxRarity.UNCOMMON, 0.25, 1, armorPool()),
                    new Section("Rare", LootboxRarity.LEGENDARY, 0.01, 1, List.of(
                        new ItemStack(Items.NETHERITE_HELMET), new ItemStack(Items.NETHERITE_CHESTPLATE),
                        new ItemStack(Items.NETHERITE_LEGGINGS), new ItemStack(Items.NETHERITE_BOOTS))),
                    new Section("Trim bonus", LootboxRarity.RARE, 0.10, 1, trimTemplatePool())));
                List<ItemStack> ultraRareArmor = ultraRareArmorPool();
                if (!ultraRareArmor.isEmpty()) {
                    armorSections.add(3, new Section("Ultra Rare", LootboxRarity.EPIC, 0.005, 1, ultraRareArmor));
                }
                yield List.copyOf(armorSections);
            }
            case MATERIALS -> List.of(
                new Section("Wood", LootboxRarity.COMMON, 1.0, 1, woodPool(), 16, 48),
                new Section("Stone", LootboxRarity.COMMON, 1.0, 1, stonePool(), 16, 48),
                new Section("Crops", LootboxRarity.COMMON, 0.70, 1, cropPool(), 12, 32),
                new Section("Supplies", LootboxRarity.COMMON, 0.70, 1, miscMaterialPool(), 8, 16),
                new Section("Ingots and gems", LootboxRarity.UNCOMMON, 0.55, 1, ingotGemPool(), 8, 20),
                new Section("Obsidian", LootboxRarity.RARE, 0.20, 1, obsidianPool(), 8, 12),
                // The legendary tier keeps small counts on purpose: eight nether stars out of a
                // ten emerald crate would be worth more than everything else in the box combined.
                new Section("LEGENDARY", LootboxRarity.LEGENDARY, 0.03, 1, legendaryMaterialPool(), 1, 2));
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

                List<ItemStack> artifactItems = artifactPool();
                List<ItemStack> moreTotemItems = moreTotemPool();
                List<Section> sections = new ArrayList<>();
                sections.add(new Section("Guaranteed", LootboxRarity.COMMON, 1.0, 3, everySpecial));
                sections.add(new Section("Bonus", LootboxRarity.UNCOMMON, 0.40, 1, everySpecial));
                if (!artifactItems.isEmpty()) {
                    // Artifacts are intentionally ultra-rare: just below the legendary jackpot
                    // lane, so they feel prestigious without dominating the box.
                    sections.add(new Section("Artifacts", LootboxRarity.EPIC, 0.04, 1, artifactItems));
                }
                if (!moreTotemItems.isEmpty()) {
                    sections.add(new Section("More Totems", LootboxRarity.EPIC, 0.03, 1, moreTotemItems));
                }
                sections.add(new Section("Jackpot", LootboxRarity.LEGENDARY, 0.05, 2, everySpecial));
                yield List.copyOf(sections);
            }
            case BOOKS -> List.of(); // books roll over every registered enchantment, not item prototypes
        };
    }

    /** Enchantment ids a Tome Cache reward may never carry: not "rare", strictly worse than
     *  a plain book, and a paid reward should never be a downgrade. */
    private static final java.util.Set<String> BOOKS_EXCLUDED_IDS = java.util.Set.of(
        "minecraft:binding_curse", "minecraft:vanishing_curse");

    private static List<ItemStack> ultraRareWeaponPool() {
        List<ItemStack> pool = new ArrayList<>();
        for (String path : List.of(
            "warden_sword", "warden_axe", "warden_shovel", "warden_hoe",
            "resonarium_sword", "resonarium_axe", "resonarium_shovel", "resonarium_hoe")) {
            Identifier id = Identifier.of("deeperdarker", path);
            if (Registries.ITEM.containsId(id)) {
                pool.add(new ItemStack(Registries.ITEM.get(id)));
            }
        }
        return List.copyOf(pool);
    }

    private static List<ItemStack> ultraRareArmorPool() {
        List<ItemStack> pool = new ArrayList<>();
        for (String path : List.of(
            "warden_helmet", "warden_chestplate", "warden_leggings", "warden_boots",
            "resonarium_helmet", "resonarium_chestplate", "resonarium_leggings", "resonarium_boots")) {
            Identifier id = Identifier.of("deeperdarker", path);
            if (Registries.ITEM.containsId(id)) {
                pool.add(new ItemStack(Registries.ITEM.get(id)));
            }
        }
        return List.copyOf(pool);
    }

    private static List<ItemStack> artifactPool() {
        List<ItemStack> pool = new ArrayList<>();
        for (Item item : Registries.ITEM) {
            Identifier id = Registries.ITEM.getId(item);
            if (id != null && "artifacts".equals(id.getNamespace())) {
                pool.add(new ItemStack(item, specialStackSize(item)));
            }
        }
        return List.copyOf(pool);
    }

    private static List<ItemStack> moreTotemPool() {
        if (!com.crackedgames.craftics.compat.moretotems.MoreTotemsCompat.isLoaded()) {
            return List.of();
        }
        List<ItemStack> pool = new ArrayList<>();
        for (Item item : Registries.ITEM) {
            if (com.crackedgames.craftics.compat.moretotems.MoreTotemsCompat.totemPath(item) != null) {
                pool.add(new ItemStack(item, 1));
            }
        }
        return List.copyOf(pool);
    }

    /**
     * Every enchantment currently registered - vanilla, Craftics, and any other installed
     * mod's - minus the two curses. Swept once per cache lifetime (see
     * {@link #invalidatePoolCaches}) since the enchantment registry is dynamic and a full
     * sweep on every roll or /odds lookup would be wasted work.
     */
    private static List<RegistryEntry<Enchantment>> allEnchantments(ServerWorld world) {
        if (enchantPoolCache != null) return enchantPoolCache;
        //? if <=1.21.1 {
        var registry = world.getRegistryManager().get(RegistryKeys.ENCHANTMENT);
        //?} else {
        /*var registry = world.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT);
        *///?}
        List<RegistryEntry<Enchantment>> entries = new ArrayList<>();
        registry.streamEntries().forEach(e -> {
            if (e.getKey().isEmpty()) return;
            if (BOOKS_EXCLUDED_IDS.contains(e.getKey().get().getValue().toString())) return;
            @SuppressWarnings("unchecked")
            RegistryEntry<Enchantment> cast = (RegistryEntry<Enchantment>) e;
            entries.add(cast);
        });
        enchantPoolCache = List.copyOf(entries);
        return enchantPoolCache;
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

    private static List<RolledReward> roll(Type type, ServerWorld world) {
        if (type == Type.BOOKS) return rollBooks(world);
        List<RolledReward> out = new ArrayList<>();
        for (Section section : sectionsFor(type, world)) {
            if (RNG.nextDouble() >= section.chance()) continue;
            for (int i = 0; i < section.picks(); i++) {
                ItemStack rolled = section.prototypes()
                    .get(RNG.nextInt(section.prototypes().size())).copy();
                polish(rolled, world);
                if (section.hasCountRange()) {
                    int want = section.minCount()
                        + RNG.nextInt(section.maxCount() - section.minCount() + 1);
                    // A material section's quantity floor applies even to items that do not
                    // stack (buckets are the common case), so hand those over as several
                    // single stacks rather than silently clamping the reward down to one.
                    int perStack = Math.max(1, rolled.getMaxCount());
                    while (want > 0) {
                        ItemStack part = rolled.copy();
                        part.setCount(Math.min(perStack, want));
                        out.add(new RolledReward(part, section.rarity()));
                        want -= part.getCount();
                    }
                } else {
                    out.add(new RolledReward(rolled, section.rarity()));
                }
            }
        }
        // A bow without ammunition is a stick with extra steps.
        if (out.stream().anyMatch(r -> r.stack().getItem() == Items.BOW)) {
            out.add(new RolledReward(new ItemStack(Items.ARROW, 16), LootboxRarity.COMMON));
        }
        return out;
    }

    /** First book UNCOMMON, the 35%-chance second book RARE - the Tome Cache has no Sections;
     *  every possible enchantment is a direct pick from {@link #allEnchantments}, not a
     *  lookup by id, so there's no collision risk between two mods sharing a path. */
    private static List<RolledReward> rollBooks(ServerWorld world) {
        List<RolledReward> out = new ArrayList<>();
        List<RegistryEntry<Enchantment>> entries = allEnchantments(world);
        if (entries.isEmpty()) {
            out.add(new RolledReward(new ItemStack(Items.BOOK, 3), LootboxRarity.COMMON)); // registry miss
            return out;
        }
        int books = 1 + (RNG.nextDouble() < BOOKS_SECOND_CHANCE ? 1 : 0);
        for (int i = 0; i < books; i++) {
            RegistryEntry<Enchantment> entry = entries.get(RNG.nextInt(entries.size()));
            int level = 1 + RNG.nextInt(Math.max(1, entry.value().getMaxLevel()));
            ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
            ItemEnchantmentsComponent.Builder builder = new ItemEnchantmentsComponent.Builder(
                book.getOrDefault(DataComponentTypes.STORED_ENCHANTMENTS,
                    ItemEnchantmentsComponent.DEFAULT));
            builder.add(entry, level);
            book.set(DataComponentTypes.STORED_ENCHANTMENTS, builder.build());
            out.add(new RolledReward(book, i == 0 ? LootboxRarity.UNCOMMON : LootboxRarity.RARE));
        }
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

    /** Above this many prototypes, the odds table shows a count instead of naming every
     *  one - a pool this big is exactly the point of this change, but a single chat line
     *  with hundreds of item names in it helps nobody. The percentage is never rounded
     *  away regardless of pool size; only the name dump is summarized. */
    private static final int NAME_LIST_THRESHOLD = 24;

    /** Renders a section's prototype list for the odds table: names when short enough to
     *  read, a plain count when the pool is a full registry sweep. */
    private static String describePrototypes(List<ItemStack> prototypes) {
        if (prototypes.size() > NAME_LIST_THRESHOLD) {
            return "§8" + prototypes.size()
                + " possible items - every matching item currently in your game, vanilla and modded.";
        }
        StringBuilder items = new StringBuilder("§f");
        for (int i = 0; i < prototypes.size(); i++) {
            if (i > 0) items.append("§7, §f");
            ItemStack proto = prototypes.get(i);
            items.append(proto.getName().getString());
            if (proto.getCount() > 1) items.append(" x").append(proto.getCount());
        }
        return items.toString();
    }

    public static List<String> oddsLines(Type type, ServerWorld world) {
        List<String> lines = new ArrayList<>();
        lines.add(type.color + "§l" + type.display + " §7- standard cost §a" + type.emeraldCost
            + " emeralds§7 (a Key always works; a chest may be priced differently). Full odds:");
        if (type == Type.BOOKS) {
            List<RegistryEntry<Enchantment>> entries = allEnchantments(world);
            int size = Math.max(1, entries.size());
            // Same call the odds menu's book icons use - the displayed percentage and the
            // roll's own per-enchantment odds can never drift apart.
            String withinPct = LootboxOdds.format(LootboxOdds.chanceOf(1, size));
            lines.add("§7Always: §f1 enchanted book§7; §f"
                + (int) (BOOKS_SECOND_CHANCE * 100) + "%§7 chance of a second.");
            lines.add("§7Each book: uniform over §f" + entries.size()
                + "§7 enchants (§f" + withinPct + "§7 each), level 1..max:");
            if (entries.size() > NAME_LIST_THRESHOLD) {
                lines.add("§8Every enchantment currently registered - vanilla, Craftics, and any"
                    + " other installed mod's - except the two curses.");
            } else {
                List<String> ids = new ArrayList<>();
                for (RegistryEntry<Enchantment> e : entries) {
                    e.getKey().ifPresent(k -> ids.add(k.getValue().toString()));
                }
                lines.add("§8" + String.join(", ", ids));
            }
            return lines;
        }
        for (Section section : sectionsFor(type, world)) {
            String header = section.chance() >= 1.0
                ? "§7Always (" + section.picks() + " pick" + (section.picks() == 1 ? "" : "s") + "):"
                : "§7" + (int) Math.round(section.chance() * 100) + "% chance ("
                    + section.picks() + " pick):";
            // Two decimals, ported from the plugin's Odds.format - the exact chance the roll
            // uses, since every prototype in a section is an equal-weight entry.
            double withinSection = LootboxOdds.chanceOf(1, section.prototypes().size());
            String amount = section.hasCountRange()
                ? " §8- §f" + section.minCount() + "-" + section.maxCount() + "§8 of it"
                : "";
            lines.add(header + " " + section.rarity().legacyColor + section.rarity().label
                + amount
                + " §8- each equally likely, §f" + LootboxOdds.format(withinSection)
                + "§8 within the section");
            lines.add(describePrototypes(section.prototypes()));
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
                + "§7, uniform §8(never above what that enchant allows)§7.");
            lines.add("§8Enchantments are drawn only from those valid for that weapon type.");
        }
        if (type == Type.ARMOR) {
            lines.add("§e§lEnchant and trim rolls §7(independent of the item roll, and of each other):");
            lines.add("§7Each piece: §f" + pct(ARMOR_ENCHANT_CHANCE)
                + "§7 chance to arrive enchanted.");
            lines.add("§7If enchanted: §f" + pct(SECOND_ENCHANT_CHANCE)
                + "§7 chance of a second enchantment. Levels §f1-" + MAX_ENCHANT_LEVEL
                + "§7, uniform §8(never above what that enchant allows)§7.");
            lines.add("§7Each piece: §f" + pct(ARMOR_TRIM_CHANCE)
                + "§7 chance to arrive trimmed (random pattern and material, both uniform).");
            lines.add("§8A piece can roll both, one, or neither - the two rolls don't affect each other.");
        }
        return lines;
    }
}
