package com.derevna230.comborush.lobby;

import com.derevna230.comborush.item.GunDefs;

import java.util.ArrayList;
import java.util.List;

/** Слоты снаряжения «Противостояния» и какие предметы в какой слот подходят (общее для клиента и сервера) */
public final class LoadoutDefs {
    private LoadoutDefs() {}

    public static final String KNIFE_SLOT = "knife";
    /** Гранаты: у каждой свой слот (скины — по виду гранаты) */
    public static final List<String> GRENADES = List.of("grenade_he", "grenade_flash", "grenade_smoke", "grenade_molotov");

    /** Слоты по порядку: нож, затем стволы */
    public static List<String> slots() {
        List<String> l = new ArrayList<>();
        l.add(KNIFE_SLOT);
        for (GunDefs.Gun g : GunDefs.GUNS) l.add(g.id());
        for (String gr : GRENADES) l.add(gr);
        return l;
    }

    /** Предметы слота: первый — стандартный (доступен всегда), остальные нужны в инвентаре */
    public static List<String> candidates(String slot) {
        List<String> l = new ArrayList<>();
        if (slot.equals(KNIFE_SLOT)) {
            l.add("knife_t"); l.add("knife_ct"); l.add("claw"); l.add("bayonet"); l.add("skeleton");
            for (GunDefs.Skin s : GunDefs.SKINS) if (GunDefs.isSpecialKnife(s.base())) l.add(s.id());
        } else {
            l.add(slot);
            for (GunDefs.Skin s : GunDefs.SKINS) if (s.base().equals(slot)) l.add(s.id());
        }
        return l;
    }

    public static boolean isStandard(String slot, String item) {
        return slot.equals(KNIFE_SLOT) ? item.equals("knife_t") || item.equals("knife_ct") : item.equals(slot);
    }
}
