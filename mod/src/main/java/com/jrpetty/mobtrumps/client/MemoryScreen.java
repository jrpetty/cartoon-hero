package com.jrpetty.mobtrumps.client;

import com.jrpetty.mobtrumps.MemoryActionPayload;
import com.jrpetty.mobtrumps.MemorySyncPayload;
import com.jrpetty.mobtrumps.game.Memory;
import com.jrpetty.mobtrumps.game.MemoryLayout;
import com.jrpetty.mobtrumps.game.MobCard;
import com.jrpetty.mobtrumps.game.MobCards;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * The Memory table: a grid of face-down mob cards.
 *
 * <p>The faces are the real cards, drawn through {@link CardRenderer} exactly
 * as the collection book draws them, and the backs are the mod's own card back
 * — scaled to whatever the board size and window leave room for. A 6x6 board on
 * the smallest window the game allows gets 23x32 pixels a card, which is
 * legible as a mob and not as a stat table; that is the honest consequence of
 * thirty-six cards on a 240-pixel screen, and the arithmetic is checked by
 * tools/checkmemorylayout.py rather than eyeballed.
 *
 * <p>Nothing here decides anything. The screen sends "I clicked tile 14" and
 * draws whatever comes back; the server owns the board.
 */
public class MemoryScreen extends Screen {

    private final List<int[]> hits = new ArrayList<>();   // x, y, w, h, tile
    private MemoryLayout.Grid grid = MemoryLayout.solve(320, 240, 4, 4,
            CardRenderer.CARD_W, CardRenderer.CARD_H);

    public MemoryScreen() {
        super(Component.literal("Memory"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void solve(int cols, int rows) {
        grid = MemoryLayout.solve(width, height, cols, rows,
                CardRenderer.CARD_W, CardRenderer.CARD_H);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        MemorySyncPayload p = ClientMemory.state();
        int phase = p.num(MemorySyncPayload.PHASE);
        hits.clear();

        if (phase == MemorySyncPayload.PHASE_MENU) {
            renderMenu(g, p, mouseX, mouseY);
            super.render(g, mouseX, mouseY, partialTick);
            return;
        }
        renderHeader(g, p);
        renderGrid(g, p, mouseX, mouseY);
        if (phase == MemorySyncPayload.PHASE_OVER) {
            renderResult(g, p, mouseX, mouseY);
        }
        String hint = phase == MemorySyncPayload.PHASE_OVER
                ? "ESC to leave the table"
                : p.num(MemorySyncPayload.PEEK_MS) > 0 ? "Remember them…"
                : p.num(MemorySyncPayload.YOUR_TURN) == 1 ? "Turn over two cards"
                : "Waiting for " + p.text(MemorySyncPayload.T_THEM);
        g.drawCenteredString(font, hint, width / 2, height - MemoryLayout.FOOTER_H + 2, 0xFFBBBBBB);
        super.render(g, mouseX, mouseY, partialTick);
    }

    // --- the size menu ------------------------------------------------------

    private void renderMenu(GuiGraphics g, MemorySyncPayload p, int mouseX, int mouseY) {
        int pw = Math.min(width - 20, 220);
        int ph = 116;
        int px = (width - pw) / 2;
        int py = Math.max(2, (height - ph) / 2);
        g.fill(px - 3, py - 3, px + pw + 3, py + ph + 3, CardRenderer.KRAFT_DARK);
        g.fill(px, py, px + pw, py + ph, CardRenderer.KRAFT);
        g.fill(px + 5, py + 5, px + pw - 5, py + ph - 5, CardRenderer.FACE);
        g.drawCenteredString(font, "MEMORY", width / 2, py + 11, CardRenderer.INK);
        g.drawCenteredString(font, "Turn two cards. Keep the pairs.",
                width / 2, py + 23, 0xFF8B8074);

        int chosen = p.num(MemorySyncPayload.BOARD);
        int y = py + 38;
        for (Memory.BoardSize size : Memory.BoardSize.values()) {
            String label = size.label + "  " + size.cols + "x" + size.rows
                    + "  (" + size.pairs() + " pairs)";
            int bw = pw - 30;
            int bx = px + 15;
            boolean active = size.ordinal() == chosen;
            boolean hover = mouseX >= bx && mouseX < bx + bw && mouseY >= y && mouseY < y + 14;
            g.fill(bx, y, bx + bw, y + 14, active ? 0xFF55A82F : hover ? 0xFFB99465 : CardRenderer.KRAFT);
            g.renderOutline(bx, y, bw, 14, CardRenderer.KRAFT_DARK);
            g.drawString(font, label, bx + 6, y + 3, active ? 0xFFFFFFFF : CardRenderer.INK, false);
            hits.add(new int[]{bx, y, bw, 14, -2 - size.ordinal()});
            y += 17;
        }
        int bw = pw - 30;
        int bx = px + 15;
        boolean hover = mouseX >= bx && mouseX < bx + bw && mouseY >= y && mouseY < y + 14;
        g.fill(bx, y, bx + bw, y + 14, hover ? 0xFF6FD03A : 0xFF3D8B3D);
        g.renderOutline(bx, y, bw, 14, CardRenderer.KRAFT_DARK);
        g.drawCenteredString(font, "Deal", bx + bw / 2, y + 3, 0xFFFFFFFF);
        hits.add(new int[]{bx, y, bw, 14, -1});
        g.drawCenteredString(font, "For two: sit at a dueling table in Memory mode",
                width / 2, py + ph + 8, 0xFF9A9083);
    }

    // --- the board ----------------------------------------------------------

    private void renderHeader(GuiGraphics g, MemorySyncPayload p) {
        boolean solo = p.num(MemorySyncPayload.SOLO) == 1;
        g.fill(0, 0, width, MemoryLayout.HEADER_H - 2, 0x66000000);
        if (solo) {
            String left = "Moves " + p.num(MemorySyncPayload.MOVES);
            String mid = p.num(MemorySyncPayload.SCORE_YOU) + " / "
                    + (p.num(MemorySyncPayload.TILES) / 2) + " pairs";
            g.drawString(font, left, MemoryLayout.PAD + 2, 5, 0xFFE8DCC0, false);
            g.drawCenteredString(font, mid, width / 2, 5, 0xFFFFFFFF);
            String time = clock(p.num(MemorySyncPayload.ELAPSED_S));
            g.drawString(font, time, width - MemoryLayout.PAD - 2 - font.width(time), 5, 0xFFE8DCC0, false);
            return;
        }
        boolean mine = p.num(MemorySyncPayload.YOUR_TURN) == 1;
        drawSide(g, p.text(MemorySyncPayload.T_YOU), p.num(MemorySyncPayload.SCORE_YOU),
                MemoryLayout.PAD + 2, mine, false);
        drawSide(g, p.text(MemorySyncPayload.T_THEM), p.num(MemorySyncPayload.SCORE_THEM),
                width - MemoryLayout.PAD - 2, !mine, true);
        String turn = mine ? "YOUR TURN" : "THEIR TURN";
        g.drawCenteredString(font, turn, width / 2, 4, mine ? 0xFF8CE07A : 0xFFD8A0A0);
        String time = clock(p.num(MemorySyncPayload.ELAPSED_S));
        g.drawCenteredString(font, time, width / 2, 14, 0xFF9A9083);
    }

    private void drawSide(GuiGraphics g, String who, int score, int x, boolean active,
                          boolean rightAligned) {
        String name = who.isEmpty() ? "?" : who;
        String tally = score + (score == 1 ? " pair" : " pairs");
        int nameX = rightAligned ? x - font.width(name) : x;
        int tallyX = rightAligned ? x - font.width(tally) : x;
        g.drawString(font, name, nameX, 4, active ? 0xFFFFE082 : 0xFFBBBBBB, false);
        g.drawString(font, tally, tallyX, 14, active ? 0xFFFFFFFF : 0xFF9A9083, false);
    }

    private static String clock(int seconds) {
        int s = Math.max(0, seconds);
        return String.format("%d:%02d", s / 60, s % 60);
    }

    private void renderGrid(GuiGraphics g, MemorySyncPayload p, int mouseX, int mouseY) {
        int cols = Math.max(1, p.num(MemorySyncPayload.COLS));
        int rows = Math.max(1, p.num(MemorySyncPayload.ROWS));
        int tiles = p.tileCount();
        solve(cols, rows);
        boolean live = p.num(MemorySyncPayload.PHASE) == MemorySyncPayload.PHASE_PLAYING
                && p.num(MemorySyncPayload.YOUR_TURN) == 1
                && p.num(MemorySyncPayload.PEEK_MS) == 0;

        for (int i = 0; i < tiles; i++) {
            // through the grid, so what is drawn and what is clickable cannot
            // be computed two slightly different ways
            int cx = grid.tileX(i, cols);
            int cy = grid.tileY(i, cols);
            int state = p.stateAt(i);
            boolean hoverable = live && state == Memory.HIDDEN;
            boolean hover = hoverable && mouseX >= cx && mouseX < cx + grid.cardW()
                    && mouseY >= cy && mouseY < cy + grid.cardH();
            if (hoverable) {
                hits.add(new int[]{cx, cy, grid.cardW(), grid.cardH(), i});
            }
            drawTile(g, p, i, state, cx, cy, hover, mouseX, mouseY);
        }
    }

    /**
     * One card, mid-turn or settled.
     *
     * <p>The turn is a horizontal squeeze to {@code |1 - 2t|} with the side
     * swapped at the halfway point, which is what makes it read as a card
     * turning over rather than one picture dissolving into another. Only
     * changes that actually change which side is up are animated — a matched
     * pair goes face-up to matched without turning, and animating that flashed
     * the back of the card for a frame.
     */
    private void drawTile(GuiGraphics g, MemorySyncPayload p, int tile, int state,
                          int x, int y, boolean hover, int mouseX, int mouseY) {
        float t = ClientMemory.turning(tile) ? ClientMemory.flipProgress(tile) : 1f;
        boolean showFront = state != Memory.HIDDEN;
        if (t < 0.5f) {
            showFront = !showFront;   // the side it is turning away from
        }
        float squeeze = t >= 1f ? 1f : Math.abs(1f - 2f * t);
        // never squeeze to nothing: a zero-width matrix is a degenerate scale
        squeeze = Math.max(0.02f, squeeze);

        MobCard card = showFront ? MobCards.byId(p.faceAt(tile)) : null;
        var pose = g.pose();
        pose.pushPose();
        // squeeze about the card's own centre line so it turns in place
        pose.translate(x + grid.cardW() / 2f, y, 0);
        pose.scale(squeeze, 1f, 1f);
        pose.translate(-grid.cardW() / 2f, 0, 0);
        if (showFront && card != null) {
            CardRenderer.renderCard(g, font, card, 0, 0, grid.scale(), mouseX, mouseY, null,
                    false, false);
            if (state == Memory.MATCHED) {
                // taken, but still on the table so you can see what has gone
                g.fill(0, 0, grid.cardW(), grid.cardH(), 0x99201808);
            }
        } else if (showFront) {
            // a face we were told about but cannot resolve to a card
            g.fill(0, 0, grid.cardW(), grid.cardH(), CardRenderer.FACE);
            g.renderOutline(0, 0, grid.cardW(), grid.cardH(), CardRenderer.KRAFT_DARK);
        } else {
            CardRenderer.renderBack(g, font, 0, 0, grid.scale());
            if (hover) {
                g.fill(0, 0, grid.cardW(), grid.cardH(), 0x33FFFFFF);
            }
        }
        pose.popPose();
    }

    private void renderResult(GuiGraphics g, MemorySyncPayload p, int mouseX, int mouseY) {
        int result = p.num(MemorySyncPayload.RESULT);
        boolean solo = p.num(MemorySyncPayload.SOLO) == 1;
        String title = solo ? "Board cleared!"
                : switch (result) {
                    case MemorySyncPayload.RESULT_WON -> "You win!";
                    case MemorySyncPayload.RESULT_LOST -> "You lose.";
                    default -> "A draw.";
                };
        String detail = solo
                ? p.num(MemorySyncPayload.MOVES) + " moves in "
                        + clock(p.num(MemorySyncPayload.ELAPSED_S))
                : p.num(MemorySyncPayload.SCORE_YOU) + " – " + p.num(MemorySyncPayload.SCORE_THEM);
        int pw = Math.min(width - 20, 180);
        int ph = 56;
        int px = (width - pw) / 2;
        int py = Math.max(MemoryLayout.HEADER_H, (height - ph) / 2);
        g.fill(px - 3, py - 3, px + pw + 3, py + ph + 3, CardRenderer.KRAFT_DARK);
        g.fill(px, py, px + pw, py + ph, CardRenderer.FACE);
        g.drawCenteredString(font, title, width / 2, py + 8, CardRenderer.INK);
        g.drawCenteredString(font, detail, width / 2, py + 20, 0xFF8B8074);
        int bw = pw - 30;
        int bx = px + 15;
        int by = py + ph - 20;
        boolean hover = mouseX >= bx && mouseX < bx + bw && mouseY >= by && mouseY < by + 14;
        g.fill(bx, by, bx + bw, by + 14, hover ? 0xFF6FD03A : 0xFF3D8B3D);
        g.renderOutline(bx, by, bw, 14, CardRenderer.KRAFT_DARK);
        g.drawCenteredString(font, "Play again", bx + bw / 2, by + 3, 0xFFFFFFFF);
        hits.add(new int[]{bx, by, bw, 14, -3});
    }

    // --- interaction --------------------------------------------------------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            // Later hits are drawn on top, so they are tested first: the result
            // panel's button sits over the grid and must win the click.
            for (int i = hits.size() - 1; i >= 0; i--) {
                int[] h = hits.get(i);
                if (mouseX >= h[0] && mouseX < h[0] + h[2]
                        && mouseY >= h[1] && mouseY < h[1] + h[3]) {
                    act(h[4]);
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void act(int code) {
        if (code >= 0) {
            PacketDistributor.sendToServer(MemoryActionPayload.flip(code));
            click(1.0f);
        } else if (code == -1) {
            PacketDistributor.sendToServer(MemoryActionPayload.start(
                    ClientMemory.state().num(MemorySyncPayload.BOARD)));
            click(1.1f);
        } else if (code == -3) {
            PacketDistributor.sendToServer(MemoryActionPayload.close());
            click(1.0f);
        } else {
            PacketDistributor.sendToServer(MemoryActionPayload.size(-2 - code));
            click(0.9f);
        }
    }

    private void click(float pitch) {
        if (minecraft != null) {
            minecraft.getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), pitch));
        }
    }

    @Override
    public void onClose() {
        // Walking away from a live match forfeits it, which is what the server
        // does with a logout too — leaving the other player waiting on a turn
        // that is never coming is the one outcome worth ruling out.
        PacketDistributor.sendToServer(MemoryActionPayload.quit());
        super.onClose();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** Kept so a resize re-solves rather than drawing at the old scale. */
    @Override
    public void resize(net.minecraft.client.Minecraft mc, int w, int h) {
        super.resize(mc, w, h);
        MemorySyncPayload p = ClientMemory.state();
        solve(Math.max(1, p.num(MemorySyncPayload.COLS)),
                Math.max(1, p.num(MemorySyncPayload.ROWS)));
    }
}
