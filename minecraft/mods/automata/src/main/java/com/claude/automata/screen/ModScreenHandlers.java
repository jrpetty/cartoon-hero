package com.claude.automata.screen;

import com.claude.automata.Automata;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/**
 * Registers Automata's screen-handler types. The single-input/single-output
 * processing machines (Forge Core, Crusher, Sawmill, Recycler) share one
 * handler, opened with the machine's BlockPos as extended data.
 */
public final class ModScreenHandlers {
	private ModScreenHandlers() {
	}

	public static final ExtendedScreenHandlerType<MachineScreenHandler, BlockPos> MACHINE =
			Registry.register(Registries.SCREEN_HANDLER, Identifier.of(Automata.MOD_ID, "machine"),
					new ExtendedScreenHandlerType<>(MachineScreenHandler::new, BlockPos.PACKET_CODEC));

	public static void register() {
		Automata.LOGGER.info("[Automata] Registering screen handlers.");
	}
}
