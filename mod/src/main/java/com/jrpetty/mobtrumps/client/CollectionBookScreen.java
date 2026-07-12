package com.jrpetty.mobtrumps.client;

import com.jrpetty.mobtrumps.MobCardItem;
import com.jrpetty.mobtrumps.game.MobCard;
import com.jrpetty.mobtrumps.game.MobCards;
import com.jrpetty.mobtrumps.game.Stat;
import com.jrpetty.mobtrumps.game.Tier;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The collection binder: pages of full Top Trumps cards with search, filter,
 * sort and a stats page. Collected mobs show their card (foil sheen if owned
 * as foil); unfound mobs lie face down.
 */
public class CollectionBookScreen extends Screen {

    private static final float CARD_SCALE = 0.52f;
    private static final int ARROW_W = 18, ARROW_H = 14;

    private enum Filter { ALL("All"), OWNED("Owned"), MISSING("Missing"), FOIL("Foil");
        final String label; Filter(String l) { label = l; } }
    private enum Sort { NUMBER("No."), NAME("Name"), TIER("Tier"), RATING("Rating");
        final String label; Sort(String l) { label = l; } }

    private final Map<String, LivingEntity> entityCache = new HashMap<>();
    private final List<MobCard> view = new ArrayList<>();
    private final List<Chip> chips = new ArrayList<>();

    private int page;
    private Filter filter = Filter.ALL;
    private Sort sort = Sort.NUMBER;
    private boolean statsOpen = false;
    private EditBox search;

    private int cols, rows, perPage, pageCount;
    private int cellW, cellH, gridX, gridY;
    private int panelX, panelY, panelW, panelH;
    private int prevX, prevY, nextX, nextY;

    private record Chip(String key, int x, int y, int w, int h) {
        boolean hit(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    public CollectionBookScreen() {
        super(Component.literal("Mob Collection"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        super.init();
        cellW = Math.round(CardRenderer.CARD_W * CARD_SCALE) + 12;
        cellH = Math.round(CardRenderer.CARD_H * CARD_SCALE) + 12;
        cols = Math.max(1, Math.min(4, (width - 70) / cellW));
        rows = Math.max(1, Math.min(3, (height - 130) / cellH));
        perPage = cols * rows;

        panelW = Math.max(cols * cellW + 28, 330);
        int headerH = 78;
        panelH = headerH + rows * cellH + 24;
        panelX = (width - panelW) / 2;
        panelY = Math.max(8, (height - panelH) / 2);
        gridX = panelX + (panelW - cols * cellW) / 2;
        gridY = panelY + headerH;

        int arrowY = panelY + panelH - 20;
        prevX = panelX + 12;
        prevY = arrowY;
        nextX = panelX + panelW - 12 - ARROW_W;
        nextY = arrowY;

        // search box centred under the progress bar
        int sbW = 120, sbX = panelX + (panelW - sbW) / 2, sbY = panelY + 44;
        search = new EditBox(font, sbX, sbY, sbW, 12, Component.literal("Search"));
        search.setMaxLength(24);
        search.setBordered(true);
        search.setHint(Component.literal("search mobs..."));
        search.setResponder(s -> { page = 0; rebuild(); });
        addWidget(search);

        layoutChips();
        rebuild();
    }

    private void layoutChips() {
        chips.clear();
        int y = panelY + 60;
        int x = panelX + 12;
        for (Filter f : Filter.values()) {
            int w = font.width(f.label) + 8;
            chips.add(new Chip("f_" + f.name(), x, y, w, 12));
            x += w + 3;
        }
        // right-aligned: stats then sort
        String statsLabel = "Stats";
        int statsW = font.width(statsLabel) + 8;
        int statsX = panelX + panelW - 12 - statsW;
        chips.add(new Chip("stats", statsX, y, statsW, 12));
        String sortLabel = "Sort: " + sort.label;
        int sortW = font.width(sortLabel) + 8;
        chips.add(new Chip("sort", statsX - sortW - 4, y, sortW, 12));
    }

    private void rebuild() {
        String q = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
        view.clear();
        for (MobCard c : MobCards.ALL) {
            boolean owned = ClientCollection.has(c.id());
            boolean foil = ClientCollection.hasFoil(c.id());
            boolean pass = switch (filter) {
                case ALL -> true;
                case OWNED -> owned;
                case MISSING -> !owned;
                case FOIL -> foil;
            };
            if (pass && (q.isEmpty() || c.displayName().toLowerCase(Locale.ROOT).contains(q))) {
                view.add(c);
            }
        }
        view.sort((a, b) -> switch (sort) {
            case NUMBER -> Integer.compare(MobCards.ordinal(a.id()), MobCards.ordinal(b.id()));
            case NAME -> a.displayName().compareTo(b.displayName());
            case TIER -> Integer.compare(b.tier().ordinal(), a.tier().ordinal()) != 0
                    ? Integer.compare(b.tier().ordinal(), a.tier().ordinal())
                    : a.displayName().compareTo(b.displayName());
            case RATING -> Integer.compare(rating(b), rating(a)) != 0
                    ? Integer.compare(rating(b), rating(a))
                    : a.displayName().compareTo(b.displayName());
        });
        pageCount = Math.max(1, (view.size() + perPage - 1) / perPage);
        page = Math.max(0, Math.min(page, pageCount - 1));
    }

    private static int rating(MobCard c) {
        int t = 0;
        for (Stat s : Stat.values()) t += c.stat(s);
        return t;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);

        g.fill(panelX - 3, panelY - 3, panelX + panelW + 3, panelY + panelH + 3, CardRenderer.KRAFT_DARK);
        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, CardRenderer.KRAFT);
        g.fill(panelX + 6, panelY + 6, panelX + panelW - 6, panelY + panelH - 6, CardRenderer.FACE);
        for (int[] c : new int[][]{{panelX + 3, panelY + 3}, {panelX + panelW - 6, panelY + 3},
                {panelX + 3, panelY + panelH - 6}, {panelX + panelW - 6, panelY + panelH - 6}}) {
            g.fill(c[0], c[1], c[0] + 3, c[1] + 3, 0xFFF3E2A7);
        }

        var pose = g.pose();
        pose.pushPose();
        pose.translate(panelX + panelW / 2f, panelY + 12f, 0);
        pose.scale(1.4f, 1.4f, 1f);
        String title = "MOB COLLECTION";
        g.drawString(font, title, -font.width(title) / 2, 0, CardRenderer.INK, false);
        pose.popPose();

        int total = MobCards.ALL.size();
        int have = ClientCollection.count();
        int foils = ClientCollection.foilCount();
        String progress = have + " / " + total + (foils > 0 ? "   ✦ " + foils : "");
        g.drawString(font, progress, panelX + (panelW - font.width(progress)) / 2,
                panelY + 27, CardRenderer.KRAFT_DARK, false);

        int barX = panelX + 14, barY = panelY + 38, barW = panelW - 28;
        g.fill(barX - 1, barY - 1, barX + barW + 1, barY + 4, CardRenderer.KRAFT_DARK);
        g.fill(barX, barY, barX + barW, barY + 3, 0xFF8A755A);
        int fillW = (int) (barW * (have / (float) total));
        if (fillW > 0) g.fill(barX, barY, barX + fillW, barY + 3, 0xFF55A82F);

        search.render(g, mouseX, mouseY, partialTick);
        renderChips(g, mouseX, mouseY);

        // cards of this page
        int start = page * perPage;
        int cw = Math.round(CardRenderer.CARD_W * CARD_SCALE);
        int ch = Math.round(CardRenderer.CARD_H * CARD_SCALE);
        if (view.isEmpty()) {
            String none = "No cards match.";
            g.drawString(font, none, panelX + (panelW - font.width(none)) / 2, gridY + 20,
                    CardRenderer.KRAFT_DARK, false);
        }
        for (int i = start; i < Math.min(start + perPage, view.size()); i++) {
            MobCard card = view.get(i);
            int slot = i - start;
            int cx = gridX + (slot % cols) * cellW + 8;
            int cy = gridY + (slot / cols) * cellH + 8;
            g.fill(cx + 2, cy + 3, cx + cw + 4, cy + ch + 5, 0x44000000);
            boolean hovered = mouseX >= cx && mouseX < cx + cw && mouseY >= cy && mouseY < cy + ch;
            if (ClientCollection.has(card.id())) {
                LivingEntity mob = CardRenderer.portraitEntity(minecraft, card, entityCache);
                CardRenderer.renderCard(g, font, card, cx, cy, CARD_SCALE, mouseX, mouseY,
                        mob, ClientCollection.hasFoil(card.id()));
                if (hovered) g.renderOutline(cx - 2, cy - 2, cw + 4, ch + 4, 0xFFF9D849);
            } else {
                CardRenderer.renderBack(g, font, cx, cy, CARD_SCALE);
                if (hovered) g.renderOutline(cx - 2, cy - 2, cw + 4, ch + 4, 0x66FFFFFF);
            }
        }

        drawArrow(g, prevX, prevY, "<", page > 0, mouseX, mouseY);
        drawArrow(g, nextX, nextY, ">", page < pageCount - 1, mouseX, mouseY);
        String pageText = "Page " + (page + 1) + " / " + pageCount;
        g.drawString(font, pageText, panelX + (panelW - font.width(pageText)) / 2,
                panelY + panelH - 17, CardRenderer.KRAFT_DARK, false);

        String hint = "Click a card to enlarge · scroll to flip pages";
        g.drawString(font, hint, (width - font.width(hint)) / 2, panelY + panelH + 8, 0xFFAAAAAA, true);

        if (statsOpen) renderStats(g);
    }

    private void renderChips(GuiGraphics g, int mouseX, int mouseY) {
        for (Chip chip : chips) {
            boolean active = switch (chip.key()) {
                case "stats" -> statsOpen;
                case "sort" -> false;
                default -> chip.key().equals("f_" + filter.name());
            };
            boolean hover = chip.hit(mouseX, mouseY);
            int bg = active ? 0xFF55A82F : hover ? 0xFFB99465 : CardRenderer.KRAFT;
            g.fill(chip.x(), chip.y(), chip.x() + chip.w(), chip.y() + chip.h(), bg);
            g.renderOutline(chip.x(), chip.y(), chip.w(), chip.h(), CardRenderer.KRAFT_DARK);
            String label = switch (chip.key()) {
                case "stats" -> "Stats";
                case "sort" -> "Sort: " + sort.label;
                default -> Filter.valueOf(chip.key().substring(2)).label;
            };
            g.drawString(font, label, chip.x() + 4, chip.y() + 2,
                    active ? 0xFFFFFFFF : CardRenderer.INK, false);
        }
    }

    private void renderStats(GuiGraphics g) {
        int pw = 220, ph = 150;
        int px = (width - pw) / 2, py = (height - ph) / 2;
        g.fill(0, 0, width, height, 0x99000000);
        g.fill(px - 3, py - 3, px + pw + 3, py + ph + 3, CardRenderer.KRAFT_DARK);
        g.fill(px, py, px + pw, py + ph, CardRenderer.KRAFT);
        g.fill(px + 5, py + 5, px + pw - 5, py + ph - 5, CardRenderer.FACE);

        g.drawCenteredString(font, "COLLECTION STATS", width / 2, py + 12, CardRenderer.INK);
        int total = MobCards.ALL.size();
        int have = ClientCollection.count();
        int pct = Math.round(have * 100f / total);
        int y = py + 30;
        line(g, px + 14, y, "Collected", have + " / " + total + "  (" + pct + "%)"); y += 12;
        line(g, px + 14, y, "Holographic foils", ClientCollection.foilCount() + " / " + total); y += 12;
        line(g, px + 14, y, "Duel wins", String.valueOf(ClientCollection.duelWins())); y += 16;

        for (Tier t : new Tier[]{Tier.LEGENDARY, Tier.EPIC, Tier.RARE, Tier.UNCOMMON, Tier.COMMON}) {
            int tierTotal = 0, tierHave = 0;
            for (MobCard c : MobCards.ALL) {
                if (c.tier() == t) {
                    tierTotal++;
                    if (ClientCollection.has(c.id())) tierHave++;
                }
            }
            int color = 0xFF000000 | (MobCardItem.tierColor(t).getColor() == null
                    ? 0x555555 : MobCardItem.tierColor(t).getColor());
            g.drawString(font, t.label(), px + 14, y, color, false);
            String v = tierHave + " / " + tierTotal;
            g.drawString(font, v, px + pw - 14 - font.width(v), y, CardRenderer.INK, false);
            y += 11;
        }
        g.drawCenteredString(font, "click to close", width / 2, py + ph - 12, 0xFF9A9083);
    }

    private void line(GuiGraphics g, int x, int y, String label, String value) {
        g.drawString(font, label, x, y, CardRenderer.KRAFT_DARK, false);
        g.drawString(font, value, panelXForStats(x) , y, CardRenderer.INK, false);
    }

    private int panelXForStats(int x) {
        // right column start for the stats lines
        return (width / 2) + 40;
    }

    private void drawArrow(GuiGraphics g, int x, int y, String glyph, boolean enabled,
                           int mouseX, int mouseY) {
        boolean hovered = enabled && mouseX >= x && mouseX < x + ARROW_W
                && mouseY >= y && mouseY < y + ARROW_H;
        g.fill(x, y, x + ARROW_W, y + ARROW_H, enabled
                ? (hovered ? 0xFFB99465 : CardRenderer.KRAFT) : 0xFFCEC3AF);
        g.renderOutline(x, y, ARROW_W, ARROW_H, CardRenderer.KRAFT_DARK);
        g.drawString(font, glyph, x + (ARROW_W - font.width(glyph)) / 2, y + 3,
                enabled ? CardRenderer.INK : 0xFF9A9083, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (statsOpen) {
            statsOpen = false;
            return true;
        }
        if (search.mouseClicked(mouseX, mouseY, button)) {
            setFocused(search);
            return true;
        }
        if (button == 0) {
            for (Chip chip : chips) {
                if (chip.hit(mouseX, mouseY)) {
                    onChip(chip.key());
                    return true;
                }
            }
            if (inRect(mouseX, mouseY, prevX, prevY) && page > 0) { flip(-1); return true; }
            if (inRect(mouseX, mouseY, nextX, nextY) && page < pageCount - 1) { flip(1); return true; }

            int start = page * perPage;
            int cw = Math.round(CardRenderer.CARD_W * CARD_SCALE);
            int ch = Math.round(CardRenderer.CARD_H * CARD_SCALE);
            for (int i = start; i < Math.min(start + perPage, view.size()); i++) {
                MobCard card = view.get(i);
                int slot = i - start;
                int cx = gridX + (slot % cols) * cellW + 8;
                int cy = gridY + (slot / cols) * cellH + 8;
                if (mouseX >= cx && mouseX < cx + cw && mouseY >= cy && mouseY < cy + ch
                        && ClientCollection.has(card.id()) && minecraft != null) {
                    minecraft.setScreen(new MobCardScreen(card, this, ClientCollection.hasFoil(card.id())));
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void onChip(String key) {
        if (key.equals("stats")) {
            statsOpen = true;
        } else if (key.equals("sort")) {
            sort = Sort.values()[(sort.ordinal() + 1) % Sort.values().length];
            layoutChips();
            rebuild();
        } else {
            filter = Filter.valueOf(key.substring(2));
            page = 0;
            rebuild();
        }
        clickSound();
    }

    private boolean inRect(double mx, double my, int x, int y) {
        return mx >= x && mx < x + ARROW_W && my >= y && my < y + ARROW_H;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY < 0 && page < pageCount - 1) { flip(1); return true; }
        if (scrollY > 0 && page > 0) { flip(-1); return true; }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (search.isFocused() && search.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (keyCode == 263 && page > 0) { flip(-1); return true; }
        if (keyCode == 262 && page < pageCount - 1) { flip(1); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        if (search.isFocused()) {
            return search.charTyped(c, modifiers);
        }
        return super.charTyped(c, modifiers);
    }

    private void flip(int direction) {
        page += direction;
        clickSound();
    }

    private void clickSound() {
        if (minecraft != null) {
            minecraft.getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0F));
        }
    }
}
