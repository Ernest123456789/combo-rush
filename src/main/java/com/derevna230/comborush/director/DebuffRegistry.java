package com.derevna230.comborush.director;

import com.derevna230.comborush.ComboRush;
import com.google.gson.Gson;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class DebuffRegistry implements SimpleSynchronousResourceReloadListener {
    private static final Map<String, DebuffDef> ALL = new LinkedHashMap<>();
    private static final Gson GSON = new Gson();

    public static Collection<DebuffDef> all() { return ALL.values(); }
    public static DebuffDef get(String id) { return ALL.get(id); }

    /** Случайный дебаф по весу, не равный предыдущему */
    public static DebuffDef pick(String exclude) {
        List<DebuffDef> pool = new ArrayList<>(ALL.values());
        if (pool.size() > 1) pool.removeIf(d -> d.id.equals(exclude));
        if (pool.isEmpty()) return null;
        int total = pool.stream().mapToInt(d -> Math.max(1, d.weight)).sum();
        int roll = ThreadLocalRandom.current().nextInt(total);
        for (DebuffDef d : pool) { roll -= Math.max(1, d.weight); if (roll < 0) return d; }
        return pool.get(0);
    }

    @Override public Identifier getFabricId() { return Identifier.of(ComboRush.MOD_ID, "debuffs"); }

    @Override
    public void reload(ResourceManager manager) {
        ALL.clear();
        for (var entry : manager.findResources("debuffs", id -> id.getPath().endsWith(".json")).entrySet()) {
            Identifier file = entry.getKey();
            try (var reader = entry.getValue().getReader()) {
                DebuffDef def = GSON.fromJson(reader, DebuffDef.class);
                String p = file.getPath();
                def.id = p.substring("debuffs/".length(), p.length() - ".json".length());
                ALL.put(def.id, def);
            } catch (Exception e) {
                ComboRush.LOGGER.error("Не удалось загрузить дебаф {}", file, e);
            }
        }
        ComboRush.LOGGER.info("Загружено дебафов: {}", ALL.size());
    }
}
