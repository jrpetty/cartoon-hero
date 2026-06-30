package com.gadgets;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class PlayerSensorBlockEntity extends BlockEntity {
    private static final double RADIUS = 8.0;
    private static final int INTERVAL = 10;

    public PlayerSensorBlockEntity(BlockPos pos, BlockState state) {
        super(Gadgets.PLAYER_SENSOR_BE, pos, state);
    }

    public static void tick(World world, BlockPos pos, BlockState state, PlayerSensorBlockEntity be) {
        if (world.getTime() % INTERVAL != 0L) {
            return;
        }

        double cx = pos.getX() + 0.5, cy = pos.getY() + 0.5, cz = pos.getZ() + 0.5;
        double radiusSq = RADIUS * RADIUS;
        boolean found = false;
        for (PlayerEntity player : world.getPlayers()) {
            if (!player.isSpectator() && player.squaredDistanceTo(cx, cy, cz) <= radiusSq) {
                found = true;
                break;
            }
        }

        if (state.get(PlayerSensorBlock.POWERED) != found) {
            world.setBlockState(pos, state.with(PlayerSensorBlock.POWERED, found), Block.NOTIFY_ALL);
        }
    }
}
