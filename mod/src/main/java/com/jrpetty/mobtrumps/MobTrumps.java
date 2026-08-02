package com.jrpetty.mobtrumps;

import com.jrpetty.mobtrumps.advancement.ModTriggers;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

@Mod(MobTrumps.MODID)
public class MobTrumps {

    public static final String MODID = "mobtrumps";

    public static final org.slf4j.Logger LOGGER =
            com.mojang.logging.LogUtils.getLogger();

    public MobTrumps(IEventBus modEventBus, ModContainer modContainer) {
        // The catalogue number ("No. 12 / 81") is the mob's position in the
        // declaration order, and serials, saved decks and the book's paging are
        // all read against it. Inserting a mob mid-list silently renumbers
        // every card after it, so say so loudly rather than let it pass.
        if (!com.jrpetty.mobtrumps.game.MobCards.orderIntact()) {
            LOGGER.warn("Mob Trumps: the card ORDER has changed ({} cards, fingerprint {}). "
                            + "Catalogue numbers after the inserted mob have shifted. If this was "
                            + "deliberate, update MobCards.ORDER_FINGERPRINT to {}L.",
                    com.jrpetty.mobtrumps.game.MobCards.ALL.size(),
                    com.jrpetty.mobtrumps.game.MobCards.ORDER_FINGERPRINT,
                    com.jrpetty.mobtrumps.game.MobCards.fingerprint());
        }
        ModItems.ITEMS.register(modEventBus);
        ModItems.DATA_COMPONENTS.register(modEventBus);
        ModItems.CREATIVE_MODE_TABS.register(modEventBus);
        ModBlocks.register(modEventBus);
        ModAttachments.ATTACHMENTS.register(modEventBus);
        ModTriggers.TRIGGERS.register(modEventBus);
        modEventBus.addListener(ModNetworking::register);

        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        NeoForge.EVENT_BUS.addListener(BattleCommands::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(MobDrops::onLivingDrops);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) -> {
            DuelManager.tickTimers(event.getServer());
            BluffManager.tick(event.getServer());
            ConditionTracker.tick(event.getServer());
            ServerSync.tick(event.getServer());
            // check for a ranked season rollover about twice a minute
            if (event.getServer().getTickCount() % 640 == 0) {
                Leaderboard.get(event.getServer()).maybeRollover(event.getServer());
            }
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                CollectionTracker.sync(player);
                BinderStorage.sync(player);
                CollectionTracker.revalidate(player);
                AchievementManager.sync(player);
                // remember what they logged in holding WITHOUT charging them for it
                ConditionTracker.seed(player);
                // the book must be correct the instant they land, not half a second later
                ServerSync.flushNow(player);
                Leaderboard.get(player.serverLevel().getServer()).claimPending(player);
            }
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerRespawnEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                CollectionTracker.sync(player);
                AchievementManager.sync(player);
                ConditionTracker.seed(player); // respawning is not handling
            }
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerChangedDimensionEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                ConditionTracker.seed(player); // nor is stepping through a portal
            }
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                DuelManager.handleLogout(player);
                DraftManager.handleLogout(player);
                TournamentManager.handleLogout(player);
                BlackjackManager.handleLogout(player);
                GuessWhoManager.handleLogout(player);
                DuelTables.clearSeatsOf(player.getUUID());
                TableBattleManager.clear(player.getUUID());
                CampaignManager.clear(player.getUUID());
                BlockReach.forget(player.getUUID());
                ClientPrefsPayload.forget(player.getUUID());
                ConditionTracker.forget(player.getUUID());
                ServerSync.forget(player.getUUID());
            }
        });
    }
}
