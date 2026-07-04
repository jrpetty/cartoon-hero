package com.voxelia.mmo.registry;

import com.voxelia.mmo.VoxeliaMMO;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;

/**
 * Adds vanilla attributes to the player that aren't on the default supplier, so
 * our talents can modify them. OXYGEN_BONUS (used by the Fishing "Gills" talent)
 * exists in 1.21.1 but is not part of Player.createAttributes(), so we attach it
 * here — otherwise player.getAttribute(OXYGEN_BONUS) is null and the modifier is
 * silently dropped.
 */
@EventBusSubscriber(modid = VoxeliaMMO.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class VoxeliaEntityAttributes {
    private VoxeliaEntityAttributes() {}

    @SubscribeEvent
    public static void onModifyAttributes(EntityAttributeModificationEvent event) {
        if (!event.has(EntityType.PLAYER, Attributes.OXYGEN_BONUS)) {
            event.add(EntityType.PLAYER, Attributes.OXYGEN_BONUS);
        }
    }
}
