package com.derevna230.comborush.item;

import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;

/** ЛКМ стволом «Арсенала» только стреляет: не бьёт и не ломает блоки (стрельбу ведёт клиент пакетом gun_fire) */
public final class WeaponEvents {
    private WeaponEvents() {}

    private static boolean isShooter(ItemStack st) {
        return (st.getItem() instanceof CRGunItem g && g.modern()) || st.getItem() instanceof CRGrenadeItem;
    }

    public static void init() {
        CRGrenadeItem.init();
        // StatTrak™: убийство с оружием в руке прибавляет счётчик (у ствола и у ножа)
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (!(source.getAttacker() instanceof ServerPlayerEntity p) || entity == p) return;
            ItemStack st = p.getMainHandStack();
            if ((st.getItem() instanceof CRGunItem || st.getItem() instanceof CRKnifeItem) && SkinData.statTrak(st)) SkinData.addKill(st);
        });
        AttackBlockCallback.EVENT.register((player, world, hand, pos, dir) ->
            isShooter(player.getMainHandStack()) ? ActionResult.FAIL : ActionResult.PASS);
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) ->
            isShooter(player.getMainHandStack()) ? ActionResult.FAIL : ActionResult.PASS);
    }
}
