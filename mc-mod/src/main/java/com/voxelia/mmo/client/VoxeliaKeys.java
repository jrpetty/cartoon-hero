package com.voxelia.mmo.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.voxelia.mmo.VoxeliaMMO;
import com.voxelia.mmo.network.AbilityPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/** Keybinds: open the Skills menu (K), use the selected ability (R), cycle it (G). */
public final class VoxeliaKeys {
    private VoxeliaKeys() {}

    private static final String CATEGORY = "key.categories.voxelia_mmo";

    public static final KeyMapping OPEN_MENU = new KeyMapping(
        "key.voxelia_mmo.menu", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, CATEGORY);
    public static final KeyMapping USE_ABILITY = new KeyMapping(
        "key.voxelia_mmo.ability", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, CATEGORY);
    public static final KeyMapping CYCLE_ABILITY = new KeyMapping(
        "key.voxelia_mmo.cycle", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, CATEGORY);

    @EventBusSubscriber(modid = VoxeliaMMO.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class ModBus {
        private ModBus() {}

        @SubscribeEvent
        public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
            event.register(OPEN_MENU);
            event.register(USE_ABILITY);
            event.register(CYCLE_ABILITY);
        }
    }

    @EventBusSubscriber(modid = VoxeliaMMO.MOD_ID, value = Dist.CLIENT)
    public static final class GameBus {
        private GameBus() {}

        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            ClientAbilities.clientTick();
            Player player = Minecraft.getInstance().player;
            if (player == null) return;

            if (Minecraft.getInstance().screen == null) {
                while (OPEN_MENU.consumeClick()) Minecraft.getInstance().setScreen(new SkillsScreen());
            }
            while (CYCLE_ABILITY.consumeClick()) {
                ClientAbilities.cycle(1);
                player.displayClientMessage(Component.literal("Ability: " + ClientAbilities.selectedSkill().abilityName())
                    .withStyle(ChatFormatting.AQUA), true);
            }
            while (USE_ABILITY.consumeClick()) {
                PacketDistributor.sendToServer(new AbilityPacket(ClientAbilities.selected()));
            }
        }
    }
}
