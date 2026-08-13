package com.jrpetty.aztecabyss.client;

import com.jrpetty.aztecabyss.network.MazeVictoryPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
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
public class MazeVictoryScreen extends Screen {

    private final MazeVictoryPayload prize;
    private int age = 0;

    private static final int BG_TOP = 0xFF0B0A10;
    private static final int BG_BOTTOM = 0xFF060508;
    private static final int PANEL_FILL = 0xFF14131C;
    private static final int PANEL_EDGE = 0xFF2A2836;
    private static final int TEXT = 0xFFD8D5E4;
    private static final int TEXT_DIM = 0xFF7A7690;
    private static final int TEXT_FAINT = 0xFF4A4760;
    private static final int GOLD = 0xFFFFC94A;

    private static final int PANEL_W = 300;

    public MazeVictoryScreen(MazeVictoryPayload prize) {
        super(Component.literal("You Got Out"));
        this.prize = prize;
    }

    private int panelX() {
        return (this.width - PANEL_W) / 2;
    }

    private int panelTop() {
        return Math.max(24, this.height / 2 - 108);
    }

    private int hallLines() {
        return Math.min(6, prize.hall().size());
    }

    private int panelBottom() {
        return panelTop() + 118 + hallLines() * 12 + 34;
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.literal("Walk away"), b -> onClose())
                .bounds(panelX() + (PANEL_W - 120) / 2, panelBottom() + 8, 120, 20).build());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fillGradient(0, 0, this.width, this.height, BG_TOP, BG_BOTTOM);
    }

    /** No blur under type. Same call every screen in this mod makes. */
    @Override
    protected void renderBlurredBackground(float partialTick) {
        // Intentionally empty.
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        age++;
        this.renderBackground(g, mouseX, mouseY, partialTick);

        int x = panelX();
        int top = panelTop();
        int cx = this.width / 2;
        int bottom = panelBottom();

        g.fill(x, top, x + PANEL_W, bottom, PANEL_FILL);
        g.fill(x, top, x + PANEL_W, top + 1, PANEL_EDGE);
        g.fill(x, bottom - 1, x + PANEL_W, bottom, PANEL_EDGE);
        g.fill(x, top, x + 1, bottom, PANEL_EDGE);
        g.fill(x + PANEL_W - 1, top, x + PANEL_W, bottom, PANEL_EDGE);
        // A gold cap that breathes, once a second, gently. The one moving
        // thing on the screen, because this is the one screen that earned it.
        int glow = 0xFF000000 | pulseRgb(GOLD, (float) (0.75 + 0.25 * Math.sin(age / 9.0)));
        g.fill(x + 1, top + 1, x + PANEL_W - 1, top + 4, glow);

        String stats = prize.stats();
        String name = MazeVictoryPayload.field(stats, 0);
        int days = MazeVictoryPayload.number(stats, 1);
        int pct = MazeVictoryPayload.number(stats, 2);
        int kills = MazeVictoryPayload.number(stats, 3);
        int held = MazeVictoryPayload.number(stats, 4);
        int seconds = MazeVictoryPayload.number(stats, 5);
        int game = MazeVictoryPayload.number(stats, 6);

        g.drawCenteredString(this.font, Component.literal("§8GAME " + game + " — THE MAZE"),
                cx, top + 12, TEXT_FAINT);
        g.pose().pushPose();
        g.pose().translate(cx, top + 26, 0);
        g.pose().scale(2.0f, 2.0f, 1.0f);
        g.drawCenteredString(this.font, Component.literal("YOU GOT OUT"), 0, 0, GOLD);
        g.pose().popPose();
        g.drawCenteredString(this.font, Component.literal("§7" + name), cx, top + 46, TEXT);

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
        y = top + 118;
        g.fill(x + 12, y - 4, x + PANEL_W - 12, y - 3, PANEL_EDGE);
        g.drawCenteredString(this.font, Component.literal(
                "§6THE HALL OF THE OUT §8— " + prize.hallTotal()
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
                    (you ? "§f▸ " : "§8") + who + " §8— " + d
                            + (d == 1 ? " day" : " days") + ", " + p + "% charted"),
                    cx, y, you ? TEXT : TEXT_FAINT);
            y += 12;
        }

        g.drawCenteredString(this.font, Component.literal(
                "§8Your line is written. Nothing takes it off."), cx, bottom - 14, TEXT_FAINT);

        super.render(g, mouseX, mouseY, partialTick);
    }

    private int stat(GuiGraphics g, int cx, int y, String label, String value) {
        g.drawCenteredString(this.font, Component.literal("§7" + label + " §8· " + value),
                cx, y, TEXT_DIM);
        return y + 11;
    }

    /** Lerps a colour toward black, for the pulse. Kept off the alpha channel. */
    private static int pulseRgb(int argb, float k) {
        int r = (int) (((argb >> 16) & 0xFF) * k);
        int gr = (int) (((argb >> 8) & 0xFF) * k);
        int b = (int) ((argb & 0xFF) * k);
        return (r << 16) | (gr << 8) | b;
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(null);
    }
}
