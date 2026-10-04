package com.voxelia.mmo.dev;

import com.voxelia.mmo.VoxeliaMMO;
import com.voxelia.mmo.client.ClientAbilities;
import com.voxelia.mmo.client.LeaderboardScreen;
import com.voxelia.mmo.client.MilestoneToasts;
import com.voxelia.mmo.client.ProfileScreen;
import com.voxelia.mmo.client.SkillsScreen;
import com.voxelia.mmo.client.TalentScreen;
import com.voxelia.mmo.config.VoxeliaClientConfig;
import com.voxelia.mmo.network.VoxeliaNetwork;
import com.voxelia.mmo.progression.LeaderboardStore;
import com.voxelia.mmo.progression.Milestones;
import com.voxelia.mmo.progression.SkillEffects;
import com.voxelia.mmo.registry.VoxeliaAttachments;
import com.voxelia.mmo.skill.PlayerSkills;
import com.voxelia.mmo.skill.PlayerTalents;
import com.voxelia.mmo.skill.Skill;
import com.voxelia.mmo.skill.SkillCurve;
import com.voxelia.mmo.skill.Talent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.lang.reflect.Field;
import java.lang.reflect.RecordComponent;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Dev-only UI showcase (never shipped — the jar task excludes this package). Run with
 * {@code ./gradlew runShowcase}: it creates a throwaway world, gives the player a
 * believable mid-game character, opens every Voxelia surface in turn, screenshots
 * each one into {@code run-showcase/screenshots/}, then quits. CI runs it under a
 * virtual display so every change gets real, same-harness before/after images.
 */
@EventBusSubscriber(modid = VoxeliaMMO.MOD_ID, value = Dist.CLIENT)
public final class UiShowcase {
    private UiShowcase() {}

    private static final boolean ON = Boolean.getBoolean("voxelia.showcase");
    private static final long SEED = 1_708_212L;

    private enum Phase { BOOT, CREATING, SETTLING, SHOOTING, DONE }

    private record Shot(String name, Runnable setup, int settleTicks) {}

    private static Phase phase = Phase.BOOT;
    private static int phaseTicks;
    private static int totalTicks;
    private static int shot;
    private static int shotTicks;

    /** Mid-game levels: some abilities unlocked (>= 75), some still locked. */
    private static final Map<Skill, Integer> LEVELS = Map.ofEntries(
        Map.entry(Skill.MINING, 142), Map.entry(Skill.FORAGING, 88), Map.entry(Skill.COMBAT, 117),
        Map.entry(Skill.FARMING, 64), Map.entry(Skill.ACROBATICS, 41), Map.entry(Skill.FISHING, 76),
        Map.entry(Skill.EXCAVATION, 93), Map.entry(Skill.DEFENSE, 81), Map.entry(Skill.COOKING, 33),
        Map.entry(Skill.ALCHEMY, 27), Map.entry(Skill.ARCHERY, 58));

    private static final List<Shot> SHOTS = List.of(
        new Shot("1-hud", UiShowcase::hudShot, 30),
        new Shot("2-skills", () -> open(new SkillsScreen()), 50),
        new Shot("3-skills-hover", () -> { open(new SkillsScreen()); }, 50),
        new Shot("4-menu", () -> { open(new SkillsScreen()); }, 50),
        new Shot("5-talents", () -> open(new TalentScreen()), 50),
        new Shot("6-profile", () -> open(new ProfileScreen()), 60),
        new Shot("7-leaderboard", () -> open(new LeaderboardScreen()), 60));

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        if (!ON) return;
        Minecraft mc = Minecraft.getInstance();
        totalTicks++;
        phaseTicks++;
        if (totalTicks > 20 * 60 * 14) {
            log("timed out in phase " + phase + " (screen=" + name(mc.screen) + ")");
            mc.stop();
            return;
        }
        if (totalTicks % 200 == 0) log("phase " + phase + " screen=" + name(mc.screen));

        switch (phase) {
            case BOOT -> {
                if (mc.screen instanceof TitleScreen && phaseTicks > 60) {
                    log("creating world");
                    mc.createWorldOpenFlows().createFreshLevel("showcase",
                        new LevelSettings("showcase", GameType.SURVIVAL, false, Difficulty.PEACEFUL, true,
                            new GameRules(), WorldDataConfiguration.DEFAULT),
                        new WorldOptions(SEED, false, false),
                        WorldPresets::createNormalWorldDimensions, mc.screen);
                    next(Phase.CREATING);
                }
            }
            case CREATING -> {
                if (mc.player != null && mc.level != null && mc.screen == null && phaseTicks > 40) {
                    log("in world; seeding character");
                    seed(mc);
                    next(Phase.SETTLING);
                }
            }
            case SETTLING -> {
                if (phaseTicks > 700) {
                    log("settled; shooting");
                    shot = 0;
                    shotTicks = 0;
                    next(Phase.SHOOTING);
                }
            }
            case SHOOTING -> shoot(mc);
            case DONE -> {
                if (phaseTicks > 20) mc.stop();
            }
        }
    }

    private static void shoot(Minecraft mc) {
        Shot s = SHOTS.get(shot);
        if (shotTicks == 0) {
            mc.gui.getChat().clearMessages(false);
            mc.getToasts().clear();
            park(mc);
            s.setup().run();
        }
        // Hover / menu state needs a rendered frame first (hitboxes are filled in render).
        if (shotTicks == s.settleTicks() - 12) {
            if (s.name().endsWith("skills-hover")) hoverCard(mc, Skill.COOKING);
            if (s.name().endsWith("menu")) openMenu(mc);
        }
        if (shotTicks == s.settleTicks()) {
            Screenshot.grab(mc.gameDirectory, s.name() + ".png", mc.getMainRenderTarget(), msg -> {});
            log("captured " + s.name());
        }
        shotTicks++;
        if (shotTicks > s.settleTicks() + 6) {
            shot++;
            shotTicks = 0;
            if (shot >= SHOTS.size()) {
                mc.setScreen(null);
                next(Phase.DONE);
            }
        }
    }

    // ── shot setups ─────────────────────────────────────────────────────────

    private static void hudShot() {
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(null);
        VoxeliaClientConfig.setShowHud(true);
        VoxeliaClientConfig.setShowSidebar(true);
        ClientAbilities.select(Skill.COMBAT);
        ClientAbilities.setCooldown(Skill.COMBAT.ordinal(), 20 * 40);
        MilestoneToasts.trigger(Skill.FISHING.ordinal(), Milestones.Kind.ABILITY.ordinal(), 75);
    }

    private static void open(Screen screen) {
        Minecraft.getInstance().setScreen(screen);
    }

    // ── world + character seeding (integrated server thread) ────────────────

    private static void seed(Minecraft mc) {
        mc.options.hideGui = false;
        UUID me = mc.player.getUUID();
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null) return;
        server.execute(() -> {
            ServerPlayer p = server.getPlayerList().getPlayer(me);
            if (p == null) return;
            ServerLevel level = p.serverLevel();
            server.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
            server.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server);
            server.getGameRules().getRule(GameRules.RULE_ANNOUNCE_ADVANCEMENTS).set(false, server);
            level.setDayTime(4200);
            level.setWeatherParameters(1_000_000, 0, false, false);

            BlockPos spawn = level.getSharedSpawnPos();
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn.getX(), spawn.getZ());
            p.teleportTo(level, spawn.getX() + 0.5, y, spawn.getZ() + 0.5, 135f, 4f);

            p.getInventory().clearContent();
            p.getInventory().setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
            p.getInventory().setItem(1, new ItemStack(Items.IRON_SWORD));
            p.getInventory().setItem(2, new ItemStack(Items.BOW));
            p.getInventory().setItem(3, new ItemStack(Items.FISHING_ROD));
            p.getInventory().setItem(4, new ItemStack(Items.IRON_SHOVEL));
            p.getInventory().setItem(5, new ItemStack(Items.COOKED_BEEF, 24));
            p.getInventory().setItem(6, new ItemStack(Items.TORCH, 48));
            p.getInventory().setItem(8, new ItemStack(Items.OAK_LOG, 32));
            p.getInventory().selected = 0;

            PlayerSkills skills = new PlayerSkills();
            for (Skill s : Skill.values()) {
                int lvl = LEVELS.getOrDefault(s, 1);
                int base = SkillCurve.xpForLevel(lvl);
                int span = SkillCurve.xpForLevel(lvl + 1) - base;
                skills.addXp(s, base + span * ((s.ordinal() * 37) % 90 + 5) / 100);
            }
            p.setData(VoxeliaAttachments.PLAYER_SKILLS.get(), skills);

            // Spend most earned points, leaving a couple unspent in a few skills.
            PlayerTalents talents = new PlayerTalents();
            for (Skill s : Skill.values()) {
                int earned = LEVELS.getOrDefault(s, 1) / 20;
                int toSpend = Math.max(0, earned - (s == Skill.MINING || s == Skill.EXCAVATION ? 2 : 0));
                List<Talent> tree = Talent.forSkill(s);
                for (int i = 0; toSpend > 0; i = (i + 1) % tree.size()) {
                    Talent t = tree.get(i);
                    if (talents.getRank(t) < 5) { talents.setRank(t, talents.getRank(t) + 1); toSpend--; }
                }
            }
            p.setData(VoxeliaAttachments.PLAYER_TALENTS.get(), talents);

            p.getStats().setValue(p, Stats.CUSTOM.get(Stats.PLAY_TIME), 20 * 60 * (60 * 41 + 17));
            p.getStats().setValue(p, Stats.CUSTOM.get(Stats.DEATHS), 14);
            p.getStats().setValue(p, Stats.CUSTOM.get(Stats.MOB_KILLS), 2318);

            LeaderboardStore.record(p);
            fakeRivals();

            SkillEffects.apply(p);
            p.setHealth(p.getMaxHealth());
            VoxeliaNetwork.syncTo(p);
            VoxeliaNetwork.syncTalents(p);
            VoxeliaNetwork.syncPerks(p);
        });
    }

    @SuppressWarnings("unchecked")
    private static void fakeRivals() {
        String[] names = {"Kestrel", "mossy_brick", "Tanglewood", "ores4days", "Vexxa",
            "lanternfish", "Quarrymaster", "pebble", "Brindle", "Oakhart", "NightSkiff"};
        int[] base = {171, 160, 133, 128, 111, 97, 90, 72, 66, 49, 31};
        try {
            Field f = LeaderboardStore.class.getDeclaredField("ENTRIES");
            f.setAccessible(true);
            Map<String, LeaderboardStore.Entry> entries = (Map<String, LeaderboardStore.Entry>) f.get(null);
            for (int i = 0; i < names.length; i++) {
                LeaderboardStore.Entry e = new LeaderboardStore.Entry();
                e.name = names[i];
                for (Skill s : Skill.values()) {
                    int lvl = Math.max(1, base[i] + ((s.ordinal() * 29 + i * 13) % 61) - 30);
                    e.xp.put(s.id(), SkillCurve.xpForLevel(Math.min(SkillCurve.MAX_LEVEL, lvl)) + 7);
                }
                e.lastSeen = System.currentTimeMillis();
                entries.put(UUID.nameUUIDFromBytes(names[i].getBytes()).toString(), e);
            }
        } catch (ReflectiveOperationException ex) {
            log("could not seed rivals: " + ex);
        }
    }

    // ── pointer control ─────────────────────────────────────────────────────

    /** Parks the cursor in the top-left corner, clear of every panel. */
    private static void park(Minecraft mc) {
        setCursor(mc, 4, 4);
    }

    private static void hoverCard(Minecraft mc, Skill target) {
        try {
            Field f = mc.screen.getClass().getDeclaredField("cards");
            f.setAccessible(true);
            for (Object card : (List<?>) f.get(mc.screen)) {
                Map<String, Object> c = recordValues(card);
                if (c.get("skill") == target) {
                    double gx = ((int) c.get("x1") + (int) c.get("x2")) / 2.0;
                    double gy = ((int) c.get("y1") + (int) c.get("y2")) / 2.0;
                    double scale = mc.getWindow().getGuiScale();
                    setCursor(mc, gx * scale, gy * scale);
                    return;
                }
            }
            log("hover target not found");
        } catch (ReflectiveOperationException ex) {
            log("hover failed: " + ex);
        }
    }

    private static void openMenu(Minecraft mc) {
        try {
            Field f = mc.screen.getClass().getDeclaredField("menu");
            f.setAccessible(true);
            Object menu = f.get(mc.screen);
            Field open = menu.getClass().getDeclaredField("open");
            open.setAccessible(true);
            open.setBoolean(menu, true);
        } catch (ReflectiveOperationException ex) {
            log("menu open failed: " + ex);
        }
    }

    private static void setCursor(Minecraft mc, double x, double y) {
        try {
            MouseHandler mh = mc.mouseHandler;
            Field xf = MouseHandler.class.getDeclaredField("xpos");
            Field yf = MouseHandler.class.getDeclaredField("ypos");
            xf.setAccessible(true);
            yf.setAccessible(true);
            xf.setDouble(mh, x);
            yf.setDouble(mh, y);
        } catch (ReflectiveOperationException ex) {
            log("cursor failed: " + ex);
        }
    }

    private static Map<String, Object> recordValues(Object rec) throws ReflectiveOperationException {
        Map<String, Object> out = new java.util.HashMap<>();
        for (RecordComponent rc : rec.getClass().getRecordComponents()) {
            var m = rc.getAccessor();
            m.setAccessible(true);
            out.put(rc.getName(), m.invoke(rec));
        }
        return out;
    }

    private static void next(Phase p) {
        phase = p;
        phaseTicks = 0;
    }

    private static String name(Screen s) {
        return s == null ? "none" : s.getClass().getSimpleName();
    }

    private static void log(String msg) {
        VoxeliaMMO.LOGGER.info("[showcase] {}", msg);
    }
}
