package com.jrpetty.aztecabyss.client;

import com.jrpetty.aztecabyss.network.RunRecapPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The end-of-run recap: a styled summary of the run just finished - round
 * reached, kills, revives, survival time, and a comparison against the player's
 * previous best - so every run feels like it counted.
 */
public final class RunRecapScreen extends Screen {

    private final RunRecapPayload data;

    public RunRecapScreen(RunRecapPayload data) {
        super(Component.literal(data.victory() ? "The Abyss is Silent"
                : data.extracted() ? "You Escaped" : "You Fell"));
        this.data = data;
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.literal("Continue"), b -> onClose())
                .bounds(this.width / 2 - 60, this.height - 48, 120, 20)
                .build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g, mouseX, mouseY, partialTick);

        int cx = this.width / 2;
        int y = this.height / 4;

        // Title.
        int titleColor = data.victory() ? 0xFFFFD24A : data.extracted() ? 0xFF4AC0FF : 0xFFD03030;
        String title = data.victory() ? "THE ABYSS IS SILENT" : data.extracted() ? "YOU ESCAPED" : "YOU FELL";
        g.drawCenteredString(this.font, Component.literal(title).withStyle(s -> s.withBold(true)), cx, y, titleColor);
        y += 14;
        g.drawCenteredString(this.font, Component.literal(data.multiplayer() ? "Co-op run" : "Solo run"), cx, y, 0xFF888888);
        y += 24;

        // Big round number.
        g.drawCenteredString(this.font, Component.literal(data.extracted() ? "EXTRACTED AT ROUND" : "REACHED ROUND"), cx, y, 0xFFAAAAAA);
        y += 12;
        g.pose().pushPose();
        g.pose().translate(cx, y, 0);
        g.pose().scale(3.0f, 3.0f, 1.0f);
        g.drawCenteredString(this.font, Component.literal(String.valueOf(data.round())), 0, 0, 0xFFFF4040);
        g.pose().popPose();
        y += 40;

        // Stat lines.
        drawStat(g, cx, y, "Survived", fmtTime(data.survivalSeconds()));
        y += 14;
        drawStat(g, cx, y, "Kills", String.valueOf(data.kills()));
        y += 14;
        if (data.multiplayer()) {
            drawStat(g, cx, y, "Revives", String.valueOf(data.revives()));
            y += 14;
        }

        // Best-ever comparison.
        y += 8;
        if (data.round() > data.previousBest()) {
            g.drawCenteredString(this.font, Component.literal("★ NEW PERSONAL BEST ★").withStyle(s -> s.withBold(true)),
                    cx, y, 0xFF55FF55);
        } else {
            g.drawCenteredString(this.font, Component.literal("Personal best: round " + data.previousBest()),
                    cx, y, 0xFF888888);
        }

        super.render(g, mouseX, mouseY, partialTick);
    }

    private void drawStat(GuiGraphics g, int cx, int y, String label, String value) {
        g.drawString(this.font, Component.literal("§7" + label + ":"), cx - 70, y, 0xFFAAAAAA, false);
        g.drawString(this.font, Component.literal("§f" + value), cx + 20, y, 0xFFFFFFFF, false);
    }

    private static String fmtTime(int seconds) {
        return (seconds / 60) + "m " + (seconds % 60) + "s";
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
