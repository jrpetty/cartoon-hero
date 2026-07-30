package com.gadgets;

import java.util.ArrayList;
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
 * individually instead of only en masse. A sort toggle can bring alarmed
 * gadgets to the top, which is what actually matters once a base has more
 * than a page of them linked.
 */
public class HubScreen extends GadgetScreen {
    private static final int ROWS_PER_PAGE = 8;
    private static final int ROW_H = 18;
    // Totals bar: TOP..32. Sort button: its own row, 36..48. Column header
    // text: 52. First row: ROW_TOP. Keeping each on its own band is what the
    // Enhancement Bench screen learned the hard way — two things sharing a y
    // coordinate is how text ends up drawn on top of itself.
    private static final int ROW_TOP = 64;
    private static final int TABLE_BG = 0xFF161A20;
    private static final int ROW_ALT = 0xFF1B2028;
    private static final String[] SORT_LABELS = {"Link order", "Alerts first", "A–Z"};

    private final CommandHubBlockEntity be;
    private int page = 0;
    /** 0 = link order, 1 = alarmed first, 2 = alphabetical. Purely a view over
     *  the synced board — never sent to the server. */
    private int sortMode = 0;
    /** Two-step guard so a stray click can never wipe the board. */
    private boolean armed = false;

    public HubScreen(CommandHubBlockEntity be) {
        super(Component.literal("Command Hub"), 320, 236);
        this.be = be;
    }

    private int maxPage() {
        return Math.max(0, (be.getNodes().size() - 1) / ROWS_PER_PAGE);
    }

    /** Indices into {@code be.getNodes()}, ordered for display under the
     *  current sort mode. Returning indices (not a copy of the nodes) means a
     *  row's disconnect button always targets the gadget actually shown. */
    private List<Integer> sortedIndices() {
        List<CommandHubBlockEntity.Node> nodes = be.getNodes();
        List<Integer> order = new ArrayList<>(nodes.size());
        for (int i = 0; i < nodes.size(); i++) {
            order.add(i);
        }
        switch (sortMode) {
            case 1 -> order.sort((a, c) -> Boolean.compare(!nodes.get(a).alarmed(), !nodes.get(c).alarmed()));
            case 2 -> order.sort((a, c) -> nodes.get(a).label.compareToIgnoreCase(nodes.get(c).label));
            default -> {
                // link order — the list is already in that order
            }
        }
        return order;
    }

    @Override
    protected void init() {
        super.init();
        page = Math.min(page, maxPage());
        List<Integer> order = sortedIndices();
        int start = page * ROWS_PER_PAGE;

        // One disconnect button per row, targeting the real board index behind
        // whichever sorted position it's drawn at. The server re-validates that
        // index against its live list, so a board that changed under us drops
        // the click rather than unlinking the wrong thing.
        for (int i = start; i < Math.min(order.size(), start + ROWS_PER_PAGE); i++) {
            int index = order.get(i);
            int y = top + ROW_TOP + (i - start) * ROW_H;
            addRenderableWidget(Button.builder(Component.literal("✕"), b -> {
                send(be.getBlockPos(), "hub_unlink", index);
                rebuildWidgets();
            }).bounds(left + panelW - 26, y - 3, 16, 16).build());
        }

        if (!be.getNodes().isEmpty()) {
            addRenderableWidget(Button.builder(Component.literal("Sort: " + SORT_LABELS[sortMode]), b -> {
                sortMode = (sortMode + 1) % SORT_LABELS.length;
                rebuildWidgets();
            }).bounds(left + panelW - 118, top + 36, 110, 12).build());
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
        int alarms = be.alarmCount();
        gfx.fill(left + 8, top + 18, left + panelW - 8, top + 32, TABLE_BG);
        gfx.drawString(font, nodes.size() + "/" + CommandHubBlockEntity.MAX_NODES + " linked", x, top + 21, AMBER, false);
        gfx.drawString(font, ItemCounterBlockEntity.fmt(be.totalRateMin()) + " items/min",
                left + 108, top + 21, AMBER, false);
        gfx.drawString(font, alarms == 0 ? "all clear" : alarms + " alert" + (alarms == 1 ? "" : "s"),
                left + 226, top + 21, alarms == 0 ? GREEN : RED, false);

        if (nodes.isEmpty()) {
            gfx.drawString(font, "Nothing linked yet.", x, top + 48, GRAY, false);
            gfx.drawString(font, "Take the Monitor Wand, click this hub,", x, top + 64, GRAY, false);
            gfx.drawString(font, "then click your counters and monitors.", x, top + 76, GRAY, false);
            gfx.drawString(font, "Sneak-click a linked one to unlink it.", x, top + 88, GRAY, false);
            return;
        }

        // Column header.
        gfx.drawString(font, "GADGET", x, top + 52, DIM, false);
        gfx.drawString(font, "READING", left + 150, top + 52, DIM, false);

        page = Math.min(page, maxPage());
        List<Integer> order = sortedIndices();
        int start = page * ROWS_PER_PAGE;
        for (int i = start; i < Math.min(order.size(), start + ROWS_PER_PAGE); i++) {
            CommandHubBlockEntity.Node n = nodes.get(order.get(i));
            int row = i - start;
            int y = top + ROW_TOP + row * ROW_H;
            if (row % 2 == 0) {
                gfx.fill(left + 8, y - 4, left + panelW - 8, y + 13, ROW_ALT);
            }

            BlockPos p = BlockPos.of(n.pos);
            boolean counter = n.type == CommandHubBlockEntity.TYPE_COUNTER;
            String kind = counter ? "counter" : "stock";
            String name = n.label.isBlank() ? "(unnamed " + kind + ")" : n.label;
            boolean alarmed = n.alarmed();

            int dot = !n.online ? GRAY : alarmed ? RED : GREEN;
            gfx.drawString(font, "●", x, y, dot, false);
            gfx.drawString(font, trim(name, 20), x + 12, y, n.online ? AMBER : GRAY, false);
            gfx.drawString(font, kind + " · " + p.getX() + "," + p.getY() + "," + p.getZ(),
                    x + 12, y + 9, GRAY, false);

            if (!n.online) {
                gfx.drawString(font, "offline (unloaded)", left + 150, y, GRAY, false);
            } else if (counter) {
                gfx.drawString(font, ItemCounterBlockEntity.compact(n.a) + "/m", left + 150, y, alarmed ? RED : AMBER, false);
                gfx.drawString(font, alarmed ? "STALLED — no items" : ItemCounterBlockEntity.compact(n.b)
                        + "/h · " + ItemCounterBlockEntity.compact(n.c) + " tot", left + 150, y + 9,
                        alarmed ? RED : GRAY, false);
            } else {
                gfx.drawString(font, ItemCounterBlockEntity.fmt(n.a), left + 150, y, alarmed ? RED : GREEN, false);
                gfx.drawString(font, alarmed ? "LOW  < " + n.b : n.d + " types",
                        left + 150, y + 9, alarmed ? RED : GRAY, false);
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
