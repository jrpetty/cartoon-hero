package com.gadgets;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Moves one matching item per cycle from the inventory above to the inventory
 * below. The filter item is set via the block's right-click interaction.
 */
public class FilterHopperBlockEntity extends BlockEntity {
    private static final int INTERVAL = 8;

    private Item filter = Items.AIR;

    public FilterHopperBlockEntity(BlockPos pos, BlockState state) {
        super(Gadgets.FILTER_HOPPER_BE, pos, state);
    }

    public void setFilter(Item item) {
        this.filter = item;
        markDirty();
    }

    public static void tick(World world, BlockPos pos, BlockState state, FilterHopperBlockEntity be) {
        if (world.getTime() % INTERVAL != 0L || be.filter == Items.AIR) {
            return;
        }

        Inventory above = HopperBlockEntity.getInventoryAt(world, pos.up());
        Inventory below = HopperBlockEntity.getInventoryAt(world, pos.down());
        if (above == null || below == null) {
            return;
        }

        for (int slot = 0; slot < above.size(); slot++) {
            ItemStack stack = above.getStack(slot);
            if (!stack.isEmpty() && stack.isOf(be.filter) && insertOne(below, stack)) {
                stack.decrement(1);
                above.markDirty();
                return;
            }
        }
    }

    private static boolean insertOne(Inventory dest, ItemStack source) {
        ItemStack single = source.copyWithCount(1);
        for (int slot = 0; slot < dest.size(); slot++) {
            ItemStack inSlot = dest.getStack(slot);
            if (inSlot.isEmpty()) {
                if (dest.isValid(slot, single)) {
                    dest.setStack(slot, single);
                    dest.markDirty();
                    return true;
                }
            } else if (ItemStack.areItemsAndComponentsEqual(inSlot, single)
                    && inSlot.getCount() < Math.min(inSlot.getMaxCount(), dest.getMaxCountPerStack())) {
                inSlot.increment(1);
                dest.markDirty();
                return true;
            }
        }
        return false;
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
