package com.jrpetty.aztecabyss.client;

import com.jrpetty.aztecabyss.network.RequisitionOrderPayload;
import com.jrpetty.aztecabyss.network.RequisitionPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The requisition slate, drawn instead of typed.
 *
 * <p>Ordering supplies meant reading a thirty-eight line catalogue that scrolled
 * past in chat and then typing {@code /maze order beetroot 3} from memory. That
 * is not a decision anybody makes well: you cannot weigh a golden apple against
 * four iron when only one of them is on screen, you cannot see what you have
 * left without asking, and the price list is gone by the time you have thought
 * about it. The evening choice is the spine of the whole supply system, and it
 * was being made blind.
 *
 * <h2>What the layout is for</h2>
 *
 * <p>A rail of eight groups down the left and that group's lines on the right,
 * because a flat list of thirty-eight rows does not fit on a Minecraft screen at
 * any sane scale and paging through one is worse than the chat sheet was. Eight
 * short lists you can flick between beats one long list you have to hunt in.
 *
 * <p>The budget bar across the top is the thing this screen exists for. It shows
 * committed against total in one bar, the bounty portion in gold at the far end,
 * and it moves the instant you add a line - so "can I afford the serum" is
 * answered by looking rather than by arithmetic.
 *
 * <h2>Nothing here decides anything</h2>
 *
 * <p>Clicks send a change, never a slate: {@code +1 iron}. The server applies it
 * against its own copy, re-checks the budget exactly as the command always did,
 * and answers with a fresh sheet that replaces this screen. A client that sends
 * a whole slate is a client that can be made to send any slate it likes, and a
 * client that predicts its own balance is one that will eventually show somebody
 * points they do not have.
 */
public class RequisitionScreen extends AbyssScreen {

    /** One catalogue line, unpacked. */
    private record Row(String group, String id, String display, int count, int cost,
                       int yours, int glade, String item) {
    }

    /** The actual items, for drawing. Cached so parsing happens once a row. */
    private final Map<String, net.minecraft.world.item.ItemStack> icons = new LinkedHashMap<>();

    private net.minecraft.world.item.ItemStack icon(Row r) {
        return icons.computeIfAbsent(r.id(), k -> new net.minecraft.world.item.ItemStack(
                net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                        net.minecraft.resources.ResourceLocation.parse(r.item()))));
    }

    private final int day;
    private final int pool;
    private final int spent;
    private final int heads;
    private final int fromWork;
    private final int fromBounty;
    private final int yourUnits;
    private final int yourQuota;
    private final int yourCredits;
    private final int maxCredits;
    private final String jobDisplay;
    private final String unitName;
    private final List<Row> rows = new ArrayList<>();
    private final List<String> groups = new ArrayList<>();
    private final Map<String, List<Row>> byGroup = new LinkedHashMap<>();

    private int tab;
    /** Which line the pointer is over, or -1. */
    private int hovered = -1;

    // Chrome, matched to the trade sheet so the two read as one interface.
    private static final int PANEL_FILL = UiKit.PANEL;
    private static final int PANEL_EDGE = UiKit.EDGE;
    private static final int ROW_FILL = 0xFF1C1B27;
    private static final int ROW_HOT = 0xFF272536;
    private static final int TEXT = UiKit.TEXT;
    private static final int TEXT_DIM = UiKit.TEXT_DIM;
    private static final int TEXT_FAINT = UiKit.TEXT_FAINT;
    private static final int ACCENT = 0xFFE0A040;
    private static final int GOLD = 0xFFFFC94A;
    private static final int RED = 0xFFD1495B;

    private static final int RAIL_W = 74;
    private static final int PANEL_W = 260;
    /**
     * Row height: eighteen where there is room, down to fourteen where there
     * is not.
     *
     * <p>The tallest group is eight lines, and eight rows, the crate strip,
     * the buttons and the footer have to fit without scrolling - a supply menu
     * you have to scroll is a supply menu people order the top four items
     * from. A fixed eighteen fitted a 360-high window and ran the buttons over
     * the crate strip at 270, which is what GUI scale "auto" gives a 1080p
     * screen.
     */
    private int rowH() {
        int rows = Math.max(1, Math.max(groups.size(), longestGroup()));
        int room = this.height - panelTop() - 4 - 26 - 6 - 20 - 14;
        return Math.max(14, Math.min(18, room / rows));
    }

    private int longestGroup() {
        int most = 0;
        for (List<Row> g : byGroup.values()) {
            most = Math.max(most, g.size());
        }
        return most;
    }

    public RequisitionScreen(RequisitionPayload payload, int openTab) {
        super(Component.literal("Requisition"));
        this.day = payload.day();
        this.pool = payload.pool();
        this.spent = payload.spent();
        this.heads = payload.heads();
        this.fromWork = payload.fromWork();
        this.fromBounty = payload.fromBounty();
        this.yourUnits = payload.yourUnits();
        this.yourQuota = payload.yourQuota();
        this.yourCredits = payload.yourCredits();
        this.maxCredits = payload.maxCredits();
        this.jobDisplay = payload.jobDisplay();
        this.unitName = payload.unitName();
        for (String packed : payload.rows()) {
            Row r = new Row(
                    RequisitionPayload.field(packed, 0),
                    RequisitionPayload.field(packed, 1),
                    RequisitionPayload.field(packed, 2),
                    RequisitionPayload.number(packed, 3),
                    RequisitionPayload.number(packed, 4),
                    RequisitionPayload.number(packed, 5),
                    RequisitionPayload.number(packed, 6),
                    RequisitionPayload.field(packed, 7));
            rows.add(r);
            byGroup.computeIfAbsent(r.group(), k -> new ArrayList<>()).add(r);
        }
        groups.addAll(byGroup.keySet());
        this.tab = Math.max(0, Math.min(openTab, Math.max(0, groups.size() - 1)));
    }

    /** So a re-send after a click can put you back on the tab you were reading. */
    public int currentTab() {
        return tab;
    }

    private List<Row> shown() {
        if (groups.isEmpty()) {
            return List.of();
        }
        return byGroup.getOrDefault(groups.get(tab), List.of());
    }

    private int left() {
        return (this.width - (RAIL_W + 6 + PANEL_W)) / 2;
    }

    /**
     * Straight under the budget lines. Centring it left a band of empty ground
     * between the pot it is spending and the list it is spent on.
     */
    private int panelTop() {
        return 86;
    }

    /** How far down the content runs, rail or lines, whichever is longer. */
    private int contentBottom() {
        return panelTop() + Math.max(groups.size(), shown().size()) * rowH();
    }

    // ------------------------------------------------------------------

    @Override
    protected boolean keepDesignLayout() {
        return true;
    }

    @Override
    protected void initWidgets() {
        int x = left();
        int top = panelTop();

        for (int i = 0; i < groups.size(); i++) {
            final int which = i;
            Button b = Button.builder(Component.literal(groups.get(i)), btn -> {
                        tab = which;
                        rebuild();
                    })
                    .bounds(x, top + i * rowH(), RAIL_W, rowH() - 2).build();
            b.active = i != tab;
            addRenderableWidget(b);
        }

        int px = x + RAIL_W + 6;
        List<Row> list = shown();
        for (int i = 0; i < list.size(); i++) {
            Row r = list.get(i);
            int ry = top + i * rowH();
            // Minus first, so the pair reads left to right in the order you would
            // use them: take one off, then put one on.
            Button minus = Button.builder(Component.literal("-"),
                            btn -> send(r.id(), -1))
                    .bounds(px + PANEL_W - 36, ry, 14, rowH() - 2).build();
            minus.active = r.yours() > 0;
            addRenderableWidget(minus);

            Button plus = Button.builder(Component.literal("+"),
                            btn -> send(r.id(), 1))
                    .bounds(px + PANEL_W - 18, ry, 14, rowH() - 2).build();
            plus.active = pool - spent >= r.cost();
            addRenderableWidget(plus);
        }

        int footY = Math.min(this.height - 24, contentBottom() + 34);
        Button clear = Button.builder(Component.literal("Take it all back"),
                        b -> send(RequisitionOrderPayload.CLEAR, 0))
                .bounds(x, footY, 120, 20).build();
        clear.active = spent > 0;
        addRenderableWidget(clear);
        addRenderableWidget(Button.builder(Component.literal("File it"), b -> onClose())
                .bounds(x + RAIL_W + 6 + PANEL_W - 120, footY, 120, 20).build());
    }

    private void rebuild() {
        this.clearWidgets();
        this.init();
    }

    private void send(String id, int delta) {
        // No optimistic update, and no refusal channel either: the plus is dead
        // when you cannot afford the line, so the only refusal the server can
        // reach is one the screen already made unclickable.
        PacketDistributor.sendToServer(new RequisitionOrderPayload(id, delta));
    }

    @Override
    protected int glow() {
        return UiKit.MAZE_GLOW;
    }

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int cx = this.width / 2;
        int x = left();
        int top = panelTop();
        int px = x + RAIL_W + 6;
        int left = pool - spent;

        // --- header -------------------------------------------------------
        g.drawCenteredString(this.font, "THE BOX — DAY " + (day + 1), cx, 8, TEXT_FAINT);
        UiKit.big(g, this.font, Component.literal("REQUISITION"), cx, 19, 1.6f, ACCENT);

        // --- the pool bar -------------------------------------------------
        // One pot for the whole Glade, so this bar is everybody's. The three
        // slices say where it came from, because "we are short because nobody
        // farmed" is the conversation this screen is meant to start.
        int barW = RAIL_W + 6 + PANEL_W;
        int barY = 42;
        int denom = Math.max(1, pool);
        int filled = (int) (barW * (Math.min(spent, pool) / (float) denom));
        int workW = (int) (barW * (Math.min(fromWork, pool) / (float) denom));
        int bountyW = (int) (barW * (Math.min(fromBounty, pool) / (float) denom));
        g.fill(x, barY, x + barW, barY + 7, 0xFF0A0910);
        // Earned slices sit at the far end, unfilled, so the part of today's pot
        // that somebody had to work or bleed for is visible even when it is
        // already spent.
        if (workW + bountyW > 0) {
            g.fill(x + barW - workW - bountyW, barY, x + barW - bountyW, barY + 7, 0xFF17301D);
        }
        if (bountyW > 0) {
            g.fill(x + barW - bountyW, barY, x + barW, barY + 7, 0xFF3A2E14);
        }
        g.fill(x, barY, x + filled, barY + 7, left <= 0 ? GOLD : ACCENT);

        g.drawString(this.font, Component.literal("§f" + spent + "§7 committed of " + pool),
                x, barY + 11, TEXT_DIM, true);
        String rightLabel = left + " left";
        int pulse = left > 0 && spent == 0
                ? UiKit.pulse(ACCENT, (float) (0.72 + 0.28 * Math.sin(age() / 7.0)))
                : left > 0 ? TEXT : TEXT_FAINT;
        g.drawString(this.font, rightLabel, x + barW - this.font.width(rightLabel), barY + 11, pulse, true);

        // Where the pot came from, in one line.
        String source = "§7" + heads + " head" + (heads == 1 ? "" : "s")
                + (fromWork > 0 ? "  §a+" + fromWork + " worked" : "")
                + (fromBounty > 0 ? "  §6+" + fromBounty + " bounty" : "");
        g.drawString(this.font, Component.literal(source),
                x + barW - this.font.width(source) - this.font.width(rightLabel) - 10,
                barY + 11, TEXT_FAINT, true);

        // --- your own day -------------------------------------------------
        // The pot is shared; this line is the only thing on screen that is
        // yours, and it is the one that says whether you have pulled your weight.
        if (yourQuota > 0) {
            int qBarW = 90;
            int qx = x + barW - qBarW;
            int qy = barY + 22;
            int qFill = (int) (qBarW * Math.min(1.0f, yourUnits / (float) yourQuota));
            g.fill(qx, qy, qx + qBarW, qy + 4, 0xFF0A0910);
            g.fill(qx, qy, qx + qFill, qy + 4,
                    yourUnits >= yourQuota ? 0xFF63D488 : 0xFF3E9E4E);
            String mine = strip(jobDisplay) + " — " + yourUnits + "/" + yourQuota
                    + " " + unitName + "  §a+" + yourCredits + "§7/" + maxCredits;
            g.drawString(this.font, Component.literal("§7" + mine),
                    x, qy - 1, TEXT_FAINT, true);
        }

        // --- the panel ----------------------------------------------------
        // The extra rows under the list are the crate strip: what YOU are
        // buying, drawn as the actual items, because a slate you can see is a
        // slate you trust.
        int stripTop = contentBottom() + 4;
        int panelBottom = stripTop + 26;
        UiKit.panel(g, px - 3, top - 4, PANEL_W + 6, panelBottom - (top - 4), PANEL_FILL, PANEL_EDGE);

        hovered = -1;
        List<Row> list = shown();
        for (int i = 0; i < list.size(); i++) {
            Row r = list.get(i);
            int ry = top + i * rowH();
            boolean hot = mouseX >= px && mouseX <= px + PANEL_W
                    && mouseY >= ry && mouseY <= ry + rowH() - 2;
            if (hot) {
                hovered = i;
            }
            g.fill(px, ry, px + PANEL_W, ry + rowH() - 2, hot ? ROW_HOT : ROW_FILL);
            if (r.glade() > 0) {
                // A gold spine on anything on the slate, so a filled order is
                // legible from the rail without reading a single number.
                g.fill(px, ry, px + 2, ry + rowH() - 2, GOLD);
            }

            // The item itself, drawn where a name used to stand alone - shrunk
            // to the row when the rows are short, so it never sits on the next.
            int size = Math.min(16, rowH() - 2);
            g.pose().pushPose();
            g.pose().translate(px + 4, ry + (rowH() - 2 - size) / 2.0f, 0);
            g.pose().scale(size / 16.0f, size / 16.0f, 1.0f);
            g.renderItem(icon(r), 0, 0);
            g.pose().popPose();

            String name = r.display();
            String bundle = r.count() > 0 ? " §7x" + r.count() : "";
            int ty = ry + (rowH() - 2 - 8) / 2;
            g.drawString(this.font, Component.literal("§r" + name + bundle),
                    px + 24, ty, r.glade() > 0 ? TEXT : TEXT_DIM, true);

            String price = r.cost() + "p";
            g.drawString(this.font, price, px + PANEL_W - 60 - this.font.width(price), ty,
                    r.cost() <= left ? TEXT_DIM : RED, true);

            if (r.glade() > 0) {
                String n = "x" + r.glade();
                g.drawString(this.font, n, px + PANEL_W - 52, ty, GOLD, true);
            }
        }

        // --- the crate strip ----------------------------------------------
        // Everything on YOUR slate, across every tab, as items with counts -
        // the crate you will actually be opening tomorrow morning.
        g.fill(px, stripTop - 1, px + PANEL_W, stripTop, PANEL_EDGE);
        int mineCost = 0;
        int ix = px + 4;
        boolean any = false;
        for (Row r : rows) {
            if (r.yours() <= 0) {
                continue;
            }
            any = true;
            mineCost += r.cost() * r.yours();
            if (ix < px + PANEL_W - 70) {
                g.renderItem(icon(r), ix, stripTop + 4);
                String n = String.valueOf(r.count() > 0 ? r.count() * r.yours() : r.yours());
                g.drawString(this.font, Component.literal(n),
                        ix + 17 - this.font.width(n), stripTop + 13, TEXT, true);
                ix += 20;
            }
        }
        if (any) {
            String total = "= " + mineCost + "p";
            g.drawString(this.font, total, px + PANEL_W - 6 - this.font.width(total), stripTop + 9, GOLD, true);
        } else {
            g.drawString(this.font, "Your crate is empty. Click + on anything above.",
                    px + 6, stripTop + 9, TEXT_FAINT, true);
        }
    }

    @Override
    protected void renderOverlay(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int cx = this.width / 2;
        int px = left() + RAIL_W + 6;
        int left = pool - spent;
        List<Row> list = shown();

        // The real tooltip of the real item, so "what even is this" is
        // answered the way the rest of the game answers it.
        if (hovered >= 0 && hovered < list.size()
                && mouseX >= px + 2 && mouseX <= px + 22) {
            g.renderTooltip(this.font, icon(list.get(hovered)), mouseX, mouseY);
        }

        // --- the footer line ----------------------------------------------
        // Drawn after the widgets so a refusal is never hidden behind a button.
        int footTextY = Math.min(this.height - 10, contentBottom() + 58);
        String foot;
        int colour = TEXT_FAINT;
        if (spent == 0) {
            foot = "§cNothing filed. The Box comes up empty tomorrow.";
            colour = RED;
        } else if (hovered >= 0 && hovered < list.size()) {
            Row r = list.get(hovered);
            if (r.yours() > 0 && r.yours() != r.glade()) {
                foot = "§7" + r.display() + " — §f" + r.glade() + "§7 on the Glade's slate, §f"
                        + r.yours() + "§7 of them yours";
                g.drawCenteredString(this.font, Component.literal(foot), cx, footTextY, TEXT_FAINT);
                return;
            }
            foot = "§7" + (r.count() > 0 ? r.count() + " × " : "") + r.display()
                    + " for " + r.cost() + ", and you have " + left;
        } else {
            foot = "§7No weapons, tools or armour — order the stock and make them.";
        }
        g.drawCenteredString(this.font, Component.literal(foot), cx, footTextY, colour);
    }

    /** Colour codes out. The trade names arrive with the server's colours on. */
    private static String strip(String s) {
        return UiKit.strip(s);
    }

    /** Shift-click a group name to jump; kept for the keyboard-minded. */
    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == 262 && tab < groups.size() - 1) {
            tab++;
            rebuild();
            return true;
        }
        if (key == 263 && tab > 0) {
            tab--;
            rebuild();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(null);
    }
}
