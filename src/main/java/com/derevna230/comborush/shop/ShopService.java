package com.derevna230.comborush.shop;

import com.derevna230.comborush.booster.BoosterApplier;
import com.derevna230.comborush.booster.BoosterDef;
import com.derevna230.comborush.booster.BoosterRegistry;
import com.derevna230.comborush.booster.Rarity;
import com.derevna230.comborush.consumable.ConsumableDef;
import com.derevna230.comborush.consumable.ConsumableLogic;
import com.derevna230.comborush.consumable.ConsumableRegistry;
import com.derevna230.comborush.data.OwnedBooster;
import com.derevna230.comborush.data.PlayerProfile;
import com.derevna230.comborush.promo.PromoManager;
import com.derevna230.comborush.score.BoosterEffects;
import com.derevna230.comborush.species.SpeciesRegistry;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.function.ToIntFunction;

/** Вся логика магазина. Её используют и сундучный интерфейс (sgui), и клиентский экран в стиле Balatro */
public final class ShopService {
    private ShopService() {}

    public record Res(boolean ok, String msg) {
        public static Res ok(String m) { return new Res(true, m); }
        public static Res err(String m) { return new Res(false, m); }
    }

    public static final class State {
        public int forEvent = -1, rerolls = 0;
        public int pending = -1;                          // только для сундучного GUI
        public List<String> offers = new ArrayList<>();   // усилители
        public List<String> cOffers = new ArrayList<>();  // расходники
    }

    public static final double PRICE_GROWTH = 1.25;
    private static final Map<UUID, State> STATES = new HashMap<>();

    public static void invalidate(UUID id) { STATES.remove(id); }

    /** Состояние витрины игрока; после каждого пройденного события витрина генерируется заново */
    public static State state(ServerPlayerEntity p) {
        State st = STATES.computeIfAbsent(p.getUuid(), k -> new State());
        int ev = PlayerProfile.of(p).eventsDone();
        if (st.forEvent != ev) { st.forEvent = ev; st.rerolls = 0; rollOffers(p, st); }
        return st;
    }

    public static BoosterEffects fx(ServerPlayerEntity p) { return BoosterEffects.of(p, PlayerProfile.of(p)); }

    // ---------------- цены ----------------
    public static double priceMult(PlayerProfile p, BoosterEffects fx) {
        return SpeciesRegistry.get(p.species()).shop_price_mult * (1 - Math.min(0.5, fx.shopDiscount));
    }
    public static int price(PlayerProfile p, BoosterDef d, BoosterEffects fx) {
        double v = d.price * Math.pow(PRICE_GROWTH, p.purchasesOf(d.id)) * priceMult(p, fx);
        return Math.max(1, (int) Math.ceil(v));
    }
    public static int cPrice(PlayerProfile p, ConsumableDef d, BoosterEffects fx) {
        return Math.max(1, (int) Math.ceil(d.price * priceMult(p, fx)));
    }
    public static int rerollCost(State st, BoosterEffects fx) { return Math.max(1, 5 + 2 * st.rerolls - fx.rerollDiscount); }
    public static int sellValue(OwnedBooster o, BoosterEffects fx) {
        return (int) Math.round(o.paid() * Math.min(1.0, 0.5 + fx.sellBonus)) + 4 * o.level();
    }
    public static int cSellValue(ConsumableDef d) { return Math.max(1, d.price / 2); }
    public static int slotCost(PlayerProfile p) { return 200 * (1 << p.extraSlots()); }

    // ---------------- витрина (редкость задаёт шанс выпадения) ----------------
    private static <T> List<String> pickWeighted(List<T> pool, int n, ToIntFunction<T> weight, Function<T, String> id) {
        List<T> p = new ArrayList<>(pool);
        List<String> out = new ArrayList<>();
        while (out.size() < n && !p.isEmpty()) {
            int total = p.stream().mapToInt(weight).sum();
            int roll = ThreadLocalRandom.current().nextInt(Math.max(1, total));
            for (int i = 0; i < p.size(); i++) {
                roll -= weight.applyAsInt(p.get(i));
                if (roll < 0 || i == p.size() - 1) { out.add(id.apply(p.get(i))); p.remove(i); break; }
            }
        }
        return out;
    }

    public static void rollOffers(ServerPlayerEntity player, State st) {
        PlayerProfile p = PlayerProfile.of(player);
        List<BoosterDef> pool = new ArrayList<>();
        for (BoosterDef d : BoosterRegistry.all()) {
            if (d.min_level > p.level() || d.unlock_resets > p.resets() || p.find(d.id) != null) continue;
            if (!d.promo.isEmpty() && !PromoManager.isActive(d.promo)) continue;
            pool.add(d);
        }
        st.offers = pickWeighted(pool, 3, d -> Rarity.weight(d.rarity), d -> d.id);
        st.cOffers = pickWeighted(new ArrayList<>(ConsumableRegistry.all()), 2, d -> Rarity.weight(d.rarity), d -> d.id);
    }

    // ---------------- действия ----------------
    /** mode: "" — обычный, "game" — негативный без игровой части, "score" — негативный без очковой */
    public static Res buy(ServerPlayerEntity p, int idx, String mode) {
        State st = state(p);
        PlayerProfile pr = PlayerProfile.of(p);
        if (idx < 0 || idx >= st.offers.size()) return Res.err("Предложение недоступно.");
        BoosterDef d = BoosterRegistry.get(st.offers.get(idx));
        if (d == null) return Res.err("Предложение недоступно.");

        String dropped = mode == null ? "" : mode;
        if (!dropped.isEmpty() && !dropped.equals("game") && !dropped.equals("score")) return Res.err("Неизвестный режим покупки.");
        if (!dropped.isEmpty() && !d.canBeNegative()) return Res.err("Этот усилитель нельзя сделать негативным.");
        boolean negative = !dropped.isEmpty();
        int price = price(pr, d, fx(p));

        if (pr.coins() < price) return Res.err("Не хватает монет.");
        if (pr.boosters().size() >= pr.slotCapacity() + (negative ? 1 : 0))
            return Res.err("Нет свободных слотов. Продай усилитель или купи негативный.");

        p.setAttached(PlayerProfile.TYPE, pr.addCoins(-price).addBooster(new OwnedBooster(d.id, dropped, price)));
        com.derevna230.comborush.campaign.Campaign.trigger(p, "booster", "", 1);
        st.offers.remove(idx);
        BoosterApplier.sync(p);
        return Res.ok("Куплено: " + d.name);
    }

    public static Res buyConsumable(ServerPlayerEntity p, int idx) {
        State st = state(p);
        PlayerProfile pr = PlayerProfile.of(p);
        if (idx < 0 || idx >= st.cOffers.size()) return Res.err("Предложение недоступно.");
        ConsumableDef d = ConsumableRegistry.get(st.cOffers.get(idx));
        if (d == null) return Res.err("Предложение недоступно.");
        int price = cPrice(pr, d, fx(p));
        if (pr.consumables().size() >= PlayerProfile.CONSUMABLE_SLOTS) return Res.err("Слоты расходников заняты.");
        if (pr.coins() < price) return Res.err("Не хватает монет.");
        p.setAttached(PlayerProfile.TYPE, pr.addCoins(-price).addConsumable(d.id));
        st.cOffers.remove(idx);
        return Res.ok("Куплено: " + d.name);
    }

    public static Res reroll(ServerPlayerEntity p) {
        State st = state(p);
        PlayerProfile pr = PlayerProfile.of(p);
        int cost = rerollCost(st, fx(p));
        if (pr.coins() < cost) return Res.err("Не хватает монет.");
        p.setAttached(PlayerProfile.TYPE, pr.addCoins(-cost));
        st.rerolls++;
        rollOffers(p, st);
        return Res.ok("Витрина обновлена.");
    }

    public static Res sell(ServerPlayerEntity p, int idx) {
        PlayerProfile pr = PlayerProfile.of(p);
        if (idx < 0 || idx >= pr.boosters().size()) return Res.err("Усилитель не найден.");
        int value = sellValue(pr.boosters().get(idx), fx(p));
        p.setAttached(PlayerProfile.TYPE, pr.removeBooster(idx).addCoins(value));
        BoosterApplier.sync(p);
        return Res.ok("Продано за " + value);
    }

    public static Res sellConsumable(ServerPlayerEntity p, int idx) {
        PlayerProfile pr = PlayerProfile.of(p);
        if (idx < 0 || idx >= pr.consumables().size()) return Res.err("Расходник не найден.");
        ConsumableDef d = ConsumableRegistry.get(pr.consumables().get(idx));
        int value = d == null ? 1 : cSellValue(d);
        p.setAttached(PlayerProfile.TYPE, pr.removeConsumable(idx).addCoins(value));
        return Res.ok("Продано за " + value);
    }

    /** target — номер усилителя; для расходников без цели игнорируется */
    public static Res useConsumable(ServerPlayerEntity p, int cIdx, int target) {
        PlayerProfile pr = PlayerProfile.of(p);
        if (cIdx < 0 || cIdx >= pr.consumables().size()) return Res.err("Расходник не найден.");
        ConsumableDef d = ConsumableRegistry.get(pr.consumables().get(cIdx));
        if (d == null) return Res.err("Расходник не найден.");

        if (d.action.equals("free_reroll")) {
            rollOffers(p, state(p));
            p.setAttached(PlayerProfile.TYPE, pr.removeConsumable(cIdx));
            return Res.ok("Витрина обновлена бесплатно.");
        }
        if (d.needsTarget() && (target < 0 || target >= pr.boosters().size())) return Res.err("Выбери усилитель.");

        String err = ConsumableLogic.apply(p, d, d.needsTarget() ? target : -1);
        if (err != null) return Res.err(err);
        p.setAttached(PlayerProfile.TYPE, PlayerProfile.of(p).removeConsumable(cIdx));
        BoosterApplier.sync(p);
        return Res.ok("Использовано: " + d.name);
    }

    public static Res buyExtraSlot(ServerPlayerEntity p) {
        PlayerProfile pr = PlayerProfile.of(p);
        int cost = slotCost(pr);
        if (pr.extraSlots() >= 4) return Res.err("Максимум доп. слотов.");
        if (pr.coins() < cost) return Res.err("Не хватает монет.");
        p.setAttached(PlayerProfile.TYPE, pr.addCoins(-cost).addExtraSlot());
        return Res.ok("Слот куплен.");
    }
}
