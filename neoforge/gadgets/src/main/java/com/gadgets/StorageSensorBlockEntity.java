package com.gadgets;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * A wireless comparator: bound to any container with the Monitor Wand, it
 * outputs a redstone level proportional to how full that container is —
 * even across dimensions. Vanilla comparator formula, so 1 means "has
 * something" and 15 means "completely full".
 */
public class StorageSensorBlockEntity extends BlockEntity {
    private static final int INTERVAL = 10;

    private String boundDim = "";
    private long boundPos = 0L;

    public StorageSensorBlockEntity(BlockPos pos, BlockState state) {
        super(Gadgets.STORAGE_SENSOR_BE.get(), pos, state);
    }

    public boolean isBound() {
        return !boundDim.isEmpty();
    }

    public String describeBinding() {
        if (!isBound()) {
            return "not bound";
        }
        BlockPos p = BlockPos.of(boundPos);
        return p.getX() + ", " + p.getY() + ", " + p.getZ() + " · " + boundDim;
    }

    public void bind(String dim, BlockPos pos) {
        this.boundDim = dim;
        this.boundPos = pos.asLong();
        setChanged();
    }

    public static void tick(Level level, BlockPos pos, BlockState state, StorageSensorBlockEntity be) {
        if (level.getGameTime() % INTERVAL != 0L) {
            return;
        }
        int power = 0;
        Container inv = be.resolveContainer(level);
        if (inv != null) {
            power = fillLevel(inv);
        }
        if (state.getValue(StorageSensorBlock.POWER) != power) {
            level.setBlock(pos, state.setValue(StorageSensorBlock.POWER, power), Block.UPDATE_ALL);
        }
    }

    @Nullable
    private Container resolveContainer(Level level) {
        if (!isBound()) {
            return null;
        }
        MinecraftServer server = level.getServer();
        if (server == null) {
            return null;
        }
        ResourceLocation dimId = ResourceLocation.tryParse(boundDim);
        if (dimId == null) {
            return null;
        }
        ServerLevel target = server.getLevel(ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, dimId));
        if (target == null) {
            return null;
        }
        BlockPos cpos = BlockPos.of(boundPos);
        if (!target.isLoaded(cpos)) {
            return null; // never resurrect an unloaded chunk
        }
        return HopperBlockEntity.getContainerAt(target, cpos);
    }

    /** Vanilla comparator formula: proportional fill, 1 = non-empty, 15 = full. */
    public static int fillLevel(Container inv) {
        float fraction = 0.0F;
        boolean any = false;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty()) {
                fraction += (float) stack.getCount() / Math.min(inv.getMaxStackSize(), stack.getMaxStackSize());
                any = true;
            }
        }
        fraction /= inv.getContainerSize();
        return Mth.floor(fraction * 14.0F) + (any ? 1 : 0);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("BoundDim", boundDim);
        tag.putLong("BoundPos", boundPos);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        boundDim = tag.getString("BoundDim");
        boundPos = tag.getLong("BoundPos");
    }
}
