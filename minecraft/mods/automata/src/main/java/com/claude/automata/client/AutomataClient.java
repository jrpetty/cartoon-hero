package com.claude.automata.client;

import com.claude.automata.screen.ModScreenHandlers;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screen.ingame.HandledScreens;

/**
 * Client entrypoint — registers the machine screen with its handler type.
 */
@Environment(EnvType.CLIENT)
public class AutomataClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		HandledScreens.register(ModScreenHandlers.MACHINE, MachineScreen::new);
	}
}
