package com.jrpetty.aztecabyss.client;

import com.jrpetty.aztecabyss.maze.MazeBuilder;
import com.jrpetty.aztecabyss.maze.MazeData;
import com.jrpetty.aztecabyss.network.MazeHubPayload;
import com.jrpetty.aztecabyss.network.MazeStatePayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Locale;

/**
 * The Glade's table: everything the maze knows about you and everything you
 * know about the maze, on two pages.
 *
 * <h2>Why a hub</h2>
 *
 * <p>The maze grew its UI the way settlements grow lanes: a screen here, a
 * command there, a chat print where neither fit. By the time it was finished
 * there were four screens and eleven commands and no front door. This is the
 * front door - M opens it, bare {@code /maze} opens it, and the screens that
 * already work (the trade sheet, the order slate) are reached from here rather
 * than rebuilt here.
 *
 * <h2>The chart page</h2>
 *
 * <p>The centrepiece, and the reason this screen exists. Runners have been
 * charting the maze since the charts system landed, and the game acknowledged
 * it as a <em>percentage</em> - a number, for a map. The chart page draws the
 * actual grid: every cell anybody has walked, tinted by the section colour
 * banded into that corridor's real walls, brighter where <em>you</em> have
 * personally been, gold where a Builder left a mark. The Glade square, the
 * four doors, and you - a pulsing blip - are drawn from the same pure grid
 * arithmetic the builder itself uses, so the picture cannot drift from the
 * place.
 *
 * <p>What it deliberately does not show: the exit. The chart is what the
 * Gladers <em>know</em>, and the way out is the one thing the maze never lets
 * anybody know for long - it moves every midnight. A map that marked the exit
 * would end the game's whole question.
 *
 * <p>The status page reads {@link ClientMazeState} live rather than the
 * snapshot this screen opened with, so the door clock keeps counting while the
 * screen is up. The chart is the delivered copy - it changes at walking pace,
 * and a re-open is a re-request.
 */
public class MazeHubScreen extends Screen {

    private enum Tab { STATUS, CHART }

    // The mod's ink palette.
    private static final int BG_TOP = 0xFF0B0A10;
    private static final int BG_BOTTOM = 0xFF060508;
    private static final int CARD_FILL = 0xFF14131C;
    private static final int CARD_EDGE = 0xFF2A2836;
    private static final int TEXT = 0xFFD8D5E4;
    private static final int TEXT_DIM = 0xFF7A7690;
    private static final int TEXT_FAINT = 0xFF4A4760;
    private static final int GOLD = 0xFFF0C75A;
    private static final int RED = 0xFFE05555;
    private static final int GREEN = 0xFF63D488;

    /**
     * The eight section hues, in {@link MazeBuilder#sectionOf} order, at full
     * strength - the "you walked this" tint. The Glade-only tint is the same
     * hue at just over half strength: shared knowledge is visible, your own
     * footsteps are vivid. Muted versions of the terracotta the builder bands
     * into the real walls, so the chart and the corridor agree about colour.
     */
    private static final int[] SECTION = {
            0xFFB05F52, 0xFFC07C48, 0xFFC4B058, 0xFF8BB054,
            0xFF58B0A4, 0xFF5C9CC4, 0xFF8662B0, 0xFFB062A0,
    };

    private final BitSet glade;
    private final BitSet mine;
    private final BitSet marks;
    private final List<String[]> roster = new ArrayList<>();

    private Tab tab = Tab.STATUS;
    private int age = 0;

    public MazeHubScreen(MazeHubPayload payload) {
        super(Component.literal("The Glade"));
        this.glade = BitSet.valueOf(payload.glade());
        this.mine = BitSet.valueOf(payload.mine());
        this.marks = BitSet.valueOf(payload.marks());
        for (String row : payload.roster()) {
            roster.add(row.split("\\|", -1));
        }
    }

    // ------------------------------------------------------------------
    // Widgets
    // ------------------------------------------------------------------

    @Override
    protected void init() {
        int cx = this.width / 2;

        // Tabs, drawn as the two halves of one pill.
        addRenderableWidget(tabButton(cx - 102, "Status", Tab.STATUS));
        addRenderableWidget(tabButton(cx + 2, "Chart", Tab.CHART));

        // The rest of the maze's UI, reached rather than rebuilt. Each runs the
        // command that has always opened it; the server answers with the screen.
        int y = this.height - 30;
        addRenderableWidget(Button.builder(Component.literal("Trade sheet"),
                b -> runAndClose("maze skills")).bounds(cx - 155, y, 96, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Order slate"),
                b -> runAndClose("maze order")).bounds(cx - 49, y, 96, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Close"),
                b -> onClose()).bounds(cx + 57, y, 96, 20).build());
    }

    private Button tabButton(int x, String label, Tab target) {
        Button b = Button.builder(Component.literal(label), btn -> {
            tab = target;
            this.clearWidgets();
            this.init();
        }).bounds(x, 34, 100, 20).build();
        b.active = tab != target;
        return b;
    }

    private void runAndClose(String command) {
        onClose();
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.connection.sendCommand(command);
        }
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

    // ------------------------------------------------------------------
    // Render
    // ------------------------------------------------------------------

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        age++;
        this.renderBackground(g, mouseX, mouseY, partialTick);
        int cx = this.width / 2;

        g.drawCenteredString(this.font, Component.literal("§8THE GLADE"), cx, 12, TEXT_FAINT);
        MazeStatePayload s = ClientMazeState.state();
        if (s != null) {
            String head = "DAY " + s.day() + "   ·   "
                    + (s.doorsOpen() ? "doors seal " : "dawn ")
                    + MazeHud.mmss(ClientMazeState.doorSeconds());
            g.drawCenteredString(this.font, Component.literal(head), cx, 22,
                    s.doorsOpen() ? TEXT : RED);
        }

        if (tab == Tab.STATUS) {
            renderStatus(g, s);
        } else {
            renderChart(g, s);
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    // ------------------------------------------------------------------
    // Status page
    // ------------------------------------------------------------------

    private void renderStatus(GuiGraphics g, MazeStatePayload s) {
        if (s == null) {
            g.drawCenteredString(this.font, Component.literal("§8waiting on the maze…"),
                    this.width / 2, this.height / 2, TEXT_FAINT);
            return;
        }
        int cx = this.width / 2;
        int top = 66;
        int colW = 150;
        int gap = 12;
        int leftX = cx - colW - gap / 2;
        int rightX = cx + gap / 2;

        // --- you ------------------------------------------------------
        int y = card(g, leftX, top, colW, 92, "YOU");
        if (s.job().isEmpty()) {
            g.drawString(this.font, "no trade sworn", leftX + 10, y, TEXT_DIM, false);
            g.drawString(this.font, "§8/maze job", leftX + 10, y + 11, TEXT_FAINT, false);
        } else {
            int accent = MazeHud.jobAccent(s.job());
            g.fill(leftX + 10, y + 1, leftX + 15, y + 6, accent);
            g.drawString(this.font, MazeHud.jobName(s.job()) + "  ·  lv " + s.jobLevel(),
                    leftX + 19, y, accent, false);
        }
        y += 15;
        if (s.changingSeconds() >= 0) {
            g.drawString(this.font, "CHANGING " + s.changingSeconds() + "s", leftX + 10, y,
                    MazeHud.pulse(RED, 0.5f + 0.5f * (float) Math.sin(age / 2.5)), true);
        } else if (s.stings() > 0) {
            StringBuilder pips = new StringBuilder();
            for (int i = 0; i < s.stingMax(); i++) {
                pips.append(i < s.stings() ? '◆' : '◇');
            }
            g.drawString(this.font, pips + "  stung " + s.stings() + "/" + s.stingMax(),
                    leftX + 10, y, RED, false);
        } else {
            g.drawString(this.font, "unstung", leftX + 10, y, TEXT_DIM, false);
        }
        y += 13;
        g.drawString(this.font, "chart  §f" + s.myPct() + "%§7 yours · §f"
                + s.gladePct() + "%§7 known", leftX + 10, y, TEXT_DIM, false);
        y += 13;
        if (s.carrying() > 0 || s.runSeconds() >= 0) {
            String line = (s.carrying() > 0 ? "▲" + s.carrying() + " carried" : "")
                    + (s.carrying() > 0 && s.runSeconds() >= 0 ? " · " : "")
                    + (s.runSeconds() >= 0 ? "run " + MazeHud.mmss(s.runSeconds()) : "");
            g.drawString(this.font, line, leftX + 10, y, TEXT, false);
        } else {
            g.drawString(this.font, "§8in the Glade", leftX + 10, y, TEXT_FAINT, false);
        }

        // --- the settlement --------------------------------------------
        y = card(g, leftX, top + 100, colW, 78, "THE GLADE'S LEDGER");
        g.drawString(this.font, "larder  §f" + s.larder(), leftX + 10, y, TEXT_DIM, false);
        y += 13;
        g.drawString(this.font, "ordered  §f" + s.orderCommitted()
                + "§7 · pot §f" + s.orderRemaining(), leftX + 10, y, TEXT_DIM, false);
        y += 13;
        g.drawString(this.font, "threat  §f"
                        + (s.threatX10() / 10) + "." + (s.threatX10() % 10) + "×",
                leftX + 10, y, s.threatX10() >= 20 ? RED : TEXT_DIM, false);
        y += 13;
        if (s.escapeSeconds() >= 0) {
            g.drawString(this.font, "WAY OUT OPEN " + MazeHud.mmss(s.escapeSeconds()),
                    leftX + 10, y, GOLD, true);
        } else if (s.raid()) {
            g.drawString(this.font, "THE WALL IS UNDER ATTACK", leftX + 10, y, RED, true);
        } else {
            g.drawString(this.font, "§8no alarms", leftX + 10, y, TEXT_FAINT, false);
        }

        // --- roster -----------------------------------------------------
        int rh = 30 + Math.max(1, roster.size()) * 13;
        y = card(g, rightX, top, colW, Math.min(rh, 178), "GLADERS · " + roster.size());
        int shown = 0;
        for (String[] row : roster) {
            if (shown >= 11) {
                g.drawString(this.font, "§8+" + (roster.size() - shown) + " more",
                        rightX + 10, y, TEXT_FAINT, false);
                break;
            }
            String name = row.length > 0 ? row[0] : "?";
            String job = row.length > 1 ? row[1] : "";
            String lv = row.length > 2 ? row[2] : "0";
            String pct = row.length > 3 ? row[3] : "0";
            g.drawString(this.font, name, rightX + 10, y, TEXT, false);
            String tail = strip(job) + " " + lv + " §8· " + pct + "%";
            g.drawString(this.font, "§7" + tail,
                    rightX + colW - 10 - this.font.width(strip(tail) + " "), y, TEXT_DIM, false);
            y += 13;
            shown++;
        }
    }

    /** Draws a card's chrome and title; returns the y where content starts. */
    private int card(GuiGraphics g, int x, int y, int w, int h, String title) {
        g.fill(x, y, x + w, y + h, CARD_FILL);
        g.fill(x, y, x + w, y + 1, CARD_EDGE);
        g.fill(x, y + h - 1, x + w, y + h, CARD_EDGE);
        g.fill(x, y, x + 1, y + h, CARD_EDGE);
        g.fill(x + w - 1, y, x + w, y + h, CARD_EDGE);
        g.drawString(this.font, title, x + 10, y + 8, TEXT_FAINT, false);
        return y + 22;
    }

    private static String strip(String s) {
        return s == null ? "" : s.replaceAll("§.", "");
    }

    // ------------------------------------------------------------------
    // Chart page
    // ------------------------------------------------------------------

    private void renderChart(GuiGraphics g, MazeStatePayload s) {
        int grid = MazeData.GRID;
        // Two pixels a cell when the window has the height for it, one when it
        // does not. A clipped map is worse than a small one.
        int px = this.height >= 380 ? 2 : 1;
        int side = grid * px;
        int left = this.width / 2 - side / 2;
        int top = 66;

        // Plate under the map, one cell of margin all round.
        g.fill(left - 6, top - 6, left + side + 6, top + side + 6, CARD_FILL);
        g.fill(left - 6, top - 6, left + side + 6, top - 5, CARD_EDGE);
        g.fill(left - 6, top + side + 5, left + side + 6, top + side + 6, CARD_EDGE);
        g.fill(left - 6, top - 6, left - 5, top + side + 6, CARD_EDGE);
        g.fill(left + side + 5, top - 6, left + side + 6, top + side + 6, CARD_EDGE);

        for (int cz = 0; cz < grid; cz++) {
            for (int cxCell = 0; cxCell < grid; cxCell++) {
                int i = cz * grid + cxCell;
                int colour;
                if (MazeData.inGlade(cxCell, cz)) {
                    colour = 0xFF1E2B1E;
                } else if (mine.get(i)) {
                    colour = SECTION[MazeBuilder.sectionOf(cxCell, cz)];
                } else if (glade.get(i)) {
                    colour = MazeHud.pulse(SECTION[MazeBuilder.sectionOf(cxCell, cz)], 0.45f);
                } else {
                    colour = 0xFF101018;
                }
                if (marks.get(i)) {
                    colour = GOLD;
                }
                int x0 = left + cxCell * px;
                int y0 = top + cz * px;
                g.fill(x0, y0, x0 + px, y0 + px, colour);
            }
        }

        // The four doors, from the builder's own table - green notches in the
        // Glade's rim, so "which door is which" is answerable from the map.
        for (int[] door : MazeBuilder.DOOR_CELLS) {
            int x0 = left + door[0] * px;
            int y0 = top + door[1] * px;
            g.fill(x0 - 1, y0 - 1, x0 + px + 1, y0 + px + 1, GREEN);
        }

        // You, breathing.
        if (minecraft != null && minecraft.player != null) {
            var at = minecraft.player.blockPosition();
            int pcx = Math.max(0, Math.min(grid - 1, at.getX() / MazeData.CELL));
            int pcz = Math.max(0, Math.min(grid - 1, at.getZ() / MazeData.CELL));
            int x0 = left + pcx * px;
            int y0 = top + pcz * px;
            int blip = MazeHud.pulse(0xFFFFFFFF, 0.6f + 0.4f * (float) Math.sin(age / 4.0));
            g.fill(x0 - 1, y0 - 1, x0 + px + 1, y0 + px + 1, blip);
        }

        // Compass. North is -Z, which is the top of this drawing already.
        g.drawCenteredString(this.font, "N", this.width / 2, top - 16, TEXT_DIM);
        g.drawCenteredString(this.font, "S", this.width / 2, top + side + 8, TEXT_DIM);
        g.drawString(this.font, "W", left - 18, top + side / 2 - 4, TEXT_DIM, false);
        g.drawString(this.font, "E", left + side + 12, top + side / 2 - 4, TEXT_DIM, false);

        // Legend, and the two percentages the grid is a picture of.
        String legend = "§7bright §fyours §8· §7dim §fknown §8· "
                + "§6◆ marked §8· §agreen §fdoors";
        g.drawCenteredString(this.font, Component.literal(legend),
                this.width / 2, top + side + 20, TEXT_DIM);
        if (s != null) {
            g.drawCenteredString(this.font, Component.literal(
                            "§8" + s.myPct() + "% walked by you · "
                                    + s.gladePct() + "% known to the Glade"),
                    this.width / 2, top + side + 32, TEXT_FAINT);
        }
        String hint = "§8The way out is never on the chart. That is the game.";
        g.drawCenteredString(this.font, Component.literal(hint),
                this.width / 2, top + side + 44, TEXT_FAINT);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // M closes what M opened.
        if (ClientSetup.MAZE_HUB.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
