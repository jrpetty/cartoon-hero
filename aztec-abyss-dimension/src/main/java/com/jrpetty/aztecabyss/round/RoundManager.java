package com.jrpetty.aztecabyss.round;

import com.jrpetty.aztecabyss.AztecAbyssConstants;
import com.jrpetty.aztecabyss.config.AbyssConfig;
import com.jrpetty.aztecabyss.dimension.AbyssTeleporter;
import com.jrpetty.aztecabyss.network.ModNetworking;
import com.jrpetty.aztecabyss.registry.ModAttachments;
import com.jrpetty.aztecabyss.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.Mob;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Drives the shared, co-op Abyss run: one {@link AbyssGame} for the whole
 * arena, plus per-player {@link RunState}. Handles round pacing, wave spawning
 * and scaling, the boss-bar/title UI, the downed-and-revive loop, and sending
 * players home with round-scaled rewards on death or victory.
 */
public final class RoundManager {

    private static final RandomSource RNG = RandomSource.create();
    private static final int MAX_CONCURRENT_BASE = 8;

    private static final Map<UUID, ServerBossEvent> BOSS_BARS = new HashMap<>();
    private static AbyssGame game = new AbyssGame();

    private RoundManager() {
    }

    public static AbyssGame game() {
        return game;
    }

    /**
     * True while a run is under way with players still inside. The Abyss is
     * sealed in this state: nobody can join late and nobody can walk out - the
     * only way home is to die, extract, or win.
     */
    public static boolean isRunLocked() {
        return !game.getParticipants().isEmpty()
                && (game.getPhase() == AbyssGame.Phase.BETWEEN_ROUNDS || game.getPhase() == AbyssGame.Phase.IN_ROUND);
    }

    /**
     * Seals or reopens the arrival portal inside the Abyss. While a run is live
     * the portal surface is removed entirely, so players can't slip back through
     * it (and can't get bounced straight home by standing in it on arrival).
     */
    public static void setArrivalPortalOpen(ServerLevel level, boolean open) {
        BlockPos arrival = game.getMap().arrival();
        int floorY = arrival.getY() - 1;
        for (int dx = 0; dx <= 1; dx++) {
            for (int dy = 1; dy <= 3; dy++) {
                BlockPos p = new BlockPos(arrival.getX() + dx, floorY + dy, arrival.getZ());
                if (open) {
                    level.setBlock(p, com.jrpetty.aztecabyss.registry.ModBlocks.ABYSS_PORTAL.get().defaultBlockState()
                            .setValue(com.jrpetty.aztecabyss.block.AbyssPortalBlock.AXIS,
                                    net.minecraft.core.Direction.Axis.X), 3);
                } else {
                    level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
    }

    private static void resetSession() {
        for (ServerBossEvent bar : BOSS_BARS.values()) {
            bar.removeAllPlayers();
        }
        BOSS_BARS.clear();
        for (ServerBossEvent bar : HEART_BARS.values()) {
            bar.removeAllPlayers();
        }
        HEART_BARS.clear();
        LAST_HUD_STAMP.clear();
        game = new AbyssGame();
    }

    // ------------------------------------------------------------------
    // Entry
    // ------------------------------------------------------------------

    public static void onPlayerEnter(ServerPlayer player) {
        RunState rs = player.getData(ModAttachments.RUN_STATE);
        rs.clearRun();
        rs.setInRun(true);
        rs.beginRunTracking(System.currentTimeMillis());
        player.setData(ModAttachments.RUN_STATE, rs);

        if (game.getPhase() == AbyssGame.Phase.IDLE || game.getPhase() == AbyssGame.Phase.ENDED) {
            resetSession();
            // The first player through fixes the arena for the whole run.
            game.setMap(com.jrpetty.aztecabyss.worldgen.ArenaMap.byId(
                    player.getPersistentData().getInt("aztecabyss_chosen_map")));
            if (player.level() instanceof ServerLevel abyssLevel) {
                clearSupplyCaches(abyssLevel); // sweep away last run's caches
                com.jrpetty.aztecabyss.worldgen.ArenaMap m = game.getMap();
                abyssLevel.getWorldBorder().setCenter(m.borderCenterX(), m.borderCenterZ());
                abyssLevel.getWorldBorder().setSize(m.borderSize());
                // Every gate goes back up for a fresh run, however last run ended.
                Barricade.resetFor(abyssLevel, m);
                resetAreas(abyssLevel, m);
                LAST_REPAIR.clear();
            }
            game.setPhase(AbyssGame.Phase.BETWEEN_ROUNDS);
            game.setRound(0);
            game.setNextFogRound(rollNextFogRound(0)); // first mist rolls in around round 5-8
            game.setPhaseChangedAt(player.level().getGameTime());
        }
        game.addParticipant(player.getUUID());

        if (game.getMap() == com.jrpetty.aztecabyss.worldgen.ArenaMap.OUTPOST) {
            // The Outpost runs its own economy: nothing you own comes in with you.
            OutpostEconomy.enter(player);
        } else if (AbyssConfig.GIVE_STARTING_LOADOUT.get()) {
            giveLoadout(player);
        }
        AbyssAbility.give(player); // one-charge Abyssal Nova, dimension-locked
        setupBossBar(player);
        setupHeartBar(player);
        // Seal the way out the moment the run begins: no late joins, no walking away.
        if (player.level() instanceof ServerLevel abyss) {
            setArrivalPortalOpen(abyss, false);
        }

        ModNetworking.sendState(player, true, game.getRound()); // triggers the arrival cinematic
        if (player.level() instanceof ServerLevel abyssLevel) {
            broadcastHud(abyssLevel);
        }
        boolean coop = game.getParticipants().size() > 1;
        title(player, "§0§lTHE AZTEC ABYSS",
                coop ? "§8You join the hunt. Round " + Math.max(1, game.getRound()) + "."
                     : "§8Survive the horde. Round 20 is nearly impossible.");
        player.level().playSound(null, player.blockPosition(), ModSounds.AMBIENT_DREAD.get(), SoundSource.HOSTILE, 0.7F, 0.8F);
    }

    private static void giveLoadout(ServerPlayer player) {
        addOrDrop(player, new ItemStack(Items.STONE_SWORD));
        addOrDrop(player, new ItemStack(Items.BOW));
        addOrDrop(player, new ItemStack(Items.ARROW, 32));
        addOrDrop(player, new ItemStack(Items.COOKED_BEEF, 8));
        addOrDrop(player, new ItemStack(Items.TORCH, 8));
        if (player.getItemBySlot(EquipmentSlot.CHEST).isEmpty()) {
            player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.LEATHER_CHESTPLATE));
        }
    }

    private static void addOrDrop(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    // ------------------------------------------------------------------
    // Main shared tick (called once per level tick from AbyssEventHandler)
    // ------------------------------------------------------------------

    public static void tickSession(ServerLevel level) {
        List<ServerPlayer> present = participantPlayers(level);
        if (present.isEmpty()) {
            if (game.getPhase() != AbyssGame.Phase.IDLE) {
                clearWaveMobs(level); // don't orphan a boss/adds when the arena empties
                resetSession();
                setArrivalPortalOpen(level, true); // arena is free again - the way in reopens
            }
            return;
        }

        tickDownedAndRevive(level, present);

        // Everyone still in the run is downed -> total wipe.
        boolean anyUp = present.stream().anyMatch(p -> !p.getData(ModAttachments.RUN_STATE).isDowned());
        if (!anyUp) {
            endGame(level, false);
            return;
        }

        long now = level.getGameTime();
        if (now % 10L == 0L) {
            broadcastHud(level); // refresh the live HUD ~twice a second
        }
        // One arena-wide entity sweep drives both retargeting and the stuck check,
        // instead of two overlapping queries on different cadences.
        if (now % 40L == 0L) {
            List<Mob> waveMobs = level.getEntitiesOfClass(Mob.class, game.getMap().bounds(),
                    m -> m.getPersistentData().getBoolean("aztecabyss_wave_mob"));
            retargetWaveMobs(level, present, waveMobs);
            if (now % 200L == 0L) {
                repatriateStuckMobs(level, present, waveMobs);
            }
        }
        if (game.getMap().objective() != null) {
            tickObjective(level, present);
            updateHeartBars();
        }
        if (game.getMap().hasBarricades() && now % BARRICADE_INTERVAL == 0L) {
            tickBarricades(level, present);
        }
        switch (game.getPhase()) {
            case BETWEEN_ROUNDS -> {
                // Extraction is offered after any cleared round; while someone is
                // channelling on the glyph we hold the next round from starting.
                boolean holding = tickExtraction(level, present);
                long delay = game.getRound() == 0
                        ? AbyssConfig.FIRST_ROUND_DELAY_TICKS.get()
                        : breatherTicks(game.getRound());
                if (!holding && now - game.getPhaseChangedAt() >= delay) {
                    startRound(level, game.getRound() + 1);
                }
            }
            case IN_ROUND -> {
                spawnQueued(level, present);
                hordeAmbience(level, present);
                if (game.isBossRound()) {
                    tickBoss(level, present);
                    // The round ends only when the Warden is slain, not when the adds run out.
                    if (game.isBossSpawned() && !game.isBossActive()) {
                        clearWaveMobs(level);
                        onRoundCleared(level);
                    }
                } else if (game.getSpawnedThisRound() >= game.getKillsNeededThisRound() && game.getAliveZombies() <= 0) {
                    onRoundCleared(level);
                }
                updateBossBars(level);
            }
            default -> {
            }
        }
    }

    private static void startRound(ServerLevel level, int round) {
        setExtractionGlyph(level, false); // no bailing once the wave is live
        game.setRound(round);
        game.setPhase(AbyssGame.Phase.IN_ROUND);
        game.setKillsThisRound(0);
        game.setSpawnedThisRound(0);
        game.setPhaseChangedAt(level.getGameTime());

        boolean bossRound = isBossRound(round);
        game.setBossRound(bossRound);
        game.setBossActive(false);
        game.setBossId(null);
        game.setBossHealthFraction(0f);

        // Special fog round: randomised, never on a boss round. Reschedule the next.
        boolean fogRound = !bossRound && round > 0 && round == game.getNextFogRound();
        game.setFogRound(fogRound);
        if (fogRound || round >= game.getNextFogRound()) {
            game.setNextFogRound(rollNextFogRound(round));
        }

        int fullWave = waveSize(round, Math.max(1, game.getParticipants().size()));
        // On a boss round the Warden is the objective; the wave is trimmed to a
        // pressure of adds (the boss summons more as the fight drags on).
        game.setKillsNeededThisRound(bossRound ? Math.max(4, fullWave / 3) : fullWave);
        if (game.getMap() == com.jrpetty.aztecabyss.worldgen.ArenaMap.TEMPLE) {
            com.jrpetty.aztecabyss.worldgen.ArenaGenerator.escalateTemple(level, round);
        }

        int max = AbyssConfig.MAX_ROUND.get();
        for (ServerPlayer p : participantPlayers(level)) {
            p.getData(ModAttachments.RUN_STATE).recordRound(round);
            if (fogRound) {
                title(p, "§7§lA CREEPING FOG ROLLS IN", "§8They'll be on you before you see them.");
                level.playSound(null, p.blockPosition(), ModSounds.AMBIENT_DREAD.get(), SoundSource.HOSTILE, 1.0F, 0.5F);
            } else if (game.getMap().isEndless()) {
                // No finish line to count down to - milestones instead.
                String sub;
                if (bossRound) {
                    sub = "§c§lSOMETHING BIG IS COMING";
                } else if (round % 10 == 9) {
                    sub = "§6" + game.getKillsNeededThisRound() + " incoming §7— a boss waits at " + (round + 1);
                } else if (round == 21) {
                    sub = "§c§lPAST THE LADDER §7— they stop growing and start getting worse";
                } else {
                    sub = "§7" + game.getKillsNeededThisRound() + " incoming";
                }
                title(p, "§4§lWAVE " + round, sub);
            } else {
                title(p, "§4§lROUND " + round, round == max
                        ? "§c§lFINAL ROUND - GOOD LUCK"
                        : "§7" + game.getKillsNeededThisRound() + " incoming");
            }
            level.playSound(null, p.blockPosition(), ModSounds.ROUND_START.get(), SoundSource.HOSTILE, 1.0F, 1.0F);
        }
        broadcastHud(level);
        for (ServerBossEvent bar : BOSS_BARS.values()) {
            bar.setName(Component.literal("§6✦ §fRound " + round + " §7— The Aztec Abyss"));
            bar.setColor(round >= 15 ? BossEvent.BossBarColor.RED : round >= 8 ? BossEvent.BossBarColor.YELLOW : BossEvent.BossBarColor.WHITE);
            bar.setProgress(0.0F);
            bar.setDarkenScreen(false);
            bar.setCreateWorldFog(false);
            bar.setPlayBossMusic(false);
        }

        if (bossRound) {
            spawnBoss(level, round, participantPlayers(level));
        }
    }

    /**
     * The gap between waves. On an endless map it closes as the run goes on -
     * from ten seconds down to three by round forty - so the time you have to
     * put boards back on shrinks exactly as the horde gets worse. It is the
     * quietest of the difficulty levers and probably the cruellest.
     */
    private static long breatherTicks(int round) {
        long base = AbyssConfig.BETWEEN_ROUND_TICKS.get();
        if (!game.getMap().isEndless() || round <= 10) {
            return base;
        }
        return Math.max(60L, base - (round - 10) * 4L);
    }

    /** Boss rounds: every tenth round on an endless map, else round 10 and the last. */
    private static boolean isBossRound(int round) {
        if (game.getMap().isEndless()) {
            return round > 0 && round % 10 == 0;
        }
        return round == 10 || round == AbyssConfig.MAX_ROUND.get();
    }

    /** Next fog round: a randomised 5-8 waves after {@code fromRound}. */
    private static int rollNextFogRound(int fromRound) {
        return fromRound + 5 + RNG.nextInt(4); // +5..+8
    }

    private static void spawnQueued(ServerLevel level, List<ServerPlayer> present) {
        // Steeper growth than before so the higher ceiling is actually reachable at
        // late rounds / with a full party, instead of the old ~30-alive plateau.
        int maxConcurrent = Math.min(
                MAX_CONCURRENT_BASE + game.getRound() * 3 + present.size() * 4,
                AbyssConfig.MAX_CONCURRENT_ALIVE.get());
        int remaining = game.getKillsNeededThisRound() - game.getSpawnedThisRound();
        int canSpawn = Math.max(0, maxConcurrent - game.getAliveZombies());
        int spawnThisTick = Math.min(4 + present.size() * 2, Math.min(canSpawn, remaining));

        for (int i = 0; i < spawnThisTick; i++) {
            boolean brute = game.getRound() % 5 == 0 && (game.getSpawnedThisRound() % 8 == 7);
            spawnWaveMob(level, present, game.getRound(), brute);
            game.setSpawnedThisRound(game.getSpawnedThisRound() + 1);
            game.setAliveZombies(game.getAliveZombies() + 1);
        }
    }

    private static void spawnWaveMob(ServerLevel level, List<ServerPlayer> present, int round, boolean brute) {
        // Every wave mob pours out of one of the active map's horde gates.
        BlockPos[] gates = game.getMap().gates();
        int gateIndex = pickGate(game.getMap());
        BlockPos gate = gates[gateIndex];
        boolean penned = game.getMap().hasBarricades();
        boolean spreadAlongX = gate.getZ() != 0 || gates.length == 1;
        int jitter = RNG.nextInt(5) - 2;
        BlockPos pos;
        if (penned) {
            // Materialise inside the sealed gatehouse, back from the boards, so
            // there is a walk-up before the tearing starts.
            BlockPos pen = Barricade.penSpawn(game.getMap(), gateIndex);
            int j = RNG.nextInt(3) - 1;
            pos = Barricade.spansX(game.getMap(), gateIndex)
                    ? pen.offset(j, 0, 0) : pen.offset(0, 0, j);
        } else {
            pos = spreadAlongX ? gate.offset(jitter, 0, 0) : gate.offset(0, 0, jitter);
        }
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.PORTAL,
                pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 12, 0.4, 0.8, 0.4, 0.05);

        WaveMobs.Spawn choice = WaveMobs.pick(RNG, round);
        Mob mob = choice.type().create(level);
        if (mob == null) {
            return;
        }
        mob.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, RNG.nextFloat() * 360.0F, 0.0F);
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null);
        equipWeapon(mob, choice.weapon());
        applyRoundScaling(mob, round, brute);
        mob.setPersistenceRequired();
        mob.getPersistentData().putBoolean("aztecabyss_wave_mob", true);
        mob.getPersistentData().putLong("aztecabyss_gate_tick", level.getGameTime());
        // -1 means "loose in the arena". Always written, because an unset int reads
        // back as 0 and would masquerade as the north gate.
        mob.getPersistentData().putInt("aztecabyss_gate_index", penned ? gateIndex : -1);
        if (penned) {
            mob.getPersistentData().putLong("aztecabyss_penned_at", level.getGameTime());
        }

        // On maps with something to defend, some of the horde comes specifically
        // for it - these are the ones that punish you for not watching the Heart.
        int role = rollRole(round);
        applyRole(level, mob, role, present);

        if (penned) {
            // Nothing penned gets a player target - the boards are the only thing
            // in front of it, and the barricade tick drives it from here.
            mob.setTarget(null);
        } else if (role != ROLE_BREAKER) {
            // Breakers never look at players; everything else opens on the nearest.
            ServerPlayer target = nearestTarget(present, pos);
            if (target != null) {
                mob.setTarget(target);
            }
        }
        level.addFreshEntity(mob);
    }

    // ------------------------------------------------------------------
    // Objective specialists - only meaningful on maps with something to defend
    // ------------------------------------------------------------------

    static final int ROLE_NORMAL = 0;
    /** Sprints for the objective and will not be distracted by players at all. */
    static final int ROLE_BREAKER = 1;
    /** Slow and armoured, but tears chunks out of the objective. */
    static final int ROLE_SAPPER = 2;

    /** How much objective damage each role deals per damage tick. */
    /**
     * Picks a window to spawn at, skipping any in a part of the map still sealed
     * behind rubble. Without this, half the horde would climb into rooms the
     * squad cannot reach and stand there until the round deadlocked.
     */
    private static int pickGate(com.jrpetty.aztecabyss.worldgen.ArenaMap map) {
        int n = map.gates().length;
        if (map.areaCount() <= 1) {
            return RNG.nextInt(n);
        }
        int[] usable = new int[n];
        int count = 0;
        for (int i = 0; i < n; i++) {
            if (isAreaOpen(map.gateArea(i))) {
                usable[count++] = i;
            }
        }
        return count == 0 ? 0 : usable[RNG.nextInt(count)];
    }

    // ------------------------------------------------------------------
    // Sealed areas - rubble the squad digs out to open more of the map
    // ------------------------------------------------------------------

    /** Which areas of the active map have been opened. Area 0 is always open. */
    private static boolean[] areaOpen = new boolean[]{true};
    /** Spadefuls of rubble shifted so far, per area. */
    private static final Map<Integer, Integer> DEBRIS_PROGRESS = new HashMap<>();
    /** How many pulls it takes to clear a pile. */
    private static final int DIG_PULLS = 5;

    public static boolean isAreaOpen(int area) {
        return area <= 0 || (area < areaOpen.length && areaOpen[area]);
    }

    private static void resetAreas(ServerLevel level, com.jrpetty.aztecabyss.worldgen.ArenaMap map) {
        areaOpen = new boolean[Math.max(1, map.areaCount())];
        areaOpen[0] = true;
        DEBRIS_PROGRESS.clear();
        if (map == com.jrpetty.aztecabyss.worldgen.ArenaMap.OUTPOST) {
            com.jrpetty.aztecabyss.worldgen.OutpostBuilder.placeDebris(level);
        }
    }

    /**
     * Shifts a spadeful of rubble. Free, like boarding a window - the cost is the
     * seconds you spend with your back to the room, and the fact that whatever is
     * behind it starts coming through the moment you break it open.
     */
    public static boolean digDebris(ServerLevel level, ServerPlayer player, int area) {
        if (!game.isParticipant(player.getUUID()) || isAreaOpen(area)) {
            return false;
        }
        long now = level.getGameTime();
        Long last = LAST_REPAIR.get(player.getUUID());
        if (last != null && now - last < REPAIR_COOLDOWN_TICKS) {
            return false;
        }
        LAST_REPAIR.put(player.getUUID(), now);

        int pulls = DEBRIS_PROGRESS.merge(area, 1, Integer::sum);
        barricadeSound(level, player.blockPosition(),
                net.minecraft.sounds.SoundEvents.STONE_BREAK, 1.0F, 0.7F);
        if (pulls < DIG_PULLS) {
            actionBar(player, "§7Shifting rubble... §8(" + pulls + "/" + DIG_PULLS + ")");
            return true;
        }

        areaOpen[area] = true;
        com.jrpetty.aztecabyss.worldgen.OutpostBuilder.clearDebris(level, area);
        String what = switch (area) {
            case com.jrpetty.aztecabyss.worldgen.OutpostBuilder.AREA_BACK -> "the back room";
            case com.jrpetty.aztecabyss.worldgen.OutpostBuilder.AREA_CELLAR -> "the cellar";
            default -> "the stairs";
        };
        for (ServerPlayer p : participantPlayers(level)) {
            actionBar(p, "§6⚒ The way to §f" + what + "§6 is clear §7— and so are its windows");
            level.playSound(null, p.blockPosition(), net.minecraft.sounds.SoundEvents.ANVIL_LAND,
                    SoundSource.BLOCKS, 0.7F, 0.8F);
        }
        return true;
    }

    private static float roleHeartDamage(int role) {
        return switch (role) {
            case ROLE_BREAKER -> 2.0f;
            case ROLE_SAPPER -> 5.0f;
            default -> 1.0f;
        };
    }

    /**
     * Rolls a specialist role. Breakers start showing up early to teach the
     * mechanic; sappers arrive later and get steadily more common, so late
     * rounds live or die on how fast you can pick them out of the crowd.
     */
    private static int rollRole(int round) {
        // Specialists need something to specialise against: a Heart to break, or
        // boards to tear. On the Temple they are the ones that get through the
        // gate you thought you were holding.
        if (game.getMap().objective() == null && !game.getMap().hasBarricades()) {
            return ROLE_NORMAL;
        }
        boolean endless = game.getMap().isEndless();
        int sapperCap = endless ? 32 : 20;
        int breakerCap = endless ? 42 : 30;
        if (round >= 6 && RNG.nextInt(100) < Math.min(6 + round, sapperCap)) {
            return ROLE_SAPPER;
        }
        if (round >= 3 && RNG.nextInt(100) < Math.min(10 + round * 2, breakerCap)) {
            return ROLE_BREAKER;
        }
        return ROLE_NORMAL;
    }

    /**
     * Stamps a specialist's stats and - just as importantly - its look. Each role
     * gets a distinct silhouette, a full themed armour set, a coloured outline
     * glow (via a scoreboard team) and a permanent particle aura, so you can pick
     * one out of a churning 100-mob horde at a glance and across a 100-block bridge.
     */
    private static void applyRole(ServerLevel level, Mob mob, int role, List<ServerPlayer> present) {
        if (role == ROLE_NORMAL) {
            return;
        }
        mob.getPersistentData().putInt("aztecabyss_role", role);
        mob.setCustomNameVisible(true);

        if (role == ROLE_BREAKER) {
            // Lean, fast, fragile - a red streak sprinting down the bridge.
            scaleAttribute(mob, Attributes.MOVEMENT_SPEED, 1.45);
            scaleAttribute(mob, Attributes.MAX_HEALTH, 0.6);
            setAttribute(mob, Attributes.KNOCKBACK_RESISTANCE, 0.0);
            AttributeInstance scale = mob.getAttribute(Attributes.SCALE);
            if (scale != null) {
                scale.setBaseValue(0.85); // small and wiry
            }
            mob.setHealth(mob.getMaxHealth());
            mob.setCustomName(Component.literal("§c§l⇥ BREAKER"));
            // Empty-handed - it isn't here to fight you, it's here to run past you.
            equipNoDrop(mob, EquipmentSlot.MAINHAND, ItemStack.EMPTY);

            // Blood-red leather - light armour for something built to run.
            dyeAndEquip(mob, EquipmentSlot.HEAD, Items.LEATHER_HELMET, 0xB01818);
            dyeAndEquip(mob, EquipmentSlot.CHEST, Items.LEATHER_CHESTPLATE, 0xB01818);
            dyeAndEquip(mob, EquipmentSlot.LEGS, Items.LEATHER_LEGGINGS, 0x701010);
            dyeAndEquip(mob, EquipmentSlot.FEET, Items.LEATHER_BOOTS, 0x701010);

            // Speed particles trail it for free (client-side, no server cost).
            mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, Integer.MAX_VALUE, 2, false, true));
            mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, Integer.MAX_VALUE, 0, false, false));
            tintOutline(level, mob, "aztecabyss_breaker", net.minecraft.ChatFormatting.RED);
        } else {
            // A hulking armoured siege-engine of a thing.
            scaleAttribute(mob, Attributes.MOVEMENT_SPEED, 0.6);
            scaleAttribute(mob, Attributes.MAX_HEALTH, 2.2);
            setAttribute(mob, Attributes.KNOCKBACK_RESISTANCE, 0.8);
            AttributeInstance armor = mob.getAttribute(Attributes.ARMOR);
            if (armor != null) {
                armor.setBaseValue(Math.min(20.0, armor.getBaseValue() + 10.0));
            }
            AttributeInstance scale = mob.getAttribute(Attributes.SCALE);
            if (scale != null) {
                scale.setBaseValue(1.45); // towers over the rank and file
            }
            mob.setHealth(mob.getMaxHealth());
            mob.setCustomName(Component.literal("§4§l⛏ SAPPER"));

            // Full netherite plate and a breaching axe.
            equipNoDrop(mob, EquipmentSlot.HEAD, new ItemStack(Items.NETHERITE_HELMET));
            equipNoDrop(mob, EquipmentSlot.CHEST, new ItemStack(Items.NETHERITE_CHESTPLATE));
            equipNoDrop(mob, EquipmentSlot.LEGS, new ItemStack(Items.NETHERITE_LEGGINGS));
            equipNoDrop(mob, EquipmentSlot.FEET, new ItemStack(Items.NETHERITE_BOOTS));
            equipNoDrop(mob, EquipmentSlot.MAINHAND, new ItemStack(Items.NETHERITE_AXE));

            // Angry red aura, again rendered client-side off the effect itself.
            mob.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, Integer.MAX_VALUE, 0, false, true));
            mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, Integer.MAX_VALUE, 0, false, false));
            tintOutline(level, mob, "aztecabyss_sapper", net.minecraft.ChatFormatting.DARK_RED);
        }

        // A burst as it comes through the gate, so specialists arrive with weight.
        // The two are pitched deliberately apart - a thin high cry versus a deep
        // toll - so you can tell them apart by ear alone from down the bridge.
        double mx = mob.getX();
        double my = mob.getY();
        double mz = mob.getZ();
        if (role == ROLE_BREAKER) {
            level.sendParticles(ParticleTypes.FLAME, mx, my + 0.8, mz, 20, 0.4, 0.5, 0.4, 0.06);
            level.sendParticles(ParticleTypes.CRIT, mx, my + 0.8, mz, 14, 0.4, 0.5, 0.4, 0.1);
            level.playSound(null, mob.blockPosition(), net.minecraft.sounds.SoundEvents.RAVAGER_ROAR,
                    SoundSource.HOSTILE, 1.1F, 1.7F);
        } else {
            level.sendParticles(ParticleTypes.LARGE_SMOKE, mx, my + 1.0, mz, 24, 0.6, 0.7, 0.6, 0.02);
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, mx, my + 0.5, mz, 18, 0.5, 0.5, 0.5, 0.03);
            level.playSound(null, mob.blockPosition(), net.minecraft.sounds.SoundEvents.RAVAGER_ROAR,
                    SoundSource.HOSTILE, 1.2F, 0.4F);
            level.playSound(null, mob.blockPosition(), net.minecraft.sounds.SoundEvents.ANVIL_LAND,
                    SoundSource.HOSTILE, 0.7F, 0.5F);
        }

        // Call it out so players can react rather than be blindsided.
        for (ServerPlayer p : present) {
            actionBar(p, role == ROLE_BREAKER
                    ? "§c§l⇥ BREAKER §7— running for the Heart!"
                    : "§4§l⛏ SAPPER §7— kill it before it lands a blow!");
        }
    }

    /** Puts a mob on a coloured scoreboard team so its Glowing outline is tinted. */
    private static void tintOutline(ServerLevel level, Mob mob, String teamName, net.minecraft.ChatFormatting colour) {
        net.minecraft.world.scores.Scoreboard sb = level.getScoreboard();
        net.minecraft.world.scores.PlayerTeam team = sb.getPlayerTeam(teamName);
        if (team == null) {
            team = sb.addPlayerTeam(teamName);
            team.setColor(colour);
            team.setSeeFriendlyInvisibles(false);
        }
        sb.addPlayerToTeam(mob.getStringUUID(), team);
    }

    private static void equipNoDrop(Mob mob, EquipmentSlot slot, ItemStack stack) {
        mob.setItemSlot(slot, stack);
        mob.setDropChance(slot, 0.0F);
    }

    /** Dyed leather piece, equipped with no drop chance. */
    private static void dyeAndEquip(Mob mob, EquipmentSlot slot, net.minecraft.world.item.Item item, int rgb) {
        ItemStack stack = new ItemStack(item);
        stack.set(net.minecraft.core.component.DataComponents.DYED_COLOR,
                new net.minecraft.world.item.component.DyedItemColor(rgb, false));
        equipNoDrop(mob, slot, stack);
    }

    private static void equipWeapon(Mob mob, WaveMobs.Weapon weapon) {
        ItemStack stack = switch (weapon) {
            case BOW -> new ItemStack(Items.BOW);
            case CROSSBOW -> new ItemStack(Items.CROSSBOW);
            case SWORD -> new ItemStack(Items.STONE_SWORD);
            case NONE -> ItemStack.EMPTY;
        };
        if (!stack.isEmpty()) {
            mob.setItemSlot(EquipmentSlot.MAINHAND, stack);
            mob.setDropChance(EquipmentSlot.MAINHAND, 0.0F); // no loot spam
        }
    }

    /**
     * Health past the soften point compounds instead of creeping, because a
     * linear curve is outrun by player gear and endless rounds would stop
     * mattering. 4.5% a round doubles roughly every fifteen: a round-40 zombie
     * runs about eleven times base, round 50 about seventeen, round 60 about
     * twenty-seven. Round 40 is meant to be a wall and round 60 heroic.
     */
    private static double healthCurve(int round) {
        double linear = 1.0 + Math.min(round, SOFTEN_AFTER) * AbyssConfig.HEALTH_SCALE_PER_ROUND.get();
        return round <= SOFTEN_AFTER ? linear : linear * Math.pow(1.045, round - SOFTEN_AFTER);
    }

    private static void applyRoundScaling(Mob mob, int round, boolean brute) {
        double healthMult = healthCurve(round) * game.getMap().difficultyMultiplier();
        // Damage is capped where health is not. Solo death is final here, so a
        // horde that one-shots you is a different (and worse) game than a horde
        // that takes an age to put down.
        double dmgMult = Math.min(1.0 + round * AbyssConfig.DAMAGE_SCALE_PER_ROUND.get(), 8.0);
        double speedMult = Math.min(1.0 + round * 0.02, 1.6);

        // Fog rounds: the horde looms a touch faster out of the murk.
        if (game.isFogRound()) {
            speedMult *= 1.12;
        }

        if (brute) {
            healthMult *= 2.5;
            dmgMult *= 1.4;
            speedMult *= 0.85;
        }

        scaleAttribute(mob, Attributes.MAX_HEALTH, healthMult);
        scaleAttribute(mob, Attributes.ATTACK_DAMAGE, dmgMult);
        scaleAttribute(mob, Attributes.MOVEMENT_SPEED, speedMult);
        // Arena mobs see the whole arena: they march from their gate straight at
        // the hunters instead of loitering until someone wanders close. Only wave
        // mobs pass through here, so nothing outside the arena is affected.
        setAttribute(mob, Attributes.FOLLOW_RANGE, AztecAbyssConstants.ARENA_RADIUS * 2.5);
        AttributeInstance armor = mob.getAttribute(Attributes.ARMOR);
        if (armor != null) {
            armor.setBaseValue(Math.min(20.0, round * 0.6));
        }
        // Late endless rounds get a standing knockback floor, so you can no
        // longer simply bat the horde back off a window forever.
        if (round > 25) {
            setAttribute(mob, Attributes.KNOCKBACK_RESISTANCE, Math.min(0.6, (round - 25) * 0.02));
        }
        applyHordeLook(mob, round, brute);
        mob.setHealth(mob.getMaxHealth());
    }

    /**
     * Dresses the rank-and-file so the horde reads as an army of the Abyss rather
     * than a vanilla night spawn, and so a glance tells you roughly how deep you
     * are: ragged and bare-headed early, blackened mail by the midgame, scorched
     * plate and burning eyes by the end. Cosmetic only - nothing drops.
     */
    private static void applyHordeLook(Mob mob, int round, boolean brute) {
        if (brute) {
            // A towering champion: oversized, crowned, wreathed in its own fire.
            AttributeInstance scale = mob.getAttribute(Attributes.SCALE);
            if (scale != null) {
                scale.setBaseValue(1.8);
            }
            equipNoDrop(mob, EquipmentSlot.HEAD, new ItemStack(Items.GOLDEN_HELMET));
            dyeAndEquip(mob, EquipmentSlot.CHEST, Items.LEATHER_CHESTPLATE, 0x2A0D0D);
            dyeAndEquip(mob, EquipmentSlot.LEGS, Items.LEATHER_LEGGINGS, 0x2A0D0D);
            equipNoDrop(mob, EquipmentSlot.FEET, new ItemStack(Items.GOLDEN_BOOTS));
            mob.setCustomName(Component.literal("§6§lBRUTE"));
            mob.setCustomNameVisible(false); // only readable up close, via the nameplate
            mob.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, Integer.MAX_VALUE, 0, false, false));
            mob.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, Integer.MAX_VALUE, 0, false, true));
            return;
        }

        // Slight size jitter so a crowd doesn't look like clones.
        AttributeInstance scale = mob.getAttribute(Attributes.SCALE);
        if (scale != null) {
            scale.setBaseValue(0.94 + RNG.nextDouble() * 0.16);
        }

        if (round <= 5) {
            // Early: mostly bare and ragged, the odd scrap of dark cloth.
            if (RNG.nextInt(3) == 0) {
                dyeAndEquip(mob, EquipmentSlot.HEAD, Items.LEATHER_HELMET, 0x241C1C);
            }
            if (RNG.nextInt(4) == 0) {
                dyeAndEquip(mob, EquipmentSlot.CHEST, Items.LEATHER_CHESTPLATE, 0x2E2323);
            }
        } else if (round <= 11) {
            // Midgame: blackened mail and soot-stained leather.
            if (RNG.nextBoolean()) {
                equipNoDrop(mob, EquipmentSlot.HEAD, new ItemStack(Items.CHAINMAIL_HELMET));
            } else {
                dyeAndEquip(mob, EquipmentSlot.HEAD, Items.LEATHER_HELMET, 0x1C1616);
            }
            if (RNG.nextInt(3) != 0) {
                dyeAndEquip(mob, EquipmentSlot.CHEST, Items.LEATHER_CHESTPLATE, 0x1C1616);
            }
            if (RNG.nextInt(3) == 0) {
                equipNoDrop(mob, EquipmentSlot.LEGS, new ItemStack(Items.CHAINMAIL_LEGGINGS));
            }
        } else {
            // Late: scorched plate and glowing eyes coming out of the dark.
            equipNoDrop(mob, EquipmentSlot.HEAD, new ItemStack(
                    RNG.nextBoolean() ? Items.IRON_HELMET : Items.CHAINMAIL_HELMET));
            equipNoDrop(mob, EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
            if (RNG.nextBoolean()) {
                equipNoDrop(mob, EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
            }
            if (RNG.nextInt(4) == 0) {
                equipNoDrop(mob, EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
            }
            // A faint ember aura on the deepest rounds.
            if (round >= 16 && RNG.nextInt(3) == 0) {
                mob.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, Integer.MAX_VALUE, 0, false, true));
            }
        }

        // Worn armour contributes real armour points, so pull the scaling
        // attribute back down - this pass is meant to change how the horde looks,
        // not quietly make it tankier than the tuning intends.
        AttributeInstance armor = mob.getAttribute(Attributes.ARMOR);
        if (armor != null && round > 5) {
            armor.setBaseValue(armor.getBaseValue() * (round > 11 ? 0.25 : 0.6));
        }
    }

    private static void scaleAttribute(Mob mob, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr, double mult) {
        AttributeInstance inst = mob.getAttribute(attr);
        if (inst != null) {
            inst.setBaseValue(inst.getBaseValue() * mult);
        }
    }

    private static void setAttribute(Mob mob, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr, double value) {
        AttributeInstance inst = mob.getAttribute(attr);
        if (inst != null) {
            inst.setBaseValue(value);
        }
    }

    /**
     * Plays a boss sound through a single-target-typed parameter. The Warden and
     * Ravager {@code SoundEvents} constants are plain {@link net.minecraft.sounds.SoundEvent}s,
     * so a {@code finale ? A : B} conditional resolves cleanly here; passing that
     * conditional straight into the heavily overloaded {@code level.playSound}
     * leaves the poly conditional unresolvable.
     */
    private static void bossSound(ServerLevel level, BlockPos pos,
            net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
        level.playSound(null, pos, sound, SoundSource.HOSTILE, volume, pitch);
    }

    // ------------------------------------------------------------------
    // Boss rounds - the Obsidian Warlord (round 10) and the Warden finale
    // ------------------------------------------------------------------

    /**
     * Where a boss makes its entrance: on the temple map, between the arrival
     * walkway and the pyramid; on the bridge, striding out of the far gate.
     */
    private static BlockPos bossSpawn() {
        return switch (game.getMap()) {
            case BRIDGE -> game.getMap().gates()[0].offset(0, 0, 4);
            // The double-height west end of the hall - the only room in the
            // building with the headroom and the floor space for it.
            case OUTPOST -> new BlockPos(
                    com.jrpetty.aztecabyss.worldgen.OutpostBuilder.CENTER_X - 9,
                    com.jrpetty.aztecabyss.worldgen.OutpostBuilder.FLOOR_Y + 1,
                    com.jrpetty.aztecabyss.worldgen.OutpostBuilder.CENTER_Z + 2);
            default -> new BlockPos(0, AztecAbyssConstants.ARENA_FLOOR_Y + 1, 16);
        };
    }

    /** True when the current boss round is the final round (the Warden); otherwise it's the mid-boss Warlord. */
    /** The bigger grade of boss: every thirtieth round endlessly, else the last. */
    private static boolean isFinaleBoss() {
        if (game.getMap().isEndless()) {
            return game.getRound() > 0 && game.getRound() % 30 == 0;
        }
        return game.getRound() >= AbyssConfig.MAX_ROUND.get();
    }

    private static void spawnBoss(ServerLevel level, int round, List<ServerPlayer> present) {
        boolean finale = round >= AbyssConfig.MAX_ROUND.get();
        // Round 10 fields a hulking Ravager brute; only the final round summons the Warden,
        // so the Warden stays the signature finale.
        Mob boss = (finale ? EntityType.WARDEN : EntityType.RAVAGER).create(level);
        if (boss == null) {
            return;
        }
        BlockPos spawnAt = bossSpawn();
        boss.moveTo(spawnAt.getX() + 0.5, spawnAt.getY(), spawnAt.getZ() + 0.5, 180.0F, 0.0F);
        boss.finalizeSpawn(level, level.getCurrentDifficultyAt(spawnAt), MobSpawnType.EVENT, null);

        // Explicit, hand-tuned stat line rather than the generic wave scaling: a
        // huge but not interminable health pool, brutal hits, unshakable footing.
        double hp = finale ? 400.0 + round * 30.0   // Warden ~1000 at round 20
                           : 350.0 + round * 20.0;  // Warlord ~550 at round 10
        setAttribute(boss, Attributes.MAX_HEALTH, hp);
        scaleAttribute(boss, Attributes.ATTACK_DAMAGE, 1.0 + round * AbyssConfig.DAMAGE_SCALE_PER_ROUND.get());
        setAttribute(boss, Attributes.KNOCKBACK_RESISTANCE, 1.0);
        setAttribute(boss, Attributes.FOLLOW_RANGE, AztecAbyssConstants.ARENA_RADIUS * 2.5);
        AttributeInstance scale = boss.getAttribute(Attributes.SCALE);
        if (scale != null) {
            scale.setBaseValue(finale ? 1.35 : 1.5); // the Warlord is a squat, wide bruiser
        }
        boss.setHealth(boss.getMaxHealth());

        boss.setPersistenceRequired();
        boss.getPersistentData().putBoolean("aztecabyss_wave_mob", true); // for arena cleanup
        boss.getPersistentData().putBoolean("aztecabyss_boss", true);
        // The boss is a wave mob for cleanup purposes, so it passes through every
        // sweep that filters on that flag. An unwritten gate index reads back as 0
        // - the north gate - which would quietly hand the Warden to the barricade
        // logic. Say "loose in the arena" out loud.
        boss.getPersistentData().putInt("aztecabyss_gate_index", -1);
        // Always trackable - the boss is huge and the arena is dark.
        boss.addEffect(new MobEffectInstance(MobEffects.GLOWING, Integer.MAX_VALUE, 0, false, false));
        level.addFreshEntity(boss);

        // Emergence spectacle: a pillar of light, a ground-crack burst, and an ember plume.
        double bx = spawnAt.getX() + 0.5;
        double by = spawnAt.getY();
        double bz = spawnAt.getZ() + 0.5;
        for (int dy = 0; dy < 14; dy++) {
            level.sendParticles(ParticleTypes.END_ROD, bx, by + dy, bz, 3, 0.18, 0.25, 0.18, 0.0);
        }
        level.sendParticles(ParticleTypes.EXPLOSION, bx, by + 0.2, bz, 4, 1.6, 0.1, 1.6, 0.0);
        level.sendParticles(finale ? ParticleTypes.SCULK_SOUL : ParticleTypes.FLAME,
                bx, by + 0.2, bz, 50, 2.6, 0.2, 2.6, 0.05);
        level.sendParticles(ParticleTypes.LAVA, bx, by + 1.0, bz, 30, 1.0, 0.6, 1.0, 0.1);

        game.setBossId(boss.getUUID());
        game.setBossActive(true);
        game.setBossEnraged(false);
        game.setBossSlamAt(0L);
        game.setBossHealthFraction(1.0f);
        game.setLastBossAbilityAt(level.getGameTime());

        String name = finale ? "THE DEVOURER" : "THE OBSIDIAN WARLORD";
        String flavor = finale ? "§cThe Warden claws its way out of the dark..."
                               : "§cA hulking brute charges from the temple steps...";
        for (ServerPlayer p : present) {
            title(p, "§4§l⚔ " + name, flavor);
            bossSound(level, p.blockPosition(),
                    finale ? SoundEvents.WARDEN_EMERGE : SoundEvents.RAVAGER_ROAR, 1.0F, finale ? 1.0F : 0.8F);
            level.playSound(null, p.blockPosition(), ModSounds.AMBIENT_DREAD.get(), SoundSource.HOSTILE, 1.0F, 0.5F);
            if (finale) {
                p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 120, 0, false, false));
            }
        }
        for (ServerBossEvent bar : BOSS_BARS.values()) {
            bar.setName(Component.literal("§4⚔ " + name));
            bar.setColor(BossEvent.BossBarColor.RED);
            bar.setProgress(1.0F);
            bar.setDarkenScreen(finale); // only the Warden blacks out the sky
            bar.setCreateWorldFog(true);
            bar.setPlayBossMusic(true);
        }
    }

    /** Per-tick boss behaviour: keep it hunting, pulse its signature abilities, summon adds. */
    private static void tickBoss(ServerLevel level, List<ServerPlayer> present) {
        if (!game.isBossActive() || game.getBossId() == null) {
            return;
        }
        Entity e = level.getEntity(game.getBossId());
        if (!(e instanceof LivingEntity boss) || !boss.isAlive()) {
            // Boss vanished without a death event (e.g. chunk edge) - treat as slain.
            game.setBossActive(false);
            return;
        }
        game.setBossHealthFraction((float) (boss.getHealth() / boss.getMaxHealth()));

        // Second wind: below a third HP the Warden enrages - faster, stronger, relentless.
        if (!game.isBossEnraged() && game.getBossHealthFraction() <= 0.33f) {
            enrageBoss(level, boss, present);
        }
        boolean enraged = game.isBossEnraged();

        // Keep the Warden locked onto a standing hunter so it never idles.
        if (boss instanceof Mob m && level.getGameTime() % 20L == 0L) {
            ServerPlayer target = pickTarget(present);
            if (target != null && (m.getTarget() == null || !m.getTarget().isAlive())) {
                m.setTarget(target);
            }
        }

        boolean finale = isFinaleBoss();
        long now = level.getGameTime();
        // The Warden leaves a creeping sculk-soul trail beneath it.
        if (finale && now % 5L == 0L) {
            level.sendParticles(ParticleTypes.SCULK_SOUL, boss.getX(), boss.getY() + 0.1, boss.getZ(),
                    2, 0.4, 0.05, 0.4, 0.0);
        }
        int abilityCd = enraged ? 70 : 120; // roughly every 3.5s / 6s
        if (now - game.getLastBossAbilityAt() >= abilityCd) {
            game.setLastBossAbilityAt(now);
            for (ServerPlayer p : present) {
                if (finale) {
                    p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 80, 0, false, false));
                }
                bossSound(level, p.blockPosition(),
                        finale ? SoundEvents.WARDEN_ROAR : SoundEvents.RAVAGER_ROAR, 0.9F, finale ? 1.0F : 0.85F);
            }
            int cap = AbyssConfig.MAX_CONCURRENT_ALIVE.get();
            int summon = Math.min((enraged ? 5 : 3) + present.size(), Math.max(0, cap - game.getAliveZombies()));
            for (int i = 0; i < summon; i++) {
                spawnWaveMob(level, present, game.getRound(), false);
                game.setAliveZombies(game.getAliveZombies() + 1);
            }
        }

        // Ground slam: charge up (telegraph) first, then land - so it can be dodged.
        long slamAt = game.getBossSlamAt();
        if (slamAt == 0L) {
            int slamInterval = enraged ? 90 : 130;
            if (now % slamInterval == 0L) {
                beginGroundSlam(level, boss, present, now + SLAM_WINDUP_TICKS);
            }
        } else {
            telegraphSlam(level, boss);
            if (now >= slamAt) {
                game.setBossSlamAt(0L);
                groundSlam(level, boss, present);
            }
        }
    }

    private static final double SLAM_RADIUS = 6.0;
    private static final int SLAM_WINDUP_TICKS = 34; // ~1.7s to sprint clear of the ring

    /** Starts the slam wind-up: a loud charge and a warning to anyone in the blast ring. */
    private static void beginGroundSlam(ServerLevel level, LivingEntity boss, List<ServerPlayer> present, long landAt) {
        game.setBossSlamAt(landAt);
        boolean finale = isFinaleBoss();
        bossSound(level, boss.blockPosition(),
                finale ? SoundEvents.WARDEN_SONIC_CHARGE : SoundEvents.RAVAGER_ROAR, 1.2F, finale ? 0.9F : 0.7F);
        String who = finale ? "The Warden rears back" : "The Warlord raises its fists";
        for (ServerPlayer p : present) {
            if (p.distanceToSqr(boss) <= SLAM_RADIUS * SLAM_RADIUS
                    && !p.getData(ModAttachments.RUN_STATE).isDowned()) {
                actionBar(p, "§c§l⚠ " + who + " — get out of the ring!");
            }
        }
    }

    /** Draws the glowing danger ring each tick during the wind-up so the AoE is readable. */
    private static void telegraphSlam(ServerLevel level, LivingEntity boss) {
        if (level.getGameTime() % 3L != 0L) {
            return;
        }
        boolean finale = isFinaleBoss();
        int points = 28;
        for (int i = 0; i < points; i++) {
            double a = (Math.PI * 2.0 * i) / points;
            double x = boss.getX() + Math.cos(a) * SLAM_RADIUS;
            double z = boss.getZ() + Math.sin(a) * SLAM_RADIUS;
            level.sendParticles(finale ? ParticleTypes.SCULK_CHARGE_POP : ParticleTypes.CRIT,
                    x, boss.getY() + 0.15, z, 1, 0.0, 0.0, 0.0, 0.0);
        }
        if (finale) {
            level.sendParticles(ParticleTypes.SONIC_BOOM, boss.getX(), boss.getY() + 1.2, boss.getZ(), 1, 0, 0, 0, 0);
        }
    }

    private static void enrageBoss(ServerLevel level, LivingEntity boss, List<ServerPlayer> present) {
        game.setBossEnraged(true);
        boolean finale = isFinaleBoss();
        boss.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, Integer.MAX_VALUE, 1, false, false));
        boss.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, Integer.MAX_VALUE, 1, false, false));
        String who = finale ? "THE WARDEN ENRAGES" : "THE WARLORD ENRAGES";
        for (ServerPlayer p : present) {
            title(p, "§4§l" + who, "§cIt will not stop now.");
            bossSound(level, p.blockPosition(),
                    finale ? SoundEvents.WARDEN_ANGRY : SoundEvents.RAVAGER_ROAR, 1.2F, finale ? 0.8F : 0.6F);
        }
        for (ServerBossEvent bar : BOSS_BARS.values()) {
            bar.setName(Component.literal(finale ? "§4§l⚔ ENRAGED WARDEN" : "§4§l⚔ ENRAGED WARLORD"));
        }
    }

    /** Lands the slam: only players who failed to leave the telegraphed ring are hit. */
    private static void groundSlam(ServerLevel level, LivingEntity boss, List<ServerPlayer> present) {
        float damage = (float) (4.0 + game.getRound() * 0.3); // r10 ~7, r20 ~10 - survivable, and dodgeable
        for (ServerPlayer p : present) {
            if (p.getData(ModAttachments.RUN_STATE).isDowned()) {
                continue; // downed players are already invulnerable
            }
            if (p.distanceToSqr(boss) > SLAM_RADIUS * SLAM_RADIUS) {
                continue; // stepped clear of the ring in time - no hit
            }
            p.hurt(boss.damageSources().mobAttack(boss), damage);
            double dx = p.getX() - boss.getX();
            double dz = p.getZ() - boss.getZ();
            double len = Math.sqrt(dx * dx + dz * dz);
            if (len < 0.1) {
                dx = RNG.nextDouble() - 0.5;
                dz = RNG.nextDouble() - 0.5;
                len = Math.sqrt(dx * dx + dz * dz) + 0.001;
            }
            p.push(dx / len * 1.0, 0.55, dz / len * 1.0);
            p.hurtMarked = true;
            p.connection.send(new ClientboundSetEntityMotionPacket(p));
        }
        boolean finale = isFinaleBoss();
        if (finale) {
            level.sendParticles(ParticleTypes.SONIC_BOOM, boss.getX(), boss.getY() + 1.2, boss.getZ(), 1, 0, 0, 0, 0);
        }
        level.sendParticles(ParticleTypes.EXPLOSION, boss.getX(), boss.getY() + 0.2, boss.getZ(), 8, 2.5, 0.2, 2.5, 0.0);
        // Split rather than ternary: GENERIC_EXPLODE and the Warden sound resolve to
        // different arg types, which breaks a shared conditional expression.
        if (finale) {
            level.playSound(null, boss.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 1.2F, 0.8F);
        } else {
            // GENERIC_EXPLODE is a Holder.Reference here (unlike the SoundEvent-typed
            // Warden/Ravager constants), so unwrap it to the SoundEvent.
            level.playSound(null, boss.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.2F, 0.8F);
        }
    }

    /** The boss died: end the spectacle, reward the arena, let the round clear. */
    public static void onBossKilled(ServerLevel level, ServerPlayer killer, BlockPos pos) {
        if (!game.isBossActive()) {
            return;
        }
        game.setBossActive(false);
        game.setBossHealthFraction(0f);
        if (killer != null) {
            RunState rs = killer.getData(ModAttachments.RUN_STATE);
            rs.addKill();
            killer.setData(ModAttachments.RUN_STATE, rs);
        }

        boolean finale = game.getRound() >= AbyssConfig.MAX_ROUND.get();
        for (ServerPlayer p : participantPlayers(level)) {
            title(p, finale ? "§6§l⚔ THE WARDEN FALLS" : "§6§l⚔ THE WARLORD FALLS",
                    finale ? "§eThe Abyss is conquered." : "§eThe brute crumbles. Press on.");
            bossSound(level, p.blockPosition(),
                    finale ? SoundEvents.WARDEN_DEATH : SoundEvents.RAVAGER_DEATH, 1.0F, 1.0F);
            p.removeEffect(MobEffects.DARKNESS);
        }
        for (ServerBossEvent bar : BOSS_BARS.values()) {
            bar.setProgress(0.0F);
            bar.setDarkenScreen(false);
            bar.setCreateWorldFog(false);
            bar.setPlayBossMusic(false);
        }

        // An immediate in-arena payoff for felling the boss (the run-end chest is separate).
        for (ItemStack drop : bossBonusDrop(game.getRound())) {
            level.addFreshEntity(new ItemEntity(level,
                    pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, drop));
        }
    }

    private static ItemStack[] bossBonusDrop(int round) {
        boolean finale = round >= AbyssConfig.MAX_ROUND.get();
        if (finale) {
            return new ItemStack[]{
                    new ItemStack(Items.NETHERITE_INGOT, 2),
                    new ItemStack(Items.ENCHANTED_GOLDEN_APPLE, 2),
                    new ItemStack(Items.EXPERIENCE_BOTTLE, 24),
            };
        }
        return new ItemStack[]{
                new ItemStack(Items.NETHERITE_SCRAP, 2),
                new ItemStack(Items.ENCHANTED_GOLDEN_APPLE, 1),
                new ItemStack(Items.EXPERIENCE_BOTTLE, 12),
        };
    }

    /** Called from the wave-mob death handler with the killer (if a participant). */
    public static void onWaveZombieKilled(ServerLevel level, ServerPlayer killer) {
        game.setKillsThisRound(game.getKillsThisRound() + 1);
        game.setAliveZombies(game.getAliveZombies() - 1);
        if (killer == null) {
            return;
        }
        RunState rs = killer.getData(ModAttachments.RUN_STATE);
        rs.addKill();
        killer.setData(ModAttachments.RUN_STATE, rs);

        if (!game.getMap().hasEconomy()) {
            return;
        }
        ItemStack held = killer.getMainHandItem();
        int pts = OutpostEconomy.POINTS_KILL;
        if (OutpostShop.hasPerk(held, OutpostShop.Perk.SCAVENGER)) {
            pts += pts / 2;
        }
        OutpostEconomy.award(killer, pts);
        if (OutpostShop.hasPerk(held, OutpostShop.Perk.SIPHON)) {
            killer.heal(2.0F);
        }
    }

    /** Points for a hit that did not finish the job. */
    public static void onWaveMobHurt(ServerPlayer attacker) {
        if (game.getMap().hasEconomy()) {
            OutpostEconomy.award(attacker, OutpostEconomy.POINTS_HIT);
        }
    }

    private static void onRoundCleared(ServerLevel level) {
        // Endless maps have no finish line - you leave with what you have, or
        // you keep going until it takes you.
        if (!game.getMap().isEndless() && game.getRound() >= AbyssConfig.MAX_ROUND.get()) {
            endGame(level, true);
            return;
        }
        game.setPhase(AbyssGame.Phase.BETWEEN_ROUNDS);
        game.setPhaseChangedAt(level.getGameTime());
        game.setFogRound(false); // mist clears in the breather
        broadcastHud(level);

        // Nothing mends the gates on its own - that is what the breather is for.
        // Say so plainly, because the cost of forgetting is a round you can't win.
        if (game.getMap().hasBarricades()) {
            int missing = Barricade.missingBoards();
            if (missing > 0) {
                int open = Barricade.openCount();
                String noun = game.getMap().gateNoun().toLowerCase(java.util.Locale.ROOT);
                String msg = open > 0
                        ? "§c⚒ " + open + " " + noun + (open > 1 ? "s" : "") + " standing open §7— board them before the next wave"
                        : "§e⚒ " + missing + " board" + (missing > 1 ? "s" : "") + " gone §7— right-click a " + noun + " to mend it";
                for (ServerPlayer p : participantPlayers(level)) {
                    actionBar(p, msg);
                }
            }
        }

        // Every fifth cleared round drops a randomised supply cache to keep long runs going.
        if (game.getRound() % 5 == 0) {
            spawnSupplyCache(level, game.getRound());
        }

        boolean canExtract = AbyssConfig.ENABLE_EXTRACTION.get();
        if (canExtract) {
            setExtractionGlyph(level, true);
        }
        for (ServerPlayer p : participantPlayers(level)) {
            title(p, "§a§lROUND " + game.getRound() + " CLEARED", "§7Next wave incoming...");
            level.playSound(null, p.blockPosition(), ModSounds.ROUND_CLEAR.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
            if (canExtract) {
                p.displayClientMessage(Component.literal(
                        "§b⟡ An extraction glyph flares to the south. §7Stand on it to leave with your spoils — or brave the next wave."), false);
            }
        }
    }

    // ------------------------------------------------------------------
    // Extraction gambit
    // ------------------------------------------------------------------

    private static final Map<UUID, Integer> EXTRACT_CHANNEL = new HashMap<>();

    /** Places or clears the glowing extraction glyph on the south approach. */
    private static void setExtractionGlyph(ServerLevel level, boolean show) {
        BlockPos c = game.getMap().extraction();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos p = c.offset(dx, 0, dz);
                boolean centre = dx == 0 && dz == 0;
                if (show) {
                    level.setBlock(p, (centre ? Blocks.SEA_LANTERN : Blocks.GILDED_BLACKSTONE).defaultBlockState(), 3);
                } else {
                    level.setBlock(p, Blocks.BLACKSTONE.defaultBlockState(), 3);
                }
            }
        }
        // A glowing beacon-beam of end rods marks it from across the arena.
        for (int dy = 1; dy <= 5; dy++) {
            level.setBlock(c.above(dy), show ? Blocks.END_ROD.defaultBlockState() : Blocks.AIR.defaultBlockState(), 3);
        }
        if (!show) {
            EXTRACT_CHANNEL.clear();
        }
    }

    /**
     * Handles players standing on the glyph between rounds. Returns true while
     * anyone is actively channelling, so the caller holds off the next round.
     */
    private static boolean tickExtraction(ServerLevel level, List<ServerPlayer> present) {
        if (!AbyssConfig.ENABLE_EXTRACTION.get() || game.getRound() < 1) {
            return false;
        }
        BlockPos glyph = game.getMap().extraction();
        int needed = AbyssConfig.EXTRACTION_CHANNEL_TICKS.get();
        boolean anyChannelling = false;

        for (ServerPlayer p : present) {
            if (p.getData(ModAttachments.RUN_STATE).isDowned()) {
                continue;
            }
            double dx = p.getX() - (glyph.getX() + 0.5);
            double dz = p.getZ() - (glyph.getZ() + 0.5);
            boolean onGlyph = (dx * dx + dz * dz) <= 2.25 && Math.abs(p.getY() - (glyph.getY() + 1)) <= 2.0;
            if (onGlyph) {
                int progress = EXTRACT_CHANNEL.merge(p.getUUID(), 1, Integer::sum);
                anyChannelling = true;
                if (progress >= needed) {
                    extractPlayer(level, p);
                } else {
                    int pct = (int) (100.0 * progress / needed);
                    actionBar(p, "§bExtracting... " + pct + "%  §7(stay on the glyph)");
                    if (progress % 6 == 0) {
                        level.playSound(null, p.blockPosition(), ModSounds.RITUAL_PROGRESS.get(), SoundSource.PLAYERS, 0.6F, 1.4F);
                    }
                }
            } else {
                EXTRACT_CHANNEL.remove(p.getUUID());
            }
        }
        return anyChannelling;
    }

    /** A player successfully bails: banked reward, no cooldown, run over for them. */
    private static void extractPlayer(ServerLevel abyssLevel, ServerPlayer player) {
        int round = game.getRound();
        RunState rs = player.getData(ModAttachments.RUN_STATE);
        boolean ritual = game.isRitualComplete();
        boolean multiplayer = game.isMultiplayerRun();
        EXTRACT_CHANNEL.remove(player.getUUID());

        MinecraftServer server = abyssLevel.getServer();
        ServerLevel homeLevel = resolveHome(server, rs);
        BlockPos returnPos = rs.getHomePortalPos() != null ? rs.getHomePortalPos() : homeLevel.getSharedSpawnPos();

        long now = System.currentTimeMillis();
        int survivalSeconds = rs.survivalSeconds(now);
        int killsThisRun = rs.getKillsThisRun();
        int revivesThisRun = rs.getRevivesThisRun();
        int prevBest = rs.getBestRound();
        rs.recordRound(round);

        player.removeAllEffects();
        player.setHealth(player.getMaxHealth());
        player.getFoodData().setFoodLevel(20);
        player.changeDimension(AbyssTeleporter.toFixedHome(homeLevel, returnPos));

        // Full reward for the round cleared, safely banked - and NO cooldown.
        spawnRewardChest(homeLevel, returnPos, RewardTable.rewardsFor(round, false, ritual));
        com.jrpetty.aztecabyss.data.AbyssStats.get(server).record(
                player.getUUID(), player.getGameProfile().getName(),
                round, survivalSeconds, rs.getTotalKills(), rs.getTotalRevives(), false, multiplayer, game.getMap().ordinal());
        com.jrpetty.aztecabyss.worldgen.MonumentBuilder.build(abyssLevel);
        ModNetworking.sendRecap(player, round, killsThisRun, revivesThisRun, survivalSeconds, prevBest,
                false, multiplayer, true, rs.getHeadshotsThisRun(), rs.getTotalDeaths(), ritual);

        rs.clearRun();
        player.setData(ModAttachments.RUN_STATE, rs);
        game.removeParticipant(player.getUUID());
        cleanupBar(player);
        ModNetworking.sendState(player, false, 0);
        player.displayClientMessage(Component.literal(
                "§bYou extracted from the Abyss after round " + round + " with your spoils. No cooldown — the portal stays open to you."), false);

        if (game.getParticipants().isEmpty()) {
            setExtractionGlyph(abyssLevel, false);
            resetSession();
        }
    }

    // ------------------------------------------------------------------
    // Downed / revive
    // ------------------------------------------------------------------

    /** Puts a participant into the downed state instead of killing them. */
    public static void downPlayer(ServerLevel level, ServerPlayer player) {
        RunState rs = player.getData(ModAttachments.RUN_STATE);
        if (rs.isDowned()) {
            return;
        }

        // Solo (or last one standing): no teammate can revive you, so the downed
        // grace period is pointless - a lethal hit just ends the run here.
        if (game.getParticipants().size() <= 1) {
            sendPlayerHome(level, player, game.getRound(), false, false);
            return;
        }
        rs.setDowned(true);
        rs.setBleedoutTicksLeft(AbyssConfig.BLEEDOUT_TICKS.get());
        rs.setReviveProgress(0);
        player.setData(ModAttachments.RUN_STATE, rs);

        player.setHealth(player.getMaxHealth());
        player.removeAllEffects();
        applyDownedEffects(player);
        level.playSound(null, player.blockPosition(), ModSounds.PLAYER_DOWNED.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        for (ServerPlayer p : participantPlayers(level)) {
            p.displayClientMessage(Component.literal("§c" + player.getGameProfile().getName() + " is down!"), false);
        }
    }

    private static void applyDownedEffects(ServerPlayer player) {
        int t = AbyssConfig.BLEEDOUT_TICKS.get() + 40;
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, t, 4, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, t, 4, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, t, 4, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.GLOWING, t, 0, false, false));
    }

    private static void tickDownedAndRevive(ServerLevel level, List<ServerPlayer> present) {
        double radius = AbyssConfig.REVIVE_RADIUS.get();
        int reviveTicks = AbyssConfig.REVIVE_TICKS.get();

        for (ServerPlayer downed : present) {
            RunState rs = downed.getData(ModAttachments.RUN_STATE);
            if (!rs.isDowned()) {
                continue;
            }

            ServerPlayer reviver = null;
            for (ServerPlayer other : present) {
                if (other == downed) {
                    continue;
                }
                if (!other.getData(ModAttachments.RUN_STATE).isDowned()
                        && other.distanceToSqr(downed) <= radius * radius) {
                    reviver = other;
                    break;
                }
            }

            if (reviver != null) {
                rs.setReviveProgress(rs.getReviveProgress() + 1);
                if (rs.getReviveProgress() >= reviveTicks) {
                    revivePlayer(level, downed, reviver);
                } else {
                    float pct = (float) rs.getReviveProgress() / reviveTicks * 100f;
                    actionBar(downed, "§eBeing revived... " + (int) pct + "%");
                    actionBar(reviver, "§eReviving " + downed.getGameProfile().getName() + "... " + (int) pct + "%");
                    downed.setData(ModAttachments.RUN_STATE, rs);
                }
            } else {
                rs.setReviveProgress(Math.max(0, rs.getReviveProgress() - 1));
                rs.setBleedoutTicksLeft(rs.getBleedoutTicksLeft() - 1);
                int secs = rs.getBleedoutTicksLeft() / 20;
                actionBar(downed, "§4DOWNED §7— bleeding out: " + secs + "s");
                downed.setData(ModAttachments.RUN_STATE, rs);
                if (rs.getBleedoutTicksLeft() <= 0) {
                    sendPlayerHome(level, downed, game.getRound(), false, false);
                }
            }
        }
    }

    private static void revivePlayer(ServerLevel level, ServerPlayer player, ServerPlayer reviver) {
        RunState rs = player.getData(ModAttachments.RUN_STATE);
        rs.setDowned(false);
        rs.setReviveProgress(0);
        rs.setBleedoutTicksLeft(0);
        player.setData(ModAttachments.RUN_STATE, rs);
        player.removeAllEffects();
        player.setHealth(player.getMaxHealth() * 0.5F);
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 60, 4, false, false));
        level.playSound(null, player.blockPosition(), ModSounds.PLAYER_REVIVED.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        actionBar(player, "§aRevived!");

        RunState reviverState = reviver.getData(ModAttachments.RUN_STATE);
        reviverState.addRevive();
        reviver.setData(ModAttachments.RUN_STATE, reviverState);
        actionBar(reviver, "§aRevived " + player.getGameProfile().getName() + "!");
    }

    // ------------------------------------------------------------------
    // Ending the run
    // ------------------------------------------------------------------

    private static void endGame(ServerLevel level, boolean victory) {
        game.setPhase(AbyssGame.Phase.ENDED);
        int round = game.getRound();
        List<ServerPlayer> present = participantPlayers(level);
        clearWaveMobs(level);
        for (ServerPlayer p : present) {
            sendPlayerHome(level, p, round, victory, true);
        }
        resetSession();
    }

    /**
     * Sends one player home with a round-scaled reward chest and (on death)
     * the re-entry cooldown, then drops them from the run.
     */
    /** Hands back the vault and pays out materials on the way out of the Outpost. */
    private static void settleOutpost(ServerPlayer player, int round) {
        if (!game.getMap().hasEconomy() || player.getServer() == null
                || !OutpostEconomy.hasVault(player.getServer(), player.getUUID())) {
            return; // nothing held at the door means this run was already settled
        }
        String paid = OutpostEconomy.payoutSummary(round);
        OutpostEconomy.leave(player, round);
        player.displayClientMessage(Component.literal(
                "§7Your gear is back. §fThe Outpost paid out: " + paid), false);
    }

    private static void sendPlayerHome(ServerLevel abyssLevel, ServerPlayer player, int round, boolean victory, boolean batched) {
        settleOutpost(player, round);
        RunState rs = player.getData(ModAttachments.RUN_STATE);
        boolean ritual = game.isRitualComplete();

        MinecraftServer server = abyssLevel.getServer();
        ServerLevel homeLevel = resolveHome(server, rs);
        BlockPos returnPos = rs.getHomePortalPos() != null ? rs.getHomePortalPos() : homeLevel.getSharedSpawnPos();

        player.removeAllEffects();
        player.setHealth(player.getMaxHealth());
        player.getFoodData().setFoodLevel(20);
        player.changeDimension(AbyssTeleporter.toFixedHome(homeLevel, returnPos));

        ItemStack[] loot = RewardTable.rewardsFor(round, victory, ritual);
        spawnRewardChest(homeLevel, returnPos, loot);

        if (!victory) {
            rs.setCooldownUntil(System.currentTimeMillis() + AbyssConfig.cooldownMillis());
            rs.addDeath();
        }
        ModNetworking.sendCooldown(player, rs.getCooldownUntil()); // drive the on-screen countdown
        rs.recordRound(round);

        long now = System.currentTimeMillis();
        int survivalSeconds = rs.survivalSeconds(now);
        int killsThisRun = rs.getKillsThisRun();
        int revivesThisRun = rs.getRevivesThisRun();
        boolean multiplayer = game.isMultiplayerRun();
        int prevBest = rs.getBestRound();

        com.jrpetty.aztecabyss.data.AbyssStats.get(server).record(
                player.getUUID(), player.getGameProfile().getName(),
                round, survivalSeconds, rs.getTotalKills(), rs.getTotalRevives(), victory, multiplayer, game.getMap().ordinal());
        com.jrpetty.aztecabyss.worldgen.MonumentBuilder.build(abyssLevel);

        // Death/victory recap screen data.
        ModNetworking.sendRecap(player, round, killsThisRun, revivesThisRun, survivalSeconds, prevBest,
                victory, multiplayer, false, rs.getHeadshotsThisRun(), rs.getTotalDeaths(), ritual);

        rs.clearRun();
        player.setData(ModAttachments.RUN_STATE, rs);

        game.removeParticipant(player.getUUID());
        cleanupBar(player);
        ModNetworking.sendState(player, false, 0);

        if (victory) {
            title(player, "§6§lTHE ABYSS IS SILENT", "§eRound " + round + " conquered. Claim your reward at home.");
        } else {
            player.displayClientMessage(Component.literal("§cYou fell on Round " + round
                    + ". Your reward is in the chest by your portal. The Abyss is sealed to you for "
                    + AbyssConfig.REENTRY_COOLDOWN_HOURS.get() + "h."), false);
        }

        if (!batched && game.getParticipants().isEmpty()) {
            resetSession();
        }
    }

    /** Voluntary retreat back through the arrival portal: home, no reward, no cooldown. */
    public static void abandonRun(ServerLevel abyssLevel, ServerPlayer player) {
        RunState rs = player.getData(ModAttachments.RUN_STATE);
        MinecraftServer server = abyssLevel.getServer();
        ServerLevel homeLevel = resolveHome(server, rs);
        BlockPos returnPos = rs.getHomePortalPos() != null ? rs.getHomePortalPos() : homeLevel.getSharedSpawnPos();

        player.removeAllEffects();
        player.changeDimension(AbyssTeleporter.toFixedHome(homeLevel, returnPos));
        rs.clearRun();
        player.setData(ModAttachments.RUN_STATE, rs);
        game.removeParticipant(player.getUUID());
        cleanupBar(player);
        ModNetworking.sendState(player, false, 0);
        player.displayClientMessage(Component.literal("§7You retreated from the Abyss empty-handed. No cooldown — try again whenever."), false);

        if (game.getParticipants().isEmpty()) {
            resetSession();
        }
    }

    /**
     * A participant logged out mid-run. We can't teleport an offline player, so
     * we bank what they're owed (reward round + cooldown) and settle it the next
     * time they log in via {@link #resolveOwedRewardOnLogin(ServerPlayer)}.
     */
    public static void onParticipantLoggedOut(ServerPlayer player) {
        if (!game.isParticipant(player.getUUID())) {
            return;
        }
        RunState rs = player.getData(ModAttachments.RUN_STATE);
        rs.setOwedRewardRound(Math.max(1, game.getRound()));
        rs.setCooldownUntil(System.currentTimeMillis() + AbyssConfig.cooldownMillis());
        rs.clearRun();
        player.setData(ModAttachments.RUN_STATE, rs);
        game.removeParticipant(player.getUUID());
        cleanupBar(player);
        if (game.getParticipants().isEmpty()) {
            resetSession();
        }
    }

    /** Settles a reward owed to a player who left mid-run, on their next login. */
    public static void resolveOwedRewardOnLogin(ServerPlayer player) {
        RunState rs = player.getData(ModAttachments.RUN_STATE);
        int round = rs.getOwedRewardRound();
        if (round <= 0) {
            return;
        }
        rs.setOwedRewardRound(0);
        rs.clearRun();
        player.setData(ModAttachments.RUN_STATE, rs);

        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        ServerLevel homeLevel = resolveHome(server, rs);
        BlockPos returnPos = rs.getHomePortalPos() != null ? rs.getHomePortalPos() : homeLevel.getSharedSpawnPos();
        if (player.level().dimension().equals(AztecAbyssConstants.ABYSS_LEVEL_KEY)) {
            player.changeDimension(AbyssTeleporter.toFixedHome(homeLevel, returnPos));
        }
        spawnRewardChest(homeLevel, returnPos, RewardTable.rewardsFor(round, false, false));
        player.displayClientMessage(Component.literal("§cYou left the Abyss mid-run on Round " + round
                + ". Your reward is waiting by your portal."), false);
    }

    /** Marks the shared ritual complete and rewards everyone present with a hint. */
    public static void onRitualComplete(ServerLevel level) {
        if (game.isRitualComplete()) {
            return;
        }
        game.setRitualComplete(true);
        for (ServerPlayer p : participantPlayers(level)) {
            level.playSound(null, p.blockPosition(), ModSounds.RITUAL_COMPLETE.get(), SoundSource.HOSTILE, 1.0F, 1.0F);
            title(p, "§5§lTHE OFFERING IS ACCEPTED", "§dThe vault grinds open. What waits inside, none can say...");
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static ServerLevel resolveHome(MinecraftServer server, RunState rs) {
        if (rs.getHomeDimension() != null) {
            ServerLevel l = server.getLevel(net.minecraft.resources.ResourceKey.create(
                    net.minecraft.core.registries.Registries.DIMENSION, rs.getHomeDimension()));
            if (l != null) {
                return l;
            }
        }
        return server.overworld();
    }

    private static void clearWaveMobs(ServerLevel level) {
        net.minecraft.world.scores.Scoreboard sb = level.getScoreboard();
        level.getEntitiesOfClass(Mob.class,
                        game.getMap().bounds(),
                        m -> m.getPersistentData().getBoolean("aztecabyss_wave_mob"))
                .forEach(m -> {
                    // Drop specialists off their colour team so the scoreboard
                    // doesn't accumulate dead UUIDs run after run.
                    sb.removePlayerFromTeam(m.getStringUUID());
                    m.remove(Entity.RemovalReason.DISCARDED);
                });
        game.setAliveZombies(0);
    }

    /** Positions of active supply-cache chests, cleared out when a new session starts. */
    private static final List<BlockPos> CACHE_MARKERS = new ArrayList<>();

    /** Drops a randomised supply cache somewhere on the open arena floor, flare and all. */
    private static void spawnSupplyCache(ServerLevel level, int round) {
        BlockPos pos;
        if (game.getMap() == com.jrpetty.aztecabyss.worldgen.ArenaMap.OUTPOST) {
            // In the hall, which is the one room always open to the squad.
            pos = new BlockPos(
                    com.jrpetty.aztecabyss.worldgen.OutpostBuilder.CENTER_X - 6 - RNG.nextInt(5),
                    com.jrpetty.aztecabyss.worldgen.OutpostBuilder.FLOOR_Y,
                    com.jrpetty.aztecabyss.worldgen.OutpostBuilder.CENTER_Z - 4 + RNG.nextInt(9));
        } else if (game.getMap() == com.jrpetty.aztecabyss.worldgen.ArenaMap.BRIDGE) {
            // Lands on the island, near the fort, so it's grabbable between waves.
            double a = RNG.nextDouble() * Math.PI * 2.0;
            int rr = 6 + RNG.nextInt(8);
            pos = new BlockPos(
                    com.jrpetty.aztecabyss.worldgen.BridgeBuilder.CENTER_X + (int) Math.round(Math.cos(a) * rr),
                    com.jrpetty.aztecabyss.worldgen.BridgeBuilder.DECK_Y + 1,
                    com.jrpetty.aztecabyss.worldgen.BridgeBuilder.ISLAND_CENTER_Z + 10 + (int) Math.round(Math.sin(a) * rr));
        } else {
            double angle = RNG.nextDouble() * Math.PI * 2.0;
            int r = 28 + RNG.nextInt(18); // 28-45: clear of the temple base, well inside the arena
            pos = new BlockPos((int) Math.round(Math.cos(angle) * r),
                    AztecAbyssConstants.ARENA_FLOOR_Y + 1,
                    (int) Math.round(Math.sin(angle) * r));
        }
        int x = pos.getX();
        int z = pos.getZ();

        SupplyCache.Result cache = SupplyCache.roll(round);
        level.setBlock(pos, Blocks.CHEST.defaultBlockState(), 3);
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof ChestBlockEntity chest) {
            int slot = 0;
            for (ItemStack stack : cache.loot()) {
                if (!stack.isEmpty() && slot < chest.getContainerSize()) {
                    chest.setItem(slot++, stack);
                }
            }
        }
        // A slim end-rod flare above the chest so it's findable, even through fog.
        for (int dy = 2; dy <= 4; dy++) {
            level.setBlock(pos.above(dy), Blocks.END_ROD.defaultBlockState(), 3);
        }
        CACHE_MARKERS.add(pos);

        String dir = Math.abs(x) > Math.abs(z) ? (x > 0 ? "east" : "west") : (z > 0 ? "south" : "north");
        for (ServerPlayer p : participantPlayers(level)) {
            title(p, "§6§l✦ SUPPLY CACHE", cache.flavor());
            p.displayClientMessage(Component.literal(
                    "§6✦ A supply cache thuds down to the §e" + dir + "§6 of the temple. §7Grab it before the next wave."), false);
            level.playSound(null, p.blockPosition(), net.minecraft.sounds.SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8F, 1.4F);
        }
    }

    /** Removes leftover cache chests + flares from a prior run when a new session begins. */
    private static void clearSupplyCaches(ServerLevel level) {
        for (BlockPos p : CACHE_MARKERS) {
            if (level.getBlockState(p).is(Blocks.CHEST)) {
                level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
            }
            for (int dy = 2; dy <= 4; dy++) {
                BlockPos m = p.above(dy);
                if (level.getBlockState(m).is(Blocks.END_ROD)) {
                    level.setBlock(m, Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
        CACHE_MARKERS.clear();
    }

    private static void spawnRewardChest(ServerLevel level, BlockPos near, ItemStack[] loot) {
        BlockPos chestPos = findChestSpot(level, near);
        level.setBlock(chestPos, Blocks.CHEST.defaultBlockState(), 3);
        BlockEntity be = level.getBlockEntity(chestPos);
        if (be instanceof ChestBlockEntity chest) {
            int slot = 0;
            for (ItemStack stack : loot) {
                if (!stack.isEmpty() && slot < chest.getContainerSize()) {
                    chest.setItem(slot++, stack);
                }
            }
        } else {
            for (ItemStack stack : loot) {
                if (!stack.isEmpty()) {
                    level.addFreshEntity(new ItemEntity(level, chestPos.getX() + 0.5, chestPos.getY() + 0.5, chestPos.getZ() + 0.5, stack));
                }
            }
        }
    }

    private static BlockPos findChestSpot(ServerLevel level, BlockPos near) {
        for (int r = 1; r <= 6; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, near.offset(dx, 0, dz));
                    if (level.getBlockState(ground).isAir() && level.getBlockState(ground.below()).isSolid()) {
                        return ground;
                    }
                }
            }
        }
        return near.above();
    }

    /** Pushes the live HUD figures (round, enemies left, squad headcount, kills) to each player. */
    private static void broadcastHud(ServerLevel level) {
        List<ServerPlayer> present = participantPlayers(level);
        if (present.isEmpty()) {
            return;
        }
        int total = present.size();
        int up = 0;
        for (ServerPlayer p : present) {
            if (!p.getData(ModAttachments.RUN_STATE).isDowned()) {
                up++;
            }
        }
        // On a boss round the counter shows the summoned adds still alive; otherwise
        // it's the wave remaining to clear.
        int enemies = game.isBossRound()
                ? game.getAliveZombies()
                : Math.max(0, game.getKillsNeededThisRound() - game.getKillsThisRound());
        int gateBoards = Barricade.packed(game.getMap());
        for (ServerPlayer p : present) {
            int myKills = p.getData(ModAttachments.RUN_STATE).getKillsThisRun();

            // Only push the HUD when something a player can actually see changed;
            // this used to fire twice a second per player regardless. Rolled as a
            // multiplicative hash rather than the old hand-placed decimal offsets,
            // which had started to overlap once there were this many fields - and
            // gate boards have to be in it, or a board falling would never show.
            long stamp = game.getRound();
            stamp = stamp * 8191L + enemies;
            stamp = stamp * 8191L + up;
            stamp = stamp * 8191L + total;
            stamp = stamp * 8191L + myKills;
            stamp = stamp * 8191L + gateBoards;
            stamp = stamp * 2L + (game.isFogRound() ? 1L : 0L);
            Long last = LAST_HUD_STAMP.get(p.getUUID());
            if (last == null || last != stamp) {
                LAST_HUD_STAMP.put(p.getUUID(), stamp);
                ModNetworking.sendHud(p, game.getRound(), game.isFogRound(), enemies, up, total, myKills, gateBoards);
            }

            // Squad panel only matters in co-op - skip the whole build solo.
            if (total <= 1) {
                continue;
            }
            List<com.jrpetty.aztecabyss.network.TeammateInfo> mates = new ArrayList<>();
            for (ServerPlayer o : present) {
                if (o == p) {
                    continue;
                }
                int hp = (int) Math.ceil(o.getHealth() / Math.max(1.0F, o.getMaxHealth()) * 100.0);
                hp = Math.max(0, Math.min(100, hp));
                mates.add(new com.jrpetty.aztecabyss.network.TeammateInfo(
                        o.getGameProfile().getName(), hp, o.getData(ModAttachments.RUN_STATE).isDowned(),
                        o.getBlockX(), o.getBlockY(), o.getBlockZ()));
            }
            ModNetworking.sendSquad(p, mates);
        }
    }

    /** Last HUD payload fingerprint per player, so unchanged frames aren't resent. */
    private static final Map<UUID, Long> LAST_HUD_STAMP = new HashMap<>();

    /** A player pinged a spot: flash a marker there and call it out to the squad. */
    public static void onPing(ServerPlayer player, BlockPos target) {
        if (!game.isParticipant(player.getUUID()) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        double tx = target.getX() + 0.5;
        double ty = target.getY();
        double tz = target.getZ() + 0.5;
        for (int dy = 0; dy < 8; dy++) {
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, tx, ty + dy, tz, 2, 0.15, 0.2, 0.15, 0.0);
        }
        level.sendParticles(ParticleTypes.END_ROD, tx, ty + 0.5, tz, 8, 0.3, 0.3, 0.3, 0.0);
        String who = player.getGameProfile().getName();
        for (ServerPlayer p : participantPlayers(level)) {
            p.displayClientMessage(Component.literal("§b⚑ " + who + " pinged a location."), true);
            level.playSound(null, p.blockPosition(), ModSounds.RITUAL_PROGRESS.get(), SoundSource.PLAYERS, 0.7F, 1.6F);
        }
    }

    private static List<ServerPlayer> participantPlayers(ServerLevel level) {
        List<ServerPlayer> out = new ArrayList<>();
        for (UUID id : game.getParticipants()) {
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
            if (p != null && p.level() == level) {
                out.add(p);
            }
        }
        return out;
    }

    /** The closest standing hunter to a point - wave mobs always hunt the nearest. */
    private static ServerPlayer nearestTarget(List<ServerPlayer> present, BlockPos from) {
        ServerPlayer best = null;
        double bestDist = Double.MAX_VALUE;
        for (ServerPlayer p : present) {
            if (p.getData(ModAttachments.RUN_STATE).isDowned()) {
                continue;
            }
            double d = p.distanceToSqr(from.getX() + 0.5, from.getY(), from.getZ() + 0.5);
            if (d < bestDist) {
                bestDist = d;
                best = p;
            }
        }
        return best != null ? best : pickTarget(present);
    }

    /** Ticks in the arena before a wave mob is assumed stuck and pulled back to a gate. */
    private static final long STUCK_TICKS = 3600L; // 3 minutes

    /**
     * Sweeps the arena for wave mobs that have been alive too long - usually
     * stuck on geometry or pathing nowhere - and teleports them back to a gate
     * with a fresh target, so a wave can never stall on a glitched straggler.
     */
    private static void repatriateStuckMobs(ServerLevel level, List<ServerPlayer> present, List<Mob> mobs) {
        long now = level.getGameTime();
        BlockPos[] gates = game.getMap().gates();
        for (Mob mob : mobs) {
            if (mob.getPersistentData().getBoolean("aztecabyss_boss")) {
                continue;
            }
            // Penned mobs stand at the boards for as long as it takes; that is the
            // mechanic, not a glitch. The barricade tick keeps their clock fresh,
            // and this is the belt to that pair of braces.
            if (mob.getPersistentData().getInt("aztecabyss_gate_index") >= 0) {
                continue;
            }
            long since = now - mob.getPersistentData().getLong("aztecabyss_gate_tick");
            if (since < STUCK_TICKS) {
                continue;
            }
            BlockPos gate = gates[RNG.nextInt(gates.length)];
            mob.teleportTo(gate.getX() + 0.5, gate.getY(), gate.getZ() + 0.5);
            mob.getPersistentData().putLong("aztecabyss_gate_tick", now);
            ServerPlayer target = nearestTarget(present, gate);
            if (target != null) {
                mob.setTarget(target);
            }
            level.sendParticles(ParticleTypes.PORTAL,
                    gate.getX() + 0.5, gate.getY() + 1.0, gate.getZ() + 0.5, 10, 0.4, 0.7, 0.4, 0.05);
        }
    }

    /** How close a hunter has to be to pull the horde's attention off the objective. */
    private static final double AGGRO_RADIUS = 7.0;
    /** How long a mob stays fixated on whoever hurt it. */
    private static final long PROVOKE_TICKS = 200L;

    /**
     * Drives horde intent. On maps with something to defend the mobs march on the
     * objective by default; a hunter who stands in the way (or who has recently
     * hit them) pulls them off it. On maps with no objective they simply hunt the
     * nearest hunter as before.
     */
    private static void retargetWaveMobs(ServerLevel level, List<ServerPlayer> present, List<Mob> mobs) {
        BlockPos objective = game.hasLiveObjective() ? game.getMap().objective() : null;
        long now = level.getGameTime();

        for (Mob mob : mobs) {
            // Anything still behind the boards belongs to the barricade tick - it
            // has no business being pointed at a player it cannot reach.
            if (mob.getPersistentData().getInt("aztecabyss_gate_index") >= 0) {
                continue;
            }

            specialistApproachCue(level, mob, present);

            // Breakers are single-minded: no aggro, no provoking, no distractions.
            // The only way to stop one is to put it down.
            if (objective != null
                    && mob.getPersistentData().getInt("aztecabyss_role") == ROLE_BREAKER) {
                mob.setTarget(null);
                mob.getNavigation().moveTo(objective.getX() + 0.5, objective.getY(), objective.getZ() + 0.5, 1.35);
                continue;
            }

            // Still fixated on whoever last hurt it?
            long provokedUntil = mob.getPersistentData().getLong("aztecabyss_provoked_until");
            net.minecraft.world.entity.LivingEntity current = mob.getTarget();
            if (now < provokedUntil && current instanceof ServerPlayer p && p.isAlive()
                    && !p.getData(ModAttachments.RUN_STATE).isDowned()) {
                continue;
            }

            // A hunter standing in its path takes priority over the objective.
            ServerPlayer blocking = nearestTargetWithin(present, mob.blockPosition(), AGGRO_RADIUS);
            if (blocking != null) {
                mob.setTarget(blocking);
                continue;
            }

            if (objective == null) {
                if (current == null || !current.isAlive()) {
                    ServerPlayer target = nearestTarget(present, mob.blockPosition());
                    if (target != null) {
                        mob.setTarget(target);
                    }
                }
                continue;
            }

            // Otherwise: march on the thing they came here to break.
            mob.setTarget(null);
            if (mob.getNavigation().isDone() || now % 40L == 0L) {
                mob.getNavigation().moveTo(objective.getX() + 0.5, objective.getY(), objective.getZ() + 0.5, 1.0);
            }
        }
    }

    /**
     * A sparse "something is coming" cue for the two specialists, ridden off the
     * existing 2-second retarget sweep so it costs nothing extra.
     *
     * Deliberately restrained: it only fires while the thing is still at range
     * (so it's a warning, not a nag once it's on top of you), it's randomised so
     * it never turns metronomic, and it's carried at a volume that reaches down
     * the bridge without being shrill. Breakers keen; Sappers toll.
     */
    private static void specialistApproachCue(ServerLevel level, Mob mob, List<ServerPlayer> present) {
        int role = mob.getPersistentData().getInt("aztecabyss_role");
        if (role == ROLE_NORMAL || present.isEmpty()) {
            return;
        }
        // Roughly every other sweep, so ~4s apart rather than a constant drone.
        if (RNG.nextInt(2) != 0) {
            return;
        }
        // Only while it's still approaching - once it's among you, you can see it.
        ServerPlayer nearest = nearestTarget(present, mob.blockPosition());
        if (nearest == null || nearest.distanceToSqr(mob) < 14.0 * 14.0) {
            return;
        }
        if (role == ROLE_BREAKER) {
            // Thin, high keen - urgent without being harsh.
            level.playSound(null, mob.blockPosition(), net.minecraft.sounds.SoundEvents.RAVAGER_ROAR,
                    SoundSource.HOSTILE, 1.4F, 1.9F);
        } else {
            // Deep, slow toll of something heavy and armoured.
            level.playSound(null, mob.blockPosition(), net.minecraft.sounds.SoundEvents.ANVIL_LAND,
                    SoundSource.HOSTILE, 1.6F, 0.45F);
        }
    }

    /** Nearest standing hunter within a radius, or null. */
    private static ServerPlayer nearestTargetWithin(List<ServerPlayer> present, BlockPos from, double radius) {
        ServerPlayer best = null;
        double bestDist = radius * radius;
        for (ServerPlayer p : present) {
            if (p.getData(ModAttachments.RUN_STATE).isDowned()) {
                continue;
            }
            double d = p.distanceToSqr(from.getX() + 0.5, from.getY(), from.getZ() + 0.5);
            if (d <= bestDist) {
                bestDist = d;
                best = p;
            }
        }
        return best;
    }

    /** A player hit a wave mob: it turns on them for a while. Breakers never care. */
    public static void provokeMob(Mob mob, ServerPlayer by, long gameTime) {
        if (mob.getPersistentData().getInt("aztecabyss_role") == ROLE_BREAKER) {
            return;
        }
        mob.getPersistentData().putLong("aztecabyss_provoked_until", gameTime + PROVOKE_TICKS);
        mob.setTarget(by);
    }

    // ------------------------------------------------------------------
    // Barricades - the boarded gates on maps that have them
    // ------------------------------------------------------------------

    /** How often the boards are worked, in ticks. One second is plenty. */
    private static final int BARRICADE_INTERVAL = 20;
    /** How close a penned mob has to be to the mouth to be working on the boards. */
    private static final double TEAR_REACH = 3.0;
    /** Effort per second by role: Breakers rip, Sappers heave, the rest just claw. */
    private static final float TEAR_RATE_NORMAL = 1.0f;
    private static final float TEAR_RATE_BREAKER = 2.0f;
    private static final float TEAR_RATE_SAPPER = 1.6f;
    /** A penned mob's patience, in ticks, before it starts tearing in earnest. */
    private static final long RAGE_TICKS = 600L;   // 30s -> doubles
    private static final long BERSERK_TICKS = 1200L; // 60s -> six times

    /** How long a player must wait between nailing boards back on. */
    private static final int REPAIR_COOLDOWN_TICKS = 25;
    private static final Map<UUID, Long> LAST_REPAIR = new HashMap<>();

    /**
     * Works every boarded gate: penned mobs walk up, tear, and are turned loose
     * into the arena the moment the last board comes off.
     *
     * <p>The escalation is the important part. A single mob against a dedicated
     * repairer would otherwise be a stalemate, and a stalemate here is fatal -
     * the round can never end while anything is still penned. So patience runs
     * out: after thirty seconds a held mob tears twice as fast, after a minute
     * six times, which outruns any number of hands on the boards. Barricades buy
     * time, and only time. They are never a way to win the round.
     *
     * <p>Costs four small box queries a second and nothing else; blocks are only
     * written when a board actually changes.
     */
    private static void tickBarricades(ServerLevel level, List<ServerPlayer> present) {
        com.jrpetty.aztecabyss.worldgen.ArenaMap map = game.getMap();
        BlockPos[] gates = map.gates();
        long now = level.getGameTime();
        for (int i = 0; i < gates.length; i++) {
            final int gateIndex = i;
            BlockPos gate = gates[i];
            List<Mob> penned = level.getEntitiesOfClass(Mob.class, Barricade.penBounds(map, i),
                    m -> m.getPersistentData().getBoolean("aztecabyss_wave_mob")
                            && !m.getPersistentData().getBoolean("aztecabyss_boss")
                            && m.getPersistentData().getInt("aztecabyss_gate_index") == gateIndex);
            if (penned.isEmpty()) {
                continue;
            }

            boolean open = Barricade.isOpen(i);
            float effort = 0.0f;
            for (Mob mob : penned) {
                // Penned mobs stand still by design - keep the stuck-sweep off them
                // or they'll be teleported away mid-tear and read as vanishing.
                mob.getPersistentData().putLong("aztecabyss_gate_tick", now);

                if (open) {
                    releaseFromPen(level, mob, present);
                    continue;
                }

                mob.setTarget(null);
                double distSqr = mob.distanceToSqr(gate.getX() + 0.5, gate.getY(), gate.getZ() + 0.5);
                if (distSqr > TEAR_REACH * TEAR_REACH) {
                    // Still walking up to the boards.
                    if (mob.getNavigation().isDone()) {
                        mob.getNavigation().moveTo(gate.getX() + 0.5, gate.getY(), gate.getZ() + 0.5, 1.0);
                    }
                    continue;
                }

                mob.getNavigation().stop();
                mob.getLookControl().setLookAt(gate.getX() + 0.5, gate.getY() + 1.5, gate.getZ() + 0.5);
                mob.swing(net.minecraft.world.InteractionHand.MAIN_HAND);

                long held = now - mob.getPersistentData().getLong("aztecabyss_penned_at");
                float rage = held >= BERSERK_TICKS ? 6.0f : held >= RAGE_TICKS ? 2.0f : 1.0f;
                effort += roleTearRate(mob.getPersistentData().getInt("aztecabyss_role")) * rage;
            }

            if (open || effort <= 0.0f) {
                continue;
            }
            // One second of work per tick of this loop, plus a late-run bite:
            // by round 30 the horde works a board loose half again as fast.
            float lateBite = 1.0f + Math.min(0.8f, Math.max(0, game.getRound() - 12) * 0.04f);
            int fell = Barricade.addEffort(level, map, i,
                    effort * (BARRICADE_INTERVAL / 20.0f) * lateBite);
            if (fell > 0) {
                onBoardTorn(level, present, i, gate);
            }
        }
    }

    private static float roleTearRate(int role) {
        return switch (role) {
            case ROLE_BREAKER -> TEAR_RATE_BREAKER;
            case ROLE_SAPPER -> TEAR_RATE_SAPPER;
            default -> TEAR_RATE_NORMAL;
        };
    }

    /** A board just came off: sound it, and call out a gate that's nearly gone. */
    private static void onBoardTorn(ServerLevel level, List<ServerPlayer> present, int gateIndex, BlockPos gate) {
        int left = Barricade.count(gateIndex);
        barricadeSound(level, gate, net.minecraft.sounds.SoundEvents.WOOD_BREAK, 1.6F, 0.7F);

        if (left == 0) {
            // The gate is open. This is the loud moment.
            barricadeSound(level, gate, net.minecraft.sounds.SoundEvents.RAVAGER_ROAR, 1.5F, 0.6F);
            for (ServerPlayer p : present) {
                actionBar(p, "§4§l✖ " + game.getMap().gateLabel(gateIndex) + " "
                        + game.getMap().gateNoun() + " IS OPEN");
                level.playSound(null, p.blockPosition(), net.minecraft.sounds.SoundEvents.ANVIL_LAND,
                        SoundSource.HOSTILE, 0.8F, 0.5F);
            }
        } else if (left == 1) {
            for (ServerPlayer p : present) {
                actionBar(p, "§c⚠ §f" + game.getMap().gateLabel(gateIndex) + "§c is about to fall");
            }
        }
    }

    /** Turns a mob loose into the arena once its gate has been stripped. */
    private static void releaseFromPen(ServerLevel level, Mob mob, List<ServerPlayer> present) {
        mob.getPersistentData().putInt("aztecabyss_gate_index", -1);
        // Clambering through the gap costs it a moment - the reward for having
        // held the gate as long as you did.
        mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 25, 2, false, false));
        ServerPlayer target = nearestTarget(present, mob.blockPosition());
        if (target != null && mob.getPersistentData().getInt("aztecabyss_role") != ROLE_BREAKER) {
            mob.setTarget(target);
        }
    }

    /**
     * Nails one board back onto a gate. Free by design: the price is the seconds
     * you spend stood at the mouth with your back to the arena, not an item.
     *
     * @return true if a board actually went back on
     */
    public static boolean repairBarricade(ServerLevel level, ServerPlayer player, int gateIndex) {
        com.jrpetty.aztecabyss.worldgen.ArenaMap map = game.getMap();
        if (!map.hasBarricades() || !game.isParticipant(player.getUUID())) {
            return false;
        }
        long now = level.getGameTime();
        boolean quick = OutpostShop.hasPerk(player.getMainHandItem(), OutpostShop.Perk.BOARDWRIGHT);
        Long last = LAST_REPAIR.get(player.getUUID());
        if (!quick && last != null && now - last < REPAIR_COOLDOWN_TICKS) {
            return false; // still hammering the previous one
        }
        if (Barricade.count(gateIndex) >= Barricade.MAX_BOARDS) {
            actionBar(player, "§7" + map.gateLabel(gateIndex) + " is sound.");
            return false;
        }
        LAST_REPAIR.put(player.getUUID(), now);
        if (!Barricade.repair(level, map, gateIndex)) {
            return false;
        }
        int left = Barricade.count(gateIndex);
        if (map.hasEconomy()) {
            OutpostEconomy.award(player, OutpostEconomy.POINTS_BOARD);
        }
        BlockPos gate = map.gates()[gateIndex];
        barricadeSound(level, gate, net.minecraft.sounds.SoundEvents.WOOD_PLACE, 1.2F, 0.9F);
        actionBar(player, left >= Barricade.MAX_BOARDS
                ? "§a✔ " + map.gateLabel(gateIndex) + " is sound again"
                : "§e⚒ Board " + left + "§7/" + Barricade.MAX_BOARDS + " §e- " + map.gateLabel(gateIndex));
        return true;
    }

    /**
     * All barricade audio goes through here, typed as a plain {@link SoundEvent}.
     * The sound constants in this mapping are a mix of bare events and holders,
     * and mixing the two has cost us builds before; funnelling them through one
     * signature makes any such mismatch a one-line fix instead of a hunt.
     */
    private static void barricadeSound(ServerLevel level, BlockPos pos,
                                       net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
        level.playSound(null, pos, sound, SoundSource.HOSTILE, volume, pitch);
    }

    /** How often the objective is sampled, in ticks. */
    private static final int OBJECTIVE_INTERVAL = 10;

    /** Fraction of the Heart's total health restored per diamond. */
    private static final float REPAIR_FRACTION_PER_DIAMOND = 0.08f;

    /** Fraction of the Heart's total health knitted back each between-round tick. */
    private static final float REGEN_FRACTION_PER_TICK = 0.01f;

    /**
     * Feeds a diamond to the Heart to mend it. Deliberately costs the one thing
     * players actually want to walk away with, so patching the Heart is a real
     * decision: spend your diamonds now, or keep them and hope the wall holds.
     *
     * @return true if a diamond was consumed
     */
    public static boolean repairObjective(ServerLevel level, ServerPlayer player) {
        if (!game.hasLiveObjective() || !game.isParticipant(player.getUUID())) {
            return false;
        }
        if (game.getObjectiveHealth() >= AbyssGame.OBJECTIVE_MAX_HEALTH) {
            actionBar(player, "§7The Heart is already whole.");
            return false;
        }
        game.setObjectiveHealth(game.getObjectiveHealth()
                + AbyssGame.OBJECTIVE_MAX_HEALTH * REPAIR_FRACTION_PER_DIAMOND);

        BlockPos heart = game.getMap().objective();
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                heart.getX() + 0.5, heart.getY() + 0.8, heart.getZ() + 0.5, 14, 0.5, 0.5, 0.5, 0.05);
        level.playSound(null, heart, net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.PLAYERS, 0.9F, 1.2F);

        int pct = (int) (game.objectiveFraction() * 100);
        for (ServerPlayer p : participantPlayers(level)) {
            p.displayClientMessage(Component.literal(
                    "§b❤ " + player.getGameProfile().getName() + " mends the Heart §7— now " + pct + "%"), true);
        }
        updateHeartBars();
        return true;
    }

    /**
     * Tells everyone the Heart is being hit, and - crucially on a map 135 blocks
     * long - which way to run. Players already standing on it just get the alert.
     */
    private static void warnHeartUnderAttack(List<ServerPlayer> present, BlockPos heart) {
        for (ServerPlayer p : present) {
            double dx = (heart.getX() + 0.5) - p.getX();
            double dz = (heart.getZ() + 0.5) - p.getZ();
            int dist = (int) Math.sqrt(dx * dx + dz * dz);
            if (dist <= 6) {
                actionBar(p, "§4⚠ THE HEART IS UNDER ATTACK!");
                continue;
            }
            String dir = Math.abs(dx) > Math.abs(dz)
                    ? (dx > 0 ? "EAST" : "WEST")
                    : (dz > 0 ? "SOUTH" : "NORTH");
            actionBar(p, "§4⚠ THE HEART IS UNDER ATTACK §7— §c" + dist + "m " + dir);
        }
    }

    /**
     * Mobs crowding the objective chew through it. Damage scales with how many
     * are on it, so letting the horde reach the Heart is visibly punishing.
     */
    private static void tickObjective(ServerLevel level, List<ServerPlayer> present) {
        BlockPos heart = game.getMap().objective();
        if (heart == null) {
            return;
        }
        if (game.getObjectiveHealth() <= 0.0f) {
            return;
        }

        // Sampled every 10 ticks rather than every tick - a full entity sweep per
        // tick was the single most expensive thing the mode did. Damage is scaled
        // by the interval so the felt rate is unchanged.
        long now = level.getGameTime();
        if (now % OBJECTIVE_INTERVAL == 0L) {
            double reach = 3.0;
            net.minecraft.world.phys.AABB near = new net.minecraft.world.phys.AABB(heart).inflate(reach);
            List<Mob> onIt = level.getEntitiesOfClass(Mob.class, near,
                    m -> m.getPersistentData().getBoolean("aztecabyss_wave_mob"));

            if (!onIt.isEmpty()) {
                // Sappers hit five times as hard as rank-and-file, breakers twice.
                float dmg = 0.0f;
                for (Mob m : onIt) {
                    dmg += roleHeartDamage(m.getPersistentData().getInt("aztecabyss_role"));
                }
                game.setObjectiveHealth(game.getObjectiveHealth() - dmg * OBJECTIVE_INTERVAL);
                level.sendParticles(ParticleTypes.CRIT,
                        heart.getX() + 0.5, heart.getY() + 0.5, heart.getZ() + 0.5, 6, 0.4, 0.4, 0.4, 0.1);
                if (now % 20L == 0L) {
                    level.playSound(null, heart, net.minecraft.sounds.SoundEvents.ANVIL_LAND,
                            SoundSource.HOSTILE, 0.6F, 0.6F);
                    warnHeartUnderAttack(present, heart);
                }
            } else if (now % 60L == 0L && game.getPhase() == AbyssGame.Phase.BETWEEN_ROUNDS) {
                // Slowly knits itself back together during the breather. Kept as a
                // fraction of the pool so retuning the Heart's health never silently
                // changes how much of it the breather hands back.
                game.setObjectiveHealth(game.getObjectiveHealth()
                        + AbyssGame.OBJECTIVE_MAX_HEALTH * REGEN_FRACTION_PER_TICK);
            }
        }

        // Ambient glow so it always reads as the thing to protect.
        if (now % 10L == 0L) {
            level.sendParticles(ParticleTypes.END_ROD,
                    heart.getX() + 0.5, heart.getY() + 0.9, heart.getZ() + 0.5, 2, 0.25, 0.25, 0.25, 0.01);
        }

        if (game.getObjectiveHealth() <= 0.0f) {
            for (ServerPlayer p : present) {
                title(p, "§4§lTHE HEART HAS FALLEN", "§cThe hold is broken.");
                level.playSound(null, heart, net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE.value(),
                        SoundSource.HOSTILE, 1.4F, 0.5F);
            }
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                    heart.getX() + 0.5, heart.getY() + 0.5, heart.getZ() + 0.5, 1, 0, 0, 0, 0);
            endGame(level, false);
        }
    }

    /** Per-player health bar for the objective, shown under the round bar. */
    private static final Map<UUID, ServerBossEvent> HEART_BARS = new HashMap<>();

    private static void setupHeartBar(ServerPlayer player) {
        if (game.getMap().objective() == null) {
            return;
        }
        ServerBossEvent bar = new ServerBossEvent(Component.literal("§b❤ The Heart"),
                BossEvent.BossBarColor.BLUE, BossEvent.BossBarOverlay.PROGRESS);
        bar.setProgress(game.objectiveFraction());
        bar.addPlayer(player);
        HEART_BARS.put(player.getUUID(), bar);
    }

    private static void updateHeartBars() {
        if (HEART_BARS.isEmpty()) {
            return;
        }
        float frac = game.objectiveFraction();
        for (ServerBossEvent bar : HEART_BARS.values()) {
            bar.setProgress(Math.max(0.0f, Math.min(1.0f, frac)));
            bar.setColor(frac > 0.5f ? BossEvent.BossBarColor.BLUE
                    : frac > 0.25f ? BossEvent.BossBarColor.YELLOW : BossEvent.BossBarColor.RED);
        }
    }

    private static ServerPlayer pickTarget(List<ServerPlayer> present) {
        List<ServerPlayer> up = new ArrayList<>();
        for (ServerPlayer p : present) {
            if (!p.getData(ModAttachments.RUN_STATE).isDowned()) {
                up.add(p);
            }
        }
        if (up.isEmpty()) {
            return present.isEmpty() ? null : present.get(0);
        }
        return up.get(RNG.nextInt(up.size()));
    }

    private static void setupBossBar(ServerPlayer player) {
        ServerBossEvent bar = new ServerBossEvent(Component.literal("The Aztec Abyss"),
                BossEvent.BossBarColor.WHITE, BossEvent.BossBarOverlay.PROGRESS);
        bar.setProgress(0.0F);
        bar.addPlayer(player);
        BOSS_BARS.put(player.getUUID(), bar);
    }

    /**
     * Refreshes every round bar, and on economy maps writes each player's own
     * balance into their own bar.
     *
     * <p>Points were previously only ever shown in the message that fired when
     * you bought something, which made the whole economy guesswork - you could
     * not tell whether you were saving for the Crucible or nowhere near it. The
     * bar is the right home for it: it is already per-player, already on screen,
     * and needs no packet of its own.
     */
    private static void updateBossBars(ServerLevel level) {
        if (game.getMap().hasEconomy()) {
            for (Map.Entry<UUID, ServerBossEvent> e : BOSS_BARS.entrySet()) {
                ServerPlayer p = level.getPlayerByUUID(e.getKey()) instanceof ServerPlayer sp ? sp : null;
                if (p == null) {
                    continue;
                }
                e.getValue().setName(Component.literal(
                        "§6✦ §fRound " + game.getRound()
                                + " §8| §e" + OutpostEconomy.points(e.getKey()) + " pts"
                                + (game.isFogRound() ? " §8| §7≈ fog" : "")));
            }
        }
        float progress;
        if (game.isBossRound() && game.isBossActive()) {
            // During a boss round the bar tracks the Warden's remaining health.
            progress = Math.max(0.0F, Math.min(1.0F, game.getBossHealthFraction()));
        } else {
            if (game.getKillsNeededThisRound() <= 0) {
                return;
            }
            progress = Math.max(0.0F, Math.min(1.0F, (float) game.getKillsThisRound() / game.getKillsNeededThisRound()));
        }
        for (ServerBossEvent bar : BOSS_BARS.values()) {
            bar.setProgress(progress);
        }
    }

    private static void cleanupBar(ServerPlayer player) {
        ServerBossEvent bar = BOSS_BARS.remove(player.getUUID());
        if (bar != null) {
            bar.removeAllPlayers();
        }
        ServerBossEvent heart = HEART_BARS.remove(player.getUUID());
        if (heart != null) {
            heart.removeAllPlayers();
        }
    }

    /** Where head-count growth gives way to the horde simply getting worse. */
    private static final int SOFTEN_AFTER = 20;

    /**
     * Wave size. Grows flat-out to round 20, then deliberately slows.
     *
     * <p>Past that point the difficulty has to come from what each mob is, not
     * how many there are: a linearly-growing count turns round 50 into ten
     * minutes of mopping up, and pins the concurrency ceiling for all of it.
     */
    private static int totalZombies(int round) {
        int per = AbyssConfig.ZOMBIES_PER_ROUND.get();
        int base = AbyssConfig.BASE_ZOMBIES.get() + Math.min(round, SOFTEN_AFTER) * per;
        if (round > SOFTEN_AFTER) {
            base += (int) Math.round((round - SOFTEN_AFTER) * per * 0.35);
        }
        double lift = game.getMap().difficultyMultiplier();
        return (int) Math.round(base * AbyssConfig.ROUND_SIZE_MULTIPLIER.get() * lift);
    }

    /**
     * Total wave for a round given the lobby size. Party scaling compounds:
     * each additional player multiplies the wave by {@code perPlayerScaling}
     * (default 1.6 = +60% per head), so a full lobby faces a genuinely
     * overwhelming horde. Solo is unaffected (exponent 0).
     */
    private static int waveSize(int round, int players) {
        double partyMult = Math.pow(AbyssConfig.PER_PLAYER_SCALING.get(), players - 1);
        return (int) Math.round(totalZombies(round) * partyMult);
    }

    /**
     * The horde presses in audibly: the more zombies are alive relative to the
     * concurrency ceiling, the more often and louder the growls swell - so a
     * near-max wave sounds genuinely overwhelming.
     */
    private static void hordeAmbience(ServerLevel level, List<ServerPlayer> present) {
        int alive = game.getAliveZombies();
        if (alive <= 0) {
            return;
        }
        int cap = Math.max(1, AbyssConfig.MAX_CONCURRENT_ALIVE.get());
        float density = Math.min(1.0f, (float) alive / cap);
        int interval = Math.max(8, (int) (40 - density * 28));
        if (level.getGameTime() % interval != 0) {
            return;
        }
        float volume = 0.4f + density * 0.9f;
        for (ServerPlayer p : present) {
            level.playSound(null, p.blockPosition(), net.minecraft.sounds.SoundEvents.ZOMBIE_AMBIENT,
                    SoundSource.HOSTILE, volume, 0.5F + RNG.nextFloat() * 0.3F);
            if (density > 0.6f && RNG.nextInt(3) == 0) {
                level.playSound(null, p.blockPosition(), ModSounds.AMBIENT_DREAD.get(),
                        SoundSource.HOSTILE, density * 0.6f, 0.5F);
            }
        }
    }

    private static void title(ServerPlayer player, String title, String subtitle) {
        player.connection.send(new ClientboundSetTitleTextPacket(Component.literal(title)));
        player.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(subtitle)));
    }

    private static void actionBar(ServerPlayer player, String text) {
        player.connection.send(new ClientboundSetActionBarTextPacket(Component.literal(text)));
    }
}
