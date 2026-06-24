package com.claude.automata.registry;

import com.claude.automata.Automata;
import com.mojang.serialization.Codec;
import net.minecraft.component.DataComponentType;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

/**
 * Custom data components. {@link #ENERGY} stores charge on an item stack (the
 * Portable Charger and the powered tools), so those tools run on energy rather
 * than wearing out.
 */
public final class ModComponents {
	private ModComponents() {
	}

	public static final DataComponentType<Integer> ENERGY = Registry.register(
			Registries.DATA_COMPONENT_TYPE,
			Identifier.of(Automata.MOD_ID, "energy"),
			DataComponentType.<Integer>builder().codec(Codec.INT).packetCodec(PacketCodecs.INTEGER).build());

	public static void register() {
		Automata.LOGGER.info("[Automata] Registering data components.");
	}
}
