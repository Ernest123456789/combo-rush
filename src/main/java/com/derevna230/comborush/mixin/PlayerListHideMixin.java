package com.derevna230.comborush.mixin;

import com.derevna230.comborush.client.match.MatchClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** В матче по Tab — своя таблица статистики вместо списка игроков */
@Mixin(InGameHud.class)
public class PlayerListHideMixin {
    @Inject(method = "renderPlayerList", at = @At("HEAD"), cancellable = true)
    private void comborush$matchTable(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (MatchClient.inMatch()) ci.cancel();
    }
}
