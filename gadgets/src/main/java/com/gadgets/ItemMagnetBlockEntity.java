package com.gadgets;

import java.util.List;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Pulls nearby dropped items toward itself and feeds them into the container
 * directly below (chest, barrel, hopper…). A redstone signal switches it off,
 * exactly like a hopper. Right-click with an empty hand to change the pull
 * radius. Pairs naturally with the Item Counter's flow mode and mob farms.
 */
public class ItemMagnetBlockEntity extends BlockEntity {
    private static final int INTERVAL = 2;
    private static final double PULL_SPEED = 0.42;
    private static final double ABSORB_DISTANCE = 1.4;
    private static final int[] RADII = {4, 6, 8, 12};

    private int radius = 6;

    public ItemMagnetBlockEntity(BlockPos pos, BlockState state) {
        super(Gadgets.ITEM_MAGNET_BE, pos, state);
    }

    public int getRadius() {
        return radius;
    }

    /** Advance to the next pull radius and return it. */
    public int cycleRadius() {
        int idx = 0;
        for (int i = 0; i < RADII.length; i++) {
            if (RADII[i] == radius) {
                idx = i;
                break;
            }
        }
        radius = RADII[(idx + 1) % RADII.length];
        markDirty();
        return radius;
    }

    public static void tick(World world, BlockPos pos, BlockState state, ItemMagnetBlockEntity be) {
        if (world.getTime() % INTERVAL != 0L || world.isReceivingRedstonePower(pos)) {
            return; // a redstone signal parks the magnet, like a hopper
        }
        Inventory target = HopperBlockEntity.getInventoryAt(world, pos.down());
        if (target == null) {
            return; // nowhere to put anything — stay idle
        }

        Vec3d center = Vec3d.ofCenter(pos);
        Box area = new Box(pos).expand(be.radius);
        List<ItemEntity> items = world.getEntitiesByClass(ItemEntity.class, area,
                item -> !item.isRemoved() && !item.getStack().isEmpty() && !item.cannotPickup());
        for (ItemEntity item : items) {
            Vec3d toCenter = center.subtract(item.getPos());
            if (toCenter.lengthSquared() < ABSORB_DISTANCE * ABSORB_DISTANCE) {
                be.absorb(target, item);
            } else {
                Vec3d pull = toCenter.normalize().multiply(PULL_SPEED);
                item.setVelocity(pull.x, pull.y + 0.04, pull.z);
                item.velocityModified = true;
            }
        }
    }

    /** Push as much of the item's stack as fits into the target; discard it if emptied. */
    private void absorb(Inventory inv, ItemEntity item) {
        ItemStack stack = item.getStack();
        boolean moved = false;

        // Top up matching stacks first.
        for (int i = 0; i < inv.size() && !stack.isEmpty(); i++) {
            ItemStack dst = inv.getStack(i);
            if (!dst.isEmpty() && ItemStack.areItemsAndComponentsEqual(dst, stack) && inv.isValid(i, stack)) {
                int space = Math.min(dst.getMaxCount(), inv.getMaxCountPerStack()) - dst.getCount();
                if (space > 0) {
                    int give = Math.min(space, stack.getCount());
                    dst.increment(give);
                    stack.decrement(give);
                    inv.setStack(i, dst);
                    moved = true;
                }
            }
        }
        // Then spill into empty slots.
        for (int i = 0; i < inv.size() && !stack.isEmpty(); i++) {
            if (inv.getStack(i).isEmpty() && inv.isValid(i, stack)) {
                int cap = Math.min(stack.getMaxCount(), inv.getMaxCountPerStack());
                inv.setStack(i, stack.split(cap));
                moved = true;
            }
        }

        if (moved) {
            inv.markDirty();
        }
        if (stack.isEmpty()) {
            item.discard();
        } else {
            item.setStack(stack);
        }
    }

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        nbt.putInt("Radius", radius);
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        if (nbt.contains("Radius")) {
            radius = nbt.getInt("Radius");
        }
    }
}
