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

    public MobTrumps(IEventBus modEventBus, ModContainer modContainer) {
        ModItems.ITEMS.register(modEventBus);
        ModItems.DATA_COMPONENTS.register(modEventBus);
        ModItems.CREATIVE_MODE_TABS.register(modEventBus);
        ModAttachments.ATTACHMENTS.register(modEventBus);
        ModTriggers.TRIGGERS.register(modEventBus);
        modEventBus.addListener(ModNetworking::register);

        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        NeoForge.EVENT_BUS.addListener(BattleCommands::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(MobDrops::onLivingDrops);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) ->
                DuelManager.tickTimers(event.getServer()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                CollectionTracker.sync(player);
                BinderStorage.sync(player);
                CollectionTracker.revalidate(player);
            }
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerRespawnEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                CollectionTracker.sync(player);
            }
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                DuelManager.handleLogout(player);
                TradeManager.handleLogout(player);
                DraftManager.handleLogout(player);
                TournamentManager.handleLogout(player);
            }
        });
    }
}
