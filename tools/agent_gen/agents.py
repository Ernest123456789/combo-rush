#!/usr/bin/env python3
"""Модели бойцов «Противостояния»: КТ (спецназ) и Т (боевик).
Геометрия — коробки в координатах костей ванильной модели игрока (y вниз, лицо смотрит в −z, правая рука игрока = −x).
Текстура — box-UV как у скинов, но с плотностью K пикселей на единицу (в игре кость содержит дочернюю часть с масштабом 1/K).
Выход: assets/combo-rush/agents/<id>.json + textures/entity/agent_<id>.png"""
import json, math, os, sys
from PIL import Image
import numpy as np

K = 4
TEXW = 512

def hsh(*a):
    h = 2166136261
    for v in a:
        h ^= (int(v) * 2654435761) & 0xFFFFFFFF
        h = (h * 16777619) & 0xFFFFFFFF
        h ^= h >> 13
    return (h & 0xFFFF) / 65535.0

def vnoise(x, y, s, f=6.0):
    """плавный шум 0..1"""
    fx, fy = x / f, y / f
    ix, iy = math.floor(fx), math.floor(fy)
    tx, ty = fx - ix, fy - iy
    tx = tx * tx * (3 - 2 * tx); ty = ty * ty * (3 - 2 * ty)
    a = hsh(ix, iy, s); b = hsh(ix + 1, iy, s); c = hsh(ix, iy + 1, s); d = hsh(ix + 1, iy + 1, s)
    return (a * (1 - tx) + b * tx) * (1 - ty) + (c * (1 - tx) + d * tx) * ty

def hexc(h): h = h.lstrip('#'); return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))
def mul(c, k): return tuple(max(0, min(255, int(v * k))) for v in c)
def mix(a, b, t): return tuple(int(a[i] * (1 - t) + b[i] * t) for i in range(3))

# ---------------------------------------------------------------- материалы: (цвет, узор)
def pat(kind, base, x, y, s):
    n = hsh(x, y, s) - 0.5
    if kind == 'ripstop':
        k = 1 + n * 0.10 + (vnoise(x, y, s, 9) - 0.5) * 0.12
        if x % 8 == 0 or y % 8 == 0: k *= 0.96
        return mul(base, k)
    if kind == 'knit':
        k = 1 + n * 0.08 + (0.07 if x % 2 == 0 else -0.05)
        if y % 3 == 0: k *= 0.95
        return mul(base, k)
    if kind == 'rib':          # резинка шапки: поперечные рубчики
        k = 1 + n * 0.06 + (0.09 if y % 2 == 0 else -0.06)
        return mul(base, k)
    if kind == 'canvas':
        k = 1 + n * 0.10 + (vnoise(x, y, s, 7) - 0.5) * 0.18
        if (x + y) % 3 == 0: k *= 0.93
        w = vnoise(x, y, s + 9, 5)
        if w > 0.80: k *= 1.10                          # потёртости
        return mul(base, k)
    if kind == 'cordura':
        k = 1 + n * 0.07 + (0.04 if (x + y) % 2 == 0 else -0.03)
        return mul(base, k)
    if kind == 'twill':
        k = 1 + n * 0.08 + (vnoise(x, y, s, 8) - 0.5) * 0.14
        if (x - y) % 4 == 0: k *= 0.92
        return mul(base, k)
    if kind == 'leather':
        k = 1 + n * 0.06 + (vnoise(x, y, s, 5) - 0.5) * 0.25
        if vnoise(x, y, s + 3, 3) > 0.83: k *= 1.18
        return mul(base, k)
    if kind == 'poly':
        k = 1 + n * 0.06 + (vnoise(x, y, s, 10) - 0.5) * 0.08
        if hsh(x, y, s + 1) > 0.985: k *= 1.35
        return mul(base, k)
    if kind == 'rubber':
        return mul(base, 1 + n * 0.05)
    if kind == 'metal':
        return mul(base, 1 + n * 0.08 + (vnoise(x, y, s, 4) - 0.5) * 0.2)
    if kind == 'skin':
        return mul(base, 1 + n * 0.03 + (vnoise(x, y, s, 6) - 0.5) * 0.08)
    if kind == 'velcro':
        return mul(base, 1 + n * 0.22)
    if kind == 'shemagh':      # красный платок с тонкой светлой сеткой и точками
        cx, cy = x % 4, y % 4
        light = hexc('#d8ccb6')
        if (cx == 0) != (cy == 0): c = mix(base, light, 0.75)
        elif cx == 2 and cy == 2: c = mix(base, light, 0.45)
        else: c = base
        return mul(c, 1 + n * 0.08 + (vnoise(x, y, s, 6) - 0.5) * 0.16)
    if kind == 'brass':
        return mul(base, 1 + n * 0.1)
    return mul(base, 1 + n * 0.05)

# ---------------------------------------------------------------- коробки
class Box:
    def __init__(s, bone, x, y, z, mat, edge=0.82, rot=None, name=''):
        s.bone = bone
        s.x0, s.x1 = sorted(x); s.y0, s.y1 = sorted(y); s.z0, s.z1 = sorted(z)
        s.mat = mat; s.edge = edge; s.rot = rot; s.name = name
        s.decor = []
        for v in (s.x0, s.x1, s.y0, s.y1, s.z0, s.z1):
            assert abs(v * K - round(v * K)) < 1e-6, (name, v)
        s.dx = round((s.x1 - s.x0) * K); s.dy = round((s.y1 - s.y0) * K); s.dz = round((s.z1 - s.z0) * K)
        assert s.dx > 0 and s.dy > 0 and s.dz > 0, name
        s.w = 2 * (s.dx + s.dz); s.h = s.dy + s.dz
        s.u = s.v = 0

    def d(s, *a): s.decor.append(a); return s

    # область грани в атласе и перевод модельных координат грани → пиксели грани
    def region(s, f):
        u, v, dx, dy, dz = s.u, s.v, s.dx, s.dy, s.dz
        return {'w': (u, v + dz, dz, dy), 'n': (u + dz, v + dz, dx, dy), 'e': (u + dz + dx, v + dz, dz, dy),
                's': (u + 2 * dz + dx, v + dz, dx, dy), 'u': (u + dz, v, dx, dz), 'd': (u + dz + dx, v, dx, dz)}[f]

    def to_px(s, f, h, v):
        """h, v — модельные координаты по осям грани: n/s → (x, y), w/e → (z, y), u/d → (x, z)"""
        if f == 'n': a, b = h - s.x0, v - s.y0
        elif f == 's': a, b = s.x1 - h, v - s.y0
        elif f == 'w': a, b = s.z1 - h, v - s.y0
        elif f == 'e': a, b = h - s.z0, v - s.y0
        else: a, b = h - s.x0, s.z1 - v
        return a * K, b * K

class Model:
    def __init__(s, mid):
        s.id = mid; s.boxes = []
    def box(s, *a, **k):
        b = Box(*a, **k); s.boxes.append(b); return b
    def mirror(s, b, bone):
        """копия коробки для другой стороны (x → −x), с отражением декора"""
        nb = Box(bone, (-b.x1, -b.x0), (b.y0, b.y1), (b.z0, b.z1), b.mat, b.edge, None, b.name + '_m')
        for dd in b.decor:
            f = dd[0]
            nf = {'w': 'e', 'e': 'w'}.get(f, f)
            args = list(dd[2:])
            if f in ('n', 's', 'u', 'd') and args:
                if isinstance(args[0], tuple): args[0] = (-args[0][1], -args[0][0])
                elif isinstance(args[0], (int, float)):
                    wdt = {'vline': 1 / K, 'zip': 2 / K, 'px': 1 / K}.get(dd[1], 0)
                    args[0] = -args[0] - wdt
            nb.decor.append((nf, dd[1], *args))
        s.boxes.append(nb); return nb

# ---------------------------------------------------------------- рисование
class Painter:
    def __init__(s, w, h):
        s.img = np.zeros((h, w, 4), dtype=np.uint8)
    def set(s, x, y, c, a=255):
        if 0 <= y < s.img.shape[0] and 0 <= x < s.img.shape[1]:
            s.img[y, x, :3] = c[:3]; s.img[y, x, 3] = a
    def get(s, x, y): return tuple(int(v) for v in s.img[y, x, :3])

MATS = {}
def M(name, color, kind): MATS[name] = (hexc(color), kind)

def paint_box(P, b, seed):
    base, kind = MATS[b.mat]
    for f in 'wneSud'.replace('S', 's'):
        ru, rv, rw, rh = b.region(f)
        for a in range(rw):
            for c in range(rh):
                col = pat(kind, base, ru + a, rv + c, seed)
                # объём: сверху светлее, к низу темнее (бока)
                if f in 'wnes':
                    col = mul(col, 1.06 - 0.12 * (c / max(1, rh - 1)))
                elif f == 'd':
                    col = mul(col, 0.8)
                # тёмная кромка
                e = min(a, c, rw - 1 - a, rh - 1 - c)
                if e == 0: col = mul(col, b.edge)
                elif e == 1 and b.edge < 0.9: col = mul(col, (1 + b.edge) / 2 + 0.04)
                P.set(ru + a, rv + c, col)

def face_rect(b, f, hr, vr):
    a0, b0 = b.to_px(f, hr[0], vr[0]); a1, b1 = b.to_px(f, hr[1], vr[1])
    ax0, ax1 = sorted((round(a0), round(a1))); bx0, bx1 = sorted((round(b0), round(b1)))
    ru, rv, rw, rh = b.region(f)
    return ru, rv, max(0, ax0), max(0, bx0), min(rw, ax1), min(rh, bx1)

def D_fill(P, b, f, hr, vr, mat, bevel=True, edge=0.8):
    base, kind = MATS[mat]
    ru, rv, a0, b0, a1, b1 = face_rect(b, f, hr, vr)
    for a in range(a0, a1):
        for c in range(b0, b1):
            col = pat(kind, base, ru + a, rv + c, 7)
            if bevel:
                if c == b0 or a == a0: col = mul(col, 1.15)
                if c == b1 - 1 or a == a1 - 1: col = mul(col, edge)
            P.set(ru + a, rv + c, col)

def D_color(P, b, f, hr, vr, color, k_noise=0.05):
    ru, rv, a0, b0, a1, b1 = face_rect(b, f, hr, vr)
    cc = hexc(color) if isinstance(color, str) else color
    for a in range(a0, a1):
        for c in range(b0, b1):
            P.set(ru + a, rv + c, mul(cc, 1 + (hsh(a, c, 3) - 0.5) * k_noise))

def D_shade(P, b, f, hr, vr, k):
    ru, rv, a0, b0, a1, b1 = face_rect(b, f, hr, vr)
    for a in range(a0, a1):
        for c in range(b0, b1):
            P.set(ru + a, rv + c, mul(P.get(ru + a, rv + c), k))

def D_stitch(P, b, f, hr, vr, k=0.7, step=2):
    """пунктирный шов по контуру прямоугольника"""
    ru, rv, a0, b0, a1, b1 = face_rect(b, f, hr, vr)
    for a in range(a0, a1):
        for c in (b0, b1 - 1):
            if (a // step) % 2 == 0 and 0 <= c: P.set(ru + a, rv + c, mul(P.get(ru + a, rv + c), k))
    for c in range(b0, b1):
        for a in (a0, a1 - 1):
            if (c // step) % 2 == 0: P.set(ru + a, rv + c, mul(P.get(ru + a, rv + c), k))

def D_hline(P, b, f, hr, v, k=0.7, dash=0):
    ru, rv, a0, b0, a1, b1 = face_rect(b, f, hr, (v, v + 1 / K))
    for a in range(a0, a1):
        if dash and (a // dash) % 2: continue
        for c in range(b0, b1): P.set(ru + a, rv + c, mul(P.get(ru + a, rv + c), k))

def D_vline(P, b, f, h, vr, k=0.7, dash=0):
    ru, rv, a0, b0, a1, b1 = face_rect(b, f, (h, h + 1 / K), vr)
    for c in range(b0, b1):
        if dash and (c // dash) % 2: continue
        for a in range(a0, a1): P.set(ru + a, rv + c, mul(P.get(ru + a, rv + c), k))

def D_molle(P, b, f, hr, vr):
    """MOLLE: горизонтальные ленты с прорезями"""
    ru, rv, a0, b0, a1, b1 = face_rect(b, f, hr, vr)
    for c in range(b0, b1):
        row = (c - b0) % 4
        for a in range(a0, a1):
            col = P.get(ru + a, rv + c)
            if row == 0: col = mul(col, 1.18)
            elif row == 1: col = mul(col, 1.05)
            elif row == 3: col = mul(col, 0.70)
            if row in (1, 2) and (a - a0) % 6 == 5: col = mul(col, 0.55)
            P.set(ru + a, rv + c, col)

def D_zip(P, b, f, h, vr, color='#9a9a9a'):
    ru, rv, a0, b0, a1, b1 = face_rect(b, f, (h, h + 2 / K), vr)
    for c in range(b0, b1):
        for a in range(a0, a1):
            P.set(ru + a, rv + c, mul(hexc(color), 1.2 if (c + a) % 2 == 0 else 0.6))

def D_text(P, b, f, h, v, text, color, scale=1):
    """крошечный пиксельный шрифт 3×5"""
    G = {'К': ["1.1", "11.", "1..", "11.", "1.1"], 'Т': ["111", ".1.", ".1.", ".1.", ".1."], 'С': ["111", "1..", "1..", "1..", "111"],
         'П': ["111", "1.1", "1.1", "1.1", "1.1"], 'Е': ["111", "1..", "11.", "1..", "111"], 'Ц': ["1.1", "1.1", "1.1", "111", "..1"],
         'Н': ["1.1", "1.1", "111", "1.1", "1.1"], 'А': ["111", "1.1", "111", "1.1", "1.1"], 'З': ["111", "..1", ".11", "..1", "111"],
         'О': ["111", "1.1", "1.1", "1.1", "111"], 'Р': ["111", "1.1", "111", "1..", "1.."], ' ': ["...", "...", "...", "...", "..."],
         '7': ["111", "..1", ".1.", ".1.", ".1."], '3': ["111", "..1", ".11", "..1", "111"], '0': ["111", "1.1", "1.1", "1.1", "111"]}
    a0, c0 = b.to_px(f, h, v); a0 = round(a0); c0 = round(c0)
    ru, rv, rw, rh = b.region(f)
    x = 0
    for ch in text:
        g = G[ch]
        for r in range(5):
            for q in range(3):
                if g[r][q] == '1':
                    for i in range(scale):
                        for j in range(scale):
                            aa, cc = a0 + x + q * scale + i, c0 + r * scale + j
                            if 0 <= aa < rw and 0 <= cc < rh: P.set(ru + aa, rv + cc, hexc(color))
        x += 4 * scale

def D_px(P, b, f, h, v, color):
    a, c = b.to_px(f, h, v)
    ru, rv, rw, rh = b.region(f)
    a, c = int(a), int(c)
    if 0 <= a < rw and 0 <= c < rh: P.set(ru + a, rv + c, hexc(color) if isinstance(color, str) else color)

def D_fn(P, b, f, fn):
    """произвольная кисть: fn(a, c, w, h, get) → цвет или None"""
    ru, rv, rw, rh = b.region(f)
    for a in range(rw):
        for c in range(rh):
            col = fn(a, c, rw, rh, P.get(ru + a, rv + c))
            if col is not None: P.set(ru + a, rv + c, col)

DECOR = {'fill': D_fill, 'color': D_color, 'shade': D_shade, 'stitch': D_stitch, 'hline': D_hline, 'vline': D_vline,
         'molle': D_molle, 'zip': D_zip, 'text': D_text, 'px': D_px, 'fn': D_fn}

# ---------------------------------------------------------------- упаковка и экспорт
def pack(boxes):
    order = sorted(boxes, key=lambda b: (-b.h, -b.w))
    x = y = rowh = 0
    for b in order:
        if x + b.w > TEXW: x = 0; y += rowh; rowh = 0
        b.u, b.v = x, y
        x += b.w; rowh = max(rowh, b.h)
    H = y + rowh
    th = 64
    while th < H: th *= 2
    return th

BONES = {'head': (0, 0, 0), 'body': (0, 0, 0), 'right_arm': (-5, 2, 0), 'left_arm': (5, 2, 0), 'right_leg': (-1.9, 12, 0), 'left_leg': (1.9, 12, 0)}
OFF = {'right_leg': (-0.1, 0, 0), 'left_leg': (0.1, 0, 0)}     # ноги ставим вплотную, без перекрытия

def build(model, outdir, texdir):
    th = pack(model.boxes)
    P = Painter(TEXW, th)
    for i, b in enumerate(model.boxes): paint_box(P, b, i * 31 + 5)
    for b in model.boxes:
        for dd in b.decor: DECOR[dd[1]](P, b, dd[0], *dd[2:])
    img = Image.fromarray(P.img, 'RGBA')
    os.makedirs(texdir, exist_ok=True); os.makedirs(outdir, exist_ok=True)
    img.save(os.path.join(texdir, 'agent_' + model.id + '.png'))
    js = {'k': K, 'w': TEXW, 'h': th, 'off': {k: list(v) for k, v in OFF.items()}, 'boxes': []}
    for b in model.boxes:
        e = {'b': b.bone, 'uv': [b.u, b.v], 'o': [b.x0, b.y0, b.z0], 's': [b.x1 - b.x0, b.y1 - b.y0, b.z1 - b.z0]}
        if b.rot: e['r'] = list(b.rot[0]) + list(b.rot[1])
        js['boxes'].append(e)
    open(os.path.join(outdir, model.id + '.json'), 'w').write(json.dumps(js, separators=(',', ':')))
    print(model.id, 'коробок:', len(model.boxes), 'текстура', TEXW, '×', th)
    return img

# ---------------------------------------------------------------- проверка совпадающих граней (мерцание)
def world_faces(model):
    out = []
    for b in model.boxes:
        if b.rot: continue
        px, py, pz = BONES[b.bone]; ox, oy, oz = OFF.get(b.bone, (0, 0, 0))
        X0, X1, Y0, Y1, Z0, Z1 = b.x0 + px + ox, b.x1 + px + ox, b.y0 + py, b.y1 + py, b.z0 + pz, b.z1 + pz
        out += [(b, 'x-', X0, (Y0, Y1, Z0, Z1)), (b, 'x+', X1, (Y0, Y1, Z0, Z1)), (b, 'y-', Y0, (X0, X1, Z0, Z1)),
                (b, 'y+', Y1, (X0, X1, Z0, Z1)), (b, 'z-', Z0, (X0, X1, Y0, Y1)), (b, 'z+', Z1, (X0, X1, Y0, Y1))]
    return out

def check(model):
    fs = world_faces(model)
    bad = []
    for i in range(len(fs)):
        for j in range(i + 1, len(fs)):
            a, b = fs[i], fs[j]
            if a[1] != b[1] or abs(a[2] - b[2]) > 1e-6: continue
            r, q = a[3], b[3]
            if min(r[1], q[1]) - max(r[0], q[0]) > 1e-6 and min(r[3], q[3]) - max(r[2], q[2]) > 1e-6:
                bad.append((a[0].name, b[0].name, a[1], a[2]))
    return bad
