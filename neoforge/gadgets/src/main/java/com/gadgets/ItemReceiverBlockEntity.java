package com.gadgets;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class ItemReceiverBlockEntity extends BlockEntity implements ChannelBlockEntity {
    private static final int INTERVAL = 5;

    private String channel = "";

    public ItemReceiverBlockEntity(BlockPos pos, BlockState state) {
        super(Gadgets.ITEM_RECEIVER_BE.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, ItemReceiverBlockEntity be) {
        if (!TickPhase.due(level, pos, INTERVAL) || be.channel.isEmpty()) {
            return;
        }
        ItemNetwork.publish(be.channel, sourceKey(level, pos), level.getGameTime());
    }

    static String sourceKey(Level level, BlockPos pos) {
        return level.dimension().location() + "|" + pos.asLong();
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
    public void setRemoved() {
        if (level != null && !level.isClientSide() && !channel.isEmpty()) {
            ItemNetwork.remove(channel, sourceKey(level, worldPosition));
        }
        super.setRemoved();
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
