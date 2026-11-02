package com.derevna230.comborush.workshop;

import com.derevna230.comborush.ModConfig;
import com.derevna230.comborush.data.PlayerProfile;
import com.derevna230.comborush.hud.HudPayload;
import com.derevna230.comborush.hud.HudSync;
import com.derevna230.comborush.hud.ShopActionPayload;
import com.derevna230.comborush.item.ModItems;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

/**
 * Мастерская: карманные станки за монеты и обмен алмазов и изумрудов на монеты.
 * Работает в любой фазе цикла. Экран — WorkshopScreen на клиенте.
 */
public final class WorkshopNet {
    private WorkshopNet() {}

    private record Ex(String id, Item item, Item block, String name) {}
    private static final Ex[] EXCHANGE = {
        new Ex("diamond", Items.DIAMOND, Items.DIAMOND_BLOCK, "Алмазы"),
        new Ex("emerald", Items.EMERALD, Items.EMERALD_BLOCK, "Изумруды"),
    };

    private static int price(String id) {
        ModConfig.Values v = ModConfig.get();
        return switch (id) {
            case "pocket_crafting" -> v.price_pocket_crafting;
            case "pocket_furnace" -> v.price_pocket_furnace;
            case "pocket_anvil" -> v.price_pocket_anvil;
            case "pocket_enchanting" -> v.price_pocket_enchanting;
            default -> -1;
        };
    }

    private static int rate(String id) {
        return id.equals("diamond") ? ModConfig.get().diamond_coins : ModConfig.get().emerald_coins;
    }

    private static String desc(String id) {
        return switch (id) {
            case "pocket_crafting" -> "Верстак 3×3 прямо из кармана";
            case "pocket_furnace" -> "Мгновенно плавит стопку из другой руки за уголь";
            case "pocket_anvil" -> "Наковальня, которая никогда не ломается";
            case "pocket_enchanting" -> "Стол зачарований где угодно";
            default -> "";
        };
    }

    public static void open(ServerPlayerEntity p) {
        if (!HudSync.has(p)) {
            p.sendMessage(Text.literal("§cМастерская работает с клиентской частью мода."), false);
            return;
        }
        send(p, "ws_open", "", true);
    }

    public static void handle(ServerPlayerEntity p, ShopActionPayload a) {
        switch (a.action()) {
            case "ws_open" -> open(p);
            case "ws_buy" -> buy(p, a.c());
            case "ws_sell" -> sell(p, a.c(), a.a());
            default -> { }
        }
    }

    private static boolean has(ServerPlayerEntity p, Item item) {
        for (int i = 0; i < p.getInventory().size(); i++) if (p.getInventory().getStack(i).isOf(item)) return true;
        return false;
    }

    private static int count(ServerPlayerEntity p, Item item) {
        int n = 0;
        for (int i = 0; i < p.getInventory().size(); i++) {
            ItemStack s = p.getInventory().getStack(i);
            if (s.isOf(item)) n += s.getCount();
        }
        return n;
    }

    private static void buy(ServerPlayerEntity p, String id) {
        Item item = ModItems.POCKET.get(id);
        int price = price(id);
        if (item == null || price < 0) { send(p, "ws", "Такого станка нет.", false); return; }
        if (has(p, item)) { send(p, "ws", "Он уже у тебя в инвентаре.", false); return; }
        PlayerProfile pr = PlayerProfile.of(p);
        if (pr.coins() < price) { send(p, "ws", "Не хватает монет: нужно " + price + ".", false); return; }
        p.setAttached(PlayerProfile.TYPE, pr.addCoins(-price));
        p.getInventory().offerOrDrop(new ItemStack(item));
        p.getServerWorld().playSound(null, p.getBlockPos(), SoundEvents.BLOCK_SMITHING_TABLE_USE, SoundCategory.PLAYERS, 0.8f, 1.1f);
        send(p, "ws", "Куплено: " + new ItemStack(item).getName().getString(), true);
    }

    /** Сдать алмазы/изумруды: amount > 0 — столько штук, 0 — всё, включая блоки (блок = 9) */
    private static void sell(ServerPlayerEntity p, String id, int amount) {
        Ex ex = null;
        for (Ex e : EXCHANGE) if (e.id().equals(id)) ex = e;
        if (ex == null) { send(p, "ws", "Это здесь не принимают.", false); return; }
        int rate = rate(id);
        int units = 0;
        if (amount > 0) {
            int left = amount;
            for (int i = 0; i < p.getInventory().size() && left > 0; i++) {
                ItemStack s = p.getInventory().getStack(i);
                if (!s.isOf(ex.item())) continue;
                int t = Math.min(left, s.getCount());
                s.decrement(t);
                left -= t;
                units += t;
            }
        } else {
            for (int i = 0; i < p.getInventory().size(); i++) {
                ItemStack s = p.getInventory().getStack(i);
                if (s.isOf(ex.item())) { units += s.getCount(); s.setCount(0); }
                else if (s.isOf(ex.block())) { units += s.getCount() * 9; s.setCount(0); }
            }
        }
        if (units == 0) { send(p, "ws", "Нечего сдавать.", false); return; }
        int coins = units * rate;
        p.setAttached(PlayerProfile.TYPE, PlayerProfile.of(p).addCoins(coins));
        p.getServerWorld().playSound(null, p.getBlockPos(), com.derevna230.comborush.sound.ModSounds.COIN, SoundCategory.PLAYERS, 0.8f, 1f);
        send(p, "ws", "Сдано: " + units + " · +" + coins + " монет", true);
    }

    private static void send(ServerPlayerEntity p, String kind, String msg, boolean ok) {
        JsonObject root = new JsonObject();
        root.addProperty("coins", PlayerProfile.of(p).coins());
        root.addProperty("msg", msg);
        root.addProperty("ok", ok);
        JsonArray offers = new JsonArray();
        ModItems.POCKET.forEach((id, item) -> {
            JsonObject o = new JsonObject();
            o.addProperty("id", id);
            o.addProperty("item", Registries.ITEM.getId(item).toString());
            o.addProperty("name", new ItemStack(item).getName().getString());
            o.addProperty("desc", desc(id));
            o.addProperty("price", price(id));
            o.addProperty("owned", has(p, item));
            offers.add(o);
        });
        root.add("offers", offers);
        JsonArray ex = new JsonArray();
        for (Ex e : EXCHANGE) {
            JsonObject o = new JsonObject();
            o.addProperty("id", e.id());
            o.addProperty("item", Registries.ITEM.getId(e.item()).toString());
            o.addProperty("name", e.name());
            o.addProperty("rate", rate(e.id()));
            o.addProperty("have", count(p, e.item()));
            o.addProperty("blocks", count(p, e.block()));
            ex.add(o);
        }
        root.add("ex", ex);
        ServerPlayNetworking.send(p, new HudPayload(kind, root.toString()));
    }
}
