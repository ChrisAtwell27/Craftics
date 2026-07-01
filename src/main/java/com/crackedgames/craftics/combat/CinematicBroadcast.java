package com.crackedgames.craftics.combat;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

/** Shared cinematic helper: push a walked player's absolute position to OTHER nearby clients each
 *  tick so remote players see continuous motion (setPosition alone does not flush a tracker update
 *  at the walk cadence). Used by both combat event walk-ups and the merchant-scene walker. */
public final class CinematicBroadcast {
    private CinematicBroadcast() {}

    public static void broadcastPlayerPositionToOthers(ServerPlayerEntity p) {
        if (p == null || p.getEntityWorld() == null) return;
        if (!(p.getEntityWorld() instanceof ServerWorld sw)) return;
        //? if <=1.21.1 {
        net.minecraft.network.packet.Packet<?> packet =
            new net.minecraft.network.packet.s2c.play.EntityPositionS2CPacket(p);
        //?} else {
        /*// 1.21.3+ split the legacy EntityPositionS2CPacket (absolute teleport
        // taking an Entity) into a new relative-position packet, and introduced
        // EntityPositionSyncS2CPacket for the absolute-sync use case.
        net.minecraft.network.packet.Packet<?> packet =
            net.minecraft.network.packet.s2c.play.EntityPositionSyncS2CPacket.create(p);
        *///?}
        sw.getChunkManager().sendToOtherNearbyPlayers(p, packet);
    }
}
