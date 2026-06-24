package com.claude.automata.block;

import com.claude.automata.block.entity.DrawerBlockEntity;
import com.claude.automata.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * The Mass Storage Drawer block. Right-click with an item to deposit your held
 * stack; right-click empty-handed to withdraw a stack (sneak for a readout
 * only). Drops everything it holds when broken.
 */
public class DrawerBlock extends BlockWithEntity {
	public static final MapCodec<DrawerBlock> CODEC = createCodec(DrawerBlock::new);

	public DrawerBlock(Settings settings) {
		super(settings);
	}

	@Override
	protected MapCodec<DrawerBlock> getCodec() {
		return CODEC;
	}

	@Override
	protected BlockRenderType getRenderType(BlockState state) {
		return BlockRenderType.MODEL;
	}

	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new DrawerBlockEntity(pos, state);
	}

	private void report(PlayerEntity player, DrawerBlockEntity drawer) {
		Item bound = drawer.getBound();
		if (bound == Items.AIR) {
			player.sendMessage(Text.literal("Drawer: empty.").formatted(Formatting.GRAY), false);
		} else {
			player.sendMessage(Text.literal("Drawer: " + drawer.totalCount() + " / "
					+ DrawerBlockEntity.CAPACITY + " " + bound.getName().getString()).formatted(Formatting.AQUA), false);
		}
	}

	@Override
	protected ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos,
			PlayerEntity player, Hand hand, BlockHitResult hit) {
		if (world.isClient) {
			return ItemActionResult.SUCCESS;
		}
		if (world.getBlockEntity(pos) instanceof DrawerBlockEntity drawer) {
			drawer.deposit(stack);
			report(player, drawer);
			return ItemActionResult.SUCCESS;
		}
		return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
	}

	@Override
	public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player,
			BlockHitResult hit) {
		if (world.isClient) {
			return ActionResult.SUCCESS;
		}
		if (world.getBlockEntity(pos) instanceof DrawerBlockEntity drawer) {
			if (!player.isSneaking()) {
				ItemStack withdrawn = drawer.withdraw(64);
				if (!withdrawn.isEmpty()) {
					player.getInventory().offerOrDrop(withdrawn);
				}
			}
			report(player, drawer);
			return ActionResult.SUCCESS;
		}
		return ActionResult.PASS;
	}

	@Override
	protected void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
		if (!state.isOf(newState.getBlock())) {
			if (world.getBlockEntity(pos) instanceof DrawerBlockEntity drawer) {
				ItemScatterer.spawn(world, pos, drawer);
				ItemStack remaining = drawer.withdraw(DrawerBlockEntity.CAPACITY);
				Item bound = remaining.getItem();
				int count = remaining.getCount();
				int maxStack = bound == Items.AIR ? 64 : bound.getDefaultStack().getMaxCount();
				while (count > 0 && !remaining.isEmpty()) {
					int n = Math.min(count, maxStack);
					ItemScatterer.spawn(world, pos.getX(), pos.getY(), pos.getZ(), new ItemStack(bound, n));
					count -= n;
				}
				world.updateComparators(pos, this);
			}
			super.onStateReplaced(state, world, pos, newState, moved);
		}
	}

	@Override
	@Nullable
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state,
			BlockEntityType<T> type) {
		if (world.isClient) {
			return null;
		}
		return validateTicker(type, ModBlockEntities.DRAWER, (w, p, s, be) -> be.tick(w, p, s));
	}
}
