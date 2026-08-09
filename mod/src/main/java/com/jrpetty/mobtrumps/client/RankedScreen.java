package com.jrpetty.mobtrumps.client;

import com.jrpetty.mobtrumps.RankTier;
import com.jrpetty.mobtrumps.RankedSyncPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.Locale;

import static com.jrpetty.mobtrumps.client.TableArt.BRASS;
import static com.jrpetty.mobtrumps.client.TableArt.BRASS_DARK;
import static com.jrpetty.mobtrumps.client.TableArt.BRASS_DIM;
import static com.jrpetty.mobtrumps.client.TableArt.DIM;
import static com.jrpetty.mobtrumps.client.TableArt.FAINT;
import static com.jrpetty.mobtrumps.client.TableArt.GOOD;
import static com.jrpetty.mobtrumps.client.TableArt.INK;

/**
 * The ranked ladder: who is what, and how far you are from the next rung.
 *
 * <p>It existed only as a printed list in chat, which is a poor way to read a
 * table and a worse way to find yourself in one. Here the whole season is on
 * screen — your crest and climb at the top, everyone else below, your own row
 * picked out wherever it falls.
 */
public class RankedScreen extends Screen {

    private static final int ROW_H = 18;
    /**
     * Where the ladder starts.
     *
     * <p>Must clear the whole header: the title, the season line, and the
     * standing card, which runs to RAIL + 4 + 28 + 38 = 77. It was 74, so the
     * card's bottom edge and its climb bar sat on top of the first row and the
     * divider was drawn inside the card.
     */
    private static final int HEAD_H = 86;

    private int scroll;

    public RankedScreen() {
        super(Component.literal("Ranked"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int rowsVisible() {
        return Math.max(1, (height - HEAD_H - 26) / ROW_H);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        TableArt.felt(g, width, height, width / 2, HEAD_H);
        TableArt.rail(g, width, height);

        RankedSyncPayload s = ClientRanked.state();
        int count = ClientRanked.count();
        int you = ClientRanked.youIndex();

        int pw = Math.min(430, width - 2 * TableArt.RAIL - 8);
        int px = (width - pw) / 2;

        drawHeader(g, px, pw, s, you);

        // --- the ladder ---
        int listTop = HEAD_H;
        int visible = rowsVisible();
        scroll = Mth.clamp(scroll, 0, Math.max(0, count - visible));
        for (int r = 0; r < visible && scroll + r < count; r++) {
            int i = scroll + r;
            int y = listTop + r * ROW_H;
            drawRow(g, px, pw, y, i, s, i == you, mouseX, mouseY);
        }

        if (count == 0) {
            g.drawCenteredString(font, "No ranked duels yet this season.",
                    width / 2, listTop + 20, DIM);
            g.drawCenteredString(font, "Win a duel to take a place on the ladder.",
                    width / 2, listTop + 32, FAINT);
        }

        // scroll hint only when there is somewhere to scroll
        if (count > visible) {
            String more = (scroll + visible) + " of " + count;
            g.drawString(font, more, px + pw - font.width(more), height - TableArt.RAIL - 11, FAINT);
        }
        g.drawString(font, ClientRanked.totalRanked() + " ranked",
                px, height - TableArt.RAIL - 11, FAINT);
    }

    private void drawHeader(GuiGraphics g, int px, int pw, RankedSyncPayload s, int you) {
        int top = TableArt.RAIL + 4;
        var pose = g.pose();
        pose.pushPose();
        pose.translate(px + 4, top, 0);
        pose.scale(1.6f, 1.6f, 1f);
        g.drawString(font, "RANKED", 0, 0, BRASS, true);
        pose.popPose();

        String season = "SEASON " + ClientRanked.season();
        g.drawString(font, season, px + 4, top + 16, DIM);
        String clock = "season ends in " + duration(ClientRanked.secondsLeft());
        g.drawString(font, clock, px + pw - font.width(clock), top + 16, FAINT);

        // --- your own standing, if you have one ---
        int cardY = top + 28;
        int cardH = 38;
        TableArt.plate(g, px, cardY, pw, cardH, BRASS_DIM);
        if (you < 0) {
            g.drawString(font, "You are unranked — win a duel to join the ladder.",
                    px + 8, cardY + 15, DIM);
        } else {
            int rating = s.rating(you);
            RankTier tier = RankTier.of(rating);
            // the crest sits in its own pool of the tier's light
            TableArt.pool(g, px + 19, cardY + cardH / 2, 26, tier.rgb, 0x38);
            // no pips here: the label beside it already spells out the
            // division, and at this size they would hang below the plate
            RankEmblem.draw(g, px + 7, cardY + 4, 26, tier, RankTier.division(rating), false);

            // the rating, large, in the tier's metal — the number the whole
            // screen is about should not be the smallest thing on it
            String big = String.valueOf(rating);
            var pose = g.pose();
            pose.pushPose();
            pose.translate(px + pw - 12 - font.width(big) * 1.6f, cardY + 14f, 0);
            pose.scale(1.6f, 1.6f, 1f);
            g.drawString(font, big, 0, 0, tier.rgb, true);
            pose.popPose();

            int tx = px + 42;
            g.drawString(font, RankTier.label(rating).toUpperCase(Locale.ROOT), tx, cardY + 6,
                    tier.rgb, true);
            int place = ClientRanked.yourPlace();
            String sub = (place > 0 ? "#" + place + " of " + ClientRanked.totalRanked() : "unranked")
                    + "   " + s.wins(you) + "W " + s.losses(you) + "L"
                    + "   peak " + s.peak(you);
            g.drawString(font, sub, tx, cardY + 18, DIM);

            // climb to the next rung, in a brass channel with the fill lit
            // along its top edge so it reads as inlay rather than paint
            int next = RankTier.nextStep(rating);
            int barX = tx;
            int barW = Math.max(40, px + pw - 88 - barX);
            int barY = cardY + 29;
            g.fill(barX - 1, barY - 1, barX + barW + 1, barY + 5, 0xFF0A140F);
            g.renderOutline(barX - 1, barY - 1, barW + 2, 6, TableArt.BRASS_DARK);
            float p = RankTier.progress(rating);
            int fill = Math.round(barW * p);
            g.fill(barX, barY, barX + fill, barY + 4, tier.rgb);
            g.fill(barX, barY, barX + fill, barY + 1, TableArt.lighten(tier.rgb, 1.35f));
            String goal = next < 0 ? "top of the ladder"
                    : (next - rating) + " to " + RankTier.badgeLabel(next);
            g.drawString(font, goal, barX + barW - font.width(goal), barY - 9, FAINT);

            if (rating > ClientRanked.decayFloor()) {
                String warn = "decays if idle";
                g.drawString(font, warn, px + pw - font.width(warn), cardY + cardH - 12, FAINT);
            }
        }
        g.fill(px, HEAD_H - 4, px + pw, HEAD_H - 3, BRASS_DARK);
    }

    private void drawRow(GuiGraphics g, int px, int pw, int y, int i, RankedSyncPayload s,
                         boolean mine, int mouseX, int mouseY) {
        int rating = s.rating(i);
        RankTier tier = RankTier.of(rating);
        boolean hover = mouseX >= px && mouseX < px + pw && mouseY >= y && mouseY < y + ROW_H - 1;

        int bg = mine ? 0x3A2E7D46 : (i % 2 == 0 ? 0x22000000 : 0x14000000);
        g.fill(px, y, px + pw, y + ROW_H - 1, hover ? TableArt.alpha(BRASS, 0x22) : bg);
        if (mine) {
            g.renderOutline(px, y, pw, ROW_H - 1, GOOD);
        }

        // the tier's colour runs down the row edge, so the ladder reads as
        // bands of metal even before a single label is read
        g.fill(px, y, px + 2, y + ROW_H - 1, tier.rgb);

        // the podium gets medal discs; everyone else a quiet number
        if (i < 3) {
            int medal = i == 0 ? 0xFFFFD54A : i == 1 ? 0xFFC7CCD1 : 0xFFCD7F32;
            int mc = px + 12;
            int my = y + ROW_H / 2 - 1;
            for (int r = 5; r >= 1; r--) {
                int shade = r == 5 ? TableArt.darken(medal, 0.55f)
                        : r >= 4 ? medal : TableArt.lighten(medal, 1.2f);
                g.fill(mc - r, my - r + 1, mc + r, my + r - 1, shade);
                g.fill(mc - r + 1, my - r, mc + r - 1, my + r, shade);
            }
            String n = String.valueOf(i + 1);
            g.drawString(font, n, mc - font.width(n) / 2 + 1, my - 4, 0xFF1A1206, false);
        } else {
            String place = "#" + (i + 1);
            g.drawString(font, place, px + 6, y + 5, FAINT, false);
        }

        RankEmblem.draw(g, px + 30, y + 1, 13, tier, RankTier.division(rating), false);

        g.drawString(font, fit(s.name(i), pw / 3), px + 50, y + 5, mine ? INK : 0xFFDCD5C6, false);

        String label = RankTier.badgeLabel(rating);
        int lx = px + pw / 2 + 10;
        g.drawString(font, label, lx, y + 5, tier.rgb, false);

        String wl = s.wins(i) + "W " + s.losses(i) + "L";
        String rate = String.valueOf(rating);
        // the number in the tier's metal: the column becomes a gradient of
        // rank, and a glance shows where the tier boundaries fall
        g.drawString(font, rate, px + pw - 8 - font.width(rate), y + 5, tier.rgb, false);
        g.drawString(font, wl, px + pw - 52 - font.width(wl), y + 5, DIM, false);
    }

    /** "2d 4h" / "3h 12m" / "8m" — a season clock nobody has to do sums on. */
    private static String duration(int seconds) {
        if (seconds <= 0) {
            return "moments";
        }
        int d = seconds / 86400;
        int h = (seconds % 86400) / 3600;
        int m = (seconds % 3600) / 60;
        if (d > 0) {
            return d + "d " + h + "h";
        }
        if (h > 0) {
            return h + "h " + m + "m";
        }
        return Math.max(1, m) + "m";
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double dx, double dy) {
        scroll = Mth.clamp(scroll - (int) Math.signum(dy), 0,
                Math.max(0, ClientRanked.count() - rowsVisible()));
        return true;
    }

    private String fit(String text, int maxWidth) {
        if (maxWidth <= 0 || font.width(text) <= maxWidth) {
            return text;
        }
        String cut = font.plainSubstrByWidth(text, Math.max(1, maxWidth - font.width("…")));
        return cut.isEmpty() ? "" : cut + "…";
    }
}
