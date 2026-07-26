package com.gadgets;

import java.util.List;
import java.util.Map;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Item Counter dashboard: live rates, totals, per-item breakdown, and controls. */
public class CounterScreen extends GadgetScreen {
    private final ItemCounterBlockEntity be;

    public CounterScreen(ItemCounterBlockEntity be) {
        super(Component.literal("Item Counter"), 240, 214);
        this.be = be;
    }

    @Override
    protected void init() {
        super.init();
        for (int i = 0; i < ItemCounterBlockEntity.THRESHOLDS.length; i++) {
            final int value = ItemCounterBlockEntity.THRESHOLDS[i];
            addRenderableWidget(Button.builder(Component.literal(String.valueOf(value)), b ->
                            send(be.getBlockPos(), "counter_threshold", value))
                    .bounds(left + 12 + i * 36, top + 128, 33, 14).build());
        }
        for (int i = 0; i < ItemCounterBlockEntity.MODE_LABELS.length; i++) {
            final int idx = i;
            addRenderableWidget(Button.builder(Component.literal(ItemCounterBlockEntity.MODE_LABELS[i]), b ->
                            send(be.getBlockPos(), "counter_mode", idx))
                    .bounds(left + 12 + i * 55, top + 160, 52, 14).build());
        }
        addRenderableWidget(Button.builder(Component.literal("Reset statistics"), b ->
                        send(be.getBlockPos(), "counter_reset", 0))
                .bounds(left + 12, top + 190, 216, 16).build());
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float delta) {
        super.render(gfx, mouseX, mouseY, delta);
        int x = left + 12;
        gfx.drawString(font, "Rate  " + ItemCounterBlockEntity.fmt(be.getRateMin()) + " /min · "
                + ItemCounterBlockEntity.fmt(be.getRateHour()) + " /hour", x, top + 20, AMBER, false);
        gfx.drawString(font, "Total " + ItemCounterBlockEntity.fmt(be.getTotal()) + " in "
                + ItemCounterBlockEntity.duration(be.getUptimeTicks()), x, top + 32, AMBER, false);
        gfx.drawString(font, "Pulse " + be.getCount() + " / " + be.getThreshold(), x, top + 44, DIM, false);

        List<Map.Entry<String, Long>> top5 = be.topItems(5);
        gfx.drawString(font, top5.isEmpty() ? "No items counted yet" : "Top items", x, top + 60, GRAY, false);
        for (int i = 0; i < top5.size(); i++) {
            Map.Entry<String, Long> e = top5.get(i);
            String line = "• " + trim(ItemCounterBlockEntity.displayName(e.getKey()), 22)
                    + "  × " + ItemCounterBlockEntity.fmt(e.getValue());
            gfx.drawString(font, line, x + 4, top + 72 + i * 10, GRAY, false);
        }
        gfx.drawString(font, "Pulse every:", x, top + 118, DIM, false);
        gfx.drawString(font, "Screen shows:  (now " + be.faceLabel() + ")", x, top + 150, DIM, false);
    }

    private static String trim(String s, int max) {
        return s.length() > max ? s.substring(0, max - 1) + "…" : s;
    }
}
