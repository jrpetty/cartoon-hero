package com.gadgets;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * The Enhancement Bench screen — a workshop panel drawn in the mod's dark
 * amber, with a live description of whatever the grid is currently set up to do.
 */
public class EnhancementScreen extends AbstractContainerScreen<EnhancementMenu> {
    private static final int PANEL = 0xF01A1410;
    private static final int FRAME = 0xFF4E4034;
    private static final int SLOT_BG = 0xFF120E0A;
    private static final int SLOT_EDGE = 0xFF3A3028;
    private static final int AMBER = 0xFFFFC864;
    private static final int DIM = 0xFFB08A50;
    private static final int GRAY = 0xFF8E8478;
    private static final int GREEN = 0xFF7CE87C;

    private Button enhanceButton;

    public EnhancementScreen(EnhancementMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 200;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
        enhanceButton = Button.builder(Component.literal("Enhance"), b -> {
            Minecraft client = Minecraft.getInstance();
            if (client.gameMode != null) {
                client.gameMode.handleInventoryButtonClick(menu.containerId, EnhancementMenu.BUTTON_ENHANCE);
            }
        }).bounds(leftPos + 96, topPos + 38, 68, 20).build();
        addRenderableWidget(enhanceButton);
    }

    @Override
    protected void renderBg(GuiGraphics gfx, float delta, int mouseX, int mouseY) {
        gfx.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, PANEL);
        gfx.renderOutline(leftPos, topPos, imageWidth, imageHeight, FRAME);
        for (int i = 0; i < menu.slots.size(); i++) {
            var slot = menu.slots.get(i);
            int sx = leftPos + slot.x;
            int sy = topPos + slot.y;
            gfx.fill(sx - 1, sy - 1, sx + 17, sy + 17, SLOT_BG);
            gfx.renderOutline(sx - 1, sy - 1, 18, 18, i == EnhancementMenu.HOOK_SLOT ? AMBER : SLOT_EDGE);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics gfx, int mouseX, int mouseY) {
        gfx.drawString(font, title, 8, 6, AMBER, false);
        gfx.drawString(font, playerInventoryTitle, 8, inventoryLabelY, DIM, false);

        EnhancementMenu.Preview preview = menu.preview();
        gfx.drawString(font, trim(preview.title(), 30), 8, 80, preview.ready() ? GREEN : GRAY, false);
        if (!preview.detail().isEmpty()) {
            gfx.drawString(font, trim(preview.detail(), 34), 8, 92, GRAY, false);
        }

        ItemStack hook = menu.slots.get(EnhancementMenu.HOOK_SLOT).getItem();
        if (!hook.isEmpty()) {
            int left = GrappleUpgrades.usesLeft(hook);
            gfx.drawString(font, "Condition " + left + "/" + GrappleUpgrades.MAX_USES
                    + " · " + GrappleUpgrades.installed(hook).size() + "/5 upgrades", 8, 104,
                    left <= 10 ? 0xFFFF5555 : DIM, false);
        }
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float delta) {
        // AbstractContainerScreen#render already draws the hovered-slot tooltip.
        super.render(gfx, mouseX, mouseY, delta);
        if (enhanceButton != null) {
            enhanceButton.active = menu.preview().ready();
        }
    }

    private static String trim(String s, int max) {
        return s.length() > max ? s.substring(0, max - 1) + "…" : s;
    }
}
