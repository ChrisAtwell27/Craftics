package com.crackedgames.craftics.combat.sherd;

import com.crackedgames.craftics.combat.CombatEffects.EffectType;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Every sherd, declared.
 *
 * <p>This file is the one place a sherd's behaviour lives. Cost, range, what it targets, what it
 * does, how it looks and what its tooltip says are all here, together, per sherd - so re-tuning
 * one is editing one block, and adding one is adding one block. Nothing else in the codebase
 * needs to learn that a new sherd exists.
 *
 * <p>Reading a definition top to bottom answers "what does this do" without opening anything
 * else. Adding a capability - burn on a lightning sherd, knockback on a fire one, an execute
 * threshold, an ally-healing step - is a line, because the vocabulary in {@link Effects} and
 * {@link Selector} does not care which sherd is borrowing it.
 *
 * <p>The numbers here are ported verbatim from the twenty-three hand-written methods that
 * preceded them; {@code SherdParityTest} pins them so a refactor cannot quietly re-balance the
 * game.
 */
public final class SherdRegistry {

    private SherdRegistry() {}

    /** Consumables the Plenty sherd hands out. */
    private static final Item[] PLENTY_CONSUMABLES = {
        Items.GOLDEN_APPLE, Items.COOKED_BEEF, Items.GOLDEN_CARROT, Items.BREAD,
        Items.ENDER_PEARL, Items.SPLASH_POTION, Items.HONEY_BOTTLE, Items.SWEET_BERRIES
    };

    /** The buffs Brewer rolls from. */
    private static final List<EffectType> BREWER_POOL = List.of(
        EffectType.SPEED, EffectType.STRENGTH, EffectType.RESISTANCE,
        EffectType.REGENERATION, EffectType.FIRE_RESISTANCE, EffectType.HASTE
    );

    private static final int SEEKER_BASE_COUNT = 2;
    private static final int SEEKER_DAMAGE = 9;
    private static final int FRIEND_ATK_BUFF = 3;
    private static final int FRIEND_SPEED_BUFF = 1;
    private static final int FRIEND_BUFF_TURNS = 3;
    /** Share of each pet's max HP Guardian Spirit restores. A full heal made pets unkillable. */
    private static final double FRIEND_HEAL_PERCENT = 0.30;
    private static final int PETSPLOSION_RADIUS = 2;
    /**
     * Flat per-blast damage. It used to be half the target's max HP, and since blasts stack
     * where pets overlap, two pets beside a boss deleted it outright.
     */
    private static final int PETSPLOSION_DAMAGE = 12;
    /**
     * Death Mark's execute line. Boss phase two triggers at 50%, so an execute at 50% let a
     * boss go from phase one straight to dead.
     */
    private static final double DEATH_MARK_EXECUTE_BELOW = 0.20;

    private static final Map<Item, SherdSpell> SPELLS = new LinkedHashMap<>();

    private static void register(SherdSpell spell) { SPELLS.put(spell.item(), spell); }

    /** The base spell for an item, or null if the item is not a sherd spell. */
    public static SherdSpell get(Item item) { return SPELLS.get(item); }

    public static boolean isSherd(Item item) { return SPELLS.containsKey(item); }

    /** Every sherd item that functions as a spell. */
    public static Set<Item> items() { return SPELLS.keySet(); }

    public static java.util.Collection<SherdSpell> all() { return SPELLS.values(); }

    /**
     * The shield-block sound moved behind a registry entry in 1.21.5.
     *
     * <p>Isolated to one method so the version fork is stated once rather than inside a
     * definition, where it would make Shelter's block look structurally different from every
     * other sherd's for a reason that has nothing to do with the spell.
     */
    private static SoundEvent shieldBlock() {
        //? if <=1.21.4 {
        return SoundEvents.ITEM_SHIELD_BLOCK;
        //?} else
        /*return SoundEvents.ITEM_SHIELD_BLOCK.value();*/
    }

    static {
        // ── 3 AP ────────────────────────────────────────────────────────

        // 3 AP, not 2: with the Robe discount a 2 AP blink was 4 free tiles for 1 AP.
        register(SherdSpell.of(Items.EXPLORER_POTTERY_SHERD, "Phase Step")
            .color("§d").ap(3).range(4).targets(SherdSpell.TargetMode.WALKABLE_TILE)
            .castVisuals(SpellVisuals.builder()
                .cast(ParticleTypes.END_ROD, SoundEvents.ENTITY_ENDERMAN_TELEPORT)
                .castCount(15).converge(1.2))
            .step(SpellStep.of(Selector.tile())
                .effect(Effects.teleportCaster())
                .visuals(SpellVisuals.builder()
                    .trail(ParticleTypes.PORTAL, ParticleTypes.END_ROD).trailShape(14, 0.5).trailDelay(3)
                    .impact(ParticleTypes.END_ROD, SoundEvents.ENTITY_ENDERMAN_TELEPORT)
                    .impactCount(20).impactRing(0.8).impactDelay(6).impactPitch(0.6f, 1.3f)))
            // Slip through space: a brief guard right after the blink, so Phase Step is an
            // escape tool rather than a scouting one.
            .step(SpellStep.of(Selector.self())
                .effect(Effects.casterEffect(EffectType.RESISTANCE, 1, 1)))
            .tooltip("§d[3 AP] Phase Step §7- Teleport 4 tiles + Resistance II (1 turn)")
            .build());

        register(SherdSpell.of(Items.FRIEND_POTTERY_SHERD, "Guardian Spirit")
            .color("§a").ap(3).selfCast()
            .emptyMessage("§7You have no pets to rally.")
            .castVisuals(SpellVisuals.builder()
                .cast(ParticleTypes.ENCHANT, SoundEvents.ENTITY_CAT_PURR).castCount(8).converge(1.0))
            .step(SpellStep.of(Selector.pets())
                .effect(Effects.healTargetPercent(FRIEND_HEAL_PERCENT))
                .effect(Effects.buffTargetAttack(FRIEND_ATK_BUFF, FRIEND_BUFF_TURNS))
                .effect(Effects.buffTargetSpeed(FRIEND_SPEED_BUFF, FRIEND_BUFF_TURNS))
                .visuals(SpellVisuals.builder()
                    .trail(ParticleTypes.HAPPY_VILLAGER, ParticleTypes.ENCHANT).trailShape(12, 0.4).trailDelay(3)
                    .impact(ParticleTypes.HEART, SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP)
                    .impactCount(6).impactRing(0.6).impactDelay(6).impactPitch(0.5f, 1.5f)))
            .tooltip("§d[3 AP] Guardian Spirit §7- Heal every pet for "
                + Math.round(FRIEND_HEAL_PERCENT * 100) + "% of its max HP\n"
                + "§7+" + FRIEND_ATK_BUFF + " ATK and +" + FRIEND_SPEED_BUFF
                + " Speed to every pet (" + FRIEND_BUFF_TURNS + " turns)")
            .build());

        register(SherdSpell.of(Items.SCRAPE_POTTERY_SHERD, "Corrode")
            .color("§d").ap(3).range(3)
            .castVisuals(SpellVisuals.builder()
                .cast(ParticleTypes.ITEM_SLIME, SoundEvents.BLOCK_GRINDSTONE_USE).castCount(6).converge(0))
            .step(SpellStep.of(Selector.enemy())
                .effect(Effects.damage(5).pctMaxHp(0.08))
                .effect(Effects.defensePenalty(3, 7))
                .visuals(spellTrail(ParticleTypes.ITEM_SLIME, ParticleTypes.FALLING_OBSIDIAN_TEAR,
                    ParticleTypes.ITEM_SLIME, SoundEvents.BLOCK_GRINDSTONE_USE)))
            .tooltip("§d[3 AP] Corrode §7- 5 dmg + reduce DEF by 7 (3 turns)")
            .build());

        register(SherdSpell.of(Items.ANGLER_POTTERY_SHERD, "Riptide Hook")
            .color("§3").ap(3).range(3)
            .castVisuals(SpellVisuals.builder()
                .cast(ParticleTypes.SPLASH, SoundEvents.ENTITY_FISHING_BOBBER_THROW).castCount(6).converge(0))
            // Pull first, THEN damage: the adjacency bonus is meant to reward the reel-in, so
            // it has to be measured after the target has actually moved.
            .step(SpellStep.of(Selector.enemy())
                .effect(Effects.pull(2))
                .effect(Effects.damage(6).pctMaxHp(0.08)
                    .bonusIf(Effects.Condition.ADJACENT_TO_CASTER, 5).label("WATER"))
                .visuals(spellTrail(ParticleTypes.CRIT, null,
                    ParticleTypes.SPLASH, SoundEvents.ENTITY_FISHING_BOBBER_SPLASH)))
            .tooltip("§3[3 AP] Riptide Hook §7- Pull 2 tiles + 6 dmg (+5 if adjacent)")
            .build());

        register(SherdSpell.of(Items.HEARTBREAK_POTTERY_SHERD, "Shatter Will")
            .color("§d").ap(3).range(3)
            .castVisuals(SpellVisuals.builder()
                .cast(ParticleTypes.ENCHANTED_HIT, SoundEvents.BLOCK_GLASS_BREAK)
                .castCount(8).converge(0).castPitch(1.0f, 0.8f))
            .step(SpellStep.of(Selector.enemy())
                .effect(Effects.damage(5).pctMaxHp(0.08))
                .effect(Effects.attackPenalty(5))
                .effect(Effects.speedChange(-4))
                .visuals(spellTrail(ParticleTypes.ENCHANTED_HIT, ParticleTypes.LARGE_SMOKE,
                    ParticleTypes.ENCHANTED_HIT, SoundEvents.BLOCK_ANVIL_DESTROY)))
            .tooltip("§d[3 AP] Shatter Will §7- 5 dmg + -5 ATK, -4 SPD (2 turns)")
            .build());

        register(SherdSpell.of(Items.SHEAF_POTTERY_SHERD, "Entangle")
            .color("§2").ap(3).range(3)
            .castVisuals(SpellVisuals.builder()
                .cast(ParticleTypes.COMPOSTER, SoundEvents.BLOCK_GRASS_BREAK).castCount(6).converge(0))
            .step(SpellStep.of(Selector.enemy())
                .effect(Effects.stun())
                .visuals(spellTrail(ParticleTypes.COMPOSTER, ParticleTypes.HAPPY_VILLAGER,
                    ParticleTypes.COMPOSTER, SoundEvents.BLOCK_VINE_PLACE)))
            .step(SpellStep.of(Selector.enemiesNear(1))
                .heading("§2Roots spread!")
                .effect(Effects.speedChange(-5))
                .visuals(SpellVisuals.builder()
                    .impact(ParticleTypes.COMPOSTER).impactCount(10).impactRing(0).impactDelay(8)))
            .tooltip("§2[3 AP] Entangle §7- Stun target + slow nearby enemies (-5 SPD)")
            .build());

        register(SherdSpell.of(Items.MINER_POTTERY_SHERD, "Earthen Spike")
            .color("§8").ap(3).range(2)
            .castVisuals(SpellVisuals.builder()
                .cast(ParticleTypes.DUST_PLUME, SoundEvents.BLOCK_STONE_BREAK)
                .castCount(8).converge(0).castPitch(1.0f, 0.8f))
            .step(SpellStep.of(Selector.enemy())
                .effect(Effects.damage(10).pctMaxHp(0.10)
                    .bonusIf(Effects.Condition.NEAR_OBSTACLE, 6).label("BLUNT"))
                .visuals(spellTrail(ParticleTypes.DUST_PLUME, null,
                    ParticleTypes.DUST_PLUME, SoundEvents.BLOCK_STONE_BREAK)))
            .tooltip("§8[3 AP] Earthen Spike §7- 10 BLUNT dmg (+6 near obstacle)")
            .build());

        register(SherdSpell.of(Items.DANGER_POTTERY_SHERD, "Hex Trap")
            .color("§d").ap(3).range(3).targets(SherdSpell.TargetMode.EMPTY_TILE)
            .castVisuals(SpellVisuals.builder()
                .cast(ParticleTypes.WITCH, SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE)
                .castCount(6).converge(0.8).castPitch(0.5f, 1.0f))
            .step(SpellStep.of(Selector.tile())
                .effect(Effects.placeTileEffect("hex_trap",
                    "§dTrap set §78 damage + stun on trigger"))
                .visuals(SpellVisuals.builder()
                    .trail(ParticleTypes.WITCH, ParticleTypes.ENCHANT).trailShape(8, 0.5).trailDelay(4)
                    .impact(ParticleTypes.WITCH).impactCount(8).impactRing(0.5).impactDelay(8)))
            .tooltip("§d[3 AP] Hex Trap §7- Invisible trap: 12 dmg + stun on trigger")
            .build());

        // ── 4 AP ────────────────────────────────────────────────────────

        register(SherdSpell.of(Items.BLADE_POTTERY_SHERD, "Phantom Slash")
            .color("§d").ap(4).range(1)
            .castVisuals(SpellVisuals.builder()
                .cast(ParticleTypes.ENCHANTED_HIT, SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP)
                .castCount(8).converge(0.6))
            .step(SpellStep.of(Selector.enemy())
                .effect(Effects.damage(12).pctMaxHp(0.10))
                .visuals(SpellVisuals.builder()
                    .trail(ParticleTypes.SWEEP_ATTACK, ParticleTypes.ENCHANTED_HIT)
                    .trailShape(6, 0.3).trailDelay(3)
                    .impact(ParticleTypes.CRIT).impactCount(15).impactRing(0.7).impactDelay(7)))
            // A random adjacent enemy, not a positionally-first one - see Selector.random().
            .step(SpellStep.of(Selector.enemiesNear(1).random().maxTargets(1))
                .heading("§dCleave!")
                .effect(Effects.damage(8).plain())
                .visuals(SpellVisuals.builder()
                    .impact(ParticleTypes.SWEEP_ATTACK).impactCount(8).impactRing(0).impactDelay(7)))
            .tooltip("§d[4 AP] Phantom Slash §7- 12 dmg + 8 cleave to adjacent enemy")
            .build());

        register(SherdSpell.of(Items.BURN_POTTERY_SHERD, "Immolation")
            .color("§6").ap(4).range(3)
            .castVisuals(SpellVisuals.builder()
                .cast(ParticleTypes.FLAME, SoundEvents.ENTITY_BLAZE_SHOOT).castCount(10).converge(0.7))
            .step(SpellStep.of(Selector.enemy())
                .effect(Effects.damage(9).pctMaxHp(0.08).label("fire"))
                .effect(Effects.burn(3, 1, 200))
                .visuals(SpellVisuals.builder()
                    .trail(ParticleTypes.FLAME, ParticleTypes.LARGE_SMOKE).trailShape(14, 1.8).trailDelay(4)
                    .impact(ParticleTypes.FLAME, SoundEvents.ENTITY_GENERIC_EXPLODE.value())
                    .impactCount(30).impactRing(1.0).impactDelay(8)))
            .step(SpellStep.of(Selector.enemiesNear(1))
                .heading("§6Caught in the blast!")
                .effect(Effects.damage(5).plain().label("fire"))
                .effect(Effects.burn(1, 0, 60))
                .visuals(SpellVisuals.builder()
                    .impact(ParticleTypes.FLAME).impactCount(10).impactRing(0).impactDelay(8)))
            .tooltip("§6[4 AP] Immolation §7- 9 fire + burn 3/t (3t), splash 5 dmg + burn")
            .build());

        register(SherdSpell.of(Items.SNORT_POTTERY_SHERD, "Tectonic Charge")
            .color("§8").ap(4).range(2)
            .castVisuals(SpellVisuals.builder()
                .cast(ParticleTypes.DUST_PLUME, SoundEvents.ENTITY_RAVAGER_ROAR).castCount(10).converge(0))
            .step(SpellStep.of(Selector.enemy())
                .effect(Effects.knockback(3, 4, 9, true))
                .visuals(SpellVisuals.builder()
                    .trail(ParticleTypes.DUST_PLUME).trailShape(6, 0.2).trailDelay(3)
                    .impact(ParticleTypes.DUST_PLUME, SoundEvents.BLOCK_ANVIL_LAND)
                    .impactCount(25).impactRing(0.8).impactDelay(7).impactPitch(1.2f, 0.7f)))
            .tooltip("§8[4 AP] Tectonic Charge §7- KB 3 tiles, 4 dmg/tile, wall slam +9")
            .build());

        register(SherdSpell.of(Items.FLOW_POTTERY_SHERD, "Tidal Surge")
            .color("§3").ap(4).selfCast()
            .emptyMessage("§7No enemies in range.")
            .castVisuals(SpellVisuals.builder()
                .cast(ParticleTypes.BUBBLE, SoundEvents.ENTITY_GENERIC_SPLASH).castCount(12).converge(1.5))
            .step(SpellStep.of(Selector.enemiesAroundCaster(2))
                .effect(Effects.damage(8).pctMaxHp(0.06).label("WATER"))
                .effect(Effects.knockback(2, 0, 0, false))
                .visuals(SpellVisuals.builder()
                    .impact(ParticleTypes.SPLASH, SoundEvents.ENTITY_GENERIC_SPLASH)
                    .impactCount(15).impactRing(1.0).impactDelay(8).impactPitch(0.7f, 0.8f)))
            .tooltip("§3[4 AP] Tidal Surge §7- 8 WATER dmg + KB 2 to all within 2 tiles")
            .build());

        register(SherdSpell.of(Items.MOURNER_POTTERY_SHERD, "Soul Drain")
            .color("§5").ap(4).range(3)
            .castVisuals(SpellVisuals.builder()
                .cast(ParticleTypes.SOUL_FIRE_FLAME, SoundEvents.ENTITY_VEX_AMBIENT)
                .castCount(10).converge(0).castPitch(0.8f, 0.6f))
            .step(SpellStep.of(Selector.enemy())
                .effect(Effects.damage(10).pctMaxHp(0.08))
                .visuals(spellTrail(ParticleTypes.SOUL_FIRE_FLAME, ParticleTypes.SOUL,
                    ParticleTypes.SOUL, SoundEvents.ENTITY_VEX_CHARGE)))
            // Reads report.totalDamage(), so it must follow the damage step.
            .step(SpellStep.of(Selector.self())
                .effect(Effects.lifesteal()))
            .tooltip("§5[4 AP] Soul Drain §7- 10 dmg, heal for damage dealt")
            .build());

        register(SherdSpell.of(Items.HOWL_POTTERY_SHERD, "Petsplosion")
            .color("§7").ap(4).selfCast()
            .emptyMessage("§7You have no pets to detonate.")
            .castVisuals(SpellVisuals.builder()
                .cast(ParticleTypes.CLOUD, howlSound()).castCount(8).converge(1.5).castPitch(2.0f, 0.8f))
            .step(SpellStep.of(Selector.enemiesAroundEachPet(PETSPLOSION_RADIUS))
                .effect(Effects.damage(PETSPLOSION_DAMAGE))
                .visuals(SpellVisuals.builder()
                    .impact(ParticleTypes.EXPLOSION, SoundEvents.ENTITY_GENERIC_EXPLODE.value())
                    .impactCount(3).impactRing(1.0).impactDelay(3).impactPitch(0.9f, 1.3f)))
            .tooltip("§7[4 AP] Petsplosion §7- Every pet erupts in a "
                + PETSPLOSION_RADIUS + "-tile blast\n"
                + "§7" + PETSPLOSION_DAMAGE + " damage per blast\n"
                + "§7Blasts STACK where they overlap. Pets are unharmed")
            .build());

        register(SherdSpell.of(Items.GUSTER_POTTERY_SHERD, "Chain Lightning")
            .color("§e").ap(4).range(3)
            .castVisuals(SpellVisuals.builder()
                .cast(ParticleTypes.ELECTRIC_SPARK, SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER)
                .castCount(15).converge(0).castPitch(0.8f, 1.4f))
            .step(SpellStep.of(Selector.enemy().chainUnlimited(2, 1))
                .effect(Effects.damage(8).pctMaxHp(0.06).lightning().decayPerHop(1).minimum(3))
                .visuals(SpellVisuals.builder()
                    .trail(ParticleTypes.ELECTRIC_SPARK, ParticleTypes.SOUL_FIRE_FLAME)
                    .trailShape(10, 1.5).trailDelay(3)
                    .impact(ParticleTypes.ELECTRIC_SPARK, SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT)
                    .impactCount(20).impactRing(0).impactDelay(7).impactPitch(0.6f, 1.2f)))
            .tooltip("§e[4 AP] Chain Lightning §7- 8 dmg, chains to enemies within 2 tiles (2x on Soaked)")
            .build());

        // ── 5 AP ────────────────────────────────────────────────────────

        register(SherdSpell.of(Items.HEART_POTTERY_SHERD, "Mending Light")
            .color("§a").ap(5).selfCast()
            .castVisuals(SpellVisuals.builder()
                .cast(ParticleTypes.ENCHANT, SoundEvents.ENTITY_PLAYER_LEVELUP).castCount(10).converge(1.5))
            .step(SpellStep.of(Selector.self())
                .effect(Effects.healCaster(15))
                .effect(Effects.casterEffect(EffectType.REGENERATION, 4, 1))
                .visuals(SpellVisuals.builder()
                    .impact(ParticleTypes.HEART, SoundEvents.BLOCK_BEACON_ACTIVATE)
                    .impactCount(12).impactRing(1.0).impactDelay(7).impactPitch(0.4f, 1.5f)
                    .extraImpact(SherdRegistry::risingHelix)))
            .tooltip("§d[5 AP] Mending Light §7- Heal 15 HP + Regen II (4 turns)")
            .build());

        register(SherdSpell.of(Items.SHELTER_POTTERY_SHERD, "Stone Aegis")
            .color("§7").ap(5).selfCast()
            .castVisuals(SpellVisuals.builder()
                .cast(ParticleTypes.DUST_PLUME, shieldBlock()).castCount(8).converge(1.2))
            .step(SpellStep.of(Selector.self())
                .effect(Effects.casterEffect(EffectType.RESISTANCE, 5, 2))
                .effect(Effects.casterEffect(EffectType.ABSORPTION, 4, 2))
                .visuals(SpellVisuals.builder()
                    .impact(ParticleTypes.ENCHANTED_HIT, SoundEvents.BLOCK_ANVIL_USE)
                    .impactCount(10).impactRing(0.6).impactDelay(8).impactPitch(0.4f, 1.5f)))
            .tooltip("§7[5 AP] Stone Aegis §7- Resistance III (5t) + Absorption III (4t)")
            .build());

        register(SherdSpell.of(Items.BREWER_POTTERY_SHERD, "Alchemist's Surge")
            .color("§d").ap(5).selfCast()
            .castVisuals(SpellVisuals.builder()
                .cast(ParticleTypes.WITCH, SoundEvents.BLOCK_BREWING_STAND_BREW).castCount(8).converge(0.8))
            .step(SpellStep.of(Selector.self())
                .effect(Effects.randomCasterEffects(BREWER_POOL, 4, 4, 1))
                .visuals(SpellVisuals.builder()
                    .impact(ParticleTypes.EFFECT, SoundEvents.ENTITY_SPLASH_POTION_BREAK)
                    .impactCount(12).impactRing(0.8).impactDelay(8)))
            .tooltip("§d[5 AP] Alchemist's Surge §7- 4 random buffs II (4 turns each)")
            .build());

        register(SherdSpell.of(Items.PLENTY_POTTERY_SHERD, "Bountiful Harvest")
            .color("§a").ap(5).selfCast()
            .castVisuals(SpellVisuals.builder()
                .cast(ParticleTypes.COMPOSTER, SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP)
                .castCount(8).converge(1.0))
            .step(SpellStep.of(Selector.self())
                .effect(Effects.healCaster(10))
                .effect(Effects.giveItems(PLENTY_CONSUMABLES, 3))
                .visuals(SpellVisuals.builder()
                    .impact(ParticleTypes.HAPPY_VILLAGER, SoundEvents.ENTITY_VILLAGER_YES)
                    .impactCount(12).impactRing(0.8).impactDelay(8).impactPitch(0.5f, 1.2f)))
            .tooltip("§a[5 AP] Bountiful Harvest §7- Heal 10 HP + 3 random consumables")
            .build());

        register(SherdSpell.of(Items.ARCHER_POTTERY_SHERD, "Seeker Vexes")
            .color("§b").ap(5).selfCast()
            .castVisuals(SpellVisuals.builder()
                .cast(ParticleTypes.ENCHANTED_HIT, SoundEvents.ENTITY_SKELETON_SHOOT)
                .castCount(10).converge(1.2))
            .step(SpellStep.of(Selector.self())
                .effect(Effects.summonSeekers(SEEKER_BASE_COUNT, SEEKER_DAMAGE))
                .visuals(SpellVisuals.builder()
                    .impact(ParticleTypes.ENCHANTED_HIT, SoundEvents.ENTITY_ILLUSIONER_CAST_SPELL)
                    .impactCount(0).impactRing(1.5).impactDelay(4).impactPitch(0.8f, 1.5f)))
            .tooltip("§b[5 AP] Seeker Vexes §7- Summon " + SEEKER_BASE_COUNT + " seeking vexes\n"
                + "§7They fly at the nearest enemy on their own each round, then destroy themselves"
                + " on attack for " + SEEKER_DAMAGE + " damage\n"
                + "§7Fragile (1 HP) and vanish after 5 rounds. Luck can summon another")
            .build());

        register(SherdSpell.of(Items.PRIZE_POTTERY_SHERD, "Fortune's Favor")
            .color("§6").ap(5).selfCast()
            .castVisuals(SpellVisuals.builder()
                .cast(ParticleTypes.WAX_ON, SoundEvents.BLOCK_NOTE_BLOCK_BELL.value())
                .castCount(8).converge(1.0))
            .step(SpellStep.of(Selector.self())
                .effect(Effects.casterEffect(EffectType.LUCK, 4, 2))
                .effect(Effects.doubleNextAttack())
                .visuals(SpellVisuals.builder()
                    .impact(ParticleTypes.ENCHANTED_HIT, SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE)
                    .impactCount(15).impactRing(0.6).impactDelay(8).impactPitch(0.5f, 1.2f)))
            .tooltip("§6[5 AP] Fortune's Favor §7- Next attack = DOUBLE damage + Luck III (4t)")
            .build());

        // ── 6 AP ────────────────────────────────────────────────────────

        register(SherdSpell.of(Items.ARMS_UP_POTTERY_SHERD, "War Cry")
            .color("§6").ap(6).selfCast()
            .castVisuals(SpellVisuals.builder()
                .cast(ParticleTypes.FLAME, SoundEvents.ENTITY_ENDER_DRAGON_GROWL)
                .castCount(10).converge(1.0).castPitch(0.5f, 1.2f))
            .step(SpellStep.of(Selector.self())
                .effect(Effects.casterEffect(EffectType.STRENGTH, 4, 2))
                .effect(Effects.casterEffect(EffectType.SPEED, 4, 1))
                .visuals(SpellVisuals.builder()
                    .impact(ParticleTypes.CRIT, SoundEvents.EVENT_RAID_HORN.value())
                    .impactCount(12).impactRing(0.6).impactDelay(8)))
            .tooltip("§6[6 AP] War Cry §7- STR III (+9 ATK) + SPD II (+4 SPD) (4 turns)")
            .build());

        // Two mutually-exclusive steps rather than an if/else. The execute kills below the
        // threshold; the second step's selector then finds no LIVING enemy on that tile and
        // resolves to nothing, so exactly one of the two ever produces an outcome.
        register(SherdSpell.of(Items.SKULL_POTTERY_SHERD, "Death Mark")
            .color("§4").ap(6).range(3)
            .castVisuals(SpellVisuals.builder()
                .cast(ParticleTypes.SOUL_FIRE_FLAME, SoundEvents.ENTITY_WITHER_AMBIENT)
                .castCount(10).converge(1.5).castPitch(0.6f, 0.6f))
            .step(SpellStep.of(Selector.enemy().onlyBelowHp(DEATH_MARK_EXECUTE_BELOW))
                .effect(Effects.execute())
                .visuals(SpellVisuals.builder()
                    .trail(ParticleTypes.SOUL_FIRE_FLAME, ParticleTypes.SOUL).trailShape(14, 2.0).trailDelay(4)
                    .impact(ParticleTypes.SOUL_FIRE_FLAME, SoundEvents.ENTITY_WITHER_SHOOT)
                    .impactCount(25).impactRing(1.0).impactDelay(8).impactPitch(1.5f, 0.5f)))
            .step(SpellStep.of(Selector.enemy())
                .effect(Effects.damage(10).pctMaxHp(0.08))
                .effect(Effects.wither(4, 3))
                .visuals(SpellVisuals.builder()
                    .trail(ParticleTypes.SOUL_FIRE_FLAME, ParticleTypes.SOUL).trailShape(14, 2.0).trailDelay(4)
                    .impact(ParticleTypes.SOUL, SoundEvents.ENTITY_WITHER_SHOOT)
                    .impactCount(15).impactRing(0.6).impactDelay(8).impactPitch(1.0f, 0.8f)))
            .tooltip("§4[6 AP] Death Mark §7- Execute <"
                + Math.round(DEATH_MARK_EXECUTE_BELOW * 100) + "% HP or 10 dmg + Wither IV (4t)")
            .build());
    }

    // ─────────────────────────────────────────────────────────────────────
    // Shared visual shapes
    // ─────────────────────────────────────────────────────────────────────

    /** The common "streak out, burst on arrival" pair most targeted sherds use. */
    private static SpellVisuals spellTrail(net.minecraft.particle.ParticleEffect trail,
                                           net.minecraft.particle.ParticleEffect trailSecondary,
                                           net.minecraft.particle.ParticleEffect impact,
                                           SoundEvent impactSound) {
        return SpellVisuals.builder()
            .trail(trail, trailSecondary).trailShape(12, 0.8).trailDelay(4)
            .impact(impact, impactSound).impactCount(18).impactRing(0.6).impactDelay(8)
            .build();
    }

    /** Mending Light's rising helix, kept exactly as it was drawn. */
    private static void risingHelix(net.minecraft.server.world.ServerWorld world,
                                    net.minecraft.util.math.BlockPos pos) {
        double cx = pos.getX() + 0.5, cy = pos.getY() + 0.5, cz = pos.getZ() + 0.5;
        for (int i = 0; i < 16; i++) {
            double angle = (2 * Math.PI * i / 16) * 2;
            double y = cy + (2.5 * i / 16);
            double x = cx + Math.cos(angle) * 0.6;
            double z = cz + Math.sin(angle) * 0.6;
            world.spawnParticles(ParticleTypes.ENCHANTED_HIT, x, y, z, 1, 0.02, 0.02, 0.02, 0.0);
        }
    }

    /** The wolf howl became a warden roar in 1.21.5. */
    private static SoundEvent howlSound() {
        //? if <=1.21.4 {
        return SoundEvents.ENTITY_WOLF_HOWL;
        //?} else
        /*return SoundEvents.ENTITY_WARDEN_ROAR;*/
    }
}
