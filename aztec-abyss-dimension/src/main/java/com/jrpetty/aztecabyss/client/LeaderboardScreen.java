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
 */
public class LeaderboardScreen extends Screen {

    private final Screen parent;
    /** Map key to display name, in the order the server sent them. */
    private final Map<String, String> maps = new LinkedHashMap<>();
    /** Board key ({@code map#solo}) to its rows, already in order. */
    private final Map<String, List<String>> boards = new LinkedHashMap<>();
    private final List<String> mapKeys = new ArrayList<>();

    private int page = 0;

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
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.literal("Back"), b -> onClose())
                .bounds(this.width / 2 - 60, this.height - 30, 120, 20).build());
        if (mapKeys.size() > 1) {
            addRenderableWidget(Button.builder(Component.literal("<"), b -> {
                        page = Math.floorMod(page - 1, mapKeys.size());
                        rebuild();
                    })
                    .bounds(this.width / 2 - 150, this.height - 30, 40, 20).build());
            addRenderableWidget(Button.builder(Component.literal(">"), b -> {
                        page = Math.floorMod(page + 1, mapKeys.size());
                        rebuild();
                    })
                    .bounds(this.width / 2 + 110, this.height - 30, 40, 20).build());
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
                Component.literal("RECORDS").withStyle(s -> s.withBold(true)), cx, 20, 0xFFFFD24A);

        if (mapKeys.isEmpty()) {
            g.drawCenteredString(this.font, Component.literal(
                    "Nothing set yet. Finish a run and you are the record."), cx, 60, 0xFF888888);
            super.render(g, mouseX, mouseY, partialTick);
            return;
        }

        String key = mapKeys.get(Math.min(page, mapKeys.size() - 1));
        g.drawCenteredString(this.font, Component.literal(maps.getOrDefault(key, key)),
                cx, 36, 0xFFF2F2F2);
        if (mapKeys.size() > 1) {
            g.drawCenteredString(this.font, Component.literal(
                    (page + 1) + " / " + mapKeys.size()), cx, this.height - 44, 0xFF9A9A9A);
        }

        column(g, cx - 175, key + "#solo", "SOLO");
        column(g, cx + 15, key + "#group", "GROUP");

        super.render(g, mouseX, mouseY, partialTick);
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
