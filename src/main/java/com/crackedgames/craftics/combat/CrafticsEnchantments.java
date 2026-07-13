package com.crackedgames.craftics.combat;

import com.crackedgames.craftics.CrafticsMod;
import net.minecraft.item.ItemStack;
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
 *   <li>Write {@code data/craftics/enchantment/<id>.json} (copy an existing one).
 *   <li>List it in {@code data/minecraft/tags/enchantment/non_treasure.json} so the
 *       enchanting table and book loot can roll it.
 *   <li>Add one {@link Entry} to {@link #ALL} below.
 *   <li>Add its translation key to the language file.
 *   <li>Read its level where the effect belongs, via {@link #level(ServerPlayerEntity, Entry)}.
 * </ol>
 * Nothing else in the codebase enumerates these by hand - loot pools, tooltips and the guide
 * book all walk {@link #ALL}, so a new entry propagates on its own.
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
        HOE
    }

    /**
     * One Craftics enchantment.
     *
     * @param id       registry path, matching the JSON filename ({@code honed} -> {@code craftics:honed})
     * @param tool     the tool it goes on
     * @param maxLevel level cap, which MUST match {@code max_level} in the JSON
     * @param display  name shown in tooltips and the guide book
     * @param summary  one-line description of the effect, for tooltips and the guide book
     */
    public record Entry(String id, Tool tool, int maxLevel, String display, String summary) {
        /** Fully-qualified registry id, e.g. {@code craftics:honed}. */
        public String fullId() {
            return CrafticsMod.MOD_ID + ":" + id;
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

    // ── Hoe: Special affinity ───────────────────────────────────────────────
    public static final Entry RESERVING = new Entry("reserving", Tool.HOE, 3,
        "Reserving", "Better chance a Special item costs no AP");
    public static final Entry PERFORMATIVE = new Entry("performative", Tool.HOE, 3,
        "Performative", "5% chance per level to cast a Special item twice");
    public static final Entry RADIANT = new Entry("radiant", Tool.HOE, 5,
        "Radiant", "Special items hit undead harder");
    public static final Entry MEDIC = new Entry("medic", Tool.HOE, 3,
        "Medic", "Special items heal you and your allies for more");

    /** Every Craftics enchantment. Loot pools, tooltips and the guide book all read this. */
    public static final List<Entry> ALL = List.of(
        HONED, FIRE_FANG, THUNDER_FANG, WATER_FANG,
        RESERVING, PERFORMATIVE, RADIANT, MEDIC);

    /** Every enchantment that goes on {@code tool}. */
    public static List<Entry> forTool(Tool tool) {
        List<Entry> out = new ArrayList<>();
        for (Entry e : ALL) {
            if (e.tool() == tool) out.add(e);
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

    /** Matches the tools an entry's enchantment can sit on, so we only scan stacks worth scanning. */
    private static Predicate<ItemStack> toolFilter(Tool tool) {
        return switch (tool) {
            case SHOVEL -> stack -> stack.getItem() instanceof net.minecraft.item.ShovelItem;
            case HOE -> stack -> stack.getItem() instanceof net.minecraft.item.HoeItem;
        };
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
        Predicate<ItemStack> matches = toolFilter(entry.tool());
        String id = entry.fullId();
        int best = 0;
        var inv = player.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack stack = inv.getStack(i);
            if (stack.isEmpty() || !matches.test(stack)) continue;
            best = Math.max(best, PlayerCombatStats.getEnchantLevel(stack, id));
            if (best >= entry.maxLevel()) break; // can't do better than the cap
        }
        return best;
    }

    /** Convenience: whether the player has any level of {@code entry}. */
    public static boolean has(ServerPlayerEntity player, Entry entry) {
        return level(player, entry) > 0;
    }
}
