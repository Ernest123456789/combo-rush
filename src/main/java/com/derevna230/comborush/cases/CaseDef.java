package com.derevna230.comborush.cases;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Кейс. Файлы: data/<modid>/cases/<id>.json, id совпадает с предметом combo-rush:case_<id> */
public class CaseDef {
    public static class Drop {
        public String set = "";     // id комплекта, шляпы, оружия или питомца (pet_<id>); у комплекта выпадает случайная часть
        public int weight = 1;
    }

    /** Порядок редкостей в подсказках: от лучшей к обычной */
    public static final List<String> RAR_ORDER = List.of("mythic", "legendary", "epic", "rare", "uncommon", "common");

    public String id;
    public String name = "?";
    public String description = "";
    public String rarity = "common";
    public int price = 10;          // в эссенции
    public int order = 0;           // порядок в магазине
    public List<Drop> drops = new ArrayList<>();

    public int totalWeight() { return Math.max(1, drops.stream().mapToInt(d -> Math.max(0, d.weight)).sum()); }
    public double chance(Drop d) { return Math.max(0, d.weight) / (double) totalWeight(); }

    public List<Drop> dropsSorted() {
        List<Drop> l = new ArrayList<>(drops);
        l.sort(Comparator.comparingInt((Drop d) -> d.weight).reversed());
        return l;
    }

    public static String pctText(double c) {
        double p = c * 100;
        return (p < 1 ? String.format(Locale.ROOT, "%.1f", p) : String.valueOf(Math.round(p))) + "%";
    }
}
