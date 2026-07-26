package com.gadgets;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;

/** Stock Monitor config: live stock readout and the low-stock alert level. */
public class MonitorScreen extends GadgetScreen {
    private final StockMonitorBlockEntity be;

    public MonitorScreen(StockMonitorBlockEntity be) {
        super(Text.literal("Stock Monitor"), 240, 132);
        this.be = be;
    }

    @Override
    protected void init() {
        super.init();
        for (int i = 0; i < StockMonitorBlockEntity.THRESHOLDS.length; i++) {
            final int value = StockMonitorBlockEntity.THRESHOLDS[i];
            addDrawableChild(ButtonWidget.builder(Text.literal(String.valueOf(value)), b ->
                            send(be.getPos(), "monitor_threshold", value))
                    .dimensions(left + 12 + i * 31, top + 78, 28, 14).build());
        }
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        int x = left + 12;
        String tracked = be.getTracked() == Items.AIR ? "nothing — right-click with an item"
                : ItemCounterBlockEntity.displayName(Registries.ITEM.getId(be.getTracked()).toString());
        ctx.drawText(textRenderer, "Tracking: " + tracked, x, top + 20, AMBER, false);
        String stock = !be.hasContainer() ? "no container in front!"
                : ItemCounterBlockEntity.fmt(be.getCount()) + " in stock";
        ctx.drawText(textRenderer, "Stock: " + stock, x, top + 32,
                be.hasContainer() ? AMBER : RED, false);
        ctx.drawText(textRenderer, be.isLow() ? "LOW — redstone alert active" : "Alert below " + be.getThreshold(),
                x, top + 44, be.isLow() ? RED : GREEN, false);
        ctx.drawText(textRenderer, "Alert level:", x, top + 66, DIM, false);
        ctx.drawText(textRenderer, "Wire the redstone to auto-restock", x, top + 104, GRAY, false);
        ctx.drawText(textRenderer, "Monitor Wand links it to a Command Hub", x, top + 116, GRAY, false);
    }
}
