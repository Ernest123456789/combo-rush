package com.derevna230.comborush.universe;

import java.util.Arrays;

/**
 * «Ночной город»: чистая логика генерации (без классов Minecraft, поэтому её можно проверять отдельно).
 * Для колонки (x, z) заполняет массив блоков по высоте. Город — сетка кварталов: улица 16 блоков, квартал 56.
 * Вся застройка вычисляется из координат, поэтому чанки не зависят друг от друга.
 */
public final class NightCity {
    private NightCity() {}

    public static final int HEIGHT = 320;
    /** Верхний блок земли; пол улиц и первых этажей. Здания растут с y = GROUND + 1 */
    public static final int GROUND = 63;
    public static final int PERIOD = 72, STREET = 16, LOT = 56, SUB = 26, ALLEY = 4;
    /** Город — квадрат из (2·LOTS+1)² кварталов вокруг Нексуса; за его краем начинаются холмы и горы с трассами ({@link NightCityWild}) */
    public static final int LOTS = 3;
    public static final int CITY_MIN = -LOTS * PERIOD, CITY_MAX = (LOTS + 1) * PERIOD + STREET - 1;
    /** Центр Нексуса (главной башни) и точка появления игроков */
    public static final int NEXUS_X = STREET + 28, NEXUS_Z = STREET + 28;
    public static final int SPAWN_X = NEXUS_X, SPAWN_Z = STREET + 53;

    /** Палитра города: rgb и признаки нужны только для превью; в игре блок выбирается в NightCityChunkGenerator */
    public enum B {
        AIR(0, false, true), BEDROCK(0x202020, false, false), DEEPSLATE(0x3a3a40, false, false), DARK_STONE(0x2a2a30, false, false),
        ASPHALT(0x1b1b20, false, false), SIDEWALK_A(0x9a9aa8, false, false), SIDEWALK_B(0x7c7c8a, false, false),
        LINE_WHITE(0xeeeeee, false, false), LINE_YELLOW(0xe8c020, false, false), ALLEY(0x26262e, false, false),
        PLAZA_A(0x15151c, false, false), PLAZA_B(0x30303c, false, false), PLAZA_LINE(0x20c8d8, true, false),
        POLE(0x555566, false, false), IRON(0x888898, false, false),
        // свет (подсветка ночного города)
        L_CYAN(0xb8fff0, true, false), L_MAGENTA(0xffb0f0, true, false), L_LIME(0xc8ff90, true, false),
        L_ORANGE(0xffc070, true, false), L_BLUE(0xa8d8ff, true, false), L_PURPLE(0xd8a8ff, true, false), L_WARM(0xffe6a0, true, false),
        // цветные стёкла и бетон по цветам акцента
        G_CYAN(0x20c8e0, false, true), G_MAGENTA(0xe030d0, false, true), G_LIME(0x70e030, false, true),
        G_ORANGE(0xf09020, false, true), G_BLUE(0x3070f0, false, true), G_PURPLE(0x8030e0, false, true),
        G_TINT(0x302838, false, true), G_WHITE(0xe8e8f8, false, true), G_RED(0xf02030, false, true),
        C_CYAN(0x1498a8, false, false), C_MAGENTA(0xa828a0, false, false), C_LIME(0x58a020, false, false),
        C_ORANGE(0xc06810, false, false), C_BLUE(0x2850b8, false, false), C_PURPLE(0x6020a8, false, false),
        BLACK(0x101014, false, false), GRAY(0x383840, false, false), LIGHT_GRAY(0x9a9aa4, false, false), WHITE(0xe8e8e8, false, false),
        FLOOR(0x2c2c36, false, false), FLOOR_LIGHT(0x606070, false, false), ROOF(0x24242c, false, false), QUARTZ(0xe6e2d8, false, false),
        ANTENNA(0x9090a0, false, false), BEACON_RED(0xff2020, true, false),
        GRASS(0x3a8a38, false, false), PATH(0x707080, false, false), LOG(0x4a2a2c, false, false), LEAVES(0xf0a0c8, false, false),
        WATER(0x3060ff, false, true), LANTERN_BASE(0x70e8e0, true, false), LAMP_WHITE(0xfff4d0, true, false),
        // за городом: рельеф и разметка трасс
        SAND(0xd8c890, false, false), DIRT(0x6a4a30, false, false), STONE(0x707078, false, false), ANDESITE(0x8a8a8c, false, false),
        SNOW(0xf0f4ff, false, false), RED_CONC(0xc02828, false, false),
        // земля, растения, тропы
        PODZOL(0x5a3e1c, false, false), COARSE(0x77512f, false, false), MOSS(0x4a7a28, false, false), MUD(0x3c2f2c, false, false),
        GRAVEL(0x85807e, false, false), PATH_DIRT(0x967a46, false, false), COBBLE(0x7a7a7a, false, false), MOSSY_COBBLE(0x667a62, false, false),
        CRACKED(0x6a6a6e, false, false), STONE_BRICKS(0x7c7c80, false, false), BRICKS(0x965a4a, false, false), BARRIER(0x000000, false, true),
        GRASS_T(0x3a8a38, false, true), FERN(0x2f7a30, false, true), FL_POPPY(0xd03030, false, true), FL_ORCHID(0x30a8d8, false, true),
        FL_ALLIUM(0xb070e0, false, true), FL_AZURE(0xe8e8f0, false, true), FL_CORN(0x4070f0, false, true), FL_TULIP(0xf090c0, false, true),
        FL_DAISY(0xf0f0d0, false, true), SHROOM_R(0xd02820, false, true), SHROOM_B(0x907050, false, true),
        SPRUCE_LOG(0x3a2a18, false, false), SPRUCE_LEAVES(0x2a5a3a, false, false), BUSH(0xe070b0, false, false),
        // домики
        W_A(0x6a4e30, false, false), W_B(0x7a7a7e, false, false), W_C(0xd8c898, false, false),
        POST_A(0x3a2a18, false, false), POST_B(0x2a2018, false, false), POST_C(0x8a6a40, false, false),
        FLOOR_A(0x4a3420, false, false), FLOOR_B(0x6a4e30, false, false), FLOOR_C(0xa08850, false, false),
        ROOF_A_N(0x4a3420, false, false), ROOF_A_E(0x4a3420, false, false), ROOF_A_S(0x4a3420, false, false), ROOF_A_W(0x4a3420, false, false),
        ROOF_B_N(0x3a3a46, false, false), ROOF_B_E(0x3a3a46, false, false), ROOF_B_S(0x3a3a46, false, false), ROOF_B_W(0x3a3a46, false, false),
        ROOF_C_N(0x965a4a, false, false), ROOF_C_E(0x965a4a, false, false), ROOF_C_S(0x965a4a, false, false), ROOF_C_W(0x965a4a, false, false),
        RIDGE_A(0x4a3420, false, false), RIDGE_B(0x3a3a46, false, false), RIDGE_C(0x965a4a, false, false),
        CHEST_N(0x9a6a2a, false, false), CHEST_E(0x9a6a2a, false, false), CHEST_S(0x9a6a2a, false, false), CHEST_W(0x9a6a2a, false, false),
        BOOKSHELF(0x7a5a30, false, false), CRAFTING(0x8a6a3a, false, false), BARREL(0x6a4a28, false, false),
        HAY(0xc8a820, false, false), PUMPKIN(0xd87818, false, false), FENCE(0x5a4228, false, false),
        LANTERN_S(0xffd070, true, false), LANTERN_H(0xffd070, true, false), CAMPFIRE(0xff8a20, true, false);

        public final int rgb; public final boolean glow, clear;
        B(int rgb, boolean glow, boolean clear) { this.rgb = rgb; this.glow = glow; this.clear = clear; }
    }

    // акценты: 0 циан, 1 маджента, 2 лайм, 3 оранж, 4 синий, 5 фиолетовый
    static final B[] ACC_LIGHT = {B.L_CYAN, B.L_MAGENTA, B.L_LIME, B.L_ORANGE, B.L_BLUE, B.L_PURPLE};
    static final B[] ACC_GLASS = {B.G_CYAN, B.G_MAGENTA, B.G_LIME, B.G_ORANGE, B.G_BLUE, B.G_PURPLE};
    static final B[] ACC_CONC = {B.C_CYAN, B.C_MAGENTA, B.C_LIME, B.C_ORANGE, B.C_BLUE, B.C_PURPLE};

    /** Колонка блоков: a[y] для y = 0..top (воздух между блоками записан явно) */
    public static final class Col {
        public final B[] a = new B[HEIGHT];
        public int top;
        /** Сундуки с лутом в колонке: {y, номер таблицы добычи} (таблицы — {@link #CHEST_TABLES}) */
        public final java.util.ArrayList<int[]> chests = new java.util.ArrayList<>();
        public Col() { reset(); }
        public void reset() { Arrays.fill(a, B.AIR); top = 0; chests.clear(); }
        void chest(int y, B chest, int table) { set(y, chest); chests.add(new int[]{y, table}); }
        void set(int y, B b) {
            if (y < 0 || y >= HEIGHT) return;
            a[y] = b;
            if (b != B.AIR && y > top) top = y;
        }
        void fill(int y0, int y1, B b) { for (int y = y0; y <= y1; y++) set(y, b); }
    }

    /** Таблицы добычи сундуков: data/combo-rush/loot_table/chests/<имя>.json */
    public static final String[] CHEST_TABLES = {"house", "hidden", "peak", "camp"};
    public static final int T_HOUSE = 0, T_HIDDEN = 1, T_PEAK = 2, T_CAMP = 3;

    // ---------------------------------------------------------------- хеши
    static int h(int a, int b, int c) {
        int x = a * 0x9E3779B1 ^ b * 0x85EBCA77 ^ c * 0xC2B2AE3D;
        x ^= x >>> 15; x *= 0x2C1B3C6D; x ^= x >>> 12; x *= 0x297A2D39; x ^= x >>> 15;
        return x;
    }
    static int r(int h, int n) { return Math.floorMod(h, n); }
    static boolean chance(int h, int pct) { return Math.floorMod(h, 100) < pct; }

    // ---------------------------------------------------------------- вход
    public static void column(int x, int z, Col col) {
        col.reset();
        if (x < CITY_MIN || x > CITY_MAX || z < CITY_MIN || z > CITY_MAX) { NightCityWild.column(x, z, col); return; }
        col.set(0, B.BEDROCK);
        col.fill(1, 61, B.DEEPSLATE);
        col.set(62, B.DARK_STONE);
        int u = Math.floorMod(x, PERIOD), v = Math.floorMod(z, PERIOD);
        int li = Math.floorDiv(x, PERIOD), lj = Math.floorDiv(z, PERIOD);
        if (u < STREET || v < STREET) street(col, x, z, u, v);
        else lot(col, li, lj, u - STREET, v - STREET, x, z);
    }

    // ---------------------------------------------------------------- улицы
    private static void street(Col c, int x, int z, int u, int v) {
        boolean sx = u < STREET, sz = v < STREET;
        boolean roadU = sx && u >= 3 && u <= 12, roadV = sz && v >= 3 && v <= 12;
        boolean road = roadU || roadV;
        if (road) {
            B s = B.ASPHALT;
            if (roadU && !sz) {                                // улица вдоль z
                if ((u == 7 || u == 8) && Math.floorMod(z, 8) < 5) s = B.LINE_YELLOW;
                if ((v >= 16 && v <= 18 || v >= 69) && u % 2 == 0) s = B.LINE_WHITE;   // зебра
            }
            if (roadV && !sx) {                                // улица вдоль x
                if ((v == 7 || v == 8) && Math.floorMod(x, 8) < 5) s = B.LINE_YELLOW;
                if ((u >= 16 && u <= 18 || u >= 69) && v % 2 == 0) s = B.LINE_WHITE;
            }
            c.set(GROUND, s);
            // лампа на плече фонаря висит над краем дороги
            if (roadU && !sz && (u == 3 || u == 12) && Math.floorMod(z, 16) == 8)
                c.set(GROUND + 6, ACC_LIGHT[r(h(Math.floorDiv(z, 32), 1, 77), 6)]);
            if (roadV && !sx && (v == 3 || v == 12) && Math.floorMod(x, 16) == 8)
                c.set(GROUND + 6, ACC_LIGHT[r(h(Math.floorDiv(x, 32), 2, 77), 6)]);
            return;
        }
        // тротуар: плитка и светящиеся «бордюры»
        B s = (((x >> 1) + (z >> 1)) & 1) == 0 ? B.SIDEWALK_A : B.SIDEWALK_B;
        int a = sx && !sz ? u : sz && !sx ? v : -1;           // смещение поперёк улицы (вне перекрёстка)
        int along = sx && !sz ? z : x;
        if (a == 2 || a == 13) { if (Math.floorMod(along, 8) == 4) s = B.L_CYAN; }
        c.set(GROUND, s);
        // фонари: столб на тротуаре, плечо над дорогой и светящаяся лампа (цвет меняется по кварталу)
        if (a >= 0 && Math.floorMod(along, 16) == 8) {
            if (a == 1 || a == 14) c.fill(GROUND + 1, GROUND + 6, B.POLE);
            if (a == 2 || a == 13) c.set(GROUND + 6, B.POLE);
        }
    }

    // ---------------------------------------------------------------- кварталы
    private static void lot(Col c, int li, int lj, int lu, int lv, int x, int z) {
        if (li == 0 && lj == 0) { nexus(c, lu, lv); return; }
        int hh = h(li, lj, 1);
        int pct = r(hh >> 8, 100);
        if (pct < 58) four(c, li, lj, lu, lv);
        else if (pct < 74) big(c, li, lj, lu, lv);
        else if (pct < 87) park(c, li, lj, lu, lv);
        else plaza(c, li, lj, lu, lv);
    }

    private static B plazaTile(int x, int z) { return (((x >> 2) + (z >> 2)) & 1) == 0 ? B.PLAZA_A : B.PLAZA_B; }

    /** Коэффициент высоты: центр города выше, окраины ниже */
    private static double heightFactor(int li, int lj) {
        double d = Math.hypot(li, lj);
        return Math.max(0.40, Math.min(1.35, 1.40 - 0.17 * d));
    }

    // ---- 4 здания в квартале
    private static void four(Col c, int li, int lj, int lu, int lv) {
        int a = lu < SUB ? 0 : lu >= SUB + ALLEY ? 1 : -1;
        int b = lv < SUB ? 0 : lv >= SUB + ALLEY ? 1 : -1;
        if (a < 0 || b < 0) {                                  // переулок
            c.set(GROUND, B.ALLEY);
            int cu = lu - SUB, cv = lv - SUB;
            if ((a < 0 && b >= 0 && (cu == 1 || cu == 2) && Math.floorMod(lv, 6) == 0)
                || (b < 0 && a >= 0 && (cv == 1 || cv == 2) && Math.floorMod(lu, 6) == 0))
                c.set(GROUND, B.L_CYAN);
            return;
        }
        int su = lu - a * (SUB + ALLEY), sv = lv - b * (SUB + ALLEY);
        Spec s = spec(li, lj, a, b);
        sublot(c, s, su, sv);
    }

    private static final class Spec {
        int type, w, d, ox, oz, n, n1, n2, acc, acc2, seed, rad;
    }

    private static final int T_RECT = 0, T_STEP = 1, T_ROUND = 2, T_LOW = 3, T_TWIN = 4;

    private static Spec spec(int li, int lj, int a, int b) {
        int hh = h(li * 2 + a, lj * 2 + b, 5);
        Spec s = new Spec();
        s.seed = hh;
        int t = r(hh >> 4, 100);
        s.type = t < 34 ? T_RECT : t < 54 ? T_STEP : t < 70 ? T_ROUND : t < 86 ? T_LOW : T_TWIN;
        s.acc = r(hh >> 9, 6);
        s.acc2 = (s.acc + 1 + r(hh >> 12, 5)) % 6;
        s.w = 14 + r(hh >> 15, 9);                              // 14..22
        s.d = 14 + r(hh >> 18, 9);
        s.ox = 2 + r(hh >> 21, Math.max(1, 24 - s.w - 2 + 1));
        s.oz = 2 + r(hh >> 24, Math.max(1, 24 - s.d - 2 + 1));
        s.rad = 7 + r(hh >> 3, 5);                              // 7..11
        double f = heightFactor(li, lj);
        int base = chance(hh >> 6, 8) ? 34 + r(hh >> 7, 12) : 10 + r(hh >> 7, 25);
        int n = (int) Math.round(base * f);
        if (s.type == T_LOW) n = 3 + r(hh >> 7, 3);
        s.n = Math.max(3, Math.min(44, n));
        s.n1 = Math.max(2, (int) Math.round(s.n * 0.55));
        s.n2 = Math.max(s.n1 + 1, (int) Math.round(s.n * 0.8));
        return s;
    }

    private static void sublot(Col c, Spec s, int su, int sv) {
        // площадка перед зданием: плитка, подсветка по контуру, фонарики по углам
        c.set(GROUND, plazaTile(su + 5, sv + 3));
        boolean inFoot;
        switch (s.type) {
            case T_ROUND -> {
                double dx = su + 0.5 - SUB / 2.0, dz = sv + 0.5 - SUB / 2.0, d2 = dx * dx + dz * dz;
                inFoot = d2 <= (s.rad + 0.6) * (s.rad + 0.6);
                double ring = (s.rad + 1.6);
                if (!inFoot && d2 <= ring * ring) c.set(GROUND, ACC_LIGHT[s.acc]);
                if (inFoot) c.set(GROUND, B.FLOOR_LIGHT);
                building(c, s, su, sv);
            }
            case T_TWIN -> {
                boolean t1 = su >= 1 && su <= 9 && sv >= 8 && sv <= 16, t2 = su >= 16 && su <= 24 && sv >= 8 && sv <= 16;
                if (t1 || t2) c.set(GROUND, B.FLOOR_LIGHT);
                else if (sv >= 7 && sv <= 17 && (su >= 0 && su <= 10 || su >= 15 && su <= 25)) c.set(GROUND, ACC_LIGHT[s.acc]);
                building(c, s, su, sv);
            }
            default -> {
                int px = su - s.ox, pz = sv - s.oz;
                inFoot = px >= 0 && px < s.w && pz >= 0 && pz < s.d;
                if (inFoot) c.set(GROUND, B.FLOOR_LIGHT);
                else if (px >= -1 && px <= s.w && pz >= -1 && pz <= s.d) c.set(GROUND, ACC_LIGHT[s.acc]);
                building(c, s, su, sv);
            }
        }
        // фонарики на углах площадки
        if ((su == 1 || su == SUB - 2) && (sv == 1 || sv == SUB - 2)) {
            c.fill(GROUND + 1, GROUND + 3, B.POLE);
            c.set(GROUND + 4, ACC_LIGHT[s.acc2]);
        }
    }

    private static void building(Col c, Spec s, int su, int sv) {
        switch (s.type) {
            case T_RECT -> {
                int px = su - s.ox, pz = sv - s.oz;
                if (px < 0 || pz < 0 || px >= s.w || pz >= s.d) return;
                int H = 5 * s.n - 1;
                for (int yy = 0; yy <= H + 28; yy++) put(c, yy, rect(s, px, pz, yy, s.w, s.d, H, true));
            }
            case T_LOW -> {
                int px = su - s.ox, pz = sv - s.oz;
                if (px < 0 || pz < 0 || px >= s.w || pz >= s.d) return;
                int H = 5 * s.n - 1;
                for (int yy = 0; yy <= H + 9; yy++) put(c, yy, low(s, px, pz, yy, H));
            }
            case T_STEP -> stepped(c, s, su, sv);
            case T_ROUND -> round(c, s, su, sv);
            default -> twin(c, s, su, sv);
        }
    }

    private static void put(Col c, int yy, B b) { if (b != B.AIR) c.set(GROUND + 1 + yy, b); else c.set(GROUND + 1 + yy, B.AIR); }

    // ---------------------------------------------------------------- фасад прямоугольной башни
    /** px, pz — координаты внутри коробки w×d; yy — высота над землёй; H — высота крыши (уровень последней плиты) */
    private static B rect(Spec s, int px, int pz, int yy, int w, int d, int H, boolean antenna) {
        boolean ex = px == 0 || px == w - 1, ez = pz == 0 || pz == d - 1, edge = ex || ez;
        int f = yy % 5;
        if (yy > H + 1) {
            if (!antenna) return B.AIR;
            int cx = w / 2, cz = d / 2, k = yy - H - 1;       // антенна: стойка и красный огонёк
            if (px == cx && pz == cz) {
                int len = 8 + r(s.seed >> 5, 14);
                if (k < len) return B.ANTENNA;
                if (k == len) return B.BEACON_RED;
            }
            return B.AIR;
        }
        if (yy == H) {                                         // крыша
            if (edge) return ACC_LIGHT[s.acc];
            return B.ROOF;
        }
        if (yy == H + 1) {                                     // парапет и оборудование на крыше
            if (edge) return ((px + pz) & 1) == 0 ? ACC_GLASS[s.acc] : B.AIR;
            int bx = px / 4, bz = pz / 4;
            if (px % 4 >= 1 && px % 4 <= 2 && pz % 4 >= 1 && pz % 4 <= 2 && chance(h(bx, bz, s.seed), 22)) return B.GRAY;
            return B.AIR;
        }
        if (!edge) {                                           // внутри: перекрытие каждого этажа с лампами
            if (f == 4) return (px % 4 == 2 && pz % 4 == 2) ? ACC_LIGHT[s.acc] : B.FLOOR;
            return B.AIR;
        }
        boolean corner = ex && ez;
        if (corner) return (yy % 4 == 0) ? ACC_LIGHT[s.acc2] : B.BLACK;
        int along = ex ? pz : px;
        int len = ex ? d : w;
        if (yy <= 2 && Math.abs(along - len / 2) <= 1) return B.AIR;          // двери
        if (along % 5 == 0) return (yy % 8 == 6) ? ACC_LIGHT[s.acc2] : B.GRAY;    // колонны
        if (f == 4) {                                          // межэтажная полоса
            return (yy / 5) % 6 == 5 ? ACC_LIGHT[s.acc2] : B.BLACK;
        }
        if (chance(h(along + (ex ? 31 : 0), yy / 5, s.seed + (ex ? 1 : 2) * 7 + (px == w - 1 || pz == d - 1 ? 3 : 0)), 11)) return B.L_WARM;   // горящее окно
        return ACC_GLASS[s.acc];
    }

    // ---------------------------------------------------------------- низкое здание с неоновой вывеской
    private static B low(Spec s, int px, int pz, int yy, int H) {
        int w = s.w, d = s.d;
        B b = rect(s, px, pz, yy, w, d, H, false);
        boolean ex = px == 0 || px == w - 1, ez = pz == 0 || pz == d - 1;
        // неоновые рёбра вместо колонн
        if ((ex || ez) && !(ex && ez) && yy <= H - 1 && b == B.GRAY) return ACC_LIGHT[s.acc2];
        // вывеска на крыше: щит вдоль передней стороны (pz == 1), с «буквами» и подсветкой
        if (yy > H + 1 && yy <= H + 8 && pz == 1 && px >= 1 && px <= w - 2) {
            int ry = yy - H - 2;                               // 0..6
            if (ry == 0 || ry == 6 || px == 1 || px == w - 2) return B.GRAY;
            int gx = (px - 2) / 2, gy = (ry - 1) / 2;
            boolean on = chance(h(gx, gy, s.seed), 55);
            return on ? ACC_LIGHT[(s.acc + (gx % 3 == 0 ? 0 : 1) * 2) % 6] : ACC_GLASS[s.acc2];
        }
        if (yy > H + 1 && yy <= H + 8 && px % 6 == 3 && pz == 2 && px < w - 2) return B.GRAY;     // опоры
        return b;
    }

    // ---------------------------------------------------------------- ступенчатая башня
    private static void stepped(Col c, Spec s, int su, int sv) {
        int[] ins = {0, 3, 6};
        int[] hs = {5 * s.n1 - 1, 5 * s.n2 - 1, 5 * s.n - 1};
        int deepest = -1;
        for (int t = 0; t < 3; t++) {
            int i = ins[t], px = su - s.ox - i, pz = sv - s.oz - i;
            if (px >= 0 && pz >= 0 && px < s.w - 2 * i && pz < s.d - 2 * i) deepest = t;
        }
        if (deepest < 0) return;
        int top = hs[deepest] + 30;
        for (int yy = 0; yy <= top; yy++) {
            int t = deepest;
            while (t > 0 && yy <= hs[t - 1]) t--;
            int i = ins[t];
            put(c, yy, rect(s, su - s.ox - i, sv - s.oz - i, yy, s.w - 2 * i, s.d - 2 * i, hs[t], t == deepest && t == 2));
        }
    }

    // ---------------------------------------------------------------- круглая башня с «волной»
    private static double radiusAt(Spec s, int yy) { return s.rad + 1.6 * Math.sin(yy * 0.06 + (s.seed & 7)); }

    private static void round(Col c, Spec s, int su, int sv) {
        double dx = su + 0.5 - SUB / 2.0, dz = sv + 0.5 - SUB / 2.0, d2 = dx * dx + dz * dz;
        if (d2 > (s.rad + 3) * (s.rad + 3)) return;
        int H = 5 * s.n - 1;
        double ang = Math.atan2(dz, dx);
        for (int yy = 0; yy <= H + 30; yy++) {
            double rr = radiusAt(s, Math.min(yy, H));
            double in = (rr - 1.4) * (rr - 1.4), out = rr * rr;
            int f = yy % 5;
            B b = B.AIR;
            if (yy > H + 1) {
                int ci = (int) Math.floor(SUB / 2.0), k = yy - H - 1;
                if (su == ci && sv == ci) {
                    int len = 8 + r(s.seed >> 5, 12);
                    b = k < len ? B.ANTENNA : k == len ? B.BEACON_RED : B.AIR;
                }
            } else if (d2 <= out) {
                boolean wall = d2 > in;
                if (yy == H) b = wall ? ACC_LIGHT[s.acc] : B.ROOF;
                else if (yy == H + 1) b = wall && (((int) (ang * 10)) & 1) == 0 ? ACC_GLASS[s.acc] : B.AIR;
                else if (!wall) b = f == 4 ? ((Math.abs(dx) < 2 && Math.abs(dz) < 2) ? B.FLOOR : (((su + sv) % 4 == 0) ? ACC_LIGHT[s.acc] : B.FLOOR)) : B.AIR;
                else {
                    double k = (ang + Math.PI) / (2 * Math.PI) * 24;
                    boolean pillar = k - Math.floor(k) < 0.22;
                    double spiral = ((ang + Math.PI) / (2 * Math.PI) * 3 + yy * 0.012) % 1.0;
                    if (yy <= 2 && Math.abs(Math.sin(ang * 2)) < 0.08 && Math.abs(Math.cos(ang)) > 0.5 - 0.5) b = B.AIR;
                    else if (spiral < 0.07) b = ACC_LIGHT[s.acc2];
                    else if (pillar) b = B.GRAY;
                    else if (f == 4) b = B.BLACK;
                    else if (chance(h((int) (k * 3), yy / 5, s.seed), 11)) b = B.L_WARM;
                    else b = ACC_GLASS[s.acc];
                }
            }
            put(c, yy, b);
        }
    }

    // ---------------------------------------------------------------- башни-близнецы с мостом
    private static void twin(Col c, Spec s, int su, int sv) {
        int n2 = Math.max(4, s.n - 3 - r(s.seed >> 8, 6));
        int[] ox = {1, 16};
        int[] Hs = {5 * s.n - 1, 5 * n2 - 1};
        int by = 5 * Math.max(2, (int) Math.round(Math.min(s.n, n2) * 0.6));   // уровень пола моста
        // башни
        for (int t = 0; t < 2; t++) {
            int px = su - ox[t], pz = sv - 8;
            if (px < 0 || px >= 9 || pz < 0 || pz >= 9) continue;
            for (int yy = 0; yy <= Hs[t] + 28; yy++) {
                B b = rect(s, px, pz, yy, 9, 9, Hs[t], true);
                // проём моста в стене
                boolean wallX = (t == 0 && px == 8) || (t == 1 && px == 0);
                if (wallX && pz >= 3 && pz <= 5 && yy >= by + 1 && yy <= by + 2) b = B.AIR;
                put(c, yy, b);
            }
            return;
        }
        // мост между башнями (su 10..15)
        if (su >= 10 && su <= 15 && sv >= 10 && sv <= 14) {
            c.set(GROUND + 1 + by, sv == 12 && su % 2 == 0 ? ACC_LIGHT[s.acc] : B.FLOOR);
            boolean side = sv == 10 || sv == 14;
            c.set(GROUND + 2 + by, side ? ACC_GLASS[s.acc2] : B.AIR);
            c.set(GROUND + 3 + by, side ? ACC_GLASS[s.acc2] : B.AIR);
            c.set(GROUND + 4 + by, sv == 12 && su % 3 == 0 ? ACC_LIGHT[s.acc2] : B.ROOF);
        }
    }

    // ---------------------------------------------------------------- большой квартал: подиум и башня
    private static void big(Col c, int li, int lj, int lu, int lv) {
        int hh = h(li, lj, 33);
        int acc = r(hh >> 3, 6), acc2 = (acc + 2 + r(hh >> 6, 3)) % 6;
        Spec s = new Spec();
        s.seed = hh; s.acc = acc; s.acc2 = acc2;
        double f = heightFactor(li, lj);
        s.n = Math.max(12, Math.min(44, (int) Math.round((22 + r(hh >> 9, 22)) * f)));
        c.set(GROUND, plazaTile(lu, lv));
        // лента подсветки вокруг подиума
        boolean inPod = lu >= 3 && lu < 53 && lv >= 3 && lv < 53;
        if (!inPod) {
            if (lu >= 2 && lu <= 53 && lv >= 2 && lv <= 53) c.set(GROUND, ACC_LIGHT[acc]);
            if (((lu == 1 || lu == 54) && (lv == 1 || lv == 54))) { c.fill(GROUND + 1, GROUND + 4, B.POLE); c.set(GROUND + 5, ACC_LIGHT[acc2]); }
            return;
        }
        c.set(GROUND, B.FLOOR_LIGHT);
        int px = lu - 3, pz = lv - 3;
        int HP = 14;                                          // подиум 3 этажа
        boolean inTower = lu >= 16 && lu < 40 && lv >= 16 && lv < 40;
        if (!inTower) {
            Spec p = s;
            for (int yy = 0; yy <= HP + 5; yy++) {
                B b = rect(p, px, pz, yy, 50, 50, HP, false);
                if (yy == HP && !(px == 0 || pz == 0 || px == 49 || pz == 49)) {
                    // крыша подиума — терраса: плитка, подсветка и деревья
                    b = (((px >> 2) + (pz >> 2)) & 1) == 0 ? B.PLAZA_A : B.PLAZA_B;
                    if (px % 8 == 4 && pz % 8 == 4) b = ACC_LIGHT[acc2];
                }
                boolean post = !(px == 0 || pz == 0 || px == 49 || pz == 49) && px % 12 == 6 && pz % 12 == 6;
                if (post && yy > HP && yy <= HP + 3) b = B.POLE;
                if (post && yy == HP + 4) b = ACC_LIGHT[acc];
                put(c, yy, b);
            }
            return;
        }
        int tx = lu - 16, tz = lv - 16;
        int HT = 5 * s.n - 1;
        for (int yy = 0; yy <= HT + 30; yy++) {
            B b;
            if (yy <= HP) {
                boolean podEdge = false;
                b = (yy % 5 == 4) ? B.FLOOR : B.AIR;
                if (yy == HP) b = B.FLOOR;
            } else b = rect(s, tx, tz, yy, 24, 24, HT, true);
            put(c, yy, b);
        }
    }

    // ---------------------------------------------------------------- парк
    private static void park(Col c, int li, int lj, int lu, int lv) {
        int hh = h(li, lj, 44);
        int acc = r(hh >> 3, 6);
        double dx = lu + 0.5 - LOT / 2.0, dz = lv + 0.5 - LOT / 2.0, d2 = dx * dx + dz * dz;
        boolean pathX = Math.abs(dx) < 2.5, pathZ = Math.abs(dz) < 2.5;
        boolean ring = d2 > 8 * 8 && d2 < 11 * 11;
        B ground = B.GRASS;
        if (pathX || pathZ || ring) ground = B.PATH;
        boolean border = lu < 1 || lu > 54 || lv < 1 || lv > 54;
        if (border) ground = B.PLAZA_A;
        c.set(GROUND, ground);
        // пруд в центре с подсветкой дна
        if (d2 <= 6.5 * 6.5) {
            if (d2 > 5.5 * 5.5) { c.set(GROUND, B.QUARTZ); c.set(GROUND + 1, B.QUARTZ); }
            else { c.set(GROUND - 1, ACC_LIGHT[acc]); c.set(GROUND, B.WATER); }
        }
        // световая колонна с голограммой в центре пруда
        if (Math.abs(dx) < 1.6 && Math.abs(dz) < 1.6) {
            c.fill(GROUND + 1, GROUND + 3, B.QUARTZ);
            if (Math.abs(dx) < 0.6 && Math.abs(dz) < 0.6) c.fill(GROUND + 4, GROUND + 26, ACC_LIGHT[acc]);
            else c.fill(GROUND + 4, GROUND + 24, B.G_WHITE);
            if (Math.abs(dx) < 0.6 && Math.abs(dz) < 0.6) c.set(GROUND + 27, B.L_WARM);
        }
        // фонарики вдоль дорожек
        if ((pathX || pathZ) && !border && lu % 8 == 4 && lv % 8 == 4 && d2 > 12 * 12) {
            c.fill(GROUND + 1, GROUND + 3, B.POLE); c.set(GROUND + 4, ACC_LIGHT[(acc + lu / 8) % 6]);
        }
        // деревья: по одному на ячейку 9×9 со сдвигом
        if (!border && ground == B.GRASS) {
            for (int gx = Math.floorDiv(lu, 9) - 1; gx <= Math.floorDiv(lu, 9) + 1; gx++)
                for (int gz = Math.floorDiv(lv, 9) - 1; gz <= Math.floorDiv(lv, 9) + 1; gz++) {
                    int th = h(gx, gz, hh);
                    int tx = gx * 9 + 1 + r(th >> 3, 7), tz = gz * 9 + 1 + r(th >> 7, 7);
                    double tdx = tx + 0.5 - LOT / 2.0, tdz = tz + 0.5 - LOT / 2.0;
                    if (tx < 2 || tx > 53 || tz < 2 || tz > 53) continue;
                    if (Math.abs(tdx) < 3.5 || Math.abs(tdz) < 3.5 || tdx * tdx + tdz * tdz < 13 * 13 && tdx * tdx + tdz * tdz > 7 * 7) continue;
                    int trunk = 4 + r(th >> 11, 3);
                    int ddx = lu - tx, ddz = lv - tz;
                    if (ddx == 0 && ddz == 0) c.fill(GROUND + 1, GROUND + trunk, B.LOG);
                    for (int dy = -2; dy <= 2; dy++) {
                        int rad2 = dy == 2 ? 2 : dy == -2 ? 3 : 7;
                        if (ddx * ddx + ddz * ddz + dy * dy * 2 <= rad2 + 3 && !(ddx == 0 && ddz == 0 && dy < 0))
                            c.set(GROUND + trunk + dy + 1, B.LEAVES);
                    }
                    if (ddx == 0 && ddz == 0) c.set(GROUND + trunk + 1, B.LEAVES);
                    if (ddx == 1 && ddz == 1) c.set(GROUND + trunk, B.L_MAGENTA);          // «светящийся плод»
                }
        }
        // гирлянда вокруг парка: светящиеся плиты на границе
        if (border && (lu + lv) % 4 == 0) c.set(GROUND, ACC_LIGHT[acc]);
    }

    // ---------------------------------------------------------------- площадь с фонтаном и «столбами света»
    private static void plaza(Col c, int li, int lj, int lu, int lv) {
        int hh = h(li, lj, 55);
        int acc = r(hh >> 3, 6), acc2 = (acc + 3) % 6;
        c.set(GROUND, plazaTile(lu, lv));
        double dx = lu + 0.5 - LOT / 2.0, dz = lv + 0.5 - LOT / 2.0, d2 = dx * dx + dz * dz;
        if (Math.floorMod(lu, 8) == 0 || Math.floorMod(lv, 8) == 0) c.set(GROUND, B.PLAZA_LINE);
        // центральный фонтан
        if (d2 <= 9 * 9) {
            if (d2 > 8 * 8) { c.set(GROUND, ACC_LIGHT[acc]); }
            else if (d2 > 7 * 7) { c.set(GROUND, B.QUARTZ); c.set(GROUND + 1, B.QUARTZ); }
            else { c.set(GROUND - 1, ACC_LIGHT[acc2]); c.set(GROUND, B.WATER); }
        }
        if (d2 <= 3 * 3 + 0.5) {
            c.fill(GROUND + 1, GROUND + 2, B.QUARTZ);
            if (d2 <= 1.6) c.fill(GROUND + 3, GROUND + 9, ACC_LIGHT[acc]);
            else if (d2 <= 5) c.fill(GROUND + 3, GROUND + 6, B.G_WHITE);
        }
        // кольцо столбов света
        for (int k = 0; k < 8; k++) {
            double ang = k * Math.PI / 4 + Math.PI / 8;
            int px = (int) Math.floor(LOT / 2.0 + Math.cos(ang) * 15), pz = (int) Math.floor(LOT / 2.0 + Math.sin(ang) * 15);
            if (lu == px && lv == pz) {
                c.fill(GROUND + 1, GROUND + 11, B.POLE);
                c.set(GROUND + 12, ACC_LIGHT[(acc + k) % 6]);
                c.set(GROUND + 13, B.L_WARM);
            }
        }
        // четыре киоска по углам
        for (int sx = 0; sx < 2; sx++) for (int sz = 0; sz < 2; sz++) {
            int ku = 4 + sx * 41, kv = 4 + sz * 41;       // 4..8 и 45..49
            int px = lu - ku, pz = lv - kv;
            if (px < 0 || pz < 0 || px >= 7 || pz >= 7) continue;
            for (int yy = 0; yy <= 5; yy++) {
                boolean edge = px == 0 || pz == 0 || px == 6 || pz == 6;
                B b;
                if (yy == 5) b = edge ? ACC_LIGHT[acc] : B.ROOF;
                else if (edge) b = (px == 3 && yy < 3 && (pz == 0 || pz == 6) ) ? B.AIR : (px == 0 || px == 6) && pz % 2 == 0 ? B.GRAY : ACC_GLASS[acc2];
                else b = yy == 4 ? ((px == 3 && pz == 3) ? B.L_WARM : B.FLOOR) : B.AIR;
                put(c, yy, b);
            }
        }
    }

    // ---------------------------------------------------------------- Нексус — главная башня
    private static void nexus(Col c, int lu, int lv) {
        double dx = lu + 0.5 - LOT / 2.0, dz = lv + 0.5 - LOT / 2.0, d2 = dx * dx + dz * dz, dd = Math.sqrt(d2);
        double ang = Math.atan2(dz, dx);
        // площадь: шахматная плитка, радиальные неоновые лучи и кольца
        c.set(GROUND, plazaTile(lu, lv));
        double kk = (ang + Math.PI) / (2 * Math.PI) * 16;
        if (dd > 22 && Math.abs(kk - Math.round(kk)) < 0.035 * 16 / Math.max(1, dd / 12)) c.set(GROUND, B.PLAZA_LINE);
        if (Math.abs(dd - 25.5) < 0.6 || Math.abs(dd - 27.2) < 0.4) c.set(GROUND, B.PLAZA_LINE);
        // четыре фонтана по углам квартала
        for (int fx = 0; fx < 2; fx++) for (int fz = 0; fz < 2; fz++) {
            double cx = 6.5 + fx * 43, cz = 6.5 + fz * 43;
            double ex = lu + 0.5 - cx, ez = lv + 0.5 - cz, e2 = ex * ex + ez * ez;
            if (e2 <= 6 * 6) {
                if (e2 > 5 * 5) { c.set(GROUND, B.L_CYAN); }
                else if (e2 > 4 * 4) { c.set(GROUND, B.QUARTZ); c.set(GROUND + 1, B.QUARTZ); }
                else { c.set(GROUND - 1, B.L_BLUE); c.set(GROUND, B.WATER); }
                if (e2 <= 1.5) c.fill(GROUND + 1, GROUND + 7, B.L_MAGENTA);
            }
        }
        // подиум: цилиндр r = 22, три этажа, терраса на крыше
        int HP = 14;
        if (dd <= 22) {
            c.set(GROUND, B.FLOOR_LIGHT);
            for (int yy = 0; yy <= HP + 1; yy++) {
                boolean wall = dd > 20.6;
                int f = yy % 5;
                B b = B.AIR;
                if (yy == HP) {
                    b = wall ? B.L_CYAN : (dd > 12.2 ? (((int) (dd) % 3 == 0) ? B.PLAZA_LINE : plazaTile(lu, lv)) : B.FLOOR);
                } else if (yy == HP + 1) {
                    if (wall && ((int) Math.floor(ang * 12) & 1) == 0) b = B.G_CYAN;
                    else if (!wall && Math.abs(dd - 16) < 0.6 && ((int) Math.floor((ang + Math.PI) * 8) & 1) == 0) b = B.L_MAGENTA;
                } else if (wall) {
                    double k = (ang + Math.PI) / (2 * Math.PI) * 48;
                    boolean pillar = k - Math.floor(k) < 0.25;
                    boolean door = yy <= 2 && Math.abs(Math.sin(ang * 2)) < 0.06;
                    if (door) b = B.AIR;
                    else if (pillar) b = (yy % 6 == 5) ? B.L_CYAN : B.GRAY;
                    else if (f == 4) b = B.BLACK;
                    else b = chance(h((int) k, yy / 5, 1234), 9) ? B.L_WARM : B.G_CYAN;
                } else if (dd > 12.2) {
                    b = f == 4 ? ((lu + lv) % 4 == 0 ? B.L_CYAN : B.FLOOR) : B.AIR;
                } else b = f == 4 ? B.FLOOR : B.AIR;
                put(c, yy, b);
            }
        }
        // башня: радиус дышит по высоте; спираль неона радужная
        final int N = 45, H = 5 * N - 1;
        if (dd > 18) return;
        for (int yy = HP + 1; yy <= H + 26; yy++) {
            double rr = 10.5 + 2.2 * Math.sin(yy * 0.045 + 0.5) - (yy > 190 ? (yy - 190) * 0.12 : 0);
            double in = (rr - 1.5) * (rr - 1.5), out = rr * rr;
            int f = yy % 5;
            B b = B.AIR;
            if (yy > H + 1) {
                int k = yy - H - 1;
                if (Math.abs(dx) < 1.6 && Math.abs(dz) < 1.6) {
                    if (Math.abs(dx) < 0.6 && Math.abs(dz) < 0.6) b = k < 12 ? B.L_CYAN : k < 24 ? B.ANTENNA : k == 24 ? B.BEACON_RED : B.AIR;
                    else if (k < 12) b = B.G_WHITE;
                }
            } else if (d2 <= out) {
                boolean wall = d2 > in;
                if (yy == H) b = wall ? B.L_MAGENTA : B.ROOF;
                else if (yy == H + 1) b = wall && ((int) Math.floor(ang * 10) & 1) == 0 ? B.G_MAGENTA : B.AIR;
                else if (!wall) b = f == 4 ? ((Math.floorMod(lu + lv, 4) == 0) ? B.L_CYAN : B.FLOOR) : B.AIR;
                else {
                    double k = (ang + Math.PI) / (2 * Math.PI) * 20;
                    boolean pillar = k - Math.floor(k) < 0.2;
                    double sp = ((ang + Math.PI) / (2 * Math.PI) * 2 + yy * 0.008) % 1.0;
                    B[] rainbow = {B.L_CYAN, B.L_BLUE, B.L_PURPLE, B.L_MAGENTA, B.L_ORANGE, B.L_LIME};
                    int ci = Math.min(5, (int) (yy / (H / 6.0)));
                    if (sp < 0.06) b = rainbow[ci];
                    else if (pillar) b = B.BLACK;
                    else if (f == 4) b = B.GRAY;
                    else b = chance(h((int) (k * 4), yy / 5, 4321), 10) ? B.L_WARM : (ci % 2 == 0 ? B.G_CYAN : B.G_BLUE);
                }
            }
            // кольца обзорных площадок
            int[][] disks = {{118, 120, 17}, {165, 167, 15}};
            for (int[] dk : disks) {
                if (yy >= dk[0] && yy <= dk[1] && dd <= dk[2] && b == B.AIR) {
                    if (dd > dk[2] - 1.3) b = yy == dk[1] ? B.L_CYAN : B.G_CYAN;
                    else if (dd > rr && yy == dk[0]) b = B.FLOOR;
                    else if (dd > rr && yy == dk[1]) b = (Math.floorMod(lu + lv, 3) == 0) ? B.L_CYAN : B.FLOOR;
                    else if (dd > rr && yy == dk[0] + 1) b = dd > dk[2] - 1.3 ? B.G_CYAN : B.AIR;
                }
            }
            put(c, yy, b);
        }
    }
}
