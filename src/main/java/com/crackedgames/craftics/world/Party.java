package com.crackedgames.craftics.world;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;

import java.util.*;

/**
 * A persistent group of players that can run biomes together.
 * One player is the leader who controls biome entry.
 */
public class Party {
    private final UUID partyId;
    private UUID leaderUuid;
    private String name = "";
    private final Set<UUID> memberUuids = new LinkedHashSet<>();
    private final Set<UUID> pendingInvites = new HashSet<>();

    public Party(UUID partyId, UUID leaderUuid) {
        this.partyId = partyId;
        this.leaderUuid = leaderUuid;
        this.memberUuids.add(leaderUuid);
    }

    public UUID getPartyId() { return partyId; }
    public UUID getLeaderUuid() { return leaderUuid; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name != null ? name : ""; }
    public String getDisplayName() { return name.isEmpty() ? "Party" : name; }
    public Set<UUID> getMemberUuids() { return Collections.unmodifiableSet(memberUuids); }
    public Set<UUID> getPendingInvites() { return Collections.unmodifiableSet(pendingInvites); }
    public int size() { return memberUuids.size(); }
    public boolean isLeader(UUID playerId) { return leaderUuid.equals(playerId); }
    public boolean isMember(UUID playerId) { return memberUuids.contains(playerId); }
    public boolean hasInvite(UUID playerId) { return pendingInvites.contains(playerId); }

    public void addMember(UUID playerId) {
        memberUuids.add(playerId);
        pendingInvites.remove(playerId);
    }

    public void removeMember(UUID playerId) {
        memberUuids.remove(playerId);
        if (leaderUuid.equals(playerId) && !memberUuids.isEmpty()) {
            leaderUuid = memberUuids.iterator().next();
        }
    }

    public void addInvite(UUID playerId) {
        pendingInvites.add(playerId);
    }

    public void removeInvite(UUID playerId) {
        pendingInvites.remove(playerId);
    }

    public boolean isEmpty() {
        return memberUuids.isEmpty();
    }

    public void transferLeadership(UUID newLeader) {
        if (memberUuids.contains(newLeader)) {
            this.leaderUuid = newLeader;
        }
    }

    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putUuid("partyId", partyId);
        nbt.putUuid("leaderUuid", leaderUuid);
        nbt.putString("name", name);
        NbtList membersNbt = new NbtList();
        for (UUID uuid : memberUuids) {
            membersNbt.add(NbtString.of(uuid.toString()));
        }
        nbt.put("members", membersNbt);
        return nbt;
    }

    public static Party fromNbt(NbtCompound nbt) {
        UUID partyId = nbt.getUuid("partyId");
        UUID leaderUuid = nbt.getUuid("leaderUuid");
        Party party = new Party(partyId, leaderUuid);
        if (nbt.contains("name")) {
            party.name = nbt.getString("name");
        }
        NbtList membersNbt = nbt.getList("members", net.minecraft.nbt.NbtElement.STRING_TYPE);
        for (int i = 0; i < membersNbt.size(); i++) {
            UUID memberUuid = UUID.fromString(membersNbt.getString(i));
            if (!memberUuid.equals(leaderUuid)) {
                party.memberUuids.add(memberUuid);
            }
        }
        return party;
    }
}
