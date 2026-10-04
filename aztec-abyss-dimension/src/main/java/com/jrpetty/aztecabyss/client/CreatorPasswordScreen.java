package com.jrpetty.aztecabyss.client;

import com.jrpetty.aztecabyss.network.CreatorUnlockPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * The Map Creator's door, for anybody who is not an operator.
 *
 * <p>The password used to be typed as a chat command - {@code /creator
 * <word>} - in plain view of the chat log and of anybody looking over a
 * shoulder, and only by somebody who had been told the command existed. This
 * is a box with the word hidden as you type it, opened by the same tile on the
 * portal that the Creator always was.
 */
public final class CreatorPasswordScreen extends AbyssScreen {

    private final boolean failed;
    private EditBox word;
    private String draft = "";

    public CreatorPasswordScreen(boolean failed) {
        super(Component.literal("Map Creator"));
        this.failed = failed;
    }

    @Override
    protected int glow() {
        return UiKit.alpha(UiKit.CYAN, 0x22);
    }

    private int panelTop() {
        return Math.max(52, this.height / 2 - 60);
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int y = panelTop() + 58;
        word = new EditBox(this.font, cx - 100, y, 200, 20, Component.literal("Password"));
        word.setMaxLength(64);
        word.setValue(draft);
        word.setResponder(s -> draft = s);
        // Shown as dots: the whole point of a box over a command is that the
        // word is not written out for the room to read.
        word.setFormatter((text, cursor) -> FormattedCharSequence.forward("•".repeat(text.length()), Style.EMPTY));
        addRenderableWidget(word);
        setInitialFocus(word);

        addRenderableWidget(Button.builder(Component.literal("Unlock"), b -> submit())
                .bounds(cx - 100, y + 28, 96, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose())
                .bounds(cx + 4, y + 28, 96, 20).build());
    }

    private void submit() {
        if (draft.isEmpty()) {
            return;
        }
        PacketDistributor.sendToServer(new CreatorUnlockPayload(draft));
        onClose();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) {
            submit();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int cx = this.width / 2;
        int top = panelTop();
        int w = Math.min(300, this.width - 32);
        UiKit.masthead(g, this.font, "THE ABYSS PORTAL", "MAP CREATOR", cx, 8, UiKit.CYAN);
        UiKit.panel(g, cx - w / 2, top, w, 116);
        g.fill(cx - w / 2 + 1, top + 1, cx + w / 2 - 1, top + 3, UiKit.CYAN);
        g.drawCenteredString(this.font, Component.literal("LOCKED").withStyle(s -> s.withBold(true)),
                cx, top + 10, UiKit.TEXT);
        List<FormattedCharSequence> why = this.font.split(Component.literal(
                "The Creator hands out creative mode, so on a server it is locked. "
                        + "Whoever runs this server has the word."), w - 24);
        int y = top + 24;
        for (FormattedCharSequence line : why) {
            g.drawCenteredString(this.font, line, cx, y, UiKit.TEXT_DIM);
            y += 10;
        }
        if (failed) {
            g.drawCenteredString(this.font, "That is not the word.", cx, top + 124, UiKit.RED);
        }
    }
}
