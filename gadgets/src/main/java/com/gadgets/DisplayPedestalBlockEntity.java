package com.gadgets;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.BlockPos;

public class DisplayPedestalBlockEntity extends BlockEntity {
    private ItemStack displayed = ItemStack.EMPTY;
    private int scale = 1; // 0=small, 1=medium, 2=large
    private int spin = 0;  // 0=slow, 1=medium, 2=fast

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
        spin = (spin + 1) % 3;
        sync();
        return spin;
    }

    private void sync() {
        markDirty();
        if (world != null && !world.isClient()) {
            world.updateListeners(pos, getCachedState(), getCachedState(), Block.NOTIFY_ALL);
        }
    }

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
