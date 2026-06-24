package com.claude.claudecraft;

import com.claude.claudecraft.block.ModBlocks;
import com.claude.claudecraft.command.ClaudeCommand;
import com.claude.claudecraft.group.ModItemGroups;
import com.claude.claudecraft.item.ModItems;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ClaudeCraft — a Fabric mod written entirely by Claude.
 *
 * <p>This is the single entrypoint that wires up every feature this mod adds:
 * items, blocks, a creative tab, the {@code /claude} command, and a welcome
 * message for joining players.
 */
public class ClaudeCraft implements ModInitializer {
	public static final String MOD_ID = "claudecraft";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("[ClaudeCraft] Initializing — mods made in Claude.");

		// Registration order matters: item groups reference items/blocks, so register
		// the content first and the group last.
		ModItems.registerModItems();
		ModBlocks.registerModBlocks();
		ModItemGroups.registerItemGroups();

		// Register the /claude command.
		CommandRegistrationCallback.EVENT.register(
				(dispatcher, registryAccess, environment) -> ClaudeCommand.register(dispatcher));

		// Greet players when they join the server.
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			handler.player.sendMessage(
					Text.literal("Welcome to a Minecraft server running only mods made in Claude!")
							.formatted(Formatting.AQUA),
					false);
			handler.player.sendMessage(
					Text.literal("Type ").formatted(Formatting.GRAY)
							.append(Text.literal("/claude").formatted(Formatting.GOLD))
							.append(Text.literal(" to learn what is installed.").formatted(Formatting.GRAY)),
					false);
		});

		LOGGER.info("[ClaudeCraft] Ready.");
	}
}
