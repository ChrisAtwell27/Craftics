package com.crackedgames.craftics.combat;

import com.crackedgames.craftics.CrafticsMod;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.util.ActionResult;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Keeps a grid fight the only thing that can hurt anyone inside it.
 *
 * <p>Arena mobs are spawned with {@code setAiDisabled(true)} so they hold still between
 * turns. That is not a guarantee. {@code NoAI} only stops vanilla's goal ticking, and a
 * modded mob whose behaviour lives in its own {@code tick} override runs regardless - the
 * Deeper and Darker Stalker does exactly this, going invisible and mauling the player in
 * real time between turns while spawning sculk leeches, none of which the turn system knows
 * anything about.
 *
 * <p>Rather than chase each mob that escapes, this closes the door the escapes come through.
 * Every point of damage the combat system deals is applied straight to health
 * ({@code CombatManager.damagePlayer} and friends), never through a damage source, so while a
 * player is in a fight ANY entity-sourced damage reaching them is by definition something the
 * grid did not sanction. It is refused.
 */
public final class ArenaGuards {

    private ArenaGuards() {}

    public static void register() {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (!(entity instanceof ServerPlayerEntity player)) return true;
            if (!CombatManager.isEngaged(player.getUuid())) return true;

            // Only entity-sourced damage is refused. Anything the mod applies itself goes
            // directly to health and never arrives here, so this cannot swallow a hazard,
            // a status tick, or a boss ability - only something swinging on its own initiative.
            Entity attacker = source.getAttacker();
            if (attacker == null) attacker = source.getSource();
            // No entity behind it at all: fall, void, suffocation, a status tick. Craftics owns
            // those itself, so they are left alone.
            if (attacker == null) return true;
            if (attacker instanceof ServerPlayerEntity) return true;   // PvP is not this system's business
            // ANY other entity, not just a living one. This used to require LivingEntity or
            // MobEntity, and that hole is what let evoker fangs through: EvokerFangsEntity is a
            // plain Entity, so it sailed past this check and bit a player who had already been
            // clamped to 1 HP for their death animation, killing them for real mid-fight. Area
            // effect clouds, arrows with no shooter and falling blocks all have the same shape.

            CrafticsMod.LOGGER.debug("Refused off-turn {} damage to {} from {}",
                amount, player.getName().getString(), attacker.getType());
            return false;
        });

        // ── A fight may never end in a vanilla death ────────────────────────────
        //
        // Craftics owns death inside an arena: the animation, the totem, the party hand-off, the
        // defeat screen and the run teardown all hang off its own path. A vanilla death runs none
        // of it - the death screen opens over a fight that is still live, and the player respawns
        // into a run with no way back in. There is no recovery from that; it is a softlock.
        //
        // Refusing the death outright and handing it to the mod turns the worst outcome into a
        // normal defeat. It is a backstop rather than a fix: anything it catches got damage past
        // the metered path, which is a bug in its own right, so it logs the cause by name.
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
            if (!(entity instanceof ServerPlayerEntity player)) return true;
            if (!CombatManager.isEngaged(player.getUuid())) return true;
            CombatManager cm = CombatManager.getActiveCombat(player.getUuid());
            if (cm == null || !cm.isActive()) return true;
            cm.takeOverRealDeath(player, source.getName());
            return false;   // the vanilla death does not happen
        });

        // The same rule for the party's allies: a pet standing on the grid takes what the
        // combat system gives it and nothing else, so an unsanctioned mob cannot chew
        // through someone's wolf while it waits for its turn.
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (entity instanceof ServerPlayerEntity) return true;
            if (!entity.getCommandTags().contains("craftics_arena")) return true;
            Entity attacker = source.getAttacker();
            return !(attacker instanceof MobEntity);
        });

        // ── The arena is not a build surface ─────────────────────────────────────
        //
        // Nothing stopped a player editing the floor they were fighting on. A shovel turns
        // grass into a dirt path with a right-click, an axe strips a log, a hoe tills - all
        // vanilla interactions that never touch Craftics' own item handling, so none of them
        // were refused. And the arena restore only reverts blocks CRAFTICS placed, so a
        // player-made block was never put back: it survived the fight, and it was still there
        // the next time that arena slot was built, on every future run.
        //
        // Only right-clicks that TRANSFORM a block are refused, matched on the tool doing it.
        // A blanket refusal would be wrong: Craftics' own combat items (TNT, beds, blue ice,
        // an end crystal) are placed through the mod's action packets and a player also
        // physically right-clicks to use them.
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (world.isClient() || !(player instanceof ServerPlayerEntity sp)) return ActionResult.PASS;
            if (!transformsBlocks(sp.getStackInHand(hand).getItem())) return ActionResult.PASS;
            return insideOwnArena(sp, hit.getBlockPos()) ? ActionResult.FAIL : ActionResult.PASS;
        });

        // Breaking it is the same problem arriving the other way round.
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, be) ->
            !(player instanceof ServerPlayerEntity sp) || !insideOwnArena(sp, pos));

        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) ->
            player instanceof ServerPlayerEntity sp && insideOwnArena(sp, pos)
                ? ActionResult.FAIL : ActionResult.PASS);

        // NO entity-removal guard here, deliberately.
        //
        // There was one: it discarded any mob that appeared inside a live arena without the
        // craftics_arena tag, meant to clear up minions a modded mob summoned behind the turn
        // system's back. It deleted every enemy in every fight instead. Not all spawn paths
        // add the tag before world.spawnEntity, so ENTITY_LOAD fired first, saw an untagged
        // mob standing in an arena, and threw the fight's own enemies away as they arrived.
        //
        // Anything attempting this again has to key off something true at spawn time - the
        // grid's own occupant list, for instance - rather than a tag that may not be attached
        // yet. The damage guard above already removes the harm without touching entities.
    }

    /**
     * Vanilla tools whose right-click changes the block underneath.
     *
     * <p>Shovel makes a dirt path, axe strips and scrapes, hoe tills. These are the whole of
     * the "a player permanently altered the arena floor" problem, and matching on them keeps
     * every other right-click - including every Craftics combat item - untouched.
     */
    private static boolean transformsBlocks(net.minecraft.item.Item item) {
        return item instanceof net.minecraft.item.ShovelItem
            || item instanceof net.minecraft.item.AxeItem
            || item instanceof net.minecraft.item.HoeItem;
    }

    /**
     * True when {@code pos} lies inside the arena of the fight this player is currently in.
     *
     * <p>Scoped to their OWN live arena rather than to "anywhere in an island dimension":
     * outside a fight the island is theirs to dig up, and that should stay true.
     */
    private static boolean insideOwnArena(ServerPlayerEntity player, net.minecraft.util.math.BlockPos pos) {
        CombatManager cm = CombatManager.getActiveCombat(player.getUuid());
        if (cm == null || !cm.isActive()) return false;
        com.crackedgames.craftics.core.GridArena arena = cm.getArena();
        if (arena == null) return false;

        net.minecraft.util.math.BlockPos origin = arena.getOrigin();
        int dx = pos.getX() - origin.getX();
        int dz = pos.getZ() - origin.getZ();
        if (dx < 0 || dz < 0 || dx >= arena.getWidth() || dz >= arena.getHeight()) return false;
        // A generous vertical band: schematic arenas carve below the floor and build well
        // above it, and the point is to protect the whole structure, not one layer of it.
        int dy = pos.getY() - origin.getY();
        return dy >= -8 && dy <= 32;
    }
}
