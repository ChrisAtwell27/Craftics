package com.crackedgames.craftics.combat.shadow;

import com.crackedgames.craftics.api.registry.WeaponEntry;
import com.crackedgames.craftics.api.registry.WeaponRegistry;
import com.crackedgames.craftics.combat.CombatEffects;
import com.crackedgames.craftics.combat.FoodValues;
import com.crackedgames.craftics.combat.ItemUseHandler;
import com.crackedgames.craftics.combat.PlayerCombatStats;
import com.crackedgames.craftics.combat.PlayerProgression;
import com.crackedgames.craftics.combat.PotterySherdSpells;
import com.crackedgames.craftics.combat.TrimEffects;
import com.crackedgames.craftics.combat.sherd.SherdModifiers;
import com.crackedgames.craftics.combat.sherd.SherdSpell;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A snapshot of one player, taken as the fight starts, for their Shadow to fight with.
 *
 * <p>The whole inventory is read, not just what is in hand: a Shadow can pull out any weapon
 * its player is carrying. That is also why putting your sword away before the fight changes
 * nothing. To face a Shadow without it you would have to walk the whole biome without it.
 *
 * <p>Stacks are copied, enchantments and all. The copy is what the weapon's own code is run
 * with when the Shadow attacks (see {@link ShadowStrike}), so whatever the weapon does in the
 * player's hands, enchantments included, it does in the Shadow's.
 */
public final class ShadowKit {

    /** Slot number used for a Shadow's bare hands, which are not a stack. */
    public static final int FIST_SLOT = -1;

    /** How many different ways of healing a Shadow will use in one fight. */
    private static final int MAX_HEALS = 2;
    private static final int MAIN_INVENTORY_SIZE = 36;
    private static final int MIN_SPEED = 1, MAX_SPEED = 8;

    private final UUID ownerUuid;
    private final String ownerName;
    private final int ap;
    private final int speed;
    private final List<ShadowWeapon> weapons;
    private final List<ShadowItem> items;
    private final Map<Integer, ItemStack> stacks;
    private final Map<EquipmentSlot, ItemStack> worn;
    private final PlayerProgression.PlayerStats stats;

    private ShadowKit(UUID ownerUuid, String ownerName, int ap, int speed,
                      List<ShadowWeapon> weapons, List<ShadowItem> items,
                      Map<Integer, ItemStack> stacks, Map<EquipmentSlot, ItemStack> worn,
                      PlayerProgression.PlayerStats stats) {
        this.ownerUuid = ownerUuid;
        this.ownerName = ownerName;
        this.ap = ap;
        this.speed = speed;
        this.weapons = List.copyOf(weapons);
        this.items = List.copyOf(items);
        this.stacks = stacks;
        this.worn = worn;
        this.stats = stats;
    }

    public UUID ownerUuid() { return ownerUuid; }
    public String ownerName() { return ownerName; }
    /** AP the player gets at the start of a turn. */
    public int ap() { return ap; }
    /** Tiles the player can move in a turn. */
    public int speed() { return speed; }
    /** Every weapon carried, bare fists first. Never empty. */
    public List<ShadowWeapon> weapons() { return weapons; }
    /** Every consumable the Shadow knows how to use, one entry per kind of item. */
    public List<ShadowItem> items() { return items; }
    /**
     * The player's own stat sheet, so a weapon's procs roll for the Shadow at the odds they
     * roll at for the player. Null for a Shadow of nobody.
     */
    @Nullable
    public PlayerProgression.PlayerStats stats() { return stats; }

    /** The attack power of the strongest weapon carried. */
    public int bestPower() {
        int best = 1;
        for (ShadowWeapon w : weapons) best = Math.max(best, w.power());
        return best;
    }

    /** The strongest weapon carried: what a Shadow holds when it has not chosen yet. */
    public ShadowWeapon bestWeapon() {
        ShadowWeapon best = weapons.get(0);
        for (ShadowWeapon w : weapons) {
            if (w.power() > best.power()) best = w;
        }
        return best;
    }

    /** A fresh copy of the stack behind a weapon or item, empty for bare fists. */
    public ItemStack stackAt(int slot) {
        ItemStack stack = stacks.get(slot);
        return stack == null ? ItemStack.EMPTY : stack.copy();
    }

    /** A fresh copy of what the player wore in {@code slot}, empty if nothing. */
    public ItemStack worn(EquipmentSlot slot) {
        ItemStack stack = worn.get(slot);
        return stack == null ? ItemStack.EMPTY : stack.copy();
    }

    /**
     * A kit for a Shadow of nobody: default stats and bare fists.
     *
     * <p>Built from plain numbers on purpose. Every Shadow AI starts out holding one of these,
     * and the AI registry creates its instances while the game is still loading, before the
     * config or the item registry can be asked anything.
     */
    public static ShadowKit empty() {
        ShadowWeapon bareFists = new ShadowWeapon(FIST_SLOT, "fist", 1, 1, 1, false);
        return new ShadowKit(null, "", 3, 3, List.of(bareFists), List.of(), Map.of(), Map.of(), null);
    }

    /**
     * A kit put together by hand rather than read off a player. For tests, which have no
     * player to read: the stacks behind the weapons and items are simply absent.
     */
    public static ShadowKit ofParts(int ap, int speed, List<ShadowWeapon> weapons, List<ShadowItem> items) {
        return new ShadowKit(null, "", ap, speed, weapons, items, Map.of(), Map.of(), null);
    }

    public static ShadowKit of(ServerPlayerEntity player) {
        ServerWorld world = (ServerWorld) player.getEntityWorld();
        PlayerProgression.PlayerStats stats = PlayerProgression.get(world).getStats(player);
        TrimEffects.TrimScan trims = TrimEffects.scan(player);
        int ap = Math.max(1, stats.getEffective(PlayerProgression.Stat.AP)
            + PlayerCombatStats.getSetApBonus(player) + trims.get(TrimEffects.Bonus.AP));
        int speed = Math.max(MIN_SPEED, Math.min(MAX_SPEED, stats.getEffective(PlayerProgression.Stat.SPEED)
            + PlayerCombatStats.getSetSpeedBonus(player) + trims.get(TrimEffects.Bonus.SPEED)));

        Map<Integer, ItemStack> stacks = new HashMap<>();
        // One entry per kind of weapon: five iron swords are one way of fighting, not five.
        Map<Item, ShadowWeapon> weaponByItem = new LinkedHashMap<>();
        Map<Item, Integer> enchantsByItem = new HashMap<>();
        Map<String, ShadowItem> itemByKey = new LinkedHashMap<>();

        for (int slot = 0; slot <= MAIN_INVENTORY_SIZE; slot++) {
            // The slot after the main inventory stands for the off hand.
            ItemStack stack = slot < MAIN_INVENTORY_SIZE
                ? player.getInventory().getStack(slot) : player.getOffHandStack();
            if (stack == null || stack.isEmpty()) continue;

            ShadowWeapon weapon = readWeapon(slot, stack);
            if (weapon != null) {
                // Of two of the same weapon, keep the better enchanted one.
                int enchants = enchantLevels(stack);
                Integer known = enchantsByItem.get(stack.getItem());
                if (known == null || enchants > known) {
                    weaponByItem.put(stack.getItem(), weapon);
                    enchantsByItem.put(stack.getItem(), enchants);
                    stacks.put(slot, stack.copyWithCount(1));
                }
                continue;
            }
            ShadowItem item = readItem(slot, stack);
            if (item != null && !itemByKey.containsKey(item.itemId() + "/" + item.effect())) {
                itemByKey.put(item.itemId() + "/" + item.effect(), item);
                stacks.put(slot, stack.copyWithCount(1));
            }
        }

        List<ShadowWeapon> weapons = new ArrayList<>();
        weapons.add(fists());
        weapons.addAll(weaponByItem.values());

        Map<EquipmentSlot, ItemStack> worn = new HashMap<>();
        for (EquipmentSlot slot : new EquipmentSlot[]{
                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
                EquipmentSlot.OFFHAND}) {
            ItemStack piece = player.getEquippedStack(slot);
            if (!piece.isEmpty()) worn.put(slot, piece.copy());
        }

        return new ShadowKit(player.getUuid(), player.getName().getString(), ap, speed,
            weapons, keepBestHeals(new ArrayList<>(itemByKey.values())), stacks, worn, stats);
    }

    /** Every enchantment level on a stack, added up. Only used to pick the better of two alike. */
    private static int enchantLevels(ItemStack stack) {
        ItemEnchantmentsComponent enchants = stack.getOrDefault(
            DataComponentTypes.ENCHANTMENTS, ItemEnchantmentsComponent.DEFAULT);
        int total = 0;
        for (var enchant : enchants.getEnchantments()) total += enchants.getLevel(enchant);
        return total;
    }

    private static ShadowWeapon fists() {
        WeaponEntry fist = WeaponRegistry.get(Items.AIR);
        return new ShadowWeapon(FIST_SLOT, "fist", Math.max(1, fist.attackPower().getAsInt()),
            1, 1, false);
    }

    private static ShadowWeapon readWeapon(int slot, ItemStack stack) {
        WeaponEntry entry = WeaponRegistry.getIfWeapon(stack.getItem());
        if (entry == null) return null;
        return new ShadowWeapon(slot, Registries.ITEM.getId(stack.getItem()).toString(),
            Math.max(1, entry.attackPower().getAsInt()),
            Math.max(1, entry.apCost()),
            Math.max(1, entry.range()),
            entry.isRanged());
    }

    private static ShadowItem readItem(int slot, ItemStack stack) {
        Item item = stack.getItem();
        String id = Registries.ITEM.getId(item).toString();
        int apCost = Math.max(1, ItemUseHandler.getApCost(stack));

        // Pottery sherds. Cost and reach come from this exact stack, so an inscribed sherd
        // keeps its inscription in the Shadow's hands.
        if (PotterySherdSpells.isPotterySherd(item)) {
            SherdSpell spell = SherdModifiers.resolve(stack);
            return spell == null ? null : ShadowSpells.forSherd(id, slot, spell.apCost(), spell.range());
        }

        ShadowItem thrown = ShadowSpells.forThrowable(id, slot, apCost, stack.getCount());
        if (thrown != null) return thrown;

        boolean drunk = item == Items.POTION;
        boolean splash = item == Items.SPLASH_POTION || item == Items.LINGERING_POTION;
        if (drunk || splash) {
            var contents = stack.get(DataComponentTypes.POTION_CONTENTS);
            if (contents == null) return null;
            for (StatusEffectInstance effect : contents.getEffects()) {
                var type = effect.getEffectType().value();
                int level = effect.getAmplifier();
                if (type == StatusEffects.INSTANT_HEALTH.value()) {
                    return ShadowItem.heal(slot, id, apCost, 6 * (level + 1));
                }
                if (type == StatusEffects.INSTANT_DAMAGE.value()) {
                    // Only a potion that can be thrown hurts anyone but the drinker.
                    return splash ? ShadowItem.harm(slot, id, apCost, ShadowSpells.THROW_RANGE, 3 * (level + 1)) : null;
                }
                CombatEffects.EffectType mapped = ItemUseHandler.mapStatusEffect(type);
                if (mapped == null) continue;
                int turns = ItemUseHandler.getTurnsForPotion(mapped, effect.getDuration());
                if (CombatEffects.isBuff(mapped)) {
                    if (!ShadowActions.canBuffSelfWith(mapped)) continue;
                    return ShadowItem.buff(slot, id, apCost, mapped.name(), level, turns);
                }
                if (!splash) continue;
                return ShadowItem.debuff(slot, id, apCost, ShadowSpells.THROW_RANGE, mapped.name(), level, turns);
            }
            return null;
        }

        if (ItemUseHandler.isFood(item)) {
            int heal = FoodValues.healFor(item);
            return heal <= 0 ? null : ShadowItem.heal(slot, id, apCost, heal);
        }
        return null;
    }

    /** Keep every buff and debuff, but only the {@value #MAX_HEALS} most filling ways to heal. */
    private static List<ShadowItem> keepBestHeals(List<ShadowItem> all) {
        List<ShadowItem> heals = new ArrayList<>();
        List<ShadowItem> rest = new ArrayList<>();
        for (ShadowItem item : all) {
            (item.use() == ShadowItem.Use.HEAL ? heals : rest).add(item);
        }
        heals.sort((a, b) -> Integer.compare(b.amount(), a.amount()));
        List<ShadowItem> kept = new ArrayList<>(heals.subList(0, Math.min(MAX_HEALS, heals.size())));
        kept.addAll(rest);
        return kept;
    }
}
