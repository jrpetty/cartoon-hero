package com.jrpetty.aztecabyss.client;

import com.jrpetty.aztecabyss.maze.MazeInduction;
import com.jrpetty.aztecabyss.network.MazeInductionPayload;
import com.jrpetty.aztecabyss.network.TradeChoicePayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * The induction: what are you?
 *
 * <p>Coming up into the Glade used to mean standing frozen in the Box while
 * the chat told you to go and right-click a post on a board you could not walk
 * to. The freeze was Slowness at amplifier 250, and vanilla scales the field of
 * view with movement speed - so the first thing the maze did to a new player
 * was zoom their camera in and hold it there. No screen ever opened; the
 * decision the whole week hangs on was a chat instruction and a command.
 *
 * <p>This is that decision, put in front of you. Four cards, one per trade:
 * the pitch, the actual kit you would come up with as item icons, who already
 * wears it, and what your first rank buys. Pick one to read the long version;
 * press the button to come up as it. The screen cannot be dismissed until you
 * do - <em>that</em> is what holds you now, and it holds without touching
 * your camera, your speed or your legs.
 *
 * <p>The server re-sends the sheet every few seconds as insurance against a
 * packet lost in the dimension change; {@link #refresh} takes those in place,
 * so the roster lines stay live and nobody mid-read is thrown back to the top.
 */
public final class MazeInductionScreen extends Screen {

    // The mod's ink palette.
    private static final int BG_TOP = 0xFF0B0A10;
    private static final int BG_BOTTOM = 0xFF060508;
    private static final int CARD_FILL = 0xFF16151E;
    private static final int CARD_HOVER = 0xFF1F1E2A;
    private static final int CARD_EDGE = 0xFF3A384A;
    private static final int TEXT = 0xFFD8D5E4;
    private static final int TEXT_DIM = 0xFF8C88A2;
    private static final int TEXT_FAINT = 0xFF4A4760;
    private static final int GOLD = 0xFFFFC94A;

    private static final int GAP = 8;
    private static final int PAD = 8;
    private static final int LINE_H = 10;
    private static final int ICON = 18;

    /** {@code job, display, blurb, description, takers, perk} per card. */
    private final List<String[]> cards = new ArrayList<>();
    /** The kit each card shows, merged by item so two torch stacks read as one. */
    private final List<List<ItemStack>> kits = new ArrayList<>();

    private int selected = -1;
    private boolean chosen;
    private Button confirm;
    private int age;
    /** The kit icon under the mouse this frame, for its tooltip. */
    private ItemStack hoveredStack = ItemStack.EMPTY;

    public MazeInductionScreen(MazeInductionPayload payload) {
        super(Component.literal("What are you?"));
        for (String packed : payload.cards()) {
            String[] row = new String[6];
            for (int i = 0; i < 6; i++) {
                row[i] = MazeInductionPayload.field(packed, i);
            }
            cards.add(row);
            kits.add(kitFor(row[0]));
        }
    }

    /** A later copy of the sheet: only the roster lines can have changed. */
    public void refresh(MazeInductionPayload payload) {
        for (String packed : payload.cards()) {
            String job = MazeInductionPayload.field(packed, 0);
            for (String[] row : cards) {
                if (row[0].equals(job)) {
                    row[4] = MazeInductionPayload.field(packed, 4);
                }
            }
        }
    }

    /**
     * What you would come up with, as icons.
     *
     * <p>Read from the same kit table the server hands out from, so the
     * picture cannot drift from the pockets. Same items merge (the Runner's
     * two torch stacks become one icon of 32), and the Runner's chart - made
     * server-side from a level, so absent from the table - is stood in for by
     * a plain map so the card is not lying by omission.
     */
    private static List<ItemStack> kitFor(String job) {
        List<ItemStack> out = new ArrayList<>();
        for (ItemStack s : MazeInduction.kit(job)) {
            boolean merged = false;
            for (ItemStack have : out) {
                if (ItemStack.isSameItemSameComponents(have, s)) {
                    have.grow(s.getCount());
                    merged = true;
                    break;
                }
            }
            if (!merged) {
                out.add(s.copy());
            }
        }
        if ("runner".equals(job)) {
            ItemStack chart = new ItemStack(Items.FILLED_MAP);
            chart.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                    Component.literal("§bRunner's Chart"));
            out.add(chart);
        }
        return out;
    }

    // ------------------------------------------------------------------
    // Geometry
    // ------------------------------------------------------------------

    private int cardW() {
        int n = Math.max(1, cards.size());
        return Math.max(96, Math.min(150, (this.width - 32 - GAP * (n - 1)) / n));
    }

    private int rowLeft() {
        int n = cards.size();
        return (this.width - (cardW() * n + GAP * (n - 1))) / 2;
    }

    private static int cardsTop() {
        return 58;
    }

    private int iconsPerRow() {
        return Math.max(3, (cardW() - PAD * 2) / ICON);
    }

    private List<FormattedCharSequence> blurbLines(int i) {
        return this.font.split(Component.literal(cards.get(i)[2]), cardW() - PAD * 2);
    }

    private List<FormattedCharSequence> perkLines(int i) {
        return this.font.split(Component.literal("§8lv1 §7" + cards.get(i)[5]), cardW() - PAD * 2);
    }

    /** Every card is as tall as the tallest, so the row reads as a row. */
    private int cardH() {
        int best = 0;
        for (int i = 0; i < cards.size(); i++) {
            int iconRows = (kits.get(i).size() + iconsPerRow() - 1) / iconsPerRow();
            int h = 24                                   // title
                    + blurbLines(i).size() * LINE_H + 6  // pitch
                    + iconRows * ICON + 6                // the kit
                    + LINE_H + 4                         // roster
                    + perkLines(i).size() * LINE_H + PAD; // first rank
            best = Math.max(best, h);
        }
        return best;
    }

    // ------------------------------------------------------------------
    // Widgets and input
    // ------------------------------------------------------------------

    @Override
    protected void init() {
        confirm = Button.builder(confirmLabel(), b -> choose())
                .bounds(this.width / 2 - 110, this.height - 30, 220, 20).build();
        confirm.active = selected >= 0;
        addRenderableWidget(confirm);
    }

    private Component confirmLabel() {
        return Component.literal(selected < 0 ? "Choose a trade above"
                : "Come up as a " + strip(cards.get(selected)[1]));
    }

    private void select(int i) {
        selected = i;
        if (confirm != null) {
            confirm.setMessage(confirmLabel());
            confirm.active = true;
        }
        if (minecraft != null) {
            minecraft.getSoundManager().play(
                    net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                            net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
    }

    private void choose() {
        if (selected < 0 || chosen) {
            return;
        }
        chosen = true;
        PacketDistributor.sendToServer(new TradeChoicePayload(cards.get(selected)[0]));
        if (minecraft != null) {
            minecraft.setScreen(null);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int top = cardsTop();
        int h = cardH();
        for (int i = 0; i < cards.size(); i++) {
            int x = rowLeft() + i * (cardW() + GAP);
            if (mouseX >= x && mouseX < x + cardW() && mouseY >= top && mouseY < top + h) {
                select(i);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // 1-4 pick a card; Enter takes it. The mouse is not required.
        int digit = keyCode - org.lwjgl.glfw.GLFW.GLFW_KEY_1;
        if (digit >= 0 && digit < cards.size()) {
            select(digit);
            return true;
        }
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER
                || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) {
            choose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** The Box does not let go until you say. */
    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public void onClose() {
        if (chosen && minecraft != null) {
            minecraft.setScreen(null);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ------------------------------------------------------------------
    // Render
    // ------------------------------------------------------------------

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fillGradient(0, 0, this.width, this.height, BG_TOP, BG_BOTTOM);
    }

    /** No blur under type. Same call every screen in this mod makes. */
    @Override
    protected void renderBlurredBackground(float partialTick) {
        // Intentionally empty.
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        age++;
        this.renderBackground(g, mouseX, mouseY, partialTick);
        hoveredStack = ItemStack.EMPTY;
        int cx = this.width / 2;

        g.drawCenteredString(this.font, Component.literal("§8THE BOX"), cx, 12, TEXT_FAINT);
        g.pose().pushPose();
        g.pose().translate(cx, 22, 0);
        g.pose().scale(2.0f, 2.0f, 1.0f);
        g.drawCenteredString(this.font, Component.literal("WHAT ARE YOU?"), 0, 0, GOLD);
        g.pose().popPose();
        g.drawCenteredString(this.font,
                Component.literal("§7The Box does not let go until you say."), cx, 44, TEXT_DIM);

        int top = cardsTop();
        int w = cardW();
        int h = cardH();
        for (int i = 0; i < cards.size(); i++) {
            renderCard(g, i, rowLeft() + i * (w + GAP), top, w, h, mouseX, mouseY);
        }

        // The long version of whichever card is lit, in the room that is left.
        int detailTop = top + h + GAP;
        int detailBottom = this.height - 46;
        if (detailBottom - detailTop >= 30) {
            renderDetail(g, detailTop, detailBottom);
        }

        g.drawCenteredString(this.font, Component.literal(
                        "§8Change your mind later at the board by the bell. The kit is once per game."),
                cx, this.height - 41, TEXT_FAINT);

        super.render(g, mouseX, mouseY, partialTick);
        if (!hoveredStack.isEmpty()) {
            g.renderTooltip(this.font, hoveredStack, mouseX, mouseY);
        }
    }

    private void renderCard(GuiGraphics g, int i, int x, int y, int w, int h, int mouseX, int mouseY) {
        String[] c = cards.get(i);
        int accent = MazeHud.jobAccent(c[0]);
        boolean isSelected = i == selected;
        boolean hovered = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;

        g.fill(x, y, x + w, y + h, hovered || isSelected ? CARD_HOVER : CARD_FILL);
        int edge = isSelected ? accent : hovered ? MazeHud.pulse(accent, 0.55f) : CARD_EDGE;
        g.fill(x, y, x + w, y + 1, edge);
        g.fill(x, y + h - 1, x + w, y + h, edge);
        g.fill(x, y, x + 1, y + h, edge);
        g.fill(x + w - 1, y, x + w, y + h, edge);
        // The trade's colour as a bar across the top; the lit card breathes.
        int bar = isSelected ? MazeHud.pulse(accent, 0.8f + 0.2f * (float) Math.sin(age / 6.0)) : accent;
        g.fill(x + 1, y + 1, x + w - 1, y + 4, bar);

        // Title, with its number so the keyboard hint is on the card itself.
        g.drawString(this.font, Component.literal("§8" + (i + 1) + " "), x + PAD, y + 10, TEXT_FAINT, true);
        g.drawString(this.font, Component.literal(strip(c[1])).withStyle(s -> s.withBold(true)),
                x + PAD + 10, y + 10, accent, true);

        int ty = y + 24;
        for (FormattedCharSequence line : blurbLines(i)) {
            g.drawString(this.font, line, x + PAD, ty, TEXT_DIM, false);
            ty += LINE_H;
        }
        ty += 6;

        // The kit, as the things themselves.
        int per = iconsPerRow();
        List<ItemStack> kit = kits.get(i);
        for (int k = 0; k < kit.size(); k++) {
            int ix = x + PAD + (k % per) * ICON;
            int iy = ty + (k / per) * ICON;
            ItemStack stack = kit.get(k);
            g.renderItem(stack, ix, iy);
            g.renderItemDecorations(this.font, stack, ix, iy);
            if (mouseX >= ix && mouseX < ix + 16 && mouseY >= iy && mouseY < iy + 16) {
                hoveredStack = stack;
            }
        }
        ty += ((kit.size() + per - 1) / per) * ICON + 6;

        // Who already wears it - half of the decision.
        String roster = c[4].isEmpty() ? "§8nobody yet — the Glade needs one"
                : "§8with " + c[4];
        g.drawString(this.font, Component.literal(
                this.font.plainSubstrByWidth(roster, w - PAD * 2)), x + PAD, ty, TEXT_FAINT, false);
        ty += LINE_H + 4;

        for (FormattedCharSequence line : perkLines(i)) {
            g.drawString(this.font, line, x + PAD, ty, TEXT_DIM, false);
            ty += LINE_H;
        }
    }

    private void renderDetail(GuiGraphics g, int top, int bottom) {
        int left = rowLeft();
        int w = cardW() * cards.size() + GAP * (cards.size() - 1);
        g.fill(left, top, left + w, bottom, CARD_FILL);
        g.fill(left, top, left + w, top + 1, CARD_EDGE);
        g.fill(left, bottom - 1, left + w, bottom, CARD_EDGE);
        g.fill(left, top, left + 1, bottom, CARD_EDGE);
        g.fill(left + w - 1, top, left + w, bottom, CARD_EDGE);

        if (selected < 0) {
            g.drawCenteredString(this.font, Component.literal(
                            "§8Pick a trade to read what the days look like."),
                    this.width / 2, top + (bottom - top) / 2 - 4, TEXT_FAINT);
            return;
        }
        String[] c = cards.get(selected);
        g.fill(left + 1, top + 1, left + 4, bottom - 1, MazeHud.jobAccent(c[0]));
        int y = top + 8;
        int max = bottom - 8;
        for (String para : c[3].split("\n\n")) {
            for (FormattedCharSequence line
                    : this.font.split(Component.literal(para.replace("\n", " ")), w - 28)) {
                if (y + LINE_H > max) {
                    return;
                }
                g.drawString(this.font, line, left + 14, y, TEXT, false);
                y += LINE_H;
            }
            y += 4;
        }
    }

    private static String strip(String s) {
        return s == null ? "" : s.replaceAll("§.", "");
    }
}
