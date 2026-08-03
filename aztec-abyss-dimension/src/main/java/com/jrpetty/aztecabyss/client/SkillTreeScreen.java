package com.jrpetty.aztecabyss.client;

import com.jrpetty.aztecabyss.network.SkillLearnPayload;
import com.jrpetty.aztecabyss.network.SkillTreePayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * The trade sheet, drawn instead of printed.
 *
 * <p>Skills lived in chat. Twelve lines of grey text scrolling away above a
 * hotbar is not a place anybody makes a decision - you cannot compare two
 * options that are not on screen together, you cannot see what a rank costs
 * against what you have, and the whole thing is gone the moment somebody else
 * says anything. A progression system nobody can look at properly is a
 * progression system nobody engages with.
 *
 * <h2>What the layout is for</h2>
 *
 * <p>Three columns, one per skill, side by side and full height. That is the
 * entire point: a trade has exactly three choices and they should be visible
 * <em>at the same time</em>, because the decision is a comparison and a
 * comparison needs both things in view.
 *
 * <p>Each column is a ladder read bottom to top - three rungs, filled as far as
 * you have bought. The rung you are about to buy is lit and priced; the ones
 * above it are dim but legible, so you can see where a column <em>goes</em>
 * before you commit a point to it. Knowing that Stride ends at Second Wind is
 * the information that makes the first rank worth buying.
 *
 * <h2>Nothing here decides anything</h2>
 *
 * <p>Clicks send the skill's name and the server checks the trade, the ceiling
 * and the balance exactly as the command always did. The screen re-asks for the
 * sheet afterwards rather than predicting it, so what you are looking at is
 * always what the server actually thinks - a client that guesses is a client
 * that eventually shows somebody a rank they do not have.
 */
public class SkillTreeScreen extends Screen {

    /** One skill, unpacked. */
    private record Row(String id, String display, int rank, int max, String[] ranks) {
    }

    private final String jobDisplay;
    private final int available;
    private final int have;
    private final int need;
    private final List<Row> rows = new ArrayList<>();

    /** Which column the pointer is over, or -1. */
    private int hovered = -1;
    /** Ticks since opening, for the one thing on screen that moves. */
    private int age = 0;
    private boolean confirmingForget = false;

    // Chrome.
    private static final int CARD_W = 132;
    private static final int CARD_GAP = 10;
    private static final int BG_TOP = 0xFF0B0A10;
    private static final int BG_BOTTOM = 0xFF060508;
    private static final int CARD_FILL = 0xFF14131C;
    private static final int CARD_EDGE = 0xFF2A2836;
    private static final int CARD_EDGE_HOT = 0xFF6B6788;
    private static final int TEXT = 0xFFD8D5E4;
    private static final int TEXT_DIM = 0xFF7A7690;
    private static final int TEXT_FAINT = 0xFF4A4760;

    public SkillTreeScreen(SkillTreePayload payload) {
        super(Component.literal("Trade"));
        this.jobDisplay = payload.display();
        this.available = payload.available();
        this.have = payload.have();
        this.need = Math.max(1, payload.need());
        for (String packed : payload.skills()) {
            rows.add(new Row(
                    SkillTreePayload.field(packed, 0),
                    SkillTreePayload.field(packed, 1),
                    SkillTreePayload.number(packed, 2),
                    Math.max(1, SkillTreePayload.number(packed, 3)),
                    new String[]{
                            SkillTreePayload.field(packed, 4),
                            SkillTreePayload.field(packed, 5),
                            SkillTreePayload.field(packed, 6)}));
        }
    }

    /**
     * The accent colour of the trade, pulled off the display name's colour code.
     *
     * <p>The server already decided what colour a Runner is and wrote it into the
     * name; re-deciding it here would be two sources of truth for one fact, and
     * they would drift the first time somebody recoloured a job.
     */
    private int accent() {
        String s = jobDisplay;
        int i = s.indexOf('§');
        char code = i >= 0 && i + 1 < s.length() ? s.charAt(i + 1) : 'f';
        return switch (code) {
            case 'b' -> 0xFF4FD4E4;
            case '6' -> 0xFFE0A040;
            case 'a' -> 0xFF63D488;
            case '2' -> 0xFF3E9E4E;
            default -> 0xFFBFBBD0;
        };
    }

    private static String strip(String s) {
        return s == null ? "" : s.replaceAll("§.", "");
    }

    // ------------------------------------------------------------------

    @Override
    protected void init() {
        int total = rows.size() * CARD_W + Math.max(0, rows.size() - 1) * CARD_GAP;
        int left = (this.width - total) / 2;
        int cardTop = this.height / 2 - 96;
        int cardH = 192;

        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            int x = left + i * (CARD_W + CARD_GAP);
            boolean maxed = r.rank() >= r.max();
            boolean afford = available > 0;
            Button b = Button.builder(
                            Component.literal(maxed ? "Mastered" : afford ? "Learn  ▸" : "No points"),
                            btn -> learn(r.id()))
                    .bounds(x + 10, cardTop + cardH - 28, CARD_W - 20, 20)
                    .build();
            b.active = !maxed && afford;
            addRenderableWidget(b);
        }

        addRenderableWidget(Button.builder(
                        Component.literal(confirmingForget ? "Sure? Click again" : "Unlearn this trade"),
                        b -> {
                            if (confirmingForget) {
                                learn(SkillLearnPayload.FORGET);
                            } else {
                                confirmingForget = true;
                                rebuild();
                            }
                        })
                .bounds(this.width / 2 - 170, this.height - 30, 150, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose())
                .bounds(this.width / 2 + 20, this.height - 30, 150, 20).build());
    }

    private void rebuild() {
        this.clearWidgets();
        this.init();
    }

    private void learn(String id) {
        PacketDistributor.sendToServer(new SkillLearnPayload(id));
        // The server answers with a fresh sheet, which replaces this screen. No
        // optimistic update: a client that guesses its own progression is a
        // client that will eventually show somebody a rank they did not buy.
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

        int accent = accent();
        int cx = this.width / 2;

        // --- header -------------------------------------------------------
        g.drawCenteredString(this.font, Component.literal("§8YOUR TRADE"), cx, 18, TEXT_FAINT);
        g.pose().pushPose();
        g.pose().translate(cx, 30, 0);
        g.pose().scale(2.0f, 2.0f, 1.0f);
        g.drawCenteredString(this.font, Component.literal(strip(jobDisplay).toUpperCase(java.util.Locale.ROOT)),
                0, 0, accent);
        g.pose().popPose();

        // A hairline under the title, in the trade's colour. Cheap, and it makes
        // the header read as a masthead rather than as floating text.
        g.fill(cx - 120, 52, cx + 120, 53, (accent & 0x00FFFFFF) | 0x50000000);

        // --- the points meter ---------------------------------------------
        int barW = 240;
        int barX = cx - barW / 2;
        int barY = 64;
        int into = have % need;
        int filled = (int) (barW * (into / (float) need));
        g.fill(barX, barY, barX + barW, barY + 5, 0xFF1A1926);
        g.fill(barX, barY, barX + filled, barY + 5, accent);
        g.drawString(this.font, Component.literal("§8" + into + "/" + need + " to the next point"),
                barX, barY + 9, TEXT_FAINT, false);

        String spare = available + (available == 1 ? " point" : " points");
        // The only thing on screen that moves, and only when it matters: unspent
        // points breathe. A number you have to remember to look at is a number
        // that sits unspent for a week.
        int pulse = available > 0
                ? 0xFF000000 | pulseRgb(accent, (float) (0.72 + 0.28 * Math.sin(age / 7.0)))
                : TEXT_FAINT;
        g.drawString(this.font, Component.literal(available > 0 ? "◆ " + spare : "no points spare"),
                barX + barW - this.font.width(available > 0 ? "◆ " + spare : "no points spare"),
                barY + 9, pulse, false);

        // --- columns ------------------------------------------------------
        int total = rows.size() * CARD_W + Math.max(0, rows.size() - 1) * CARD_GAP;
        int left = (this.width - total) / 2;
        int cardTop = this.height / 2 - 96;
        int cardH = 192;
        hovered = -1;

        for (int i = 0; i < rows.size(); i++) {
            int x = left + i * (CARD_W + CARD_GAP);
            boolean hot = mouseX >= x && mouseX <= x + CARD_W
                    && mouseY >= cardTop && mouseY <= cardTop + cardH;
            if (hot) {
                hovered = i;
            }
            drawCard(g, rows.get(i), x, cardTop, cardH, accent, hot);
        }

        // --- the footer line ----------------------------------------------
        String foot = hovered >= 0
                ? "§8" + strip(rows.get(hovered).display()) + " — every rank of it"
                : "§8Nothing here makes you stronger. It makes you better at your job.";
        g.drawCenteredString(this.font, Component.literal(foot), cx, this.height - 46, TEXT_FAINT);

        super.render(g, mouseX, mouseY, partialTick);
    }

    /** Lerps a colour toward black, for the pulse. Kept off the alpha channel. */
    private static int pulseRgb(int argb, float k) {
        int r = (int) (((argb >> 16) & 0xFF) * k);
        int gr = (int) (((argb >> 8) & 0xFF) * k);
        int b = (int) ((argb & 0xFF) * k);
        return (r << 16) | (gr << 8) | b;
    }

    /**
     * One column.
     *
     * <p>Drawn as a ladder rather than a list. The rungs are read bottom to top
     * and the next one is lit, so the shape of the card answers "what do I get
     * now" and "where does this go" in the same glance.
     */
    private void drawCard(GuiGraphics g, Row r, int x, int y, int h, int accent, boolean hot) {
        g.fill(x, y, x + CARD_W, y + h, CARD_FILL);
        int edge = hot ? CARD_EDGE_HOT : CARD_EDGE;
        g.fill(x, y, x + CARD_W, y + 1, edge);
        g.fill(x, y + h - 1, x + CARD_W, y + h, edge);
        g.fill(x, y, x + 1, y + h, edge);
        g.fill(x + CARD_W - 1, y, x + CARD_W, y + h, edge);
        // A cap in the trade's colour, filled to however far this column is bought.
        int capW = (int) ((CARD_W - 2) * (r.rank() / (float) r.max()));
        g.fill(x + 1, y + 1, x + 1 + capW, y + 4, accent);

        int cx = x + CARD_W / 2;
        g.drawCenteredString(this.font, Component.literal(strip(r.display())), cx, y + 12,
                r.rank() > 0 ? TEXT : TEXT_DIM);

        // Rank pips.
        int pipY = y + 26;
        int pipGap = 14;
        int pipsX = cx - ((r.max() - 1) * pipGap) / 2;
        for (int i = 0; i < r.max(); i++) {
            int px = pipsX + i * pipGap;
            boolean owned = i < r.rank();
            boolean next = i == r.rank();
            g.fill(px - 4, pipY, px + 4, pipY + 8, owned ? accent
                    : next ? 0xFF3A3750 : 0xFF201F2C);
            if (next && !owned) {
                g.fill(px - 2, pipY + 2, px + 2, pipY + 6, 0xFF56536E);
            }
        }

        // The ladder, bottom to top: what you have, then what is next, then the rest.
        int textY = y + 46;
        for (int i = 0; i < r.max(); i++) {
            boolean owned = i < r.rank();
            boolean next = i == r.rank();
            int colour = owned ? TEXT : next ? 0xFFBFBBD0 : TEXT_FAINT;
            String prefix = owned ? "✔ " : next ? "▸ " : "· ";
            List<net.minecraft.util.FormattedCharSequence> lines =
                    this.font.split(Component.literal(prefix + strip(r.ranks()[i])), CARD_W - 16);
            for (net.minecraft.util.FormattedCharSequence line : lines) {
                g.drawString(this.font, line, x + 8, textY, colour, false);
                textY += 10;
            }
            textY += 3;
        }
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(null);
    }
}
