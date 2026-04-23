package com.crackedgames.craftics.component;

import com.crackedgames.craftics.combat.animation.AnimState;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;

/**
 * Per-mob animation state used by the client-side anim mixins. Auto-syncs to
 * all clients tracking the entity so the pose is visible in multiplayer.
 * Attached to every {@link net.minecraft.entity.mob.MobEntity} — the mixin
 * gates further on the {@code craftics_arena} command tag.
 */
public class CrafticsAnimComponent implements AutoSyncedComponent {

    private final Entity provider;
    private AnimState state = AnimState.IDLE;
    private long startTick = 0L;

    public CrafticsAnimComponent(Entity provider) {
        this.provider = provider;
    }

    public AnimState getState() {
        return state;
    }

    public long getStartTick() {
        return startTick;
    }

    public void setState(AnimState next, long now) {
        if (this.state == next && next != AnimState.HIT) return;
        this.state = next;
        this.startTick = now;
        CrafticsComponents.ANIM.sync(provider);
    }

    public void reset(long now) {
        setState(AnimState.IDLE, now);
    }

    @Override
    public void readFromNbt(NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        //? if <=1.21.4 {
        int ord = tag.getInt("state");
        this.startTick = tag.getLong("startTick");
        //?} else {
        /*int ord = tag.getInt("state", 0);
        this.startTick = tag.getLong("startTick", 0L);
        *///?}
        AnimState[] values = AnimState.values();
        this.state = (ord >= 0 && ord < values.length) ? values[ord] : AnimState.IDLE;
    }

    @Override
    public void writeToNbt(NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        tag.putInt("state", state.ordinal());
        tag.putLong("startTick", startTick);
    }
}
