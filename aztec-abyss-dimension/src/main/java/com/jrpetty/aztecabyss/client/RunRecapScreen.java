package com.jrpetty.aztecabyss.client;

import com.jrpetty.aztecabyss.network.RunRecapPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The end-of-run scoreboard. Opens the moment a run resolves - on death, on a
 * successful extraction, or on clearing the final round - and lays out
 * everything the run earned: round reached, kills, headshots, revives, survival
 * time, lifetime deaths, and whether the hidden ritual was solved.
 *
 * The player can sit on it and read, or hit the button to drop back into the
 * overworld. It closes itself after {@link #MAX_TICKS} either way, so nobody is
 * ever stuck staring at a scoreboard.
 */
public final class RunRecapScreen extends Screen {

    /** Hard cap the scoreboard stays up: one minute. */
    private static final int MAX_TICKS = 1200;

    private final RunRecapPayload data;
    private int ticks;

    public RunRecapScreen(RunRecapPayload data) {
        super(Component.literal(data.victory() ? "The Abyss is Silent"
                : data.extracted() ? "You Escaped" : "You Fell"));
        this.data = data;
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.literal("Return to the Overworld"), b -> onClose())
                .bounds(this.width / 2 - 90, this.height - 40, 180, 20)
                .build());
    }

    @Override
    public void tick() {
        super.tick();
        if (++ticks >= MAX_TICKS) {
            onClose();
        }
    }

    /**
     * A flat dark wash instead of vanilla's blurred backdrop - the blur made the
     * scoreboard text read as smeared.
     */
    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, this.width, this.height, 0xE0080608);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g, mouseX, mouseY, partialTick);

        int cx = this.width / 2;
        int y = 24;

        int titleColor = data.victory() ? 0xFFFFD24A : data.extracted() ? 0xFF4AC0FF : 0xFFD03030;
        String title = data.victory() ? "THE ABYSS IS SILENT" : data.extracted() ? "YOU ESCAPED" : "YOU FELL";
        g.drawCenteredString(this.font, Component.literal(title).withStyle(s -> s.withBold(true)), cx, y, titleColor);
        y += 13;
        g.drawCenteredString(this.font, Component.literal(data.multiplayer() ? "Co-op run" : "Solo run"), cx, y, 0xFF888888);
        y += 20;

        // Headline round number.
        g.drawCenteredString(this.font,
                Component.literal(data.extracted() ? "EXTRACTED AT ROUND" : "REACHED ROUND"), cx, y, 0xFFAAAAAA);
        y += 11;
        g.pose().pushPose();
        g.pose().translate(cx, y, 0);
        g.pose().scale(3.0f, 3.0f, 1.0f);
        g.drawCenteredString(this.font, Component.literal(String.valueOf(data.round())), 0, 0, 0xFFFF4040);
        g.pose().popPose();
        y += 38;

        // Scoreboard panel.
        int panelW = 220;
        int left = cx - panelW / 2;
        int rows = data.multiplayer() ? 6 : 5;
        int panelH = rows * 13 + 10;
        g.fill(left, y - 5, left + panelW, y + panelH - 5, 0x99000000);
        g.fill(left, y - 5, left + panelW, y - 4, 0xC0B8860B);
        g.fill(left, y + panelH - 6, left + panelW, y + panelH - 5, 0xC0B8860B);

        drawStat(g, left, y, "Survived", fmtTime(data.survivalSeconds()), 0xFFFFFFFF);
        y += 13;
        drawStat(g, left, y, "Kills", String.valueOf(data.kills()), 0xFF6ED36E);
        y += 13;
        drawStat(g, left, y, "Headshots", String.valueOf(data.headshots()), 0xFFFFC04A);
        y += 13;
        if (data.multiplayer()) {
            drawStat(g, left, y, "Revives", String.valueOf(data.revives()), 0xFF6EC8FF);
            y += 13;
        }
        drawStat(g, left, y, "Total deaths", String.valueOf(data.deaths()), 0xFFD07070);
        y += 13;
        drawStat(g, left, y, "Hidden ritual",
                data.ritualComplete() ? "SOLVED" : "unsolved",
                data.ritualComplete() ? 0xFFD98CFF : 0xFF777777);
        y += 20;

        // Best-ever comparison.
        if (data.round() > data.previousBest()) {
            g.drawCenteredString(this.font, Component.literal("★ NEW PERSONAL BEST ★").withStyle(s -> s.withBold(true)),
                    cx, y, 0xFF55FF55);
        } else {
            g.drawCenteredString(this.font, Component.literal("Personal best: round " + data.previousBest()),
                    cx, y, 0xFF888888);
        }

        // Countdown so the auto-close is never a surprise.
        int secondsLeft = Math.max(0, (MAX_TICKS - ticks) / 20);
        g.drawCenteredString(this.font,
                Component.literal("Closing in " + secondsLeft + "s"), cx, this.height - 54, 0xFF666666);

        super.render(g, mouseX, mouseY, partialTick);
    }

    private void drawStat(GuiGraphics g, int left, int y, String label, String value, int valueColor) {
        g.drawString(this.font, Component.literal(label), left + 12, y, 0xFFAAAAAA, false);
        int vw = this.font.width(value);
        g.drawString(this.font, Component.literal(value), left + 208 - vw, y, valueColor, false);
    }

    private static String fmtTime(int seconds) {
        return (seconds / 60) + "m " + (seconds % 60) + "s";
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
