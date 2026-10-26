package com.derevna230.comborush.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * dropped: "" (обычный), "score" или "game" (какую часть потеряли у негативного усилителя)
 * level: 0..MAX_LEVEL, каждый уровень усиливает положительные очковые эффекты на 20%
 * edition: "" | "foil" | "holo" | "poly"
 */
public record OwnedBooster(String id, String dropped, int paid, int level, String edition) {
    public static final int MAX_LEVEL = 5;

    public static final Codec<OwnedBooster> CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.STRING.fieldOf("id").forGetter(OwnedBooster::id),
        Codec.STRING.optionalFieldOf("dropped", "").forGetter(OwnedBooster::dropped),
        Codec.INT.optionalFieldOf("paid", 0).forGetter(OwnedBooster::paid),
        Codec.INT.optionalFieldOf("level", 0).forGetter(OwnedBooster::level),
        Codec.STRING.optionalFieldOf("edition", "").forGetter(OwnedBooster::edition)
    ).apply(i, OwnedBooster::new));

    public OwnedBooster(String id, String dropped, int paid) { this(id, dropped, paid, 0, ""); }

    public boolean negative() { return !dropped.isEmpty(); }
    public OwnedBooster withLevel(int l) { return new OwnedBooster(id, dropped, paid, Math.max(0, Math.min(MAX_LEVEL, l)), edition); }
    public OwnedBooster withEdition(String e) { return new OwnedBooster(id, dropped, paid, level, e); }
    public OwnedBooster withId(String newId) { return new OwnedBooster(newId, dropped, paid, level, edition); }

    public static String editionName(String e) {
        return switch (e) { case "foil" -> "Фольга"; case "holo" -> "Голограмма"; case "poly" -> "Полихром"; default -> ""; };
    }
    public static String editionDesc(String e) {
        return switch (e) {
            case "foil" -> "+25 фишек к каждому действию";
            case "holo" -> "+0.4 к множителю";
            case "poly" -> "×1.2 к множителю";
            default -> "";
        };
    }
}
