package com.gadgets;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
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
    private static final int LINE = 10;
    private static final int MAX_LINES = 2;

    private Button enhanceButton;

    public EnhancementScreen(EnhancementMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        // Tall enough for the worst-case status block: a two-line preview title,
        // a two-line detail line and the condition row, all clear of the
        // inventory label below them.
        this.imageHeight = 224;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
        enhanceButton = PanelButton.of(Component.literal("Enhance"), b -> {
            Minecraft client = Minecraft.getInstance();
            if (client.gameMode != null) {
                client.gameMode.handleInventoryButtonClick(menu.containerId, EnhancementMenu.BUTTON_ENHANCE);
            }
        }, leftPos + 96, topPos + 38, 68, 20);
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

        // One cursor down the panel so nothing can land on top of anything else,
        // however long the wrapped strings turn out to be.
        EnhancementMenu.Preview preview = menu.preview();
        int y = 80;
        y = drawWrapped(gfx, preview.title(), y, preview.ready() ? GREEN : GRAY);
        if (!preview.detail().isEmpty()) {
            y = drawWrapped(gfx, preview.detail(), y, GRAY);
        }

        ItemStack hook = menu.slots.get(EnhancementMenu.HOOK_SLOT).getItem();
        if (!hook.isEmpty()) {
            int left = GrappleUpgrades.usesLeft(hook);
            gfx.drawString(font, "Condition " + left + "/" + GrappleUpgrades.MAX_USES
                            + " · " + GrappleUpgrades.installed(hook).size() + "/5 upgrades", 8, y,
                    left <= 10 ? 0xFFFF5555 : DIM, false);
        }
    }

    /** Draws {@code text} wrapped to the panel, capped at two lines, and
     *  returns the y the next row should use. */
    private int drawWrapped(GuiGraphics gfx, String text, int y, int colour) {
        List<FormattedCharSequence> lines = font.split(Component.literal(text), imageWidth - 16);
        for (int i = 0; i < Math.min(lines.size(), MAX_LINES); i++) {
            gfx.drawString(font, lines.get(i), 8, y, colour, false);
            y += LINE;
        }
        return y;
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float delta) {
        // AbstractContainerScreen#render already draws the hovered-slot tooltip.
        super.render(gfx, mouseX, mouseY, delta);
        if (enhanceButton != null) {
            enhanceButton.active = menu.preview().ready();
        }
    }

}
