package com.derevna230.comborush.weapon;

/**
 * Особая способность оружия, щита или питомца.
 * trigger: hit (удар/попадание), kill (убийство), block (блок щитом), arrow (выстрел из лука)
 * type: effect, self_effect, ignite, freeze, lightning, lifesteal, heal, bleed, crit, execute,
 *       shockwave, cleave, blast, knockback, reflect, drop_item, arrow_damage
 */
public class AbilityDef {
    public String trigger = "hit";
    public String type = "";
    public double chance = 1;
    public double value = 0;
    public double duration = 3;      // секунды
    public int amplifier = 0;
    public String id = "";           // статус-эффект или предмет
    public double radius = 3;
}
