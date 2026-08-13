package com.voxelia.mmo.client;

import com.voxelia.mmo.game.MemoryDeck;
import com.voxelia.mmo.game.MemoryGame;
import com.voxelia.mmo.network.MemoryActionPacket;
import com.voxelia.mmo.skill.Skill;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Memory (Menu ▸ Memory Game): flip two cards, keep the pairs you match. Solo is
 * scored by moves and time; versus is turn-based — a match keeps your turn, a miss
 * hands it over after a short peek. The board lives on the server; this screen only
 * draws what it is told and asks to flip.
 */
public final class MemoryScreen extends Screen {
    private static final int PAD = 8;
    private static final int TITLE_H = 17;
    private static final int HEADER_H = 27;
    private static final int FOOTER_H = 18;
    private static final int CARD_GAP = 4;
    private static final int PANEL_W = 220;
    private static final int LOBBY_H = 112;
    private static final int[] NONE = new int[4];

    private final ScreenMenu menu = new ScreenMenu();
    private final List<int[]> cardRects = new ArrayList<>();
    private final int[][] diffButtons = new int[MemoryGame.Difficulty.values().length][4];
    private int[] btnNew = NONE;
    private int[] btnLeave = NONE;

    public MemoryScreen() {
        super(Component.literal("Voxelia Memory"));
        PacketDistributor.sendToServer(new MemoryActionPacket(MemoryActionPacket.REFRESH, 0));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        float e = VoxeliaUi.introT();
        g.pose().pushPose();
        g.pose().translate(0, (1f - e) * 6f, 0);

        cardRects.clear();
        btnNew = NONE;
        btnLeave = NONE;

        boolean playing = ClientMemory.inGame();
        int cw = cardW(), ch = cardH();
        int boardW = playing ? ClientMemory.cols() * cw + (ClientMemory.cols() - 1) * CARD_GAP : 0;
        int boardH = playing ? ClientMemory.rows() * ch + (ClientMemory.rows() - 1) * CARD_GAP : 0;
        int h = playing ? TITLE_H + HEADER_H + boardH + PAD + FOOTER_H : LOBBY_H;
        int panelW = Math.max(PANEL_W, boardW + 2 * PAD);
        int x = (this.width - panelW) / 2;
        int y = (this.height - h) / 2;

        VoxeliaUi.panel(g, x, y, panelW, h);
        VoxeliaUi.titleBar(g, this.font, x, y, panelW, "MEMORY");
        int totalPts = 0;
        for (Skill s : Skill.values()) totalPts += ClientTalents.available(s);
        menu.renderButton(g, this.font, x, y, panelW, mouseX, mouseY, totalPts > 0);

        if (playing) renderGame(g, x, y, panelW, h, boardW, boardH, mouseX, mouseY);
        else renderLobby(g, x, y, panelW, h, mouseX, mouseY);

        menu.renderDropdown(g, this.font, ScreenMenu.Page.MEMORY, mouseX, mouseY);
        g.pose().popPose();
        super.render(g, mouseX, mouseY, partialTick);
    }

    // ── Lobby: pick a size, or read how to challenge someone ────────────────
    private void renderLobby(GuiGraphics g, int x, int y, int panelW, int h, int mouseX, int mouseY) {
        int ty = y + TITLE_H + 6;
        g.drawString(this.font, "Match every pair", x + PAD, ty, VoxeliaUi.TEXT);
        g.drawString(this.font, "Flip two cards a turn. Keep the pairs you find.",
            x + PAD, ty + 11, VoxeliaUi.MUTED);

        MemoryGame.Difficulty[] all = MemoryGame.Difficulty.values();
        int bw = (panelW - 2 * PAD - (all.length - 1) * 4) / all.length;
        int by = ty + 26;
        for (int i = 0; i < all.length; i++) {
            int bx = x + PAD + i * (bw + 4);
            String label = all[i].display();
            String sub = all[i].cols() + "×" + all[i].rows();
            boolean hover = !menu.isOpen() && in(new int[]{bx, by, bx + bw, by + 20}, mouseX, mouseY);
            g.fillGradient(bx, by, bx + bw, by + 20,
                hover ? 0xFF2C4056 : 0xFF1E2B3A, hover ? 0xFF1B2938 : 0xFF131C27);
            g.fill(bx, by, bx + bw, by + 1, hover ? 0x90FFCE54 : 0x40FFFFFF);
            g.fill(bx, by + 19, bx + bw, by + 20, 0x40000000);
            g.drawCenteredString(this.font, label, bx + bw / 2, by + 3, hover ? VoxeliaUi.GOLD : VoxeliaUi.TEXT);
            g.drawCenteredString(this.font, sub, bx + bw / 2, by + 12, VoxeliaUi.MUTED);
            diffButtons[i] = new int[]{bx, by, bx + bw, by + 20};
        }

        int fy = y + h - FOOTER_H + 4;
        VoxeliaUi.footer(g, x, y + h - FOOTER_H, panelW, FOOTER_H);
        String lead = "Two players: ";
        g.drawString(this.font, lead, x + PAD, fy, VoxeliaUi.MUTED);
        g.drawString(this.font, "/voxelia memory invite <player>",
            x + PAD + this.font.width(lead), fy, VoxeliaUi.LINK);
    }

    // ── An actual game ──────────────────────────────────────────────────────
    private void renderGame(GuiGraphics g, int x, int y, int panelW, int h,
                            int boardW, int boardH, int mouseX, int mouseY) {
        boolean versus = ClientMemory.versus();
        boolean finished = ClientMemory.finished();
        int you = ClientMemory.you();
        List<String> names = ClientMemory.names();
        List<Integer> scores = ClientMemory.scores();

        // Header line 1: whose turn / the result.
        int hy = y + TITLE_H + 4;
        String status;
        int statusColor;
        if (finished) {
            if (versus) {
                int w = ClientMemory.winner();
                status = w < 0 ? "Draw!" : (w == you ? "You win!" : names.get(w) + " wins");
                statusColor = w == you ? VoxeliaUi.GOOD : (w < 0 ? VoxeliaUi.GOLD : VoxeliaUi.WARN);
            } else {
                status = "Solved in " + ClientMemory.moves() + " moves · "
                    + MemoryGame.formatTime(ClientMemory.elapsed());
                statusColor = VoxeliaUi.GOLD;
            }
        } else if (!versus) {
            status = ClientMemory.pairsLeft() + " pair" + (ClientMemory.pairsLeft() == 1 ? "" : "s") + " left";
            statusColor = VoxeliaUi.TEXT;
        } else if (ClientMemory.yourTurn()) {
            status = ClientMemory.peeking() ? "No match…" : "Your turn";
            statusColor = ClientMemory.peeking() ? VoxeliaUi.MUTED : VoxeliaUi.GOOD;
        } else {
            int t = ClientMemory.turn();
            status = (t >= 0 && t < names.size() ? names.get(t) : "Opponent") + "'s turn";
            statusColor = VoxeliaUi.MUTED;
        }
        g.drawString(this.font, status, x + PAD, hy, statusColor);

        // Header right: the clock (solo) or the running score (versus).
        if (!versus) {
            String clock = MemoryGame.formatTime(ClientMemory.elapsed()) + "  ·  "
                + ClientMemory.moves() + " moves";
            g.drawString(this.font, clock, x + panelW - PAD - this.font.width(clock), hy, VoxeliaUi.MUTED);
        }

        // Header line 2: the seats and their scores.
        if (versus) {
            int sy = hy + 12;
            int sx = x + PAD;
            for (int i = 0; i < names.size(); i++) {
                boolean active = i == ClientMemory.turn() && !finished;
                String label = names.get(i) + " " + (i < scores.size() ? scores.get(i) : 0);
                int col = active ? VoxeliaUi.GOLD : (i == you ? VoxeliaUi.TEXT : VoxeliaUi.MUTED);
                if (active) g.fill(sx - 3, sy - 1, sx - 2, sy + 9, VoxeliaUi.GOLD);
                g.drawString(this.font, label, sx, sy, col);
                sx += this.font.width(label) + 12;
            }
            String left = ClientMemory.pairsLeft() + " left";
            g.drawString(this.font, left, x + panelW - PAD - this.font.width(left), sy, VoxeliaUi.MUTED);
        }

        // The board.
        int bx0 = x + (panelW - boardW) / 2;
        int by0 = y + TITLE_H + HEADER_H;
        boolean clickable = ClientMemory.yourTurn() && !ClientMemory.peeking() && !menu.isOpen();
        int cw = cardW(), ch = cardH();
        for (int i = 0; i < ClientMemory.cards(); i++) {
            int cx = bx0 + (i % ClientMemory.cols()) * (cw + CARD_GAP);
            int cy = by0 + (i / ClientMemory.cols()) * (ch + CARD_GAP);
            int st = ClientMemory.stateOf(i);
            boolean hover = clickable && st == MemoryGame.HIDDEN
                && mouseX >= cx && mouseX < cx + cw && mouseY >= cy && mouseY < cy + ch;
            drawCard(g, cx, cy, cw, ch, i, st, hover);
            cardRects.add(new int[]{cx, cy, cx + cw, cy + ch});
        }

        // Footer buttons.
        int footY = y + h - FOOTER_H;
        VoxeliaUi.footer(g, x, footY, panelW, FOOTER_H);
        int by = footY + 2;
        if (!versus || finished) {
            btnNew = drawButton(g, x + PAD, by, 78, "New game", mouseX, mouseY, 0xFF2E5C3A);
        }
        String leaveLabel = versus && !finished ? "Forfeit" : "Quit";
        int lw = 58;
        btnLeave = drawButton(g, x + panelW - PAD - lw, by, lw, leaveLabel, mouseX, mouseY, 0xFF5C3A3A);

        if (!versus && !finished) {
            String hint = "Solo";
            g.drawCenteredString(this.font, hint, x + panelW / 2, by + 3, VoxeliaUi.DISABLED);
        }
    }

    /** Cards shrink as the board grows so even 6×6 fits a 240px-tall GUI. */
    private static int cardW() {
        int rows = ClientMemory.rows();
        return rows >= 6 ? 26 : (rows == 5 ? 28 : 32);
    }

    private static int cardH() {
        int rows = ClientMemory.rows();
        return rows >= 6 ? 20 : (rows == 5 ? 22 : 24);
    }

    /** One card: slate back with a gold ✦, or its face; squeezed mid-flip. */
    private void drawCard(GuiGraphics g, int cx, int cy, int cw, int ch,
                          int index, int state, boolean hover) {
        float t = ClientMemory.flipT(index);
        boolean revealed = state != MemoryGame.HIDDEN;
        // Before the halfway point the card still shows its previous side.
        boolean showFace = (t >= 0.5f) == revealed;
        float squeeze = Math.max(0.06f, Math.abs((float) Math.cos(Math.PI * t)));
        int half = Math.max(1, (int) (cw / 2f * squeeze));
        int x1 = cx + cw / 2 - half;
        int x2 = cx + cw / 2 + half;
        int textY = cy + ch / 2 - 4;

        if (!showFace) { // face down
            g.fillGradient(x1, cy, x2, cy + ch,
                hover ? 0xFF2A3C51 : 0xFF1E2B3A, hover ? 0xFF1B2938 : 0xFF131C27);
            g.fill(x1, cy, x2, cy + 1, hover ? 0x90FFCE54 : 0x40FFFFFF);
            g.fill(x1, cy + ch - 1, x2, cy + ch, 0x50000000);
            if (half > 5) {
                g.drawCenteredString(this.font, "✦", (x1 + x2) / 2, textY,
                    hover ? VoxeliaUi.GOLD : 0xFF3A4E63);
            }
            return;
        }

        MemoryDeck.Face face = MemoryDeck.byId(ClientMemory.faceForRender(index));
        boolean matched = state == MemoryGame.MATCHED;
        int rgb = face != null ? face.color() : 0x8FA0AD;
        int base = 0xFF000000 | rgb;

        if (face != null && face.kind() == MemoryDeck.Kind.TALENT) {
            // Talent cards wear the talent screen's look: slate body, category badge.
            int body = matched ? 0xFF141B24 : 0xFF1B2532;
            g.fillGradient(x1, cy, x2, cy + ch, VoxeliaUi.brighten(body, 10), body);
            g.fill(x1, cy, x2, cy + 1, 0x50FFFFFF);
            g.fill(x1, cy + ch - 1, x2, cy + ch, 0x50000000);
            g.fill(x1, cy, x1 + Math.min(3, half * 2), cy + ch, matched
                ? VoxeliaUi.lerp(base, 0xFF0A0F14, 0.5f) : base);
            if (half > 8) {
                int bw = (x2 - x1) - 10;
                int by2 = cy + ch / 2 - 6;
                g.fill(x1 + 5, by2, x1 + 5 + bw, by2 + 12, matched
                    ? VoxeliaUi.lerp(base, 0xFF0A0F14, 0.45f) : base);
                g.fill(x1 + 5, by2, x1 + 5 + bw, by2 + 1, 0x50FFFFFF);
                g.drawCenteredString(this.font, face.code(), (x1 + x2) / 2, by2 + 2,
                    matched ? 0xFF6C7A6C : 0xFF14181C);
            }
            if (matched) {
                g.fill(x1, cy, x2, cy + 1, 0x806EE86E);
                g.fill(x1, cy + ch - 1, x2, cy + ch, 0x806EE86E);
            }
            return;
        }

        // Skill and Character cards: the skills-screen card, shrunk to a playing card.
        int top = matched ? VoxeliaUi.lerp(base, 0xFF0A0F14, 0.45f) : VoxeliaUi.brighten(base, 20);
        int bot = matched ? VoxeliaUi.lerp(base, 0xFF0A0F14, 0.65f) : VoxeliaUi.lerp(base, 0xFF0A0F14, 0.35f);
        g.fillGradient(x1, cy, x2, cy + ch, top, bot);
        g.fill(x1, cy, x2, cy + 1, 0x60FFFFFF);
        g.fill(x1, cy + ch - 1, x2, cy + ch, 0x50000000);
        if (half > 4) { // the accent strip every Voxelia card carries
            g.fillGradient(x1, cy, x1 + Math.min(3, half), cy + ch,
                VoxeliaUi.brighten(base, 40), VoxeliaUi.lerp(base, 0xFF0A0F14, 0.4f));
        }
        if (matched) { // quiet green frame: this pair is banked
            g.fill(x1, cy, x2, cy + 1, 0x806EE86E);
            g.fill(x1, cy + ch - 1, x2, cy + ch, 0x806EE86E);
        }
        if (half > 8 && face != null) {
            boolean character = face.kind() == MemoryDeck.Kind.CHARACTER;
            String text = character ? "✦" : face.code();
            g.drawCenteredString(this.font, text, (x1 + x2) / 2 + 1, textY,
                matched ? 0xFFBFD0BF : 0xFF14181C);
        }
    }

    private int[] drawButton(GuiGraphics g, int x, int y, int w, String label,
                             int mouseX, int mouseY, int color) {
        boolean hover = !menu.isOpen() && mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + 13;
        g.fill(x, y, x + w, y + 13, hover ? VoxeliaUi.brighten(color, 28) : color);
        g.fill(x, y, x + w, y + 1, 0x60FFFFFF);
        g.drawCenteredString(this.font, label, x + w / 2, y + 3, 0xFFFFFFFF);
        return new int[]{x, y, x + w, y + 13};
    }

    private static boolean in(int[] r, double mx, double my) {
        return mx >= r[0] && mx < r[2] && my >= r[1] && my < r[3];
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (menu.mouseClicked(mouseX, mouseY, ScreenMenu.Page.MEMORY)) return true;

            if (!ClientMemory.inGame()) {
                for (int i = 0; i < diffButtons.length; i++) {
                    if (in(diffButtons[i], mouseX, mouseY)) {
                        PacketDistributor.sendToServer(new MemoryActionPacket(MemoryActionPacket.NEW_SOLO, i));
                        return true;
                    }
                }
                return super.mouseClicked(mouseX, mouseY, button);
            }

            if (in(btnNew, mouseX, mouseY)) {
                PacketDistributor.sendToServer(new MemoryActionPacket(
                    MemoryActionPacket.NEW_SOLO, difficultyOrdinal()));
                return true;
            }
            if (in(btnLeave, mouseX, mouseY)) {
                PacketDistributor.sendToServer(new MemoryActionPacket(MemoryActionPacket.LEAVE, 0));
                return true;
            }
            if (ClientMemory.yourTurn() && !ClientMemory.peeking()) {
                for (int i = 0; i < cardRects.size(); i++) {
                    if (in(cardRects.get(i), mouseX, mouseY) && ClientMemory.stateOf(i) == MemoryGame.HIDDEN) {
                        PacketDistributor.sendToServer(new MemoryActionPacket(MemoryActionPacket.FLIP, i));
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** Best match for the board we're looking at, so "New game" keeps the same size. */
    private int difficultyOrdinal() {
        MemoryGame.Difficulty[] all = MemoryGame.Difficulty.values();
        for (int i = 0; i < all.length; i++) {
            if (all[i].cols() == ClientMemory.cols() && all[i].rows() == ClientMemory.rows()) return i;
        }
        return 1;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && menu.close()) return true;
        if (VoxeliaKeys.OPEN_MENU.matches(keyCode, scanCode)) {
            Minecraft.getInstance().setScreen(new SkillsScreen());
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void renderBlurredBackground(float partialTick) {}
}
