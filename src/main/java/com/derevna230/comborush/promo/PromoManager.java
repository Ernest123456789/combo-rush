package com.derevna230.comborush.promo;

import com.derevna230.comborush.ComboRush;
import com.google.gson.Gson;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.resource.ResourceManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.time.LocalDate;
import java.time.MonthDay;
import java.util.*;
import java.util.stream.Collectors;

public class PromoManager implements SimpleSynchronousResourceReloadListener {
    private static final Map<String, PromoDef> ALL = new LinkedHashMap<>();
    private static final Set<String> FORCED = new HashSet<>();      // включены командой
    private static final Set<String> SUPPRESSED = new HashSet<>();  // выключены командой
    private static Set<String> active = null;                       // null — ещё не инициализировано
    private static final Gson GSON = new Gson();

    private static double shopDiscount = 0, scoreMult = 1, coinBonus = 0, sellBonus = 0;
    private static int rerollDiscount = 0;

    private static final ServerBossBar BAR =
        new ServerBossBar(Text.empty(), BossBar.Color.PURPLE, BossBar.Style.PROGRESS);

    // ---------- геттеры для остальной логики ----------
    public static double shopDiscount() { return shopDiscount; }
    public static double scoreMult() { return scoreMult; }
    public static double coinBonus() { return coinBonus; }
    public static double sellBonus() { return sellBonus; }
    public static int rerollDiscount() { return rerollDiscount; }

    public static boolean isActive(String id) { return active != null && active.contains(id); }
    public static Collection<String> ids() { return ALL.keySet(); }
    public static PromoDef get(String id) { return ALL.get(id); }
    public static List<String> activeNames() {
        if (active == null) return List.of();
        return active.stream().map(i -> ALL.get(i)).filter(Objects::nonNull).map(d -> d.name).collect(Collectors.toList());
    }

    // ---------- загрузка ----------
    @Override public Identifier getFabricId() { return Identifier.of(ComboRush.MOD_ID, "promos"); }

    @Override
    public void reload(ResourceManager manager) {
        ALL.clear();
        for (var entry : manager.findResources("promos", id -> id.getPath().endsWith(".json")).entrySet()) {
            Identifier file = entry.getKey();
            try (var reader = entry.getValue().getReader()) {
                PromoDef def = GSON.fromJson(reader, PromoDef.class);
                String p = file.getPath();
                def.id = p.substring("promos/".length(), p.length() - ".json".length());
                ALL.put(def.id, def);
            } catch (Exception e) {
                ComboRush.LOGGER.error("Не удалось загрузить акцию {}", file, e);
            }
        }
        ComboRush.LOGGER.info("Загружено акций: {}", ALL.size());
    }

    // ---------- проверка дат ----------
    private static boolean matches(PromoDef d, LocalDate today) {
        if (!d.enabled) return false;
        boolean fullDates = !d.date_start.isEmpty() || !d.date_end.isEmpty();
        boolean yearly = !d.start.isEmpty() && !d.end.isEmpty();
        boolean days = !d.weekdays.isEmpty();
        if (!fullDates && !yearly && !days) return false; // без условий — только вручную

        try {
            if (days && d.weekdays.stream().noneMatch(w -> w.equalsIgnoreCase(today.getDayOfWeek().name())))
                return false;
            if (fullDates) {
                LocalDate s = d.date_start.isEmpty() ? LocalDate.MIN : LocalDate.parse(d.date_start);
                LocalDate e = d.date_end.isEmpty() ? LocalDate.MAX : LocalDate.parse(d.date_end);
                return !today.isBefore(s) && !today.isAfter(e);
            }
            if (yearly) {
                MonthDay s = monthDay(d.start), e = monthDay(d.end), t = MonthDay.from(today);
                return s.isAfter(e) ? (!t.isBefore(s) || !t.isAfter(e))   // через Новый год
                                    : (!t.isBefore(s) && !t.isAfter(e));
            }
            return true; // только дни недели
        } catch (Exception ex) {
            ComboRush.LOGGER.error("Ошибка в датах акции {}", d.id, ex);
            return false;
        }
    }

    private static MonthDay monthDay(String s) {
        String[] a = s.split("-");
        return MonthDay.of(Integer.parseInt(a[0].trim()), Integer.parseInt(a[1].trim()));
    }

    private static Set<String> computeActive() {
        LocalDate today = LocalDate.now(); // дата сервера
        Set<String> now = new LinkedHashSet<>();
        for (PromoDef d : ALL.values()) {
            if (SUPPRESSED.contains(d.id)) continue;
            if (FORCED.contains(d.id) || matches(d, today)) now.add(d.id);
        }
        return now;
    }

    // ---------- обновление ----------
    public static void tick(MinecraftServer server) {
        if (server.getTicks() % 200 == 0) refresh(server);
    }

    public static void refresh(MinecraftServer server) {
        Set<String> now = computeActive();
        if (active != null) {
            for (String id : now) if (!active.contains(id)) announceStart(server, ALL.get(id));
            for (String id : active) if (!now.contains(id) && ALL.get(id) != null)
                server.getPlayerManager().broadcast(
                    Text.literal("§7Акция «" + ALL.get(id).name + "» завершилась."), false);
        }
        active = now;
        recompute();
        updateBar(server);
    }

    private static void announceStart(MinecraftServer server, PromoDef d) {
        if (d == null) return;
        server.getPlayerManager().broadcast(Text.literal("§d§l★ АКЦИЯ: " + d.name), false);
        if (!d.announce.isEmpty()) server.getPlayerManager().broadcast(Text.literal("§f" + d.announce), false);
    }

    private static void recompute() {
        shopDiscount = 0; scoreMult = 1; coinBonus = 0; sellBonus = 0; rerollDiscount = 0;
        for (String id : active) {
            PromoDef d = ALL.get(id);
            if (d == null) continue;
            for (PromoDef.Effect e : d.effects) {
                switch (e.type) {
                    case "shop_discount"    -> shopDiscount += e.value;
                    case "score_multiplier" -> scoreMult *= e.value;
                    case "coin_bonus"       -> coinBonus += e.value;
                    case "sell_bonus"       -> sellBonus += e.value;
                    case "reroll_discount"  -> rerollDiscount += (int) Math.round(e.value);
                    default -> { }
                }
            }
        }
    }

    private static void updateBar(MinecraftServer server) {
        List<String> names = activeNames();
        if (names.isEmpty()) { BAR.clearPlayers(); BAR.setVisible(false); return; }
        BAR.setName(Text.literal("§d★ Акции: §f" + String.join(", ", names)));
        BAR.setPercent(1f);
        BAR.setVisible(true);
        server.getPlayerManager().getPlayerList().forEach(BAR::addPlayer);
    }

    // ---------- события игроков ----------
    public static void onJoin(ServerPlayerEntity p) {
        List<String> names = activeNames();
        if (names.isEmpty()) return;
        BAR.addPlayer(p);
        p.sendMessage(Text.literal("§d★ Сейчас действуют акции: §f" + String.join(", ", names)), false);
    }
    public static void onDisconnect(ServerPlayerEntity p) { BAR.removePlayer(p); }

    // ---------- команды ----------
    public static boolean start(MinecraftServer server, String id) {
        if (!ALL.containsKey(id)) return false;
        SUPPRESSED.remove(id); FORCED.add(id);
        refresh(server);
        return true;
    }
    public static boolean stop(MinecraftServer server, String id) {
        if (!ALL.containsKey(id)) return false;
        FORCED.remove(id); SUPPRESSED.add(id);
        refresh(server);
        return true;
    }
    /** Вернуть автоматический режим (по датам) */
    public static boolean auto(MinecraftServer server, String id) {
        if (!ALL.containsKey(id)) return false;
        FORCED.remove(id); SUPPRESSED.remove(id);
        refresh(server);
        return true;
    }
}
