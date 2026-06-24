package com.claude.automata.block;

import com.claude.automata.block.entity.RouterBlockEntity;
import com.claude.automata.registry.ModBlockEntities;
import com.claude.automata.registry.ModItems;
import com.mojang.serialization.MapCodec;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * The Logistics Router block. Interaction:
 * <ul>
 *   <li>Right-click with the Logistics Wrench → select this router (then click
 *       destination inventories with the wrench to link them).</li>
 *   <li>Right-click with any other item → toggle that item in the filter.</li>
 *   <li>Right-click empty-handed → show status; sneak-right-click → clear links.</li>
 * </ul>
 */
public class RouterBlock extends BlockWithEntity {
	public static final MapCodec<RouterBlock> CODEC = createCodec(RouterBlock::new);

	public RouterBlock(Settings settings) {
		super(settings);
	}

	@Override
	protected MapCodec<RouterBlock> getCodec() {
		return CODEC;
	}

	@Override
	protected BlockRenderType getRenderType(BlockState state) {
		return BlockRenderType.MODEL;
	}

	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new RouterBlockEntity(pos, state);
	}

	@Override
	protected ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos,
			PlayerEntity player, Hand hand, BlockHitResult hit) {
		if (world.isClient) {
			return ItemActionResult.SUCCESS;
		}
		if (!(world.getBlockEntity(pos) instanceof RouterBlockEntity router)) {
			return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
		}
		if (stack.isOf(ModItems.LOGISTICS_WRENCH)) {
			RouterBlockEntity.select(player.getUuid(), pos);
			player.sendMessage(Text.literal("Router selected. Right-click chests/machines with the wrench to link them.")
					.formatted(Formatting.AQUA), false);
		} else {
			boolean added = router.toggleFilter(stack.getItem());
			player.sendMessage(Text.literal((added ? "Filter: now sending " : "Filter: stopped sending ")
					+ stack.getName().getString()).formatted(added ? Formatting.GREEN : Formatting.YELLOW), false);
		}
		return ItemActionResult.SUCCESS;
	}

	@Override
	public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player,
			BlockHitResult hit) {
		if (world.isClient) {
			return ActionResult.SUCCESS;
		}
		if (world.getBlockEntity(pos) instanceof RouterBlockEntity router) {
			if (player.isSneaking()) {
				router.clearLinks();
				player.sendMessage(Text.literal("Router links and filter cleared.").formatted(Formatting.RED), false);
			} else {
				player.sendMessage(Text.literal("Router: " + router.destinationCount()
						+ " destination(s), " + router.filterCount() + " filter item(s).")
						.formatted(Formatting.GRAY), false);
			}
			return ActionResult.SUCCESS;
		}
		return ActionResult.PASS;
	}

	@Override
	@Nullable
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state,
			BlockEntityType<T> type) {
		if (world.isClient) {
			return null;
		}
		return validateTicker(type, ModBlockEntities.ROUTER, (w, p, s, be) -> be.tick(w, p, s));
	}
}
