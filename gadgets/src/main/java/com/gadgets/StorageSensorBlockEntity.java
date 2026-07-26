package com.gadgets;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
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
        super(Gadgets.STORAGE_SENSOR_BE, pos, state);
    }

    public boolean isBound() {
        return !boundDim.isEmpty();
    }

    public String describeBinding() {
        if (!isBound()) {
            return "not bound";
        }
        BlockPos p = BlockPos.fromLong(boundPos);
        return p.getX() + ", " + p.getY() + ", " + p.getZ() + " · " + boundDim;
    }

    public void bind(String dim, BlockPos pos) {
        this.boundDim = dim;
        this.boundPos = pos.asLong();
        markDirty();
    }

    public static void tick(World world, BlockPos pos, BlockState state, StorageSensorBlockEntity be) {
        if (world.getTime() % INTERVAL != 0L) {
            return;
        }
        int power = 0;
        Inventory inv = be.resolveContainer(world);
        if (inv != null) {
            power = fillLevel(inv);
        }
        if (state.get(StorageSensorBlock.POWER) != power) {
            world.setBlockState(pos, state.with(StorageSensorBlock.POWER, power), Block.NOTIFY_ALL);
        }
    }

    @Nullable
    private Inventory resolveContainer(World world) {
        if (!isBound()) {
            return null;
        }
        MinecraftServer server = world.getServer();
        if (server == null) {
            return null;
        }
        Identifier dimId = Identifier.tryParse(boundDim);
        if (dimId == null) {
            return null;
        }
        ServerWorld target = server.getWorld(RegistryKey.of(RegistryKeys.WORLD, dimId));
        if (target == null) {
            return null;
        }
        BlockPos cpos = BlockPos.fromLong(boundPos);
        if (!target.isChunkLoaded(cpos.getX() >> 4, cpos.getZ() >> 4)) {
            return null; // never resurrect an unloaded chunk
        }
        return HopperBlockEntity.getInventoryAt(target, cpos);
    }

    /** Vanilla comparator formula: proportional fill, 1 = non-empty, 15 = full. */
    public static int fillLevel(Inventory inv) {
        float fraction = 0.0F;
        boolean any = false;
        for (int i = 0; i < inv.size(); i++) {
            ItemStack stack = inv.getStack(i);
            if (!stack.isEmpty()) {
                fraction += (float) stack.getCount() / Math.min(inv.getMaxCountPerStack(), stack.getMaxCount());
                any = true;
            }
        }
        fraction /= inv.size();
        return net.minecraft.util.math.MathHelper.floor(fraction * 14.0F) + (any ? 1 : 0);
    }

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        nbt.putString("BoundDim", boundDim);
        nbt.putLong("BoundPos", boundPos);
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        boundDim = nbt.getString("BoundDim");
        boundPos = nbt.getLong("BoundPos");
    }
}
