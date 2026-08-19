package com.jrpetty.aztecabyss.client;

import com.jrpetty.aztecabyss.AztecAbyssConstants;
import com.jrpetty.aztecabyss.maze.MazeData;
import com.jrpetty.aztecabyss.network.MazeStatePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/**
 * The maze, on screen instead of in scrollback.
 *
 * <p>Until now the maze spoke through a one-line boss bar and twenty kinds of
 * chat print. The bar can hold a clock and nothing else; chat holds everything
 * and keeps none of it where you can look. So the facts a player steers by -
 * how long until the doors, what I am, how stung I am, what I am carrying -
 * had no fixed place on the screen, and the game was played half from memory.
 *
 * <h2>What goes where, and why</h2>
 *
 * <p><b>The panel</b> (top-left) is the steady state: day, the door clock, your
 * trade, your body, your load, the Glade's ledger. It is dense, quiet, and
 * always in the same place, because the whole value of an instrument is that
 * the eye learns where the needle lives. Rows that do not apply do not draw -
 * an unstung player has no sting row, not a zero in one.
 *
 * <p><b>Banners</b> (centre, upper third) are the exceptions: the way out is
 * open, the wall is breached, the doors are about to shut on you. A banner is
 * loud precisely because the panel never is - each earns attention by being
 * absent the rest of the time. The seal warning only shows while you are
 * actually <em>outside</em> the Glade, because a deadline you have already
 * beaten is noise.
 *
 * <p>The boss bar stays. It is the one line visible to somebody who has turned
 * this HUD off (H, the same toggle the Abyss uses), and removing it would take
 * information away from exactly the player who asked for less chrome.
 *
 * <p>Nothing here is computed. Every number is the server's, at most a second
 * old, via {@link ClientMazeState}; the only client-side arithmetic is ticking
 * the door clock smoothly between packets.
 */
@EventBusSubscriber(modid = AztecAbyssConstants.MOD_ID, value = Dist.CLIENT)
public final class MazeHud {

    // The mod's ink palette, same as every screen it draws.
    private static final int PANEL = 0xE60B0A10;
    private static final int EDGE = 0xFF2A2836;
    private static final int TEXT = 0xFFD8D5E4;
    private static final int TEXT_DIM = 0xFF7A7690;
    private static final int TEXT_FAINT = 0xFF4A4760;
    private static final int GREEN = 0xFF63D488;
    private static final int AMBER = 0xFFE0A040;
    private static final int RED = 0xFFE05555;
    private static final int GOLD = 0xFFF0C75A;
    private static final int BLUE = 0xFF4FD4E4;

    private static int age = 0;

    private MazeHud() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        age++;
        // The hub key answers only inside a live maze; anywhere else it is
        // somebody else's M.
        while (ClientSetup.MAZE_HUB.consumeClick()) {
            if (ClientMazeState.active() && Minecraft.getInstance().screen == null) {
                ClientMazeState.requestHub();
            }
        }
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || !ClientMazeState.active()) {
            return;
        }
        // Same toggle as the Abyss HUD: H means "less chrome", mod-wide.
        if (!ClientAbyssState.isHudVisible()) {
            return;
        }
        MazeStatePayload s = ClientMazeState.state();
        GuiGraphics g = event.getGuiGraphics();
        drawPanel(g, mc, s);
        drawBanners(g, mc, s);
    }

    // ------------------------------------------------------------------
    // The panel
    // ------------------------------------------------------------------

    private static void drawPanel(GuiGraphics g, Minecraft mc, MazeStatePayload s) {
        var font = mc.font;
        int x = 8;
        int y = 8;
        int w = 158;

        // Measure first: rows that do not apply take no space.
        boolean stung = s.stings() > 0 || s.changingSeconds() >= 0;
        boolean load = s.carrying() > 0 || s.runSeconds() >= 0;
        int rows = 3 + (stung ? 1 : 0) + (load ? 1 : 0) + 1;
        int h = 8 + rows * 11 + 4;

        g.fill(x, y, x + w, y + h, PANEL);
        g.fill(x, y, x + w, y + 1, EDGE);
        g.fill(x, y + h - 1, x + w, y + h, EDGE);
        g.fill(x + w - 1, y, x + w, y + h, EDGE);
        // The left edge is the door clock's colour - the panel's one glance-fact.
        int doorColour = doorColour(s);
        g.fill(x, y, x + 2, y + h, doorColour);

        int tx = x + 7;
        int ty = y + 5;

        // Day, and the threat multiplier at the right edge.
        g.drawString(font, "DAY " + s.day(), tx, ty, TEXT, true);
        String threat = "☠ " + (s.threatX10() / 10) + "." + (s.threatX10() % 10) + "×";
        g.drawString(font, threat, x + w - 6 - font.width(threat), ty,
                s.threatX10() >= 20 ? RED : TEXT_DIM, false);
        ty += 11;

        // The door clock. The one line somebody dies for misreading.
        int left = ClientMazeState.doorSeconds();
        String clockText;
        if (s.doorsOpen()) {
            clockText = "DOORS SEAL " + mmss(left);
        } else {
            clockText = "SEALED · DAWN " + mmss(left);
        }
        int clockColour = doorColour;
        if (s.doorsOpen() && left <= 30) {
            // The last thirty seconds breathe. Same trick as the skill screen's
            // unspent points: motion only where a decision is due.
            clockColour = pulse(RED, 0.55f + 0.45f * (float) Math.sin(age / 3.0));
        }
        g.drawString(font, clockText, tx, ty, clockColour, true);
        ty += 11;

        // Trade.
        if (s.job().isEmpty()) {
            g.drawString(font, "no trade · /maze job", tx, ty, TEXT_FAINT, false);
        } else {
            int accent = jobAccent(s.job());
            g.fill(tx, ty + 1, tx + 5, ty + 6, accent);
            g.drawString(font, jobName(s.job()) + " · lv " + s.jobLevel(),
                    tx + 9, ty, accent, false);
        }
        ty += 11;

        // The body, only when the body has news.
        if (stung) {
            if (s.changingSeconds() >= 0) {
                g.drawString(font, "CHANGING " + s.changingSeconds() + "s", tx, ty,
                        pulse(RED, 0.5f + 0.5f * (float) Math.sin(age / 2.5)), true);
            } else {
                StringBuilder pips = new StringBuilder();
                for (int i = 0; i < s.stingMax(); i++) {
                    pips.append(i < s.stings() ? '◆' : '◇');
                }
                g.drawString(font, pips.toString(), tx, ty, RED, false);
                g.drawString(font, " stung " + s.stings() + "/" + s.stingMax(),
                        tx + font.width(pips.toString()), ty, TEXT_DIM, false);
            }
            ty += 11;
        }

        // The load: what you carry, and the run clock if one is going.
        if (load) {
            StringBuilder line = new StringBuilder();
            if (s.carrying() > 0) {
                line.append('▲').append(s.carrying()).append(" carried");
            }
            if (s.runSeconds() >= 0) {
                if (!line.isEmpty()) {
                    line.append(" · ");
                }
                line.append("run ").append(mmss(s.runSeconds()));
            }
            g.drawString(font, line.toString(), tx, ty, BLUE, false);
            ty += 11;
        }

        // The settlement's ledger, and the way in to the rest of the UI.
        g.drawString(font, "larder " + s.larder() + " · chart " + s.gladePct() + "%",
                tx, ty, TEXT_FAINT, false);
        String hint = "[M]";
        g.drawString(font, hint, x + w - 6 - font.width(hint), ty, TEXT_FAINT, false);
    }

    // ------------------------------------------------------------------
    // Banners
    // ------------------------------------------------------------------

    private static void drawBanners(GuiGraphics g, Minecraft mc, MazeStatePayload s) {
        int cx = g.guiWidth() / 2;
        int y = g.guiHeight() / 5;

        // The way out beats everything else on the screen.
        if (s.escapeSeconds() >= 0) {
            int shown = Math.max(0, s.escapeSeconds() - ClientMazeState.ticksSince() / 20);
            banner(g, mc, cx, y, "THE WAY OUT IS OPEN — " + mmss(shown),
                    pulse(GOLD, 0.7f + 0.3f * (float) Math.sin(age / 5.0)));
            y += 24;
        }
        if (s.raid()) {
            banner(g, mc, cx, y, "THE WALL IS UNDER ATTACK", RED);
            y += 24;
        }
        // The seal warning, only while it is still your problem: doors open,
        // under a minute left, and you stood outside the Glade.
        int left = ClientMazeState.doorSeconds();
        if (s.doorsOpen() && left <= 60 && outsideGlade(mc)) {
            banner(g, mc, cx, y, "GET INSIDE — " + mmss(left),
                    pulse(RED, 0.5f + 0.5f * (float) Math.sin(age / 3.0)));
        }
    }

    /** One centred, double-size line on a dark plate. */
    private static void banner(GuiGraphics g, Minecraft mc, int cx, int y, String text, int colour) {
        var font = mc.font;
        int w = font.width(text) * 2;
        g.fill(cx - w / 2 - 8, y - 4, cx + w / 2 + 8, y + 20, PANEL);
        g.pose().pushPose();
        g.pose().translate(cx, y, 0);
        g.pose().scale(2.0f, 2.0f, 1.0f);
        g.drawCenteredString(font, text, 0, 0, colour);
        g.pose().popPose();
    }

    // ------------------------------------------------------------------
    // Small helpers
    // ------------------------------------------------------------------

    private static boolean outsideGlade(Minecraft mc) {
        var at = mc.player.blockPosition();
        return !MazeData.inGlade(at.getX() / MazeData.CELL, at.getZ() / MazeData.CELL);
    }

    private static int doorColour(MazeStatePayload s) {
        if (!s.doorsOpen()) {
            return RED;
        }
        // Amber once the warning window opens; green while the day is yours.
        return ClientMazeState.doorSeconds() <= 120 ? AMBER : GREEN;
    }

    static String mmss(int seconds) {
        return (seconds / 60) + ":" + String.format("%02d", seconds % 60);
    }

    /** The trade colours every screen in the mod already uses. */
    static int jobAccent(String job) {
        return switch (job) {
            case "runner" -> 0xFF4FD4E4;
            case "builder" -> 0xFFE0A040;
            case "medjack" -> 0xFF63D488;
            case "trackhoe" -> 0xFF3E9E4E;
            default -> 0xFFBFBBD0;
        };
    }

    static String jobName(String job) {
        return switch (job) {
            case "runner" -> "Runner";
            case "builder" -> "Builder";
            case "medjack" -> "Med-jack";
            case "trackhoe" -> "Track-hoe";
            default -> job;
        };
    }

    /** A colour scaled toward black by {@code t}, alpha forced opaque. */
    static int pulse(int colour, float t) {
        int r = (int) (((colour >> 16) & 0xFF) * t);
        int gr = (int) (((colour >> 8) & 0xFF) * t);
        int b = (int) ((colour & 0xFF) * t);
        return 0xFF000000 | (r << 16) | (gr << 8) | b;
    }
}
