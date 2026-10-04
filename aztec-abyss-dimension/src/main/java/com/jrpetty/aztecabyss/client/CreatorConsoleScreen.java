package com.jrpetty.aztecabyss.client;

import com.jrpetty.aztecabyss.engine.BuildTools;
import com.jrpetty.aztecabyss.network.CreatorActionPayload;
import com.jrpetty.aztecabyss.network.CreatorConsolePayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The Creator Console: the Map Creator's whole workflow on three pages.
 *
 * <p><b>Build</b> - what the wand has marked out, a button to check it, and a
 * button to play it under any ruleset on the server. <b>Publish</b> - save the
 * marked-out area as a map, give it a title, a pitch, a difficulty and a
 * ruleset, and put it on the portal (or take it off). <b>Markers</b> - every
 * marker the engine reads, one click to put its sign in your hand.
 *
 * <p>Opened by right-clicking the air with the Map Wand. Every button asks the
 * server, which runs the same code the old {@code /arena} commands ran and
 * sends the console back - so what this screen shows is always what the
 * server thinks, and an author who is not an operator can do everything an
 * operator could.
 */
public final class CreatorConsoleScreen extends AbyssScreen {

    private enum Tab { BUILD, PUBLISH, MARKERS }

    private static final int ACCENT = UiKit.CYAN;
    private static final int CONTENT_TOP = 84;
    private static final int ROW_H = 14;

    /** What each marker is for, in a line, for the Markers page. */
    private static final Map<String, String> NOTES = Map.ofEntries(
            Map.entry("spawn", "Where players start."),
            Map.entry("extract", "A way out that ends the run as a win."),
            Map.entry("horde", "Where the horde comes in."),
            Map.entry("pen", "A hidden room the horde spawns in, out of sight."),
            Map.entry("boss", "Where a boss appears."),
            Map.entry("loot", "A chest that is refilled for every run."),
            Map.entry("powerup", "Somewhere power-ups can drop."),
            Map.entry("dealer", "A sign players buy from."),
            Map.entry("door", "A door that opens for a price."),
            Map.entry("box", "A mystery box."),
            Map.entry("perk", "A perk machine."),
            Map.entry("upgrade", "An upgrade station for held weapons."),
            Map.entry("zone", "An area with an effect on whoever stands in it."),
            Map.entry("spawner", "A point that spawns one kind of mob."),
            Map.entry("trap", "A trap players pay to set off."),
            Map.entry("teleport", "A pad linked to another with the same id."),
            Map.entry("objective", "Something to defend, or to take."),
            Map.entry("region", "A named area the rules can watch."));

    private CreatorConsolePayload data;
    private Tab tab = Tab.BUILD;
    private int ruleIndex = 0;
    private String selectedMap = "";
    private String nameDraft = "";
    private String titleDraft;
    private String blurbDraft;
    private int listScroll = 0;
    private String hoveredMarker;

    public CreatorConsoleScreen(CreatorConsolePayload data) {
        super(Component.literal("Creator Console"));
        this.data = data;
    }

    /** A fresh copy from the server: same page, same selection, same typing. */
    public void accept(CreatorConsolePayload fresh) {
        this.data = fresh;
        rebuild();
    }

    @Override
    protected int glow() {
        return UiKit.alpha(ACCENT, 0x22);
    }

    // ------------------------------------------------------------------
    // Data
    // ------------------------------------------------------------------

    private List<String[]> maps() {
        List<String[]> out = new ArrayList<>();
        for (String row : data.maps()) {
            String[] f = new String[7];
            for (int i = 0; i < 7; i++) {
                f[i] = CreatorConsolePayload.field(row, i);
            }
            out.add(f);
        }
        return out;
    }

    private String[] selectedRow() {
        for (String[] m : maps()) {
            if (m[0].equals(selectedMap)) {
                return m;
            }
        }
        return null;
    }

    private List<String[]> rules() {
        List<String[]> out = new ArrayList<>();
        for (String row : data.rulesets()) {
            out.add(new String[]{CreatorConsolePayload.field(row, 0), CreatorConsolePayload.field(row, 1)});
        }
        if (out.isEmpty()) {
            out.add(new String[]{"built-in", "Built-in rules"});
        }
        return out;
    }

    private String ruleTitle(String id) {
        for (String[] r : rules()) {
            if (r[0].equals(id)) {
                return r[1];
            }
        }
        return id;
    }

    private boolean hasSelection() {
        return !data.selection().isEmpty();
    }

    private int contentW() {
        return Math.min(420, this.width - 32);
    }

    private int left() {
        return this.width / 2 - contentW() / 2;
    }

    private int bottom() {
        return this.height - 36;
    }

    private void send(String action, String a, String b) {
        PacketDistributor.sendToServer(new CreatorActionPayload(action, a, b));
    }

    // ------------------------------------------------------------------
    // Widgets
    // ------------------------------------------------------------------

    private void rebuild() {
        this.clearWidgets();
        this.init();
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        tabButton(cx - 138, "Build", Tab.BUILD);
        tabButton(cx - 45, "Publish", Tab.PUBLISH);
        tabButton(cx + 48, "Markers", Tab.MARKERS);

        addRenderableWidget(Button.builder(Component.literal("Leave the Workshop"), b -> {
                    send("leave", "", "");
                    onClose();
                })
                .bounds(cx - 152, this.height - 28, 150, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose())
                .bounds(cx + 2, this.height - 28, 150, 20).build());

        switch (tab) {
            case BUILD -> initBuild();
            case PUBLISH -> initPublish();
            case MARKERS -> initMarkers();
        }
    }

    private void tabButton(int x, String label, Tab target) {
        Button b = Button.builder(Component.literal(label), btn -> {
            tab = target;
            rebuild();
        }).bounds(x, 44, 90, 20).build();
        b.active = tab != target;
        addRenderableWidget(b);
    }

    private void initBuild() {
        int w = contentW();
        int x = left();
        int y = CONTENT_TOP + 58;
        Button check = Button.builder(Component.literal("Check the map"), b -> send("check", "", ""))
                .bounds(x, y, 112, 20).build();
        check.active = hasSelection();
        addRenderableWidget(check);

        List<String[]> rules = rules();
        ruleIndex = Math.floorMod(ruleIndex, rules.size());
        String[] rule = rules.get(ruleIndex);
        addRenderableWidget(Button.builder(Component.literal(
                                "Rules: " + this.font.plainSubstrByWidth(rule[1], w - 112 - 112 - 40) + " ▸"),
                        b -> {
                            ruleIndex++;
                            rebuild();
                        })
                .bounds(x + 116, y, w - 116 - 108, 20).build());

        if (data.testing()) {
            addRenderableWidget(Button.builder(Component.literal("Stop the test"), b -> send("stop", "", ""))
                    .bounds(x + w - 104, y, 104, 20).build());
        } else {
            Button test = Button.builder(Component.literal("Play-test ▸"), b -> {
                        send("test", rule[0], "");
                        onClose();
                    })
                    .bounds(x + w - 104, y, 104, 20).build();
            test.active = hasSelection();
            addRenderableWidget(test);
        }
    }

    private void initPublish() {
        int x = left();
        int colLW = Math.min(190, contentW() / 2 - 6);
        int colR = x + colLW + 12;
        int colRW = contentW() - colLW - 12;

        EditBox name = new EditBox(this.font, x, CONTENT_TOP + 12, colLW - 58, 18, Component.literal("Map name"));
        name.setMaxLength(32);
        name.setFilter(s -> s.matches("[a-z0-9_-]*"));
        name.setValue(nameDraft);
        name.setResponder(s -> nameDraft = s);
        name.setHint(Component.literal("my_map"));
        addRenderableWidget(name);
        Button save = Button.builder(Component.literal("Save"), b -> {
                    // What you just saved is what you want to describe next, so
                    // its details open as soon as the server confirms it.
                    selectedMap = nameDraft;
                    titleDraft = null;
                    blurbDraft = null;
                    send("save", nameDraft, "");
                })
                .bounds(x + colLW - 54, CONTENT_TOP + 11, 54, 20).build();
        save.active = hasSelection();
        addRenderableWidget(save);

        String[] row = selectedRow();
        if (row == null) {
            return;
        }
        if (titleDraft == null) {
            titleDraft = row[1];
        }
        if (blurbDraft == null) {
            blurbDraft = row[5];
        }
        EditBox title = new EditBox(this.font, colR, CONTENT_TOP + 12, colRW, 18, Component.literal("Title"));
        title.setMaxLength(40);
        title.setValue(titleDraft);
        title.setResponder(s -> titleDraft = s);
        addRenderableWidget(title);
        EditBox blurb = new EditBox(this.font, colR, CONTENT_TOP + 46, colRW, 18, Component.literal("Pitch"));
        blurb.setMaxLength(160);
        blurb.setValue(blurbDraft);
        blurb.setResponder(s -> blurbDraft = s);
        addRenderableWidget(blurb);

        int half = colRW / 2 - 2;
        String nextDiff = switch (row[3]) {
            case "EASY" -> "MEDIUM";
            case "MEDIUM" -> "HARD";
            case "HARD" -> "BRUTAL";
            default -> "EASY";
        };
        addRenderableWidget(Button.builder(Component.literal("Difficulty: " + row[3] + " ▸"),
                        b -> send("meta", row[0], "difficulty=" + nextDiff))
                .bounds(colR, CONTENT_TOP + 70, half, 20).build());
        List<String[]> rules = rules();
        int at = 0;
        for (int i = 0; i < rules.size(); i++) {
            if (rules.get(i)[0].equals(row[4])) {
                at = i;
            }
        }
        String nextRule = rules.get((at + 1) % rules.size())[0];
        addRenderableWidget(Button.builder(Component.literal(
                                this.font.plainSubstrByWidth("Rules: " + ruleTitle(row[4]), half - 16) + " ▸"),
                        b -> send("meta", row[0], "ruleset=" + nextRule))
                .bounds(colR + half + 4, CONTENT_TOP + 70, half, 20).build());

        // Always pressable: it sends only what has changed, so pressing it with
        // nothing changed simply does nothing.
        addRenderableWidget(Button.builder(Component.literal("Save title & pitch"), b -> {
                    if (!titleDraft.equals(row[1])) {
                        send("meta", row[0], "title=" + titleDraft);
                    }
                    if (!blurbDraft.equals(row[5])) {
                        send("meta", row[0], "blurb=" + blurbDraft);
                    }
                })
                .bounds(colR, CONTENT_TOP + 94, half, 20).build());
        boolean live = row[6].equals("1");
        addRenderableWidget(Button.builder(Component.literal(live ? "Update the portal" : "Publish ▸"),
                        b -> send("publish", row[0], ""))
                .bounds(colR + half + 4, CONTENT_TOP + 94, half, 20).build());
        if (live) {
            addRenderableWidget(Button.builder(Component.literal("Take it off the portal"),
                            b -> send("unpublish", row[0], ""))
                    .bounds(colR, CONTENT_TOP + 118, colRW, 20).build());
        }
    }

    private void initMarkers() {
        int w = contentW();
        int x = left();
        int cols = Math.max(2, Math.min(6, (w + 6) / 96));
        int tileW = (w - (cols - 1) * 6) / cols;
        String[] kinds = BuildTools.KINDS;
        for (int i = 0; i < kinds.length; i++) {
            String kind = kinds[i];
            int tx = x + (i % cols) * (tileW + 6);
            int ty = CONTENT_TOP + 6 + (i / cols) * 26;
            addRenderableWidget(new TileButton(tx, ty, tileW, 22, Component.literal("[" + kind + "]"),
                    (g, tile, hot) -> {
                        if (hot) {
                            hoveredMarker = kind;
                        }
                        UiKit.panel(g, tile.getX(), tile.getY(), tile.getWidth(), tile.getHeight(),
                                hot ? UiKit.PANEL_HOT : UiKit.PANEL, hot ? ACCENT : UiKit.EDGE);
                        g.drawCenteredString(this.font, "[" + kind + "]",
                                tile.getX() + tile.getWidth() / 2, tile.getY() + 7, hot ? ACCENT : UiKit.TEXT);
                    },
                    () -> send("marker", kind, "")));
        }
        int rows = (kinds.length + cols - 1) / cols;
        addRenderableWidget(Button.builder(Component.literal("A fresh Map Wand"), b -> send("wand", "", ""))
                .bounds(x, CONTENT_TOP + 10 + rows * 26, 130, 20).build());
    }

    // ------------------------------------------------------------------
    // Input
    // ------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (tab == Tab.PUBLISH) {
            int x = left();
            int colLW = Math.min(190, contentW() / 2 - 6);
            int top = listTop();
            List<String[]> maps = maps();
            if (mouseX >= x && mouseX <= x + colLW && mouseY >= top && mouseY < listBottom()) {
                int i = (int) ((mouseY - top) / ROW_H) + listScroll;
                if (i >= 0 && i < maps.size()) {
                    selectedMap = maps.get(i)[0];
                    titleDraft = null;
                    blurbDraft = null;
                    rebuild();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double dx, double dy) {
        if (tab == Tab.PUBLISH) {
            int fit = Math.max(1, (listBottom() - listTop()) / ROW_H);
            listScroll = Math.max(0, Math.min(Math.max(0, maps().size() - fit), listScroll - (int) Math.signum(dy)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, dx, dy);
    }

    private int listTop() {
        return CONTENT_TOP + 52;
    }

    private int listBottom() {
        return bottom() - 4;
    }

    // ------------------------------------------------------------------
    // Painting
    // ------------------------------------------------------------------

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int cx = this.width / 2;
        UiKit.masthead(g, this.font, "THE WORKSHOP", "CREATOR CONSOLE", cx, 8, ACCENT);
        if (!data.status().isEmpty()) {
            g.drawCenteredString(this.font, this.font.plainSubstrByWidth(data.status(), this.width - 24),
                    cx, 70, UiKit.TEXT);
        }
        switch (tab) {
            case BUILD -> renderBuild(g);
            case PUBLISH -> renderPublish(g, mouseX, mouseY);
            case MARKERS -> {
            }
        }
    }

    private void renderBuild(GuiGraphics g) {
        int w = contentW();
        int x = left();
        UiKit.panel(g, x, CONTENT_TOP, w, 50);
        g.fill(x + 1, CONTENT_TOP + 1, x + 3, CONTENT_TOP + 49, ACCENT);
        g.drawString(this.font, "THE MAP", x + 10, CONTENT_TOP + 7, UiKit.TEXT_FAINT, true);
        if (!hasSelection()) {
            g.drawString(this.font, "Nothing marked out yet.", x + 10, CONTENT_TOP + 20, UiKit.TEXT, true);
            g.drawString(this.font, this.font.plainSubstrByWidth(
                            "Left-click one corner with the Map Wand, right-click the opposite one.", w - 20),
                    x + 10, CONTENT_TOP + 32, UiKit.TEXT_DIM, true);
        } else {
            String span = CreatorConsolePayload.field(data.selection(), 0);
            String volume = CreatorConsolePayload.field(data.selection(), 1);
            g.drawString(this.font, "Marked out: " + span, x + 10, CONTENT_TOP + 20, UiKit.TEXT, true);
            g.drawString(this.font, volume + " blocks · check it, then play it or save it",
                    x + 10, CONTENT_TOP + 32, UiKit.TEXT_DIM, true);
        }

        int top = CONTENT_TOP + 86;
        int h = bottom() - top;
        if (h < 24) {
            return;
        }
        UiKit.panel(g, x, top, w, h, UiKit.PANEL_DEEP, UiKit.EDGE);
        g.drawString(this.font, "WHAT THE CHECK FOUND", x + 10, top + 7, UiKit.TEXT_FAINT, true);
        int y = top + 20;
        if (data.problems().isEmpty()) {
            boolean clean = data.status().contains("✔");
            g.drawString(this.font, clean ? "Nothing to fix. It plays." : "Press Check the map to scan the marked-out area.",
                    x + 10, y, clean ? UiKit.GREEN : UiKit.TEXT_DIM, true);
            return;
        }
        for (String problem : data.problems()) {
            for (FormattedCharSequence line : this.font.split(Component.literal(problem), w - 20)) {
                if (y + 10 > top + h - 4) {
                    g.drawString(this.font, "…", x + 10, y - 2, UiKit.TEXT_FAINT, true);
                    return;
                }
                g.drawString(this.font, line, x + 10, y, UiKit.TEXT_DIM, true);
                y += 10;
            }
            y += 2;
        }
    }

    private void renderPublish(GuiGraphics g, int mouseX, int mouseY) {
        int x = left();
        int colLW = Math.min(190, contentW() / 2 - 6);
        int colR = x + colLW + 12;
        int colRW = contentW() - colLW - 12;

        g.drawString(this.font, "SAVE THE MARKED-OUT MAP AS", x, CONTENT_TOP, UiKit.TEXT_FAINT, true);
        if (!hasSelection()) {
            g.drawString(this.font, "mark the map out with the wand first", x, CONTENT_TOP + 33,
                    UiKit.TEXT_FAINT, true);
        }

        g.drawString(this.font, "SAVED MAPS", x, listTop() - 11, UiKit.TEXT_FAINT, true);
        int top = listTop();
        int bottom = listBottom();
        UiKit.panel(g, x, top, colLW, bottom - top, UiKit.PANEL_DEEP, UiKit.EDGE);
        List<String[]> maps = maps();
        if (maps.isEmpty()) {
            g.drawString(this.font, "none yet", x + 8, top + 6, UiKit.TEXT_FAINT, true);
        }
        int fit = Math.max(1, (bottom - top) / ROW_H);
        for (int i = 0; i < fit && i + listScroll < maps.size(); i++) {
            String[] m = maps.get(i + listScroll);
            int ry = top + i * ROW_H;
            boolean selected = m[0].equals(selectedMap);
            boolean hot = mouseX >= x && mouseX <= x + colLW && mouseY >= ry && mouseY < ry + ROW_H;
            if (selected || hot) {
                g.fill(x + 1, ry + 1, x + colLW - 1, ry + ROW_H, selected ? UiKit.alpha(ACCENT, 0x40) : UiKit.PANEL_HOT);
            }
            boolean live = m[6].equals("1");
            String badge = live ? "● live" : "v" + m[2];
            int badgeW = this.font.width(badge);
            g.drawString(this.font, this.font.plainSubstrByWidth(m[1].isEmpty() ? m[0] : m[1], colLW - badgeW - 18),
                    x + 6, ry + 3, selected ? UiKit.TEXT : UiKit.TEXT_DIM, true);
            g.drawString(this.font, badge, x + colLW - 6 - badgeW, ry + 3,
                    live ? UiKit.GREEN : UiKit.TEXT_FAINT, true);
        }

        String[] row = selectedRow();
        if (row == null) {
            List<FormattedCharSequence> hint = this.font.split(Component.literal(maps.isEmpty()
                    ? "Mark a map out with the wand, give it a name on the left and save it. "
                    + "Its title, pitch, difficulty and rules are set here, and Publish puts it on the portal."
                    : "Pick a saved map on the left to set its details and put it on the portal."), colRW);
            int y = CONTENT_TOP + 12;
            for (FormattedCharSequence line : hint) {
                g.drawString(this.font, line, colR, y, UiKit.TEXT_DIM, true);
                y += 10;
            }
            return;
        }
        g.drawString(this.font, "TITLE", colR, CONTENT_TOP, UiKit.TEXT_FAINT, true);
        g.drawString(this.font, "PITCH", colR, CONTENT_TOP + 34, UiKit.TEXT_FAINT, true);
        String id = row[0] + " · v" + row[2];
        g.drawString(this.font, id, colR + colRW - this.font.width(id), CONTENT_TOP, UiKit.TEXT_MUTED, true);
    }

    @Override
    protected void renderOverlay(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (tab != Tab.MARKERS) {
            return;
        }
        String kind = hoveredMarker;
        hoveredMarker = null;
        int y = bottom() - 22;
        if (kind == null) {
            g.drawCenteredString(this.font, "Click a marker to put its sign in your hand. Place it, then edit it by hand.",
                    this.width / 2, y, UiKit.TEXT_FAINT);
            return;
        }
        String hint = BuildTools.hintFor(kind);
        g.drawCenteredString(this.font, Component.literal("§b[" + kind + "]§r  " + NOTES.getOrDefault(kind, "")),
                this.width / 2, y, UiKit.TEXT);
        if (!hint.isEmpty()) {
            g.drawCenteredString(this.font, "second line, for example: " + hint, this.width / 2, y + 11, UiKit.TEXT_DIM);
        }
    }
}
