package com.derevna230.comborush.species;

import com.derevna230.comborush.booster.EffectDef;
import com.derevna230.comborush.data.PlayerProfile;
import com.derevna230.comborush.score.ScoreSource;
import com.derevna230.comborush.shop.ShopService.Res;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Логика выбора существа и человекочитаемое описание его бонусов. Общая для сундука и клиентского экрана */
public final class SpeciesService {
    private SpeciesService() {}

    /** Строка бонуса или штрафа: good = плюс для игрока */
    public record Trait(String text, boolean good) {}

    public static boolean canChange(PlayerProfile p) { return p.eventsDone() == 0; }

    public static Res choose(ServerPlayerEntity player, String id) {
        PlayerProfile p = PlayerProfile.of(player);
        if (!SpeciesRegistry.exists(id)) return Res.err("Такого существа нет.");
        SpeciesDef d = SpeciesRegistry.get(id);
        if (d.id.equals(p.species())) return Res.err("Это существо уже выбрано.");
        if (!d.unlockedFor(p)) return Res.err("Закрыто: нужно сбросов — " + d.unlock_resets + ".");
        if (!canChange(p)) return Res.err("Сменить существо можно только после сброса.");
        player.setAttached(PlayerProfile.TYPE, p.withSpecies(d.id));
        SpeciesApplier.sync(player);
        com.derevna230.comborush.campaign.Campaign.trigger(player, "species", "", 1);
        return Res.ok("Теперь ты: " + d.name);
    }

    // ---------------- описание бонусов ----------------
    public static String num(double v) {
        String s = String.format(Locale.ROOT, "%.2f", v);
        while (s.contains(".") && (s.endsWith("0") || s.endsWith("."))) s = s.substring(0, s.length() - 1);
        return s;
    }

    public static String pct(double fraction) { return num(Math.abs(fraction) * 100) + "%"; }

    public static String sourceLabel(String key, boolean hasOthers) {
        if (key.equalsIgnoreCase("ALL")) return hasOthers ? "остальные действия" : "все действия";
        try { return ScoreSource.valueOf(key.toUpperCase(Locale.ROOT)).label.toLowerCase(Locale.ROOT); }
        catch (IllegalArgumentException e) { return key; }
    }

    public static String condLabel(String c) {
        return switch (c) {
            case "night" -> "ночью"; case "day" -> "днём"; case "dark" -> "в темноте";
            case "low_health" -> "при низком здоровье"; case "full_health" -> "при полном здоровье";
            case "overworld" -> "в Верхнем мире"; case "nether" -> "в Незере"; case "end" -> "в Крае";
            case "underground" -> "под землёй"; case "high" -> "на высоте"; case "sneaking" -> "крадучись";
            case "sprinting" -> "на бегу"; case "raining" -> "в дождь"; case "hungry" -> "при голоде";
            case "no_armor" -> "без брони";
            default -> c;
        };
    }

    public static List<Trait> traits(SpeciesDef d) {
        List<Trait> out = new ArrayList<>();

        boolean hasOthers = d.score_mult.size() > 1;
        for (Map.Entry<String, Double> e : d.score_mult.entrySet()) {
            double v = e.getValue();
            if (Math.abs(v - 1) < 1e-9) continue;
            out.add(new Trait("Очки ×" + num(v) + ": " + sourceLabel(e.getKey(), hasOthers), v > 1));
        }
        for (SpeciesDef.CondMult c : d.conditional_mult) {
            if (Math.abs(c.value - 1) < 1e-9) continue;
            String src = c.source.equalsIgnoreCase("ALL") ? "" : " (" + sourceLabel(c.source, false) + ")";
            out.add(new Trait("Очки ×" + num(c.value) + " " + condLabel(c.condition) + src, c.value > 1));
        }
        if (Math.abs(d.shop_price_mult - 1) > 1e-9)
            out.add(new Trait("Цены в магазине " + (d.shop_price_mult > 1 ? "+" : "−") + pct(d.shop_price_mult - 1),
                d.shop_price_mult < 1));
        if (Math.abs(d.combo_step_mult - 1) > 1e-9)
            out.add(new Trait("Серия растёт " + (d.combo_step_mult > 1 ? "быстрее" : "медленнее") + " на "
                + pct(d.combo_step_mult - 1), d.combo_step_mult > 1));

        for (EffectDef e : d.effects) {
            if ("attribute".equals(e.type)) out.add(attribute(e));
            else if ("status_effect".equals(e.type)) out.add(new Trait(statusLabel(e.id), true));
        }
        out.sort((a, b) -> Boolean.compare(b.good(), a.good()));      // сначала плюсы
        if (out.isEmpty()) out.add(new Trait("Без бонусов и штрафов", true));
        return out;
    }

    public static Trait attribute(EffectDef e) {
        String id = e.id.replace("minecraft:", "");
        double v = e.value;
        boolean percent = !"add_value".equals(e.operation);
        String sign = v >= 0 ? "+" : "−";
        String amount = percent ? pct(v) : num(Math.abs(v));
        return switch (id) {
            case "generic.max_health" -> new Trait(sign + num(Math.abs(v) / 2) + " " + hearts(Math.abs(v) / 2), v > 0);
            case "generic.attack_damage" -> new Trait(sign + amount + " урона", v > 0);
            case "generic.armor" -> new Trait(sign + amount + " брони", v > 0);
            case "generic.armor_toughness" -> new Trait(sign + amount + " к прочности брони", v > 0);
            case "generic.attack_speed" -> new Trait(sign + amount + " к скорости атаки", v > 0);
            case "generic.luck" -> new Trait(sign + amount + " к удаче", v > 0);
            case "generic.movement_speed" -> new Trait("Скорость " + sign + (percent ? amount : num(Math.abs(v))), v > 0);
            case "generic.knockback_resistance" -> new Trait(sign + pct(v) + " стойкости к отбрасыванию", v > 0);
            case "player.mining_efficiency" -> new Trait("Копание быстрее (" + sign + amount + ")", v > 0);
            default -> new Trait(id + " " + sign + amount, v > 0);
        };
    }

    private static String hearts(double n) {
        long k = Math.round(n);
        if (Math.abs(n - k) > 1e-9) return "сердца";
        long m10 = k % 10, m100 = k % 100;
        if (m10 == 1 && m100 != 11) return "сердце";
        if (m10 >= 2 && m10 <= 4 && (m100 < 12 || m100 > 14)) return "сердца";
        return "сердец";
    }

    public static String statusLabel(String id) {
        return switch (id.replace("minecraft:", "")) {
            case "water_breathing" -> "Дыхание под водой";
            case "night_vision" -> "Ночное зрение";
            case "fire_resistance" -> "Огнестойкость";
            case "speed" -> "Постоянная скорость";
            case "haste" -> "Постоянная спешка";
            case "slow_falling" -> "Медленное падение";
            case "dolphins_grace" -> "Грация дельфина";
            case "regeneration" -> "Постоянная регенерация";
            case "resistance" -> "Постоянное сопротивление";
            case "jump_boost" -> "Прыгучесть";
            case "luck" -> "Удача";
            default -> "Эффект: " + id;
        };
    }
}
