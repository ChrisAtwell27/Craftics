package com.crackedgames.craftics.network;

import com.crackedgames.craftics.CrafticsMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * S2C: Full combat state sync.
 * enemyData is a flat array: [entityId, currentHp, maxHp, entityId, currentHp, maxHp, ...]
 * enemyTypeIds is a pipe-separated string of entity type IDs in the same order as enemyData triplets.
 */
public record CombatSyncPayload(int phase, int ap, int movePoints,
                                 int playerHp, int playerMaxHp, int turnNumber,
                                 int maxAp, int maxSpeed,
                                 int[] enemyData, String enemyTypeIds,
                                 String playerEffects, int killStreak) implements CustomPayload {

    public static final CustomPayload.Id<CombatSyncPayload> ID =
        new CustomPayload.Id<>(Identifier.of(CrafticsMod.MOD_ID, "combat_sync"));

    public static final PacketCodec<RegistryByteBuf, CombatSyncPayload> CODEC =
        new PacketCodec<>() {
            @Override
            public CombatSyncPayload decode(RegistryByteBuf buf) {
                int phase = buf.readInt();
                int ap = buf.readInt();
                int movePoints = buf.readInt();
                int playerHp = buf.readInt();
                int playerMaxHp = buf.readInt();
                int turnNumber = buf.readInt();
                int maxAp = buf.readInt();
                int maxSpeed = buf.readInt();
                int enemyCount = buf.readInt();
                int[] enemyData = new int[enemyCount * 3];
                for (int i = 0; i < enemyData.length; i++) {
                    enemyData[i] = buf.readInt();
                }
                String enemyTypeIds = buf.readString();
                String playerEffects = buf.readString();
                int killStreak = buf.readInt();
                return new CombatSyncPayload(phase, ap, movePoints, playerHp, playerMaxHp, turnNumber, maxAp, maxSpeed, enemyData, enemyTypeIds, playerEffects, killStreak);
            }

            @Override
            public void encode(RegistryByteBuf buf, CombatSyncPayload payload) {
                buf.writeInt(payload.phase);
                buf.writeInt(payload.ap);
                buf.writeInt(payload.movePoints);
                buf.writeInt(payload.playerHp);
                buf.writeInt(payload.playerMaxHp);
                buf.writeInt(payload.turnNumber);
                buf.writeInt(payload.maxAp);
                buf.writeInt(payload.maxSpeed);
                buf.writeInt(payload.enemyData.length / 3);
                for (int v : payload.enemyData) {
                    buf.writeInt(v);
                }
                buf.writeString(payload.enemyTypeIds);
                buf.writeString(payload.playerEffects);
                buf.writeInt(payload.killStreak);
            }
        };

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
