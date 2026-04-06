package com.crackedgames.craftics.combat;

import com.crackedgames.craftics.CrafticsMod;
import com.crackedgames.craftics.world.CrafticsSavedData;
import net.minecraft.entity.Entity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.passive.AbstractHorseEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Scans the hub world for vanilla-tamed animals that are following the player
 * and collects them for combat. Handles restoring surviving pets to the hub
 * after a biome run ends.
 */
public class HubPetCollector {

    /** Max pets that can join a single combat run. */
    public static final int MAX_COMBAT_PETS = 4;

    /** Hub yard bounds relative to hub origin. */
    private static final int YARD_MIN_X = -30, YARD_MAX_X = 30;
    private static final int YARD_MIN_Z = -20, YARD_MAX_Z = 40;
    private static final int SCAN_HEIGHT = 20;

    /** Vanilla entity types that qualify as tameable combat pets. */
    private static final Set<String> TAMEABLE_TYPES = Set.of(
        "minecraft:wolf", "minecraft:cat", "minecraft:parrot",
        "minecraft:horse", "minecraft:donkey", "minecraft:mule",
        "minecraft:skeleton_horse", "minecraft:zombie_horse",
        "minecraft:camel", "minecraft:llama"
    );

    /** Snapshot of a tamed pet collected from the hub before combat. */
    public record TamedPetSnapshot(
        String entityTypeId,
        UUID entityUuid,
        NbtCompound fullEntityNbt,
        PetStats.Stats combatStats,
        UUID playerUuid
    ) {}

    /**
     * Scan the hub area for the player's tamed, following animals.
     * Returns up to {@link #MAX_COMBAT_PETS} snapshots.
     * The matching entities are discarded from the world.
     */
    public static List<TamedPetSnapshot> collectFollowingPets(
            ServerWorld world, ServerPlayerEntity player, CrafticsSavedData data) {

        BlockPos hubOrigin = data.getHubOrigin(player.getUuid());
        if (hubOrigin == null) return List.of();

        int hx = hubOrigin.getX(), hy = hubOrigin.getY(), hz = hubOrigin.getZ();
        Box scanBox = new Box(
            hx + YARD_MIN_X, hy - 3, hz + YARD_MIN_Z,
            hx + YARD_MAX_X, hy + SCAN_HEIGHT, hz + YARD_MAX_Z
        );

        UUID ownerUuid = player.getUuid();
        List<TamedPetSnapshot> results = new ArrayList<>();
        List<Entity> toDiscard = new ArrayList<>();

        for (AnimalEntity animal : world.getEntitiesByClass(AnimalEntity.class, scanBox, e -> true)) {
            if (results.size() >= MAX_COMBAT_PETS) break;

            String typeId = Registries.ENTITY_TYPE.getId(animal.getType()).toString();
            if (!TAMEABLE_TYPES.contains(typeId)) continue;

            // Check ownership and follow state
            if (animal instanceof TameableEntity tameable) {
                if (!tameable.isTamed()) continue;
                if (!ownerUuid.equals(tameable.getOwnerUuid())) continue;
                if (tameable.isSitting()) continue; // sitting = stay home
            } else if (animal instanceof AbstractHorseEntity horse) {
                if (!horse.isTame()) continue;
                if (!ownerUuid.equals(horse.getOwnerUuid())) continue;
                // Horses don't sit, they're always "following"
            } else {
                continue; // Unknown tameable type
            }

            // Capture full NBT snapshot
            NbtCompound nbt = new NbtCompound();
            animal.writeNbt(nbt);

            PetStats.Stats stats = PetStats.get(typeId);
            results.add(new TamedPetSnapshot(typeId, animal.getUuid(), nbt, stats, ownerUuid));
            toDiscard.add(animal);

            CrafticsMod.LOGGER.info("Collected following pet for combat: {} ({})",
                animal.getName().getString(), typeId);
        }

        // Remove collected pets from the hub world
        for (Entity e : toDiscard) {
            e.discard();
        }

        return results;
    }

    /**
     * Restore surviving pets to the hub world after a biome run ends.
     * Recreates entities from their original NBT snapshots with updated positions.
     */
    public static void restorePetsToHub(ServerWorld world, ServerPlayerEntity player,
                                         List<PetData> survivingPets, CrafticsSavedData data) {
        BlockPos hubPos = data.getHubTeleportPos(player.getUuid());
        if (hubPos == null) return;

        int offset = 0;
        for (PetData pet : survivingPets) {
            offset++;
            try {
                if (pet.originalNbt() != null) {
                    // Restore from original NBT (preserves collar color, armor, name, variant)
                    NbtCompound nbt = pet.originalNbt().copy();
                    // Override position to hub
                    double px = hubPos.getX() + offset + 0.5;
                    double py = hubPos.getY();
                    double pz = hubPos.getZ() + 0.5;

                    // Remove old UUID so a fresh one is assigned (prevents duplicates)
                    nbt.remove("UUID");

                    var entityType = Registries.ENTITY_TYPE.get(Identifier.of(pet.entityType()));
                    Entity restored = entityType.create(world, null, BlockPos.ofFloored(px, py, pz),
                        SpawnReason.MOB_SUMMONED, false, false);

                    if (restored != null) {
                        restored.readNbt(nbt);
                        restored.refreshPositionAndAngles(px, py, pz, 0, 0);
                        restored.setVelocity(0, 0, 0);
                        if (restored instanceof net.minecraft.entity.mob.MobEntity mob) {
                            mob.setPersistent();
                            mob.setAiDisabled(false);
                            mob.setSilent(false);
                        }
                        world.spawnEntity(restored);
                        CrafticsMod.LOGGER.info("Restored pet to hub: {} at ({}, {}, {})",
                            pet.entityType(), (int) px, (int) py, (int) pz);
                    }
                } else {
                    // Fallback: create a fresh entity (no NBT to restore)
                    var entityType = Registries.ENTITY_TYPE.get(Identifier.of(pet.entityType()));
                    var rawEntity = entityType.create(world, null,
                        BlockPos.ofFloored(hubPos.getX() + offset + 0.5, hubPos.getY(), hubPos.getZ() + 0.5),
                        SpawnReason.MOB_SUMMONED, false, false);
                    if (rawEntity instanceof net.minecraft.entity.mob.MobEntity mob) {
                        mob.setPersistent();
                        mob.setAiDisabled(false);
                        // Try to set tamed state
                        if (mob instanceof TameableEntity tameable) {
                            tameable.setOwnerUuid(player.getUuid());
                            tameable.setTamed(true, false);
                        } else if (mob instanceof AbstractHorseEntity horse) {
                            horse.setOwnerUuid(player.getUuid());
                            horse.bondWithPlayer(player);
                        }
                        world.spawnEntity(mob);
                    }
                }
            } catch (Exception e) {
                CrafticsMod.LOGGER.error("Failed to restore pet {} to hub: {}",
                    pet.entityType(), e.getMessage());
            }
        }
    }

    /**
     * Extended pet data record that includes original NBT for hub restoration.
     * Replaces the old PetData record in CombatManager.
     */
    public record PetData(String entityType, int hp, int maxHp, int atk, int def, int speed, int range,
                          @org.jetbrains.annotations.Nullable NbtCompound originalNbt) {

        /** Create from a TamedPetSnapshot (first level entry). */
        public static PetData fromSnapshot(TamedPetSnapshot snapshot) {
            var s = snapshot.combatStats();
            return new PetData(snapshot.entityTypeId(), s.hp(), s.hp(), s.atk(), s.def(), s.speed(), s.range(),
                snapshot.fullEntityNbt());
        }

        /** Create from a surviving combat entity (between levels). */
        public static PetData fromCombatEntity(CombatEntity e, @org.jetbrains.annotations.Nullable NbtCompound originalNbt) {
            return new PetData(e.getEntityTypeId(), e.getCurrentHp(), e.getMaxHp(),
                e.getAttackPower(), e.getDefense(), e.getMoveSpeed(), e.getRange(), originalNbt);
        }
    }
}
