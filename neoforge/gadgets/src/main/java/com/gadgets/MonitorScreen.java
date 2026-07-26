package com.gadgets;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;

/** Stock Monitor config: live stock readout and the low-stock alert level. */
public class MonitorScreen extends GadgetScreen {
    private final StockMonitorBlockEntity be;

    public MonitorScreen(StockMonitorBlockEntity be) {
        super(Component.literal("Stock Monitor"), 240, 132);
        this.be = be;
    }

    @Override
    protected void init() {
        super.init();
        for (int i = 0; i < StockMonitorBlockEntity.THRESHOLDS.length; i++) {
            final int value = StockMonitorBlockEntity.THRESHOLDS[i];
            addRenderableWidget(Button.builder(Component.literal(String.valueOf(value)), b ->
                            send(be.getBlockPos(), "monitor_threshold", value))
                    .bounds(left + 12 + i * 31, top + 78, 28, 14).build());
        }
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float delta) {
        super.render(gfx, mouseX, mouseY, delta);
        int x = left + 12;
        String tracked = be.getTracked() == Items.AIR ? "nothing — right-click with an item"
                : ItemCounterBlockEntity.displayName(BuiltInRegistries.ITEM.getKey(be.getTracked()).toString());
        gfx.drawString(font, "Tracking: " + tracked, x, top + 20, AMBER, false);
        String stock = !be.hasContainer() ? "no container in front!"
                : ItemCounterBlockEntity.fmt(be.getCount()) + " in stock";
        gfx.drawString(font, "Stock: " + stock, x, top + 32, be.hasContainer() ? AMBER : RED, false);
        gfx.drawString(font, be.isLow() ? "LOW — redstone alert active" : "Alert below " + be.getThreshold(),
                x, top + 44, be.isLow() ? RED : GREEN, false);
        gfx.drawString(font, "Alert level:", x, top + 66, DIM, false);
        gfx.drawString(font, "Wire the redstone to auto-restock", x, top + 104, GRAY, false);
        gfx.drawString(font, "Monitor Wand links it to a Command Hub", x, top + 116, GRAY, false);
    }
}
