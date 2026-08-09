package com.jrpetty.mobtrumps.client;

import com.jrpetty.mobtrumps.BlackjackActionPayload;
import com.jrpetty.mobtrumps.BlackjackSyncPayload;
import com.jrpetty.mobtrumps.game.Blackjack;
import com.jrpetty.mobtrumps.game.MobCard;
import com.jrpetty.mobtrumps.game.MobCards;
import com.jrpetty.mobtrumps.game.Stat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The Twenty-One table.
 *
 * <p>The cards you were dealt are the point of the screen, so they are drawn as
 * cards rather than listed as text — and the layout is solved from the space
 * actually available rather than assumed. Hard-coded card sizes fitted a large
 * window and ran the buttons straight off the bottom of a small one; a hand can
 * now be twelve cards long, so the size has to give instead.
 *
 * <p>The dealer turns its hand over one card at a time. It knows the whole hand
 * the moment you stand, but watching it arrive a card at a time is the tense
 * part, and dumping five cards on screen at once threw the result away.
 *
 * <p>No odds are shown. Working out that Farmable climbs fast and Attack is
 * often nothing is the game; a table that prints the answer beside every button
 * is a calculator with a felt background.
 */
public class BlackjackScreen extends Screen {

    private static final int FELT_1 = 0xFF0E3B2C;
    private static final int FELT_0 = 0xFF04170F;
    private static final int GOLD = 0xFFE9C46A;
    private static final int GOLD_DIM = 0xFF8A6A2A;
    private static final int INK = 0xFFF2ECDD;
    private static final int DIM = 0xFF7E9A8B;
    private static final int WIN = 0xFF6BE87A;
    private static final int LOSE = 0xFFF0625A;
    private static final int EDGE = 0xFF1B4536;

    /** How long one card takes to turn over. */
    private static final long FLIP_MS = 190L;
    /** Gap between the dealer's cards as it plays its hand out. */
    private static final long DEAL_STEP_MS = 330L;
    private static final int CAPTION_H = 10;

    private final Map<String, LivingEntity> entityCache = new HashMap<>();
    private final int[][] statRects = new int[Stat.values().length][];
    private final int[][] stakeRects =
            new int[Blackjack.STAKES.length][];
    private int[] dealRect;
    private int[] standRect;
    private int hovered = -1;
    /** Dealer cards already turned, so a new one can be announced with a sound. */
    private int announced;

    // solved each frame from the window
    /**
     * Largest a dealt card may be drawn. The old ceiling was 0.24, which is
     * 41x57 pixels — small enough that the art was a smudge and the numbers
     * were nothing at all, with the rest of the table sitting empty.
     */
    private static final float CARD_SCALE_MAX = 0.52f;

    private float cardScale;
    private int cardW;
    private int cardH;
    private int rowH;
    /** Stat buttons per row: 1 tall column when there is room, 2 or 3 when not. */
    private int statCols = 1;
    private int statRows = 6;
    private boolean compact;

    public BlackjackScreen() {
        super(Component.literal("Twenty-One"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /**
     * Fit the whole screen into the window, biggest cards first.
     *
     * <p>Everything gives in turn: the cards shrink, then the stat buttons wrap
     * into two or three columns, then the header loses its subtitle. Solving it
     * this way rather than hard-coding sizes is the difference between a table
     * that works at every GUI scale and one that hides its own buttons below the
     * bottom of the screen — which the first version did on anything under about
     * 320 pixels, meaning GUI scale 3 and 4 on an ordinary display.
     *
     * <p>The row count is derived from the size being tried, not fixed up front:
     * smaller cards fit more per row, so shrinking them reduces the rows as well
     * as their height. Computing rows first and then picking a scale meant a long
     * hand could never be made to fit however small the cards went.
     *
     * @param cards how many cards the longer of the two hands holds
     * @param half  the width one hand has to lay out in
     * @return the y the card area starts at
     */
    private int solveLayout(int cards, int half) {
        // The search below is ~700 arrangements and its answer depends on
        // nothing but the window, so it runs when the window changes rather
        // than sixty times a second. It also used to allocate a two-element
        // array in its innermost loop, which at 60fps is some twenty thousand
        // of them a second to say "false, then true".
        if (solvedW == width && solvedH == height && solvedHalf == half && solvedCards == cards) {
            return solvedTop;
        }
        solvedW = width;
        solvedH = height;
        solvedHalf = half;
        solvedCards = cards;
        solvedTop = searchLayout(cards, half);
        return solvedTop;
    }

    /** Cached answer of the arrangement search, and the window it was for. */
    private int solvedW = -1;
    private int solvedH = -1;
    private int solvedHalf = -1;
    private int solvedCards = -1;
    private int solvedTop;

    /** Header can be roomy or tight; hoisted so the search allocates nothing. */
    private static final boolean[] TIGHTNESS = {false, true};

    private int searchLayout(int cards, int half) {
        int stats = Stat.values().length;
        // SCALE IS THE OUTER LOOP, descending, so the biggest cards that can be
        // made to fit win. It used to be the innermost loop under a one-column
        // stat list, which meant the layout would rather shrink the cards than
        // put the stat list in two columns — and the cap was 0.24, so a card
        // could never exceed 41x57 however much empty table was going spare.
        // On a 640x360 window that left half the screen bare above tiny cards.
        for (float scale = CARD_SCALE_MAX; scale >= 0.10f; scale -= 0.01f) {
            int cw = Math.round(CardRenderer.CARD_W * scale);
            int ch = Math.round(CardRenderer.CARD_H * scale);
            int perRow = Math.max(1, (half + 4) / (cw + 4));
            int handRows = Math.max(1, (Math.max(1, cards) + perRow - 1) / perRow);
            for (int cols = 1; cols <= 3; cols++) {
                for (int rh = 15; rh >= 12; rh--) {
                    for (boolean tight : TIGHTNESS) {
                        int headH = tight ? 26 : 42;
                        int sRows = (stats + cols - 1) / cols;
                        int total = headH + 24 + 6 + handRows * (ch + CAPTION_H + 4)
                                + 14 + sRows * rh + 6 + 15 + 14;
                        if (total <= height) {
                            cardScale = scale;
                            cardW = cw;
                            cardH = ch;
                            rowH = rh;
                            statCols = cols;
                            statRows = sRows;
                            compact = tight;
                            return headH + 24 + 6;
                        }
                    }
                }
            }
        }
        // a window too small for any arrangement: smallest of everything and
        // let the card area clip rather than push the buttons off-screen
        cardScale = 0.10f;
        cardW = Math.round(CardRenderer.CARD_W * cardScale);
        cardH = Math.round(CardRenderer.CARD_H * cardScale);
        rowH = 12;
        statCols = 3;
        statRows = 2;
        compact = true;
        return 26 + 24 + 6;
    }

    /** How many of the dealer's cards have been turned over so far. */
    private int dealerRevealed(long since) {
        int total = ClientBlackjack.dealerDraws().size();
        if (ClientBlackjack.phase() == BlackjackSyncPayload.PHASE_PLAYER) {
            return 0;
        }
        return Mth.clamp((int) (since / DEAL_STEP_MS) + 1, 0, total);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.fillGradient(0, 0, width, height, FELT_1, FELT_0);
        g.fill(0, 0, width, 2, GOLD_DIM);
        g.fill(0, height - 2, width, height, GOLD_DIM);

        int phase = ClientBlackjack.phase();
        boolean live = phase == BlackjackSyncPayload.PHASE_PLAYER;
        long since = System.currentTimeMillis() - ClientBlackjack.changedAt();

        int half = Math.min(310, (width - 34) / 2);
        int leftX = width / 2 - half - 5;
        int rightX = width / 2 + 5;

        List<ClientBlackjack.Draw> mine = ClientBlackjack.playerDraws();
        List<ClientBlackjack.Draw> theirs = ClientBlackjack.dealerDraws();
        int shown = dealerRevealed(since);

        // announce each dealer card as it lands
        if (shown > announced) {
            announced = shown;
            if (minecraft != null && shown > 0) {
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(
                        SoundEvents.BOOK_PAGE_TURN, 1.0f + shown * 0.05f));
            }
        }
        if (live) {
            announced = 0;
        }

        // Solved for a FULL hand, not the current one. Sizing to the cards on
        // the table meant the whole screen re-laid itself out the moment a
        // fourth card arrived — stat list into two columns, header shrinking,
        // everything moving under the cursor mid-hand. A layout that can hold
        // the most cards the rules allow never has to change.
        int cardsTop = solveLayout(com.jrpetty.mobtrumps.game.Blackjack.MAX_DRAWS, half);
        int perRow = Math.max(1, (half + 4) / (cardW + 4));
        int rows = Math.max(rowsFor(mine.size(), perRow), rowsFor(shown, perRow));

        // --- header ---------------------------------------------------------
        var pose = g.pose();
        pose.pushPose();
        pose.translate(width / 2f, compact ? 4f : 8f, 0);
        float ts = compact ? 1.2f : 1.6f;
        pose.scale(ts, ts, 1f);
        String head = "TWENTY-ONE";
        g.drawString(font, head, -font.width(head) / 2, 0, GOLD, true);
        pose.popPose();
        if (!compact) {
            g.drawCenteredString(font, "call a stat, then the card turns", width / 2, 28, DIM);
        }

        // --- totals, counting up as cards land ------------------------------
        int totalsY = compact ? 20 : 40;
        int myShown = countUp(ClientBlackjack.playerTotal(), mine, mine.size(), since, true);
        drawTotal(g, leftX, totalsY, half, "YOU", myShown,
                ClientBlackjack.playerTotal() > Blackjack.TARGET && since > FLIP_MS);
        int theirShown = shown == 0 ? -1
                : theirs.get(Math.min(shown, theirs.size()) - 1).total();
        drawTotal(g, rightX, totalsY, half, "DEALER", theirShown,
                theirShown > Blackjack.TARGET);

        // --- the two hands ---------------------------------------------------
        drawHand(g, leftX, cardsTop, perRow, mine, mine.size(), mouseX, mouseY, since, true);
        if (shown > 0) {
            drawHand(g, rightX, cardsTop, perRow, theirs, shown, mouseX, mouseY, since, false);
        } else if (live) {
            g.drawString(font, "waiting…", rightX + 4, cardsTop + 4, EDGE, false);
        }

        int cardsBottom = cardsTop + Math.max(1, rows) * (cardH + CAPTION_H + 4);

        // --- the six calls ----------------------------------------------------
        final int listW = Math.min(240, width - 20);
        int listX0 = width / 2 - listW / 2;
        int listX1 = listX0 + listW;
        int colW = (listW - (statCols - 1) * 4) / statCols;
        int listY = Math.max(cardsBottom + 14, height - 20 - statRows * rowH - 21);
        g.drawString(font, live ? "CALL A STAT" : "PRESS DEAL TO PLAY A HAND",
                listX0, listY - 11, GOLD, false);
        hovered = -1;
        for (Stat stat : Stat.values()) {
            int i = stat.ordinal();
            int cx = listX0 + (i % statCols) * (colW + 4);
            int y = listY + (i / statCols) * rowH;
            boolean over = mouseX >= cx && mouseX < cx + colW
                    && mouseY >= y && mouseY < y + rowH - 1;
            if (over && live) {
                hovered = i;
            }
            statRects[i] = new int[]{cx, y, colW, rowH - 1};
            g.fill(cx, y, cx + colW, y + rowH - 1,
                    !live ? 0x18000000 : over ? 0x3AE9C46A : 0x22000000);
            if (live && over) {
                g.renderOutline(cx, y, colW, rowH - 1, GOLD);
            }
            String label = stat.label.toUpperCase(Locale.ROOT);
            // the number that also calls it, so the keyboard shortcut is discoverable
            String key = String.valueOf(i + 1);
            g.drawString(font, key, cx + 4, y + 2, live ? GOLD_DIM : 0xFF3E5A4C, false);
            g.drawString(font, fit(label, colW - 20), cx + 13, y + 2,
                    live ? INK : 0xFF4E6A5C, false);
        }

        // --- buttons -----------------------------------------------------------
        int by = listY + statRows * rowH + 6;
        if (live) {
            standRect = new int[]{listX1 - 78, by, 78, 15};
            dealRect = null;
            button(g, standRect, "STAND", mouseX, mouseY, true);
        } else {
            standRect = null;
            // the wager, then DEAL. Chips rather than a typed number: the odds do
            // not change with the size of the bet, only the swing, so the choice
            // is how big a swing you want and six rungs say that clearly.
            int chips = Blackjack.STAKES.length;
            int dealW = 62;
            int chipW = Math.max(16, (listW - dealW - 6 - (chips - 1) * 2) / chips);
            for (int i = 0; i < chips; i++) {
                int cx = listX0 + i * (chipW + 2);
                stakeRects[i] = new int[]{cx, by, chipW, 15};
                int amount = Blackjack.STAKES[i];
                boolean picked = i == ClientBlackjack.stakeIndex();
                boolean afford = ClientBlackjack.fragments() >= amount;
                boolean over = mouseX >= cx && mouseX < cx + chipW
                        && mouseY >= by && mouseY < by + 15;
                g.fill(cx, by, cx + chipW, by + 15,
                        picked ? 0xFF6A5320 : !afford ? 0x18000000
                                : over ? 0x3AE9C46A : 0x22000000);
                g.renderOutline(cx, by, chipW, 15,
                        picked ? GOLD : afford ? EDGE : 0xFF2A3A32);
                String label = String.valueOf(amount);
                g.drawString(font, label, cx + (chipW - font.width(label)) / 2, by + 4,
                        picked ? GOLD : afford ? INK : 0xFF4E6A5C, false);
            }
            dealRect = new int[]{listX1 - dealW, by, dealW, 15};
            button(g, dealRect, "DEAL", mouseX, mouseY,
                    ClientBlackjack.fragments() >= ClientBlackjack.stake());
            g.drawString(font, "WAGER", listX0, by - 10, GOLD_DIM, false);
        }

        // --- result, once the dealer has finished turning its hand -------------
        boolean settled = phase == BlackjackSyncPayload.PHASE_DONE
                && ClientBlackjack.result() != BlackjackSyncPayload.RESULT_NONE
                && shown >= theirs.size();
        if (settled) {
            String text = switch (ClientBlackjack.result()) {
                case BlackjackSyncPayload.RESULT_PLAYER -> "YOU WIN";
                case BlackjackSyncPayload.RESULT_DEALER ->
                        ClientBlackjack.playerTotal() > Blackjack.TARGET ? "BUST" : "DEALER WINS";
                default -> "PUSH";
            };
            int colour = switch (ClientBlackjack.result()) {
                case BlackjackSyncPayload.RESULT_PLAYER -> WIN;
                case BlackjackSyncPayload.RESULT_DEALER -> LOSE;
                default -> GOLD;
            };
            long settledFor = since - (long) Math.max(0, theirs.size() - 1) * DEAL_STEP_MS;
            float grow = Mth.clamp(settledFor / 200f, 0f, 1f);
            pose.pushPose();
            pose.translate(width / 2f, listY - 26f, 0);
            pose.scale(Math.max(0.05f, 1.7f * grow), Math.max(0.05f, 1.7f * grow), 1f);
            g.drawString(font, text, -font.width(text) / 2, 0, colour, true);
            pose.popPose();
        }

        g.drawCenteredString(font, ClientBlackjack.fragments() + " fragments  ·  ESC to leave",
                width / 2, height - 12, DIM);
    }

    private String fit(String text, int maxWidth) {
        if (maxWidth <= 0 || font.width(text) <= maxWidth) {
            return text;
        }
        String cut = font.plainSubstrByWidth(text, Math.max(1, maxWidth));
        return cut;
    }

    private static int rowsFor(int cards, int perRow) {
        return cards <= 0 ? 1 : (cards + perRow - 1) / perRow;
    }

    /** The total as it appears mid-animation, so the number climbs with the card. */
    private int countUp(int finalTotal, List<ClientBlackjack.Draw> draws, int shown,
                        long since, boolean mine) {
        if (draws.isEmpty() || shown <= 0) {
            return mine ? finalTotal : -1;
        }
        if (!mine || since >= FLIP_MS) {
            return draws.get(Math.min(shown, draws.size()) - 1).total();
        }
        // the newest card has not finished turning, so it has not scored yet
        return shown >= 2 ? draws.get(shown - 2).total() : 0;
    }

    /**
     * One side's cards, wrapping into rows. Each newly turned card flips open on
     * its vertical axis and settles down into place.
     */
    private void drawHand(GuiGraphics g, int x, int y, int perRow,
                          List<ClientBlackjack.Draw> draws, int shown,
                          int mouseX, int mouseY, long since, boolean mine) {
        MobCard hoverCard = null;
        int hoverX = 0;
        int hoverY = 0;
        for (int i = 0; i < Math.min(shown, draws.size()); i++) {
            ClientBlackjack.Draw draw = draws.get(i);
            MobCard card = MobCards.byId(draw.mobId());
            int cx = x + (i % perRow) * (cardW + 4);
            int cy = y + (i / perRow) * (cardH + CAPTION_H + 4);

            // how far through its flip this particular card is
            long age = mine
                    ? (i == draws.size() - 1 ? since : FLIP_MS)
                    : since - (long) i * DEAL_STEP_MS;
            float t = Mth.clamp(age / (float) FLIP_MS, 0f, 1f);
            float ease = t * t * (3f - 2f * t);

            if (card != null) {
                var pose = g.pose();
                pose.pushPose();
                // The card is drawn AT cx,cy and the flip squashes around that
                // point. It used to be drawn at 0,0 and moved entirely by the
                // pose — but renderCard places the live mob in screen space from
                // the x,y it is handed, so every mob in this game was being
                // rendered in the corner of the window instead of on its card.
                pose.translate(cx + cardW / 2f, cy + cardH / 2f - (1f - ease) * 6f, 0);
                pose.scale(Math.max(0.02f, ease), 1f, 1f);
                pose.translate(-(cx + cardW / 2f), -(cy + cardH / 2f), 0);
                // and the mob only joins once the card has settled: an entity
                // render inside a squashed pose is not a thing to gamble on
                LivingEntity mob = ease >= 1f
                        ? CardRenderer.portraitEntity(minecraft, card, entityCache) : null;
                CardRenderer.renderCard(g, font, card, cx, cy, cardScale, -1, -1, mob, false, false);
                pose.popPose();
                if (mouseX >= cx && mouseX < cx + cardW && mouseY >= cy && mouseY < cy + cardH) {
                    hoverCard = card;
                    hoverX = cx;
                    hoverY = cy;
                }
            }

            if (t >= 1f) {
                String cap = draw.stat() >= 0 && draw.stat() < Stat.values().length
                        ? Stat.values()[draw.stat()].shortLabel : "?";
                String val = "+" + draw.value();
                g.drawString(font, cap, cx, cy + cardH + 1, DIM, false);
                g.drawString(font, val, cx + cardW - font.width(val), cy + cardH + 1,
                        draw.value() == 0 ? DIM : INK, false);
            }
        }
        if (hoverCard != null) {
            String name = hoverCard.displayName();
            int w = font.width(name) + 8;
            int tx = Mth.clamp(hoverX + cardW / 2 - w / 2, 2, width - w - 2);
            int ty = Math.max(2, hoverY - 12);
            g.fill(tx, ty, tx + w, ty + 11, 0xE0000000);
            g.renderOutline(tx, ty, w, 11, GOLD_DIM);
            g.drawString(font, name, tx + 4, ty + 2, INK, false);
        }
    }

    private void drawTotal(GuiGraphics g, int x, int y, int w, String who, int total,
                           boolean bust) {
        g.fill(x, y, x + w, y + 24, 0x33000000);
        g.renderOutline(x, y, w, 24, bust ? LOSE : EDGE);
        g.drawString(font, who, x + 6, y + 8, DIM, false);
        String value = total < 0 ? "??" : String.valueOf(total);
        var pose = g.pose();
        pose.pushPose();
        pose.translate(x + w - 8f, y + 4f, 0);
        pose.scale(1.6f, 1.6f, 1f);
        g.drawString(font, value, -font.width(value), 0, bust ? LOSE : INK, true);
        pose.popPose();
    }

    private void button(GuiGraphics g, int[] r, String label, int mouseX, int mouseY,
                        boolean enabled) {
        boolean over = enabled && mouseX >= r[0] && mouseX < r[0] + r[2]
                && mouseY >= r[1] && mouseY < r[1] + r[3];
        g.fill(r[0], r[1], r[0] + r[2], r[1] + r[3],
                !enabled ? 0x22000000 : over ? 0xFF1D5C43 : 0xFF124532);
        g.renderOutline(r[0], r[1], r[2], r[3], enabled ? GOLD_DIM : EDGE);
        g.drawString(font, label, r[0] + (r[2] - font.width(label)) / 2, r[1] + 4,
                enabled ? INK : 0xFF4E6A5C, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (ClientBlackjack.inHand()) {
                if (hovered >= 0) {
                    send(BlackjackActionPayload.call(hovered));
                    click();
                    return true;
                }
                if (hit(standRect, mouseX, mouseY)) {
                    send(BlackjackActionPayload.stand());
                    click();
                    return true;
                }
            } else {
                for (int i = 0; i < stakeRects.length; i++) {
                    if (hit(stakeRects[i], mouseX, mouseY)
                            && ClientBlackjack.fragments() >= Blackjack.STAKES[i]) {
                        send(BlackjackActionPayload.setStake(i));
                        click();
                        return true;
                    }
                }
                if (hit(dealRect, mouseX, mouseY)
                        && ClientBlackjack.fragments() >= ClientBlackjack.stake()) {
                    send(BlackjackActionPayload.deal());
                    click();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double dx, double dy) {
        if (!ClientBlackjack.inHand() && dy != 0) {
            int next = ClientBlackjack.stakeIndex() + (int) Math.signum(dy);
            next = Mth.clamp(next, 0, Blackjack.STAKES.length - 1);
            if (next != ClientBlackjack.stakeIndex()) {
                send(BlackjackActionPayload.setStake(next));
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, dx, dy);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        // 1-6 call a stat, space stands, enter deals — the table should be
        // playable without hunting for a small row with the mouse
        if (ClientBlackjack.inHand()) {
            if (key >= 49 && key < 49 + Stat.values().length) {
                send(BlackjackActionPayload.call(key - 49));
                click();
                return true;
            }
            if (key == 32) {
                send(BlackjackActionPayload.stand());
                click();
                return true;
            }
        } else if ((key == 257 || key == 335)
                && ClientBlackjack.fragments() >= ClientBlackjack.stake()) {
            send(BlackjackActionPayload.deal());
            click();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    private static boolean hit(int[] r, double mouseX, double mouseY) {
        return r != null && mouseX >= r[0] && mouseX < r[0] + r[2]
                && mouseY >= r[1] && mouseY < r[1] + r[3];
    }

    private void click() {
        if (minecraft != null) {
            minecraft.getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f));
        }
    }

    private static void send(BlackjackActionPayload payload) {
        PacketDistributor.sendToServer(payload);
    }

    @Override
    public void onClose() {
        send(BlackjackActionPayload.leave());
        super.onClose();
    }
}
