package com.derevna230.comborush.campaign;

import java.util.ArrayList;
import java.util.List;

/**
 * Задание кампании из data/combo-rush/campaign/quests/*.json.
 * scope: "personal" — у каждого игрока своё; "server" — общее на сервер, награда достаётся всем, кто до него дошёл.
 * goal.type: talk | species | booster | event | courier | poster | splice | stage
 * Ночной город (см. {@link CityStory}): enter | drive | kill | reach | hold | boss
 */
public class QuestDef {
    public transient String id = "";
    public String chapter = "prologue";
    public int order = 0;
    public String title = "";
    public String desc = "";
    public String scope = "personal";
    public Goal goal = new Goal();
    public List<String> requires = new ArrayList<>();
    public Reward reward = new Reward();
    /** Реплики NPC: при взятии/старте и при завершении */
    public List<String> lines_start = new ArrayList<>();
    public List<String> lines_done = new ArrayList<>();
    /** Подсказка для журнала и трекера (что сделать) */
    public String hint = "";
    /** Кто произносит реплики: ticketer, director, assistant, poster… */
    public String speaker = "ticketer";

    public static class Goal {
        public String type = "talk";
        public int count = 1;
        /** для talk — роль NPC; для event — минимальная медаль (BRONZE/SILVER/GOLD) */
        public String target = "";
        /** Ночной город: кого убивать (any | zombie | skeleton | spider | creeper) / набор мобов для волн / id босса */
        public String mob = "";
        /** точка на карте города (id из CityStory.SITES): где убивать, держать оборону или биться с боссом */
        public String site = "";
        /** reach: список точек; ordered — строго по порядку, иначе в любом */
        public List<String> sites = new ArrayList<>();
        public boolean ordered = false;
        /** reach: время на весь маршрут после первой точки (0 — без таймера); hold: сколько секунд держаться */
        public int seconds = 0;
        /** нужно ли быть за рулём машины (reach, kill) */
        public boolean car = false;
        /** радиус точки в блоках (0 — по умолчанию) */
        public int radius = 0;
        /** reach: сообщения при достижении точек (по порядку списка) */
        public List<String> msgs = new ArrayList<>();
    }

    public static class Reward {
        public int essence = 0;
        public int coins = 0;
        public List<String> cases = new ArrayList<>();
        /** "minecraft:diamond:3" или "combo-rush:pass" */
        public List<String> items = new ArrayList<>();
        public boolean skateboard = false;
        public boolean script_scrap = false;
    }

    public boolean server() { return "server".equals(scope); }
    public int count() { return Math.max(1, goal.count); }

    /** Короткая строка наград для журнала */
    public String rewardText() {
        List<String> parts = new ArrayList<>();
        if (reward.essence > 0) parts.add(reward.essence + " эссенции");
        if (reward.coins > 0) parts.add(reward.coins + " монет");
        for (String c : reward.cases) parts.add("кейс «" + caseName(c) + "»");
        if (reward.skateboard) parts.add("зачарованный скейт");
        if (reward.script_scrap) parts.add("обрывок сценария");
        for (String i : reward.items) if (i.contains("pass")) parts.add("Пропуск участника");
        return String.join(", ", parts);
    }

    private static String caseName(String id) {
        return switch (id) {
            case "common" -> "Обычный"; case "rare" -> "Редкий"; case "epic" -> "Эпический";
            case "legendary" -> "Легендарный"; case "mythic" -> "Мифический"; default -> id;
        };
    }
}
