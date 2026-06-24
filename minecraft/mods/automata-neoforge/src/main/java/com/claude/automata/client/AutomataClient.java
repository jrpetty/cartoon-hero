package com.claude.automata.client;

import com.claude.automata.Automata;
import com.claude.automata.registry.ModEntities;
import com.claude.automata.screen.ModScreenHandlers;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/**
 * Client entrypoint — registers the machine screen with its handler type and
 * the Courier Drone renderer.
 */
@EventBusSubscriber(modid = Automata.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public class AutomataClient {
	private AutomataClient() {
	}

	@SubscribeEvent
	public static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
		event.register(ModScreenHandlers.MACHINE.get(), MachineScreen::new);
	}

	@SubscribeEvent
	public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
		event.registerEntityRenderer(ModEntities.COURIER_DRONE.get(), CourierDroneRenderer::new);
	}
}
