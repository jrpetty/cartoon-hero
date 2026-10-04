package com.jrpetty.aztecabyss.client;

import com.jrpetty.aztecabyss.network.TradeBoardPayload;
import com.jrpetty.aztecabyss.network.TradeChoicePayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
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
public class TradeBoardScreen extends AbyssScreen {

    private final TradeBoardPayload sheet;

    // The shared chrome, so the board reads as part of the same interface as
    // the slate and the trade sheet.
    private static final int TEXT = UiKit.TEXT;
    private static final int TEXT_DIM = UiKit.TEXT_DIM;
    private static final int TEXT_FAINT = UiKit.TEXT_FAINT;
    private static final int GOLD = UiKit.GOLD;

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
    protected int glow() {
        return UiKit.alpha(accent(), 0x22);
    }

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int x = panelX();
        int top = panelTop();
        int cx = this.width / 2;
        int bottom = top + 66 + body.size() * 10 + 74;

        // The panel, with the trade's colour as a spine down the left edge.
        UiKit.panel(g, x, top, PANEL_W, bottom - top);
        g.fill(x + 1, top + 1, x + 4, bottom - 1, accent());

        g.drawCenteredString(this.font, "THE TRADE BOARD", cx, top + 10, TEXT_FAINT);
        UiKit.big(g, this.font, Component.literal(strip(sheet.display()).toUpperCase(java.util.Locale.ROOT)),
                cx, top + 23, 2.0f, accent());
        UiKit.fret(g, cx, top + 43, PANEL_W / 2 - 20, accent());

        int y = top + 52;
        // Who already wears it - before the pitch, because "the Glade already
        // has two Runners and no farmer" is half of the decision.
        String takers = sheet.takers().isEmpty()
                ? "Nobody on the roster yet. The Glade needs one."
                : "On the roster: " + strip(sheet.takers());
        g.drawCenteredString(this.font, Component.literal(takers), cx, y, TEXT_FAINT);
        y += 14;

        for (FormattedCharSequence line : body) {
            g.drawString(this.font, line, x + 14, y, TEXT_DIM, true);
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
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(null);
    }
}
