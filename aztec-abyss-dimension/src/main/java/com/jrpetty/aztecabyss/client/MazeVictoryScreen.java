package com.jrpetty.aztecabyss.client;

import com.jrpetty.aztecabyss.network.MazeVictoryPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * The ceremony: you got out, and it is written down.
 *
 * <p>Dying in the maze has a red screen, a forfeiture, dropped charts and -
 * on a real server - the door. Escaping had a teleport and a chat line, which
 * is exactly backwards for a mode whose whole point is getting out. This
 * screen is the other half of that weight: your run in numbers, and your line
 * in a hall that survives the game, the session and the restart.
 *
 * <p>Deliberately quiet chrome. A win screen that screams is a slot machine;
 * this one is a record being read out.
 */
public class MazeVictoryScreen extends AbyssScreen {

    private final MazeVictoryPayload prize;

    private static final int TEXT = UiKit.TEXT;
    private static final int TEXT_DIM = UiKit.TEXT_DIM;
    private static final int TEXT_FAINT = UiKit.TEXT_FAINT;
    private static final int GOLD = UiKit.GOLD;

    private static final int PANEL_W = 300;

    public MazeVictoryScreen(MazeVictoryPayload prize) {
        super(Component.literal("You Got Out"));
        this.prize = prize;
    }

    private int panelX() {
        return (this.width - PANEL_W) / 2;
    }

    /** Centred, with the button under it, never higher than the top margin. */
    private int panelTop() {
        return Math.max(24, (this.height - panelHeight() - 28) / 2);
    }

    private int panelHeight() {
        return 62 + statLines() * 11 + 14 + 12 + hallLines() * 12 + 30;
    }

    private int hallLines() {
        return Math.min(6, prize.hall().size());
    }

    /**
     * How many lines the run's own numbers take: days always, the rest only
     * when they are more than nothing. The hall used to sit at a fixed depth
     * sized for all five, leaving a hole in the panel on every short run.
     */
    private int statLines() {
        String stats = prize.stats();
        int n = 1;
        for (int i : new int[]{2, 3, 4, 5}) {
            if (MazeVictoryPayload.number(stats, i) > 0) {
                n++;
            }
        }
        return n;
    }

    private int hallTop() {
        return panelTop() + 62 + statLines() * 11 + 14;
    }

    private int panelBottom() {
        return panelTop() + panelHeight();
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.literal("Walk away"), b -> onClose())
                .bounds(panelX() + (PANEL_W - 120) / 2, panelBottom() + 8, 120, 20).build());
    }

    @Override
    protected int glow() {
        return 0x30FFC94A;
    }

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int x = panelX();
        int top = panelTop();
        int cx = this.width / 2;
        int bottom = panelBottom();

        UiKit.panel(g, x, top, PANEL_W, bottom - top);
        // A gold cap that breathes, once a second, gently. The one moving
        // thing on the screen, because this is the one screen that earned it.
        int glow = UiKit.pulse(GOLD, (float) (0.75 + 0.25 * Math.sin(age() / 9.0)));
        g.fill(x + 1, top + 1, x + PANEL_W - 1, top + 4, glow);

        String stats = prize.stats();
        String name = MazeVictoryPayload.field(stats, 0);
        int days = MazeVictoryPayload.number(stats, 1);
        int pct = MazeVictoryPayload.number(stats, 2);
        int kills = MazeVictoryPayload.number(stats, 3);
        int held = MazeVictoryPayload.number(stats, 4);
        int seconds = MazeVictoryPayload.number(stats, 5);
        int game = MazeVictoryPayload.number(stats, 6);

        g.drawCenteredString(this.font, "GAME " + game + " — THE MAZE", cx, top + 12, TEXT_FAINT);
        UiKit.big(g, this.font, Component.literal("YOU GOT OUT"), cx, top + 25, 2.0f, GOLD);
        g.drawCenteredString(this.font, name, cx, top + 46, TEXT);

        // The run, in the numbers that were actually the run.
        int y = top + 62;
        y = stat(g, cx, y, "survived", "§f" + days + (days == 1 ? " day" : " days"));
        if (pct > 0) {
            y = stat(g, cx, y, "charted", "§f" + pct + "%§7 of the maze");
        }
        if (kills > 0) {
            y = stat(g, cx, y, "killed", "§f" + kills + (kills == 1 ? " Griever" : " Grievers"));
        }
        if (held > 0) {
            y = stat(g, cx, y, "the wall held", "§f" + held + (held == 1 ? " raid" : " raids"));
        }
        if (seconds > 0) {
            y = stat(g, cx, y, "final run", "§f" + (seconds / 60) + "m " + (seconds % 60) + "s");
        }

        // The hall. The reason the screen exists: the line is permanent.
        y = hallTop();
        UiKit.fret(g, cx, y - 6, PANEL_W / 2 - 14, GOLD);
        g.drawCenteredString(this.font, Component.literal(
                "§6THE HALL OF THE OUT §7— " + prize.hallTotal()
                        + (prize.hallTotal() == 1 ? " escape, ever" : " escapes, ever")),
                cx, y, GOLD);
        y += 12;
        for (int i = 0; i < hallLines(); i++) {
            String line = prize.hall().get(i);
            String who = MazeVictoryPayload.field(line, 0);
            int d = MazeVictoryPayload.number(line, 1);
            int p = MazeVictoryPayload.number(line, 2);
            boolean you = i == 0 && who.equals(name);
            g.drawCenteredString(this.font, Component.literal(
                    (you ? "§f▸ " : "§7") + who + " §7— " + d
                            + (d == 1 ? " day" : " days") + ", " + p + "% charted"),
                    cx, y, you ? TEXT : TEXT_FAINT);
            y += 12;
        }

        g.drawCenteredString(this.font, "Your line is written. Nothing takes it off.",
                cx, bottom - 14, TEXT_FAINT);
    }

    private int stat(GuiGraphics g, int cx, int y, String label, String value) {
        g.drawCenteredString(this.font, Component.literal("§7" + label + " · " + value),
                cx, y, TEXT_DIM);
        return y + 11;
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(null);
    }
}
