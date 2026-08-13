package com.jrpetty.aztecabyss.client;

import com.jrpetty.aztecabyss.network.RequisitionOrderPayload;
import com.jrpetty.aztecabyss.network.RequisitionPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
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
public class RequisitionScreen extends Screen {

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
    private int age = 0;
    /** Which line the pointer is over, or -1. */
    private int hovered = -1;

    // Chrome, matched to the trade sheet so the two read as one interface.
    private static final int BG_TOP = 0xFF0B0A10;
    private static final int BG_BOTTOM = 0xFF060508;
    private static final int PANEL_FILL = 0xFF14131C;
    private static final int PANEL_EDGE = 0xFF2A2836;
    private static final int ROW_FILL = 0xFF191822;
    private static final int ROW_HOT = 0xFF232231;
    private static final int TEXT = 0xFFD8D5E4;
    private static final int TEXT_DIM = 0xFF7A7690;
    private static final int TEXT_FAINT = 0xFF4A4760;
    private static final int ACCENT = 0xFFE0A040;
    private static final int GOLD = 0xFFFFC94A;
    private static final int RED = 0xFFD1495B;

    private static final int RAIL_W = 74;
    private static final int PANEL_W = 260;
    /**
     * Sixteen, not twenty.
     *
     * <p>The tallest group is eight lines and the shortest useful screen is 240
     * tall. Eight rows, a header and a footer have to fit inside that without
     * scrolling, because a supply menu you have to scroll is a supply menu
     * people order the top four items from.
     */
    private static final int ROW_H = 18;

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

    private int panelTop() {
        return Math.max(78, this.height / 2 - 68);
    }

    /** How far down the content runs, rail or lines, whichever is longer. */
    private int contentBottom() {
        return panelTop() + Math.max(groups.size(), shown().size()) * ROW_H;
    }

    // ------------------------------------------------------------------

    @Override
    protected void init() {
        int x = left();
        int top = panelTop();

        for (int i = 0; i < groups.size(); i++) {
            final int which = i;
            Button b = Button.builder(Component.literal(groups.get(i)), btn -> {
                        tab = which;
                        rebuild();
                    })
                    .bounds(x, top + i * ROW_H, RAIL_W, ROW_H - 2).build();
            b.active = i != tab;
            addRenderableWidget(b);
        }

        int px = x + RAIL_W + 6;
        List<Row> list = shown();
        for (int i = 0; i < list.size(); i++) {
            Row r = list.get(i);
            int ry = top + i * ROW_H;
            // Minus first, so the pair reads left to right in the order you would
            // use them: take one off, then put one on.
            Button minus = Button.builder(Component.literal("-"),
                            btn -> send(r.id(), -1))
                    .bounds(px + PANEL_W - 36, ry, 14, ROW_H - 2).build();
            minus.active = r.yours() > 0;
            addRenderableWidget(minus);

            Button plus = Button.builder(Component.literal("+"),
                            btn -> send(r.id(), 1))
                    .bounds(px + PANEL_W - 18, ry, 14, ROW_H - 2).build();
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
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fillGradient(0, 0, this.width, this.height, BG_TOP, BG_BOTTOM);
    }

    /** No blur under type. Same call every screen in this mod makes. */
    @Override
    protected void renderBlurredBackground(float partialTick) {
        // Intentionally empty.
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        age++;
        this.renderBackground(g, mouseX, mouseY, partialTick);

        int cx = this.width / 2;
        int x = left();
        int top = panelTop();
        int px = x + RAIL_W + 6;
        int left = pool - spent;

        // --- header -------------------------------------------------------
        g.drawCenteredString(this.font, Component.literal("§8THE BOX — DAY " + day),
                cx, 10, TEXT_FAINT);
        g.pose().pushPose();
        g.pose().translate(cx, 19, 0);
        g.pose().scale(1.6f, 1.6f, 1.0f);
        g.drawCenteredString(this.font, Component.literal("REQUISITION"), 0, 0, ACCENT);
        g.pose().popPose();

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
        g.fill(x, barY, x + barW, barY + 7, 0xFF1A1926);
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

        g.drawString(this.font, Component.literal("§7" + spent + "§8 committed of " + pool),
                x, barY + 11, TEXT_DIM, false);
        String rightLabel = left + " left";
        int pulse = left > 0 && spent == 0
                ? 0xFF000000 | pulseRgb(ACCENT, (float) (0.72 + 0.28 * Math.sin(age / 7.0)))
                : left > 0 ? TEXT : TEXT_FAINT;
        g.drawString(this.font, Component.literal(rightLabel),
                x + barW - this.font.width(rightLabel), barY + 11, pulse, false);

        // Where the pot came from, in one line.
        String source = "§8" + heads + " head" + (heads == 1 ? "" : "s")
                + (fromWork > 0 ? "  §a+" + fromWork + " worked" : "")
                + (fromBounty > 0 ? "  §6+" + fromBounty + " bounty" : "");
        g.drawString(this.font, Component.literal(source),
                x + barW - this.font.width(source) - this.font.width(rightLabel) - 10,
                barY + 11, TEXT_FAINT, false);

        // --- your own day -------------------------------------------------
        // The pot is shared; this line is the only thing on screen that is
        // yours, and it is the one that says whether you have pulled your weight.
        if (yourQuota > 0) {
            int qBarW = 90;
            int qx = x + barW - qBarW;
            int qy = barY + 22;
            int qFill = (int) (qBarW * Math.min(1.0f, yourUnits / (float) yourQuota));
            g.fill(qx, qy, qx + qBarW, qy + 4, 0xFF1A1926);
            g.fill(qx, qy, qx + qFill, qy + 4,
                    yourUnits >= yourQuota ? 0xFF63D488 : 0xFF3E9E4E);
            String mine = strip(jobDisplay) + " — " + yourUnits + "/" + yourQuota
                    + " " + unitName + "  §a+" + yourCredits + "§8/" + maxCredits;
            g.drawString(this.font, Component.literal("§8" + mine),
                    x, qy - 1, TEXT_FAINT, false);
        }

        // --- the panel ----------------------------------------------------
        // The extra rows under the list are the crate strip: what YOU are
        // buying, drawn as the actual items, because a slate you can see is a
        // slate you trust.
        int stripTop = contentBottom() + 4;
        int panelBottom = stripTop + 26;
        g.fill(px - 3, top - 4, px + PANEL_W + 3, panelBottom, PANEL_FILL);
        g.fill(px - 3, top - 4, px + PANEL_W + 3, top - 3, PANEL_EDGE);
        g.fill(px - 3, panelBottom - 1, px + PANEL_W + 3, panelBottom, PANEL_EDGE);

        hovered = -1;
        List<Row> list = shown();
        for (int i = 0; i < list.size(); i++) {
            Row r = list.get(i);
            int ry = top + i * ROW_H;
            boolean hot = mouseX >= px && mouseX <= px + PANEL_W
                    && mouseY >= ry && mouseY <= ry + ROW_H - 2;
            if (hot) {
                hovered = i;
            }
            g.fill(px, ry, px + PANEL_W, ry + ROW_H - 2, hot ? ROW_HOT : ROW_FILL);
            if (r.glade() > 0) {
                // A gold spine on anything on the slate, so a filled order is
                // legible from the rail without reading a single number.
                g.fill(px, ry, px + 2, ry + ROW_H - 2, GOLD);
            }

            // The item itself, drawn where a name used to stand alone.
            g.renderItem(icon(r), px + 4, ry);

            String name = r.display();
            String bundle = r.count() > 0 ? " §8x" + r.count() : "";
            g.drawString(this.font, Component.literal("§r" + name + bundle),
                    px + 24, ry + 5, r.glade() > 0 ? TEXT : TEXT_DIM, false);

            String price = r.cost() + "p";
            g.drawString(this.font, Component.literal(price),
                    px + PANEL_W - 60 - this.font.width(price), ry + 5,
                    r.cost() <= left ? TEXT_DIM : RED, false);

            if (r.glade() > 0) {
                String n = "x" + r.glade();
                g.drawString(this.font, Component.literal(n),
                        px + PANEL_W - 52, ry + 5, GOLD, false);
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
            g.drawString(this.font, Component.literal(total),
                    px + PANEL_W - 6 - this.font.width(total), stripTop + 9, GOLD, false);
        } else {
            g.drawString(this.font, Component.literal(
                    "§8Your crate is empty. Click + on anything above."),
                    px + 6, stripTop + 9, TEXT_FAINT, false);
        }

        super.render(g, mouseX, mouseY, partialTick);

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
                foot = "§8" + r.display() + " — §f" + r.glade() + "§8 on the Glade's slate, §f"
                        + r.yours() + "§8 of them yours";
                g.drawCenteredString(this.font, Component.literal(foot), cx, footTextY, TEXT_FAINT);
                return;
            }
            foot = "§8" + (r.count() > 0 ? r.count() + " × " : "") + r.display()
                    + " for " + r.cost() + ", and you have " + left;
        } else {
            foot = "§8No weapons, tools or armour — order the stock and make them.";
        }
        g.drawCenteredString(this.font, Component.literal(foot), cx, footTextY, colour);
    }

    /** Colour codes out. The trade names arrive with the server's colours on. */
    private static String strip(String s) {
        return s == null ? "" : s.replaceAll("§.", "");
    }

    /** Lerps a colour toward black, for the pulse. Kept off the alpha channel. */
    private static int pulseRgb(int argb, float k) {
        int r = (int) (((argb >> 16) & 0xFF) * k);
        int gr = (int) (((argb >> 8) & 0xFF) * k);
        int b = (int) ((argb & 0xFF) * k);
        return (r << 16) | (gr << 8) | b;
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
