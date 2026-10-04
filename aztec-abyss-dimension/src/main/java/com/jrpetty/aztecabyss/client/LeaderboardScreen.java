package com.jrpetty.aztecabyss.client;

import com.jrpetty.aztecabyss.network.LeaderboardPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
 * is actually asking. Almost nobody tops a board; nearly every run is a defeat,
 * and the interesting part of a defeat is the detail. Which day it got you. How
 * much you had charted when it did. Whether you were the one who turned.
 *
 * <p>So the history is not a list of scores. Each run is a card with its outcome
 * written in the colour of what happened - gold for getting out, red for being
 * taken, and a deeper red for the Changing, which is its own ending and deserves
 * to look like one.
 */
public class LeaderboardScreen extends AbyssScreen {

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

    private static final int CONTENT_TOP = 72;
    private static final int ROW_H = 11;
    private static final int CARD_H = 28;

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

    private int contentW() {
        return Math.min(360, this.width - 24);
    }

    private int backY() {
        return this.height - 28;
    }

    @Override
    protected void initWidgets() {
        int cx = this.width / 2;
        Button records = Button.builder(Component.literal("Records"), b -> {
            historyTab = false;
            scroll = 0;
            rebuild();
        }).bounds(cx - 112, 46, 110, 20).build();
        records.active = historyTab;
        addRenderableWidget(records);

        Button mine = Button.builder(Component.literal("Your runs"), b -> {
            historyTab = true;
            scroll = 0;
            rebuild();
        }).bounds(cx + 2, 46, 110, 20).build();
        mine.active = !historyTab;
        addRenderableWidget(mine);

        addRenderableWidget(Button.builder(Component.literal("Back"), b -> onClose())
                .bounds(cx - 50, backY(), 100, 20).build());
        if (!historyTab && mapKeys.size() > 1) {
            int half = contentW() / 2;
            addRenderableWidget(Button.builder(Component.literal("◀"), b -> {
                        page = Math.floorMod(page - 1, mapKeys.size());
                        rebuild();
                    })
                    .bounds(cx - half, CONTENT_TOP, 20, 20).build());
            addRenderableWidget(Button.builder(Component.literal("▶"), b -> {
                        page = Math.floorMod(page + 1, mapKeys.size());
                        rebuild();
                    })
                    .bounds(cx + half - 20, CONTENT_TOP, 20, 20).build());
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
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int cx = this.width / 2;
        UiKit.masthead(g, this.font, "THE ABYSS PORTAL", "RECORDS", cx, 8, UiKit.GOLD);
        if (historyTab) {
            renderHistory(g, cx);
        } else {
            renderBoards(g, cx);
        }
    }

    // ------------------------------------------------------------------
    // Records
    // ------------------------------------------------------------------

    private void renderBoards(GuiGraphics g, int cx) {
        if (mapKeys.isEmpty()) {
            g.drawCenteredString(this.font, "Nothing set yet. Finish a run and you are the record.",
                    cx, CONTENT_TOP + 30, UiKit.TEXT_DIM);
            return;
        }
        String key = mapKeys.get(Math.min(page, mapKeys.size() - 1));
        g.drawCenteredString(this.font, Component.literal(label(key)).withStyle(s -> s.withBold(true)),
                cx, CONTENT_TOP + 3, UiKit.TEXT);
        if (mapKeys.size() > 1) {
            g.drawCenteredString(this.font, (page + 1) + " of " + mapKeys.size(),
                    cx, CONTENT_TOP + 13, UiKit.TEXT_FAINT);
        }

        int top = CONTENT_TOP + 28;
        int colW = (contentW() - 10) / 2;
        int left = cx - contentW() / 2;
        int rows = Math.max(1, Math.min(10, (backY() - 8 - top - 24) / ROW_H));
        column(g, left, top, colW, rows, key + "#solo", "SOLO");
        column(g, left + colW + 10, top, colW, rows, key + "#group", "GROUP");
    }

    /** One board. Ten places is as many as anybody reads. */
    private void column(GuiGraphics g, int x, int y, int w, int rows, String boardKey, String heading) {
        int h = 22 + rows * ROW_H + 6;
        UiKit.panel(g, x, y, w, h);
        g.drawString(this.font, Component.literal(heading).withStyle(s -> s.withBold(true)),
                x + 8, y + 7, UiKit.GOLD, true);
        UiKit.rule(g, x + 8, x + w - 8, y + 18, UiKit.EDGE_HOT);

        List<String> list = boards.getOrDefault(boardKey, List.of());
        if (list.isEmpty()) {
            g.drawString(this.font, "nobody yet — be the first", x + 8, y + 26, UiKit.TEXT_FAINT, true);
            return;
        }
        int ry = y + 24;
        for (int i = 0; i < Math.min(rows, list.size()); i++) {
            String row = list.get(i);
            String place = LeaderboardPayload.field(row, 1);
            String name = LeaderboardPayload.field(row, 2);
            String score = LeaderboardPayload.field(row, 3);
            String party = LeaderboardPayload.field(row, 5);

            // Gold, silver, bronze, then plain. The top three are the only ones
            // anybody is trying for, so they should be visible at a glance.
            int colour = switch (i) {
                case 0 -> UiKit.GOLD;
                case 1 -> 0xFFD6D6E0;
                case 2 -> 0xFFD08A4A;
                default -> UiKit.TEXT_DIM;
            };
            if (i < 3) {
                g.fill(x + 3, ry - 1, x + 5, ry + 8, colour);
            }
            g.drawString(this.font, place + ".", x + 8, ry, colour, true);
            boolean squad = !party.isEmpty() && !party.equals("1");
            String tail = squad ? " ×" + party : "";
            int scoreW = this.font.width(score + tail);
            g.drawString(this.font, this.font.plainSubstrByWidth(name, w - 34 - scoreW - 8),
                    x + 26, ry, colour, true);
            g.drawString(this.font, score, x + w - 8 - scoreW, ry, colour, true);
            if (squad) {
                g.drawString(this.font, tail, x + w - 8 - this.font.width(tail), ry, UiKit.TEXT_FAINT, true);
            }
            ry += ROW_H;
        }
    }

    // ------------------------------------------------------------------
    // Your runs
    // ------------------------------------------------------------------

    /**
     * The player's own runs, newest first: a summary strip, then one card per
     * run. The card leads with the outcome because that is the thing being
     * remembered - not the score.
     */
    private void renderHistory(GuiGraphics g, int cx) {
        int w = contentW();
        int left = cx - w / 2;
        if (runs.isEmpty()) {
            UiKit.panel(g, left, CONTENT_TOP + 6, w, 44, UiKit.PANEL_DEEP, UiKit.EDGE);
            g.drawCenteredString(this.font, "No runs yet.", cx, CONTENT_TOP + 16, UiKit.TEXT);
            g.drawCenteredString(this.font, "Step through the portal and this fills up.",
                    cx, CONTENT_TOP + 30, UiKit.TEXT_DIM);
            return;
        }

        int out = 0;
        int bestRound = 0;
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
            if (LeaderboardPayload.field(r, 0).equals("maze")) {
                bestDay = Math.max(bestDay, num(r, 2));
            } else {
                bestRound = Math.max(bestRound, num(r, 2));
            }
            kills += num(r, 4);
        }

        // The summary strip: what the kept runs add up to.
        UiKit.panel(g, left, CONTENT_TOP, w, 30, UiKit.PANEL_DEEP, UiKit.EDGE);
        int cells = 4;
        int cellW = w / cells;
        stat(g, left, cellW, 0, String.valueOf(runs.size()), "runs kept", UiKit.TEXT);
        stat(g, left, cellW, 1, String.valueOf(out), "got out", UiKit.GOLD);
        stat(g, left, cellW, 2, bestRound > 0 ? "Round " + bestRound : bestDay > 0 ? "Day " + bestDay : "—",
                "best", UiKit.CYAN);
        stat(g, left, cellW, 3, String.valueOf(kills), "kills", UiKit.GREEN);
        if (changed > 0) {
            String turned = "☠ turned " + changed + (changed == 1 ? " time" : " times");
            g.drawString(this.font, turned, left + w - 6 - this.font.width(turned), CONTENT_TOP + 33,
                    UiKit.DEEP_RED, true);
        }

        int top = CONTENT_TOP + 44;
        int fit = Math.max(1, (backY() - 8 - top) / (CARD_H + 3));
        int max = Math.max(0, runs.size() - fit);
        scroll = Math.max(0, Math.min(scroll, max));
        for (int i = 0; i < fit && i + scroll < runs.size(); i++) {
            card(g, left, top + i * (CARD_H + 3), w, runs.get(i + scroll));
        }
        if (max > 0) {
            String more = (scroll + 1) + "–" + Math.min(runs.size(), scroll + fit)
                    + " of " + runs.size() + " · scroll for more";
            g.drawString(this.font, more, left + w - this.font.width(more), backY() + 6, UiKit.TEXT_FAINT, true);
        }
    }

    private void stat(GuiGraphics g, int left, int cellW, int index, String value, String label, int colour) {
        int cx = left + cellW * index + cellW / 2;
        g.drawCenteredString(this.font, Component.literal(value).withStyle(s -> s.withBold(true)),
                cx, CONTENT_TOP + 5, colour);
        g.drawCenteredString(this.font, label, cx, CONTENT_TOP + 17, UiKit.TEXT_FAINT);
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
            case "escaped", "extracted" -> UiKit.GOLD;
            case "changed" -> UiKit.DEEP_RED;
            default -> UiKit.RED;
        };
        String verdict = switch (outcome) {
            case "escaped" -> "GOT OUT";
            case "extracted" -> "BANKED";
            case "changed" -> "TURNED";
            case "taken" -> "TAKEN";
            default -> "FELL";
        };

        UiKit.panel(g, x, y, w, CARD_H);
        g.fill(x + 1, y + 1, x + 4, y + CARD_H - 1, accent);

        int c1 = x + 10;
        int c2 = x + Math.max(96, w * 30 / 100);
        int c3 = x + Math.max(170, w * 52 / 100);
        int c4 = x + w - 8;
        g.drawString(this.font, Component.literal(verdict).withStyle(s -> s.withBold(true)), c1, y + 5, accent, true);
        g.drawString(this.font, this.font.plainSubstrByWidth(label(map), c2 - c1 - 6), c1, y + 16,
                UiKit.TEXT_FAINT, true);

        g.drawString(this.font, maze ? "day " + score : "round " + score, c2, y + 5, UiKit.TEXT, true);
        g.drawString(this.font, mmss(seconds), c2, y + 16, UiKit.TEXT_FAINT, true);

        g.drawString(this.font, kills + " kills", c3, y + 5, UiKit.TEXT_DIM, true);
        if (maze) {
            g.drawString(this.font, charted + "% charted", c3, y + 16, UiKit.CYAN, true);
        }
        String who = party > 1 ? "squad of " + party : "solo";
        g.drawString(this.font, who, c4 - this.font.width(who), y + 5, UiKit.TEXT_FAINT, true);
    }

    @Override
    protected boolean scrolledAt(double mx, double my, double dx, double dy) {
        if (historyTab) {
            scroll = Math.max(0, scroll - (int) Math.signum(dy));
            return true;
        }
        return super.scrolledAt(mx, my, dx, dy);
    }

    /**
     * A map's name, from the server's labels when it sent one. A run on a map
     * that has since left the portal still gets a readable name rather than
     * its storage key.
     */
    private String label(String key) {
        String named = maps.get(key);
        if (named != null) {
            return named;
        }
        String bare = key.startsWith("custom:") ? key.substring(7) : key;
        if (bare.isEmpty()) {
            return key;
        }
        return "The " + bare.substring(0, 1).toUpperCase(Locale.ROOT) + bare.substring(1);
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
}
