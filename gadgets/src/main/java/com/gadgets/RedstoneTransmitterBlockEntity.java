package com.gadgets;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class RedstoneTransmitterBlockEntity extends BlockEntity implements ChannelBlockEntity {
    private static final int INTERVAL = 5;

    private String channel = "";

    public RedstoneTransmitterBlockEntity(BlockPos pos, BlockState state) {
        super(Gadgets.REDSTONE_TRANSMITTER_BE, pos, state);
    }

    public static void tick(World world, BlockPos pos, BlockState state, RedstoneTransmitterBlockEntity be) {
        if (world.getTime() % INTERVAL != 0L) {
            return;
        }
        int power = world.getReceivedRedstonePower(pos);
        WirelessNetwork.publish(be.channel, sourceId(world, pos), power, world.getTime());
    }

    static String sourceId(World world, BlockPos pos) {
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
