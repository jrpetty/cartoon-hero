package com.gadgets;

import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * The Command Hub board: every linked counter and monitor, live, in one place.
 *
 * <p>Laid out as a table with a header strip, a totals bar and one row per
 * gadget, each row carrying its own disconnect button so links can be dropped
 * individually instead of only en masse.
 */
public class HubScreen extends GadgetScreen {
    private static final int ROWS_PER_PAGE = 8;
    private static final int ROW_H = 18;
    private static final int ROW_TOP = 46;
    private static final int TABLE_BG = 0xFF161A20;
    private static final int ROW_ALT = 0xFF1B2028;

    private final CommandHubBlockEntity be;
    private int page = 0;
    /** Two-step guard so a stray click can never wipe the board. */
    private boolean armed = false;

    public HubScreen(CommandHubBlockEntity be) {
        super(Component.literal("Command Hub"), 320, 230);
        this.be = be;
    }

    private int maxPage() {
        return Math.max(0, (be.getNodes().size() - 1) / ROWS_PER_PAGE);
    }

    @Override
    protected void init() {
        super.init();
        page = Math.min(page, maxPage());
        List<CommandHubBlockEntity.Node> nodes = be.getNodes();
        int start = page * ROWS_PER_PAGE;

        // One disconnect button per row. The index is resolved against the live
        // list on the server, so a board that changed under us drops the click
        // rather than unlinking the wrong thing.
        for (int i = start; i < Math.min(nodes.size(), start + ROWS_PER_PAGE); i++) {
            int index = i;
            int y = top + ROW_TOP + (i - start) * ROW_H;
            addRenderableWidget(Button.builder(Component.literal("✕"), b -> {
                send(be.getBlockPos(), "hub_unlink", index);
                rebuildWidgets();
            }).bounds(left + panelW - 26, y - 3, 16, 16).build());
        }

        if (maxPage() > 0) {
            addRenderableWidget(Button.builder(Component.literal("◀"), b -> {
                page = Math.max(0, page - 1);
                rebuildWidgets();
            }).bounds(left + 12, top + panelH - 24, 20, 14).build());
            addRenderableWidget(Button.builder(Component.literal("▶"), b -> {
                page = Math.min(maxPage(), page + 1);
                rebuildWidgets();
            }).bounds(left + 36, top + panelH - 24, 20, 14).build());
        }

        addRenderableWidget(Button.builder(
                        Component.literal(armed ? "Confirm — clear all" : "Disconnect all"), b -> {
                            if (armed) {
                                send(be.getBlockPos(), "hub_clear", 0);
                                armed = false;
                            } else {
                                armed = true;
                            }
                            rebuildWidgets();
                        })
                .bounds(left + panelW - 130, top + panelH - 24, 118, 14).build());
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float delta) {
        super.render(gfx, mouseX, mouseY, delta);
        List<CommandHubBlockEntity.Node> nodes = be.getNodes();
        int x = left + 12;

        // Totals bar.
        int low = be.lowCount();
        gfx.fill(left + 8, top + 18, left + panelW - 8, top + 32, TABLE_BG);
        gfx.drawString(font, nodes.size() + "/" + CommandHubBlockEntity.MAX_NODES + " linked", x, top + 21, AMBER, false);
        gfx.drawString(font, ItemCounterBlockEntity.fmt(be.totalRateMin()) + " items/min",
                left + 108, top + 21, AMBER, false);
        gfx.drawString(font, low == 0 ? "all stocked" : low + " low",
                left + 226, top + 21, low == 0 ? GREEN : RED, false);

        if (nodes.isEmpty()) {
            gfx.drawString(font, "Nothing linked yet.", x, top + 48, GRAY, false);
            gfx.drawString(font, "Take the Monitor Wand, click this hub,", x, top + 64, GRAY, false);
            gfx.drawString(font, "then click your counters and monitors.", x, top + 76, GRAY, false);
            gfx.drawString(font, "Sneak-click a linked one to unlink it.", x, top + 88, GRAY, false);
            return;
        }

        // Column header.
        gfx.drawString(font, "GADGET", x, top + 36, DIM, false);
        gfx.drawString(font, "READING", left + 150, top + 36, DIM, false);

        page = Math.min(page, maxPage());
        int start = page * ROWS_PER_PAGE;
        for (int i = start; i < Math.min(nodes.size(), start + ROWS_PER_PAGE); i++) {
            CommandHubBlockEntity.Node n = nodes.get(i);
            int row = i - start;
            int y = top + ROW_TOP + row * ROW_H;
            if (row % 2 == 0) {
                gfx.fill(left + 8, y - 4, left + panelW - 8, y + 13, ROW_ALT);
            }

            BlockPos p = BlockPos.of(n.pos);
            boolean counter = n.type == CommandHubBlockEntity.TYPE_COUNTER;
            String kind = counter ? "counter" : "stock";
            String name = n.label.isBlank() ? "(unnamed " + kind + ")" : n.label;
            boolean lowNode = !counter && n.c != 0;

            int dot = !n.online ? GRAY : lowNode ? RED : GREEN;
            gfx.drawString(font, "●", x, y, dot, false);
            gfx.drawString(font, trim(name, 20), x + 12, y, n.online ? AMBER : GRAY, false);
            gfx.drawString(font, kind + " · " + p.getX() + "," + p.getY() + "," + p.getZ(),
                    x + 12, y + 9, GRAY, false);

            if (!n.online) {
                gfx.drawString(font, "offline (unloaded)", left + 150, y, GRAY, false);
            } else if (counter) {
                gfx.drawString(font, ItemCounterBlockEntity.compact(n.a) + "/m", left + 150, y, AMBER, false);
                gfx.drawString(font, ItemCounterBlockEntity.compact(n.b) + "/h · "
                        + ItemCounterBlockEntity.compact(n.c) + " tot", left + 150, y + 9, GRAY, false);
            } else {
                gfx.drawString(font, ItemCounterBlockEntity.fmt(n.a), left + 150, y, lowNode ? RED : GREEN, false);
                gfx.drawString(font, lowNode ? "LOW  < " + n.b : n.d + " types",
                        left + 150, y + 9, lowNode ? RED : GRAY, false);
            }
        }

        if (maxPage() > 0) {
            gfx.drawString(font, (page + 1) + " / " + (maxPage() + 1), left + 62, top + panelH - 21, GRAY, false);
        }
    }

    private static String trim(String s, int max) {
        return s.length() > max ? s.substring(0, max - 1) + "…" : s;
    }
}
