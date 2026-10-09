package com.crackedgames.craftics.combat.shadow;

import com.crackedgames.craftics.CrafticsMod;
import com.crackedgames.craftics.api.AbilityPlan;
import com.crackedgames.craftics.api.VanillaWeapons;
import com.crackedgames.craftics.api.WeaponAbilityHandler;
import com.crackedgames.craftics.api.registry.WeaponEntry;
import com.crackedgames.craftics.api.registry.WeaponRegistry;
import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.CombatManager;
import com.crackedgames.craftics.combat.PlayerCombatStats;
import com.crackedgames.craftics.combat.PlayerProgression;
import com.crackedgames.craftics.combat.WeaponAbility;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;
import com.crackedgames.craftics.core.GridTile;
import com.crackedgames.craftics.core.TileType;
import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A Shadow using a weapon the way its player would.
 *
 * <p>A weapon's ability is written as "this player hits that monster, in this arena", and it
 * reaches for everything it needs through those three: the player's held item for its
 * enchantments, the arena for who else is in range, the monster for something to hurt. So
 * rather than describe every weapon a second time for the Shadow - which could never keep up
 * with a modded armoury - the real code is run, with all three swapped for stand-ins:
 *
 * <ul>
 *   <li><b>the player</b> is a fake one standing where the Shadow stands, holding the copied
 *       weapon, enchantments and all;</li>
 *   <li><b>the monsters</b> are stand-ins for the real players and their pets, at the same
 *       tiles with the same health;</li>
 *   <li><b>the arena</b> is a copy holding only those stand-ins, so "everything near the
 *       target" means the party and never the Shadow's own side.</li>
 * </ul>
 *
 * <p>Nothing real is touched while the weapon resolves. A chakram ricochets between
 * stand-ins, Cyclone throws one across the copied arena, Fire Aspect sets one alight. Then
 * {@link ShadowAftermath} reads each stand-in and the caller does the same to whoever it stood
 * for, through the ordinary enemy damage path, so their dodging and death handling still
 * apply.
 *
 * <p>The order of events is the one a player's own swing follows (see
 * {@code CombatManager.resolveAbilityHop}): the hit, then the weapon's ability, then its
 * enchantments, and for a weapon that plans a chain of hits, the same again at every hop.
 * Each hop comes back as its own {@link Stage}, so a thrown weapon's damage can land as the
 * weapon arrives rather than all at once when it leaves the hand.
 */
public final class ShadowStrike {

    private ShadowStrike() {}

    /** What the weapon did to one real victim. Exactly one of {@code player} and {@code pet} is set. */
    public record Outcome(@Nullable ServerPlayerEntity player, @Nullable CombatEntity pet,
                          ShadowAftermath.Change change) {}

    /**
     * One landing of the weapon: the first hit, or one ricochet.
     *
     * @param landsAt  the tile the weapon reaches for this landing
     * @param outcomes everyone this landing touched, the one it was aimed at first
     * @param messages what the weapon's own code had to say about it
     */
    public record Stage(GridPos landsAt, List<Outcome> outcomes, List<String> messages) {}

    /**
     * @param stages           the landings, in order; empty when the weapon found nobody
     * @param flies            true for a weapon that leaves the hand: thrown, or shot
     * @param returnsToThrower whether a thrown weapon comes back
     * @param healed           health the weapon gave back to its wielder, on a player's scale
     */
    public record Result(List<Stage> stages, boolean flies, boolean returnsToThrower, int healed) {
        public static Result nothing() {
            return new Result(List.of(), false, false, 0);
        }
    }

    /** The stand-in attacker. One profile for every Shadow: it is only ever borrowed for a moment. */
    private static final UUID ATTACKER_ID = UUID.fromString("5ad0c10e-5ad0-4c10-8e5a-d0c10e5ad0c1");
    private static final GameProfile ATTACKER = new GameProfile(ATTACKER_ID, "Shadow");

    /**
     * Use {@code weaponStack} on the primary victim and report what happened to everyone.
     *
     * @param damage        what the first hit is meant to land for
     * @param stats         the copied player's stats, so the weapon's procs roll the way they do
     *                      for that player; may be null
     * @param players       every living party member and the tile they stand on
     * @param pets          every living pet in the fight
     * @param primaryPlayer the player being attacked, or null when the target is a pet
     * @param primaryPet    the pet being attacked, or null when the target is a player
     */
    public static Result run(ServerWorld world, GridArena arena, CombatEntity shadow,
                             ItemStack weaponStack, int damage,
                             @Nullable PlayerProgression.PlayerStats stats,
                             Map<ServerPlayerEntity, GridPos> players, List<CombatEntity> pets,
                             @Nullable ServerPlayerEntity primaryPlayer, @Nullable CombatEntity primaryPet) {
        GridPos from = shadow.getGridPos();
        GridArena copy = copyOf(arena, from);
        Map<CombatEntity, Outcome> realByStandIn = new LinkedHashMap<>();
        Map<CombatEntity, ShadowAftermath> watches = new LinkedHashMap<>();
        CombatEntity primary = null;

        int nextId = -5000;
        for (Map.Entry<ServerPlayerEntity, GridPos> entry : players.entrySet()) {
            ServerPlayerEntity real = entry.getKey();
            GridPos at = entry.getValue();
            if (at == null || !copy.isInBounds(at) || copy.getOccupant(at) != null) continue;
            int hp = (int) Math.ceil(real.getHealth() + real.getAbsorptionAmount());
            CombatEntity standIn = new CombatEntity(nextId--, "minecraft:player", at,
                Math.max(1, hp), 0, 0, 1, 1, 3);
            standIn.setNameOverride(real.getName().getString());
            if (!copy.placeEntity(standIn)) continue;
            realByStandIn.put(standIn, new Outcome(real, null, null));
            watches.put(standIn, ShadowAftermath.watch(standIn));
            if (real == primaryPlayer) primary = standIn;
        }
        for (CombatEntity real : pets) {
            if (real == null || !real.isAlive()) continue;
            GridPos at = real.getGridPos();
            // A pet somebody is riding shares its rider's tile. The rider is the one hit.
            if (at == null || !copy.isInBounds(at) || copy.getOccupant(at) != null) continue;
            CombatEntity standIn = new CombatEntity(nextId--, real.getEntityTypeId(), at,
                Math.max(1, real.getCurrentHp()), 0, 0, 1, 1, 3);
            standIn.setNameOverride(real.getDisplayName());
            if (!copy.placeEntity(standIn)) continue;
            realByStandIn.put(standIn, new Outcome(null, real, null));
            watches.put(standIn, ShadowAftermath.watch(standIn));
            if (real == primaryPet) primary = standIn;
        }
        if (primary == null) return Result.nothing();

        Item item = weaponStack.isEmpty() ? Items.AIR : weaponStack.getItem();
        WeaponEntry entry = WeaponRegistry.get(item);
        WeaponAbilityHandler ability = entry.ability();
        int luck = stats != null ? stats.getPoints(PlayerProgression.Stat.LUCK) : 0;

        ServerPlayerEntity attacker = FakePlayer.get(world, ATTACKER);
        float restingHealth = attacker.getMaxHealth() / 2f;
        List<Stage> stages = new ArrayList<>();
        boolean planned = false;
        boolean returns = false;
        int healed = 0;
        try {
            BlockPos block = arena.gridToBlockPos(from);
            GridPos aim = primary.getGridPos();
            float yaw = (float) Math.toDegrees(Math.atan2(-(aim.x() - from.x()), aim.z() - from.z()));
            attacker.refreshPositionAndAngles(block.getX() + 0.5, block.getY() + 1.0, block.getZ() + 0.5, yaw, 0f);
            attacker.setStackInHand(Hand.MAIN_HAND, weaponStack.copy());
            // Half health, so a weapon that heals its wielder has room to show it.
            attacker.setHealth(restingHealth);

            // A weapon that plans its hits (a chakram: out, on to the next, and back) says who
            // is struck and for how much before anything lands. Everything else is one hit.
            List<AbilityPlan.Hop> hops = List.of(new AbilityPlan.Hop(primary, damage));
            if (ability != null && ability.isPlanned()) {
                try {
                    AbilityPlan plan = ability.plan(attacker, primary, copy, damage, stats, luck);
                    if (plan != null && !plan.hops().isEmpty()) {
                        hops = plan.hops();
                        returns = plan.returnsToThrower();
                        planned = true;
                    }
                } catch (Throwable broken) {
                    warn(item, broken);
                }
            }

            for (int i = 0; i < hops.size(); i++) {
                AbilityPlan.Hop hop = hops.get(i);
                CombatEntity target = hop.target();
                if (target == null || !target.isAlive() || !watches.containsKey(target)) continue;
                GridPos landsAt = target.getGridPos();
                List<String> messages = new ArrayList<>();
                boolean faulted = false;
                try {
                    land(attacker, weaponStack, item, ability, hop, copy, from, stats, luck, messages);
                } catch (Throwable broken) {
                    // A weapon written for a real player met something a stand-in does not
                    // have. The Shadow still hit with it: whatever landed before the fault
                    // stands, and if nothing had, the plain hit does.
                    warn(item, broken);
                    if (watches.get(target).read().damage() <= 0) target.takeDamage(hop.damage());
                    faulted = true;
                }
                stages.add(collect(landsAt, target, realByStandIn, watches, messages));
                // A hop that kills ends the chain, the same as it does for a player's throw.
                if (faulted || !target.isAlive()) break;
            }
            healed = Math.max(0, Math.round(attacker.getHealth() - restingHealth));
        } finally {
            attacker.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
            attacker.clearStatusEffects();
            // A weapon that asks for "this player's fight" (to summon something into it) is
            // handed an empty one made on the spot, which correctly does nothing. Drop it.
            CombatManager.remove(ATTACKER_ID);
        }
        return new Result(stages, entry.isRanged() || planned, returns, healed);
    }

    /** One landing: the hit, the weapon's own ability, then its enchantments. */
    private static void land(ServerPlayerEntity attacker, ItemStack weaponStack, Item item,
                             @Nullable WeaponAbilityHandler ability, AbilityPlan.Hop hop,
                             GridArena copy, GridPos from,
                             @Nullable PlayerProgression.PlayerStats stats, int luck,
                             List<String> messages) {
        CombatEntity target = hop.target();
        target.takeDamage(hop.damage());
        if (!hop.appliesOnHitEffects() || !target.isAlive()) return;

        boolean abilityHitOthers = false;
        if (ability != null) {
            WeaponAbility.AttackResult result = ability.apply(attacker, target, copy, hop.damage(), stats, luck);
            abilityHitOthers = !result.extraTargets().isEmpty();
            keep(messages, result.messages());
        }
        if (!target.isAlive()) return;

        if (PlayerCombatStats.isBowItem(item) || item == Items.CROSSBOW) {
            // A bow's enchantments, as a player's shot applies them.
            int flame = PlayerCombatStats.getBowFlame(attacker);
            if (flame > 0) {
                target.stackBurning(flame == 1 ? 2 : 4, flame == 1 ? 0 : 1);
                messages.add("§6Flame! §7" + target.getDisplayName() + " catches light.");
            }
            int punch = PlayerCombatStats.getBowPunch(attacker);
            if (punch > 0) shove(copy, target, from, punch);
            return;
        }
        // Sharpness, Smite, Bane, Knockback, Serrated and Sweeping Edge: the same pass a
        // player's swing gets.
        WeaponAbility.AttackResult enchants = VanillaWeapons.universalEnchantEffects(
            attacker, target, copy, hop.damage(), stats, luck, abilityHitOthers);
        keep(messages, enchants.messages());
        int fire = PlayerCombatStats.getEnchantLevel(weaponStack, "minecraft:fire_aspect");
        if (fire > 0 && target.isAlive()) {
            target.stackBurning(fire * 2, Math.max(0, fire - 1));
            messages.add("§6Fire Aspect! §7" + target.getDisplayName() + " is set alight.");
        }
    }

    /** Read every stand-in, turn what changed into outcomes, and start watching afresh. */
    private static Stage collect(GridPos landsAt, CombatEntity aimedAt,
                                 Map<CombatEntity, Outcome> realByStandIn,
                                 Map<CombatEntity, ShadowAftermath> watches, List<String> messages) {
        List<Outcome> outcomes = new ArrayList<>();
        for (Map.Entry<CombatEntity, Outcome> entry : realByStandIn.entrySet()) {
            CombatEntity standIn = entry.getKey();
            ShadowAftermath.Change change = watches.get(standIn).read();
            if (change.nothing()) continue;
            Outcome outcome = new Outcome(entry.getValue().player(), entry.getValue().pet(), change);
            if (standIn == aimedAt) outcomes.add(0, outcome);
            else outcomes.add(outcome);
            watches.put(standIn, ShadowAftermath.watch(standIn));
        }
        return new Stage(landsAt, outcomes, messages);
    }

    /** Lines meant for the chat. Bracketed ones are instructions to the combat manager and are dropped. */
    private static void keep(List<String> messages, List<String> more) {
        if (more == null) return;
        for (String line : more) {
            if (line != null && !line.isEmpty() && !line.startsWith("[")) messages.add(line);
        }
    }

    private static void warn(Item item, Throwable broken) {
        CrafticsMod.LOGGER.warn("[SHADOW] {} could not be used as its owner would: {}", item, broken.toString());
    }

    /** Push a stand-in away from {@code from}, a tile at a time, until something is in the way. */
    private static void shove(GridArena copy, CombatEntity standIn, GridPos from, int tiles) {
        GridPos at = standIn.getGridPos();
        int dx = Integer.signum(at.x() - from.x());
        int dz = Integer.signum(at.z() - from.z());
        if (dx == 0 && dz == 0) return;
        for (int step = 0; step < tiles; step++) {
            GridPos next = new GridPos(at.x() + dx, at.z() + dz);
            GridTile tile = copy.isInBounds(next) ? copy.getTile(next.x(), next.z()) : null;
            if (tile == null || !tile.isWalkable() || copy.isOccupied(next)) return;
            if (!copy.moveEntity(standIn, next)) return;
            at = next;
        }
    }

    /**
     * The same ground with nobody on it, and the "player" standing where the Shadow is.
     *
     * <p>Pits, deep water and lava are laid flat in the copy. A weapon only decides how far
     * and which way something is thrown; what the landing costs is decided when the real
     * victim is moved across the real arena, with a real player's ways out of it (a boat, a
     * ledge grip, a totem). Left in, the copy would kill the stand-in for falling and the
     * victim would pay for the fall twice.
     *
     * <p>Every tile is a copy, never the arena's own. A weapon that scorches or floods the
     * ground does it by changing the tile it is handed, and a change made to a shared tile
     * would turn real ground into fire the world never drew.
     */
    private static GridArena copyOf(GridArena arena, GridPos attackerAt) {
        int width = arena.getWidth();
        int height = arena.getHeight();
        GridTile[][] tiles = new GridTile[width][height];
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < height; z++) {
                GridTile tile = arena.getTile(x, z);
                if (tile == null) continue;
                tiles[x][z] = isHazard(tile.getType())
                    ? new GridTile(TileType.NORMAL)
                    : new GridTile(tile.getType(), tile.getBlockType());
            }
        }
        GridArena copy = new GridArena(width, height, tiles, arena.getOrigin(), arena.getLevelNumber(),
            attackerAt, arena.getInsideMask());
        copy.setPlayerGridPos(attackerAt);
        return copy;
    }

    private static boolean isHazard(TileType type) {
        return type == TileType.VOID || type == TileType.DEEP_WATER
            || type == TileType.LAVA || type == TileType.WATER;
    }
}
