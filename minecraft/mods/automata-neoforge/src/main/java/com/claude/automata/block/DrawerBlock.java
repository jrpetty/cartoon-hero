package com.claude.automata.block;

import com.claude.automata.block.entity.DrawerBlockEntity;
import com.claude.automata.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * The Mass Storage Drawer block. Right-click with an item to deposit your held
 * stack; right-click empty-handed to withdraw a stack (sneak for a readout
 * only). Drops everything it holds when broken.
 */
public class DrawerBlock extends BaseEntityBlock {
	public static final MapCodec<DrawerBlock> CODEC = simpleCodec(DrawerBlock::new);

	public DrawerBlock(Properties settings) {
		super(settings);
	}

	@Override
	protected MapCodec<DrawerBlock> codec() {
		return CODEC;
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new DrawerBlockEntity(pos, state);
	}

	private void report(Player player, DrawerBlockEntity drawer) {
		Item bound = drawer.getBound();
		if (bound == Items.AIR) {
			player.displayClientMessage(Component.literal("Drawer: empty.").withStyle(ChatFormatting.GRAY), false);
		} else {
			// PORT-NOTE: Yarn Item.getName() -> Mojmap Item.getDescription() (no-arg name accessor on Item).
			player.displayClientMessage(Component.literal("Drawer: " + drawer.totalCount() + " / "
					+ DrawerBlockEntity.CAPACITY + " " + bound.getDescription().getString()).withStyle(ChatFormatting.AQUA), false);
		}
	}

	@Override
	protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level world, BlockPos pos,
			Player player, InteractionHand hand, BlockHitResult hit) {
		if (world.isClientSide) {
			return ItemInteractionResult.SUCCESS;
		}
		if (world.getBlockEntity(pos) instanceof DrawerBlockEntity drawer) {
			drawer.deposit(stack);
			report(player, drawer);
			return ItemInteractionResult.SUCCESS;
		}
		return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player,
			BlockHitResult hit) {
		if (world.isClientSide) {
			return InteractionResult.SUCCESS;
		}
		if (world.getBlockEntity(pos) instanceof DrawerBlockEntity drawer) {
			if (!player.isShiftKeyDown()) {
				ItemStack withdrawn = drawer.withdraw(64);
				if (!withdrawn.isEmpty()) {
					player.getInventory().placeItemBackInInventory(withdrawn);
				}
			}
			report(player, drawer);
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.PASS;
	}

	@Override
	protected void onRemove(BlockState state, Level world, BlockPos pos, BlockState newState, boolean moved) {
		if (!state.is(newState.getBlock())) {
			if (world.getBlockEntity(pos) instanceof DrawerBlockEntity drawer) {
				Containers.dropContents(world, pos, drawer);
				ItemStack remaining = drawer.withdraw(DrawerBlockEntity.CAPACITY);
				Item bound = remaining.getItem();
				int count = remaining.getCount();
				int maxStack = bound == Items.AIR ? 64 : bound.getDefaultInstance().getMaxStackSize();
				while (count > 0 && !remaining.isEmpty()) {
					int n = Math.min(count, maxStack);
					Containers.dropItemStack(world, pos.getX(), pos.getY(), pos.getZ(), new ItemStack(bound, n));
					count -= n;
				}
				world.updateNeighbourForOutputSignal(pos, this);
			}
			super.onRemove(state, world, pos, newState, moved);
		}
	}

	@Override
	@Nullable
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level world, BlockState state,
			BlockEntityType<T> type) {
		if (world.isClientSide) {
			return null;
		}
		return createTickerHelper(type, ModBlockEntities.DRAWER.get(), (w, p, s, be) -> be.tick(w, p, s));
	}
}
