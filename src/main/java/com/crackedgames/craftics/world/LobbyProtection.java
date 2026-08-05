package com.crackedgames.craftics.world;

import com.crackedgames.craftics.CrafticsMod;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Spawn protection for the central lobby: nobody edits or operates the shared room.
 *
 * <p>The lobby is the one area every player on the server passes through and nobody owns.
 * Personal islands are already covered - {@link VisitProtection} locks a visitor out of
 * someone else's island - but the lobby had no equivalent, so any player could mine the floor
 * out from under the hub portals or empty a decorative chest.
 *
 * <p>Scope is deliberately narrow: the overworld only, inside {@link #RADIUS} blocks of the
 * stored lobby spawn. Everything beyond that is ordinary world. Operators (permission level 2)
 * are exempt so the lobby can still be built and edited in place.
 *
 * <p>Lootbox kiosks are the one interaction that must still work here - they live in the lobby
 * and are the whole point of the room - so their own use handler runs first and returns
 * SUCCESS, which stops the event before this ever sees it.
 */
public final class LobbyProtection {

    private LobbyProtection() {}

    /** Protected radius around the lobby spawn, in blocks. */
    private static final int RADIUS = 64;
    /** Vertical reach of the protection, measured from the spawn Y. */
    private static final int HEIGHT = 48;

    /** Rate-limit the denial message so a player holding left-click isn't spammed. */
    private static final java.util.Map<java.util.UUID, Long> LAST_TOLD = new java.util.HashMap<>();
    private static final long TELL_COOLDOWN_MS = 3000L;

    public static void init() {
        // Breaking: cancel before the block is destroyed.
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, be) ->
            !(isProtected(world, player, pos) && deny(player)));

        // Left-click (attack) on a block: stop the break animation starting at all.
        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) ->
            isProtected(world, player, pos) && deny(player)
                ? ActionResult.FAIL : ActionResult.PASS);

        // Right-click on a block: placing, opening containers, flipping levers, everything.
        UseBlockCallback.EVENT.register((player, world, hand, hit) ->
            isProtected(world, player, hit.getBlockPos()) && deny(player)
                ? ActionResult.FAIL : ActionResult.PASS);

        // Interacting with entities: item frames, armor stands, decorative mobs.
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) ->
            isProtected(world, player, entity.getBlockPos()) && deny(player)
                ? ActionResult.FAIL : ActionResult.PASS);
    }

    /**
     * True when this position is inside the protected lobby volume and the player isn't
     * allowed to edit it. Operators and non-overworld positions always fall through.
     */
    private static boolean isProtected(World world, PlayerEntity player, BlockPos pos) {
        if (world == null || world.isClient()) return false;
        if (!(world instanceof ServerWorld sw)) return false;
        if (!(player instanceof ServerPlayerEntity sp)) return false;
        // Operators build the lobby; protecting it from its own builders would be perverse.
        if (sp.hasPermissionLevel(2) || sp.isCreative()) return false;
        // Lobby only ever exists in the overworld - islands have their own protection.
        if (sw.getServer() == null || sw != sw.getServer().getOverworld()) return false;

        BlockPos spawn = CrafticsSavedData.get(sw).getLobbySpawn();
        if (spawn == null) return false;   // no lobby configured: nothing to protect
        if (Math.abs(pos.getY() - spawn.getY()) > HEIGHT) return false;
        int dx = pos.getX() - spawn.getX();
        int dz = pos.getZ() - spawn.getZ();
        return dx * dx + dz * dz <= RADIUS * RADIUS;
    }

    /** Tell the player why, at most once every few seconds. Always returns true. */
    private static boolean deny(PlayerEntity player) {
        if (player instanceof ServerPlayerEntity sp) {
            long now = System.currentTimeMillis();
            Long last = LAST_TOLD.get(sp.getUuid());
            if (last == null || now - last > TELL_COOLDOWN_MS) {
                LAST_TOLD.put(sp.getUuid(), now);
                sp.sendMessage(Text.literal("§cThe lobby is protected. §7Use §e/home§7 for your own island."), true);
            }
        }
        return true;
    }

    /** Log the configured area once at startup so an admin can see what's covered. */
    public static void logConfig(ServerWorld overworld) {
        BlockPos spawn = CrafticsSavedData.get(overworld).getLobbySpawn();
        if (spawn == null) {
            CrafticsMod.LOGGER.info("Lobby protection idle - no lobby spawn set "
                + "(/craftics lobby setspawn to enable).");
        } else {
            CrafticsMod.LOGGER.info("Lobby protection active: {} blocks around {}", RADIUS, spawn);
        }
    }
}
