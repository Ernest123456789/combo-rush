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
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> HudModel.reset());
        EntityRendererRegistry.register(ModEntities.PET, PetRenderer::new);
        EntityRendererRegistry.register(ModEntities.DECOR, DecorRenderer::new);
        EntityRendererRegistry.register(ModEntities.SKATE, SkateRenderer::new);
        // скейт: клиент сам считает прыжок и планирование, ему нужно знать, зажат ли пробел
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client ->
            com.derevna230.comborush.skate.SkateboardEntity.CLIENT_JUMP = client.player != null && client.options.jumpKey.isPressed());
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
                .then(ClientCommandManager.literal("sound").executes(ctx -> {
                    HudModel.sound = !HudModel.sound;
                    ctx.getSource().sendFeedback(Text.literal("Звуки подсчёта: " + (HudModel.sound ? "включены" : "выключены")));
                    return 1;
                }))));
    }
}
