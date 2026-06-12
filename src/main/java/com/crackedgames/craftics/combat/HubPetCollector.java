package com.crackedgames.craftics.combat;

import com.crackedgames.craftics.CrafticsMod;
import com.crackedgames.craftics.api.registry.AllyEntry;
import com.crackedgames.craftics.api.registry.AllyRegistry;
import com.crackedgames.craftics.network.PartyMobSync;
import com.crackedgames.craftics.world.CrafticsSavedData;
import net.minecraft.entity.Entity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.AbstractHorseEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Collects the mobs in a player's battle party ({@link CrafticsSavedData.PlayerData#getPartyMobs()})
 * for a combat run, and restores the survivors to the hub afterwards.
 *
 * <p>A party is built explicitly: the player Shift+Right-Clicks mobs on their
 * island (see {@code PartyMobs}). Any passive or neutral mob qualifies — combat
 * stats come from a hand-tuned {@link AllyEntry} when one exists, or are derived
 * from the mob's vanilla attributes otherwise.
 */
public class HubPetCollector {

    /** Snapshot of a party mob collected from the hub before combat. */
    public record TamedPetSnapshot(
        String entityTypeId,
        UUID entityUuid,
        NbtCompound fullEntityNbt,
        AllyEntry allyEntry,
        UUID playerUuid,
        boolean saddledMount
    ) {}

    /**
     * Collect the player's battle-party mobs for combat. Each mob in the party
     * list is looked up by UUID, snapshotted, and discarded from the hub world.
     * Party entries whose mob no longer exists (died, despawned) are pruned.
     * Fields up to the player's {@code PartyMobs.partyCap} mobs.
     */
    public static List<TamedPetSnapshot> collectFollowingPets(
            ServerWorld world, ServerPlayerEntity player, CrafticsSavedData data) {

        CrafticsSavedData.PlayerData pd = data.getPlayerData(player.getUuid());
        List<UUID> party = pd.getPartyMobs();
        if (party.isEmpty()) return List.of();

        int cap = PartyMobs.partyCap(player);
        UUID ownerUuid = player.getUuid();
        List<TamedPetSnapshot> results = new ArrayList<>();
        List<Entity> toDiscard = new ArrayList<>();
        List<UUID> survivors = new ArrayList<>(); // party entries whose mob still exists

        for (UUID mobUuid : party) {
            Entity entity = world.getEntity(mobUuid);
            if (!(entity instanceof MobEntity mob) || !mob.isAlive()) {
                continue; // dead / despawned / unloaded — drop this party entry
            }
            survivors.add(mobUuid);
            if (results.size() >= cap) continue;

            String typeId = Registries.ENTITY_TYPE.getId(mob.getType()).toString();
            // Hand-tuned stats when registered, otherwise derived from the mob itself.
            AllyEntry allyEntry = AllyRegistry.getOrNull(typeId);
            if (allyEntry == null) {
                allyEntry = PartyMobs.deriveEntry(mob);
            }

            NbtCompound nbt = new NbtCompound();
            mob.writeNbt(nbt);
            boolean saddledMount = PartyMobs.isSaddledMount(mob);

            results.add(new TamedPetSnapshot(
                typeId, mob.getUuid(), nbt, allyEntry, ownerUuid, saddledMount));
            toDiscard.add(mob);

            CrafticsMod.LOGGER.info("Party mob joining combat: {} ({}){}",
                mob.getName().getString(), typeId, saddledMount ? " [saddled mount]" : "");
        }

        // Remove collected mobs from the hub world.
        for (Entity e : toDiscard) {
            e.discard();
        }

        // Prune dangling party entries; persist + resync if the list changed.
        if (!survivors.equals(party)) {
            party.clear();
            party.addAll(survivors);
            data.markDirty();
        }
        PartyMobSync.sync(player);

        return results;
    }

    /**
     * Landing Y of the hub ANCHOR column (the spawn plate the player teleports
     * to): scan up then down from {@code hub.y} for a solid block with air above.
     * Matches the safe-landing logic in CrafticsMod.teleportToHub, so this is
     * the same floor the returning player ends up on.
     */
    private static int findAnchorLandingY(ServerWorld world, BlockPos hub) {
        BlockPos.Mutable probe = new BlockPos.Mutable(hub.getX(), hub.getY(), hub.getZ());
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
     * Landing spot {x, y, z} for a restored pet. Pets used to run the full
     * 60-up/40-down anchor scan on their own OFFSET column, which had two ways
     * to kill them: a tree canopy or roof over the offset column won the
     * up-scan (pet placed high, walks off and falls), and a column past the
     * island edge found nothing at all and silently returned hub.y — a midair
     * spawn over the void. Now the anchor's landing is resolved first (the
     * floor the player lands on), the pet's offset column is only accepted
     * when it has a floor within a few blocks of that height, and a column
     * with no such floor falls back to the anchor column itself.
     */
    private static double[] findPetLanding(ServerWorld world, BlockPos hub, int offset) {
        int anchorY = findAnchorLandingY(world, hub);
        BlockPos.Mutable probe = new BlockPos.Mutable(hub.getX() + offset, anchorY, hub.getZ());
        for (int landY = anchorY + 6; landY >= anchorY - 6; landY--) {
            probe.setY(landY - 1);
            var floor = world.getBlockState(probe);
            var at = world.getBlockState(probe.up());
            if (!floor.isAir() && floor.isSolidBlock(world, probe) && at.isAir()) {
                return new double[]{hub.getX() + offset + 0.5, landY, hub.getZ() + 0.5};
            }
        }
        return new double[]{hub.getX() + 0.5, anchorY, hub.getZ() + 0.5};
    }

    /**
     * Restore surviving pets to the hub world after a biome run ends.
     * Recreates entities from their original NBT snapshots with updated positions.
     * The original entity UUID is kept so the mob stays valid in the player's
     * battle party — its hub copy was discarded when it was collected for combat,
     * so there is no duplicate to worry about.
     */
    public static void restorePetsToHub(ServerWorld world, ServerPlayerEntity player,
                                         List<PetData> survivingPets, CrafticsSavedData data) {
        BlockPos hubPos = data.getHubTeleportPos(player.getUuid());
        if (hubPos == null) return;

        int offset = 0;
        for (PetData pet : survivingPets) {
            // Defensive guard: never resurrect a dead pet. Restoration reads the
            // pre-combat NBT (full Health tag), so a fallen pet that slipped into
            // this list would otherwise reappear at the hub alive and well. Every
            // caller already filters on isAlive(); this is the last line of defense.
            if (pet.hp() <= 0) {
                CrafticsMod.LOGGER.info("Skipping hub restore of fallen pet: {}", pet.entityType());
                continue;
            }
            offset++;
            try {
                if (pet.originalNbt() != null) {
                    // Restore from original NBT (preserves collar color, armor, name, variant, UUID)
                    NbtCompound nbt = pet.originalNbt().copy();
                    // Override position to a verified landing near the hub anchor
                    double[] landing = findPetLanding(world, hubPos, offset);
                    double px = landing[0];
                    double py = landing[1];
                    double pz = landing[2];

                    // Strip combat-arena flags so the pet walks/breathes/takes damage at the hub.
                    // These get set by CombatManager when a mob enters the grid and would
                    // otherwise freeze the restored pet in place. The UUID is intentionally
                    // KEPT so battle-party membership survives the combat run.
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
                    double[] landing = findPetLanding(world, hubPos, offset);
                    var rawEntity = entityType.create(world, null,
                        BlockPos.ofFloored(landing[0], landing[1], landing[2]),
                        SpawnReason.MOB_SUMMONED, false, false);
                    if (rawEntity instanceof net.minecraft.entity.mob.MobEntity mob) {
                        mob.setPersistent();
                        mob.setAiDisabled(false);
                        // Try to set tamed state
                        if (mob instanceof TameableEntity tameable) {
                            //? if <=1.21.4 {
                            /*tameable.setOwnerUuid(player.getUuid());
                            *///?} else
                            tameable.setOwner(player);
                            tameable.setTamed(true, false);
                        } else if (mob instanceof AbstractHorseEntity horse) {
                            //? if <=1.21.4 {
                            /*horse.setOwnerUuid(player.getUuid());
                            *///?} else
                            horse.setOwner(player);
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
        // Survivors kept their UUIDs, so the party list stays valid; resync to the client.
        PartyMobSync.sync(player);
    }

    /**
     * Extended pet data record that includes original NBT for hub restoration.
     * Replaces the old PetData record in CombatManager.
     *
     * @param mounted whether this pet was acting as the player's rideable mount —
     *                so it can be re-mounted after a between-level transition.
     */
    public record PetData(String entityType, int hp, int maxHp, int atk, int def, int speed, int range,
                          @org.jetbrains.annotations.Nullable NbtCompound originalNbt, boolean mounted) {

        /** Create from a TamedPetSnapshot (first level entry). */
        public static PetData fromSnapshot(TamedPetSnapshot snapshot) {
            var a = snapshot.allyEntry();
            return new PetData(snapshot.entityTypeId(), a.hp(), a.hp(), a.attack(), a.defense(),
                a.speed(), a.range(), snapshot.fullEntityNbt(), snapshot.saddledMount());
        }

        /** Create from a surviving combat entity (between levels). */
        public static PetData fromCombatEntity(CombatEntity e, @org.jetbrains.annotations.Nullable NbtCompound originalNbt) {
            return new PetData(e.getEntityTypeId(), e.getCurrentHp(), e.getMaxHp(),
                e.getAttackPower(), e.getDefense(), e.getMoveSpeed(), e.getRange(), originalNbt, e.isMounted());
        }
    }
}
