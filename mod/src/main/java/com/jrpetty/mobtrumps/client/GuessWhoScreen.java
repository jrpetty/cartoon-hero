package com.jrpetty.mobtrumps.client;

import com.jrpetty.mobtrumps.GuessWhoActionPayload;
import com.jrpetty.mobtrumps.GuessWhoManager;
import com.jrpetty.mobtrumps.game.GuessQuestion;
import com.jrpetty.mobtrumps.game.MobCard;
import com.jrpetty.mobtrumps.game.MobCards;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The Guess Who board: eighty-one faces, one of them hidden, and a list of
 * questions to narrow it down with.
 *
 * <p><b>Nothing here decides what gets eliminated.</b> A question goes to the
 * server, which answers it against the mob it is hiding and sends back the faces
 * that survive; this screen draws that set. So the striking-off cannot be
 * skipped, fudged or miscounted — the satisfying part of Guess Who is knocking
 * the faces down, and the trustworthy part is that you are not the one deciding
 * which.
 *
 * <p>A face that has just been eliminated turns over rather than vanishing, so
 * you can see the answer sweep across the board. Everything is laid out from the
 * space available: the tiles shrink and the question panel narrows before
 * anything is allowed off-screen.
 */
public class GuessWhoScreen extends Screen {

    private static final int BACK_1 = 0xFF241F33;
    private static final int BACK_0 = 0xFF120F1C;
    private static final int GOLD = 0xFFE9C46A;
    private static final int GOLD_DIM = 0xFF8A6A2A;
    private static final int INK = 0xFFF2ECDD;
    private static final int DIM = 0xFF8C86A0;
    private static final int FAINT = 0xFF564F68;
    private static final int YES = 0xFF6BE87A;
    private static final int NO = 0xFFF0625A;
    private static final int PANEL = 0xE014101F;
    private static final int EDGE = 0xFF35304A;

    /** How long a struck-off face takes to turn over. */
    private static final long FLIP_MS = 260L;
    /** Stagger across the board, so the answer sweeps rather than snaps. */
    private static final long SWEEP_MS = 320L;

    private final Map<String, LivingEntity> entityCache = new HashMap<>();
    private EditBox search;

    // solved per frame
    private int tileW;
    private int tileH;
    private int cols;
    private int boardX;
    private int boardY;
    private int panelX;
    private int panelW;
    private float tileScale;

    private final List<int[]> questionRects = new ArrayList<>();
    private final List<Integer> questionIndex = new ArrayList<>();
    private int[] newGameRect;
    private int[] confirmRect;
    private int[] cancelRect;
    private int scroll;
    /** Value chosen for each threshold row, remembered while the screen is open. */
    private final Map<Integer, Integer> values = new HashMap<>();
    /** A face clicked but not yet confirmed — naming the wrong one ends the game. */
    private String pendingGuess;
    private String hoveredMob;

    public GuessWhoScreen() {
        super(Component.literal("Guess Who"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        super.init();
        panelW = Mth.clamp(width / 3, 150, 216);
        panelX = width - panelW - 8;
        search = new EditBox(font, panelX + 4, 42, panelW - 8, 14,
                Component.literal("search"));
        search.setHint(Component.literal("search questions…"));
        search.setMaxLength(32);
        search.setResponder(s -> scroll = 0);
        addRenderableWidget(search);
    }

    /**
     * Fit an 81-tile board into whatever space is left of the question panel.
     *
     * <p>Biggest tiles first, and among the arrangements that fit, the fewest
     * columns — a board wants to look like a board. Taking the widest row that
     * fits gave 46 across and 2 down on a large window, which is a strip of
     * faces rather than something you can scan.
     */
    private void solveBoard() {
        int availW = panelX - 16;
        int availH = height - 74;
        int total = MobCards.ALL.size();
        for (float scale = 0.28f; scale >= 0.055f; scale -= 0.005f) {
            int tw = Math.max(8, Math.round(CardRenderer.CARD_W * scale));
            int th = Math.max(11, Math.round(CardRenderer.CARD_H * scale));
            int maxCols = Math.max(1, (availW + 2) / (tw + 2));
            int maxRows = Math.max(1, (availH + 2) / (th + 2));
            // of the arrangements that fit, the one whose shape best matches the
            // space it sits in — so the board fills the area rather than becoming
            // a long strip on a wide window or a column on a tall one
            int bestCols = -1;
            double bestGap = Double.MAX_VALUE;
            for (int c = 1; c <= maxCols; c++) {
                int rows = (total + c - 1) / c;
                if (rows > maxRows) {
                    continue;
                }
                double boardAspect = (c * (tw + 2) - 2) / (double) Math.max(1, rows * (th + 2) - 2);
                double gap = Math.abs(Math.log(boardAspect / (availW / (double) availH)));
                if (gap < bestGap) {
                    bestGap = gap;
                    bestCols = c;
                }
            }
            if (bestCols > 0) {
                int rows = (total + bestCols - 1) / bestCols;
                tileScale = scale;
                tileW = tw;
                tileH = th;
                cols = bestCols;
                boardX = 8 + Math.max(0, (availW - (bestCols * (tw + 2) - 2)) / 2);
                boardY = 40 + Math.max(0, (availH - rows * (th + 2)) / 2);
                return;
            }
        }
        tileScale = 0.055f;
        tileW = Math.max(8, Math.round(CardRenderer.CARD_W * tileScale));
        tileH = Math.max(11, Math.round(CardRenderer.CARD_H * tileScale));
        cols = Math.max(1, (availW + 2) / (tileW + 2));
        boardX = 8;
        boardY = 40;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.fillGradient(0, 0, width, height, BACK_1, BACK_0);
        g.fill(0, 0, width, 2, GOLD_DIM);
        g.fill(0, height - 2, width, height, GOLD_DIM);

        solveBoard();
        long since = System.currentTimeMillis() - ClientGuessWho.changedAt();
        boolean playing = ClientGuessWho.playing();

        drawHeader(g);
        drawBoard(g, mouseX, mouseY, since, playing);
        drawPanel(g, mouseX, mouseY, playing);
        drawFooter(g, mouseX, mouseY, playing);
        if (ClientGuessWho.picking()) {
            // nothing to ask yet — the panel is a preview of what is coming
            g.fill(panelX, 34, panelX + panelW, height - 26, 0x99000000);
            g.drawString(font, "questions unlock", panelX + 8, height / 2 - 10, DIM, false);
            g.drawString(font, "once both have hidden", panelX + 8, height / 2, DIM, false);
        }
        // last, so it sits over the panel and the picking veil — while you are
        // choosing your own hidden mob is exactly when you want a good look
        drawPreview(g);
    }

    private void drawHeader(GuiGraphics g) {
        var pose = g.pose();
        pose.pushPose();
        pose.translate(10f, 6f, 0);
        pose.scale(1.5f, 1.5f, 1f);
        g.drawString(font, "GUESS WHO", 0, 0, GOLD, true);
        pose.popPose();

        if (ClientGuessWho.picking()) {
            g.drawString(font, "PICK THE MOB "
                            + ClientGuessWho.opponentName().toUpperCase(java.util.Locale.ROOT)
                            + " HAS TO FIND", 12, 26, GOLD, false);
            return;
        }
        int left = ClientGuessWho.aliveCount();
        String count = left + (left == 1 ? " face left" : " faces left");
        g.drawString(font, count, 12, 26, left <= 2 ? GOLD : DIM, false);
        int x = 12 + font.width(count) + 12;
        String asked = ClientGuessWho.asked() + " asked";
        g.drawString(font, asked, x, 26, DIM, false);
        x += font.width(asked) + 14;

        if (ClientGuessWho.versusPlayer()) {
            // how the other side is getting on, so a race feels like a race
            String them = "vs " + ClientGuessWho.opponentName() + " — "
                    + ClientGuessWho.opponentLeft() + " left, "
                    + ClientGuessWho.opponentAsked() + " asked";
            g.drawString(font, them, x, 26, FAINT, false);
            String turn = ClientGuessWho.waitingOnThem() ? "THEIR TURN"
                    : ClientGuessWho.playing() ? "YOUR TURN" : "";
            if (!turn.isEmpty()) {
                boolean mine = ClientGuessWho.playing();
                int tw = font.width(turn) + 10;
                int tx = panelX - tw - 8;
                g.fill(tx, 22, tx + tw, 34, mine ? 0x442E7D46 : 0x44000000);
                g.renderOutline(tx, 22, tw, 12, mine ? YES : EDGE);
                g.drawString(font, turn, tx + 5, 24, mine ? YES : DIM, false);
            }
        }
    }

    /** The eighty-one faces. Struck-off ones turn face down where they stand. */
    private void drawBoard(GuiGraphics g, int mouseX, int mouseY, long since, boolean playing) {
        hoveredMob = null;
        List<MobCard> all = MobCards.ALL;
        String secret = ClientGuessWho.secret();
        for (int i = 0; i < all.size(); i++) {
            MobCard card = all.get(i);
            int x = boardX + (i % cols) * (tileW + 2);
            int y = boardY + (i / cols) * (tileH + 2);
            boolean alive = ClientGuessWho.isAlive(card.id());
            boolean going = ClientGuessWho.justEliminated(card.id());

            // the sweep: tiles further along the board turn a moment later
            long delay = going ? (long) (i % cols) * SWEEP_MS / Math.max(1, cols) : 0L;
            float flip = going
                    ? Mth.clamp((since - delay) / (float) FLIP_MS, 0f, 1f)
                    : (alive ? 0f : 1f);

            boolean isSecret = !secret.isEmpty() && secret.equals(card.id());
            boolean selectable = playing || ClientGuessWho.picking();
            boolean hover = selectable && alive && mouseX >= x && mouseX < x + tileW
                    && mouseY >= y && mouseY < y + tileH;
            if (hover) {
                hoveredMob = card.id();
            }

            var pose = g.pose();
            pose.pushPose();
            // squeeze horizontally through the turn, so it reads as a card flipping
            float squeeze = flip < 0.5f ? 1f - flip * 2f : (flip - 0.5f) * 2f;
            pose.translate(x + tileW / 2f, y + tileH / 2f, 0);
            pose.scale(Math.max(0.02f, squeeze), 1f, 1f);
            if (hover) {
                pose.scale(1.12f, 1.12f, 1f);
            }
            pose.translate(-tileW / 2f, -tileH / 2f, 0);
            if (flip < 0.5f) {
                LivingEntity mob = CardRenderer.portraitEntity(minecraft, card, entityCache);
                CardRenderer.renderCard(g, font, card, 0, 0, tileScale, -1, -1, mob, false, false);
            } else {
                CardRenderer.renderBack(g, font, 0, 0, tileScale);
                g.fill(0, 0, tileW, tileH, 0x77000000);
            }
            pose.popPose();

            if (isSecret) {
                // the answer, once it is over
                g.renderOutline(x - 1, y - 1, tileW + 2, tileH + 2, GOLD);
                g.renderOutline(x - 2, y - 2, tileW + 4, tileH + 4, 0x66E9C46A);
            } else if (card.id().equals(pendingGuess)) {
                g.renderOutline(x - 1, y - 1, tileW + 2, tileH + 2, NO);
            } else if (hover) {
                g.renderOutline(x - 1, y - 1, tileW + 2, tileH + 2, GOLD_DIM);
            }
        }

    }

    /** Search box, the question catalogue, and the answers so far. */
    private void drawPanel(GuiGraphics g, int mouseX, int mouseY, boolean playing) {
        g.fill(panelX, 34, panelX + panelW, height - 26, PANEL);
        g.renderOutline(panelX, 34, panelW, height - 60, EDGE);
        g.fill(panelX, 34, panelX + panelW, 36, GOLD_DIM);

        List<GuessQuestion.Template> hits = GuessQuestion.search(search.getValue());
        int listTop = 60;
        int logH = Math.min(64, Math.max(0, (height - 26 - listTop) / 3));
        int listBottom = height - 30 - logH;
        int rowH = 16;
        int visible = Math.max(1, (listBottom - listTop) / rowH);
        scroll = Mth.clamp(scroll, 0, Math.max(0, hits.size() - visible));

        questionRects.clear();
        questionIndex.clear();
        for (int r = 0; r < visible && scroll + r < hits.size(); r++) {
            GuessQuestion.Template t = hits.get(scroll + r);
            int idx = GuessQuestion.TEMPLATES.indexOf(t);
            int y = listTop + r * rowH;
            int value = valueFor(idx, t);
            boolean useful = playing
                    && GuessQuestion.isUseful(aliveCards(), t, t.needsValue() ? value : 0);
            boolean over = mouseX >= panelX + 3 && mouseX < panelX + panelW - 3
                    && mouseY >= y && mouseY < y + rowH - 1;

            g.fill(panelX + 3, y, panelX + panelW - 3, y + rowH - 1,
                    !useful ? 0x14000000 : over ? 0x3AE9C46A : 0x22FFFFFF);
            if (useful && over) {
                g.renderOutline(panelX + 3, y, panelW - 6, rowH - 1, GOLD);
            }

            int textColour = useful ? INK : 0xFF4A4560;
            if (t.needsValue()) {
                // "Is its Health less than [ 5 ]?" — the number sits in its own box
                String[] parts = t.label().split("%d", 2);
                int tx = panelX + 7;
                String head = fit(parts[0], panelW - 46);
                g.drawString(font, head, tx, y + 4, textColour, false);
                int bx = tx + font.width(head) + 1;
                g.fill(bx, y + 2, bx + 15, y + rowH - 3, 0xFF0B0812);
                g.renderOutline(bx, y + 2, 15, rowH - 5, useful ? GOLD_DIM : EDGE);
                String v = String.valueOf(value);
                g.drawString(font, v, bx + 8 - font.width(v) / 2, y + 4,
                        useful ? GOLD : 0xFF4A4560, false);
                if (parts.length > 1) {
                    g.drawString(font, fit(parts[1], panelW - 30), bx + 17, y + 4,
                            textColour, false);
                }
                questionRects.add(new int[]{panelX + 3, y, panelW - 6, rowH - 1, bx, 15});
            } else {
                g.drawString(font, fit(t.label(), panelW - 14), panelX + 7, y + 4,
                        textColour, false);
                questionRects.add(new int[]{panelX + 3, y, panelW - 6, rowH - 1, -1, 0});
            }
            questionIndex.add(idx);
        }

        if (hits.isEmpty()) {
            g.drawString(font, "nothing matches", panelX + 8, listTop + 4, FAINT, false);
        }
        if (hits.size() > visible) {
            int trackH = listBottom - listTop;
            g.fill(panelX + panelW - 5, listTop, panelX + panelW - 3, listTop + trackH, 0x30FFFFFF);
            int thumb = Math.max(12, trackH * visible / hits.size());
            int ty = listTop + (int) ((long) (trackH - thumb) * scroll
                    / Math.max(1, hits.size() - visible));
            g.fill(panelX + panelW - 5, ty, panelX + panelW - 3, ty + thumb, GOLD_DIM);
        }

        // what has been asked, newest first
        g.fill(panelX + 3, listBottom + 2, panelX + panelW - 3, listBottom + 3, 0x30FFFFFF);
        List<ClientGuessWho.Asked> log = ClientGuessWho.log();
        int ly = listBottom + 6;
        for (int i = log.size() - 1; i >= 0 && ly < height - 30; i--) {
            ClientGuessWho.Asked a = log.get(i);
            if (a.template() < 0 || a.template() >= GuessQuestion.TEMPLATES.size()) {
                continue;
            }
            GuessQuestion.Template t = GuessQuestion.TEMPLATES.get(a.template());
            String tag = a.answer() ? "YES" : "NO";
            g.drawString(font, tag, panelX + 7, ly, a.answer() ? YES : NO, false);
            g.drawString(font, fit(t.text(a.value()), panelW - 34), panelX + 29, ly, DIM, false);
            ly += 10;
        }
    }

    private void drawFooter(GuiGraphics g, int mouseX, int mouseY, boolean playing) {
        newGameRect = null;
        confirmRect = null;
        cancelRect = null;
        int y = height - 22;

        if (ClientGuessWho.picking()) {
            g.drawString(font, "click any face to hide it — every one of the 81 can be found",
                    12, y + 4, GOLD, false);
            return;
        }
        if (ClientGuessWho.waitingOnThem()) {
            String msg = ClientGuessWho.aliveCount() == MobCards.ALL.size()
                    ? "waiting for " + ClientGuessWho.opponentName() + " to hide one…"
                    : ClientGuessWho.opponentName() + " is thinking…";
            g.drawString(font, msg, 12, y + 4, DIM, false);
            return;
        }
        if (!playing) {
            String secret = ClientGuessWho.secret();
            MobCard card = MobCards.byId(secret);
            boolean won = ClientGuessWho.phase() == GuessWhoManager.PHASE_WON;
            String text = won
                    ? "FOUND IT — " + (card == null ? "?" : card.displayName().toUpperCase())
                            + " in " + ClientGuessWho.asked() + " questions"
                    : "IT WAS " + (card == null ? "?" : card.displayName().toUpperCase());
            g.drawString(font, text, 12, y + 4, won ? YES : NO, true);
            if (!ClientGuessWho.versusPlayer()) {
                newGameRect = new int[]{width / 2 - 50, y, 100, 16};
                button(g, newGameRect, "NEW BOARD", mouseX, mouseY, true);
            } else {
                g.drawString(font, "/mobtrumps guesswho <player> to play again",
                        width / 2 - 90, y + 4, FAINT, false);
            }
            return;
        }

        if (pendingGuess != null) {
            MobCard card = MobCards.byId(pendingGuess);
            String q = "Name it: " + (card == null ? "?" : card.displayName()) + "?";
            g.drawString(font, q, 12, y + 4, GOLD, false);
            int bx = 12 + font.width(q) + 10;
            confirmRect = new int[]{bx, y, 60, 16};
            cancelRect = new int[]{bx + 66, y, 54, 16};
            button(g, confirmRect, "NAME IT", mouseX, mouseY, true);
            button(g, cancelRect, "CANCEL", mouseX, mouseY, true);
            g.drawString(font, "a wrong name ends the board",
                    bx + 126, y + 4, ClientGuessWho.aliveCount() > 1 ? NO : DIM, false);
        } else {
            g.drawString(font, "click a face to name it  ·  ask questions on the right",
                    12, y + 4, FAINT, false);
        }
    }

    /**
     * The hovered mob, shown big in the bottom-right corner.
     *
     * <p>A board tile is barely fifty pixels across, which is enough to
     * recognise a mob you already know and not enough to read one you do not.
     * The preview is the same card at a size where the portrait and the numbers
     * are legible, parked in a corner rather than chasing the cursor — a tooltip
     * that follows the mouse across a grid of eighty-one tiles covers the very
     * faces you are trying to compare.
     */
    private void drawPreview(GuiGraphics g) {
        if (hoveredMob == null) {
            return;
        }
        MobCard card = MobCards.byId(hoveredMob);
        if (card == null) {
            return;
        }
        // a budget rather than a guess: the popup may claim at most a third of
        // the width and not quite half the height, so it never buries the board
        float scale = Math.min(0.58f, Math.min(width * 0.30f / CardRenderer.CARD_W,
                height * 0.44f / CardRenderer.CARD_H));
        scale = Math.max(0.22f, scale);
        int cw = Math.round(CardRenderer.CARD_W * scale);
        int ch = Math.round(CardRenderer.CARD_H * scale);
        int padX = 7;
        int nameH = 13;
        int boxW = cw + padX * 2;
        int boxH = ch + nameH + 10;
        int bx = width - boxW - 6;
        int by = height - boxH - 6;

        // a solid backing, because it sits over the board and the question panel
        g.fill(bx - 2, by - 2, bx + boxW + 2, by + boxH + 2, 0xF2080610);
        g.renderOutline(bx - 2, by - 2, boxW + 4, boxH + 4, GOLD_DIM);
        g.renderOutline(bx - 1, by - 1, boxW + 2, boxH + 2, 0x66000000);

        LivingEntity mob = CardRenderer.portraitEntity(minecraft, card, entityCache);
        CardRenderer.renderCard(g, font, card, bx + padX, by + 5, scale, -1, -1, mob, false, false);

        String name = fit(card.displayName(), boxW - 8);
        g.drawString(font, name, bx + (boxW - font.width(name)) / 2, by + 5 + ch + 3, INK, true);
    }

    private List<MobCard> aliveCards() {
        List<MobCard> out = new ArrayList<>();
        for (MobCard card : MobCards.ALL) {
            if (ClientGuessWho.isAlive(card.id())) {
                out.add(card);
            }
        }
        return out;
    }

    private int valueFor(int index, GuessQuestion.Template t) {
        return t.clamp(values.getOrDefault(index, 5));
    }

    private String fit(String text, int maxWidth) {
        if (maxWidth <= 0 || font.width(text) <= maxWidth) {
            return text;
        }
        String cut = font.plainSubstrByWidth(text, Math.max(1, maxWidth - font.width("…")));
        return cut.isEmpty() ? "" : cut + "…";
    }

    private void button(GuiGraphics g, int[] r, String label, int mouseX, int mouseY,
                        boolean enabled) {
        boolean over = enabled && mouseX >= r[0] && mouseX < r[0] + r[2]
                && mouseY >= r[1] && mouseY < r[1] + r[3];
        g.fill(r[0], r[1], r[0] + r[2], r[1] + r[3], over ? 0xFF3E3468 : 0xFF262040);
        g.renderOutline(r[0], r[1], r[2], r[3], enabled ? GOLD_DIM : EDGE);
        g.drawString(font, label, r[0] + (r[2] - font.width(label)) / 2, r[1] + 4,
                enabled ? INK : 0xFF4A4560, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (hit(newGameRect, mouseX, mouseY)) {
                pendingGuess = null;
                send(GuessWhoActionPayload.newGame());
                click();
                return true;
            }
            if (hit(confirmRect, mouseX, mouseY) && pendingGuess != null) {
                send(GuessWhoActionPayload.guess(pendingGuess));
                pendingGuess = null;
                click();
                return true;
            }
            if (hit(cancelRect, mouseX, mouseY)) {
                pendingGuess = null;
                click();
                return true;
            }
            // a question row: the value box steps the number, the row asks it
            for (int i = 0; i < questionRects.size(); i++) {
                int[] r = questionRects.get(i);
                if (mouseX < r[0] || mouseX >= r[0] + r[2]
                        || mouseY < r[1] || mouseY >= r[1] + r[3]) {
                    continue;
                }
                int idx = questionIndex.get(i);
                GuessQuestion.Template t = GuessQuestion.TEMPLATES.get(idx);
                if (r[4] >= 0 && mouseX >= r[4] && mouseX < r[4] + r[5]) {
                    step(idx, t, 1);
                    click();
                    return true;
                }
                if (ClientGuessWho.playing()) {
                    send(GuessWhoActionPayload.ask(idx, valueFor(idx, t)));
                    click();
                }
                return true;
            }
            if (ClientGuessWho.picking() && hoveredMob != null) {
                send(GuessWhoActionPayload.pick(hoveredMob));
                click();
                return true;
            }
            if (ClientGuessWho.playing() && hoveredMob != null) {
                pendingGuess = hoveredMob;
                click();
                return true;
            }
        } else if (button == 1) {
            // right-click a value box steps it back down
            for (int i = 0; i < questionRects.size(); i++) {
                int[] r = questionRects.get(i);
                if (r[4] >= 0 && mouseX >= r[4] && mouseX < r[4] + r[5]
                        && mouseY >= r[1] && mouseY < r[1] + r[3]) {
                    int idx = questionIndex.get(i);
                    step(idx, GuessQuestion.TEMPLATES.get(idx), -1);
                    click();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void step(int index, GuessQuestion.Template t, int by) {
        int next = valueFor(index, t) + by;
        if (next > t.maxValue()) {
            next = t.minValue();
        }
        if (next < t.minValue()) {
            next = t.maxValue();
        }
        values.put(index, next);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double dx, double dy) {
        // over a value box, the wheel changes the number; elsewhere it scrolls
        for (int i = 0; i < questionRects.size(); i++) {
            int[] r = questionRects.get(i);
            if (r[4] >= 0 && mouseX >= r[4] && mouseX < r[4] + r[5]
                    && mouseY >= r[1] && mouseY < r[1] + r[3]) {
                int idx = questionIndex.get(i);
                step(idx, GuessQuestion.TEMPLATES.get(idx), (int) Math.signum(dy));
                return true;
            }
        }
        if (mouseX >= panelX) {
            scroll = Math.max(0, scroll - (int) Math.signum(dy));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, dx, dy);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == 256 && pendingGuess != null) {
            pendingGuess = null; // escape backs out of a guess before it leaves
            return true;
        }
        if (search != null && search.isFocused()) {
            return search.keyPressed(key, scan, mods) || super.keyPressed(key, scan, mods);
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

    private static void send(GuessWhoActionPayload payload) {
        PacketDistributor.sendToServer(payload);
    }

    @Override
    public void onClose() {
        send(GuessWhoActionPayload.leave());
        super.onClose();
    }
}
