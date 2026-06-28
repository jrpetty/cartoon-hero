package com.voxelia.mmo;

import com.mojang.logging.LogUtils;
import com.voxelia.mmo.config.VoxeliaClientConfig;
import com.voxelia.mmo.config.VoxeliaConfig;
import com.voxelia.mmo.registry.VoxeliaAttachments;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

/**
 * Voxelia MMO — adds an MMO progression layer to Minecraft: per-skill XP and
 * levels, level-up stat scaling, a skills HUD, a quest/advancement chain, and
 * commands. Server-authoritative; the client only renders synced skill data.
 */
@Mod(VoxeliaMMO.MOD_ID)
public final class VoxeliaMMO {
    public static final String MOD_ID = "voxelia_mmo";
    public static final Logger LOGGER = LogUtils.getLogger();

    // NeoForge (1.21+) injects the mod event bus and container into the ctor.
    public VoxeliaMMO(IEventBus modBus, ModContainer container) {
        VoxeliaAttachments.ATTACHMENTS.register(modBus);
        container.registerConfig(ModConfig.Type.COMMON, VoxeliaConfig.SPEC);
        container.registerConfig(ModConfig.Type.CLIENT, VoxeliaClientConfig.SPEC);
        // In-game config screen (client only — class is loaded lazily here).
        if (FMLEnvironment.dist == Dist.CLIENT) {
            com.voxelia.mmo.client.VoxeliaConfigScreen.register(container);
        }
        LOGGER.info("[Voxelia MMO] initialised");
    }
}
