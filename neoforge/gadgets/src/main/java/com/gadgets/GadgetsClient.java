package com.gadgets;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * Client-side setup: registers the Rope Arrow projectile renderer.
 */
@EventBusSubscriber(modid = Gadgets.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class GadgetsClient {
    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(Gadgets.ROPE_ARROW_ENTITY.get(), RopeArrowRenderer::new);
        event.registerEntityRenderer(Gadgets.TORCH_ARROW_ENTITY.get(), TorchArrowRenderer::new);
        event.registerBlockEntityRenderer(Gadgets.DISPLAY_PEDESTAL_BE.get(), DisplayPedestalRenderer::new);
        event.registerBlockEntityRenderer(Gadgets.DRAIN_BE.get(), DrainRenderer::new);
        event.registerBlockEntityRenderer(Gadgets.ITEM_COUNTER_BE.get(), ItemCounterRenderer::new);
        event.registerBlockEntityRenderer(Gadgets.STOCK_MONITOR_BE.get(), StockMonitorRenderer::new);
        event.registerBlockEntityRenderer(Gadgets.TRASH_CAN_BE.get(), TrashCanRenderer::new);
        event.registerBlockEntityRenderer(Gadgets.COMMAND_HUB_BE.get(), CommandHubRenderer::new);
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        ScreenOpener.HUB = be -> Minecraft.getInstance().setScreen(new HubScreen((CommandHubBlockEntity) be));
        ScreenOpener.SENSOR = be -> Minecraft.getInstance().setScreen(new SensorScreen((PlayerSensorBlockEntity) be));
        ScreenOpener.COUNTER = be -> Minecraft.getInstance().setScreen(new CounterScreen((ItemCounterBlockEntity) be));
        ScreenOpener.MONITOR = be -> Minecraft.getInstance().setScreen(new MonitorScreen((StockMonitorBlockEntity) be));
    }
}
