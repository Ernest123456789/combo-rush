package com.derevna230.comborush.consumable;

import com.derevna230.comborush.booster.BoosterDef;
import com.derevna230.comborush.booster.BoosterRegistry;
import com.derevna230.comborush.data.OwnedBooster;
import com.derevna230.comborush.data.PlayerProfile;
import com.derevna230.comborush.promo.PromoManager;
import com.derevna230.comborush.universe.Universes;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Применение расходника. Возвращает текст ошибки или null, если всё получилось (сам расходник списывает вызывающий) */
public class ConsumableLogic {
    private static final List<String> EDITIONS = List.of("foil", "holo", "poly");

    public static String apply(ServerPlayerEntity p, ConsumableDef d, int target) {
        PlayerProfile prof = PlayerProfile.of(p);
        List<OwnedBooster> list = prof.boosters();

        if (d.needsTarget() && (target < 0 || target >= list.size())) return "Выбери усилитель.";
        OwnedBooster o = d.needsTarget() ? list.get(target) : null;

        switch (d.action) {
            case "level_up" -> {
                if (o.level() >= OwnedBooster.MAX_LEVEL) return "Уже максимальный уровень.";
                p.setAttached(PlayerProfile.TYPE, prof.replaceBooster(target, o.withLevel(o.level() + 1)));
            }
            case "level_up_all" -> {
                PlayerProfile cur = prof;
                boolean any = false;
                for (int i = 0; i < list.size(); i++) {
                    OwnedBooster x = list.get(i);
                    if (x.level() < OwnedBooster.MAX_LEVEL) { cur = cur.replaceBooster(i, x.withLevel(x.level() + 1)); any = true; }
                }
                if (!any) return "Нечего улучшать.";
                p.setAttached(PlayerProfile.TYPE, cur);
            }
            case "edition" -> {
                if (o.edition().equals(d.edition)) return "Это издание уже есть.";
                p.setAttached(PlayerProfile.TYPE, prof.replaceBooster(target, o.withEdition(d.edition)));
            }
            case "random_edition" -> {
                List<String> pool = new ArrayList<>(EDITIONS);
                pool.remove(o.edition());
                String e = pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
                p.setAttached(PlayerProfile.TYPE, prof.replaceBooster(target, o.withEdition(e)));
            }
            case "duplicate" -> {
                if (list.size() >= prof.slotCapacity()) return "Нет свободного слота для копии.";
                p.setAttached(PlayerProfile.TYPE, prof.addBoosterRaw(new OwnedBooster(o.id(), o.dropped(), 0, o.level(), o.edition())));
            }
            case "reroll_booster" -> {
                BoosterDef cur = BoosterRegistry.get(o.id());
                String rarity = cur == null ? "common" : cur.rarity;
                List<BoosterDef> pool = new ArrayList<>();
                for (BoosterDef b : BoosterRegistry.all()) {
                    if (!b.rarity.equals(rarity) || b.id.equals(o.id()) || prof.find(b.id) != null) continue;
                    if (b.unlock_resets > prof.resets() || !Universes.worldOpen(p, b.world)) continue;
                    if (!b.promo.isEmpty() && !PromoManager.isActive(b.promo)) continue;
                    pool.add(b);
                }
                if (pool.isEmpty()) return "Нет подходящих усилителей такой же редкости.";
                BoosterDef pick = pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
                p.setAttached(PlayerProfile.TYPE, prof.replaceBooster(target, o.withId(pick.id)));
            }
            case "city_booster" -> {
                BoosterDef cur = BoosterRegistry.get(o.id());
                if (cur != null && !cur.world.isEmpty()) return "Это уже усилитель города — выбери другой.";
                List<BoosterDef> pool = new ArrayList<>();
                for (BoosterDef b : BoosterRegistry.all()) {
                    if (!b.world.equals("night_city") || prof.find(b.id) != null) continue;
                    if (b.unlock_resets > prof.resets() || b.min_level > prof.level()) continue;
                    pool.add(b);
                }
                if (pool.isEmpty()) return "У тебя уже есть все усилители города.";
                BoosterDef pick = pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
                p.setAttached(PlayerProfile.TYPE, prof.replaceBooster(target, o.withId(pick.id)));
            }
            case "add_slot" -> {
                if (prof.extraSlots() >= 4) return "Максимум дополнительных слотов.";
                p.setAttached(PlayerProfile.TYPE, prof.addExtraSlot());
            }
            default -> { }
        }
        return null;
    }
}
