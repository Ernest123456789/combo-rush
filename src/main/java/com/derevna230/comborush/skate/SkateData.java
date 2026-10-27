package com.derevna230.comborush.skate;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;

/** Прокачка скейтборда хранится прямо в предмете (custom_data): скорость, прыжок, планирование */
public final class SkateData {
    private SkateData() {}

    public static final String SPEED = "speed", JUMP = "jump", GLIDE = "glide";
    public static final String[] KEYS = {SPEED, JUMP, GLIDE};

    public static int max(String key) { return GLIDE.equals(key) ? 2 : 3; }

    /** Цена перехода на уровень level+1 (в эссенции) */
    public static int cost(String key, int level) {
        int[] c = GLIDE.equals(key) ? new int[]{20, 45} : new int[]{12, 25, 45};
        return level < c.length ? c[level] : -1;
    }

    public static int get(ItemStack st, String key) {
        NbtComponent c = st.get(DataComponentTypes.CUSTOM_DATA);
        return c == null ? 0 : c.copyNbt().getInt("cr_skate_" + key);
    }

    public static void set(ItemStack st, String key, int value) {
        NbtComponent.set(DataComponentTypes.CUSTOM_DATA, st, nbt -> nbt.putInt("cr_skate_" + key, value));
    }

    /** Скорость езды (как атрибут скорости у лошади) */
    public static float speed(int lvl) { return 0.26f + 0.065f * lvl; }
    /** Начальная скорость прыжка: ~1.5 блока на 0 уровне, ~3.3 блока на 3 */
    public static double jumpVelocity(int lvl) { return new double[]{0.46, 0.56, 0.64, 0.72}[Math.max(0, Math.min(3, lvl))]; }
    /** Самая быстрая скорость падения при планировании */
    public static double glideFall(int lvl) { return lvl >= 2 ? -0.045 : -0.09; }

    public static String levelText(String key, int lvl) {
        return switch (key) {
            case SPEED -> lvl == 0 ? "Обычная скорость" : "Скорость +" + (lvl * 25) + "%" + (lvl > 0 ? ", след полихрома" : "");
            case JUMP -> lvl == 0 ? "Прыжок на 1 блок" : "Прыжок на " + new String[]{"1", "2", "2.5", "3"}[lvl] + " блока, искры света";
            default -> lvl == 0 ? "Нет" : lvl == 1 ? "Планирование (держи пробел)" : "Долгое планирование";
        };
    }
}
