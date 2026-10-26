package com.derevna230.comborush.booster;

import com.derevna230.comborush.ComboRush;
import com.google.gson.Gson;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public class BoosterRegistry implements SimpleSynchronousResourceReloadListener {
    private static final Map<String, BoosterDef> ALL = new LinkedHashMap<>();
    private static final Gson GSON = new Gson();

    public static Collection<BoosterDef> all() { return ALL.values(); }
    public static BoosterDef get(String id) { return ALL.get(id); }

    @Override public Identifier getFabricId() { return Identifier.of(ComboRush.MOD_ID, "boosters"); }

    @Override
    public void reload(ResourceManager manager) {
        ALL.clear();
        for (var entry : manager.findResources("boosters", id -> id.getPath().endsWith(".json")).entrySet()) {
            Identifier file = entry.getKey();
            try (var reader = entry.getValue().getReader()) {
                BoosterDef def = GSON.fromJson(reader, BoosterDef.class);
                String p = file.getPath();
                def.id = file.getNamespace() + ":" + p.substring("boosters/".length(), p.length() - ".json".length());
                ALL.put(def.id, def);
            } catch (Exception e) {
                ComboRush.LOGGER.error("Не удалось загрузить усилитель {}", file, e);
            }
        }
        ComboRush.LOGGER.info("Загружено усилителей: {}", ALL.size());
    }
}
