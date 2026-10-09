package com.crackedgames.craftics.compat.aether;

import com.crackedgames.craftics.api.CombatEffectContext;
import com.crackedgames.craftics.api.CombatEffectHandler;
import com.crackedgames.craftics.api.CombatResult;
import com.crackedgames.craftics.combat.CombatEffects;
import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.core.GridPos;

import java.util.Map;
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

    /**
     * Gravitite armor: a chance that whatever lands a hit on you is sent Levitating.
     *
     * <p>Each piece is worth a quarter of the full set's chance, so a mixed set still does
     * something and the fourth piece is what makes it reliable.
     */
    static final class Updraft implements CombatEffectHandler {
        /** Chance with all four pieces worn. */
        static final double FULL_SET_CHANCE = 0.25;

        private final int pieces;

        Updraft(int pieces) {
            this.pieces = pieces;
        }

        static double chance(int pieces) {
            return FULL_SET_CHANCE * Math.max(0, Math.min(4, pieces)) / 4.0;
        }

        @Override
        public CombatResult onTakeDamage(CombatEffectContext ctx, CombatEntity attacker, int damage) {
            if (attacker == null || damage <= 0 || Math.random() >= chance(pieces)) {
                return CombatResult.unchanged(damage);
            }
            if (!AetherCompat.levitate(attacker)) return CombatResult.unchanged(damage);
            return CombatResult.modify(damage, "§d✦ Gravitite updraft! " + attacker.getDisplayName()
                + " is lifted off its feet, Levitating for " + AetherCompat.LEVITATION_TURNS + " turns.");
        }
    }

    /**
     * Zanite set: the plate hardens as it is hit. Zanite gets better with wear, and a fight
     * is where armor is worn: every blow that lands makes the next one deal 1 less, up to
     * {@value #MAX_REDUCTION}, until the fight is over.
     *
     * <p>Silent, like {@link Outsider}: it touches every hit, and a line per hit would bury
     * the fight. The set's tooltip says what it does.
     */
    static final class Hardened implements CombatEffectHandler {
        static final int MAX_REDUCTION = 3;

        /** Hits each player has taken so far this fight. */
        private static final Map<UUID, Integer> HITS = new ConcurrentHashMap<>();

        /** What a hit deals after {@code hitsTaken} earlier ones. Never below 1. */
        static int reduced(int damage, int hitsTaken) {
            if (damage <= 0) return damage;
            return Math.max(1, damage - Math.max(0, Math.min(MAX_REDUCTION, hitsTaken)));
        }

        @Override
        public void onCombatStart(CombatEffectContext ctx) {
            if (ctx.getPlayer() != null) HITS.remove(ctx.getPlayer().getUuid());
        }

        @Override
        public void onCombatEnd(CombatEffectContext ctx) {
            if (ctx.getPlayer() != null) HITS.remove(ctx.getPlayer().getUuid());
        }

        @Override
        public CombatResult onTakeDamage(CombatEffectContext ctx, CombatEntity attacker, int damage) {
            if (attacker == null || damage <= 0 || ctx.getPlayer() == null) {
                return CombatResult.unchanged(damage);
            }
            UUID id = ctx.getPlayer().getUuid();
            int hits = HITS.getOrDefault(id, 0);
            HITS.put(id, Math.min(MAX_REDUCTION, hits + 1));
            int dealt = reduced(damage, hits);
            if (dealt == damage) return CombatResult.unchanged(damage);
            return new CombatResult(dealt, java.util.List.of(), false);
        }
    }

    /**
     * Obsidian set: phoenix plate, cooled hard. The first hit to land on you each round
     * deals half; everything after it that round lands in full.
     */
    static final class Tempered implements CombatEffectHandler {
        /** Players already hit since their last turn began. */
        private static final Set<UUID> STRUCK = ConcurrentHashMap.newKeySet();

        /** Half, rounded up, so a hit of 1 is still a hit. */
        static int halved(int damage) {
            return damage <= 1 ? damage : (damage + 1) / 2;
        }

        @Override
        public void onTurnStart(CombatEffectContext ctx) {
            if (ctx.getPlayer() != null) STRUCK.remove(ctx.getPlayer().getUuid());
        }

        @Override
        public void onCombatEnd(CombatEffectContext ctx) {
            if (ctx.getPlayer() != null) STRUCK.remove(ctx.getPlayer().getUuid());
        }

        @Override
        public CombatResult onTakeDamage(CombatEffectContext ctx, CombatEntity attacker, int damage) {
            if (attacker == null || damage <= 1 || ctx.getPlayer() == null) {
                return CombatResult.unchanged(damage);
            }
            if (!STRUCK.add(ctx.getPlayer().getUuid())) return CombatResult.unchanged(damage);
            return CombatResult.modify(halved(damage), "§8✦ Obsidian plate takes the worst of it.");
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
     * The dyed capes: each one keeps a kind of trouble off its wearer a turn sooner. Red for
     * Weakness, Blue for Levitation, White for Poison, Yellow for a burn. They are plain wool
     * in the Aether and do nothing there. Here a cape is what it looks like, something
     * between you and the weather, and each colour is one thing the Aether throws at you.
     *
     * <p>A turn and no more. The Ice charms already stack against a burn, and the armor sets
     * that shut an effect out altogether are a boss's treasure, not a strip of wool.
     */
    static final class Shortened implements CombatEffectHandler {
        private final String cape;
        private final String trouble;
        private final Set<CombatEffects.EffectType> kinds;

        Shortened(String cape, String trouble, CombatEffects.EffectType... kinds) {
            this.cape = cape;
            this.trouble = trouble;
            this.kinds = Set.of(kinds);
        }

        /** What is left of {@code turns} once the cape has had its one. */
        static int left(int turns) {
            return Math.max(0, turns - 1);
        }

        boolean covers(CombatEffects.EffectType effect) {
            return kinds.contains(effect);
        }

        @Override
        public CombatResult onEffectApplied(CombatEffectContext ctx, CombatEffects.EffectType effect, int turns) {
            if (!kinds.contains(effect) || turns <= 0) return CombatResult.unchanged(turns);
            int left = left(turns);
            return CombatResult.modify(left, left == 0
                ? "§f✦ Your " + cape + " shrugs off the " + trouble + "."
                : "§f✦ Your " + cape + " takes a turn off the " + trouble + ".");
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
