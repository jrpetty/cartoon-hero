package com.claude.automata.registry;

import com.claude.automata.Automata;
import com.claude.automata.block.BlockBreakerBlock;
import com.claude.automata.block.BlockPlacerBlock;
import com.claude.automata.block.CapacitorBlock;
import com.claude.automata.block.CircuitAssemblerBlock;
import com.claude.automata.block.CollectorBlock;
import com.claude.automata.block.ConduitBlock;
import com.claude.automata.block.ConveyorBlock;
import com.claude.automata.block.CrusherBlock;
import com.claude.automata.block.DrawerBlock;
import com.claude.automata.block.DroneBayBlock;
import com.claude.automata.block.FabricatorBlock;
import com.claude.automata.block.FluidPumpBlock;
import com.claude.automata.block.ForgeCoreBlock;
import com.claude.automata.block.GeneratorBlock;
import com.claude.automata.block.HarvesterBlock;
import com.claude.automata.block.MinerBlock;
import com.claude.automata.block.PylonBlock;
import com.claude.automata.block.RecyclerBlock;
import com.claude.automata.block.RouterBlock;
import com.claude.automata.block.RancherBlock;
import com.claude.automata.block.SawmillBlock;
import com.claude.automata.block.SolarArrayBlock;
import com.claude.automata.block.SentryBlock;
import com.claude.automata.block.SorterBlock;
import com.claude.automata.block.ThermalGeneratorBlock;
import com.claude.automata.block.TreeFarmBlock;
import com.claude.automata.block.VoidBlock;
import com.claude.automata.block.XpCollectorBlock;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.MapColor;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;

/**
 * Blocks added by Automata: the two machines that replace the crafting table
 * and furnace.
 */
public final class ModBlocks {
	private ModBlocks() {
	}

	public static final Block CIRCUIT_ASSEMBLER = register("circuit_assembler",
			new CircuitAssemblerBlock(AbstractBlock.Settings.create()
					.mapColor(MapColor.LIME)
					.strength(2.5f, 6.0f)
					.requiresTool()
					.sounds(BlockSoundGroup.METAL)));

	public static final Block FABRICATOR = register("fabricator",
			new FabricatorBlock(AbstractBlock.Settings.create()
					.mapColor(MapColor.ORANGE)
					.strength(2.5f, 6.0f)
					.requiresTool()
					.sounds(BlockSoundGroup.METAL)));

	public static final Block FORGE_CORE = register("forge_core",
			new ForgeCoreBlock(AbstractBlock.Settings.create()
					.mapColor(MapColor.RED)
					.strength(3.5f, 6.0f)
					.requiresTool()
					.luminance(state -> 13)
					.sounds(BlockSoundGroup.STONE)));

	public static final Block GENERATOR = register("generator",
			new GeneratorBlock(AbstractBlock.Settings.create()
					.mapColor(MapColor.BROWN)
					.strength(3.5f, 6.0f)
					.requiresTool()
					.luminance(state -> 7)
					.sounds(BlockSoundGroup.METAL)));

	public static final Block CRUSHER = register("crusher",
			new CrusherBlock(AbstractBlock.Settings.create()
					.mapColor(MapColor.GRAY)
					.strength(3.0f, 6.0f)
					.requiresTool()
					.sounds(BlockSoundGroup.METAL)));

	public static final Block SAWMILL = register("sawmill",
			new SawmillBlock(AbstractBlock.Settings.create()
					.mapColor(MapColor.BROWN)
					.strength(3.0f, 6.0f)
					.requiresTool()
					.sounds(BlockSoundGroup.METAL)));

	public static final Block MINER = register("miner",
			new MinerBlock(AbstractBlock.Settings.create()
					.mapColor(MapColor.LIGHT_GRAY)
					.strength(4.0f, 6.0f)
					.requiresTool()
					.sounds(BlockSoundGroup.METAL)));

	public static final Block COLLECTOR = register("collector",
			new CollectorBlock(AbstractBlock.Settings.create()
					.mapColor(MapColor.LIGHT_BLUE)
					.strength(3.0f, 6.0f)
					.requiresTool()
					.sounds(BlockSoundGroup.METAL)));

	public static final Block ROUTER = register("router",
			new RouterBlock(AbstractBlock.Settings.create()
					.mapColor(MapColor.GREEN)
					.strength(3.0f, 6.0f)
					.requiresTool()
					.sounds(BlockSoundGroup.METAL)));

	public static final Block SORTER = register("sorter",
			new SorterBlock(AbstractBlock.Settings.create()
					.mapColor(MapColor.PURPLE)
					.strength(3.0f, 6.0f)
					.requiresTool()
					.sounds(BlockSoundGroup.METAL)));

	public static final Block SOLAR_ARRAY = register("solar_array",
			new SolarArrayBlock(AbstractBlock.Settings.create()
					.mapColor(MapColor.BLACK)
					.strength(2.5f, 6.0f)
					.requiresTool()
					.sounds(BlockSoundGroup.GLASS)));

	public static final Block THERMAL_GENERATOR = register("thermal_generator",
			new ThermalGeneratorBlock(AbstractBlock.Settings.create()
					.mapColor(MapColor.ORANGE)
					.strength(3.5f, 6.0f)
					.requiresTool()
					.luminance(state -> 10)
					.sounds(BlockSoundGroup.METAL)));

	public static final Block CAPACITOR = register("capacitor",
			new CapacitorBlock(AbstractBlock.Settings.create()
					.mapColor(MapColor.BLUE)
					.strength(3.0f, 6.0f)
					.requiresTool()
					.sounds(BlockSoundGroup.METAL)));

	public static final Block RECYCLER = register("recycler",
			new RecyclerBlock(AbstractBlock.Settings.create()
					.mapColor(MapColor.RED)
					.strength(3.0f, 6.0f)
					.requiresTool()
					.sounds(BlockSoundGroup.METAL)));

	public static final Block FLUID_PUMP = register("fluid_pump",
			new FluidPumpBlock(AbstractBlock.Settings.create()
					.mapColor(MapColor.CYAN)
					.strength(3.0f, 6.0f)
					.requiresTool()
					.sounds(BlockSoundGroup.METAL)));

	public static final Block CONVEYOR = register("conveyor",
			new ConveyorBlock(AbstractBlock.Settings.create()
					.mapColor(MapColor.GRAY)
					.strength(1.5f)
					.sounds(BlockSoundGroup.METAL)
					.nonOpaque()));

	public static final Block DRAWER = register("drawer",
			new DrawerBlock(AbstractBlock.Settings.create()
					.mapColor(MapColor.BROWN)
					.strength(2.5f)
					.sounds(BlockSoundGroup.WOOD)));

	public static final Block VOID = register("void_hatch",
			new VoidBlock(AbstractBlock.Settings.create()
					.mapColor(MapColor.BLACK)
					.strength(3.0f, 6.0f)
					.requiresTool()
					.sounds(BlockSoundGroup.METAL)));

	public static final Block BLOCK_BREAKER = register("block_breaker",
			new BlockBreakerBlock(AbstractBlock.Settings.create()
					.mapColor(MapColor.GRAY)
					.strength(3.5f, 6.0f)
					.requiresTool()
					.sounds(BlockSoundGroup.METAL)));

	public static final Block BLOCK_PLACER = register("block_placer",
			new BlockPlacerBlock(AbstractBlock.Settings.create()
					.mapColor(MapColor.GRAY)
					.strength(3.5f, 6.0f)
					.requiresTool()
					.sounds(BlockSoundGroup.METAL)));

	public static final Block HARVESTER = register("harvester",
			new HarvesterBlock(AbstractBlock.Settings.create()
					.mapColor(MapColor.LIME)
					.strength(3.0f, 6.0f)
					.requiresTool()
					.sounds(BlockSoundGroup.METAL)));

	public static final Block TREE_FARM = register("tree_farm",
			new TreeFarmBlock(AbstractBlock.Settings.create()
					.mapColor(MapColor.GREEN)
					.strength(3.0f, 6.0f)
					.requiresTool()
					.sounds(BlockSoundGroup.METAL)));

	public static final Block SENTRY = register("sentry",
			new SentryBlock(AbstractBlock.Settings.create()
					.mapColor(MapColor.RED)
					.strength(3.5f, 6.0f)
					.requiresTool()
					.luminance(state -> 5)
					.sounds(BlockSoundGroup.METAL)));

	public static final Block RANCHER = register("rancher",
			new RancherBlock(AbstractBlock.Settings.create()
					.mapColor(MapColor.PINK)
					.strength(3.0f, 6.0f)
					.requiresTool()
					.sounds(BlockSoundGroup.METAL)));

	public static final Block DRONE_BAY = register("drone_bay",
			new DroneBayBlock(AbstractBlock.Settings.create()
					.mapColor(MapColor.CYAN)
					.strength(3.0f, 6.0f)
					.requiresTool()
					.sounds(BlockSoundGroup.METAL)));

	public static final Block XP_COLLECTOR = register("xp_collector",
			new XpCollectorBlock(AbstractBlock.Settings.create()
					.mapColor(MapColor.LIME)
					.strength(3.0f, 6.0f)
					.requiresTool()
					.luminance(state -> 7)
					.sounds(BlockSoundGroup.METAL)));

	public static final Block CONDUIT = register("conduit",
			new ConduitBlock(AbstractBlock.Settings.create()
					.mapColor(MapColor.ORANGE)
					.strength(1.0f)
					.sounds(BlockSoundGroup.METAL)
					.nonOpaque()));

	public static final Block PYLON = register("pylon",
			new PylonBlock(AbstractBlock.Settings.create()
					.mapColor(MapColor.LIGHT_BLUE)
					.strength(3.0f, 6.0f)
					.requiresTool()
					.luminance(state -> 6)
					.sounds(BlockSoundGroup.METAL)));

	private static Block register(String name, Block block) {
		Identifier id = Identifier.of(Automata.MOD_ID, name);
		Registry.register(Registries.ITEM, id, new BlockItem(block, new Item.Settings()));
		return Registry.register(Registries.BLOCK, id, block);
	}

	public static void register() {
		Automata.LOGGER.info("[Automata] Registering blocks.");
	}
}
