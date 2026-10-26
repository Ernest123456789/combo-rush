package com.derevna230.comborush.score;

import com.derevna230.comborush.booster.BoosterDef;
import com.derevna230.comborush.booster.BoosterRegistry;
import com.derevna230.comborush.booster.EffectDef;
import com.derevna230.comborush.data.OwnedBooster;
import com.derevna230.comborush.data.PlayerProfile;
import com.derevna230.comborush.director.DebuffDef;
import com.derevna230.comborush.director.DirectorManager;
import com.derevna230.comborush.promo.PromoManager;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Собирает все не-игровые эффекты усилителей игрока (и аур союзников рядом) на текущий момент */
public class BoosterEffects {
    /** Каждый уровень усилителя усиливает положительные эффекты на 20% */
    public static final double LEVEL_STEP = 0.20;

    private final Map<ScoreSource, Double> flat = new EnumMap<>(ScoreSource.class);
    private final Map<ScoreSource, Double> mult = new EnumMap<>(ScoreSource.class);
    public double multAdd = 0, multTimes = 1;

    // комбо
    public double comboStep = 0;
    public int comboWindowTicks = 0;

    // экономика
    public double coinBonus = 0;       // +% монет за событие
    public double shopDiscount = 0;    // скидка в магазине
    public int rerollDiscount = 0;     // скидка на перекрут (монеты)
    public double sellBonus = 0;       // добавка к доле возврата при продаже (база 0.5)
    public int interestMax = 0;        // максимум процентов (монет)
    public double interestPer = 5;     // сколько монет в запасе дают 1 монету процентов

    /** Что дал каждый усилитель: нужно для подписей на карточках и анимации подсчёта */
    public static class Trigger {
        public final int index;       // номер усилителя игрока; -1 — эффект союзника
        public final String kind;     // chips, chipsx, mult, xmult, combo, coin, disc, reroll, sell, interest, share
        public final double value;
        public final String source;   // ALL / KILL / ...
        public final String label;    // для аур: название усилителя союзника
        Trigger(int index, String kind, double value, String source, String label) {
            this.index = index; this.kind = kind; this.value = value; this.source = source; this.label = label;
        }
    }
    public final List<Trigger> triggers = new ArrayList<>();

    public double chipsFlat(ScoreSource s) { return flat.getOrDefault(s, 0.0); }
    public double chipsMult(ScoreSource s) { return mult.getOrDefault(s, 1.0); }

    public static BoosterEffects of(ServerPlayerEntity player, PlayerProfile profile) {
        BoosterEffects fx = new BoosterEffects();
        DebuffDef deb = DirectorManager.debuffFor(player);

        // --- собственные усилители ---
        List<OwnedBooster> owned = profile.boosters();
        for (int bi = 0; bi < owned.size(); bi++) {
            if (deb != null && deb.silence && bi == 0) continue;     // босс «Тишина»: первый усилитель отключён
            accumulate(fx, player, player, profile, bi, owned.get(bi), false);
        }

        // --- ауры союзников в радиусе ---
        MinecraftServer server = player.getServer();
        if (server != null) {
            for (ServerPlayerEntity q : server.getPlayerManager().getPlayerList()) {
                if (q == player || q.isSpectator() || q.getServerWorld() != player.getServerWorld()) continue;
                PlayerProfile qp = PlayerProfile.of(q);
                DebuffDef qd = DirectorManager.debuffFor(q);
                List<OwnedBooster> qo = qp.boosters();
                for (int qi = 0; qi < qo.size(); qi++) {
                    if (qd != null && qd.silence && qi == 0) continue;
                    accumulate(fx, player, q, qp, qi, qo.get(qi), true);
                }
            }
        }

        // --- акции и правила события ---
        fx.shopDiscount += PromoManager.shopDiscount();
        fx.coinBonus += PromoManager.coinBonus();
        fx.sellBonus += PromoManager.sellBonus();
        fx.rerollDiscount += PromoManager.rerollDiscount();
        fx.multTimes *= PromoManager.scoreMult();
        fx.multAdd += DirectorManager.multAdd();
        fx.comboStep += DirectorManager.comboStep();
        fx.comboWindowTicks += (int) Math.round(DirectorManager.comboWindow() * 20);
        fx.coinBonus += DirectorManager.coinBonus();
        if (deb != null) fx.comboWindowTicks += (int) Math.round(deb.combo_window * 20);
        return fx;
    }

    private static String baseType(String auraType) {
        return switch (auraType) {
            case "aura_mult_add"    -> "mult_add";
            case "aura_mult_times"  -> "mult_times";
            case "aura_chips"       -> "chips_per_action";
            case "aura_coin_bonus"  -> "coin_bonus";
            default -> "";
        };
    }

    /**
     * Применяет эффекты одного усилителя.
     * owner — владелец усилителя; viewer — тот, кому считаем (для ауры это сосед владельца).
     */
    private static void accumulate(BoosterEffects fx, ServerPlayerEntity viewer, ServerPlayerEntity owner,
                                   PlayerProfile ownerProfile, int bi, OwnedBooster o, boolean asAura) {
        BoosterDef def = BoosterRegistry.get(o.id());
        if (def == null || o.dropped().equals("score")) return;     // очковая часть потеряна

        double lf = 1 + LEVEL_STEP * o.level();
        int idx = asAura ? -1 : bi;
        String label = asAura ? def.name : "";

        for (EffectDef e : def.effects) {
            if (e.isGame()) continue;
            String type = e.type == null ? "" : e.type;
            boolean aura = type.startsWith("aura_");
            if (asAura && !aura) continue;
            if (asAura && viewer.squaredDistanceTo(owner) > e.radius * e.radius) continue;
            if (!Conditions.met(e.condition, owner)) continue;
            if (aura) type = baseType(type);

            boolean scaled = !e.scale.isEmpty();
            double k = 1;
            if (scaled) {
                k = Math.floor(stat(owner, ownerProfile, e.scale) / Math.max(e.per, 1e-9));
                if (e.max > 0) k = Math.min(k, e.max);
                k = Math.max(0, k);
            }
            // для «умножающих» эффектов с масштабированием value — прирост за шаг: 1 + value × k
            double times = scaled ? 1 + e.value * k : e.value;
            double add = e.value * k;
            if (times > 1) times = 1 + (times - 1) * lf;       // уровень усиливает только плюсы
            if (add > 0) add *= lf;
            final double addF = add, timesF = times;

            switch (type) {
                case "chips_per_action" -> { sources(e).forEach(s -> fx.flat.merge(s, addF, Double::sum)); fx.triggers.add(new Trigger(idx, "chips", addF, e.source, label)); }
                case "chips_multiplier" -> { sources(e).forEach(s -> fx.mult.merge(s, timesF, (a, b) -> a * b)); fx.triggers.add(new Trigger(idx, "chipsx", timesF, e.source, label)); }
                case "mult_add"         -> { fx.multAdd += addF; fx.triggers.add(new Trigger(idx, "mult", addF, "ALL", label)); }
                case "mult_times"       -> { fx.multTimes *= timesF; fx.triggers.add(new Trigger(idx, "xmult", timesF, "ALL", label)); }
                case "combo_step"       -> { fx.comboStep += addF; fx.triggers.add(new Trigger(idx, "combo", addF, "ALL", label)); }
                case "combo_window"     -> { fx.comboWindowTicks += (int) Math.round(addF * 20); fx.triggers.add(new Trigger(idx, "combo", addF, "ALL", label)); }
                case "coin_bonus"       -> { fx.coinBonus += addF; fx.triggers.add(new Trigger(idx, "coin", addF, "ALL", label)); }
                case "shop_discount"    -> { fx.shopDiscount += addF; fx.triggers.add(new Trigger(idx, "disc", addF, "ALL", label)); }
                case "reroll_discount"  -> { fx.rerollDiscount += (int) Math.round(addF); fx.triggers.add(new Trigger(idx, "reroll", addF, "ALL", label)); }
                case "sell_bonus"       -> { fx.sellBonus += addF; fx.triggers.add(new Trigger(idx, "sell", addF, "ALL", label)); }
                case "interest"         -> { fx.triggers.add(new Trigger(idx, "interest", e.value, "ALL", label));
                                             fx.interestMax += (int) Math.round(e.value * lf);
                                             fx.interestPer = Math.min(fx.interestPer, Math.max(1, e.per)); }
                case "share_score"      -> { if (!asAura) fx.triggers.add(new Trigger(idx, "share", e.value * lf, "ALL", label)); }
                default -> { }
            }
        }

        if (!asAura && !o.edition().isEmpty()) applyEdition(fx, bi, o.edition());
    }

    /** Издания: фольга +25 фишек, голограмма +0.4 множителя, полихром ×1.2 множителя */
    private static void applyEdition(BoosterEffects fx, int bi, String edition) {
        switch (edition) {
            case "foil" -> {
                for (ScoreSource s : ScoreSource.values()) fx.flat.merge(s, 25.0, Double::sum);
                fx.triggers.add(new Trigger(bi, "chips", 25, "ALL", ""));
            }
            case "holo" -> { fx.multAdd += 0.4; fx.triggers.add(new Trigger(bi, "mult", 0.4, "ALL", "")); }
            case "poly" -> { fx.multTimes *= 1.2; fx.triggers.add(new Trigger(bi, "xmult", 1.2, "ALL", "")); }
            default -> { }
        }
    }

    /** Значения, которые зависят от самого игрока; остальное берётся из профиля */
    static double stat(ServerPlayerEntity p, PlayerProfile prof, String key) {
        return switch (key) {
            case "health"       -> p.getHealth();
            case "armor"        -> p.getArmor();
            case "hunger"       -> p.getHungerManager().getFoodLevel();
            case "xp_level"     -> p.experienceLevel;
            case "unique_items" -> uniqueItems(p);
            case "team_score"   -> teamScore(p);
            default             -> prof.stat(key);
        };
    }

    /** Сумма очков остальных игроков в текущем событии (для «Эстафеты») */
    private static double teamScore(ServerPlayerEntity p) {
        MinecraftServer server = p.getServer();
        if (server == null) return 0;
        double sum = 0;
        for (ServerPlayerEntity q : server.getPlayerManager().getPlayerList())
            if (q != p) sum += ScoreEngine.get(q.getUuid()).total();
        return sum;
    }

    private static int uniqueItems(ServerPlayerEntity p) {
        Set<Item> set = new HashSet<>();
        for (ItemStack s : p.getInventory().main) if (!s.isEmpty()) set.add(s.getItem());
        return set.size();
    }

    private static List<ScoreSource> sources(EffectDef e) {
        return e.source.equalsIgnoreCase("ALL") ? List.of(ScoreSource.values())
                                                 : List.of(ScoreSource.valueOf(e.source.toUpperCase()));
    }
}
