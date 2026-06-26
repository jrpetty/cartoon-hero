package com.voxelia.mmo.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.voxelia.mmo.VoxeliaMMO;
import com.voxelia.mmo.network.AbilityPacket;
import com.voxelia.mmo.progression.Abilities;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/** Keybinds: open the Skills menu (K), and the two active abilities (R / V). */
public final class VoxeliaKeys {
    private VoxeliaKeys() {}

    private static final String CATEGORY = "key.categories.voxelia_mmo";

    public static final KeyMapping OPEN_MENU = new KeyMapping(
        "key.voxelia_mmo.menu", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, CATEGORY);
    public static final KeyMapping FRENZY = new KeyMapping(
        "key.voxelia_mmo.frenzy", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, CATEGORY);
    public static final KeyMapping LEAP = new KeyMapping(
        "key.voxelia_mmo.leap", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, CATEGORY);

    @EventBusSubscriber(modid = VoxeliaMMO.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class ModBus {
        private ModBus() {}

        @SubscribeEvent
        public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
            event.register(OPEN_MENU);
            event.register(FRENZY);
            event.register(LEAP);
        }
    }

    @EventBusSubscriber(modid = VoxeliaMMO.MOD_ID, value = Dist.CLIENT)
    public static final class GameBus {
        private GameBus() {}

        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;

            if (mc.screen == null) {
                while (OPEN_MENU.consumeClick()) mc.setScreen(new SkillsScreen());
            }
            while (FRENZY.consumeClick()) PacketDistributor.sendToServer(new AbilityPacket(Abilities.FRENZY));
            while (LEAP.consumeClick()) PacketDistributor.sendToServer(new AbilityPacket(Abilities.LEAP));
        }
    }
}
