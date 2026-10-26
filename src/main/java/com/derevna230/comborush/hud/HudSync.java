package com.derevna230.comborush.hud;

import com.derevna230.comborush.booster.BoosterDef;
import com.derevna230.comborush.booster.BoosterRegistry;
import com.derevna230.comborush.booster.EffectDef;
import com.derevna230.comborush.consumable.ConsumableDef;
import com.derevna230.comborush.consumable.ConsumableRegistry;
import com.derevna230.comborush.cycle.CycleManager;
import com.derevna230.comborush.cycle.CyclePhase;
import com.derevna230.comborush.data.OwnedBooster;
import com.derevna230.comborush.data.PlayerProfile;
import com.derevna230.comborush.director.DirectorManager;
import com.derevna230.comborush.promo.PromoManager;
import com.derevna230.comborush.score.BoosterEffects;
import com.derevna230.comborush.score.EventScore;
import com.derevna230.comborush.score.ScoreEngine;
import com.derevna230.comborush.score.ScoreSource;
import com.derevna230.comborush.species.SpeciesRegistry;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.*;

/** Отправляет клиентскому моду список усилителей, состояние цикла и «попы» подсчёта очков. Без клиентского мода ничего не шлёт. */
public class HudSync {
    private static final Gson GSON = new Gson();
    private static final Map<UUID, String> LAST_BOOSTERS = new HashMap<>();
    private static final Map<UUID, String> LAST_STATE = new HashMap<>();
    private static final DecimalFormat NUM = new DecimalFormat("0.##", DecimalFormatSymbols.getInstance(Locale.ROOT));

    public static boolean has(ServerPlayerEntity p) { return ServerPlayNetworking.canSend(p, HudPayload.ID); }

    public static void forget(UUID id) { LAST_BOOSTERS.remove(id); LAST_STATE.remove(id); }

    public static void tick(MinecraftServer server) {
        int t = server.getTicks();
        if (t % 10 != 0) return;
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (!has(p)) continue;
            sendIfChanged(p, "boosters", boostersJson(p), LAST_BOOSTERS);
            if (t % 20 == 0) sendIfChanged(p, "state", stateJson(p), LAST_STATE);
        }
    }

    private static void sendIfChanged(ServerPlayerEntity p, String kind, String json, Map<UUID, String> cache) {
        if (json.equals(cache.get(p.getUuid()))) return;
        cache.put(p.getUuid(), json);
        ServerPlayNetworking.send(p, new HudPayload(kind, json));
    }

    // ---------------- состояние ----------------
    private static String stateJson(ServerPlayerEntity p) {
        PlayerProfile prof = PlayerProfile.of(p);
        boolean event = CycleManager.phase() == CyclePhase.EVENT;
        JsonObject o = new JsonObject();
        o.addProperty("phase", event ? "EVENT" : "NORMAL");
        o.addProperty("secs", CycleManager.secondsLeft());
        o.addProperty("coins", prof.coins());
        o.addProperty("score", event ? ScoreEngine.get(p.getUuid()).total() : 0L);
        o.addProperty("event", DirectorManager.currentName());
        o.addProperty("level", prof.level());
        o.addProperty("species", SpeciesRegistry.get(prof.species()).name);
        o.addProperty("rank", prof.rank());
        o.addProperty("promo", String.join(", ", PromoManager.activeNames()));
        var deb = DirectorManager.debuffFor(p);
        o.addProperty("boss", deb == null ? "" : deb.name);
        o.addProperty("blind", deb != null && deb.blind);
        return GSON.toJson(o);
    }

    // ---------------- усилители ----------------
    private static String category(BoosterDef def) {
        boolean game = def.hasGame(), score = def.hasScore();
        boolean team = false;
        for (EffectDef e : def.effects)
            if (e.type != null && (e.type.startsWith("aura_") || e.type.equals("share_score") || "team_score".equals(e.scale))) team = true;
        if (team && !game) return "team";
        if (game && score) return "hybrid";
        if (game) return "game";
        boolean mult = false, chips = false;
        for (EffectDef e : def.effects) {
            if (e.type == null) continue;
            if (e.type.startsWith("mult_") || e.type.startsWith("combo_")) mult = true;
            else if (e.type.startsWith("chips_")) chips = true;
        }
        return mult ? "mult" : chips ? "chips" : "econ";
    }

    private static String fmt(double v) { return NUM.format(v); }

    private static String tagText(BoosterEffects.Trigger t) {
        return switch (t.kind) {
            case "chips", "mult" -> "+" + fmt(t.value);
            case "chipsx", "xmult" -> "×" + fmt(t.value);
            case "coin", "sell" -> "+" + fmt(t.value * 100) + "%";
            case "disc" -> "-" + fmt(t.value * 100) + "%";
            case "share" -> "+" + fmt(t.value * 100) + "%";
            case "reroll" -> "-" + fmt(t.value);
            case "interest" -> "+%";
            default -> "+" + fmt(t.value);
        };
    }

    private static String boostersJson(ServerPlayerEntity p) {
        PlayerProfile prof = PlayerProfile.of(p);
        BoosterEffects fx = BoosterEffects.of(p, prof);
        JsonObject root = new JsonObject();
        JsonArray arr = new JsonArray();
        List<OwnedBooster> list = prof.boosters();

        for (int i = 0; i < list.size(); i++) {
            OwnedBooster o = list.get(i);
            BoosterDef def = BoosterRegistry.get(o.id());
            if (def == null) continue;

            JsonObject b = new JsonObject();
            b.addProperty("id", def.id);
            b.addProperty("name", def.name);
            b.addProperty("desc", def.description);
            b.addProperty("icon", def.icon);
            b.addProperty("cat", category(def));
            b.addProperty("neg", o.negative());
            b.addProperty("rar", def.rarity);
            b.addProperty("lvl", o.level());
            b.addProperty("ed", o.edition());

            JsonArray tags = new JsonArray();
            boolean any = false;
            for (BoosterEffects.Trigger t : fx.triggers) {
                if (t.index != i) continue;
                any = true;
                if (tags.size() >= 2) continue;
                JsonObject tg = new JsonObject();
                tg.addProperty("k", t.kind);
                tg.addProperty("t", tagText(t));
                tags.add(tg);
            }
            b.add("tags", tags);
            // карточка тусклая, если у усилителя есть очковая часть, но сейчас она не работает (условие не выполнено / часть потеряна)
            boolean hasScorePart = def.hasScore();
            b.addProperty("active", !hasScorePart || any);
            arr.add(b);
        }
        root.add("list", arr);

        JsonArray cons = new JsonArray();
        for (String cid : prof.consumables()) {
            ConsumableDef cd = ConsumableRegistry.get(cid);
            if (cd == null) continue;
            JsonObject co = new JsonObject();
            co.addProperty("id", cd.id);
            co.addProperty("name", cd.name);
            co.addProperty("desc", cd.description);
            co.addProperty("icon", cd.icon);
            co.addProperty("rar", cd.rarity);
            cons.add(co);
        }
        root.add("cons", cons);
        root.addProperty("slots", prof.slotCapacity());
        return GSON.toJson(root);
    }

    // ---------------- экран итогов события ----------------
    private static final String[] MEDAL_NAMES = {"Бронза", "Серебро", "Золото", "Платина"};

    /** @return true, если у игрока есть клиентский мод и экран итогов отправлен */
    public static boolean result(ServerPlayerEntity p, int number, long score, String medal, String medalLabel,
                                 double[] thr, int coinsPure, int coinsBonus, int interest, int coinsTotal,
                                 String boss, boolean bossWin, String consumable, String eventName, EventScore s) {
        if (!has(p)) return false;
        JsonObject o = new JsonObject();
        o.addProperty("n", number);
        o.addProperty("score", score);
        o.addProperty("medal", medal);
        o.addProperty("medalLabel", medalLabel);
        JsonArray th = new JsonArray();
        for (double v : thr) th.add(v);
        o.add("thr", th);
        double next = 0; String nextName = "";
        for (int i = 0; i < thr.length; i++) if (thr[i] > score) { next = thr[i]; nextName = MEDAL_NAMES[i]; break; }
        o.addProperty("next", next);
        o.addProperty("nextName", nextName);
        o.addProperty("pure", coinsPure);
        o.addProperty("bonus", coinsBonus);
        o.addProperty("interest", interest);
        o.addProperty("total", coinsTotal);
        o.addProperty("boss", boss);
        o.addProperty("bossWin", bossWin);
        o.addProperty("cons", consumable);
        o.addProperty("event", eventName);

        List<Map.Entry<String, Double>> rows = new ArrayList<>(s.bySource.entrySet());
        rows.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        JsonArray arr = new JsonArray();
        for (var e : rows) {
            if (e.getValue() <= 0) continue;
            JsonObject r = new JsonObject();
            r.addProperty("k", e.getKey());
            r.addProperty("p", e.getValue());
            r.addProperty("n", s.countBySource.getOrDefault(e.getKey(), 0));
            arr.add(r);
        }
        o.add("rows", arr);
        ServerPlayNetworking.send(p, new HudPayload("result", GSON.toJson(o)));
        return true;
    }

    // ---------------- подсчёт очков: данные для анимации ----------------
    private static int order(String kind) {
        return switch (kind) { case "chips" -> 0; case "chipsx" -> 1; case "mult" -> 2; case "xmult" -> 3; default -> 9; };
    }

    private record Step(int index, String kind, double value, String label) {}

    /** @return true, если у игрока есть клиентский мод и анимация отправлена (тогда action bar не нужен) */
    public static boolean pop(ServerPlayerEntity p, ScoreSource src, double base, double factor, double speciesMult,
                              String speciesName, double directorMult, double markMult,
                              double debuffMult, String debuffName, double chips, double comboBase, double mult, double gained, long total,
                              int windowTicks, BoosterEffects fx) {
        if (!has(p)) return false;

        List<Step> list = new ArrayList<>();
        for (BoosterEffects.Trigger t : fx.triggers) {
            boolean scoreKind = order(t.kind) < 9;
            boolean sourceOk = t.source.equalsIgnoreCase("ALL") || t.source.equalsIgnoreCase(src.name());
            boolean nonZero = switch (t.kind) {
                case "chipsx", "xmult" -> Math.abs(t.value - 1) > 1e-9;
                default -> Math.abs(t.value) > 1e-9;
            };
            if (scoreKind && sourceOk && nonZero) list.add(new Step(t.index, t.kind, t.value, t.label));
        }
        // «внешние» поправки фишек: существо, событие, метка, усталость от повторов (index = -1)
        if (Math.abs(speciesMult - 1) > 1e-9) list.add(new Step(-1, "chipsx", speciesMult, speciesName));
        if (Math.abs(directorMult - 1) > 1e-9) list.add(new Step(-1, "chipsx", directorMult, DirectorManager.currentName()));
        if (Math.abs(markMult - 1) > 1e-9) list.add(new Step(-1, "chipsx", markMult, "Метка"));
        if (Math.abs(debuffMult - 1) > 1e-9) list.add(new Step(-1, "chipsx", debuffMult, debuffName));
        if (factor < 0.999) list.add(new Step(-1, "chipsx", factor, "Повтор"));
        // порядок как в формуле: плоские фишки → множители фишек → +множитель → ×множитель
        list.sort(Comparator.<Step>comparingInt(st -> order(st.kind())).thenComparingInt(Step::index));

        JsonArray steps = new JsonArray();
        for (Step st : list) {
            JsonObject so = new JsonObject();
            so.addProperty("i", st.index());
            so.addProperty("k", st.kind());
            so.addProperty("v", st.value());
            if (!st.label().isEmpty()) so.addProperty("l", st.label());
            steps.add(so);
        }

        JsonObject o = new JsonObject();
        o.addProperty("src", src.name());
        o.addProperty("base", base);
        o.addProperty("chips", chips);
        o.addProperty("m0", comboBase);
        o.addProperty("mult", mult);
        o.addProperty("gain", gained);
        o.addProperty("total", total);
        o.addProperty("win", windowTicks);
        o.add("steps", steps);
        ServerPlayNetworking.send(p, new HudPayload("pop", GSON.toJson(o)));
        return true;
    }
}
