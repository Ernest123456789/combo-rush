package com.derevna230.comborush.cosmetic;

import com.derevna230.comborush.ComboRush;
import com.google.gson.Gson;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public class CosmeticRegistry implements SimpleSynchronousResourceReloadListener {
    private static final Map<String, CosmeticDef> ALL = new LinkedHashMap<>();
    private static final Gson GSON = new Gson();

    public static Collection<CosmeticDef> all() { return ALL.values(); }
    public static CosmeticDef get(String id) { return id == null ? null : ALL.get(id); }

    @Override public Identifier getFabricId() { return Identifier.of(ComboRush.MOD_ID, "cosmetics"); }

    @Override
    public void reload(ResourceManager manager) {
        ALL.clear();
        for (var entry : manager.findResources("cosmetics", id -> id.getPath().endsWith(".json")).entrySet()) {
            Identifier file = entry.getKey();
            try (var reader = entry.getValue().getReader()) {
                CosmeticDef def = GSON.fromJson(reader, CosmeticDef.class);
                String p = file.getPath();
                def.id = file.getNamespace() + ":" + p.substring("cosmetics/".length(), p.length() - ".json".length());
                ALL.put(def.id, def);
            } catch (Exception e) {
                ComboRush.LOGGER.error("Не удалось загрузить косметику {}", file, e);
            }
        }
        ComboRush.LOGGER.info("Загружено косметики: {}", ALL.size());
    }
}
