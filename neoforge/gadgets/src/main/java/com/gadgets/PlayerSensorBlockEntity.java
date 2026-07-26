package com.gadgets;

import java.util.function.Predicate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Emits a full redstone signal while a matching entity is within range. The
 * target is tunable: point it at players, monsters, animals, all living things,
 * or a single mob type (via a spawn egg). See {@link PlayerSensorBlock}.
 */
public class PlayerSensorBlockEntity extends BlockEntity {
    public static final int MIN_RADIUS = 2;
    public static final int MAX_RADIUS = 24;
    private static final int INTERVAL = 10;

    /** Built-in modes cycled with an empty hand. */
    public static final String PLAYERS = "players";
    public static final String MONSTERS = "monsters";
    public static final String ANIMALS = "animals";
    public static final String ALL = "all";

    /** One of the mode constants above, or a mob type id like "minecraft:creeper". */
    private String target = PLAYERS;
    /** Detection radius in blocks, adjustable in the sensor's screen. */
    private int radius = 8;

    public PlayerSensorBlockEntity(BlockPos pos, BlockState state) {
        super(Gadgets.PLAYER_SENSOR_BE.get(), pos, state);
    }

    public String getTarget() {
        return target;
    }

    public int getRadius() {
        return radius;
    }

    public void setRadius(int radius) {
        this.radius = Math.max(MIN_RADIUS, Math.min(MAX_RADIUS, radius));
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    public void setTarget(String target) {
        this.target = target;
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    public static final String[] MODES = {PLAYERS, MONSTERS, ANIMALS, ALL};

    /** Index of the current built-in mode, or -1 for a specific mob type. */
    public int modeIndex() {
        for (int i = 0; i < MODES.length; i++) {
            if (MODES[i].equals(target)) {
                return i;
            }
        }
        return -1;
    }

    /** Set one of the four built-in modes by index (screen buttons). */
    public void setModeIndex(int index) {
        if (index >= 0 && index < MODES.length) {
            setTarget(MODES[index]);
        }
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
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
            case PLAYERS -> e -> e instanceof Player p && !p.isSpectator();
            case MONSTERS -> e -> e instanceof Monster;
            case ANIMALS -> e -> e instanceof Animal;
            case ALL -> e -> e instanceof LivingEntity && !e.isSpectator();
            default -> e -> EntityType.getKey(e.getType()).toString().equals(target);
        };
    }

    public static void tick(Level level, BlockPos pos, BlockState state, PlayerSensorBlockEntity be) {
        if (level.getGameTime() % INTERVAL != 0L) {
            return;
        }

        AABB area = new AABB(pos).inflate(be.radius);
        int power = Math.min(15, level.getEntitiesOfClass(Entity.class, area, be.predicate()).size());

        if (state.getValue(PlayerSensorBlock.POWER) != power) {
            level.setBlock(pos, state.setValue(PlayerSensorBlock.POWER, power), Block.UPDATE_ALL);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("Target", target);
        tag.putInt("Radius", radius);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Target")) {
            target = tag.getString("Target");
        }
        if (tag.contains("Radius")) {
            radius = Math.max(MIN_RADIUS, Math.min(MAX_RADIUS, tag.getInt("Radius")));
        }
    }
}
