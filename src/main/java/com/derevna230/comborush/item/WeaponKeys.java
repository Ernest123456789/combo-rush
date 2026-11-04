package com.derevna230.comborush.item;

import com.derevna230.comborush.hud.ShopActionPayload;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Действия оружия с клиента: gun_fire (a = 1, если игрок в прицеле), gun_reload, gun_silencer.
 * Осмотр (V) и анимации клиент проигрывает у себя, серверу они не нужны.
 */
public final class WeaponKeys {
    private WeaponKeys() {}

    public static void handle(ServerPlayerEntity p, ShopActionPayload a) {
        ItemStack s = p.getMainHandStack();
        if (s.getItem() instanceof CRGunItem g) g.keyAction(p, s, a.action(), a.a() == 1);
    }
}
