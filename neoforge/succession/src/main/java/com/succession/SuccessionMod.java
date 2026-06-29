package com.succession;

import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * NeoForge entrypoint for Succession — the wild slowly reclaims cleared land.
 */
@Mod(SuccessionMod.MODID)
public class SuccessionMod {
    public static final String MODID = "succession";

    private final SuccessionEngine engine = new SuccessionEngine();

    public SuccessionMod(IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener(this::onLevelTick);
    }

    private void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) {
            engine.onWorldTick(level);
        }
    }
}
