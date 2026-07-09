package com.gadgets;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Container;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Holds the showcased item. Also a single-slot inventory, so hoppers and Item
 * Receivers can load the display automatically (insert only while empty; the
 * item can't be piped back out — retrieve it by hand).
 */
public class DisplayPedestalBlockEntity extends BlockEntity implements WorldlyContainer {
    private static final int[] SLOTS = {0};

    private ItemStack displayed = ItemStack.EMPTY;
    private int scale = 1; // 0=small, 1=medium, 2=large
    private int spin = 1;  // 0=off, 1=slow, 2=medium, 3=fast

    public DisplayPedestalBlockEntity(BlockPos pos, BlockState state) {
        super(Gadgets.DISPLAY_PEDESTAL_BE.get(), pos, state);
    }

    public ItemStack getDisplayed() {
        return displayed;
    }

    public int getScale() {
        return scale;
    }

    public int getSpin() {
        return spin;
    }

    public void setDisplayed(ItemStack stack) {
        this.displayed = stack;
        sync();
    }

    public ItemStack removeDisplayed() {
        ItemStack taken = displayed;
        displayed = ItemStack.EMPTY;
        sync();
        return taken;
    }

    public int cycleScale() {
        scale = (scale + 1) % 3;
        sync();
        return scale;
    }

    public int cycleSpin() {
        spin = (spin + 1) % 4;
        sync();
        return spin;
    }

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    // --- Container: one slot, insert-only while empty, no automated extraction ---

    @Override
    public int getContainerSize() {
        return 1;
    }

    @Override
    public boolean isEmpty() {
        return displayed.isEmpty();
    }

    @Override
    public ItemStack getItem(int slot) {
        return slot == 0 ? displayed : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        if (slot != 0 || displayed.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack split = displayed.split(amount);
        sync();
        return split;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return slot == 0 ? removeDisplayed() : ItemStack.EMPTY;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot == 0) {
            displayed = stack;
            sync();
        }
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return displayed.isEmpty();
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        displayed = ItemStack.EMPTY;
        sync();
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        return SLOTS;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction dir) {
        return displayed.isEmpty();
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction dir) {
        return false; // the display is not a chest — take it back by hand
    }

    // --- sync + persistence ---

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!displayed.isEmpty()) {
            tag.put("Item", displayed.save(registries));
        }
        tag.putInt("Scale", scale);
        tag.putInt("Spin", spin);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        displayed = tag.contains("Item")
                ? ItemStack.parseOptional(registries, tag.getCompound("Item"))
                : ItemStack.EMPTY;
        scale = tag.getInt("Scale");
        spin = tag.getInt("Spin");
    }
}
