package com.grapplinghook;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

/**
 * The Enhancement Bench screen — a workshop panel drawn in the mod's dark
 * amber, with a live description of whatever the grid is currently set up to do.
 */
public class EnhancementScreen extends HandledScreen<EnhancementScreenHandler> {
    private static final int PANEL = 0xF01A1410;
    private static final int FRAME = 0xFF4E4034;
    private static final int SLOT_BG = 0xFF120E0A;
    private static final int SLOT_EDGE = 0xFF3A3028;
    private static final int AMBER = 0xFFFFC864;
    private static final int DIM = 0xFFB08A50;
    private static final int GRAY = 0xFF8E8478;
    private static final int GREEN = 0xFF7CE87C;

    private ButtonWidget enhanceButton;

    public EnhancementScreen(EnhancementScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundWidth = 176;
        this.backgroundHeight = 200;
        this.playerInventoryTitleY = this.backgroundHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
        enhanceButton = ButtonWidget.builder(Text.literal("Enhance"), b -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.interactionManager != null) {
                client.interactionManager.clickButton(handler.syncId, EnhancementScreenHandler.BUTTON_ENHANCE);
            }
        }).dimensions(x + 96, y + 38, 68, 20).build();
        addDrawableChild(enhanceButton);
    }

    @Override
    protected void drawBackground(DrawContext ctx, float delta, int mouseX, int mouseY) {
        ctx.fill(x, y, x + backgroundWidth, y + backgroundHeight, PANEL);
        ctx.drawBorder(x, y, backgroundWidth, backgroundHeight, FRAME);
        for (int i = 0; i < handler.slots.size(); i++) {
            var slot = handler.slots.get(i);
            int sx = x + slot.x;
            int sy = y + slot.y;
            ctx.fill(sx - 1, sy - 1, sx + 17, sy + 17, SLOT_BG);
            ctx.drawBorder(sx - 1, sy - 1, 18, 18, i == EnhancementScreenHandler.HOOK_SLOT ? AMBER : SLOT_EDGE);
        }
    }

    @Override
    protected void drawForeground(DrawContext ctx, int mouseX, int mouseY) {
        ctx.drawText(textRenderer, title, 8, 6, AMBER, false);
        ctx.drawText(textRenderer, playerInventoryTitle, 8, playerInventoryTitleY, DIM, false);

        EnhancementScreenHandler.Preview preview = handler.preview();
        ctx.drawText(textRenderer, trim(preview.title(), 30), 8, 80, preview.ready() ? GREEN : GRAY, false);
        if (!preview.detail().isEmpty()) {
            ctx.drawText(textRenderer, trim(preview.detail(), 34), 8, 92, GRAY, false);
        }

        ItemStack hook = handler.slots.get(EnhancementScreenHandler.HOOK_SLOT).getStack();
        if (!hook.isEmpty()) {
            int left = GrappleUpgrades.usesLeft(hook);
            ctx.drawText(textRenderer, "Condition " + left + "/" + GrappleUpgrades.MAX_USES
                    + " · " + GrappleUpgrades.installed(hook).size() + "/5 upgrades", 8, 104,
                    left <= 10 ? 0xFFFF5555 : DIM, false);
        }
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        if (enhanceButton != null) {
            enhanceButton.active = handler.preview().ready();
        }
        drawMouseoverTooltip(ctx, mouseX, mouseY);
    }

    private static String trim(String s, int max) {
        return s.length() > max ? s.substring(0, max - 1) + "…" : s;
    }
}
