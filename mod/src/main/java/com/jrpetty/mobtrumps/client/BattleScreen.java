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
import java.util.Locale;
import java.util.Map;

/**
 * The on-screen Mob Trumps battle, laid out like a real card table:
 *
 * <pre>
 *  ┌──────────────── header band: title · ROUND chip · opponent ───────────────┐
 *  │  ┌───────────────── gold-piped felt playing surface ─────────────────┐    │
 *  │  │   [nameplate YOU xN]                       [nameplate THEM xN]    │    │
 *  │  │   ┌────────┐        status chip / banner       ┌────────┐         │    │
 *  │  │   │  your  │            VS · tally             │ face-  │         │    │
 *  │  │   │  card  │              pot pile             │ down   │         │    │
 *  │  │   └────────┘                                   └────────┘         │    │
 *  │  └────────────────────────────────────────────────────────────────────┘   │
 *  └──────────── bottom dock: size · primary action · leave ────────────────┘
 * </pre>
 *
 * Drives both the CPU game and live PvP duels: the opponent's card stays
 * face-down until the play resolves, then FLIPS to reveal the round. Cards
 * auto-size to the window (override via the dock's size button).
 */
public class BattleScreen extends Screen {

    private static final long FLIP_MS = 440L;
    private static final long DEAL_MS = 320L;
    private static final long LEAVE_CONFIRM_MS = 2500L;

    // felt & trim palette (matches TableMenuScreen)
    private static final int FELT_LIGHT = 0xFF14503C;
    private static final int FELT_DARK = 0xFF072A1F;
    private static final int SURFACE_LIGHT = 0xFF1A5C46;
    private static final int SURFACE_DARK = 0xFF0E3B2C;
    private static final int BAND = 0x99051A12;
    private static final int GOLD = 0xFFE9C46A;
    private static final int GOLD_DIM = 0xFF9A7F45;
    private static final int TEXT_DIM = 0xFFB9C8C0;
    private static final int EDGE = 0xFF2E5C48;
    private static final int YOU_ACCENT = 0xFF55E06A;
    private static final int OPP_ACCENT = 0xFFF0857D;

    private static final int HEADER_H = 26;
    private static final int DOCK_H = 30;

    private final Map<String, LivingEntity> entityCache = new HashMap<>();
    private final long openedAt = System.currentTimeMillis();
    private long leaveArmedAt = -1;
    private float cardScale = 0.68f;

    private static final long EMOTE_MS = 2800L;
    private static final String[] EMOTES = {"GG", "GL", "Nice", "Close", "Oops", "Wow"};

    // rects captured during render for hit-testing on click
    private int[] actionRect;
    private int[] leaveRect;
    private int[] sizeRect;
    private int[] emoteRect;
    private final int[][] emoteBtnRects = new int[EMOTES.length][];
    private boolean emoteOpen;
    private final int[][] statRects = new int[Stat.values().length][];
    // card centres captured each render, so emote bubbles land on the right card
    private int playerCardMidX;
    private int cpuCardMidX;
    private int cardsTopY;

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

    /** Fit two cards + a centre gap between the header and dock, then apply the size pref. */
    private float computeScale() {
        int dockY = height - DOCK_H;
        float byW = (width * 0.94f - 48f) / (2f * CardRenderer.CARD_W);
        float byH = (dockY - HEADER_H - 84f) / CardRenderer.CARD_H;
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
        int dockY = height - DOCK_H;

        // --- base felt + grain + vignette ---
        g.fillGradient(0, 0, width, height, FELT_LIGHT, FELT_DARK);
        for (int i = 0; i < 90; i++) {
            int sx = (i * 97 + 31) % Math.max(1, width);
            int sy = (i * 61 + 17) % Math.max(1, height);
            g.fill(sx, sy, sx + 1, sy + 1, 0x0DFFFFFF);
        }
        g.fillGradient(0, height * 2 / 3, width, height, 0x00000000, 0x66000000);

        // --- geometry ---
        int cardW = Math.round(CardRenderer.CARD_W * cardScale);
        int cardH = Math.round(CardRenderer.CARD_H * cardScale);
        int gap = Math.max(48, Math.min(160, cardW));
        int total = 2 * cardW + gap;
        int startX = Math.max(2, (width - total) / 2);
        int playerX = startX;
        int cpuX = startX + cardW + gap;
        int centerY = (HEADER_H + dockY) / 2 + 2;
        int baseCardY = centerY - cardH / 2;
        boolean showPiles = startX >= 42;
        playerCardMidX = playerX + cardW / 2;
        cpuCardMidX = cpuX + cardW / 2;
        cardsTopY = baseCardY;

        String opp = ClientBattle.label().isEmpty() ? "Opponent" : ClientBattle.label();
        boolean reveal = phase == BattleSyncPayload.RESULT || phase == BattleSyncPayload.FINISHED;
        int winner = ClientBattle.winner();
        int chosen = ClientBattle.chosenStat();

        // --- the playing surface: a gold-piped felt panel the whole game sits on ---
        int surfX0 = Math.max(4, startX - (showPiles ? 40 : 14));
        int surfX1 = Math.min(width - 4, cpuX + cardW + (showPiles ? 40 : 14));
        int surfY0 = HEADER_H + 6;
        int surfY1 = dockY - 6;
        g.fill(surfX0 + 3, surfY0 + 4, surfX1 + 3, surfY1 + 4, 0x44000000); // drop shadow
        g.fillGradient(surfX0, surfY0, surfX1, surfY1, SURFACE_LIGHT, SURFACE_DARK);
        g.renderOutline(surfX0, surfY0, surfX1 - surfX0, surfY1 - surfY0, GOLD_DIM);
        g.renderOutline(surfX0 + 2, surfY0 + 2, surfX1 - surfX0 - 4, surfY1 - surfY0 - 4, 0x55E9C46A);
        cornerTicks(g, surfX0, surfY0, surfX1, surfY1);

        // deal-in: on pick phases the cards ease to the table from off-stage
        boolean dealing = (phase == BattleSyncPayload.PLAYER_PICK
                || phase == BattleSyncPayload.CPU_PICK
                || phase == BattleSyncPayload.OPPONENT_PICK) && elapsed < DEAL_MS;
        float dealT = dealing ? easeOutCubic(elapsed / (float) DEAL_MS) : 1f;
        int playerCardY = baseCardY + Math.round((1f - dealT) * 46f);
        int cpuCardY = baseCardY - Math.round((1f - dealT) * 46f);

        // --- nameplates riding on top of each card ---
        nameplate(g, playerX, baseCardY - 17, cardW, "YOU", ClientBattle.playerCount(), YOU_ACCENT,
                phase == BattleSyncPayload.PLAYER_PICK);
        nameplate(g, cpuX, baseCardY - 17, cardW, pvp ? shorten(opp) : "CPU", ClientBattle.cpuCount(),
                OPP_ACCENT, phase == BattleSyncPayload.CPU_PICK || phase == BattleSyncPayload.OPPONENT_PICK);
        if (showPiles) {
            pile(g, playerX - 28, centerY, ClientBattle.playerCount());
            pile(g, cpuX + cardW + 14, centerY, ClientBattle.cpuCount());
        }

        // --- player card (always face up) ---
        MobCard playerCard = MobCards.byId(ClientBattle.playerCardId());
        boolean myPick = phase == BattleSyncPayload.PLAYER_PICK && playerCard != null;
        final int pcy = playerCardY;
        if (playerCard != null) {
            drawCardGlow(g, playerX, pcy, cardW, cardH,
                    reveal && winner == 0 ? 0xFFFFD54A : (myPick ? YOU_ACCENT : 0));
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

        // --- opponent card: face down until the play resolves, then FLIPS ---
        MobCard cpuCard = MobCards.byId(ClientBattle.cpuCardId());
        boolean flipping = phase == BattleSyncPayload.RESULT && elapsed < FLIP_MS && cpuCard != null;
        boolean faceUp = reveal && cpuCard != null;
        var pose = g.pose();
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
            // while the other side thinks, their face-down card wears their glow
            boolean theirTurn = phase == BattleSyncPayload.CPU_PICK
                    || phase == BattleSyncPayload.OPPONENT_PICK;
            if (theirTurn) {
                drawCardGlow(g, cpuX, cpuCardY, cardW, cardH, OPP_ACCENT);
            }
            CardRenderer.renderBack(g, font, cpuX, cpuCardY, cardScale);
        }

        // --- stat rows on the player card, with always-on hotkey chips ---
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
            if (myPick) {
                // hotkey chip riding the card's left edge
                int chipX = r[0] - 11;
                g.fill(chipX, r[1] + 1, chipX + 8, r[1] + r[3] - 2, hover ? 0xFF2E7D46 : 0xC0081E16);
                g.drawString(font, String.valueOf(i + 1), chipX + 2, r[1] + 1,
                        hover ? 0xFFFFFFFF : TEXT_DIM, false);
            }
            if (hover) {
                g.fill(r[0], r[1], r[0] + r[2], r[1] + r[3], 0x553BE0A0);
                g.renderOutline(r[0], r[1], r[2], r[3], YOU_ACCENT);
            }
            if (reveal && chosen == i && !flipping) {
                g.renderOutline(r[0], r[1], r[2], r[3], 0xFFFFD54A);
                int[] cr = statRow(cpuX, cpuCardY, cardW, i);
                if (cpuCard != null) g.renderOutline(cr[0], cr[1], cr[2], cr[3], 0xFFFFD54A);
            }
        }

        // --- centre column: status chip, VS medallion, tally, pot ---
        drawStatusChip(g, phase, opp, baseCardY - 26, now);
        drawCenterInfo(g, centerY);

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

        // --- PvP turn-timer bar, just under the header ---
        boolean picking = phase == BattleSyncPayload.PLAYER_PICK
                || phase == BattleSyncPayload.OPPONENT_PICK;
        if (pvp && picking && ClientBattle.turnSeconds() > 0) {
            drawTurnTimer(g, elapsed, ClientBattle.turnSeconds() * 1000L, myPick, surfX0, surfX1, HEADER_H + 2);
        }

        // --- emote bubble floating above whoever spoke ---
        drawEmote(g, now);

        // --- header band & bottom dock frame everything ---
        drawHeader(g, pvp, opp);
        drawDock(g, phase, pvp, mouseX, mouseY, now, dockY);

        // the hint lives in the dock's centre slot, which is free exactly when
        // there is no primary button — so text never sits on a border
        if (actionRect == null) {
            String hint;
            if (myPick) {
                hint = "Click a stat on your card  ·  or press 1-6";
            } else if (phase == BattleSyncPayload.OPPONENT_PICK) {
                hint = "Waiting for " + shorten(opp) + "...";
            } else {
                hint = "";
            }
            if (!hint.isEmpty()) {
                g.drawCenteredString(font, hint, width / 2, dockY + 11, TEXT_DIM);
            }
        }

        if (fadeIn < 1f) {
            g.fill(0, 0, width, height, ((int) ((1f - fadeIn) * 0xE0) << 24));
        }

        // emote wheel sits on top of everything
        if (emoteOpen) {
            drawEmoteWheel(g, mouseX, mouseY);
        }
    }

    // --- PvP extras ---

    /** A depleting turn-timer bar; green on your turn, red on the opponent's. */
    private void drawTurnTimer(GuiGraphics g, long elapsed, long total, boolean mine,
                               int x0, int x1, int y) {
        float frac = Mth.clamp(1f - elapsed / (float) total, 0f, 1f);
        int w = x1 - x0;
        g.fill(x0, y, x1, y + 3, 0x80000000);
        int fillW = (int) (w * frac);
        int col = mine ? YOU_ACCENT : OPP_ACCENT;
        if (frac < 0.25f) {
            // flash when time is short
            col = (System.currentTimeMillis() / 250 % 2 == 0) ? 0xFFF0625A : col;
        }
        g.fill(x0, y, x0 + fillW, y + 3, col);
    }

    /** A speech bubble above the card of whoever emoted, fading out. */
    private void drawEmote(GuiGraphics g, long now) {
        long age = now - ClientBattle.emoteAt();
        int side = ClientBattle.emoteSide();
        String text = ClientBattle.emoteText();
        if (side < 0 || text.isEmpty() || age > EMOTE_MS) {
            return;
        }
        int cx = side == 0 ? playerCardMidX : cpuCardMidX;
        int y = cardsTopY - 22 - (int) (Math.min(age, 300) / 300f * 4);
        int w = font.width(text) + 12;
        int x = cx - w / 2;
        int fade = age > EMOTE_MS - 400 ? (int) ((EMOTE_MS - age) / 400f * 255) : 255;
        int a = (fade << 24);
        g.fill(x, y, x + w, y + 13, (Math.min(0xC0, fade) << 24));
        g.renderOutline(x, y, w, 13, (a & 0xFF000000) | 0x00E9C46A);
        g.drawString(font, text, x + 6, y + 3, (a & 0xFF000000) | 0x00FFF3C8, false);
        // little tail
        g.fill(cx - 1, y + 13, cx + 1, y + 15, (Math.min(0xC0, fade) << 24));
    }

    /** The 2x3 emote picker, opened by the dock's Emote button. */
    private void drawEmoteWheel(GuiGraphics g, int mouseX, int mouseY) {
        g.fill(0, 0, width, height, 0x66000000);
        int cols = 3, rows = 2, bw = 58, bh = 22, pad = 6;
        int pw = cols * bw + (cols + 1) * pad;
        int ph = rows * bh + (rows + 1) * pad + 14;
        int px = width / 2 - pw / 2;
        int py = height - DOCK_H - ph - 6;
        g.fill(px, py, px + pw, py + ph, 0xE0081E16);
        g.renderOutline(px, py, pw, ph, GOLD_DIM);
        g.drawCenteredString(font, "EMOTE", width / 2, py + 4, GOLD);
        for (int i = 0; i < EMOTES.length; i++) {
            int c = i % cols, r = i / cols;
            int bx = px + pad + c * (bw + pad);
            int by = py + 14 + pad + r * (bh + pad);
            emoteBtnRects[i] = new int[]{bx, by, bw, bh};
            boolean hover = inRect(mouseX, mouseY, emoteBtnRects[i]);
            g.fill(bx, by, bx + bw, by + bh, hover ? 0xFF2E7D46 : 0xFF16352A);
            g.renderOutline(bx, by, bw, bh, hover ? GOLD : EDGE);
            g.drawCenteredString(font, EMOTES[i], bx + bw / 2, by + (bh - 8) / 2, 0xFFFFFFFF);
        }
    }

    // --- layout bands ---

    /** Top band: title left, ROUND (and series score) centred, opponent right. */
    private void drawHeader(GuiGraphics g, boolean pvp, String opp) {
        g.fill(0, 0, width, HEADER_H, BAND);
        g.fill(0, HEADER_H, width, HEADER_H + 1, GOLD_DIM);
        if (width >= 330) {
            g.drawString(font, "MOB TRUMPS", 10, 9, GOLD, true);
        }
        String chip = "ROUND " + Math.max(1, ClientBattle.round());
        if (pvp && (ClientBattle.myGames() > 0 || ClientBattle.oppGames() > 0)) {
            chip += "  ·  " + ClientBattle.myGames() + "-" + ClientBattle.oppGames();
        }
        int cw = font.width(chip) + 14;
        int cx = width / 2 - cw / 2;
        g.fill(cx, 5, cx + cw, 20, 0xC0081E16);
        g.renderOutline(cx, 5, cw, 15, GOLD_DIM);
        g.drawString(font, chip, cx + 7, 9, GOLD, false);
        String right = pvp ? "vs " + shorten(opp)
                : "vs " + Difficulty.values()[Mth.clamp(ClientBattle.difficulty(), 0, 2)].label() + " CPU";
        g.drawString(font, right, width - 10 - font.width(right), 9, TEXT_DIM, false);
    }

    /** Bottom dock: card-size button left, primary action centre, leave right. */
    private void drawDock(GuiGraphics g, int phase, boolean pvp, int mouseX, int mouseY,
                          long now, int dockY) {
        g.fill(0, dockY, width, height, BAND);
        g.fill(0, dockY, width, dockY + 1, GOLD_DIM);
        int by = dockY + 6;

        // size button, left
        String sizeLabel = "Cards: " + ClientPrefs.cardSize().label;
        int sw = font.width(sizeLabel) + 14;
        sizeRect = new int[]{10, by, sw, 18};
        boolean sHover = inRect(mouseX, mouseY, sizeRect);
        g.fill(10, by, 10 + sw, by + 18, sHover ? 0xFF20463A : 0xFF14352A);
        g.renderOutline(10, by, sw, 18, sHover ? GOLD : EDGE);
        g.drawString(font, sizeLabel, 17, by + 5, TEXT_DIM, false);

        // emote button (PvP only), just right of the size button
        emoteRect = null;
        if (pvp) {
            String el = "Emote";
            int ew = font.width(el) + 16;
            int ex = 10 + sw + 8;
            emoteRect = new int[]{ex, by, ew, 18};
            boolean eHover = inRect(mouseX, mouseY, emoteRect) || emoteOpen;
            g.fill(ex, by, ex + ew, by + 18, eHover ? 0xFF3A5E2C : 0xFF223A18);
            g.renderOutline(ex, by, ew, 18, eHover ? GOLD : EDGE);
            g.drawString(font, el, ex + 8, by + 5, 0xFFFFFFFF, false);
        }

        // primary action, centre. In PvP the round is server-paced, so the only
        // centre button is Rematch at the end.
        actionRect = null;
        String label = pvp
                ? (phase == BattleSyncPayload.FINISHED ? "Rematch" : null)
                : switch (phase) {
                    case BattleSyncPayload.CPU_PICK -> "Reveal their pick";
                    case BattleSyncPayload.RESULT -> "Next >";
                    case BattleSyncPayload.FINISHED -> "Play Again";
                    default -> null;
                };
        if (label != null) {
            int w = Math.max(110, font.width(label) + 28);
            int x = width / 2 - w / 2;
            actionRect = new int[]{x, by, w, 18};
            boolean hover = inRect(mouseX, mouseY, actionRect);
            float pulse = 0.5f + 0.5f * (float) Math.sin(now / 350.0);
            int glowA = (int) (0x30 + 0x28 * pulse) << 24;
            g.fill(x - 3, by - 3, x + w + 3, by + 21, glowA | 0x00E9C46A);
            g.fill(x, by, x + w, by + 18, hover ? 0xFF3BA85E : 0xFF2E7D46);
            g.renderOutline(x, by, w, 18, hover ? GOLD : 0x66FFFFFF);
            g.drawString(font, label, x + (w - font.width(label)) / 2, by + 5, 0xFFFFFFFF, true);
        }

        // leave, right — needs a confirming second click while the game is live
        boolean live = phase != BattleSyncPayload.FINISHED;
        boolean armed = leaveArmedAt > 0 && now - leaveArmedAt < LEAVE_CONFIRM_MS;
        String leaveLabel = (armed && live) ? "Forfeit?!" : "Leave";
        int lw = font.width(leaveLabel) + 20;
        int lx = width - lw - 10;
        leaveRect = new int[]{lx, by, lw, 18};
        boolean lHover = inRect(mouseX, mouseY, leaveRect);
        int base = armed && live ? 0xFF8A2020 : 0xFF5A2530;
        g.fill(lx, by, lx + lw, by + 18, lHover ? 0xFF7A3140 : base);
        g.renderOutline(lx, by, lw, 18, armed && live ? 0xFFF0625A : EDGE);
        g.drawString(font, leaveLabel, lx + 10, by + 5, 0xFFFFFFFF, armed && live);
    }

    // --- pieces ---

    /** A nameplate bar riding the top edge of a card: accent, name, count. */
    private void nameplate(GuiGraphics g, int x, int y, int w, String name, int count,
                           int accent, boolean active) {
        g.fill(x, y, x + w, y + 13, 0xC0081E16);
        g.renderOutline(x, y, w, 13, active ? accent : EDGE);
        g.fill(x, y, x + 3, y + 13, accent);
        g.drawString(font, name, x + 6, y + 3, active ? 0xFFFFFFFF : TEXT_DIM, active);
        String c = "x" + count;
        int cx = x + w - font.width(c) - 4;
        // tiny card glyph beside the count
        g.fill(cx - 8, y + 3, cx - 3, y + 10, 0xFF7A5F3E);
        g.renderOutline(cx - 8, y + 3, 5, 7, 0xFF5F4A32);
        g.drawString(font, c, cx, y + 3, TEXT_DIM, false);
    }

    private void drawStatusChip(GuiGraphics g, int phase, String opp, int y, long now) {
        String text;
        int color;
        switch (phase) {
            case BattleSyncPayload.PLAYER_PICK -> {
                text = "YOUR PICK";
                color = YOU_ACCENT;
            }
            case BattleSyncPayload.CPU_PICK -> {
                text = "CPU IS THINKING" + ".".repeat((int) ((now / 350) % 4));
                color = OPP_ACCENT;
            }
            case BattleSyncPayload.OPPONENT_PICK -> {
                text = shorten(opp).toUpperCase(Locale.ROOT) + " IS CHOOSING"
                        + ".".repeat((int) ((now / 350) % 4));
                color = OPP_ACCENT;
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

    private void pile(GuiGraphics g, int x, int centerY, int count) {
        int stack = Math.min(8, Math.max(count > 0 ? 1 : 0, count / 2));
        int w = 14, h = 19;
        int baseY = centerY + 10;
        for (int i = 0; i < stack; i++) {
            int py = baseY - i * 2;
            g.fill(x - 1, py - 1, x + w + 1, py + h + 1, 0xFF2A1F12);
            g.fill(x, py, x + w, py + h, 0xFF7A5F3E);
            g.renderOutline(x, py, w, h, 0xFF5F4A32);
        }
    }

    /** The VS medallion: a gold diamond with the beat of the table, tally + pot below. */
    private void drawCenterInfo(GuiGraphics g, int centerY) {
        int cx = width / 2;
        float beat = 1f + 0.05f * (float) Math.sin(System.currentTimeMillis() / 500.0);
        var pose = g.pose();
        // the diamond behind the VS
        pose.pushPose();
        pose.translate(cx, centerY - 8f, 0);
        pose.mulPose(Axis.ZP.rotationDegrees(45f));
        pose.scale(beat, beat, 1f);
        g.fill(-11, -11, 11, 11, 0xC0081E16);
        g.renderOutline(-11, -11, 22, 22, GOLD_DIM);
        pose.popPose();
        pose.pushPose();
        pose.translate(cx, centerY - 8f, 0);
        pose.scale(1.4f * beat, 1.4f * beat, 1f);
        g.drawString(font, "VS", -font.width("VS") / 2, -4, GOLD, true);
        pose.popPose();
        String tally = ClientBattle.playerCount() + " — " + ClientBattle.cpuCount();
        g.drawCenteredString(font, tally, cx, centerY + 10, 0xFFCED6E0);
        if (ClientBattle.potCount() > 0) {
            g.drawCenteredString(font, "POT " + ClientBattle.potCount(), cx, centerY + 22, 0xFFE7C24A);
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
            pose.pushPose();
            pose.translate(x, centerY + arc, 0);
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
                case 1 -> oppName.toUpperCase(Locale.ROOT) + " WINS THE ROUND";
                default -> "TIE — INTO THE POT";
            };
            color = winner == 0 ? 0xFF6BE87A : winner == 1 ? OPP_ACCENT : 0xFFE7C24A;
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

    /** Little gold L-marks in the corners of the playing surface. */
    private void cornerTicks(GuiGraphics g, int x0, int y0, int x1, int y1) {
        int len = 7;
        for (int[] c : new int[][]{{x0 + 4, y0 + 4, 1, 1}, {x1 - 4, y0 + 4, -1, 1},
                {x0 + 4, y1 - 4, 1, -1}, {x1 - 4, y1 - 4, -1, -1}}) {
            g.fill(c[0], c[1], c[0] + c[2] * len, c[1] + c[3], GOLD_DIM);
            g.fill(c[0], c[1], c[0] + c[2], c[1] + c[3] * len, GOLD_DIM);
        }
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
        // the emote wheel captures all clicks while open
        if (emoteOpen) {
            for (int i = 0; i < emoteBtnRects.length; i++) {
                if (inRect((int) mouseX, (int) mouseY, emoteBtnRects[i])) {
                    click();
                    send(BattleActionPayload.EMOTE, i);
                    emoteOpen = false;
                    return true;
                }
            }
            emoteOpen = false; // click anywhere else closes it
            return true;
        }
        if (emoteRect != null && inRect((int) mouseX, (int) mouseY, emoteRect)) {
            click();
            emoteOpen = true;
            return true;
        }
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
        if (keyCode == 256 && emoteOpen) { // ESC closes the emote wheel first
            emoteOpen = false;
            return true;
        }
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
                // the centre button is Rematch; the server pairs both requests
                send(BattleActionPayload.REMATCH, 0);
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
