package com.claude.automata.block;

import com.claude.automata.block.entity.PylonBlockEntity;
import com.claude.automata.registry.ModBlockEntities;
import com.claude.automata.registry.ModItems;
import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * The Power Pylon block. Right-click with the Logistics Wrench to select it,
 * then wrench-click another pylon to beam power from the first to the second.
 * Sneak-wrench-click to clear a pylon's links. Right-click empty-handed for an
 * energy readout (inherited from {@link EnergyBlock}).
 */
public class PylonBlock extends EnergyBlock {
	public static final MapCodec<PylonBlock> CODEC = simpleCodec(PylonBlock::new);

	public PylonBlock(Properties settings) {
		super(settings);
	}

	@Override
	protected MapCodec<PylonBlock> codec() {
		return CODEC;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new PylonBlockEntity(pos, state);
	}

	@Override
	protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level world, BlockPos pos,
			Player player, InteractionHand hand, BlockHitResult hit) {
		if (!stack.is(ModItems.LOGISTICS_WRENCH.get())) {
			return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
		}
		if (world.isClientSide) {
			return ItemInteractionResult.SUCCESS;
		}
		if (!(world.getBlockEntity(pos) instanceof PylonBlockEntity pylon)) {
			return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
		}

		if (player.isShiftKeyDown()) {
			pylon.clearLinks();
			player.displayClientMessage(Component.literal("Pylon links cleared.").withStyle(ChatFormatting.RED), false);
			return ItemInteractionResult.SUCCESS;
		}

		BlockPos selected = PylonBlockEntity.getSelection(player.getUUID());
		if (selected == null || selected.equals(pos)) {
			PylonBlockEntity.select(player.getUUID(), pos);
			player.displayClientMessage(Component.literal("Pylon selected. Wrench-click another pylon to send power to it.")
					.withStyle(ChatFormatting.AQUA), false);
		} else if (world.getBlockEntity(selected) instanceof PylonBlockEntity source) {
			int count = source.addLink(pos);
			PylonBlockEntity.deselect(player.getUUID());
			player.displayClientMessage(Component.literal("Linked: power will beam to this pylon (" + count + " link(s)).")
					.withStyle(ChatFormatting.GREEN), false);
		} else {
			PylonBlockEntity.select(player.getUUID(), pos);
			player.displayClientMessage(Component.literal("Pylon selected.").withStyle(ChatFormatting.AQUA), false);
		}
		return ItemInteractionResult.SUCCESS;
	}

	@Override
	@Nullable
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level world, BlockState state,
			BlockEntityType<T> type) {
		if (world.isClientSide) {
			return null;
		}
		return createTickerHelper(type, ModBlockEntities.PYLON.get(), (w, p, s, be) -> be.tick(w, p, s));
	}
}
