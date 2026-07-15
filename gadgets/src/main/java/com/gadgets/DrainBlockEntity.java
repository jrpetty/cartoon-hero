package com.gadgets;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FluidBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SidedInventory;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Swallows fluid landing on the drain and holds its disguise block. The
 * disguise slot is a one-slot inventory (block items only, insert while empty)
 * so it can be set by hopper or Item Receiver as well as by hand.
 */
public class DrainBlockEntity extends BlockEntity implements SidedInventory {
    private static final int INTERVAL = 10;
    private static final int[] SLOTS = {0};

    private ItemStack disguise = ItemStack.EMPTY;

    public DrainBlockEntity(BlockPos pos, BlockState state) {
        super(Gadgets.DRAIN_BE, pos, state);
    }

    public static void tick(World world, BlockPos pos, BlockState state, DrainBlockEntity be) {
        if (world.getTime() % INTERVAL != 0L) {
            return;
        }
        BlockPos up = pos.up();
        BlockState above = world.getBlockState(up);
        // Only act on actual fluid blocks — never a waterlogged block someone built.
        if (above.getFluidState().isEmpty() || !(above.getBlock() instanceof FluidBlock)) {
            return;
        }
        // Pass the fluid straight through: drop it to the first open space below the
        // drain (or a stack of drains). If there's no room, it just drains away.
        BlockPos.Mutable target = pos.mutableCopy().move(Direction.DOWN);
        while (world.getBlockState(target).getBlock() instanceof DrainBlock) {
            target.move(Direction.DOWN);
        }
        BlockState landing = world.getBlockState(target);
        if (landing.isAir() || landing.isReplaceable()) {
            world.setBlockState(target, above);
        }
        world.setBlockState(up, Blocks.AIR.getDefaultState());
    }

    public ItemStack getDisguise() {
        return disguise;
    }

    /** The block state the drain should render as, or null when undisguised. */
    @Nullable
    public BlockState getDisguiseState() {
        return disguise.getItem() instanceof BlockItem blockItem ? blockItem.getBlock().getDefaultState() : null;
    }

    public void setDisguise(ItemStack stack) {
        this.disguise = stack;
        updateDisguisedProperty();
        sync();
    }

    public ItemStack removeDisguise() {
        ItemStack taken = disguise;
        disguise = ItemStack.EMPTY;
        updateDisguisedProperty();
        sync();
        return taken;
    }

    private void updateDisguisedProperty() {
        if (world != null && !world.isClient()) {
            BlockState state = world.getBlockState(pos);
            if (state.getBlock() instanceof DrainBlock) {
                boolean disguised = getDisguiseState() != null;
                if (state.get(DrainBlock.DISGUISED) != disguised) {
                    world.setBlockState(pos, state.with(DrainBlock.DISGUISED, disguised), Block.NOTIFY_ALL);
                }
            }
        }
    }

    private void sync() {
        markDirty();
        if (world != null && !world.isClient()) {
            world.updateListeners(pos, getCachedState(), getCachedState(), Block.NOTIFY_ALL);
        }
    }

    // --- Inventory: one disguise slot, block items only, no automated extraction ---

    @Override
    public int size() {
        return 1;
    }

    @Override
    public boolean isEmpty() {
        return disguise.isEmpty();
    }

    @Override
    public ItemStack getStack(int slot) {
        return slot == 0 ? disguise : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeStack(int slot, int amount) {
        if (slot != 0 || disguise.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack split = disguise.split(amount);
        updateDisguisedProperty();
        sync();
        return split;
    }

    @Override
    public ItemStack removeStack(int slot) {
        return slot == 0 ? removeDisguise() : ItemStack.EMPTY;
    }

    @Override
    public void setStack(int slot, ItemStack stack) {
        if (slot == 0) {
            disguise = stack;
            updateDisguisedProperty();
            sync();
        }
    }

    @Override
    public int getMaxCountPerStack() {
        return 1;
    }

    @Override
    public boolean isValid(int slot, ItemStack stack) {
        return disguise.isEmpty() && stack.getItem() instanceof BlockItem;
    }

    @Override
    public boolean canPlayerUse(PlayerEntity player) {
        return Inventory.canPlayerUse(this, player);
    }

    @Override
    public void clear() {
        disguise = ItemStack.EMPTY;
        updateDisguisedProperty();
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
        if (!disguise.isEmpty()) {
            nbt.put("Disguise", disguise.encode(registries));
        }
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        disguise = nbt.contains("Disguise")
                ? ItemStack.fromNbt(registries, nbt.get("Disguise")).orElse(ItemStack.EMPTY)
                : ItemStack.EMPTY;
    }
}
