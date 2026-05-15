package com.crackedgames.craftics.api;

/**
 * Marks where a Craftics registry entry came from.
 *
 * <p>{@link #CODE} entries are registered from Java — by Craftics itself, by a
 * compat module, or by an addon's {@code onCrafticsInit()}. They persist for the
 * lifetime of the game.
 *
 * <p>{@link #DATAPACK} entries are loaded from {@code data/<namespace>/craftics/...}
 * JSON files. They are cleared and re-read on every {@code /reload}, so editing a
 * datapack JSON takes effect without restarting the server.
 *
 * @since 0.2.0
 */
public enum RegistrationSource {
    /** Registered from Java. Survives {@code /reload}. */
    CODE,
    /** Loaded from a JSON datapack file. Re-read on every {@code /reload}. */
    DATAPACK
}
