package com.voxelia.mmo.client;

import com.voxelia.mmo.VoxeliaMMO;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/** Client-only mod-bus setup: registers the skills HUD above the hotbar. */
@EventBusSubscriber(modid = VoxeliaMMO.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class VoxeliaClient {
    private VoxeliaClient() {}

    @SubscribeEvent
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(
            VanillaGuiLayers.HOTBAR,
            ResourceLocation.fromNamespaceAndPath(VoxeliaMMO.MOD_ID, "skills_hud"),
            SkillHudOverlay.INSTANCE);
        event.registerAbove(
            VanillaGuiLayers.HOTBAR,
            ResourceLocation.fromNamespaceAndPath(VoxeliaMMO.MOD_ID, "skill_sidebar"),
            SkillSidebarOverlay.INSTANCE);
        event.registerAbove(
            VanillaGuiLayers.HOTBAR,
            ResourceLocation.fromNamespaceAndPath(VoxeliaMMO.MOD_ID, "milestone_toasts"),
            MilestoneToastOverlay.INSTANCE);
    }
}
