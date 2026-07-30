package com.jrpetty.mobtrumps.client;

import com.jrpetty.mobtrumps.CampaignActionPayload;
import com.jrpetty.mobtrumps.game.CampaignDecks;
import com.jrpetty.mobtrumps.game.CampaignMission;
import com.jrpetty.mobtrumps.game.MobCard;
import com.jrpetty.mobtrumps.game.MobCategories;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.Locale;

/**
 * The campaign: twenty missions as a route you climb.
 *
 * <p>Laid out as a scrolling column of plates in each mission's category
 * colour, chained together, with state readable at a glance — locked plates sit
 * dark behind their chain, the next one available breathes, cleared ones carry
 * a stamp and flawless ones a gold star. Selecting a mission opens its briefing
 * on the right: the name, its one line of identity, the sixteen cards it is
 * played with, and what the opponent will do to you.
 */
public class CampaignScreen extends Screen {

    private static final int ROW_H = 30;
    private static final int LIST_W = 210;
    private static final int PAD = 12;

    private static final int INK = 0xFFF2ECDD;
    private static final int INK_DIM = 0xFF9A93A8;
    private static final int PLATE = 0xFF241F33;
    private static final int PLATE_LOCKED = 0xFF171425;
    private static final int GOLD = 0xFFE3C071;

    private int selected = 1;
    private int scroll;
    private int listX, listY, listH, panelX, panelW;
    private int[] beginRect = {0, 0, 0, 0};

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
        selected = Mth.clamp(ClientCampaign.highestCleared() + 1, 1, CampaignDecks.count());
        ensureVisible();
    }

    private int rowsVisible() {
        return Math.max(1, listH / ROW_H);
    }

    private void ensureVisible() {
        int i = selected - 1;
        if (i < scroll) scroll = i;
        if (i >= scroll + rowsVisible()) scroll = i - rowsVisible() + 1;
        scroll = Mth.clamp(scroll, 0, Math.max(0, CampaignDecks.count() - rowsVisible()));
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
        String sub = done + " of " + CampaignDecks.count() + " missions cleared";
        g.drawCenteredString(font, sub, width / 2, 42, INK_DIM);
        int barW = Math.min(340, width - 40);
        int barX = width / 2 - barW / 2;
        g.fill(barX, 54, barX + barW, 57, 0x40000000);
        if (done > 0) {
            g.fill(barX, 54, barX + barW * done / CampaignDecks.count(), 57, GOLD);
        }

        drawRoute(g, mouseX, mouseY, now);
        drawBriefing(g, mouseX, mouseY, now);

        g.drawCenteredString(font, "scroll the route  ·  ESC to close", width / 2, height - 11, 0xFF6C6480);
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
            // the category's colour as an identity stripe
            int accent = m.anchor().accent();
            g.fill(listX, y, listX + 3, y + ROW_H - 4, unlocked ? accent : 0xFF3A3350);
            if (isSel) {
                g.renderOutline(listX, y, LIST_W, ROW_H - 4, GOLD);
            }

            // the number bead on the chain
            int beadCol = cleared ? 0xFF3D8B3D : unlocked ? accent : 0xFF3A3350;
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
                g.drawString(font, "✔", listX + LIST_W - 14, y + 10, 0xFF3D8B3D, false);
            } else if (unlocked) {
                // the one you can play breathes
                float pulse = 0.5f + 0.5f * (float) Math.sin(now / 400.0);
                int a = (int) (0x60 + 0x8F * pulse) << 24;
                g.fill(listX + LIST_W - 16, y + 12, listX + LIST_W - 10, y + 18, a | (accent & 0xFFFFFF));
            }
        }
    }

    private void drawBriefing(GuiGraphics g, int mouseX, int mouseY, long now) {
        CampaignMission m = CampaignDecks.byIndex(selected);
        if (m == null || panelW < 120) {
            return;
        }
        boolean unlocked = ClientCampaign.unlocked(m);
        int y = listY;
        int x1 = panelX + panelW;

        g.fill(panelX, y, x1, listY + listH, 0x66120E1E);
        g.fill(panelX, y, x1, y + 2, m.anchor().accent());
        y += 8;

        var pose = g.pose();
        pose.pushPose();
        pose.translate(panelX + 8, y, 0);
        pose.scale(1.4f, 1.4f, 1f);
        g.drawString(font, "MISSION " + m.index(), 0, 0, m.anchor().accent(), false);
        pose.popPose();
        y += 16;
        pose.pushPose();
        pose.translate(panelX + 8, y, 0);
        pose.scale(1.6f, 1.6f, 1f);
        g.drawString(font, m.name(), 0, 0, INK, false);
        pose.popPose();
        y += 22;

        for (var line : font.split(Component.literal(m.tagline()), panelW - 16)) {
            g.drawString(font, line, panelX + 8, y, INK_DIM, false);
            y += 10;
        }
        y += 6;

        // threat
        StringBuilder skulls = new StringBuilder();
        for (int i = 0; i < 5; i++) skulls.append(i < m.threat() ? "◆" : "◇");
        g.drawString(font, "THREAT  " + skulls, panelX + 8, y, 0xFFD8735E, false);
        y += 12;
        g.drawString(font, m.opponentLabel(), panelX + 8, y, INK_DIM, false);
        y += 16;

        // the deck: sixteen chips, anchor cards in the set colour, padding grey
        g.drawString(font, "THE DECK  ·  16 cards, dealt between you", panelX + 8, y, GOLD, false);
        y += 12;
        List<MobCard> deck = CampaignDecks.deck(m);
        int cx = panelX + 8;
        int rowTop = y;
        for (MobCard card : deck) {
            boolean anchor = MobCategories.of(card.id()) == m.anchor();
            String label = card.displayName();
            int w = font.width(label) + 8;
            if (cx + w > x1 - 8) {
                cx = panelX + 8;
                rowTop += 12;
            }
            g.fill(cx, rowTop, cx + w, rowTop + 10, anchor ? (0x66000000 | (m.anchor().accent() & 0xFFFFFF))
                    : 0x40FFFFFF);
            g.drawString(font, label, cx + 4, rowTop + 1, anchor ? INK : 0xFFB9B2C8, false);
            cx += w + 3;
        }
        y = rowTop + 18;
        g.drawString(font, "coloured = " + m.anchor().label() + "  ·  grey = brought in",
                panelX + 8, y, 0xFF6C6480, false);
        y += 16;

        g.drawString(font, "REWARD  ·  first clear only", panelX + 8, y, GOLD, false);
        y += 11;
        for (var line : font.split(Component.literal(
                com.jrpetty.mobtrumps.CampaignRewards.forMission(m.index()).label()), panelW - 16)) {
            g.drawString(font, line, panelX + 8, y, INK_DIM, false);
            y += 10;
        }
        y += 6;

        g.drawString(font, "TROPHY", panelX + 8, y, GOLD, false);
        MobCard trophy = com.jrpetty.mobtrumps.game.MobCards.byId(m.trophyMob());
        g.drawString(font, trophy == null ? "—" : trophy.displayName() + " — Trophy print, first clear only",
                panelX + 8, y + 11, INK_DIM, false);

        // begin
        String label = !unlocked ? "Locked"
                : ClientCampaign.cleared(m) ? "Play again" : "Begin";
        int bw = Math.max(120, font.width(label) + 32);
        int bx = panelX + (panelW - bw) / 2;
        int by = listY + listH - 26;
        beginRect = new int[]{bx, by, bw, 20};
        boolean hover = unlocked && inRect(mouseX, mouseY, beginRect);
        int base = !unlocked ? 0xFF2A2440 : hover ? 0xFF4B8F3E : 0xFF3A7A32;
        if (unlocked) {
            float pulse = 0.5f + 0.5f * (float) Math.sin(now / 380.0);
            g.fill(bx - 2, by - 2, bx + bw + 2, by + 22, ((int) (0x28 + 0x24 * pulse) << 24) | 0x00E3C071);
        }
        g.fill(bx, by, bx + bw, by + 20, base);
        g.renderOutline(bx, by, bw, 20, unlocked ? (hover ? GOLD : 0x66FFFFFF) : 0xFF3A3350);
        g.drawString(font, label, bx + (bw - font.width(label)) / 2, by + 6,
                unlocked ? 0xFFFFFFFF : 0xFF6C6480, true);
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
                selected = scroll + r + 1;
                click(1.0f);
                return true;
            }
        }
        CampaignMission m = CampaignDecks.byIndex(selected);
        if (m != null && ClientCampaign.unlocked(m) && inRect((int) mouseX, (int) mouseY, beginRect)) {
            PacketDistributor.sendToServer(CampaignActionPayload.begin(m.index()));
            click(1.3f);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double dx, double dy) {
        int max = Math.max(0, CampaignDecks.count() - rowsVisible());
        scroll = Mth.clamp(scroll - (int) Math.signum(dy), 0, max);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 264) { // down
            selected = Math.min(CampaignDecks.count(), selected + 1);
            ensureVisible();
            return true;
        }
        if (keyCode == 265) { // up
            selected = Math.max(1, selected - 1);
            ensureVisible();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
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
