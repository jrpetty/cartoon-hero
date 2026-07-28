package com.jrpetty.mobtrumps.client;

import com.jrpetty.mobtrumps.AwardActionPayload;
import com.jrpetty.mobtrumps.MobCardItem;
import com.jrpetty.mobtrumps.SetDisplayPayload;
import com.jrpetty.mobtrumps.SetEggs;
import com.jrpetty.mobtrumps.StorageActionPayload;
import com.jrpetty.mobtrumps.game.Achievement;
import com.jrpetty.mobtrumps.game.Achievements;
import com.jrpetty.mobtrumps.game.Category;
import com.jrpetty.mobtrumps.game.MobCard;
import com.jrpetty.mobtrumps.game.MobCards;
import com.jrpetty.mobtrumps.game.MobCategories;
import com.jrpetty.mobtrumps.game.Stat;
import com.jrpetty.mobtrumps.game.Tier;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The collection binder as an open two-page book. The card pages come first — a
 * 3x3 grid on each leaf, 18 to a spread — and the back of the book holds the
 * award pages and a settings leaf, reached by the tabs along the top or simply
 * by flipping on past the last card.
 *
 * <p>Awards are never paid out automatically: an earned one shows a Collect
 * button and the reward drops straight into your inventory when you press it.
 * A finished set additionally lets you take one spawn egg from that set, once.
 */
public class CollectionBookScreen extends Screen {

    private static final float BOOK_SCALE_CAP = 0.42f;
    /** Card scale for this window — shrinks below the cap so the two 3x3
     *  pages always fit, even at big GUI scales on small screens. */
    private float cardScale = BOOK_SCALE_CAP;
    private static final int COLS = 3, ROWS = 3, PER_PAGE = COLS * ROWS, PER_SPREAD = 2 * PER_PAGE;
    private static final int ARROW_W = 18, ARROW_H = 14;
    private static final int SPINE = 24;
    /** Award spreads (two groups each) plus the one set-rewards/settings leaf. */
    private static final int AWARD_SPREADS = 2;
    private static final int BACK_SPREADS = AWARD_SPREADS + 1;

    private enum Section { CARDS, AWARDS, SETTINGS }

    private enum Filter { ALL("All"), OWNED("Owned"), MISSING("Missing"), FOIL("Foil");
        final String label; Filter(String l) { label = l; } }
    private enum Sort { NUMBER("No."), NAME("Name"), TIER("Tier"), RATING("Rating");
        final String label; Sort(String l) { label = l; } }

    /** One toggle/cycle on the settings leaf. */
    private record Setting(String key, String label, String blurb) {
    }

    private static final List<Setting> SETTINGS = List.of(
            new Setting("kill_counter", "Hunt counter on cards",
                    "The little x12 badge showing how many you've hunted"),
            new Setting("card_size", "Battle card size", "How large cards render at the table"),
            new Setting("brightness", "Arena brightness", "How brightly the duel table is lit"),
            new Setting("live_portraits", "Live mob portraits", "Real mobs posing inside the card art"),
            new Setting("foil_sheen", "Holographic sheen", "The moving rainbow on foil cards"),
            new Setting("reduced_motion", "Reduced motion", "Calms flips, pulses and flying cards"),
            new Setting("battle_hints", "Battle hints", "The prompt line along the bottom of a duel"),
            new Setting("confirm_leave", "Confirm forfeits", "Ask twice before leaving a live game"));

    private final Map<String, LivingEntity> entityCache = new HashMap<>();
    private final List<MobCard> view = new ArrayList<>();
    private final List<Chip> chips = new ArrayList<>();
    private final List<Chip> tabs = new ArrayList<>();
    /** Rebuilt every frame on the back pages: buttons and their action keys. */
    private final List<Chip> hotspots = new ArrayList<>();

    private int spread;
    private Filter filter = Filter.ALL;
    private Sort sort = Sort.NUMBER;
    private boolean statsOpen = false;
    private String pickerMob = null;
    private Category eggPicker = null;
    /** The award row under the cursor this frame — its description fills the footer. */
    private Achievement hoveredAward;
    private EditBox search;

    private int cardSpreads;
    private int spreadCount;
    private int cellW, cellH, gridTop, leftGridX, rightGridX, pageW, pageBottom;
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
        // fit the open book to the window: shrink the cards below the cap until
        // both 3x3 pages + header + footer fit (fixes GUI scale 2 cutoff)
        float scaleW = (((width - 28f - SPINE) / 2f) / COLS - 8f) / CardRenderer.CARD_W;
        float scaleH = ((height - 78f - 26f - 12f) / ROWS - 8f) / CardRenderer.CARD_H;
        cardScale = Math.max(0.26f, Math.min(BOOK_SCALE_CAP, Math.min(scaleW, scaleH)));
        cellW = Math.round(CardRenderer.CARD_W * cardScale) + 8;
        cellH = Math.round(CardRenderer.CARD_H * cardScale) + 8;

        pageW = COLS * cellW;
        panelW = 2 * pageW + SPINE + 28;
        int headerH = 78;
        panelH = headerH + ROWS * cellH + 26;
        panelX = (width - panelW) / 2;
        panelY = Math.max(20, (height - panelH) / 2);
        gridTop = panelY + headerH;
        leftGridX = panelX + 14;
        rightGridX = panelX + 14 + pageW + SPINE;
        pageBottom = panelY + panelH - 26;

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

        layoutTabs();
        layoutChips();
        rebuild();
    }

    // --- structure ----------------------------------------------------------

    private Section section() {
        if (spread < cardSpreads) return Section.CARDS;
        return spread < cardSpreads + AWARD_SPREADS ? Section.AWARDS : Section.SETTINGS;
    }

    /** The first spread of a section, for the tabs to jump to. */
    private int firstSpread(Section s) {
        return switch (s) {
            case CARDS -> 0;
            case AWARDS -> cardSpreads;
            case SETTINGS -> cardSpreads + AWARD_SPREADS;
        };
    }

    private void layoutTabs() {
        tabs.clear();
        int x = panelX + panelW - 12;
        // laid out right to left so the rightmost tab is the last leaf
        String[] labels = {"Settings", "Awards", "Cards"};
        Section[] keys = {Section.SETTINGS, Section.AWARDS, Section.CARDS};
        for (int i = 0; i < labels.length; i++) {
            int w = font.width(labels[i]) + 14;
            x -= w + 3;
            tabs.add(new Chip("tab_" + keys[i].name(), x, panelY - 13, w, 15));
        }
    }

    private void layoutChips() {
        chips.clear();
        int y = panelY + 60;
        if (section() != Section.CARDS) {
            return; // the filter row belongs to the card pages only
        }
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
        cardSpreads = Math.max(1, (view.size() + PER_SPREAD - 1) / PER_SPREAD);
        spreadCount = cardSpreads + BACK_SPREADS;
        spread = Math.max(0, Math.min(spread, spreadCount - 1));
        layoutChips();
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

    // --- render -------------------------------------------------------------

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        hotspots.clear();
        hoveredAward = null;
        Section section = section();
        if (section != Section.CARDS && search.isFocused()) {
            search.setFocused(false); // the search box belongs to the card pages
            setFocused(null);
        }

        drawTabs(g, section, mouseX, mouseY);

        // book cover + cream inner with a central spine through the card area
        g.fill(panelX - 3, panelY - 3, panelX + panelW + 3, panelY + panelH + 3, CardRenderer.KRAFT_DARK);
        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, CardRenderer.KRAFT);
        g.fill(panelX + 6, panelY + 6, panelX + panelW - 6, panelY + panelH - 6, CardRenderer.FACE);
        g.fill(panelX + panelW / 2 - 1, gridTop - 6, panelX + panelW / 2 + 1, panelY + panelH - 24,
                CardRenderer.KRAFT_DARK);

        drawMasthead(g);

        if (section == Section.CARDS) {
            search.render(g, mouseX, mouseY, partialTick);
            renderChips(g, mouseX, mouseY);
            renderCardPages(g, mouseX, mouseY);
        } else if (section == Section.AWARDS) {
            int pair = (spread - cardSpreads) * 2;
            Achievement.Group[] groups = Achievement.Group.values();
            renderAwardPage(g, leftGridX, groups[pair], mouseX, mouseY);
            if (pair + 1 < groups.length) {
                renderAwardPage(g, rightGridX, groups[pair + 1], mouseX, mouseY);
            }
        } else {
            renderSetRewardsPage(g, leftGridX, mouseX, mouseY);
            renderSettingsPage(g, rightGridX, mouseX, mouseY);
        }

        drawArrow(g, prevX, prevY, "<", spread > 0, mouseX, mouseY);
        drawArrow(g, nextX, nextY, ">", spread < spreadCount - 1, mouseX, mouseY);
        String pageText = "Pages " + (spread * 2 + 1) + "–" + (spread * 2 + 2) + " / " + (spreadCount * 2);
        g.drawString(font, pageText, panelX + (panelW - font.width(pageText)) / 2, panelY + panelH - 17,
                CardRenderer.KRAFT_DARK, false);

        // the footer doubles as the awards' description line, so a row never has
        // to squeeze the title, the payout, the bar and the blurb into 26 pixels
        String hint;
        int hintColor = 0xFFAAAAAA;
        if (hoveredAward != null) {
            hint = hoveredAward.title() + " — " + hoveredAward.description()
                    + "   ·   " + hoveredAward.rewardLabel();
            hintColor = 0xFFF3E2A7;
        } else {
            hint = switch (section) {
                case CARDS -> "Click a card to view it · Store files loose cards away · shift-click a green-tabbed card to take one out";
                case AWARDS -> "Press Collect on a finished award and the reward lands straight in your inventory";
                case SETTINGS -> "Settings are yours alone — they change how the mod looks, never how it plays";
            };
        }
        g.drawString(font, hint, (width - font.width(hint)) / 2, panelY + panelH + 8, hintColor, true);

        if (pickerMob != null) renderPicker(g, mouseX, mouseY);
        if (eggPicker != null) renderEggPicker(g, mouseX, mouseY);
        if (statsOpen) renderStats(g);
    }

    /** Title, collection tally and progress bar across the top of the spread. */
    private void drawMasthead(GuiGraphics g) {
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
    }

    /** Section tabs sticking up out of the top edge of the book. */
    private void drawTabs(GuiGraphics g, Section active, int mouseX, int mouseY) {
        int waiting = ClientAwards.collectableCount();
        for (Chip tab : tabs) {
            Section s = Section.valueOf(tab.key().substring(4));
            boolean on = s == active;
            boolean hover = tab.hit(mouseX, mouseY);
            int bg = on ? CardRenderer.FACE : hover ? 0xFFB99465 : CardRenderer.KRAFT;
            g.fill(tab.x() - 2, tab.y() - 2, tab.x() + tab.w() + 2, tab.y() + tab.h() + 2,
                    CardRenderer.KRAFT_DARK);
            g.fill(tab.x(), tab.y(), tab.x() + tab.w(), tab.y() + tab.h() + 4, bg);
            String label = switch (s) {
                case CARDS -> "Cards";
                case AWARDS -> "Awards";
                case SETTINGS -> "Settings";
            };
            g.drawString(font, label, tab.x() + 7, tab.y() + 4,
                    on ? CardRenderer.INK : CardRenderer.KRAFT_DARK, false);
            // a red pip on the Awards tab while anything is waiting to be collected
            if (s == Section.AWARDS && waiting > 0) {
                g.fill(tab.x() + tab.w() - 5, tab.y() + 1, tab.x() + tab.w() - 1, tab.y() + 5, 0xFFD8452F);
            }
        }
    }

    private void renderCardPages(GuiGraphics g, int mouseX, int mouseY) {
        int cw = Math.round(CardRenderer.CARD_W * cardScale);
        int ch = Math.round(CardRenderer.CARD_H * cardScale);
        int start = spread * PER_SPREAD;
        if (view.isEmpty()) {
            g.drawCenteredString(font, "No cards match.", width / 2, gridTop + 30, CardRenderer.KRAFT_DARK);
        }
        boolean overlayOpen = pickerMob != null || statsOpen || eggPicker != null;
        for (int s = 0; s < PER_SPREAD; s++) {
            int i = start + s;
            if (i >= view.size()) break;
            MobCard card = view.get(i);
            int[] p = slotPos(s);
            int cx = p[0], cy = p[1];
            g.fill(cx + 2, cy + 3, cx + cw + 4, cy + ch + 5, 0x44000000);
            boolean hovered = !overlayOpen
                    && mouseX >= cx && mouseX < cx + cw && mouseY >= cy && mouseY < cy + ch;
            if (ClientCollection.has(card.id())) {
                // while an overlay is open, skip grid mobs entirely — their 3D
                // depth writes would poke through the panel drawn on top
                LivingEntity mob = overlayOpen ? null
                        : CardRenderer.portraitEntity(minecraft, card, entityCache);
                boolean foil = ClientCollection.displayedIsFoil(card.id());
                int level = ClientCollection.displayLevel(card.id(), foil);
                // only the hovered card comes alive and follows the cursor
                CardRenderer.renderCard(g, font, card, level, cx, cy, cardScale,
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
                // how many of this mob you've hunted, bottom-right (settings)
                int copies = ClientCollection.killCount(card.id());
                if (copies > 0 && ClientPrefs.killCounter()) {
                    String badge = "x" + copies;
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
                CardRenderer.renderBack(g, font, cx, cy, cardScale);
                if (hovered) g.renderOutline(cx - 2, cy - 2, cw + 4, ch + 4, 0x66FFFFFF);
            }
        }
    }

    // --- award pages --------------------------------------------------------

    private void renderAwardPage(GuiGraphics g, int x0, Achievement.Group group,
                                 int mouseX, int mouseY) {
        List<Achievement> list = Achievements.of(group);
        int x1 = x0 + pageW;
        int y = gridTop;

        String done = countCollected(list) + " / " + list.size();
        y = drawPageHeading(g, x0, x1, y, group.accent(),
                group.label().toUpperCase(Locale.ROOT), group.blurb(), done);

        int available = pageBottom - y;
        int rowH = Math.max(16, Math.min(30, available / Math.max(1, list.size())));
        for (Achievement a : list) {
            drawAwardRow(g, a, x0, x1, y, rowH, mouseX, mouseY);
            y += rowH;
        }
    }

    /**
     * The accent rule, heading, optional right-aligned tally and blurb every
     * back page shares. Everything is trimmed to the leaf it belongs to, so a
     * long blurb can never bleed across the spine onto the facing page.
     */
    private int drawPageHeading(GuiGraphics g, int x0, int x1, int y, int accent,
                                String heading, String blurb, String tally) {
        g.fill(x0, y, x1, y + 2, accent);
        y += 6;
        int headingMax = x1 - x0;
        if (tally != null) {
            g.drawString(font, tally, x1 - font.width(tally), y, CardRenderer.KRAFT_DARK, false);
            headingMax = x1 - x0 - font.width(tally) - 6;
        }
        g.drawString(font, trim(heading, headingMax), x0, y, CardRenderer.INK, false);
        y += 10;
        g.drawString(font, trim(blurb, x1 - x0), x0, y, 0xFF8B8074, false);
        return y + 12;
    }

    private int countCollected(List<Achievement> list) {
        int n = 0;
        for (Achievement a : list) {
            if (ClientAwards.isClaimed(a)) n++;
        }
        return n;
    }

    /**
     * One award row. Two layouts, picked by how much height the page can spare:
     * a two-line card (title + payout above, progress + Collect below) when
     * there is room, and a single baseline when the book is squeezed. The
     * description never competes for space — it goes to the footer on hover.
     */
    private void drawAwardRow(GuiGraphics g, Achievement a, int x0, int x1, int y, int rowH,
                              int mouseX, int mouseY) {
        boolean claimed = ClientAwards.isClaimed(a);
        boolean ready = ClientAwards.isCollectable(a);
        int progress = ClientAwards.progress(a);
        float frac = Math.min(1f, progress / (float) a.target());
        boolean tall = rowH >= 24;
        int inner = rowH - 2;

        // the row plate: earned rows lift off the page, claimed ones settle back
        int plate = ready ? 0x2255A82F : claimed ? 0x14000000 : 0x0E000000;
        g.fill(x0, y, x1, y + inner, plate);
        if (ready) {
            g.renderOutline(x0, y, x1 - x0, inner, 0xFF55A82F);
        }
        if (mouseX >= x0 && mouseX < x1 && mouseY >= y && mouseY < y + inner) {
            hoveredAward = a;
        }

        // right-hand slot: the Collect button, or a tick once it is spent
        int slotLeft = x1 - 4;
        if (ready) {
            int btnW = font.width("Collect") + 10;
            int bx = x1 - btnW - 3;
            int by = y + (inner - 12) / 2;
            Chip btn = new Chip("claim_" + a.id(), bx, by, btnW, 12);
            hotspots.add(btn);
            boolean hover = btn.hit(mouseX, mouseY);
            g.fill(bx, by, bx + btnW, by + 12, hover ? 0xFF6BC33F : 0xFF55A82F);
            g.renderOutline(bx, by, btnW, 12, 0xFF2E6B18);
            g.drawString(font, "Collect", bx + 5, by + 2, 0xFFFFFFFF, false);
            slotLeft = bx - 6;
        } else if (claimed) {
            String tick = "✔";
            g.drawString(font, tick, x1 - font.width(tick) - 4, y + (inner - 8) / 2,
                    0xFF3D8B3D, false);
            slotLeft = x1 - font.width(tick) - 10;
        }

        int titleColor = claimed ? 0xFF8B8074 : CardRenderer.INK;
        String count = progress + " / " + a.target();
        int countW = font.width(count);

        // the count is always pinned to the slot and everything else takes what
        // is left, so a long title or a wide button can never push it under the
        // Collect button the way a minimum-rail-width rule could
        int countX = slotLeft - countW;

        if (tall) {
            // line 1: title, with the payout right-aligned against the slot
            g.drawString(font, a.title(), x0 + 4, y + 2, titleColor, false);
            int titleEnd = x0 + 8 + font.width(a.title());
            String reward = trim(a.rewardLabel(), Math.max(20, slotLeft - titleEnd));
            g.drawString(font, reward, slotLeft - font.width(reward), y + 2,
                    claimed ? 0xFFB3AA9C : 0xFF6E6154, false);

            // line 2: the progress rail, with its count on the right
            int railY = y + inner - 8;
            g.drawString(font, count, countX, railY - 3, 0xFF8B8074, false);
            drawRail(g, a, x0 + 4, countX - 6, railY, frac, claimed);
        } else if (ready) {
            // squeezed AND finished: a full bar next to a Collect button says
            // nothing the button doesn't, so give the whole line to the title
            int textY = y + (inner - 8) / 2;
            g.drawString(font, trim(a.title(), slotLeft - x0 - 8), x0 + 4, textY, titleColor, false);
        } else {
            // one baseline: title | rail | count | slot
            int textY = y + (inner - 8) / 2;
            int titleMax = Math.max(30, Math.min((x1 - x0) * 2 / 5, countX - x0 - 46));
            g.drawString(font, trim(a.title(), titleMax), x0 + 4, textY, titleColor, false);
            g.drawString(font, count, countX, textY, 0xFF8B8074, false);
            drawRail(g, a, x0 + 8 + titleMax, countX - 6, y + inner / 2 - 1, frac, claimed);
        }
    }

    private void drawRail(GuiGraphics g, Achievement a, int x0, int x1, int y,
                          float frac, boolean claimed) {
        if (x1 <= x0) {
            return;
        }
        g.fill(x0, y, x1, y + 3, 0x33000000);
        int fill = (int) ((x1 - x0) * frac);
        if (fill > 0) {
            g.fill(x0, y, x0 + fill, y + 3, claimed ? 0xFF9A9083 : a.group().accent());
        }
    }

    private String trim(String text, int maxWidth) {
        if (maxWidth <= 8 || font.width(text) <= maxWidth) {
            return text;
        }
        String out = text;
        while (out.length() > 1 && font.width(out + "…") > maxWidth) {
            out = out.substring(0, out.length() - 1);
        }
        return out + "…";
    }

    // --- set rewards leaf ---------------------------------------------------

    private void renderSetRewardsPage(GuiGraphics g, int x0, int mouseX, int mouseY) {
        int x1 = x0 + pageW;
        int y = drawPageHeading(g, x0, x1, gridTop, 0xFFB57EDC, "SET REWARDS",
                "Finish a set, keep one of its mobs as a spawn egg", null);

        Category[] cats = Category.values();
        int rowH = Math.max(16, Math.min(26, (pageBottom - y) / cats.length));
        for (Category cat : cats) {
            drawSetRow(g, cat, x0, x1, y, rowH, mouseX, mouseY);
            y += rowH;
        }
    }

    private void drawSetRow(GuiGraphics g, Category cat, int x0, int x1, int y, int rowH,
                            int mouseX, int mouseY) {
        List<String> members = MobCategories.members(cat);
        int have = 0;
        for (String id : members) {
            if (ClientCollection.has(id)) have++;
        }
        boolean complete = have == members.size();
        boolean pending = ClientAwards.eggPending(cat);
        String taken = ClientAwards.eggTaken(cat);

        g.fill(x0, y, x1, y + rowH - 2, pending ? 0x22B57EDC : 0x0E000000);
        if (pending) {
            g.renderOutline(x0, y, x1 - x0, rowH - 2, 0xFFB57EDC);
        }
        g.fill(x0 + 3, y + 3, x0 + 6, y + rowH - 5, cat.accent());

        // draw the right-hand slot first, so the set name knows what room it has
        int slotLeft = x1 - 2;
        if (pending) {
            String label = "Choose egg";
            int bw = font.width(label) + 10;
            int bx = x1 - bw - 3;
            int by = y + (rowH - 2 - 12) / 2;
            Chip btn = new Chip("egg_" + cat.name(), bx, by, bw, 12);
            hotspots.add(btn);
            boolean hover = btn.hit(mouseX, mouseY);
            g.fill(bx, by, bx + bw, by + 12, hover ? 0xFFC79BEA : 0xFFB57EDC);
            g.renderOutline(bx, by, bw, 12, 0xFF6B3F94);
            g.drawString(font, label, bx + 5, by + 2, 0xFF2A1338, false);
            slotLeft = bx - 6;
        } else {
            String state;
            int stateColor = 0xFF9A9083;
            if (taken != null) {
                MobCard card = MobCards.byId(taken);
                state = "✔ " + (card == null ? taken : card.displayName()) + " egg";
                stateColor = 0xFF3D8B3D;
            } else if (complete) {
                state = SetEggs.hasAny(cat) ? "claimed" : "no eggs in this set";
            } else {
                state = have + " / " + members.size() + " collected";
            }
            g.drawString(font, state, x1 - font.width(state) - 2, y + 2, stateColor, false);
            slotLeft = x1 - font.width(state) - 8;
        }
        g.drawString(font, trim(cat.label(), slotLeft - x0 - 11), x0 + 11, y + 2,
                complete ? CardRenderer.INK : 0xFF6E6154, false);

        if (rowH >= 22 && !complete) {
            int railY = y + rowH - 8;
            int railW = x1 - x0 - 8;
            g.fill(x0 + 4, railY, x0 + 4 + railW, railY + 2, 0x33000000);
            int fill = (int) (railW * (have / (float) Math.max(1, members.size())));
            if (fill > 0) g.fill(x0 + 4, railY, x0 + 4 + fill, railY + 2, cat.accent());
        }
    }

    // --- settings leaf ------------------------------------------------------

    private void renderSettingsPage(GuiGraphics g, int x0, int mouseX, int mouseY) {
        int x1 = x0 + pageW;
        int y = drawPageHeading(g, x0, x1, gridTop, 0xFF3FA7D6, "SETTINGS",
                "Saved on this computer, for every world", null);

        int rowH = Math.max(18, Math.min(28, (pageBottom - y) / SETTINGS.size()));
        for (Setting s : SETTINGS) {
            drawSettingRow(g, s, x0, x1, y, rowH, mouseX, mouseY);
            y += rowH;
        }
    }

    private void drawSettingRow(GuiGraphics g, Setting s, int x0, int x1, int y, int rowH,
                                int mouseX, int mouseY) {
        String value;
        boolean on;
        switch (s.key()) {
            case "card_size" -> {
                value = ClientPrefs.cardSize().label;
                on = true;
            }
            case "brightness" -> {
                value = ClientPrefs.brightness().label;
                on = true;
            }
            default -> {
                on = ClientPrefs.get(s.key());
                value = on ? "On" : "Off";
            }
        }
        boolean isToggle = !s.key().equals("card_size") && !s.key().equals("brightness");

        int bw = Math.max(font.width("Normal"), font.width(value)) + 12;
        int bx = x1 - bw - 3;
        int by = y + (rowH - 2 - 12) / 2;
        Chip btn = new Chip("set_" + s.key(), bx, by, bw, 12);
        hotspots.add(btn);
        boolean hover = btn.hit(mouseX, mouseY);

        g.fill(x0, y, x1, y + rowH - 2, hover ? 0x18000000 : 0x0A000000);
        g.drawString(font, trim(s.label(), bx - x0 - 10), x0 + 4, y + 2, CardRenderer.INK, false);
        if (rowH >= 24) {
            g.drawString(font, trim(s.blurb(), bx - x0 - 10), x0 + 4, y + 12, 0xFF9A9083, false);
        }

        int fillCol = isToggle
                ? (on ? (hover ? 0xFF6BC33F : 0xFF55A82F) : (hover ? 0xFFB0A695 : 0xFF8E8578))
                : (hover ? 0xFF5EC0E8 : 0xFF3FA7D6);
        g.fill(bx, by, bx + bw, by + 12, fillCol);
        g.renderOutline(bx, by, bw, 12, CardRenderer.KRAFT_DARK);
        g.drawString(font, value, bx + (bw - font.width(value)) / 2, by + 2, 0xFFFFFFFF, false);
    }

    // --- overlays -----------------------------------------------------------

    /** Pick which mob's spawn egg to take for a finished set. */
    private void renderEggPicker(GuiGraphics g, int mouseX, int mouseY) {
        List<String> options = SetEggs.choices(eggPicker);
        if (options.isEmpty()) {
            eggPicker = null;
            return;
        }
        g.fill(0, 0, width, height, 0xAA000000);
        int cols = Math.min(5, options.size());
        int cell = 42;
        int pw = Math.max(260, cols * cell + 40);
        int rows = (options.size() + cols - 1) / cols;
        int ph = 78 + rows * cell;
        int px = (width - pw) / 2, py = (height - ph) / 2;
        g.fill(px - 3, py - 3, px + pw + 3, py + ph + 3, CardRenderer.KRAFT_DARK);
        g.fill(px, py, px + pw, py + ph, CardRenderer.KRAFT);
        g.fill(px + 5, py + 5, px + pw - 5, py + ph - 5, CardRenderer.FACE);

        g.drawCenteredString(font, eggPicker.label().toUpperCase(Locale.ROOT) + " — COMPLETE",
                width / 2, py + 12, CardRenderer.INK);
        g.drawCenteredString(font, "Take one spawn egg. You only get this choice once.",
                width / 2, py + 25, CardRenderer.KRAFT_DARK);

        int gridX = px + (pw - cols * cell) / 2;
        int gridY = py + 44;
        for (int i = 0; i < options.size(); i++) {
            String mobId = options.get(i);
            Item egg = SetEggs.eggFor(mobId);
            if (egg == null) continue;
            int cx = gridX + (i % cols) * cell;
            int cy = gridY + (i / cols) * cell;
            Chip slot = new Chip("pickegg_" + mobId, cx + 3, cy + 2, cell - 6, cell - 6);
            hotspots.add(slot);
            boolean hover = slot.hit(mouseX, mouseY);
            g.fill(cx + 3, cy + 2, cx + cell - 3, cy + cell - 4, hover ? 0xFFE6D9BC : 0x22000000);
            g.renderOutline(cx + 3, cy + 2, cell - 6, cell - 6, hover ? 0xFFB57EDC : CardRenderer.KRAFT_DARK);
            g.renderItem(new ItemStack(egg), cx + cell / 2 - 8, cy + 6);
            MobCard card = MobCards.byId(mobId);
            String name = card == null ? mobId : card.displayName();
            String shown = trim(name, cell - 6);
            g.drawString(font, shown, cx + (cell - font.width(shown)) / 2, cy + cell - 14,
                    CardRenderer.INK, false);
        }
        g.drawCenteredString(font, "ESC to decide later", width / 2, py + ph - 14, 0xFF9A9083);
    }

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
        int level = ClientCollection.displayLevel(card.id(), foil);
        CardRenderer.renderCard(g, font, card, level, cx, cy, 0.5f, mouseX, mouseY, mob, foil, true);
        if (displayed) {
            g.renderOutline(cx - 2, cy - 2, cw + 4, ch + 4, 0xFF55E06A);
            g.renderOutline(cx - 1, cy - 1, cw + 2, ch + 2, 0xFF55E06A);
        } else if (hover) {
            g.renderOutline(cx - 2, cy - 2, cw + 4, ch + 4, 0xFFF9D849);
        }
        int lc = displayed ? 0xFF2E8B3A : CardRenderer.KRAFT_DARK;
        g.drawCenteredString(font, displayed ? label + "  ★" : label, cx + cw / 2, cy + ch + 4, lc);
    }

    private int categoriesComplete() {
        int done = 0;
        for (Category cat : Category.values()) {
            boolean all = true;
            for (String id : MobCategories.members(cat)) {
                if (!ClientCollection.has(id)) { all = false; break; }
            }
            if (all) done++;
        }
        return done;
    }

    private void renderStats(GuiGraphics g) {
        int pw = 220, ph = 190;
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
        statLine(g, px + 14, valX, y, "Duel wins", String.valueOf(ClientCollection.duelWins())); y += 12;
        statLine(g, px + 14, valX, y, "Sets done", categoriesComplete() + " / " + Category.values().length); y += 12;
        statLine(g, px + 14, valX, y, "Awards waiting", String.valueOf(ClientAwards.collectableCount())); y += 16;
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

    // --- interaction --------------------------------------------------------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (statsOpen) { statsOpen = false; return true; }
        if (eggPicker != null) {
            // the overlay swallows every click; only its own slots do anything,
            // so a stray click can't reach a settings button underneath it
            if (button == 0) {
                for (Chip spot : hotspots) {
                    if (spot.key().startsWith("pickegg_") && spot.hit(mouseX, mouseY)) {
                        PacketDistributor.sendToServer(AwardActionPayload.egg(
                                eggPicker.name(), spot.key().substring("pickegg_".length())));
                        eggPicker = null;
                        rewardSound();
                        return true;
                    }
                }
            }
            eggPicker = null;
            return true;
        }
        if (pickerMob != null) { pickerClick(mouseX, mouseY); return true; }
        if (button == 0) {
            for (Chip tab : tabs) {
                if (tab.hit(mouseX, mouseY)) {
                    spread = firstSpread(Section.valueOf(tab.key().substring(4)));
                    layoutChips();
                    clickSound();
                    return true;
                }
            }
        }
        if (section() == Section.CARDS && search.mouseClicked(mouseX, mouseY, button)) {
            setFocused(search);
            return true;
        }
        if (button == 0) {
            if (clickHotspots(mouseX, mouseY)) return true;
            for (Chip chip : chips) {
                if (chip.hit(mouseX, mouseY)) { onChip(chip.key()); return true; }
            }
            if (inArrow(mouseX, mouseY, prevX, prevY) && spread > 0) { flip(-1); return true; }
            if (inArrow(mouseX, mouseY, nextX, nextY) && spread < spreadCount - 1) { flip(1); return true; }

            if (section() == Section.CARDS && clickCard(mouseX, mouseY)) {
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** Buttons on the award / set / settings leaves and in the egg picker. */
    private boolean clickHotspots(double mouseX, double mouseY) {
        for (Chip spot : hotspots) {
            if (!spot.hit(mouseX, mouseY)) continue;
            String key = spot.key();
            if (key.startsWith("claim_")) {
                PacketDistributor.sendToServer(
                        AwardActionPayload.claim(key.substring("claim_".length())));
                rewardSound();
            } else if (key.startsWith("egg_")) {
                eggPicker = SetEggs.category(key.substring("egg_".length()));
                clickSound();
            } else if (key.startsWith("set_")) {
                String setting = key.substring("set_".length());
                switch (setting) {
                    case "card_size" -> ClientPrefs.cycleCardSize();
                    case "brightness" -> ClientPrefs.cycleBrightness();
                    default -> ClientPrefs.toggle(setting);
                }
                clickSound();
            } else {
                continue;
            }
            return true;
        }
        return false;
    }

    private boolean clickCard(double mouseX, double mouseY) {
        int cw = Math.round(CardRenderer.CARD_W * cardScale);
        int ch = Math.round(CardRenderer.CARD_H * cardScale);
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
        return false;
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
        if (pickerMob != null || statsOpen || eggPicker != null) return true;
        if (sy < 0 && spread < spreadCount - 1) { flip(1); return true; }
        if (sy > 0 && spread > 0) { flip(-1); return true; }
        return super.mouseScrolled(mouseX, mouseY, sx, sy);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (pickerMob != null && keyCode == 256) { pickerMob = null; return true; }
        if (eggPicker != null && keyCode == 256) { eggPicker = null; return true; }
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
        layoutChips();
        clickSound();
    }

    private void clickSound() {
        if (minecraft != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0F));
        }
    }

    private void rewardSound() {
        if (minecraft != null) {
            minecraft.getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.5f));
        }
    }
}
