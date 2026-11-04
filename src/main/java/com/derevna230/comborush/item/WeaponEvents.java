package com.derevna230.comborush.item;

import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.util.ActionResult;

/** ЛКМ стволом «Арсенала» только стреляет: не бьёт и не ломает блоки (стрельбу ведёт клиент пакетом gun_fire) */
public final class WeaponEvents {
    private WeaponEvents() {}

    public static void init() {
        AttackBlockCallback.EVENT.register((player, world, hand, pos, dir) ->
            player.getMainHandStack().getItem() instanceof CRGunItem g && g.modern() ? ActionResult.FAIL : ActionResult.PASS);
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) ->
            player.getMainHandStack().getItem() instanceof CRGunItem g && g.modern() ? ActionResult.FAIL : ActionResult.PASS);
    }
}
