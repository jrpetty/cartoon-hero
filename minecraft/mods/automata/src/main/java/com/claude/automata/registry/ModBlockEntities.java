package com.claude.automata.registry;

import com.claude.automata.Automata;
import com.claude.automata.block.entity.CollectorBlockEntity;
import com.claude.automata.block.entity.CrusherBlockEntity;
import com.claude.automata.block.entity.FabricatorBlockEntity;
import com.claude.automata.block.entity.ForgeCoreBlockEntity;
import com.claude.automata.block.entity.GeneratorBlockEntity;
import com.claude.automata.block.entity.MinerBlockEntity;
import com.claude.automata.block.entity.RouterBlockEntity;
import com.claude.automata.block.entity.SawmillBlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class ModBlockEntities {
	private ModBlockEntities() {
	}

	public static final BlockEntityType<FabricatorBlockEntity> FABRICATOR = Registry.register(
			Registries.BLOCK_ENTITY_TYPE,
			Identifier.of(Automata.MOD_ID, "fabricator"),
			BlockEntityType.Builder.create(FabricatorBlockEntity::new, ModBlocks.FABRICATOR).build());

	public static final BlockEntityType<ForgeCoreBlockEntity> FORGE_CORE = Registry.register(
			Registries.BLOCK_ENTITY_TYPE,
			Identifier.of(Automata.MOD_ID, "forge_core"),
			BlockEntityType.Builder.create(ForgeCoreBlockEntity::new, ModBlocks.FORGE_CORE).build());

	public static final BlockEntityType<GeneratorBlockEntity> GENERATOR = Registry.register(
			Registries.BLOCK_ENTITY_TYPE,
			Identifier.of(Automata.MOD_ID, "generator"),
			BlockEntityType.Builder.create(GeneratorBlockEntity::new, ModBlocks.GENERATOR).build());

	public static final BlockEntityType<CrusherBlockEntity> CRUSHER = Registry.register(
			Registries.BLOCK_ENTITY_TYPE,
			Identifier.of(Automata.MOD_ID, "crusher"),
			BlockEntityType.Builder.create(CrusherBlockEntity::new, ModBlocks.CRUSHER).build());

	public static final BlockEntityType<SawmillBlockEntity> SAWMILL = Registry.register(
			Registries.BLOCK_ENTITY_TYPE,
			Identifier.of(Automata.MOD_ID, "sawmill"),
			BlockEntityType.Builder.create(SawmillBlockEntity::new, ModBlocks.SAWMILL).build());

	public static final BlockEntityType<MinerBlockEntity> MINER = Registry.register(
			Registries.BLOCK_ENTITY_TYPE,
			Identifier.of(Automata.MOD_ID, "miner"),
			BlockEntityType.Builder.create(MinerBlockEntity::new, ModBlocks.MINER).build());

	public static final BlockEntityType<CollectorBlockEntity> COLLECTOR = Registry.register(
			Registries.BLOCK_ENTITY_TYPE,
			Identifier.of(Automata.MOD_ID, "collector"),
			BlockEntityType.Builder.create(CollectorBlockEntity::new, ModBlocks.COLLECTOR).build());

	public static final BlockEntityType<RouterBlockEntity> ROUTER = Registry.register(
			Registries.BLOCK_ENTITY_TYPE,
			Identifier.of(Automata.MOD_ID, "router"),
			BlockEntityType.Builder.create(RouterBlockEntity::new, ModBlocks.ROUTER).build());

	public static void register() {
		Automata.LOGGER.info("[Automata] Registering block entities.");
	}
}
