package com.derevna230.comborush.cases;

import com.derevna230.comborush.armor.ArmorSetDef;
import com.derevna230.comborush.armor.ArmorSetRegistry;
import com.derevna230.comborush.armor.ArmorTooltips;
import com.derevna230.comborush.data.PlayerProfile;
import com.derevna230.comborush.hud.HudPayload;
import com.derevna230.comborush.hud.HudSync;
import com.derevna230.comborush.item.CaseItem;
import com.derevna230.comborush.item.ModItems;
import com.derevna230.comborush.shop.ShopService.Res;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.ServerTask;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Покупка кейсов за эссенцию, открытие (рулетка) и разбор лишних предметов обратно в эссенцию */
public final class CaseService {
    private CaseService() {}
    private static final Gson GSON = new Gson();
    public static final int STRIP = 52, WIN = 45;

    public static int scrapValue(String rarity) {
        return switch (rarity) { case "uncommon" -> 4; case "rare" -> 7; case "epic" -> 12; case "legendary" -> 20; case "mythic" -> 60; default -> 2; };
    }

    public static Res buy(ServerPlayerEntity p, String caseId) {
        CaseDef def = CaseRegistry.get(caseId);
        return buy(p, caseId, def == null ? 0 : def.price);
    }

    /** Покупка по своей цене (скидки Торговца Редстоуна); валюта — как у кейса */
    public static Res buy(ServerPlayerEntity p, String caseId, int price) {
        CaseDef def = CaseRegistry.get(caseId);
        Item item = ModItems.CASES.get(caseId);
        if (def == null || item == null) return Res.err("Такого кейса нет.");
        if (def.noShop) return Res.err("Этот кейс не продаётся: он выпадает за матчи.");
        if (def.paysDiamonds()) {
            if (countDiamonds(p) < price) return Res.err("Не хватает алмазов: нужно " + price + ".");
            takeDiamonds(p, price);
        } else {
            PlayerProfile pr = PlayerProfile.of(p);
            if (pr.essence() < price) return Res.err("Не хватает эссенции: нужно " + price + ".");
            p.setAttached(PlayerProfile.TYPE, pr.addEssence(-price));
        }
        p.getInventory().offerOrDrop(new ItemStack(item));
        return Res.ok("Куплен " + def.name + ". Он в инвентаре — ПКМ, чтобы открыть.");
    }

    public static int countDiamonds(ServerPlayerEntity p) {
        int n = 0;
        var inv = p.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack st = inv.getStack(i);
            if (st.isOf(Items.DIAMOND)) n += st.getCount();
        }
        return n;
    }

    public static void takeDiamonds(ServerPlayerEntity p, int amount) {
        var inv = p.getInventory();
        for (int i = 0; i < inv.size() && amount > 0; i++) {
            ItemStack st = inv.getStack(i);
            if (!st.isOf(Items.DIAMOND)) continue;
            int t = Math.min(amount, st.getCount());
            st.decrement(t);
            amount -= t;
        }
        inv.markDirty();
    }

    /** Разобрать предмет в основной руке */
    public static Res scrap(ServerPlayerEntity p) {
        ItemStack st = p.getStackInHand(Hand.MAIN_HAND);
        String set = ModItems.ITEM_SET.get(st.getItem());
        if (set == null) return Res.err("Возьми в руку предмет из кейса: броню, оружие или питомца.");
        int value = scrapValue(ModItems.rarityOf(set));
        String name = st.getName().getString();
        st.decrement(1);
        p.setAttached(PlayerProfile.TYPE, PlayerProfile.of(p).addEssence(value));
        return Res.ok("Разобрано: " + name + " → +" + value + " эссенции");
    }

    private static CaseDef.Drop roll(CaseDef def) {
        int r = ThreadLocalRandom.current().nextInt(def.totalWeight());
        for (CaseDef.Drop d : def.drops) { r -= Math.max(0, d.weight); if (r < 0) return d; }
        return def.drops.get(def.drops.size() - 1);
    }

    private static Item pieceOf(String set) {
        List<Item> l = ModItems.SET_ITEMS.get(set);
        if (l == null || l.isEmpty()) return null;
        return l.get(ThreadLocalRandom.current().nextInt(l.size()));
    }

    /** ПКМ кейсом в руке: если нужен ключ — сначала запрос на его покупку */
    public static void open(ServerPlayerEntity p, ItemStack stack, String caseId) {
        CaseDef def = CaseRegistry.get(caseId);
        if (def != null && def.needsKey() && !def.drops.isEmpty()) { askKey(p, def); return; }
        openNow(p, stack, caseId);
    }

    /** Экран подтверждения: «Купить ключ за N эссенции и открыть кейс» */
    private static void askKey(ServerPlayerEntity p, CaseDef def) {
        PlayerProfile pr = PlayerProfile.of(p);
        JsonObject o = new JsonObject();
        o.addProperty("case", def.id);
        o.addProperty("name", def.name);
        o.addProperty("rar", def.rarity);
        o.addProperty("price", def.keyPrice);
        o.addProperty("essence", pr.essence());
        if (HudSync.has(p)) ServerPlayNetworking.send(p, new HudPayload("case_key", GSON.toJson(o)));
        else p.sendMessage(Text.literal("§eДля «" + def.name + "» нужен ключ: " + def.keyPrice + " эссенции. Установите мод на клиенте, чтобы купить его."), false);
    }

    /** Подтверждение с экрана: списать эссенцию за ключ и открыть кейс из инвентаря */
    public static Res buyKeyAndOpen(ServerPlayerEntity p, String caseId) {
        CaseDef def = CaseRegistry.get(caseId);
        if (def == null || !def.needsKey()) return Res.err("Этому кейсу ключ не нужен.");
        var inv = p.getInventory();
        ItemStack found = null;
        for (int i = 0; i < inv.size() && found == null; i++) {
            ItemStack st = inv.getStack(i);
            if (st.getItem() instanceof CaseItem ci && ci.caseId().equals(caseId)) found = st;
        }
        if (found == null) return Res.err("Кейса уже нет в инвентаре.");
        PlayerProfile pr = PlayerProfile.of(p);
        if (pr.essence() < def.keyPrice) return Res.err("Не хватает эссенции на ключ: нужно " + def.keyPrice + ".");
        p.setAttached(PlayerProfile.TYPE, pr.addEssence(-def.keyPrice));
        p.sendMessage(Text.literal("§7Ключ куплен: §d-" + def.keyPrice + " эссенции§7."), false);
        openNow(p, found, caseId);
        return Res.ok("");
    }

    private static void openNow(ServerPlayerEntity p, ItemStack stack, String caseId) {
        CaseDef def = CaseRegistry.get(caseId);
        if (def == null || def.drops.isEmpty()) { p.sendMessage(Text.literal("§cЭтот кейс пока нельзя открыть."), true); return; }

        CaseDef.Drop win = roll(def);
        Item reward = pieceOf(win.set);
        if (reward == null) { p.sendMessage(Text.literal("§cВ кейсе ошибка: нет предмета «" + win.set + "»."), false); return; }

        stack.decrement(1);
        ItemStack rewardStack = new ItemStack(reward);
        String rewardName = rewardStack.getName().getString();
        // Награду кладём на следующем шаге сервера: в креативе после ПКМ игра возвращает предмет в руку
        // и перезаписала бы слот, куда только что легла награда (из-за этого в креативе «ничего не выпадало»).
        ItemStack give = rewardStack.copy();
        p.getServer().send(new ServerTask(p.getServer().getTicks(), () -> p.getInventory().offerOrDrop(give)));
        String rar = ModItems.rarityOf(win.set);
        ArmorSetDef setDef = ArmorSetRegistry.get(win.set);

        // полоса рулетки: случайные предметы по тем же шансам, победный — на позиции WIN
        JsonArray items = new JsonArray();
        for (int i = 0; i < STRIP; i++) {
            String set = i == WIN ? win.set : roll(def).set;
            Item it = i == WIN ? reward : pieceOf(set);
            if (it == null) { set = win.set; it = reward; }
            JsonObject o = new JsonObject();
            o.addProperty("id", Registries.ITEM.getId(it).toString());
            o.addProperty("rar", ModItems.rarityOf(set));
            items.add(o);
        }
        JsonObject root = new JsonObject();
        root.addProperty("case", caseId);
        root.addProperty("caseName", def.name);
        root.addProperty("caseRar", def.rarity);
        root.addProperty("win", WIN);
        root.addProperty("off", 0.15 + ThreadLocalRandom.current().nextDouble() * 0.7);
        root.addProperty("name", rewardName);
        root.addProperty("rar", rar);
        root.addProperty("set", ModItems.piecesIn(win.set) > 1 ? (setDef == null ? win.set : setDef.name) : "");   // у оружия, питомцев и шляп — без «Комплект:»
        root.addProperty("left", countCases(p, caseId));
        root.add("items", items);

        if (HudSync.has(p)) ServerPlayNetworking.send(p, new HudPayload("case_open", GSON.toJson(root)));
        else p.sendMessage(Text.literal("§6" + def.name + ": §f" + rewardName), false);

        if (rar.equals("epic") || rar.equals("legendary")) {
            p.getServer().getPlayerManager().broadcast(Text.literal("§d" + p.getName().getString() + " §fоткрыл "
                + def.name + " и получил " + ArmorTooltips.rarCode(rar) + "§l" + rewardName + "§r§f!"), false);
            p.getServerWorld().playSound(null, p.getBlockPos(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 0.6f, 1.0f);
        }
    }

    /** Кнопка «Открыть ещё» на экране рулетки: берёт такой же кейс из инвентаря */
    public static Res openAgain(ServerPlayerEntity p, String caseId) {
        var inv = p.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack st = inv.getStack(i);
            if (st.getItem() instanceof CaseItem ci && ci.caseId().equals(caseId)) {
                CaseDef d = CaseRegistry.get(caseId);
                if (d != null && d.needsKey()) { askKey(p, d); return Res.ok(""); }
                openNow(p, st, caseId);
                return Res.ok("");
            }
        }
        return Res.err("Таких кейсов больше нет.");
    }

    public static int countCases(ServerPlayerEntity p, String caseId) {
        int n = 0;
        var inv = p.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack st = inv.getStack(i);
            if (st.getItem() instanceof CaseItem ci && ci.caseId().equals(caseId)) n += st.getCount();
        }
        return n;
    }
}
