package com.voxelia.mmo.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.voxelia.mmo.VoxeliaMMO;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

/** The "open skills menu" keybind (default K) and its handler. */
public final class VoxeliaKeys {
    private VoxeliaKeys() {}

    public static final KeyMapping OPEN_MENU = new KeyMapping(
        "key.voxelia_mmo.menu", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, "key.categories.voxelia_mmo");

    @EventBusSubscriber(modid = VoxeliaMMO.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class ModBus {
        private ModBus() {}

        @SubscribeEvent
        public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
            event.register(OPEN_MENU);
        }
    }

    @EventBusSubscriber(modid = VoxeliaMMO.MOD_ID, value = Dist.CLIENT)
    public static final class GameBus {
        private GameBus() {}

        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.screen != null) return;
            while (OPEN_MENU.consumeClick()) {
                mc.setScreen(new SkillsScreen());
            }
        }
    }
}
