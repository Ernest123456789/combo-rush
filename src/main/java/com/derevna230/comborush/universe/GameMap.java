package com.derevna230.comborush.universe;

import com.derevna230.comborush.ComboRush;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Карта соревновательного режима: текстовый ресурс {@code data/combo-rush/maps/<id>.map} (генераторы в tools/map_gen).
 * Строки: B — палитра, C — колонка (RLE от нижнего уровня), Z/ZP — зоны (прямоугольник/многоугольник с названием места),
 * SITE — площадки закладки, SPAWN — места появления, HOLD/ENTRY/POST — позиции ботов (по желанию — точка, куда смотреть).
 * Уровень 0 = блок пола на высоте {@link #FLOOR}; игрок стоит на FLOOR + уровень + 1.
 */
public final class GameMap {
    public static final int FLOOR = 64;

    public final String id;
    public final RegistryKey<World> world;
    public String title;
    public int x0, x1, z0, z1;
    private int lv0;
    private BlockState[] palette;
    private final Map<Long, int[]> cols = new HashMap<>();

    public record Site(char id, int x, int z, int r, int level) {}
    /** Точка на карте; lookX/lookZ — куда смотреть, стоя на ней (NaN — неважно) */
    public record Pt(int x, int z, int level, double lookX, double lookZ) {
        public double cx() { return x + 0.5; }
        public double cz() { return z + 0.5; }
        public double y() { return FLOOR + level + 1; }
        public boolean hasLook() { return !Double.isNaN(lookX); }
        public String key() { return x + ":" + z + ":" + level; }
    }
    private record Zone(String name, int[] rect, double[] poly) {}

    private final List<Site> sites = new ArrayList<>();
    private final List<Zone> zones = new ArrayList<>();
    private final List<Pt> spawnT = new ArrayList<>(), spawnCT = new ArrayList<>();
    private final Map<String, List<Pt>> hold = new HashMap<>(), entry = new HashMap<>(), post = new HashMap<>();

    GameMap(String id, RegistryKey<World> world) {
        this.id = id; this.world = world; this.title = id;
        load();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static BlockState parseState(String s) {
        String name = s, props = null;
        int i = s.indexOf('[');
        if (i >= 0) { name = s.substring(0, i); props = s.substring(i + 1, s.length() - 1); }
        Block b = Registries.BLOCK.get(Identifier.of(name));
        BlockState st = b.getDefaultState();
        if (props != null) {
            for (String kv : props.split(",")) {
                String[] p = kv.split("=");
                Property prop = b.getStateManager().getProperty(p[0]);
                if (prop == null) continue;
                java.util.Optional v = prop.parse(p[1]);
                if (v.isPresent()) st = st.with(prop, (Comparable) v.get());
            }
        }
        return st;
    }

    private static Pt pt(String[] p, int from) {
        int x = Integer.parseInt(p[from]), z = Integer.parseInt(p[from + 1]), lv = Integer.parseInt(p[from + 2]);
        double lx = Double.NaN, lz = Double.NaN;
        if (p.length >= from + 5) { lx = Integer.parseInt(p[from + 3]) + 0.5; lz = Integer.parseInt(p[from + 4]) + 0.5; }
        return new Pt(x, z, lv, lx, lz);
    }

    private void load() {
        Map<Integer, BlockState> pal = new HashMap<>();
        int maxId = 0;
        try (InputStream in = GameMap.class.getResourceAsStream("/data/" + ComboRush.MOD_ID + "/maps/" + id + ".map")) {
            if (in == null) throw new IllegalStateException("нет ресурса карты " + id);
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = r.readLine()) != null) {
                if (line.isEmpty() || line.charAt(0) == '#') continue;
                if (line.startsWith("C ")) {
                    String[] p = line.split(" ");
                    int[] rle = new int[(p.length - 3) * 2];
                    for (int i = 3; i < p.length; i++) {
                        int c = p[i].indexOf(':');
                        rle[(i - 3) * 2] = Integer.parseInt(p[i].substring(0, c));
                        rle[(i - 3) * 2 + 1] = Integer.parseInt(p[i].substring(c + 1));
                    }
                    cols.put(key(Integer.parseInt(p[1]), Integer.parseInt(p[2])), rle);
                } else if (line.startsWith("B ")) {
                    String[] p = line.split(" ", 3);
                    int bid = Integer.parseInt(p[1]);
                    pal.put(bid, parseState(p[2]));
                    maxId = Math.max(maxId, bid);
                } else if (line.startsWith("BOUNDS ")) {
                    String[] p = line.split(" ");
                    x0 = Integer.parseInt(p[1]); x1 = Integer.parseInt(p[2]); z0 = Integer.parseInt(p[3]); z1 = Integer.parseInt(p[4]); lv0 = Integer.parseInt(p[5]);
                } else if (line.startsWith("TITLE ")) {
                    title = line.substring(6).trim();
                } else if (line.startsWith("Z ")) {
                    String[] p = line.substring(2).split("\\|");
                    zones.add(new Zone(p[0], new int[]{Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]), Integer.parseInt(p[4])}, null));
                } else if (line.startsWith("ZP ")) {
                    String[] p = line.substring(3).split("\\|");
                    String[] pts = p[2].split(";");
                    double[] poly = new double[pts.length * 2];
                    for (int i = 0; i < pts.length; i++) {
                        String[] xz = pts[i].split(",");
                        poly[i * 2] = Double.parseDouble(xz[0]); poly[i * 2 + 1] = Double.parseDouble(xz[1]);
                    }
                    zones.add(new Zone(p[0], null, poly));
                } else if (line.startsWith("SITE ")) {
                    String[] p = line.split(" ");
                    sites.add(new Site(p[1].charAt(0), Integer.parseInt(p[2]), Integer.parseInt(p[3]), Integer.parseInt(p[4]), Integer.parseInt(p[5])));
                } else if (line.startsWith("SPAWN ")) {
                    String[] p = line.split(" ");
                    (p[1].equals("T") ? spawnT : spawnCT).add(pt(p, 2));
                } else {
                    String[] p = line.split(" ");
                    Map<String, List<Pt>> target = switch (p[0]) { case "HOLD" -> hold; case "ENTRY" -> entry; case "POST" -> post; default -> null; };
                    if (target != null) target.computeIfAbsent(p[1], k -> new ArrayList<>()).add(pt(p, 2));
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("не удалось прочитать карту " + id, e);
        }
        palette = new BlockState[maxId + 1];
        palette[0] = Blocks.AIR.getDefaultState();
        pal.forEach((k, v) -> palette[k] = v);
    }

    private static long key(int x, int z) { return ((long) x << 32) ^ (z & 0xFFFFFFFFL); }

    /** Колонка блоков для генератора мира */
    public void column(int x, int z, BlockState[] a) {
        int[] rle = cols.get(key(x, z));
        if (rle == null) return;
        int lv = 0;
        for (int i = 0; i < rle.length; i += 2) {
            int bid = rle[i], n = rle[i + 1];
            if (bid != 0) {
                BlockState st = palette[bid];
                for (int k = 0; k < n; k++) {
                    int y = FLOOR + lv0 + lv + k;
                    if (y >= 0 && y < a.length) a[y] = st;
                }
            }
            lv += n;
        }
    }

    // ---------------------------------------------------------------- площадки, спавны, точки ботов
    public Site site(char c) {
        for (Site s : sites) if (s.id == c) return s;
        return sites.get(0);
    }

    /** 'A', 'B' или 0 — на какой площадке закладки точка (x, z) */
    public char siteAt(double x, double z) {
        for (Site s : sites)
            if (Math.abs(x - (s.x + 0.5)) <= s.r + 0.5 && Math.abs(z - (s.z + 0.5)) <= s.r + 0.5) return s.id;
        return 0;
    }

    public double[] siteCenter(char c) { Site s = site(c); return new double[]{s.x + 0.5, s.z + 0.5}; }
    public double siteY(char c) { return FLOOR + site(c).level + 1; }

    public Pt spawn(boolean ct, int i) {
        List<Pt> l = ct ? spawnCT : spawnT;
        return l.get(Math.floorMod(i, l.size()));
    }
    public List<Pt> spawns(boolean ct) { return ct ? spawnCT : spawnT; }

    private static final List<Pt> NONE = List.of();
    public List<Pt> hold(String k) { return hold.getOrDefault(k, NONE); }
    public List<Pt> entry(char site) { return entry.getOrDefault(String.valueOf(site), NONE); }
    public List<Pt> post(char site) { return post.getOrDefault(String.valueOf(site), NONE); }

    private static boolean inPoly(double[] p, double x, double z) {
        boolean c = false;
        int n = p.length / 2;
        for (int i = 0, j = n - 1; i < n; j = i++) {
            double xi = p[i * 2], zi = p[i * 2 + 1], xj = p[j * 2], zj = p[j * 2 + 1];
            if ((zi > z) != (zj > z) && x < (xj - xi) * (z - zi) / (zj - zi) + xi) c = !c;
        }
        return c;
    }

    /** Название места по координатам (подсказки в матче, сообщение о закладке) */
    public String zoneAt(double x, double z) {
        int ix = (int) Math.floor(x), iz = (int) Math.floor(z);
        for (Zone zn : zones) {
            if (zn.rect != null) { if (ix >= zn.rect[0] && ix <= zn.rect[1] && iz >= zn.rect[2] && iz <= zn.rect[3]) return zn.name; }
            else if (inPoly(zn.poly, x, z)) return zn.name;
        }
        return "Карта";
    }
}
