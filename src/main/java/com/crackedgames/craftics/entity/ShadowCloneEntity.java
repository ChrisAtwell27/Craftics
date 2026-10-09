package com.crackedgames.craftics.entity;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * The body of a Shadow: the End City boss, which is a dark copy of a player.
 *
 * <p>Deliberately almost empty. Everything a Shadow DOES is combat logic and lives with the
 * rest of it ({@code ShadowCloneAI}). This class exists for one reason: no vanilla mob can be
 * drawn with a player's model and a player's skin, so the game needs an entity type of its
 * own to hang that renderer on. It carries the one fact the renderer needs and the server
 * knows - whose skin to wear - and nothing else.
 *
 * <p>The owner is not saved. A Shadow only exists inside a fight, and one left behind by a
 * crash is an orphan the arena sweep removes; there is no later moment at which its skin
 * would matter.
 */
public class ShadowCloneEntity extends PathAwareEntity {

    /** The copied player's UUID as text, or empty for a Shadow of nobody. Synced to clients. */
    private static final TrackedData<String> OWNER =
        DataTracker.registerData(ShadowCloneEntity.class, TrackedDataHandlerRegistry.STRING);

    public ShadowCloneEntity(EntityType<? extends ShadowCloneEntity> type, World world) {
        super(type, world);
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return MobEntity.createMobAttributes();
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(OWNER, "");
    }

    /** Set whose skin this Shadow wears. Null clears it back to the default skin. */
    public void setOwnerUuid(@Nullable UUID owner) {
        this.dataTracker.set(OWNER, owner == null ? "" : owner.toString());
    }

    /** The player this Shadow copies, or null when it copies nobody. */
    @Nullable
    public UUID getOwnerUuid() {
        String raw = this.dataTracker.get(OWNER);
        if (raw == null || raw.isEmpty()) return null;
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
