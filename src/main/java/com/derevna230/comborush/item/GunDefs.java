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
        new Gun("m4a1s",     "M4A1-S",      5.5f, 1, 0.0025, 2.0,   68,  54, true,  25, true,  false, 0.75f, 0.022),
        new Gun("famas",     "FAMAS",       5.0f, 1, 0.0035, 1.8,   58,  56, false, 25, true,  false, 0.85f, 0.026),
        new Gun("mp9",       "MP9",         4.3f, 1, 0.0055, 1.4,   36,  46, false, 30, true,  false, 0.60f, 0.030),
        new Gun("mp7",       "MP7",         4.8f, 1, 0.0050, 1.6,   44,  52, false, 30, true,  false, 0.70f, 0.030),
        new Gun("scar20",    "SCAR-20",    11.0f, 1, 0.0010, 5.0,  140, 66, false, 20, false, true,  1.6f,  0.0),
        // дробовики: урон — за одну дробинку (урон × pellets при попадании всей картечью), разброс — конус картечи
        new Gun("nova",      "Nova",        2.1f, 9, 0.045,  18.0,  26,  72, false,  8, false, false, 3.0f,  0.0),
        new Gun("xm1014",    "XM1014",      1.7f, 6, 0.050,   7.0,  24,  68, false,  7, false, false, 2.0f,  0.0),
        new Gun("mag7",      "MAG-7",       2.8f, 5, 0.035,  17.0,  28,  58, false,  5, false, false, 2.8f,  0.0),
        new Gun("sawedoff",  "Обрез",       2.3f, 8, 0.075,  17.0,  18,  76, false,  7, false, false, 3.2f,  0.0),
        new Gun("mac10",     "MAC-10",      4.6f, 1, 0.0070,  1.5,  34,  44, false, 30, true,  false, 0.70f, 0.034),
        // Тайзер: один заряд, бьёт вплотную (≈4,5 блока) и сильно, затем зарядка
        new Gun("taser",     "Тайзер",     20.0f, 1, 0.0,    60.0, 4.5,  50, false,  1, false, false, 3.0f,  0.0));

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
new Skin("famas_tundra", "famas", "uncommon"),
        new Skin("famas_eclipse", "famas", "rare"),
        new Skin("famas_sakura", "famas", "epic"),
        new Skin("mp9_lime", "mp9", "uncommon"),
        new Skin("mp9_lava", "mp9", "rare"),
        new Skin("mp9_voidglass", "mp9", "epic"),
        new Skin("mp7_ashfall", "mp7", "uncommon"),
        new Skin("mp7_ocean", "mp7", "rare"),
        new Skin("mp7_royal", "mp7", "epic"),
        new Skin("scar20_desert", "scar20", "uncommon"),
        new Skin("scar20_cyrex", "scar20", "rare"),
        new Skin("scar20_emerald", "scar20", "epic"),
        new Skin("mac10_tarnish", "mac10", "uncommon"),
        new Skin("mac10_neonrider", "mac10", "rare"),
        new Skin("mac10_vapor", "mac10", "epic"),
        new Skin("nova_forest", "nova", "uncommon"),
        new Skin("nova_antique", "nova", "rare"),
        new Skin("nova_koi", "nova", "epic"),
        new Skin("xm1014_scrapyard", "xm1014", "uncommon"),
        new Skin("xm1014_aqua", "xm1014", "rare"),
        new Skin("xm1014_magma", "xm1014", "epic"),
        new Skin("mag7_caution", "mag7", "uncommon"),
        new Skin("mag7_cobalt", "mag7", "rare"),
        new Skin("mag7_praetorian", "mag7", "epic"),
        new Skin("sawedoff_walnut", "sawedoff", "uncommon"),
        new Skin("sawedoff_copper", "sawedoff", "rare"),
        new Skin("sawedoff_moonshine", "sawedoff", "epic"),
        new Skin("claw_fade", "claw", "mythic"),
        new Skin("claw_doppler", "claw", "mythic"),
        new Skin("claw_tiger", "claw", "mythic"),
        new Skin("claw_crimsonweb", "claw", "mythic"),
        new Skin("claw_heat", "claw", "mythic"),
        new Skin("taser_hazard", "taser", "uncommon"),
        new Skin("taser_storm", "taser", "rare"),
        new Skin("taser_plasma", "taser", "epic"),
        new Skin("grenade_he_woodland", "grenade_he", "uncommon"),
        new Skin("grenade_he_ember", "grenade_he", "rare"),
        new Skin("grenade_he_gilded", "grenade_he", "epic"),
        new Skin("grenade_flash_arctic", "grenade_flash", "uncommon"),
        new Skin("grenade_flash_neon", "grenade_flash", "rare"),
        new Skin("grenade_flash_prism", "grenade_flash", "epic"),
        new Skin("grenade_smoke_urban", "grenade_smoke", "uncommon"),
        new Skin("grenade_smoke_toxic", "grenade_smoke", "rare"),
        new Skin("grenade_smoke_nebula", "grenade_smoke", "epic"),
        new Skin("grenade_molotov_hazard", "grenade_molotov", "uncommon"),
        new Skin("grenade_molotov_inferno", "grenade_molotov", "rare"),
        new Skin("grenade_molotov_frostfire", "grenade_molotov", "epic"),
        new Skin("bayonet_fade", "bayonet", "mythic"),
        new Skin("bayonet_doppler", "bayonet", "mythic"),
        new Skin("bayonet_tiger", "bayonet", "mythic"),
        new Skin("bayonet_crimsonweb", "bayonet", "mythic"),
        new Skin("bayonet_heat", "bayonet", "mythic"),
        new Skin("skeleton_fade", "skeleton", "mythic"),
        new Skin("skeleton_doppler", "skeleton", "mythic"),
        new Skin("skeleton_tiger", "skeleton", "mythic"),
        new Skin("skeleton_crimsonweb", "skeleton", "mythic"),
        new Skin("skeleton_heat", "skeleton", "mythic"),
        new Skin("knife_t", "knife", "common"),
        new Skin("knife_ct", "knife", "common"));

    /** Базовые id гранат (модель и анимации общие у скинов) */
    public static boolean isGrenadeBase(String base) { return base.startsWith("grenade_"); }
    /** Id всех скинов на гранаты — индекс (с 1) уходит в летящую гранату, чтобы клиент нарисовал нужную текстуру */
    public static List<String> grenadeSkinIds() {
        List<String> l = new ArrayList<>();
        for (Skin s : SKINS) if (isGrenadeBase(s.base())) l.add(s.id());
        return l;
    }

    public static final String KNIFE = "claw";
    public static final String BAYONET = "bayonet";
    public static final String SKELETON = "skeleton";
    /** Базовые модели-ножи (claw — Нож-коготь, bayonet — Штык, knife — стандартный нож Т/КТ) */
    public static boolean isKnifeBase(String base) { return base.equals("claw") || base.equals("knife") || base.equals("bayonet") || base.equals("skeleton"); }
    /** Коллекционные ножи (не стандартные Т/КТ): коготь и штык */
    public static boolean isSpecialKnife(String base) { return base.equals("claw") || base.equals("bayonet") || base.equals("skeleton"); }
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
