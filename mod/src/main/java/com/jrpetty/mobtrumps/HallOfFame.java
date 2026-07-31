package com.jrpetty.mobtrumps;

import com.jrpetty.mobtrumps.game.Category;
import com.jrpetty.mobtrumps.game.MobCard;
import com.jrpetty.mobtrumps.game.MobCards;
import com.jrpetty.mobtrumps.game.MobCategories;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The server's own record of who did what first.
 *
 * <p>Serials made this worth having. Every mob has exactly one
 * {@code 000001} — the historical first of that card on this server, signed by
 * whoever's kill created it — and that is a fact about the world, not about a
 * player's inventory. It stays true after the card is traded, and after it is
 * shredded.
 *
 * <p>Three records, all append-only and all permanent:
 * <ul>
 *   <li><b>First prints</b> — who unlocked each mob's 000001, and when.</li>
 *   <li><b>Category firsts</b> — who completed each set first.</li>
 *   <li><b>The full set</b> — who has finished all {@value #ALL} of them.</li>
 * </ul>
 *
 * <p>Nothing here can be lost by later play: a first is a first. That is the
 * point of it, and it is why none of these entries are ever overwritten.
 */
public class HallOfFame extends SavedData {

    public static final String FILE = "mobtrumps_hall";

    /** One line of history. */
    public record Entry(String key, String player, long when) {
    }

    private final Map<String, Entry> firstPrints = new LinkedHashMap<>();
    private final Map<String, Entry> categoryFirsts = new LinkedHashMap<>();
    private final List<Entry> completions = new ArrayList<>();

    public static SavedData.Factory<HallOfFame> factory() {
        return new SavedData.Factory<>(HallOfFame::new, HallOfFame::load, null);
    }

    public static HallOfFame get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(factory(), FILE);
    }

    // --- recording ----------------------------------------------------------

    /**
     * Record the first ever print of a mob. Called when the serial registry
     * hands out number 1, so it cannot be claimed twice and does not depend on
     * anyone still holding the card.
     */
    public void recordFirstPrint(String mobId, ServerPlayer finder) {
        if (mobId == null || mobId.isEmpty() || firstPrints.containsKey(mobId)) {
            return;
        }
        firstPrints.put(mobId, new Entry(mobId, finder.getGameProfile().getName(),
                System.currentTimeMillis()));
        setDirty();
    }

    /** Record the first player to complete a category, and to complete them all. */
    public void checkCompletion(ServerPlayer player) {
        List<String> collected = player.getData(ModAttachments.COLLECTED.get());
        if (collected.isEmpty()) {
            return;
        }
        String name = player.getGameProfile().getName();
        boolean changed = false;
        for (Category category : Category.values()) {
            String key = category.name().toLowerCase(Locale.ROOT);
            if (categoryFirsts.containsKey(key)) {
                continue;
            }
            boolean whole = true;
            for (String id : MobCategories.members(category)) {
                if (MobCards.byId(id) != null && !collected.contains(id)) {
                    whole = false;
                    break;
                }
            }
            if (whole) {
                categoryFirsts.put(key, new Entry(key, name, System.currentTimeMillis()));
                changed = true;
                announce(player, "completed " + category.label());
            }
        }
        if (collected.size() >= MobCards.ALL.size()
                && completions.stream().noneMatch(e -> e.player().equals(name))) {
            completions.add(new Entry("all", name, System.currentTimeMillis()));
            changed = true;
            announce(player, "COMPLETED THE SET — all " + MobCards.ALL.size() + " mobs");
        }
        if (changed) {
            setDirty();
        }
    }

    private void announce(ServerPlayer player, String what) {
        var server = player.getServer();
        if (server == null) {
            return;
        }
        var line = net.minecraft.network.chat.Component
                .literal("★ " + player.getGameProfile().getName() + " " + what
                        + " — first on this server.")
                .withStyle(net.minecraft.ChatFormatting.GOLD);
        for (ServerPlayer other : server.getPlayerList().getPlayers()) {
            other.sendSystemMessage(line);
        }
    }

    // --- reading ------------------------------------------------------------

    public static final int ALL = 81;

    public Entry firstPrint(String mobId) {
        return firstPrints.get(mobId);
    }

    public Entry categoryFirst(Category category) {
        return categoryFirsts.get(category.name().toLowerCase(Locale.ROOT));
    }

    public List<Entry> completions() {
        return List.copyOf(completions);
    }

    /** How many mobs each named finder holds the 000001 of, best first. */
    public List<Map.Entry<String, Integer>> finders() {
        Map<String, Integer> tally = new LinkedHashMap<>();
        for (Entry e : firstPrints.values()) {
            tally.merge(e.player(), 1, Integer::sum);
        }
        List<Map.Entry<String, Integer>> out = new ArrayList<>(tally.entrySet());
        out.sort((a, b) -> b.getValue() - a.getValue());
        return out;
    }

    public int firstPrintCount() {
        return firstPrints.size();
    }

    /** Every mob that still has no 000001 anywhere on the server. */
    public List<MobCard> undiscovered() {
        List<MobCard> out = new ArrayList<>();
        for (MobCard card : MobCards.ALL) {
            if (!firstPrints.containsKey(card.id())) {
                out.add(card);
            }
        }
        return out;
    }

    /** Flatten for the wire. Sent on request only; these records change rarely. */
    public HallSyncPayload snapshot() {
        List<String> firsts = new ArrayList<>();
        for (Entry e : firstPrints.values()) {
            firsts.add(e.key() + "|" + e.player());
        }
        List<String> cats = new ArrayList<>();
        for (Entry e : categoryFirsts.values()) {
            cats.add(e.key() + "|" + e.player());
        }
        List<String> done = new ArrayList<>();
        for (Entry e : completions) {
            done.add(e.player());
        }
        return new HallSyncPayload(firsts, cats, done);
    }

    // --- persistence --------------------------------------------------------

    private static void writeMap(CompoundTag tag, String key, Map<String, Entry> map) {
        ListTag list = new ListTag();
        for (Entry e : map.values()) {
            CompoundTag t = new CompoundTag();
            t.putString("k", e.key());
            t.putString("p", e.player());
            t.putLong("w", e.when());
            list.add(t);
        }
        tag.put(key, list);
    }

    private static void readMap(CompoundTag tag, String key, Map<String, Entry> into) {
        ListTag list = tag.getList(key, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            into.put(t.getString("k"),
                    new Entry(t.getString("k"), t.getString("p"), t.getLong("w")));
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        writeMap(tag, "firsts", firstPrints);
        writeMap(tag, "categories", categoryFirsts);
        ListTag done = new ListTag();
        for (Entry e : completions) {
            CompoundTag t = new CompoundTag();
            t.putString("p", e.player());
            t.putLong("w", e.when());
            done.add(t);
        }
        tag.put("completions", done);
        return tag;
    }

    public static HallOfFame load(CompoundTag tag, HolderLookup.Provider registries) {
        HallOfFame hall = new HallOfFame();
        readMap(tag, "firsts", hall.firstPrints);
        readMap(tag, "categories", hall.categoryFirsts);
        ListTag done = tag.getList("completions", Tag.TAG_COMPOUND);
        for (int i = 0; i < done.size(); i++) {
            CompoundTag t = done.getCompound(i);
            hall.completions.add(new Entry("all", t.getString("p"), t.getLong("w")));
        }
        return hall;
    }
}
