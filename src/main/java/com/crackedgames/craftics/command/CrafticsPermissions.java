package com.crackedgames.craftics.command;

import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.function.Predicate;

/**
 * Permission checks for Craftics' admin surface, asked of a permissions provider first.
 *
 * <p>Every admin command used to guard itself with {@code src.hasPermissionLevel(2)}, which
 * reads the server's op list directly. Nothing can intervene in that: LuckPerms and every
 * other manager on Fabric answer through {@code fabric-permissions-api}, and a command that
 * never asks the API can never be delegated. The practical effect was that running a server
 * meant opping anyone who needed to place a lootbox kiosk, which hands them the whole game.
 *
 * <p>Each check names a node and passes {@value #ADMIN_LEVEL} as the fallback, so with no
 * permissions mod installed the answer is the op level and behaviour is exactly what it was.
 * Grant {@code craftics.command.*} for the old all-or-nothing shape, or individual nodes to
 * hand out one command at a time.
 */
public final class CrafticsPermissions {

    private CrafticsPermissions() {}

    /** Op level a check falls back to when no permissions provider is installed. */
    public static final int ADMIN_LEVEL = 2;

    /** Namespaced form of a node suffix, e.g. {@code "command.set_level"}. */
    public static String node(String suffix) {
        return "craftics." + suffix;
    }

    /** True when {@code src} may use the thing {@code suffix} names. */
    public static boolean check(ServerCommandSource src, String suffix) {
        return Permissions.check(src, node(suffix), ADMIN_LEVEL);
    }

    /** True when {@code player} may use the thing {@code suffix} names. */
    public static boolean check(ServerPlayerEntity player, String suffix) {
        return Permissions.check(player, node(suffix), ADMIN_LEVEL);
    }

    /** The guard to hand a Brigadier {@code .requires(...)}. */
    public static Predicate<ServerCommandSource> require(String suffix) {
        return src -> check(src, suffix);
    }
}
