package com.gadgets;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The tablet's lock: four digits set the first time it is opened, asked for on
 * every session after that.
 *
 * <p>This screen is the polite face of the lock, not the lock itself — the
 * server refuses to answer a locked tablet until the passcode has been proven,
 * so skipping this screen earns a client silence, not a board. Input is drawn
 * masked because a passcode typed at a public crafting bench is typed in front
 * of whoever is standing there.
 */
public class TabletLockScreen extends GadgetScreen {
    private static final int HEAD_Y = 20;
    private static final int BODY_Y = 34;

    /** True when choosing a passcode (first open, or changing); false when entering one. */
    private final boolean setting;

    private EditBox pin;
    private EditBox again;
    /** A local complaint ("they don't match") shown until the next keystroke. */
    private String gripe = "";

    public TabletLockScreen(boolean setting) {
        super(Component.literal("Base Tablet"), 208, setting ? 158 : 132);
        this.setting = setting;
    }

    @Override
    protected void init() {
        super.init();
        int fieldY = setting ? BODY_Y + 26 : BODY_Y + 22;
        pin = masked(fieldY);
        addRenderableWidget(pin);
        setInitialFocus(pin);
        if (setting) {
            again = masked(fieldY + 22);
            addRenderableWidget(again);
        }
        addRenderableWidget(PanelButton.of(
                Component.literal(setting ? "Set passcode" : "Unlock"), b -> submit(),
                left + 12, top + panelH - 32, panelW - 24, 18));
    }

    /** A four-digit box that draws dots, whatever is actually in it. */
    private EditBox masked(int y) {
        EditBox box = new EditBox(font, left + 70, top + y, 68, 14, Component.literal("Passcode"));
        box.setMaxLength(BaseTabletItem.LOCK_LENGTH);
        box.setFilter(t -> t.chars().allMatch(Character::isDigit));
        box.setFormatter((text, offset) ->
                FormattedCharSequence.forward("•".repeat(text.length()), Style.EMPTY));
        box.setResponder(t -> {
            gripe = "";
            if (ClientTabletLock.state() == ClientTabletLock.DENIED) {
                ClientTabletLock.reset();
            }
        });
        return box;
    }

    private void submit() {
        String code = pin.getValue();
        if (!BaseTabletItem.isPasscode(code)) {
            gripe = "A passcode is four digits.";
            return;
        }
        if (setting) {
            if (!code.equals(again.getValue())) {
                gripe = "They don't match — type it twice.";
                return;
            }
            ClientTabletLock.asked();
            PacketDistributor.sendToServer(new TabletLockPayload.SetLock(code));
        } else {
            ClientTabletLock.asked();
            PacketDistributor.sendToServer(new TabletLockPayload.Unlock(code));
        }
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == 257 || key == 335) {
            submit();
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float delta) {
        if (ClientTabletLock.consumeGranted()) {
            // Proven — on to the tablet proper, which asks for the board itself.
            ScreenOpener.TABLET_MAIN.run();
            return;
        }
        super.render(gfx, mouseX, mouseY, delta);
        int x = left + 12;

        if (setting) {
            gfx.drawString(font, "SET A PASSCODE", x, top + HEAD_Y, DIM, false);
            gfx.drawString(font, "Anyone holding this tablet can read", x, top + BODY_Y, GRAY, false);
            gfx.drawString(font, "your whole base. Keep it yours.", x, top + BODY_Y + 11, GRAY, false);
            gfx.drawString(font, "PIN", x, top + BODY_Y + 30, DIM, false);
            gfx.drawString(font, "AGAIN", x, top + BODY_Y + 52, DIM, false);
        } else {
            gfx.drawString(font, "● LOCKED", x, top + HEAD_Y, AMBER, false);
            gfx.drawString(font, "Enter the passcode to open.", x, top + BODY_Y, GRAY, false);
            gfx.drawString(font, "PIN", x, top + BODY_Y + 26, DIM, false);
        }

        int lineY = top + panelH - 46;
        if (!gripe.isEmpty()) {
            gfx.drawString(font, gripe, x, lineY, RED, false);
        } else if (ClientTabletLock.state() == ClientTabletLock.DENIED) {
            gfx.drawString(font, "Wrong passcode.", x, lineY, RED, false);
        } else if (ClientTabletLock.state() == ClientTabletLock.WAITING) {
            gfx.drawString(font, "Asking…", x, lineY, GRAY, false);
        }
    }
}
