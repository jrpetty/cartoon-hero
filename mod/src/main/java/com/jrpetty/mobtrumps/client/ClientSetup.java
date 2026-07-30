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
        event.enqueueWork(ClientPrefs::load);
        event.enqueueWork(() -> ItemProperties.register(
                ModItems.MOB_CARD.get(),
                ResourceLocation.fromNamespaceAndPath(MobTrumps.MODID, "mob_index"),
                (stack, level, entity, seed) -> {
                    var card = MobCardItem.cardOf(stack);
                    return card == null ? -1f : MobCards.ordinal(card.id());
                }));
        // how battered the card looks in your hand: 0 Mint through 5 Ruined,
        // selecting a model that stacks the matching wear overlay on the art
        event.enqueueWork(() -> ItemProperties.register(
                ModItems.MOB_CARD.get(),
                ResourceLocation.fromNamespaceAndPath(MobTrumps.MODID, "wear"),
                (stack, level, entity, seed) -> com.jrpetty.mobtrumps.game.CardCondition.stage(
                        com.jrpetty.mobtrumps.CardIdentityService.wearOf(stack).condition())));
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlocks.HOLO_PROJECTOR_BE.get(), HoloProjectorRenderer::new);
    }

    /** Announce the server-facing settings as soon as we're in a world. */
    @EventBusSubscriber(modid = MobTrumps.MODID, value = Dist.CLIENT)
    public static final class Game {

        private Game() {
        }

        @SubscribeEvent
        public static void onLoggedIn(net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingIn event) {
            ClientPrefs.load();
            ClientPrefs.syncToServer();
        }
    }

    @SubscribeEvent
    public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(
                ResourceLocation.fromNamespaceAndPath(MobTrumps.MODID, "card_scanner"),
                ScannerClient::renderHud);
    }
}
