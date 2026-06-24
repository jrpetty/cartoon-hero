package com.claude.claudecraft.group;

import com.claude.claudecraft.ClaudeCraft;
import com.claude.claudecraft.block.ModBlocks;
import com.claude.claudecraft.item.ModItems;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * The creative-inventory tab that collects everything ClaudeCraft adds.
 */
public final class ModItemGroups {
	private ModItemGroups() {
	}

	public static final RegistryKey<net.minecraft.item.ItemGroup> CLAUDECRAFT_GROUP_KEY =
			RegistryKey.of(RegistryKeys.ITEM_GROUP, Identifier.of(ClaudeCraft.MOD_ID, "claudecraft"));

	public static void registerItemGroups() {
		ClaudeCraft.LOGGER.info("[ClaudeCraft] Registering item group.");

		Registry.register(Registries.ITEM_GROUP, CLAUDECRAFT_GROUP_KEY, FabricItemGroup.builder()
				.icon(() -> new ItemStack(ModItems.CLAUDE_CORE))
				.displayName(Text.translatable("itemGroup.claudecraft.claudecraft"))
				.build());

		ItemGroupEvents.modifyEntriesEvent(CLAUDECRAFT_GROUP_KEY).register(entries -> {
			entries.add(ModItems.CLAUDE_CORE);
			entries.add(ModItems.ANTHROPIC_APPLE);
			entries.add(ModBlocks.CLAUDE_BLOCK);
			entries.add(ModBlocks.GLOWING_CLAUDE_LAMP);
		});
	}
}
