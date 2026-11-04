package com.derevna230.comborush.item;

import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;

/** Клавиши оружия (R — перезарядка, V — осмотр, G — глушитель): сервер получает gun_reload / gun_inspect / gun_silencer */
public final class WeaponKeys {
    private WeaponKeys() {}

    public static void handle(ServerPlayerEntity p, String act) {
        ItemStack s = p.getMainHandStack();
        if (s.getItem() instanceof CRGunItem g) g.keyAction(p, s, act);
        else if (s.getItem() instanceof CRKnifeItem k) k.keyAction(p, s, act);
    }
}
