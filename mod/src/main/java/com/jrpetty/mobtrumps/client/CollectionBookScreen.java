package com.jrpetty.mobtrumps.client;

import com.jrpetty.mobtrumps.MobCardItem;
import com.jrpetty.mobtrumps.SetDisplayPayload;
import com.jrpetty.mobtrumps.StorageActionPayload;
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
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The collection binder as an open two-page book: a 3x3 grid of cards on each
 * page (18 per spread) with a central spine. Search / filter / sort / stats
 * carry over. Each mob shows its chosen "top of the pile" variant; clicking a
 * mob you own in more than one variant opens a picker to choose which sits on
 * top and to view it large.
 */
public class CollectionBookScreen extends Screen {

    private static final float CARD_SCALE = 0.42f;
    private static final int COLS = 3, ROWS = 3, PER_PAGE = COLS * ROWS, PER_SPREAD = 2 * PER_PAGE;
    private static final int ARROW_W = 18, ARROW_H = 14;
    private static final int SPINE = 24;

    private enum Filter { ALL("All"), OWNED("Owned"), MISSING("Missing"), FOIL("Foil");
        final String label; Filter(String l) { label = l; } }
    private enum Sort { NUMBER("No."), NAME("Name"), TIER("Tier"), RATING("Rating");
        final String label; Sort(String l) { label = l; } }

    private final Map<String, LivingEntity> entityCache = new HashMap<>();
    private final List<MobCard> view = new ArrayList<>();
    private final List<Chip> chips = new ArrayList<>();

    private int spread;
    private Filter filter = Filter.ALL;
    private Sort sort = Sort.NUMBER;
    private boolean statsOpen = false;
    private String pickerMob = null;
    private EditBox search;

    private int spreadCount;
    private int cellW, cellH, gridTop, leftGridX, rightGridX;
    private int panelX, panelY, panelW, panelH;
    private int prevX, prevY, nextX, nextY;

    private record Chip(String key, int x, int y, int w, int h) {
        boolean hit(double mx, double my) { return mx >= x && mx < x + w && my >= y && my < y + h; }
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
        cellW = Math.round(CardRenderer.CARD_W * CARD_SCALE) + 8;
        cellH = Math.round(CardRenderer.CARD_H * CARD_SCALE) + 8;

        int pageW = COLS * cellW;
        panelW = 2 * pageW + SPINE + 28;
        int headerH = 78;
        panelH = headerH + ROWS * cellH + 26;
        panelX = (width - panelW) / 2;
        panelY = Math.max(6, (height - panelH) / 2);
        gridTop = panelY + headerH;
        leftGridX = panelX + 14;
        rightGridX = panelX + 14 + pageW + SPINE;

        int arrowY = panelY + panelH - 20;
        prevX = panelX + 12;
        prevY = arrowY;
        nextX = panelX + panelW - 12 - ARROW_W;
        nextY = arrowY;

        int sbW = 120, sbX = panelX + (panelW - sbW) / 2, sbY = panelY + 44;
        search = new EditBox(font, sbX, sbY, sbW, 12, Component.literal("Search"));
        search.setMaxLength(24);
        search.setBordered(true);
        search.setHint(Component.literal("search mobs..."));
        search.setResponder(s -> { spread = 0; rebuild(); });
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
        int statsW = font.width("Stats") + 8;
        int statsX = panelX + panelW - 12 - statsW;
        chips.add(new Chip("stats", statsX, y, statsW, 12));
        String sortLabel = "Sort: " + sort.label;
        int sortW = font.width(sortLabel) + 8;
        int sortX = statsX - sortW - 4;
        chips.add(new Chip("sort", sortX, y, sortW, 12));
        int deckW = font.width("Deck") + 8;
        int deckX = sortX - deckW - 4;
        chips.add(new Chip("deck", deckX, y, deckW, 12));
        int storeW = font.width("Store") + 8;
        chips.add(new Chip("store", deckX - storeW - 4, y, storeW, 12));
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
            case TIER -> cmpTier(a, b);
            case RATING -> cmpRating(a, b);
        });
        spreadCount = Math.max(1, (view.size() + PER_SPREAD - 1) / PER_SPREAD);
        spread = Math.max(0, Math.min(spread, spreadCount - 1));
    }

    private int cmpTier(MobCard a, MobCard b) {
        int c = Integer.compare(b.tier().ordinal(), a.tier().ordinal());
        return c != 0 ? c : a.displayName().compareTo(b.displayName());
    }

    private int cmpRating(MobCard a, MobCard b) {
        int c = Integer.compare(rating(b), rating(a));
        return c != 0 ? c : a.displayName().compareTo(b.displayName());
    }

    private static int rating(MobCard c) {
        int t = 0;
        for (Stat s : Stat.values()) t += c.stat(s);
        return t;
    }

    /** Screen position of a spread slot (0..17), or null if empty. */
    private int[] slotPos(int slotInSpread) {
        boolean right = slotInSpread >= PER_PAGE;
        int idx = right ? slotInSpread - PER_PAGE : slotInSpread;
        int col = idx % COLS, row = idx / COLS;
        int gx = (right ? rightGridX : leftGridX) + col * cellW + 4;
        int gy = gridTop + row * cellH + 4;
        return new int[]{gx, gy};
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);

        // book cover + cream inner with a central spine through the card area
        g.fill(panelX - 3, panelY - 3, panelX + panelW + 3, panelY + panelH + 3, CardRenderer.KRAFT_DARK);
        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, CardRenderer.KRAFT);
        g.fill(panelX + 6, panelY + 6, panelX + panelW - 6, panelY + panelH - 6, CardRenderer.FACE);
        g.fill(panelX + panelW / 2 - 1, gridTop - 6, panelX + panelW / 2 + 1, panelY + panelH - 24,
                CardRenderer.KRAFT_DARK);

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
        g.drawString(font, progress, panelX + (panelW - font.width(progress)) / 2, panelY + 27,
                CardRenderer.KRAFT_DARK, false);
        int barX = panelX + 14, barY = panelY + 38, barW = panelW - 28;
        g.fill(barX - 1, barY - 1, barX + barW + 1, barY + 4, CardRenderer.KRAFT_DARK);
        g.fill(barX, barY, barX + barW, barY + 3, 0xFF8A755A);
        int fillW = (int) (barW * (have / (float) total));
        if (fillW > 0) g.fill(barX, barY, barX + fillW, barY + 3, 0xFF55A82F);

        search.render(g, mouseX, mouseY, partialTick);
        renderChips(g, mouseX, mouseY);

        int cw = Math.round(CardRenderer.CARD_W * CARD_SCALE);
        int ch = Math.round(CardRenderer.CARD_H * CARD_SCALE);
        int start = spread * PER_SPREAD;
        if (view.isEmpty()) {
            g.drawCenteredString(font, "No cards match.", width / 2, gridTop + 30, CardRenderer.KRAFT_DARK);
        }
        for (int s = 0; s < PER_SPREAD; s++) {
            int i = start + s;
            if (i >= view.size()) break;
            MobCard card = view.get(i);
            int[] p = slotPos(s);
            int cx = p[0], cy = p[1];
            g.fill(cx + 2, cy + 3, cx + cw + 4, cy + ch + 5, 0x44000000);
            boolean overlayOpen = pickerMob != null || statsOpen;
            boolean hovered = !overlayOpen
                    && mouseX >= cx && mouseX < cx + cw && mouseY >= cy && mouseY < cy + ch;
            if (ClientCollection.has(card.id())) {
                // while an overlay is open, skip grid mobs entirely — their 3D
                // depth writes would poke through the panel drawn on top
                LivingEntity mob = overlayOpen ? null
                        : CardRenderer.portraitEntity(minecraft, card, entityCache);
                boolean foil = ClientCollection.displayedIsFoil(card.id());
                // only the hovered card comes alive and follows the cursor
                CardRenderer.renderCard(g, font, card, cx, cy, CARD_SCALE,
                        mouseX, mouseY, mob, foil, hovered);
                // a small stack tab if more than one variant is owned
                if (ClientCollection.variantCount(card.id()) > 1) {
                    g.fill(cx + cw - 6, cy - 3, cx + cw + 2, cy + 4, CardRenderer.KRAFT_DARK);
                    g.fill(cx + cw - 8, cy - 1, cx + cw, cy + 6, 0xFFF3E2A7);
                }
                // a green "filed in binder" tab on the bottom-left corner
                if (ClientCollection.isStored(card.id())) {
                    g.fill(cx - 2, cy + ch - 4, cx + 6, cy + ch + 3, CardRenderer.KRAFT_DARK);
                    g.fill(cx - 1, cy + ch - 3, cx + 5, cy + ch + 2, 0xFF55A82F);
                }
                // how many of this mob you've collected, bottom-right
                int have = ClientCollection.killCount(card.id());
                if (have > 0) {
                    String badge = "x" + have;
                    int bw = font.width(badge);
                    int bx = cx + cw - bw - 2;
                    int by = cy + ch - 9;
                    g.fill(bx - 2, by - 1, cx + cw + 1, by + 9, 0xC0101010);
                    g.drawString(font, badge, bx, by, 0xFFF3E2A7, false);
                }
                if (hovered) {
                    g.renderOutline(cx - 2, cy - 2, cw + 4, ch + 4, 0xFFF9D849);
                    g.renderOutline(cx - 3, cy - 3, cw + 6, ch + 6, 0x66F9D849);
                }
            } else {
                CardRenderer.renderBack(g, font, cx, cy, CARD_SCALE);
                if (hovered) g.renderOutline(cx - 2, cy - 2, cw + 4, ch + 4, 0x66FFFFFF);
            }
        }

        drawArrow(g, prevX, prevY, "<", spread > 0, mouseX, mouseY);
        drawArrow(g, nextX, nextY, ">", spread < spreadCount - 1, mouseX, mouseY);
        String pageText = "Pages " + (spread * 2 + 1) + "–" + (spread * 2 + 2) + " / " + (spreadCount * 2);
        g.drawString(font, pageText, panelX + (panelW - font.width(pageText)) / 2, panelY + panelH - 17,
                CardRenderer.KRAFT_DARK, false);

        String hint = "Click a card to view it · Store files loose cards away · shift-click a green-tabbed card to take one out";
        g.drawString(font, hint, (width - font.width(hint)) / 2, panelY + panelH + 8, 0xFFAAAAAA, true);

        if (pickerMob != null) renderPicker(g, mouseX, mouseY);
        if (statsOpen) renderStats(g);
    }

    // --- variant picker ---

    private void renderPicker(GuiGraphics g, int mouseX, int mouseY) {
        MobCard card = MobCards.byId(pickerMob);
        if (card == null) { pickerMob = null; return; }
        g.fill(0, 0, width, height, 0xAA000000);

        int pw = 300, ph = 200;
        int px = (width - pw) / 2, py = (height - ph) / 2;
        g.fill(px - 3, py - 3, px + pw + 3, py + ph + 3, CardRenderer.KRAFT_DARK);
        g.fill(px, py, px + pw, py + ph, CardRenderer.KRAFT);
        g.fill(px + 5, py + 5, px + pw - 5, py + ph - 5, CardRenderer.FACE);
        g.drawCenteredString(font, card.displayName() + " — your variants", width / 2, py + 12, CardRenderer.INK);

        float scale = 0.5f;
        int cw = Math.round(CardRenderer.CARD_W * scale);
        int ch = Math.round(CardRenderer.CARD_H * scale);
        int gap = 24;
        int totalW = cw * 2 + gap;
        int startX = px + (pw - totalW) / 2;
        int cy = py + 34;
        LivingEntity mob = CardRenderer.portraitEntity(minecraft, card, entityCache);
        boolean displayedFoil = ClientCollection.displayedIsFoil(card.id());

        int[] normalRect = {startX, cy, cw, ch};
        int[] foilRect = {startX + cw + gap, cy, cw, ch};

        // normal (always owned if we opened the picker)
        drawVariant(g, card, mob, normalRect, false, !displayedFoil, "Normal", mouseX, mouseY);
        // foil (only if owned)
        if (ClientCollection.hasFoil(card.id())) {
            drawVariant(g, card, mob, foilRect, true, displayedFoil, "✦ Foil", mouseX, mouseY);
        } else {
            g.fill(foilRect[0], foilRect[1], foilRect[0] + cw, foilRect[1] + ch, 0x22000000);
            g.drawCenteredString(font, "no foil", foilRect[0] + cw / 2, foilRect[1] + ch / 2 - 4, 0xFF8B8074);
        }

        g.drawCenteredString(font, "Click a card to set it on top of the pile", width / 2, py + ph - 26, CardRenderer.KRAFT_DARK);
        g.drawCenteredString(font, "ESC to close", width / 2, py + ph - 14, 0xFF9A9083);
    }

    private void drawVariant(GuiGraphics g, MobCard card, LivingEntity mob, int[] r, boolean foil,
                             boolean displayed, String label, int mouseX, int mouseY) {
        int cx = r[0], cy = r[1], cw = r[2], ch = r[3];
        boolean hover = mouseX >= cx && mouseX < cx + cw && mouseY >= cy && mouseY < cy + ch;
        CardRenderer.renderCard(g, font, card, cx, cy, 0.5f, mouseX, mouseY, mob, foil);
        if (displayed) {
            g.renderOutline(cx - 2, cy - 2, cw + 4, ch + 4, 0xFF55E06A);
            g.renderOutline(cx - 1, cy - 1, cw + 2, ch + 2, 0xFF55E06A);
        } else if (hover) {
            g.renderOutline(cx - 2, cy - 2, cw + 4, ch + 4, 0xFFF9D849);
        }
        int lc = displayed ? 0xFF2E8B3A : CardRenderer.KRAFT_DARK;
        g.drawCenteredString(font, displayed ? label + "  ★" : label, cx + cw / 2, cy + ch + 4, lc);
    }

    private void renderStats(GuiGraphics g) {
        int pw = 220, ph = 164;
        int px = (width - pw) / 2, py = (height - ph) / 2;
        g.fill(0, 0, width, height, 0x99000000);
        g.fill(px - 3, py - 3, px + pw + 3, py + ph + 3, CardRenderer.KRAFT_DARK);
        g.fill(px, py, px + pw, py + ph, CardRenderer.KRAFT);
        g.fill(px + 5, py + 5, px + pw - 5, py + ph - 5, CardRenderer.FACE);
        g.drawCenteredString(font, "COLLECTION STATS", width / 2, py + 12, CardRenderer.INK);
        int total = MobCards.ALL.size();
        int have = ClientCollection.count();
        int pct = Math.round(have * 100f / total);
        int valX = width / 2 + 40;
        int y = py + 30;
        statLine(g, px + 14, valX, y, "Collected", have + " / " + total + "  (" + pct + "%)"); y += 12;
        statLine(g, px + 14, valX, y, "Holographic foils", ClientCollection.foilCount() + " / " + total); y += 12;
        statLine(g, px + 14, valX, y, "Filed in book", String.valueOf(ClientCollection.storedCount())); y += 12;
        statLine(g, px + 14, valX, y, "Duel wins", String.valueOf(ClientCollection.duelWins())); y += 16;
        for (Tier t : new Tier[]{Tier.LEGENDARY, Tier.EPIC, Tier.RARE, Tier.UNCOMMON, Tier.COMMON}) {
            int tt = 0, th = 0;
            for (MobCard c : MobCards.ALL) {
                if (c.tier() == t) { tt++; if (ClientCollection.has(c.id())) th++; }
            }
            Integer rgb = MobCardItem.tierColor(t).getColor();
            g.drawString(font, t.label(), px + 14, y, 0xFF000000 | (rgb == null ? 0x555555 : rgb), false);
            String v = th + " / " + tt;
            g.drawString(font, v, px + pw - 14 - font.width(v), y, CardRenderer.INK, false);
            y += 11;
        }
        g.drawCenteredString(font, "click to close", width / 2, py + ph - 12, 0xFF9A9083);
    }

    private void statLine(GuiGraphics g, int labelX, int valueX, int y, String label, String value) {
        g.drawString(font, label, labelX, y, CardRenderer.KRAFT_DARK, false);
        g.drawString(font, value, valueX, y, CardRenderer.INK, false);
    }

    private void renderChips(GuiGraphics g, int mouseX, int mouseY) {
        for (Chip chip : chips) {
            boolean active = switch (chip.key()) {
                case "stats" -> statsOpen;
                case "sort", "deck", "store" -> false;
                default -> chip.key().equals("f_" + filter.name());
            };
            boolean hover = chip.hit(mouseX, mouseY);
            int bg = active ? 0xFF55A82F : hover ? 0xFFB99465 : CardRenderer.KRAFT;
            g.fill(chip.x(), chip.y(), chip.x() + chip.w(), chip.y() + chip.h(), bg);
            g.renderOutline(chip.x(), chip.y(), chip.w(), chip.h(), CardRenderer.KRAFT_DARK);
            String label = switch (chip.key()) {
                case "stats" -> "Stats";
                case "sort" -> "Sort: " + sort.label;
                case "deck" -> "Deck";
                case "store" -> "Store";
                default -> Filter.valueOf(chip.key().substring(2)).label;
            };
            g.drawString(font, label, chip.x() + 4, chip.y() + 2, active ? 0xFFFFFFFF : CardRenderer.INK, false);
        }
    }

    private void drawArrow(GuiGraphics g, int x, int y, String glyph, boolean enabled, int mouseX, int mouseY) {
        boolean hovered = enabled && mouseX >= x && mouseX < x + ARROW_W && mouseY >= y && mouseY < y + ARROW_H;
        g.fill(x, y, x + ARROW_W, y + ARROW_H, enabled ? (hovered ? 0xFFB99465 : CardRenderer.KRAFT) : 0xFFCEC3AF);
        g.renderOutline(x, y, ARROW_W, ARROW_H, CardRenderer.KRAFT_DARK);
        g.drawString(font, glyph, x + (ARROW_W - font.width(glyph)) / 2, y + 3,
                enabled ? CardRenderer.INK : 0xFF9A9083, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (statsOpen) { statsOpen = false; return true; }
        if (pickerMob != null) { pickerClick(mouseX, mouseY); return true; }
        if (search.mouseClicked(mouseX, mouseY, button)) { setFocused(search); return true; }
        if (button == 0) {
            for (Chip chip : chips) {
                if (chip.hit(mouseX, mouseY)) { onChip(chip.key()); return true; }
            }
            if (inArrow(mouseX, mouseY, prevX, prevY) && spread > 0) { flip(-1); return true; }
            if (inArrow(mouseX, mouseY, nextX, nextY) && spread < spreadCount - 1) { flip(1); return true; }

            int cw = Math.round(CardRenderer.CARD_W * CARD_SCALE);
            int ch = Math.round(CardRenderer.CARD_H * CARD_SCALE);
            int startIdx = spread * PER_SPREAD;
            for (int s = 0; s < PER_SPREAD; s++) {
                int i = startIdx + s;
                if (i >= view.size()) break;
                MobCard card = view.get(i);
                int[] p = slotPos(s);
                if (mouseX >= p[0] && mouseX < p[0] + cw && mouseY >= p[1] && mouseY < p[1] + ch
                        && ClientCollection.has(card.id())) {
                    if (hasShiftDown() && withdrawOne(card.id())) {
                        return true;
                    }
                    if (ClientCollection.variantCount(card.id()) > 1) {
                        pickerMob = card.id();
                        clickSound();
                    } else if (minecraft != null) {
                        minecraft.setScreen(new MobCardScreen(card, this, ClientCollection.displayedIsFoil(card.id())));
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void pickerClick(double mouseX, double mouseY) {
        MobCard card = MobCards.byId(pickerMob);
        if (card == null) { pickerMob = null; return; }
        int pw = 300, ph = 200;
        int px = (width - pw) / 2, py = (height - ph) / 2;
        float scale = 0.5f;
        int cw = Math.round(CardRenderer.CARD_W * scale);
        int ch = Math.round(CardRenderer.CARD_H * scale);
        int gap = 24, totalW = cw * 2 + gap;
        int startX = px + (pw - totalW) / 2, cy = py + 34;
        // click outside panel closes
        if (mouseX < px || mouseX > px + pw || mouseY < py || mouseY > py + ph) { pickerMob = null; return; }
        // normal
        if (mouseX >= startX && mouseX < startX + cw && mouseY >= cy && mouseY < cy + ch) {
            choose(card.id(), false);
            return;
        }
        // foil
        int fx = startX + cw + gap;
        if (ClientCollection.hasFoil(card.id()) && mouseX >= fx && mouseX < fx + cw && mouseY >= cy && mouseY < cy + ch) {
            choose(card.id(), true);
        }
    }

    /** Shift-click withdraw: take the displayed variant (or whichever is filed) out of the book. */
    private boolean withdrawOne(String mobId) {
        boolean foil = ClientCollection.displayedIsFoil(mobId);
        if (!ClientCollection.isStored(mobId, foil)) {
            // fall back to the other variant if the displayed one isn't filed
            foil = !foil;
        }
        if (!ClientCollection.isStored(mobId, foil)) {
            return false;
        }
        PacketDistributor.sendToServer(StorageActionPayload.withdraw(mobId, foil));
        clickSound();
        return true;
    }

    private void choose(String mobId, boolean foil) {
        PacketDistributor.sendToServer(new SetDisplayPayload(mobId, foil));
        if (minecraft != null) {
            minecraft.getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), foil ? 1.3f : 1.0f));
        }
        pickerMob = null;
    }

    private void onChip(String key) {
        switch (key) {
            case "stats" -> statsOpen = true;
            case "deck" -> { if (minecraft != null) minecraft.setScreen(new DeckBuilderScreen(this)); }
            case "store" -> PacketDistributor.sendToServer(StorageActionPayload.depositAll());
            case "sort" -> { sort = Sort.values()[(sort.ordinal() + 1) % Sort.values().length]; layoutChips(); rebuild(); }
            default -> { filter = Filter.valueOf(key.substring(2)); spread = 0; rebuild(); }
        }
        clickSound();
    }

    private boolean inArrow(double mx, double my, int x, int y) {
        return mx >= x && mx < x + ARROW_W && my >= y && my < y + ARROW_H;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double sx, double sy) {
        if (pickerMob != null || statsOpen) return true;
        if (sy < 0 && spread < spreadCount - 1) { flip(1); return true; }
        if (sy > 0 && spread > 0) { flip(-1); return true; }
        return super.mouseScrolled(mouseX, mouseY, sx, sy);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (pickerMob != null && keyCode == 256) { pickerMob = null; return true; }
        if (statsOpen && keyCode == 256) { statsOpen = false; return true; }
        if (search.isFocused() && search.keyPressed(keyCode, scanCode, modifiers)) return true;
        if (keyCode == 263 && spread > 0) { flip(-1); return true; }
        if (keyCode == 262 && spread < spreadCount - 1) { flip(1); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        if (search.isFocused()) return search.charTyped(c, modifiers);
        return super.charTyped(c, modifiers);
    }

    private void flip(int direction) {
        spread += direction;
        clickSound();
    }

    private void clickSound() {
        if (minecraft != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0F));
        }
    }
}
