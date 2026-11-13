package com.derevna230.comborush.universe;

import com.derevna230.comborush.ComboRush;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;

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
 * Карта «Дюны» (измерение combo-rush:dust). Сама карта — текстовый ресурс {@code data/combo-rush/maps/dust.map},
 * его делает генератор {@code tools/map_gen/dust.py} (там же схема и проверка проходимости).
 * <pre>
 *   x → восток, z → юг.   Т — на западе (x ≈ −56), КТ — на востоке (x ≈ +56)
 *   «Лонг» (север) ведёт к А (приподнятая, +2, балкон и яма), «Нижний» (юг) — к Б (утоплена, −2, склад и мостик);
 *   посередине «Мид» → «Развязка» → «Центр» с мостом; «Склад» и «Тоннели» — закрытые здания.
 * </pre>
 * Уровень 0 = блок пола на высоте {@link #FLOOR}; игрок стоит на FLOOR + уровень + 1.
 */
public final class DustMap {
    private DustMap() {}

    public static final int FLOOR = 64;
    /** Игровая область (вокруг ещё кольцо внешней стены) */
    public static final int X0, X1, Z0, Z1;
    private static final int LV0;

    private static final BlockState[] PALETTE;
    private static final Map<Long, int[]> COLS = new HashMap<>();     // колонка → RLE: пары (id палитры, длина) от LV0

    public record Site(char id, int x, int z, int r, int level) {}
    public record Pt(int x, int z, int level) {
        public double cx() { return x + 0.5; }
        public double cz() { return z + 0.5; }
        public double y() { return FLOOR + level + 1; }
    }
    private record Zone(String name, int x0, int x1, int z0, int z1) {}

    private static final List<Site> SITES = new ArrayList<>();
    private static final List<Zone> ZONES = new ArrayList<>();
    private static final List<Pt> SPAWN_T = new ArrayList<>(), SPAWN_CT = new ArrayList<>();
    private static final Map<String, List<Pt>> HOLD = new HashMap<>(), ENTRY = new HashMap<>(), POST = new HashMap<>();

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

    static {
        int x0 = 0, x1 = 0, z0 = 0, z1 = 0, lv0 = 0;
        Map<Integer, BlockState> pal = new HashMap<>();
        int maxId = 0;
        try (InputStream in = DustMap.class.getResourceAsStream("/data/" + ComboRush.MOD_ID + "/maps/dust.map")) {
            if (in == null) throw new IllegalStateException("нет ресурса карты dust.map");
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = r.readLine()) != null) {
                if (line.isEmpty() || line.charAt(0) == '#') continue;
                if (line.startsWith("C ")) {
                    String[] p = line.split(" ");
                    int[] rle = new int[(p.length - 3) * 2];
                    for (int i = 3; i < p.length; i++) {
                        String[] q = p[i].split(":");
                        rle[(i - 3) * 2] = Integer.parseInt(q[0]);
                        rle[(i - 3) * 2 + 1] = Integer.parseInt(q[1]);
                    }
                    COLS.put(key(Integer.parseInt(p[1]), Integer.parseInt(p[2])), rle);
                } else if (line.startsWith("B ")) {
                    String[] p = line.split(" ", 3);
                    int id = Integer.parseInt(p[1]);
                    pal.put(id, parseState(p[2]));
                    maxId = Math.max(maxId, id);
                } else if (line.startsWith("BOUNDS ")) {
                    String[] p = line.split(" ");
                    x0 = Integer.parseInt(p[1]); x1 = Integer.parseInt(p[2]); z0 = Integer.parseInt(p[3]); z1 = Integer.parseInt(p[4]); lv0 = Integer.parseInt(p[5]);
                } else if (line.startsWith("Z ")) {
                    String[] p = line.substring(2).split("\\|");
                    ZONES.add(new Zone(p[0], Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]), Integer.parseInt(p[4])));
                } else if (line.startsWith("SITE ")) {
                    String[] p = line.split(" ");
                    SITES.add(new Site(p[1].charAt(0), Integer.parseInt(p[2]), Integer.parseInt(p[3]), Integer.parseInt(p[4]), Integer.parseInt(p[5])));
                } else if (line.startsWith("SPAWN ")) {
                    String[] p = line.split(" ");
                    Pt pt = new Pt(Integer.parseInt(p[2]), Integer.parseInt(p[3]), Integer.parseInt(p[4]));
                    (p[1].equals("T") ? SPAWN_T : SPAWN_CT).add(pt);
                } else {
                    String[] p = line.split(" ");
                    Map<String, List<Pt>> target = switch (p[0]) { case "HOLD" -> HOLD; case "ENTRY" -> ENTRY; case "POST" -> POST; default -> null; };
                    if (target != null) target.computeIfAbsent(p[1], k -> new ArrayList<>()).add(new Pt(Integer.parseInt(p[2]), Integer.parseInt(p[3]), Integer.parseInt(p[4])));
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("не удалось прочитать карту dust.map", e);
        }
        X0 = x0; X1 = x1; Z0 = z0; Z1 = z1; LV0 = lv0;
        PALETTE = new BlockState[maxId + 1];
        PALETTE[0] = Blocks.AIR.getDefaultState();
        pal.forEach((k, v) -> PALETTE[k] = v);
    }

    private static long key(int x, int z) { return ((long) x << 32) ^ (z & 0xFFFFFFFFL); }

    // ---------------------------------------------------------------- колонка
    public static void column(int x, int z, BlockState[] a) {
        int[] rle = COLS.get(key(x, z));
        if (rle == null) return;
        int lv = 0;
        for (int i = 0; i < rle.length; i += 2) {
            int id = rle[i], n = rle[i + 1];
            if (id != 0) {
                BlockState st = PALETTE[id];
                for (int k = 0; k < n; k++) {
                    int y = FLOOR + LV0 + lv + k;
                    if (y >= 0 && y < a.length) a[y] = st;
                }
            }
            lv += n;
        }
    }

    // ---------------------------------------------------------------- площадки, спавны, точки ботов
    public static Site site(char c) {
        for (Site s : SITES) if (s.id == c) return s;
        return SITES.get(0);
    }

    /** 'A', 'B' или 0 — на какой площадке закладки точка (x, z) */
    public static char siteAt(double x, double z) {
        for (Site s : SITES) {
            if (Math.abs(x - (s.x + 0.5)) <= s.r + 0.5 && Math.abs(z - (s.z + 0.5)) <= s.r + 0.5) return s.id;
        }
        return 0;
    }

    public static double[] siteCenter(char c) { Site s = site(c); return new double[]{s.x + 0.5, s.z + 0.5}; }
    public static double siteY(char c) { return FLOOR + site(c).level + 1; }

    /** Место возрождения игрока/бота номер i (0..4) */
    public static Pt spawn(boolean ct, int i) {
        List<Pt> l = ct ? SPAWN_CT : SPAWN_T;
        return l.get(Math.floorMod(i, l.size()));
    }

    private static final List<Pt> NONE = List.of();
    public static List<Pt> hold(String key) { return HOLD.getOrDefault(key, NONE); }
    public static List<Pt> entry(char site) { return ENTRY.getOrDefault(String.valueOf(site), NONE); }
    public static List<Pt> post(char site) { return POST.getOrDefault(String.valueOf(site), NONE); }

    /** Название места по координатам (для подсказок в матче) */
    public static String zoneAt(double x, double z) {
        int ix = (int) Math.floor(x), iz = (int) Math.floor(z);
        for (Zone zn : ZONES) if (ix >= zn.x0 && ix <= zn.x1 && iz >= zn.z0 && iz <= zn.z1) return zn.name;
        return "Карта";
    }
}
