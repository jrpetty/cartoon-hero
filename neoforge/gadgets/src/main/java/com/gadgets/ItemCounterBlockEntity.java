package com.gadgets;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Counts items passing the face it points at and pulses redstone every N items.
 *
 * <p>Two automatic modes. If the block it faces exposes an item handler (a
 * hopper, chest, barrel — and any modded inventory such as a Create depot,
 * vault or basin) it counts the items that <em>leave</em> that container.
 * Otherwise it watches the block space in front for dropped items (belt
 * drop-offs, chutes, thrown items) and counts each stack that passes once.
 */
public class ItemCounterBlockEntity extends BlockEntity {
    private static final int INTERVAL = 2;
    private static final int PULSE_TICKS = 4;
    private static final int[] THRESHOLDS = {1, 4, 8, 16, 32, 64};

    private int threshold = 8;
    private int count = 0;
    private long poweredUntil = 0L;

    /** Total item count of the watched container on the previous sample; -1 = reseed. */
    private int lastContainerCount = -1;
    /** Ids of item entities already counted while they sit in the detection cell. */
    private final Set<Integer> seenEntities = new HashSet<>();

    public ItemCounterBlockEntity(BlockPos pos, BlockState state) {
        super(Gadgets.ITEM_COUNTER_BE.get(), pos, state);
    }

    public int getThreshold() {
        return threshold;
    }

    public int getCount() {
        return count;
    }

    /** Advance to the next pulse size and restart the running count. */
    public int cycleThreshold() {
        int idx = 0;
        for (int i = 0; i < THRESHOLDS.length; i++) {
            if (THRESHOLDS[i] == threshold) {
                idx = i;
                break;
            }
        }
        threshold = THRESHOLDS[(idx + 1) % THRESHOLDS.length];
        count = 0;
        lastContainerCount = -1;
        seenEntities.clear();
        setChanged();
        return threshold;
    }

    public static void tick(Level level, BlockPos pos, BlockState state, ItemCounterBlockEntity be) {
        if (level.getGameTime() % INTERVAL == 0L) {
            Direction facing = state.getValue(ItemCounterBlock.FACING);
            BlockPos target = pos.relative(facing);

            int passed;
            IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, target, facing.getOpposite());
            if (handler != null) {
                passed = be.sampleContainer(handler);
                be.seenEntities.clear(); // not in flow mode; forget any stragglers
            } else {
                passed = be.sampleFlow(level, target);
                be.lastContainerCount = -1; // container gone; reseed if it returns
            }

            if (passed > 0) {
                be.count += passed;
                be.setChanged();
            }
            if (be.count >= be.threshold) {
                be.count %= be.threshold; // a bulk drop fires one pulse, never a runaway
                be.poweredUntil = level.getGameTime() + PULSE_TICKS;
                be.setChanged();
            }
        }

        boolean powered = level.getGameTime() < be.poweredUntil;
        if (state.getValue(ItemCounterBlock.POWERED) != powered) {
            level.setBlock(pos, state.setValue(ItemCounterBlock.POWERED, powered), Block.UPDATE_ALL);
        }
    }

    /** Count the items that have left the container since the last sample. */
    private int sampleContainer(IItemHandler handler) {
        int total = 0;
        for (int i = 0; i < handler.getSlots(); i++) {
            total += handler.getStackInSlot(i).getCount();
        }
        int passed = (lastContainerCount >= 0 && total < lastContainerCount) ? lastContainerCount - total : 0;
        lastContainerCount = total;
        return passed;
    }

    /** Count dropped item entities entering the detection cell — each stack once. */
    private int sampleFlow(Level level, BlockPos target) {
        List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, new AABB(target));
        int passed = 0;
        Set<Integer> present = new HashSet<>();
        for (ItemEntity item : items) {
            int id = item.getId();
            present.add(id);
            if (seenEntities.add(id)) {
                passed += item.getItem().getCount();
            }
        }
        seenEntities.retainAll(present); // forget items that have moved on
        return passed;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Threshold", threshold);
        tag.putInt("Count", count);
        tag.putLong("PoweredUntil", poweredUntil);
        tag.putInt("LastContainerCount", lastContainerCount);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Threshold")) {
            threshold = tag.getInt("Threshold");
        }
        count = tag.getInt("Count");
        poweredUntil = tag.getLong("PoweredUntil");
        lastContainerCount = tag.contains("LastContainerCount") ? tag.getInt("LastContainerCount") : -1;
    }
}
