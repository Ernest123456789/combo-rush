package com.derevna230.comborush.skate;

import com.derevna230.comborush.data.PlayerProfile;
import com.derevna230.comborush.hud.HudPayload;
import com.derevna230.comborush.hud.HudSync;
import com.derevna230.comborush.hud.ShopActionPayload;
import com.derevna230.comborush.item.ModItems;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;

/** Меню прокачки скейта (экран на клиенте). Скейт должен быть в основной руке */
public final class SkateNet {
    private SkateNet() {}

    public static void open(ServerPlayerEntity p) {
        if (!HudSync.has(p)) {
            p.sendMessage(Text.literal("§cМеню прокачки скейта работает с клиентской частью мода."), false);
            return;
        }
        send(p, "skate_open", "", true);
    }

    public static void handle(ServerPlayerEntity p, ShopActionPayload a) {
        if (!a.action().equals("skate_up")) return;
        String key = a.c();
        ItemStack st = p.getStackInHand(Hand.MAIN_HAND);
        String msg; boolean ok = false;
        if (!st.isOf(ModItems.SKATEBOARD)) msg = "Возьми скейт в основную руку.";
        else if (!java.util.Arrays.asList(SkateData.KEYS).contains(key)) msg = "Нет такого улучшения.";
        else {
            int lvl = SkateData.get(st, key);
            int cost = SkateData.cost(key, lvl);
            PlayerProfile pr = PlayerProfile.of(p);
            if (lvl >= SkateData.max(key) || cost < 0) msg = "Уже максимальный уровень.";
            else if (pr.essence() < cost) msg = "Не хватает эссенции: нужно " + cost + ".";
            else {
                p.setAttached(PlayerProfile.TYPE, pr.addEssence(-cost));
                SkateData.set(st, key, lvl + 1);
                msg = "Улучшено: " + SkateData.levelText(key, lvl + 1);
                ok = true;
                p.getServerWorld().playSound(null, p.getBlockPos(), SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE, SoundCategory.PLAYERS, 1f, 1.2f);
            }
        }
        send(p, "skate", msg, ok);
    }

    private static void send(ServerPlayerEntity p, String kind, String msg, boolean ok) {
        ItemStack st = p.getStackInHand(Hand.MAIN_HAND);
        JsonObject root = new JsonObject();
        root.addProperty("essence", PlayerProfile.of(p).essence());
        root.addProperty("msg", msg);
        root.addProperty("ok", ok);
        root.addProperty("has", st.isOf(ModItems.SKATEBOARD));
        JsonArray ups = new JsonArray();
        for (String k : SkateData.KEYS) {
            int lvl = st.isOf(ModItems.SKATEBOARD) ? SkateData.get(st, k) : 0;
            JsonObject o = new JsonObject();
            o.addProperty("key", k);
            o.addProperty("lvl", lvl);
            o.addProperty("max", SkateData.max(k));
            o.addProperty("cost", SkateData.cost(k, lvl));
            o.addProperty("now", SkateData.levelText(k, lvl));
            o.addProperty("next", lvl < SkateData.max(k) ? SkateData.levelText(k, lvl + 1) : "");
            ups.add(o);
        }
        root.add("ups", ups);
        ServerPlayNetworking.send(p, new HudPayload(kind, root.toString()));
    }
}
