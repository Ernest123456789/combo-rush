package com.derevna230.comborush.species;

import com.derevna230.comborush.ComboRush;
import com.google.gson.Gson;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public class SpeciesRegistry implements SimpleSynchronousResourceReloadListener {
    public static final String DEFAULT_ID = ComboRush.MOD_ID + ":human";
    private static final Map<String, SpeciesDef> ALL = new LinkedHashMap<>();
    private static final SpeciesDef NEUTRAL = new SpeciesDef();
    private static final Gson GSON = new Gson();

    public static Collection<SpeciesDef> all() { return ALL.values(); }

    /** Никогда не возвращает null: неизвестное существо = нейтральное */
    public static SpeciesDef get(String id) { return ALL.getOrDefault(id, NEUTRAL); }
    public static boolean exists(String id) { return ALL.containsKey(id); }

    @Override public Identifier getFabricId() { return Identifier.of(ComboRush.MOD_ID, "species"); }

    @Override
    public void reload(ResourceManager manager) {
        ALL.clear();
        for (var entry : manager.findResources("species", id -> id.getPath().endsWith(".json")).entrySet()) {
            Identifier file = entry.getKey();
            try (var reader = entry.getValue().getReader()) {
                SpeciesDef def = GSON.fromJson(reader, SpeciesDef.class);
                String p = file.getPath();
                def.id = file.getNamespace() + ":" + p.substring("species/".length(), p.length() - ".json".length());
                ALL.put(def.id, def);
            } catch (Exception e) {
                ComboRush.LOGGER.error("Не удалось загрузить существо {}", file, e);
            }
        }
        ComboRush.LOGGER.info("Загружено существ: {}", ALL.size());
    }
}
