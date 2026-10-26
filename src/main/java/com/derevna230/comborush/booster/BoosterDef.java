package com.derevna230.comborush.booster;

import java.util.ArrayList;
import java.util.List;

public class BoosterDef {
    public String id;                      // заполняет загрузчик: "rift:whetstone"
    public String name = "?";
    public String description = "";
    public String icon = "minecraft:paper";
    public int price = 10;
    public String rarity = "common";       // common | uncommon | rare | epic | legendary
    public int min_level = 1;
    public String promo = "";              // если задано — усилитель продаётся только пока идёт эта акция
    public int unlock_resets = 0;          // сколько сбросов нужно, чтобы усилитель попал в магазин
    public boolean negative_allowed = true;
    public List<EffectDef> effects = new ArrayList<>();

    public boolean hasScore() { return effects.stream().anyMatch(EffectDef::isScore); }
    public boolean hasGame()  { return effects.stream().anyMatch(EffectDef::isGame); }
    /** Негативным можно сделать только усилитель, у которого есть обе части */
    public boolean canBeNegative() { return negative_allowed && hasScore() && hasGame(); }
}
