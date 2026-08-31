package com.jrpetty.aztecabyss.client;

import com.jrpetty.aztecabyss.network.LeaderboardPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Records, for every map.
 *
 * <p>Reached from the portal rather than a command, because a leaderboard nobody
 * looks at is not one. The portal screen is the only place everybody goes before
 * every run, and it is exactly the moment somebody cares what the record is.
 *
 * <p>Two boards per map, side by side, because solo and group are not comparable.
 * Four people reaching round thirty on ground one person could not hold past
 * twelve is not the better result, it is a different result - and a single table
 * would quietly make the solo column look like everyone was bad at the game.
 *
 * <h2>Two tabs, because there are two questions</h2>
 *
 * <p>Records answers "who is best", which is about other people. <b>Your runs</b>
 * answers "how did I do", which is the question somebody standing at the portal
 * is actually asking - and the one the game had no answer to at all. Almost
 * nobody tops a board; nearly every run is a defeat, and the interesting part of
 * a defeat is the detail. Which day it got you. How much you had charted when it
 * did. Whether you were the one who turned.
 *
 * <p>So the history is not a list of scores. Each run is a card with its outcome
 * written in the colour of what happened - gold for getting out, red for being
 * taken, and a deeper red for the Changing, which is its own ending and deserves
 * to look like one.
 */
public class LeaderboardScreen extends Screen {

    private final Screen parent;
    /** Map key to display name, in the order the server sent them. */
    private final Map<String, String> maps = new LinkedHashMap<>();
    /** Board key ({@code map#solo}) to its rows, already in order. */
    private final Map<String, List<String>> boards = new LinkedHashMap<>();
    private final List<String> mapKeys = new ArrayList<>();
    /** This player's own runs, newest first, as the server packed them. */
    private final List<String> runs = new ArrayList<>();

    private int page = 0;
    private boolean historyTab = false;
    private int scroll = 0;

    // One ink set, shared with every other screen in the mod.
    private static final int GOLD = 0xFFFFD24A;
    private static final int TEXT = 0xFFE4E1EE;
    private static final int DIM = 0xFF8A8698;
    private static final int FAINT = 0xFF56526A;
    private static final int CARD = 0xFF14131C;
    private static final int EDGE = 0xFF2A2836;
    private static final int RED = 0xFFE0554F;
    private static final int DEEP_RED = 0xFF9B2F2A;

    public LeaderboardScreen(Screen parent, LeaderboardPayload payload) {
        super(Component.literal("Records"));
        this.parent = parent;
        for (String label : payload.labels()) {
            String key = LeaderboardPayload.field(label, 0);
            maps.put(key, LeaderboardPayload.field(label, 1));
            mapKeys.add(key);
        }
        for (String row : payload.rows()) {
            boards.computeIfAbsent(LeaderboardPayload.field(row, 0), k -> new ArrayList<>()).add(row);
        }
        runs.addAll(payload.runs());
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        Button records = Button.builder(Component.literal("Records"), b -> {
            historyTab = false;
            scroll = 0;
            rebuild();
        }).bounds(cx - 152, 44, 150, 20).build();
        records.active = historyTab;
        addRenderableWidget(records);

        Button mine = Button.builder(Component.literal("Your runs"), b -> {
            historyTab = true;
            scroll = 0;
            rebuild();
        }).bounds(cx + 2, 44, 150, 20).build();
        mine.active = !historyTab;
        addRenderableWidget(mine);

        addRenderableWidget(Button.builder(Component.literal("Back"), b -> onClose())
                .bounds(cx - 60, this.height - 28, 120, 20).build());
        if (!historyTab && mapKeys.size() > 1) {
            addRenderableWidget(Button.builder(Component.literal("<"), b -> {
                        page = Math.floorMod(page - 1, mapKeys.size());
                        rebuild();
                    })
                    .bounds(cx - 150, this.height - 28, 40, 20).build());
            addRenderableWidget(Button.builder(Component.literal(">"), b -> {
                        page = Math.floorMod(page + 1, mapKeys.size());
                        rebuild();
                    })
                    .bounds(cx + 110, this.height - 28, 40, 20).build());
        }
    }

    private void rebuild() {
        this.clearWidgets();
        this.init();
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fillGradient(0, 0, this.width, this.height, 0xFF0B0A10, 0xFF060508);
    }

    /** Same reason as the picker: no blur pass under type. */
    @Override
    protected void renderBlurredBackground(float partialTick) {
        // Intentionally empty.
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g, mouseX, mouseY, partialTick);
        int cx = this.width / 2;

        g.drawCenteredString(this.font,
                Component.literal("RECORDS").withStyle(s -> s.withBold(true)), cx, 18, GOLD);
        g.fill(cx - 152, 36, cx + 152, 37, EDGE);

        if (historyTab) {
            renderHistory(g, cx);
        } else {
            renderBoards(g, cx);
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    private void renderBoards(GuiGraphics g, int cx) {
        if (mapKeys.isEmpty()) {
            g.drawCenteredString(this.font, Component.literal(
                    "Nothing set yet. Finish a run and you are the record."), cx, 90, DIM);
            return;
        }
        String key = mapKeys.get(Math.min(page, mapKeys.size() - 1));
        g.drawCenteredString(this.font, Component.literal(maps.getOrDefault(key, key)),
                cx, 74, TEXT);
        if (mapKeys.size() > 1) {
            g.drawCenteredString(this.font, Component.literal(
                    (page + 1) + " / " + mapKeys.size()), cx, this.height - 42, DIM);
        }
        column(g, cx - 175, key + "#solo", "SOLO");
        column(g, cx + 15, key + "#group", "GROUP");
    }

    /**
     * The player's own runs, newest first.
     *
     * <p>A summary strip, then one card per run. The card leads with the outcome
     * because that is the thing being remembered - not the score.
     */
    private void renderHistory(GuiGraphics g, int cx) {
        if (runs.isEmpty()) {
            g.drawCenteredString(this.font, Component.literal(
                    "No runs yet."), cx, 88, DIM);
            g.drawCenteredString(this.font, Component.literal(
                    "\u00a78Step through the portal and this fills up."), cx, 102, FAINT);
            return;
        }

        // Summary: what the kept runs add up to.
        int out = 0;
        int bestDay = 0;
        int kills = 0;
        int changed = 0;
        for (String r : runs) {
            String o = LeaderboardPayload.field(r, 1);
            if (o.equals("escaped") || o.equals("extracted")) {
                out++;
            }
            if (o.equals("changed")) {
                changed++;
            }
            bestDay = Math.max(bestDay, num(r, 2));
            kills += num(r, 4);
        }
        int sx = cx - 178;
        stat(g, sx, 76, String.valueOf(runs.size()), "runs kept");
        stat(g, sx + 90, 76, String.valueOf(out), "got out");
        stat(g, sx + 180, 76, String.valueOf(bestDay), "best");
        stat(g, sx + 270, 76, String.valueOf(kills), "kills");
        if (changed > 0) {
            g.drawString(this.font, Component.literal(
                            "\u00a74\u2620 turned " + changed + (changed == 1 ? " time" : " times")),
                    sx, 104, DEEP_RED, false);
        }
        g.fill(cx - 178, 116, cx + 178, 117, EDGE);

        int top = 124;
        int rowH = 30;
        int fit = Math.max(1, (this.height - top - 40) / rowH);
        int max = Math.max(0, runs.size() - fit);
        scroll = Math.max(0, Math.min(scroll, max));

        for (int i = 0; i < fit && i + scroll < runs.size(); i++) {
            card(g, cx - 178, top + i * rowH, 356, runs.get(i + scroll));
        }
        if (max > 0) {
            g.drawCenteredString(this.font, Component.literal(
                            "\u00a78" + (scroll + 1) + "\u2013" + Math.min(runs.size(), scroll + fit)
                                    + " of " + runs.size() + "  \u00b7  scroll"),
                    cx, this.height - 42, FAINT);
        }
    }

    private void stat(GuiGraphics g, int x, int y, String value, String label) {
        g.drawString(this.font, Component.literal(value), x, y, TEXT, false);
        g.drawString(this.font, Component.literal("\u00a78" + label), x, y + 10, FAINT, false);
    }

    /** One run, as a card. */
    private void card(GuiGraphics g, int x, int y, int w, String run) {
        String map = LeaderboardPayload.field(run, 0);
        String outcome = LeaderboardPayload.field(run, 1);
        int score = num(run, 2);
        int seconds = num(run, 3);
        int kills = num(run, 4);
        int charted = num(run, 6);
        int party = num(run, 7);

        boolean maze = map.equals("maze");
        int accent = switch (outcome) {
            case "escaped", "extracted" -> GOLD;
            case "changed" -> DEEP_RED;
            default -> RED;
        };
        String verdict = switch (outcome) {
            case "escaped" -> "GOT OUT";
            case "extracted" -> "BANKED";
            case "changed" -> "TURNED";
            case "taken" -> "TAKEN";
            default -> "FELL";
        };

        g.fill(x, y, x + w, y + 27, CARD);
        g.fill(x, y, x + 3, y + 27, accent);

        g.drawString(this.font, Component.literal(verdict), x + 10, y + 5, accent, false);
        g.drawString(this.font, Component.literal(
                        "\u00a77" + maps.getOrDefault(map, map)), x + 10, y + 16, DIM, false);

        String left = maze ? "day " + score : "round " + score;
        g.drawString(this.font, Component.literal(left), x + 108, y + 5, TEXT, false);
        g.drawString(this.font, Component.literal("\u00a78" + mmss(seconds)),
                x + 108, y + 16, FAINT, false);

        g.drawString(this.font, Component.literal("\u00a7f" + kills + " \u00a78kills"),
                x + 190, y + 5, DIM, false);
        if (maze) {
            g.drawString(this.font, Component.literal("\u00a7b" + charted + "% \u00a78charted"),
                    x + 190, y + 16, DIM, false);
        }
        if (party > 1) {
            g.drawString(this.font, Component.literal("\u00a78squad of " + party),
                    x + 280, y + 5, FAINT, false);
        } else {
            g.drawString(this.font, Component.literal("\u00a78solo"), x + 280, y + 5, FAINT, false);
        }
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        if (historyTab) {
            scroll = Math.max(0, scroll - (int) Math.signum(dy));
            return true;
        }
        return super.mouseScrolled(mx, my, dx, dy);
    }

    private static int num(String packed, int index) {
        try {
            return Integer.parseInt(LeaderboardPayload.field(packed, index));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String mmss(int seconds) {
        return (seconds / 60) + "m " + (seconds % 60) + "s";
    }

    /** One board. Ten places is as many as anybody reads. */
    private void column(GuiGraphics g, int left, String boardKey, String heading) {
        g.drawString(this.font, Component.literal(heading).withStyle(s -> s.withBold(true)),
                left, 58, 0xFFFFD24A, true);
        g.fill(left, 70, left + 160, 71, 0xFF3A3A3A);

        List<String> rows = boards.getOrDefault(boardKey, List.of());
        if (rows.isEmpty()) {
            g.drawString(this.font, Component.literal("— nobody yet —"), left, 80, 0xFF8A8A8A, true);
            return;
        }
        int y = 80;
        for (int i = 0; i < Math.min(10, rows.size()); i++) {
            String row = rows.get(i);
            String place = LeaderboardPayload.field(row, 1);
            String name = LeaderboardPayload.field(row, 2);
            String score = LeaderboardPayload.field(row, 3);
            String party = LeaderboardPayload.field(row, 5);

            // Gold, silver, bronze, then plain. The top three are the only ones
            // anybody is trying for, so they should be visible at a glance.
            int colour = switch (i) {
                case 0 -> 0xFFFFD24A;
                case 1 -> 0xFFCCCCCC;
                case 2 -> 0xFFC08040;
                default -> 0xFFD4D4D4;
            };
            g.drawString(this.font, Component.literal(place + "."), left, y, colour, true);
            g.drawString(this.font, Component.literal(name), left + 20, y, colour, true);
            g.drawString(this.font, Component.literal(score), left + 108, y, colour, true);
            if (!party.isEmpty() && !party.equals("1")) {
                g.drawString(this.font, Component.literal("§8×" + party), left + 150, y, 0xFF8A8A8A, true);
            }
            y += 11;
        }
    }
}
