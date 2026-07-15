package com.jrpetty.mobtrumps.client;

import com.jrpetty.mobtrumps.MobCardItem;
import com.jrpetty.mobtrumps.MobTrumps;
import com.jrpetty.mobtrumps.ModBlocks;
import com.jrpetty.mobtrumps.ModItems;
import com.jrpetty.mobtrumps.game.MobCards;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/** Client-only registration: the mob_index item property that selects a
 *  card's per-mob inventory icon via the model overrides. */
@EventBusSubscriber(modid = MobTrumps.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class ClientSetup {

    private ClientSetup() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> ItemProperties.register(
                ModItems.MOB_CARD.get(),
                ResourceLocation.fromNamespaceAndPath(MobTrumps.MODID, "mob_index"),
                (stack, level, entity, seed) -> {
                    var card = MobCardItem.cardOf(stack);
                    return card == null ? -1f : MobCards.ordinal(card.id());
                }));
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlocks.CARD_DISPLAY_BE.get(), CardDisplayRenderer::new);
        event.registerBlockEntityRenderer(ModBlocks.HOLO_PROJECTOR_BE.get(), HoloProjectorRenderer::new);
    }

    @SubscribeEvent
    public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(
                ResourceLocation.fromNamespaceAndPath(MobTrumps.MODID, "card_scanner"),
                ScannerClient::renderHud);
    }
}
