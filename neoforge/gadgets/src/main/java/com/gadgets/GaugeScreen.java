package com.gadgets;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/** Config for a fill gauge: its name and the level it should alert below. */
public class GaugeScreen extends GadgetScreen {
    private final HubGauge be;
    private EditBox nameField;
    /** Last value pushed into the box, so a server update can refresh it
     *  without overwriting what the player is typing. */
    private String shownName = "";

    public GaugeScreen(HubGauge be, String title) {
        super(Component.literal(title), 240, 168);
        this.be = be;
    }

    @Override
    protected void init() {
        super.init();
        shownName = be.getCustomName();
        nameField = new EditBox(font, left + 52, top + 18, 112, 14, Component.literal("Name"));
        nameField.setMaxLength(24);
        nameField.setHint(Component.literal("name this gauge"));
        nameField.setValue(shownName);
        addRenderableWidget(nameField);
        addRenderableWidget(Button.builder(Component.literal("Save"), b ->
                        sendText(be.getBlockPos(), "set_name", nameField.getValue()))
                .bounds(left + 168, top + 18, 60, 14).build());

        for (int i = 0; i < HubGauge.THRESHOLDS.length; i++) {
            final int value = HubGauge.THRESHOLDS[i];
            addRenderableWidget(Button.builder(Component.literal(value + "%"), b ->
                            send(be.getBlockPos(), "gauge_threshold", value))
                    .bounds(left + 12 + i * 44, top + 108, 40, 14).build())
                    .active = be.getThreshold() != value;
        }
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float delta) {
        String current = be.getCustomName();
        if (!current.equals(shownName) && !nameField.isFocused()) {
            shownName = current;
            nameField.setValue(current);
        }

        super.render(gfx, mouseX, mouseY, delta);
        int x = left + 12;
        gfx.drawString(font, "Name", x, top + 22, DIM, false);

        if (!be.hasSource()) {
            gfx.drawString(font, "Nothing readable in front!", x, top + 44, RED, false);
            gfx.drawString(font, "Point the back of this panel at a tank,", x, top + 62, GRAY, false);
            gfx.drawString(font, "cell, or machine buffer.", x, top + 74, GRAY, false);
        } else {
            gfx.drawString(font, be.percent() + "% full", x, top + 44, be.isLow() ? RED : AMBER, false);
            gfx.drawString(font, be.amountText(), x, top + 58, GRAY, false);

            // A bar, because a percentage alone is hard to feel.
            int barW = 216;
            int filled = barW * Math.max(0, Math.min(100, be.percent())) / 100;
            gfx.fill(x, top + 74, x + barW, top + 84, 0xFF241C14);
            if (filled > 0) {
                gfx.fill(x, top + 74, x + filled, top + 84, be.isLow() ? RED : GREEN);
            }
            gfx.renderOutline(x, top + 74, barW, 10, FRAME);

            gfx.drawString(font, be.isLow()
                            ? "LOW — redstone alert active"
                            : "Alert below " + be.getThreshold() + "%",
                    x, top + 90, be.isLow() ? RED : GREEN, false);
        }

        gfx.drawString(font, "Alert level:", x, top + 128, DIM, false);
        gfx.drawString(font, "Monitor Wand links it to a Command Hub", x, top + 144, GRAY, false);
    }
}
