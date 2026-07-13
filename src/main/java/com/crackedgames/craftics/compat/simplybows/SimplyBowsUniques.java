package com.crackedgames.craftics.compat.simplybows;

import com.crackedgames.craftics.CrafticsMod;
import com.crackedgames.craftics.api.TargetlessCastHandler;
import com.crackedgames.craftics.api.WeaponAbilityHandler;
import com.crackedgames.craftics.api.registry.WeaponEntry;
import com.crackedgames.craftics.api.registry.WeaponRegistry;
import com.crackedgames.craftics.combat.AoeShapes;
import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.CombatManager;
import com.crackedgames.craftics.combat.DamageType;
import com.crackedgames.craftics.combat.MobResistances;
import com.crackedgames.craftics.combat.ProjectileSpawner;
import com.crackedgames.craftics.combat.TileTrap;
import com.crackedgames.craftics.combat.WeaponAbility;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;
import net.minecraft.item.Item;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.IntSupplier;

/**
 * Craftics combat behavior for the seven Simply Bows uniques.
 *
 * <p>Each bow keeps the identity it has in Simply Bows, translated from that mod's
 * real-time archery into a turn-based grid. Everbloom still grows a field that mends
 * friends and saps foes; Echo still summons phantom bows that copy your shot. What
 * changes is only the shape of the effect, never its meaning.
 *
 * <p>Two of them - Everbloom and Bubbleveil - are aimed at the ground rather than at an
 * enemy, and one - Petalwind - rains across the whole arena. Those use a
 * {@link TargetlessCastHandler}, so clicking bare floor with them casts instead of being
 * refused. The rest hang their effect on a normal on-hit ability.
 *
 * <p>Registration is id-based with per-item null gating, so a bow missing from the
 * installed Simply Bows version is silently skipped.
 */
public final class SimplyBowsUniques {

    private SimplyBowsUniques() {}

    // === Tuning ===

    /**
     * Every point of the Luck stat adds this much to a proc chance. Craftics' universal
     * convention - the same +2% per point every other weapon ability uses.
     */
    public static final double LUCK_PER_POINT = 0.02;

    /** Fraction of the arena's tiles Petalwind's rain lands on. */
    public static final int ARROW_RAIN_TILE_DIVISOR = 4;
    /** Damage each rained arrow deals to whatever stands on its tile. */
    public static final int ARROW_RAIN_DAMAGE = 3;
    /** Extra arena tiles the rain covers per point of Luck, as a fraction of the arena. */
    public static final double ARROW_RAIN_LUCK_COVERAGE = 0.01;
    /** Chance per Luck point that a rained shaft strikes true for an extra point of damage. */
    public static final double ARROW_RAIN_LUCK_CRIT = LUCK_PER_POINT;

    /** Frost bolts Winterfang looses after a hit. */
    public static final int FROST_BOLT_COUNT = 3;
    /** Damage per frost bolt. */
    public static final int FROST_BOLT_DAMAGE = 2;
    /** Tiles a frost bolt can reach from the wielder. */
    public static final int FROST_BOLT_RANGE = 4;
    /** Chance per Luck point that the fan looses a fourth bolt. */
    public static final double FROST_BOLT_LUCK_EXTRA = LUCK_PER_POINT;

    /** Chance Buzzkill's arrow bursts into bees. */
    public static final double BEE_SWARM_CHANCE = 0.35;
    /** Chance the burst yields a second bee rather than one. */
    public static final double BEE_SECOND_CHANCE = 0.35;
    /** Rounds a summoned bee fights for. */
    public static final int BEE_LIFESPAN_ROUNDS = 3;

    /** Radius, in tiles, of Tremorstrike's shockwave around the struck enemy. */
    public static final int TREMOR_RADIUS = 2;
    /** Chance per Luck point that the tremor rolls out one tile further. */
    public static final double TREMOR_LUCK_WIDEN = LUCK_PER_POINT;

    /** Phantom bows Echo summons on every shot. */
    public static final int ECHO_BOW_COUNT = 2;
    /** Fraction of your damage each phantom bow deals. */
    public static final double ECHO_DAMAGE_MULT = 0.50;
    /** Tiles a phantom bow can reach. */
    public static final int ECHO_RANGE = 5;
    /** Chance per Luck point that a third phantom bow answers the shot. */
    public static final double ECHO_LUCK_EXTRA_BOW = LUCK_PER_POINT;

    /** Chance per Luck point that a freshly laid trap lingers an extra turn. */
    public static final double TRAP_LUCK_EXTRA_TURN = LUCK_PER_POINT;

    /** Paths of every unique registered this launch (drives the loot pool + tooltips). */
    private static final List<String> REGISTERED_PATHS = new ArrayList<>();

    public static List<String> registeredPaths() { return List.copyOf(REGISTERED_PATHS); }

    /** True if this item is one of the registered Simply Bows uniques. */
    public static boolean isUnique(Item item) {
        if (item == null) return false;
        Identifier id = Registries.ITEM.getId(item);
        return SimplyBowsCompat.NAMESPACE.equals(id.getNamespace())
            && REGISTERED_PATHS.contains(id.getPath());
    }

    /** Bow damage baseline: uniques sit just above the vanilla bow, read live from config. */
    private static IntSupplier bow(int offset) {
        return () -> Math.max(1, CrafticsMod.CONFIG.dmgBow() + offset);
    }

    // =========================================================================
    // Registration table
    // =========================================================================

    static boolean registerAll() {
        REGISTERED_PATHS.clear();
        boolean any = false;

        // Everbloom - the mod's flower field, which heals allies and harms enemies. Shoot
        // an enemy to grow the field around it, or aim at open ground to grow it there.
        any |= bowEntry("vine_bow/vine_bow", DamageType.RANGED, null, bow(0), 2, 4,
            everbloom(), everbloomCast());

        // Winterfang - the mod's fan of homing frost arrows.
        any |= bowEntry("ice_bow/ice_bow", DamageType.WATER, null, bow(0), 1, 3,
            winterfang(), null);

        // Bubbleveil - the mod's bubble columns. A hybrid of the water it carries and the
        // bow it is. Shoot an enemy to soak it and leave a column under it; or aim at open
        // ground to set a column there. Either way the column pops on the next contact,
        // so a shot that knocks an enemy around can drive it straight back into one.
        any |= bowEntry("bubble_bow/bubble_bow", DamageType.WATER, DamageType.RANGED, bow(0), 2, 4,
            bubbleveil(), bubbleveilCast());

        // Buzzkill - the mod's bees. Ranged bow that fights alongside the pets it looses.
        any |= bowEntry("bee_bow/bee_bow", DamageType.RANGED, DamageType.PET, bow(-1), 1, 3,
            buzzkill(), null);

        // Petalwind - the mod's blossom storm, scaled to the whole arena. Shoot an enemy
        // and the storm breaks over it as well; aim at open ground and it breaks there.
        any |= bowEntry("blossom_bow/blossom_bow", DamageType.RANGED, null, bow(-1), 2, 4,
            petalwind(), petalwindCast());

        // Tremorstrike - the mod's earth tremor and knock-up.
        any |= bowEntry("earth_bow/earth_bow", DamageType.BLUNT, null, bow(1), 1, 3,
            tremorstrike(), null);

        // Echo - the mod's two phantom bows that mimic your shots.
        any |= bowEntry("echo_bow/echo_bow", DamageType.SPECIAL, null, bow(0), 1, 4,
            echo(), null);

        return any;
    }

    private static boolean bowEntry(String path, DamageType dt, DamageType secondary, IntSupplier dmg,
                                    int ap, int range, WeaponAbilityHandler ability,
                                    TargetlessCastHandler cast) {
        Item item = SimplyBowsCompat.lookupItem(path);
        if (item == null) return false;
        WeaponEntry.Builder b = WeaponEntry.builder(item)
            .damageType(dt).attackPower(dmg).apCost(ap).range(range).ranged(true);
        if (secondary != null) b.secondaryDamageType(secondary);
        if (ability != null) b.ability(ability);
        if (cast != null) b.targetlessCast(cast);
        WeaponRegistry.register(item, b.build());
        REGISTERED_PATHS.add(path);
        return true;
    }

    // =========================================================================
    // Shared helpers
    // =========================================================================

    private static ServerWorld world(ServerPlayerEntity player) {
        return player.getEntityWorld() instanceof ServerWorld sw ? sw : null;
    }

    private static void sound(ServerWorld sw, BlockPos pos, String soundId, float pitch) {
        SoundEvent ev = Registries.SOUND_EVENT.get(Identifier.of("minecraft", soundId));
        if (ev != null) sw.playSound(null, pos, ev, SoundCategory.PLAYERS, 0.8f, pitch);
    }

    /** The live combat this player is fighting, or {@code null} outside of one. */
    private static CombatManager combat(ServerPlayerEntity player) {
        return CombatManager.getActiveCombat(player.getUuid());
    }

    /** Deal typed damage to an enemy, honoring that mob's resistances. */
    private static int hurt(CombatEntity enemy, int amount, DamageType type) {
        int adjusted = MobResistances.applyResistance(enemy.getEntityTypeId(), type, amount);
        return adjusted <= 0 ? 0 : enemy.takeDamage(adjusted);
    }

    /** Live, non-ally enemies within {@code range} tiles of {@code from}, nearest first. */
    private static List<CombatEntity> enemiesNear(GridArena arena, GridPos from, int range,
                                                  CombatEntity exclude) {
        List<CombatEntity> found = new ArrayList<>();
        for (CombatEntity e : arena.getOccupants().values()) {
            if (e == exclude || !e.isAlive() || e.isAlly()) continue;
            if (e.minDistanceTo(from) > range) continue;
            if (!found.contains(e)) found.add(e);
        }
        found.sort(Comparator.comparingInt(e -> e.minDistanceTo(from)));
        return found;
    }

    // =========================================================================
    // Ground-aimed casts
    // =========================================================================

    /**
     * Everbloom - Flower Field. Seeds a 3x3 patch of blossoms around {@code center}. For as
     * long as it lasts, enemies standing in it wither and stagger in the pollen, and the
     * party standing in it mends. The bow that plants a garden mid-battle.
     *
     * <p>Shared by the on-hit ability (centered on the enemy struck) and the ground-aimed
     * cast (centered on the tile clicked), so the field is the same either way.
     */
    /** Roll whether a lucky wielder's trap lingers an extra turn. */
    private static int luckyTrapBonus(int luckPoints) {
        return Math.random() < luckPoints * TRAP_LUCK_EXTRA_TURN ? 1 : 0;
    }

    private static List<String> growFlowerField(ServerPlayerEntity player, GridArena arena,
                                                GridPos center, int luckPoints) {
        CombatManager cm = combat(player);
        if (cm == null) return List.of("§cThe field will not take root here.");
        int bonus = luckyTrapBonus(luckPoints);
        int laid = cm.layTileTraps(AoeShapes.slam3x3(center), TileTrap.Kind.FLOWER_FIELD, player, bonus);
        if (laid == 0) return List.of("§cThere is nowhere for the field to grow.");

        ServerWorld sw = world(player);
        if (sw != null) {
            BlockPos bp = arena.gridToBlockPos(center);
            ProjectileSpawner.spawnExpandingRing(sw, bp, 1.6, ParticleTypes.HAPPY_VILLAGER, 24);
            sound(sw, bp, "block.grass.place", 1.2f);
        }
        List<String> msgs = new ArrayList<>();
        msgs.add("§a✿ Everbloom: §7a flower field takes root across " + laid + " tiles.");
        if (bonus > 0) msgs.add("§6✦ Lucky: §7the blossoms are slow to fade.");
        return msgs;
    }

    /** Set a single bubble column on {@code center}. Shared by the hit and the cast. */
    private static List<String> setBubbleColumn(ServerPlayerEntity player, GridArena arena,
                                                GridPos center, int luckPoints) {
        CombatManager cm = combat(player);
        if (cm == null) return List.of("§cThe column will not hold here.");
        int bonus = luckyTrapBonus(luckPoints);
        if (cm.layTileTraps(List.of(center), TileTrap.Kind.BUBBLE_COLUMN, player, bonus) == 0) {
            return List.of("§cThe column needs open ground.");
        }
        ServerWorld sw = world(player);
        if (sw != null) {
            BlockPos bp = arena.gridToBlockPos(center);
            sw.spawnParticles(ParticleTypes.BUBBLE_COLUMN_UP,
                bp.getX() + 0.5, bp.getY() + 0.5, bp.getZ() + 0.5, 30, 0.25, 0.6, 0.25, 0.05);
            sound(sw, bp, "entity.generic.splash", 0.8f);
        }
        List<String> msgs = new ArrayList<>();
        msgs.add("§b○ Bubbleveil: §7a bubble column churns on that tile.");
        if (bonus > 0) msgs.add("§6✦ Lucky: §7the column holds its shape longer.");
        return msgs;
    }

    /**
     * Petalwind - Blossom Storm. The arrow bursts overhead and the whole arena is raked:
     * a quarter of its tiles take a falling shaft. Anything standing on a struck tile is
     * hit. Bigger arenas mean more arrows, so the storm always fills the room.
     *
     * <p>{@code alwaysStrike} is the tile the shot itself found - the storm always includes
     * it, so shooting an enemy guarantees the blossoms break over that enemy too. Pass
     * {@code null} when the shot was aimed at bare ground.
     *
     * <p>Luck widens the storm - every point rakes another 1% of the arena - and gives each
     * shaft that finds a target a chance to bite an extra point deep. A lucky archer's rain
     * covers more ground and lands harder on what it covers.
     */
    private static List<String> rakeArenaWithArrows(ServerPlayerEntity player, GridArena arena,
                                                    GridPos alwaysStrike, int luckPoints) {
        List<GridPos> candidates = new ArrayList<>(AoeShapes.allTiles(arena));
        if (candidates.isEmpty()) return List.of("§cThere is no sky here.");
        double coverage = 1.0 / ARROW_RAIN_TILE_DIVISOR + luckPoints * ARROW_RAIN_LUCK_COVERAGE;
        int count = Math.max(1, (int) Math.round(candidates.size() * Math.min(1.0, coverage)));
        java.util.Collections.shuffle(candidates);
        List<GridPos> struck = new ArrayList<>(candidates.subList(0, Math.min(count, candidates.size())));
        if (alwaysStrike != null && !struck.contains(alwaysStrike)) struck.add(alwaysStrike);

        ServerWorld sw = world(player);
        CombatManager cm = combat(player);
        List<String> msgs = new ArrayList<>();
        msgs.add("§d❀ Petalwind: §7a blossom storm rakes " + struck.size() + " tiles!");

        int hits = 0;
        for (GridPos tile : struck) {
            if (sw != null) {
                ProjectileSpawner.spawnDescendingVolley(sw, arena.gridToBlockPos(tile), 1, 0.25);
            }
            CombatEntity occupant = arena.getOccupant(tile);
            if (occupant == null || !occupant.isAlive() || occupant.isAlly()) continue;
            boolean lucky = Math.random() < luckPoints * ARROW_RAIN_LUCK_CRIT;
            int dealt = hurt(occupant, ARROW_RAIN_DAMAGE + (lucky ? 1 : 0), DamageType.RANGED);
            if (dealt <= 0) continue;
            hits++;
            msgs.add("§d❀ A falling shaft strikes " + occupant.getDisplayName() + " for " + dealt + "!"
                + (lucky ? " §6✦ Lucky!" : ""));
            if (cm != null) cm.checkAndHandleDeathPublic(occupant);
        }
        if (sw != null) sound(sw, player.getBlockPos(), "entity.arrow.shoot", 0.7f);
        if (hits == 0) msgs.add("§7Every shaft buries itself in empty ground.");
        return msgs;
    }

    private static TargetlessCastHandler everbloomCast() {
        return (player, aimTile, arena, baseDamage, luckPoints) ->
            growFlowerField(player, arena, aimTile, luckPoints);
    }

    private static TargetlessCastHandler bubbleveilCast() {
        return (player, aimTile, arena, baseDamage, luckPoints) ->
            setBubbleColumn(player, arena, aimTile, luckPoints);
    }

    private static TargetlessCastHandler petalwindCast() {
        return (player, aimTile, arena, baseDamage, luckPoints) ->
            rakeArenaWithArrows(player, arena, null, luckPoints);
    }

    /** Everbloom's on-hit face: the field grows around the enemy the arrow found. */
    private static WeaponAbilityHandler everbloom() {
        return (player, target, arena, baseDamage, stats, luckPoints) -> new WeaponAbility.AttackResult(
            baseDamage,
            growFlowerField(player, arena, target.nearestTileTo(arena.getPlayerGridPos()), luckPoints),
            List.of());
    }

    /** Petalwind's on-hit face: the storm breaks over the struck enemy as well. */
    private static WeaponAbilityHandler petalwind() {
        return (player, target, arena, baseDamage, stats, luckPoints) -> new WeaponAbility.AttackResult(
            baseDamage,
            rakeArenaWithArrows(player, arena, target.nearestTileTo(arena.getPlayerGridPos()), luckPoints),
            List.of());
    }

    // =========================================================================
    // On-hit abilities
    // =========================================================================

    /**
     * Bubbleveil - Bubble Burst. The arrow soaks whatever it hits and leaves a column
     * churning on that enemy's own tile. The column does not pop under a standing enemy -
     * it only triggers on something walking into it - so anything that shoves the target
     * off and back again (a ricochet, a knockback, a bounce) drives it straight into the
     * column it is standing on.
     */
    private static WeaponAbilityHandler bubbleveil() {
        return (player, target, arena, baseDamage, stats, luckPoints) -> {
            List<String> msgs = new ArrayList<>();
            target.stackSoaked(TileTrap.BUBBLE_COLUMN_SOAK_TURNS, 1);
            msgs.add("§b○ Bubbleveil: §7" + target.getDisplayName() + " is drenched.");
            msgs.addAll(setBubbleColumn(player, arena,
                target.nearestTileTo(arena.getPlayerGridPos()), luckPoints));
            return new WeaponAbility.AttackResult(baseDamage, msgs, List.of());
        };
    }

    /**
     * Winterfang - Frost Fan. The hit fans out into homing bolts that seek whatever else
     * is near, chilling each of them. Against a lone enemy every bolt comes home to it.
     * Luck can coax a fourth bolt out of the fan.
     */
    private static WeaponAbilityHandler winterfang() {
        return (player, target, arena, baseDamage, stats, luckPoints) -> {
            List<String> msgs = new ArrayList<>();
            List<CombatEntity> extras = new ArrayList<>();
            int total = baseDamage;

            List<CombatEntity> others = enemiesNear(arena, arena.getPlayerGridPos(), FROST_BOLT_RANGE, target);
            ServerWorld sw = world(player);
            int bolts = FROST_BOLT_COUNT;
            if (Math.random() < luckPoints * FROST_BOLT_LUCK_EXTRA) {
                bolts++;
                msgs.add("§6✦ Lucky: §7the fan looses a fourth bolt.");
            }
            for (int i = 0; i < bolts; i++) {
                // Spread across nearby enemies; with nobody else around, every bolt homes
                // back onto the target the shot already found.
                CombatEntity victim = others.isEmpty() ? target : others.get(i % others.size());
                if (!victim.isAlive()) continue;
                int dealt = hurt(victim, FROST_BOLT_DAMAGE, DamageType.WATER);
                if (dealt <= 0) continue;
                victim.stackSlowness(2, 1);
                if (victim != target && !extras.contains(victim)) extras.add(victim);
                total += dealt;
                if (sw != null) {
                    ProjectileSpawner.spawnSpellTrail(sw, arena.gridToBlockPos(arena.getPlayerGridPos()),
                        arena.gridToBlockPos(victim.getGridPos()),
                        ParticleTypes.SNOWFLAKE, ParticleTypes.ITEM_SNOWBALL, 14, 0.25);
                }
                msgs.add("§b❄ Frost bolt strikes " + victim.getDisplayName()
                    + " for " + dealt + " and chills it!");
            }
            if (sw != null) sound(sw, player.getBlockPos(), "block.glass.break", 1.7f);
            return new WeaponAbility.AttackResult(total, msgs, extras);
        };
    }

    /**
     * Buzzkill - The Hive. The arrow poisons on contact, and sometimes bursts open: a bee
     * or two spills out and fights for you. Being both Ranged and Pet, it grows with the
     * archer who keeps a menagerie.
     */
    private static WeaponAbilityHandler buzzkill() {
        return (player, target, arena, baseDamage, stats, luckPoints) -> {
            List<String> msgs = new ArrayList<>();
            target.stackPoison(2, 0);
            msgs.add("§e🐝 Buzzkill: §7" + target.getDisplayName() + " is stung and poisoned.");

            if (Math.random() < BEE_SWARM_CHANCE + luckPoints * LUCK_PER_POINT) {
                CombatManager cm = combat(player);
                int summoned = 0;
                if (cm != null) {
                    // Luck makes the burst more likely to yield a second bee, not just
                    // more likely to happen at all.
                    boolean second = Math.random() < BEE_SECOND_CHANCE + luckPoints * LUCK_PER_POINT;
                    int wanted = 1 + (second ? 1 : 0);
                    for (int i = 0; i < wanted; i++) {
                        if (cm.summonWeaponProcAlly("minecraft:bee", player, BEE_LIFESPAN_ROUNDS) != null) {
                            summoned++;
                        }
                    }
                }
                if (summoned > 0) {
                    ServerWorld sw = world(player);
                    if (sw != null) {
                        sw.spawnParticles(ParticleTypes.FALLING_NECTAR,
                            player.getX(), player.getY() + 1.0, player.getZ(), 16, 0.4, 0.4, 0.4, 0.02);
                        sound(sw, player.getBlockPos(), "block.beehive.work", 1.4f);
                    }
                    msgs.add("§e🐝 THE HIVE SPILLS! §7" + summoned + " bee"
                        + (summoned == 1 ? "" : "s") + " joins the fight.");
                }
            }
            return new WeaponAbility.AttackResult(baseDamage, msgs, List.of());
        };
    }

    /**
     * Tremorstrike - Tremor. The arrow buries itself and the ground answers. Everything
     * around the struck enemy is thrown off its feet - the mod's knock-up, read here as a
     * turn spent picking yourself up.
     */
    private static WeaponAbilityHandler tremorstrike() {
        return (player, target, arena, baseDamage, stats, luckPoints) -> {
            List<String> msgs = new ArrayList<>();
            List<CombatEntity> extras = new ArrayList<>();
            int total = baseDamage;

            target.setStunned(true);
            msgs.add("§6⛰ Tremorstrike: §7the ground bucks beneath " + target.getDisplayName() + "!");

            // Luck rolls the tremor one tile further out.
            int radius = TREMOR_RADIUS;
            if (Math.random() < luckPoints * TREMOR_LUCK_WIDEN) {
                radius++;
                msgs.add("§6✦ Lucky: §7the tremor rolls further than it should.");
            }

            ServerWorld sw = world(player);
            if (sw != null) {
                ProjectileSpawner.spawnExpandingRing(sw, arena.gridToBlockPos(target.getGridPos()),
                    radius + 0.4, ParticleTypes.CLOUD, 28);
                sound(sw, arena.gridToBlockPos(target.getGridPos()), "entity.generic.explode", 0.6f);
            }
            for (CombatEntity e : AoeShapes.enemiesOn(arena,
                    AoeShapes.filledDisc(target.getGridPos(), radius), target)) {
                int dealt = hurt(e, Math.max(1, baseDamage / 2), DamageType.BLUNT);
                if (dealt <= 0) continue;
                e.setStunned(true);
                extras.add(e);
                total += dealt;
                msgs.add("§6⛰ The tremor throws " + e.getDisplayName()
                    + " down for " + dealt + "!");
            }
            return new WeaponAbility.AttackResult(total, msgs, extras);
        };
    }

    /**
     * Echo - Echoing Volley. Two phantom bows hang at your shoulders and loose whenever
     * you do, each picking its own mark. They copy the shot, not the shooter, so they
     * deal half of whatever you dealt.
     */
    private static WeaponAbilityHandler echo() {
        return (player, target, arena, baseDamage, stats, luckPoints) -> {
            List<String> msgs = new ArrayList<>();
            List<CombatEntity> extras = new ArrayList<>();
            int total = baseDamage;

            List<CombatEntity> marks = enemiesNear(arena, arena.getPlayerGridPos(), ECHO_RANGE, target);
            if (marks.isEmpty()) {
                return new WeaponAbility.AttackResult(total,
                    List.of("§5⟳ Echo: §7the phantom bows find nothing else to shoot."), List.of());
            }

            ServerWorld sw = world(player);
            int echoDamage = Math.max(1, (int) Math.round(baseDamage * ECHO_DAMAGE_MULT));
            // Luck can conjure a third bow out of the echo.
            int bows = ECHO_BOW_COUNT;
            if (Math.random() < luckPoints * ECHO_LUCK_EXTRA_BOW) {
                bows++;
                msgs.add("§6✦ Lucky: §7a third phantom bow answers.");
            }
            int fired = 0;
            for (CombatEntity mark : marks) {
                if (fired >= bows) break;
                if (!mark.isAlive()) continue;
                int dealt = hurt(mark, echoDamage, DamageType.SPECIAL);
                if (dealt <= 0) continue;
                extras.add(mark);
                total += dealt;
                fired++;
                if (sw != null) {
                    ProjectileSpawner.spawnSpellTrail(sw, arena.gridToBlockPos(arena.getPlayerGridPos()),
                        arena.gridToBlockPos(mark.getGridPos()),
                        ParticleTypes.SCULK_SOUL, ParticleTypes.SONIC_BOOM, 16, 0.2);
                }
                msgs.add("§5⟳ A phantom bow looses at " + mark.getDisplayName()
                    + " for " + dealt + "!");
            }
            if (fired > 0 && sw != null) sound(sw, player.getBlockPos(), "block.sculk_sensor.clicking", 1.3f);
            return new WeaponAbility.AttackResult(total, msgs, extras);
        };
    }
}
