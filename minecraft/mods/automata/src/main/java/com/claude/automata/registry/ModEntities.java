package com.claude.automata.registry;

import com.claude.automata.Automata;
import com.claude.automata.entity.CourierDroneEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

/**
 * Custom entities. The Courier Drone is the flying item-ferry dispatched by the
 * Drone Bay.
 */
public final class ModEntities {
	private ModEntities() {
	}

	private static final RegistryKey<EntityType<?>> COURIER_DRONE_KEY =
			RegistryKey.of(RegistryKeys.ENTITY_TYPE, Identifier.of(Automata.MOD_ID, "courier_drone"));

	public static final EntityType<CourierDroneEntity> COURIER_DRONE = Registry.register(
			Registries.ENTITY_TYPE, COURIER_DRONE_KEY,
			EntityType.Builder.<CourierDroneEntity>create(CourierDroneEntity::new, SpawnGroup.MISC)
					.dimensions(0.6f, 0.6f)
					.build(COURIER_DRONE_KEY));

	public static void register() {
		Automata.LOGGER.info("[Automata] Registering entities.");
	}
}
