package com.jrpetty.aztecabyss.engine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The things a player walks up to and pays.
 *
 * <p>Every one of these is a sign, and every one is described entirely by what is
 * written on it. There is no registry of machines and no per-map code - the engine
 * reads a kind, reads its arguments, and does the corresponding thing. Adding a
 * shop to a map is placing a sign, and that is the whole of it.
 *
 * <p>The perk machine is the clearest case of why this is worth doing. The old
 * hand-written version had four perks baked into an enum, and a fifth meant a code
 * change. Here a perk is <em>any</em> status effect named on a sign, so the set of
 * perks a map can have is the set of effects the game has - plus whatever any other
 * mod has added, for free.
 */
public final class Machines {

    private Machines() {
    }

    /**
     * The material ladder the upgrade bench climbs.
     *
     * <p>Kept as plain item ids rather than as enchantments on purpose. A tier
     * jump is legible from the item in your hand at a glance, mid-fight, which is
     * exactly when you need to know whether the money you just spent did anything.
     */
    private static final Map<String, String> LADDER = Map.ofEntries(
            Map.entry("wooden_sword", "stone_sword"),
            Map.entry("stone_sword", "iron_sword"),
            Map.entry("iron_sword", "diamond_sword"),
            Map.entry("diamond_sword", "netherite_sword"),
            Map.entry("wooden_axe", "stone_axe"),
            Map.entry("stone_axe", "iron_axe"),
            Map.entry("iron_axe", "diamond_axe"),
            Map.entry("diamond_axe", "netherite_axe"),
            Map.entry("leather_chestplate", "chainmail_chestplate"),
            Map.entry("chainmail_chestplate", "iron_chestplate"),
            Map.entry("iron_chestplate", "diamond_chestplate"),
            Map.entry("diamond_chestplate", "netherite_chestplate"),
            Map.entry("leather_helmet", "chainmail_helmet"),
            Map.entry("chainmail_helmet", "iron_helmet"),
            Map.entry("iron_helmet", "diamond_helmet"),
            Map.entry("diamond_helmet", "netherite_helmet"),
            Map.entry("bow", "crossbow"));


    /**
     * Handles a right-click on an interactive marker.
     *
     * @return true if this was a machine, whether or not the player could afford it
     */
    public static boolean use(ServerLevel level, ServerPlayer player, Marker marker) {
        return switch (marker.kind()) {
            case "door" -> door(level, player, marker);
            case "box" -> box(level, player, marker);
            case "perk" -> perk(level, player, marker);
            case "upgrade" -> upgrade(level, player, marker);
            case "loot" -> loot(level, player, marker);
            case "trap" -> trap(level, player, marker);
            case "objective" -> {
                EngineArena a = EngineArena.active();
                yield a != null && a.handInTo(player, marker);
            }
            default -> false;
        };
    }

    private static Currency currencyOf(Marker m) {
        return Currency.byId(m.arg("currency", null));
    }

    private static boolean pay(ServerPlayer player, Marker m, int price, String what) {
        Currency c = currencyOf(m);
        if (c.charge(player, price)) {
            return true;
        }
        player.displayClientMessage(Component.literal(
                "§c" + what + " costs " + c.format(price) + " §7— you have "
                        + c.format(c.balance(player))), true);
        return false;
    }

    // ------------------------------------------------------------------
    // Door
    // ------------------------------------------------------------------

    /**
     * Buys a sealed area open.
     *
     * <p>Clears a hole through whatever the sign is mounted on and marks the named
     * area live, which is what lets that area's {@code [Horde]} markers start
     * sending anything. A map is therefore as big as the players have paid for -
     * and the horde grows with it, so opening ground is a decision rather than a
     * formality.
     */
    private static boolean door(ServerLevel level, ServerPlayer player, Marker m) {
        String area = m.arg("area", m.arg("value", "")).toLowerCase(Locale.ROOT);
        if (area.isEmpty()) {
            player.displayClientMessage(Component.literal(
                    "§7This door names no area — add §farea=<name>§7 to the sign."), true);
            return true;
        }
        EngineArena arena = EngineArena.active();
        if (arena == null) {
            player.displayClientMessage(Component.literal("§7No run in progress."), true);
            return true;
        }
        if (arena.isAreaOpen(area)) {
            player.displayClientMessage(Component.literal("§7That way is already open."), true);
            return true;
        }
        int price = m.intArg("cost", m.intArg("price", 1000));
        if (!pay(player, m, price, "Opening the way")) {
            return true;
        }
        int width = m.intArg("width", 3);
        int height = m.intArg("height", 3);
        carve(level, m.pos(), m.facing(), width, height);
        arena.openArea(area);
        level.playSound(null, m.pos(), SoundEvents.WOOD_BREAK, SoundSource.BLOCKS, 1.4F, 0.7F);
        for (ServerPlayer p : level.players()) {
            p.displayClientMessage(Component.literal(
                    "§6The way to §f" + area + "§6 is open. §7It comes from there now too."), false);
        }
        return true;
    }

    /**
     * Punches a doorway through the wall the sign is on.
     *
     * <p>Every block goes through the arena's tracked write, so the doorway - and
     * the sign itself - come back when the run ends. Without that, buying a door
     * permanently rebuilt the map: the wall stayed open and the sign that sold it
     * was gone, so the next run started with that area already unlocked and no way
     * to gate it again.
     */
    private static void carve(ServerLevel level, BlockPos signPos, Direction facing, int width, int height) {
        EngineArena arena = EngineArena.active();
        Direction across = facing.getClockWise();
        int half = Math.max(0, width / 2);
        // Two blocks deep, so a doorway in a thick wall actually goes through.
        for (int depth = 0; depth <= 1; depth++) {
            for (int w = -half; w <= half; w++) {
                for (int h = 0; h < height; h++) {
                    BlockPos at = signPos.relative(facing.getOpposite(), depth)
                            .relative(across, w).above(h);
                    if (arena != null) {
                        arena.setTracked(at, Blocks.AIR.defaultBlockState());
                    } else {
                        level.setBlock(at, Blocks.AIR.defaultBlockState(), 2);
                    }
                }
            }
        }
        // The sign goes too, so a door cannot be bought twice - but tracked, so it
        // is standing again for the next run.
        if (arena != null) {
            arena.setTracked(signPos, Blocks.AIR.defaultBlockState());
        } else {
            level.setBlock(signPos, Blocks.AIR.defaultBlockState(), 2);
        }
    }

    // ------------------------------------------------------------------
    // Box
    // ------------------------------------------------------------------

    private static boolean box(ServerLevel level, ServerPlayer player, Marker m) {
        int price = m.intArg("price", 950);
        if (!pay(player, m, price, "The Box")) {
            return true;
        }
        RandomSource rng = level.getRandom();
        ItemStack prize = poolFor(m, "box", ItemPool::defaultBox).roll(rng, level);
        if (!player.getInventory().add(prize)) {
            player.drop(prize, false);
        }
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,
                m.pos().getX() + 0.5, m.pos().getY() + 1.0, m.pos().getZ() + 0.5,
                20, 0.3, 0.5, 0.3, 0.02);
        level.playSound(null, m.pos(), SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.8F, 1.2F);
        player.displayClientMessage(Component.literal(
                "§6The Box §7gives you §f" + prize.getHoverName().getString()), true);
        return true;
    }

    /**
     * Which pool a machine draws from, in the order an author would expect.
     *
     * <p>Three places can say, and the most specific wins. {@code items=} written
     * on the marker itself beats {@code pool=} naming one from the ruleset, which
     * beats the built-in list. That ordering is the point: a one-off Box in one map
     * should not need a datapack, and a map with eight Boxes that should all agree
     * should not need the list written eight times.
     */
    private static ItemPool poolFor(Marker m, String defaultName,
                                    java.util.function.Supplier<ItemPool> fallback) {
        String inline = m.arg("items", "");
        if (!inline.isBlank()) {
            ItemPool pool = ItemPool.inline(inline);
            if (!pool.isEmpty()) {
                return pool;
            }
        }
        EngineArena arena = EngineArena.active();
        if (arena != null) {
            String named = m.arg("pool", defaultName).toLowerCase(Locale.ROOT);
            ItemPool pool = arena.rules().pools.get(named);
            if (pool != null && !pool.isEmpty()) {
                return pool;
            }
        }
        return fallback.get();
    }

    // ------------------------------------------------------------------
    // Perk
    // ------------------------------------------------------------------

    /**
     * Any status effect, for a price, for the rest of the run.
     *
     * <pre>
     *   [Perk]
     *   minecraft:health_boost
     *   2500 amp=2
     * </pre>
     *
     * <p>Effects are applied for a very long duration rather than truly infinitely,
     * so that death clears them without the engine having to track who bought what.
     */
    private static boolean perk(ServerLevel level, ServerPlayer player, Marker m) {
        String id = m.arg("effect", m.arg("value", ""));
        if (id.isEmpty()) {
            player.displayClientMessage(Component.literal(
                    "§7This perk names no effect — put one on line 2, "
                            + "like §fminecraft:health_boost§7."), true);
            return true;
        }
        ResourceLocation rl = ResourceLocation.tryParse(id.toLowerCase(Locale.ROOT));
        if (rl == null) {
            return true;
        }
        var holder = BuiltInRegistries.MOB_EFFECT.getHolder(
                ResourceKey.create(Registries.MOB_EFFECT, rl));
        if (holder.isEmpty()) {
            player.displayClientMessage(Component.literal(
                    "§7No such effect: §f" + id), true);
            return true;
        }
        int price = m.intArg("price", 2500);
        if (!pay(player, m, price, "That draught")) {
            return true;
        }
        int amp = Math.max(0, Math.min(9, m.intArg("amp", 0)));
        player.addEffect(new MobEffectInstance(holder.get(), 60 * 60 * 20, amp, false, false, true));
        level.playSound(null, m.pos(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0F, 0.9F);
        player.displayClientMessage(Component.literal(
                "§d✦ " + holder.get().value().getDisplayName().getString()
                        + (amp > 0 ? " " + (amp + 1) : "")), false);
        return true;
    }

    // ------------------------------------------------------------------
    // Upgrade bench
    // ------------------------------------------------------------------

    private static boolean upgrade(ServerLevel level, ServerPlayer player, Marker m) {
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            player.displayClientMessage(Component.literal(
                    "§7Hold what you want improved."), true);
            return true;
        }
        ResourceLocation current = BuiltInRegistries.ITEM.getKey(held.getItem());
        String next = LADDER.get(current.getPath());
        if (next == null) {
            player.displayClientMessage(Component.literal(
                    "§7" + held.getHoverName().getString() + " cannot go any further."), true);
            return true;
        }
        int price = m.intArg("price", 5000);
        if (!pay(player, m, price, "That upgrade")) {
            return true;
        }
        Item better = BuiltInRegistries.ITEM.get(
                ResourceLocation.fromNamespaceAndPath("minecraft", next));
        // A fresh item of the better material. An upgrade repairing the weapon as
        // a side effect is a small bonus rather than a problem, and tracking
        // durability across a material change is more fiddle than it is worth.
        ItemStack made = new ItemStack(better, held.getCount());
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, made);
        level.playSound(null, m.pos(), SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 0.8F, 1.2F);
        player.displayClientMessage(Component.literal(
                "§6⚒ " + made.getHoverName().getString()), false);
        return true;
    }

    // ------------------------------------------------------------------
    // Loot cache
    // ------------------------------------------------------------------

    private static boolean loot(ServerLevel level, ServerPlayer player, Marker m) {
        EngineArena arena = EngineArena.active();
        if (arena == null) {
            return true;
        }
        if (!arena.claimLoot(m.pos())) {
            player.displayClientMessage(Component.literal("§7Already taken this round."), true);
            return true;
        }
        int tier = Math.max(1, Math.min(3, m.intArg("tier", 1)));
        // A cache draws three times from its tier's pool rather than handing over
        // one fixed list, so two caches at the same tier are not the same cache.
        ItemPool pool = poolFor(m, "loot_" + tier, () -> ItemPool.defaultLoot(tier));
        RandomSource rng = level.getRandom();
        for (int i = 0; i < 3; i++) {
            ItemStack s = pool.roll(rng, level);
            if (!player.getInventory().add(s)) {
                player.drop(s, false);
            }
        }
        level.playSound(null, m.pos(), SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.7F, 1.6F);
        player.displayClientMessage(Component.literal("§a✔ Supplies."), true);
        return true;
    }

    /**
     * A trap: pay to make a piece of the map lethal for a while.
     *
     * <pre>
     *   [Trap]
     *   cost=1000
     *   damage=12 radius=5 seconds=10
     * </pre>
     *
     * <p>The cooldown matters more than the damage. A trap you can re-arm the
     * instant it stops is a wall the horde simply cannot cross, and the map stops
     * being about where you stand. Paying again is meant to be a decision about
     * timing, not a tax on a permanent kill zone.
     */
    private static boolean trap(ServerLevel level, ServerPlayer player, Marker m) {
        EngineArena arena = EngineArena.active();
        if (arena == null) {
            player.displayClientMessage(Component.literal("§7No run in progress."), true);
            return true;
        }
        int seconds = Math.max(1, Math.min(60, m.intArg("seconds", 10)));
        int cooldown = Math.max(0, Math.min(600, m.intArg("cooldown", 45)));
        long now = level.getGameTime();
        // Check availability before charging, so a refusal never costs anything.
        String refusal = arena.trapUnavailable(m.pos(), now);
        if (refusal != null) {
            player.displayClientMessage(Component.literal(refusal), true);
            return true;
        }
        int price = m.intArg("cost", m.intArg("price", 1000));
        if (!pay(player, m, price, "Arming that")) {
            return true;
        }
        arena.armTrap(m.pos(), seconds, cooldown);
        level.playSound(null, m.pos(), SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.2F, 0.6F);
        for (ServerPlayer p : level.players()) {
            p.displayClientMessage(Component.literal("§6Something has been switched on."), true);
        }
        return true;
    }

    /**
     * What a zone marker means, worked out once.
     *
     * <p>This used to be worked out per player, per zone, on every tick. Standing
     * in one meant lower-casing the effect name, parsing it into a
     * {@link ResourceLocation}, building a {@link ResourceKey} and looking it up
     * in the registry — twenty times a second, each — to arrive at the same
     * answer, because a {@link Marker} is a snapshot of a sign taken when the map
     * was scanned and its arguments cannot change while a run is going. Four
     * players in three zones was around two hundred and forty parses a second.
     *
     * <p>The radius is squared here because the only reader compares it against
     * a squared distance.
     *
     * @param effect null if the sign named no effect, or named one that does not
     *               exist — in which case the zone simply does nothing, which is
     *               what it did before
     */
    public record ZoneEffect(int radiusSq,
                             net.minecraft.core.Holder<MobEffect> effect,
                             int amp) {

        public static ZoneEffect of(Marker zone) {
            int radius = zone.intArg("radius", 8);
            int amp = Math.max(0, Math.min(9, zone.intArg("amp", 0)));
            String id = zone.arg("effect", "");
            if (id.isEmpty()) {
                return new ZoneEffect(radius * radius, null, amp);
            }
            ResourceLocation rl = ResourceLocation.tryParse(id.toLowerCase(Locale.ROOT));
            if (rl == null) {
                return new ZoneEffect(radius * radius, null, amp);
            }
            var holder = BuiltInRegistries.MOB_EFFECT.getHolder(
                    ResourceKey.create(Registries.MOB_EFFECT, rl));
            return new ZoneEffect(radius * radius, holder.orElse(null), amp);
        }
    }

    /** Tops up a zone's effect on somebody stood in it. */
    public static void applyZone(ServerPlayer player, ZoneEffect zone) {
        if (zone.effect() == null) {
            return;
        }
        // Short and constantly refreshed, so it lapses the moment you step out.
        player.addEffect(new MobEffectInstance(zone.effect(), 60, zone.amp(), true, false, false));
    }
}
