package com.claude.automata.client;

import com.claude.automata.Automata;
import com.claude.automata.screen.MachineScreenHandler;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * The client screen for processing machines: a standard container background
 * with a progress arrow that fills as the machine works.
 */
@Environment(EnvType.CLIENT)
public class MachineScreen extends HandledScreen<MachineScreenHandler> {
	private static final Identifier TEXTURE = Identifier.of(Automata.MOD_ID, "textures/gui/machine.png");

	public MachineScreen(MachineScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
	}

	@Override
	protected void init() {
		super.init();
		// Centre the title over the GUI.
		titleX = (backgroundWidth - textRenderer.getWidth(title)) / 2;
	}

	@Override
	protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
		int x = (width - backgroundWidth) / 2;
		int y = (height - backgroundHeight) / 2;
		context.drawTexture(TEXTURE, x, y, 0, 0, backgroundWidth, backgroundHeight);

		// Progress arrow, drawn from the strip stored to the right of the background.
		int progress = handler.getScaledProgress();
		if (progress > 0) {
			context.drawTexture(TEXTURE, x + 79, y + 34, 176, 0, progress + 1, 17);
		}
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		drawMouseoverTooltip(context, mouseX, mouseY);
	}
}
