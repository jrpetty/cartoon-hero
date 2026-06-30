package com.gadgets;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class PlayerSensorBlockEntity extends BlockEntity {
    private static final double RADIUS = 8.0;
    private static final int INTERVAL = 10;

    public PlayerSensorBlockEntity(BlockPos pos, BlockState state) {
        super(Gadgets.PLAYER_SENSOR_BE.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, PlayerSensorBlockEntity be) {
        if (level.getGameTime() % INTERVAL != 0L) {
            return;
        }

        double cx = pos.getX() + 0.5, cy = pos.getY() + 0.5, cz = pos.getZ() + 0.5;
        double radiusSq = RADIUS * RADIUS;
        boolean found = false;
        for (Player player : level.players()) {
            if (!player.isSpectator() && player.distanceToSqr(cx, cy, cz) <= radiusSq) {
                found = true;
                break;
            }
        }

        if (state.getValue(PlayerSensorBlock.POWERED) != found) {
            level.setBlock(pos, state.setValue(PlayerSensorBlock.POWERED, found), Block.UPDATE_ALL);
        }
    }
}
