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
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Automata — a NeoForge mod that reimagines Minecraft's core loop.
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
@Mod(Automata.MOD_ID)
public class Automata {
	public static final String MOD_ID = "automata";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public Automata(IEventBus modEventBus) {
		LOGGER.info("[Automata] Booting the assembly line — mods made in Claude.");

		// Register every DeferredRegister onto the mod event bus.
		ModComponents.register(modEventBus);
		ModItems.register(modEventBus);
		ModBlocks.register(modEventBus);
		ModBlockEntities.register(modEventBus);
		ModEntities.register(modEventBus);
		ModScreenHandlers.register(modEventBus);
		ModItemGroups.register(modEventBus);

		// Gameplay event hooks (replaces Fabric's UseBlockCallback / join callback).
		NeoForge.EVENT_BUS.register(GameEvents.class);

		LOGGER.info("[Automata] Ready.");
	}

	/**
	 * Game-event listeners replacing the Fabric callbacks registered in
	 * {@code onInitialize}.
	 */
	public static final class GameEvents {
		private GameEvents() {
		}

		// Logistics Wrench: link the selected Router to the inventory you click.
		@SubscribeEvent
		public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
			Level world = event.getLevel();
			var player = event.getEntity();
			if (world.isClientSide || !player.getItemInHand(event.getHand()).is(ModItems.LOGISTICS_WRENCH.get())) {
				return;
			}
			BlockPos pos = event.getPos();
			// Let the Router and the Drone Bay handle their own wrench clicks
			// (router selection / drone-bay linking) rather than treating them as
			// router destinations.
			net.minecraft.world.level.block.entity.BlockEntity clicked = world.getBlockEntity(pos);
			if (clicked instanceof RouterBlockEntity
					|| clicked instanceof com.claude.automata.block.entity.DroneBayBlockEntity) {
				return;
			}
			if (InventoryTransfer.inventoryAt(world, pos) == null) {
				return;
			}
			BlockPos selected = RouterBlockEntity.getSelection(player.getUUID());
			if (selected != null && world.getBlockEntity(selected) instanceof RouterBlockEntity router) {
				int count = router.addDestination(pos);
				player.sendSystemMessage(Component.literal("Linked destination (" + count + " total).")
						.withStyle(ChatFormatting.GREEN));
				event.setCanceled(true);
				event.setCancellationResult(InteractionResult.SUCCESS);
				return;
			}
			player.sendSystemMessage(Component.literal("Select a Router with the wrench first.")
					.withStyle(ChatFormatting.RED));
			event.setCanceled(true);
			event.setCancellationResult(InteractionResult.SUCCESS);
		}

		@SubscribeEvent
		public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
			event.getEntity().sendSystemMessage(Component.literal(
					"Welcome to Automata. The crafting table and furnace are gone.")
					.withStyle(ChatFormatting.AQUA));
			event.getEntity().sendSystemMessage(Component.literal(
					"Punch a tree, mine stone, then craft a Fabricator (logs over cobblestone) in your 2x2 grid.")
					.withStyle(ChatFormatting.GRAY));
		}
	}
}
