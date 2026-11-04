package com.derevna230.comborush;

import com.derevna230.comborush.booster.BoosterApplier;
import com.derevna230.comborush.booster.BoosterRegistry;
import com.derevna230.comborush.command.ComboRushCommand;
import com.derevna230.comborush.cycle.CycleManager;
import com.derevna230.comborush.anti.PlacedBlocks;
import com.derevna230.comborush.anti.SpawnerTracker;
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
import com.derevna230.comborush.hud.HudPayload;
import com.derevna230.comborush.hud.HudSync;
import com.derevna230.comborush.hud.ShopActionPayload;
import com.derevna230.comborush.shop.ShopNet;
import com.derevna230.comborush.armor.ArmorSetApplier;
import com.derevna230.comborush.armor.ArmorSetRegistry;
import com.derevna230.comborush.armor.ArmorTooltips;
import com.derevna230.comborush.cases.CaseRegistry;
import com.derevna230.comborush.item.LootBonus;
import com.derevna230.comborush.rift.EliteMobs;
import com.derevna230.comborush.rift.RiftManager;
import com.derevna230.comborush.item.ModItems;
import com.derevna230.comborush.pet.ModEntities;
import com.derevna230.comborush.pet.PetManager;
import com.derevna230.comborush.weapon.WeaponAbilities;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.server.network.ServerPlayerEntity;
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
        ModConfig.load();
        com.derevna230.comborush.sound.ModSounds.init();
        ModItems.init();
        ModEntities.init();
        com.derevna230.comborush.universe.Universes.init();
        com.derevna230.comborush.lobby.Lobby.init();
        com.derevna230.comborush.item.WeaponEvents.init();
        PetManager.init();
        PayloadTypeRegistry.playS2C().register(HudPayload.ID, HudPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ShopActionPayload.ID, ShopActionPayload.CODEC);
        ShopNet.register();
        ResourceManagerHelper.get(ResourceType.SERVER_DATA).registerReloadListener(new BoosterRegistry());
        ResourceManagerHelper.get(ResourceType.SERVER_DATA).registerReloadListener(new SpeciesRegistry());
        ResourceManagerHelper.get(ResourceType.SERVER_DATA).registerReloadListener(new PromoManager());
        ResourceManagerHelper.get(ResourceType.SERVER_DATA).registerReloadListener(new CosmeticRegistry());
        ResourceManagerHelper.get(ResourceType.SERVER_DATA).registerReloadListener(new DirectorManager());
        ResourceManagerHelper.get(ResourceType.SERVER_DATA).registerReloadListener(new ConsumableRegistry());
        ResourceManagerHelper.get(ResourceType.SERVER_DATA).registerReloadListener(new DebuffRegistry());
        ResourceManagerHelper.get(ResourceType.SERVER_DATA).registerReloadListener(new ArmorSetRegistry());
        ResourceManagerHelper.get(ResourceType.SERVER_DATA).registerReloadListener(new CaseRegistry());
        ResourceManagerHelper.get(ResourceType.SERVER_DATA).registerReloadListener(new com.derevna230.comborush.campaign.QuestRegistry());
        com.derevna230.comborush.campaign.Campaign.init();
        PlacedBlocks.init();
        SpawnerTracker.init();
        Leaderboard.init();
        CycleManager.init();
        ScoreListeners.register();
        WeaponAbilities.register();
        LootBonus.register();
        EliteMobs.register();
        RiftManager.register();
        com.derevna230.comborush.skate.SkateboardEntity.registerEvents();

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            CycleManager.tick(server);
            PromoManager.tick(server);
            CosmeticManager.tick(server);
            DirectorManager.tick(server);
            Leaderboard.tick(server);
            HudSync.tick(server);
            ArmorTooltips.tick(server);
            PetManager.tick(server);
            WeaponAbilities.tick(server);
            EliteMobs.tick(server);
            RiftManager.tick(server);
            com.derevna230.comborush.boss.TraderSpawner.tick(server);
            com.derevna230.comborush.campaign.Campaign.tick(server);
            if (server.getTicks() % 100 == 0)
                server.getPlayerManager().getPlayerList().forEach(p -> { BoosterApplier.sync(p); SpeciesApplier.sync(p); ArmorSetApplier.sync(p); });
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            HudSync.forget(handler.player.getUuid());
            BoosterApplier.sync(handler.player);
            SpeciesApplier.sync(handler.player);
            PromoManager.onJoin(handler.player);
            CosmeticManager.refresh(handler.player);
            Leaderboard.update(handler.player);
            if (!PlayerProfile.of(handler.player).started())
                com.derevna230.comborush.score.ScoreEngine.remindStart(handler.player, true);
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            HudSync.forget(handler.player.getUuid());
            ArmorTooltips.forget(handler.player.getUuid());
            Leaderboard.update(handler.player);
            CosmeticManager.onDisconnect(handler.player);
            PromoManager.onDisconnect(handler.player);
            PetManager.despawn(handler.player);
        });

        // смена брони: сразу пересчитать атрибуты и эффекты комплектов
        ServerEntityEvents.EQUIPMENT_CHANGE.register((entity, slot, previous, current) -> {
            if (entity instanceof ServerPlayerEntity sp
                    && (slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR || slot.getType() == EquipmentSlot.Type.HAND))
                ArmorSetApplier.sync(sp);
        });

        CommandRegistrationCallback.EVENT.register((d, r, e) -> ComboRushCommand.register(d));
        LOGGER.info("Combo Rush загружен");
    }
}
