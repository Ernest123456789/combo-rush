package com.derevna230.comborush.leaderboard;

import com.derevna230.comborush.ComboRush;
import com.derevna230.comborush.data.PlayerProfile;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.WorldSavePath;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Общий рейтинг сервера. Хранится в файле мира combo-rush-leaderboard.json (видны и офлайн-игроки) */
public class Leaderboard {
    public static class Entry {
        public String name = "?";
        public long best = 0, score = 0, kills = 0, events = 0;
        public int ranks = 0, resets = 0;
    }

    public static final List<String> BOARDS = List.of("best", "score", "ranks", "kills", "events");

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<String, Entry> DATA = new HashMap<>();
    private static Path file;
    private static boolean dirty = false;

    public static void init() {
        ServerLifecycleEvents.SERVER_STARTED.register(Leaderboard::load);
        ServerLifecycleEvents.SERVER_STOPPING.register(s -> save());
    }

    public static void tick(MinecraftServer server) {
        if (dirty && server.getTicks() % 600 == 0) save();
    }

    private static void load(MinecraftServer server) {
        file = server.getSavePath(WorldSavePath.ROOT).resolve("combo-rush-leaderboard.json");
        DATA.clear();
        try {
            if (Files.exists(file)) {
                Map<String, Entry> m = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8),
                    new TypeToken<Map<String, Entry>>() {}.getType());
                if (m != null) DATA.putAll(m);
            }
        } catch (Exception e) {
            ComboRush.LOGGER.error("Не удалось прочитать таблицу лидеров", e);
        }
    }

    private static void save() {
        if (file == null) return;
        try {
            Files.writeString(file, GSON.toJson(DATA), StandardCharsets.UTF_8);
            dirty = false;
        } catch (Exception e) {
            ComboRush.LOGGER.error("Не удалось сохранить таблицу лидеров", e);
        }
    }

    /** Обновить запись игрока из его профиля */
    public static void update(ServerPlayerEntity p) {
        PlayerProfile pr = PlayerProfile.of(p);
        Entry e = new Entry();
        e.name = p.getName().getString();
        e.best = pr.statLong("best_event_score");
        e.score = pr.statLong("life_score");
        e.kills = pr.statLong("life_kills");
        e.events = pr.statLong("life_events");
        e.ranks = pr.ranks().values().stream().mapToInt(Integer::intValue).sum();
        e.resets = pr.resets();
        DATA.put(p.getUuid().toString(), e);
        dirty = true;
    }

    private static long metric(Entry e, String board) {
        return switch (board) {
            case "best"   -> e.best;
            case "score"  -> e.score;
            case "ranks"  -> e.ranks;
            case "kills"  -> e.kills;
            case "events" -> e.events;
            default       -> 0;
        };
    }

    private static String title(String board) {
        return switch (board) {
            case "best"   -> "лучший результат за событие";
            case "score"  -> "очки за всё время";
            case "ranks"  -> "сумма рангов";
            case "kills"  -> "убито мобов";
            case "events" -> "событий пройдено";
            default       -> board;
        };
    }

    /** Названия для подсказок: английские и русские */
    public static final List<String> BOARD_NAMES = List.of("best", "score", "ranks", "kills", "events",
        "рекорд", "очки", "ранги", "убийства", "события");

    private static String normalize(String s) {
        String b = s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
        return switch (b) {
            case "рекорд", "лучшее", "лучший" -> "best";
            case "очки" -> "score";
            case "ранги", "ранг" -> "ranks";
            case "убийства", "киллы" -> "kills";
            case "события", "событий" -> "events";
            default -> b;
        };
    }

    private static String shortTitle(String board) {
        return switch (board) {
            case "best" -> "Рекорд"; case "score" -> "Очки"; case "ranks" -> "Ранги";
            case "kills" -> "Убийства"; default -> "События";
        };
    }

    public static void send(ServerCommandSource src, String requested) {
        String board = normalize(requested);
        if (!BOARDS.contains(board)) {
            src.sendFeedback(() -> Text.literal("§cТаблицы: " + String.join(", ", BOARD_NAMES)), false);
            return;
        }
        // свежие данные у игроков онлайн
        if (src.getServer() != null)
            for (ServerPlayerEntity online : src.getServer().getPlayerManager().getPlayerList()) update(online);

        List<Map.Entry<String, Entry>> list = new ArrayList<>();
        for (var en : DATA.entrySet()) if (metric(en.getValue(), board) > 0) list.add(en);
        list.sort((a, b) -> {
            int c = Long.compare(metric(b.getValue(), board), metric(a.getValue(), board));
            return c != 0 ? c : a.getValue().name.compareToIgnoreCase(b.getValue().name);
        });

        src.sendFeedback(() -> Text.literal("§6§l=== Топ: " + title(board) + " ==="), false);
        MutableText tabs = Text.literal("§7Таблицы: ");
        for (String b : BOARDS) {
            tabs.append(Text.literal((b.equals(board) ? "§e§l[" : "§b[") + shortTitle(b) + "] ").styled(st -> st
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/cr top " + b))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal(title(b))))));
        }
        src.sendFeedback(() -> tabs, false);
        if (list.isEmpty()) src.sendFeedback(() -> Text.literal("§7Пока пусто."), false);

        String me = src.getPlayer() == null ? "" : src.getPlayer().getUuid().toString();
        for (int i = 0; i < list.size() && i < 10; i++) {
            Entry e = list.get(i).getValue();
            String color = i == 0 ? "§6" : i == 1 ? "§f" : i == 2 ? "§c" : "§7";
            String line = color + (i + 1) + ". " + (list.get(i).getKey().equals(me) ? "§a" : "§f") + e.name
                + " §7— §e" + metric(e, board) + (board.equals("ranks") ? " §8(сбросов: " + e.resets + ")" : "");
            src.sendFeedback(() -> Text.literal(line), false);
        }
        for (int i = 10; i < list.size(); i++) {
            if (list.get(i).getKey().equals(me)) {
                String line = "§7..." + "\n§a" + (i + 1) + ". " + list.get(i).getValue().name
                    + " §7— §e" + metric(list.get(i).getValue(), board);
                src.sendFeedback(() -> Text.literal(line), false);
                break;
            }
        }
    }
}
