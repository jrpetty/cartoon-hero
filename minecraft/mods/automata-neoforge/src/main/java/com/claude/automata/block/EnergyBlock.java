package com.claude.automata.block;

import com.claude.automata.block.entity.EnergyBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Base for the pure-energy blocks (Solar Array, Capacitor Bank). Right-clicking
 * reports the stored energy; they have no inventory.
 */
public abstract class EnergyBlock extends BaseEntityBlock {
	protected EnergyBlock(Properties settings) {
		super(settings);
	}

	// PORT-NOTE: BaseEntityBlock declares `protected abstract MapCodec<? extends BaseEntityBlock> codec();`
	// EnergyBlock is abstract, so concrete leaf subclasses supply their own simpleCodec.

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Override
	public InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player,
			BlockHitResult hit) {
		if (world.isClientSide) {
			return InteractionResult.SUCCESS;
		}
		if (world.getBlockEntity(pos) instanceof EnergyBlockEntity energy) {
			player.sendSystemMessage(Component.literal("Stored energy: " + energy.getEnergy()).withStyle(ChatFormatting.AQUA));
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.PASS;
	}
}
