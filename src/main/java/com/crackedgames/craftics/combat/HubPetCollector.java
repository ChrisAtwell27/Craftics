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
 * island (see {@code PartyMobs}). Any passive or neutral mob qualifies - combat
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
        boolean saddledMount,
        /**
         * True when this ally came from a {@code FieldAllyProvider} rather than from a real
         * mob in the hub. It fights the battle and is gone: never carried between levels,
         * never materialised into the hub afterwards. Putting one "back" would spawn a real
         * creature the owning mod is still tracking in its own party, giving the player two
         * of it.
         */
        boolean temporary,
        /** NBT merged onto the mob at spawn. Distinct from {@code fullEntityNbt}, which is
         *  the hub-restore blob for a real pet. */
        @org.jetbrains.annotations.Nullable NbtCompound spawnNbt,
        /** AI and typing key, or null to use the entity type id. What lets one entity type
         *  field many different creatures. */
        @org.jetbrains.annotations.Nullable String aiKey,
        /** Name shown in combat, or null for the entity's own. */
        @org.jetbrains.annotations.Nullable String displayName,
        /**
         * True when this ally starts the fight on the bench rather than on the grid: carried
         * in, given no tile and no world mob, and fielded only if the player swaps it in.
         * Only ever set on a {@code temporary} provider ally - a hub pet is a real animal and
         * has nowhere to be benched to.
         */
        boolean reserve
    ) {
        /** A real hub pet: permanent, no spawn NBT, AI from its entity type. */
        public TamedPetSnapshot(String entityTypeId, UUID entityUuid, NbtCompound fullEntityNbt,
                                AllyEntry allyEntry, UUID playerUuid, boolean saddledMount) {
            this(entityTypeId, entityUuid, fullEntityNbt, allyEntry, playerUuid, saddledMount,
                 false, null, null, null, false);
        }
    }

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
        // Do NOT bail on an empty hub party. A mod whose party is data on the player rather
        // than mobs in a yard has no hub entries at all, so returning here would mean its
        // provider was never asked and its creatures never took the field. The loop below is
        // a no-op for an empty list, and the provider pass at the end still runs.
        if (party.isEmpty()
                && com.crackedgames.craftics.api.registry.FieldAllyProviderRegistry.isEmpty()) {
            return List.of();
        }

        // A party member's pets live on THEIR island, not on the leader's, and a run starts in
        // the leader's world - so searching only `world` found nothing for anyone but the
        // leader, and then pruned their whole party list as dangling for good measure. Search
        // the owner's island as well, and treat "not in either world" as inconclusive rather
        // than as proof the animal is gone.
        ServerWorld ownerIsland = ownerIslandOrNull(world, player);
        boolean searchedEverywhere = ownerIsland != null;

        int cap = PartyMobs.partyCap(player);
        UUID ownerUuid = player.getUuid();
        List<TamedPetSnapshot> results = new ArrayList<>();
        List<Entity> toDiscard = new ArrayList<>();
        List<UUID> survivors = new ArrayList<>(); // party entries whose mob still exists

        for (UUID mobUuid : party) {
            Entity entity = world.getEntity(mobUuid);
            if (entity == null && ownerIsland != null && ownerIsland != world) {
                entity = ownerIsland.getEntity(mobUuid);
            }
            if (!(entity instanceof MobEntity mob) || !mob.isAlive()) {
                // Only a search that actually covered the owner's island is allowed to
                // conclude the animal is gone. Otherwise keep the entry: an unopened island
                // is a lookup that could not answer, not an answer.
                if (!searchedEverywhere) survivors.add(mobUuid);
                continue;
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

            // Career log of distinct species tamed - the Pet Collector achievement counts
            // species, not animals, so five wolves are still one entry.
            com.crackedgames.craftics.achievement.AchievementManager.recordCollected(
                player, com.crackedgames.craftics.achievement.AchievementManager.COL_PET_SPECIES,
                typeId);

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

        // Allies contributed from outside the hub-party model. Appended after real pets so
        // freeSlots reflects what the hub already took, and so a provider cannot displace a
        // pet the player explicitly tagged.
        appendProvidedAllies(world, player, results, cap);

        return results;
    }

    /**
     * Ask every {@code FieldAllyProvider} for allies and append them as temporary snapshots.
     *
     * <p>Marked temporary, which is what keeps them out of the hub: they fight the battle and
     * are gone. A provider ally was never a hub entity, so "returning" one would spawn a real
     * creature into the world that the owning mod is still tracking in its own party.
     *
     * <p>{@code freeSlots} is passed as advisory and the result is deliberately not truncated
     * to it. Craftics' cap is written for tamed wolves; a mod with a six-creature party owns
     * its own rules, and silently cutting it to one would look like a Craftics bug.
     */
    private static void appendProvidedAllies(ServerWorld world, ServerPlayerEntity player,
                                             List<TamedPetSnapshot> results, int cap) {
        if (com.crackedgames.craftics.api.registry.FieldAllyProviderRegistry.isEmpty()) return;
        int freeSlots = cap - results.size();
        var provided = com.crackedgames.craftics.api.registry.FieldAllyProviderRegistry
            .collect(world, player, freeSlots);
        for (var fa : provided) {
            results.add(providedSnapshot(fa, player, false));
        }
        // Reserves are appended AFTER the field allies so they can never displace one. The
        // bench is asked for separately rather than being carved off the front of `provided`:
        // a provider that returns six and means all six to fight must keep getting all six.
        var benched = com.crackedgames.craftics.api.registry.FieldAllyProviderRegistry
            .collectReserves(world, player);
        for (var fa : benched) {
            results.add(providedSnapshot(fa, player, true));
        }
        if (!provided.isEmpty() || !benched.isEmpty()) {
            CrafticsMod.LOGGER.info("{} provided ally/allies joining for {} ({} benched)",
                provided.size(), player.getName().getString(), benched.size());
        }
    }

    /** One provider ally as a snapshot, fielded or benched. */
    private static TamedPetSnapshot providedSnapshot(
            com.crackedgames.craftics.api.FieldAllyProvider.FieldAlly fa,
            ServerPlayerEntity player, boolean reserve) {
        return new TamedPetSnapshot(
            fa.entityTypeId(),
            java.util.UUID.randomUUID(),   // no hub entity to identify; a fresh id keeps
                                           // downstream maps that key on it well-formed
            null,                          // no hub-restore blob: it never came from the hub
            fa.stats(),
            player.getUuid(),
            false,                         // provider allies are never auto-mounts
            true,                          // temporary
            fa.spawnNbt(),
            fa.aiKey(),
            fa.displayName(),
            reserve);
    }

    /**
     * The island world {@code player} owns, or null when they have none or it is not
     * resolvable. Returns {@code world} itself when the player is already standing on their
     * own island, so the common single-player path does no extra work.
     */
    private static ServerWorld ownerIslandOrNull(ServerWorld world, ServerPlayerEntity player) {
        try {
            var server = world.getServer();
            CrafticsSavedData data = CrafticsSavedData.get(server.getOverworld());
            if (!data.hasPersonalWorld(player.getUuid())) return world;
            ServerWorld island = com.crackedgames.craftics.world.IslandDimensions
                .getOrCreate(server, player.getUuid());
            return island != null ? island : world;
        } catch (Exception e) {
            // A pet that cannot be looked up is a pet that stays in the party list; never
            // let a failure here delete someone's animals.
            CrafticsMod.LOGGER.warn("Could not resolve island for pet lookup: {}", e.toString());
            return null;
        }
    }

    /** The island world belonging to {@code owner}, or null if it cannot be resolved. */
    private static ServerWorld islandWorldOf(ServerWorld world, UUID owner) {
        try {
            var server = world.getServer();
            CrafticsSavedData data = CrafticsSavedData.get(server.getOverworld());
            if (!data.hasPersonalWorld(owner)) return null;
            return com.crackedgames.craftics.world.IslandDimensions.getOrCreate(server, owner);
        } catch (Exception e) {
            CrafticsMod.LOGGER.warn("Could not resolve island for pet restore: {}", e.toString());
            return null;
        }
    }

    /**
     * Landing Y of the hub ANCHOR column (the spawn plate the player teleports to):
     * the HIGHEST solid block with air above. Delegates to the shared
     * {@code CrafticsMod.hubLandingY}, so pets land on the exact same top surface the
     * returning player does (and never inside a hollow island or under it).
     */
    private static int findAnchorLandingY(ServerWorld world, BlockPos hub) {
        int y = com.crackedgames.craftics.CrafticsMod.hubLandingY(world, hub.getX(), hub.getZ(), hub.getY());
        return y != Integer.MIN_VALUE ? y : hub.getY();
    }

    /**
     * Landing spot {x, y, z} for a restored pet. Pets used to run the full
     * 60-up/40-down anchor scan on their own OFFSET column, which had two ways
     * to kill them: a tree canopy or roof over the offset column won the
     * up-scan (pet placed high, walks off and falls), and a column past the
     * island edge found nothing at all and silently returned hub.y - a midair
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
     * battle party - its hub copy was discarded when it was collected for combat,
     * so there is no duplicate to worry about.
     */
    public static void restorePetsToHub(ServerWorld world, ServerPlayerEntity player,
                                         List<PetData> survivingPets, CrafticsSavedData data) {
        BlockPos defaultHub = data.getHubTeleportPos(player.getUuid());
        if (defaultHub == null) return;

        int offset = 0;
        for (PetData pet : survivingPets) {
            // Each animal goes home to ITS owner's island, not to whoever this restore was
            // called for. Same-owner pets (every single-player case) resolve to exactly what
            // the old code did.
            ServerWorld petWorld = world;
            BlockPos hubPos = defaultHub;
            if (pet.owner() != null && !pet.owner().equals(player.getUuid())) {
                BlockPos ownerHub = data.getHubTeleportPos(pet.owner());
                ServerWorld ownerWorld = islandWorldOf(world, pet.owner());
                if (ownerHub != null && ownerWorld != null) {
                    hubPos = ownerHub;
                    petWorld = ownerWorld;
                }
            }
            final ServerWorld world0 = petWorld;   // the loop body below spawns into this
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
        // Survivors kept their UUIDs, so the party list stays valid; resync to the client.
        PartyMobSync.sync(player);
    }

    /**
     * Extended pet data record that includes original NBT for hub restoration.
     * Replaces the old PetData record in CombatManager.
     *
     * @param mounted whether this pet was acting as the player's rideable mount -
     *                so it can be re-mounted after a between-level transition.
     */
    /**
     * A pet carried between levels and back to the hub afterwards.
     *
     * <p>{@code owner} is who it belongs to, and it matters now that a party fight can hold
     * pets from several players at once: without it every animal went back to whichever
     * player the restore happened to be called for, which in multiplayer meant a member's
     * wolf being rehomed onto the leader's island. Null means "the player being restored",
     * which is what every single-player path wants.
     */
    public record PetData(String entityType, int hp, int maxHp, int atk, int def, int speed, int range,
                          @org.jetbrains.annotations.Nullable NbtCompound originalNbt, boolean mounted,
                          @org.jetbrains.annotations.Nullable UUID owner) {

        /** Create from a TamedPetSnapshot (first level entry). */
        public static PetData fromSnapshot(TamedPetSnapshot snapshot) {
            var a = snapshot.allyEntry();
            return new PetData(snapshot.entityTypeId(), a.hp(), a.hp(), a.attack(), a.defense(),
                a.speed(), a.range(), snapshot.fullEntityNbt(), snapshot.saddledMount(),
                snapshot.playerUuid());
        }

        /** Create from a surviving combat entity (between levels). */
        public static PetData fromCombatEntity(CombatEntity e, @org.jetbrains.annotations.Nullable NbtCompound originalNbt) {
            return new PetData(e.getEntityTypeId(), e.getCurrentHp(), e.getMaxHp(),
                e.getAttackPower(), e.getDefense(), e.getMoveSpeed(), e.getRange(), originalNbt,
                e.isMounted(), e.getOwnerUuid());
        }
    }
}
