"""Скины кейса «Граффити»: рисунки во весь ствол (координаты p — доли габаритов модели: p[2] вдоль ствола, p[1] — высота)
и три скина на G3SG1. Добавляются в skins.SKINS."""
import math
import skins as SK
from skins import C, ramp, lerp, camo, grad, BLK, WHT, ORG, RED, GOLD, CYAN, MAG, NAVY, BLU, ICE, LIME, YEL, PUR
from wgen import hsh


def _edge(d, w=0.012):
    return abs(d) < w


def beast():
    """«Зверь»: пурпур/бирюза по диагонали, оскал зубов, жёлтый глаз, чёрный контур"""
    A, Bc, Y, K = C('#d4307e'), C('#1fc4b4'), C('#ffd21f'), C('#101014')
    def f(p, h, ab):
        u, v = p[2], p[1]
        d = (u * 0.8 - v * 0.6) - 0.15
        col = A if d < 0 else Bc
        if _edge(d, 0.015): return K
        # глаз
        r = math.hypot((u - 0.42) * 2.2, v - 0.68)
        if r < 0.10: return K if r < 0.035 else (Y if r < 0.085 else K)
        # зубы: пила белых треугольников
        if 0.30 < v < 0.46 and 0.5 < u < 0.9:
            t = (u * 40) % 1
            top = 0.46 - abs(t - 0.5) * 0.30
            if v > top - 0.01 and v < top + 0.01: return K
            if v < top: return WHT
        if 0.29 < v < 0.31 and 0.5 < u < 0.9: return K
        # брызги
        if hsh(int(ab[2] * 2), int(ab[1] * 2), 41) > 0.95: return Y
        return col
    return f


def printstream():
    """«Оттиск»: перламутрово-белый с чёрными текучими линиями"""
    P1, P2, K = C('#f4f2f6'), C('#d9cfe6'), C('#0e0e12')
    def f(p, h, ab):
        u, v = p[2], p[1]
        s = math.sin(u * 22 + math.sin(v * 7 + u * 3) * 2.2)
        if abs(s) < 0.16: return K
        if u > 0.72 and abs(math.sin(u * 60 + v * 9)) < 0.3: return K        # к дулу — плотнее
        return lerp(P1, P2, (math.sin(v * 9 + u * 4) + 1) / 2)
    return f


def azimuth():
    """«Азимут»: белые панели, оранжевая диагональная вставка, чёрный «хвост» с сеткой"""
    W2, O, K, G = C('#eef0f2'), C('#f07a1a'), C('#121418'), C('#3a3f48')
    def f(p, h, ab):
        u, v = p[2], p[1]
        a = u + v * 0.6
        if _edge(a - 0.55, 0.012) or _edge(a - 0.85, 0.012) or _edge(v - 0.25, 0.01): return K
        if u > 0.86 or v < 0.25:
            gx, gy = (ab[2] * 2) % 1, (ab[1] * 2) % 1
            return G if (gx < 0.12 or gy < 0.12) else K
        if 0.55 < a < 0.85: return O
        if 0.12 < u < 0.4 and 0.7 < v < 0.74: return O
        return W2
    return f


def great_wave():
    """«Великая волна»: тёмно-синее море, гребни волн с белой пеной и завитками"""
    N1, N2, B2, F = C('#0f1d3a'), C('#1d3d74'), C('#3f73b8'), C('#eef4fa')
    def f(p, h, ab):
        u, v = p[2], p[1]
        crest = 0.66 + 0.14 * math.sin(u * 16) + 0.05 * math.sin(u * 41)
        if v > crest + 0.07: return lerp(C('#e9dcc0'), C('#d8c39a'), v)                # небо-бумага
        if v > crest: return F if (u * 60) % 1 < 0.7 else B2                            # пена
        k = (crest - v) * 3
        col = ramp([B2, N2, N1], min(1.0, k))
        if abs(math.sin((crest - v) * 40 + u * 6)) < 0.12: col = F if k < 0.4 else B2  # прожилки
        return col
    return f


def noir():
    """«Нуар»: комикс — белый/чёрный, красные пятна, растровые точки"""
    Wc, K, R = C('#f2efe8'), C('#121212'), C('#d0282e')
    def f(p, h, ab):
        u, v = p[2], p[1]
        s = math.sin(u * 7) * math.cos(v * 6 + u * 2)
        col = R if s > 0.45 else (K if s < -0.55 else Wc)
        if _edge(s - 0.45, 0.04) or _edge(s + 0.55, 0.04): return K
        if col == Wc:                                                                     # растр
            gx, gy = (ab[2] * 2.5) % 1 - 0.5, (ab[1] * 2.5) % 1 - 0.5
            if math.hypot(gx, gy) < 0.22 * (1 - v): return K
        return col
    return f


def graffiti():
    """«Граффити»: серый бетон, яркие кляксы-теги с обводкой и потёками"""
    G1, K = C('#7d7f84'), C('#121216')
    COLS = [C('#ff3e8a'), C('#2fd0ff'), C('#ffd41f'), C('#7aff3a'), C('#a26bff'), C('#ff7a1f')]
    def f(p, h, ab):
        z, y = ab[2], ab[1]
        cz, cy = math.floor(z / 4), math.floor(y / 4)
        best = None
        for dz in (-1, 0, 1):
            for dy in (-1, 0, 1):
                gz, gy = cz + dz, cy + dy
                if hsh(gz, gy, 7) < 0.35: continue
                pz, py = (gz + hsh(gz, gy, 8)) * 4, (gy + hsh(gz, gy, 9)) * 4
                r = 1.4 + hsh(gz, gy, 10) * 1.6
                d = math.hypot(z - pz, (y - py) * 1.2) - r
                if best is None or d < best[0]: best = (d, int(hsh(gz, gy, 11) * len(COLS)) % len(COLS), pz, py, r)
        if best:
            d, ci, pz, py, r = best
            if d < -0.35: return COLS[ci]
            if d < 0.05: return K
            if y < py and abs(z - pz - (hsh(int(pz), 1, 3) - .5) * r) < 0.35 and y > py - r - 3 * hsh(int(pz), 2, 4): return COLS[ci]   # потёк
        n = hsh(int(z * 3), int(y * 3), 5)
        return lerp(G1, C('#9a9ca2'), n)
    return f


def serpent():
    """«Огненный змей»: тёмно-зелёный фон, извивающийся змей в золотой и красной чешуе"""
    G1, G2, S1, S2, K = C('#14301e'), C('#1f4a2c'), C('#e2b13c'), C('#c0301e'), C('#0a0a0a')
    def f(p, h, ab):
        u, v = p[2], p[1]
        c = 0.5 + 0.24 * math.sin(u * 9.5)
        d = abs(v - c)
        if d < 0.14:
            if d > 0.125: return K
            sc = (math.sin(ab[2] * 3.2) + math.sin(ab[1] * 3.2 + ab[2] * 1.6)) > 0.4
            return S1 if sc else S2
        if 0.95 < u and d < 0.2: return S2
        return G1 if hsh(int(ab[2]), int(ab[1]), 3) < 0.6 else G2
    return f


def acid():
    """«Кислота»: чёрный с ядовито-зелёными кляксами и брызгами"""
    K, L1, L2 = C('#101410'), C('#9cff2e'), C('#3cbf1a')
    def f(p, h, ab):
        n = math.sin(ab[2] * 0.9 + math.sin(ab[1] * 1.3) * 2) + math.sin(ab[1] * 1.7 - ab[2] * 0.5) * 0.8
        if n > 0.9: return L1
        if n > 0.7: return L2
        if hsh(int(ab[2] * 3), int(ab[1] * 3), 2) > 0.97: return L1
        return K
    return f


def treasure():
    """«Карта сокровищ»: пергамент, пунктирный путь, крест-метка и роза ветров"""
    P1, P2, Br, R = C('#e2cf9e'), C('#c4a96a'), C('#5a3b1c'), C('#b32a1a')
    def f(p, h, ab):
        u, v = p[2], p[1]
        col = lerp(P1, P2, hsh(int(ab[2] * 2), int(ab[1] * 2), 3) * 0.5 + (abs(u - 0.5) + abs(v - 0.5)) * 0.6)
        path = 0.5 + 0.2 * math.sin(u * 11)
        if abs(v - path) < 0.02 and (u * 50) % 1 < 0.55: return Br
        if abs((u - 0.78) - (v - 0.6)) < 0.02 and abs(u - 0.78) < 0.05: return R
        if abs((u - 0.78) + (v - 0.6)) < 0.02 and abs(u - 0.78) < 0.05: return R
        r = math.hypot((u - 0.2) * 2, v - 0.55)
        if abs(r - 0.18) < 0.012 or (r < 0.18 and (abs(u - 0.2) < 0.005 or abs(v - 0.55) < 0.01)): return Br
        return col
    return f


def chalk():
    """«Мел»: аспидная доска, белые меловые каракули и стрелки"""
    B1, B2, Wc = C('#25302a'), C('#2f3c34'), C('#e8ece4')
    def f(p, h, ab):
        u, v = p[2], p[1]
        col = lerp(B1, B2, hsh(int(ab[2] * 2), int(ab[1] * 2), 6))
        for k in range(3):
            y0 = 0.25 + k * 0.25
            if abs(v - (y0 + 0.06 * math.sin(u * (20 + k * 7) + k))) < 0.012 and hsh(int(ab[2] * 4), k, 8) > 0.15: return Wc
        if abs(u - 0.5 - (v - 0.5) * 0.3) < 0.006: return lerp(Wc, B1, 0.3)
        return col
    return f


def orange_crash():
    """«Оранжевый всплеск»: оранжево-чёрные диагональные осколки"""
    O, K, Y = C('#f07a1a'), C('#16181c'), C('#ffd23a')
    def f(p, h, ab):
        a = ab[2] * 0.8 + ab[1] * 1.4
        k = int(math.floor(a / 2.2))
        t = hsh(k, 0, 5)
        return O if t < 0.45 else (K if t < 0.85 else Y)
    return f


PIC = lambda fn: (fn, fn)        # рисунок идёт и по металлу, и по фурнитуре
GRAFFITI = {
    'ak47': [('beast', 'Неоновый зверь', 'Neon Beast', 'legendary', *PIC(beast()))],
    'm4a1s': [('printstream', 'Оттиск', 'Printstream', 'legendary', *PIC(printstream()))],
    'awp': [('azimuth', 'Азимут', 'Azimuth', 'legendary', *PIC(azimuth()))],
    'deagle': [('wave', 'Великая волна', 'Great Wave', 'epic', *PIC(great_wave()))],
    'usp': [('noir', 'Нуар', 'Noir', 'epic', *PIC(noir()))],
    'glock18': [('graffiti', 'Граффити', 'Graffiti', 'epic', *PIC(graffiti()))],
    'm4a4': [('serpent', 'Огненный змей', 'Fire Serpent', 'epic', *PIC(serpent()))],
    'mp9': [('acid', 'Кислота', 'Acid', 'rare', *PIC(acid()))],
    'galil': [('treasure', 'Карта сокровищ', 'Treasure Map', 'rare', *PIC(treasure()))],
    'g3sg1': [('dunes', 'Барханы', 'Dunes', 'uncommon', camo(C('#c2a873'), C('#9a8158'), C('#dcc89a'), 1.9), SK.solid(C('#3e3426'))),
              ('crash', 'Оранжевый всплеск', 'Orange Crash', 'rare', *PIC(orange_crash())),
              ('chalk', 'Мел', 'Chalkboard', 'epic', *PIC(chalk()))],
}
for gid, lst in GRAFFITI.items():
    SK.SKINS.setdefault(gid, [])
    have = {s[0] for s in SK.SKINS[gid]}
    SK.SKINS[gid] += [s for s in lst if s[0] not in have]
