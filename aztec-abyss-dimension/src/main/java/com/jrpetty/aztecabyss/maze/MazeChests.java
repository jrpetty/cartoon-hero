package com.jrpetty.aztecabyss.maze;

import com.jrpetty.aztecabyss.config.AbyssConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * Caches scattered through the corridors, moved every night.
 *
 * <p>The maze had exactly one thing in it worth walking toward, and that was the
 * exit. Everything between the Glade and the way out was corridor you crossed as
 * fast as you could, which makes exploring it strictly a cost - and a maze nobody
 * explores is a corridor with a puzzle at the end.
 *
 * <p>Chests give the space a second reason to exist. They are placed fresh with
 * each night's reshape, so yesterday's map of where things were is worth exactly
 * nothing, and a Runner choosing between the known route and the unknown one is
 * making a real decision rather than an obedient one.
 *
 * <p>They are deliberately weighted toward dead ends. A cache on the way to the
 * exit is free; a cache down a passage that goes nowhere costs you the walk back,
 * and the walk back is where the Grievers are.
 */
public final class MazeChests {

    /** How many go out each night. */
    private static final int COUNT = 28;
    /** Attempts per chest before giving up on finding it a home. */
    private static final int TRIES = 40;

    private MazeChests() {
    }

    /**
     * Clears yesterday's chests and scatters today's.
     *
     * <p>Only tracks what it placed rather than sweeping the map for chests, so a
     * chest a player put down is not something this deletes. Players cannot place
     * chests in the maze, but that is a rule that could change and this should not
     * quietly become destructive if it does.
     */
    private static final List<BlockPos> PLACED = new ArrayList<>();

    public static void reshuffle(ServerLevel level, long daySeed) {
        for (BlockPos old : PLACED) {
            if (level.getBlockState(old).is(Blocks.CHEST)) {
                level.setBlock(old, Blocks.AIR.defaultBlockState(), 2);
            }
        }
        PLACED.clear();

        RandomSource rng = RandomSource.create(daySeed * 31L + 0xC4E5);
        int placed = 0;
        for (int attempt = 0; attempt < COUNT * TRIES && placed < COUNT; attempt++) {
            int cellX = rng.nextInt(MazeData.GRID - 4) + 2;
            int cellZ = rng.nextInt(MazeData.GRID - 4) + 2;
            if (MazeData.inGlade(cellX, cellZ)) {
                continue;
            }
            // A cell with one way out is a dead end, which is where a cache earns
            // its keep. Two thirds of them insist on one; the rest go anywhere, so
            // the pattern is not learnable.
            boolean wantDeadEnd = rng.nextInt(3) != 0;
            if (wantDeadEnd && exits(level, cellX, cellZ) > 1) {
                continue;
            }
            int x = MazeData.corridorMin(cellX) + rng.nextInt(2);
            int z = MazeData.corridorMin(cellZ) + rng.nextInt(2);
            BlockPos at = new BlockPos(x, MazeData.FLOOR_Y + 1, z);
            if (!level.getBlockState(at).isAir()) {
                continue;
            }
            level.setBlock(at, Blocks.CHEST.defaultBlockState(), 2);
            fill(level, at, rng, cellX, cellZ);
            PLACED.add(at);
            placed++;
        }
        appleCaches(level, rng);
    }

    /**
     * The apple caches, and the light that gives them away.
     *
     * <p>The golden apple came off the requisition sheet, which is the right call
     * - it was the one line that sold the answer rather than the stock - but a
     * thing you can no longer buy has to be a thing you can find, or it has
     * quietly stopped existing. So a handful of them are laid in the corridors
     * every night, and finding one is meant to be an event.
     *
     * <h2>Why they are not just more chest loot</h2>
     *
     * <p>Twenty-eight chests already hold golden apples at depth, and nobody has
     * ever gone looking for a chest - you open the ones you walk past. A cache
     * you can <em>see</em> is a different thing entirely: a lantern burning at
     * the end of a dead end is visible from the junction, and the whole point is
     * the moment where somebody stops, looks down a side corridor they were
     * going to ignore, and says there is something down there.
     *
     * <p>Deep only. Fourteen cells is far enough that the light is never
     * something you spot from the Glade doorway, and the walk is the price.
     */
    private static final List<BlockPos> CACHES = new ArrayList<>();

    private static void appleCaches(ServerLevel level, RandomSource rng) {
        for (BlockPos old : CACHES) {
            if (level.getBlockState(old).is(Blocks.BARREL)
                    || level.getBlockState(old).is(Blocks.LANTERN)) {
                level.setBlock(old, Blocks.AIR.defaultBlockState(), 2);
            }
        }
        CACHES.clear();

        int want = AbyssConfig.MAZE_APPLE_CACHES.get();
        int centre = (MazeData.GLADE_MIN_CELL + MazeData.GLADE_MAX_CELL) / 2;
        int placed = 0;
        for (int attempt = 0; attempt < want * TRIES && placed < want; attempt++) {
            int cellX = rng.nextInt(MazeData.GRID - 4) + 2;
            int cellZ = rng.nextInt(MazeData.GRID - 4) + 2;
            if (MazeData.inGlade(cellX, cellZ)) {
                continue;
            }
            if (Math.max(Math.abs(cellX - centre), Math.abs(cellZ - centre)) < 14) {
                continue;
            }
            // Dead ends only, with no exception. A cache on a through route is
            // one you find by walking your usual way, which is not finding it.
            if (exits(level, cellX, cellZ) > 1) {
                continue;
            }
            int x = MazeData.corridorMin(cellX);
            int z = MazeData.corridorMin(cellZ);
            BlockPos at = new BlockPos(x, MazeData.FLOOR_Y + 1, z);
            if (!level.getBlockState(at).isAir()) {
                continue;
            }
            level.setBlock(at, Blocks.BARREL.defaultBlockState(), 2);
            if (level.getBlockEntity(at) instanceof Container barrel) {
                // One in eight is the enchanted one. Rare enough that the story
                // is about that specific morning rather than about barrels.
                barrel.setItem(0, new ItemStack(rng.nextInt(8) == 0
                        ? Items.ENCHANTED_GOLDEN_APPLE : Items.GOLDEN_APPLE));
            }
            CACHES.add(at);
            placed++;

            // The tell. Beside it rather than on it, so the barrel is still a
            // shape you have to walk up to and identify.
            BlockPos lamp = at.east();
            if (!level.getBlockState(lamp).isAir()) {
                lamp = at.south();
            }
            if (level.getBlockState(lamp).isAir()) {
                level.setBlock(lamp, Blocks.LANTERN.defaultBlockState(), 2);
                CACHES.add(lamp);
            }
        }
    }

    private static net.minecraft.world.item.Item ironPiece(RandomSource rng) {
        return switch (rng.nextInt(4)) {
            case 0 -> Items.IRON_HELMET;
            case 1 -> Items.IRON_CHESTPLATE;
            case 2 -> Items.IRON_LEGGINGS;
            default -> Items.IRON_BOOTS;
        };
    }

    private static net.minecraft.world.item.Item diamondPiece(RandomSource rng) {
        return switch (rng.nextInt(4)) {
            case 0 -> Items.DIAMOND_HELMET;
            case 1 -> Items.DIAMOND_CHESTPLATE;
            case 2 -> Items.DIAMOND_LEGGINGS;
            default -> Items.DIAMOND_BOOTS;
        };
    }

    /** How many of a cell's four sides are open, read from the world as built. */
    private static int exits(ServerLevel level, int cellX, int cellZ) {
        int open = 0;
        int y = MazeData.FLOOR_Y + 1;
        int cx = MazeData.corridorMin(cellX) + 1;
        int cz = MazeData.corridorMin(cellZ) + 1;
        int half = MazeData.CELL / 2 + 1;
        if (level.getBlockState(new BlockPos(cx, y, cz - half)).isAir()) {
            open++;
        }
        if (level.getBlockState(new BlockPos(cx, y, cz + half)).isAir()) {
            open++;
        }
        if (level.getBlockState(new BlockPos(cx - half, y, cz)).isAir()) {
            open++;
        }
        if (level.getBlockState(new BlockPos(cx + half, y, cz)).isAir()) {
            open++;
        }
        return open;
    }

    /**
     * What is in one.
     *
     * <p>Better the further out it is. A cache twenty cells from the Glade is a
     * lucky find on the way past; one in the far corner is worth the walk, and
     * should be, because the walk is through everything the maze has.
     */
    private static void fill(ServerLevel level, BlockPos at, RandomSource rng, int cellX, int cellZ) {
        BlockEntity be = level.getBlockEntity(at);
        if (!(be instanceof Container chest)) {
            return;
        }
        int centre = (MazeData.GLADE_MIN_CELL + MazeData.GLADE_MAX_CELL) / 2;
        int distance = Math.max(Math.abs(cellX - centre), Math.abs(cellZ - centre));
        int tier = distance > 32 ? 3 : distance > 18 ? 2 : 1;

        List<ItemStack> loot = new ArrayList<>();
        loot.add(new ItemStack(Items.COOKED_BEEF, 2 + rng.nextInt(3 * tier)));
        if (rng.nextInt(2) == 0) {
            loot.add(new ItemStack(Items.TORCH, 4 + rng.nextInt(8)));
        }
        if (rng.nextInt(3) == 0) {
            loot.add(new ItemStack(Items.OAK_SIGN, 2 + rng.nextInt(3)));
        }
        if (tier >= 2 && rng.nextInt(2) == 0) {
            loot.add(new ItemStack(Items.GOLDEN_APPLE, tier - 1));
        }
        if (tier >= 2) {
            loot.add(new ItemStack(Items.IRON_INGOT, 2 + rng.nextInt(4 * tier)));
        }
        // Materials the dimension cannot produce. A cache that only ever holds
        // food is a cache you stop opening; these are the ones that change what
        // the Glade is able to build tomorrow.
        if (rng.nextInt(3) == 0) {
            loot.add(new ItemStack(Items.STRING, 2 + rng.nextInt(4 * tier)));
        }
        if (rng.nextInt(3) == 0) {
            loot.add(new ItemStack(Items.ARROW, 4 + rng.nextInt(6 * tier)));
        }
        if (rng.nextInt(4) == 0) {
            loot.add(new ItemStack(Items.LEATHER, 1 + rng.nextInt(3)));
        }
        if (rng.nextInt(4) == 0) {
            loot.add(new ItemStack(Items.COAL, 3 + rng.nextInt(5 * tier)));
        }
        if (tier >= 2 && rng.nextInt(3) == 0) {
            loot.add(new ItemStack(Items.WHEAT_SEEDS, 2 + rng.nextInt(5)));
        }
        // Armour, because walking four hundred cells to find two steaks is not
        // a reason to walk four hundred cells. Iron is the honest reward for a
        // long trip; diamond is the story you tell about the one time.
        if (tier >= 2 && rng.nextInt(3) == 0) {
            loot.add(new ItemStack(ironPiece(rng)));
        }
        if (tier >= 3) {
            loot.add(new ItemStack(Items.DIAMOND, 1 + rng.nextInt(3)));
            if (rng.nextInt(3) == 0) {
                loot.add(new ItemStack(ironPiece(rng)));
            }
            if (rng.nextInt(8) == 0) {
                loot.add(new ItemStack(diamondPiece(rng)));
            }
            if (rng.nextInt(6) == 0) {
                loot.add(new ItemStack(Items.IRON_SWORD));
            }
            if (rng.nextInt(4) == 0) {
                loot.add(new ItemStack(Items.ENCHANTED_GOLDEN_APPLE));
            }
        }
        // The serum is the one thing in here that can change how a night ends.
        if (rng.nextInt(tier >= 3 ? 3 : 6) == 0) {
            loot.add(MazeSerum.create());
        }

        for (ItemStack stack : loot) {
            int slot = rng.nextInt(chest.getContainerSize());
            for (int i = 0; i < chest.getContainerSize(); i++) {
                int s = (slot + i) % chest.getContainerSize();
                if (chest.getItem(s).isEmpty()) {
                    chest.setItem(s, stack);
                    break;
                }
            }
        }
        chest.setChanged();
    }
}
