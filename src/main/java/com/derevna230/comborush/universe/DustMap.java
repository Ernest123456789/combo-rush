package com.derevna230.comborush.universe;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;

/**
 * Тестовая карта «Дюны» (измерение combo-rush:dust). Всё считается по формулам от координат, как у небесных миров.
 * <pre>
 *   x → восток, z → юг.   Т — на западе (x ≈ −38), КТ — на востоке (x ≈ +38)
 *
 *    ┌──────────────────────────────────────────────┐
 *    │  северный коридор                 ┌───A───┐  │
 *    │ Т  ═════════════╗ дверь            └───────┘  │  КТ
 *    │ Т  ────────  средний коридор  ───  центр ─── │
 *    │  южный коридор                    ┌───B───┐  │
 *    └──────────────────────────────────────────────┘
 * </pre>
 * Площадки A и B — квадраты 13×13 с буквой на полу: бомбу можно закладывать только на них ({@link #siteAt}).
 */
public final class DustMap {
    private DustMap() {}

    /** Блок пола; игрок стоит на FLOOR + 1 */
    public static final int FLOOR = 64;
    public static final int X0 = -44, X1 = 44, Z0 = -32, Z1 = 32;
    private static final int WALL_H = 10, CEIL = 34;

    /** Центры площадок и половина их стороны (зона закладки: |dx| ≤ R, |dz| ≤ R) */
    public static final int AX = 22, AZ = -20, BX = 22, BZ = 20, SITE_R = 6;

    /** Места возрождения: центр зоны и половина размаха по z */
    public static final double T_X = -38.5, CT_X = 38.5, SPAWN_SPREAD = 9;

    private static BlockState s(net.minecraft.block.Block b) { return b.getDefaultState(); }
    private static final BlockState STONE = s(Blocks.STONE), SAND_A = s(Blocks.SMOOTH_SANDSTONE), SAND_B = s(Blocks.CUT_SANDSTONE),
        SAND_C = s(Blocks.SANDSTONE), WALL_A = s(Blocks.SANDSTONE), WALL_B = s(Blocks.CUT_SANDSTONE), WALL_C = s(Blocks.SMOOTH_SANDSTONE),
        CRATE = s(Blocks.SPRUCE_PLANKS), CRATE2 = s(Blocks.DARK_OAK_PLANKS), SITE = s(Blocks.ORANGE_TERRACOTTA),
        SITE_RIM = s(Blocks.YELLOW_CONCRETE), LETTER = s(Blocks.WHITE_CONCRETE), LANTERN = s(Blocks.SEA_LANTERN),
        T_PAD = s(Blocks.RED_TERRACOTTA), CT_PAD = s(Blocks.BLUE_TERRACOTTA), BARRIER = s(Blocks.BARRIER),
        PILLAR = s(Blocks.YELLOW_CONCRETE), BRICK = s(Blocks.STONE_BRICKS);

    /** Коробка: x0..x1, z0..z1 (включительно), высота h, материал */
    private record Box(int x0, int x1, int z0, int z1, int h, BlockState st) {
        boolean has(int x, int z) { return x >= x0 && x <= x1 && z >= z0 && z <= z1; }
    }

    private static Box box(int x0, int x1, int z0, int z1, int h, BlockState st) { return new Box(x0, x1, z0, z1, h, st); }

    private static final Box[] BOXES = {
        // стенка у базы Т (x −31..−30) с проходами: север z −26..−14, центр −3..3, юг 14..26
        box(-31, -30, Z0, -27, 6, WALL_B), box(-31, -30, -13, -4, 6, WALL_B), box(-31, -30, 4, 13, 6, WALL_B), box(-31, -30, 27, Z1, 6, WALL_B),
        // стенка у базы КТ (x 30..31): проходы к A (−26..−20), центр, к B (20..26)
        box(30, 31, Z0, -27, 6, WALL_B), box(30, 31, -19, -4, 6, WALL_B), box(30, 31, 4, 19, 6, WALL_B), box(30, 31, 27, Z1, 6, WALL_B),
        // разделители коридоров (с «дверью» z-ряда x −15..−11)
        box(-28, -16, -10, -9, 6, WALL_A), box(-10, 8, -10, -9, 6, WALL_A),
        box(-28, -16, 9, 10, 6, WALL_A), box(-10, 8, 9, 10, 6, WALL_A),
        // центр: две колонны и низкая стенка
        box(-1, 1, -3, -2, 4, WALL_C), box(-1, 1, 2, 3, 4, WALL_C), box(14, 15, -1, 1, 6, WALL_B),
        // ящики в коридорах и в центре (2×2×2, перепрыгнуть нельзя, обойти можно)
        box(-24, -23, -20, -19, 2, CRATE), box(-8, -7, -16, -15, 2, CRATE2), box(2, 3, -22, -21, 2, CRATE),
        box(-24, -23, 19, 20, 2, CRATE), box(-8, -7, 15, 16, 2, CRATE2), box(2, 3, 21, 22, 2, CRATE),
        box(-20, -19, -1, 0, 2, CRATE), box(-6, -5, 4, 5, 2, CRATE2), box(-6, -5, -5, -4, 2, CRATE), box(6, 7, -1, 0, 2, CRATE2),
        // укрытия на площадке A и B
        box(17, 18, -25, -24, 2, CRATE), box(26, 27, -16, -15, 2, CRATE2), box(26, 27, -25, -24, 2, CRATE),
        box(17, 18, 24, 25, 2, CRATE), box(26, 27, 15, 16, 2, CRATE2), box(26, 27, 24, 25, 2, CRATE),
        // ящики у баз
        box(-36, -35, -14, -13, 2, CRATE), box(-36, -35, 13, 14, 2, CRATE2),
        box(35, 36, -14, -13, 2, CRATE2), box(35, 36, 13, 14, 2, CRATE),
    };

    // ---------------------------------------------------------------- площадки A и B
    /** 'A', 'B' или 0 — на какой площадке закладки точка (x, z) */
    public static char siteAt(double x, double z) {
        if (Math.abs(x - (AX + 0.5)) <= SITE_R + 0.5 && Math.abs(z - (AZ + 0.5)) <= SITE_R + 0.5) return 'A';
        if (Math.abs(x - (BX + 0.5)) <= SITE_R + 0.5 && Math.abs(z - (BZ + 0.5)) <= SITE_R + 0.5) return 'B';
        return 0;
    }

    public static double[] siteCenter(char site) { return site == 'A' ? new double[]{AX + 0.5, AZ + 0.5} : new double[]{BX + 0.5, BZ + 0.5}; }

    private static final String[] GLYPH_A = {".###.", "#...#", "#...#", "#####", "#...#", "#...#", "#...#"};
    private static final String[] GLYPH_B = {"####.", "#...#", "#...#", "####.", "#...#", "#...#", "####."};

    /** Буква на полу, читается стоя к западу от площадки и глядя на восток: вверх буквы — к востоку (+x), вправо — к югу (+z) */
    private static boolean letter(String[] g, int dx, int dz) {
        int row = 3 - dx;              // dx = +3 — верх буквы
        int col = dz + 2;
        return row >= 0 && row < 7 && col >= 0 && col < 5 && g[row].charAt(col) == '#';
    }

    private static BlockState floor(int x, int z) {
        for (char c : new char[]{'A', 'B'}) {
            int cx = c == 'A' ? AX : BX, cz = c == 'A' ? AZ : BZ;
            int dx = x - cx, dz = z - cz;
            if (Math.abs(dx) <= SITE_R && Math.abs(dz) <= SITE_R) {
                if (Math.abs(dx) == SITE_R || Math.abs(dz) == SITE_R) return SITE_RIM;
                return letter(c == 'A' ? GLYPH_A : GLYPH_B, dx, dz) ? LETTER : SITE;
            }
        }
        if (x <= -32 && x >= -44) return ((x + z) & 1) == 0 ? T_PAD : s(Blocks.TERRACOTTA);
        if (x >= 32) return ((x + z) & 1) == 0 ? CT_PAD : s(Blocks.LIGHT_BLUE_TERRACOTTA);
        int h = (x * 7 + z * 13) & 7;
        return h < 5 ? SAND_A : h < 7 ? SAND_B : SAND_C;
    }

    // ---------------------------------------------------------------- колонка
    public static void column(int x, int z, BlockState[] a) {
        boolean inside = x >= X0 && x <= X1 && z >= Z0 && z <= Z1;
        boolean ring = x >= X0 - 1 && x <= X1 + 1 && z >= Z0 - 1 && z <= Z1 + 1;
        if (!ring) return;
        a[FLOOR - 1] = STONE;
        if (!inside) {                                              // внешняя стена: песчаник до WALL_H, выше невидимый барьер
            for (int y = FLOOR; y <= FLOOR + WALL_H; y++) a[y] = ((x + z + y) & 3) == 0 ? WALL_B : WALL_A;
            for (int y = FLOOR + WALL_H + 1; y <= CEIL; y++) a[y] = BARRIER;
            return;
        }
        a[FLOOR] = floor(x, z);
        a[CEIL] = BARRIER;                                          // потолок: не вылететь вверх
        for (Box b : BOXES) {
            if (!b.has(x, z)) continue;
            for (int y = FLOOR + 1; y <= FLOOR + b.h; y++) a[y] = b.st;
        }
        // угловые столбы площадок: видны издалека
        for (char c : new char[]{'A', 'B'}) {
            int cx = c == 'A' ? AX : BX, cz = c == 'A' ? AZ : BZ;
            if (Math.abs(x - cx) == SITE_R && Math.abs(z - cz) == SITE_R) {
                for (int y = FLOOR + 1; y <= FLOOR + 5; y++) a[y] = PILLAR;
                a[FLOOR + 6] = LANTERN;
            }
        }
    }
}
