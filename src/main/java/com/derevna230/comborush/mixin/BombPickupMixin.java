package com.derevna230.comborush.mixin;

import com.derevna230.comborush.item.BombItem;
import com.derevna230.comborush.match.Match;
import com.derevna230.comborush.match.Team;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Выпавшую бомбу спецназ не подбирает (в матче) */
@Mixin(ItemEntity.class)
public abstract class BombPickupMixin {
    @Shadow public abstract ItemStack getStack();

    @Inject(method = "onPlayerCollision", at = @At("HEAD"), cancellable = true)
    private void comborush$noBombForCt(PlayerEntity player, CallbackInfo ci) {
        if (!player.getWorld().isClient && getStack().getItem() instanceof BombItem && Match.teamOf(player) == Team.CT) ci.cancel();
    }
}
