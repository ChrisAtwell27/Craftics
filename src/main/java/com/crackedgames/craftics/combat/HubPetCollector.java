package com.crackedgames.craftics.combat;

import com.crackedgames.craftics.CrafticsMod;
import com.crackedgames.craftics.api.registry.AllyEntry;
import com.crackedgames.craftics.api.registry.AllyRegistry;
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
import java.util.UUID;

/**
 * Scans the hub yard for registered allies ({@link AllyEntry} in {@code AllyRegistry})
 * and collects them for combat. {@code TAMED} allies must be tamed and owned by the
 * player; {@code BUILT} allies qualify by entity type alone. Handles restoring
 * surviving allies to the hub after a biome run ends.
 */
public class HubPetCollector {

    /** Max pets that can join a single combat run. */
    public static final int MAX_COMBAT_PETS = 4;

    /** Hub yard bounds relative to hub origin. */
    private static final int YARD_MIN_X = -30, YARD_MAX_X = 30;
    private static final int YARD_MIN_Z = -20, YARD_MAX_Z = 40;
    private static final int SCAN_HEIGHT = 20;

    /** Snapshot of a tamed pet collected from the hub before combat. */
    public record TamedPetSnapshot(
        String entityTypeId,
        UUID entityUuid,
        NbtCompound fullEntityNbt,
        AllyEntry allyEntry,
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
            AllyEntry allyEntry = AllyRegistry.getOrNull(typeId);
            if (allyEntry == null) continue; // not a registered ally — stays home
            if (allyEntry.recruitMode() == AllyEntry.RecruitMode.IN_COMBAT_ONLY) {
                continue; // registered for combat stats only — never recruited from the hub
            }

            // TAMED allies must be tamed + owned by this player and not sitting.
            // BUILT allies (golems) qualify on type alone.
            if (allyEntry.recruitMode() == AllyEntry.RecruitMode.TAMED) {
                if (animal instanceof TameableEntity tameable) {
                    if (!tameable.isTamed()) continue;
                    //? if <=1.21.4 {
                    if (!ownerUuid.equals(tameable.getOwnerUuid())) continue;
                    //?} else {
                    /*var ref = tameable.getOwnerReference();
                    if (ref == null || !ownerUuid.equals(ref.getUuid())) continue;
                    *///?}
                    if (tameable.isSitting()) continue; // sitting = stay home
                } else if (animal instanceof AbstractHorseEntity horse) {
                    if (!horse.isTame()) continue;
                    //? if <=1.21.4 {
                    if (!ownerUuid.equals(horse.getOwnerUuid())) continue;
                    //?} else {
                    /*var ref = horse.getOwnerReference();
                    if (ref == null || !ownerUuid.equals(ref.getUuid())) continue;
                    *///?}
                    // Horses don't sit, they're always "following"
                } else {
                    continue; // TAMED entry but not a tameable entity class
                }
            }
            // recruitMode == BUILT: no taming/ownership check.

            // Capture full NBT snapshot
            NbtCompound nbt = new NbtCompound();
            animal.writeNbt(nbt);

            results.add(new TamedPetSnapshot(typeId, animal.getUuid(), nbt, allyEntry, ownerUuid));
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
     * Scan up then down from {@code hub.y} for a solid block with air above, so
     * pets don't spawn below the hub island or inside a block. Matches the
     * safe-landing logic used by CombatManager.teleportToHub / CrafticsMod.teleportToHub.
     */
    private static int findSafeHubY(ServerWorld world, BlockPos hub, int dx, int dz) {
        BlockPos.Mutable probe = new BlockPos.Mutable(hub.getX() + dx, hub.getY(), hub.getZ() + dz);
        for (int dy = 0; dy < 60; dy++) {
            probe.setY(hub.getY() + dy);
            var below = world.getBlockState(probe);
            var at = world.getBlockState(probe.up());
            if (!below.isAir() && below.isSolidBlock(world, probe) && at.isAir()) {
                return probe.getY() + 1;
            }
        }
        for (int dy = 1; dy < 40; dy++) {
            probe.setY(hub.getY() - dy);
            var below = world.getBlockState(probe);
            var at = world.getBlockState(probe.up());
            if (!below.isAir() && below.isSolidBlock(world, probe) && at.isAir()) {
                return probe.getY() + 1;
            }
        }
        return hub.getY();
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
                    double py = findSafeHubY(world, hubPos, offset, 0);
                    double pz = hubPos.getZ() + 0.5;

                    // Remove old UUID so a fresh one is assigned (prevents duplicates)
                    nbt.remove("UUID");
                    // Strip combat-arena flags so the pet walks/breathes/takes damage at the hub.
                    // These get set by CombatManager when a mob enters the grid and would
                    // otherwise freeze the restored pet in place.
                    nbt.putBoolean("NoAI", false);
                    nbt.putBoolean("NoGravity", false);
                    nbt.putBoolean("Invulnerable", false);
                    nbt.putBoolean("Silent", false);
                    nbt.remove("Tags");

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
                    int fallbackY = findSafeHubY(world, hubPos, offset, 0);
                    var rawEntity = entityType.create(world, null,
                        BlockPos.ofFloored(hubPos.getX() + offset + 0.5, fallbackY, hubPos.getZ() + 0.5),
                        SpawnReason.MOB_SUMMONED, false, false);
                    if (rawEntity instanceof net.minecraft.entity.mob.MobEntity mob) {
                        mob.setPersistent();
                        mob.setAiDisabled(false);
                        // Try to set tamed state
                        if (mob instanceof TameableEntity tameable) {
                            //? if <=1.21.4 {
                            tameable.setOwnerUuid(player.getUuid());
                            //?} else
                            /*tameable.setOwner(player);*/
                            tameable.setTamed(true, false);
                        } else if (mob instanceof AbstractHorseEntity horse) {
                            //? if <=1.21.4 {
                            horse.setOwnerUuid(player.getUuid());
                            //?} else
                            /*horse.setOwner(player);*/
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
            var a = snapshot.allyEntry();
            return new PetData(snapshot.entityTypeId(), a.hp(), a.hp(), a.attack(), a.defense(),
                a.speed(), a.range(), snapshot.fullEntityNbt());
        }

        /** Create from a surviving combat entity (between levels). */
        public static PetData fromCombatEntity(CombatEntity e, @org.jetbrains.annotations.Nullable NbtCompound originalNbt) {
            return new PetData(e.getEntityTypeId(), e.getCurrentHp(), e.getMaxHp(),
                e.getAttackPower(), e.getDefense(), e.getMoveSpeed(), e.getRange(), originalNbt);
        }
    }
}
