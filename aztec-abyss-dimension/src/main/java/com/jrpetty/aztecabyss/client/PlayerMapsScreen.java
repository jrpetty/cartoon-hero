package com.jrpetty.aztecabyss.client;

import com.jrpetty.aztecabyss.network.MapSelectPayload;
import com.jrpetty.aztecabyss.network.OpenMapPickerPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.Locale;

/**
 * Every map the players have put on the portal, as a shelf you can read.
 *
 * <p>Published maps used to be a row of up to four buttons squeezed under the
 * arena cards - a title and a difficulty crushed into fifty pixels each, the
 * author nowhere, the blurb nowhere, and the fifth map onward simply not shown.
 * That is the wrong shape for the thing publishing built: a server where people
 * make games for each other needs the games to be browsable, or the making is a
 * hobby only the maker sees.
 *
 * <p>So: one card per map, however many there are, scrolling. A card carries
 * everything the manifest knows - the title, who built it, the difficulty, the
 * author's own pitch - and one line the manifest does not: which <em>game</em>
 * it plays, from the ruleset's title and blurb. Two authors can stamp the same
 * corridors and ship different games, and this line is where that shows.
 *
 * <p>Clicking a card is picking it: the same {@code CUSTOM_BASE + index}
 * protocol the old buttons spoke, against the same ordered list the server
 * packed, so this screen and the picker cannot disagree about which map is
 * which.
 */
public final class PlayerMapsScreen extends Screen {

    // The picker's palette, so stepping between the two screens reads as one UI.
    private static final int BG_TOP = 0xFF0B0A10;
    private static final int BG_BOTTOM = 0xFF060508;
    private static final int CARD_W = 300;
    private static final int GAP = 8;
    private static final int LINE_H = 10;

    private final List<String[]> maps = new java.util.ArrayList<>();
    private final Screen back;
    private double scroll = 0;

    public PlayerMapsScreen(List<String> packed, Screen back) {
        super(Component.literal("Player Maps"));
        this.back = back;
        for (String row : packed) {
            maps.add(row.split("\\|", -1));
        }
    }

    /** Field {@code i} of card {@code m}, or empty - same packing as the picker. */
    private String field(int m, int i) {
        String[] row = maps.get(m);
        return i < row.length ? row[i] : "";
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.literal("Back"),
                        b -> onClose())
                .bounds(this.width / 2 - 48, this.height - 28, 96, 20).build());
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(back);
        }
    }

    /**
     * How tall card {@code m} is: chrome plus its wrapped blurb plus the
     * ruleset line. Derived, not fixed, for the same reason the picker's cards
     * are - a three-line pitch must push the card out, not the text off it.
     */
    private int cardHeight(int m) {
        int lines = field(m, 3).isEmpty() ? 0
                : this.font.split(Component.literal(field(m, 3)), CARD_W - 20).size();
        return 24 + 12 + lines * LINE_H + 14 + 6;
    }

    /** Top edge of card {@code m} in content space, before scrolling. */
    private int cardTop(int m) {
        int y = 0;
        for (int i = 0; i < m; i++) {
            y += cardHeight(i) + GAP;
        }
        return y;
    }

    private int contentHeight() {
        return maps.isEmpty() ? 0 : cardTop(maps.size() - 1) + cardHeight(maps.size() - 1);
    }

    /** Where the scrolling window for cards begins and ends on screen. */
    private int viewTop() {
        return 56;
    }

    private int viewBottom() {
        return this.height - 36;
    }

    private int maxScroll() {
        return Math.max(0, contentHeight() - (viewBottom() - viewTop()));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double dx, double dy) {
        scroll = Math.max(0, Math.min(maxScroll(), scroll - dy * 24));
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int left = this.width / 2 - CARD_W / 2;
        if (mouseY >= viewTop() && mouseY <= viewBottom()
                && mouseX >= left && mouseX <= left + CARD_W) {
            for (int m = 0; m < maps.size(); m++) {
                int y = viewTop() + cardTop(m) - (int) scroll;
                if (mouseY >= y && mouseY < y + cardHeight(m)) {
                    PacketDistributor.sendToServer(
                            new MapSelectPayload(MapSelectPayload.CUSTOM_BASE + m));
                    if (minecraft != null) {
                        minecraft.setScreen(null);
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fillGradient(0, 0, this.width, this.height, BG_TOP, BG_BOTTOM);
    }

    /** No blur under type - the same call every screen in this mod makes. */
    @Override
    protected void renderBlurredBackground(float partialTick) {
        // Intentionally empty.
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g, mouseX, mouseY, partialTick);
        int cx = this.width / 2;
        g.drawCenteredString(this.font,
                Component.literal("PLAYER MAPS").withStyle(s -> s.withBold(true)),
                cx, 22, 0xFFE08FE0);
        g.drawCenteredString(this.font, Component.literal(
                        maps.isEmpty() ? "Nothing on the portal yet."
                                : maps.size() + " on the portal — click one to play it."),
                cx, 36, 0xFFB6B0A2);

        if (maps.isEmpty()) {
            g.drawCenteredString(this.font, Component.literal(
                    "§8Build one in the Map Creator and publish it — it will be here."),
                    cx, this.height / 2, 0xFF6A6A6A);
            super.render(g, mouseX, mouseY, partialTick);
            return;
        }

        int left = cx - CARD_W / 2;
        // Cards outside the window are clipped by the scissor, so a hundred
        // maps cost a hundred rectangles and nothing bleeds over the chrome.
        g.enableScissor(0, viewTop(), this.width, viewBottom());
        for (int m = 0; m < maps.size(); m++) {
            int y = viewTop() + cardTop(m) - (int) scroll;
            int h = cardHeight(m);
            if (y + h < viewTop() || y > viewBottom()) {
                continue;
            }
            boolean hovered = mouseX >= left && mouseX <= left + CARD_W
                    && mouseY >= y && mouseY < y + h
                    && mouseY >= viewTop() && mouseY <= viewBottom();
            g.fill(left, y, left + CARD_W, y + h, hovered ? 0xFF1C1C1C : 0xFF121212);
            int edge = hovered ? 0xFFB07AB0 : 0xFF3A3A3A;
            g.fill(left, y, left + CARD_W, y + 1, edge);
            g.fill(left, y + h - 1, left + CARD_W, y + h, edge);
            g.fill(left, y, left + 1, y + h, edge);
            g.fill(left + CARD_W - 1, y, left + CARD_W, y + h, edge);

            g.drawString(this.font,
                    Component.literal(field(m, 1)).withStyle(s -> s.withBold(true)),
                    left + 10, y + 7, hovered ? 0xFFFFF0C8 : 0xFFF2F2F2, true);

            String diff = field(m, 2).toUpperCase(Locale.ROOT);
            int diffColour = diffColour(diff);
            int tagW = this.font.width(diff) + 8;
            int tagX = left + CARD_W - tagW - 8;
            g.fill(tagX, y + 5, tagX + tagW, y + 17, 0xFF000000);
            g.fill(tagX, y + 5, tagX + tagW, y + 6, diffColour);
            g.drawString(this.font, Component.literal(diff), tagX + 4, y + 8, diffColour, true);

            int by = y + 24;
            g.drawString(this.font, Component.literal("§8by §7" + field(m, 4)),
                    left + 10, by, 0xFF8A8A8A, true);
            by += 12;
            if (!field(m, 3).isEmpty()) {
                for (net.minecraft.util.FormattedCharSequence line
                        : this.font.split(Component.literal(field(m, 3)), CARD_W - 20)) {
                    g.drawString(this.font, line, left + 10, by, 0xFFC8C4BA, true);
                    by += LINE_H;
                }
            }
            // What game these blocks play. The ruleset's own name and pitch -
            // the difference between "a map" and "capture the flag, in here".
            String plays = field(m, 5);
            if (!plays.isEmpty()) {
                String line = "§dplays " + plays
                        + (field(m, 6).isEmpty() ? "" : " §8— " + field(m, 6));
                for (net.minecraft.util.FormattedCharSequence seq
                        : this.font.split(Component.literal(line), CARD_W - 20)) {
                    g.drawString(this.font, seq, left + 10, by, 0xFFB07AB0, true);
                    by += LINE_H;
                    break; // one line; a ruleset pitch that wraps is elided
                }
            }
        }
        g.disableScissor();

        // The window's own edges, drawn over the clip so half-cards read as
        // deliberately cut rather than broken.
        if (scroll > 0) {
            g.fillGradient(0, viewTop(), this.width, viewTop() + 8, 0xFF060508, 0x00060508);
        }
        if (scroll < maxScroll()) {
            g.fillGradient(0, viewBottom() - 8, this.width, viewBottom(), 0x00060508, 0xFF060508);
        }

        super.render(g, mouseX, mouseY, partialTick);
    }

    private static int diffColour(String diff) {
        return switch (diff) {
            case "EASY" -> 0xFF63D488;
            case "HARD", "BRUTAL" -> 0xFFE05555;
            case "MEDIUM" -> 0xFFF0C75A;
            default -> 0xFFB6B0A2;
        };
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
