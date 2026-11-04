package com.derevna230.comborush.item;

import java.util.*;

/** Огнестрел, ножи и скины (генерируется tools/weapon_gen). Скин — отдельный предмет со своей текстурой и теми же геометрией/анимациями. */
public final class GunDefs {
    private GunDefs() {}
    /**
     * Ствол. damage — урон за пулю (в ванильных HP, по CS: урон CS ÷ 6); cooldown — тиков между выстрелами (дробное: у P90 1,4);
     * spread — разброс стоя на месте; bloom — на сколько расходится прицел при длинной очереди; recoil — подброс камеры за выстрел (градусы);
     * mag — патронов в магазине (запас бесконечный); auto — зажатая ЛКМ стреляет очередью; scope — есть оптический прицел (ПКМ).
     */
    public record Gun(String id, String name, float damage, int pellets, double spread, double cooldown, double range, int reloadTicks,
                      boolean silenceable, int mag, boolean auto, boolean scope, float recoil, double bloom) {}
    /** id предмета, id базовой модели (ствол или claw), редкость */
    public record Skin(String id, String base, String rarity) {}

    public static final List<Gun> GUNS = List.of(
        //        id          имя           урон  дробь разброс  такты  дальн. перезар. глуш.   маг.  авто   прицел отдача bloom
        new Gun("ak47",      "AK-47",       6.0f, 1, 0.0035, 2.0,   64,  54, false, 30, true,  false, 1.15f, 0.030),
        new Gun("awp",       "AWP",        20.0f, 1, 0.0,    29.0, 160,  80, false,  5, false, true,  3.2f,  0.0),
        new Gun("glock18",   "Glock-18",    5.0f, 1, 0.0045, 3.0,   40,  44, false, 20, false, false, 0.8f,  0.020),
        new Gun("galil",     "Galil AR",    5.0f, 1, 0.0040, 2.0,   60,  56, false, 35, true,  false, 0.95f, 0.028),
        new Gun("deagle",    "Desert Eagle",10.5f,1, 0.0020, 5.0,   72,  50, false,  7, false, false, 2.6f,  0.035),
        new Gun("fiveseven", "Five-SeveN",  5.3f, 1, 0.0040, 3.0,   44,  44, false, 20, false, false, 0.8f,  0.020),
        new Gun("p90",       "P90",         4.3f, 1, 0.0050, 1.4,   40,  62, false, 50, true,  false, 0.65f, 0.032),
        new Gun("usp",       "USP-S",       5.8f, 1, 0.0025, 3.0,   52,  40, true,  12, false, false, 0.85f, 0.018),
        new Gun("revolver",  "Револьвер",  14.0f, 1, 0.0010, 9.0,   80,  66, false,  8, false, false, 2.4f,  0.025),
        new Gun("m4a4",      "M4A4",        5.5f, 1, 0.0030, 2.0,   64,  54, false, 30, true,  false, 0.95f, 0.026),
        new Gun("m4a1s",     "M4A1-S",      5.5f, 1, 0.0025, 2.0,   68,  54, true,  25, true,  false, 0.75f, 0.022));

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
