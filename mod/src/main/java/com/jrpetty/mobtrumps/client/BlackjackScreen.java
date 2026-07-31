package com.jrpetty.mobtrumps.client;

import com.jrpetty.mobtrumps.BlackjackActionPayload;
import com.jrpetty.mobtrumps.BlackjackSyncPayload;
import com.jrpetty.mobtrumps.game.Blackjack;
import com.jrpetty.mobtrumps.game.MobCard;
import com.jrpetty.mobtrumps.game.MobCards;
import com.jrpetty.mobtrumps.game.Stat;
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
 * The Twenty-One table.
 *
 * <p>The screen's real job is showing the odds. Calling a stat before the card
 * turns means betting on that stat's shape, and the six shapes differ enormously
 * — thirty of the eighty-one mobs have no Attack, while Farmable is spread right
 * across the top. Measured, a player who knows those curves beats one who does
 * not by about 29 points of win rate, which is far too much to leave as trivia.
 * So each stat carries its own spread and its live chance of busting from the
 * total actually on the table.
 */
public class BlackjackScreen extends Screen {

    private static final int FELT_1 = 0xFF0E3B2C;
    private static final int FELT_0 = 0xFF04170F;
    private static final int GOLD = 0xFFE9C46A;
    private static final int GOLD_DIM = 0xFF8A6A2A;
    private static final int INK = 0xFFF2ECDD;
    private static final int DIM = 0xFF7E9A8B;
    private static final int SAFE = 0xFF6BE87A;
    private static final int RISK = 0xFFF0625A;
    private static final int EDGE = 0xFF1B4536;

    private static final long FLIP_MS = 260L;

    private final int[][] statRects = new int[Stat.values().length][];
    private int[] dealRect;
    private int[] standRect;
    private int hovered = -1;

    public BlackjackScreen() {
        super(Component.literal("Twenty-One"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static String title(Stat stat) {
        return stat.label.toUpperCase(Locale.ROOT);
    }

    /** Room left before busting. Negative once the hand is already over. */
    private int room() {
        return Blackjack.TARGET - ClientBlackjack.playerTotal();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.fillGradient(0, 0, width, height, FELT_1, FELT_0);
        g.fill(0, 0, width, 2, GOLD_DIM);
        g.fill(0, height - 2, width, height, GOLD_DIM);

        int phase = ClientBlackjack.phase();
        boolean live = phase == BlackjackSyncPayload.PHASE_PLAYER;
        long since = System.currentTimeMillis() - ClientBlackjack.changedAt();

        // --- header -------------------------------------------------------
        String head = "TWENTY-ONE";
        var pose = g.pose();
        pose.pushPose();
        pose.translate(width / 2f, 14f, 0);
        pose.scale(1.8f, 1.8f, 1f);
        g.drawString(font, head, -font.width(head) / 2, 0, GOLD, true);
        pose.popPose();
        g.drawCenteredString(font, "call a stat, then the card turns",
                width / 2, 38, DIM);

        // --- totals -------------------------------------------------------
        int totalsY = 54;
        drawTotal(g, width / 2 - 110, totalsY, "YOU", ClientBlackjack.playerTotal(),
                ClientBlackjack.playerTotal() > Blackjack.TARGET);
        boolean showDealer = phase != BlackjackSyncPayload.PHASE_PLAYER
                && !ClientBlackjack.dealerDraws().isEmpty();
        drawTotal(g, width / 2 + 34, totalsY, "DEALER",
                showDealer ? ClientBlackjack.dealerTotal() : -1,
                showDealer && ClientBlackjack.dealerTotal() > Blackjack.TARGET);

        // --- the cards turned so far --------------------------------------
        drawDraws(g, width / 2 - 110, totalsY + 34, ClientBlackjack.playerDraws(), since);
        if (showDealer) {
            drawDraws(g, width / 2 + 34, totalsY + 34, ClientBlackjack.dealerDraws(), since);
        }

        // --- the six calls -------------------------------------------------
        int listY = Math.max(totalsY + 74, height - 150);
        g.drawString(font, "CALL A STAT", width / 2 - 110, listY - 12, GOLD, false);
        g.drawString(font, "chance it busts you", width / 2 + 34, listY - 12, DIM, false);
        hovered = -1;
        for (Stat stat : Stat.values()) {
            int i = stat.ordinal();
            int y = listY + i * 15;
            int x0 = width / 2 - 114;
            int x1 = width / 2 + 118;
            boolean spent = ClientBlackjack.used(i);
            boolean over = mouseX >= x0 && mouseX < x1 && mouseY >= y && mouseY < y + 14;
            boolean pickable = live && !spent;
            if (over && pickable) {
                hovered = i;
            }
            statRects[i] = new int[]{x0, y, x1 - x0, 14};

            g.fill(x0, y, x1, y + 14, spent ? 0x2A000000 : over && pickable ? 0x33E9C46A : 0x22000000);
            if (pickable && over) {
                g.renderOutline(x0, y, x1 - x0, 14, GOLD_DIM);
            }
            int label = spent ? 0xFF4E6A5C : INK;
            g.drawString(font, title(stat), x0 + 6, y + 3, label, false);

            if (spent) {
                g.drawString(font, "spent", x1 - 6 - font.width("spent"), y + 3, 0xFF4E6A5C, false);
                continue;
            }
            drawSpread(g, x0 + 62, y + 3, stat);
            double bust = MobCards.bustChance(stat, room());
            String pct = Math.round(bust * 100) + "%";
            int colour = bust <= 0.10 ? SAFE : bust >= 0.45 ? RISK : 0xFFE7C24A;
            g.drawString(font, pct, x1 - 6 - font.width(pct), y + 3, colour, false);
        }

        // --- buttons --------------------------------------------------------
        int by = listY + Stat.values().length * 15 + 8;
        String dealLabel = live ? "—" : "DEAL  (" + ClientBlackjack.stake() + " fragments)";
        dealRect = new int[]{width / 2 - 114, by, 150, 16};
        standRect = new int[]{width / 2 + 40, by, 78, 16};
        if (!live) {
            button(g, dealRect, dealLabel, mouseX, mouseY,
                    ClientBlackjack.fragments() >= ClientBlackjack.stake());
        }
        if (live) {
            button(g, standRect, "STAND", mouseX, mouseY, true);
        }

        // --- result ---------------------------------------------------------
        if (phase == BlackjackSyncPayload.PHASE_DONE
                && ClientBlackjack.result() != BlackjackSyncPayload.RESULT_NONE) {
            String text = switch (ClientBlackjack.result()) {
                case BlackjackSyncPayload.RESULT_PLAYER -> "YOU WIN";
                case BlackjackSyncPayload.RESULT_DEALER ->
                        ClientBlackjack.playerTotal() > Blackjack.TARGET ? "BUST" : "DEALER WINS";
                default -> "PUSH";
            };
            int colour = switch (ClientBlackjack.result()) {
                case BlackjackSyncPayload.RESULT_PLAYER -> SAFE;
                case BlackjackSyncPayload.RESULT_DEALER -> RISK;
                default -> GOLD;
            };
            float grow = Mth.clamp(since / 220f, 0f, 1f);
            pose.pushPose();
            pose.translate(width / 2f, totalsY - 16f, 0);
            pose.scale(Math.max(0.05f, 1.9f * grow), Math.max(0.05f, 1.9f * grow), 1f);
            g.drawString(font, text, -font.width(text) / 2, 0, colour, true);
            pose.popPose();
        }

        g.drawCenteredString(font, ClientBlackjack.fragments() + " fragments  ·  ESC to leave",
                width / 2, height - 14, DIM);
    }

    /** A stat's shape across the 81 mobs, as a tiny bar chart. */
    private void drawSpread(GuiGraphics g, int x, int y, Stat stat) {
        int[] spread = MobCards.spread(stat);
        int max = 1;
        for (int count : spread) {
            max = Math.max(max, count);
        }
        int room = room();
        for (int v = 0; v < spread.length; v++) {
            int h = spread[v] == 0 ? 0 : 1 + Math.round(spread[v] * 7f / max);
            int bx = x + v * 4;
            // bars past your remaining room are the ones that would bust you
            int colour = v > room ? 0xFFA8443C : 0xFF5E8A6E;
            if (h > 0) {
                g.fill(bx, y + 9 - h, bx + 3, y + 9, colour);
            } else {
                g.fill(bx, y + 8, bx + 3, y + 9, 0xFF2A3A32);
            }
        }
    }

    private void drawTotal(GuiGraphics g, int x, int y, String who, int total, boolean bust) {
        g.fill(x, y, x + 76, y + 26, 0x33000000);
        g.renderOutline(x, y, 76, 26, EDGE);
        g.drawString(font, who, x + 5, y + 3, DIM, false);
        String value = total < 0 ? "??" : String.valueOf(total);
        var pose = g.pose();
        pose.pushPose();
        pose.translate(x + 68f, y + 6f, 0);
        pose.scale(1.6f, 1.6f, 1f);
        g.drawString(font, value, -font.width(value), 0, bust ? RISK : INK, true);
        pose.popPose();
    }

    private void drawDraws(GuiGraphics g, int x, int y, List<ClientBlackjack.Draw> draws,
                           long since) {
        int i = 0;
        for (ClientBlackjack.Draw draw : draws) {
            MobCard card = MobCards.byId(draw.mobId());
            String name = card == null ? draw.mobId() : card.displayName();
            Stat stat = draw.stat() >= 0 && draw.stat() < Stat.values().length
                    ? Stat.values()[draw.stat()] : null;
            boolean newest = i == draws.size() - 1;
            int alpha = newest ? (int) (Mth.clamp(since / (float) FLIP_MS, 0f, 1f) * 255) : 255;
            int colour = (Math.max(40, alpha) << 24) | 0x00F2ECDD;
            String line = (stat == null ? "?" : stat.shortLabel) + "  " + fit(name, 74)
                    + "  +" + draw.value();
            g.drawString(font, line, x, y + i * 11, colour, false);
            i++;
        }
    }

    private String fit(String text, int maxWidth) {
        if (font.width(text) <= maxWidth) {
            return text;
        }
        String cut = font.plainSubstrByWidth(text, Math.max(1, maxWidth - font.width("…")));
        return cut.isEmpty() ? "" : cut + "…";
    }

    private void button(GuiGraphics g, int[] r, String label, int mouseX, int mouseY,
                        boolean enabled) {
        boolean over = enabled && mouseX >= r[0] && mouseX < r[0] + r[2]
                && mouseY >= r[1] && mouseY < r[1] + r[3];
        g.fill(r[0], r[1], r[0] + r[2], r[1] + r[3],
                !enabled ? 0x22000000 : over ? 0xFF1D5C43 : 0xFF124532);
        g.renderOutline(r[0], r[1], r[2], r[3], enabled ? GOLD_DIM : EDGE);
        g.drawString(font, label, r[0] + (r[2] - font.width(label)) / 2, r[1] + 4,
                enabled ? INK : 0xFF4E6A5C, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (ClientBlackjack.inHand()) {
                if (hovered >= 0) {
                    send(BlackjackActionPayload.call(hovered));
                    click();
                    return true;
                }
                if (hit(standRect, mouseX, mouseY)) {
                    send(BlackjackActionPayload.stand());
                    click();
                    return true;
                }
            } else if (hit(dealRect, mouseX, mouseY)
                    && ClientBlackjack.fragments() >= ClientBlackjack.stake()) {
                send(BlackjackActionPayload.deal());
                click();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
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

    private static void send(BlackjackActionPayload payload) {
        PacketDistributor.sendToServer(payload);
    }

    @Override
    public void onClose() {
        send(BlackjackActionPayload.leave());
        super.onClose();
    }
}
