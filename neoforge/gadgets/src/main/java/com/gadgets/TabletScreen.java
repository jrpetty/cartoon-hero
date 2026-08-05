package com.gadgets;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The Base Tablet's own screen: which hub it is linked to, the headline from
 * that hub, and a way through to the full board.
 *
 * <p>The headline is the point. Most of the time the question is "is anything
 * wrong at home", and that is one line — so it is answered here without opening
 * anything. The board is a button away for when the answer is yes.
 *
 * <p>Every reading carries its age. A base whose chunks are unloaded is not
 * running, so there is nothing newer to know; saying "4m ago" rather than
 * implying live data is the difference between a useful instrument and one that
 * quietly lies when you are furthest from being able to check.
 */
public class TabletScreen extends GadgetScreen {
    private static final int CODE_Y = 20;
    private static final int STATUS_Y = 44;
    private static final int HEAD_Y = 64;
    private static final int STAT_Y = 92;
    private static final int BOARD_Y = 128;

    private EditBox codeField;
    private Button board;
    private Button refresh;
    private int ticks;

    public TabletScreen() {
        super(Component.literal("Base Tablet"), 240, BOARD_Y + 30);
    }

    @Override
    protected void init() {
        super.init();
        codeField = new EditBox(font, left + 52, top + CODE_Y, 116, 14, Component.literal("Code"));
        codeField.setMaxLength(LinkCode.LENGTH);
        codeField.setFilter(t -> t.chars().allMatch(Character::isDigit));
        codeField.setHint(Component.literal("00000000"));
        codeField.setValue(ClientHubReport.code());
        addRenderableWidget(codeField);

        addRenderableWidget(Button.builder(Component.literal("Link"), b -> link())
                .bounds(left + 172, top + CODE_Y, 56, 14).build());

        refresh = addRenderableWidget(Button.builder(Component.literal("Refresh"), b -> {
                    ClientHubReport.asked(ClientHubReport.code());
                    PacketDistributor.sendToServer(new HubReportPayload.Request(ClientHubReport.code()));
                })
                .bounds(left + 12, top + BOARD_Y, 104, 18).build());

        board = addRenderableWidget(Button.builder(Component.literal("Open board"), b -> openBoard())
                .bounds(left + 124, top + BOARD_Y, 104, 18).build());
    }

    /** Write the typed code onto the held tablet, and ask with it in the same breath. */
    private void link() {
        String code = codeField.getValue();
        if (code.isEmpty()) {
            ClientHubReport.forget();
        } else {
            ClientHubReport.asked(code);
        }
        PacketDistributor.sendToServer(new HubReportPayload.Link(code));
    }

    /** Enter links, so the code can be typed and committed without reaching for the mouse. */
    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if ((key == 257 || key == 335) && codeField != null && codeField.isFocused()) {
            link();
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    /**
     * Re-ask once a second while the screen is open.
     *
     * <p>Without this the age would freeze at whatever it was when the screen
     * opened — so a tablet held while stood at the hub would keep saying "12s
     * ago" as the base ticked away in front of you. One small packet a second,
     * only while someone is actually looking at it.
     */
    @Override
    public void tick() {
        if (++ticks % 20 == 0 && LinkCode.isCode(ClientHubReport.code())) {
            PacketDistributor.sendToServer(new HubReportPayload.Request(ClientHubReport.code()));
        }
    }

    /**
     * Hand the reported board to the hub's own screen.
     *
     * <p>A detached block entity is filled from the report and handed over, so
     * the board looks and behaves exactly as it does at the hub instead of being
     * a second, slightly different implementation of the same table. It opens
     * read-only: the buttons that would unlink a gadget are the one thing that
     * cannot work from a thousand blocks away.
     */
    private void openBoard() {
        if (ClientHubReport.state() != ClientHubReport.FOUND
                || minecraft == null || minecraft.level == null || minecraft.player == null) {
            return;
        }
        CommandHubBlockEntity ghost = new CommandHubBlockEntity(
                minecraft.player.blockPosition(), Gadgets.COMMAND_HUB.get().defaultBlockState());
        ghost.handleUpdateTag(ClientHubReport.board(), minecraft.level.registryAccess());
        minecraft.setScreen(new HubScreen(ghost, ClientHubReport.name() + "  ·  " + ClientHubReport.age()));
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float delta) {
        if (board != null) {
            board.active = ClientHubReport.state() == ClientHubReport.FOUND;
        }
        if (refresh != null) {
            refresh.active = LinkCode.isCode(ClientHubReport.code());
        }
        super.render(gfx, mouseX, mouseY, delta);
        int x = left + 12;
        gfx.drawString(font, "Hub", x, top + CODE_Y + 4, DIM, false);

        int state = ClientHubReport.state();
        String status = switch (state) {
            case ClientHubReport.WAITING -> "Asking…";
            case ClientHubReport.FOUND -> ClientHubReport.live()
                    ? "Reporting live" : "Last heard from " + ClientHubReport.age();
            case ClientHubReport.MISSING -> "No hub answering that code";
            default -> "Enter the code shown on a Command Hub";
        };
        int colour = switch (state) {
            case ClientHubReport.FOUND -> ClientHubReport.live() ? GREEN : AMBER;
            case ClientHubReport.MISSING -> RED;
            default -> GRAY;
        };
        gfx.drawString(font, status, x, top + STATUS_Y, colour, false);

        if (state != ClientHubReport.FOUND) {
            gfx.drawString(font, "A hub only reports while its chunks are loaded.",
                    x, top + HEAD_Y, GRAY, false);
            gfx.drawString(font, "Away from base you get its last word, dated.",
                    x, top + HEAD_Y + 11, GRAY, false);
            return;
        }

        gfx.drawString(font, ClientHubReport.name(), x, top + HEAD_Y, AMBER, false);
        gfx.drawString(font, ClientHubReport.dim(), x, top + HEAD_Y + 11, GRAY, false);

        // The headline, read straight out of the reported board.
        var tag = ClientHubReport.board();
        int linked = tag.getList("Nodes", 10).size();
        int alarms = 0;
        long rate = 0;
        for (int i = 0; i < linked; i++) {
            CommandHubBlockEntity.Node n = CommandHubBlockEntity.Node.fromNbt(tag.getList("Nodes", 10).getCompound(i));
            if (n.alarmed()) {
                alarms++;
            }
            if (n.type == CommandHubBlockEntity.TYPE_COUNTER && n.online) {
                rate += n.a;
            }
        }
        gfx.drawString(font, linked + " linked", x, top + STAT_Y, GRAY, false);
        gfx.drawString(font, ItemCounterBlockEntity.compact(rate) + " items/min", x + 76, top + STAT_Y, GRAY, false);
        String alert = alarms == 0 ? "all clear" : alarms + (alarms == 1 ? " alert" : " alerts");
        gfx.drawString(font, alert, left + panelW - 12 - font.width(alert), top + STAT_Y,
                alarms == 0 ? GREEN : RED, false);
    }
}
