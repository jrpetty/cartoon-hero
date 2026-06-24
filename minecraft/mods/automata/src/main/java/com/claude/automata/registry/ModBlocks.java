package com.claude.automata.registry;

import com.claude.automata.Automata;
import com.claude.automata.block.FabricatorBlock;
import com.claude.automata.block.ForgeCoreBlock;
import com.claude.automata.block.GeneratorBlock;
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

	private static Block register(String name, Block block) {
		Identifier id = Identifier.of(Automata.MOD_ID, name);
		Registry.register(Registries.ITEM, id, new BlockItem(block, new Item.Settings()));
		return Registry.register(Registries.BLOCK, id, block);
	}

	public static void register() {
		Automata.LOGGER.info("[Automata] Registering blocks.");
	}
}
