package com.jrpetty.aztecabyss.client;

import com.jrpetty.aztecabyss.network.RunRecapPayload;
import com.jrpetty.aztecabyss.worldgen.ArenaMap;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The end-of-run scoreboard. Opens the moment a run resolves - on death, on a
 * successful extraction, or on clearing the final round - and lays out
 * everything the run earned: the round reached, kills, headshots, revives,
 * survival time, lifetime deaths, and whether the hidden ritual was solved.
 *
 * <p>The player can sit on it and read, or press the button to drop back into
 * the overworld. It closes itself after {@link #MAX_TICKS} either way, so
 * nobody is ever stuck staring at a scoreboard, and the countdown is on the
 * button so the auto-close is never a surprise.
 */
public final class RunRecapScreen extends AbyssScreen {

    /** Hard cap the scoreboard stays up: one minute. */
    private static final int MAX_TICKS = 1200;

    /** One line of the sheet. */
    private record Stat(String label, String value, int colour) {
    }

    private final RunRecapPayload data;
    private Button leave;
    private int ticks;

    public RunRecapScreen(RunRecapPayload data) {
        super(Component.literal(data.victory() ? "The Abyss is Silent"
                : data.extracted() ? "You Escaped" : "You Fell"));
        this.data = data;
    }

    private int accent() {
        return data.victory() ? UiKit.GOLD : data.extracted() ? UiKit.BLUE : UiKit.RED;
    }

    @Override
    protected int glow() {
        return UiKit.alpha(accent(), 0x26);
    }

    @Override
    protected void init() {
        leave = Button.builder(leaveLabel(), b -> onClose())
                .bounds(this.width / 2 - 100, this.height - 32, 200, 20)
                .build();
        addRenderableWidget(leave);
    }

    private Component leaveLabel() {
        int secondsLeft = Math.max(0, (MAX_TICKS - ticks) / 20);
        return Component.literal("Return to the Overworld  (" + secondsLeft + "s)");
    }

    @Override
    public void tick() {
        super.tick();
        if (++ticks >= MAX_TICKS) {
            onClose();
            return;
        }
        if (leave != null && ticks % 20 == 0) {
            leave.setMessage(leaveLabel());
        }
    }

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int cx = this.width / 2;
        int accent = accent();

        String where = data.mapOrdinal() >= 0
                ? ArenaMap.byId(data.mapOrdinal()).title().toUpperCase(Locale.ROOT)
                : "THE AZTEC ABYSS";
        String eyebrow = where + " · " + (data.multiplayer() ? "CO-OP RUN" : "SOLO RUN");
        String title = data.victory() ? "THE ABYSS IS SILENT" : data.extracted() ? "YOU ESCAPED" : "YOU FELL";
        // The sheet is about 150 high; centred in the room above the button
        // rather than pinned to the top of a tall window.
        int y = UiKit.masthead(g, this.font, eyebrow, title, cx,
                Math.max(10, (this.height - 40 - 152) / 2), accent);
        UiKit.fret(g, cx, y + 1, 130, accent);

        // The headline: the round, big, because it is the number the run is.
        y += 12;
        g.drawCenteredString(this.font, data.extracted() ? "EXTRACTED AT ROUND" : "REACHED ROUND",
                cx, y, UiKit.TEXT_FAINT);
        UiKit.big(g, this.font, Component.literal(String.valueOf(data.round())), cx, y + 11, 3.0f, accent);
        y += 40;

        boolean best = data.round() > data.previousBest();
        if (best) {
            g.drawCenteredString(this.font,
                    Component.literal("★ NEW PERSONAL BEST ★").withStyle(s -> s.withBold(true)),
                    cx, y, UiKit.GREEN);
        } else {
            g.drawCenteredString(this.font, "Personal best: round " + data.previousBest(),
                    cx, y, UiKit.TEXT_DIM);
        }
        y += 16;

        // The rest of the run, as a two-column sheet.
        List<Stat> stats = new ArrayList<>();
        stats.add(new Stat("Survived", fmtTime(data.survivalSeconds()), UiKit.TEXT));
        stats.add(new Stat("Kills", String.valueOf(data.kills()), UiKit.GREEN));
        stats.add(new Stat("Headshots", String.valueOf(data.headshots()), UiKit.GOLD));
        if (data.multiplayer()) {
            stats.add(new Stat("Revives", String.valueOf(data.revives()), UiKit.BLUE));
        }
        stats.add(new Stat("Lifetime deaths", String.valueOf(data.deaths()), 0xFFE08A8A));
        stats.add(new Stat("Hidden ritual", data.ritualComplete() ? "SOLVED" : "unsolved",
                data.ritualComplete() ? UiKit.PURPLE : UiKit.TEXT_FAINT));

        int panelW = Math.min(300, this.width - 32);
        int colW = (panelW - 24) / 2;
        int rows = (stats.size() + 1) / 2;
        int panelH = rows * 14 + 12;
        int left = cx - panelW / 2;
        UiKit.panel(g, left, y, panelW, panelH);
        g.fill(left + 1, y + 1, left + panelW - 1, y + 3, UiKit.alpha(accent, 0xB0));
        for (int i = 0; i < stats.size(); i++) {
            int col = i / rows;
            int row = i % rows;
            int x = left + 10 + col * (colW + 4);
            int ry = y + 9 + row * 14;
            Stat s = stats.get(i);
            UiKit.row(g, this.font, s.label(), s.value(), x, x + colW - 6, ry, UiKit.TEXT_FAINT, s.colour());
        }
        if (stats.size() > rows) {
            g.fill(cx, y + 7, cx + 1, y + panelH - 7, UiKit.alpha(UiKit.EDGE_HOT, 0x80));
        }
    }

    private static String fmtTime(int seconds) {
        return (seconds / 60) + "m " + (seconds % 60) + "s";
    }
}
