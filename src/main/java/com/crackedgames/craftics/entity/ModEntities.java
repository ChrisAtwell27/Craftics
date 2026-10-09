package com.crackedgames.craftics.entity;

import com.crackedgames.craftics.CrafticsMod;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

/** Entity types Craftics adds. There is one: the Shadow, because it needs a player's body. */
public final class ModEntities {

    private ModEntities() {}

    /** The id biome JSON uses to ask for a Shadow as its boss. */
    public static final String SHADOW_CLONE_TYPE_ID = CrafticsMod.MOD_ID + ":shadow_clone";

    private static final Identifier SHADOW_CLONE_ID = Identifier.of(CrafticsMod.MOD_ID, "shadow_clone");
    private static final RegistryKey<EntityType<?>> SHADOW_CLONE_KEY =
        RegistryKey.of(RegistryKeys.ENTITY_TYPE, SHADOW_CLONE_ID);

    // MISC rather than MONSTER: it is never spawned by the world, and MISC keeps it out of the
    // mob cap and out of anything that treats "a monster is nearby" as meaningful.
    public static final EntityType<ShadowCloneEntity> SHADOW_CLONE = Registry.register(
        Registries.ENTITY_TYPE, SHADOW_CLONE_ID,
        EntityType.Builder.<ShadowCloneEntity>create(ShadowCloneEntity::new, SpawnGroup.MISC)
            .dimensions(0.6f, 1.8f)
            .maxTrackingRange(10)
            //? if <=1.21.1 {
            .build("shadow_clone")
            //?} else {
            /*.build(SHADOW_CLONE_KEY)
            *///?}
    );

    public static void register() {
        FabricDefaultAttributeRegistry.register(SHADOW_CLONE, ShadowCloneEntity.createAttributes());
    }
}
