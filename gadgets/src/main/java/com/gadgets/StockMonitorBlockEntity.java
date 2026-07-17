package com.gadgets;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

/**
 * Watches the container it faces and shows the live count of one chosen item on
 * its screen. When that count falls below the alert level it emits redstone —
 * wire it to an Item Sender or dropper for hands-off restocking. Set the tracked
 * item by right-clicking with it; set the alert level with an empty hand.
 */
public class StockMonitorBlockEntity extends BlockEntity {
    private static final int INTERVAL = 10;
    private static final int[] THRESHOLDS = {1, 8, 16, 32, 64, 128, 256};

    private Item tracked = Items.AIR;
    private int threshold = 16;
    private long count = 0;
    private boolean low = false;

    public StockMonitorBlockEntity(BlockPos pos, BlockState state) {
        super(Gadgets.STOCK_MONITOR_BE, pos, state);
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
        String name = tracked.getName().getString();
        return name.length() > 14 ? name.substring(0, 13) + "…" : name;
    }

    public static void tick(World world, BlockPos pos, BlockState state, StockMonitorBlockEntity be) {
        if (world.getTime() % INTERVAL != 0L) {
            return;
        }
        long found = 0;
        if (be.tracked != Items.AIR) {
            Inventory inv = HopperBlockEntity.getInventoryAt(world, pos.offset(state.get(StockMonitorBlock.FACING)));
            if (inv != null) {
                for (int i = 0; i < inv.size(); i++) {
                    ItemStack stack = inv.getStack(i);
                    if (stack.isOf(be.tracked)) {
                        found += stack.getCount();
                    }
                }
            }
        }
        boolean lowNow = be.tracked != Items.AIR && found < be.threshold;

        boolean changed = found != be.count || lowNow != be.low;
        be.count = found;
        be.low = lowNow;
        if (state.get(StockMonitorBlock.LOW) != lowNow) {
            world.setBlockState(pos, state.with(StockMonitorBlock.LOW, lowNow), Block.NOTIFY_ALL);
        }
        if (changed) {
            be.sync();
        }
    }

    private void sync() {
        markDirty();
        if (world != null && !world.isClient()) {
            world.updateListeners(pos, getCachedState(), getCachedState(), Block.NOTIFY_ALL);
        }
    }

    // --- sync + persistence ---

    @Override
    public Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }

    @Override
    public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) {
        return createNbt(registries);
    }

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        nbt.putString("Tracked", Registries.ITEM.getId(tracked).toString());
        nbt.putInt("Threshold", threshold);
        nbt.putLong("Count", count);
        nbt.putBoolean("Low", low);
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        Identifier id = Identifier.tryParse(nbt.getString("Tracked"));
        tracked = id == null ? Items.AIR : Registries.ITEM.get(id);
        if (nbt.contains("Threshold")) {
            threshold = nbt.getInt("Threshold");
        }
        count = nbt.getLong("Count");
        low = nbt.getBoolean("Low");
    }
}
