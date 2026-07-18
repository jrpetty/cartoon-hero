package com.jrpetty.aztecabyss.client;

import com.jrpetty.aztecabyss.particle.BlackPortalSwirlParticle;
import com.jrpetty.aztecabyss.registry.ModParticles;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

@EventBusSubscriber(modid = com.jrpetty.aztecabyss.AztecAbyssConstants.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class ClientSetup {

    @SubscribeEvent
    public static void registerParticleProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.BLACK_PORTAL_SWIRL.get(), BlackPortalSwirlParticle.Provider::new);
    }
}
