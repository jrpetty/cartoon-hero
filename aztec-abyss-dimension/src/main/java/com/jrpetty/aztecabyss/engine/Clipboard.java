package com.jrpetty.aztecabyss.engine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Copy, paste, rotate, mirror - and undo.
 *
 * <p>The most tedious thing left in authoring was symmetry. An arena worth playing
 * usually has four corners that answer each other, and building the same corner
 * four times by hand is hours of work that teaches nobody anything. Every serious
 * world-editing tool solves this the same way and there is no reason to be clever
 * about it.
 *
 * <p>All of it rides on {@link StructureTemplate}, the same machinery that saves
 * maps to disk. A clipboard is a template that was never written to a file, and
 * rotation and mirroring are settings vanilla already applies when it places
 * structures. That is worth stating because it means paste behaves <em>exactly</em>
 * like loading a map does - stairs turn the right way, signs keep their text - with
 * no separate code path to disagree about corner cases.
 *
 * <p>Undo works the same way in reverse: before anything is pasted, the region it
 * is about to overwrite is captured into another template. Undo is then just a
 * paste of the old contents. One level deep, which is the level that matters -
 * almost every mistake is noticed immediately.
 */
public final class Clipboard {

    private record Held(StructureTemplate template, Vec3i size) {
    }

    private static final Map<UUID, Held> CLIP = new HashMap<>();
    private static final Map<UUID, Held> UNDO = new HashMap<>();
    private static final Map<UUID, BlockPos> UNDO_AT = new HashMap<>();

    /** Refuses selections large enough to stall a server. */
    public static final long MAX_VOLUME = 2_000_000L;

    private Clipboard() {
    }

    public static boolean has(ServerPlayer player) {
        return CLIP.containsKey(player.getUUID());
    }

    public static Vec3i sizeOf(ServerPlayer player) {
        Held held = CLIP.get(player.getUUID());
        return held == null ? Vec3i.ZERO : held.size();
    }

    /** Captures a region into this player's clipboard. */
    public static boolean copy(ServerLevel level, ServerPlayer player, BoundingBox box) {
        Vec3i size = new Vec3i(
                box.maxX() - box.minX() + 1,
                box.maxY() - box.minY() + 1,
                box.maxZ() - box.minZ() + 1);
        if ((long) size.getX() * size.getY() * size.getZ() > MAX_VOLUME) {
            return false;
        }
        StructureTemplate template = new StructureTemplate();
        template.fillFromWorld(level, new BlockPos(box.minX(), box.minY(), box.minZ()),
                size, true, Blocks.STRUCTURE_VOID);
        CLIP.put(player.getUUID(), new Held(template, size));
        return true;
    }

    /**
     * Pastes the clipboard with the given orientation, snapshotting first.
     *
     * @return the number of blocks the pasted region covers, or -1 with nothing held
     */
    public static int paste(ServerLevel level, ServerPlayer player, BlockPos at,
                            Rotation rotation, Mirror mirror) {
        Held held = CLIP.get(player.getUUID());
        if (held == null) {
            return -1;
        }
        // Snapshot what is about to be lost. Rotation can change the footprint, so
        // the snapshot covers the larger of the two dimensions on both axes rather
        // than assuming the paste lands in the same shape it was copied from.
        int span = Math.max(held.size().getX(), held.size().getZ());
        Vec3i undoSize = new Vec3i(span, held.size().getY(), span);
        StructureTemplate before = new StructureTemplate();
        before.fillFromWorld(level, at, undoSize, true, Blocks.STRUCTURE_VOID);
        UNDO.put(player.getUUID(), new Held(before, undoSize));
        UNDO_AT.put(player.getUUID(), at.immutable());

        StructurePlaceSettings settings = new StructurePlaceSettings()
                .setRotation(rotation)
                .setMirror(mirror)
                .setIgnoreEntities(false);
        held.template().placeInWorld(level, at, at, settings, RandomSource.create(), 2);
        return held.size().getX() * held.size().getY() * held.size().getZ();
    }

    /** Puts back whatever the last paste covered. */
    public static boolean undo(ServerLevel level, ServerPlayer player) {
        Held held = UNDO.get(player.getUUID());
        BlockPos at = UNDO_AT.get(player.getUUID());
        if (held == null || at == null) {
            return false;
        }
        held.template().placeInWorld(level, at, at,
                new StructurePlaceSettings().setIgnoreEntities(false), RandomSource.create(), 2);
        // One level deep on purpose. Chaining undos would need a stack of full
        // region snapshots, and the mistake worth catching is always the last one.
        UNDO.remove(player.getUUID());
        UNDO_AT.remove(player.getUUID());
        return true;
    }

    public static Rotation rotationOf(int degrees) {
        return switch (((degrees % 360) + 360) % 360) {
            case 90 -> Rotation.CLOCKWISE_90;
            case 180 -> Rotation.CLOCKWISE_180;
            case 270 -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }

    public static Mirror mirrorOf(String axis) {
        return switch (axis == null ? "" : axis.toLowerCase(java.util.Locale.ROOT)) {
            case "x", "front_back" -> Mirror.FRONT_BACK;
            case "z", "left_right" -> Mirror.LEFT_RIGHT;
            default -> Mirror.NONE;
        };
    }

    public static void forget(UUID id) {
        CLIP.remove(id);
        UNDO.remove(id);
        UNDO_AT.remove(id);
    }
}
