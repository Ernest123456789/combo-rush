package com.derevna230.comborush.cases;

import com.derevna230.comborush.ComboRush;
import com.derevna230.comborush.armor.ArmorTooltips;
import com.google.gson.Gson;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

import java.util.*;

public class CaseRegistry implements SimpleSynchronousResourceReloadListener {
    private static final Map<String, CaseDef> ALL = new LinkedHashMap<>();
    private static final Gson GSON = new Gson();

    public static Collection<CaseDef> all() {
        List<CaseDef> l = new ArrayList<>(ALL.values());
        l.sort(Comparator.comparingInt(c -> c.order));
        return l;
    }
    public static CaseDef get(String id) { return id == null ? null : ALL.get(id); }

    @Override public Identifier getFabricId() { return Identifier.of(ComboRush.MOD_ID, "cases"); }

    @Override
    public void reload(ResourceManager manager) {
        ALL.clear();
        for (var entry : manager.findResources("cases", id -> id.getPath().endsWith(".json")).entrySet()) {
            Identifier file = entry.getKey();
            try (var reader = entry.getValue().getReader()) {
                CaseDef def = GSON.fromJson(reader, CaseDef.class);
                String p = file.getPath();
                def.id = p.substring("cases/".length(), p.length() - ".json".length());
                ALL.put(def.id, def);
            } catch (Exception e) {
                ComboRush.LOGGER.error("Не удалось загрузить кейс {}", file, e);
            }
        }
        ArmorTooltips.rebuildServer();
        ComboRush.LOGGER.info("Загружено кейсов: {}", ALL.size());
    }
}
