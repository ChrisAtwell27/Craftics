package com.crackedgames.craftics.sound;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

import java.util.EnumMap;
import java.util.Map;

/**
 * Registers one {@link SoundEvent} per {@link MusicTracks} entry into the synced
 * {@code SOUND_EVENT} registry. Call {@link #register()} from the main mod
 * initializer (server + client both run it, mirroring vanilla sound registration).
 */
public final class ModSounds {

    private static final Map<MusicTracks, SoundEvent> EVENTS = new EnumMap<>(MusicTracks.class);

    private ModSounds() {}

    public static void register() {
        for (MusicTracks track : MusicTracks.values()) {
            Identifier id = track.id();
            SoundEvent event = SoundEvent.of(id);
            Registry.register(Registries.SOUND_EVENT, id, event);
            EVENTS.put(track, event);
        }
    }

    /** The registered {@link SoundEvent} for a track (null if {@link #register()} hasn't run). */
    public static SoundEvent get(MusicTracks track) {
        return EVENTS.get(track);
    }

    /** Convenience: resolve a track key straight to its {@link SoundEvent}. */
    public static SoundEvent get(String key) {
        MusicTracks t = MusicTracks.byKey(key);
        return t == null ? null : EVENTS.get(t);
    }
}
