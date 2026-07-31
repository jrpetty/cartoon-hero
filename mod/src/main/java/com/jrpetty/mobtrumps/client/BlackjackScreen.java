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
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The Twenty-One table.
 *
 * <p>The cards you have been dealt are the point of the screen, so they are
 * drawn as cards — art and all — rather than listed as text. A hand reads as a
 * hand, and you can see what you have been given at a glance.
 *
 * <p>No odds are shown. Working out that Farmable climbs fast and Attack is
 * often nothing is the game; a table that prints the answer beside every button
 * is a calculator with a felt background.
 */
public class BlackjackScreen extends Screen {

    private static final int FELT_1 = 0xFF0E3B2C;
    private static final int FELT_0 = 0xFF04170F;
    private static final int GOLD = 0xFFE9C46A;
    private static final int GOLD_DIM = 0xFF8A6A2A;
    private static final int INK = 0xFFF2ECDD;
    private static final int DIM = 0xFF7E9A8B;
    private static final int WIN = 0xFF6BE87A;
    private static final int LOSE = 0xFFF0625A;
    private static final int EDGE = 0xFF1B4536;

    private static final float CARD_SCALE = 0.22f;
    private static final int CARD_W = Math.round(CardRenderer.CARD_W * CARD_SCALE);
    private static final int CARD_H = Math.round(CardRenderer.CARD_H * CARD_SCALE);
    private static final int CARD_GAP = 4;
    /** Height of the stat/value caption drawn under each card. */
    private static final int CAPTION_H = 11;

    private final Map<String, LivingEntity> entityCache = new HashMap<>();
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

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.fillGradient(0, 0, width, height, FELT_1, FELT_0);
        g.fill(0, 0, width, 2, GOLD_DIM);
        g.fill(0, height - 2, width, height, GOLD_DIM);

        int phase = ClientBlackjack.phase();
        boolean live = phase == BlackjackSyncPayload.PHASE_PLAYER;
        long since = System.currentTimeMillis() - ClientBlackjack.changedAt();

        var pose = g.pose();
        pose.pushPose();
        pose.translate(width / 2f, 10f, 0);
        pose.scale(1.7f, 1.7f, 1f);
        String head = "TWENTY-ONE";
        g.drawString(font, head, -font.width(head) / 2, 0, GOLD, true);
        pose.popPose();
        g.drawCenteredString(font, "call a stat, then the card turns", width / 2, 32, DIM);

        // --- the two hands, side by side --------------------------------
        int half = Math.min(300, (width - 40) / 2);
        int leftX = width / 2 - half - 6;
        int rightX = width / 2 + 6;
        int handY = 48;

        boolean showDealer = phase != BlackjackSyncPayload.PHASE_PLAYER
                && !ClientBlackjack.dealerDraws().isEmpty();
        drawTotal(g, leftX, handY, half, "YOU", ClientBlackjack.playerTotal(),
                ClientBlackjack.playerTotal() > Blackjack.TARGET);
        drawTotal(g, rightX, handY, half, "DEALER",
                showDealer ? ClientBlackjack.dealerTotal() : -1,
                showDealer && ClientBlackjack.dealerTotal() > Blackjack.TARGET);

        int cardsTop = handY + 30;
        int cardsBottom = drawHand(g, leftX, cardsTop, half,
                ClientBlackjack.playerDraws(), mouseX, mouseY, since, true);
        if (showDealer) {
            cardsBottom = Math.max(cardsBottom, drawHand(g, rightX, cardsTop, half,
                    ClientBlackjack.dealerDraws(), mouseX, mouseY, since, false));
        } else if (live) {
            g.drawString(font, "waiting…", rightX + 4, cardsTop + 4, EDGE, false);
        }

        // --- the six calls ------------------------------------------------
        int rowH = 15;
        int listY = Math.max(cardsBottom + 20, height - 60 - Stat.values().length * rowH);
        int listX0 = width / 2 - 110;
        int listX1 = width / 2 + 110;
        g.drawString(font, live ? "CALL A STAT" : "PRESS DEAL TO PLAY A HAND",
                listX0, listY - 12, GOLD, false);
        hovered = -1;
        for (Stat stat : Stat.values()) {
            int i = stat.ordinal();
            int y = listY + i * rowH;
            boolean over = mouseX >= listX0 && mouseX < listX1 && mouseY >= y && mouseY < y + 14;
            if (over && live) {
                hovered = i;
            }
            statRects[i] = new int[]{listX0, y, listX1 - listX0, 14};
            g.fill(listX0, y, listX1, y + 14,
                    !live ? 0x18000000 : over ? 0x33E9C46A : 0x22000000);
            if (live && over) {
                g.renderOutline(listX0, y, listX1 - listX0, 14, GOLD_DIM);
            }
            String label = stat.label.toUpperCase(Locale.ROOT);
            g.drawString(font, label, listX0 + 8, y + 3, live ? INK : 0xFF4E6A5C, false);
        }

        // --- buttons --------------------------------------------------------
        int by = listY + Stat.values().length * rowH + 8;
        dealRect = new int[]{listX0, by, 130, 16};
        standRect = new int[]{listX1 - 78, by, 78, 16};
        if (live) {
            button(g, standRect, "STAND", mouseX, mouseY, true);
        } else {
            button(g, dealRect, "DEAL  (" + ClientBlackjack.stake() + " fragments)",
                    mouseX, mouseY, ClientBlackjack.fragments() >= ClientBlackjack.stake());
        }

        // --- result, in its own band so it cannot land on anything ----------
        if (phase == BlackjackSyncPayload.PHASE_DONE
                && ClientBlackjack.result() != BlackjackSyncPayload.RESULT_NONE) {
            String text = switch (ClientBlackjack.result()) {
                case BlackjackSyncPayload.RESULT_PLAYER -> "YOU WIN";
                case BlackjackSyncPayload.RESULT_DEALER ->
                        ClientBlackjack.playerTotal() > Blackjack.TARGET ? "BUST" : "DEALER WINS";
                default -> "PUSH";
            };
            int colour = switch (ClientBlackjack.result()) {
                case BlackjackSyncPayload.RESULT_PLAYER -> WIN;
                case BlackjackSyncPayload.RESULT_DEALER -> LOSE;
                default -> GOLD;
            };
            float grow = Mth.clamp(since / 220f, 0f, 1f);
            int bannerY = Math.max(cardsBottom + 4, listY - 30);
            pose.pushPose();
            pose.translate(width / 2f, bannerY, 0);
            pose.scale(Math.max(0.05f, 1.8f * grow), Math.max(0.05f, 1.8f * grow), 1f);
            g.drawString(font, text, -font.width(text) / 2, 0, colour, true);
            pose.popPose();
        }

        g.drawCenteredString(font, ClientBlackjack.fragments() + " fragments  ·  ESC to leave",
                width / 2, height - 14, DIM);
    }

    /**
     * One side's dealt cards, drawn as cards. Wraps into rows rather than
     * overlapping, so every card in the hand stays fully readable — with stats
     * repeatable a hand can now run long, and a fanned pile would hide the
     * middle of it.
     *
     * @return the y just below the last row
     */
    private int drawHand(GuiGraphics g, int x, int y, int available,
                         List<ClientBlackjack.Draw> draws, int mouseX, int mouseY,
                         long since, boolean mine) {
        int perRow = Math.max(2, (available + CARD_GAP) / (CARD_W + CARD_GAP));
        int row = 0;
        int col = 0;
        MobCard hoverCard = null;
        int hoverX = 0;
        int hoverY = 0;
        for (int i = 0; i < draws.size(); i++) {
            ClientBlackjack.Draw draw = draws.get(i);
            MobCard card = MobCards.byId(draw.mobId());
            int cx = x + col * (CARD_W + CARD_GAP);
            int cy = y + row * (CARD_H + CAPTION_H + 4);

            if (card != null) {
                // the newest card fades in as it is turned over
                boolean newest = mine && i == draws.size() - 1;
                float t = newest ? Mth.clamp(since / 220f, 0f, 1f) : 1f;
                if (t < 1f) {
                    g.pose().pushPose();
                    g.pose().translate(cx + CARD_W / 2f, cy + CARD_H / 2f, 0);
                    g.pose().scale(Math.max(0.05f, t), 1f, 1f);
                    g.pose().translate(-CARD_W / 2f, -CARD_H / 2f, 0);
                    LivingEntity mob = CardRenderer.portraitEntity(minecraft, card, entityCache);
                    CardRenderer.renderCard(g, font, card, 0, 0, CARD_SCALE, -1, -1, mob, false, false);
                    g.pose().popPose();
                } else {
                    LivingEntity mob = CardRenderer.portraitEntity(minecraft, card, entityCache);
                    CardRenderer.renderCard(g, font, card, cx, cy, CARD_SCALE, -1, -1, mob, false, false);
                }
                if (mouseX >= cx && mouseX < cx + CARD_W && mouseY >= cy && mouseY < cy + CARD_H) {
                    hoverCard = card;
                    hoverX = cx;
                    hoverY = cy;
                }
            }

            // which stat was called, and what it was worth
            String cap = draw.stat() >= 0 && draw.stat() < Stat.values().length
                    ? Stat.values()[draw.stat()].shortLabel : "?";
            String val = "+" + draw.value();
            g.drawString(font, cap, cx, cy + CARD_H + 2, DIM, false);
            g.drawString(font, val, cx + CARD_W - font.width(val), cy + CARD_H + 2,
                    draw.value() == 0 ? DIM : INK, false);

            if (++col >= perRow) {
                col = 0;
                row++;
            }
        }
        // hovering a dealt card names it, since the art is small at this size
        if (hoverCard != null) {
            String name = hoverCard.displayName();
            int w = font.width(name) + 8;
            int tx = Mth.clamp(hoverX + CARD_W / 2 - w / 2, 2, width - w - 2);
            int ty = Math.max(2, hoverY - 12);
            g.fill(tx, ty, tx + w, ty + 11, 0xE0000000);
            g.renderOutline(tx, ty, w, 11, GOLD_DIM);
            g.drawString(font, name, tx + 4, ty + 2, INK, false);
        }
        int rows = draws.isEmpty() ? 0 : row + (col > 0 ? 1 : 0);
        return y + Math.max(1, rows) * (CARD_H + CAPTION_H + 4);
    }

    private void drawTotal(GuiGraphics g, int x, int y, int w, String who, int total,
                           boolean bust) {
        g.fill(x, y, x + w, y + 26, 0x33000000);
        g.renderOutline(x, y, w, 26, EDGE);
        g.drawString(font, who, x + 6, y + 9, DIM, false);
        String value = total < 0 ? "??" : String.valueOf(total);
        var pose = g.pose();
        pose.pushPose();
        pose.translate(x + w - 8f, y + 5f, 0);
        pose.scale(1.7f, 1.7f, 1f);
        g.drawString(font, value, -font.width(value), 0, bust ? LOSE : INK, true);
        pose.popPose();
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
