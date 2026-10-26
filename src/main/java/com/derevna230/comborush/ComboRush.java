package com.derevna230.comborush;

import com.derevna230.comborush.booster.BoosterApplier;
import com.derevna230.comborush.booster.BoosterRegistry;
import com.derevna230.comborush.command.ComboRushCommand;
import com.derevna230.comborush.cycle.CycleManager;
import com.derevna230.comborush.anti.PlacedBlocks;
import com.derevna230.comborush.consumable.ConsumableRegistry;
import com.derevna230.comborush.cosmetic.CosmeticManager;
import com.derevna230.comborush.cosmetic.CosmeticRegistry;
import com.derevna230.comborush.data.PlayerProfile;
import com.derevna230.comborush.director.DebuffRegistry;
import com.derevna230.comborush.director.DirectorManager;
import com.derevna230.comborush.leaderboard.Leaderboard;
import com.derevna230.comborush.promo.PromoManager;
import com.derevna230.comborush.score.ScoreListeners;
import com.derevna230.comborush.species.SpeciesApplier;
import com.derevna230.comborush.species.SpeciesRegistry;
import net.minecraft.text.Text;
import com.derevna230.comborush.hud.HudPayload;
import com.derevna230.comborush.hud.HudSync;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.resource.ResourceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ComboRush implements ModInitializer {
    public static final String MOD_ID = "combo-rush";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.playS2C().register(HudPayload.ID, HudPayload.CODEC);
        ResourceManagerHelper.get(ResourceType.SERVER_DATA).registerReloadListener(new BoosterRegistry());
        ResourceManagerHelper.get(ResourceType.SERVER_DATA).registerReloadListener(new SpeciesRegistry());
        ResourceManagerHelper.get(ResourceType.SERVER_DATA).registerReloadListener(new PromoManager());
        ResourceManagerHelper.get(ResourceType.SERVER_DATA).registerReloadListener(new CosmeticRegistry());
        ResourceManagerHelper.get(ResourceType.SERVER_DATA).registerReloadListener(new DirectorManager());
        ResourceManagerHelper.get(ResourceType.SERVER_DATA).registerReloadListener(new ConsumableRegistry());
        ResourceManagerHelper.get(ResourceType.SERVER_DATA).registerReloadListener(new DebuffRegistry());
        PlacedBlocks.init();
        Leaderboard.init();
        CycleManager.init();
        ScoreListeners.register();

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            CycleManager.tick(server);
            PromoManager.tick(server);
            CosmeticManager.tick(server);
            DirectorManager.tick(server);
            Leaderboard.tick(server);
            HudSync.tick(server);
            if (server.getTicks() % 100 == 0)
                server.getPlayerManager().getPlayerList().forEach(p -> { BoosterApplier.sync(p); SpeciesApplier.sync(p); });
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            HudSync.forget(handler.player.getUuid());
            BoosterApplier.sync(handler.player);
            SpeciesApplier.sync(handler.player);
            PromoManager.onJoin(handler.player);
            CosmeticManager.refresh(handler.player);
            Leaderboard.update(handler.player);
            if (PlayerProfile.of(handler.player).eventsDone() == 0)
                handler.player.sendMessage(Text.literal("§6Выбери существо: §f/species§6, магазин: §f/shop"), false);
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            HudSync.forget(handler.player.getUuid());
            Leaderboard.update(handler.player);
            CosmeticManager.onDisconnect(handler.player);
            PromoManager.onDisconnect(handler.player);
        });

        CommandRegistrationCallback.EVENT.register((d, r, e) -> ComboRushCommand.register(d));
        LOGGER.info("Combo Rush загружен");
    }
}
