package com.derevna230.comborush.species;

import com.derevna230.comborush.data.PlayerProfile;
import com.derevna230.comborush.hud.HudPayload;
import com.derevna230.comborush.hud.ShopActionPayload;
import com.derevna230.comborush.prestige.PrestigeManager;
import com.derevna230.comborush.shop.ShopService.Res;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;

/** Сетевая часть клиентского экрана выбора существа */
public class SpeciesNet {
    private static final Gson GSON = new Gson();

    public static void open(ServerPlayerEntity p) { send(p, "species_open", "", true); }

    /** Действия приходят тем же пакетом, что и у магазина (action = "species_pick", c = id существа) */
    public static void handle(ServerPlayerEntity p, ShopActionPayload a) {
        Res r = switch (a.action()) {
            case "species_pick" -> SpeciesService.choose(p, a.c());
            default -> Res.ok("");
        };
        send(p, "species", r.msg(), r.ok());
    }

    private static void send(ServerPlayerEntity p, String kind, String msg, boolean ok) {
        ServerPlayNetworking.send(p, new HudPayload(kind, json(p, msg, ok)));
    }

    private static String json(ServerPlayerEntity p, String msg, boolean ok) {
        PlayerProfile pr = PlayerProfile.of(p);
        JsonObject root = new JsonObject();
        root.addProperty("current", pr.species());
        root.addProperty("canChange", SpeciesService.canChange(pr));
        root.addProperty("started", pr.started());
        root.addProperty("events", pr.eventsDone());
        root.addProperty("resets", pr.resets());
        root.addProperty("essence", pr.essence());
        root.addProperty("maxRank", PrestigeManager.MAX_RANK);
        root.addProperty("msg", msg == null ? "" : msg);
        root.addProperty("ok", ok);

        JsonArray list = new JsonArray();
        for (SpeciesDef d : SpeciesRegistry.all()) {
            JsonObject o = new JsonObject();
            o.addProperty("id", d.id);
            o.addProperty("name", d.name);
            o.addProperty("desc", d.description);
            o.addProperty("icon", d.icon);
            o.addProperty("unlocked", d.unlockedFor(pr));
            o.addProperty("need", d.unlock_resets);
            o.addProperty("rank", pr.rankOf(d.id));
            JsonArray traits = new JsonArray();
            for (SpeciesService.Trait t : SpeciesService.traits(d)) {
                JsonObject to = new JsonObject();
                to.addProperty("t", t.text());
                to.addProperty("g", t.good());
                traits.add(to);
            }
            o.add("traits", traits);
            list.add(o);
        }
        root.add("list", list);
        return GSON.toJson(root);
    }
}
