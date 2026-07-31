package com.gadgets;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

/**
 * Pulls nearby dropped items toward itself and feeds them into the container
 * directly below (chest, barrel, hopper — and any modded inventory such as a
 * Create depot or vault, via the item-handler capability). A redstone signal
 * switches it off, exactly like a hopper. Right-click with an empty hand to
 * change the pull radius. Pairs naturally with the Item Counter and mob farms.
 */
public class ItemMagnetBlockEntity extends BlockEntity {
    private static final int INTERVAL = 2;
    private static final double PULL_SPEED = 0.42;
    private static final double ABSORB_DISTANCE = 1.4;
    private static final int[] RADII = {4, 6, 8, 12};

    private int radius = 6;

    /** The container below, which collected items are pushed into. */
    private final CapCache<IItemHandler> destination = new CapCache<>(Capabilities.ItemHandler.BLOCK, this);

    public ItemMagnetBlockEntity(BlockPos pos, BlockState state) {
        super(Gadgets.ITEM_MAGNET_BE.get(), pos, state);
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
        setChanged();
        return radius;
    }

    public static void tick(Level level, BlockPos pos, BlockState state, ItemMagnetBlockEntity be) {
        if (!TickPhase.due(level, pos, INTERVAL) || level.hasNeighborSignal(pos)) {
            return; // a redstone signal parks the magnet, like a hopper
        }
        IItemHandler target = be.destination.get(level, pos.below(), Direction.UP);
        if (target == null) {
            return; // nowhere to put anything — stay idle
        }

        Vec3 center = Vec3.atCenterOf(pos);
        AABB area = new AABB(pos).inflate(be.radius);
        List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, area,
                item -> item.isAlive() && !item.getItem().isEmpty() && !item.hasPickUpDelay());
        for (ItemEntity item : items) {
            if (!hasRoom(target, item.getItem())) {
                continue; // a full chest doesn't tug — items stay collectable elsewhere
            }
            Vec3 toCenter = center.subtract(item.position());
            if (toCenter.lengthSqr() < ABSORB_DISTANCE * ABSORB_DISTANCE) {
                be.absorb(target, item);
            } else {
                Vec3 pull = toCenter.normalize().scale(PULL_SPEED);
                item.setDeltaMovement(pull.x, pull.y + 0.04, pull.z);
                item.hasImpulse = true;
            }
        }
    }

    /** Can the target accept at least one item of this stack? (simulated insert) */
    private static boolean hasRoom(IItemHandler handler, ItemStack stack) {
        ItemStack remainder = ItemHandlerHelper.insertItemStacked(handler, stack, true);
        return remainder.getCount() < stack.getCount();
    }

    /** Push as much of the item's stack as fits into the target; discard it if emptied. */
    private void absorb(IItemHandler handler, ItemEntity item) {
        ItemStack remainder = ItemHandlerHelper.insertItemStacked(handler, item.getItem(), false);
        if (remainder.isEmpty()) {
            item.discard();
        } else {
            item.setItem(remainder);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Radius", radius);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Radius")) {
            radius = tag.getInt("Radius");
        }
    }
}
