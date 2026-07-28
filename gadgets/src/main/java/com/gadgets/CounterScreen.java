package com.gadgets;

import java.util.List;
import java.util.Map;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/** Item Counter dashboard: live rates, totals, per-item breakdown, and controls. */
public class CounterScreen extends GadgetScreen {
    private final ItemCounterBlockEntity be;
    private TextFieldWidget nameField;

    public CounterScreen(ItemCounterBlockEntity be) {
        super(Text.literal("Item Counter"), 240, 232);
        this.be = be;
    }

    @Override
    protected void init() {
        super.init();
        nameField = new TextFieldWidget(textRenderer, left + 52, top + 18, 112, 14, Text.literal("Name"));
        nameField.setMaxLength(24);
        nameField.setPlaceholder(Text.literal("name this counter"));
        nameField.setText(be.getCustomName());
        addDrawableChild(nameField);
        addDrawableChild(ButtonWidget.builder(Text.literal("Save"), b ->
                        sendText(be.getPos(), "set_name", nameField.getText()))
                .dimensions(left + 168, top + 18, 60, 14).build());

        // Pulse size presets.
        for (int i = 0; i < ItemCounterBlockEntity.THRESHOLDS.length; i++) {
            final int value = ItemCounterBlockEntity.THRESHOLDS[i];
            addDrawableChild(ButtonWidget.builder(Text.literal(String.valueOf(value)), b ->
                            send(be.getPos(), "counter_threshold", value))
                    .dimensions(left + 12 + i * 36, top + 146, 33, 14).build());
        }
        // Face readout modes.
        for (int i = 0; i < ItemCounterBlockEntity.MODE_LABELS.length; i++) {
            final int idx = i;
            addDrawableChild(ButtonWidget.builder(Text.literal(ItemCounterBlockEntity.MODE_LABELS[i]), b ->
                            send(be.getPos(), "counter_mode", idx))
                    .dimensions(left + 12 + i * 55, top + 178, 52, 14).build());
        }
        addDrawableChild(ButtonWidget.builder(Text.literal("Reset statistics"), b ->
                        send(be.getPos(), "counter_reset", 0))
                .dimensions(left + 12, top + 206, 216, 16).build());
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        int x = left + 12;
        ctx.drawText(textRenderer, "Name", x, top + 22, DIM, false);
        ctx.drawText(textRenderer, "Rate  " + ItemCounterBlockEntity.fmt(be.getRateMin()) + " /min · "
                + ItemCounterBlockEntity.fmt(be.getRateHour()) + " /hour", x, top + 40, AMBER, false);
        ctx.drawText(textRenderer, "Total " + ItemCounterBlockEntity.fmt(be.getTotal()) + " in "
                + ItemCounterBlockEntity.duration(be.getUptimeTicks()), x, top + 52, AMBER, false);
        ctx.drawText(textRenderer, "Pulse " + be.getCount() + " / " + be.getThreshold(), x, top + 64, DIM, false);

        List<Map.Entry<String, Long>> top5 = be.topItems(5);
        ctx.drawText(textRenderer, top5.isEmpty() ? "No items counted yet" : "Top items", x, top + 82, GRAY, false);
        for (int i = 0; i < top5.size(); i++) {
            Map.Entry<String, Long> e = top5.get(i);
            String line = "• " + trim(ItemCounterBlockEntity.displayName(e.getKey()), 22)
                    + "  × " + ItemCounterBlockEntity.fmt(e.getValue());
            ctx.drawText(textRenderer, line, x + 4, top + 94 + i * 10, GRAY, false);
        }
        ctx.drawText(textRenderer, "Pulse every:", x, top + 136, DIM, false);
        ctx.drawText(textRenderer, "Screen shows:  (now " + be.faceLabel() + ")", x, top + 168, DIM, false);
    }

    private static String trim(String s, int max) {
        return s.length() > max ? s.substring(0, max - 1) + "…" : s;
    }
}
