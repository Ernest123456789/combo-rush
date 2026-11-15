"""Ножи v3: детализированные модели (4 px на единицу), сквозные отверстия (прозрачные тексели, рендер cutout),
настоящие долы, травление, береста. Общая схема костей у всех ножей:

    root → weapon (замахи/осмотр) → spin (броски, вращения при доставании) → handle / blade / ring
                                  → hand (невидимая «рука»: Java не рисует эту кость, по ней ставит кисть в перчатке)

Кисть привязана к weapon, а не к spin, поэтому, когда нож крутится/подбрасывается при доставании, рука остаётся на месте.
Запуск: python3 knives.py [--write]   (превью в /tmp/kn_<id>.png)"""
import math, sys, os, json
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from wgen import Gun, MATS, NOISE, hsh, clamp, FACES
from PIL import Image
import skins as SK

K = 4
MATS.update({'birch': (198, 128, 76), 'birchD': (64, 40, 28), 'coat': (98, 96, 80), 'oliveK': (78, 82, 66), 'titan': (132, 136, 144),
             'mirror': (214, 220, 230), 'ivory': (226, 214, 186), 'wood2': (182, 140, 92), 'spacer': (34, 26, 22)})
NOISE.update({'birch': 6, 'coat': 4, 'oliveK': 4, 'titan': 3, 'mirror': 2, 'wood2': 6, 'spacer': 2})
SK.METAL |= {'coat', 'titan', 'mirror'}
SK.FURN |= {'oliveK', 'wood2'}


def q(v, s=0.25): return round(v / s) * s


def face_frame(c, face):
    x0, x1 = c.x; y0, y1 = c.y; z0, z1 = c.z
    return {'north': ((x0, y1, z0), (c.sx, 0, 0), (0, -c.sy, 0)), 'south': ((x1, y1, z1), (-c.sx, 0, 0), (0, -c.sy, 0)),
            'east': ((x1, y1, z1), (0, 0, -c.sz), (0, -c.sy, 0)), 'west': ((x0, y1, z0), (0, 0, c.sz), (0, -c.sy, 0)),
            'up': ((x0, y1, z1), (c.sx, 0, 0), (0, 0, -c.sz)), 'down': ((x0, y0, z0), (c.sx, 0, 0), (0, 0, c.sz))}[face]


def abspos(c, face, xx, yy, w, h):
    o, du, dv = face_frame(c, face)
    p = [o[i] + du[i] * (xx + .5) / w + dv[i] * (yy + .5) / h for i in range(3)]
    if c.rot:
        th = -math.radians(c.rot[0]); cs, sn = math.cos(th), math.sin(th)
        py, pz = p[1] - c.rpiv[1], p[2] - c.rpiv[2]
        p = [p[0], c.rpiv[1] + cs * py - sn * pz, c.rpiv[2] + sn * py + cs * pz]
    return p


class KGun(Gun):
    """Нож: Gun + отверстия + «кисти» (fx) по кубам"""
    def __init__(s, gid, tw=512):
        super().__init__(gid, tw, K)
        s.holes = []       # (parts, kind, a, b, r, [len])  kind 'x' — по оси X (в плоскости yz), 'z' — по оси Z (в плоскости xy)

    def hole(s, parts, kind, a, b, r, ln=0.0):
        s.holes.append((set(parts), kind, a, b, r, ln))

    def B(s, bone, part, mat, x, y, z, pat=None, rot=None, rpiv=None, fx=None, smooth=False, ytop=None):
        c = s.box(bone, part, mat, x, y, z, pat, rot, rpiv)
        c.fx = fx; c.smooth = smooth; c.ytop = ytop
        return c

    def smooth_px(s, c, face, P):
        """цельная заливка (для клинков из срезов): без кантов отдельного куба, свет по абсолютной высоте"""
        base = MATS[c.mat]
        k = {'up': 1.10, 'down': 0.80}.get(face, 1.0)
        top = getattr(c, 'ytop', None)
        if face in SIDE and top is not None: k *= 1.04 - 0.10 * min(1.0, max(0.0, (top - P[1]) / 3.0))
        n = (hsh(int(P[2] * 4), int(P[1] * 4), 7) - .5) * 4
        return (clamp(base[0] * k + n), clamp(base[1] * k + n), clamp(base[2] * k + n), 255)

    def _hole(s, c, P):
        for parts, kind, a, b, r, ln in s.holes:
            if c.part not in parts: continue
            if kind == 'x':      # a=z, b=y ; капсула вдоль z длиной ln
                dz = max(0.0, abs(P[2] - a) - ln / 2); d = math.hypot(dz, P[1] - b)
            else:                # a=x, b=y
                d = math.hypot(P[0] - a, P[1] - b)
            if d < r: return 'hole'
            if d < r + 0.3: return 'rim'
        return None

    def paint(s):
        s.pack()
        img = Image.new('RGBA', (s.tw, s.th), (0, 0, 0, 0))
        pix = img.load()
        for c in s.cubes:
            for face, (u, v, w, h) in c.rects().items():
                for yy in range(h):
                    for xx in range(w):
                        P = abspos(c, face, xx, yy, w, h)
                        col = s.smooth_px(c, face, P) if getattr(c, 'smooth', False) else s.px(c, face, xx, yy, w, h)
                        hs = s._hole(c, P) if s.holes else None
                        if hs == 'hole': col = (0, 0, 0, 0)
                        else:
                            if getattr(c, 'fx', None): col = c.fx(col, c, face, xx, yy, w, h, P)
                            if hs == 'rim': col = (int(col[0] * .62), int(col[1] * .62), int(col[2] * .62), 255)
                        pix[u + xx, v + yy] = col
        s.img = img
        return img


SIDE = ('east', 'west')
def mul(col, k): return (clamp(col[0] * k), clamp(col[1] * k), clamp(col[2] * k), col[3])
def add(col, k): return (clamp(col[0] + k), clamp(col[1] + k), clamp(col[2] + k), col[3])
def tint(col, rgb, t): return (clamp(col[0] + (rgb[0] - col[0]) * t), clamp(col[1] + (rgb[1] - col[1]) * t), clamp(col[2] + (rgb[2] - col[2]) * t), col[3])


# --------------------------------------------------------------------------------------- «кисти»
def fx_brushed(col, c, face, xx, yy, w, h, P):
    """шлифовка вдоль клинка: тонкие продольные риски"""
    if face in SIDE or face in ('up', 'down'):
        k = 1 + (hsh(int(P[1] * 4 + 50), 3, 17) - .5) * .07 + (hsh(int(P[2] * 1), int(P[1] * 4 + 50), 5) - .5) * .04
        col = mul(col, k)
    return col


def fx_edge(col, c, face, xx, yy, w, h, P):
    """подвод: светлеет к самой кромке, тонкая белая линия режущей кромки"""
    if face in SIDE or face in ('north', 'south'):
        hh = c.sy
        d = hh - (yy + .5) / K if face in SIDE else P[1] - c.y[0]
        t = 1 - min(1.0, d / max(0.25, hh))
        col = add(col, 30 * t)
        if (h - 1 - yy) == 0: col = (min(255, col[0] + 26), min(255, col[1] + 26), min(255, col[2] + 30), col[3])
    elif face == 'down': col = add(col, 30)
    return fx_brushed(col, c, face, xx, yy, w, h, P)


def fx_groove(y0, y1):
    """продольная линия-канавка (дол, спуск) на абсолютной высоте y0..y1"""
    def f(col, c, face, xx, yy, w, h, P):
        col = fx_brushed(col, c, face, xx, yy, w, h, P)
        if face in SIDE:
            if y0 <= P[1] < y1: col = mul(col, .5)
            elif y0 - .25 <= P[1] < y0: col = add(col, 26)
        return col
    return f


def fx_etch(depth0, depth1, seed=1, f0=0.0, f1=1.0, n=1):
    """травлёная панель (листья, ветки) на полосе глубины от обуха depth0..depth1, на сегментах f0..f1"""
    def f(col, c, face, xx, yy, w, h, P):
        col = fx_brushed(col, c, face, xx, yy, w, h, P)
        if face not in SIDE: return col
        fr = getattr(c, 'frac', 0.5)
        if not (f0 <= fr <= f1): return col
        d = (yy + .5) / K
        if not (depth0 <= d <= depth1): return col
        ab = int(P[2] * 4) + int(P[1] * 4) * 7
        if d - depth0 < .26 or depth1 - d < .26: return mul(col, .58)          # рамка
        col = mul(col, .80)                                                      # матовый фон
        gx, gy = int(P[2] * 4 + 0.5), int(P[1] * 4 + 0.5)
        if (gx + gy * 2 + seed) % 7 == 0 and hsh(gx // 3, gy // 3, seed) > .35: col = mul(col, .55)   # штрихи-листья
        if (gx - gy + seed) % 9 == 0 and hsh(gx // 2, gy // 4, seed + 3) > .45: col = mul(col, .6)
        if hsh(gx, gy, seed + 11) > .9: col = add(col, 40)                       # искра
        return col
    return f


def fx_birch(col, c, face, xx, yy, w, h, P):
    """наборная береста: тонкие кольца (1 тексель), тёмные чечевички-штрихи поперёк оси, светлые прожилки"""
    ring = int(math.floor(P[2] * 4 + 1e-6))
    col = mul(col, 1 + (hsh(ring, 7, 3) - .5) * .16 + (hsh(ring // 2, 1, 8) - .5) * .10)
    if ring % 2 == 0: col = mul(col, .93)
    if face in ('north', 'south'): return col
    gy = int(math.floor((P[1] + (P[0] if face in ('up', 'down') else 0)) * 4 + 50))
    if hsh(ring, gy // 3, 9) > .83: col = tint(col, MATS['birchD'], .82)                 # тёмный штрих 1×3
    elif hsh(ring, gy, 2) > .93: col = add(col, 26)
    return col


def fx_knurl(col, c, face, xx, yy, w, h, P):
    """ромбическая насечка"""
    a, b = int(P[2] * 4 + P[1] * 4), int(P[2] * 4 - P[1] * 4 + (P[0] * 4 if face in ('up', 'down') else 0))
    if a % 2 == 0 or b % 2 == 0: col = mul(col, .74)
    else: col = add(col, 10)
    return col


def fx_grain(col, c, face, xx, yy, w, h, P):
    """дерево: волокна вдоль рукояти"""
    v = math.sin(P[1] * 6.0 + math.sin(P[2] * .9) * 1.6 + P[0] * 3)
    col = mul(col, 1 + v * .07)
    if hsh(int(P[1] * 8), int(P[2] * 1.5), 4) > .9: col = mul(col, .8)
    return col


def fx_emblem(zc, yc, r):
    """круглая эмблема-«солнце» на щёчках (как на фото когтя)"""
    def f(col, c, face, xx, yy, w, h, P):
        col = fx_grain(col, c, face, xx, yy, w, h, P)
        if face not in SIDE: return col
        d = math.hypot(P[2] - zc, P[1] - yc)
        if abs(d - r) < .14: return mul(col, .55)
        if d < r - .1:
            a = math.atan2(P[1] - yc, P[2] - zc)
            if abs(math.sin(a * 4)) < .22 or d < .22: return mul(col, .6)
        return col
    return f


def fx_pins(pts, r=0.3):
    """заклёпки/винты (z, y) на щёчках"""
    def f(col, c, face, xx, yy, w, h, P):
        col0 = col
        if face in SIDE:
            for z, y in pts:
                d = math.hypot(P[2] - z, P[1] - y)
                if d < r: return add(tint(col0, (200, 204, 214), .85), 20 if (P[1] - y) > 0 else -10)
                if d < r + .14: return mul(col0, .6)
        return col
    return f


def chain(*fs):
    def f(col, c, face, xx, yy, w, h, P):
        for g in fs: col = g(col, c, face, xx, yy, w, h, P)
        return col
    return f


# --------------------------------------------------------------------------------------- геометрия
def straight_blade(g, bone, z0, z1, spine, edge, hw, bev=0.5, core='bright', emat='mirror', fuller=None, fxc=fx_brushed,
                   part='blade', epart='edge', dz=0.25):
    """Клинок срезами по 0.25: обух spine(z), кромка edge(z) (абс. y), полутолщина hw(z).
    Подводы — отдельной узкой полосой, дол fuller(z)->(y0,y1) — настоящая выемка (тоньше на 0.12)."""
    z = z0
    while z < z1 - 1e-6:
        za = min(z + dz, z1); zm = (z + za) / 2
        ys, ye, t = q(spine(zm)), q(edge(zm)), hw(zm)
        if ys - ye >= 0.25:
            b = 0.0 if ys - ye < 0.75 else min(bev, q((ys - ye) * 0.45))
            if b > 0: g.B(bone, epart, emat, (-t * 0.5, t * 0.5), (ye, ye + b), (z, za), fx=fx_edge, smooth=True, ytop=ye + b)
            lo = ye + b
            fu = fuller(zm) if fuller else None
            if fu and lo < q(fu[0]) < q(fu[1]) < ys:
                f0, f1 = q(fu[0]), q(fu[1])
                g.B(bone, part, core, (-t, t), (lo, f0), (z, za), fx=fxc, smooth=True, ytop=ys)
                g.B(bone, part, core, (-t + 0.12, t - 0.12), (f0, f1), (z, za), fx=lambda col, *a: mul(fxc(col, *a), .78), smooth=True, ytop=ys)
                g.B(bone, part, core, (-t, t), (f1, ys), (z, za), fx=fxc, smooth=True, ytop=ys)
            else:
                tt = t if b > 0 else t * 0.6
                g.B(bone, part, core, (-tt, tt), (lo, ys), (z, za), fx=fxc, smooth=True, ytop=ys)
        z = za


def curved_blade(g, bone, sz, sy, n, ds, bend, T, hw, bevk=0.42, core='bright', emat='mirror', fxc=fx_brushed, part='blade', epart='edge',
                 extra=None):
    """Изогнутый клинок: цепочка повёрнутых сегментов вдоль обуха. bend(f) — угол вниз (град.), T(f) — ширина от обуха до кромки.
    Возвращает список точек обуха [(z, y, angle)]"""
    pts = []
    for i in range(n):
        f = i / (n - 1)
        a = bend(f); t = T(f); w = hw(f) - 0.006 * i
        rot = (-a, 0, 0) if abs(a) > 1e-6 else None
        rp = (0, sy, sz)
        L = ds + (0.14 if i < n - 1 and abs(a) > 1e-6 else 0)
        if t >= 0.25:
            tb = q(max(0.25, t * bevk)) if t >= 0.75 else 0
            if tb:
                c = g.B(bone, epart, emat, (-w * 0.5, w * 0.5), (sy - t, sy - t + tb + 0.06), (sz + 0.01, sz + L - 0.01), 'edge', rot=rot, rpiv=rp if rot else None, fx=fx_brushed)
                c.frac = f
            c = g.B(bone, part, core, (-w if tb else -w * .6, w if tb else w * .6), (sy - t + tb, sy), (sz, sz + L), rot=rot, rpiv=rp if rot else None, fx=fxc)
            c.frac = f
            if extra: extra(g, i, f, sz, sy, a, rot, rp, L, w)
        pts.append((sz, sy, a))
        sz += ds * math.cos(math.radians(a)); sy -= ds * math.sin(math.radians(a))
    return pts


def std_bones(g, weapon, spin, handle=(0, 1, 6), blade=(0, 1, 10)):
    g.bone('weapon', 'root', weapon)
    g.bone('spin', 'weapon', spin)
    g.bone('handle', 'spin', handle)
    g.bone('blade', 'spin', blade)
    g.bone('hand', 'weapon', weapon)


def hand(g, x, y, z):
    g.B('hand', 'hand', 'black', x, y, z)


# =========================================================================================== НОЖ (Т/КТ)
def knife():
    """Тактический нож: прорезиненная рукоять с подпальцевыми выемками, гарда, капля с пилой по обуху и долом."""
    g = KGun('knife'); B = g.B
    std_bones(g, (0, 1.25, 6), (0, 1.25, 7))
    # навершие с отверстием под темляк
    B('handle', 'pommel', 'steel', (-1.0, 1.0), (-0.25, 2.75), (1.0, 2.25), 'scratch')
    B('handle', 'pommel', 'steel', (-0.75, 0.75), (0.25, 2.25), (0.5, 1.0))
    g.hole(['pommel'], 'x', 1.25, 1.25, 0.32)
    # рукоять: срезы, низ с подпальцевыми выемками
    z = 2.25
    while z < 9.25 - 1e-6:
        zm = z + 0.125
        bump = 0.25 * (math.cos((zm - 2.25) / 7 * 3 * 2 * math.pi) + 1) / 2
        yb = q(-0.25 + bump * 1.0, 0.125) if zm > 3 else 0.0
        B('handle', 'grip', 'rubber', (-0.9, 0.9), (yb, 2.5), (z, z + 0.25), 'rib_v')
        z += 0.25
    for s in (1, -1):        # накладки-вставки с насечкой
        B('handle', 'insert', 'poly', (0.9, 1.0) if s > 0 else (-1.0, -0.9), (0.5, 2.0), (3.0, 8.5), fx=fx_knurl)
    # гарда и рикассо
    B('handle', 'guard', 'darksteel', (-1.0, 1.0), (-0.75, 3.0), (9.25, 9.75), 'scratch')
    B('handle', 'guard', 'darksteel', (-0.75, 0.75), (-1.0, -0.5), (9.4, 9.6))
    B('blade', 'ricasso', 'steel', (-0.5, 0.5), (0.25, 2.75), (9.75, 10.75), 'seam_v')
    # клинок «капля»
    sp = lambda z: 2.75 if z < 16 else 2.75 - ((z - 16) / 4.75) ** 1.6 * 1.6
    ed = lambda z: 0.25 if z < 15.5 else 0.25 + ((z - 15.5) / 5.25) ** 2.2 * 0.9
    straight_blade(g, 'blade', 10.75, 20.75, sp, ed, lambda z: 0.45 - 0.2 * max(0, z - 15) / 5.75,
                   fuller=lambda z: (1.75, 2.25) if 11.25 < z < 16 else None)
    for z in [11.25 + i * 0.5 for i in range(7)]:          # пила по обуху
        B('blade', 'serr', 'steel', (-0.3, 0.3), (2.75, 3.25), (z, z + 0.25))
        B('blade', 'serr', 'steel', (-0.25, 0.25), (2.75, 3.0), (z + 0.25, z + 0.5))
    hand(g, (-0.9, 0.9), (0.0, 2.5), (3.0, 8.75))
    return g


# =========================================================================================== ШТЫК (M9)
def bayonet():
    """Штык-нож M9: оливковая рукоять с ромбической насечкой и продольной канавкой, защёлка на навершии,
    овальная гарда с кольцом под ствол, клипповый клинок, частая пила по обуху, отверстие кусачек у острия."""
    g = KGun('bayonet'); B = g.B
    std_bones(g, (0, 1.5, 6), (0, 1.5, 6), blade=(0, 1.5, 11))
    # навершие + защёлка
    B('handle', 'pommel', 'darksteel', (-1.25, 1.25), (-0.25, 3.25), (0.75, 2.25), 'scratch')
    B('handle', 'pommel', 'darksteel', (-1.0, 1.0), (0.0, 3.0), (0.25, 0.75))
    B('handle', 'latch', 'black', (-0.5, 0.5), (3.25, 3.75), (0.5, 2.0), 'rib_v')
    B('handle', 'latch', 'steel', (-0.25, 0.25), (3.75, 4.0), (0.75, 1.5))
    # рукоять: «цилиндр» (крест из двух коробок) + 5 рядов насечённых подушек
    B('handle', 'grip', 'oliveK', (-1.0, 1.0), (0.25, 2.75), (2.25, 9.4))
    B('handle', 'grip', 'oliveK', (-0.75, 0.75), (0.0, 3.0), (2.4, 9.35))
    for i, z in enumerate((3.0, 4.25, 5.5, 6.75)):
        for s in (1, -1):
            B('handle', 'pad', 'oliveK', (1.0, 1.2) if s > 0 else (-1.2, -1.0), (0.4, 1.35), (z, z + 1.0), fx=fx_knurl)
            B('handle', 'pad', 'oliveK', (1.0, 1.2) if s > 0 else (-1.2, -1.0), (1.65, 2.6), (z, z + 1.0), fx=fx_knurl)
        B('handle', 'pad', 'oliveK', (-0.6, 0.6), (3.0, 3.2), (z, z + 1.0), fx=fx_knurl)
    B('handle', 'collar', 'oliveK', (-1.1, 1.1), (0.1, 2.9), (8.25, 9.5), 'rib_v')
    B('handle', 'collar', 'darksteel', (-0.9, 0.9), (0.25, 2.75), (9.5, 10.25), 'scratch')
    # гарда: овальная пластина, внизу кольцо под ствол
    B('handle', 'guard', 'black', (-1.0, 1.0), (-2.0, 4.0), (10.25, 10.75), 'scratch')
    B('handle', 'guard', 'black', (-0.7, 0.7), (-2.5, 4.5), (10.3, 10.7))
    B('handle', 'guard', 'black', (-0.4, 0.4), (-2.75, -2.25), (10.35, 10.65))
    g.hole(['guard'], 'z', 0.0, -1.25, 0.55)
    # клинок
    sp = lambda z: 3.0 if z < 18 else 3.0 - ((z - 18) / 3.75) * 1.5
    ed = lambda z: 0.0 if z < 17.5 else ((z - 17.5) / 4.25) ** 1.8 * 1.5
    straight_blade(g, 'blade', 10.75, 21.75, sp, ed, lambda z: 0.5 - 0.22 * max(0, z - 16) / 5.75, core='coat', emat='titan',
                   fuller=lambda z: (1.5, 2.25) if 11.75 < z < 17.5 else None)
    g.hole(['blade', 'edge'], 'x', 19.25, 2.0, 0.42, 0.9)
    for i in range(18):                                    # частая пила
        z = 11.5 + i * 0.25
        B('blade', 'serr', 'coat', (-0.35, 0.35), (3.0, 3.5 if i % 2 == 0 else 3.25), (z, z + 0.25))
    hand(g, (-1.0, 1.0), (0.0, 3.0), (3.0, 9.0))
    return g


# =========================================================================================== СКЕЛЕТНЫЙ (стилет-складник)
def skeleton():
    """Складной стилет: титановые плашки с круглыми сквозными отверстиями, клипса, винт оси, гарды-квиллоны,
    узкий копьевидный клинок с упором и флиппером. Клинок — отдельная кость: при доставании он выбрасывается из рукояти."""
    g = KGun('skeleton'); B = g.B
    std_bones(g, (0, 1.25, 6), (0, 1.25, 6), blade=(0, 1.25, 9.25))
    for s in (1, -1):
        xr = (0.25, 0.7) if s > 0 else (-0.7, -0.25)
        z = 1.0
        while z < 10.0 - 1e-6:                              # плашки со скруглённым торцом
            zm = z + 0.125
            yb, yt = (0.0, 2.5)
            if zm < 1.75: yb, yt = (0.25 + (1.75 - zm) * 0.4, 2.25 - (1.75 - zm) * 0.4)
            B('handle', 'scale', 'titan', xr, (q(yb, .125), q(yt, .125)), (z, z + 0.25), fx=fx_brushed)
            z += 0.25
        B('handle', 'screw', 'bright', (0.7, 0.85) if s > 0 else (-0.85, -0.7), (0.9, 1.6), (8.9, 9.6), 'screw')
        B('handle', 'screw', 'bright', (0.7, 0.8) if s > 0 else (-0.8, -0.7), (1.0, 1.5), (1.5, 2.0), 'screw')
    for z, r in ((3.0, 0.42), (4.6, 0.55), (6.3, 0.55), (7.8, 0.42)):
        g.hole(['scale'], 'x', z, 1.25, r)
    B('handle', 'spacer', 'darksteel', (-0.25, 0.25), (2.25, 2.5), (1.25, 8.75), 'seam_v')
    B('handle', 'clip', 'bright', (0.7, 0.9), (1.75, 2.25), (2.0, 7.25), 'scratch')
    B('handle', 'clip', 'bright', (0.7, 0.9), (2.0, 2.5), (7.25, 7.75))
    # квиллоны
    B('handle', 'guard', 'darksteel', (-0.6, 0.6), (-0.75, 0.0), (9.5, 10.0), 'scratch')
    B('handle', 'guard', 'darksteel', (-0.6, 0.6), (2.5, 3.0), (9.5, 10.0), 'scratch')
    # клинок (кость blade, ось — винт)
    B('blade', 'tang', 'steel', (-0.22, 0.22), (0.5, 2.25), (8.5, 10.0), 'seam_v')
    B('blade', 'tang', 'steel', (-0.22, 0.22), (-0.6, 0.5), (9.25, 9.85), 'rib_h')      # флиппер
    B('blade', 'stud', 'bright', (0.22, 0.45), (1.9, 2.3), (10.6, 10.95))
    B('blade', 'stud', 'bright', (-0.45, -0.22), (1.9, 2.3), (10.6, 10.95))
    sp = lambda z: 2.5 if z < 15 else 2.5 - ((z - 15) / 3.25) ** 1.3 * 1.15
    ed = lambda z: 0.25 if z < 15 else 0.25 + ((z - 15) / 3.25) ** 1.3 * 1.1
    straight_blade(g, 'blade', 10.0, 18.25, sp, ed, lambda z: 0.24 - 0.08 * max(0, z - 14) / 4.25, bev=0.75, emat='mirror',
                   fxc=fx_groove(1.25, 1.5))
    hand(g, (-0.75, 0.75), (0.0, 2.5), (3.0, 8.75))
    return g


# =========================================================================================== КОГОТЬ (керамбит)
def claw():
    """Керамбит: деревянные щёчки с эмблемой и штифтами, кольцо-восьмигранник, джимпинг, серп с тремя сквозными отверстиями."""
    g = KGun('claw'); B = g.B
    RC = (0, -2.75, 1.75)                                 # центр кольца
    g.bone('weapon', 'root', RC)
    g.bone('spin', 'weapon', RC)
    g.bone('handle', 'spin', (0, 0.5, 7))
    g.bone('ring', 'spin', RC)
    g.bone('blade', 'spin', (0, 1.5, 10))
    g.bone('hand', 'weapon', RC)
    # хвостовик (сталь) и щёчки
    B('handle', 'tang', 'darksteel', (-0.3, 0.3), (-1.0, 2.0), (3.5, 10.25), 'seam')
    pins = [(5.0, 0.5), (7.75, 0.5)]
    for s in (1, -1):
        xr = (0.3, 0.95) if s > 0 else (-0.95, -0.3)
        z = 3.75
        while z < 10.0 - 1e-6:                               # низ рукояти — дугой, «подпальцевый» горб у гарды
            zm = z + 0.125
            yb = -0.75 - 0.35 * math.sin((zm - 3.75) / 6.25 * math.pi)
            yt = 1.75 - (0.3 * (zm - 8.5) if zm > 8.5 else 0)
            B('handle', 'scale', 'wood2', xr, (q(yb, .125), q(yt, .125)), (z, z + 0.25), fx=chain(fx_emblem(6.4, 0.5, 0.75), fx_pins(pins)))
            z += 0.25
    B('handle', 'guard', 'darksteel', (-0.6, 0.6), (-2.0, -0.75), (9.75, 10.5), 'scratch')       # упор
    B('handle', 'guard', 'darksteel', (-0.45, 0.45), (-2.5, -2.0), (10.0, 10.5))
    # кольцо: 8 граней вокруг RC (радиус 1.65), с ободком
    for i in range(8):
        a = i * 45.0
        Ro, Ri = 2.1, 1.25
        hl = (Ro + Ri) / 2 * math.tan(math.radians(22.5)) + 0.08
        # сегмент строим «внизу» кольца (в -y от центра), затем поворачиваем на a вокруг центра
        B('ring', 'ring', 'darksteel', (-0.45 + 0.004 * i, 0.45 - 0.004 * i), (RC[1] - Ro, RC[1] - Ri), (RC[2] - hl, RC[2] + hl), 'scratch',
          rot=(a, 0, 0) if a else None, rpiv=RC if a else None)
    B('ring', 'ring', 'darksteel', (-0.35, 0.35), (-0.95, 0.25), (2.75, 3.75))             # перемычка кольца к рукояти
    # клинок-серп: обух дугой, 18 сегментов, отверстия у основания
    Rs, n, A = 7.75, 18, 104.0
    d = A / n; ch = 2 * Rs * math.sin(math.radians(d / 2))
    sz0, sy0 = 10.5, 2.0
    zc, yc = sz0, sy0 - Rs
    for i in range(n):
        a0 = math.radians(i * d)
        sz, sy = zc + Rs * math.sin(a0), yc + Rs * math.cos(a0)
        h = i * d + d / 2
        f = i / (n - 1)
        T = max(0.5, 4.5 - 4.0 * f ** 1.1)
        hw = 0.55 - 0.012 * i
        rot = (-h, 0, 0); rp = (0, sy, sz)
        L = ch + (0.14 if i < n - 1 else 0)
        Tb = max(0.5, q(T * 0.4))
        B('blade', 'edge', 'mirror', (-hw * 0.5, hw * 0.5), (sy - T, sy - T + Tb + 0.06), (sz, sz + L), rot=rot, rpiv=rp, fx=fx_edge, smooth=True)
        B('blade', 'blade', 'bright', (-hw, hw), (sy - T + Tb, sy), (sz, sz + L), rot=rot, rpiv=rp, fx=fx_brushed, smooth=True)
        if i < 5:                                             # джимпинг на обухе
            for j in range(2):
                B('blade', 'jimp', 'darksteel', (-hw * 0.7, hw * 0.7), (sy, sy + 0.3), (sz + 0.1 + j * ch / 2, sz + 0.1 + j * ch / 2 + ch / 4),
                  rot=rot, rpiv=rp)
    for z, y, r in ((11.6, 0.75, 0.42), (12.9, 0.55, 0.36), (14.1, 0.15, 0.3)):
        g.hole(['blade', 'edge'], 'x', z, y, r)
    hand(g, (-0.95, 0.95), (-1.0, 1.75), (4.25, 9.5))
    return g


# =========================================================================================== КУКРИ
def gurkha():
    """Кукри: хромированное навершие с клювом и бойком, тёмные проставки, наборная береста (бочонком), хромированный больстер,
    тяжёлый изогнутый клинок с зеркальным спуском, долом у обуха и травлёной панелью."""
    g = KGun('gurkha'); B = g.B
    std_bones(g, (0, 1.25, 4.5), (0, 1.25, 6), blade=(0, 1.25, 10))
    # навершие
    B('handle', 'pommel', 'mirror', (-1.3, 1.3), (-0.75, 3.0), (-0.75, 0.25), 'scratch')
    B('handle', 'pommel', 'mirror', (-1.05, 1.05), (1.0, 3.5), (-1.5, -0.75))                  # верхний «клюв» назад
    B('handle', 'pommel', 'mirror', (-0.95, 0.95), (-1.25, 0.25), (-1.0, -0.75))
    B('handle', 'pommel', 'mirror', (-0.8, 0.8), (3.0, 3.75), (-1.75, -1.0))
    g.hole(['pommel'], 'x', -0.4, 1.75, 0.3)
    B('handle', 'spacer', 'spacer', (-1.2, 1.2), (-0.5, 2.75), (0.25, 0.75))
    # береста бочонком: два креста срезов
    z = 0.75
    while z < 8.0 - 1e-6:
        zm = z + 0.125
        bul = math.sin((zm - 0.75) / 7.25 * math.pi)
        r1, r2 = 1.15 + 0.2 * bul, 0.85 + 0.15 * bul
        yc = 1.125
        B('handle', 'birch', 'birch', (-r1, r1), (q(yc - r2 - 0.05, .125), q(yc + r2 + 0.05, .125)), (z, z + 0.25), fx=fx_birch)
        B('handle', 'birch', 'birch', (-r2, r2), (q(yc - r1 - 0.1, .125), q(yc + r1 + 0.1, .125)), (z + 0.02, z + 0.23), fx=fx_birch)
        z += 0.25
    B('handle', 'spacer', 'spacer', (-1.2, 1.2), (-0.5, 2.75), (8.0, 8.5))
    # больстер
    B('handle', 'bolster', 'mirror', (-1.1, 1.1), (-0.25, 2.5), (8.5, 9.75), 'scratch')
    B('handle', 'bolster', 'mirror', (-0.85, 0.85), (-0.5, 2.75), (8.6, 9.6))
    B('handle', 'bolster', 'mirror', (-0.7, 0.7), (0.0, 2.25), (9.75, 10.25))
    # клинок: профиль в абсолютных координатах (срезы по 0.25): обух круто уходит вниз, брюхо широкое, остриё внизу-впереди
    def slope(z):
        if z < 13: return 0.0
        if z < 17: return 0.58 * (z - 13) / 4
        if z < 22: return 0.58
        return 0.58 + 0.9 * ((z - 22) / 3.5) ** 1.5
    zs = [10.25 + i * 0.05 for i in range(400)]
    sp_tab, y = [], 2.5
    for z in zs: sp_tab.append(y); y -= slope(z) * 0.05
    def spine(z): return sp_tab[min(len(zs) - 1, max(0, int((z - 10.25) / 0.05)))]
    ZT = 25.75
    def W(z):
        if z < 13.5: return 2.5 - 0.3 * (z - 10.25) / 3.25
        if z < 21: return 2.2 + 2.6 * ((z - 13.5) / 7.5) ** 1.3
        return 4.8 * max(0.0, 1 - ((z - 21) / (ZT - 21)) ** 1.8) ** 0.75
    edge = lambda z: spine(z) - W(z)
    def fx_k(col, c, face, xx, yy, w, h, P):
        col = fx_brushed(col, c, face, xx, yy, w, h, P)
        if face not in SIDE: return col
        d = spine(P[2]) - P[1]
        if P[2] < 22.5 and 0.5 <= d < 0.75: return mul(col, .5)                  # дол-линия вдоль обуха
        if P[2] < 22.5 and 0.75 <= d < 1.0: return add(col, 18)
        if 13.25 < P[2] < 20.25 and 1.25 <= d <= 0.25 + 0.55 * W(P[2]):
            return fx_etch(0, 99, seed=3)(col, c, face, xx, yy, w, h, P) if (d - 1.25 >= 0.25 and 0.25 + 0.55 * W(P[2]) - d >= 0.25 and 13.5 < P[2] < 20) else mul(col, .58)
        return col
    straight_blade(g, 'blade', 10.25, ZT, spine, edge, lambda z: 0.6 - 0.25 * max(0, z - 20) / 5.75, bev=2.0, fxc=fx_k,
                   fuller=lambda z: (spine(z) - 0.75, spine(z) - 0.5) if z < 12.5 else None)
    hand(g, (-1.2, 1.2), (-0.25, 2.5), (1.5, 7.5))
    return g


# =========================================================================================== НОЖ-БАБОЧКА
MATS['bfly'] = (104, 108, 118); NOISE['bfly'] = 3


def butterfly():
    """Нож-бабочка (балисонг): две рукояти-швеллера со сквозными прорезями вращаются на осях у хвостовика,
    защёлка на конце рукояти A, клинок с долом, рикассо с «кикером». Закрыт — рукояти накрывают клинок с двух сторон."""
    g = KGun('butterfly'); B = g.B
    PV = (0, 1.25, 10.0)
    g.bone('weapon', 'root', (0, 1.25, 6))
    g.bone('spin', 'weapon', (0, 1.25, 7))
    g.bone('blade', 'spin', PV)
    g.bone('handle_a', 'spin', PV)
    g.bone('handle_b', 'spin', PV)
    g.bone('hand', 'weapon', (0, 1.25, 6))
    for bone, s in (('handle_a', 1), ('handle_b', -1)):
        xo = (0.28, 0.82) if s > 0 else (-0.82, -0.28)
        part = 'wall_' + bone[-1]
        z = 1.0
        while z < 10.5 - 1e-6:                                   # стенка со скруглёнными концами
            zm = z + 0.125
            yb, yt = 0.0, 2.5
            if zm < 1.6: yb, yt = 0.2 + (1.6 - zm) * 0.5, 2.3 - (1.6 - zm) * 0.5
            if zm > 9.9: yb, yt = 0.3 + (zm - 9.9) * 0.9, 2.2 - (zm - 9.9) * 0.9
            B(bone, part, 'bfly', xo, (q(yb, .125), q(yt, .125)), (z, z + 0.25), fx=fx_brushed)
            z += 0.25
        xi = (0.22, 0.28) if s > 0 else (-0.28, -0.22)          # полки швеллера
        B(bone, 'lip', 'bfly', xi, (0.0, 0.3), (1.5, 9.5), 'seam_v')
        B(bone, 'lip', 'bfly', xi, (2.2, 2.5), (1.5, 9.5), 'seam_v')
        px = (0.82, 0.97) if s > 0 else (-0.97, -0.82)
        B(bone, 'pin', 'bright', px, (0.9, 1.6), (9.65, 10.35), 'screw')            # ось
        B(bone, 'pin', 'bright', px, (0.95, 1.55), (1.7, 2.3), 'screw')              # стопорный штифт
        for z, r in ((3.0, 0.42), (4.5, 0.5), (6.0, 0.5), (7.5, 0.42)):
            g.hole([part], 'x', z, 1.25, r)
        g.hole([part], 'x', 8.9, 1.25, 0.28, 0.6)
    B('handle_a', 'latch', 'steel', (0.0, 0.28), (1.0, 1.5), (0.6, 1.8), 'rib_h')    # защёлка
    B('handle_a', 'latch', 'bright', (-0.1, 0.0), (1.0, 1.5), (0.6, 1.0))
    # клинок: хвостовик с кикером, рикассо, клинок с долом и скосом обуха
    B('blade', 'tang', 'steel', (-0.21, 0.21), (0.3, 2.3), (9.0, 10.75), 'seam_v')
    B('blade', 'tang', 'steel', (-0.21, 0.21), (-0.3, 0.3), (9.4, 10.4))
    sp = lambda z: 2.5 if z < 16.25 else 2.5 - ((z - 16.25) / 3.5) ** 1.2 * 1.25
    ed = lambda z: (0.75 if z < 11.25 else 0.25) if z < 16.5 else 0.25 + ((z - 16.5) / 3.25) ** 2 * 1.0
    straight_blade(g, 'blade', 10.75, 19.75, sp, ed, lambda z: 0.21 - 0.06 * max(0, z - 15.5) / 4.25, bev=0.75,
                   fuller=lambda z: (1.5, 1.75) if 11.5 < z < 15.75 else None)
    hand(g, (-0.85, 0.85), (0.0, 2.5), (2.5, 9.0))
    return g


ALL = {'knife': knife, 'bayonet': bayonet, 'skeleton': skeleton, 'claw': claw, 'gurkha': gurkha, 'butterfly': butterfly}


# --------------------------------------------------------------------------------------- превью
def preview(gid, out=None, scale=26):
    import texprev as TP
    g = ALL[gid](); g.paint()
    keep = g.cubes; g.cubes = [c for c in keep if c.part != 'hand']
    W, H = 1000, 420
    views = [TP.render(g, 90, 0, W, H, scale, bg=(52, 58, 70)), TP.render(g, -90, 0, W, H, scale, bg=(52, 58, 70)),
             TP.render(g, 55, 22, W, H, scale * .9, bg=(52, 58, 70)), TP.render(g, 0, 90, W, H, scale * .9, bg=(52, 58, 70))]
    im = Image.new('RGB', (W * 2, H * 2))
    for i, v in enumerate(views): im.paste(v, ((i % 2) * W, (i // 2) * H))
    g.cubes = keep
    im.save(out or f'/tmp/kn_{gid}.png')
    return g


if __name__ == '__main__':
    for gid in (sys.argv[1:] or ALL):
        if gid.startswith('--'): continue
        g = preview(gid)
        bad = g.coplanar()
        print(gid, 'cubes', len(g.cubes), 'tex', g.tw, g.th, 'coplanar', len(bad), bad[:3])
