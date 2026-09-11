package com.crackedgames.craftics.combat.sherd;

import com.crackedgames.craftics.combat.CombatEffects;
import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.DamageType;
import com.crackedgames.craftics.combat.HoeEnchantEffects;
import com.crackedgames.craftics.combat.PlayerProgression;
import com.crackedgames.craftics.combat.TrimEffects;
import com.crackedgames.craftics.core.GridPos;
import com.crackedgames.craftics.core.GridTile;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The vocabulary a sherd is written in.
 *
 * <p>Each factory here returns one {@link SpellEffect}, and a sherd is a list of them. Nothing
 * in this class knows which sherd it belongs to, which is the whole point: chain lightning that
 * also burns is the lightning damage effect and the burn effect in the same step, and neither
 * had to be told about the other.
 *
 * <p>Numbers are deliberately parameters rather than constants. Every value that used to be
 * hard-coded inside a {@code useXSherd} body - damage, turns, radius, knockback distance, the
 * execute threshold - is an argument, so re-tuning a sherd is editing its definition and never
 * this file.
 */
public final class Effects {

    private Effects() {}

    // ─────────────────────────────────────────────────────────────────────
    // Damage
    // ─────────────────────────────────────────────────────────────────────

    /** Conditions a damage bonus can be gated on, evaluated per target at cast time. */
    public enum Condition {
        /** The target is adjacent to the caster after any movement this step. */
        ADJACENT_TO_CASTER,
        /** The target is next to a wall, an unwalkable tile, or the arena edge. */
        NEAR_OBSTACLE,
        /** The target is Soaked. */
        SOAKED,
        /** The target is burning. */
        BURNING,
        /** The target is stunned. */
        STUNNED
    }

    /**
     * Damage, with every knob the existing sherds needed and several they did not have.
     *
     * <p>{@code decayPerHop} is what makes falloff a property of the spell rather than of the
     * chain code: a flat AoE leaves it 0 and behaves as it always did, and only a chaining
     * spell ever sees a non-zero hop.
     */
    public static final class DamageBuilder {
        private int flat;
        private double pctMaxHp = 0.0;
        private int decayPerHop = 0;
        private int minimum = 0;
        private boolean plain = false;
        private boolean lightning = false;
        private boolean fractionOfMaxHp = false;
        private int hpDivisor = 2;
        private Condition bonusCondition = null;
        private int bonusAmount = 0;
        private String label = "";

        private DamageBuilder(int flat) { this.flat = flat; }

        /** Add a percentage of the target's max HP, so the hit scales into late biomes. */
        public DamageBuilder pctMaxHp(double pct) { this.pctMaxHp = pct; return this; }

        /** Subtract this much per chain link. Ignored by non-chaining spells. */
        public DamageBuilder decayPerHop(int d) { this.decayPerHop = d; return this; }

        /** Never fall below this after decay. */
        public DamageBuilder minimum(int m) { this.minimum = m; return this; }

        /**
         * Use the flat damage path instead of the Special path.
         *
         * <p>Matches what the splash halves of Blade and Burn always did - they called
         * {@code takeDamage} while the primary hit called {@code takeSpecialDamage}, so a
         * faithful port needs to be able to say which.
         */
        public DamageBuilder plain() { this.plain = true; return this; }

        /** Route through the lightning path: Special-affinity scaling and 2x on Soaked. */
        public DamageBuilder lightning() { this.lightning = true; return this; }

        /** Anvil-grade: {@code max(flat, maxHp / divisor)}, as the petsplosion uses. */
        public DamageBuilder orFractionOfMaxHp(int divisor) {
            this.fractionOfMaxHp = true;
            this.hpDivisor = divisor;
            return this;
        }

        /** Extra damage when the condition holds for this target. */
        public DamageBuilder bonusIf(Condition condition, int amount) {
            this.bonusCondition = condition;
            this.bonusAmount = amount;
            return this;
        }

        /** Damage-type word for the chat line, e.g. {@code "WATER"}. Cosmetic. */
        public DamageBuilder label(String l) { this.label = l; return this; }

        public SpellEffect build() {
            return (ctx, target, report) -> {
                if (!target.hasEntity()) return;
                CombatEntity victim = target.entity();
                if (!victim.isAlive()) return;

                int base = flat;
                if (fractionOfMaxHp) base = Math.max(flat, victim.getMaxHp() / hpDivisor);
                base -= target.hop() * decayPerHop;
                if (minimum > 0) base = Math.max(minimum, base);
                if (base < 0) base = 0;

                boolean bonusApplied = bonusCondition != null && holds(bonusCondition, ctx, victim);
                if (bonusApplied) base += bonusAmount;

                int dealt;
                if (lightning) {
                    dealt = victim.takeSpecialLightningDamage(base + specialBonus(ctx), pctMaxHp);
                } else if (plain) {
                    dealt = victim.takeDamage(base);
                } else {
                    dealt = victim.takeSpecialDamage(base, pctMaxHp);
                }

                report.addDamage(dealt);
                report.hit(target.tile());
                String type = label.isEmpty() ? "" : label + " ";
                String bonusTag = bonusApplied ? " §e(+" + bonusAmount + ")" : "";
                report.say("§f" + victim.getDisplayName() + " takes §c" + dealt
                    + "§f " + type + "damage" + bonusTag);
            };
        }
    }

    /** Damage the target. Chain to {@code .pctMaxHp} / {@code .decayPerHop} / {@code .bonusIf}. */
    public static DamageBuilder damage(int flat) { return new DamageBuilder(flat); }

    /**
     * Kill outright.
     *
     * <p>Pair with {@code Selector...onlyBelowHp(x).excludeBosses()} - the threshold and the
     * boss exemption are targeting questions, not damage ones, so they live on the selector
     * where any spell can borrow them.
     */
    public static SpellEffect execute() {
        return (ctx, target, report) -> {
            if (!target.hasEntity() || !target.entity().isAlive()) return;
            CombatEntity victim = target.entity();
            report.hit(target.tile());
            victim.takeDamage(9999);
            report.say("§4§l☠ " + victim.getDisplayName() + " obliterated!");
        };
    }

    // ─────────────────────────────────────────────────────────────────────
    // Status
    // ─────────────────────────────────────────────────────────────────────

    /** Set the target alight for {@code turns}, and light the real mob for flavour. */
    public static SpellEffect burn(int turns, int amplifier, int fireTicks) {
        return (ctx, target, report) -> {
            if (!target.hasEntity() || !target.entity().isAlive()) return;
            CombatEntity victim = target.entity();
            victim.stackBurning(turns, amplifier);
            if (victim.getMobEntity() != null) victim.getMobEntity().setFireTicks(fireTicks);
            report.hit(target.tile());
            report.say("§6burning (" + turns + "t)");
        };
    }

    public static SpellEffect wither(int turns, int amplifier) {
        return (ctx, target, report) -> {
            if (!target.hasEntity() || !target.entity().isAlive()) return;
            target.entity().stackWither(turns, amplifier);
            report.say("§5Wither (" + turns + "t)");
        };
    }

    public static SpellEffect stun() {
        return (ctx, target, report) -> {
            if (!target.hasEntity() || !target.entity().isAlive()) return;
            target.entity().setStunned(true);
            report.hit(target.tile());
            report.say("§estunned");
        };
    }

    public static SpellEffect defensePenalty(int turns, int amount) {
        return (ctx, target, report) -> {
            if (!target.hasEntity() || !target.entity().isAlive()) return;
            target.entity().stackDefensePenalty(turns, amount);
            report.say("§7-" + amount + " DEF (" + turns + "t)");
        };
    }

    public static SpellEffect attackPenalty(int amount) {
        return (ctx, target, report) -> {
            if (!target.hasEntity() || !target.entity().isAlive()) return;
            CombatEntity victim = target.entity();
            victim.setAttackPenalty(victim.getAttackPenalty() + amount);
            report.say("§7-" + amount + " ATK");
        };
    }

    /** Shift the target's speed. Negative slows, positive hastens. */
    public static SpellEffect speedChange(int amount) {
        return (ctx, target, report) -> {
            if (!target.hasEntity() || !target.entity().isAlive()) return;
            CombatEntity victim = target.entity();
            victim.setSpeedBonus(victim.getSpeedBonus() + amount);
            report.say("§7" + (amount >= 0 ? "+" : "") + amount + " SPD");
        };
    }

    // ─────────────────────────────────────────────────────────────────────
    // Support - the ally-facing half, usable by any spell that points at pets
    // ─────────────────────────────────────────────────────────────────────

    /** Heal the target. Pass a negative amount for "to full". */
    public static SpellEffect healTarget(int amount) {
        return (ctx, target, report) -> {
            if (!target.hasEntity() || !target.entity().isAlive()) return;
            CombatEntity ally = target.entity();
            int healed = amount < 0 ? ally.getEffectiveMaxHp() : amount;
            ally.heal(healed);
            report.hit(target.tile());
            report.say("§a" + ally.getDisplayName() + " healed");
        };
    }

    public static SpellEffect buffTargetAttack(int bonus, int turns) {
        return (ctx, target, report) -> {
            if (!target.hasEntity() || !target.entity().isAlive()) return;
            target.entity().applyAttackBuff(bonus, turns);
            report.say("§a+" + bonus + " ATK");
        };
    }

    public static SpellEffect buffTargetSpeed(int bonus, int turns) {
        return (ctx, target, report) -> {
            if (!target.hasEntity() || !target.entity().isAlive()) return;
            target.entity().applySpeedBuff(bonus, turns);
            report.say("§a+" + bonus + " SPD");
        };
    }

    // ─────────────────────────────────────────────────────────────────────
    // Caster-facing
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Heal the caster, Medic hoe bonus included.
     *
     * <p>Every sherd that healed the player went through one shared helper for exactly this
     * reason - so the Medic bonus could never be forgotten at one of them - and that guarantee
     * is preserved by there being one healing effect rather than one per sherd.
     */
    public static SpellEffect healCaster(int base) {
        return (ctx, target, report) -> {
            int healed = healPlayer(ctx.caster(), base);
            report.say("§ahealed " + healed + " HP");
        };
    }

    /** Heal the caster for the damage this cast has dealt so far. */
    public static SpellEffect lifesteal() {
        return (ctx, target, report) -> {
            int healed = healPlayer(ctx.caster(), report.totalDamage());
            report.say("§ayou heal " + healed + " HP");
        };
    }

    public static SpellEffect casterEffect(CombatEffects.EffectType type, int turns, int amplifier) {
        return (ctx, target, report) -> {
            ctx.casterEffects().addEffect(type, turns, amplifier);
            report.say("§b" + type.displayName + " " + roman(amplifier + 1) + " (" + turns + "t)");
        };
    }

    /** Roll {@code count} distinct effects out of {@code pool} and apply them all. */
    public static SpellEffect randomCasterEffects(List<CombatEffects.EffectType> pool,
                                                  int count, int turns, int amplifier) {
        return (ctx, target, report) -> {
            List<CombatEffects.EffectType> shuffled = new ArrayList<>(pool);
            Collections.shuffle(shuffled);
            StringBuilder names = new StringBuilder();
            for (int i = 0; i < count && i < shuffled.size(); i++) {
                CombatEffects.EffectType type = shuffled.get(i);
                ctx.casterEffects().addEffect(type, turns, amplifier);
                if (names.length() > 0) names.append(", ");
                names.append(type.displayName).append(" ").append(roman(amplifier + 1));
            }
            report.say("§bgained " + names + " (" + turns + "t each)");
        };
    }

    /** Hand the caster random items from a pool, dropping any that will not fit. */
    public static SpellEffect giveItems(Item[] pool, int count) {
        return (ctx, target, report) -> {
            ServerPlayerEntity player = ctx.caster();
            StringBuilder gained = new StringBuilder();
            for (int i = 0; i < count; i++) {
                Item pick = pool[player.getRandom().nextInt(pool.length)];
                ItemStack stack = new ItemStack(pick);
                if (!player.getInventory().insertStack(stack)) player.dropItem(stack, false);
                if (gained.length() > 0) gained.append(", ");
                gained.append(pick.getName().getString());
            }
            report.say("§agained " + gained);
        };
    }

    /** Blink the caster onto the targeted tile. */
    public static SpellEffect teleportCaster() {
        return (ctx, target, report) -> {
            GridPos dest = target.tile();
            ctx.arena().setPlayerGridPos(dest);
            BlockPos block = ctx.blockOf(dest);
            ctx.caster().requestTeleport(block.getX() + 0.5, block.getY(), block.getZ() + 0.5);
            report.hit(dest);
            report.say("§dblinked");
        };
    }

    // ─────────────────────────────────────────────────────────────────────
    // Movement
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Shove the target away from the caster, damaging it per tile travelled and again if it
     * runs out of room.
     *
     * <p>Snort and Flow both did this, with different numbers and two separate copies of the
     * push loop. One effect, parameterised: Flow is this with no wall bonus, Snort is this with
     * one. {@code immovable} targets are refused here rather than at each call site.
     */
    public static SpellEffect knockback(int tiles, int damagePerTile, int wallSlamDamage,
                                        boolean wallSlamStuns) {
        return (ctx, target, report) -> {
            if (!target.hasEntity() || !target.entity().isAlive()) return;
            CombatEntity victim = target.entity();
            if (victim.isImmovable()) return;

            GridPos from = victim.getGridPos();
            GridPos caster = ctx.casterPos();
            int dx = Integer.signum(from.x() - caster.x());
            int dz = Integer.signum(from.z() - caster.z());
            if (dx == 0 && dz == 0) dx = 1;

            GridPos landing = from;
            boolean blocked = false;
            int pushed = 0;
            for (int i = 1; i <= tiles; i++) {
                GridPos candidate = new GridPos(from.x() + dx * i, from.z() + dz * i);
                if (!walkableAndFree(ctx, candidate)) { blocked = true; break; }
                landing = candidate;
                pushed++;
            }
            moveTo(ctx, victim, landing);

            int damage = pushed * damagePerTile;
            if (blocked && wallSlamDamage > 0) {
                damage += wallSlamDamage;
                if (wallSlamStuns) victim.setStunned(true);
            }
            if (damage > 0) {
                int dealt = victim.takeSpecialDamage(damage, 0.08);
                report.addDamage(dealt);
                report.say("§f" + victim.getDisplayName() + " pushed " + pushed
                    + " tiles for §c" + dealt + "§f damage"
                    + (blocked && wallSlamDamage > 0 ? " §c§l(WALL SLAM!)" : ""));
            } else if (pushed > 0) {
                report.say("§f" + victim.getDisplayName() + " pushed " + pushed + " tiles");
            }
            report.hit(victim.getGridPos());
        };
    }

    /** Drag the target toward the caster, stopping short of the caster's own tile. */
    public static SpellEffect pull(int tiles) {
        return (ctx, target, report) -> {
            if (!target.hasEntity() || !target.entity().isAlive()) return;
            CombatEntity victim = target.entity();
            if (victim.isImmovable()) return;

            GridPos from = victim.getGridPos();
            GridPos caster = ctx.casterPos();
            int dx = Integer.signum(caster.x() - from.x());
            int dz = Integer.signum(caster.z() - from.z());

            GridPos landing = from;
            for (int i = 1; i <= tiles; i++) {
                GridPos candidate = new GridPos(from.x() + dx * i, from.z() + dz * i);
                if (candidate.equals(caster)) break;
                if (!walkableAndFree(ctx, candidate)) break;
                landing = candidate;
            }
            if (!landing.equals(from)) {
                moveTo(ctx, victim, landing);
                report.say("§3" + victim.getDisplayName() + " reeled in");
            }
            report.hit(victim.getGridPos());
        };
    }

    // ─────────────────────────────────────────────────────────────────────
    // Out-of-band - things only CombatManager can carry out
    // ─────────────────────────────────────────────────────────────────────

    /** Register a tile effect at the targeted tile (the hex trap and anything like it). */
    public static SpellEffect placeTileEffect(String effectId, String description) {
        return (ctx, target, report) -> {
            GridPos tile = target.tile();
            ctx.addDirective(com.crackedgames.craftics.combat.ItemUseHandler.TILE_EFFECT_PREFIX
                + effectId + ":" + tile.x() + ":" + tile.z());
            report.hit(tile);
            report.say(description + " at (" + tile.x() + "," + tile.z() + ")");
        };
    }

    /** Summon seeker projectiles. Luck buys extras on the usual +2%/point curve. */
    public static SpellEffect summonSeekers(int baseCount, int damage) {
        return (ctx, target, report) -> {
            int luck = PlayerProgression.get(ctx.world()).getStats(ctx.caster())
                .getPoints(PlayerProgression.Stat.LUCK);
            int count = baseCount;
            if (ctx.caster().getRandom().nextDouble() < luck * 0.02) count++;
            ctx.addDirective(com.crackedgames.craftics.combat.PotterySherdSpells.SEEKERS_PREFIX
                + count + ":" + damage);
            report.say("§b" + count + " seekers");
        };
    }

    /** Arm the caster's next attack for double damage. */
    public static SpellEffect doubleNextAttack() {
        return (ctx, target, report) -> {
            ctx.addDirective(com.crackedgames.craftics.combat.PotterySherdSpells.DOUBLE_NEXT_PREFIX + "1");
            report.say("§6next attack deals §6§lDOUBLE DAMAGE");
        };
    }

    /**
     * Anything the vocabulary above cannot say yet.
     *
     * <p>An escape hatch on purpose. A new sherd idea should be buildable the moment it is
     * thought of, and only promoted into a named effect once it turns out to be reusable -
     * otherwise the vocabulary grows entries that exist for exactly one spell, which is the
     * situation this whole package replaced.
     */
    public static SpellEffect custom(SpellEffect effect) { return effect; }

    // ─────────────────────────────────────────────────────────────────────
    // Shared internals
    // ─────────────────────────────────────────────────────────────────────

    private static int healPlayer(ServerPlayerEntity player, int base) {
        int amount = base + HoeEnchantEffects.medicBonus(player);
        float before = player.getHealth();
        player.setHealth(Math.min(player.getMaxHealth(), before + amount));
        return Math.round(player.getHealth() - before);
    }

    /** Special-damage bonus from affinity, trims, effects and a worn mob head. */
    private static int specialBonus(SpellContext ctx) {
        return DamageType.getTotalBonus(
                ctx.caster(), TrimEffects.scan(ctx.caster()), ctx.casterEffects(), DamageType.SPECIAL,
                PlayerProgression.get(ctx.world()).getStats(ctx.caster()))
            + DamageType.getMobHeadBonus(
                ctx.caster().getEquippedStack(net.minecraft.entity.EquipmentSlot.HEAD), DamageType.SPECIAL);
    }

    private static boolean walkableAndFree(SpellContext ctx, GridPos pos) {
        if (!ctx.arena().isInBounds(pos)) return false;
        if (ctx.arena().isOccupied(pos)) return false;
        GridTile tile = ctx.arena().getTile(pos);
        return tile != null && tile.isWalkable();
    }

    private static void moveTo(SpellContext ctx, CombatEntity entity, GridPos dest) {
        if (dest.equals(entity.getGridPos())) return;
        ctx.arena().moveEntity(entity, dest);
        if (entity.getMobEntity() != null) {
            BlockPos block = ctx.blockOf(dest);
            entity.getMobEntity().requestTeleport(block.getX() + 0.5, block.getY(), block.getZ() + 0.5);
        }
    }

    private static boolean holds(Condition condition, SpellContext ctx, CombatEntity victim) {
        return switch (condition) {
            case ADJACENT_TO_CASTER -> victim.minDistanceTo(ctx.casterPos()) <= 1;
            case SOAKED -> victim.getSoakedTurns() > 0;
            case BURNING -> victim.getBurningTurns() > 0;
            case STUNNED -> victim.isStunned();
            case NEAR_OBSTACLE -> nearObstacle(ctx, victim.getGridPos());
        };
    }

    /** True if any of the eight neighbours is unwalkable or off the arena. */
    private static boolean nearObstacle(SpellContext ctx, GridPos pos) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                GridPos adj = new GridPos(pos.x() + dx, pos.z() + dz);
                if (!ctx.arena().isInBounds(adj)) return true;
                GridTile tile = ctx.arena().getTile(adj);
                if (tile != null && !tile.isWalkable()) return true;
            }
        }
        return false;
    }

    private static String roman(int n) {
        return switch (n) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> String.valueOf(n);
        };
    }
}
