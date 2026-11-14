package com.derevna230.comborush.lobby;

import com.derevna230.comborush.hud.HudPayload;
import com.derevna230.comborush.universe.Universes;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.List;

/** Кто каким агентом играет: раз в 2 с рассылаем выбор всех игроков режима, чтобы все видели друг друга в выбранном облике */
public final class AgentSync {
    private AgentSync() {}

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTicks() % 40 != 0) return;
            List<ServerPlayerEntity> ps = new ArrayList<>();
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) if (Universes.standoffWorld(p.getWorld())) ps.add(p);
            if (ps.isEmpty()) return;
            JsonArray a = new JsonArray();
            for (ServerPlayerEntity p : ps) {
                JsonObject o = new JsonObject();
                o.addProperty("u", p.getUuidAsString());
                o.addProperty("t", Loadout.equipped(p, AgentDefs.SLOT_T));
                o.addProperty("ct", Loadout.equipped(p, AgentDefs.SLOT_CT));
                a.add(o);
            }
            JsonObject msg = new JsonObject();
            msg.add("list", a);
            String s = msg.toString();
            for (ServerPlayerEntity p : ps) if (ServerPlayNetworking.canSend(p, HudPayload.ID)) ServerPlayNetworking.send(p, new HudPayload("agents", s));
        });
    }
}
