package com.derevna230.comborush.prestige;

import com.derevna230.comborush.booster.BoosterApplier;
import com.derevna230.comborush.cycle.CycleManager;
import com.derevna230.comborush.cycle.CyclePhase;
import com.derevna230.comborush.data.PlayerProfile;
import com.derevna230.comborush.leaderboard.Leaderboard;
import com.derevna230.comborush.shop.ShopGui;
import com.derevna230.comborush.species.SpeciesApplier;
import com.derevna230.comborush.species.SpeciesRegistry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PrestigeManager {
    public static final int REQUIRED_EVENTS = 50;
    public static final int MAX_RANK = 5;

    // Эссенция за сброс: ранг 1 → 15, 2 → 20, 3 → 25, 4 → 30, 5 → 35; при уже имеющемся 5 ранге → 40
    public static final int ESSENCE_BASE = 10;
    public static final int ESSENCE_PER_RANK = 5;
    public static final int ESSENCE_AT_MAX = 40;

    private static final int CONFIRM_TICKS = 30 * 20;
    private static final Map<UUID, Integer> PENDING = new HashMap<>();

    public static int newRank(int currentRank) { return Math.min(MAX_RANK, currentRank + 1); }

    public static int essenceFor(int currentRank) {
        return currentRank >= MAX_RANK ? ESSENCE_AT_MAX : ESSENCE_BASE + ESSENCE_PER_RANK * (currentRank + 1);
    }

    private static boolean allowed(ServerPlayerEntity p, PlayerProfile prof) {
        if (CycleManager.phase() != CyclePhase.NORMAL) {
            p.sendMessage(Text.literal("§cСброс недоступен во время события."), false);
            return false;
        }
        if (prof.eventsDone() < REQUIRED_EVENTS) {
            p.sendMessage(Text.literal("§cСброс доступен после " + REQUIRED_EVENTS
                + " событий (сейчас " + prof.eventsDone() + ")."), false);
            return false;
        }
        return true;
    }

    /** Шаг 1: показать, что будет, и попросить подтверждение */
    public static void request(ServerPlayerEntity p) {
        PlayerProfile prof = PlayerProfile.of(p);
        if (!allowed(p, prof)) return;

        int cur = prof.rank();
        String name = SpeciesRegistry.get(prof.species()).name;
        PENDING.put(p.getUuid(), p.getServer().getTicks());

        p.sendMessage(Text.literal("§6§l=== Сброс прогресса ==="), false);
        p.sendMessage(Text.literal("§cПотеряешь: §7монеты (" + prof.coins() + "), усилители ("
            + prof.boosters().size() + "), уровень " + prof.level() + ", доп. слоты и прогресс событий."), false);
        p.sendMessage(Text.literal("§aСохранится: §7ранги, эссенция, открытые усилители и существа, статистика."), false);
        p.sendMessage(Text.literal(cur >= MAX_RANK
            ? "§eНаграда: §7ранг «" + name + "» уже максимальный (" + MAX_RANK + "), бонус: §d+"
                + essenceFor(cur) + " эссенции"
            : "§eНаграда: §7ранг «" + name + "» " + cur + " → §f" + newRank(cur) + "§7, §d+"
                + essenceFor(cur) + " эссенции"), false);
        p.sendMessage(Text.literal("§fПодтверди командой §e/cr reset confirm §7(в течение 30 секунд)"), false);
    }

    /** Шаг 2: выполнить сброс */
    public static void confirm(ServerPlayerEntity p) {
        PlayerProfile prof = PlayerProfile.of(p);
        if (!allowed(p, prof)) return;

        Integer asked = PENDING.remove(p.getUuid());
        int now = p.getServer().getTicks();
        if (asked == null || now - asked > CONFIRM_TICKS) {
            p.sendMessage(Text.literal("§cСначала введи §e/cr reset §c— подтверждение действует 30 секунд."), false);
            return;
        }

        int cur = prof.rank();
        int rank = newRank(cur);
        int essence = essenceFor(cur);
        p.setAttached(PlayerProfile.TYPE, prof.afterReset(rank, essence));
        Leaderboard.update(p);
        ShopGui.invalidate(p.getUuid());
        BoosterApplier.sync(p);
        SpeciesApplier.sync(p);

        String name = SpeciesRegistry.get(prof.species()).name;
        p.getServer().getPlayerManager().broadcast(Text.literal("§d" + p.getName().getString()
            + " §fсбросил прогресс! Ранг «" + name + "»: §6" + rank + "/" + MAX_RANK), false);
        p.sendMessage(Text.literal("§aНовый забег начался. §7Можно выбрать существо: §e/species"), false);
    }
}
