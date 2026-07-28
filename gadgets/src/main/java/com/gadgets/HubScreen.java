package com.gadgets;

import java.util.List;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

/** The Command Hub board: every linked counter and monitor, live, in one place. */
public class HubScreen extends GadgetScreen {
    private static final int ROWS_PER_PAGE = 11;

    private final CommandHubBlockEntity be;
    private int page = 0;
    /** Two-step guard so a stray click can never wipe the board. */
    private boolean armed = false;

    public HubScreen(CommandHubBlockEntity be) {
        super(Text.literal("Command Hub"), 300, 214);
        this.be = be;
    }

    @Override
    protected void init() {
        super.init();
        addDrawableChild(ButtonWidget.builder(Text.literal("◀"), b -> page = Math.max(0, page - 1))
                .dimensions(left + 12, top + 174, 20, 14).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("▶"), b -> page = Math.min(maxPage(), page + 1))
                .dimensions(left + panelW - 32, top + 174, 20, 14).build());
        // Clearing the whole board lives here rather than on the wand, where it
        // used to fire by accident on a sneak-click.
        addDrawableChild(ButtonWidget.builder(
                        Text.literal(armed ? "Confirm — clear all links" : "Reset links"), b -> {
                            if (armed) {
                                send(be.getPos(), "hub_clear", 0);
                                armed = false;
                            } else {
                                armed = true;
                            }
                            clearAndInit();
                        })
                .dimensions(left + 40, top + 192, panelW - 80, 14).build());
    }

    private int maxPage() {
        return Math.max(0, (be.getNodes().size() - 1) / ROWS_PER_PAGE);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        int x = left + 12;
        List<CommandHubBlockEntity.Node> nodes = be.getNodes();

        ctx.drawText(textRenderer, nodes.size() + "/" + CommandHubBlockEntity.MAX_NODES + " linked · "
                + ItemCounterBlockEntity.fmt(be.totalRateMin()) + " items/min · "
                + be.lowCount() + " low", x, top + 20, AMBER, false);

        if (nodes.isEmpty()) {
            ctx.drawText(textRenderer, "Nothing linked yet.", x, top + 44, GRAY, false);
            ctx.drawText(textRenderer, "Take the Monitor Wand, click this hub,", x, top + 58, GRAY, false);
            ctx.drawText(textRenderer, "then click your counters and monitors.", x, top + 70, GRAY, false);
            ctx.drawText(textRenderer, "Sneak-click a linked one to unlink it.", x, top + 82, GRAY, false);
            return;
        }

        page = Math.min(page, maxPage());
        int start = page * ROWS_PER_PAGE;
        for (int i = start; i < Math.min(nodes.size(), start + ROWS_PER_PAGE); i++) {
            CommandHubBlockEntity.Node n = nodes.get(i);
            int y = top + 36 + (i - start) * 12;
            BlockPos p = BlockPos.fromLong(n.pos);
            String where = p.getX() + "," + p.getY() + "," + p.getZ();
            if (!n.online) {
                ctx.drawText(textRenderer, "○ " + where + " — offline (unloaded)", x, y, GRAY, false);
            } else if (n.type == CommandHubBlockEntity.TYPE_COUNTER) {
                ctx.drawText(textRenderer, "● " + trim(n.label, 14) + "  "
                        + ItemCounterBlockEntity.compact(n.a) + "/m · "
                        + ItemCounterBlockEntity.compact(n.b) + "/h · "
                        + ItemCounterBlockEntity.compact(n.c) + " total", x, y, AMBER, false);
            } else {
                boolean low = n.c != 0;
                ctx.drawText(textRenderer, (low ? "▼ " : "● ") + trim(n.label, 14) + "  "
                        + ItemCounterBlockEntity.fmt(n.a)
                        + (low ? " LOW(<" + n.b + ")" : "")
                        + " · " + n.d + " types", x, y, low ? RED : GREEN, false);
            }
        }
        if (maxPage() > 0) {
            ctx.drawCenteredTextWithShadow(textRenderer, (page + 1) + " / " + (maxPage() + 1),
                    left + panelW / 2, top + 177, GRAY);
        }
    }

    private static String trim(String s, int max) {
        return s.length() > max ? s.substring(0, max - 1) + "…" : s;
    }
}
