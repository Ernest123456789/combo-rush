package com.derevna230.comborush.booster;

public class EffectDef {
    public String type;
    public String source = "ALL";          // KILL / MINE / HARVEST / ALL
    public double value = 0;
    public String id = "";                 // для attribute и status_effect
    public int amplifier = 0;
    public String operation = "add_value"; // add_value / add_multiplied_base / add_multiplied_total

    /** Условие срабатывания (см. Conditions): night, day, low_health, nether, ... */
    public String condition = "";
    /** Масштабирование: значение × floor(stat / per), не больше max шагов (max=0 — без ограничения) */
    public String scale = "";
    public double per = 1;
    public double max = 0;
    /** Радиус действия для эффектов aura_* (в блоках) */
    public double radius = 20;

    /** Игровые эффекты: атрибуты и статус-эффекты. Всё остальное относится к «очковой/экономической» части */
    public boolean isGame()  { return "attribute".equals(type) || "status_effect".equals(type); }
    public boolean isScore() { return !isGame(); }
}
