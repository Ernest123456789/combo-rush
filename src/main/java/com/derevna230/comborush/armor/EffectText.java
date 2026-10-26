package com.derevna230.comborush.armor;

import com.derevna230.comborush.booster.EffectDef;
import com.derevna230.comborush.species.SpeciesService;
import com.derevna230.comborush.species.SpeciesService.Trait;

import static com.derevna230.comborush.species.SpeciesService.num;
import static com.derevna230.comborush.species.SpeciesService.pct;

/** Человекочитаемое описание эффекта (для подсказок брони и кейсов) */
public final class EffectText {
    private EffectText() {}

    public static Trait describe(EffectDef e) {
        String src = SpeciesService.sourceLabel(e.source == null ? "ALL" : e.source, false);
        String cond = e.condition == null || e.condition.isEmpty() ? "" : " (" + SpeciesService.condLabel(e.condition) + ")";
        double v = e.value;
        String type = e.type == null ? "" : e.type;
        Trait t = switch (type) {
            case "chips_per_action" -> new Trait((v >= 0 ? "+" : "−") + num(Math.abs(v)) + " фишек: " + src, v >= 0);
            case "chips_multiplier" -> new Trait("Фишки ×" + num(v) + ": " + src, v >= 1);
            case "mult_add" -> new Trait((v >= 0 ? "+" : "−") + num(Math.abs(v)) + " к множителю", v >= 0);
            case "mult_times" -> new Trait("Множитель ×" + num(v), v >= 1);
            case "combo_step" -> new Trait("Серия растёт быстрее (+" + num(v) + ")", v >= 0);
            case "combo_window" -> new Trait("Окно серии " + (v >= 0 ? "+" : "−") + num(Math.abs(v)) + " сек", v >= 0);
            case "coin_bonus" -> new Trait("+" + pct(v) + " монет за событие", v >= 0);
            case "shop_discount" -> new Trait("Скидка " + pct(v) + " в магазине", v >= 0);
            case "reroll_discount" -> new Trait("Перекрут дешевле на " + num(v), v >= 0);
            case "sell_bonus" -> new Trait("+" + pct(v) + " к цене продажи", v >= 0);
            case "attribute" -> SpeciesService.attribute(e);
            case "status_effect" -> new Trait(SpeciesService.statusLabel(e.id), true);
            default -> new Trait(type, true);
        };
        return new Trait(t.text() + cond, t.good());
    }
}
