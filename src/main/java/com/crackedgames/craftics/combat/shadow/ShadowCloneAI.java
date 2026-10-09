package com.crackedgames.craftics.combat.shadow;

import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.combat.Pathfinding;
import com.crackedgames.craftics.combat.ai.AIUtils;
import com.crackedgames.craftics.combat.ai.EnemyAction;
import com.crackedgames.craftics.combat.ai.boss.BossAI;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * End City boss - "The Shadow". A dark copy of a player, one per player in the fight.
 *
 * <p>It takes its turn the way its player does. It has their AP and their Speed, it can draw
 * any weapon they are carrying, and it spends the turn one action at a time until nothing
 * useful is left to buy:
 *
 * <ul>
 *   <li><b>Weapons</b> keep their reach and AP cost, and do what they do in the player's
 *       hands: the weapon's own ability and enchantment code is run, aimed back at the party
 *       ({@link ShadowStrike}). A chakram is thrown and ricochets between the players and
 *       their pets, a Knockback blade throws them across the arena, Fire Aspect sets them
 *       alight, and a 1-AP weapon gets more uses out of a turn than a 2-AP one.</li>
 *   <li><b>Throwables and sherds</b> are thrown and cast back at the player, each with its
 *       own reach, area and extra effect ({@link ShadowSpells}).</li>
 *   <li><b>Food and potions</b>: it eats when badly hurt, drinks buffs on the way in and
 *       throws the harmful ones.</li>
 *   <li><b>Pets</b>: on its first turn it calls up a shadow of every pet they brought.</li>
 * </ul>
 *
 * <p>Phase 2, at half health, gives it one more AP a turn.
 *
 * <p>{@link ShadowPlanner} decides what to do and {@link ShadowBalance} decides how hard it
 * hits. This class is the part in between that has to touch the arena: it turns each step
 * into one enemy action.
 */
public class ShadowCloneAI extends BossAI {

    /** How many shadow pets one Shadow will call up. */
    private static final int MAX_SHADOW_PETS = 4;
    private static final int PHASE_TWO_BONUS_AP = 1;
    /** What Seeker Vexes calls up, and how tough each one is. */
    private static final String SEEKER_TYPE = "minecraft:vex";
    private static final int SEEKER_HP = 4;
    /** A step that turns out to be impossible is dropped and the turn re-planned this many times. */
    private static final int MAX_REPLANS = 6;

    /** An item on its way to being used: the handler collects it when the action resolves. */
    public record PendingItem(ShadowItem item, int amount, String displayName) {}

    /**
     * A weapon on its way to being used: the combat manager collects it when the swing lands,
     * and takes the copied stack itself out of the {@link #kit()} by the weapon's slot.
     *
     * @param damage what the first hit is meant to land for
     */
    public record PendingStrike(ShadowWeapon weapon, int damage) {}

    private ShadowKit kit = ShadowKit.empty();
    /** True for one Shadow per fight: the one that also copies pets nobody is recorded as owning. */
    private boolean claimsUnownedPets;

    /** True while the turn machine has granted another action on the turn already under way. */
    private boolean continuing;
    private int apLeft;
    private int moveLeft;
    private int actionsThisTurn;
    private boolean moreToDo;

    /**
     * Free actions the last step left behind, in order: a snowball's knockback, an angler
     * sherd's pull. They resolve before anything new is started.
     */
    private final Deque<EnemyAction> owed = new ArrayDeque<>();

    /** How many times each item has been used, by kit slot. */
    private final Map<Integer, Integer> usesBySlot = new HashMap<>();
    private PendingItem pendingItem;
    private PendingStrike pendingStrike;

    /** A pet waiting to be copied: its type and the stats its shadow will have. */
    private record ShadowPet(String typeId, int hp, int attack, int defense) {}

    private boolean petsCalled;
    private final Deque<ShadowPet> petsToCall = new ArrayDeque<>();

    /** Give this Shadow the player it copies. Called once, as it spawns. */
    public void bind(ShadowKit kit, boolean claimsUnownedPets) {
        this.kit = kit == null ? ShadowKit.empty() : kit;
        this.claimsUnownedPets = claimsUnownedPets;
    }

    public ShadowKit kit() { return kit; }

    @Override
    public boolean wantsAnotherAction(CombatEntity self) {
        // Hand the request over and forget it. If the turn machine comes back round but
        // never reaches this AI (stunned, target out of sight), the next answer is no and
        // the turn ends, rather than the two of them asking each other forever.
        continuing = moreToDo;
        moreToDo = false;
        return continuing;
    }

    /** The item the last action is using. Cleared as it is read, so it is used exactly once. */
    public PendingItem takePendingItem() {
        PendingItem item = pendingItem;
        pendingItem = null;
        return item;
    }

    /** The weapon the last action is attacking with. Cleared as it is read, so it lands exactly once. */
    public PendingStrike takePendingStrike() {
        PendingStrike strike = pendingStrike;
        pendingStrike = null;
        return strike;
    }

    @Override
    protected void onPhaseTransition(CombatEntity self, GridArena arena, GridPos playerPos) {
        self.setEnraged(true);
    }

    /** A Shadow never telegraphs, so there is nothing to walk forward under. */
    @Override
    public EnemyAction getChargingAdvanceAction(CombatEntity self, GridArena arena, GridPos playerPos) {
        return new EnemyAction.Idle();
    }

    @Override
    protected EnemyAction chooseAbility(CombatEntity self, GridArena arena, GridPos targetPos) {
        if (!continuing) openTurn(self);
        continuing = false;

        // Shadow pets come first and cost nothing: they arrive with it, not instead of a swing.
        if (!petsCalled) {
            petsCalled = true;
            queueShadowPets(self, arena);
        }
        EnemyAction summon = nextShadowPet(self, arena);
        if (summon != null) {
            moreToDo = true;
            return summon;
        }

        int distance = self.minDistanceTo(targetPos);
        boolean sight = Pathfinding.hasLineOfSight(arena, self.getGridPos(), targetPos);

        // A step can turn out to be impossible once the arena is looked at: no free tile to
        // blink to, nowhere to put a summon, no way through. That item or that movement is
        // written off and the turn planned again, rather than the turn being thrown away.
        for (int attempt = 0; attempt < MAX_REPLANS; attempt++) {
            List<ShadowItem> usable = usableItems();
            ShadowPlanner.Step step = ShadowPlanner.next(situation(self, distance, sight, usable));
            int distanceAfter = distance;
            boolean sightAfter = sight;
            EnemyAction action;

            switch (step.kind()) {
                case FOLLOW_UP -> action = owed.poll();
                case HEAL, BUFF, DEBUFF -> action = useItem(self, usable.get(step.index()), targetPos);
                case SPELL -> action = cast(self, arena, usable.get(step.index()), targetPos);
                case SUMMON -> action = summonWith(self, arena, usable.get(step.index()));
                case BLINK -> {
                    action = blinkWith(self, arena, usable.get(step.index()), targetPos);
                    if (action != null) {
                        distanceAfter = 1;
                        sightAfter = true;
                    }
                }
                case ATTACK -> {
                    ShadowWeapon weapon = kit.weapons().get(step.index());
                    apLeft -= weapon.apCost();
                    action = attackWith(self, weapon, targetPos);
                }
                case APPROACH -> {
                    ShadowWeapon weapon = kit.weapons().get(step.index());
                    List<GridPos> path = pathToward(self, arena, targetPos, weapon, moveLeft);
                    if (path.isEmpty()) {
                        // Boxed in. Spend the movement so the planner stops asking for it.
                        moveLeft = 0;
                        action = null;
                    } else {
                        moveLeft -= path.size();
                        GridPos end = path.get(path.size() - 1);
                        distanceAfter = end.manhattanDistance(targetPos);
                        sightAfter = Pathfinding.hasLineOfSight(arena, end, targetPos);
                        action = new EnemyAction.Move(path);
                    }
                }
                default -> {
                    return closeTurn();
                }
            }
            if (action == null) continue;

            actionsThisTurn++;
            // Look one step ahead, so a finished turn ends here rather than costing an idle beat.
            moreToDo = ShadowPlanner.next(situation(self, distanceAfter, sightAfter, usableItems()))
                .kind() != ShadowPlanner.Kind.END;
            return action;
        }
        return closeTurn();
    }

    private ShadowPlanner.Situation situation(CombatEntity self, int distance, boolean sight,
                                              List<ShadowItem> usable) {
        return new ShadowPlanner.Situation(apLeft, moveLeft, distance, sight, hpPercent(self),
            !owed.isEmpty(), actionsThisTurn, kit.weapons(), usable);
    }

    private void openTurn(CombatEntity self) {
        apLeft = kit.ap() + (isPhaseTwo() ? PHASE_TWO_BONUS_AP : 0);
        // Read off the entity rather than the kit, so Slowness and Soaked slow a Shadow down
        // the way they slow anything else.
        moveLeft = self.getMoveSpeed();
        actionsThisTurn = 0;
        owed.clear();
    }

    private EnemyAction closeTurn() {
        moreToDo = false;
        return new EnemyAction.Idle();
    }

    // ── Weapons ──────────────────────────────────────────────────────────────

    /**
     * Draw the weapon and swing, shoot or throw it. Only the size of the first hit is decided
     * here. Everything the weapon then does with it (who else it reaches, what it sets alight,
     * where it throws them) is the weapon's own code, run when the action resolves.
     */
    private EnemyAction attackWith(CombatEntity self, ShadowWeapon weapon, GridPos targetPos) {
        hold(self, weapon);
        int damage = ShadowBalance.hitDamage(self.getAttackPower(), weapon.apCost(),
            weapon.power(), kit.bestPower());
        pendingStrike = new PendingStrike(weapon, damage);
        return new EnemyAction.CustomAction(ShadowActions.STRIKE, List.of(targetPos), damage, null);
    }

    /** Put the chosen weapon in the Shadow's hand, so the swing is seen being made with it. */
    private void hold(CombatEntity self, ShadowWeapon weapon) {
        MobEntity mob = self.getMobEntity();
        if (mob == null) return;
        mob.equipStack(EquipmentSlot.MAINHAND, kit.stackAt(weapon.slot()));
    }

    // ── Items ─────────────────────────────────────────────────────────────────

    /** Food, a buff or a thrown potion: resolved by {@link ShadowActions} when the action fires. */
    private EnemyAction useItem(CombatEntity self, ShadowItem item, GridPos targetPos) {
        spend(item);
        ItemStack stack = kit.stackAt(item.slot());
        String name = stack.isEmpty() ? item.itemId() : stack.getName().getString();
        if (item.use() == ShadowItem.Use.DEBUFF && item.effect().isEmpty()) {
            // A potion that simply hurts lands as a one-tile burst, which already knows how to
            // find whichever party member is standing there.
            return new EnemyAction.TileAreaAttack(List.of(targetPos), targetPos, item.amount(),
                "a_harming_potion");
        }
        int amount = item.use() == ShadowItem.Use.HEAL ? healAmount(self, item) : item.amount();
        pendingItem = new PendingItem(item, amount, name);
        return new EnemyAction.CustomAction(ShadowActions.USE_ITEM, List.of(targetPos), amount, null);
    }

    /**
     * Throw something or cast an attacking sherd: damage over its area, then whatever else it
     * does. The damage and any status effect ride on one area attack, which already handles a
     * party standing in it. A shove or a drag follows as its own free action.
     */
    private EnemyAction cast(CombatEntity self, GridArena arena, ShadowItem item, GridPos targetPos) {
        spend(item);
        hold(self, item);
        GridPos centre = item.onSelf() ? self.getGridPos() : targetPos;
        List<GridPos> tiles = new ArrayList<>();
        for (GridPos tile : tilesAround(arena, centre, item.radius())) {
            if (tile.equals(self.getGridPos())) continue;
            // Never its own side: a burst that caught its own shadow pets would be a gift.
            CombatEntity occupant = arena.getOccupant(tile);
            if (occupant != null && occupant.isAlive() && !occupant.isAlly()) continue;
            tiles.add(tile);
        }
        if (tiles.isEmpty()) tiles.add(targetPos);

        int damage = ShadowBalance.hitDamage(self.getAttackPower(), item.apCost(), item.potency(), 100);
        if (item.lifesteal()) self.heal(Math.max(1, damage / 2));
        if (item.knockback() != 0) {
            int[] away = getDirectionToward(self.getGridPos(), targetPos);
            int sign = item.knockback() > 0 ? 1 : -1;
            owed.add(new EnemyAction.ForcedMovement(-1, away[0] * sign, away[1] * sign,
                Math.abs(item.knockback())));
        }
        return new EnemyAction.TileAreaAttack(tiles, centre, damage, areaTag(item));
    }

    /**
     * The tag an area attack carries: the name the chat line uses, then the status effect to
     * put on whoever it hits. {@code CombatManager.applyBossAreaEffect} reads the second half.
     */
    static String areaTag(ShadowItem item) {
        if (item.effect().isEmpty()) return item.label();
        return item.label() + ":rider:" + item.effect() + "," + item.turns() + "," + item.amount();
    }

    /** Appear beside the target, or null (and the item written off) when there is nowhere to land. */
    private EnemyAction blinkWith(CombatEntity self, GridArena arena, ShadowItem item, GridPos targetPos) {
        GridPos landing = AIUtils.findBestAdjacentTarget(arena, self.getGridPos(), targetPos, item.range());
        if (landing == null || landing.equals(self.getGridPos())
                || self.getGridPos().manhattanDistance(landing) > item.range()) {
            writeOff(item);
            return null;
        }
        spend(item);
        return new EnemyAction.Teleport(landing);
    }

    /** Seeker Vexes, or null (and the item written off) when there is nowhere to put them. */
    private EnemyAction summonWith(CombatEntity self, GridArena arena, ShadowItem item) {
        List<GridPos> spots = new ArrayList<>(findSummonPositionsNear(arena, self.getGridPos(), 2, item.amount()));
        if (spots.isEmpty()) spots = new ArrayList<>(findSummonPositions(arena, item.amount()));
        if (spots.isEmpty()) {
            writeOff(item);
            return null;
        }
        spend(item);
        return new EnemyAction.SummonMinions(SEEKER_TYPE, spots.size(), spots,
            SEEKER_HP, Math.max(1, self.getAttackPower() / 3), 0);
    }

    /** Show the thing being thrown or cast in the Shadow's hand. */
    private void hold(CombatEntity self, ShadowItem item) {
        MobEntity mob = self.getMobEntity();
        if (mob == null) return;
        ItemStack stack = kit.stackAt(item.slot());
        if (!stack.isEmpty()) mob.equipStack(EquipmentSlot.MAINHAND, stack);
    }

    private void spend(ShadowItem item) {
        apLeft -= item.apCost();
        usesBySlot.merge(item.slot(), 1, Integer::sum);
    }

    /** Use up an item that turned out to be unusable, at no AP cost, so it is not picked again. */
    private void writeOff(ShadowItem item) {
        usesBySlot.put(item.slot(), item.charges());
    }

    private List<ShadowItem> usableItems() {
        List<ShadowItem> usable = new ArrayList<>();
        for (ShadowItem item : kit.items()) {
            if (usesBySlot.getOrDefault(item.slot(), 0) < item.charges()) usable.add(item);
        }
        return usable;
    }

    /**
     * What a heal is worth to a Shadow. Food values are written for a player with 20 health,
     * so they are scaled up to the boss's pool, and capped so no single bite undoes a
     * quarter of the fight.
     */
    private static int healAmount(CombatEntity self, ShadowItem item) {
        int scaled = Math.max(1, item.amount() * self.getMaxHp() / 20);
        return Math.min(Math.max(1, self.getMaxHp() / 4), scaled);
    }

    // ── Movement ───────────────────────────────────────────────────────────────

    /** As far toward the target as {@code steps} allows, stopping where the weapon first connects. */
    private List<GridPos> pathToward(CombatEntity self, GridArena arena, GridPos targetPos,
                                     ShadowWeapon weapon, int steps) {
        if (steps <= 0) return List.of();
        GridPos from = self.getGridPos();
        GridPos dest = Pathfinding.findClosestReachableTo(arena, from, targetPos, steps, self);
        if (dest == null || dest.equals(from)) return List.of();
        List<GridPos> path = Pathfinding.findPathSized(arena, from, dest, steps, self);
        for (int i = 0; i < path.size(); i++) {
            GridPos tile = path.get(i);
            boolean sight = !weapon.ranged() || Pathfinding.hasLineOfSight(arena, tile, targetPos);
            if (weapon.reaches(tile.manhattanDistance(targetPos), sight)) {
                return new ArrayList<>(path.subList(0, i + 1));
            }
        }
        return path;
    }

    /** Every in-bounds tile within {@code radius} of {@code centre}, the centre included. */
    private static List<GridPos> tilesAround(GridArena arena, GridPos centre, int radius) {
        List<GridPos> tiles = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                GridPos tile = new GridPos(centre.x() + dx, centre.z() + dz);
                if (arena.isInBounds(tile)) tiles.add(tile);
            }
        }
        return tiles;
    }

    private static int hpPercent(CombatEntity self) {
        return self.getMaxHp() <= 0 ? 100 : self.getCurrentHp() * 100 / self.getMaxHp();
    }

    // ── Shadow pets ────────────────────────────────────────────────────────────

    /** Note down every pet this Shadow's player brought into the fight. */
    private void queueShadowPets(CombatEntity self, GridArena arena) {
        Set<Integer> seen = new HashSet<>();
        UUID owner = kit.ownerUuid();
        for (CombatEntity ally : arena.getOccupants().values()) {
            if (petsToCall.size() >= MAX_SHADOW_PETS) break;
            if (ally == null || !ally.isAlive() || !ally.isAlly() || !seen.add(ally.getEntityId())) continue;
            if (ally.isMountWall() || ally.isSeekerProjectile() || ally.getMobEntity() == null) continue;
            UUID petOwner = ally.getOwnerUuid();
            boolean mine = petOwner == null ? claimsUnownedPets : petOwner.equals(owner);
            if (!mine) continue;
            String typeId = Registries.ENTITY_TYPE.getId(ally.getMobEntity().getType()).toString();
            // A shadow of the pet as it stands, but never hitting harder than the Shadow itself.
            int attack = Math.max(1, Math.min(ally.getAttackPower(), self.getAttackPower()));
            petsToCall.add(new ShadowPet(typeId, Math.max(1, ally.getMaxHp()), attack,
                Math.max(0, ally.getDefense())));
        }
    }

    /**
     * The summon for the next pet on the list, or null when there are none left. The tile is
     * picked now rather than when the list was made: each shadow takes a tile as it arrives,
     * so choosing them all up front would stack every one on the same spot.
     */
    private EnemyAction nextShadowPet(CombatEntity self, GridArena arena) {
        if (petsToCall.isEmpty()) return null;
        ShadowPet pet = petsToCall.poll();
        List<GridPos> spot = findSummonPositionsNear(arena, self.getGridPos(), 2, 1);
        if (spot.isEmpty()) spot = findSummonPositions(arena, 1);
        if (spot.isEmpty()) {
            petsToCall.clear();
            return null;
        }
        return new EnemyAction.SummonMinions(pet.typeId(), 1, new ArrayList<>(spot),
            pet.hp(), pet.attack(), pet.defense());
    }
}
