package com.derevna230.comborush.client.agent;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.entity.LivingEntity;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Модели бойцов «Противостояния» из {@code assets/combo-rush/agents/<id>.json} (их делает tools/agent_gen/chars.py).
 * Кости — как у ванильной модели игрока, поэтому все ванильные позы (ходьба, присед, ствол в руках, езда) работают сами.
 * Внутри каждой кости — часть «g» с масштабом 1/K: коробки в ней в K раз крупнее, и текстура получается в K раз детальнее скина.
 */
public final class AgentModels {
    private AgentModels() {}

    static final String[] BONES = {"head", "body", "right_arm", "left_arm", "right_leg", "left_leg"};
    private static final float[][] PIVOTS = {{0, 0, 0}, {0, 0, 0}, {-5, 2, 0}, {5, 2, 0}, {-1.9f, 12, 0}, {1.9f, 12, 0}};
    private static final String[] EMPTY = {"hat", "jacket", "left_sleeve", "right_sleeve", "left_pants", "right_pants", "ear", "cloak"};

    private record Loaded(TexturedModelData data, int k, int[] icon) {}

    public static int[] icon(String id) { return load(id).icon(); }
    private static final Map<String, Loaded> CACHE = new HashMap<>();

    private static Loaded load(String id) {
        return CACHE.computeIfAbsent(id, AgentModels::read);
    }

    private static float f(JsonArray a, int i) { return a.get(i).getAsFloat(); }

    private static Loaded read(String id) {
        JsonObject js;
        try (InputStream in = AgentModels.class.getResourceAsStream("/assets/combo-rush/agents/" + id + ".json")) {
            if (in == null) throw new IllegalStateException("нет модели бойца " + id);
            js = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (java.io.IOException e) {
            throw new IllegalStateException("не удалось прочитать модель бойца " + id, e);
        }
        int k = js.get("k").getAsInt();
        int w = js.get("w").getAsInt(), h = js.get("h").getAsInt();
        JsonObject off = js.getAsJsonObject("off");

        Map<String, ModelPartBuilder> flat = new HashMap<>();
        Map<String, List<JsonObject>> rotated = new HashMap<>();
        for (String b : BONES) { flat.put(b, ModelPartBuilder.create()); rotated.put(b, new ArrayList<>()); }
        for (JsonElement el : js.getAsJsonArray("boxes")) {
            JsonObject o = el.getAsJsonObject();
            String bone = o.get("b").getAsString();
            if (o.has("r")) { rotated.get(bone).add(o); continue; }
            JsonArray uv = o.getAsJsonArray("uv"), p = o.getAsJsonArray("o"), s = o.getAsJsonArray("s");
            flat.get(bone).uv(uv.get(0).getAsInt(), uv.get(1).getAsInt())
                .cuboid(f(p, 0) * k, f(p, 1) * k, f(p, 2) * k, f(s, 0) * k, f(s, 1) * k, f(s, 2) * k);
        }

        ModelData md = new ModelData();
        ModelPartData root = md.getRoot();
        for (int i = 0; i < BONES.length; i++) {
            String b = BONES[i];
            ModelPartData bone = root.addChild(b, ModelPartBuilder.create(), ModelTransform.pivot(PIVOTS[i][0], PIVOTS[i][1], PIVOTS[i][2]));
            float ox = 0, oy = 0, oz = 0;
            if (off != null && off.has(b)) { JsonArray a = off.getAsJsonArray(b); ox = f(a, 0); oy = f(a, 1); oz = f(a, 2); }
            ModelPartData g = bone.addChild("g", flat.get(b), ModelTransform.pivot(ox, oy, oz));
            int n = 0;
            for (JsonObject o : rotated.get(b)) {
                JsonArray uv = o.getAsJsonArray("uv"), p = o.getAsJsonArray("o"), s = o.getAsJsonArray("s"), r = o.getAsJsonArray("r");
                float px = f(r, 0), py = f(r, 1), pz = f(r, 2);
                g.addChild("r" + (n++), ModelPartBuilder.create().uv(uv.get(0).getAsInt(), uv.get(1).getAsInt())
                        .cuboid((f(p, 0) - px) * k, (f(p, 1) - py) * k, (f(p, 2) - pz) * k, f(s, 0) * k, f(s, 1) * k, f(s, 2) * k),
                    ModelTransform.of(px * k, py * k, pz * k, f(r, 3), f(r, 4), f(r, 5)));
            }
        }
        for (String e : EMPTY) root.addChild(e, ModelPartBuilder.create(), ModelTransform.NONE);
        int[] icon = {0, 0, 8, 8, w, h};
        if (js.has("icon")) { JsonArray ic = js.getAsJsonArray("icon"); icon = new int[]{ic.get(0).getAsInt(), ic.get(1).getAsInt(), ic.get(2).getAsInt(), ic.get(3).getAsInt(), w, h}; }
        return new Loaded(TexturedModelData.of(md, w, h), k, icon);
    }

    /** Новая модель бойца ("ct" или "t") для любого существа с телом игрока */
    public static <E extends LivingEntity> AgentModel<E> create(String id) {
        Loaded l = load(id);
        ModelPart root = l.data().createModel();
        return new AgentModel<>(root, l.k());
    }
}
