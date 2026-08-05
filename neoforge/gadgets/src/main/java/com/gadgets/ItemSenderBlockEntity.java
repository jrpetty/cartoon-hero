package com.gadgets;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Pulls items from the inventory directly above it and teleports them to an
 * Item Receiver on the same channel. Pair it with the Redstone Linker;
 * right-click it to see its level and upgrade it.
 *
 * <p>It moves one item at a time, on a clock set by its own level and the level
 * of the receiver that answers — see {@link TransferNode}.
 */
public class ItemSenderBlockEntity extends BlockEntity implements TransferNode {
    /** How often the clock is consulted. Fine enough for the fastest level. */
    private static final int INTERVAL = 4;

    private String channel = "";
    private int tier = MIN_TIER;
    /** Game time the next item may move; the link idles until it comes round. */
    private long nextMove = 0L;

    public ItemSenderBlockEntity(BlockPos pos, BlockState state) {
        super(Gadgets.ITEM_SENDER_BE.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, ItemSenderBlockEntity be) {
        if (!TickPhase.due(level, pos, INTERVAL) || be.channel.isEmpty()) {
            return;
        }
        if (level.getGameTime() < be.nextMove) {
            return; // still paying for the last item
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
            if (ItemTransfer.move(source, dest, 1) > 0) {
                // The link is only as quick as its slower end.
                int paired = target.getBlockEntity(rpos) instanceof ItemReceiverBlockEntity r
                        ? Math.min(be.tier, r.getTier()) : be.tier;
                be.nextMove = level.getGameTime() + TransferNode.ticksPerItem(paired);
                spark(level, pos, true);
                spark(target, rpos, false);
                break;
            }
        }
    }

    /**
     * A puff of ender at whichever end of the block the item passed through:
     * the top of a sender, where it was drawn in, and the bottom of a receiver,
     * where it drops out. While a link is running, this is the plainest signal
     * of which way it points.
     */
    static void spark(Level level, BlockPos pos, boolean intake) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        double y = pos.getY() + (intake ? 1.05 : -0.05);
        server.sendParticles(intake ? ParticleTypes.REVERSE_PORTAL : ParticleTypes.PORTAL,
                pos.getX() + 0.5, y, pos.getZ() + 0.5, 6, 0.22, 0.05, 0.22, 0.02);
    }

    @Override
    public int getTier() {
        return tier;
    }

    @Override
    public void setTier(int tier) {
        this.tier = Mth.clamp(tier, MIN_TIER, MAX_TIER);
        setChanged();
        sync();
    }

    @Override
    public String getChannel() {
        return channel;
    }

    @Override
    public void setChannel(String channel) {
        this.channel = channel == null ? "" : channel;
        setChanged();
        sync();
    }

    private void sync() {
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
        tag.putString("Channel", channel);
        tag.putInt("Tier", tier);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        channel = tag.getString("Channel");
        // A link saved before levels existed reads as zero; it starts at one.
        tier = Mth.clamp(tag.getInt("Tier"), MIN_TIER, MAX_TIER);
    }
}
