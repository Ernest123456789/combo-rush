package com.derevna230.comborush.client;

import com.derevna230.comborush.hud.HudPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import com.derevna230.comborush.item.ModItems;
import com.derevna230.comborush.pet.ModEntities;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.item.ModelPredicateProviderRegistry;
import net.minecraft.item.Item;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/** Клиентская часть: HUD справа и анимации подсчёта. Без неё мод работает как раньше (action bar) */
public class ComboRushClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(HudPayload.ID, (payload, context) -> HudModel.receive(payload));
        HudRenderCallback.EVENT.register((context, tickCounter) -> HudRenderer.render(context));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> { HudModel.reset(); HudModel.pockets = java.util.Set.of(); StoryHud.reset(); MusicPlayer.reset(); });
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(MusicPlayer::tick);
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(c -> Hints.tick());
        Hints.register();
        TitleCredits.register();
        InventoryButtons.register();
        EntityRendererRegistry.register(ModEntities.PET, PetRenderer::new);
        EntityRendererRegistry.register(ModEntities.DECOR, DecorRenderer::new);
        EntityRendererRegistry.register(ModEntities.SKATE, SkateRenderer::new);
        EntityRendererRegistry.register(ModEntities.STORY_NPC, StoryRenderer::new);
        EntityRendererRegistry.register(ModEntities.STORY_PROP, StoryRenderer::new);
        EntityRendererRegistry.register(ModEntities.GOLEM, GolemRenderer::new);
        EntityRendererRegistry.register(ModEntities.TRADER, TraderRenderer::new);
        ModEntities.CARS.forEach((id, type) -> EntityRendererRegistry.register(type, ctx -> new CarRenderer(ctx, id)));
        // J — журнал «Сценарий»
        net.minecraft.client.option.KeyBinding journalKey = net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper.registerKeyBinding(
            new net.minecraft.client.option.KeyBinding("key.combo-rush.journal", net.minecraft.client.util.InputUtil.Type.KEYSYM,
                org.lwjgl.glfw.GLFW.GLFW_KEY_J, "key.categories.combo-rush"));
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (journalKey.wasPressed()) {
                if (client.player == null || client.currentScreen != null) continue;
                if (ClientPlayNetworking.canSend(com.derevna230.comborush.hud.ShopActionPayload.ID))
                    ClientPlayNetworking.send(new com.derevna230.comborush.hud.ShopActionPayload("camp_open", 0, 0, ""));
            }
        });
        // оружие: ЛКМ — выстрел, ПКМ — прицел/глушитель, R — перезарядка, V — осмотр (всё остальное — в WeaponClient)
        net.minecraft.client.option.KeyBinding reloadKey = net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper.registerKeyBinding(
            new net.minecraft.client.option.KeyBinding("key.combo-rush.gun_reload", net.minecraft.client.util.InputUtil.Type.KEYSYM,
                org.lwjgl.glfw.GLFW.GLFW_KEY_R, "key.categories.combo-rush"));
        net.minecraft.client.option.KeyBinding inspectKey = net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper.registerKeyBinding(
            new net.minecraft.client.option.KeyBinding("key.combo-rush.gun_inspect", net.minecraft.client.util.InputUtil.Type.KEYSYM,
                org.lwjgl.glfw.GLFW.GLFW_KEY_V, "key.categories.combo-rush"));
        WeaponClient.init();
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (reloadKey.wasPressed()) if (client.player != null && client.currentScreen == null) WeaponClient.keyReload();
            while (inspectKey.wasPressed()) WeaponClient.keyInspect();
        });
        // комната меню «Противостояния»: пока игрок в ней и нет других экранов — показываем меню
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(StandoffScreen::tickReopen);
        // скейт: клиент сам считает прыжок и планирование, ему нужно знать, зажат ли пробел
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client ->
            com.derevna230.comborush.skate.SkateboardEntity.CLIENT_JUMP = client.player != null && client.options.jumpKey.isPressed());
        // машины: ручник (пробел), нитро (Ctrl) и надпись со скоростью над хотбаром
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client -> {
            boolean driving = client.player != null
                && client.player.getVehicle() instanceof com.derevna230.comborush.car.CarEntity car
                && car.getControllingPassenger() == client.player;
            com.derevna230.comborush.car.CarEntity.CLIENT_HANDBRAKE = driving && client.options.jumpKey.isPressed();
            com.derevna230.comborush.car.CarEntity.CLIENT_NITRO = driving && client.options.sprintKey.isPressed();
            if (driving)   // камера водителя поворачивается плавно, а не 20 раз в секунду рывками
                ((com.derevna230.comborush.car.CarEntity) client.player.getVehicle()).smoothDriverCamera(client.player);
            if (driving && client.currentScreen == null)
                client.inGameHud.setOverlayMessage(net.minecraft.text.Text.literal(
                    ((com.derevna230.comborush.car.CarEntity) client.player.getVehicle()).hudText()), false);
        });
        // щиты из кейсов: при блоке — модель <id>_blocking
        for (Item shield : ModItems.SHIELDS)
            ModelPredicateProviderRegistry.register(shield, Identifier.ofVanilla("blocking"), (stack, world, entity, seed) ->
                entity != null && entity.isUsingItem() && entity.getActiveItem() == stack ? 1.0f : 0.0f);

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
            dispatcher.register(ClientCommandManager.literal("crhud")
                .executes(ctx -> {
                    HudModel.enabled = !HudModel.enabled;
                    ctx.getSource().sendFeedback(Text.literal("HUD Combo Rush: " + (HudModel.enabled ? "включён" : "выключен")));
                    return 1;
                })
                .then(ClientCommandManager.literal("results").executes(ctx -> {
                    boolean ok = HudModel.showLastResult();
                    ctx.getSource().sendFeedback(Text.literal(ok ? "Показываю итоги прошлого события." : "Итогов пока нет."));
                    return 1;
                }))
                .then(ClientCommandManager.literal("hints").executes(ctx -> {
                    Hints.resetAll();
                    ctx.getSource().sendFeedback(Text.literal("Подсказки покажутся заново при открытии экранов."));
                    return 1;
                }))
                .then(ClientCommandManager.literal("sound").executes(ctx -> {
                    HudModel.sound = !HudModel.sound;
                    ctx.getSource().sendFeedback(Text.literal("Звуки подсчёта: " + (HudModel.sound ? "включены" : "выключены")));
                    return 1;
                }))));
    }
}
