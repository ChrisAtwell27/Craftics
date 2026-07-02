package com.crackedgames.craftics.combat;

import com.crackedgames.craftics.CrafticsMod;
import com.crackedgames.craftics.core.GridArena;
import com.crackedgames.craftics.core.GridPos;
import com.crackedgames.craftics.level.ArenaBuilder;
import com.crackedgames.craftics.level.BiomeRegistry;
import com.crackedgames.craftics.level.BiomeTemplate;
import com.crackedgames.craftics.level.LevelDefinition;
import com.crackedgames.craftics.level.LevelRegistry;
import com.crackedgames.craftics.level.campaign.CampaignManager;
import com.crackedgames.craftics.network.EnterCombatPayload;
import com.crackedgames.craftics.network.ExitCombatPayload;
import com.crackedgames.craftics.network.LoadingScreenPayload;
import com.crackedgames.craftics.network.RunInvitePayload;
import com.crackedgames.craftics.world.CrafticsSavedData;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Pre-combat "join lobby" for biome runs. Any party member can start a run on the shared
 * island; the starter becomes the host (their progression drives the run) and every other
 * online party member - the island's original owner included, with no special status - is
 * sent a Yes/No popup. Only those who accept are teleported into the arena; decliners (and
 * anyone who times out) stay on the island.
 *
 * <p>The run doesn't begin until the lobby resolves (all invited replied, or the timeout
 * elapses), so players join all at once via the normal {@code finishPartyJoin} path rather
 * than mid-fight. A pending lobby (and the active run that follows) is keyed by the island
 * = {@code getEffectiveWorldOwner}, so only one run can be in flight per island at a time.
 */
public final class RunInviteManager {
    private RunInviteManager() {}

    /** Server timeout - a couple seconds past the client countdown so a client auto-decline
     *  lands first; the timeout is the backstop for a fully unresponsive client. */
    private static final int INVITE_SECONDS = 20;
    private static final int TIMEOUT_TICKS = (INVITE_SECONDS + 2) * 20;

    private static final class Pending {
        final UUID starter;
        final String biomeId;
        final UUID island;
        final Set<UUID> awaiting = new HashSet<>();  // invited, not yet replied
        final Set<UUID> accepted = new HashSet<>();
        int ticksLeft = TIMEOUT_TICKS;
        Pending(UUID starter, String biomeId, UUID island) {
            this.starter = starter; this.biomeId = biomeId; this.island = island;
        }
    }

    /** One pending lobby per island. */
    private static final Map<UUID, Pending> BY_ISLAND = new HashMap<>();
    /** Invitee UUID -> the island whose lobby they were invited to (for routing a reply). */
    private static final Map<UUID, UUID> INVITEE_ISLAND = new HashMap<>();

    /**
     * Entry point from the StartLevelPayload receiver - any party member may call this.
     * Validates, then either starts immediately (no one else to ask) or opens the lobby.
     */
    public static void requestStart(ServerPlayerEntity starter, String biomeId) {
        ServerWorld world = (ServerWorld) starter.getEntityWorld();
        CrafticsSavedData data = CrafticsSavedData.get(world);
        data.claimLegacyData(starter.getUuid());
        CrafticsSavedData.PlayerData pd = data.getPlayerData(starter.getUuid());
        UUID island = data.getEffectiveWorldOwner(starter.getUuid());

        // One run per island: reject if a lobby is already open or a member is mid-run here.
        if (BY_ISLAND.containsKey(island) || islandRunActive(starter, data)) {
            starter.sendMessage(Text.literal("§cA run is already starting on this island."), false);
            ServerPlayNetworking.send(starter, new ExitCombatPayload(false));
            return;
        }
        if (CombatManager.get(starter).isActive()) {
            ServerPlayNetworking.send(starter, new ExitCombatPayload(false));
            return;
        }
        if (!data.hasPersonalWorld(island)) {
            starter.sendMessage(Text.literal(
                "§cThere's no island to run on yet: §e/craftics world create"), false);
            ServerPlayNetworking.send(starter, new ExitCombatPayload(false));
            return;
        }

        BiomeTemplate biome = findBiome(biomeId);
        if (biome == null) {
            CrafticsMod.LOGGER.warn("RunInviteManager: no biome for ID '{}'", biomeId);
            ServerPlayNetworking.send(starter, new ExitCombatPayload(false));
            return;
        }
        // Unlock check uses the starter's own progression (it's their run).
        pd.initBranchIfNeeded();
        int biomeOrder = CampaignManager.ordinalOf(biomeId, Math.max(0, pd.branchChoice)) + 1;
        if (biomeOrder <= 0 || biomeOrder > pd.highestBiomeUnlocked) {
            CrafticsMod.LOGGER.warn("{} tried to start locked biome {} (unlocked={}, needed={})",
                starter.getName().getString(), biomeId, pd.highestBiomeUnlocked, biomeOrder);
            ServerPlayNetworking.send(starter, new ExitCombatPayload(false));
            return;
        }

        // Eligible joiners: every other online party member (they all share this
        // island). Members browsing a merchant scene are skipped - a run invite
        // popping over the trading hall would drag them into combat while still
        // registered as scene members, wedging both flows.
        List<ServerPlayerEntity> invitees = new ArrayList<>();
        for (UUID memberUuid : data.getPartyMemberUuids(starter.getUuid())) {
            if (memberUuid.equals(starter.getUuid())) continue;
            ServerPlayerEntity member = world.getServer().getPlayerManager().getPlayer(memberUuid);
            if (member != null && !CombatManager.get(memberUuid).isActive()
                    && !com.crackedgames.craftics.scene.SceneController.isSceneMember(memberUuid)) {
                invitees.add(member);
            }
        }

        if (invitees.isEmpty()) {
            beginRun(starter, biomeId, List.of(starter.getUuid()));
            return;
        }

        // Otherwise hold the lobby and prompt each invitee.
        Pending p = new Pending(starter.getUuid(), biomeId, island);
        String prettyBiome = prettyBiome(biomeId);
        for (ServerPlayerEntity inv : invitees) {
            p.awaiting.add(inv.getUuid());
            INVITEE_ISLAND.put(inv.getUuid(), island);
            ServerPlayNetworking.send(inv, new RunInvitePayload(
                prettyBiome, starter.getName().getString(), INVITE_SECONDS));
        }
        BY_ISLAND.put(island, p);
        ServerPlayNetworking.send(starter, new LoadingScreenPayload(true,
            "§6Gathering the party", "§7Waiting for players to join..."));
    }

    /** A C2S reply to a {@link RunInvitePayload}. */
    public static void respond(MinecraftServer server, UUID responder, boolean accept) {
        UUID island = INVITEE_ISLAND.remove(responder);
        if (island == null) return; // no live invite for this player
        Pending p = BY_ISLAND.get(island);
        if (p == null) return;
        p.awaiting.remove(responder);
        if (accept) p.accepted.add(responder);
        if (p.awaiting.isEmpty()) resolve(server, p);
    }

    /** Tick the pending lobbies; call once per server tick. */
    public static void tick(MinecraftServer server) {
        if (BY_ISLAND.isEmpty()) return;
        List<Pending> expired = null;
        for (Pending p : BY_ISLAND.values()) {
            if (--p.ticksLeft <= 0) {
                if (expired == null) expired = new ArrayList<>();
                expired.add(p);
            }
        }
        if (expired != null) {
            for (Pending p : expired) resolve(server, p);
        }
    }

    private static void resolve(MinecraftServer server, Pending p) {
        BY_ISLAND.remove(p.island);
        for (UUID a : p.awaiting) INVITEE_ISLAND.remove(a); // drop non-responders' routing

        ServerPlayerEntity starter = server.getPlayerManager().getPlayer(p.starter);
        if (starter == null) {
            // Starter left before the run began: nothing to host. Bounce any accepters' UI.
            for (UUID a : p.accepted) {
                ServerPlayerEntity m = server.getPlayerManager().getPlayer(a);
                if (m != null) ServerPlayNetworking.send(m, new ExitCombatPayload(false));
            }
            return;
        }
        List<UUID> participants = new ArrayList<>();
        participants.add(p.starter);
        for (UUID a : p.accepted) {
            if (server.getPlayerManager().getPlayer(a) != null) participants.add(a);
        }
        beginRun(starter, p.biomeId, participants);
    }

    /** True if any of the starter's party members is currently mid-run (one run per island). */
    private static boolean islandRunActive(ServerPlayerEntity starter, CrafticsSavedData data) {
        for (UUID memberUuid : data.getPartyMemberUuids(starter.getUuid())) {
            if (CombatManager.get(memberUuid).isActive()) return true;
        }
        return false;
    }

    private static BiomeTemplate findBiome(String biomeId) {
        for (BiomeTemplate b : BiomeRegistry.getAllBiomes()) {
            if (b.biomeId.equals(biomeId)) return b;
        }
        return null;
    }

    /** "deep_dark" / "forest/pale_garden" -> "Deep Dark" / "Pale Garden" for the popup. */
    private static String prettyBiome(String biomeId) {
        String tail = biomeId.contains("/") ? biomeId.substring(biomeId.lastIndexOf('/') + 1) : biomeId;
        String[] words = tail.replace('_', ' ').trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (w.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return sb.length() > 0 ? sb.toString() : biomeId;
    }

    /**
     * Build the arena and pull {@code participants} (the host + accepters) into combat. This
     * is the refactored body of the old StartLevelPayload handler, parameterized by the
     * participant set so decliners are simply never added.
     */
    static void beginRun(ServerPlayerEntity starter, String biomeId, List<UUID> participants) {
        ServerWorld world = (ServerWorld) starter.getEntityWorld();
        CrafticsSavedData data = CrafticsSavedData.get(world);
        CrafticsSavedData.PlayerData pd = data.getPlayerData(starter.getUuid());

        // A run and a merchant scene are mutually exclusive. The invite filter
        // already skips scene members, but the ~22s lobby window (and any future
        // entry path) can still race a scene entry - eject at grant time so no
        // participant enters the arena while still registered in a scene.
        com.crackedgames.craftics.scene.SceneController.ejectForRun(starter);
        for (UUID participantUuid : participants) {
            ServerPlayerEntity participant =
                world.getServer().getPlayerManager().getPlayer(participantUuid);
            if (participant != null) {
                com.crackedgames.craftics.scene.SceneController.ejectForRun(participant);
            }
        }

        BiomeTemplate biome = findBiome(biomeId);
        if (biome == null) { ServerPlayNetworking.send(starter, new ExitCombatPayload(false)); return; }
        pd.initBranchIfNeeded();

        int levelIndex;
        if (pd.isInBiomeRun() && biomeId.equals(pd.activeBiomeId)) {
            levelIndex = pd.activeBiomeLevelIndex; // resume
        } else {
            pd.startBiomeRun(biome.biomeId);
            pd.discoverBiome(biome.biomeId);
            data.markDirty();
            levelIndex = 0;
        }

        int globalLevel = biome.startLevel + levelIndex;
        UUID worldOwner = data.getEffectiveWorldOwner(starter.getUuid());
        boolean ownerHpScale = data.getPlayerData(worldOwner).scaleHpPerLevelEnabled;
        LevelDefinition levelDef = LevelRegistry.get(globalLevel, pd.branchChoice, ownerHpScale);
        if (levelDef == null) {
            CrafticsMod.LOGGER.warn("RunInviteManager: no definition for level {}", globalLevel);
            ServerPlayNetworking.send(starter, new ExitCombatPayload(false));
            return;
        }

        GridArena arena = ArenaBuilder.build(world, levelDef, worldOwner);
        BlockPos startPos = arena.getPlayerStartBlockPos();
        BlockPos origin = arena.getOrigin();
        float cameraYaw = ArenaBuilder.consumePendingCameraYaw();

        starter.requestTeleport(startPos.getX() + 0.5, startPos.getY(), startPos.getZ() + 0.5);

        var hubPetSnapshots = HubPetCollector.collectFollowingPets(world, starter, data);
        CombatManager.get(starter).setHubPetSnapshots(hubPetSnapshots);

        // Clear the host's "waiting for party" overlay, then enter combat.
        ServerPlayNetworking.send(starter, new LoadingScreenPayload(false, "", ""));
        ServerPlayNetworking.send(starter, new EnterCombatPayload(
            origin.getX(), origin.getY(), origin.getZ(),
            arena.getWidth(), arena.getHeight(), cameraYaw));

        CombatManager.get(starter).startCombat(starter, arena, levelDef);

        EventManager em = new EventManager(participants);
        CombatManager hostCm = CombatManager.get(starter);
        hostCm.setEventManager(em);
        hostCm.setWorldOwnerUuid(worldOwner);
        hostCm.addPartyMember(starter);

        GridPos hostGrid = arena.getPlayerGridPos();
        Set<GridPos> reserved = new HashSet<>();
        reserved.add(hostGrid);
        int idx = 0;
        for (UUID memberUuid : participants) {
            if (memberUuid.equals(starter.getUuid())) continue;
            ServerPlayerEntity member = world.getServer().getPlayerManager().getPlayer(memberUuid);
            if (member == null) continue;
            int dx = (idx % 2 == 0) ? ((idx / 2) + 1) : -((idx / 2) + 1);
            idx++;
            int cx = Math.max(0, Math.min(arena.getWidth() - 1, hostGrid.x() + dx));
            int cz = Math.max(0, Math.min(arena.getHeight() - 1, hostGrid.z()));
            GridPos desired = new GridPos(cx, cz);
            GridPos chosen = CombatManager.findNearestWalkableUnreserved(arena, desired, reserved);
            if (chosen == null) chosen = hostGrid;
            reserved.add(chosen);

            BlockPos mbp = arena.gridToBlockPos(chosen);
            member.requestTeleport(mbp.getX() + 0.5, mbp.getY(), mbp.getZ() + 0.5);
            member.changeGameMode(net.minecraft.world.GameMode.ADVENTURE);
            ServerPlayNetworking.send(member, new EnterCombatPayload(
                origin.getX(), origin.getY(), origin.getZ(),
                arena.getWidth(), arena.getHeight(), cameraYaw));
            hostCm.addPartyMember(member);
            com.crackedgames.craftics.item.MoveSlotManager.enforce(member);
            CombatManager.get(memberUuid).setEventManager(em);
            CrafticsMod.LOGGER.info("Party member {} joined the run (biome {}, level {})",
                member.getName().getString(), biome.biomeId, levelIndex + 1);
        }

        hostCm.finishPartyJoin();
        CrafticsMod.LOGGER.info("{} started {} (biome {}, level {}, {} player(s))",
            starter.getName().getString(), levelDef.getName(), biome.biomeId,
            levelIndex + 1, participants.size());
    }
}
