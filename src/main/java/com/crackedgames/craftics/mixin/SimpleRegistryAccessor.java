package com.crackedgames.craftics.mixin;

import it.unimi.dsi.fastutil.objects.ObjectList;
import net.minecraft.registry.SimpleRegistry;
import net.minecraft.registry.entry.RegistryEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Access to SimpleRegistry's raw-id list for {@code RegistryHealthScanner}'s
 * hole compaction: Fabric registry-sync leaves null slots in this list when the
 * client joins a server whose mods register entries the client doesn't have,
 * and then its own disconnect unmap NPEs iterating them.
 */
@Mixin(SimpleRegistry.class)
public interface SimpleRegistryAccessor<T> {
    @Accessor("rawIdToEntry")
    ObjectList<RegistryEntry.Reference<T>> craftics$rawIdToEntry();
}
