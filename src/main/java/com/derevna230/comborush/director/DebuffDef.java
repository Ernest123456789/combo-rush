package com.derevna230.comborush.director;

import java.util.HashMap;
import java.util.Map;

/** Дебаф босс-блайнда (каждое 5-е событие игрока). Файлы: data/<modid>/debuffs/*.json */
public class DebuffDef {
    public String id;
    public String name = "?";
    public String description = "";
    public int weight = 10;

    public boolean silence = false;          // первый усилитель слева отключён
    public double hunger_target = 0;         // нельзя есть, пока очков меньше доли (0..1) бронзового порога
    public boolean fragile = false;          // инструменты изнашиваются вдвое быстрее при добыче
    public boolean blind = false;            // HUD со счётом скрыт
    public double target_mult = 1.0;         // порог бронзы/серебра/… выше во столько раз
    public Map<String, Double> source_mult = new HashMap<>();   // множители фишек по источникам
    public double combo_window = 0;          // секунды (может быть отрицательным)
    public String status_effect = "";        // например "minecraft:slowness"
    public int amplifier = 0;

    public double coin_bonus = 0.5;          // награда: + к монетам, если событие пройдено
    public boolean reward_consumable = true; // награда: случайный расходник
}
