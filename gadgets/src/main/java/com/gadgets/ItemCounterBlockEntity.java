package com.gadgets;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

/**
 * Counts items passing the face it points at and pulses redstone every N items.
 *
 * <p>Two automatic modes. If the block it faces is a container (hopper, chest,
 * barrel — and any modded inventory such as a Create depot or vault on NeoForge)
 * it counts the items that <em>leave</em> that container. Otherwise it watches
 * the block space in front for dropped items (belt drop-offs, chutes, thrown
 * items) and counts each stack that passes through once.
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
    /** Which mode the last sample ran in — purely informational, not persisted. */
    private boolean watchingContainer = false;

    public ItemCounterBlockEntity(BlockPos pos, BlockState state) {
        super(Gadgets.ITEM_COUNTER_BE, pos, state);
    }

    public int getThreshold() {
        return threshold;
    }

    public int getCount() {
        return count;
    }

    public boolean isWatchingContainer() {
        return watchingContainer;
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
        markDirty();
        return threshold;
    }

    public static void tick(World world, BlockPos pos, BlockState state, ItemCounterBlockEntity be) {
        if (world.getTime() % INTERVAL == 0L) {
            Direction facing = state.get(ItemCounterBlock.FACING);
            BlockPos target = pos.offset(facing);

            int passed;
            Inventory container = HopperBlockEntity.getInventoryAt(world, target);
            be.watchingContainer = container != null;
            if (container != null) {
                passed = be.sampleContainer(container);
                be.seenEntities.clear(); // not in flow mode; forget any stragglers
            } else {
                passed = be.sampleFlow(world, target);
                be.lastContainerCount = -1; // container gone; reseed if it returns
            }

            if (passed > 0) {
                be.count += passed;
                be.markDirty();
            }
            if (be.count >= be.threshold) {
                be.count %= be.threshold; // a bulk drop fires one pulse, never a runaway
                be.poweredUntil = world.getTime() + PULSE_TICKS;
                be.markDirty();
            }
        }

        boolean powered = world.getTime() < be.poweredUntil;
        if (state.get(ItemCounterBlock.POWERED) != powered) {
            world.setBlockState(pos, state.with(ItemCounterBlock.POWERED, powered), Block.NOTIFY_ALL);
        }
    }

    /** Count the items that have left the container since the last sample. */
    private int sampleContainer(Inventory inv) {
        int total = 0;
        for (int i = 0; i < inv.size(); i++) {
            total += inv.getStack(i).getCount();
        }
        int passed = (lastContainerCount >= 0 && total < lastContainerCount) ? lastContainerCount - total : 0;
        lastContainerCount = total;
        return passed;
    }

    /** Count dropped item entities entering the detection cell — each stack once. */
    private int sampleFlow(World world, BlockPos target) {
        List<ItemEntity> items = world.getEntitiesByClass(ItemEntity.class, new Box(target), e -> true);
        int passed = 0;
        Set<Integer> present = new HashSet<>();
        for (ItemEntity item : items) {
            int id = item.getId();
            present.add(id);
            if (seenEntities.add(id)) {
                passed += item.getStack().getCount();
            }
        }
        seenEntities.retainAll(present); // forget items that have moved on
        return passed;
    }

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        nbt.putInt("Threshold", threshold);
        nbt.putInt("Count", count);
        nbt.putLong("PoweredUntil", poweredUntil);
        nbt.putInt("LastContainerCount", lastContainerCount);
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        if (nbt.contains("Threshold")) {
            threshold = nbt.getInt("Threshold");
        }
        count = nbt.getInt("Count");
        poweredUntil = nbt.getLong("PoweredUntil");
        lastContainerCount = nbt.contains("LastContainerCount") ? nbt.getInt("LastContainerCount") : -1;
    }
}
