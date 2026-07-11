package com.jrpetty.mobtrumps.client;

import com.jrpetty.mobtrumps.game.MobCard;
import com.jrpetty.mobtrumps.game.MobCards;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;

import java.util.HashMap;
import java.util.Map;

/**
 * The collection binder: pages of full Top Trumps cards. Collected mobs
 * show their complete card with the live 3D portrait; unfound mobs lie
 * face down. Flip pages with the arrows, arrow keys or the scroll wheel;
 * click a card to enlarge it.
 */
public class CollectionBookScreen extends Screen {

    private static final float CARD_SCALE = 0.52f;

    private final Map<String, LivingEntity> entityCache = new HashMap<>();
    private int page;

    private int cols, rows, perPage, pageCount;
    private int cellW, cellH, gridX, gridY;
    private int panelX, panelY, panelW, panelH;
    private int prevX, prevY, nextX, nextY;
    private static final int ARROW_W = 18, ARROW_H = 14;

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
        rows = Math.max(1, Math.min(3, (height - 96) / cellH));
        perPage = cols * rows;
        pageCount = (MobCards.ALL.size() + perPage - 1) / perPage;
        page = Math.max(0, Math.min(page, pageCount - 1));

        panelW = cols * cellW + 28;
        panelH = 46 + rows * cellH + 24;
        panelX = (width - panelW) / 2;
        panelY = Math.max(8, (height - panelH) / 2);
        gridX = panelX + 14;
        gridY = panelY + 44;

        int arrowY = panelY + panelH - 20;
        prevX = panelX + 12;
        prevY = arrowY;
        nextX = panelX + panelW - 12 - ARROW_W;
        nextY = arrowY;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);

        // kraft binder with cream page face and gold corner studs
        g.fill(panelX - 3, panelY - 3, panelX + panelW + 3, panelY + panelH + 3, CardRenderer.KRAFT_DARK);
        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, CardRenderer.KRAFT);
        g.fill(panelX + 6, panelY + 6, panelX + panelW - 6, panelY + panelH - 6, CardRenderer.FACE);
        for (int[] c : new int[][]{{panelX + 3, panelY + 3}, {panelX + panelW - 6, panelY + 3},
                {panelX + 3, panelY + panelH - 6}, {panelX + panelW - 6, panelY + panelH - 6}}) {
            g.fill(c[0], c[1], c[0] + 3, c[1] + 3, 0xFFF3E2A7);
        }

        // header: title, progress count and bar
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
        String progress = have + " / " + total + " collected"
                + (foils > 0 ? "   ✦ " + foils + " foil" : "");
        g.drawString(font, progress, panelX + (panelW - font.width(progress)) / 2,
                panelY + 27, CardRenderer.KRAFT_DARK, false);

        int barX = panelX + 14, barY = panelY + 38, barW = panelW - 28;
        g.fill(barX - 1, barY - 1, barX + barW + 1, barY + 4, CardRenderer.KRAFT_DARK);
        g.fill(barX, barY, barX + barW, barY + 3, 0xFF8A755A);
        int fillW = (int) (barW * (have / (float) total));
        if (fillW > 0) {
            g.fill(barX, barY, barX + fillW, barY + 3, 0xFF55A82F);
        }

        // the cards of this page
        int start = page * perPage;
        for (int i = start; i < Math.min(start + perPage, total); i++) {
            MobCard card = MobCards.ALL.get(i);
            int slot = i - start;
            int cx = gridX + (slot % cols) * cellW + 8;
            int cy = gridY + (slot / cols) * cellH + 8;
            int cw = Math.round(CardRenderer.CARD_W * CARD_SCALE);
            int ch = Math.round(CardRenderer.CARD_H * CARD_SCALE);

            g.fill(cx + 2, cy + 3, cx + cw + 4, cy + ch + 5, 0x44000000); // shadow

            boolean hovered = mouseX >= cx && mouseX < cx + cw && mouseY >= cy && mouseY < cy + ch;
            if (ClientCollection.has(card.id())) {
                LivingEntity mob = CardRenderer.portraitEntity(minecraft, card, entityCache);
                boolean foil = ClientCollection.hasFoil(card.id());
                CardRenderer.renderCard(g, font, card, cx, cy, CARD_SCALE, mouseX, mouseY, mob, foil);
                if (hovered) {
                    g.renderOutline(cx - 2, cy - 2, cw + 4, ch + 4, 0xFFF9D849);
                }
            } else {
                CardRenderer.renderBack(g, font, cx, cy, CARD_SCALE);
                if (hovered) {
                    g.renderOutline(cx - 2, cy - 2, cw + 4, ch + 4, 0x66FFFFFF);
                }
            }
        }

        // footer: page arrows and indicator
        drawArrow(g, prevX, prevY, "<", page > 0, mouseX, mouseY);
        drawArrow(g, nextX, nextY, ">", page < pageCount - 1, mouseX, mouseY);
        String pageText = "Page " + (page + 1) + " / " + pageCount;
        g.drawString(font, pageText, panelX + (panelW - font.width(pageText)) / 2,
                panelY + panelH - 17, CardRenderer.KRAFT_DARK, false);

        String hint = "Click a card to enlarge · scroll or use arrow keys to flip pages";
        g.drawString(font, hint, (width - font.width(hint)) / 2,
                panelY + panelH + 8, 0xFFAAAAAA, true);
    }

    private void drawArrow(GuiGraphics g, int x, int y, String glyph, boolean enabled,
                           int mouseX, int mouseY) {
        boolean hovered = enabled && mouseX >= x && mouseX < x + ARROW_W
                && mouseY >= y && mouseY < y + ARROW_H;
        g.fill(x, y, x + ARROW_W, y + ARROW_H, enabled
                ? (hovered ? 0xFFB99465 : CardRenderer.KRAFT) : 0xFFCEC3AF);
        g.renderOutline(x, y, ARROW_W, ARROW_H, CardRenderer.KRAFT_DARK);
        int color = enabled ? CardRenderer.INK : 0xFF9A9083;
        g.drawString(font, glyph, x + (ARROW_W - font.width(glyph)) / 2, y + 3, color, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (inRect(mouseX, mouseY, prevX, prevY) && page > 0) {
                flip(-1);
                return true;
            }
            if (inRect(mouseX, mouseY, nextX, nextY) && page < pageCount - 1) {
                flip(1);
                return true;
            }
            int start = page * perPage;
            for (int i = start; i < Math.min(start + perPage, MobCards.ALL.size()); i++) {
                MobCard card = MobCards.ALL.get(i);
                int slot = i - start;
                int cx = gridX + (slot % cols) * cellW + 8;
                int cy = gridY + (slot / cols) * cellH + 8;
                int cw = Math.round(CardRenderer.CARD_W * CARD_SCALE);
                int ch = Math.round(CardRenderer.CARD_H * CARD_SCALE);
                if (mouseX >= cx && mouseX < cx + cw && mouseY >= cy && mouseY < cy + ch
                        && ClientCollection.has(card.id()) && minecraft != null) {
                    minecraft.setScreen(new MobCardScreen(card, this, ClientCollection.hasFoil(card.id())));
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean inRect(double mx, double my, int x, int y) {
        return mx >= x && mx < x + ARROW_W && my >= y && my < y + ARROW_H;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY < 0 && page < pageCount - 1) {
            flip(1);
            return true;
        }
        if (scrollY > 0 && page > 0) {
            flip(-1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 263 && page > 0) { // left arrow
            flip(-1);
            return true;
        }
        if (keyCode == 262 && page < pageCount - 1) { // right arrow
            flip(1);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void flip(int direction) {
        page += direction;
        if (minecraft != null) {
            minecraft.getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0F));
        }
    }
}
