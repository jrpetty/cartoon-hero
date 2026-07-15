package com.gadgets;

import java.util.function.Predicate;

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
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Rings and warns nearby players when a chosen target enters range — a
 * redstone-free tripwire. Tune the target like the Entity Sensor (spawn egg for
 * a specific mob, or empty-hand to cycle players/monsters/animals/all).
 */
public class AlarmBlockEntity extends BlockEntity {
    private static final double DETECT_RADIUS = 8.0;
    private static final double WARN_RADIUS = 24.0;
    private static final int INTERVAL = 10;
    private static final int REARM_TICKS = 60;

    public static final String PLAYERS = "players";
    public static final String MONSTERS = "monsters";
    public static final String ANIMALS = "animals";
    public static final String ALL = "all";

    private String target = MONSTERS;
    private boolean wasPresent = false;
    private long lastAlarm = -1000L;

    public AlarmBlockEntity(BlockPos pos, BlockState state) {
        super(Gadgets.ALARM_BE, pos, state);
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
        markDirty();
    }

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

    public static void tick(World world, BlockPos pos, BlockState state, AlarmBlockEntity be) {
        if (world.getTime() % INTERVAL != 0L) {
            return;
        }
        Box area = new Box(pos).expand(DETECT_RADIUS);
        boolean present = !world.getEntitiesByClass(Entity.class, area, be.predicate()).isEmpty();

        if (present && !be.wasPresent && world.getTime() - be.lastAlarm >= REARM_TICKS) {
            be.lastAlarm = world.getTime();
            be.trigger(world, pos);
        }
        be.wasPresent = present;
    }

    private void trigger(World world, BlockPos pos) {
        world.playSound(null, pos, SoundEvents.BLOCK_BELL_USE, SoundCategory.BLOCKS, 2.0F, 0.7F);
        world.playSound(null, pos, SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), SoundCategory.BLOCKS, 1.5F, 1.8F);
        Text message = Text.literal("⚠ Alarm: " + target + " detected!");
        double warnSq = WARN_RADIUS * WARN_RADIUS;
        for (PlayerEntity player : world.getPlayers()) {
            if (player.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= warnSq) {
                player.sendMessage(message, true);
            }
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
