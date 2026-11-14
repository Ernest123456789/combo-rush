package com.derevna230.comborush.lobby;

import com.derevna230.comborush.data.PlayerProfile;
import com.derevna230.comborush.item.SkinData;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.world.PersistentState;

import java.util.*;

/**
 * Торговая площадка скинов между игроками (за эссенцию) и быстрая продажа игре — дешевле.
 * <ul>
 * <li>Выставить: скин уходит из инвентаря/эндер-сундука в лот (до {@value #MAX_LOTS} лотов на игрока), цена 1…1 000 000 ✦.</li>
 * <li>Купить: покупатель платит цену, продавец получает 95 % (если не в сети — при входе).</li>
 * <li>Снять лот — скин возвращается. Быстрая продажа игре — по цене редкости, износа и StatTrak (заметно ниже рыночной).</li>
 * </ul>
 * Хранится в мире (data/combo-rush_market.dat). Действия — только в меню «Противостояния».
 */
public final class SkinMarket extends PersistentState {
    public static final int MAX_LOTS = 10, MAX_PRICE = 1_000_000;
    public static final double FEE = 0.05;

    private static final class Lot {
        int id; UUID seller; String sellerName; int price; long at; ItemStack stack;
    }
    private final List<Lot> lots = new ArrayList<>();
    private final Map<UUID, Long> payouts = new HashMap<>();
    private int nextId = 1;

    private static final Type<SkinMarket> TYPE = new Type<>(SkinMarket::new, SkinMarket::read, null);

    public static SkinMarket get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE, "combo-rush_market");
    }

    public static void init() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> deliver(handler.getPlayer()));
    }

    // ---------------------------------------------------------------- хранение
    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        nbt.putInt("next", nextId);
        NbtList l = new NbtList();
        for (Lot lot : lots) {
            NbtCompound c = new NbtCompound();
            c.putInt("id", lot.id); c.putUuid("seller", lot.seller); c.putString("name", lot.sellerName);
            c.putInt("price", lot.price); c.putLong("at", lot.at);
            c.put("item", lot.stack.encode(lookup));
            l.add(c);
        }
        nbt.put("lots", l);
        NbtCompound pay = new NbtCompound();
        for (var e : payouts.entrySet()) pay.putLong(e.getKey().toString(), e.getValue());
        nbt.put("pay", pay);
        return nbt;
    }

    private static SkinMarket read(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        SkinMarket m = new SkinMarket();
        m.nextId = Math.max(1, nbt.getInt("next"));
        for (NbtElement e : nbt.getList("lots", NbtElement.COMPOUND_TYPE)) {
            NbtCompound c = (NbtCompound) e;
            Optional<ItemStack> st = ItemStack.fromNbt(lookup, c.get("item"));
            if (st.isEmpty() || st.get().isEmpty()) continue;
            Lot lot = new Lot();
            lot.id = c.getInt("id"); lot.seller = c.getUuid("seller"); lot.sellerName = c.getString("name");
            lot.price = c.getInt("price"); lot.at = c.getLong("at"); lot.stack = st.get();
            m.lots.add(lot);
        }
        NbtCompound pay = nbt.getCompound("pay");
        for (String k : pay.getKeys()) {
            try { m.payouts.put(UUID.fromString(k), pay.getLong(k)); } catch (IllegalArgumentException ignored) { }
        }
        return m;
    }

    // ---------------------------------------------------------------- цены
    private static int rarityBase(String r) {
        return switch (r) {
            case "uncommon" -> 15; case "rare" -> 60; case "epic" -> 200; case "legendary" -> 600; case "mythic" -> 2500;
            default -> 0;
        };
    }

    /** Сколько даёт игра при быстрой продаже (за весь стак) */
    public static int quickPrice(ItemStack st) {
        String r = SkinData.rarityOf(SkinData.idOf(st));
        int base = rarityBase(r);
        if (base == 0) return 0;
        double k = 1.2 - 0.5 * Math.max(0, Math.min(1, SkinData.wear(st)));
        if (SkinData.statTrak(st)) k *= 1.5;
        return Math.max(1, (int) Math.round(base * k)) * st.getCount();
    }

    private static boolean sellable(ItemStack st) { return !st.isEmpty() && !SkinData.rarityOf(SkinData.idOf(st)).isEmpty(); }

    // ---------------------------------------------------------------- предметы игрока
    private record Ref(Inventory inv, int slot, String loc, ItemStack st) {}

    private static List<Ref> refs(ServerPlayerEntity p) {
        List<Ref> out = new ArrayList<>();
        Inventory[] invs = {p.getInventory(), p.getEnderChestInventory()};
        String[] tags = {"i", "e"};
        for (int k = 0; k < 2; k++)
            for (int i = 0; i < invs[k].size(); i++) {
                ItemStack st = invs[k].getStack(i);
                if (!sellable(st)) continue;
                if (SkinData.rollable(st)) SkinData.ensure(st);
                out.add(new Ref(invs[k], i, tags[k] + i, st));
            }
        return out;
    }

    private static Ref find(ServerPlayerEntity p, String loc) {
        for (Ref r : refs(p)) if (r.loc.equals(loc)) return r;
        return null;
    }

    private static void addEssence(ServerPlayerEntity p, long n) {
        p.setAttached(PlayerProfile.TYPE, PlayerProfile.of(p).addEssence((int) Math.min(Integer.MAX_VALUE, n)));
    }

    // ---------------------------------------------------------------- действия
    /** @return null или причина отказа */
    public static String handle(ServerPlayerEntity p, String action, String arg) {
        if (!com.derevna230.comborush.universe.Universes.inStandoff(p)) return "Торговая площадка — в меню «Противостояния».";
        SkinMarket m = get(p.getServer());
        switch (action) {
            case "so_mkt_sell" -> {
                Ref r = find(p, arg);
                if (r == null) return "Скин не найден.";
                int q = quickPrice(r.st);
                if (q <= 0) return "Этот предмет игра не покупает.";
                String name = r.st.getName().getString();
                r.inv.setStack(r.slot, ItemStack.EMPTY);
                r.inv.markDirty();
                addEssence(p, q);
                p.sendMessage(Text.literal("§aПродано игре: §f" + name + " §7→ §d+" + q + " ✦"), false);
                return null;
            }
            case "so_mkt_list" -> {
                int bar = arg.indexOf('|');
                if (bar < 0) return "Неверная цена.";
                int price;
                try { price = Integer.parseInt(arg.substring(bar + 1)); } catch (NumberFormatException e) { return "Неверная цена."; }
                if (price < 1 || price > MAX_PRICE) return "Цена — от 1 до " + MAX_PRICE + " ✦.";
                long mine = m.lots.stream().filter(l -> l.seller.equals(p.getUuid())).count();
                if (mine >= MAX_LOTS) return "Не больше " + MAX_LOTS + " лотов одновременно.";
                Ref r = find(p, arg.substring(0, bar));
                if (r == null) return "Скин не найден.";
                ItemStack one = r.st.split(1);
                r.inv.markDirty();
                Lot lot = new Lot();
                lot.id = m.nextId++; lot.seller = p.getUuid(); lot.sellerName = p.getName().getString();
                lot.price = price; lot.at = System.currentTimeMillis(); lot.stack = one;
                m.lots.add(lot);
                m.markDirty();
                p.sendMessage(Text.literal("§aВыставлено: §f" + one.getName().getString() + " §7за §d" + price + " ✦"), false);
                return null;
            }
            case "so_mkt_cancel", "so_mkt_buy" -> {
                int id;
                try { id = Integer.parseInt(arg); } catch (NumberFormatException e) { return "Лот не найден."; }
                Lot lot = null;
                for (Lot l : m.lots) if (l.id == id) lot = l;
                if (lot == null) return "Этот лот уже купили или сняли.";
                boolean own = lot.seller.equals(p.getUuid());
                if (action.equals("so_mkt_cancel")) {
                    if (!own) return "Это не ваш лот.";
                    m.lots.remove(lot); m.markDirty();
                    p.getInventory().offerOrDrop(lot.stack.copy());
                    p.sendMessage(Text.literal("§7Лот снят, скин вернулся в инвентарь."), false);
                    return null;
                }
                if (own) return "Свой лот купить нельзя — его можно снять.";
                int have = PlayerProfile.of(p).essence();
                if (have < lot.price) return "Не хватает эссенции: нужно " + lot.price + " ✦.";
                m.lots.remove(lot); m.markDirty();
                addEssence(p, -lot.price);
                p.getInventory().offerOrDrop(lot.stack.copy());
                long gain = Math.max(1, Math.round(lot.price * (1 - FEE)));
                ServerPlayerEntity seller = p.getServer().getPlayerManager().getPlayer(lot.seller);
                if (seller != null) {
                    addEssence(seller, gain);
                    seller.sendMessage(Text.literal("§aВаш скин §f" + lot.stack.getName().getString() + " §aкупил §f" + p.getName().getString()
                        + " §7→ §d+" + gain + " ✦"), false);
                } else {
                    m.payouts.merge(lot.seller, gain, Long::sum);
                }
                p.sendMessage(Text.literal("§aКуплено: §f" + lot.stack.getName().getString() + " §7за §d" + lot.price + " ✦"), false);
                return null;
            }
            default -> { return null; }
        }
    }

    /** Выручка, накопившаяся, пока игрока не было */
    private static void deliver(ServerPlayerEntity p) {
        if (p.getServer() == null) return;
        SkinMarket m = get(p.getServer());
        Long v = m.payouts.remove(p.getUuid());
        if (v == null || v <= 0) return;
        m.markDirty();
        addEssence(p, v);
        p.sendMessage(Text.literal("§aПока вас не было, на торговой площадке продались ваши скины: §d+" + v + " ✦"), false);
    }

    // ---------------------------------------------------------------- состояние для экрана
    public static JsonObject json(ServerPlayerEntity p) {
        SkinMarket m = get(p.getServer());
        JsonObject o = new JsonObject();
        JsonArray lots = new JsonArray();
        List<Lot> sorted = new ArrayList<>(m.lots);
        sorted.sort(Comparator.comparingLong((Lot l) -> -l.at));
        for (Lot l : sorted) {
            JsonObject j = new JsonObject();
            j.addProperty("id", l.id);
            j.addProperty("item", SkinData.idOf(l.stack));
            j.addProperty("wf", SkinData.rollable(l.stack) ? SkinData.wear(l.stack) : -1f);
            j.addProperty("st", SkinData.statTrak(l.stack) ? 1 : 0);
            j.addProperty("price", l.price);
            j.addProperty("seller", l.sellerName);
            j.addProperty("mine", l.seller.equals(p.getUuid()));
            lots.add(j);
        }
        o.add("lots", lots);
        JsonArray mine = new JsonArray();
        for (Ref r : refs(p)) {
            JsonObject j = new JsonObject();
            j.addProperty("loc", r.loc);
            j.addProperty("item", SkinData.idOf(r.st));
            j.addProperty("wf", SkinData.rollable(r.st) ? SkinData.wear(r.st) : -1f);
            j.addProperty("st", SkinData.statTrak(r.st) ? 1 : 0);
            j.addProperty("n", r.st.getCount());
            j.addProperty("q", quickPrice(r.st));
            mine.add(j);
        }
        o.add("mine", mine);
        return o;
    }
}
