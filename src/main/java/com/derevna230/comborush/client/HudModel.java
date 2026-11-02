package com.derevna230.comborush.client;

import com.derevna230.comborush.armor.ArmorTooltips;
import com.derevna230.comborush.hud.HudPayload;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.Locale;

/** Всё состояние HUD на клиенте и логика анимаций подсчёта (как «рука» в Balatro) */
public final class HudModel {
    private HudModel() {}

    // ---------- настройки ----------
    public static boolean enabled = true;
    public static boolean sound = true;

    // ---------- карточки усилителей ----------
    public static final class Tag { public String kind = ""; public String text = ""; }
    public static final class Card {
        public String id = "", name = "", desc = "", icon = "minecraft:paper", cat = "chips";
        public String rar = "common", ed = "";
        public int lvl = 0;
        public boolean neg, active = true;
        public List<Tag> tags = new ArrayList<>();
        public ItemStack stack = ItemStack.EMPTY;
        public long jiggleAt = -100000;
        public float jiggleAmp = 1f;
    }
    public static final List<Card> cards = new ArrayList<>();
    public static int slots = 3;

    // ---------- состояние цикла ----------
    public static String phase = "NORMAL", eventName = "", species = "", promo = "", boss = "";
    public static boolean blind = false;
    public static int secs = 0, coins = 0, level = 1, rank = 0;
    public static long secsAt = 0;

    // ---------- счёт ----------
    public static double shownTotal = 0;
    public static long targetTotal = 0;
    public static long lastEventTotal = 0;
    public static double chipsShown = 0, multShown = 1;
    public static String handLabel = "";
    public static long chipsBounce = -100000, multBounce = -100000, totalBounce = -100000;
    public static long totalShakeAt = -100000;
    public static float totalShakeAmp = 0;
    public static long idleSince = 0;

    // ---------- серия (комбо) ----------
    public static long comboAt = 0;
    public static int comboWinMs = 0;
    public static double comboMult = 1;

    // ---------- баннер ----------
    public static String bannerKind = "";   // "start" / "end"
    public static long bannerAt = -100000;

    // ---------- всплывающие числа ----------
    public static final class Floating {
        public String text; public int rgb; public int card = -1; public String box = "";
        public long born; public float scale = 1f;
    }
    public static final List<Floating> floats = new ArrayList<>();

    // ---------- очередь анимаций ----------
    private static final class StepData { int i; String k; double v; String l = ""; }
    private static final class Pop {
        String src; double base, chips, m0, mult, gain; long total; int win; List<StepData> steps = new ArrayList<>();
    }
    private static final class Anim {
        Pop pop; long start; double speed; int nextStep = 0; boolean finalDone, gainDone;
        double chipsRun, multRun;
    }
    private static final Deque<Pop> queue = new ArrayDeque<>();
    private static Anim cur = null;

    public static boolean animating() { return cur != null; }

    public static void reset() {
        cards.clear(); cons.clear(); slots = 3; queue.clear(); cur = null; floats.clear();
        result = null; lastResult = null; flies.clear();
        phase = "NORMAL"; shownTotal = 0; targetTotal = 0; lastEventTotal = 0;
        chipsShown = 0; multShown = 1; eventName = ""; comboWinMs = 0;
    }

    // ---------- расходники в HUD ----------
    public static final class Cons {
        public String id = "", name = "", desc = "", icon = "minecraft:paper", rar = "common";
        public ItemStack stack = ItemStack.EMPTY;
    }
    public static final List<Cons> cons = new ArrayList<>();

    // ---------- экран итогов события ----------
    public static final class Row { public String key = ""; public double points; public int count; }
    public static final class Result {
        public int number; public long score;
        public String medal = "NONE", medalLabel = "", boss = "", consumable = "", event = "", nextName = "";
        public double[] thr = new double[4];
        public double next;
        public int pure, bonus, interest, total;
        public boolean bossWin;
        public List<Row> rows = new ArrayList<>();
    }
    public static final class Fly { public long start, dur; public boolean landed; public float rnd; }

    public static final double RESULT_LIFE = 13500, T_MEDAL = 2800, T_COINS = 3500;
    public static Result result = null, lastResult = null;
    public static long resultAt = 0, coinBounceAt = -100000;
    public static final List<Fly> flies = new ArrayList<>();
    private static boolean medalSnd, coinsSpawned;

    /** Показать последние итоги ещё раз (/crhud results) */
    public static boolean showLastResult() {
        if (lastResult == null) return false;
        startResult(lastResult);
        return true;
    }

    private static void startResult(Result r) {
        result = r; lastResult = r;
        resultAt = System.nanoTime();
        medalSnd = false; coinsSpawned = false;
        flies.clear();
    }

    private static void readResult(JsonObject o) {
        Result r = new Result();
        r.number = o.get("n").getAsInt();
        r.score = o.get("score").getAsLong();
        r.medal = str(o, "medal", "NONE"); r.medalLabel = str(o, "medalLabel", "");
        JsonArray th = o.getAsJsonArray("thr");
        for (int i = 0; i < 4 && i < th.size(); i++) r.thr[i] = th.get(i).getAsDouble();
        r.next = o.get("next").getAsDouble(); r.nextName = str(o, "nextName", "");
        r.pure = o.get("pure").getAsInt(); r.bonus = o.get("bonus").getAsInt();
        r.interest = o.get("interest").getAsInt(); r.total = o.get("total").getAsInt();
        r.boss = str(o, "boss", ""); r.bossWin = o.has("bossWin") && o.get("bossWin").getAsBoolean();
        r.consumable = str(o, "cons", ""); r.event = str(o, "event", "");
        for (JsonElement el : o.getAsJsonArray("rows")) {
            JsonObject ro = el.getAsJsonObject();
            Row row = new Row();
            row.key = str(ro, "k", ""); row.points = ro.get("p").getAsDouble(); row.count = ro.get("n").getAsInt();
            r.rows.add(row);
        }
        startResult(r);
    }

    /** Звуки и «полёт» монет по расписанию (позиции считает отрисовка) */
    private static void updateResult(long now) {
        if (result == null) return;
        double t = (now - resultAt) / 1e6;
        if (!medalSnd && t >= T_MEDAL) {
            medalSnd = true;
            switch (result.medal) {
                case "BRONZE"   -> play(com.derevna230.comborush.sound.ModSounds.MEDAL_BRONZE, 1.0f, 0.7f);
                case "SILVER"   -> play(com.derevna230.comborush.sound.ModSounds.MEDAL_SILVER, 1.0f, 0.75f);
                case "GOLD"     -> play(com.derevna230.comborush.sound.ModSounds.MEDAL_GOLD, 1.0f, 0.8f);
                case "PLATINUM" -> play(com.derevna230.comborush.sound.ModSounds.MEDAL_PLATINUM, 1.0f, 0.85f);
                default         -> play(com.derevna230.comborush.sound.ModSounds.MEDAL_NONE, 1.0f, 0.6f);
            }
        }
        if (!coinsSpawned && t >= T_COINS) {
            coinsSpawned = true;
            int n = result.total <= 0 ? 0 : Math.min(14, Math.max(3, result.total / 4));
            for (int i = 0; i < n; i++) {
                Fly f = new Fly();
                f.start = resultAt + (long) ((T_COINS + i * 85.0) * 1e6);
                f.dur = 800_000_000L;
                f.rnd = (float) Math.random();
                flies.add(f);
            }
        }
        for (int i = 0; i < flies.size(); i++) {
            Fly f = flies.get(i);
            if (!f.landed && now >= f.start + f.dur) {
                f.landed = true;
                coinBounceAt = now;
                play(com.derevna230.comborush.sound.ModSounds.COIN, 0.9f + 0.04f * Math.min(i, 14), 0.3f);
            }
        }
        if (t > RESULT_LIFE) { result = null; flies.clear(); }
    }

    // ================= приём пакетов =================
    /** Открытые карманные станки (кнопки справа от инвентаря): pocket_crafting, pocket_furnace… */
    public static volatile Set<String> pockets = Set.of();

    public static void receive(HudPayload p) {
        try {
            JsonObject o = JsonParser.parseString(p.json()).getAsJsonObject();
            switch (p.kind()) {
                case "boosters" -> readBoosters(o);
                case "state"    -> readState(o);
                case "pop"      -> readPop(o);
                case "result"   -> readResult(o);
                case "shop_open", "shop", "shop_close" -> ShopScreen.onPacket(p.kind(), o);
                case "species_open", "species" -> SpeciesScreen.onPacket(p.kind(), o);
                case "eshop_open", "eshop" -> EssenceShopScreen.onPacket(p.kind(), o);
                case "case_open" -> CaseOpenScreen.open(o);
                case "skate_open", "skate" -> SkateScreen.onPacket(p.kind(), o);
                case "camp_open", "camp" -> JournalScreen.onPacket(p.kind(), o);
                case "camp_track" -> StoryHud.readTrack(o);
                case "camp_say" -> StoryHud.readSay(o);
                case "camp_choice" -> ChoiceScreen.open();
                case "ws_open", "ws" -> WorkshopScreen.onPacket(p.kind(), o);
                case "trader_open", "trader" -> TraderScreen.onPacket(p.kind(), o);
                case "cosm_open", "cosm" -> CosmeticsScreen.onPacket(p.kind(), o);
                case "armor_info" -> ArmorTooltips.read(o);
                default -> { }
            }
        } catch (Exception ignored) { /* битый пакет не должен ронять клиент */ }
    }

    private static String str(JsonObject o, String k, String d) { return o.has(k) ? o.get(k).getAsString() : d; }

    private static void readBoosters(JsonObject o) {
        long now = System.nanoTime();
        List<Card> old = new ArrayList<>(cards);
        cards.clear();
        for (JsonElement el : o.getAsJsonArray("list")) {
            JsonObject b = el.getAsJsonObject();
            Card c = new Card();
            c.id = str(b, "id", ""); c.name = str(b, "name", "?"); c.desc = str(b, "desc", "");
            c.icon = str(b, "icon", "minecraft:paper"); c.cat = str(b, "cat", "chips");
            c.neg = b.has("neg") && b.get("neg").getAsBoolean();
            c.rar = str(b, "rar", "common"); c.ed = str(b, "ed", "");
            c.lvl = b.has("lvl") ? b.get("lvl").getAsInt() : 0;
            c.active = !b.has("active") || b.get("active").getAsBoolean();
            for (JsonElement te : b.getAsJsonArray("tags")) {
                JsonObject t = te.getAsJsonObject();
                Tag tg = new Tag(); tg.kind = str(t, "k", ""); tg.text = str(t, "t", "");
                c.tags.add(tg);
            }
            Identifier id = Identifier.tryParse(c.icon);
            c.stack = id != null && Registries.ITEM.containsId(id) ? new ItemStack(Registries.ITEM.get(id)) : ItemStack.EMPTY;
            // новая карточка «вылетает» с покачиванием
            boolean known = old.stream().anyMatch(x -> x.id.equals(c.id));
            if (!known) { c.jiggleAt = now; c.jiggleAmp = 1.8f; }
            else old.stream().filter(x -> x.id.equals(c.id)).findFirst().ifPresent(x -> { c.jiggleAt = x.jiggleAt; c.jiggleAmp = x.jiggleAmp; });
            cards.add(c);
        }
        slots = o.has("slots") ? o.get("slots").getAsInt() : 3;
        cons.clear();
        if (o.has("cons")) {
            for (JsonElement el : o.getAsJsonArray("cons")) {
                JsonObject co = el.getAsJsonObject();
                Cons cc = new Cons();
                cc.id = str(co, "id", ""); cc.name = str(co, "name", "?"); cc.desc = str(co, "desc", "");
                cc.icon = str(co, "icon", "minecraft:paper"); cc.rar = str(co, "rar", "common");
                Identifier cid = Identifier.tryParse(cc.icon);
                cc.stack = cid != null && Registries.ITEM.containsId(cid) ? new ItemStack(Registries.ITEM.get(cid)) : ItemStack.EMPTY;
                cons.add(cc);
            }
        }
    }

    private static void readState(JsonObject o) {
        long now = System.nanoTime();
        String newPhase = str(o, "phase", "NORMAL");
        if (!newPhase.equals(phase)) {
            if (newPhase.equals("EVENT")) {
                bannerKind = "start"; bannerAt = now;
                shownTotal = 0; targetTotal = 0; chipsShown = 0; multShown = 1;
            } else {
                lastEventTotal = targetTotal;
                bannerKind = "end"; bannerAt = now;
            }
            phase = newPhase;
        }
        secs = o.get("secs").getAsInt();
        secsAt = now;
        coins = o.get("coins").getAsInt();
        eventName = str(o, "event", "");
        species = str(o, "species", "");
        promo = str(o, "promo", "");
        boss = str(o, "boss", "");
        blind = o.has("blind") && o.get("blind").getAsBoolean();
        level = o.has("level") ? o.get("level").getAsInt() : 1;
        rank = o.has("rank") ? o.get("rank").getAsInt() : 0;
        if (o.has("pockets")) {
            Set<String> pk = new HashSet<>();
            for (JsonElement el : o.getAsJsonArray("pockets")) pk.add(el.getAsString());
            pockets = pk;
        }
        // синхронизация счёта (например, после бонусных очков), если анимаций нет
        long sv = o.has("score") ? o.get("score").getAsLong() : 0;
        if (phase.equals("EVENT") && cur == null && queue.isEmpty() && sv > targetTotal) targetTotal = sv;
    }

    private static void readPop(JsonObject o) {
        Pop p = new Pop();
        p.src = str(o, "src", "KILL");
        p.base = o.get("base").getAsDouble(); p.chips = o.get("chips").getAsDouble();
        p.m0 = o.get("m0").getAsDouble(); p.mult = o.get("mult").getAsDouble();
        p.gain = o.get("gain").getAsDouble(); p.total = o.get("total").getAsLong();
        p.win = o.get("win").getAsInt();
        for (JsonElement el : o.getAsJsonArray("steps")) {
            JsonObject s = el.getAsJsonObject();
            StepData d = new StepData();
            d.i = s.get("i").getAsInt(); d.k = s.get("k").getAsString(); d.v = s.get("v").getAsDouble();
            d.l = str(s, "l", "");
            p.steps.add(d);
        }
        queue.addLast(p);
        while (queue.size() > 40) applyInstant(queue.pollFirst());
    }

    // ================= анимация =================
    private static final double T0 = 120, STEP = 140, GAP_FINAL = 50, GAP_GAIN = 110, HOLD = 260;

    public static String srcLabel(String s) {
        return switch (s) {
            case "KILL" -> "Убийство"; case "MINE" -> "Руда"; case "HARVEST" -> "Урожай";
            case "SHOOT" -> "Выстрел"; case "BREW" -> "Зелье"; case "ENCHANT" -> "Зачарование";
            case "BREED" -> "Разведение"; case "TRADE" -> "Сделка"; case "FISH" -> "Улов";
            default -> s;
        };
    }

    /** Покадровое обновление: берёт следующее событие из очереди и двигает таймлайн */
    public static void update(long now) {
        updateResult(now);
        while (cur == null && !queue.isEmpty()) {
            Pop p = queue.pollFirst();
            int backlog = queue.size();
            if (backlog > 6) { applyInstant(p); continue; }       // слишком много — не тормозим игру
            Anim a = new Anim();
            a.pop = p; a.start = now; a.speed = 1 + 0.7 * Math.min(backlog, 4);
            a.chipsRun = p.base; a.multRun = p.m0;
            chipsShown = p.base; multShown = p.m0;
            handLabel = srcLabel(p.src);
            comboMult = p.mult; comboAt = now; comboWinMs = p.win * 50;
            chipsBounce = now; multBounce = now;
            cur = a;
        }
        if (cur != null) {
            Anim a = cur;
            double el = (now - a.start) / 1e6 * a.speed;
            int n = a.pop.steps.size();
            while (a.nextStep < n && el >= T0 + a.nextStep * STEP) applyStep(a, a.nextStep++, now);

            double tFinal = T0 + n * STEP + GAP_FINAL;
            if (!a.finalDone && el >= tFinal) {
                a.finalDone = true;
                chipsShown = a.pop.chips; multShown = a.pop.mult;
                chipsBounce = now; multBounce = now;
                play(com.derevna230.comborush.sound.ModSounds.MULT, 1.0f + 0.05f * Math.min(n, 8), 0.5f);
            }
            double tGain = tFinal + GAP_GAIN;
            if (!a.gainDone && el >= tGain) {
                a.gainDone = true;
                targetTotal = Math.max(targetTotal, a.pop.total);
                totalBounce = now;
                totalShakeAt = now;
                totalShakeAmp = (float) Math.min(4.0, 0.8 + Math.log10(a.pop.gain + 1));
                addFloat("+" + fmtInt(Math.round(a.pop.gain)), 0xFFE066, -1, "total", 1.4f, now);
                play(com.derevna230.comborush.sound.ModSounds.COIN, 1.0f, 0.45f);
            }
            if (el >= tGain + HOLD) { cur = null; idleSince = now; }
        }
        floats.removeIf(f -> (now - f.born) / 1e6 > 950);
    }

    private static void applyInstant(Pop p) {
        targetTotal = Math.max(targetTotal, p.total);
        comboMult = p.mult; comboAt = System.nanoTime(); comboWinMs = p.win * 50;
    }

    private static void applyStep(Anim a, int idx, long now) {
        StepData s = a.pop.steps.get(idx);
        boolean chipsSide = s.k.equals("chips") || s.k.equals("chipsx");
        switch (s.k) {
            case "chips"  -> a.chipsRun += s.v;
            case "chipsx" -> a.chipsRun *= s.v;
            case "mult"   -> a.multRun += s.v;
            case "xmult"  -> a.multRun *= s.v;
            default -> { }
        }
        chipsShown = a.chipsRun; multShown = a.multRun;
        if (chipsSide) chipsBounce = now; else multBounce = now;

        boolean times = s.k.endsWith("x");
        String t = (times ? "×" : "+") + trim(s.v) + (s.l.isEmpty() ? "" : " " + s.l);
        int color = chipsSide ? 0x6FA8FF : 0xFF6B6B;
        if (s.i >= 0 && s.i < cards.size()) {
            Card c = cards.get(s.i);
            c.jiggleAt = now; c.jiggleAmp = times ? 1.6f : 1.0f;
            addFloat(t, color, s.i, "", 1.0f, now);
        } else {
            addFloat(t, color, -1, chipsSide ? "chips" : "mult", 0.85f, now);
        }
        // своя «лесенка» звуков: каждая следующая строка подсчёта выше на полтона
        float step = (float) Math.pow(2, Math.min(idx, 14) / 12.0);
        if (s.k.equals("xmult")) { play(com.derevna230.comborush.sound.ModSounds.MULT, 0.75f * step, 0.55f); play(com.derevna230.comborush.sound.ModSounds.MULT, 1.12f * step, 0.3f); }
        else if (chipsSide)      play(com.derevna230.comborush.sound.ModSounds.CHIPS, 0.7f * step, 0.35f);
        else                     play(com.derevna230.comborush.sound.ModSounds.MULT, 0.75f * step, 0.4f);
    }

    private static void addFloat(String text, int rgb, int card, String box, float scale, long now) {
        Floating f = new Floating();
        f.text = text; f.rgb = rgb; f.card = card; f.box = box; f.scale = scale; f.born = now;
        floats.add(f);
        if (floats.size() > 40) floats.remove(0);
    }

    /** Принимает и SoundEvent, и RegistryEntry<SoundEvent> — в разных версиях констант SoundEvents тип отличается */
    public static void play(Object raw, float pitch, float vol) {
        if (!sound) return;
        SoundEvent ev = raw instanceof SoundEvent se ? se
            : (raw instanceof RegistryEntry<?> re && re.value() instanceof SoundEvent se2 ? se2 : null);
        if (ev == null) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null && mc.getSoundManager() != null)
            mc.getSoundManager().play(PositionedSoundInstance.master(ev, Math.min(2f, pitch), vol));
    }

    // ================= форматирование =================
    public static String trim(double v) {
        if (Math.abs(v - Math.round(v)) < 1e-9) return Long.toString(Math.round(v));
        String s = String.format(Locale.ROOT, "%.2f", v);
        if (s.endsWith("0")) s = s.substring(0, s.length() - 1);
        return s;
    }

    public static String fmtInt(long v) {
        String s = Long.toString(Math.abs(v));
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            if (i > 0 && (s.length() - i) % 3 == 0) b.append(' ');
            b.append(s.charAt(i));
        }
        return (v < 0 ? "-" : "") + b;
    }
}
