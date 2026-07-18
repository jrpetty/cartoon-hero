package com.jrpetty.aztecabyss.round;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The single, server-wide shared state for an active Abyss run. Because there
 * is one physical arena, all players inside it share one round counter, one
 * zombie pool, and one ritual - this is what turns the mode from parallel solo
 * runs into true co-op.
 *
 * Lifecycle is owned by {@link RoundManager}: created when the first player
 * enters an idle Abyss, torn down when the last participant leaves or the run
 * ends.
 */
public final class AbyssGame {

    public enum Phase { IDLE, BETWEEN_ROUNDS, IN_ROUND, ENDED }

    private Phase phase = Phase.IDLE;
    private int round = 0;
    private int killsThisRound = 0;
    private int killsNeededThisRound = 0;
    private int spawnedThisRound = 0;
    private int aliveZombies = 0;
    private long phaseChangedAt = 0L;
    private long lastAmbientAt = 0L;

    /** Players currently taking part in this run (still alive / downed, not yet sent home). */
    private final Set<UUID> participants = new LinkedHashSet<>();

    /** True once this run has ever had more than one participant - classifies the run as co-op. */
    private boolean everMultiplayer = false;

    // --- Easter-egg ritual state ---
    /** Braziers lit so far, in the order the player lit them. */
    private final List<Integer> ritualSequence = new ArrayList<>();
    private boolean altarFed = false;
    private boolean ritualComplete = false;
    private BlockPos vaultDoorPos = null;

    public Phase getPhase() {
        return phase;
    }

    public void setPhase(Phase phase) {
        this.phase = phase;
    }

    public int getRound() {
        return round;
    }

    public void setRound(int round) {
        this.round = round;
    }

    public int getKillsThisRound() {
        return killsThisRound;
    }

    public void setKillsThisRound(int v) {
        this.killsThisRound = v;
    }

    public int getKillsNeededThisRound() {
        return killsNeededThisRound;
    }

    public void setKillsNeededThisRound(int v) {
        this.killsNeededThisRound = v;
    }

    public int getSpawnedThisRound() {
        return spawnedThisRound;
    }

    public void setSpawnedThisRound(int v) {
        this.spawnedThisRound = v;
    }

    public int getAliveZombies() {
        return aliveZombies;
    }

    public void setAliveZombies(int v) {
        this.aliveZombies = Math.max(0, v);
    }

    public long getPhaseChangedAt() {
        return phaseChangedAt;
    }

    public void setPhaseChangedAt(long v) {
        this.phaseChangedAt = v;
    }

    public long getLastAmbientAt() {
        return lastAmbientAt;
    }

    public void setLastAmbientAt(long v) {
        this.lastAmbientAt = v;
    }

    public Set<UUID> getParticipants() {
        return participants;
    }

    public void addParticipant(UUID id) {
        participants.add(id);
        if (participants.size() > 1) {
            everMultiplayer = true;
        }
    }

    public boolean isMultiplayerRun() {
        return everMultiplayer;
    }

    public void removeParticipant(UUID id) {
        participants.remove(id);
    }

    public boolean isParticipant(UUID id) {
        return participants.contains(id);
    }

    // --- Ritual ---

    public List<Integer> getRitualSequence() {
        return ritualSequence;
    }

    public boolean isBrazierLit(int index) {
        return ritualSequence.contains(index);
    }

    public void lightBrazier(int index) {
        if (!ritualSequence.contains(index)) {
            ritualSequence.add(index);
        }
    }

    public void resetRitualSequence() {
        ritualSequence.clear();
    }

    public boolean isAltarFed() {
        return altarFed;
    }

    public void setAltarFed(boolean v) {
        this.altarFed = v;
    }

    public boolean isRitualComplete() {
        return ritualComplete;
    }

    public void setRitualComplete(boolean v) {
        this.ritualComplete = v;
    }

    public BlockPos getVaultDoorPos() {
        return vaultDoorPos;
    }

    public void setVaultDoorPos(BlockPos pos) {
        this.vaultDoorPos = pos;
    }
}
