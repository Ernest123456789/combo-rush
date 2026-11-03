package com.derevna230.comborush.cosmetic;

public class CosmeticDef {
    public String id;                 // "combo-rush:chicken"
    public String name = "?";
    public String description = "";
    public String icon = "minecraft:paper";
    /** pet | title | trail — в каждый тип можно надеть один предмет */
    public String type = "title";
    public String world = "";         // "" — всегда, "night_city" — после пролога
    public int price = 20;            // в эссенции

    // pet
    public String entity = "minecraft:chicken";
    public String pet_name = "Питомец {player}";
    public boolean baby = false;
    // title
    public String prefix = "";        // например "§d[Режиссёр] "
    // trail
    public String particle = "minecraft:end_rod";
    public int count = 2;
}
