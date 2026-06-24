package com.claude.automata.client;

import com.claude.automata.Automata;
import com.claude.automata.screen.MachineScreenHandler;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/**
 * The client screen for processing machines: a standard container background
 * with a progress arrow that fills as the machine works.
 */
public class MachineScreen extends AbstractContainerScreen<MachineScreenHandler> {
	private static final ResourceLocation TEXTURE =
			ResourceLocation.fromNamespaceAndPath(Automata.MOD_ID, "textures/gui/machine.png");

	public MachineScreen(MachineScreenHandler handler, Inventory inventory, Component title) {
		super(handler, inventory, title);
	}

	@Override
	protected void init() {
		super.init();
		// Centre the title over the GUI.
		titleLabelX = (imageWidth - font.width(title)) / 2;
	}

	@Override
	protected void renderBg(GuiGraphics context, float delta, int mouseX, int mouseY) {
		int x = (width - imageWidth) / 2;
		int y = (height - imageHeight) / 2;
		context.blit(TEXTURE, x, y, 0, 0, imageWidth, imageHeight);

		// Progress arrow, drawn from the strip stored to the right of the background.
		int progress = menu.getScaledProgress();
		if (progress > 0) {
			context.blit(TEXTURE, x + 79, y + 34, 176, 0, progress + 1, 17);
		}
	}

	@Override
	public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
		renderBackground(context, mouseX, mouseY, delta);
		super.render(context, mouseX, mouseY, delta);
		renderTooltip(context, mouseX, mouseY);
	}
}
