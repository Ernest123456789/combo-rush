package com.derevna230.comborush.promo;

import java.util.ArrayList;
import java.util.List;

public class PromoDef {
    public static class Effect {
        /** shop_discount, score_multiplier, coin_bonus, reroll_discount, sell_bonus */
        public String type = "";
        public double value = 0;
    }

    public String id;                         // из имени файла: "new_year"
    public String name = "?";
    public String announce = "";
    public boolean enabled = true;

    /** Ежегодный период "MM-dd" (поддерживает переход через Новый год: 12-20 → 01-20) */
    public String start = "";
    public String end = "";
    /** Разовый период "yyyy-MM-dd" (например, день рождения сервера) */
    public String date_start = "";
    public String date_end = "";
    /** Дни недели: MONDAY … SUNDAY. Если заданы вместе с датами — нужны оба условия */
    public List<String> weekdays = new ArrayList<>();

    public List<Effect> effects = new ArrayList<>();
}
