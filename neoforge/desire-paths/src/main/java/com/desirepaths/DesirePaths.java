package com.desirepaths;

import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * NeoForge entrypoint for Desire Paths — the ground remembers where you walk.
 */
@Mod(DesirePaths.MODID)
public class DesirePaths {
    public static final String MODID = "desirepaths";

    private final PathWear tracker = new PathWear();

    public DesirePaths(IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener(this::onLevelTick);
    }

    private void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) {
            tracker.onWorldTick(level);
        }
    }
}
