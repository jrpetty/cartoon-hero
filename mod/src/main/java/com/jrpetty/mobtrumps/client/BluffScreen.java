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

import static com.jrpetty.mobtrumps.client.TableArt.BAD;
import static com.jrpetty.mobtrumps.client.TableArt.BRASS;
import static com.jrpetty.mobtrumps.client.TableArt.BRASS_DARK;
import static com.jrpetty.mobtrumps.client.TableArt.BRASS_DIM;
import static com.jrpetty.mobtrumps.client.TableArt.BRASS_HI;
import static com.jrpetty.mobtrumps.client.TableArt.DIM;
import static com.jrpetty.mobtrumps.client.TableArt.FAINT;
import static com.jrpetty.mobtrumps.client.TableArt.GOOD;
import static com.jrpetty.mobtrumps.client.TableArt.INK;
import static com.jrpetty.mobtrumps.client.TableArt.RAIL;

/**
 * The Bluff table.
 *
 * <p>Laid out as a real one: a timber rail around the edge, a pool of light
 * over the middle of the felt, the other players ranged across the top behind
 * brass name plates, the round's claim hanging above the table on a board, the
 * pile scattered face down beneath it, and your own hand fanned along the
 * bottom.
 *
 * <p>The screen knows only what the server has told it — your cards, and
 * everyone else's hand <em>counts</em>. It cannot show you what is in the pile
 * because it does not know, and that is the game.
 *
 * <p><b>Nothing carrying a live mob model is ever rotated.</b> The fan in your
 * hand comes from an arc and an overlap rather than from turning each card,
 * because every rotation elsewhere in this mod is applied to flat fills and a
 * spinning entity render is not a thing to find out about in a release. The
 * card backs — opponents' hands, the pile — are pure rectangles, so those turn.
 */
public class BluffScreen extends Screen {

    /** How long a revealed challenge stays lit before it stops flashing. */
    private static final long FLASH_MS = 1400L;
    /**
     * Tightest a fanned hand may be packed, as a fraction of a card's width.
     * A third of a card is enough to tell two mobs apart and to click the one
     * you meant; below that the fan stops being a row of cards.
     */
    private static final float MIN_OVERLAP = 0.34f;
    /**
     * Largest a card in hand may be drawn. Lowered from 0.42 because the hand
     * was eating the room the reveal needed: at 640x360 a full-size hand left a
     * twenty-two pixel band, which is under the floor, so the cards a challenge
     * turned over were never shown at the commonest window size in the game.
     */
    private static final float HAND_SCALE_CAP = 0.36f;
    /** First row below the opponents' fanned hands. */
    private static final int MID_TOP = 64;

    // The middle of the table — claim board, who swore what, the heap, and the
    // band a reveal is turned over in — is SOLVED, not laid out from fixed
    // constants. Fixed ones fit at 640x360 and ran the pile's caption straight
    // through the player's own hand at 320x240, because the space between the
    // opponents and the hand is barely seventy pixels there and the pieces want
    // a hundred. What gets dropped goes in order of what a player can least
    // afford to lose: the heap's picture before its count, and the "swore to"
    // line before either.
    private int claimY;
    private int claimH;
    /** -1 when there is no room for it. */
    private int sworeY;
    /** -1 when the heap is reduced to its caption. */
    private int pileCy;
    private int pileCapY;
    private int revealTop;
    private int revealBand;

    /**
     * Smallest band worth turning cards over in; below this, the ribbon alone.
     * Counts the ribbon's own {@value #VERDICT_H} pixels, since the verdict is
     * part of the reveal and not something extra underneath it — centring the
     * cards alone and then adding the ribbon pushed it into the hand.
     */
    private static final int MIN_REVEAL_BAND = 42;
    private static final int VERDICT_H = 12;

    private void solveMiddle() {
        int bottom = handTop() - 2;
        int space = bottom - MID_TOP;
        claimH = space >= 96 ? 30 : 24;
        claimY = MID_TOP;
        int y = claimY + claimH + 2;

        sworeY = space >= claimH + 46 ? y : -1;
        if (sworeY >= 0) {
            y += 10;
        }
        if (space >= claimH + 46) {
            pileCy = y + 11;
            y += 24;
        } else {
            pileCy = -1;
        }
        pileCapY = y;
        y += 13;
        revealTop = y + 2;
        revealBand = bottom - revealTop;
    }
    /** How far the middle of the fan rises above its ends. */
    private static final int ARC = 9;
    /** How far a chosen card lifts out of the hand. */
    private static final int LIFT = 15;
    /** ...and a hovered one. */
    private static final int HOVER_LIFT = 6;

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

    private static float breath(long period) {
        if (ClientPrefs.reducedMotion()) {
            return 0.5f;
        }
        return 0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / (double) period);
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
        handScale = Mth.clamp(Math.min(byWidth, byHeight), 0.13f, HAND_SCALE_CAP);
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

    /**
     * The resting height of card {@code i}, following the fan's curve.
     *
     * <p>Drawing and hit-testing both come through here, so a card can never be
     * somewhere other than where it can be clicked.
     */
    /**
     * The highest pixel the hand can reach — the arc's crown, plus the lift a
     * chosen card takes.
     *
     * <p>Everything above the hand is spaced against this and not against
     * {@link #handY}. Measuring from the resting line let the reveal's verdict
     * ribbon be drawn straight through the cards a player had just picked up,
     * and a layout sweep passed it, because the sweep made the same mistake.
     */
    private int handTop() {
        return handY - Math.min(ARC, Math.round(cardH * 0.16f)) - LIFT - HOVER_LIFT;
    }

    private int cardY(int i, int count) {
        if (count <= 1) {
            return handY;
        }
        float centre = (count - 1) / 2f;
        float t = (i - centre) / centre;
        int arc = Math.min(ARC, Math.round(cardH * 0.16f));
        return handY - Math.round(arc * (1f - t * t));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);

        stakeRects.clear();
        seatRects.clear();
        playRect = null;
        challengeRect = null;
        passRect = null;
        newRect = null;
        hovered = -1;

        if (ClientBluff.idle()) {
            TableArt.felt(g, width, height, width / 2, height / 2);
            TableArt.rail(g, width, height);
            drawLobby(g, mouseX, mouseY);
            return;
        }

        List<MobCard> hand = ClientBluff.hand();
        solveHand(hand.size());
        solveMiddle();

        // the light hangs over the pile, which is where the game happens
        TableArt.felt(g, width, height, width / 2, 112);
        TableArt.rail(g, width, height);

        drawOpponents(g);
        drawClaim(g);
        drawPile(g);
        drawReveal(g);
        drawLog(g);
        drawHand(g, hand, mouseX, mouseY);
        drawControls(g, mouseX, mouseY);
        if (ClientBluff.over()) {
            drawResult(g, mouseX, mouseY);
        }
    }

    /** Before a hand is dealt: the rules, the table size and the stake. */
    private void drawLobby(GuiGraphics g, int mouseX, int mouseY) {
        int pw = Math.min(330, width - RAIL * 2 - 8);
        int ph = Math.min(214, height - RAIL * 2 - 8);
        int px = (width - pw) / 2;
        int py = (height - ph) / 2;
        TableArt.pool(g, width / 2, py + 30, pw, BRASS, 0x1E);
        TableArt.plate(g, px, py, pw, ph, BRASS_DIM);

        // Everything below the masthead is measured against what is left, so a
        // short window drops explanation rather than printing the stake row
        // through the Deal button — which is what a fixed layout did at 200px.
        String[] rules = {
                "A question turns up. Put cards down that answer",
                "YES to it — or lie and hope nobody calls it.",
                "Call the play before yours: catch a liar and they",
                "eat the pile, get it wrong and you eat what you",
                "called. Empty your hand to win."};
        int head = 46;
        int rows = 22 + 22 + 30;  // players, stake, deal
        int forRules = ph - head - rows - 12;
        int lines = Mth.clamp(forRules / 10, 0, rules.length);

        var pose = g.pose();
        pose.pushPose();
        pose.translate(px + pw / 2f, py + 10f, 0);
        pose.scale(1.7f, 1.7f, 1f);
        String title = "MOB BLUFF";
        g.drawString(font, title, -font.width(title) / 2, 0, BRASS, true);
        pose.popPose();
        g.fill(px + 20, py + 30, px + pw - 20, py + 31, BRASS_DARK);
        g.drawCenteredString(font, "say it with a straight face", width / 2, py + 34, FAINT);

        int y = py + head;
        for (int i = 0; i < lines; i++) {
            g.drawString(font, fit(rules[i], pw - 24), px + 14, y, DIM, false);
            y += 10;
        }

        // the two chooser rows sit above the Deal button, never over it
        int dealY = py + ph - 28;
        int stakeY = dealY - 24;
        int seatY = stakeY - 22;
        y = Math.max(y + 6, Math.min(seatY, py + head + lines * 10 + 6));
        seatY = Math.max(y, seatY);
        stakeY = seatY + 22;

        g.drawString(font, "PLAYERS", px + 14, seatY + 3, BRASS_DIM, false);
        int bx = px + 76;
        for (int n = Bluff.MIN_SEATS; n <= Bluff.MAX_SEATS; n++) {
            int bw = 26;
            boolean on = ClientBluff.seats() == n;
            boolean hot = inRect(mouseX, mouseY, new int[]{bx, seatY, bw, 15});
            TableArt.button(g, bx, seatY, bw, 15, on ? 0xFF2C6E49 : 0xFF23382E, hot, true);
            g.drawCenteredString(font, String.valueOf(n), bx + bw / 2, seatY + 4, on ? INK : DIM);
            seatRects.add(new int[]{bx, seatY, bw, 15, n});
            bx += bw + 5;
        }

        g.drawString(font, "STAKE", px + 14, stakeY + 3, BRASS_DIM, false);
        bx = px + 76;
        for (int i = 0; i < BluffManager.STAKES.length; i++) {
            String label = String.valueOf(BluffManager.STAKES[i]);
            int bw = font.width(label) + 12;
            boolean on = ClientBluff.stakeIndex() == i;
            boolean hot = inRect(mouseX, mouseY, new int[]{bx, stakeY, bw, 15});
            TableArt.button(g, bx, stakeY, bw, 15, on ? 0xFF2C6E49 : 0xFF23382E, hot, true);
            g.drawCenteredString(font, label, bx + bw / 2, stakeY + 4, on ? INK : DIM);
            stakeRects.add(new int[]{bx, stakeY, bw, 15, i});
            bx += bw + 4;
        }

        String deal = "DEAL  ·  " + ClientBluff.stake() + " fragments";
        int dw = Math.min(pw - 24, font.width(deal) + 30);
        int dx = px + (pw - dw) / 2;
        boolean hot = inRect(mouseX, mouseY, new int[]{dx, dealY, dw, 20});
        TableArt.button(g, dx, dealY, dw, 20, 0xFF2C6E49, hot, true);
        g.drawCenteredString(font, fit(deal, dw - 8), dx + dw / 2, dealY + 6, INK);
        newRect = new int[]{dx, dealY, dw, 20};
    }

    /** The other players: a brass name plate and a fan of backs for each. */
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
        int slot = (width - RAIL * 2) / others.size();
        for (int i = 0; i < others.size(); i++) {
            int seat = others.get(i);
            int cx = RAIL + slot * i + slot / 2;
            boolean acting = ClientBluff.turn() == seat;
            boolean pending = ClientBluff.pendingOut() == seat;
            int count = ClientBluff.handSize(seat);
            int accent = pending ? BAD : acting ? BRASS : BRASS_DARK;

            if (acting || pending) {
                // a lamp over whoever the table is waiting on
                int glow = pending ? BAD : BRASS;
                TableArt.pool(g, cx, 30, 46, glow, Math.round(0x22 * (0.55f + 0.45f * breath(620))));
            }

            String name = ClientBluff.seatName(seat).toUpperCase(Locale.ROOT);
            int pw = Math.min(slot - 8, Math.max(58, font.width(name) + 22));
            TableArt.plate(g, cx - pw / 2, RAIL + 4, pw, 22, accent);
            g.drawCenteredString(font, fit(name, pw - 8), cx, RAIL + 7, acting ? INK : DIM);
            String sub = count + (count == 1 ? " card" : " cards");
            g.drawCenteredString(font, sub, cx, RAIL + 17, count <= 2 ? BRASS : FAINT);

            // their hand, turned as if held: a shallow fan of backs
            int show = Math.min(count, 11);
            if (show == 0) {
                continue;
            }
            // capped at 13 so the deepest fan still stops short of the claim
            // board hanging at y=66; a 15px back put the middle seat's cards
            // through the top of it
            int bw = Math.max(9, Math.min(13, (slot - 20) / Math.max(4, show)));
            int bh = Math.round(bw * 1.35f);
            int step = Math.max(4, bw - (show > 6 ? 5 : 3));
            int fanY = RAIL + 36 + bh / 2;
            for (int c = 0; c < show; c++) {
                float t = show == 1 ? 0 : (c / (float) (show - 1)) * 2f - 1f;
                int bx = cx + Math.round(t * (show - 1) * step / 2f);
                int lift = Math.round(3 * (1 - t * t));
                TableArt.back(g, bx, fanY - lift, bw, bh, t * 13f,
                        pending ? BAD : BRASS_DARK);
            }
            if (count > show) {
                // beside the fan, not beneath it — beneath is the claim board
                int span = (show - 1) * step;
                g.drawString(font, "+" + (count - show), cx + span / 2 + 4,
                        fanY - 4, FAINT, false);
            }
        }
    }

    /** The round's claim, on a board hung over the table. */
    private void drawClaim(GuiGraphics g) {
        String text = ClientBluff.claim().text();
        String caption = "EVERY CARD PLAYED SWEARS TO";
        int y = claimY;
        int h = claimH;
        // A short claim used to make a board narrower than its own caption, and
        // "EVERY CARD PLAYED SWEARS TO" hung out over both edges of it. The
        // board is sized to whichever line is wider, and when it has been
        // compressed there is only room for one, so the caption goes.
        boolean captioned = h >= 28;
        int widest = font.width(text);
        if (captioned) {
            widest = Math.max(widest, font.width(caption));
        }
        int w = Math.min(width - 30, widest + 30);
        int x = (width - w) / 2;

        TableArt.pool(g, width / 2, y + h / 2, w, BRASS, 0x1A);
        // the two hangers up to the rail, so it reads as suspended
        for (int hx : new int[]{x + 14, x + w - 15}) {
            g.fill(hx, RAIL, hx + 1, y, BRASS_DARK);
            g.fill(hx, y - 3, hx + 2, y + 1, BRASS_DIM);
        }
        TableArt.plate(g, x, y, w, h, BRASS_DIM);
        g.fill(x + 4, y + 3, x + w - 4, y + 4, BRASS_DARK);
        if (captioned) {
            g.drawCenteredString(font, fit(caption, w - 10), width / 2, y + 6, FAINT);
            g.drawCenteredString(font, fit(text, w - 10), width / 2, y + h - 12, BRASS_HI);
        } else {
            g.drawCenteredString(font, fit(text, w - 10), width / 2, y + (h - 8) / 2, BRASS_HI);
        }
    }

    /** The face-down pile. Its size is what a challenge is played for. */
    private void drawPile(GuiGraphics g) {
        int count = ClientBluff.pile();
        int cy = pileCy;
        if (count > 0 && cy > 0) {
            // a shadow on the felt under the heap
            int spread = Math.min(34, 14 + count);
            g.fill(width / 2 - spread, cy + 8, width / 2 + spread, cy + 15, 0x44000000);
            int show = Math.min(count, 12);
            for (int i = 0; i < show; i++) {
                // deterministic scatter — the same pile must not twitch per frame
                int a = (i * 73) % 31 - 15;
                int b = (i * 149) % 17 - 8;
                float deg = ((i * 97) % 37) - 18;
                TableArt.back(g, width / 2 + a, cy - i + b / 3, 27, 21, deg, BRASS_DARK);
            }
        }
        String label = count == 0 ? "nothing on the table yet"
                : count + (count == 1 ? " card face down" : " cards face down");
        int lw = font.width(label) + 14;
        int lx = width / 2 - lw / 2;
        TableArt.bevel(g, lx, pileCapY, lw, 12, count >= 5 ? 0xFF3A2C10 : 0xFF1A2A22,
                0x33FFFFFF, 0x55000000);
        g.renderOutline(lx, pileCapY, lw, 12, count >= 5 ? BRASS_DIM : BRASS_DARK);
        g.drawCenteredString(font, label, width / 2, pileCapY + 2,
                count >= 5 ? BRASS_HI : DIM);

        int last = ClientBluff.lastCount();
        if (last > 0 && ClientBluff.lastSeat() >= 0) {
            // under the claim board, not above the pile — above the pile put it
            // inside the board's own frame
            if (sworeY >= 0) {
                String who = ClientBluff.seatName(ClientBluff.lastSeat()) + " swore to " + last;
                g.drawCenteredString(font, fit(who, width - 20), width / 2, sworeY, DIM);
            }
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
        int band = revealBand;
        int usable = band - VERDICT_H;
        float scale = band < MIN_REVEAL_BAND ? 0f
                : Math.min(0.30f, Math.min(usable / (float) CardRenderer.CARD_H,
                        (width - 40f) / (shown.size() * (CardRenderer.CARD_W + 14f))));
        if (scale < 0.10f) {
            // No room to turn the cards over on screen — but the outcome is the
            // one thing a player must never be left guessing at. The ribbon
            // takes the pile's caption slot, which this paints over: the count
            // of face-down cards and the verdict on the last play are never
            // both the thing you need to read.
            drawVerdict(g, pileCapY, lying, accent);
            return;
        }
        int cw = Math.round(CardRenderer.CARD_W * scale);
        int ch = Math.round(CardRenderer.CARD_H * scale);
        int span = shown.size() * (cw + 5) - 5;
        int x = (width - span) / 2;
        int y = revealTop + Math.max(0, (usable - ch) / 2);

        TableArt.pool(g, width / 2, y + ch / 2, span, accent, 0x1C);
        for (int i = 0; i < shown.size(); i++) {
            MobCard card = shown.get(i);
            int cx = x + i * (cw + 5);
            boolean matched = ClientBluff.claim().matches(card);
            int edge = matched ? GOOD : BAD;
            g.fill(cx + 1, y + 2, cx + cw + 2, y + ch + 3, 0x66000000);
            LivingEntity mob = CardRenderer.portraitEntity(minecraft, card, entityCache);
            CardRenderer.renderCard(g, font, card, cx, y, scale, -1, -1, mob, false, false);
            g.renderOutline(cx - 1, y - 1, cw + 2, ch + 2, edge);
            if (since < FLASH_MS && (since / 180) % 2 == 0) {
                g.renderOutline(cx - 2, y - 2, cw + 4, ch + 4, TableArt.alpha(edge, 0xAA));
            }
            // a shape as well as a colour, for anyone who cannot separate red
            // from green — and drawn, because the font has no tick glyph
            g.fill(cx + 1, y + 1, cx + 8, y + 8, 0xAA000000);
            TableArt.mark(g, cx + 2, y + 2, matched, edge);
        }
        drawVerdict(g, y + ch + 1, lying, accent);
    }

    /** Who ended up eating what, on a ribbon. */
    private void drawVerdict(GuiGraphics g, int vy, boolean lying, int accent) {
        String verdict = lying
                ? "CAUGHT — " + ClientBluff.revealTaken() + " to "
                        + ClientBluff.seatName(ClientBluff.revealAccused())
                : "HONEST — " + ClientBluff.revealTaken() + " to "
                        + ClientBluff.seatName(ClientBluff.revealChallenger());
        verdict = fit(verdict, width - 30);
        int vw = font.width(verdict) + 14;
        int vx = width / 2 - vw / 2;
        TableArt.bevel(g, vx, vy, vw, 11, TableArt.alpha(accent, 0x33), 0x22FFFFFF, 0x55000000);
        g.drawCenteredString(font, verdict, width / 2, vy + 2, accent);
    }

    /** Your hand, fanned. Picked cards lift clear of the row. */
    private void drawHand(GuiGraphics g, List<MobCard> hand, int mouseX, int mouseY) {
        if (hand.isEmpty()) {
            return;
        }
        // a rail under the hand, to sit the cards on rather than float them
        g.fill(RAIL, handY + cardH + 1, width - RAIL, handY + cardH + 2, BRASS_DARK);
        TableArt.pool(g, width / 2, handY + cardH, width / 2, BRASS, 0x12);

        // topmost card wins the hover, which is the one you can actually see
        for (int i = 0; i < hand.size(); i++) {
            int x = handX + i * gap;
            int visible = i == hand.size() - 1 ? cardW : gap;
            int y = cardY(i, hand.size()) - (picked.contains(i) ? 15 : 0);
            if (mouseX >= x && mouseX < x + visible && mouseY >= y && mouseY < y + cardH) {
                hovered = i;
            }
        }
        for (int i = 0; i < hand.size(); i++) {
            MobCard card = hand.get(i);
            int x = handX + i * gap;
            boolean lifted = picked.contains(i);
            boolean hot = hovered == i;
            int y = cardY(i, hand.size()) - (lifted ? 15 : 0) - (hot && !lifted ? 6 : 0);
            g.fill(x + 2, y + 3, x + cardW + 3, y + cardH + 4, 0x66000000);
            LivingEntity mob = CardRenderer.portraitEntity(minecraft, card, entityCache);
            CardRenderer.renderCard(g, font, card, x, y, handScale, mouseX, mouseY, mob,
                    false, hot);
            if (lifted) {
                int pulse = Math.round(0x50 + 0x40 * breath(500));
                g.renderOutline(x - 2, y - 2, cardW + 4, cardH + 4, BRASS);
                g.renderOutline(x - 3, y - 3, cardW + 6, cardH + 6,
                        TableArt.alpha(BRASS, pulse));
            } else if (hot) {
                g.renderOutline(x - 1, y - 1, cardW + 2, cardH + 2, BRASS_DIM);
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
            g.fill(x + 2, y + 2, x + 7, y + 7, 0xAA000000);
            g.fill(x + 2, y + 2, x + 6, y + 6, tick);
            g.fill(x + 2, y + 2, x + 6, y + 3, 0x66FFFFFF);
        }

        if (hovered >= 0) {
            MobCard card = hand.get(hovered);
            boolean matches = ClientBluff.claim().matches(card);
            String note = card.displayName() + (matches ? "  ·  answers YES" : "  ·  answers NO");
            // exactly the strip between the hand's rail and the button row —
            // at +7 this was printed straight through the buttons
            g.drawCenteredString(font, fit(note, width - 16), width / 2, handY + cardH + 3,
                    matches ? GOOD : BAD);
        }
    }

    /** Swear / Challenge / Let them go. */
    private void drawControls(GuiGraphics g, int mouseX, int mouseY) {
        int y = height - 28;
        if (ClientBluff.over()) {
            return;
        }
        if (!ClientBluff.myTurn()) {
            // the dots are appended, not sliced off the end — slicing ate into
            // "thinking" itself whenever the count was below three
            int dots = (int) ((System.currentTimeMillis() / 400) % 4);
            String who = ClientBluff.seatName(ClientBluff.turn()) + " is thinking"
                    + ".".repeat(ClientPrefs.reducedMotion() ? 3 : dots);
            g.drawCenteredString(font, who, width / 2, y + 5, DIM);
            return;
        }

        boolean pending = ClientBluff.atMatchPoint();
        List<String> labels = new ArrayList<>();
        List<Boolean> on = new ArrayList<>();
        if (pending) {
            labels.add("Let them go");
            on.add(true);
        } else {
            labels.add(picked.isEmpty() ? "Pick cards to swear to" : "SWEAR TO " + picked.size());
            on.add(!picked.isEmpty());
        }
        if (ClientBluff.canChallenge()) {
            labels.add("CHALLENGE");
            on.add(true);
        }

        int total = 0;
        for (String label : labels) {
            total += font.width(label) + 30 + 8;
        }
        int x = (width - total) / 2;
        List<int[]> rects = new ArrayList<>();
        for (int i = 0; i < labels.size(); i++) {
            String label = labels.get(i);
            int bw = font.width(label) + 30;
            int[] rect = {x, y, bw, 20};
            boolean enabled = on.get(i);
            boolean hot = enabled && inRect(mouseX, mouseY, rect);
            boolean danger = label.equals("CHALLENGE");
            if (danger) {
                // the risky move gets a heartbeat, not just a colour
                TableArt.pool(g, x + bw / 2, y + 10, bw, BAD,
                        Math.round(0x1E * (0.4f + 0.6f * breath(560))));
            }
            TableArt.button(g, x, y, bw, 20, danger ? 0xFF8A322B : 0xFF2C6E49, hot, enabled);
            g.drawCenteredString(font, label, x + bw / 2, y + 6, enabled ? INK : FAINT);
            rects.add(rect);
            x += bw + 8;
        }
        int at = 0;
        if (pending) {
            passRect = rects.get(at++);
        } else {
            playRect = rects.get(at++);
        }
        if (ClientBluff.canChallenge() && at < rects.size()) {
            challengeRect = rects.get(at);
        }

        if (pending) {
            String warn = ClientBluff.seatName(ClientBluff.pendingOut())
                    + " is one move from winning — call it, or let it stand";
            g.drawCenteredString(font, fit(warn, width - 20), width / 2, y - 13, BAD);
        }
    }

    /** The last few things that happened, down the left. */
    private void drawLog(GuiGraphics g) {
        List<String> lines = ClientBluff.log();
        if (lines.isEmpty()) {
            return;
        }
        // A lifted card rises fifteen pixels above the hand, so the log stops
        // short of that and not of the resting row — it used to be drawn over
        // the top of whichever cards you had just selected.
        int bottom = handTop() - 2;
        int room = (bottom - revealTop) / 10;
        int max = Math.min(Math.min(5, lines.size()), room);
        if (max <= 0) {
            return;
        }
        int wide = Math.min(146, width / 3);
        int y = bottom - max * 10;
        g.fill(RAIL + 2, y - 4, RAIL + 6 + wide, bottom, 0x40000000);
        g.fill(RAIL + 2, y - 4, RAIL + 3, bottom, BRASS_DARK);
        for (int i = 0; i < max; i++) {
            String line = lines.get(lines.size() - max + i);
            g.drawString(font, fit(line, wide), RAIL + 8, y + i * 10,
                    i == max - 1 ? DIM : FAINT, false);
        }
    }

    private void drawResult(GuiGraphics g, int mouseX, int mouseY) {
        g.fill(0, 0, width, height, 0xC0000000);
        boolean won = ClientBluff.won();
        int accent = won ? GOOD : BAD;
        int pw = Math.min(280, width - 24);
        int ph = 92;
        int px = (width - pw) / 2;
        int py = (height - ph) / 2;
        TableArt.pool(g, width / 2, height / 2, pw, accent, 0x28);
        TableArt.plate(g, px, py, pw, ph, accent);

        var pose = g.pose();
        pose.pushPose();
        pose.translate(width / 2f, py + 12f, 0);
        pose.scale(1.4f, 1.4f, 1f);
        String head = won ? "YOU WENT OUT" : "BEATEN";
        g.drawString(font, head, -font.width(head) / 2, 0, accent, true);
        pose.popPose();

        String sub = won
                ? "+" + (ClientBluff.stake() * ClientBluff.seats()) + " fragments"
                : ClientBluff.seatName(ClientBluff.winner()) + " emptied their hand first";
        g.drawCenteredString(font, fit(sub, pw - 16), width / 2, py + 34, INK);
        g.fill(px + 24, py + 48, px + pw - 24, py + 49, BRASS_DARK);

        String again = "Deal again";
        int bw = font.width(again) + 30;
        int bx = px + (pw - bw) / 2;
        int by = py + ph - 28;
        boolean hot = inRect(mouseX, mouseY, new int[]{bx, by, bw, 20});
        TableArt.button(g, bx, by, bw, 20, 0xFF2C6E49, hot, true);
        g.drawCenteredString(font, again, bx + bw / 2, by + 6, INK);
        newRect = new int[]{bx, by, bw, 20};
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
                click(1.0f);
                return true;
            }
        }
        for (int[] rect : stakeRects) {
            if (inRect(mx, my, rect)) {
                send(BluffActionPayload.stake(rect[4]));
                click(1.0f);
                return true;
            }
        }
        if (newRect != null && inRect(mx, my, newRect)) {
            picked.clear();
            send(BluffActionPayload.newGame());
            click(1.2f);
            return true;
        }
        if (challengeRect != null && inRect(mx, my, challengeRect)) {
            picked.clear();
            send(BluffActionPayload.challenge());
            click(0.7f);
            return true;
        }
        if (passRect != null && inRect(mx, my, passRect)) {
            picked.clear();
            send(BluffActionPayload.pass());
            click(0.9f);
            return true;
        }
        if (playRect != null && inRect(mx, my, playRect) && !picked.isEmpty()) {
            send(BluffActionPayload.play(new ArrayList<>(picked)));
            picked.clear();
            click(1.1f);
            return true;
        }
        if (hovered >= 0 && ClientBluff.myTurn() && !ClientBluff.atMatchPoint()) {
            if (picked.contains(hovered)) {
                picked.remove(hovered);
                click(0.9f);
            } else if (picked.size() < Bluff.MAX_PLAY) {
                picked.add(hovered);
                click(1.4f);
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

    private void click(float pitch) {
        if (minecraft != null) {
            minecraft.getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), pitch));
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
