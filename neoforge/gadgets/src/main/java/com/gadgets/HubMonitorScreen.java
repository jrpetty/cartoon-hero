package com.gadgets;

import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * Pick which line of the linked hub's board this monitor shows. Rows list the
 * gadget's own name so a wall of monitors can be told apart.
 */
public class HubMonitorScreen extends GadgetScreen {
    private static final int ROWS_PER_PAGE = 8;
    private static final int ROW_H = 18;

    private final CommandHubMonitorBlockEntity be;
    private int page = 0;

    public HubMonitorScreen(CommandHubMonitorBlockEntity be) {
        super(Component.literal("Monitor — choose a display"), 280, 204);
        this.be = be;
    }

    private int maxPage() {
        return Math.max(0, (be.choices().size() - 1) / ROWS_PER_PAGE);
    }

    @Override
    protected void init() {
        super.init();
        page = Math.min(page, maxPage());
        List<CommandHubMonitorBlockEntity.Choice> all = be.choices();
        int start = page * ROWS_PER_PAGE;

        for (int i = start; i < Math.min(all.size(), start + ROWS_PER_PAGE); i++) {
            CommandHubMonitorBlockEntity.Choice choice = all.get(i);
            int y = top + 34 + (i - start) * ROW_H;
            boolean showing = be.isShowing(choice);
            addRenderableWidget(Button.builder(
                            Component.literal(showing ? "Showing" : "Show"), b -> {
                                sendText(be.getBlockPos(), "hubmon_pick", choice.dim() + "@" + choice.pos());
                                onClose();
                            })
                    .bounds(left + panelW - 76, y - 3, 64, 16)
                    .build()).active = !showing;
        }

        if (maxPage() > 0) {
            addRenderableWidget(Button.builder(Component.literal("◀"), b -> {
                page = Math.max(0, page - 1);
                rebuildWidgets();
            }).bounds(left + 12, top + panelH - 22, 20, 14).build());
            addRenderableWidget(Button.builder(Component.literal("▶"), b -> {
                page = Math.min(maxPage(), page + 1);
                rebuildWidgets();
            }).bounds(left + 36, top + panelH - 22, 20, 14).build());
        }

        addRenderableWidget(Button.builder(Component.literal("Clear"), b -> {
            sendText(be.getBlockPos(), "hubmon_pick", "");
            onClose();
        }).bounds(left + panelW - 76, top + panelH - 22, 64, 14).build());
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float delta) {
        super.render(gfx, mouseX, mouseY, delta);
        int x = left + 12;
        BlockPos hub = be.hubBlockPos();
        gfx.drawString(font, "Hub at " + hub.getX() + ", " + hub.getY() + ", " + hub.getZ(),
                x, top + 20, GRAY, false);

        List<CommandHubMonitorBlockEntity.Choice> all = be.choices();
        if (all.isEmpty()) {
            gfx.drawString(font, "The hub has nothing linked yet.", x, top + 44, GRAY, false);
            gfx.drawString(font, "Link counters and monitors to it first,", x, top + 58, GRAY, false);
            gfx.drawString(font, "with the Monitor Wand.", x, top + 72, GRAY, false);
            return;
        }

        page = Math.min(page, maxPage());
        int start = page * ROWS_PER_PAGE;
        for (int i = start; i < Math.min(all.size(), start + ROWS_PER_PAGE); i++) {
            CommandHubMonitorBlockEntity.Choice choice = all.get(i);
            int y = top + 34 + (i - start) * ROW_H;
            boolean showing = be.isShowing(choice);
            BlockPos p = BlockPos.of(choice.pos());
            String kind = choice.type() == CommandHubBlockEntity.TYPE_COUNTER ? "counter" : "stock";
            String name = choice.label().isBlank() ? "(unnamed " + kind + ")" : choice.label();

            String dot = choice.alarmed() ? "● " : "";
            gfx.drawString(font, (showing ? "▶ " : "  ") + dot + name, x, y,
                    choice.alarmed() ? RED : showing ? GREEN : AMBER, false);
            gfx.drawString(font, kind + " · " + p.getX() + "," + p.getY() + "," + p.getZ(),
                    x + 8, y + 9, GRAY, false);
        }
        if (maxPage() > 0) {
            gfx.drawString(font, (page + 1) + " / " + (maxPage() + 1), left + 62, top + panelH - 19, GRAY, false);
        }
    }
}
