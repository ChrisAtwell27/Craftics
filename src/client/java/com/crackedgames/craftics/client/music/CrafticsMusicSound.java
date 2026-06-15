package com.crackedgames.craftics.client.music;

import net.minecraft.client.sound.AbstractSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.TickableSoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.random.Random;

/**
 * A single looping, global (non-positional) music track with its own software volume
 * envelope for fade in / out. Played under the {@code RECORD} category (the in-game
 * "Jukebox/Note Blocks" slider). Driven by {@link net.minecraft.client.sound.SoundSystem},
 * which calls {@link #tick()} every client tick and re-reads {@link #getVolume()} to update
 * the channel gain - so adjusting {@link #volume} here produces an audible fade.
 */
public class CrafticsMusicSound extends AbstractSoundInstance implements TickableSoundInstance {

    /** Ticks to go from silent to full (and full to silent). ~2 seconds. */
    private static final int FADE_TICKS = 40;
    private static final float FADE_STEP = 1.0f / FADE_TICKS;

    /** Target the envelope is moving toward: 1 = full, 0 = silent. */
    private float targetVolume = 1.0f;
    private boolean done = false;

    public CrafticsMusicSound(SoundEvent sound) {
        super(sound, SoundCategory.RECORDS, Random.create());
        this.repeat = true;          // seamless loop
        this.repeatDelay = 0;
        // Start at a tiny NON-ZERO volume, not 0: SoundSystem refuses to start any sound
        // whose initial volume is 0 ("Skipped playing sound, volume was zero"), so it would
        // never stream and the fade-in in tick() would never run. We ramp up from here.
        this.volume = 0.01f;
        this.pitch = 1.0f;
        this.attenuationType = SoundInstance.AttenuationType.NONE; // global, no distance falloff
        this.relative = true;        // position follows the listener (non-directional)
        this.x = 0.0;
        this.y = 0.0;
        this.z = 0.0;
    }

    /** Begin (or resume) fading toward full volume. */
    public void fadeIn() {
        this.targetVolume = 1.0f;
    }

    /** Begin fading out; the instance ends itself once silent. */
    public void fadeOut() {
        this.targetVolume = 0.0f;
    }

    /** Stop immediately (no fade), e.g. on world disconnect. */
    public void stopNow() {
        this.targetVolume = 0.0f;
        this.volume = 0.0f;
        this.done = true;
    }

    public boolean isFadingOut() {
        return this.targetVolume <= 0.0f;
    }

    @Override
    public boolean isDone() {
        return done;
    }

    @Override
    public void tick() {
        if (volume < targetVolume) {
            volume = Math.min(targetVolume, volume + FADE_STEP);
        } else if (volume > targetVolume) {
            volume = Math.max(targetVolume, volume - FADE_STEP);
        }
        // Finished fading out → let the sound system reap this instance.
        if (targetVolume <= 0.0f && volume <= 0.0f) {
            done = true;
        }
    }
}
