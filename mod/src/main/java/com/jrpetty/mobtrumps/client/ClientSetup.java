package com.jrpetty.mobtrumps.client;

import com.jrpetty.mobtrumps.MobCardItem;
import com.jrpetty.mobtrumps.MobTrumps;
import com.jrpetty.mobtrumps.ModItems;
import com.jrpetty.mobtrumps.game.MobCards;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

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
}
