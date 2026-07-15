package com.gadgets;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.jetbrains.annotations.Nullable;

/**
 * Holds the showcased item. Also a single-slot inventory, so hoppers and Item
 * Receivers can load the display automatically (insert only while empty; the
 * item can't be piped back out — retrieve it by hand).
 */
public class DisplayPedestalBlockEntity extends BlockEntity implements SidedInventory {
    private static final int[] SLOTS = {0};

    private ItemStack displayed = ItemStack.EMPTY;
    private int scale = 1; // 0=small, 1=medium, 2=large
    private int spin = 1;  // 0=off, 1=slow, 2=medium, 3=fast

    public DisplayPedestalBlockEntity(BlockPos pos, BlockState state) {
        super(Gadgets.DISPLAY_PEDESTAL_BE, pos, state);
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
        markDirty();
        if (world != null && !world.isClient()) {
            world.updateListeners(pos, getCachedState(), getCachedState(), Block.NOTIFY_ALL);
        }
    }

    // --- Inventory: one slot, insert-only while empty, no automated extraction ---

    @Override
    public int size() {
        return 1;
    }

    @Override
    public boolean isEmpty() {
        return displayed.isEmpty();
    }

    @Override
    public ItemStack getStack(int slot) {
        return slot == 0 ? displayed : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeStack(int slot, int amount) {
        if (slot != 0 || displayed.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack split = displayed.split(amount);
        sync();
        return split;
    }

    @Override
    public ItemStack removeStack(int slot) {
        return slot == 0 ? removeDisplayed() : ItemStack.EMPTY;
    }

    @Override
    public void setStack(int slot, ItemStack stack) {
        if (slot == 0) {
            displayed = stack;
            sync();
        }
    }

    @Override
    public int getMaxCountPerStack() {
        return 1;
    }

    @Override
    public boolean isValid(int slot, ItemStack stack) {
        return isColumnOwner() && displayed.isEmpty();
    }

    /** Only the bottom-most pedestal of a stacked column accepts items. */
    private boolean isColumnOwner() {
        return world == null || !(world.getBlockState(pos.down()).getBlock() instanceof DisplayPedestalBlock);
    }

    @Override
    public boolean canPlayerUse(PlayerEntity player) {
        return Inventory.canPlayerUse(this, player);
    }

    @Override
    public void clear() {
        displayed = ItemStack.EMPTY;
        sync();
    }

    @Override
    public int[] getAvailableSlots(Direction side) {
        return SLOTS;
    }

    @Override
    public boolean canInsert(int slot, ItemStack stack, @Nullable Direction dir) {
        return isValid(slot, stack);
    }

    @Override
    public boolean canExtract(int slot, ItemStack stack, Direction dir) {
        return false; // the display is not a chest — take it back by hand
    }

    // --- sync + persistence ---

    @Override
    public Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }

    @Override
    public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) {
        return createNbt(registries);
    }

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        if (!displayed.isEmpty()) {
            nbt.put("Item", displayed.encode(registries));
        }
        nbt.putInt("Scale", scale);
        nbt.putInt("Spin", spin);
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        displayed = nbt.contains("Item")
                ? ItemStack.fromNbt(registries, nbt.get("Item")).orElse(ItemStack.EMPTY)
                : ItemStack.EMPTY;
        scale = nbt.getInt("Scale");
        spin = nbt.getInt("Spin");
    }
}
