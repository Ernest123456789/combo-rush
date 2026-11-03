package com.derevna230.comborush.universe;

import com.derevna230.comborush.universe.NightCity.B;
import com.derevna230.comborush.universe.NightCity.Col;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Загородная жизнь «Ночного города»: посёлки с домиками и колодцем, грунтовые тропы между ними,
 * лагеря и тайники (зарытые сундуки и ниши в горах). Всё размещается один раз при загрузке класса
 * по детерминированным правилам, а потом колонки только спрашивают «что здесь».
 */
public final class NightCityVillage {
    private NightCityVillage() {}

    static final int G = NightCity.GROUND;
    static final int HOUSE = 0, HUB = 1, CAMP = 2, BURIED = 3, NICHE = 4;

    /** Объект на местности: дом, площадь посёлка, лагерь, тайник */
    static final class Feat {
        final int kind, cx, cz, y0, rot, hw, style, acc, rad;
        final boolean cellar;
        Feat(int kind, int cx, int cz, int y0, int rot, int hw, int style, int acc, int rad, boolean cellar) {
            this.kind = kind; this.cx = cx; this.cz = cz; this.y0 = y0; this.rot = rot; this.hw = hw;
            this.style = style; this.acc = acc; this.rad = rad; this.cellar = cellar;
        }
    }

    public static final List<double[]> HUBS = new ArrayList<>();      // {x, z, y}
    static final List<Feat> FEATS = new ArrayList<>();
    static final List<double[]> PADS = new ArrayList<>();             // {x, z, y, r}: ровные площадки под постройки
    private static final List<double[]> SEGS = new ArrayList<>();     // {x0, z0, x1, z1, полуширина}
    private static final int CELL = 48;
    private static final Map<Long, int[]> FEAT_GRID = new HashMap<>(), PAD_GRID = new HashMap<>(), SEG_GRID = new HashMap<>();
    public static int pathSegments, houses, camps, stashes;

    private static long key(int cx, int cz) { return ((long) cx << 32) ^ (cz & 0xffffffffL); }

    static {
        try { build(); } catch (RuntimeException e) { throw new IllegalStateException("NightCityVillage.build", e); }
    }

    // ---------------------------------------------------------------- размещение
    private static boolean site(double x, double z, double rad, double minRoad, double minFromHubs) {
        if (NightCityWild.edgeDist(x, z) < 200 || NightCityWild.cityDist(x, z) < 110) return false;
        if (NightCityWild.roadDist(x, z) < minRoad) return false;
        double t0 = NightCityWild.terrain(x, z), e = t0 - G;
        if (e < 1 || e > 34) return false;
        double mn = t0, mx = t0;
        for (int k = 0; k < 12; k++) {
            double a = k * Math.PI / 6, t = NightCityWild.terrain(x + Math.cos(a) * rad, z + Math.sin(a) * rad);
            if (t < NightCityWild.WATER_Y + 3) return false;
            mn = Math.min(mn, t); mx = Math.max(mx, t);
        }
        if (mx - mn > 12) return false;
        for (double[] h : HUBS) if (Math.hypot(h[0] - x, h[1] - z) < minFromHubs) return false;
        return true;
    }

    private static void build() {
        final int cell = 320, n = 6;
        final double x0 = NightCityWild.WORLD_C - NightCityWild.WORLD_HALF, z0 = x0;
        // --- посёлки
        for (int ci = 0; ci < n; ci++) for (int cj = 0; cj < n; cj++) {
            int hs = NightCity.h(ci, cj, 5151);
            if (NightCity.r(hs >> 5, 100) >= 96) continue;
            for (int t = 0; t < 30; t++) {
                int ht = NightCity.h(ci * 31 + t, cj * 17 + t, 5252);
                double x = Math.floor(x0 + ci * cell + 40 + NightCity.r(ht >> 3, cell - 80)),
                       z = Math.floor(z0 + cj * cell + 40 + NightCity.r(ht >> 13, cell - 80));
                if (!site(x, z, 34, 70, 130)) continue;
                makeHamlet((int) x, (int) z, hs);
                break;
            }
        }
        // --- лагеря
        int hubs = HUBS.size();
        for (int ci = 0; ci < n + 1; ci++) for (int cj = 0; cj < n + 1; cj++) {
            int hs = NightCity.h(ci, cj, 6161);
            if (NightCity.r(hs >> 5, 100) >= 60) continue;
            for (int t = 0; t < 10; t++) {
                int ht = NightCity.h(ci * 13 + t, cj * 29 + t, 6262);
                double x = Math.floor(x0 + ci * 270 + 30 + NightCity.r(ht >> 3, 210)), z = Math.floor(z0 + cj * 270 + 30 + NightCity.r(ht >> 13, 210));
                if (!site(x, z, 11, 60, 110) || tooCloseToFeat(x, z, 90)) continue;
                int y0 = (int) Math.round(NightCityWild.terrain(x + 0.5, z + 0.5));
                FEATS.add(new Feat(CAMP, (int) x, (int) z, y0, NightCity.r(ht >> 20, 4), 0, 0, NightCity.r(ht >> 24, 6), 12, false));
                PADS.add(new double[]{x + 0.5, z + 0.5, y0, 11.0});
                camps++;
                break;
            }
        }
        // --- тайники в горах: самая высокая точка клетки 170×170
        for (int ci = 0; ci < 12; ci++) for (int cj = 0; cj < 12; cj++) {
            double bx = 0, bz = 0, be = 0;
            for (double x = x0 + ci * 170 + 10; x < x0 + ci * 170 + 160; x += 6)
                for (double z = z0 + cj * 170 + 10; z < z0 + cj * 170 + 160; z += 6) {
                    if (NightCityWild.edgeDist(x, z) < 190) continue;
                    double e = NightCityWild.terrain(x, z) - G;
                    if (e > be) { be = e; bx = x; bz = z; }
                }
            if (be < 40 || NightCityWild.roadDist(bx, bz) < 40) continue;
            int cx = (int) Math.floor(bx), cz = (int) Math.floor(bz);
            FEATS.add(new Feat(NICHE, cx, cz, (int) Math.round(NightCityWild.terrain(cx + 0.5, cz + 0.5)), 0, 0, 0, 0, 3, false));
            stashes++;
        }
        // --- тропы: каждый посёлок связан с двумя ближайшими и с ближайшей асфальтовой дорогой
        int nh = HUBS.size();
        List<int[]> edges = new ArrayList<>();
        for (int i = 0; i < nh; i++) {
            int a = -1, b = -1; double da = 1e18, db = 1e18;
            for (int j = 0; j < nh; j++) {
                if (j == i) continue;
                double d = Math.hypot(HUBS.get(i)[0] - HUBS.get(j)[0], HUBS.get(i)[1] - HUBS.get(j)[1]);
                if (d < da) { db = da; b = a; da = d; a = j; } else if (d < db) { db = d; b = j; }
            }
            for (int j : new int[]{a, b}) {
                if (j < 0) continue;
                int lo = Math.min(i, j), hi = Math.max(i, j);
                boolean dup = false;
                for (int[] e : edges) if (e[0] == lo && e[1] == hi) dup = true;
                if (!dup) edges.add(new int[]{lo, hi});
            }
        }
        for (int[] e : edges) addPath(HUBS.get(e[0])[0], HUBS.get(e[0])[1], HUBS.get(e[1])[0], HUBS.get(e[1])[1], 2.0, e[0] * 97 + e[1]);
        for (int i = 0; i < nh; i++) {
            double[] hb = HUBS.get(i);
            double[] rp = NightCityWild.nearestRoadPoint(hb[0], hb[1]);
            if (rp != null && Math.hypot(rp[0] - hb[0], rp[1] - hb[1]) < 520) addPath(hb[0], hb[1], rp[0], rp[1], 2.0, i * 53 + 7);
        }
        // --- индексы по клеткам
        for (int i = 0; i < FEATS.size(); i++) index(FEAT_GRID, i, FEATS.get(i).cx - FEATS.get(i).rad - 4, FEATS.get(i).cz - FEATS.get(i).rad - 4,
            FEATS.get(i).cx + FEATS.get(i).rad + 4, FEATS.get(i).cz + FEATS.get(i).rad + 4);
        for (int i = 0; i < PADS.size(); i++) {
            double[] p = PADS.get(i);
            index(PAD_GRID, i, (int) Math.floor(p[0] - p[3] - 6), (int) Math.floor(p[1] - p[3] - 6), (int) Math.ceil(p[0] + p[3] + 6), (int) Math.ceil(p[1] + p[3] + 6));
        }
        for (int i = 0; i < SEGS.size(); i++) {
            double[] s = SEGS.get(i);
            double r = s[4] + 3;
            index(SEG_GRID, i, (int) Math.floor(Math.min(s[0], s[2]) - r), (int) Math.floor(Math.min(s[1], s[3]) - r),
                (int) Math.ceil(Math.max(s[0], s[2]) + r), (int) Math.ceil(Math.max(s[1], s[3]) + r));
        }
        pathSegments = SEGS.size();
    }

    private static boolean tooCloseToFeat(double x, double z, double d) {
        for (Feat f : FEATS) if (Math.hypot(f.cx - x, f.cz - z) < d) return true;
        return false;
    }

    private static void index(Map<Long, int[]> grid, int id, int x0, int z0, int x1, int z1) {
        for (int cx = Math.floorDiv(x0, CELL); cx <= Math.floorDiv(x1, CELL); cx++)
            for (int cz = Math.floorDiv(z0, CELL); cz <= Math.floorDiv(z1, CELL); cz++) {
                int[] old = grid.get(key(cx, cz));
                int[] nu = old == null ? new int[1] : java.util.Arrays.copyOf(old, old.length + 1);
                nu[nu.length - 1] = id;
                grid.put(key(cx, cz), nu);
            }
    }

    private static void makeHamlet(int hx, int hz, int hs) {
        int y0 = (int) Math.round(NightCityWild.terrain(hx + 0.5, hz + 0.5));
        HUBS.add(new double[]{hx + 0.5, hz + 0.5, y0});
        FEATS.add(new Feat(HUB, hx, hz, y0, 0, 0, 0, NightCity.r(hs >> 11, 6), 9, false));
        PADS.add(new double[]{hx + 0.5, hz + 0.5, y0, 9.0});
        int count = 3 + NightCity.r(hs >> 9, 4);
        double base = NightCity.r(hs >> 14, 628) / 100.0;
        for (int k = 0; k < count; k++) {
            int hh = NightCity.h(hx, hz, 7000 + k);
            double ang = base + k * 2 * Math.PI / count + (NightCity.r(hh >> 3, 50) - 25) / 100.0;
            double rr = 22 + NightCity.r(hh >> 9, 9);
            int cx = (int) Math.floor(hx + 0.5 + Math.cos(ang) * rr), cz = (int) Math.floor(hz + 0.5 + Math.sin(ang) * rr);
            double ct = NightCityWild.terrain(cx + 0.5, cz + 0.5);
            double mn = ct, mx = ct;
            boolean ok = ct - G >= 0.5;
            for (int q = 0; q < 8 && ok; q++) {
                double a = q * Math.PI / 4, t = NightCityWild.terrain(cx + 0.5 + Math.cos(a) * 8, cz + 0.5 + Math.sin(a) * 8);
                if (t < NightCityWild.WATER_Y + 2) ok = false;
                mn = Math.min(mn, t); mx = Math.max(mx, t);
            }
            if (!ok || mx - mn > 6 || NightCityWild.roadDist(cx, cz) < 50) continue;
            // фасад смотрит на площадь
            double dx = hx + 0.5 - (cx + 0.5), dz = hz + 0.5 - (cz + 0.5);
            int rot = Math.abs(dz) >= Math.abs(dx) ? (dz < 0 ? 0 : 2) : (dx > 0 ? 1 : 3);
            int hw = 3 + NightCity.r(hh >> 15, 3);
            int style = NightCity.r(hh >> 18, 3), acc = NightCity.r(hh >> 21, 6);
            boolean cellar = NightCity.r(hh >> 24, 100) < 40;
            int hy = (int) Math.round(ct);
            FEATS.add(new Feat(HOUSE, cx, cz, hy, rot, hw, style, acc, 9, cellar));
            PADS.add(new double[]{cx + 0.5, cz + 0.5, hy, 8.0});
            houses++;
            // тропинка от двери к площади (дверь на локальной стороне −z)
            double[] door = worldOf(rot, 0, -5);
            double[] hubDoor = {cx + 0.5 + door[0], cz + 0.5 + door[1]};
            SEGS.add(new double[]{hubDoor[0], hubDoor[1], hx + 0.5, hz + 0.5, 1.3});
        }
        // бесхозный зарытый сундук неподалёку от посёлка, помеченный пятном
        for (int t = 0; t < 12; t++) {
            int hh = NightCity.h(hx, hz, 8000 + t);
            double ang = NightCity.r(hh >> 3, 628) / 100.0, rr = 48 + NightCity.r(hh >> 12, 26);
            int cx = (int) Math.floor(hx + Math.cos(ang) * rr), cz = (int) Math.floor(hz + Math.sin(ang) * rr);
            double ct = NightCityWild.terrain(cx + 0.5, cz + 0.5);
            if (ct - G < 2 || ct - G > 40 || NightCityWild.roadDist(cx, cz) < 36 || NightCityWild.slope(cx + 0.5, cz + 0.5) > 0.5) continue;
            FEATS.add(new Feat(BURIED, cx, cz, (int) Math.round(ct), 0, 0, 0, 0, 2, false));
            stashes++;
            break;
        }
    }

    /** Локальная точка дома → смещение в мире */
    private static double[] worldOf(int rot, double lx, double lz) {
        return switch (rot) {
            case 0 -> new double[]{lx, lz};
            case 1 -> new double[]{-lz, lx};
            case 2 -> new double[]{-lx, -lz};
            default -> new double[]{lz, -lx};
        };
    }

    private static void addPath(double ax, double az, double bx, double bz, double hw, int seed) {
        double len = Math.hypot(bx - ax, bz - az);
        if (len < 20) return;
        for (int attempt = 0; attempt < 6; attempt++) {
            int n = Math.max(3, (int) (len / 5));
            double dx = (bx - ax) / len, dz = (bz - az) / len, px = -dz, pz = dx;
            double amp = (8 + len * 0.035) * (attempt % 2 == 0 ? 1 : -1) * (1 + attempt * 0.15);
            double[][] pts = new double[n + 1][2];
            for (int i = 0; i <= n; i++) {
                double t = (double) i / n, env = Math.sin(Math.PI * t);
                double off = amp * env * (0.6 * NightCityWild.vn(i * 0.13 + seed * 0.7, seed * 0.31 + attempt, 61) + 0.4 * Math.sin(t * 9 + seed));
                pts[i][0] = ax + dx * len * t + px * off; pts[i][1] = az + dz * len * t + pz * off;
            }
            int bad = 0, total = 0;
            for (int i = 0; i < pts.length - 1; i++) {
                for (int k = 0; k < 4; k++) {
                    double x = pts[i][0] + (pts[i + 1][0] - pts[i][0]) * k / 4, z = pts[i][1] + (pts[i + 1][1] - pts[i][1]) * k / 4;
                    total++;
                    if (NightCityWild.terrain(x, z) < NightCityWild.WATER_Y + 2 || NightCityWild.slope(x, z) > 1.0 || NightCityWild.edgeDist(x, z) < 160) bad++;
                }
            }
            if (bad > Math.max(2, total / 40)) continue;
            for (int i = 0; i < pts.length - 1; i++) SEGS.add(new double[]{pts[i][0], pts[i][1], pts[i + 1][0], pts[i + 1][1], hw});
            return;
        }
    }

    // ---------------------------------------------------------------- запросы
    /** Подвинуть высоту к площадкам под постройки */
    static double pad(double px, double pz, double h) {
        int[] ids = PAD_GRID.get(key(Math.floorDiv((int) Math.floor(px), CELL), Math.floorDiv((int) Math.floor(pz), CELL)));
        if (ids == null) return h;
        for (int id : ids) {
            double[] p = PADS.get(id);
            double d = Math.hypot(px - p[0], pz - p[1]);
            double c = 1 - NightCityWild.smooth((d - p[3]) / 5.0);
            if (c > 0) h += (p[2] - h) * c;
        }
        return h;
    }

    /** Расстояние до края ближайшей тропы (отрицательное — внутри); 99 — троп рядом нет */
    static double pathEdge(double px, double pz) {
        int[] ids = SEG_GRID.get(key(Math.floorDiv((int) Math.floor(px), CELL), Math.floorDiv((int) Math.floor(pz), CELL)));
        if (ids == null) return 99;
        double best = 99;
        for (int id : ids) {
            double[] s = SEGS.get(id);
            double ex = s[2] - s[0], ez = s[3] - s[1], l2 = ex * ex + ez * ez;
            double u = l2 == 0 ? 0 : ((px - s[0]) * ex + (pz - s[1]) * ez) / l2;
            u = u < 0 ? 0 : u > 1 ? 1 : u;
            double d = Math.hypot(px - (s[0] + ex * u), pz - (s[1] + ez * u)) - s[4];
            if (d < best) best = d;
        }
        return best;
    }

    /** Рядом постройка, лагерь или тайник: деревья, цветы и кристаллы здесь не ставим */
    static boolean occupied(double px, double pz) {
        int[] ids = FEAT_GRID.get(key(Math.floorDiv((int) Math.floor(px), CELL), Math.floorDiv((int) Math.floor(pz), CELL)));
        if (ids == null) return false;
        for (int id : ids) {
            Feat f = FEATS.get(id);
            if (Math.abs(px - f.cx) <= f.rad + 4 && Math.abs(pz - f.cz) <= f.rad + 4) return true;
        }
        return false;
    }

    /** Достройка колонки: дома, колодцы, лагеря, тайники */
    static void decorate(Col c, int x, int z, int h) {
        int[] ids = FEAT_GRID.get(key(Math.floorDiv(x, CELL), Math.floorDiv(z, CELL)));
        if (ids == null) return;
        for (int id : ids) {
            Feat f = FEATS.get(id);
            int dx = x - f.cx, dz = z - f.cz;
            if (Math.abs(dx) > f.rad + 1 || Math.abs(dz) > f.rad + 1) continue;
            switch (f.kind) {
                case HOUSE -> house(c, f, dx, dz);
                case HUB -> hub(c, f, dx, dz);
                case CAMP -> camp(c, f, dx, dz);
                case BURIED -> buried(c, f, dx, dz, h);
                default -> niche(c, f, dx, dz, h);
            }
        }
    }

    private static void put(Col c, Feat f, int ly, B b) { c.set(f.y0 + ly, b); }

    private static int dirIdx(int rot, int ldx, int ldz) {
        int wx, wz;
        switch (rot) {
            case 0 -> { wx = ldx; wz = ldz; }
            case 1 -> { wx = -ldz; wz = ldx; }
            case 2 -> { wx = -ldx; wz = -ldz; }
            default -> { wx = ldz; wz = -ldx; }
        }
        return wz < 0 ? 0 : wx > 0 ? 1 : wz > 0 ? 2 : 3;      // N, E, S, W
    }

    private static B at(B first, int idx) { return B.values()[first.ordinal() + idx]; }

    // ---------------------------------------------------------------- дом
    private static void house(Col c, Feat f, int dx, int dz) {
        int lx, lz;
        switch (f.rot) {
            case 0 -> { lx = dx; lz = dz; }
            case 1 -> { lx = dz; lz = -dx; }
            case 2 -> { lx = -dx; lz = -dz; }
            default -> { lx = -dz; lz = dx; }
        }
        int hw = f.hw, ax = Math.abs(lx), az = Math.abs(lz);
        if (ax > hw + 2 || az > 6) return;
        B wall = at(B.W_A, f.style), post = at(B.POST_A, f.style), floor = at(B.FLOOR_A, f.style), ridge = at(B.RIDGE_A, f.style);
        B glass = NightCity.ACC_GLASS[f.acc];
        boolean inside = ax < hw && az < 3, shell = ax <= hw && az <= 3;
        for (int ly = -1; ly <= 8; ly++) {
            B b = null;
            if (ly == -1) {
                if (f.cellar && lx == -hw + 1 && lz == -2) { c.chest(f.y0 - 1, at(B.CHEST_N, dirIdx(f.rot, 0, 1)), NightCity.T_HIDDEN); }
                continue;
            }
            if (ly == 0) {
                if (shell) b = f.cellar && lx == -hw + 1 && lz == -2 ? at(B.FLOOR_A, (f.style + 1) % 3) : floor;
                else if (lz == -4 && ax <= 1) b = B.COBBLE;
                else if (ax <= hw + 1 && az <= 4 && Math.floorMod(NightCity.h(f.cx + dx, f.cz + dz, 33), 5) == 0) b = B.COARSE;
            } else if (ly <= 3 && shell) {
                if (ax == hw && az == 3) b = post;
                else if (inside) b = interior(c, f, lx, lz, ly, hw);
                else if (lz == -3 && lx == 0 && ly <= 2) b = B.AIR;
                else if (ly == 2 && ((lz == -3 && ax == 2) || (lz == 3 && (ax == 2 || lx == 0)) || (ax == hw && lz == 0))) b = glass;
                else b = wall;
            } else if (ly == 4) {
                if (az == 3 && ax <= hw + 1) b = at(B.ROOF_A_N, f.style * 4 + dirIdx(f.rot, 0, lz < 0 ? 1 : -1));
                else if (az < 3 && ax < hw) b = floor;
                else if (az < 3 && ax == hw) b = wall;
            } else if (ly == 5) {
                if (az == 2 && ax <= hw + 1) b = at(B.ROOF_A_N, f.style * 4 + dirIdx(f.rot, 0, lz < 0 ? 1 : -1));
                else if (az < 2 && ax == hw) b = wall;
            } else if (ly == 6) {
                if (az == 1 && ax <= hw + 1) b = at(B.ROOF_A_N, f.style * 4 + dirIdx(f.rot, 0, lz < 0 ? 1 : -1));
                else if (az == 0 && ax == hw) b = wall;
            } else if (ly == 7) {
                if (az == 0 && ax <= hw + 1) b = ridge;
            }
            // труба с дымящим костром наверху, фонари у двери, сено и тыквы во дворе
            if (b == null) {
                if (lx == hw - 2 && lz == 4) { if (ly >= 1 && ly <= 7) b = f.style == 1 ? B.COBBLE : B.BRICKS; else if (ly == 8) b = B.CAMPFIRE; }
                else if (lz == -4 && ax == 2) { if (ly == 1) b = B.FENCE; else if (ly == 2) b = B.LANTERN_S; }
                else if (lx == -hw - 1) {
                    if (lz == -1 && ly == 1) b = B.HAY;
                    else if (lz == 0 && ly <= 2 && ly >= 1) b = B.HAY;
                    else if (lz == 1 && ly == 1) b = B.PUMPKIN;
                }
            }
            if (b == null) continue;
            put(c, f, ly, b);
        }
    }

    /** Обстановка внутри: сундук у задней стены, верстак, бочка, книжная полка, фонарь под потолком */
    private static B interior(Col c, Feat f, int lx, int lz, int ly, int hw) {
        if (ly == 1) {
            if (lx == hw - 1 && lz == 2) { c.chest(f.y0 + 1, at(B.CHEST_N, dirIdx(f.rot, 0, -1)), NightCity.T_HOUSE); return null; }
            if (lx == -hw + 1 && lz == 2) return B.CRAFTING;
            if (lx == -hw + 1 && lz == -2) return B.BARREL;
            if (lx == hw - 1 && lz == -2 && hw >= 4) return B.BARREL;
        }
        if ((ly == 1 || ly == 2) && lz == 2 && lx == 0) return B.BOOKSHELF;
        if (ly == 3 && lx == 0 && lz == 0) return B.LANTERN_H;
        return B.AIR;
    }

    // ---------------------------------------------------------------- площадь посёлка: колодец, фонари
    private static void hub(Col c, Feat f, int dx, int dz) {
        int ax = Math.abs(dx), az = Math.abs(dz);
        if (ax <= 1 && az <= 1) {
            if (dx == 0 && dz == 0) { put(c, f, -1, B.WATER); put(c, f, 0, B.WATER); put(c, f, 3, B.LANTERN_H); }
            else put(c, f, 0, B.COBBLE);
            if (ax == 1 && az == 1) { put(c, f, 1, B.FENCE); put(c, f, 2, B.FENCE); put(c, f, 3, B.FENCE); }
            else if (ax + az == 1) put(c, f, 1, B.COBBLE);
            put(c, f, 4, at(B.RIDGE_A, 0));
            return;
        }
        if ((dx == 4 && dz == 4) || (dx == -4 && dz == -4)) {
            put(c, f, 1, B.FENCE); put(c, f, 2, B.FENCE); put(c, f, 3, B.FENCE); put(c, f, 4, B.LANTERN_S);
        }
    }

    // ---------------------------------------------------------------- лагерь
    private static void camp(Col c, Feat f, int dx, int dz) {
        int lx, lz;
        switch (f.rot) {
            case 0 -> { lx = dx; lz = dz; }
            case 1 -> { lx = dz; lz = -dx; }
            case 2 -> { lx = -dx; lz = -dz; }
            default -> { lx = -dz; lz = dx; }
        }
        double d = Math.hypot(lx, lz);
        if (d <= 3.6) put(c, f, 0, (lx + lz) % 2 == 0 ? B.PODZOL : B.COARSE);
        if (lx == 0 && lz == 0) put(c, f, 1, B.CAMPFIRE);
        if ((Math.abs(lx) == 3 && lz == 0) || (lx == 0 && Math.abs(lz) == 3)) put(c, f, 1, B.SPRUCE_LOG);
        // палатка вдоль оси x на расстоянии 6..10
        if (lx >= 6 && lx <= 10 && Math.abs(lz) <= 1) {
            B tent = NightCity.ACC_CONC[f.acc];
            put(c, f, 0, B.PODZOL);
            if (Math.abs(lz) == 1) { put(c, f, 1, tent); }
            else {
                if (lx == 10) put(c, f, 1, tent);
                else if (lx == 6) put(c, f, 1, B.AIR);
                else if (lx == 8) c.chest(f.y0 + 1, at(B.CHEST_N, dirIdx(f.rot, -1, 0)), NightCity.T_CAMP);
                else if (lx == 9) put(c, f, 1, B.LANTERN_S);
                put(c, f, 2, tent);
            }
            if (Math.abs(lz) == 1) put(c, f, 2, B.AIR);
        }
    }

    // ---------------------------------------------------------------- тайники
    /** Зарытый сундук: пятно «сухой земли» с замшелым булыжником в центре, сундук на два блока ниже */
    private static void buried(Col c, Feat f, int dx, int dz, int h) {
        if (Math.abs(dx) > 1 || Math.abs(dz) > 1) return;
        c.set(h, dx == 0 && dz == 0 ? B.MOSSY_COBBLE : B.COARSE);
        if (dx == 0 && dz == 0) c.chest(f.y0 - 2, at(B.CHEST_N, 0), NightCity.T_HIDDEN);
    }

    /** Ниша в горе: каверна 3×3×3 на глубине, сверху едва заметное пятно потрескавшихся кирпичей */
    private static void niche(Col c, Feat f, int dx, int dz, int h) {
        if (Math.abs(dx) > 1 || Math.abs(dz) > 1) return;
        c.set(h, dx == 0 && dz == 0 ? B.STONE_BRICKS : B.CRACKED);
        int y = f.y0 - 8;
        c.set(y - 1, B.STONE);
        for (int k = 0; k < 3; k++) c.set(y + k, B.AIR);
        if (dx == 0 && dz == 0) c.chest(y, at(B.CHEST_N, 2), NightCity.T_PEAK);
        else if (Math.abs(dx) == 1 && Math.abs(dz) == 1) c.set(y, B.LANTERN_S);
    }
}
