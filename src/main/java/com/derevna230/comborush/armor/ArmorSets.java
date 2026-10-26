package com.derevna230.comborush.armor;

import com.derevna230.comborush.item.ModItems;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Item;
import net.minecraft.server.network.ServerPlayerEntity;
import com.derevna230.comborush.pet.PetManager;

import java.util.LinkedHashMap;
import java.util.Map;

/** Сколько частей каждого комплекта надето; оружие в руке и призванный питомец считаются «полным комплектом» */
public final class ArmorSets {
    private ArmorSets() {}
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    public static Map<String, Integer> worn(LivingEntity e) {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (EquipmentSlot s : SLOTS) {
            Item it = e.getEquippedStack(s).getItem();
            String set = ModItems.ITEM_SET.get(it);
            if (set != null) out.merge(set, 1, Integer::sum);
        }
        String main = ModItems.ITEM_SET.get(e.getMainHandStack().getItem());
        if (main != null && ModItems.isMainHand(main)) out.put(main, 1);
        String off = ModItems.ITEM_SET.get(e.getOffHandStack().getItem());
        if (off != null && ModItems.kindOf(off).equals("shield")) out.put(off, 1);
        if (e instanceof ServerPlayerEntity sp) {
            String pet = PetManager.activeSet(sp);
            if (pet != null) out.put(pet, 1);
        }
        return out;
    }

    public static boolean full(String setId, int count) { return count >= Math.max(1, ModItems.piecesIn(setId)); }
}
