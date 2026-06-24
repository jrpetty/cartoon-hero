package com.claude.automata.registry;

import com.claude.automata.Automata;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The creative-inventory tab that gathers Automata's machines, components and
 * the Pulsar Multi-Tool.
 */
public final class ModItemGroups {
	private ModItemGroups() {
	}

	public static final DeferredRegister<CreativeModeTab> TABS =
			DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Automata.MOD_ID);

	public static final Supplier<CreativeModeTab> AUTOMATA_GROUP = TABS.register("automata",
			() -> CreativeModeTab.builder()
					.title(Component.translatable("itemGroup.automata.automata"))
					.icon(() -> new ItemStack(ModBlocks.FABRICATOR.get()))
					.displayItems((params, output) -> {
						output.accept(ModBlocks.FABRICATOR.get());
						output.accept(ModBlocks.CIRCUIT_ASSEMBLER.get());
						output.accept(ModItems.LOGIC_CIRCUIT.get());
						output.accept(ModBlocks.FORGE_CORE.get());
						output.accept(ModBlocks.GENERATOR.get());
						output.accept(ModBlocks.THERMAL_GENERATOR.get());
						output.accept(ModBlocks.SOLAR_ARRAY.get());
						output.accept(ModBlocks.CAPACITOR.get());
						output.accept(ModBlocks.CONDUIT.get());
						output.accept(ModBlocks.PYLON.get());
						output.accept(ModBlocks.CRUSHER.get());
						output.accept(ModBlocks.SAWMILL.get());
						output.accept(ModBlocks.RECYCLER.get());
						output.accept(ModBlocks.MINER.get());
						output.accept(ModBlocks.COLLECTOR.get());
						output.accept(ModBlocks.BLOCK_BREAKER.get());
						output.accept(ModBlocks.BLOCK_PLACER.get());
						output.accept(ModBlocks.FLUID_PUMP.get());
						output.accept(ModBlocks.CONVEYOR.get());
						output.accept(ModBlocks.DRAWER.get());
						output.accept(ModBlocks.DRONE_BAY.get());
						output.accept(ModBlocks.ROUTER.get());
						output.accept(ModBlocks.SORTER.get());
						output.accept(ModBlocks.VOID.get());
						output.accept(ModBlocks.SENTRY.get());
						output.accept(ModBlocks.RANCHER.get());
						output.accept(ModBlocks.HARVESTER.get());
						output.accept(ModBlocks.TREE_FARM.get());
						output.accept(ModBlocks.XP_COLLECTOR.get());
						output.accept(ModItems.XP_SHARD.get());
						output.accept(ModItems.LOGISTICS_WRENCH.get());
						output.accept(ModItems.SPEED_UPGRADE.get());
						output.accept(ModItems.EFFICIENCY_UPGRADE.get());
						output.accept(ModItems.IRON_GEAR.get());
						output.accept(ModItems.MACHINE_FRAME.get());
						output.accept(ModItems.ASH.get());
						output.accept(ModItems.IRON_DUST.get());
						output.accept(ModItems.GOLD_DUST.get());
						output.accept(ModItems.COPPER_DUST.get());
						output.accept(ModItems.PULSAR_MULTITOOL.get());
						output.accept(ModItems.POWERED_DRILL.get());
						output.accept(ModItems.PORTABLE_CHARGER.get());
						output.accept(ModItems.DRONE_BATTERY.get());
						output.accept(ModItems.DRONE_BATTERY_ADVANCED.get());
						output.accept(ModItems.DRONE_BATTERY_ELITE.get());
					})
					.build());

	public static void register(IEventBus modEventBus) {
		Automata.LOGGER.info("[Automata] Registering item group.");
		TABS.register(modEventBus);
	}
}
