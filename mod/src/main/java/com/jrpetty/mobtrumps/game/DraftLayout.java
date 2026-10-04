package com.jrpetty.mobtrumps.game;

/**
 * Where everything on the draft screen goes, as plain arithmetic the harness
 * can sweep across every window size.
 *
 * <pre>
 *  ┌──────────── header: DRAFT · pick 3 of 6 · whose turn ────────────┐
 *  │  ═════════════════════ the pick clock ══════════════════════════  │
 *  │   ┌──┐ ┌──┐ ┌──┐ ┌──┐ ┌──┐ ┌──┐ ┌──┐ ┌──┐                       │
 *  │   │  │ │  │ │  │ │  │ │  │ │  │ │  │ │  │    the pool, face up  │
 *  │   └──┘ └──┘ └──┘ └──┘ └──┘ └──┘ └──┘ └──┘                       │
 *  │   YOUR PICKS  ▯▯▯▯▯▯              THEIR PICKS  ▯▯▯▯▯▯            │
 *  └──────────────────────────────── hint ··················· Leave ─┘
 * </pre>
 *
 * <p>The pool grid takes whichever column count gives the largest cards, so
 * it grows as the pool empties.
 */
public final class DraftLayout {

    public static final int CARD_W = 170;
    public static final int CARD_H = 236;
    public static final int HEADER_H = 30;
    public static final int FOOTER_H = 24;
    public static final int PAD = 8;
    public static final int CELL_GAP = 4;
    /** "YOUR PICKS" over each row of picks. */
    public static final int LABEL_H = 11;

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
    }

    /**
     * @param cols      columns in the pool grid
     * @param poolScale the pool cards' scale
     * @param miniScale the scale of the cards in the two rows of picks
     */
    public record Layout(Rect header, Rect clock, Rect pool, int cols, float poolScale,
                         Rect mine, Rect theirs, float miniScale, Rect footer, Rect leave) {

        /** The {@code i}-th pool card's rectangle. */
        public Rect poolCard(int i, int count) {
            int cw = (int) (CARD_W * poolScale);
            int ch = (int) (CARD_H * poolScale);
            int rows = Math.max(1, (count + cols - 1) / cols);
            int inRow = Math.min(cols, count - (i / cols) * cols);
            int gridH = rows * ch + (rows - 1) * CELL_GAP;
            int rowW = inRow * cw + (inRow - 1) * CELL_GAP;
            int x0 = pool.x() + (pool.w() - rowW) / 2;
            int y0 = pool.y() + (pool.h() - gridH) / 2;
            return new Rect(x0 + (i % cols) * (cw + CELL_GAP), y0 + (i / cols) * (ch + CELL_GAP), cw, ch);
        }

        /** The {@code i}-th slot in a row of picks. */
        public Rect slot(Rect row, int i) {
            int w = (int) (CARD_W * miniScale);
            int h = (int) (CARD_H * miniScale);
            return new Rect(row.x() + i * (w + 3), row.y() + LABEL_H, w, h);
        }
    }

    private DraftLayout() {
    }

    public static Layout solve(int width, int height, int poolCount, int picksEach) {
        Rect header = new Rect(0, 0, width, HEADER_H);
        Rect clock = new Rect(PAD, HEADER_H + 2, Math.max(0, width - 2 * PAD), 3);
        Rect footer = new Rect(0, height - FOOTER_H, width, FOOTER_H);
        Rect leave = new Rect(width - PAD - 56, footer.y() + 3, 56, 18);

        // the two rows of picks share one band across the bottom, side by side
        int half = (width - 3 * PAD) / 2;
        float mini = Math.min(0.20f, (half - 3f * (picksEach - 1)) / (picksEach * (float) CARD_W));
        mini = Math.max(0.08f, mini);
        int miniH = (int) (CARD_H * mini);
        int picksH = LABEL_H + miniH;
        int picksY = footer.y() - 4 - picksH;
        Rect mine = new Rect(PAD, picksY, half, picksH);
        Rect theirs = new Rect(2 * PAD + half, picksY, half, picksH);

        Rect pool = new Rect(PAD, clock.bottom() + 6, Math.max(0, width - 2 * PAD),
                Math.max(0, picksY - 6 - (clock.bottom() + 6)));
        int bestCols = 1;
        float best = 0;
        int n = Math.max(1, poolCount);
        for (int cols = 1; cols <= n; cols++) {
            int rows = (n + cols - 1) / cols;
            float byW = (pool.w() - (cols - 1) * CELL_GAP) / (cols * (float) CARD_W);
            float byH = (pool.h() - (rows - 1) * CELL_GAP) / (rows * (float) CARD_H);
            float s = Math.min(byW, byH);
            if (s > best) {
                best = s;
                bestCols = cols;
            }
        }
        float poolScale = Math.max(0.05f, Math.min(best, 0.75f));
        return new Layout(header, clock, pool, bestCols, poolScale, mine, theirs, mini, footer, leave);
    }
}
