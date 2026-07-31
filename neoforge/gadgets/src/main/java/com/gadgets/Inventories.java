package com.gadgets;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.WorldlyContainerHolder;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.wrapper.InvWrapper;
import net.neoforged.neoforge.items.wrapper.SidedInvWrapper;
import org.jetbrains.annotations.Nullable;

/**
 * Finds the inventory of whatever a gadget is pointed at.
 *
 * <p>The item-handler capability is the right first question, and for most
 * blocks it is the only one worth asking. It is not the whole answer though:
 * a block can hold items and never publish a handler — a composter exposes
 * itself only as a container attached to the block rather than to a block
 * entity, and plenty of mods still ship inventories that implement nothing but
 * {@link Container}. Those all used to read as "nothing there".
 *
 * <p>So the capability is tried first and the plain vanilla container is the
 * fallback, wrapped to look like a handler. Chests get one extra courtesy:
 * asked through the block rather than the block entity, a double chest answers
 * as the one inventory a player thinks of it as, instead of just the half the
 * gadget happens to be facing.
 */
public final class Inventories {
    private Inventories() {
    }

    /**
     * The inventory at {@code pos} as seen from {@code side}, or null when there
     * genuinely isn't one.
     */
    @Nullable
    public static IItemHandler at(Level level, BlockPos pos, @Nullable Direction side, CapCache<IItemHandler> cache) {
        IItemHandler handler = cache.get(level, pos, side);
        if (handler != null) {
            return handler;
        }
        Container container = container(level, pos);
        if (container == null) {
            return null;
        }
        return container instanceof WorldlyContainer worldly
                ? new SidedInvWrapper(worldly, side)
                : new InvWrapper(container);
    }

    @Nullable
    private static Container container(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof WorldlyContainerHolder holder) {
            return holder.getContainer(state, level, pos); // composter and friends
        }
        if (!state.hasBlockEntity()) {
            return null;
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (state.getBlock() instanceof ChestBlock chest) {
            // true: read it even with a cat sat on the lid. A gadget is not a
            // player opening it, and a blocked chest still holds what it holds.
            return ChestBlock.getContainer(chest, state, level, pos, true);
        }
        return be instanceof Container c ? c : null;
    }
}
