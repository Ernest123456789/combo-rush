package com.derevna230.comborush.score;

import java.util.HashMap;
import java.util.Map;

public class EventScore {
    public double chips = 0;       // статистика
    public double score = 0;       // сумма (фишки × множитель) по каждому действию
    public double combo = 1.0;
    public double multAdd = 0, multTimes = 1.0;
    public long lastActionTick = -1000;
    public final Map<String, Integer> counters = new HashMap<>();
    /** Очки и число действий по источникам (ключи: имена ScoreSource, "BONUS", "SHARE") */
    public final Map<String, Double> bySource = new HashMap<>();
    public final Map<String, Integer> countBySource = new HashMap<>();

    public double mult() { return (combo + multAdd) * multTimes; }
    public long total() { return Math.round(score); }
}
