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
    /** Kept so a card click can rename it after the selection it acts on. */
    private Button enterButton;
    /** Published maps, packed by the server - see {@link com.jrpetty.aztecabyss.network.OpenMapPickerPayload}. */
    private final java.util.List<String> customMaps;

    public MapSelectScreen(int currentChoice, int[] bestRounds, java.util.List<String> customMaps) {
        super(Component.literal("Choose Your Hunt"));
        // A stored choice from before a map was shelved snaps back to the
        // Temple, exactly as the server will when the run starts.
        this.selected = ArenaMap.byId(currentChoice).ordinal();
        this.bestRounds = bestRounds;
        this.customMaps = customMaps == null ? java.util.List.of() : customMaps;
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
        return cardHeight(map, fit());
    }

    /**
     * How much of the bottom of the screen the mode buttons own.
     *
     * <p>Four stacked buttons plus a margin. The card stack is centred in what is
     * left rather than in the whole screen, because centring on the screen while
     * the buttons grew downward is how the bottom card ends up underneath the
     * Maze button on a short window.
     */
    private static final int BUTTON_BAND = 124;

    /** Where the card stack may begin: just under the two header lines. */
    private static final int TOP = 54;

    /** A shelved map folded to one line: title left, COMING SOON right. */
    private static final int STRIP_H = 22;

    /**
     * How the cards have to give to fit the window.
     *
     * <p>The stack was centred in the space between header and buttons by an
     * offset that went <em>negative</em> when the stack was taller than the
     * space - so at the most common GUI scale the top card's title sat on the
     * header's subtitle and the bottom card's record row sat under the first
     * button. Three full cards need about 240 pixels; a 1080p window at GUI
     * scale 3 has about 190 to give them.
     *
     * <p>Instead of overflowing, the layout yields in order of what is worth
     * least: 0 - everything as written; 1 - the shelved map folds to a
     * one-line strip (a teaser does not need a blurb) and the gaps tighten;
     * 2 - every card drops its blurb and keeps title, tag and record. Chosen
     * fresh each frame from the live height, so resizing the window can never
     * leave the wrong shape behind.
     */
    private int fit() {
        int avail = this.height - BUTTON_BAND - TOP;
        for (int mode = 0; mode < 2; mode++) {
            if (stackHeight(mode) <= avail) {
                return mode;
            }
        }
        return 2;
    }

    private static int gap(int mode) {
        return mode == 0 ? GAP : 4;
    }

    private int cardHeight(ArenaMap map, int mode) {
        if (map.comingSoon() && mode >= 1) {
            return STRIP_H;
        }
        if (mode >= 2) {
            return CARD_HEAD + CARD_FOOT;
        }
        int lines = this.font.split(Component.literal(map.blurb()), CARD_W - 20).size();
        return CARD_HEAD + lines * LINE_H + CARD_FOOT;
    }

    private int stackHeight(int mode) {
        ArenaMap[] maps = ArenaMap.values();
        int total = 0;
        for (ArenaMap m : maps) {
            total += cardHeight(m, mode);
        }
        return total + gap(mode) * Math.max(0, maps.length - 1);
    }

    /** Top edge of card {@code index}, stacking the variable heights above it. */
    private int cardY(int index) {
        ArenaMap[] maps = ArenaMap.values();
        int mode = fit();
        int avail = this.height - BUTTON_BAND - TOP;
        // Centred in the room there is, and never above TOP: an offset that
        // could go negative is exactly the bug this replaces.
        int y = TOP + Math.max(0, (avail - stackHeight(mode)) / 2);
        for (int i = 0; i < index; i++) {
            y += cardHeight(maps[i], mode) + gap(mode);
        }
        return y;
    }

    @Override
    protected void init() {
        // Records live one press from the place everybody stands before every run,
        // which is the only moment anyone actually wants to know what the record is.
        addRenderableWidget(Button.builder(Component.literal("§eRecords"), b ->
                        PacketDistributor.sendToServer(
                                new com.jrpetty.aztecabyss.network.RequestLeaderboardPayload(0)))
                .bounds(this.width / 2 + 84, this.height - 42, 76, 20)
                .build());
        // Named after what it will actually do. "Enter this Hunt" made you
        // glance back up to check which card was lit; the button carrying the
        // map's own name removes the round trip.
        enterButton = Button.builder(enterLabel(), b -> {
                    PacketDistributor.sendToServer(new MapSelectPayload(selected));
                    onClose();
                })
                .bounds(this.width / 2 - 160, this.height - 42, 160, 20)
                .build();
        addRenderableWidget(enterButton);
        // The maze is deliberately set apart from the arena cards: no rounds, no
        // rewards, no scoring in common with them.
        addRenderableWidget(Button.builder(
                        Component.literal("§5The Maze §8— a different game entirely"), b -> {
                            PacketDistributor.sendToServer(new MapSelectPayload(MapSelectPayload.MAZE));
                            onClose();
                        })
                .bounds(this.width / 2 - 110, this.height - 68, 220, 20)
                .build());
        // Published maps used to be a row of up to four cramped buttons here -
        // title and difficulty crushed into fifty pixels, author and blurb
        // nowhere, and the fifth map onward simply not offered. They are a
        // browser now: one door on this screen, and a whole screen of cards
        // behind it, however many the server has.
        addRenderableWidget(Button.builder(
                        Component.literal(customMaps.isEmpty()
                                ? "§7Player maps §8— none published yet"
                                : "§dPlayer maps §8— " + customMaps.size() + " on the portal"),
                        b -> {
                            if (minecraft != null) {
                                minecraft.setScreen(new PlayerMapsScreen(customMaps, this));
                            }
                        })
                .bounds(this.width / 2 - 110, this.height - 116, 220, 20)
                .build());

        // Creator is set apart further still - it is the only thing on this screen
        // that is not a fight. Listed rather than left to be discovered, because a
        // mode you reach only by knowing a command to type is not really offered.
        addRenderableWidget(Button.builder(
                        Component.literal("§bMap Creator §8— build your own §7(password)"), b -> {
                            PacketDistributor.sendToServer(new MapSelectPayload(MapSelectPayload.CREATOR));
                            onClose();
                        })
                .bounds(this.width / 2 - 110, this.height - 92, 220, 20)
                .build());
    }

    private Component enterLabel() {
        return Component.literal("Enter — " + ArenaMap.values()[selected].title());
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
                if (maps[i].comingSoon()) {
                    // The card is a teaser, not a choice. It stays visible so the
                    // roster reads as three, but nothing selects it.
                    return true;
                }
                selected = i;
                if (enterButton != null) {
                    enterButton.setMessage(enterLabel());
                }
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
        // Fully opaque, and deliberately not a call to super.
        //
        // Vanilla's screen background is a blur pass over the live world, and at
        // 0xE0 this was letting 12% of that blurred, moving world through - which
        // is exactly the smear the cards and their text were being read against.
        // Sitting a type-heavy screen on top of a blurred render of whatever you
        // happened to be standing in front of makes every card look out of focus,
        // and no amount of contrast in the card fixes it.
        //
        // A solid ground costs nothing and means the picker reads the same whether
        // you opened it in daylight or in a cave.
        g.fillGradient(0, 0, this.width, this.height, 0xFF0B0A10, 0xFF060508);
    }

    /**
     * No blur pass at all.
     *
     * <p>Belt to the brace above: vanilla runs the blur from its own background
     * hook, so a screen that merely paints over the result is still paying for it
     * and is one refactor away from showing it again. Overriding it away is the
     * only version that cannot come back.
     */
    @Override
    protected void renderBlurredBackground(float partialTick) {
        // Intentionally empty.
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g, mouseX, mouseY, partialTick);
        int cx = this.width / 2;

        g.drawCenteredString(this.font, Component.literal("CHOOSE YOUR HUNT").withStyle(s -> s.withBold(true)),
                cx, 24, 0xFFFFD24A);
        g.drawCenteredString(this.font, Component.literal("Two battlefields, a maze — or build your own."),
                cx, 38, 0xFFB6B0A2);

        ArenaMap[] maps = ArenaMap.values();
        int left = cx - CARD_W / 2;

        int mode = fit();
        for (int i = 0; i < maps.length; i++) {
            ArenaMap map = maps[i];
            int y = cardY(i);
            int cardH = cardHeight(map, mode);
            boolean shelved = map.comingSoon();
            boolean strip = shelved && mode >= 1;
            boolean isSelected = i == selected && !shelved;
            boolean hovered = !shelved
                    && mouseX >= left && mouseX <= left + CARD_W && mouseY >= y && mouseY <= y + cardH;

            // Opaque card bodies, and visibly lighter than the ground: at
            // 0xFF121212 on a 0xFF0B0A10 gradient the card and the backdrop
            // were seven shades apart, which on most monitors is no card at
            // all - just text floating in the dark with a faint line under it.
            g.fill(left, y, left + CARD_W, y + cardH,
                    isSelected ? 0xFF2A2117 : hovered ? 0xFF22212C : 0xFF19181F);
            int accent = map.difficultyColor();
            int edge = isSelected ? accent : hovered ? 0xFF9A8A5A : shelved ? 0xFF34323E : 0xFF56536A;
            g.fill(left, y, left + CARD_W, y + 1, edge);
            g.fill(left, y + cardH - 1, left + CARD_W, y + cardH, edge);
            g.fill(left, y, left + 1, y + cardH, edge);
            g.fill(left + CARD_W - 1, y, left + CARD_W, y + cardH, edge);
            if (isSelected) {
                g.fill(left + 1, y + 1, left + 4, y + cardH - 1, accent);
            }

            // Every string below is drawn WITH its shadow. Minecraft's font is
            // designed around that shadow; without it small text on a dark ground
            // goes thin and shimmers, which is most of why this screen was hard to
            // read. It was passing false everywhere.
            // In strip mode the title and tag sit on one 22-pixel line, so both
            // move up a notch; everything else on the card is skipped.
            int titleY = strip ? y + 7 : y + 8;
            g.drawString(this.font, Component.literal(map.title()).withStyle(s -> s.withBold(true)),
                    left + 10, titleY, shelved ? 0xFF6A6A6A : isSelected ? 0xFFFFF0C8 : 0xFFF2F2F2, true);

            String tag = shelved ? "COMING SOON" : map.difficulty();
            int tagColour = shelved ? 0xFFFFD24A : map.difficultyColor();
            int tagW = this.font.width(tag) + 8;
            int tagX = left + CARD_W - tagW - 8;
            int tagY = strip ? y + 5 : y + 6;
            g.fill(tagX, tagY, tagX + tagW, tagY + 12, 0xFF000000);
            g.fill(tagX, tagY, tagX + tagW, tagY + 1, tagColour);
            g.drawString(this.font, Component.literal(tag), tagX + 4, tagY + 3, tagColour, true);

            if (strip) {
                continue;
            }

            if (mode < 2) {
                int by = y + CARD_HEAD;
                for (net.minecraft.util.FormattedCharSequence line
                        : this.font.split(Component.literal(map.blurb()), CARD_W - 20)) {
                    g.drawString(this.font, line, left + 10, by, shelved ? 0xFF555555 : 0xFFC8C4BA, true);
                    by += LINE_H;
                }
            }

            int best = (i < bestRounds.length) ? bestRounds[i] : 0;
            String rec = shelved ? "Being rethought — back in a future update"
                    : best > 0 ? "Your best: Round " + best : "Never attempted";
            int recW = this.font.width(rec);
            g.drawString(this.font, Component.literal(rec),
                    left + CARD_W - recW - 10, y + cardH - 13,
                    shelved ? 0xFF9A8A5A : best > 0 ? 0xFF7FD4FF : 0xFF8A8A8A, true);
        }

        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
