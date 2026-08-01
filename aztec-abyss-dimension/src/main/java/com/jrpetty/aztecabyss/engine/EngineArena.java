package com.jrpetty.aztecabyss.engine;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.server.level.ServerBossEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * A round-survival game played on a map nobody wrote any code for.
 *
 * <p>This is the join between the two halves of the engine. Up to now a build
 * could be authored, validated, saved and shipped, and a ruleset could be written
 * and reloaded - but nothing ever put the two together and played them. Markers
 * were read and then ignored; rulesets changed what a command printed and nothing
 * else.
 *
 * <p>Everything here comes from data. Where players start is a {@code [Spawn]}
 * sign. Where the horde comes from is every {@code [Horde]} sign. How many arrive,
 * how much health they have, how hard they hit and how long you get between rounds
 * are all read off the ruleset. There is no map-specific logic anywhere in this
 * class, and that is the test of whether the engine is real: if this file ever
 * needs to know the name of a map, the design has failed.
 *
 * <p>Deliberately independent of the existing round system. The hand-built maps
 * keep working exactly as they do while this grows up alongside them, rather than
 * both being half-migrated at once.
 */
public final class EngineArena {

    private static final int SPAWN_INTERVAL_TICKS = 20;

    private static EngineArena current;

    private final ServerLevel level;
    private final String mapName;
    private final Ruleset rules;
    private final BlockPos spawn;
    private final List<Marker> hordes;
    private final BoundingBox bounds;

    private final List<UUID> participants = new ArrayList<>();
    private final List<Mob> alive = new ArrayList<>();
    private final ServerBossEvent bar;
    private final RandomSource rng = RandomSource.create();

    private int round = 0;
    private int leftToSpawn = 0;
    private int breather = 0;
    private boolean running = true;

    private EngineArena(ServerLevel level, String mapName, Ruleset rules,
                        BlockPos spawn, List<Marker> hordes, BoundingBox bounds) {
        this.level = level;
        this.mapName = mapName;
        this.rules = rules;
        this.spawn = spawn;
        this.hordes = hordes;
        this.bounds = bounds;
        this.bar = new ServerBossEvent(Component.literal(mapName),
                BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS);
    }

    public static EngineArena active() {
        return current;
    }

    public static boolean isRunning() {
        return current != null && current.running;
    }

    /**
     * Starts a run on whatever is around the caller.
     *
     * @return an error to show the player, or null if it started
     */
    public static String start(ServerLevel level, ServerPlayer player, int radius, String rulesetId) {
        return startIn(level, player, new BoundingBox(
                player.blockPosition().getX() - radius, level.getMinBuildHeight(),
                player.blockPosition().getZ() - radius,
                player.blockPosition().getX() + radius, level.getMaxBuildHeight() - 1,
                player.blockPosition().getZ() + radius), rulesetId);
    }

    /** Runs a game inside an explicit box - what a wand selection plays. */
    public static String startIn(ServerLevel level, ServerPlayer player,
                                 BoundingBox box, String rulesetId) {
        MapScan.Result scan = MapScan.scan(level, box);

        Marker spawnMarker = scan.first("spawn");
        if (spawnMarker == null) {
            return "This map has no [Spawn] marker. Run /arena validate to see what else is missing.";
        }
        if (scan.of("horde").isEmpty()) {
            return "This map has no [Horde] markers, so nothing could ever attack.";
        }
        Ruleset rules = RulesetLoader.byId(rulesetId);

        stop(false);
        current = new EngineArena(level, "Custom Arena", rules,
                spawnMarker.pos(), scan.of("horde"), box);
        current.consumeMarkers(scan);
        current.join(player);
        current.beginRound(1);
        return null;
    }

    /**
     * Deletes the signs that were only ever instructions.
     *
     * <p>A {@code [Horde]} sign nailed to the wall of a map being played is set
     * dressing nobody asked for. The dealers stay, because those are the shop.
     */
    private void consumeMarkers(MapScan.Result scan) {
        for (Marker m : scan.all()) {
            if (m.consumedOnLoad()) {
                level.setBlock(m.pos(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
            }
        }
    }

    private void join(ServerPlayer player) {
        participants.add(player.getUUID());
        bar.addPlayer(player);
        player.teleportTo(level, spawn.getX() + 0.5, spawn.getY() + 1, spawn.getZ() + 0.5,
                java.util.Set.of(), player.getYRot(), 0.0F);
        if (rules.economyEnabled) {
            Currency c = Currency.byId(rules.defaultCurrency);
            c.set(player, c.start());
        }
    }

    public static void stop(boolean announce) {
        if (current == null) {
            return;
        }
        if (announce) {
            for (ServerPlayer p : current.players()) {
                p.displayClientMessage(Component.literal("§7The run is over."), false);
            }
        }
        for (Mob m : current.alive) {
            if (m.isAlive()) {
                m.discard();
            }
        }
        current.bar.removeAllPlayers();
        current.running = false;
        current = null;
    }

    private List<ServerPlayer> players() {
        List<ServerPlayer> out = new ArrayList<>();
        for (UUID id : participants) {
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
            if (p != null) {
                out.add(p);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------
    // The loop
    // ------------------------------------------------------------------

    /**
     * Driven from the level tick, and fussy about which level.
     *
     * <p>Every loaded dimension ticks, so an unguarded call here would run the
     * round loop once per dimension per tick - the breather counting down three
     * times as fast and three mobs arriving where one was asked for, on a server
     * that happens to have the Nether loaded.
     */
    public static void tickActive(ServerLevel level) {
        if (current != null && current.running && current.level.dimension().equals(level.dimension())) {
            current.tick();
        }
    }

    private void tick() {
        List<ServerPlayer> present = players();
        if (present.isEmpty()) {
            stop(false);
            return;
        }
        // Anyone who has died is out. When the last one goes, so does the run.
        present.removeIf(p -> p.isDeadOrDying() || !p.level().dimension().equals(level.dimension()));
        if (present.isEmpty()) {
            for (ServerPlayer p : players()) {
                p.displayClientMessage(Component.literal(
                        "§c§lDOWN. §r§7You reached round §f" + round + "§7."), false);
            }
            stop(false);
            return;
        }

        alive.removeIf(m -> !m.isAlive());

        if (breather > 0) {
            breather--;
            bar.setName(Component.literal("§7Next round in §f"
                    + Math.max(1, breather / 20) + "s"));
            bar.setProgress(1.0F - (breather / (float) Math.max(1, rules.breatherFor(round))));
            if (breather == 0) {
                beginRound(round + 1);
            }
            return;
        }

        if (leftToSpawn > 0 && level.getGameTime() % SPAWN_INTERVAL_TICKS == 0
                && alive.size() < rules.concurrentCap) {
            spawnOne();
        }
        if (leftToSpawn <= 0 && alive.isEmpty()) {
            endRound();
            return;
        }
        bar.setName(Component.literal("§c§lROUND " + round + " §r§7— §f"
                + (alive.size() + leftToSpawn) + "§7 left"));
        int total = Math.max(1, rules.countFor(round));
        bar.setProgress(Math.max(0.0F, Math.min(1.0F, (alive.size() + leftToSpawn) / (float) total)));
    }

    private void beginRound(int n) {
        round = n;
        leftToSpawn = rules.countFor(n);
        breather = 0;
        for (ServerPlayer p : players()) {
            p.displayClientMessage(Component.literal("§c§lROUND " + n), true);
            level.playSound(null, p.blockPosition(), SoundEvents.WARDEN_ROAR,
                    SoundSource.HOSTILE, 0.5F, 1.4F);
        }
    }

    private void endRound() {
        if (!rules.endless && rules.finalRound > 0 && round >= rules.finalRound) {
            for (ServerPlayer p : players()) {
                p.displayClientMessage(Component.literal(
                        "§6§lCLEARED. §r§7Round §f" + round + "§7 was the last."), false);
            }
            stop(false);
            return;
        }
        breather = Math.max(1, rules.breatherFor(round));
    }

    /**
     * Puts one mob into the world at one of the map's horde markers.
     *
     * <p>Attributes are set from the ruleset and then scaled by the round curve,
     * in that order, so a mob's own numbers stay meaningful - a husk written as
     * three times a zombie's health is still three times a zombie's health at
     * round forty.
     */
    private void spawnOne() {
        Marker gate = hordes.get(rng.nextInt(hordes.size()));
        Ruleset.MobEntry pick = pickMob();
        if (pick == null) {
            leftToSpawn = 0;
            return;
        }
        var maybeType = EntityType.byString(pick.entityId());
        if (maybeType.isEmpty()) {
            leftToSpawn--;
            return;
        }
        Entity entity = maybeType.get().create(level);
        if (!(entity instanceof Mob mob)) {
            leftToSpawn--;
            return;
        }
        // Just behind the marker, so they walk in rather than appearing on top of you.
        BlockPos at = gate.pos().relative(gate.facing().getOpposite(), 2);
        mob.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, rng.nextFloat() * 360.0F, 0.0F);

        double healthMul = rules.healthMultiplier(round);
        double damageMul = rules.damageMultiplier(round);
        setAttr(mob, Attributes.MAX_HEALTH, pick.maxHealth() * healthMul);
        setAttr(mob, Attributes.MOVEMENT_SPEED, pick.speed());
        setAttr(mob, Attributes.ATTACK_DAMAGE, pick.attackDamage() * damageMul);
        mob.setHealth(mob.getMaxHealth());
        equip(mob, pick);
        mob.getPersistentData().putBoolean("aztecabyss_engine_mob", true);
        mob.setPersistenceRequired();

        level.addFreshEntity(mob);
        ServerPlayer target = nearestPlayer(at);
        if (target != null) {
            mob.setTarget(target);
        }
        alive.add(mob);
        leftToSpawn--;
    }

    /** Weighted choice among everything unlocked at this round. */
    private Ruleset.MobEntry pickMob() {
        if (rules.mobs.isEmpty()) {
            // A ruleset with no mob table still has to produce a game.
            return new Ruleset.MobEntry("minecraft:zombie", 1, 1, "grunt",
                    20.0, 0.25, 3.0, "", "");
        }
        int total = 0;
        for (Ruleset.MobEntry m : rules.mobs) {
            if (m.fromRound() <= round) {
                total += m.weight();
            }
        }
        if (total <= 0) {
            return rules.mobs.get(0);
        }
        int roll = rng.nextInt(total);
        for (Ruleset.MobEntry m : rules.mobs) {
            if (m.fromRound() > round) {
                continue;
            }
            roll -= m.weight();
            if (roll < 0) {
                return m;
            }
        }
        return rules.mobs.get(0);
    }

    private void equip(Mob mob, Ruleset.MobEntry entry) {
        ItemStack hand = itemOf(entry.mainHand());
        if (!hand.isEmpty()) {
            mob.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, hand);
        }
        ItemStack head = itemOf(entry.head());
        if (!head.isEmpty()) {
            mob.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, head);
        }
    }

    private static ItemStack itemOf(String id) {
        if (id == null || id.isEmpty()) {
            return ItemStack.EMPTY;
        }
        net.minecraft.resources.ResourceLocation rl = net.minecraft.resources.ResourceLocation.tryParse(id);
        if (rl == null || !net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(rl)) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(rl));
    }

    private static void setAttr(Mob mob,
                                net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr,
                                double value) {
        AttributeInstance inst = mob.getAttribute(attr);
        if (inst != null) {
            inst.setBaseValue(value);
        }
    }

    private ServerPlayer nearestPlayer(BlockPos from) {
        ServerPlayer best = null;
        double bestDist = Double.MAX_VALUE;
        for (ServerPlayer p : players()) {
            double d = p.blockPosition().distSqr(from);
            if (d < bestDist) {
                bestDist = d;
                best = p;
            }
        }
        return best;
    }

    /** Pays for a kill, if this run has an economy. */
    public static void onKill(Mob mob, ServerPlayer killer) {
        EngineArena a = current;
        if (a == null || !a.running || killer == null
                || !mob.getPersistentData().getBoolean("aztecabyss_engine_mob")) {
            return;
        }
        if (a.rules.economyEnabled) {
            Currency.byId(a.rules.defaultCurrency).award(killer, a.rules.pointsKill);
        }
    }

    public int round() {
        return round;
    }

    public String mapName() {
        return mapName;
    }

    public BoundingBox bounds() {
        return bounds;
    }
}
