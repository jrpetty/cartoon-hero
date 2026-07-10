package com.jrpetty.mobtrumps;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

@Mod(MobTrumps.MODID)
public class MobTrumps {

    public static final String MODID = "mobtrumps";

    public MobTrumps(IEventBus modEventBus, ModContainer modContainer) {
        ModItems.ITEMS.register(modEventBus);
        ModItems.DATA_COMPONENTS.register(modEventBus);
        ModItems.CREATIVE_MODE_TABS.register(modEventBus);
        NeoForge.EVENT_BUS.addListener(BattleCommands::onRegisterCommands);
    }
}
