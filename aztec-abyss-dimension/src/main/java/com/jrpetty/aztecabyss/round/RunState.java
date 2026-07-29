package com.jrpetty.aztecabyss.round;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * Per-player state for the Abyss. The shared round/zombie state lives in
 * {@link AbyssGame}; this holds the things that are genuinely per-player:
 * where "home" is (for the return trip + reward chest), the re-entry cooldown,
 * the downed/revive state used by co-op, and persistent personal stats.
 *
 * Attached via a NeoForge data attachment so it survives relog and restart.
 */
public final class RunState {

    private boolean inRun;
    private boolean downed;
    private int bleedoutTicksLeft;
    private int reviveProgress;
    private long cooldownUntil;
    private int bestRound;
    private int totalKills;
    /** If > 0, the player left mid-run and is owed a reward for this round on next login. */
    private int owedRewardRound;
    // Per-run counters (reset on entry, used for the death recap).
    private int killsThisRun;
    private int revivesThisRun;
    private long runStartMillis;
    private int headshotsThisRun;
    // Lifetime.
    private int totalRevives;
    private int totalDeaths;

    @Nullable
    private BlockPos homePortalPos;
    @Nullable
    private ResourceLocation homeDimension;

    public RunState() {
        this(false, false, 0, 0, 0L, 0, 0, 0, 0, 0, 0L, 0, 0, 0, null, null);
    }

    private RunState(boolean inRun, boolean downed, int bleedoutTicksLeft, int reviveProgress,
                     long cooldownUntil, int bestRound, int totalKills, int owedRewardRound,
                     int killsThisRun, int revivesThisRun, long runStartMillis, int totalRevives,
                     int headshotsThisRun, int totalDeaths,
                     @Nullable BlockPos homePortalPos, @Nullable ResourceLocation homeDimension) {
        this.headshotsThisRun = headshotsThisRun;
        this.totalDeaths = totalDeaths;
        this.inRun = inRun;
        this.downed = downed;
        this.bleedoutTicksLeft = bleedoutTicksLeft;
        this.reviveProgress = reviveProgress;
        this.cooldownUntil = cooldownUntil;
        this.bestRound = bestRound;
        this.totalKills = totalKills;
        this.owedRewardRound = owedRewardRound;
        this.killsThisRun = killsThisRun;
        this.revivesThisRun = revivesThisRun;
        this.runStartMillis = runStartMillis;
        this.totalRevives = totalRevives;
        this.homePortalPos = homePortalPos;
        this.homeDimension = homeDimension;
    }

    public static final Codec<RunState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.fieldOf("in_run").forGetter(s -> s.inRun),
            Codec.BOOL.fieldOf("downed").forGetter(s -> s.downed),
            Codec.INT.fieldOf("bleedout_ticks_left").forGetter(s -> s.bleedoutTicksLeft),
            Codec.INT.fieldOf("revive_progress").forGetter(s -> s.reviveProgress),
            Codec.LONG.fieldOf("cooldown_until").forGetter(s -> s.cooldownUntil),
            Codec.INT.fieldOf("best_round").forGetter(s -> s.bestRound),
            Codec.INT.fieldOf("total_kills").forGetter(s -> s.totalKills),
            Codec.INT.optionalFieldOf("owed_reward_round", 0).forGetter(s -> s.owedRewardRound),
            Codec.INT.optionalFieldOf("kills_this_run", 0).forGetter(s -> s.killsThisRun),
            Codec.INT.optionalFieldOf("revives_this_run", 0).forGetter(s -> s.revivesThisRun),
            Codec.LONG.optionalFieldOf("run_start_millis", 0L).forGetter(s -> s.runStartMillis),
            Codec.INT.optionalFieldOf("total_revives", 0).forGetter(s -> s.totalRevives),
            Codec.INT.optionalFieldOf("headshots_this_run", 0).forGetter(s -> s.headshotsThisRun),
            Codec.INT.optionalFieldOf("total_deaths", 0).forGetter(s -> s.totalDeaths),
            BlockPos.CODEC.optionalFieldOf("home_portal_pos").forGetter(s -> Optional.ofNullable(s.homePortalPos)),
            ResourceLocation.CODEC.optionalFieldOf("home_dimension").forGetter(s -> Optional.ofNullable(s.homeDimension))
    ).apply(instance, RunState::fromCodec));

    private static RunState fromCodec(boolean inRun, boolean downed, int bleed, int revive, long cooldown,
                                      int best, int kills, int owed, int killsRun, int revivesRun,
                                      long startMillis, int totalRevives, int headshotsRun, int totalDeaths,
                                      Optional<BlockPos> homePos, Optional<ResourceLocation> homeDim) {
        return new RunState(inRun, downed, bleed, revive, cooldown, best, kills, owed, killsRun, revivesRun,
                startMillis, totalRevives, headshotsRun, totalDeaths, homePos.orElse(null), homeDim.orElse(null));
    }

    // ------------------------------------------------------------------

    public boolean isInRun() {
        return inRun;
    }

    public void setInRun(boolean inRun) {
        this.inRun = inRun;
    }

    public boolean isDowned() {
        return downed;
    }

    public void setDowned(boolean downed) {
        this.downed = downed;
    }

    public int getBleedoutTicksLeft() {
        return bleedoutTicksLeft;
    }

    public void setBleedoutTicksLeft(int v) {
        this.bleedoutTicksLeft = v;
    }

    public int getReviveProgress() {
        return reviveProgress;
    }

    public void setReviveProgress(int v) {
        this.reviveProgress = v;
    }

    public long getCooldownUntil() {
        return cooldownUntil;
    }

    public void setCooldownUntil(long cooldownUntil) {
        this.cooldownUntil = cooldownUntil;
    }

    public boolean isOnCooldown(long now) {
        return now < cooldownUntil;
    }

    public int getBestRound() {
        return bestRound;
    }

    public void recordRound(int round) {
        if (round > bestRound) {
            bestRound = round;
        }
    }

    public int getTotalKills() {
        return totalKills;
    }

    public void addKill() {
        this.totalKills++;
        this.killsThisRun++;
    }

    public int getKillsThisRun() {
        return killsThisRun;
    }

    public int getRevivesThisRun() {
        return revivesThisRun;
    }

    public int getTotalRevives() {
        return totalRevives;
    }

    public void addRevive() {
        this.totalRevives++;
        this.revivesThisRun++;
    }

    public long getRunStartMillis() {
        return runStartMillis;
    }

    public int getHeadshotsThisRun() {
        return headshotsThisRun;
    }

    public void addHeadshot() {
        this.headshotsThisRun++;
    }

    public int getTotalDeaths() {
        return totalDeaths;
    }

    public void addDeath() {
        this.totalDeaths++;
    }

    /** Marks the start of a fresh run and zeroes the per-run counters. */
    public void beginRunTracking(long nowMillis) {
        this.runStartMillis = nowMillis;
        this.killsThisRun = 0;
        this.revivesThisRun = 0;
        this.headshotsThisRun = 0;
    }

    /** Survival time of the current/just-finished run, in whole seconds. */
    public int survivalSeconds(long nowMillis) {
        if (runStartMillis <= 0L) {
            return 0;
        }
        return (int) Math.max(0, (nowMillis - runStartMillis) / 1000L);
    }

    public int getOwedRewardRound() {
        return owedRewardRound;
    }

    public void setOwedRewardRound(int round) {
        this.owedRewardRound = round;
    }

    @Nullable
    public BlockPos getHomePortalPos() {
        return homePortalPos;
    }

    @Nullable
    public ResourceLocation getHomeDimension() {
        return homeDimension;
    }

    public void setHome(BlockPos pos, ResourceKey<Level> dimension) {
        this.homePortalPos = pos;
        this.homeDimension = dimension.location();
    }

    /** Clears transient run state (keeps cooldown, home, and persistent stats). */
    public void clearRun() {
        this.inRun = false;
        this.downed = false;
        this.bleedoutTicksLeft = 0;
        this.reviveProgress = 0;
    }
}
