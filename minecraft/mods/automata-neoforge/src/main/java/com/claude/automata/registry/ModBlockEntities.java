package com.claude.automata.registry;

import com.claude.automata.Automata;
import com.claude.automata.block.entity.CollectorBlockEntity;
import com.claude.automata.block.entity.CrusherBlockEntity;
import com.claude.automata.block.entity.FabricatorBlockEntity;
import com.claude.automata.block.entity.ForgeCoreBlockEntity;
import com.claude.automata.block.entity.GeneratorBlockEntity;
import com.claude.automata.block.entity.HarvesterBlockEntity;
import com.claude.automata.block.entity.TreeFarmBlockEntity;
import com.claude.automata.block.entity.BlockBreakerBlockEntity;
import com.claude.automata.block.entity.BlockPlacerBlockEntity;
import com.claude.automata.block.entity.CapacitorBlockEntity;
import com.claude.automata.block.entity.CircuitAssemblerBlockEntity;
import com.claude.automata.block.entity.ConduitBlockEntity;
import com.claude.automata.block.entity.ConveyorBlockEntity;
import com.claude.automata.block.entity.DrawerBlockEntity;
import com.claude.automata.block.entity.DroneBayBlockEntity;
import com.claude.automata.block.entity.FluidPumpBlockEntity;
import com.claude.automata.block.entity.MinerBlockEntity;
import com.claude.automata.block.entity.PylonBlockEntity;
import com.claude.automata.block.entity.RecyclerBlockEntity;
import com.claude.automata.block.entity.RouterBlockEntity;
import com.claude.automata.block.entity.RancherBlockEntity;
import com.claude.automata.block.entity.SawmillBlockEntity;
import com.claude.automata.block.entity.SolarArrayBlockEntity;
import com.claude.automata.block.entity.SentryBlockEntity;
import com.claude.automata.block.entity.SorterBlockEntity;
import com.claude.automata.block.entity.ThermalGeneratorBlockEntity;
import com.claude.automata.block.entity.VoidBlockEntity;
import com.claude.automata.block.entity.XpCollectorBlockEntity;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {
	private ModBlockEntities() {
	}

	public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
			DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Automata.MOD_ID);

	public static final Supplier<BlockEntityType<FabricatorBlockEntity>> FABRICATOR = BLOCK_ENTITIES.register(
			"fabricator",
			() -> BlockEntityType.Builder.of(FabricatorBlockEntity::new, ModBlocks.FABRICATOR.get()).build(null));

	public static final Supplier<BlockEntityType<ForgeCoreBlockEntity>> FORGE_CORE = BLOCK_ENTITIES.register(
			"forge_core",
			() -> BlockEntityType.Builder.of(ForgeCoreBlockEntity::new, ModBlocks.FORGE_CORE.get()).build(null));

	public static final Supplier<BlockEntityType<GeneratorBlockEntity>> GENERATOR = BLOCK_ENTITIES.register(
			"generator",
			() -> BlockEntityType.Builder.of(GeneratorBlockEntity::new, ModBlocks.GENERATOR.get()).build(null));

	public static final Supplier<BlockEntityType<CrusherBlockEntity>> CRUSHER = BLOCK_ENTITIES.register(
			"crusher",
			() -> BlockEntityType.Builder.of(CrusherBlockEntity::new, ModBlocks.CRUSHER.get()).build(null));

	public static final Supplier<BlockEntityType<SawmillBlockEntity>> SAWMILL = BLOCK_ENTITIES.register(
			"sawmill",
			() -> BlockEntityType.Builder.of(SawmillBlockEntity::new, ModBlocks.SAWMILL.get()).build(null));

	public static final Supplier<BlockEntityType<MinerBlockEntity>> MINER = BLOCK_ENTITIES.register(
			"miner",
			() -> BlockEntityType.Builder.of(MinerBlockEntity::new, ModBlocks.MINER.get()).build(null));

	public static final Supplier<BlockEntityType<CollectorBlockEntity>> COLLECTOR = BLOCK_ENTITIES.register(
			"collector",
			() -> BlockEntityType.Builder.of(CollectorBlockEntity::new, ModBlocks.COLLECTOR.get()).build(null));

	public static final Supplier<BlockEntityType<RouterBlockEntity>> ROUTER = BLOCK_ENTITIES.register(
			"router",
			() -> BlockEntityType.Builder.of(RouterBlockEntity::new, ModBlocks.ROUTER.get()).build(null));

	public static final Supplier<BlockEntityType<SorterBlockEntity>> SORTER = BLOCK_ENTITIES.register(
			"sorter",
			() -> BlockEntityType.Builder.of(SorterBlockEntity::new, ModBlocks.SORTER.get()).build(null));

	public static final Supplier<BlockEntityType<SolarArrayBlockEntity>> SOLAR_ARRAY = BLOCK_ENTITIES.register(
			"solar_array",
			() -> BlockEntityType.Builder.of(SolarArrayBlockEntity::new, ModBlocks.SOLAR_ARRAY.get()).build(null));

	public static final Supplier<BlockEntityType<ThermalGeneratorBlockEntity>> THERMAL_GENERATOR = BLOCK_ENTITIES.register(
			"thermal_generator",
			() -> BlockEntityType.Builder.of(ThermalGeneratorBlockEntity::new, ModBlocks.THERMAL_GENERATOR.get()).build(null));

	public static final Supplier<BlockEntityType<CapacitorBlockEntity>> CAPACITOR = BLOCK_ENTITIES.register(
			"capacitor",
			() -> BlockEntityType.Builder.of(CapacitorBlockEntity::new, ModBlocks.CAPACITOR.get()).build(null));

	public static final Supplier<BlockEntityType<RecyclerBlockEntity>> RECYCLER = BLOCK_ENTITIES.register(
			"recycler",
			() -> BlockEntityType.Builder.of(RecyclerBlockEntity::new, ModBlocks.RECYCLER.get()).build(null));

	public static final Supplier<BlockEntityType<FluidPumpBlockEntity>> FLUID_PUMP = BLOCK_ENTITIES.register(
			"fluid_pump",
			() -> BlockEntityType.Builder.of(FluidPumpBlockEntity::new, ModBlocks.FLUID_PUMP.get()).build(null));

	public static final Supplier<BlockEntityType<ConveyorBlockEntity>> CONVEYOR = BLOCK_ENTITIES.register(
			"conveyor",
			() -> BlockEntityType.Builder.of(ConveyorBlockEntity::new, ModBlocks.CONVEYOR.get()).build(null));

	public static final Supplier<BlockEntityType<DrawerBlockEntity>> DRAWER = BLOCK_ENTITIES.register(
			"drawer",
			() -> BlockEntityType.Builder.of(DrawerBlockEntity::new, ModBlocks.DRAWER.get()).build(null));

	public static final Supplier<BlockEntityType<VoidBlockEntity>> VOID = BLOCK_ENTITIES.register(
			"void_hatch",
			() -> BlockEntityType.Builder.of(VoidBlockEntity::new, ModBlocks.VOID.get()).build(null));

	public static final Supplier<BlockEntityType<ConduitBlockEntity>> CONDUIT = BLOCK_ENTITIES.register(
			"conduit",
			() -> BlockEntityType.Builder.of(ConduitBlockEntity::new, ModBlocks.CONDUIT.get()).build(null));

	public static final Supplier<BlockEntityType<SentryBlockEntity>> SENTRY = BLOCK_ENTITIES.register(
			"sentry",
			() -> BlockEntityType.Builder.of(SentryBlockEntity::new, ModBlocks.SENTRY.get()).build(null));

	public static final Supplier<BlockEntityType<RancherBlockEntity>> RANCHER = BLOCK_ENTITIES.register(
			"rancher",
			() -> BlockEntityType.Builder.of(RancherBlockEntity::new, ModBlocks.RANCHER.get()).build(null));

	public static final Supplier<BlockEntityType<HarvesterBlockEntity>> HARVESTER = BLOCK_ENTITIES.register(
			"harvester",
			() -> BlockEntityType.Builder.of(HarvesterBlockEntity::new, ModBlocks.HARVESTER.get()).build(null));

	public static final Supplier<BlockEntityType<TreeFarmBlockEntity>> TREE_FARM = BLOCK_ENTITIES.register(
			"tree_farm",
			() -> BlockEntityType.Builder.of(TreeFarmBlockEntity::new, ModBlocks.TREE_FARM.get()).build(null));

	public static final Supplier<BlockEntityType<DroneBayBlockEntity>> DRONE_BAY = BLOCK_ENTITIES.register(
			"drone_bay",
			() -> BlockEntityType.Builder.of(DroneBayBlockEntity::new, ModBlocks.DRONE_BAY.get()).build(null));

	public static final Supplier<BlockEntityType<XpCollectorBlockEntity>> XP_COLLECTOR = BLOCK_ENTITIES.register(
			"xp_collector",
			() -> BlockEntityType.Builder.of(XpCollectorBlockEntity::new, ModBlocks.XP_COLLECTOR.get()).build(null));

	public static final Supplier<BlockEntityType<PylonBlockEntity>> PYLON = BLOCK_ENTITIES.register(
			"pylon",
			() -> BlockEntityType.Builder.of(PylonBlockEntity::new, ModBlocks.PYLON.get()).build(null));

	public static final Supplier<BlockEntityType<BlockBreakerBlockEntity>> BLOCK_BREAKER = BLOCK_ENTITIES.register(
			"block_breaker",
			() -> BlockEntityType.Builder.of(BlockBreakerBlockEntity::new, ModBlocks.BLOCK_BREAKER.get()).build(null));

	public static final Supplier<BlockEntityType<BlockPlacerBlockEntity>> BLOCK_PLACER = BLOCK_ENTITIES.register(
			"block_placer",
			() -> BlockEntityType.Builder.of(BlockPlacerBlockEntity::new, ModBlocks.BLOCK_PLACER.get()).build(null));

	public static final Supplier<BlockEntityType<CircuitAssemblerBlockEntity>> CIRCUIT_ASSEMBLER = BLOCK_ENTITIES.register(
			"circuit_assembler",
			() -> BlockEntityType.Builder.of(CircuitAssemblerBlockEntity::new, ModBlocks.CIRCUIT_ASSEMBLER.get()).build(null));

	public static void register(IEventBus modEventBus) {
		Automata.LOGGER.info("[Automata] Registering block entities.");
		BLOCK_ENTITIES.register(modEventBus);
	}
}
