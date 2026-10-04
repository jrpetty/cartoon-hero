package com.jrpetty.mobtrumps.client;

import com.jrpetty.mobtrumps.BattleActionPayload;
import com.jrpetty.mobtrumps.BattleSyncPayload;
import com.jrpetty.mobtrumps.game.BattleLayout;
import com.jrpetty.mobtrumps.game.CampaignDecks;
import com.jrpetty.mobtrumps.game.CampaignMission;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The on-screen Mob Trumps battle, laid out like a real card table — see
 * {@link BattleLayout} for the plan, which this class only draws.
 *
 * <p>Your hand runs down the left as small cards in the order they will be
 * played; theirs runs down the right, face down. Between the two big cards is
 * the stat board: your six stats at full text size, with keys 1-6 and (with
 * battle hints on) a bar for how often each one wins; once the round turns,
 * their numbers fill in beside yours and the stat that was played lights up.
 * Under it, a strip of the rounds so far.
 *
 * <p>Drives the CPU game, campaign missions and live duels alike. The CPU
 * plays its own turns; between rounds of a CPU game the table moves on by
 * itself after a moment unless auto-continue is switched off in the dock. The
 * end of a game opens a result panel with the story of it — rounds, best run,
 * best card — and everything it paid.
 */
public class BattleScreen extends Screen {

    private static final long FLIP_MS = 440L;
    private static final long DEAL_MS = 320L;
    private static final long LEAVE_CONFIRM_MS = 2500L;
    /** How long a CPU-game result stays up before auto-continue moves on. */
    private static final long AUTO_MS = 2200L;
    private static final long PANEL_IN_MS = 240L;
    private static final long COIN_SPIN_MS = 900L;
    private static final long EMOTE_MS = 2800L;
    private static final String[] EMOTES = {"GG", "GL", "Nice", "Close", "Oops", "Wow"};

    // felt & trim palette. The base greens are lifted and then scaled again by
    // the Arena brightness setting — see lit() — so every felt colour goes
    // through it.
    private static final int FELT_LIGHT = 0xFF20785B;
    private static final int FELT_DARK = 0xFF0D4433;
    private static final int SURFACE_LIGHT = 0xFF2A8A69;
    private static final int SURFACE_DARK = 0xFF165944;
    private static final int BAND = 0xB4082A1E;
    private static final int PANEL = 0xC8081E16;
    private static final int GOLD = 0xFFF3D68A;
    private static final int GOLD_DIM = 0xFFB89555;
    private static final int TEXT_DIM = 0xFFD5E2DB;
    private static final int TEXT_FAINT = 0xFF8FA89C;
    private static final int EDGE = 0xFF48836B;
    private static final int YOU_ACCENT = 0xFF55E06A;
    private static final int OPP_ACCENT = 0xFFF0857D;
    private static final int TIE_GOLD = 0xFFE7C24A;
    private static final int WIN_GOLD = 0xFFFFD54A;
    private static final int XP_GREEN = 0xFF80FF20;
    private static final int EMERALD = 0xFF3DDC84;

    /** The result panel is drawn on a canvas this size, then fitted to its rect. */
    private static final int PANEL_W = 300;
    private static final int PANEL_H = 172;
    /** The MVP card's size on that canvas. */
    private static final float MVP_SCALE = 0.3f;
    /** The gap between your number and theirs on the board, for the separator. */
    private static final int SEP_W = 9;

    private final Map<String, LivingEntity> entityCache = new HashMap<>();
    private final long openedAt = System.currentTimeMillis();
    private long leaveArmedAt = -1;
    private boolean emoteOpen;
    private final int[][] emoteBtnRects = new int[EMOTES.length][];

    private BattleLayout.Layout layout;
    /** The NEXT already sent for a state, so auto-continue fires once per round. */
    private int autoSentTicket = -1;
    private long rematchSentAt = -1;

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
        // a campaign mission hands you back to the route, so a clear rolls
        // straight on into picking the next one
        if (minecraft != null && ClientBattle.campaignMission() > 0) {
            minecraft.setScreen(new CampaignScreen());
            return;
        }
        super.onClose();
    }

    private static int lit(int argb) {
        return ClientPrefs.lit(argb);
    }

    private static int translucent(int argb, int alpha) {
        return (Mth.clamp(alpha, 0, 255) << 24) | (argb & 0x00FFFFFF);
    }

    /** The card-size setting as a cap on the fitted scale. */
    private static float sizeCap() {
        return ClientPrefs.resolveScale(BattleLayout.MAX_SCALE);
    }

    private static boolean live(int phase) {
        return phase != BattleSyncPayload.FINISHED;
    }

    private static boolean revealed(int phase) {
        return phase == BattleSyncPayload.RESULT || phase == BattleSyncPayload.FINISHED
                || phase == BattleSyncPayload.GAME_OVER;
    }

    // =====================================================================
    // render
    // =====================================================================

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        ClientBattle.poll(); // promote any held post-reveal state once it has played
        long now = System.currentTimeMillis();
        long elapsed = now - ClientBattle.changedAt();
        int phase = ClientBattle.phase();
        boolean pvp = ClientBattle.isPvp();
        BattleLayout.Layout L = BattleLayout.solve(width, height, sizeCap(), pvp);
        layout = L;
        float fadeIn = Mth.clamp((now - openedAt) / 260f, 0f, 1f);
        boolean flipping = phase == BattleSyncPayload.RESULT && elapsed < FLIP_MS;
        String opp = oppName();

        drawBackdrop(g);
        drawFelt(g, L);

        // deal-in: on pick phases the cards ease to the table from off-stage
        boolean dealing = (phase == BattleSyncPayload.PLAYER_PICK
                || phase == BattleSyncPayload.CPU_PICK
                || phase == BattleSyncPayload.OPPONENT_PICK)
                && elapsed < DEAL_MS && !ClientPrefs.reducedMotion();
        float dealT = dealing ? easeOutCubic(elapsed / (float) DEAL_MS) : 1f;
        int myY = L.myCard().y() + Math.round((1f - dealT) * 46f);
        int oppY = L.oppCard().y() - Math.round((1f - dealT) * 46f);

        drawHands(g, L, mouseX, mouseY);
        boolean myTurn = phase == BattleSyncPayload.PLAYER_PICK;
        boolean theirTurn = phase == BattleSyncPayload.CPU_PICK || phase == BattleSyncPayload.OPPONENT_PICK;
        nameplate(g, L.myPlate(), "YOU", ClientBattle.playerCount(), YOU_ACCENT, myTurn);
        nameplate(g, L.oppPlate(), pvp ? shorten(opp) : "CPU", ClientBattle.cpuCount(), OPP_ACCENT, theirTurn);

        int hoverStat = hoveredStat(mouseX, mouseY, phase);
        drawMyCard(g, L, myY, phase, elapsed, mouseX, mouseY, hoverStat);
        drawOppCard(g, L, oppY, phase, elapsed, flipping);

        drawStatus(g, L, phase, elapsed, opp, now);
        drawBoard(g, L, phase, elapsed, hoverStat, now);

        // spoils fly to the winner's hand once the card lands
        if (phase == BattleSyncPayload.RESULT && elapsed >= FLIP_MS && !ClientPrefs.reducedMotion()) {
            drawFlyingCards(g, L, ClientBattle.winner(), elapsed - FLIP_MS);
        }
        if (phase == BattleSyncPayload.RESULT && ClientBattle.winner() == 2 && ClientBattle.coin() != 0) {
            drawCoinFlip(g, L, Math.max(0, elapsed - FLIP_MS), ClientBattle.coin(), pvp ? shorten(opp) : "CPU");
        }

        boolean picking = phase == BattleSyncPayload.PLAYER_PICK || phase == BattleSyncPayload.OPPONENT_PICK;
        if (pvp && picking && ClientBattle.turnSeconds() > 0) {
            drawTurnTimer(g, elapsed, ClientBattle.turnSeconds() * 1000L, myTurn, L.felt().x(),
                    L.felt().right(), BattleLayout.HEADER_H + 2);
        }
        drawEmote(g, L, now);

        drawHeader(g, pvp, opp);
        drawDock(g, L, phase, pvp, mouseX, mouseY, now, elapsed, opp);

        if (phase == BattleSyncPayload.FINISHED || phase == BattleSyncPayload.GAME_OVER) {
            drawResultPanel(g, L, phase, elapsed, pvp, opp);
        } else {
            drawHandPreview(g, L, mouseX, mouseY);
            drawHistoryTooltip(g, L, mouseX, mouseY);
        }

        autoContinue(phase, elapsed);

        if (fadeIn < 1f) {
            g.fill(0, 0, width, height, ((int) ((1f - fadeIn) * 0xE0) << 24));
        }
        if (emoteOpen) {
            drawEmoteWheel(g, L, mouseX, mouseY);
        }
    }

    private String oppName() {
        return ClientBattle.label().isEmpty() ? "Opponent" : ClientBattle.label();
    }

    /** A campaign mission is played in its own place; everything else on green felt. */
    private void drawBackdrop(GuiGraphics g) {
        CampaignMission place = mission();
        if (place != null) {
            MissionArt.drawBackdrop(g, place, width, height, 0);
        } else {
            g.fillGradient(0, 0, width, height, lit(FELT_LIGHT), lit(FELT_DARK));
        }
        for (int i = 0; i < 90; i++) {
            int sx = (i * 97 + 31) % Math.max(1, width);
            int sy = (i * 61 + 17) % Math.max(1, height);
            g.fill(sx, sy, sx + 1, sy + 1, 0x14FFFFFF);
        }
        // a soft warm pool of light over the middle of the table
        g.fillGradient(0, BattleLayout.HEADER_H, width, height / 2, 0x18FFF2C8, 0x00FFF2C8);
        g.fillGradient(0, height * 3 / 4, width, height, 0x00000000, 0x33000000);
    }

    private static CampaignMission mission() {
        return ClientBattle.campaignMission() > 0 ? CampaignDecks.byIndex(ClientBattle.campaignMission()) : null;
    }

    /** The gold-piped playing surface the whole game sits on. */
    private void drawFelt(GuiGraphics g, BattleLayout.Layout L) {
        BattleLayout.Rect f = L.felt();
        g.fill(f.x() + 3, f.y() + 4, f.right() + 3, f.bottom() + 4, 0x44000000); // drop shadow
        CampaignMission place = mission();
        if (place != null) {
            // in a mission the felt is laid ON the scene, so it lets the place show through
            g.fillGradient(f.x(), f.y(), f.right(), f.bottom(),
                    translucent(lit(SURFACE_LIGHT), 0x62), translucent(lit(SURFACE_DARK), 0x8C));
            g.renderOutline(f.x(), f.y(), f.w(), f.h(), place.anchor().accent());
        } else {
            g.fillGradient(f.x(), f.y(), f.right(), f.bottom(), lit(SURFACE_LIGHT), lit(SURFACE_DARK));
        }
        g.renderOutline(f.x(), f.y(), f.w(), f.h(), GOLD_DIM);
        g.renderOutline(f.x() + 2, f.y() + 2, f.w() - 4, f.h() - 4, 0x55E9C46A);
        int len = 7;
        for (int[] c : new int[][]{{f.x() + 4, f.y() + 4, 1, 1}, {f.right() - 4, f.y() + 4, -1, 1},
                {f.x() + 4, f.bottom() - 4, 1, -1}, {f.right() - 4, f.bottom() - 4, -1, -1}}) {
            g.fill(c[0], c[1], c[0] + c[2] * len, c[1] + c[3], GOLD_DIM);
            g.fill(c[0], c[1], c[0] + c[2], c[1] + c[3] * len, GOLD_DIM);
        }
    }

    // --- hands --------------------------------------------------------------

    /** Your hand face up in play order down the left; theirs face down on the right. */
    private void drawHands(GuiGraphics g, BattleLayout.Layout L, int mouseX, int mouseY) {
        if (!L.hands()) {
            return;
        }
        List<ClientBattle.Held> hand = ClientBattle.hand();
        int n = hand.size();
        int hovered = hoveredMini(L, mouseX, mouseY);
        for (int i = 0; i < n; i++) {
            BattleLayout.Rect r = BattleLayout.mini(L.myHand(), L.miniScale(), i, n);
            g.fill(r.x() + 1, r.y() + 2, r.right() + 2, r.bottom() + 2, 0x55000000);
            ClientBattle.Held held = hand.get(i);
            CardRenderer.renderCard(g, font, held.base(), held.level(), r.x(), r.y(), L.miniScale(),
                    0, 0, null, held.level() > 0, false);
            if (i == hovered) {
                g.renderOutline(r.x() - 1, r.y() - 1, r.w() + 2, r.h() + 2, GOLD);
            } else if (i == 0) {
                // the card in play now
                g.renderOutline(r.x() - 1, r.y() - 1, r.w() + 2, r.h() + 2, YOU_ACCENT);
            }
        }
        int m = Math.min(ClientBattle.cpuCount(), 64);
        for (int i = 0; i < m; i++) {
            BattleLayout.Rect r = BattleLayout.mini(L.oppHand(), L.miniScale(), i, m);
            g.fill(r.x() + 1, r.y() + 2, r.right() + 2, r.bottom() + 2, 0x55000000);
            CardRenderer.renderBack(g, font, r.x(), r.y(), L.miniScale());
        }
    }

    private int hoveredMini(BattleLayout.Layout L, int mouseX, int mouseY) {
        if (!L.hands() || !L.myHand().contains(mouseX, mouseY)) {
            return -1;
        }
        int n = ClientBattle.hand().size();
        // the later card is drawn on top, so test from the bottom of the stack up
        for (int i = n - 1; i >= 0; i--) {
            if (BattleLayout.mini(L.myHand(), L.miniScale(), i, n).contains(mouseX, mouseY)) {
                return i;
            }
        }
        return -1;
    }

    /** Hovering a card in your hand shows it at a readable size beside the column. */
    private void drawHandPreview(GuiGraphics g, BattleLayout.Layout L, int mouseX, int mouseY) {
        int i = hoveredMini(L, mouseX, mouseY);
        List<ClientBattle.Held> hand = ClientBattle.hand();
        if (i < 0 || i >= hand.size()) {
            return;
        }
        ClientBattle.Held held = hand.get(i);
        float scale = Mth.clamp((L.felt().h() - 24) / (float) BattleLayout.CARD_H, 0.3f, 0.6f);
        int w = Math.round(BattleLayout.CARD_W * scale);
        int h = Math.round(BattleLayout.CARD_H * scale);
        int x = Math.min(L.myHand().right() + 6, width - w - 4);
        int y = Mth.clamp(mouseY - h / 2, L.felt().y() + 2, Math.max(L.felt().y() + 2, L.felt().bottom() - h - 14));
        var pose = g.pose();
        pose.pushPose();
        pose.translate(0, 0, 300);
        g.fill(x - 3, y - 3, x + w + 3, y + h + 3, 0xC0000000);
        LivingEntity mob = CardRenderer.portraitEntity(minecraft, held.base(), entityCache);
        CardRenderer.renderCard(g, font, held.base(), held.level(), x, y, scale, mouseX, mouseY, mob,
                held.level() > 0, true);
        String tag = i == 0 ? "IN PLAY" : "NEXT · " + ordinal(i);
        int tw = font.width(tag) + 8;
        g.fill(x, y + h + 3, x + tw, y + h + 14, PANEL);
        g.drawString(font, tag, x + 4, y + h + 5, i == 0 ? YOU_ACCENT : GOLD, false);
        pose.popPose();
    }

    private static String ordinal(int i) {
        return switch (i) {
            case 1 -> "1ST";
            case 2 -> "2ND";
            case 3 -> "3RD";
            default -> i + "TH";
        };
    }

    // --- the big cards ------------------------------------------------------

    private void drawMyCard(GuiGraphics g, BattleLayout.Layout L, int y, int phase, long elapsed,
                            int mouseX, int mouseY, int hoverStat) {
        ClientBattle.Held held = ClientBattle.myHeld();
        if (held == null) {
            return;
        }
        BattleLayout.Rect c = L.myCard();
        int winner = ClientBattle.winner();
        boolean myPick = phase == BattleSyncPayload.PLAYER_PICK;
        boolean result = phase == BattleSyncPayload.RESULT;
        drawCardGlow(g, c.x(), y, c.w(), c.h(), result && winner == 0 ? WIN_GOLD : (myPick ? YOU_ACCENT : 0));
        boolean pulse = winner == 0 && result && elapsed >= FLIP_MS;
        withPulse(g, pulse, elapsed - FLIP_MS, c.x() + c.w() / 2f, y + c.h() / 2f, () -> {
            LivingEntity mob = CardRenderer.portraitEntity(minecraft, held.base(), entityCache);
            CardRenderer.renderCard(g, font, held.base(), held.level(), c.x(), y, L.scale(),
                    mouseX, mouseY, mob, held.level() > 0, true);
        });
        // the stat being considered or played, ringed on the card itself
        int chosen = ClientBattle.chosenStat();
        if (result && chosen >= 0 && elapsed >= FLIP_MS) {
            int[] r = statRow(c.x(), y, L.scale(), chosen);
            g.renderOutline(r[0], r[1], r[2], r[3], WIN_GOLD);
        } else if (myPick && hoverStat >= 0) {
            int[] r = statRow(c.x(), y, L.scale(), hoverStat);
            g.fill(r[0], r[1], r[0] + r[2], r[1] + r[3], 0x553BE0A0);
            g.renderOutline(r[0], r[1], r[2], r[3], YOU_ACCENT);
        }
        if (result && winner == 1 && elapsed >= FLIP_MS) {
            int dim = (int) (Mth.clamp((elapsed - FLIP_MS) / 300f, 0f, 1f) * 0x66) << 24;
            g.fill(c.x() - 2, y - 2, c.right() + 2, y + c.h() + 2, dim | 0x000A0806);
        }
    }

    /** The other side's card: face down until the play resolves, then it FLIPS. */
    private void drawOppCard(GuiGraphics g, BattleLayout.Layout L, int y, int phase, long elapsed,
                             boolean flipping) {
        BattleLayout.Rect c = L.oppCard();
        ClientBattle.Held held = ClientBattle.oppHeld();
        boolean faceUp = revealed(phase) && held != null;
        var pose = g.pose();
        if (flipping && held != null) {
            float ft = elapsed / (float) FLIP_MS;
            boolean backHalf = ft < 0.5f;
            float sx = Math.max(0.04f, backHalf ? 1f - ft * 2f : ft * 2f - 1f);
            float mid = c.x() + c.w() / 2f;
            pose.pushPose();
            pose.translate(mid, 0, 0);
            pose.scale(sx, 1f, 1f);
            pose.translate(-mid, 0, 0);
            if (backHalf) {
                CardRenderer.renderBack(g, font, c.x(), y, L.scale());
            } else {
                CardRenderer.renderCard(g, font, held.base(), held.level(), c.x(), y, L.scale(),
                        0, 0, null, held.level() > 0, false);
            }
            pose.popPose();
            float flash = 1f - Math.abs(ft - 0.5f) * 4f;
            if (flash > 0f) {
                g.fill(c.x() - 4, y - 4, c.right() + 4, y + c.h() + 4, ((int) (flash * 0x80) << 24) | 0x00FFFFFF);
            }
            return;
        }
        if (faceUp) {
            int winner = ClientBattle.winner();
            boolean result = phase == BattleSyncPayload.RESULT;
            drawCardGlow(g, c.x(), y, c.w(), c.h(), winner == 1 && result ? WIN_GOLD : 0);
            boolean pulse = winner == 1 && result && elapsed >= FLIP_MS;
            withPulse(g, pulse, elapsed - FLIP_MS, c.x() + c.w() / 2f, y + c.h() / 2f, () -> {
                LivingEntity mob = CardRenderer.portraitEntity(minecraft, held.base(), entityCache);
                CardRenderer.renderCard(g, font, held.base(), held.level(), c.x(), y, L.scale(),
                        0, 0, mob, held.level() > 0, false);
            });
            int chosen = ClientBattle.chosenStat();
            if (chosen >= 0 && result) {
                int[] r = statRow(c.x(), y, L.scale(), chosen);
                g.renderOutline(r[0], r[1], r[2], r[3], WIN_GOLD);
            }
            if (winner == 0 && result && elapsed >= FLIP_MS) {
                int dim = (int) (Mth.clamp((elapsed - FLIP_MS) / 300f, 0f, 1f) * 0x66) << 24;
                g.fill(c.x() - 2, y - 2, c.right() + 2, y + c.h() + 2, dim | 0x000A0806);
            }
            return;
        }
        boolean theirTurn = phase == BattleSyncPayload.CPU_PICK || phase == BattleSyncPayload.OPPONENT_PICK;
        if (theirTurn) {
            drawCardGlow(g, c.x(), y, c.w(), c.h(), OPP_ACCENT);
        }
        CardRenderer.renderBack(g, font, c.x(), y, L.scale());
    }

    /** A stat row's rectangle on a card drawn at {@code scale}: {x, y, w, h}. */
    private static int[] statRow(int cardX, int cardY, float scale, int i) {
        int x0 = cardX + Math.round(12 * scale);
        int x1 = cardX + Math.round((CardRenderer.CARD_W - 12) * scale);
        int top = cardY + Math.round((CardRenderer.STAT_TOP + i * CardRenderer.ROW_H) * scale);
        return new int[]{x0, top, x1 - x0, Math.max(1, Math.round(CardRenderer.ROW_H * scale))};
    }

    // --- the stat board -----------------------------------------------------

    /** The board's chip: whose move it is, or how the round went. */
    private void drawStatus(GuiGraphics g, BattleLayout.Layout L, int phase, long elapsed, String opp, long now) {
        String text;
        int color;
        String dots = ".".repeat((int) ((now / 350) % 4));
        switch (phase) {
            case BattleSyncPayload.PLAYER_PICK -> {
                text = "YOUR PICK";
                color = YOU_ACCENT;
            }
            case BattleSyncPayload.CPU_PICK -> {
                text = "CPU IS THINKING" + dots;
                color = OPP_ACCENT;
            }
            case BattleSyncPayload.OPPONENT_PICK -> {
                text = shorten(opp).toUpperCase(Locale.ROOT) + " IS CHOOSING" + dots;
                color = OPP_ACCENT;
            }
            case BattleSyncPayload.RESULT -> {
                if (elapsed < FLIP_MS) {
                    text = "THE REVEAL...";
                    color = GOLD;
                } else {
                    int w = ClientBattle.winner();
                    text = w == 0 ? "YOU WIN THE ROUND" : w == 1
                            ? (ClientBattle.isPvp() ? shorten(opp).toUpperCase(Locale.ROOT) : "CPU") + " WINS IT"
                            : "TIE — INTO THE POT";
                    color = w == 0 ? YOU_ACCENT : w == 1 ? OPP_ACCENT : TIE_GOLD;
                }
            }
            case BattleSyncPayload.GAME_OVER -> {
                text = "GAME OVER";
                color = GOLD;
            }
            case BattleSyncPayload.FINISHED -> {
                text = "MATCH OVER";
                color = GOLD;
            }
            default -> {
                return;
            }
        }
        BattleLayout.Rect s = L.status();
        float pop = phase == BattleSyncPayload.RESULT && elapsed >= FLIP_MS && !ClientPrefs.reducedMotion()
                ? 1f + 0.12f * (float) Math.sin(Mth.clamp((elapsed - FLIP_MS) / 260f, 0f, 1f) * Math.PI) : 1f;
        var pose = g.pose();
        pose.pushPose();
        pose.translate(s.midX(), s.midY(), 0);
        pose.scale(pop, pop, 1f);
        pose.translate(-s.midX(), -s.midY(), 0);
        g.fill(s.x(), s.y(), s.right(), s.bottom(), PANEL);
        g.renderOutline(s.x(), s.y(), s.w(), s.h(), color);
        String fitted = fitWidth(text, s.w() - 8);
        g.drawString(font, fitted, s.midX() - font.width(fitted) / 2, s.y() + 3, color, false);
        pose.popPose();
    }

    /** Which board row or card row the mouse is on while you are picking, or -1. */
    private int hoveredStat(int mouseX, int mouseY, int phase) {
        if (phase != BattleSyncPayload.PLAYER_PICK || layout == null || emoteOpen) {
            return -1;
        }
        for (int i = 0; i < Stat.values().length; i++) {
            if (layout.row(i).contains(mouseX, mouseY)) {
                return i;
            }
            BattleLayout.Rect c = layout.myCard();
            int[] r = statRow(c.x(), c.y(), layout.scale(), i);
            if (mouseX >= r[0] && mouseX < r[0] + r[2] && mouseY >= r[1] && mouseY < r[1] + r[3]) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Your six stats at full text size, keyed 1-6, with how often each wins as
     * a bar when hints are on; after the reveal, their numbers beside yours and
     * the played stat lit up. The footer carries the pot and the rounds so far.
     */
    private void drawBoard(GuiGraphics g, BattleLayout.Layout L, int phase, long elapsed, int hoverStat, long now) {
        BattleLayout.Rect b = L.board();
        g.fill(b.x() + 2, b.y() + 3, b.right() + 2, b.bottom() + 3, 0x44000000);
        g.fill(b.x(), b.y(), b.right(), b.bottom(), PANEL);
        g.renderOutline(b.x(), b.y(), b.w(), b.h(), GOLD_DIM);

        ClientBattle.Held mineHeld = ClientBattle.myHeld();
        MobCard mine = mineHeld == null ? null : mineHeld.card();
        ClientBattle.Held theirsHeld = ClientBattle.oppHeld();
        MobCard theirs = theirsHeld == null ? null : theirsHeld.card();
        boolean reveal = revealed(phase) && theirs != null
                && !(phase == BattleSyncPayload.RESULT && elapsed < FLIP_MS);
        boolean myPick = phase == BattleSyncPayload.PLAYER_PICK;
        int chosen = ClientBattle.chosenStat();
        int winner = ClientBattle.winner();

        if (myPick && !ClientPrefs.reducedMotion()) {
            // a slow shimmer down the board says "your move"
            float roll = (now % 2000L) / 2000f;
            int sy = L.rows().y() + (int) (roll * L.rows().h());
            g.fill(L.rows().x(), sy, L.rows().right(), sy + 1, 0x22FFFFFF);
        }

        Stat[] stats = Stat.values();
        for (int i = 0; i < stats.length; i++) {
            Stat stat = stats[i];
            BattleLayout.Rect r = L.row(i);
            boolean played = reveal && chosen == i;
            boolean hover = myPick && hoverStat == i;
            if (played) {
                g.fill(r.x(), r.y(), r.right(), r.bottom(), 0x50E9C46A);
                g.renderOutline(r.x(), r.y(), r.w(), r.h(), WIN_GOLD);
            } else if (hover) {
                g.fill(r.x(), r.y(), r.right(), r.bottom(), 0x553BE0A0);
                g.renderOutline(r.x(), r.y(), r.w(), r.h(), YOU_ACCENT);
            } else if (i % 2 == 1) {
                g.fill(r.x(), r.y(), r.right(), r.bottom(), 0x14FFFFFF);
            }
            int ty = r.y() + Math.max(0, (r.h() - 8) / 2);
            boolean dim = reveal && !played;
            // the key to press, while it can be pressed
            int x = r.x() + 2;
            if (myPick) {
                g.fill(x, ty - 1, x + 8, ty + 8, hover ? 0xFF2E7D46 : 0xC0103A2A);
                g.drawString(font, String.valueOf(i + 1), x + 2, ty, hover ? 0xFFFFFFFF : TEXT_DIM, false);
            }
            x += 11;
            int labelColor = dim ? TEXT_FAINT : (played ? WIN_GOLD : 0xFFFFFFFF);
            String label = r.w() >= 120 ? stat.label : stat.shortLabel;
            g.drawString(font, label, x, ty, labelColor, false);
            if (stat.lowerWins) {
                // a small down-caret: lower wins on this one
                int cx = x + font.width(label) + 3;
                int col = dim ? TEXT_FAINT : TIE_GOLD;
                g.fill(cx, ty + 2, cx + 5, ty + 3, col);
                g.fill(cx + 1, ty + 3, cx + 4, ty + 4, col);
                g.fill(cx + 2, ty + 4, cx + 3, ty + 5, col);
            }
            // values, right-aligned: yours, then theirs once they are known
            int myVal = mine == null ? 0 : mine.stat(stat);
            String mv = mine == null ? "-" : String.valueOf(myVal);
            String tv = reveal ? String.valueOf(theirs.stat(stat)) : "?";
            // fixed columns, each as wide as "10", the widest a stat can read,
            // with a gap between them for the separator: right-aligned numbers
            // of different widths otherwise ran into the separator at 10
            int valW = font.width("10");
            int theirRight = r.right() - 3;
            int theirX = theirRight - font.width(tv);
            int sepX = theirRight - valW - SEP_W;
            int myX = sepX - font.width(mv);
            int myCol = dim ? TEXT_FAINT
                    : (played ? (winner == 0 ? YOU_ACCENT : winner == 1 ? 0xFFE0E0E0 : TIE_GOLD) : 0xFFFFFFFF);
            int thCol = !reveal ? 0xFF6E8A7E : dim ? TEXT_FAINT
                    : (played ? (winner == 1 ? OPP_ACCENT : winner == 0 ? 0xFFE0E0E0 : TIE_GOLD) : TEXT_DIM);
            g.drawString(font, mv, myX, ty, myCol, played && winner == 0);
            if (played) {
                // which way the round went: a pointer at the number that took
                // it. Not "<" or ">" — those read as arithmetic, which is
                // backwards on Rarity, where the lower number wins.
                pointer(g, sepX + (SEP_W - 4) / 2, ty, winner);
            } else {
                g.drawString(font, ":", sepX + (SEP_W - font.width(":")) / 2, ty, 0xFF4E6E60, false);
            }
            g.drawString(font, tv, theirX, ty, thCol, played && winner == 1);
            // how often this number wins against the whole set — a hint, so it
            // only shows on your own pick and only with hints switched on
            if (myPick && ClientPrefs.battleHints() && mine != null && r.h() >= 12) {
                double odds = MobCards.winOdds(stat, myVal);
                int barX0 = r.x() + 13;
                int bw = Math.max(0, myX - 4 - barX0);
                int by = r.bottom() - 2;
                g.fill(barX0, by, barX0 + bw, by + 1, 0x30FFFFFF);
                g.fill(barX0, by, barX0 + (int) Math.round(bw * odds), by + 1, oddsColor(odds));
            }
        }
        drawFooter(g, L);
    }

    /**
     * A small solid triangle pointing at the winning number — left at yours,
     * right at theirs — or an "=" for a drawn round. Drawn, not typed, so it
     * cannot be mistaken for a comparison.
     */
    private void pointer(GuiGraphics g, int x, int y, int winner) {
        if (winner == 2) {
            g.drawString(font, "=", x, y, TIE_GOLD, false);
            return;
        }
        for (int i = 0; i < 4; i++) {
            int col = winner == 0 ? x + i : x + 3 - i;
            g.fill(col, y + 3 - i, col + 1, y + 4 + i, WIN_GOLD);
        }
    }

    private static int oddsColor(double odds) {
        if (odds >= 0.7) return 0xFF55E06A;
        if (odds >= 0.45) return 0xFFE7C24A;
        return 0xFFF0857D;
    }

    /** Where the history pips start in the footer, after the pot chip if there is one. */
    private int pipsStart(BattleLayout.Rect f) {
        int x = f.x() + 2;
        int pot = ClientBattle.potCount();
        return pot > 0 ? x + font.width("POT " + pot) + 9 : x;
    }

    /** The pot, and a pip for each recent round: green yours, red theirs, gold drawn. */
    private void drawFooter(GuiGraphics g, BattleLayout.Layout L) {
        BattleLayout.Rect f = L.footer();
        g.fill(f.x(), f.y(), f.right(), f.y() + 1, 0x40E9C46A);
        int ty = f.y() + 3;
        int pot = ClientBattle.potCount();
        if (pot > 0) {
            String p = "POT " + pot;
            int px = f.x() + 2;
            g.fill(px, ty - 1, px + font.width(p) + 6, ty + 9, 0xFF5A4A18);
            g.drawString(font, p, px + 3, ty, TIE_GOLD, false);
        }
        int x = pipsStart(f);
        List<Integer> rounds = ClientBattle.history();
        int fit = Math.max(0, (f.right() - 2 - x) / 7);
        if (rounds.isEmpty()) {
            g.drawString(font, fitWidth("First round", f.right() - x - 2), x, ty, TEXT_FAINT, false);
            return;
        }
        int from = Math.max(0, rounds.size() - fit);
        for (int i = from; i < rounds.size(); i++) {
            int code = rounds.get(i);
            int w = BattleSyncPayload.historyWinner(code);
            int col = w == 0 ? YOU_ACCENT : w == 1 ? OPP_ACCENT : TIE_GOLD;
            int px = x + (i - from) * 7;
            g.fill(px, ty, px + 5, ty + 7, 0xFF0A1A12);
            g.fill(px + 1, ty + 1, px + 4, ty + 6, col);
            if (BattleSyncPayload.historyChooser(code) == 0) {
                g.fill(px + 2, ty + 3, px + 3, ty + 4, 0xFFFFFFFF); // your pick
            }
        }
    }

    /** Hovering a pip names the round. */
    private void drawHistoryTooltip(GuiGraphics g, BattleLayout.Layout L, int mouseX, int mouseY) {
        BattleLayout.Rect f = L.footer();
        if (!f.contains(mouseX, mouseY)) {
            return;
        }
        List<Integer> rounds = ClientBattle.history();
        int x = pipsStart(f);
        int fit = Math.max(0, (f.right() - 2 - x) / 7);
        int from = Math.max(0, rounds.size() - fit);
        if (mouseX < x) {
            return;
        }
        int idx = from + (mouseX - x) / 7;
        if (idx < from || idx >= rounds.size()) {
            return;
        }
        int code = rounds.get(idx);
        int roundNo = ClientBattle.round() - (rounds.size() - 1 - idx);
        Stat stat = Stat.values()[Mth.clamp(BattleSyncPayload.historyStat(code), 0, Stat.values().length - 1)];
        int w = BattleSyncPayload.historyWinner(code);
        String who = w == 0 ? "you took it" : w == 1 ? "they took it" : "a draw";
        String pick = BattleSyncPayload.historyChooser(code) == 0 ? "your pick" : "their pick";
        g.renderTooltip(font, Component.literal("Round " + roundNo + " · " + stat.label + " · "
                + who + " (" + pick + ")"), mouseX, mouseY);
    }

    // --- PvP extras ---------------------------------------------------------

    /** A depleting turn-timer bar; green on your turn, red on the opponent's. */
    private void drawTurnTimer(GuiGraphics g, long elapsed, long total, boolean mine, int x0, int x1, int y) {
        float frac = Mth.clamp(1f - elapsed / (float) total, 0f, 1f);
        g.fill(x0, y, x1, y + 3, 0x80000000);
        int col = mine ? YOU_ACCENT : OPP_ACCENT;
        if (frac < 0.25f) {
            col = (System.currentTimeMillis() / 250 % 2 == 0) ? 0xFFF0625A : col;
        }
        g.fill(x0, y, x0 + (int) ((x1 - x0) * frac), y + 3, col);
    }

    /**
     * The coin that settles a drawn round, spinning over the board. Nobody won
     * the cards, so nobody has earned the next pick — it spins, squashing
     * through its edge as it turns, then lands and names who chooses.
     */
    private void drawCoinFlip(GuiGraphics g, BattleLayout.Layout L, long since, int coin, String oppName) {
        boolean mine = coin == 1;
        float t = ClientPrefs.reducedMotion() ? 1f : Mth.clamp(since / (float) COIN_SPIN_MS, 0f, 1f);
        int cx = L.board().midX();
        int cy = L.rows().y() + L.rows().h() / 2 - 8;
        int r = 13;
        float turns = (1f - (1f - t) * (1f - t) * (1f - t)) * 7.5f;
        float squash = t >= 1f ? 1f : (float) Math.abs(Math.cos(turns * Math.PI));
        int h = Math.max(1, Math.round(r * squash));
        boolean showMine = t >= 1f ? mine : (((int) (turns * 2)) % 2 == 0) == mine;
        int face = showMine ? YOU_ACCENT : OPP_ACCENT;
        var pose = g.pose();
        pose.pushPose();
        pose.translate(0, 0, 200);
        g.fill(cx - r - 2, cy - h - 2, cx + r + 2, cy + h + 2, 0xC0081E16);
        g.fill(cx - r, cy - h, cx + r, cy + h, 0xFFB8892E);
        g.fill(cx - r + 2, cy - Math.max(1, h - 2), cx + r - 2, cy + Math.max(1, h - 2), GOLD);
        if (h > 4) {
            g.fill(cx - r + 5, cy - h + 4, cx + r - 5, cy + h - 4, face);
        }
        String label = t < 1f ? "COIN FLIP"
                : mine ? "YOU PICK NEXT" : oppName.toUpperCase(Locale.ROOT) + " PICKS";
        int labelColor = t < 1f ? GOLD : mine ? YOU_ACCENT : OPP_ACCENT;
        label = fitWidth(label, L.board().w() - 12);
        int w = font.width(label) + 10;
        int lx = cx - w / 2;
        int ly = cy + r + 4;
        g.fill(lx, ly, lx + w, ly + 12, 0xE0081E16);
        g.renderOutline(lx, ly, w, 12, labelColor);
        g.drawString(font, label, lx + 5, ly + 2, labelColor, false);
        pose.popPose();
    }

    /** A speech bubble over the card of whoever emoted, fading out. */
    private void drawEmote(GuiGraphics g, BattleLayout.Layout L, long now) {
        long age = now - ClientBattle.emoteAt();
        int side = ClientBattle.emoteSide();
        String text = ClientBattle.emoteText();
        if (side < 0 || text.isEmpty() || age > EMOTE_MS) {
            return;
        }
        BattleLayout.Rect card = side == 0 ? L.myCard() : L.oppCard();
        text = fitWidth(text, Math.max(40, Math.min(width - 16, card.w() + 80)));
        int w = font.width(text) + 12;
        int x = Mth.clamp(card.midX() - w / 2, 2, Math.max(2, width - w - 2));
        int y = card.y() + 6 - (int) (Math.min(age, 300) / 300f * 4);
        int fade = age > EMOTE_MS - 400 ? (int) ((EMOTE_MS - age) / 400f * 255) : 255;
        if (fade < 8) {
            return;
        }
        var pose = g.pose();
        pose.pushPose();
        pose.translate(0, 0, 250);
        g.fill(x, y, x + w, y + 13, (Math.min(0xD0, fade) << 24));
        g.renderOutline(x, y, w, 13, (fade << 24) | 0x00E9C46A);
        g.drawString(font, text, x + 6, y + 3, (fade << 24) | 0x00FFF3C8, false);
        pose.popPose();
    }

    /** The 2x3 emote picker, opened by the dock's Emote button. */
    private void drawEmoteWheel(GuiGraphics g, BattleLayout.Layout L, int mouseX, int mouseY) {
        var pose = g.pose();
        pose.pushPose();
        pose.translate(0, 0, 450);
        g.fill(0, 0, width, height, 0x66000000);
        int cols = 3, rows = 2, bw = 58, bh = 22, pad = 6;
        int pw = cols * bw + (cols + 1) * pad;
        int ph = rows * bh + (rows + 1) * pad + 14;
        int px = width / 2 - pw / 2;
        int py = Math.max(BattleLayout.HEADER_H + 2, L.dock().y() - ph - 6);
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
        pose.popPose();
    }

    // --- bands --------------------------------------------------------------

    /** Top band: title left, ROUND (and the series) centred, opponent right. */
    private void drawHeader(GuiGraphics g, boolean pvp, String opp) {
        g.fill(0, 0, width, BattleLayout.HEADER_H, BAND);
        g.fill(0, BattleLayout.HEADER_H, width, BattleLayout.HEADER_H + 1, GOLD_DIM);
        int limit = ClientBattle.roundLimit();
        // while a stat is being chosen the round in play is the next one
        int phaseNow = ClientBattle.phase();
        boolean choosing = phaseNow == BattleSyncPayload.PLAYER_PICK || phaseNow == BattleSyncPayload.CPU_PICK
                || phaseNow == BattleSyncPayload.OPPONENT_PICK;
        int shownRound = Math.max(1, ClientBattle.round() + (choosing ? 1 : 0));
        String chip = limit > 0 && shownRound > limit ? "SUDDEN DEATH"
                : "ROUND " + shownRound + (limit > 0 ? " / " + limit : "");
        if (ClientBattle.bestOf() > 1) {
            int game = Math.min(ClientBattle.bestOf(), ClientBattle.myGames() + ClientBattle.oppGames() + 1);
            chip += "  ·  GAME " + game + "  ·  " + ClientBattle.myGames() + "-" + ClientBattle.oppGames();
        }
        int cw = font.width(chip) + 14;
        int cx = width / 2 - cw / 2;
        String brain = Difficulty.values()[Mth.clamp(ClientBattle.difficulty(), 0, 2)].label();
        String right;
        if (pvp) {
            right = "vs " + shorten(opp);
        } else if (ClientBattle.campaignMission() > 0) {
            // a mission keeps its own name on screen the whole way through
            right = opp + "  ·  " + brain;
        } else {
            right = "vs " + brain + " CPU";
        }
        // the sides give way to the chip rather than run under it
        int leftRoom = cx - 16;
        int rightRoom = width - (cx + cw) - 16;
        if (leftRoom >= font.width("MOB TRUMPS")) {
            g.drawString(font, "MOB TRUMPS", 10, 9, GOLD, true);
        }
        String r = fitWidth(right, rightRoom);
        g.drawString(font, r, width - 10 - font.width(r), 9, TEXT_DIM, false);
        g.fill(cx, 5, cx + cw, 20, 0xC0081E16);
        g.renderOutline(cx, 5, cw, 15, GOLD_DIM);
        g.drawString(font, chip, cx + 7, 9, GOLD, false);
    }

    /** What the centre of the dock does right now, or null when nothing can be pressed. */
    private String primaryLabel(int phase, boolean pvp) {
        if (pvp) {
            if (phase != BattleSyncPayload.FINISHED) {
                return null; // a duel is paced by the server
            }
            return rematchSentAt > 0 ? "Waiting..." : "Rematch";
        }
        return switch (phase) {
            case BattleSyncPayload.RESULT -> "Next  >";
            case BattleSyncPayload.FINISHED -> ClientBattle.campaignMission() > 0 ? "Try again" : "Play again";
            default -> null;
        };
    }

    /** Bottom dock: card size, emote, the primary action, auto-continue and leave. */
    private void drawDock(GuiGraphics g, BattleLayout.Layout L, int phase, boolean pvp, int mouseX, int mouseY,
                          long now, long elapsed, String opp) {
        BattleLayout.Rect d = L.dock();
        g.fill(0, d.y(), width, height, BAND);
        g.fill(0, d.y(), width, d.y() + 1, GOLD_DIM);

        BattleLayout.Rect s = L.sizeButton();
        String sizeLabel = s.w() >= 60 ? "Cards: " + ClientPrefs.cardSize().label : ClientPrefs.cardSize().label;
        smallButton(g, s, sizeLabel, mouseX, mouseY, 0xFF14352A, 0xFF20463A, TEXT_DIM, false);

        if (L.emoteButton() != null) {
            smallButton(g, L.emoteButton(), "Emote", mouseX, mouseY, 0xFF223A18, 0xFF3A5E2C, 0xFFFFFFFF, emoteOpen);
        }

        String label = primaryLabel(phase, pvp);
        BattleLayout.Rect p = L.primary();
        if (label != null) {
            boolean hover = p.contains(mouseX, mouseY);
            boolean waiting = pvp && rematchSentAt > 0;
            float pulse = ClientPrefs.reducedMotion() ? 0.5f : 0.5f + 0.5f * (float) Math.sin(now / 350.0);
            if (!waiting) {
                int glowA = (int) (0x30 + 0x28 * pulse) << 24;
                g.fill(p.x() - 3, p.y() - 3, p.right() + 3, p.bottom() + 3, glowA | 0x00E9C46A);
            }
            g.fill(p.x(), p.y(), p.right(), p.bottom(), waiting ? 0xFF24402F : hover ? 0xFF3BA85E : 0xFF2E7D46);
            // auto-continue drains along the button, so you can see the table move on
            if (!pvp && phase == BattleSyncPayload.RESULT && ClientPrefs.autoContinue()) {
                float t = Mth.clamp((elapsed - FLIP_MS) / (float) AUTO_MS, 0f, 1f);
                g.fill(p.x(), p.bottom() - 2, p.x() + Math.round(p.w() * t), p.bottom(), 0xC0E9C46A);
            }
            g.renderOutline(p.x(), p.y(), p.w(), p.h(), hover && !waiting ? GOLD : 0x66FFFFFF);
            String fitted = fitWidth(label, p.w() - 8);
            g.drawString(font, fitted, p.midX() - font.width(fitted) / 2, p.y() + 5,
                    waiting ? TEXT_DIM : 0xFFFFFFFF, !waiting);
        } else if (ClientPrefs.battleHints()) {
            // the hint lives in the primary's slot, which is free exactly when
            // there is no primary button — so text never sits on a border
            String hint = switch (phase) {
                case BattleSyncPayload.PLAYER_PICK -> "Pick a stat  ·  keys 1-6";
                case BattleSyncPayload.OPPONENT_PICK -> "Waiting for " + shorten(opp) + "...";
                case BattleSyncPayload.CPU_PICK -> "The CPU is choosing...";
                case BattleSyncPayload.RESULT -> "Next round coming...";
                case BattleSyncPayload.GAME_OVER -> "Next game dealing...";
                default -> "";
            };
            if (!hint.isEmpty()) {
                String fitted = fitWidth(hint, p.w());
                g.drawString(font, fitted, p.midX() - font.width(fitted) / 2, p.y() + 5, TEXT_DIM, false);
            }
        }

        if (L.autoButton() != null) {
            BattleLayout.Rect a = L.autoButton();
            boolean on = ClientPrefs.autoContinue();
            smallButton(g, a, "Auto", mouseX, mouseY, 0xFF14352A, 0xFF20463A, on ? 0xFFFFFFFF : TEXT_FAINT, false);
            // the light: lit while the table moves on by itself
            int lx = a.right() - 9;
            int ly = a.y() + 6;
            g.fill(lx, ly, lx + 5, ly + 6, 0xFF0A1A12);
            g.fill(lx + 1, ly + 1, lx + 4, ly + 5, on ? YOU_ACCENT : 0xFF3E4E46);
        }

        boolean live = live(phase) && ClientPrefs.confirmLeave();
        boolean armed = leaveArmedAt > 0 && now - leaveArmedAt < LEAVE_CONFIRM_MS;
        BattleLayout.Rect lv = L.leaveButton();
        boolean lHover = lv.contains(mouseX, mouseY);
        int base = armed && live ? 0xFF8A2020 : 0xFF5A2530;
        g.fill(lv.x(), lv.y(), lv.right(), lv.bottom(), lHover ? 0xFF7A3140 : base);
        g.renderOutline(lv.x(), lv.y(), lv.w(), lv.h(), armed && live ? 0xFFF0625A : EDGE);
        String leaveLabel = fitWidth((armed && live) ? "Forfeit?" : "Leave", lv.w() - 6);
        g.drawString(font, leaveLabel, lv.midX() - font.width(leaveLabel) / 2, lv.y() + 5, 0xFFFFFFFF, armed && live);
    }

    private void smallButton(GuiGraphics g, BattleLayout.Rect r, String label, int mouseX, int mouseY,
                             int base, int hoverCol, int textCol, boolean lit) {
        boolean hover = r.contains(mouseX, mouseY) || lit;
        g.fill(r.x(), r.y(), r.right(), r.bottom(), hover ? hoverCol : base);
        g.renderOutline(r.x(), r.y(), r.w(), r.h(), hover ? GOLD : EDGE);
        String fitted = fitWidth(label, r.w() - 10);
        g.drawString(font, fitted, r.x() + 5, r.y() + 5, textCol, false);
    }

    // --- pieces -------------------------------------------------------------

    /** A nameplate riding the top edge of a card: accent, name, count. */
    private void nameplate(GuiGraphics g, BattleLayout.Rect r, String name, int count, int accent, boolean active) {
        g.fill(r.x(), r.y(), r.right(), r.bottom(), 0xC0081E16);
        g.renderOutline(r.x(), r.y(), r.w(), r.h(), active ? accent : EDGE);
        g.fill(r.x(), r.y(), r.x() + 3, r.bottom(), accent);
        String c = "x" + count;
        int cx = r.right() - font.width(c) - 4;
        String n = fitWidth(name, cx - 10 - (r.x() + 6));
        g.drawString(font, n, r.x() + 6, r.y() + 3, active ? 0xFFFFFFFF : TEXT_DIM, active);
        g.fill(cx - 8, r.y() + 3, cx - 3, r.y() + 10, 0xFF7A5F3E);
        g.renderOutline(cx - 8, r.y() + 3, 5, 7, 0xFF5F4A32);
        g.drawString(font, c, cx, r.y() + 3, TEXT_DIM, false);
    }

    /** Spoils flying from the losing card to the winner's hand, or into the pot. */
    private void drawFlyingCards(GuiGraphics g, BattleLayout.Layout L, int winner, long sinceFlip) {
        if (sinceFlip > 700) {
            return;
        }
        int fromX;
        int toX;
        int toY;
        if (winner == 0) {
            fromX = L.oppCard().midX();
            toX = L.hands() ? L.myHand().midX() : L.myCard().midX();
            toY = L.hands() ? L.myHand().bottom() - 10 : L.myCard().midY();
        } else if (winner == 1) {
            fromX = L.myCard().midX();
            toX = L.hands() ? L.oppHand().midX() : L.oppCard().midX();
            toY = L.hands() ? L.oppHand().bottom() - 10 : L.oppCard().midY();
        } else {
            fromX = L.myCard().midX();
            toX = L.footer().x() + 12;
            toY = L.footer().y() + 6;
        }
        int fromY = L.myCard().midY();
        var pose = g.pose();
        for (int i = 0; i < 3; i++) {
            float p = Mth.clamp((sinceFlip - i * 80) / 450f, 0f, 1f);
            if (p <= 0f || p >= 1f) {
                continue;
            }
            float ease = easeOutCubic(p);
            float x = Mth.lerp(ease, fromX, toX);
            float y = Mth.lerp(ease, fromY, toY) - 22f * (float) Math.sin(p * Math.PI);
            pose.pushPose();
            pose.translate(x, y, 220);
            pose.mulPose(Axis.ZP.rotationDegrees(p * 360f + i * 40f));
            g.fill(-4, -5, 4, 5, 0xFFE9C46A);
            g.fill(-3, -4, 3, 4, 0xFF7A5F3E);
            pose.popPose();
        }
    }

    private void drawCardGlow(GuiGraphics g, int x, int y, int w, int h, int color) {
        if (color == 0) {
            return;
        }
        float pulse = ClientPrefs.reducedMotion() ? 1f
                : 0.6f + 0.4f * (float) Math.sin(System.currentTimeMillis() / 400.0);
        for (int i = 4; i >= 1; i--) {
            int s = i * 3;
            int a = (int) (0x30 * pulse * (1f - (i - 1) / 4f)) << 24;
            g.fill(x - s, y - s, x + w + s, y + h + s, (color & 0x00FFFFFF) | a);
        }
    }

    private void withPulse(GuiGraphics g, boolean active, long sincePulse, float cx, float cy, Runnable draw) {
        if (!active || sincePulse > 500 || ClientPrefs.reducedMotion()) {
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

    // --- the result panel ---------------------------------------------------

    /**
     * The end of a game: who took it, the story of it, and everything it paid.
     * Drawn on a fixed canvas and fitted to the space, so it reads the same at
     * every window size the layout sweep covers.
     */
    private void drawResultPanel(GuiGraphics g, BattleLayout.Layout L, int phase, long elapsed, boolean pvp,
                                 String opp) {
        BattleLayout.Rect felt = L.felt();
        int scrim = (int) (Mth.clamp(elapsed / 300f, 0f, 1f) * 0x90);
        g.fill(felt.x(), felt.y(), felt.right(), felt.bottom(), scrim << 24);

        BattleLayout.Rect p = L.panel();
        float fit = Math.min(p.w() / (float) PANEL_W, p.h() / (float) PANEL_H);
        float in = ClientPrefs.reducedMotion() ? 1f : easeOutBack(Mth.clamp(elapsed / (float) PANEL_IN_MS, 0f, 1f));
        float scale = fit * Math.max(0.05f, in);
        var pose = g.pose();
        pose.pushPose();
        pose.translate(p.midX(), p.midY(), 300);
        pose.scale(scale, scale, 1f);
        pose.translate(-PANEL_W / 2f, -PANEL_H / 2f, 0);

        boolean gameOver = phase == BattleSyncPayload.GAME_OVER;
        int winner = ClientBattle.winner();
        int accent = winner == 0 ? WIN_GOLD : winner == 1 ? OPP_ACCENT : TIE_GOLD;
        g.fill(3, 4, PANEL_W + 3, PANEL_H + 4, 0x66000000);
        g.fill(0, 0, PANEL_W, PANEL_H, 0xF0071812);
        g.renderOutline(0, 0, PANEL_W, PANEL_H, accent);
        g.renderOutline(2, 2, PANEL_W - 4, PANEL_H - 4, 0x55E9C46A);

        // --- the headline ---
        String them = pvp ? shorten(opp).toUpperCase(Locale.ROOT) : "CPU";
        String title;
        if (gameOver) {
            int game = ClientBattle.myGames() + ClientBattle.oppGames() + (winner == 2 ? 1 : 0);
            title = winner == 2 ? "GAME DRAWN" : "GAME " + Math.max(1, game) + (winner == 0 ? " · YOURS" : " · " + them);
        } else {
            title = winner == 0 ? "VICTORY!" : winner == 1 ? "DEFEAT" : "DRAW";
        }
        float ts = gameOver ? 1.6f : 2.2f;
        title = fitWidth(title, (int) ((PANEL_W - 16) / ts));
        pose.pushPose();
        pose.translate(PANEL_W / 2f, 9, 0);
        pose.scale(ts, ts, 1f);
        g.drawString(font, title, -font.width(title) / 2, 0, accent, true);
        pose.popPose();

        String sub;
        CampaignMission m = mission();
        int rounds = ClientBattle.roundsWon() + ClientBattle.roundsLost() + ClientBattle.roundsTied();
        if (m != null) {
            sub = "Mission " + m.index() + " · " + m.name();
        } else if (pvp && ClientBattle.bestOf() > 1) {
            sub = "vs " + shorten(opp) + " · best of " + ClientBattle.bestOf() + " · "
                    + ClientBattle.myGames() + "-" + ClientBattle.oppGames();
        } else if (pvp) {
            sub = "vs " + shorten(opp) + " · " + rounds + " rounds";
        } else {
            String brain = Difficulty.values()[Mth.clamp(ClientBattle.difficulty(), 0, 2)].label();
            sub = "vs " + brain + " CPU · " + rounds + " rounds";
        }
        sub = fitWidth(sub, PANEL_W - 16);
        int subY = 9 + Math.round(9 * ts) + 3;
        g.drawString(font, sub, PANEL_W / 2 - font.width(sub) / 2, subY, TEXT_DIM, false);

        // --- the story: the best card on the left, the numbers beside it ---
        int top = subY + 14;
        ClientBattle.Held mvp = ClientBattle.mvpHeld();
        int textX = 12;
        int mw = Math.round(BattleLayout.CARD_W * MVP_SCALE);
        int mh = Math.round(BattleLayout.CARD_H * MVP_SCALE);
        if (mvp != null) {
            // the frame here; the card itself goes on after this canvas, in
            // screen space, because its live mob is placed in screen space
            g.fill(11, top - 1, 13 + mw, top + mh + 1, accent);
            g.fill(12, top, 12 + mw, top + mh, 0xFF071812);
            textX = 12 + mw + 10;
        }
        int y = top + 2;
        y = statLine(g, textX, y, "Rounds", ClientBattle.roundsWon() + " won · " + ClientBattle.roundsLost()
                + " lost" + (ClientBattle.roundsTied() > 0 ? " · " + ClientBattle.roundsTied() + " tied" : ""));
        y = statLine(g, textX, y, "Best run", ClientBattle.bestStreak() == 0 ? "—"
                : ClientBattle.bestStreak() + (ClientBattle.bestStreak() == 1 ? " round" : " in a row"));
        if (mvp != null) {
            y = statLine(g, textX, y, "MVP", mvp.base().displayName() + " · " + ClientBattle.mvpWins()
                    + (ClientBattle.mvpWins() == 1 ? " round" : " rounds"));
        }
        if (pvp && ClientBattle.bestOf() > 1) {
            y = statLine(g, textX, y, "Series", ClientBattle.myGames() + " - " + ClientBattle.oppGames()
                    + "  (first to " + (ClientBattle.bestOf() / 2 + 1) + ")");
        }
        if (!pvp && m == null && !ClientBattle.counts()) {
            statLine(g, textX, y, "Practice", "no wins or awards");
        }

        // --- what it paid: chips along one row ---
        int chipY = PANEL_H - 46;
        g.fill(10, chipY - 5, PANEL_W - 10, chipY - 4, 0x40E9C46A);
        int cx = 12;
        if (ClientBattle.xp() > 0) {
            cx = rewardChip(g, cx, chipY, "+" + ClientBattle.xp() + " XP", XP_GREEN, true);
        }
        if (ClientBattle.emeralds() > 0) {
            cx = rewardChip(g, cx, chipY, "+" + ClientBattle.emeralds() + " emerald"
                    + (ClientBattle.emeralds() == 1 ? "" : "s"), EMERALD, false);
        }
        if (ClientBattle.rating() > 0) {
            int dlt = ClientBattle.ratingDelta();
            String r = "Rating " + ClientBattle.rating() + " (" + (dlt >= 0 ? "+" : "") + dlt + ")"
                    + (ClientBattle.rank().isEmpty() ? "" : " · " + ClientBattle.rank());
            rewardChip(g, cx, chipY, fitWidth(r, PANEL_W - 12 - cx - 10), dlt >= 0 ? GOLD : OPP_ACCENT, false);
        }

        // --- the notes: what the result meant ---
        String note = ClientBattle.note();
        if (ClientBattle.onTime()) {
            // the game went the distance: say how it was decided
            note = "Round " + ClientBattle.roundLimit() + " reached — most cards wins"
                    + (note.isEmpty() ? "" : " · " + note);
        }
        if (gameOver && note.isEmpty()) {
            note = "Next game dealing...";
        }
        note = fitWidth(note, PANEL_W - 20);
        String note2 = fitWidth(ClientBattle.note2(), PANEL_W - 20);
        g.drawString(font, note, PANEL_W / 2 - font.width(note) / 2, PANEL_H - 28, GOLD, false);
        g.drawString(font, note2, PANEL_W / 2 - font.width(note2) / 2, PANEL_H - 16, TEXT_DIM, false);
        pose.popPose();

        if (mvp != null) {
            // the canvas point (12, top) carried through the panel's transform
            int sx = Math.round(p.midX() + (12 - PANEL_W / 2f) * scale);
            int sy = Math.round(p.midY() + (top - PANEL_H / 2f) * scale);
            pose.pushPose();
            pose.translate(0, 0, 320);
            LivingEntity mob = CardRenderer.portraitEntity(minecraft, mvp.base(), entityCache);
            CardRenderer.renderCard(g, font, mvp.base(), mvp.level(), sx, sy, MVP_SCALE * scale,
                    0, 0, mob, mvp.level() > 0, false);
            pose.popPose();
        }
    }

    private int statLine(GuiGraphics g, int x, int y, String key, String value) {
        g.drawString(font, key.toUpperCase(Locale.ROOT), x, y, GOLD_DIM, false);
        String v = fitWidth(value, PANEL_W - 12 - (x + 58));
        g.drawString(font, v, x + 58, y, 0xFFFFFFFF, false);
        return y + 12;
    }

    /** A reward on the result panel; the XP one carries a little orb. */
    private int rewardChip(GuiGraphics g, int x, int y, String text, int color, boolean orb) {
        if (text.isEmpty()) {
            return x;
        }
        int w = font.width(text) + (orb ? 18 : 10);
        g.fill(x, y, x + w, y + 14, 0xC0000000);
        g.renderOutline(x, y, w, 14, color);
        int tx = x + 5;
        if (orb) {
            g.fill(x + 4, y + 3, x + 12, y + 11, 0xFF3E8E12);
            g.fill(x + 5, y + 4, x + 11, y + 10, XP_GREEN);
            g.fill(x + 6, y + 5, x + 8, y + 7, 0xFFF2FFB0);
            tx = x + 14;
        }
        g.drawString(font, text, tx, y + 3, color, false);
        return x + w + 6;
    }

    // =====================================================================
    // behaviour
    // =====================================================================

    /** Between rounds of a CPU game the table moves on by itself, once per round. */
    private void autoContinue(int phase, long elapsed) {
        if (ClientBattle.isPvp() || phase != BattleSyncPayload.RESULT || !ClientPrefs.autoContinue()
                || emoteOpen || elapsed < FLIP_MS + AUTO_MS) {
            return;
        }
        int ticket = BattleActionPayload.ticket(phase, ClientBattle.round());
        if (ticket != autoSentTicket) {
            autoSentTicket = ticket;
            PacketDistributor.sendToServer(BattleActionPayload.next(phase, ClientBattle.round()));
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || layout == null) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        BattleLayout.Layout L = layout;
        int phase = ClientBattle.phase();
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
        if (L.emoteButton() != null && L.emoteButton().contains(mouseX, mouseY)) {
            click();
            emoteOpen = true;
            return true;
        }
        if (L.sizeButton().contains(mouseX, mouseY)) {
            ClientPrefs.cycleCardSize();
            click();
            return true;
        }
        if (L.autoButton() != null && L.autoButton().contains(mouseX, mouseY)) {
            ClientPrefs.toggle("auto_continue");
            click();
            return true;
        }
        if (L.leaveButton().contains(mouseX, mouseY)) {
            leave();
            return true;
        }
        if (primaryLabel(phase, ClientBattle.isPvp()) != null && L.primary().contains(mouseX, mouseY)) {
            click();
            primaryAction(phase);
            return true;
        }
        if (phase == BattleSyncPayload.PLAYER_PICK) {
            int stat = hoveredStat((int) mouseX, (int) mouseY, phase);
            if (stat >= 0) {
                click();
                send(BattleActionPayload.PICK, stat);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** Leave — with a confirming second press while a game is live. */
    private void leave() {
        long now = System.currentTimeMillis();
        boolean live = live(ClientBattle.phase()) && ClientPrefs.confirmLeave();
        if (live && (leaveArmedAt < 0 || now - leaveArmedAt >= LEAVE_CONFIRM_MS)) {
            leaveArmedAt = now;
            click();
            return;
        }
        click();
        onClose();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        int phase = ClientBattle.phase();
        if (keyCode == 256) { // ESC
            if (emoteOpen) {
                emoteOpen = false;
            } else {
                // a live game asks first, exactly as the Leave button does
                leave();
            }
            return true;
        }
        if (keyCode == 32 || keyCode == 257 || keyCode == 335) { // SPACE / ENTER
            if (primaryLabel(phase, ClientBattle.isPvp()) != null) {
                primaryAction(phase);
            }
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
            if (phase == BattleSyncPayload.FINISHED && rematchSentAt < 0) {
                // the server pairs both players' requests
                rematchSentAt = System.currentTimeMillis();
                send(BattleActionPayload.REMATCH, 0);
            }
            return; // PvP rounds are paced by the server
        }
        switch (phase) {
            case BattleSyncPayload.RESULT -> {
                autoSentTicket = BattleActionPayload.ticket(phase, ClientBattle.round());
                PacketDistributor.sendToServer(BattleActionPayload.next(phase, ClientBattle.round()));
            }
            case BattleSyncPayload.FINISHED -> send(BattleActionPayload.PLAY_AGAIN, 0);
            default -> {
            }
        }
    }

    @Override
    public void tick() {
        super.tick();
        // a rematch offer goes stale once a new game is on the table
        if (ClientBattle.phase() != BattleSyncPayload.FINISHED) {
            rematchSentAt = -1;
        }
    }

    private void send(int action, int stat) {
        PacketDistributor.sendToServer(new BattleActionPayload(action, stat));
    }

    private void click() {
        if (minecraft != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f));
        }
    }

    // --- small helpers --------------------------------------------------------

    /** Trim text to fit a width, with an ellipsis when it has to be cut. */
    private String fitWidth(String text, int maxWidth) {
        if (text == null || text.isEmpty() || maxWidth <= 0) {
            return "";
        }
        if (font.width(text) <= maxWidth) {
            return text;
        }
        String cut = font.plainSubstrByWidth(text, Math.max(1, maxWidth - font.width("…")));
        return cut.isEmpty() ? "" : cut + "…";
    }

    private static String shorten(String s) {
        return s.length() > 12 ? s.substring(0, 12) : s;
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
