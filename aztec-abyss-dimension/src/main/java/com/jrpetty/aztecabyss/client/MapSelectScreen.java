package com.jrpetty.aztecabyss.client;

import com.jrpetty.aztecabyss.network.MapSelectPayload;
import com.jrpetty.aztecabyss.worldgen.ArenaMap;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * The portal's front door: right-click a lit Abyss portal and this opens.
 *
 * <h2>Layout</h2>
 *
 * <p>The two arenas sit side by side as cards - name, difficulty, the pitch,
 * and your best round there - because the choice between them is a comparison
 * and a comparison wants both things in view. Under them, the three other
 * things the portal leads to, as tiles of their own: the Maze (a different game
 * entirely, so set apart from the arena cards), the shelf of maps other players
 * have published, and the Map Creator.
 *
 * <p>Every card and tile is a real widget: Tab walks them, Enter presses the
 * focused one, the narrator reads them. Rewards, rounds and scoring are the
 * same whichever arena you pick; only the battlefield changes.
 */
public final class MapSelectScreen extends AbyssScreen {

    private static final int GAP = 8;
    private static final int LINE_H = 10;
    /** Card chrome above the pitch (title row) and below it (record row). */
    private static final int CARD_HEAD = 26;
    private static final int CARD_FOOT = 22;
    private static final int TILE_H = 34;
    /** The highest the cards may start: just under the masthead's subtitle. */
    private static final int TOP = 60;

    private final int[] bestRounds;
    private final List<String> customMaps;
    private int selected;
    private Button enterButton;

    public MapSelectScreen(int currentChoice, int[] bestRounds, List<String> customMaps) {
        super(Component.literal("Choose Your Hunt"));
        // A stored choice from a map that no longer exists snaps back to the
        // Temple, exactly as the server will when the run starts.
        this.selected = ArenaMap.byId(currentChoice).ordinal();
        this.bestRounds = bestRounds;
        this.customMaps = customMaps == null ? List.of() : customMaps;
    }

    // ------------------------------------------------------------------
    // Geometry
    // ------------------------------------------------------------------

    private int cardW() {
        return Math.min(220, (this.width - 24 - GAP) / 2);
    }

    private int rowW() {
        return cardW() * 2 + GAP;
    }

    private int rowLeft() {
        return this.width / 2 - rowW() / 2;
    }

    private int buttonsY() {
        return this.height - 30;
    }

    /**
     * Where the cards start: the cards and the tiles under them are centred,
     * together, in the room between the masthead and the buttons - never above
     * {@link #TOP}. Pinned to the top they left a third of a tall window empty
     * under them.
     */
    private int cardsY() {
        int block = cardH() + 10 + TILE_H;
        return TOP + Math.max(0, (buttonsY() - 10 - TOP - block) / 2);
    }

    /**
     * How many lines of pitch a card may show. As many as the longest pitch
     * needs, unless the window is too short - then the pitch gives way before
     * the tiles or the buttons do, because those are the things you act on.
     */
    private int blurbLines() {
        int need = 1;
        for (ArenaMap m : ArenaMap.values()) {
            need = Math.max(need, wrap(m).size());
        }
        int room = buttonsY() - 10 - TILE_H - 10 - TOP - CARD_HEAD - CARD_FOOT;
        return Math.max(0, Math.min(need, room / LINE_H));
    }

    private int cardH() {
        return CARD_HEAD + blurbLines() * LINE_H + CARD_FOOT;
    }

    private int tilesY() {
        return cardsY() + cardH() + 10;
    }

    private List<FormattedCharSequence> wrap(ArenaMap map) {
        return this.font.split(Component.literal(map.blurb()), cardW() - 20);
    }

    // ------------------------------------------------------------------
    // Widgets
    // ------------------------------------------------------------------

    @Override
    protected void initWidgets() {
        ArenaMap[] maps = ArenaMap.values();
        int left = rowLeft();
        int w = cardW();
        int h = cardH();
        for (int i = 0; i < maps.length; i++) {
            final int index = i;
            ArenaMap map = maps[i];
            addRenderableWidget(new TileButton(left + i * (w + GAP), cardsY(), w, h,
                    Component.literal(map.title()),
                    (g, tile, hot) -> paintCard(g, tile, map, index, hot),
                    () -> select(index)));
        }

        // The other three doors. The Maze is set apart from the arena cards on
        // purpose: no rounds, no rewards, nothing in common with them.
        int tileW = (rowW() - GAP * 2) / 3;
        int ty = tilesY();
        String shelf = customMaps.isEmpty() ? "None published yet"
                : customMaps.size() + (customMaps.size() == 1 ? " map" : " maps") + " on the portal";
        addRenderableWidget(new TileButton(left, ty, tileW, TILE_H, Component.literal("The Maze"),
                (g, tile, hot) -> paintTile(g, tile, hot, new ItemStack(Items.FILLED_MAP),
                        "The Maze", "A different game", UiKit.PURPLE),
                () -> {
                    PacketDistributor.sendToServer(new MapSelectPayload(MapSelectPayload.MAZE));
                    onClose();
                }));
        addRenderableWidget(new TileButton(left + tileW + GAP, ty, tileW, TILE_H,
                Component.literal("Player maps"),
                (g, tile, hot) -> paintTile(g, tile, hot, new ItemStack(Items.WRITABLE_BOOK),
                        "Player Maps", shelf, 0xFFE08FE0),
                () -> {
                    if (minecraft != null) {
                        minecraft.setScreen(new PlayerMapsScreen(customMaps, this));
                    }
                }));
        // Listed rather than left to be discovered: a mode you can reach only
        // by knowing a command to type is not really offered.
        addRenderableWidget(new TileButton(left + (tileW + GAP) * 2, ty, rowW() - (tileW + GAP) * 2, TILE_H,
                Component.literal("Map Creator"),
                (g, tile, hot) -> paintTile(g, tile, hot, new ItemStack(Items.BLAZE_ROD),
                        "Map Creator", "Build your own", UiKit.CYAN),
                () -> {
                    PacketDistributor.sendToServer(new MapSelectPayload(MapSelectPayload.CREATOR));
                    onClose();
                }));

        // Named after what it will actually do, so nobody glances back up to
        // check which card is lit.
        int by = buttonsY();
        int recordsW = 96;
        enterButton = Button.builder(enterLabel(), b -> {
                    PacketDistributor.sendToServer(new MapSelectPayload(selected));
                    onClose();
                })
                .bounds(left, by, rowW() - recordsW - GAP, 20)
                .build();
        addRenderableWidget(enterButton);
        // Records live one press from the place everybody stands before every
        // run - the only moment anyone actually wants to know what the record is.
        addRenderableWidget(Button.builder(Component.literal("Records"), b ->
                        PacketDistributor.sendToServer(
                                new com.jrpetty.aztecabyss.network.RequestLeaderboardPayload(0)))
                .bounds(left + rowW() - recordsW, by, recordsW, 20)
                .build());
    }

    private Component enterLabel() {
        return Component.literal("Enter — " + ArenaMap.values()[selected].title());
    }

    private void select(int index) {
        selected = index;
        if (enterButton != null) {
            enterButton.setMessage(enterLabel());
        }
    }

    // ------------------------------------------------------------------
    // Painting
    // ------------------------------------------------------------------

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int cx = this.width / 2;
        int y = UiKit.masthead(g, this.font, "THE ABYSS PORTAL", "CHOOSE YOUR HUNT", cx, 8, UiKit.GOLD);
        UiKit.fret(g, cx, y + 1, Math.min(150, rowW() / 2), UiKit.GOLD);
        g.drawCenteredString(this.font, Component.literal(
                "Two battlefields, a maze — or build your own."), cx, y + 8, UiKit.TEXT_DIM);
    }

    private void paintCard(GuiGraphics g, TileButton tile, ArenaMap map, int index, boolean hot) {
        int x = tile.getX();
        int y = tile.getY();
        int w = tile.getWidth();
        int h = tile.getHeight();
        boolean isSelected = index == selected;
        int accent = map.difficultyColor();

        int fill = isSelected ? UiKit.lerp(UiKit.PANEL, accent, 0.10f) : hot ? UiKit.PANEL_HOT : UiKit.PANEL;
        int edge = isSelected ? accent : hot ? UiKit.EDGE_HOT : UiKit.EDGE;
        UiKit.panel(g, x, y, w, h, fill, edge);
        // A cap in the map's colour: full strength on the chosen card, a hint
        // of it on the other, so the pair reads as a pair.
        g.fill(x + 1, y + 1, x + w - 1, y + 3, isSelected ? accent : UiKit.alpha(accent, 0x55));
        if (isSelected) {
            g.fill(x + 1, y + 3, x + 3, y + h - 1, accent);
        }

        g.renderItem(icon(map), x + 8, y + 7);
        g.drawString(this.font, Component.literal(map.title()).withStyle(s -> s.withBold(true)),
                x + 28, y + 11, isSelected ? 0xFFFFF0C8 : UiKit.TEXT, true);
        String diff = map.difficulty();
        UiKit.tag(g, this.font, diff, x + w - UiKit.tagWidth(this.font, diff) - 8, y + 9, accent);

        int lines = blurbLines();
        int by = y + CARD_HEAD + 2;
        List<FormattedCharSequence> wrapped = wrap(map);
        for (int i = 0; i < Math.min(lines, wrapped.size()); i++) {
            g.drawString(this.font, wrapped.get(i), x + 10, by, UiKit.TEXT_DIM, true);
            by += LINE_H;
        }

        int footY = y + h - 15;
        UiKit.rule(g, x + 8, x + w - 8, footY - 5, UiKit.alpha(UiKit.EDGE_HOT, 0x80));
        g.drawString(this.font, isSelected ? "◆ Chosen" : hot ? "Click to choose" : "",
                x + 10, footY, isSelected ? accent : UiKit.TEXT_FAINT, true);
        int best = index < bestRounds.length ? bestRounds[index] : 0;
        String rec = best > 0 ? "Your best: Round " + best : "Never attempted";
        g.drawString(this.font, rec, x + w - 10 - this.font.width(rec), footY,
                best > 0 ? UiKit.CYAN : UiKit.TEXT_FAINT, true);
    }

    private void paintTile(GuiGraphics g, TileButton tile, boolean hot, ItemStack icon,
                           String title, String sub, int accent) {
        int x = tile.getX();
        int y = tile.getY();
        int w = tile.getWidth();
        int h = tile.getHeight();
        UiKit.panel(g, x, y, w, h, hot ? UiKit.PANEL_HOT : UiKit.PANEL_DEEP, hot ? accent : UiKit.EDGE);
        g.fill(x + 1, y + 1, x + 3, y + h - 1, hot ? accent : UiKit.alpha(accent, 0x90));
        g.renderItem(icon, x + 8, y + (h - 16) / 2);
        int tx = x + 28;
        int room = w - 28 - 6;
        g.drawString(this.font, this.font.plainSubstrByWidth(title, room), tx, y + 7, accent, true);
        g.drawString(this.font, this.font.plainSubstrByWidth(sub, room), tx, y + 19,
                hot ? UiKit.TEXT : UiKit.TEXT_DIM, true);
    }

    private static ItemStack icon(ArenaMap map) {
        return map == ArenaMap.BRIDGE
                ? new ItemStack(Items.HEART_OF_THE_SEA)
                : new ItemStack(Items.ZOMBIE_HEAD);
    }
}
