package com.derevna230.comborush.data;

import com.derevna230.comborush.ComboRush;
import com.derevna230.comborush.species.SpeciesRegistry;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Данные игрока.
 * Сбрасываются при престиже: монеты, события, очки забега, усилители, доп. слоты, покупки, статистика забега.
 * Сохраняются: существо, число сбросов, ранги, эссенция, статистика "life_*" и лучший результат.
 */
public record PlayerProfile(int coins, int eventsDone, long totalScore,
                            List<OwnedBooster> boosters, int extraSlots,
                            Map<String, Integer> purchases,
                            String species, int resets,
                            Map<String, Integer> ranks, int essence,
                            Map<String, Long> stats,
                            List<String> cosmetics, Map<String, String> equipped,
                            List<String> consumables) {

    public static final PlayerProfile EMPTY = new PlayerProfile(0, 0, 0, List.of(), 0, Map.of(),
        SpeciesRegistry.DEFAULT_ID, 0, Map.of(), 0, Map.of(), List.of(), Map.of(), List.of());

    public static final Codec<PlayerProfile> CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.INT.optionalFieldOf("coins", 0).forGetter(PlayerProfile::coins),
        Codec.INT.optionalFieldOf("events_done", 0).forGetter(PlayerProfile::eventsDone),
        Codec.LONG.optionalFieldOf("total_score", 0L).forGetter(PlayerProfile::totalScore),
        OwnedBooster.CODEC.listOf().optionalFieldOf("boosters", List.of()).forGetter(PlayerProfile::boosters),
        Codec.INT.optionalFieldOf("extra_slots", 0).forGetter(PlayerProfile::extraSlots),
        Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("purchases", Map.of())
            .forGetter(PlayerProfile::purchases),
        Codec.STRING.optionalFieldOf("species", SpeciesRegistry.DEFAULT_ID).forGetter(PlayerProfile::species),
        Codec.INT.optionalFieldOf("resets", 0).forGetter(PlayerProfile::resets),
        Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("ranks", Map.of())
            .forGetter(PlayerProfile::ranks),
        Codec.INT.optionalFieldOf("essence", 0).forGetter(PlayerProfile::essence),
        Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("stats", Map.of())
            .forGetter(PlayerProfile::stats),
        Codec.STRING.listOf().optionalFieldOf("cosmetics", List.of()).forGetter(PlayerProfile::cosmetics),
        Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("equipped", Map.of())
            .forGetter(PlayerProfile::equipped),
        Codec.STRING.listOf().optionalFieldOf("consumables", List.of()).forGetter(PlayerProfile::consumables)
    ).apply(i, PlayerProfile::new));

    public static final AttachmentType<PlayerProfile> TYPE = AttachmentRegistry.create(
        Identifier.of(ComboRush.MOD_ID, "profile"),
        b -> b.persistent(CODEC).copyOnDeath().initializer(() -> EMPTY));

    public static PlayerProfile of(ServerPlayerEntity p) { return p.getAttachedOrElse(TYPE, EMPTY); }

    // ---------- уровень и слоты (каждый забег начинается с 3 слотов) ----------
    /** Уровень 15 ≈ 13 700 очков, уровень 30 ≈ 59 000 очков за забег */
    public int level() { return 1 + (int) Math.floor(Math.sqrt(totalScore / 70.0)); }
    public int baseSlots() { return level() >= 30 ? 5 : level() >= 15 ? 4 : 3; }
    public int slotCapacity() {
        long negatives = boosters.stream().filter(OwnedBooster::negative).count();
        return Math.min(9, baseSlots() + extraSlots + (int) negatives);
    }

    public OwnedBooster find(String id) {
        return boosters.stream().filter(b -> b.id().equals(id)).findFirst().orElse(null);
    }
    public int purchasesOf(String id) { return purchases.getOrDefault(id, 0); }

    // ---------- ранги и статистика ----------
    public int rank() { return rankOf(species); }
    public int rankOf(String speciesId) { return ranks.getOrDefault(speciesId, 0); }
    public long statLong(String key) { return stats.getOrDefault(key, 0L); }

    /**
     * Забег начат: игрок выбрал существо или подтвердил старт (/cr ready). До этого события его не засчитывают,
     * и существо можно выбрать в любой момент. Флаг сбрасывается вместе с забегом.
     * Старые профили: уже играли и выбрали не человека — считаются начатыми.
     */
    public boolean started() {
        return stats.getOrDefault("run_started", 0L) > 0
            || (eventsDone > 0 && !species.equals(SpeciesRegistry.DEFAULT_ID));
    }
    public PlayerProfile withStarted() { B b = b(); b.stats.put("run_started", 1L); return b.build(); }

    /** Значение для масштабирования усилителей (ключи см. README) */
    public double stat(String key) {
        return switch (key) {
            case "resets"     -> (double) resets;
            case "events"     -> (double) eventsDone;
            case "coins"      -> (double) coins;
            case "level"      -> (double) level();
            case "boosters"   -> (double) boosters.size();
            case "free_slots" -> (double) Math.max(0, slotCapacity() - boosters.size());
            case "rank"       -> (double) rank();
            case "essence"    -> (double) essence;
            case "score"      -> (double) totalScore;
            default           -> (double) stats.getOrDefault(key, 0L);
        };
    }

    // ---------- изменения (record неизменяемый, возвращаем копию) ----------
    private static final class B {
        int coins, eventsDone, extraSlots, resets, essence;
        long totalScore;
        String species;
        List<OwnedBooster> boosters;
        Map<String, Integer> purchases, ranks;
        Map<String, Long> stats;
        List<String> cosmetics;
        Map<String, String> equipped;
        List<String> consumables;

        PlayerProfile build() {
            return new PlayerProfile(coins, eventsDone, totalScore, List.copyOf(boosters), extraSlots,
                Map.copyOf(purchases), species, resets, Map.copyOf(ranks), essence, Map.copyOf(stats),
                List.copyOf(cosmetics), Map.copyOf(equipped), List.copyOf(consumables));
        }
    }

    private B b() {
        B b = new B();
        b.coins = coins; b.eventsDone = eventsDone; b.totalScore = totalScore;
        b.boosters = new ArrayList<>(boosters); b.extraSlots = extraSlots;
        b.purchases = new HashMap<>(purchases); b.species = species; b.resets = resets;
        b.ranks = new HashMap<>(ranks); b.essence = essence; b.stats = new HashMap<>(stats);
        b.cosmetics = new ArrayList<>(cosmetics); b.equipped = new HashMap<>(equipped);
        b.consumables = new ArrayList<>(consumables);
        return b;
    }

    /** Записать число в статистику (ключи life_* переживают сброс прогресса) */
    public PlayerProfile withStat(String key, long value) { B b = b(); b.stats.put(key, value); return b.build(); }
    public PlayerProfile addCoins(int c) { B b = b(); b.coins += c; return b.build(); }
    public PlayerProfile addTotalScore(long s) { B b = b(); b.totalScore += s; return b.build(); }
    public PlayerProfile addExtraSlot() { B b = b(); b.extraSlots++; return b.build(); }
    public PlayerProfile withSpecies(String id) { B b = b(); b.species = id; return b.build(); }
    public PlayerProfile withEvents(int n) { B b = b(); b.eventsDone = Math.max(0, n); return b.build(); }

    public PlayerProfile addBooster(OwnedBooster o) {
        B b = b(); b.boosters.add(o); b.purchases.merge(o.id(), 1, Integer::sum); return b.build();
    }
    public PlayerProfile removeBooster(int index) { B b = b(); b.boosters.remove(index); return b.build(); }

    /** Учёт действия для масштабируемых усилителей: kills / ores / crops (+ life_*) */
    public PlayerProfile countAction(String key) {
        B b = b();
        b.stats.merge(key, 1L, Long::sum);
        b.stats.merge("life_" + key, 1L, Long::sum);
        return b.build();
    }

    public PlayerProfile finishEvent(long score, boolean gold) {
        B b = b();
        b.eventsDone++; b.totalScore += score;
        b.stats.merge("life_events", 1L, Long::sum);
        b.stats.merge("life_score", score, Long::sum);
        b.stats.merge("best_event_score", score, Long::max);
        if (gold) b.stats.merge("gold_medals", 1L, Long::sum);
        return b.build();
    }

    // ---------- косметика (хранится вечно, покупается за эссенцию) ----------
    public boolean owns(String cosmeticId) { return cosmetics.contains(cosmeticId); }
    /** id надетой косметики данного типа или null */
    public String equippedId(String type) { return equipped.get(type); }
    public PlayerProfile addEssence(int n) { B b = b(); b.essence += n; return b.build(); }
    public PlayerProfile buyCosmetic(String id, int price) {
        B b = b(); b.essence -= price; if (!b.cosmetics.contains(id)) b.cosmetics.add(id); return b.build();
    }
    public PlayerProfile equip(String type, String id) { B b = b(); b.equipped.put(type, id); return b.build(); }
    public PlayerProfile unequip(String type) { B b = b(); b.equipped.remove(type); return b.build(); }

    // ---------- расходники (в забеге, 2 слота) ----------
    public static final int CONSUMABLE_SLOTS = 2;
    public PlayerProfile addConsumable(String id) { B b = b(); b.consumables.add(id); return b.build(); }
    public PlayerProfile removeConsumable(int index) { B b = b(); b.consumables.remove(index); return b.build(); }
    public PlayerProfile replaceBooster(int index, OwnedBooster o) { B b = b(); b.boosters.set(index, o); return b.build(); }
    /** Добавить усилитель без учёта покупки (копии, награды) */
    public PlayerProfile addBoosterRaw(OwnedBooster o) { B b = b(); b.boosters.add(o); return b.build(); }

    /** Сброс прогресса: ранг за текущее существо, эссенция, чистый забег */
    public PlayerProfile afterReset(int newRank, int essenceGain) {
        B b = b();
        b.ranks.put(species, newRank);
        b.essence += essenceGain;
        b.resets++;
        b.coins = 0; b.eventsDone = 0; b.totalScore = 0; b.extraSlots = 0;
        b.boosters = new ArrayList<>();
        b.consumables = new ArrayList<>();
        b.purchases = new HashMap<>();
        b.stats.keySet().removeIf(k -> !(k.startsWith("life_") || k.equals("best_event_score")));
        return b.build();
    }
}
