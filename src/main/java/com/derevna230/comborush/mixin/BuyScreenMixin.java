package com.derevna230.comborush.mixin;

import com.derevna230.comborush.client.match.BuyScreen;
import com.derevna230.comborush.client.match.MatchClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** В матче E открывает меню закупки вместо инвентаря */
@Mixin(MinecraftClient.class)
public class BuyScreenMixin {
    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void comborush$buyMenu(Screen screen, CallbackInfo ci) {
        if (screen instanceof InventoryScreen && MatchClient.inMatch()) {
            ci.cancel();
            ((MinecraftClient) (Object) this).setScreen(new BuyScreen());
        }
    }
}
