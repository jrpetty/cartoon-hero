package com.gadgets;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class ItemSenderBlockEntity extends BlockEntity implements ChannelBlockEntity {
    private static final int INTERVAL = 8;
    private static final int MAX_PER_CYCLE = 4;

    private String channel = "";

    public ItemSenderBlockEntity(BlockPos pos, BlockState state) {
        super(Gadgets.ITEM_SENDER_BE, pos, state);
    }

    public static void tick(World world, BlockPos pos, BlockState state, ItemSenderBlockEntity be) {
        if (world.getTime() % INTERVAL != 0L || be.channel.isEmpty()) {
            return;
        }
        Inventory source = HopperBlockEntity.getInventoryAt(world, pos.up());
        if (source == null || ItemTransfer.isEmpty(source)) {
            return;
        }
        MinecraftServer server = world.getServer();
        if (server == null) {
            return;
        }
        for (String key : ItemNetwork.receivers(be.channel, world.getTime())) {
            int bar = key.indexOf('|');
            if (bar < 0) {
                continue;
            }
            Identifier dimId = Identifier.tryParse(key.substring(0, bar));
            if (dimId == null) {
                continue;
            }
            ServerWorld target = server.getWorld(RegistryKey.of(RegistryKeys.WORLD, dimId));
            if (target == null) {
                continue;
            }
            BlockPos rpos = BlockPos.fromLong(Long.parseLong(key.substring(bar + 1)));
            if (!target.isChunkLoaded(rpos.getX() >> 4, rpos.getZ() >> 4)) {
                continue; // never resurrect an unloaded chunk
            }
            if (!(target.getBlockState(rpos).getBlock() instanceof ItemReceiverBlock)) {
                continue;
            }
            Inventory dest = HopperBlockEntity.getInventoryAt(target, rpos.down());
            if (dest == null) {
                continue;
            }
            if (ItemTransfer.move(source, dest, MAX_PER_CYCLE) > 0) {
                break;
            }
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
