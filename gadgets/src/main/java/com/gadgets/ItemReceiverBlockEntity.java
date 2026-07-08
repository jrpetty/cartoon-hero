package com.gadgets;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class ItemReceiverBlockEntity extends BlockEntity implements ChannelBlockEntity {
    private static final int INTERVAL = 5;

    private String channel = "";

    public ItemReceiverBlockEntity(BlockPos pos, BlockState state) {
        super(Gadgets.ITEM_RECEIVER_BE, pos, state);
    }

    public static void tick(World world, BlockPos pos, BlockState state, ItemReceiverBlockEntity be) {
        if (world.getTime() % INTERVAL != 0L || be.channel.isEmpty()) {
            return;
        }
        ItemNetwork.publish(be.channel, sourceKey(world, pos), world.getTime());
    }

    static String sourceKey(World world, BlockPos pos) {
        return world.getRegistryKey().getValue() + "|" + pos.asLong();
    }

    @Override
    public String getChannel() {
        return channel;
    }

    @Override
    public void setChannel(String channel) {
        this.channel = channel == null ? "" : channel;
        markDirty();
    }

    @Override
    public void markRemoved() {
        if (world != null && !world.isClient() && !channel.isEmpty()) {
            ItemNetwork.remove(channel, sourceKey(world, pos));
        }
        super.markRemoved();
    }

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        nbt.putString("Channel", channel);
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        channel = nbt.getString("Channel");
    }
}
