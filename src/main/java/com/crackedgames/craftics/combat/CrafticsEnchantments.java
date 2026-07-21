package com.crackedgames.craftics.combat;

import com.crackedgames.craftics.CrafticsMod;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Craftics' own enchantments: the ones whose BEHAVIOR lives in mod code rather than in a
 * vanilla effect component.
 *
 * <p>Each enchantment is defined twice, on purpose:
 * <ul>
 *   <li><b>JSON</b> ({@code data/craftics/enchantment/<id>.json}) declares it to the game -
 *       its name, level cap, cost curve, what it can go on. That is what makes it appear in
 *       enchanting tables, on dropped books, and in the enchanter event, all for free.
 *   <li><b>{@link Entry} here</b> declares what it DOES. Vanilla's effect components can't
 *       express "make my pets apply Soaked", so the behavior is a hook in combat code that
 *       reads the level off this table.
 * </ul>
 *
 * <h2>Adding a new enchantment</h2>
 * <ol>
 *   <li>Write {@code data/craftics/enchantment/<id>.json} (copy an existing one). Point its
 *       {@code supported_items} at {@code #craftics:enchantable/<tool>}, and write that tag
 *       too if the tool is a new one.
 *   <li>List it in {@code data/minecraft/tags/enchantment/non_treasure.json} so the
 *       enchanting table and book loot can roll it.
 *   <li>Add one {@link Entry} to {@link #ALL} below.
 *   <li>Add its translation key to the language file.
 *   <li>Read its level where the effect belongs, via {@link #level(ServerPlayerEntity, Entry)}
 *       for a carried focus, or {@link #heldLevel(ServerPlayerEntity, Entry)} for a weapon.
 * </ol>
 * Loot pools walk {@link #ALL} through {@link #poolFor}, and a tooltip falls back to the
 * {@link Entry}'s own {@link Entry#display} and {@link Entry#summary}, so both pick a new entry
 * up on their own. {@code CrafticsEnchantmentsTest} fails the build if the JSON, the tag, or the
 * translation is missing.
 *
 * <p>The GUIDE BOOK is the exception: its pages are hand-written prose in
 * {@code GuideBookData}, so a new enchantment needs a line adding there by hand. Nothing will
 * fail if you forget - it just goes undocumented in-game.
 *
 * @since 0.2.92
 */
public final class CrafticsEnchantments {

    private CrafticsEnchantments() {}

    /** Which tool an enchantment lives on, and therefore which affinity it serves. */
    public enum Tool {
        /** Shovels - Pet affinity. Effects act through the owner's allies. */
        SHOVEL,
        /** Hoes - Special affinity. Effects act on Special-item casts. */
        HOE,
        /**
         * Axes - Cleaving affinity. Unlike the shovel and hoe, an axe is a WEAPON: its effects
         * act on the owner's own melee swing, so they read the held stack rather than the
         * inventory. See {@link #heldLevel(ServerPlayerEntity, Entry)}.
         */
        AXE,
        /**
         * Swords - Slashing affinity. Like the axe, a sword is a WEAPON: its effects act on the
         * owner's own swing, so they read the held stack via heldLevel, not the carried focus.
         */
        SWORD,
        /**
         * Blunt weapons - Blunt affinity. The vanilla Mace plus addon-registered bludgeons
         * (Basic Weapons club/hammer/quarterstaff, the SimplySwords greathammer, anything a mod
         * registers {@link DamageType#BLUNT}). Matched by {@link #matchesBlunt} - a DamageType
         * check, not an Item class, because the addon item classes are unknowable here. The
         * data-pack side is {@code #craftics:enchantable/blunt}, which names the KNOWN blunt
         * items as optional entries.
         */
        BLUNT,
        /**
         * Armor slots. Unlike every tool above, an armor enchantment reads from the piece
         * actually WORN in its slot - {@link #wornLevel} - never from the inventory or the
         * hands. The item filters are registry-path suffix checks (_helmet, _chestplate,
         * _leggings, _boots) rather than Item classes, because the armor item classes shifted
         * across the supported Minecraft versions and modded armor follows the naming
         * convention anyway.
         */
        HELMET, CHESTPLATE, LEGGINGS, BOOTS
    }

    /** The equipment slot an armor Tool reads from, or null for the hand/focus tools. */
    public static net.minecraft.entity.EquipmentSlot slotFor(Tool tool) {
        return switch (tool) {
            case HELMET -> net.minecraft.entity.EquipmentSlot.HEAD;
            case CHESTPLATE -> net.minecraft.entity.EquipmentSlot.CHEST;
            case LEGGINGS -> net.minecraft.entity.EquipmentSlot.LEGS;
            case BOOTS -> net.minecraft.entity.EquipmentSlot.FEET;
            default -> null;
        };
    }

    /**
     * One Craftics enchantment.
     *
     * <p>Most enchantments sit on a single tool, but a few (Hilt, Dull) are weapon-agnostic in
     * their effect and are meant to go on more than one weapon class. Rather than duplicate an
     * entry per weapon, an entry carries a {@code Tool[]} - the FIRST is the primary tool (the one
     * {@link #tool()} reports and the affinity the entry serves), and the whole set is what
     * {@link #poolFor} and the runtime filters consider. A single-tool entry is written with the
     * plain {@code Tool} constructor, which wraps it into a one-element array, so every existing
     * entry reads exactly as before.
     *
     * <p>{@link Tool#BLUNT} is a Tool like any other since {@code #craftics:enchantable/blunt}
     * exists: its item filter is a DamageType check ({@link #matchesBlunt}) rather than an Item
     * class, because blunt weapons are mostly addon-registered. (An earlier design carried a
     * separate {@code appliesToBlunt} flag from before that tag existed.)
     *
     * @param id             registry path, matching the JSON filename ({@code honed} -> {@code craftics:honed})
     * @param tools          the tools it goes on; the first is the primary tool/affinity
     * @param maxLevel       level cap, which MUST match {@code max_level} in the JSON
     * @param display        name shown in tooltips and the guide book
     * @param summary        one-line description of the effect, for tooltips and the guide book
     */
    public record Entry(String id, Tool[] tools, int maxLevel,
                        String display, String summary) {
        /** Single-tool entry - the common case. */
        public Entry(String id, Tool tool, int maxLevel, String display, String summary) {
            this(id, new Tool[]{tool}, maxLevel, display, summary);
        }

        /** Whether this entry can sit on a BLUNT weapon. */
        public boolean appliesToBlunt() {
            return appliesTo(Tool.BLUNT);
        }

        /** Fully-qualified registry id, e.g. {@code craftics:honed}. */
        public String fullId() {
            return CrafticsMod.MOD_ID + ":" + id;
        }

        /**
         * The primary tool - the affinity this entry serves and the one tooltips key off. For a
         * multi-tool entry it is the first tool listed.
         */
        public Tool tool() {
            return tools[0];
        }

        /** Whether {@code tool} is one of the tools this entry can sit on. */
        public boolean appliesTo(Tool tool) {
            for (Tool t : tools) {
                if (t == tool) return true;
            }
            return false;
        }
    }

    // ── Shovel: Pet affinity ────────────────────────────────────────────────
    public static final Entry HONED = new Entry("honed", Tool.SHOVEL, 5,
        "Honed", "Your pets deal +1 damage per level");
    public static final Entry FIRE_FANG = new Entry("fire_fang", Tool.SHOVEL, 3,
        "Fire Fang", "Your pets set what they hit on fire; higher levels burn longer");
    public static final Entry THUNDER_FANG = new Entry("thunder_fang", Tool.SHOVEL, 3,
        "Thunder Fang", "Your pets shock enemies near what they hit; higher levels reach further");
    public static final Entry WATER_FANG = new Entry("water_fang", Tool.SHOVEL, 3,
        "Water Fang", "Your pets apply Soaked; higher levels keep it on longer");
    public static final Entry PACK_BOND = new Entry("pack_bond", Tool.SHOVEL, 3,
        "Pack Bond", "Each pet deals +1 damage per other living pet, per level");
    public static final Entry RABID = new Entry("rabid", Tool.SHOVEL, 1,
        "Rabid", "Your pets copy their negative effects onto what they hit");

    // ── Hoe: Special affinity ───────────────────────────────────────────────
    public static final Entry RESERVING = new Entry("reserving", Tool.HOE, 3,
        "Reserving", "Better chance a Special item costs no AP");
    public static final Entry PERFORMATIVE = new Entry("performative", Tool.HOE, 3,
        "Performative", "5% chance per level to cast a Special item twice");
    public static final Entry RADIANT = new Entry("radiant", Tool.HOE, 5,
        "Radiant", "Special items hit undead harder");
    public static final Entry MEDIC = new Entry("medic", Tool.HOE, 3,
        "Medic", "Special items heal you and your allies for more");

    // ── Axe: Cleaving affinity ──────────────────────────────────────────────
    public static final Entry FACADE = new Entry("facade", Tool.AXE, 1,
        "Facade", "Your axe hits 1.5x harder while you are suffering a debuff");
    public static final Entry EXECUTIONER = new Entry("executioner", Tool.AXE, 3,
        "Executioner", "Deal +1 damage per negative effect on the target, per level");

    // ── Sword: Slashing affinity ────────────────────────────────────────────
    public static final Entry SERRATED = new Entry("serrated", Tool.SWORD, 3,
        "Serrated", "Lose your sword sweep; each hit applies Bleed stacks equal to the level");
    public static final Entry REVERSAL = new Entry("reversal", Tool.SWORD, 1,
        "Reversal", "At or below a quarter HP, a hit cleanses one debuff off you and deals 1.5x");
    public static final Entry CONDUCTIVE = new Entry("conductive", Tool.SWORD, 1,
        "Conductive", "Copy your negative effects onto enemies you hit");
    // Hilt and Dull are weapon-agnostic in effect, so they go on more than swords. Hilt covers
    // sword + axe + blunt; Dull covers sword + axe (Dull converts hits to Blunt, which is a
    // no-op on an already-blunt weapon). The primary tool stays SWORD, so their
    // affinity/tooltip reads unchanged.
    public static final Entry HILT = new Entry("hilt", new Tool[]{Tool.SWORD, Tool.AXE, Tool.BLUNT}, 1,
        "Hilt", "Your hits count as Physical and land for a quarter of their damage");
    public static final Entry DULL = new Entry("dull", new Tool[]{Tool.SWORD, Tool.AXE}, 1,
        "Dull", "Your hits count as Blunt and land for half of their damage");

    // ── New-wave enchantments: unique mechanics over stat bumps ─────────────
    public static final Entry MATADOR = new Entry("matador", Tool.SWORD, 1,
        "Matador", "Dodging or deflecting an attack Exposes the attacker (-2 DEF for 1 turn)");
    public static final Entry PHANTOM_EDGE = new Entry("phantom_edge", Tool.SWORD, 3,
        "Phantom Edge", "Attacks from a stealth tile don't break Hidden, once per turn per level");
    public static final Entry DEMOLISHER = new Entry("demolisher", Tool.AXE, 1,
        "Demolisher", "Attack an adjacent obstacle to chop it off the battlefield (1 AP, refunds 1 Speed)");
    public static final Entry CRATER = new Entry("crater", Tool.BLUNT, 1,
        "Crater", "Your knockback sends enemies 1 tile further; slamming into anything hurts and Stuns them");
    public static final Entry MOMENTUM = new Entry("momentum", Tool.BLUNT, 1,
        "Momentum", "Your killing blows grant the next party member in the turn order 1 AP");
    public static final Entry VENGEFUL_BOND = new Entry("vengeful_bond", Tool.SHOVEL, 1,
        "Vengeful Bond", "An enemy that kills one of your pets is Marked for 2 turns");
    public static final Entry TERRAFORM = new Entry("terraform", Tool.HOE, 1,
        "Terraform", "Tile-targeting Specials also douse fire, drain water, and fill sunken ground");

    // ── Second wave: weapons ────────────────────────────────────────────────
    public static final Entry UNDERTOW = new Entry("undertow", Tool.SWORD, 1,
        "Undertow", "An enemy whose attack you dodge or deflect is pulled 1 tile toward you");
    public static final Entry HEMORRHAGE = new Entry("hemorrhage", Tool.SWORD, 1,
        "Hemorrhage", "Knocking back a Bleeding enemy detonates its Bleed stacks all at once");
    public static final Entry AMBUSH = new Entry("ambush", Tool.SWORD, 1,
        "Ambush", "Killing an enemy before it acts frightens the next enemy in the order (-2 ATK, 1 turn)");
    public static final Entry TIMBERFALL = new Entry("timberfall", Tool.AXE, 1,
        "Timberfall", "Obstacles you demolish fall onto the enemies beside them - damage and Stun");
    public static final Entry POLE_VAULT = new Entry("pole_vault", Tool.BLUNT, 1,
        "Pole Vault", "Gap jumps cost the plain walk price, and you can vault over enemies");
    public static final Entry MIDAS = new Entry("midas", Tool.BLUNT, 1,
        "Midas", "Slamming an enemy into a wall shakes emeralds into the party bank, once per enemy");
    public static final Entry TAG_TEAM = new Entry("tag_team", Tool.SHOVEL, 1,
        "Tag Team", "Once per turn, swap tiles with one of your pets as a free action");
    public static final Entry TRAPPER = new Entry("trapper", Tool.HOE, 1,
        "Trapper", "Cast a splash potion onto an empty tile as a hidden trap that springs on the first enemy to step there");

    // ── Second wave: armor. Read from the WORN piece via wornLevel, never the inventory. ──
    public static final Entry IRON_WILL = new Entry("iron_will", Tool.HELMET, 1,
        "Iron Will", "Confusion, Blindness and Darkness on you tick out at double speed");
    public static final Entry BEACON = new Entry("beacon", Tool.HELMET, 1,
        "Beacon", "You count as a walking banner: party members within 2 tiles gain the banner defense aura");
    public static final Entry PHALANX = new Entry("phalanx", Tool.CHESTPLATE, 1,
        "Phalanx", "+1 AC for you and each adjacent party member while you stand together");
    public static final Entry GRUDGEPLATE = new Entry("grudgeplate", Tool.CHESTPLATE, 1,
        "Grudgeplate", "The last enemy to damage you takes +2 from the whole party");
    public static final Entry TRAILBLAZER = new Entry("trailblazer", Tool.LEGGINGS, 1,
        "Trailblazer", "Party members moving along tiles you crossed this round pay 1 less Speed");
    public static final Entry LONGSTRIDE = new Entry("longstride", Tool.LEGGINGS, 1,
        "Longstride", "Your jumps clear gaps up to 3 tiles wide");
    public static final Entry LEDGEGRIP = new Entry("ledgegrip", Tool.BOOTS, 1,
        "Ledgegrip", "Once per combat, catch the edge instead of falling when knocked into a pit or deep water");
    public static final Entry SHOCKSTEP = new Entry("shockstep", Tool.BOOTS, 1,
        "Shockstep", "Landing a gap jump stomps adjacent enemies - damage and a 1-turn Slow");

    /** Every Craftics enchantment. Loot pools, tooltips and the guide book all read this. */
    public static final List<Entry> ALL = List.of(
        HONED, FIRE_FANG, THUNDER_FANG, WATER_FANG, PACK_BOND, RABID, VENGEFUL_BOND, TAG_TEAM,
        RESERVING, PERFORMATIVE, RADIANT, MEDIC, TERRAFORM, TRAPPER,
        FACADE, EXECUTIONER, DEMOLISHER, TIMBERFALL,
        SERRATED, REVERSAL, MATADOR, PHANTOM_EDGE, UNDERTOW, HEMORRHAGE, AMBUSH,
        HILT, DULL, CONDUCTIVE, CRATER, MOMENTUM, POLE_VAULT, MIDAS,
        IRON_WILL, BEACON, PHALANX, GRUDGEPLATE, TRAILBLAZER, LONGSTRIDE, LEDGEGRIP, SHOCKSTEP);

    /**
     * Every enchantment that goes on {@code tool}. A multi-tool enchantment (Hilt, Dull) is
     * returned for EVERY tool in its set, so an axe's pool now includes Hilt and Dull just as a
     * sword's does.
     */
    public static List<Entry> forTool(Tool tool) {
        List<Entry> out = new ArrayList<>();
        for (Entry e : ALL) {
            if (e.appliesTo(tool)) out.add(e);
        }
        return out;
    }

    /** Registry paths of every enchantment for {@code tool}, ready to hand to a loot enchant pool. */
    public static String[] poolFor(Tool tool) {
        List<Entry> entries = forTool(tool);
        String[] pool = new String[entries.size()];
        for (int i = 0; i < entries.size(); i++) pool[i] = entries.get(i).id();
        return pool;
    }

    /**
     * Registry paths of every enchantment that goes on BLUNT weapons - the blunt-native entries
     * (Crater, Momentum) plus the multi-tool ones that include {@link Tool#BLUNT} (Hilt). The
     * pool the blunt branches of {@code CombatManager.getValidWeaponEnchants} splice in.
     */
    public static String[] poolForBlunt() {
        return poolFor(Tool.BLUNT);
    }

    /** The Craftics enchantment with this registry path, or {@code null}. Accepts {@code craftics:honed} or {@code honed}. */
    public static Entry byId(String id) {
        if (id == null) return null;
        String path = id.startsWith(CrafticsMod.MOD_ID + ":")
            ? id.substring(CrafticsMod.MOD_ID.length() + 1) : id;
        for (Entry e : ALL) {
            if (e.id().equals(path)) return e;
        }
        return null;
    }

    // ── Level lookup ────────────────────────────────────────────────────────

    /** Matches a single tool against a stack, so we only scan stacks worth scanning. */
    private static Predicate<ItemStack> toolFilter(Tool tool) {
        return switch (tool) {
            case SHOVEL -> stack -> stack.getItem() instanceof net.minecraft.item.ShovelItem;
            case HOE -> stack -> stack.getItem() instanceof net.minecraft.item.HoeItem;
            case AXE -> CrafticsEnchantments::isAxeLike;
            case SWORD -> CrafticsEnchantments::isSword;
            case BLUNT -> CrafticsEnchantments::matchesBlunt;
            case HELMET -> stack -> pathEndsWith(stack, "_helmet");
            case CHESTPLATE -> stack -> pathEndsWith(stack, "_chestplate");
            case LEGGINGS -> stack -> pathEndsWith(stack, "_leggings");
            case BOOTS -> stack -> pathEndsWith(stack, "_boots");
        };
    }

    /** Registry-path suffix check - the version-safe way to classify armor by slot. */
    private static boolean pathEndsWith(ItemStack stack, String suffix) {
        if (stack == null || stack.isEmpty()) return false;
        return net.minecraft.registry.Registries.ITEM.getId(stack.getItem())
            .getPath().endsWith(suffix);
    }

    /**
     * Whether {@code stack} is a coral weapon (live or dead, coral or fan). Corals are WATER
     * weapons by damage type, but they enchant AS SWORDS by design - the whole vanilla sword
     * kit plus the Craftics sword enchantments. The suffix check covers all twenty coral
     * items and nothing else ({@code *_coral_block} doesn't match either suffix).
     */
    public static boolean isCoralWeapon(ItemStack stack) {
        return pathEndsWith(stack, "_coral") || pathEndsWith(stack, "_coral_fan");
    }

    /**
     * The blunt oddballs that enchant AS MACES by design: stick, bamboo, blaze rod, breeze
     * rod. They're registered {@link DamageType#BLUNT} (so the Craftics blunt enchants
     * already fire on them); this identifies them for the mace enchant pool.
     */
    public static boolean isMaceOddball(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        var item = stack.getItem();
        return item == Items.STICK || item == Items.BAMBOO
            || item == Items.BLAZE_ROD || item == Items.BREEZE_ROD;
    }

    /**
     * Whether {@code stack} is one of the weapons {@code entry} can sit on. ORs every tool in the
     * entry's set, plus the blunt weapons when the entry {@link Entry#appliesToBlunt() applies to
     * blunt}. This is the single gate {@link #level} and {@link #heldLevel} use, so a Hilt on an
     * axe (or a blunt club) reads back exactly like a Hilt on a sword.
     */
    private static boolean matchesEntry(Entry entry, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        for (Tool t : entry.tools()) {
            if (toolFilter(t).test(stack)) return true;
        }
        return false;
    }

    /**
     * Whether {@code stack} counts as a "blunt" weapon for the multi-tool enchantments that opt
     * into blunt coverage (Hilt).
     *
     * <p>Blunt weapons: the vanilla Mace, the addon-registered Basic Weapons blunt trio
     * (club/hammer/quarterstaff, matched through {@code BasicWeaponsCompat.isBlunt}), and any
     * weapon a mod registers with {@link DamageType#BLUNT}, mirroring how {@link #isAxeLike}
     * trusts the registered DamageType. The KNOWN blunt items are also named (as optional
     * entries) in {@code #craftics:enchantable/blunt}, which is what lets the anvil and
     * enchanting table apply Hilt to them - but this runtime check stays the authority for the
     * effect itself, because an arbitrary addon's BLUNT registration is not knowable at
     * data-pack time.
     */
    public static boolean matchesBlunt(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        var item = stack.getItem();
        if (item instanceof net.minecraft.item.MaceItem) return true;
        if (com.crackedgames.craftics.compat.basicweapons.BasicWeaponsCompat.isBlunt(item)) return true;
        return com.crackedgames.craftics.api.registry.WeaponRegistry.getDamageType(item)
            == com.crackedgames.craftics.combat.DamageType.BLUNT;
    }

    /**
     * Whether {@code stack} counts as an "axe" for the Cleaving affinity enchantments.
     *
     * <p>"Axe" here is deliberately wider than {@link net.minecraft.item.AxeItem}: any weapon a
     * mod registered with {@link com.crackedgames.craftics.combat.DamageType#CLEAVING} also counts,
     * even when its Item class is not an AxeItem. The SimplySwords greataxe is a {@code SwordItem}
     * registered CLEAVING, and the user wants it to take Facade like a real axe. The registered
     * DamageType is the mod's authority on affinity, so reading it here keeps this filter in step
     * with combat's own idea of what swings Cleaving.
     *
     * <p>This is the single definition shared by the runtime effect filter ({@link #toolFilter}
     * for {@link Tool#AXE}) and the Craftics enchanter pool ({@code CombatManager.getValidWeaponEnchants}),
     * so the two never drift apart.
     */
    public static boolean isAxeLike(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (stack.getItem() instanceof net.minecraft.item.AxeItem) return true;
        return com.crackedgames.craftics.api.registry.WeaponRegistry.getDamageType(stack.getItem())
            == com.crackedgames.craftics.combat.DamageType.CLEAVING;
    }

    /**
     * Whether {@code stack} counts as a "sword" for the Slashing affinity enchantments.
     *
     * <p>Mirrors {@link #isAxeLike}: any weapon a mod registered with
     * {@link DamageType#SLASHING} counts, even when its Item class is unknowable here. The
     * SimplySwords light blades (longsword, katana, rapier, cutlass, sai, twinblade, spear) and
     * the Basic Weapons dagger/spear are all registered Slashing, and without this fallback a
     * Hilt or Dull set on them read back level 0 - the enchant sat on the stack but its effect
     * never fired. The registered DamageType is the mod's authority on affinity, same as the
     * axe filter.
     *
     * <p>The vanilla six are matched against the {@link Items} constants rather than
     * {@code instanceof SwordItem}: the SwordItem class does not exist on every shard (it was
     * folded away in a later version), and this set is exactly the vanilla {@code #minecraft:swords}
     * tag the enchantable JSON points at, so the two stay in step. A Cleaving SwordItem (the
     * SimplySwords greataxe) is caught by {@link #isAxeLike} first in the pool - Cleaving and
     * Slashing registrations are disjoint, so the fallbacks never overlap.
     */
    public static boolean isSword(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        var item = stack.getItem();
        if (item == Items.WOODEN_SWORD || item == Items.STONE_SWORD
            || item == Items.IRON_SWORD || item == Items.GOLDEN_SWORD
            || item == Items.DIAMOND_SWORD || item == Items.NETHERITE_SWORD) {
            return true;
        }
        // Corals enchant as swords by design, WATER damage type notwithstanding.
        if (isCoralWeapon(stack)) return true;
        return com.crackedgames.craftics.api.registry.WeaponRegistry.getDamageType(item)
            == com.crackedgames.craftics.combat.DamageType.SLASHING;
    }

    /**
     * The level of {@code entry} this player has, taken from the best matching tool ANYWHERE in
     * their inventory - it does not have to be held.
     *
     * <p>Deliberately a max, not a sum: carrying five Honed III shovels is still Honed III. That
     * keeps the enchantments a reason to find a better tool rather than a reason to hoard tools.
     *
     * @return the highest level found, or 0 when the player has none
     */
    public static int level(ServerPlayerEntity player, Entry entry) {
        if (player == null || entry == null) return 0;
        String id = entry.fullId();
        int best = 0;
        var inv = player.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack stack = inv.getStack(i);
            if (stack.isEmpty() || !matchesEntry(entry, stack)) continue;
            best = Math.max(best, PlayerCombatStats.getEnchantLevel(stack, id));
            if (best >= entry.maxLevel()) break; // can't do better than the cap
        }
        return best;
    }

    /** Convenience: whether the player has any level of {@code entry}. */
    public static boolean has(ServerPlayerEntity player, Entry entry) {
        return level(player, entry) > 0;
    }

    /**
     * The level of {@code entry} on the stack the player is actually HOLDING, and only when that
     * stack is the entry's own tool.
     *
     * <p>The counterpart to {@link #level}, for enchantments on a tool that gets SWUNG. A shovel
     * or hoe is a carried focus, so an inventory-wide max is right for them: the tool is never in
     * hand at the moment its effect fires. An axe is the weapon dealing the hit, so its
     * enchantment must be read off the weapon dealing the hit - otherwise an axe in the backpack
     * would buff a sword swing, and every melee weapon in the game would inherit the axe's
     * enchantments for free.
     *
     * @return the level on the held stack, or 0 when it is empty, the wrong tool, or unenchanted
     */
    public static int heldLevel(ServerPlayerEntity player, Entry entry) {
        if (player == null || entry == null) return 0;
        ItemStack held = player.getMainHandStack();
        if (held.isEmpty() || !matchesEntry(entry, held)) return 0;
        return PlayerCombatStats.getEnchantLevel(held, entry.fullId());
    }

    /**
     * The level of {@code entry} on the OFFHAND item (same item-type filter as
     * {@link #heldLevel}). For enchants that assist the main hand's action - e.g.
     * Timberfall on an offhand axe crushing enemies when the PICKAXE in the main
     * hand mines an obstacle.
     */
    public static int offhandLevel(ServerPlayerEntity player, Entry entry) {
        if (player == null || entry == null) return 0;
        ItemStack off = player.getOffHandStack();
        if (off.isEmpty() || !matchesEntry(entry, off)) return 0;
        return PlayerCombatStats.getEnchantLevel(off, entry.fullId());
    }

    /**
     * The level of {@code entry} on the armor piece actually WORN in the entry's slot.
     *
     * <p>The armor counterpart to {@link #heldLevel}: an armor enchantment protects the body
     * wearing it, so it reads the equipped stack for its slot and nothing else - a Ledgegrip
     * boot in the backpack catches no ledges. The equipped slot is the authority; no item
     * filter is consulted, because whatever sits in the FEET slot with the enchant on it IS
     * the worn boots.
     *
     * @return the level on the equipped piece, or 0 when the slot is empty, the entry is not
     *         an armor enchantment, or the piece is unenchanted
     */
    public static int wornLevel(ServerPlayerEntity player, Entry entry) {
        if (player == null || entry == null) return 0;
        net.minecraft.entity.EquipmentSlot slot = slotFor(entry.tool());
        if (slot == null) return 0;
        ItemStack worn = player.getEquippedStack(slot);
        if (worn.isEmpty()) return 0;
        return PlayerCombatStats.getEnchantLevel(worn, entry.fullId());
    }
}
