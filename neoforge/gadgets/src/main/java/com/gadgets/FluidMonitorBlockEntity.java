package com.gadgets;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/**
 * Watches the tank it faces and shows how full it is, emitting redstone once it
 * drops below the alert level.
 *
 * <p>Reads through the fluid-handler capability, so anything that exposes one —
 * a vanilla cauldron mod block, a Create fluid tank, a Mekanism tank — works
 * without knowing what it is. Totals every tank in the block and names the
 * fluid holding the most.
 */
public class FluidMonitorBlockEntity extends BlockEntity implements HubGauge {
    private static final int INTERVAL = 10;

    private long stored = 0;
    private long capacity = 0;
    private String fluidName = "";
    private int threshold = 25;
    private boolean low = false;
    private boolean present = false;
    private String customName = "";

    /** The tank this monitor faces. */
    private final CapCache<IFluidHandler> source = new CapCache<>(Capabilities.FluidHandler.BLOCK, this);

    public FluidMonitorBlockEntity(BlockPos pos, BlockState state) {
        super(Gadgets.FLUID_MONITOR_BE.get(), pos, state);
    }

    @Override
    public int gaugeType() {
        return CommandHubBlockEntity.TYPE_FLUID;
    }

    @Override
    public long gaugeStored() {
        return stored;
    }

    @Override
    public long gaugeCapacity() {
        return capacity;
    }

    @Override
    public int getThreshold() {
        return threshold;
    }

    @Override
    public void setThreshold(int percent) {
        for (int preset : THRESHOLDS) {
            if (preset == percent) {
                threshold = percent;
                sync();
                return;
            }
        }
    }

    @Override
    public boolean isLow() {
        return low;
    }

    @Override
    public boolean hasSource() {
        return present;
    }

    @Override
    public String getCustomName() {
        return customName;
    }

    @Override
    public void setCustomName(String name) {
        this.customName = name == null ? "" : name;
        sync();
    }

    /** The player's label, else the fluid it is watching. */
    @Override
    public String displayName() {
        if (!customName.isEmpty()) {
            return customName;
        }
        return fluidName.isEmpty() ? "empty tank" : fluidName;
    }

    /** Small line under the percentage on the panel. */
    @Override
    public String faceLabel() {
        String name = customName.isEmpty() ? (fluidName.isEmpty() ? "no fluid" : fluidName) : customName;
        return name.length() > 14 ? name.substring(0, 13) + "…" : name;
    }

    /** "12.5B / 32B" — buckets read better than five-digit millibuckets. */
    @Override
    public String amountText() {
        return buckets(stored) + " / " + buckets(capacity);
    }

    private static String buckets(long millibuckets) {
        if (millibuckets <= 0) {
            return "0B";
        }
        double b = millibuckets / 1000.0;
        return b < 10 ? String.format(java.util.Locale.ROOT, "%.1fB", b) : (millibuckets / 1000L) + "B";
    }

    public static void tick(Level level, BlockPos pos, BlockState state, FluidMonitorBlockEntity be) {
        if (!TickPhase.due(level, pos, INTERVAL)) {
            return;
        }
        Direction facing = state.getValue(FluidMonitorBlock.FACING);
        IFluidHandler tank = be.source.get(level, pos.relative(facing), facing.getOpposite());

        long total = 0;
        long room = 0;
        long best = 0;
        String bestName = "";
        boolean found = tank != null;
        if (found) {
            for (int i = 0; i < tank.getTanks(); i++) {
                FluidStack fluid = tank.getFluidInTank(i);
                room += tank.getTankCapacity(i);
                if (fluid.isEmpty()) {
                    continue;
                }
                total += fluid.getAmount();
                if (fluid.getAmount() > best) {
                    best = fluid.getAmount();
                    bestName = fluid.getHoverName().getString();
                }
            }
        }
        // An unreadable block is not an empty tank — never alarm on one.
        boolean lowNow = found && room > 0 && total * 100L / room < be.threshold;

        boolean changed = total != be.stored || room != be.capacity || lowNow != be.low
                || found != be.present || !bestName.equals(be.fluidName);
        be.stored = total;
        be.capacity = room;
        be.fluidName = bestName;
        be.low = lowNow;
        be.present = found;
        if (state.getValue(FluidMonitorBlock.LOW) != lowNow) {
            level.setBlock(pos, state.setValue(FluidMonitorBlock.LOW, lowNow), Block.UPDATE_ALL);
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
        tag.putLong("Stored", stored);
        tag.putLong("Capacity", capacity);
        tag.putString("Fluid", fluidName);
        tag.putInt("Threshold", threshold);
        tag.putBoolean("Low", low);
        tag.putBoolean("Present", present);
        tag.putString("CustomName", customName);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        stored = tag.getLong("Stored");
        capacity = tag.getLong("Capacity");
        fluidName = tag.getString("Fluid");
        if (tag.contains("Threshold")) {
            threshold = tag.getInt("Threshold");
        }
        low = tag.getBoolean("Low");
        present = tag.getBoolean("Present");
        customName = tag.getString("CustomName");
    }
}
