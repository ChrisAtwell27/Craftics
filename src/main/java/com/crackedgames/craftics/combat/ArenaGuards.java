package com.crackedgames.craftics.combat;

import com.crackedgames.craftics.CrafticsMod;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
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
            // a status tick, or a boss ability - only a mob swinging on its own initiative.
            Entity attacker = source.getAttacker();
            if (attacker == null) attacker = source.getSource();
            if (!(attacker instanceof LivingEntity) && !(attacker instanceof MobEntity)) return true;
            if (attacker instanceof ServerPlayerEntity) return true;   // PvP is not this system's business

            CrafticsMod.LOGGER.debug("Refused off-turn {} damage to {} from {}",
                amount, player.getName().getString(), attacker.getType());
            return false;
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
}
