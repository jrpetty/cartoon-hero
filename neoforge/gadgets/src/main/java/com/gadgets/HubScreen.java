package com.gadgets;

import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/** The Command Hub board: every linked counter and monitor, live, in one place. */
public class HubScreen extends GadgetScreen {
    private static final int ROWS_PER_PAGE = 11;

    private final CommandHubBlockEntity be;
    private int page = 0;
    /** Two-step guard so a stray click can never wipe the board. */
    private boolean armed = false;

    public HubScreen(CommandHubBlockEntity be) {
        super(Component.literal("Command Hub"), 300, 214);
        this.be = be;
    }

    @Override
    protected void init() {
        super.init();
        addRenderableWidget(Button.builder(Component.literal("◀"), b -> page = Math.max(0, page - 1))
                .bounds(left + 12, top + 174, 20, 14).build());
        addRenderableWidget(Button.builder(Component.literal("▶"), b -> page = Math.min(maxPage(), page + 1))
                .bounds(left + panelW - 32, top + 174, 20, 14).build());
        // Clearing the whole board lives here rather than on the wand, where it
        // used to fire by accident on a sneak-click.
        addRenderableWidget(Button.builder(
                        Component.literal(armed ? "Confirm — clear all links" : "Reset links"), b -> {
                            if (armed) {
                                send(be.getBlockPos(), "hub_clear", 0);
                                armed = false;
                            } else {
                                armed = true;
                            }
                            rebuildWidgets();
                        })
                .bounds(left + 40, top + 192, panelW - 80, 14).build());
    }

    private int maxPage() {
        return Math.max(0, (be.getNodes().size() - 1) / ROWS_PER_PAGE);
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float delta) {
        super.render(gfx, mouseX, mouseY, delta);
        int x = left + 12;
        List<CommandHubBlockEntity.Node> nodes = be.getNodes();

        gfx.drawString(font, nodes.size() + "/" + CommandHubBlockEntity.MAX_NODES + " linked · "
                + ItemCounterBlockEntity.fmt(be.totalRateMin()) + " items/min · "
                + be.lowCount() + " low", x, top + 20, AMBER, false);

        if (nodes.isEmpty()) {
            gfx.drawString(font, "Nothing linked yet.", x, top + 44, GRAY, false);
            gfx.drawString(font, "Take the Monitor Wand, click this hub,", x, top + 58, GRAY, false);
            gfx.drawString(font, "then click your counters and monitors.", x, top + 70, GRAY, false);
            gfx.drawString(font, "Sneak-click a linked one to unlink it.", x, top + 82, GRAY, false);
            return;
        }

        page = Math.min(page, maxPage());
        int start = page * ROWS_PER_PAGE;
        for (int i = start; i < Math.min(nodes.size(), start + ROWS_PER_PAGE); i++) {
            CommandHubBlockEntity.Node n = nodes.get(i);
            int y = top + 36 + (i - start) * 12;
            BlockPos p = BlockPos.of(n.pos);
            String where = p.getX() + "," + p.getY() + "," + p.getZ();
            if (!n.online) {
                gfx.drawString(font, "○ " + where + " — offline (unloaded)", x, y, GRAY, false);
            } else if (n.type == CommandHubBlockEntity.TYPE_COUNTER) {
                gfx.drawString(font, "● " + trim(n.label, 14) + "  "
                        + ItemCounterBlockEntity.compact(n.a) + "/m · "
                        + ItemCounterBlockEntity.compact(n.b) + "/h · "
                        + ItemCounterBlockEntity.compact(n.c) + " total", x, y, AMBER, false);
            } else {
                boolean low = n.c != 0;
                gfx.drawString(font, (low ? "▼ " : "● ") + trim(n.label, 14) + "  "
                        + ItemCounterBlockEntity.fmt(n.a)
                        + (low ? " LOW(<" + n.b + ")" : "")
                        + " · " + n.d + " types", x, y, low ? RED : GREEN, false);
            }
        }
        if (maxPage() > 0) {
            gfx.drawCenteredString(font, (page + 1) + " / " + (maxPage() + 1), left + panelW / 2, top + 177, GRAY);
        }
    }

    private static String trim(String s, int max) {
        return s.length() > max ? s.substring(0, max - 1) + "…" : s;
    }
}
