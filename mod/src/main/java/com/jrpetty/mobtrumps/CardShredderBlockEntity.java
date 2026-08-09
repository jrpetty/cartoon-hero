package com.jrpetty.mobtrumps;

import com.jrpetty.mobtrumps.game.MobCard;
import com.jrpetty.mobtrumps.game.Recycler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The shredder, wired for hoppers.
 *
 * <p>Nothing in the mod could be automated. A shredder is the obvious first
 * thing that should be: pulping a chest of spares one click at a time is
 * exactly the sort of job a hopper exists for.
 *
 * <p>Two slots, and the sided access rules a hopper reads: cards go in from
 * anywhere except the bottom, fragments come out of the bottom only. It grinds
 * on a timer rather than instantly so the block has something to show and a
 * pipeline has a rate rather than a spike.
 *
 * <p><b>It does not know what you already own.</b> The by-hand shredder refuses
 * your last copy of a mob, which is a kindness that depends on knowing whose
 * cards these are — and a hopper has no owner. So automation deliberately has
 * no such protection: whatever is fed in is pulped. That is the trade for
 * running it unattended, and it is the reason the manual path keeps its guard
 * rather than both being loosened to match.
 */
public class CardShredderBlockEntity extends BlockEntity implements WorldlyContainer {

    public static final int SLOT_IN = 0;
    public static final int SLOT_OUT = 1;

    /** Ticks to pulp one card. Twenty is a card a second — brisk, not instant. */
    public static final int GRIND_TICKS = 20;

    private static final int[] TOP_AND_SIDES = {SLOT_IN};
    private static final int[] BOTTOM = {SLOT_OUT};

    private final NonNullList<ItemStack> items = NonNullList.withSize(2, ItemStack.EMPTY);
    private int grinding;

    public CardShredderBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.CARD_SHREDDER_BE.get(), pos, state);
    }

    /** How far through the current card it is, 0..1 — for the block's animation. */
    public float progress() {
        return grinding <= 0 ? 0f : grinding / (float) GRIND_TICKS;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state,
                                  CardShredderBlockEntity be) {
        ItemStack input = be.items.get(SLOT_IN);
        MobCard card = MobCardItem.cardOf(input);
        if (card == null) {
            be.grinding = 0;
            return;
        }
        int paid = Recycler.yield(card.tier(), CardIdentityService.wearOf(input).condition());
        ItemStack out = be.items.get(SLOT_OUT);
        // Refuse to start what cannot be finished: pulping a card and then
        // discovering the output is full would destroy it for nothing.
        if (!out.isEmpty() && (!out.is(ModItems.CARD_FRAGMENT.get())
                || out.getCount() + paid > out.getMaxStackSize())) {
            return;
        }
        if (++be.grinding < GRIND_TICKS) {
            be.setChanged();
            return;
        }
        be.grinding = 0;
        input.shrink(1);
        if (out.isEmpty()) {
            be.items.set(SLOT_OUT, new ItemStack(ModItems.CARD_FRAGMENT.get(), paid));
        } else {
            out.grow(paid);
        }
        level.playSound(null, pos, net.minecraft.sounds.SoundEvents.GRINDSTONE_USE,
                net.minecraft.sounds.SoundSource.BLOCKS, 0.35F, 1.1F);
        be.setChanged();
        level.updateNeighbourForOutputSignal(pos, state.getBlock());
    }

    /** Fullness of the output, for a comparator. */
    public int signal() {
        ItemStack out = items.get(SLOT_OUT);
        if (out.isEmpty()) {
            return items.get(SLOT_IN).isEmpty() ? 0 : 1;
        }
        return Math.max(1, Math.min(15,
                1 + out.getCount() * 14 / Math.max(1, out.getMaxStackSize())));
    }

    // --- WorldlyContainer ---

    @Override
    public int[] getSlotsForFace(Direction side) {
        return side == Direction.DOWN ? BOTTOM : TOP_AND_SIDES;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) {
        return slot == SLOT_IN && MobCardItem.cardOf(stack) != null;
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return slot == SLOT_OUT;
    }

    @Override
    public int getContainerSize() {
        return items.size();
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack s : items) {
            if (!s.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack taken = ContainerHelper.removeItem(items, slot, amount);
        if (!taken.isEmpty()) {
            setChanged();
        }
        return taken;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        if (stack.getCount() > getMaxStackSize()) {
            stack.setCount(getMaxStackSize());
        }
        if (slot == SLOT_IN) {
            grinding = 0;   // a swapped card starts its own grind
        }
        setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return level != null && level.getBlockEntity(worldPosition) == this
                && player.distanceToSqr(worldPosition.getCenter()) <= 64.0;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot == SLOT_IN && MobCardItem.cardOf(stack) != null;
    }

    @Override
    public void clearContent() {
        items.clear();
        setChanged();
    }

    // --- persistence ---

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items.clear();
        ContainerHelper.loadAllItems(tag, items, registries);
        grinding = tag.getInt("Grinding");
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        tag.putInt("Grinding", grinding);
    }
}
