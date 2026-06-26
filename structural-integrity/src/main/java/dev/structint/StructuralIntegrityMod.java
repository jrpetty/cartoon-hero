package dev.structint;

import dev.structint.registry.ModBlocks;
import dev.structint.registry.ModContent;
import dev.structint.world.ModAttachments;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

/**
 * Entry point for the Structural Integrity mod.
 *
 * <p>A lightweight, deterministic <em>gameplay rule</em> for building: player-placed blocks
 * must trace a valid load path back to natural ground or a foundation within their material's
 * span, or they fall. It is explicitly not a physics engine — see the README for the design.
 */
@Mod(StructuralIntegrityMod.MODID)
public final class StructuralIntegrityMod {

    public static final String MODID = "structint";
    public static final Logger LOGGER = LogUtils.getLogger();

    public StructuralIntegrityMod(IEventBus modBus, ModContainer container) {
        ModBlocks.BLOCKS.register(modBus);
        ModContent.ITEMS.register(modBus);
        ModContent.CREATIVE_TABS.register(modBus);
        ModAttachments.ATTACHMENT_TYPES.register(modBus);

        container.registerConfig(ModConfig.Type.SERVER, Config.SPEC);

        LOGGER.info("Structural Integrity loaded — build with supports!");
        // Gameplay events are wired up via @EventBusSubscriber on StructuralEventHandler.
    }
}
