package com.derevna230.comborush.match;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.*;

/**
 * Статистика матча: у каждого участника (игрок или бот) — убийства, смерти, помощь, урон, MVP, закладки/разминирования.
 * Ключ строки — UUID игрока или «bot:имя» (боты пересоздаются каждый раунд, а имя у слота постоянное).
 * Помощь — нанёс погибшему за эту жизнь не меньше 8 урона (40 %), но добил другой.
 */
public final class MatchStats {
    private MatchStats() {}

    public static final class Row {
        public String key, name; public boolean bot; public Team team;
        public int kills, deaths, assists, mvps, plants, defuses, roundKills, headshots;
        public float damage;
        public int score() { return kills * 2 + assists + plants * 2 + defuses * 2 + mvps * 2; }
    }

    private record LastHit(String attacker, HitBoxes.Part part, String weapon, long tick) {}

    static final Map<String, Row> ROWS = new LinkedHashMap<>();
    private static final Map<String, Map<String, Float>> DMG = new HashMap<>();
    private static final Map<String, LastHit> LAST = new HashMap<>();
    static String planter, defuser;

    static void reset() { ROWS.clear(); DMG.clear(); LAST.clear(); planter = defuser = null; }

    static void roundStart() {
        for (Row r : ROWS.values()) r.roundKills = 0;
        DMG.clear(); LAST.clear(); planter = defuser = null;
    }

    public static String key(Entity e) {
        if (e instanceof BotEntity b) return b.statKey();
        if (e instanceof ServerPlayerEntity p) return p.getUuidAsString();
        return null;
    }

    static Row row(Entity e) {
        String k = key(e);
        if (k == null) return null;
        Team t = Match.teamOf(e);
        Row r = ROWS.computeIfAbsent(k, kk -> {
            Row n = new Row();
            n.key = kk; n.bot = e instanceof BotEntity;
            n.name = e instanceof BotEntity b ? b.botName() : e.getName().getString();
            return n;
        });
        if (t != null) r.team = t;
        return r;
    }

    /** Участник появился в раунде — строка нужна даже без убийств */
    static void join(Entity e) { row(e); }

    /** Запоминаем последнее попадание: куда и чем (для значка хедшота и оружия в ленте) */
    public static void noteHit(LivingEntity victim, Entity attacker, HitBoxes.Part part) {
        String vk = key(victim), ak = key(attacker);
        if (vk == null || ak == null || !(attacker instanceof LivingEntity la)) return;
        String w = Registries.ITEM.getId(la.getMainHandStack().getItem()).getPath();
        LAST.put(vk, new LastHit(ak, part, w, victim.getWorld().getTime()));
    }

    static void damage(LivingEntity victim, Entity attacker, float amount) {
        Row a = row(attacker);
        String vk = key(victim);
        if (a == null || vk == null || Match.teamOf(attacker) == Match.teamOf(victim)) return;
        float real = Math.min(amount, victim.getHealth());
        a.damage += real;
        DMG.computeIfAbsent(vk, k -> new HashMap<>()).merge(a.key, real, Float::sum);
    }

    /** Убийство: смерть, убийство, помощь. Возвращает данные для ленты: оружие и был ли хедшот */
    static JsonObject kill(LivingEntity victim, Entity killer) {
        Row v = row(victim);
        String vk = key(victim);
        if (v != null) v.deaths++;
        LastHit lh = vk == null ? null : LAST.remove(vk);
        Row k = killer == null || killer == victim ? null : row(killer);
        boolean hs = lh != null && k != null && lh.attacker.equals(k.key) && lh.part == HitBoxes.Part.HEAD;
        if (k != null && Match.teamOf(killer) != Match.teamOf(victim)) {
            k.kills++; k.roundKills++;
            if (hs) k.headshots++;
        }
        Map<String, Float> dm = vk == null ? null : DMG.remove(vk);
        if (dm != null) for (var e : dm.entrySet())
            if (k == null || !e.getKey().equals(k.key)) if (e.getValue() >= 8f) { Row a = ROWS.get(e.getKey()); if (a != null) a.assists++; }
        JsonObject o = new JsonObject();
        String weapon = "";
        if (killer instanceof LivingEntity lk) weapon = Registries.ITEM.getId(lk.getMainHandStack().getItem()).getPath();
        if (lh != null && k != null && lh.attacker.equals(k.key)) weapon = lh.weapon;
        o.addProperty("w", weapon);
        o.addProperty("hs", hs);
        return o;
    }

    /** MVP раунда: заложивший (Т, взрыв) / разминировавший (КТ) или лучший по убийствам у победителей */
    static String mvp(Team winner, boolean bombWin) {
        String best = null;
        if (bombWin) best = winner == Team.T ? planter : defuser;
        if (best == null) {
            int bk = 0;
            for (Row r : ROWS.values()) if (r.team == winner && r.roundKills > bk) { bk = r.roundKills; best = r.key; }
        }
        if (best != null && ROWS.containsKey(best)) ROWS.get(best).mvps++;
        return best == null || !ROWS.containsKey(best) ? null : ROWS.get(best).name;
    }

    static void swapSides() { for (Row r : ROWS.values()) if (r.team != null) r.team = r.team.opposite(); }

    /** Таблица для Tab */
    static JsonArray table(Map<String, Boolean> alive, Map<String, Integer> money, String me) {
        List<Row> l = new ArrayList<>(ROWS.values());
        l.sort(Comparator.comparingInt((Row r) -> r.team == Team.CT ? 0 : 1).thenComparingInt(r -> -r.score()));
        JsonArray a = new JsonArray();
        for (Row r : l) {
            if (r.team == null) continue;
            JsonObject o = new JsonObject();
            o.addProperty("n", r.name); o.addProperty("t", r.team == Team.T ? "t" : "ct"); o.addProperty("bot", r.bot);
            o.addProperty("k", r.kills); o.addProperty("d", r.deaths); o.addProperty("a", r.assists);
            o.addProperty("dmg", Math.round(r.damage * 5));                 // в «процентах здоровья», как в CS (20 HP = 100)
            o.addProperty("hs", r.headshots); o.addProperty("mvp", r.mvps); o.addProperty("sc", r.score());
            o.addProperty("alive", alive.getOrDefault(r.key, false));
            if (money.containsKey(r.key)) o.addProperty("$", money.get(r.key));
            o.addProperty("me", r.key.equals(me));
            a.add(o);
        }
        return a;
    }
}
