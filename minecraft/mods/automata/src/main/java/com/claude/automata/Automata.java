package com.claude.automata;

import com.claude.automata.block.entity.RouterBlockEntity;
import com.claude.automata.logistics.InventoryTransfer;
import com.claude.automata.registry.ModBlockEntities;
import com.claude.automata.registry.ModBlocks;
import com.claude.automata.registry.ModComponents;
import com.claude.automata.registry.ModEntities;
import com.claude.automata.registry.ModItemGroups;
import com.claude.automata.registry.ModItems;
import com.claude.automata.screen.ModScreenHandlers;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Automata — a Fabric mod that reimagines Minecraft's core loop.
 *
 * <p>The vanilla crafting table and furnace are disabled (see the recipe
 * overrides under {@code data/minecraft/recipe}). In their place are two
 * self-running machines:
 *
 * <ul>
 *   <li>{@code Forge Core} — auto-smelts anything fed into it (replaces the furnace).</li>
 *   <li>{@code Fabricator} — auto-assembles components, machines, blocks and tools
 *       (replaces the crafting table).</li>
 * </ul>
 *
 * <p>Both machines accept items from hoppers and push results to hoppers, so the
 * intended endgame is hands-free factories. Before you have hoppers you feed the
 * machines by hand (right-click to insert, right-click empty-handed to pull the
 * output) — that is the manual twist at the start.
 */
public class Automata implements ModInitializer {
	public static final String MOD_ID = "automata";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("[Automata] Booting the assembly line — mods made in Claude.");

		ModComponents.register();
		ModItems.register();
		ModBlocks.register();
		ModBlockEntities.register();
		ModEntities.register();
		ModScreenHandlers.register();
		ModItemGroups.register();

		// Logistics Wrench: link the selected Router to the inventory you click.
		UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
			if (world.isClient || !player.getStackInHand(hand).isOf(ModItems.LOGISTICS_WRENCH)) {
				return ActionResult.PASS;
			}
			BlockPos pos = hit.getBlockPos();
			// Let the router block itself handle wrench clicks (selection).
			if (world.getBlockEntity(pos) instanceof RouterBlockEntity) {
				return ActionResult.PASS;
			}
			if (InventoryTransfer.inventoryAt(world, pos) == null) {
				return ActionResult.PASS;
			}
			BlockPos selected = RouterBlockEntity.getSelection(player.getUuid());
			if (selected != null && world.getBlockEntity(selected) instanceof RouterBlockEntity router) {
				int count = router.addDestination(pos);
				player.sendMessage(Text.literal("Linked destination (" + count + " total).")
						.formatted(Formatting.GREEN), false);
				return ActionResult.SUCCESS;
			}
			player.sendMessage(Text.literal("Select a Router with the wrench first.")
					.formatted(Formatting.RED), false);
			return ActionResult.SUCCESS;
		});

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			handler.player.sendMessage(Text.literal(
					"Welcome to Automata. The crafting table and furnace are gone.")
					.formatted(Formatting.AQUA), false);
			handler.player.sendMessage(Text.literal(
					"Punch a tree, mine stone, then craft a Fabricator (logs over cobblestone) in your 2x2 grid.")
					.formatted(Formatting.GRAY), false);
		});

		LOGGER.info("[Automata] Ready.");
	}
}
