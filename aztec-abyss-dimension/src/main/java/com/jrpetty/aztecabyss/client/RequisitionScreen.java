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
    private record Row(String group, String id, String display, int count, int cost, int ordered) {
    }

    private final int day;
    private final int budget;
    private final int bonusPart;
    private final int spent;
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
    private static final int ROW_H = 16;

    public RequisitionScreen(RequisitionPayload payload, int openTab) {
        super(Component.literal("Requisition"));
        this.day = payload.day();
        this.budget = payload.budget();
        this.bonusPart = payload.bonus();
        this.spent = payload.spent();
        for (String packed : payload.rows()) {
            Row r = new Row(
                    RequisitionPayload.field(packed, 0),
                    RequisitionPayload.field(packed, 1),
                    RequisitionPayload.field(packed, 2),
                    RequisitionPayload.number(packed, 3),
                    RequisitionPayload.number(packed, 4),
                    RequisitionPayload.number(packed, 5));
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
        return Math.max(70, this.height / 2 - 72);
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
            minus.active = r.ordered() > 0;
            addRenderableWidget(minus);

            Button plus = Button.builder(Component.literal("+"),
                            btn -> send(r.id(), 1))
                    .bounds(px + PANEL_W - 18, ry, 14, ROW_H - 2).build();
            plus.active = budget - spent >= r.cost();
            addRenderableWidget(plus);
        }

        int footY = Math.min(this.height - 24, contentBottom() + 16);
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
        int left = budget - spent;

        // --- header -------------------------------------------------------
        g.drawCenteredString(this.font, Component.literal("§8THE BOX — DAY " + day),
                cx, 12, TEXT_FAINT);
        g.pose().pushPose();
        g.pose().translate(cx, 22, 0);
        g.pose().scale(1.6f, 1.6f, 1.0f);
        g.drawCenteredString(this.font, Component.literal("REQUISITION"), 0, 0, ACCENT);
        g.pose().popPose();

        // --- the budget bar -----------------------------------------------
        // The whole screen exists so that "can I afford this" is a look rather
        // than a sum, and this bar is the answer to it.
        int barW = RAIL_W + 6 + PANEL_W;
        int barY = 48;
        int denom = Math.max(1, budget);
        int filled = (int) (barW * (Math.min(spent, budget) / (float) denom));
        int bonusW = (int) (barW * (Math.min(bonusPart, budget) / (float) denom));
        g.fill(x, barY, x + barW, barY + 7, 0xFF1A1926);
        // The bounty slice sits at the far end, unfilled, so you can see the part
        // of today's budget you had to kill something for.
        if (bonusW > 0) {
            g.fill(x + barW - bonusW, barY, x + barW, barY + 7, 0xFF3A2E14);
        }
        g.fill(x, barY, x + filled, barY + 7, left <= 0 ? GOLD : ACCENT);

        g.drawString(this.font, Component.literal("§7" + spent + "§8 committed"),
                x, barY + 11, TEXT_DIM, false);
        String rightLabel = left + " left";
        int pulse = left > 0 && spent == 0
                ? 0xFF000000 | pulseRgb(ACCENT, (float) (0.72 + 0.28 * Math.sin(age / 7.0)))
                : left > 0 ? TEXT : TEXT_FAINT;
        g.drawString(this.font, Component.literal(rightLabel),
                x + barW - this.font.width(rightLabel), barY + 11, pulse, false);
        if (bonusPart > 0) {
            String b = "◆ " + bonusPart + " bounty";
            g.drawString(this.font, Component.literal(b),
                    x + barW - this.font.width(b) - this.font.width(rightLabel) - 10,
                    barY + 11, GOLD, false);
        }

        // --- the panel ----------------------------------------------------
        int panelBottom = contentBottom() + 4;
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
            if (r.ordered() > 0) {
                // A gold spine on anything on the slate, so a filled order is
                // legible from the rail without reading a single number.
                g.fill(px, ry, px + 2, ry + ROW_H - 2, GOLD);
            }

            String name = r.display();
            String bundle = r.count() > 0 ? " §8x" + r.count() : "";
            g.drawString(this.font, Component.literal("§r" + name + bundle),
                    px + 8, ry + 4, r.ordered() > 0 ? TEXT : TEXT_DIM, false);

            String price = r.cost() + "p";
            g.drawString(this.font, Component.literal(price),
                    px + PANEL_W - 60 - this.font.width(price), ry + 4,
                    r.cost() <= left ? TEXT_DIM : RED, false);

            if (r.ordered() > 0) {
                String n = "x" + r.ordered();
                g.drawString(this.font, Component.literal(n),
                        px + PANEL_W - 52, ry + 4, GOLD, false);
            }
        }

        super.render(g, mouseX, mouseY, partialTick);

        // --- the footer line ----------------------------------------------
        // Drawn after the widgets so a refusal is never hidden behind a button.
        int footTextY = Math.min(this.height - 36, contentBottom() + 6);
        String foot;
        int colour = TEXT_FAINT;
        if (spent == 0) {
            foot = "§cNothing filed. The Box comes up empty tomorrow.";
            colour = RED;
        } else if (hovered >= 0 && hovered < list.size()) {
            Row r = list.get(hovered);
            foot = "§8" + (r.count() > 0 ? r.count() + " × " : "") + r.display()
                    + " for " + r.cost() + ", and you have " + left;
        } else {
            foot = "§8No weapons, tools or armour — order the stock and make them.";
        }
        g.drawCenteredString(this.font, Component.literal(foot), cx, footTextY, colour);
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
