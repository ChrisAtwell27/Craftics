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
        SWORD
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
     * <p>{@code appliesToBlunt} is a separate flag, not a {@code Tool}. Blunt weapons are
     * addon-registered (Basic Weapons) and unknowable at data-pack time, so there can be no
     * {@code #craftics:enchantable/blunt} tag and no BLUNT {@code Tool} that the vanilla enchanting
     * table could point at. Blunt coverage is therefore expressed purely at runtime: the effect
     * filter matches a held blunt weapon, and {@code CombatManager.getValidWeaponEnchants} offers
     * the enchant on the blunt pool. See {@link #matchesBlunt(ItemStack)}.
     *
     * @param id             registry path, matching the JSON filename ({@code honed} -> {@code craftics:honed})
     * @param tools          the tools it goes on; the first is the primary tool/affinity
     * @param appliesToBlunt whether it also applies to addon-registered BLUNT weapons (runtime only, no tag)
     * @param maxLevel       level cap, which MUST match {@code max_level} in the JSON
     * @param display        name shown in tooltips and the guide book
     * @param summary        one-line description of the effect, for tooltips and the guide book
     */
    public record Entry(String id, Tool[] tools, boolean appliesToBlunt, int maxLevel,
                        String display, String summary) {
        /** Single-tool entry with no blunt coverage - the common case. */
        public Entry(String id, Tool tool, int maxLevel, String display, String summary) {
            this(id, new Tool[]{tool}, false, maxLevel, display, summary);
        }

        /** Multi-tool entry with no blunt coverage. */
        public Entry(String id, Tool[] tools, int maxLevel, String display, String summary) {
            this(id, tools, false, maxLevel, display, summary);
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
    // sword + axe + blunt (blunt via the runtime flag, no tag - see Entry docs); Dull covers
    // sword + axe. The primary tool stays SWORD, so their affinity/tooltip reads unchanged.
    public static final Entry HILT = new Entry("hilt", new Tool[]{Tool.SWORD, Tool.AXE}, true, 1,
        "Hilt", "Your hits count as Physical and land for a quarter of their damage");
    public static final Entry DULL = new Entry("dull", new Tool[]{Tool.SWORD, Tool.AXE}, 1,
        "Dull", "Your hits count as Blunt and land for half of their damage");

    /** Every Craftics enchantment. Loot pools, tooltips and the guide book all read this. */
    public static final List<Entry> ALL = List.of(
        HONED, FIRE_FANG, THUNDER_FANG, WATER_FANG, PACK_BOND, RABID,
        RESERVING, PERFORMATIVE, RADIANT, MEDIC,
        FACADE, EXECUTIONER, SERRATED, REVERSAL, HILT, DULL, CONDUCTIVE);

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
     * Registry paths of every enchantment that opts into BLUNT coverage (Hilt). There is no BLUNT
     * {@link Tool} or tag, so this is the pool the blunt branches of
     * {@code CombatManager.getValidWeaponEnchants} splice in, the counterpart to {@link #poolFor}
     * for the addon-registered blunt weapons.
     */
    public static String[] poolForBlunt() {
        List<String> out = new ArrayList<>();
        for (Entry e : ALL) {
            if (e.appliesToBlunt()) out.add(e.id());
        }
        return out.toArray(new String[0]);
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
        };
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
        return entry.appliesToBlunt() && matchesBlunt(stack);
    }

    /**
     * Whether {@code stack} counts as a "blunt" weapon for the multi-tool enchantments that opt
     * into blunt coverage (Hilt).
     *
     * <p>Blunt weapons come from two places, neither of which a vanilla enchantable tag can name at
     * data-pack time: the vanilla Mace, and the addon-registered Basic Weapons blunt trio
     * (club/hammer/quarterstaff), matched through {@code BasicWeaponsCompat.isBlunt}. A weapon a
     * mod registers with {@link DamageType#BLUNT} also counts, mirroring how {@link #isAxeLike}
     * trusts the registered DamageType. This is why Hilt-on-blunt is runtime-only and has no tag:
     * the set of blunt items is not knowable until the addon has registered its weapons.
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
     * <p>Unlike axes there is no known non-sword weapon registered Slashing, so this stays a plain
     * vanilla-sword check with no wider DamageType fallback. A Cleaving SwordItem (the SimplySwords
     * greataxe) is caught by {@link #isAxeLike} first in the pool, so it never reaches here.
     *
     * <p>Matched against the six vanilla sword {@link Items} constants rather than
     * {@code instanceof SwordItem}: the SwordItem class does not exist on every shard (it was
     * folded away in a later version), and this set is exactly the vanilla {@code #minecraft:swords}
     * tag the enchantable JSON points at, so the two stay in step.
     */
    public static boolean isSword(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        var item = stack.getItem();
        return item == Items.WOODEN_SWORD || item == Items.STONE_SWORD
            || item == Items.IRON_SWORD || item == Items.GOLDEN_SWORD
            || item == Items.DIAMOND_SWORD || item == Items.NETHERITE_SWORD;
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
}
