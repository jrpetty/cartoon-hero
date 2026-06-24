package com.claude.automata.registry;

import com.claude.automata.Automata;
import com.mojang.serialization.Codec;
import java.util.function.Supplier;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Custom data components. {@link #ENERGY} stores charge on an item stack (the
 * Portable Charger and the powered tools), so those tools run on energy rather
 * than wearing out.
 */
public final class ModComponents {
	private ModComponents() {
	}

	public static final DeferredRegister.DataComponents COMPONENTS =
			DeferredRegister.createDataComponents(Automata.MOD_ID);

	public static final Supplier<DataComponentType<Integer>> ENERGY = COMPONENTS.register(
			"energy",
			() -> DataComponentType.<Integer>builder()
					.persistent(Codec.INT)
					.networkSynchronized(ByteBufCodecs.VAR_INT)
					.build());

	public static void register(IEventBus modEventBus) {
		Automata.LOGGER.info("[Automata] Registering data components.");
		COMPONENTS.register(modEventBus);
	}
}
