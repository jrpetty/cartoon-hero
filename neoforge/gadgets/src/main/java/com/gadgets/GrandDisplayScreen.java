package com.gadgets;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/** Grand Display config: row ordering and how many rows to show. */
public class GrandDisplayScreen extends GadgetScreen {
    private final GrandDisplayBlockEntity be;

    public GrandDisplayScreen(GrandDisplayBlockEntity be) {
        super(Component.literal("Grand Display"), 250, 150);
        this.be = be;
    }

    @Override
    protected void init() {
        super.init();
        for (int i = 0; i < GrandDisplayBlockEntity.ORDER_LABELS.length; i++) {
            final int order = i;
            addRenderableWidget(Button.builder(
                            Component.literal(GrandDisplayBlockEntity.ORDER_LABELS[i]), b -> {
                                send(be.getBlockPos(), "grand_order", order);
                                rebuildWidgets();
                            })
                    .bounds(left + 12 + i * 76, top + 46, 72, 16).build())
                    .active = be.getOrder() != i;
        }

        addRenderableWidget(Button.builder(
                        Component.literal(be.isLarge() ? "Large rows" : "Compact rows"), b -> {
                            send(be.getBlockPos(), "grand_large", be.isLarge() ? 0 : 1);
                            rebuildWidgets();
                        })
                .bounds(left + 12, top + 86, 110, 16).build());
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float delta) {
        super.render(gfx, mouseX, mouseY, delta);
        int x = left + 12;
        BlockPos hub = be.hubBlockPos();
        gfx.drawString(font, "Hub at " + hub.getX() + ", " + hub.getY() + ", " + hub.getZ(),
                x, top + 20, GRAY, false);
        gfx.drawString(font, "Showing " + be.getRows().size() + " of " + (be.getRows().size() + be.hiddenRows())
                + " linked gadgets", x, top + 32, AMBER, false);

        gfx.drawString(font, "Order:", x, top + 68, DIM, false);
        gfx.drawString(font, be.isLarge()
                        ? GrandDisplayBlockEntity.ROWS_LARGE + " big rows — readable from further away"
                        : GrandDisplayBlockEntity.ROWS_COMPACT + " small rows — more of the base at once",
                x + 116, top + 90, GRAY, false);

        gfx.drawString(font, "The board draws " + GrandDisplayRenderer.BLOCKS_WIDE + " blocks wide —",
                x, top + 114, GRAY, false);
        gfx.drawString(font, "build it into a flat wall, centre block here.", x, top + 126, GRAY, false);
    }
}
