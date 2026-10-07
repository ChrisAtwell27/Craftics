package com.crackedgames.craftics.combat;

import com.crackedgames.craftics.CrafticsMod;
import com.crackedgames.craftics.world.CrafticsSavedData;
import com.crackedgames.craftics.world.IslandDimensions;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Brings home the pets a player left on somebody else's island.
 *
 * <p>A party plays on its leader's island and a guest's pets wait there between runs. When the
 * guest stops being in that party - they leave, they are kicked, it disbands, or the leader
 * changes and the party moves to another island - those animals have no reason to be there any
 * more and nothing used to move them. {@link PetTags#stranded} is the whole rule; this is the
 * part that acts on it.
 *
 * <p>Two passes, because an animal can only be moved while it is loaded:
 * <ul>
 *   <li>{@link #recallStrandedPets} sweeps every loaded island straight away. Call it after
 *       anything that changes who is in a party.</li>
 *   <li>The load hook from {@link #register} catches the rest the next time their chunk loads.
 *       Minecraft reads entities off disk in the background, so forcing the chunk and searching
 *       it on the spot would miss animals that had not arrived yet.</li>
 * </ul>
 */
public final class PetRecall {

    private PetRecall() {}

    /** Arena combatants carry this; a pet in the middle of a fight is not somebody's stray. */
    private static final String ARENA_TAG = "craftics_arena";

    public static void register() {
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (!(entity instanceof MobEntity mob)) return;
            UUID islandOwner = IslandDimensions.ownerOf(world);
            if (islandOwner == null) return;
            // Marked pets only on this path: it runs for every entity that ever loads, and the
            // roster lookup the sweep does for unmarked ones is not worth paying here.
            UUID owner = PetTags.ownerOf(mob.getCommandTags());
            if (owner == null || mob.getCommandTags().contains(ARENA_TAG)) return;
            MinecraftServer server = world.getServer();
            CrafticsSavedData data = CrafticsSavedData.get(server.getOverworld());
            if (!PetTags.stranded(owner, islandOwner, data.getIslandOwnerFor(owner))) return;
            // Not from inside its own load: pulling an entity out of a world while that world is
            // still adding it is asking for trouble. Next tick it is an ordinary loaded animal.
            server.execute(() -> {
                if (mob.isAlive() && !mob.isRemoved()) sendHome(server, data, mob, owner);
            });
        });
    }

    /**
     * Send every stranded pet on a loaded island home. Safe to call at any time and as often as
     * anything changes: a pet that is where it belongs is left alone.
     *
     * @return how many animals were moved
     */
    public static int recallStrandedPets(MinecraftServer server) {
        if (server == null) return 0;
        CrafticsSavedData data = CrafticsSavedData.get(server.getOverworld());
        Map<UUID, UUID> rosterOwners = null;   // built only if an unmarked animal turns up

        // Collected first, moved after: moving discards entities, and that must not happen
        // while a world's entity list is being walked.
        List<MobEntity> strays = new ArrayList<>();
        List<UUID> owners = new ArrayList<>();
        for (Map.Entry<UUID, ServerWorld> island : IslandDimensions.loadedIslands().entrySet()) {
            UUID islandOwner = island.getKey();
            for (Entity e : island.getValue().iterateEntities()) {
                if (!(e instanceof MobEntity mob) || !mob.isAlive()) continue;
                if (mob.getCommandTags().contains(ARENA_TAG)) continue;
                UUID owner = PetTags.ownerOf(mob.getCommandTags());
                if (owner == null) {
                    // An animal parked before pets were marked. Its owner's battle party still
                    // lists it by id, which is the only link left.
                    if (rosterOwners == null) rosterOwners = rosterOwners(data);
                    owner = rosterOwners.get(mob.getUuid());
                }
                if (PetTags.stranded(owner, islandOwner, owner == null ? null : data.getIslandOwnerFor(owner))) {
                    strays.add(mob);
                    owners.add(owner);
                }
            }
        }
        int moved = 0;
        for (int i = 0; i < strays.size(); i++) {
            if (sendHome(server, data, strays.get(i), owners.get(i))) moved++;
        }
        return moved;
    }

    /** Battle-party animal id -> the player whose party lists it. */
    private static Map<UUID, UUID> rosterOwners(CrafticsSavedData data) {
        Map<UUID, UUID> out = new HashMap<>();
        for (UUID playerId : data.getAllPlayerIds()) {
            for (UUID mobId : data.getPlayerData(playerId).getPartyMobs()) {
                out.putIfAbsent(mobId, playerId);
            }
        }
        return out;
    }

    /**
     * Move one animal to its owner's own island: back to the spot it is marked with when that is
     * still good, otherwise to their island spawn.
     *
     * <p>The copy is made first and the original removed only once the copy is standing in the
     * other world. The other order loses the animal outright if the far side refuses it.
     */
    private static boolean sendHome(MinecraftServer server, CrafticsSavedData data, MobEntity mob, UUID owner) {
        if (!(mob.getEntityWorld() instanceof ServerWorld from)) return false;
        if (!data.hasPersonalWorld(owner)) return false;   // no island of their own to send it to
        try {
            NbtCompound nbt = new NbtCompound();
            mob.writeNbt(nbt);
            String typeId = Registries.ENTITY_TYPE.getId(mob.getType()).toString();
            String name = mob.getName().getString();
            HubPetCollector.PetData pet = new HubPetCollector.PetData(
                typeId, Math.max(1, (int) Math.ceil(mob.getHealth())), Math.max(1, (int) mob.getMaxHealth()),
                0, 0, 0, 0, nbt, false, owner, null, null, PetTags.homeOf(mob.getCommandTags()));

            if (HubPetCollector.restorePets(from, null, owner, List.of(pet), data, true) == 0) {
                CrafticsMod.LOGGER.warn("Could not send {} home to {}'s island; leaving it where it is",
                    typeId, owner);
                return false;
            }
            mob.discard();
            CrafticsMod.LOGGER.info("Sent stranded pet {} home from {} to {}'s island",
                typeId, from.getRegistryKey().getValue(), owner);
            ServerPlayerEntity online = server.getPlayerManager().getPlayer(owner);
            if (online != null) {
                online.sendMessage(Text.literal("§aYour " + name + " went home to your island."), false);
            }
            return true;
        } catch (Exception e) {
            CrafticsMod.LOGGER.error("Failed to send a stranded pet home: {}", e.toString());
            return false;
        }
    }
}
