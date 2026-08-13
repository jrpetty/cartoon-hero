package com.jrpetty.mobtrumps.client;

import com.jrpetty.mobtrumps.AwardActionPayload;
import com.jrpetty.mobtrumps.MobCardItem;
import com.jrpetty.mobtrumps.ProfileSyncPayload;
import com.jrpetty.mobtrumps.RankTier;
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
import net.minecraft.util.Mth;
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
    /** The card grid. Not constant: a short window drops to fewer rows rather
     *  than shrinking the cards until they are unreadable, or -- as it used to
     *  -- letting the panel hang off the bottom of the screen. */
    private int cols = 3, rows = 3, perPage = 9, perSpread = 18;
    private static final float MIN_SCALE = 0.26f;
    private static final int ARROW_W = 18, ARROW_H = 14;
    private static final int SPINE = 24;
    /**
     * The back of the book: one spread per award group, then the set-rewards
     * leaf and the settings leaf. Each gets the WHOLE spread — rows run across
     * both leaves rather than being squeezed into one narrow column, which is
     * what used to chop every title and blurb in half.
     */
    /**
     * How many awards one spread holds.
     *
     * <p>At the smallest window Minecraft allows, the award area is 72 pixels
     * and a row cannot be read below twelve, so a spread fits six rows down
     * each of its two leaves and no arrangement fits more. A group larger than
     * that has to run onto another spread — the row loop skips anything past
     * the fold, and a skipped award is invisible AND unclickable, so its reward
     * simply cannot be collected. The Parlour's twenty-one awards were losing
     * nine that way.
     *
     * <p>Fixed rather than solved from the window on purpose. If capacity moved
     * with the window, so would the number of spreads, and the page you were
     * reading would become a different page when you resized.
     */
    private static final int AWARDS_PER_SPREAD = 12;

    /**
     * Which group each award spread shows, and which page of it — one entry per
     * spread, in reading order. Derived, because this was once a hardcoded 4
     * that would have silently hidden any group added after it.
     */
    private static final java.util.List<int[]> AWARD_PAGES = buildAwardPages();

    private static java.util.List<int[]> buildAwardPages() {
        java.util.List<int[]> pages = new ArrayList<>();
        com.jrpetty.mobtrumps.game.Achievement.Group[] groups =
                com.jrpetty.mobtrumps.game.Achievement.Group.values();
        for (int g = 0; g < groups.length; g++) {
            int n = com.jrpetty.mobtrumps.game.Achievements.of(groups[g]).size();
            int spreads = Math.max(1, (n + AWARDS_PER_SPREAD - 1) / AWARDS_PER_SPREAD);
            for (int page = 0; page < spreads; page++) {
                pages.add(new int[]{g, page, spreads});
            }
        }
        return java.util.List.copyOf(pages);
    }

    private static final int AWARD_SPREADS = AWARD_PAGES.size();
    /** Standing, then The Record. Splitting them is what lets every row fit
     *  on a 320x240 window, where one leaf holds only eight lines. */
    private static final int PROFILE_SPREADS = 2;
    private static final int BACK_SPREADS = AWARD_SPREADS + 2 + PROFILE_SPREADS;
    /** Back pages are drawn through this scale so the text is properly legible. */
    private static final float UI = 1.25f;
    /** Panel chrome that must be budgeted for: page arrows and numbers inside
     *  the panel, the tab row above it, the hint line below it. */
    private static final int FOOTER_H = 26, TAB_RESERVE = 20, HINT_RESERVE = 22;

    private enum Section { CARDS, AWARDS, SETS, PROFILE, SETTINGS }

    private enum Filter { ALL("All"), OWNED("Owned"), MISSING("Missing"), FOIL("Foil");
        final String label; Filter(String l) { label = l; } }
    private enum Sort { NUMBER("No."), NAME("Name"), TIER("Tier"), RATING("Rating");
        final String label; Sort(String l) { label = l; } }

    /**
     * One toggle/cycle on the settings leaf. {@code blurb} is the one-liner that
     * fits on the row; {@code detail} is the full explanation shown on hover,
     * so nothing important has to survive being truncated.
     */
    private record Setting(String key, String label, String blurb, String detail) {
    }

    private static final List<Setting> SETTINGS = List.of(
            new Setting("kill_counter", "Hunt counter", "The kill tally that pops up as you hunt",
                    "Shows \"Creeper holo: 12 / 100 kills\" above your hotbar each time you kill a "
                    + "mob, and puts the matching x12 badge on cards in this book. Turn it off for "
                    + "a clean screen — your kills are still counted, you just aren't told about "
                    + "each one."),
            new Setting("card_size", "Battle card size", "How large cards render at the table",
                    "Auto fits the cards to your window, which is usually what you want. Small, "
                    + "Medium and Large pin them to a fixed size instead, never larger than the "
                    + "window can show."),
            new Setting("brightness", "Arena brightness", "How brightly the duel table is lit",
                    "Scales the light on the felt during a battle. Dim is the old, moodier table; "
                    + "Bright is the default; Vivid pushes it further again if your monitor runs "
                    + "dark."),
            new Setting("live_portraits", "Live mob portraits", "Real mobs posing inside the card art",
                    "Each card's picture holds an actual 3D mob that looks around and follows your "
                    + "cursor. Turning it off leaves the painted scene behind it, which is calmer "
                    + "and noticeably cheaper on a busy book page."),
            new Setting("foil_sheen", "Holographic sheen", "The moving rainbow on foil cards",
                    "Holographic cards wash a shifting rainbow across their face. Off freezes that "
                    + "pattern rather than removing it, so a holo still looks like a holo — it just "
                    + "stops moving."),
            new Setting("reduced_motion", "Reduced motion", "Calms flips, pulses and flying cards",
                    "Switches off the decorative movement in a battle: cards stop sliding in, "
                    + "winners stop pulsing, spoils stop flying across the table and the banners "
                    + "stop overshooting. The card flip itself still plays so you can read the "
                    + "round."),
            new Setting("battle_hints", "Battle hints", "The prompt line along the bottom of a duel",
                    "The line at the foot of the battle screen telling you to click a stat or that "
                    + "you're waiting on your opponent. Off once you know the game by heart."),
            new Setting("confirm_leave", "Confirm forfeits", "Ask twice before leaving a live game",
                    "Leaving a game in progress forfeits it. With this on, the Leave button arms "
                    + "itself and asks \"Forfeit?!\" before it actually quits, so a stray click "
                    + "can't cost you a duel."));

    private final Map<String, LivingEntity> entityCache = new HashMap<>();
    private final List<MobCard> view = new ArrayList<>();
    private final List<Chip> chips = new ArrayList<>();
    private final List<Chip> tabs = new ArrayList<>();
    /** Rebuilt every frame on the back pages: buttons and their action keys. */
    private final List<Chip> hotspots = new ArrayList<>();

    private int spread;
    private Filter filter = Filter.ALL;
    private Sort sort = Sort.NUMBER;
    private String pickerMob = null;
    private Category eggPicker = null;
    /** The award row under the cursor this frame — its description fills the footer. */
    private Achievement hoveredAward;
    /** The settings row under the cursor — its full detail is drawn as a tooltip. */
    private Setting hoveredSetting;
    /** The grid card under the cursor — drives the details tooltip and its File/Take control. */
    private MobCard hoveredCard;
    /** Screen-space bounds of whatever is hovered, so its tooltip can dodge it. */
    private int[] hoverRect;
    private Chip cardAction;
    private EditBox search;

    /**
     * Whether the spread is turned face-down. The back is the one piece of the
     * card art you otherwise only glimpse across a duel table, so the book can
     * turn its cards over to look at it — a viewing mode, not an editing one:
     * while a spread is face-down nothing on it can be clicked.
     */
    /** When the turn started, so the cards flip in a wave rather than all at once. */

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
        // Fit the open book to the window. The panel is not the whole story:
        // the tabs sit ABOVE it and the hint line BELOW it, and both used to be
        // left out of the budget, which is what pushed them off-screen.
        //
        // The card scale is solved FROM the space that leaves rather than
        // floored at a readable size and hoped for -- a floor is what let a
        // short window overflow again. If three rows cannot be read at that
        // scale we drop a row, then trim the header, then drop a column.
        int avail = height - TAB_RESERVE - HINT_RESERVE;
        int[][] plans = {{3, 3, 78}, {3, 2, 78}, {3, 2, 62}, {2, 2, 62}};
        int headerH = 78;
        for (int i = 0; i < plans.length; i++) {
            int[] plan = plans[i];
            float sH = (((avail - plan[2] - FOOTER_H) / (float) plan[1]) - 8f) / CardRenderer.CARD_H;
            float sW = (((width - 28f - SPINE) / 2f) / plan[0] - 8f) / CardRenderer.CARD_W;
            float s = Math.min(BOOK_SCALE_CAP, Math.min(sW, sH));
            // take this plan if it reads, or if it is the last one we have
            if (s >= MIN_SCALE || i == plans.length - 1) {
                cols = plan[0];
                rows = plan[1];
                headerH = plan[2];
                cardScale = Math.max(0.12f, s);
                break;
            }
        }
        // Whatever plan we landed on, force it inside the window. Solving the
        // scale per-plan is not enough on its own, because the plan we accept
        // may be the last one rather than one that actually fit.
        cardScale = Math.min(cardScale,
                ((width - 12f - SPINE - 28f) / (2 * cols) - 8f) / CardRenderer.CARD_W);
        cardScale = Math.min(cardScale,
                (((avail - headerH - FOOTER_H) / (float) rows) - 8f) / CardRenderer.CARD_H);
        cardScale = Math.max(0.08f, cardScale);

        perPage = cols * rows;
        perSpread = 2 * perPage;
        cellW = Math.round(CardRenderer.CARD_W * cardScale) + 8;
        cellH = Math.round(CardRenderer.CARD_H * cardScale) + 8;

        pageW = cols * cellW;
        panelW = 2 * pageW + SPINE + 28;
        panelH = headerH + rows * cellH + FOOTER_H;
        panelX = Math.max(0, (width - panelW) / 2);
        // centre in the band left between the tabs and the hint, and never let
        // either end escape the window
        int band = Math.max(0, avail - panelH);
        panelY = Math.max(TAB_RESERVE, Math.min(TAB_RESERVE + band / 2,
                Math.max(TAB_RESERVE, height - HINT_RESERVE - panelH)));
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
        if (spread < cardSpreads + AWARD_SPREADS) return Section.AWARDS;
        int back = spread - cardSpreads - AWARD_SPREADS;
        if (back == 0) return Section.SETS;
        return back <= PROFILE_SPREADS ? Section.PROFILE : Section.SETTINGS;
    }

    /** The first spread of a section, for the tabs to jump to. */
    private int firstSpread(Section s) {
        return switch (s) {
            case CARDS -> 0;
            case AWARDS -> cardSpreads;
            case SETS -> cardSpreads + AWARD_SPREADS;
            case PROFILE -> cardSpreads + AWARD_SPREADS + 1;
            case SETTINGS -> cardSpreads + AWARD_SPREADS + 1 + PROFILE_SPREADS;
        };
    }

    private void layoutTabs() {
        tabs.clear();
        int x = panelX + panelW - 12;
        // laid out right to left so the rightmost tab is the last leaf
        Section[] keys = {Section.SETTINGS, Section.PROFILE, Section.SETS,
                Section.AWARDS, Section.CARDS};
        for (Section key : keys) {
            int w = font.width(tabLabel(key)) + 14;
            x -= w + 3;
            tabs.add(new Chip("tab_" + key.name(), x, panelY - 13, w, 15));
        }
    }

    private static String tabLabel(Section s) {
        return switch (s) {
            case CARDS -> "Cards";
            case AWARDS -> "Awards";
            case SETS -> "Sets";
            case PROFILE -> "Profile";
            case SETTINGS -> "Settings";
        };
    }

    // --- the scaled coordinate space the back pages are laid out in ----------

    /** Screen -> logical (back-page) coordinate. */
    /** The open book, in screen space. */
    private int[] panelRect() {
        return new int[]{panelX, panelY, panelW, panelH};
    }

    private static int lg(int screen) {
        return Math.round(screen / UI);
    }

    /**
     * The logical bounds of the whole content area, across both leaves. Back
     * pages start right under the masthead: the band below it is reserved for
     * the search box and filter chips, which only the card pages draw, so
     * keeping the gap left a dead strip across the top of every award leaf.
     */
    private int[] contentBounds() {
        return new int[]{lg(panelX + 14), lg(panelY + 48), lg(panelX + panelW - 14), lg(pageBottom)};
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
        // The action chips hang off the RIGHT edge, right to left. They used to
        // grow far enough left that Store ended up floating near the middle of
        // the spread, over the spine, with a gap either side of it. Laying them
        // out as one measured group and pushing the whole thing right keeps them
        // together and keeps the centre of the book clear.
        String sortLabel = "Sort: " + sort.label;
        String[] actions = {"Profile", sortLabel, "Deck", "Store"};
        String[] keys = {"stats", "sort", "deck", "store"};
        int right = panelX + panelW - 12;
        for (int i = 0; i < actions.length; i++) {
            int w = font.width(actions[i]) + 8;
            right -= w;
            chips.add(new Chip(keys[i], right, y, w, 12));
            right -= 4;
        }
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
        cardSpreads = Math.max(1, (view.size() + perSpread - 1) / perSpread);
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
        boolean right = slotInSpread >= perPage;
        int idx = right ? slotInSpread - perPage : slotInSpread;
        int col = idx % cols, row = idx / cols;
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
        hoveredSetting = null;
        hoveredCard = null;
        hoverRect = null;
        cardAction = null;
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
        if (section == Section.CARDS && !view.isEmpty()) {
            // The spine only belongs on the card pages; the back pages run
            // across both leaves and would be cut in half by it. An EMPTY
            // spread drops it too — with nothing to divide, the line just ran
            // straight through the "no cards match" message.
            g.fill(panelX + panelW / 2 - 1, gridTop - 6, panelX + panelW / 2 + 1,
                    panelY + panelH - 24, CardRenderer.KRAFT_DARK);
        }

        drawMasthead(g);

        if (section == Section.CARDS) {
            search.render(g, mouseX, mouseY, partialTick);
            renderChips(g, mouseX, mouseY);
            renderCardPages(g, mouseX, mouseY);
        } else {
            // back pages are laid out in logical units and drawn through UI, so
            // hit-testing has to happen in the same space — see clickHotspots
            int lmx = lg(mouseX);
            int lmy = lg(mouseY);
            var pose = g.pose();
            pose.pushPose();
            pose.scale(UI, UI, 1f);
            switch (section) {
                case AWARDS -> {
                    int[] page = AWARD_PAGES.get(
                            Mth.clamp(spread - cardSpreads, 0, AWARD_PAGES.size() - 1));
                    renderAwardPage(g, Achievement.Group.values()[page[0]],
                            page[1], page[2], lmx, lmy);
                }
                case SETS -> renderSetRewardsPage(g, lmx, lmy);
                case PROFILE -> renderProfilePage(g);
                default -> renderSettingsPage(g, lmx, lmy);
            }
            pose.popPose();
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
                case CARDS -> "Click a card to view it  ·  hover it to File or Take out";
                case AWARDS -> "Press Collect on a finished award and the reward lands straight in your inventory";
                case SETS -> "Complete every mob in a set to choose one of them as a spawn egg";
                case PROFILE -> "Everything you have done, counted — this page updates itself as you play";
                case SETTINGS -> "Settings are yours alone — they change how the mod looks, never how it plays";
            };
        }
        if (font.width(hint) > width - 8) {
            hint = font.plainSubstrByWidth(hint, width - 16) + "…";
        }
        g.drawString(font, hint, (width - font.width(hint)) / 2, panelY + panelH + 8, hintColor, true);

        boolean overlay = pickerMob != null || eggPicker != null;
        // Everything from here up is an overlay ON the book, so it all rides at
        // z=400 the way vanilla tooltips do. The grid's live mob portraits are
        // 3D models drawn with depth around z 50-150, and a panel left at z=0
        // gets them poking straight through its face — mobs walking over the
        // tooltip text was exactly that.
        var overPose = g.pose();
        overPose.pushPose();
        overPose.translate(0, 0, 400);
        if (hoveredSetting != null && !overlay) {
            drawInfoTooltip(g, mouseX, mouseY, hoveredSetting.label(), hoveredSetting.detail(), 0xFF3FA7D6);
        } else if (hoveredCard != null && !overlay) {
            drawCardTooltip(g, mouseX, mouseY, hoveredCard);
        } else if (hoveredAward != null && !overlay) {
            Achievement a = hoveredAward;
            String need = a.description() + ".  Progress " + ClientAwards.progress(a) + " / " + a.target()
                    + (ClientAwards.isClaimed(a) ? ".  Already collected."
                        : ClientAwards.isCollectable(a) ? ".  Ready — press Collect."
                        : ".")
                    + "  Reward: " + a.rewardLabel() + ".";
            drawInfoTooltip(g, mouseX, mouseY, a.title(), need, a.group().accent());
        }
        if (pickerMob != null) renderPicker(g, mouseX, mouseY);
        if (eggPicker != null) renderEggPicker(g, mouseX, mouseY);
        overPose.popPose();
    }

    /** Cut a typed search down to something quotable in the empty-page note. */
    private String fitHint(String typed) {
        String t = typed.strip();
        return t.length() <= 18 ? t : t.substring(0, 17) + "…";
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
            g.drawString(font, tabLabel(s), tab.x() + 7, tab.y() + 4,
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
        int start = spread * perSpread;
        if (view.isEmpty()) {
            // A deliberate empty page rather than a stray line: centred in the
            // card area (the spine is suppressed for it), with the way out
            // underneath. It used to sit high on the spread with the spine
            // running straight through it, which read as a rendering bug.
            int myPos = gridTop + (panelY + panelH - 30 - gridTop) / 2 - 10;
            g.drawCenteredString(font, "No cards match", width / 2, myPos, CardRenderer.INK);
            String why = search.getValue().isBlank()
                    ? "this filter has nothing to show"
                    : "nothing is called \"" + fitHint(search.getValue()) + "\"";
            g.drawCenteredString(font, why, width / 2, myPos + 12, CardRenderer.KRAFT_DARK);
            g.drawCenteredString(font, "try another search or filter", width / 2, myPos + 24,
                    CardRenderer.KRAFT_DARK);
        }
        boolean overlayOpen = pickerMob != null || eggPicker != null;
        for (int s = 0; s < perSpread; s++) {
            int i = start + s;
            if (i >= view.size()) break;
            MobCard card = view.get(i);
            int[] p = slotPos(s);
            int cx = p[0], cy = p[1];
            g.fill(cx + 2, cy + 3, cx + cw + 4, cy + ch + 5, 0x44000000);

            boolean hovered = !overlayOpen
                    && mouseX >= cx && mouseX < cx + cw && mouseY >= cy && mouseY < cy + ch;
            drawFrontSlot(g, card, cx, cy, cw, ch, hovered, overlayOpen, mouseX, mouseY);
        }
    }

    /** One card the right way up — exactly what the grid drew before the flip. */
    private void drawFrontSlot(GuiGraphics g, MobCard card, int cx, int cy, int cw, int ch,
                               boolean hovered, boolean overlayOpen, int mouseX, int mouseY) {
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
                hoveredCard = card;
                // the cell INCLUDING its File/Take control, so the tooltip
                // is never placed over the button it is telling you about
                hoverRect = new int[]{cx - 2, cy - 2, cw + 4, ch + 16};
                drawCardAction(g, card, foil, cx, cy, cw, ch, mouseX, mouseY);
            }
        } else {
            // a card you are missing: a silhouette on its own set's scene,
            // named only once you have actually killed one
            boolean met = ClientCollection.killCount(card.id()) > 0;
            CardRenderer.renderUnknown(g, font, card, cx, cy, cardScale,
                    overlayOpen ? null
                            : CardRenderer.portraitEntity(minecraft, card, entityCache), met);
            if (hovered) {
                g.renderOutline(cx - 2, cy - 2, cw + 4, ch + 4, 0x99F9D849);
                hoveredCard = card;
                hoverRect = new int[]{cx - 2, cy - 2, cw + 4, ch + 4};
            }
        }
    }

    /**
     * The File / Take out control on the hovered card. The binder holds one of
     * each card; this is how a single card goes in or comes back out, for
     * anyone whose plan is to sell them rather than keep them.
     */
    private void drawCardAction(GuiGraphics g, MobCard card, boolean foil,
                                int cx, int cy, int cw, int ch, int mouseX, int mouseY) {
        boolean filed = ClientCollection.isStored(card.id(), foil);
        boolean holding = heldCopies(card.id(), foil) > 0;
        if (!filed && !holding) {
            return; // nothing to put in and nothing to take out
        }
        String label = filed ? "Take out" : "File";
        int w = Math.min(cw, font.width(label) + 12);
        int x = cx + (cw - w) / 2;
        int y = cy + ch - 15;
        cardAction = new Chip((filed ? "take_" : "file_") + card.id() + (foil ? ":f" : ""), x, y, w, 13);
        boolean hover = cardAction.hit(mouseX, mouseY);
        int base = filed ? 0xFFB4762A : 0xFF2E7D46;
        g.fill(x, y, x + w, y + 13, hover ? CardRenderer.lighten(base) : base);
        g.renderOutline(x, y, w, 13, hover ? 0xFFFFF0B0 : 0xFF2A1F12);
        g.drawString(font, label, x + (w - font.width(label)) / 2, y + 3, 0xFFFFFFFF, false);
    }

    /** Loose copies of a card in the player's own inventory, counted client-side. */
    private int heldCopies(String mobId, boolean foil) {
        if (minecraft == null || minecraft.player == null) {
            return 0;
        }
        int n = 0;
        var inv = minecraft.player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            MobCard c = MobCardItem.cardOf(s);
            if (c != null && c.id().equals(mobId) && MobCardItem.isFoilCard(s) == foil) {
                n += s.getCount();
            }
        }
        return n;
    }

    /**
     * A hover panel that is placed <em>outside</em> the thing being hovered.
     *
     * <p>Tooltips used to be pinned to the cursor, which put them straight over
     * the row's own Collect button and three cards either side. This tries
     * below, above, right and left of {@code avoid} in turn and takes the first
     * side the panel fits on, so the control you are reading about stays
     * visible while you read about it. Body text is wrapped to a narrow column
     * rather than running to whatever width the longest line happens to want.
     */
    private void drawHoverPanel(GuiGraphics g, int mouseX, int mouseY, String title, int accent,
                                List<String> lines, List<Integer> colors, int[] avoid) {
        int maxW = Math.max(120, Math.min(190, width - 24));
        // When the target is as wide as the book, the panel can only go beside
        // it — so size it to the margin that is actually there rather than to a
        // fixed width that then cannot fit anywhere and falls back onto the
        // cursor, which is the covering-the-button behaviour we are fixing.
        if (avoid != null && avoid[2] > width / 2) {
            int room = Math.max(avoid[0] - 10, width - (avoid[0] + avoid[2]) - 10);
            if (room >= 90) {
                maxW = Mth.clamp(room - 12, 90, maxW);
            }
        }
        List<net.minecraft.util.FormattedCharSequence> body = new ArrayList<>();
        List<Integer> bodyColor = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            for (var wrapped : font.split(Component.literal(lines.get(i)), maxW)) {
                body.add(wrapped);
                bodyColor.add(colors.get(Math.min(i, colors.size() - 1)));
            }
        }
        int textW = font.width(title);
        for (var line : body) textW = Math.max(textW, font.width(line));
        int w = Math.min(maxW + 12, textW + 12);
        int h = 12 + body.size() * 10 + 8;

        int[] pos = place(w, h, mouseX, mouseY, avoid);
        int x = pos[0], y = pos[1];

        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF241C10);
        g.fill(x, y, x + w, y + h, CardRenderer.FACE);
        g.fill(x, y, x + w, y + 1, accent);
        g.drawString(font, title, x + 6, y + 5, CardRenderer.INK, false);
        int ly = y + 17;
        for (int i = 0; i < body.size(); i++) {
            g.drawString(font, body.get(i), x + 6, ly, bodyColor.get(i), false);
            ly += 10;
        }
    }

    /** Pick the first side of {@code avoid} the panel fits on; else hug the cursor. */
    private int[] place(int w, int h, int mouseX, int mouseY, int[] avoid) {
        if (avoid != null) {
            int ax = avoid[0], ay = avoid[1], aw = avoid[2], ah = avoid[3];
            int alignX = Mth.clamp(ax, 2, Math.max(2, width - w - 2));
            int alignY = Mth.clamp(ay, 2, Math.max(2, height - h - 2));
            int[] below = {alignX, ay + ah + 4};
            int[] above = {alignX, ay - h - 4};
            int[] right = {ax + aw + 6, alignY};
            int[] left = {ax - w - 6, alignY};
            // something as wide as the book has to be dodged sideways; a small
            // card cell is better dodged downwards, nearer the cursor
            int[][] tries = aw > width / 2
                    ? new int[][]{right, left, below, above}
                    : new int[][]{below, right, above, left};
            for (int[] t : tries) {
                if (t[0] >= 2 && t[1] >= 2 && t[0] + w <= width - 2 && t[1] + h <= height - 2) {
                    return t;
                }
            }
        }
        return new int[]{Math.min(mouseX + 12, Math.max(2, width - w - 2)),
                Mth.clamp(mouseY + 12, 2, Math.max(2, height - h - 2))};
    }

    /**
     * What you may know about a card you have not collected: where it lives,
     * its number in the set, how often it drops, and whether you have ever met
     * one. Never its tier, its rarity or a single stat — those are the reward
     * for finding it.
     */
    private void drawUnknownTooltip(GuiGraphics g, int mouseX, int mouseY, MobCard card) {
        int kills = ClientCollection.killCount(card.id());
        List<String> lines = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        lines.add("Not in your collection");
        colors.add(0xFF9A9083);
        lines.add("No. " + (MobCards.ordinal(card.id()) + 1) + " / " + MobCards.ALL.size()
                + (card.category() == null ? "" : "  ·  " + card.category().label()));
        colors.add(0xFF6E6154);
        lines.add(kills > 0
                ? "You have hunted " + kills + " of these"
                : "You have never met one");
        colors.add(kills > 0 ? 0xFF1C7FA8 : 0xFF9A9083);
        lines.add("Its card drops 1 in " + Math.round(1f / card.tier().cardDropChance()));
        colors.add(0xFF6E6154);
        drawHoverPanel(g, mouseX, mouseY,
                kills > 0 ? card.displayName() : "? ? ? ?", 0xFF8A8072, lines, colors, hoverRect);
    }

    /** Everything about a card, on hover: tier, the stats, the hunt, where it is. */
    /** "1st" / "2nd" / "3rd" / "11th" — for the rarity placing. */
    private static String ordinalWord(int n) {
        int mod100 = n % 100;
        if (mod100 >= 11 && mod100 <= 13) {
            return n + "th";
        }
        return n + switch (n % 10) {
            case 1 -> "st";
            case 2 -> "nd";
            case 3 -> "rd";
            default -> "th";
        };
    }

    private void drawCardTooltip(GuiGraphics g, int mouseX, int mouseY, MobCard card) {
        if (!ClientCollection.has(card.id())) {
            drawUnknownTooltip(g, mouseX, mouseY, card);
            return;
        }
        boolean foil = ClientCollection.displayedIsFoil(card.id());
        int level = ClientCollection.displayLevel(card.id(), foil);
        MobCard shown = card.upgraded(level);
        int total = 0;
        for (Stat st : Stat.values()) total += shown.stat(st);
        int kills = ClientCollection.killCount(card.id());
        Tier tier = card.tier();

        List<String> lines = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        lines.add(tier.label().toUpperCase(Locale.ROOT) + "  ·  Rarity " + card.rarity() + " (lower wins)");
        colors.add(CardRenderer.tierPrintColor(card));
        lines.add("No. " + (MobCards.ordinal(card.id()) + 1) + " / " + MobCards.ALL.size()
                + (card.category() == null ? "" : "  ·  " + card.category().label()));
        colors.add(0xFF6E6154);
        lines.add("Mob rating " + total + "  ·  drops 1 in "
                + Math.round(1f / tier.cardDropChance()));
        colors.add(0xFF6E6154);
        lines.add("Hunted " + kills + " time" + (kills == 1 ? "" : "s"));
        colors.add(0xFF6E6154);
        int next = tier.nextMilestone(kills);
        lines.add(level > 0 ? "Holo " + "I".repeat(Math.min(3, level))
                        + (next < 0 ? " — fully upgraded" : "  ·  next at " + next + " kills")
                : (next < 0 ? "No holo" : "Holo at " + next + " kills"));
        colors.add(level > 0 ? 0xFF8746C9 : 0xFF9A9083);

        // What the server has actually printed, which is the only rarity
        // number that is about this world rather than about the drop tables.
        int printed = ClientCensus.printedOf(card.id());
        if (printed >= 0) {
            int place = ClientCensus.rarityPlace(card.id());
            String census = printed == 0
                    ? "Never printed on this server"
                    : printed + " printed here"
                            + (place > 0 ? "  ·  " + ordinalWord(place) + " rarest of "
                                    + ClientCensus.printedKinds() : "");
            lines.add(census);
            colors.add(printed == 0 ? 0xFF8746C9 : 0xFF6E6154);
        }

        boolean filed = ClientCollection.isStored(card.id(), foil);
        int held = heldCopies(card.id(), foil);
        String where = filed ? "Filed in this book — Take out to pocket it"
                : held > 0 ? held + " in your inventory — File to store one here"
                : "Not in your book or your inventory";
        lines.add(where);
        colors.add(filed ? 0xFF2E8B3A : held > 0 ? 0xFF1C7FA8 : 0xFF9A9083);

        drawHoverPanel(g, mouseX, mouseY, card.displayName(),
                CardRenderer.tierPrintColor(card), lines, colors, hoverRect);
    }

    // --- award pages --------------------------------------------------------

    private void renderAwardPage(GuiGraphics g, Achievement.Group group, int page, int pages,
                                 int mouseX, int mouseY) {
        List<Achievement> all = Achievements.of(group);
        // the slice this spread is responsible for
        int from = Math.min(page * AWARDS_PER_SPREAD, all.size());
        int to = Math.min(from + AWARDS_PER_SPREAD, all.size());
        List<Achievement> list = all.subList(from, to);
        int[] b = contentBounds();
        int x0 = b[0], x1 = b[2];

        // the tally stays over the WHOLE group, not the slice — "3 / 21" is
        // what a player wants to know, on whichever page they are looking at
        String done = countCollected(all) + " / " + all.size();
        String heading = group.label().toUpperCase(Locale.ROOT)
                + (pages > 1 ? "  (" + (page + 1) + "/" + pages + ")" : "");
        int y = drawPageHeading(g, x0, x1, b[1], group.accent(),
                heading, group.blurb(), done);

        // A group used to be one full-width column, with a row height that
        // could not go below sixteen — so a long group simply ran off the foot
        // of the page and the awards past the fold were invisible AND
        // unclickable. The Parlour has twenty-one. Long groups now break across
        // the two leaves of the spread, which is what the second leaf is for.
        int available = b[3] - y;
        int n = list.size();
        int perColumn = n;
        boolean twoUp = n * 16 > available;
        if (twoUp) {
            perColumn = (n + 1) / 2;
        }
        int rowH = Math.max(twoUp ? 12 : 16,
                Math.min(30, available / Math.max(1, perColumn)));
        int spine = lg(panelX + panelW / 2);
        for (int i = 0; i < n; i++) {
            int cx0 = x0;
            int cx1 = x1;
            if (twoUp) {
                boolean right = i >= perColumn;
                cx0 = right ? spine + 8 : x0;
                cx1 = right ? x1 : spine - 8;
            }
            int ry = y + (i % perColumn) * rowH;
            if (ry + rowH > b[3]) {
                continue; // never draw a row the page cannot hold
            }
            drawAwardRow(g, list.get(i), cx0, cx1, ry, rowH, mouseX, mouseY);
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
            hoverRect = panelRect();
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

    private void renderSetRewardsPage(GuiGraphics g, int mouseX, int mouseY) {
        int[] b = contentBounds();
        int x0 = b[0], x1 = b[2];
        int y = drawPageHeading(g, x0, x1, b[1], 0xFFB57EDC, "SET REWARDS",
                "Finish a set and keep one of its mobs as a spawn egg — one choice, forever", null);

        Category[] cats = Category.values();
        int rowH = Math.max(16, Math.min(30, (b[3] - y) / cats.length));
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

    // --- profile leaf -------------------------------------------------------

    /**
     * One line of the profile. A {@code heading} row is a section title rather
     * than a statistic, which is why it carries no value.
     */
    private record ProfileRow(String label, String value, int color, boolean heading) {

        static ProfileRow of(String label, String value) {
            return new ProfileRow(label, value, CardRenderer.INK, false);
        }

        static ProfileRow of(String label, int value) {
            return of(label, String.valueOf(value));
        }

        static ProfileRow head(String label, int color) {
            return new ProfileRow(label, "", color, true);
        }
    }

    /**
     * Ranked standing. Seven rows, because the smallest window the book opens
     * in leaves room for eight lines in a leaf -- see tools/checkprofilepage.py.
     */
    private List<ProfileRow> profileRanked(ProfileSyncPayload p) {
        List<ProfileRow> rows = new ArrayList<>();
        int rating = p.num(ProfileSyncPayload.RATING);
        RankTier tier = RankTier.of(rating);
        rows.add(ProfileRow.head("RANKED", tier.rgb));
        rows.add(new ProfileRow("Rank", RankTier.label(rating), tier.rgb, false));
        rows.add(ProfileRow.of("Rating", rating + "   (best " + p.num(ProfileSyncPayload.PEAK) + ")"));
        int place = p.num(ProfileSyncPayload.PLACE);
        rows.add(ProfileRow.of("Standing", place <= 0 ? "unranked"
                : "#" + place + " of " + p.num(ProfileSyncPayload.TOTAL_RANKED)));
        rows.add(ProfileRow.of("Record", p.num(ProfileSyncPayload.RANKED_WINS) + "W  "
                + p.num(ProfileSyncPayload.RANKED_LOSSES) + "L"));
        int streak = p.num(ProfileSyncPayload.STREAK);
        rows.add(new ProfileRow("Streak", streak + "   (best "
                + p.num(ProfileSyncPayload.STREAK_BEST) + ")",
                streak >= 3 ? 0xFFB8860B : CardRenderer.INK, false));
        rows.add(ProfileRow.of("Season " + p.num(ProfileSyncPayload.SEASON),
                p.num(ProfileSyncPayload.BADGES) + " badges  ·  "
                + p.num(ProfileSyncPayload.GIANT) + " giants"));
        return rows;
    }

    /** Who you actually play, and how it has gone. */
    private List<ProfileRow> profileRivals(ProfileSyncPayload p, int shown) {
        List<ProfileRow> rows = new ArrayList<>();
        rows.add(ProfileRow.head("HEAD TO HEAD", 0xFFB57EDC));
        if (shown <= 0) {
            rows.add(new ProfileRow("No duels yet", "", 0xFF9A9083, false));
        }
        for (int i = 0; i < shown; i++) {
            int w = p.rivalWins(i);
            int l = p.rivalLosses(i);
            rows.add(new ProfileRow(p.rivalName(i), w + "\u2013" + l,
                    w > l ? 0xFF3D8B3D : w < l ? 0xFF9E4444 : CardRenderer.INK, false));
        }
        return rows;
    }

    private List<ProfileRow> profileGames(ProfileSyncPayload p) {
        List<ProfileRow> rows = new ArrayList<>();
        rows.add(ProfileRow.head("GAMES PLAYED", 0xFF3FA7D6));
        rows.add(ProfileRow.of("Games", p.num(ProfileSyncPayload.GAMES)));
        rows.add(ProfileRow.of("Duel wins", p.num(ProfileSyncPayload.DUEL_WINS)));
        rows.add(ProfileRow.of("CPU wins", p.num(ProfileSyncPayload.CPU_TOTAL) + "   ("
                + p.num(ProfileSyncPayload.CPU_EASY) + "/"
                + p.num(ProfileSyncPayload.CPU_NORMAL) + "/"
                + p.num(ProfileSyncPayload.CPU_HARD) + ")"));
        rows.add(ProfileRow.of("Twenty-One", p.num(ProfileSyncPayload.T21_WINS) + " won  ·  "
                + p.num(ProfileSyncPayload.T21_EXACT) + " exact"));
        rows.add(ProfileRow.of("Guess Who", p.num(ProfileSyncPayload.GW_WINS) + " won  ·  "
                + p.num(ProfileSyncPayload.GW_SHARP) + " sharp"));
        rows.add(ProfileRow.of("Bluff", p.num(ProfileSyncPayload.BLUFF_WINS) + "W  "
                + p.num(ProfileSyncPayload.BLUFF_LOSSES) + "L  ·  "
                + p.num(ProfileSyncPayload.BLUFF_CATCHES) + " caught"));
        return rows;
    }

    private List<ProfileRow> profileCollection(ProfileSyncPayload p) {
        List<ProfileRow> rows = new ArrayList<>();
        rows.add(ProfileRow.head("COLLECTION", 0xFF55A82F));
        int total = MobCards.ALL.size();
        int have = p.num(ProfileSyncPayload.COLLECTED);
        rows.add(ProfileRow.of("Cards", have + " / " + total
                + "   (" + Math.round(have * 100f / Math.max(1, total)) + "%)"));
        rows.add(ProfileRow.of("Holographic", p.num(ProfileSyncPayload.FOILS) + " / " + total));
        rows.add(ProfileRow.of("Holo III", p.num(ProfileSyncPayload.HOLO_MAX)));
        rows.add(ProfileRow.of("Filed in book", ClientCollection.storedCount()));
        rows.add(ProfileRow.of("Sets finished", p.num(ProfileSyncPayload.SETS_DONE) + " / "
                + p.num(ProfileSyncPayload.SETS_TOTAL)));
        rows.add(ProfileRow.of("Awards", p.num(ProfileSyncPayload.AWARDS_CLAIMED) + " / "
                + p.num(ProfileSyncPayload.AWARDS_TOTAL)));
        rows.add(ProfileRow.of("Mobs hunted", p.num(ProfileSyncPayload.KILLS)));
        return rows;
    }

    /**
     * The Profile pages: everything the mod counts about you.
     *
     * <p>Two spreads rather than one because a 320x240 window leaves eight
     * lines in a leaf, and there are twenty-five things to say. The identity
     * strip is drawn only once the rows are known to fit, so decoration can
     * never be what pushes a statistic off the page.
     *
     * <p>The numbers come from the server rather than being recomputed here, so
     * the page cannot disagree with the award sitting beside it. The request is
     * throttled inside {@link ClientProfile}: asking every frame costs one small
     * packet a second, and only while this page is the one being looked at.
     */
    private void renderProfilePage(GuiGraphics g) {
        ClientProfile.request();
        ProfileSyncPayload p = ClientProfile.state();
        boolean standing = spread == firstSpread(Section.PROFILE);

        int[] b = contentBounds();
        int x0 = b[0], x1 = b[2], bottom = b[3];
        int rating = p.num(ProfileSyncPayload.RATING);
        RankTier tier = RankTier.of(rating);

        List<ProfileRow> leftRows;
        List<ProfileRow> rightRows;
        int y;
        if (standing) {
            y = drawPageHeading(g, x0, x1, b[1], tier.rgb, "PLAYER PROFILE",
                    p.text(ProfileSyncPayload.T_TITLE), null);
            leftRows = profileRanked(p);
            rightRows = profileRivals(p, p.rivalCount());
        } else {
            y = drawPageHeading(g, x0, x1, b[1], 0xFF3FA7D6, "THE RECORD",
                    "Every game you have played and every card you have found", null);
            leftRows = profileGames(p);
            rightRows = profileCollection(p);
        }

        int rowH = 9;
        int need = Math.max(leftRows.size(), rightRows.size());
        // Rivals are the only variable block, so they give way first.
        int rivals = p.rivalCount();
        while (standing && need * rowH > bottom - y && rivals > 0) {
            rivals--;
            rightRows = profileRivals(p, rivals);
            need = Math.max(leftRows.size(), rightRows.size());
        }
        // Rows first, decoration second: the identity strip is only drawn when
        // the page already has room for every line without it.
        int emblem = 26;
        int strip = emblem + 10;
        if (standing && need * rowH + strip <= bottom - y) {
            y = drawIdentityStrip(g, p, x0, x1, y, emblem, rating);
        }
        // Spend any space left over on breathing room between the lines.
        rowH = Mth.clamp((bottom - y) / Math.max(1, need), rowH, 13);

        int gap = 10;
        int colW = (x1 - x0 - gap) / 2;
        drawProfileColumn(g, leftRows, x0, y, colW, rowH);
        drawProfileColumn(g, rightRows, x0 + colW + gap, y, colW, rowH);
    }

    /** Emblem, name and the one-line summary. Returns the new top of the rows. */
    private int drawIdentityStrip(GuiGraphics g, ProfileSyncPayload p, int x0, int x1, int y,
                                  int emblem, int rating) {
        String name = p.text(ProfileSyncPayload.T_NAME);
        if (name.isEmpty() && minecraft != null && minecraft.player != null) {
            name = minecraft.player.getGameProfile().getName();
        }
        g.fill(x0, y, x1, y + emblem + 4, 0x0E000000);
        RankEmblem.draw(g, x0 + 3, y + 2, emblem, rating);
        int textX = x0 + emblem + 10;
        g.drawString(font, trim(name.toUpperCase(Locale.ROOT), x1 - textX - 4),
                textX, y + 3, CardRenderer.INK, false);
        String fav = p.text(ProfileSyncPayload.T_FAVOURITE);
        String nemesis = p.text(ProfileSyncPayload.T_NEMESIS);
        StringBuilder sub = new StringBuilder(RankTier.label(rating));
        if (!fav.isEmpty()) {
            sub.append("   ·   favours ").append(fav);
        }
        if (!nemesis.isEmpty()) {
            sub.append("   ·   nemesis ").append(nemesis);
        }
        g.drawString(font, trim(sub.toString(), x1 - textX - 4), textX, y + 14, 0xFF8B8074, false);
        return y + emblem + 10;
    }

    private void drawProfileColumn(GuiGraphics g, List<ProfileRow> rows, int x, int y,
                                   int w, int rowH) {
        for (int i = 0; i < rows.size(); i++) {
            ProfileRow row = rows.get(i);
            int ry = y + i * rowH;
            if (row.heading()) {
                g.drawString(font, row.label(), x, ry, row.color(), false);
                g.fill(x, ry + 9, x + w, ry + 10, (row.color() & 0x00FFFFFF) | 0x55000000);
            } else {
                int valueW = font.width(row.value());
                g.drawString(font, trim(row.label(), w - valueW - 6), x, ry,
                        CardRenderer.KRAFT_DARK, false);
                g.drawString(font, row.value(), x + w - valueW, ry, row.color(), false);
            }
        }
    }

    // --- settings leaf ------------------------------------------------------

    private void renderSettingsPage(GuiGraphics g, int mouseX, int mouseY) {
        int[] b = contentBounds();
        int x0 = b[0], x1 = b[2];
        int y = drawPageHeading(g, x0, x1, b[1], 0xFF3FA7D6, "SETTINGS",
                "Saved on this computer, for every world — hover a row for the full story", null);

        int rowH = Math.max(18, Math.min(34, (b[3] - y) / SETTINGS.size()));
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

        boolean rowHover = mouseX >= x0 && mouseX < x1 && mouseY >= y && mouseY < y + rowH - 2;
        if (rowHover) {
            hoveredSetting = s;
            hoverRect = panelRect();
        }
        g.fill(x0, y, x1, y + rowH - 2, rowHover ? 0x18000000 : 0x0A000000);
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

    /**
     * The full explanation of a setting, wrapped into a panel beside the cursor.
     * Drawn in plain screen space (not the back pages' scaled space) and nudged
     * to stay on screen.
     */
    private void drawInfoTooltip(GuiGraphics g, int mouseX, int mouseY, String title,
                                 String detail, int accent) {
        drawHoverPanel(g, mouseX, mouseY, title, accent,
                List.of(detail), List.of(0xFF6E6154), hoverRect);
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

    private void statLine(GuiGraphics g, int labelX, int valueX, int y, String label, String value) {
        g.drawString(font, label, labelX, y, CardRenderer.KRAFT_DARK, false);
        g.drawString(font, value, valueX, y, CardRenderer.INK, false);
    }

    private void renderChips(GuiGraphics g, int mouseX, int mouseY) {
        for (Chip chip : chips) {
            boolean active = switch (chip.key()) {
                case "stats" -> false;
                case "sort", "deck", "store" -> false;
                default -> chip.key().equals("f_" + filter.name());
            };
            boolean hover = chip.hit(mouseX, mouseY);
            int bg = active ? 0xFF55A82F : hover ? 0xFFB99465 : CardRenderer.KRAFT;
            g.fill(chip.x(), chip.y(), chip.x() + chip.w(), chip.y() + chip.h(), bg);
            g.renderOutline(chip.x(), chip.y(), chip.w(), chip.h(), CardRenderer.KRAFT_DARK);
            String label = switch (chip.key()) {
                case "stats" -> "Profile";
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

    /**
     * Buttons on the award / set / settings leaves and in the egg picker. Back
     * pages record their rects in the scaled logical space they are drawn in,
     * so the cursor has to be converted the same way before it is compared.
     */
    private boolean clickHotspots(double mouseX, double mouseY) {
        boolean scaled = section() != Section.CARDS && eggPicker == null;
        double mx = scaled ? mouseX / UI : mouseX;
        double my = scaled ? mouseY / UI : mouseY;
        for (Chip spot : hotspots) {
            if (!spot.hit(mx, my)) continue;
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
        // the File / Take out pill sits on top of the card and wins the click
        if (cardAction != null && cardAction.hit(mouseX, mouseY)) {
            String key = cardAction.key();
            boolean foil = key.endsWith(":f");
            String id = key.substring(key.indexOf('_') + 1, foil ? key.length() - 2 : key.length());
            PacketDistributor.sendToServer(key.startsWith("take_")
                    ? StorageActionPayload.withdraw(id, foil)
                    : StorageActionPayload.deposit(id, foil));
            clickSound();
            return true;
        }
        int cw = Math.round(CardRenderer.CARD_W * cardScale);
        int ch = Math.round(CardRenderer.CARD_H * cardScale);
        int startIdx = spread * perSpread;
        for (int s = 0; s < perSpread; s++) {
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
            // The Stats chip used to open a small overlay showing a subset of
            // these numbers. It now turns to the page that shows all of them,
            // so there is one place to look rather than two that overlap.
            case "stats" -> { spread = firstSpread(Section.PROFILE); layoutChips(); }
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
        if (pickerMob != null || eggPicker != null) return true;
        if (sy < 0 && spread < spreadCount - 1) { flip(1); return true; }
        if (sy > 0 && spread > 0) { flip(-1); return true; }
        return super.mouseScrolled(mouseX, mouseY, sx, sy);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (pickerMob != null && keyCode == 256) { pickerMob = null; return true; }
        if (eggPicker != null && keyCode == 256) { eggPicker = null; return true; }
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
