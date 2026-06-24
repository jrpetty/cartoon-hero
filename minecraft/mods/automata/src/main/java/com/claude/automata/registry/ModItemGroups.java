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
			entries.add(ModBlocks.FORGE_CORE);
			entries.add(ModBlocks.GENERATOR);
			entries.add(ModBlocks.CRUSHER);
			entries.add(ModBlocks.SAWMILL);
			entries.add(ModItems.IRON_GEAR);
			entries.add(ModItems.MACHINE_FRAME);
			entries.add(ModItems.ASH);
			entries.add(ModItems.IRON_DUST);
			entries.add(ModItems.GOLD_DUST);
			entries.add(ModItems.COPPER_DUST);
			entries.add(ModItems.PULSAR_MULTITOOL);
		});
	}
}
