package com.claude.automata.registry;

import com.claude.automata.Automata;
import com.claude.automata.entity.CourierDroneEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Custom entities. The Courier Drone is the flying item-ferry dispatched by the
 * Drone Bay.
 */
public final class ModEntities {
	private ModEntities() {
	}

	public static final DeferredRegister<EntityType<?>> ENTITIES =
			DeferredRegister.create(Registries.ENTITY_TYPE, Automata.MOD_ID);

	private static final ResourceKey<EntityType<?>> COURIER_DRONE_KEY =
			ResourceKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(Automata.MOD_ID, "courier_drone"));

	public static final java.util.function.Supplier<EntityType<CourierDroneEntity>> COURIER_DRONE = ENTITIES.register(
			"courier_drone",
			() -> EntityType.Builder.<CourierDroneEntity>of(CourierDroneEntity::new, MobCategory.MISC)
					.sized(0.6f, 0.6f)
					.build(COURIER_DRONE_KEY));

	public static void register(IEventBus modEventBus) {
		Automata.LOGGER.info("[Automata] Registering entities.");
		ENTITIES.register(modEventBus);
	}
}
