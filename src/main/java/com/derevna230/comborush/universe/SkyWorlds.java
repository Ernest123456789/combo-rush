package com.derevna230.comborush.universe;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LeavesBlock;
import net.minecraft.state.property.Properties;

/**
 * Блоки небесных миров. Всё считается по формулам от координат (без шума и соседей), поэтому чанки собираются быстро:
 * <ul>
 * <li><b>lobby</b> — остров в небе: круглая площадь с фонтаном, четыре лавки (С, В, Ю, З), пруд, деревья, подсвеченная подложка;</li>
 * <li><b>standoff</b> — тёмная сцена меню «Противостояния» за стеклом барьеров.</li>
 * </ul>
 * В колонке a[y] == null — воздух.
 */
public final class SkyWorlds {
    private SkyWorlds() {}

    public static final int HEIGHT = 256;
    /** Верхний слой травы острова; игрок стоит на TOP + 1 */
    public static final int TOP = 100;
    public static final int ST_FLOOR = 64;

    /** Центры лавок: север, восток, юг, запад. Прилавок смотрит на площадь */
    public static final int[][] BOOTH = {{0, -17}, {17, 0}, {0, 17}, {-17, 0}};
    /** Вектор «лицом к площади» для каждой лавки */
    private static final int[] FX = {0, -1, 0, 1}, FZ = {1, 0, -1, 0};
    /** Поворот NPC лицом к площади (0 = на юг, 90 = на запад, 180 = на север, 270 = на восток) */
    public static final float[] BOOTH_YAW = {0f, 90f, 180f, 270f};

    private static BlockState s(net.minecraft.block.Block b) { return b.getDefaultState(); }
    private static final BlockState GRASS = s(Blocks.GRASS_BLOCK), DIRT = s(Blocks.DIRT), STONE = s(Blocks.STONE),
        ANDESITE = s(Blocks.ANDESITE), TUFF = s(Blocks.TUFF), SAND = s(Blocks.SAND), WATER = s(Blocks.WATER),
        PATH = s(Blocks.DIRT_PATH), PLAZA_A = s(Blocks.POLISHED_ANDESITE), PLAZA_B = s(Blocks.STONE_BRICKS),
        PLAZA_C = s(Blocks.QUARTZ_BLOCK), PLAZA_D = s(Blocks.SMOOTH_STONE), RIM = s(Blocks.STONE_BRICK_WALL),
        SEA = s(Blocks.SEA_LANTERN), FENCE = s(Blocks.SPRUCE_FENCE), PLANKS = s(Blocks.SPRUCE_PLANKS),
        FLOOR = s(Blocks.DARK_OAK_PLANKS), POST = s(Blocks.STRIPPED_SPRUCE_LOG), BARREL = s(Blocks.BARREL),
        WHITE = s(Blocks.WHITE_CONCRETE), LANTERN = s(Blocks.LANTERN),
        LANTERN_H = Blocks.LANTERN.getDefaultState().with(Properties.HANGING, true),
        BLACKSTONE = s(Blocks.BLACKSTONE), POLISHED_BS = s(Blocks.POLISHED_BLACKSTONE), BS_BRICKS = s(Blocks.POLISHED_BLACKSTONE_BRICKS),
        GILDED = s(Blocks.GILDED_BLACKSTONE), BARRIER = s(Blocks.BARRIER), GRASS_T = s(Blocks.SHORT_GRASS),
        POPPY = s(Blocks.POPPY), DAISY = s(Blocks.OXEYE_DAISY), CORN = s(Blocks.CORNFLOWER), ALLIUM = s(Blocks.ALLIUM),
        TULIP = s(Blocks.PINK_TULIP), SEA_GLASS = s(Blocks.WHITE_STAINED_GLASS);
    private static final BlockState[] ROOF = {s(Blocks.RED_CONCRETE), s(Blocks.BLUE_CONCRETE), s(Blocks.LIME_CONCRETE), s(Blocks.YELLOW_CONCRETE)};
    private static final BlockState[] FLOWERS = {POPPY, DAISY, CORN, ALLIUM, TULIP, GRASS_T, GRASS_T, GRASS_T};
    private static final BlockState[] LOGS = {s(Blocks.OAK_LOG), s(Blocks.CHERRY_LOG), s(Blocks.BIRCH_LOG)};
    private static final BlockState[] LEAVES = {
        Blocks.OAK_LEAVES.getDefaultState().with(LeavesBlock.PERSISTENT, true),
        Blocks.CHERRY_LEAVES.getDefaultState().with(LeavesBlock.PERSISTENT, true),
        Blocks.BIRCH_LEAVES.getDefaultState().with(LeavesBlock.PERSISTENT, true)};

    // ---------------------------------------------------------------- шум
    private static double hash(int x, int z, int seed) {
        long n = x * 374761393L + z * 668265263L + seed * 2147483647L;
        n = (n ^ (n >> 13)) * 1274126177L;
        return ((n ^ (n >> 16)) & 0xffff) / 65535.0;
    }

    /** Радиус острова в направлении (x, z): неровный круг */
    private static double islandRadius(int x, int z) {
        double a = Math.atan2(z, x);
        return 27 + 2.2 * Math.sin(3 * a + 0.7) + 1.6 * Math.cos(5 * a + 1.3);
    }

    // ---------------------------------------------------------------- лобби
    /** Локальные координаты точки относительно лавки i: u — вдоль прилавка, v — в сторону площади */
    private static int boothU(int i, int dx, int dz) { return -dx * FZ[i] + dz * FX[i]; }
    private static int boothV(int i, int dx, int dz) { return dx * FX[i] + dz * FZ[i]; }

    private static boolean inPond(int x, int z, double extra) {
        double dx = x + 12, dz = z - 12;
        return Math.sqrt(dx * dx + dz * dz) <= 4.6 + extra;
    }

    /** Нельзя ли ставить дерево или цветок: площадь, дорожки, лавки, пруд */
    private static boolean busy(int x, int z, int margin) {
        if (Math.sqrt((double) x * x + (double) z * z) <= 11.5 + margin) return true;
        if (Math.abs(x) <= 2 + margin || Math.abs(z) <= 2 + margin) return true;
        for (int i = 0; i < 4; i++) {
            if (Math.abs(x - BOOTH[i][0]) <= 5 + margin && Math.abs(z - BOOTH[i][1]) <= 5 + margin) return true;
        }
        return inPond(x, z, 2.5 + margin);
    }

    /** Центр дерева в ячейке 8×8 (или null) */
    private static int[] treeIn(int cx, int cz) {
        if (hash(cx, cz, 91) > 0.62) return null;
        int tx = cx * 8 + 1 + (int) (hash(cx, cz, 92) * 6), tz = cz * 8 + 1 + (int) (hash(cx, cz, 93) * 6);
        double r = Math.sqrt((double) tx * tx + (double) tz * tz);
        if (r > islandRadius(tx, tz) - 5 || busy(tx, tz, 1)) return null;
        int species = (int) (hash(cx, cz, 94) * 3);
        int height = 4 + (int) (hash(cx, cz, 95) * 3);
        return new int[]{tx, tz, species, height};
    }

    /** Блок над травой (h = y − TOP ≥ 1); null — воздух */
    private static BlockState above(int x, int z, int h) {
        // лавки
        for (int i = 0; i < 4; i++) {
            int dx = x - BOOTH[i][0], dz = z - BOOTH[i][1];
            if (Math.abs(dx) > 5 || Math.abs(dz) > 5) continue;
            int u = boothU(i, dx, dz), v = boothV(i, dx, dz);
            int au = Math.abs(u), av = Math.abs(v);
            if (au <= 3 && av <= 3) {
                if (au == 3 && av == 3) return h <= 4 ? POST : null;                       // угловые стойки
                if (v == -3) {                                                           // задняя стенка
                    if (h <= 3) return (h == 3 && au <= 1) ? ROOF[i] : PLANKS;
                }
                if (au == 3 && v < 0 && v > -3 && h <= 2) return PLANKS;                // боковые низкие стенки
                if (v == 1 && au <= 2) {                                                  // прилавок
                    if (h == 1) return au == 2 ? BARREL : PLANKS;
                    if (h == 2 && au == 1) return LANTERN;
                }
                if (v == -2 && au == 2 && h == 1) return BARREL;
                if (h == 4 && u == 0 && v == 0) return LANTERN_H;                        // фонарь под крышей
            }
            if (h == 5 && au <= 4 && av <= 4) return (((u + 4) & 1) == 0) ? ROOF[i] : WHITE;   // крыша с полосами
            if (h == 6 && au <= 2 && av <= 2) return ROOF[i];
            if (h == 7 && au <= 1 && av <= 1) return WHITE;
        }
        // площадь: фонтан и фонари
        double r2 = (double) x * x + (double) z * z;
        if (Math.abs(x) <= 2 && Math.abs(z) <= 2) {
            boolean ring = Math.abs(x) == 2 || Math.abs(z) == 2;
            if (ring) return h == 1 ? RIM : null;
            if (x == 0 && z == 0) return h <= 3 ? SEA : (h == 4 ? SEA_GLASS : null);
            return h == 1 ? WATER : null;                                                // вода в чаше фонтана
        }
        if (Math.abs(Math.abs(x) - 7) == 0 && Math.abs(Math.abs(z) - 7) == 0) {
            if (h <= 3) return FENCE;
            if (h == 4) return LANTERN;
        }
        // деревья
        int ccx = Math.floorDiv(x, 8), ccz = Math.floorDiv(z, 8);
        for (int cx = ccx - 1; cx <= ccx + 1; cx++) {
            for (int cz = ccz - 1; cz <= ccz + 1; cz++) {
                int[] t = treeIn(cx, cz);
                if (t == null) continue;
                int dx = x - t[0], dz = z - t[1], ht = t[3];
                if (Math.abs(dx) > 2 || Math.abs(dz) > 2) continue;
                if (dx == 0 && dz == 0 && h <= ht) return LOGS[t[2]];
                int d2 = dx * dx + dz * dz;
                if (h >= ht - 1 && h <= ht && d2 <= 5 && !(dx == 0 && dz == 0 && h <= ht)) return LEAVES[t[2]];
                if (h == ht + 1 && d2 <= 2) return LEAVES[t[2]];
                if (h == ht + 2 && d2 <= 0) return LEAVES[t[2]];
                if (h == ht - 2 && d2 == 4 + 1 && hash(x, z, 96) > 0.5) return LEAVES[t[2]];
            }
        }
        // цветы и трава
        if (h == 1 && !busy(x, z, 0) && Math.sqrt(r2) < islandRadius(x, z) - 1.5 && hash(x, z, 97) < 0.10)
            return FLOWERS[(int) (hash(x, z, 98) * FLOWERS.length) % FLOWERS.length];
        return null;
    }

    /** Верхний блок (y == TOP): трава, площадь, дорожки, пол лавок, пруд */
    private static BlockState surface(int x, int z) {
        double r = Math.sqrt((double) x * x + (double) z * z);
        if (inPond(x, z, 0)) return WATER;
        if (inPond(x, z, 1.6)) return SAND;
        for (int i = 0; i < 4; i++) {
            int dx = x - BOOTH[i][0], dz = z - BOOTH[i][1];
            int u = boothU(i, dx, dz), v = boothV(i, dx, dz);
            if (Math.abs(u) <= 3 && Math.abs(v) <= 3) return FLOOR;
            if (Math.abs(u) <= 1 && v >= 4 && v <= 6 && r > 9) return PATH;              // дорожка от лавки
        }
        if (r <= 10.5) {                                                                // площадь
            if (r <= 2.6) return PLAZA_C;
            if (r <= 3.6) return PLAZA_D;
            if (r >= 9.2) return PLAZA_B;
            double a = Math.atan2(z, x);
            int sector = (int) Math.floor((a + Math.PI) / (Math.PI / 4));
            return ((sector + (int) (r / 2)) & 1) == 0 ? PLAZA_A : PLAZA_B;
        }
        if (Math.abs(x) <= 1 || Math.abs(z) <= 1) {                                      // аллеи вдоль осей
            if (r < islandRadius(x, z) - 0.5 && (Math.abs(x) <= 1 ? Math.abs(z) : Math.abs(x)) >= 10) return PATH;
        }
        return GRASS;
    }

    private static void lobby(int x, int z, BlockState[] a) {
        double r = Math.sqrt((double) x * x + (double) z * z);
        double R = islandRadius(x, z);
        if (r > R) return;
        double edge = R - r;
        double spike = hash(x >> 1, z >> 1, 31) > 0.88 ? 4 : 0;
        int depth = (int) (4 + edge * 1.3 + hash(x, z, 33) * 2 + spike);
        int bottom = Math.max(2, TOP - depth);
        for (int y = bottom; y <= TOP; y++) {
            BlockState st;
            if (y == TOP) st = surface(x, z);
            else if (y >= TOP - 3) st = (inPond(x, z, 1.6) && y == TOP - 1) ? (inPond(x, z, 0) ? SAND : DIRT) : DIRT;
            else {
                double h = hash(x + y * 7, z - y * 3, 41);
                st = h < 0.12 ? ANDESITE : (h > 0.93 ? TUFF : STONE);
            }
            a[y] = st;
        }
        for (int h = 1; h <= 14 && TOP + h < HEIGHT; h++) {
            BlockState up = above(x, z, h);
            if (up != null && a[TOP] != WATER) a[TOP + h] = up;
        }
    }

    // ---------------------------------------------------------------- «Противостояние»
    private static void standoff(int x, int z, BlockState[] a) {
        int ax = Math.abs(x), az = Math.abs(z);
        int m = Math.max(ax, az);
        if (m <= 12) {
            a[ST_FLOOR - 1] = BLACKSTONE;
            BlockState top = (m == 12 || (m == 7 && ax != 7 && az != 7) || m == 7) ? GILDED
                : ((x + z) & 1) == 0 ? POLISHED_BS : BS_BRICKS;
            a[ST_FLOOR] = top;
        }
        if (m <= 13) {
            if (m == 13) for (int y = ST_FLOOR + 1; y <= ST_FLOOR + 16; y++) a[y] = BARRIER;
            else a[ST_FLOOR + 16] = BARRIER;
        }
    }

    public static void column(String kind, int x, int z, BlockState[] a) {
        java.util.Arrays.fill(a, null);
        if ("standoff".equals(kind)) standoff(x, z, a); else lobby(x, z, a);
    }

    /** Высота «земли» в точке: для getHeight */
    public static int topY(String kind, int x, int z, BlockState[] scratch) {
        column(kind, x, z, scratch);
        for (int y = HEIGHT - 1; y >= 0; y--) if (scratch[y] != null) return y + 1;
        return 0;
    }
}
