package com.jrpetty.mobtrumps.client;

import com.jrpetty.mobtrumps.DeckManager;
import com.jrpetty.mobtrumps.SetDeckPayload;
import com.jrpetty.mobtrumps.game.MobCard;
import com.jrpetty.mobtrumps.game.MobCards;
import com.jrpetty.mobtrumps.game.Stat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Build a battle deck from the cards you own: exactly
 * {@link DeckManager#DECK_SIZE}, the hand every game is played with.
 *
 * <p>Your six sit in a row of slots along the top — click one to take it out
 * again — and everything you own is in the grid beneath; click a card to put
 * it in the first free slot. Auto-pick fills the empty slots with your
 * strongest cards at the level you would field them. Save keeps the deck for
 * the dueling table and the campaign.
 */
public class DeckBuilderScreen extends Screen {

    private static final int BTN_H = 14;
    private static final int ARROW_W = 18;

    private final Screen parent;
    private final Map<String, LivingEntity> entityCache = new HashMap<>();
    private final List<MobCard> owned = new ArrayList<>();
    /** The deck in slot order; never longer than a hand. */
    private final List<String> deck = new ArrayList<>();

    private int page;
    private int cols, rows, perPage, pageCount;
    private float gridScale;
    private float slotScale;
    private int cellW, cellH, gridX, gridY;
    private int panelX, panelY, panelW, panelH;
    private int slotsY;
    private int prevX, nextX, arrowY, saveX, saveW, autoX, autoW, footY;

    public DeckBuilderScreen(Screen parent) {
        super(Component.literal("Deck Builder"));
        this.parent = parent;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        if (parent != null && minecraft != null) {
            minecraft.setScreen(parent);
        } else {
            super.onClose();
        }
    }

    @Override
    protected void init() {
        super.init();
        owned.clear();
        for (MobCard c : MobCards.ALL) {
            if (ClientCollection.has(c.id())) owned.add(c);
        }
        if (deck.isEmpty()) {
            // a deck saved when decks were longer plays its first six — open on those
            for (String id : ClientCollection.deck()) {
                if (deck.size() >= DeckManager.DECK_SIZE) break;
                if (ClientCollection.has(id) && MobCards.byId(id) != null && !deck.contains(id)) deck.add(id);
            }
        }

        panelW = Math.min(width - 16, 560);
        panelH = height - 16;
        panelX = (width - panelW) / 2;
        panelY = 8;

        // the slots: a row of six small cards under the title
        slotsY = panelY + 32;
        slotScale = Mth.clamp((panelW - 40) / (DeckManager.DECK_SIZE * (CardRenderer.CARD_W + 16f)), 0.12f, 0.24f);
        int slotH = Math.round(CardRenderer.CARD_H * slotScale);

        // the grid: whatever height is left, cards as large as one row allows
        footY = panelY + panelH - 22;
        gridY = slotsY + slotH + 12;
        int gridH = Math.max(40, footY - 6 - gridY);
        gridScale = Mth.clamp((gridH - 8) / (float) CardRenderer.CARD_H, 0.16f, 0.46f);
        cellW = Math.round(CardRenderer.CARD_W * gridScale) + 8;
        cellH = Math.round(CardRenderer.CARD_H * gridScale) + 8;
        cols = Math.max(1, (panelW - 24) / cellW);
        rows = Math.max(1, gridH / cellH);
        perPage = cols * rows;
        pageCount = Math.max(1, (owned.size() + perPage - 1) / perPage);
        page = Math.max(0, Math.min(page, pageCount - 1));
        gridX = panelX + (panelW - cols * cellW) / 2;

        arrowY = footY + 3;
        prevX = panelX + 10;
        nextX = panelX + panelW - 10 - ARROW_W;
        saveW = font.width("Save Deck") + 14;
        autoW = font.width("Auto-pick") + 14;
        int mid = panelX + panelW / 2;
        saveX = mid + 3;
        autoX = mid - 3 - autoW;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);

        g.fill(panelX - 3, panelY - 3, panelX + panelW + 3, panelY + panelH + 3, CardRenderer.KRAFT_DARK);
        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, CardRenderer.KRAFT);
        g.fill(panelX + 6, panelY + 6, panelX + panelW - 6, panelY + panelH - 6, CardRenderer.FACE);

        g.drawCenteredString(font, "BUILD YOUR DECK", width / 2, panelY + 10, CardRenderer.INK);
        boolean full = deck.size() == DeckManager.DECK_SIZE;
        String count = full ? "Your " + DeckManager.DECK_SIZE + " are ready"
                : "Pick " + DeckManager.DECK_SIZE + " cards  ·  " + deck.size() + " / " + DeckManager.DECK_SIZE;
        g.drawCenteredString(font, count, width / 2, panelY + 21, full ? 0xFF3D8B3D : 0xFFB5502E);

        // --- the six slots ---
        int sw = Math.round(CardRenderer.CARD_W * slotScale);
        int sh = Math.round(CardRenderer.CARD_H * slotScale);
        for (int i = 0; i < DeckManager.DECK_SIZE; i++) {
            int[] r = slotRect(i);
            MobCard card = i < deck.size() ? MobCards.byId(deck.get(i)) : null;
            if (card == null) {
                g.fill(r[0], r[1], r[0] + sw, r[1] + sh, 0x22000000);
                g.renderOutline(r[0], r[1], sw, sh, 0x665F4A32);
                String n = String.valueOf(i + 1);
                g.drawString(font, n, r[0] + (sw - font.width(n)) / 2, r[1] + sh / 2 - 4, 0x885F4A32, false);
            } else {
                boolean foil = ClientCollection.hasFoil(card.id());
                int level = ClientCollection.displayLevel(card.id(), foil);
                CardRenderer.renderCard(g, font, card, level, r[0], r[1], slotScale, 0, 0, null, foil, false);
                if (inside(mouseX, mouseY, r[0], r[1], sw, sh)) {
                    g.fill(r[0], r[1], r[0] + sw, r[1] + sh, 0x66B5502E);
                    g.drawString(font, "x", r[0] + sw - 7, r[1] + 1, 0xFFFFFFFF, true);
                }
            }
        }

        // --- everything you own ---
        if (owned.isEmpty()) {
            g.drawCenteredString(font, "Collect some cards first!", width / 2, gridY + 20, CardRenderer.KRAFT_DARK);
        }
        int start = page * perPage;
        int cw = Math.round(CardRenderer.CARD_W * gridScale);
        int ch = Math.round(CardRenderer.CARD_H * gridScale);
        MobCard hoveredCard = null;
        for (int i = start; i < Math.min(start + perPage, owned.size()); i++) {
            MobCard card = owned.get(i);
            int slot = i - start;
            int cx = gridX + (slot % cols) * cellW + 4;
            int cy = gridY + (slot / cols) * cellH + 4;
            boolean inDeck = deck.contains(card.id());
            boolean hovered = inside(mouseX, mouseY, cx, cy, cw, ch);
            g.fill(cx + 2, cy + 3, cx + cw + 3, cy + ch + 4, 0x44000000);
            LivingEntity mob = hovered ? CardRenderer.portraitEntity(minecraft, card, entityCache) : null;
            boolean foil = ClientCollection.hasFoil(card.id());
            int level = ClientCollection.displayLevel(card.id(), foil);
            CardRenderer.renderCard(g, font, card, level, cx, cy, gridScale, mouseX, mouseY, mob, foil, hovered);
            if (inDeck) {
                g.fill(cx, cy, cx + cw, cy + ch, 0x3355E06A);
                g.renderOutline(cx - 2, cy - 2, cw + 4, ch + 4, 0xFF55E06A);
                g.renderOutline(cx - 1, cy - 1, cw + 2, ch + 2, 0xFF55E06A);
                int n = deck.indexOf(card.id()) + 1;
                g.fill(cx + cw - 10, cy + 1, cx + cw - 1, cy + 11, 0xFF2E8B3A);
                g.drawString(font, String.valueOf(n), cx + cw - 8, cy + 2, 0xFFFFFFFF, false);
            } else if (hovered) {
                g.renderOutline(cx - 2, cy - 2, cw + 4, ch + 4, full ? 0xFFB5502E : 0xFFF9D849);
            }
            if (hovered) hoveredCard = card;
        }

        drawButton(g, prevX, arrowY, ARROW_W, "<", page > 0, mouseX, mouseY);
        drawButton(g, nextX, arrowY, ARROW_W, ">", page < pageCount - 1, mouseX, mouseY);
        drawButton(g, autoX, arrowY, autoW, "Auto-pick", !full && deck.size() < owned.size(), mouseX, mouseY);
        drawButton(g, saveX, arrowY, saveW, "Save Deck", true, mouseX, mouseY);
        String pageLabel = (page + 1) + " / " + pageCount;
        g.drawString(font, pageLabel, prevX + ARROW_W + 6, arrowY + 3, CardRenderer.KRAFT_DARK, false);

        if (hoveredCard != null && full && !deck.contains(hoveredCard.id())) {
            g.renderTooltip(font, Component.literal("Your six are picked — click one at the top to swap it out"),
                    mouseX, mouseY);
        }
    }

    /** Slot {@code i} of the row of six: {x, y}. */
    private int[] slotRect(int i) {
        int sw = Math.round(CardRenderer.CARD_W * slotScale);
        int gap = 8;
        int total = DeckManager.DECK_SIZE * sw + (DeckManager.DECK_SIZE - 1) * gap;
        int x0 = panelX + (panelW - total) / 2;
        return new int[]{x0 + i * (sw + gap), slotsY};
    }

    private void drawButton(GuiGraphics g, int x, int y, int w, String label, boolean enabled,
                            int mouseX, int mouseY) {
        boolean hovered = enabled && inside(mouseX, mouseY, x, y, w, BTN_H);
        g.fill(x, y, x + w, y + BTN_H, enabled ? (hovered ? 0xFF6FB84A : 0xFF55A82F) : 0xFFCEC3AF);
        g.renderOutline(x, y, w, BTN_H, CardRenderer.KRAFT_DARK);
        g.drawString(font, label, x + (w - font.width(label)) / 2, y + 3, enabled ? 0xFFFFFFFF : 0xFF9A9083, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (inside(mouseX, mouseY, prevX, arrowY, ARROW_W, BTN_H) && page > 0) { flip(-1); return true; }
            if (inside(mouseX, mouseY, nextX, arrowY, ARROW_W, BTN_H) && page < pageCount - 1) { flip(1); return true; }
            if (inside(mouseX, mouseY, saveX, arrowY, saveW, BTN_H)) { save(); return true; }
            if (inside(mouseX, mouseY, autoX, arrowY, autoW, BTN_H)) { autoPick(); return true; }

            int sw = Math.round(CardRenderer.CARD_W * slotScale);
            int sh = Math.round(CardRenderer.CARD_H * slotScale);
            for (int i = 0; i < deck.size(); i++) {
                int[] r = slotRect(i);
                if (inside(mouseX, mouseY, r[0], r[1], sw, sh)) {
                    deck.remove(i);
                    playClick(0.9f);
                    return true;
                }
            }

            int start = page * perPage;
            int cw = Math.round(CardRenderer.CARD_W * gridScale);
            int ch = Math.round(CardRenderer.CARD_H * gridScale);
            for (int i = start; i < Math.min(start + perPage, owned.size()); i++) {
                MobCard card = owned.get(i);
                int slot = i - start;
                int cx = gridX + (slot % cols) * cellW + 4;
                int cy = gridY + (slot / cols) * cellH + 4;
                if (inside(mouseX, mouseY, cx, cy, cw, ch)) {
                    toggle(card.id());
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void toggle(String id) {
        if (deck.contains(id)) {
            deck.remove(id);
            playClick(0.9f);
        } else if (deck.size() < DeckManager.DECK_SIZE) {
            deck.add(id);
            playClick(1.2f);
        } else {
            playClick(0.6f); // full: swap one out at the top first
        }
    }

    /**
     * Fill the empty slots with your strongest cards not already in: strength
     * is how often a card's best stats beat the rest of the set, at the level
     * you would actually play it.
     */
    private void autoPick() {
        List<MobCard> candidates = new ArrayList<>();
        for (MobCard card : owned) {
            if (!deck.contains(card.id())) candidates.add(card);
        }
        candidates.sort(Comparator.comparingDouble(this::strength).reversed());
        for (MobCard card : candidates) {
            if (deck.size() >= DeckManager.DECK_SIZE) break;
            deck.add(card.id());
        }
        playClick(1.3f);
    }

    private double strength(MobCard card) {
        boolean foil = ClientCollection.hasFoil(card.id());
        MobCard played = card.upgraded(ClientCollection.displayLevel(card.id(), foil));
        double total = 0;
        for (Stat stat : Stat.values()) {
            total += MobCards.winOdds(stat, played.stat(stat));
        }
        return total;
    }

    private void save() {
        PacketDistributor.sendToServer(new SetDeckPayload(new ArrayList<>(deck)));
        if (minecraft != null) {
            minecraft.getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.1f));
        }
        onClose();
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double sx, double sy) {
        if (sy < 0 && page < pageCount - 1) { flip(1); return true; }
        if (sy > 0 && page > 0) { flip(-1); return true; }
        return super.mouseScrolled(mouseX, mouseY, sx, sy);
    }

    private void flip(int dir) {
        page += dir;
        playClick(1.0f);
    }

    private void playClick(float pitch) {
        if (minecraft != null) {
            minecraft.getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), pitch));
        }
    }
}
