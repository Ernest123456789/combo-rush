package com.derevna230.comborush.campaign;

import com.derevna230.comborush.ComboRush;
import com.google.gson.Gson;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

import java.util.*;

/** Главы и задания кампании: data/combo-rush/campaign/chapters/*.json и campaign/quests/*.json */
public class QuestRegistry implements SimpleSynchronousResourceReloadListener {
    public static class ChapterDef {
        public transient String id = "";
        public int order = 0;
        public String title = "";
        public String subtitle = "";
        /** Глава ещё не сделана: в журнале «скоро» */
        public boolean soon = false;
        /** Книга журнала: "prologue" — весь нынешний сюжет, "other" — кампания «С других сюжетов» */
        public String book = "prologue";
        /** Текст главы без заданий (заглушка до выхода сюжета) */
        public String note = "";
    }

    private static final Map<String, QuestDef> QUESTS = new LinkedHashMap<>();
    private static final Map<String, ChapterDef> CHAPTERS = new LinkedHashMap<>();
    private static final Gson GSON = new Gson();

    public static QuestDef get(String id) { return QUESTS.get(id); }
    public static Collection<QuestDef> all() { return QUESTS.values(); }
    public static Collection<ChapterDef> chapters() { return CHAPTERS.values(); }
    public static ChapterDef chapter(String id) { return CHAPTERS.get(id); }

    public static List<QuestDef> of(String chapter) {
        List<QuestDef> out = new ArrayList<>();
        for (QuestDef q : QUESTS.values()) if (q.chapter.equals(chapter)) out.add(q);
        return out;
    }

    @Override public Identifier getFabricId() { return Identifier.of(ComboRush.MOD_ID, "campaign"); }

    @Override
    public void reload(ResourceManager manager) {
        QUESTS.clear();
        CHAPTERS.clear();
        List<ChapterDef> ch = new ArrayList<>();
        for (var e : manager.findResources("campaign/chapters", id -> id.getPath().endsWith(".json")).entrySet()) {
            try (var r = e.getValue().getReader()) {
                ChapterDef d = GSON.fromJson(r, ChapterDef.class);
                d.id = name(e.getKey(), "campaign/chapters/");
                ch.add(d);
            } catch (Exception ex) { ComboRush.LOGGER.error("Не удалось загрузить главу {}", e.getKey(), ex); }
        }
        ch.sort(Comparator.comparingInt(d -> d.order));
        for (ChapterDef d : ch) CHAPTERS.put(d.id, d);

        List<QuestDef> qs = new ArrayList<>();
        for (var e : manager.findResources("campaign/quests", id -> id.getPath().endsWith(".json")).entrySet()) {
            try (var r = e.getValue().getReader()) {
                QuestDef q = GSON.fromJson(r, QuestDef.class);
                q.id = name(e.getKey(), "campaign/quests/");
                qs.add(q);
            } catch (Exception ex) { ComboRush.LOGGER.error("Не удалось загрузить задание {}", e.getKey(), ex); }
        }
        qs.sort(Comparator.<QuestDef>comparingInt(q -> CHAPTERS.containsKey(q.chapter) ? CHAPTERS.get(q.chapter).order : 999)
            .thenComparingInt(q -> q.order));
        for (QuestDef q : qs) QUESTS.put(q.id, q);
        ComboRush.LOGGER.info("Кампания: глав {}, заданий {}", CHAPTERS.size(), QUESTS.size());
    }

    private static String name(Identifier file, String prefix) {
        String p = file.getPath();
        return p.substring(prefix.length(), p.length() - ".json".length());
    }
}
