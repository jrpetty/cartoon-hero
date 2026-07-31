package com.gadgets;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/**
 * Config for a fill gauge: its name and the level it should alert below.
 *
 * <p>The bar carries a marker at the alert level, so choosing a threshold is a
 * question of where the line sits relative to the fill rather than a number in
 * the abstract.
 */
public class GaugeScreen extends GadgetScreen {
    private static final int NAME_Y = 18;
    private static final int READOUT_Y = 44;
    private static final int BAR_Y = 76;
    private static final int BAR_H = 14;
    private static final int STATUS_Y = 98;
    private static final int THRESHOLD_Y = 122;
    private static final int FOOTER_Y = 152;

    private static final int TRACK = 0xFF241C14;
    private static final int TICK = 0xFF4E4034;
    private static final int MARKER = 0xFFFFFFFF;

    private final HubGauge be;
    private final String kind;
    private EditBox nameField;
    /** Last value pushed into the box, so a server update can refresh it
     *  without overwriting what the player is typing. */
    private String shownName = "";

    public GaugeScreen(HubGauge be, String title) {
        super(Component.literal(title), 250, 186);
        this.be = be;
        this.kind = title.startsWith("Fluid") ? "tank" : "cell";
    }

    @Override
    protected void init() {
        super.init();
        shownName = be.getCustomName();
        nameField = new EditBox(font, left + 52, top + NAME_Y, 122, 14, Component.literal("Name"));
        nameField.setMaxLength(24);
        nameField.setHint(Component.literal("name this gauge"));
        nameField.setValue(shownName);
        addRenderableWidget(nameField);
        addRenderableWidget(Button.builder(Component.literal("Save"), b ->
                        sendText(be.getBlockPos(), "set_name", nameField.getValue()))
                .bounds(left + 178, top + NAME_Y, 60, 14).build());

        for (int i = 0; i < HubGauge.THRESHOLDS.length; i++) {
            final int value = HubGauge.THRESHOLDS[i];
            addRenderableWidget(Button.builder(Component.literal(value + "%"), b -> {
                send(be.getBlockPos(), "gauge_threshold", value);
                rebuildWidgets();
            }).bounds(left + 12 + i * 46, top + THRESHOLD_Y + 12, 42, 14).build())
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
        gfx.drawString(font, "Name", x, top + NAME_Y + 4, DIM, false);

        if (!be.hasSource()) {
            gfx.drawString(font, "Nothing readable in front", x, top + READOUT_Y, RED, false);
            gfx.drawString(font, "Point the back of this panel at a " + kind + ".", x, top + READOUT_Y + 16, GRAY, false);
            gfx.drawString(font, "Anything exposing the capability works —", x, top + READOUT_Y + 28, GRAY, false);
            gfx.drawString(font, "vanilla or modded, it needn't know about us.", x, top + READOUT_Y + 40, GRAY, false);
        } else {
            int percent = be.percent();
            int colour = be.isLow() ? RED : GREEN;

            // The percentage at double size — the one number worth reading first.
            gfx.pose().pushPose();
            gfx.pose().translate((float) x, (float) (top + READOUT_Y), 0.0F);
            gfx.pose().scale(2.0F, 2.0F, 1.0F);
            gfx.drawString(font, percent + "%", 0, 0, colour, false);
            gfx.pose().popPose();

            gfx.drawString(font, be.amountText(), x + 78, top + READOUT_Y + 8, AMBER, false);

            // Track, fill, ticks every 25%, and the alert marker.
            int barW = panelW - 24;
            gfx.fill(x, top + BAR_Y, x + barW, top + BAR_Y + BAR_H, TRACK);
            int filled = barW * Math.max(0, Math.min(100, percent)) / 100;
            if (filled > 0) {
                gfx.fill(x, top + BAR_Y, x + filled, top + BAR_Y + BAR_H, colour);
            }
            for (int q = 1; q < 4; q++) {
                int tx = x + barW * q / 4;
                gfx.fill(tx, top + BAR_Y, tx + 1, top + BAR_Y + BAR_H, TICK);
            }
            int marker = x + barW * be.getThreshold() / 100;
            gfx.fill(marker, top + BAR_Y - 3, marker + 1, top + BAR_Y + BAR_H + 3, MARKER);
            gfx.renderOutline(x, top + BAR_Y, barW, BAR_H, FRAME);

            gfx.drawString(font, be.isLow()
                            ? "LOW — redstone alert active"
                            : "Above the " + be.getThreshold() + "% alert line",
                    x, top + STATUS_Y, be.isLow() ? RED : GREEN, false);
            gfx.drawString(font, "The white mark is the alert level.", x, top + STATUS_Y + 12, GRAY, false);
        }

        gfx.drawString(font, "Alert below:", x, top + THRESHOLD_Y, DIM, false);
        gfx.drawString(font, "Emits redstone while below the line", x, top + FOOTER_Y, GRAY, false);
        gfx.drawString(font, "Monitor Wand links it to a Command Hub", x, top + FOOTER_Y + 12, GRAY, false);
    }
}
