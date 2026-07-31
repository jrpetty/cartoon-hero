package com.gadgets;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
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
    /** Full redstone — counting past this changes nothing. */
    private static final int MAX_POWER = 15;

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

    /**
     * The mob type a custom target names, looked up once per change of target
     * rather than rebuilt from a string for every entity in range on every scan.
     */
    private EntityType<?> exactType;
    private String exactTypeFor;

    private EntityType<?> exactType() {
        if (!target.equals(exactTypeFor)) {
            exactTypeFor = target;
            ResourceLocation id = ResourceLocation.tryParse(target);
            exactType = id == null ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
        }
        return exactType;
    }

    /**
     * How many matching entities are in range, capped at full redstone.
     *
     * <p>Each mode asks the level for the narrowest class it can. That matters:
     * a sweep for {@code Entity.class} hands back every arrow, dropped item and
     * XP orb in the box for the predicate to throw away, and a busy base has far
     * more of those than it has mobs. Watching for players skips the world scan
     * altogether — the server already keeps a list of them.
     */
    private int count(Level level, BlockPos pos) {
        AABB area = new AABB(pos).inflate(radius);
        switch (target) {
            case PLAYERS: {
                int found = 0;
                for (Player player : level.players()) {
                    if (!player.isSpectator() && area.intersects(player.getBoundingBox()) && ++found >= MAX_POWER) {
                        break;
                    }
                }
                return found;
            }
            case MONSTERS:
                return level.getEntitiesOfClass(Monster.class, area, e -> true).size();
            case ANIMALS:
                return level.getEntitiesOfClass(Animal.class, area, e -> true).size();
            case ALL:
                return level.getEntitiesOfClass(LivingEntity.class, area, e -> !e.isSpectator()).size();
            default: {
                EntityType<?> want = exactType();
                if (want == null) {
                    return 0; // an unknown id matches nothing, and costs nothing to check
                }
                return level.getEntitiesOfClass(Entity.class, area, e -> e.getType() == want).size();
            }
        }
    }

    public static void tick(Level level, BlockPos pos, BlockState state, PlayerSensorBlockEntity be) {
        if (!TickPhase.due(level, pos, INTERVAL)) {
            return;
        }

        int power = Math.min(MAX_POWER, be.count(level, pos));

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
