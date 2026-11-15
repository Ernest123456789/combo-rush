package com.derevna230.comborush.match;

import com.derevna230.comborush.item.GunDefs;

import java.util.ArrayList;
import java.util.List;

/**
 * Магазин соревновательного режима (общий для клиента и сервера): цены, категории, сторона, слот в хотбаре.
 * Слоты: 0 — основное оружие, 1 — пистолет, 2 — нож, 3–6 — гранаты, 7 — тайзер, 8 — бомба / набор сапёра.
 */
public final class BuyDefs {
    private BuyDefs() {}

    public static final int START_MONEY = 800, MAX_MONEY = 16000;
    public static final int SLOT_PRIMARY = 0, SLOT_PISTOL = 1, SLOT_KNIFE = 2, SLOT_GREN0 = 3, SLOT_GREN1 = 6, SLOT_TASER = 7, SLOT_GEAR = 8;

    /** side: null — обе стороны */
    public record Entry(String id, String name, int price, int cat, Team side, int slot) {}

    public static final List<String> CATS = List.of("ПИСТОЛЕТЫ", "ПП", "ДРОБОВИКИ", "ВИНТОВКИ", "СНАЙПЕРСКИЕ", "ГРАНАТЫ", "СНАРЯЖЕНИЕ");

    public static final List<Entry> ALL = new ArrayList<>();
    private static void g(String id, int price, int cat, Team side, int slot) {
        GunDefs.Gun gun = GunDefs.gun(id);
        ALL.add(new Entry(id, gun == null ? id : gun.name(), price, cat, side, slot));
    }
    private static void e(String id, String name, int price, int cat, Team side, int slot) { ALL.add(new Entry(id, name, price, cat, side, slot)); }

    static {
        g("glock18", 200, 0, Team.T, SLOT_PISTOL);
        g("usp", 200, 0, Team.CT, SLOT_PISTOL);
        g("fiveseven", 500, 0, Team.CT, SLOT_PISTOL);
        g("revolver", 600, 0, null, SLOT_PISTOL);
        g("deagle", 700, 0, null, SLOT_PISTOL);

        g("mac10", 1050, 1, Team.T, SLOT_PRIMARY);
        g("mp9", 1250, 1, Team.CT, SLOT_PRIMARY);
        g("mp7", 1500, 1, null, SLOT_PRIMARY);
        g("p90", 2350, 1, null, SLOT_PRIMARY);

        g("nova", 1050, 2, null, SLOT_PRIMARY);
        g("sawedoff", 1100, 2, Team.T, SLOT_PRIMARY);
        g("mag7", 1300, 2, Team.CT, SLOT_PRIMARY);
        g("xm1014", 2000, 2, null, SLOT_PRIMARY);

        g("galil", 1800, 3, Team.T, SLOT_PRIMARY);
        g("famas", 2050, 3, Team.CT, SLOT_PRIMARY);
        g("ak47", 2700, 3, Team.T, SLOT_PRIMARY);
        g("m4a1s", 2900, 3, Team.CT, SLOT_PRIMARY);
        g("m4a4", 3100, 3, Team.CT, SLOT_PRIMARY);

        g("scar20", 5000, 4, Team.CT, SLOT_PRIMARY);
        g("g3sg1", 5000, 4, Team.T, SLOT_PRIMARY);
        g("awp", 4750, 4, null, SLOT_PRIMARY);

        e("grenade_flash", "Светошумовая", 200, 5, null, SLOT_GREN0);
        e("grenade_smoke", "Дымовая", 300, 5, null, SLOT_GREN0);
        e("grenade_he", "Осколочная", 300, 5, null, SLOT_GREN0);
        e("grenade_molotov", "Коктейль Молотова", 400, 5, null, SLOT_GREN0);

        e("taser", "Тайзер Zeus x27", 200, 6, null, SLOT_TASER);
        e("defuse_kit", "Набор сапёра", 400, 6, Team.CT, SLOT_GEAR);
    }

    public static Entry get(String id) {
        for (Entry x : ALL) if (x.id().equals(id)) return x;
        return null;
    }

    public static boolean allowed(Entry x, Team side) { return x.side() == null || x.side() == side; }

    /** Награда за убийство по базовому id оружия в руке убийцы (как в CS) */
    public static int killReward(String base) {
        if (base == null) return 300;
        if (GunDefs.isKnifeBase(base)) return 1500;
        return switch (base) {
            case "awp" -> 100;
            case "taser" -> 0;
            case "nova", "xm1014", "mag7", "sawedoff" -> 900;
            case "mac10", "mp9", "mp7" -> 600;
            case "p90" -> 300;
            default -> 300;
        };
    }
}
