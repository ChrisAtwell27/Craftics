package com.crackedgames.craftics.compat.paladins;

import com.crackedgames.craftics.api.ItemUseResult;
import com.crackedgames.craftics.api.TargetType;
import com.crackedgames.craftics.api.UsableItemContext;
import com.crackedgames.craftics.api.UsableItemHandler;
import com.crackedgames.craftics.api.registry.UsableItemEntry;
import com.crackedgames.craftics.api.registry.UsableItemRegistry;
import com.crackedgames.craftics.combat.CombatEffects;
import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.ProjectileSpawner;
import com.crackedgames.craftics.core.GridPos;
import net.minecraft.item.Item;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/** Registers Paladins staff/wand spell casters as Craftics usable items. */
final class PaladinsSpells {

    private PaladinsSpells() {}

    static void register() {
        // Wands = priest/support spells (one per tier).
        registerHeal("acolyte_wand", 0);                 // Heal
        registerFlashHeal("holy_wand", 0);               // Flash Heal (bigger single-target)
        registerCircleOfHealing("diamond_holy_wand", 1); // Circle of Healing (AoE heal + absorption)
        registerBarrier("netherite_holy_wand", 2);       // Barrier (party absorption shield)
        // Staves = paladin/offense spells (one per tier).
        registerHolyShock("holy_staff", 0);              // Holy Shock
        registerJudgement("diamond_holy_staff", 1);      // Judgement (AoE dmg, 2x undead, stun)
        registerBattleBanner("netherite_holy_staff", 2); // Battle Banner (ally Strength aura)
        registerHolyBeam("ruby_holy_staff", 3);          // Holy Beam
        registerDivineProtection("aether_holy_staff", 3);// Divine Protection (self Resistance)
    }

    private static void registerHeal(String path, int bump) {
        Item item = PaladinsCompat.lookupItem(path);
        if (item == null) return;
        int heal = 8 + bump;
        UsableItemHandler handler = ctx -> {
            CombatEntity target = ctx.targetEntity();
            if (target != null && target.isAlly()) {
                ctx.healEntity(target, heal);
                ctx.message("§dHoly Wand heals " + target.getDisplayName() + " for " + heal + "!");
            } else {
                ctx.healPlayer(heal);
                ctx.message("§dHoly Wand heals you for " + heal + "!");
            }
            healParticles(ctx);
            return ItemUseResult.ok();
        };
        UsableItemRegistry.register(item, UsableItemEntry.builder(item)
            .apCost(1).range(4).targetType(TargetType.ANY_TILE).consumedOnUse(false)
            .handler(handler).build());
    }

    private static void registerHolyShock(String path, int bump) {
        Item item = PaladinsCompat.lookupItem(path);
        if (item == null) return;
        int power = 6 + bump;
        UsableItemHandler handler = ctx -> {
            CombatEntity target = ctx.targetEntity();
            if (target == null) return ItemUseResult.fail("§cNo target for Holy Shock.");
            if (target.isAlly()) {
                ctx.healEntity(target, power);
                ctx.message("§eHoly Shock mends " + target.getDisplayName() + " for " + power + "!");
                healParticles(ctx);
            } else {
                int dealt = ctx.damage(target, power);
                ctx.knockback(target, 1);
                ctx.message("§eHoly Shock smites " + target.getDisplayName() + " for " + dealt + "!");
                burstParticles(ctx);
            }
            return ItemUseResult.ok();
        };
        UsableItemRegistry.register(item, UsableItemEntry.builder(item)
            .apCost(2).range(4).targetType(TargetType.ANY_TILE).consumedOnUse(false)
            .handler(handler).build());
    }

    private static void registerHolyBeam(String path, int bump) {
        Item item = PaladinsCompat.lookupItem(path);
        if (item == null) return;
        int dmg = 7 + bump;
        int heal = 5 + bump;
        UsableItemHandler handler = ctx -> {
            CombatEntity primary = ctx.targetEntity();
            if (primary == null) return ItemUseResult.fail("§cNo target for Holy Beam.");
            GridPos playerPos = ctx.arena().getPlayerGridPos();
            GridPos tPos = primary.getGridPos();
            int dx = Integer.signum(tPos.x() - playerPos.x());
            int dz = Integer.signum(tPos.z() - playerPos.z());
            GridPos beyond = new GridPos(tPos.x() + dx, tPos.z() + dz);
            beamHit(ctx, primary, dmg, heal);
            for (CombatEntity c : ctx.combatants()) {
                if (c != primary && c.isAlive() && c.getGridPos().equals(beyond)) {
                    beamHit(ctx, c, dmg, heal);
                }
            }
            beamParticles(ctx);
            return ItemUseResult.ok();
        };
        UsableItemRegistry.register(item, UsableItemEntry.builder(item)
            .apCost(2).range(4).targetType(TargetType.ANY_TILE).consumedOnUse(false)
            .handler(handler).build());
    }

    // ── Flash Heal (wand): a stronger single-target heal. ──
    private static void registerFlashHeal(String path, int bump) {
        Item item = PaladinsCompat.lookupItem(path);
        if (item == null) return;
        int heal = 14 + bump * 2;
        UsableItemHandler handler = ctx -> {
            CombatEntity target = ctx.targetEntity();
            if (target != null && target.isAlly()) {
                ctx.healEntity(target, heal);
                ctx.message("§dFlash Heal restores " + heal + " HP to " + target.getDisplayName() + "!");
            } else {
                ctx.healPlayer(heal);
                ctx.message("§dFlash Heal restores " + heal + " HP to you!");
            }
            healParticles(ctx);
            return ItemUseResult.ok();
        };
        UsableItemRegistry.register(item, UsableItemEntry.builder(item)
            .apCost(2).range(4).targetType(TargetType.ANY_TILE).consumedOnUse(false)
            .handler(handler).build());
    }

    // ── Circle of Healing (wand): heal every ally within 2 tiles of the caster + Absorption. ──
    private static void registerCircleOfHealing(String path, int bump) {
        Item item = PaladinsCompat.lookupItem(path);
        if (item == null) return;
        int heal = 6 + bump;
        UsableItemHandler handler = ctx -> {
            GridPos center = ctx.arena().getPlayerGridPos();
            ctx.healPlayer(heal);
            ctx.applyPlayerEffect(CombatEffects.EffectType.ABSORPTION, 3, 0);
            int healed = 1;
            for (CombatEntity c : ctx.combatants()) {
                if (c.isAlive() && c.isAlly() && center.manhattanDistance(c.getGridPos()) <= 2) {
                    ctx.healEntity(c, heal);
                    ctx.applyEffect(c, CombatEffects.EffectType.ABSORPTION, 3, 0);
                    healed++;
                }
            }
            ctx.message("§dCircle of Healing mends " + healed + " allies for " + heal + " HP (+Absorption)!");
            circleParticles(ctx);
            return ItemUseResult.ok();
        };
        UsableItemRegistry.register(item, UsableItemEntry.builder(item)
            .apCost(2).range(0).targetType(TargetType.SELF).consumedOnUse(false)
            .handler(handler).build());
    }

    // ── Barrier (wand): a protective absorption shield on the caster and nearby allies. ──
    private static void registerBarrier(String path, int bump) {
        Item item = PaladinsCompat.lookupItem(path);
        if (item == null) return;
        int amp = bump; // higher tiers = stronger shield
        UsableItemHandler handler = ctx -> {
            GridPos center = ctx.arena().getPlayerGridPos();
            ctx.applyPlayerEffect(CombatEffects.EffectType.ABSORPTION, 5, amp);
            int shielded = 1;
            for (CombatEntity c : ctx.combatants()) {
                if (c.isAlive() && c.isAlly() && center.manhattanDistance(c.getGridPos()) <= 2) {
                    ctx.applyEffect(c, CombatEffects.EffectType.ABSORPTION, 5, amp);
                    shielded++;
                }
            }
            ctx.message("§bBarrier shields " + shielded + " allies with Absorption!");
            barrierParticles(ctx);
            return ItemUseResult.ok();
        };
        UsableItemRegistry.register(item, UsableItemEntry.builder(item)
            .apCost(2).range(0).targetType(TargetType.SELF).consumedOnUse(false)
            .handler(handler).build());
    }

    // ── Judgement (staff): AoE damage around the target, 2x vs undead, stuns survivors. ──
    private static void registerJudgement(String path, int bump) {
        Item item = PaladinsCompat.lookupItem(path);
        if (item == null) return;
        int power = 8 + bump;
        UsableItemHandler handler = ctx -> {
            CombatEntity primary = ctx.targetEntity();
            if (primary == null) return ItemUseResult.fail("§cNo target for Judgement.");
            GridPos hit = primary.getGridPos();
            int struck = 0;
            for (CombatEntity c : ctx.combatants()) {
                if (!c.isAlive() || c.isAlly()) continue;
                if (hit.manhattanDistance(c.getGridPos()) > 2) continue;
                int dmg = isUndead(c) ? power * 2 : power;
                ctx.damage(c, dmg);
                ctx.stun(c);
                struck++;
            }
            if (struck == 0) return ItemUseResult.fail("§cNo enemies in the judgement zone.");
            ctx.message("§6Judgement smites " + struck + " foe(s) and stuns them!");
            judgementParticles(ctx, hit);
            return ItemUseResult.ok();
        };
        UsableItemRegistry.register(item, UsableItemEntry.builder(item)
            .apCost(3).range(4).targetType(TargetType.ANY_TILE).consumedOnUse(false)
            .handler(handler).build());
    }

    // ── Battle Banner (staff): buff the caster and nearby allies with Strength. ──
    private static void registerBattleBanner(String path, int bump) {
        Item item = PaladinsCompat.lookupItem(path);
        if (item == null) return;
        int turns = 4 + bump;
        UsableItemHandler handler = ctx -> {
            GridPos center = ctx.arena().getPlayerGridPos();
            ctx.applyPlayerEffect(CombatEffects.EffectType.STRENGTH, turns, 0);
            int rallied = 1;
            for (CombatEntity c : ctx.combatants()) {
                if (c.isAlive() && c.isAlly() && center.manhattanDistance(c.getGridPos()) <= 3) {
                    ctx.applyEffect(c, CombatEffects.EffectType.STRENGTH, turns, 0);
                    rallied++;
                }
            }
            ctx.message("§cBattle Banner rallies " + rallied + " allies with Strength for " + turns + " turns!");
            bannerParticles(ctx);
            return ItemUseResult.ok();
        };
        UsableItemRegistry.register(item, UsableItemEntry.builder(item)
            .apCost(2).range(0).targetType(TargetType.SELF).consumedOnUse(false)
            .handler(handler).build());
    }

    // ── Divine Protection (staff): a Resistance ward on the caster. ──
    private static void registerDivineProtection(String path, int bump) {
        Item item = PaladinsCompat.lookupItem(path);
        if (item == null) return;
        int turns = 4;
        int amp = bump > 0 ? 1 : 0;
        UsableItemHandler handler = ctx -> {
            ctx.applyPlayerEffect(CombatEffects.EffectType.RESISTANCE, turns, amp);
            ctx.message("§eDivine Protection wards you (Resistance " + turns + " turns)!");
            protectionParticles(ctx);
            return ItemUseResult.ok();
        };
        UsableItemRegistry.register(item, UsableItemEntry.builder(item)
            .apCost(2).range(0).targetType(TargetType.SELF).consumedOnUse(false)
            .handler(handler).build());
    }

    /** Coarse undead check by entity type id, for the Judgement 2x bonus. */
    private static boolean isUndead(CombatEntity c) {
        String id = c.getEntityTypeId();
        if (id == null) return false;
        return id.contains("zombie") || id.contains("skeleton") || id.contains("wither")
            || id.contains("drowned") || id.contains("husk") || id.contains("stray")
            || id.contains("phantom") || id.contains("zoglin") || id.contains("bogged")
            || id.contains("zombified") || id.contains("revenant") || id.contains("wraith");
    }

    private static void beamHit(UsableItemContext ctx, CombatEntity c, int dmg, int heal) {
        if (c.isAlly()) {
            ctx.healEntity(c, heal);
            ctx.message("§bHoly Beam mends " + c.getDisplayName() + " for " + heal + "!");
        } else {
            int dealt = ctx.damage(c, dmg);
            ctx.message("§bHoly Beam sears " + c.getDisplayName() + " for " + dealt + "!");
        }
    }

    // --- VFX (authentic spell_engine particles + sounds, vanilla fallback) ---
    // Every cast layers three beats so it reads as a real spell: a charge flourish at
    // the caster, a trail from caster to target, and a rich multi-particle impact.

    private static ServerWorld world(UsableItemContext ctx) {
        return (ServerWorld) ctx.player().getEntityWorld();
    }

    private static BlockPos casterBlock(UsableItemContext ctx) {
        return ctx.arena().gridToBlockPos(ctx.arena().getPlayerGridPos());
    }

    private static void spawn(UsableItemContext ctx, ParticleEffect p, BlockPos b, double yOff,
                              int count, double spread, double vSpread, double speed) {
        world(ctx).spawnParticles(p, b.getX() + 0.5, b.getY() + yOff, b.getZ() + 0.5,
            count, spread, vSpread, spread, speed);
    }

    /** Resolve a mod sound by id at runtime, or null if absent (then a vanilla fallback plays). */
    private static SoundEvent sound(String namespace, String path) {
        Identifier id = Identifier.of(namespace, path);
        if (!Registries.SOUND_EVENT.containsId(id)) return null;
        return Registries.SOUND_EVENT.get(id);
    }

    private static void playSound(UsableItemContext ctx, BlockPos at, String namespace, String path,
                                  net.minecraft.sound.SoundEvent vanillaFallback, float pitch) {
        SoundEvent se = sound(namespace, path);
        if (se == null) se = vanillaFallback;
        if (se == null) return;
        world(ctx).playSound(null, at, se, SoundCategory.PLAYERS, 0.9f, pitch);
    }

    /** Charge shimmer at the caster: sparks rising, holy motes drifting. Sold on every cast. */
    private static void castFlourish(UsableItemContext ctx) {
        BlockPos c = casterBlock(ctx);
        spawn(ctx, PaladinsParticles.spark(), c, 1.4, 14, 0.25, 0.4, 0.12);
        spawn(ctx, PaladinsParticles.holyFloat(), c, 1.0, 8, 0.3, 0.3, 0.02);
        playSound(ctx, c, "spell_engine", "generic_healing_casting",
            net.minecraft.sound.SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 1.3f);
    }

    private static void healParticles(UsableItemContext ctx) {
        castFlourish(ctx);
        CombatEntity t = ctx.targetEntity();
        GridPos p = t != null ? t.getGridPos() : ctx.arena().getPlayerGridPos();
        BlockPos b = ctx.arena().gridToBlockPos(p);
        // Green ascending pillar + a bright burst + drifting motes = a clear "you were healed" beat.
        spawn(ctx, PaladinsParticles.heal(), b, 0.4, 26, 0.3, 0.9, 0.06);
        spawn(ctx, PaladinsParticles.healBurst(), b, 1.0, 12, 0.3, 0.4, 0.15);
        spawn(ctx, PaladinsParticles.healFloat(), b, 1.2, 10, 0.4, 0.5, 0.02);
        playSound(ctx, b, "spell_engine", "generic_healing_release",
            net.minecraft.sound.SoundEvents.BLOCK_AMETHYST_BLOCK_HIT, 1.4f);
    }

    private static void burstParticles(UsableItemContext ctx) {
        castFlourish(ctx);
        CombatEntity t = ctx.targetEntity();
        if (t == null) return;
        BlockPos from = casterBlock(ctx);
        BlockPos to = ctx.arena().gridToBlockPos(t.getGridPos());
        // A holy bolt arcs to the target, then detonates in a radiant sphere with a settling aura.
        ProjectileSpawner.spawnSpellTrail(world(ctx), from, to,
            PaladinsParticles.spark(), PaladinsParticles.holyFloat(), 14, 1.0);
        spawn(ctx, PaladinsParticles.holyBurst(), to, 1.0, 36, 0.4, 0.6, 0.25);
        spawn(ctx, PaladinsParticles.holyDecelerate(), to, 1.0, 18, 0.4, 0.5, 0.1);
        spawn(ctx, PaladinsParticles.sparkBurst(), to, 1.0, 14, 0.3, 0.4, 0.2);
        playSound(ctx, to, "paladins", "holy_shock_damage",
            net.minecraft.sound.SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT, 1.2f);
    }

    private static void beamParticles(UsableItemContext ctx) {
        castFlourish(ctx);
        CombatEntity t = ctx.targetEntity();
        if (t == null) return;
        BlockPos from = casterBlock(ctx);
        BlockPos to = ctx.arena().gridToBlockPos(t.getGridPos());
        // A near-flat radiant beam streaks to the target with a firework accent, then bursts.
        ProjectileSpawner.spawnSpellTrail(world(ctx), from, to,
            PaladinsParticles.spellFloat(), PaladinsParticles.spark(), 22, 0.15);
        spawn(ctx, net.minecraft.particle.ParticleTypes.FIREWORK, to, 1.0, 6, 0.2, 0.3, 0.05);
        spawn(ctx, PaladinsParticles.spellBurst(), to, 1.0, 4, 0.1, 0.1, 0.0);
        spawn(ctx, PaladinsParticles.holyBurst(), to, 1.0, 30, 0.4, 0.5, 0.2);
        spawn(ctx, PaladinsParticles.holyAscend(), to, 0.4, 16, 0.3, 0.8, 0.06);
        playSound(ctx, to, "paladins", "holy_beam_damage",
            net.minecraft.sound.SoundEvents.ENTITY_ILLUSIONER_CAST_SPELL, 1.1f);
    }

    /** Circle of Healing: a ring of green ascend around the caster + settling holy motes. */
    private static void circleParticles(UsableItemContext ctx) {
        castFlourish(ctx);
        BlockPos c = casterBlock(ctx);
        spawn(ctx, PaladinsParticles.heal(), c, 0.3, 40, 1.6, 0.6, 0.04);
        spawn(ctx, PaladinsParticles.healFloat(), c, 0.8, 24, 1.4, 0.5, 0.02);
        spawn(ctx, PaladinsParticles.holyDecelerate(), c, 1.0, 16, 1.2, 0.4, 0.03);
        playSound(ctx, c, "spell_engine", "generic_healing_release",
            net.minecraft.sound.SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 1.1f);
    }

    /** Barrier: a settling ward ring of spark/spell decelerate around the caster. */
    private static void barrierParticles(UsableItemContext ctx) {
        castFlourish(ctx);
        BlockPos c = casterBlock(ctx);
        spawn(ctx, PaladinsParticles.sparkDecelerate(), c, 1.0, 50, 1.8, 0.8, 0.03);
        spawn(ctx, PaladinsParticles.spellDecelerate(), c, 1.0, 30, 1.6, 0.6, 0.02);
        playSound(ctx, c, "spell_engine", "generic_healing_release",
            net.minecraft.sound.SoundEvents.BLOCK_BEACON_ACTIVATE, 1.3f);
    }

    /** Judgement: a meteoric burst over the target zone - holy burst, decelerate, float, smoke. */
    private static void judgementParticles(UsableItemContext ctx, GridPos zone) {
        castFlourish(ctx);
        BlockPos b = ctx.arena().gridToBlockPos(zone);
        spawn(ctx, PaladinsParticles.holyBurst(), b, 1.2, 60, 1.4, 0.8, 0.35);
        spawn(ctx, PaladinsParticles.holyDecelerate(), b, 1.0, 40, 1.4, 0.7, 0.1);
        spawn(ctx, PaladinsParticles.holyFloat(), b, 0.6, 30, 1.6, 0.6, 0.03);
        spawn(ctx, net.minecraft.particle.ParticleTypes.SMOKE, b, 0.4, 24, 1.4, 0.4, 0.02);
        playSound(ctx, b, "paladins", "judgement_impact",
            net.minecraft.sound.SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT, 0.9f);
    }

    /** Battle Banner: a rising column of spark/spell motes at the caster (a rallying standard). */
    private static void bannerParticles(UsableItemContext ctx) {
        castFlourish(ctx);
        BlockPos c = casterBlock(ctx);
        spawn(ctx, PaladinsParticles.spellFloat(), c, 1.4, 30, 0.3, 1.0, 0.05);
        spawn(ctx, PaladinsParticles.sparkDecelerate(), c, 1.0, 20, 0.8, 0.6, 0.02);
        playSound(ctx, c, "paladins", "battle_banner_release",
            net.minecraft.sound.SoundEvents.BLOCK_BELL_USE, 1.2f);
    }

    /** Divine Protection: a settling golden ward around the caster. */
    private static void protectionParticles(UsableItemContext ctx) {
        castFlourish(ctx);
        BlockPos c = casterBlock(ctx);
        spawn(ctx, PaladinsParticles.holyDecelerate(), c, 1.0, 30, 0.9, 0.8, 0.04);
        spawn(ctx, PaladinsParticles.holyFloat(), c, 1.2, 16, 0.8, 0.5, 0.02);
        playSound(ctx, c, "paladins", "divine_protection_release",
            net.minecraft.sound.SoundEvents.BLOCK_BEACON_POWER_SELECT, 1.2f);
    }
}
