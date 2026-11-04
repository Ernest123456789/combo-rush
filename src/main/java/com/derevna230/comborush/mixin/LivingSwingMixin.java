package com.derevna230.comborush.mixin;

import com.derevna230.comborush.item.CRGunItem;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Ствол «Арсенала» не машет рукой при клике: за выстрел отвечает его собственная анимация (как во вьюмодели CS) */
@Mixin(LivingEntity.class)
public class LivingSwingMixin {
    @Inject(method = "swingHand(Lnet/minecraft/util/Hand;Z)V", at = @At("HEAD"), cancellable = true)
    private void comborush$noGunSwing(Hand hand, boolean fromServerPlayer, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self.getWorld().isClient && self.getStackInHand(hand).getItem() instanceof CRGunItem g && g.modern()) ci.cancel();
    }
}
