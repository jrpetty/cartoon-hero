package com.claude.automata.block;

import com.claude.automata.block.entity.PylonBlockEntity;
import com.claude.automata.registry.ModBlockEntities;
import com.claude.automata.registry.ModItems;
import com.mojang.serialization.MapCodec;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * The Power Pylon block. Right-click with the Logistics Wrench to select it,
 * then wrench-click another pylon to beam power from the first to the second.
 * Sneak-wrench-click to clear a pylon's links. Right-click empty-handed for an
 * energy readout (inherited from {@link EnergyBlock}).
 */
public class PylonBlock extends EnergyBlock {
	public static final MapCodec<PylonBlock> CODEC = createCodec(PylonBlock::new);

	public PylonBlock(Settings settings) {
		super(settings);
	}

	@Override
	protected MapCodec<PylonBlock> getCodec() {
		return CODEC;
	}

	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new PylonBlockEntity(pos, state);
	}

	@Override
	protected ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos,
			PlayerEntity player, Hand hand, BlockHitResult hit) {
		if (!stack.isOf(ModItems.LOGISTICS_WRENCH)) {
			return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
		}
		if (world.isClient) {
			return ItemActionResult.SUCCESS;
		}
		if (!(world.getBlockEntity(pos) instanceof PylonBlockEntity pylon)) {
			return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
		}

		if (player.isSneaking()) {
			pylon.clearLinks();
			player.sendMessage(Text.literal("Pylon links cleared.").formatted(Formatting.RED), false);
			return ItemActionResult.SUCCESS;
		}

		BlockPos selected = PylonBlockEntity.getSelection(player.getUuid());
		if (selected == null || selected.equals(pos)) {
			PylonBlockEntity.select(player.getUuid(), pos);
			player.sendMessage(Text.literal("Pylon selected. Wrench-click another pylon to send power to it.")
					.formatted(Formatting.AQUA), false);
		} else if (world.getBlockEntity(selected) instanceof PylonBlockEntity source) {
			int count = source.addLink(pos);
			PylonBlockEntity.deselect(player.getUuid());
			player.sendMessage(Text.literal("Linked: power will beam to this pylon (" + count + " link(s)).")
					.formatted(Formatting.GREEN), false);
		} else {
			PylonBlockEntity.select(player.getUuid(), pos);
			player.sendMessage(Text.literal("Pylon selected.").formatted(Formatting.AQUA), false);
		}
		return ItemActionResult.SUCCESS;
	}

	@Override
	@Nullable
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state,
			BlockEntityType<T> type) {
		if (world.isClient) {
			return null;
		}
		return validateTicker(type, ModBlockEntities.PYLON, (w, p, s, be) -> be.tick(w, p, s));
	}
}
