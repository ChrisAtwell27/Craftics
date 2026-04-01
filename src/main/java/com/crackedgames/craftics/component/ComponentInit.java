package com.crackedgames.craftics.component;

import net.minecraft.world.World;
import org.ladysnake.cca.api.v3.entity.EntityComponentFactoryRegistry;
import org.ladysnake.cca.api.v3.entity.EntityComponentInitializer;
import org.ladysnake.cca.api.v3.entity.RespawnCopyStrategy;
import org.ladysnake.cca.api.v3.world.WorldComponentFactoryRegistry;
import org.ladysnake.cca.api.v3.world.WorldComponentInitializer;

/**
 * CCA entrypoint — registers component factories for players and worlds.
 */
public class ComponentInit implements EntityComponentInitializer, WorldComponentInitializer {

    @Override
    public void registerEntityComponentFactories(EntityComponentFactoryRegistry registry) {
        // Player progression: survives death, dimension changes, everything
        registry.registerForPlayers(
            CrafticsComponents.PLAYER_PROGRESSION,
            player -> new PlayerProgressionComponent(),
            RespawnCopyStrategy.ALWAYS_COPY
        );

        registry.registerForPlayers(
            CrafticsComponents.DEATH_PROTECTION,
            player -> new DeathProtectionComponent(),
            RespawnCopyStrategy.ALWAYS_COPY
        );
    }

    @Override
    public void registerWorldComponentFactories(WorldComponentFactoryRegistry registry) {
        // World data: only on overworld
        registry.registerFor(
            World.OVERWORLD,
            CrafticsComponents.WORLD_DATA,
            world -> new WorldDataComponent()
        );
    }
}
