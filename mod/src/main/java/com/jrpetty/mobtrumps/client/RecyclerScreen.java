package com.jrpetty.mobtrumps.client;

import com.jrpetty.mobtrumps.CardIdentityService;
import com.jrpetty.mobtrumps.MobCardItem;
import com.jrpetty.mobtrumps.RecyclerActionPayload;
import com.jrpetty.mobtrumps.RecyclerManager;
import com.jrpetty.mobtrumps.game.CardCondition;
import com.jrpetty.mobtrumps.game.MobCard;
import com.jrpetty.mobtrumps.game.Recycler;
import com.jrpetty.mobtrumps.game.Tier;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * Both recycler machines behind one screen.
 *
 * <p>The <b>Shredder</b> lists the spares you are actually carrying — a card
 * counts as a spare when its mob is already filed in your book — with what each
 * is worth, which falls with its condition. The <b>Press</b> asks for a tier
 * and a stake and shows you the odds it buys, which are exactly linear in the
 * stake. Neither hides its arithmetic: there is nothing to look up here, only a
 * choice about how much variance you want.
 */
public class RecyclerScreen extends Screen {

    private static final int INK = 0xFFF2ECDD;
    private static final int DIM = 0xFF9A93A8;
    private static final int GOLD = 0xFFE3C071;
    private static final int PLATE = 0xFF241F33;

    private record Spare(MobCard card, boolean foil, int condition, int value) {
    }

    private final int mode;
    private int panelX, panelY, panelW, panelH;
    private int scroll;
    private Tier tier = Tier.COMMON;
    private int stake = Recycler.MIN_STAKE;
    private final List<Spare> spares = new ArrayList<>();
    private final List<int[]> rowRects = new ArrayList<>();
    private int[] actionRect = {0, 0, 0, 0};
    private int[] allRect = {0, 0, 0, 0};
    private final int[][] tierRects = new int[Tier.values().length][];
    private int[] lessRect = {0, 0, 0, 0};
    private int[] moreRect = {0, 0, 0, 0};
    private int[] maxRect = {0, 0, 0, 0};

    public RecyclerScreen(int mode) {
        super(Component.literal(mode == RecyclerManager.MODE_PRESS ? "Printing Press" : "Card Shredder"));
        this.mode = mode;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        super.init();
        panelW = Math.min(320, width - 24);
        panelH = Math.min(210, height - 60);
        panelX = (width - panelW) / 2;
        panelY = Math.max(28, (height - panelH) / 2);
        rebuild();
    }

    /** Read the spares straight off the player's own inventory. */
    private void rebuild() {
        spares.clear();
        if (minecraft == null || minecraft.player == null) {
            return;
        }
        var inv = minecraft.player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            MobCard card = MobCardItem.cardOf(s);
            if (card == null) continue;
            boolean foil = MobCardItem.isFoilCard(s);
            // a spare is a card whose mob is already filed in the book
            if (!ClientCollection.isStored(card.id(), foil)) continue;
            int condition = CardIdentityService.wearOf(s).condition();
            spares.add(new Spare(card, foil, condition, Recycler.yield(card.tier(), condition)));
        }
        spares.sort((a, b) -> b.value() - a.value());
        scroll = Mth.clamp(scroll, 0, Math.max(0, spares.size() - rows()));
    }

    private int rows() {
        return Math.max(1, (panelH - 78) / 14);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.fillGradient(0, 0, width, height, 0xF0161320, 0xF00A0810);

        var pose = g.pose();
        pose.pushPose();
        pose.translate(width / 2f, panelY - 24f, 0);
        pose.scale(1.7f, 1.7f, 1f);
        String title = mode == RecyclerManager.MODE_PRESS ? "PRINTING PRESS" : "CARD SHREDDER";
        g.drawString(font, title, -font.width(title) / 2, 0, GOLD, true);
        pose.popPose();

        g.fill(panelX - 2, panelY - 2, panelX + panelW + 2, panelY + panelH + 2, 0xFF3A3350);
        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, PLATE);

        String frag = ClientRecycler.fragments() + " fragments";
        g.drawString(font, frag, panelX + panelW - 8 - font.width(frag), panelY + 8, GOLD, false);

        if (mode == RecyclerManager.MODE_PRESS) {
            renderPress(g, mouseX, mouseY);
        } else {
            renderShredder(g, mouseX, mouseY);
        }
        g.drawCenteredString(font, "ESC to close", width / 2, panelY + panelH + 8, 0xFF6C6480);
    }

    // --- shredder -----------------------------------------------------------

    private void renderShredder(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, "YOUR SPARES", panelX + 8, panelY + 8, INK, false);
        g.drawString(font, "A card is spare once its mob is filed in your book.",
                panelX + 8, panelY + 20, DIM, false);
        g.fill(panelX + 8, panelY + 32, panelX + panelW - 8, panelY + 33, 0x30FFFFFF);

        rowRects.clear();
        int y = panelY + 38;
        int shown = Math.min(rows(), spares.size() - scroll);
        if (spares.isEmpty()) {
            g.drawString(font, "Nothing spare — every card you carry is one you still need.",
                    panelX + 8, y + 6, 0xFF7E7590, false);
        }
        int total = 0;
        for (Spare s : spares) total += s.value();

        for (int r = 0; r < shown; r++) {
            Spare s = spares.get(scroll + r);
            boolean hover = mouseX >= panelX + 8 && mouseX < panelX + panelW - 8
                    && mouseY >= y && mouseY < y + 13;
            g.fill(panelX + 8, y, panelX + panelW - 8, y + 13, hover ? 0xFF3A3150 : 0xFF1C1830);
            String name = (s.foil() ? "✦ " : "") + s.card().displayName();
            g.drawString(font, name, panelX + 12, y + 3, s.foil() ? 0xFFC77BFF : INK, false);
            String cond = s.condition() + "% " + CardCondition.label(s.condition());
            g.drawString(font, cond, panelX + panelW / 2, y + 3,
                    CardCondition.color(s.condition()) | 0xFF000000, false);
            String val = "+" + s.value();
            g.drawString(font, val, panelX + panelW - 14 - font.width(val), y + 3, GOLD, false);
            rowRects.add(new int[]{panelX + 8, y, panelW - 16, 13});
            y += 14;
        }

        int by = panelY + panelH - 26;
        String label = spares.isEmpty() ? "Nothing to shred"
                : "Shred all " + spares.size() + "  ·  +" + total;
        int bw = Math.max(150, font.width(label) + 24);
        int bx = panelX + (panelW - bw) / 2;
        allRect = new int[]{bx, by, bw, 18};
        boolean on = !spares.isEmpty();
        boolean hover = on && inRect(mouseX, mouseY, allRect);
        g.fill(bx, by, bx + bw, by + 18, !on ? 0xFF2A2440 : hover ? 0xFF8A4030 : 0xFF6E3325);
        g.renderOutline(bx, by, bw, 18, on ? (hover ? GOLD : 0x66FFFFFF) : 0xFF3A3350);
        g.drawString(font, label, bx + (bw - font.width(label)) / 2, by + 5,
                on ? 0xFFFFFFFF : 0xFF6C6480, true);
    }

    // --- press --------------------------------------------------------------

    private void renderPress(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, "PRINT A CARD", panelX + 8, panelY + 8, INK, false);
        g.drawString(font, "A random card of the tier. Odds are what you paid for.",
                panelX + 8, panelY + 20, DIM, false);

        int y = panelY + 36;
        int tw = (panelW - 16) / Tier.values().length;
        for (Tier t : Tier.values()) {
            int tx = panelX + 8 + t.ordinal() * tw;
            boolean on = t == tier;
            boolean hover = mouseX >= tx && mouseX < tx + tw - 2 && mouseY >= y && mouseY < y + 16;
            g.fill(tx, y, tx + tw - 2, y + 16, on ? 0xFF4A3E6E : hover ? 0xFF2C2542 : 0xFF1C1830);
            if (on) g.renderOutline(tx, y, tw - 2, 16, GOLD);
            String label = t.label().substring(0, Math.min(4, t.label().length()));
            g.drawString(font, label, tx + (tw - 2 - font.width(label)) / 2, y + 4,
                    on ? INK : DIM, false);
            tierRects[t.ordinal()] = new int[]{tx, y, tw - 2, 16};
        }
        y += 26;

        int max = Recycler.maxStake(tier);
        stake = Recycler.clampStake(tier, stake);
        int pct = Recycler.percent(tier, stake);

        g.drawString(font, "STAKE", panelX + 8, y, GOLD, false);
        String bet = stake + " / " + max + " fragments";
        g.drawString(font, bet, panelX + panelW - 8 - font.width(bet), y, INK, false);
        y += 12;
        int barW = panelW - 16;
        g.fill(panelX + 8, y, panelX + 8 + barW, y + 8, 0xFF12101C);
        g.fill(panelX + 8, y, panelX + 8 + barW * stake / max, y + 8, 0xFF3A7A32);
        g.renderOutline(panelX + 8, y, barW, 8, 0x66FFFFFF);
        y += 14;

        lessRect = button(g, panelX + 8, y, 26, "−", mouseX, mouseY);
        moreRect = button(g, panelX + 38, y, 26, "+", mouseX, mouseY);
        maxRect = button(g, panelX + 68, y, 44, "Max", mouseX, mouseY);
        String odds = pct + "% chance";
        g.drawString(font, odds, panelX + panelW - 8 - font.width(odds), y + 4,
                pct >= 100 ? 0xFF55E06A : INK, false);
        y += 24;

        g.drawString(font, "A misprint keeps nothing. Every stake costs the same",
                panelX + 8, y, 0xFF7E7590, false);
        g.drawString(font, "per card on average — only the variance changes.",
                panelX + 8, y + 10, 0xFF7E7590, false);

        boolean afford = ClientRecycler.fragments() >= stake;
        int by = panelY + panelH - 26;
        String label = afford ? "PRINT  ·  " + stake + " fragments" : "Not enough fragments";
        int bw = Math.max(160, font.width(label) + 24);
        int bx = panelX + (panelW - bw) / 2;
        actionRect = new int[]{bx, by, bw, 18};
        boolean hover = afford && inRect(mouseX, mouseY, actionRect);
        g.fill(bx, by, bx + bw, by + 18, !afford ? 0xFF2A2440 : hover ? 0xFF4B8F3E : 0xFF3A7A32);
        g.renderOutline(bx, by, bw, 18, afford ? (hover ? GOLD : 0x66FFFFFF) : 0xFF3A3350);
        g.drawString(font, label, bx + (bw - font.width(label)) / 2, by + 5,
                afford ? 0xFFFFFFFF : 0xFF6C6480, true);
    }

    private int[] button(GuiGraphics g, int x, int y, int w, String label, int mouseX, int mouseY) {
        boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + 16;
        g.fill(x, y, x + w, y + 16, hover ? 0xFF3A3150 : 0xFF1C1830);
        g.renderOutline(x, y, w, 16, 0x55FFFFFF);
        g.drawString(font, label, x + (w - font.width(label)) / 2, y + 4, INK, false);
        return new int[]{x, y, w, 16};
    }

    // --- interaction --------------------------------------------------------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        int mx = (int) mouseX, my = (int) mouseY;
        if (mode == RecyclerManager.MODE_SHREDDER) {
            for (int r = 0; r < rowRects.size(); r++) {
                if (inRect(mx, my, rowRects.get(r))) {
                    Spare s = spares.get(scroll + r);
                    PacketDistributor.sendToServer(
                            RecyclerActionPayload.shred(s.card().id(), s.foil()));
                    click(0.8f);
                    rebuildSoon();
                    return true;
                }
            }
            if (!spares.isEmpty() && inRect(mx, my, allRect)) {
                PacketDistributor.sendToServer(RecyclerActionPayload.shredAll());
                click(0.7f);
                rebuildSoon();
                return true;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        for (Tier t : Tier.values()) {
            if (inRect(mx, my, tierRects[t.ordinal()])) {
                tier = t;
                stake = Recycler.clampStake(t, stake);
                click(1.0f);
                return true;
            }
        }
        int step = Math.max(1, Recycler.maxStake(tier) / 10);
        if (inRect(mx, my, lessRect)) {
            stake = Recycler.clampStake(tier, stake - step);
            click(0.9f);
            return true;
        }
        if (inRect(mx, my, moreRect)) {
            stake = Recycler.clampStake(tier, stake + step);
            click(1.1f);
            return true;
        }
        if (inRect(mx, my, maxRect)) {
            stake = Recycler.maxStake(tier);
            click(1.2f);
            return true;
        }
        if (ClientRecycler.fragments() >= stake && inRect(mx, my, actionRect)) {
            PacketDistributor.sendToServer(RecyclerActionPayload.print(tier.ordinal(), stake));
            click(1.3f);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double dx, double dy) {
        if (mode == RecyclerManager.MODE_SHREDDER) {
            scroll = Mth.clamp(scroll - (int) Math.signum(dy), 0,
                    Math.max(0, spares.size() - rows()));
            return true;
        }
        stake = Recycler.clampStake(tier,
                stake + (int) Math.signum(dy) * Math.max(1, Recycler.maxStake(tier) / 20));
        return true;
    }

    /** The server answers asynchronously, so re-read the inventory next frame. */
    private void rebuildSoon() {
        if (minecraft != null) {
            minecraft.execute(this::rebuild);
        }
    }

    @Override
    public void tick() {
        super.tick();
        rebuild(); // the inventory is the source of truth and it changes under us
    }

    private void click(float pitch) {
        if (minecraft != null) {
            minecraft.getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), pitch));
        }
    }

    private static boolean inRect(int mx, int my, int[] r) {
        return r != null && mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3];
    }
}
