package com.gadgets;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class RedstoneReceiverBlockEntity extends BlockEntity implements ChannelBlockEntity {
    private static final int INTERVAL = 5;

    private String channel = "";

    public RedstoneReceiverBlockEntity(BlockPos pos, BlockState state) {
        super(Gadgets.REDSTONE_RECEIVER_BE.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, RedstoneReceiverBlockEntity be) {
        if (!TickPhase.due(level, pos, INTERVAL)) {
            return;
        }
        int power = WirelessNetwork.strength(be.channel, level.getGameTime());
        if (state.getValue(RedstoneReceiverBlock.POWER) != power) {
            level.setBlock(pos, state.setValue(RedstoneReceiverBlock.POWER, power), Block.UPDATE_ALL);
        }
    }

    @Override
    public String getChannel() {
        return channel;
    }

    @Override
    public void setChannel(String channel) {
        this.channel = channel == null ? "" : channel;
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("Channel", channel);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        channel = tag.getString("Channel");
    }
}
