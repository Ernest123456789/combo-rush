package com.derevna230.comborush.director;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Сценарий 5-минутного события. Файлы: data/<modid>/director/*.json */
public class DirectorEventDef {
    public static class Spawns {
        public int interval = 600;       // тиков между волнами
        public int per_player = 3;       // мобов на игрока за волну
        public int cap = 12;             // максимум мобов события рядом с игроком
        public int radius_min = 10;
        public int radius_max = 18;
        public List<String> entities = new ArrayList<>();
    }

    public static class Mark {
        public boolean enabled = true;
        public double score_mult = 1.5;        // множитель очков игрока с Меткой
        public String boss_entity = "minecraft:zombie";
        public String boss_name = "Охотник на {player}";
        public double boss_health = 60;
        public double boss_health_per_event = 3;
        public double bonus = 300;             // бонусные очки тому, кто убьёт охотника
        public double bonus_per_event = 40;
    }

    public String id;                          // из имени файла
    public String name = "?";
    public String description = "";
    public int weight = 10;                    // шанс выпадения
    public int min_event = 1;                  // не выпадает, пока средний номер события игроков меньше

    /** Множители фишек по источникам (KILL/MINE/.../ALL) */
    public Map<String, Double> source_mult = new HashMap<>();
    public double mult_add = 0;                // + к множителю очков для всех
    public double combo_step = 0;              // + к приросту серии
    public double combo_window = 0;            // + секунд к окну серии
    public double coin_bonus = 0;              // + к монетам за событие

    public String weather = "";                // "", "clear", "rain", "thunder"
    public Spawns spawns = null;
    public Mark mark = null;
}
