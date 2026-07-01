package com.gadgets;

import java.util.function.Predicate;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Emits a full redstone signal while a matching entity is within range. The
 * target is tunable: point it at players, monsters, animals, all living things,
 * or a single mob type (via a spawn egg). See {@link PlayerSensorBlock}.
 */
public class PlayerSensorBlockEntity extends BlockEntity {
    private static final double RADIUS = 8.0;
    private static final int INTERVAL = 10;

    /** Built-in modes cycled with an empty hand. */
    public static final String PLAYERS = "players";
    public static final String MONSTERS = "monsters";
    public static final String ANIMALS = "animals";
    public static final String ALL = "all";

    /** One of the mode constants above, or a mob type id like "minecraft:creeper". */
    private String target = PLAYERS;

    public PlayerSensorBlockEntity(BlockPos pos, BlockState state) {
        super(Gadgets.PLAYER_SENSOR_BE, pos, state);
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
        markDirty();
        if (world != null) {
            world.updateListeners(pos, getCachedState(), getCachedState(), Block.NOTIFY_ALL);
        }
    }

    /** Cycle through the four built-in modes. */
    public String cycleMode() {
        String next = switch (target) {
            case PLAYERS -> MONSTERS;
            case MONSTERS -> ANIMALS;
            case ANIMALS -> ALL;
            default -> PLAYERS;
        };
        setTarget(next);
        return next;
    }

    private Predicate<Entity> predicate() {
        return switch (target) {
            case PLAYERS -> e -> e instanceof PlayerEntity p && !p.isSpectator();
            case MONSTERS -> e -> e instanceof Monster;
            case ANIMALS -> e -> e instanceof AnimalEntity;
            case ALL -> e -> e instanceof LivingEntity && !e.isSpectator();
            default -> e -> EntityType.getId(e.getType()).toString().equals(target);
        };
    }

    public static void tick(World world, BlockPos pos, BlockState state, PlayerSensorBlockEntity be) {
        if (world.getTime() % INTERVAL != 0L) {
            return;
        }

        Box area = new Box(pos).expand(RADIUS);
        boolean found = !world.getEntitiesByClass(Entity.class, area, be.predicate()).isEmpty();

        if (state.get(PlayerSensorBlock.POWERED) != found) {
            world.setBlockState(pos, state.with(PlayerSensorBlock.POWERED, found), Block.NOTIFY_ALL);
        }
    }

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        nbt.putString("Target", target);
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        if (nbt.contains("Target")) {
            target = nbt.getString("Target");
        }
    }
}
