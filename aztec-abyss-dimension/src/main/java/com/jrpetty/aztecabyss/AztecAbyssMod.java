package com.jrpetty.aztecabyss;

import com.jrpetty.aztecabyss.config.AbyssConfig;
import com.jrpetty.aztecabyss.event.AbyssEventHandler;
import com.jrpetty.aztecabyss.event.PortalEvents;
import com.jrpetty.aztecabyss.event.RitualHandler;
import com.jrpetty.aztecabyss.registry.ModAttachments;
import com.jrpetty.aztecabyss.registry.ModBlocks;
import com.jrpetty.aztecabyss.registry.ModItems;
import com.jrpetty.aztecabyss.registry.ModParticles;
import com.jrpetty.aztecabyss.registry.ModSounds;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;

@Mod(AztecAbyssConstants.MOD_ID)
public final class AztecAbyssMod {

    public AztecAbyssMod(IEventBus modEventBus, ModContainer modContainer) {
        ModBlocks.register(modEventBus);
        ModItems.register(modEventBus);
        ModParticles.register(modEventBus);
        ModSounds.register(modEventBus);
        ModAttachments.register(modEventBus);

        modContainer.registerConfig(ModConfig.Type.COMMON, AbyssConfig.SPEC);

        NeoForge.EVENT_BUS.register(new AbyssEventHandler());
        NeoForge.EVENT_BUS.register(new PortalEvents());
        NeoForge.EVENT_BUS.register(new RitualHandler());
        // Client-only handlers (ClientSetup, AbyssClientEffects, AbyssHudOverlay)
        // self-register via @EventBusSubscriber(Dist.CLIENT).
    }
}
