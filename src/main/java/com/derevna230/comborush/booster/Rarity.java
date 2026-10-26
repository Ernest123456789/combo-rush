package com.derevna230.comborush.booster;

import java.util.List;

public final class Rarity {
    private Rarity() {}
    public static final List<String> ORDER = List.of("common", "uncommon", "rare", "epic", "legendary");

    /** Вес выпадения в магазине */
    public static int weight(String r) {
        return switch (r) { case "uncommon" -> 28; case "rare" -> 14; case "epic" -> 6; case "legendary" -> 2; default -> 60; };
    }
    public static String label(String r) {
        return switch (r) { case "uncommon" -> "Необычный"; case "rare" -> "Редкий"; case "epic" -> "Эпический";
                            case "legendary" -> "Легендарный"; default -> "Обычный"; };
    }
    /** Цветовой код чата */
    public static String color(String r) {
        return switch (r) { case "uncommon" -> "§a"; case "rare" -> "§9"; case "epic" -> "§5"; case "legendary" -> "§6"; default -> "§f"; };
    }
}
