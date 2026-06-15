package com.crackedgames.craftics.vfx.instruments;

import com.crackedgames.craftics.compat.instruments.InstrumentDef;
import com.crackedgames.craftics.core.GridPos;
import com.crackedgames.craftics.vfx.VfxAnchor;
import com.crackedgames.craftics.vfx.VfxDescriptor;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import java.util.List;

/**
 * Builds a per-instrument VfxDescriptor: windup -> beat -> settle. The note
 * particles burst on the actual affected tiles (so the player sees exactly where
 * the performance lands), and all sounds are the instrument's own, resolved at
 * runtime.
 */
public final class InstrumentVfx {

    private InstrumentVfx() {}

    /** Cap on tiles we spawn per-tile particles on, so a Full Arena cast doesn't flood. */
    private static final int MAX_PARTICLE_TILES = 49;

    /** Resolve a mod sound by index into def.soundIds(); null if out of range or not registered. */
    private static SoundEvent sound(InstrumentDef def, int index) {
        if (index < 0 || index >= def.soundIds().size()) return null;
        Identifier id = Identifier.of(def.modId(), def.soundIds().get(index));
        return Registries.SOUND_EVENT.containsId(id) ? Registries.SOUND_EVENT.get(id) : null;
    }

    /** Note-particle burst on one affected tile (lifted just above the floor). */
    private static VfxAnchor tileAnchor(GridPos tile) {
        return new VfxAnchor.AtGridTile(tile.x(), tile.z(), 0.3);
    }

    /**
     * Build the descriptor for an instrument performance over {@code tiles} (the
     * resolved shape, in arena grid coords). Phase 0 is a windup converging notes
     * on the player; phase 6 is the beat, bursting {@link ParticleTypes#NOTE} on
     * each affected tile (this is what shows where the shape lands), with attack
     * instruments adding camera shake / screen flash / hit-pause; phase 12 is a
     * lighter per-tile settle. Sounds are the instrument's own note set.
     *
     * <p>Per-instrument note tint ({@code noteColor}) is reserved for a later
     * colored-note pass; v1 uses the music note's default hue.
     */
    public static VfxDescriptor describe(InstrumentDef def, List<GridPos> tiles) {
        boolean attack = def.role() == InstrumentDef.Role.ATTACK;
        VfxDescriptor.Builder b = VfxDescriptor.builder();

        // Phase 0 - windup: notes converge on the player as the instrument is played.
        var p0 = b.phase(0)
            .converge(VfxAnchor.ORIGIN, 1.6, ParticleTypes.NOTE, 14);
        SoundEvent windup = sound(def, 0);
        if (windup != null) p0.sound(VfxAnchor.ORIGIN, windup, 0.8f, attack ? 0.8f : 1.0f);

        // Phase 6 - the beat: burst notes on EACH affected tile so the shape reads
        // clearly (a star looks like a star, a cone like a cone). Attack adds impact.
        var p1 = b.phase(6);
        int drawn = 0;
        for (GridPos tile : tiles) {
            if (drawn++ >= MAX_PARTICLE_TILES) break;
            p1.particles(ParticleTypes.NOTE, tileAnchor(tile),
                attack ? 6 : 5, new Vec3d(0.25, 0.25, 0.25), 0.08);
            if (attack) {
                p1.particles(ParticleTypes.CRIT, tileAnchor(tile), 2, new Vec3d(0.2, 0.2, 0.2), 0.1);
            }
        }
        SoundEvent beat = sound(def, 1);
        if (beat != null) p1.sound(VfxAnchor.ORIGIN, beat, attack ? 1.3f : 0.9f, attack ? 0.9f : 1.1f);
        if (attack) {
            p1.shake(0.5f, 5).screenFlash(0x33FFCC66, 3);
            if (def.apCost() >= 3) p1.shake(0.9f, 8).hitPause(3);
        }

        // Phase 12 - settle: a lighter note drift on each tile + an accent note.
        var p2 = b.phase(12);
        drawn = 0;
        for (GridPos tile : tiles) {
            if (drawn++ >= MAX_PARTICLE_TILES) break;
            p2.particles(ParticleTypes.NOTE, tileAnchor(tile), 3, new Vec3d(0.2, 0.35, 0.2), 0.06);
        }
        SoundEvent tail = sound(def, 2);
        if (tail != null) p2.sound(VfxAnchor.ORIGIN, tail, 0.9f, attack ? 1.1f : 1.2f);

        return b.build();
    }
}
