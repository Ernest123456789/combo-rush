package com.derevna230.comborush.mixin;

import com.derevna230.comborush.item.CRGunItem;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Игрок со стволом «Арсенала» в руке держит его двумя руками, как арбалет (поза от третьего лица и в меню) */
@Mixin(PlayerEntityRenderer.class)
public class PlayerArmPoseMixin {
    @Inject(method = "getArmPose(Lnet/minecraft/client/network/AbstractClientPlayerEntity;Lnet/minecraft/util/Hand;)Lnet/minecraft/client/render/entity/model/BipedEntityModel$ArmPose;",
            at = @At("HEAD"), cancellable = true)
    private static void comborush$gunPose(AbstractClientPlayerEntity player, Hand hand, CallbackInfoReturnable<BipedEntityModel.ArmPose> cir) {
        if (hand == Hand.MAIN_HAND && player.getStackInHand(hand).getItem() instanceof CRGunItem g && g.modern())
            cir.setReturnValue(BipedEntityModel.ArmPose.CROSSBOW_HOLD);
    }
}
