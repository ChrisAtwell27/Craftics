package com.crackedgames.craftics.combat;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.world.ServerWorld;

/**
 * 1.21.5+ mob variant assignment. Assigns Spring to Life cow/pig/chicken
 * variants (temperate/warm/cold) based on biome climate. No-op on older
 * versions where the variant system does not exist.
 */
public final class VariantHelper {
    private VariantHelper() {}

    public enum Climate { TEMPERATE, WARM, COLD }

    /**
     * Map a Craftics biome ID to the climate used for its passive variant spawns.
     * Biome IDs here match the JSON files under {@code data/craftics/craftics/biomes/}.
     */
    public static Climate climateFor(String biomeId) {
        if (biomeId == null) return Climate.TEMPERATE;
        switch (biomeId) {
            case "desert":
            case "jungle":
            case "nether_wastes":
            case "basalt_deltas":
            case "crimson_forest":
                return Climate.WARM;
            case "snowy":
            case "mountain":
            case "deep_dark":
                return Climate.COLD;
            default:
                return Climate.TEMPERATE;
        }
    }

    /**
     * Apply the Spring to Life variant matching the given climate to a freshly
     * spawned cow/pig/chicken. No-op on any other mob type or on &lt;=1.21.4.
     */
    public static void applyVariant(MobEntity mob, ServerWorld world, String biomeId) {
        //? if >=1.21.5 {
        /*Climate climate = climateFor(biomeId);
        net.minecraft.registry.DynamicRegistryManager drm = world.getRegistryManager();
        if (mob instanceof net.minecraft.entity.passive.CowEntity cow) {
            net.minecraft.registry.RegistryKey<net.minecraft.entity.passive.CowVariant> key = switch (climate) {
                case WARM -> net.minecraft.entity.passive.CowVariants.WARM;
                case COLD -> net.minecraft.entity.passive.CowVariants.COLD;
                case TEMPERATE -> net.minecraft.entity.passive.CowVariants.TEMPERATE;
            };
            drm.getOrThrow(net.minecraft.registry.RegistryKeys.COW_VARIANT)
                .getEntry(key.getValue()).ifPresent(cow::setVariant);
        } else if (mob instanceof net.minecraft.entity.passive.ChickenEntity chicken) {
            net.minecraft.registry.RegistryKey<net.minecraft.entity.passive.ChickenVariant> key = switch (climate) {
                case WARM -> net.minecraft.entity.passive.ChickenVariants.WARM;
                case COLD -> net.minecraft.entity.passive.ChickenVariants.COLD;
                case TEMPERATE -> net.minecraft.entity.passive.ChickenVariants.TEMPERATE;
            };
            drm.getOrThrow(net.minecraft.registry.RegistryKeys.CHICKEN_VARIANT)
                .getEntry(key.getValue()).ifPresent(chicken::setVariant);
        } else if (mob instanceof net.minecraft.entity.passive.PigEntity pig) {
            // PigEntity.setVariant is package-private/private — go through NBT.
            net.minecraft.registry.RegistryKey<net.minecraft.entity.passive.PigVariant> key = switch (climate) {
                case WARM -> net.minecraft.entity.passive.PigVariants.WARM;
                case COLD -> net.minecraft.entity.passive.PigVariants.COLD;
                case TEMPERATE -> net.minecraft.entity.passive.PigVariants.TEMPERATE;
            };
            drm.getOrThrow(net.minecraft.registry.RegistryKeys.PIG_VARIANT).getEntry(key.getValue()).ifPresent((net.minecraft.registry.entry.RegistryEntry<net.minecraft.entity.passive.PigVariant> entry) -> {
                net.minecraft.nbt.NbtCompound tag = new net.minecraft.nbt.NbtCompound();
                pig.writeCustomDataToNbt(tag);
                net.minecraft.entity.Variants.writeVariantToNbt(tag, entry);
                pig.readCustomDataFromNbt(tag);
            });
        }
        *///?}
    }
}
