package com.crackedgames.craftics.combat.sherd;

import com.crackedgames.craftics.combat.CombatEffects;
import com.crackedgames.craftics.combat.CombatEntity;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * Everything a spell effect is allowed to touch while it runs.
 *
 * <p>Every {@code useXSherd} method used to take its own hand-picked subset of these as
 * positional parameters - seven of them, in a different order and a different combination per
 * sherd, because each one was written for exactly one spell. Adding a capability to a sherd
 * meant changing its signature and its call in the dispatch chain, which is precisely the cost
 * that kept the sherds from being composable. One context object means an effect can reach for
 * anything without the pipeline knowing in advance which effects want what.
 *
 * <p>Deliberately mutable in one direction only: effects read the world and write to the game
 * state through the entity/arena APIs, but the context's own fields are fixed for the cast.
 */
public final class SpellContext {

    private final ServerPlayerEntity caster;
    private final GridArena arena;
    private final ServerWorld world;
    private final List<CombatEntity> combatants;
    private final CombatEffects casterEffects;
    private final ItemStack sherdStack;
    private final GridPos targetTile;
    private final GridPos casterPos;
    private final SherdSpell spell;

    /**
     * Out-of-band instructions for CombatManager, drained after the cast.
     *
     * <p>Three of the sherds cannot finish their own work: only CombatManager can put entities
     * on the grid (Archer's seekers), register a tile effect (Danger's trap), or set the
     * double-damage flag (Prize). Those have always been expressed as magic string prefixes on
     * the returned message, parsed back apart at the far end. The prefixes are kept for
     * compatibility with that parser, but they are collected here rather than concatenated by
     * hand inside a spell, so an effect that emits one does not have to know it must land at
     * the very front of the string.
     */
    private final List<String> directives = new ArrayList<>();

    public SpellContext(ServerPlayerEntity caster, GridArena arena, ServerWorld world,
                        List<CombatEntity> combatants, CombatEffects casterEffects,
                        ItemStack sherdStack, GridPos targetTile, SherdSpell spell) {
        this.caster = caster;
        this.arena = arena;
        this.world = world;
        this.combatants = combatants;
        this.casterEffects = casterEffects;
        this.sherdStack = sherdStack;
        this.targetTile = targetTile;
        this.casterPos = arena.getPlayerGridPos();
        this.spell = spell;
    }

    public ServerPlayerEntity caster() { return caster; }
    public GridArena arena() { return arena; }
    public ServerWorld world() { return world; }
    public List<CombatEntity> combatants() { return combatants; }
    public CombatEffects casterEffects() { return casterEffects; }
    public ItemStack sherdStack() { return sherdStack; }
    public SherdSpell spell() { return spell; }

    /** The tile the player aimed at, or the caster's own tile for a self-cast. */
    public GridPos targetTile() { return targetTile != null ? targetTile : casterPos; }

    public GridPos casterPos() { return casterPos; }

    public BlockPos casterBlock() { return arena.gridToBlockPos(casterPos); }

    public BlockPos blockOf(GridPos pos) { return arena.gridToBlockPos(pos); }

    /** Block position of an entity, resolved through the arena so multi-tile mobs stay centred. */
    public BlockPos blockOf(CombatEntity entity) { return arena.gridToBlockPos(entity.getGridPos()); }

    /**
     * The caster's live pets - every ally EXCEPT seeker projectiles, which are a spell's
     * payload rather than a companion.
     *
     * <p>Without that exclusion, casting Archer and then Friend or Howl would heal and detonate
     * your own vexes. Lifted verbatim from the old {@code livePets} helper, which every
     * pet-facing sherd already had to remember to call.
     */
    public List<CombatEntity> livePets() {
        List<CombatEntity> pets = new ArrayList<>();
        for (CombatEntity e : combatants) {
            if (!e.isAlive() || !e.isAlly()) continue;
            if (e.isSeekerProjectile()) continue;
            pets.add(e);
        }
        return pets;
    }

    /** Queue an out-of-band instruction for CombatManager. See {@link #directives}. */
    public void addDirective(String directive) { directives.add(directive); }

    public List<String> directives() { return directives; }
}
