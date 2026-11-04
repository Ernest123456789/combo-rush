package com.derevna230.comborush.lobby;

import com.derevna230.comborush.data.PlayerProfile;
import com.derevna230.comborush.hud.HudPayload;
import com.derevna230.comborush.hud.HudSync;
import com.derevna230.comborush.hud.ShopActionPayload;
import com.derevna230.comborush.universe.Universes;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Сеть меню «Противостояния» и лавок Лобби: действия so_* и lobby_*, состояние для экрана */
public final class StandoffNet {
    private StandoffNet() {}

    public static void handle(ServerPlayerEntity p, ShopActionPayload a) {
        String err = null;
        switch (a.action()) {
            case "so_state" -> { }
            case "so_invite" -> err = Party.invite(p, a.c());
            case "so_accept" -> err = Party.accept(p);
            case "so_decline" -> err = Party.decline(p);
            case "so_leave_party" -> Party.leave(p, true);
            case "so_kick" -> err = Party.kick(p, a.c());
            case "so_exit" -> err = Standoff.exit(p);
            case "so_equip" -> {
                int i = a.c().indexOf('|');
                err = !Universes.inStandoff(p) || i < 0 ? "Снаряжение меняется в меню «Противостояния»."
                    : Loadout.equip(p, a.c().substring(0, i), a.c().substring(i + 1));
            }
            case "so_trade" -> err = !Universes.inStandoff(p) ? "Контракты подписывают в меню «Противостояния»." : SkinTrade.execute(p, a.c());
            case "lobby_play" -> err = Lobby.play(p, a.a());
            default -> { }
        }
        if (err != null) p.sendMessage(Text.literal("§c" + err), true);
        sendState(p);
        // изменения группы видят все её члены
        Party q = Party.of(p);
        if (q != null && a.action().startsWith("so_") && !a.action().equals("so_state")) q.sync(p.getServer());
    }

    public static void sendState(ServerPlayerEntity p) {
        if (!HudSync.has(p)) return;
        JsonObject o = new JsonObject();
        o.addProperty("where", Universes.where(p));
        o.addProperty("essence", PlayerProfile.of(p).essence());

        JsonObject party = new JsonObject();
        Party q = Party.of(p);
        JsonArray members = new JsonArray();
        if (q != null) {
            for (UUID id : q.members()) {
                ServerPlayerEntity m = p.getServer().getPlayerManager().getPlayer(id);
                if (m == null) continue;
                JsonObject mo = new JsonObject();
                mo.addProperty("name", m.getName().getString());
                mo.addProperty("leader", id.equals(q.leader()));
                mo.addProperty("me", m == p);
                members.add(mo);
            }
        }
        party.add("members", members);
        party.addProperty("max", Party.MAX);
        o.add("party", party);

        String from = Party.pendingFrom(p);
        if (from != null) o.addProperty("invite", from);

        JsonArray online = new JsonArray();
        for (ServerPlayerEntity t : p.getServer().getPlayerManager().getPlayerList()) {
            if (t == p || !(Universes.inLobby(t) || Universes.inStandoff(t))) continue;
            if (q != null && q.members().contains(t.getUuid())) continue;
            JsonObject to = new JsonObject();
            to.addProperty("name", t.getName().getString());
            to.addProperty("busy", Party.of(t) != null);
            online.add(to);
        }
        o.add("online", online);

        JsonObject lo = new JsonObject();
        for (Map.Entry<String, String> e : Loadout.get(p).entrySet()) lo.addProperty(e.getKey(), e.getValue());
        o.add("loadout", lo);
        JsonArray owned = new JsonArray();
        for (String id : Loadout.owned(p)) owned.add(id);
        o.add("owned", owned);
        o.add("stacks", SkinTrade.stacksJson(p));
        JsonObject traded = SkinTrade.takeResult(p.getUuid());
        if (traded != null) o.add("traded", traded);
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(p, new HudPayload("so", o.toString()));
    }
}
