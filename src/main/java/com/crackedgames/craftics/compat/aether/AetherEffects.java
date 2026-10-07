package com.crackedgames.craftics.compat.aether;

import com.crackedgames.craftics.api.CombatEffectContext;
import com.crackedgames.craftics.api.CombatEffectHandler;
import com.crackedgames.craftics.api.CombatResult;
import com.crackedgames.craftics.combat.CombatEffects;
import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.core.GridPos;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The conditional half of Aether gear: what a set or an accessory does when something
 * happens, as opposed to the flat stats {@link AetherScanner} adds directly.
 *
 * <p>Handlers hold no state of their own. Craftics rebuilds the scanner result, and so these
 * instances, every turn - anything that must survive between turns lives in a static map
 * keyed by player, the same arrangement the Artifacts compat uses.
 */
final class AetherEffects {

    private AetherEffects() {}

    /**
     * The Aether's rule that gear from below is out of its depth up there, kept but softened.
     *
     * <p>In the Aether itself an overworld sword's damage is raised to the power 0.6 against an
     * Aether mob - a 12-damage blade lands for 4 - and every piece of overworld armor adds a
     * fifteenth of its armor value to each hit taken. That is a wall, and it is meant to be:
     * the mod wants you re-geared before you fight anything. Here the Aether is one optional
     * region of a longer campaign, so the same idea is a lean instead of a wall: an outsider
     * weapon gives up a fifth of its damage, and each outsider armor piece lets a twentieth
     * more through. Enough that Aether gear is the right answer, not so much that arriving
     * in diamond is a mistake.
     *
     * <p>Silent on purpose. It applies to every hit, and a chat line per swing would bury the
     * fight; the player is told once, as the fight opens.
     */
    static final class Outsider implements CombatEffectHandler {
        /** Share of its damage a non-Aether weapon keeps against an Aether mob. */
        static final double WEAPON_KEEPS = 0.80;
        /** Extra damage taken from an Aether mob, per non-Aether armor piece worn. */
        static final double PER_ARMOR_PIECE = 0.05;

        private final int outsiderArmorPieces;

        Outsider(int outsiderArmorPieces) {
            this.outsiderArmorPieces = outsiderArmorPieces;
        }

        static boolean isAetherMob(CombatEntity entity) {
            return entity != null && entity.getEntityTypeId() != null
                && entity.getEntityTypeId().startsWith(AetherCompat.MOD_ID + ":");
        }

        /**
         * What an outsider weapon's hit is worth. Rounded up, so the loss is never MORE than
         * the fifth it is meant to be - plain rounding took a third off a 3-damage hit.
         */
        static int weaponDamage(int damage) {
            if (damage <= 0) return damage;
            return Math.max(1, (int) Math.ceil(damage * WEAPON_KEEPS - 1e-9));
        }

        /** What an Aether mob's hit does through {@code pieces} pieces of outsider armor. */
        static int armorDamage(int damage, int pieces) {
            if (damage <= 0 || pieces <= 0) return damage;
            return damage + (int) Math.ceil(damage * PER_ARMOR_PIECE * Math.min(4, pieces));
        }

        /**
         * A registered weapon that is not from the Aether. Bare hands, food and tools with no
         * combat entry are not "outsider weapons" - the Aether only penalises real weapons,
         * and so does this.
         */
        private static boolean holdsOutsiderWeapon(CombatEffectContext ctx) {
            if (ctx.getPlayer() == null) return false;
            var item = ctx.getPlayer().getMainHandStack().getItem();
            return com.crackedgames.craftics.api.registry.WeaponRegistry.isRegistered(item)
                && !AetherCompat.isAetherItem(item);
        }

        @Override
        public void onCombatStart(CombatEffectContext ctx) {
            if (ctx.getPlayer() == null) return;
            boolean aetherFight = false;
            for (CombatEntity enemy : ctx.getAllEnemies()) {
                if (isAetherMob(enemy)) aetherFight = true;
            }
            if (!aetherFight) return;
            boolean weapon = holdsOutsiderWeapon(ctx);
            if (!weapon && outsiderArmorPieces <= 0) return;
            ctx.getPlayer().sendMessage(net.minecraft.text.Text.literal(
                "§7Gear from below is out of its depth here: "
                    + (weapon ? "your weapon deals 20% less to Aether creatures" : "")
                    + (weapon && outsiderArmorPieces > 0 ? ", and " : "")
                    + (outsiderArmorPieces > 0
                        ? "they hit " + (outsiderArmorPieces * 5) + "% harder through your armor" : "")
                    + "."), false);
        }

        @Override
        public CombatResult onDealDamage(CombatEffectContext ctx, CombatEntity target, int damage) {
            if (!isAetherMob(target) || !holdsOutsiderWeapon(ctx)) return CombatResult.unchanged(damage);
            return new CombatResult(weaponDamage(damage), java.util.List.of(), false);
        }

        @Override
        public CombatResult onTakeDamage(CombatEffectContext ctx, CombatEntity attacker, int damage) {
            if (!isAetherMob(attacker)) return CombatResult.unchanged(damage);
            return new CombatResult(armorDamage(damage, outsiderArmorPieces), java.util.List.of(), false);
        }
    }

    /** Valkyrie set: winged, so a blow that would throw you does not. */
    static final class Winged implements CombatEffectHandler {
        @Override
        public CombatResult onKnockback(CombatEffectContext ctx, CombatEntity source, int distance) {
            if (distance <= 0) return CombatResult.unchanged(distance);
            return CombatResult.modify(0, "§f✦ Valkyrie wings hold you in place.");
        }
    }

    /**
     * Shortens every knockback by a fixed number of tiles: Sentry Boots, the Valkyrie Cape,
     * the Golden Feather. In the Aether these cancel a fall or a Zephyr's shove; here both
     * of those are being moved against your will.
     */
    static final class Braced implements CombatEffectHandler {
        private final int tiles;
        private final String source;

        Braced(int tiles, String source) {
            this.tiles = tiles;
            this.source = source;
        }

        @Override
        public CombatResult onKnockback(CombatEffectContext ctx, CombatEntity attacker, int distance) {
            if (distance <= 0 || tiles <= 0) return CombatResult.unchanged(distance);
            int left = Math.max(0, distance - tiles);
            return CombatResult.modify(left, "§f✦ " + source + " steadies you.");
        }
    }

    /** Neptune set (Soaked) and Phoenix set (Burning): an effect that simply does not take. */
    static final class Ward implements CombatEffectHandler {
        private final CombatEffects.EffectType warded;
        private final String message;

        Ward(CombatEffects.EffectType warded, String message) {
            this.warded = warded;
            this.message = message;
        }

        @Override
        public CombatResult onEffectApplied(CombatEffectContext ctx, CombatEffects.EffectType effect, int turns) {
            if (effect != warded || turns <= 0) return CombatResult.unchanged(turns);
            return CombatResult.modify(0, message);
        }
    }

    /**
     * Ice Ring and Ice Pendant: in the Aether they freeze the lava under your feet. Here they
     * take a turn off any burn, one per piece worn, so two of them shrug off a short one.
     */
    static final class Chill implements CombatEffectHandler {
        private final int turnsOff;

        Chill(int turnsOff) {
            this.turnsOff = turnsOff;
        }

        @Override
        public CombatResult onEffectApplied(CombatEffectContext ctx, CombatEffects.EffectType effect, int turns) {
            boolean fire = effect == CombatEffects.EffectType.BURNING
                || effect == CombatEffects.EffectType.SOUL_BURNING;
            if (!fire || turns <= 0 || turnsOff <= 0) return CombatResult.unchanged(turns);
            int left = Math.max(0, turns - turnsOff);
            return CombatResult.modify(left, left == 0
                ? "§b✦ Your ice charms snuff the flames."
                : "§b✦ Your ice charms cool the burn.");
        }
    }

    /**
     * Shield of Repulsion: turns a shot back on whoever fired it, but only while you hold
     * still - the Aether's rule exactly. "Still" on a grid is a turn in which you did not
     * move, so the shield rewards planting your feet and punishes repositioning.
     *
     * <p>Half the time rather than always: on a turn-based grid, standing still costs far
     * less than it does in real time, and a certainty would make every archer harmless to
     * anyone willing to hold their ground.
     */
    static final class Repulsion implements CombatEffectHandler {
        static final double REFLECT_CHANCE = 0.5;

        /** Players who moved during their current or most recent turn. */
        private static final Set<UUID> MOVED = ConcurrentHashMap.newKeySet();

        @Override
        public void onTurnStart(CombatEffectContext ctx) {
            if (ctx.getPlayer() != null) MOVED.remove(ctx.getPlayer().getUuid());
        }

        @Override
        public void onMove(CombatEffectContext ctx, GridPos from, GridPos to, int distance) {
            if (ctx.getPlayer() != null && distance > 0) MOVED.add(ctx.getPlayer().getUuid());
        }

        @Override
        public void onCombatEnd(CombatEffectContext ctx) {
            if (ctx.getPlayer() != null) MOVED.remove(ctx.getPlayer().getUuid());
        }

        @Override
        public CombatResult onTakeDamage(CombatEffectContext ctx, CombatEntity attacker, int damage) {
            if (attacker == null || damage <= 0 || ctx.getPlayer() == null) {
                return CombatResult.unchanged(damage);
            }
            if (MOVED.contains(ctx.getPlayer().getUuid())) return CombatResult.unchanged(damage);
            // A shot, not a swing: the attacker has reach and is using it.
            GridPos me = ctx.getArena().getPlayerGridPos();
            boolean shot = attacker.getRange() > 1 && me != null
                && attacker.minChebyshevDistanceTo(me) > 1;
            if (!shot || Math.random() >= REFLECT_CHANCE) return CombatResult.unchanged(damage);
            int dealt = attacker.takeDamage(damage);
            return CombatResult.modify(0, "§d✦ Shield of Repulsion! The shot turns back on "
                + attacker.getDisplayName() + " for " + dealt + ".");
        }
    }
}
