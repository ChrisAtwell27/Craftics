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

    /**
     * True when {@code player} may use the thing {@code suffix} names.
     *
     * <p>Deliberately NOT {@code Permissions.check(player, ...)}. The permissions API ships one
     * prebuilt jar for every Minecraft version, and its Entity overload reaches for the no-arg
     * {@code Entity.getCommandSource()} - which grew a world parameter in <b>1.21.3</b>. That
     * jar is still pinned to the old signature, so the call compiled on every shard and then
     * threw NoSuchMethodError at runtime on 1.21.3, 1.21.4 and 1.21.5 alike, taking the server
     * tick loop down with it. Only 1.21.1 was ever safe.
     *
     * <p>The trigger was as cheap as it gets: this is reached from the lobby-protection
     * AttackBlockCallback, so any non-creative player left-clicking any block killed the
     * server. Resolving the source ourselves keeps the broken overload permanently unreached.
     */
    public static boolean check(ServerPlayerEntity player, String suffix) {
        // No version split needed, and deliberately so: ServerPlayerEntity has a no-arg
        // getCommandSource on every shard - Entity's own on 1.21.1, its own from 1.21.3 on.
        // It is only Entity's that grew a world parameter, and only the prebuilt library
        // that is still pinned to the old one. A stonecutter block here would be a second
        // version boundary to keep in step for no benefit.
        return check(player.getCommandSource(), suffix);
    }

    /** The guard to hand a Brigadier {@code .requires(...)}. */
    public static Predicate<ServerCommandSource> require(String suffix) {
        return src -> check(src, suffix);
    }
}
