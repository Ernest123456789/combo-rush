package com.derevna230.comborush.armor;

import com.derevna230.comborush.ComboRush;
import com.google.gson.Gson;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public class ArmorSetRegistry implements SimpleSynchronousResourceReloadListener {
    private static final Map<String, ArmorSetDef> ALL = new LinkedHashMap<>();
    private static final Gson GSON = new Gson();
    private static final String[] FOLDERS = {"armor_sets", "weapons", "pets", "decor"};

    public static Collection<ArmorSetDef> all() { return ALL.values(); }
    public static ArmorSetDef get(String id) { return id == null ? null : ALL.get(id); }

    @Override public Identifier getFabricId() { return Identifier.of(ComboRush.MOD_ID, "armor_sets"); }

    @Override
    public void reload(ResourceManager manager) {
        ALL.clear();
        for (String folder : FOLDERS) {
            for (var entry : manager.findResources(folder, id -> id.getPath().endsWith(".json")).entrySet()) {
                Identifier file = entry.getKey();
                try (var reader = entry.getValue().getReader()) {
                    ArmorSetDef def = GSON.fromJson(reader, ArmorSetDef.class);
                    String p = file.getPath();
                    def.id = p.substring(folder.length() + 1, p.length() - ".json".length());
                    ALL.put(def.id, def);
                } catch (Exception e) {
                    ComboRush.LOGGER.error("Не удалось загрузить {}", file, e);
                }
            }
        }
        ArmorTooltips.rebuildServer();
        ComboRush.LOGGER.info("Загружено описаний брони, оружия и питомцев: {}", ALL.size());
    }
}
