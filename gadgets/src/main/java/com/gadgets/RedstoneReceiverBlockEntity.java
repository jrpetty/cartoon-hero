package com.gadgets;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class RedstoneReceiverBlockEntity extends BlockEntity implements ChannelBlockEntity {
    private static final int INTERVAL = 5;

    private String channel = "";

    public RedstoneReceiverBlockEntity(BlockPos pos, BlockState state) {
        super(Gadgets.REDSTONE_RECEIVER_BE, pos, state);
    }

    public static void tick(World world, BlockPos pos, BlockState state, RedstoneReceiverBlockEntity be) {
        if (world.getTime() % INTERVAL != 0L) {
            return;
        }
        int power = WirelessNetwork.strength(be.channel, world.getTime());
        if (state.get(RedstoneReceiverBlock.POWER) != power) {
            world.setBlockState(pos, state.with(RedstoneReceiverBlock.POWER, power), Block.NOTIFY_ALL);
        }
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
