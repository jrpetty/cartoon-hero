package com.claude.automata.registry;

import com.claude.automata.Automata;
import net.minecraft.item.Item;
import net.minecraft.item.MiningToolItem;
import net.minecraft.item.ToolMaterials;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

/**
 * Items added by Automata.
 */
public final class ModItems {
	private ModItems() {
	}

	/** Blocks the Pulsar Multi-Tool can break at full speed (pickaxe + axe + shovel + hoe targets). */
	public static final TagKey<net.minecraft.block.Block> MULTITOOL_MINEABLE =
			TagKey.of(RegistryKeys.BLOCK, Identifier.of(Automata.MOD_ID, "multitool_mineable"));

	// Crafting components produced by the Fabricator.
	public static final Item IRON_GEAR = register("iron_gear", new Item(new Item.Settings()));
	public static final Item MACHINE_FRAME = register("machine_frame", new Item(new Item.Settings()));

	// Byproduct emitted by the Combustion Dynamo while it burns fuel.
	public static final Item ASH = register("ash", new Item(new Item.Settings()));

	// Crushed ore dust from the Crusher — smelt in a Forge Core for ore doubling.
	public static final Item IRON_DUST = register("iron_dust", new Item(new Item.Settings()));
	public static final Item GOLD_DUST = register("gold_dust", new Item(new Item.Settings()));
	public static final Item COPPER_DUST = register("copper_dust", new Item(new Item.Settings()));

	// Used to link Logistics Routers to destination inventories.
	public static final Item LOGISTICS_WRENCH = register("logistics_wrench",
			new Item(new Item.Settings().maxCount(1)));

	// Upgrade modules — right-click a machine to install (up to 3 of each).
	public static final Item SPEED_UPGRADE = register("speed_upgrade", new Item(new Item.Settings()));
	public static final Item EFFICIENCY_UPGRADE = register("efficiency_upgrade", new Item(new Item.Settings()));

	// Advanced component that gates higher-tier machines, made in the Circuit Assembler.
	public static final Item LOGIC_CIRCUIT = register("logic_circuit", new Item(new Item.Settings()));

	/**
	 * The Pulsar Multi-Tool — one tool that mines like a pickaxe, axe, shovel and
	 * hoe at the same time. A deliberate simplification of vanilla's tool zoo.
	 */
	public static final Item PULSAR_MULTITOOL = register("pulsar_multitool",
			new MiningToolItem(ToolMaterials.IRON, MULTITOOL_MINEABLE, 2.0f, -2.4f,
					new Item.Settings().maxDamage(750)));

	/**
	 * A locked component with no recipe and no creative-tab entry. It exists only
	 * so the disabled vanilla recipes (crafting table, furnace) can reference an
	 * unobtainable ingredient — see {@code data/minecraft/recipe}.
	 */
	public static final Item LOCKED_COMPONENT = register("locked_component", new Item(new Item.Settings()));

	private static Item register(String name, Item item) {
		return Registry.register(Registries.ITEM, Identifier.of(Automata.MOD_ID, name), item);
	}

	public static void register() {
		Automata.LOGGER.info("[Automata] Registering items.");
	}
}
