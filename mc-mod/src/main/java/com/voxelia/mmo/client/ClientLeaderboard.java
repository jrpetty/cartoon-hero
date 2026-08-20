package com.voxelia.mmo.client;

import com.voxelia.mmo.network.LeaderboardPayload;
import com.voxelia.mmo.network.LeaderboardRequestPacket;
import com.voxelia.mmo.skill.Skill;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/** Client-side cache of the standings the server last sent. */
public final class ClientLeaderboard {
    private ClientLeaderboard() {}

    /** One row as the screen draws it. */
    public record Row(int rank, String name, int level, int prestige, boolean self) {}

    private static List<Row> rows = List.of();
    private static List<Integer> meta = List.of();
    private static int requested = -2; // -1 is a valid selection (Character), so start outside it
    private static boolean waiting;

    public static void update(LeaderboardPayload payload) {
        List<Row> out = new ArrayList<>();
        List<Integer> data = payload.rows();
        List<String> names = payload.names();
        for (int i = 0; i < names.size(); i++) {
            int base = i * LeaderboardPayload.STRIDE;
            if (base + LeaderboardPayload.STRIDE > data.size()) break;
            out.add(new Row(data.get(base), names.get(i), data.get(base + 1),
                data.get(base + 2), data.get(base + 3) == 1));
        }
        rows = out;
        meta = payload.meta();
        waiting = false;
    }

    /** Asks the server for a skill's standings ({@code null} = the character average). */
    public static void request(Skill skill) {
        int ordinal = skill == null ? -1 : skill.ordinal();
        requested = ordinal;
        waiting = true;
        rows = List.of();
        PacketDistributor.sendToServer(new LeaderboardRequestPacket(ordinal));
    }

    /** Re-asks for whatever is on screen (used when the screen opens). */
    public static void refresh() {
        Skill[] all = Skill.values();
        request(requested >= 0 && requested < all.length ? all[requested] : null);
    }

    public static List<Row> rows() { return rows; }

    public static boolean waiting() { return waiting; }

    public static int yourRank() { return metaAt(LeaderboardPayload.M_YOUR_RANK); }

    public static int yourLevel() { return metaAt(LeaderboardPayload.M_YOUR_LEVEL); }

    public static int tracked() { return metaAt(LeaderboardPayload.M_TRACKED); }

    /** The skill this data is for, or null when it's the character ranking. */
    public static Skill skill() {
        int ordinal = meta.isEmpty() ? requested : metaAt(LeaderboardPayload.M_SKILL);
        Skill[] all = Skill.values();
        return ordinal >= 0 && ordinal < all.length ? all[ordinal] : null;
    }

    private static int metaAt(int index) {
        return index >= 0 && index < meta.size() ? meta.get(index) : 0;
    }
}
