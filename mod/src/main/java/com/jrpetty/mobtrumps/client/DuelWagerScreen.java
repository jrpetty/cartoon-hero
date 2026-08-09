package com.jrpetty.mobtrumps.client;

import com.jrpetty.mobtrumps.DuelWagerActionPayload;
import com.jrpetty.mobtrumps.DuelWagerPayload;
import com.jrpetty.mobtrumps.game.WagerTable;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static com.jrpetty.mobtrumps.client.TableArt.BAD;
import static com.jrpetty.mobtrumps.client.TableArt.BRASS;
import static com.jrpetty.mobtrumps.client.TableArt.BRASS_DARK;
import static com.jrpetty.mobtrumps.client.TableArt.BRASS_DIM;
import static com.jrpetty.mobtrumps.client.TableArt.DIM;
import static com.jrpetty.mobtrumps.client.TableArt.FAINT;
import static com.jrpetty.mobtrumps.client.TableArt.GOOD;
import static com.jrpetty.mobtrumps.client.TableArt.INK;

/**
 * Two players agreeing what a duel is worth, before a single emerald moves.
 *
 * <p>The price sits in the middle where both of them can reach it. Either can
 * type a new one; both have to say yes to the one showing; and RESET WAGER
 * takes the agreements back off so it can be argued over again. Nothing is
 * staked until both lights are green, so walking away is free and the screen
 * says so.
 */
public class DuelWagerScreen extends Screen {

    /**
     * Above this height everything fits; below it the trimmings come off.
     *
     * <p>Derived, not chosen: the tallest roomy column (best-of series plus a
     * card wager) bottoms out at y=186, and the clock sits at height-48, so
     * roomy needs 234. The first value here was 214, picked by eye — the sweep
     * in tools/checkwagerlayout.py only caught it once it swept below the
     * vanilla 240 floor, which is also why those twenty-six heights are swept
     * even though vanilla cannot reach them: modded windows can.
     */
    private static final int ROOMY_H = 234;

    /** The staked cards get the gutters; below this width there are none. */
    private static final int CARDS_MIN_W = 470;

    private EditBox price;
    private final List<int[]> hits = new ArrayList<>();   // x, y, w, h, id
    private final long openedAt = System.currentTimeMillis();
    private final java.util.Map<String, net.minecraft.world.entity.LivingEntity> entityCache =
            new java.util.HashMap<>();

    // hit ids
    private static final int ID_SET = 0;
    private static final int ID_AGREE = 1;
    private static final int ID_RESET = 2;
    private static final int ID_LEAVE = 3;
    private static final int ID_QUICK = 100;   // + the amount's index

    private static final int[] QUICK = {0, 10, 50, 250, 1000};

    public DuelWagerScreen() {
        super(Component.literal("The Stakes"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        super.init();
        int bw = Math.min(96, width / 3);
        price = new EditBox(font, (width - bw) / 2, height / 2, bw, 16,
                Component.literal("price"));
        price.setMaxLength(7);
        price.setFilter(t -> t.chars().allMatch(Character::isDigit));
        price.setValue(String.valueOf(ClientDuelWager.price()));
        addRenderableWidget(price);
    }

    /** What is typed right now, clamped to what the table will hold. */
    private int typed() {
        try {
            return WagerTable.clamp(Integer.parseInt(price.getValue().trim()));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (!ClientDuelWager.open()) {
            onClose();
            return;
        }
        super.render(g, mouseX, mouseY, partialTick);
        hits.clear();
        long t = System.currentTimeMillis() - openedAt;
        boolean roomy = height >= ROOMY_H;

        TableArt.felt(g, width, height, width / 2, height / 2);
        TableArt.rail(g, width, height);
        drawStakedCards(g, mouseX, mouseY);

        int pw = Math.min(320, width - 2 * TableArt.RAIL - 8);
        int px = (width - pw) / 2;
        int shown = ClientDuelWager.price();
        boolean youAgreed = ClientDuelWager.youAgreed();
        boolean theyAgreed = ClientDuelWager.theyAgreed();
        boolean youCan = ClientDuelWager.youCanAfford();
        boolean theyCan = ClientDuelWager.theyCanAfford();

        // --- heading ---
        int y = TableArt.RAIL + (roomy ? 8 : 3);
        var pose = g.pose();
        pose.pushPose();
        pose.translate(width / 2f, y, 0);
        pose.scale(1.5f, 1.5f, 1f);
        String head = "THE STAKES";
        g.drawString(font, head, -font.width(head) / 2, 0, BRASS, true);
        pose.popPose();
        y += roomy ? 16 : 14;
        String versus = "YOU  vs  " + ClientDuelWager.opponent().toUpperCase(Locale.ROOT);
        g.drawCenteredString(font, fit(versus, pw - 8), width / 2, y, DIM);
        y += 11;
        if (ClientDuelWager.bestOf() > 1) {
            g.drawCenteredString(font, "best of " + ClientDuelWager.bestOf(), width / 2, y, FAINT);
            y += 10;
        }
        g.fill(px + 20, y, px + pw - 20, y + 1, BRASS_DARK);
        y += roomy ? 8 : 5;

        // --- the price, the thing they are actually arguing about ---
        int plateH = roomy ? 34 : 26;
        int plateW = Math.min(pw - 40, 220);
        TableArt.plate(g, (width - plateW) / 2, y, plateW, plateH, BRASS_DIM);
        String big = shown <= 0 ? "FREE" : String.valueOf(shown);
        float scale = shown <= 0 ? 1.8f : (big.length() > 5 ? 1.6f : 2.2f);
        pose.pushPose();
        pose.translate(width / 2f, y + (roomy ? 6 : 4), 0);
        pose.scale(scale, scale, 1f);
        g.drawString(font, big, -font.width(big) / 2, 0, shown <= 0 ? DIM : BRASS, true);
        pose.popPose();
        String under = shown <= 0 ? "a friendly game"
                : "emeralds each  ·  pot " + (shown * 2L);
        g.drawCenteredString(font, under, width / 2, y + plateH - (roomy ? 11 : 9),
                shown <= 0 ? FAINT : DIM);
        y += plateH + (roomy ? 7 : 4);

        if (ClientDuelWager.cardWager()) {
            g.drawCenteredString(font, "+ the mob card in your hand, each", width / 2, y, BRASS_DIM);
            y += 11;
        }

        // --- naming a different one ---
        int bw = Math.min(96, width / 3);
        int setW = font.width("SET") + 14;
        int rowW = bw + 4 + setW;
        int rowX = (width - rowW) / 2;
        price.setX(rowX);
        price.setY(y);
        price.setWidth(bw);
        price.render(g, mouseX, mouseY, partialTick);
        boolean canSet = typed() != shown;
        button(g, ID_SET, rowX + bw + 4, y, setW, 16, "SET",
                canSet ? 0xFF2A5F8A : 0xFF243029, mouseX, mouseY, canSet);
        y += 20;

        if (roomy) {
            int total = 0;
            for (int q : QUICK) {
                total += font.width(quickLabel(q)) + 12 + 3;
            }
            int qx = (width - total) / 2;
            for (int i = 0; i < QUICK.length; i++) {
                String label = quickLabel(QUICK[i]);
                int qw = font.width(label) + 12;
                button(g, ID_QUICK + i, qx, y, qw, 13, label,
                        QUICK[i] == shown ? 0xFF2C6E49 : 0xFF243029, mouseX, mouseY, true);
                qx += qw + 3;
            }
            y += 17;
        }

        // --- who has said yes ---
        int lampW = Math.min((pw - 24) / 2, 130);
        int lx = width / 2 - lampW - 3;
        lamp(g, lx, y, lampW, "YOU", youAgreed, t);
        lamp(g, width / 2 + 3, y, lampW, ClientDuelWager.opponent(), theyAgreed, t);
        y += 18;

        g.drawCenteredString(font, "you hold " + ClientDuelWager.purse() + " emeralds",
                width / 2, y, youCan ? DIM : BAD);
        y += 10;
        if (!theyCan && shown > 0) {
            g.drawCenteredString(font, fit(ClientDuelWager.opponent() + " can't cover this", pw - 8),
                    width / 2, y, BAD);
        } else if (ClientDuelWager.lastMover() == DuelWagerPayload.MOVER_THEM) {
            g.drawCenteredString(font, fit(ClientDuelWager.opponent() + " named this price", pw - 8),
                    width / 2, y, FAINT);
        }

        // --- the three answers ---
        // The clock sits ABOVE the buttons rather than under them: below the
        // row there are only 7px of rail, and a line of text is 9.
        int by = height - TableArt.RAIL - 30;
        int gap = 4;
        int leaveW = Math.min(58, pw / 4);
        int resetW = Math.min(96, pw / 3);
        int agreeW = Math.min(pw - leaveW - resetW - gap * 2, 130);
        int ax = (width - (agreeW + resetW + leaveW + gap * 2)) / 2;

        boolean canAgree = !youAgreed && youCan;
        String agreeLabel = youAgreed ? "WAITING…" : (youCan ? "AGREE" : "CAN'T AFFORD");
        if (theyAgreed && canAgree) {
            // they have said yes and the whole thing is waiting on this button:
            // give it the pulse their lamp already has
            int a = 0x2A + (int) (0x2A * (0.5 + 0.5 * Math.sin(t / 320.0)));
            g.fill(ax - 3, by - 3, ax + agreeW + 3, by + 22, TableArt.alpha(GOOD, a));
        }
        button(g, ID_AGREE, ax, by, agreeW, 19, fit(agreeLabel, agreeW - 8),
                youAgreed ? 0xFF243029 : (youCan ? 0xFF2C6E49 : 0xFF5A2530),
                mouseX, mouseY, canAgree);
        button(g, ID_RESET, ax + agreeW + gap, by, resetW, 19, fit("RESET WAGER", resetW - 8),
                0xFF8A6A1A, mouseX, mouseY, true);
        button(g, ID_LEAVE, ax + agreeW + resetW + gap * 2, by, leaveW, 19,
                fit("Leave", leaveW - 8), 0xFF5A2530, mouseX, mouseY, true);

        int left = ClientDuelWager.seconds();
        g.drawCenteredString(font, left + "s", width / 2, by - 11,
                left <= 15 ? BAD : FAINT);
    }

    /**
     * The actual cards on the line, one in each gutter beside the panel — the
     * same renders as every other table in the mod, live mob and all.
     *
     * <p>Drawn at real screen coordinates because that is where renderCard puts
     * the mob: it places the portrait in screen space from its x,y arguments,
     * and wrapping it in a pose translation is exactly what once left
     * Twenty-One dealing mobless cards.
     *
     * <p>In an emerald game the gutters get card backs instead: the deck is
     * dealt fresh at the duel, so there is no honest face to show.
     */
    private void drawStakedCards(GuiGraphics g, int mouseX, int mouseY) {
        if (width < CARDS_MIN_W) {
            return;
        }
        int panelW = Math.min(320, width - 2 * TableArt.RAIL - 8);
        int gutter = (width - panelW) / 2 - TableArt.RAIL;   // space each side
        float cardScale = Math.min(0.42f, Math.min(
                (gutter - 16) / (float) CardRenderer.CARD_W,
                (height - 2 * TableArt.RAIL - 34) / (float) CardRenderer.CARD_H));
        if (cardScale < 0.18f) {
            return;   // a card too small to read is noise, not information
        }
        int cw = Math.round(CardRenderer.CARD_W * cardScale);
        int ch = Math.round(CardRenderer.CARD_H * cardScale);
        int cy = (height - ch) / 2 + 4;
        int lx = TableArt.RAIL + (gutter - cw) / 2;
        int rx = width - TableArt.RAIL - (gutter - cw) / 2 - cw;

        drawStake(g, "YOUR STAKE", ClientDuelWager.yourCard(), lx, cy, cw, ch,
                cardScale, mouseX, mouseY);
        drawStake(g, "THEIR STAKE", ClientDuelWager.theirCard(), rx, cy, cw, ch,
                cardScale, mouseX, mouseY);
    }

    private void drawStake(GuiGraphics g, String label, String cardId,
                           int x, int y, int cw, int ch, float scale, int mouseX, int mouseY) {
        g.drawCenteredString(font, fit(label, cw + 12), x + cw / 2, y - 11,
                TableArt.BRASS_DIM);
        com.jrpetty.mobtrumps.game.MobCard card =
                com.jrpetty.mobtrumps.game.MobCards.byId(cardId);
        if (card != null && minecraft != null) {
            var mob = CardRenderer.portraitEntity(minecraft, card, entityCache);
            CardRenderer.renderCard(g, font, card, x, y, scale, mouseX, mouseY, mob, false, true);
        } else {
            CardRenderer.renderBack(g, font, x, y, scale);
            if (ClientDuelWager.cardWager()) {
                // a card wager with no card up: say so where the card would be
                g.drawCenteredString(font, "hold a card", x + cw / 2, y + ch + 3, BAD);
            }
        }
    }

    private String quickLabel(int amount) {
        if (amount == 0) return "Free";
        if (amount >= 1000) return (amount / 1000) + "k";
        return String.valueOf(amount);
    }

    /** One player's agreement light. */
    private void lamp(GuiGraphics g, int x, int y, int w, String who, boolean lit, long t) {
        TableArt.plate(g, x, y, w, 15, lit ? GOOD : BRASS_DARK);
        int cx = x + 5;
        if (lit) {
            // a slow pulse, so a table waiting on one player reads at a glance
            int a = 0x40 + (int) (0x30 * (0.5 + 0.5 * Math.sin(t / 380.0)));
            g.fill(x + 1, y + 1, x + w - 1, y + 14, TableArt.alpha(GOOD, a / 3));
            TableArt.mark(g, cx, y + 4, true, GOOD);
        } else {
            g.fill(cx + 1, y + 6, cx + 4, y + 9, FAINT);
        }
        g.drawString(font, fit(who.toUpperCase(Locale.ROOT) + (lit ? " AGREED" : " …"), w - 14),
                cx + 8, y + 4, lit ? INK : DIM, false);
    }

    private void button(GuiGraphics g, int id, int x, int y, int w, int h, String label,
                        int face, int mouseX, int mouseY, boolean enabled) {
        boolean hot = enabled && mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        TableArt.button(g, x, y, w, h, face, hot, enabled);
        g.drawString(font, label, x + (w - font.width(label)) / 2, y + (h - 8) / 2,
                enabled ? INK : FAINT, false);
        if (enabled) {
            hits.add(new int[]{x, y, w, h, id});
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (int[] r : hits) {
                if (mouseX >= r[0] && mouseX < r[0] + r[2] && mouseY >= r[1] && mouseY < r[1] + r[3]) {
                    press(r[4]);
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void press(int id) {
        click();
        if (id >= ID_QUICK) {
            int amount = QUICK[Mth.clamp(id - ID_QUICK, 0, QUICK.length - 1)];
            price.setValue(String.valueOf(amount));
            send(DuelWagerActionPayload.PROPOSE, amount);
            return;
        }
        switch (id) {
            case ID_SET -> send(DuelWagerActionPayload.PROPOSE, typed());
            case ID_AGREE -> send(DuelWagerActionPayload.AGREE, 0);
            case ID_RESET -> {
                // put the box back to what is on the table, so "reset" also
                // undoes a half-typed number nobody has proposed yet
                price.setValue(String.valueOf(ClientDuelWager.price()));
                send(DuelWagerActionPayload.REOPEN, 0);
            }
            case ID_LEAVE -> send(DuelWagerActionPayload.LEAVE, 0);
            default -> {
            }
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if ((keyCode == 257 || keyCode == 335) && price.isFocused()) {   // enter in the box
            click();
            send(DuelWagerActionPayload.PROPOSE, typed());
            return true;
        }
        if (keyCode == 256) {   // escape leaves — nothing is staked, so it is safe
            click();
            send(DuelWagerActionPayload.LEAVE, 0);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void send(int action, int value) {
        PacketDistributor.sendToServer(new DuelWagerActionPayload(action, value));
    }

    private void click() {
        if (minecraft != null) {
            minecraft.getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f));
        }
    }

    private String fit(String text, int maxWidth) {
        if (maxWidth <= 0) {
            return "";
        }
        if (font.width(text) <= maxWidth) {
            return text;
        }
        String cut = font.plainSubstrByWidth(text, Math.max(1, maxWidth - font.width("…")));
        return cut.isEmpty() ? "" : cut + "…";
    }
}
