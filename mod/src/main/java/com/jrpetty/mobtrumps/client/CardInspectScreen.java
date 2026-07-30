package com.jrpetty.mobtrumps.client;

import com.jrpetty.mobtrumps.CardIdentity;
import com.jrpetty.mobtrumps.CardIdentityService;
import com.jrpetty.mobtrumps.CardWear;
import com.jrpetty.mobtrumps.MobCardItem;
import com.jrpetty.mobtrumps.game.CardCondition;
import com.jrpetty.mobtrumps.game.CardEdition;
import com.jrpetty.mobtrumps.game.MobCard;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The big look at one physical card. Opens on its front — art, stats, serial
 * and whatever wear it has picked up — and flips to a back that carries its
 * history: serial, exact condition, who unlocked it and when, its edition and
 * whether it is protected.
 *
 * <p>Everything is read straight off the held stack's synced components, so
 * opening this screen sends nothing and, crucially, never counts as handling
 * the card: the card is already in your hand before you press the button.
 */
public class CardInspectScreen extends Screen {

    private static final long FLIP_MS = 380L;

    private final InteractionHand hand;
    private final Map<String, LivingEntity> entityCache = new HashMap<>();
    private boolean showingBack;
    private long flipStartedAt = -1;
    private int[] cardRect = {0, 0, 0, 0};
    private int[] flipRect = {0, 0, 0, 0};

    public CardInspectScreen(InteractionHand hand) {
        super(Component.literal("Card"));
        this.hand = hand;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private ItemStack stack() {
        return minecraft == null || minecraft.player == null
                ? ItemStack.EMPTY : minecraft.player.getItemInHand(hand);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        ItemStack stack = stack();
        MobCard card = MobCardItem.cardOf(stack);
        if (card == null) {
            onClose(); // they swapped away from the card
            return;
        }
        g.fillGradient(0, 0, width, height, 0xF0161320, 0xF00B0910);

        CardIdentity identity = CardIdentityService.identityOf(stack);
        CardWear wear = CardIdentityService.wearOf(stack);
        boolean foil = MobCardItem.isFoilCard(stack);
        int level = foil ? Math.max(1, ClientCollection.upgradeLevel(card.id())) : 0;

        // fit one big card between a title and a hint line
        float scale = Math.min((height - 96) / (float) CardRenderer.CARD_H,
                (width - 80) / (float) CardRenderer.CARD_W);
        scale = Mth.clamp(scale, 0.5f, 1.9f);
        int cw = Math.round(CardRenderer.CARD_W * scale);
        int ch = Math.round(CardRenderer.CARD_H * scale);
        int cx = (width - cw) / 2;
        int cy = (height - ch) / 2 - 4;
        cardRect = new int[]{cx, cy, cw, ch};

        // the flip itself: squash through the edge, swapping face at halfway
        float t = flipStartedAt < 0 ? 1f
                : Mth.clamp((System.currentTimeMillis() - flipStartedAt) / (float) FLIP_MS, 0f, 1f);
        boolean mid = t < 1f;
        float squash = mid ? Math.abs((float) Math.cos(t * Math.PI)) : 1f;
        if (mid && t >= 0.5f && flipStartedAt > 0) {
            showingBack = pendingBack;
        }

        var pose = g.pose();
        pose.pushPose();
        pose.translate(cx + cw / 2f, 0, 0);
        pose.scale(Math.max(0.02f, squash), 1f, 1f);
        pose.translate(-(cx + cw / 2f), 0, 0);
        if (showingBack) {
            drawBack(g, card, identity, wear, cx, cy, cw, ch, scale);
        } else {
            LivingEntity mob = CardRenderer.portraitEntity(minecraft, card, entityCache);
            CardRenderer.renderCard(g, font, card, level, cx, cy, scale,
                    mouseX, mouseY, mob, foil, true,
                    identity == null ? CardEdition.STANDARD : identity.editionType());
            drawFrontOverlay(g, stack, identity, wear, cx, cy, cw, ch);
        }
        // wear is drawn over whichever face is showing, so a battered card
        // looks battered from both sides
        drawWear(g, wear.condition(), cx, cy, cw, ch);
        if (wear.sleeved()) {
            drawSleeve(g, cx, cy, cw, ch);
        }
        pose.popPose();
        if (t >= 1f) {
            flipStartedAt = -1;
        }

        String title = showingBack ? "THE BACK" : card.displayName().toUpperCase(Locale.ROOT);
        g.drawCenteredString(font, title, width / 2, 18, 0xFFE8DFC8);

        String label = showingBack ? "Flip to the front" : "Flip the card";
        int bw = font.width(label) + 20;
        int bx = width / 2 - bw / 2;
        int by = height - 30;
        flipRect = new int[]{bx, by, bw, 18};
        boolean hover = inRect(mouseX, mouseY, flipRect);
        g.fill(bx, by, bx + bw, by + 18, hover ? 0xFF4A3E6E : 0xFF2E2545);
        g.renderOutline(bx, by, bw, 18, hover ? 0xFFE3C071 : 0xFF564A7A);
        g.drawString(font, label, bx + 10, by + 5, 0xFFF0E8D0, false);
        g.drawCenteredString(font, "click the card or press F  ·  ESC to close",
                width / 2, height - 11, 0xFF7E7590);
    }

    private boolean pendingBack;

    /** Serial and edition badge, riding the front of the card. */
    private void drawFrontOverlay(GuiGraphics g, ItemStack stack, CardIdentity identity,
                                  CardWear wear, int cx, int cy, int cw, int ch) {
        if (identity == null || !identity.valid()) {
            String note = "Unregistered card";
            g.fill(cx + 6, cy + ch - 22, cx + cw - 6, cy + ch - 8, 0xC0402018);
            g.drawCenteredString(font, note, cx + cw / 2, cy + ch - 19, 0xFFE0A090);
            return;
        }
        // The serial sits on a plate just BELOW the card. It used to be centred
        // over the fact strip, which printed the number straight through
        // "Farm · 4 / 81" — there is no clear space inside the frame for it.
        String serial = CardIdentityService.serialText(stack);
        int w = font.width(serial) + 14;
        int plateY = Math.min(cy + ch + 5, height - 46);
        g.fill(cx + cw / 2 - w / 2, plateY, cx + cw / 2 + w / 2, plateY + 12, 0xC0140F0A);
        g.renderOutline(cx + cw / 2 - w / 2, plateY, w, 12, 0x60E3C071);
        g.drawCenteredString(font, serial, cx + cw / 2, plateY + 2, 0xFFE3C071);

        CardEdition edition = identity.editionType();
        if (edition != CardEdition.STANDARD && !edition.symbol().isEmpty()) {
            g.drawString(font, edition.symbol(), cx + cw - 18, cy + 10, edition.color(), false);
        }
    }

    /** The back: everything the card remembers about itself. */
    private void drawBack(GuiGraphics g, MobCard card, CardIdentity identity, CardWear wear,
                          int cx, int cy, int cw, int ch, float scale) {
        CardRenderer.renderBack(g, font, cx, cy, scale);
        // a parchment panel over the damask so the record is readable
        int px = cx + Math.round(18 * scale);
        int py = cy + Math.round(30 * scale);
        int pw = cw - Math.round(36 * scale);
        int phg = ch - Math.round(74 * scale);
        g.fill(px - 2, py - 2, px + pw + 2, py + phg + 2, 0xFF241C10);
        g.fill(px, py, px + pw, py + phg, 0xFFF7F1E3);

        int y = py + 8;
        int lx = px + 8;
        if (identity == null || !identity.valid()) {
            g.drawString(font, "UNREGISTERED", lx, y, 0xFFA33A25, false);
            g.drawString(font, "This card predates the serial", lx, y + 14, 0xFF6E6154, false);
            g.drawString(font, "system. Hold it once and the", lx, y + 24, 0xFF6E6154, false);
            g.drawString(font, "server will number it.", lx, y + 34, 0xFF6E6154, false);
            return;
        }
        row(g, lx, y, "SERIAL", identity.formatSerial(card.id(), CardIdentityService.serialDigits()),
                0xFF8A6D1F);
        y += 20;
        row(g, lx, y, "CONDITION", wear.condition() + "%  " + wear.label(),
                CardCondition.color(wear.condition()));
        y += 20;
        row(g, lx, y, "UNLOCKED BY",
                identity.unlockerName().isEmpty() ? "unknown" : identity.unlockerName(), 0xFF35281A);
        y += 20;
        String date = identity.formatDate();
        row(g, lx, y, "UNLOCKED", date.isEmpty() ? "before records" : date, 0xFF35281A);
        y += 20;
        row(g, lx, y, "EDITION", identity.editionType().label(), identity.editionType().color());
        y += 20;
        row(g, lx, y, "PROTECTION", wear.sleeved() ? "Card Sleeve" : "Unsleeved",
                wear.sleeved() ? 0xFF1C7FA8 : 0xFF8B8074);
        if (identity.migrated()) {
            g.drawString(font, "Numbered retrospectively — its true", lx, y + 22, 0xFF9A9083, false);
            g.drawString(font, "place in this mob's history is unknown.", lx, y + 32, 0xFF9A9083, false);
        }
    }

    private void row(GuiGraphics g, int x, int y, String label, String value, int color) {
        g.drawString(font, label, x, y, 0xFF9A9083, false);
        g.drawString(font, value, x, y + 9, color, false);
    }

    /**
     * Visible damage. Each stage adds to the last: corner scuffs first, then
     * creases and fading, then heavy wear and a torn edge at the very bottom.
     */
    private void drawWear(GuiGraphics g, int condition, int x, int y, int w, int h) {
        int stage = CardCondition.stage(condition);
        if (stage <= 0) {
            return;
        }
        // corners scuff first
        int corner = 2 + stage;
        for (int[] c : new int[][]{{x, y}, {x + w - corner, y}, {x, y + h - corner},
                {x + w - corner, y + h - corner}}) {
            g.fill(c[0], c[1], c[0] + corner, c[1] + corner, 0x6030240F);
        }
        if (stage >= 2) {
            // creases: a couple of pale diagonals across the face
            for (int i = 1; i <= stage - 1; i++) {
                int cy2 = y + h * i / stage;
                g.fill(x + 4, cy2, x + w - 4, cy2 + 1, 0x33FFFFFF);
                g.fill(x + 4, cy2 + 1, x + w - 4, cy2 + 2, 0x2A000000);
            }
        }
        if (stage >= 3) {
            g.fill(x, y, x + w, y + h, (Math.min(0x60, stage * 0x16) << 24) | 0x00C8B78A); // fading
        }
        if (stage >= 4) {
            // nicks bitten out of the edges
            for (int i = 0; i < 6 + stage; i++) {
                int nx = x + 6 + (i * 37) % Math.max(1, w - 12);
                int ny = (i % 2 == 0) ? y : y + h - 3;
                g.fill(nx, ny, nx + 3 + stage, ny + 3, 0x70201408);
            }
        }
        if (stage >= 5) {
            g.fill(x, y, x + w, y + h, 0x40000000);
            g.fill(x + w / 2 - 1, y + 6, x + w / 2 + 1, y + h - 6, 0x50201408); // a split
        }
    }

    /** The tell-tale gloss of a protected card. */
    private void drawSleeve(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x - 3, y - 3, x + w + 3, y + h + 3, 0x22BFE8FF);
        g.renderOutline(x - 3, y - 3, w + 6, h + 6, 0x88CFE9F6);
        g.renderOutline(x - 2, y - 2, w + 4, h + 4, 0x44FFFFFF);
        // a soft diagonal highlight, like light off plastic
        for (int i = 0; i < 10; i++) {
            int sx = x + w / 5 + i;
            g.fill(sx, y, sx + 1, y + h, 0x0EFFFFFF);
        }
    }

    private void flip() {
        if (flipStartedAt > 0) {
            return;
        }
        pendingBack = !showingBack;
        flipStartedAt = System.currentTimeMillis();
        if (minecraft != null) {
            minecraft.getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.2f));
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && (inRect((int) mouseX, (int) mouseY, flipRect)
                || inRect((int) mouseX, (int) mouseY, cardRect))) {
            flip();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 70) { // F
            flip();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private static boolean inRect(int mx, int my, int[] r) {
        return r != null && mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3];
    }
}
