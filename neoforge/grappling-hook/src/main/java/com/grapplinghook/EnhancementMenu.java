package com.grapplinghook;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The Enhancement Bench's 3×3 grid: the hook goes in the middle, eight stacks of
 * one material ring it, and the button fuses them into an upgrade.
 *
 * <p>The grid is scratch space like a crafting table — anything left in it is
 * returned when the screen closes, so an upgrade can never eat a hook by
 * accident.
 */
public class EnhancementMenu extends AbstractContainerMenu {
    public static final int GRID_SIZE = 9;
    public static final int HOOK_SLOT = 4;
    public static final int BUTTON_ENHANCE = 0;

    /** Outcome of inspecting the grid — drives both the button and the label. */
    public record Preview(boolean ready, String title, String detail) {
    }

    private final Container grid = new SimpleContainer(GRID_SIZE) {
        @Override
        public void setChanged() {
            super.setChanged();
            slotsChanged(this);
        }
    };
    private final ContainerLevelAccess access;

    public EnhancementMenu(int id, Inventory playerInventory) {
        this(id, playerInventory, ContainerLevelAccess.NULL);
    }

    public EnhancementMenu(int id, Inventory playerInventory, ContainerLevelAccess access) {
        super(GrapplingHookMod.ENHANCEMENT_MENU.get(), id);
        this.access = access;

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                int index = col + row * 3;
                addSlot(new Slot(grid, index, 30 + col * 18, 20 + row * 18) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        // Only the middle takes a hook, and it takes nothing else.
                        boolean isHook = stack.is(GrapplingHookMod.GRAPPLING_HOOK.get());
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
        ItemStack hook = grid.getItem(HOOK_SLOT);
        if (hook.isEmpty()) {
            return new Preview(false, "Place a Grappling Hook in the middle", "");
        }

        if (repairLayout() && GrappleUpgrades.wear(hook) > 0) {
            return new Preview(true, "Repair", "Restores all " + GrappleUpgrades.MAX_USES + " uses");
        }

        for (GrappleUpgrades.Upgrade u : GrappleUpgrades.ALL) {
            if (upgradeLayout(u)) {
                return GrappleUpgrades.has(hook, u)
                        ? new Preview(false, u.name() + " already installed", u.effect())
                        : new Preview(true, u.name(), u.effect());
            }
        }
        return new Preview(false, "Ring the hook with a material",
                "8 × 20 string · iron · blaze powder · gunpowder · membrane");
    }

    /** Every outer slot holds iron or string, mixed freely. */
    private boolean repairLayout() {
        for (int i = 0; i < GRID_SIZE; i++) {
            if (i == HOOK_SLOT) {
                continue;
            }
            ItemStack s = grid.getItem(i);
            boolean ok = (s.is(Items.IRON_INGOT) || s.is(Items.STRING))
                    && s.getCount() >= GrappleUpgrades.REPAIR_PER_SLOT;
            if (!ok) {
                return false;
            }
        }
        return true;
    }

    /** Every outer slot holds enough of this upgrade's material. */
    private boolean upgradeLayout(GrappleUpgrades.Upgrade u) {
        for (int i = 0; i < GRID_SIZE; i++) {
            if (i == HOOK_SLOT) {
                continue;
            }
            ItemStack s = grid.getItem(i);
            if (!s.is(u.material()) || s.getCount() < u.perSlot()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id != BUTTON_ENHANCE || !preview().ready()) {
            return false;
        }
        ItemStack hook = grid.getItem(HOOK_SLOT);
        if (hook.isEmpty()) {
            return false;
        }

        // Repair first — it shares its materials with no upgrade.
        if (repairLayout() && GrappleUpgrades.wear(hook) > 0) {
            consume(GrappleUpgrades.REPAIR_PER_SLOT);
            GrappleUpgrades.repair(hook);
            celebrate();
            return true;
        }

        for (GrappleUpgrades.Upgrade u : GrappleUpgrades.ALL) {
            if (upgradeLayout(u) && !GrappleUpgrades.has(hook, u)) {
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
            grid.getItem(i).shrink(perSlot);
        }
        grid.setChanged();
        broadcastChanges();
    }

    private void celebrate() {
        access.execute((level, pos) -> level.playSound(null, pos, SoundEvents.ANVIL_USE,
                SoundSource.BLOCKS, 0.8F, 1.4F));
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (slot.hasItem()) {
            ItemStack stack = slot.getItem();
            result = stack.copy();
            if (index < GRID_SIZE) {
                if (!moveItemStackTo(stack, GRID_SIZE, slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else if (!moveItemStackTo(stack, 0, GRID_SIZE, false)) {
                return ItemStack.EMPTY;
            }
            if (stack.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }
        return result;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        // Scratch space: hand everything back rather than swallowing it.
        clearContainer(player, grid);
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, GrapplingHookMod.ENHANCEMENT_TABLE.get());
    }
}
