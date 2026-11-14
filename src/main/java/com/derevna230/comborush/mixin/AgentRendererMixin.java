package com.derevna230.comborush.mixin;

import com.derevna230.comborush.client.agent.AgentClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** В режиме «Противостояние» игрока рисует рендер бойца (и в мире, и в меню, и руки от первого лица) */
@Mixin(EntityRenderDispatcher.class)
public class AgentRendererMixin {
    @SuppressWarnings({"unchecked", "rawtypes"})
    @Inject(method = "getRenderer", at = @At("HEAD"), cancellable = true)
    private <T extends Entity> void comborush$agent(T entity, CallbackInfoReturnable<EntityRenderer<? super T>> cir) {
        if (entity instanceof AbstractClientPlayerEntity p) {
            EntityRenderer<?> r = AgentClient.rendererFor(p);
            if (r != null) cir.setReturnValue((EntityRenderer) r);
        }
    }
}
