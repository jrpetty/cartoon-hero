package com.gadgets;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The Base Tablet's own screen: which hub it is linked to, what that hub is
 * saying, and a way through to the full board.
 *
 * <p>The headline is the point. Most of the time the question is "is anything
 * wrong at home", and that is a lamp, three numbers and at most a few lines —
 * so it is answered here without opening anything. The board is one button away
 * for when the answer is yes.
 *
 * <p>Every reading carries its age. A base whose chunks are unloaded is not
 * running, so there is nothing newer to know; saying "4m ago" rather than
 * implying live data is the difference between a useful instrument and one that
 * quietly lies when you are furthest from being able to check.
 */
public class TabletScreen extends GadgetScreen {
    private static final int W = 246;

    private static final int CODE_Y = 20;
    private static final int STATUS_Y = 40;
    private static final int CARD_Y = 54;
    private static final int CARD_H = 28;
    private static final int TILE_Y = 88;
    private static final int TILE_H = 30;
    private static final int LIST_Y = 122;
    private static final int LIST_ROW = 12;
    private static final int LIST_ROWS = 3;
    private static final int BUTTON_Y = 186;

    private static final int CARD_BG = 0xFF161A20;
    private static final int TILE_BG = 0xFF1B2028;

    private EditBox codeField;
    private Button board;
    private Button refresh;
    private int ticks;

    // --- the last report, read once per reply rather than once per frame ---

    /** One gadget worth flagging, and what is wrong with it. */
    private record Alert(String label, String issue) {
    }

    private CompoundTag summarised;
    private int linked;
    private long rate;
    private final List<Alert> alerts = new ArrayList<>();

    public TabletScreen() {
        super(Component.literal("Base Tablet"), W, BUTTON_Y + 30);
    }

    @Override
    protected void init() {
        super.init();
        codeField = new EditBox(font, left + 44, top + CODE_Y, 116, 14, Component.literal("Code"));
        codeField.setMaxLength(LinkCode.LENGTH);
        codeField.setFilter(t -> t.chars().allMatch(Character::isDigit));
        codeField.setHint(Component.literal("00000000"));
        codeField.setValue(ClientHubReport.code());
        addRenderableWidget(codeField);
        setInitialFocus(codeField);

        addRenderableWidget(PanelButton.of(Component.literal("Link"), b -> link(), left + 166, top + CODE_Y, 68, 14));

        refresh = addRenderableWidget(PanelButton.of(Component.literal("Refresh"), b -> {
                    ClientHubReport.asked(ClientHubReport.code());
                    PacketDistributor.sendToServer(new HubReportPayload.Request(ClientHubReport.code()));
                }, left + 12, top + BUTTON_Y, 106, 18));

        board = addRenderableWidget(PanelButton.of(Component.literal("Open board"), b -> openBoard(), left + 128, top + BUTTON_Y, 106, 18));
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

    /** Enter links, so a code can be typed and committed without reaching for the mouse. */
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
        minecraft.setScreen(new HubScreen(ghost, ClientHubReport.name() + "  ·  " + ClientHubReport.age(), this));
    }

    /**
     * Read the reported board into the few things this screen shows.
     *
     * <p>Re-read when the tag itself changes, which is once per reply, rather
     * than every frame: the whole board is walked here and doing that sixty
     * times a second to draw three numbers would be silly.
     */
    private void summarise() {
        CompoundTag tag = ClientHubReport.board();
        if (tag == summarised) {
            return;
        }
        summarised = tag;
        alerts.clear();
        rate = 0;
        var list = tag.getList("Nodes", 10);
        linked = list.size();
        for (int i = 0; i < list.size(); i++) {
            CommandHubBlockEntity.Node n = CommandHubBlockEntity.Node.fromNbt(list.getCompound(i));
            if (n.type == CommandHubBlockEntity.TYPE_COUNTER && n.online) {
                rate += n.a;
            }
            if (!n.alarmed()) {
                continue;
            }
            String kind = CommandHubBlockEntity.kindOf(n.type);
            String issue = switch (n.type) {
                case CommandHubBlockEntity.TYPE_COUNTER -> "STALLED";
                case CommandHubBlockEntity.TYPE_MONITOR -> "LOW · " + ItemCounterBlockEntity.compact(n.a)
                        + " < " + ItemCounterBlockEntity.compact(n.b);
                default -> "LOW · " + n.d + "%";
            };
            alerts.add(new Alert(n.label.isBlank() ? "(unnamed " + kind + ")" : n.label, issue));
        }
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float delta) {
        int state = ClientHubReport.state();
        boolean found = state == ClientHubReport.FOUND;
        if (board != null) {
            board.active = found;
        }
        if (refresh != null) {
            refresh.active = LinkCode.isCode(ClientHubReport.code());
        }
        super.render(gfx, mouseX, mouseY, delta);
        summarise();

        int x = left + 12;
        gfx.drawString(font, "HUB", x, top + CODE_Y + 4, DIM, false);

        // Status lamp and line — the first thing read, so it is on its own row.
        boolean live = ClientHubReport.live();
        int colour = switch (state) {
            case ClientHubReport.FOUND -> live ? GREEN : AMBER;
            case ClientHubReport.MISSING -> RED;
            default -> GRAY;
        };
        String status = switch (state) {
            case ClientHubReport.WAITING -> "Asking…";
            case ClientHubReport.FOUND -> live ? "Reporting live" : "Last heard " + ClientHubReport.age();
            case ClientHubReport.MISSING -> "No hub answering that code";
            default -> "Enter the code shown on a Command Hub";
        };
        gfx.drawString(font, "●", x, top + STATUS_Y, colour, false);
        gfx.drawString(font, status, x + 12, top + STATUS_Y, colour, false);
        if (found) {
            String tail = alerts.isEmpty() ? "ALL CLEAR"
                    : alerts.size() + (alerts.size() == 1 ? " ALERT" : " ALERTS");
            gfx.drawString(font, tail, left + W - 12 - font.width(tail), top + STATUS_Y,
                    alerts.isEmpty() ? GREEN : RED, false);
        }

        if (!found) {
            explain(gfx, x, state);
            return;
        }

        // Identity card: which base this is, and where.
        panel(gfx, left + 8, top + CARD_Y, left + W - 8, top + CARD_Y + CARD_H, CARD_BG);
        gfx.drawString(font, trim(ClientHubReport.name(), 30), x, top + CARD_Y + 6, AMBER, false);
        gfx.drawString(font, trim(ClientHubReport.dim(), 34), x, top + CARD_Y + 17, GRAY, false);

        // Three tiles: the whole base in three numbers.
        tile(gfx, 0, "LINKED", String.valueOf(linked), AMBER);
        tile(gfx, 1, "PER MIN", ItemCounterBlockEntity.compact(rate), AMBER);
        tile(gfx, 2, "ALERTS", String.valueOf(alerts.size()), alerts.isEmpty() ? GREEN : RED);

        // What is actually wrong, if anything — the reason to have looked.
        if (alerts.isEmpty()) {
            gfx.drawString(font, "NOTHING NEEDS YOU", x, top + LIST_Y, DIM, false);
            gfx.drawString(font, linked == 0
                            ? "No gadgets linked to this hub yet."
                            : "Every linked gadget is running normally.",
                    x, top + LIST_Y + 14, GRAY, false);
            if (!live) {
                gfx.drawString(font, "…as of " + ClientHubReport.age() + ".", x, top + LIST_Y + 25, GRAY, false);
            }
            return;
        }

        gfx.drawString(font, "NEEDS ATTENTION", x, top + LIST_Y, DIM, false);
        int shown = Math.min(alerts.size(), LIST_ROWS);
        for (int i = 0; i < shown; i++) {
            Alert a = alerts.get(i);
            int y = top + LIST_Y + 13 + i * LIST_ROW;
            if (i % 2 == 0) {
                gfx.fill(left + 8, y - 2, left + W - 8, y + 9, TILE_BG);
            }
            gfx.drawString(font, "●", x, y, RED, false);
            gfx.drawString(font, trim(a.label(), 18), x + 11, y, AMBER, false);
            gfx.drawString(font, a.issue(), left + W - 12 - font.width(a.issue()), y, RED, false);
        }
        if (alerts.size() > shown) {
            String more = "+" + (alerts.size() - shown) + " more on the board";
            gfx.drawString(font, more, x, top + LIST_Y + 15 + shown * LIST_ROW, GRAY, false);
        }
    }

    /** The unlinked / unanswered case: say why, and what to do about it. */
    private void explain(GuiGraphics gfx, int x, int state) {
        panel(gfx, left + 8, top + CARD_Y, left + W - 8, top + CARD_Y + 62, CARD_BG);
        if (state == ClientHubReport.MISSING) {
            gfx.drawString(font, "Nothing is answering on those digits.", x, top + CARD_Y + 8, AMBER, false);
            gfx.drawString(font, "Either the code is wrong, or that hub has", x, top + CARD_Y + 22, GRAY, false);
            gfx.drawString(font, "not been in a loaded chunk since the", x, top + CARD_Y + 33, GRAY, false);
            gfx.drawString(font, "server last started.", x, top + CARD_Y + 44, GRAY, false);
            return;
        }
        gfx.drawString(font, "Link this tablet to a Command Hub.", x, top + CARD_Y + 8, AMBER, false);
        gfx.drawString(font, "Its code is in the top corner of the hub's", x, top + CARD_Y + 22, GRAY, false);
        gfx.drawString(font, "own screen — click it there to copy.", x, top + CARD_Y + 33, GRAY, false);
        gfx.drawString(font, "Type it above, then press Link.", x, top + CARD_Y + 44, GRAY, false);

        gfx.drawString(font, "A hub only reports while its chunks are loaded.",
                x, top + TILE_Y + 42, GRAY, false);
        gfx.drawString(font, "Away from base you get its last word, dated.",
                x, top + TILE_Y + 53, GRAY, false);
    }

    /** One of the three headline tiles: a small caption over a big number. */
    private void tile(GuiGraphics gfx, int slot, String caption, String value, int colour) {
        int gap = 6;
        int w = (W - 24 - gap * 2) / 3;
        int x0 = left + 12 + slot * (w + gap);
        int y0 = top + TILE_Y;
        panel(gfx, x0, y0, x0 + w, y0 + TILE_H, TILE_BG);
        gfx.drawString(font, caption, x0 + (w - font.width(caption)) / 2, y0 + 5, DIM, false);

        // Drawn at 1.5× so the number carries the tile, which is the whole point
        // of a tile — the caption is there to be read once and then ignored.
        float scale = 1.5F;
        gfx.pose().pushPose();
        gfx.pose().translate(x0 + w / 2F - font.width(value) * scale / 2F, y0 + 15F, 0F);
        gfx.pose().scale(scale, scale, 1F);
        gfx.drawString(font, value, 0, 0, colour, false);
        gfx.pose().popPose();
    }

    private static String trim(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }
}
