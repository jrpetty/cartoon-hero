package com.gadgets;

import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;

/**
 * Stock Monitor config: what it tracks, the live total, and the alert level.
 *
 * <p>Rows are laid out on fixed bands — name, readout, tracked list, hints,
 * alert level — each with its own y range, so nothing can overlap however many
 * items end up in the list.
 */
public class MonitorScreen extends GadgetScreen {
    /** First row of the tracked list. Up to MAX_TRACKED rows of ROW_H follow. */
    private static final int LIST_TOP = 76;
    private static final int ROW_H = 12;
    /** Hints sit below the longest possible list. */
    private static final int HINTS_TOP = LIST_TOP + StockMonitorBlockEntity.MAX_TRACKED * ROW_H + 4;
    private static final int ALERT_TOP = HINTS_TOP + 26;

    private final StockMonitorBlockEntity be;
    private EditBox nameField;

    public MonitorScreen(StockMonitorBlockEntity be) {
        super(Component.literal("Stock Monitor"), 240, ALERT_TOP + 62);
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
        addRenderableWidget(PanelButton.of(Component.literal("Save"), b ->
                        sendText(be.getBlockPos(), "set_name", nameField.getValue()), left + 168, top + 18, 60, 14));

        // Tag mode has no list to edit, so it gets one button back to item mode.
        if (be.isTagMode()) {
            addRenderableWidget(PanelButton.of(Component.literal("Clear tag"), b -> {
                send(be.getBlockPos(), "monitor_clear", 0);
                rebuildWidgets();
            }, left + 164, top + LIST_TOP - 3, 64, 14));
        } else {
            List<Item> tracked = be.getTracked();
            for (int i = 0; i < tracked.size(); i++) {
                String id = BuiltInRegistries.ITEM.getKey(tracked.get(i)).toString();
                int y = top + LIST_TOP + i * ROW_H;
                addRenderableWidget(PanelButton.of(Component.literal("✕"), b -> {
                    sendText(be.getBlockPos(), "monitor_untrack", id);
                    rebuildWidgets();
                }, left + 210, y - 2, 14, 11));
            }
        }

        for (int i = 0; i < StockMonitorBlockEntity.THRESHOLDS.length; i++) {
            final int value = StockMonitorBlockEntity.THRESHOLDS[i];
            addRenderableWidget(PanelButton.of(Component.literal(String.valueOf(value)), b ->
                            send(be.getBlockPos(), "monitor_threshold", value), left + 12 + i * 31, top + ALERT_TOP + 12, 28, 14));
        }
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float delta) {
        super.render(gfx, mouseX, mouseY, delta);
        int x = left + 12;
        gfx.drawString(font, "Name", x, top + 22, DIM, false);

        panel(gfx, left + 8, top + 36, left + panelW - 8, top + 62, 0xFF161A20);
        String stock = !be.hasContainer() ? "no container in front!"
                : ItemCounterBlockEntity.fmt(be.getCount()) + " in stock";
        gfx.drawString(font, "Stock: " + stock, x, top + 40, be.hasContainer() ? AMBER : RED, false);
        gfx.drawString(font, be.isLow() ? "LOW — redstone alert active" : "Alert below " + be.getThreshold(),
                x, top + 52, be.isLow() ? RED : GREEN, false);
        gfx.drawString(font, be.isTagMode() ? "Tracking tag:" : "Tracking:", x, top + 64, DIM, false);

        if (be.isTagMode()) {
            gfx.drawString(font, "#" + StockMonitorBlockEntity.shortTag(be.getTagId()),
                    x, top + LIST_TOP, AMBER, false);
            gfx.drawString(font, "Totals every item in this tag.", x, top + LIST_TOP + ROW_H, GRAY, false);
        } else {
            List<Item> tracked = be.getTracked();
            if (tracked.isEmpty()) {
                gfx.drawString(font, "nothing yet", x, top + LIST_TOP, GRAY, false);
            }
            for (int i = 0; i < tracked.size(); i++) {
                gfx.drawString(font, "· " + trim(tracked.get(i).getDescription().getString(), 28),
                        x, top + LIST_TOP + i * ROW_H, AMBER, false);
            }
        }

        gfx.drawString(font, "Right-click an item: add or remove it", x, top + HINTS_TOP, GRAY, false);
        gfx.drawString(font, "Sneak + right-click: cycle that item's tags", x, top + HINTS_TOP + 11, GRAY, false);

        gfx.drawString(font, "Alert level:", x, top + ALERT_TOP, DIM, false);
        gfx.drawString(font, "Wire the redstone to auto-restock", x, top + ALERT_TOP + 32, GRAY, false);
        gfx.drawString(font, "Monitor Wand links it to a Command Hub", x, top + ALERT_TOP + 44, GRAY, false);
    }

    private static String trim(String s, int max) {
        return s.length() > max ? s.substring(0, max - 1) + "…" : s;
    }
}
