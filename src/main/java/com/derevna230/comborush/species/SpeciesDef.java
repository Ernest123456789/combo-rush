package com.derevna230.comborush.species;

import com.derevna230.comborush.booster.EffectDef;
import com.derevna230.comborush.data.PlayerProfile;
import com.derevna230.comborush.score.Conditions;
import com.derevna230.comborush.score.ScoreSource;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SpeciesDef {
    public String id;                       // заполняет загрузчик: "combo-rush:dwarf"
    public String name = "?";
    public String description = "";
    public String icon = "minecraft:player_head";
    public boolean default_unlocked = true;
    public int unlock_resets = 0;           // сколько сбросов нужно для открытия
    public double shop_price_mult = 1.0;    // множитель цен в магазине
    /** Множители фишек по источникам: KILL / MINE / HARVEST; "ALL" — для всех, не указанных явно */
    public Map<String, Double> score_mult = new HashMap<>();
    /** Игровые эффекты (attribute / status_effect), как у усилителей */
    public List<EffectDef> effects = new ArrayList<>();

    /** Условный множитель: например {"condition":"night","value":1.5}. source — KILL/MINE/... или ALL */
    public static class CondMult {
        public String condition = "";
        public String source = "ALL";
        public double value = 1.0;
    }
    public List<CondMult> conditional_mult = new ArrayList<>();
    /** Во сколько раз медленнее/быстрее растёт множитель серии (комбо). 0.6 = на 40% медленнее */
    public double combo_step_mult = 1.0;

    public double conditionalMult(ServerPlayerEntity p, ScoreSource src) {
        double m = 1.0;
        for (CondMult c : conditional_mult) {
            if (!c.source.equalsIgnoreCase("ALL") && !c.source.equalsIgnoreCase(src.name())) continue;
            if (Conditions.met(c.condition, p)) m *= c.value;
        }
        return m;
    }

    public double scoreMult(ScoreSource src) {
        return score_mult.getOrDefault(src.name(), score_mult.getOrDefault("ALL", 1.0));
    }

    public boolean unlockedFor(PlayerProfile p) {
        return default_unlocked || p.resets() >= unlock_resets;
    }
}
