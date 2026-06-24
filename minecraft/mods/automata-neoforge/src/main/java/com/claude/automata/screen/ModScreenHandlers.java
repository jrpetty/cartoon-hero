package com.claude.automata.screen;

import com.claude.automata.Automata;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Registers Automata's screen-handler types. The single-input/single-output
 * processing machines (Forge Core, Crusher, Sawmill, Recycler) share one
 * handler, opened with the machine's BlockPos as extended data.
 */
public final class ModScreenHandlers {
	private ModScreenHandlers() {
	}

	public static final DeferredRegister<MenuType<?>> MENUS =
			DeferredRegister.create(Registries.MENU, Automata.MOD_ID);

	public static final Supplier<MenuType<MachineScreenHandler>> MACHINE = MENUS.register(
			"machine",
			() -> IMenuTypeExtension.create(
					(windowId, inv, buf) -> new MachineScreenHandler(windowId, inv, buf)));

	public static void register(IEventBus modEventBus) {
		Automata.LOGGER.info("[Automata] Registering screen handlers.");
		MENUS.register(modEventBus);
	}
}
