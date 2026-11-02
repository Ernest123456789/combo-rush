package com.derevna230.comborush.score;

import com.derevna230.comborush.booster.BoosterDef;
import com.derevna230.comborush.booster.BoosterRegistry;
import com.derevna230.comborush.booster.EffectDef;
import com.derevna230.comborush.consumable.ConsumableDef;
import com.derevna230.comborush.consumable.ConsumableRegistry;
import com.derevna230.comborush.cycle.CycleManager;
import com.derevna230.comborush.data.OwnedBooster;
import com.derevna230.comborush.director.DebuffDef;
import com.derevna230.comborush.cycle.CyclePhase;
import com.derevna230.comborush.data.PlayerProfile;
import com.derevna230.comborush.director.DirectorManager;
import com.derevna230.comborush.hud.HudSync;
import com.derevna230.comborush.leaderboard.Leaderboard;
import com.derevna230.comborush.species.SpeciesDef;
import com.derevna230.comborush.prestige.PrestigeManager;
import com.derevna230.comborush.species.SpeciesRegistry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class ScoreEngine {
    private static final int COMBO_WINDOW_TICKS = 100; // 5 секунд
    private static final double COMBO_STEP = 0.05;

    private static final Map<UUID, EventScore> SCORES = new HashMap<>();

    public static void reset() { SCORES.clear(); }

    public static EventScore get(UUID id) {
        return SCORES.computeIfAbsent(id, k -> new EventScore());
    }

    /** key: тип моба/блока — отдельный счётчик убывающей отдачи */
    public static void award(ServerPlayerEntity player, ScoreSource src, String key) {
        award(player, src, key, 1.0, "");
    }

    /**
     * extraMult < 1 — урезанная награда (например, моб из спавнера); такие действия не растят статистику.
     * extraLabel показывается в анимации подсчёта.
     */
    public static void award(ServerPlayerEntity player, ScoreSource src, String key, double extraMult, String extraLabel) {
        // статистика действий считается всегда (для масштабируемых усилителей и /cr stats)
        PlayerProfile prof = PlayerProfile.of(player);
        if (extraMult >= 1.0) {
            prof = prof.countAction(src.statKey);
            player.setAttached(PlayerProfile.TYPE, prof);
        }

        if (CycleManager.phase() != CyclePhase.EVENT) return;
        if (!prof.started()) { remindStart(player, false); return; }   // забег не начат — очки не идут

        EventScore s = get(player.getUuid());
        BoosterEffects fx = BoosterEffects.of(player, prof);
        SpeciesDef sd = SpeciesRegistry.get(prof.species());
        double speciesMult = sd.scoreMult(src) * sd.conditionalMult(player, src);
        long now = player.getServer().getTicks();

        int count = s.counters.merge(src.name() + ":" + key, 1, Integer::sum);
        double factor = diminishing(count);

        int window = Math.max(20, COMBO_WINDOW_TICKS + fx.comboWindowTicks);
        double step = Math.max(0.0, COMBO_STEP + fx.comboStep) * sd.combo_step_mult;
        if (now - s.lastActionTick > window) s.combo = 1.0; else s.combo += step;
        s.lastActionTick = now;
        s.multAdd = fx.multAdd;
        s.multTimes = fx.multTimes;

        double dirMult = DirectorManager.sourceMult(src);
        double markMult = DirectorManager.markMult(player);
        double debMult = DirectorManager.debuffSourceMult(player, src);
        DebuffDef deb = DirectorManager.debuffFor(player);
        double chips = Math.max(0, src.baseChips + fx.chipsFlat(src)) * fx.chipsMult(src) * speciesMult
            * dirMult * markMult * debMult * extraMult * factor;
        double gained = chips * s.mult();
        s.chips += chips;
        s.score += gained;
        s.bySource.merge(src.name(), gained, Double::sum);
        s.countBySource.merge(src.name(), 1, Integer::sum);
        shareWithAllies(player, gained);        // усилители «Командный дух» у союзников рядом

        // клиентский мод получает анимацию, остальные — строку в action bar
        boolean hud = HudSync.pop(player, src, src.baseChips, factor, speciesMult, sd.name, dirMult, markMult,
            debMult, deb == null ? "" : deb.name, extraMult, extraLabel, chips, s.combo, s.mult(), gained, s.total(), window, fx);
        if (!hud) {
            player.sendMessage(Text.literal(String.format("+%.0f × %.2f = +%.0f §7| всего %d",
                chips, s.mult(), gained, s.total())).formatted(Formatting.GOLD), true);
        }
    }

    /** «Командный дух»: союзники рядом получают долю очков, которые только что заработал игрок */
    private static void shareWithAllies(ServerPlayerEntity src, double gained) {
        MinecraftServer server = src.getServer();
        if (server == null) return;
        for (ServerPlayerEntity q : server.getPlayerManager().getPlayerList()) {
            if (q == src || q.isSpectator() || q.getServerWorld() != src.getServerWorld()) continue;
            for (OwnedBooster o : PlayerProfile.of(q).boosters()) {
                BoosterDef def = BoosterRegistry.get(o.id());
                if (def == null || o.dropped().equals("score")) continue;
                for (EffectDef e : def.effects) {
                    if (!"share_score".equals(e.type)) continue;
                    if (q.squaredDistanceTo(src) > e.radius * e.radius) continue;
                    if (!Conditions.met(e.condition, q)) continue;
                    double amount = gained * e.value * (1 + BoosterEffects.LEVEL_STEP * o.level());
                    EventScore es = get(q.getUuid());
                    es.score += amount;
                    es.bySource.merge("SHARE", amount, Double::sum);
                }
            }
        }
    }

    /** Бонусные очки с отдельной строкой в итогах (например, «Разломы») */
    public static void addBonus(ServerPlayerEntity p, double points, String key, String reason) {
        if (CycleManager.phase() != CyclePhase.EVENT) return;
        EventScore s = get(p.getUuid());
        s.score += points;
        s.bySource.merge(key, points, Double::sum);
        p.sendMessage(Text.literal("§d§l+" + Math.round(points) + " очков §7— " + reason + " (всего " + s.total() + ")"), false);
    }

    /** Бонусные очки (например, за убийство охотника), без множителей */
    public static void addBonus(ServerPlayerEntity p, double points) {
        if (CycleManager.phase() != CyclePhase.EVENT) return;
        EventScore s = get(p.getUuid());
        s.score += points;
        s.bySource.merge("BONUS", points, Double::sum);
        p.sendMessage(Text.literal("§6§l+" + Math.round(points) + " бонусных очков! §7(всего " + s.total() + ")"), false);
    }

    static double diminishing(int count) {
        if (count <= 10) return 1.0;
        if (count <= 20) return 0.7;
        if (count <= 30) return 0.5;
        return 0.25;
    }

    // --- итоги ---
    public static double bronzeTarget(int eventNumber) {
        return 250 * Math.pow(1.12, eventNumber - 1);
    }

    public static Medal medalFor(long score, int eventNumber, double targetMult) {
        double b = bronzeTarget(eventNumber) * targetMult;
        if (score >= b * 3.5)  return Medal.PLATINUM;
        if (score >= b * 2.25) return Medal.GOLD;
        if (score >= b * 1.5)  return Medal.SILVER;
        if (score >= b)        return Medal.BRONZE;
        return Medal.NONE;
    }

    public enum Medal {
        NONE(0.4, "без медали"), BRONZE(1.0, "Бронза"), SILVER(1.1, "Серебро"),
        GOLD(1.25, "Золото"), PLATINUM(1.5, "Платина");

        public final double coinMult; public final String label;
        Medal(double c, String l) { coinMult = c; label = l; }
    }

    public static String sourceLabel(String key) {
        return switch (key) {
            case "BONUS" -> "Бонусы";
            case "SHARE" -> "Командный дух";
            case "RIFT" -> "Разломы";
            default -> {
                try { yield ScoreSource.valueOf(key).label; } catch (IllegalArgumentException e) { yield key; }
            }
        };
    }

    private static String breakdownText(EventScore s) {
        double total = s.bySource.values().stream().mapToDouble(Double::doubleValue).sum();
        if (total <= 0) return "—";
        return s.bySource.entrySet().stream()
            .sorted((a, b2) -> Double.compare(b2.getValue(), a.getValue())).limit(4)
            .map(e -> sourceLabel(e.getKey()) + " " + Math.round(e.getValue())
                + " (" + Math.round(e.getValue() / total * 100) + "%)")
            .collect(Collectors.joining(", "));
    }

    /** @return название выданного расходника или null, если слоты заняты */
    private static String giveRandomConsumable(ServerPlayerEntity p) {
        PlayerProfile pr = PlayerProfile.of(p);
        if (pr.consumables().size() >= PlayerProfile.CONSUMABLE_SLOTS) return null;
        String id = ConsumableRegistry.randomId();
        ConsumableDef def = ConsumableRegistry.get(id);
        if (def == null) return null;
        p.setAttached(PlayerProfile.TYPE, pr.addConsumable(id));
        return def.name;
    }

    private static final java.util.Map<UUID, Integer> LAST_REMIND = new java.util.HashMap<>();

    /** Напомнить, что забег не начат: кнопки в чате (не чаще раза в 20 с) или строка над хотбаром */
    public static void remindStart(ServerPlayerEntity p, boolean chat) {
        int now = p.getServer().getTicks();
        if (!chat) {
            if (now - LAST_REMIND.getOrDefault(p.getUuid(), -10000) < 100) return;
            LAST_REMIND.put(p.getUuid(), now);
            p.sendMessage(Text.literal("§eЗабег не начат — очки не засчитываются. §f/species §eили §f/cr ready"), true);
            return;
        }
        Text pick = Text.literal("§a§l[ВЫБРАТЬ СУЩЕСТВО]").styled(st -> st
            .withClickEvent(new net.minecraft.text.ClickEvent(net.minecraft.text.ClickEvent.Action.RUN_COMMAND, "/species")));
        Text go = Text.literal("§6§l[НАЧАТЬ С ТЕКУЩИМ]").styled(st -> st
            .withClickEvent(new net.minecraft.text.ClickEvent(net.minecraft.text.ClickEvent.Action.RUN_COMMAND, "/cr ready")));
        p.sendMessage(Text.literal("§eТвой забег ещё не начат: события тебе не засчитываются. ").append(pick).append(Text.literal(" ")).append(go), false);
    }

    public static void finishEvent(MinecraftServer server) {
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            EventScore s = get(p.getUuid());
            PlayerProfile profile = PlayerProfile.of(p);
            if (!profile.started()) {                 // опоздал или ещё не выбрал существо — событие не тратится
                p.sendMessage(Text.literal("§7Событие тебе не засчитано: забег ещё не начат."), false);
                remindStart(p, true);
                continue;
            }
            BoosterEffects fx = BoosterEffects.of(p, profile);
            int eventNumber = profile.eventsDone() + 1;

            long score = s.total();
            DebuffDef deb = DirectorManager.debuffFor(p);
            Medal medal = medalFor(score, eventNumber, DirectorManager.targetMult(p));
            double bossBonus = deb != null && medal != Medal.NONE ? deb.coin_bonus : 0;
            int base = (int) Math.floor(score / 100.0 * medal.coinMult * (1 + fx.coinBonus + bossBonus));
            int interest = fx.interestMax > 0
                ? (int) Math.min(fx.interestMax, Math.floor(profile.coins() / fx.interestPer)) : 0;
            int coins = base + interest;
            if (com.derevna230.comborush.campaign.Finale.chaos(server)) coins *= 2;     // финал «хаос»: монеты ×2
            coins = (int) Math.round(coins * Math.max(0, com.derevna230.comborush.ModConfig.get().coin_multiplier));
            boolean gold = medal.ordinal() >= Medal.GOLD.ordinal();

            p.setAttached(PlayerProfile.TYPE, profile.finishEvent(score, gold).addCoins(coins));
            Leaderboard.update(p);
            com.derevna230.comborush.campaign.Campaign.onEventFinished(p, medal.ordinal());
            BalanceStats.record(server, p.getName().getString(), eventNumber, score,
                bronzeTarget(eventNumber) * DirectorManager.targetMult(p), medal.name(), profile.species(),
                DirectorManager.currentName(), deb == null ? "" : deb.name, profile.boosters().size(), profile.resets());
            p.sendMessage(Text.literal("§6Итог: §f" + score + " очков §7| " + medal.label
                + " §7| §e+" + coins + " монет" + (interest > 0 ? " §7(проценты: " + interest + ")" : "")), false);

            String got = null;
            if (deb != null) {
                if (medal == Medal.NONE) {
                    p.sendMessage(Text.literal("§cБосс-блайнд «" + deb.name + "» провален."), false);
                } else {
                    got = deb.reward_consumable ? giveRandomConsumable(p) : null;
                    p.sendMessage(Text.literal("§6§lБосс-блайнд «" + deb.name + "» пройден! §eМонеты +"
                        + Math.round(deb.coin_bonus * 100) + "%" + (got != null ? " §7| §aрасходник: " + got : "")), false);
                }
            }

            // экран итогов (клиентский мод) или краткая сводка по источникам в чат
            double b = bronzeTarget(eventNumber) * DirectorManager.targetMult(p);
            double[] thr = {b, b * 1.5, b * 2.25, b * 3.5};
            int pure = (int) Math.floor(score / 100.0 * medal.coinMult);
            boolean sent = HudSync.result(p, eventNumber, score, medal.name(), medal.label, thr, pure,
                Math.max(0, base - pure), interest, coins, deb == null ? "" : deb.name,
                medal != Medal.NONE, got == null ? "" : got, DirectorManager.currentName(), s);
            if (!sent) p.sendMessage(Text.literal("§7Источники: " + breakdownText(s)), false);

            if (eventNumber == PrestigeManager.REQUIRED_EVENTS)
                p.sendMessage(Text.literal("§d§lСброс прогресса доступен! §fПодробности: §e/cr reset"), false);
        }
        reset();
    }
}
