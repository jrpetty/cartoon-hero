package com.claude.automata.registry;

import com.claude.automata.Automata;
import com.claude.automata.block.entity.FabricatorBlockEntity;
import com.claude.automata.block.entity.ForgeCoreBlockEntity;
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

	public static void register() {
		Automata.LOGGER.info("[Automata] Registering block entities.");
	}
}
