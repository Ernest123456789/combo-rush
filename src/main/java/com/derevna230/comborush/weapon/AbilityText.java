package com.derevna230.comborush.weapon;

import static com.derevna230.comborush.species.SpeciesService.num;

/** Описание способности для подсказки */
public final class AbilityText {
    private AbilityText() {}

    public static String effectName(String id) {
        return switch (id.replace("minecraft:", "")) {
            case "poison" -> "Отравление"; case "wither" -> "Иссушение"; case "weakness" -> "Слабость";
            case "slowness" -> "Замедление"; case "glowing" -> "Свечение"; case "levitation" -> "Левитация";
            case "blindness" -> "Слепота"; case "darkness" -> "Тьма"; case "nausea" -> "Тошнота";
            case "speed" -> "Скорость"; case "strength" -> "Сила"; case "regeneration" -> "Регенерация";
            case "absorption" -> "Поглощение"; case "resistance" -> "Сопротивление"; case "haste" -> "Спешка";
            case "jump_boost" -> "Прыгучесть"; case "fire_resistance" -> "Огнестойкость";
            default -> id;
        };
    }

    private static String lvl(int amp) {
        return switch (amp) { case 0 -> ""; case 1 -> " II"; case 2 -> " III"; case 3 -> " IV"; default -> " " + (amp + 1); };
    }

    private static String sec(double s) { return num(s) + " с"; }

    public static String describe(AbilityDef a, boolean bow, boolean pet) {
        String when = switch (a.trigger) {
            case "kill" -> pet ? "Когда питомец добивает врага" : "При убийстве";
            case "block" -> "При блоке";
            case "arrow" -> "Стрелы";
            default -> pet ? "Удар питомца" : bow ? "При попадании" : "При ударе";
        };
        String chance = a.chance < 1 ? " (" + num(a.chance * 100) + "%)" : "";
        String what = switch (a.type) {
            case "effect" -> effectName(a.id) + lvl(a.amplifier) + " на цель, " + sec(a.duration);
            case "self_effect" -> effectName(a.id) + lvl(a.amplifier) + " тебе, " + sec(a.duration);
            case "ignite" -> "поджигает на " + sec(a.duration);
            case "freeze" -> "замораживает на " + sec(a.duration);
            case "lightning" -> "удар молнии, +" + num(a.value) + " урона";
            case "lifesteal" -> "лечит на " + num(a.value * 100) + "% урона";
            case "heal" -> "лечит " + num(a.value / 2) + " ❤";
            case "bleed" -> "кровотечение " + num(a.value) + " урона/с, " + sec(a.duration);
            case "crit" -> "крит +" + num(a.value * 100) + "% урона";
            case "execute" -> "добивание: +" + num(a.value * 100) + "% урона по раненым (<30%)";
            case "shockwave" -> "звуковая волна: " + num(a.value) + " урона вокруг (" + num(a.radius) + " бл.)";
            case "cleave" -> "рассекает соседей: " + num(a.value) + " урона";
            case "blast" -> "взрыв без разрушений: " + num(a.value) + " урона вокруг";
            case "knockback" -> "сильно отбрасывает";
            case "reflect" -> "возвращает " + num(a.value * 100) + "% урона атакующему";
            case "drop_item" -> "выпадает добыча: " + a.id.replace("minecraft:", "");
            case "arrow_damage" -> "урон стрел ×" + num(a.value);
            default -> a.type;
        };
        return when + chance + ": " + what;
    }
}
