"""Скины кейса «Калейдоскоп» (без ножей): 17 скинов на стволы, среди них аниме-рисунок (AWP «Сакура»)
и рельефный скин с собственной геометрией (AK-47 «Резное золото» — выпуклый золотой орнамент на боковинах)."""
import math
from PIL import Image, ImageDraw
import skins as SK
from skins import C, ramp, lerp
from wgen import hsh

K_ = (16, 16, 18)


# ------------------------------------------------------------------ аниме: AWP «Сакура» (оригинальная героиня)
def _sakura_canvas(z0=-9, z1=36, y0=-10, y1=9, ppu=8):
    W, H = int((z1 - z0) * ppu), int((y1 - y0) * ppu)
    im = Image.new('RGB', (W, H)); d = ImageDraw.Draw(im)
    P = lambda z, y: ((z - z0) * ppu, (y1 - y) * ppu)
    for yy in range(H):                                   # пастельный фон: розовый → лавандовый
        t = yy / H
        d.line([(0, yy), (W, yy)], fill=tuple(int(a + (b - a) * t) for a, b in zip((255, 205, 225), (190, 175, 245))))
    for i in range(70):                                   # лепестки сакуры и искры
        z, y = z0 + hsh(i, 1, 3) * (z1 - z0), y0 + hsh(i, 2, 3) * (y1 - y0)
        x, yp = P(z, y); r = 3 + hsh(i, 3, 3) * 3
        if i % 3:
            d.ellipse([x - r, yp - r * .6, x + r, yp + r * .6], fill=(255, 150, 190)); d.ellipse([x - r * .4, yp - r * .3, x + r * .4, yp + r * .3], fill=(255, 225, 238))
        else:
            d.line([(x - r, yp), (x + r, yp)], fill=(255, 255, 255), width=2); d.line([(x, yp - r), (x, yp + r)], fill=(255, 255, 255), width=2)
    cz, cy = 3.9, -1.2                                   # лицо — на прикладе, левее окна ствольной коробки
    def E(z, y, rz, ry, col):
        a, b = P(z - rz, y + ry); c, e = P(z + rz, y - ry); d.ellipse([a, b, c, e], fill=col)
    HAIR, HAIR2, SKIN = (255, 118, 172), (214, 74, 136), (255, 226, 210)
    E(cz - 3.9, cy + 1.6, 1.4, 1.4, HAIR2); E(cz + 3.9, cy + 1.6, 1.4, 1.4, HAIR2)           # пучки-«оданго»
    for s in (-1, 1):                                                                     # хвостики
        d.polygon([P(cz + s * 3.6, cy + 1.2), P(cz + s * 5.6, cy - 2.5), P(cz + s * 4.8, cy - 6.5), P(cz + s * 3.2, cy - 2.0)], fill=HAIR)
    E(cz, cy + 0.6, 3.6, 3.5, HAIR)                                                       # волосы сзади
    E(cz, cy - 0.5, 2.75, 2.6, SKIN)                                                      # лицо
    bang = [P(cz - 3.2, cy + 0.2)]                                                        # чёлка зубцами
    for i in range(9):
        z = cz - 3.0 + i * 0.75
        bang += [P(z, cy + (0.1 if i % 2 else 1.0)), P(z + 0.38, cy + (1.0 if i % 2 else 0.1))]
    bang += [P(cz + 3.2, cy + 0.2), P(cz + 3.0, cy + 3.6), P(cz - 3.0, cy + 3.6)]
    d.polygon(bang, fill=HAIR)
    for i in range(5): d.line([P(cz - 2.2 + i * 1.1, cy + 3.0), P(cz - 2.0 + i * 1.1, cy + 1.2)], fill=HAIR2, width=2)
    for s in (-1, 1):                                                                     # глаза
        ez = cz + s * 1.15
        E(ez, cy - 0.8, 0.62, 0.86, (255, 255, 255))
        E(ez, cy - 0.85, 0.5, 0.74, (126, 74, 214))
        E(ez, cy - 1.0, 0.3, 0.42, (64, 30, 120))
        E(ez - 0.18, cy - 0.45, 0.17, 0.2, (255, 255, 255)); E(ez + 0.2, cy - 1.25, 0.09, 0.1, (255, 255, 255))
        d.line([P(ez - 0.7, cy - 0.05), P(ez + 0.7, cy - 0.1)], fill=(40, 20, 40), width=4)   # ресницы
        d.line([P(ez + s * 0.7, cy - 0.1), P(ez + s * 0.95, cy + 0.15)], fill=(40, 20, 40), width=3)
        E(ez + s * 0.35, cy - 1.95, 0.5, 0.2, (255, 170, 190))                             # румянец
    d.line([P(cz - 0.35, cy - 2.25), P(cz, cy - 2.45), P(cz + 0.35, cy - 2.25)], fill=(190, 60, 80), width=3)   # «ω»-улыбка
    # звёздочка-заколка
    sx, sy = P(cz + 2.6, cy + 2.6)
    pts = [(sx + math.cos(a) * (9 if k % 2 == 0 else 4), sy - math.sin(a) * (9 if k % 2 == 0 else 4)) for k, a in enumerate([math.pi / 2 + i * math.pi / 5 for i in range(10)])]
    d.polygon(pts, fill=(255, 214, 60), outline=(200, 140, 20))
    # надпись катаканой-«подписью» заменена простым сердечком
    hx, hy = P(cz - 6.5, cy + 1.6)
    d.ellipse([hx - 6, hy - 5, hx, hy + 1], fill=(240, 60, 110)); d.ellipse([hx, hy - 5, hx + 6, hy + 1], fill=(240, 60, 110)); d.polygon([(hx - 6, hy - 1), (hx + 6, hy - 1), (hx, hy + 7)], fill=(240, 60, 110))
    return im, (z0, z1, y0, y1, ppu)


def anime():
    im, (z0, z1, y0, y1, ppu) = _sakura_canvas()
    px = im.load(); W, H = im.size
    def f(p, h, ab):
        x = int((ab[2] - z0) * ppu); y = int((y1 - ab[1]) * ppu)
        return px[min(W - 1, max(0, x)), min(H - 1, max(0, y))]
    return f


# ------------------------------------------------------------------ прочие рисунки
def synthwave():
    """«Неоновая ночь»: закатное солнце в полоску, неоновая сетка, фиолетовое небо"""
    def f(p, h, ab):
        u, v = p[2], p[1]
        if v < 0.42:                                                   # сетка-пол
            d = (0.42 - v) / 0.42
            if abs(math.sin((u - 0.5) / (d + 0.08) * 10)) < 0.12 or (math.sin(1 / (d + 0.06) * 3) > 0.92): return C('#ff3ec8')
            return lerp(C('#1a0636'), C('#05010f'), d)
        r = math.hypot((u - 0.5) * 2.4, v - 0.5)
        if r < 0.28 and not (v < 0.6 and int(v * 60) % 3 == 0): return ramp([C('#ffd23f'), C('#ff6a3d'), C('#ff2d8a')], (0.78 - v) * 2.6)
        if hsh(int(ab[2] * 3), int(ab[1] * 3), 4) > 0.97: return C('#ffffff')
        return lerp(C('#2a0a5a'), C('#7a1a8a'), max(0, 1 - (v - 0.42) * 2))
    return f


def stained():
    """«Витраж»: цветные стёкла в тёмной свинцовой обвязке (ячейки Вороного)"""
    COLS = [C('#c8283a'), C('#2a6ad8'), C('#f0b82a'), C('#2aa86a'), C('#8a3ad8'), C('#e86a2a'), C('#3ad8d0')]
    def f(p, h, ab):
        z, y = ab[2] / 2.6, ab[1] / 2.6
        cz, cy = math.floor(z), math.floor(y)
        best = [9, 9, 0]
        for dz in (-1, 0, 1):
            for dy in (-1, 0, 1):
                gz, gy = cz + dz, cy + dy
                qz, qy = gz + hsh(gz, gy, 1), gy + hsh(gz, gy, 2)
                dd = math.hypot(z - qz, y - qy)
                if dd < best[0]: best = [dd, best[0], int(hsh(gz, gy, 3) * 7)]
                elif dd < best[1]: best[1] = dd
        if best[1] - best[0] < 0.09: return C('#1a1a1e')
        c = COLS[best[2]]
        return lerp(c, (255, 255, 255), max(0, 0.35 - best[0]) * 0.8)
    return f


def galaxy():
    """«Космос»: туманность синим-пурпурным, звёзды"""
    def f(p, h, ab):
        n = (math.sin(ab[2] * 0.45 + math.sin(ab[1] * 0.6) * 2.1) + math.sin(ab[1] * 0.8 - ab[2] * 0.25 + 1.3)) / 2
        col = ramp([C('#05061a'), C('#1a1660'), C('#6a2aa8'), C('#e45ac8'), C('#ffd6f0')], max(0, min(1, n * 0.6 + 0.35)))
        s = hsh(int(ab[2] * 4), int(ab[1] * 4), 13)
        if s > 0.985: return C('#ffffff')
        if s > 0.97: return lerp(col, (255, 255, 255), 0.5)
        return col
    return f


def watercolor():
    """«Акварель»: размытые цветные пятна с тёмным краем на бумаге"""
    COLS = [C('#4aa8e8'), C('#f07aa8'), C('#f0c84a'), C('#7ad87a')]
    def f(p, h, ab):
        best = None
        for i in range(10):
            pz, py = -10 + hsh(i, 1, 9) * 40, -9 + hsh(i, 2, 9) * 16
            r = 3 + hsh(i, 3, 9) * 4
            d = math.hypot(ab[2] - pz, (ab[1] - py) * 1.4) / r
            if d < 1 and (best is None or d < best[0]): best = (d, COLS[i % 4])
        if best:
            d, c = best
            col = lerp(c, (250, 246, 238), 0.55 * (1 - d))
            return lerp(col, lerp(c, (0, 0, 0), 0.3), 1.0) if d > 0.9 else col
        return lerp(C('#f6f1e6'), C('#e8e0cc'), hsh(int(ab[2] * 2), int(ab[1] * 2), 5))
    return f


def candy():
    """«Леденец»: пастельные диагональные полосы"""
    COLS = [C('#ff9ac8'), C('#fff2f8'), C('#9ad8ff'), C('#fff2f8'), C('#c8ff9a'), C('#fff2f8')]
    return lambda p, h, ab: COLS[int(math.floor((ab[2] + ab[1] * 1.3) / 1.2)) % 6]


def khokhloma():
    """«Хохлома»: чёрный лак, красные ягоды, золотые завитки и листья"""
    K, R, G1, G2 = C('#121010'), C('#c8201c'), C('#e8b83a'), C('#a87818')
    def f(p, h, ab):
        z, y = ab[2], ab[1]
        stem = 0.5 * math.sin(z * 0.7)
        if abs(y - stem) < 0.28: return G1
        for k in range(-20, 40, 3):                              # завитки и листья вдоль стебля
            sy = 0.5 * math.sin(k * 0.7) + (1.4 if k % 2 else -1.4)
            dd = math.hypot(z - k, y - sy)
            if dd < 0.75: return R if dd < 0.6 else K                                      # ягода
            if abs(dd - 1.25) < 0.18 and (z - k) * (1 if k % 2 else -1) > -0.3: return G1  # завиток
            lz, ly = z - (k + 1.5), y - (0.5 * math.sin((k + 1.5) * 0.7) + (-1.0 if k % 2 else 1.0))
            if (lz / 0.9) ** 2 + (ly / 0.4) ** 2 < 1: return G2 if lz < 0 else G1           # лист
        return K
    return f


def aurora():
    """«Северное сияние»: ночное небо, зелёно-фиолетовые занавеси"""
    def f(p, h, ab):
        u, v = p[2], p[1]
        a = math.sin(u * 14 + math.sin(u * 5) * 2) * 0.12 + 0.55
        d = v - a
        if 0 < d < 0.3:
            t = d / 0.3
            col = ramp([C('#3cffb0'), C('#2ad86a'), C('#8a4ae8'), C('#14183a')], t)
            if abs(math.sin(u * 80)) < 0.3: col = lerp(col, (255, 255, 255), 0.15 * (1 - t))
            return col
        if hsh(int(ab[2] * 4), int(ab[1] * 4), 21) > 0.985: return C('#ffffff')
        return lerp(C('#0a1030'), C('#030612'), 1 - v)
    return f


def marble():
    """«Мрамор»: белый мрамор с золотыми прожилками"""
    def f(p, h, ab):
        n = math.sin(ab[2] * 0.6 + math.sin(ab[1] * 1.1 + ab[2] * 0.2) * 3 + math.sin(ab[1] * 0.4) * 2)
        if abs(n) < 0.06: return C('#d8a83a')
        if abs(n) < 0.14: return C('#a8a6a0')
        return lerp(C('#f6f4f0'), C('#dcdad4'), (n + 1) / 2)
    return f


def scroll():
    """«Гравюра»: светлое серебро с тёмной гравировкой завитков"""
    def f(p, h, ab):
        z, y = ab[2], ab[1]
        gz, gy = (z % 2.5) - 1.25, (y % 2.5) - 1.25
        a = math.atan2(gy, gz); r = math.hypot(gz, gy)
        spiral = abs(r - (0.25 + (a + math.pi) / (2 * math.pi) * 0.9))
        if spiral < 0.1: return C('#2a2c32')
        return lerp(C('#e2e6ec'), C('#b8bec8'), (math.sin(y * 3) + 1) / 2)
    return f


def pixels():
    """«Пиксели»: 8-битные крупные квадраты в ретро-палитре"""
    COLS = [C('#2a2a5a'), C('#3a6ad8'), C('#e83a5a'), C('#f0d83a'), C('#3ad87a'), C('#1a1a2a')]
    return lambda p, h, ab: COLS[int(hsh(int(math.floor(ab[2])), int(math.floor(ab[1])), 31) * 6)]


def rust():
    """«Ржавчина»: патина, рыжие подтёки, облупленная зелёная краска"""
    def f(p, h, ab):
        n = hsh(int(ab[2] * 2), int(ab[1] * 2), 4) * 0.5 + (math.sin(ab[2] * 0.8 + ab[1]) + 1) * 0.25
        if n > 0.62: return lerp(C('#a4502a'), C('#6e3118'), hsh(int(ab[2] * 4), int(ab[1] * 4), 2))
        if n > 0.5: return C('#5a3a20')
        return lerp(C('#4a6a4a'), C('#3a5040'), hsh(int(ab[2]), int(ab[1]), 6))
    return f


def holo():
    """«Голограмма»: радужный перелив по диагонали с блёстками"""
    def f(p, h, ab):
        t = ((ab[2] * 0.15 + ab[1] * 0.25) % 1 + 1) % 1
        col = ramp([C('#ff6ad8'), C('#6ad8ff'), C('#8aff6a'), C('#ffe46a'), C('#ff6ad8')], t)
        if hsh(int(ab[2] * 4), int(ab[1] * 4), 17) > 0.94: return C('#ffffff')
        return lerp(col, (255, 255, 255), 0.25)
    return f


def lava():
    """«Лава»: чёрный базальт, светящиеся трещины"""
    def f(p, h, ab):
        z, y = ab[2] / 1.8, ab[1] / 1.8
        cz, cy = math.floor(z), math.floor(y)
        best = [9, 9]
        for dz in (-1, 0, 1):
            for dy in (-1, 0, 1):
                gz, gy = cz + dz, cy + dy
                dd = math.hypot(z - gz - hsh(gz, gy, 1), y - gy - hsh(gz, gy, 2))
                if dd < best[0]: best = [dd, best[0]]
                elif dd < best[1]: best[1] = dd
        e = best[1] - best[0]
        if e < 0.06: return C('#ffe24a')
        if e < 0.14: return C('#ff6a1a')
        if e < 0.22: return C('#8a1a0a')
        return lerp(C('#1e1a1a'), C('#2e2626'), hsh(int(ab[2] * 3), int(ab[1] * 3), 8))
    return f


PIC = lambda fn: (fn, fn)
KALEIDO = {
    'awp': [('sakura', 'Сакура', 'Sakura', 'legendary', *PIC(anime()))],
    'ak47': [('carved', 'Резное золото', 'Carved Gold', 'legendary', SK.solid(C('#18161a')), SK.solid(C('#4a1e14')))],
    'm4a4': [('neon', 'Неоновая ночь', 'Neon Night', 'epic', *PIC(synthwave()))],
    'famas': [('stained', 'Витраж', 'Stained Glass', 'epic', *PIC(stained()))],
    'usp': [('galaxy', 'Космос', 'Galaxy', 'epic', *PIC(galaxy()))],
    'nova': [('khokhloma', 'Хохлома', 'Khokhloma', 'epic', *PIC(khokhloma()))],
    'revolver': [('engraved', 'Гравюра', 'Engraved', 'epic', *PIC(scroll()))],
    'mp7': [('watercolor', 'Акварель', 'Watercolor', 'rare', *PIC(watercolor()))],
    'mac10': [('candy', 'Леденец', 'Candy', 'rare', *PIC(candy()))],
    'xm1014': [('aurora', 'Северное сияние', 'Aurora', 'rare', *PIC(aurora()))],
    'fiveseven': [('marble', 'Мрамор', 'Marble', 'rare', *PIC(marble()))],
    'scar20': [('holo', 'Голограмма', 'Hologram', 'rare', *PIC(holo()))],
    'glock18': [('lava', 'Лава', 'Lava', 'rare', *PIC(lava()))],
    'mag7': [('pixels', 'Пиксели', 'Pixels', 'uncommon', *PIC(pixels()))],
    'sawedoff': [('rust', 'Ржавчина', 'Rust', 'uncommon', *PIC(rust()))],
    'g3sg1': [('digital', 'Цифра', 'Digital', 'uncommon', SK.digi(C('#5a6a7a'), C('#3a4652'), C('#8a9aa8')), SK.solid(C('#2a323a')))],
    'deagle': [('cobalt', 'Кобальт', 'Cobalt', 'uncommon', SK.grad([C('#1a3a8a'), C('#2a5ad8'), C('#1a3a8a')], 2), SK.solid(C('#141e3a')))],
}
# скины с собственной геометрией: id → (база, функция рельефа)
OWN_MODEL = {'ak47_carved'}
for gid, lst in KALEIDO.items():
    SK.SKINS.setdefault(gid, [])
    have = {s[0] for s in SK.SKINS[gid]}
    SK.SKINS[gid] += [s for s in lst if s[0] not in have]
