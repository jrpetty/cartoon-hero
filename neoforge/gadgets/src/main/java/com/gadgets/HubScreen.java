package com.gadgets;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * The Command Hub board: every linked counter and monitor, live, in one place.
 *
 * <p>A filter box and a sort toggle narrow the list; each row carries its own
 * disconnect control. The board re-syncs every second and the filter can change
 * on any keystroke, so the row list is derived fresh in {@link #visibleRows()}
 * and everything — the drawn rows, the ✕ hit boxes, the page count — reads from
 * that one method. Nothing caches a row index between frames: an index baked
 * into a widget at init time would point at the wrong gadget the moment the
 * board reordered underneath it.
 */
public class HubScreen extends GadgetScreen {
    private static final int ROWS_PER_PAGE = 8;
    private static final int ROW_H = 18;
    private static final int CONTROLS_TOP = 36;
    private static final int HEADER_TOP = 56;
    private static final int ROW_TOP = 68;
    private static final int TABLE_BG = 0xFF161A20;
    private static final int ROW_ALT = 0xFF1B2028;
    private static final int CLOSE_W = 16;
    private static final String[] SORT_LABELS = {"Link order", "Alerts first", "A–Z"};

    private final CommandHubBlockEntity be;
    private int page = 0;
    /** 0 = link order, 1 = alarmed first, 2 = alphabetical. A view over the
     *  synced board — never sent to the server. */
    private int sortMode = 0;
    private String filter = "";
    /** Two-step guard so a stray click can never wipe the board. */
    private boolean armed = false;

    private EditBox filterBox;
    private Button sortButton;
    private Button prevButton;
    private Button nextButton;
    private Button clearButton;

    public HubScreen(CommandHubBlockEntity be) {
        super(Component.literal("Command Hub"), 320, 248);
        this.be = be;
    }

    /** Board indices to show, after filtering and sorting. */
    private List<Integer> visibleRows() {
        List<CommandHubBlockEntity.Node> nodes = be.getNodes();
        String needle = filter.trim().toLowerCase(Locale.ROOT);
        List<Integer> rows = new ArrayList<>(nodes.size());
        for (int i = 0; i < nodes.size(); i++) {
            if (needle.isEmpty() || haystack(nodes.get(i)).contains(needle)) {
                rows.add(i);
            }
        }
        switch (sortMode) {
            case 1 -> rows.sort((a, c) -> Boolean.compare(!nodes.get(a).alarmed(), !nodes.get(c).alarmed()));
            case 2 -> rows.sort((a, c) -> nodes.get(a).label.compareToIgnoreCase(nodes.get(c).label));
            default -> {
                // link order — already in that order
            }
        }
        return rows;
    }

    /** What the filter matches against: name, kind and coordinates. */
    private static String haystack(CommandHubBlockEntity.Node n) {
        BlockPos p = BlockPos.of(n.pos);
        String kind = n.type == CommandHubBlockEntity.TYPE_COUNTER ? "counter" : "stock monitor";
        return (n.label + " " + kind + " " + p.getX() + " " + p.getY() + " " + p.getZ()).toLowerCase(Locale.ROOT);
    }

    private int maxPage() {
        return Math.max(0, (visibleRows().size() - 1) / ROWS_PER_PAGE);
    }

    @Override
    protected void init() {
        super.init();

        // Every widget here is independent of the board's contents, so none of
        // them go stale when it re-syncs. Row controls are hit-tested instead.
        filterBox = new EditBox(font, left + 12, top + CONTROLS_TOP, 150, 14, Component.literal("Filter"));
        filterBox.setMaxLength(32);
        filterBox.setHint(Component.literal("filter by name…"));
        filterBox.setValue(filter);
        filterBox.setResponder(text -> {
            filter = text;
            page = 0;
        });
        addRenderableWidget(filterBox);

        sortButton = addRenderableWidget(Button.builder(Component.literal("Sort: " + SORT_LABELS[sortMode]), b -> {
            sortMode = (sortMode + 1) % SORT_LABELS.length;
            page = 0;
        }).bounds(left + panelW - 118, top + CONTROLS_TOP, 106, 14).build());

        prevButton = addRenderableWidget(Button.builder(Component.literal("◀"), b ->
                page = Math.max(0, page - 1)).bounds(left + 12, top + panelH - 24, 20, 14).build());
        nextButton = addRenderableWidget(Button.builder(Component.literal("▶"), b ->
                page = Math.min(maxPage(), page + 1)).bounds(left + 36, top + panelH - 24, 20, 14).build());

        clearButton = addRenderableWidget(Button.builder(Component.literal("Disconnect all"), b -> {
            if (armed) {
                send(be.getBlockPos(), "hub_clear", 0);
                armed = false;
            } else {
                armed = true;
            }
        }).bounds(left + panelW - 130, top + panelH - 24, 118, 14).build());
    }

    /** The ✕ box for the row drawn at {@code slot} on the current page. */
    private boolean overClose(int slot, double mouseX, double mouseY) {
        int y = top + ROW_TOP + slot * ROW_H - 3;
        int x = left + panelW - 26;
        return mouseX >= x && mouseX < x + CLOSE_W && mouseY >= y && mouseY < y + CLOSE_W;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            List<Integer> rows = visibleRows();
            int start = Math.min(page, maxPage()) * ROWS_PER_PAGE;
            for (int i = start; i < Math.min(rows.size(), start + ROWS_PER_PAGE); i++) {
                if (overClose(i - start, mouseX, mouseY)) {
                    CommandHubBlockEntity.Node n = be.getNodes().get(rows.get(i));
                    // Addressed by dimension and position, not row, so the click
                    // lands on the gadget the player actually pointed at.
                    sendText(be.getBlockPos(), "hub_unlink", n.dim + "@" + n.pos);
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float delta) {
        List<CommandHubBlockEntity.Node> nodes = be.getNodes();
        List<Integer> rows = visibleRows();
        int maxPage = maxPage();
        page = Math.min(page, maxPage);

        // Widget state follows the data rather than being rebuilt with it.
        sortButton.setMessage(Component.literal("Sort: " + SORT_LABELS[sortMode]));
        sortButton.active = !nodes.isEmpty();
        prevButton.active = page > 0;
        nextButton.active = page < maxPage;
        clearButton.setMessage(Component.literal(armed ? "Confirm — clear all" : "Disconnect all"));
        clearButton.active = !nodes.isEmpty();

        super.render(gfx, mouseX, mouseY, delta);
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
            gfx.drawString(font, "Nothing linked yet.", x, top + HEADER_TOP + 8, GRAY, false);
            gfx.drawString(font, "Take the Monitor Wand, click this hub,", x, top + HEADER_TOP + 22, GRAY, false);
            gfx.drawString(font, "then click your counters and monitors.", x, top + HEADER_TOP + 34, GRAY, false);
            gfx.drawString(font, "Sneak-click a linked one to unlink it.", x, top + HEADER_TOP + 46, GRAY, false);
            return;
        }

        gfx.drawString(font, "GADGET", x, top + HEADER_TOP, DIM, false);
        gfx.drawString(font, "READING", left + 150, top + HEADER_TOP, DIM, false);

        if (rows.isEmpty()) {
            gfx.drawString(font, "No gadget matches \"" + filter.trim() + "\".", x, top + ROW_TOP, GRAY, false);
            return;
        }

        int start = page * ROWS_PER_PAGE;
        for (int i = start; i < Math.min(rows.size(), start + ROWS_PER_PAGE); i++) {
            CommandHubBlockEntity.Node n = nodes.get(rows.get(i));
            int slot = i - start;
            int y = top + ROW_TOP + slot * ROW_H;
            if (slot % 2 == 0) {
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

            // Drawn where mouseClicked hit-tests, from the same row list.
            boolean hot = overClose(slot, mouseX, mouseY);
            gfx.fill(left + panelW - 26, y - 3, left + panelW - 26 + CLOSE_W, y - 3 + CLOSE_W,
                    hot ? 0xFF3A2020 : 0xFF20242C);
            gfx.renderOutline(left + panelW - 26, y - 3, CLOSE_W, CLOSE_W, hot ? RED : FRAME);
            gfx.drawString(font, "✕", left + panelW - 21, y + 1, hot ? RED : GRAY, false);
        }

        if (maxPage > 0) {
            gfx.drawString(font, (page + 1) + " / " + (maxPage + 1), left + 62, top + panelH - 21, GRAY, false);
        }
    }

    private static String trim(String s, int max) {
        return s.length() > max ? s.substring(0, max - 1) + "…" : s;
    }
}
