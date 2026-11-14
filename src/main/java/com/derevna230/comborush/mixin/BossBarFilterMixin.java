package com.derevna230.comborush.mixin;

import com.derevna230.comborush.universe.Universes;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.BossBarHud;
import net.minecraft.client.gui.hud.ClientBossBar;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** В «Противостоянии» сверху только полоса матча: полосы событий, акций, разломов и боссов прячем на время отрисовки */
@Mixin(BossBarHud.class)
public class BossBarFilterMixin {
    @Shadow @Final Map<UUID, ClientBossBar> bossBars;
    private final Map<UUID, ClientBossBar> comborush$hidden = new LinkedHashMap<>();

    @Inject(method = "render", at = @At("HEAD"))
    private void comborush$hide(DrawContext context, CallbackInfo ci) {
        comborush$hidden.clear();
        if (!Universes.standoffWorld(MinecraftClient.getInstance().world)) return;
        bossBars.entrySet().removeIf(e -> {
            if (e.getValue().getName().getString().contains("Раунд ")) return false;
            comborush$hidden.put(e.getKey(), e.getValue());
            return true;
        });
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void comborush$restore(DrawContext context, CallbackInfo ci) {
        if (comborush$hidden.isEmpty()) return;
        Map<UUID, ClientBossBar> all = new LinkedHashMap<>(comborush$hidden);
        all.putAll(bossBars);
        bossBars.clear();
        bossBars.putAll(all);
        comborush$hidden.clear();
    }
}
