package com.jrpetty.aztecabyss.client;

import com.jrpetty.aztecabyss.network.MapSelectPayload;
import com.jrpetty.aztecabyss.worldgen.ArenaMap;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The portal's arena picker: right-click a lit Abyss portal in the overworld and
 * this opens. Each arena gets a card showing its name, a difficulty tag, a short
 * description and your personal best round there, so the choice is informed and
 * there's always a record on the other map to go and beat.
 *
 * Rewards, rounds and scoring are identical whichever you pick.
 */
public final class MapSelectScreen extends Screen {

    private static final int CARD_W = 260;
    /** Card chrome above the blurb (title row) and below it (record row). */
    private static final int CARD_HEAD = 22;
    /**
     * Room under the blurb for the record row.
     *
     * <p>The record sits at {@code cardH - 13}, and the card grows with the
     * wrapped line count, so the clearance below the last line is this minus 13
     * whatever the blurb says - constant, never a collision. It was 18, leaving
     * five pixels, which did not read as a gap so much as a near miss on the
     * three-line cards.
     */
    private static final int CARD_FOOT = 24;
    private static final int LINE_H = 10;
    private static final int GAP = 10;

    private final int[] bestRounds;
    private int selected;

    public MapSelectScreen(int currentChoice, int[] bestRounds) {
        super(Component.literal("Choose Your Hunt"));
        this.selected = currentChoice;
        this.bestRounds = bestRounds;
    }

    /**
     * How tall a card has to be to hold its own blurb.
     *
     * <p>Cards used to be a fixed 58 high, which fitted a two-line blurb and
     * quietly overlapped a three-line one - the third line landed straight on
     * top of the record row. Height is now derived from the wrapped text, so a
     * map can be described in as many lines as it needs.
     */
    private int cardHeight(ArenaMap map) {
        int lines = this.font.split(Component.literal(map.blurb()), CARD_W - 20).size();
        return CARD_HEAD + lines * LINE_H + CARD_FOOT;
    }

    /**
     * How much of the bottom of the screen the mode buttons own.
     *
     * <p>Three stacked buttons plus a margin. The card stack is centred in what is
     * left rather than in the whole screen, because centring on the screen while
     * the buttons grew downward is how the bottom card ends up underneath the
     * Maze button on a short window.
     */
    private static final int BUTTON_BAND = 96;

    /** Top edge of card {@code index}, stacking the variable heights above it. */
    private int cardY(int index) {
        ArenaMap[] maps = ArenaMap.values();
        int total = 0;
        for (ArenaMap m : maps) {
            total += cardHeight(m) + GAP;
        }
        // Centred between the header and the button band, not on the screen.
        int top = 52;
        int bottom = this.height - BUTTON_BAND;
        int y = top + (bottom - top - (total - GAP)) / 2;
        for (int i = 0; i < index; i++) {
            y += cardHeight(maps[i]) + GAP;
        }
        return y;
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.literal("Enter this Hunt"), b -> {
                    PacketDistributor.sendToServer(new MapSelectPayload(selected));
                    onClose();
                })
                .bounds(this.width / 2 - 80, this.height - 42, 160, 20)
                .build());
        // The maze is deliberately set apart from the arena cards: no rounds, no
        // rewards, no scoring in common with them.
        addRenderableWidget(Button.builder(
                        Component.literal("§5The Maze §8— a different game entirely"), b -> {
                            PacketDistributor.sendToServer(new MapSelectPayload(MapSelectPayload.MAZE));
                            onClose();
                        })
                .bounds(this.width / 2 - 110, this.height - 68, 220, 20)
                .build());
        // Creator is set apart further still - it is the only thing on this screen
        // that is not a fight. Listed rather than left to be discovered, because a
        // mode you reach only by knowing a command to type is not really offered.
        addRenderableWidget(Button.builder(
                        Component.literal("§bMap Creator §8— build your own"), b -> {
                            PacketDistributor.sendToServer(new MapSelectPayload(MapSelectPayload.CREATOR));
                            onClose();
                        })
                .bounds(this.width / 2 - 110, this.height - 92, 220, 20)
                .build());
    }

    /** The whole card is the click target, so selection feels like picking a tile. */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int left = this.width / 2 - CARD_W / 2;
        ArenaMap[] maps = ArenaMap.values();
        for (int i = 0; i < maps.length; i++) {
            int y = cardY(i);
            int h = cardHeight(maps[i]);
            if (mouseX >= left && mouseX <= left + CARD_W && mouseY >= y && mouseY <= y + h) {
                selected = i;
                if (this.minecraft != null) {
                    this.minecraft.getSoundManager().play(
                            net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                                    net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /**
     * A flat dark wash instead of vanilla's blurred backdrop - the blur made the
     * card text and the world behind it read as smeared.
     */
    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, this.width, this.height, 0xE0080608);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g, mouseX, mouseY, partialTick);
        int cx = this.width / 2;

        g.drawCenteredString(this.font, Component.literal("CHOOSE YOUR HUNT").withStyle(s -> s.withBold(true)),
                cx, 26, 0xFFFFD24A);
        // Honest about what is actually on the menu: three arenas, the maze, and
        // the tools to add a fourth arena yourself.
        g.drawCenteredString(this.font, Component.literal("Three battlefields, a maze — or build your own."),
                cx, 40, 0xFF888888);

        ArenaMap[] maps = ArenaMap.values();
        int left = cx - CARD_W / 2;

        for (int i = 0; i < maps.length; i++) {
            ArenaMap map = maps[i];
            int y = cardY(i);
            int cardH = cardHeight(map);
            boolean isSelected = i == selected;
            boolean hovered = mouseX >= left && mouseX <= left + CARD_W && mouseY >= y && mouseY <= y + cardH;

            // Card body, brightened when picked or hovered.
            g.fill(left, y, left + CARD_W, y + cardH, isSelected ? 0xCC1A1410 : hovered ? 0xAA141414 : 0x99101010);
            int edge = isSelected ? 0xFFFFD24A : hovered ? 0xFF7A6A3A : 0xFF3A3A3A;
            g.fill(left, y, left + CARD_W, y + 1, edge);
            g.fill(left, y + cardH - 1, left + CARD_W, y + cardH, edge);
            g.fill(left, y, left + 1, y + cardH, edge);
            g.fill(left + CARD_W - 1, y, left + CARD_W, y + cardH, edge);
            // Selected cards get a thick gold spine down the left.
            if (isSelected) {
                g.fill(left + 1, y + 1, left + 4, y + cardH - 1, 0xFFFFD24A);
            }

            // Title.
            g.drawString(this.font, Component.literal(map.title()).withStyle(s -> s.withBold(true)),
                    left + 10, y + 8, isSelected ? 0xFFFFE9A8 : 0xFFDDDDDD, false);

            // Difficulty pill, right-aligned.
            String tag = map.difficulty();
            int tagW = this.font.width(tag) + 8;
            int tagX = left + CARD_W - tagW - 8;
            g.fill(tagX, y + 6, tagX + tagW, y + 18, 0x66000000);
            g.fill(tagX, y + 6, tagX + tagW, y + 7, map.difficultyColor());
            g.drawString(this.font, Component.literal(tag), tagX + 4, y + 9, map.difficultyColor(), false);

            // Blurb, wrapped across the card.
            int by = y + CARD_HEAD;
            for (net.minecraft.util.FormattedCharSequence line
                    : this.font.split(Component.literal(map.blurb()), CARD_W - 20)) {
                g.drawString(this.font, line, left + 10, by, 0xFF9A9A9A, false);
                by += LINE_H;
            }

            // Personal best on this arena - the record to beat.
            int best = (i < bestRounds.length) ? bestRounds[i] : 0;
            String rec = best > 0 ? "Your best: Round " + best : "Never attempted";
            int recW = this.font.width(rec);
            g.drawString(this.font, Component.literal(rec),
                    left + CARD_W - recW - 10, y + cardH - 13,
                    best > 0 ? 0xFF6EC8FF : 0xFF666666, false);
        }

        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
