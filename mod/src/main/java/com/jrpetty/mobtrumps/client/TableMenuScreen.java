package com.jrpetty.mobtrumps.client;

import com.jrpetty.mobtrumps.DeckManager;
import com.jrpetty.mobtrumps.DuelTables;
import com.jrpetty.mobtrumps.TableActionPayload;
import com.jrpetty.mobtrumps.game.MobCard;
import com.jrpetty.mobtrumps.game.MobCards;
import com.mojang.math.Axis;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The dueling table's home screen — the casino floor of Mob Trumps. Everything
 * eases in when it opens: the title pops, the VS AI panel slides in from the
 * left, VS PLAYER from the right and the deck bar rises from the bottom, with
 * hit-boxes tracking the animation the whole way. Pick VS AI (easy / normal /
 * hard) or VS PLAYER (best of 1/3/5, or draft), and choose whether you battle
 * with your own deck — previewed face-up in a little fan — or a random deal.
 */
public class TableMenuScreen extends Screen {

    // felt & trim palette
    /** Where the deck fan's centre sits, measured back from the panel's right edge. */
    private static final int FAN_INSET = 40;
    /** Half the fan's drawn width, tilt included — the button row must clear it. */
    private static final int FAN_HALF_W = 26;

    private static final int FELT_LIGHT = 0xFF14503C;
    private static final int FELT_DARK = 0xFF072A1F;
    private static final int PANEL = 0xC0081E16;
    private static final int PANEL_EDGE = 0xFF2E5C48;
    private static final int GOLD = 0xFFE9C46A;
    private static final int GOLD_DIM = 0xFF9A7F45;
    private static final int TEXT_DIM = 0xFFB9C8C0;

    private static final long ENTER_MS = 300L;

    private final BlockPos pos;
    private final String seatedName;
    private final int seatedMode;
    private final boolean selfSeated;

    /** Remembered for the session so re-opening the table keeps your choice. */
    private static boolean useMyDeck = true;

    private record Btn(String key, int x, int y, int w, int h, boolean enabled) {
        boolean hit(double mx, double my) {
            return enabled && mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private final List<Btn> buttons = new ArrayList<>();
    private final long openedAt = System.currentTimeMillis();

    public TableMenuScreen(BlockPos pos, String seatedName, int seatedMode, boolean selfSeated) {
        super(Component.literal("Dueling Table"));
        this.pos = pos;
        this.seatedName = seatedName == null ? "" : seatedName;
        this.seatedMode = seatedMode;
        this.selfSeated = selfSeated;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private boolean deckReady() {
        return ClientCollection.deck().size() >= DeckManager.MIN_DECK;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // vanilla background (and its blur pass) FIRST — calling this at the end
        // of the frame blurs the whole menu instead of the world behind it
        super.render(g, mouseX, mouseY, partialTick);
        long t = System.currentTimeMillis() - openedAt;
        float in = easeOutCubic(Mth.clamp(t / (float) ENTER_MS, 0f, 1f));
        buttons.clear();

        // --- the table: layered felt, speckle grain, vignette, gold rails ---
        g.fillGradient(0, 0, width, height, FELT_LIGHT, FELT_DARK);
        for (int i = 0; i < 90; i++) {
            int sx = (i * 97 + 31) % Math.max(1, width);
            int sy = (i * 61 + 17) % Math.max(1, height);
            g.fill(sx, sy, sx + 1, sy + 1, 0x0DFFFFFF);
        }
        g.fillGradient(0, 0, width, height / 3, 0x66000000, 0x00000000);
        g.fillGradient(0, height * 2 / 3, width, height, 0x00000000, 0x88000000);
        g.fill(0, 0, width, 2, GOLD_DIM);
        g.fill(0, height - 2, width, height, GOLD_DIM);

        // decorative tilted card backs breathing on the felt
        decoCard(g, 30, height - 46, -14f + 2f * (float) Math.sin(t / 900.0));
        decoCard(g, width - 64, height - 42, 11f + 2f * (float) Math.sin(t / 760.0 + 1.7));

        // --- title: pops in with overshoot, then wears a slow shine sweep ---
        float titleScale = 2.1f * easeOutBack(Mth.clamp(t / (float) ENTER_MS, 0f, 1f));
        var pose = g.pose();
        pose.pushPose();
        pose.translate(width / 2f, 22f, 0);
        pose.scale(Math.max(0.05f, titleScale), Math.max(0.05f, titleScale), 1f);
        String title = "MOB TRUMPS";
        g.drawString(font, title, -font.width(title) / 2, -4, GOLD, true);
        pose.popPose();
        if (t > ENTER_MS) {
            float sweep = ((t - ENTER_MS) % 2600L) / 2600f;
            int sweepX = (int) (width / 2f - 90 + sweep * 180);
            shine(g, sweepX, 12, 34, 10, 0x2A);
        }
        g.drawCenteredString(font, "— DUELING TABLE —", width / 2, 38, TEXT_DIM);

        int panelW = Math.min(420, width - 20);
        int colW = (panelW - 12) / 2;
        // panels slide in from their own side; rects follow the same offsets
        int slide = Math.round((1f - in) * 34f);
        int leftX = (width - panelW) / 2 - slide;
        int rightX = (width - panelW) / 2 + colW + 12 + slide;
        int panelTop = 52;
        int panelH = height - panelTop - 64;

        // --- VS AI panel ---
        panel(g, leftX, panelTop, colW, panelH, "VS  AI");
        int by = panelTop + 22;
        by = modeButton(g, "cpu_0", leftX + 8, by, colW - 16, "EASY", 1,
                "Plays random stats", 0xFF2E7D46, mouseX, mouseY, t);
        by = modeButton(g, "cpu_1", leftX + 8, by, colW - 16, "NORMAL", 2,
                "Leads its best stat", 0xFF2A5F8A, mouseX, mouseY, t);
        by = modeButton(g, "cpu_2", leftX + 8, by, colW - 16, "HARD", 3,
                "Reads the odds & bluffs", 0xFF8A3A2E, mouseX, mouseY, t);
        drawCenteredFitted(g, "CPU deck: same size as yours,", leftX + colW / 2, by + 2, colW - 12, TEXT_DIM);
        drawCenteredFitted(g, "mostly commons, one legendary", leftX + colW / 2, by + 12, colW - 12, TEXT_DIM);
        drawCenteredFitted(g, "and levelled to match your holos", leftX + colW / 2, by + 22, colW - 12, TEXT_DIM);
        // a different game on the same cards, so it lives under the duel modes
        // rather than in the deck row, which has no width left to give
        int tw = by + 40;
        g.fill(leftX + 8, tw - 6, leftX + colW - 8, tw - 5, 0x30FFFFFF);
        modeButton(g, "twentyone", leftX + 8, tw, colW - 16, "TWENTY-ONE", 0,
                "Call a stat, then the card turns", 0xFF7A5AA8, mouseX, mouseY, t);

        // --- VS PLAYER panel ---
        panel(g, rightX, panelTop, colW, panelH, "VS  PLAYER");
        int py = panelTop + 22;
        if (!seatedName.isEmpty() && !selfSeated) {
            // someone is waiting: one big pulsing challenge button
            String modeLabel = DuelTables.Mode.values()[
                    Mth.clamp(seatedMode, 0, DuelTables.Mode.values().length - 1)].label;
            float pulse = 0.75f + 0.25f * (float) Math.sin(t / 300.0);
            int glow = ((int) (0x50 * pulse) << 24) | 0x00FFD54A;
            int chY = py + 14;
            g.fill(rightX + 6, chY - 4, rightX + colW - 6, chY + 40, glow);
            bigButton(g, "challenge", rightX + 10, chY, colW - 20, 26,
                    "CHALLENGE " + seatedName.toUpperCase(Locale.ROOT),
                    0xFF9A6A18, 0xFFC08A28, mouseX, mouseY, true);
            g.drawCenteredString(font, "They chose: " + modeLabel, rightX + colW / 2, chY + 32, GOLD);
            g.drawCenteredString(font, "Winner takes the match!", rightX + colW / 2, chY + 46, TEXT_DIM);
        } else if (selfSeated) {
            String modeLabel = DuelTables.Mode.values()[
                    Mth.clamp(seatedMode, 0, DuelTables.Mode.values().length - 1)].label;
            // gentle "waiting" dots so the seat feels alive
            String dots = ".".repeat((int) ((t / 400) % 4));
            g.drawCenteredString(font, "You're seated — " + modeLabel, rightX + colW / 2, py + 8, GOLD);
            g.drawCenteredString(font, "Waiting for a challenger" + dots, rightX + colW / 2, py + 20, TEXT_DIM);
            bigButton(g, "stand", rightX + 14, py + 34, colW - 28, 18, "Stand up",
                    0xFF5A2530, 0xFF7A3140, mouseX, mouseY, true);
        } else {
            py = modeButton(g, "seat_0", rightX + 8, py, colW - 16, "BEST OF 1", 0,
                    "One game, sudden death", 0xFF3A5E2C, mouseX, mouseY, t);
            py = modeButton(g, "seat_1", rightX + 8, py, colW - 16, "BEST OF 3", 0,
                    "First to two wins", 0xFF3A5E2C, mouseX, mouseY, t);
            py = modeButton(g, "seat_2", rightX + 8, py, colW - 16, "BEST OF 5", 0,
                    "First to three wins", 0xFF3A5E2C, mouseX, mouseY, t);
            py = modeButton(g, "seat_3", rightX + 8, py, colW - 16, "DRAFT", 0,
                    "Draft from ALL cards, this game only", 0xFF5E4A8A, mouseX, mouseY, t);
            drawCenteredFitted(g, "You'll wait at the table until", rightX + colW / 2, py + 2, colW - 12, TEXT_DIM);
            drawCenteredFitted(g, "another player clicks it", rightX + colW / 2, py + 12, colW - 12, TEXT_DIM);
        }

        // --- deck bar: rises in from the bottom ---
        int barLift = Math.round((1f - in) * 26f);
        int barX = (width - panelW) / 2;
        int barY = height - 58 + barLift;
        g.fill(barX, barY, barX + panelW, barY + 44, PANEL);
        g.renderOutline(barX, barY, panelW, 44, PANEL_EDGE);
        g.drawString(font, "BATTLE DECK", barX + 8, barY + 5, GOLD, false);
        boolean ready = deckReady();
        int deckN = ClientCollection.deck().size();
        // One measured row: the two deck pills run from the left, the two
        // buttons back from the card fan on the right. Everything used to sit
        // at hardcoded offsets, so "Random deal (practice)" ran straight under
        // the Edit Deck button. If the row cannot fit, the labels give way in
        // order of how much they can afford to lose.
        String myLabel = "My Deck (" + deckN + ")";
        String randLabel = "Random deal (practice)";
        String editLabel = "Edit Deck";
        String campLabel = "Campaign " + ClientCampaign.clearedCount()
                + "/" + com.jrpetty.mobtrumps.game.CampaignDecks.count();
        String hallLabel = "Hall";
        int rowLeft = barX + 8;
        // The fan is drawn at 0.16 scale from a 170x236 card, three of them
        // stepped 7px apart and tilted +-14 degrees: about 50px wide around its
        // centre, rising 38px from its baseline. The old 44px reserve was a
        // guess and the fan sat straight on top of the Hall and Campaign
        // buttons, clipping them. Derive the reserve from the geometry instead.
        int fanCx = barX + panelW - FAN_INSET;
        int rowRight = fanCx - FAN_HALF_W - 6;
        int gap = 6;
        int room = Math.max(80, rowRight - rowLeft);

        // Budget the row before drawing any of it, then trim each label into
        // the width it was given. Shrinking labels and hoping was not enough:
        // whichever element measured widest still ran over its neighbour.
        int rightCap = room * 62 / 100;
        int hallW = Math.min(font.width(hallLabel) + 14, rightCap / 5);
        int campW = Math.min(font.width(campLabel) + 16, rightCap * 45 / 100);
        int editW = Math.max(26, Math.min(font.width(editLabel) + 16,
                rightCap - campW - hallW - gap * 2));
        int hallX = rowRight - hallW;
        int campX = hallX - gap - campW;
        int editX = campX - gap - editW;

        int leftRoom = Math.max(56, editX - gap - rowLeft);
        int myW = Math.min(font.width(myLabel) + 14, Math.max(24, leftRoom * 40 / 100));
        int randW = Math.max(24, leftRoom - myW - gap);

        bigButton(g, "hall", hallX, barY + 17, hallW, 14, fit(hallLabel, hallW - 14),
                0xFF8A6A1A, 0xFFB89228, mouseX, mouseY, true);
        bigButton(g, "campaign", campX, barY + 17, campW, 14, fit(campLabel, campW - 16),
                0xFF5A3A8A, 0xFF7A52B4, mouseX, mouseY, true);
        bigButton(g, "deck_edit", editX, barY + 17, editW, 14, fit(editLabel, editW - 16),
                0xFF2A5F8A, 0xFF3A7FB4, mouseX, mouseY, true);
        pill(g, "deck_my", rowLeft, barY + 18, fit(myLabel, myW - 14),
                useMyDeck && ready, ready, mouseX, mouseY);
        pill(g, "deck_rand", rowLeft + myW + gap, barY + 18, fit(randLabel, randW - 14),
                !useMyDeck || !ready, true, mouseX, mouseY);
        // a dealt hand costs nothing to enter, so it earns nothing either
        String note = !ready
                ? "Build a deck of " + DeckManager.MIN_DECK + "+ in the book — random deals don't count"
                : (useMyDeck ? "Your deck: wins and awards count"
                             : "Practice: no wins, no awards, no progress");
        drawFitted(g, note, barX + 8, barY + 34,
                panelW - 16, !ready ? 0xFFCB8A8A : (useMyDeck ? 0xFF8FD08F : TEXT_DIM));

        // a face-up fan of your actual top deck cards, so the choice is real
        fan(g, fanCx, barY + 41, t);

        // slim footer strip so the hint reads as part of the frame
        g.fill(0, height - 13, width, height - 2, 0x66051A12);
        g.drawCenteredString(font, "ESC to close", width / 2, height - 11, 0xFF7E9288);
    }

    // --- widgets ---

    private void panel(GuiGraphics g, int x, int y, int w, int h, String head) {
        g.fill(x + 2, y + 3, x + w + 2, y + h + 3, 0x44000000); // drop shadow
        g.fill(x, y, x + w, y + h, PANEL);
        g.renderOutline(x, y, w, h, PANEL_EDGE);
        g.fill(x, y, x + w, y + 14, 0x66000000);
        g.fill(x, y + 14, x + w, y + 15, GOLD_DIM);
        g.drawCenteredString(font, head, x + w / 2, y + 3, GOLD);
        // little gold L-marks in the corners
        int len = 6;
        for (int[] c : new int[][]{{x + 3, y + 3, 1, 1}, {x + w - 3, y + 3, -1, 1},
                {x + 3, y + h - 3, 1, -1}, {x + w - 3, y + h - 3, -1, -1}}) {
            g.fill(c[0], c[1], c[0] + c[2] * len, c[1] + c[3], GOLD_DIM);
            g.fill(c[0], c[1], c[0] + c[2], c[1] + c[3] * len, GOLD_DIM);
        }
    }

    /**
     * A mode row: identity stripe, bold label, difficulty pips, description,
     * layered gold glow + a nudging chevron on hover. Returns the next y.
     */
    private int modeButton(GuiGraphics g, String key, int x, int y, int w, String label,
                           int pips, String desc, int base, int mouseX, int mouseY, long t) {
        int h = 26;
        Btn btn = new Btn(key, x, y, w, h, true);
        buttons.add(btn);
        boolean hover = btn.hit(mouseX, mouseY);
        if (hover) {
            // layered outer glow instead of scaling, so hit-boxes never lie
            g.fill(x - 3, y - 3, x + w + 3, y + h + 3, 0x28E9C46A);
            g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0x3CE9C46A);
        }
        g.fill(x, y, x + w, y + h, hover ? brighten(base) : base);
        g.renderOutline(x, y, w, h, hover ? GOLD : 0x66FFFFFF);
        g.fill(x, y, x + 3, y + h, hover ? GOLD : 0x88FFFFFF); // identity stripe
        g.drawString(font, label, x + 8, y + 4, 0xFFFFFFFF, true);
        // difficulty pips: one gold square per skull-level
        for (int i = 0; i < pips; i++) {
            int px = x + 10 + font.width(label) + i * 6;
            g.fill(px, y + 6, px + 4, y + 10, GOLD);
            g.fill(px, y + 6, px + 4, y + 7, 0xFFFFF0B0);
        }
        // shrink the description to fit rather than letting it bleed past the
        // card's right edge, which is what "…this game only" used to do
        drawFitted(g, desc, x + 8, y + 15, w - 18, hover ? 0xFFEFE8D0 : TEXT_DIM);
        if (hover) {
            int nudge = (int) (2 * Math.sin(t / 150.0));
            g.drawString(font, ">", x + w - 11 + nudge, y + 9, GOLD, true);
        }
        return y + h + 5;
    }

    private void bigButton(GuiGraphics g, String key, int x, int y, int w, int h, String label,
                           int base, int hoverCol, int mouseX, int mouseY, boolean enabled) {
        Btn btn = new Btn(key, x, y, w, h, enabled);
        buttons.add(btn);
        boolean hover = btn.hit(mouseX, mouseY);
        if (hover) {
            g.fill(x - 2, y - 2, x + w + 2, y + h + 2, 0x30E9C46A);
        }
        g.fill(x, y, x + w, y + h, hover ? hoverCol : base);
        g.renderOutline(x, y, w, h, hover ? GOLD : 0x66FFFFFF);
        g.drawString(font, label, x + (w - font.width(label)) / 2, y + (h - 8) / 2 + 1, 0xFFFFFFFF, true);
    }

    private void pill(GuiGraphics g, String key, int x, int y, String label,
                      boolean active, boolean enabled, int mouseX, int mouseY) {
        int w = font.width(label) + 14;
        Btn btn = new Btn(key, x, y, w, 13, enabled);
        buttons.add(btn);
        boolean hover = btn.hit(mouseX, mouseY);
        int bg = !enabled ? 0xFF23312B : active ? 0xFF2E7D46 : hover ? 0xFF20463A : 0xFF16352A;
        g.fill(x, y, x + w, y + 13, bg);
        g.renderOutline(x, y, w, 13, active ? 0xFF55E06A : PANEL_EDGE);
        g.drawString(font, label, x + 7, y + 3,
                !enabled ? 0xFF5E6E66 : active ? 0xFFFFFFFF : TEXT_DIM, false);
    }

    /**
     * A soft vertical band that fades out to both sides — the highlight that
     * travels across the title.
     *
     * <p>Hand-rolled because {@code fillGradient} only ramps top-to-bottom. Used
     * for a sweep it draws a hard-edged rectangle sitting over the letters,
     * which read as a grey block punched into the wordmark rather than a shine.
     */
    private void shine(GuiGraphics g, int cx, int top, int bottom, int halfW, int peakAlpha) {
        for (int dx = -halfW; dx < halfW; dx++) {
            float falloff = 1f - Math.abs(dx) / (float) halfW;
            int alpha = (int) (peakAlpha * falloff * falloff);
            if (alpha > 0) {
                g.fill(cx + dx, top, cx + dx + 1, bottom, (alpha << 24) | 0xFFFFFF);
            }
        }
    }

    /** A tilted decorative card back on the felt. */
    private void decoCard(GuiGraphics g, int x, int y, float deg) {
        var pose = g.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        pose.mulPose(Axis.ZP.rotationDegrees(deg));
        pose.scale(0.32f, 0.32f, 1f);
        pose.translate(-CardRenderer.CARD_W / 2f, -CardRenderer.CARD_H / 2f, 0);
        CardRenderer.renderBack(g, font, 0, 0, 1f);
        pose.popPose();
    }

    /**
     * A little fan of your top deck cards, FACE UP, breathing gently — a real
     * preview of what you'll battle with. Card backs when the deck is empty.
     */
    private void fan(GuiGraphics g, int cx, int baseY, long t) {
        List<String> deck = ClientCollection.deck();
        int cards = 3;
        for (int i = 0; i < cards; i++) {
            float breathe = 1.2f * (float) Math.sin(t / 800.0 + i * 0.9);
            float deg = (i - (cards - 1) / 2f) * 14f + breathe;
            var pose = g.pose();
            pose.pushPose();
            pose.translate(cx + i * 7 - (cards - 1) * 3, baseY, 0);
            pose.mulPose(Axis.ZP.rotationDegrees(deg));
            pose.scale(0.16f, 0.16f, 1f);
            pose.translate(-CardRenderer.CARD_W / 2f, -CardRenderer.CARD_H, 0);
            MobCard card = i < deck.size() ? MobCards.byId(deck.get(i)) : null;
            if (card != null) {
                CardRenderer.renderCard(g, font, card, 0, 0, 1f, 0, 0, null, false, false);
            } else {
                CardRenderer.renderBack(g, font, 0, 0, 1f);
            }
            pose.popPose();
        }
    }

    /** Draw text at x, squeezing it horizontally if it would exceed maxWidth. */
    /** Cut a label down to a width it was budgeted, with an ellipsis if cut. */
    private String fit(String text, int maxWidth) {
        if (maxWidth <= 0) {
            return "";
        }
        if (font.width(text) <= maxWidth) {
            return text;
        }
        String cut = font.plainSubstrByWidth(text, Math.max(1, maxWidth - font.width("…")));
        return cut.isEmpty() ? "" : cut + "…";
    }

    private void drawFitted(GuiGraphics g, String text, int x, int y, int maxWidth, int color) {
        int tw = font.width(text);
        if (tw <= maxWidth) {
            g.drawString(font, text, x, y, color, false);
            return;
        }
        float scale = maxWidth / (float) tw;
        var pose = g.pose();
        pose.pushPose();
        pose.translate(x, y + (font.lineHeight * (1 - scale)) / 2f, 0);
        pose.scale(scale, scale, 1f);
        g.drawString(font, text, 0, 0, color, false);
        pose.popPose();
    }

    /** As {@link #drawFitted}, centred on cx. */
    private void drawCenteredFitted(GuiGraphics g, String text, int cx, int y, int maxWidth, int color) {
        int tw = font.width(text);
        float scale = tw <= maxWidth ? 1f : maxWidth / (float) tw;
        var pose = g.pose();
        pose.pushPose();
        pose.translate(cx, y + (font.lineHeight * (1 - scale)) / 2f, 0);
        pose.scale(scale, scale, 1f);
        g.drawString(font, text, -tw / 2, 0, color, false);
        pose.popPose();
    }

    private static int brighten(int argb) {
        int r = Math.min(255, ((argb >> 16) & 0xFF) + 28);
        int gg = Math.min(255, ((argb >> 8) & 0xFF) + 28);
        int b = Math.min(255, (argb & 0xFF) + 28);
        return (argb & 0xFF000000) | (r << 16) | (gg << 8) | b;
    }

    private static float easeOutCubic(float t) {
        float u = 1f - t;
        return 1f - u * u * u;
    }

    private static float easeOutBack(float t) {
        float c1 = 1.70158f;
        float c3 = c1 + 1f;
        float u = t - 1f;
        return 1f + c3 * u * u * u + c1 * u * u;
    }

    // --- interaction ---

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        for (Btn btn : buttons) {
            if (!btn.hit(mouseX, mouseY)) {
                continue;
            }
            click();
            String key = btn.key();
            if (key.startsWith("cpu_")) {
                int difficulty = key.charAt(4) - '0';
                send(TableActionPayload.CPU, difficulty, useMyDeck && deckReady());
                // the battle screen replaces this menu when the server deals
            } else if (key.startsWith("seat_")) {
                send(TableActionPayload.SEAT, key.charAt(5) - '0', false);
                onClose();
            } else if (key.equals("challenge")) {
                send(TableActionPayload.CHALLENGE, 0, false);
                onClose();
            } else if (key.equals("stand")) {
                send(TableActionPayload.STAND, 0, false);
                onClose();
            } else if (key.equals("deck_my")) {
                useMyDeck = true;
            } else if (key.equals("deck_rand")) {
                useMyDeck = false;
            } else if (key.equals("hall") && minecraft != null) {
                minecraft.setScreen(new HallScreen());
            } else if (key.equals("campaign") && minecraft != null) {
                minecraft.setScreen(new CampaignScreen());
                return true;
            } else if (key.equals("twentyone")) {
                send(TableActionPayload.TWENTY_ONE, 0, false);
                return true;
            } else if (key.equals("deck_edit") && minecraft != null) {
                minecraft.setScreen(new DeckBuilderScreen(this));
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void send(int action, int arg, boolean useDeck) {
        PacketDistributor.sendToServer(new TableActionPayload(pos, action, arg, useDeck));
    }

    private void click() {
        if (minecraft != null) {
            minecraft.getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f));
        }
    }
}
