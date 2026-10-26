package com.derevna230.comborush.cases;

import com.derevna230.comborush.armor.ArmorSetDef;
import com.derevna230.comborush.armor.ArmorSetRegistry;
import com.derevna230.comborush.cosmetic.CosmeticsGui;
import com.derevna230.comborush.data.PlayerProfile;
import com.derevna230.comborush.hud.HudPayload;
import com.derevna230.comborush.hud.ShopActionPayload;
import com.derevna230.comborush.item.ModItems;
import com.derevna230.comborush.shop.ShopService.Res;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;

/** Магазин эссенции (клиентский экран): кейсы, разбор брони, переход в косметику */
public final class EssenceShopNet {
    private EssenceShopNet() {}
    private static final Gson GSON = new Gson();

    public static void open(ServerPlayerEntity p) { send(p, "eshop_open", "", true); }

    public static void handle(ServerPlayerEntity p, ShopActionPayload a) {
        switch (a.action()) {
            case "eshop_cosmetics" -> { CosmeticsGui.open(p); return; }
            case "case_again" -> {
                Res r = CaseService.openAgain(p, a.c());
                if (!r.ok()) send(p, "eshop_open", r.msg(), false);
                return;
            }
            default -> { }
        }
        Res r = switch (a.action()) {
            case "eshop_buy" -> CaseService.buy(p, a.c());
            case "eshop_scrap" -> CaseService.scrap(p);
            default -> Res.ok("");
        };
        send(p, "eshop", r.msg(), r.ok());
    }

    private static void send(ServerPlayerEntity p, String kind, String msg, boolean ok) {
        ServerPlayNetworking.send(p, new HudPayload(kind, json(p, msg, ok)));
    }

    private static String json(ServerPlayerEntity p, String msg, boolean ok) {
        PlayerProfile pr = PlayerProfile.of(p);
        JsonObject root = new JsonObject();
        root.addProperty("essence", pr.essence());
        root.addProperty("resets", pr.resets());
        root.addProperty("msg", msg == null ? "" : msg);
        root.addProperty("ok", ok);

        ItemStack hand = p.getStackInHand(Hand.MAIN_HAND);
        String set = ModItems.ITEM_SET.get(hand.getItem());
        if (set != null) {
            JsonObject h = new JsonObject();
            h.addProperty("name", hand.getName().getString());
            h.addProperty("rar", ModItems.rarityOf(set));
            h.addProperty("value", CaseService.scrapValue(ModItems.rarityOf(set)));
            root.add("hand", h);
        }

        JsonArray cases = new JsonArray();
        for (CaseDef c : CaseRegistry.all()) {
            Item item = ModItems.CASES.get(c.id);
            if (item == null) continue;
            JsonObject o = new JsonObject();
            o.addProperty("id", c.id);
            o.addProperty("item", Registries.ITEM.getId(item).toString());
            o.addProperty("name", c.name);
            o.addProperty("desc", c.description);
            o.addProperty("rar", c.rarity);
            o.addProperty("price", c.price);
            o.addProperty("owned", CaseService.countCases(p, c.id));
            JsonArray drops = new JsonArray();
            for (CaseDef.Drop d : c.dropsSorted()) {
                ArmorSetDef def = ArmorSetRegistry.get(d.set);
                JsonObject dd = new JsonObject();
                dd.addProperty("name", def == null ? d.set : def.name);
                dd.addProperty("rar", ModItems.rarityOf(d.set));
                dd.addProperty("chance", CaseDef.pctText(c.chance(d)));
                dd.addProperty("p", c.chance(d));
                drops.add(dd);
            }
            o.add("drops", drops);
            cases.add(o);
        }
        root.add("cases", cases);
        return GSON.toJson(root);
    }
}
