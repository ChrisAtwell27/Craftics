package com.crackedgames.craftics.compat.artifacts;

import com.crackedgames.craftics.api.CombatEffectContext;
import com.crackedgames.craftics.api.CombatEffectHandler;
import com.crackedgames.craftics.api.CombatResult;
import com.crackedgames.craftics.combat.CombatEffects;
import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Combat effect handlers for every supported Artifacts mod item.
 * <p>
 * Handlers are stateless wherever possible. The few that need to remember things
 * across the fight (Cross Necklace shield, Chorus Totem charge, Scarf of Invisibility
 * first-attack flag, Umbrella first-hit, Novelty Drinking Hat roll) read/write
 * {@link ArtifactsState} keyed by player UUID, because Craftics rebuilds the
 * scanner result (and therefore handler instances) each turn.
 */
public final class ArtifactEffects {

    private static final Random RNG = new Random();

    private ArtifactEffects() {}

    // ======================================================================
    // Helpers
    // ======================================================================

    private static UUID uid(CombatEffectContext ctx) {
        return ctx.getPlayer().getUuid();
    }

    private static ArtifactsState state(CombatEffectContext ctx) {
        return ArtifactsState.get(uid(ctx));
    }

    /** Push the latest grid state to the player's client (used after pulling/pushing enemies). */
    private static void syncIfPossible(CombatEffectContext ctx) {
        com.crackedgames.craftics.combat.CombatManager cm =
            com.crackedgames.craftics.combat.CombatManager.get(ctx.getPlayer());
        if (cm != null) cm.requestSync();
    }

    /** All living, non-ally enemies adjacent (Manhattan ≤ 1) to the given grid position. */
    private static List<CombatEntity> adjacentEnemies(GridArena arena, GridPos center) {
        List<CombatEntity> out = new ArrayList<>();
        for (var entry : arena.getOccupants().entrySet()) {
            CombatEntity e = entry.getValue();
            if (!e.isAlive() || e.isAlly()) continue;
            if (e.getGridPos().manhattanDistance(center) <= 1) {
                if (!out.contains(e)) out.add(e);
            }
        }
        return out;
    }

    /** Pull an enemy 1 tile toward the given anchor point if the destination is valid. */
    private static boolean pullToward(GridArena arena, CombatEntity enemy, GridPos anchor) {
        GridPos pos = enemy.getGridPos();
        int dx = Integer.signum(anchor.x() - pos.x());
        int dz = Integer.signum(anchor.z() - pos.z());
        if (dx == 0 && dz == 0) return false;
        // Prefer the larger axis first; fall back to the other if blocked.
        GridPos[] tries;
        if (Math.abs(anchor.x() - pos.x()) >= Math.abs(anchor.z() - pos.z())) {
            tries = new GridPos[] { new GridPos(pos.x() + dx, pos.z()), new GridPos(pos.x(), pos.z() + dz) };
        } else {
            tries = new GridPos[] { new GridPos(pos.x(), pos.z() + dz), new GridPos(pos.x() + dx, pos.z()) };
        }
        for (GridPos dest : tries) {
            if (dest.equals(pos)) continue;
            if (!arena.isInBounds(dest)) continue;
            if (arena.isOccupied(dest)) continue;
            arena.moveEntity(enemy, dest);
            return true;
        }
        return false;
    }

    // ======================================================================
    // Head slot
    // ======================================================================

    /** 30% chance for a bonus duplicate from the loot pool on level completion. */
    public static final class SuperstitiousHat implements CombatEffectHandler {
        @Override
        public void onLootRoll(CombatEffectContext ctx, List<ItemStack> loot) {
            if (loot.isEmpty()) return;
            if (RNG.nextFloat() < 0.30f) {
                loot.add(loot.get(RNG.nextInt(loot.size())).copy());
            }
        }
    }

    /** +50% emeralds (rounding up to at least +1). */
    public static final class VillagerHat implements CombatEffectHandler {
        @Override
        public CombatResult onEmeraldGain(CombatEffectContext ctx, int amount) {
            if (amount <= 0) return CombatResult.unchanged(amount);
            int bonus = Math.max(1, (int) Math.ceil(amount * 0.5));
            return CombatResult.modify(amount + bonus, "§a🎩 Villager Hat: +" + bonus + " emeralds");
        }
    }

    /** On hit, pulls the target 1 tile closer toward the player. */
    public static final class CowboyHat implements CombatEffectHandler {
        @Override
        public CombatResult onDealDamage(CombatEffectContext ctx, CombatEntity target, int damage) {
            if (target != null && target.isAlive()) {
                if (pullToward(ctx.getArena(), target, ctx.getArena().getPlayerGridPos())) {
                    syncIfPossible(ctx);
                }
            }
            return CombatResult.unchanged(damage);
        }
    }

    /** +1 bonus item from loot rolls. */
    public static final class AnglersHat implements CombatEffectHandler {
        @Override
        public void onLootRoll(CombatEffectContext ctx, List<ItemStack> loot) {
            if (loot.isEmpty()) return;
            loot.add(loot.get(RNG.nextInt(loot.size())).copy());
        }
    }

    /** Immune to SOAKED. */
    public static final class Snorkel implements CombatEffectHandler {
        @Override
        public CombatResult onEffectApplied(CombatEffectContext ctx, CombatEffects.EffectType effect, int turns) {
            if (effect == CombatEffects.EffectType.SOAKED) {
                return CombatResult.cancel("§b🤿 Snorkel blocks SOAKED");
            }
            return CombatResult.unchanged(turns);
        }
    }

    /** Heal 1 HP at the start of each turn. */
    public static final class PlasticDrinkingHat implements CombatEffectHandler {
        @Override
        public void onTurnStart(CombatEffectContext ctx) {
            ServerPlayerEntity p = ctx.getPlayer();
            if (p.getHealth() < p.getMaxHealth()) {
                p.heal(1);
            }
        }
    }

    /** At combat start, randomly grants Strength / Speed / Resistance / Luck for the fight. */
    public static final class NoveltyDrinkingHat implements CombatEffectHandler {
        @Override
        public void onCombatStart(CombatEffectContext ctx) {
            ArtifactsState s = state(ctx);
            // Always re-roll at the start of a new fight. onCombatStart only fires once
            // per combat (subsequent turn-start scans never invoke onCombatStart on their
            // fresh handler instances), so we don't need a "rolled already" guard.
            ArtifactsState.NoveltyStat[] options = ArtifactsState.NoveltyStat.values();
            s.noveltyStat = options[RNG.nextInt(options.length)];
            CombatEffects fx = ctx.getPlayerEffects();
            int dur = 999;
            String label;
            switch (s.noveltyStat) {
                case MELEE -> { fx.addEffect(CombatEffects.EffectType.STRENGTH, dur, 0); label = "Strength"; }
                case SPEED -> { fx.addEffect(CombatEffects.EffectType.SPEED, dur, 0); label = "Speed"; }
                case DEFENSE -> { fx.addEffect(CombatEffects.EffectType.RESISTANCE, dur, 0); label = "Resistance"; }
                case LUCK -> { fx.addEffect(CombatEffects.EffectType.LUCK, dur, 0); label = "Luck"; }
                default -> { label = ""; }
            }
            ctx.getPlayer().sendMessage(
                net.minecraft.text.Text.literal("§d§l\"Am I a Pretty Girl?\" §r§dGranted " + label + "!"),
                false);
        }
    }

    // ======================================================================
    // Necklace slot
    // ======================================================================

    /** After being hit, the next hit you take deals 50% less damage. Resets each turn. */
    public static final class CrossNecklace implements CombatEffectHandler {
        @Override
        public void onTurnStart(CombatEffectContext ctx) {
            state(ctx).crossShieldActive = false;
        }
        @Override
        public CombatResult onTakeDamage(CombatEffectContext ctx, CombatEntity attacker, int damage) {
            ArtifactsState s = state(ctx);
            if (s.crossShieldActive) {
                int reduced = Math.max(1, damage / 2);
                s.crossShieldActive = false;
                return CombatResult.modify(reduced, "§c✟ Cross Necklace blocks " + (damage - reduced) + " damage");
            }
            // First hit primes the shield for the *next* incoming hit.
            s.crossShieldActive = true;
            return CombatResult.unchanged(damage);
        }
    }

    /** At turn start, deal 2 fire damage + 1 BURNING (2 turns) to all adjacent enemies. */
    public static final class FlamePendant implements CombatEffectHandler {
        @Override
        public void onTurnStart(CombatEffectContext ctx) {
            GridArena arena = ctx.getArena();
            if (arena == null) return;
            for (CombatEntity e : adjacentEnemies(arena, arena.getPlayerGridPos())) {
                e.applyDirectDamage(2);
                e.stackBurning(2, 1);
            }
        }
    }

    /** Reflect 25% (min 1) of damage taken back to the attacker. */
    public static final class ThornPendant implements CombatEffectHandler {
        @Override
        public CombatResult onTakeDamage(CombatEffectContext ctx, CombatEntity attacker, int damage) {
            if (attacker != null && attacker.isAlive() && damage > 0) {
                int reflect = Math.max(1, (int) Math.round(damage * 0.25));
                attacker.applyDirectDamage(reflect);
            }
            return CombatResult.unchanged(damage);
        }
    }

    /** When you take damage, gain +2 Speed for the rest of the turn. */
    public static final class PanicNecklace implements CombatEffectHandler {
        @Override
        public CombatResult onTakeDamage(CombatEffectContext ctx, CombatEntity attacker, int damage) {
            ctx.getPlayerEffects().addEffect(CombatEffects.EffectType.SPEED, 1, 0);
            return CombatResult.unchanged(damage);
        }
    }

    /** 30% chance on hit to chain 3 damage to an adjacent enemy. */
    public static final class ShockPendant implements CombatEffectHandler {
        @Override
        public CombatResult onDealDamage(CombatEffectContext ctx, CombatEntity target, int damage) {
            if (target == null || RNG.nextFloat() >= 0.30f) return CombatResult.unchanged(damage);
            for (CombatEntity adj : adjacentEnemies(ctx.getArena(), target.getGridPos())) {
                if (adj == target) continue;
                adj.applyDirectDamage(3);
                return new CombatResult(damage, List.of("§b⚡ Shock Pendant chains 3 to " + adj.getDisplayName()), false);
            }
            return CombatResult.unchanged(damage);
        }
    }

    /** Immune to knockback (paired with the +1 Defense stat bonus). */
    public static final class CharmOfSinking implements CombatEffectHandler {
        @Override
        public CombatResult onKnockback(CombatEffectContext ctx, CombatEntity source, int distance) {
            return CombatResult.cancel("§7Charm of Sinking ignores knockback");
        }
    }

    /** 20% chance to dodge any incoming attack. */
    public static final class CharmOfShrinking implements CombatEffectHandler {
        @Override
        public CombatResult onTakeDamage(CombatEffectContext ctx, CombatEntity attacker, int damage) {
            if (RNG.nextFloat() < 0.20f) {
                return CombatResult.cancel("§7Charm of Shrinking — dodged!");
            }
            return CombatResult.unchanged(damage);
        }
    }

    /** First attack of combat deals 2× damage. */
    public static final class ScarfOfInvisibility implements CombatEffectHandler {
        @Override
        public void onCombatStart(CombatEffectContext ctx) {
            state(ctx).scarfFirstAttackUsed = false;
        }
        @Override
        public CombatResult onDealDamage(CombatEffectContext ctx, CombatEntity target, int damage) {
            ArtifactsState s = state(ctx);
            if (!s.scarfFirstAttackUsed) {
                s.scarfFirstAttackUsed = true;
                int doubled = damage * 2;
                return CombatResult.modify(doubled, "§5🥷 Scarf of Invisibility — surprise strike! (×2)");
            }
            return CombatResult.unchanged(damage);
        }
    }

    /** 15% chance to crit for double damage; +1 bonus loot item. */
    public static final class LuckyScarf implements CombatEffectHandler {
        @Override
        public CombatResult onDealDamage(CombatEffectContext ctx, CombatEntity target, int damage) {
            if (RNG.nextFloat() < 0.15f) {
                return CombatResult.modify(damage * 2, "§e🍀 Lucky Scarf crit!");
            }
            return CombatResult.unchanged(damage);
        }
        @Override
        public void onLootRoll(CombatEffectContext ctx, List<ItemStack> loot) {
            if (loot.isEmpty()) return;
            loot.add(loot.get(RNG.nextInt(loot.size())).copy());
        }
    }

    // ======================================================================
    // Ring slot
    // ======================================================================

    /** +2 emeralds per emerald payout. */
    public static final class GoldenHook implements CombatEffectHandler {
        @Override
        public CombatResult onEmeraldGain(CombatEffectContext ctx, int amount) {
            if (amount <= 0) return CombatResult.unchanged(amount);
            return CombatResult.modify(amount + 2, "§6⚓ Golden Hook: +2 emeralds");
        }
    }

    /** On hit, apply 1 stack of BURNING for 2 turns. */
    public static final class PickaxeHeater implements CombatEffectHandler {
        @Override
        public CombatResult onDealDamage(CombatEffectContext ctx, CombatEntity target, int damage) {
            if (target != null && target.isAlive()) {
                target.stackBurning(2, 1);
            }
            return CombatResult.unchanged(damage);
        }
    }

    /** On hit, reduces the target's max HP by 1 (true Wither effect). */
    public static final class WitheredBracelet implements CombatEffectHandler {
        @Override
        public CombatResult onDealDamage(CombatEffectContext ctx, CombatEntity target, int damage) {
            if (target != null && target.isAlive()) {
                target.addMaxHpReduction(1);
                // If the new effective max is below current HP, clamp by dealing the difference.
                int over = target.getCurrentHp() - target.getEffectiveMaxHp();
                if (over > 0) target.applyDirectDamage(over);
            }
            return CombatResult.unchanged(damage);
        }
    }

    // ======================================================================
    // Hand slot
    // ======================================================================

    /** Ignore 50% of effective defense — adds the ignored portion as bonus damage. */
    public static final class DiggingClaws implements CombatEffectHandler {
        @Override
        public CombatResult onDealDamage(CombatEffectContext ctx, CombatEntity target, int damage) {
            if (target == null) return CombatResult.unchanged(damage);
            int def = target.getEffectiveDefense();
            if (def <= 0) return CombatResult.unchanged(damage);
            // Each defense point ≈ 5% mitigation; halving defense recovers ~half of that.
            int bonus = Math.max(1, (int) Math.round(damage * (def * 0.05) * 0.5));
            return CombatResult.modify(damage + bonus, "§7⛏ Digging Claws ignore " + bonus + " defense");
        }
    }

    /** Refund 1 AP on kill. */
    public static final class FeralClaws implements CombatEffectHandler {
        @Override
        public void onDealKillingBlow(CombatEffectContext ctx, CombatEntity killed) {
            var cm = com.crackedgames.craftics.combat.CombatManager.get(ctx.getPlayer());
            if (cm == null) {
                com.crackedgames.craftics.CrafticsMod.LOGGER.warn(
                    "[Craftics × Artifacts] Feral Claws: no CombatManager for {}",
                    ctx.getPlayer().getName().getString());
                return;
            }
            int before = cm.getApRemaining();
            cm.setApRemaining(before + 1);
            com.crackedgames.craftics.CrafticsMod.LOGGER.info(
                "[Craftics × Artifacts] Feral Claws fired — AP {} → {}", before, before + 1);
            ctx.getPlayer().sendMessage(
                net.minecraft.text.Text.literal("§6🐾 Feral Claws: §e+1 AP refunded"), false);
        }
    }

    /** On hit, apply 2 stacks BURNING (3 turns); on kill, AoE 3 fire dmg + 1 BURNING (2 turns). */
    public static final class FireGauntlet implements CombatEffectHandler {
        @Override
        public CombatResult onDealDamage(CombatEffectContext ctx, CombatEntity target, int damage) {
            if (target != null && target.isAlive()) {
                target.stackBurning(3, 2);
            }
            return CombatResult.unchanged(damage);
        }
        @Override
        public void onDealKillingBlow(CombatEffectContext ctx, CombatEntity killed) {
            if (killed == null) return;
            for (CombatEntity e : adjacentEnemies(ctx.getArena(), killed.getGridPos())) {
                if (e == killed) continue;
                e.applyDirectDamage(3);
                e.stackBurning(2, 1);
            }
        }
    }

    /** Knock target back 2 tiles; +3 collision damage if blocked. */
    public static final class PocketPiston implements CombatEffectHandler {
        @Override
        public CombatResult onDealDamage(CombatEffectContext ctx, CombatEntity target, int damage) {
            if (target == null || !target.isAlive()) return CombatResult.unchanged(damage);
            GridArena arena = ctx.getArena();
            GridPos player = arena.getPlayerGridPos();
            GridPos start = target.getGridPos();
            int dx = Integer.signum(start.x() - player.x());
            int dz = Integer.signum(start.z() - player.z());
            if (dx == 0 && dz == 0) return CombatResult.unchanged(damage);
            GridPos current = start;
            int moved = 0;
            boolean collided = false;
            for (int i = 1; i <= 2; i++) {
                GridPos candidate = new GridPos(start.x() + dx * i, start.z() + dz * i);
                if (!arena.isInBounds(candidate) || arena.isOccupied(candidate)) {
                    collided = true;
                    break;
                }
                current = candidate;
                moved++;
            }
            if (moved > 0) {
                arena.moveEntity(target, current);
                syncIfPossible(ctx);
            }
            if (collided) {
                target.applyDirectDamage(3);
                return new CombatResult(damage, List.of("§e🥊 Pocket Piston slam — +3 collision damage"), false);
            }
            return CombatResult.unchanged(damage);
        }
    }

    /** Heal for 25% of damage dealt (min 1). */
    public static final class VampiricGlove implements CombatEffectHandler {
        @Override
        public CombatResult onDealDamage(CombatEffectContext ctx, CombatEntity target, int damage) {
            if (damage > 0) {
                int heal = Math.max(1, (int) Math.round(damage * 0.25));
                ctx.getPlayer().heal(heal);
            }
            return CombatResult.unchanged(damage);
        }
    }

    // ======================================================================
    // Belt slot
    // ======================================================================

    /** Immune to knockback. (Move-through-enemies portion isn't expressible via hooks.) */
    public static final class HeliumFlamingo implements CombatEffectHandler {
        @Override
        public CombatResult onKnockback(CombatEffectContext ctx, CombatEntity source, int distance) {
            return CombatResult.cancel("§b🦩 Helium Flamingo ignores knockback");
        }
    }

    /** Once per combat, survive lethal damage and heal to 25% of max HP. */
    public static final class ChorusTotem implements CombatEffectHandler {
        @Override
        public void onCombatStart(CombatEffectContext ctx) {
            state(ctx).chorusTotemUsed = false;
        }
        @Override
        public CombatResult onLethalDamage(CombatEffectContext ctx, CombatEntity attacker, int damage) {
            ArtifactsState s = state(ctx);
            if (s.chorusTotemUsed) return CombatResult.unchanged(damage);
            s.chorusTotemUsed = true;
            ServerPlayerEntity p = ctx.getPlayer();
            float restored = p.getMaxHealth() * 0.25f;
            p.setHealth(Math.max(1f, restored));
            return CombatResult.cancel("§5✦ Chorus Totem — saved from death!");
        }
    }

    /** Immune to BURNING. */
    public static final class ObsidianSkull implements CombatEffectHandler {
        @Override
        public CombatResult onEffectApplied(CombatEffectContext ctx, CombatEffects.EffectType effect, int turns) {
            if (effect == CombatEffects.EffectType.BURNING) {
                return CombatResult.cancel("§8💀 Obsidian Skull blocks BURNING");
            }
            return CombatResult.unchanged(turns);
        }
    }

    /** 15% chance to dodge incoming attacks (paired with +2 Speed stat bonus). */
    public static final class CloudInABottle implements CombatEffectHandler {
        @Override
        public CombatResult onTakeDamage(CombatEffectContext ctx, CombatEntity attacker, int damage) {
            if (RNG.nextFloat() < 0.15f) {
                return CombatResult.cancel("§b☁ Cloud in a Bottle — dodged!");
            }
            return CombatResult.unchanged(damage);
        }
    }

    /** Immune to POISON and CONFUSION. */
    public static final class AntidoteVessel implements CombatEffectHandler {
        @Override
        public CombatResult onEffectApplied(CombatEffectContext ctx, CombatEffects.EffectType effect, int turns) {
            if (effect == CombatEffects.EffectType.POISON || effect == CombatEffects.EffectType.CONFUSION) {
                return CombatResult.cancel("§a🧪 Antidote Vessel blocks " + effect.displayName);
            }
            return CombatResult.unchanged(turns);
        }
    }

    /** Pull all living enemies 1 tile toward the player at turn start; +1 loot. */
    public static final class UniversalAttractor implements CombatEffectHandler {
        @Override
        public void onTurnStart(CombatEffectContext ctx) {
            GridArena arena = ctx.getArena();
            if (arena == null) return;
            GridPos anchor = arena.getPlayerGridPos();
            boolean anyMoved = false;
            for (CombatEntity e : ctx.getAllEnemies()) {
                if (pullToward(arena, e, anchor)) anyMoved = true;
            }
            if (anyMoved) syncIfPossible(ctx);
        }
        @Override
        public void onLootRoll(CombatEffectContext ctx, List<ItemStack> loot) {
            if (loot.isEmpty()) return;
            loot.add(loot.get(RNG.nextInt(loot.size())).copy());
        }
    }

    // ======================================================================
    // Misc slot
    // ======================================================================

    /** Block first hit of combat completely; 10% block on incoming ranged hits. */
    public static final class Umbrella implements CombatEffectHandler {
        @Override
        public void onCombatStart(CombatEffectContext ctx) {
            state(ctx).umbrellaFirstHitUsed = false;
        }
        @Override
        public CombatResult onTakeDamage(CombatEffectContext ctx, CombatEntity attacker, int damage) {
            ArtifactsState s = state(ctx);
            if (!s.umbrellaFirstHitUsed) {
                s.umbrellaFirstHitUsed = true;
                return CombatResult.cancel("§f☂ Umbrella blocks the first hit!");
            }
            if (attacker != null && attacker.getRange() > 1 && RNG.nextFloat() < 0.10f) {
                return CombatResult.cancel("§f☂ Umbrella blocks the ranged shot!");
            }
            return CombatResult.unchanged(damage);
        }
    }

    // ======================================================================
    // Feet slot
    // ======================================================================

    /** 10% chance to dodge incoming attacks. */
    public static final class BunnyHoppers implements CombatEffectHandler {
        @Override
        public CombatResult onTakeDamage(CombatEffectContext ctx, CombatEntity attacker, int damage) {
            if (RNG.nextFloat() < 0.10f) {
                return CombatResult.cancel("§a🐰 Bunny Hoppers — dodged!");
            }
            return CombatResult.unchanged(damage);
        }
    }

    /** +2 bonus Water damage; on move applies SLOWNESS to enemies adjacent to destination. */
    public static final class AquaDashers implements CombatEffectHandler {
        @Override
        public CombatResult onDealDamage(CombatEffectContext ctx, CombatEntity target, int damage) {
            return CombatResult.modify(damage + 2, "§b🌊 Aqua Dashers +2 water");
        }
        @Override
        public void onMove(CombatEffectContext ctx, GridPos from, GridPos to, int distance) {
            for (CombatEntity e : adjacentEnemies(ctx.getArena(), to)) {
                e.stackSlowness(1, 1);
            }
        }
    }

    /** Heal 3 if you didn't move this turn, else 1, at the end of your turn. */
    public static final class RootedBoots implements CombatEffectHandler {
        @Override
        public void onTurnStart(CombatEffectContext ctx) {
            state(ctx).movedThisTurn = false;
        }
        @Override
        public void onMove(CombatEffectContext ctx, GridPos from, GridPos to, int distance) {
            if (distance > 0) state(ctx).movedThisTurn = true;
        }
        @Override
        public void onTurnEnd(CombatEffectContext ctx) {
            ServerPlayerEntity p = ctx.getPlayer();
            int heal = state(ctx).movedThisTurn ? 1 : 3;
            if (p.getHealth() < p.getMaxHealth()) p.heal(heal);
        }
    }

    /** SLOWNESS aura at turn start + on movement. */
    public static final class Snowshoes implements CombatEffectHandler {
        @Override
        public void onTurnStart(CombatEffectContext ctx) {
            for (CombatEntity e : adjacentEnemies(ctx.getArena(), ctx.getArena().getPlayerGridPos())) {
                e.stackSlowness(1, 1);
            }
        }
        @Override
        public void onMove(CombatEffectContext ctx, GridPos from, GridPos to, int distance) {
            for (CombatEntity e : adjacentEnemies(ctx.getArena(), to)) {
                e.stackSlowness(1, 1);
            }
        }
    }

    /** Immune to knockback; reflect 2 damage to melee attackers. */
    public static final class SteadfastSpikes implements CombatEffectHandler {
        @Override
        public CombatResult onKnockback(CombatEffectContext ctx, CombatEntity source, int distance) {
            return CombatResult.cancel("§7⚙ Steadfast Spikes resist knockback");
        }
        @Override
        public CombatResult onTakeDamage(CombatEffectContext ctx, CombatEntity attacker, int damage) {
            if (attacker != null && attacker.isAlive() && attacker.getRange() <= 1) {
                attacker.applyDirectDamage(2);
            }
            return CombatResult.unchanged(damage);
        }
    }

    /** +3 bonus damage to SOAKED targets. */
    public static final class Flippers implements CombatEffectHandler {
        @Override
        public CombatResult onDealDamage(CombatEffectContext ctx, CombatEntity target, int damage) {
            if (target != null && target.getSoakedTurns() > 0) {
                return CombatResult.modify(damage + 3, "§b🐟 Flippers +3 vs. SOAKED");
            }
            return CombatResult.unchanged(damage);
        }
    }

    /** Immune to BURNING and SLOWNESS. */
    public static final class StriderShoes implements CombatEffectHandler {
        @Override
        public CombatResult onEffectApplied(CombatEffectContext ctx, CombatEffects.EffectType effect, int turns) {
            if (effect == CombatEffects.EffectType.BURNING || effect == CombatEffects.EffectType.SLOWNESS) {
                return CombatResult.cancel("§c👟 Strider Shoes block " + effect.displayName);
            }
            return CombatResult.unchanged(turns);
        }
    }

    /** 25% chance at turn start to apply CONFUSION to a random enemy; 15% on hit to stun the attacker. */
    public static final class WhoopeeCushion implements CombatEffectHandler {
        @Override
        public void onTurnStart(CombatEffectContext ctx) {
            if (RNG.nextFloat() >= 0.25f) return;
            List<CombatEntity> enemies = ctx.getAllEnemies();
            if (enemies.isEmpty()) return;
            CombatEntity victim = enemies.get(RNG.nextInt(enemies.size()));
            victim.stackConfusion(1, 1);
        }
        @Override
        public CombatResult onTakeDamage(CombatEffectContext ctx, CombatEntity attacker, int damage) {
            if (attacker != null && attacker.isAlive() && RNG.nextFloat() < 0.15f) {
                attacker.setStunned(true);
            }
            return CombatResult.unchanged(damage);
        }
    }
}
