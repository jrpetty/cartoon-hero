package com.jrpetty.aztecabyss.client;

import com.jrpetty.aztecabyss.network.MapSelectPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.Locale;

/**
 * Every map the players have put on the portal, as a shelf you can read.
 *
 * <p>One card per map, however many there are, scrolling. A card carries
 * everything the manifest knows - the title, who built it, the difficulty, the
 * author's own pitch - and one line the manifest does not: which <em>game</em>
 * it plays, from the ruleset's title and blurb. Two authors can stamp the same
 * corridors and ship different games, and this line is where that shows.
 *
 * <p>Clicking a card is picking it: the same {@code CUSTOM_BASE + index}
 * protocol the picker speaks, against the same ordered list the server packed,
 * so the two screens cannot disagree about which map is which.
 */
public final class PlayerMapsScreen extends AbyssScreen {

    private static final int GAP = 8;
    private static final int LINE_H = 10;
    private static final int ACCENT = 0xFFE08FE0;

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

    private int cardW() {
        return Math.min(320, this.width - 32);
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.literal("Back"), b -> onClose())
                .bounds(this.width / 2 - 50, this.height - 28, 100, 20).build());
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(back);
        }
    }

    /**
     * How tall card {@code m} is: chrome plus its wrapped blurb plus the
     * ruleset line. Derived, not fixed - a three-line pitch must push the card
     * out, not the text off it.
     */
    private int cardHeight(int m) {
        int lines = field(m, 3).isEmpty() ? 0
                : this.font.split(Component.literal(field(m, 3)), cardW() - 20).size();
        return 26 + 12 + lines * LINE_H + (field(m, 5).isEmpty() ? 0 : 12) + 8;
    }

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

    private int viewTop() {
        return 58;
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
        int left = this.width / 2 - cardW() / 2;
        if (mouseY >= viewTop() && mouseY <= viewBottom()
                && mouseX >= left && mouseX <= left + cardW()) {
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
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int cx = this.width / 2;
        int y = UiKit.masthead(g, this.font, "ON THE PORTAL", "PLAYER MAPS", cx, 8, ACCENT);
        UiKit.fret(g, cx, y + 1, Math.min(150, cardW() / 2), ACCENT);
        g.drawCenteredString(this.font, Component.literal(maps.isEmpty()
                        ? "Nothing published yet."
                        : maps.size() + (maps.size() == 1 ? " map" : " maps") + " — click one to play it."),
                cx, y + 8, UiKit.TEXT_DIM);

        if (maps.isEmpty()) {
            renderEmpty(g, cx);
            return;
        }

        int left = cx - cardW() / 2;
        // Cards outside the window are clipped, so a hundred maps cost a hundred
        // rectangles and nothing bleeds over the header or the Back button.
        g.enableScissor(0, viewTop(), this.width, viewBottom());
        for (int m = 0; m < maps.size(); m++) {
            int top = viewTop() + cardTop(m) - (int) scroll;
            int h = cardHeight(m);
            if (top + h < viewTop() || top > viewBottom()) {
                continue;
            }
            boolean hot = mouseX >= left && mouseX <= left + cardW()
                    && mouseY >= top && mouseY < top + h
                    && mouseY >= viewTop() && mouseY <= viewBottom();
            renderCard(g, m, left, top, h, hot);
        }
        g.disableScissor();

        // The window's own edges, drawn over the clip so half-cards read as
        // deliberately cut rather than broken.
        if (scroll > 0) {
            g.fillGradient(0, viewTop(), this.width, viewTop() + 10, 0xFF09080D, 0x0009080D);
        }
        if (scroll < maxScroll()) {
            g.fillGradient(0, viewBottom() - 10, this.width, viewBottom(), 0x0009080D, 0xFF09080D);
        }
    }

    private void renderCard(GuiGraphics g, int m, int x, int y, int h, boolean hot) {
        int w = cardW();
        String diff = field(m, 2).toUpperCase(Locale.ROOT);
        int diffColour = diffColour(diff);
        UiKit.panel(g, x, y, w, h, hot ? UiKit.PANEL_HOT : UiKit.PANEL, hot ? ACCENT : UiKit.EDGE);
        g.fill(x + 1, y + 1, x + 3, y + h - 1, hot ? ACCENT : UiKit.alpha(ACCENT, 0x80));

        g.renderItem(new ItemStack(Items.FILLED_MAP), x + 9, y + 6);
        int tagW = UiKit.tagWidth(this.font, diff);
        String title = this.font.plainSubstrByWidth(field(m, 1), w - 40 - tagW - 12);
        g.drawString(this.font, Component.literal(title).withStyle(s -> s.withBold(true)),
                x + 30, y + 10, hot ? 0xFFFFF0C8 : UiKit.TEXT, true);
        UiKit.tag(g, this.font, diff, x + w - tagW - 8, y + 8, diffColour);

        int by = y + 26;
        g.drawString(this.font, "by " + field(m, 4), x + 12, by, UiKit.TEXT_FAINT, true);
        by += 12;
        if (!field(m, 3).isEmpty()) {
            for (FormattedCharSequence line : this.font.split(Component.literal(field(m, 3)), w - 20)) {
                g.drawString(this.font, line, x + 12, by, UiKit.TEXT_DIM, true);
                by += LINE_H;
            }
        }
        // What game these blocks play: the ruleset's own name and pitch - the
        // difference between "a map" and "capture the flag, in here".
        String plays = field(m, 5);
        if (!plays.isEmpty()) {
            String line = "plays " + plays + (field(m, 6).isEmpty() ? "" : " — " + field(m, 6));
            g.drawString(this.font, this.font.plainSubstrByWidth(line, w - 24),
                    x + 12, by + 2, ACCENT, true);
        }
    }

    /** What the shelf looks like before anyone has put anything on it. */
    private void renderEmpty(GuiGraphics g, int cx) {
        int w = Math.min(300, this.width - 40);
        List<FormattedCharSequence> how = this.font.split(Component.literal(
                "Open the Map Creator from the portal, build something, then publish it "
                        + "from the Creator Console — it will be here for everyone."), w - 24);
        int h = 50 + how.size() * LINE_H + 6;
        int x = cx - w / 2;
        int y = Math.max(viewTop() + 6, (viewTop() + viewBottom()) / 2 - h / 2);
        UiKit.panel(g, x, y, w, h, UiKit.PANEL_DEEP, UiKit.EDGE);
        g.renderItem(new ItemStack(Items.WRITABLE_BOOK), cx - 8, y + 9);
        g.drawCenteredString(this.font, "The shelf is empty.", cx, y + 31, UiKit.TEXT);
        int ty = y + 46;
        for (FormattedCharSequence line : how) {
            g.drawCenteredString(this.font, line, cx, ty, UiKit.TEXT_DIM);
            ty += LINE_H;
        }
    }

    private static int diffColour(String diff) {
        return switch (diff) {
            case "EASY" -> UiKit.GREEN;
            case "HARD", "BRUTAL" -> UiKit.RED;
            case "MEDIUM" -> 0xFFF0C75A;
            default -> UiKit.TEXT_DIM;
        };
    }
}
