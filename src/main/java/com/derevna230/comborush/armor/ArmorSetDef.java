package com.derevna230.comborush.armor;

import com.derevna230.comborush.booster.EffectDef;
import com.derevna230.comborush.weapon.AbilityDef;

import java.util.ArrayList;
import java.util.List;

/**
 * Способности предмета из кейса. Файлы: data/<modid>/armor_sets/<id>.json (броня, шляпы),
 * weapons/<id>.json (оружие, щиты), pets/pet_<id>.json (питомцы)
 */
public class ArmorSetDef {
    public String id;                                   // из имени файла
    public String name = "?";
    public String description = "";
    /** Эффекты за каждую надетую часть (значение умножается на число частей) */
    public List<EffectDef> piece_effects = new ArrayList<>();
    /** Эффекты полного комплекта (для шляп — когда шляпа надета) */
    public List<EffectDef> set_effects = new ArrayList<>();
    /** Особые способности (оружие, щиты, питомцы) */
    public List<AbilityDef> abilities = new ArrayList<>();
    /** Урон боевого питомца за удар (0 — спокойный питомец, не дерётся) */
    public double pet_damage = 0;
}
