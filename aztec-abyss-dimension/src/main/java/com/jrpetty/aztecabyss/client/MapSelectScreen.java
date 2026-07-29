package com.jrpetty.aztecabyss.client;

import com.jrpetty.aztecabyss.network.MapSelectPayload;
import com.jrpetty.aztecabyss.worldgen.ArenaMap;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The portal's map picker: right-click a lit Abyss portal in the overworld and
 * this opens, listing every arena with a short blurb. Picking one records the
 * choice server-side; stepping through the portal then drops you into that
 * arena. Rewards and scoring are identical whichever you choose.
 */
public final class MapSelectScreen extends Screen {

    private int selected;

    public MapSelectScreen(int currentChoice) {
        super(Component.literal("Choose Your Hunt"));
        this.selected = currentChoice;
    }

    @Override
    protected void init() {
        ArenaMap[] maps = ArenaMap.values();
        int cardW = 220;
        int cardH = 46;
        int top = this.height / 2 - (maps.length * (cardH + 8)) / 2 + 6;

        for (int i = 0; i < maps.length; i++) {
            final int id = i;
            int y = top + i * (cardH + 8);
            addRenderableWidget(Button.builder(Component.literal(maps[i].title()), b -> choose(id))
                    .bounds(this.width / 2 - cardW / 2, y, cardW, 20)
                    .build());
        }

        addRenderableWidget(Button.builder(Component.literal("Confirm"), b -> {
                    PacketDistributor.sendToServer(new MapSelectPayload(selected));
                    onClose();
                })
                .bounds(this.width / 2 - 60, this.height - 40, 120, 20)
                .build());
    }

    private void choose(int id) {
        this.selected = id;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g, mouseX, mouseY, partialTick);
        int cx = this.width / 2;

        g.drawCenteredString(this.font, Component.literal("CHOOSE YOUR HUNT").withStyle(s -> s.withBold(true)),
                cx, 28, 0xFFFFD24A);
        g.drawCenteredString(this.font, Component.literal("Same rounds, same rewards — different battlefield."),
                cx, 42, 0xFF888888);

        ArenaMap[] maps = ArenaMap.values();
        int cardH = 46;
        int top = this.height / 2 - (maps.length * (cardH + 8)) / 2 + 6;
        for (int i = 0; i < maps.length; i++) {
            int y = top + i * (cardH + 8);
            boolean isSelected = i == selected;
            // Blurb under each button, highlighted when picked.
            g.drawCenteredString(this.font, Component.literal(maps[i].blurb()),
                    cx, y + 24, isSelected ? 0xFFDDDDDD : 0xFF777777);
            if (isSelected) {
                g.fill(cx - 116, y - 2, cx - 112, y + 22, 0xFFFFD24A);
                g.fill(cx + 112, y - 2, cx + 116, y + 22, 0xFFFFD24A);
            }
        }

        g.drawCenteredString(this.font,
                Component.literal("Selected: " + maps[Math.max(0, Math.min(selected, maps.length - 1))].title()),
                cx, this.height - 56, 0xFF6EC8FF);

        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
