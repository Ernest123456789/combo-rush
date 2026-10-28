package com.derevna230.comborush.score;

import com.derevna230.comborush.ComboRush;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.WorldSavePath;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Статистика для баланса: строка на каждого игрока после каждого события в <мир>/combo-rush-stats.csv
 * (открывается в Excel/Google Таблицах). /cr balance — сводка: как часто берут медали на разных этапах забега.
 */
public final class BalanceStats {
    private BalanceStats() {}

    private static final String HEADER = "time;player;event;score;bronze;ratio;medal;species;director;boss;boosters;resets\n";

    private static Path file(MinecraftServer server) { return server.getSavePath(WorldSavePath.ROOT).resolve("combo-rush-stats.csv"); }

    public static void record(MinecraftServer server, String player, int event, long score, double bronze, String medal,
                              String species, String director, String boss, int boosters, int resets) {
        try {
            Path f = file(server);
            if (!Files.exists(f)) Files.writeString(f, HEADER, StandardCharsets.UTF_8);
            String line = String.join(";",
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")),
                player, String.valueOf(event), String.valueOf(score), String.valueOf(Math.round(bronze)),
                String.format(Locale.ROOT, "%.2f", bronze > 0 ? score / bronze : 0),
                medal, species, clean(director), clean(boss), String.valueOf(boosters), String.valueOf(resets)) + "\n";
            Files.writeString(f, line, StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        } catch (Exception e) {
            ComboRush.LOGGER.warn("Не удалось записать статистику баланса: {}", e.toString());
        }
    }

    private static String clean(String s) { return s == null ? "" : s.replace(';', ','); }

    /** Сводка по этапам забега (по 5 событий): сколько событий, медиана «очки/порог бронзы», доля медалей */
    public static void report(ServerCommandSource src) {
        MinecraftServer server = src.getServer();
        List<String[]> rows = new ArrayList<>();
        try {
            Path f = file(server);
            if (!Files.exists(f)) { src.sendFeedback(() -> Text.literal("§7Статистики пока нет — сыграйте пару событий."), false); return; }
            List<String> lines = Files.readAllLines(f, StandardCharsets.UTF_8);
            for (int i = 1; i < lines.size(); i++) {
                String[] p = lines.get(i).split(";", -1);
                if (p.length >= 7) rows.add(p);
            }
        } catch (Exception e) {
            src.sendFeedback(() -> Text.literal("§cНе удалось прочитать статистику: " + e.getMessage()), false);
            return;
        }
        if (rows.isEmpty()) { src.sendFeedback(() -> Text.literal("§7Статистики пока нет."), false); return; }

        TreeMap<Integer, List<String[]>> byStage = new TreeMap<>();
        for (String[] r : rows) {
            int ev;
            try { ev = Integer.parseInt(r[2]); } catch (NumberFormatException e) { continue; }
            byStage.computeIfAbsent((ev - 1) / 5, k -> new ArrayList<>()).add(r);
        }
        src.sendFeedback(() -> Text.literal("§6§lБаланс §7(" + rows.size() + " записей, файл combo-rush-stats.csv в папке мира)"), false);
        src.sendFeedback(() -> Text.literal("§7События │ игр │ очки/бронза │ нет · бр · сер · зол · плат"), false);
        for (var e : byStage.entrySet()) {
            List<String[]> rs = e.getValue();
            List<Double> ratios = new ArrayList<>();
            Map<String, Integer> medals = new HashMap<>();
            for (String[] r : rs) {
                try { ratios.add(Double.parseDouble(r[5])); } catch (NumberFormatException ignored) { }
                medals.merge(r[6], 1, Integer::sum);
            }
            Collections.sort(ratios);
            double med = ratios.isEmpty() ? 0 : ratios.get(ratios.size() / 2);
            int n = rs.size();
            String dist = pct(medals, "NONE", n) + " · " + pct(medals, "BRONZE", n) + " · " + pct(medals, "SILVER", n)
                + " · " + pct(medals, "GOLD", n) + " · " + pct(medals, "PLATINUM", n);
            int from = e.getKey() * 5 + 1;
            String col = med < 1 ? "§c" : med < 1.5 ? "§e" : med > 3 ? "§b" : "§a";
            String line = String.format(Locale.ROOT, "§f%2d–%-2d §7│ §f%3d §7│ %s%.2f §7│ §f%s", from, from + 4, n, col, med, dist);
            src.sendFeedback(() -> Text.literal(line), false);
        }
        src.sendFeedback(() -> Text.literal("§8Красный — большинство не берёт бронзу (слишком сложно), голубой — слишком легко."), false);
    }

    private static String pct(Map<String, Integer> m, String k, int n) {
        return Math.round(100.0 * m.getOrDefault(k, 0) / Math.max(1, n)) + "%";
    }
}
