package com.derevna230.comborush.shop;

import com.derevna230.comborush.booster.BoosterDef;
import com.derevna230.comborush.cases.EssenceShopNet;
import com.derevna230.comborush.booster.BoosterRegistry;
import com.derevna230.comborush.consumable.ConsumableDef;
import com.derevna230.comborush.consumable.ConsumableRegistry;
import com.derevna230.comborush.cycle.CycleManager;
import com.derevna230.comborush.cycle.CyclePhase;
import com.derevna230.comborush.data.OwnedBooster;
import com.derevna230.comborush.data.PlayerProfile;
import com.derevna230.comborush.hud.HudPayload;
import com.derevna230.comborush.hud.HudSync;
import com.derevna230.comborush.hud.ShopActionPayload;
import com.derevna230.comborush.promo.PromoManager;
import com.derevna230.comborush.score.BoosterEffects;
import com.derevna230.comborush.shop.ShopService.Res;
import com.derevna230.comborush.shop.ShopService.State;
import com.derevna230.comborush.species.SpeciesNet;
import com.derevna230.comborush.species.SpeciesRegistry;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;

/** Сетевая часть клиентского экрана магазина: отправляет состояние витрины и принимает действия игрока */
public class ShopNet {
    private static final Gson GSON = new Gson();

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(ShopActionPayload.ID,
            (payload, context) -> handle(context.player(), payload));
    }

    public static void open(ServerPlayerEntity p) {
        ShopService.state(p);
        send(p, "shop_open", "", true);
    }

    private static void send(ServerPlayerEntity p, String kind, String msg, boolean ok) {
        ServerPlayNetworking.send(p, new HudPayload(kind, stateJson(p, msg, ok)));
    }

    private static void handle(ServerPlayerEntity p, ShopActionPayload a) {
        if (a.action().startsWith("species")) { SpeciesNet.handle(p, a); return; }
        if (a.action().equals("camp_open")) { com.derevna230.comborush.campaign.Campaign.openJournal(p); return; }
        if (a.action().equals("camp_choose")) {
            p.sendMessage(net.minecraft.text.Text.literal("§6[Сценарий] §f" + com.derevna230.comborush.campaign.Finale.choose(p, a.c())), false);
            return;
        }
        if (a.action().startsWith("skate")) { com.derevna230.comborush.skate.SkateNet.handle(p, a); return; }
        if (a.action().startsWith("eshop") || a.action().equals("case_again")) { EssenceShopNet.handle(p, a); return; }
        if (CycleManager.phase() != CyclePhase.NORMAL) {
            ServerPlayNetworking.send(p, new HudPayload("shop_close", "{}"));
            return;
        }
        Res r = switch (a.action()) {
            case "buy"    -> ShopService.buy(p, a.a(), a.c());
            case "buyc"   -> ShopService.buyConsumable(p, a.a());
            case "reroll" -> ShopService.reroll(p);
            case "sell"   -> ShopService.sell(p, a.a());
            case "sellc"  -> ShopService.sellConsumable(p, a.a());
            case "usec"   -> ShopService.useConsumable(p, a.a(), a.b());
            case "slot"   -> ShopService.buyExtraSlot(p);
            case "sync"   -> Res.ok("");
            default       -> Res.err("Неизвестное действие.");
        };
        send(p, "shop", r.msg(), r.ok());
    }

    // ---------------- JSON ----------------
    private static JsonObject view(String id, String name, String desc, String icon, String rar, String cat) {
        JsonObject o = new JsonObject();
        o.addProperty("id", id); o.addProperty("name", name); o.addProperty("desc", desc);
        o.addProperty("icon", icon); o.addProperty("rar", rar); o.addProperty("cat", cat);
        return o;
    }

    static String stateJson(ServerPlayerEntity p, String msg, boolean ok) {
        PlayerProfile pr = PlayerProfile.of(p);
        BoosterEffects fx = ShopService.fx(p);
        State st = ShopService.state(p);

        JsonObject root = new JsonObject();
        root.addProperty("coins", pr.coins());
        root.addProperty("level", pr.level());
        root.addProperty("events", pr.eventsDone());
        root.addProperty("resets", pr.resets());
        root.addProperty("essence", pr.essence());
        root.addProperty("rank", pr.rank());
        root.addProperty("species", SpeciesRegistry.get(pr.species()).name);
        root.addProperty("promo", String.join(", ", PromoManager.activeNames()));
        root.addProperty("rerollCost", ShopService.rerollCost(st, fx));
        root.addProperty("slotCost", ShopService.slotCost(pr));
        root.addProperty("extra", pr.extraSlots());
        root.addProperty("cap", pr.slotCapacity());
        root.addProperty("msg", msg == null ? "" : msg);
        root.addProperty("ok", ok);

        JsonArray offers = new JsonArray();
        for (String id : st.offers) {
            BoosterDef d = BoosterRegistry.get(id);
            if (d == null) continue;
            JsonObject o = view(d.id, d.name, d.description, d.icon, d.rarity, HudSync.category(d));
            o.addProperty("price", ShopService.price(pr, d, fx));
            o.addProperty("canNeg", d.canBeNegative());
            offers.add(o);
        }
        root.add("offers", offers);

        JsonArray cOffers = new JsonArray();
        for (String id : st.cOffers) {
            ConsumableDef d = ConsumableRegistry.get(id);
            if (d == null) continue;
            JsonObject o = view(d.id, d.name, d.description, d.icon, d.rarity, "cons");
            o.addProperty("price", ShopService.cPrice(pr, d, fx));
            o.addProperty("needsTarget", d.needsTarget());
            cOffers.add(o);
        }
        root.add("cOffers", cOffers);

        JsonArray owned = new JsonArray();
        for (OwnedBooster ob : pr.boosters()) {
            BoosterDef d = BoosterRegistry.get(ob.id());
            if (d == null) continue;
            JsonObject o = view(d.id, d.name, d.description, d.icon, d.rarity, HudSync.category(d));
            o.addProperty("lvl", ob.level());
            o.addProperty("ed", ob.edition());
            o.addProperty("neg", ob.negative());
            o.addProperty("sell", ShopService.sellValue(ob, fx));
            owned.add(o);
        }
        root.add("owned", owned);

        JsonArray ownedCons = new JsonArray();
        for (String id : pr.consumables()) {
            ConsumableDef d = ConsumableRegistry.get(id);
            if (d == null) continue;
            JsonObject o = view(d.id, d.name, d.description, d.icon, d.rarity, "cons");
            o.addProperty("sell", ShopService.cSellValue(d));
            o.addProperty("needsTarget", d.needsTarget());
            ownedCons.add(o);
        }
        root.add("ownedCons", ownedCons);
        return GSON.toJson(root);
    }
}
