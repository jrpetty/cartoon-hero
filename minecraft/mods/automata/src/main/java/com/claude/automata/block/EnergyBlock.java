package com.claude.automata.block;

import com.claude.automata.block.entity.EnergyBlockEntity;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Base for the pure-energy blocks (Solar Array, Capacitor Bank). Right-clicking
 * reports the stored energy; they have no inventory.
 */
public abstract class EnergyBlock extends BlockWithEntity {
	protected EnergyBlock(Settings settings) {
		super(settings);
	}

	@Override
	protected BlockRenderType getRenderType(BlockState state) {
		return BlockRenderType.MODEL;
	}

	@Override
	public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player,
			BlockHitResult hit) {
		if (world.isClient) {
			return ActionResult.SUCCESS;
		}
		if (world.getBlockEntity(pos) instanceof EnergyBlockEntity energy) {
			player.sendMessage(Text.literal("Stored energy: " + energy.getEnergy()).formatted(Formatting.AQUA), false);
			return ActionResult.SUCCESS;
		}
		return ActionResult.PASS;
	}
}
