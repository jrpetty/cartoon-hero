package com.claude.automata.registry;

import com.claude.automata.Automata;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Items added by Automata.
 */
public final class ModItems {
	private ModItems() {
	}

	public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Automata.MOD_ID);

	/** Blocks the Pulsar Multi-Tool can break at full speed (pickaxe + axe + shovel + hoe targets). */
	public static final TagKey<Block> MULTITOOL_MINEABLE =
			TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(Automata.MOD_ID, "multitool_mineable"));

	// Crafting components produced by the Fabricator.
	public static final DeferredItem<Item> IRON_GEAR = register("iron_gear", () -> new Item(new Item.Properties()));
	public static final DeferredItem<Item> MACHINE_FRAME = register("machine_frame", () -> new Item(new Item.Properties()));

	// Byproduct emitted by the Combustion Dynamo while it burns fuel.
	public static final DeferredItem<Item> ASH = register("ash", () -> new Item(new Item.Properties()));

	// Crushed ore dust from the Crusher — smelt in a Forge Core for ore doubling.
	public static final DeferredItem<Item> IRON_DUST = register("iron_dust", () -> new Item(new Item.Properties()));
	public static final DeferredItem<Item> GOLD_DUST = register("gold_dust", () -> new Item(new Item.Properties()));
	public static final DeferredItem<Item> COPPER_DUST = register("copper_dust", () -> new Item(new Item.Properties()));

	// Used to link Logistics Routers to destination inventories.
	public static final DeferredItem<Item> LOGISTICS_WRENCH = register("logistics_wrench",
			() -> new Item(new Item.Properties().stacksTo(1)));

	// Upgrade modules — right-click a machine to install (up to 3 of each).
	public static final DeferredItem<Item> SPEED_UPGRADE = register("speed_upgrade", () -> new Item(new Item.Properties()));
	public static final DeferredItem<Item> EFFICIENCY_UPGRADE = register("efficiency_upgrade", () -> new Item(new Item.Properties()));

	// Advanced component that gates higher-tier machines, made in the Circuit Assembler.
	public static final DeferredItem<Item> LOGIC_CIRCUIT = register("logic_circuit", () -> new Item(new Item.Properties()));

	// Bottled experience from the XP Collector; right-click to cash in.
	public static final DeferredItem<com.claude.automata.item.XpShardItem> XP_SHARD = register("xp_shard",
			() -> new com.claude.automata.item.XpShardItem(new Item.Properties()));

	// A carried battery: charge from power blocks, auto-feeds powered tools.
	public static final DeferredItem<com.claude.automata.item.PortableChargerItem> PORTABLE_CHARGER = register("portable_charger",
			() -> new com.claude.automata.item.PortableChargerItem(200000, new Item.Properties().stacksTo(1)));

	// A drill that runs on charge instead of durability (mines the multitool blocks).
	public static final DeferredItem<com.claude.automata.item.PoweredToolItem> POWERED_DRILL = register("powered_drill",
			() -> new com.claude.automata.item.PoweredToolItem(Tiers.IRON, MULTITOOL_MINEABLE, 3.0f, -2.6f,
					20000, 20, new Item.Properties().stacksTo(1)));

	// Drone Bay batteries — set the drone's range. Install by right-clicking the bay.
	public static final DeferredItem<Item> DRONE_BATTERY = register("drone_battery", () -> new Item(new Item.Properties().stacksTo(1)));
	public static final DeferredItem<Item> DRONE_BATTERY_ADVANCED =
			register("drone_battery_advanced", () -> new Item(new Item.Properties().stacksTo(1)));
	public static final DeferredItem<Item> DRONE_BATTERY_ELITE =
			register("drone_battery_elite", () -> new Item(new Item.Properties().stacksTo(1)));

	/**
	 * The Pulsar Multi-Tool — one tool that mines like a pickaxe, axe, shovel and
	 * hoe at the same time. A deliberate simplification of vanilla's tool zoo.
	 */
	public static final DeferredItem<DiggerItem> PULSAR_MULTITOOL = register("pulsar_multitool",
			() -> new DiggerItem(Tiers.IRON, MULTITOOL_MINEABLE,
					new Item.Properties().durability(750).attributes(
							DiggerItem.createAttributes(Tiers.IRON, 2.0f, -2.4f))));

	/**
	 * A locked component with no recipe and no creative-tab entry. It exists only
	 * so the disabled vanilla recipes (crafting table, furnace) can reference an
	 * unobtainable ingredient — see {@code data/minecraft/recipe}.
	 */
	public static final DeferredItem<Item> LOCKED_COMPONENT = register("locked_component", () -> new Item(new Item.Properties()));

	private static <T extends Item> DeferredItem<T> register(String name, java.util.function.Supplier<T> item) {
		return ITEMS.register(name, item);
	}

	public static void register(IEventBus modEventBus) {
		Automata.LOGGER.info("[Automata] Registering items.");
		ITEMS.register(modEventBus);
	}
}
