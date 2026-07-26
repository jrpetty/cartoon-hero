package com.gadgets;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The inverse of the Filter Hopper: a bottomless bin that swallows items pushed
 * into it. With no filter it voids everything; set a filter (right-click with an
 * item) and it accepts only that item, so a pipe or hopper can dump one kind of
 * overflow into it while everything else is turned away. Its one slot is always
 * empty — anything inserted is destroyed immediately.
 */
public class TrashCanBlockEntity extends BlockEntity implements WorldlyContainer {
    private static final int[] SLOTS = {0};

    private Item filter = Items.AIR;

    public TrashCanBlockEntity(BlockPos pos, BlockState state) {
        super(Gadgets.TRASH_CAN_BE.get(), pos, state);
    }

    public Item getFilter() {
        return filter;
    }

    public void setFilter(Item item) {
        this.filter = item;
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    /** True when this item is allowed in (and therefore destroyed). */
    private boolean accepts(ItemStack stack) {
        return !stack.isEmpty() && (filter == Items.AIR || stack.is(filter));
    }

    // --- Container: one slot, always empty, insert-only, contents voided ---

    @Override
    public int getContainerSize() {
        return 1;
    }

    @Override
    public boolean isEmpty() {
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        // The void: whatever is placed here simply ceases to exist.
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return accepts(stack);
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        return SLOTS;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @org.jetbrains.annotations.Nullable Direction dir) {
        return accepts(stack);
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction dir) {
        return false;
    }

    // --- sync + persistence ---

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
        tag.putString("Filter", BuiltInRegistries.ITEM.getKey(filter).toString());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ResourceLocation id = ResourceLocation.tryParse(tag.getString("Filter"));
        filter = id == null ? Items.AIR : BuiltInRegistries.ITEM.get(id);
    }
}
