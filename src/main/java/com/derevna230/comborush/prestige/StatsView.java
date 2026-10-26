package com.derevna230.comborush.prestige;

import com.derevna230.comborush.data.PlayerProfile;
import com.derevna230.comborush.species.SpeciesDef;
import com.derevna230.comborush.species.SpeciesRegistry;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

public class StatsView {
    public static void send(ServerCommandSource src, ServerPlayerEntity target) {
        PlayerProfile p = PlayerProfile.of(target);
        SpeciesDef sp = SpeciesRegistry.get(p.species());

        List<String> lines = new ArrayList<>();
        lines.add("§6§l=== Статистика: " + target.getName().getString() + " ===");
        lines.add("§eСущество: §f" + sp.name + " §7(ранг " + rankText(p.rank()) + "§7)");

        StringBuilder ranks = new StringBuilder("§eРанги: ");
        boolean first = true;
        for (SpeciesDef d : SpeciesRegistry.all()) {
            if (!first) ranks.append("§7, ");
            first = false;
            ranks.append("§f").append(d.name).append(" ").append(rankText(p.rankOf(d.id)));
        }
        lines.add(ranks.toString());

        lines.add("§eСбросов: §f" + p.resets() + "  §eЭссенция: §d" + p.essence());
        lines.add("§6Текущий забег:");
        lines.add("§7  События: §f" + p.eventsDone() + "/" + PrestigeManager.REQUIRED_EVENTS
            + "  §7Уровень: §f" + p.level() + "  §7Монеты: §f" + p.coins());
        lines.add("§7  Очки: §f" + p.totalScore() + "  §7Усилители: §f" + p.boosters().size() + "/" + p.slotCapacity());
        lines.add("§7  Убито: §f" + p.statLong("kills") + "  §7Руд: §f" + p.statLong("ores")
            + "  §7Урожая: §f" + p.statLong("crops") + "  §7Золотых медалей: §f" + p.statLong("gold_medals"));
        lines.add("§7  Выстрелов: §f" + p.statLong("shots") + "  §7Зелий: §f" + p.statLong("brews")
            + "  §7Зачарований: §f" + p.statLong("enchants") + "  §7Разведений: §f" + p.statLong("breeds")
            + "  §7Сделок: §f" + p.statLong("trades") + "  §7Рыбы: §f" + p.statLong("fish"));
        lines.add("§6За всё время:");
        lines.add("§7  Событий: §f" + p.statLong("life_events") + "  §7Лучший результат: §f" + p.statLong("best_event_score"));
        lines.add("§7  Убито: §f" + p.statLong("life_kills") + "  §7Руд: §f" + p.statLong("life_ores")
            + "  §7Урожая: §f" + p.statLong("life_crops"));

        lines.add("§7  Выстрелов: §f" + p.statLong("life_shots") + "  §7Зелий: §f" + p.statLong("life_brews")
            + "  §7Зачарований: §f" + p.statLong("life_enchants") + "  §7Разведений: §f" + p.statLong("life_breeds")
            + "  §7Сделок: §f" + p.statLong("life_trades") + "  §7Рыбы: §f" + p.statLong("life_fish"));
        lines.add("§7  Косметики куплено: §f" + p.cosmetics().size());

        for (String line : lines) src.sendFeedback(() -> Text.literal(line), false);
    }

    private static String rankText(int rank) {
        return (rank >= PrestigeManager.MAX_RANK ? "§6§l" : rank > 0 ? "§a" : "§8") + rank + "/" + PrestigeManager.MAX_RANK;
    }
}
