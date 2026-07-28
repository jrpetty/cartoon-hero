package com.grapplinghook;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.screen.slot.Slot;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;

/**
 * The Enhancement Bench's 3×3 grid: the hook goes in the middle, eight stacks of
 * one material ring it, and the button fuses them into an upgrade.
 *
 * <p>The grid is scratch space like a crafting table — anything left in it is
 * returned when the screen closes, so an upgrade can never eat a hook by
 * accident.
 */
public class EnhancementScreenHandler extends ScreenHandler {
    public static final int GRID_SIZE = 9;
    public static final int HOOK_SLOT = 4;
    public static final int BUTTON_ENHANCE = 0;

    /** Outcome of inspecting the grid — drives both the button and the label. */
    public record Preview(boolean ready, String title, String detail) {
    }

    private final Inventory grid = new SimpleInventory(GRID_SIZE) {
        @Override
        public void markDirty() {
            super.markDirty();
            onContentChanged(this);
        }
    };
    private final ScreenHandlerContext context;
    private final PlayerEntity player;

    public EnhancementScreenHandler(int syncId, PlayerInventory playerInventory) {
        this(syncId, playerInventory, ScreenHandlerContext.EMPTY);
    }

    public EnhancementScreenHandler(int syncId, PlayerInventory playerInventory, ScreenHandlerContext context) {
        super(GrapplingHookMod.ENHANCEMENT_SCREEN_HANDLER, syncId);
        this.context = context;
        this.player = playerInventory.player;

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                int index = col + row * 3;
                addSlot(new Slot(grid, index, 30 + col * 18, 20 + row * 18) {
                    @Override
                    public boolean canInsert(ItemStack stack) {
                        // Only the middle takes a hook, and it takes nothing else.
                        boolean isHook = stack.isOf(GrapplingHookMod.GRAPPLING_HOOK);
                        return index == HOOK_SLOT ? isHook : !isHook;
                    }
                });
            }
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 118 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, 176));
        }
    }

    /** What the grid would do right now, for the button and the status line. */
    public Preview preview() {
        ItemStack hook = grid.getStack(HOOK_SLOT);
        if (hook.isEmpty()) {
            return new Preview(false, "Place a Grappling Hook in the middle", "");
        }

        // Repair: every outer slot holds iron or string, mixed freely.
        boolean repairable = true;
        for (int i = 0; i < GRID_SIZE; i++) {
            if (i == HOOK_SLOT) {
                continue;
            }
            ItemStack s = grid.getStack(i);
            boolean ok = (s.isOf(Items.IRON_INGOT) || s.isOf(Items.STRING))
                    && s.getCount() >= GrappleUpgrades.REPAIR_PER_SLOT;
            if (!ok) {
                repairable = false;
                break;
            }
        }
        if (repairable && GrappleUpgrades.wear(hook) > 0) {
            return new Preview(true, "Repair", "Restores all " + GrappleUpgrades.MAX_USES + " uses");
        }

        // Upgrades: eight identical stacks of the right material.
        for (GrappleUpgrades.Upgrade u : GrappleUpgrades.ALL) {
            boolean match = true;
            for (int i = 0; i < GRID_SIZE; i++) {
                if (i == HOOK_SLOT) {
                    continue;
                }
                ItemStack s = grid.getStack(i);
                if (!s.isOf(u.material()) || s.getCount() < u.perSlot()) {
                    match = false;
                    break;
                }
            }
            if (match) {
                return GrappleUpgrades.has(hook, u)
                        ? new Preview(false, u.name() + " already installed", u.effect())
                        : new Preview(true, u.name(), u.effect());
            }
        }
        return new Preview(false, "Ring the hook with a material", "8 × 20 string · iron · blaze powder · gunpowder · membrane");
    }

    @Override
    public boolean onButtonClick(PlayerEntity clicker, int id) {
        if (id != BUTTON_ENHANCE || !preview().ready()) {
            return false;
        }
        ItemStack hook = grid.getStack(HOOK_SLOT);
        if (hook.isEmpty()) {
            return false;
        }

        // Repair first — it shares its materials with no upgrade.
        boolean repairing = true;
        for (int i = 0; i < GRID_SIZE && repairing; i++) {
            if (i == HOOK_SLOT) {
                continue;
            }
            ItemStack s = grid.getStack(i);
            repairing = (s.isOf(Items.IRON_INGOT) || s.isOf(Items.STRING))
                    && s.getCount() >= GrappleUpgrades.REPAIR_PER_SLOT;
        }
        if (repairing && GrappleUpgrades.wear(hook) > 0) {
            consume(GrappleUpgrades.REPAIR_PER_SLOT);
            GrappleUpgrades.repair(hook);
            celebrate();
            return true;
        }

        for (GrappleUpgrades.Upgrade u : GrappleUpgrades.ALL) {
            boolean match = true;
            for (int i = 0; i < GRID_SIZE; i++) {
                if (i == HOOK_SLOT) {
                    continue;
                }
                ItemStack s = grid.getStack(i);
                if (!s.isOf(u.material()) || s.getCount() < u.perSlot()) {
                    match = false;
                    break;
                }
            }
            if (match && !GrappleUpgrades.has(hook, u)) {
                consume(u.perSlot());
                GrappleUpgrades.apply(hook, u);
                celebrate();
                return true;
            }
        }
        return false;
    }

    /** Take the cost out of every outer slot. */
    private void consume(int perSlot) {
        for (int i = 0; i < GRID_SIZE; i++) {
            if (i == HOOK_SLOT) {
                continue;
            }
            grid.getStack(i).decrement(perSlot);
        }
        grid.markDirty();
        sendContentUpdates();
    }

    private void celebrate() {
        context.run((world, pos) -> world.playSound(null, pos, SoundEvents.BLOCK_ANVIL_USE,
                SoundCategory.BLOCKS, 0.8F, 1.4F));
    }

    @Override
    public void onContentChanged(Inventory inventory) {
        super.onContentChanged(inventory);
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (slot.hasStack()) {
            ItemStack stack = slot.getStack();
            result = stack.copy();
            if (index < GRID_SIZE) {
                if (!insertItem(stack, GRID_SIZE, slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else if (!insertItem(stack, 0, GRID_SIZE, false)) {
                return ItemStack.EMPTY;
            }
            if (stack.isEmpty()) {
                slot.setStack(ItemStack.EMPTY);
            } else {
                slot.markDirty();
            }
        }
        return result;
    }

    @Override
    public void onClosed(PlayerEntity closer) {
        super.onClosed(closer);
        // Scratch space: hand everything back rather than swallowing it.
        dropInventory(closer, grid);
    }

    @Override
    public boolean canUse(PlayerEntity user) {
        return canUse(context, user, GrapplingHookMod.ENHANCEMENT_TABLE);
    }
}
