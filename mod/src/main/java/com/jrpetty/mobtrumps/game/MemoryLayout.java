package com.jrpetty.mobtrumps.game;

/**
 * Where the Memory cards go.
 *
 * <p>This lives in the Minecraft-free package on purpose. It began as a private
 * method on the screen with a Python script that re-implemented the same
 * arithmetic and swept it for overflows — and that script could not see a
 * single change to the algorithm it was supposedly guarding. Rounding the card
 * size up instead of down, and dropping the height budget out of the scale
 * entirely, both left it reporting a confident pass. A check that re-implements
 * the thing it is checking is only ever testing itself.
 *
 * <p>Here, the regression harness runs THIS code, so a change to the solve is a
 * change to what is being swept.
 */
public final class MemoryLayout {

    /** Chrome budgeted before any card is placed. */
    public static final int HEADER_H = 26;
    public static final int FOOTER_H = 12;
    public static final int PAD = 4;
    public static final int GAP = 2;
    /** Cards never draw larger than their natural size, however big the window. */
    public static final float SCALE_CAP = 1.0f;

    /** A solved board: where the grid sits and how big a card is. */
    public record Grid(float scale, int cardW, int cardH, int gridW, int gridH,
                       int gridX, int gridY) {

        public int tileX(int index, int cols) {
            return gridX + (index % cols) * (cardW + GAP);
        }

        public int tileY(int index, int cols) {
            return gridY + (index / cols) * (cardH + GAP);
        }
    }

    private MemoryLayout() {
    }

    /**
     * Fit {@code cols}x{@code rows} cards into a window.
     *
     * <p>The cards take what is left after the header and footer and shrink as
     * the board grows, so Hard is not a special case somebody has to remember
     * to re-check. Sizes are FLOORED rather than rounded: rounding a card up
     * can push the last row a pixel past the bottom of the band, where it is
     * invisible and — because the click test uses these same numbers —
     * unclickable. Flooring makes {@code rows * (cardH + GAP) - GAP <= availH}
     * true by construction rather than nearly always.
     */
    public static Grid solve(int width, int height, int cols, int rows,
                             int cardWidth, int cardHeight) {
        int c = Math.max(1, cols);
        int r = Math.max(1, rows);
        int availW = width - 2 * PAD;
        int availH = height - HEADER_H - FOOTER_H;
        float byW = ((float) availW / c - GAP) / cardWidth;
        float byH = ((float) availH / r - GAP) / cardHeight;
        float scale = Math.max(0.02f, Math.min(SCALE_CAP, Math.min(byW, byH)));
        int cw = Math.max(1, (int) Math.floor(cardWidth * scale));
        int ch = Math.max(1, (int) Math.floor(cardHeight * scale));
        int gridW = c * (cw + GAP) - GAP;
        int gridH = r * (ch + GAP) - GAP;
        int gridX = (width - gridW) / 2;
        int gridY = HEADER_H + Math.max(0, (availH - gridH) / 2);
        return new Grid(scale, cw, ch, gridW, gridH, gridX, gridY);
    }

    /**
     * Is every card of this board on screen and therefore clickable?
     *
     * <p>The invariant the sweep asserts, expressed once so the harness cannot
     * check something subtly different from what the screen relies on.
     */
    public static boolean fits(Grid grid, int width, int height) {
        return grid.cardW() >= 1 && grid.cardH() >= 1
                && grid.gridX() >= 0 && grid.gridX() + grid.gridW() <= width
                && grid.gridY() >= HEADER_H
                && grid.gridY() + grid.gridH() <= height - FOOTER_H;
    }
}
