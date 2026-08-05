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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

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
    /**
     * How often an idle sender re-reads its pairing. Resolving receivers every
     * cycle to refresh a screen nobody has open is work for nothing; a second
     * is still immediate to a player who has just typed a code.
     */
    private static final int STATUS_INTERVAL = 20;

    private String channel = "";
    private int tier = MIN_TIER;
    private String customName = "";
    /** The stack the next transfer will draw from, for the screen's preview. */
    private ItemStack nextItem = ItemStack.EMPTY;
    /** Game time the next item may move; the link idles until it comes round. */
    private long nextMove = 0L;
    /** How the pairing is faring, for the screen. Recomputed every cycle. */
    private int linkState = LINK_FREE;

    public ItemSenderBlockEntity(BlockPos pos, BlockState state) {
        super(Gadgets.ITEM_SENDER_BE.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, ItemSenderBlockEntity be) {
        if (!TickPhase.due(level, pos, INTERVAL) || be.channel.isEmpty()) {
            return;
        }
        Container source = HopperBlockEntity.getContainerAt(level, pos.above());
        // Peeked every cycle, not just when an item actually moves — the screen
        // is most useful in the long wait between one item and the next.
        be.setNextItem(source == null ? ItemStack.EMPTY : ItemTransfer.peek(source));
        MinecraftServer server = level.getServer();
        if (server == null) {
            return;
        }
        // The pairing is re-read every cycle even when nothing can move, so the
        // screen tells the truth about a typed code straight away rather than
        // only once an item happens to be waiting.
        boolean due = level.getGameTime() >= be.nextMove
                && source != null && !ItemTransfer.isEmpty(source);
        if (!due && !TickPhase.due(level, pos, STATUS_INTERVAL)) {
            return; // nothing to move, and the pairing was checked recently
        }
        String me = sourceKey(level, pos);
        int state = LINK_SEARCHING;
        for (String key : ItemNetwork.receivers(be.channel, level.getGameTime())) {
            ItemReceiverBlockEntity far = resolve(server, key);
            if (far == null) {
                continue;
            }
            ServerLevel target = (ServerLevel) far.getLevel();
            BlockPos rpos = far.getBlockPos();
            // One sender to a receiver: the first to reach it holds it, and
            // anyone else who typed the same code is turned away here.
            if (!far.claim(me)) {
                state = LINK_TAKEN;
                continue;
            }
            state = LINK_PAIRED;
            Container dest = HopperBlockEntity.getContainerAt(target, rpos.below());
            if (dest == null || !due) {
                continue;
            }
            // Copied before the move, which empties the stack it came from.
            ItemStack sent = ItemTransfer.peek(source).copyWithCount(1);
            if (ItemTransfer.move(source, dest, 1) > 0) {
                far.setLastItem(sent);
                // The link is only as quick as its slower end.
                be.nextMove = level.getGameTime()
                        + TransferNode.ticksPerItem(Math.min(be.tier, far.getTier()));
                spark(level, pos, true);
                spark(target, rpos, false);
                break;
            }
        }
        be.setLinkState(be.channel.isEmpty() ? LINK_FREE : state);
    }

    static String sourceKey(Level level, BlockPos pos) {
        return level.dimension().location() + "|" + pos.asLong();
    }

    /**
     * The receiver a published key points at, or null when it cannot be reached.
     * An unloaded chunk is never forced — an absent receiver is away, not gone.
     */
    @Nullable
    private static ItemReceiverBlockEntity resolve(MinecraftServer server, String key) {
        int bar = key.indexOf('|');
        if (bar < 0) {
            return null;
        }
        ResourceLocation dimId = ResourceLocation.tryParse(key.substring(0, bar));
        if (dimId == null) {
            return null;
        }
        ServerLevel target = server.getLevel(ResourceKey.create(Registries.DIMENSION, dimId));
        if (target == null) {
            return null;
        }
        BlockPos rpos;
        try {
            rpos = BlockPos.of(Long.parseLong(key.substring(bar + 1)));
        } catch (NumberFormatException malformed) {
            return null;
        }
        if (!target.isLoaded(rpos) || !(target.getBlockState(rpos).getBlock() instanceof ItemReceiverBlock)) {
            return null;
        }
        return target.getBlockEntity(rpos) instanceof ItemReceiverBlockEntity far ? far : null;
    }

    /**
     * Let go of whatever receiver this sender holds, so retyping a code frees
     * the old one at once rather than leaving it claimed until the receiver
     * next notices — which would refuse the next sender to try that code.
     */
    void releaseClaim() {
        if (level == null || level.isClientSide() || channel.isEmpty()) {
            return;
        }
        MinecraftServer server = level.getServer();
        if (server == null) {
            return;
        }
        String me = sourceKey(level, worldPosition);
        for (String key : ItemNetwork.receivers(channel, level.getGameTime())) {
            ItemReceiverBlockEntity far = resolve(server, key);
            if (far != null && me.equals(far.getBoundSender())) {
                far.release();
            }
        }
    }

    private void setLinkState(int state) {
        if (linkState != state) {
            linkState = state;
            setChanged();
            sync();
        }
    }

    @Override
    public int linkState() {
        return linkState;
    }

    @Override
    public String linkLine() {
        return switch (linkState) {
            case LINK_PAIRED -> "Paired with a receiver";
            case LINK_TAKEN -> "That receiver already has a sender";
            case LINK_SEARCHING -> "No receiver answering that code";
            default -> "No code — read one off a receiver";
        };
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

    /** Only syncs when the answer changes — this is consulted every few ticks. */
    private void setNextItem(ItemStack stack) {
        if (ItemStack.isSameItemSameComponents(nextItem, stack)) {
            return;
        }
        nextItem = stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
        setChanged();
        sync();
    }

    @Override
    public ItemStack flowing() {
        return nextItem;
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
        return customName.isEmpty() ? "Item Sender" : customName;
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
        tag.putString("CustomName", customName);
        tag.putInt("LinkState", linkState);
        if (!nextItem.isEmpty()) {
            tag.put("NextItem", nextItem.save(registries));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        channel = tag.getString("Channel");
        // A link saved before levels existed reads as zero; it starts at one.
        tier = Mth.clamp(tag.getInt("Tier"), MIN_TIER, MAX_TIER);
        customName = tag.getString("CustomName");
        linkState = tag.getInt("LinkState");
        nextItem = tag.contains("NextItem")
                ? ItemStack.parse(registries, tag.getCompound("NextItem")).orElse(ItemStack.EMPTY)
                : ItemStack.EMPTY;
    }
}
