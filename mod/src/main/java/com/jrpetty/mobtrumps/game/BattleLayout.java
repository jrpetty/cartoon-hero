package com.jrpetty.mobtrumps.game;

/**
 * Where everything on the battle screen goes, as plain arithmetic.
 *
 * <p>The table reads left to right as your hand, your card, the stat board,
 * their card and their hand:
 *
 * <pre>
 *  ┌──────────────── header: title · ROUND chip · opponent ────────────────┐
 *  │ ┌───────────────────────────── felt ────────────────────────────────┐ │
 *  │ │       [ YOU x6 ]            [ status ]           [ CPU x6 ]       │ │
 *  │ │  ▯   ┌────────┐   ┌────────────────────┐   ┌────────┐   ▯        │ │
 *  │ │  ▯   │  your  │   │  1 HEALTH   7 : 9  │   │ their  │   ▯        │ │
 *  │ │  ▯   │  card  │   │  2 ATTACK  12 : 4  │   │  card  │   ▯        │ │
 *  │ │  ▯   │        │   │  ...  · rounds ·   │   │        │   ▯        │ │
 *  │ │      └────────┘   └────────────────────┘   └────────┘            │ │
 *  │ └───────────────────────────────────────────────────────────────────┘ │
 *  └──── dock: card size · emote ·    [ primary ]    · auto · leave ─────┘
 * </pre>
 *
 * <p>Kept out of the screen class so the harness can sweep every window size
 * and prove nothing overlaps — the battle screen has shipped elements drawn on
 * top of each other more than once, each time at a size nobody tried. The
 * stat board is what makes the small windows work: the stats on a card at a
 * third of its size are too small to read, so the board shows them at full
 * text size beside it.
 */
public final class BattleLayout {

    public static final int CARD_W = 170;
    public static final int CARD_H = 236;
    public static final int HEADER_H = 26;
    public static final int DOCK_H = 30;
    /** Felt inset from the header, dock and window edges. */
    public static final int MARGIN = 6;
    /** Space between the columns of the table. */
    public static final int GAP = 8;
    /** The nameplate riding above each card. */
    public static final int PLATE_H = 13;
    public static final int PLATE_GAP = 3;
    /** The status chip over the board, level with the nameplates. */
    public static final int STATUS_H = 13;
    public static final int BOARD_MIN_W = 112;
    public static final int BOARD_MAX_W = 156;
    /** One stat row on the board: room for an 8px line of text and a bar. */
    public static final int ROW_MIN_H = 11;
    public static final int ROW_MAX_H = 18;
    /** The board's footer: the pot and the strip of past rounds. */
    public static final int FOOTER_H = 20;
    /** Below this the cards would be unreadable even beside the board. */
    public static final float MIN_SCALE = 0.30f;
    public static final float MAX_SCALE = 1.05f;
    /** Dock buttons are this tall, centred in the dock. */
    public static final int BUTTON_H = 18;

    public record Rect(int x, int y, int w, int h) {
        public int right() {
            return x + w;
        }

        public int bottom() {
            return y + h;
        }

        public boolean overlaps(Rect o) {
            return x < o.right() && o.x < right() && y < o.bottom() && o.y < bottom();
        }

        public boolean inside(Rect o) {
            return x >= o.x && y >= o.y && right() <= o.right() && bottom() <= o.bottom();
        }

        public boolean contains(double px, double py) {
            return px >= x && px < right() && py >= y && py < bottom();
        }

        public int midX() {
            return x + w / 2;
        }

        public int midY() {
            return y + h / 2;
        }
    }

    /**
     * Everything the screen draws, solved for one window.
     *
     * @param scale    the big cards' scale
     * @param miniScale the scale of the small cards in the hand columns
     * @param hands    whether there was room for the hand columns at all
     * @param rowH     the height of one stat row on the board
     */
    public record Layout(float scale, float miniScale, boolean hands, int rowH,
                         Rect header, Rect felt, Rect dock,
                         Rect myCard, Rect oppCard, Rect myPlate, Rect oppPlate,
                         Rect board, Rect status, Rect rows, Rect footer,
                         Rect myHand, Rect oppHand, Rect panel,
                         Rect sizeButton, Rect emoteButton, Rect primary, Rect autoButton,
                         Rect leaveButton) {

        /** One stat row on the board, top to bottom in Stat order. */
        public Rect row(int i) {
            return new Rect(rows.x(), rows.y() + i * rowH, rows.w(), rowH);
        }
    }

    private BattleLayout() {
    }

    /**
     * Solve the table for a {@code width} x {@code height} window.
     *
     * @param scaleCap the largest card scale the player's size setting allows
     * @param pvp      a duel: the dock has an emote button and no auto-continue
     */
    public static Layout solve(int width, int height, float scaleCap, boolean pvp) {
        Rect header = new Rect(0, 0, width, HEADER_H);
        Rect dock = new Rect(0, height - DOCK_H, width, DOCK_H);
        Rect felt = new Rect(MARGIN, HEADER_H + MARGIN, Math.max(0, width - 2 * MARGIN),
                Math.max(0, height - HEADER_H - DOCK_H - 2 * MARGIN));

        int boardW = clamp(Math.round(felt.w() * 0.28f), BOARD_MIN_W, BOARD_MAX_W);
        // the cards and the board share the felt below the nameplate band
        int top = felt.y() + 4 + PLATE_H + PLATE_GAP;
        int availH = felt.bottom() - 4 - top;
        float byH = availH / (float) CARD_H;

        // first with the hand columns; if they squeeze the cards too far, without
        boolean hands = true;
        float scale = 0;
        float mini = 0;
        int handW = 0;
        for (int attempt = 0; attempt < 2; attempt++) {
            hands = attempt == 0;
            float provisional = Math.min(byH, MAX_SCALE);
            mini = clampF(provisional * 0.25f, 0.11f, 0.20f);
            handW = hands ? Math.round(CARD_W * mini) : 0;
            int columns = hands ? 4 : 2;
            int spare = felt.w() - 8 - boardW - columns * GAP - 2 * handW;
            float byW = spare / (2f * CARD_W);
            scale = Math.min(Math.min(byH, byW), Math.min(scaleCap, MAX_SCALE));
            if (!hands || scale >= 0.40f) {
                break;
            }
        }
        scale = Math.max(MIN_SCALE, scale);
        if (hands) {
            mini = clampF(scale * 0.25f, 0.11f, 0.20f);
            handW = Math.round(CARD_W * mini);
        }

        int cardW = Math.round(CARD_W * scale);
        int cardH = Math.round(CARD_H * scale);
        // the board is at least as tall as it needs to be, and grows with the card
        int boardH = Math.max(cardH, STATUS_H + 2 + 6 * ROW_MIN_H + FOOTER_H);
        boardH = Math.min(boardH, Math.max(0, felt.bottom() - 4 - top));
        int rowsH = boardH - FOOTER_H;
        int rowH = clamp(rowsH / 6, ROW_MIN_H, ROW_MAX_H);

        int total = 2 * cardW + boardW + 2 * GAP + (hands ? 2 * (handW + GAP) : 0);
        int x = felt.x() + Math.max(4, (felt.w() - total) / 2);
        int cardY = top + Math.max(0, (availH - cardH) / 2);
        // level with the cards' tops where the board fits that way; a board
        // taller than the cards rises only as far as it has to
        int boardY = Math.max(top, Math.min(cardY, top + availH - boardH));

        Rect myHand = null;
        if (hands) {
            myHand = new Rect(x, cardY, handW, cardH);
            x += handW + GAP;
        }
        Rect myCard = new Rect(x, cardY, cardW, cardH);
        x += cardW + GAP;
        Rect board = new Rect(x, boardY, boardW, boardH);
        x += boardW + GAP;
        Rect oppCard = new Rect(x, cardY, cardW, cardH);
        x += cardW + GAP;
        Rect oppHand = hands ? new Rect(x, cardY, handW, cardH) : null;

        Rect myPlate = new Rect(myCard.x(), myCard.y() - PLATE_GAP - PLATE_H, cardW, PLATE_H);
        Rect oppPlate = new Rect(oppCard.x(), oppCard.y() - PLATE_GAP - PLATE_H, cardW, PLATE_H);
        // the status chip sits over the board, level with the nameplates — or
        // just above the board, if a tall board has risen past them
        Rect status = new Rect(board.x(), Math.min(myPlate.y(), board.y() - PLATE_GAP - STATUS_H),
                board.w(), STATUS_H);
        Rect rows = new Rect(board.x() + 2, board.y() + 2, board.w() - 4, 6 * rowH);
        Rect footer = new Rect(board.x() + 2, rows.bottom() + 1, board.w() - 4,
                Math.max(0, board.bottom() - rows.bottom() - 2));

        int panelW = Math.min(felt.w() - 12, 300);
        int panelH = Math.min(felt.h() - 8, 172);
        Rect panel = new Rect(felt.x() + (felt.w() - panelW) / 2, felt.y() + (felt.h() - panelH) / 2,
                panelW, panelH);

        // --- the dock: a group on each side, the primary action between them ---
        int by = dock.y() + (DOCK_H - BUTTON_H) / 2;
        int edge = 6;
        int sizeW = width >= 380 ? 66 : 46;
        Rect sizeButton = new Rect(edge, by, sizeW, BUTTON_H);
        Rect emoteButton = pvp ? new Rect(sizeButton.right() + 4, by, 44, BUTTON_H) : null;
        int leaveW = 52;
        Rect leaveButton = new Rect(width - edge - leaveW, by, leaveW, BUTTON_H);
        Rect autoButton = pvp ? null : new Rect(leaveButton.x() - 4 - 48, by, 48, BUTTON_H);
        int leftEnd = (emoteButton != null ? emoteButton : sizeButton).right() + 6;
        int rightStart = (autoButton != null ? autoButton : leaveButton).x() - 6;
        int primaryW = Math.max(0, Math.min(124, rightStart - leftEnd));
        int primaryX = clamp(width / 2 - primaryW / 2, leftEnd, Math.max(leftEnd, rightStart - primaryW));
        Rect primary = new Rect(primaryX, by, primaryW, BUTTON_H);

        return new Layout(scale, mini, hands, rowH, header, felt, dock, myCard, oppCard, myPlate, oppPlate,
                board, status, rows, footer, myHand, oppHand, panel,
                sizeButton, emoteButton, primary, autoButton, leaveButton);
    }

    /**
     * Where the {@code i}-th of {@code count} small cards sits in a hand column,
     * overlapping when the column is too short to show them all whole.
     */
    public static Rect mini(Rect column, float miniScale, int i, int count) {
        int w = Math.round(CARD_W * miniScale);
        int h = Math.round(CARD_H * miniScale);
        int step = count <= 1 ? 0 : Math.min(h + 2, (column.h() - h) / (count - 1));
        return new Rect(column.x(), column.y() + i * Math.max(1, step), w, h);
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static float clampF(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
