package com.derevna230.comborush.mixin;

import com.derevna230.comborush.client.WeaponClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Оптический прицел: сужает поле зрения, пока игрок смотрит в прицел */
@Mixin(GameRenderer.class)
public class GameRendererMixin {
    @Inject(method = "getFov(Lnet/minecraft/client/render/Camera;FZ)D", at = @At("RETURN"), cancellable = true)
    private void comborush$scope(Camera camera, float tickDelta, boolean changingFov, CallbackInfoReturnable<Double> cir) {
        double m = WeaponClient.fovMult();
        if (changingFov && m != 1.0)   // рука с оружием рисуется с changingFov=false — она не должна «увеличиваться» вместе с прицелом
            cir.setReturnValue(cir.getReturnValue() * m);
    }
}
