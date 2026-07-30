package com.jrpetty.mobtrumps.client;

import com.jrpetty.mobtrumps.CampaignActionPayload;
import com.jrpetty.mobtrumps.CampaignRewards;
import com.jrpetty.mobtrumps.game.CampaignDecks;
import com.jrpetty.mobtrumps.game.CampaignMission;
import com.jrpetty.mobtrumps.game.MobCard;
import com.jrpetty.mobtrumps.game.MobCards;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * The campaign: twenty missions as a route you climb.
 *
 * <p>Laid out as a scrolling column of plates in each mission's category
 * colour, chained together, with state readable at a glance — locked plates sit
 * dark behind their chain, the next one available breathes, cleared ones carry
 * a stamp and flawless ones a gold star. Selecting a mission opens its briefing
 * on the right: the name, its one line of identity, the sixteen cards it is
 * played with, what the opponent will do to you, and what a first clear pays.
 *
 * <p>Both columns scroll independently — the wheel follows the cursor — and the
 * briefing body is clipped so it can never spill over the Begin button, however
 * short the window is.
 */
public class CampaignScreen extends Screen {

    private static final int ROW_H = 30;
    private static final int LIST_W = 210;
    private static final int PAD = 12;
    private static final int BTN_H = 20;

    private static final int INK = 0xFFF2ECDD;
    private static final int INK_DIM = 0xFF9A93A8;
    private static final int PLATE = 0xFF241F33;
    private static final int PLATE_LOCKED = 0xFF171425;
    private static final int GOLD = 0xFFE3C071;
    private static final int GREEN = 0xFF3D8B3D;

    private int selected = 1;
    private int scroll;
    private int briefScroll;
    private int briefContentH;
    private int listX, listY, listH, panelX, panelW;
    private int[] beginRect = {0, 0, 0, 0};

    /** The selected mission's deck, rebuilt only when the selection changes. */
    private int deckFor = -1;
    private List<MobCard> deck = List.of();

    public CampaignScreen() {
        super(Component.literal("Campaign"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        super.init();
        listX = Math.max(PAD, (width - (LIST_W + 300 + PAD)) / 2);
        listY = 64;
        listH = height - listY - 20;
        panelX = listX + LIST_W + PAD;
        panelW = Math.min(340, width - panelX - PAD);
        // open on the next mission you can actually play
        select(Mth.clamp(ClientCampaign.highestCleared() + 1, 1, CampaignDecks.count()));
        ensureVisible();
    }

    /** The campaign is shut until the book can field a full sixteen. */
    private static boolean canPlay() {
        return ClientCollection.storedCount() >= CampaignDecks.DECK_SIZE;
    }

    private void select(int index) {
        selected = index;
        briefScroll = 0;
        if (deckFor != index) {
            CampaignMission m = CampaignDecks.byIndex(index);
            deck = m == null ? List.of() : CampaignDecks.cpuDeck(m);
            deckFor = index;
        }
    }

    private int rowsVisible() {
        return Math.max(1, listH / ROW_H);
    }

    private int maxScroll() {
        return Math.max(0, CampaignDecks.count() - rowsVisible());
    }

    private void ensureVisible() {
        int i = selected - 1;
        if (i < scroll) scroll = i;
        if (i >= scroll + rowsVisible()) scroll = i - rowsVisible() + 1;
        scroll = Mth.clamp(scroll, 0, maxScroll());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        long now = System.currentTimeMillis();
        g.fillGradient(0, 0, width, height, 0xF01A1428, 0xF00C0A16);

        // masthead
        var pose = g.pose();
        pose.pushPose();
        pose.translate(width / 2f, 20f, 0);
        pose.scale(2f, 2f, 1f);
        String title = "CAMPAIGN";
        g.drawString(font, title, -font.width(title) / 2, 0, GOLD, true);
        pose.popPose();
        int done = ClientCampaign.clearedCount();
        int filed = ClientCollection.storedCount();
        String sub = canPlay()
                ? done + " of " + CampaignDecks.count() + " missions cleared"
                : "File " + CampaignDecks.DECK_SIZE + " cards in your Collection Book to begin — "
                        + filed + " / " + CampaignDecks.DECK_SIZE;
        g.drawCenteredString(font, sub, width / 2, 42, canPlay() ? INK_DIM : 0xFFE0A05A);
        int barW = Math.min(340, width - 40);
        int barX = width / 2 - barW / 2;
        g.fill(barX, 54, barX + barW, 57, 0x40000000);
        if (canPlay()) {
            if (done > 0) {
                g.fill(barX, 54, barX + barW * done / CampaignDecks.count(), 57, GOLD);
            }
        } else {
            int got = Math.min(filed, CampaignDecks.DECK_SIZE);
            g.fill(barX, 54, barX + barW * got / CampaignDecks.DECK_SIZE, 57, 0xFFE0A05A);
        }

        drawRoute(g, mouseX, mouseY, now);
        drawBriefing(g, mouseX, mouseY, now);

        g.drawCenteredString(font, "↑↓ to move  ·  Enter to begin  ·  ESC to close",
                width / 2, height - 11, 0xFF6C6480);
    }

    private void drawRoute(GuiGraphics g, int mouseX, int mouseY, long now) {
        int shown = Math.min(rowsVisible(), CampaignDecks.count() - scroll);
        for (int r = 0; r < shown; r++) {
            CampaignMission m = CampaignDecks.byIndex(scroll + r + 1);
            int y = listY + r * ROW_H;
            boolean unlocked = ClientCampaign.unlocked(m);
            boolean cleared = ClientCampaign.cleared(m);
            boolean flawless = ClientCampaign.flawless(m);
            boolean isSel = m.index() == selected;
            boolean hover = mouseX >= listX && mouseX < listX + LIST_W
                    && mouseY >= y && mouseY < y + ROW_H - 4;

            // the chain running down the left, joining the plates
            g.fill(listX + 13, y - 4, listX + 15, y + ROW_H - 4, unlocked ? 0xFF4A4066 : 0xFF241F33);

            int plate = !unlocked ? PLATE_LOCKED : (isSel ? 0xFF352C4E : hover ? 0xFF2C2542 : PLATE);
            g.fill(listX, y, listX + LIST_W, y + ROW_H - 4, plate);
            // a band of the mission's own scene behind the label
            MissionArt.drawStrip(g, m, listX, y, LIST_W, ROW_H - 4, !unlocked);
            if (isSel) {
                g.fill(listX, y, listX + LIST_W, y + ROW_H - 4, 0x2AE3C071);
            } else if (hover) {
                g.fill(listX, y, listX + LIST_W, y + ROW_H - 4, 0x18FFFFFF);
            }
            // the category's colour as an identity stripe
            int accent = m.anchor().accent();
            g.fill(listX, y, listX + 3, y + ROW_H - 4, unlocked ? accent : 0xFF3A3350);
            if (isSel) {
                g.renderOutline(listX, y, LIST_W, ROW_H - 4, GOLD);
            }

            // the number bead on the chain
            int beadCol = cleared ? GREEN : unlocked ? accent : 0xFF3A3350;
            g.fill(listX + 8, y + 8, listX + 20, y + 20, 0xFF12101C);
            g.fill(listX + 9, y + 9, listX + 19, y + 19, beadCol);
            String num = String.valueOf(m.index());
            g.drawString(font, num, listX + 14 - font.width(num) / 2, y + 11,
                    unlocked ? 0xFF12101C : 0xFF6C6480, false);

            g.drawString(font, unlocked ? m.name() : "Locked", listX + 26, y + 5,
                    unlocked ? (cleared ? 0xFFB9B2C8 : INK) : 0xFF564E70, false);
            g.drawString(font, unlocked ? m.anchor().label() : "clear the mission before it",
                    listX + 26, y + 16, unlocked ? INK_DIM : 0xFF453D5E, false);

            if (flawless) {
                g.drawString(font, "★", listX + LIST_W - 14, y + 10, GOLD, false);
            } else if (cleared) {
                g.drawString(font, "✔", listX + LIST_W - 14, y + 10, GREEN, false);
            } else if (unlocked) {
                // the one you can play breathes
                float pulse = 0.5f + 0.5f * (float) Math.sin(now / 400.0);
                int a = (int) (0x60 + 0x8F * pulse) << 24;
                g.fill(listX + LIST_W - 16, y + 12, listX + LIST_W - 10, y + 18, a | (accent & 0xFFFFFF));
            }
        }
        drawScrollbar(g, listX + LIST_W + 2, listY, listH, CampaignDecks.count(),
                rowsVisible(), scroll);
    }

    /** A hairline rail with a thumb, so a twenty-long route reads as scrollable. */
    private void drawScrollbar(GuiGraphics g, int x, int y, int h, int total, int visible, int at) {
        if (total <= visible || h < 20) {
            return;
        }
        g.fill(x, y, x + 3, y + h, 0x30FFFFFF);
        int thumbH = Math.max(14, h * visible / total);
        int travel = h - thumbH;
        int thumbY = y + (int) ((long) travel * at / Math.max(1, total - visible));
        g.fill(x, thumbY, x + 3, thumbY + thumbH, 0xFF4A4066);
    }

    private void drawBriefing(GuiGraphics g, int mouseX, int mouseY, long now) {
        CampaignMission m = CampaignDecks.byIndex(selected);
        if (m == null || panelW < 120) {
            return;
        }
        boolean unlocked = ClientCampaign.unlocked(m);
        int x0 = panelX;
        int x1 = panelX + panelW;
        int panelBottom = listY + listH;
        int by = panelBottom - 26;                 // top of the Begin button

        g.fill(x0, listY, x1, panelBottom, 0xCC120E1E);

        // --- the mission's own scene, with its name laid over the foot of it --
        int bannerH = Mth.clamp(listH / 3, 46, MissionArt.BANNER_H);
        MissionArt.draw(g, m, x0, listY, panelW, bannerH, !unlocked);
        var pose = g.pose();
        pose.pushPose();
        pose.translate(x0 + 8, listY + bannerH - 22f, 0);
        pose.scale(1.7f, 1.7f, 1f);
        g.drawString(font, m.name(), 0, 0, INK, true);
        pose.popPose();
        pose.pushPose();
        pose.translate(x0 + 8, listY + 7f, 0);
        pose.scale(1.3f, 1.3f, 1f);
        g.drawString(font, "MISSION " + m.index(), 0, 0, 0xFFFFFFFF, true);
        pose.popPose();
        drawStatusChip(g, m, x1 - 8, listY + 7);
        int y = listY + bannerH + 6;

        // --- scrollable body ------------------------------------------------
        int bodyTop = y;
        int bodyH = Math.max(20, by - 8 - bodyTop);
        briefScroll = Mth.clamp(briefScroll, 0, Math.max(0, briefContentH - bodyH));

        g.enableScissor(x0, bodyTop, x1, bodyTop + bodyH);
        pose.pushPose();
        pose.translate(0, -briefScroll, 0);
        int b = bodyTop;

        for (var line : font.split(Component.literal(m.tagline()), panelW - 16)) {
            g.drawString(font, line, x0 + 8, b, INK_DIM, false);
            b += 10;
        }
        b += 8;

        StringBuilder threat = new StringBuilder();
        for (int i = 0; i < 5; i++) threat.append(i < m.threat() ? "◆" : "◇");
        g.drawString(font, "THREAT  " + threat, x0 + 8, b, 0xFFD8735E, false);
        b += 12;
        g.drawString(font, m.opponentLabel(), x0 + 8, b, INK_DIM, false);
        b += 18;

        // the deck: sixteen chips, anchor cards in the set colour, padding grey
        g.drawString(font, "THEIR DECK  ·  " + deck.size() + " cards"
                + (m.cpuLevel() > 0 ? "  ·  Holo " + "I".repeat(Math.min(3, m.cpuLevel())) : ""),
                x0 + 8, b, GOLD, false);
        b += 12;
        int cx = x0 + 8;
        for (MobCard card : deck) {
            boolean anchor = card.category() == m.anchor();
            String label = card.displayName();
            int w = font.width(label) + 8;
            if (cx + w > x1 - 8) {
                cx = x0 + 8;
                b += 12;
            }
            g.fill(cx, b, cx + w, b + 10,
                    anchor ? (0x66000000 | (m.anchor().accent() & 0xFFFFFF)) : 0x40FFFFFF);
            g.drawString(font, label, cx + 4, b + 1, anchor ? INK : 0xFFB9B2C8, false);
            cx += w + 3;
        }
        b += 14;
        g.drawString(font, "coloured = " + m.anchor().label() + "  ·  grey = brought in",
                x0 + 8, b, 0xFF6C6480, false);
        b += 14;
        int filed = ClientCollection.storedCount();
        g.drawString(font, "YOUR DECK  ·  " + CampaignDecks.DECK_SIZE + " from your book",
                x0 + 8, b, GOLD, false);
        b += 11;
        g.drawString(font, canPlay()
                        ? "Your battle deck, topped up from the book, at your holo levels"
                        : "You have " + filed + " of " + CampaignDecks.DECK_SIZE + " cards filed",
                x0 + 8, b, canPlay() ? INK_DIM : 0xFFE0A05A, false);
        b += 18;

        g.drawString(font, "REWARD  ·  first clear only", x0 + 8, b, GOLD, false);
        b += 11;
        for (var line : font.split(Component.literal(
                CampaignRewards.forMission(m.index()).label()), panelW - 16)) {
            g.drawString(font, line, x0 + 8, b, INK_DIM, false);
            b += 10;
        }
        b += 8;

        g.drawString(font, "TROPHY", x0 + 8, b, GOLD, false);
        b += 11;
        MobCard trophy = MobCards.byId(m.trophyMob());
        for (var line : font.split(Component.literal(trophy == null ? "—"
                : trophy.displayName() + " — a Trophy print, only ever won"), panelW - 16)) {
            g.drawString(font, line, x0 + 8, b, INK_DIM, false);
            b += 10;
        }

        pose.popPose();
        g.disableScissor();
        briefContentH = b - bodyTop + 4;

        // a hint that there is more below, and the rail to reach it
        if (briefContentH > bodyH) {
            g.fillGradient(x0, bodyTop + bodyH - 10, x1, bodyTop + bodyH, 0x00120E1E, 0xCC120E1E);
            drawScrollbar(g, x1 - 5, bodyTop, bodyH, briefContentH, bodyH, briefScroll);
        }

        // --- fixed Begin button ---------------------------------------------
        boolean ready = unlocked && canPlay();
        String label = !canPlay()
                ? "Need " + CampaignDecks.DECK_SIZE + " filed  ·  " + ClientCollection.storedCount()
                        + "/" + CampaignDecks.DECK_SIZE
                : !unlocked ? "Locked"
                : ClientCampaign.cleared(m) ? "Play again" : "Begin";
        int bw = Math.max(120, font.width(label) + 32);
        int bx = x0 + (panelW - bw) / 2;
        beginRect = new int[]{bx, by, bw, BTN_H};
        boolean hover = ready && inRect(mouseX, mouseY, beginRect);
        int base = !ready ? 0xFF2A2440 : hover ? 0xFF4B8F3E : 0xFF3A7A32;
        if (ready) {
            float pulse = 0.5f + 0.5f * (float) Math.sin(now / 380.0);
            g.fill(bx - 2, by - 2, bx + bw + 2, by + BTN_H + 2,
                    ((int) (0x28 + 0x24 * pulse) << 24) | 0x00E3C071);
        }
        g.fill(bx, by, bx + bw, by + BTN_H, base);
        g.renderOutline(bx, by, bw, BTN_H, ready ? (hover ? GOLD : 0x66FFFFFF) : 0xFF3A3350);
        g.drawString(font, label, bx + (bw - font.width(label)) / 2, by + 6,
                ready ? 0xFFFFFFFF : 0xFF6C6480, true);
    }

    /** CLEARED / FLAWLESS stamp, right-aligned on the briefing's header row. */
    private void drawStatusChip(GuiGraphics g, CampaignMission m, int right, int y) {
        String text;
        int fg;
        if (ClientCampaign.flawless(m)) {
            text = "★ FLAWLESS";
            fg = GOLD;
        } else if (ClientCampaign.cleared(m)) {
            text = "✔ CLEARED";
            fg = GREEN;
        } else if (ClientCampaign.unlocked(m)) {
            text = "AVAILABLE";
            fg = INK_DIM;
        } else {
            text = "LOCKED";
            fg = 0xFF564E70;
        }
        int w = font.width(text) + 8;
        g.fill(right - w, y, right, y + 11, 0x40000000);
        g.drawString(font, text, right - w + 4, y + 2, fg, false);
    }

    private boolean overPanel(double mouseX, double mouseY) {
        return mouseX >= panelX && mouseX < panelX + panelW
                && mouseY >= listY && mouseY < listY + listH;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        int shown = Math.min(rowsVisible(), CampaignDecks.count() - scroll);
        for (int r = 0; r < shown; r++) {
            int y = listY + r * ROW_H;
            if (mouseX >= listX && mouseX < listX + LIST_W && mouseY >= y && mouseY < y + ROW_H - 4) {
                select(scroll + r + 1);
                click(1.0f);
                return true;
            }
        }
        if (inRect((int) mouseX, (int) mouseY, beginRect)) {
            begin();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double dx, double dy) {
        int step = (int) Math.signum(dy);
        if (overPanel(mouseX, mouseY)) {
            briefScroll = Math.max(0, briefScroll - step * 12);
            return true;
        }
        scroll = Mth.clamp(scroll - step, 0, maxScroll());
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        switch (keyCode) {
            case 264 -> { // down
                select(Math.min(CampaignDecks.count(), selected + 1));
                ensureVisible();
                return true;
            }
            case 265 -> { // up
                select(Math.max(1, selected - 1));
                ensureVisible();
                return true;
            }
            case 257, 335 -> { // enter / numpad enter
                begin();
                return true;
            }
            default -> {
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** Launch the selected mission, if it is actually playable. */
    private void begin() {
        CampaignMission m = CampaignDecks.byIndex(selected);
        if (m == null || !ClientCampaign.unlocked(m) || !canPlay()) {
            click(0.7f);
            return;
        }
        PacketDistributor.sendToServer(CampaignActionPayload.begin(m.index()));
        click(1.3f);
    }

    private void click(float pitch) {
        if (minecraft != null) {
            minecraft.getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), pitch));
        }
    }

    private static boolean inRect(int mx, int my, int[] r) {
        return r != null && mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3];
    }
}
