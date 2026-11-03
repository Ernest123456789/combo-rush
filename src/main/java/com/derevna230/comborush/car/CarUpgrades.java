package com.derevna230.comborush.car;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

/**
 * Прокачка машины за эссенцию: пять веток по три уровня. Уровни хранятся в предмете (custom_data)
 * и в самой машине (одно число: пять «цифр» по основанию 4), так что улучшения не пропадают,
 * когда машину убирают в инвентарь и ставят заново.
 */
public final class CarUpgrades {
    private CarUpgrades() {}

    public static final String ENGINE = "engine", TIRES = "tires", SUSPENSION = "suspension", NITRO = "nitro", ARMOR = "armor";
    public static final String[] KEYS = {ENGINE, TIRES, SUSPENSION, NITRO, ARMOR};
    public static final int MAX = 3;

    public static String title(String key) {
        return switch (key) {
            case ENGINE -> "Двигатель";
            case TIRES -> "Шины";
            case SUSPENSION -> "Подвеска";
            case NITRO -> "Закись азота";
            default -> "Таран-бампер";
        };
    }

    public static Item icon(String key) {
        return switch (key) {
            case ENGINE -> Items.PISTON;
            case TIRES -> Items.COAL_BLOCK;
            case SUSPENSION -> Items.SLIME_BLOCK;
            case NITRO -> Items.FIRE_CHARGE;
            default -> Items.IRON_BARS;
        };
    }

    /** Цена перехода с уровня level на level+1 в эссенции; −1, если уровень максимальный */
    public static int cost(String key, int level) {
        int[] c = switch (key) {
            case ENGINE -> new int[]{15, 30, 55};
            case TIRES -> new int[]{10, 22, 40};
            case SUSPENSION -> new int[]{12, 26, 45};
            case NITRO -> new int[]{14, 28, 50};
            default -> new int[]{16, 32, 60};
        };
        return level >= 0 && level < c.length ? c[level] : -1;
    }

    public static String levelText(String key, int lvl) {
        return switch (key) {
            case ENGINE -> new String[]{"Заводской мотор", "Мощность +12%, макс. скорость +8%",
                "Мощность +24%, макс. скорость +16%", "Мощность +36%, макс. скорость +24%, рёв форсажа"}[lvl];
            case TIRES -> new String[]{"Обычная резина", "Сцепление +15%, руль отзывчивее",
                "Сцепление +30%, руль ещё отзывчивее", "Спорт-резина: сцепление +45%, дрифт-очки ×2"}[lvl];
            case SUSPENSION -> new String[]{"Мягкая подвеска, берёт уступ в 1 блок", "Приземление мягче, кузов меньше качает",
                "Вездеход: берёт уступы до 1,5 блока", "Раллийная: берёт уступы в 2 блока, почти не теряет скорость на прыжках"}[lvl];
            case NITRO -> new String[]{"Баллон на 3 секунды (Ctrl)", "Баллон на 5 секунд, быстрее наполняется",
                "Баллон на 7 секунд, мощнее рывок", "Баллон на 9 секунд, максимальный рывок и огненный шлейф"}[lvl];
            default -> new String[]{"Без усилений", "Таран: удар по врагу сильнее на 40%",
                "Таран: сильнее на 80% и отбрасывает", "Таран: сильнее на 120%, отбрасывает и поджигает"}[lvl];
        };
    }

    // ---------------------------------------------------------------- упаковка уровней в число
    private static int slot(String key) {
        for (int i = 0; i < KEYS.length; i++) if (KEYS[i].equals(key)) return i;
        return 0;
    }

    public static int get(int packed, String key) { return (packed >> (slot(key) * 2)) & 3; }

    public static int set(int packed, String key, int level) {
        int sh = slot(key) * 2;
        return (packed & ~(3 << sh)) | (Math.max(0, Math.min(MAX, level)) << sh);
    }

    // ---------------------------------------------------------------- предмет
    public static int read(ItemStack st) {
        NbtComponent c = st.get(DataComponentTypes.CUSTOM_DATA);
        if (c == null) return 0;
        var nbt = c.copyNbt();
        int packed = 0;
        for (String k : KEYS) packed = set(packed, k, nbt.getInt("cr_car_" + k));
        return packed;
    }

    public static void write(ItemStack st, int packed) {
        NbtComponent.set(DataComponentTypes.CUSTOM_DATA, st, nbt -> {
            for (String k : KEYS) nbt.putInt("cr_car_" + k, get(packed, k));
        });
    }

    public static int total(int packed) {
        int n = 0;
        for (String k : KEYS) n += get(packed, k);
        return n;
    }

    // ---------------------------------------------------------------- числа для физики
    public static double powerMult(int packed) { return 1.0 + 0.12 * get(packed, ENGINE); }
    public static double topSpeedMult(int packed) { return 1.0 + 0.08 * get(packed, ENGINE); }
    public static double gripMult(int packed) { return 1.0 + 0.15 * get(packed, TIRES); }
    /** Высота ступеньки, на которую заезжает машина */
    public static double stepHeight(int packed) { return new double[]{1.0, 1.0, 1.5, 2.05}[get(packed, SUSPENSION)]; }
    /** Ёмкость баллона нитро в тиках */
    public static int nitroCapacity(int packed) { return 60 + 40 * get(packed, NITRO); }
    public static double nitroBoost(int packed) { return 1.7 + 0.15 * get(packed, NITRO); }
    public static double rammingMult(int packed) { return 1.0 + 0.4 * get(packed, ARMOR); }
}
