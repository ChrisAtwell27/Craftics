package com.crackedgames.craftics.component;

import net.minecraft.util.Identifier;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistryV3;

/**
 * Holds all Cardinal Components keys for Craftics.
 */
public final class CrafticsComponents {

    /** Player progression: level, stat points, allocated stats. Attached to each player. */
    public static final ComponentKey<PlayerProgressionComponent> PLAYER_PROGRESSION =
        ComponentRegistryV3.INSTANCE.getOrCreate(
            Identifier.of("craftics", "progression"),
            PlayerProgressionComponent.class
        );

    /** One-shot death protection state used by the recovery compass. */
    public static final ComponentKey<DeathProtectionComponent> DEATH_PROTECTION =
        ComponentRegistryV3.INSTANCE.getOrCreate(
            Identifier.of("craftics", "death_protection"),
            DeathProtectionComponent.class
        );

    /** World-level game data: emeralds, biome progress, NG+. Attached to the overworld. */
    public static final ComponentKey<WorldDataComponent> WORLD_DATA =
        ComponentRegistryV3.INSTANCE.getOrCreate(
            Identifier.of("craftics", "world_data"),
            WorldDataComponent.class
        );

    /** Per-mob animation state driving client-side bone overrides for craftics_arena entities. */
    public static final ComponentKey<CrafticsAnimComponent> ANIM =
        ComponentRegistryV3.INSTANCE.getOrCreate(
            Identifier.of("craftics", "anim"),
            CrafticsAnimComponent.class
        );

    private CrafticsComponents() {}
}
