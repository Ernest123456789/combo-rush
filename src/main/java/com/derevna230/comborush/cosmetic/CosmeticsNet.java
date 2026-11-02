package com.derevna230.comborush.cosmetic;

import com.derevna230.comborush.cases.EssenceShopNet;
import com.derevna230.comborush.data.PlayerProfile;
import com.derevna230.comborush.hud.HudPayload;
import com.derevna230.comborush.hud.HudSync;
import com.derevna230.comborush.hud.ShopActionPayload;
import com.derevna230.comborush.shop.ShopService.Res;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;

/** Косметика (клиентский экран CosmeticsScreen): покупка за эссенцию, надеть и снять. Без клиентского мода — сундучное меню */
public final class CosmeticsNet {
    private CosmeticsNet() {}

    public static void open(ServerPlayerEntity p) {
        if (!HudSync.has(p)) { CosmeticsGui.open(p); return; }
        send(p, "cosm_open", "", true);
    }

    public static void handle(ServerPlayerEntity p, ShopActionPayload a) {
        switch (a.action()) {
            case "cosm_back" -> EssenceShopNet.open(p);
            case "cosm_click" -> {
                Res r = click(p, a.c());
                send(p, "cosm", r.msg(), r.ok());
            }
            default -> { }
        }
    }

    private static Res click(ServerPlayerEntity player, String id) {
        CosmeticDef d = CosmeticRegistry.get(id);
        if (d == null) return Res.err("Такой косметики нет.");
        PlayerProfile p = PlayerProfile.of(player);
        Res res;
        if (p.owns(d.id)) {
            if (d.id.equals(p.equippedId(d.type))) {
                player.setAttached(PlayerProfile.TYPE, p.unequip(d.type));
                res = Res.ok("Снято: " + d.name);
            } else {
                player.setAttached(PlayerProfile.TYPE, p.equip(d.type, d.id));
                res = Res.ok("Надето: " + d.name);
            }
        } else {
            if (p.essence() < d.price) return Res.err("Не хватает эссенции: нужно " + d.price + ".");
            player.setAttached(PlayerProfile.TYPE, p.buyCosmetic(d.id, d.price).equip(d.type, d.id));
            res = Res.ok("Куплено и надето: " + d.name);
            player.getServerWorld().playSound(null, player.getBlockPos(), SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.6f, 1.6f);
        }
        CosmeticManager.refresh(player);
        return res;
    }

    private static void send(ServerPlayerEntity player, String kind, String msg, boolean ok) {
        PlayerProfile p = PlayerProfile.of(player);
        JsonObject root = new JsonObject();
        root.addProperty("essence", p.essence());
        root.addProperty("msg", msg == null ? "" : msg);
        root.addProperty("ok", ok);
        root.addProperty("name", player.getName().getString());
        JsonArray items = new JsonArray();
        for (CosmeticDef d : CosmeticRegistry.all()) {
            boolean owned = p.owns(d.id);
            JsonObject o = new JsonObject();
            o.addProperty("id", d.id);
            o.addProperty("name", d.name);
            o.addProperty("desc", d.description);
            o.addProperty("icon", d.icon);
            o.addProperty("type", d.type);
            o.addProperty("price", d.price);
            o.addProperty("owned", owned);
            o.addProperty("on", owned && d.id.equals(p.equippedId(d.type)));
            o.addProperty("prefix", d.prefix == null ? "" : d.prefix);
            items.add(o);
        }
        root.add("items", items);
        ServerPlayNetworking.send(player, new HudPayload(kind, root.toString()));
    }
}
