package com.gadgets;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Answers on a channel and drops whatever arrives into the inventory directly
 * below it. Pair it with the Redstone Linker; right-click it to see its level
 * and upgrade it.
 *
 * <p>It does not move anything itself — the sender does that — but its level
 * caps the link just as the sender's does, so a fast sender feeding a level-one
 * receiver still trickles.
 */
public class ItemReceiverBlockEntity extends BlockEntity implements TransferNode {
    private static final int INTERVAL = 5;

    private String channel = "";
    private int tier = MIN_TIER;
    private String customName = "";
    /** The last item delivered here. Nothing tells a receiver what is queued at
     *  the far end, so this reports what came through rather than what is next. */
    private ItemStack lastItem = ItemStack.EMPTY;

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

    /** Called by the sender at the moment of delivery. */
    void setLastItem(ItemStack stack) {
        if (ItemStack.isSameItemSameComponents(lastItem, stack)) {
            return;
        }
        lastItem = stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
        setChanged();
        sync();
    }

    @Override
    public ItemStack flowing() {
        return lastItem;
    }

    @Override
    public String getCustomName() {
        return customName;
    }

    @Override
    public void setCustomName(String name) {
        customName = name == null ? "" : name;
        setChanged();
        sync();
    }

    @Override
    public String displayName() {
        return customName.isEmpty() ? "Item Receiver" : customName;
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
    public void setRemoved() {
        if (level != null && !level.isClientSide() && !channel.isEmpty()) {
            ItemNetwork.remove(channel, sourceKey(level, worldPosition));
        }
        super.setRemoved();
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
        tag.putString("CustomName", customName);
        if (!lastItem.isEmpty()) {
            tag.put("LastItem", lastItem.save(registries));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        channel = tag.getString("Channel");
        // A link saved before levels existed reads as zero; it starts at one.
        tier = Mth.clamp(tag.getInt("Tier"), MIN_TIER, MAX_TIER);
        customName = tag.getString("CustomName");
        lastItem = tag.contains("LastItem")
                ? ItemStack.parse(registries, tag.getCompound("LastItem")).orElse(ItemStack.EMPTY)
                : ItemStack.EMPTY;
    }
}
