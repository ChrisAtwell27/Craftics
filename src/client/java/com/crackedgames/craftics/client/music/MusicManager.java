package com.crackedgames.craftics.client.music;

import com.crackedgames.craftics.sound.ModSounds;
import com.crackedgames.craftics.sound.MusicTracks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.SoundManager;
import net.minecraft.sound.SoundEvent;

/**
 * Client-side playback director. Receives a track key from the server
 * ({@link com.crackedgames.craftics.network.MusicSyncPayload}) and cross-fades to it:
 * the outgoing track fades out (and reaps itself), the incoming one fades in. An empty
 * key means "stop". Pops a bottom-left {@link MusicToast} whenever a new song starts.
 */
public final class MusicManager {

    private static String currentKey = "";
    private static CrafticsMusicSound current = null;

    private MusicManager() {}

    /**
     * Switch to {@code key} (a {@link MusicTracks#key}). {@code null}/empty fades out and
     * plays nothing. Must be called on the client thread.
     */
    public static void request(String key) {
        if (key == null) key = "";
        if (key.equals(currentKey)) return; // already playing this (or already stopped)
        currentKey = key;

        // Fade out the current track; SoundSystem reaps it once it goes silent.
        if (current != null) {
            current.fadeOut();
            current = null;
        }

        MusicTracks track = MusicTracks.byKey(key);
        if (track == null) return; // stop request — nothing to start

        SoundEvent event = ModSounds.get(track);
        if (event == null) return; // unregistered key — ignore defensively

        MinecraftClient mc = MinecraftClient.getInstance();
        SoundManager sm = mc.getSoundManager();
        CrafticsMusicSound sound = new CrafticsMusicSound(event);
        current = sound;
        sm.play(sound);
        sound.fadeIn();

        // "Now playing" toast in the bottom-left corner.
        MusicToast.show(track.displayName, track.source);
    }

    /**
     * Hard-stop immediately with no fade — used when leaving a world so the soundtrack
     * never bleeds onto the title screen.
     */
    public static void stopAll() {
        if (current != null) {
            MinecraftClient.getInstance().getSoundManager().stop(current);
            current.stopNow();
            current = null;
        }
        currentKey = "";
    }
}
