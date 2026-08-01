package com.jrpetty.aztecabyss.engine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

import java.util.Optional;

/**
 * Getting a build out of Minecraft as a file, and back in again.
 *
 * <p>This is the step that turns "a thing I made in creative" into "a thing I can
 * send someone". It is the same machinery the vanilla Structure Block uses, driven
 * from a command instead of a GUI - which matters for two reasons.
 *
 * <p>First, the Structure Block interface caps a save at 48 blocks a side. That
 * limit is in the GUI, not in the format, so a command can take a whole arena in
 * one piece instead of making an author tile their map into a grid and line the
 * offsets up by hand.
 *
 * <p>Second, it saves to exactly where vanilla saves:
 * {@code <world>/generated/<namespace>/structures/<name>.nbt}. That file is
 * already in the right shape and the right place to be dropped straight into a
 * datapack - so the path from "I built something" to "here is a map pack" is a
 * copy, not a conversion.
 *
 * <p>Signs come with it. Sign text lives in the block entity, which structure NBT
 * carries, so every dealer, door and marker in the build travels inside the file
 * for free. The shop layout is part of the map, not a separate manifest to keep in
 * step with it.
 */
public final class MapStore {

    /** Namespace used for saves made in-game, to keep them clear of datapack maps. */
    public static final String LOCAL = "abyss_local";

    /** How far below the author's feet a save reaches, for floors and cellars. */
    private static final int BELOW = 8;
    /** How tall a save is by default. */
    private static final int HEIGHT = 48;

    private MapStore() {
    }

    public static ResourceLocation idFor(String name) {
        String cleaned = name.toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9_/.-]", "_");
        return ResourceLocation.fromNamespaceAndPath(LOCAL, cleaned);
    }

    /** What a save would cover, so the caller can report it before doing it. */
    public static BlockPos originFor(ServerLevel level, BlockPos centre, int radius) {
        int y = Math.max(level.getMinBuildHeight(), centre.getY() - BELOW);
        return new BlockPos(centre.getX() - radius, y, centre.getZ() - radius);
    }

    public static Vec3i sizeFor(ServerLevel level, BlockPos origin, int radius) {
        int span = radius * 2 + 1;
        int height = Math.min(HEIGHT, level.getMaxBuildHeight() - origin.getY());
        return new Vec3i(span, Math.max(1, height), span);
    }

    /**
     * Captures a region to disk.
     *
     * <p>Air is captured rather than ignored. An arena is mostly the space inside
     * it, and a template that skipped air would place its walls into whatever
     * happened to be standing there already - which is fine on a flat void and
     * wrong everywhere else.
     *
     * @return true if the file was written
     */
    public static boolean save(ServerLevel level, String name, BlockPos centre, int radius) {
        StructureTemplateManager mgr = level.getStructureManager();
        ResourceLocation id = idFor(name);
        StructureTemplate template = mgr.getOrCreate(id);
        BlockPos origin = originFor(level, centre, radius);
        template.fillFromWorld(level, origin, sizeFor(level, origin, radius), true, Blocks.STRUCTURE_VOID);
        template.setAuthor("abyss");
        return mgr.save(id);
    }

    /**
     * Captures an exact box - what the Map Wand selects.
     *
     * <p>Preferred over the radius form for anything real. A radius is a guess
     * that either clips the far corner of a build or drags in a hundred blocks of
     * whatever was standing next to it; two corners are what the author actually
     * meant.
     */
    public static boolean saveRegion(ServerLevel level, String name,
                                     net.minecraft.world.level.levelgen.structure.BoundingBox box) {
        StructureTemplateManager mgr = level.getStructureManager();
        ResourceLocation id = idFor(name);
        StructureTemplate template = mgr.getOrCreate(id);
        BlockPos origin = new BlockPos(box.minX(), box.minY(), box.minZ());
        Vec3i size = new Vec3i(
                box.maxX() - box.minX() + 1,
                box.maxY() - box.minY() + 1,
                box.maxZ() - box.minZ() + 1);
        template.fillFromWorld(level, origin, size, true, Blocks.STRUCTURE_VOID);
        template.setAuthor("abyss");
        return mgr.save(id);
    }

    /**
     * Places a saved region back into the world, and reports how many markers it
     * brought with it.
     *
     * @return the number of blocks placed, or -1 if there is no such structure
     */
    public static int load(ServerLevel level, String name, BlockPos origin) {
        StructureTemplateManager mgr = level.getStructureManager();
        Optional<StructureTemplate> found = mgr.get(idFor(name));
        if (found.isEmpty()) {
            // Fall back to a fully-qualified id, so datapack maps load by name too.
            ResourceLocation direct = ResourceLocation.tryParse(name);
            if (direct == null) {
                return -1;
            }
            found = mgr.get(direct);
            if (found.isEmpty()) {
                return -1;
            }
        }
        StructureTemplate template = found.get();
        StructurePlaceSettings settings = new StructurePlaceSettings().setIgnoreEntities(false);
        template.placeInWorld(level, origin, origin, settings, RandomSource.create(), 2);
        Vec3i size = template.getSize();
        return size.getX() * size.getY() * size.getZ();
    }

    /** The box a placed structure occupies, for scanning it straight afterwards. */
    public static net.minecraft.world.level.levelgen.structure.BoundingBox boundsOf(
            ServerLevel level, String name, BlockPos origin) {
        Optional<StructureTemplate> found = level.getStructureManager().get(idFor(name));
        if (found.isEmpty()) {
            return null;
        }
        Vec3i size = found.get().getSize();
        return new net.minecraft.world.level.levelgen.structure.BoundingBox(
                origin.getX(), origin.getY(), origin.getZ(),
                origin.getX() + size.getX(), origin.getY() + size.getY(), origin.getZ() + size.getZ());
    }
}
