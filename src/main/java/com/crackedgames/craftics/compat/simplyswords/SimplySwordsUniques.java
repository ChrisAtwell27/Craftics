package com.crackedgames.craftics.compat.simplyswords;

import com.crackedgames.craftics.CrafticsMod;
import com.crackedgames.craftics.api.Abilities;
import com.crackedgames.craftics.api.WeaponAbilityHandler;
import com.crackedgames.craftics.api.registry.WeaponEntry;
import com.crackedgames.craftics.api.registry.WeaponRegistry;
import com.crackedgames.craftics.combat.AoeShapes;
import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.DamageType;
import com.crackedgames.craftics.combat.PlayerProgression;
import com.crackedgames.craftics.combat.ProjectileSpawner;
import com.crackedgames.craftics.combat.WeaponAbility;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;
import net.minecraft.item.Item;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntSupplier;

/**
 * Craftics combat behavior for Simply Swords UNIQUE weapons - the rare boss drops.
 * Each unique carries its own damage type (affinity), signature special effect, and
 * a particle/sound "animation" that plays when the effect procs.
 *
 * <p>Registration is id-based with per-item null gating, so any unique missing from
 * the installed Simply Swords version is silently skipped.
 */
public final class SimplySwordsUniques {

    private SimplySwordsUniques() {}

    /** Paths of every unique registered this launch (drives the boss loot pool + tooltips). */
    private static final List<String> REGISTERED_PATHS = new ArrayList<>();

    public static List<String> registeredPaths() { return List.copyOf(REGISTERED_PATHS); }

    /** True if this item is one of the registered Simply Swords uniques. */
    public static boolean isUnique(Item item) {
        if (item == null) return false;
        Identifier id = Registries.ITEM.getId(item);
        return SimplySwordsCompat.NAMESPACE.equals(id.getNamespace())
            && REGISTERED_PATHS.contains(id.getPath());
    }

    // Damage baselines: uniques sit at/above netherite, read live from config.
    private static IntSupplier sword(int offset) {
        return () -> CrafticsMod.CONFIG.dmgNetheriteSword() + offset;
    }

    private static IntSupplier axe(int offset) {
        return () -> CrafticsMod.CONFIG.dmgNetheriteAxe() + offset;
    }

    // =========================================================================
    // Registration table
    // =========================================================================

    static boolean registerAll() {
        REGISTERED_PATHS.clear();
        boolean any = false;

        // ── Fire ──
        any |= u("emberblade", DamageType.SLASHING, sword(1), 1, 1, emberblade());
        any |= u("molten_edge", DamageType.SLASHING, sword(1), 1, 1, moltenEdge());
        any |= u("hearthflame", DamageType.BLUNT, axe(2), 3, 1, hearthflame());
        any |= u("brimstone_claymore", DamageType.CLEAVING, axe(2), 3, 1, brimstone());
        any |= u("sunfire", DamageType.SPECIAL, sword(0), 1, 1, sunfire());
        any |= u("wickpiercer", DamageType.SLASHING, sword(0), 1, 1, wickpiercer());
        any |= u("flamewind", DamageType.SLASHING, sword(0), 1, 1, flamewind());
        any |= u("emberlash", DamageType.SLASHING, sword(-1), 1, 2, emberlash());
        any |= u("soulpyre", DamageType.SPECIAL, sword(0), 1, 1, lifestealBlade(0.25, "Soul Pyre",
            ParticleTypes.SOUL_FIRE_FLAME, "particle.soul_escape"));

        // ── Ice & Water ──
        any |= u("frostfall", DamageType.WATER, sword(1), 1, 1, frostfall());
        any |= u("icewhisper", DamageType.WATER, sword(0), 1, 1, icewhisper());
        any |= u("dreadtide", DamageType.WATER, sword(1), 1, 1, dreadtide());
        any |= u("livyatan", DamageType.BLUNT, axe(2), 3, 1, livyatan());
        any |= u("chompolotl", DamageType.WATER, sword(0), 1, 1, chompolotl());

        // ── Storm ──
        any |= u("mjolnir", DamageType.BLUNT, axe(2), 2, 1, mjolnir());
        any |= u("stormbringer", DamageType.SPECIAL, sword(1), 1, 1, stormbringer());
        any |= u("thunderbrand", DamageType.SLASHING, sword(0), 1, 1, thunderbrand());
        any |= u("storms_edge", DamageType.SLASHING, sword(0), 1, 1, stormsEdge());
        any |= u("tempest", DamageType.SPECIAL, sword(0), 1, 1, tempest());
        any |= u("whisperwind", DamageType.SLASHING, sword(-1), 1, 1, whisperwind());

        // ── Nature & Toxin ──
        any |= u("toxic_longsword", DamageType.SLASHING, sword(0), 1, 1, plagueLongsword());
        any |= u("bramblethorn", DamageType.CLEAVING, axe(1), 2, 1, bramblethorn());
        any |= u("hiveheart", DamageType.PET, sword(-1), 1, 1, hiveheart());
        any |= u("waxweaver", DamageType.BLUNT, sword(0), 1, 1, waxweaver());

        // ── Soul & Shadow ──
        any |= u("shadowsting", DamageType.SLASHING, sword(0), 1, 1, shadowsting());
        any |= u("wraithfang", DamageType.SPECIAL, sword(0), 1, 1, wraithfang());
        any |= u("soulrender", DamageType.CLEAVING, axe(1), 2, 1, soulrender());
        any |= u("soulstealer", DamageType.SPECIAL, sword(0), 1, 1, soulstealer());
        any |= u("soulkeeper", DamageType.SPECIAL, sword(0), 1, 1, lifestealBlade(0.20, "Keeper's Pact",
            ParticleTypes.SOUL, "particle.soul_escape"));
        any |= u("slumbering_lichblade", DamageType.SPECIAL, sword(0), 1, 1, lifestealBlade(0.15,
            "Slumbering Soul", ParticleTypes.SOUL, "particle.soul_escape"));
        any |= u("waking_lichblade", DamageType.SPECIAL, sword(1), 1, 1, wakingLichblade());
        any |= u("awakened_lichblade", DamageType.SPECIAL, sword(2), 1, 1, awakenedLichblade());

        // ── Arcane ──
        any |= u("arcanethyst", DamageType.SPECIAL, sword(0), 1, 1, arcanethyst());
        any |= u("magiblade", DamageType.SPECIAL, sword(0), 1, 1, magiblade());
        any |= u("magic_estoc", DamageType.SPECIAL, sword(0), 1, 1, magicEstoc());
        any |= u("magiscythe", DamageType.SPECIAL, axe(1), 2, 1, magiscythe());
        any |= u("magispear", DamageType.SPECIAL, sword(-1), 1, 2, magispear());
        any |= u("enigma", DamageType.SPECIAL, sword(0), 1, 1, enigma());
        any |= u("twilight", DamageType.SPECIAL, sword(1), 1, 1, twilight());
        any |= u("stars_edge", DamageType.SPECIAL, sword(1), 1, 1, starsEdge());
        any |= u("caelestis", DamageType.SPECIAL, sword(1), 1, 1, caelestis());

        // ── Sculk ──
        any |= u("watcher_claymore", DamageType.CLEAVING, axe(2), 3, 1, watcherClaymore());
        any |= u("watching_warglaive", DamageType.CLEAVING, sword(0), 1, 1, watchingWarglaive());

        // ── Curios ──
        any |= u("ribboncleaver", DamageType.CLEAVING, sword(0), 1, 1, ribboncleaver());
        any |= u("twisted_blade", DamageType.SLASHING, sword(0), 1, 1, twistedBlade());
        any |= u("harbinger", DamageType.BLUNT, axe(2), 3, 1, harbinger());
        any |= u("sword_on_a_stick", DamageType.PHYSICAL, sword(-2), 1, 2, swordOnAStick());

        return any;
    }

    private static boolean u(String path, DamageType dt, IntSupplier dmg, int ap, int range,
                             WeaponAbilityHandler ability) {
        Item item = SimplySwordsCompat.lookupItem(path);
        if (item == null) return false;
        WeaponRegistry.register(item, WeaponEntry.builder(item)
            .damageType(dt).attackPower(dmg).apCost(ap).range(range).ability(ability).build());
        REGISTERED_PATHS.add(path);
        return true;
    }

    // =========================================================================
    // VFX helpers ("animations": particles + sound on proc)
    // =========================================================================

    private static ServerWorld world(ServerPlayerEntity player) {
        return player.getEntityWorld() instanceof ServerWorld sw ? sw : null;
    }

    private static void sound(ServerWorld sw, BlockPos pos, String soundId, float pitch) {
        SoundEvent ev = Registries.SOUND_EVENT.get(Identifier.of("minecraft", soundId));
        if (ev != null) {
            sw.playSound(null, pos, ev, SoundCategory.PLAYERS, 0.8f, pitch);
        }
    }

    /** Particle burst on the target tile + optional sound. */
    private static void fxBurst(ServerPlayerEntity player, GridArena arena, CombatEntity target,
                                ParticleEffect particle, int count, String soundId, float pitch) {
        ServerWorld sw = world(player);
        if (sw == null) return;
        BlockPos bp = arena.gridToBlockPos(target.getGridPos());
        sw.spawnParticles(particle, bp.getX() + 0.5, bp.getY() + 1.2, bp.getZ() + 0.5,
            count, 0.35, 0.5, 0.35, 0.03);
        if (soundId != null) sound(sw, bp, soundId, pitch);
    }

    /** Arced particle trail from the player to the target + optional sound at impact. */
    private static void fxTrail(ServerPlayerEntity player, GridArena arena, CombatEntity target,
                                ParticleEffect primary, ParticleEffect secondary,
                                String soundId, float pitch) {
        ServerWorld sw = world(player);
        if (sw == null) return;
        BlockPos from = arena.gridToBlockPos(arena.getPlayerGridPos());
        BlockPos to = arena.gridToBlockPos(target.getGridPos());
        ProjectileSpawner.spawnSpellTrail(sw, from, to, primary, secondary, 18, 0.35);
        if (soundId != null) sound(sw, to, soundId, pitch);
    }

    /** Expanding particle ring centered on the target + optional sound. */
    private static void fxRing(ServerPlayerEntity player, GridArena arena, CombatEntity target,
                               double radius, ParticleEffect particle, String soundId, float pitch) {
        ServerWorld sw = world(player);
        if (sw == null) return;
        BlockPos bp = arena.gridToBlockPos(target.getGridPos());
        ProjectileSpawner.spawnExpandingRing(sw, bp, radius, particle, 24);
        if (soundId != null) sound(sw, bp, soundId, pitch);
    }

    /** Heal the attacker for a fraction of the damage dealt (min 1 when damage landed). */
    private static int lifesteal(ServerPlayerEntity player, int damageDealt, double fraction) {
        if (damageDealt <= 0) return 0;
        int heal = Math.max(1, (int) Math.round(damageDealt * fraction));
        float before = player.getHealth();
        player.setHealth(Math.min(player.getMaxHealth(), before + heal));
        return (int) (player.getHealth() - before);
    }

    /** Pull the target one tile toward the player if the tile is free and walkable. */
    private static boolean pullTowardPlayer(GridArena arena, CombatEntity target) {
        GridPos pPos = arena.getPlayerGridPos();
        GridPos tPos = target.getGridPos();
        int dx = Integer.signum(pPos.x() - tPos.x());
        int dz = Integer.signum(pPos.z() - tPos.z());
        if (dx == 0 && dz == 0) return false;
        GridPos next = new GridPos(tPos.x() + dx, tPos.z() + dz);
        if (!arena.isInBounds(next) || arena.isOccupied(next)) return false;
        var tile = arena.getTile(next);
        if (tile == null || !tile.isWalkable()) return false;
        arena.moveEntity(target, next);
        if (target.getMobEntity() != null) {
            BlockPos bp = arena.gridToBlockPos(next);
            target.getMobEntity().requestTeleport(bp.getX() + 0.5, bp.getY(), bp.getZ() + 0.5);
        }
        return true;
    }

    private static int affinityPts(PlayerProgression.PlayerStats stats, PlayerProgression.Affinity aff) {
        return stats != null ? stats.getAffinityPoints(aff) : 0;
    }

    /** Shared lifesteal-blade shape used by the soul-themed swords. */
    private static WeaponAbilityHandler lifestealBlade(double fraction, String procName,
                                                       ParticleEffect particle, String soundId) {
        return (player, target, arena, baseDamage, stats, luckPoints) -> {
            List<String> msgs = new ArrayList<>();
            int healed = lifesteal(player, baseDamage, fraction);
            if (healed > 0) {
                fxBurst(player, arena, target, particle, 10, soundId, 1.2f);
                msgs.add("§d✦ " + procName + ": §adrained " + healed + " HP.");
            }
            return new WeaponAbility.AttackResult(baseDamage, msgs, List.of());
        };
    }

    // =========================================================================
    // Fire
    // =========================================================================

    /** Emberblade - Ember Ire: every hit ignites the target. */
    private static WeaponAbilityHandler emberblade() {
        return (player, target, arena, baseDamage, stats, luckPoints) -> {
            target.stackBurning(2, 0);
            fxBurst(player, arena, target, ParticleTypes.FLAME, 14, "item.firecharge.use", 1.1f);
            return new WeaponAbility.AttackResult(baseDamage,
                List.of("§6✦ Ember Ire: §7" + target.getDisplayName() + " burns for 2 turns."), List.of());
        };
    }

    /** Molten Edge - Molten Roar: chance to erupt, burning every enemy adjacent to the target. */
    private static WeaponAbilityHandler moltenEdge() {
        return (player, target, arena, baseDamage, stats, luckPoints) -> {
            List<String> msgs = new ArrayList<>();
            List<CombatEntity> extras = new ArrayList<>();
            int total = baseDamage;
            if (Math.random() < 0.25 + luckPoints * 0.02) {
                fxRing(player, arena, target, 1.6, ParticleTypes.LAVA, "entity.blaze.shoot", 0.8f);
                target.stackBurning(2, 0);
                msgs.add("§c✦ MOLTEN ROAR! §7Lava erupts around " + target.getDisplayName() + "!");
                for (CombatEntity e : Abilities.findAdjacentEnemies(arena, target, 8)) {
                    int dmg = e.takeDamage(Math.max(1, baseDamage / 2));
                    e.stackBurning(2, 0);
                    extras.add(e);
                    total += dmg;
                    msgs.add("§6✦ Molten splash hits " + e.getDisplayName() + " for " + dmg + "!");
                }
            }
            return new WeaponAbility.AttackResult(total, msgs, extras);
        };
    }

    /** Hearthflame - Cinder Slam: mace-style 3x3 slam that leaves everything smoldering. */
    private static WeaponAbilityHandler hearthflame() {
        return (player, target, arena, baseDamage, stats, luckPoints) -> {
            List<String> msgs = new ArrayList<>();
            List<CombatEntity> extras = new ArrayList<>();
            int total = baseDamage;
            target.stackBurning(1, 0);
            fxRing(player, arena, target, 1.5, ParticleTypes.FLAME, "entity.generic.explode", 0.7f);
            msgs.add("§6✦ Cinder Slam! §7Embers scatter from the impact.");
            GridPos tPos = target.getGridPos();
            for (CombatEntity e : arena.getOccupants().values()) {
                if (e == target || !e.isAlive() || e.isAlly()) continue;
                if (tPos.manhattanDistance(e.getGridPos()) <= 1) {
                    int dmg = e.takeDamage(Math.max(1, baseDamage / 2));
                    e.stackBurning(1, 0);
                    extras.add(e);
                    total += dmg;
                    msgs.add("§6✦ Cinders hit " + e.getDisplayName() + " for " + dmg + "!");
                }
            }
            return new WeaponAbility.AttackResult(total, msgs, extras);
        };
    }

    /** Brimstone Claymore - Brimstone Cleave: wide arc, everything caught catches fire. */
    private static WeaponAbilityHandler brimstone() {
        return (player, target, arena, baseDamage, stats, luckPoints) -> {
            List<String> msgs = new ArrayList<>();
            List<CombatEntity> extras = new ArrayList<>();
            int total = baseDamage;
            target.stackBurning(2, 0);
            fxBurst(player, arena, target, ParticleTypes.FLAME, 16, "entity.blaze.shoot", 0.9f);
            msgs.add("§c✦ Brimstone Cleave: §7the arc blazes.");
            List<GridPos> tiles = AoeShapes.sweepingEdge(arena.getPlayerGridPos(), target.getGridPos(), 2);
            for (CombatEntity e : AoeShapes.enemiesOn(arena, tiles, target)) {
                int dmg = e.takeDamage(baseDamage / 2);
                e.stackBurning(2, 0);
                extras.add(e);
                total += dmg;
                msgs.add("§6✦ Brimstone burns " + e.getDisplayName() + " for " + dmg + "!");
            }
            return new WeaponAbility.AttackResult(total, msgs, extras);
        };
    }

    /** Sunfire - Solar Flare: chance for a searing burst of bonus damage that dazzles the target. */
    private static WeaponAbilityHandler sunfire() {
        return (player, target, arena, baseDamage, stats, luckPoints) -> {
            List<String> msgs = new ArrayList<>();
            int total = baseDamage;
            if (Math.random() < 0.30 + luckPoints * 0.02) {
                int bonus = target.takeDamage(3);
                target.setAttackPenalty(target.getAttackPenalty() + 1);
                total += bonus;
                fxBurst(player, arena, target, ParticleTypes.END_ROD, 20, "entity.guardian.attack", 1.6f);
                msgs.add("§e✦ SOLAR FLARE! §7+" + bonus + " searing damage, target dazzled (-1 ATK).");
            }
            return new WeaponAbility.AttackResult(total, msgs, List.of());
        };
    }

    /** Wickpiercer - Candlelight: ambush bonus against unhurt prey, and the wick catches. */
    private static WeaponAbilityHandler wickpiercer() {
        return (player, target, arena, baseDamage, stats, luckPoints) -> {
            List<String> msgs = new ArrayList<>();
            int total = baseDamage;
            if (target.getCurrentHp() >= target.getMaxHp()) {
                int bonus = target.takeDamage(3);
                total += bonus;
                target.stackBurning(1, 0);
                fxBurst(player, arena, target, ParticleTypes.SMALL_FLAME, 12, "block.candle.extinguish", 0.8f);
                msgs.add("§6✦ Candlelight: §7first blood! +" + bonus + " and the wick ignites.");
            }
            return new WeaponAbility.AttackResult(total, msgs, List.of());
        };
    }

    /** Flamewind - Flame Dash: bleeding cuts with a chance to trail fire. */
    private static WeaponAbilityHandler flamewind() {
        return (player, target, arena, baseDamage, stats, luckPoints) -> {
            List<String> msgs = new ArrayList<>();
            if (Math.random() < 0.40 + luckPoints * 0.02) {
                target.stackBleed(1);
                target.stackBurning(1, 0);
                fxTrail(player, arena, target, ParticleTypes.FLAME, ParticleTypes.CRIT,
                    "entity.blaze.burn", 1.4f);
                msgs.add("§c✦ Flame Dash: §7" + target.getDisplayName() + " bleeds and burns.");
            }
            return new WeaponAbility.AttackResult(baseDamage, msgs, List.of());
        };
    }

    /** Emberlash - a reach-2 burning whip that can drag enemies in. */
    private static WeaponAbilityHandler emberlash() {
        return (player, target, arena, baseDamage, stats, luckPoints) -> {
            List<String> msgs = new ArrayList<>();
            target.stackBurning(1, 0);
            if (Math.random() < 0.25 + luckPoints * 0.02 && pullTowardPlayer(arena, target)) {
                msgs.add("§6✦ Emberlash: §7the burning whip drags " + target.getDisplayName() + " closer!");
            }
            fxTrail(player, arena, target, ParticleTypes.FLAME, ParticleTypes.SMOKE,
                "entity.fishing_bobber.retrieve", 0.7f);
            return new WeaponAbility.AttackResult(baseDamage, msgs, List.of());
        };
    }

    // =========================================================================
    // Ice & Water
    // =========================================================================

    /** Frostfall - Frost Fury: soaks and slows; sometimes flash-freezes solid. */
    private static WeaponAbilityHandler frostfall() {
        return (player, target, arena, baseDamage, stats, luckPoints) -> {
            List<String> msgs = new ArrayList<>();
            target.stackSoaked(2, 1);
            target.stackSlowness(2, 1);
            msgs.add("§b✦ Frost Fury: §7" + target.getDisplayName() + " is chilled and slowed.");
            if (Math.random() < 0.20 + luckPoints * 0.02) {
                target.setStunned(true);
                fxBurst(player, arena, target, ParticleTypes.SNOWFLAKE, 24, "block.glass.break", 1.3f);
                msgs.add("§b✦ FLASH FREEZE! §7" + target.getDisplayName() + " is frozen solid for a turn!");
            } else {
                fxBurst(player, arena, target, ParticleTypes.SNOWFLAKE, 12, "block.powder_snow.hit", 0.9f);
            }
            return new WeaponAbility.AttackResult(baseDamage, msgs, List.of());
        };
    }

    /** Icewhisper - Cold Snap: quiet cuts of creeping cold. */
    private static WeaponAbilityHandler icewhisper() {
        return (player, target, arena, baseDamage, stats, luckPoints) -> {
            List<String> msgs = new ArrayList<>();
            int total = baseDamage;
            if (Math.random() < 0.40 + luckPoints * 0.02) {
                target.stackSlowness(2, 1);
                msgs.add("§b✦ Cold Snap: §7" + target.getDisplayName() + " slows.");
            }
            if (Math.random() < 0.25) {
                int bonus = target.takeDamage(2);
                total += bonus;
                fxBurst(player, arena, target, ParticleTypes.SNOWFLAKE, 10, "block.amethyst_block.chime", 0.6f);
                msgs.add("§b✦ Ice crystallizes for +" + bonus + " damage.");
            }
            return new WeaponAbility.AttackResult(total, msgs, List.of());
        };
    }

    /** Dreadtide - Riptide Pull: the tide soaks the target and can drag it into reach. */
    private static WeaponAbilityHandler dreadtide() {
        return (player, target, arena, baseDamage, stats, luckPoints) -> {
            List<String> msgs = new ArrayList<>();
            target.stackSoaked(2, 1);
            msgs.add("§3✦ Dreadtide: §7" + target.getDisplayName() + " is drenched.");
            if (Math.random() < 0.25 + luckPoints * 0.02 && pullTowardPlayer(arena, target)) {
                fxTrail(player, arena, target, ParticleTypes.SPLASH, ParticleTypes.BUBBLE,
                    "entity.player.splash", 0.7f);
                msgs.add("§3✦ RIPTIDE! §7The undertow drags " + target.getDisplayName() + " toward you!");
            } else {
                fxBurst(player, arena, target, ParticleTypes.SPLASH, 14, "entity.generic.splash", 1.0f);
            }
            return new WeaponAbility.AttackResult(baseDamage, msgs, List.of());
        };
    }

    /** Livyatan - Depth Charge: a whale-bone slam that swamps the whole area. */
    private static WeaponAbilityHandler livyatan() {
        return (player, target, arena, baseDamage, stats, luckPoints) -> {
            List<String> msgs = new ArrayList<>();
            List<CombatEntity> extras = new ArrayList<>();
            int total = baseDamage;
            target.stackSoaked(2, 1);
            fxRing(player, arena, target, 1.6, ParticleTypes.SPLASH, "entity.generic.splash", 0.6f);
            msgs.add("§3✦ Depth Charge! §7A wall of water crashes outward.");
            GridPos tPos = target.getGridPos();
            for (CombatEntity e : arena.getOccupants().values()) {
                if (e == target || !e.isAlive() || e.isAlly()) continue;
                if (tPos.manhattanDistance(e.getGridPos()) <= 1) {
                    int dmg = e.takeDamage(Math.max(1, baseDamage / 2));
                    e.stackSoaked(2, 1);
                    extras.add(e);
                    total += dmg;
                    msgs.add("§3✦ The wave hits " + e.getDisplayName() + " for " + dmg + "!");
                }
            }
            return new WeaponAbility.AttackResult(total, msgs, extras);
        };
    }

    /** Chomp'olotl - Chomp: bites feed the axolotl spirit, which feeds you. */
    private static WeaponAbilityHandler chompolotl() {
        return (player, target, arena, baseDamage, stats, luckPoints) -> {
            List<String> msgs = new ArrayList<>();
            if (Math.random() < 0.30 + luckPoints * 0.02) {
                target.stackSoaked(1, 1);
                int healed = lifesteal(player, Math.max(baseDamage, 4), 0.5);
                fxBurst(player, arena, target, ParticleTypes.HEART, 4, "entity.axolotl.attack", 1.0f);
                fxBurst(player, arena, target, ParticleTypes.BUBBLE, 10, null, 1.0f);
                msgs.add("§d✦ CHOMP! §aThe axolotl spirit shares its meal: +" + healed + " HP.");
            }
            return new WeaponAbility.AttackResult(baseDamage, msgs, List.of());
        };
    }

    // =========================================================================
    // Storm
    // =========================================================================

    /** Mjolnir - Thunderstrike: chance to call lightning down on the whole area. */
    private static WeaponAbilityHandler mjolnir() {
        return (player, target, arena, baseDamage, stats, luckPoints) -> {
            List<String> msgs = new ArrayList<>();
            List<CombatEntity> extras = new ArrayList<>();
            int total = baseDamage;
            if (Math.random() < 0.30 + luckPoints * 0.02) {
                ServerWorld sw = world(player);
                if (sw != null) {
                    BlockPos bp = arena.gridToBlockPos(target.getGridPos());
                    ProjectileSpawner.spawnImpact(sw, bp, "lightning");
                    sound(sw, bp, "entity.lightning_bolt.thunder", 1.4f);
                }
                int bonus = target.takeDamage(4);
                total += bonus;
                msgs.add("§e✦ THUNDERSTRIKE! §7Lightning courses through " + target.getDisplayName()
                    + " for +" + bonus + "!");
                GridPos tPos = target.getGridPos();
                for (CombatEntity e : arena.getOccupants().values()) {
                    if (e == target || !e.isAlive() || e.isAlly()) continue;
                    if (tPos.manhattanDistance(e.getGridPos()) <= 1) {
                        int dmg = e.takeDamage(Math.max(1, baseDamage / 2));
                        extras.add(e);
                        total += dmg;
                        msgs.add("§e✦ The shockwave arcs to " + e.getDisplayName() + " for " + dmg + "!");
                    }
                }
            }
            return new WeaponAbility.AttackResult(total, msgs, extras);
        };
    }

    /** Stormbringer - Storm Surge: chance to chain lightning to the two nearest enemies. */
    private static WeaponAbilityHandler stormbringer() {
        return (player, target, arena, baseDamage, stats, luckPoints) -> {
            List<String> msgs = new ArrayList<>();
            List<CombatEntity> extras = new ArrayList<>();
            int total = baseDamage;
            if (Math.random() < 0.25 + luckPoints * 0.02) {
                List<CombatEntity> chained = new ArrayList<>();
                for (CombatEntity e : arena.getOccupants().values()) {
                    if (e == target || !e.isAlive() || e.isAlly()) continue;
                    if (e.getGridPos().manhattanDistance(target.getGridPos()) <= 3) chained.add(e);
                }
                chained.sort(java.util.Comparator.comparingInt(
                    e -> e.getGridPos().manhattanDistance(target.getGridPos())));
                int hops = 0;
                CombatEntity from = target;
                for (CombatEntity e : chained) {
                    if (hops >= 2) break;
                    int dmg = e.takeDamage(Math.max(1, baseDamage / 2));
                    extras.add(e);
                    total += dmg;
                    hops++;
                    ServerWorld sw = world(player);
                    if (sw != null) {
                        ProjectileSpawner.spawnSpellTrail(sw, arena.gridToBlockPos(from.getGridPos()),
                            arena.gridToBlockPos(e.getGridPos()),
                            ParticleTypes.ELECTRIC_SPARK, ParticleTypes.WAX_OFF, 14, 0.25);
                    }
                    msgs.add("§b✦ Storm Surge arcs to " + e.getDisplayName() + " for " + dmg + "!");
                    from = e;
                }
                if (hops > 0) {
                    fxBurst(player, arena, target, ParticleTypes.ELECTRIC_SPARK, 16,
                        "entity.lightning_bolt.impact", 1.2f);
                }
            }
            return new WeaponAbility.AttackResult(total, msgs, extras);
        };
    }

    /** Thunderbrand - Static Charge: a third of hits discharge stored lightning. */
    private static WeaponAbilityHandler thunderbrand() {
        return (player, target, arena, baseDamage, stats, luckPoints) -> {
            List<String> msgs = new ArrayList<>();
            int total = baseDamage;
            if (Math.random() < 0.33 + luckPoints * 0.02) {
                int bonus = target.takeDamage(3);
                total += bonus;
                fxBurst(player, arena, target, ParticleTypes.ELECTRIC_SPARK, 18,
                    "entity.lightning_bolt.impact", 1.6f);
                msgs.add("§e✦ Static Charge: §7the blade discharges for +" + bonus + "!");
            }
            return new WeaponAbility.AttackResult(total, msgs, List.of());
        };
    }

    /** Storm's Edge - Gale Slash: every hit carries a gust that shoves the target back. */
    private static WeaponAbilityHandler stormsEdge() {
        return Abilities.knockbackDirection(1).and(
            (player, target, arena, baseDamage, stats, luckPoints) -> {
                fxBurst(player, arena, target, ParticleTypes.GUST, 2, "entity.breeze.shoot", 1.1f);
                return new WeaponAbility.AttackResult(baseDamage, List.of(), List.of());
            });
    }

    /** Tempest - Cyclone: chance to hurl the target away in a violent spiral. */
    private static WeaponAbilityHandler tempest() {
        return (player, target, arena, baseDamage, stats, luckPoints) -> {
            List<String> msgs = new ArrayList<>();
            int total = baseDamage;
            if (Math.random() < 0.30 + luckPoints * 0.02) {
                fxRing(player, arena, target, 1.4, ParticleTypes.GUST, "entity.breeze.idle_air", 0.8f);
                msgs.add("§b✦ CYCLONE! §7" + target.getDisplayName() + " is caught in the vortex!");
                var kb = Abilities.knockbackDirection(2)
                    .apply(player, target, arena, 0, stats, luckPoints);
                msgs.addAll(kb.messages());
                int bonus = target.takeDamage(2);
                total += bonus;
            }
            return new WeaponAbility.AttackResult(total, msgs, List.of());
        };
    }

    /** Whisperwind - Zephyr: the wind blade sometimes strikes twice. */
    private static WeaponAbilityHandler whisperwind() {
        return (player, target, arena, baseDamage, stats, luckPoints) -> {
            List<String> msgs = new ArrayList<>();
            int total = baseDamage;
            if (Math.random() < 0.30 + luckPoints * 0.02) {
                int second = target.takeDamage(Math.max(1, baseDamage / 2));
                total += second;
                fxBurst(player, arena, target, ParticleTypes.SWEEP_ATTACK, 3, "entity.breeze.whirl", 1.5f);
                msgs.add("§f✦ Zephyr: §7the wind echoes your cut for +" + second + "!");
            }
            return new WeaponAbility.AttackResult(total, msgs, List.of());
        };
    }

    // =========================================================================
    // Nature & Toxin
    // =========================================================================

    /** Longsword of the Plague - Plaguebearer: every wound festers. */
    private static WeaponAbilityHandler plagueLongsword() {
        return (player, target, arena, baseDamage, stats, luckPoints) -> {
            target.stackPoison(2, 1);
            fxBurst(player, arena, target, ParticleTypes.SNEEZE, 10, "entity.zombie_villager.cure", 1.8f);
            return new WeaponAbility.AttackResult(baseDamage,
                List.of("§2✦ Plaguebearer: §7" + target.getDisplayName() + " festers with plague."), List.of());
        };
    }

    /** Bramblethorn - Thorn Lash: thorns punch through to whatever hides behind. */
    private static WeaponAbilityHandler bramblethorn() {
        return Abilities.pierce().and(
            (player, target, arena, baseDamage, stats, luckPoints) -> {
                target.stackPoison(1, 0);
                fxBurst(player, arena, target, ParticleTypes.COMPOSTER, 12, "block.sweet_berry_bush.pick_berries", 0.7f);
                return new WeaponAbility.AttackResult(baseDamage,
                    List.of("§2✦ Thorn Lash: §7brambles dig into " + target.getDisplayName() + "."), List.of());
            });
    }

    /** Hiveheart - Swarm Defense: chance to send the swarm at nearby enemies; scales with PET affinity. */
    private static WeaponAbilityHandler hiveheart() {
        return (player, target, arena, baseDamage, stats, luckPoints) -> {
            List<String> msgs = new ArrayList<>();
            List<CombatEntity> extras = new ArrayList<>();
            int total = baseDamage;
            int petPts = affinityPts(stats, PlayerProgression.Affinity.PET);
            if (Math.random() < 0.30 + petPts * 0.03 + luckPoints * 0.02) {
                int stingDmg = 2 + petPts / 2;
                int stung = 0;
                for (CombatEntity e : arena.getOccupants().values()) {
                    if (!e.isAlive() || e.isAlly()) continue;
                    if (e.getGridPos().manhattanDistance(target.getGridPos()) <= 3) {
                        int dmg = e.takeDamage(stingDmg);
                        if (e != target) extras.add(e);
                        total += dmg;
                        stung++;
                        fxBurst(player, arena, e, ParticleTypes.FALLING_NECTAR, 8, null, 1.0f);
                        if (stung >= 3) break;
                    }
                }
                if (stung > 0) {
                    ServerWorld sw = world(player);
                    if (sw != null) sound(sw, arena.gridToBlockPos(target.getGridPos()), "block.beehive.work", 1.4f);
                    msgs.add("§e✦ THE SWARM! §7Bees sting " + stung + " enemies for " + stingDmg + " each!");
                }
            }
            return new WeaponAbility.AttackResult(total, msgs, extras);
        };
    }

    /** Waxweaver - Wax Coating: coats enemies in hardening wax that slows, sometimes seizes. */
    private static WeaponAbilityHandler waxweaver() {
        return (player, target, arena, baseDamage, stats, luckPoints) -> {
            List<String> msgs = new ArrayList<>();
            if (Math.random() < 0.50) {
                target.stackSlowness(2, 1);
                msgs.add("§e✦ Wax Coating: §7" + target.getDisplayName() + " slows as the wax hardens.");
            }
            if (Math.random() < 0.15 + luckPoints * 0.02) {
                target.setStunned(true);
                fxBurst(player, arena, target, ParticleTypes.WAX_ON, 16, "block.honey_block.place", 0.8f);
                msgs.add("§6✦ ENCASED! §7The wax sets - " + target.getDisplayName() + " can't act next turn!");
            } else {
                fxBurst(player, arena, target, ParticleTypes.FALLING_HONEY, 8, "block.honey_block.hit", 1.0f);
            }
            return new WeaponAbility.AttackResult(baseDamage, msgs, List.of());
        };
    }

    // =========================================================================
    // Soul & Shadow
    // =========================================================================

    /** Shadowsting - Venom Strike: poison, with a vicious opener against unhurt prey. */
    private static WeaponAbilityHandler shadowsting() {
        return (player, target, arena, baseDamage, stats, luckPoints) -> {
            List<String> msgs = new ArrayList<>();
            int total = baseDamage;
            target.stackPoison(2, 0);
            msgs.add("§2✦ Venom Strike: §7venom seeps into " + target.getDisplayName() + ".");
            if (target.getCurrentHp() >= target.getMaxHp()) {
                int bonus = target.takeDamage(3);
                total += bonus;
                fxBurst(player, arena, target, ParticleTypes.SQUID_INK, 12, "entity.spider.ambient", 0.6f);
                msgs.add("§8✦ AMBUSH! §7Strike from the shadows: +" + bonus + "!");
            }
            return new WeaponAbility.AttackResult(total, msgs, List.of());
        };
    }

    /** Wraithfang - Soul Rend: feeds on the living and saps their strength. */
    private static WeaponAbilityHandler wraithfang() {
        return (player, target, arena, baseDamage, stats, luckPoints) -> {
            List<String> msgs = new ArrayList<>();
            int healed = lifesteal(player, baseDamage, 0.30);
            if (healed > 0) msgs.add("§d✦ Soul Rend: §adrained " + healed + " HP.");
            if (Math.random() < 0.20 + luckPoints * 0.02) {
                target.setAttackPenalty(target.getAttackPenalty() + 1);
                msgs.add("§8✦ " + target.getDisplayName() + "'s strength is sapped (-1 ATK).");
            }
            fxBurst(player, arena, target, ParticleTypes.SOUL, 10, "particle.soul_escape", 0.8f);
            return new WeaponAbility.AttackResult(baseDamage, msgs, List.of());
        };
    }

    /** Soulrender - Rend Souls: a soul-fire cleave that feeds you per enemy caught. */
    private static WeaponAbilityHandler soulrender() {
        return (player, target, arena, baseDamage, stats, luckPoints) -> {
            List<String> msgs = new ArrayList<>();
            List<CombatEntity> extras = new ArrayList<>();
            int total = baseDamage;
            List<GridPos> tiles = AoeShapes.sweepingEdge(arena.getPlayerGridPos(), target.getGridPos(), 2);
            int caught = 0;
            for (CombatEntity e : AoeShapes.enemiesOn(arena, tiles, target)) {
                int dmg = e.takeDamage(baseDamage / 2);
                extras.add(e);
                total += dmg;
                caught++;
                msgs.add("§5✦ Rend! " + e.getDisplayName() + " takes " + dmg + "!");
            }
            if (caught > 0) {
                int healed = lifesteal(player, caught * 2, 1.0);
                fxRing(player, arena, target, 1.4, ParticleTypes.SOUL_FIRE_FLAME, "particle.soul_escape", 0.6f);
                msgs.add("§d✦ Souls flow back to you: §a+" + healed + " HP.");
            }
            return new WeaponAbility.AttackResult(total, msgs, extras);
        };
    }

    /** Soulstealer - Siphon: steals health, occasionally strength itself. */
    private static WeaponAbilityHandler soulstealer() {
        return (player, target, arena, baseDamage, stats, luckPoints) -> {
            List<String> msgs = new ArrayList<>();
            int healed = lifesteal(player, baseDamage, 0.25);
            if (healed > 0) msgs.add("§d✦ Siphon: §adrained " + healed + " HP.");
            if (Math.random() < 0.15 + luckPoints * 0.02) {
                target.setAttackPenalty(target.getAttackPenalty() + 1);
                fxBurst(player, arena, target, ParticleTypes.WITCH, 12, "entity.vex.charge", 0.7f);
                msgs.add("§5✦ SOUL THEFT! §7" + target.getDisplayName() + " loses 1 ATK.");
            }
            return new WeaponAbility.AttackResult(baseDamage, msgs, List.of());
        };
    }

    /** Waking Lichblade - the phylactery stirs: strong drain, whispers of wither. */
    private static WeaponAbilityHandler wakingLichblade() {
        return (player, target, arena, baseDamage, stats, luckPoints) -> {
            List<String> msgs = new ArrayList<>();
            int healed = lifesteal(player, baseDamage, 0.25);
            if (healed > 0) msgs.add("§d✦ Waking Hunger: §adrained " + healed + " HP.");
            if (Math.random() < 0.15 + luckPoints * 0.02) {
                target.stackWither(1, 0);
                msgs.add("§8✦ Wither creeps into " + target.getDisplayName() + ".");
            }
            fxBurst(player, arena, target, ParticleTypes.SOUL, 12, "particle.soul_escape", 0.7f);
            return new WeaponAbility.AttackResult(baseDamage, msgs, List.of());
        };
    }

    /** Awakened Lichblade - the lich's full hunger: heavy drain and spreading wither. */
    private static WeaponAbilityHandler awakenedLichblade() {
        return (player, target, arena, baseDamage, stats, luckPoints) -> {
            List<String> msgs = new ArrayList<>();
            int healed = lifesteal(player, baseDamage, 0.33);
            if (healed > 0) msgs.add("§d✦ Awakened Hunger: §adrained " + healed + " HP.");
            if (Math.random() < 0.25 + luckPoints * 0.02) {
                target.stackWither(2, 0);
                fxBurst(player, arena, target, ParticleTypes.SOUL_FIRE_FLAME, 16, "entity.wither.hurt", 1.3f);
                msgs.add("§8✦ WITHERING GRASP! §7Decay spreads through " + target.getDisplayName() + "!");
            } else {
                fxBurst(player, arena, target, ParticleTypes.SOUL, 12, "particle.soul_escape", 0.6f);
            }
            return new WeaponAbility.AttackResult(baseDamage, msgs, List.of());
        };
    }

    // =========================================================================
    // Arcane
    // =========================================================================

    /** Arcanethyst - Prismatic Echo: crystal resonance repeats part of your hit. */
    private static WeaponAbilityHandler arcanethyst() {
        return (player, target, arena, baseDamage, stats, luckPoints) -> {
            List<String> msgs = new ArrayList<>();
            int total = baseDamage;
            if (Math.random() < 0.30 + luckPoints * 0.02) {
                int echo = Math.max(1, baseDamage / 2) + affinityPts(stats, PlayerProgression.Affinity.SPECIAL) / 2;
                int dmg = target.takeDamage(echo);
                total += dmg;
                fxBurst(player, arena, target, ParticleTypes.END_ROD, 10, "block.amethyst_block.chime", 1.0f);
                fxBurst(player, arena, target, ParticleTypes.ENCHANT, 16, "block.amethyst_cluster.break", 1.2f);
                msgs.add("§d✦ Prismatic Echo: §7the crystal rings for +" + dmg + "!");
            }
            return new WeaponAbility.AttackResult(total, msgs, List.of());
        };
    }

    /** Magiblade - Arcane Edge: raw spellpower sharpens every swing (SPECIAL affinity scaling). */
    private static WeaponAbilityHandler magiblade() {
        return (player, target, arena, baseDamage, stats, luckPoints) -> {
            List<String> msgs = new ArrayList<>();
            int total = baseDamage;
            int specialPts = affinityPts(stats, PlayerProgression.Affinity.SPECIAL);
            if (specialPts >= 2) {
                int bonus = target.takeDamage(specialPts / 2);
                total += bonus;
                fxBurst(player, arena, target, ParticleTypes.ENCHANT, 12, "entity.illusioner.cast_spell", 1.3f);
                msgs.add("§d✦ Arcane Edge: §7spellpower adds +" + bonus + ".");
            }
            return new WeaponAbility.AttackResult(total, msgs, List.of());
        };
    }

    /** Enchanted Estoc - Mana Thrust: chance to punch clean through to the enemy behind. */
    private static WeaponAbilityHandler magicEstoc() {
        return (player, target, arena, baseDamage, stats, luckPoints) -> {
            if (Math.random() < 0.25 + luckPoints * 0.02) {
                var r = Abilities.pierce().apply(player, target, arena, baseDamage, stats, luckPoints);
                if (!r.extraTargets().isEmpty()) {
                    fxTrail(player, arena, target, ParticleTypes.ENCHANT, ParticleTypes.CRIT,
                        "entity.illusioner.cast_spell", 1.6f);
                    List<String> msgs = new ArrayList<>(List.of("§d✦ Mana Thrust!"));
                    msgs.addAll(r.messages());
                    return new WeaponAbility.AttackResult(r.totalDamage(), msgs, r.extraTargets());
                }
            }
            return new WeaponAbility.AttackResult(baseDamage, List.of(), List.of());
        };
    }

    /** Magiscythe - Arcane Reap: an arcane arc empowered by SPECIAL affinity. */
    private static WeaponAbilityHandler magiscythe() {
        return (player, target, arena, baseDamage, stats, luckPoints) -> {
            List<String> msgs = new ArrayList<>();
            List<CombatEntity> extras = new ArrayList<>();
            int total = baseDamage;
            int specialPts = affinityPts(stats, PlayerProgression.Affinity.SPECIAL);
            List<GridPos> tiles = AoeShapes.sweepingEdge(arena.getPlayerGridPos(), target.getGridPos(), 2);
            for (CombatEntity e : AoeShapes.enemiesOn(arena, tiles, target)) {
                int dmg = e.takeDamage(baseDamage / 2 + specialPts / 2);
                extras.add(e);
                total += dmg;
                msgs.add("§d✦ Arcane Reap! " + e.getDisplayName() + " takes " + dmg + "!");
            }
            if (!extras.isEmpty()) {
                fxRing(player, arena, target, 1.4, ParticleTypes.ENCHANT, "entity.evoker.cast_spell", 1.1f);
            }
            return new WeaponAbility.AttackResult(total, msgs, extras);
        };
    }

    /** Magispear - Mana Lance: reach-2 lance that always pierces the tile behind. */
    private static WeaponAbilityHandler magispear() {
        return Abilities.pierce().and(
            (player, target, arena, baseDamage, stats, luckPoints) -> {
                fxTrail(player, arena, target, ParticleTypes.ENCHANT, ParticleTypes.END_ROD,
                    "entity.illusioner.cast_spell", 1.4f);
                return new WeaponAbility.AttackResult(baseDamage, List.of(), List.of());
            });
    }

    /** Enigma - Paradox: nobody knows what it does next. Including it. */
    private static WeaponAbilityHandler enigma() {
        return (player, target, arena, baseDamage, stats, luckPoints) -> {
            List<String> msgs = new ArrayList<>();
            int total = baseDamage;
            switch (new java.util.Random().nextInt(5)) {
                case 0 -> {
                    target.stackBurning(1, 0);
                    fxBurst(player, arena, target, ParticleTypes.FLAME, 10, "entity.blaze.burn", 1.2f);
                    msgs.add("§6✦ Paradox: §7today it burns.");
                }
                case 1 -> {
                    target.stackSlowness(2, 1);
                    fxBurst(player, arena, target, ParticleTypes.SNOWFLAKE, 10, "block.powder_snow.hit", 0.9f);
                    msgs.add("§b✦ Paradox: §7today it chills.");
                }
                case 2 -> {
                    target.stackPoison(2, 0);
                    fxBurst(player, arena, target, ParticleTypes.SNEEZE, 10, "entity.witch.throw", 0.8f);
                    msgs.add("§2✦ Paradox: §7today it poisons.");
                }
                case 3 -> {
                    target.stackConfusion(1, 0);
                    fxBurst(player, arena, target, ParticleTypes.WITCH, 12, "entity.shulker.teleport", 1.4f);
                    msgs.add("§d✦ Paradox: §7today it bewilders.");
                }
                default -> {
                    int bonus = target.takeDamage(3);
                    total += bonus;
                    fxBurst(player, arena, target, ParticleTypes.CRIT, 14, "entity.player.attack.crit", 0.9f);
                    msgs.add("§c✦ Paradox: §7today it simply hits harder (+" + bonus + ").");
                }
            }
            return new WeaponAbility.AttackResult(total, msgs, List.of());
        };
    }

    /** Twilight - Duskfall: dusk swallows the target's sight. */
    private static WeaponAbilityHandler twilight() {
        return (player, target, arena, baseDamage, stats, luckPoints) -> {
            List<String> msgs = new ArrayList<>();
            if (Math.random() < 0.25 + luckPoints * 0.02) {
                target.setAttackPenalty(target.getAttackPenalty() + 1);
                target.stackConfusion(1, 0);
                fxBurst(player, arena, target, ParticleTypes.PORTAL, 20, "block.respawn_anchor.deplete", 0.6f);
                msgs.add("§5✦ Duskfall: §7shadow pours over " + target.getDisplayName()
                    + " (-1 ATK, confused).");
            }
            return new WeaponAbility.AttackResult(baseDamage, msgs, List.of());
        };
    }

    /** Star's Edge - Starfall: chance to call a shard of starlight down on the area. */
    private static WeaponAbilityHandler starsEdge() {
        return (player, target, arena, baseDamage, stats, luckPoints) -> {
            List<String> msgs = new ArrayList<>();
            List<CombatEntity> extras = new ArrayList<>();
            int total = baseDamage;
            if (Math.random() < 0.25 + luckPoints * 0.02) {
                int bonus = target.takeDamage(3);
                total += bonus;
                fxBurst(player, arena, target, ParticleTypes.END_ROD, 26, "block.amethyst_block.resonate", 0.8f);
                msgs.add("§f✦ STARFALL! §7Starlight lances down for +" + bonus + "!");
                GridPos tPos = target.getGridPos();
                for (CombatEntity e : arena.getOccupants().values()) {
                    if (e == target || !e.isAlive() || e.isAlly()) continue;
                    if (tPos.manhattanDistance(e.getGridPos()) <= 1) {
                        int dmg = e.takeDamage(Math.max(1, baseDamage / 2));
                        extras.add(e);
                        total += dmg;
                        msgs.add("§f✦ Stardust burns " + e.getDisplayName() + " for " + dmg + "!");
                    }
                }
            }
            return new WeaponAbility.AttackResult(total, msgs, extras);
        };
    }

    /** Caelestis - Divine Light: smites the wicked, mends the wielder. */
    private static WeaponAbilityHandler caelestis() {
        return (player, target, arena, baseDamage, stats, luckPoints) -> {
            List<String> msgs = new ArrayList<>();
            int total = baseDamage;
            if (Math.random() < 0.25 + luckPoints * 0.02) {
                int bonus = target.takeDamage(2);
                total += bonus;
                float before = player.getHealth();
                player.setHealth(Math.min(player.getMaxHealth(), before + 2));
                int healed = (int) (player.getHealth() - before);
                fxBurst(player, arena, target, ParticleTypes.END_ROD, 14, "block.beacon.power_select", 1.5f);
                fxBurst(player, arena, target, ParticleTypes.GLOW, 8, null, 1.0f);
                msgs.add("§e✦ Divine Light: §7+" + bonus + " holy damage" +
                    (healed > 0 ? "§a, +" + healed + " HP restored." : "."));
            }
            return new WeaponAbility.AttackResult(total, msgs, List.of());
        };
    }

    // =========================================================================
    // Sculk
    // =========================================================================

    /** The Watcher - Sonic Rend: the claymore screams with the Warden's voice. */
    private static WeaponAbilityHandler watcherClaymore() {
        return (player, target, arena, baseDamage, stats, luckPoints) -> {
            List<String> msgs = new ArrayList<>();
            int total = baseDamage;
            if (Math.random() < 0.30 + luckPoints * 0.02) {
                int bonus = target.takeDamage(4);
                total += bonus;
                ServerWorld sw = world(player);
                if (sw != null) {
                    BlockPos from = arena.gridToBlockPos(arena.getPlayerGridPos());
                    BlockPos to = arena.gridToBlockPos(target.getGridPos());
                    ProjectileSpawner.spawnProjectile(sw, from, to, "sonic_boom");
                    sound(sw, to, "entity.warden.sonic_boom", 1.2f);
                }
                msgs.add("§3✦ SONIC REND! §7A shriek tears through " + target.getDisplayName()
                    + " for +" + bonus + "!");
            }
            return new WeaponAbility.AttackResult(total, msgs, List.of());
        };
    }

    /** Watching Warglaive - Echoing Slash: the sculk remembers your strike and repeats it. */
    private static WeaponAbilityHandler watchingWarglaive() {
        return (player, target, arena, baseDamage, stats, luckPoints) -> {
            List<String> msgs = new ArrayList<>();
            int total = baseDamage;
            if (Math.random() < 0.30 + luckPoints * 0.02) {
                int echo = target.takeDamage(Math.max(1, baseDamage / 2));
                total += echo;
                fxBurst(player, arena, target, ParticleTypes.SCULK_SOUL, 14, "block.sculk_sensor.clicking", 0.7f);
                msgs.add("§3✦ Echoing Slash: §7the sculk repeats your cut for +" + echo + "!");
            }
            return new WeaponAbility.AttackResult(total, msgs, List.of());
        };
    }

    // =========================================================================
    // Curios
    // =========================================================================

    /** Ribboncleaver - Ribbon Cut: leaves long, ragged wounds; sometimes flicks to a neighbor. */
    private static WeaponAbilityHandler ribboncleaver() {
        return (player, target, arena, baseDamage, stats, luckPoints) -> {
            List<String> msgs = new ArrayList<>();
            List<CombatEntity> extras = new ArrayList<>();
            int total = baseDamage;
            target.stackBleed(2);
            msgs.add("§c✦ Ribbon Cut: §7" + target.getDisplayName() + " bleeds in ribbons.");
            if (Math.random() < 0.20 + luckPoints * 0.02) {
                for (CombatEntity e : Abilities.findAdjacentEnemies(arena, target, 1)) {
                    int dmg = e.takeDamage(Math.max(1, baseDamage / 2));
                    e.stackBleed(1);
                    extras.add(e);
                    total += dmg;
                    fxBurst(player, arena, e, ParticleTypes.CHERRY_LEAVES, 10, "entity.player.attack.sweep", 1.3f);
                    msgs.add("§c✦ The ribbon flicks to " + e.getDisplayName() + " for " + dmg + "!");
                }
            }
            return new WeaponAbility.AttackResult(total, msgs, extras);
        };
    }

    /** Twisted Blade - Twist Reality: reality bends; sometimes the enemy forgets whose side it's on. */
    private static WeaponAbilityHandler twistedBlade() {
        return (player, target, arena, baseDamage, stats, luckPoints) -> {
            List<String> msgs = new ArrayList<>();
            if (Math.random() < 0.25 + luckPoints * 0.02) {
                target.stackConfusion(1, 0);
                fxBurst(player, arena, target, ParticleTypes.PORTAL, 18, "entity.enderman.teleport", 0.7f);
                msgs.add("§5✦ Twist Reality: §7" + target.getDisplayName() + " is bewildered!");
            }
            return new WeaponAbility.AttackResult(baseDamage, msgs, List.of());
        };
    }

    /** Harbinger - Doomsday: sometimes the doom it promises arrives early (% max HP). */
    private static WeaponAbilityHandler harbinger() {
        return (player, target, arena, baseDamage, stats, luckPoints) -> {
            List<String> msgs = new ArrayList<>();
            int total = baseDamage;
            if (Math.random() < 0.20 + luckPoints * 0.02) {
                int doom = target.takeDamage(Math.max(2, target.percentMaxHpDamage(0.10)));
                total += doom;
                fxBurst(player, arena, target, ParticleTypes.LARGE_SMOKE, 18, "entity.wither.spawn", 1.8f);
                msgs.add("§4✦ DOOMSDAY: §7fate collects " + doom + " early.");
            }
            return new WeaponAbility.AttackResult(total, msgs, List.of());
        };
    }

    /** Sword on a Stick - Bonk: it's a sword. On a stick. Reach 2. Sometimes bonks. */
    private static WeaponAbilityHandler swordOnAStick() {
        return (player, target, arena, baseDamage, stats, luckPoints) -> {
            List<String> msgs = new ArrayList<>();
            if (Math.random() < 0.10 + luckPoints * 0.02) {
                target.setStunned(true);
                fxBurst(player, arena, target, ParticleTypes.CRIT, 8, "entity.shield.block", 0.5f);
                msgs.add("§e✦ BONK! §7" + target.getDisplayName() + " is bonked silly for a turn.");
            }
            return new WeaponAbility.AttackResult(baseDamage, msgs, List.of());
        };
    }
}
