package com.jrpetty.mobtrumps.client;

import com.jrpetty.mobtrumps.CardIdentityService;
import com.jrpetty.mobtrumps.MobCardItem;
import com.jrpetty.mobtrumps.RecyclerActionPayload;
import com.jrpetty.mobtrumps.RecyclerManager;
import com.jrpetty.mobtrumps.game.CardCondition;
import com.jrpetty.mobtrumps.game.MobCard;
import com.jrpetty.mobtrumps.game.MobCards;
import com.jrpetty.mobtrumps.game.Recycler;
import com.jrpetty.mobtrumps.game.Tier;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Both recycler machines behind one screen: a list on the left, the card it is
 * talking about on the right.
 *
 * <p>The <b>Shredder</b> shows the spares you are carrying — a card is spare
 * once its mob is filed in your book — and renders whichever one you are
 * pointing at, full size, so you can <em>see</em> the thing before you destroy
 * it. Shredding a stack is irreversible, so Shred All arms before it fires.
 *
 * <p>The <b>Press</b> shows the odds your stake buys as a bar you can read at a
 * glance, and states the arithmetic plainly: the expected cost per card is the
 * same at every stake, so the only thing you are choosing is variance.
 */
public class RecyclerScreen extends Screen {

    private static final int INK = 0xFFF2ECDD;
    private static final int DIM = 0xFF9A93A8;
    private static final int FAINT = 0xFF7E7590;
    private static final int GOLD = 0xFFE3C071;
    private static final int PLATE = 0xFF241F33;
    private static final int SUNK = 0xFF15121F;
    private static final long ARM_MS = 2600L;
    private static final int ROW_H = 14;

    private static final String[] EMPTY_HELP = {
            "Every card you're carrying is one",
            "you still need. File a duplicate in",
            "your book and its spares appear here.",
    };

    private record Spare(MobCard card, boolean foil, int condition, int value) {
    }

    private final int mode;
    private final Map<String, LivingEntity> entityCache = new HashMap<>();

    private int panelX, panelY, panelW, panelH;
    private int listX, listW, previewX, previewW;
    private int scroll;
    private Tier tier = Tier.COMMON;
    private int stake = Recycler.MIN_STAKE;
    private long armedAt = -1;
    /** When the current machine run started, or -1 when the machine is idle. */
    private long runStart = -1;
    private MobCard runCard;
    private boolean runFoil;

    private final List<Spare> spares = new ArrayList<>();
    private final List<int[]> rowRects = new ArrayList<>();
    private Spare focus;
    private int[] actionRect = {0, 0, 0, 0};
    private final int[][] tierRects = new int[Tier.values().length][];
    private int[] lessRect = {0, 0, 0, 0};
    private int[] moreRect = {0, 0, 0, 0};
    private int[] maxRect = {0, 0, 0, 0};
    private int[] minRect = {0, 0, 0, 0};

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
        // budget the preview column first, then give the list what is left, so
        // a narrow window loses the picture rather than clipping the controls
        panelW = Math.min(410, width - 20);
        panelH = Math.min(230, height - 56);
        panelX = (width - panelW) / 2;
        panelY = Math.max(30, (height - panelH) / 2);
        previewW = panelW >= 310 ? 122 : 0;
        listX = panelX + 10;
        listW = panelW - 20 - (previewW > 0 ? previewW + 8 : 0);
        previewX = panelX + panelW - 10 - previewW;
        rebuild();
    }

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
            if (!ClientCollection.isStored(card.id(), foil)) continue;   // not a spare
            int condition = CardIdentityService.wearOf(s).condition();
            spares.add(new Spare(card, foil, condition, Recycler.yield(card.tier(), condition)));
        }
        // worst condition first: the ones you should be pulping, at the top
        spares.sort((a, b) -> a.condition() != b.condition()
                ? a.condition() - b.condition()
                : b.value() - a.value());
        scroll = Mth.clamp(scroll, 0, Math.max(0, spares.size() - rows()));
        if (spares.isEmpty()) {
            armedAt = -1;
        }
    }

    private int rows() {
        return Math.max(1, (bodyBottom() - bodyTop()) / ROW_H);
    }

    private int bodyTop() {
        return panelY + 44;
    }

    private int bodyBottom() {
        return panelY + panelH - 30;
    }

    // --- render -------------------------------------------------------------

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        long now = System.currentTimeMillis();
        g.fillGradient(0, 0, width, height, 0xF0161320, 0xF00A0810);
        boolean press = mode == RecyclerManager.MODE_PRESS;

        var pose = g.pose();
        pose.pushPose();
        pose.translate(width / 2f, panelY - 26f, 0);
        pose.scale(1.7f, 1.7f, 1f);
        String title = press ? "PRINTING PRESS" : "CARD SHREDDER";
        g.drawString(font, title, -font.width(title) / 2, 0, GOLD, true);
        pose.popPose();

        // panel: a lifted plate with a hairline and a coloured cap
        g.fill(panelX - 3, panelY - 3, panelX + panelW + 3, panelY + panelH + 3, 0xFF15121F);
        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, PLATE);
        g.fill(panelX, panelY, panelX + panelW, panelY + 2, press ? 0xFF3A7A32 : 0xFF8A4030);

        // fragment counter, always in the same place on both machines
        String frag = ClientRecycler.fragments() + "";
        int fw = font.width(frag) + font.width(" fragments") + 12;
        g.fill(panelX + panelW - 8 - fw, panelY + 7, panelX + panelW - 8, panelY + 20, SUNK);
        g.drawString(font, frag, panelX + panelW - 6 - fw, panelY + 10, GOLD, false);
        g.drawString(font, " fragments", panelX + panelW - 6 - fw + font.width(frag),
                panelY + 10, DIM, false);

        if (press) {
            renderPress(g, mouseX, mouseY, now);
        } else {
            renderShredder(g, mouseX, mouseY, now);
        }
        g.drawCenteredString(font, "ESC to close", width / 2, panelY + panelH + 9, 0xFF5F5875);
    }

    private void renderShredder(GuiGraphics g, int mouseX, int mouseY, long now) {
        g.drawString(font, "YOUR SPARES", panelX + 10, panelY + 10, INK, false);
        g.drawString(font, fit("Spare = its mob is already filed in your book.  Worst condition first.",
                panelW - 20), panelX + 10, panelY + 24, FAINT, false);
        g.fill(panelX + 10, panelY + 38, panelX + panelW - 10, panelY + 39, 0x30FFFFFF);

        rowRects.clear();
        focus = null;
        int y = bodyTop();
        int shown = Math.min(rows(), spares.size() - scroll);
        int total = 0;
        for (Spare s : spares) total += s.value();

        if (spares.isEmpty()) {
            g.drawString(font, "Nothing spare.", listX, y + 8, DIM, false);
            for (int i = 0; i < EMPTY_HELP.length; i++) {
                g.drawString(font, fit(EMPTY_HELP[i], listW), listX, y + 22 + i * 10, FAINT, false);
            }
        }

        for (int r = 0; r < shown; r++) {
            Spare s = spares.get(scroll + r);
            boolean hover = mouseX >= listX && mouseX < listX + listW
                    && mouseY >= y && mouseY < y + ROW_H - 1;
            if (hover) {
                focus = s;
            }
            g.fill(listX, y, listX + listW, y + ROW_H - 1, hover ? 0xFF3A3150 : 0xFF1C1830);
            // a condition stripe: the row reads before you read the row
            g.fill(listX, y, listX + 2, y + ROW_H - 1,
                    CardCondition.color(s.condition()) | 0xFF000000);
            String name = (s.foil() ? "✦ " : "") + s.card().displayName();
            g.drawString(font, fit(name, listW - 96), listX + 6, y + 3,
                    s.foil() ? 0xFFC77BFF : INK, false);
            String cond = s.condition() + "%";
            g.drawString(font, cond, listX + listW - 44 - font.width(cond), y + 3,
                    CardCondition.color(s.condition()) | 0xFF000000, false);
            String val = "+" + s.value();
            g.drawString(font, val, listX + listW - 8 - font.width(val), y + 3, GOLD, false);
            rowRects.add(new int[]{listX, y, listW, ROW_H - 1});
            y += ROW_H;
        }
        drawScrollbar(g, listX + listW + 2, bodyTop(), bodyBottom() - bodyTop(),
                spares.size(), rows(), scroll);

        // the machine itself: throat, drum, bar, tray
        if (previewW > 0) {
            boolean running = runStart > 0;
            float phase = running
                    ? Mth.clamp((now - runStart) / (float) MachineView.RUN_MS, 0f, 1f) : -1f;
            MobCard inJaws = running ? runCard : (focus == null ? null : focus.card());
            boolean jawsFoil = running ? runFoil : (focus != null && focus.foil());
            int payout = ClientRecycler.resolvedSince(runStart) ? ClientRecycler.amount()
                    : (running ? 0 : (focus == null ? 0 : focus.value()));
            MachineView.shredder(g, font, previewX, bodyTop(), previewW,
                    bodyBottom() - bodyTop(), phase, inJaws,
                    inJaws == null ? null
                            : CardRenderer.portraitEntity(minecraft, inJaws, entityCache),
                    jawsFoil, payout, now);
        }

        // Shred All arms before it fires: this is irreversible and it is one click
        boolean on = !spares.isEmpty() && runStart < 0;
        boolean armed = on && armedAt > 0 && now - armedAt < ARM_MS;
        String label = !on ? "Nothing to shred"
                : armed ? "Click again to shred " + spares.size() + " cards"
                : "Shred all " + spares.size() + "  ·  +" + total;
        int by = panelY + panelH - 24;
        int bw = Math.max(180, font.width(label) + 26);
        int bx = panelX + (panelW - bw) / 2;
        actionRect = new int[]{bx, by, bw, 18};
        boolean hover = on && inRect(mouseX, mouseY, actionRect);
        int base = !on ? 0xFF2A2440 : armed ? 0xFFB4482E : hover ? 0xFF8A4030 : 0xFF6E3325;
        if (armed) {
            // the arming window drains, so it is obvious it will lapse
            float left = 1f - (now - armedAt) / (float) ARM_MS;
            g.fill(bx - 2, by - 2, bx + bw + 2, by + 20, 0x66FF8A5A);
            g.fill(bx, by + 18, bx + (int) (bw * left), by + 20, GOLD);
        }
        g.fill(bx, by, bx + bw, by + 18, base);
        g.renderOutline(bx, by, bw, 18, on ? (hover ? GOLD : 0x66FFFFFF) : 0xFF3A3350);
        g.drawString(font, label, bx + (bw - font.width(label)) / 2, by + 5,
                on ? 0xFFFFFFFF : 0xFF6C6480, true);
        if (on && !armed) {
            // shares the line with the centred button, so it takes what is left
            g.drawString(font, fit("click a row to shred one", bx - panelX - 16),
                    panelX + 10, by + 5, FAINT, false);
        }
    }

    private void renderPress(GuiGraphics g, int mouseX, int mouseY, long now) {
        g.drawString(font, "PRINT A CARD", panelX + 10, panelY + 10, INK, false);
        g.drawString(font, fit("A random card of the tier you pay for.", panelW - 20),
                panelX + 10, panelY + 24, FAINT, false);
        g.fill(panelX + 10, panelY + 38, panelX + panelW - 10, panelY + 39, 0x30FFFFFF);

        int max = Recycler.maxStake(tier);
        stake = Recycler.clampStake(tier, stake);
        int pct = Recycler.percent(tier, stake);
        int col = panelX + 10;
        int colW = panelW - 20 - (previewW > 0 ? previewW + 8 : 0);
        int y = bodyTop();

        int tw = colW / Tier.values().length;
        for (Tier t : Tier.values()) {
            int tx = col + t.ordinal() * tw;
            boolean on = t == tier;
            boolean hover = mouseX >= tx && mouseX < tx + tw - 2 && mouseY >= y && mouseY < y + 17;
            g.fill(tx, y, tx + tw - 2, y + 17, on ? 0xFF4A3E6E : hover ? 0xFF2C2542 : 0xFF1C1830);
            g.fill(tx, y, tx + tw - 2, y + 2, CardRenderer.tierColor(t));
            if (on) g.renderOutline(tx, y, tw - 2, 17, GOLD);
            // abbreviate rather than truncate: "Unc" reads, "Uncommo…" does not
            String full = t.label();
            String label = font.width(full) + 8 <= tw ? full : abbrev(t);
            g.drawString(font, label, tx + (tw - 2 - font.width(label)) / 2, y + 6,
                    on ? INK : DIM, false);
            tierRects[t.ordinal()] = new int[]{tx, y, tw - 2, 17};
        }
        y += 26;

        // the odds bar: the whole decision, in one shape
        g.drawString(font, "ODDS", col, y, GOLD, false);
        String pctText = pct + "%";
        g.drawString(font, pctText, col + colW - font.width(pctText), y,
                pct >= 100 ? 0xFF55E06A : INK, false);
        y += 12;
        g.fill(col, y, col + colW, y + 10, SUNK);
        int fill = colW * stake / max;
        g.fillGradient(col, y, col + fill, y + 10, 0xFF4B8F3E, 0xFF2E6B26);
        g.renderOutline(col, y, colW, 10, 0x66FFFFFF);
        // the halfway mark, because "50% at" is the number people quote
        int half = col + colW / 2;
        g.fill(half, y, half + 1, y + 10, 0x99FFFFFF);
        y += 16;

        String bet = stake + " of " + max + " fragments";
        g.drawString(font, bet, col, y, DIM, false);
        y += 12;
        minRect = button(g, col, y, 30, "Min", mouseX, mouseY);
        lessRect = button(g, col + 34, y, 24, "−", mouseX, mouseY);
        moreRect = button(g, col + 62, y, 24, "+", mouseX, mouseY);
        maxRect = button(g, col + 90, y, 34, "Max", mouseX, mouseY);
        y += 24;

        g.drawString(font, fit("Every stake costs the same per card", colW), col, y, FAINT, false);
        g.drawString(font, fit("on average. Only the risk changes.", colW), col, y + 10, FAINT, false);

        if (previewW > 0) {
            boolean running = runStart > 0;
            float phase = running
                    ? Mth.clamp((now - runStart) / (float) MachineView.RUN_MS, 0f, 1f) : -1f;
            boolean resolved = ClientRecycler.resolvedSince(runStart);
            boolean hit = resolved && ClientRecycler.kind() == 2;   // PRINT_HIT
            MobCard printed = resolved ? ClientRecycler.card() : null;
            MachineView.press(g, font, previewX, bodyTop(), previewW,
                    bodyBottom() - bodyTop(), phase, resolved, hit, printed,
                    printed == null ? null
                            : CardRenderer.portraitEntity(minecraft, printed, entityCache), now);
            if (!running) {
                String pool = poolSize(tier) + " cards in this tier";
                g.drawString(font, fit(pool, previewW - 8), previewX + 4,
                        bodyBottom() - 10, DIM, false);
            }
        }

        boolean afford = ClientRecycler.fragments() >= stake && runStart < 0;
        int by = panelY + panelH - 24;
        String label = runStart > 0 ? "Printing…"
                : ClientRecycler.fragments() >= stake ? "PRINT  ·  " + stake
                : "Need " + stake + " fragments";
        int bw = Math.max(180, font.width(label) + 26);
        int bx = panelX + (panelW - bw) / 2;
        actionRect = new int[]{bx, by, bw, 18};
        boolean hover = afford && inRect(mouseX, mouseY, actionRect);
        g.fill(bx, by, bx + bw, by + 18, !afford ? 0xFF2A2440 : hover ? 0xFF4B8F3E : 0xFF3A7A32);
        g.renderOutline(bx, by, bw, 18, afford ? (hover ? GOLD : 0x66FFFFFF) : 0xFF3A3350);
        g.drawString(font, label, bx + (bw - font.width(label)) / 2, by + 5,
                afford ? 0xFFFFFFFF : 0xFF6C6480, true);
    }

    /** True while a run is playing out; the controls are inert until it ends. */
    private boolean busy(long now) {
        if (runStart < 0) {
            return false;
        }
        if (now - runStart >= MachineView.RUN_MS) {
            runStart = -1;
            return false;
        }
        return true;
    }

    private void startRun(long now, MobCard card, boolean foil) {
        runStart = now;
        runCard = card;
        runFoil = foil;
        ClientRecycler.clear();
    }

    /** Three letters that still read as the tier when the button is narrow. */
    private static String abbrev(Tier tier) {
        return switch (tier) {
            case COMMON -> "Com";
            case UNCOMMON -> "Unc";
            case RARE -> "Rare";
            case EPIC -> "Epic";
            case LEGENDARY -> "Leg";
        };
    }

    private static int poolSize(Tier tier) {
        int n = 0;
        for (MobCard card : MobCards.ALL) {
            if (card.tier() == tier) n++;
        }
        return n;
    }

    private void drawScrollbar(GuiGraphics g, int x, int y, int h, int total, int visible, int at) {
        if (total <= visible || h < 20) {
            return;
        }
        g.fill(x, y, x + 3, y + h, 0x30FFFFFF);
        int thumbH = Math.max(14, h * visible / total);
        int thumbY = y + (int) ((long) (h - thumbH) * at / Math.max(1, total - visible));
        g.fill(x, thumbY, x + 3, thumbY + thumbH, 0xFF4A4066);
    }

    private int[] button(GuiGraphics g, int x, int y, int w, String label, int mouseX, int mouseY) {
        boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + 16;
        g.fill(x, y, x + w, y + 16, hover ? 0xFF3A3150 : 0xFF1C1830);
        g.renderOutline(x, y, w, 16, hover ? 0x99FFFFFF : 0x55FFFFFF);
        g.drawString(font, label, x + (w - font.width(label)) / 2, y + 4, INK, false);
        return new int[]{x, y, w, 16};
    }

    private String fit(String text, int maxWidth) {
        if (maxWidth <= 0 || font.width(text) <= maxWidth) {
            return text;
        }
        String cut = font.plainSubstrByWidth(text, Math.max(1, maxWidth - font.width("…")));
        return cut.isEmpty() ? "" : cut + "…";
    }

    // --- interaction --------------------------------------------------------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        int mx = (int) mouseX, my = (int) mouseY;
        long now = System.currentTimeMillis();
        if (busy(now)) {
            return true;   // the machine is running; let it finish
        }

        if (mode == RecyclerManager.MODE_SHREDDER) {
            for (int r = 0; r < rowRects.size(); r++) {
                if (inRect(mx, my, rowRects.get(r))) {
                    Spare s = spares.get(scroll + r);
                    PacketDistributor.sendToServer(
                            RecyclerActionPayload.shred(s.card().id(), s.foil()));
                    startRun(now, s.card(), s.foil());
                    click(0.8f);
                    armedAt = -1;
                    return true;
                }
            }
            if (!spares.isEmpty() && inRect(mx, my, actionRect)) {
                if (armedAt < 0 || now - armedAt >= ARM_MS) {
                    armedAt = now;          // first click only arms it
                    click(0.6f);
                    return true;
                }
                PacketDistributor.sendToServer(RecyclerActionPayload.shredAll());
                startRun(now, spares.get(0).card(), spares.get(0).foil());
                armedAt = -1;
                click(0.5f);
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
        if (inRect(mx, my, minRect)) {
            stake = Recycler.MIN_STAKE;
            click(0.9f);
            return true;
        }
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
            startRun(now, null, false);
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

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (mode == RecyclerManager.MODE_PRESS) {
            int step = Math.max(1, Recycler.maxStake(tier) / 10);
            switch (keyCode) {
                case 263 -> {   // left
                    stake = Recycler.clampStake(tier, stake - step);
                    return true;
                }
                case 262 -> {   // right
                    stake = Recycler.clampStake(tier, stake + step);
                    return true;
                }
                case 265, 264 -> {   // up / down through the tiers
                    Tier[] all = Tier.values();
                    int next = Mth.clamp(tier.ordinal() + (keyCode == 264 ? 1 : -1),
                            0, all.length - 1);
                    tier = all[next];
                    stake = Recycler.clampStake(tier, stake);
                    click(1.0f);
                    return true;
                }
                default -> { }
            }
        } else if (keyCode == 265 || keyCode == 264) {
            scroll = Mth.clamp(scroll + (keyCode == 264 ? 1 : -1), 0,
                    Math.max(0, spares.size() - rows()));
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void tick() {
        super.tick();
        rebuild(); // the inventory is the source of truth and it changes under us
        // hold the finished frame long enough to read, then go back to idle
        if (runStart > 0 && System.currentTimeMillis() - runStart > MachineView.RUN_MS + 4000L) {
            runStart = -1;
            ClientRecycler.clear();
        }
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
