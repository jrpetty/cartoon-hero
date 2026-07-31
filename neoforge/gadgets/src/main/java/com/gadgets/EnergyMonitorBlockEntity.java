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
import net.neoforged.neoforge.energy.IEnergyStorage;

/**
 * Watches the energy store it faces and shows its charge, emitting redstone
 * once it drops below the alert level — wire it to a generator to keep a buffer
 * topped up without running the generator flat out.
 *
 * <p>Reads through the energy capability, so any mod's cell or machine buffer
 * that exposes one works.
 */
public class EnergyMonitorBlockEntity extends BlockEntity implements HubGauge {
    private static final int INTERVAL = 10;

    private long stored = 0;
    private long capacity = 0;
    private int threshold = 25;
    private boolean low = false;
    private boolean present = false;
    private String customName = "";

    public EnergyMonitorBlockEntity(BlockPos pos, BlockState state) {
        super(Gadgets.ENERGY_MONITOR_BE.get(), pos, state);
    }

    @Override
    public int gaugeType() {
        return CommandHubBlockEntity.TYPE_ENERGY;
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

    @Override
    public String displayName() {
        return customName.isEmpty() ? "energy" : customName;
    }

    @Override
    public String faceLabel() {
        String name = customName.isEmpty() ? "energy" : customName;
        return name.length() > 14 ? name.substring(0, 13) + "…" : name;
    }

    /** "12.4k / 60k FE". */
    @Override
    public String amountText() {
        return ItemCounterBlockEntity.compact(stored) + " / " + ItemCounterBlockEntity.compact(capacity) + " FE";
    }

    public static void tick(Level level, BlockPos pos, BlockState state, EnergyMonitorBlockEntity be) {
        if (level.getGameTime() % INTERVAL != 0L) {
            return;
        }
        Direction facing = state.getValue(EnergyMonitorBlock.FACING);
        IEnergyStorage cell = level.getCapability(Capabilities.EnergyStorage.BLOCK,
                pos.relative(facing), facing.getOpposite());

        boolean found = cell != null;
        long total = found ? cell.getEnergyStored() : 0;
        long room = found ? cell.getMaxEnergyStored() : 0;
        // An unreadable block is not a flat battery — never alarm on one.
        boolean lowNow = found && room > 0 && total * 100L / room < be.threshold;

        boolean changed = total != be.stored || room != be.capacity || lowNow != be.low || found != be.present;
        be.stored = total;
        be.capacity = room;
        be.low = lowNow;
        be.present = found;
        if (state.getValue(EnergyMonitorBlock.LOW) != lowNow) {
            level.setBlock(pos, state.setValue(EnergyMonitorBlock.LOW, lowNow), Block.UPDATE_ALL);
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
        if (tag.contains("Threshold")) {
            threshold = tag.getInt("Threshold");
        }
        low = tag.getBoolean("Low");
        present = tag.getBoolean("Present");
        customName = tag.getString("CustomName");
    }
}
