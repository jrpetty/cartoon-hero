package com.gadgets;

import java.util.List;
import java.util.Map;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;

/** Item Counter dashboard: live rates, totals, per-item breakdown, and controls. */
public class CounterScreen extends GadgetScreen {
    private static final int NAME_Y = 18;
    private static final int STATS_Y = 40;
    private static final int DIR_Y = 78;
    private static final int FILTER_Y = 100;
    private static final int TOP_ITEMS_Y = 152;
    private static final int PULSE_Y = 206;
    private static final int MODE_Y = 238;
    private static final int RESET_Y = 266;

    private final ItemCounterBlockEntity be;
    private EditBox nameField;
    /** The name last pushed into the box, so a server update can refresh it
     *  without stamping over what the player is part-way through typing. */
    private String shownName = "";

    public CounterScreen(ItemCounterBlockEntity be) {
        super(Component.literal("Item Counter"), 240, RESET_Y + 26);
        this.be = be;
    }

    @Override
    protected void init() {
        super.init();
        shownName = be.getCustomName();
        nameField = new EditBox(font, left + 52, top + NAME_Y, 112, 14, Component.literal("Name"));
        nameField.setMaxLength(24);
        nameField.setHint(Component.literal("name this counter"));
        nameField.setValue(shownName);
        addRenderableWidget(nameField);
        addRenderableWidget(Button.builder(Component.literal("Save"), b ->
                        sendText(be.getBlockPos(), "set_name", nameField.getValue()))
                .bounds(left + 168, top + NAME_Y, 60, 14).build());

        // Which direction of movement counts.
        for (int i = 0; i < ItemCounterBlockEntity.COUNT_LABELS.length; i++) {
            final int mode = i;
            addRenderableWidget(Button.builder(
                            Component.literal(ItemCounterBlockEntity.COUNT_LABELS[i]), b -> {
                                send(be.getBlockPos(), "counter_direction", mode);
                                rebuildWidgets();
                            })
                    .bounds(left + 12 + i * 72, top + DIR_Y + 12, 68, 14).build())
                    .active = be.getCountMode() != i;
        }

        // One remove control per filtered item, plus a reset to "everything".
        List<Item> filter = be.getFilter();
        for (int i = 0; i < filter.size(); i++) {
            String id = BuiltInRegistries.ITEM.getKey(filter.get(i)).toString();
            addRenderableWidget(Button.builder(Component.literal("✕"), b -> {
                sendText(be.getBlockPos(), "counter_unfilter", id);
                rebuildWidgets();
            }).bounds(left + 210, top + FILTER_Y + 12 + i * 10 - 2, 14, 10).build());
        }
        if (!filter.isEmpty()) {
            addRenderableWidget(Button.builder(Component.literal("Count everything"), b -> {
                send(be.getBlockPos(), "counter_unfilter_all", 0);
                rebuildWidgets();
            }).bounds(left + 100, top + FILTER_Y, 124, 12).build());
        }

        for (int i = 0; i < ItemCounterBlockEntity.THRESHOLDS.length; i++) {
            final int value = ItemCounterBlockEntity.THRESHOLDS[i];
            addRenderableWidget(Button.builder(Component.literal(String.valueOf(value)), b ->
                            send(be.getBlockPos(), "counter_threshold", value))
                    .bounds(left + 12 + i * 36, top + PULSE_Y + 12, 33, 14).build());
        }
        for (int i = 0; i < ItemCounterBlockEntity.MODE_LABELS.length; i++) {
            final int idx = i;
            addRenderableWidget(Button.builder(Component.literal(ItemCounterBlockEntity.MODE_LABELS[i]), b ->
                            send(be.getBlockPos(), "counter_mode", idx))
                    .bounds(left + 12 + i * 55, top + MODE_Y + 12, 52, 14).build());
        }
        addRenderableWidget(Button.builder(Component.literal("Reset statistics"), b ->
                        send(be.getBlockPos(), "counter_reset", 0))
                .bounds(left + 12, top + RESET_Y, 216, 16).build());
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float delta) {
        // The name arrives from the server after the screen opens, so refresh
        // the box when it changes — unless the player is editing it themselves.
        String current = be.getCustomName();
        if (!current.equals(shownName) && !nameField.isFocused()) {
            shownName = current;
            nameField.setValue(current);
        }

        super.render(gfx, mouseX, mouseY, delta);
        int x = left + 12;
        gfx.drawString(font, "Name", x, top + NAME_Y + 4, DIM, false);

        gfx.drawString(font, "Rate  " + ItemCounterBlockEntity.fmt(be.getRateMin()) + " /min · "
                + ItemCounterBlockEntity.fmt(be.getRateHour()) + " /hour", x, top + STATS_Y, AMBER, false);
        gfx.drawString(font, "Total " + ItemCounterBlockEntity.fmt(be.getTotal()) + " in "
                + ItemCounterBlockEntity.duration(be.getUptimeTicks()), x, top + STATS_Y + 12, AMBER, false);
        gfx.drawString(font, "Pulse " + be.getCount() + " / " + be.getThreshold(), x, top + STATS_Y + 24, DIM, false);

        gfx.drawString(font, be.isWatchingContainer()
                        ? "Counting items " + be.getCountLabel() + " in the container it faces"
                        : "No container ahead — counting items dropping past",
                x, top + DIR_Y, be.isWatchingContainer() ? GRAY : DIM, false);

        List<Item> filter = be.getFilter();
        gfx.drawString(font, "Counting: " + be.describeFilter(), x, top + FILTER_Y, AMBER, false);
        if (filter.isEmpty()) {
            gfx.drawString(font, "Right-click it with an item to count only that",
                    x, top + FILTER_Y + 12, GRAY, false);
        }
        for (int i = 0; i < filter.size(); i++) {
            gfx.drawString(font, "· " + trim(filter.get(i).getDescription().getString(), 26),
                    x, top + FILTER_Y + 12 + i * 10, GRAY, false);
        }

        List<Map.Entry<String, Long>> top5 = be.topItems(4);
        gfx.drawString(font, top5.isEmpty() ? "No items counted yet" : "Top items", x, top + TOP_ITEMS_Y, GRAY, false);
        for (int i = 0; i < top5.size(); i++) {
            Map.Entry<String, Long> e = top5.get(i);
            String line = "• " + trim(ItemCounterBlockEntity.displayName(e.getKey()), 22)
                    + "  × " + ItemCounterBlockEntity.fmt(e.getValue());
            gfx.drawString(font, line, x + 4, top + TOP_ITEMS_Y + 12 + i * 10, GRAY, false);
        }

        gfx.drawString(font, "Pulse every:", x, top + PULSE_Y, DIM, false);
        gfx.drawString(font, "Screen shows:  (now " + be.faceLabel() + ")", x, top + MODE_Y, DIM, false);
    }

    private static String trim(String s, int max) {
        return s.length() > max ? s.substring(0, max - 1) + "…" : s;
    }
}
