package com.gadgets;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SidedInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.jetbrains.annotations.Nullable;

/**
 * The inverse of the Filter Hopper: a bottomless bin that swallows items pushed
 * into it. With no filter it voids everything; set a filter (right-click with an
 * item) and it accepts only that item, so a pipe or hopper can dump one kind of
 * overflow into it while everything else is turned away. Its one slot is always
 * empty — anything inserted is destroyed immediately.
 */
public class TrashCanBlockEntity extends BlockEntity implements SidedInventory {
    private static final int[] SLOTS = {0};

    private Item filter = Items.AIR;

    public TrashCanBlockEntity(BlockPos pos, BlockState state) {
        super(Gadgets.TRASH_CAN_BE, pos, state);
    }

    public Item getFilter() {
        return filter;
    }

    public void setFilter(Item item) {
        this.filter = item;
        markDirty();
        if (world != null && !world.isClient()) {
            world.updateListeners(pos, getCachedState(), getCachedState(), 3);
        }
    }

    /** True when this item is allowed in (and therefore destroyed). */
    private boolean accepts(ItemStack stack) {
        return !stack.isEmpty() && (filter == Items.AIR || stack.isOf(filter));
    }

    // --- Inventory: one slot, always empty, insert-only, contents voided ---

    @Override
    public int size() {
        return 1;
    }

    @Override
    public boolean isEmpty() {
        return true;
    }

    @Override
    public ItemStack getStack(int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeStack(int slot, int amount) {
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeStack(int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public void setStack(int slot, ItemStack stack) {
        // The void: whatever is placed here simply ceases to exist.
    }

    @Override
    public boolean isValid(int slot, ItemStack stack) {
        return accepts(stack);
    }

    @Override
    public boolean canPlayerUse(PlayerEntity player) {
        return Inventory.canPlayerUse(this, player);
    }

    @Override
    public void clear() {
    }

    @Override
    public int[] getAvailableSlots(Direction side) {
        return SLOTS;
    }

    @Override
    public boolean canInsert(int slot, ItemStack stack, @Nullable Direction dir) {
        return accepts(stack);
    }

    @Override
    public boolean canExtract(int slot, ItemStack stack, Direction dir) {
        return false;
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
        nbt.putString("Filter", Registries.ITEM.getId(filter).toString());
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        Identifier id = Identifier.tryParse(nbt.getString("Filter"));
        filter = id == null ? Items.AIR : Registries.ITEM.get(id);
    }
}
