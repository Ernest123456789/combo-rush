package com.derevna230.comborush.boss;

import com.derevna230.comborush.cases.CaseDef;
import com.derevna230.comborush.cases.CaseRegistry;
import com.derevna230.comborush.cases.CaseService;
import com.derevna230.comborush.data.PlayerProfile;
import com.derevna230.comborush.hud.HudPayload;
import com.derevna230.comborush.hud.ShopActionPayload;
import com.derevna230.comborush.item.ModItems;
import com.derevna230.comborush.score.ScoreEngine;
import com.derevna230.comborush.score.ScoreSource;
import com.derevna230.comborush.shop.ShopService.Res;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;

import java.util.List;

/**
 * Прилавок Торговца Редстоуна: Сердце голема и три кейса со скидкой 25% (набор у каждого торговца свой).
 * Экран — TraderScreen на клиенте; без клиентского мода работает сундучное меню TraderGui. Покупки — здесь.
 */
public final class TraderNet {
    private TraderNet() {}

    public static final int HEART_DIAMONDS = 24, HEART_ESSENCE = 300;

    public static void open(ServerPlayerEntity p, GolemTraderEntity t) { send(p, t, "trader_open", "", true); }

    public static void handle(ServerPlayerEntity p, ShopActionPayload a) {
        if (!a.action().equals("trader_buy")) return;
        GolemTraderEntity t = nearest(p);
        if (t == null) { ServerPlayNetworking.send(p, new HudPayload("trader", "{\"gone\":true}")); return; }
        Res r = buy(p, t, a.c());
        done(p, r);
        send(p, t, "trader", r.msg(), r.ok());
    }

    /** Ближайший торговец в 12 блоках — иначе игрок уже отошёл или торговец ушёл */
    public static GolemTraderEntity nearest(ServerPlayerEntity p) {
        GolemTraderEntity best = null;
        for (GolemTraderEntity e : p.getServerWorld().getEntitiesByClass(GolemTraderEntity.class, p.getBoundingBox().expand(12), x -> true))
            if (best == null || e.squaredDistanceTo(p) < best.squaredDistanceTo(p)) best = e;
        return best;
    }

    /** Цена кейса у торговца: −25%, но не меньше 1 */
    public static int price(CaseDef def) {
        return Math.max(1, (int) Math.round(def.price * (100 - GolemTraderEntity.DISCOUNT_PERCENT) / 100.0));
    }

    /** key: heart_d, heart_e или case0..case2 */
    public static Res buy(ServerPlayerEntity p, GolemTraderEntity t, String key) {
        if (key.equals("heart_d")) {
            if (CaseService.countDiamonds(p) < HEART_DIAMONDS) return Res.err("Не хватает алмазов: нужно " + HEART_DIAMONDS + ".");
            CaseService.takeDiamonds(p, HEART_DIAMONDS);
            p.getInventory().offerOrDrop(new ItemStack(ModItems.REDSTONE_HEART));
            return Res.ok("Сердце голема у вас. Найдите открытое место и ПКМ по земле — если не боитесь.");
        }
        if (key.equals("heart_e")) {
            PlayerProfile pr = PlayerProfile.of(p);
            if (pr.essence() < HEART_ESSENCE) return Res.err("Не хватает эссенции: нужно " + HEART_ESSENCE + ".");
            p.setAttached(PlayerProfile.TYPE, pr.addEssence(-HEART_ESSENCE));
            p.getInventory().offerOrDrop(new ItemStack(ModItems.REDSTONE_HEART));
            return Res.ok("Сердце голема у вас. Найдите открытое место и ПКМ по земле — если не боитесь.");
        }
        if (key.startsWith("case")) {
            int idx;
            try { idx = Integer.parseInt(key.substring(4)); } catch (NumberFormatException e) { return Res.err("Нет такого товара."); }
            if (idx < 0 || idx >= t.offers().size()) return Res.err("Нет такого товара.");
            if (t.stock(idx) <= 0) return Res.err("Этот кейс уже раскупили.");
            CaseDef def = CaseRegistry.get(t.offers().get(idx));
            if (def == null) return Res.err("Нет такого кейса.");
            Res r = CaseService.buy(p, def.id, price(def));
            if (r.ok()) t.takeStock(idx);
            return r;
        }
        return Res.err("Нет такого товара.");
    }

    private static void done(ServerPlayerEntity p, Res r) {
        p.getWorld().playSound(null, p.getBlockPos(), r.ok() ? SoundEvents.ENTITY_VILLAGER_YES : SoundEvents.ENTITY_VILLAGER_NO,
            SoundCategory.NEUTRAL, 1f, 1f);
        if (r.ok()) ScoreEngine.award(p, ScoreSource.TRADE, "redstone_trader");
    }

    /** Для сундучного меню: то же самое действие и звук */
    public static Res buyAndNotify(ServerPlayerEntity p, GolemTraderEntity t, String key) {
        Res r = buy(p, t, key);
        done(p, r);
        return r;
    }

    private static void send(ServerPlayerEntity p, GolemTraderEntity t, String kind, String msg, boolean ok) {
        ServerPlayNetworking.send(p, new HudPayload(kind, json(p, t, msg, ok)));
    }

    private static String json(ServerPlayerEntity p, GolemTraderEntity t, String msg, boolean ok) {
        JsonObject root = new JsonObject();
        root.addProperty("diamonds", CaseService.countDiamonds(p));
        root.addProperty("essence", PlayerProfile.of(p).essence());
        root.addProperty("msg", msg == null ? "" : msg);
        root.addProperty("ok", ok);
        root.addProperty("left", t.leftTicks() / 20);
        root.addProperty("discount", GolemTraderEntity.DISCOUNT_PERCENT);
        root.addProperty("heartDia", HEART_DIAMONDS);
        root.addProperty("heartEss", HEART_ESSENCE);
        JsonArray offers = new JsonArray();
        List<String> ids = t.offers();
        for (int i = 0; i < ids.size(); i++) {
            CaseDef def = CaseRegistry.get(ids.get(i));
            Item item = ModItems.CASES.get(ids.get(i));
            if (def == null || item == null) continue;
            JsonObject o = new JsonObject();
            o.addProperty("key", "case" + i);
            o.addProperty("item", Registries.ITEM.getId(item).toString());
            o.addProperty("name", def.name);
            o.addProperty("desc", def.description);
            o.addProperty("rar", def.rarity);
            o.addProperty("base", def.price);
            o.addProperty("price", price(def));
            o.addProperty("cur", def.paysDiamonds() ? "diamond" : "essence");
            o.addProperty("stock", t.stock(i));
            o.addProperty("owned", CaseService.countCases(p, def.id));
            offers.add(o);
        }
        root.add("offers", offers);
        return root.toString();
    }
}
