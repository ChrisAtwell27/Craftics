package com.crackedgames.craftics.vfx;

import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleType;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VfxDescriptorBuilderTest {

    // Lightweight stand-ins that don't require Minecraft Bootstrap.
    // The builder stores them as opaque values; tests only check instanceof / counts.
    private static final ParticleEffect FLAME_STUB = new ParticleEffect() {
        @Override public ParticleType<?> getType() { throw new UnsupportedOperationException(); }
    };

    private static final SoundEvent SOUND_STUB =
        SoundEvent.of(Identifier.of("craftics", "test_sound"));

    @Test
    void buildsEmptyDescriptor() {
        VfxDescriptor desc = VfxDescriptor.builder().build();
        assertTrue(desc.phases().isEmpty());
    }

    @Test
    void buildsSinglePhaseWithMultiplePrimitives() {
        VfxDescriptor desc = VfxDescriptor.builder()
            .phase(0)
                .particles(FLAME_STUB, VfxAnchor.ORIGIN, 5, new Vec3d(0.1, 0.1, 0.1), 0.05)
                .sound(VfxAnchor.ORIGIN, SOUND_STUB, 1.0f, 1.0f)
                .shake(0.5f, 8)
            .build();

        assertEquals(1, desc.phases().size());
        VfxPhase p = desc.phases().get(0);
        assertEquals(0, p.delayTicks());
        assertEquals(3, p.primitives().size());
        assertInstanceOf(VfxPrimitive.SpawnParticles.class, p.primitives().get(0));
        assertInstanceOf(VfxPrimitive.PlaySound.class, p.primitives().get(1));
        assertInstanceOf(VfxPrimitive.Shake.class, p.primitives().get(2));
    }

    @Test
    void multiplePhasesPreserveOrderAndDelay() {
        VfxDescriptor desc = VfxDescriptor.builder()
            .phase(0).shake(0.2f, 4)
            .phase(5).shake(0.9f, 10)
            .phase(12).hitPause(3)
            .build();

        assertEquals(3, desc.phases().size());
        assertEquals(0, desc.phases().get(0).delayTicks());
        assertEquals(5, desc.phases().get(1).delayTicks());
        assertEquals(12, desc.phases().get(2).delayTicks());
    }

    @Test
    void sameDelayOnMultiplePhasesAllowed() {
        VfxDescriptor desc = VfxDescriptor.builder()
            .phase(3).shake(0.2f, 4)
            .phase(3).hitPause(2)
            .build();
        assertEquals(2, desc.phases().size());
    }

    @Test
    void descriptorPhasesListIsImmutable() {
        VfxDescriptor desc = VfxDescriptor.builder().phase(0).shake(0.1f, 1).build();
        assertThrows(UnsupportedOperationException.class,
            () -> desc.phases().add(new VfxPhase(99, java.util.List.of())));
    }
}
