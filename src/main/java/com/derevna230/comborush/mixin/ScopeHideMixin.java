package com.derevna230.comborush.mixin;

import com.derevna230.comborush.client.WeaponClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** В оптическом прицеле (AWP, SCAR-20) ствол и руки от первого лица не рисуются — видно только сетку прицела */
@Mixin(HeldItemRenderer.class)
public class ScopeHideMixin {
    @Inject(method = "renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider$Immediate;Lnet/minecraft/client/network/ClientPlayerEntity;I)V",
            at = @At("HEAD"), cancellable = true)
    private void comborush$hideInScope(float tickDelta, MatrixStack matrices, VertexConsumerProvider.Immediate vertexConsumers,
                                       ClientPlayerEntity player, int light, CallbackInfo ci) {
        if (WeaponClient.scoped()) ci.cancel();
    }
}
