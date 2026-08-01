package com.jrpetty.mobtrumps.client;

import com.jrpetty.mobtrumps.BluffActionPayload;
import com.jrpetty.mobtrumps.BluffManager;
import com.jrpetty.mobtrumps.game.Bluff;
import com.jrpetty.mobtrumps.game.MobCard;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The Bluff table: your hand along the bottom, the other seats across the top,
 * the claim in the middle and a pile of face-down cards under it.
 *
 * <p>The screen knows only what the server has told it, which is your own cards
 * and everyone else's hand <em>counts</em>. It cannot show you what is in the
 * pile because it does not know, and that is the game — the Challenge button is
 * the only way to find out, and it costs you if you are wrong.
 *
 * <p>Cards you have selected lift out of your hand rather than highlighting in
 * place, so what you are about to swear to is unmistakable before you commit.
 */
public class BluffScreen extends Screen {

    private static final int FELT_1 = 0xFF1B3A2E;
    private static final int FELT_0 = 0xFF0C1C16;
    private static final int GOLD = 0xFFE9C46A;
    private static final int GOLD_DIM = 0xFF8A6A2A;
    private static final int INK = 0xFFF2ECDD;
    private static final int DIM = 0xFF8FA79A;
    private static final int FAINT = 0xFF52685D;
    private static final int GOOD = 0xFF6BE87A;
    private static final int BAD = 0xFFF0625A;
    private static final int PANEL = 0xE00B1710;
    private static final int EDGE = 0xFF2C4A3C;

    /** How long a revealed challenge stays lit before it stops flashing. */
    private static final long FLASH_MS = 1400L;
    /**
     * Tightest a fanned hand may be packed, as a fraction of a card's width.
     * A third of a card is enough to tell two mobs apart and to click the one
     * you meant; below that the fan stops being a row of cards.
     */
    private static final float MIN_OVERLAP = 0.34f;
    /**
     * Top of the band between the pile and the hand, where a reveal goes.
     * Clears the pile's own caption, which sits at y=130 — at 136 the revealed
     * cards were printed straight through "7 cards face down".
     */
    private static final int REVEAL_TOP = 144;

    private final Map<String, LivingEntity> entityCache = new HashMap<>();
    /** Indices into the hand that are lifted, ready to be sworn to. */
    private final Set<Integer> picked = new LinkedHashSet<>();

    private float handScale;
    private int handY;
    private int cardW;
    private int cardH;
    private int gap;
    private int handX;
    private int hovered = -1;

    private int[] playRect;
    private int[] challengeRect;
    private int[] passRect;
    private int[] newRect;
    private final List<int[]> stakeRects = new ArrayList<>();
    private final List<int[]> seatRects = new ArrayList<>();

    public BluffScreen() {
        super(Component.literal("Mob Bluff"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        picked.clear();
    }

    /**
     * Fit the hand across the bottom.
     *
     * <p>Hands grow — eating a pile can leave you holding twenty — so the cards
     * shrink and then start overlapping rather than running off the edge. The
     * overlap is capped at two thirds so every card still shows enough of
     * itself to be told apart and clicked.
     */
    private void solveHand(int count) {
        int avail = width - 24;
        int n = Math.max(1, count);
        // widest the cards can be and still fan inside the space at the tightest
        // overlap we allow, and short enough to leave the claim and pile alone
        float byWidth = avail / (1f + MIN_OVERLAP * (n - 1)) / CardRenderer.CARD_W;
        float byHeight = (height - 200) / (float) CardRenderer.CARD_H;
        handScale = Mth.clamp(Math.min(byWidth, byHeight), 0.13f, 0.42f);
        cardW = Math.round(CardRenderer.CARD_W * handScale);
        cardH = Math.round(CardRenderer.CARD_H * handScale);

        if (n == 1) {
            gap = cardW;
        } else {
            int spread = (avail - cardW) / (n - 1);
            gap = Mth.clamp(spread, Math.round(cardW * MIN_OVERLAP), cardW + 4);
            // the floor above can still overrun a very large hand at the scale
            // floor, so the fan is squeezed the rest of the way rather than
            // being allowed off the edge
            if ((n - 1) * gap + cardW > avail) {
                gap = Math.max(1, spread);
            }
        }
        int span = count == 0 ? 0 : (count - 1) * gap + cardW;
        handX = (width - span) / 2;
        handY = height - cardH - 40;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.fillGradient(0, 0, width, height, FELT_1, FELT_0);
        g.fill(0, 0, width, 2, GOLD_DIM);
        g.fill(0, height - 2, width, height, GOLD_DIM);

        stakeRects.clear();
        seatRects.clear();
        playRect = null;
        challengeRect = null;
        passRect = null;
        newRect = null;
        hovered = -1;

        if (ClientBluff.idle()) {
            drawLobby(g, mouseX, mouseY);
            return;
        }

        List<MobCard> hand = ClientBluff.hand();
        solveHand(hand.size());

        drawOpponents(g);
        drawClaim(g);
        drawPile(g);
        drawReveal(g);
        drawHand(g, hand, mouseX, mouseY);
        drawControls(g, mouseX, mouseY);
        drawLog(g);
        if (ClientBluff.over()) {
            drawResult(g, mouseX, mouseY);
        }
    }

    /** Before a hand is dealt: table size, stake, and the rules in three lines. */
    private void drawLobby(GuiGraphics g, int mouseX, int mouseY) {
        int pw = Math.min(320, width - 20);
        int ph = Math.min(200, height - 20);
        int px = (width - pw) / 2;
        int py = (height - ph) / 2;
        g.fill(px, py, px + pw, py + ph, PANEL);
        g.renderOutline(px, py, pw, ph, GOLD_DIM);
        g.fill(px, py, px + pw, py + 1, GOLD);

        var pose = g.pose();
        pose.pushPose();
        pose.translate(px + 12f, py + 10f, 0);
        pose.scale(1.5f, 1.5f, 1f);
        g.drawString(font, "MOB BLUFF", 0, 0, GOLD, true);
        pose.popPose();

        int y = py + 34;
        for (String line : new String[]{
                "A question is turned up. Put cards down face",
                "for it — or lie. Call the play before yours and",
                "the liar eats the pile. Wrong, and you do.",
                "Empty your hand to win."}) {
            g.drawString(font, line, px + 12, y, DIM, false);
            y += 11;
        }

        y += 8;
        g.drawString(font, "PLAYERS", px + 12, y, GOLD, false);
        int bx = px + 74;
        for (int n = Bluff.MIN_SEATS; n <= Bluff.MAX_SEATS; n++) {
            int bw = 24;
            boolean on = ClientBluff.seats() == n;
            boolean hot = inRect(mouseX, mouseY, new int[]{bx, y - 3, bw, 14});
            g.fill(bx, y - 3, bx + bw, y + 11, on ? 0xFF2C6E49 : (hot ? 0xFF203A2E : 0xFF16261E));
            g.renderOutline(bx, y - 3, bw, 14, on ? GOLD : EDGE);
            g.drawCenteredString(font, String.valueOf(n), bx + bw / 2, y, on ? INK : DIM);
            seatRects.add(new int[]{bx, y - 3, bw, 14, n});
            bx += bw + 4;
        }

        y += 22;
        g.drawString(font, "STAKE", px + 12, y, GOLD, false);
        bx = px + 74;
        for (int i = 0; i < BluffManager.STAKES.length; i++) {
            String label = String.valueOf(BluffManager.STAKES[i]);
            int bw = font.width(label) + 10;
            boolean on = ClientBluff.stakeIndex() == i;
            boolean hot = inRect(mouseX, mouseY, new int[]{bx, y - 3, bw, 14});
            g.fill(bx, y - 3, bx + bw, y + 11, on ? 0xFF2C6E49 : (hot ? 0xFF203A2E : 0xFF16261E));
            g.renderOutline(bx, y - 3, bw, 14, on ? GOLD : EDGE);
            g.drawString(font, label, bx + 5, y, on ? INK : DIM, false);
            stakeRects.add(new int[]{bx, y - 3, bw, 14, i});
            bx += bw + 4;
        }

        String deal = "DEAL  ·  " + ClientBluff.stake() + " fragments";
        int dw = font.width(deal) + 24;
        int dx = px + (pw - dw) / 2;
        int dy = py + ph - 26;
        boolean hot = inRect(mouseX, mouseY, new int[]{dx, dy, dw, 18});
        g.fill(dx, dy, dx + dw, dy + 18, hot ? 0xFF3C8F5F : 0xFF2C6E49);
        g.renderOutline(dx, dy, dw, 18, hot ? GOLD : GOLD_DIM);
        g.drawCenteredString(font, deal, dx + dw / 2, dy + 5, INK);
        newRect = new int[]{dx, dy, dw, 18};
    }

    /** The other seats, as a name, a card count and a row of card backs. */
    private void drawOpponents(GuiGraphics g) {
        int seats = ClientBluff.seats();
        int mine = ClientBluff.mySeat();
        List<Integer> others = new ArrayList<>();
        for (int s = 0; s < seats; s++) {
            if (s != mine) {
                others.add(s);
            }
        }
        if (others.isEmpty()) {
            return;
        }
        int slot = width / others.size();
        for (int i = 0; i < others.size(); i++) {
            int seat = others.get(i);
            int cx = slot * i + slot / 2;
            int y = 8;
            boolean acting = ClientBluff.turn() == seat;
            boolean pending = ClientBluff.pendingOut() == seat;
            int count = ClientBluff.handSize(seat);

            String name = ClientBluff.seatName(seat);
            int color = pending ? BAD : acting ? GOLD : DIM;
            g.drawCenteredString(font, name.toUpperCase(Locale.ROOT), cx, y, color);
            String sub = count + (count == 1 ? " card" : " cards");
            g.drawCenteredString(font, sub, cx, y + 11, count <= 2 ? GOLD : FAINT);

            // a fan of backs, one per card, capped so a big hand stays on screen
            int show = Math.min(count, 12);
            int bw = 7;
            int step = show > 8 ? 5 : 7;
            int span = show == 0 ? 0 : (show - 1) * step + bw;
            int bx = cx - span / 2;
            for (int c = 0; c < show; c++) {
                int x = bx + c * step;
                g.fill(x, y + 23, x + bw, y + 34, 0xFF241C4A);
                g.renderOutline(x, y + 23, bw, 11, pending ? BAD : GOLD_DIM);
            }
            if (count > show) {
                g.drawCenteredString(font, "+" + (count - show), cx, y + 36, FAINT);
            }
            if (acting) {
                g.renderOutline(cx - span / 2 - 3, y + 20, span + 6, 17, GOLD);
            }
        }
    }

    /** The round's claim, which every card played is sworn to answer YES to. */
    private void drawClaim(GuiGraphics g) {
        int y = 62;
        String text = ClientBluff.claim().text();
        int w = Math.min(width - 28, font.width(text) + 24);
        int x = (width - w) / 2;
        g.fill(x, y, x + w, y + 30, PANEL);
        g.renderOutline(x, y, w, 30, GOLD_DIM);
        g.drawCenteredString(font, "EVERY CARD PLAYED CLAIMS", width / 2, y + 4, FAINT);
        String fitted = fit(text, w - 12);
        g.drawCenteredString(font, fitted, width / 2, y + 16, GOLD);
    }

    /** The face-down pile. Its size is the stake on the next challenge. */
    private void drawPile(GuiGraphics g) {
        int count = ClientBluff.pile();
        int cy = 104;
        String label = count == 0 ? "no cards on the table"
                : count + (count == 1 ? " card face down" : " cards face down");
        g.drawCenteredString(font, label, width / 2, cy + 26, count >= 5 ? GOLD : FAINT);
        if (count == 0) {
            return;
        }
        // a scattered stack, deepest at the bottom
        int show = Math.min(count, 10);
        for (int i = 0; i < show; i++) {
            int off = i * 2;
            int w = 26;
            int h = 20;
            int x = width / 2 - w / 2 + ((i % 3) - 1) * 2;
            int y = cy - off / 2;
            g.fill(x, y, x + w, y + h, 0xFF241C4A);
            g.renderOutline(x, y, w, h, GOLD_DIM);
        }
        int last = ClientBluff.lastCount();
        if (last > 0 && ClientBluff.lastSeat() >= 0) {
            String who = ClientBluff.seatName(ClientBluff.lastSeat());
            g.drawCenteredString(font, who + " put down " + last, width / 2, cy - 14, DIM);
        }
    }

    /** What the last challenge turned over, while it is still news. */
    private void drawReveal(GuiGraphics g) {
        List<MobCard> shown = ClientBluff.reveal();
        if (shown.isEmpty() || ClientBluff.revealAccused() < 0) {
            return;
        }
        long since = System.currentTimeMillis() - ClientBluff.changedAt();
        boolean lying = ClientBluff.revealWasLying();
        int accent = lying ? BAD : GOOD;

        // The reveal is the moment the whole game is played for, so it is sized
        // to whatever band is left between the pile and the hand rather than
        // drawn at a fixed size and dropped when it does not fit. It used to be
        // fixed at 0.30, which silently showed nothing at all on a 640x360
        // window — the one frame a player actually wants to see.
        int top = REVEAL_TOP;
        int band = handY - 14 - top - 10; // 10 for the verdict line beneath
        if (band < 22) {
            return; // genuinely no room; the log and the verdict still say it
        }
        float scale = Math.min(0.30f, Math.min(band / (float) CardRenderer.CARD_H,
                (width - 40f) / (shown.size() * (CardRenderer.CARD_W + 14f))));
        if (scale < 0.10f) {
            return;
        }
        int cw = Math.round(CardRenderer.CARD_W * scale);
        int ch = Math.round(CardRenderer.CARD_H * scale);
        int span = shown.size() * (cw + 4) - 4;
        int x = (width - span) / 2;
        int y = top + Math.max(0, (band - ch) / 2);
        String head = ClientBluff.seatName(ClientBluff.revealChallenger()) + " called "
                + ClientBluff.seatName(ClientBluff.revealAccused());
        g.drawCenteredString(font, head, width / 2, y - 12, DIM);
        for (int i = 0; i < shown.size(); i++) {
            MobCard card = shown.get(i);
            int cx = x + i * (cw + 4);
            LivingEntity mob = CardRenderer.portraitEntity(minecraft, card, entityCache);
            CardRenderer.renderCard(g, font, card, cx, y, scale, -1, -1, mob, false, false);
            boolean matched = ClientBluff.claim().matches(card);
            g.renderOutline(cx - 1, y - 1, cw + 2, ch + 2, matched ? GOOD : BAD);
            if (since < FLASH_MS && (since / 180) % 2 == 0) {
                g.renderOutline(cx - 2, y - 2, cw + 4, ch + 4, matched ? GOOD : BAD);
            }
        }
        String verdict = lying
                ? "CAUGHT — " + ClientBluff.revealTaken() + " cards to "
                        + ClientBluff.seatName(ClientBluff.revealAccused())
                : "HONEST — " + ClientBluff.revealTaken() + " cards to "
                        + ClientBluff.seatName(ClientBluff.revealChallenger());
        g.drawCenteredString(font, verdict, width / 2, y + ch + 4, accent);
    }

    /** Your hand. Picked cards lift clear of the row. */
    private void drawHand(GuiGraphics g, List<MobCard> hand, int mouseX, int mouseY) {
        for (int i = 0; i < hand.size(); i++) {
            int x = handX + i * gap;
            int y = handY - (picked.contains(i) ? 14 : 0);
            boolean over = mouseX >= x && mouseX < x + (i == hand.size() - 1 ? cardW : gap)
                    && mouseY >= y && mouseY < y + cardH;
            if (over) {
                hovered = i;
            }
        }
        for (int i = 0; i < hand.size(); i++) {
            MobCard card = hand.get(i);
            int x = handX + i * gap;
            boolean lifted = picked.contains(i);
            int y = handY - (lifted ? 14 : 0) - (hovered == i && !lifted ? 5 : 0);
            LivingEntity mob = CardRenderer.portraitEntity(minecraft, card, entityCache);
            g.fill(x + 2, y + 3, x + cardW + 3, y + cardH + 4, 0x55000000);
            CardRenderer.renderCard(g, font, card, x, y, handScale, mouseX, mouseY, mob,
                    false, hovered == i);
            if (lifted) {
                g.renderOutline(x - 2, y - 2, cardW + 4, cardH + 4, GOLD);
                g.renderOutline(x - 1, y - 1, cardW + 2, cardH + 2, 0x66E9C46A);
            }
            // Whether this card actually answers the claim: your own cards are
            // yours to see, and the whole decision is which of them to swear to.
            //
            // On the LEFT edge, because a fanned hand overlaps left-to-right —
            // the only strip of a card guaranteed to be visible is its left one,
            // and a tick on the right vanished under the next card the moment a
            // hand grew past about a dozen.
            boolean matches = ClientBluff.claim().matches(card);
            int tick = matches ? GOOD : BAD;
            g.fill(x + 2, y + 2, x + 6, y + 6, tick);
            g.fill(x + 2, y + 2, x + 6, y + 3, 0x66FFFFFF);
        }
    }

    /** Play / Challenge / Let them go, and the stake line. */
    private void drawControls(GuiGraphics g, int mouseX, int mouseY) {
        int y = height - 30;
        boolean mine = ClientBluff.myTurn();
        boolean pending = ClientBluff.atMatchPoint();

        if (ClientBluff.over()) {
            return;
        }
        if (!mine) {
            g.drawCenteredString(font, ClientBluff.seatName(ClientBluff.turn()) + " is thinking…",
                    width / 2, y + 5, DIM);
            return;
        }

        List<int[]> buttons = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        List<Boolean> enabled = new ArrayList<>();

        if (pending) {
            labels.add("Let them go");
            enabled.add(true);
        } else {
            labels.add(picked.isEmpty() ? "Pick cards" : "Swear to " + picked.size());
            enabled.add(!picked.isEmpty());
        }
        if (ClientBluff.canChallenge()) {
            labels.add("Challenge");
            enabled.add(true);
        }

        int total = 0;
        for (String label : labels) {
            total += font.width(label) + 24 + 6;
        }
        int x = (width - total) / 2;
        for (int i = 0; i < labels.size(); i++) {
            String label = labels.get(i);
            int bw = font.width(label) + 24;
            int[] rect = {x, y, bw, 18};
            boolean on = enabled.get(i);
            boolean hot = on && inRect(mouseX, mouseY, rect);
            boolean danger = label.equals("Challenge");
            int fill = !on ? 0xFF1A2A22
                    : danger ? (hot ? 0xFF8E3A34 : 0xFF6A2A26)
                    : (hot ? 0xFF3C8F5F : 0xFF2C6E49);
            g.fill(x, y, x + bw, y + 18, fill);
            g.renderOutline(x, y, bw, 18, on ? (hot ? GOLD : GOLD_DIM) : EDGE);
            g.drawCenteredString(font, label, x + bw / 2, y + 5, on ? INK : FAINT);
            buttons.add(rect);
            x += bw + 6;
        }
        int at = 0;
        if (pending) {
            passRect = buttons.get(at++);
        } else {
            playRect = buttons.get(at++);
        }
        if (ClientBluff.canChallenge() && at < buttons.size()) {
            challengeRect = buttons.get(at);
        }

        if (pending) {
            g.drawCenteredString(font, ClientBluff.seatName(ClientBluff.pendingOut())
                    + " is one move from winning — call it or let it stand",
                    width / 2, y - 12, BAD);
        }
    }

    /** The last few things that happened, down the left. */
    private void drawLog(GuiGraphics g) {
        List<String> lines = ClientBluff.log();
        if (lines.isEmpty()) {
            return;
        }
        // A lifted card rises fourteen pixels above the hand, so the log stops
        // short of that and not of the resting row — it used to be drawn over
        // the top of whichever cards you had just selected.
        int bottom = handY - 18;
        int room = (bottom - REVEAL_TOP) / 10;
        int max = Math.min(Math.min(6, lines.size()), room);
        if (max <= 0) {
            return;
        }
        int y = bottom - max * 10;
        int wide = Math.min(150, width / 3);
        for (int i = 0; i < max; i++) {
            String line = lines.get(lines.size() - max + i);
            g.drawString(font, fit(line, wide), 8, y + i * 10, i == max - 1 ? DIM : FAINT, false);
        }
    }

    private void drawResult(GuiGraphics g, int mouseX, int mouseY) {
        int pw = Math.min(260, width - 20);
        int ph = 84;
        int px = (width - pw) / 2;
        int py = (height - ph) / 2;
        g.fill(0, 0, width, height, 0xB0000000);
        g.fill(px, py, px + pw, py + ph, PANEL);
        g.renderOutline(px, py, pw, ph, ClientBluff.won() ? GOOD : BAD);
        String head = ClientBluff.won() ? "YOU WENT OUT" : "BEATEN";
        g.drawCenteredString(font, head, width / 2, py + 12, ClientBluff.won() ? GOOD : BAD);
        String sub = ClientBluff.won()
                ? "+" + (ClientBluff.stake() * ClientBluff.seats()) + " fragments"
                : ClientBluff.seatName(ClientBluff.winner()) + " emptied their hand first";
        g.drawCenteredString(font, fit(sub, pw - 16), width / 2, py + 28, INK);

        String again = "Deal again";
        int bw = font.width(again) + 24;
        int bx = px + (pw - bw) / 2;
        int by = py + ph - 26;
        boolean hot = inRect(mouseX, mouseY, new int[]{bx, by, bw, 18});
        g.fill(bx, by, bx + bw, by + 18, hot ? 0xFF3C8F5F : 0xFF2C6E49);
        g.renderOutline(bx, by, bw, 18, hot ? GOLD : GOLD_DIM);
        g.drawCenteredString(font, again, bx + bw / 2, by + 5, INK);
        newRect = new int[]{bx, by, bw, 18};
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        int mx = (int) mouseX;
        int my = (int) mouseY;

        for (int[] rect : seatRects) {
            if (inRect(mx, my, rect)) {
                send(BluffActionPayload.seats(rect[4]));
                return true;
            }
        }
        for (int[] rect : stakeRects) {
            if (inRect(mx, my, rect)) {
                send(BluffActionPayload.stake(rect[4]));
                return true;
            }
        }
        if (newRect != null && inRect(mx, my, newRect)) {
            picked.clear();
            send(BluffActionPayload.newGame());
            return true;
        }
        if (challengeRect != null && inRect(mx, my, challengeRect)) {
            picked.clear();
            send(BluffActionPayload.challenge());
            return true;
        }
        if (passRect != null && inRect(mx, my, passRect)) {
            picked.clear();
            send(BluffActionPayload.pass());
            return true;
        }
        if (playRect != null && inRect(mx, my, playRect) && !picked.isEmpty()) {
            send(BluffActionPayload.play(new ArrayList<>(picked)));
            picked.clear();
            return true;
        }
        if (hovered >= 0 && ClientBluff.myTurn() && !ClientBluff.atMatchPoint()) {
            if (picked.contains(hovered)) {
                picked.remove(hovered);
            } else if (picked.size() < Bluff.MAX_PLAY) {
                picked.add(hovered);
                click();
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 || keyCode == 335) { // enter
            if (ClientBluff.idle() || ClientBluff.over()) {
                send(BluffActionPayload.newGame());
            } else if (ClientBluff.atMatchPoint()) {
                send(BluffActionPayload.pass());
            } else if (!picked.isEmpty()) {
                send(BluffActionPayload.play(new ArrayList<>(picked)));
                picked.clear();
            }
            return true;
        }
        if (keyCode == 67 && ClientBluff.canChallenge()) { // C
            picked.clear();
            send(BluffActionPayload.challenge());
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        send(BluffActionPayload.leave());
        super.onClose();
    }

    private void send(BluffActionPayload payload) {
        PacketDistributor.sendToServer(payload);
    }

    private void click() {
        if (minecraft != null) {
            minecraft.getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.4f));
        }
    }

    private String fit(String text, int maxWidth) {
        if (font.width(text) <= maxWidth) {
            return text;
        }
        return font.plainSubstrByWidth(text, Math.max(8, maxWidth - font.width("…"))) + "…";
    }

    private static boolean inRect(int mx, int my, int[] rect) {
        return rect != null && mx >= rect[0] && mx < rect[0] + rect[2]
                && my >= rect[1] && my < rect[1] + rect[3];
    }
}
