package com.gadgets;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Moves one matching item per cycle from the inventory above to the one below.
 */
public class FilterHopperBlockEntity extends BlockEntity {
    private static final int INTERVAL = 8;

    private Item filter = Items.AIR;

    public FilterHopperBlockEntity(BlockPos pos, BlockState state) {
        super(Gadgets.FILTER_HOPPER_BE.get(), pos, state);
    }

    public void setFilter(Item item) {
        this.filter = item;
        setChanged();
    }

    public static void tick(Level level, BlockPos pos, BlockState state, FilterHopperBlockEntity be) {
        if (level.getGameTime() % INTERVAL != 0L || be.filter == Items.AIR) {
            return;
        }

        Container above = HopperBlockEntity.getContainerAt(level, pos.above());
        Container below = HopperBlockEntity.getContainerAt(level, pos.below());
        if (above == null || below == null) {
            return;
        }

        for (int slot = 0; slot < above.getContainerSize(); slot++) {
            ItemStack stack = above.getItem(slot);
            if (!stack.isEmpty() && stack.is(be.filter) && insertOne(below, stack)) {
                stack.shrink(1);
                above.setChanged();
                return;
            }
        }
    }

    private static boolean insertOne(Container dest, ItemStack source) {
        ItemStack single = source.copyWithCount(1);
        for (int slot = 0; slot < dest.getContainerSize(); slot++) {
            ItemStack inSlot = dest.getItem(slot);
            if (inSlot.isEmpty()) {
                if (dest.canPlaceItem(slot, single)) {
                    dest.setItem(slot, single);
                    dest.setChanged();
                    return true;
                }
            } else if (ItemStack.isSameItemSameComponents(inSlot, single)
                    && inSlot.getCount() < Math.min(inSlot.getMaxStackSize(), dest.getMaxStackSize())) {
                inSlot.grow(1);
                dest.setChanged();
                return true;
            }
        }
        return false;
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
