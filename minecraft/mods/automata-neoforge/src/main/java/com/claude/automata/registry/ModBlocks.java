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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Blocks added by Automata: the two machines that replace the crafting table
 * and furnace.
 */
public final class ModBlocks {
	private ModBlocks() {
	}

	public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Automata.MOD_ID);

	public static final DeferredBlock<CircuitAssemblerBlock> CIRCUIT_ASSEMBLER = register("circuit_assembler",
			() -> new CircuitAssemblerBlock(BlockBehaviour.Properties.of()
					.mapColor(MapColor.COLOR_LIGHT_GREEN)
					.strength(2.5f, 6.0f)
					.requiresCorrectToolForDrops()
					.sound(SoundType.METAL)));

	public static final DeferredBlock<FabricatorBlock> FABRICATOR = register("fabricator",
			() -> new FabricatorBlock(BlockBehaviour.Properties.of()
					.mapColor(MapColor.COLOR_ORANGE)
					.strength(2.5f, 6.0f)
					.requiresCorrectToolForDrops()
					.sound(SoundType.METAL)));

	public static final DeferredBlock<ForgeCoreBlock> FORGE_CORE = register("forge_core",
			() -> new ForgeCoreBlock(BlockBehaviour.Properties.of()
					.mapColor(MapColor.COLOR_RED)
					.strength(3.5f, 6.0f)
					.requiresCorrectToolForDrops()
					.lightLevel(state -> 13)
					.sound(SoundType.STONE)));

	public static final DeferredBlock<GeneratorBlock> GENERATOR = register("generator",
			() -> new GeneratorBlock(BlockBehaviour.Properties.of()
					.mapColor(MapColor.COLOR_BROWN)
					.strength(3.5f, 6.0f)
					.requiresCorrectToolForDrops()
					.lightLevel(state -> 7)
					.sound(SoundType.METAL)));

	public static final DeferredBlock<CrusherBlock> CRUSHER = register("crusher",
			() -> new CrusherBlock(BlockBehaviour.Properties.of()
					.mapColor(MapColor.COLOR_GRAY)
					.strength(3.0f, 6.0f)
					.requiresCorrectToolForDrops()
					.sound(SoundType.METAL)));

	public static final DeferredBlock<SawmillBlock> SAWMILL = register("sawmill",
			() -> new SawmillBlock(BlockBehaviour.Properties.of()
					.mapColor(MapColor.COLOR_BROWN)
					.strength(3.0f, 6.0f)
					.requiresCorrectToolForDrops()
					.sound(SoundType.METAL)));

	public static final DeferredBlock<MinerBlock> MINER = register("miner",
			() -> new MinerBlock(BlockBehaviour.Properties.of()
					.mapColor(MapColor.COLOR_LIGHT_GRAY)
					.strength(4.0f, 6.0f)
					.requiresCorrectToolForDrops()
					.sound(SoundType.METAL)));

	public static final DeferredBlock<CollectorBlock> COLLECTOR = register("collector",
			() -> new CollectorBlock(BlockBehaviour.Properties.of()
					.mapColor(MapColor.COLOR_LIGHT_BLUE)
					.strength(3.0f, 6.0f)
					.requiresCorrectToolForDrops()
					.sound(SoundType.METAL)));

	public static final DeferredBlock<RouterBlock> ROUTER = register("router",
			() -> new RouterBlock(BlockBehaviour.Properties.of()
					.mapColor(MapColor.COLOR_GREEN)
					.strength(3.0f, 6.0f)
					.requiresCorrectToolForDrops()
					.sound(SoundType.METAL)));

	public static final DeferredBlock<SorterBlock> SORTER = register("sorter",
			() -> new SorterBlock(BlockBehaviour.Properties.of()
					.mapColor(MapColor.COLOR_PURPLE)
					.strength(3.0f, 6.0f)
					.requiresCorrectToolForDrops()
					.sound(SoundType.METAL)));

	public static final DeferredBlock<SolarArrayBlock> SOLAR_ARRAY = register("solar_array",
			() -> new SolarArrayBlock(BlockBehaviour.Properties.of()
					.mapColor(MapColor.COLOR_BLACK)
					.strength(2.5f, 6.0f)
					.requiresCorrectToolForDrops()
					.sound(SoundType.GLASS)));

	public static final DeferredBlock<ThermalGeneratorBlock> THERMAL_GENERATOR = register("thermal_generator",
			() -> new ThermalGeneratorBlock(BlockBehaviour.Properties.of()
					.mapColor(MapColor.COLOR_ORANGE)
					.strength(3.5f, 6.0f)
					.requiresCorrectToolForDrops()
					.lightLevel(state -> 10)
					.sound(SoundType.METAL)));

	public static final DeferredBlock<CapacitorBlock> CAPACITOR = register("capacitor",
			() -> new CapacitorBlock(BlockBehaviour.Properties.of()
					.mapColor(MapColor.COLOR_BLUE)
					.strength(3.0f, 6.0f)
					.requiresCorrectToolForDrops()
					.sound(SoundType.METAL)));

	public static final DeferredBlock<RecyclerBlock> RECYCLER = register("recycler",
			() -> new RecyclerBlock(BlockBehaviour.Properties.of()
					.mapColor(MapColor.COLOR_RED)
					.strength(3.0f, 6.0f)
					.requiresCorrectToolForDrops()
					.sound(SoundType.METAL)));

	public static final DeferredBlock<FluidPumpBlock> FLUID_PUMP = register("fluid_pump",
			() -> new FluidPumpBlock(BlockBehaviour.Properties.of()
					.mapColor(MapColor.COLOR_CYAN)
					.strength(3.0f, 6.0f)
					.requiresCorrectToolForDrops()
					.sound(SoundType.METAL)));

	public static final DeferredBlock<ConveyorBlock> CONVEYOR = register("conveyor",
			() -> new ConveyorBlock(BlockBehaviour.Properties.of()
					.mapColor(MapColor.COLOR_GRAY)
					.strength(1.5f)
					.sound(SoundType.METAL)
					.noOcclusion()));

	public static final DeferredBlock<DrawerBlock> DRAWER = register("drawer",
			() -> new DrawerBlock(BlockBehaviour.Properties.of()
					.mapColor(MapColor.COLOR_BROWN)
					.strength(2.5f)
					.sound(SoundType.WOOD)));

	public static final DeferredBlock<VoidBlock> VOID = register("void_hatch",
			() -> new VoidBlock(BlockBehaviour.Properties.of()
					.mapColor(MapColor.COLOR_BLACK)
					.strength(3.0f, 6.0f)
					.requiresCorrectToolForDrops()
					.sound(SoundType.METAL)));

	public static final DeferredBlock<BlockBreakerBlock> BLOCK_BREAKER = register("block_breaker",
			() -> new BlockBreakerBlock(BlockBehaviour.Properties.of()
					.mapColor(MapColor.COLOR_GRAY)
					.strength(3.5f, 6.0f)
					.requiresCorrectToolForDrops()
					.sound(SoundType.METAL)));

	public static final DeferredBlock<BlockPlacerBlock> BLOCK_PLACER = register("block_placer",
			() -> new BlockPlacerBlock(BlockBehaviour.Properties.of()
					.mapColor(MapColor.COLOR_GRAY)
					.strength(3.5f, 6.0f)
					.requiresCorrectToolForDrops()
					.sound(SoundType.METAL)));

	public static final DeferredBlock<HarvesterBlock> HARVESTER = register("harvester",
			() -> new HarvesterBlock(BlockBehaviour.Properties.of()
					.mapColor(MapColor.COLOR_LIGHT_GREEN)
					.strength(3.0f, 6.0f)
					.requiresCorrectToolForDrops()
					.sound(SoundType.METAL)));

	public static final DeferredBlock<TreeFarmBlock> TREE_FARM = register("tree_farm",
			() -> new TreeFarmBlock(BlockBehaviour.Properties.of()
					.mapColor(MapColor.COLOR_GREEN)
					.strength(3.0f, 6.0f)
					.requiresCorrectToolForDrops()
					.sound(SoundType.METAL)));

	public static final DeferredBlock<SentryBlock> SENTRY = register("sentry",
			() -> new SentryBlock(BlockBehaviour.Properties.of()
					.mapColor(MapColor.COLOR_RED)
					.strength(3.5f, 6.0f)
					.requiresCorrectToolForDrops()
					.lightLevel(state -> 5)
					.sound(SoundType.METAL)));

	public static final DeferredBlock<RancherBlock> RANCHER = register("rancher",
			() -> new RancherBlock(BlockBehaviour.Properties.of()
					.mapColor(MapColor.COLOR_PINK)
					.strength(3.0f, 6.0f)
					.requiresCorrectToolForDrops()
					.sound(SoundType.METAL)));

	public static final DeferredBlock<DroneBayBlock> DRONE_BAY = register("drone_bay",
			() -> new DroneBayBlock(BlockBehaviour.Properties.of()
					.mapColor(MapColor.COLOR_CYAN)
					.strength(3.0f, 6.0f)
					.requiresCorrectToolForDrops()
					.sound(SoundType.METAL)));

	public static final DeferredBlock<XpCollectorBlock> XP_COLLECTOR = register("xp_collector",
			() -> new XpCollectorBlock(BlockBehaviour.Properties.of()
					.mapColor(MapColor.COLOR_LIGHT_GREEN)
					.strength(3.0f, 6.0f)
					.requiresCorrectToolForDrops()
					.lightLevel(state -> 7)
					.sound(SoundType.METAL)));

	public static final DeferredBlock<ConduitBlock> CONDUIT = register("conduit",
			() -> new ConduitBlock(BlockBehaviour.Properties.of()
					.mapColor(MapColor.COLOR_ORANGE)
					.strength(1.0f)
					.sound(SoundType.METAL)
					.noOcclusion()));

	public static final DeferredBlock<PylonBlock> PYLON = register("pylon",
			() -> new PylonBlock(BlockBehaviour.Properties.of()
					.mapColor(MapColor.COLOR_LIGHT_BLUE)
					.strength(3.0f, 6.0f)
					.requiresCorrectToolForDrops()
					.lightLevel(state -> 6)
					.sound(SoundType.METAL)));

	private static <T extends Block> DeferredBlock<T> register(String name, java.util.function.Supplier<T> block) {
		DeferredBlock<T> registered = BLOCKS.register(name, block);
		// Register a matching BlockItem in the same pass (vanilla parity with the Fabric source).
		ModItems.ITEMS.registerSimpleBlockItem(name, registered);
		return registered;
	}

	public static void register(IEventBus modEventBus) {
		Automata.LOGGER.info("[Automata] Registering blocks.");
		BLOCKS.register(modEventBus);
	}
}
