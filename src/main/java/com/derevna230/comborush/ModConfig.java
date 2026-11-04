package com.derevna230.comborush;

import com.derevna230.comborush.cycle.CycleManager;
import com.derevna230.comborush.director.DirectorManager;
import com.derevna230.comborush.prestige.PrestigeManager;
import com.derevna230.comborush.rift.EliteMobs;
import com.derevna230.comborush.rift.RiftManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Настройки сервера: config/combo-rush.json. Файл создаётся при первом запуске со значениями по умолчанию.
 * После правки — /cr config reload (или перезапуск). Длительности применяются со следующей фазы цикла.
 */
public final class ModConfig {
    private ModConfig() {}

    /** Поля = ключи JSON. Пояснения — в README, раздел «Настройки сервера» */
    public static final class Values {
        public String _help = "Пояснения к каждому полю — в README мода, раздел «Настройки сервера». После правки: /cr config reload";
        public int event_minutes = 5;
        public int pause_minutes = 5;
        public int rifts_per_event = 2;
        public int rift_minutes = 4;
        public double elite_chance = 0.03;
        public double elite_chance_event = 0.08;
        public int boss_blind_every = 5;
        public int reset_after_events = 50;
        public double coin_multiplier = 1.0;
        public boolean campaign = true;
        public boolean trader_enabled = true;
        public int trader_minutes = 30;
        public int diamond_coins = 2;
        public int emerald_coins = 1;
        public int price_pocket_crafting = 3;
        public int price_pocket_furnace = 5;
        public int price_pocket_anvil = 40;
        public int price_pocket_enchanting = 60;
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static Values v = new Values();

    public static Values get() { return v; }

    private static Path file() { return FabricLoader.getInstance().getConfigDir().resolve("combo-rush.json"); }

    /** @return текст для команды: что загружено или почему нет */
    public static String load() {
        Path f = file();
        String msg;
        try {
            if (Files.exists(f)) {
                Values n = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), Values.class);
                v = n == null ? new Values() : n;
                // старые значения по умолчанию (8 и 12) подтягиваем до новых; свои значения игрока не трогаем
                if (v.price_pocket_anvil == 8) v.price_pocket_anvil = 40;
                if (v.price_pocket_enchanting == 12) v.price_pocket_enchanting = 60;
                msg = "Настройки загружены из " + f.getFileName();
            } else {
                v = new Values();
                msg = "Создан файл настроек " + f.getFileName() + " со значениями по умолчанию";
            }
            Files.createDirectories(f.getParent());
            Files.writeString(f, GSON.toJson(v), StandardCharsets.UTF_8);   // дописать новые поля, если файл старый
        } catch (Exception e) {
            ComboRush.LOGGER.error("Не удалось прочитать config/combo-rush.json — беру значения по умолчанию", e);
            v = new Values();
            msg = "Ошибка в файле настроек (" + e.getMessage() + ") — взяты значения по умолчанию";
        }
        apply();
        ComboRush.LOGGER.info(msg);
        return msg;
    }

    private static void apply() {
        CycleManager.configure(clamp(v.pause_minutes, 1, 60) * 60, clamp(v.event_minutes, 1, 60) * 60);
        RiftManager.PER_EVENT = clamp(v.rifts_per_event, 0, 2);
        RiftManager.LIFETIME = clamp(v.rift_minutes, 1, 30) * 60 * 20;
        EliteMobs.CHANCE_IDLE = Math.max(0, Math.min(1, v.elite_chance));
        EliteMobs.CHANCE_EVENT = Math.max(0, Math.min(1, v.elite_chance_event));
        DirectorManager.BOSS_EVERY = Math.max(0, v.boss_blind_every);
        PrestigeManager.REQUIRED_EVENTS = clamp(v.reset_after_events, 1, 1000);
    }

    private static int clamp(int x, int lo, int hi) { return Math.max(lo, Math.min(hi, x)); }
}
