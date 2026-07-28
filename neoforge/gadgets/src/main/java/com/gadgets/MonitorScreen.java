package com.gadgets;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;

/** Stock Monitor config: live stock readout and the low-stock alert level. */
public class MonitorScreen extends GadgetScreen {
    private final StockMonitorBlockEntity be;
    private EditBox nameField;

    public MonitorScreen(StockMonitorBlockEntity be) {
        super(Component.literal("Stock Monitor"), 240, 172);
        this.be = be;
    }

    @Override
    protected void init() {
        super.init();
        nameField = new EditBox(font, left + 52, top + 18, 112, 14, Component.literal("Name"));
        nameField.setMaxLength(24);
        nameField.setHint(Component.literal("name this monitor"));
        nameField.setValue(be.getCustomName());
        addRenderableWidget(nameField);
        addRenderableWidget(Button.builder(Component.literal("Save"), b ->
                        sendText(be.getBlockPos(), "set_name", nameField.getValue()))
                .bounds(left + 168, top + 18, 60, 14).build());

        for (int i = 0; i < StockMonitorBlockEntity.THRESHOLDS.length; i++) {
            final int value = StockMonitorBlockEntity.THRESHOLDS[i];
            addRenderableWidget(Button.builder(Component.literal(String.valueOf(value)), b ->
                            send(be.getBlockPos(), "monitor_threshold", value))
                    .bounds(left + 12 + i * 31, top + 118, 28, 14).build());
        }
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float delta) {
        super.render(gfx, mouseX, mouseY, delta);
        int x = left + 12;
        gfx.drawString(font, "Name", x, top + 22, DIM, false);

        String tracked = be.getTracked() == Items.AIR ? "nothing — right-click with an item"
                : ItemCounterBlockEntity.displayName(BuiltInRegistries.ITEM.getKey(be.getTracked()).toString());
        gfx.drawString(font, "Tracking: " + tracked, x, top + 40, AMBER, false);
        String stock = !be.hasContainer() ? "no container in front!"
                : ItemCounterBlockEntity.fmt(be.getCount()) + " in stock";
        gfx.drawString(font, "Stock: " + stock, x, top + 52, be.hasContainer() ? AMBER : RED, false);
        gfx.drawString(font, be.isLow() ? "LOW — redstone alert active" : "Alert below " + be.getThreshold(),
                x, top + 64, be.isLow() ? RED : GREEN, false);
        gfx.drawString(font, be.hasContainer()
                        ? "Container holds " + be.getDistinctTypes() + " different item type"
                          + (be.getDistinctTypes() == 1 ? "" : "s")
                        : "Point the back of this panel at a container",
                x, top + 82, GRAY, false);

        gfx.drawString(font, "Alert level:", x, top + 106, DIM, false);
        gfx.drawString(font, "Wire the redstone to auto-restock", x, top + 142, GRAY, false);
        gfx.drawString(font, "Monitor Wand links it to a Command Hub", x, top + 154, GRAY, false);
    }
}
