package com.gadgets;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class ItemSenderBlockEntity extends BlockEntity implements ChannelBlockEntity {
    private static final int INTERVAL = 8;
    private static final int MAX_PER_CYCLE = 4;

    private String channel = "";

    public ItemSenderBlockEntity(BlockPos pos, BlockState state) {
        super(Gadgets.ITEM_SENDER_BE.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, ItemSenderBlockEntity be) {
        if (level.getGameTime() % INTERVAL != 0L || be.channel.isEmpty()) {
            return;
        }
        Container source = HopperBlockEntity.getContainerAt(level, pos.above());
        if (source == null || ItemTransfer.isEmpty(source)) {
            return;
        }
        MinecraftServer server = level.getServer();
        if (server == null) {
            return;
        }
        for (String key : ItemNetwork.receivers(be.channel, level.getGameTime())) {
            int bar = key.indexOf('|');
            if (bar < 0) {
                continue;
            }
            ResourceLocation dimId = ResourceLocation.tryParse(key.substring(0, bar));
            if (dimId == null) {
                continue;
            }
            ServerLevel target = server.getLevel(ResourceKey.create(Registries.DIMENSION, dimId));
            if (target == null) {
                continue;
            }
            BlockPos rpos = BlockPos.of(Long.parseLong(key.substring(bar + 1)));
            if (!target.isLoaded(rpos)) {
                continue; // never resurrect an unloaded chunk
            }
            if (!(target.getBlockState(rpos).getBlock() instanceof ItemReceiverBlock)) {
                continue;
            }
            Container dest = HopperBlockEntity.getContainerAt(target, rpos.below());
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
