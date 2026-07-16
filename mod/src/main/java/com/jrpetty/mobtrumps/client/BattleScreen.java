package com.jrpetty.mobtrumps.client;

import com.jrpetty.mobtrumps.BattleActionPayload;
import com.jrpetty.mobtrumps.BattleSyncPayload;
import com.jrpetty.mobtrumps.game.Difficulty;
import com.jrpetty.mobtrumps.game.MobCard;
import com.jrpetty.mobtrumps.game.MobCards;
import com.jrpetty.mobtrumps.game.Stat;
import com.mojang.math.Axis;
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
 * The on-screen Mob Trumps battle — same felt-and-gold table as the menu it
 * opens from. Drives both a solo game vs the CPU and a live player-vs-player
 * duel through the same board: your card left, the opponent's face-down card
 * right until the play resolves, then it FLIPS to reveal who took the round.
 * Cards auto-size to the window (override with the size button), the reveal is
 * held long enough to read even in a fast duel, and everything animates.
 */
public class BattleScreen extends Screen {

    private static final long FLIP_MS = 440L;
    private static final long DEAL_MS = 320L;
    private static final long LEAVE_CONFIRM_MS = 2500L;

    // felt & trim palette (matches TableMenuScreen)
    private static final int FELT_LIGHT = 0xFF14503C;
    private static final int FELT_DARK = 0xFF072A1F;
    private static final int GOLD = 0xFFE9C46A;
    private static final int GOLD_DIM = 0xFF9A7F45;
    private static final int TEXT_DIM = 0xFFB9C8C0;

    private final Map<String, LivingEntity> entityCache = new HashMap<>();
    private final long openedAt = System.currentTimeMillis();
    private long leaveArmedAt = -1;
    private float cardScale = 0.68f;

    // rects captured during render for hit-testing on click
    private int[] actionRect;
    private int[] leaveRect;
    private int[] sizeRect;
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
        // leaving the screen forfeits the running game (server cleans up)
        PacketDistributor.sendToServer(new BattleActionPayload(BattleActionPayload.FORFEIT, 0));
        super.onClose();
    }

    /** Fit two cards + a centre gap into the window, then apply the size preference. */
    private float computeScale() {
        float byW = (width * 0.94f - 48f) / (2f * CardRenderer.CARD_W);
        float byH = (height - 96f) / CardRenderer.CARD_H;
        float fit = Mth.clamp(Math.min(byW, byH), 0.40f, 1.05f);
        return ClientPrefs.resolveScale(fit);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        ClientBattle.poll(); // promote any held post-reveal state once it has played
        long now = System.currentTimeMillis();
        long elapsed = now - ClientBattle.changedAt();
        float fadeIn = Mth.clamp((now - openedAt) / 260f, 0f, 1f);
        int phase = ClientBattle.phase();
        boolean pvp = ClientBattle.isPvp();
        cardScale = computeScale();

        // --- the same felt table the menu lives on ---
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
        if (fadeIn < 1f) {
            g.fill(0, 0, width, height, ((int) ((1f - fadeIn) * 0xE0) << 24));
        }

        // --- header ---
        var pose = g.pose();
        pose.pushPose();
        pose.translate(width / 2f, 14f, 0);
        pose.scale(1.6f, 1.6f, 1f);
        String title = "MOB TRUMPS";
        g.drawString(font, title, -font.width(title) / 2, 0, GOLD, true);
        pose.popPose();
        String opp = ClientBattle.label().isEmpty() ? "Opponent" : ClientBattle.label();
        String sub = pvp
                ? "vs " + opp + "   ·   Round " + Math.max(1, ClientBattle.round())
                : "vs " + Difficulty.values()[Mth.clamp(ClientBattle.difficulty(), 0, 2)].label()
                        + " CPU   ·   Round " + Math.max(1, ClientBattle.round());
        g.drawCenteredString(font, sub, width / 2, 30, TEXT_DIM);
        if (pvp && (ClientBattle.myGames() > 0 || ClientBattle.oppGames() > 0)) {
            String series = "Series   You " + ClientBattle.myGames()
                    + " - " + ClientBattle.oppGames() + "   " + opp;
            g.drawCenteredString(font, series, width / 2, 40, GOLD);
        }

        int cardW = Math.round(CardRenderer.CARD_W * cardScale);
        int cardH = Math.round(CardRenderer.CARD_H * cardScale);
        int gap = Math.max(48, Math.min(160, cardW));
        int total = 2 * cardW + gap;
        int startX = Math.max(2, (width - total) / 2);
        int playerX = startX;
        int cpuX = startX + cardW + gap;
        int centerY = height / 2 - 4;
        int baseCardY = centerY - cardH / 2;

        boolean reveal = phase == BattleSyncPayload.RESULT || phase == BattleSyncPayload.FINISHED;
        int winner = ClientBattle.winner();
        int chosen = ClientBattle.chosenStat();

        // deal-in: on pick phases the cards ease to the table from off-stage
        boolean dealing = (phase == BattleSyncPayload.PLAYER_PICK
                || phase == BattleSyncPayload.CPU_PICK
                || phase == BattleSyncPayload.OPPONENT_PICK) && elapsed < DEAL_MS;
        float dealT = dealing ? easeOutCubic(elapsed / (float) DEAL_MS) : 1f;
        int playerCardY = baseCardY + Math.round((1f - dealT) * 46f);
        int cpuCardY = baseCardY - Math.round((1f - dealT) * 46f);

        // name tags + card piles beside each side
        g.drawCenteredString(font, "YOU", playerX + cardW / 2, baseCardY - 12, 0xFF7BE38A);
        g.drawCenteredString(font, pvp ? shorten(opp) : "CPU",
                cpuX + cardW / 2, baseCardY - 12, 0xFFF0857D);
        if (startX >= 30) {
            pile(g, playerX - 24, centerY, ClientBattle.playerCount(), 0xFF7BE38A);
            pile(g, cpuX + cardW + 10, centerY, ClientBattle.cpuCount(), 0xFFF0857D);
        }

        // player card (always face up)
        MobCard playerCard = MobCards.byId(ClientBattle.playerCardId());
        boolean myPick = phase == BattleSyncPayload.PLAYER_PICK && playerCard != null;
        final int pcy = playerCardY;
        if (playerCard != null) {
            drawCardGlow(g, playerX, pcy, cardW, cardH,
                    reveal && winner == 0 ? 0xFFFFD54A : (myPick ? 0xFF55E06A : 0));
            boolean pulseMe = winner == 0 && phase == BattleSyncPayload.RESULT && elapsed >= FLIP_MS;
            final int fpx = playerX;
            withPulse(g, pulseMe, elapsed - FLIP_MS, playerX + cardW / 2f, pcy + cardH / 2f, () -> {
                LivingEntity mob = CardRenderer.portraitEntity(minecraft, playerCard, entityCache);
                CardRenderer.renderCard(g, font, playerCard, fpx, pcy, cardScale,
                        mouseX, mouseY, mob, false, true);
            });
            if (reveal && winner == 1 && elapsed >= FLIP_MS) {
                int dim = (int) (Mth.clamp((elapsed - FLIP_MS) / 300f, 0f, 1f) * 0x66) << 24;
                g.fill(playerX - 2, pcy - 2, playerX + cardW + 2, pcy + cardH + 2, dim | 0x000A0806);
            }
        }

        // opponent card: face down until the play resolves, then it FLIPS to reveal
        MobCard cpuCard = MobCards.byId(ClientBattle.cpuCardId());
        boolean flipping = phase == BattleSyncPayload.RESULT && elapsed < FLIP_MS && cpuCard != null;
        boolean faceUp = reveal && cpuCard != null;
        if (flipping) {
            float ft = elapsed / (float) FLIP_MS;
            boolean backHalf = ft < 0.5f;
            float sx = Math.max(0.04f, backHalf ? 1f - ft * 2f : ft * 2f - 1f);
            float cxMid = cpuX + cardW / 2f;
            pose.pushPose();
            pose.translate(cxMid, 0, 0);
            pose.scale(sx, 1f, 1f);
            pose.translate(-cxMid, 0, 0);
            if (backHalf) {
                CardRenderer.renderBack(g, font, cpuX, cpuCardY, cardScale);
            } else {
                CardRenderer.renderCard(g, font, cpuCard, cpuX, cpuCardY, cardScale,
                        mouseX, mouseY, null, false, false);
            }
            pose.popPose();
            float flash = 1f - Math.abs(ft - 0.5f) * 4f;
            if (flash > 0f) {
                g.fill(cpuX - 4, cpuCardY - 4, cpuX + cardW + 4, cpuCardY + cardH + 4,
                        ((int) (flash * 0x80) << 24) | 0x00FFFFFF);
            }
        } else if (faceUp) {
            final int ccy = cpuCardY;
            drawCardGlow(g, cpuX, ccy, cardW, cardH, winner == 1 ? 0xFFFFD54A : 0);
            boolean pulseCpu = winner == 1 && phase == BattleSyncPayload.RESULT && elapsed >= FLIP_MS;
            final int fcx = cpuX;
            withPulse(g, pulseCpu, elapsed - FLIP_MS, cpuX + cardW / 2f, ccy + cardH / 2f, () -> {
                LivingEntity mob = CardRenderer.portraitEntity(minecraft, cpuCard, entityCache);
                CardRenderer.renderCard(g, font, cpuCard, fcx, ccy, cardScale,
                        mouseX, mouseY, mob, false, false);
            });
            if (winner == 0 && phase == BattleSyncPayload.RESULT && elapsed >= FLIP_MS) {
                int dim = (int) (Mth.clamp((elapsed - FLIP_MS) / 300f, 0f, 1f) * 0x66) << 24;
                g.fill(cpuX - 2, ccy - 2, cpuX + cardW + 2, ccy + cardH + 2, dim | 0x000A0806);
            }
        } else {
            CardRenderer.renderBack(g, font, cpuX, cpuCardY, cardScale);
        }

        // stat rows on the player card — clickable when it's your pick
        layoutStatRows(playerX, playerCardY, cardW);
        if (myPick) {
            float roll = (now % 2000L) / 2000f;
            int span = statRects[5][1] + statRects[5][3] - statRects[0][1];
            int shimmerY = statRects[0][1] + (int) (roll * span);
            g.fill(statRects[0][0], shimmerY, statRects[0][0] + statRects[0][2], shimmerY + 2, 0x26FFFFFF);
        }
        for (int i = 0; i < statRects.length; i++) {
            int[] r = statRects[i];
            boolean hover = myPick && inRect(mouseX, mouseY, r);
            if (hover) {
                g.fill(r[0], r[1], r[0] + r[2], r[1] + r[3], 0x553BE0A0);
                g.renderOutline(r[0], r[1], r[2], r[3], 0xFF55E06A);
                g.drawString(font, String.valueOf(i + 1), r[0] - 9, r[1] + 1, 0xFF55E06A, true);
            }
            if (reveal && chosen == i && !flipping) {
                g.renderOutline(r[0], r[1], r[2], r[3], 0xFFFFD54A);
                int[] cr = statRow(cpuX, cpuCardY, cardW, i);
                if (cpuCard != null) g.renderOutline(cr[0], cr[1], cr[2], cr[3], 0xFFFFD54A);
            }
        }

        // centre column: status chip, VS emblem, tallies, pot
        drawStatusChip(g, phase, pvp, opp, baseCardY - 26, now);
        drawCenterInfo(g, centerY, opp, pvp);

        // spoils fly to the winner's pile once the card lands
        if (phase == BattleSyncPayload.RESULT && elapsed >= FLIP_MS) {
            drawFlyingCards(g, winner, elapsed - FLIP_MS,
                    playerX + cardW / 2, cpuX + cardW / 2, centerY);
        }

        // result / finished banner — pops in only after the flip reveals the card
        if (reveal && !flipping) {
            long bannerElapsed = phase == BattleSyncPayload.RESULT
                    ? Math.max(0, elapsed - FLIP_MS) : elapsed;
            drawBanner(g, phase, winner, chosen, playerCard, cpuCard,
                    centerY - cardH / 2 - 26, bannerElapsed, pvp ? shorten(opp) : "CPU");
        }

        // buttons + size control
        drawButtons(g, phase, pvp, opp, mouseX, mouseY, now);
        drawSizeButton(g, mouseX, mouseY);

        String hint;
        if (myPick) {
            hint = "Click a stat on your card (or press 1-6)";
        } else if (phase == BattleSyncPayload.OPPONENT_PICK) {
            hint = "Waiting for " + opp + "...";
        } else {
            hint = pvp ? "ESC to leave" : "SPACE to continue  ·  ESC to leave";
        }
        g.drawCenteredString(font, hint, width / 2, baseCardY + cardH + 8, TEXT_DIM);
    }

    // --- pieces ---

    private void drawStatusChip(GuiGraphics g, int phase, boolean pvp, String opp, int y, long now) {
        String text;
        int color;
        switch (phase) {
            case BattleSyncPayload.PLAYER_PICK -> {
                text = "YOUR PICK";
                color = 0xFF55E06A;
            }
            case BattleSyncPayload.CPU_PICK -> {
                text = "CPU IS THINKING" + ".".repeat((int) ((now / 350) % 4));
                color = 0xFFF0857D;
            }
            case BattleSyncPayload.OPPONENT_PICK -> {
                text = shorten(opp).toUpperCase(java.util.Locale.ROOT) + " IS CHOOSING"
                        + ".".repeat((int) ((now / 350) % 4));
                color = 0xFFF0857D;
            }
            default -> {
                return;
            }
        }
        int w = font.width(text) + 14;
        int x = width / 2 - w / 2;
        g.fill(x, y, x + w, y + 13, 0xC0081E16);
        g.renderOutline(x, y, w, 13, color);
        g.drawString(font, text, x + 7, y + 3, color, false);
    }

    private void pile(GuiGraphics g, int x, int centerY, int count, int color) {
        int stack = Math.min(8, Math.max(count > 0 ? 1 : 0, count / 2));
        int w = 14, h = 19;
        int baseY = centerY + 10;
        for (int i = 0; i < stack; i++) {
            int py = baseY - i * 2;
            g.fill(x - 1, py - 1, x + w + 1, py + h + 1, 0xFF2A1F12);
            g.fill(x, py, x + w, py + h, 0xFF7A5F3E);
            g.renderOutline(x, py, w, h, 0xFF5F4A32);
        }
        g.drawCenteredString(font, String.valueOf(count), x + w / 2, baseY - stack * 2 - 11, color);
    }

    private void drawCenterInfo(GuiGraphics g, int centerY, String opp, boolean pvp) {
        int cx = width / 2;
        float beat = 1f + 0.05f * (float) Math.sin(System.currentTimeMillis() / 500.0);
        var pose = g.pose();
        pose.pushPose();
        pose.translate(cx, centerY - 14f, 0);
        pose.scale(2.0f * beat, 2.0f * beat, 1f);
        g.drawString(font, "VS", -font.width("VS") / 2, 0, 0xFFEFE3B0, true);
        pose.popPose();
        // reliable tally (piles are decorative and may be hidden when narrow)
        String tally = ClientBattle.playerCount() + " — " + ClientBattle.cpuCount();
        g.drawCenteredString(font, tally, cx, centerY + 8, 0xFFCED6E0);
        if (ClientBattle.potCount() > 0) {
            g.drawCenteredString(font, "POT " + ClientBattle.potCount(), cx, centerY + 20, 0xFFE7C24A);
        }
    }

    private void drawFlyingCards(GuiGraphics g, int winner, long sinceFlip,
                                 int playerCx, int cpuCx, int centerY) {
        if (sinceFlip > 700) {
            return;
        }
        int fromX;
        int toX;
        if (winner == 0) {
            fromX = cpuCx;
            toX = playerCx;
        } else if (winner == 1) {
            fromX = playerCx;
            toX = cpuCx;
        } else {
            fromX = playerCx;
            toX = width / 2;
        }
        var pose = g.pose();
        for (int i = 0; i < 3; i++) {
            float p = Mth.clamp((sinceFlip - i * 80) / 450f, 0f, 1f);
            if (p <= 0f || p >= 1f) {
                continue;
            }
            float ease = easeOutCubic(p);
            float x = Mth.lerp(ease, fromX, toX);
            float arc = -22f * (float) Math.sin(p * Math.PI);
            float y = centerY + arc;
            pose.pushPose();
            pose.translate(x, y, 0);
            pose.mulPose(Axis.ZP.rotationDegrees(p * 360f + i * 40f));
            g.fill(-4, -5, 4, 5, 0xFFE9C46A);
            g.fill(-3, -4, 3, 4, 0xFF7A5F3E);
            pose.popPose();
        }
    }

    private void drawBanner(GuiGraphics g, int phase, int winner, int chosen,
                            MobCard playerCard, MobCard cpuCard, int y, long elapsed, String oppName) {
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
                case 1 -> oppName.toUpperCase(java.util.Locale.ROOT) + " WINS THE ROUND";
                default -> "TIE — INTO THE POT";
            };
            color = winner == 0 ? 0xFF6BE87A : winner == 1 ? 0xFFF0857D : 0xFFE7C24A;
        }
        float t = Mth.clamp(elapsed / 200f, 0f, 1f);
        float scale = (phase == BattleSyncPayload.FINISHED ? 2.4f : 1.5f) * easeOutBack(t);
        var pose = g.pose();
        pose.pushPose();
        pose.translate(width / 2f, y, 0);
        pose.scale(Math.max(0.05f, scale), Math.max(0.05f, scale), 1f);
        g.drawString(font, text, -font.width(text) / 2, 0, color, true);
        pose.popPose();
        if (chosen >= 0 && playerCard != null && cpuCard != null) {
            Stat s = Stat.values()[chosen];
            String line = s.label + ":  you " + playerCard.stat(s) + "  vs  " + cpuCard.stat(s);
            g.drawCenteredString(font, line, width / 2, y + 16, TEXT_DIM);
        }
    }

    private void drawButtons(GuiGraphics g, int phase, boolean pvp, String opp,
                             int mouseX, int mouseY, long now) {
        actionRect = null;
        // primary action. In PvP the round is paced by the server, so the only
        // button is Close at the end; the CPU game keeps reveal / next / again.
        String label = pvp
                ? (phase == BattleSyncPayload.FINISHED ? "Close" : null)
                : switch (phase) {
                    case BattleSyncPayload.CPU_PICK -> "Reveal opponent's pick";
                    case BattleSyncPayload.RESULT -> "Next >";
                    case BattleSyncPayload.FINISHED -> "Play Again";
                    default -> null;
                };
        if (label != null) {
            int w = Math.max(120, font.width(label) + 28);
            int x = width / 2 - w / 2;
            int y = height - 40;
            actionRect = new int[]{x, y, w, 20};
            boolean hover = inRect(mouseX, mouseY, actionRect);
            float pulse = 0.5f + 0.5f * (float) Math.sin(now / 350.0);
            int glowA = (int) (0x30 + 0x28 * pulse) << 24;
            g.fill(x - 3, y - 3, x + w + 3, y + 23, glowA | 0x00E9C46A);
            g.fill(x, y, x + w, y + 20, hover ? 0xFF3BA85E : 0xFF2E7D46);
            g.renderOutline(x, y, w, 20, hover ? GOLD : 0x66FFFFFF);
            g.drawString(font, label, x + (w - font.width(label)) / 2, y + 6, 0xFFFFFFFF, true);
        }
        // leave: a second click confirms while the game is live
        boolean live = phase != BattleSyncPayload.FINISHED;
        boolean armed = leaveArmedAt > 0 && now - leaveArmedAt < LEAVE_CONFIRM_MS;
        String leaveLabel = (armed && live) ? "Forfeit?!" : "Leave";
        int lw = font.width(leaveLabel) + 20;
        int lx = phase == BattleSyncPayload.FINISHED ? width / 2 + 70 : width - lw - 14;
        int ly = height - 40;
        leaveRect = new int[]{lx, ly, lw, 20};
        boolean hover = inRect(mouseX, mouseY, leaveRect);
        int base = armed && live ? 0xFF8A2020 : 0xFF5A2530;
        g.fill(lx, ly, lx + lw, ly + 20, hover ? 0xFF7A3140 : base);
        g.renderOutline(lx, ly, lw, 20, armed && live ? 0xFFF0625A : 0x66FFFFFF);
        g.drawString(font, leaveLabel, lx + 10, ly + 6, 0xFFFFFFFF, armed && live);
    }

    private void drawSizeButton(GuiGraphics g, int mouseX, int mouseY) {
        String label = "Cards: " + ClientPrefs.cardSize().label;
        int w = font.width(label) + 14;
        int x = 14;
        int y = height - 40;
        sizeRect = new int[]{x, y, w, 20};
        boolean hover = inRect(mouseX, mouseY, sizeRect);
        g.fill(x, y, x + w, y + 20, hover ? 0xFF20463A : 0xFF14352A);
        g.renderOutline(x, y, w, 20, hover ? GOLD : 0x66FFFFFF);
        g.drawString(font, label, x + 7, y + 6, TEXT_DIM, false);
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

    private void withPulse(GuiGraphics g, boolean active, long sincePulse,
                           float cx, float cy, Runnable draw) {
        if (!active || sincePulse > 500) {
            draw.run();
            return;
        }
        float p = Mth.clamp(sincePulse / 500f, 0f, 1f);
        float s = 1f + 0.05f * (float) Math.sin(p * Math.PI);
        var pose = g.pose();
        pose.pushPose();
        pose.translate(cx, cy, 0);
        pose.scale(s, s, 1f);
        pose.translate(-cx, -cy, 0);
        draw.run();
        pose.popPose();
    }

    private void layoutStatRows(int cardX, int cardY, int cardW) {
        for (int i = 0; i < statRects.length; i++) {
            statRects[i] = statRow(cardX, cardY, cardW, i);
        }
    }

    private int[] statRow(int cardX, int cardY, int cardW, int i) {
        int x0 = cardX + Math.round(12 * cardScale);
        int x1 = cardX + Math.round((CardRenderer.CARD_W - 12) * cardScale);
        int yTop = cardY + Math.round((121 + i * 13) * cardScale);
        int hgt = Math.round(13 * cardScale);
        return new int[]{x0, yTop, x1 - x0, hgt};
    }

    private String shorten(String s) {
        return s.length() > 12 ? s.substring(0, 12) : s;
    }

    // --- interaction ---

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        int phase = ClientBattle.phase();
        if (sizeRect != null && inRect((int) mouseX, (int) mouseY, sizeRect)) {
            ClientPrefs.cycleCardSize();
            click();
            return true;
        }
        if (leaveRect != null && inRect((int) mouseX, (int) mouseY, leaveRect)) {
            long now = System.currentTimeMillis();
            boolean live = phase != BattleSyncPayload.FINISHED;
            if (live && (leaveArmedAt < 0 || now - leaveArmedAt >= LEAVE_CONFIRM_MS)) {
                leaveArmedAt = now;
                click();
                return true;
            }
            click();
            onClose();
            return true;
        }
        if (actionRect != null && inRect((int) mouseX, (int) mouseY, actionRect)) {
            click();
            primaryAction(phase);
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

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        int phase = ClientBattle.phase();
        if (keyCode == 32 || keyCode == 257) { // SPACE / ENTER
            primaryAction(phase);
            return true;
        }
        if (phase == BattleSyncPayload.PLAYER_PICK && keyCode >= 49 && keyCode <= 54) {
            click();
            send(BattleActionPayload.PICK, keyCode - 49);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void primaryAction(int phase) {
        if (ClientBattle.isPvp()) {
            if (phase == BattleSyncPayload.FINISHED) {
                onClose();
            }
            return; // PvP rounds are paced by the server
        }
        switch (phase) {
            case BattleSyncPayload.CPU_PICK, BattleSyncPayload.RESULT ->
                    send(BattleActionPayload.NEXT, 0);
            case BattleSyncPayload.FINISHED -> send(BattleActionPayload.PLAY_AGAIN, 0);
            default -> {
            }
        }
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
}
