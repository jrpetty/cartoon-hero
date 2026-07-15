package com.gadgets;

import net.minecraft.ChatFormatting;
import java.util.function.Predicate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

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
        super(Gadgets.ALARM_BE.get(), pos, state);
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
        setChanged();
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
            case PLAYERS -> e -> e instanceof Player p && !p.isSpectator();
            case MONSTERS -> e -> e instanceof Monster;
            case ANIMALS -> e -> e instanceof Animal;
            case ALL -> e -> e instanceof LivingEntity && !e.isSpectator();
            default -> e -> EntityType.getKey(e.getType()).toString().equals(target);
        };
    }

    public static void tick(Level level, BlockPos pos, BlockState state, AlarmBlockEntity be) {
        if (level.getGameTime() % INTERVAL != 0L) {
            return;
        }
        AABB area = new AABB(pos).inflate(DETECT_RADIUS);
        boolean present = !level.getEntitiesOfClass(Entity.class, area, be.predicate()).isEmpty();

        if (present && !be.wasPresent && level.getGameTime() - be.lastAlarm >= REARM_TICKS) {
            be.lastAlarm = level.getGameTime();
            be.trigger(level, pos);
        }
        be.wasPresent = present;
    }

    private void trigger(Level level, BlockPos pos) {
        level.playSound(null, pos, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 2.0F, 0.7F);
        level.playSound(null, pos, SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.BLOCKS, 1.5F, 1.8F);
        Component message = Component.literal("⚠ Alarm: " + target + " detected!").withStyle(ChatFormatting.RED);
        double warnSq = WARN_RADIUS * WARN_RADIUS;
        for (Player player : level.players()) {
            if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= warnSq) {
                player.displayClientMessage(message, true);
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("Target", target);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Target")) {
            target = tag.getString("Target");
        }
    }
}
