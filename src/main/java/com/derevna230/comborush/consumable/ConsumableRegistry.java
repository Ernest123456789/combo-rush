package com.derevna230.comborush.consumable;

import com.derevna230.comborush.ComboRush;
import com.google.gson.Gson;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class ConsumableRegistry implements SimpleSynchronousResourceReloadListener {
    private static final Map<String, ConsumableDef> ALL = new LinkedHashMap<>();
    private static final Gson GSON = new Gson();

    public static Collection<ConsumableDef> all() { return ALL.values(); }
    public static ConsumableDef get(String id) { return id == null ? null : ALL.get(id); }

    public static String randomId() {
        if (ALL.isEmpty()) return null;
        List<String> ids = new ArrayList<>(ALL.keySet());
        return ids.get(ThreadLocalRandom.current().nextInt(ids.size()));
    }

    @Override public Identifier getFabricId() { return Identifier.of(ComboRush.MOD_ID, "consumables"); }

    @Override
    public void reload(ResourceManager manager) {
        ALL.clear();
        for (var entry : manager.findResources("consumables", id -> id.getPath().endsWith(".json")).entrySet()) {
            Identifier file = entry.getKey();
            try (var reader = entry.getValue().getReader()) {
                ConsumableDef def = GSON.fromJson(reader, ConsumableDef.class);
                String p = file.getPath();
                def.id = file.getNamespace() + ":" + p.substring("consumables/".length(), p.length() - ".json".length());
                ALL.put(def.id, def);
            } catch (Exception e) {
                ComboRush.LOGGER.error("Не удалось загрузить расходник {}", file, e);
            }
        }
        ComboRush.LOGGER.info("Загружено расходников: {}", ALL.size());
    }
}
