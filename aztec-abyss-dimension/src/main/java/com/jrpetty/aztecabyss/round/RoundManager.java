package com.jrpetty.aztecabyss.round;

import com.jrpetty.aztecabyss.AztecAbyssConstants;
import com.jrpetty.aztecabyss.config.AbyssConfig;
import com.jrpetty.aztecabyss.dimension.AbyssTeleporter;
import com.jrpetty.aztecabyss.network.ModNetworking;
import com.jrpetty.aztecabyss.registry.ModAttachments;
import com.jrpetty.aztecabyss.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
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
    private static final int SPAWN_RADIUS = 36;
    private static final int MAX_CONCURRENT_BASE = 8;

    private static final Map<UUID, ServerBossEvent> BOSS_BARS = new HashMap<>();
    private static AbyssGame game = new AbyssGame();

    private RoundManager() {
    }

    public static AbyssGame game() {
        return game;
    }

    private static void resetSession() {
        for (ServerBossEvent bar : BOSS_BARS.values()) {
            bar.removeAllPlayers();
        }
        BOSS_BARS.clear();
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
            game.setPhase(AbyssGame.Phase.BETWEEN_ROUNDS);
            game.setRound(0);
            game.setPhaseChangedAt(player.level().getGameTime());
        }
        game.addParticipant(player.getUUID());

        if (AbyssConfig.GIVE_STARTING_LOADOUT.get()) {
            giveLoadout(player);
        }
        setupBossBar(player);

        ModNetworking.sendState(player, true, game.getRound());
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
        switch (game.getPhase()) {
            case BETWEEN_ROUNDS -> {
                // Extraction is offered after any cleared round; while someone is
                // channelling on the glyph we hold the next round from starting.
                boolean holding = tickExtraction(level, present);
                long delay = game.getRound() == 0
                        ? AbyssConfig.FIRST_ROUND_DELAY_TICKS.get()
                        : AbyssConfig.BETWEEN_ROUND_TICKS.get();
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
                updateBossBars();
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

        int fullWave = waveSize(round, Math.max(1, game.getParticipants().size()));
        // On a boss round the Warden is the objective; the wave is trimmed to a
        // pressure of adds (the boss summons more as the fight drags on).
        game.setKillsNeededThisRound(bossRound ? Math.max(4, fullWave / 3) : fullWave);
        com.jrpetty.aztecabyss.worldgen.ArenaGenerator.escalateTemple(level, round);

        int max = AbyssConfig.MAX_ROUND.get();
        for (ServerPlayer p : participantPlayers(level)) {
            p.getData(ModAttachments.RUN_STATE).recordRound(round);
            ModNetworking.sendState(p, true, round);
            title(p, "§4§lROUND " + round, round == max
                    ? "§c§lFINAL ROUND - GOOD LUCK"
                    : "§7" + game.getKillsNeededThisRound() + " incoming");
            level.playSound(null, p.blockPosition(), ModSounds.ROUND_START.get(), SoundSource.HOSTILE, 1.0F, 1.0F);
        }
        for (ServerBossEvent bar : BOSS_BARS.values()) {
            bar.setName(Component.literal("Round " + round + " — Aztec Abyss"));
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

    /** Boss rounds: the mid-run gauntlet (10) and the final round. */
    private static boolean isBossRound(int round) {
        return round == 10 || round == AbyssConfig.MAX_ROUND.get();
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
        ServerPlayer target = pickTarget(present);
        double angle = RNG.nextDouble() * Math.PI * 2.0;
        BlockPos anchor = target != null ? target.blockPosition() : AztecAbyssConstants.TEMPLE_CENTER;
        int x = anchor.getX() + (int) (Math.cos(angle) * SPAWN_RADIUS);
        int z = anchor.getZ() + (int) (Math.sin(angle) * SPAWN_RADIUS);
        BlockPos pos = new BlockPos(x, AztecAbyssConstants.ARENA_FLOOR_Y + 1, z);

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
        if (target != null) {
            mob.setTarget(target);
        }
        level.addFreshEntity(mob);
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

    private static void applyRoundScaling(Mob mob, int round, boolean brute) {
        double healthMult = 1.0 + round * AbyssConfig.HEALTH_SCALE_PER_ROUND.get();
        double dmgMult = 1.0 + round * AbyssConfig.DAMAGE_SCALE_PER_ROUND.get();
        double speedMult = Math.min(1.0 + round * 0.02, 1.6);

        if (brute) {
            healthMult *= 2.5;
            dmgMult *= 1.4;
            speedMult *= 0.85;
        }

        scaleAttribute(mob, Attributes.MAX_HEALTH, healthMult);
        scaleAttribute(mob, Attributes.ATTACK_DAMAGE, dmgMult);
        scaleAttribute(mob, Attributes.MOVEMENT_SPEED, speedMult);
        AttributeInstance armor = mob.getAttribute(Attributes.ARMOR);
        if (armor != null) {
            armor.setBaseValue(Math.min(20.0, round * 0.6));
        }
        if (brute) {
            AttributeInstance scale = mob.getAttribute(Attributes.SCALE);
            if (scale != null) {
                scale.setBaseValue(1.8);
            }
            mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.GOLDEN_HELMET));
            mob.setDropChance(EquipmentSlot.HEAD, 0.0F);
            mob.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, Integer.MAX_VALUE, 0, false, false));
        }
        mob.setHealth(mob.getMaxHealth());
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

    // ------------------------------------------------------------------
    // Boss rounds - the Warden rises (round 10 and the final round)
    // ------------------------------------------------------------------

    /** Fixed, dramatic spawn point: between the arrival walkway and the temple. */
    private static final BlockPos BOSS_SPAWN = new BlockPos(0, AztecAbyssConstants.ARENA_FLOOR_Y + 1, 16);

    private static void spawnBoss(ServerLevel level, int round, List<ServerPlayer> present) {
        boolean finale = round >= AbyssConfig.MAX_ROUND.get();
        Mob boss = EntityType.WARDEN.create(level);
        if (boss == null) {
            return;
        }
        boss.moveTo(BOSS_SPAWN.getX() + 0.5, BOSS_SPAWN.getY(), BOSS_SPAWN.getZ() + 0.5, 180.0F, 0.0F);
        boss.finalizeSpawn(level, level.getCurrentDifficultyAt(BOSS_SPAWN), MobSpawnType.EVENT, null);

        // Explicit, hand-tuned stat line rather than the generic wave scaling: a
        // huge but not interminable health pool, brutal hits, unshakable footing.
        double hp = 400.0 + round * 30.0;                // r10 ~700, r20 ~1000
        setAttribute(boss, Attributes.MAX_HEALTH, hp);
        scaleAttribute(boss, Attributes.ATTACK_DAMAGE, 1.0 + round * AbyssConfig.DAMAGE_SCALE_PER_ROUND.get());
        setAttribute(boss, Attributes.KNOCKBACK_RESISTANCE, 1.0);
        AttributeInstance scale = boss.getAttribute(Attributes.SCALE);
        if (scale != null) {
            scale.setBaseValue(finale ? 1.35 : 1.15);
        }
        boss.setHealth(boss.getMaxHealth());

        boss.setPersistenceRequired();
        boss.getPersistentData().putBoolean("aztecabyss_wave_mob", true); // for arena cleanup
        boss.getPersistentData().putBoolean("aztecabyss_boss", true);
        // Always trackable - the Warden is huge and the arena is dark.
        boss.addEffect(new MobEffectInstance(MobEffects.GLOWING, Integer.MAX_VALUE, 0, false, false));
        level.addFreshEntity(boss);

        game.setBossId(boss.getUUID());
        game.setBossActive(true);
        game.setBossHealthFraction(1.0f);
        game.setLastBossAbilityAt(level.getGameTime());

        String name = finale ? "THE DEVOURER" : "WARDEN OF THE ABYSS";
        for (ServerPlayer p : present) {
            title(p, "§4§l⚔ " + name, "§cThe Warden claws its way out of the dark...");
            level.playSound(null, p.blockPosition(), SoundEvents.WARDEN_EMERGE, SoundSource.HOSTILE, 1.0F, 1.0F);
            level.playSound(null, p.blockPosition(), ModSounds.AMBIENT_DREAD.get(), SoundSource.HOSTILE, 1.0F, 0.5F);
            p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 120, 0, false, false));
        }
        for (ServerBossEvent bar : BOSS_BARS.values()) {
            bar.setName(Component.literal("§4⚔ " + name));
            bar.setColor(BossEvent.BossBarColor.RED);
            bar.setProgress(1.0F);
            bar.setDarkenScreen(true);
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

        // Keep the Warden locked onto a standing hunter so it never idles.
        if (boss instanceof Mob m && level.getGameTime() % 20L == 0L) {
            ServerPlayer target = pickTarget(present);
            if (target != null && (m.getTarget() == null || !m.getTarget().isAlive())) {
                m.setTarget(target);
            }
        }

        long now = level.getGameTime();
        if (now - game.getLastBossAbilityAt() >= 120L) { // roughly every 6s
            game.setLastBossAbilityAt(now);
            for (ServerPlayer p : present) {
                p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 80, 0, false, false));
                level.playSound(null, p.blockPosition(), SoundEvents.WARDEN_ROAR, SoundSource.HOSTILE, 0.9F, 1.0F);
            }
            int cap = AbyssConfig.MAX_CONCURRENT_ALIVE.get();
            int summon = Math.min(3 + present.size(), Math.max(0, cap - game.getAliveZombies()));
            for (int i = 0; i < summon; i++) {
                spawnWaveMob(level, present, game.getRound(), false);
                game.setAliveZombies(game.getAliveZombies() + 1);
            }
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
            title(p, "§6§l⚔ THE WARDEN FALLS",
                    finale ? "§eThe Abyss is conquered." : "§eThe dark recoils. Press on.");
            level.playSound(null, p.blockPosition(), SoundEvents.WARDEN_DEATH, SoundSource.HOSTILE, 1.0F, 1.0F);
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
        if (killer != null) {
            RunState rs = killer.getData(ModAttachments.RUN_STATE);
            rs.addKill();
            killer.setData(ModAttachments.RUN_STATE, rs);
        }
    }

    private static void onRoundCleared(ServerLevel level) {
        if (game.getRound() >= AbyssConfig.MAX_ROUND.get()) {
            endGame(level, true);
            return;
        }
        game.setPhase(AbyssGame.Phase.BETWEEN_ROUNDS);
        game.setPhaseChangedAt(level.getGameTime());
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
        BlockPos c = AztecAbyssConstants.EXTRACTION_POS;
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
        BlockPos glyph = AztecAbyssConstants.EXTRACTION_POS;
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
                round, survivalSeconds, rs.getTotalKills(), rs.getTotalRevives(), false, multiplayer);
        com.jrpetty.aztecabyss.worldgen.MonumentBuilder.build(abyssLevel);
        ModNetworking.sendRecap(player, round, killsThisRun, revivesThisRun, survivalSeconds, prevBest, false, multiplayer, true);

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
    private static void sendPlayerHome(ServerLevel abyssLevel, ServerPlayer player, int round, boolean victory, boolean batched) {
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
        }
        rs.recordRound(round);

        long now = System.currentTimeMillis();
        int survivalSeconds = rs.survivalSeconds(now);
        int killsThisRun = rs.getKillsThisRun();
        int revivesThisRun = rs.getRevivesThisRun();
        boolean multiplayer = game.isMultiplayerRun();
        int prevBest = rs.getBestRound();

        com.jrpetty.aztecabyss.data.AbyssStats.get(server).record(
                player.getUUID(), player.getGameProfile().getName(),
                round, survivalSeconds, rs.getTotalKills(), rs.getTotalRevives(), victory, multiplayer);
        com.jrpetty.aztecabyss.worldgen.MonumentBuilder.build(abyssLevel);

        // Death/victory recap screen data.
        ModNetworking.sendRecap(player, round, killsThisRun, revivesThisRun, survivalSeconds, prevBest, victory, multiplayer, false);

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
            title(p, "§5§lTHE OFFERING IS ACCEPTED", "§dThe vault is open. Your reward will be legendary.");
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
        level.getEntitiesOfClass(Mob.class,
                        new net.minecraft.world.phys.AABB(
                                -AztecAbyssConstants.ARENA_RADIUS, AztecAbyssConstants.ARENA_FLOOR_Y - 4, -AztecAbyssConstants.ARENA_RADIUS,
                                AztecAbyssConstants.ARENA_RADIUS, AztecAbyssConstants.ARENA_FLOOR_Y + AztecAbyssConstants.WALL_HEIGHT, AztecAbyssConstants.ARENA_RADIUS),
                        m -> m.getPersistentData().getBoolean("aztecabyss_wave_mob"))
                .forEach(m -> m.remove(Entity.RemovalReason.DISCARDED));
        game.setAliveZombies(0);
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

    private static void updateBossBars() {
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
    }

    private static int totalZombies(int round) {
        int base = AbyssConfig.BASE_ZOMBIES.get() + round * AbyssConfig.ZOMBIES_PER_ROUND.get();
        return (int) Math.round(base * AbyssConfig.ROUND_SIZE_MULTIPLIER.get());
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
