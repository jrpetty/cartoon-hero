package com.voxelia.mmo.client;

import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/** Registers the in-game config screen (the "Config" button in the mod list). */
public final class VoxeliaConfigScreen {
    private VoxeliaConfigScreen() {}

    public static void register(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class,
            (mod, parent) -> new ConfigurationScreen(mod, parent));
    }
}
