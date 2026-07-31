package com.gadgets;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import org.jetbrains.annotations.Nullable;

/**
 * A kept handle on the capability of the block a gadget is pointed at.
 *
 * <p>Asking the level for a capability is not free — it looks up the chunk, the
 * block entity, and then the provider, every single time. A counter does that
 * every other tick for as long as it exists, which over a base full of gadgets
 * adds up to a lot of repeated work for an answer that almost never changes.
 *
 * <p>NeoForge's {@link BlockCapabilityCache} holds the resolved capability and
 * listens for invalidation, so a chest being broken, replaced or upgraded still
 * takes effect immediately — the gadget just stops paying for the lookup while
 * nothing is changing. The cache rebuilds itself if the gadget is ever pointed
 * somewhere new, and reports itself dead once its owner is removed so the level
 * can drop the listener instead of holding it forever.
 */
public final class CapCache<T> {
    private final BlockCapability<T, Direction> capability;
    private final BlockEntity owner;

    @Nullable
    private BlockCapabilityCache<T, Direction> cache;
    @Nullable
    private BlockPos watching;
    @Nullable
    private Direction side;

    public CapCache(BlockCapability<T, Direction> capability, BlockEntity owner) {
        this.capability = capability;
        this.owner = owner;
    }

    /**
     * The capability of {@code target} as seen from {@code side}, or null if
     * there is nothing there to talk to.
     */
    @Nullable
    public T get(Level level, BlockPos target, @Nullable Direction side) {
        if (!(level instanceof ServerLevel server)) {
            return null; // gadget logic is server-side only; nothing to cache for
        }
        if (cache == null || !target.equals(watching) || side != this.side) {
            watching = target.immutable();
            this.side = side;
            cache = BlockCapabilityCache.create(capability, server, watching, side,
                    () -> !owner.isRemoved(), () -> {
                    });
        }
        return cache.getCapability();
    }
}
