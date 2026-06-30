package com.succession;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
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
        NeoForge.EVENT_BUS.addListener(this::onBlockBreak);
    }

    private void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) {
            engine.onWorldTick(level);
        }
    }

    private void onBlockBreak(BlockEvent.BreakEvent event) {
        // BreakEvent fires before the block is removed; defer a tick so the
        // broken log's spot is clear when we replant under the trunk.
        if (event.getLevel() instanceof ServerLevel level) {
            BlockPos pos = event.getPos().immutable();
            BlockState state = event.getState();
            level.getServer().execute(() -> TreeReplanter.onLogBroken(level, pos, state));
        }
    }
}
