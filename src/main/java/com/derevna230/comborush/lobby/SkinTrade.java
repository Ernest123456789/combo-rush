package com.derevna230.comborush.lobby;

import com.derevna230.comborush.ComboRush;
import com.derevna230.comborush.item.GunDefs;
import com.derevna230.comborush.item.SkinData;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Контракт обмена, как в CS2: 10 скинов одной редкости → один скин редкостью выше (нельзя смешивать StatTrak™ с обычными);
 * 5 «Тайных» скинов → скин ножа (Нож-коготь или Штык). Износ результата — средний износ вложенных скинов, пересчитанный в окно нового скина.
 * Предметы берутся из инвентаря и эндер-сундука; место задаётся строкой «i<слот>» или «e<слот>».
 */
public final class SkinTrade {
    private SkinTrade() {}

    private static final Map<UUID, JsonObject> RESULTS = new HashMap<>();

    private record Ref(Inventory inv, int slot, String loc, ItemStack st) {}

    private static boolean tradeable(ItemStack st) {
        if (st.isEmpty() || !SkinData.rollable(st) || st.getItem() instanceof com.derevna230.comborush.item.CRGrenadeItem) return false;
        String r = SkinData.rarityOf(SkinData.idOf(st));
        return !r.equals("mythic");          // ножи дальше не обменять
    }

    private static List<Ref> refs(ServerPlayerEntity p) {
        List<Ref> out = new ArrayList<>();
        scan(p.getInventory(), "i", out);
        scan(p.getEnderChestInventory(), "e", out);
        return out;
    }

    private static void scan(Inventory inv, String tag, List<Ref> out) {
        for (int i = 0; i < inv.size(); i++) {
            ItemStack st = inv.getStack(i);
            if (!tradeable(st)) continue;
            SkinData.ensure(st);
            out.add(new Ref(inv, i, tag + i, st));
        }
    }

    /** Скины, которые можно положить в контракт (для экрана) */
    public static JsonArray stacksJson(ServerPlayerEntity p) {
        JsonArray a = new JsonArray();
        for (Ref r : refs(p)) {
            JsonObject o = new JsonObject();
            o.addProperty("id", SkinData.idOf(r.st));
            o.addProperty("loc", r.loc);
            o.addProperty("wf", SkinData.wear(r.st));
            o.addProperty("st", SkinData.statTrak(r.st) ? 1 : 0);
            a.add(o);
        }
        return a;
    }

    /** Итог последнего контракта — один раз уходит на экран вместе с состоянием */
    public static JsonObject takeResult(UUID id) { return RESULTS.remove(id); }

    public static int need(String rarity) { return rarity.equals("legendary") ? 5 : 10; }

    public static String next(String rarity) {
        int i = SkinData.RARITIES.indexOf(rarity);
        return i < 0 || i + 1 >= SkinData.RARITIES.size() ? "" : SkinData.RARITIES.get(i + 1);
    }

    /** @return null или причина отказа */
    public static String execute(ServerPlayerEntity p, String locs) {
        Map<String, Ref> byLoc = new HashMap<>();
        for (Ref r : refs(p)) byLoc.put(r.loc, r);
        List<Ref> picked = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String l : locs.split(",")) {
            l = l.trim();
            if (l.isEmpty()) continue;
            Ref r = byLoc.get(l);
            if (r == null || !seen.add(l)) return "Контракт неверный: предмета уже нет на месте.";
            picked.add(r);
        }
        if (picked.isEmpty()) return "Контракт пуст.";
        String rar = SkinData.rarityOf(SkinData.idOf(picked.get(0).st));
        int need = need(rar);
        if (picked.size() != need) return "Нужно ровно " + need + " скинов этой редкости.";
        boolean stat = SkinData.statTrak(picked.get(0).st);
        float sum = 0;
        for (Ref r : picked) {
            if (!SkinData.rarityOf(SkinData.idOf(r.st)).equals(rar)) return "Все скины в контракте должны быть одной редкости.";
            if (SkinData.statTrak(r.st) != stat) return "Нельзя смешивать StatTrak™ и обычные скины.";
            sum += SkinData.norm(r.st);
        }
        String target = next(rar);
        if (target.isEmpty()) return "Из этой редкости обменять нельзя.";

        // пул результата; шанс скина выше у того же ствола, что среди вложенных
        List<GunDefs.Skin> pool = new ArrayList<>();
        for (GunDefs.Skin s : GunDefs.SKINS) if (s.rarity().equals(target) && !GunDefs.isGrenadeBase(s.base())) pool.add(s);
        if (pool.isEmpty()) return "Для этой редкости пока нет скинов.";
        double[] w = new double[pool.size()];
        double total = 0;
        for (int i = 0; i < pool.size(); i++) {
            int same = 0;
            for (Ref r : picked) if (GunDefs.baseOf(SkinData.idOf(r.st)).equals(pool.get(i).base())) same++;
            w[i] = 1 + same;
            total += w[i];
        }
        double x = ThreadLocalRandom.current().nextDouble() * total;
        int pick = 0;
        for (; pick < w.length - 1; pick++) { x -= w[pick]; if (x < 0) break; }
        GunDefs.Skin out = pool.get(pick);

        var item = Registries.ITEM.get(Identifier.of(ComboRush.MOD_ID, out.id()));
        ItemStack reward = new ItemStack(item);
        float[] win = SkinData.window(out.id());
        float wf = win[0] + (sum / picked.size()) * (win[1] - win[0]);
        SkinData.apply(reward, wf, ThreadLocalRandom.current().nextInt(1000), stat);

        for (Ref r : picked) r.inv.setStack(r.slot, ItemStack.EMPTY);
        p.getInventory().offerOrDrop(reward);
        p.getServerWorld().playSound(null, p.getBlockPos(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 0.7f, 1.2f);

        JsonObject res = new JsonObject();
        res.addProperty("id", out.id());
        res.addProperty("name", reward.getName().getString());
        res.addProperty("wf", wf);
        res.addProperty("rar", out.rarity());
        res.addProperty("st", stat ? 1 : 0);
        RESULTS.put(p.getUuid(), res);
        return null;
    }
}
