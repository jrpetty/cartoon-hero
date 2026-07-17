package com.gadgets;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Watches the container it faces and shows the live count of one chosen item on
 * its screen. When that count falls below the alert level it emits redstone —
 * wire it to an Item Sender or dropper for hands-off restocking. Set the tracked
 * item by right-clicking with it; set the alert level with an empty hand. Reads
 * through the item-handler capability, so Create depots/vaults work too.
 */
public class StockMonitorBlockEntity extends BlockEntity {
    private static final int INTERVAL = 10;
    private static final int[] THRESHOLDS = {1, 8, 16, 32, 64, 128, 256};

    private Item tracked = Items.AIR;
    private int threshold = 16;
    private long count = 0;
    private boolean low = false;

    public StockMonitorBlockEntity(BlockPos pos, BlockState state) {
        super(Gadgets.STOCK_MONITOR_BE.get(), pos, state);
    }

    public Item getTracked() {
        return tracked;
    }

    public int getThreshold() {
        return threshold;
    }

    public long getCount() {
        return count;
    }

    public boolean isLow() {
        return low;
    }

    public void setTracked(Item item) {
        this.tracked = item;
        sync();
    }

    /** Advance the low-stock alert level and return it. */
    public int cycleThreshold() {
        int idx = 0;
        for (int i = 0; i < THRESHOLDS.length; i++) {
            if (THRESHOLDS[i] == threshold) {
                idx = i;
                break;
            }
        }
        threshold = THRESHOLDS[(idx + 1) % THRESHOLDS.length];
        sync();
        return threshold;
    }

    /** Big line on the screen: the current stock, or a dash when nothing is tracked. */
    public String faceValue() {
        return tracked == Items.AIR ? "—" : ItemCounterBlockEntity.compact(count);
    }

    /** Small line on the screen: the tracked item's name, or a prompt. */
    public String faceLabel() {
        if (tracked == Items.AIR) {
            return "set item";
        }
        String name = tracked.getDescription().getString();
        return name.length() > 14 ? name.substring(0, 13) + "…" : name;
    }

    public static void tick(Level level, BlockPos pos, BlockState state, StockMonitorBlockEntity be) {
        if (level.getGameTime() % INTERVAL != 0L) {
            return;
        }
        long found = 0;
        if (be.tracked != Items.AIR) {
            Direction facing = state.getValue(StockMonitorBlock.FACING);
            IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos.relative(facing), facing.getOpposite());
            if (handler != null) {
                for (int i = 0; i < handler.getSlots(); i++) {
                    ItemStack stack = handler.getStackInSlot(i);
                    if (stack.is(be.tracked)) {
                        found += stack.getCount();
                    }
                }
            }
        }
        boolean lowNow = be.tracked != Items.AIR && found < be.threshold;

        boolean changed = found != be.count || lowNow != be.low;
        be.count = found;
        be.low = lowNow;
        if (state.getValue(StockMonitorBlock.LOW) != lowNow) {
            level.setBlock(pos, state.setValue(StockMonitorBlock.LOW, lowNow), Block.UPDATE_ALL);
        }
        if (changed) {
            be.sync();
        }
    }

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    // --- sync + persistence ---

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("Tracked", BuiltInRegistries.ITEM.getKey(tracked).toString());
        tag.putInt("Threshold", threshold);
        tag.putLong("Count", count);
        tag.putBoolean("Low", low);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ResourceLocation id = ResourceLocation.tryParse(tag.getString("Tracked"));
        tracked = id == null ? Items.AIR : BuiltInRegistries.ITEM.get(id);
        if (tag.contains("Threshold")) {
            threshold = tag.getInt("Threshold");
        }
        count = tag.getLong("Count");
        low = tag.getBoolean("Low");
    }
}
