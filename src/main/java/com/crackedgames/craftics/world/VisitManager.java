package com.crackedgames.craftics.world;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Invite-based island visiting. Transient state only - nothing persists.
 *  Visitors are look-only (enforced via Fabric events, see VisitProtection). */
public final class VisitManager {
    private VisitManager() {}

    private static final int EXPIRY_TICKS = 600;

    /** host owner UUID -> pending visitor UUID -> ticks left */
    private static final Map<UUID, Map<UUID, Integer>> PENDING = new HashMap<>();
    /** island owner UUID -> active visitor UUIDs */
    private static final Map<UUID, Set<UUID>> VISITORS = new HashMap<>();

    public static void request(ServerPlayerEntity visitor, ServerPlayerEntity host) {
        var server = visitor.getServer();
        CrafticsSavedData data = CrafticsSavedData.get(server.getOverworld());
        UUID owner = data.getEffectiveWorldOwner(host.getUuid());
        if (owner.equals(data.getEffectiveWorldOwner(visitor.getUuid()))) {
            visitor.sendMessage(Text.literal("§eThat is your own island."), false);
            return;
        }
        if (!data.hasPersonalWorld(owner)) {
            visitor.sendMessage(Text.literal("§c" + host.getName().getString()
                + " doesn't have an island yet."), false);
            return;
        }
        var party = data.getPlayerParty(owner);
        if (party != null && party.isMember(visitor.getUuid())) {
            beginVisit(visitor, owner); // party fast-path, no invite
            return;
        }
        PENDING.computeIfAbsent(owner, k -> new HashMap<>()).put(visitor.getUuid(), EXPIRY_TICKS);
        String name = visitor.getName().getString();
        host.sendMessage(Text.literal("§6" + name + " §ewants to visit your island. ")
            .append(Text.literal("§a[Accept]").styled(s -> s.withClickEvent(acceptClick(name))))
            .append(Text.literal(" "))
            .append(Text.literal("§c[Deny]").styled(s -> s.withClickEvent(denyClick(name)))), false);
        visitor.sendMessage(Text.literal("§eVisit request sent."), false);
    }

    //? if <=1.21.4 {
    private static net.minecraft.text.ClickEvent acceptClick(String name) {
        return new net.minecraft.text.ClickEvent(
            net.minecraft.text.ClickEvent.Action.RUN_COMMAND, "/craftics visit accept " + name);
    }

    private static net.minecraft.text.ClickEvent denyClick(String name) {
        return new net.minecraft.text.ClickEvent(
            net.minecraft.text.ClickEvent.Action.RUN_COMMAND, "/craftics visit deny " + name);
    }
    //?} else {
    /*private static net.minecraft.text.ClickEvent acceptClick(String name) {
        return new net.minecraft.text.ClickEvent.RunCommand("/craftics visit accept " + name);
    }

    private static net.minecraft.text.ClickEvent denyClick(String name) {
        return new net.minecraft.text.ClickEvent.RunCommand("/craftics visit deny " + name);
    }
    *///?}

    public static void respond(ServerPlayerEntity host, ServerPlayerEntity visitor, boolean accept) {
        var server = host.getServer();
        CrafticsSavedData data = CrafticsSavedData.get(server.getOverworld());
        UUID owner = data.getEffectiveWorldOwner(host.getUuid());
        Map<UUID, Integer> pend = PENDING.get(owner);
        if (pend == null || pend.remove(visitor.getUuid()) == null) {
            host.sendMessage(Text.literal("§cNo pending request from that player."), false);
            return;
        }
        if (!accept) {
            visitor.sendMessage(Text.literal("§cVisit request denied."), false);
            return;
        }
        beginVisit(visitor, owner);
        host.sendMessage(Text.literal("§a" + visitor.getName().getString() + " is now visiting."), false);
    }

    private static void beginVisit(ServerPlayerEntity visitor, UUID owner) {
        clearVisit(visitor.getUuid());
        if (com.crackedgames.craftics.combat.CombatManager.isEngaged(visitor.getUuid())) {
            visitor.sendMessage(Text.literal("§cYou cannot visit mid-combat."), false);
            return;
        }
        VISITORS.computeIfAbsent(owner, k -> new HashSet<>()).add(visitor.getUuid());
        HubTeleports.visitHub(visitor, owner);
        visitor.sendMessage(Text.literal("§aVisiting. Look around - interactions are disabled. "
            + "/craftics lobby or /craftics home to leave."), false);
    }

    public static void kick(ServerPlayerEntity host, ServerPlayerEntity visitor) {
        var server = host.getServer();
        CrafticsSavedData data = CrafticsSavedData.get(server.getOverworld());
        UUID owner = data.getEffectiveWorldOwner(host.getUuid());
        Set<UUID> vs = VISITORS.get(owner);
        if (vs == null || !vs.remove(visitor.getUuid())) {
            host.sendMessage(Text.literal("§cThat player is not visiting your island."), false);
            return;
        }
        HubTeleports.toLobby(visitor);
        visitor.sendMessage(Text.literal("§eYou were sent back to the lobby."), false);
    }

    public static boolean isVisitor(UUID player, UUID islandOwner) {
        Set<UUID> vs = VISITORS.get(islandOwner);
        return vs != null && vs.contains(player);
    }

    /** Visitor leaves by any means (teleport home/lobby/logout): drop tracking. */
    public static void clearVisit(UUID player) {
        VISITORS.values().forEach(s -> s.remove(player));
    }

    public static void onOwnerLogout(MinecraftServer server, UUID owner) {
        Set<UUID> vs = VISITORS.remove(owner);
        if (vs != null) for (UUID v : new ArrayList<>(vs)) {
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(v);
            if (p != null) HubTeleports.toLobby(p);
        }
        IslandDimensions.unloadIfEmpty(server, owner);
    }

    public static void tick() {
        PENDING.values().forEach(m -> m.entrySet().removeIf(e -> {
            e.setValue(e.getValue() - 1);
            return e.getValue() <= 0;
        }));
        PENDING.entrySet().removeIf(e -> e.getValue().isEmpty());
    }
}
