package com.jrpetty.aztecabyss.engine;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reads a region of the world back as a playable map.
 *
 * <p>The engine's whole promise is that geometry is authored in Minecraft and not
 * in Java, which means at some point something has to look at the blocks and work
 * out what the author meant. This is that step.
 *
 * <p>It walks chunks rather than blocks. A 96-cube region is nearly nine hundred
 * thousand positions and almost none of them are signs, but the chunks in it hold a
 * ready-made map of exactly the block entities present - so the scan costs about as
 * much as the number of signs, not the volume of the map.
 */
public final class MapScan {

    private MapScan() {
    }

    /**
     * Everything the engine found, grouped by marker kind.
     *
     * @param brokenDealers dealer signs that would not parse into an offer. Caught
     *                      here rather than in validation because this is the only
     *                      point where the sign itself is in hand - and a price
     *                      typo is otherwise invisible until someone walks up to a
     *                      shop mid-run and it does nothing at all.
     */
    public record Result(Map<String, List<Marker>> byKind, List<Marker> all,
                         List<BlockPos> brokenDealers) {

        public List<Marker> of(String kind) {
            return byKind.getOrDefault(kind, List.of());
        }

        public Marker first(String kind) {
            List<Marker> list = of(kind);
            return list.isEmpty() ? null : list.get(0);
        }

        public int count(String kind) {
            return of(kind).size();
        }
    }

    public static Result scan(ServerLevel level, BoundingBox box) {
        Map<String, List<Marker>> byKind = new LinkedHashMap<>();
        List<Marker> all = new ArrayList<>();
        List<BlockPos> brokenDealers = new ArrayList<>();

        int minChunkX = box.minX() >> 4;
        int maxChunkX = box.maxX() >> 4;
        int minChunkZ = box.minZ() >> 4;
        int maxChunkZ = box.maxZ() >> 4;

        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                LevelChunk chunk = level.getChunk(cx, cz);
                for (Map.Entry<BlockPos, net.minecraft.world.level.block.entity.BlockEntity> e
                        : chunk.getBlockEntities().entrySet()) {
                    BlockPos pos = e.getKey();
                    if (!box.isInside(pos)) {
                        continue;
                    }
                    // Both authoring surfaces feed one parser. A map may mix them
                    // freely - an old build full of signs keeps working, and a new
                    // one can use blocks throughout.
                    Marker m;
                    SignBlockEntity sign = null;
                    if (e.getValue() instanceof SignBlockEntity s) {
                        sign = s;
                        m = Marker.parse(s, level.getBlockState(pos));
                    } else if (e.getValue() instanceof com.jrpetty.aztecabyss.block.MarkerBlockEntity mb) {
                        m = Marker.parse(mb, level.getBlockState(pos));
                    } else {
                        continue;
                    }
                    if (m == null) {
                        continue;
                    }
                    all.add(m);
                    byKind.computeIfAbsent(m.kind(), k -> new ArrayList<>()).add(m);
                    if (m.kind().equals("dealer") && sign != null && DealerSign.parse(sign) == null) {
                        brokenDealers.add(pos.immutable());
                    }
                }
            }
        }
        return new Result(byKind, all, brokenDealers);
    }

    /**
     * Checks a scanned map for the things that make one unplayable.
     *
     * <p>This matters more than it sounds. The likeliest failure for someone
     * building a map is not a crash - it is a map that loads perfectly and then
     * turns out to have no way for the horde to get in, or no spawn point, and the
     * only symptom is a run that feels broken for reasons nobody can name. Better
     * to say so in English before a run is wasted.
     *
     * @return human-readable problems, worst first; empty means it will play
     */
    public static List<String> validate(Result scan) {
        List<String> problems = new ArrayList<>();

        if (scan.count("spawn") == 0) {
            problems.add("§cNo §f[Spawn]§c marker — players have nowhere to arrive.");
        } else if (scan.count("spawn") > 1) {
            problems.add("§e" + scan.count("spawn") + " §f[Spawn]§e markers — only the first is used.");
        }

        if (scan.count("horde") == 0) {
            problems.add("§eNo §f[Horde]§e markers — nothing will attack. "
                    + "Fine for a §ffree§e-mode map (a race, a heist, an escape room); "
                    + "wrong for anything that expects a horde.");
        }

        if (scan.count("extract") == 0) {
            problems.add("§eNo §f[Extract]§e marker — this map has no way to win, "
                    + "only a way to lose. Players can leave it only by dying.");
        }

        // A sealed area with no door is a room nobody can ever reach.
        java.util.Set<String> doorAreas = new java.util.HashSet<>();
        for (Marker d : scan.of("door")) {
            doorAreas.add(d.arg("area", d.arg("value", "")).toLowerCase(Locale.ROOT));
        }
        java.util.Set<String> hordeAreas = new java.util.HashSet<>();
        for (Marker h : scan.of("horde")) {
            String area = h.arg("area", "").toLowerCase(Locale.ROOT);
            if (!area.isEmpty()) {
                hordeAreas.add(area);
            }
        }
        for (String area : hordeAreas) {
            if (!area.equals("start") && !doorAreas.contains(area)) {
                problems.add("§eArea §f" + area + "§e has horde markers but no §f[Door]§e — "
                        + "it can never be opened.");
            }
        }

        // An entity id that does not exist spawns nothing at all, silently. A map
        // with a typo in one reads as a spawner that simply never fires, which is
        // indistinguishable from a spawner the author configured for a later round.
        for (String kind : new String[]{"spawner", "boss"}) {
            for (Marker m : scan.of(kind)) {
                String id = m.arg("id", m.arg("value", ""));
                if (id.isEmpty()) {
                    problems.add("§e[" + kind + "] at §f" + m.pos().getX() + ", " + m.pos().getZ()
                            + "§e names no entity — put one on line 2.");
                } else if (net.minecraft.world.entity.EntityType.byString(id).isEmpty()) {
                    problems.add("§c[" + kind + "] at §f" + m.pos().getX() + ", " + m.pos().getZ()
                            + "§c: no entity called §f" + id
                            + "§c. Run §f/arena mobs " + id.replace("minecraft:", "") + "§c.");
                }
            }
        }

        // An objective is the one marker whose failure is completely mute. A
        // misspelled type does nothing at all; a misspelled item on a collect
        // objective answers every hand-in with "nothing to hand in", which reads
        // as the player carrying the wrong thing rather than the map naming a
        // thing that does not exist.
        if (scan.count("objective") > 1) {
            problems.add("§e" + scan.count("objective") + " §f[Objective]§e markers — "
                    + "only the first is used.");
        }
        for (Marker m : scan.of("objective")) {
            String where = " at §f" + m.pos().getX() + ", " + m.pos().getZ();
            String type = m.arg("type", m.arg("value", "")).toLowerCase(Locale.ROOT);
            if (!type.equals("defend") && !type.equals("hold") && !type.equals("collect")) {
                problems.add("§c[objective]" + where + "§c: §f"
                        + (type.isEmpty() ? "(nothing)" : type)
                        + "§c is not a kind. Line 2 must start §fdefend§c, §fhold§c or §fcollect§c.");
                continue;
            }
            if (type.equals("collect")) {
                String id = m.arg("item", "minecraft:gold_ingot");
                var rl = net.minecraft.resources.ResourceLocation.tryParse(id.toLowerCase(Locale.ROOT));
                if (rl == null || !net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(rl)) {
                    problems.add("§c[objective]" + where + "§c: no item called §f" + id
                            + "§c — every hand-in will be refused.");
                }
            }
        }

        // A teleport pad only does anything if something else shares its id, so a
        // lone pad is a piece of map furniture the author believes is a door.
        Map<String, Integer> padIds = new LinkedHashMap<>();
        for (Marker t : scan.of("teleport")) {
            String id = t.arg("id", t.arg("value", "")).toLowerCase(Locale.ROOT);
            padIds.merge(id, 1, Integer::sum);
        }
        padIds.forEach((id, n) -> {
            String named = id.isEmpty() ? "(no id)" : id;
            if (n == 1) {
                problems.add("§e[teleport] §f" + named + "§e is on its own — a pad goes "
                        + "nowhere until a second one shares its §fid=§e.");
            } else if (n > 2) {
                problems.add("§e" + n + " §f[teleport]§e pads share §f" + named
                        + "§e — each one sends to whichever of the others it finds first.");
            }
        });

        for (BlockPos bad : scan.brokenDealers()) {
            problems.add("§cDealer at §f" + bad.getX() + ", " + bad.getY() + ", " + bad.getZ()
                    + "§c will not sell — check the item id on line 2 and that line 3 "
                    + "starts with a plain number.");
        }

        return problems;
    }
}
