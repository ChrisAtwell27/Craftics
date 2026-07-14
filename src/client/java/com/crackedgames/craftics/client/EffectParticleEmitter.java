package com.crackedgames.craftics.client;

import com.crackedgames.craftics.combat.EffectIcons;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.random.Random;

import java.util.List;
import java.util.Locale;

/**
 * Ambient particles around combatants carrying a physically obvious status effect: fire licks
 * off a burning mob, poison hangs in a green haze, a soaked one drips, and so on.
 *
 * <p>Purely cosmetic and driven entirely off the synced combat state, so the particles last
 * exactly as long as the effect does. Effects with no natural physical read (Marked, Exposed,
 * Haste) deliberately emit nothing - particles on all two dozen effects would bury the arena
 * and cost framerate for no added clarity. {@link EffectIcons#hasParticles} owns that list.
 *
 * @since 0.3.0
 */
public final class EffectParticleEmitter {

    private EffectParticleEmitter() {}

    /**
     * Emit every N client ticks rather than every tick. At 20 tps a burning mob still reads as
     * continuously alight, but a crowded arena spawns a fraction of the particles.
     */
    private static final int EMIT_INTERVAL_TICKS = 3;

    /** Don't emit for combatants beyond this distance (squared) - they're specks anyway. */
    private static final double MAX_DIST_SQ = 32 * 32;

    private static final Random RANDOM = Random.create();

    private static int tickCounter;

    /** Register the client tick callback. Called once from client init. */
    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(EffectParticleEmitter::onClientTick);
    }

    private static void onClientTick(MinecraftClient client) {
        if (client.world == null || client.player == null) return;
        if (!CombatState.isInCombat()) return;
        if (++tickCounter % EMIT_INTERVAL_TICKS != 0) return;

        var enemyTypes = CombatState.getEnemyTypeMap();
        var allyTypes = CombatState.getAllyTypeMap();

        for (Entity entity : client.world.getEntities()) {
            if (entity.squaredDistanceTo(client.player) > MAX_DIST_SQ) continue;

            for (String effect : effectsFor(entity, enemyTypes, allyTypes)) {
                if (!EffectIcons.hasParticles(effect)) continue;
                ParticleEffect particle = particleFor(effect);
                if (particle != null) emitAround(client, entity, particle);
            }
        }
    }

    private static List<String> effectsFor(Entity entity,
                                           java.util.Map<Integer, String> enemyTypes,
                                           java.util.Map<Integer, String> allyTypes) {
        if (entity instanceof PlayerEntity player) {
            return EffectIcons.parsePlayerEffects(playerEffectString(player));
        }
        String blob = enemyTypes.get(entity.getId());
        if (blob == null) blob = allyTypes.get(entity.getId());
        if (blob == null) return List.of();
        return EffectIcons.parseEnemyEffects(blob);
    }

    private static String playerEffectString(PlayerEntity player) {
        for (CombatState.PartyMemberHp member : CombatState.getPartyHpList()) {
            if (player.getUuid().toString().equals(member.uuid())) {
                return member.dead() ? "" : member.effects();
            }
        }
        MinecraftClient client = MinecraftClient.getInstance();
        boolean isLocal = client.player != null && client.player.getUuid().equals(player.getUuid());
        return isLocal ? CombatState.getPlayerEffects() : "";
    }

    /**
     * The particle that visually reads as this effect, or null if it emits none.
     *
     * <p>All of these are simple, argument-free particle types on purpose: the parameterized
     * ones (ENTITY_EFFECT and its color argument, DUST and its scale) changed shape across the
     * supported Minecraft versions, and none of them buy enough here to be worth a per-version
     * split. ITEM_SLIME is the green blob that reads as venom.
     */
    private static ParticleEffect particleFor(String rawName) {
        return switch (EffectIcons.baseName(rawName)) {
            case "burning", "burn"              -> ParticleTypes.FLAME;
            case "poison", "poisoned"           -> ParticleTypes.ITEM_SLIME;      // green venom
            case "wither", "withered"           -> ParticleTypes.SMOKE;           // black decay
            case "bleeding", "bleed"            -> ParticleTypes.DAMAGE_INDICATOR;
            case "soaked"                       -> ParticleTypes.DRIPPING_WATER;
            case "frozen"                       -> ParticleTypes.SNOWFLAKE;
            case "enraged"                      -> ParticleTypes.ANGRY_VILLAGER;
            case "regeneration", "regenerating" -> ParticleTypes.HAPPY_VILLAGER;
            default                             -> null;
        };
    }

    /**
     * Spawn one particle at a random point inside the entity's bounding box, so the effect
     * clings to the whole body rather than pinning to a single spot.
     */
    private static void emitAround(MinecraftClient client, Entity entity, ParticleEffect particle) {
        double w = entity.getWidth();
        double h = entity.getHeight();
        double x = entity.getX() + (RANDOM.nextDouble() - 0.5) * w * 1.2;
        double y = entity.getY() + RANDOM.nextDouble() * h;
        double z = entity.getZ() + (RANDOM.nextDouble() - 0.5) * w * 1.2;
        // Slight upward drift so the particles rise off the body instead of hanging still.
        //? if <=1.21.4 {
        client.world.addParticle(particle, x, y, z, 0.0, 0.02, 0.0);
        //?} else
        /*client.world.addParticleClient(particle, x, y, z, 0.0, 0.02, 0.0);*/
    }
}
