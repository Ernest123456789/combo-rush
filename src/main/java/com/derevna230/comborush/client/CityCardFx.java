package com.derevna230.comborush.client;

import net.minecraft.client.gui.DrawContext;

/**
 * Неоновый «киберпанк»-вид карточек усилителей и расходников Ночного города:
 * срезанные углы (сверху справа и снизу слева), двойная рамка (бегущий неон снаружи, цвет редкости внутри),
 * тёмно-синий градиент с сеткой и строчной разверткой, ромб-гало за иконкой, бегущий огонёк и луч-сканер.
 */
public final class CityCardFx {
    private CityCardFx() {}

    public static final int CYAN = 0x35F2FF, MAGENTA = 0xFF3DDC, VIOLET = 0x8A5CFF;
    /** Насколько срезан внутренний контур (в пикселях) */
    public static final int CUT = 3;

    private static int mix(int a, int b, float k) {
        k = Math.max(0f, Math.min(1f, k));
        int r = (int) (((a >> 16) & 255) * (1 - k) + ((b >> 16) & 255) * k);
        int g = (int) (((a >> 8) & 255) * (1 - k) + ((b >> 8) & 255) * k);
        int bl = (int) ((a & 255) * (1 - k) + (b & 255) * k);
        return (r << 16) | (g << 8) | bl;
    }

    /** Неоновый цвет, плавно переливающийся от голубого к розовому */
    public static int neon(long now, int seed, double off) {
        double t = now / 1e9 * 0.55 + seed * 0.41 + off;
        return mix(CYAN, MAGENTA, (float) (0.5 + 0.5 * Math.sin(t)));
    }

    /** Заливка прямоугольника со срезом сверху справа и снизу слева */
    private static void shape(DrawContext c, int x0, int y0, int x1, int y1, int cut, int argb) {
        int h = y1 - y0;
        for (int r = 0; r < h; r++) {
            int l = x0, rr = x1;
            if (r < cut) rr = x1 - (cut - r);
            if (r >= h - cut) l = x0 + (cut - (h - 1 - r));
            if (rr > l) c.fill(l, y0 + r, rr, y0 + r + 1, argb);
        }
    }

    /**
     * Рамка и фон карточки размером w×h (координаты карточки: 0..w, 0..h).
     * rarArgb — цвет внутренней линии (редкость), outerOverride — цвет внешней рамки при выделении (0 — неон), tint — оттенок категории.
     */
    public static void frame(DrawContext c, int w, int h, long now, int seed, int rarArgb, int outerOverride, int tint) {
        int outer = outerOverride != 0 ? outerOverride : (0xFF000000 | neon(now, seed, 0));
        shape(c, -1, -1, w + 1, h + 1, CUT + 2, outer);
        shape(c, 0, 0, w, h, CUT + 1, rarArgb);
        // тело: градиент по строкам
        int top = mix(0x080B26, tint, 0.16f), bot = mix(0x1E0B3E, tint, 0.26f);
        int bh = h - 2;
        for (int r = 0; r < bh; r++) {
            int l = 1, rr = w - 1;
            if (r < CUT) rr = w - 1 - (CUT - r);
            if (r >= bh - CUT) l = 1 + (CUT - (bh - 1 - r));
            c.fill(l, 1 + r, rr, 2 + r, 0xFF000000 | mix(top, bot, r / (float) Math.max(1, bh - 1)));
        }
        // сетка и строчная развёртка
        for (int x = 4; x < w - 2; x += 7) c.fill(x, 2, x + 1, h - 2, 0x0E000000 | CYAN);
        for (int y = 3; y < h - 2; y += 3) c.fill(2, y, w - 2, y + 1, 0x26000000);
        // нижний неоновый кант
        c.fill(CUT + 2, h - 2, w - 2, h - 1, 0x55000000 | neon(now, seed, 2.0));
    }

    /** Ромб-гало за иконкой (cx, cy — центр, r — «радиус»), мягко пульсирует */
    public static void halo(DrawContext c, int cx, int cy, int r, long now, int seed) {
        int base = neon(now, seed, 1.2);
        float pulse = 0.65f + 0.35f * (float) Math.sin(now / 4.2e8 + seed);
        for (int ring = 3; ring >= 1; ring--) {
            int rr = r * ring / 3;
            int a = (int) (0x2A * pulse * (4 - ring) / 3f) + 6;
            for (int dy = -rr; dy <= rr; dy++) {
                int hw = rr - Math.abs(dy);
                c.fill(cx - hw, cy + dy, cx + hw + 1, cy + dy + 1, (a << 24) | base);
            }
        }
    }

    /** Поверх содержимого: уголки, бегущий огонёк по низу и луч-сканер */
    public static void overlay(DrawContext c, int w, int h, long now, int seed) {
        int cy = 0xFF000000 | CYAN, mg = 0xFF000000 | MAGENTA;
        // уголки: левый верх и правый низ
        c.fill(2, 2, 7, 3, cy); c.fill(2, 2, 3, 7, cy);
        c.fill(w - 7, h - 3, w - 2, h - 2, mg); c.fill(w - 3, h - 7, w - 2, h - 2, mg);
        // бегущий огонёк по нижнему канту
        double ph = (now / 1e9 * 0.7 + seed * 0.29) % 1.0;
        int sx = (int) (ph * (w + 10)) - 8;
        int x1 = Math.max(CUT + 2, sx), x2 = Math.min(w - 2, sx + 9);
        if (x2 > x1) c.fill(x1, h - 2, x2, h - 1, 0xFFFFFFFF);
        // луч-сканер
        double bp = (now / 1e9 * 0.33 + seed * 0.21) % 1.6 - 0.3;
        int by = (int) (bp * h);
        if (by > 1 && by < h - 3) {
            c.fill(2, by, w - 2, by + 1, 0x40000000 | CYAN);
            c.fill(2, by + 1, w - 2, by + 2, 0x16000000 | CYAN);
        }
    }
}
