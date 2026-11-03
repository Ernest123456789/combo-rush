package com.derevna230.comborush.universe;

import com.derevna230.comborush.universe.NightCity.B;
import com.derevna230.comborush.universe.NightCity.Col;

import java.util.ArrayList;
import java.util.List;

/**
 * Окрестности «Ночного города»: холмы, небольшие горы, озёра и гоночные трассы за городской чертой.
 * Как и сам город, всё считается из координат (шум и дороги детерминированы), так что чанки не зависят друг от друга.
 *
 * <p>Трассы: четыре шоссе выходят из города по главным улицам и ведут к трём маршрутам:
 * «Серпантин» (восток) — подъём на гору шпильками к смотровой площадке;
 * «Гран-при» (запад) — быстрая петля со шпильками и шиканой;
 * «Дрифт-кольцо» (север) — тесная петля из S-поворотов. Дорога сама подстраивает рельеф: вдоль неё
 * выравнивается полотно, а склон рядом плавно сдвигается к нему.
 */
public final class NightCityWild {
    private NightCityWild() {}

    static final int G = NightCity.GROUND;
    /** Уровень воды озёр */
    public static final int WATER_Y = G - 2;
    /** Полуширина полотна (дорога 11 блоков) */
    public static final double HW = 5.5;
    private static final double MAX_SLOPE = 0.25;
    private static final double TAU = 3.0;
    private static final double ISLAND = 8.0;
    /** Мир — квадрат вокруг центра города: половина стороны; по краю — хребет и невидимый барьер */
    public static final int WORLD_C = 44, WORLD_HALF = 950;

    /** Расстояние до края мира (положительное — внутри), по Чебышёву */
    public static double edgeDist(double px, double pz) { return WORLD_HALF - Math.max(Math.abs(px - WORLD_C), Math.abs(pz - WORLD_C)); }

    // ---------------------------------------------------------------- шум
    private static double rnd(int xi, int zi, int seed) { return (NightCity.h(xi, zi, seed) & 0xFFFF) / 32767.5 - 1.0; }

    /** Гладкий value-шум в диапазоне [−1; 1] */
    static double vn(double x, double z, int seed) {
        int xi = (int) Math.floor(x), zi = (int) Math.floor(z);
        double fx = x - xi, fz = z - zi;
        fx = fx * fx * fx * (fx * (fx * 6 - 15) + 10);
        fz = fz * fz * fz * (fz * (fz * 6 - 15) + 10);
        double a = rnd(xi, zi, seed), b = rnd(xi + 1, zi, seed), c = rnd(xi, zi + 1, seed), d = rnd(xi + 1, zi + 1, seed);
        double ab = a + (b - a) * fx, cd = c + (d - c) * fx;
        return ab + (cd - ab) * fz;
    }

    static double smooth(double t) { t = t < 0 ? 0 : t > 1 ? 1 : t; return t * t * (3 - 2 * t); }

    // ---------------------------------------------------------------- рельеф
    /** Крупные «горы»: {x, z, σx, σz, высота} — гауссовы холмы поверх шума */
    private static final double[][] BUMPS = {
        {544, 204, 230, 85, 62},        // гора серпантина (на востоке)
    };

    static double cityDist(double x, double z) {
        double dx = Math.max(Math.max(NightCity.CITY_MIN - x, x - (NightCity.CITY_MAX + 1)), 0);
        double dz = Math.max(Math.max(NightCity.CITY_MIN - z, z - (NightCity.CITY_MAX + 1)), 0);
        return Math.hypot(dx, dz);
    }

    /** Естественная высота рельефа (без дорог) */
    public static double terrain(double x, double z) {
        double amp = smooth((cityDist(x, z) - 20) / 150.0);
        if (amp <= 0) return G;
        double hills = 20 * vn(x / 170, z / 170, 11) + 9 * vn(x / 70, z / 70, 12) + 3.5 * vn(x / 28, z / 28, 13) + 1.0 * vn(x / 11, z / 11, 14);
        double mask = smooth((vn(x / 380 + 7.3, z / 380 - 3.1, 21) + 0.1) / 0.6);
        double ridge = 1 - Math.abs(vn(x / 140, z / 140, 22));
        double mt = mask * (8 + 36 * ridge * ridge);
        for (double[] b : BUMPS) {
            double dx = (x - b[0]) / b[2], dz = (z - b[1]) / b[3];
            mt = Math.max(mt, b[4] * Math.exp(-(dx * dx + dz * dz) / 2) * (0.9 + 0.1 * vn(x / 40, z / 40, 31)));
        }
        double t = G + amp * (hills + mt + 10);
        // хребет по краю мира
        double rim = smooth((150 - edgeDist(x, z)) / 150.0);
        if (rim > 0) t += rim * (62 + 26 * vn(x / 55, z / 55, 51) + 12 * vn(x / 23, z / 23, 52));
        return Math.max(t, G - 7);
    }

    static double slope(double x, double z) {
        double sx = Math.abs(terrain(x + 2, z) - terrain(x - 2, z)), sz = Math.abs(terrain(x, z + 2) - terrain(x, z - 2));
        return Math.max(sx, sz) / 4.0;
    }

    // ---------------------------------------------------------------- дороги
    /** Дорога-ломаная с точками через ~2 блока; высоты уже согласованы с рельефом */
    public static final class Road {
        public final String name;
        public final boolean closed, circuit;
        public final int acc;
        final int n;
        final double[] x, z, y, dl, s;
        final boolean[] turn;
        final byte[] curv;
        public final double len;
        double minX, maxX, minZ, maxZ;
        double[] gates = new double[0];
        boolean[] checker = new boolean[0];
        boolean hasRing; double ringX, ringZ, ringY;

        Road(String name, List<double[]> pts, boolean closed, boolean circuit, int acc) {
            this.name = name; this.closed = closed; this.circuit = circuit; this.acc = acc;
            n = pts.size();
            x = new double[n]; z = new double[n]; y = new double[n]; dl = new double[n]; s = new double[n];
            turn = new boolean[n]; curv = new byte[n];
            minX = minZ = 1e18; maxX = maxZ = -1e18;
            for (int i = 0; i < n; i++) {
                x[i] = pts.get(i)[0]; z[i] = pts.get(i)[1];
                if (i > 0) s[i] = s[i - 1] + Math.hypot(x[i] - x[i - 1], z[i] - z[i - 1]);
                minX = Math.min(minX, x[i]); maxX = Math.max(maxX, x[i]);
                minZ = Math.min(minZ, z[i]); maxZ = Math.max(maxZ, z[i]);
            }
            len = s[n - 1] + (closed ? Math.hypot(x[0] - x[n - 1], z[0] - z[n - 1]) : 0);
            minX -= 42; maxX += 42; minZ -= 42; maxZ += 42;
            // повороты: изменение курса на шаге > 2/45 рад (радиус < 45) → кербы и маркеры, с запасом по 8 точек в обе стороны
            for (int i = 1; i < (closed ? n + 1 : n - 1); i++) {
                int a = (i - 1 + n) % n, b = i % n, c = (i + 1) % n;
                double h1 = Math.atan2(z[b] - z[a], x[b] - x[a]), h2 = Math.atan2(z[c] - z[b], x[c] - x[b]);
                double d = h2 - h1;
                while (d > Math.PI) d -= 2 * Math.PI;
                while (d < -Math.PI) d += 2 * Math.PI;
                if (Math.abs(d) > 2.0 / 45) {
                    for (int k = -8; k <= 8; k++) {
                        int j = closed ? Math.floorMod(b + k, n) : b + k;
                        if (j < 0 || j >= n) continue;
                        turn[j] = true; curv[j] = (byte) (d > 0 ? 1 : -1);
                    }
                }
            }
        }

        public int n() { return n; }
        public double yAt(int i) { return y[i]; }

        double segLen(int i) { int j = i + 1 == n ? 0 : i + 1; return Math.hypot(x[j] - x[i], z[j] - z[i]); }

        /** Профиль высот: сглаженный рельеф вдоль пути, ограниченный по уклону; p0/p1 — закреплённые концы (null — свободный) */
        void setHeights(Double p0, Double p1, int flatFrom) {
            double[] raw = new double[n];
            for (int i = 0; i < n; i++) raw[i] = terrain(x[i], z[i]);
            int w = 14;
            for (int i = 0; i < n; i++) {
                double acc = 0; int cnt = 0;
                for (int k = -w; k <= w; k++) {
                    int j = closed ? Math.floorMod(i + k, n) : i + k;
                    if (j < 0 || j >= n) continue;
                    acc += raw[j]; cnt++;
                }
                y[i] = Math.max(acc / cnt, G);
            }
            for (int it = 0; it < 6; it++) {
                if (p0 != null) y[0] = p0;
                if (p1 != null) y[n - 1] = p1;
                for (int pass = 0; pass < (closed ? 2 : 1); pass++) {
                    for (int i = 1; i < n; i++) y[i] = clamp(y[i], y[i - 1], MAX_SLOPE * segLen(i - 1));
                    if (closed) y[0] = clamp(y[0], y[n - 1], MAX_SLOPE * segLen(n - 1));
                    for (int i = n - 2; i >= 0; i--) y[i] = clamp(y[i], y[i + 1], MAX_SLOPE * segLen(i));
                    if (closed) y[n - 1] = clamp(y[n - 1], y[0], MAX_SLOPE * segLen(n - 1));
                }
            }
            if (flatFrom >= 0) for (int i = flatFrom; i < n; i++) y[i] = y[flatFrom];
            if (p0 != null) { y[0] = p0; for (int i = 1; i < n; i++) y[i] = clamp(y[i], y[i - 1], MAX_SLOPE * segLen(i - 1)); }
            if (p1 != null) { y[n - 1] = p1; for (int i = n - 2; i >= 0; i--) y[i] = clamp(y[i], y[i + 1], MAX_SLOPE * segLen(i)); }
            for (int i = 0; i < n; i++) dl[i] = y[i] - terrain(x[i], z[i]);
        }

        private static double clamp(double v, double ref, double step) { return Math.max(ref - step, Math.min(ref + step, v)); }

        /** Номер ближайшей точки по длине дуги */
        int indexAt(double sTarget) {
            int best = 0; double bd = 1e18;
            for (int i = 0; i < n; i++) { double d = Math.abs(s[i] - sTarget); if (d < bd) { bd = d; best = i; } }
            return best;
        }

        void gates(double[] at, boolean[] chk) { gates = at; checker = chk; }
    }

    /** «Черепашка»: прямые и дуги; положительный угол — поворот в сторону +z при движении по +x */
    private static final class Pen {
        final List<double[]> pts = new ArrayList<>();
        double x, z, a, lcx, lcz;
        Pen(double x, double z, double deg) { this.x = x; this.z = z; a = Math.toRadians(deg); pts.add(new double[]{x, z}); }
        Pen fwd(double len) {
            int n = Math.max(1, (int) Math.round(len / 2)); double st = len / n;
            for (int i = 0; i < n; i++) { x += Math.cos(a) * st; z += Math.sin(a) * st; pts.add(new double[]{x, z}); }
            return this;
        }
        Pen arc(double r, double deg) {
            double th = Math.toRadians(deg); int sg = th > 0 ? 1 : -1;
            double cx = x + sg * r * (-Math.sin(a)), cz = z + sg * r * Math.cos(a);
            lcx = cx; lcz = cz;
            int n = Math.max(2, (int) Math.round(Math.abs(th) * r / 2));
            for (int k = 1; k <= n; k++) {
                double a2 = a + th * k / n;
                pts.add(new double[]{cx + sg * r * Math.sin(a2), cz - sg * r * Math.cos(a2)});
            }
            a += th;
            double[] l = pts.get(pts.size() - 1); x = l[0]; z = l[1];
            return this;
        }
    }

    private static final double F = 0, A = 1;

    /** Петля из половины пути: вторая половина — поворот первой на 180° вокруг середины между концами */
    private static List<double[]> loopPoints(double[][] cmds, double rotDeg, boolean mirror) {
        Pen p = new Pen(0, 0, 0);
        for (double[] c : cmds) { if (c[0] == F) p.fwd(c[1]); else p.arc(c[1], c[2]); }
        List<double[]> half = p.pts;
        double[] a = half.get(0), b = half.get(half.size() - 1);
        double cx = (a[0] + b[0]) / 2, cz = (a[1] + b[1]) / 2;
        List<double[]> all = new ArrayList<>(half);
        for (int i = 1; i < half.size() - 1; i++) all.add(new double[]{2 * cx - half.get(i)[0], 2 * cz - half.get(i)[1]});
        double cr = Math.cos(Math.toRadians(rotDeg)), sr = Math.sin(Math.toRadians(rotDeg));
        List<double[]> out = new ArrayList<>();
        for (double[] q : all) {
            double zz = mirror ? -q[1] : q[1];
            out.add(new double[]{q[0] * cr - zz * sr, q[0] * sr + zz * cr});
        }
        return out;
    }

    private static List<double[]> straight(double x0, double z0, double x1, double z1) {
        int n = Math.max(1, (int) Math.round(Math.hypot(x1 - x0, z1 - z0) / 2));
        List<double[]> l = new ArrayList<>();
        for (int i = 0; i <= n; i++) l.add(new double[]{x0 + (x1 - x0) * i / n, z0 + (z1 - z0) * i / n});
        return l;
    }

    // чертежи половин петель: {тип, длина | радиус, угол}
    private static final double[][] GRAND_PRIX = {
        {F, 110}, {A, 34, 90}, {F, 30}, {A, 13, -150}, {F, 22}, {A, 20, 130}, {F, 35}, {A, 16, -80},
        {F, 26}, {A, 16, 110}, {A, 40, 30}, {F, 70}, {A, 30, 50}, {F, 60}};
    private static final double[][] DRIFT = {
        {F, 60}, {A, 13, 120}, {F, 8}, {A, 13, -70}, {F, 14}, {A, 13, 120}, {F, 20}, {A, 15, -100},
        {A, 14, 90}, {F, 24}, {A, 13, -70}, {F, 10}, {A, 13, 80}, {F, 30}};

    public static final List<Road> ROADS = new ArrayList<>();
    private static Road grandPrixRoad, driftRoad, serpRoad;

    /** Точки для телепорта операторов: {x, y (стоять), z, yaw}; ключи: serpentine, summit, grandprix, drift */
    public static double[] spot(String key) {
        Road r; int i;
        switch (key) {
            case "serpentine" -> { r = serpRoad; i = 20; }
            case "grandprix" -> { r = grandPrixRoad; i = grandPrixRoad.n - 4; }
            case "drift" -> { r = driftRoad; i = driftRoad.n - 4; }
            case "summit" -> {
                r = serpRoad;
                return new double[]{r.ringX, r.ringY + 1, r.ringZ + 10, 180};
            }
            default -> { return null; }
        }
        int j = (i + 1) % r.n;
        double yaw = Math.toDegrees(Math.atan2(-(r.x[j] - r.x[i]), r.z[j] - r.z[i]));
        return new double[]{r.x[i], r.y[i] + 1, r.z[i], yaw};
    }

    static {
        try { build(); } catch (RuntimeException e) { throw new IllegalStateException("NightCityWild.build", e); }
    }

    private static void build() {
        final double CE = NightCity.CITY_MAX + 1, CW = NightCity.CITY_MIN - 1;
        final double LANE = 8.0;                    // осевая линия главных улиц (блоки 3..12 в квартале 0)

        // --- «Гран-при» на западе: петля стоит стартовой прямой вдоль z, шоссе упирается в её середину
        List<double[]> gp = loopPoints(GRAND_PRIX, 90, false);
        Road gpTmp = new Road("tmp", gp, true, true, 0);
        int jg = gpTmp.indexAt(56);
        double tx = -360 - gp.get(jg)[0], tz = LANE - gp.get(jg)[1];
        for (double[] q : gp) { q[0] += tx; q[1] += tz; }
        Road grandPrix = new Road("Гран-при", gp, true, true, 0);
        grandPrix.setHeights(null, null, -1);
        grandPrix.gates(new double[]{0, grandPrix.len / 2}, new boolean[]{true, false});
        double[] gj = gp.get(jg);
        Road hwW = new Road("Западное шоссе", straight(CW, LANE, gj[0], gj[1]), false, false, 4);
        hwW.setHeights((double) G, grandPrix.y[jg], -1);
        hwW.gates(new double[]{22}, new boolean[]{false});

        // --- «Дрифт-кольцо» на севере
        List<double[]> dr = loopPoints(DRIFT, 0, true);
        Road drTmp = new Road("tmp", dr, true, true, 0);
        int jd = drTmp.indexAt(30);
        double ux = LANE - dr.get(jd)[0], uz = -300 - dr.get(jd)[1];
        for (double[] q : dr) { q[0] += ux; q[1] += uz; }
        Road drift = new Road("Дрифт-кольцо", dr, true, true, 3);
        drift.setHeights(null, null, -1);
        drift.gates(new double[]{0, drift.len / 2}, new boolean[]{true, false});
        double[] dj = dr.get(jd);
        Road hwN = new Road("Северное шоссе", straight(LANE, CW, dj[0], dj[1]), false, false, 4);
        hwN.setHeights((double) G, drift.y[jd], -1);
        hwN.gates(new double[]{22}, new boolean[]{false});

        // --- «Серпантин» на востоке: шоссе плавно переходит в зигзаг шпилек, на вершине — кольцо-разворот
        Pen pe = new Pen(CE, LANE, 0);
        pe.fwd(330);
        for (int k = 0; k < 7; k++) {
            pe.arc(13, k % 2 == 0 ? 180 : -180);
            pe.fwd(k % 3 == 1 ? 80 : 90);
        }
        int ringStart = pe.pts.size() - 1;
        pe.arc(14, -360);
        Road serp = new Road("Серпантин", pe.pts, false, false, 1);
        serp.setHeights((double) G, null, ringStart);
        serp.hasRing = true; serp.ringX = pe.lcx; serp.ringZ = pe.lcz; serp.ringY = serp.y[ringStart];
        double gs = serp.s[ringStart] - 14;
        serp.gates(new double[]{22, gs}, new boolean[]{false, true});

        grandPrixRoad = grandPrix; driftRoad = drift; serpRoad = serp;
        ROADS.add(hwW); ROADS.add(grandPrix); ROADS.add(hwN); ROADS.add(drift); ROADS.add(serp);
    }

    // ---------------------------------------------------------------- запрос дорог
    static final class Probe {
        double dmin = 1e9, delta, roadY, s, t;
        Road road; boolean turn; int curv;
    }

    private static void probe(double px, double pz, Probe p) {
        p.dmin = 1e9; p.road = null;
        double sw = 0, sd = 0, sy = 0;
        for (Road r : ROADS) {
            if (px < r.minX || px > r.maxX || pz < r.minZ || pz > r.maxZ) continue;
            int m = r.closed ? r.n : r.n - 1;
            for (int i = 0; i < m; i++) {
                int j = i + 1 == r.n ? 0 : i + 1;
                double ax = r.x[i], az = r.z[i], ex = r.x[j] - ax, ez = r.z[j] - az, l2 = ex * ex + ez * ez;
                double u = ((px - ax) * ex + (pz - az) * ez) / l2;
                u = u < 0 ? 0 : u > 1 ? 1 : u;
                double dx = px - (ax + ex * u), dz = pz - (az + ez * u), d2 = dx * dx + dz * dz;
                if (d2 > 1600) continue;
                double d = Math.sqrt(d2);
                if (d < p.dmin) {
                    double k = Math.exp(-(p.dmin - d) / TAU);
                    sw *= k; sd *= k; sy *= k;
                    p.dmin = d; p.road = r;
                    double l = Math.sqrt(l2);
                    p.s = r.s[i] + u * l;
                    double cr = (ex * (pz - az) - ez * (px - ax)) / l;
                    p.t = cr >= 0 ? d : -d;
                    p.turn = r.turn[i] || r.turn[j];
                    p.curv = r.turn[i] ? r.curv[i] : r.curv[j];
                }
                double w = Math.exp(-(d - p.dmin) / TAU);
                sw += w;
                sd += w * (r.dl[i] + (r.dl[j] - r.dl[i]) * u);
                sy += w * (r.y[i] + (r.y[j] - r.y[i]) * u);
            }
        }
        if (p.road != null) { p.delta = sd / sw; p.roadY = sy / sw; }
    }

    /** Итоговая высота поверхности: рельеф, сдвинутый вдоль дороги, и ровное полотно в её коридоре */
    static double height(double px, double pz, Probe p) {
        double t = terrain(px, pz);
        probe(px, pz, p);
        if (p.road == null) return NightCityVillage.pad(px, pz, t);
        double w = 1 - smooth((p.dmin - (HW + 7)) / 21.0);
        double base = t + w * p.delta;
        double c = 1 - smooth((p.dmin - (HW + 1)) / 5.0);
        return NightCityVillage.pad(px, pz, base + (p.roadY - base) * c);
    }

    /** Расстояние до ближайшей точки асфальтовых дорог */
    static double roadDist(double x, double z) {
        double best = 1e9;
        for (Road r : ROADS) for (int i = 0; i < r.n; i++) best = Math.min(best, Math.hypot(r.x[i] - x, r.z[i] - z));
        return best;
    }

    static double[] nearestRoadPoint(double x, double z) {
        double best = 1e9; double[] at = null;
        for (Road r : ROADS) for (int i = 0; i < r.n; i++) {
            double d = Math.hypot(r.x[i] - x, r.z[i] - z);
            if (d < best) { best = d; at = new double[]{r.x[i], r.z[i]}; }
        }
        return at;
    }

    // ---------------------------------------------------------------- зоны
    /** Лесистость 0..1 */
    static double forest(double x, double z) { return smooth((vn(x / 260 + 11.1, z / 260 - 4.2, 41) + 0.05) / 0.5); }
    /** Луговость 0..1 (где нет леса) */
    static double meadow(double x, double z) { return smooth((vn(x / 240 - 2.7, z / 240 + 9.3, 42) - 0.0) / 0.5) * (1 - forest(x, z)); }

    // ---------------------------------------------------------------- колонка
    private static int roundH(double v) { return Math.max(2, Math.min(300, (int) Math.floor(v + 0.5))); }

    public static void column(int x, int z, Col col) {
        double px = x + 0.5, pz = z + 0.5;
        double ed = edgeDist(px, pz);
        if (ed < -6) return;                                  // за краем мира — пустота
        Probe p = new Probe();
        int h = roundH(height(px, pz, p));
        Road rd = p.road;

        // кольцо на вершине серпантина: ровный остров с неоновой стелой
        Road ringRoad = null; double ringD = 0;
        for (Road r : ROADS) {
            if (!r.hasRing) continue;
            double d = Math.hypot(px - r.ringX, pz - r.ringZ);
            if (d <= ISLAND) { ringRoad = r; ringD = d; h = roundH(r.ringY); }
        }
        boolean onRoad = rd != null && p.dmin <= HW && ringRoad == null;
        int ry = rd == null ? h : (int) Math.round(p.roadY);
        double forest = forest(px, pz);

        B top, sub;
        boolean path = false;
        if (onRoad) {
            top = roadTile(p, rd); sub = B.STONE;
        } else {
            double sl = (h <= WATER_Y + 1 || h - G > 44) ? 0 : slope(px, pz);
            int e = h - G;
            if (h <= WATER_Y + 1) { top = B.SAND; sub = B.SAND; }
            else if (e > 58) { top = B.SNOW; sub = B.STONE; }
            else if (sl > 0.9 || e > 44) { top = NightCity.r(NightCity.h(x, z, 91), 4) == 0 ? B.ANDESITE : B.STONE; sub = B.STONE; }
            else {
                top = B.GRASS; sub = B.DIRT;
                int gh = NightCity.r(NightCity.h(x, z, 92), 100);
                if (forest > 0.5) { if (gh < 30) top = B.PODZOL; else if (gh < 40) top = B.MOSS; }
                // протоптанные грунтовые тропы
                if (ringRoad == null && (rd == null || p.dmin > HW + 2.5)) {
                    double pe = NightCityVillage.pathEdge(px, pz);
                    if (pe < 0.55 * vn(px / 4, pz / 4, 71)) {
                        path = true;
                        int ph = NightCity.r(NightCity.h(x, z, 93), 100);
                        if (e <= 1) top = B.MUD;
                        else if (pe < -1.0) top = ph < 68 ? B.PATH_DIRT : ph < 88 ? B.COARSE : B.GRAVEL;
                        else top = ph < 50 ? B.COARSE : ph < 78 ? B.DIRT : B.PODZOL;
                        sub = B.DIRT;
                    }
                }
            }
            if (rd != null && ringRoad == null && p.dmin > HW && p.dmin <= HW + 1.0)
                top = Math.floorMod((int) Math.floor(p.s), 6) == 0 ? NightCity.ACC_LIGHT[rd.acc] : B.ALLEY;
            if (ringRoad != null) {
                if (ringD > 6.5) top = NightCity.ACC_LIGHT[ringRoad.acc];
                else if (ringD > 3.4) top = (((x >> 1) + (z >> 1)) & 1) == 0 ? B.PLAZA_A : B.PLAZA_B;
                else top = B.PLAZA_LINE;
                sub = B.STONE;
            }
        }

        col.set(0, B.BEDROCK);
        int solid = h - 1;
        col.fill(1, Math.min(solid, 40), B.DEEPSLATE);
        if (solid > 40) col.fill(41, solid, B.STONE);
        if (sub != B.STONE) col.fill(Math.max(1, h - 3), solid, sub);
        col.set(h, top);
        if (h < WATER_Y) col.fill(h + 1, WATER_Y, B.WATER);

        // край мира: невидимый барьер на всю высоту и огни на гребне
        if (ed < 3) {
            col.fill(h + 1, NightCity.HEIGHT - 1, B.BARRIER);
            return;
        }
        if (ed < 4 && (Math.floorMod(x, 20) == 0 || Math.floorMod(z, 20) == 0)) {
            col.fill(h + 1, h + 14, B.L_PURPLE); col.set(h + 15, B.L_WARM);
            return;
        }

        if (ringRoad != null) { ringDecor(col, x, z, h, ringD, ringRoad); return; }
        if (rd != null) roadDecor(col, x, z, px, pz, h, ry, p, rd, onRoad);
        NightCityVillage.decorate(col, x, z, h);
        if (onRoad || ed < 30) return;
        boolean free = !NightCityVillage.occupied(px, pz) && (rd == null || p.dmin > 14);
        if (!free) return;
        if ((top == B.GRASS || top == B.PODZOL || top == B.MOSS) && !path) {
            trees(col, x, z, h);
            bushes(col, x, z, h);
        }
        if (top != B.SAND && top != B.SNOW && !path) boulders(col, x, z, h);
        if (h > WATER_Y + 1 && (rd == null || p.dmin > 12)) crystals(col, x, z, h);
        if ((top == B.GRASS || top == B.MOSS || top == B.PODZOL) && col.a[h + 1] == B.AIR) plants(col, x, z, h, top, px, pz, forest);
    }

    private static B roadTile(Probe p, Road r) {
        double a = p.dmin;
        B road = r.circuit ? B.GRAY : B.ASPHALT;
        if (a > 4.5) {
            if (p.turn) return Math.floorMod((int) Math.floor(p.s / 2), 2) == 0 ? B.RED_CONC : B.LINE_WHITE;
            return B.LINE_WHITE;
        }
        if (!r.circuit && a <= 0.7 && Math.floorMod((int) Math.floor(p.s), 10) < 6) return B.LINE_YELLOW;
        for (int g = 0; g < r.gates.length; g++)
            if (r.checker[g] && p.s >= r.gates[g] && p.s < r.gates[g] + 2)
                return (((int) Math.floor(p.t) + (int) Math.floor(p.s)) & 1) == 0 ? B.WHITE : B.BLACK;
        return road;
    }

    /** Ограждения на насыпях, фонари, маркеры поворотов и арки ворот */
    private static void roadDecor(Col col, int x, int z, double px, double pz, int h, int ry, Probe p, Road r, boolean onRoad) {
        double d = p.dmin;
        B light = NightCity.ACC_LIGHT[r.acc];
        // ворота: стойки по краям и неоновая перемычка над полотном
        for (int g = 0; g < r.gates.length; g++) {
            if (p.s < r.gates[g] || p.s >= r.gates[g] + 2 || d > 7.5) continue;
            if (d >= 6.0) {
                if (h + 1 <= ry + 8) col.fill(h + 1, ry + 8, B.POLE);
                col.set(ry + 9, light); col.set(ry + 10, light);
            } else {
                col.set(ry + 9, NightCity.ACC_GLASS[r.acc]); col.set(ry + 10, light);
            }
            return;
        }
        if (onRoad) return;
        // ограждение там, где дорога идёт по насыпи
        if (d > HW + 1.0 && d <= HW + 2.0 && terrain(px, pz) < p.roadY - 2.5) {
            col.set(h + 1, B.POLE);
            if (p.turn && Math.floorMod((int) Math.floor(p.s), 3) == 0) col.set(h + 2, B.L_ORANGE);
            return;
        }
        if (d > HW + 2.0 && d <= HW + 3.0 && h <= ry + 3) {
            // фонарь через каждые 28 блоков, стороны чередуются
            int k = (int) Math.floor(p.s / 28);
            if (p.s % 28 < 1.5 && (Math.floorMod(k, 2) == 0) == (p.t > 0)) {
                if (h + 1 <= ry + 4) col.fill(h + 1, ry + 4, B.POLE);
                col.set(ry + 5, B.L_WARM); col.set(ry + 6, light);
                return;
            }
            // маркеры на внешней стороне поворота
            if (p.turn && p.t * p.curv < 0 && Math.floorMod((int) Math.floor(p.s), 4) == 0) {
                col.set(h + 1, B.POLE); col.set(h + 2, B.L_ORANGE);
            }
        }
    }

    private static void ringDecor(Col col, int x, int z, int h, double d, Road r) {
        B light = NightCity.ACC_LIGHT[r.acc];
        if (d <= 0.9) { col.fill(h + 1, h + 36, light); col.set(h + 37, B.L_WARM); }
        else if (d <= 2.2) col.fill(h + 1, h + 3, B.QUARTZ);
        else if (d > 6.5 && d <= 7.4) {
            col.fill(h + 1, h + 2, B.POLE);
            if (Math.floorMod(x + z, 3) == 0) col.set(h + 3, B.L_WARM);
        }
    }

    // ---------------------------------------------------------------- деревья и кристаллы
    private static void trees(Col col, int x, int z, int h) {
        for (int gx = Math.floorDiv(x - 3, 9); gx <= Math.floorDiv(x + 3, 9); gx++)
            for (int gz = Math.floorDiv(z - 3, 9); gz <= Math.floorDiv(z + 3, 9); gz++) {
                int th = NightCity.h(gx, gz, 777);
                int tx = gx * 9 + NightCity.r(th >> 9, 9), tz = gz * 9 + NightCity.r(th >> 14, 9);
                int ddx = x - tx, ddz = z - tz;
                if (Math.abs(ddx) > 3 || Math.abs(ddz) > 3) continue;
                double fo = forest(tx + 0.5, tz + 0.5), me = meadow(tx + 0.5, tz + 0.5);
                double chance = 30 + 58 * fo - 22 * me;
                if (NightCity.r(th >> 4, 100) >= chance) continue;
                if (NightCityVillage.occupied(tx + 0.5, tz + 0.5)) continue;
                Probe q = new Probe();
                int hb = roundH(height(tx + 0.5, tz + 0.5, q));
                int e = hb - G;
                if (hb <= WATER_Y + 2 || e > 46 || (q.road != null && q.dmin <= 14) || slope(tx + 0.5, tz + 0.5) > 0.7) continue;
                if (cityDist(tx + 0.5, tz + 0.5) < 14 || edgeDist(tx + 0.5, tz + 0.5) < 40) continue;
                if (NightCityVillage.pathEdge(tx + 0.5, tz + 0.5) < 2.5) continue;
                if (fo > 0.35 && NightCity.r(th >> 26, 100) < 60) {
                    // ёлка с неоновой звездой на макушке
                    int H = 8 + NightCity.r(th >> 19, 5);
                    if (ddx == 0 && ddz == 0) col.fill(h + 1, hb + H, B.SPRUCE_LOG);
                    for (int dy = 3; dy <= H + 1; dy++) {
                        int rr = (H + 1 - dy) * 3 / (H - 2);
                        if (dy == H + 1) { if (ddx == 0 && ddz == 0) col.set(hb + dy, NightCity.ACC_LIGHT[NightCity.r(th >> 12, 6)]); }
                        else if (ddx * ddx + ddz * ddz <= rr * rr + (rr > 0 ? 1 : 0) && !(ddx == 0 && ddz == 0)) col.set(hb + dy, B.SPRUCE_LEAVES);
                    }
                    return;
                }
                int trunk = 4 + NightCity.r(th >> 19, 4);
                // крона привязана к высоте основания дерева, ствол — до уровня земли в этой колонке
                if (ddx == 0 && ddz == 0) col.fill(h + 1, hb + trunk, B.LOG);
                for (int dy = -2; dy <= 2; dy++) {
                    int rad2 = dy == 2 ? 2 : dy == -2 ? 3 : 7;
                    if (ddx * ddx + ddz * ddz + dy * dy * 2 <= rad2 + 3 && !(ddx == 0 && ddz == 0 && dy < 0))
                        col.set(hb + trunk + dy + 1, B.LEAVES);
                }
                if (ddx == 0 && ddz == 0) col.set(hb + trunk + 1, B.LEAVES);
                if (ddx == 1 && ddz == 1) col.set(hb + trunk, B.L_MAGENTA);
                return;
            }
    }

    /** Цветущие кусты */
    private static void bushes(Col col, int x, int z, int h) {
        if (col.a[h + 1] != B.AIR) return;
        for (int gx = Math.floorDiv(x - 1, 11); gx <= Math.floorDiv(x + 1, 11); gx++)
            for (int gz = Math.floorDiv(z - 1, 11); gz <= Math.floorDiv(z + 1, 11); gz++) {
                int bh = NightCity.h(gx, gz, 888);
                int bx = gx * 11 + NightCity.r(bh >> 9, 11), bz = gz * 11 + NightCity.r(bh >> 14, 11);
                int ddx = x - bx, ddz = z - bz;
                if (Math.abs(ddx) > 1 || Math.abs(ddz) > 1) continue;
                double fo = forest(bx + 0.5, bz + 0.5), me = meadow(bx + 0.5, bz + 0.5);
                if (NightCity.r(bh >> 4, 100) >= 10 + 38 * fo - 4 * me) continue;
                if (NightCityVillage.occupied(bx + 0.5, bz + 0.5) || NightCityVillage.pathEdge(bx + 0.5, bz + 0.5) < 2.0) continue;
                Probe q = new Probe();
                int hb = roundH(height(bx + 0.5, bz + 0.5, q));
                if (hb <= WATER_Y + 2 || hb - G > 44 || (q.road != null && q.dmin <= 12)) continue;
                col.set(h + 1, B.BUSH);
                if (ddx == 0 && ddz == 0) col.set(h + 2, B.BUSH);
                return;
            }
    }

    /** Валуны: полузарытые каменные глыбы */
    private static void boulders(Col col, int x, int z, int h) {
        for (int gx = Math.floorDiv(x - 3, 37); gx <= Math.floorDiv(x + 3, 37); gx++)
            for (int gz = Math.floorDiv(z - 3, 37); gz <= Math.floorDiv(z + 3, 37); gz++) {
                int bh = NightCity.h(gx, gz, 999);
                if (NightCity.r(bh >> 4, 100) >= 24) continue;
                int bx = gx * 37 + NightCity.r(bh >> 9, 37), bz = gz * 37 + NightCity.r(bh >> 15, 37);
                int ddx = x - bx, ddz = z - bz;
                if (Math.abs(ddx) > 3 || Math.abs(ddz) > 3) continue;
                if (NightCityVillage.occupied(bx + 0.5, bz + 0.5)) continue;
                Probe q = new Probe();
                int hb = roundH(height(bx + 0.5, bz + 0.5, q));
                if (hb <= WATER_Y + 2 || hb - G > 50 || (q.road != null && q.dmin <= 14) || NightCityVillage.pathEdge(bx + 0.5, bz + 0.5) < 3) continue;
                double rb = 1.5 + NightCity.r(bh >> 21, 14) / 10.0;
                B b = NightCity.r(bh >> 25, 3) == 0 ? B.MOSSY_COBBLE : NightCity.r(bh >> 25, 3) == 1 ? B.ANDESITE : B.STONE;
                for (int dy = 0; dy <= 3; dy++) {
                    double d2 = ddx * ddx + ddz * ddz + dy * dy * 1.8;
                    if (d2 <= rb * rb) col.set(hb + dy, b);
                }
                return;
            }
    }

    /** Трава, цветы, папоротники и грибы — мелочи, оживляющие землю */
    private static void plants(Col col, int x, int z, int h, B top, double px, double pz, double forest) {
        double me = meadow(px, pz);
        int r = NightCity.r(NightCity.h(x, z, 811), 1000);
        double grass = 70 + 190 * me - 40 * forest, flower = 8 + 150 * me, fern = 140 * forest, shroom = 14 * forest;
        B b = null;
        if (r < flower) {
            B[] fl = {B.FL_POPPY, B.FL_ORCHID, B.FL_ALLIUM, B.FL_AZURE, B.FL_CORN, B.FL_TULIP, B.FL_DAISY};
            b = fl[NightCity.r(NightCity.h(x, z, 812), fl.length)];
        } else if (r < flower + grass) b = B.GRASS_T;
        else if (r < flower + grass + fern) b = B.FERN;
        else if (r < flower + grass + fern + shroom) b = NightCity.r(NightCity.h(x, z, 813), 2) == 0 ? B.SHROOM_R : B.SHROOM_B;
        if (b != null) col.set(h + 1, b);
    }

    /** Светящиеся «кристаллы» на холмах — чтобы окрестности было видно ночью */
    private static void crystals(Col col, int x, int z, int h) {
        for (int gx = Math.floorDiv(x - 1, 24); gx <= Math.floorDiv(x + 1, 24); gx++)
            for (int gz = Math.floorDiv(z - 1, 24); gz <= Math.floorDiv(z + 1, 24); gz++) {
                int ch = NightCity.h(gx, gz, 4242);
                if (NightCity.r(ch >> 4, 100) >= 42) continue;
                int cx = gx * 24 + 2 + NightCity.r(ch >> 9, 20), cz = gz * 24 + 2 + NightCity.r(ch >> 14, 20);
                int ddx = x - cx, ddz = z - cz;
                if (Math.abs(ddx) > 1 || Math.abs(ddz) > 1 || (ddx != 0 && ddz != 0)) continue;
                Probe q = new Probe();
                int hb = roundH(height(cx + 0.5, cz + 0.5, q));
                if (hb <= WATER_Y + 2 || (q.road != null && q.dmin <= 12) || cityDist(cx + 0.5, cz + 0.5) < 12) continue;
                if (NightCityVillage.occupied(cx + 0.5, cz + 0.5) || NightCityVillage.pathEdge(cx + 0.5, cz + 0.5) < 3 || edgeDist(cx + 0.5, cz + 0.5) < 40) continue;
                B b = NightCity.ACC_LIGHT[NightCity.r(ch >> 20, 6)];
                int height = ddx == 0 && ddz == 0 ? 9 + NightCity.r(ch >> 24, 9) : 3 + NightCity.r(ch >> 26, 4);
                col.fill(h + 1, h + height, b);
                return;
            }
    }
}
