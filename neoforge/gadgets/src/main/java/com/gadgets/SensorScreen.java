package com.gadgets;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Entity Sensor config: detection radius and target type. */
public class SensorScreen extends GadgetScreen {
    private static final String[] MODE_NAMES = {"Players", "Monsters", "Animals", "All"};

    private final PlayerSensorBlockEntity be;

    public SensorScreen(PlayerSensorBlockEntity be) {
        super(Component.literal("Entity Sensor"), 220, 120);
        this.be = be;
    }

    @Override
    protected void init() {
        super.init();
        addRenderableWidget(Button.builder(Component.literal("−"), b ->
                        send(be.getBlockPos(), "sensor_radius", be.getRadius() - 2))
                .bounds(left + 128, top + 24, 20, 16).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b ->
                        send(be.getBlockPos(), "sensor_radius", be.getRadius() + 2))
                .bounds(left + 152, top + 24, 20, 16).build());
        for (int i = 0; i < MODE_NAMES.length; i++) {
            final int idx = i;
            addRenderableWidget(Button.builder(Component.literal(MODE_NAMES[i]), b ->
                            send(be.getBlockPos(), "sensor_mode", idx))
                    .bounds(left + 12 + i * 50, top + 62, 47, 16).build());
        }
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float delta) {
        super.render(gfx, mouseX, mouseY, delta);
        gfx.drawString(font, "Radius: " + be.getRadius() + " blocks", left + 12, top + 28, AMBER, false);
        int idx = be.modeIndex();
        String target = idx >= 0 ? MODE_NAMES[idx] : be.getTarget();
        gfx.drawString(font, "Watching: " + target, left + 12, top + 48, DIM, false);
        gfx.drawString(font, "Signal = entities in range (max 15)", left + 12, top + 88, GRAY, false);
        gfx.drawString(font, "Spawn egg on the block = one mob type", left + 12, top + 100, GRAY, false);
    }
}
