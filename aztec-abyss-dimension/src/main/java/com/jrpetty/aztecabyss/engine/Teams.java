package com.jrpetty.aztecabyss.engine;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Sides.
 *
 * <p>The engine had exactly one relationship between players: everybody was on the
 * same side, all the time, permanently. That is not a limitation of the arena mode
 * - it is the absence of a concept, and it rules out every game where the answer
 * to "who is against you" is anyone other than the horde. Capture the flag, team
 * deathmatch, hunters and runners, infection, prop hunt, attack and defend: all of
 * them are one idea away and none of them were reachable.
 *
 * <p>A team here is a name, a colour and a set of people. Deliberately not more
 * than that. Scores live in {@link Vars} because they already do, spawns live on
 * {@code [Spawn] team=} markers because that is where the author is standing when
 * they think about it, and everything else a team could want turns out to be a
 * variable and a condition that already exist.
 *
 * <h2>Riding vanilla's scoreboard teams</h2>
 *
 * <p>Membership is mirrored onto a real scoreboard team, which buys three things
 * for free that would otherwise each be a system: name colouring, so you can tell
 * at a glance who is on your side; see-through-walls glow when a map wants it; and
 * friendly fire, which vanilla already knows how to switch off. Reimplementing any
 * of those would be strictly worse than the version already in the game.
 */
public final class Teams {

    /** One side. */
    public record Side(String id, String display, ChatFormatting colour) {
    }

    /** Colours handed out in order, so a two-team map is red against blue. */
    private static final ChatFormatting[] PALETTE = {
            ChatFormatting.RED, ChatFormatting.BLUE, ChatFormatting.GREEN,
            ChatFormatting.YELLOW, ChatFormatting.LIGHT_PURPLE, ChatFormatting.AQUA
    };

    private final Map<String, Side> sides = new LinkedHashMap<>();
    private final Map<UUID, String> membership = new LinkedHashMap<>();
    private final ServerLevel level;

    public Teams(ServerLevel level) {
        this.level = level;
    }

    private static String key(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
    }

    /** Declares a side, or returns the one already declared under that name. */
    public Side define(String id, String display) {
        String k = key(id);
        if (k.isEmpty()) {
            return null;
        }
        Side existing = sides.get(k);
        if (existing != null) {
            return existing;
        }
        ChatFormatting colour = PALETTE[sides.size() % PALETTE.length];
        Side side = new Side(k, display == null || display.isBlank()
                ? Character.toUpperCase(k.charAt(0)) + k.substring(1) : display, colour);
        sides.put(k, side);
        vanillaTeam(side);
        return side;
    }

    /**
     * The vanilla scoreboard team behind one of ours.
     *
     * <p>Friendly fire is off by default, because a team that can shoot itself is
     * a team by name only, and an author who genuinely wants it can turn it back
     * on. Names are coloured so a glance across a room answers "is that one of
     * mine", which in any competitive mode is the single most important question.
     */
    private PlayerTeam vanillaTeam(Side side) {
        Scoreboard board = level.getScoreboard();
        String name = "abyss_" + side.id();
        PlayerTeam team = board.getPlayerTeam(name);
        if (team == null) {
            team = board.addPlayerTeam(name);
            team.setColor(side.colour());
            team.setAllowFriendlyFire(false);
            team.setSeeFriendlyInvisibles(true);
            team.setDisplayName(Component.literal(side.display()));
        }
        return team;
    }

    public java.util.Collection<Side> all() {
        return sides.values();
    }

    public Side side(String id) {
        return sides.get(key(id));
    }

    /** Which side this player is on, or null. */
    public String teamOf(ServerPlayer player) {
        return membership.get(player.getUUID());
    }

    public boolean isOn(ServerPlayer player, String id) {
        String mine = teamOf(player);
        return mine != null && mine.equals(key(id));
    }

    public List<ServerPlayer> membersOf(String id, List<ServerPlayer> from) {
        String k = key(id);
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : from) {
            if (k.equals(membership.get(p.getUUID()))) {
                out.add(p);
            }
        }
        return out;
    }

    public int size(String id) {
        String k = key(id);
        int n = 0;
        for (String v : membership.values()) {
            if (v.equals(k)) {
                n++;
            }
        }
        return n;
    }

    /** Puts a player on a side, declaring it if the map never did. */
    public void join(ServerPlayer player, String id) {
        Side side = side(id);
        if (side == null) {
            side = define(id, null);
        }
        if (side == null) {
            return;
        }
        leave(player);
        membership.put(player.getUUID(), side.id());
        level.getScoreboard().addPlayerToTeam(player.getScoreboardName(), vanillaTeam(side));
        player.displayClientMessage(Component.literal(
                "§7You are on " + side.colour() + side.display()), false);
    }

    public void leave(ServerPlayer player) {
        membership.remove(player.getUUID());
        level.getScoreboard().removePlayerFromTeam(player.getScoreboardName());
    }

    /**
     * Fills the declared sides as evenly as possible.
     *
     * <p>Always smallest-first rather than round-robin, so a player joining a run
     * already under way lands on the side that needs them. Round-robin is only
     * even if everybody arrives at once, which on a server is the one case that
     * never happens.
     */
    public void balance(List<ServerPlayer> players) {
        if (sides.isEmpty()) {
            define("red", null);
            define("blue", null);
        }
        for (ServerPlayer p : players) {
            if (teamOf(p) != null) {
                continue;
            }
            Side smallest = null;
            int best = Integer.MAX_VALUE;
            for (Side s : sides.values()) {
                int n = size(s.id());
                if (n < best) {
                    best = n;
                    smallest = s;
                }
            }
            if (smallest != null) {
                join(p, smallest.id());
            }
        }
    }

    /** Clears every side off the scoreboard when a run ends. */
    public void clear() {
        Scoreboard board = level.getScoreboard();
        for (UUID id : new ArrayList<>(membership.keySet())) {
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
            if (p != null) {
                board.removePlayerFromTeam(p.getScoreboardName());
            }
        }
        membership.clear();
        for (Side s : sides.values()) {
            PlayerTeam team = board.getPlayerTeam("abyss_" + s.id());
            if (team != null) {
                board.removePlayerTeam(team);
            }
        }
        sides.clear();
    }

    /** A short readout of every side and its size, for the bar and for status. */
    public String summary() {
        if (sides.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Side s : sides.values()) {
            if (sb.length() > 0) {
                sb.append(" §8| ");
            }
            sb.append(s.colour()).append(s.display()).append(" §f").append(size(s.id()));
        }
        return sb.toString();
    }
}
