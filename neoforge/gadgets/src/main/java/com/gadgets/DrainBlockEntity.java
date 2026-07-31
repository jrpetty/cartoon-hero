package com.gadgets;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Container;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Swallows fluid landing on the drain and holds its disguise block. The
 * disguise slot is a one-slot inventory (block items only, insert while empty)
 * so it can be set by hopper or Item Receiver as well as by hand.
 */
public class DrainBlockEntity extends BlockEntity implements WorldlyContainer {
    private static final int INTERVAL = 10;
    private static final int[] SLOTS = {0};

    private ItemStack disguise = ItemStack.EMPTY;

    public DrainBlockEntity(BlockPos pos, BlockState state) {
        super(Gadgets.DRAIN_BE.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, DrainBlockEntity be) {
        if (!TickPhase.due(level, pos, INTERVAL)) {
            return;
        }
        BlockPos up = pos.above();
        BlockState above = level.getBlockState(up);
        // Only act on actual fluid blocks — never a waterlogged block someone built.
        if (above.getFluidState().isEmpty() || !(above.getBlock() instanceof LiquidBlock)) {
            return;
        }
        // Pass the fluid straight through: drop it to the first open space below the
        // drain (or a stack of drains). If there's no room, it just drains away.
        BlockPos.MutableBlockPos target = pos.mutable().move(Direction.DOWN);
        while (level.getBlockState(target).getBlock() instanceof DrainBlock) {
            target.move(Direction.DOWN);
        }
        BlockState landing = level.getBlockState(target);
        // Only land in air or replaceables with no fluid — never overwrite (delete)
        // an existing water/lava source below.
        if (landing.isAir() || (landing.canBeReplaced() && landing.getFluidState().isEmpty())) {
            level.setBlockAndUpdate(target, above);
        }
        level.setBlockAndUpdate(up, Blocks.AIR.defaultBlockState());
    }

    public ItemStack getDisguise() {
        return disguise;
    }

    /** The block state the drain should render as, or null when undisguised. */
    @Nullable
    public BlockState getDisguiseState() {
        return disguise.getItem() instanceof BlockItem blockItem ? blockItem.getBlock().defaultBlockState() : null;
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
        if (level != null && !level.isClientSide()) {
            BlockState state = level.getBlockState(worldPosition);
            if (state.getBlock() instanceof DrainBlock) {
                boolean disguised = getDisguiseState() != null;
                if (state.getValue(DrainBlock.DISGUISED) != disguised) {
                    level.setBlock(worldPosition, state.setValue(DrainBlock.DISGUISED, disguised), Block.UPDATE_ALL);
                }
            }
        }
    }

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    // --- Container: one disguise slot, block items only, no automated extraction ---

    @Override
    public int getContainerSize() {
        return 1;
    }

    @Override
    public boolean isEmpty() {
        return disguise.isEmpty();
    }

    @Override
    public ItemStack getItem(int slot) {
        return slot == 0 ? disguise : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        if (slot != 0 || disguise.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack split = disguise.split(amount);
        updateDisguisedProperty();
        sync();
        return split;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return slot == 0 ? removeDisguise() : ItemStack.EMPTY;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot == 0) {
            disguise = stack;
            updateDisguisedProperty();
            sync();
        }
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return disguise.isEmpty() && stack.getItem() instanceof BlockItem;
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        disguise = ItemStack.EMPTY;
        updateDisguisedProperty();
        sync();
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        return SLOTS;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction dir) {
        return canPlaceItem(slot, stack);
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction dir) {
        return false;
    }

    // --- sync + persistence ---

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
        if (!disguise.isEmpty()) {
            tag.put("Disguise", disguise.save(registries));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        disguise = tag.contains("Disguise")
                ? ItemStack.parseOptional(registries, tag.getCompound("Disguise"))
                : ItemStack.EMPTY;
    }
}
