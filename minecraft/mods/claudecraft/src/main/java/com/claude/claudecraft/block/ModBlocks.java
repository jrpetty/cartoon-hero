package com.claude.claudecraft.block;

import com.claude.claudecraft.ClaudeCraft;
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
 * Blocks added by ClaudeCraft.
 *
 * <p>Each block is registered together with a matching {@link BlockItem} so it
 * can be carried in the inventory and placed.
 */
public final class ModBlocks {
	private ModBlocks() {
	}

	// A solid decorative block — the crafted form of {@code claude_core}.
	public static final Block CLAUDE_BLOCK = registerBlock("claude_block",
			new Block(AbstractBlock.Settings.create()
					.mapColor(MapColor.ORANGE)
					.strength(3.0f, 6.0f)
					.requiresTool()
					.sounds(BlockSoundGroup.METAL)));

	// A soft glow block useful for decoration and light.
	public static final Block GLOWING_CLAUDE_LAMP = registerBlock("glowing_claude_lamp",
			new Block(AbstractBlock.Settings.create()
					.mapColor(MapColor.YELLOW)
					.strength(0.5f)
					.luminance(state -> 15)
					.sounds(BlockSoundGroup.GLASS)));

	private static Block registerBlock(String name, Block block) {
		Identifier id = Identifier.of(ClaudeCraft.MOD_ID, name);
		registerBlockItem(name, block);
		return Registry.register(Registries.BLOCK, id, block);
	}

	private static void registerBlockItem(String name, Block block) {
		Registry.register(Registries.ITEM, Identifier.of(ClaudeCraft.MOD_ID, name),
				new BlockItem(block, new Item.Settings()));
	}

	public static void registerModBlocks() {
		ClaudeCraft.LOGGER.info("[ClaudeCraft] Registering blocks.");
	}
}
