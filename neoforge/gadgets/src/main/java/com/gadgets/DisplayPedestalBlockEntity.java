package com.gadgets;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class DisplayPedestalBlockEntity extends BlockEntity {
    private ItemStack displayed = ItemStack.EMPTY;
    private int scale = 1; // 0=small, 1=medium, 2=large
    private int spin = 0;  // 0=slow, 1=medium, 2=fast

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
        spin = (spin + 1) % 3;
        sync();
        return spin;
    }

    private void sync() {
        setChanged();
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
