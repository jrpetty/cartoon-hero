package com.claude.automata.registry;

import com.claude.automata.Automata;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * The creative-inventory tab that gathers Automata's machines, components and
 * the Pulsar Multi-Tool.
 */
public final class ModItemGroups {
	private ModItemGroups() {
	}

	public static final RegistryKey<ItemGroup> AUTOMATA_GROUP_KEY =
			RegistryKey.of(RegistryKeys.ITEM_GROUP, Identifier.of(Automata.MOD_ID, "automata"));

	public static void register() {
		Automata.LOGGER.info("[Automata] Registering item group.");

		Registry.register(Registries.ITEM_GROUP, AUTOMATA_GROUP_KEY, FabricItemGroup.builder()
				.icon(() -> new ItemStack(ModBlocks.FABRICATOR))
				.displayName(Text.translatable("itemGroup.automata.automata"))
				.build());

		ItemGroupEvents.modifyEntriesEvent(AUTOMATA_GROUP_KEY).register(entries -> {
			entries.add(ModBlocks.FABRICATOR);
			entries.add(ModBlocks.CIRCUIT_ASSEMBLER);
			entries.add(ModItems.LOGIC_CIRCUIT);
			entries.add(ModBlocks.FORGE_CORE);
			entries.add(ModBlocks.GENERATOR);
			entries.add(ModBlocks.THERMAL_GENERATOR);
			entries.add(ModBlocks.SOLAR_ARRAY);
			entries.add(ModBlocks.CAPACITOR);
			entries.add(ModBlocks.CONDUIT);
			entries.add(ModBlocks.PYLON);
			entries.add(ModBlocks.CRUSHER);
			entries.add(ModBlocks.SAWMILL);
			entries.add(ModBlocks.RECYCLER);
			entries.add(ModBlocks.MINER);
			entries.add(ModBlocks.COLLECTOR);
			entries.add(ModBlocks.BLOCK_BREAKER);
			entries.add(ModBlocks.BLOCK_PLACER);
			entries.add(ModBlocks.FLUID_PUMP);
			entries.add(ModBlocks.CONVEYOR);
			entries.add(ModBlocks.DRAWER);
			entries.add(ModBlocks.ROUTER);
			entries.add(ModBlocks.SORTER);
			entries.add(ModBlocks.VOID);
			entries.add(ModBlocks.SENTRY);
			entries.add(ModBlocks.RANCHER);
			entries.add(ModBlocks.HARVESTER);
			entries.add(ModBlocks.TREE_FARM);
			entries.add(ModBlocks.XP_COLLECTOR);
			entries.add(ModItems.XP_SHARD);
			entries.add(ModItems.LOGISTICS_WRENCH);
			entries.add(ModItems.SPEED_UPGRADE);
			entries.add(ModItems.EFFICIENCY_UPGRADE);
			entries.add(ModItems.IRON_GEAR);
			entries.add(ModItems.MACHINE_FRAME);
			entries.add(ModItems.ASH);
			entries.add(ModItems.IRON_DUST);
			entries.add(ModItems.GOLD_DUST);
			entries.add(ModItems.COPPER_DUST);
			entries.add(ModItems.PULSAR_MULTITOOL);
			entries.add(ModItems.POWERED_DRILL);
			entries.add(ModItems.PORTABLE_CHARGER);
		});
	}
}
