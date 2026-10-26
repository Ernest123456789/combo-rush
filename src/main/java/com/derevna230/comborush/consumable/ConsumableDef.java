package com.derevna230.comborush.consumable;

public class ConsumableDef {
    public String id;
    public String name = "?";
    public String description = "";
    public String icon = "minecraft:paper";
    public String rarity = "common";
    public int price = 10;
    /** level_up | level_up_all | edition | random_edition | duplicate | reroll_booster | add_slot | free_reroll */
    public String action = "level_up";
    public String edition = "";           // для action = edition

    public boolean needsTarget() {
        return switch (action) {
            case "level_up", "edition", "random_edition", "duplicate", "reroll_booster" -> true;
            default -> false;
        };
    }
}
