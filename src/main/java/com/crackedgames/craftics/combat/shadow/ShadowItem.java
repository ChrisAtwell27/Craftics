package com.crackedgames.craftics.combat.shadow;

/**
 * One usable thing out of the player's inventory that the Shadow knows what to do with: food,
 * a potion, something to throw, or a pottery sherd.
 *
 * <p>Most of them can be used once. A player carries a stack of sixteen golden carrots to get
 * through a whole biome, and a boss that could eat all sixteen would never die. Throwables get
 * a few uses, because one snowball is not much of a snowball fight.
 *
 * <p>Build these with the factory methods and the {@code with...} methods rather than the
 * long constructor: most fields mean nothing for most uses.
 *
 * @param slot      where the stack sits in the copied kit
 * @param itemId    registry id, for logs
 * @param use       what the Shadow does with it
 * @param apCost    AP to use it
 * @param range     how far away the target can be; for a spell centred on the Shadow, how
 *                  close the target has to come
 * @param amount    health restored (HEAL), creatures called (SUMMON), flat damage (a DEBUFF
 *                  with no effect), otherwise the level of {@code effect}
 * @param effect    the status effect it carries, as a {@code CombatEffects.EffectType} name,
 *                  or empty
 * @param turns     how long that effect lasts
 * @param label     what the chat line calls it, lower case with underscores
 * @param radius    for a SPELL, how many tiles out from its centre it lands; 0 is one tile
 * @param onSelf    for a SPELL, true when it goes off around the Shadow rather than the target
 * @param knockback for a SPELL, tiles the target is thrown back; negative drags it closer
 * @param potency   for a SPELL, its damage as a percentage of what a weapon of the same AP
 *                  cost would deal
 * @param charges   how many times it can be used in one fight
 * @param lifesteal for a SPELL, true when it heals the Shadow for part of what it deals
 */
public record ShadowItem(int slot, String itemId, Use use, int apCost, int range, int amount,
                         String effect, int turns, String label, int radius, boolean onSelf,
                         int knockback, int potency, int charges, boolean lifesteal) {

    public enum Use {
        /** Food, healing potions, healing sherds: the Shadow restores its own health. */
        HEAL,
        /** Strength, Swiftness and the like: the Shadow takes the effect itself. */
        BUFF,
        /** A splash potion that hurts: thrown at whoever it is fighting. */
        DEBUFF,
        /** A throwable or an attacking sherd: damage on a tile or an area, often with something extra. */
        SPELL,
        /** An ender pearl or Phase Step: the Shadow appears beside its target. */
        BLINK,
        /** Seeker Vexes: creatures arrive to fight for it. */
        SUMMON
    }

    public static ShadowItem heal(int slot, String itemId, int apCost, int amount) {
        return base(slot, itemId, Use.HEAL, apCost, 0, amount, "");
    }

    public static ShadowItem buff(int slot, String itemId, int apCost, String effect, int level, int turns) {
        return base(slot, itemId, Use.BUFF, apCost, 0, level, "").withRider(effect, level, turns);
    }

    /** A thrown potion that inflicts {@code effect}. */
    public static ShadowItem debuff(int slot, String itemId, int apCost, int range,
                                    String effect, int level, int turns) {
        return base(slot, itemId, Use.DEBUFF, apCost, range, level, "").withRider(effect, level, turns);
    }

    /** A thrown potion that simply deals {@code damage}. */
    public static ShadowItem harm(int slot, String itemId, int apCost, int range, int damage) {
        return base(slot, itemId, Use.DEBUFF, apCost, range, damage, "");
    }

    public static ShadowItem spell(int slot, String itemId, String label, int apCost, int range, int potency) {
        ShadowItem item = base(slot, itemId, Use.SPELL, apCost, range, 0, label);
        return new ShadowItem(item.slot, item.itemId, item.use, item.apCost, item.range, item.amount,
            item.effect, item.turns, item.label, item.radius, item.onSelf, item.knockback,
            potency, item.charges, item.lifesteal);
    }

    public static ShadowItem blink(int slot, String itemId, String label, int apCost, int range) {
        return base(slot, itemId, Use.BLINK, apCost, range, 0, label);
    }

    public static ShadowItem summon(int slot, String itemId, String label, int apCost, int count) {
        return base(slot, itemId, Use.SUMMON, apCost, 0, count, label);
    }

    private static ShadowItem base(int slot, String itemId, Use use, int apCost, int range, int amount,
                                   String label) {
        return new ShadowItem(slot, itemId, use, Math.max(1, apCost), Math.max(0, range), amount,
            "", 0, label, 0, false, 0, 100, 1, false);
    }

    /** The status effect it puts on whoever it hits (or on the Shadow, for a BUFF). */
    public ShadowItem withRider(String effect, int level, int turns) {
        return new ShadowItem(slot, itemId, use, apCost, range, level, effect, turns, label, radius,
            onSelf, knockback, potency, charges, lifesteal);
    }

    public ShadowItem withRadius(int radius) {
        return new ShadowItem(slot, itemId, use, apCost, range, amount, effect, turns, label, radius,
            onSelf, knockback, potency, charges, lifesteal);
    }

    /** It goes off around the Shadow, so its reach is its radius. */
    public ShadowItem centredOnSelf() {
        return new ShadowItem(slot, itemId, use, apCost, radius, amount, effect, turns, label, radius,
            true, knockback, potency, charges, lifesteal);
    }

    /** Positive throws the target back, negative drags it in. */
    public ShadowItem withKnockback(int tiles) {
        return new ShadowItem(slot, itemId, use, apCost, range, amount, effect, turns, label, radius,
            onSelf, tiles, potency, charges, lifesteal);
    }

    public ShadowItem withCharges(int charges) {
        return new ShadowItem(slot, itemId, use, apCost, range, amount, effect, turns, label, radius,
            onSelf, knockback, potency, Math.max(1, charges), lifesteal);
    }

    public ShadowItem withLifesteal() {
        return new ShadowItem(slot, itemId, use, apCost, range, amount, effect, turns, label, radius,
            onSelf, knockback, potency, charges, true);
    }
}
