package com.gadgets;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Answers on its code and drops whatever arrives into the inventory directly
 * below it.
 *
 * <p>Every receiver mints its own eight-digit code the first time it ticks, and
 * that code is the whole of its identity — read it off the receiver, type it
 * into a sender, and the two are paired. It is also the address the transport
 * layer already used, so nothing underneath had to change.
 *
 * <p>A receiver takes <em>one</em> sender. The first to reach it claims it, and
 * every other sender typing the same code is turned away rather than quietly
 * sharing the line. The claim is released when the bound sender is provably
 * gone — a loaded chunk with no sender in it — or by hand from this screen.
 */
public class ItemReceiverBlockEntity extends BlockEntity implements TransferNode {
    private static final int INTERVAL = 5;
    /** How often the claim is checked against the sender that holds it. */
    private static final int VERIFY = 100;
    /** Re-rolls allowed when a fresh code collides with one already answering. */
    private static final int CODE_ATTEMPTS = 5;

    private String channel = "";
    private int tier = MIN_TIER;
    private String customName = "";
    /** "dim|packedPos" of the sender holding this receiver, empty when free. */
    private String boundSender = "";
    /** The last item delivered here. Nothing tells a receiver what is queued at
     *  the far end, so this reports what came through rather than what is next. */
    private ItemStack lastItem = ItemStack.EMPTY;

    public ItemReceiverBlockEntity(BlockPos pos, BlockState state) {
        super(Gadgets.ITEM_RECEIVER_BE.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, ItemReceiverBlockEntity be) {
        if (!TickPhase.due(level, pos, INTERVAL)) {
            return;
        }
        if (be.channel.isEmpty()) {
            be.mintCode(level);
        }
        ItemNetwork.publish(be.channel, sourceKey(level, pos), level.getGameTime());
        if (!be.boundSender.isEmpty() && TickPhase.due(level, pos, VERIFY)) {
            be.verifyClaim(level);
        }
    }

    static String sourceKey(Level level, BlockPos pos) {
        return level.dimension().location() + "|" + pos.asLong();
    }

    /**
     * Eight digits, minted once and then kept for the life of the block.
     * Re-rolled if the code is already answering somewhere — which only catches
     * receivers currently loaded, but that is precisely the case where you are
     * about to read two codes off two blocks standing next to each other.
     */
    private void mintCode(Level level) {
        for (int attempt = 0; attempt < CODE_ATTEMPTS; attempt++) {
            String candidate = LinkCode.mint(level.getRandom());
            if (ItemNetwork.receivers(candidate, level.getGameTime()).isEmpty()) {
                channel = candidate;
                setChanged();
                sync();
                return;
            }
        }
    }

    /**
     * Claim this receiver for a sender. True when it is free or already that
     * sender's; false when somebody else holds it.
     */
    boolean claim(String senderKey) {
        if (boundSender.equals(senderKey)) {
            return true;
        }
        if (!boundSender.isEmpty()) {
            return false;
        }
        boundSender = senderKey;
        setChanged();
        sync();
        return true;
    }

    /** Let go of whichever sender holds this receiver. */
    public void release() {
        if (boundSender.isEmpty()) {
            return;
        }
        boundSender = "";
        setChanged();
        sync();
    }

    /**
     * Drop the claim once the sender holding it is provably gone. As everywhere
     * else in this mod, only a loaded chunk may prove absence — an unloaded
     * sender is away, not deleted, and its line is still its own.
     */
    private void verifyClaim(Level level) {
        MinecraftServer server = level.getServer();
        int bar = boundSender.indexOf('|');
        if (server == null || bar < 0) {
            return;
        }
        ResourceLocation dim = ResourceLocation.tryParse(boundSender.substring(0, bar));
        if (dim == null) {
            release();
            return;
        }
        ServerLevel world = server.getLevel(ResourceKey.create(Registries.DIMENSION, dim));
        if (world == null) {
            return;
        }
        BlockPos sp;
        try {
            sp = BlockPos.of(Long.parseLong(boundSender.substring(bar + 1)));
        } catch (NumberFormatException malformed) {
            release();
            return;
        }
        if (!world.isLoaded(sp)) {
            return;
        }
        boolean stillOurs = world.getBlockEntity(sp) instanceof ItemSenderBlockEntity sender
                && sender.getChannel().equals(channel);
        if (!stillOurs) {
            release();
        }
    }

    public String getBoundSender() {
        return boundSender;
    }

    @Override
    public int linkState() {
        return boundSender.isEmpty() ? LINK_FREE : LINK_PAIRED;
    }

    @Override
    public String linkLine() {
        return boundSender.isEmpty() ? "Free — type this code into a sender" : "Paired with a sender";
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

    /**
     * A receiver's code is minted, not chosen; the setter exists for the
     * transport contract and is ignored so nothing can rename a live address
     * out from under the sender holding it.
     */
    @Override
    public void setChannel(String channel) {
        // deliberately ignored — see above
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
        tag.putString("BoundSender", boundSender);
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
        boundSender = tag.getString("BoundSender");
        lastItem = tag.contains("LastItem")
                ? ItemStack.parse(registries, tag.getCompound("LastItem")).orElse(ItemStack.EMPTY)
                : ItemStack.EMPTY;
    }
}
