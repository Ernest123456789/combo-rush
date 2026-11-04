package com.derevna230.comborush.item;

import java.util.*;

/** Огнестрел, ножи и скины (генерируется tools/weapon_gen). Скин — отдельный предмет со своей текстурой и теми же геометрией/анимациями. */
public final class GunDefs {
    private GunDefs() {}
    /** id, название, урон, дробин, разброс, перезарядка выстрела (тики), дальность, перезарядка-анимация (тики), глушитель */
    public record Gun(String id, String name, float damage, int pellets, double spread, int cooldown, double range, int reloadTicks, boolean silenceable) {}
    /** id предмета, id базовой модели (ствол или claw), редкость */
    public record Skin(String id, String base, String rarity) {}

    public static final List<Gun> GUNS = List.of(
        new Gun("ak47", "AK-47", 6.0f, 1, 0.014, 9, 48, 50, false),
        new Gun("awp", "AWP", 38.0f, 1, 0.0, 60, 128, 80, false),
        new Gun("glock18", "Glock-18", 3.5f, 1, 0.016, 5, 32, 36, false),
        new Gun("galil", "Galil AR", 4.5f, 1, 0.016, 7, 44, 46, false),
        new Gun("deagle", "Desert Eagle", 12.0f, 1, 0.004, 22, 48, 44, false),
        new Gun("fiveseven", "Five-SeveN", 3.5f, 1, 0.012, 6, 36, 36, false),
        new Gun("p90", "P90", 3.0f, 1, 0.024, 3, 32, 56, false),
        new Gun("usp", "USP-S", 4.5f, 1, 0.008, 8, 40, 34, true),
        new Gun("revolver", "Револьвер", 14.0f, 1, 0.0, 30, 56, 60, false),
        new Gun("m4a4", "M4A4", 5.0f, 1, 0.01, 6, 48, 48, false),
        new Gun("m4a1s", "M4A1-S", 4.5f, 1, 0.008, 6, 52, 48, true));

    public static final List<Skin> SKINS = List.of(
        new Skin("ak47_jungle", "ak47", "uncommon"),
        new Skin("ak47_redline", "ak47", "rare"),
        new Skin("ak47_dragon", "ak47", "legendary"),
        new Skin("awp_dune", "awp", "uncommon"),
        new Skin("awp_arctic", "awp", "rare"),
        new Skin("awp_aurora", "awp", "legendary"),
        new Skin("deagle_rust", "deagle", "uncommon"),
        new Skin("deagle_hazard", "deagle", "rare"),
        new Skin("deagle_goldage", "deagle", "legendary"),
        new Skin("m4a4_urban", "m4a4", "uncommon"),
        new Skin("m4a4_carbon", "m4a4", "rare"),
        new Skin("m4a4_bloodmoon", "m4a4", "legendary"),
        new Skin("glock18_slate", "glock18", "uncommon"),
        new Skin("glock18_hazefade", "glock18", "epic"),
        new Skin("glock18_ember", "glock18", "rare"),
        new Skin("usp_khaki", "usp", "uncommon"),
        new Skin("usp_toxic", "usp", "epic"),
        new Skin("usp_midnight", "usp", "rare"),
        new Skin("galil_sandstorm", "galil", "uncommon"),
        new Skin("galil_crystal", "galil", "epic"),
        new Skin("galil_plum", "galil", "rare"),
        new Skin("p90_olivedrab", "p90", "uncommon"),
        new Skin("p90_cosmos", "p90", "epic"),
        new Skin("p90_sky", "p90", "rare"),
        new Skin("fiveseven_olivegrid", "fiveseven", "uncommon"),
        new Skin("fiveseven_cyber", "fiveseven", "epic"),
        new Skin("fiveseven_coral", "fiveseven", "rare"),
        new Skin("revolver_copper", "revolver", "uncommon"),
        new Skin("revolver_pirategold", "revolver", "epic"),
        new Skin("revolver_abyss", "revolver", "rare"),
        new Skin("m4a1s_graphite", "m4a1s", "uncommon"),
        new Skin("m4a1s_goldsteel", "m4a1s", "epic"),
        new Skin("m4a1s_mint", "m4a1s", "rare"),
        new Skin("claw_fade", "claw", "mythic"),
        new Skin("claw_doppler", "claw", "mythic"),
        new Skin("claw_tiger", "claw", "mythic"),
        new Skin("claw_crimsonweb", "claw", "mythic"),
        new Skin("claw_heat", "claw", "mythic"),
        new Skin("knife_t", "knife", "common"),
        new Skin("knife_ct", "knife", "common"));

    public static final String KNIFE = "claw";
    /** Базовые модели-ножи (claw — Нож-коготь, knife — стандартный нож Т/КТ) */
    public static boolean isKnifeBase(String base) { return base.equals("claw") || base.equals("knife"); }
    private static final Map<String, Gun> BY_ID = new HashMap<>();
    private static final Map<String, Skin> SKIN_BY_ID = new HashMap<>();
    static {
        for (Gun g : GUNS) BY_ID.put(g.id(), g);
        for (Skin s : SKINS) SKIN_BY_ID.put(s.id(), s);
    }

    public static Gun gun(String id) { return BY_ID.get(id); }
    /** id модели/анимаций: у скина — id ствола, у обычного предмета — он сам */
    public static String baseOf(String id) { Skin s = SKIN_BY_ID.get(id); return s == null ? id : s.base(); }
    public static boolean isSkin(String id) { return SKIN_BY_ID.containsKey(id); }
    public static String rarityName(String r) {
        return switch (r) {
            case "uncommon" -> "§9Армейское качество";
            case "rare" -> "§5Запрещённое";
            case "epic" -> "§dЗасекреченное";
            case "legendary" -> "§cТайное";
            case "mythic" -> "§6★ Редкий особый предмет";
            default -> "";
        };
    }
}
