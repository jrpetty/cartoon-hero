package com.gadgets;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** Entity Sensor config: detection radius and target type. */
public class SensorScreen extends GadgetScreen {
    private static final String[] MODE_NAMES = {"Players", "Monsters", "Animals", "All"};

    private final PlayerSensorBlockEntity be;

    public SensorScreen(PlayerSensorBlockEntity be) {
        super(Text.literal("Entity Sensor"), 220, 120);
        this.be = be;
    }

    @Override
    protected void init() {
        super.init();
        addDrawableChild(ButtonWidget.builder(Text.literal("−"), b ->
                        send(be.getPos(), "sensor_radius", be.getRadius() - 2))
                .dimensions(left + 128, top + 24, 20, 16).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("+"), b ->
                        send(be.getPos(), "sensor_radius", be.getRadius() + 2))
                .dimensions(left + 152, top + 24, 20, 16).build());
        for (int i = 0; i < MODE_NAMES.length; i++) {
            final int idx = i;
            addDrawableChild(ButtonWidget.builder(Text.literal(MODE_NAMES[i]), b ->
                            send(be.getPos(), "sensor_mode", idx))
                    .dimensions(left + 12 + i * 50, top + 62, 47, 16).build());
        }
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        ctx.drawText(textRenderer, "Radius: " + be.getRadius() + " blocks", left + 12, top + 28, AMBER, false);
        int idx = be.modeIndex();
        String target = idx >= 0 ? MODE_NAMES[idx] : be.getTarget();
        ctx.drawText(textRenderer, "Watching: " + target, left + 12, top + 48, DIM, false);
        ctx.drawText(textRenderer, "Signal = entities in range (max 15)", left + 12, top + 88, GRAY, false);
        ctx.drawText(textRenderer, "Spawn egg on the block = one mob type", left + 12, top + 100, GRAY, false);
    }
}
