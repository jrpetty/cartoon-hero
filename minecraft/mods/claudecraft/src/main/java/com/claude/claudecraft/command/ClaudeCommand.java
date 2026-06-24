package com.claude.claudecraft.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import static net.minecraft.server.command.CommandManager.literal;

/**
 * Registers the {@code /claude} command, which prints information about the mod
 * and the content it adds. Available to every player (permission level 0).
 */
public final class ClaudeCommand {
	private ClaudeCommand() {
	}

	public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
		dispatcher.register(literal("claude")
				.executes(context -> {
					ServerCommandSource source = context.getSource();
					source.sendFeedback(() -> Text.literal("=== ClaudeCraft ===").formatted(Formatting.AQUA, Formatting.BOLD), false);
					source.sendFeedback(() -> Text.literal("A Minecraft server running only mods made in Claude.").formatted(Formatting.GRAY), false);
					source.sendFeedback(() -> Text.literal("Items: ").formatted(Formatting.WHITE)
							.append(Text.literal("Claude Core, Anthropic Apple").formatted(Formatting.GOLD)), false);
					source.sendFeedback(() -> Text.literal("Blocks: ").formatted(Formatting.WHITE)
							.append(Text.literal("Claude Block, Glowing Claude Lamp").formatted(Formatting.GOLD)), false);
					source.sendFeedback(() -> Text.literal("Try ").formatted(Formatting.GRAY)
							.append(Text.literal("/claude credits").formatted(Formatting.YELLOW)), false);
					return 1;
				})
				.then(literal("credits")
						.executes(context -> {
							context.getSource().sendFeedback(
									() -> Text.literal("ClaudeCraft v1.0.0 — designed and coded by Claude.").formatted(Formatting.LIGHT_PURPLE),
									false);
							return 1;
						})));
	}
}
