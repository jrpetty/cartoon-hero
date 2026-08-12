package com.jrpetty.aztecabyss.maze;

import com.jrpetty.aztecabyss.worldgen.Deco;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/**
 * The night raid: every few nights, the wall itself is the fight.
 *
 * <p>Standing inside the Glade at night used to be perfectly safe, which made
 * the night a wait for everyone who was not a Runner caught outside. On raid
 * nights it is not a wait. A pack of Grievers picks a stretch of the wall and
 * batters it - audibly, for most of a minute, before anything gives - and if
 * nobody does anything about it the wall opens and the night comes inside.
 *
 * <p>The defence has two jobs, which is the point: fighters can go over or
 * through the doors' arrow slits at nothing, or wait at the breach; Builders
 * can plug the hole with blocks, which genuinely holds - a plugged breach
 * takes renewed battering before it opens again. Kill the pack and the raid
 * is over; every kill pays the standing Griever bounty, and a wall that was
 * never opened pays a little extra to everyone at dawn.
 *
 * <h2>Raid nights are part of the calendar</h2>
 *
 * <p>Like the doors and the portals, raid nights depend on the day number and
 * nothing else: a small probe on night two, then full raids at gaps of two to
 * five nights, drawn deterministically, the same in every game. Veterans get
 * to learn "day four is a raid night" and be standing on the wall with
 * torches when the first blow lands - which is better play than a surprise,
 * because the surprise only works once.
 *
 * <h2>The wall always comes back</h2>
 *
 * <p>At dawn the ring is rebuilt whole, whatever state the night left it in.
 * The Glade wall is seeded, deterministic construction, so rebuilding it is
 * exact - no drifting damage across days, no permanently ruined safe ground,
 * and a restart mid-raid costs the raid rather than the wall.
 */
public final class MazeRaid {

    private MazeRaid() {
    }

    /** Persistent-data tag marking a raider; also carried by every Griever tag. */
    public static final String TAG = "AztecRaider";

    private static final int SEED = 0xBA1D;
    /** Seconds into the night before the first blow: dread first, then noise. */
    private static final int START_SECONDS = 75;
    /** Seconds between blows on the wall. */
    private static final int BANG_SECONDS = 4;
    /** Blows before the wall opens: about a minute of warning. */
    private static final int BANGS_TO_BREACH = 14;
    /** Blows a plugged breach withstands before it is torn open again. */
    private static final int REPAIR_BANGS = 6;

    // Transient on purpose. A restart mid-raid forfeits the raid; the dawn
    // rebuild squares the wall away regardless.
    private static int raidDay = -1;
    private static boolean active;
    private static boolean breached;
    private static boolean everBreached;
    private static int face;
    private static int alongCell;
    private static int secondsToBang;
    private static int bangs;
    private static boolean needRepair;

    // ------------------------------------------------------------------
    // The calendar
    // ------------------------------------------------------------------

    /**
     * The probe: a small early raid, two nights in. Two raiders, a hole half
     * the size, a smaller purse. It exists to teach - the group meets the
     * battering, the cracks, the plugging, while the stakes are still low -
     * so that night four's full raid finds them drilled rather than surprised.
     */
    private static final int PROBE_DAY = 2;

    /** Gap to the next raid night: two to five nights, fixed per step. */
    private static int gap(int k) {
        return 2 + (Deco.hash(SEED, k, 0x9A9, 0x5EED) & 0x7FFFFFFF) % 4;
    }

    /** Whether this day's night is a raid night. Same answer in every game. */
    public static boolean raidNight(int day) {
        if (day == PROBE_DAY) {
            return true;
        }
        int next = gap(0);
        int k = 1;
        while (next < day) {
            next += gap(k++);
        }
        return next == day;
    }

    /** True while the running raid is the small day-two probe. */
    private static boolean probe() {
        return raidDay == PROBE_DAY;
    }

    // ------------------------------------------------------------------
    // The tick
    // ------------------------------------------------------------------

    /** Called once a second from the maze runtime. */
    public static void tick(ServerLevel level, MazeClock clock) {
        if (!clock.isNight()) {
            if (active || needRepair) {
                dawnRepair(level);
            }
            return;
        }
        int day = clock.day();
        if (!active) {
            if (raidDay == day || !raidNight(day)) {
                return;
            }
            int intoNight = (clock.phase() - MazeClock.dayTicks()) / 20;
            if (intoNight >= START_SECONDS) {
                start(level, day);
            }
            return;
        }
        List<Mob> raiders = raiders(level);
        if (raiders.isEmpty()) {
            endEarly(level);
            return;
        }
        if (breached) {
            drive(level, raiders);
            if (plugged(level)) {
                // The Builders got it shut. It holds - for a while.
                breached = false;
                bangs = BANGS_TO_BREACH - REPAIR_BANGS;
                broadcast(level, "§6✦ The breach is plugged. §7They are still out there.");
            }
            return;
        }
        if (--secondsToBang > 0) {
            return;
        }
        secondsToBang = BANG_SECONDS;
        bang(level);
    }

    /** Clears raid state at game end so the next game's calendar starts clean. */
    public static void reset(ServerLevel level) {
        if (active || needRepair) {
            dawnRepair(level);
        }
        raidDay = -1;
    }

    // ------------------------------------------------------------------
    // The assault
    // ------------------------------------------------------------------

    private static void start(ServerLevel level, int day) {
        active = true;
        breached = false;
        everBreached = false;
        bangs = 0;
        secondsToBang = BANG_SECONDS;
        raidDay = day;
        needRepair = true;
        face = (Deco.hash(SEED, day, 0xFACE, 1) & 0x7FFFFFFF) % 4;
        alongCell = pickCell(day);

        int count = probe() ? 2 : Math.min(6, 2 + level.players().size());
        int made = 0;
        for (int i = 0; i < count * 4 && made < count; i++) {
            BlockPos spot = outsideSpot(level, day, i);
            if (spot != null && Griever.raiderAt(level, spot) != null) {
                made++;
            }
        }

        BlockPos mid = segmentCentre();
        level.playSound(null, mid, SoundEvents.WARDEN_ROAR, SoundSource.HOSTILE,
                probe() ? 2.0F : 3.0F, 0.45F);
        broadcast(level, probe()
                ? "§c⚠ Something is testing the " + faceName() + " wall of the Glade."
                : "§4§l⚠ THE WALL. §r§cSomething is battering the "
                        + faceName() + " wall of the Glade.");
        for (ServerPlayer p : level.players()) {
            level.playSound(null, p.blockPosition(), SoundEvents.BELL_BLOCK,
                    SoundSource.BLOCKS, 1.4F, 0.6F);
        }
    }

    /** One blow: noise, dust, and - eventually - a hole. */
    private static void bang(ServerLevel level) {
        BlockPos mid = segmentCentre();
        level.playSound(null, mid, SoundEvents.ZOMBIE_ATTACK_IRON_DOOR,
                SoundSource.HOSTILE, 3.0F, 0.5F);
        level.playSound(null, mid, SoundEvents.ANVIL_LAND,
                SoundSource.BLOCKS, 1.6F, 0.4F);
        level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(
                        net.minecraft.core.particles.ParticleTypes.BLOCK,
                        Blocks.DEEPSLATE_BRICKS.defaultBlockState()),
                mid.getX() + 0.5, mid.getY() + 1.5, mid.getZ() + 0.5,
                30, 1.6, 1.2, 1.6, 0.02);
        bangs++;
        // The damage shows before it opens: cracked courses, so a defender who
        // walks the wall can see where tonight's trouble is without being told.
        if (bangs == BANGS_TO_BREACH / 2) {
            crack(level, 2);
            broadcast(level, "§c✦ The wall is cracking. §7Builders to the "
                    + faceName() + " wall.");
        } else if (bangs == BANGS_TO_BREACH - 3) {
            crack(level, 0);
        }
        if (bangs >= BANGS_TO_BREACH) {
            open(level);
        }
    }

    /** Replaces a course of the segment with cracked stone. */
    private static void crack(ServerLevel level, int course) {
        BlockState cracked = Blocks.CRACKED_DEEPSLATE_BRICKS.defaultBlockState();
        for (BlockPos at : segment(course, course + 1)) {
            if (!level.getBlockState(at).isAir()) {
                level.setBlock(at, cracked, 2);
            }
        }
    }

    /** Tears the hole open. */
    private static void open(ServerLevel level) {
        breached = true;
        everBreached = true;
        for (BlockPos at : hole()) {
            level.setBlock(at, Blocks.AIR.defaultBlockState(), 2);
        }
        BlockPos mid = segmentCentre();
        level.playSound(null, mid, SoundEvents.GENERIC_EXPLODE.value(),
                SoundSource.BLOCKS, 2.0F, 0.7F);
        level.playSound(null, mid, SoundEvents.WARDEN_EMERGE, SoundSource.HOSTILE, 2.5F, 0.6F);
        broadcast(level, "§4§l✦ THE WALL IS BREACHED. §r§cThey are coming inside.");
    }

    /** Points the pack through the hole at whoever is nearest inside. */
    private static void drive(ServerLevel level, List<Mob> raiders) {
        for (Mob raider : raiders) {
            if (raider.getTarget() != null && raider.getTarget().isAlive()) {
                continue;
            }
            Player prey = level.getNearestPlayer(raider, 96);
            if (prey instanceof ServerPlayer sp && !sp.isCreative() && !sp.isSpectator()) {
                raider.setTarget(sp);
            }
        }
    }

    /**
     * Whether a player may set a block here right now. The wall ring sits just
     * outside the Glade's build zone, where placement is normally refused with
     * "signs and torches only" - but plugging the breach is the Builders' whole
     * job on a raid night, so the hole itself accepts blocks while the raid runs.
     */
    public static boolean placeable(BlockPos at) {
        if (!active) {
            return false;
        }
        for (BlockPos h : hole()) {
            if (h.equals(at)) {
                return true;
            }
        }
        return false;
    }

    /** True when no cell of the hole is open air any more. */
    private static boolean plugged(ServerLevel level) {
        for (BlockPos at : hole()) {
            if (level.getBlockState(at).isAir()) {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------
    // Endings
    // ------------------------------------------------------------------

    /** The pack is dead before dawn. */
    private static void endEarly(ServerLevel level) {
        active = false;
        if (!everBreached) {
            // The wall held the whole way through: a little extra into the pot
            // for everyone who stood the night, on top of the kill bounties.
            int purse = probe() ? 3 : 5;
            MazeOrders orders = MazeOrders.get(level);
            for (ServerPlayer p : level.players()) {
                orders.addBonus(p.getUUID(), purse);
            }
            broadcast(level, "§a§l✦ THE WALL HELD. §r§7+" + purse + " credits each into the pot.");
        } else {
            broadcast(level, "§a✦ The pack is dead. §7The hole stands until dawn.");
        }
    }

    /**
     * Dawn: the ring is rebuilt whole. The build is seeded and deterministic,
     * so this restores exactly what stood before - including re-punching the
     * four doors.
     */
    private static void dawnRepair(ServerLevel level) {
        boolean announce = needRepair;
        active = false;
        breached = false;
        needRepair = false;
        MazeBuilder.gladeWall(level);
        if (announce) {
            broadcast(level, "§7The wall stands whole again with the light.");
        }
    }

    // ------------------------------------------------------------------
    // Geometry
    // ------------------------------------------------------------------

    /** Ring lines, matching MazeBuilder.gladeWall exactly. */
    private static int loLine() {
        return MazeData.GLADE_MIN_CELL * MazeData.CELL - 1;
    }

    private static int hiLine() {
        return (MazeData.GLADE_MAX_CELL + 1) * MazeData.CELL;
    }

    /**
     * Picks the cell along the face the pack attacks - never the door cell or
     * its neighbours, because a raid that batters an opening is not a raid.
     */
    private static int pickCell(int day) {
        int door = switch (face) {
            case 0 -> 48;   // north door cell x
            case 1 -> 48;   // east door cell z
            case 2 -> 47;   // south door cell x
            default -> 47;  // west door cell z
        };
        List<Integer> cells = new ArrayList<>();
        for (int c = MazeData.GLADE_MIN_CELL + 1; c <= MazeData.GLADE_MAX_CELL - 1; c++) {
            if (Math.abs(c - door) > 1) {
                cells.add(c);
            }
        }
        return cells.get((Deco.hash(SEED, day, 0xA10, 2) & 0x7FFFFFFF) % cells.size());
    }

    /** Where the attacked stretch of wall runs, one cell wide. */
    private static List<BlockPos> segment(int fromCourse, int toCourse) {
        List<BlockPos> out = new ArrayList<>();
        int a0 = alongCell * MazeData.CELL;
        for (int a = a0; a < a0 + MazeData.CELL; a++) {
            for (int y = MazeData.WALL_BASE_Y + fromCourse;
                    y <= MazeData.WALL_BASE_Y + toCourse; y++) {
                out.add(onFace(a, y));
            }
        }
        return out;
    }

    /**
     * The hole itself, at the foot of the segment: four wide and three high
     * on a full raid, two by two on the probe - a crack you defend with a
     * handful of blocks, not a gate.
     */
    private static List<BlockPos> hole() {
        List<BlockPos> out = new ArrayList<>();
        int wide = probe() ? 2 : 4;
        int high = probe() ? 1 : 2;
        int a0 = alongCell * MazeData.CELL + (probe() ? 2 : 1);
        for (int a = a0; a < a0 + wide; a++) {
            for (int y = MazeData.WALL_BASE_Y; y <= MazeData.WALL_BASE_Y + high; y++) {
                out.add(onFace(a, y));
            }
        }
        return out;
    }

    private static BlockPos onFace(int along, int y) {
        return switch (face) {
            case 0 -> new BlockPos(along, y, loLine());
            case 1 -> new BlockPos(hiLine(), y, along);
            case 2 -> new BlockPos(along, y, hiLine());
            default -> new BlockPos(loLine(), y, along);
        };
    }

    private static BlockPos segmentCentre() {
        return onFace(alongCell * MazeData.CELL + 3, MazeData.WALL_BASE_Y + 1);
    }

    /** A standable spot in the corridors outside the attacked stretch. */
    private static BlockPos outsideSpot(ServerLevel level, int day, int i) {
        int h = Deco.hash(SEED, day, 0x50F, i) & 0x7FFFFFFF;
        int along = alongCell * MazeData.CELL + h % MazeData.CELL;
        int out = 2 + (h >> 4) % 5;
        int y = MazeData.WALL_BASE_Y;
        BlockPos at = switch (face) {
            case 0 -> new BlockPos(along, y, loLine() - out);
            case 1 -> new BlockPos(hiLine() + out, y, along);
            case 2 -> new BlockPos(along, y, hiLine() + out);
            default -> new BlockPos(loLine() - out, y, along);
        };
        // Two blocks of air over the guaranteed flat floor, the same test the
        // corridor spawner uses.
        if (level.getBlockState(at).isAir() && level.getBlockState(at.above()).isAir()) {
            return at;
        }
        return null;
    }

    private static String faceName() {
        return switch (face) {
            case 0 -> "north";
            case 1 -> "east";
            case 2 -> "south";
            default -> "west";
        };
    }

    private static List<Mob> raiders(ServerLevel level) {
        int lo = loLine() - 24;
        int hi = hiLine() + 24;
        List<Mob> out = level.getEntitiesOfClass(Mob.class,
                new AABB(lo, MazeData.FLOOR_Y - 4, lo, hi, MazeData.WALL_TOP_Y + 4, hi),
                m -> m.getPersistentData().getBoolean(TAG) && m.isAlive());
        return out;
    }

    private static void broadcast(ServerLevel level, String msg) {
        for (ServerPlayer p : level.players()) {
            p.displayClientMessage(Component.literal(msg), false);
        }
    }
}
