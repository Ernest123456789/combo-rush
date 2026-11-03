package com.derevna230.comborush.pet;

import java.util.Map;

/** Размер хитбокса и масштаб модели декора по id. Старые растения — стандартные 0.6×0.7 без масштаба. */
public final class DecorSizes {
    private DecorSizes() {}

    public record Size(float width, float height, float scale) {}

    public static final Size DEFAULT = new Size(0.6f, 0.7f, 1f);

    private static final Map<String, Size> SIZES = Map.ofEntries(
        Map.entry("camping_campfire", new Size(1.0f, 0.75f, 0.6f)),
        Map.entry("wooden_stump", new Size(0.48f, 0.55f, 0.637f)),
        Map.entry("fumeshroom", new Size(0.6f, 0.47f, 0.565f)),
        Map.entry("wandering_trader", new Size(0.52f, 1.0f, 0.464f)),
        Map.entry("redstone_golem", new Size(1.0f, 1.17f, 0.311f)));

    public static Size of(String id) { return SIZES.getOrDefault(id, DEFAULT); }
}
