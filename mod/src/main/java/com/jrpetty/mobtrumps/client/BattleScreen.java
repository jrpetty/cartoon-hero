package com.jrpetty.mobtrumps.client;

import com.jrpetty.mobtrumps.BattleActionPayload;
import com.jrpetty.mobtrumps.BattleSyncPayload;
import com.jrpetty.mobtrumps.game.Difficulty;
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
import java.util.Map;

/**
 * The on-screen Mob Trumps battle against the CPU, started from a dueling table.
 * Two cards face off in the same look as the collection book; you pick a stat by
 * clicking a row on your card, then reveal the CPU's card and see who takes the
 * round — all driven by server {@link BattleSyncPayload} snapshots.
 */
public class BattleScreen extends Screen {

    private static final float CARD_SCALE = 0.68f;

    private final Map<String, LivingEntity> entityCache = new HashMap<>();

    // rects captured during render for hit-testing on click
    private int[] actionRect;   // {x, y, w, h}
    private int[] leaveRect;
    private final int[][] statRects = new int[Stat.values().length][];

    public BattleScreen() {
        super(Component.literal("Mob Trumps Battle"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        // leaving the screen forfeits the running battle (server cleans up)
        PacketDistributor.sendToServer(new BattleActionPayload(BattleActionPayload.FORFEIT, 0));
        super.onClose();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        g.fillGradient(0, 0, width, height, 0xE60D1017, 0xF2050709);

        int phase = ClientBattle.phase();
        long elapsed = System.currentTimeMillis() - ClientBattle.changedAt();

        // header
        String title = "MOB TRUMPS";
        var pose = g.pose();
        pose.pushPose();
        pose.translate(width / 2f, 14f, 0);
        pose.scale(1.6f, 1.6f, 1f);
        g.drawString(font, title, -font.width(title) / 2, 0, 0xFFF6E27A, true);
        pose.popPose();
        String diff = Difficulty.values()[Mth.clamp(ClientBattle.difficulty(), 0, 2)].label();
        String sub = "vs " + diff + " CPU   ·   Round " + Math.max(1, ClientBattle.round());
        g.drawCenteredString(font, sub, width / 2, 30, 0xFF9AA6B2);

        int cardW = Math.round(CardRenderer.CARD_W * CARD_SCALE);
        int cardH = Math.round(CardRenderer.CARD_H * CARD_SCALE);
        int centerY = height / 2 - 4;
        int cardY = centerY - cardH / 2;
        int playerX = width / 2 - cardW - 48;
        int cpuX = width / 2 + 48;

        boolean reveal = phase == BattleSyncPayload.RESULT || phase == BattleSyncPayload.FINISHED;
        int winner = ClientBattle.winner();
        int chosen = ClientBattle.chosenStat();

        // labels above each card
        g.drawCenteredString(font, "YOU", playerX + cardW / 2, cardY - 12, 0xFF7BE38A);
        g.drawCenteredString(font, "CPU", cpuX + cardW / 2, cardY - 12, 0xFFF0857D);

        // player card (always face up)
        MobCard playerCard = MobCards.byId(ClientBattle.playerCardId());
        if (playerCard != null) {
            drawCardGlow(g, playerX, cardY, cardW, cardH,
                    reveal && winner == 0 ? 0xFFFFD54A : (phase == BattleSyncPayload.PLAYER_PICK ? 0xFF55E06A : 0));
            LivingEntity mob = CardRenderer.portraitEntity(minecraft, playerCard, entityCache);
            CardRenderer.renderCard(g, font, playerCard, playerX, cardY, CARD_SCALE,
                    mouseX, mouseY, mob, false, true);
        }

        // cpu card (face down until reveal)
        MobCard cpuCard = MobCards.byId(ClientBattle.cpuCardId());
        if (reveal && cpuCard != null) {
            drawCardGlow(g, cpuX, cardY, cardW, cardH, winner == 1 ? 0xFFFFD54A : 0);
            LivingEntity mob = CardRenderer.portraitEntity(minecraft, cpuCard, entityCache);
            CardRenderer.renderCard(g, font, cpuCard, cpuX, cardY, CARD_SCALE,
                    mouseX, mouseY, mob, false, false);
        } else {
            CardRenderer.renderBack(g, font, cpuX, cardY, CARD_SCALE);
        }

        // stat rows on the player card — clickable when it's your pick
        layoutStatRows(playerX, cardY, cardW);
        boolean myPick = phase == BattleSyncPayload.PLAYER_PICK && playerCard != null;
        for (int i = 0; i < statRects.length; i++) {
            int[] r = statRects[i];
            boolean hover = myPick && inRect(mouseX, mouseY, r);
            if (myPick && hover) {
                g.fill(r[0], r[1], r[0] + r[2], r[1] + r[3], 0x553BE0A0);
                g.renderOutline(r[0], r[1], r[2], r[3], 0xFF55E06A);
            }
            // highlight the chosen stat row on both cards after a reveal
            if (reveal && chosen == i) {
                g.renderOutline(r[0], r[1], r[2], r[3], 0xFFFFD54A);
                int[] cr = statRow(cpuX, cardY, cardW, i);
                if (reveal && cpuCard != null) g.renderOutline(cr[0], cr[1], cr[2], cr[3], 0xFFFFD54A);
            }
        }

        // centre VS + tallies
        drawCenterInfo(g, centerY);

        // result / finished banner
        if (reveal) {
            drawBanner(g, phase, winner, chosen, playerCard, cpuCard, centerY - cardH / 2 - 26, elapsed);
        }

        // contextual buttons
        drawButtons(g, phase, mouseX, mouseY);

        if (myPick) {
            g.drawCenteredString(font, "Click a stat on your card to play it",
                    width / 2, cardY + cardH + 8, 0xFFB9BFC9);
        }
    }

    private void drawCenterInfo(GuiGraphics g, int centerY) {
        int cx = width / 2;
        var pose = g.pose();
        pose.pushPose();
        pose.translate(cx, centerY - 12f, 0);
        pose.scale(2.0f, 2.0f, 1f);
        g.drawString(font, "VS", -font.width("VS") / 2, 0, 0xFFEFE3B0, true);
        pose.popPose();
        String tally = "You " + ClientBattle.playerCount() + "   —   " + ClientBattle.cpuCount() + " CPU";
        g.drawCenteredString(font, tally, cx, centerY + 12, 0xFFCED6E0);
        if (ClientBattle.potCount() > 0) {
            g.drawCenteredString(font, "Pot: " + ClientBattle.potCount(), cx, centerY + 24, 0xFFE7C24A);
        }
    }

    private void drawBanner(GuiGraphics g, int phase, int winner, int chosen,
                            MobCard playerCard, MobCard cpuCard, int y, long elapsed) {
        String text;
        int color;
        if (phase == BattleSyncPayload.FINISHED) {
            text = switch (winner) {
                case 0 -> "VICTORY!";
                case 1 -> "DEFEAT";
                default -> "DRAW";
            };
            color = winner == 0 ? 0xFFFFD54A : winner == 1 ? 0xFFF0625A : 0xFFE7C24A;
        } else {
            text = switch (winner) {
                case 0 -> "YOU WIN THE ROUND!";
                case 1 -> "CPU WINS THE ROUND";
                default -> "TIE — INTO THE POT";
            };
            color = winner == 0 ? 0xFF6BE87A : winner == 1 ? 0xFFF0857D : 0xFFE7C24A;
        }
        float t = Mth.clamp(elapsed / 200f, 0f, 1f);
        float scale = (phase == BattleSyncPayload.FINISHED ? 2.4f : 1.5f) * (0.7f + 0.3f * t);
        var pose = g.pose();
        pose.pushPose();
        pose.translate(width / 2f, y, 0);
        pose.scale(scale, scale, 1f);
        g.drawString(font, text, -font.width(text) / 2, 0, color, true);
        pose.popPose();

        // the head-to-head values on the chosen stat
        if (chosen >= 0 && playerCard != null && cpuCard != null) {
            Stat s = Stat.values()[chosen];
            String line = s.label + ":  you " + playerCard.stat(s) + "  vs  CPU " + cpuCard.stat(s);
            g.drawCenteredString(font, line, width / 2, y + 16, 0xFFB9BFC9);
        }
    }

    private void drawButtons(GuiGraphics g, int phase, int mouseX, int mouseY) {
        actionRect = null;
        // primary action, bottom-centre
        String label = switch (phase) {
            case BattleSyncPayload.CPU_PICK -> "Reveal CPU's pick";
            case BattleSyncPayload.RESULT -> "Next ▶";
            case BattleSyncPayload.FINISHED -> "Play Again";
            default -> null;
        };
        if (label != null) {
            int w = Math.max(120, font.width(label) + 28);
            int x = width / 2 - w / 2;
            int y = height - 40;
            actionRect = new int[]{x, y, w, 20};
            button(g, actionRect, label, 0xFF2E7D46, 0xFF3BA85E, mouseX, mouseY);
        }
        // leave, bottom-right (or beside Play Again when finished)
        String leaveLabel = "Leave";
        int lw = font.width(leaveLabel) + 20;
        int lx = phase == BattleSyncPayload.FINISHED ? width / 2 + 70 : width - lw - 14;
        int ly = height - 40;
        leaveRect = new int[]{lx, ly, lw, 20};
        button(g, leaveRect, leaveLabel, 0xFF5A2530, 0xFF7A3140, mouseX, mouseY);
    }

    private void button(GuiGraphics g, int[] r, String label, int base, int hover, int mouseX, int mouseY) {
        boolean h = inRect(mouseX, mouseY, r);
        g.fill(r[0], r[1], r[0] + r[2], r[1] + r[3], h ? hover : base);
        g.renderOutline(r[0], r[1], r[2], r[3], 0x66FFFFFF);
        g.drawString(font, label, r[0] + (r[2] - font.width(label)) / 2, r[1] + 6, 0xFFFFFFFF, false);
    }

    private void drawCardGlow(GuiGraphics g, int x, int y, int w, int h, int color) {
        if (color == 0) return;
        float pulse = 0.6f + 0.4f * (float) Math.sin(System.currentTimeMillis() / 400.0);
        for (int i = 4; i >= 1; i--) {
            int s = i * 3;
            int a = (int) (0x30 * pulse * (1f - (i - 1) / 4f)) << 24;
            g.fill(x - s, y - s, x + w + s, y + h + s, (color & 0x00FFFFFF) | a);
        }
    }

    private void layoutStatRows(int cardX, int cardY, int cardW) {
        for (int i = 0; i < statRects.length; i++) {
            statRects[i] = statRow(cardX, cardY, cardW, i);
        }
    }

    /** Screen rect of stat row {@code i} on a card at (cardX,cardY), matching CardRenderer's layout. */
    private int[] statRow(int cardX, int cardY, int cardW, int i) {
        int x0 = cardX + Math.round(12 * CARD_SCALE);
        int x1 = cardX + Math.round((CardRenderer.CARD_W - 12) * CARD_SCALE);
        int yTop = cardY + Math.round((121 + i * 13) * CARD_SCALE);
        int hgt = Math.round(13 * CARD_SCALE);
        return new int[]{x0, yTop, x1 - x0, hgt};
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        int phase = ClientBattle.phase();
        if (leaveRect != null && inRect((int) mouseX, (int) mouseY, leaveRect)) {
            click();
            onClose();
            return true;
        }
        if (actionRect != null && inRect((int) mouseX, (int) mouseY, actionRect)) {
            click();
            switch (phase) {
                case BattleSyncPayload.CPU_PICK, BattleSyncPayload.RESULT ->
                        send(BattleActionPayload.NEXT, 0);
                case BattleSyncPayload.FINISHED -> send(BattleActionPayload.PLAY_AGAIN, 0);
                default -> {
                }
            }
            return true;
        }
        if (phase == BattleSyncPayload.PLAYER_PICK) {
            for (int i = 0; i < statRects.length; i++) {
                if (inRect((int) mouseX, (int) mouseY, statRects[i])) {
                    click();
                    send(BattleActionPayload.PICK, i);
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void send(int action, int stat) {
        PacketDistributor.sendToServer(new BattleActionPayload(action, stat));
    }

    private void click() {
        if (minecraft != null) {
            minecraft.getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f));
        }
    }

    private static boolean inRect(int mx, int my, int[] r) {
        return r != null && mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3];
    }
}
