package com.jrpetty.aztecabyss.client;

import com.jrpetty.aztecabyss.network.TradeBoardPayload;
import com.jrpetty.aztecabyss.network.TradeChoicePayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * The sign-up sheet: what the trade is, who already does it, and are you sure.
 *
 * <p>Choosing a trade used to be a chat command, which is the wrong register
 * entirely for the one decision that shapes a player's whole week. Now it is a
 * thing you do at the board: right-click the post, read what the job actually
 * is, see who you would be working beside, and put your name down - or step
 * back. The confirm is deliberate, because "I clicked the wrong sign" should
 * never be how somebody ends up farming for eight days.
 *
 * <p>Nothing here decides anything. The confirm sends a wish; the server
 * validates the trade and does the signing on, exactly as the command did.
 */
public class TradeBoardScreen extends Screen {

    private final TradeBoardPayload sheet;

    // The shared chrome, so the board reads as part of the same interface as
    // the slate and the trade sheet.
    private static final int BG_TOP = 0xFF0B0A10;
    private static final int BG_BOTTOM = 0xFF060508;
    private static final int PANEL_FILL = 0xFF14131C;
    private static final int PANEL_EDGE = 0xFF2A2836;
    private static final int TEXT = 0xFFD8D5E4;
    private static final int TEXT_DIM = 0xFF7A7690;
    private static final int TEXT_FAINT = 0xFF4A4760;
    private static final int GOLD = 0xFFFFC94A;

    private static final int PANEL_W = 300;

    private List<FormattedCharSequence> body = new ArrayList<>();

    public TradeBoardScreen(TradeBoardPayload sheet) {
        super(Component.literal("The Trade Board"));
        this.sheet = sheet;
    }

    /** The trade's own colour, pulled from its display name's code. */
    private int accent() {
        String d = sheet.display();
        int idx = d.indexOf('§');
        char code = idx >= 0 && idx + 1 < d.length() ? d.charAt(idx + 1) : 'f';
        return switch (code) {
            case 'b', '1', '9', '3' -> 0xFF58C4DD;   // runner blue
            case '6', 'e' -> 0xFFE0A040;             // builder amber
            case 'a', '2' -> 0xFF63D488;             // med-jack green
            case '0', '8', '7' -> 0xFFB9B4CC;        // track-hoe pale
            default -> 0xFFE0A040;
        };
    }

    private static String strip(String s) {
        return s == null ? "" : s.replaceAll("§.", "");
    }

    private int panelX() {
        return (this.width - PANEL_W) / 2;
    }

    private int panelTop() {
        return Math.max(30, this.height / 2 - 110);
    }

    @Override
    protected void init() {
        // The description, wrapped to the panel, paragraph breaks kept.
        body = new ArrayList<>();
        for (String para : sheet.body().split("\n\n")) {
            body.addAll(this.font.split(
                    Component.literal(para.replace("\n", " ")), PANEL_W - 28));
            body.add(FormattedCharSequence.EMPTY);
        }

        int x = panelX();
        int y = panelTop() + 66 + body.size() * 10 + 26;
        boolean already = strip(sheet.current()).equals(strip(sheet.display()));

        Button confirm = Button.builder(
                        Component.literal(already ? "This is already your trade"
                                : "Sign on as " + strip(sheet.display())),
                        b -> {
                            PacketDistributor.sendToServer(new TradeChoicePayload(sheet.job()));
                            onClose();
                        })
                .bounds(x + 14, y, PANEL_W - 28, 20).build();
        confirm.active = !already;
        addRenderableWidget(confirm);

        addRenderableWidget(Button.builder(Component.literal("Not yet"), b -> onClose())
                .bounds(x + 14, y + 24, PANEL_W - 28, 20).build());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

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
        this.renderBackground(g, mouseX, mouseY, partialTick);

        int x = panelX();
        int top = panelTop();
        int cx = this.width / 2;
        int bottom = top + 66 + body.size() * 10 + 74;

        // The panel, with the trade's colour as a spine down the left edge.
        g.fill(x, top, x + PANEL_W, bottom, PANEL_FILL);
        g.fill(x, top, x + PANEL_W, top + 1, PANEL_EDGE);
        g.fill(x, bottom - 1, x + PANEL_W, bottom, PANEL_EDGE);
        g.fill(x, top, x + 1, bottom, PANEL_EDGE);
        g.fill(x + PANEL_W - 1, top, x + PANEL_W, bottom, PANEL_EDGE);
        g.fill(x + 1, top, x + 4, bottom, accent());

        g.drawCenteredString(this.font, Component.literal("§8THE TRADE BOARD"),
                cx, top + 10, TEXT_FAINT);
        g.pose().pushPose();
        g.pose().translate(cx, top + 24, 0);
        g.pose().scale(2.0f, 2.0f, 1.0f);
        g.drawCenteredString(this.font, Component.literal(strip(sheet.display()).toUpperCase()),
                0, 0, accent());
        g.pose().popPose();

        int y = top + 52;
        // Who already wears it - before the pitch, because "the Glade already
        // has two Runners and no farmer" is half of the decision.
        String takers = sheet.takers().isEmpty()
                ? "§8Nobody on the roster yet. The Glade needs one."
                : "§8On the roster: " + sheet.takers();
        g.drawCenteredString(this.font, Component.literal(takers), cx, y, TEXT_FAINT);
        y += 14;

        for (FormattedCharSequence line : body) {
            g.drawString(this.font, line, x + 14, y, TEXT_DIM, false);
            y += 10;
        }

        // The "are you sure" line, said plainly.
        boolean switching = !sheet.current().isEmpty()
                && !strip(sheet.current()).equals(strip(sheet.display()));
        String sure = switching
                ? "§eYou are a " + strip(sheet.current())
                        + " now. §7Signing on here changes your trade."
                : "§7Take the trade? The Glade will be counting on you.";
        g.drawCenteredString(this.font, Component.literal(sure), cx, y + 4,
                switching ? GOLD : TEXT);

        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(null);
    }
}
