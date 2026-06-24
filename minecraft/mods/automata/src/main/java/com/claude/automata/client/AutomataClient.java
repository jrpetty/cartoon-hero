package com.claude.automata.client;

import com.claude.automata.registry.ModEntities;
import com.claude.automata.screen.ModScreenHandlers;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.gui.screen.ingame.HandledScreens;

/**
 * Client entrypoint — registers the machine screen with its handler type and
 * the Courier Drone renderer.
 */
@Environment(EnvType.CLIENT)
public class AutomataClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		HandledScreens.register(ModScreenHandlers.MACHINE, MachineScreen::new);
		EntityRendererRegistry.register(ModEntities.COURIER_DRONE, CourierDroneRenderer::new);
	}
}
